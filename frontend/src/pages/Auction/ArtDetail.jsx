import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { mockArts } from '../../data/mockArts'
import styles from './ArtDetail.module.css'

export default function ArtDetail() {
  const { id } = useParams()
  const art = mockArts.find((item) => item.id === id)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [isLiked, setIsLiked] = useState(art?.isLiked ?? false)

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

  if (!art) {
    return <div className={styles.notFound}>작품을 찾을 수 없습니다</div>
  }

  return (
    <div className={styles.page}>
      <section className={styles.hero}>
        <button
          type="button"
          className={styles.imageButton}
          onClick={() => setIsModalOpen(true)}
          aria-label={`${art.title} 원본 이미지 보기`}
        >
          <img className={styles.image} src={art.imageUrl} alt={art.title} />
        </button>

        <div className={styles.info}>
          <p className={styles.kicker}>작품 상세</p>
          <h1 className={styles.title}>{art.title}</h1>
          <p className={styles.artist}>{art.artist}</p>

          <div className={styles.meta}>
            <p className={styles.metaItem}>
              <span className={styles.metaLabel}>카테고리</span>
              <span className={styles.metaValue}>{art.category}</span>
            </p>
            <p className={styles.metaItem}>
              <span className={styles.metaLabel}>재료</span>
              <span className={styles.metaValue}>{art.material}</span>
            </p>
          </div>

          <p className={styles.price}>
            <span className={styles.priceLabel}>가격</span>
            <strong>{art.price.toLocaleString()}원</strong>
          </p>

          <div className={styles.likeRow}>
            <button
              type="button"
              className={`${styles.likeButton} ${isLiked ? styles.likeButtonActive : ''}`}
              onClick={() => setIsLiked((prev) => !prev)}
              aria-pressed={isLiked}
            >
              <span className={styles.likeIcon} aria-hidden="true">
                {isLiked ? '\u2665' : '\u2661'}
              </span>
              <span className={styles.likeText}>{isLiked ? '찜 완료' : '찜하기'}</span>
            </button>
          </div>
        </div>
      </section>

      <section className={styles.descriptionSection}>
        <h2 className={styles.sectionTitle}>작품 소개</h2>
        <p className={styles.description}>{art.description}</p>
      </section>

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
            <img className={styles.modalImage} src={art.imageUrl} alt={art.title} />
          </div>
        </div>
      )}
    </div>
  )
}
