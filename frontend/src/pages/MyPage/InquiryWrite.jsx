// src/pages/MyPage/InquiryWrite.jsx  —  1:1 문의 작성
import { useState, useRef } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { PageBanner, PageWrap } from './components/atoms'
import s from './InquiryWrite.module.css'

const INQUIRY_TYPES = ['회원정보', '포인트', '작품', '배송', '경매', '기타']

export default function InquiryWrite() {
  const navigate = useNavigate()
  const [form, setForm] = useState({
    type:     '배송',
    title:    '',
    content:  '',
    emailAlert: true,
  })
  const [file,       setFile]       = useState(null)
  const [filePreview, setFilePreview] = useState(null)
  const [errors,     setErrors]     = useState({})
  const [submitting, setSub]        = useState(false)
  const [done,       setDone]       = useState(false)
  const fileRef = useRef()

  if (!localStorage.getItem('token')) {
    alert('로그인이 필요합니다.')
    navigate('/login', { replace: true })
    return null
  }

  const set = (key) => (e) => {
    setForm(f => ({ ...f, [key]: e.target.value }))
    setErrors(er => ({ ...er, [key]: '' }))
  }

  const onFileChange = (e) => {
    const f = e.target.files[0]
    if (!f) return
    if (f.size > 10 * 1024 * 1024) { alert('파일 크기는 10MB 이하여야 합니다.'); return }
    setFile(f)
    if (f.type.startsWith('image/')) {
      const reader = new FileReader()
      reader.onload = ev => setFilePreview(ev.target.result)
      reader.readAsDataURL(f)
    } else {
      setFilePreview(null)
    }
  }

  const removeFile = () => { setFile(null); setFilePreview(null); if (fileRef.current) fileRef.current.value = '' }

  const validate = () => {
    const er = {}
    if (!form.title.trim())   er.title   = '제목을 입력해주세요.'
    if (form.title.length > 50) er.title = '제목은 50자 이내로 입력해주세요.'
    if (!form.content.trim()) er.content = '내용을 입력해주세요.'
    if (form.content.trim().length < 10) er.content = '내용을 10자 이상 입력해주세요.'
    setErrors(er)
    return Object.keys(er).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validate()) return
    setSub(true)
    // TODO: POST /api/inquiries  (multipart/form-data)
    await new Promise(r => setTimeout(r, 800))
    setSub(false)
    setDone(true)
  }

  if (done) {
    return (
      <PageWrap>
        <PageBanner title="문의 등록 완료" crumb="1:1 문의" />
        <div className={s.doneWrap}>
          <span className={s.doneIcon}>✉️</span>
          <h2 className={s.doneTitle}>문의가 등록되었습니다!</h2>
          <p className={s.doneSub}>영업일 기준 1~3일 내 답변 드리겠습니다.</p>
          <div className={s.doneActions}>
            <Link to="/mypage/inquiry" className={s.doneBtn}>내 문의 보기</Link>
            <Link to="/"              className={`${s.doneBtn} ${s.doneBtnOutline}`}>홈으로</Link>
          </div>
        </div>
      </PageWrap>
    )
  }

  return (
    <PageWrap>
      <PageBanner title="1:1 문의하기" crumb="문의 작성" />

      <div className={s.body}>
        <form onSubmit={handleSubmit} className={s.form}>
          <section className={s.card}>
            <h2 className={s.cardTitle}>문의 내용</h2>

            {/* 유형 */}
            <div className={s.field}>
              <label className={s.label} htmlFor="inq-type">문의 유형 *</label>
              <div className={s.typeRow}>
                {INQUIRY_TYPES.map(t => (
                  <button
                    key={t} type="button"
                    className={`${s.typeBtn} ${form.type === t ? s.typeBtnActive : ''}`}
                    onClick={() => setForm(f => ({ ...f, type: t }))}
                  >
                    {t}
                  </button>
                ))}
              </div>
            </div>

            {/* 제목 */}
            <div className={s.field}>
              <label className={s.label} htmlFor="inq-title">
                제목 *
                <span className={s.charCount}>{form.title.length}/50</span>
              </label>
              <input
                id="inq-title"
                className={`${s.input} ${errors.title ? s.inputErr : ''}`}
                value={form.title}
                onChange={set('title')}
                placeholder="문의 제목을 입력해주세요"
                maxLength={50}
              />
              {errors.title && <p className={s.errMsg}>{errors.title}</p>}
            </div>

            {/* 내용 */}
            <div className={s.field}>
              <label className={s.label} htmlFor="inq-content">
                내용 *
                <span className={s.charCount}>{form.content.length}자</span>
              </label>
              <textarea
                id="inq-content"
                className={`${s.textarea} ${errors.content ? s.inputErr : ''}`}
                value={form.content}
                onChange={set('content')}
                placeholder="문의하실 내용을 상세하게 입력해주세요. (최소 10자)"
                rows={8}
              />
              {errors.content && <p className={s.errMsg}>{errors.content}</p>}
            </div>

            {/* 첨부 파일 */}
            <div className={s.field}>
              <label className={s.label}>첨부 파일 <span className={s.optional}>(선택, 최대 10MB)</span></label>
              {file
                ? <div className={s.filePreviewBox}>
                    {filePreview
                      ? <img src={filePreview} alt="미리보기" className={s.imgPreview} />
                      : <span className={s.fileName}>📎 {file.name}</span>
                    }
                    <button type="button" className={s.removeFile} onClick={removeFile}>✕ 제거</button>
                  </div>
                : <label className={s.fileLabel}>
                    📎 파일 선택
                    <input
                      ref={fileRef}
                      type="file"
                      accept="image/png,image/jpeg,image/jpg,application/pdf"
                      className={s.fileInput}
                      onChange={onFileChange}
                    />
                  </label>
              }
              <p className={s.fileGuide}>JPG, PNG, PDF 업로드 가능</p>
            </div>

            {/* 이메일 알림 */}
            <div className={s.field}>
              <label className={s.label}>답변 알림</label>
              <label className={s.checkRow}>
                <input
                  type="checkbox"
                  checked={form.emailAlert}
                  onChange={e => setForm(f => ({ ...f, emailAlert: e.target.checked }))}
                  className={s.checkbox}
                />
                <span className={s.checkText}>
                  답변 등록 시 이메일로 알림을 받겠습니다.
                </span>
              </label>
            </div>
          </section>

          {/* 유의사항 */}
          <div className={s.notice}>
            ※ 문의 답변은 영업일 기준 1~3일 내로 처리됩니다. 빠른 처리를 위해 주문번호나 작품명을 함께 입력해주세요.
          </div>

          <div className={s.btnRow}>
            <button type="submit" className={s.submitBtn} disabled={submitting}>
              {submitting ? '등록 중…' : '문의 등록'}
            </button>
            <button type="button" className={s.cancelBtn} onClick={() => navigate(-1)}>
              취소
            </button>
          </div>
        </form>
      </div>
    </PageWrap>
  )
}
