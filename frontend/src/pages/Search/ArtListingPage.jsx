import { useEffect, useRef, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { searchArts } from '../../api/artApi'
import { ArtResults, LoadingResults, Pagination, ResultFeedback } from '../../components/ArtResults/ArtResults'
import { CATEGORIES, normalizeSearchParams, readSearchState } from './searchParams'
import styles from './ArtSearch.module.css'

const PAGE_SIZE = 12
const CATEGORY_LABELS = {
  OIL_PAINTING: '유화', WATERCOLOR: '수채화', ACRYLIC_PAINTING: '아크릴화', DRAWING: '드로잉', DIGITAL_ART: '디지털 아트',
  PRINTMAKING: '판화', PHOTOGRAPHY: '사진', SCULPTURE: '조각', CRAFT: '공예', MIXED_MEDIA: '혼합 매체', OTHER: '기타',
}
const SEARCH_KEYS = ['q', 'artist', 'format', 'category', 'status', 'sort', 'page']
const AUCTION_KEYS = ['category', 'sort', 'page']

export default function ArtListingPage({ preset = {}, title, subtitle, eyebrow, resultTitle, auction = false }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const location = useLocation()
  const allowedKeys = auction ? AUCTION_KEYS : SEARCH_KEYS
  const allowedCategories = preset.format === 'DIGITAL'
    ? ['DIGITAL_ART']
    : preset.format === 'PHYSICAL' ? CATEGORIES.filter((value) => value !== 'DIGITAL_ART') : CATEGORIES
  const rawQuery = searchParams.toString()
  const canonicalParams = normalizeSearchParams(searchParams, { allowedKeys, allowedCategories })
  const canonicalQuery = canonicalParams.toString()
  const urlState = readSearchState(canonicalParams)
  const state = { ...urlState, ...preset }
  const { q, artist, format, category, status, sort, page } = state
  const [requestState, setRequestState] = useState(null)
  const [retryKey, setRetryKey] = useState(0)
  const requestSequence = useRef(0)
  const requestKey = `${location.pathname}?${canonicalQuery}#${retryKey}`
  const loading = requestState?.key !== requestKey
  const result = loading ? null : requestState.data
  const error = loading ? '' : requestState.error

  useEffect(() => {
    if (rawQuery !== canonicalQuery) setSearchParams(canonicalQuery, { replace: true })
  }, [canonicalQuery, rawQuery, setSearchParams])

  useEffect(() => {
    const controller = new AbortController()
    const sequence = ++requestSequence.current
    searchArts({ q, artist, format, category, status, sort, page: page - 1, size: PAGE_SIZE, signal: controller.signal })
      .then(({ data }) => { if (sequence === requestSequence.current) setRequestState({ key: requestKey, data, error: '' }) })
      .catch((requestError) => {
        if (controller.signal.aborted || requestError.code === 'ERR_CANCELED' || sequence !== requestSequence.current) return
        setRequestState({ key: requestKey, data: null, error: requestError.response?.data?.message || '작품을 불러오지 못했습니다.' })
      })
    return () => controller.abort()
  }, [artist, category, format, page, q, requestKey, sort, status])

  const updateConditions = (changes) => {
    const next = new URLSearchParams(canonicalParams)
    Object.entries(changes).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key))
    next.delete('page')
    setSearchParams(normalizeSearchParams(next, { allowedKeys, allowedCategories }))
  }
  const reset = () => setSearchParams({})
  const moveToPage = (nextPage) => {
    const next = new URLSearchParams(canonicalParams)
    if (nextPage > 1) next.set('page', String(nextPage)); else next.delete('page')
    setSearchParams(next)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  const arts = result?.content || []

  return <main className={styles.page}>
    <header className={styles.hero}><p>{eyebrow}</p><h1>{title}</h1><span>{subtitle}</span></header>
    <section className={styles.content} aria-labelledby="art-results-title">
      <div className={styles.filters}>
        {!auction && <KeywordSearch key={`${urlState.q}:${urlState.artist}`} q={urlState.q} artist={urlState.artist} onSubmit={updateConditions} />}
        <div className={`${styles.selectGrid} ${auction ? styles.auctionSelectGrid : ''}`}>
          {!auction && <FilterSelect label="형태" value={urlState.format} onChange={(value) => updateConditions({ format: value })} options={[['DIGITAL', '디지털'], ['PHYSICAL', '실물']]} />}
          <FilterSelect label="카테고리" value={urlState.category} onChange={(value) => updateConditions({ category: value })} options={allowedCategories.map((value) => [value, CATEGORY_LABELS[value]])} />
          {!auction && <FilterSelect label="상태" value={urlState.status} onChange={(value) => updateConditions({ status: value })} options={[['UPCOMING', '예정'], ['ONGOING', '진행 중'], ['ENDED', '종료']]} />}
          <FilterSelect label="정렬" value={urlState.sort} allLabel={null} onChange={(value) => updateConditions({ sort: value })} options={[['ENDING_SOON', '마감 임박순'], ['NEWEST', '최신 등록순'], ['PRICE_ASC', '가격 낮은순'], ['PRICE_DESC', '가격 높은순']]} />
          <button type="button" className={styles.resetButton} onClick={reset}>{auction ? '필터 초기화' : '전체 초기화'}</button>
        </div>
      </div>
      <div className={styles.resultHeader}><div><p>{auction ? 'LIVE COLLECTION' : 'SEARCH RESULTS'}</p><h2 id="art-results-title">{resultTitle}</h2></div>{!loading && !error && <span>총 {Number(result?.totalElements || 0).toLocaleString('ko-KR')}점</span>}</div>
      {loading ? <LoadingResults /> : error ? <ResultFeedback title="작품을 불러오지 못했습니다" message={error} actionLabel="다시 시도" onAction={() => setRetryKey((key) => key + 1)} />
        : arts.length === 0 ? <ResultFeedback title={auction ? '진행 중인 작품이 없습니다' : '검색 결과가 없습니다'} message={page > 1 ? '요청한 페이지에 작품이 없습니다.' : auction ? '조건을 바꾸거나 필터를 초기화해 보세요.' : '조건을 바꾸거나 전체 초기화를 이용해 보세요.'} actionLabel={page > 1 ? '첫 페이지로' : canonicalQuery ? (auction ? '필터 초기화' : '전체 초기화') : undefined} onAction={page > 1 ? () => moveToPage(1) : canonicalQuery ? reset : undefined} />
        : <><ArtResults arts={arts} from={`${location.pathname}${location.search}`} /><Pagination currentPage={page} totalPages={result.totalPages} onPageChange={moveToPage} /></>}
    </section>
  </main>
}

function KeywordSearch({ q, artist, onSubmit }) {
  const [draft, setDraft] = useState({ q, artist })
  return <form className={styles.keywordGrid} onSubmit={(event) => { event.preventDefault(); onSubmit(draft) }} role="search">
    <label>작품명<input value={draft.q} onChange={(event) => setDraft((value) => ({ ...value, q: event.target.value }))} placeholder="작품명 검색" /></label>
    <label>작가명<input value={draft.artist} onChange={(event) => setDraft((value) => ({ ...value, artist: event.target.value }))} placeholder="작가명 검색" /></label>
    <button type="submit" className={styles.searchButton}>검색</button>
  </form>
}

function FilterSelect({ label, value, onChange, options, allLabel = '전체' }) {
  return <label>{label}<select value={value} onChange={(event) => onChange(event.target.value)}>{allLabel !== null && <option value="">{allLabel}</option>}{options.map(([optionValue, text]) => <option key={optionValue} value={optionValue}>{text}</option>)}</select></label>
}
