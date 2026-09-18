import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import api from '../api/authApi'
import { deleteArt, getArt, updateArt } from '../api/artApi'
import EditArt from '../pages/MyPage/EditArt'

vi.mock('../api/authApi', () => ({ default: { post: vi.fn() } }))
vi.mock('../api/artApi', () => ({ deleteArt: vi.fn(), getArt: vi.fn(), updateArt: vi.fn() }))

const baseArt = {
  artId: 7, name: '여름의 정원', descript: '설명', material: '캔버스', wIntro: '소개',
  format: 'PHYSICAL', category: 'OTHER', startPrice: 100000, currentPrice: 100000,
  minimumBidIncrement: 1000, bidStartTime: '2098-07-01T10:00:00',
  closingTime: '2099-07-31T18:00:00', imgPath: '/art.jpg', artStatus: 0, isOwner: true,
}

function Destination() {
  const location = useLocation()
  return <output>{location.pathname}{location.search}|{location.state?.feedback}</output>
}

const renderPage = () => render(
  <MemoryRouter initialEntries={[{
    pathname: '/mypage/manage-arts/7/edit',
    state: { from: '/mypage/manage-arts?state=ACTIVE&page=2' },
  }]}>
    <Routes>
      <Route path="/mypage/manage-arts/:artId/edit" element={<EditArt />} />
      <Route path="/mypage/manage-arts" element={<Destination />} />
    </Routes>
  </MemoryRouter>,
)

describe('작품 편집·삭제 화면', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getArt.mockResolvedValue({ data: baseArt })
    updateArt.mockResolvedValue({ data: baseArt })
    deleteArt.mockResolvedValue({ data: { artId: 7, action: 'DELETED', artStatus: null } })
    api.post.mockResolvedValue({ data: {
      apiKey: 'key', timestamp: 1, signature: 'signature',
      folder: 'arts/artist1', uploadUrl: '/cloudinary/upload',
    } })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        secure_url: 'https://res.cloudinary.com/test/image/upload/v1/arts/artist1/new-art.jpg',
        public_id: 'arts/artist1/new-art',
      }),
    }))
    vi.stubGlobal('confirm', vi.fn(() => true))
    vi.stubGlobal('scrollTo', vi.fn())
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:preview'),
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    })
  })

  it('상세 초기값과 수정할 수 없는 작품명을 표시한다', async () => {
    renderPage()
    expect(await screen.findByLabelText('작품명')).toHaveValue('여름의 정원')
    expect(screen.getByLabelText('작품명')).toHaveAttribute('readonly')
    expect(screen.getByLabelText('작품 형태 *')).toHaveValue('PHYSICAL')
    expect(screen.getByLabelText('카테고리 *')).toHaveValue('OTHER')
  })

  it('입찰 전 변경을 저장하고 기존 관리 목록 위치로 복귀한다', async () => {
    renderPage()
    fireEvent.change(await screen.findByLabelText('작품 설명'), { target: { value: '새 설명' } })
    fireEvent.click(screen.getByRole('button', { name: '수정 저장' }))

    await waitFor(() => expect(updateArt).toHaveBeenCalledWith('7', expect.objectContaining({
      descript: '새 설명', format: 'PHYSICAL', category: 'OTHER', startPrice: 100000,
      minimumBidIncrement: 1000,
    })))
    expect(updateArt.mock.calls[0][1]).not.toHaveProperty('imgPath')
    expect(updateArt.mock.calls[0][1]).not.toHaveProperty('publicId')
    expect(await screen.findByText('/mypage/manage-arts?state=ACTIVE&page=2|작품 정보를 수정했습니다.')).toBeVisible()
  })

  it('새 이미지의 URL과 publicId를 함께 전송한다', async () => {
    const view = renderPage()
    await screen.findByRole('button', { name: '수정 저장' })
    const file = new File(['image'], 'new-art.png', { type: 'image/png' })
    fireEvent.change(view.container.querySelector('input[type="file"]'), {
      target: { files: [file] },
    })
    fireEvent.click(await screen.findByRole('button', { name: '수정 저장' }))

    await waitFor(() => expect(updateArt).toHaveBeenCalledWith('7', expect.objectContaining({
      imgPath: 'https://res.cloudinary.com/test/image/upload/v1/arts/artist1/new-art.jpg',
      publicId: 'arts/artist1/new-art',
    })))
  })

  it('입찰 후 가격·기간과 증분을 잠그고 비가격 필드만 전송한다', async () => {
    getArt.mockResolvedValue({ data: { ...baseArt, currentPrice: 110000 } })
    renderPage()
    expect(await screen.findByLabelText('시작가 *')).toBeDisabled()
    expect(screen.getByLabelText('최소 입찰 증분 *')).toBeDisabled()
    expect(screen.getByLabelText('입찰 시작 시간 *')).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '수정 저장' }))

    await waitFor(() => expect(updateArt).toHaveBeenCalled())
    const payload = updateArt.mock.calls[0][1]
    expect(payload).not.toHaveProperty('startPrice')
    expect(payload).not.toHaveProperty('minimumBidIncrement')
    expect(payload).not.toHaveProperty('bidStartTime')
    expect(payload).not.toHaveProperty('closingTime')
  })

  it('삭제 확인을 거치고 처리 중 중복 요청을 막은 뒤 복귀한다', async () => {
    let resolveDelete
    deleteArt.mockReturnValue(new Promise((resolve) => { resolveDelete = resolve }))
    renderPage()
    const button = await screen.findByRole('button', { name: '작품 삭제' })
    fireEvent.click(button)
    fireEvent.click(button)
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(deleteArt).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: '삭제 중...' })).toBeDisabled()
    resolveDelete({ data: { artId: 7, action: 'DELETED' } })
    expect(await screen.findByText('/mypage/manage-arts?state=ACTIVE&page=2|작품을 삭제했습니다.')).toBeVisible()
  })

  it('409 충돌 시 최신 상세를 다시 읽고 서버 안내를 표시한다', async () => {
    updateArt.mockRejectedValue({ response: { status: 409, data: { code: 'ART_STATUS_CONFLICT' } } })
    getArt.mockResolvedValueOnce({ data: baseArt }).mockResolvedValueOnce({ data: { ...baseArt, currentPrice: 120000 } })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '수정 저장' }))

    expect(await screen.findByText('작품 상태가 변경되어 최신 정보를 다시 불러왔습니다.')).toBeVisible()
    expect(getArt).toHaveBeenCalledTimes(2)
    expect(screen.getByLabelText('시작가 *')).toBeDisabled()
  })

  it('기본 API 오류 메시지를 화면 안에 표시한다', async () => {
    getArt.mockRejectedValue({ response: { status: 403 } })
    renderPage()
    expect(await screen.findByText('이 작품을 수정하거나 삭제할 권한이 없습니다.')).toBeVisible()
  })
})
