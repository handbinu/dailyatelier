import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  approveSellerOrderRefund,
  getSellerOrder,
  getSellerOrders,
  rejectSellerOrderRefund,
  updateSellerOrderStatus,
} from '../../api/orderApi'
import { applyArtImageFallback, getArtImageSrc } from '../../utils/artImage'
import {
  formatOrderDate,
  formatOrderPrice,
  formatShippingAddress,
  getOrderError,
  ORDER_FILTERS,
} from '../../utils/orderView'
import { createOrderRequestGuard } from '../../utils/orderRequestGuard'
import {
  buildSellerStatusRequest,
  SELLER_ACTION,
} from '../../utils/sellerOrderView'
import { PageBanner, PageWrap } from './components/atoms'
import {
  OrderFeedback,
  OrderListState,
  OrderStatusBadge,
} from './components/OrderCommon'
import s from './SalesOrders.module.css'

const PAGE_SIZE = 12
const EMPTY_SHIPPING = { shippingCarrier: '', trackingNumber: '' }

export default function SalesOrders() {
  const navigate = useNavigate()
  const [filter, setFilter] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [details, setDetails] = useState({})
  const [openOrderId, setOpenOrderId] = useState(null)
  const [shippingOrderId, setShippingOrderId] = useState(null)
  const [shippingForm, setShippingForm] = useState(EMPTY_SHIPPING)
  const [loading, setLoading] = useState(true)
  const [detailLoadingId, setDetailLoadingId] = useState(null)
  const [processingId, setProcessingId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const requestGuard = useRef(createOrderRequestGuard())

  const handleRequestError = useCallback((requestError, fallback) => {
    const orderError = getOrderError(requestError, fallback)
    if (orderError.shouldLogin) {
      navigate('/login', { replace: true })
      return orderError
    }
    setError(orderError.message)
    return orderError
  }, [navigate])

  const loadOrders = useCallback(async ({ signal } = {}) => {
    setLoading(true)
    setError('')
    try {
      const { data } = await getSellerOrders({
        status: filter || undefined,
        page,
        size: PAGE_SIZE,
        signal,
      })
      setResult(data)
    } catch (requestError) {
      if (requestError.code !== 'ERR_CANCELED') {
        setResult(null)
        handleRequestError(requestError, '판매 주문을 불러오지 못했습니다.')
      }
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [filter, handleRequestError, page])

  const loadDetail = useCallback(async (orderId, { force = false } = {}) => {
    if (!force && details[orderId]) return details[orderId]
    setDetailLoadingId(orderId)
    setError('')
    try {
      const { data } = await getSellerOrder(orderId)
      setDetails((current) => ({ ...current, [orderId]: data }))
      return data
    } catch (requestError) {
      handleRequestError(requestError, '판매 주문 상세를 불러오지 못했습니다.')
      return null
    } finally {
      setDetailLoadingId(null)
    }
  }, [details, handleRequestError])

  useEffect(() => {
    const controller = new AbortController()
    loadOrders({ signal: controller.signal })
    return () => controller.abort()
  }, [loadOrders])

  const summaryItems = useMemo(() => {
    const counts = result?.statusCounts ?? {}
    return [
      { label: '전체 판매', value: result?.totalElements ?? 0 },
      { label: '결제 완료', value: counts.PAID ?? 0 },
      { label: '배송 준비', value: counts.PREPARING ?? 0 },
      { label: '배송 중', value: counts.SHIPPED ?? 0 },
    ]
  }, [result])

  const changeFilter = (value) => {
    setFilter(value)
    setPage(0)
    setOpenOrderId(null)
    setShippingOrderId(null)
    setNotice('')
  }

  const toggleDetail = async (orderId) => {
    if (openOrderId === orderId) {
      setOpenOrderId(null)
      setShippingOrderId(null)
      return
    }
    setOpenOrderId(orderId)
    setShippingOrderId(null)
    await loadDetail(orderId)
  }

  const runAction = async (orderId, action) => {
    if (!requestGuard.current.begin(orderId)) return
    let request
    try {
      request = buildSellerStatusRequest(action, shippingForm)
    } catch (validationError) {
      requestGuard.current.end(orderId)
      setError(validationError.message)
      return
    }

    setProcessingId(orderId)
    setError('')
    setNotice('')
    try {
      const { data } = await updateSellerOrderStatus(orderId, request)
      setDetails((current) => ({ ...current, [orderId]: data }))
      setShippingOrderId(null)
      setShippingForm(EMPTY_SHIPPING)
      await loadOrders()
      setNotice(action === SELLER_ACTION.START_PREPARING
        ? '배송 준비 상태로 변경했습니다.'
        : '발송 정보와 주문 상태를 저장했습니다.')
    } catch (requestError) {
      const orderError = handleRequestError(
        requestError,
        '판매 주문 상태를 변경하지 못했습니다.',
      )
      if (orderError.shouldReload) {
        await Promise.all([
          loadDetail(orderId, { force: true }),
          loadOrders(),
        ])
      }
    } finally {
      requestGuard.current.end(orderId)
      setProcessingId(null)
    }
  }

  const runRefundDecision = async (orderId, approve) => {
    if (!window.confirm(approve ? '환불을 승인하시겠습니까?' : '환불을 거절하시겠습니까?')) return
    if (!requestGuard.current.begin(orderId)) return
    setProcessingId(orderId)
    setError('')
    setNotice('')
    try {
      const response = approve
        ? await approveSellerOrderRefund(orderId, `order-refund:${orderId}`)
        : await rejectSellerOrderRefund(orderId)
      setDetails((current) => ({ ...current, [orderId]: response.data }))
      await loadOrders()
      setNotice(approve ? '환불을 승인했습니다.' : '환불을 거절했습니다.')
    } catch (requestError) {
      const orderError = handleRequestError(requestError, '환불 요청을 처리하지 못했습니다.')
      if (orderError.shouldReload) {
        await Promise.all([loadDetail(orderId, { force: true }), loadOrders()])
      }
    } finally {
      requestGuard.current.end(orderId)
      setProcessingId(null)
    }
  }

  const items = result?.content ?? []

  return (
    <PageWrap>
      <PageBanner title="판매 주문 관리" crumb="판매 주문" />
      <div className={s.body}>
        <OrderFeedback
          notice={notice}
          error={error}
          onRetry={() => loadOrders()}
        />

        <div className={s.summary}>
          {summaryItems.map((item) => (
            <div key={item.label} className={s.summaryItem}>
              <strong>{item.value}</strong>
              <span>{item.label}</span>
            </div>
          ))}
        </div>

        <div className={s.filterBar} role="group" aria-label="판매 주문 상태 필터">
          {ORDER_FILTERS.map((option) => (
            <button
              type="button"
              key={option.value}
              className={filter === option.value ? s.filterActive : ''}
              onClick={() => changeFilter(option.value)}
              aria-pressed={filter === option.value}
            >
              {option.label}
            </button>
          ))}
        </div>

        <OrderListState
          loading={loading}
          isEmpty={items.length === 0}
          loadingMessage="판매 주문을 불러오는 중입니다."
          emptyMessage="조건에 맞는 판매 주문이 없습니다."
        >
          <div className={s.list}>
            {items.map((order) => (
              <SellerOrderItem
                key={order.orderId}
                order={order}
                detail={details[order.orderId]}
                isOpen={openOrderId === order.orderId}
                isLoading={detailLoadingId === order.orderId}
                isProcessing={processingId === order.orderId}
                isShipping={shippingOrderId === order.orderId}
                shippingForm={shippingForm}
                onToggle={() => toggleDetail(order.orderId)}
                onPrepare={() =>
                  runAction(order.orderId, SELLER_ACTION.START_PREPARING)}
                onOpenShipping={() => {
                  setOpenOrderId(order.orderId)
                  setShippingOrderId(order.orderId)
                  setShippingForm(EMPTY_SHIPPING)
                  loadDetail(order.orderId)
                }}
                onShippingChange={(event) => setShippingForm((current) => ({
                  ...current,
                  [event.target.name]: event.target.value,
                }))}
                onShippingCancel={() => setShippingOrderId(null)}
                onShip={(event) => {
                  event.preventDefault()
                  runAction(order.orderId, SELLER_ACTION.SHIP)
                }}
                onApproveRefund={() => runRefundDecision(order.orderId, true)}
                onRejectRefund={() => runRefundDecision(order.orderId, false)}
              />
            ))}
          </div>
        </OrderListState>

        {result?.totalPages > 1 && (
          <nav className={s.pagination} aria-label="판매 주문 목록 페이지">
            <button
              type="button"
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              disabled={page === 0 || loading}
            >
              이전
            </button>
            <span>{page + 1} / {result.totalPages}</span>
            <button
              type="button"
              onClick={() => setPage((current) => current + 1)}
              disabled={page + 1 >= result.totalPages || loading}
            >
              다음
            </button>
          </nav>
        )}

        <div className={s.backRow}>
          <Link to="/mypage">← 마이페이지로 돌아가기</Link>
        </div>
      </div>
    </PageWrap>
  )
}

function SellerOrderItem({
  order,
  detail,
  isOpen,
  isLoading,
  isProcessing,
  isShipping,
  shippingForm,
  onToggle,
  onPrepare,
  onOpenShipping,
  onShippingChange,
  onShippingCancel,
  onShip,
  onApproveRefund,
  onRejectRefund,
}) {
  const actions = detail?.availableActions ?? order.availableActions ?? []

  return (
    <article className={s.order}>
      <div className={s.orderRow}>
        <button type="button" className={s.orderMain} onClick={onToggle}>
          <img
            src={getArtImageSrc(order.artImage)}
            alt={order.artName}
            onError={applyArtImageFallback}
          />
          <span className={s.orderInfo}>
            <strong>{order.artName}</strong>
            <span>구매자 {order.counterpartyName}</span>
            <span>{order.orderNumber} · {formatOrderDate(order.createdAt)}</span>
          </span>
          <span className={s.price}>{formatOrderPrice(order.winningPrice)}</span>
          <OrderStatusBadge status={order.status} />
        </button>
        <div className={s.actions}>
          {actions.includes(SELLER_ACTION.START_PREPARING) && (
            <button type="button" onClick={onPrepare} disabled={isProcessing}>
              배송 준비
            </button>
          )}
          {actions.includes(SELLER_ACTION.SHIP) && (
            <button type="button" onClick={onOpenShipping} disabled={isProcessing}>
              발송 처리
            </button>
          )}
          {actions.includes(SELLER_ACTION.APPROVE_REFUND) && (
            <button type="button" onClick={onApproveRefund} disabled={isProcessing}>
              환불 승인
            </button>
          )}
          {actions.includes(SELLER_ACTION.REJECT_REFUND) && (
            <button type="button" onClick={onRejectRefund} disabled={isProcessing}>
              환불 거절
            </button>
          )}
          <button type="button" onClick={onToggle} aria-expanded={isOpen}>
            {isOpen ? '접기' : '상세'}
          </button>
        </div>
      </div>

      {isOpen && (
        <div className={s.detail}>
          {isLoading && !detail ? (
            <p>주문 상세를 불러오는 중입니다.</p>
          ) : detail ? (
            <>
              <div className={s.meta}>
                <Meta label="주문 번호" value={detail.orderNumber} />
                <Meta
                  label="구매자"
                  value={`${detail.buyerName} (${detail.buyerNickname}) · ${detail.buyerPhone}`}
                />
                <Meta
                  label="배송지"
                  value={detail.shippingAddress
                    ? `${detail.shippingAddress.recipientName} · ${detail.shippingAddress.recipientPhone} · ${formatShippingAddress(detail.shippingAddress)}`
                    : '배송지 미확정'}
                />
                <Meta label="낙찰가" value={formatOrderPrice(detail.winningPrice)} />
                {detail.shippingCarrier && (
                  <Meta
                    label="배송 정보"
                    value={`${detail.shippingCarrier} · ${detail.trackingNumber}`}
                  />
                )}
                {detail.paidAt && <Meta label="결제 시각" value={formatOrderDate(detail.paidAt)} />}
                {detail.preparingAt && <Meta label="준비 시각" value={formatOrderDate(detail.preparingAt)} />}
                {detail.shippedAt && <Meta label="발송 시각" value={formatOrderDate(detail.shippedAt)} />}
                {detail.cancelReason && <Meta label="취소 사유" value={detail.cancelReason} />}
                {detail.refundReason && <Meta label="환불 사유" value={detail.refundReason} />}
              </div>
              <Link className={s.artLink} to={`/auction/${detail.artId}`}>작품 페이지</Link>
            </>
          ) : null}

          {isShipping && (
            <form className={s.shippingForm} onSubmit={onShip}>
              <h3>발송 정보</h3>
              <label>
                택배사
                <input
                  name="shippingCarrier"
                  value={shippingForm.shippingCarrier}
                  onChange={onShippingChange}
                  maxLength={50}
                  required
                />
              </label>
              <label>
                송장번호
                <input
                  name="trackingNumber"
                  value={shippingForm.trackingNumber}
                  onChange={onShippingChange}
                  maxLength={100}
                  required
                />
              </label>
              <div>
                <button type="button" onClick={onShippingCancel}>취소</button>
                <button type="submit" disabled={isProcessing}>
                  {isProcessing ? '처리 중...' : '발송 저장'}
                </button>
              </div>
            </form>
          )}
        </div>
      )}
    </article>
  )
}

function Meta({ label, value }) {
  return (
    <div className={s.metaRow}>
      <strong>{label}</strong>
      <span>{value || '-'}</span>
    </div>
  )
}
