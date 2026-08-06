import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import Header from '../components/Header/Header'

function LocationDisplay() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}{location.search}</output>
}

function renderHeader() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Header />
      <LocationDisplay />
    </MemoryRouter>,
  )
}

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

    fireEvent.click(screen.getByRole('button', { name: '모바일 메뉴' }))
    const searchTypes = screen.getAllByRole('combobox', { name: '검색 유형' })
    const searchInputs = screen.getAllByRole('textbox', { name: '검색어' })
    const searchButtons = screen.getAllByRole('button', { name: '검색' })

    fireEvent.change(searchTypes[1], { target: { value: 'artist' } })
    fireEvent.change(searchInputs[1], { target: { value: ' 모바일 작가 ' } })
    fireEvent.click(searchButtons[1])

    expect(screen.getByTestId('location')).toHaveTextContent(`/artists?keyword=${encodeURIComponent('모바일 작가')}`)
    expect(screen.getAllByRole('combobox', { name: '검색 유형' })).toHaveLength(1)
  })
})
