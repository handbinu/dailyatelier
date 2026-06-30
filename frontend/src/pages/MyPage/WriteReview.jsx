// src/pages/MyPage/WriteReview.jsx  —  리뷰 쓰기 / 수정
import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { PageBanner, PageWrap } from './components/atoms'
import { MOCK_SUCCESSFUL, MOCK_REVIEWS, fmt } from './mockData'
import s from './WriteReview.module.css'

export default function WriteReview() {
  const navigate   = useNavigate()
  const { artId }  = useParams()

  if (!localStorage.getItem('token')) {
    alert('로그인이 필요합니다.')
    navigate('/login', { replace: true })
    return null
  }

  // 작품 정보 조회 (목업)
  const artItem = MOCK_SUCCESSFUL.find(a => a.id === artId) ?? MOCK_SUCCESSFUL[0]
  // 기존 리뷰 조회
  const existing = MOCK_REVIEWS.find(r => r.artId === artId)

  const [star,    setStar]    = useState(existing?.star    ?? 5)
  const [hover,   setHover]   = useState(null)
  const [content, setContent] = useState(existing?.content ?? '')
  const [submitting, setSub]  = useState(false)
  const [done,    setDone]    = useState(false)

  const isEdit = Boolean(existing)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (content.trim().length < 10) {
      alert('리뷰 내용을 10자 이상 입력해주세요.')
      return
    }
    setSub(true)
    // TODO: API 호출 → POST /api/reviews  or  PUT /api/reviews/:id
    await new Promise(r => setTimeout(r, 800))   // 목업 딜레이
    setSub(false)
    setDone(true)
  }

  if (done) {
    return (
      <PageWrap>
        <PageBanner title={isEdit ? '리뷰 수정 완료' : '리뷰 등록 완료'} crumb="리뷰 쓰기" />
        <div className={s.doneWrap}>
          <span className={s.doneIcon}>✓</span>
          <h2 className={s.doneTitle}>{isEdit ? '리뷰가 수정되었습니다!' : '리뷰가 등록되었습니다!'}</h2>
          <p className={s.doneSub}>소중한 리뷰 감사합니다 🎨</p>
          <div className={s.doneActions}>
            <Link to="/mypage/my-review" className={s.doneBtn}>내 리뷰 보기</Link>
            <Link to="/"                 className={`${s.doneBtn} ${s.doneBtnOutline}`}>홈으로</Link>
          </div>
        </div>
      </PageWrap>
    )
  }

  return (
    <PageWrap>
      <PageBanner title={isEdit ? '리뷰 수정하기' : '리뷰 쓰기'} crumb="리뷰 쓰기" />

      <div className={s.body}>
        <form onSubmit={handleSubmit} className={s.form}>
          {/* 좌측: 작품 정보 */}
          <div className={s.artPanel}>
            <div className={s.artImgWrap}>
              <img src={artItem.artImg} alt={artItem.artName} />
            </div>
            <div className={s.artInfo}>
              <p className={s.artName}>{artItem.artName}</p>
              <p className={s.artArtist}>by {artItem.artist}</p>
              <hr className={s.artHr} />
              <p className={s.artPrice}>낙찰가 <strong>{fmt(artItem.finalPrice)}원</strong></p>
              <p className={s.artDate}>{artItem.orderedAt} 낙찰</p>
            </div>
          </div>

          {/* 우측: 리뷰 작성 */}
          <div className={s.writePanel}>
            {/* 별점 */}
            <section className={s.section}>
              <label className={s.sectionLabel}>별점 <span className={s.starScore}>{hover ?? star} / 10</span></label>
              <div className={s.starRow}>
                {Array.from({ length: 10 }, (_, i) => i + 1).map(n => (
                  <button
                    key={n}
                    type="button"
                    className={`${s.starBtn} ${n <= (hover ?? star) ? s.starFilled : ''}`}
                    onMouseEnter={() => setHover(n)}
                    onMouseLeave={() => setHover(null)}
                    onClick={() => setStar(n)}
                    aria-label={`별점 ${n}점`}
                  >
                    ★
                  </button>
                ))}
              </div>
              <div className={s.starHint}>
                {star <= 3  ? '아쉬운 점이 있었군요 😔'
                 : star <= 6 ? '보통이었군요 😊'
                 : star <= 8 ? '만족스러웠군요 😄'
                              : '최고의 작품이었군요! 🤩'}
              </div>
            </section>

            {/* 리뷰 내용 */}
            <section className={s.section}>
              <label className={s.sectionLabel} htmlFor="review-content">
                리뷰 내용 <span className={s.charCount}>{content.length} / 300</span>
              </label>
              <textarea
                id="review-content"
                className={s.textarea}
                placeholder="작품을 구매한 후기를 자세히 남겨주세요. (최소 10자)"
                value={content}
                onChange={e => setContent(e.target.value.slice(0, 300))}
                rows={8}
                required
              />
            </section>

            {/* 안내 */}
            <p className={s.notice}>
              ※ 허위 리뷰 또는 욕설·비방이 포함된 리뷰는 예고 없이 삭제될 수 있습니다.
            </p>

            {/* 버튼 */}
            <div className={s.btnRow}>
              <button
                type="submit"
                className={s.submitBtn}
                disabled={submitting}
              >
                {submitting ? '처리 중…' : isEdit ? '수정 완료' : '리뷰 등록'}
              </button>
              <button
                type="button"
                className={s.cancelBtn}
                onClick={() => navigate(-1)}
              >
                취소
              </button>
            </div>
          </div>
        </form>
      </div>
    </PageWrap>
  )
}