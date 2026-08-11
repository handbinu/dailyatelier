import api from './authApi'

export const getArts = ({ page = 0, size = 12, signal } = {}) =>
  api.get('/api/arts', { params: { page, size }, signal })

export const searchArts = ({ q, artist, format, category, status, sort, page = 0, size = 12, signal } = {}) =>
  api.get('/api/arts/search', {
    params: { q, artist, format, category, status, sort, page, size },
    signal,
  })

export const getArt = (artId, { signal } = {}) =>
  api.get(`/api/arts/${artId}`, { signal })

export const getMyArts = ({ state = 'ALL', page = 0, size = 12, signal } = {}) =>
  api.get('/api/users/me/arts', { params: { state, page, size }, signal })

export const createArt = (data) =>
  api.post('/api/arts', data)

export const createBid = (artId, bidPrice) =>
  api.post(`/api/arts/${artId}/bids`, { bidPrice })
