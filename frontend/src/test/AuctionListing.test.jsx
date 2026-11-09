import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { searchArts } from '../api/artApi'
import AuctionTotal from '../pages/Auction/AuctionTotal'

vi.mock('../api/artApi', () => ({ searchArts: vi.fn() }))
const emptyPage = { content: [], totalElements: 0, totalPages: 0 }

function LocationDisplay() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}{location.search}|{location.state?.from || ''}</output>
}

function renderAuction(entry, type = 'total') {
  return render(<MemoryRouter initialEntries={[entry]}><Routes>
    <Route path="/auction/:kind" element={<><AuctionTotal type={type} /><LocationDisplay /></>} />
    <Route path="/auction/:id/detail" element={<LocationDisplay />} />
    <Route path="/auction/:id" element={<LocationDisplay />} />
  </Routes></MemoryRouter>)
}

describe('경매 목록 preset', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    searchArts.mockResolvedValue({ data: emptyPage })
    window.scrollTo = vi.fn()
  })

  it('전체 경매에 ONGOING을 강제하고 고정 조건 쿼리를 제거한다', async () => {
    renderAuction('/auction/total?status=ENDED&format=DIGITAL&category=CRAFT&sort=PRICE_ASC&page=2')
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/auction/total?category=CRAFT&sort=PRICE_ASC&page=2'))
    expect(searchArts).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'ONGOING', format: '', category: 'CRAFT', sort: 'PRICE_ASC', page: 1 }))
  })

  it.each([
    ['digital', 'DIGITAL'],
    ['analog', 'PHYSICAL'],
  ])('%s 경매에 진행 상태와 형태를 강제한다', async (type, format) => {
    renderAuction(`/auction/${type}?status=UPCOMING&format=PHYSICAL`, type)
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(`/auction/${type}|`))
    expect(searchArts).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'ONGOING', format }))
  })

  it('고정 형태와 충돌하는 카테고리를 URL에서 제거한다', async () => {
    renderAuction('/auction/digital?category=SCULPTURE', 'digital')
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/auction/digital|'))
    expect(searchArts).toHaveBeenLastCalledWith(expect.objectContaining({ format: 'DIGITAL', category: '' }))
    expect(screen.queryByRole('option', { name: '조각' })).not.toBeInTheDocument()
  })

  it('카테고리 변경 시 페이지를 초기화하고 상세 이동에 원래 URL을 보존한다', async () => {
    searchArts.mockResolvedValue({ data: { content: [{ artId: 7, name: '봄', artistName: '작가', currentPrice: 1000, status: 'ONGOING', closingTime: '2030-01-01T00:00:00' }], totalElements: 1, totalPages: 1 } })
    renderAuction('/auction/digital?sort=PRICE_DESC&page=3', 'digital')
    expect(await screen.findByText('봄')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('카테고리'), { target: { value: 'DIGITAL_ART' } })
    expect(screen.getByTestId('location')).toHaveTextContent('/auction/digital?category=DIGITAL_ART&sort=PRICE_DESC|')
    fireEvent.click(await screen.findByRole('link', { name: '봄 작품 상세 보기' }))
    expect(screen.getByTestId('location')).toHaveTextContent('/auction/7|/auction/digital?category=DIGITAL_ART&sort=PRICE_DESC')
  })
})
