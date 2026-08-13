import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createBid, getArt } from '../api/artApi'
import { getArtLikeStatus } from '../api/userApi'
import ArtDetail from '../pages/Auction/ArtDetail'

vi.mock('../api/artApi', () => ({ createBid: vi.fn(), getArt: vi.fn() }))
vi.mock('../api/userApi', () => ({ addArtLike: vi.fn(), getArtLikeStatus: vi.fn(), removeArtLike: vi.fn() }))

const art = {
  artId: 1, name: '여름', artistName: '김작가', material: '캔버스', imgPath: '/art.jpg',
  descript: '설명', startPrice: 30000, currentPrice: 30000, minimumBidIncrement: 1000,
  nextMinimumBidPrice: 31000, bidStartTime: '2020-01-01T00:00:00', closingTime: '2099-01-01T00:00:00',
  artStatus: 0, isOwner: false,
}

const renderPage = () => render(
  <MemoryRouter initialEntries={['/auction/1']}>
    <Routes><Route path="/auction/:id" element={<ArtDetail />} /></Routes>
  </MemoryRouter>,
)

describe('작품 상세 최소 입찰 증분', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('token', 'token')
    getArt.mockResolvedValue({ data: art })
    getArtLikeStatus.mockResolvedValue({ data: { liked: false } })
  })

  it('현재가, 다음 입찰 가능 금액과 최소 증분을 구분해 표시한다', async () => {
    renderPage()
    expect(await screen.findByText('30,000원')).toBeVisible()
    expect(screen.getByText('다음 입찰 가능 금액')).toBeVisible()
    expect(screen.getAllByText('31,000원').length).toBeGreaterThan(0)
    expect(screen.getByText('최소 입찰 증분')).toBeVisible()
    expect(screen.getByText('1,000원')).toBeVisible()
    expect(screen.getByText(/서버가 최신 금액으로 최종 확인합니다/)).toBeVisible()
  })

  it('원본 이미지 dialog를 접근 가능한 제목과 닫기 동작으로 제공한다', async () => {
    renderPage()
    const opener = await screen.findByRole('button', { name: '여름 원본 이미지 보기' })
    opener.focus()
    fireEvent.click(opener)

    expect(screen.getByRole('dialog', { name: '여름 원본 이미지' })).toBeVisible()
    await waitFor(() => expect(screen.getByRole('button', { name: '닫기' })).toHaveFocus())
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('최소가보다 1원 낮으면 차단하고 100원 배수가 아닌 유효 입찰은 전송한다', async () => {
    createBid.mockResolvedValue({ data: { bidPrice: 31001, currentPrice: 31001, minimumBidIncrement: 1000, nextMinimumBidPrice: 32001 } })
    renderPage()
    const input = await screen.findByLabelText('입찰 금액')

    fireEvent.change(input, { target: { value: '30999' } })
    fireEvent.click(screen.getByRole('button', { name: '입찰하기' }))
    expect(screen.getByText('최소 입찰 가능 금액은 31,000원입니다.')).toBeVisible()
    expect(createBid).not.toHaveBeenCalled()

    fireEvent.change(input, { target: { value: '31001' } })
    fireEvent.click(screen.getByRole('button', { name: '입찰하기' }))
    await waitFor(() => expect(createBid).toHaveBeenCalledWith(1, 31001))
    expect(await screen.findByText('32,001원')).toBeVisible()
  })

  it('입찰 성공 응답으로 현재가, 증분과 다음 최소가를 갱신한다', async () => {
    createBid.mockResolvedValue({ data: { bidPrice: 31000, currentPrice: 31000, minimumBidIncrement: 2000, nextMinimumBidPrice: 33000 } })
    renderPage()
    fireEvent.change(await screen.findByLabelText('입찰 금액'), { target: { value: '31000' } })
    fireEvent.click(screen.getByRole('button', { name: '입찰하기' }))

    expect(await screen.findByText('33,000원')).toBeVisible()
    expect(screen.getByText('2,000원')).toBeVisible()
  })

  it('BID_TOO_LOW 후 재조회한 최신 최소가를 오류에 직접 표시한다', async () => {
    getArt.mockResolvedValueOnce({ data: art }).mockResolvedValueOnce({ data: { ...art, currentPrice: 32000, nextMinimumBidPrice: 33000 } })
    createBid.mockRejectedValue({ response: { status: 409, data: { code: 'BID_TOO_LOW', message: '낮음' } } })
    renderPage()
    fireEvent.change(await screen.findByLabelText('입찰 금액'), { target: { value: '31000' } })
    fireEvent.click(screen.getByRole('button', { name: '입찰하기' }))

    expect(await screen.findByText('현재가가 갱신되었습니다. 최소 입찰 가능 금액은 33,000원입니다.')).toBeVisible()
    expect(getArt).toHaveBeenCalledTimes(2)
  })

  it.each([
    ['상세 응답의 null', null],
    ['BID_LIMIT_REACHED', 31000],
  ])('%s이면 한도 안내와 함께 입력과 버튼을 비활성화한다', async (scenario, nextMinimumBidPrice) => {
    getArt.mockResolvedValue({ data: { ...art, nextMinimumBidPrice } })
    if (scenario === 'BID_LIMIT_REACHED') {
      createBid.mockRejectedValue({ response: { status: 409, data: { code: 'BID_LIMIT_REACHED' } } })
    }
    renderPage()
    const input = await screen.findByLabelText('입찰 금액')

    if (scenario === 'BID_LIMIT_REACHED') {
      fireEvent.change(input, { target: { value: '31000' } })
      fireEvent.click(screen.getByRole('button', { name: '입찰하기' }))
      await waitFor(() => expect(createBid).toHaveBeenCalled())
    }

    expect((await screen.findAllByText('최소 증분을 적용하면 시스템 최대 입찰가를 초과합니다.'))[0]).toBeVisible()
    expect(input).toBeDisabled()
    expect(screen.getByRole('button', { name: '입찰하기' })).toBeDisabled()
  })
})
