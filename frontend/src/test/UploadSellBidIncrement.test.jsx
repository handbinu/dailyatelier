import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import api from '../api/authApi'
import { createArt } from '../api/artApi'
import UploadSell from '../pages/MyPage/UploadSell'

vi.mock('../api/authApi', () => ({ default: { post: vi.fn() } }))
vi.mock('../api/artApi', () => ({ createArt: vi.fn() }))

const renderPage = () => render(<MemoryRouter><UploadSell /></MemoryRouter>)

const submit = () => fireEvent.submit(screen.getByRole('button', { name: '작품 등록하기' }).closest('form'))

describe('판매 등록 최소 입찰 증분', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('token', 'token')
    localStorage.setItem('userStatus', '1')
    vi.stubGlobal('alert', vi.fn())
    vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:preview'), revokeObjectURL: vi.fn() })
  })

  it('기본값, 입력 속성과 상시 도움말을 표시하고 첫 입찰가를 즉시 계산한다', () => {
    renderPage()
    const increment = screen.getByLabelText('최소 입찰 증분 *')

    expect(increment).toHaveValue(1000)
    expect(increment).toHaveAttribute('min', '100')
    expect(increment).toHaveAttribute('max', '10000000')
    expect(increment).toHaveAttribute('step', '100')
    expect(screen.getByText('다음 입찰자는 현재가보다 최소 이 금액만큼 높게 입찰해야 합니다.')).toBeVisible()
    expect(screen.getByText('100원 단위 · 기본 1,000원')).toBeVisible()

    fireEvent.change(screen.getByPlaceholderText('0'), { target: { value: '30000' } })
    expect(screen.getByText('첫 입찰 가능 금액은 31,000원부터입니다.')).toBeVisible()
    fireEvent.change(increment, { target: { value: '2000' } })
    expect(screen.getByText('첫 입찰 가능 금액은 32,000원부터입니다.')).toBeVisible()
  })

  it.each([
    ['99', '최소 입찰 증분은 100원 이상 1,000만원 이하로 입력해 주세요.'],
    ['150', '최소 입찰 증분은 100원 단위로 입력해 주세요.'],
    ['10000100', '최소 입찰 증분은 100원 이상 1,000만원 이하로 입력해 주세요.'],
  ])('%s원 증분을 클라이언트에서 거절한다', (value, message) => {
    renderPage()
    fireEvent.change(screen.getByLabelText('최소 입찰 증분 *'), { target: { value } })
    submit()
    expect(screen.getByText(message)).toBeInTheDocument()
    expect(createArt).not.toHaveBeenCalled()
  })

  it.each(['100', '10000000'])('%s원 증분의 자체 검증을 통과한다', (value) => {
    renderPage()
    fireEvent.change(screen.getByLabelText('최소 입찰 증분 *'), { target: { value } })
    submit()
    expect(screen.queryByText(/최소 입찰 증분은 .*입력해 주세요/)).not.toBeInTheDocument()
  })

  it('시작가 상한과 시작가·증분 합계 상한을 등록 전에 거절한다', () => {
    renderPage()
    const startPrice = screen.getByPlaceholderText('0')

    fireEvent.change(startPrice, { target: { value: '2100000001' } })
    submit()
    expect(screen.getByText('시작가격은 1원 이상 21억 원 이하의 정수로 입력해 주세요.')).toBeInTheDocument()

    fireEvent.change(startPrice, { target: { value: '2099999500' } })
    submit()
    expect(screen.getByText('시작가와 최소 입찰 증분의 합은 21억 원 이하여야 합니다.')).toBeInTheDocument()
  })

  it('유효한 요청에 숫자형 증분을 포함하고 완료 화면에 표시한다', async () => {
    api.post.mockResolvedValue({ data: { apiKey: 'key', timestamp: 1, signature: 'sig', folder: 'arts', uploadUrl: '/upload' } })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ secure_url: '/art.jpg' }) }))
    createArt.mockResolvedValue({ data: { artId: 1, name: '새 작품', currentPrice: 30000, minimumBidIncrement: 1000 } })
    const { container } = renderPage()

    fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [new File(['image'], 'art.png', { type: 'image/png' })] } })
    fireEvent.change(screen.getByLabelText('작품명 *'), { target: { value: '새 작품' } })
    fireEvent.change(screen.getByLabelText('작품 형태 *'), { target: { value: 'DIGITAL' } })
    fireEvent.change(screen.getByLabelText('카테고리 *'), { target: { value: 'DIGITAL_ART' } })
    fireEvent.change(screen.getByLabelText('재료·기법 *'), { target: { value: '디지털' } })
    fireEvent.change(screen.getByPlaceholderText('0'), { target: { value: '30000' } })
    const [bidStartTime, closingTime] = container.querySelectorAll('input[type="datetime-local"]')
    fireEvent.change(bidStartTime, { target: { value: '2099-01-01T10:00' } })
    fireEvent.change(closingTime, { target: { value: '2099-01-02T10:00' } })
    submit()

    await waitFor(() => expect(createArt).toHaveBeenCalledWith(expect.objectContaining({ minimumBidIncrement: 1000 })))
    expect(await screen.findByText('최소 입찰 증분 1,000원')).toBeVisible()
  })
})
