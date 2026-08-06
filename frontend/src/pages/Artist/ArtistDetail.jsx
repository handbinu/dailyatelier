import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom'
import { getArtist, getArtistArts } from '../../api/artistApi'
import { formatClosingTime, formatPrice, getDeadlineMeta } from '../../utils/artDisplay'
import { applyArtImageFallback, applyArtImageFallbackIfBlank, getArtImageSrc } from '../../utils/artImage'
import styles from './ArtistDetail.module.css'

const PAGE_SIZE = 12
const PAGE_WINDOW = 5

const parsePage = (value) => {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 1 ? parsed : 1
}

const getVisiblePages = (currentPage, totalPages) => {
  if (totalPages <= PAGE_WINDOW) return Array.from({ length: totalPages }, (_, index) => index + 1)
  let start = Math.max(1, currentPage - Math.floor(PAGE_WINDOW / 2))
  const end = Math.min(totalPages, start + PAGE_WINDOW - 1)
  start = Math.max(1, end - PAGE_WINDOW + 1)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
}

const isArtistNotFound = (error) =>
  error.response?.status === 404 && error.response?.data?.code === 'ARTIST_NOT_FOUND'

export default function ArtistDetail() {
  const { artistId } = useParams()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const pageParam = searchParams.get('page')
  const currentPage = parsePage(pageParam)
  const [artist, setArtist] = useState(null)
  const [artsResult, setArtsResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notFound, setNotFound] = useState(false)
  const [retryKey, setRetryKey] = useState(0)
  const backTo = typeof location.state?.from === 'string' && location.state.from.startsWith('/artists?')
    ? location.state.from
    : '/artists?page=1'

  useEffect(() => {
    if (pageParam === String(currentPage)) return
    const next = new URLSearchParams(searchParams)
    next.set('page', String(currentPage))
    setSearchParams(next, { replace: true })
  }, [currentPage, pageParam, searchParams, setSearchParams])

  useEffect(() => {
    const controller = new AbortController()
    const loadDetail = async () => {
      setLoading(true)
      setError('')
      setNotFound(false)
      try {
        const artistResponse = await getArtist(artistId, { signal: controller.signal })
        setArtist(artistResponse.data)
        const artsResponse = await getArtistArts(artistId, {
          page: currentPage - 1,
          size: PAGE_SIZE,
          signal: controller.signal,
        })
        setArtsResult(artsResponse.data)
      } catch (requestError) {
        if (requestError.code === 'ERR_CANCELED') return
        setArtsResult(null)
        if (isArtistNotFound(requestError)) {
          setArtist(null)
          setNotFound(true)
        } else {
          setError(requestError.response?.data?.message || '작가 정보를 불러오지 못했습니다.')
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }
    loadDetail()
    return () => controller.abort()
  }, [artistId, currentPage, retryKey])

  const visiblePages = useMemo(
    () => getVisiblePages(currentPage, artsResult?.totalPages ?? 0),
    [currentPage, artsResult?.totalPages],
  )

  const moveToPage = (page) => {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(page))
    setSearchParams(next)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  if (loading) return <StatePage loading title="작가 정보를 불러오는 중입니다" />
  if (notFound) return <StatePage title="존재하지 않는 작가입니다" message="삭제되었거나 공개되지 않은 작가입니다." backTo={backTo} />
  if (error) return <StatePage title="작가 정보를 불러오지 못했습니다" message={error} actionLabel="다시 시도" onAction={() => setRetryKey((key) => key + 1)} backTo={backTo} />

  const arts = artsResult?.content ?? []
  const detailLocation = `/artists/${artistId}?page=${currentPage}`

  return (
    <main className={styles.page}>
      <section className={styles.profile}>
        <div className={styles.profileInner}>
          <Link to={backTo} className={styles.back}>← 작가 목록</Link>
          <div className={styles.profileGrid}>
            <img src={artist.profileImagePath || '/img/artist.png'} alt="" onError={(event) => { event.currentTarget.src = '/img/artist.png' }} />
            <div>
              <p className={styles.eyebrow}>ARTIST PROFILE</p>
              <h1>{artist.artistName}</h1>
              <p className={styles.intro}>{artist.artistIntro || '등록된 작가 소개가 없습니다.'}</p>
              <p className={styles.activeCount}>현재 입찰 가능한 작품 <strong>{artist.activeArtCount}점</strong></p>
            </div>
          </div>
        </div>
      </section>

      <section className={styles.content} aria-labelledby="artist-arts-title">
        <div className={styles.listHeader}>
          <div><p className={styles.sectionLabel}>PUBLIC COLLECTION</p><h2 id="artist-arts-title">공개 작품</h2></div>
          <p>총 {Number(artsResult?.totalElements ?? 0).toLocaleString('ko-KR')}점</p>
        </div>
        {arts.length === 0 ? (
          <div className={styles.feedback} role="status">
            <h3>표시할 공개 작품이 없습니다</h3>
            <p>{currentPage > 1 ? '요청한 페이지에 표시할 작품이 없습니다.' : '공개 작품이 등록되면 이곳에 표시됩니다.'}</p>
            {currentPage > 1 && <button type="button" onClick={() => moveToPage(1)}>첫 페이지로</button>}
          </div>
        ) : (
          <>
            <div className={styles.grid}>{arts.map((art) => <ArtCard key={art.artId} art={art} from={detailLocation} />)}</div>
            {artsResult.totalPages > 1 && (
              <nav className={styles.pagination} aria-label="작가 공개 작품 페이지">
                <button type="button" onClick={() => moveToPage(currentPage - 1)} disabled={currentPage === 1} aria-label="이전 페이지">‹</button>
                {visiblePages.map((page) => <button type="button" key={page} onClick={() => moveToPage(page)} className={page === currentPage ? styles.activePage : ''} aria-current={page === currentPage ? 'page' : undefined}>{page}</button>)}
                <button type="button" onClick={() => moveToPage(currentPage + 1)} disabled={currentPage >= artsResult.totalPages} aria-label="다음 페이지">›</button>
              </nav>
            )}
          </>
        )}
      </section>
    </main>
  )
}

function ArtCard({ art, from }) {
  const deadline = getDeadlineMeta(art.closingTime)
  return (
    <Link to={`/auction/${art.artId}`} state={{ from }} className={styles.card} aria-label={`${art.name} 작품 상세 보기`}>
      <img src={getArtImageSrc(art.imgPath)} alt={art.name} loading="lazy" onError={applyArtImageFallback} onLoad={applyArtImageFallbackIfBlank} />
      <div className={styles.cardBody}>
        <span className={deadline.isClosed ? styles.closed : deadline.isUrgent ? styles.urgent : ''}>{deadline.label}</span>
        <h3>{art.name}</h3>
        <p><span>현재가</span><strong>{formatPrice(art.currentPrice)}원</strong></p>
        <p><span>마감</span><time dateTime={art.closingTime}>{formatClosingTime(art.closingTime)}</time></p>
      </div>
    </Link>
  )
}

function StatePage({ loading = false, title, message, actionLabel, onAction, backTo }) {
  return (
    <main className={styles.statePage} aria-live="polite" aria-busy={loading}>
      <h1>{title}</h1>
      {message && <p>{message}</p>}
      <div>{actionLabel && <button type="button" onClick={onAction}>{actionLabel}</button>}{backTo && <Link to={backTo}>작가 목록으로</Link>}</div>
    </main>
  )
}
