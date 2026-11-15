import api from './authApi'

export const getPointSummary = ({ signal } = {}) =>
  api.get('/api/users/me/points', { signal })

export const getPointTransactions = ({ page = 0, size = 20, signal } = {}) =>
  api.get('/api/users/me/points/transactions', {
    params: { page, size },
    signal,
  })

export const getPointCharges = ({ page = 0, size = 20, signal } = {}) =>
  api.get('/api/users/me/points/charges', {
    params: { page, size },
    signal,
  })

export const chargePoint = (amount, idempotencyKey) =>
  api.post(
    '/api/users/me/points/charges',
    { amount },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
