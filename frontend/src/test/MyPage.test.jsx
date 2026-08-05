import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MyPage from '../pages/MyPage/MyPage'
import { getAllMyBids, getAllMyWins, getUserProfile } from '../api/userApi'

vi.mock('../api/userApi', () => ({
  getAllMyBids: vi.fn(),
  getAllMyWins: vi.fn(),
  getUserProfile: vi.fn(),
  updateUserProfile: vi.fn(),
}))

vi.mock('../api/artApi', () => ({
  getMyArts: vi.fn(),
}))

const profile = {
  nickname: '테스트 사용자',
  email: 'test@example.com',
  availablePoint: 0,
  heldPoint: 0,
}

const bids = [{
  artId: 11,
  artName: '진행 작품',
  imgPath: '',
  myBidPrice: 10000,
  auctionStatus: 'ONGOING',
}]

const wins = [
  { artId: 21, artName: '첫 번째 낙찰작', imgPath: '', winningPrice: 20000 },
  { artId: 22, artName: '두 번째 낙찰작', imgPath: '', winningPrice: 30000 },
]

function renderMyPage() {
  return render(
    <MemoryRouter initialEntries={['/mypage']}>
      <MyPage />
    </MemoryRouter>,
  )
}

describe('마이페이지 낙찰 작품 요약', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('token', 'test-token')
    localStorage.setItem('userStatus', '0')
    getUserProfile.mockResolvedValue({ data: profile })
    getAllMyBids.mockResolvedValue(bids)
    getAllMyWins.mockResolvedValue(wins)
  })

  it('각 낙찰 작품에 자신의 artId를 사용하는 주문 확인 링크를 표시한다', async () => {
    renderMyPage()

    const links = await screen.findAllByRole('link', { name: '주문 확인' })

    expect(links).toHaveLength(2)
    expect(links[0]).toHaveAttribute('href', '/mypage/order-status?artId=21')
    expect(links[1]).toHaveAttribute('href', '/mypage/order-status?artId=22')
  })

  it('진행 중 입찰 행에는 주문 확인 링크를 표시하지 않는다', async () => {
    renderMyPage()

    const bidTitle = await screen.findByText('진행 작품')
    const bidRow = bidTitle.closest('div[class*="miniRow"]')

    expect(within(bidRow).queryByRole('link', { name: '주문 확인' })).not.toBeInTheDocument()
  })

  it.each([
    ['빈 상태', () => getAllMyWins.mockResolvedValue([])],
    ['오류 상태', () => getAllMyWins.mockRejectedValue(new Error('조회 실패'))],
  ])('낙찰 작품 %s에는 주문 확인 링크를 표시하지 않는다', async (_name, arrange) => {
    arrange()
    renderMyPage()

    await waitFor(() => expect(getAllMyWins).toHaveBeenCalled())
    expect(screen.queryByRole('link', { name: '주문 확인' })).not.toBeInTheDocument()
  })

  it('낙찰 작품 로딩 중에는 주문 확인 링크를 표시하지 않는다', async () => {
    getAllMyWins.mockReturnValue(new Promise(() => {}))
    renderMyPage()

    await screen.findByText('낙찰 작품을 불러오는 중입니다.')
    expect(screen.queryByRole('link', { name: '주문 확인' })).not.toBeInTheDocument()
  })
})
