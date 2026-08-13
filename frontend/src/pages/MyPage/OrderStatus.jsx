import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import {
  cancelBuyerOrder,
  confirmBuyerOrder,
  getBuyerOrder,
  getBuyerOrders,
  markBuyerOrderDelivered,
  payBuyerOrder,
  requestBuyerOrderRefund,
  updateOrderShippingAddress,
} from '../../api/orderApi'
import { getUserProfile } from '../../api/userApi'
import { applyArtImageFallback, getArtImageSrc } from '../../utils/artImage'
import {
  formatOrderDate,
  formatOrderPrice,
  formatShippingAddress,
  getOrderError,
  getOrderStatusView,
  getRefundRequestStatusView,
  ORDER_FILTERS,
} from '../../utils/orderView'
import { createOrderRequestGuard } from '../../utils/orderRequestGuard'
import { createLoginState } from '../../utils/loginReturn'
import AccessibleDialog from '../../components/Dialog/AccessibleDialog'
import { PageBanner, PageWrap } from './components/atoms'
import {
  OrderFeedback,
  OrderListState,
  OrderStatusBadge,
} from './components/OrderCommon'
import s from './OrderStatus.module.css'

const PAGE_SIZE = 12
const STEPS = ['주문 접수', '결제 완료', '배송 준비', '배송 중', '배송 완료', '구매 확정']
const EMPTY_ADDRESS = {
  recipientName: '',
  recipientPhone: '',
  zipCode: '',
  address1: '',
  address2: '',
  saveAsDefault: false,
}
const ADDRESS_FIELDS = [
  ['recipientName', '받는 분'],
  ['recipientPhone', '연락처'],
  ['zipCode', '우편번호'],
  ['address1', '기본 주소'],
  ['address2', '상세 주소'],
]

const hasChangedAddress = (previous, next) => ADDRESS_FIELDS.some(([field]) =>
  String(previous?.[field] ?? '').trim() !== String(next?.[field] ?? '').trim())

const getInitialAddress = (order, profile) => {
  if (order?.shippingAddress) {
    return {
      ...EMPTY_ADDRESS,
      ...order.shippingAddress,
    }
  }

  return {
    ...EMPTY_ADDRESS,
    recipientName: profile?.name || '',
    recipientPhone: profile?.phoneNumber || '',
    zipCode: profile?.zipCode || '',
    address1: profile?.userAddress1 || '',
    address2: profile?.userAddress2 || '',
  }
}

