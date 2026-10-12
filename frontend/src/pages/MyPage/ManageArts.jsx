import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { getMyArts } from '../../api/artApi'
import { formatClosingTime, formatPrice } from '../../utils/artDisplay'
import {
  applyArtImageFallback,
  applyArtImageFallbackIfBlank,
  getArtImageSrc,
} from '../../utils/artImage'
import { PageBanner, Badge, Empty, FilterBar, PageWrap, ActionBtn } from './components/atoms'
import styles from './MyPage.module.css'

const PAGE_SIZE = 12
const PAGE_WINDOW = 5
const STATUS_META = {
  0: { label: '진행 중', color: 'green' },
  1: { label: '유찰', color: 'gray' },
  2: { label: '낙찰', color: 'blue' },
  3: { label: '경매 취소', color: 'gray' },
}
const FILTERS = ['전체', '진행 중', '종료']
const STATE_BY_FILTER = { '전체': 'ALL', '진행 중': 'ACTIVE', '종료': 'ENDED' }
const FILTER_BY_STATE = { ALL: '전체', ACTIVE: '진행 중', ENDED: '종료' }

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

export default function ManageArts() {
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const pageParam = searchParams.get('page')
  const currentPage = parsePage(pageParam)
  const stateParam = searchParams.get('state')?.toUpperCase()
  const currentState = FILTER_BY_STATE[stateParam] ? stateParam : 'ALL'
  const currentFilter = FILTER_BY_STATE[currentState]
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    if (pageParam === String(currentPage) && stateParam === currentState) return

    setSearchParams((previous) => {
      const next = new URLSearchParams(previous)
      next.set('page', String(currentPage))
      next.set('state', currentState)
      return next
    }, { replace: true })
  }, [currentPage, currentState, pageParam, setSearchParams, stateParam])

  useEffect(() => {
    const controller = new AbortController()

    const loadMyArts = async () => {
      setLoading(true)
      setError('')

      try {
        const { data } = await getMyArts({
          state: currentState,
          page: currentPage - 1,
          size: PAGE_SIZE,
          signal: controller.signal,
        })
        setResult(data)
      } catch (requestError) {
        if (requestError.code === 'ERR_CANCELED') return
        setResult(null)
        if (requestError.response?.status === 401) {
          setError('로그인이 만료되었습니다. 다시 로그인해 주세요.')
        } else if (requestError.response?.status === 403) {
          setError('작가 회원만 내 작품을 조회할 수 있습니다.')
        } else {
          setError(requestError.response?.data?.message || '내 작품을 불러오지 못했습니다.')
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }

    loadMyArts()
    return () => controller.abort()
  }, [currentPage, currentState, retryKey])

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

  const changeFilter = (filter) => {
    const next = new URLSearchParams(searchParams)
    next.set('state', STATE_BY_FILTER[filter])
    next.set('page', '1')
    setSearchParams(next)
  }

  const arts = result?.content ?? []
  const listLocation = `/mypage/manage-arts?state=${currentState}&page=${currentPage}`

  return (
    <PageWrap>
      <PageBanner title="내 작품 관리" crumb="내 작품 관리" />

      <main className={styles.managePage}>
        {location.state?.feedback && (
          <p className={styles.manageSuccess} role="status">{location.state.feedback}</p>
        )}
        <div className={styles.manageHeader}>
          {!loading && !error && (
            <p className={styles.manageResultCount}>
              등록 작품 {Number(result?.totalElements ?? 0).toLocaleString('ko-KR')}점
            </p>
          )}
          <ActionBtn to="/upload" variant="final">+ 작품 등록</ActionBtn>
        </div>
        <div className={styles.manageFilters}>
          <FilterBar options={FILTERS} value={currentFilter} onChange={changeFilter} />
        </div>

        {loading ? (
          <div className={styles.manageLoading} role="status" aria-live="polite">
            내 작품을 불러오는 중입니다.
          </div>
        ) : error ? (
          <div className={styles.manageFeedback} role="alert">
            <p>{error}</p>
            <button type="button" className="da-button-secondary-soft" onClick={() => setRetryKey((key) => key + 1)}>다시 시도</button>
          </div>
        ) : arts.length === 0 ? (
          <div className={styles.manageEmpty}>
            <Empty
              msg={
                currentPage > 1
                  ? '요청한 페이지에 등록된 작품이 없습니다.'
                  : currentState === 'ACTIVE'
                    ? '진행 중인 작품이 없습니다.'
                    : currentState === 'ENDED'
                      ? '종료된 작품이 없습니다.'
                      : '등록한 작품이 없습니다.'
              }
            />
            {currentPage > 1 && (
              <button type="button" className={`${styles.manageFirstPageButton} da-button-secondary-soft`} onClick={() => moveToPage(1)}>
                첫 페이지로
              </button>
            )}
          </div>
        ) : (
          <>
            <div className={styles.artManageGrid}>
              {arts.map((art) => {
                const status = STATUS_META[art.artStatus] ?? { label: '상태 미정', color: 'gray' }
                const isActive = art.artStatus === 0
                const isSold = art.result === 'SOLD'
                return (
                  <article key={art.artId} className={styles.manageCard}>
                    <Link
                      to={`/auction/${art.artId}`}
                      state={{ from: listLocation }}
                      className={styles.manageCardLink}
                      aria-label={`${art.name} 작품 상세 보기`}
                      >
                        <div className={styles.manageImgWrap}>
                          <img
                          src={getArtImageSrc(art.imgPath)}
                          alt={art.name}
                          loading="lazy"
                            onError={applyArtImageFallback}
                            onLoad={applyArtImageFallbackIfBlank}
                          />
                        </div>
                        <div className={styles.manageCardBody}>
                          <div className={styles.manageTitleRow}>
                            <div className={styles.manageTitleGroup}>
                              <div className={styles.manageTitleLine}>
                                <h2 className={styles.manageCardTitle}>{art.name}</h2>
                                <div className={styles.manageStatusBadge}>
                                  <Badge label={status.label} color={status.color} />
                                </div>
                              </div>
                              <p className={styles.manageCardType}>{art.material || '재료 정보 없음'}</p>
                            </div>
                          </div>
                          <div className={styles.manageFinancials}>
                            <div className={styles.managePriceMetrics}>
                              <div className={styles.managePriceMetric}>
                                <span>시작가</span>
                                <strong>{formatPrice(art.startPrice)}원</strong>
                              </div>
                              {isActive && (
                                <div className={`${styles.managePriceMetric} ${styles.managePriceMetricLeading}`}>
                                  <span>현재가</span>
                                  <strong>{formatPrice(art.currentPrice)}원</strong>
                                </div>
                              )}
                              {isSold && (
                                <div className={`${styles.managePriceMetric} ${styles.managePriceMetricLeading} ${styles.managePriceMetricResult}`}>
                                  <span>낙찰가</span>
                                  <strong>{art.winningPrice == null ? '확인 필요' : `${formatPrice(art.winningPrice)}원`}</strong>
                                </div>
                              )}
                            </div>
                            {!isActive && !isSold && (
                              <div className={styles.manageOutcome}>
                                <strong>{art.result === 'CANCELED' ? '경매 취소' : '유찰'}</strong>
                              </div>
                            )}
                          </div>
                          <div className={styles.manageMetaRow}>
                            <p className={styles.manageBidCount}>입찰 {Number(art.bidCount ?? 0).toLocaleString('ko-KR')}건</p>
                            <p className={styles.manageCloseTime}>
                              마감 <time dateTime={art.closingTime}>{formatClosingTime(art.closingTime)}</time>
                            </p>
                          </div>
                        </div>
                      </Link>
                    {isActive && (
                      <div className={styles.manageCardActions}>
                        <Link
                          to={`/mypage/manage-arts/${art.artId}/edit`}
                          state={{ from: listLocation }}
                          className={styles.manageEditLink}
                        >
                          수정·삭제
                        </Link>
                      </div>
                    )}
                    {isActive && (
                      <div className={styles.manageMobileCardActions}>
                        <Link
                          to={`/mypage/manage-arts/${art.artId}/edit`}
                          state={{ from: listLocation }}
                          className={styles.manageEditLink}
                        >
                          수정·삭제
                        </Link>
                      </div>
                    )}
                    <div className={styles.manageMobileBottomRow}>
                      <p className={styles.manageBidCount}>입찰 {Number(art.bidCount ?? 0).toLocaleString('ko-KR')}건</p>
                      <p className={styles.manageCloseTime}>
                        마감 <time dateTime={art.closingTime}>{formatClosingTime(art.closingTime)}</time>
                      </p>
                    </div>
                  </article>
                )
              })}
            </div>

            {result.totalPages > 1 && (
              <nav className={styles.managePagination} aria-label="내 작품 목록 페이지">
                <button type="button" onClick={() => moveToPage(currentPage - 1)} disabled={currentPage === 1} aria-label="이전 페이지">‹</button>
                {visiblePages.map((page) => (
                  <button
                    type="button"
                    key={page}
                    className={page === currentPage ? `${styles.managePageButtonActive} da-pagination-current` : ''}
                    onClick={() => moveToPage(page)}
                    aria-current={page === currentPage ? 'page' : undefined}
                  >
                    {page}
                  </button>
                ))}
                <button type="button" onClick={() => moveToPage(currentPage + 1)} disabled={currentPage >= result.totalPages} aria-label="다음 페이지">›</button>
              </nav>
            )}
          </>
        )}
      </main>
    </PageWrap>
  )
}
