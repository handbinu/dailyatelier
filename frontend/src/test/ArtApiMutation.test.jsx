import { beforeEach, describe, expect, it, vi } from 'vitest'
import api from '../api/authApi'
import { deleteArt, updateArt } from '../api/artApi'

vi.mock('../api/authApi', () => ({
  default: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))

describe('작품 수정·삭제 API', () => {
  beforeEach(() => vi.clearAllMocks())

  it('작품 ID 경로로 변경 필드만 PATCH한다', () => {
    const payload = { descript: '수정 설명', material: '캔버스' }
    updateArt(17, payload)
    expect(api.patch).toHaveBeenCalledWith('/api/arts/17', payload)
  })

  it('작품 ID 경로로 DELETE한다', () => {
    deleteArt(17)
    expect(api.delete).toHaveBeenCalledWith('/api/arts/17')
  })
})
