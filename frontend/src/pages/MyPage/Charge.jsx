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
const PAYMENT_METHODS = [
  { id: 'internal', label: '데모 포인트', icon: '💰' },
]
const NOTICES = [
  '이 충전은 포트폴리오 기능 체험을 위한 데모 포인트이며 실제 결제가 아닙니다.',
  '데모 포인트는 계정당 사용 중인 예치 포인트를 포함해 최대 1,000,000P까지 보유할 수 있습니다.',
  '데모 포인트는 현금 가치가 없으며 현금으로 환불하거나 출금할 수 없습니다.',
]

export default function Charge() {
  const navigate = useNavigate()
  const [amount,    setAmount]    = useState(50000)
  const [method,    setMethod]    = useState('internal')
  const [agreed,    setAgreed]    = useState(false)
  const [charging,  setCharging]  = useState(false)
  const [done,      setDone]      = useState(false)
  const [balance, setBalance] = useState(0)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [loaded, setLoaded] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [transactions, setTransactions] = useState([])
  const [charges, setCharges] = useState([])
  const chargeRequest = useRef(null)

  const token = localStorage.getItem('token')

  const finalAmount = amount
  const predicted   = balance + finalAmount

  useEffect(() => {
    if (!token) {
      navigate('/login', { replace: true })
      return undefined
    }
    const controller = new AbortController()
    setLoading(true)
    setLoaded(false)
    setError('')
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
    setAmount(val)
    chargeRequest.current = invalidateChargeRequest()
  }

  const handleCharge = async (e) => {
    e.preventDefault()
    if (finalAmount < 1000) { alert('최소 충전 금액은 1,000원입니다.'); return }
    if (!agreed) { alert('결제 유의사항에 동의해주세요.'); return }
    setCharging(true)
    setError('')
    chargeRequest.current = chargeRequestFor(
      chargeRequest.current, finalAmount, method, () => crypto.randomUUID(),
    )
    try {
      await chargePoint(finalAmount, chargeRequest.current.key)
      const [summary, transactionHistory, chargeHistory] = await Promise.all([
        getPointSummary(),
        getPointTransactions({ size: 10 }),
        getPointCharges({ size: 10 }),
      ])
      setBalance(Number(summary.data.availablePoint ?? 0))
      setTransactions(transactionHistory.data?.content ?? [])
      setCharges(chargeHistory.data?.content ?? [])
      setDone(true)
    } catch (requestError) {
      setError(requestError.response?.data?.message || '충전에 실패했습니다. 다시 시도해 주세요.')
      if (requestError.response?.status === 409) {
        await getPointSummary()
          .then(({ data }) => setBalance(Number(data.availablePoint ?? 0)))
          .catch(() => {})
      }
    } finally {
      setCharging(false)
    }
  }

  if (done) {
    return (
      <PageWrap>
        <PageBanner title="충전 완료" crumb="적립금 충전" />
        <div className={s.doneWrap}>
          <div className={s.doneIcon}>💰</div>
          <h2 className={s.doneTitle}>{fmt(finalAmount)}P 충전 완료!</h2>
          <p className={s.doneSub}>현재 사용 가능 포인트 <strong>{fmt(balance)}P</strong></p>
          <div className={s.doneActions}>
            <button className={s.doneBtn} onClick={() => navigate('/mypage')}>마이페이지로</button>
            <button className={`${s.doneBtn} ${s.doneBtnOutline}`} onClick={() => { setDone(false); setAgreed(false); chargeRequest.current = null }}>추가 충전</button>
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
                >
                  {fmt(v)}P
                </button>
              ))}
            </div>
            {/* 예상 잔액 */}
            <div className={s.predictRow}>
              <span className={s.predictLabel}>충전 후 예상 적립금</span>
              <span className={s.predictValue}>{loaded ? `${fmt(predicted)}P` : '—'}</span>
            </div>
          </section>

          {error && (
            <div className={s.errorBox} role="alert">
              <span>{error}</span>
              {!loaded && !loading && (
                <button type="button" onClick={() => setReloadKey(value => value + 1)}>다시 조회</button>
              )}
            </div>
          )}

          {/* 결제 수단 */}
          <section className={s.card}>
            <h2 className={s.cardTitle}>결제 수단</h2>
            <div className={s.methodGrid}>
              {PAYMENT_METHODS.map(m => (
                <button
                  key={m.id}
                  type="button"
                  className={`${s.methodBtn} ${method === m.id ? s.methodBtnActive : ''}`}
                  onClick={() => setMethod(m.id)}
                >
                  <span className={s.methodIcon}>{m.icon}</span>
                  <span className={s.methodLabel}>{m.label}</span>
                </button>
              ))}
            </div>
          </section>

          {/* 결제 금액 요약 */}
          <section className={s.card}>
            <h2 className={s.cardTitle}>결제 금액</h2>
            <div className={s.summaryRow}>
              <span>충전 적립금</span>
              <span className={s.summaryAmt}>{fmt(finalAmount)}P</span>
            </div>
            <hr className={s.hr} />
            <div className={`${s.summaryRow} ${s.summaryTotal}`}>
              <span>지급 데모 포인트</span>
              <span>{fmt(finalAmount)}P</span>
            </div>
          </section>

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
              />
              <span>주문 내용과 유의사항을 확인하였으며 결제 진행에 동의합니다.</span>
            </label>
          </section>

          <section className={s.card}>
            <h2 className={s.cardTitle}>최근 충전 내역</h2>
            {charges.length === 0
              ? <p>충전 내역이 없습니다.</p>
              : charges.map((charge) => (
                <div className={s.summaryRow} key={charge.chargeId}>
                  <span>{charge.status}</span>
                  <span>{fmt(charge.paidAmount || charge.requestedAmount)}P</span>
                </div>
              ))}
          </section>

          <section className={s.card}>
            <h2 className={s.cardTitle}>최근 포인트 거래</h2>
            {transactions.length === 0
              ? <p>포인트 거래 내역이 없습니다.</p>
              : transactions.map((transaction) => (
                <div className={s.summaryRow} key={transaction.transactionId}>
                  <span>{transaction.description || transaction.type}</span>
                  <span>{fmt(transaction.amount)}P</span>
                </div>
              ))}
          </section>

          <button type="submit" className={s.chargeBtn} disabled={charging || !loaded || loading}>
            {charging ? '처리 중…' : `${fmt(finalAmount)}P 데모 충전`}
          </button>
        </form>
      </div>
    </PageWrap>
  )
}
