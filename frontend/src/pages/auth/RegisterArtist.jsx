import { useRef, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { registerArtist, checkUserId, checkNickname } from '../../api/authApi'
import styles from './RegisterForm.module.css'
import useDuplicateCheck from './useDuplicateCheck'

const duplicateCheckers = { userId: checkUserId, nickname: checkNickname }

function RegisterArtist(){
    const navigator = useNavigate()

    const [form, setForm] = useState({
        userId: '', password:'', name: '',
        nickname: '', phoneNumber:'', email:'',
        userStatus: 1,
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
            setError('활동명 중복확인을 해주세요.')
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
            await registerArtist(form)
            navigator('/login')
        }catch(err){
            setError(err.response?.data?.message || '회원가입 중 오류가 발생했습니다.')
        } finally {
            setLoading(false)
        }
    }

    return(
        <div className={styles.wrap}>
            <h1 className={styles.title}>작가 회원가입</h1>
            <hr className={styles.hr} />
            <p className={styles.notice}>*는 필수 입력</p>

            <form onSubmit={handleSubmit}>
                <p className={styles.sectionLabel}>계정 정보</p>

                {/* 아이디 */}
                <div className={styles.field}>
                    <label htmlFor="artist-register-id">아이디 <span aria-hidden="true">*</span></label>
                    <div className={styles.inputRow}>
                        <input
                            ref={userIdRef} id="artist-register-id"
                            type="text" name="userId" placeholder="사용할 아이디"
                            value={form.userId} onChange={handleChange} required
                            autoComplete="username" aria-describedby="artist-register-id-message"
                            aria-invalid={Boolean(messages.userId && !checked.userId)}
                        />
                        <button type="button" className={styles.btnCheck}
                            onClick={() => check('userId')}
                            disabled={checking.userId}>
                            {checking.userId ? '확인 중...' : '중복확인'}
                        </button>
                    </div>
                    <p id="artist-register-id-message" aria-live="polite" className={`${styles.msg} ${checked.userId ? styles.ok : styles.err}`}>
                        {messages.userId}
                    </p>
                </div>

                {/* 비밀번호 */}
                <div className={styles.field}>
                    <label htmlFor="artist-register-password">비밀번호 <span aria-hidden="true">*</span></label>
                    <input
                        id="artist-register-password"
                        type="password" name="password"
                        placeholder="문자·숫자·특수문자 포함 8~20자"
                        value={form.password} onChange={handleChange} required
                        autoComplete="new-password"
                    />
                </div>

                {/* 비밀번호 중복 확인 */}
                <div className={styles.field}>
                    <label htmlFor="artist-register-password-confirm">비밀번호 재입력 <span aria-hidden="true">*</span></label>
                    <input
                        ref={pwConfirmRef} id="artist-register-password-confirm"
                        type="password" placeholder="비밀번호 재입력"
                        value={pwConfirm} onChange={handlePwConfirm} required
                        autoComplete="new-password" aria-describedby="artist-register-password-message"
                        aria-invalid={Boolean(pwConfirm && form.password !== pwConfirm)}
                    />
                    <p id="artist-register-password-message" aria-live="polite" className={`${styles.msg} ${form.password === pwConfirm && pwConfirm ? styles.ok : styles.err}`}>
                        {pwMsg}
                    </p>
                </div>

                <p className={styles.sectionLabel}>작가 정보</p>

               
                {/*활동명(닉네임) */}
                <div className={styles.field}>
                    <label htmlFor="artist-register-nickname">활동명 <span aria-hidden="true">*</span></label>
                    <div className={styles.inputRow}>
                        <input
                            ref={nicknameRef} id="artist-register-nickname"
                            type="text" name="nickname" placeholder="닉네임, 10자 이내"
                            value={form.nickname} onChange={handleChange} 
                            required maxLength={10}
                            aria-describedby="artist-register-nickname-message"
                            aria-invalid={Boolean(messages.nickname && !checked.nickname)}
                        />
                        <button type="button" className={styles.btnCheck}
                            onClick={() => check('nickname')}
                            disabled={checking.nickname}>
                            {checking.nickname ? '확인 중...' : '중복확인'}
                        </button>
                    </div>
                    <p id="artist-register-nickname-message" aria-live="polite" className={`${styles.msg} ${checked.nickname ? styles.ok : styles.err}`}>
                        {messages.nickname}
                    </p>
                </div>

                {/* 작가명 */}
                <div className={styles.field}>
                    <label htmlFor="artist-register-name">작가명 <span className={styles.optional}>(선택)</span></label>
                    <input
                        id="artist-register-name" type="text" name="artistName"
                        placeholder="작가명 (미입력 시 활동명 자동 적용)"
                        value={form.artistName} onChange={handleChange} 
                        maxLength={50}
                        aria-describedby="artist-register-name-hint"
                    />
                     <p id="artist-register-name-hint" className={styles.hint}>활동명과 다른 작가 전용 이름을 쓸 경우 입력</p>
                </div>

                {/* 홈페이지 */}
                <div className={styles.field}>
                    <label htmlFor="artist-register-homepage">개인 홈페이지 <span className={styles.optional}>(선택)</span></label>
                    <input
                        id="artist-register-homepage" type="url" name="homepage"
                        placeholder="https://example.com"
                        value={form.homepage} onChange={handleChange}
                        autoComplete="url"
                    />
                </div>
                
                {/* SNS */}
                <div className={styles.field}>
                    <label htmlFor="artist-register-sns">SNS 아이디 <span className={styles.optional}>(선택)</span></label>
                    <input
                        id="artist-register-sns" type="text" name="artistSns"
                        placeholder="예: @daily_art"
                        value={form.artistSns} onChange={handleChange}
                    />
                </div>

                <p className={styles.sectionLabel}>개인 정보</p>

                {/* 실명 */}
                <div className={styles.field}>
                    <label htmlFor="artist-register-real-name">이름 <span aria-hidden="true">*</span></label>
                    <input
                        id="artist-register-real-name" type="text" name="name" placeholder="실명"
                        value={form.name} onChange={handleChange} required
                        autoComplete="name"
                    />
                </div>

                {/* 전화번호 */}
                <div className={styles.field}>
                    <label htmlFor="artist-register-phone">전화번호 <span aria-hidden="true">*</span></label>
                    <input
                        id="artist-register-phone" type="tel" name="phoneNumber" placeholder="- 없이 입력"
                        value={form.phoneNumber} onChange={handleChange} required
                        autoComplete="tel"
                    />
                </div>

                {/* 이메일 */}
                <div className={styles.field}>
                    <label htmlFor="artist-register-email">이메일 <span aria-hidden="true">*</span></label>
                    <input
                        id="artist-register-email" type="email" name="email" placeholder="example@email.com"
                        value={form.email} onChange={handleChange} required
                        autoComplete="email"
                    />
                </div>

                {error && <p className={styles.errMsg} role="alert">{error}</p>}

                <button type="submit" className={styles.btnSubmit}
                    disabled={loading || checking.userId || checking.nickname
                        || !checked.userId || !checked.nickname}>
                    {loading ? '처리 중...' : '작가로 가입하기'}
                </button>
            </form>

            <div className={styles.loginLink}>
                이미 계정이 있으신가요? <Link to="/login">로그인</Link>
            </div>
        </div>
    )
}

export default RegisterArtist
