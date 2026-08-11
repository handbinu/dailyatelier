import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UploadSell from '../pages/MyPage/UploadSell'

vi.mock('../api/artApi', () => ({ createArt: vi.fn() }))

describe('작품 등록 분류 입력', () => {
  beforeEach(() => { localStorage.setItem('token', 'token'); localStorage.setItem('userStatus', '1'); vi.stubGlobal('alert', vi.fn()) })

  it('형태와 카테고리를 필수로 검증하고 재료·기법 라벨을 표시한다', () => {
    render(<MemoryRouter><UploadSell /></MemoryRouter>)
    expect(screen.getByLabelText('작품 형태 *')).toBeRequired()
    expect(screen.getByLabelText('카테고리 *')).toBeRequired()
    expect(screen.getByText('재료·기법 *')).toBeInTheDocument()
    fireEvent.submit(screen.getByRole('button', { name: '작품 등록하기' }).closest('form'))
    expect(screen.getByText('작품 형태를 선택해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('작품 카테고리를 선택해 주세요.')).toBeInTheDocument()
  })

  it('형태를 선택하기 전에는 카테고리 선택을 비활성화한다', () => {
    render(<MemoryRouter><UploadSell /></MemoryRouter>)

    expect(screen.getByLabelText('카테고리 *')).toBeDisabled()
  })

  it('디지털 형태에는 디지털 아트 카테고리만 노출한다', () => {
    render(<MemoryRouter><UploadSell /></MemoryRouter>)

    fireEvent.change(screen.getByLabelText('작품 형태 *'), { target: { value: 'DIGITAL' } })

    const category = screen.getByLabelText('카테고리 *')
    expect(category).toBeEnabled()
    expect(category).toHaveTextContent('디지털 아트')
    expect(category).not.toHaveTextContent('유화')
  })

  it('실물 형태에는 디지털 아트를 제외한 카테고리만 노출한다', () => {
    render(<MemoryRouter><UploadSell /></MemoryRouter>)

    fireEvent.change(screen.getByLabelText('작품 형태 *'), { target: { value: 'PHYSICAL' } })

    const category = screen.getByLabelText('카테고리 *')
    expect(category).toBeEnabled()
    expect(category).toHaveTextContent('유화')
    expect(category).not.toHaveTextContent('디지털 아트')
  })

  it('형태 변경 후 호환되지 않는 카테고리 선택을 초기화한다', () => {
    render(<MemoryRouter><UploadSell /></MemoryRouter>)

    const format = screen.getByLabelText('작품 형태 *')
    const category = screen.getByLabelText('카테고리 *')
    fireEvent.change(format, { target: { value: 'PHYSICAL' } })
    fireEvent.change(category, { target: { value: 'OIL_PAINTING' } })
    expect(category).toHaveValue('OIL_PAINTING')

    fireEvent.change(format, { target: { value: 'DIGITAL' } })

    expect(category).toHaveValue('')
    expect(category).toHaveTextContent('디지털 아트')
    expect(category).not.toHaveTextContent('유화')
  })
})
