import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { getArts, searchArts } from '../../api/artApi'
import { formatClosingTime, formatPrice, formatRemainingTime, getAuctionStatusMeta, getDeadlineMeta } from '../../utils/artDisplay'
import {
  applyArtImageFallback,
  applyArtImageFallbackIfBlank,
  getArtImageSrc,
} from '../../utils/artImage'
import styles from './Home.module.css'

/* ── 탭 필터 옵션 ──────────────────────────────────────── */
const ART_FILTERS = [
  { label: '전체', value: 'all' },
  { label: '디지털', value: 'DIGITAL' },
  { label: '실물', value: 'PHYSICAL' },
]

/* ────────────────────────────────────────────────────────── */

export default function Home() {
  const [now, setNow] = useState(0)
  const [endFilter, setEndFilter] = useState('all')
  const [newArts, setNewArts] = useState([])
  const [newArtsLoading, setNewArtsLoading] = useState(true)
  const [newArtsError, setNewArtsError] = useState('')
  const [newArtsRetryKey, setNewArtsRetryKey] = useState(0)
  const [bestArts, setBestArts] = useState([])
  const [bestArtsLoading, setBestArtsLoading] = useState(true)
  const [bestArtsError, setBestArtsError] = useState('')
  const [bestArtsRetryKey, setBestArtsRetryKey] = useState(0)
  const [endedArts, setEndedArts] = useState([])
  const [endedArtsLoading, setEndedArtsLoading] = useState(true)
  const [endedArtsError, setEndedArtsError] = useState('')
  const [endedArtsRetryKey, setEndedArtsRetryKey] = useState(0)
  useEffect(() => {
    const syncNow = () => setNow(Date.now())
    syncNow()
    const timer = window.setInterval(syncNow, 60_000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    const loadNewArts = async () => {
      setNewArtsLoading(true)
      setNewArtsError('')

      try {
        const { data } = await getArts({ page: 0, size: 3, signal: controller.signal })
        setNewArts(data.content ?? [])
      } catch (requestError) {
        if (requestError.code === 'ERR_CANCELED') return
        setNewArts([])
        setNewArtsError(requestError.response?.data?.message || '신규 작품을 불러오지 못했습니다.')
      } finally {
        if (!controller.signal.aborted) setNewArtsLoading(false)
      }
    }

    loadNewArts()
    return () => controller.abort()
  }, [newArtsRetryKey])

  useEffect(() => {
    const controller = new AbortController()

    const loadBestArts = async () => {
      setBestArtsLoading(true)
      setBestArtsError('')

      try {
        const { data } = await searchArts({
          status: 'ENDED',
          sort: 'PRICE_DESC',
          page: 0,
          size: 4,
          signal: controller.signal,
        })
        if (controller.signal.aborted) return
        setBestArts(data.content ?? [])
      } catch (requestError) {
        if (controller.signal.aborted || requestError.code === 'ERR_CANCELED') return
        setBestArts([])
        setBestArtsError(requestError.response?.data?.message || 'Best Art를 불러오지 못했습니다.')
      } finally {
        if (!controller.signal.aborted) setBestArtsLoading(false)
      }
    }

    loadBestArts()
    return () => controller.abort()
  }, [bestArtsRetryKey])

  useEffect(() => {
    const controller = new AbortController()

    const loadEndedArts = async () => {
      setEndedArtsLoading(true)
      setEndedArtsError('')

      try {
        const { data } = await searchArts({
          status: 'ENDED',
          sort: 'RECENTLY_ENDED',
          format: endFilter === 'all' ? undefined : endFilter,
          page: 0,
          size: 4,
          signal: controller.signal,
        })
        if (controller.signal.aborted) return
        setEndedArts(data.content ?? [])
      } catch (requestError) {
        if (controller.signal.aborted || requestError.code === 'ERR_CANCELED') return
        setEndedArts([])
        setEndedArtsError(requestError.response?.data?.message || '종료 작품을 불러오지 못했습니다.')
      } finally {
        if (!controller.signal.aborted) setEndedArtsLoading(false)
      }
    }

    loadEndedArts()
    return () => controller.abort()
  }, [endFilter, endedArtsRetryKey])

  const endedArtsParams = new URLSearchParams({
    status: 'ENDED',
    sort: 'RECENTLY_ENDED',
  })
  if (endFilter !== 'all') endedArtsParams.set('format', endFilter)
  const endedArtsPath = `/search?${endedArtsParams.toString()}`

  return (
    <div className={styles.page}>
      <Hero art={newArts[0]} loading={newArtsLoading} error={newArtsError} now={now} />

      {/* ── Best Art ────────────────────────────────── */}
      <section className={`${styles.section} ${styles.bestSection} ${!bestArtsLoading && !bestArtsError && bestArts.length === 1 ? styles.bestSectionSingle : ''}`} aria-label="베스트 작품">
        <div className={styles.sectionHeader}>
          <div>
            <p className={styles.eyebrow}>Auction records</p>
            <h2 className={styles.sectionTitle}>Best Art</h2>
          </div>
          <Link to="/search?status=ENDED&sort=PRICE_DESC" className={styles.sectionMore}>전체 결과 보기 →</Link>
        </div>
        <BestArtGrid
          arts={bestArts}
          loading={bestArtsLoading}
          error={bestArtsError}
          onRetry={() => setBestArtsRetryKey((key) => key + 1)}
        />
      </section>

      {/* ── 신규 작품 ────────────────────────────────── */}
      <section className={`${styles.section} ${styles.newSection}`} aria-label="신규 작품">
        <div className={styles.sectionHeader}>
          <div>
            <p className={styles.eyebrow}>New to auction</p>
            <h2 className={styles.sectionTitle}>신규 작품</h2>
          </div>
          <Link to="/auction/total?page=1" className={styles.sectionMore}>전체 작품 보기 →</Link>
        </div>
        <NewArtGrid
          arts={newArts}
          loading={newArtsLoading}
          error={newArtsError}
          now={now}
          onRetry={() => setNewArtsRetryKey((key) => key + 1)}
        />
      </section>

      {/* ── 종료 작품 ────────────────────────────────── */}
      <section className={`${styles.section} ${styles.sectionAlt}`} aria-label="종료 작품">
        <div className={styles.sectionHeader}>
          <div>
            <p className={styles.eyebrow}>Closed auction</p>
            <h2 className={styles.sectionTitle}>종료 작품</h2>
          </div>
          <div className={styles.sectionActions}>
            <FilterTabs
              options={ART_FILTERS}
              value={endFilter}
              onChange={setEndFilter}
            />
            <Link to={endedArtsPath} className={styles.sectionMore}>종료 작품 전체 보기 →</Link>
          </div>
        </div>
        <EndedArtGrid
          arts={endedArts}
          loading={endedArtsLoading}
          error={endedArtsError}
          from={endedArtsPath}
          onRetry={() => setEndedArtsRetryKey((key) => key + 1)}
        />
      </section>
    </div>
  )
}

function Hero({ art, loading, error, now }) {
  const auction = art ? getAuctionStatusMeta(art, now) : null
  const canBid = auction?.phase === 'ONGOING'

  return (
    <section className={styles.hero} aria-label="Daily Atelier 소개">
      <div className={styles.noticeBand}>
        <Link to="/info" className={styles.noticeStrip}><strong>NOTICE</strong><span>경매 이용 안내와 참여 방법을 확인하세요</span><span aria-hidden="true">→</span></Link>
      </div>
      <div className={styles.heroInner}>
        <div className={styles.heroCopy}>
          <p className={styles.heroEyebrow}>NOW BIDDING</p>
          <h1>
            오늘,<br />
            새로운 <em>작품</em>을<br />
            만나다
          </h1>
          <p>
            당신의 작품을 선보일 기회가 적었나요?<br />
            신인 작가와 기회가 적었던 작가들을 위한 사이트
          </p>
          <Link to="/auction/total" className={styles.heroCta}>경매 참여하기 <span aria-hidden="true">→</span></Link>
        </div>

        {!loading && !error && art && (
          <Link to={`/auction/${art.artId}`} state={{ from: '/' }} className={styles.heroArt} aria-label={`${art.name} 작품 상세 보기`}>
            <div className={styles.heroArtImage}>
              <img
                src={getArtImageSrc(art.imgPath)}
                alt={art.name}
                onError={applyArtImageFallback}
                onLoad={applyArtImageFallbackIfBlank}
              />
              <span className={styles.heroArtIndex}>01</span>
            </div>
            <div className={styles.heroArtMeta}>
              <span>{art.artistName || '작가 미상'}</span>
              <strong>{art.name}</strong>
              <span>{canBid ? `현재가 ${formatPrice(art.currentPrice)}원 · ${formatRemainingTime(art.closingTime, now)}` : auction.label}</span>
            </div>
          </Link>
        )}
      </div>
    </section>
  )
}

function BestArtGrid({ arts, loading, error, onRetry }) {
  if (loading) {
    return (
      <div className={styles.bestGrid} aria-label="Best Art를 불러오는 중" aria-busy="true">
        {Array.from({ length: 4 }, (_, index) => (
          <div key={index} className={styles.homeSkeletonCard}>
            <div className={styles.homeSkeletonImage} />
            <div className={styles.homeSkeletonBody}><span /><span /><span /></div>
          </div>
        ))}
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.homeFeedback} role="alert">
        <p>{error}</p>
        <button type="button" className="da-button-secondary" onClick={onRetry}>다시 시도</button>
      </div>
    )
  }

  if (arts.length === 0) {
    return <p className={styles.empty}>표시할 Best Art가 없습니다.</p>
  }

  return (
    <div className={`${styles.bestGrid} ${arts.length <= 2 ? styles.compactGrid : ''}`}>
      {arts.map((art) => {
        const sold = art.result === 'SOLD'
        return (
        <Link
          key={art.artId}
          to={`/auction/${art.artId}`}
          state={{ from: '/search?status=ENDED&sort=PRICE_DESC' }}
          className={styles.bestCard}
          aria-label={`${art.name} Best Art 상세 보기`}
        >
          <div className={styles.bestImgWrap}>
            <img
              src={getArtImageSrc(art.imgPath)}
              alt={art.name}
              loading="lazy"
              onError={applyArtImageFallback}
              onLoad={applyArtImageFallbackIfBlank}
            />
          </div>
          <div className={styles.bestInfo}>
            <span className={styles.bestTitle}>{art.name}</span>
            <span className={styles.bestPrice}>{art.artistName || '작가 미상'}</span>
            <span className={styles.bestPrice}>{sold ? '낙찰가' : '최종가'} {formatPrice(art.currentPrice)}원</span>
          </div>
        </Link>
        )
      })}
      {arts.length === 4 && <span className={styles.bestEmptyRank} aria-hidden="true">05</span>}
    </div>
  )
}

