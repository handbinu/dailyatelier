import axios from 'axios'

//Axios 기본 인스턴스
const api = axios.create({
    baseURL: 'http://localhost:8080',
    headers: {'Content-Type': 'application/json'},
})

//요청 인터셉터 - 토큰 자동 첨부
api.interceptors.request.use((config) => {
    const url = config.url || ''
    if (url.startsWith('/api/auth/') || url.startsWith('/api/check/')) {
        return config
    }
    const token = localStorage.getItem('token')
    if(token){
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

// 중복 실행 방지 플래그
let isHandling = false

//응답 인터셉터
api.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error.response?.status
        if ((status === 401 || status === 403) && !isHandling) {
            isHandling = true
            ;['token', 'userId', 'nickname', 'userStatus'].forEach(k => localStorage.removeItem(k))
            alert('세션이 만료되었거나 권한이 없습니다. 다시 로그인해 주세요.')
            window.location.href = '/login'
            setTimeout(() => { isHandling = false }, 3000)
        }
        return Promise.reject(error)
    }
)


//── 인증 API ────────────────────────────────────────────────────

//로그인
export const login = (userId, password) =>
    api.post('/api/auth/login', {userId, password})

//일반 회원가입
export const registerUser = (data) =>
    api.post('/api/auth/register/user', data)

//작가 회원가입
export const registerArtist = (data) =>
    api.post('/api/auth/register/artist', data)

//아이디 중복확인
export const checkUserId = (value) =>
    api.get('/api/check/userId', {params: {value}})

//닉네임 중복확인
export const checkNickname = (value) =>
    api.get('/api/check/nickname', {params: {value}})

export default api
