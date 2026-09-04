import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ProfileEdit from '../pages/MyPage/ProfileEdit'
import { getUserProfile, updateUserProfile, updateUserProfileImage } from '../api/userApi'

vi.mock('../api/userApi', () => ({
  getUserProfile: vi.fn(),
  updateUserProfile: vi.fn(),
  updateUserProfileImage: vi.fn(),
  checkNickname: vi.fn(),
}))

const profile = {
  userId: 'member',
  name: '회원',
  nickname: '테스트',
  phoneNumber: '010-0000-0000',
  email: 'member@example.com',
  emailAgree: true,
  userStatus: 0,
  profileImageUrl: 'https://res.cloudinary.com/test/old-profile.png',
}

function renderProfileEdit() {
  return render(
    <MemoryRouter>
      <ProfileEdit />
      <LocationProbe />
    </MemoryRouter>,
  )
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}</output>
}

describe('프로필 사진 변경', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('token', 'test-token')
    getUserProfile.mockResolvedValue({ data: profile })
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:profile-preview'),
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    })
  })

  it('저장된 이미지와 로딩 실패 시 이니셜 fallback을 표시한다', async () => {
    renderProfileEdit()

    const image = await screen.findByRole('img', { name: '테스트 프로필' })
    expect(image).toHaveAttribute('src', profile.profileImageUrl)

    fireEvent.error(image)

    expect(screen.queryByRole('img', { name: '테스트 프로필' })).not.toBeInTheDocument()
    expect(screen.getByText('테')).toBeVisible()
  })

  it('정상 이미지를 미리보기하고 한 번만 저장한 뒤 응답 URL을 반영한다', async () => {
    const uploadedProfile = {
      ...profile,
      profileImageUrl: 'https://res.cloudinary.com/test/new-profile.png',
    }
    let resolveUpload
    updateUserProfileImage.mockReturnValue(new Promise((resolve) => { resolveUpload = resolve }))
    renderProfileEdit()
    const input = await screen.findByLabelText('사진 선택')
    const image = new File(['image'], 'profile.png', { type: 'image/png' })

    fireEvent.change(input, { target: { files: [image] } })

    expect(URL.createObjectURL).toHaveBeenCalledWith(image)
    expect(screen.getByRole('img', { name: '테스트 프로필' })).toHaveAttribute('src', 'blob:profile-preview')

    const saveButton = screen.getByRole('button', { name: '사진 저장' })
    fireEvent.click(saveButton)
    fireEvent.click(saveButton)
    expect(updateUserProfileImage).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: '사진 저장 중…' })).toBeDisabled()

    resolveUpload({ data: uploadedProfile })

    expect(await screen.findByText('프로필 사진이 저장되었습니다.')).toBeVisible()
    expect(screen.getByRole('img', { name: '테스트 프로필' }))
      .toHaveAttribute('src', uploadedProfile.profileImageUrl)
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:profile-preview'))
  })

  it('지원하지 않는 파일 형식을 업로드하지 않는다', async () => {
    renderProfileEdit()
    const input = await screen.findByLabelText('사진 선택')
    const image = new File(['image'], 'profile.webp', { type: 'image/webp' })

    fireEvent.change(input, { target: { files: [image] } })

    expect(screen.getByRole('alert')).toHaveTextContent('JPG, PNG 이미지만 업로드할 수 있습니다.')
    expect(screen.getByRole('button', { name: '사진 저장' })).toBeDisabled()
    expect(updateUserProfileImage).not.toHaveBeenCalled()
  })

  it('5MB 초과 파일을 업로드하지 않는다', async () => {
    renderProfileEdit()
    const input = await screen.findByLabelText('사진 선택')
    const image = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'profile.png', { type: 'image/png' })

    fireEvent.change(input, { target: { files: [image] } })

    expect(screen.getByRole('alert')).toHaveTextContent('프로필 이미지는 5MB 이하여야 합니다.')
    expect(screen.getByRole('button', { name: '사진 저장' })).toBeDisabled()
    expect(updateUserProfileImage).not.toHaveBeenCalled()
  })

  it('업로드 실패 메시지를 표시하고 선택한 이미지로 재시도할 수 있다', async () => {
    updateUserProfileImage.mockRejectedValue({
      response: { data: { message: 'Cloudinary 서비스를 사용할 수 없습니다.' } },
    })
    renderProfileEdit()
    const input = await screen.findByLabelText('사진 선택')
    const image = new File(['image'], 'profile.jpg', { type: 'image/jpeg' })
    fireEvent.change(input, { target: { files: [image] } })

    fireEvent.click(screen.getByRole('button', { name: '사진 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Cloudinary 서비스를 사용할 수 없습니다.')
    expect(screen.getByRole('button', { name: '사진 저장' })).toBeEnabled()
    expect(screen.getByRole('img', { name: '테스트 프로필' })).toHaveAttribute('src', 'blob:profile-preview')
  })
})

