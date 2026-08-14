import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getBuyerOrders,
  getSellerOrders,
  updateSellerOrderStatus,
} from '../api/orderApi'
import OrderStatus from '../pages/MyPage/OrderStatus'
import SalesOrders from '../pages/MyPage/SalesOrders'

vi.mock('../api/orderApi', () => ({
  approveSellerOrderRefund: vi.fn(),
  cancelBuyerOrder: vi.fn(),
  confirmBuyerOrder: vi.fn(),
  getBuyerOrder: vi.fn(),
  getBuyerOrders: vi.fn(),
  getSellerOrder: vi.fn(),
  getSellerOrders: vi.fn(),
  markBuyerOrderDelivered: vi.fn(),
  payBuyerOrder: vi.fn(),
  rejectSellerOrderRefund: vi.fn(),
  requestBuyerOrderRefund: vi.fn(),
  updateOrderShippingAddress: vi.fn(),
  updateSellerOrderStatus: vi.fn(),
}))

vi.mock('../api/userApi', () => ({ getUserProfile: vi.fn() }))

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

const order = (orderId, artName, overrides = {}) => ({
  orderId,
  artId: orderId,
  artName,
  artImage: '',
  counterpartyName: '테스트 사용자',
  orderNumber: `ORDER-${orderId}`,
  createdAt: '2026-08-14T10:00:00',
  winningPrice: 100000,
  status: 'PAID',
  availableActions: [],
  shippingAddressConfirmed: true,
  ...overrides,
})

const page = (item, overrides = {}) => ({
  content: [item],
  statusCounts: { [item.status]: 1 },
  totalElements: 1,
  totalPages: 1,
  ...overrides,
})

const renderPage = (element) => render(
  <MemoryRouter>{element}</MemoryRouter>,
)

describe('주문 목록 최신 요청 적용', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it.each([
    ['성공', (request) => request.resolve({ data: page(order(1, '이전 주문')) })],
    ['실패', (request) => request.reject(new Error('이전 요청 실패'))],
  ])('구매자 이전 필터 요청이 늦게 %s해도 최신 목록을 유지한다', async (
    _result,
    settlePrevious,
  ) => {
    const previous = deferred()
    const current = deferred()
    getBuyerOrders
      .mockReturnValueOnce(previous.promise)
      .mockReturnValueOnce(current.promise)

    renderPage(<OrderStatus />)
    await waitFor(() => expect(getBuyerOrders).toHaveBeenCalledTimes(1))

    fireEvent.click(screen.getByRole('button', { name: '결제 대기' }))
    await waitFor(() => expect(getBuyerOrders).toHaveBeenCalledTimes(2))
    expect(getBuyerOrders.mock.calls[0][0].signal.aborted).toBe(true)

    current.resolve({
      data: page(order(2, '최신 주문', { status: 'PAYMENT_PENDING' })),
    })
    expect(await screen.findByText('최신 주문')).toBeInTheDocument()
    const totalSummary = screen.getByText('전체 주문').parentElement
    expect(totalSummary.firstElementChild).toHaveTextContent('1')
    expect(totalSummary.nextElementSibling.firstElementChild).toHaveTextContent('1')

    settlePrevious(previous)
    await waitFor(() => expect(screen.getByText('최신 주문')).toBeInTheDocument())
    expect(screen.queryByText('이전 주문')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByText('주문 내역을 불러오는 중입니다.')).not.toBeInTheDocument()
  })

  it('구매자 최신 요청이 실패하면 이전 요청의 늦은 실패가 오류를 덮지 않는다', async () => {
    const previous = deferred()
    const current = deferred()
    getBuyerOrders
      .mockReturnValueOnce(previous.promise)
      .mockReturnValueOnce(current.promise)

    renderPage(<OrderStatus />)
    await waitFor(() => expect(getBuyerOrders).toHaveBeenCalledTimes(1))
    fireEvent.click(screen.getByRole('button', { name: '결제 완료' }))
    await waitFor(() => expect(getBuyerOrders).toHaveBeenCalledTimes(2))

    current.reject(new Error('최신 요청 실패'))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '주문 내역을 불러오지 못했습니다.',
    )
    previous.reject(new Error('이전 요청 실패'))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(
      '주문 내역을 불러오지 못했습니다.',
    ))
    expect(screen.getByText('주문 내역이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText('주문 내역을 불러오는 중입니다.')).not.toBeInTheDocument()
  })

  it('판매자 페이지 요청과 mutation 후 재조회가 경합하면 마지막 재조회만 반영한다', async () => {
    const pageRequest = deferred()
    const mutation = deferred()
    const reload = deferred()
    const initialOrder = order(10, '처리 전 주문', {
      availableActions: ['START_PREPARING'],
    })
    getSellerOrders
      .mockResolvedValueOnce({
        data: page(initialOrder, { totalElements: 2, totalPages: 2 }),
      })
      .mockReturnValueOnce(pageRequest.promise)
      .mockReturnValueOnce(reload.promise)
    updateSellerOrderStatus.mockReturnValue(mutation.promise)

    renderPage(<SalesOrders />)
    const orderActions = await screen.findByRole('group', {
      name: '처리 전 주문 주문 작업',
    })
    fireEvent.click(within(orderActions).getByRole('button', { name: '배송 준비' }))
    fireEvent.click(screen.getByRole('button', { name: '다음' }))
    await waitFor(() => expect(getSellerOrders).toHaveBeenCalledTimes(2))

    mutation.resolve({
      data: { ...initialOrder, status: 'PREPARING', availableActions: [] },
    })
    await waitFor(() => expect(getSellerOrders).toHaveBeenCalledTimes(3))
    expect(getSellerOrders.mock.calls[1][0].signal.aborted).toBe(true)
    expect(getSellerOrders.mock.calls[2][0]).toMatchObject({ page: 1 })

    reload.resolve({ data: page(order(12, '상태 변경 후 최신 주문')) })
    expect(await screen.findByText('상태 변경 후 최신 주문')).toBeInTheDocument()
    pageRequest.resolve({ data: page(order(11, '늦은 페이지 주문')) })

    await waitFor(() => expect(
      screen.getByText('상태 변경 후 최신 주문'),
    ).toBeInTheDocument())
    expect(screen.queryByText('늦은 페이지 주문')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
