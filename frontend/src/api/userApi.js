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
