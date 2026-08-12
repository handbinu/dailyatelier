import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { createBid, getArt } from '../../api/artApi'
import { addArtLike, getArtLikeStatus, removeArtLike } from '../../api/userApi'
import { formatClosingTime, formatPrice, getAuctionStatusMeta, getDeadlineMeta } from '../../utils/artDisplay'
import {
  applyArtImageFallback,
  applyArtImageFallbackIfBlank,
  getArtImageSrc,
} from '../../utils/artImage'
import { MAX_BID_PRICE, parseIntegerPrice } from '../../utils/bidPricePolicy'
import styles from './ArtDetail.module.css'

const LIST_PATH_PATTERN = /^\/(?:search|auction\/(?:total|digital|analog))(?:\?.*)?$/

export default function ArtDetail() {
  const { id } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const [art, setArt] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notFound, setNotFound] = useState(false)
  const [retryKey, setRetryKey] = useState(0)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [isLiked, setIsLiked] = useState(false)
  const [likeLoading, setLikeLoading] = useState(false)
  const [likeError, setLikeError] = useState('')
  const [bidPrice, setBidPrice] = useState('')
  const [bidSubmitting, setBidSubmitting] = useState(false)
  const [bidFeedback, setBidFeedback] = useState(null)
  const [now, setNow] = useState(() => Date.now())

  const listPath = useMemo(() => {
    const previousPath = location.state?.from
    return typeof previousPath === 'string' && LIST_PATH_PATTERN.test(previousPath)
      ? previousPath
      : '/auction/total'
  }, [location.state])

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    const loadDetail = async () => {
      setLoading(true)
      setError('')
      setNotFound(false)
      setArt(null)
      setIsLiked(false)
      setLikeError('')
      setIsModalOpen(false)
      setBidPrice('')
      setBidFeedback(null)

      if (!/^\d+$/.test(id)) {
        setNotFound(true)
        setLoading(false)
        return
      }

      try {
        const { data } = await getArt(id, { signal: controller.signal })
        if (!active) return
        setArt(data)

        if (localStorage.getItem('token') && !data.isOwner) {
          try {
            const likeResponse = await getArtLikeStatus(id)
            if (active) setIsLiked(likeResponse.data.liked)
          } catch (likeRequestError) {
            if (!active) return
            if (likeRequestError.response?.status === 401) {
              setIsLiked(false)
            } else {
              setLikeError('찜 상태를 불러오지 못했습니다.')
            }
          }
        }
      } catch (requestError) {
        if (!active || requestError.code === 'ERR_CANCELED') return
        if (requestError.response?.status === 404) {
          setNotFound(true)
        } else {
          setError(requestError.response?.data?.message || '작품 정보를 불러오지 못했습니다.')
        }
      } finally {
        if (active) setLoading(false)
      }
    }

    loadDetail()
    return () => {
      active = false
      controller.abort()
    }
  }, [id, retryKey])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (!isModalOpen) return undefined

    const originalOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        setIsModalOpen(false)
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = originalOverflow
    }
  }, [isModalOpen])

  const handleLike = async () => {
    if (!localStorage.getItem('token')) {
      alert('로그인 후 찜할 수 있습니다.')
      navigate('/login', { state: { from: location } })
      return
    }
    if (art.isOwner || likeLoading) return

    setLikeLoading(true)
    setLikeError('')
    try {
      const response = isLiked ? await removeArtLike(art.artId) : await addArtLike(art.artId)
      setIsLiked(response.data.liked)
    } catch (requestError) {
      if (requestError.response?.status === 401) {
        setIsLiked(false)
      } else {
        setLikeError(requestError.response?.data?.message || '찜 상태를 변경하지 못했습니다.')
      }
    } finally {
      setLikeLoading(false)
    }
  }

  const refreshArtAfterBidError = async () => {
    try {
      const { data } = await getArt(id)
      setArt(data)
      return data
    } catch {
      // 기존 상세 정보를 유지하고 사용자가 다시 시도할 수 있게 한다.
      return null
    }
  }

  const handleBidSubmit = async (event) => {
    event.preventDefault()
    if (bidSubmitting) return

    const amount = parseIntegerPrice(bidPrice)
    if (amount === null) {
      setBidFeedback({ type: 'error', message: '입찰 금액을 원 단위 정수로 입력해 주세요.' })
      return
    }

    if (amount < 1 || amount > MAX_BID_PRICE) {
      setBidFeedback({ type: 'error', message: '입찰 금액은 1원 이상 21억 원 이하로 입력해 주세요.' })
      return
    }
    if (art.nextMinimumBidPrice === null) {
      setBidFeedback({ type: 'error', message: '최소 증분을 적용하면 시스템 최대 입찰가를 초과합니다.' })
      return
    }
    if (amount < art.nextMinimumBidPrice) {
      setBidFeedback({
        type: 'error',
        message: `최소 입찰 가능 금액은 ${formatPrice(art.nextMinimumBidPrice)}원입니다.`,
      })
      return
    }

    setBidSubmitting(true)
    setBidFeedback(null)
    try {
      const { data } = await createBid(art.artId, amount)
      setArt((currentArt) => ({
        ...currentArt,
        currentPrice: data.currentPrice,
        minimumBidIncrement: data.minimumBidIncrement,
        nextMinimumBidPrice: data.nextMinimumBidPrice,
      }))
      setBidPrice('')
      setBidFeedback({
        type: 'success',
        message: `${formatPrice(data.bidPrice)}원에 입찰했습니다.`,
      })
    } catch (requestError) {
      const statusCode = requestError.response?.status
      const errorCode = requestError.response?.data?.code

      const refreshedArt = statusCode === 409 ? await refreshArtAfterBidError() : null

      if (statusCode === 401) {
        setBidFeedback({ type: 'error', message: '로그인이 만료되었습니다. 다시 로그인해 주세요.' })
      } else if (errorCode === 'BID_CONFLICT') {
        setBidFeedback({
          type: 'error',
          message: '다른 입찰이 처리 중입니다. 갱신된 현재가를 확인하고 다시 시도해 주세요.',
        })
      } else if (errorCode === 'BID_TOO_LOW') {
        const latestMinimum = refreshedArt?.nextMinimumBidPrice
        setBidFeedback({
          type: 'error',
          message: latestMinimum === null
            ? '최소 증분을 적용하면 시스템 최대 입찰가를 초과합니다.'
            : latestMinimum !== undefined
              ? `현재가가 갱신되었습니다. 최소 입찰 가능 금액은 ${formatPrice(latestMinimum)}원입니다.`
              : requestError.response?.data?.message || '현재가가 갱신되었습니다. 최신 입찰 가능 금액을 확인해 주세요.',
        })
      } else if (errorCode === 'BID_LIMIT_REACHED') {
        setArt((currentArt) => ({ ...currentArt, nextMinimumBidPrice: null }))
        setBidFeedback({
          type: 'error',
          message: '최소 증분을 적용하면 시스템 최대 입찰가를 초과합니다.',
        })
      } else {
        setBidFeedback({
          type: 'error',
          message: requestError.response?.data?.message || '입찰을 처리하지 못했습니다.',
        })
      }
    } finally {
      setBidSubmitting(false)
    }
  }

  if (loading) {
    return <DetailState title="작품 정보를 불러오는 중입니다" variant="loading" loading />
  }

  if (notFound) {
    return (
      <DetailState
        title="작품을 찾을 수 없습니다"
        message="삭제되었거나 존재하지 않는 작품입니다."
        variant="notFound"
        action={<Link to={listPath}>작품 목록으로 돌아가기</Link>}
      />
    )
  }

  if (error || !art) {
    return (
      <DetailState
        title="작품 정보를 불러오지 못했습니다"
        message={error}
        variant="error"
        action={<button type="button" onClick={() => setRetryKey((key) => key + 1)}>다시 시도</button>}
      />
    )
  }

  const deadline = getDeadlineMeta(art.closingTime)
  const status = getAuctionStatusMeta(art, now)
  const isUrgent = status.phase === 'ONGOING' && deadline.isUrgent
  const imageSrc = getArtImageSrc(art.imgPath)
  const isLoggedIn = Boolean(localStorage.getItem('token'))
  const bidStartTimestamp = new Date(art.bidStartTime).getTime()
  const closingTimestamp = new Date(art.closingTime).getTime()
  const hasValidAuctionTime = !Number.isNaN(bidStartTimestamp) && !Number.isNaN(closingTimestamp)
  let bidDisabledReason = ''

  if (art.isOwner) {
    bidDisabledReason = '본인이 등록한 작품에는 입찰할 수 없습니다.'
  } else if (art.artStatus !== 0) {
    bidDisabledReason = '종료된 경매에는 입찰할 수 없습니다.'
  } else if (!hasValidAuctionTime) {
    bidDisabledReason = '경매 시간을 확인할 수 없어 입찰할 수 없습니다.'
  } else if (now < bidStartTimestamp) {
    bidDisabledReason = `${formatClosingTime(art.bidStartTime)}부터 입찰할 수 있습니다.`
  } else if (now >= closingTimestamp) {
    bidDisabledReason = '마감된 경매에는 입찰할 수 없습니다.'
  } else if (art.nextMinimumBidPrice === null) {
    bidDisabledReason = '최소 증분을 적용하면 시스템 최대 입찰가를 초과합니다.'
  } else if (!isLoggedIn) {
    bidDisabledReason = '로그인 후 입찰할 수 있습니다.'
  }

  const canBid = !bidDisabledReason

  return (
    <div className={styles.page}>
      <Link to={listPath} className={styles.backLink}>← 작품 목록으로</Link>

      <section className={styles.hero}>
        <button
          type="button"
          className={styles.imageButton}
          onClick={() => setIsModalOpen(true)}
          aria-label={`${art.name} 원본 이미지 보기`}
        >
          <img
            className={styles.image}
            src={imageSrc}
            alt={art.name}
            onError={applyArtImageFallback}
            onLoad={applyArtImageFallbackIfBlank}
          />
        </button>

        <div className={styles.info}>
          <div className={styles.headingRow}>
            <p className={styles.kicker}>작품 상세</p>
            <span className={`${styles.statusBadge} ${styles[`status_${status.tone}`]}`}>
              {status.label}
            </span>
          </div>
          <h1 className={styles.title}>{art.name}</h1>
          <p className={styles.artist}>by {art.artistName || '작가 미상'}</p>

          <div className={styles.meta}>
            <p className={styles.metaItem}>
              <span className={styles.metaLabel}>재료</span>
              <span className={styles.metaValue}>{art.material || '정보 없음'}</span>
            </p>
            <p className={styles.metaItem}>
              <span className={styles.metaLabel}>경매 시작</span>
              <time className={styles.metaValue} dateTime={art.bidStartTime}>
                {formatClosingTime(art.bidStartTime)}
              </time>
            </p>
            <p className={styles.metaItem}>
              <span className={styles.metaLabel}>경매 마감</span>
              <time className={`${styles.metaValue} ${isUrgent ? styles.urgentText : ''}`} dateTime={art.closingTime}>
                {formatClosingTime(art.closingTime)} {isUrgent ? '(마감 임박)' : ''}
              </time>
            </p>
          </div>

          <dl className={styles.priceSummary}>
            <div className={styles.currentPriceItem}>
              <dt>현재가</dt>
              <dd>{formatPrice(art.currentPrice)}원</dd>
              <small>시작가 {formatPrice(art.startPrice)}원</small>
            </div>
            <div>
              <dt>다음 입찰 가능 금액</dt>
              <dd>{art.nextMinimumBidPrice === null ? '입찰 한도 도달' : `${formatPrice(art.nextMinimumBidPrice)}원`}</dd>
            </div>
            <div>
              <dt>최소 입찰 증분</dt>
              <dd>{formatPrice(art.minimumBidIncrement)}원</dd>
            </div>
          </dl>

          <form className={styles.bidForm} onSubmit={handleBidSubmit}>
            <div className={styles.bidHeading}>
              <div>
                <h2>입찰하기</h2>
                <p>표시된 다음 입찰 가능 금액 이상으로 입력해 주세요.</p>
              </div>
              {canBid && (
                <span className={styles.minimumBid}>
                  최소 {formatPrice(art.nextMinimumBidPrice)}원
                </span>
              )}
            </div>

            <div className={styles.bidControls}>
              <label className={styles.bidInputWrap} htmlFor="bid-price">
                <span className={styles.srOnly}>입찰 금액</span>
                <input
                  id="bid-price"
                  aria-label="입찰 금액"
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  value={bidPrice}
                  onChange={(event) => {
                    setBidPrice(event.target.value.replace(/[^\d]/g, ''))
                    setBidFeedback(null)
                  }}
                  placeholder={canBid ? `${formatPrice(art.nextMinimumBidPrice)}원 이상` : '입찰 불가'}
                  disabled={!canBid || bidSubmitting}
                  aria-describedby="bid-help"
                />
                <span aria-hidden="true">원</span>
              </label>
              {!isLoggedIn && !art.isOwner ? (
                <button
                  type="button"
                  className={styles.bidButton}
                  onClick={() => navigate('/login', { state: { from: location } })}
                >
                  로그인
                </button>
              ) : (
                <button
                  type="submit"
                  className={styles.bidButton}
                  disabled={!canBid || bidSubmitting}
                >
                  {bidSubmitting ? '입찰 처리 중…' : '입찰하기'}
                </button>
              )}
            </div>

            <div id="bid-help" className={styles.bidHelp} aria-live="polite">
              <p className={styles.bidNotice}>다른 사용자의 입찰로 현재가가 바뀔 수 있으며, 서버가 최신 금액으로 최종 확인합니다.</p>
              {bidDisabledReason && <p className={styles.bidNotice}>{bidDisabledReason}</p>}
              {bidFeedback && (
                <p className={bidFeedback.type === 'success' ? styles.bidSuccess : styles.bidError}>
                  {bidFeedback.message}
                </p>
              )}
            </div>
          </form>

          {art.isOwner ? (
            <p className={styles.ownerNotice}>내가 등록한 작품입니다.</p>
          ) : (
            <div className={styles.likeRow}>
              <button
                type="button"
                className={`${styles.likeButton} ${isLiked ? styles.likeButtonActive : ''}`}
                onClick={handleLike}
                aria-pressed={isLiked}
                disabled={likeLoading}
              >
                <span className={styles.likeIcon} aria-hidden="true">
                  {isLiked ? '\u2665' : '\u2661'}
                </span>
                <span className={styles.likeText}>
                  {likeLoading ? '처리 중' : isLiked ? '찜 완료' : localStorage.getItem('token') ? '찜하기' : '로그인 후 찜하기'}
                </span>
              </button>
              {likeError && <p className={styles.likeError}>{likeError}</p>}
            </div>
          )}
        </div>
      </section>

      <section className={styles.descriptionSection}>
        <h2 className={styles.sectionTitle}>작품 소개</h2>
        <p className={styles.description}>{art.descript || '등록된 작품 소개가 없습니다.'}</p>
      </section>

      {art.wIntro && (
        <section className={styles.descriptionSection}>
          <h2 className={styles.sectionTitle}>작가의 한마디</h2>
          <p className={styles.description}>{art.wIntro}</p>
        </section>
      )}

      {isModalOpen && (
        <div
          className={styles.modalDim}
          role="dialog"
          aria-modal="true"
          onClick={() => setIsModalOpen(false)}
        >
          <div
            className={styles.modal}
            onClick={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              className={styles.modalClose}
              onClick={() => setIsModalOpen(false)}
              aria-label="닫기"
            >
              ×
            </button>
            <img
              className={styles.modalImage}
              src={imageSrc}
              alt={art.name}
              onError={applyArtImageFallback}
              onLoad={applyArtImageFallbackIfBlank}
            />
          </div>
        </div>
      )}
    </div>
  )
}

function DetailState({ title, message, action, variant = 'error', loading = false }) {
  return (
    <main className={styles.statePage} aria-live="polite" aria-busy={loading}>
      <StateIcon variant={variant} />
      <h1>{title}</h1>
      {message && <p>{message}</p>}
      {action && <div className={styles.stateAction}>{action}</div>}
    </main>
  )
}

function StateIcon({ variant }) {
  const className = `${styles.stateIcon} ${styles[`stateIcon_${variant}`]}`

  if (variant === 'loading') {
    return (
      <div className={className} aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none">
          <path d="M12 3a9 9 0 1 1-9 9" />
          <path d="M3 6v6h6" />
        </svg>
      </div>
    )
  }

  if (variant === 'notFound') {
    return (
      <div className={className} aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none">
          <circle cx="10.5" cy="10.5" r="5.5" />
          <path d="m15 15 5 5" />
          <path d="M8.5 10.5h4" />
        </svg>
      </div>
    )
  }

  return (
    <div className={className} aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v6" />
        <path d="M12 17h.01" />
      </svg>
    </div>
  )
}
