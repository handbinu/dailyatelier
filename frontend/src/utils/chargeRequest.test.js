import assert from 'node:assert/strict'
import test from 'node:test'
import { chargeRequestFor, invalidateChargeRequest } from './chargeRequest.js'

test('같은 충전 요청 재시도에는 기존 멱등성 키를 유지한다', () => {
  const first = chargeRequestFor(null, 50000, 'internal', () => 'key-1')
  const retry = chargeRequestFor(first, 50000, 'internal', () => 'key-2')

  assert.equal(retry, first)
  assert.equal(retry.key, 'key-1')
})

test('요청 내용 변경 후에는 새 멱등성 키를 발급한다', () => {
  const first = chargeRequestFor(null, 50000, 'internal', () => 'key-1')
  const changed = chargeRequestFor(first, 100000, 'internal', () => 'key-2')

  assert.equal(changed.key, 'key-2')
  assert.equal(invalidateChargeRequest(), null)
})
