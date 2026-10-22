import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { StrictMode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SuccessfulBid from '../pages/MyPage/SuccessfulBid'
import BidStatus from '../pages/MyPage/BidStatus'
import ArtDetail from '../pages/Auction/ArtDetail'
import OrderStatus from '../pages/MyPage/OrderStatus'
import { getArt } from '../api/artApi'
import { getAllMyBids, getArtLikeStatus, getMyWins, getUserProfile } from '../api/userApi'
import { getBuyerOrder, getBuyerOrders, payBuyerOrder } from '../api/orderApi'
import { getPointSummary } from '../api/pointApi'

vi.mock('../api/artApi', () => ({ getArt: vi.fn(), createBid: vi.fn() }))
vi.mock('../api/userApi', () => ({
  addArtLike: vi.fn(),
  getAllMyBids: vi.fn(),
  getArtLikeStatus: vi.fn(),
  getMyWins: vi.fn(),
  getUserProfile: vi.fn(),
  removeArtLike: vi.fn(),
}))
vi.mock('../api/orderApi', () => ({
  cancelBuyerOrder: vi.fn(),
  confirmBuyerOrder: vi.fn(),
  getBuyerOrder: vi.fn(),
  getBuyerOrders: vi.fn(),
  markBuyerOrderDelivered: vi.fn(),
  payBuyerOrder: vi.fn(),
  requestBuyerOrderRefund: vi.fn(),
  updateOrderShippingAddress: vi.fn(),
}))
vi.mock('../api/pointApi', () => ({ getPointSummary: vi.fn() }))

const page = (content = [], overrides = {}) => ({
  content,
  statusCounts: { PAYMENT_PENDING: content.length },
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
  ...overrides,
})

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

const targetDetail = {
  orderId: 77,
  orderNumber: 'ORDER-2026-00077',
  artId: 7,
  artName: '정확한 낙찰 작품',
  artImage: '',
  winningPrice: 450_000,
  status: 'PAYMENT_PENDING',
  sellerArtistName: '테스트 작가',
  sellerNickname: 'artist',
  createdAt: '2026-09-08T10:00:00',
  paymentDueAt: '2026-09-09T10:00:00',
  availableActions: ['UPDATE_SHIPPING_ADDRESS'],
  shippingAddress: null,
  addressConfirmedAt: null,
}

const confirmedDetail = {
  ...targetDetail,
  availableActions: ['UPDATE_SHIPPING_ADDRESS'],
  shippingAddress: {
    recipientName: '구매자',
    recipientPhone: '010-0000-0000',
    zipCode: '12345',
    address1: '서울시 테스트로',
    address2: '',
  },
  addressConfirmedAt: '2026-09-08T10:05:00',
}

const renderAt = (path, element) => render(
  <MemoryRouter initialEntries={[path]}>{element}</MemoryRouter>,
)

describe('낙찰 후 주문 연결 흐름', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('token', 'token')
    getBuyerOrders.mockResolvedValue({ data: page([]) })
    getUserProfile.mockResolvedValue({ data: {} })
    getArtLikeStatus.mockResolvedValue({ data: { liked: false } })
    getPointSummary.mockResolvedValue({
      data: { availablePoint: 25_000, heldPoint: 450_000 },
    })
  })

  it('낙찰 작품은 orderId로 연결하고 주문이 없으면 대체 안내를 표시한다', async () => {
    getMyWins.mockResolvedValue({ data: page([
      { artId: 7, orderId: 77, artName: '연결 작품', winningPrice: 450_000 },
      { artId: 8, orderId: null, artName: '주문 누락 작품', winningPrice: 300_000 },
    ]) })

    renderAt('/mypage/successful-bid', <SuccessfulBid />)

    expect(await screen.findByRole('link', { name: '주문 확인' }))
      .toHaveAttribute('href', '/mypage/order-status?orderId=77')
    expect(screen.getByText('연결된 주문을 확인할 수 없습니다.')).toBeInTheDocument()
  })

  it('낙찰 작품은 최신 요청만 반영하고 언마운트 시 활성 요청을 취소한다', async () => {
    const previousRequest = deferred()
    const currentRequest = deferred()
    const pageRequest = deferred()
    getMyWins
      .mockReturnValueOnce(previousRequest.promise)
      .mockReturnValueOnce(currentRequest.promise)
      .mockReturnValueOnce(pageRequest.promise)

    const view = render(
      <StrictMode>
        <MemoryRouter initialEntries={['/mypage/successful-bid']}>
          <SuccessfulBid />
        </MemoryRouter>
      </StrictMode>,
    )

    await waitFor(() => expect(getMyWins).toHaveBeenCalledTimes(2))
    const previousSignal = getMyWins.mock.calls[0][0].signal
    expect(previousSignal.aborted).toBe(true)

    currentRequest.resolve({ data: page([
      { artId: 8, orderId: 88, artName: '최신 낙찰 작품', winningPrice: 500_000 },
    ], { totalPages: 2 }) })
    expect(await screen.findByText('최신 낙찰 작품')).toBeVisible()

    previousRequest.resolve({ data: page([
      { artId: 7, orderId: 77, artName: '이전 낙찰 작품', winningPrice: 400_000 },
    ]) })
    await waitFor(() => expect(screen.queryByText('이전 낙찰 작품')).not.toBeInTheDocument())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '다음' }))
    await waitFor(() => expect(getMyWins).toHaveBeenCalledTimes(3))
    expect(getMyWins.mock.calls[2][0]).toMatchObject({ page: 1, size: 12 })
    const pageSignal = getMyWins.mock.calls[2][0].signal

    view.unmount()
    expect(pageSignal.aborted).toBe(true)
    pageRequest.reject({ code: 'ERR_CANCELED' })
  })

  it('낙찰 작품의 이전 요청이 늦게 실패해도 최신 결과를 유지한다', async () => {
    const previousRequest = deferred()
    const currentRequest = deferred()
    getMyWins
      .mockReturnValueOnce(previousRequest.promise)
      .mockReturnValueOnce(currentRequest.promise)

    render(
      <StrictMode>
        <MemoryRouter initialEntries={['/mypage/successful-bid']}>
          <SuccessfulBid />
        </MemoryRouter>
      </StrictMode>,
    )

    await waitFor(() => expect(getMyWins).toHaveBeenCalledTimes(2))
    currentRequest.resolve({ data: page([
      { artId: 8, orderId: 88, artName: '현재 낙찰 작품', winningPrice: 500_000 },
    ]) })
    expect(await screen.findByText('현재 낙찰 작품')).toBeVisible()

    previousRequest.reject({ response: { data: { message: '늦은 목록 오류' } } })
    await waitFor(() => expect(screen.queryByText('늦은 목록 오류')).not.toBeInTheDocument())
    expect(screen.getByText('현재 낙찰 작품')).toBeVisible()
    expect(screen.queryByText('낙찰 작품을 불러오는 중입니다.')).not.toBeInTheDocument()
  })

  it('입찰 현황의 낙찰 건은 orderId로 주문 화면에 진입한다', async () => {
    getAllMyBids.mockResolvedValue([{
      artId: 7,
      orderId: 77,
      artName: '낙찰 작품',
      myBidPrice: 450_000,
      currentPrice: 450_000,
      auctionStatus: 'ENDED',
      bidResult: 'WON',
    }])

    renderAt('/mypage/bid-status', <BidStatus />)

    expect(await screen.findByRole('link', { name: '낙찰 주문 확인' }))
      .toHaveAttribute('href', '/mypage/order-status?orderId=77')
  })

  it('작품 상세는 낙찰 구매자에게 전달된 orderId로 주문 링크를 제공한다', async () => {
    getArt.mockResolvedValue({ data: {
      artId: 7,
      orderId: 77,
      artistName: '테스트 작가',
      name: '낙찰 작품',
      startPrice: 100_000,
      currentPrice: 450_000,
      minimumBidIncrement: 10_000,
      nextMinimumBidPrice: 460_000,
      bidStartTime: '2026-09-01T10:00:00',
      closingTime: '2026-09-02T10:00:00',
      artStatus: 2,
      isOwner: false,
    } })

    renderAt('/auction/7', (
      <Routes><Route path="/auction/:id" element={<ArtDetail />} /></Routes>
    ))

    expect(await screen.findByRole('link', { name: '낙찰 주문 확인' }))
      .toHaveAttribute('href', '/mypage/order-status?orderId=77')
  })

  it('목록 현재 페이지에 없어도 orderId 상세를 직접 조회해 미확정 배송지를 연다', async () => {
    getBuyerOrder.mockResolvedValue({ data: targetDetail })

    renderAt('/mypage/order-status?orderId=77', <OrderStatus />)

    expect(await screen.findByText('정확한 낙찰 작품')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '배송지 확인' })).toBeInTheDocument()
    expect(getBuyerOrder).toHaveBeenCalledWith(77)
  })

  it('포인트 조회 중 결제를 막고 사용 가능·예치 포인트와 결제 금액을 안내한다', async () => {
    let resolvePoints
    getBuyerOrder.mockResolvedValue({ data: confirmedDetail })
    getPointSummary.mockReturnValue(new Promise((resolve) => { resolvePoints = resolve }))

    renderAt('/mypage/order-status?orderId=77', <OrderStatus />)

    expect(await screen.findByRole('button', { name: '포인트 조회 중…' })).toBeDisabled()
    resolvePoints({ data: { availablePoint: 25_000, heldPoint: 450_000 } })
    const pointHeading = await screen.findByRole('heading', { name: '포인트 결제 안내' })
    const pointPanel = pointHeading.closest('section')
    expect(within(pointPanel).getByText(/사용 가능 포인트가 결제 금액보다 적어도 결제할 수 있습니다/)).toBeInTheDocument()
    expect(within(pointPanel).getByText('사용 가능 포인트')).toBeInTheDocument()
    expect(within(pointPanel).getByText('전체 예치 포인트')).toBeInTheDocument()
    expect(within(pointPanel).getByText('결제 금액')).toBeInTheDocument()
    expect(await screen.findByText('25,000P')).toBeInTheDocument()
    expect(screen.getByText('450,000P')).toBeInTheDocument()
    expect(screen.getAllByText('450,000원').length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: '포인트 결제' })).toBeEnabled()
  })

  it('포인트 조회 실패 시 결제를 막고 재조회할 수 있다', async () => {
    getBuyerOrder.mockResolvedValue({ data: confirmedDetail })
    getPointSummary
      .mockRejectedValueOnce({ response: { data: { message: '잔액 조회 실패' } } })
      .mockResolvedValueOnce({ data: { availablePoint: 25_000, heldPoint: 450_000 } })

    renderAt('/mypage/order-status?orderId=77', <OrderStatus />)

    expect(await screen.findByRole('alert')).toHaveTextContent('잔액 조회 실패')
    expect(screen.getByRole('button', { name: '포인트 확인 필요' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '다시 조회' }))
    await waitFor(() => expect(getPointSummary).toHaveBeenCalledTimes(2))
    expect(await screen.findByRole('button', { name: '포인트 결제' })).toBeEnabled()
  })

  it('결제 충돌은 서버 오류를 안내하고 주문과 포인트를 다시 조회한다', async () => {
    getBuyerOrder.mockResolvedValue({ data: confirmedDetail })
    payBuyerOrder.mockRejectedValue({
      response: {
        status: 409,
        data: { code: 'POINT_HOLD_INTEGRITY', message: '낙찰 예치를 찾을 수 없습니다.' },
      },
    })

    renderAt('/mypage/order-status?orderId=77', <OrderStatus />)
    fireEvent.click(await screen.findByRole('button', { name: '포인트 결제' }))

    expect(await screen.findByText('낙찰 예치를 찾을 수 없습니다.')).toBeInTheDocument()
    await waitFor(() => expect(getBuyerOrder).toHaveBeenCalledTimes(2))
    expect(getPointSummary).toHaveBeenCalledTimes(2)
  })
})
