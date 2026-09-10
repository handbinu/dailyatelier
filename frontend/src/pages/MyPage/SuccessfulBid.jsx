import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageBanner, Empty, PageWrap, ActionBtn } from './components/atoms'
import { getMyWins } from '../../api/userApi'
import { formatClosingTime, formatPrice } from '../../utils/artDisplay'
import { applyArtImageFallback, getArtImageSrc } from '../../utils/artImage'
import { createLatestRequest } from '../../utils/latestRequest'
import s from './SuccessfulBid.module.css'

const PAGE_SIZE = 12

export default function SuccessfulBid() {
  const navigate = useNavigate()
  const [result, setResult] = useState(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const winsRequest = useRef(null)

  const loadWins = useCallback(() => {
    const requestPage = page
    return winsRequest.current?.run(
      async ({ signal }) => {
        const { data } = await getMyWins({
          page: requestPage,
          size: PAGE_SIZE,
          signal,
        })
        return data
      },
      {
        onStart: () => {
          setLoading(true)
          setError('')
        },
        onSuccess: setResult,
        onError: (requestError) => {
          setResult(null)
          if (requestError.response?.status === 401) {
            setError('로그인이 만료되었습니다. 다시 로그인해 주세요.')
          } else {
            setError(requestError.response?.data?.message || '낙찰 작품을 불러오지 못했습니다.')
          }
        },
        onFinally: () => setLoading(false),
      },
    )
  }, [page])

  useEffect(() => {
    const request = createLatestRequest()
    winsRequest.current = request
    return () => {
      request.dispose()
      if (winsRequest.current === request) winsRequest.current = null
    }
  }, [])

  useEffect(() => {
    if (!localStorage.getItem('token')) {
      navigate('/login', { replace: true })
      return
    }
    loadWins()
  }, [loadWins, navigate, page])

  const wins = result?.content ?? []

  return (
    <PageWrap>
      <PageBanner title="낙찰 작품" crumb="낙찰 작품" />
      <div className={s.body}>
        {loading ? (
          <Empty msg="낙찰 작품을 불러오는 중입니다." />
        ) : error ? (
          <div className={s.feedback} role="alert">
            <p>{error}</p>
            <ActionBtn onClick={loadWins} variant="outline">다시 시도</ActionBtn>
          </div>
        ) : wins.length === 0 ? (
          <Empty msg="낙찰된 작품이 없습니다." />
        ) : (
          <>
            <div className={s.grid}>
              {wins.map((item) => (
                <article key={item.artId} className={s.card}>
                  <img
                    src={getArtImageSrc(item.imgPath)}
                    alt={item.artName}
                    className={s.thumb}
                    onError={applyArtImageFallback}
                  />
                  <div className={s.content}>
                    <p className={s.title}>{item.artName}</p>
                    <p className={s.artist}>by {item.artistName || '작가 정보 없음'}</p>
                    <p className={s.meta}>{formatClosingTime(item.closedAt)} 낙찰 확정</p>
                    <p className={s.price}>낙찰가 {formatPrice(item.winningPrice)}원</p>
                    <div className={s.actions}>
                      <ActionBtn to={`/auction/${item.artId}`} variant="outline">상세 보기</ActionBtn>
                      {item.orderId ? (
                        <ActionBtn
                          to={`/mypage/order-status?orderId=${item.orderId}`}
                          variant="fill"
                        >
                          주문 확인
                        </ActionBtn>
                      ) : (
                        <p className={s.orderUnavailable}>연결된 주문을 확인할 수 없습니다.</p>
                      )}
                    </div>
                  </div>
                </article>
              ))}
            </div>
            {result.totalPages > 1 && (
              <nav className={s.pagination} aria-label="낙찰 작품 목록 페이지">
                <button
                  type="button"
                  onClick={() => setPage((current) => Math.max(current - 1, 0))}
                  disabled={page === 0}
                >
                  이전
                </button>
                <span>{page + 1} / {result.totalPages}</span>
                <button
                  type="button"
                  onClick={() => setPage((current) => current + 1)}
                  disabled={page + 1 >= result.totalPages}
                >
                  다음
                </button>
              </nav>
            )}
          </>
        )}
      </div>
    </PageWrap>
  )
}
