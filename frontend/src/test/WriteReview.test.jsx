import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createReview, getOrderReview, updateReview } from '../api/reviewApi'
import WriteReview from '../pages/MyPage/WriteReview'

vi.mock('../api/reviewApi', () => ({
  createReview: vi.fn(),
  getOrderReview: vi.fn(),
  updateReview: vi.fn(),
}))

const context = {
  orderId: 17,
  artId: 3,
  artName: '주문 기반 리뷰 작품',
  artImage: 'https://example.com/art.jpg',
  artistName: '테스트 작가',
  winningPrice: 350000,
  review: null,
}

const existingReview = {
  reviewId: 31,
  orderId: 17,
  artId: 3,
  star: 7,
  content: '기존에 작성한 충분히 긴 리뷰 내용',
}

const renderPage = () => render(
  <MemoryRouter initialEntries={['/write-review/17']}>
    <Routes>
      <Route path="/write-review/:orderId" element={<WriteReview />} />
    </Routes>
  </MemoryRouter>,
)

describe('리뷰 작성·수정 화면', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getOrderReview.mockResolvedValue({ data: context })
    createReview.mockResolvedValue({ data: { ...existingReview, star: 9 } })
    updateReview.mockResolvedValue({ data: existingReview })
  })

  it('주문 ID로 초기 정보를 조회하고 생성 중 중복 제출을 막는다', async () => {
    let resolveCreate
    createReview.mockReturnValue(new Promise((resolve) => {
      resolveCreate = resolve
    }))
    renderPage()

    expect(await screen.findByRole('heading', { name: '리뷰 쓰기' })).toBeInTheDocument()
    expect(getOrderReview).toHaveBeenCalledWith('17', expect.objectContaining({
      signal: expect.any(AbortSignal),
    }))
    expect(screen.getByText('주문 기반 리뷰 작품')).toBeInTheDocument()
    expect(screen.getByText('350,000원')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '별점 9점' }))
    fireEvent.change(screen.getByLabelText(/리뷰 내용/), {
      target: { value: '  새로 작성한 충분히 긴 리뷰 내용  ' },
    })
    const submit = screen.getByRole('button', { name: '리뷰 등록' })
    fireEvent.click(submit)
    fireEvent.click(submit)

    expect(createReview).toHaveBeenCalledTimes(1)
    expect(createReview).toHaveBeenCalledWith({
      orderId: 17,
      star: 9,
      content: '새로 작성한 충분히 긴 리뷰 내용',
    })
    expect(screen.getByRole('button', { name: '처리 중…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '취소' })).toBeDisabled()

    await act(async () => resolveCreate({ data: existingReview }))
    expect(await screen.findByRole('heading', { name: '리뷰가 등록되었습니다!' }))
      .toBeInTheDocument()
    expect(screen.getByRole('link', { name: '내 리뷰 보기' }))
      .toHaveAttribute('href', '/mypage/my-review')
  })

  it('기존 리뷰를 표시하고 리뷰 ID로 수정한다', async () => {
    getOrderReview.mockResolvedValue({
      data: { ...context, review: existingReview },
    })
    renderPage()

    expect(await screen.findByRole('heading', { name: '리뷰 수정하기' })).toBeInTheDocument()
    expect(screen.getByLabelText(/리뷰 내용/)).toHaveValue(existingReview.content)
    expect(screen.getByRole('button', { name: '별점 7점' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )

    fireEvent.change(screen.getByLabelText(/리뷰 내용/), {
      target: { value: '수정해서 저장하는 충분히 긴 리뷰 내용' },
    })
    fireEvent.click(screen.getByRole('button', { name: '수정 완료' }))

    await waitFor(() => expect(updateReview).toHaveBeenCalledWith(31, {
      star: 7,
      content: '수정해서 저장하는 충분히 긴 리뷰 내용',
    }))
    expect(createReview).not.toHaveBeenCalled()
    expect(await screen.findByRole('heading', { name: '리뷰가 수정되었습니다!' }))
      .toBeInTheDocument()
  })

  it('공백 제거 후 길이를 검증하고 서버 충돌을 화면에 안내한다', async () => {
    createReview.mockRejectedValue({
      response: {
        status: 409,
        data: { code: 'REVIEW_ALREADY_EXISTS' },
      },
    })
    renderPage()
    await screen.findByRole('heading', { name: '리뷰 쓰기' })

    fireEvent.change(screen.getByLabelText(/리뷰 내용/), {
      target: { value: '          짧음          ' },
    })
    fireEvent.click(screen.getByRole('button', { name: '리뷰 등록' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '10자 이상 300자 이하',
    )
    expect(createReview).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText(/리뷰 내용/), {
      target: { value: '충돌 응답을 확인하는 충분히 긴 리뷰 내용' },
    })
    fireEvent.click(screen.getByRole('button', { name: '리뷰 등록' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이미 리뷰가 작성된 주문입니다.',
    )
  })

  it('직접 접근의 권한 오류를 안내하고 다시 조회할 수 있다', async () => {
    getOrderReview
      .mockRejectedValueOnce({
        response: {
          status: 403,
          data: { code: 'REVIEW_ACCESS_DENIED' },
        },
      })
      .mockResolvedValueOnce({ data: context })
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '접근할 권한이 없습니다.',
    )
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { name: '리뷰 쓰기' })).toBeInTheDocument()
    expect(getOrderReview).toHaveBeenCalledTimes(2)
  })
})
