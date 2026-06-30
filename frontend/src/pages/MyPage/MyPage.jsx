// src/pages/MyPage/MyPage.jsx  —  마이페이지 허브
import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { getUserProfile, updateUserProfile } from '../../api/userApi'
// ── mockData: 인라인 중복 선언 제거, 여기서만 import ──────────────
import {
  MOCK_USER, MOCK_BIDS, MOCK_LIKES, MOCK_SUCCESSFUL,
  MOCK_REVIEWS, MOCK_ORDERS, MOCK_INQUIRIES,
  MOCK_MY_ARTS, MOCK_ARTIST_REVIEWS,
  fmt, STATUS_META, ORDER_STATUS_COLOR,
  ART_STATUS_LABEL, ART_STATUS_COLOR,
} from './mockData'
// ── atoms: 중복 컴포넌트 제거, 공유 원자만 사용 ───────────────────
import { Badge, StarDisplay, FilterBar } from './components/atoms'
import styles from './MyPage.module.css'

// ── 탭 정의 ──────────────────────────────────────────────────────
const USER_TABS   = ['활동 개요', '입찰 현황', '찜한 작품', '낙찰 작품', '나의 리뷰', '주문 조회', '문의 현황']
const ARTIST_TABS = [...USER_TABS, '내 작품 관리', '작품 리뷰']

