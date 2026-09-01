import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createReview, getOrderReview, updateReview } from '../../api/reviewApi'
import { applyArtImageFallback, getArtImageSrc } from '../../utils/artImage'
import { formatOrderPrice } from '../../utils/orderView'
import { PageBanner, PageWrap } from './components/atoms'
import s from './WriteReview.module.css'

const REVIEW_ERROR_MESSAGES = {
  REVIEW_ORDER_NOT_FOUND: '리뷰를 작성할 주문을 찾을 수 없습니다.',
  REVIEW_NOT_FOUND: '수정할 리뷰를 찾을 수 없습니다.',
  REVIEW_ACCESS_DENIED: '이 주문 또는 리뷰에 접근할 권한이 없습니다.',
  REVIEW_ORDER_NOT_CONFIRMED: '구매 확정된 주문에만 리뷰를 작성할 수 있습니다.',
  REVIEW_ALREADY_EXISTS: '이미 리뷰가 작성된 주문입니다. 화면을 새로고침해 주세요.',
  INVALID_REVIEW: '별점과 리뷰 내용을 다시 확인해 주세요.',
}

const getReviewError = (error, fallback) => {
  const status = error?.response?.status
  const code = error?.response?.data?.code
  return {
    status,
    message: REVIEW_ERROR_MESSAGES[code]
      || error?.response?.data?.message
      || fallback,
  }
}

