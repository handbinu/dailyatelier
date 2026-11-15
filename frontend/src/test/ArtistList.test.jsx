import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getArtists } from '../api/artistApi'
import ArtistList from '../pages/Artist/ArtistList'

vi.mock('../api/artistApi', () => ({ getArtists: vi.fn() }))

const artistPage = {
  content: [{ artistId: 'artist-code', profileImagePath: '/img/artist.png', artistName: '김화가', artistIntro: '빛을 그리는 작가입니다.', activeArtCount: 2 }],
  totalElements: 1,
  totalPages: 1,
}

function LocationView() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}{location.search}</output>
}

function renderList(path = '/artists?page=1') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes><Route path="/artists" element={<><ArtistList /><LocationView /></>} /></Routes>
    </MemoryRouter>,
  )
}

describe('작가 목록 화면', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.scrollTo = vi.fn()
    getArtists.mockResolvedValue({ data: artistPage })
  })

  it('URL 검색 조건으로 작가 목록을 조회하고 결과를 표시한다', async () => {
    renderList('/artists?keyword=%EA%B9%80&page=2')

    expect(await screen.findByRole('heading', { name: '김화가' })).toBeInTheDocument()
    expect(screen.getByText('빛을 그리는 작가입니다.')).toBeInTheDocument()
    expect(screen.getByText('2점')).toBeInTheDocument()
    expect(getArtists).toHaveBeenCalledWith(expect.objectContaining({ keyword: '김', page: 1, size: 12 }))
  })

  it('검색어를 정리하고 페이지를 1로 초기화한다', async () => {
    renderList('/artists?keyword=%EA%B8%B0%EC%A1%B4&page=3')
    await screen.findByRole('heading', { name: '김화가' })

    fireEvent.change(screen.getByRole('textbox', { name: '작가명 검색' }), { target: { value: '  새 작가  ' } })
    fireEvent.click(screen.getByRole('button', { name: '검색' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/artists?keyword=%EC%83%88+%EC%9E%91%EA%B0%80&page=1'))
    expect(getArtists).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '새 작가', page: 0 }))
  })

  it('오류 메시지를 표시하고 재시도한다', async () => {
    getArtists.mockRejectedValueOnce({ response: { data: { message: '조회 실패' } } }).mockResolvedValueOnce({ data: artistPage })
    renderList()

    expect(await screen.findByText('조회 실패')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { name: '김화가' })).toBeInTheDocument()
    expect(getArtists).toHaveBeenCalledTimes(2)
  })

  it('범위를 벗어난 빈 페이지에서 검색어를 유지하고 첫 페이지로 이동한다', async () => {
    getArtists.mockResolvedValue({ data: { content: [], totalElements: 1, totalPages: 1 } })
    renderList('/artists?keyword=%EA%B9%80&page=3')

    fireEvent.click(await screen.findByRole('button', { name: '첫 페이지로' }))
    expect(screen.getByTestId('location')).toHaveTextContent('/artists?keyword=%EA%B9%80&page=1')
  })
})
