import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OrderStatus from '../pages/MyPage/OrderStatus'
import SalesOrders from '../pages/MyPage/SalesOrders'
import { getBuyerOrders, getSellerOrders } from '../api/orderApi'

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

function LoginStateView() {
  const location = useLocation()
  const from = location.state?.from
  return <output data-testid="login-from">{from?.pathname}{from?.search}{from?.hash}</output>
}

const unauthorized = { response: { status: 401, data: { message: '인증이 필요합니다.' } } }

const renderPage = (path, element) => render(
  <MemoryRouter initialEntries={[path]}>
    <Routes>
      <Route path={path.split('?')[0]} element={element} />
      <Route path="/login" element={<LoginStateView />} />
    </Routes>
  </MemoryRouter>,
)

describe('주문 화면 인증 만료 복귀 위치', () => {
  beforeEach(() => vi.clearAllMocks())

  it('구매자 주문 GET 화면의 query를 로그인 state로 전달한다', async () => {
    getBuyerOrders.mockRejectedValue(unauthorized)
    renderPage('/mypage/order-status?artId=7', <OrderStatus />)

    await waitFor(() => expect(getBuyerOrders).toHaveBeenCalledTimes(1))
    expect(await screen.findByTestId('login-from')).toHaveTextContent(
      '/mypage/order-status?artId=7',
    )
  })

  it('판매 주문 GET 화면을 로그인 state로 전달한다', async () => {
    getSellerOrders.mockRejectedValue(unauthorized)
    renderPage('/mypage/sales-orders', <SalesOrders />)

    await waitFor(() => expect(getSellerOrders).toHaveBeenCalledTimes(1))
    expect(await screen.findByTestId('login-from')).toHaveTextContent(
      '/mypage/sales-orders',
    )
  })
})
