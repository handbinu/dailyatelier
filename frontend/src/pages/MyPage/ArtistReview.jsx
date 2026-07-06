// src/pages/MyPage/ArtistReview.jsx  —  내 작품 리뷰 보기 (작가 전용)
import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageBanner, StarDisplay, Empty, PageWrap } from './components/atoms'
import { MOCK_ARTIST_REVIEWS, MOCK_MY_ARTS, fmt } from './mockData'
import s from './ArtistReview.module.css'

const PER_PAGE   = 6
const SORT_OPTIONS = [
  { value: 'recent', label: '최근순'  },
  { value: 'star',   label: '별점순'  },
  { value: 'price',  label: '가격순'  },
]

function sortItems(arr, sort) {
  const cp = [...arr]
  if (sort === 'star')  return cp.sort((a, b) => b.star - a.star)
  if (sort === 'price') return cp.sort((a, b) => b.finalPrice - a.finalPrice)
  return cp.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
}

export default function ArtistReview() {
  const navigate = useNavigate()
  const token      = localStorage.getItem('token')
  const userStatus = Number(localStorage.getItem('userStatus') ?? 0)

  if (!token) { alert('로그인이 필요합니다.'); navigate('/login', { replace: true }); return null }
  if (userStatus !== 1) { alert('작가 회원 전용 페이지입니다.'); navigate('/', { replace: true }); return null }

  const artNames  = ['전체', ...new Set(MOCK_MY_ARTS.map(a => a.name))]

  const [artFilter, setArtFilter] = useState('전체')
  const [sort,      setSort]      = useState('recent')
  const [page,      setPage]      = useState(1)
  const [modal,     setModal]     = useState(null)

  const filtered = useMemo(() => {
    const base = artFilter === '전체'
      ? MOCK_ARTIST_REVIEWS
      : MOCK_ARTIST_REVIEWS.filter(r => r.artName === artFilter)
    return sortItems(base, sort)
  }, [artFilter, sort])

  const totalPg = Math.max(1, Math.ceil(filtered.length / PER_PAGE))
  const paged   = filtered.slice((page - 1) * PER_PAGE, page * PER_PAGE)

  const avgStar = filtered.length
    ? (filtered.reduce((s, r) => s + r.star, 0) / filtered.length).toFixed(1)
    : '-'

  const handleFilter = (v) => { setArtFilter(v); setPage(1) }
  const handleSort   = (v) => { setSort(v);      setPage(1) }

  return (
    <PageWrap>
      <PageBanner title="내 작품 리뷰 보기" crumb="작품 리뷰" />

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
        {/* 통계 카드 */}
        <div className={s.statRow}>
          <div className={s.statCard}>
            <span className={s.statValue}>{MOCK_ARTIST_REVIEWS.length}</span>
            <span className={s.statLabel}>전체 리뷰</span>
          </div>
          <div className={s.statCard}>
            <span className={s.statValue}>{avgStar}</span>
            <span className={s.statLabel}>평균 별점</span>
          </div>
          <div className={s.statCard}>
            <span className={s.statValue}>{MOCK_MY_ARTS.filter(a => a.status === 'ended').length}</span>
            <span className={s.statLabel}>종료된 작품</span>
          </div>
        </div>

        {/* 필터 & 정렬 */}
        <div className={s.controlRow}>
          <div className={s.artFilter}>
            {artNames.map(name => (
              <button
                key={name}
                className={`${s.artFilterBtn} ${artFilter === name ? s.artFilterBtnActive : ''}`}
                onClick={() => handleFilter(name)}
              >
                {name}
              </button>
            ))}
          </div>
          <div className={s.sortTabs}>
            {SORT_OPTIONS.map(o => (
              <button
                key={o.value}
                className={`${s.sortTab} ${sort === o.value ? s.sortTabActive : ''}`}
                onClick={() => handleSort(o.value)}
              >
                {o.label}
              </button>
            ))}
          </div>
        </div>

        <p className={s.resultCount}>총 {filtered.length}개 리뷰</p>

        {/* 리뷰 카드 그리드 */}
        {paged.length === 0
          ? <Empty msg="이 작품에 달린 리뷰가 없습니다." />
          : <div className={s.grid}>
              {paged.map(r => (
                <ReviewCard key={r.id} review={r} onClick={() => setModal(r)} />
              ))}
            </div>
        }

        {/* 페이지네이션 */}
        {totalPg > 1 && (
          <Pagination current={page} total={totalPg} onChange={setPage} />
        )}
      </div>

      {/* 상세 모달 */}
      {modal && <ReviewModal review={modal} onClose={() => setModal(null)} />}
    </PageWrap>
  )
}

