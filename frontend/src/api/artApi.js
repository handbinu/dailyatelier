import api from './authApi'

export const getArts = ({ page = 0, size = 12, signal } = {}) =>
  api.get('/api/arts', { params: { page, size }, signal })

export const getArt = (artId, { signal } = {}) =>
  api.get(`/api/arts/${artId}`, { signal })

export const getMyArts = ({ page = 0, size = 12, signal } = {}) =>
  api.get('/api/users/me/arts', { params: { page, size }, signal })

export const createArt = (data) =>
  api.post('/api/arts', data)
