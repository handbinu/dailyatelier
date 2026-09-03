import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MyPage from '../pages/MyPage/MyPage'
import { getAllMyBids, getAllMyWins, getUserProfile } from '../api/userApi'
import { getMyArts } from '../api/artApi'
import { getMyInquiries } from '../api/inquiryApi'

vi.mock('../api/userApi', () => ({
  getAllMyBids: vi.fn(),
  getAllMyWins: vi.fn(),
  getUserProfile: vi.fn(),
  updateUserProfile: vi.fn(),
}))

vi.mock('../api/artApi', () => ({
  getMyArts: vi.fn(),
}))

vi.mock('../api/inquiryApi', () => ({
  getMyInquiries: vi.fn(),
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

function renderMyPage(initialEntry = '/mypage') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
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
    getMyInquiries.mockResolvedValue({ data: { content: [], totalElements: 0 } })
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

  it('일반 회원에게 회원 메뉴만 링크로 제공하고 현재 위치를 표시한다', async () => {
    renderMyPage()
    await screen.findByText('테스트 사용자')

    const userMenu = screen.getByRole('navigation', { name: '회원 메뉴' })
    expect(within(userMenu).getByRole('link', { name: '홈' })).toHaveAttribute('aria-current', 'page')
    expect(screen.queryByRole('navigation', { name: '작가 관리' })).not.toBeInTheDocument()
  })

  it('작가 회원에게 분리된 작가 관리 메뉴와 네 목적지를 제공한다', async () => {
    localStorage.setItem('userStatus', '1')
    getMyArts.mockResolvedValue({ data: { totalElements: 2 } })
    renderMyPage('/mypage')
    await screen.findByText('테스트 사용자')

    const artistMenu = screen.getByRole('navigation', { name: '작가 관리' })
    expect(within(artistMenu).getByRole('link', { name: '작품 등록' })).toHaveAttribute('href', '/upload')
    expect(within(artistMenu).getByRole('link', { name: '작품 관리' })).toHaveAttribute('href', '/mypage/manage-arts')
    expect(within(artistMenu).getByRole('link', { name: '작품 리뷰' })).toHaveAttribute('href', '/mypage/artist-review')
    expect(within(artistMenu).getByRole('link', { name: '판매 주문' })).toHaveAttribute('href', '/mypage/sales-orders')
  })

  it('관리자 회원에게 문의 관리 바로가기를 제공한다', async () => {
    localStorage.setItem('userStatus', '2')

    renderMyPage()
    await screen.findByText('테스트 사용자')

    expect(screen.getByRole('link', { name: '관리자 문의 관리' })).toHaveAttribute('href', '/admin/inquiries')
  })

  it('문의 배지는 미답변 totalElements를 사용한다', async () => {
    getMyInquiries.mockResolvedValue({ data: { content: [{ inquiryId: 1 }], totalElements: 12 } })

    renderMyPage()
    await screen.findByText('테스트 사용자')

    expect(getMyInquiries).toHaveBeenCalledWith({ status: 'PENDING', size: 1 })
    expect(screen.getByText('12')).toBeVisible()
  })

  it('프로필 이미지를 표시하고 변경 링크를 제공한다', async () => {
    getUserProfile.mockResolvedValue({
      data: {
        ...profile,
        profileImageUrl: 'https://res.cloudinary.com/test/profile.png',
      },
    })

    renderMyPage()

    expect(await screen.findByRole('img', { name: '테스트 사용자 프로필' }))
      .toHaveAttribute('src', 'https://res.cloudinary.com/test/profile.png')
    expect(screen.getByRole('link', { name: '프로필 사진 변경' }))
      .toHaveAttribute('href', '/mypage/profile-edit')
  })

  it('프로필 이미지 로딩 실패 시 닉네임 이니셜을 표시한다', async () => {
    getUserProfile.mockResolvedValue({
      data: {
        ...profile,
        profileImageUrl: 'https://res.cloudinary.com/test/broken.png',
      },
    })
    renderMyPage()
    const image = await screen.findByRole('img', { name: '테스트 사용자 프로필' })

    fireEvent.error(image)

    expect(screen.queryByRole('img', { name: '테스트 사용자 프로필' })).not.toBeInTheDocument()
    expect(screen.getByText('테')).toBeVisible()
  })
})
