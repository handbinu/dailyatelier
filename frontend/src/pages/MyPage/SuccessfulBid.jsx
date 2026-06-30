import { useNavigate } from 'react-router-dom'
import { PageBanner, Empty, PageWrap, ActionBtn } from './components/atoms'
import { MOCK_SUCCESSFUL, fmt } from './mockData'
import s from './SuccessfulBid.module.css'

export default function SuccessfulBid() {
  const navigate = useNavigate()
  if (!localStorage.getItem('token')) {
    alert('로그인이 필요합니다.')
    navigate('/login', { replace: true })
    return null
  }

  return (
    <PageWrap>
      <PageBanner title="낙찰 작품" crumb="낙찰 작품" />
      <div className={s.body}>
        {MOCK_SUCCESSFUL.length === 0 ? (
          <Empty msg="낙찰된 작품이 없습니다." />
        ) : (
          <div className={s.grid}>
            {MOCK_SUCCESSFUL.map((item) => (
              <article key={item.id} className={s.card}>
                <img src={item.artImg} alt={item.artName} className={s.thumb} />
                <div className={s.content}>
                  <p className={s.title}>{item.artName}</p>
                  <p className={s.artist}>by {item.artist}</p>
                  <p className={s.meta}>{item.orderedAt} 낙찰</p>
                  <p className={s.price}>낙찰가 {fmt(item.finalPrice)}원</p>
                  <div className={s.actions}>
                    <ActionBtn to={`/auction/${item.id}`} variant="outline">상세 보기</ActionBtn>
                    <ActionBtn to={`/write-review/${item.id}`} variant="fill">
                      {item.reviewWritten ? '리뷰 수정' : '리뷰 쓰기'}
                    </ActionBtn>
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