/* ── 리뷰 카드 ─────────────────────────────────────────── */
function ReviewCard({ review, onClick }) {
  return (
    <button className={s.card} onClick={onClick} aria-label={`${review.artName} 리뷰 상세 보기`}>
      <div className={s.cardImg}>
        <img src={review.artImg} alt={review.artName} loading="lazy" />
        <div className={s.cardOverlay}>
          <span className={s.cardOverlayText}>상세 보기</span>
        </div>
      </div>
      <div className={s.cardBody}>
        <div className={s.cardTop}>
          <p className={s.cardArtName}>{review.artName}</p>
          <StarDisplay star={review.star} />
        </div>
        <p className={s.cardBuyer}>구매자: {review.buyer}</p>
        <p className={s.cardContent}>
          {review.content.length > 50 ? review.content.slice(0, 50) + '…' : review.content}
        </p>
        <div className={s.cardFooter}>
          <span className={s.cardPrice}>낙찰가 {fmt(review.finalPrice)}원</span>
          <span className={s.cardDate}>{review.createdAt}</span>
        </div>
      </div>
    </button>
  )
}

/* ── 페이지네이션 ─────────────────────────────────────── */
function Pagination({ current, total, onChange }) {
  return (
    <div className={s.pagination}>
      <button className={s.pgBtn} disabled={current === 1} onClick={() => onChange(current - 1)} aria-label="이전">‹</button>
      {Array.from({ length: total }, (_, i) => i + 1).map(n => (
        <button
          key={n}
          className={`${s.pgBtn} ${n === current ? s.pgBtnActive : ''}`}
          onClick={() => onChange(n)}
          aria-current={n === current ? 'page' : undefined}
        >
          {n}
        </button>
      ))}
      <button className={s.pgBtn} disabled={current === total} onClick={() => onChange(current + 1)} aria-label="다음">›</button>
    </div>
  )
}

/* ── 리뷰 상세 모달 ───────────────────────────────────── */
function ReviewModal({ review, onClose }) {
  const handleBg = (e) => { if (e.target === e.currentTarget) onClose() }

  return (
    <div className={s.modalDim} onClick={handleBg} role="dialog" aria-modal="true">
      <div className={s.modal}>
        <button className={s.modalClose} onClick={onClose} aria-label="닫기">✕</button>

        <div className={s.modalInner}>
          {/* 작품 이미지 */}
          <div className={s.modalImg}>
            <img src={review.artImg} alt={review.artName} />
            <div className={s.modalImgLabel}>{review.artName}</div>
          </div>

          {/* 리뷰 내용 */}
          <div className={s.modalDetail}>
            {/* 구매자 정보 */}
            <div className={s.buyerRow}>
              <div className={s.buyerAvatar}>{review.buyer[0]}</div>
              <div>
                <p className={s.buyerName}>{review.buyer}</p>
                <p className={s.reviewDate}>{review.createdAt} 작성</p>
              </div>
            </div>

            <hr className={s.hr} />

            {/* 별점 */}
            <div className={s.starRow}>
              <StarDisplay star={review.star} />
              <span className={s.starBig}>{review.star} / 10</span>
            </div>

            <hr className={s.hr} />

            {/* 리뷰 본문 */}
            <div className={s.reviewBody}>
              <p>{review.content}</p>
            </div>

            <hr className={s.hr} />

            {/* 메타 */}
            <div className={s.metaGrid}>
              <div className={s.metaItem}>
                <span className={s.metaKey}>작품명</span>
                <span className={s.metaVal}>{review.artName}</span>
              </div>
              <div className={s.metaItem}>
                <span className={s.metaKey}>낙찰가</span>
                <span className={`${s.metaVal} ${s.metaPrice}`}>{fmt(review.finalPrice)}원</span>
              </div>
            </div>

            <button className={s.closeFullBtn} onClick={onClose}>닫기</button>
          </div>
        </div>
      </div>
    </div>
  )
}