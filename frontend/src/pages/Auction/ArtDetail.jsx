import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { getArt } from '../../api/artApi'
import { addArtLike, getArtLikeStatus, removeArtLike } from '../../api/userApi'
import { formatClosingTime, formatPrice, getDeadlineMeta } from '../../utils/artDisplay'
import {
  applyArtImageFallback,
  applyArtImageFallbackIfBlank,
  getArtImageSrc,
} from '../../utils/artImage'
import styles from './ArtDetail.module.css'

const STATUS_META = {
  0: { label: '진행 중', tone: 'active' },
  1: { label: '종료', tone: 'ended' },
  2: { label: '낙찰', tone: 'won' },
}

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

  const listPath = useMemo(() => {
    const previousPath = location.state?.from
    return typeof previousPath === 'string' && previousPath.startsWith('/auction/total')
      ? previousPath
      : '/auction/total?page=1'
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

  const status = STATUS_META[art.artStatus] ?? { label: '상태 미정', tone: 'ended' }
  const deadline = getDeadlineMeta(art.closingTime)
  const isUrgent = art.artStatus === 0 && deadline.isUrgent
  const imageSrc = getArtImageSrc(art.imgPath)

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

          <p className={styles.price}>
            <span className={styles.priceLabel}>현재가</span>
            <strong>{formatPrice(art.currentPrice)}원</strong>
            <small>시작가 {formatPrice(art.startPrice)}원</small>
          </p>

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
