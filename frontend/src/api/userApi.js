import api from './authApi'

// 마이페이지 프로필 정보 조회
export const getUserProfile = () => 
  api.get('/api/users/me')

// 마이페이지 프로필 정보 수정
export const updateUserProfile = (data) => 
  api.put('/api/users/me', data)

// 닉네임 중복 확인
export const checkNickname = (value) => 
  api.get('/api/check/nickname', { params: { value } })

// 마이페이지 찜 목록 조회
export const getMyLikes = ({ page = 0, size = 12 } = {}) =>
  api.get('/api/users/me/likes', { params: { page, size } })

// 작품 찜 상태 조회
export const getArtLikeStatus = (artId) =>
  api.get(`/api/arts/${artId}/like`)

// 작품 찜 등록
export const addArtLike = (artId) =>
  api.post(`/api/arts/${artId}/like`)

// 작품 찜 해제
export const removeArtLike = (artId) =>
  api.delete(`/api/arts/${artId}/like`)
