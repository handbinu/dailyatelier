import { useRef, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { registerUser, checkUserId, checkNickname } from '../../api/authApi'
import styles from './RegisterForm.module.css'
import useDuplicateCheck from './useDuplicateCheck'

const duplicateCheckers = { userId: checkUserId, nickname: checkNickname }

function RegisterUser(){
    const navigator = useNavigate()

    const [form, setForm] = useState({
        userId: '', password:'', name: '',
        nickname: '', phoneNumber:'', email:'',
        userStatus: 0,
    })
    const [pwConfirm, setPwConfirm] = useState('')
    const [pwMsg, setPwMsg] = useState('')
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)
    const userIdRef = useRef(null)
    const nicknameRef = useRef(null)
    const pwConfirmRef = useRef(null)
    const {
        checked,
        checking,
        messages,
        invalidate,
        check,
    } = useDuplicateCheck(form, duplicateCheckers)

    const handleChange = (e) => {
        const {name, value} = e.target
        setForm({...form, [name]: value})
        if(name === 'userId' || name === 'nickname') invalidate(name)
    }

    const handlePwConfirm = (e) => {
        setPwConfirm(e.target.value)
        if(e.target.value && form.password !== e.target.value){
            setPwMsg('비밀번호가 일치하지 않습니다.')
        } else if(e.target.value){
            setPwMsg('비밀번호가 일치합니다.')
        } else {
            setPwMsg('')
        }
    }
    const handleSubmit = async (e) => {
        e.preventDefault()
        setError('')
        if (!checked.userId) {
            setError('아이디 중복확인을 해주세요.')
            userIdRef.current?.focus()
            return
        }
        if (!checked.nickname) {
            setError('닉네임 중복확인을 해주세요.')
            nicknameRef.current?.focus()
            return
        }
        if (form.password !== pwConfirm) {
            setError('비밀번호가 일치하지 않습니다.')
            pwConfirmRef.current?.focus()
            return
        }
    
        setLoading(true)
        try{
            await registerUser(form)
            navigator('/login')
        }catch(err){
            setError(err.response?.data?.message || '회원가입 중 오류가 발생했습니다.')
        } finally {
            setLoading(false)
        }
    }

    return(
        <div className={styles.wrap}>
            <h1 className={styles.title}>일반 회원가입</h1>
            <hr className={styles.hr} />
            <p className={styles.notice}>*는 필수 입력</p>

            <form onSubmit={handleSubmit}>
                <p className={styles.sectionLabel}>계정 정보</p>

                {/* 아이디 */}
                <div className={styles.field}>
                    <label htmlFor="user-register-id">아이디 <span aria-hidden="true">*</span></label>
                    <div className={styles.inputRow}>
                        <input
                            ref={userIdRef} id="user-register-id"
                            type="text" name="userId" placeholder="사용할 아이디"
                            value={form.userId} onChange={handleChange} required
                            autoComplete="username" aria-describedby="user-register-id-message"
                            aria-invalid={Boolean(messages.userId && !checked.userId)}
                        />
                        <button type="button" className={styles.btnCheck}
                            onClick={() => check('userId')}
                            disabled={checking.userId}>
                            {checking.userId ? '확인 중...' : '중복확인'}
                        </button>
                    </div>
                    <p id="user-register-id-message" aria-live="polite" className={`${styles.msg} ${checked.userId ? styles.ok : styles.err}`}>



                        {messages.userId}
                    </p>
                </div>

                {/* 비밀번호 */}
                <div className={styles.field}>
                    <label htmlFor="user-register-password">비밀번호 <span aria-hidden="true">*</span></label>
                    <input
                        id="user-register-password"
                        type="password" name="password"
                        placeholder="문자·숫자·특수문자 포함 8~20자"
                        value={form.password} onChange={handleChange} required
                        autoComplete="new-password"
                    />
                </div>

                {/* 비밀번호 중복 확인 */}
                <div className={styles.field}>
                    <label htmlFor="user-register-password-confirm">비밀번호 재입력 <span aria-hidden="true">*</span></label>
                    <input
                        ref={pwConfirmRef} id="user-register-password-confirm"
                        type="password" placeholder="비밀번호 재입력"
                        value={pwConfirm} onChange={handlePwConfirm} required
                        autoComplete="new-password" aria-describedby="user-register-password-message"
                        aria-invalid={Boolean(pwConfirm && form.password !== pwConfirm)}
                    />
                    <p id="user-register-password-message" aria-live="polite" className={`${styles.msg} ${form.password === pwConfirm && pwConfirm ? styles.ok : styles.err}`}>
                        {pwMsg}
                    </p>
                </div>

                <p className={styles.sectionLabel}>개인 정보</p>

                {/* 이름 */}
                <div className={styles.field}>
                    <label htmlFor="user-register-name">이름 <span aria-hidden="true">*</span></label>
                    <input
                        id="user-register-name" type="text" name="name" placeholder="실명"
                        value={form.name} onChange={handleChange} required
                        autoComplete="name"
                    />
                </div>

                {/* 닉네임 */}
                <div className={styles.field}>
                    <label htmlFor="user-register-nickname">닉네임 <span aria-hidden="true">*</span></label>
                    <div className={styles.inputRow}>
                        <input
                            ref={nicknameRef} id="user-register-nickname"
                            type="text" name="nickname" placeholder="사용할 닉네임"
                            value={form.nickname} onChange={handleChange} required
                            aria-describedby="user-register-nickname-message"
                            aria-invalid={Boolean(messages.nickname && !checked.nickname)}
                        />
                        <button type="button" className={styles.btnCheck}
                            onClick={() => check('nickname')}
                            disabled={checking.nickname}>
                            {checking.nickname ? '확인 중...' : '중복확인'}
                        </button>
                    </div>
                    <p id="user-register-nickname-message" aria-live="polite" className={`${styles.msg} ${checked.nickname ? styles.ok : styles.err}`}>
                        {messages.nickname}
                    </p>
                </div>

                {/* 전화번호 */}
                <div className={styles.field}>
                    <label htmlFor="user-register-phone">전화번호 <span aria-hidden="true">*</span></label>
                    <input
                        id="user-register-phone" type="tel" name="phoneNumber" placeholder="- 없이 입력"
                        value={form.phoneNumber} onChange={handleChange} required
                        autoComplete="tel"
                    />
                </div>

                {/* 이메일 */}
                <div className={styles.field}>
                    <label htmlFor="user-register-email">이메일 <span aria-hidden="true">*</span></label>
                    <input
                        id="user-register-email" type="email" name="email" placeholder="example@email.com"
                        value={form.email} onChange={handleChange} required
                        autoComplete="email"
                    />
                </div>

                {error && <p className={styles.errMsg} role="alert">{error}</p>}

                <button type="submit" className={styles.btnSubmit}
                    disabled={loading || checking.userId || checking.nickname
                        || !checked.userId || !checked.nickname}>
                    {loading ? '처리 중...' : '가입하기'}
                </button>
            </form>

            <div className={styles.loginLink}>
                이미 계정이 있으신가요? <Link to="/login">로그인</Link>
            </div>
        </div>
    )
}

export default RegisterUser
