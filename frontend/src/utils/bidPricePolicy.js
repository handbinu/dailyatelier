export const MAX_BID_PRICE = 2_100_000_000
export const DEFAULT_MINIMUM_BID_INCREMENT = 1_000
export const MINIMUM_BID_INCREMENT = 100
export const MAXIMUM_BID_INCREMENT = 10_000_000

export const parseIntegerPrice = (value) => {
  const normalized = String(value).trim()
  if (!/^\d+$/.test(normalized)) return null

  const price = Number(normalized)
  return Number.isSafeInteger(price) ? price : null
}

export const getNextMinimumBidPrice = (currentPrice, minimumBidIncrement) => {
  const nextPrice = currentPrice + minimumBidIncrement
  return Number.isSafeInteger(nextPrice) && nextPrice <= MAX_BID_PRICE ? nextPrice : null
}

export const getMinimumBidIncrementError = (value) => {
  const increment = parseIntegerPrice(value)
  if (increment === null) return '최소 입찰 증분은 정수로 입력해 주세요.'
  if (increment < MINIMUM_BID_INCREMENT || increment > MAXIMUM_BID_INCREMENT) {
    return '최소 입찰 증분은 100원 이상 1,000만원 이하로 입력해 주세요.'
  }
  if (increment % 100 !== 0) return '최소 입찰 증분은 100원 단위로 입력해 주세요.'
  return ''
}
