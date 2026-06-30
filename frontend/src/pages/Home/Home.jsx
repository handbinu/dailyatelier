import { useState, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
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
  { label: '디지털', value: '디지털' },
  { label: '실물', value: '실물' },
]

/* ────────────────────────────────────────────────────────── */

export default function Home() {
  const [slideIdx, setSlideIdx] = useState(0)
  const [newFilter, setNewFilter] = useState('all')
  const [endFilter, setEndFilter] = useState('all')
  const timerRef = useRef(null)

  /* 자동 슬라이드 */
  useEffect(() => {
    timerRef.current = setInterval(() => {
      setSlideIdx((i) => (i + 1) % SLIDES.length)
    }, 4000)
    return () => clearInterval(timerRef.current)
  }, [])

  const goSlide = (idx) => {
    clearInterval(timerRef.current)
    setSlideIdx(idx)
    timerRef.current = setInterval(
      () => setSlideIdx((i) => (i + 1) % SLIDES.length),
      4000
    )
  }

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
          <FilterTabs
            options={ART_FILTERS}
            value={newFilter}
            onChange={setNewFilter}
          />
        </div>
        <ArtGrid filter={newFilter} type="new" />
      </section>

      {/* ── 종료 작품 ────────────────────────────────── */}
      <section className={`${styles.section} ${styles.sectionAlt}`} aria-label="종료 작품">
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>종료 작품</h2>
          <FilterTabs
            options={ART_FILTERS}
            value={endFilter}
            onChange={setEndFilter}
          />
        </div>
        <ArtGrid filter={endFilter} type="end" />
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

/* ── 작품 그리드 (정적 목업 데이터) ─────────────────── */
const MOCK_ARTS = {
  new: [
    { id: 'a1', img: '/img/auction/new_1.jpg', title: '엎질러진 자연', price: '209,001', time: '18:49:12', type: '실물' },
    { id: 'a2', img: '/img/auction/new_2.jpg', title: '노을', price: '360,064', time: '67:33:57', type: '실물' },
    { id: 'a3', img: '/img/auction/new_3.jpg', title: '목도리냥', price: '278,200', time: '65:04:28', type: '디지털' },
    { id: 'a4', img: '/img/auction/new_4.png', title: '우리 집 앞', price: '459,768', time: '62:39:39', type: '실물' },
    { id: 'a5', img: '/img/auction/new_5.png', title: '멍때림', price: '203,200', time: '58:12:00', type: '디지털' },
    { id: 'a6', img: '/img/auction/new_6.png', title: '골목', price: '195,000', time: '44:30:10', type: '실물' },
  ],
  end: [
    { id: 'e1', img: '/img/auction/done_digi_1.jpg', title: '연예인 병', price: '530,000', type: '디지털' },
    { id: 'e2', img: '/img/auction/done_digi_2.jpg', title: '세사람', price: '430,000', type: '디지털' },
    { id: 'e3', img: '/img/auction/done_real_1.jpg', title: '숲속에서', price: '720,000', type: '실물' },
    { id: 'e4', img: '/img/auction/done_real_2.jpg', title: '어린시절', price: '610,000', type: '실물' },
    { id: 'e5', img: '/img/auction/done_digi_3.jpg', title: '우주비행사', price: '380,000', type: '디지털' },
    { id: 'e6', img: '/img/auction/done_real_3.jpg', title: '내 속마음', price: '850,000', type: '실물' },
  ],
}

function ArtGrid({ filter, type }) {
  const all = MOCK_ARTS[type] ?? []
  const items =
    filter === 'all' ? all : all.filter((a) => a.type === filter)

  if (items.length === 0) {
    return (
      <p className={styles.empty}>선택한 유형의 작품이 없습니다.</p>
    )
  }

  return (
    <div className={styles.artGrid}>
      {items.map((art) => (
        <Link
          key={art.id}
          to={`/auction/${art.id}`}
          className={`${styles.artCard} ${type === 'end' ? styles.artCardEnd : ''}`}
        >
          <div className={styles.artImgWrap}>
            <img src={art.img} alt={art.title} />
            {type === 'new' && (
              <div className={styles.artOverlay}>
                <span className={styles.artBidBtn}>입찰하기</span>
              </div>
            )}
          </div>
          <div className={styles.artInfo}>
            <p className={styles.artTitle}>{art.title}</p>
            {type === 'new' ? (
              <>
                <p className={styles.artPrice}>현재가: {art.price}원</p>
                <p className={styles.artTime}>⏱ {art.time}</p>
              </>
            ) : (
              <p className={styles.artPrice}>낙찰가: {art.price}원</p>
            )}
          </div>
        </Link>
      ))}
    </div>
  )
}