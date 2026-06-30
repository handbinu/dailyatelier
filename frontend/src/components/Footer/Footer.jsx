import { Link } from 'react-router-dom'
import styles from './Footer.module.css'

export default function Footer() {
  return (
    <footer className={styles.footer}>
      <div className={styles.inner}>
        {/* 상단: 링크 + SNS */}
        <div className={styles.top}>
          <nav className={styles.links} aria-label="푸터 메뉴">
            <span className={styles.linkItem}>개인정보처리방침</span>
            <span className={styles.divider} aria-hidden="true">|</span>
            <span className={styles.linkItem}>이용약관</span>
            <span className={styles.divider} aria-hidden="true">|</span>
            <Link to="/info" className={styles.linkItem}>서비스</Link>
            <span className={styles.divider} aria-hidden="true">|</span>
            <Link to="/qna" className={styles.linkItem}>고객센터</Link>
          </nav>

          <div className={styles.social} aria-label="SNS 링크">
            <SocialIcon label="Instagram">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <rect x="2" y="2" width="20" height="20" rx="5" ry="5"/>
                <circle cx="12" cy="12" r="4"/>
                <circle cx="17.5" cy="6.5" r="0.5" fill="currentColor" stroke="none"/>
              </svg>
            </SocialIcon>
            <SocialIcon label="Blog">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 2L2 7l10 5 10-5-10-5z"/>
                <path d="M2 17l10 5 10-5"/>
                <path d="M2 12l10 5 10-5"/>
              </svg>
            </SocialIcon>
            <SocialIcon label="Facebook">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M18 2h-3a5 5 0 00-5 5v3H7v4h3v8h4v-8h3l1-4h-4V7a1 1 0 011-1h3z"/>
              </svg>
            </SocialIcon>
          </div>
        </div>

        {/* 하단: 회사 정보 */}
        <div className={styles.bottom}>
          <p className={styles.info}>
            (주)대단학과 &nbsp;&nbsp;|&nbsp;&nbsp;
            사무실: &nbsp;&nbsp;|&nbsp;&nbsp;
            주소: 
          </p>
          <p className={styles.info}>
            이메일: &nbsp;&nbsp;|&nbsp;&nbsp;
            디자인:
          </p>
          <p className={styles.info}>
            개인정보보호책임자 : hand binu
          </p>
          <p className={styles.copy}>© 2026 데일리 아틀리에. All rights reserved.</p>
        </div>
      </div>
    </footer>
  )
}

function SocialIcon({ label, children }) {
  return (
    <button className={styles.socialBtn} aria-label={label}>
      {children}
    </button>
  )
}