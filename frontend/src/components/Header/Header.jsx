import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { AUTH_STATE_CHANGED_EVENT, clearStoredAuth } from '../../utils/authStorage'
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
    label: '작가 소개',
    children: [
      { label: '작가 소개', to: '/artist-introduce' },
      { label: '개발자 소개', to: '/developer' },
    ],
  },
  {
    label: '고객센터',
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
  const headerRef = useRef(null)
  const [openIdx, setOpenIdx] = useState(null)
  const [scrolled, setScrolled] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [, setAuthVersion] = useState(0)

  const token = localStorage.getItem('token')
  const userStatus = Number(localStorage.getItem('userStatus') ?? 0)
  const nickname = localStorage.getItem('nickname')

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 10)
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => {
    const handleAuthChange = () => setAuthVersion((version) => version + 1)
    window.addEventListener(AUTH_STATE_CHANGED_EVENT, handleAuthChange)
    return () => window.removeEventListener(AUTH_STATE_CHANGED_EVENT, handleAuthChange)
  }, [])

  useEffect(() => {
    const handler = (e) => {
      if (headerRef.current && !headerRef.current.contains(e.target)) {
        setOpenIdx(null)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const logout = () => {
    if (!window.confirm('로그아웃 하시겠습니까?')) return
    clearStoredAuth()
    navigate('/login')
  }

  return (
    <header ref={headerRef} className={`${styles.header} ${scrolled ? styles.scrolled : ''}`}>
      <div className={styles.inner}>
        <Link to="/" className={styles.logo}>
          <img src="/img/Logo_2.png" alt="Daily Atelier" />
        </Link>

        <nav className={styles.gnb} aria-label="주 메뉴">
          {NAV_ITEMS.map((item, idx) => (
            <div
              key={item.label}
              className={`${styles.gnbItem} ${openIdx === idx ? styles.open : ''}`}
              onMouseEnter={() => setOpenIdx(idx)}
              onMouseLeave={() => setOpenIdx(null)}
            >
              <button
                className={styles.gnbTrigger}
                onClick={() => setOpenIdx((prev) => (prev === idx ? null : idx))}
                aria-expanded={openIdx === idx}
              >
                {item.label}
                <span className={styles.chevron} aria-hidden="true">
                  ▾
                </span>
              </button>

              <div className={styles.dropdown} aria-hidden={openIdx !== idx}>
                <ul className={styles.dropdownList}>
                  {item.children.map((child) => (
                    <li key={child.to}>
                      <Link
                        to={child.to}
                        className={`${styles.dropdownLink} ${location.pathname === child.to ? styles.active : ''}`}
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

        <div className={styles.searchWrap}>
          <input
            type="text"
            placeholder="작품 검색"
            className={styles.searchInput}
            onKeyDown={(e) => {
              if (e.key === 'Enter') navigate(`/search?q=${encodeURIComponent(e.target.value)}`)
            }}
          />
          <button className={styles.searchBtn} aria-label="검색">
            <SearchIcon />
          </button>
        </div>

        <div className={styles.userMenu}>
          {token ? (
            <>
              <span className={styles.nickname}>{nickname}</span>
              <Link to="/reliable-status" className={styles.userLink}>
                입찰 현황
              </Link>
              <Link to="/mypage" className={styles.userLink}>
                마이페이지
              </Link>
              {userStatus === 1 && (
                <Link to="/upload" className={`${styles.userLink} ${styles.artistLink}`}>
                  작품 등록
                </Link>
              )}
              <button onClick={logout} className={styles.userLink}>
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
            </>
          )}
        </div>

        <button
          className={`${styles.hamburger} ${mobileOpen ? styles.hamburgerOpen : ''}`}
          onClick={() => setMobileOpen((v) => !v)}
          aria-label="모바일 메뉴"
        >
          <span />
          <span />
          <span />
        </button>
      </div>

      <div className={`${styles.hdBg} ${openIdx !== null ? styles.hdBgVisible : ''}`} aria-hidden="true" />

      {mobileOpen && (
        <div className={styles.mobileMenu}>
          {NAV_ITEMS.map((item) => (
            <div key={item.label} className={styles.mobileSection}>
              <p className={styles.mobileSectionLabel}>{item.label}</p>
              {item.children.map((child) => (
                <Link key={child.to} to={child.to} className={styles.mobileLink} onClick={() => setMobileOpen(false)}>
                  {child.label}
                </Link>
              ))}
            </div>
          ))}
          <div className={styles.mobileDivider} />
          {token ? (
            <>
              <Link to="/reliable-status" className={styles.mobileLink} onClick={() => setMobileOpen(false)}>
                입찰 현황
              </Link>
              <Link to="/mypage" className={styles.mobileLink} onClick={() => setMobileOpen(false)}>
                마이페이지
              </Link>
              {userStatus === 1 && (
                <Link to="/upload" className={styles.mobileLink} onClick={() => setMobileOpen(false)}>
                  작품 등록
                </Link>
              )}
              <button onClick={logout} className={styles.mobileLink}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className={styles.mobileLink} onClick={() => setMobileOpen(false)}>
                로그인
              </Link>
              <Link to="/register" className={styles.mobileLink} onClick={() => setMobileOpen(false)}>
                회원가입
              </Link>
            </>
          )}
        </div>
      )}
    </header>
  )
}

function SearchIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="11" cy="11" r="8" />
      <line x1="21" y1="21" x2="16.65" y2="16.65" />
    </svg>
  )
}
