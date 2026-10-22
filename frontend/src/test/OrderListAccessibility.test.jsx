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

  it('구매자 주문 상세를 여러 건 동시에 펼쳐 비교할 수 있다', async () => {
    const secondOrder = {
      ...order,
      orderId: 18,
      artId: 4,
      artName: '두 번째 주문 작품',
      orderNumber: 'ORDER-2026-VERY-LONG-00018',
      status: 'SHIPPED',
    }
    getBuyerOrders.mockResolvedValue({
      data: { ...page, content: [order, secondOrder], totalElements: 2 },
    })
    getBuyerOrder.mockImplementation((orderId) => Promise.resolve({
      data: orderId === 18 ? { ...detail, ...secondOrder } : detail,
    }))

    renderPage(<OrderStatus />)
    const firstToggle = await screen.findByRole('button', {
      name: `${order.artName} 주문 상세 보기`,
    })
    const secondToggle = screen.getByRole('button', {
      name: `${secondOrder.artName} 주문 상세 보기`,
    })

    fireEvent.click(firstToggle)
    await screen.findByRole('button', { name: `${secondOrder.artName} 주문 상세 보기` })
    fireEvent.click(secondToggle)

    await waitFor(() => {
      expect(document.getElementById('buyer-order-detail-17')).toBeInTheDocument()
      expect(document.getElementById('buyer-order-detail-18')).toBeInTheDocument()
    })
    expect(firstToggle).toHaveAttribute('aria-expanded', 'true')
    expect(secondToggle).toHaveAttribute('aria-expanded', 'true')

    fireEvent.click(secondToggle)
    expect(document.getElementById('buyer-order-detail-17')).toBeInTheDocument()
    expect(document.getElementById('buyer-order-detail-18')).not.toBeInTheDocument()
  })

  it('배송지 미확정 주문은 중복 없이 배송지 상태와 취소 사유를 안내한다', async () => {
    const canceled = { ...order, status: 'CANCELED' }
    getBuyerOrders.mockResolvedValue({
      data: { ...page, content: [canceled], statusCounts: { CANCELED: 1 } },
    })
    getBuyerOrder.mockResolvedValue({
      data: { ...detail, status: 'CANCELED', cancelReason: 'BUYER_FORFEIT' },
    })
    renderPage(<OrderStatus />)

    fireEvent.click(await screen.findByRole('button', {
      name: `${order.artName} 주문 상세 보기`,
    }))

    expect(await screen.findByText('취소 사유: 구매자가 낙찰을 포기했습니다.')).toBeInTheDocument()
    expect(screen.getByText('배송지')).toBeInTheDocument()
    expect(screen.getByText('미확정')).toBeInTheDocument()
    expect(screen.queryByText('받는 분')).not.toBeInTheDocument()
    expect(screen.queryByText('배송 주소')).not.toBeInTheDocument()
  })

  it('택배사와 송장을 배송 추적으로 표시한다', async () => {
    getBuyerOrder.mockResolvedValue({
      data: {
        ...detail,
        shippingAddress: {
          recipientName: '구매자',
          recipientPhone: '010-1234-5678',
          zipCode: '02535',
          address1: '서울특별시 중랑구',
          address2: '101호',
        },
        shippingCarrier: '택배사',
        trackingNumber: '1234567890',
      },
    })
    renderPage(<OrderStatus />)

    fireEvent.click(await screen.findByRole('button', {
      name: `${order.artName} 주문 상세 보기`,
    }))

    expect(await screen.findByText('배송 추적')).toBeInTheDocument()
    expect(screen.getByText('택배사 · 1234567890')).toBeInTheDocument()
  })

  it('구매확정 주문은 카드에서만 리뷰 동선을 제공한다', async () => {
    const confirmed = { ...order, status: 'CONFIRMED' }
    getBuyerOrders.mockResolvedValue({
      data: {
        ...page,
        content: [confirmed],
        statusCounts: { CONFIRMED: 1 },
      },
    })
    getBuyerOrder.mockResolvedValue({
      data: { ...detail, status: 'CONFIRMED', reviewId: null },
    })
    renderPage(<OrderStatus />)

    expect(await screen.findByRole('link', { name: '리뷰 쓰기·수정' }))
      .toHaveAttribute('href', '/write-review/17')
    expect(screen.getByRole('link', { name: `${order.artName} 작품 상세 보기` }))
      .toHaveAttribute('href', '/auction/3')

    fireEvent.click(await screen.findByRole('button', {
      name: `${order.artName} 주문 상세 보기`,
    }))

    await screen.findByText('결제·주문 정보')
    expect(screen.queryByRole('link', { name: '리뷰 쓰기' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '작품 페이지' })).not.toBeInTheDocument()
  })
})
