export const ORDER_STATUS = {
  PAYMENT_PENDING: {
    label: '결제 대기',
    color: 'orange',
    step: 0,
  },
  PAID: {
    label: '결제 완료',
    color: 'blue',
    step: 1,
  },
  PREPARING: {
    label: '배송 준비',
    color: 'blue',
    step: 2,
  },
  SHIPPED: {
    label: '배송 중',
    color: 'orange',
    step: 3,
  },
  DELIVERED: {
    label: '배송 완료',
    color: 'green',
    step: 4,
  },
  CONFIRMED: {
    label: '구매 확정',
    color: 'green',
    step: 5,
  },
  CANCELED: {
    label: '취소',
    color: 'red',
    step: null,
  },
  REFUNDED: {
    label: '환불',
    color: 'gray',
    step: null,
  },
}

export const ORDER_FILTERS = [
  { label: '전체', value: '' },
  ...Object.entries(ORDER_STATUS).map(([value, config]) => ({
    label: config.label,
    value,
  })),
]

const ERROR_MESSAGES = {
  ORDER_NOT_FOUND: '주문을 찾을 수 없습니다.',
  ORDER_ACCESS_DENIED: '이 주문을 조회하거나 처리할 권한이 없습니다.',
  ORDER_STATUS_CONFLICT: '주문 상태가 변경되었습니다. 최신 정보를 확인해 주세요.',
  PAYMENT_DEADLINE_EXPIRED: '결제 기한이 만료된 주문입니다.',
  SHIPPING_ADDRESS_REQUIRED: '결제 전에 배송지를 확정해 주세요.',
  INVALID_SHIPPING_ADDRESS: '배송지 입력값을 확인해 주세요.',
}

export const getOrderStatusView = (status) =>
  ORDER_STATUS[status] ?? {
    label: status || '상태 미확인',
    color: 'gray',
    step: null,
  }

export const getOrderError = (error, fallback) => {
  const status = error?.response?.status
  const code = error?.response?.data?.code
  const message = error?.response?.data?.message

  return {
    status,
    code,
    message: ERROR_MESSAGES[code]
      || message
      || fallback
      || '주문 처리 중 오류가 발생했습니다.',
    shouldLogin: status === 401,
    shouldReload: status === 409,
  }
}

export const formatOrderDate = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'

  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export const formatOrderPrice = (value) =>
  `${Number(value ?? 0).toLocaleString('ko-KR')}원`

export const formatShippingAddress = (address) => {
  if (!address) return '배송지 미확정'
  return [
    `(${address.zipCode})`,
    address.address1,
    address.address2,
  ].filter(Boolean).join(' ')
}
