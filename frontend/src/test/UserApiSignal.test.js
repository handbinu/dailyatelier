import { beforeEach, describe, expect, it, vi } from 'vitest'
import api from '../api/authApi'
import { getAllMyBids, getMyBids, getMyLikes } from '../api/userApi'

vi.mock('../api/authApi', () => ({
  default: {
    get: vi.fn(),
  },
}))

describe('마이페이지 목록 API 요청 취소 signal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('찜과 입찰 목록 조회가 전달받은 signal을 Axios에 전달한다', async () => {
    const signal = new AbortController().signal
    api.get.mockResolvedValue({ data: { content: [], totalPages: 1 } })

    await getMyLikes({ page: 2, size: 12, signal })
    await getMyBids({ page: 3, size: 50, signal })

    expect(api.get).toHaveBeenNthCalledWith(1, '/api/users/me/likes', {
      params: { page: 2, size: 12 },
      signal,
    })
    expect(api.get).toHaveBeenNthCalledWith(2, '/api/users/me/bids', {
      params: { page: 3, size: 50 },
      signal,
    })
  })

  it('전체 입찰 조회가 모든 페이지 요청에 동일한 signal을 전달한다', async () => {
    const signal = new AbortController().signal
    api.get
      .mockResolvedValueOnce({
        data: { content: [{ artId: 1 }], totalPages: 3 },
      })
      .mockResolvedValueOnce({
        data: { content: [{ artId: 2 }], totalPages: 3 },
      })
      .mockResolvedValueOnce({
        data: { content: [{ artId: 3 }], totalPages: 3 },
      })

    await expect(getAllMyBids({ signal })).resolves.toEqual([
      { artId: 1 },
      { artId: 2 },
      { artId: 3 },
    ])

    expect(api.get).toHaveBeenCalledTimes(3)
    for (const [index, call] of api.get.mock.calls.entries()) {
      expect(call).toEqual([
        '/api/users/me/bids',
        { params: { page: index, size: 50 }, signal },
      ])
    }
  })
})
