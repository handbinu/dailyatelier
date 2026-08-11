import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { searchArts } from '../api/artApi'
import ArtSearch from '../pages/Search/ArtSearch'

vi.mock('../api/artApi', () => ({ searchArts: vi.fn() }))
const emptyPage = { content: [], totalElements: 0, totalPages: 0 }

function LocationDisplay() { const location = useLocation(); return <output data-testid="location">{location.pathname}{location.search}</output> }
function renderSearch(entry = '/search') {
  return render(<MemoryRouter initialEntries={[entry]}><Routes><Route path="/search" element={<><ArtSearch /><LocationDisplay /></>} /></Routes></MemoryRouter>)
}

describe('작품 탐색 화면', () => {
  beforeEach(() => { vi.clearAllMocks(); searchArts.mockResolvedValue({ data: emptyPage }); window.scrollTo = vi.fn() })

  it('잘못된 URL 값을 제거하고 유효한 조건으로 조회한다', async () => {
    renderSearch('/search?q=%20%20%EB%B4%84%20%20&format=VIDEO&sort=PRICE_ASC&page=-2')
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/search?q=%EB%B4%84&sort=PRICE_ASC'))
    expect(searchArts).toHaveBeenLastCalledWith(expect.objectContaining({ q: '봄', format: '', sort: 'PRICE_ASC', page: 0, size: 12 }))
  })

  it('필터 변경 시 페이지를 1로 초기화하고 가격 정렬을 URL에 반영한다', async () => {
    renderSearch('/search?page=3')
    await screen.findByText('검색 결과가 없습니다')
    fireEvent.change(screen.getByLabelText('상태'), { target: { value: 'ONGOING' } })
    expect(screen.getByTestId('location')).toHaveTextContent('/search?status=ONGOING')
    fireEvent.change(screen.getByLabelText('정렬'), { target: { value: 'PRICE_DESC' } })
    expect(screen.getByTestId('location')).toHaveTextContent('/search?status=ONGOING&sort=PRICE_DESC')
  })

  it('늦게 끝난 이전 요청이 최신 결과를 덮지 못하게 한다', async () => {
    let resolveFirst
    const first = new Promise((resolve) => { resolveFirst = resolve })
    searchArts.mockReturnValueOnce(first).mockResolvedValueOnce({ data: { content: [{ artId: 2, name: '최신 작품', artistName: '작가', currentPrice: 1, status: 'ONGOING' }], totalElements: 1, totalPages: 1 } })
    renderSearch('/search?q=이전')
    fireEvent.change(screen.getByLabelText('상태'), { target: { value: 'ONGOING' } })
    expect(await screen.findByText('최신 작품')).toBeInTheDocument()
    resolveFirst({ data: { content: [{ artId: 1, name: '오래된 작품' }], totalElements: 1, totalPages: 1 } })
    await waitFor(() => expect(screen.queryByText('오래된 작품')).not.toBeInTheDocument())
  })

  it('오류 상태에서 재시도한다', async () => {
    searchArts.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce({ data: emptyPage })
    renderSearch()
    expect(await screen.findByText('작품을 불러오지 못했습니다')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText('검색 결과가 없습니다')).toBeInTheDocument()
    expect(searchArts).toHaveBeenCalledTimes(2)
  })
})
