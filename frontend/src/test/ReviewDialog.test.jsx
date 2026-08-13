import { createElement } from 'react'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import ArtistReview from '../pages/MyPage/ArtistReview'
import MyReview from '../pages/MyPage/MyReview'

function renderPage(Component) {
  return render(
    <MemoryRouter>
      {createElement(Component)}
    </MemoryRouter>,
  )
}

describe('기존 리뷰 dialog', () => {
  beforeEach(() => {
    localStorage.setItem('token', 'token')
    localStorage.setItem('userStatus', '1')
  })

  it('내 리뷰 상세의 제목과 기존 수정 동작을 유지한다', () => {
    renderPage(MyReview)
    fireEvent.click(screen.getByRole('button', { name: '연예인 병 리뷰 상세 보기' }))

    expect(screen.getByRole('dialog', { name: '연예인 병' })).toBeVisible()
    expect(screen.getByRole('link', { name: '리뷰 수정하기' })).toHaveAttribute('href', '/write-review/s1')
    fireEvent.click(screen.getAllByRole('button', { name: '닫기' })[0])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('작가 리뷰 상세에 접근 가능한 제목과 기존 내용을 유지한다', () => {
    renderPage(ArtistReview)
    fireEvent.click(screen.getAllByRole('button', { name: '봄날의 기억 리뷰 상세 보기' })[0])

    const dialog = screen.getByRole('dialog', { name: '봄날의 기억 리뷰 상세' })
    expect(dialog).toBeVisible()
    expect(within(dialog).getByText('아트러버123')).toBeVisible()
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
