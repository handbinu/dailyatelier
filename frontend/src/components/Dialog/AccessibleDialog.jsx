import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

function getFocusableElements(container) {
  return [...container.querySelectorAll(FOCUSABLE_SELECTOR)]
    .filter((element) => !element.closest('[hidden], [inert], [aria-hidden="true"]'))
}

export default function AccessibleDialog({
  children,
  onClose,
  labelledBy,
  describedBy,
  overlayClassName,
  contentClassName,
  closeOnBackdrop = true,
}) {
  const overlayRef = useRef(null)
  const contentRef = useRef(null)
  const openerRef = useRef(typeof document === 'undefined' ? null : document.activeElement)
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    const backgroundRoot = document.getElementById('root')
    const overlay = overlayRef.current
    const opener = openerRef.current
    const previousInert = backgroundRoot?.inert ?? false
    const previousOverflow = document.body.style.overflow

    if (backgroundRoot) backgroundRoot.inert = true
    document.body.style.overflow = 'hidden'

    const focusInitialElement = () => {
      const content = contentRef.current
      if (!content) return
      const initialElement = content.querySelector('[data-dialog-initial-focus]')
        || getFocusableElements(content)[0]
        || content
      initialElement.focus()
    }

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCloseRef.current()
        return
      }

      if (event.key !== 'Tab') return
      const content = contentRef.current
      if (!content) return
      const focusable = getFocusableElements(content)

      if (focusable.length === 0) {
        event.preventDefault()
        content.focus()
        return
      }

      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    const focusFrame = requestAnimationFrame(focusInitialElement)
    document.addEventListener('keydown', handleKeyDown)

    return () => {
      cancelAnimationFrame(focusFrame)
      document.removeEventListener('keydown', handleKeyDown)
      if (backgroundRoot) backgroundRoot.inert = previousInert
      document.body.style.overflow = previousOverflow

      requestAnimationFrame(() => {
        if (overlay?.isConnected) return
        if (opener?.isConnected && typeof opener.focus === 'function') opener.focus()
      })
    }
  }, [])

  const dialog = (
    <div
      ref={overlayRef}
      className={overlayClassName}
      onMouseDown={(event) => {
        if (closeOnBackdrop && event.target === event.currentTarget) onClose()
      }}
    >
      <div
        ref={contentRef}
        className={contentClassName}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        aria-describedby={describedBy}
        tabIndex={-1}
      >
        {children}
      </div>
    </div>
  )

  return createPortal(dialog, document.body)
}
