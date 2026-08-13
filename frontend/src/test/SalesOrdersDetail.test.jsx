import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getSellerOrder,
  getSellerOrders,
  updateSellerOrderStatus,
} from '../api/orderApi'
import SalesOrders from '../pages/MyPage/SalesOrders'

vi.mock('../api/orderApi', () => ({
  approveSellerOrderRefund: vi.fn(),
  getSellerOrder: vi.fn(),
  getSellerOrders: vi.fn(),
  rejectSellerOrderRefund: vi.fn(),
  updateSellerOrderStatus: vi.fn(),
}))

const actions = [
  'START_PREPARING',
  'SHIP',
  'APPROVE_REFUND',
  'REJECT_REFUND',
]

const order = {
  orderId: 21,
  artId: 8,
  artName: '판매 주문 상세 구조 테스트 작품',
  artImage: '',
  counterpartyName: '구매자 닉네임',
  orderNumber: 'ORDER-2026-00021',
  createdAt: '2026-08-13T10:00:00',
  winningPrice: 980000,
  status: 'PAID',
  availableActions: actions,
}

const detail = {
  ...order,
  buyerName: '아주 긴 구매자 이름',
  buyerNickname: '아주 긴 구매자 닉네임',
  buyerPhone: '010-1234-5678',
  paidAt: '2026-08-13T10:30:00',
  shippingAddress: {
    recipientName: '아주 긴 수령인 이름',
    recipientPhone: '010-9876-5432',
    zipCode: '12345',
    address1: '서울특별시 아주 긴 기본 주소가 줄바꿈되어야 하는 도로명 123',
    address2: '아주 긴 상세 주소 101동 202호 공동현관 앞',
  },
  refundRequestStatus: 'REQUESTED',
  refundRequestReason: '작품 상태 확인이 필요합니다.',
  refundRequestedAt: '2026-08-13T11:00:00',
}

const page = {
  content: [order],
  statusCounts: { PAID: 1 },
  totalElements: 1,
  totalPages: 1,
}

const renderPage = () => render(
  <MemoryRouter><SalesOrders /></MemoryRouter>,
)

const openDetail = async () => {
  fireEvent.click(await screen.findByRole('button', {
    name: `${order.artName} 주문 상세 보기`,
  }))
  return screen.findByRole('heading', { name: '현재 주문 상태' })
}

describe('판매 주문 상세 정보 구조', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getSellerOrders.mockResolvedValue({ data: page })
    getSellerOrder.mockResolvedValue({ data: detail })
  })

  it('현재 상태와 다음 작업 뒤에 의미별 정보 section을 제공한다', async () => {
    renderPage()
    await openDetail()

    expect(screen.getByText('배송 준비, 발송 처리, 환불 승인, 환불 거절')).toBeInTheDocument()
    for (const heading of [
      '주문·결제',
      '구매자 연락처',
      '배송지',
      '발송 정보',
      '환불 요청 상태',
    ]) {
      expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument()
    }

    const buyerSection = screen.getByRole('heading', { name: '구매자 연락처' }).closest('section')
    expect(within(buyerSection).getByText(detail.buyerName)).toBeInTheDocument()
    expect(within(buyerSection).getByText(detail.buyerNickname)).toBeInTheDocument()
    expect(within(buyerSection).getByText(detail.buyerPhone)).toBeInTheDocument()

    const addressSection = screen.getByRole('heading', { name: '배송지' }).closest('section')
    expect(within(addressSection).getByText(detail.shippingAddress.address1)).toBeInTheDocument()
    expect(within(addressSection).getByText(detail.shippingAddress.address2)).toBeInTheDocument()
    expect(screen.getByRole('group', { name: '환불 결정' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '작품 페이지로 이동' })).toHaveAttribute(
      'href',
      `/auction/${detail.artId}`,
    )
  })

  it('배송지와 상태별 정보가 비어도 section과 대체값을 유지한다', async () => {
    getSellerOrder.mockResolvedValue({
      data: {
        ...detail,
        availableActions: [],
        shippingAddress: null,
        paidAt: null,
        preparingAt: null,
        shippedAt: null,
        refundRequestStatus: null,
      },
    })
    renderPage()
    await openDetail()

    expect(screen.getByText('현재 가능한 작업이 없습니다.')).toBeInTheDocument()
    expect(screen.getByText('배송지 미확정')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '발송 정보' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '환불 요청 상태' })).not.toBeInTheDocument()
  })

  it('발송 입력에 도움말·오류·제출 상태를 연결한다', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '발송 처리' }))

    const help = await screen.findByText(
      '택배사와 송장번호를 확인한 뒤 발송 정보를 저장해 주세요.',
    )
    const shippingForm = help.closest('form')
    expect(shippingForm).toHaveAttribute('aria-busy', 'false')
    expect(within(shippingForm).getByText(
      '택배사와 송장번호를 확인한 뒤 발송 정보를 저장해 주세요.',
    )).toBeInTheDocument()
    expect(within(shippingForm).getByLabelText('택배사')).toHaveAttribute('aria-describedby')
    expect(within(shippingForm).getByLabelText('송장번호')).toHaveAttribute('aria-describedby')

    fireEvent.submit(shippingForm)
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '택배사와 송장번호를 모두 입력해 주세요.',
    )

    updateSellerOrderStatus.mockReturnValue(new Promise(() => {}))
    fireEvent.change(within(shippingForm).getByLabelText('택배사'), {
      target: { value: '테스트 택배' },
    })
    fireEvent.change(within(shippingForm).getByLabelText('송장번호'), {
      target: { value: 'TRACK-1234' },
    })
    fireEvent.click(within(shippingForm).getByRole('button', { name: '발송 저장' }))

    expect(shippingForm).toHaveAttribute('aria-busy', 'true')
    expect(within(shippingForm).getByRole('button', { name: '처리 중...' })).toBeDisabled()
  })

  it('상세 로딩 실패를 alert로 전달한다', async () => {
    getSellerOrder.mockRejectedValue({ response: { status: 500 } })
    renderPage()
    fireEvent.click(await screen.findByRole('button', {
      name: `${order.artName} 주문 상세 보기`,
    }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '판매 주문 상세를 불러오지 못했습니다.',
    )
  })
})
