import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getBuyerOrder,
  getBuyerOrders,
  updateOrderShippingAddress,
} from '../api/orderApi'
import OrderStatus from '../pages/MyPage/OrderStatus'
import { getUserProfile } from '../api/userApi'

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

vi.mock('../api/userApi', () => ({ getUserProfile: vi.fn() }))

const shippingAddress = {
  recipientName: '기존 수령인',
  recipientPhone: '010-1111-2222',
  zipCode: '12345',
  address1: '서울시 기존 주소 1',
  address2: '기존 상세 주소',
}

const order = {
  orderId: 31,
  artId: 9,
  artName: '배송지 변경 테스트 작품',
  artImage: '',
  counterpartyName: '테스트 작가',
  orderNumber: 'ORDER-2026-00031',
  createdAt: '2026-08-13T10:00:00',
  winningPrice: 450000,
  status: 'PAYMENT_PENDING',
  availableActions: ['UPDATE_SHIPPING_ADDRESS'],
  shippingAddressConfirmed: true,
}

const detail = {
  ...order,
  shippingAddress,
  addressConfirmedAt: '2026-08-13T10:10:00',
}

const makePage = (overrides = {}) => ({
  content: [{ ...order, ...overrides }],
  statusCounts: { PAYMENT_PENDING: 1 },
  totalElements: 1,
  totalPages: 1,
})

const renderPage = () => render(
  <MemoryRouter><OrderStatus /></MemoryRouter>,
  { container: document.getElementById('root') },
)

const openAddressForm = async () => {
  fireEvent.click(await screen.findByRole('button', { name: '배송지 변경' }))
  return screen.findByRole('heading', { name: '배송지 확인' })
}

const changeAddressAndSubmit = async (value = '서울시 변경 주소 99') => {
  const address1 = screen.getByLabelText('기본 주소')
  fireEvent.change(address1, { target: { value } })
  const submit = screen.getByRole('button', { name: '배송지 확정' })
  submit.focus()
  fireEvent.click(submit)
  return { address1, submit }
}

