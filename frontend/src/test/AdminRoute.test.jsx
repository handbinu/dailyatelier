import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import AdminRoute from '../pages/auth/AdminRoute'

function renderAdminRoute() {
  render(
    <MemoryRouter initialEntries={['/admin/inquiries']}>
      <Routes>
        <Route path="/mypage" element={<p>마이페이지</p>} />
        <Route element={<AdminRoute />}>
          <Route path="/admin/inquiries" element={<p>문의 관리 화면</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('관리자 전용 라우트', () => {
  beforeEach(() => localStorage.clear())

  it('일반 회원은 관리자 화면에 접근할 수 없다', () => {
    localStorage.setItem('userStatus', '0')
    renderAdminRoute()

    expect(screen.getByRole('heading', { name: '관리자 전용 페이지입니다' })).toBeVisible()
    expect(screen.queryByText('문의 관리 화면')).not.toBeInTheDocument()
  })

  it('관리자는 관리자 화면에 접근한다', () => {
    localStorage.setItem('userStatus', '2')
    renderAdminRoute()

    expect(screen.getByText('문의 관리 화면')).toBeVisible()
  })
})
