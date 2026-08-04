import { useState } from 'react'
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
        if (!checked.userId) { alert('아이디 중복확인을 해주세요.'); return }
        if  (!checked.nickname) {alert('닉네임 중복확인을 해주세요.'); return}
        if  (form.password !== pwConfirm) {alert('비밀번호가 일치하지 않습니다'); return}
    
        setLoading(true)
        try{
            await registerUser(form)
            alert('회원가입이 완료되었습니다!')
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
                    <div className={styles.inputRow}>
                        <input
                            type="text" name="userId" placeholder="* 아이디"
                            value={form.userId} onChange={handleChange} required
                        />
                        <button type="button" className={styles.btnCheck}
                            onClick={() => check('userId')}
                            disabled={checking.userId}>
                            {checking.userId ? '확인 중...' : '중복확인'}
                        </button>
                    </div>
                    <p className={`${styles.msg} ${checked.userId ? styles.ok : styles.err}`}>



                        {messages.userId}
                    </p>
                </div>

                {/* 비밀번호 */}
                <div className={styles.field}>
                    <input
                        type="password" name="password"
                        placeholder="* 비밀번호 (문자·숫자·특수문자 포함 8~20자)"
                        value={form.password} onChange={handleChange} required
                    />
                </div>

                {/* 비밀번호 중복 확인 */}
                <div className={styles.field}>
                    <input
                        type="password" placeholder="* 비밀번호 재입력"
                        value={pwConfirm} onChange={handlePwConfirm} required
                    />
                    <p className={`${styles.msg} ${form.password === pwConfirm && pwConfirm ? styles.ok : styles.err}`}>
                        {pwMsg}
                    </p>
                </div>

                <p className={styles.sectionLabel}>개인 정보</p>

                {/* 이름 */}
                <div className={styles.field}>
                    <input
                        type="text" name="name" placeholder="* 이름 (실명)"
                        value={form.name} onChange={handleChange} required
                    />
                </div>

                {/* 닉네임 */}
                <div className={styles.field}>
                    <div className={styles.inputRow}>
                        <input
                            type="text" name="nickname" placeholder="* 닉네임"
                            value={form.nickname} onChange={handleChange} required
                        />
                        <button type="button" className={styles.btnCheck}
                            onClick={() => check('nickname')}
                            disabled={checking.nickname}>
                            {checking.nickname ? '확인 중...' : '중복확인'}
                        </button>
                    </div>
                    <p className={`${styles.msg} ${checked.nickname ? styles.ok : styles.err}`}>
                        {messages.nickname}
                    </p>
                </div>

                {/* 전화번호 */}
                <div className={styles.field}>
                    <input
                        type="tel" name="phoneNumber" placeholder="* 전화번호 (-없이)"
                        value={form.phoneNumber} onChange={handleChange} required
                    />
                </div>

                {/* 이메일 */}
                <div className={styles.field}>
                    <input
                        type="email" name="email" placeholder="* 이메일"
                        value={form.email} onChange={handleChange} required
                    />
                </div>

                {error && <p className={styles.errMsg}>{error}</p>}

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
