import api from './authApi'

export const getBuyerOrders = ({
  status,
  page = 0,
  size = 12,
  signal,
} = {}) =>
  api.get('/api/users/me/orders', {
    params: {
      ...(status ? { status } : {}),
      page,
      size,
    },
    signal,
  })

export const getBuyerOrder = (orderId, { signal } = {}) =>
  api.get(`/api/users/me/orders/${orderId}`, { signal })

export const updateOrderShippingAddress = (orderId, data) =>
  api.put(`/api/users/me/orders/${orderId}/shipping-address`, data)

export const cancelBuyerOrder = (orderId) =>
  api.post(`/api/users/me/orders/${orderId}/cancel`)

export const confirmBuyerOrder = (orderId) =>
  api.post(`/api/users/me/orders/${orderId}/confirm`)

export const markBuyerOrderDelivered = (orderId) =>
  api.post(`/api/users/me/orders/${orderId}/delivered`)

export const requestBuyerOrderRefund = (orderId, reason) =>
  api.post(`/api/users/me/orders/${orderId}/refund-request`, { reason })

export const payBuyerOrder = (orderId, idempotencyKey) =>
  api.post(
    `/api/users/me/orders/${orderId}/payment`,
    null,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )

export const getSellerOrders = ({
  status,
  page = 0,
  size = 12,
  signal,
} = {}) =>
  api.get('/api/artists/me/orders', {
    params: {
      ...(status ? { status } : {}),
      page,
      size,
    },
    signal,
  })

export const getSellerOrder = (orderId, { signal } = {}) =>
  api.get(`/api/artists/me/orders/${orderId}`, { signal })

export const updateSellerOrderStatus = (orderId, data) =>
  api.patch(`/api/artists/me/orders/${orderId}/status`, data)

export const approveSellerOrderRefund = (orderId, idempotencyKey) =>
  api.post(`/api/artists/me/orders/${orderId}/refund/approve`, null, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })

export const rejectSellerOrderRefund = (orderId) =>
  api.post(`/api/artists/me/orders/${orderId}/refund/reject`)
