import api from './authApi'

export const getOrderReview = (orderId, { signal } = {}) =>
  api.get(`/api/users/me/orders/${orderId}/review`, { signal })

export const createReview = ({ orderId, star, content }) =>
  api.post('/api/users/me/reviews', { orderId, star, content })

export const updateReview = (reviewId, { star, content }) =>
  api.put(`/api/users/me/reviews/${reviewId}`, { star, content })

export const getMyReviews = ({ sort = 'RECENT', page = 0, size = 6, signal } = {}) =>
  api.get('/api/users/me/reviews', { params: { sort, page, size }, signal })

export const getArtistReviews = ({ artId, sort = 'RECENT', page = 0, size = 6, signal } = {}) =>
  api.get('/api/artists/me/reviews', {
    params: { ...(artId == null ? {} : { artId }), sort, page, size },
    signal,
  })
