import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Charge from '../pages/MyPage/Charge'
import {
  chargePoint,
  getPointCharges,
  getPointSummary,
  getPointTransactions,
} from '../api/pointApi'

vi.mock('../api/pointApi', () => ({
  chargePoint: vi.fn(),
  getPointCharges: vi.fn(),
  getPointSummary: vi.fn(),
  getPointTransactions: vi.fn(),
}))

const emptyPage = { data: { content: [] } }

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function renderCharge() {
  return render(
    <MemoryRouter>
      <Charge />
    </MemoryRouter>,
  )
}

function mockSuccessfulLookup(balance = 0) {
  getPointSummary.mockResolvedValue({ data: { availablePoint: balance, heldPoint: 0 } })
  getPointTransactions.mockResolvedValue(emptyPage)
  getPointCharges.mockResolvedValue(emptyPage)
}

describe('데모 포인트 충전 화면', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('token', 'test-token')
    vi.stubGlobal('crypto', { randomUUID: vi.fn(() => 'charge-key') })
  })

  it('초기 잔액 조회가 끝나기 전 충전 제출을 차단한다', () => {
    const pending = new Promise(() => {})
    getPointSummary.mockReturnValue(pending)
    getPointTransactions.mockReturnValue(pending)
    getPointCharges.mockReturnValue(pending)

    renderCharge()

    expect(screen.getByRole('button', { name: '50,000P 데모 충전' })).toBeDisabled()
    expect(screen.getByText('조회 중…')).toBeInTheDocument()
  })

  it('잔액 조회 실패 시 오류 안내와 재조회 버튼을 표시한다', async () => {
    getPointSummary.mockRejectedValue({
      response: { data: { message: '잔액 조회에 실패했습니다.' } },
    })
    getPointTransactions.mockResolvedValue(emptyPage)
    getPointCharges.mockResolvedValue(emptyPage)

    renderCharge()

    expect(await screen.findByRole('alert')).toHaveTextContent('잔액 조회에 실패했습니다.')
    expect(screen.getByRole('button', { name: '다시 조회' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '50,000P 데모 충전' })).toBeDisabled()
  })

  it('재조회 버튼 클릭 시 포인트 조회 API를 다시 호출한다', async () => {
    getPointSummary
      .mockRejectedValueOnce({ response: { data: { message: '조회 실패' } } })
      .mockResolvedValueOnce({ data: { availablePoint: 20_000, heldPoint: 0 } })
    getPointTransactions.mockResolvedValue(emptyPage)
    getPointCharges.mockResolvedValue(emptyPage)

    renderCharge()
    fireEvent.click(await screen.findByRole('button', { name: '다시 조회' }))

    await waitFor(() => expect(getPointSummary).toHaveBeenCalledTimes(2))
    expect(getPointTransactions).toHaveBeenCalledTimes(2)
    expect(getPointCharges).toHaveBeenCalledTimes(2)
    expect(await screen.findByText('20,000P')).toBeInTheDocument()
  })

  it('실제 결제가 아닌 데모 기능임을 안내한다', () => {
    mockSuccessfulLookup()

    renderCharge()

    expect(screen.getByText('데모 포인트 충전')).toBeInTheDocument()
    expect(screen.getByText(/실제 결제 없이 포트폴리오의 구매 흐름을 체험/)).toBeInTheDocument()
    expect(screen.getByText(/현금 가치가 없으며 현금으로 환불하거나 출금할 수 없습니다/))
      .toBeInTheDocument()
  })

  it('충전 성공 후 최신 잔액을 조회하고 완료 화면을 표시한다', async () => {
    getPointSummary
      .mockResolvedValueOnce({ data: { availablePoint: 1_000, heldPoint: 0 } })
      .mockResolvedValueOnce({ data: { availablePoint: 51_000, heldPoint: 0 } })
    getPointTransactions.mockResolvedValue(emptyPage)
    getPointCharges.mockResolvedValue(emptyPage)
    chargePoint.mockResolvedValue({ data: { demo: true, paidAmount: 50_000 } })

    renderCharge()

    const submit = await screen.findByRole('button', { name: '50,000P 데모 충전' })
    await waitFor(() => expect(submit).toBeEnabled())
    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.click(submit)

    expect(await screen.findByRole('heading', { name: '50,000P 충전 완료!' }))
      .toBeInTheDocument()
    expect(screen.getByText('51,000P')).toBeInTheDocument()
    expect(chargePoint).toHaveBeenCalledWith(50_000, 'charge-key')
    expect(getPointSummary).toHaveBeenCalledTimes(2)
  })

  it('충전 요청 중 입력 변경과 같은 렌더의 연속 제출을 막고 요청 금액을 표시한다', async () => {
    mockSuccessfulLookup(1_000)
    const charge = deferred()
    chargePoint.mockReturnValue(charge.promise)

    renderCharge()

    const submit = await screen.findByRole('button', { name: '50,000P 데모 충전' })
    await waitFor(() => expect(submit).toBeEnabled())
    fireEvent.click(screen.getByRole('checkbox'))
    const form = submit.closest('form')
    fireEvent.submit(form)
    fireEvent.submit(form)

    expect(chargePoint).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: '100,000P' })).toBeDisabled()
    expect(screen.getByRole('checkbox')).toBeDisabled()

    charge.resolve({ data: { paidAmount: 50_000 } })

    expect(await screen.findByRole('heading', { name: '50,000P 충전 완료!' }))
      .toBeInTheDocument()
  })

  it('충전 성공 후 재조회 실패를 성공과 분리하고 완료 상태에서 다시 조회한다', async () => {
    getPointSummary
      .mockResolvedValueOnce({ data: { availablePoint: 1_000, heldPoint: 0 } })
      .mockRejectedValueOnce({ response: { data: { message: '잔액 조회 실패' } } })
      .mockResolvedValueOnce({ data: { availablePoint: 51_000, heldPoint: 0 } })
    getPointTransactions.mockResolvedValue(emptyPage)
    getPointCharges.mockResolvedValue(emptyPage)
    chargePoint.mockResolvedValue({ data: { paidAmount: 50_000 } })

    renderCharge()

    const submit = await screen.findByRole('button', { name: '50,000P 데모 충전' })
    await waitFor(() => expect(submit).toBeEnabled())
    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.click(submit)

    expect(await screen.findByRole('heading', { name: '50,000P 충전 완료!' }))
      .toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent('충전은 완료됐지만 최신 잔액')
    expect(screen.queryByText(/충전에 실패했습니다/)).not.toBeInTheDocument()
    expect(screen.getByText('확인 필요')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '다시 조회' }))

    expect(await screen.findByText('51,000P')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
    expect(chargePoint).toHaveBeenCalledTimes(1)
  })
})
