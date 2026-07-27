// src/pages/MyPage/MyReview.jsx  —  내가 쓴 리뷰
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageBanner, StarDisplay, Empty, PageWrap, ActionBtn } from './components/atoms'
import { MOCK_REVIEWS, fmt } from './mockData'
import s from './MyReview.module.css'

const PER_PAGE = 6

const SORT_OPTIONS = [
  { value: 'recent', label: '최근 리뷰순' },
  { value: 'star',   label: '별점순'     },
  { value: 'price',  label: '가격순'     },
]

function sortItems(arr, sort) {
  const copy = [...arr]
  if (sort === 'star')  return copy.sort((a, b) => b.star - a.star)
  if (sort === 'price') return copy.sort((a, b) => b.finalPrice - a.finalPrice)
  return copy.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
}

export default function MyReview() {
  const navigate = useNavigate()
  const [sort, setSort]       = useState('recent')
  const [page, setPage]       = useState(1)
  const [modal, setModal]     = useState(null)   // 선택된 리뷰 객체

  if (!localStorage.getItem('token')) {
    alert('로그인이 필요합니다.')
    navigate('/login', { replace: true })
    return null
  }

  const sorted  = sortItems(MOCK_REVIEWS, sort)
  const total   = sorted.length
  const totalPg = Math.max(1, Math.ceil(total / PER_PAGE))
  const paged   = sorted.slice((page - 1) * PER_PAGE, page * PER_PAGE)

  const handleSort = (v) => { setSort(v); setPage(1) }

  return (
    <PageWrap>
      <PageBanner title="내가 쓴 리뷰" crumb="나의 리뷰" />

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
        {paged.length === 0
          ? <Empty msg="작성한 리뷰가 없습니다." />
          : <div className={s.grid}>
              {paged.map(r => (
                <button
                  key={r.id}
                  className={s.card}
                  onClick={() => setModal(r)}
                  aria-label={`${r.artName} 리뷰 상세 보기`}
                >
                  <div className={s.cardImg}>
                    <img src={r.artImg} alt={r.artName} loading="lazy" />
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
                    <p className={s.cardPrice}>낙찰가 {fmt(r.finalPrice)}원</p>
                    <p className={s.cardDate}>{r.createdAt}</p>
                  </div>
                </button>
              ))}
            </div>
        }

        {/* 페이지네이션 */}
        {totalPg > 1 && (
          <Pagination current={page} total={totalPg} onChange={setPage} />
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
        disabled={current === 1}
        onClick={() => onChange(current - 1)}
        aria-label="이전 페이지"
      >‹</button>

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
  const handleBg = (e) => { if (e.target === e.currentTarget) onClose() }

  return (
    <div className={s.modalDim} onClick={handleBg} role="dialog" aria-modal="true">
      <div className={s.modal}>
        <button className={s.modalClose} onClick={onClose} aria-label="닫기">✕</button>

        <div className={s.modalInner}>
          {/* 이미지 */}
          <div className={s.modalImg}>
            <img src={review.artImg} alt={review.artName} />
          </div>

          {/* 내용 */}
          <div className={s.modalDetail}>
            <p className={s.modalArtName}>{review.artName}</p>
            <div className={s.modalMeta}>
              <StarDisplay star={review.star} />
              <span className={s.modalDate}>{review.createdAt}</span>
            </div>
            <hr className={s.modalHr} />

            <div className={s.modalContent}>
              <p>{review.content}</p>
            </div>

            <hr className={s.modalHr} />
            <p className={s.modalPrice}>낙찰가 <strong>{fmt(review.finalPrice)}원</strong></p>

            <div className={s.modalActions}>
              <ActionBtn to={`/write-review/${review.artId}`} variant="fill">리뷰 수정하기</ActionBtn>
              <ActionBtn onClick={onClose} variant="outline">닫기</ActionBtn>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
