import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InquiryList from '../pages/MyPage/InquiryList'
import InquiryWrite from '../pages/MyPage/InquiryWrite'
import { createInquiry, getInquiryDetail, getMyInquiries } from '../api/inquiryApi'

vi.mock('../api/inquiryApi', () => ({
  createInquiry: vi.fn(),
  getInquiryDetail: vi.fn(),
  getMyInquiries: vi.fn(),
  getAdminInquiries: vi.fn(),
  answerInquiry: vi.fn(),
}))

const inquiry = {
  inquiryId: 4,
  inquiryType: 'DELIVERY',
  title: '배송 문의',
  answered: true,
  createdAt: '2026-08-14T09:00:00',
  answeredAt: '2026-08-14T10:00:00',
}

const detail = {
  ...inquiry,
  content: '배송 예정일이 궁금합니다.',
  emailAlert: true,
  attachmentUrl: null,
  attachmentName: null,
  attachmentResourceType: null,
  answer: '내일 출고 예정입니다.',
}

function LocationView() {
  const location = useLocation()
  return <output>{location.pathname}</output>
}

describe('문의 실제 연동', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('목록에서 상세 API의 문의와 답변을 표시한다', async () => {
    getMyInquiries.mockResolvedValue({ data: { content: [inquiry] } })
    getInquiryDetail.mockResolvedValue({ data: detail })
    render(<MemoryRouter><InquiryList /></MemoryRouter>)

    fireEvent.click(await screen.findByRole('button', { name: /배송 문의/ }))

    expect(await screen.findByText('배송 예정일이 궁금합니다.')).toBeVisible()
    expect(screen.getByText('내일 출고 예정입니다.')).toBeVisible()
    expect(getInquiryDetail).toHaveBeenCalledWith(4)
  })

  it('등록 성공 후 가짜 완료 화면 대신 문의 목록으로 이동한다', async () => {
    createInquiry.mockResolvedValue({ data: detail })
    render(
      <MemoryRouter initialEntries={['/mypage/inquiry/write']}>
        <Routes>
          <Route path="/mypage/inquiry/write" element={<InquiryWrite />} />
          <Route path="/mypage/inquiry" element={<LocationView />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText(/제목/), { target: { value: '배송 문의' } })
    fireEvent.change(screen.getByLabelText(/내용/), { target: { value: '배송 일정이 궁금합니다.' } })
    fireEvent.click(screen.getByRole('button', { name: '문의 등록' }))

    await waitFor(() => expect(createInquiry).toHaveBeenCalledWith(expect.objectContaining({
      inquiryType: 'DELIVERY',
      title: '배송 문의',
      content: '배송 일정이 궁금합니다.',
    })))
    expect(await screen.findByText('/mypage/inquiry')).toBeVisible()
  })

  it('등록 실패 시 입력한 내용을 유지하고 서버 오류를 표시한다', async () => {
    createInquiry.mockRejectedValue({ response: { data: { message: '첨부 파일 형식이 올바르지 않습니다.' } } })
    render(<MemoryRouter><InquiryWrite /></MemoryRouter>)

    fireEvent.change(screen.getByLabelText(/제목/), { target: { value: '배송 문의' } })
    fireEvent.change(screen.getByLabelText(/내용/), { target: { value: '배송 일정이 궁금합니다.' } })
    fireEvent.click(screen.getByRole('button', { name: '문의 등록' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('첨부 파일 형식이 올바르지 않습니다.')
    expect(screen.getByLabelText(/제목/)).toHaveValue('배송 문의')
  })
})