/* ── 탭 필터 ─────────────────────────────────────────── */
function FilterTabs({ options, value, onChange }) {
  return (
    <div className={styles.filterTabs} role="tablist">
      {options.map((opt) => (
        <button
          key={opt.value}
          role="tab"
          aria-selected={value === opt.value}
          className={`${styles.filterTab} ${value === opt.value ? styles.filterTabActive : ''}`}
          onClick={() => onChange(opt.value)}
        >
          {opt.label}
        </button>
      ))}
    </div>
  )
}

function NewArtGrid({ arts, loading, error, onRetry, now }) {
  if (loading) {
    return (
      <div className={styles.artGrid} aria-label="신규 작품을 불러오는 중" aria-busy="true">
        {Array.from({ length: 6 }, (_, index) => (
          <div key={index} className={styles.homeSkeletonCard}>
            <div className={styles.homeSkeletonImage} />
            <div className={styles.homeSkeletonBody}><span /><span /><span /></div>
          </div>
        ))}
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.homeFeedback} role="alert">
        <p>{error}</p>
        <button type="button" className="da-button-secondary" onClick={onRetry}>다시 시도</button>
      </div>
    )
  }

  if (arts.length === 0) {
    return <p className={styles.empty}>표시할 작품이 없습니다.</p>
  }

  return (
    <div className={`${styles.artGrid} ${arts.length <= 2 ? styles.compactGrid : ''}`}>
      {arts.map((art) => {
        const auction = getAuctionStatusMeta(art, now)
        const deadline = getDeadlineMeta(art.closingTime, now)
        const canBid = auction.phase === 'ONGOING'
        return (
          <Link
            key={art.artId}
            to={`/auction/${art.artId}`}
            state={{ from: '/auction/total?page=1' }}
            className={styles.artCard}
          >
            <div className={styles.artImgWrap}>
              <img
                src={getArtImageSrc(art.imgPath)}
                alt={art.name}
                loading="lazy"
                onError={applyArtImageFallback}
                onLoad={applyArtImageFallbackIfBlank}
              />
              <span className={`${styles.homeStatusBadge} ${auction.phase === 'ONGOING' && deadline.isUrgent ? styles.homeStatusUrgent : ''} ${auction.phase !== 'ONGOING' ? styles.homeStatusClosed : ''}`}>
                {auction.label}
              </span>
              <div className={styles.artOverlay}>
                <span className={styles.artBidBtn}>{canBid ? '응찰하기' : '작품 보기'}</span>
              </div>
            </div>
            <div className={styles.artInfo}>
              <p className={styles.artTitle}>{art.name}</p>
              <p className={styles.artArtist}>by {art.artistName || '작가 미상'}</p>
              <p className={`${styles.artTime} ${auction.phase === 'ONGOING' && deadline.isUrgent ? styles.artTimeUrgent : ''}`}>
                {canBid ? formatRemainingTime(art.closingTime, now) : auction.label}
              </p>
              <p className={styles.artClosing}>마감 <time dateTime={art.closingTime}>{formatClosingTime(art.closingTime)}</time></p>
              <p className={styles.artPrice}><span>현재가</span><strong>{formatPrice(art.currentPrice)}원</strong></p>
              {canBid && <span className={styles.inlineBidCta}>응찰하기 <span aria-hidden="true">→</span></span>}
            </div>
          </Link>
        )
      })}
    </div>
  )
}

