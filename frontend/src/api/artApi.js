import api from './authApi'

export const createArt = (data) =>
  api.post('/api/arts', data)
