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
})
