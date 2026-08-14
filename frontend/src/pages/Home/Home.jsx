import { useState, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { getArts, searchArts } from '../../api/artApi'
import { formatClosingTime, formatPrice, getDeadlineMeta } from '../../utils/artDisplay'
import {
  applyArtImageFallback,
  applyArtImageFallbackIfBlank,
  getArtImageSrc,
} from '../../utils/artImage'
import styles from './Home.module.css'

/* ── 슬라이드 데이터 ───────────────────────────────────── */
const SLIDES = [
  { src: './img/main/001.jpg', alt: '슬라이드 1' },
  { src: './img/main/002.jpg', alt: '슬라이드 2' },
  { src: './img/main/003.jpg', alt: '슬라이드 3' },
]

/* ── 탭 필터 옵션 ──────────────────────────────────────── */
const ART_FILTERS = [
  { label: '전체', value: 'all' },
  { label: '디지털', value: 'DIGITAL' },
  { label: '실물', value: 'PHYSICAL' },
]

/* ────────────────────────────────────────────────────────── */

export default function Home() {
  const [slideIdx, setSlideIdx] = useState(0)
  const [endFilter, setEndFilter] = useState('all')
  const [newArts, setNewArts] = useState([])
  const [newArtsLoading, setNewArtsLoading] = useState(true)
  const [newArtsError, setNewArtsError] = useState('')
  const [newArtsRetryKey, setNewArtsRetryKey] = useState(0)
  const [endedArts, setEndedArts] = useState([])
  const [endedArtsLoading, setEndedArtsLoading] = useState(true)
  const [endedArtsError, setEndedArtsError] = useState('')
  const [endedArtsRetryKey, setEndedArtsRetryKey] = useState(0)
  const timerRef = useRef(null)
  const reduceMotionRef = useRef(false)

  /* 자동 슬라이드 */
  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')

    const stopTimer = () => {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
    const startTimer = () => {
      stopTimer()
      if (reduceMotionRef.current) return
      timerRef.current = setInterval(() => {
        setSlideIdx((i) => (i + 1) % SLIDES.length)
      }, 4000)
    }
    const syncMotionPreference = (event) => {
      reduceMotionRef.current = event.matches
      if (event.matches) stopTimer()
      else startTimer()
    }

    reduceMotionRef.current = mediaQuery.matches
    startTimer()
    mediaQuery.addEventListener('change', syncMotionPreference)
    return () => {
      stopTimer()
      mediaQuery.removeEventListener('change', syncMotionPreference)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    const loadNewArts = async () => {
      setNewArtsLoading(true)
      setNewArtsError('')

      try {
        const { data } = await getArts({ page: 0, size: 6, signal: controller.signal })
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

    const loadEndedArts = async () => {
      setEndedArtsLoading(true)
      setEndedArtsError('')

      try {
        const { data } = await searchArts({
          status: 'ENDED',
          sort: 'RECENTLY_ENDED',
          format: endFilter === 'all' ? undefined : endFilter,
          page: 0,
          size: 6,
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

  const goSlide = (idx) => {
    clearInterval(timerRef.current)
    timerRef.current = null
    setSlideIdx(idx)
    if (reduceMotionRef.current) return
    timerRef.current = setInterval(
      () => setSlideIdx((i) => (i + 1) % SLIDES.length),
      4000
    )
  }

  const endedArtsParams = new URLSearchParams({
    status: 'ENDED',
    sort: 'RECENTLY_ENDED',
  })
  if (endFilter !== 'all') endedArtsParams.set('format', endFilter)
  const endedArtsPath = `/search?${endedArtsParams.toString()}`

  return (
    <div className={styles.page}>
      {/* ── 히어로 슬라이더 ─────────────────────────── */}
      <section className={styles.hero} aria-label="메인 슬라이더">
        <div className={styles.slideTrack}>
          {SLIDES.map((s, i) => (
            <div
              key={i}
              className={`${styles.slide} ${i === slideIdx ? styles.slideActive : ''}`}
              aria-hidden={i !== slideIdx}
            >
              <img src={s.src} alt={s.alt} draggable={false} />
            </div>
          ))}
        </div>

        {/* 이전/다음 버튼 */}
        <button
          className={`${styles.slideArrow} ${styles.slideArrowLeft}`}
          onClick={() => goSlide((slideIdx - 1 + SLIDES.length) % SLIDES.length)}
          aria-label="이전 슬라이드"
        >
          ‹
        </button>
        <button
          className={`${styles.slideArrow} ${styles.slideArrowRight}`}
          onClick={() => goSlide((slideIdx + 1) % SLIDES.length)}
          aria-label="다음 슬라이드"
        >
          ›
        </button>

        {/* 페이징 */}
        <div className={styles.pageDots} role="tablist" aria-label="슬라이드 선택">
          {SLIDES.map((_, i) => (
            <button
              key={i}
              role="tab"
              aria-selected={i === slideIdx}
              className={`${styles.dot} ${i === slideIdx ? styles.dotActive : ''}`}
              onClick={() => goSlide(i)}
              aria-label={`슬라이드 ${i + 1}`}
            />
          ))}
        </div>
      </section>

      {/* ── 소개 배너 ───────────────────────────────── */}
      <section className={styles.introduce} aria-label="서비스 소개">
        <div className={styles.introduceText}>
          <h2 className={styles.introduceHeading}>
            누구나 가볍게<br />
            참여 할 수 있는<br />
            <em>미술 경매사이트</em>
          </h2>
          <p className={styles.introduceSub}>
            당신의 작품을 선보일 기회가 적었나요?<br />
            신인 작가와 기회가 적었던 작가들을 위한 사이트
          </p>
          <Link to="/auction/total" className={styles.introduceBtn}>
            경매 참여하기 →
          </Link>
        </div>
        <div className={styles.introduceImages}>
          <img
            src="/img/main_intro_1.jpg"
            alt="갤러리 소개 1"
            className={styles.introImg1}
          />
          <img
            src="/img/main_intro_2.jpg"
            alt="갤러리 소개 2"
            className={styles.introImg2}
          />
        </div>
      </section>

      {/* ── Best Art ────────────────────────────────── */}
      <section className={styles.section} aria-label="베스트 작품">
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>Best Art</h2>
          <p className={styles.sectionDesc}>
            최근 한 달 경매가가 가장 높은 작품을 선정했습니다
          </p>
        </div>
        <BestArtGrid />
      </section>

      {/* ── 신규 작품 ────────────────────────────────── */}
      <section className={styles.section} aria-label="신규 작품">
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>신규 작품</h2>
          <Link to="/auction/total?page=1" className={styles.sectionMore}>전체 작품 보기 →</Link>
        </div>
        <NewArtGrid
          arts={newArts}
          loading={newArtsLoading}
          error={newArtsError}
          onRetry={() => setNewArtsRetryKey((key) => key + 1)}
        />
      </section>

      {/* ── 종료 작품 ────────────────────────────────── */}
      <section className={`${styles.section} ${styles.sectionAlt}`} aria-label="종료 작품">
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>종료 작품</h2>
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

/* ── 베스트 아트 그리드 (정적 목업) ─────────────────── */
function BestArtGrid() {
  const BEST = [
    { id: 1, img: '/img/auction/best1.jpg', title: '자연의 속삭임', price: '1,280,000' },
    { id: 2, img: '/img/auction/best2.png', title: '도시의 감성', price: '980,000' },
    { id: 3, img: '/img/auction/best3.jpg', title: '기억의 조각', price: '840,000' },
    { id: 4, img: '/img/auction/best4.jpg', title: '빛의 여행', price: '720,000' },
  ]
  return (
    <div className={styles.bestGrid}>
      {BEST.map((art) => (
        <Link key={art.id} to={`/auction/${art.id}`} className={styles.bestCard}>
          <div className={styles.bestImgWrap}>
            <img src={art.img} alt={art.title} />
          </div>
          <div className={styles.bestInfo}>
            <span className={styles.bestTitle}>{art.title}</span>
            <span className={styles.bestPrice}>낙찰가 {art.price}원</span>
          </div>
        </Link>
      ))}
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

function NewArtGrid({ arts, loading, error, onRetry }) {
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
        <button type="button" onClick={onRetry}>다시 시도</button>
      </div>
    )
  }

  if (arts.length === 0) {
    return <p className={styles.empty}>표시할 작품이 없습니다.</p>
  }

  return (
    <div className={styles.artGrid}>
      {arts.map((art) => {
        const deadline = getDeadlineMeta(art.closingTime)
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
              <span className={`${styles.homeStatusBadge} ${deadline.isUrgent ? styles.homeStatusUrgent : ''} ${deadline.isClosed ? styles.homeStatusClosed : ''}`}>
                {deadline.label}
              </span>
              <div className={styles.artOverlay}>
                <span className={styles.artBidBtn}>작품 보기</span>
              </div>
            </div>
            <div className={styles.artInfo}>
              <p className={styles.artTitle}>{art.name}</p>
              <p className={styles.artPrice}>현재가: {formatPrice(art.currentPrice)}원</p>
              <p className={`${styles.artTime} ${deadline.isUrgent ? styles.artTimeUrgent : ''}`}>
                마감 <time dateTime={art.closingTime}>{formatClosingTime(art.closingTime)}</time>
              </p>
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
        <button type="button" onClick={onRetry}>다시 시도</button>
      </div>
    )
  }

  if (arts.length === 0) {
    return <p className={styles.empty}>선택한 유형의 종료 작품이 없습니다.</p>
  }

  return (
    <div className={styles.artGrid}>
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
                {sold ? '낙찰가' : '최종가'}: {formatPrice(art.currentPrice)}원
              </p>
            </div>
          </Link>
        )
      })}
    </div>
  )
}
