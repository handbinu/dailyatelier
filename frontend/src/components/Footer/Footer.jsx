import { Link } from 'react-router-dom'
import styles from './Footer.module.css'

export default function Footer() {
  return (
    <footer className={styles.footer}>
      <div className={styles.inner}>
        {/* 상단: 주요 링크 */}
        <div className={styles.top}>
          <nav className={styles.links} aria-label="푸터 메뉴">
            <Link to="/auction/total" className={styles.linkItem}>전체 작품</Link>
            <span className={styles.divider} aria-hidden="true">|</span>
            <Link to="/artists" className={styles.linkItem}>작가 목록</Link>
            <span className={styles.divider} aria-hidden="true">|</span>
            <Link to="/qna" className={styles.linkItem}>고객센터</Link>
            <span className={styles.divider} aria-hidden="true">|</span>
            <Link to="/info" className={styles.linkItem}>경매 이용 안내</Link>
          </nav>
        </div>

        {/* 하단: 회사 정보 */}
        <div className={styles.bottom}>
          <p className={styles.info}>신인 작가와 컬렉터를 잇는 온라인 미술 경매</p>
          <p className={styles.copy}>© 2026 데일리 아틀리에. All rights reserved.</p>
        </div>
      </div>
    </footer>
  )
}
