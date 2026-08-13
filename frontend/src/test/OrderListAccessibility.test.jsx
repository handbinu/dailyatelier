import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getBuyerOrder,
  getBuyerOrders,
  getSellerOrder,
  getSellerOrders,
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

const order = {
  orderId: 17,
  artId: 3,
  artName: '아주 긴 작품 이름도 모바일에서 안전하게 읽히는 작품',
  artImage: '',
  counterpartyName: '테스트 사용자',
  orderNumber: 'ORDER-2026-VERY-LONG-00017',
  createdAt: '2026-08-13T10:00:00',
  winningPrice: 1234567,
  status: 'PAID',
  availableActions: [],
  shippingAddressConfirmed: true,
}

const detail = {
  ...order,
  buyerName: '구매자',
  buyerNickname: '닉네임',
  buyerPhone: '010-1234-5678',
  shippingAddress: null,
}

const page = {
  content: [order],
  statusCounts: { PAID: 1 },
  totalElements: 1,
  totalPages: 1,
}

const renderPage = (element) => render(
  <MemoryRouter>{element}</MemoryRouter>,
)

describe('주문 목록 상세 토글 접근성', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getBuyerOrders.mockResolvedValue({ data: page })
    getSellerOrders.mockResolvedValue({ data: page })
    getBuyerOrder.mockResolvedValue({ data: detail })
    getSellerOrder.mockResolvedValue({ data: detail })
  })

  it.each([
    ['구매자', <OrderStatus />, getBuyerOrder, 'buyer-order-detail-17'],
    ['판매자', <SalesOrders />, getSellerOrder, 'seller-order-detail-17'],
  ])('%s 목록의 주문별 토글과 상세 패널을 연결하고 포커스를 유지한다', async (
    _role,
    element,
    detailRequest,
    panelId,
  ) => {
    renderPage(element)
    const toggle = await screen.findByRole('button', {
      name: `${order.artName} 주문 상세 보기`,
    })

    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle).toHaveAttribute('aria-controls', panelId)

    toggle.focus()
    fireEvent.click(toggle)

    await waitFor(() => expect(detailRequest).toHaveBeenCalledWith(order.orderId))
    expect(toggle).toHaveFocus()
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(document.getElementById(panelId)).toBeInTheDocument()

    fireEvent.click(toggle)
    expect(toggle).toHaveFocus()
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(document.getElementById(panelId)).not.toBeInTheDocument()
  })

  it('구매자 상세 로딩 실패를 alert로 전달한다', async () => {
    getBuyerOrder.mockRejectedValue({ response: { status: 500 } })
    renderPage(<OrderStatus />)

    fireEvent.click(await screen.findByRole('button', {
      name: `${order.artName} 주문 상세 보기`,
    }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '주문 상세를 불러오지 못했습니다.',
    )
  })
})
