import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildSellerStatusRequest,
  SELLER_ACTION,
  sellerActionLabel,
} from './sellerOrderView.js'

test('판매 주문의 준비 처리를 API 요청으로 변환한다', () => {
  assert.deepEqual(
    buildSellerStatusRequest(SELLER_ACTION.START_PREPARING),
    { status: 'PREPARING' },
  )
  assert.equal(
    sellerActionLabel(SELLER_ACTION.START_PREPARING),
    '배송 준비 시작',
  )
})

test('발송 처리 시 택배사와 송장번호를 정리해 전송한다', () => {
  assert.deepEqual(
    buildSellerStatusRequest(SELLER_ACTION.SHIP, {
      shippingCarrier: ' 우체국택배 ',
      trackingNumber: ' 1234-5678 ',
    }),
    {
      status: 'SHIPPED',
      shippingCarrier: '우체국택배',
      trackingNumber: '1234-5678',
    },
  )
})

test('발송 정보가 불완전하면 요청을 만들지 않는다', () => {
  assert.throws(
    () => buildSellerStatusRequest(SELLER_ACTION.SHIP, {
      shippingCarrier: '우체국택배',
    }),
    /택배사와 송장번호/,
  )
})
