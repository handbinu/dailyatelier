import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
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
  formatOrderCancelReason,
  formatOrderPrice,
  formatShippingAddress,
  getOrderError,
  ORDER_FILTERS,
} from '../../utils/orderView'
import { createOrderRequestGuard } from '../../utils/orderRequestGuard'
import { createLatestRequest } from '../../utils/latestRequest'
import { createLoginState } from '../../utils/loginReturn'
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
const SELLER_ACTION_LABELS = {
  [SELLER_ACTION.START_PREPARING]: '배송 준비',
  [SELLER_ACTION.SHIP]: '발송 처리',
}

export default function SalesOrders() {
  const navigate = useNavigate()
  const location = useLocation()
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
  const listRequest = useRef(null)
  const listParams = useRef({ filter, page })

  const handleRequestError = useCallback((requestError, fallback) => {
    const orderError = getOrderError(requestError, fallback)
    if (orderError.shouldLogin) {
      navigate('/login', { replace: true, state: createLoginState(location) })
      return orderError
    }
    setError(orderError.message)
    return orderError
  }, [location, navigate])

  const loadOrders = useCallback(() => listRequest.current?.run(
    async ({ signal }) => {
      const { filter: currentFilter, page: currentPage } = listParams.current
      const { data } = await getSellerOrders({
        status: currentFilter || undefined,
        page: currentPage,
        size: PAGE_SIZE,
        signal,
      })
      return data
    },
    {
      onStart: () => {
        setLoading(true)
        setError('')
      },
      onSuccess: setResult,
      onError: (requestError) => {
        setResult(null)
        handleRequestError(requestError, '판매 주문을 불러오지 못했습니다.')
      },
      onFinally: () => setLoading(false),
    },
  ), [handleRequestError])

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
    const request = createLatestRequest()
    listRequest.current = request
    return () => {
      request.dispose()
      if (listRequest.current === request) listRequest.current = null
    }
  }, [])

  useEffect(() => {
    listParams.current = { filter, page }
  }, [filter, page])

  useEffect(() => {
    loadOrders()
  }, [filter, loadOrders, page])

  const summaryItems = useMemo(() => {
    const counts = result?.statusCounts ?? {}
    const total = Object.values(counts).reduce(
      (sum, value) => sum + Number(value || 0),
      0,
    )
    return [
      { label: '전체 판매', value: total, tone: 'Neutral', icon: 'orders' },
      { label: '배송 준비', value: counts.PAID ?? 0, tone: 'Attention', icon: 'package' },
      { label: '발송 필요', value: counts.PREPARING ?? 0, tone: 'Progress', icon: 'shipping' },
      { label: '환불 요청', value: result?.refundRequestedCount ?? 0, tone: 'Confirm', icon: 'confirm' },
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
          {summaryItems.map(({ label, value, tone, icon }) => (
            <div key={label} className={`${s.summaryItem} ${s[`summary${tone}`]}`}>
              <span className={s.summaryHeader}>
                <SummaryIcon type={icon} />
                <span className={s.summaryLabel}>{label}</span>
              </span>
              <span className={s.summaryValue}>{value}</span>
            </div>
          ))}
        </div>

        <div className={s.filterBar} role="group" aria-label="판매 주문 상태 필터">
          <button type="button" className={filter === '' ? s.filterActive : ''} onClick={() => changeFilter('')} aria-pressed={filter === ''}>전체</button>
          <button type="button" className={filter === 'PAID' ? s.filterActive : ''} onClick={() => changeFilter('PAID')} aria-pressed={filter === 'PAID'}>배송 준비 필요</button>
          <button type="button" className={filter === 'PREPARING' ? s.filterActive : ''} onClick={() => changeFilter('PREPARING')} aria-pressed={filter === 'PREPARING'}>발송 필요</button>
          <label className={s.statusSelectLabel}>
            <span className="sr-only">상태 선택</span>
            <select value={filter === 'PAID' || filter === 'PREPARING' ? '' : filter} onChange={(event) => changeFilter(event.target.value)} aria-label="상태 선택">
              <option value="">상태 선택</option>
              {ORDER_FILTERS.filter((option) => option.value && option.value !== 'PAID' && option.value !== 'PREPARING')
                .map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
        </div>

        <OrderListState
          loading={loading}
          isEmpty={items.length === 0}
          loadingMessage="판매 주문을 불러오는 중입니다."
          emptyMessage="조건에 맞는 판매 주문이 없습니다."
        >
          <div className={s.tableHead}>
            <span>작품</span><span>상태·금액</span><span>주문 관리</span>
          </div>
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
  const detailId = `seller-order-detail-${order.orderId}`
  const toggleLabel = `${order.artName} 주문 상세 ${isOpen ? '접기' : '보기'}`
  const showsPaymentTime = detail?.paidAt && [
    'PAID', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CONFIRMED',
  ].includes(detail.status)
  const showsPreparingTime = detail?.preparingAt && [
    'PREPARING', 'SHIPPED', 'DELIVERED', 'CONFIRMED',
  ].includes(detail.status)
  const hasDistinctBuyerPhone = detail?.buyerPhone
    && detail.buyerPhone !== detail.shippingAddress?.recipientPhone

  return (
    <article className={s.order}>
      <div className={s.orderRow}>
        <div className={s.orderMain}>
          <img
            src={getArtImageSrc(order.artImage)}
            alt={order.artName}
            onError={applyArtImageFallback}
          />
          <span className={s.orderInfo}>
            <strong>{order.artName}</strong>
            <span>구매자 {order.counterpartyName}</span>
            <span>{formatOrderDate(order.createdAt)}</span>
          </span>
        </div>
        <div className={s.coreInfo}>
          <OrderStatusBadge status={order.status} />
          <span className={s.price}>{formatOrderPrice(order.winningPrice)}</span>
        </div>
        <div
          className={s.actions}
          role="group"
          aria-label={`${order.artName} 주문 작업`}
        >
          {actions.includes(SELLER_ACTION.START_PREPARING) && (
            <button
              type="button"
              className={s.primaryAction}
              onClick={onPrepare}
              disabled={isProcessing}
            >
              배송 준비
            </button>
          )}
          {actions.includes(SELLER_ACTION.SHIP) && (
            <button
              type="button"
              className={s.primaryAction}
              onClick={onOpenShipping}
              disabled={isProcessing}
            >
              발송 처리
            </button>
          )}
          {(actions.includes(SELLER_ACTION.APPROVE_REFUND)
            || actions.includes(SELLER_ACTION.REJECT_REFUND)) && (
            <div className={s.refundActions} role="group" aria-label="환불 결정">
              <span>환불 결정</span>
              {actions.includes(SELLER_ACTION.APPROVE_REFUND) && (
                <button type="button" className={s.refundApprove} onClick={onApproveRefund} disabled={isProcessing}>
                  환불 승인
                </button>
              )}
              {actions.includes(SELLER_ACTION.REJECT_REFUND) && (
                <button type="button" className={s.refundReject} onClick={onRejectRefund} disabled={isProcessing}>
                  환불 거절
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      <button type="button" className={s.detailToggle} onClick={onToggle} aria-expanded={isOpen} aria-controls={detailId} aria-label={toggleLabel}>
        <span>주문 상세 정보</span>
        <svg
          className={`${s.detailChevron} ${isOpen ? s.detailChevronOpen : ''}`}
          viewBox="0 0 24 24"
          aria-hidden="true"
          focusable="false"
        >
          <path d="m9 18 6-6-6-6" />
        </svg>
      </button>

      {isOpen && (
        <div
          id={detailId}
          className={s.detail}
          aria-busy={isLoading && !detail}
        >
          {isLoading && !detail ? (
            <p>주문 상세를 불러오는 중입니다.</p>
          ) : detail ? (
            <>
              <section className={s.meta} aria-labelledby={`${detailId}-order`}>
                <h3 id={`${detailId}-order`}>주문 정보</h3>
                <Meta label="주문 번호" value={detail.orderNumber} />
                {detail.createdAt && <Meta label="주문 일자" value={formatOrderDate(detail.createdAt)} />}
                <Meta label="낙찰가" value={formatOrderPrice(detail.winningPrice)} />
                {showsPaymentTime && <Meta label="결제 시각" value={formatOrderDate(detail.paidAt)} />}
                {showsPreparingTime && <Meta label="준비 시각" value={formatOrderDate(detail.preparingAt)} />}
              </section>

              {detail.status === 'CANCELED' && detail.cancelReason && (
                <p className={s.statusNotice}>
                  취소 사유: {formatOrderCancelReason(detail.cancelReason)}
                </p>
              )}

              <section className={s.meta} aria-labelledby={`${detailId}-buyer-shipping`}>
                <h3 id={`${detailId}-buyer-shipping`}>구매자·배송 정보</h3>
                <Meta label="이름" value={detail.buyerName} />
                {hasDistinctBuyerPhone && <Meta label="구매자 연락처" value={detail.buyerPhone} />}
                {detail.shippingAddress ? (
                  <>
                    <Meta label="받는 분" value={detail.shippingAddress.recipientName} />
                    <Meta label="연락처" value={detail.shippingAddress.recipientPhone} />
                    <Meta label="주소" value={formatShippingAddress(detail.shippingAddress)} />
                  </>
                ) : (
                  <p className={s.emptyValue}>구매자가 아직 배송지를 확정하지 않았습니다.</p>
                )}
              </section>

              {(detail.shippedAt || detail.shippingCarrier || detail.trackingNumber) && (
                <section className={s.meta} aria-labelledby={`${detailId}-shipping`}>
                  <h3 id={`${detailId}-shipping`}>발송 정보</h3>
                  <Meta label="택배사" value={detail.shippingCarrier} />
                  <Meta label="송장번호" value={detail.trackingNumber} />
                  {detail.shippedAt && <Meta label="발송 시각" value={formatOrderDate(detail.shippedAt)} />}
                </section>
              )}

              {isShipping && (
                <ShippingForm
                  detailId={detailId}
                  form={shippingForm}
                  isProcessing={isProcessing}
                  onChange={onShippingChange}
                  onCancel={onShippingCancel}
                  onSubmit={onShip}
                />
              )}
              <SellerRefundStatus detail={detail} headingId={`${detailId}-refund`} />
              <div className={s.secondaryActions}>
                <Link className={s.artLink} to={`/auction/${detail.artId}`}>
                  작품 페이지로 이동
                </Link>
              </div>
            </>
          ) : null}

        </div>
      )}
    </article>
  )
}

function SummaryIcon({ type }) {
  const paths = {
    orders: <><rect x="5" y="4" width="14" height="16" rx="2" /><path d="M8 9h8M8 13h8M8 17h5" /></>,
    payment: <><path d="M4 7h16v11H4z" /><path d="M4 10h16M15 15h2" /></>,
    package: <><path d="m4 7.5 8-4.5 8 4.5v9L12 21l-8-4.5z" /><path d="m4 7.5 8 4.5 8-4.5M12 12v9" /></>,
    shipping: <><path d="M3 7h11v10H3zM14 10h4l3 3v4h-7z" /><circle cx="7" cy="18" r="2" /><circle cx="18" cy="18" r="2" /></>,
    confirm: <><circle cx="12" cy="12" r="8" /><path d="m8.5 12 2.3 2.3 4.7-4.8" /></>,
  }
  return <svg className={s.summaryIcon} viewBox="0 0 24 24" aria-hidden="true" focusable="false">{paths[type]}</svg>
}

function ShippingForm({
  detailId,
  form,
  isProcessing,
  onChange,
  onCancel,
  onSubmit,
}) {
  return (
    <form className={s.shippingForm} onSubmit={onSubmit} aria-busy={isProcessing}>
      <h3>발송 입력</h3>
      <p id={`${detailId}-shipping-help`}>
        택배사와 송장번호를 확인한 뒤 발송 정보를 저장해 주세요.
      </p>
      <label>
        택배사
        <input name="shippingCarrier" value={form.shippingCarrier} onChange={onChange} aria-describedby={`${detailId}-shipping-help`} maxLength={50} required />
      </label>
      <label>
        송장번호
        <input name="trackingNumber" value={form.trackingNumber} onChange={onChange} aria-describedby={`${detailId}-shipping-help`} maxLength={100} required />
      </label>
      <div>
        <button type="button" onClick={onCancel}>취소</button>
        <button type="submit" disabled={isProcessing}>
          {isProcessing ? '처리 중...' : '발송 저장'}
        </button>
      </div>
    </form>
  )
}

function Meta({ label, value }) {
  if (!value) return null
  return (
    <div className={s.metaRow}>
      <strong>{label}</strong>
      <span className={label === '낙찰가' ? s.metaPrice : ''}>{value}</span>
    </div>
  )
}

function SellerRefundStatus({ detail, headingId }) {
  if (!detail.refundRequestStatus) return null

  const isRequested = detail.refundRequestStatus === 'REQUESTED'
  const isApproved = detail.refundRequestStatus === 'APPROVED'
  const message = isRequested
    ? '구매자가 환불을 요청했습니다. 승인 또는 거절을 선택해 처리해 주세요.'
    : isApproved
      ? '환불이 승인되어 처리가 완료되었습니다.'
      : '환불이 거절되었습니다. 주문은 현재 상태로 계속 진행됩니다.'
  const processedAt = isApproved ? detail.refundedAt : detail.refundRejectedAt

  return (
    <section className={s.refundStatus} aria-labelledby={headingId}>
      <h3 id={headingId}>환불 정보</h3>
      <p>{message}</p>
      <dl>
        {detail.refundRequestReason && (
          <div><dt>요청 사유</dt><dd>{detail.refundRequestReason}</dd></div>
        )}
        {detail.refundRequestedAt && (
          <div><dt>요청 시각</dt><dd>{formatOrderDate(detail.refundRequestedAt)}</dd></div>
        )}
        {processedAt && (
          <div><dt>처리 시각</dt><dd>{formatOrderDate(processedAt)}</dd></div>
        )}
      </dl>
    </section>
  )
}
