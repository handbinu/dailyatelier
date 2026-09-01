import { beforeEach, describe, expect, it, vi } from 'vitest'
import api from '../api/authApi'
import { createReview, getArtistReviews, getMyReviews, getOrderReview, updateReview } from '../api/reviewApi'

vi.mock('../api/authApi', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}))

describe('리뷰 API', () => {
  beforeEach(() => vi.clearAllMocks())

  it('주문 ID로 작성 정보를 조회한다', () => {
    const signal = new AbortController().signal
    getOrderReview(17, { signal })
    expect(api.get).toHaveBeenCalledWith(
      '/api/users/me/orders/17/review',
      { signal },
    )
  })

  it('생성 요청에는 주문 ID, 별점, 내용만 전달한다', () => {
    createReview({ orderId: 17, star: 9, content: '충분히 긴 리뷰 내용입니다.' })
    expect(api.post).toHaveBeenCalledWith('/api/users/me/reviews', {
      orderId: 17,
      star: 9,
      content: '충분히 긴 리뷰 내용입니다.',
    })
  })

  it('수정 요청은 리뷰 ID 경로와 변경 필드만 사용한다', () => {
    updateReview(31, { star: 8, content: '수정한 충분히 긴 리뷰입니다.' })
    expect(api.put).toHaveBeenCalledWith('/api/users/me/reviews/31', {
      star: 8,
      content: '수정한 충분히 긴 리뷰입니다.',
    })
  })

  it('내 리뷰를 서버 정렬과 페이지 조건으로 조회한다', () => {
    const signal = new AbortController().signal
    getMyReviews({ sort: 'STAR', page: 2, size: 6, signal })
    expect(api.get).toHaveBeenCalledWith('/api/users/me/reviews', {
      params: { sort: 'STAR', page: 2, size: 6 }, signal,
    })
  })

  it('작가 리뷰를 작품 필터와 서버 페이지 조건으로 조회한다', () => {
    getArtistReviews({ artId: 9, sort: 'PRICE', page: 1, size: 6 })
    expect(api.get).toHaveBeenCalledWith('/api/artists/me/reviews', {
      params: { artId: 9, sort: 'PRICE', page: 1, size: 6 }, signal: undefined,
    })
  })
})