export default function WriteReview() {
  const navigate = useNavigate()
  const { orderId } = useParams()
  const [reviewContext, setReviewContext] = useState(null)
  const [star, setStar] = useState(5)
  const [hover, setHover] = useState(null)
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [validationError, setValidationError] = useState('')
  const [submitError, setSubmitError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const submitGuard = useRef(false)

  useEffect(() => {
    const controller = new AbortController()

    const loadReview = async () => {
      setLoading(true)
      setLoadError('')
      try {
        const { data } = await getOrderReview(orderId, {
          signal: controller.signal,
        })
        const existing = data.review
        setReviewContext(data)
        setStar(existing?.star ?? 5)
        setContent(existing?.content ?? '')
      } catch (error) {
        if (controller.signal.aborted) return
        const reviewError = getReviewError(
          error,
          '리뷰 작성 정보를 불러오지 못했습니다.',
        )
        if (reviewError.status === 401) {
          navigate('/login', { replace: true })
          return
        }
        setLoadError(reviewError.message)
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }

    loadReview()
    return () => controller.abort()
  }, [navigate, orderId, reloadKey])

  const existing = reviewContext?.review
  const isEdit = Boolean(existing)

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (submitGuard.current) return

    const normalizedContent = content.trim()
    if (!Number.isInteger(star) || star < 1 || star > 10) {
      setValidationError('별점은 1점에서 10점 사이로 선택해 주세요.')
      return
    }
    if (normalizedContent.length < 10 || normalizedContent.length > 300) {
      setValidationError('리뷰 내용은 앞뒤 공백 제거 후 10자 이상 300자 이하로 입력해 주세요.')
      return
    }

    submitGuard.current = true
    setSubmitting(true)
    setValidationError('')
    setSubmitError('')
    try {
      if (isEdit) {
        await updateReview(existing.reviewId, {
          star,
          content: normalizedContent,
        })
      } else {
        await createReview({
          orderId: reviewContext.orderId,
          star,
          content: normalizedContent,
        })
      }
      setDone(true)
    } catch (error) {
      const reviewError = getReviewError(
        error,
        isEdit
          ? '리뷰를 수정하지 못했습니다.'
          : '리뷰를 등록하지 못했습니다.',
      )
      if (reviewError.status === 401) {
        navigate('/login', { replace: true })
        return
      }
      setSubmitError(reviewError.message)
    } finally {
      submitGuard.current = false
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <PageWrap>
        <PageBanner title="리뷰 정보 확인 중" crumb="리뷰 쓰기" />
        <div className={s.stateWrap} aria-live="polite">
          <p className={s.stateMessage}>주문과 리뷰 정보를 불러오는 중입니다.</p>
        </div>
      </PageWrap>
    )
  }

  if (loadError || !reviewContext) {
    return (
      <PageWrap>
        <PageBanner title="리뷰 정보를 확인할 수 없습니다" crumb="리뷰 쓰기" />
        <div className={s.stateWrap} role="alert">
          <p className={s.stateMessage}>{loadError}</p>
          <div className={s.stateActions}>
            <button
              type="button"
              className={s.retryBtn}
              onClick={() => setReloadKey((current) => current + 1)}
            >
              다시 시도
            </button>
            <Link to="/mypage/order-status" className={s.backLink}>주문 내역으로</Link>
          </div>
        </div>
      </PageWrap>
    )
  }

  if (done) {
    return (
      <PageWrap>
        <PageBanner title={isEdit ? '리뷰 수정 완료' : '리뷰 등록 완료'} crumb="리뷰 쓰기" />
        <div className={s.doneWrap}>
          <span className={s.doneIcon} aria-hidden="true">✓</span>
          <h2 className={s.doneTitle}>{isEdit ? '리뷰가 수정되었습니다!' : '리뷰가 등록되었습니다!'}</h2>
          <p className={s.doneSub}>소중한 리뷰 감사합니다.</p>
          <div className={s.doneActions}>
            <Link to="/mypage/my-review" className={s.doneBtn}>내 리뷰 보기</Link>
            <Link to="/mypage/order-status" className={`${s.doneBtn} ${s.doneBtnOutline}`}>주문 내역</Link>
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
          <div className={s.artPanel}>
            <div className={s.artImgWrap}>
              <img
                src={getArtImageSrc(reviewContext.artImage)}
                alt={reviewContext.artName}
                onError={applyArtImageFallback}
              />
            </div>
            <div className={s.artInfo}>
              <p className={s.artName}>{reviewContext.artName}</p>
              <p className={s.artArtist}>by {reviewContext.artistName || '작가 정보 없음'}</p>
              <hr className={s.artHr} />
              <p className={s.artPrice}>낙찰가 <strong>{formatOrderPrice(reviewContext.winningPrice)}</strong></p>
            </div>
          </div>

          <div className={s.writePanel}>
            <section className={s.section}>
              <label className={s.sectionLabel}>별점 <span className={s.starScore}>{hover ?? star} / 10</span></label>
              <div className={s.starRow} role="group" aria-label="리뷰 별점">
                {Array.from({ length: 10 }, (_, index) => index + 1).map((score) => (
                  <button
                    key={score}
                    type="button"
                    className={`${s.starBtn} ${score <= (hover ?? star) ? s.starFilled : ''}`}
                    onMouseEnter={() => setHover(score)}
                    onMouseLeave={() => setHover(null)}
                    onFocus={() => setHover(score)}
                    onBlur={() => setHover(null)}
                    onClick={() => setStar(score)}
                    aria-label={`별점 ${score}점`}
                    aria-pressed={star === score}
                    disabled={submitting}
                  >
                    ★
                  </button>
                ))}
              </div>
              <div className={s.starHint}>
                {star <= 3 ? '아쉬운 점이 있었군요.'
                  : star <= 6 ? '보통이었군요.'
                    : star <= 8 ? '만족스러웠군요.'
                      : '최고의 작품이었군요!'}
              </div>
            </section>

            <section className={s.section}>
              <label className={s.sectionLabel} htmlFor="review-content">
                리뷰 내용 <span className={s.charCount}>{content.length} / 300</span>
              </label>
              <textarea
                id="review-content"
                className={s.textarea}
                placeholder="작품을 구매한 후기를 자세히 남겨주세요. (최소 10자)"
                value={content}
                onChange={(event) => {
                  setContent(event.target.value.slice(0, 300))
                  setValidationError('')
                }}
                rows={8}
                minLength={10}
                maxLength={300}
                required
                disabled={submitting}
              />
            </section>

            <p className={s.notice}>
              ※ 허위 리뷰 또는 욕설·비방이 포함된 리뷰는 예고 없이 삭제될 수 있습니다.
            </p>

            {(validationError || submitError) && (
              <p className={s.formError} role="alert">
                {validationError || submitError}
              </p>
            )}

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
                disabled={submitting}
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
