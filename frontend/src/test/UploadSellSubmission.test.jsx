import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import api from '../api/authApi'
import { createArt } from '../api/artApi'
import UploadSell from '../pages/MyPage/UploadSell'

vi.mock('../api/authApi', () => ({ default: { post: vi.fn() } }))
vi.mock('../api/artApi', () => ({ createArt: vi.fn() }))

const signatureResponse = {
  data: {
    apiKey: 'key',
    timestamp: 1,
    signature: 'signature',
    folder: 'arts',
    uploadUrl: '/cloudinary/upload',
  },
}

const uploadResponse = {
  ok: true,
  json: async () => ({ secure_url: '/uploaded-art.jpg' }),
}

const createdArtResponse = {
  data: {
    artId: 7,
    name: '새 작품',
    currentPrice: 30000,
    minimumBidIncrement: 1000,
  },
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

function renderValidForm() {
  const view = render(<MemoryRouter><UploadSell /></MemoryRouter>)
  const fileInput = view.container.querySelector('input[type="file"]')
  const image = new File(['image'], 'art.png', { type: 'image/png' })

  fireEvent.change(fileInput, { target: { files: [image] } })
  fireEvent.change(screen.getByLabelText('작품명 *'), { target: { value: '새 작품' } })
  fireEvent.change(screen.getByLabelText('작품 설명'), { target: { value: '작품 설명' } })
  fireEvent.change(screen.getByLabelText('작품 형태 *'), { target: { value: 'DIGITAL' } })
  fireEvent.change(screen.getByLabelText('카테고리 *'), { target: { value: 'DIGITAL_ART' } })
  fireEvent.change(screen.getByLabelText('재료·기법 *'), { target: { value: '디지털' } })
  fireEvent.change(screen.getByLabelText('작가 소개'), { target: { value: '작가 소개' } })
  fireEvent.change(screen.getByPlaceholderText('0'), { target: { value: '30000' } })
  fireEvent.change(screen.getByLabelText('최소 입찰 증분 *'), { target: { value: '1000' } })
  const [bidStartTime, closingTime] = view.container.querySelectorAll('input[type="datetime-local"]')
  fireEvent.change(bidStartTime, { target: { value: '2099-01-01T10:00' } })
  fireEvent.change(closingTime, { target: { value: '2099-01-02T10:00' } })

  return {
    ...view,
    fileInput,
    image,
    form: screen.getByRole('button', { name: '작품 등록하기' }).closest('form'),
  }
}

describe('작품 등록 제출 제어', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem('token', 'token')
    localStorage.setItem('userStatus', '1')
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:preview'),
      revokeObjectURL: vi.fn(),
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(uploadResponse))
    api.post.mockResolvedValue(signatureResponse)
    createArt.mockResolvedValue(createdArtResponse)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('연속 제출에도 서명, 업로드와 작품 생성을 한 번만 순서대로 실행한다', async () => {
    const signatureRequest = deferred()
    const cloudinaryRequest = deferred()
    const artRequest = deferred()
    api.post.mockReturnValue(signatureRequest.promise)
    fetch.mockReturnValue(cloudinaryRequest.promise)
    createArt.mockReturnValue(artRequest.promise)
    const { form } = renderValidForm()

    fireEvent.submit(form)
    fireEvent.submit(form)

    expect(api.post).toHaveBeenCalledTimes(1)
    expect(api.post).toHaveBeenCalledWith('/api/uploads/cloudinary/signature', { folder: 'arts' })
    expect(fetch).not.toHaveBeenCalled()
    expect(createArt).not.toHaveBeenCalled()
    form.querySelectorAll('input, textarea, select, button')
      .forEach((control) => expect(control).toBeDisabled())

    signatureRequest.resolve(signatureResponse)
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1))
    expect(createArt).not.toHaveBeenCalled()

    cloudinaryRequest.resolve(uploadResponse)
    await waitFor(() => expect(createArt).toHaveBeenCalledTimes(1))

    artRequest.resolve(createdArtResponse)
    expect(await screen.findByRole('heading', { name: '새 작품' })).toBeVisible()
    expect(api.post).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(createArt).toHaveBeenCalledTimes(1)
  })

  it.each([
    ['서명', '서명 요청 실패', 2, 1, 1],
    ['업로드', 'Cloudinary 업로드 실패', 2, 2, 1],
    ['작품 생성', '작품 생성 실패', 2, 2, 2],
  ])('%s 실패 후 입력과 파일을 유지하고 다시 제출한다', async (
    stage,
    message,
    signatureCalls,
    uploadCalls,
    createCalls,
  ) => {
    if (stage === '서명') {
      api.post.mockRejectedValueOnce(new Error(message))
    } else if (stage === '업로드') {
      fetch.mockRejectedValueOnce(new Error(message))
    } else {
      createArt.mockRejectedValueOnce({ response: { data: { message } } })
    }
    const { fileInput, form, image } = renderValidForm()

    fireEvent.submit(form)

    expect(await screen.findByText(message)).toBeVisible()
    expect(screen.getByLabelText('작품명 *')).toBeEnabled()
    expect(screen.getByLabelText('작품명 *')).toHaveValue('새 작품')
    expect(fileInput).toBeEnabled()
    expect(fileInput.files[0]).toBe(image)
    expect(screen.getByRole('button', { name: '작품 등록하기' })).toBeEnabled()

    fireEvent.submit(form)

    expect(await screen.findByRole('heading', { name: '새 작품' })).toBeVisible()
    expect(api.post).toHaveBeenCalledTimes(signatureCalls)
    expect(fetch).toHaveBeenCalledTimes(uploadCalls)
    expect(createArt).toHaveBeenCalledTimes(createCalls)
  })
})
