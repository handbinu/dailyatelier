import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

function SmokeComponent() {
  return <button type="button" disabled>렌더링 준비 완료</button>
}

describe('React 컴포넌트 테스트 환경', () => {
  it('jsdom에 컴포넌트를 렌더링하고 jest-dom matcher를 사용한다', () => {
    render(<SmokeComponent />)

    expect(screen.getByRole('button', { name: '렌더링 준비 완료' }))
      .toBeDisabled()
  })
})
