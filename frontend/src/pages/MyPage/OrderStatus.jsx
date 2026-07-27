// src/pages/MyPage/OrderStatus.jsx  —  주문 조회
import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { PageBanner, Badge, FilterBar, Empty, PageWrap } from './components/atoms'
import { MOCK_ORDERS, ORDER_STATUS_COLOR, fmt } from './mockData'
import s from './OrderStatus.module.css'

const FILTERS = ['전체', '입금완료', '배송중', '배송완료', '취소']

const STATUS_STEP = {
  '입금완료': 1,
  '배송중':   2,
  '배송완료': 3,
  '취소':     0,
}
const STEPS = ['주문 접수', '입금 완료', '배송 중', '배송 완료']

export default function OrderStatus() {
  const navigate = useNavigate()
  const [filter,  setFilter]  = useState('전체')
  const [detail,  setDetail]  = useState(null)   // 상세 펼침 row id

  if (!localStorage.getItem('token')) {
    alert('로그인이 필요합니다.')
    navigate('/login', { replace: true })
    return null
  }

  const items = filter === '전체'
    ? MOCK_ORDERS
    : MOCK_ORDERS.filter(o => o.status === filter)

  const toggleDetail = (id) => setDetail(prev => prev === id ? null : id)

  return (
    <PageWrap>
      <PageBanner title="주문 조회" crumb="주문 조회" />

      <div className={s.body}>
        <div style={{
          backgroundColor: '#fff9db',
          border: '1px solid #ffe066',
          borderRadius: '8px',
          padding: '12px 16px',
          fontSize: '14px',
          color: '#f08c00',
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          marginBottom: '20px',
          lineHeight: '1.5'
        }}>
          ⚠️ 해당 기능은 현재 준비 중이며, 화면의 데이터는 임시 목업 데이터입니다.
        </div>
        {/* 요약 띠 */}
        <div className={s.summary}>
          {[
            { label: '전체 주문',   value: MOCK_ORDERS.length,                                         color: '#555' },
            { label: '입금 완료',   value: MOCK_ORDERS.filter(o => o.status === '입금완료').length,   color: '#2a75c7' },
            { label: '배송 중',     value: MOCK_ORDERS.filter(o => o.status === '배송중').length,     color: '#c0622a' },
            { label: '배송 완료',   value: MOCK_ORDERS.filter(o => o.status === '배송완료').length,   color: '#1e8c4f' },
          ].map(({ label, value, color }) => (
            <div key={label} className={s.summaryItem} style={{ '--c': color }}>
              <span className={s.summaryValue}>{value}</span>
              <span className={s.summaryLabel}>{label}</span>
            </div>
          ))}
        </div>

        <FilterBar options={FILTERS} value={filter} onChange={setFilter} />

        {/* 테이블 헤더 */}
        {items.length > 0 && (
          <div className={s.tableHead}>
            <span className={s.colInfo}>주문 정보</span>
            <span className={s.colPrice}>금액</span>
            <span className={s.colStatus}>상태</span>
            <span className={s.colAction}>관리</span>
          </div>
        )}

        {items.length === 0
          ? <Empty msg="주문 내역이 없습니다." />
          : <div className={s.list}>
              {items.map(order => (
                <div key={order.id} className={s.orderGroup}>
                  {/* 주문 행 */}
                  <div className={s.orderRow} onClick={() => toggleDetail(order.id)}>
                    {/* 작품 정보 */}
                    <div className={s.colInfo}>
                      <img src={order.artImg} alt={order.artName} className={s.thumb} />
                      <div className={s.orderInfo}>
                        <p className={s.artName}>{order.artName}</p>
                        <p className={s.artist}>by {order.artist}</p>
                        <p className={s.orderNo}>{order.orderNo}</p>
                        <p className={s.orderDate}>{order.orderedAt}</p>
                      </div>
                    </div>

                    {/* 금액 */}
                    <span className={`${s.colPrice} ${s.price}`}>{fmt(order.price)}원</span>

                    {/* 상태 뱃지 */}
                    <span className={s.colStatus}>
                      <Badge
                        label={order.status}
                        color={ORDER_STATUS_COLOR[order.status] ?? 'gray'}
                      />
                    </span>

                    {/* 관리 버튼 */}
                    <div className={`${s.colAction} ${s.actions}`}>
                      {order.status === '입금완료' && (
                        <button
                          className={s.cancelBtn}
                          onClick={e => { e.stopPropagation(); alert('취소 신청이 접수되었습니다.') }}
                        >
                          취소 신청
                        </button>
                      )}
                      <button
                        className={s.detailToggle}
                        aria-expanded={detail === order.id}
                        onClick={e => { e.stopPropagation(); toggleDetail(order.id) }}
                        aria-label="상세 보기"
                      >
                        {detail === order.id ? '▲' : '▼'}
                      </button>
                    </div>
                  </div>

                  {/* 상세 패널 */}
                  {detail === order.id && (
                    <div className={s.detailPanel}>
                      {/* 배송 진행 단계 (취소 아닌 경우만) */}
                      {order.status !== '취소' && (
                        <div className={s.progressWrap}>
                          <div className={s.progressTrack}>
                            {STEPS.map((step, idx) => {
                              const cur  = STATUS_STEP[order.status] ?? 0
                              const done = idx < cur
                              const active = idx === cur - 1
                              return (
                                <div key={step} className={s.progressStep}>
                                  <div className={`${s.stepDot} ${done || active ? s.stepDotDone : ''} ${active ? s.stepDotActive : ''}`}>
                                    {done ? '✓' : idx + 1}
                                  </div>
                                  <div className={`${s.stepLine} ${done ? s.stepLineDone : ''}`} />
                                  <p className={`${s.stepLabel} ${active ? s.stepLabelActive : ''}`}>{step}</p>
                                </div>
                              )
                            })}
                          </div>
                        </div>
                      )}

                      {order.status === '취소' && (
                        <div className={s.canceledNote}>
                          <span>⚠️</span> 취소된 주문입니다.
                        </div>
                      )}

                      {/* 주문 메타 */}
                      <div className={s.detailMeta}>
                        <div className={s.metaRow}>
                          <span className={s.metaKey}>주문 번호</span>
                          <span className={s.metaVal}>{order.orderNo}</span>
                        </div>
                        <div className={s.metaRow}>
                          <span className={s.metaKey}>주문 일자</span>
                          <span className={s.metaVal}>{order.orderedAt}</span>
                        </div>
                        <div className={s.metaRow}>
                          <span className={s.metaKey}>결제 금액</span>
                          <span className={`${s.metaVal} ${s.metaPrice}`}>{fmt(order.price)}원</span>
                        </div>
                        <div className={s.metaRow}>
                          <span className={s.metaKey}>배송 주소</span>
                          <span className={s.metaVal}>서울특별시 중랑구 용마산로90길 28 (우: 02535)</span>
                        </div>
                      </div>

                      {/* 액션 링크 */}
                      <div className={s.detailActions}>
                        <Link to={`/auction/${order.id}`} className={s.linkBtn}>작품 페이지</Link>
                        {order.status === '배송완료' && (
                          <Link to={`/write-review/${order.id}`} className={`${s.linkBtn} ${s.linkBtnAccent}`}>
                            리뷰 쓰기
                          </Link>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
        }

        {/* 마이페이지 돌아가기 */}
        <div className={s.backRow}>
          <Link to="/mypage" className={s.backLink}>← 마이페이지로 돌아가기</Link>
        </div>
      </div>
    </PageWrap>
  )
}
