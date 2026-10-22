import { useSyncExternalStore } from 'react'
import { Link, Outlet } from 'react-router-dom'
import { getStoredUserStatus, subscribeToAuthChanges } from '../../utils/authStorage'

function ArtistRoute() {
  const userStatus = useSyncExternalStore(subscribeToAuthChanges, getStoredUserStatus)

  if (Number(userStatus) !== 1) {
    return (
      <main className="route-access-denied" aria-labelledby="artist-access-title">
        <h1 id="artist-access-title">작가 회원 전용 페이지입니다</h1>
        <p>이 메뉴는 작가 회원만 이용할 수 있습니다.</p>
        <Link to="/mypage">마이페이지로 돌아가기</Link>
      </main>
    )
  }

  return <Outlet />
}

export default ArtistRoute
