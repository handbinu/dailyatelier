import { Link } from 'react-router-dom'
import { formatClosingTime, formatPrice } from '../../utils/artDisplay'
import { applyArtImageFallback, applyArtImageFallbackIfBlank, getArtImageSrc } from '../../utils/artImage'
import styles from './ArtResults.module.css'

const STATUS_LABELS = { UPCOMING: '예정', ONGOING: '진행 중', ENDED: '종료' }

export function ArtResults({ arts, from }) {
  return (
    <div className={styles.grid}>
      {arts.map((art) => <ArtCard key={art.artId} art={art} from={from} />)}
    </div>
  )
}

export function ArtCard({ art, from }) {
  return (
    <Link to={`/auction/${art.artId}`} state={{ from }} className={styles.card} aria-label={`${art.name} 작품 상세 보기`}>
      <article>
        <div className={styles.imageWrap}>
          <img className={styles.image} src={getArtImageSrc(art.imgPath)} alt={art.name} loading="lazy"
            onError={applyArtImageFallback} onLoad={applyArtImageFallbackIfBlank} />
          <span className={`${styles.statusBadge} ${styles[`status${art.status}`] || ''}`}>
            {STATUS_LABELS[art.status] || art.status}
          </span>
        </div>
        <div className={styles.cardBody}>
          <p className={styles.artist}>by {art.artistName || '작가 미상'}</p>
          <h3 className={styles.artName}>{art.name}</h3>
          <div className={styles.priceRow}><span>현재가</span><strong>{formatPrice(art.currentPrice)}원</strong></div>
          <p className={styles.closingTime}><span>마감</span><time dateTime={art.closingTime}>{formatClosingTime(art.closingTime)}</time></p>
        </div>
      </article>
    </Link>
  )
}

export function LoadingResults() {
  return (
    <div className={styles.grid} aria-label="작품 목록을 불러오는 중" aria-busy="true">
      {Array.from({ length: 8 }, (_, index) => (
        <div key={index} className={styles.skeletonCard}><div className={styles.skeletonImage} /><div className={styles.skeletonBody}><span /><span /><span /></div></div>
      ))}
    </div>
  )
}

export function ResultFeedback({ title, message, actionLabel, onAction }) {
  return (
    <div className={styles.feedback} role="status">
      <span className={styles.feedbackIcon} aria-hidden="true">□</span>
      <h3>{title}</h3><p>{message}</p>
      {actionLabel && <button type="button" className={styles.feedbackButton} onClick={onAction}>{actionLabel}</button>}
    </div>
  )
}

export function Pagination({ currentPage, totalPages, onPageChange }) {
  if (totalPages <= 1) return null
  const start = Math.max(1, Math.min(currentPage - 2, totalPages - 4))
  const pages = Array.from({ length: Math.min(5, totalPages) }, (_, index) => Math.max(1, start) + index)
  return (
    <nav className={styles.pagination} aria-label="작품 목록 페이지">
      <button type="button" onClick={() => onPageChange(currentPage - 1)} disabled={currentPage === 1} aria-label="이전 페이지">‹</button>
      {pages.map((page) => <button type="button" key={page} onClick={() => onPageChange(page)} aria-current={page === currentPage ? 'page' : undefined}>{page}</button>)}
      <button type="button" onClick={() => onPageChange(currentPage + 1)} disabled={currentPage >= totalPages} aria-label="다음 페이지">›</button>
    </nav>
  )
}
