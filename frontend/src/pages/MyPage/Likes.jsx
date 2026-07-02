import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageBanner, Badge, Empty, PageWrap, ActionBtn } from './components/atoms'
import { getMyLikes, removeArtLike } from '../../api/userApi'
import s from './Likes.module.css'

const PAGE_SIZE = 12

const STATUS_META = {
  0: { label: '진행 중', color: 'green' },
  1: { label: '종료', color: 'gray' },
  2: { label: '승인 대기', color: 'blue' },
}

const fmt = (n) => Number(n ?? 0).toLocaleString()

export default function Likes() {
  const navigate = useNavigate()
  const [likes, setLikes] = useState([])
  const [pageInfo, setPageInfo] = useState({ page: 0, last: true })
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const [removingId, setRemovingId] = useState(null)

  const loadLikes = useCallback(async (page = 0) => {
    const isFirstPage = page === 0
    if (isFirstPage) {
      setLoading(true)
    } else {
      setLoadingMore(true)
    }
    setError('')

    try {
      const { data } = await getMyLikes({ page, size: PAGE_SIZE })
      setLikes((prev) => (isFirstPage ? data.content : [...prev, ...data.content]))
      setPageInfo({ page: data.number, last: data.last })
    } catch (err) {
      setError(err.response?.data?.message || '찜한 작품을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }, [])

  useEffect(() => {
    if (!localStorage.getItem('token')) {
      alert('로그인이 필요합니다.')
      navigate('/login', { replace: true })
      return
    }
    loadLikes()
  }, [loadLikes, navigate])

  const handleRemove = async (artId) => {
    if (!window.confirm('찜 목록에서 삭제할까요?')) return

    setRemovingId(artId)
    try {
      await removeArtLike(artId)
      setLikes((prev) => prev.filter((item) => item.artId !== artId))
    } catch (err) {
      alert(err.response?.data?.message || '찜 해제에 실패했습니다.')
    } finally {
      setRemovingId(null)
    }
  }

  return (
    <PageWrap>
      <PageBanner title="찜한 작품" crumb="찜한 작품" />
      <div className={s.body}>
        {loading ? (
          <Empty msg="찜한 작품을 불러오는 중입니다." />
        ) : error ? (
          <div className={s.feedback}>
            <p>{error}</p>
            <ActionBtn onClick={() => loadLikes()} variant="outline">다시 시도</ActionBtn>
          </div>
        ) : likes.length === 0 ? (
          <Empty msg="찜한 작품이 없습니다." />
        ) : (
          <>
            <div className={s.grid}>
              {likes.map((item) => {
                const status = STATUS_META[item.artStatus] ?? { label: '상태 없음', color: 'gray' }
                const isEnded = item.artStatus === 1

                return (
                  <article key={item.likeId} className={s.card}>
                    <div className={s.thumbWrap}>
                      <img src={item.artImg} alt={item.artName} className={s.thumb} />
                      <Badge label={status.label} color={status.color} />
                    </div>
                    <div className={s.content}>
                      <p className={s.title}>{item.artName}</p>
                      <p className={s.artist}>by {item.artistName || '작가 미상'}</p>
                      <p className={s.price}>현재가 {fmt(item.currentPrice)}원</p>
                      <div className={s.actions}>
                        <ActionBtn to={`/auction/${item.artId}`} variant="outline">상세 보기</ActionBtn>
                        {!isEnded && (
                          <ActionBtn to={`/auction/${item.artId}`} variant="fill">입찰하기</ActionBtn>
                        )}
                        <ActionBtn
                          onClick={() => handleRemove(item.artId)}
                          variant="danger"
                          disabled={removingId === item.artId}
                        >
                          {removingId === item.artId ? '삭제 중' : '찜 해제'}
                        </ActionBtn>
                      </div>
                    </div>
                  </article>
                )
              })}
            </div>

            {!pageInfo.last && (
              <div className={s.more}>
                <ActionBtn
                  onClick={() => loadLikes(pageInfo.page + 1)}
                  variant="outline"
                  disabled={loadingMore}
                >
                  {loadingMore ? '불러오는 중' : '더 보기'}
                </ActionBtn>
              </div>
            )}
          </>
        )}
      </div>
    </PageWrap>
  )
}
