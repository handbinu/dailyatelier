import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { getAllMyBids, getAllMyWins, getUserProfile, updateUserProfile } from '../../api/userApi'
import { getMyArts } from '../../api/artApi'
import { formatPrice } from '../../utils/artDisplay'
import { applyArtImageFallback, getArtImageSrc } from '../../utils/artImage'
import { MOCK_INQUIRIES, fmt } from './mockData'
import { Badge } from './components/atoms'
import styles from './MyPage.module.css'

const BID_STATUS_META = {
  ONGOING: { label: '진행 중', color: 'green' },
  IMMINENT: { label: '종료 임박', color: 'orange' },
  ENDED: { label: '종료', color: 'gray' },
}

const T = {
  home: '홈',
  mypage: '마이페이지',
  artist: '작가 회원',
  loading: '로딩 중...',
  loginRequired: '로그인이 필요합니다.',
  profileLoadFail: '프로필 조회 실패',
  profileChanged: '회원 정보가 수정되었습니다.',
  editFail: '정보 수정에 실패했습니다.',
  logoutConfirm: '로그아웃 하시겠습니까?',
  save: '저장',
  saving: '저장 중...',
  close: '닫기',
  editProfile: '프로필 수정',
  changePhoto: '프로필 사진 변경',
  user: '사용자',
  emailNone: '이메일 정보 없음',
  point: '보유 포인트',
  charge: '충전하기',
  artUpload: '작품 등록',
  artManage: '작품 관리',
  logout: '로그아웃',
  recentBids: '최근 입찰 현황',
  successful: '낙찰 작품',
  noBids: '진행 중인 입찰이 없습니다.',
  noSuccess: '낙찰 작품이 없습니다.',
  currentBid: '현재 입찰가',
  finalBid: '낙찰가',
  myArts: '내 작품',
  noInquiry: '문의 항목이 없습니다.',
  tabs: {
    home: '홈',
    bid: '입찰 현황',
    likes: '찜한 작품',
    success: '낙찰 작품',
    review: '리뷰 관리',
    order: '주문 조회',
    inquiry: '문의 현황',
    manage: '작품 관리',
    artistReview: '작품 리뷰',
  },
}

const USER_TABS = [
  { name: T.tabs.home, path: '/mypage' },
  { name: T.tabs.bid, path: '/mypage/bid-status' },
  { name: T.tabs.likes, path: '/mypage/likes' },
  { name: T.tabs.success, path: '/mypage/successful-bid' },
  { name: T.tabs.review, path: '/mypage/my-review' },
  { name: T.tabs.order, path: '/mypage/order-status' },
  { name: T.tabs.inquiry, path: '/mypage/inquiry' },
]

const ARTIST_TABS = [
  ...USER_TABS,
  { name: T.tabs.manage, path: '/mypage/manage-arts' },
  { name: T.tabs.artistReview, path: '/mypage/artist-review' },
]

