import { Outlet, useLocation } from 'react-router-dom'
import Header from '../components/Header/Header'
import Footer from '../components/Footer/Footer'
import styles from './Layout.module.css'
 
/* 헤더·푸터가 필요 없는 라우트 */
const BARE_ROUTES = ['/login', '/register', '/register/user', '/register/artist']
 
export default function Layout() {
  const { pathname } = useLocation()
  const isBare = BARE_ROUTES.some((r) => pathname.startsWith(r))
 
  if (isBare) {
    return (
      <div className={styles.bare}>
        <Outlet />
      </div>
    )
  }
 
  return (
    <div className={styles.root}>
      <Header />
      <main className={styles.main}>
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}
 