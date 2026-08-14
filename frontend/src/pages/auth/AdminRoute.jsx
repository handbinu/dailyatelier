import { useSyncExternalStore } from 'react'
import { Link, Outlet } from 'react-router-dom'
import { getStoredUserStatus, subscribeToAuthChanges } from '../../utils/authStorage'

function AdminRoute() {
  const userStatus = useSyncExternalStore(subscribeToAuthChanges, getStoredUserStatus)

  if (Number(userStatus) !== 2) {
    return (
      <main className="route-access-denied" aria-labelledby="admin-access-title">
        <h1 id="admin-access-title">관리자 전용 페이지입니다</h1>
        <p>이 메뉴는 관리자만 이용할 수 있습니다.</p>
        <Link to="/mypage">마이페이지로 돌아가기</Link>
      </main>
    )
  }

  return <Outlet />
}

export default AdminRoute
