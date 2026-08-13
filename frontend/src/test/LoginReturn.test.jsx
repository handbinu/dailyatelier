import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { login } from '../api/authApi'
import Login from '../pages/auth/Login'
import PrivateRoute from '../pages/auth/PrivateRoute'
import { toSafeReturnPath } from '../utils/loginReturn'

vi.mock('../api/authApi', () => ({ login: vi.fn() }))

function LocationView() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}{location.search}{location.hash}</output>
}

function renderRoutes(initialEntries) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<PrivateRoute />}>
          <Route path="/mypage/order-status" element={<LocationView />} />
        </Route>
        <Route path="/" element={<LocationView />} />
      </Routes>
    </MemoryRouter>,
  )
}

const submitLogin = async () => {
  fireEvent.change(screen.getByLabelText('아이디'), { target: { value: 'user' } })
  fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password' } })
  fireEvent.click(screen.getByRole('button', { name: '로그인' }))
  await waitFor(() => expect(login).toHaveBeenCalledWith('user', 'password'))
}

describe('로그인 후 원래 위치 복귀', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    login.mockResolvedValue({
      data: { token: 'token', userId: 'user', nickname: '사용자', userStatus: 0 },
    })
  })

  it('보호 화면의 query와 hash를 로그인 후 복원한다', async () => {
    renderRoutes(['/mypage/order-status?status=PAID#order-7'])

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeVisible()
    await submitLogin()

    expect(await screen.findByTestId('location')).toHaveTextContent(
      '/mypage/order-status?status=PAID#order-7',
    )
  })

  it('복귀 정보가 없는 직접 로그인은 홈으로 이동한다', async () => {
    renderRoutes(['/login'])
    await submitLogin()
    expect(await screen.findByTestId('location')).toHaveTextContent('/')
  })

  it.each([
    ['외부 절대 URL', 'https://evil.example/steal'],
    ['protocol-relative URL', '//evil.example/steal'],
    ['백슬래시 URL', '/\\evil.example/steal'],
    ['로그인 순환', '/login?next=/mypage'],
  ])('%s 복귀 값은 홈으로 대체한다', async (_name, from) => {
    renderRoutes([{ pathname: '/login', state: { from } }])
    await submitLogin()
    expect(await screen.findByTestId('location')).toHaveTextContent('/')
  })

  it('안전한 location 객체만 pathname, search와 hash를 결합한다', () => {
    expect(toSafeReturnPath({
      pathname: '/auction/7',
      search: '?bid=true',
      hash: '#bid-form',
    })).toBe('/auction/7?bid=true#bid-form')
    expect(toSafeReturnPath({ pathname: 'https://evil.example' })).toBe('/')
  })
})
