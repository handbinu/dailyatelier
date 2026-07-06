// src/pages/MyPage/ManageArts.jsx — 작가 전용 내 작품 관리 독립 서브페이지
import { useNavigate } from 'react-router-dom'
import { PageBanner, Badge, Empty, PageWrap, ActionBtn } from './components/atoms'
import { MOCK_MY_ARTS, ART_STATUS_LABEL, ART_STATUS_COLOR, fmt } from './mockData'
import styles from './MyPage.module.css'

export default function ManageArts() {
  const navigate = useNavigate()
  
  if (!localStorage.getItem('token')) {
    alert('로그인이 필요합니다.')
    navigate('/login', { replace: true })
    return null
  }

  // 작가 권한 체크
  const userStatus = Number(localStorage.getItem('userStatus') ?? 0)
  if (userStatus !== 1) {
    alert('작가 회원만 접근 가능합니다.')
    navigate('/mypage', { replace: true })
    return null
  }

  return (
    <PageWrap>
      <PageBanner title="내 작품 관리" crumb="내 작품 관리" />

      <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '2rem 1.5rem' }}>
        <div className={styles.mockAlertBanner}>
          ⚠️ 해당 기능은 현재 준비 중이며, 화면의 데이터는 임시 목업 데이터입니다.
        </div>

        <div style={{ marginBottom: '2rem', display: 'flex', justifyContent: 'flex-end' }}>
          <ActionBtn to="/upload" variant="fill">+ 작품 등록</ActionBtn>
        </div>

        {MOCK_MY_ARTS.length === 0 ? (
          <Empty msg="등록한 작품이 없습니다." />
        ) : (
          <div className={styles.artManageGrid}>
            {MOCK_MY_ARTS.map(a => (
              <div key={a.id} className={styles.manageCard}>
                <div className={styles.manageImgWrap}>
                  <img src={a.img} alt={a.name} />
                  <Badge label={ART_STATUS_LABEL[a.status]} color={ART_STATUS_COLOR[a.status]} />
                </div>
                <div className={styles.manageCardBody}>
                  <p className={a.status === 'ended' ? styles.manageCardTitle : styles.manageCardTitle}>{a.name}</p>
                  <p className={styles.manageCardType}>{a.type} · {a.material}</p>
                  <div className={styles.managePriceRow}>
                    <span>시작가 {fmt(a.startPrice)}원</span>
                    <span>현재가 {fmt(a.currentPrice)}원</span>
                  </div>
                  <p className={styles.manageBidCount}>입찰 {a.bidCount}회</p>
                  <p className={styles.manageCloseTime}>마감: {a.closingTime}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </PageWrap>
  )
}
