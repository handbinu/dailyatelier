import { createElement } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ArtistReview from '../pages/MyPage/ArtistReview'
import MyReview from '../pages/MyPage/MyReview'
import { getArtistReviews, getMyReviews } from '../api/reviewApi'

vi.mock('../api/reviewApi', () => ({ getArtistReviews: vi.fn(), getMyReviews: vi.fn() }))

const review = {
  reviewId: 31, orderId: 17, artId: 9, artName: '봄날의 기억', artImage: '/image.jpg',
  buyerNickname: '구매자별명', winningPrice: 530000, star: 9,
  content: '서버에서 조회한 실제 리뷰 내용입니다.', createdAt: '2026-08-20T12:00:00',
}
const renderPage = (Component) => render(<MemoryRouter>{createElement(Component)}</MemoryRouter>)

describe('리뷰 목록 실제 연동', () => {
  beforeEach(() => vi.clearAllMocks())

  it('내 리뷰의 로딩, 서버 정렬, 페이지 이동과 빈 상태를 처리한다', async () => {
    getMyReviews
      .mockResolvedValueOnce({ data: { content: [review], totalElements: 7, totalPages: 2 } })
      .mockResolvedValueOnce({ data: { content: [], totalElements: 0, totalPages: 0 } })
      .mockResolvedValueOnce({ data: { content: [], totalElements: 0, totalPages: 0 } })
    renderPage(MyReview)
    expect(screen.getByRole('status')).toHaveTextContent('불러오는 중')
    expect(await screen.findByText('서버에서 조회한 실제 리뷰 내용입니다.')).toBeVisible()
    expect(getMyReviews).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'RECENT', page: 0, size: 6 }))
    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))
    await waitFor(() => expect(getMyReviews).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })))
    fireEvent.click(screen.getByRole('button', { name: '별점순' }))
    await waitFor(() => expect(getMyReviews).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'STAR', page: 0 })))
    expect(await screen.findByText('작성한 리뷰가 없습니다.')).toBeVisible()
  })

  it('내 리뷰 조회 오류를 안내하고 재시도한다', async () => {
    getMyReviews.mockRejectedValueOnce({ response: { status: 500 } }).mockResolvedValueOnce({ data: { content: [], totalElements: 0, totalPages: 0 } })
    renderPage(MyReview)
    expect(await screen.findByRole('alert')).toHaveTextContent('불러오지 못했습니다')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText('작성한 리뷰가 없습니다.')).toBeVisible()
    expect(getMyReviews).toHaveBeenCalledTimes(2)
  })

  it('현재 선택된 정렬과 필터를 다시 선택해도 로딩 상태로 전환하지 않는다', async () => {
    getMyReviews.mockResolvedValue({ data: { content: [review], totalElements: 1, totalPages: 1 } })
    const myReviewPage = renderPage(MyReview)
    expect(await screen.findByText('서버에서 조회한 실제 리뷰 내용입니다.')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: '최근 리뷰순' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(getMyReviews).toHaveBeenCalledTimes(1)
    myReviewPage.unmount()

    getArtistReviews.mockResolvedValue({ data: { content: [review], totalElements: 1, totalPages: 1, totalReviewCount: 1, averageStar: 9, soldArtCount: 1, reviewedArtCount: 1, unreviewedArtCount: 0, unreviewedSoldArts: [] } })
    renderPage(ArtistReview)
    expect(await screen.findByText('구매자: 구매자별명')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: '최근순' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(getArtistReviews).toHaveBeenCalledTimes(1)
  })

  it('판매 완료 리뷰 현황과 미작성 작품 목록을 표시하고 작품별 버튼은 제거한다', async () => {
    getArtistReviews
      .mockResolvedValueOnce({ data: { content: [review], totalElements: 5, totalPages: 1, totalReviewCount: 5, averageStar: 8.4, soldArtCount: 4, reviewedArtCount: 3, unreviewedArtCount: 1, unreviewedSoldArts: [{ artId: 10, artName: '리뷰 없는 판매 작품', artImage: '/empty.jpg' }] } })
    renderPage(ArtistReview)
    expect(await screen.findByText('총 5개 리뷰')).toBeVisible()
    expect(screen.getByText('판매 작품 리뷰 현황')).toBeVisible()
    expect(screen.getByText('판매 완료')).toBeVisible()
    expect(screen.getByText('리뷰 작성')).toBeVisible()
    expect(screen.getByText(/전체 리뷰 평균 별점/)).toBeVisible()
    expect(screen.getByText('4')).toBeVisible()
    expect(screen.getByText('3')).toBeVisible()
    expect(screen.getByText('8.4')).toBeVisible()
    expect(screen.getByText('구매자: 구매자별명')).toBeVisible()
    expect(screen.queryByRole('button', { name: '봄날의 기억' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /판매 완료/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /리뷰 작성 작품/ })).not.toBeInTheDocument()

    expect(screen.queryByRole('button', { name: '리뷰 미작성 작품 1개 보기' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '리뷰 미작성 작품 보기' }))
    expect(screen.getByText('리뷰 미작성 작품')).toBeVisible()
    expect(screen.getByText('리뷰 없는 판매 작품')).toBeVisible()
    expect(screen.getByRole('link', { name: '리뷰 없는 판매 작품 작품 상세 보기' })).toHaveAttribute('href', '/auction/10')
    expect(screen.queryByRole('button', { name: '최근순' })).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '작성된 리뷰 보기' }))
    expect(screen.getByRole('button', { name: '최근순' })).toBeVisible()
    expect(screen.getByText('구매자: 구매자별명')).toBeVisible()
  })

  it('작가 권한 오류와 재시도를 처리한다', async () => {
    getArtistReviews.mockRejectedValueOnce({ response: { status: 403 } }).mockResolvedValueOnce({ data: { content: [], totalElements: 0, totalPages: 0, totalReviewCount: 0, averageStar: null, soldArtCount: 0, reviewedArtCount: 0, unreviewedArtCount: 0, unreviewedSoldArts: [] } })
    renderPage(ArtistReview)
    expect(await screen.findByRole('alert')).toHaveTextContent('작가만')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText('작성된 작품 리뷰가 없습니다.')).toBeVisible()
  })

  it('작가 리뷰 조건 변경 조회가 실패하면 이전 통계와 결과를 표시하지 않는다', async () => {
    getArtistReviews
      .mockResolvedValueOnce({ data: { content: [review], totalElements: 5, totalPages: 1, totalReviewCount: 12, averageStar: 8.4, soldArtCount: 4, reviewedArtCount: 3, unreviewedArtCount: 1, unreviewedSoldArts: [{ artId: 10, artName: '리뷰 없는 판매 작품', artImage: '/empty.jpg' }] } })
      .mockRejectedValueOnce({ response: { status: 500 } })
    renderPage(ArtistReview)
    expect(await screen.findByText('총 5개 리뷰')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: '별점순' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('불러오지 못했습니다')
    expect(screen.getByText('총 0개 리뷰')).toBeVisible()
    expect(screen.getByText('-')).toBeVisible()
    expect(screen.getByText('4')).toBeVisible()
    expect(screen.getByText('3')).toBeVisible()
    expect(screen.queryByText('구매자: 구매자별명')).not.toBeInTheDocument()
  })

  it('작가 리뷰 조건 변경 중 전체 통계는 유지하고 필터 통계만 로딩 상태로 표시한다', async () => {
    let resolveFiltered
    getArtistReviews
      .mockResolvedValueOnce({ data: { content: [review], totalElements: 5, totalPages: 1, totalReviewCount: 12, averageStar: 8.4, soldArtCount: 4, reviewedArtCount: 3, unreviewedArtCount: 1, unreviewedSoldArts: [{ artId: 10, artName: '리뷰 없는 판매 작품', artImage: '/empty.jpg' }] } })
      .mockImplementationOnce(() => new Promise(resolve => { resolveFiltered = resolve }))
    renderPage(ArtistReview)
    expect(await screen.findByText('총 5개 리뷰')).toBeVisible()

    fireEvent.click(screen.getByRole('button', { name: '별점순' }))
    expect(screen.getByText('4')).toBeVisible()
    expect(screen.getByText('3')).toBeVisible()
    expect(screen.getByText('리뷰 결과 조회 중')).toBeVisible()

    resolveFiltered({ data: { content: [], totalElements: 0, totalPages: 0, totalReviewCount: 12, averageStar: null, soldArtCount: 4, reviewedArtCount: 3, unreviewedArtCount: 1, unreviewedSoldArts: [{ artId: 10, artName: '리뷰 없는 판매 작품', artImage: '/empty.jpg' }] } })
    expect(await screen.findByText('총 0개 리뷰')).toBeVisible()
  })
})
