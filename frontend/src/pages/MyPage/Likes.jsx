import { useNavigate } from 'react-router-dom'
import { PageBanner, Badge, Empty, PageWrap, ActionBtn } from './components/atoms'
import { MOCK_LIKES, STATUS_META, fmt } from './mockData'
import s from './Likes.module.css'

export default function Likes() {
  const navigate = useNavigate()
  if (!localStorage.getItem('token')) {
    alert('로그인이 필요합니다.')
    navigate('/login', { replace: true })
    return null
  }

  return (
    <PageWrap>
      <PageBanner title="찜한 작품" crumb="찜한 작품" />
      <div className={s.body}>
        {MOCK_LIKES.length === 0 ? (
          <Empty msg="찜한 작품이 없습니다." />
        ) : (
          <div className={s.grid}>
            {MOCK_LIKES.map((item) => (
              <article key={item.id} className={s.card}>
                <div className={s.thumbWrap}>
                  <img src={item.artImg} alt={item.artName} className={s.thumb} />
                  <Badge
                    label={STATUS_META[item.status]?.label ?? '상태 없음'}
                    color={STATUS_META[item.status]?.color ?? 'gray'}
                  />
                </div>
                <div className={s.content}>
                  <p className={s.title}>{item.artName}</p>
                  <p className={s.artist}>by {item.artist}</p>
                  <p className={s.price}>현재가 {fmt(item.currentPrice)}원</p>
                  <div className={s.actions}>
                    <ActionBtn to={`/auction/${item.id}`} variant="outline">상세 보기</ActionBtn>
                    {item.status !== 'ended' && (
                      <ActionBtn to={`/auction/${item.id}`} variant="fill">입찰하기</ActionBtn>
                    )}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </div>
    </PageWrap>
  )
}
