import { useSyncExternalStore } from 'react'
import { Link, Outlet } from 'react-router-dom'
import { getStoredUserStatus, subscribeToAuthChanges } from '../../utils/authStorage'

function AdminRoute() {
  const userStatus = useSyncExternalStore(subscribeToAuthChanges, getStoredUserStatus)

  if (Number(userStatus) !== 2) {
    return (
      <main style={{ padding: '120px 20px', textAlign: 'center', minHeight: '60vh' }} aria-labelledby="admin-access-title">
        <h1 id="admin-access-title" style={{ fontSize: '24px', fontWeight: '600', marginBottom: '16px', color: '#111' }}>관리자 전용 페이지입니다</h1>
        <p style={{ color: '#666', marginBottom: '32px', fontSize: '15px' }}>해당 페이지는 관리자 권한이 있는 계정만 접근할 수 있습니다.</p>
        <Link to="/mypage" style={{ display: 'inline-block', padding: '12px 24px', backgroundColor: '#111', color: '#fff', borderRadius: '6px', textDecoration: 'none', fontWeight: '500' }}>
          마이페이지로 돌아가기
        </Link>
      </main>
    )
  }

  return <Outlet />
}

export default AdminRoute
