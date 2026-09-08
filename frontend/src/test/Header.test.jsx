import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Header from '../components/Header/Header'

function LocationDisplay() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}{location.search}</output>
}

function HistoryControls() {
  const navigate = useNavigate()
  return (
    <>
      <button type="button" onClick={() => navigate(-1)}>뒤로</button>
      <button type="button" onClick={() => navigate(1)}>앞으로</button>
    </>
  )
}

function renderHeader({ initialEntries = ['/'], initialIndex, mediaQuery } = {}) {
  const resolvedMediaQuery = mediaQuery ?? {
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }
  vi.stubGlobal('matchMedia', vi.fn().mockReturnValue(resolvedMediaQuery))
  return render(
    <MemoryRouter initialEntries={initialEntries} initialIndex={initialIndex}>
      <Header />
      <main>본문</main>
      <footer>푸터</footer>
      <LocationDisplay />
      <HistoryControls />
    </MemoryRouter>,
  )
}

afterEach(() => {
  localStorage.clear()
  vi.unstubAllGlobals()
})

function submitWithEnter(input) {
  fireEvent.submit(input.closest('form'))
}

describe('Header 검색', () => {
  it('작품 검색은 입력값을 유지해 버튼으로 검색한다', () => {
    renderHeader()

    expect(screen.getByRole('combobox', { name: '검색 유형' })).toHaveValue('artwork')
    fireEvent.change(screen.getByRole('textbox', { name: '검색어' }), { target: { value: '  여름 풍경  ' } })
    fireEvent.click(screen.getByRole('button', { name: '검색' }))

    expect(screen.getByTestId('location')).toHaveTextContent(`/search?q=${encodeURIComponent('  여름 풍경  ')}`)
  })

  it('작품 검색은 Enter 제출도 같은 경로로 연결한다', () => {
    renderHeader()

    const input = screen.getByRole('textbox', { name: '검색어' })
    fireEvent.change(input, { target: { value: '봄&꽃' } })
    submitWithEnter(input)

    expect(screen.getByTestId('location')).toHaveTextContent(`/search?q=${encodeURIComponent('봄&꽃')}`)
  })

  it('작가 검색은 앞뒤 공백을 제거해 검색한다', () => {
    renderHeader()

    fireEvent.change(screen.getByRole('combobox', { name: '검색 유형' }), { target: { value: 'artist' } })
    const input = screen.getByRole('textbox', { name: '검색어' })
    fireEvent.change(input, { target: { value: '  김 작가  ' } })
    submitWithEnter(input)

    expect(screen.getByTestId('location')).toHaveTextContent(`/artists?keyword=${encodeURIComponent('김 작가')}`)
  })

  it('빈 작가 검색은 쿼리 없이 전체 작가 목록으로 이동한다', () => {
    renderHeader()

    fireEvent.change(screen.getByRole('combobox', { name: '검색 유형' }), { target: { value: 'artist' } })
    fireEvent.change(screen.getByRole('textbox', { name: '검색어' }), { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: '검색' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/artists')
  })

  it('모바일 메뉴 검색은 같은 상태를 사용하고 제출 후 메뉴를 닫는다', () => {
    renderHeader()

    const toggle = screen.getByRole('button', { name: '모바일 메뉴 열기' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(toggle)
    expect(screen.getByRole('button', { name: '모바일 메뉴 닫기' })).toHaveAttribute('aria-expanded', 'true')
    const searchTypes = screen.getAllByRole('combobox', { name: '검색 유형' })
    const searchInputs = screen.getAllByRole('textbox', { name: '검색어' })
    const searchButtons = screen.getAllByRole('button', { name: '검색' })

    fireEvent.change(searchTypes[1], { target: { value: 'artist' } })
    fireEvent.change(searchInputs[1], { target: { value: ' 모바일 작가 ' } })
    fireEvent.click(searchButtons[1])

    expect(screen.getByTestId('location')).toHaveTextContent(`/artists?keyword=${encodeURIComponent('모바일 작가')}`)
    expect(screen.getAllByRole('combobox', { name: '검색 유형' })).toHaveLength(1)
  })

  it('로고 route 이동 후 모바일 메뉴와 배경 잠금을 정리한다', () => {
    renderHeader({ initialEntries: ['/mypage'] })

    fireEvent.click(screen.getByRole('button', { name: '모바일 메뉴 열기' }))
    const logo = screen.getByRole('link', { name: 'Daily Atelier' })
    logo.focus()

    expect(document.body.style.overflow).toBe('hidden')
    expect(screen.getByRole('main').inert).toBe(true)
    expect(screen.getByRole('contentinfo').inert).toBe(true)

    fireEvent.click(logo)

    expect(screen.getByTestId('location')).toHaveTextContent('/')
    expect(screen.getByRole('button', { name: '모바일 메뉴 열기' })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('navigation', { name: '모바일 주 메뉴' })).not.toBeInTheDocument()
    expect(document.body.style.overflow).toBe('')
    expect(screen.getByRole('main').inert).toBe(false)
    expect(screen.getByRole('contentinfo').inert).toBe(false)
    expect(logo).toHaveFocus()
  })

  it.each([
    { label: '작가 목록', target: '/artists', authenticated: false },
    { label: '마이페이지', target: '/mypage', authenticated: true },
  ])('모바일 내부 링크 $label 이동 후 메뉴를 닫는다', ({ label, target, authenticated }) => {
    if (authenticated) localStorage.setItem('token', 'test-token')
    renderHeader({ initialEntries: ['/auction/total'] })

    fireEvent.click(screen.getByRole('button', { name: '모바일 메뉴 열기' }))
    const mobileMenu = screen.getByRole('navigation', { name: '모바일 주 메뉴' })
    const link = within(mobileMenu).getByRole('link', { name: label })
    link.focus()
    fireEvent.click(link)

    expect(screen.getByTestId('location')).toHaveTextContent(target)
    expect(screen.queryByRole('navigation', { name: '모바일 주 메뉴' })).not.toBeInTheDocument()
    expect(link).not.toHaveFocus()
  })

  it('뒤로가기와 앞으로가기 route 변경 후 모바일 메뉴를 닫는다', () => {
    renderHeader({
      initialEntries: ['/auction/total', '/artists', '/mypage'],
      initialIndex: 1,
    })

    fireEvent.click(screen.getByRole('button', { name: '모바일 메뉴 열기' }))
    fireEvent.click(screen.getByRole('button', { name: '뒤로' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/auction/total')
    expect(screen.queryByRole('navigation', { name: '모바일 주 메뉴' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '모바일 메뉴 열기' }))
    fireEvent.click(screen.getByRole('button', { name: '앞으로' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/artists')
    expect(screen.queryByRole('navigation', { name: '모바일 주 메뉴' })).not.toBeInTheDocument()
    expect(document.body.style.overflow).toBe('')
    expect(screen.getByRole('main').inert).toBe(false)
    expect(screen.getByRole('contentinfo').inert).toBe(false)
  })

  it('모바일 메뉴는 Escape 후 햄버거로 focus를 복귀한다', () => {
    vi.stubGlobal('requestAnimationFrame', (callback) => callback())
    renderHeader()

    fireEvent.click(screen.getByRole('button', { name: '모바일 메뉴 열기' }))
    fireEvent.keyDown(document, { key: 'Escape' })

    const toggle = screen.getByRole('button', { name: '모바일 메뉴 열기' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle).toHaveFocus()
  })

  it('데스크톱 breakpoint 진입 시 모바일 메뉴를 닫는다', () => {
    let changeHandler
    const mediaQuery = {
      matches: false,
      addEventListener: vi.fn((event, handler) => {
        if (event === 'change') changeHandler = handler
      }),
      removeEventListener: vi.fn(),
    }
    renderHeader({ mediaQuery })

    fireEvent.click(screen.getByRole('button', { name: '모바일 메뉴 열기' }))
    act(() => changeHandler({ matches: true }))

    expect(screen.queryByRole('navigation', { name: '모바일 주 메뉴' })).not.toBeInTheDocument()
    expect(document.body.style.overflow).toBe('')
  })

  it('데스크톱 드롭다운은 제어 대상을 연결하고 Escape 후 trigger로 복귀한다', () => {
    vi.stubGlobal('requestAnimationFrame', (callback) => callback())
    renderHeader()

    const trigger = screen.getByRole('button', { name: '경매' })
    fireEvent.click(trigger)
    expect(trigger).toHaveAttribute('aria-expanded', 'true')
    expect(trigger).toHaveAttribute('aria-controls', 'desktop-nav-menu-0')

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(trigger).toHaveAttribute('aria-expanded', 'false')
    expect(trigger).toHaveFocus()
  })
})
