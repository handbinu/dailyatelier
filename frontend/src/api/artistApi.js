import api from './authApi'

export const getArtists = ({ keyword = '', page = 0, size = 12, signal } = {}) =>
  api.get('/api/artists', { params: { keyword, page, size }, signal })

export const getArtist = (artistId, { signal } = {}) =>
  api.get(`/api/artists/${artistId}`, { signal })

export const getArtistArts = (artistId, { page = 0, size = 12, signal } = {}) =>
  api.get(`/api/artists/${artistId}/arts`, { params: { page, size }, signal })
