import assert from 'node:assert/strict'
import test from 'node:test'
import { getAuctionStatusMeta } from './artDisplay.js'

const start = '2026-08-11T10:00:00.000Z'
const closing = '2026-08-11T11:00:00.000Z'
const activeArt = { artStatus: 0, bidStartTime: start, closingTime: closing }

test('경매 상태는 시작 직전과 시작 경계를 구분한다', () => {
  assert.equal(getAuctionStatusMeta(activeArt, Date.parse(start) - 1).phase, 'UPCOMING')
  assert.equal(getAuctionStatusMeta(activeArt, Date.parse(start)).phase, 'ONGOING')
})

test('경매 상태는 마감 직전과 마감 경계를 구분한다', () => {
  assert.equal(getAuctionStatusMeta(activeArt, Date.parse(closing) - 1).phase, 'ONGOING')
  assert.equal(getAuctionStatusMeta(activeArt, Date.parse(closing)).phase, 'ENDED')
})

test('판매·유찰과 낙찰 상태는 시간과 관계없이 종료로 표시한다', () => {
  assert.equal(getAuctionStatusMeta({ ...activeArt, artStatus: 1 }, Date.parse(start) - 1).phase, 'ENDED')
  assert.deepEqual(getAuctionStatusMeta({ ...activeArt, artStatus: 2 }, Date.parse(start) - 1), { label: '낙찰', tone: 'won', phase: 'ENDED' })
})