export default function MyPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [editMode, setEditMode] = useState(false)
  const [bids, setBids] = useState([])
  const [bidsLoading, setBidsLoading] = useState(true)
  const [bidsError, setBidsError] = useState('')
  const [wins, setWins] = useState([])
  const [winsLoading, setWinsLoading] = useState(true)
  const [winsError, setWinsError] = useState('')
  const [myArtsCount, setMyArtsCount] = useState(0)
  const [myArtsLoading, setMyArtsLoading] = useState(true)
  const [myArtsError, setMyArtsError] = useState('')
  const [retryKey, setRetryKey] = useState(0)

  const token = localStorage.getItem('token')
  const userStatus = Number(localStorage.getItem('userStatus') ?? 0)
  const isArtist = userStatus === 1

  useEffect(() => {
    if (!token) {
      alert(T.loginRequired)
      navigate('/login', { replace: true })
      return
    }

    getUserProfile()
      .then((res) => setUser(res.data))
      .catch((err) => console.error(T.profileLoadFail, err))
      .finally(() => setLoading(false))

    getAllMyBids()
      .then((items) => {
        setBids(items)
        setBidsError('')
      })
      .catch((err) => {
        console.error('입찰 현황 조회 실패', err)
        setBidsError(err.response?.data?.message || '입찰 현황을 불러오지 못했습니다.')
      })
      .finally(() => setBidsLoading(false))

    getAllMyWins()
      .then((items) => {
        setWins(items)
        setWinsError('')
      })
      .catch((err) => {
        console.error('낙찰 작품 조회 실패', err)
        setWinsError(err.response?.data?.message || '낙찰 작품을 불러오지 못했습니다.')
      })
      .finally(() => setWinsLoading(false))

    if (isArtist) {
      getMyArts({ state: 'ALL', page: 0, size: 1 })
        .then(({ data }) => {
          setMyArtsCount(Number(data?.totalElements ?? 0))
          setMyArtsError('')
        })
        .catch((err) => {
          console.error('내 작품 수 조회 실패', err)
          setMyArtsError(err.response?.data?.message || '내 작품 수를 불러오지 못했습니다.')
        })
        .finally(() => setMyArtsLoading(false))
    }
  }, [isArtist, navigate, retryKey, token])

  if (!token) return null
  if (loading) return <div style={{ textAlign: 'center', padding: '5rem 0' }}>{T.loading}</div>

  const tabs = isArtist ? ARTIST_TABS : USER_TABS
  const retryOverview = () => {
    setBidsLoading(true)
    setBidsError('')
    setWinsLoading(true)
    setWinsError('')
    if (isArtist) {
      setMyArtsLoading(true)
      setMyArtsError('')
    }
    setRetryKey((key) => key + 1)
  }

  return (
    <div className={styles.page}>
      <div className={styles.heroBanner}>
        <div className={styles.heroBannerInner}>
          <span className={styles.heroBreadcrumb}>{T.home} · {T.mypage}</span>
          <h1 className={styles.heroTitle}>MY PAGE</h1>
          {isArtist && <span className={styles.artistBadge}>{T.artist}</span>}
        </div>
      </div>

      <div className={styles.layout}>
        <aside className={styles.sidebar}>
          {user && (
            <ProfileCard
              user={user}
              isArtist={isArtist}
              editMode={editMode}
              setEditMode={setEditMode}
              onUpdate={() => getUserProfile().then((res) => setUser(res.data))}
            />
          )}
          {user && <PointCard user={user} />}
          <QuickActions isArtist={isArtist} navigate={navigate} />
        </aside>

        <main className={styles.content}>
          <div className={styles.tabBar} role="tablist">
            {tabs.map((tab, idx) => {
              const isActive = location.pathname === tab.path
              return (
                <button
                  key={tab.path}
                  role="tab"
                  aria-selected={isActive}
                  className={`${styles.tabBtn} ${isActive ? styles.tabBtnActive : ''} ${idx >= 7 ? styles.tabBtnArtist : ''}`}
                  onClick={() => navigate(tab.path)}
                >
                  {tab.name}
                  {tab.name === T.tabs.bid && (
                    <span className={styles.tabBadge}>
                      {bids.filter((bid) => bid.auctionStatus !== 'ENDED').length}
                    </span>
                  )}
                  {tab.name === T.tabs.inquiry && (
                    <span className={styles.tabBadge}>{MOCK_INQUIRIES.filter((q) => !q.answered).length}</span>
                  )}
                </button>
              )
            })}
          </div>

          <div className={styles.tabPanel}>
            <OverviewTab
              bids={bids}
              bidsLoading={bidsLoading}
              bidsError={bidsError}
              wins={wins}
              winsLoading={winsLoading}
              winsError={winsError}
              isArtist={isArtist}
              myArtsCount={myArtsCount}
              myArtsLoading={myArtsLoading}
              myArtsError={myArtsError}
              onRetry={retryOverview}
            />
          </div>
        </main>
      </div>
    </div>
  )
}