// ── 메인 컴포넌트 ─────────────────────────────────────────────────
export default function MyPage() {
  const navigate    = useNavigate()
  const [activeTab, setActiveTab] = useState(0)
  const [editMode,  setEditMode]  = useState(false)
  const [user,      setUser]      = useState(null)
  const [loading,   setLoading]   = useState(true)

  const token      = localStorage.getItem('token')
  const userStatus = Number(localStorage.getItem('userStatus') ?? 0)
  const isArtist   = userStatus === 1

  const fetchProfile = () => {
    getUserProfile()
      .then(res => {
        setUser(res.data)
        setLoading(false)
      })
      .catch(err => {
        console.error('프로필 로딩 실패:', err)
        setLoading(false)
      })
  }

  useEffect(() => {
    if (!token) {
      alert('로그인이 필요한 페이지입니다.')
      navigate('/login', { replace: true })
      return
    }
    fetchProfile()
  }, [token, navigate])

  if (!token) return null
  if (loading) return <div style={{ textAlign: 'center', padding: '5rem 0' }}>로딩 중...</div>

  const tabs = isArtist ? ARTIST_TABS : USER_TABS
  const profileUser = user || MOCK_USER

  return (
    <div className={styles.page}>
      {/* 헤더 배너 */}
      <div className={styles.heroBanner}>
        <div className={styles.heroBannerInner}>
          <span className={styles.heroBreadcrumb}>홈 · 마이페이지</span>
          <h1 className={styles.heroTitle}>MY PAGE</h1>
          {isArtist && <span className={styles.artistBadge}>작가 회원</span>}
        </div>
      </div>

      <div className={styles.layout}>
        {/* 사이드바 */}
        <aside className={styles.sidebar}>
          <ProfileCard
            user={profileUser}
            isArtist={isArtist}
            editMode={editMode}
            setEditMode={setEditMode}
            onUpdate={fetchProfile}
          />
          <PointCard user={profileUser} />
          <QuickActions isArtist={isArtist} navigate={navigate} />
        </aside>

        {/* 메인 콘텐츠 */}
        <main className={styles.content}>
          {/* 탭 바 */}
          <div className={styles.tabBar} role="tablist">
            {tabs.map((tab, idx) => (
              <button
                key={tab}
                role="tab"
                aria-selected={activeTab === idx}
                className={`${styles.tabBtn} ${activeTab === idx ? styles.tabBtnActive : ''} ${idx >= 7 ? styles.tabBtnArtist : ''}`}
                onClick={() => setActiveTab(idx)}
              >
                {tab}
                {tab === '입찰 현황' && (
                  <span className={styles.tabBadge}>
                    {MOCK_BIDS.filter(b => b.status !== 'ended').length}
                  </span>
                )}
                {tab === '문의 현황' && (
                  <span className={styles.tabBadge}>
                    {MOCK_INQUIRIES.filter(q => !q.answered).length}
                  </span>
                )}
              </button>
            ))}
          </div>

          {/* 탭 패널 */}
          <div className={styles.tabPanel}>
            {activeTab === 0 && <OverviewTab  bids={MOCK_BIDS} successful={MOCK_SUCCESSFUL} isArtist={isArtist} myArts={MOCK_MY_ARTS} />}
            {activeTab === 1 && <BidTab       bids={MOCK_BIDS} />}
            {activeTab === 2 && <LikesTab     likes={MOCK_LIKES} />}
            {activeTab === 3 && <SuccessfulTab items={MOCK_SUCCESSFUL} />}
            {activeTab === 4 && <ReviewTab    reviews={MOCK_REVIEWS} />}
            {activeTab === 5 && <OrderTab     orders={MOCK_ORDERS} />}
            {activeTab === 6 && <InquiryTab   inquiries={MOCK_INQUIRIES} />}
            {isArtist && activeTab === 7 && <ArtManageTab arts={MOCK_MY_ARTS} navigate={navigate} />}
            {isArtist && activeTab === 8 && <ArtistReviewTab reviews={MOCK_ARTIST_REVIEWS} />}
          </div>
        </main>
      </div>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════
//  사이드바 컴포넌트
// ══════════════════════════════════════════════════════════════════
function ProfileCard({ user, isArtist, editMode, setEditMode, onUpdate }) {
  const initial = user.nickname?.[0] ?? '?'
  return (
    <div className={styles.profileCard}>
      <div className={styles.avatar}>
        {user.profileImg
          ? <img src={user.profileImg} alt="프로필" />
          : <span className={styles.avatarInitial}>{initial}</span>
        }
        <button className={styles.avatarEditBtn} aria-label="프로필 사진 변경">+</button>
      </div>
      <p className={styles.profileNickname}>{user.nickname} 님</p>
      <p className={styles.profileEmail}>{user.email}</p>
      {isArtist && <span className={styles.profileArtistTag}>작가</span>}
      <button className={styles.editProfileBtn} onClick={() => setEditMode(v => !v)}>
        {editMode ? '닫기' : '프로필 수정'}
      </button>
      {editMode && <ProfileEditForm user={user} onClose={() => setEditMode(false)} onUpdate={onUpdate} />}
    </div>
  )
}

function ProfileEditForm({ user, onClose, onUpdate }) {
  const [form, setForm] = useState({
    nickname: user.nickname || '',
    email: user.email || '',
    phoneNumber: user.phoneNumber || '',
  })
  const [saving, setSaving] = useState(false)
  const handle = (e) => setForm(f => ({ ...f, [e.target.name]: e.target.value }))

  const handleSave = async () => {
    if (!form.nickname.trim()) { alert('닉네임을 입력해 주세요.'); return }
    setSaving(true)
    try {
      await updateUserProfile({
        nickname: form.nickname,
        email: form.email,
        phoneNumber: form.phoneNumber,
        zipCode: user.zipCode,
        userAddress1: user.userAddress1,
        userAddress2: user.userAddress2,
        artistIntro: user.artistIntro,
        homepage: user.homepage,
        artistSns: user.artistSns,
        artistName: user.artistName
      })
      alert('회원 정보가 수정되었습니다.')
      if (onUpdate) onUpdate()
      onClose()
    } catch (err) {
      alert(err.response?.data?.message || '정보 수정에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className={styles.editForm}>
      <label className={styles.editLabel}>닉네임
        <input className={styles.editInput} name="nickname" value={form.nickname} onChange={handle} />
      </label>
      <label className={styles.editLabel}>이메일
        <input className={styles.editInput} name="email" value={form.email} onChange={handle} />
      </label>
      <label className={styles.editLabel}>전화번호
        <input className={styles.editInput} name="phoneNumber" value={form.phoneNumber} onChange={handle} />
      </label>
      <div className={styles.editActions}>
        <button className={styles.saveBtn} onClick={handleSave} disabled={saving}>
          {saving ? '저장 중...' : '저장'}
        </button>
        <button className={styles.cancelBtn} onClick={onClose} disabled={saving}>취소</button>
      </div>
    </div>
  )
}

function PointCard({ user }) {
  const total = user.reserve + user.usedReserve
  const usedPct = total > 0 ? Math.min((user.usedReserve / total) * 100, 100) : 0
  return (
    <div className={styles.pointCard}>
      <div className={styles.pointRow}>
        <span className={styles.pointLabel}>보유 적립금</span>
        <span className={styles.pointValue}>{fmt(user.reserve)}원</span>
      </div>
      <div className={styles.pointBar}>
        <div className={styles.pointBarFill} style={{ width: `${usedPct}%` }} />
      </div>
      <div className={styles.pointSubRow}>
        <span className={styles.pointSub}>사용 {fmt(user.usedReserve)}원</span>
        <span className={styles.pointSub}>총 {fmt(total)}원</span>
      </div>
      <Link to="/charge" className={styles.chargeBtn}>충전하기</Link>
    </div>
  )
}

function QuickActions({ isArtist, navigate }) {
  const logout = () => {
    if (window.confirm('로그아웃 하시겠습니까?')) {
      ;['token', 'userId', 'nickname', 'userStatus'].forEach(k => localStorage.removeItem(k))
      navigate('/login')
    }
  }
  return (
    <div className={styles.quickActions}>
      <Link to="/mypage/order-status" className={styles.quickBtn}>주문 조회</Link>
      <Link to="/auction/total"       className={styles.quickBtn}>경매 참여</Link>
      {isArtist && <Link to="/upload" className={styles.quickBtn}>작품 등록</Link>}
      <button className={styles.quickBtnLogout} onClick={logout}>로그아웃</button>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════
//  탭 패널 컴포넌트
// ══════════════════════════════════════════════════════════════════

// ── 탭 0: 활동 개요 ───────────────────────────────────────────────
function OverviewTab({ bids, successful, isArtist, myArts }) {
  const ongoing  = bids.filter(b => b.status !== 'ended')
  const imminent = bids.filter(b => b.status === 'imminent')
  const stats = [
    { label: '진행 중 입찰', value: ongoing.length,    unit: '건', color: 'var(--color-accent)'  },
    { label: '종료 임박',    value: imminent.length,   unit: '건', color: '#c0622a'               },
    { label: '낙찰 작품',    value: successful.length, unit: '점', color: '#2a75c7'               },
    ...(isArtist ? [{ label: '내 등록 작품', value: myArts.length, unit: '점', color: '#7b5ea7' }] : []),
  ]
  return (
    <div className={styles.overviewWrap}>
      <div className={styles.statGrid}>
        {stats.map(st => (
          <div key={st.label} className={styles.statCard} style={{ '--stat-color': st.color }}>
            <span className={styles.statValue}>{st.value}<small>{st.unit}</small></span>
            <span className={styles.statLabel}>{st.label}</span>
          </div>
        ))}
      </div>

      <section className={styles.overviewSection}>
        <h3 className={styles.overviewSectionTitle}>최근 입찰 현황</h3>
        {ongoing.length === 0
          ? <EmptyState msg="진행 중인 입찰이 없습니다." />
          : <div className={styles.miniList}>
              {ongoing.map(b => (
                <MiniArtRow
                  key={b.id}
                  img={b.artImg}
                  title={b.artName}
                  sub={`내 입찰가 ${fmt(b.myPrice)}원`}
                  badge={STATUS_META[b.status].label}
                  badgeColor={STATUS_META[b.status].color}
                />
              ))}
            </div>
        }
      </section>

      <section className={styles.overviewSection}>
        <h3 className={styles.overviewSectionTitle}>낙찰 작품</h3>
        {successful.length === 0
          ? <EmptyState msg="낙찰 작품이 없습니다." />
          : <div className={styles.miniList}>
              {successful.map(s => (
                <MiniArtRow
                  key={s.id}
                  img={s.artImg}
                  title={s.artName}
                  sub={`낙찰가 ${fmt(s.finalPrice)}원`}
                  badge={s.reviewWritten ? '리뷰 완료' : '리뷰 미작성'}
                  badgeColor={s.reviewWritten ? 'green' : 'gray'}
                />
              ))}
            </div>
        }
      </section>
    </div>
  )
}

// ── 탭 1: 입찰 현황 ───────────────────────────────────────────────
const BID_FILTERS = ['전체', '진행 중', '종료 임박', '종료']
const BID_STATUS_KEY = { '진행 중': 'ongoing', '종료 임박': 'imminent', '종료': 'ended' }

function BidTab({ bids }) {
  const [filter, setFilter] = useState('전체')
  const filtered = filter === '전체' ? bids : bids.filter(b => b.status === BID_STATUS_KEY[filter])
  return (
    <div>
      <FilterBar options={BID_FILTERS} value={filter} onChange={setFilter} />
      {filtered.length === 0
        ? <EmptyState msg="해당 항목이 없습니다." />
        : <div className={styles.artCardGrid}>
            {filtered.map(b => <BidCard key={b.id} bid={b} />)}
          </div>
      }
    </div>
  )
}

function BidCard({ bid }) {
  const meta      = STATUS_META[bid.status]
  const isLeading = bid.myPrice >= bid.currentPrice
  return (
    <div className={styles.bidCard}>
      <div className={styles.bidImgWrap}>
        <img src={bid.artImg} alt={bid.artName} />
        <Badge label={meta.label} color={meta.color} />
      </div>
      <div className={styles.bidCardBody}>
        <p className={styles.bidCardTitle}>{bid.artName}</p>
        <p className={styles.bidCardArtist}>by {bid.artist}</p>
        <div className={styles.bidPriceRow}>
          <span className={styles.bidMyPrice}>내 입찰가 <strong>{fmt(bid.myPrice)}원</strong></span>
          <span className={`${styles.bidLeading} ${isLeading ? styles.bidLeadingYes : styles.bidLeadingNo}`}>
            {isLeading ? '최고가' : '경쟁 중'}
          </span>
        </div>
        <p className={styles.bidCurrentPrice}>현재 최고가 <strong>{fmt(bid.currentPrice)}원</strong></p>
        <p className={styles.bidTime}>{bid.closingTime} 마감</p>
        {bid.status !== 'ended' && (
          <Link to={`/auction/${bid.id}`} className={styles.bidRaiseBtn}>가격 올리기</Link>
        )}
      </div>
    </div>
  )
}

// ── 탭 2: 찜한 작품 ───────────────────────────────────────────────
function LikesTab({ likes }) {
  return likes.length === 0
    ? <EmptyState msg="찜한 작품이 없습니다." />
    : <div className={styles.artCardGrid}>
        {likes.map(l => (
          <div key={l.id} className={styles.likeCard}>
            <div className={styles.likeImgWrap}>
              <img src={l.artImg} alt={l.artName} />
              <button className={styles.likeHeartBtn} aria-label="찜 해제">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                  <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09A6.014 6.014 0 0116.5 3C19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
                </svg>
              </button>
              {l.status === 'ended' && <div className={styles.likeEndedOverlay}>경매 종료</div>}
            </div>
            <div className={styles.likeInfo}>
              <p className={styles.likeTitle}>{l.artName}</p>
              <p className={styles.likeArtist}>by {l.artist}</p>
              <p className={styles.likePrice}>현재가 {fmt(l.currentPrice)}원</p>
              {l.status === 'ongoing' && (
                <Link to={`/auction/${l.id}`} className={styles.likeBidBtn}>입찰하기</Link>
              )}
            </div>
          </div>
        ))}
      </div>
}

// ── 탭 3: 낙찰 작품 ───────────────────────────────────────────────
function SuccessfulTab({ items }) {
  return items.length === 0
    ? <EmptyState msg="낙찰된 작품이 없습니다." />
    : <div className={styles.artCardGrid}>
        {items.map(s => (
          <div key={s.id} className={styles.successCard}>
            <div className={styles.successImgWrap}>
              <img src={s.artImg} alt={s.artName} />
              <div className={styles.successHoverLayer}>
                <Link to={`/auction/${s.id}`}        className={styles.successHoverBtn}>자세히 보기</Link>
                <Link to={`/write-review/${s.id}`}   className={styles.successHoverBtn}>
                  {s.reviewWritten ? '리뷰 수정' : '리뷰 쓰기'}
                </Link>
              </div>
            </div>
            <div className={styles.successInfo}>
              <p className={styles.successTitle}>{s.artName}</p>
              <p className={styles.successArtist}>by {s.artist}</p>
              <p className={styles.successPrice}>낙찰가 {fmt(s.finalPrice)}원</p>
              <p className={styles.successDate}>{s.orderedAt} 낙찰</p>
              <span className={`${styles.successReviewTag} ${s.reviewWritten ? styles.reviewDone : styles.reviewPending}`}>
                {s.reviewWritten ? '리뷰 완료' : '리뷰 미작성'}
              </span>
            </div>
          </div>
        ))}
      </div>
}

// ── 탭 4: 나의 리뷰 ───────────────────────────────────────────────
function ReviewTab({ reviews }) {
  return reviews.length === 0
    ? <EmptyState msg="작성한 리뷰가 없습니다." />
    : <div className={styles.reviewList}>
        {reviews.map(r => (
          <div key={r.id} className={styles.reviewItem}>
            <img src={r.artImg} alt={r.artName} className={styles.reviewItemImg} />
            <div className={styles.reviewItemBody}>
              <div className={styles.reviewItemTop}>
                <span className={styles.reviewItemTitle}>{r.artName}</span>
                <StarDisplay star={r.star} />
              </div>
              <p className={styles.reviewItemContent}>{r.content}</p>
              <span className={styles.reviewItemDate}>{r.createdAt}</span>
            </div>
            <div className={styles.reviewItemActions}>
              <Link to={`/write-review/${r.artId}`} className={styles.reviewEditBtn}>수정</Link>
            </div>
          </div>
        ))}
      </div>
}

// ── 탭 5: 주문 조회 ───────────────────────────────────────────────
function OrderTab({ orders }) {
  return orders.length === 0
    ? <EmptyState msg="주문 내역이 없습니다." />
    : <div className={styles.orderList}>
        <div className={styles.orderHeader}>
          <span>주문 정보</span>
          <span>금액</span>
          <span>상태</span>
          <span>관리</span>
        </div>
        {orders.map(o => (
          <div key={o.id} className={styles.orderRow}>
            <div className={styles.orderInfo}>
              <img src={o.artImg} alt={o.artName} className={styles.orderImg} />
              <div>
                <p className={styles.orderArtName}>{o.artName}</p>
                <p className={styles.orderArtist}>by {o.artist}</p>
                <p className={styles.orderNo}>{o.orderNo}</p>
                <p className={styles.orderDate}>{o.orderedAt}</p>
              </div>
            </div>
            <span className={styles.orderPrice}>{fmt(o.price)}원</span>
            <Badge label={o.status} color={ORDER_STATUS_COLOR[o.status] ?? 'gray'} />
            {o.status !== '취소' && o.status !== '배송완료' && (
              <button className={styles.orderCancelBtn}>취소 신청</button>
            )}
          </div>
        ))}
      </div>
}

// ── 탭 6: 문의 현황 ───────────────────────────────────────────────
function InquiryTab({ inquiries }) {
  const [open, setOpen] = useState(null)
  return (
    <div>
      <div className={styles.inquiryHead}>
        <Link to="/mypage/inquiry/write" className={styles.inquiryNewBtn}>+ 새 문의</Link>
      </div>
      <div className={styles.inquiryList}>
        {inquiries.length === 0
          ? <EmptyState msg="문의 내역이 없습니다." />
          : inquiries.map(q => (
              <div key={q.id} className={styles.inquiryItem}>
                <button
                  className={styles.inquiryTrigger}
                  onClick={() => setOpen(open === q.id ? null : q.id)}
                  aria-expanded={open === q.id}
                >
                  <span className={styles.inquiryType}>[{q.type}]</span>
                  <span className={styles.inquiryTitle}>{q.title}</span>
                  <Badge
                    label={q.answered ? '답변 완료' : '대기 중'}
                    color={q.answered ? 'green' : 'orange'}
                  />
                  <span className={styles.inquiryDate}>{q.createdAt}</span>
                  <span className={styles.inquiryChevron} aria-hidden="true">
                    {open === q.id ? '▲' : '▼'}
                  </span>
                </button>
                {open === q.id && (
                  <div className={styles.inquiryBody}>
                    {q.answer
                      ? <p className={styles.inquiryAnswer}><strong>A.</strong> {q.answer}</p>
                      : <p className={styles.inquiryNoAnswer}>아직 답변이 등록되지 않았습니다.</p>
                    }
                  </div>
                )}
              </div>
            ))
        }
      </div>
    </div>
  )
}

// ── 탭 7: 내 작품 관리 (작가 전용) ───────────────────────────────
function ArtManageTab({ arts, navigate }) {
  return (
    <div>
      <div className={styles.artManageHead}>
        <Link to="/upload" className={styles.artUploadBtn}>+ 작품 등록</Link>
      </div>
      <div className={styles.artManageGrid}>
        {arts.map(a => (
          <div key={a.id} className={styles.manageCard}>
            <div className={styles.manageImgWrap}>
              <img src={a.img} alt={a.name} />
              <Badge label={ART_STATUS_LABEL[a.status]} color={ART_STATUS_COLOR[a.status]} />
            </div>
            <div className={styles.manageCardBody}>
              <p className={styles.manageCardTitle}>{a.name}</p>
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
    </div>
  )
}

// ── 탭 8: 작품 리뷰 (작가 전용) ──────────────────────────────────
function ArtistReviewTab({ reviews }) {
  return reviews.length === 0
    ? <EmptyState msg="작품에 등록된 리뷰가 없습니다." />
    : <div className={styles.reviewList}>
        {reviews.map(r => (
          <div key={r.id} className={styles.reviewItem}>
            <img src={r.artImg} alt={r.artName} className={styles.reviewItemImg} />
            <div className={styles.reviewItemBody}>
              <div className={styles.reviewItemTop}>
                <span className={styles.reviewItemTitle}>{r.artName}</span>
                <StarDisplay star={r.star} />
              </div>
              <p className={styles.reviewItemBuyer}>구매자: {r.buyer}</p>
              <p className={styles.reviewItemContent}>{r.content}</p>
              <span className={styles.reviewItemDate}>{r.createdAt}</span>
            </div>
          </div>
        ))}
      </div>
}

// ── 공통 서브 컴포넌트 ────────────────────────────────────────────
function MiniArtRow({ img, title, sub, badge, badgeColor }) {
  return (
    <div className={styles.miniRow}>
      <img src={img} alt={title} className={styles.miniRowImg} />
      <div className={styles.miniRowInfo}>
        <p className={styles.miniRowTitle}>{title}</p>
        <p className={styles.miniRowSub}>{sub}</p>
      </div>
      <Badge label={badge} color={badgeColor} />
    </div>
  )
}

function EmptyState({ msg }) {
  return (
    <div className={styles.empty}>
      <svg className={styles.emptyIcon} viewBox="0 0 48 48" fill="none" aria-hidden="true">
        <rect x="8" y="14" width="32" height="26" rx="3" stroke="currentColor" strokeWidth="1.5"/>
        <path d="M16 14V10a8 8 0 0116 0v4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
        <circle cx="24" cy="27" r="3" stroke="currentColor" strokeWidth="1.5"/>
      </svg>
      <p>{msg}</p>
    </div>
  )
}