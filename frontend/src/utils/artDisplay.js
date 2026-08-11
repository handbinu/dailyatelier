const DEADLINE_SOON_MS = 24 * 60 * 60 * 1000

const closingFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export const formatPrice = (price) => Number(price ?? 0).toLocaleString('ko-KR')

export const formatClosingTime = (closingTime) => {
  const date = new Date(closingTime)
  return Number.isNaN(date.getTime()) ? '마감 일시 미정' : closingFormatter.format(date)
}

export const getDeadlineMeta = (closingTime) => {
  const closingTimestamp = new Date(closingTime).getTime()
  if (Number.isNaN(closingTimestamp)) {
    return { label: '마감 일시 미정', isUrgent: false, isClosed: false }
  }

  const remaining = closingTimestamp - Date.now()
  if (remaining <= 0) {
    return { label: '마감됨', isUrgent: false, isClosed: true }
  }
  if (remaining <= DEADLINE_SOON_MS) {
    return { label: '마감 임박', isUrgent: true, isClosed: false }
  }
  return { label: '진행 중', isUrgent: false, isClosed: false }
}

export const getAuctionStatusMeta = ({ artStatus, bidStartTime, closingTime }, now = Date.now()) => {
  if (artStatus === 2) return { label: '낙찰', tone: 'won', phase: 'ENDED' }
  if (artStatus !== 0) return { label: '종료', tone: 'ended', phase: 'ENDED' }

  const start = new Date(bidStartTime).getTime()
  const closing = new Date(closingTime).getTime()
  if (Number.isNaN(start) || Number.isNaN(closing)) return { label: '상태 미정', tone: 'ended', phase: 'UNKNOWN' }
  if (now < start) return { label: '예정', tone: 'upcoming', phase: 'UPCOMING' }
  if (now >= closing) return { label: '종료', tone: 'ended', phase: 'ENDED' }
  return { label: '진행 중', tone: 'active', phase: 'ONGOING' }
}
