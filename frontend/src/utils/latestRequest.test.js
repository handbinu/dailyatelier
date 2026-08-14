import test from 'node:test'
import assert from 'node:assert/strict'
import { createLatestRequest } from './latestRequest.js'

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

const handlers = (events, name) => ({
  onStart: () => events.push(`${name}:start`),
  onSuccess: (value) => events.push(`${name}:success:${value}`),
  onError: (error) => events.push(`${name}:error:${error.message}`),
  onFinally: () => events.push(`${name}:finally`),
})

test('최신 요청이 먼저 성공하면 이전 요청의 늦은 성공을 폐기한다', async () => {
  const latestRequest = createLatestRequest()
  const previous = deferred()
  const current = deferred()
  const events = []
  let previousSignal

  const previousRun = latestRequest.run(({ signal }) => {
    previousSignal = signal
    return previous.promise
  }, handlers(events, 'previous'))
  const currentRun = latestRequest.run(() => current.promise, handlers(events, 'current'))

  assert.equal(previousSignal.aborted, true)
  current.resolve('new')
  await currentRun
  previous.resolve('old')
  await previousRun

  assert.deepEqual(events, [
    'previous:start',
    'current:start',
    'current:success:new',
    'current:finally',
  ])
})

test('최신 요청이 실패하면 그 오류만 반영하고 이전 요청의 늦은 실패를 폐기한다', async () => {
  const latestRequest = createLatestRequest()
  const previous = deferred()
  const current = deferred()
  const events = []

  const previousRun = latestRequest.run(
    () => previous.promise,
    handlers(events, 'previous'),
  )
  const currentRun = latestRequest.run(() => current.promise, handlers(events, 'current'))

  current.reject(new Error('current failed'))
  await currentRun
  previous.reject(new Error('previous failed'))
  await previousRun

  assert.deepEqual(events, [
    'previous:start',
    'current:start',
    'current:error:current failed',
    'current:finally',
  ])
})

test('이전 요청의 abort 오류는 사용자 오류로 반영하지 않는다', async () => {
  const latestRequest = createLatestRequest()
  const current = deferred()
  const events = []

  const previousRun = latestRequest.run(({ signal }) => new Promise((resolve, reject) => {
    signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
  }), handlers(events, 'previous'))
  const currentRun = latestRequest.run(() => current.promise, handlers(events, 'current'))

  current.resolve('new')
  await Promise.all([previousRun, currentRun])

  assert.deepEqual(events, [
    'previous:start',
    'current:start',
    'current:success:new',
    'current:finally',
  ])
})

test('dispose는 활성 요청을 취소하고 이후 상태 콜백과 새 요청을 차단한다', async () => {
  const latestRequest = createLatestRequest()
  const active = deferred()
  const events = []
  let activeSignal
  let executedAfterDispose = false

  const activeRun = latestRequest.run(({ signal }) => {
    activeSignal = signal
    return active.promise
  }, handlers(events, 'active'))

  latestRequest.dispose()
  assert.equal(activeSignal.aborted, true)

  active.resolve('late')
  await activeRun
  await latestRequest.run(() => {
    executedAfterDispose = true
  }, handlers(events, 'disposed'))

  assert.equal(executedAfterDispose, false)
  assert.deepEqual(events, ['active:start'])
})
