import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InquiryList from '../pages/MyPage/InquiryList'
import InquiryWrite from '../pages/MyPage/InquiryWrite'
import AdminInquiry from '../pages/MyPage/AdminInquiry'
import {
  answerInquiry,
  createInquiry,
  getAdminInquiries,
  getInquiryDetail,
  getMyInquiries,
} from '../api/inquiryApi'

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

const pendingInquiry = {
  ...inquiry,
  answered: false,
  answeredAt: null,
}

const pendingDetail = {
  ...detail,
  answered: false,
  answeredAt: null,
  answer: null,
}

const otherInquiry = {
  ...pendingInquiry,
  inquiryId: 5,
  title: '포인트 문의',
  inquiryType: 'POINT',
}

const otherDetail = {
  ...pendingDetail,
  ...otherInquiry,
  content: '포인트 사용 내역이 궁금합니다.',
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
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
    expect(getInquiryDetail).toHaveBeenCalledWith(4, {
      signal: expect.any(AbortSignal),
    })
  })

  it('내 문의에서 A의 늦은 응답이 현재 펼친 B의 상세 상태를 덮지 않는다', async () => {
    getMyInquiries.mockResolvedValue({ data: { content: [pendingInquiry, otherInquiry] } })
    const firstRequest = deferred()
    const secondRequest = deferred()
    getInquiryDetail
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise)

    render(<MemoryRouter><InquiryList /></MemoryRouter>)

    fireEvent.click(await screen.findByRole('button', { name: /배송 문의/ }))
    const firstSignal = getInquiryDetail.mock.calls[0][1].signal
    fireEvent.click(screen.getByRole('button', { name: /포인트 문의/ }))

    expect(firstSignal.aborted).toBe(true)
    secondRequest.resolve({ data: otherDetail })
    expect(await screen.findByText('포인트 사용 내역이 궁금합니다.')).toBeVisible()

    firstRequest.resolve({ data: pendingDetail })
    await waitFor(() => expect(screen.queryByText('배송 예정일이 궁금합니다.')).not.toBeInTheDocument())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByText('문의 상세 내용을 불러오는 중입니다.')).not.toBeInTheDocument()
  })

  it('내 문의에서 접기와 캐시된 문의 열기가 진행 중인 상세 요청을 무효화한다', async () => {
    getMyInquiries.mockResolvedValue({ data: { content: [pendingInquiry, otherInquiry] } })
    const firstRequest = deferred()
    getInquiryDetail
      .mockResolvedValueOnce({ data: otherDetail })
      .mockReturnValueOnce(firstRequest.promise)

    render(<MemoryRouter><InquiryList /></MemoryRouter>)

    fireEvent.click(await screen.findByRole('button', { name: /포인트 문의/ }))
    expect(await screen.findByText('포인트 사용 내역이 궁금합니다.')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: /포인트 문의/ }))

    fireEvent.click(screen.getByRole('button', { name: /배송 문의/ }))
    const firstSignal = getInquiryDetail.mock.calls[1][1].signal
    fireEvent.click(screen.getByRole('button', { name: /포인트 문의/ }))

    expect(firstSignal.aborted).toBe(true)
    expect(screen.getByText('포인트 사용 내역이 궁금합니다.')).toBeVisible()
    firstRequest.reject({ response: { data: { message: '늦은 상세 오류' } } })
    await waitFor(() => expect(screen.queryByText('늦은 상세 오류')).not.toBeInTheDocument())
    expect(screen.queryByText('문의 상세 내용을 불러오는 중입니다.')).not.toBeInTheDocument()
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

  it('관리자 답변 중 대상과 입력 변경 및 같은 렌더의 연속 제출을 막는다', async () => {
    getAdminInquiries.mockResolvedValue({ data: { content: [pendingInquiry] } })
    getInquiryDetail.mockResolvedValue({ data: pendingDetail })
    const answerRequest = deferred()
    answerInquiry.mockReturnValue(answerRequest.promise)

    render(<MemoryRouter><AdminInquiry /></MemoryRouter>)

    fireEvent.click(await screen.findByRole('button', { name: /배송 문의/ }))
    const textarea = await screen.findByLabelText('답변')
    fireEvent.change(textarea, { target: { value: '내일 출고 예정입니다.' } })
    const submit = screen.getByRole('button', { name: '답변 등록' })
    const form = submit.closest('form')
    fireEvent.submit(form)
    fireEvent.submit(form)

    expect(answerInquiry).toHaveBeenCalledTimes(1)
    expect(answerInquiry).toHaveBeenCalledWith(4, '내일 출고 예정입니다.')
    expect(screen.getByRole('button', { name: /배송 문의/ })).toBeDisabled()
    expect(textarea).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '대기 중' }))
    expect(getAdminInquiries).toHaveBeenCalledTimes(1)

    answerRequest.resolve({ data: detail })

    expect(await screen.findByText('답변을 등록했습니다.')).toBeVisible()
    expect(screen.getByText('내일 출고 예정입니다.')).toBeVisible()
    expect(getAdminInquiries).toHaveBeenLastCalledWith({ status: 'ALL', size: 50 })
  })

  it('관리자 답변 성공 후 목록 재조회 실패를 분리하고 다시 조회한다', async () => {
    getAdminInquiries
      .mockResolvedValueOnce({ data: { content: [pendingInquiry] } })
      .mockRejectedValueOnce({ response: { data: { message: '목록 서버 오류' } } })
      .mockResolvedValueOnce({ data: { content: [inquiry] } })
    getInquiryDetail.mockResolvedValue({ data: pendingDetail })
    answerInquiry.mockResolvedValue({ data: detail })

    render(<MemoryRouter><AdminInquiry /></MemoryRouter>)

    fireEvent.click(await screen.findByRole('button', { name: /배송 문의/ }))
    fireEvent.change(await screen.findByLabelText('답변'), {
      target: { value: '내일 출고 예정입니다.' },
    })
    fireEvent.click(screen.getByRole('button', { name: '답변 등록' }))

    expect(await screen.findByText('답변을 등록했습니다.')).toBeVisible()
    expect(await screen.findByRole('alert')).toHaveTextContent('답변은 등록됐지만 문의 목록을 갱신하지 못했습니다.')
    expect(screen.queryByText('답변 등록에 실패했습니다.')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '목록 다시 조회' }))

    await waitFor(() => expect(getAdminInquiries).toHaveBeenCalledTimes(3))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
    expect(answerInquiry).toHaveBeenCalledTimes(1)
  })

  it('관리자 문의 A의 늦은 성공이 B의 상세와 답변 입력값을 덮지 않는다', async () => {
    getAdminInquiries.mockResolvedValue({ data: { content: [pendingInquiry, otherInquiry] } })
    const firstRequest = deferred()
    const secondRequest = deferred()
    getInquiryDetail
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise)

    render(<MemoryRouter><AdminInquiry /></MemoryRouter>)

    fireEvent.click(await screen.findByRole('button', { name: /배송 문의/ }))
    const firstSignal = getInquiryDetail.mock.calls[0][1].signal
    fireEvent.click(screen.getByRole('button', { name: /포인트 문의/ }))

    expect(firstSignal.aborted).toBe(true)
    secondRequest.resolve({ data: otherDetail })
    expect(await screen.findByText('포인트 사용 내역이 궁금합니다.')).toBeVisible()
    expect(screen.getByLabelText('답변')).toHaveValue('')

    firstRequest.resolve({ data: { ...pendingDetail, answer: '늦은 답변' } })
    await waitFor(() => expect(screen.queryByText('배송 예정일이 궁금합니다.')).not.toBeInTheDocument())
    expect(screen.getByLabelText('답변')).toHaveValue('')
    expect(screen.queryByText('문의 상세 내용을 불러오는 중입니다.')).not.toBeInTheDocument()
  })

  it('관리자 문의 A의 늦은 실패를 현재 B의 오류로 표시하지 않는다', async () => {
    getAdminInquiries.mockResolvedValue({ data: { content: [pendingInquiry, otherInquiry] } })
    const firstRequest = deferred()
    getInquiryDetail
      .mockReturnValueOnce(firstRequest.promise)
      .mockResolvedValueOnce({ data: otherDetail })

    render(<MemoryRouter><AdminInquiry /></MemoryRouter>)

    fireEvent.click(await screen.findByRole('button', { name: /배송 문의/ }))
    fireEvent.click(screen.getByRole('button', { name: /포인트 문의/ }))
    expect(await screen.findByText('포인트 사용 내역이 궁금합니다.')).toBeVisible()

    firstRequest.reject({ response: { data: { message: '늦은 관리자 오류' } } })
    await waitFor(() => expect(screen.queryByText('늦은 관리자 오류')).not.toBeInTheDocument())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByText('문의 상세 내용을 불러오는 중입니다.')).not.toBeInTheDocument()
  })

  it('관리자 문의 상세 요청은 언마운트 시 취소된다', async () => {
    getAdminInquiries.mockResolvedValue({ data: { content: [pendingInquiry] } })
    const request = deferred()
    getInquiryDetail.mockReturnValue(request.promise)

    const view = render(<MemoryRouter><AdminInquiry /></MemoryRouter>)

    fireEvent.click(await screen.findByRole('button', { name: /배송 문의/ }))
    const signal = getInquiryDetail.mock.calls[0][1].signal
    view.unmount()

    expect(signal.aborted).toBe(true)
    request.resolve({ data: pendingDetail })
  })
})
