import { useCallback, useRef, useState } from 'react'

const initialFieldState = { userId: false, nickname: false }
const initialMessages = { userId: '', nickname: '' }

export default function useDuplicateCheck(values, checkers) {
  const [checked, setChecked] = useState(initialFieldState)
  const [checking, setChecking] = useState(initialFieldState)
  const [messages, setMessages] = useState(initialMessages)
  const requestIds = useRef({ userId: 0, nickname: 0 })
  const valuesRef = useRef(values)
  valuesRef.current = values

  const invalidate = useCallback((field) => {
    requestIds.current[field] += 1
    setChecked((current) => ({ ...current, [field]: false }))
    setChecking((current) => ({ ...current, [field]: false }))
    setMessages((current) => ({ ...current, [field]: '' }))
  }, [])

  const check = useCallback(async (field) => {
    const value = valuesRef.current[field].trim()
    const requestId = requestIds.current[field] + 1
    requestIds.current[field] = requestId

    setChecked((current) => ({ ...current, [field]: false }))
    if (!value) {
      setChecking((current) => ({ ...current, [field]: false }))
      setMessages((current) => ({ ...current, [field]: '값을 입력해주세요.' }))
      return
    }

    setChecking((current) => ({ ...current, [field]: true }))
    setMessages((current) => ({ ...current, [field]: '확인 중...' }))

    const isCurrentRequest = () => (
      requestIds.current[field] === requestId
      && valuesRef.current[field].trim() === value
    )

    try {
      const response = await checkers[field](value)
      if (!isCurrentRequest()) return

      const duplicate = response.data.duplicate
      setChecked((current) => ({ ...current, [field]: !duplicate }))
      setMessages((current) => ({
        ...current,
        [field]: duplicate ? '이미 사용 중입니다' : '사용 가능합니다. ✓',
      }))
    } catch {
      if (!isCurrentRequest()) return
      setChecked((current) => ({ ...current, [field]: false }))
      setMessages((current) => ({
        ...current,
        [field]: '확인 중 오류가 발생했습니다.',
      }))
    } finally {
      if (isCurrentRequest()) {
        setChecking((current) => ({ ...current, [field]: false }))
      }
    }
  }, [checkers])

  return { checked, checking, messages, invalidate, check }
}
