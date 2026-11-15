import test from 'node:test'
import assert from 'node:assert/strict'
import { createOrderRequestGuard } from './orderRequestGuard.js'

test('같은 주문의 중복 상태 변경 요청을 막고 완료 후 다시 허용한다', () => {
  const guard = createOrderRequestGuard()

  assert.equal(guard.begin(1), true)
  assert.equal(guard.begin(1), false)
  assert.equal(guard.isActive(1), true)

  guard.end(1)

  assert.equal(guard.isActive(1), false)
  assert.equal(guard.begin(1), true)
})

test('서로 다른 주문 요청은 동시에 허용한다', () => {
  const guard = createOrderRequestGuard()

  assert.equal(guard.begin(1), true)
  assert.equal(guard.begin(2), true)
})
