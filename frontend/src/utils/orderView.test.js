import test from 'node:test'
import assert from 'node:assert/strict'
import {
  formatShippingAddress,
  getOrderError,
  getOrderStatusView,
} from './orderView.js'

test('전체 주문 상태를 화면 표시 정보로 변환한다', () => {
  const statuses = [
    'PAYMENT_PENDING',
    'PAID',
    'PREPARING',
    'SHIPPED',
    'DELIVERED',
    'CONFIRMED',
    'CANCELED',
    'REFUNDED',
  ]

  statuses.forEach((status) => {
    const view = getOrderStatusView(status)
    assert.notEqual(view.label, status)
    assert.ok(view.color)
  })
})

test('주문 API 오류를 사용자 메시지와 후속 처리로 변환한다', () => {
  const conflict = getOrderError({
    response: {
      status: 409,
      data: { code: 'ORDER_STATUS_CONFLICT' },
    },
  })
  const unauthorized = getOrderError({
    response: {
      status: 401,
      data: { code: 'UNAUTHORIZED' },
    },
  })

  assert.equal(conflict.shouldReload, true)
  assert.match(conflict.message, /최신 정보/)
  assert.equal(unauthorized.shouldLogin, true)
})

test('배송지 스냅샷을 우편번호와 상세 주소까지 표시한다', () => {
  assert.equal(formatShippingAddress(null), '배송지 미확정')
  assert.equal(
    formatShippingAddress({
      zipCode: '02535',
      address1: '서울특별시 중랑구',
      address2: '101호',
    }),
    '(02535) 서울특별시 중랑구 101호',
  )
})
