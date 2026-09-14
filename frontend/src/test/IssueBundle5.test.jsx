import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
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
    await waitFor(() => expect(getMyLikes).toHaveBeenNthCalledWith(3, { page: 1, size: 12 }))
  })
})
