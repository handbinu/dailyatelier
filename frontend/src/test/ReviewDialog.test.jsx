import { createElement } from 'react'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ArtistReview from '../pages/MyPage/ArtistReview'
import MyReview from '../pages/MyPage/MyReview'
import { getArtistReviews, getMyReviews } from '../api/reviewApi'

vi.mock('../api/reviewApi', () => ({ getArtistReviews: vi.fn(), getMyReviews: vi.fn() }))

const review = {
  reviewId: 31, orderId: 17, artId: 9, artName: '봄날의 기억', artImage: '/image.jpg',
  buyerNickname: '아트러버123', winningPrice: 530000, star: 9,
  content: '작품이 정말 마음에 듭니다.', createdAt: '2026-08-20T12:00:00',
}

function renderPage(Component) {
  return render(
    <MemoryRouter>
      {createElement(Component)}
    </MemoryRouter>,
  )
}

describe('기존 리뷰 dialog', () => {
  beforeEach(() => {
    localStorage.setItem('token', 'token')
    localStorage.setItem('userStatus', '1')
    getMyReviews.mockResolvedValue({ data: { content: [review], totalElements: 1, totalPages: 1 } })
    getArtistReviews.mockResolvedValue({ data: { content: [review], totalElements: 1, totalPages: 1, totalReviewCount: 1, endedArtCount: 1, averageStar: 9, arts: [{ artId: 9, artName: '봄날의 기억' }] } })
  })

  it('내 리뷰 상세의 제목과 주문 기반 수정 동작을 유지한다', async () => {
    renderPage(MyReview)
    fireEvent.click(await screen.findByRole('button', { name: '봄날의 기억 리뷰 상세 보기' }))

    expect(screen.getByRole('dialog', { name: '봄날의 기억' })).toBeVisible()
    expect(screen.getByRole('link', { name: '리뷰 수정하기' })).toHaveAttribute('href', '/write-review/17')
    fireEvent.click(screen.getAllByRole('button', { name: '닫기' })[0])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('작가 리뷰 상세에 접근 가능한 제목과 구매자 nickname을 유지한다', async () => {
    renderPage(ArtistReview)
    fireEvent.click(await screen.findByRole('button', { name: '봄날의 기억 리뷰 상세 보기' }))

    const dialog = screen.getByRole('dialog', { name: '봄날의 기억 리뷰 상세' })
    expect(dialog).toBeVisible()
    expect(within(dialog).getByText('아트러버123')).toBeVisible()
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
