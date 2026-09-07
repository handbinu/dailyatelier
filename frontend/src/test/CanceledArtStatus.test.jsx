import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getMyArts } from '../api/artApi'
import { getAllMyBids, getMyLikes } from '../api/userApi'
import ManageArts from '../pages/MyPage/ManageArts'
import Likes from '../pages/MyPage/Likes'
import BidStatus from '../pages/MyPage/BidStatus'

vi.mock('../api/artApi', () => ({ getMyArts: vi.fn() }))
vi.mock('../api/userApi', () => ({
  getAllMyBids: vi.fn(), getMyLikes: vi.fn(), removeArtLike: vi.fn(),
}))

const page = (content) => ({ content, number: 0, last: true, totalPages: 1, totalElements: content.length })

describe('삭제·취소 작품 상태 표시', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('token', 'token')
    vi.stubGlobal('scrollTo', vi.fn())
  })

  it('작가 관리 목록에서 취소 상태와 결과를 유찰과 구분한다', async () => {
    getMyArts.mockResolvedValue({ data: page([{
      artId: 3, name: '취소 작품', material: '캔버스', startPrice: 1000, currentPrice: 2000,
      bidCount: 1, artStatus: 3, result: 'CANCELED', closingTime: '2099-01-01T00:00:00',
    }]) })
    render(<MemoryRouter initialEntries={['/mypage/manage-arts?state=ENDED&page=1']}><ManageArts /></MemoryRouter>)

    expect((await screen.findAllByText('경매 취소')).length).toBeGreaterThanOrEqual(2)
    expect(screen.queryByText('유찰')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '수정·삭제' })).not.toBeInTheDocument()
  })

  it('삭제 tombstone에는 서버 안내만 표시하고 잘못된 action을 만들지 않는다', async () => {
    getMyLikes.mockResolvedValue({ data: page([{
      likeId: 11, artId: null, artDeleted: true, availabilityMessage: '없어진 작품입니다.',
    }]) })
    render(<MemoryRouter><Likes /></MemoryRouter>)

    const card = (await screen.findByText('없어진 작품입니다.')).closest('article')
    expect(within(card).getAllByText('삭제된 작품').length).toBeGreaterThan(0)
    expect(within(card).queryByRole('link')).not.toBeInTheDocument()
    expect(within(card).queryByRole('button')).not.toBeInTheDocument()
  })

  it('취소된 찜은 상태와 사유를 표시하고 입찰 action을 제거한다', async () => {
    getMyLikes.mockResolvedValue({ data: page([{
      likeId: 12, artId: 3, artName: '취소 작품', artistName: '김작가', currentPrice: 2000,
      artStatus: 3, artDeleted: false,
    }]) })
    render(<MemoryRouter><Likes /></MemoryRouter>)

    expect(await screen.findByText('작가가 취소한 경매입니다.')).toBeVisible()
    expect(screen.getByText('경매 취소')).toBeVisible()
    expect(screen.queryByRole('link', { name: '입찰하기' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '상세 보기' })).toHaveAttribute('href', '/auction/3')
  })

  it('입찰 현황에서 취소 결과와 서버 안내 문구를 표시한다', async () => {
    getAllMyBids.mockResolvedValue([{
      artId: 3, artName: '취소 작품', artistName: '김작가', myBidPrice: 2000,
      currentPrice: 2000, auctionStatus: 'ENDED', bidResult: 'CANCELED',
      bidResultMessage: '작가가 취소한 경매입니다.', closingTime: '2099-01-01T00:00:00',
    }])
    render(<MemoryRouter><BidStatus /></MemoryRouter>)

    expect(await screen.findByText('경매 취소')).toBeVisible()
    expect(screen.getByText('작가가 취소한 경매입니다.')).toBeVisible()
    expect(screen.queryByText('패찰')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '가격 올리기' })).not.toBeInTheDocument()
  })
})
