// src/pages/MyPage/BidStatus.jsx  —  입찰 현황 (독립 서브페이지)
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageBanner, Badge, FilterBar, Empty, PageWrap, ArtThumb, ActionBtn } from './components/atoms'
// mockData에서 공유 데이터 사용 (MyPage.jsx와 동일 소스)
import { MOCK_BIDS, STATUS_META, fmt } from './mockData'
import s from './BidStatus.module.css'

const FILTERS     = ['전체', '진행 중', '종료 임박', '종료']
const STATUS_KEY  = { '진행 중': 'ongoing', '종료 임박': 'imminent', '종료': 'ended' }

export default function BidStatus() {
  const navigate = useNavigate()
  if (!localStorage.getItem('token')) {
    alert('로그인이 필요합니다.')
    navigate('/login', { replace: true })
    return null
  }

  const [filter, setFilter] = useState('전체')
  const items    = filter === '전체' ? MOCK_BIDS : MOCK_BIDS.filter(b => b.status === STATUS_KEY[filter])
  const ongoing  = MOCK_BIDS.filter(b => b.status === 'ongoing').length
  const imminent = MOCK_BIDS.filter(b => b.status === 'imminent').length
  const ended    = MOCK_BIDS.filter(b => b.status === 'ended').length

  return (
    <PageWrap>
      <PageBanner title="입찰 현황" crumb="입찰 현황" />

      <div className={s.body}>
        {/* 요약 카드 */}
        <div className={s.summary}>
          {[
            { label: '진행 중',   value: ongoing,  color: 'var(--color-accent)' },
            { label: '종료 임박', value: imminent, color: '#c0622a'              },
            { label: '종료',      value: ended,    color: 'var(--color-text-muted)' },
          ].map(({ label, value, color }) => (
            <div key={label} className={s.summaryCard} style={{ '--c': color }}>
              <span className={s.summaryValue}>{value}<small>건</small></span>
              <span className={s.summaryLabel}>{label}</span>
            </div>
          ))}
        </div>

        <FilterBar options={FILTERS} value={filter} onChange={setFilter} />

        {items.length === 0
          ? <Empty msg="해당 입찰 내역이 없습니다." />
          : <div className={s.list}>
              {items.map(bid => <BidCard key={bid.id} bid={bid} />)}
            </div>
        }
      </div>
    </PageWrap>
  )
}

function BidCard({ bid }) {
  const meta      = STATUS_META[bid.status]
  const isLeading = bid.myPrice >= bid.currentPrice

  return (
    <div className={`${s.card} ${bid.status === 'ended' ? s.cardEnded : ''}`}>
      <div className={s.cardImg}>
        <ArtThumb src={bid.artImg} alt={bid.artName} ratio="1/1" />
        <Badge label={meta.label} color={meta.color} />
      </div>

      <div className={s.cardBody}>
        <div className={s.cardTop}>
          <div>
            <p className={s.cardTitle}>{bid.artName}</p>
            <p className={s.cardArtist}>by {bid.artist}</p>
          </div>
          <span className={`${s.leading} ${isLeading ? s.leadingYes : s.leadingNo}`}>
            {isLeading ? '최고가' : '경쟁 중'}
          </span>
        </div>

        <div className={s.priceGrid}>
          <div className={s.priceItem}>
            <span className={s.priceLabel}>내 입찰가</span>
            <span className={s.priceValue}>{fmt(bid.myPrice)}원</span>
          </div>
          <div className={s.priceDivider} />
          <div className={s.priceItem}>
            <span className={s.priceLabel}>현재 최고가</span>
            <span className={`${s.priceValue} ${s.priceHighlight}`}>{fmt(bid.currentPrice)}원</span>
          </div>
        </div>

        <p className={s.closingTime}>{bid.closingTime} 마감</p>

        <div className={s.cardActions}>
          <ActionBtn to={`/auction/${bid.id}`} variant="outline">상세 보기</ActionBtn>
          {bid.status !== 'ended' && (
            <ActionBtn to={`/auction/${bid.id}`} variant="fill">가격 올리기</ActionBtn>
          )}
        </div>
      </div>
    </div>
  )
}