describe('구매자 배송지 재확정 dialog', () => {
  beforeEach(() => {
    document.body.innerHTML = '<div id="root"></div>'
    vi.clearAllMocks()
    vi.stubGlobal('requestAnimationFrame', (callback) => {
      callback()
      return 1
    })
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    getBuyerOrders.mockResolvedValue({ data: makePage() })
    getBuyerOrder.mockResolvedValue({ data: detail })
    getUserProfile.mockResolvedValue({ data: {} })
    updateOrderShippingAddress.mockResolvedValue({ data: detail })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.body.style.overflow = ''
  })

  it('변경 주소를 비교하고 취소 시 서버 확정값 복원과 focus return을 제공한다', async () => {
    renderPage()
    await openAddressForm()
    const { address1, submit } = await changeAddressAndSubmit()

    const dialog = await screen.findByRole('dialog', {
      name: '배송지 변경을 확정할까요?',
    })
    expect(dialog).toHaveAccessibleDescription(
      '결제와 배송이 시작되기 전에 이 주문의 확정 배송지가 변경됩니다.',
    )
    expect(updateOrderShippingAddress).not.toHaveBeenCalled()
    expect(within(dialog).getByRole('button', { name: '취소' })).toHaveFocus()
    expect(within(dialog).getByText('기본 주소 (변경됨)')).toBeInTheDocument()
    expect(within(dialog).getByText('서울시 기존 주소 1')).toBeInTheDocument()
    expect(within(dialog).getByText('서울시 변경 주소 99')).toBeInTheDocument()

    const cancel = within(dialog).getByRole('button', { name: '취소' })
    const confirm = within(dialog).getByRole('button', { name: '배송지 변경 확정' })
    confirm.focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(cancel).toHaveFocus()
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(confirm).toHaveFocus()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(address1).toHaveValue(shippingAddress.address1)
    expect(screen.getByRole('heading', { name: '배송지 확인' })).toBeInTheDocument()
    expect(submit).toHaveFocus()
  })

  it('backdrop 취소도 API 없이 서버 확정값으로 복원한다', async () => {
    renderPage()
    await openAddressForm()
    const { address1 } = await changeAddressAndSubmit('부산시 변경 주소')
    const dialog = await screen.findByRole('dialog')

    fireEvent.mouseDown(dialog.parentElement)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(address1).toHaveValue(shippingAddress.address1)
    expect(updateOrderShippingAddress).not.toHaveBeenCalled()
  })

  it('동일 주소 또는 기본 배송지만 변경하면 dialog 없이 기존 PUT을 호출한다', async () => {
    renderPage()
    await openAddressForm()
    fireEvent.click(screen.getByLabelText('이 배송지를 기본 배송지로 저장'))
    const addressForm = screen.getByRole('heading', { name: '배송지 확인' }).closest('form')
    fireEvent.click(within(addressForm).getByRole('button', { name: '배송지 확정' }))

    await waitFor(() => expect(updateOrderShippingAddress).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(updateOrderShippingAddress.mock.calls[0][1]).toMatchObject({
      ...shippingAddress,
      saveAsDefault: true,
    })
  })

  it('최초 확정은 dialog 없이 기존 PUT을 호출한다', async () => {
    getBuyerOrders.mockResolvedValue({
      data: makePage({ shippingAddressConfirmed: false }),
    })
    getBuyerOrder.mockResolvedValue({
      data: { ...detail, shippingAddress: null, addressConfirmedAt: null },
    })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '배송지 확정' }))
    await screen.findByRole('heading', { name: '배송지 확인' })
    fireEvent.change(screen.getByLabelText('받는 분'), { target: { value: '새 수령인' } })
    fireEvent.change(screen.getByLabelText('연락처'), { target: { value: '010-9999-8888' } })
    fireEvent.change(screen.getByLabelText('우편번호'), { target: { value: '54321' } })
    fireEvent.change(screen.getByLabelText('기본 주소'), { target: { value: '새 주소' } })
    const addressForm = screen.getByRole('heading', { name: '배송지 확인' }).closest('form')
    fireEvent.click(within(addressForm).getByRole('button', { name: '배송지 확정' }))

    await waitFor(() => expect(updateOrderShippingAddress).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('확인할 때만 PUT하고 저장 중 중복 제출을 막는다', async () => {
    let resolveUpdate
    updateOrderShippingAddress.mockReturnValue(new Promise((resolve) => {
      resolveUpdate = resolve
    }))
    renderPage()
    await openAddressForm()
    await changeAddressAndSubmit()
    const confirm = await screen.findByRole('button', { name: '배송지 변경 확정' })

    fireEvent.click(confirm)
    fireEvent.click(confirm)
    expect(updateOrderShippingAddress).toHaveBeenCalledTimes(1)
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('button', { name: '저장 중…' })).toBeDisabled()
    expect(within(dialog).getByRole('button', { name: '취소' })).toBeDisabled()
    expect(within(dialog).getByRole('button', { name: '저장 중…' }).parentElement)
      .toHaveAttribute('aria-busy', 'true')

    resolveUpdate({ data: detail })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(getBuyerOrders).toHaveBeenCalledTimes(2)
  })

  it('409 오류면 dialog를 닫고 오류 전달 후 상세와 목록을 강제 재조회한다', async () => {
    updateOrderShippingAddress.mockRejectedValue({
      response: {
        status: 409,
        data: { code: 'ORDER_STATUS_CONFLICT' },
      },
    })
    renderPage()
    await openAddressForm()
    await changeAddressAndSubmit()
    fireEvent.click(await screen.findByRole('button', { name: '배송지 변경 확정' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '주문 상태가 변경되었습니다. 최신 정보를 확인해 주세요.',
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(getBuyerOrder).toHaveBeenCalledTimes(3)
    expect(getBuyerOrders).toHaveBeenCalledTimes(2)
  })

  it('제출 직전 상세 조회가 실패하면 PUT을 중단한다', async () => {
    getBuyerOrder
      .mockResolvedValueOnce({ data: detail })
      .mockRejectedValueOnce({ response: { status: 500 } })
    renderPage()
    await openAddressForm()
    await changeAddressAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '주문 상세를 불러오지 못했습니다.',
    )
    expect(updateOrderShippingAddress).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
