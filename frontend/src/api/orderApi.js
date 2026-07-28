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