describe('프로필 주소 검색', () => {
  let postcodeOptions

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('token', 'test-token')
    getUserProfile.mockResolvedValue({
      data: {
        ...profile,
        zipCode: '01234',
        userAddress1: '기존 기본주소',
        userAddress2: '기존 상세주소',
      },
    })
    window.kakao = {
      Postcode: vi.fn(function Postcode(options) {
        postcodeOptions = options
        this.embed = vi.fn()
      }),
    }
  })

  afterEach(() => {
    delete window.kakao
    document.querySelectorAll('script[src*="postcode.v2.js"]').forEach((script) => script.remove())
  })

  it('선택한 주소의 문자열 우편번호와 기본주소를 반영하고 상세주소를 비운다', async () => {
    renderProfileEdit()
    fireEvent.click(await screen.findByRole('button', { name: '우편번호 찾기' }))
    await waitFor(() => expect(window.kakao.Postcode).toHaveBeenCalledTimes(1))

    postcodeOptions.oncomplete({ zonecode: '02535', address: '서울특별시 중구 세종대로 110' })

    await waitFor(() => expect(screen.getByPlaceholderText('우편번호')).toHaveValue('02535'))
    expect(screen.getByPlaceholderText('기본 주소')).toHaveValue('서울특별시 중구 세종대로 110')
    expect(screen.getByPlaceholderText('상세 주소')).toHaveValue('')
    await waitFor(() => expect(screen.getByPlaceholderText('상세 주소')).toHaveFocus())
  })

  it('취소와 외부 스크립트 실패 시 기존 주소를 보존한다', async () => {
    delete window.kakao
    renderProfileEdit()
    const openButton = await screen.findByRole('button', { name: '우편번호 찾기' })
    openButton.focus()
    fireEvent.click(openButton)
    fireEvent.error(document.querySelector('script[src*="postcode.v2.js"]'))

    expect(await screen.findByRole('alert')).toHaveTextContent('주소 검색 서비스를 불러오지 못했습니다.')
    expect(screen.getByPlaceholderText('우편번호')).toHaveValue('01234')
    expect(screen.getByPlaceholderText('기본 주소')).toHaveValue('기존 기본주소')
    expect(screen.getByPlaceholderText('상세 주소')).toHaveValue('기존 상세주소')

    fireEvent.click(screen.getByRole('button', { name: '닫기' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(openButton).toHaveFocus())
  })

  it('선택된 주소를 기존 프로필 수정 요청 계약으로 저장한다', async () => {
    updateUserProfile.mockResolvedValue({ data: { message: 'ok' } })
    renderProfileEdit()
    fireEvent.click(await screen.findByRole('button', { name: '우편번호 찾기' }))
    await waitFor(() => expect(window.kakao.Postcode).toHaveBeenCalledTimes(1))
    postcodeOptions.oncomplete({ zonecode: '02535', address: '서울특별시 중구 세종대로 110' })
    fireEvent.change(screen.getByPlaceholderText('상세 주소'), { target: { value: '101호' } })
    fireEvent.click(screen.getByRole('button', { name: '수정 완료' }))

    await waitFor(() => expect(updateUserProfile).toHaveBeenCalledWith(expect.objectContaining({
      zipCode: '02535',
      userAddress1: '서울특별시 중구 세종대로 110',
      userAddress2: '101호',
    })))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/mypage'))
  })
})
