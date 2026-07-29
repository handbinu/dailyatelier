export const SELLER_ACTION = {
  START_PREPARING: 'START_PREPARING',
  SHIP: 'SHIP',
}

export const sellerActionLabel = (action) => {
  if (action === SELLER_ACTION.START_PREPARING) return '배송 준비 시작'
  if (action === SELLER_ACTION.SHIP) return '발송 처리'
  return action || ''
}

export const buildSellerStatusRequest = (
  action,
  { shippingCarrier = '', trackingNumber = '' } = {},
) => {
  if (action === SELLER_ACTION.START_PREPARING) {
    return { status: 'PREPARING' }
  }

  if (action === SELLER_ACTION.SHIP) {
    const carrier = shippingCarrier.trim()
    const tracking = trackingNumber.trim()
    if (!carrier || !tracking) {
      throw new Error('택배사와 송장번호를 모두 입력해 주세요.')
    }
    return {
      status: 'SHIPPED',
      shippingCarrier: carrier,
      trackingNumber: tracking,
    }
  }

  throw new Error('처리할 수 없는 판매 주문 작업입니다.')
}