function ProfileCard({ user, isArtist, editMode, setEditMode, onUpdate }) {
  const initial = user?.nickname?.[0] ?? '?'

  return (
    <div className={styles.profileCard}>
      <div className={styles.avatar}>
        {user?.profileImg ? (
          <img src={user.profileImg} alt="프로필" />
        ) : (
          <span className={styles.avatarInitial}>{initial}</span>
        )}
        <button className={styles.avatarEditBtn} aria-label={T.changePhoto}>+</button>
      </div>
      <p className={styles.profileNickname}>{user?.nickname || T.user}</p>
      <p className={styles.profileEmail}>{user?.email || T.emailNone}</p>
      {isArtist && <span className={styles.profileArtistTag}>{T.artist}</span>}
      <button className={styles.editProfileBtn} onClick={() => setEditMode((v) => !v)}>
        {editMode ? T.close : T.editProfile}
      </button>
      {editMode && <ProfileEditForm user={user} onClose={() => setEditMode(false)} onUpdate={onUpdate} />}
    </div>
  )
}

function ProfileEditForm({ user, onClose, onUpdate }) {
  const [form, setForm] = useState({
    nickname: user?.nickname || '',
    email: user?.email || '',
    phoneNumber: user?.phoneNumber || '',
  })
  const [saving, setSaving] = useState(false)
  const handle = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))

  const handleSave = async () => {
    if (!form.nickname.trim()) {
      alert('\uB2C9\uB124\uC784\uC744 \uC785\uB825\uD574 \uC8FC\uC138\uC694.')
      return
    }

    setSaving(true)
    try {
      await updateUserProfile({
        nickname: form.nickname,
        email: form.email,
        phoneNumber: form.phoneNumber,
        zipCode: user?.zipCode,
        userAddress1: user?.userAddress1,
        userAddress2: user?.userAddress2,
        artistIntro: user?.artistIntro,
        homepage: user?.homepage,
        artistSns: user?.artistSns,
        artistName: user?.artistName,
      })
      alert(T.profileChanged)
      await onUpdate?.()
      onClose()
    } catch (err) {
      alert(err.response?.data?.message || T.editFail)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={styles.editForm}>
      <label className={styles.editLabel}>
        닉네임
        <input className={styles.editInput} name="nickname" value={form.nickname} onChange={handle} />
      </label>
      <label className={styles.editLabel}>
        이메일
        <input className={styles.editInput} name="email" value={form.email} onChange={handle} />
      </label>
      <label className={styles.editLabel}>
        전화번호
        <input className={styles.editInput} name="phoneNumber" value={form.phoneNumber} onChange={handle} />
      </label>
      <div className={styles.editActions}>
        <button className={styles.saveBtn} onClick={handleSave} disabled={saving}>
          {saving ? T.saving : T.save}
        </button>
        <button className={styles.cancelBtn} onClick={onClose} disabled={saving}>
          취소
        </button>
      </div>
    </div>
  )
}

function PointCard({ user }) {
  return (
    <div className={styles.pointCard}>
      <div className={styles.pointRow}>
        <span className={styles.pointLabel}>{T.point}</span>
        <span className={styles.pointValue}>{fmt(user?.reserve || 0)}원</span>
      </div>
      <Link to="/charge" className={styles.chargeBtn}>
        {T.charge}
      </Link>
    </div>
  )
}

function QuickActions({ isArtist, navigate }) {
  const logout = () => {
    if (!window.confirm(T.logoutConfirm)) return
    ;['token', 'userId', 'nickname', 'userStatus'].forEach((k) => localStorage.removeItem(k))
    navigate('/login')
  }

  return (
    <div className={styles.quickActions}>
      {isArtist && (
        <Link to="/upload" className={styles.quickBtn}>{T.artUpload}</Link>
      )}
      <button className={styles.quickBtnLogout} onClick={logout}>{T.logout}</button>
    </div>
  )
}

