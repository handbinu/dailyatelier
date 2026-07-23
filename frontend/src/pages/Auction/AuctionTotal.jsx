import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { getArts } from '../../api/artApi'
import { formatClosingTime, formatPrice, getDeadlineMeta } from '../../utils/artDisplay'
import {
  applyArtImageFallback,
  applyArtImageFallbackIfBlank,
  getArtImageSrc,
} from '../../utils/artImage'
import styles from './AuctionTotal.module.css'

const PAGE_SIZE = 12
const PAGE_WINDOW = 5

const parsePage = (value) => {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 1 ? parsed : 1
}

const getVisiblePages = (currentPage, totalPages) => {
  if (totalPages <= PAGE_WINDOW) {
    return Array.from({ length: totalPages }, (_, index) => index + 1)
  }

  let start = Math.max(1, currentPage - Math.floor(PAGE_WINDOW / 2))
  const end = Math.min(totalPages, start + PAGE_WINDOW - 1)
  start = Math.max(1, end - PAGE_WINDOW + 1)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
}

export default function AuctionTotal() {
  const [searchParams, setSearchParams] = useSearchParams()
  const pageParam = searchParams.get('page')
  const currentPage = parsePage(pageParam)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    if (pageParam === String(currentPage)) return

    setSearchParams((previous) => {
      const next = new URLSearchParams(previous)
      next.set('page', String(currentPage))
      return next
    }, { replace: true })
  }, [currentPage, pageParam, setSearchParams])

  useEffect(() => {
    const controller = new AbortController()

    const loadArts = async () => {
      setLoading(true)
      setError('')

      try {
        const { data } = await getArts({
          page: currentPage - 1,
          size: PAGE_SIZE,
          signal: controller.signal,
        })
        setResult(data)
      } catch (requestError) {
        if (requestError.code === 'ERR_CANCELED') return
        setResult(null)
        setError(requestError.response?.data?.message || '작품 목록을 불러오지 못했습니다.')
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }

    loadArts()
    return () => controller.abort()
  }, [currentPage, retryKey])

  const visiblePages = useMemo(
    () => getVisiblePages(currentPage, result?.totalPages ?? 0),
    [currentPage, result?.totalPages],
  )

  const moveToPage = (page) => {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(page))
    setSearchParams(next)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const listLocation = `/auction/total?page=${currentPage}`
  const arts = result?.content ?? []

  return (
    <main className={styles.page}>
      <header className={styles.hero}>
        <div className={styles.heroInner}>
          <p className={styles.eyebrow}>DAILY AUCTION</p>
          <h1 className={styles.title}>전체 경매</h1>
          <p className={styles.subtitle}>지금 경매가 진행 중인 작품을 만나보세요.</p>
        </div>
      </header>

      <section className={styles.content} aria-labelledby="auction-list-title">
        <div className={styles.listHeader}>
          <div>
            <p className={styles.sectionLabel}>LIVE COLLECTION</p>
            <h2 id="auction-list-title" className={styles.sectionTitle}>진행 중인 작품</h2>
          </div>
          {!loading && !error && (
            <p className={styles.resultCount}>총 {Number(result?.totalElements ?? 0).toLocaleString('ko-KR')}점</p>
          )}
        </div>

        {loading ? (
          <LoadingGrid />
        ) : error ? (
          <Feedback
            title="작품을 불러오지 못했습니다"
            message={error}
            actionLabel="다시 시도"
            onAction={() => setRetryKey((key) => key + 1)}
          />
        ) : arts.length === 0 ? (
          <Feedback
            title="표시할 작품이 없습니다"
            message={currentPage > 1 ? '요청한 페이지에 등록된 작품이 없습니다.' : '진행 중인 작품이 등록되면 이곳에 표시됩니다.'}
            actionLabel={currentPage > 1 ? '첫 페이지로' : undefined}
            onAction={currentPage > 1 ? () => moveToPage(1) : undefined}
          />
        ) : (
          <>
            <div className={styles.grid}>
              {arts.map((art) => (
                <ArtCard key={art.artId} art={art} listLocation={listLocation} />
              ))}
            </div>

            {result.totalPages > 1 && (
              <nav className={styles.pagination} aria-label="작품 목록 페이지">
                <button
                  type="button"
                  className={styles.pageButton}
                  onClick={() => moveToPage(currentPage - 1)}
                  disabled={currentPage === 1}
                  aria-label="이전 페이지"
                >
                  ‹
                </button>
                {visiblePages.map((page) => (
                  <button
                    type="button"
                    key={page}
                    className={`${styles.pageButton} ${page === currentPage ? styles.pageButtonActive : ''}`}
                    onClick={() => moveToPage(page)}
                    aria-current={page === currentPage ? 'page' : undefined}
                  >
                    {page}
                  </button>
                ))}
                <button
                  type="button"
                  className={styles.pageButton}
                  onClick={() => moveToPage(currentPage + 1)}
                  disabled={currentPage >= result.totalPages}
                  aria-label="다음 페이지"
                >
                  ›
                </button>
              </nav>
            )}
          </>
        )}
      </section>
    </main>
  )
}

function ArtCard({ art, listLocation }) {
  const deadline = getDeadlineMeta(art.closingTime)

  return (
    <Link
      to={`/auction/${art.artId}`}
      state={{ from: listLocation }}
      className={styles.card}
      aria-label={`${art.name} 작품 상세 보기`}
    >
      <article>
        <div className={styles.imageWrap}>
          <img
            className={styles.image}
            src={getArtImageSrc(art.imgPath)}
            alt={art.name}
            loading="lazy"
            onError={applyArtImageFallback}
            onLoad={applyArtImageFallbackIfBlank}
          />
          <span className={`${styles.statusBadge} ${deadline.isUrgent ? styles.statusUrgent : ''} ${deadline.isClosed ? styles.statusClosed : ''}`}>
            {deadline.label}
          </span>
          <span className={styles.cardOverlay}>작품 자세히 보기</span>
        </div>
        <div className={styles.cardBody}>
          <p className={styles.artist}>by {art.artistName || '작가 미상'}</p>
          <h3 className={styles.artName}>{art.name}</h3>
          <div className={styles.priceRow}>
            <span>현재가</span>
            <strong>{formatPrice(art.currentPrice)}원</strong>
          </div>
          <p className={`${styles.closingTime} ${deadline.isUrgent ? styles.closingUrgent : ''}`}>
            <span>마감</span>
            <time dateTime={art.closingTime}>{formatClosingTime(art.closingTime)}</time>
          </p>
        </div>
      </article>
    </Link>
  )
}

function LoadingGrid() {
  return (
    <div className={styles.grid} aria-label="작품 목록을 불러오는 중" aria-busy="true">
      {Array.from({ length: 8 }, (_, index) => (
        <div key={index} className={styles.skeletonCard}>
          <div className={styles.skeletonImage} />
          <div className={styles.skeletonBody}>
            <span />
            <span />
            <span />
          </div>
        </div>
      ))}
    </div>
  )
}

function Feedback({ title, message, actionLabel, onAction }) {
  return (
    <div className={styles.feedback} role="status">
      <div className={styles.feedbackIcon} aria-hidden="true">□</div>
      <h3>{title}</h3>
      <p>{message}</p>
      {actionLabel && (
        <button type="button" className={styles.feedbackButton} onClick={onAction}>
          {actionLabel}
        </button>
      )}
    </div>
  )
}
