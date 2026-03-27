import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { registerArtist, checkUserId, checkNickname } from '../../api/authApi'
import styles from './RegisterForm.module.css'

function RegisterArtist(){
    const navigator = useNavigate()

    const [form, setForm] = useState({
        userId: '', password:'', name: '',
        nickname: '', phoneNumber:'', email:'',
        userStatus: 1,
    })
    const [pwConfirm, setPwConfirm] = useState('')
    const [checked, setChecked] = useState({ userId:false, nickname: false })
    const [msgs, setMsgs] = useState({ userId:'', nickname:'', pw:''})
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)

    const handleChange = (e) => {
        const {name, value} = e.target
        setForm({...form, [name]: value})
        if(name === 'userId') setChecked((c)=> ({...c, userId:false}))
        if(name === 'nickname') setChecked((c)=> ({...c, nickname:false}))
    }

    const handleDuplicateCheck = async (field) => {
        const value = form[field].trim()
        if (!value){
            setMsgs((m) => ({...m, [field]: '값을 입력해주세요.'}))
            return;
        }
        try {
            const res = field === 'userId'
            ? await checkUserId(value)
            : await checkNickname(value)

            if(res.data.duplicate){
                setMsgs((m) =>  ({...m, [field]: '이미 사용 중입니다'}))
                setChecked((c) => ({...c, [field]: false}))
            } else{
                setMsgs((m) =>  ({...m, [field]: '사용 가능합니다. ✓'}))
                setChecked((c) => ({...c, [field]: true}))
            }
        } catch{
            setMsgs((m) => ({...m, [field]: '확인 중 오류가 발생했습니다.'}))
        }
    }
    
    const handlePwConfirm = (e) => {
        setPwConfirm(e.target.value)
        if(e.target.value && form.password !== e.target.value){
            setMsgs((m) => ({...m, pw:'비밀번호가 일치하지 않습니다.'}))
        } else if(e.target.value){
            setMsgs((m) => ({...m, pw:'비밀번호가 일치합니다.'}))
        } else {
            setMsgs((m) => ({ ...m, pw: '' }))
        }
    }
    const handleSubmit = async (e) => {
        e.preventDefault()
        if (!checked.userId) { alert('아이디 중복확인을 해주세요.'); return }
        if  (!checked.nickname) {alert('활동명 중복확인을 해주세요.'); return}
        if  (form.password !== pwConfirm) {alert('비밀번호가 일치하지 않습니다'); return}
    
        setLoading(true)
        try{
            await registerArtist(form)
            alert('작가 회원가입이 완료되었습니다!')
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
                    <div className={styles.inputRow}>
                        <input
                            type="text" name="userId" placeholder="* 아이디"
                            value={form.userId} onChange={handleChange} required
                        />
                        <button type="button" className={styles.btnCheck}
                            onClick={() => handleDuplicateCheck('userId')}>
                            중복확인
                        </button>
                    </div>
                    <p className={`${styles.msg} ${checked.userId ? styles.ok : styles.err}`}>
                        {msgs.userId}
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
                        {msgs.pw}
                    </p>
                </div>

                <p className={styles.sectionLabel}>작가 정보</p>

               
                {/*활동명(닉네임) */}
                <div className={styles.field}>
                    <div className={styles.inputRow}>
                        <input
                            type="text" name="nickname"
                            placeholder="* 활동명 (닉네임, 10자 이내)"
                            value={form.nickname} onChange={handleChange} 
                            required maxLength={10}
                        />
                        <button type="button" className={styles.btnCheck}
                            onClick={() => handleDuplicateCheck('nickname')}>
                            중복확인
                        </button>
                    </div>
                    <p className={`${styles.msg} ${checked.nickname ? styles.ok : styles.err}`}>
                        {msgs.nickname}
                    </p>
                </div>

                {/* 작가명 */}
                <div className={styles.field}>
                    <input
                        type="text" name="artistName" 
                        placeholder="작가명 (미입력 시 활동명 자동 적용)"
                        value={form.artistName} onChange={handleChange} 
                        maxLength={50}
                    />
                     <p className={styles.hint}>활동명과 다른 작가 전용 이름을 쓸 경우 입력</p>
                </div>

                {/* 홈페이지 */}
                <div className={styles.field}>
                    <input
                        type="text" name="homepage" 
                        placeholder="개인 홈페이지 URL (선택)"
                        value={form.homepage} onChange={handleChange}
                    />
                </div>
                
                {/* SNS */}
                <div className={styles.field}>
                    <input
                        type="text" name="artistSns" 
                        placeholder="SNS 아이디 (선택, 예: @daily_art)"
                        value={form.artistSns} onChange={handleChange}
                    />
                </div>

                <p className={styles.sectionLabel}>개인 정보</p>

                {/* 실명 */}
                <div className={styles.field}>
                    <input
                        type="text" name="name" placeholder="* 이름 (실명)"
                        value={form.name} onChange={handleChange} required
                    />
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

                <button type="submit" className={styles.btnSubmit} disabled={loading}>
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