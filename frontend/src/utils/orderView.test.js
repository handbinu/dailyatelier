import test from 'node:test'
import assert from 'node:assert/strict'
import {
  formatShippingAddress,
  getOrderError,
  getOrderStatusView,
  getRefundRequestStatusView,
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

test('환불 완료와 환불 요청 처리 상태를 명확한 라벨로 변환한다', () => {
  assert.equal(getOrderStatusView('REFUNDED').label, '환불 완료')
  assert.equal(getRefundRequestStatusView('REQUESTED').label, '환불 요청됨')
  assert.equal(getRefundRequestStatusView('APPROVED').label, '환불 승인됨')
  assert.equal(getRefundRequestStatusView('REJECTED').label, '환불 거절됨')
  assert.equal(getRefundRequestStatusView(), null)
  assert.equal(getRefundRequestStatusView('UNKNOWN'), null)
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

test('판매 주문 권한 오류를 접근 거부 안내로 변환한다', () => {
  const forbidden = getOrderError({
    response: {
      status: 403,
      data: { code: 'ORDER_ACCESS_DENIED' },
    },
  })

  assert.equal(forbidden.status, 403)
  assert.match(forbidden.message, /권한/)
  assert.equal(forbidden.shouldLogin, false)
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