function EndedArtGrid({ arts, loading, error, from, onRetry }) {
  if (loading) {
    return (
      <div className={styles.artGrid} aria-label="종료 작품을 불러오는 중" aria-busy="true">
        {Array.from({ length: 6 }, (_, index) => (
          <div key={index} className={styles.homeSkeletonCard}>
            <div className={styles.homeSkeletonImage} />
            <div className={styles.homeSkeletonBody}><span /><span /></div>
          </div>
        ))}
      </div>
    )
  }

  if (error) {
    return (
      <div className={styles.homeFeedback} role="alert">
        <p>{error}</p>
        <button type="button" className="da-button-secondary" onClick={onRetry}>다시 시도</button>
      </div>
    )
  }

  if (arts.length === 0) {
    return <p className={styles.empty}>선택한 유형의 종료 작품이 없습니다.</p>
  }

  return (
    <div className={`${styles.artGrid} ${arts.length <= 2 ? styles.compactGrid : ''}`}>
      {arts.map((art) => {
        const sold = art.result === 'SOLD'
        const resultLabel = sold ? '낙찰' : art.result === 'UNSOLD' ? '유찰' : '종료'
        return (
          <Link
            key={art.artId}
            to={`/auction/${art.artId}`}
            state={{ from }}
            className={`${styles.artCard} ${styles.artCardEnd}`}
            aria-label={`${art.name} 종료 작품 상세 보기`}
          >
            <div className={styles.artImgWrap}>
              <img
                src={getArtImageSrc(art.imgPath)}
                alt={art.name}
                loading="lazy"
                onError={applyArtImageFallback}
                onLoad={applyArtImageFallbackIfBlank}
              />
              <span className={`${styles.homeStatusBadge} ${sold ? styles.endedResultSold : styles.endedResultUnsold}`}>
                {resultLabel}
              </span>
            </div>
            <div className={styles.artInfo}>
              <p className={styles.artTitle}>{art.name}</p>
              <p className={styles.artPrice}>
                <span>{sold ? '낙찰가' : '최종가'}</span><strong>{formatPrice(art.currentPrice)}원</strong>
              </p>
            </div>
          </Link>
        )
      })}
    </div>
  )
}
