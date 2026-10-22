import { Badge, Empty } from './atoms'
import { getOrderStatusView } from '../../../utils/orderView'
import s from './OrderCommon.module.css'

export function OrderStatusBadge({ status }) {
  const view = getOrderStatusView(status)
  return <Badge label={view.label} color={view.color} />
}

export function OrderFeedback({ notice, error, onRetry }) {
  if (!notice && !error) return null

  return (
    <div
      className={`${s.feedback} ${error ? s.error : s.notice}`}
      role={error ? 'alert' : 'status'}
    >
      <p>{error || notice}</p>
      {error && onRetry && (
        <button type="button" onClick={onRetry}>
          다시 시도
        </button>
      )}
    </div>
  )
}

export function OrderListState({
  loading,
  isEmpty,
  loadingMessage,
  emptyMessage,
  children,
}) {
  if (loading) return <Empty msg={loadingMessage} />
  if (isEmpty) return <Empty msg={emptyMessage} />
  return children
}
