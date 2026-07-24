import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getAllMyBids } from '../../api/userApi'
import { formatPrice } from '../../utils/artDisplay'
import { getArtImageSrc } from '../../utils/artImage'
import { PageBanner, Badge, FilterBar, Empty, PageWrap, ArtThumb, ActionBtn } from './components/atoms'
import s from './BidStatus.module.css'

const FILTERS     = ['전체', '진행 중', '종료 임박', '종료']
const STATUS_KEY  = { '진행 중': 'ONGOING', '종료 임박': 'IMMINENT', '종료': 'ENDED' }
const STATUS_META = {
  ONGOING: { label: '진행 중', color: 'green' },
  IMMINENT: { label: '종료 임박', color: 'orange' },
  ENDED: { label: '종료', color: 'gray' },
}

const formatClosingTime = (value) => {
  if (!value) return '마감 시간 정보 없음'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

export default function BidStatus() {
  const navigate = useNavigate()
  const [filter, setFilter] = useState('전체')
  const [bids, setBids] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadBids = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setBids(await getAllMyBids())
    } catch (requestError) {
      setError(requestError.response?.data?.message || '입찰 현황을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!localStorage.getItem('token')) {
      navigate('/login', { replace: true })
      return
    }
    loadBids()
  }, [loadBids, navigate])

  const items = useMemo(
    () => filter === '전체' ? bids : bids.filter((bid) => bid.auctionStatus === STATUS_KEY[filter]),
    [bids, filter],
  )
  const ongoing  = bids.filter((bid) => bid.auctionStatus === 'ONGOING').length
  const imminent = bids.filter((bid) => bid.auctionStatus === 'IMMINENT').length
  const ended    = bids.filter((bid) => bid.auctionStatus === 'ENDED').length

  return (
    <PageWrap>
      <PageBanner title="입찰 현황" crumb="입찰 현황" />

      <div className={s.body}>
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

        {loading
          ? <Empty msg="입찰 현황을 불러오는 중입니다." />
          : error
            ? (
                <div className={s.feedback}>
                  <p>{error}</p>
                  <ActionBtn onClick={loadBids} variant="outline">다시 시도</ActionBtn>
                </div>
              )
            : items.length === 0
          ? <Empty msg="해당 입찰 내역이 없습니다." />
          : <div className={s.list}>
              {items.map(bid => <BidCard key={bid.artId} bid={bid} />)}
            </div>
        }
      </div>
    </PageWrap>
  )
}

function BidCard({ bid }) {
  const meta = STATUS_META[bid.auctionStatus] ?? STATUS_META.ENDED

  return (
    <div className={`${s.card} ${bid.auctionStatus === 'ENDED' ? s.cardEnded : ''}`}>
      <div className={s.cardImg}>
        <ArtThumb src={getArtImageSrc(bid.imgPath)} alt={bid.artName} ratio="1/1" />
        <Badge label={meta.label} color={meta.color} />
      </div>

      <div className={s.cardBody}>
        <div className={s.cardTop}>
          <div>
            <p className={s.cardTitle}>{bid.artName}</p>
            <p className={s.cardArtist}>by {bid.artistName || '작가 정보 없음'}</p>
          </div>
          <span className={`${s.leading} ${bid.isLeading ? s.leadingYes : s.leadingNo}`}>
            {bid.isLeading ? '최고가' : '경쟁 중'}
          </span>
        </div>

        <div className={s.priceGrid}>
          <div className={s.priceItem}>
            <span className={s.priceLabel}>내 입찰가</span>
            <span className={s.priceValue}>{formatPrice(bid.myBidPrice)}원</span>
          </div>
          <div className={s.priceDivider} />
          <div className={s.priceItem}>
            <span className={s.priceLabel}>현재 최고가</span>
            <span className={`${s.priceValue} ${s.priceHighlight}`}>{formatPrice(bid.currentPrice)}원</span>
          </div>
        </div>

        <p className={s.closingTime}>{formatClosingTime(bid.closingTime)} 마감</p>

        <div className={s.cardActions}>
          <ActionBtn to={`/auction/${bid.artId}`} variant="outline">상세 보기</ActionBtn>
          {bid.auctionStatus !== 'ENDED' && (
            <ActionBtn to={`/auction/${bid.artId}`} variant="fill">가격 올리기</ActionBtn>
          )}
        </div>
      </div>
    </div>
  )
}
