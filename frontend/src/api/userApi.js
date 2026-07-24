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

// 마이페이지 입찰 현황 조회
export const getMyBids = ({ page = 0, size = 50 } = {}) =>
  api.get('/api/users/me/bids', { params: { page, size } })

// 상태별 집계와 필터가 전체 입찰 내역을 기준으로 동작하도록 모든 페이지 조회
export const getAllMyBids = async () => {
  const firstResponse = await getMyBids({ page: 0, size: 50 })
  const firstPage = firstResponse.data
  const totalPages = Number(firstPage?.totalPages ?? 0)

  if (totalPages <= 1) return firstPage?.content ?? []

  const remainingResponses = await Promise.all(
    Array.from(
      { length: totalPages - 1 },
      (_, index) => getMyBids({ page: index + 1, size: 50 }),
    ),
  )

  return [
    ...(firstPage?.content ?? []),
    ...remainingResponses.flatMap(({ data }) => data?.content ?? []),
  ]
}

// 작품 찜 상태 조회
export const getArtLikeStatus = (artId) =>
  api.get(`/api/arts/${artId}/like`)

// 작품 찜 등록
export const addArtLike = (artId) =>
  api.post(`/api/arts/${artId}/like`)

// 작품 찜 해제
export const removeArtLike = (artId) =>
  api.delete(`/api/arts/${artId}/like`)