export default function OrderStatus() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const targetArtId = searchParams.get('artId')
  const [filter, setFilter] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [details, setDetails] = useState({})
  const [openOrderId, setOpenOrderId] = useState(null)
  const [editingOrderId, setEditingOrderId] = useState(null)
  const [addressForm, setAddressForm] = useState(EMPTY_ADDRESS)
  const [loading, setLoading] = useState(true)
  const [detailLoadingId, setDetailLoadingId] = useState(null)
  const [processingId, setProcessingId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [addressConfirmation, setAddressConfirmation] = useState(null)
  const requestGuard = useRef(createOrderRequestGuard())

  const handleRequestError = useCallback((requestError, fallback) => {
    const orderError = getOrderError(requestError, fallback)
    if (orderError.shouldLogin) {
      navigate('/login', { replace: true, state: createLoginState(location) })
      return orderError
    }
    setError(orderError.message)
    return orderError
  }, [location, navigate])

  const loadOrders = useCallback(async ({ signal } = {}) => {
    setLoading(true)
    setError('')

    try {
      const { data } = await getBuyerOrders({
        status: filter || undefined,
        page,
        size: PAGE_SIZE,
        signal,
      })
      setResult(data)
    } catch (requestError) {
      if (requestError.code !== 'ERR_CANCELED') {
        setResult(null)
        handleRequestError(requestError, '주문 내역을 불러오지 못했습니다.')
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
      const { data } = await getBuyerOrder(orderId)
      setDetails((current) => ({ ...current, [orderId]: data }))
      return data
    } catch (requestError) {
      handleRequestError(requestError, '주문 상세를 불러오지 못했습니다.')
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

  useEffect(() => {
    if (!targetArtId || !result?.content?.length) return
    const matched = result.content.find(
      (order) => String(order.artId) === targetArtId,
    )
    if (!matched) return

    setOpenOrderId(matched.orderId)
    loadDetail(matched.orderId)
    setSearchParams({}, { replace: true })
  }, [loadDetail, result, setSearchParams, targetArtId])

  const summaryItems = useMemo(() => {
    const counts = result?.statusCounts ?? {}
    const total = Object.values(counts).reduce(
      (sum, value) => sum + Number(value || 0),
      0,
    )
    return [
      { label: '전체 주문', value: total, color: '#555' },
      {
        label: '결제 대기',
        value: counts.PAYMENT_PENDING ?? 0,
        color: '#c0622a',
      },
      {
        label: '배송 중',
        value: counts.SHIPPED ?? 0,
        color: '#c0622a',
      },
      {
        label: '배송 완료',
        value: counts.DELIVERED ?? 0,
        color: '#1e8c4f',
      },
    ]
  }, [result])

  const handleFilterChange = (nextFilter) => {
    setFilter(nextFilter)
    setPage(0)
    setOpenOrderId(null)
    setEditingOrderId(null)
    setNotice('')
  }

  const toggleDetail = async (orderId) => {
    if (openOrderId === orderId) {
      setOpenOrderId(null)
      setEditingOrderId(null)
      return
    }
    setOpenOrderId(orderId)
    setEditingOrderId(null)
    await loadDetail(orderId)
  }

  const startAddressEdit = async (orderId) => {
    setOpenOrderId(orderId)
    const detail = await loadDetail(orderId)
    if (!detail) return

    let profile = null
    if (!detail.shippingAddress) {
      try {
        const response = await getUserProfile()
        profile = response.data
      } catch (requestError) {
        handleRequestError(
          requestError,
          '기본 배송지를 불러오지 못했습니다. 직접 입력해 주세요.',
        )
      }
    }
    setAddressForm(getInitialAddress(detail, profile))
    setEditingOrderId(orderId)
  }

  const updateAddressField = (event) => {
    const { name, value, checked, type } = event.target
    setAddressForm((current) => ({
      ...current,
      [name]: type === 'checkbox' ? checked : value,
    }))
  }

  const saveAddress = async (orderId, nextAddress, { closeDialog = false } = {}) => {
    if (!requestGuard.current.begin(orderId)) return
    setProcessingId(orderId)
    setError('')
    setNotice('')

    try {
      await updateOrderShippingAddress(orderId, nextAddress)
      await Promise.all([
        loadDetail(orderId, { force: true }),
        loadOrders(),
      ])
      if (closeDialog) setAddressConfirmation(null)
      setEditingOrderId(null)
      setNotice('배송지를 확정했습니다.')
    } catch (requestError) {
      const orderError = handleRequestError(
        requestError,
        '배송지를 저장하지 못했습니다.',
      )
      if (closeDialog) setAddressConfirmation(null)
      if (orderError.shouldReload) {
        await Promise.all([
          loadDetail(orderId, { force: true }),
          loadOrders(),
        ])
        setError(orderError.message)
      }
    } finally {
      requestGuard.current.end(orderId)
      setProcessingId(null)
    }
  }

  const submitAddress = async (event, orderId) => {
    event.preventDefault()
    const order = result?.content?.find((item) => item.orderId === orderId)
    const nextAddress = { ...addressForm }

    if (order?.status === 'PAYMENT_PENDING' && order.shippingAddressConfirmed) {
      const latestDetail = await loadDetail(orderId, { force: true })
      if (!latestDetail) return
      const previousAddress = latestDetail?.shippingAddress
      const isServerConfirmed = Boolean(
        latestDetail?.addressConfirmedAt && previousAddress,
      )

      if (isServerConfirmed && hasChangedAddress(previousAddress, nextAddress)) {
        setAddressConfirmation({
          orderId,
          orderNumber: latestDetail.orderNumber || order.orderNumber,
          artName: latestDetail.artName || order.artName,
          previousAddress: getInitialAddress(latestDetail, null),
          nextAddress,
        })
        return
      }
    }

    await saveAddress(orderId, nextAddress)
  }

  const cancelAddressConfirmation = () => {
    if (!addressConfirmation || processingId === addressConfirmation.orderId) return
    setAddressForm(addressConfirmation.previousAddress)
    setAddressConfirmation(null)
  }

  const confirmAddressChange = () => {
    if (!addressConfirmation) return
    saveAddress(
      addressConfirmation.orderId,
      addressConfirmation.nextAddress,
      { closeDialog: true },
    )
  }

  const runOrderAction = async (orderId, action) => {
    if (action === 'CANCEL'
      && !window.confirm('이 낙찰을 포기하시겠습니까?')) {
      return
    }
    if (action === 'CONFIRM'
      && !window.confirm('작품을 수령하셨나요? 구매를 확정하시겠습니까?')) {
      return
    }
    let refundReason = ''
    if (action === 'REQUEST_REFUND') {
      refundReason = window.prompt('환불 요청 사유를 입력해 주세요.')?.trim() ?? ''
      if (!refundReason) return
    }
    if (!requestGuard.current.begin(orderId)) return

    setProcessingId(orderId)
    setError('')
    setNotice('')
    try {
      const request = action === 'CANCEL'
        ? cancelBuyerOrder(orderId)
        : action === 'PAY'
          ? payBuyerOrder(orderId, `order-payment:${orderId}`)
          : action === 'MARK_DELIVERED'
            ? markBuyerOrderDelivered(orderId)
            : action === 'REQUEST_REFUND'
              ? requestBuyerOrderRefund(orderId, refundReason)
              : confirmBuyerOrder(orderId)
      const { data } = await request
      setDetails((current) => ({ ...current, [orderId]: data }))
      await loadOrders()
      setNotice(action === 'CANCEL'
        ? '낙찰 포기가 처리되었습니다.'
        : action === 'PAY'
          ? '포인트 결제가 완료되었습니다.'
          : action === 'MARK_DELIVERED'
            ? '배송 완료로 처리했습니다. 작품을 확인한 뒤 구매를 확정해 주세요.'
            : action === 'REQUEST_REFUND'
              ? '환불을 요청했습니다. 판매자 처리 결과를 기다려 주세요.'
              : '구매가 확정되었습니다.')
    } catch (requestError) {
      const orderError = handleRequestError(
        requestError,
        '주문 상태를 변경하지 못했습니다.',
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

  const items = result?.content ?? []

  return (
    <PageWrap>
      <PageBanner title="주문 조회" crumb="주문 조회" />
      <div className={s.body}>
        <OrderFeedback
          notice={notice}
          error={error}
          onRetry={() => loadOrders()}
        />

        <div className={s.summary}>
          {summaryItems.map(({ label, value, color }) => (
            <div key={label} className={s.summaryItem} style={{ '--c': color }}>
              <span className={s.summaryValue}>{value}</span>
              <span className={s.summaryLabel}>{label}</span>
            </div>
          ))}
        </div>

        <div className={s.filterBar} role="group" aria-label="주문 상태 필터">
          {ORDER_FILTERS.map((option) => (
            <button
              type="button"
              key={option.value}
              className={filter === option.value ? s.filterActive : ''}
              onClick={() => handleFilterChange(option.value)}
              aria-pressed={filter === option.value}
            >
              {option.label}
            </button>
          ))}
        </div>

        <OrderListState
          loading={loading}
          isEmpty={items.length === 0}
          loadingMessage="주문 내역을 불러오는 중입니다."
          emptyMessage="주문 내역이 없습니다."
        >
          <>
            <div className={s.tableHead}>
              <span className={s.colInfo}>주문 정보</span>
              <span className={s.colPrice}>금액</span>
              <span className={s.colStatus}>상태</span>
              <span className={s.colAction}>관리</span>
            </div>
            <div className={s.list}>
              {items.map((order) => (
                <OrderItem
                  key={order.orderId}
                  order={order}
                  detail={details[order.orderId]}
                  isOpen={openOrderId === order.orderId}
                  isEditing={editingOrderId === order.orderId}
                  isDetailLoading={detailLoadingId === order.orderId}
                  isProcessing={processingId === order.orderId}
                  addressForm={addressForm}
                  onToggle={() => toggleDetail(order.orderId)}
                  onAddressEdit={() => startAddressEdit(order.orderId)}
                  onAddressCancel={() => setEditingOrderId(null)}
                  onAddressChange={updateAddressField}
                  onAddressSubmit={(event) =>
                    submitAddress(event, order.orderId)}
                  onAction={(action) =>
                    runOrderAction(order.orderId, action)}
                />
              ))}
            </div>
          </>
        </OrderListState>

        {result?.totalPages > 1 && (
          <nav className={s.pagination} aria-label="주문 목록 페이지">
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
          <Link to="/mypage" className={s.backLink}>
            ← 마이페이지로 돌아가기
          </Link>
        </div>
      </div>
      {addressConfirmation && (
        <ShippingAddressConfirmationDialog
          confirmation={addressConfirmation}
          saving={processingId === addressConfirmation.orderId}
          onCancel={cancelAddressConfirmation}
          onConfirm={confirmAddressChange}
        />
      )}
    </PageWrap>
  )
}

function OrderItem({
  order,
  detail,
  isOpen,
  isEditing,
  isDetailLoading,
  isProcessing,
  addressForm,
  onToggle,
  onAddressEdit,
  onAddressCancel,
  onAddressChange,
  onAddressSubmit,
  onAction,
}) {
  const actions = detail?.availableActions ?? order.availableActions ?? []
  const detailId = `buyer-order-detail-${order.orderId}`
  const toggleLabel = `${order.artName} 주문 상세 ${isOpen ? '접기' : '보기'}`

  return (
    <article className={s.orderGroup}>
      <div className={s.orderRow}>
        <div className={s.orderMain}>
          <span className={s.colInfo}>
            <img
              src={getArtImageSrc(order.artImage)}
              alt={order.artName}
              className={s.thumb}
              onError={applyArtImageFallback}
            />
            <span className={s.orderInfo}>
              <strong className={s.artName}>{order.artName}</strong>
              <span className={s.artist}>by {order.counterpartyName}</span>
              <span className={s.orderNo}>{order.orderNumber}</span>
              <span className={s.orderDate}>
                {formatOrderDate(order.createdAt)}
              </span>
            </span>
          </span>
          <span className={`${s.colPrice} ${s.price}`}>
            {formatOrderPrice(order.winningPrice)}
          </span>
          <span className={s.colStatus}>
            <OrderStatusBadge status={order.status} />
          </span>
        </div>
        <div
          className={`${s.colAction} ${s.actions}`}
          role="group"
          aria-label={`${order.artName} 주문 작업`}
        >
          {actions.includes('UPDATE_SHIPPING_ADDRESS') && (
            <button
              type="button"
              className={s.primaryBtn}
              onClick={onAddressEdit}
              disabled={isProcessing}
            >
              배송지 {order.shippingAddressConfirmed ? '변경' : '확정'}
            </button>
          )}
          {actions.includes('CANCEL') && (
            <button
              type="button"
              className={s.dangerBtn}
              onClick={() => onAction('CANCEL')}
              disabled={isProcessing}
            >
              낙찰 포기
            </button>
          )}
          {order.status === 'PAYMENT_PENDING' && order.shippingAddressConfirmed && (
            <button
              type="button"
              className={s.primaryBtn}
              onClick={() => onAction('PAY')}
              disabled={isProcessing}
            >
              포인트 결제
            </button>
          )}
          {actions.includes('CONFIRM') && (
            <button
              type="button"
              className={s.primaryBtn}
              onClick={() => onAction('CONFIRM')}
              disabled={isProcessing}
            >
              구매 확정
            </button>
          )}
          {actions.includes('MARK_DELIVERED') && (
            <button
              type="button"
              className={s.primaryBtn}
              onClick={() => onAction('MARK_DELIVERED')}
              disabled={isProcessing}
            >
              배송 완료
            </button>
          )}
          {actions.includes('REQUEST_REFUND') && (
            <button
              type="button"
              className={s.dangerBtn}
              onClick={() => onAction('REQUEST_REFUND')}
              disabled={isProcessing}
            >
              환불 요청
            </button>
          )}
          <button
            type="button"
            className={s.detailToggle}
            aria-expanded={isOpen}
            aria-controls={detailId}
            onClick={onToggle}
            aria-label={toggleLabel}
          >
            {isOpen ? '상세 접기' : '상세 보기'}
          </button>
        </div>
      </div>

      {isOpen && (
        <div
          id={detailId}
          className={s.detailPanel}
          aria-busy={isDetailLoading && !detail}
        >
          {isDetailLoading && !detail ? (
            <p className={s.detailLoading}>주문 상세를 불러오는 중입니다.</p>
          ) : detail ? (
            <>
              <OrderProgress status={detail.status} />
              <BuyerRefundStatus detail={detail} />
              <OrderMeta detail={detail} />

              {isEditing && (
                <ShippingAddressForm
                  form={addressForm}
                  saving={isProcessing}
                  onChange={onAddressChange}
                  onSubmit={onAddressSubmit}
                  onCancel={onAddressCancel}
                />
              )}

              <div className={s.detailActions}>
                <Link to={`/auction/${detail.artId}`} className={s.linkBtn}>
                  작품 페이지
                </Link>
                {detail.status === 'CONFIRMED' && (
                  <Link
                    to={`/write-review/${detail.artId}`}
                    className={`${s.linkBtn} ${s.linkBtnAccent}`}
                  >
                    리뷰 쓰기
                  </Link>
                )}
              </div>
            </>
          ) : null}
        </div>
      )}
    </article>
  )
}

function OrderProgress({ status }) {
  const current = getOrderStatusView(status).step
  if (current === null) {
    const message = status === 'REFUNDED'
      ? '환불된 주문입니다.'
      : '취소된 주문입니다.'
    return <div className={s.canceledNote}>{message}</div>
  }

  return (
    <div
      className={s.progressWrap}
      role="group"
      aria-label={`주문 진행 단계: 현재 ${STEPS[current]}`}
    >
      <div className={s.progressTrack}>
        {STEPS.map((step, index) => {
          const done = index < current
          const active = index === current
          return (
            <div key={step} className={s.progressStep}>
              <div
                className={[
                  s.stepDot,
                  done || active ? s.stepDotDone : '',
                  active ? s.stepDotActive : '',
                ].join(' ')}
              >
                {done ? '✓' : index + 1}
              </div>
              <div className={`${s.stepLine} ${done ? s.stepLineDone : ''}`} />
              <p className={`${s.stepLabel} ${active ? s.stepLabelActive : ''}`}>
                {step}
              </p>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function OrderMeta({ detail }) {
  const statusTimes = [
    ['결제 완료', detail.paidAt],
    ['배송 준비', detail.preparingAt],
    ['발송', detail.shippedAt],
    ['배송 완료', detail.deliveredAt],
    ['구매 확정', detail.confirmedAt],
    ['취소', detail.canceledAt],
  ].filter(([, value]) => value)

  return (
    <div className={s.detailMeta}>
      <MetaRow label="주문 번호" value={detail.orderNumber} />
      <MetaRow label="주문 일자" value={formatOrderDate(detail.createdAt)} />
      <MetaRow
        label="결제 금액"
        value={formatOrderPrice(detail.winningPrice)}
        accent
      />
      {detail.status === 'PAYMENT_PENDING' && (
        <MetaRow
          label="결제 기한"
          value={formatOrderDate(detail.paymentDueAt)}
        />
      )}
      <MetaRow
        label="받는 분"
        value={detail.shippingAddress
          ? `${detail.shippingAddress.recipientName} · ${detail.shippingAddress.recipientPhone}`
          : '배송지 미확정'}
      />
      <MetaRow
        label="배송 주소"
        value={formatShippingAddress(detail.shippingAddress)}
      />
      {detail.trackingNumber && (
        <MetaRow
          label="배송 정보"
          value={`${detail.shippingCarrier} · ${detail.trackingNumber}`}
        />
      )}
      {detail.cancelReason && (
        <MetaRow label="취소 사유" value={detail.cancelReason} />
      )}
      {statusTimes.map(([label, value]) => (
        <MetaRow
          key={label}
          label={`${label} 시각`}
          value={formatOrderDate(value)}
        />
      ))}
    </div>
  )
}

function BuyerRefundStatus({ detail }) {
  const view = getRefundRequestStatusView(detail.refundRequestStatus)
  if (!view) return null

  const isRequested = detail.refundRequestStatus === 'REQUESTED'
  const isApproved = detail.refundRequestStatus === 'APPROVED'
  const message = isRequested
    ? '판매자의 확인을 기다리고 있습니다. 현재 추가로 할 작업은 없습니다.'
    : isApproved
      ? '환불이 완료되었습니다. 추가로 할 작업은 없습니다.'
      : '환불 요청이 거절되어 주문은 위에 표시된 현재 상태로 계속 진행됩니다. 현재 처리 중인 환불은 없습니다.'
  const processedAt = isApproved ? detail.refundedAt : detail.refundRejectedAt

  return (
    <section className={s.refundStatus} aria-label="환불 요청 상태">
      <strong className={s.refundStatusTitle}>{view.label}</strong>
      <p>{message}</p>
      <dl className={s.refundStatusMeta}>
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

function MetaRow({ label, value, accent = false }) {
  return (
    <div className={s.metaRow}>
      <span className={s.metaKey}>{label}</span>
      <span className={`${s.metaVal} ${accent ? s.metaPrice : ''}`}>
        {value}
      </span>
    </div>
  )
}

function ShippingAddressForm({
  form,
  saving,
  onChange,
  onSubmit,
  onCancel,
}) {
  return (
    <form className={s.addressForm} onSubmit={onSubmit}>
      <div className={s.addressHeading}>
        <div>
          <h3>배송지 확인</h3>
          <p>우편번호 5자리와 받는 분 정보를 확인해 주세요.</p>
        </div>
      </div>
      <div className={s.addressGrid}>
        <label>
          받는 분
          <input
            name="recipientName"
            value={form.recipientName}
            onChange={onChange}
            maxLength={50}
            required
          />
        </label>
        <label>
          연락처
          <input
            name="recipientPhone"
            value={form.recipientPhone}
            onChange={onChange}
            maxLength={30}
            required
          />
        </label>
        <label>
          우편번호
          <input
            name="zipCode"
            value={form.zipCode}
            onChange={onChange}
            inputMode="numeric"
            pattern="\d{5}"
            maxLength={5}
            placeholder="00000"
            required
          />
        </label>
        <label className={s.addressWide}>
          기본 주소
          <input
            name="address1"
            value={form.address1}
            onChange={onChange}
            maxLength={100}
            required
          />
        </label>
        <label className={s.addressWide}>
          상세 주소
          <input
            name="address2"
            value={form.address2}
            onChange={onChange}
            maxLength={100}
          />
        </label>
      </div>
      <label className={s.defaultCheck}>
        <input
          type="checkbox"
          name="saveAsDefault"
          checked={form.saveAsDefault}
          onChange={onChange}
        />
        이 배송지를 기본 배송지로 저장
      </label>
      <div className={s.formActions}>
        <button type="button" onClick={onCancel} disabled={saving}>
          닫기
        </button>
        <button type="submit" disabled={saving}>
          {saving ? '저장 중…' : '배송지 확정'}
        </button>
      </div>
    </form>
  )
}

function ShippingAddressConfirmationDialog({
  confirmation,
  saving,
  onCancel,
  onConfirm,
}) {
  const titleId = `shipping-confirmation-title-${confirmation.orderId}`
  const descriptionId = `shipping-confirmation-description-${confirmation.orderId}`

  return (
    <AccessibleDialog
      onClose={onCancel}
      labelledBy={titleId}
      describedBy={descriptionId}
      overlayClassName={s.confirmationOverlay}
      contentClassName={s.confirmationDialog}
      closeOnBackdrop={!saving}
    >
      <div className={s.confirmationHeading}>
        <h2 id={titleId}>배송지 변경을 확정할까요?</h2>
        <p id={descriptionId}>
          결제와 배송이 시작되기 전에 이 주문의 확정 배송지가 변경됩니다.
        </p>
      </div>

      <dl className={s.confirmationOrder}>
        <div><dt>작품</dt><dd>{confirmation.artName}</dd></div>
        <div><dt>주문 번호</dt><dd>{confirmation.orderNumber}</dd></div>
      </dl>

      <div className={s.addressComparison}>
        <div className={s.comparisonHeader} aria-hidden="true">
          <span>항목</span><span>기존 배송지</span><span>변경 배송지</span>
        </div>
        {ADDRESS_FIELDS.map(([field, label]) => {
          const previousRawValue = confirmation.previousAddress[field] ?? ''
          const nextRawValue = confirmation.nextAddress[field] ?? ''
          const previousValue = previousRawValue || '-'
          const nextValue = nextRawValue || '-'
          const changed = String(previousRawValue).trim()
            !== String(nextRawValue).trim()
          return (
            <div
              key={field}
              className={`${s.comparisonRow} ${changed ? s.comparisonChanged : ''}`}
            >
              <strong>{label}{changed ? ' (변경됨)' : ''}</strong>
              <div><span>기존</span><p>{previousValue}</p></div>
              <div><span>변경</span><p>{nextValue}</p></div>
            </div>
          )
        })}
      </div>

      <p className={s.cancelNotice}>
        취소하면 입력한 변경 내용은 사라지고 마지막으로 확정된 배송지로 복원됩니다.
      </p>
      <div className={s.confirmationActions} aria-busy={saving}>
        <button
          type="button"
          data-dialog-initial-focus
          onClick={onCancel}
          disabled={saving}
        >
          취소
        </button>
        <button type="button" onClick={onConfirm} disabled={saving}>
          {saving ? '저장 중…' : '배송지 변경 확정'}
        </button>
      </div>
    </AccessibleDialog>
  )
}
