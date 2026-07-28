import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageBanner, PageWrap } from './components/atoms'
import { getUserProfile, updateUserProfile, checkNickname } from '../../api/userApi'
import s from './ProfileEdit.module.css'

const EMAIL_DOMAINS = ['직접 입력', 'naver.com', 'daum.net', 'gmail.com', 'nate.com']

function splitEmail(email) {
  if (!email) return { id: '', domain: '', preset: '직접 입력' }
  const [id = '', domain = ''] = email.split('@')
  const preset = EMAIL_DOMAINS.includes(domain) ? domain : '직접 입력'
  return { id, domain: preset === '직접 입력' ? domain : '', preset }
}

export default function ProfileEdit() {
  const navigate = useNavigate()
  const token = localStorage.getItem('token')

  const [loading, setLoading] = useState(true)
  const [form, setForm] = useState(null)
  const [nickChecked, setNickChecked] = useState(true)  // 초기값은 현재 닉네임이므로 ok
  const [nickMsg,     setNickMsg]     = useState('')
  const [pwMatch,     setPwMatch]     = useState(null)
  const [saving,      setSaving]      = useState(false)
  const [saved,       setSaved]       = useState(false)
  const [user,        setUser]        = useState(null)

  useEffect(() => {
    if (!token) {
      alert('로그인이 필요합니다.')
      navigate('/login', { replace: true })
      return
    }

    getUserProfile()
      .then(res => {
        const u = res.data
        setUser(u)
        const emailSplit = splitEmail(u.email || '')
        setForm({
          name:          u.name || '',
          nickname:      u.nickname || '',
          tel1:          u.phoneNumber?.split('-')[0] ?? '010',
          tel2:          u.phoneNumber?.split('-')[1] ?? '',
          tel3:          u.phoneNumber?.split('-')[2] ?? '',
          emailId:       emailSplit.id,
          emailPreset:   emailSplit.preset,
          emailDomain:   emailSplit.domain,
          emailAgree:    u.emailAgree ?? true,
          address:       u.userAddress1 || '',
          addressDetail: u.userAddress2 || '',
          zipCode:       u.zipCode || '',
          currentPw:     '',
          newPw:         '',
          newPwConfirm:  '',
          artistName:    u.artistName || '',
          artistIntro:   u.artistIntro || '',
          homepage:      u.homepage || '',
          artistSns:     u.artistSns || '',
        })
        setLoading(false)
      })
      .catch(err => {
        console.error(err)
        alert('프로필 정보를 로딩하는데 실패했습니다.')
        navigate('/mypage')
      })
  }, [token, navigate])

  const set = (key) => (e) => {
    setForm(f => {
      const next = { ...f, [key]: e.target.value }
      if (key === 'nickname') { setNickChecked(false); setNickMsg('') }
      if (key === 'newPwConfirm') setPwMatch(next.newPw === e.target.value)
      if (key === 'newPw') setPwMatch(e.target.value === next.newPwConfirm)
      return next
    })
  }

  const handleCheckNickname = async () => {
    if (!form.nickname.trim()) { setNickMsg('닉네임을 입력해주세요.'); return }
    if (form.nickname === user.nickname) {
      setNickMsg('현재 사용 중인 닉네임입니다. ✓'); setNickChecked(true); return
    }
    try {
      const res = await checkNickname(form.nickname)
      const duplicate = res.data.duplicate
      if (duplicate) {
        setNickMsg('이미 사용 중인 닉네임입니다.'); setNickChecked(false)
      } else {
        setNickMsg('사용 가능한 닉네임입니다. ✓'); setNickChecked(true)
      }
    } catch {
      setNickMsg('닉네임 중복확인 실패'); setNickChecked(false)
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!nickChecked) { alert('닉네임 중복확인을 해주세요.'); return }
    if (form.newPw && form.newPw !== form.newPwConfirm) { alert('새 비밀번호가 일치하지 않습니다.'); return }
    setSaving(true)
    try {
      const payload = {
        nickname: form.nickname,
        email: fullEmail,
        phoneNumber: `${form.tel1}-${form.tel2}-${form.tel3}`,
        currentPw: form.currentPw || null,
        newPw: form.newPw || null,
        emailAgree: form.emailAgree,
        zipCode: form.zipCode || null,
        userAddress1: form.address,
        userAddress2: form.addressDetail,
        artistIntro: form.artistIntro,
        homepage: form.homepage,
        artistSns: form.artistSns,
        artistName: form.artistName,
      }
      await updateUserProfile(payload)
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    } catch (err) {
      alert(err.response?.data?.message || '정보 수정에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  if (!token) return null
  if (loading || !form) return <div style={{ textAlign: 'center', padding: '5rem 0' }}>로딩 중...</div>

  const fullEmail = form.emailPreset === '직접 입력'
    ? `${form.emailId}@${form.emailDomain}`
    : `${form.emailId}@${form.emailPreset}`

  return (
    <PageWrap>
      <PageBanner title="회원 정보 수정" crumb="프로필 수정" />

      <div className={s.body}>
        {saved && (
          <div className={s.savedBanner}>✓ 정보가 성공적으로 저장되었습니다.</div>
        )}

        <form onSubmit={handleSubmit} className={s.form}>
          {/* 프로필 이미지 */}
          <section className={s.card}>
            <h2 className={s.cardTitle}>프로필 사진</h2>
            <div className={s.avatarRow}>
              <div className={s.avatar}>
                <span className={s.avatarInitial}>{form.nickname?.[0] ?? '?'}</span>
              </div>
              <div className={s.avatarInfo}>
                <p className={s.avatarGuide}>JPG, PNG 파일 (최대 5MB)</p>
                <label className={s.fileLabel}>
                  사진 선택
                  <input type="file" accept="image/*" className={s.fileInput} />
                </label>
              </div>
            </div>
          </section>

          {/* 기본 정보 */}
          <section className={s.card}>
            <h2 className={s.cardTitle}>기본 정보</h2>
            <div className={s.fieldGrid}>
              <Field label="이름 *">
                <input className={s.input} value={form.name} onChange={set('name')} required placeholder="실명 입력" />
              </Field>

              <Field label="닉네임 *">
                <div className={s.inlineRow}>
                  <input
                    className={s.input}
                    value={form.nickname}
                    onChange={set('nickname')}
                    required placeholder="닉네임 입력"
                  />
                  <button type="button" className={s.checkBtn} onClick={handleCheckNickname}>중복확인</button>
                </div>
                {nickMsg && (
                  <p className={`${s.fieldMsg} ${nickChecked ? s.fieldOk : s.fieldErr}`}>{nickMsg}</p>
                )}
              </Field>

              <Field label="연락처 *">
                <div className={s.telRow}>
                  <select className={s.select} value={form.tel1} onChange={set('tel1')}>
                    {['010','011','016','017','018','019'].map(v => <option key={v}>{v}</option>)}
                  </select>
                  <span className={s.telDash}>-</span>
                  <input className={`${s.input} ${s.telPart}`} value={form.tel2} onChange={set('tel2')} maxLength={4} placeholder="0000" />
                  <span className={s.telDash}>-</span>
                  <input className={`${s.input} ${s.telPart}`} value={form.tel3} onChange={set('tel3')} maxLength={4} placeholder="0000" />
                </div>
              </Field>

              <Field label="이메일 *">
                <div className={s.emailRow}>
                  <input className={s.input} value={form.emailId} onChange={set('emailId')} required placeholder="이메일 아이디" />
                  <span className={s.telDash}>@</span>
                  <select
                    className={s.select}
                    value={form.emailPreset}
                    onChange={e => setForm(f => ({ ...f, emailPreset: e.target.value, emailDomain: '' }))}
                  >
                    {EMAIL_DOMAINS.map(d => <option key={d}>{d}</option>)}
                  </select>
                </div>
                {form.emailPreset === '직접 입력' && (
                  <input className={s.input} style={{ marginTop: 6 }} value={form.emailDomain} onChange={set('emailDomain')} placeholder="도메인 입력 (예: example.com)" />
                )}
                {form.emailId && <p className={s.emailPreview}>→ {fullEmail}</p>}
              </Field>

              <Field label="이메일 수신">
                <div className={s.radioRow}>
                  <label className={s.radio}>
                    <input type="radio" name="emailAgree" checked={form.emailAgree}    onChange={() => setForm(f=>({...f, emailAgree: true}))}  /> 동의
                  </label>
                  <label className={s.radio}>
                    <input type="radio" name="emailAgree" checked={!form.emailAgree}   onChange={() => setForm(f=>({...f, emailAgree: false}))} /> 비동의
                  </label>
                </div>
              </Field>
            </div>
          </section>

          {/* 배송지 */}
          <section className={s.card}>
            <h2 className={s.cardTitle}>배송지 정보 <span className={s.optional}>(선택)</span></h2>
            <div className={s.fieldGrid}>
              <Field label="우편번호">
                <div className={s.inlineRow}>
                  <input className={s.input} value={form.zipCode} onChange={set('zipCode')} placeholder="우편번호" />
                  <button type="button" className={s.checkBtn}>우편번호 찾기</button>
                </div>
              </Field>
              <Field label="주소">
                <input className={s.input} value={form.address} onChange={set('address')} placeholder="기본 주소" />
                <input className={s.input} style={{ marginTop: 6 }} value={form.addressDetail} onChange={set('addressDetail')} placeholder="상세 주소" />
              </Field>
            </div>
          </section>

          {/* 비밀번호 변경 */}
          <section className={s.card}>
            <h2 className={s.cardTitle}>비밀번호 변경 <span className={s.optional}>(변경 시에만 입력)</span></h2>
            <div className={s.fieldGrid}>
              <Field label="현재 비밀번호">
                <input className={s.input} type="password" value={form.currentPw} onChange={set('currentPw')} placeholder="현재 비밀번호" autoComplete="current-password" />
              </Field>
              <Field label="새 비밀번호">
                <input className={s.input} type="password" value={form.newPw} onChange={set('newPw')} placeholder="문자·숫자·특수문자 포함 8~20자" autoComplete="new-password" />
              </Field>
              <Field label="새 비밀번호 확인">
                <input
                  className={`${s.input} ${form.newPwConfirm && (pwMatch ? s.inputOk : s.inputErr)}`}
                  type="password"
                  value={form.newPwConfirm}
                  onChange={set('newPwConfirm')}
                  placeholder="새 비밀번호 재입력"
                  autoComplete="new-password"
                />
                {form.newPwConfirm && (
                  <p className={`${s.fieldMsg} ${pwMatch ? s.fieldOk : s.fieldErr}`}>
                    {pwMatch ? '비밀번호가 일치합니다. ✓' : '비밀번호가 일치하지 않습니다.'}
                  </p>
                )}
              </Field>
            </div>
          </section>

          {/* 버튼 */}
          <div className={s.btnRow}>
            <button type="submit" className={s.submitBtn} disabled={saving}>
              {saving ? '저장 중…' : '수정 완료'}
            </button>
            <button type="button" className={s.cancelBtn} onClick={() => navigate(-1)}>취소</button>
          </div>
        </form>
      </div>
    </PageWrap>
  )
}

function Field({ label, children }) {
  return (
    <div className={s.field}>
      <p className={s.fieldLabel}>{label}</p>
      {children}
    </div>
  )
}
