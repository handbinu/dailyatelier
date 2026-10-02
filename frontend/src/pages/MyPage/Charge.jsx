// src/pages/MyPage/Charge.jsx  —  적립금 충전
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  chargePoint,
  getPointCharges,
  getPointSummary,
  getPointTransactions,
} from '../../api/pointApi'
import { PageBanner, PageWrap } from './components/atoms'
import { fmt } from './mockData'
import { chargeRequestFor, invalidateChargeRequest } from '../../utils/chargeRequest'
import s from './Charge.module.css'

const PRESET_AMOUNTS = [10000, 30000, 50000, 100000, 200000, 300000]
const PAYMENT_METHOD = 'internal'
const NOTICES = [
  '이 충전은 포트폴리오 기능 체험을 위한 데모 포인트이며 실제 결제가 아닙니다.',
  '데모 포인트는 계정당 사용 중인 예치 포인트를 포함해 최대 1,000,000P까지 보유할 수 있습니다.',
  '데모 포인트는 현금 가치가 없으며 현금으로 환불하거나 출금할 수 없습니다.',
]

const CHARGE_STATUS_LABELS = {
  PENDING: '처리 대기',
  PAID: '충전 완료',
  FAILED: '충전 실패',
  CANCELED: '충전 취소',
  REFUNDED: '충전 환불',
}

function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'

  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const TRANSACTION_TYPE_LABELS = {
  HOLD: '포인트 예치',
  HOLD_INCREASE: '추가 예치',
  RELEASE: '예치 해제',
  COMMIT: '포인트 결제',
  CHARGE: '포인트 충전',
  DEMO_CHARGE: '데모 포인트 충전',
  REFUND: '포인트 환불',
}

function transactionContext(transaction) {
  const description = transaction.description?.trim()
  const type = transaction.type

  if (!description) return TRANSACTION_TYPE_LABELS[type] || type || '포인트 거래'

  switch (type) {
    case 'HOLD':
    case 'HOLD_INCREASE':
      return description.replace(/\s*(?:전액|차액|추가)?\s*예치$/, '').trim() || '입찰'
    case 'RELEASE':
      return description.replace(/\s*예치 해제$/, '').trim() || '입찰'
    case 'COMMIT':
      return description.replace(/\s*(?:포인트\s*)?결제$/, '').trim() || '낙찰 주문'
    case 'CHARGE':
      return description.replace(/\s*(?:데모\s*)?포인트\s*충전$/, '').trim() || '포인트 잔액'
    case 'DEMO_CHARGE':
      return description.replace(/\s*(?:데모\s*)?포인트\s*충전$/, '').trim() || '데모 포인트'
    case 'REFUND':
      return description
        .replace(/\s*포인트\s*(?:충전\s*)?환불$/, '')
        .trim() || (description.includes('충전') ? '충전 요청' : '주문')
    default:
      return description
  }
}

function transactionBalanceDetails(transaction) {
  const availableAfter = Number(transaction.availableBalanceAfter)
  const heldAfter = Number(transaction.heldBalanceAfter)
  const availableDelta = Number(transaction.availableDelta)
  const heldDelta = Number(transaction.heldDelta)
  const amount = fmt(Math.abs(Number(transaction.amount ?? 0)))

  if (![availableAfter, heldAfter, availableDelta, heldDelta].every(Number.isFinite)) {
    return { changes: [{ label: '거래 금액', value: `${amount}P` }], after: [] }
  }

  const signed = (value) => `${value >= 0 ? '+' : '-'}${fmt(Math.abs(value))}P`

  switch (transaction.type) {
    case 'HOLD':
    case 'HOLD_INCREASE':
    case 'RELEASE':
      return {
        changes: [
          { label: '사용 가능', value: signed(availableDelta) },
          { label: '예치', value: signed(heldDelta) },
        ],
        after: [
          { label: '사용 가능 잔액', value: `${fmt(availableAfter)}P` },
          { label: '예치 잔액', value: `${fmt(heldAfter)}P` },
        ],
      }
    case 'COMMIT':
      return {
        changes: [{ label: '예치', value: signed(heldDelta) }],
        after: [{ label: '예치 잔액', value: `${fmt(heldAfter)}P` }],
      }
    case 'CHARGE':
    case 'DEMO_CHARGE':
    case 'REFUND':
      return {
        changes: [{ label: '사용 가능', value: signed(availableDelta) }],
        after: [{ label: '사용 가능 잔액', value: `${fmt(availableAfter)}P` }],
      }
    default:
      return availableDelta === 0
        ? { changes: [{ label: '거래 금액', value: `${amount}P` }], after: [] }
        : {
            changes: [{ label: '사용 가능', value: signed(availableDelta) }],
            after: [{ label: '사용 가능 잔액', value: `${fmt(availableAfter)}P` }],
          }
  }
}

