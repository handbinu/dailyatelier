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
  refundRequestedCount: 3,
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
  return screen.findByRole('heading', { name: '주문 정보' })
}

describe('판매 주문 상세 정보 구조', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getSellerOrders.mockResolvedValue({ data: page })
    getSellerOrder.mockResolvedValue({ data: detail })
  })

  it('전체 통계와 핵심 빠른 필터, 상태 선택을 제공한다', async () => {
    renderPage()

    expect(await screen.findByText('전체 판매')).toBeInTheDocument()
    expect(screen.getAllByText('배송 준비').length).toBeGreaterThan(0)
    expect(screen.getAllByText('발송 필요')).toHaveLength(2)
    expect(screen.getByText('환불 요청')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '전체' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '배송 준비 필요' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '발송 필요' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: '상태 선택' })).toBeInTheDocument()
    expect(screen.queryByText(order.orderNumber)).not.toBeInTheDocument()
  })

  it('중복 상태 요약 없이 판매자용 상세 정보를 묶어 제공한다', async () => {
    renderPage()
    await openDetail()

    for (const heading of [
      '주문 정보',
      '구매자·배송 정보',
      '환불 정보',
    ]) {
      expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument()
    }

    expect(screen.queryByText('현재 주문 상태')).not.toBeInTheDocument()
    expect(screen.queryByText('다음 가능한 작업')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '발송 정보' })).not.toBeInTheDocument()
    expect(screen.getByText('구매자가 환불을 요청했습니다. 승인 또는 거절을 선택해 처리해 주세요.')).toBeInTheDocument()
    expect(screen.queryByText('환불 요청됨')).not.toBeInTheDocument()

    const buyerSection = screen.getByRole('heading', { name: '구매자·배송 정보' }).closest('section')
    expect(within(buyerSection).getByText(detail.buyerName)).toBeInTheDocument()
    expect(within(buyerSection).getByText(detail.buyerPhone)).toBeInTheDocument()
    expect(within(buyerSection).queryByText(detail.buyerNickname)).not.toBeInTheDocument()

    expect(within(buyerSection).getByText(
      `(12345) ${detail.shippingAddress.address1} ${detail.shippingAddress.address2}`,
    )).toBeInTheDocument()
    expect(screen.getByRole('group', { name: '환불 결정' })).toBeInTheDocument()
    expect(screen.getByText(detail.orderNumber)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '작품 페이지로 이동' })).toHaveAttribute(
      'href',
      `/auction/${detail.artId}`,
    )
  })

  it('값이 없는 주문·발송 정보는 노출하지 않는다', async () => {
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

    expect(screen.getByText('구매자가 아직 배송지를 확정하지 않았습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '발송 정보' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '환불 정보' })).not.toBeInTheDocument()
    expect(screen.queryByText('-')).not.toBeInTheDocument()
  })

  it('취소 사유 enum을 사용자 문구로 표시한다', async () => {
    getSellerOrder.mockResolvedValue({
      data: { ...detail, status: 'CANCELED', cancelReason: 'BUYER_FORFEIT' },
    })
    renderPage()
    await openDetail()

    expect(screen.getByText('취소 사유: 구매자가 낙찰을 포기했습니다.')).toBeInTheDocument()
    expect(screen.queryByText('BUYER_FORFEIT')).not.toBeInTheDocument()
  })

  it('구매자와 배송 연락처가 같으면 배송 연락처만 표시한다', async () => {
    getSellerOrder.mockResolvedValue({
      data: {
        ...detail,
        buyerPhone: detail.shippingAddress.recipientPhone,
      },
    })
    renderPage()
    await openDetail()

    const buyerSection = screen.getByRole('heading', { name: '구매자·배송 정보' }).closest('section')
    expect(within(buyerSection).queryByText('구매자 연락처')).not.toBeInTheDocument()
    expect(within(buyerSection).getByText('연락처')).toBeInTheDocument()
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
