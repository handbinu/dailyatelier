// src/pages/MyPage/MyReview.jsx  —  내가 쓴 리뷰
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageBanner, StarDisplay, Empty, PageWrap, ActionBtn } from './components/atoms'
import { fmt } from './mockData'
import { getMyReviews } from '../../api/reviewApi'
import { applyArtImageFallback, getArtImageSrc } from '../../utils/artImage'
import AccessibleDialog from '../../components/Dialog/AccessibleDialog'
import s from './MyReview.module.css'

const PER_PAGE = 6

const SORT_OPTIONS = [
  { value: 'RECENT', label: '최근 리뷰순' },
  { value: 'STAR',   label: '별점순'     },
  { value: 'PRICE',  label: '가격순'     },
]

const formatDate = (value) => value ? new Date(value).toLocaleDateString('ko-KR') : ''

export default function MyReview() {
  const navigate = useNavigate()
  const [sort, setSort]       = useState('RECENT')
  const [page, setPage]       = useState(0)
  const [modal, setModal]     = useState(null)   // 선택된 리뷰 객체
  const [result, setResult]   = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState('')
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getMyReviews({ sort, page, size: PER_PAGE, signal: controller.signal })
      .then(({ data }) => setResult(data))
      .catch((requestError) => {
        if (requestError.name === 'CanceledError') return
        if (requestError.response?.status === 401) {
          navigate('/login', { replace: true })
          return
        }
        setError('리뷰 목록을 불러오지 못했습니다.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [navigate, page, retryKey, sort])

  const reviews = result?.content ?? []
  const total = result?.totalElements ?? 0
  const totalPg = result?.totalPages ?? 0
  const beginRequest = () => { setLoading(true); setError(''); setModal(null) }
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
      <PageBanner title="내가 쓴 리뷰" crumb="나의 리뷰" />

      <div className={s.body}>
        {/* 상단 컨트롤 */}
        <div className={s.controls}>
          <span className={s.total}>총 {total}개</span>
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

        {/* 리뷰 카드 그리드 */}
        {loading
          ? <p className={s.state} role="status">리뷰를 불러오는 중입니다.</p>
          : error
            ? <div className={s.state} role="alert"><p>{error}</p><button className={s.retryBtn} onClick={retry}>다시 시도</button></div>
          : reviews.length === 0
          ? <Empty msg="작성한 리뷰가 없습니다." />
          : <div className={s.grid}>
              {reviews.map(r => (
                <button
                  key={r.reviewId}
                  className={s.card}
                  onClick={() => setModal(r)}
                  aria-label={`${r.artName} 리뷰 상세 보기`}
                >
                  <div className={s.cardImg}>
                    <img src={getArtImageSrc(r.artImage)} alt={r.artName} loading="lazy" onError={applyArtImageFallback} />
                    <div className={s.cardOverlay}>
                      <span className={s.cardOverlayText}>상세 보기</span>
                    </div>
                  </div>
                  <div className={s.cardBody}>
                    <p className={s.cardTitle}>{r.artName}</p>
                    <StarDisplay star={r.star} />
                    <p className={s.cardContent}>
                      {r.content.length > 40 ? r.content.slice(0, 40) + '…' : r.content}
                    </p>
                    <p className={s.cardPrice}>낙찰가 {fmt(r.winningPrice)}원</p>
                    <p className={s.cardDate}>{formatDate(r.createdAt)}</p>
                  </div>
                </button>
              ))}
            </div>
        }

        {/* 페이지네이션 */}
        {!loading && !error && totalPg > 1 && (
          <Pagination current={page} total={totalPg} onChange={handlePage} />
        )}

        {/* 리뷰 수정 링크 */}
        <div className={s.writeHint}>
          낙찰 작품의 리뷰를 수정하려면{' '}
          <Link to="/mypage/successful-bid" className={s.writeLink}>낙찰 작품 페이지</Link>
          에서 '리뷰 수정'을 눌러주세요.
        </div>
      </div>

      {/* 상세 모달 */}
      {modal && <ReviewModal review={modal} onClose={() => setModal(null)} />}
    </PageWrap>
  )
}

/* ── 페이지네이션 ─────────────────────────────────────────── */
function Pagination({ current, total, onChange }) {
  return (
    <div className={s.pagination}>
      <button
        className={s.pgBtn}
        disabled={current === 0}
        onClick={() => onChange(current - 1)}
        aria-label="이전 페이지"
      >‹</button>

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

      <button
        className={s.pgBtn}
        disabled={current === total}
        onClick={() => onChange(current + 1)}
        aria-label="다음 페이지"
      >›</button>
    </div>
  )
}

/* ── 리뷰 상세 모달 ──────────────────────────────────────── */
function ReviewModal({ review, onClose }) {
  return (
    <AccessibleDialog
      onClose={onClose}
      labelledBy="my-review-dialog-title"
      overlayClassName={s.modalDim}
      contentClassName={s.modal}
    >
        <button className={s.modalClose} onClick={onClose} aria-label="닫기" data-dialog-initial-focus>✕</button>

        <div className={s.modalInner}>
          {/* 이미지 */}
          <div className={s.modalImg}>
            <img src={getArtImageSrc(review.artImage)} alt={review.artName} onError={applyArtImageFallback} />
          </div>

          {/* 내용 */}
          <div className={s.modalDetail}>
            <h2 id="my-review-dialog-title" className={s.modalArtName}>{review.artName}</h2>
            <div className={s.modalMeta}>
              <StarDisplay star={review.star} />
              <span className={s.modalDate}>{formatDate(review.createdAt)}</span>
            </div>
            <hr className={s.modalHr} />

            <div className={s.modalContent}>
              <p>{review.content}</p>
            </div>

            <hr className={s.modalHr} />
            <p className={s.modalPrice}>낙찰가 <strong>{fmt(review.winningPrice)}원</strong></p>

            <div className={s.modalActions}>
              <ActionBtn to={`/write-review/${review.orderId}`} variant="fill">리뷰 수정하기</ActionBtn>
              <ActionBtn onClick={onClose} variant="outline">닫기</ActionBtn>
            </div>
          </div>
        </div>
    </AccessibleDialog>
  )
}
