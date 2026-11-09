import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AccessibleDialog from '../components/Dialog/AccessibleDialog'

function DialogHarness({ removeOpener = false }) {
  const [open, setOpen] = useState(false)
  const [showOpener, setShowOpener] = useState(true)

  return (
    <>
      {showOpener && (
        <button
          type="button"
          onClick={() => {
            setOpen(true)
            if (removeOpener) setShowOpener(false)
          }}
        >
          열기
        </button>
      )}
      <a href="/background">배경 링크</a>
      {open && (
        <AccessibleDialog
          onClose={() => setOpen(false)}
          labelledBy="dialog-title"
          describedBy="dialog-description"
          overlayClassName="overlay"
          contentClassName="content"
        >
          <h2 id="dialog-title">상세 정보</h2>
          <p id="dialog-description">상세 설명</p>
          <button type="button" data-dialog-initial-focus onClick={() => setOpen(false)}>닫기</button>
          <a href="/action">다음 작업</a>
        </AccessibleDialog>
      )}
    </>
  )
}

describe('AccessibleDialog', () => {
  beforeEach(() => {
    document.body.innerHTML = '<div id="root"></div>'
    document.body.style.overflow = 'clip'
    vi.stubGlobal('requestAnimationFrame', (callback) => {
      callback()
      return 1
    })
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.body.style.overflow = ''
  })

  it('dialog 계약과 최초 포커스, 배경 차단을 적용한다', () => {
    render(<DialogHarness />, { container: document.getElementById('root') })
    fireEvent.click(screen.getByRole('button', { name: '열기' }))

    const dialog = screen.getByRole('dialog', { name: '상세 정보' })
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(dialog).toHaveAccessibleDescription('상세 설명')
    expect(screen.getByRole('button', { name: '닫기' })).toHaveFocus()
    expect(document.getElementById('root').inert).toBe(true)
    expect(document.body.style.overflow).toBe('hidden')
  })

  it('Tab과 Shift+Tab 포커스를 dialog 안에서 순환시킨다', () => {
    render(<DialogHarness />, { container: document.getElementById('root') })
    fireEvent.click(screen.getByRole('button', { name: '열기' }))

    const close = screen.getByRole('button', { name: '닫기' })
    const action = screen.getByRole('link', { name: '다음 작업' })
    action.focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(close).toHaveFocus()

    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(action).toHaveFocus()
  })

  it('Escape와 overlay 클릭으로 닫고 opener와 기존 배경 상태를 복원한다', () => {
    const root = document.getElementById('root')
    render(<DialogHarness />, { container: root })
    const opener = screen.getByRole('button', { name: '열기' })
    opener.focus()
    fireEvent.click(opener)
    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(opener).toHaveFocus()
    expect(root.inert).toBe(false)
    expect(document.body.style.overflow).toBe('clip')

    fireEvent.click(opener)
    fireEvent.mouseDown(document.querySelector('.overlay'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('내부 클릭은 닫지 않고 제거된 opener에는 포커스를 시도하지 않는다', () => {
    render(<DialogHarness removeOpener />, { container: document.getElementById('root') })
    fireEvent.click(screen.getByRole('button', { name: '열기' }))
    const dialog = screen.getByRole('dialog')
    fireEvent.mouseDown(dialog)
    expect(dialog).toBeInTheDocument()

    expect(() => fireEvent.keyDown(document, { key: 'Escape' })).not.toThrow()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