function OverviewTab({
  bids,
  bidsLoading,
  bidsError,
  wins,
  winsLoading,
  winsError,
  isArtist,
  myArtsCount,
  myArtsLoading,
  myArtsError,
  onRetry,
}) {
  const ongoing = bids.filter((bid) => bid.auctionStatus !== 'ENDED')
  const imminent = bids.filter((bid) => bid.auctionStatus === 'IMMINENT')
  const stats = [
    { label: '\uC9C4\uD589 \uC911 \uC785\uCC30', value: ongoing.length, unit: '건', color: 'var(--color-accent)' },
    { label: '\uC885\uB8CC \uC784\uBC15', value: imminent.length, unit: '건', color: '#c0622a' },
    {
      label: T.successful,
      value: winsLoading || winsError ? '-' : wins.length,
      unit: '건',
      color: '#2a75c7',
    },
    ...(isArtist ? [{
      label: T.myArts,
      value: myArtsLoading ? '-' : myArtsError ? '-' : myArtsCount,
      unit: '건',
      color: '#7b5ea7',
    }] : []),
  ]

  return (
    <div className={styles.overviewWrap}>
      <div className={styles.statGrid}>
        {stats.map((st) => (
          <div key={st.label} className={styles.statCard} style={{ '--stat-color': st.color }}>
            <span className={styles.statValue}>{st.value}<small>{st.unit}</small></span>
            <span className={styles.statLabel}>{st.label}</span>
          </div>
        ))}
      </div>
      {isArtist && myArtsError && <EmptyState msg={myArtsError} onRetry={onRetry} />}

      <section className={styles.overviewSection}>
        <h3 className={styles.overviewSectionTitle}>{T.recentBids}</h3>
        {bidsLoading ? (
          <EmptyState msg="입찰 현황을 불러오는 중입니다." />
        ) : bidsError ? (
          <EmptyState msg={bidsError} onRetry={onRetry} />
        ) : ongoing.length === 0 ? (
          <EmptyState msg={T.noBids} />
        ) : (
          <div className={styles.miniList}>
            {ongoing.map((b) => (
              <MiniArtRow
                key={b.artId}
                img={getArtImageSrc(b.imgPath)}
                title={b.artName}
                sub={`${T.currentBid} ${formatPrice(b.myBidPrice)}원`}
                badge={(BID_STATUS_META[b.auctionStatus] ?? BID_STATUS_META.ENDED).label}
                badgeColor={(BID_STATUS_META[b.auctionStatus] ?? BID_STATUS_META.ENDED).color}
              />
            ))}
          </div>
        )}
      </section>

      <section className={styles.overviewSection}>
        <h3 className={styles.overviewSectionTitle}>{T.successful}</h3>
        {winsLoading ? (
          <EmptyState msg="낙찰 작품을 불러오는 중입니다." />
        ) : winsError ? (
          <EmptyState msg={winsError} onRetry={onRetry} />
        ) : wins.length === 0 ? (
          <EmptyState msg={T.noSuccess} />
        ) : (
          <div className={styles.miniList}>
            {wins.slice(0, 3).map((win) => (
              <MiniArtRow
                key={win.artId}
                img={getArtImageSrc(win.imgPath)}
                title={win.artName}
                sub={`${T.finalBid} ${formatPrice(win.winningPrice)}원`}
                badge="낙찰"
                badgeColor="blue"
              />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

function EmptyState({ msg, onRetry }) {
  return (
    <div className={styles.emptyState}>
      <span>{msg}</span>
      {onRetry && (
        <button type="button" className={styles.emptyRetryButton} onClick={onRetry}>
          다시 시도
        </button>
      )}
    </div>
  )
}

function MiniArtRow({ img, title, sub, badge, badgeColor }) {
  return (
    <div className={styles.miniRow}>
      <img src={img} alt={title} className={styles.miniImg} onError={applyArtImageFallback} />
      <div className={styles.miniInfo}>
        <div className={styles.miniTop}>
          <strong>{title}</strong>
          <Badge label={badge} color={badgeColor} />
        </div>
        <p className={styles.miniSub}>{sub}</p>
      </div>
    </div>
  )
}
