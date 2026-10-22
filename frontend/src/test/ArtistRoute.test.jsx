import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ArtistRoute from '../pages/auth/ArtistRoute'
import PrivateRoute from '../pages/auth/PrivateRoute'

function LocationView({ onRender }) {
  const location = useLocation()
  onRender?.()
  return <output>{location.pathname}{location.search}{location.hash}</output>
}

function LoginLocationView() {
  const location = useLocation()
  const from = location.state?.from
  return <output>{location.pathname}|{from?.pathname}{from?.search}{from?.hash}</output>
}

function renderArtistRoute(initialEntry, onArtistRender = vi.fn()) {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/login" element={<LoginLocationView />} />
        <Route path="/mypage" element={<LocationView />} />
        <Route element={<PrivateRoute />}>
          <Route element={<ArtistRoute />}>
            <Route path="/upload" element={<LocationView onRender={onArtistRender} />} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>,
  )
  return onArtistRender
}

describe('작가 전용 라우트', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('비로그인은 원래 작가 URL을 보존해 로그인으로 이동한다', () => {
    renderArtistRoute('/upload?draft=7#price')

    expect(screen.getByText('/login|/upload?draft=7#price')).toBeVisible()
  })

  it('일반 회원은 자식 화면을 렌더링하지 않고 접근 가능한 안내를 표시한다', () => {
    localStorage.setItem('token', 'token')
    localStorage.setItem('userStatus', '0')
    const onArtistRender = renderArtistRoute('/upload')

    expect(screen.getByRole('heading', { name: '작가 회원 전용 페이지입니다' })).toBeVisible()
    expect(screen.getByRole('link', { name: '마이페이지로 돌아가기' })).toHaveAttribute('href', '/mypage')
    expect(onArtistRender).not.toHaveBeenCalled()
  })

  it('작가 회원은 자식 화면에 진입한다', () => {
    localStorage.setItem('token', 'token')
    localStorage.setItem('userStatus', '1')
    const onArtistRender = renderArtistRoute('/upload')

    expect(screen.getByText('/upload')).toBeVisible()
    expect(onArtistRender).toHaveBeenCalledOnce()
  })
})
