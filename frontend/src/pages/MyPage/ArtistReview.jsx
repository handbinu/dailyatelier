// src/pages/MyPage/ArtistReview.jsx  —  내 작품 리뷰 보기 (작가 전용)
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageBanner, StarDisplay, Empty, PageWrap } from './components/atoms'
import { fmt } from './mockData'
import { getArtistReviews } from '../../api/reviewApi'
import { applyArtImageFallback, getArtImageSrc } from '../../utils/artImage'
import AccessibleDialog from '../../components/Dialog/AccessibleDialog'
import s from './ArtistReview.module.css'

const PER_PAGE   = 6
const SORT_OPTIONS = [
  { value: 'RECENT', label: '최근순'  },
  { value: 'STAR',   label: '별점순'  },
  { value: 'PRICE',  label: '가격순'  },
]

const formatDate = (value) => value ? new Date(value).toLocaleDateString('ko-KR') : ''

export default function ArtistReview() {
  const navigate = useNavigate()
  const [sort,      setSort]      = useState('RECENT')
  const [page,      setPage]      = useState(0)
  const [modal,     setModal]     = useState(null)
  const [view, setView] = useState('reviews')
  const [result, setResult]       = useState(null)
  const [summaryStats, setSummaryStats] = useState({
    totalReviewCount: 0,
    soldArtCount: 0,
    reviewedArtCount: 0,
    unreviewedArtCount: 0,
    unreviewedSoldArts: [],
  })
  const [loading, setLoading]     = useState(true)
  const [error, setError]         = useState('')
  const [retryKey, setRetryKey]   = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getArtistReviews({ sort, page, size: PER_PAGE, signal: controller.signal })
      .then(({ data }) => {
        setResult(data)
        setSummaryStats({
          totalReviewCount: data.totalReviewCount ?? 0,
          soldArtCount: data.soldArtCount ?? 0,
          reviewedArtCount: data.reviewedArtCount ?? 0,
          unreviewedArtCount: data.unreviewedArtCount ?? 0,
          unreviewedSoldArts: data.unreviewedSoldArts ?? [],
        })
      })
      .catch((requestError) => {
        if (requestError.name === 'CanceledError') return
        const status = requestError.response?.status
        if (status === 401) {
          navigate('/login', { replace: true })
          return
        }
        setError(status === 403 ? '작가만 작품 리뷰를 조회할 수 있습니다.' : '작품 리뷰를 불러오지 못했습니다.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [navigate, page, retryKey, sort])

  const reviews = result?.content ?? []
  const totalPg = result?.totalPages ?? 0
  const avgStar = result?.averageStar == null ? '-' : Number(result.averageStar).toFixed(1)
  const beginRequest = () => { setLoading(true); setError(''); setModal(null); setResult(null) }
  const handleSort = (value) => {
    if (value === sort && page === 0) return
    beginRequest(); setSort(value); setPage(0)
  }
  const handlePage = (value) => {
    if (value === page) return
    beginRequest(); setPage(value)
  }
  const retry = () => { beginRequest(); setRetryKey(key => key + 1) }

  return (
    <PageWrap>
      <PageBanner title="내 작품 리뷰 보기" crumb="작품 리뷰" />

      <div className={s.body}>
        <section className={s.summary} aria-labelledby="review-summary-title">
          <h2 id="review-summary-title" className={s.summaryTitle}>판매 작품 리뷰 현황</h2>
          <dl className={s.summaryItems}>
            <div><dt>판매 완료</dt><dd>{summaryStats.soldArtCount}</dd></div>
            <div><dt>리뷰 작성</dt><dd>{summaryStats.reviewedArtCount}</dd></div>
            <div><dt>리뷰 미작성</dt><dd>{summaryStats.unreviewedArtCount}</dd></div>
          </dl>
        </section>

        {/* 리뷰 현황 & 정렬 */}
        <div className={s.controlRow}>
          <p className={s.resultCount} aria-live="polite">
            {view === 'unreviewed'
              ? '리뷰 미작성 작품'
              : loading
                ? '리뷰 결과 조회 중'
                : <><span>총 {result?.totalElements ?? 0}개 리뷰</span><span className={s.average}>전체 리뷰 평균 별점 <strong>{avgStar}</strong></span></>}
          </p>
          <div className={s.controls}>
            {view === 'reviews' && (
              <div className={s.sortTabs}>
                {SORT_OPTIONS.map(o => (
                  <button key={o.value} className={`${s.sortTab} ${sort === o.value ? s.sortTabActive : ''}`} onClick={() => handleSort(o.value)}>
                    {o.label}
                  </button>
                ))}
              </div>
            )}
            <button type="button" className={s.viewSwitch} onClick={() => setView(current => current === 'reviews' ? 'unreviewed' : 'reviews')}>
              {view === 'reviews' ? '리뷰 미작성 작품 보기' : '작성된 리뷰 보기'}
            </button>
          </div>
        </div>

        {view === 'unreviewed'
          ? summaryStats.unreviewedSoldArts.length === 0
            ? <Empty msg="리뷰 미작성 판매 작품이 없습니다." />
            : <div className={s.grid}>
                {summaryStats.unreviewedSoldArts.map(art => (
                  <Link key={art.artId} to={`/auction/${art.artId}`} className={`${s.card} ${s.cardLink}`} aria-label={`${art.artName} 작품 상세 보기`}>
                    <div className={s.cardImg}>
                      <img src={getArtImageSrc(art.artImage)} alt={art.artName} onError={applyArtImageFallback} />
                    </div>
                    <div className={s.cardBody}>
                      <p className={s.cardArtName}>{art.artName}</p>
                      <span className={s.cardBuyer}>판매 완료 · 리뷰 미작성</span>
                    </div>
                  </Link>
                ))}
              </div>
          : loading
          ? <p className={s.state} role="status">작품 리뷰를 불러오는 중입니다.</p>
          : error
            ? <div className={s.state} role="alert"><p>{error}</p><button className={s.retryBtn} onClick={retry}>다시 시도</button></div>
          : reviews.length === 0
          ? <Empty msg="작성된 작품 리뷰가 없습니다." />
          : <div className={s.grid}>
              {reviews.map(r => (
                <ReviewCard key={r.reviewId} review={r} onClick={() => setModal(r)} />
              ))}
            </div>
        }

        {/* 페이지네이션 */}
        {view === 'reviews' && !loading && !error && totalPg > 1 && (
          <Pagination current={page} total={totalPg} onChange={handlePage} />
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
        <img src={getArtImageSrc(review.artImage)} alt={review.artName} loading="lazy" onError={applyArtImageFallback} />
        <div className={s.cardOverlay}>
          <span className={s.cardOverlayText}>상세 보기</span>
        </div>
      </div>
      <div className={s.cardBody}>
        <div className={s.cardTop}>
          <p className={s.cardArtName}>{review.artName}</p>
          <StarDisplay star={review.star} />
        </div>
        <p className={s.cardBuyer}>구매자: {review.buyerNickname}</p>
        <p className={s.cardContent}>
          {review.content.length > 50 ? review.content.slice(0, 50) + '…' : review.content}
        </p>
        <div className={s.cardFooter}>
          <span className={s.cardPrice}>낙찰가 {fmt(review.winningPrice)}원</span>
          <span className={s.cardDate}>{formatDate(review.createdAt)}</span>
        </div>
      </div>
    </button>
  )
}

/* ── 페이지네이션 ─────────────────────────────────────── */
function Pagination({ current, total, onChange }) {
  return (
    <div className={s.pagination}>
      <button className={s.pgBtn} disabled={current === 0} onClick={() => onChange(current - 1)} aria-label="이전">‹</button>
      {Array.from({ length: total }, (_, i) => i).map(n => (
        <button
          key={n}
          className={`${s.pgBtn} ${n === current ? s.pgBtnActive : ''}`}
          onClick={() => onChange(n)}
          aria-current={n === current ? 'page' : undefined}
        >
          {n + 1}
        </button>
      ))}
      <button className={s.pgBtn} disabled={current === total} onClick={() => onChange(current + 1)} aria-label="다음">›</button>
    </div>
  )
}

/* ── 리뷰 상세 모달 ───────────────────────────────────── */
function ReviewModal({ review, onClose }) {
  return (
    <AccessibleDialog
      onClose={onClose}
      labelledBy="artist-review-dialog-title"
      overlayClassName={s.modalDim}
      contentClassName={s.modal}
    >
        <h2 id="artist-review-dialog-title" className="sr-only">{review.artName} 리뷰 상세</h2>
        <button className={s.modalClose} onClick={onClose} aria-label="닫기" data-dialog-initial-focus>✕</button>

        <div className={s.modalInner}>
          {/* 작품 이미지 */}
          <div className={s.modalImg}>
            <img src={getArtImageSrc(review.artImage)} alt={review.artName} onError={applyArtImageFallback} />
            <div className={s.modalImgLabel}>{review.artName}</div>
          </div>

          {/* 리뷰 내용 */}
          <div className={s.modalDetail}>
            {/* 구매자 정보 */}
            <div className={s.buyerRow}>
              <div className={s.buyerAvatar}>{review.buyerNickname?.[0] ?? '?'}</div>
              <div>
                <p className={s.buyerName}>{review.buyerNickname}</p>
                <p className={s.reviewDate}>{formatDate(review.createdAt)} 작성</p>
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
                <span className={`${s.metaVal} ${s.metaPrice}`}>{fmt(review.winningPrice)}원</span>
              </div>
              <div className={s.metaItem}>
                <span className={s.metaKey}>작성일</span>
                <span className={s.metaVal}>{formatDate(review.createdAt)}</span>
              </div>
            </div>

            <button className={s.closeFullBtn} onClick={onClose}>닫기</button>
          </div>
        </div>
    </AccessibleDialog>
  )
}
