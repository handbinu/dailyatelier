import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RegisterArtist from '../pages/auth/RegisterArtist'
import RegisterUser from '../pages/auth/RegisterUser'
import {
  checkNickname,
  checkUserId,
  registerArtist,
  registerUser,
} from '../api/authApi'

vi.mock('../api/authApi', () => ({
  checkNickname: vi.fn(),
  checkUserId: vi.fn(),
  registerArtist: vi.fn(),
  registerUser: vi.fn(),
}))

const cases = [
  {
    label: '일반 회원가입',
    Component: RegisterUser,
    submitName: '가입하기',
    nicknameLabel: '닉네임',
    register: registerUser,
  },
  {
    label: '작가 회원가입',
    Component: RegisterArtist,
    submitName: '작가로 가입하기',
    nicknameLabel: '활동명',
    register: registerArtist,
  },
]

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function renderRegister(component) {
  return render(
    <MemoryRouter>
      {createElement(component)}
    </MemoryRouter>,
  )
}

function duplicateButtons() {
  return screen.getAllByRole('button', { name: '중복확인' })
}

describe.each(cases)('$label 중복확인', ({ Component, submitName, nicknameLabel, register }) => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('이전 입력값의 늦은 응답을 현재 입력값에 적용하지 않는다', async () => {
    const firstRequest = deferred()
    checkUserId.mockReturnValue(firstRequest.promise)
    renderRegister(Component)

    const userIdInput = screen.getByLabelText(/^아이디/)
    fireEvent.change(userIdInput, { target: { value: 'first-id' } })
    fireEvent.click(duplicateButtons()[0])

    expect(screen.getByRole('button', { name: '확인 중...' })).toBeDisabled()
    expect(screen.getByRole('button', { name: submitName })).toBeDisabled()

    fireEvent.change(userIdInput, { target: { value: 'second-id' } })
    await act(async () => {
      firstRequest.resolve({ data: { duplicate: false } })
      await firstRequest.promise
    })

    expect(screen.queryByText('사용 가능합니다. ✓')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: submitName })).toBeDisabled()
  })

  it('응답 순서가 역전되어도 최신 요청 결과만 유지한다', async () => {
    const firstRequest = deferred()
    const secondRequest = deferred()
    checkUserId
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise)
    renderRegister(Component)

    const userIdInput = screen.getByLabelText(/^아이디/)
    fireEvent.change(userIdInput, { target: { value: 'first-id' } })
    fireEvent.click(duplicateButtons()[0])
    fireEvent.change(userIdInput, { target: { value: 'second-id' } })
    fireEvent.click(duplicateButtons()[0])

    await act(async () => {
      secondRequest.resolve({ data: { duplicate: false } })
      await secondRequest.promise
    })
    expect(screen.getByText('사용 가능합니다. ✓')).toBeInTheDocument()

    await act(async () => {
      firstRequest.resolve({ data: { duplicate: true } })
      await firstRequest.promise
    })
    expect(screen.getByText('사용 가능합니다. ✓')).toBeInTheDocument()
    expect(screen.queryByText('이미 사용 중입니다')).not.toBeInTheDocument()
  })

  it('요청 실패와 서버 중복 응답에서는 미확인 상태를 유지한다', async () => {
    checkUserId
      .mockRejectedValueOnce(new Error('network error'))
      .mockResolvedValueOnce({ data: { duplicate: false } })
    checkNickname.mockResolvedValue({ data: { duplicate: true } })
    renderRegister(Component)

    fireEvent.change(screen.getByLabelText(/^아이디/), {
      target: { value: 'user-id' },
    })
    fireEvent.click(duplicateButtons()[0])
    expect(await screen.findByText('확인 중 오류가 발생했습니다.')).toBeInTheDocument()

    fireEvent.click(duplicateButtons()[0])
    expect(await screen.findByText('사용 가능합니다. ✓')).toBeInTheDocument()

    const nicknameInput = screen.getByRole('textbox', { name: new RegExp(nicknameLabel) })
    fireEvent.change(nicknameInput, { target: { value: 'duplicate-name' } })
    fireEvent.click(duplicateButtons()[1])
    expect(await screen.findByText('이미 사용 중입니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: submitName })).toBeDisabled()
  })

  it('현재 아이디와 닉네임을 확인한 경우에만 제출한다', async () => {
    checkUserId.mockResolvedValue({ data: { duplicate: false } })
    checkNickname.mockResolvedValue({ data: { duplicate: false } })
    register.mockResolvedValue({ data: {} })
    renderRegister(Component)

    fireEvent.change(screen.getByLabelText(/^아이디/), {
      target: { value: 'verified-user' },
    })
    fireEvent.click(duplicateButtons()[0])
    await screen.findByText('사용 가능합니다. ✓')

    fireEvent.change(screen.getByRole('textbox', { name: new RegExp(nicknameLabel) }), {
      target: { value: 'verified-name' },
    })
    fireEvent.click(duplicateButtons()[1])
    await waitFor(() => expect(screen.getAllByText('사용 가능합니다. ✓')).toHaveLength(2))

    fireEvent.change(screen.getByLabelText(/^비밀번호 \*/), {
      target: { name: 'password', value: 'Password1!' },
    })
    fireEvent.change(screen.getByLabelText(/비밀번호 재입력/), {
      target: { value: 'Password1!' },
    })

    const submit = screen.getByRole('button', { name: submitName })
    expect(submit).toBeEnabled()
    fireEvent.submit(submit.closest('form'))

    await waitFor(() => expect(register).toHaveBeenCalledTimes(1))
    expect(register).toHaveBeenCalledWith(expect.objectContaining({
      userId: 'verified-user',
      nickname: 'verified-name',
    }))
  })
})
