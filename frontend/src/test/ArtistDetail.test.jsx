import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getArtist, getArtistArts } from '../api/artistApi'
import ArtistDetail from '../pages/Artist/ArtistDetail'

vi.mock('../api/artistApi', () => ({ getArtist: vi.fn(), getArtistArts: vi.fn() }))

const artist = { artistId: 'artist-code', profileImagePath: '/img/artist.png', artistName: '김화가', artistIntro: '자연을 그립니다.', activeArtCount: 1 }
const artsPage = {
  content: [{ artId: 7, name: '숲', currentPrice: 120000, closingTime: '2099-08-06T12:00:00', imgPath: '/img/art.jpg' }],
  totalElements: 4,
  totalPages: 2,
}

function renderDetail(path = '/artists/artist-code?page=1', state) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: path.split('?')[0], search: path.includes('?') ? `?${path.split('?')[1]}` : '', state }]}>
      <Routes>
        <Route path="/artists/:artistId" element={<ArtistDetail />} />
        <Route path="/artists" element={<div>작가 목록 도착</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('작가 상세 화면', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getArtist.mockResolvedValue({ data: artist })
    getArtistArts.mockResolvedValue({ data: artsPage })
  })

  it('프로필과 공개 작품을 구분해 표시한다', async () => {
    renderDetail('/artists/artist-code?page=2')

    expect(await screen.findByRole('heading', { name: '김화가' })).toBeInTheDocument()
    expect(screen.getByText('현재 입찰 가능한 작품')).toHaveTextContent('1점')
    expect(screen.getByRole('heading', { name: '숲' })).toBeInTheDocument()
    expect(screen.getByText('총 4점')).toBeInTheDocument()
    expect(getArtist).toHaveBeenCalledWith('artist-code', expect.any(Object))
    expect(getArtistArts).toHaveBeenCalledWith('artist-code', expect.objectContaining({ page: 1, size: 12 }))
  })

  it('ARTIST_NOT_FOUND를 별도 상태로 처리한다', async () => {
    getArtist.mockRejectedValue({ response: { status: 404, data: { code: 'ARTIST_NOT_FOUND' } } })
    renderDetail()

    expect(await screen.findByRole('heading', { name: '존재하지 않는 작가입니다' })).toBeInTheDocument()
    expect(getArtistArts).not.toHaveBeenCalled()
  })

  it('작품 조회 오류를 일반 오류로 표시하고 재시도한다', async () => {
    getArtistArts.mockRejectedValueOnce({ response: { status: 500, data: { message: '작품 조회 실패' } } }).mockResolvedValueOnce({ data: artsPage })
    renderDetail()

    expect(await screen.findByText('작품 조회 실패')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { name: '숲' })).toBeInTheDocument()
    expect(getArtistArts).toHaveBeenCalledTimes(2)
  })

  it('목록에서 전달한 검색 위치로 돌아간다', async () => {
    renderDetail('/artists/artist-code?page=1', { from: '/artists?keyword=%EA%B9%80&page=3' })
    const back = await screen.findByRole('link', { name: '← 작가 목록' })
    expect(back).toHaveAttribute('href', '/artists?keyword=%EA%B9%80&page=3')
  })
})
