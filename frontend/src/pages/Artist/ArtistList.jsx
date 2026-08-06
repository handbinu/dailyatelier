import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { getArtists } from '../../api/artistApi'
import styles from './ArtistList.module.css'

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

export default function ArtistList() {
  const [searchParams, setSearchParams] = useSearchParams()
  const keywordParam = searchParams.get('keyword') ?? ''
  const keyword = keywordParam.trim()
  const pageParam = searchParams.get('page')
  const currentPage = parsePage(pageParam)
  const [query, setQuery] = useState(keywordParam)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => setQuery(keywordParam), [keywordParam])

  useEffect(() => {
    if (pageParam === String(currentPage) && keywordParam === keyword) return
    const next = new URLSearchParams(searchParams)
    next.set('page', String(currentPage))
    if (keyword) next.set('keyword', keyword)
    else next.delete('keyword')
    setSearchParams(next, { replace: true })
  }, [currentPage, keyword, keywordParam, pageParam, searchParams, setSearchParams])

  useEffect(() => {
    const controller = new AbortController()
    const loadArtists = async () => {
      setLoading(true)
      setError('')
      try {
        const { data } = await getArtists({
          keyword,
          page: currentPage - 1,
          size: PAGE_SIZE,
          signal: controller.signal,
        })
        setResult(data)
      } catch (requestError) {
        if (requestError.code === 'ERR_CANCELED') return
        setResult(null)
        setError(requestError.response?.data?.message || '작가 목록을 불러오지 못했습니다.')
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }
    loadArtists()
    return () => controller.abort()
  }, [currentPage, keyword, retryKey])

  const visiblePages = useMemo(
    () => getVisiblePages(currentPage, result?.totalPages ?? 0),
    [currentPage, result?.totalPages],
  )

  const updateLocation = (nextKeyword, page) => {
    const next = new URLSearchParams()
    if (nextKeyword) next.set('keyword', nextKeyword)
    next.set('page', String(page))
    setSearchParams(next)
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    updateLocation(query.trim(), 1)
  }

  const moveToPage = (page) => {
    updateLocation(keyword, page)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const listLocation = `/artists?${searchParams.toString()}`
  const artists = result?.content ?? []

  return (
    <main className={styles.page}>
      <header className={styles.hero}>
        <div className={styles.heroInner}>
          <p className={styles.eyebrow}>DAILY ARTISTS</p>
          <h1>작가 찾기</h1>
          <p>데일리 아틀리에의 작가와 작품을 만나보세요.</p>
          <form className={styles.search} onSubmit={handleSubmit} role="search">
            <label className={styles.srOnly} htmlFor="artist-keyword">작가명 검색</label>
            <input
              id="artist-keyword"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="작가명을 입력하세요"
            />
            <button type="submit">검색</button>
          </form>
        </div>
      </header>

      <section className={styles.content} aria-labelledby="artist-list-title">
        <div className={styles.listHeader}>
          <div>
            <p className={styles.sectionLabel}>{keyword ? 'SEARCH RESULT' : 'OUR ARTISTS'}</p>
            <h2 id="artist-list-title">{keyword ? `“${keyword}” 검색 결과` : '전체 작가'}</h2>
          </div>
          {!loading && !error && <p>총 {Number(result?.totalElements ?? 0).toLocaleString('ko-KR')}명</p>}
        </div>

        {loading ? <LoadingGrid /> : error ? (
          <Feedback title="작가를 불러오지 못했습니다" message={error} actionLabel="다시 시도" onAction={() => setRetryKey((key) => key + 1)} />
        ) : artists.length === 0 ? (
          <Feedback
            title={keyword ? '검색 결과가 없습니다' : '등록된 작가가 없습니다'}
            message={currentPage > 1 ? '요청한 페이지에 표시할 작가가 없습니다.' : keyword ? '다른 작가명으로 검색해 보세요.' : '새로운 작가가 등록되면 이곳에 표시됩니다.'}
            actionLabel={currentPage > 1 ? '첫 페이지로' : undefined}
            onAction={currentPage > 1 ? () => moveToPage(1) : undefined}
          />
        ) : (
          <>
            <div className={styles.grid}>
              {artists.map((artist) => (
                <Link key={artist.artistId} to={`/artists/${artist.artistId}`} state={{ from: listLocation }} className={styles.card}>
                  <img src={artist.profileImagePath || '/img/artist.png'} alt="" onError={(event) => { event.currentTarget.src = '/img/artist.png' }} />
                  <div className={styles.cardBody}>
                    <h3>{artist.artistName}</h3>
                    <p className={styles.intro}>{artist.artistIntro || '등록된 작가 소개가 없습니다.'}</p>
                    <p className={styles.activeCount}>현재 입찰 가능 <strong>{artist.activeArtCount}점</strong></p>
                  </div>
                </Link>
              ))}
            </div>
            {result.totalPages > 1 && (
              <Pagination currentPage={currentPage} totalPages={result.totalPages} visiblePages={visiblePages} onMove={moveToPage} label="작가 목록 페이지" />
            )}
          </>
        )}
      </section>
    </main>
  )
}

function Pagination({ currentPage, totalPages, visiblePages, onMove, label }) {
  return (
    <nav className={styles.pagination} aria-label={label}>
      <button type="button" onClick={() => onMove(currentPage - 1)} disabled={currentPage === 1} aria-label="이전 페이지">‹</button>
      {visiblePages.map((page) => <button type="button" key={page} onClick={() => onMove(page)} className={page === currentPage ? styles.activePage : ''} aria-current={page === currentPage ? 'page' : undefined}>{page}</button>)}
      <button type="button" onClick={() => onMove(currentPage + 1)} disabled={currentPage >= totalPages} aria-label="다음 페이지">›</button>
    </nav>
  )
}

function LoadingGrid() {
  return <div className={styles.grid} aria-label="작가 목록을 불러오는 중" aria-busy="true">{Array.from({ length: 8 }, (_, index) => <div key={index} className={styles.skeleton}><span /><div><span /><span /><span /></div></div>)}</div>
}

function Feedback({ title, message, actionLabel, onAction }) {
  return <div className={styles.feedback} role="status"><h3>{title}</h3><p>{message}</p>{actionLabel && <button type="button" onClick={onAction}>{actionLabel}</button>}</div>
}
