import { useState, useEffect, useRef } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import styles from './Header.module.css'

const NAV_ITEMS = [
  {
    label: '공지사항',
    children: [
      { label: '공지 사항', to: '/notice' },
      { label: '이벤트 안내', to: '/event' },
    ],
  },
  {
    label: '경매',
    children: [
      { label: '전체', to: '/auction/total' },
      { label: '디지털', to: '/auction/digital' },
      { label: '실물', to: '/auction/analog' },
      { label: '작가별 작품', to: '/auction/artist' },
    ],
  },
  {
    label: '소개글',
    children: [
      { label: '작가소개', to: '/artist-introduce' },
      { label: '개발자 소개', to: '/developer' },
    ],
  },
  {
    label: '서비스',
    children: [
      { label: '경매 진행방법', to: '/info' },
      { label: '고객센터', to: '/qna' },
      { label: 'Q&A', to: '/q-list' },
    ],
  },
]

export default function Header() {
  const navigate = useNavigate()
  const location = useLocation()
  const [openIdx, setOpenIdx] = useState(null)
  const [scrolled, setScrolled] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const headerRef = useRef(null)

  const token = localStorage.getItem('token')
  const nickname = localStorage.getItem('nickname')

  /* 스크롤 감지 */
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 10)
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  /* 라우트 변경 시 메뉴 닫기 */
  useEffect(() => {
    setOpenIdx(null)
    setMobileOpen(false)
  }, [location.pathname])

  /* 외부 클릭 시 닫기 */
  useEffect(() => {
    const handler = (e) => {
      if (headerRef.current && !headerRef.current.contains(e.target)) {
        setOpenIdx(null)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const handleLogout = () => {
    if (!confirm('로그아웃 하시겠습니까?')) return
    ;['token', 'userId', 'nickname', 'userStatus'].forEach((k) =>
      localStorage.removeItem(k)
    )
    navigate('/login')
  }

  const toggleMenu = (idx) =>
    setOpenIdx((prev) => (prev === idx ? null : idx))
  const isDesktop = () => window.matchMedia('(min-width: 769px)').matches

  return (
    <header
      ref={headerRef}
      className={`${styles.header} ${scrolled ? styles.scrolled : ''}`}
    >
      <div className={styles.inner}>
        {/* 로고 */}
        <Link to="/" className={styles.logo}>
          <img src="/img/Logo_2.png" alt="데일리 아틀리에" />
        </Link>

        {/* 데스크톱 GNB */}
        <nav className={styles.gnb} aria-label="주 메뉴">
          {NAV_ITEMS.map((item, idx) => (
            <div
              key={item.label}
              className={`${styles.gnbItem} ${openIdx === idx ? styles.open : ''}`}
              onMouseEnter={() => {
                if (isDesktop()) setOpenIdx(idx)
              }}
              onMouseLeave={() => {
                if (isDesktop()) setOpenIdx(null)
              }}
            >
              <button
                className={styles.gnbTrigger}
                onClick={() => toggleMenu(idx)}
                aria-expanded={openIdx === idx}
                onFocus={() => {
                  if (isDesktop()) setOpenIdx(idx)
                }}
                onBlur={(e) => {
                  if (!e.currentTarget.parentElement?.contains(e.relatedTarget)) {
                    setOpenIdx(null)
                  }
                }}
              >
                {item.label}
                <span className={styles.chevron} aria-hidden="true">
                  ›
                </span>
              </button>

              {/* 드롭다운 */}
              <div
                className={styles.dropdown}
                aria-hidden={openIdx !== idx}
              >
                <ul className={styles.dropdownList}>
                  {item.children.map((child) => (
                    <li key={child.to}>
                      <Link
                        to={child.to}
                        className={`${styles.dropdownLink} ${
                          location.pathname === child.to ? styles.active : ''
                        }`}
                        onClick={() => setOpenIdx(null)}
                      >
                        {child.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          ))}
        </nav>

        {/* 검색 */}
        <div className={styles.searchWrap}>
          <input
            type="text"
            placeholder="작품 검색"
            className={styles.searchInput}
            onKeyDown={(e) => {
              if (e.key === 'Enter') navigate(`/search?q=${e.target.value}`)
            }}
          />
          <button className={styles.searchBtn} aria-label="검색">
            <SearchIcon />
          </button>
        </div>

        {/* 사용자 메뉴 */}
        <div className={styles.userMenu}>
          {token ? (
            <>
              <span className={styles.nickname}>{nickname}</span>
              <Link to="/reliable-status" className={styles.userLink}>
                입찰현황
              </Link>
              <Link to="/mypage" className={styles.userLink}>
                마이페이지
              </Link>
              <button onClick={handleLogout} className={styles.userLink}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className={styles.userLink}>
                로그인
              </Link>
              <Link to="/register" className={`${styles.userLink} ${styles.registerBtn}`}>
                회원가입
              </Link>
              <Link to="/qna" className={styles.userLink}>
                고객센터
              </Link>
            </>
          )}
        </div>

        {/* 모바일 햄버거 */}
        <button
          className={`${styles.hamburger} ${mobileOpen ? styles.hamburgerOpen : ''}`}
          onClick={() => setMobileOpen((v) => !v)}
          aria-label="모바일 메뉴"
        >
          <span /><span /><span />
        </button>
      </div>

      {/* 드롭다운 배경 (hover 영역 유지) */}
      <div
        className={`${styles.hdBg} ${openIdx !== null ? styles.hdBgVisible : ''}`}
        aria-hidden="true"
      />

      {/* 모바일 메뉴 */}
      {mobileOpen && (
        <div className={styles.mobileMenu}>
          {NAV_ITEMS.map((item) => (
            <div key={item.label} className={styles.mobileSection}>
              <p className={styles.mobileSectionLabel}>{item.label}</p>
              {item.children.map((child) => (
                <Link key={child.to} to={child.to} className={styles.mobileLink}>
                  {child.label}
                </Link>
              ))}
            </div>
          ))}
          <div className={styles.mobileDivider} />
          {token ? (
            <>
              <Link to="/reliable-status" className={styles.mobileLink}>
                입찰현황
              </Link>
              <Link to="/mypage" className={styles.mobileLink}>
                마이페이지
              </Link>
              <button onClick={handleLogout} className={styles.mobileLink}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className={styles.mobileLink}>로그인</Link>
              <Link to="/register" className={styles.mobileLink}>회원가입</Link>
            </>
          )}
        </div>
      )}
    </header>
  )
}

function SearchIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="11" cy="11" r="8" />
      <line x1="21" y1="21" x2="16.65" y2="16.65" />
    </svg>
  )
}