import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { login } from '../../api/authApi'
import styles from './Login.module.css'

function Login() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ userId: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      const res = await login(form.userId, form.password)
      localStorage.setItem('token',      res.data.token)
      localStorage.setItem('userId',     res.data.userId)
      localStorage.setItem('nickname',   res.data.nickname)
      localStorage.setItem('userStatus', res.data.userStatus)
      navigate('/')
    } catch (err) {
      setError(
        err.response?.data?.message || '아이디 또는 비밀번호가 올바르지 않습니다.'
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.wrap}>
      {/* ── 카드 박스 ── */}
      <div className={styles.card}>
        <h1 className={styles.title}>Login</h1>

        <form className={styles.form} onSubmit={handleSubmit}>
          <div className={styles.field}>
            <label htmlFor="login-user-id">아이디</label>
            <input
              id="login-user-id"
              type="text"
              name="userId"
              placeholder="아이디"
              value={form.userId}
              onChange={handleChange}
              required
              autoComplete="username"
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="login-password">비밀번호</label>
            <input
              id="login-password"
              type="password"
              name="password"
              placeholder="비밀번호"
              value={form.password}
              onChange={handleChange}
              required
              autoComplete="current-password"
            />
          </div>

          <div className={styles.errorWrap}>
            {error && <p className={styles.error} role="alert">{error}</p>}
          </div>

          <button
            type="submit"
            className={styles.btnSubmit}
            disabled={loading}
          >
            {loading ? '로그인 중…' : '로그인'}
          </button>
        </form>

        <div className={styles.links}>
          <span>계정이 없으신가요?</span>
          <Link to="/register">회원가입</Link>
        </div>
        <div className={styles.quickLinks}>
          <Link to="/">홈으로</Link>
          <Link to="/auction/total">경매 둘러보기</Link>
        </div>
      </div>
    </div>
  )
}

export default Login
