import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { StrictMode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BidStatus from '../pages/MyPage/BidStatus'
import Likes from '../pages/MyPage/Likes'
import { getAllMyBids, getMyLikes } from '../api/userApi'

vi.mock('../api/userApi', () => ({
  getAllMyBids: vi.fn(),
  getMyLikes: vi.fn(),
  removeArtLike: vi.fn(),
}))

const page = (content, { number = 0, last = true } = {}) => ({ content, number, last })

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const like = (likeId, artName) => ({
  likeId,
  artId: likeId,
  artName,
  artImg: '',
  artStatus: 0,
  currentPrice: 10_000,
})

describe('문제 묶음 5 조회 상태', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('token', 'token')
  })

  it('입찰 현황 조회 실패 중 상태별 통계를 0건으로 표시하지 않고 재시도로 복구한다', async () => {
    getAllMyBids
      .mockRejectedValueOnce(new Error('조회 실패'))
      .mockResolvedValueOnce([{
        artId: 1,
        artName: '진행 작품',
        auctionStatus: 'ONGOING',
        myBidPrice: 10_000,
        currentPrice: 10_000,
      }])

    render(<MemoryRouter><BidStatus /></MemoryRouter>)

    expect(await screen.findByText('입찰 현황을 불러오지 못했습니다.')).toBeVisible()
    for (const label of ['진행 중', '종료 임박', '종료']) {
      const card = screen.getAllByText(label).find((node) => node.className.includes('summaryLabel')).closest('div')
      expect(within(card).getByText('-')).toBeVisible()
    }

    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText('진행 작품')).toBeVisible()
    const ongoingCard = screen.getAllByText('진행 중').find((node) => node.className.includes('summaryLabel')).closest('div')
    expect(within(ongoingCard).getByText('1')).toBeVisible()
  })

  it('찜 더 보기 실패 시 기존 목록을 유지하고 실패한 페이지를 다시 요청한다', async () => {
    getMyLikes
      .mockResolvedValueOnce({ data: page([like(1, '기존 작품')], { last: false }) })
      .mockRejectedValueOnce({ response: { data: { message: '다음 페이지 실패' } } })
      .mockResolvedValueOnce({ data: page([like(2, '추가 작품')], { number: 1 }) })

    render(<MemoryRouter><Likes /></MemoryRouter>)

    expect(await screen.findByText('기존 작품')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: '더 보기' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('다음 페이지 실패')
    expect(screen.getByText('기존 작품')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByText('추가 작품')).toBeVisible()
    expect(screen.getAllByText('기존 작품')).toHaveLength(1)
    await waitFor(() => expect(getMyLikes).toHaveBeenNthCalledWith(3, {
      page: 1,
      size: 12,
      signal: expect.any(AbortSignal),
    }))
  })

  it('입찰 현황은 새 요청을 시작하면 이전 요청을 취소하고 최신 응답만 유지한다', async () => {
    const previousRequest = deferred()
    const currentRequest = deferred()
    getAllMyBids
      .mockReturnValueOnce(previousRequest.promise)
      .mockReturnValueOnce(currentRequest.promise)

    const view = render(
      <StrictMode>
        <MemoryRouter><BidStatus /></MemoryRouter>
      </StrictMode>,
    )

    await waitFor(() => expect(getAllMyBids).toHaveBeenCalledTimes(2))
    expect(getAllMyBids.mock.calls[0][0].signal.aborted).toBe(true)

    currentRequest.resolve([{
      artId: 2,
      artName: '최신 입찰 작품',
      auctionStatus: 'ONGOING',
      myBidPrice: 20_000,
      currentPrice: 20_000,
    }])
    expect(await screen.findByText('최신 입찰 작품')).toBeVisible()

    await act(async () => {
      previousRequest.resolve([{
        artId: 1,
        artName: '이전 입찰 작품',
        auctionStatus: 'ONGOING',
        myBidPrice: 10_000,
        currentPrice: 10_000,
      }])
      await previousRequest.promise
    })

    expect(screen.getByText('최신 입찰 작품')).toBeVisible()
    expect(screen.queryByText('이전 입찰 작품')).not.toBeInTheDocument()
    view.unmount()
  })

  it('입찰 현황은 활성 요청 중 화면을 이탈하면 요청을 취소한다', async () => {
    const activeRequest = deferred()
    getAllMyBids.mockReturnValue(activeRequest.promise)

    const view = render(<MemoryRouter><BidStatus /></MemoryRouter>)

    await waitFor(() => expect(getAllMyBids).toHaveBeenCalledTimes(1))
    const signal = getAllMyBids.mock.calls[0][0].signal
    expect(signal.aborted).toBe(false)

    view.unmount()
    expect(signal.aborted).toBe(true)
  })

  it('찜 목록은 새 요청을 시작하면 이전 요청을 취소하고 최신 응답만 유지한다', async () => {
    const previousRequest = deferred()
    const currentRequest = deferred()
    getMyLikes
      .mockReturnValueOnce(previousRequest.promise)
      .mockReturnValueOnce(currentRequest.promise)

    const view = render(
      <StrictMode>
        <MemoryRouter><Likes /></MemoryRouter>
      </StrictMode>,
    )

    await waitFor(() => expect(getMyLikes).toHaveBeenCalledTimes(2))
    expect(getMyLikes.mock.calls[0][0].signal.aborted).toBe(true)

    currentRequest.resolve({ data: page([like(2, '최신 찜 작품')]) })
    expect(await screen.findByText('최신 찜 작품')).toBeVisible()

    await act(async () => {
      previousRequest.resolve({ data: page([like(1, '이전 찜 작품')]) })
      await previousRequest.promise
    })

    expect(screen.getByText('최신 찜 작품')).toBeVisible()
    expect(screen.queryByText('이전 찜 작품')).not.toBeInTheDocument()
    view.unmount()
  })

  it('찜 목록은 활성 요청 중 화면을 이탈하면 요청을 취소한다', async () => {
    const activeRequest = deferred()
    getMyLikes.mockReturnValue(activeRequest.promise)

    const view = render(<MemoryRouter><Likes /></MemoryRouter>)

    await waitFor(() => expect(getMyLikes).toHaveBeenCalledTimes(1))
    const signal = getMyLikes.mock.calls[0][0].signal
    expect(signal.aborted).toBe(false)

    view.unmount()
    expect(signal.aborted).toBe(true)
  })
})
