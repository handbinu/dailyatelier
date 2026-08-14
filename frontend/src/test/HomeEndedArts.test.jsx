import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getArts, searchArts } from '../api/artApi'
import Home from '../pages/Home/Home'

vi.mock('../api/artApi', () => ({ getArts: vi.fn(), searchArts: vi.fn() }))

const soldArt = { artId: 7, name: '낙찰 작품', imgPath: '/sold.jpg', currentPrice: 120000, format: 'DIGITAL', status: 'ENDED', result: 'SOLD' }
const unsoldArt = { artId: 8, name: '유찰 작품', imgPath: '', currentPrice: 80000, format: 'PHYSICAL', status: 'ENDED', result: 'UNSOLD' }

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => { resolve = resolvePromise })
  return { promise, resolve }
}

function renderHome() {
  return render(<MemoryRouter><Home /></MemoryRouter>)
}

describe('홈 종료 작품', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.matchMedia = vi.fn().mockReturnValue({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })
    getArts.mockResolvedValue({ data: { content: [] } })
  })

  it('최근 종료 작품을 조회하고 실제 ID와 판매·유찰 결과를 표시한다', async () => {
    searchArts.mockResolvedValue({ data: { content: [soldArt, unsoldArt] } })
    renderHome()

    expect(screen.getByLabelText('종료 작품을 불러오는 중')).toHaveAttribute('aria-busy', 'true')
    expect(await screen.findByText('낙찰 작품')).toBeInTheDocument()
    expect(screen.getByText('유찰 작품')).toBeInTheDocument()
    expect(screen.getByText('낙찰가: 120,000원')).toBeInTheDocument()
    expect(screen.getByText('최종가: 80,000원')).toBeInTheDocument()
    expect(screen.getByText('낙찰')).toBeInTheDocument()
    expect(screen.getByText('유찰')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '낙찰 작품 종료 작품 상세 보기' })).toHaveAttribute('href', '/auction/7')
    expect(document.querySelector('a[href^="/auction/e"]')).not.toBeInTheDocument()
    expect(searchArts).toHaveBeenCalledWith(expect.objectContaining({ status: 'ENDED', sort: 'RECENTLY_ENDED', format: undefined, page: 0, size: 6 }))
    expect(screen.getByRole('link', { name: '종료 작품 전체 보기 →' })).toHaveAttribute('href', '/search?status=ENDED&sort=RECENTLY_ENDED')
  })

  it('탭 전환 시 이전 요청을 취소하고 마지막 형식 결과를 유지한다', async () => {
    const digitalRequest = deferred()
    searchArts
      .mockResolvedValueOnce({ data: { content: [] } })
      .mockReturnValueOnce(digitalRequest.promise)
      .mockResolvedValueOnce({ data: { content: [{ ...unsoldArt, artId: 9, name: '실물 종료 작품' }] } })

    renderHome()
    await screen.findByText('선택한 유형의 종료 작품이 없습니다.')
    fireEvent.click(screen.getByRole('tab', { name: '디지털' }))
    await waitFor(() => expect(searchArts).toHaveBeenCalledTimes(2))
    const digitalSignal = searchArts.mock.calls[1][0].signal
    fireEvent.click(screen.getByRole('tab', { name: '실물' }))

    expect(await screen.findByText('실물 종료 작품')).toBeInTheDocument()
    expect(digitalSignal.aborted).toBe(true)
    digitalRequest.resolve({ data: { content: [{ ...soldArt, name: '늦은 디지털 응답' }] } })
    await waitFor(() => expect(screen.queryByText('늦은 디지털 응답')).not.toBeInTheDocument())
    expect(searchArts).toHaveBeenLastCalledWith(expect.objectContaining({ format: 'PHYSICAL' }))
    expect(screen.getByRole('link', { name: '종료 작품 전체 보기 →' })).toHaveAttribute('href', '/search?status=ENDED&sort=RECENTLY_ENDED&format=PHYSICAL')
  })

  it('조회 오류를 표시하고 재시도 후 빈 상태로 복구한다', async () => {
    searchArts
      .mockRejectedValueOnce({ response: { data: { message: '종료 작품 조회 실패' } } })
      .mockResolvedValueOnce({ data: { content: [] } })
    renderHome()

    expect(await screen.findByText('종료 작품 조회 실패')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText('선택한 유형의 종료 작품이 없습니다.')).toBeInTheDocument()
    expect(searchArts).toHaveBeenCalledTimes(2)
  })
})