function transactionAction(transaction) {
  const amount = fmt(Math.abs(Number(transaction.amount ?? 0)))

  switch (transaction.type) {
    case 'HOLD':
    case 'HOLD_INCREASE':
      return `${amount}P 예치`
    case 'RELEASE':
      return `${amount}P 예치 해제`
    case 'COMMIT':
      return `${amount}P 결제 확정`
    case 'CHARGE':
    case 'DEMO_CHARGE':
      return `+${amount}P 충전`
    case 'REFUND':
      return transaction.description?.includes('충전')
        ? `-${amount}P 충전 취소`
        : `+${amount}P 환불`
    default:
      return amount === '0' ? '거래 금액 확인 필요' : `${amount}P`
  }
}

export default function Charge() {
  const navigate = useNavigate()
  const [amount,    setAmount]    = useState(50000)
  const [agreed,    setAgreed]    = useState(false)
  const [charging,  setCharging]  = useState(false)
  const [completedCharge, setCompletedCharge] = useState(null)
  const [refreshing, setRefreshing] = useState(false)
  const [refreshError, setRefreshError] = useState('')
  const [balance, setBalance] = useState(0)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [loaded, setLoaded] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [transactions, setTransactions] = useState([])
  const [charges, setCharges] = useState([])
  const [showTransactionDetails, setShowTransactionDetails] = useState(false)
  const chargeRequest = useRef(null)
  const chargeSubmitting = useRef(false)

  const token = localStorage.getItem('token')

  const finalAmount = amount
  const predicted   = balance + finalAmount

  useEffect(() => {
    if (!token) {
      navigate('/login', { replace: true })
      return undefined
    }
    const controller = new AbortController()
    Promise.all([
      getPointSummary({ signal: controller.signal }),
      getPointTransactions({ size: 10, signal: controller.signal }),
      getPointCharges({ size: 10, signal: controller.signal }),
    ])
      .then(([summary, transactionHistory, chargeHistory]) => {
        setBalance(Number(summary.data.availablePoint ?? 0))
        setTransactions(transactionHistory.data?.content ?? [])
        setCharges(chargeHistory.data?.content ?? [])
        setLoaded(true)
      })
      .catch((requestError) => {
        if (requestError.code !== 'ERR_CANCELED') {
          setError(requestError.response?.data?.message || '포인트 잔액을 불러오지 못했습니다.')
        }
      })
      .finally(() => setLoading(false))
    return () => controller.abort()
  }, [navigate, reloadKey, token])

  if (!token) return null

  const handlePreset = (val) => {
    if (chargeSubmitting.current) return
    setAmount(val)
    chargeRequest.current = invalidateChargeRequest()
  }

  const retryInitialLoad = () => {
    setLoading(true)
    setLoaded(false)
    setError('')
    setReloadKey((value) => value + 1)
  }

  const refreshPointData = async () => {
    setRefreshing(true)
    setRefreshError('')

    const results = await Promise.allSettled([
      getPointSummary(),
      getPointTransactions({ size: 10 }),
      getPointCharges({ size: 10 }),
    ])
    const [summaryResult, transactionResult, chargeResult] = results
    const failed = []

    if (summaryResult.status === 'fulfilled') {
      const nextBalance = Number(summaryResult.value.data.availablePoint ?? 0)
      setBalance(nextBalance)
      setCompletedCharge((current) => current && ({
        ...current,
        balance: nextBalance,
        balanceLoaded: true,
      }))
    } else {
      failed.push('최신 잔액')
    }
    if (transactionResult.status === 'fulfilled') {
      setTransactions(transactionResult.value.data?.content ?? [])
    } else {
      failed.push('포인트 거래 내역')
    }
    if (chargeResult.status === 'fulfilled') {
      setCharges(chargeResult.value.data?.content ?? [])
    } else {
      failed.push('충전 내역')
    }

    if (failed.length > 0) {
      setRefreshError(`충전은 완료됐지만 ${failed.join(', ')}을 불러오지 못했습니다.`)
    }
    setRefreshing(false)
  }

  const handleCharge = async (e) => {
    e.preventDefault()
    if (chargeSubmitting.current) return
    if (finalAmount < 1000) { alert('최소 충전 금액은 1,000원입니다.'); return }
    if (!agreed) { alert('결제 유의사항에 동의해주세요.'); return }

    chargeRequest.current = chargeRequestFor(
      chargeRequest.current, finalAmount, PAYMENT_METHOD, () => crypto.randomUUID(),
    )
    const request = {
      amount: finalAmount,
      method: PAYMENT_METHOD,
      key: chargeRequest.current.key,
    }
    chargeSubmitting.current = true
    setCharging(true)
    setError('')
    try {
      const { data } = await chargePoint(request.amount, request.key)
      setCompletedCharge({
        amount: Number(data?.paidAmount ?? request.amount),
        method: request.method,
        balance: null,
        balanceLoaded: false,
      })
    } catch (requestError) {
      setError(requestError.response?.data?.message || '충전에 실패했습니다. 다시 시도해 주세요.')
      if (requestError.response?.status === 409) {
        await getPointSummary()
          .then(({ data }) => setBalance(Number(data.availablePoint ?? 0)))
          .catch(() => {})
      }
      setCharging(false)
      chargeSubmitting.current = false
      return
    }

    setCharging(false)
    await refreshPointData()
    chargeSubmitting.current = false
  }

  if (completedCharge) {
    return (
      <PageWrap>
        <PageBanner title="충전 완료" crumb="적립금 충전" />
        <div className={s.doneWrap}>
          <div className={s.doneIcon}>💰</div>
          <h2 className={s.doneTitle}>{fmt(completedCharge.amount)}P 충전 완료!</h2>
          <p className={s.doneSub}>
            현재 사용 가능 포인트{' '}
            <strong>{completedCharge.balanceLoaded ? `${fmt(completedCharge.balance)}P` : '확인 필요'}</strong>
          </p>
          {refreshing && <p role="status">최신 포인트 정보를 확인하고 있습니다.</p>}
          {refreshError && (
            <div className={s.errorBox} role="alert">
              <span>{refreshError}</span>
              <button type="button" onClick={refreshPointData} disabled={refreshing}>다시 조회</button>
            </div>
          )}
          <div className={s.doneActions}>
            <button className={s.doneBtn} onClick={() => navigate('/mypage')}>마이페이지로</button>
            <button
              className={`${s.doneBtn} ${s.doneBtnOutline}`}
              onClick={() => {
                setCompletedCharge(null)
                setRefreshError('')
                setAgreed(false)
                chargeRequest.current = null
              }}
              disabled={refreshing}
            >
              추가 충전
            </button>
          </div>
        </div>
      </PageWrap>
    )
  }

  return (
    <PageWrap>
      <PageBanner title="적립금 충전" crumb="충전하기" />

      <div className={s.body}>
        <div className={s.demoBanner} role="note">
          <strong>데모 포인트 충전</strong>
          <span>실제 결제 없이 포트폴리오의 구매 흐름을 체험하는 기능입니다.</span>
        </div>
        <form onSubmit={handleCharge} className={s.form}>
          {/* 현재 보유 */}
          <div className={s.balanceCard}>
            <span className={s.balanceLabel}>현재 보유 적립금</span>
            <span className={s.balanceValue}>
              {loaded ? `${fmt(balance)}P` : (loading ? '조회 중…' : '—')}
            </span>
          </div>

          {/* 충전 금액 선택 */}
          <section className={s.card}>
            <h2 className={s.cardTitle}>충전 금액 선택</h2>
            <div className={s.presetGrid}>
              {PRESET_AMOUNTS.map(v => (
                <button
                  key={v}
                  type="button"
                  className={`${s.presetBtn} ${amount === v ? s.presetBtnActive : ''}`}
                  onClick={() => handlePreset(v)}
                  disabled={charging}
                >
                  {fmt(v)}P
                </button>
              ))}
            </div>
            <div className={s.predictRow}>
              <div className={s.equationFormula}>
                <div className={s.equationTerm}>
                  <span>현재 잔액</span>
                  <strong>{loaded ? `${fmt(balance)}P` : '—'}</strong>
                </div>
                <span className={s.equationOperator} aria-hidden="true">+</span>
                <div className={s.equationTerm}>
                  <span>충전 금액</span>
                  <strong>{fmt(finalAmount)}P</strong>
                </div>
                <span className={`${s.equationOperator} ${s.equationEquals}`} aria-hidden="true">=</span>
                <div className={`${s.equationTerm} ${s.equationResult}`}>
                  <span>충전 후 잔액</span>
                  <strong>{loaded ? `${fmt(predicted)}P` : '—'}</strong>
                </div>
              </div>
            </div>
          </section>

          {error && (
            <div className={s.errorBox} role="alert">
              <span>{error}</span>
              {!loaded && !loading && (
                <button type="button" onClick={retryInitialLoad}>다시 조회</button>
              )}
            </div>
          )}

          {/* 유의사항 */}
          <section className={s.noticeSection}>
            <h3 className={s.noticeTitle}>유의사항</h3>
            <ul className={s.noticeList}>
              {NOTICES.map((n, i) => <li key={i} className={s.noticeItem}>- {n}</li>)}
            </ul>
            <label className={s.agreeRow}>
              <input
                type="checkbox"
                checked={agreed}
                onChange={e => setAgreed(e.target.checked)}
                className={s.agreeCheck}
                disabled={charging}
              />
              <span>주문 내용과 유의사항을 확인하였으며 결제 진행에 동의합니다.</span>
            </label>
          </section>

          <button type="submit" className={s.chargeBtn} disabled={charging || !loaded || loading}>
            {charging ? '처리 중…' : `${fmt(finalAmount)}P 데모 충전`}
          </button>

          <section className={s.historySection} aria-labelledby="charge-history-title">
            <div className={s.historySectionIntro}>
              <h2 id="charge-history-title">최근 내역</h2>
              <p>충전과 포인트 변동 기록을 확인할 수 있습니다.</p>
            </div>

            <section className={`${s.card} ${charges.length === 0 ? s.historyCardEmpty : ''}`}>
              <h3 className={s.cardTitle}>최근 충전 내역</h3>
              <p className={`${s.historyDescription} ${s.chargeHistoryDescription}`}>직접 충전한 포인트 기록</p>
              {charges.length === 0
                ? <p className={s.emptyHistory}>충전 내역이 없습니다.</p>
                : charges.map((charge) => (
                  <div className={s.chargeHistoryRow} key={charge.chargeId}>
                    <strong className={s.chargeAmount}>
                      {fmt(charge.paidAmount || charge.requestedAmount)}P <span className={s.chargeStatus}>{CHARGE_STATUS_LABELS[charge.status] || charge.status}</span>
                    </strong>
                    <span className={s.chargeDate}>{formatDateTime(charge.paidAt || charge.createdAt)}</span>
                  </div>
                ))}
            </section>

            <section className={s.card}>
              <div className={s.historyCardHeader}>
                <div>
                  <h3 className={s.cardTitle}>최근 포인트 거래</h3>
                  <p className={s.historyDescription}>입찰·결제·반환 등 포인트 변동 기록</p>
                </div>
                <div className={s.transactionToolbar}>
                  <button
                    type="button"
                    className={s.transactionToggle}
                    aria-pressed={showTransactionDetails}
                    onClick={() => setShowTransactionDetails((current) => !current)}
                  >
                    {showTransactionDetails ? '간단히 보기' : '자세히 보기'}
                  </button>
                </div>
              </div>
              {transactions.length === 0
                ? <p className={s.emptyHistory}>포인트 거래 내역이 없습니다.</p>
                : transactions.map((transaction) => {
                  const meaning = transactionContext(transaction)
                  const balanceDetails = transactionBalanceDetails(transaction)
                  const action = transactionAction(transaction)
                  return (
                    <div className={s.historyRow} key={transaction.transactionId}>
                      <strong className={s.transactionAction}>{action}</strong>
                      <div className={s.transactionMeta}>
                        <strong>{meaning}</strong>
                        <span>{formatDateTime(transaction.createdAt)}</span>
                      </div>
                      {showTransactionDetails && (
                        <div className={s.transactionDetails}>
                          <div className={s.transactionDetailRow}>
                            <span className={s.transactionDetailGroup}>변동</span>
                            {balanceDetails.changes.map((detail) => (
                              <div className={s.transactionDetailMetric} key={detail.label}>
                                <span>{detail.label}</span>
                                <strong>{detail.value}</strong>
                              </div>
                            ))}
                          </div>
                          {balanceDetails.after.length > 0 && (
                            <div className={s.transactionDetailRow}>
                              <span className={s.transactionDetailGroup}>거래 후</span>
                              {balanceDetails.after.map((detail) => (
                                <div className={s.transactionDetailMetric} key={detail.label}>
                                  <span>{detail.label}</span>
                                  <strong>{detail.value}</strong>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  )
                })}
            </section>
          </section>
        </form>
      </div>
    </PageWrap>
  )
}
