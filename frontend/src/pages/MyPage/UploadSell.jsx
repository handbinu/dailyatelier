// src/pages/MyPage/UploadSell.jsx  —  작품 등록 (작가 전용)
import { useState, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageBanner, PageWrap } from './components/atoms'
import s from './UploadSell.module.css'

const ART_TYPES   = ['실물', '디지털', '오브제']
const THEME_TAGS  = ['풍경', '인물', '정물', '동물', '추상', '팝아트', '오브제', '미디어', '기타']

export default function UploadSell() {
  const navigate = useNavigate()
  const token      = localStorage.getItem('token')
  const userStatus = Number(localStorage.getItem('userStatus') ?? 0)

  if (!token) { alert('로그인이 필요합니다.'); navigate('/login', { replace: true }); return null }
  if (userStatus !== 1) { alert('작가 회원만 접근할 수 있습니다.'); navigate('/', { replace: true }); return null }

  const [preview,   setPreview]   = useState(null)
  const [dragging,  setDragging]  = useState(false)
  const [form, setForm] = useState({
    name:      '',
    artType:   '실물',
    material:  '',
    descript:  '',
    tags:      [],
    price:     '',
    startTime: '',
    endTime:   '',
  })
  const [errors,   setErrors]   = useState({})
  const [submitting, setSub]    = useState(false)
  const [done,      setDone]    = useState(false)
  const fileRef = useRef()

  const set = (key) => (e) => {
    setForm(f => ({ ...f, [key]: e.target.value }))
    setErrors(er => ({ ...er, [key]: '' }))
  }

  const toggleTag = (tag) => {
    setForm(f => ({
      ...f,
      tags: f.tags.includes(tag) ? f.tags.filter(t => t !== tag) : [...f.tags, tag],
    }))
  }

  const loadFile = (file) => {
    if (!file || !file.type.startsWith('image/')) { alert('이미지 파일만 업로드 가능합니다.'); return }
    if (file.size > 5 * 1024 * 1024) { alert('파일 크기는 5MB 이하여야 합니다.'); return }
    const reader = new FileReader()
    reader.onload = (e) => setPreview(e.target.result)
    reader.readAsDataURL(file)
  }

  const onDrop   = useCallback((e) => { e.preventDefault(); setDragging(false); loadFile(e.dataTransfer.files[0]) }, [])
  const onDragOv = (e) => { e.preventDefault(); setDragging(true) }
  const onDragLv = () => setDragging(false)

  const validate = () => {
    const er = {}
    if (!preview)            er.img      = '작품 이미지를 업로드해주세요.'
    if (!form.name.trim())   er.name     = '작품명을 입력해주세요.'
    if (!form.descript.trim()) er.descript = '작품 설명을 입력해주세요.'
    if (!form.price || Number(form.price) < 1000) er.price = '시작가를 1,000원 이상 입력해주세요.'
    if (!form.startTime)     er.startTime = '경매 시작 시간을 선택해주세요.'
    if (!form.endTime)       er.endTime   = '경매 종료 시간을 선택해주세요.'
    if (form.startTime && form.endTime && new Date(form.endTime) <= new Date(form.startTime))
      er.endTime = '종료 시간은 시작 시간보다 미래여야 합니다.'
    setErrors(er)
    return Object.keys(er).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validate()) return
    setSub(true)
    // TODO: POST /api/arts  (multipart/form-data)
    await new Promise(r => setTimeout(r, 1000))
    setSub(false)
    setDone(true)
  }

  if (done) {
    return (
      <PageWrap>
        <PageBanner title="작품 등록 완료" crumb="작품 등록" />
        <div className={s.doneWrap}>
          <span className={s.doneIcon}>🖼️</span>
          <h2 className={s.doneTitle}>작품이 등록되었습니다!</h2>
          <p className={s.doneSub}>경매 시작 시간이 되면 자동으로 진행됩니다.</p>
          <div className={s.doneActions}>
            <button className={s.doneBtn} onClick={() => navigate('/mypage')}>마이페이지로</button>
            <button className={`${s.doneBtn} ${s.doneBtnOutline}`} onClick={() => { setDone(false); setPreview(null); setForm({ name:'', artType:'실물', material:'', descript:'', tags:[], price:'', startTime:'', endTime:'' }) }}>
              추가 등록
            </button>
          </div>
        </div>
      </PageWrap>
    )
  }

  return (
    <PageWrap>
      <PageBanner title="내 작품 판매하기" crumb="작품 등록" />

      <div className={s.body}>
        <form onSubmit={handleSubmit} className={s.form}>
          {/* 이미지 업로드 */}
          <section className={s.card}>
            <h2 className={s.cardTitle}>작품 이미지 <span className={s.req}>*</span></h2>
            <div
              className={`${s.dropZone} ${dragging ? s.dropZoneDragging : ''} ${preview ? s.dropZoneHasImg : ''}`}
              onDrop={onDrop}
              onDragOver={onDragOv}
              onDragLeave={onDragLv}
              onClick={() => fileRef.current?.click()}
            >
              {preview
                ? <img src={preview} alt="미리보기" className={s.preview} />
                : <>
                    <span className={s.dropIcon}>📁</span>
                    <p className={s.dropText}>클릭하거나 이미지를 드래그해서 업로드</p>
                    <p className={s.dropSub}>JPG, PNG · 최대 5MB</p>
                  </>
              }
              <input
                ref={fileRef}
                type="file"
                accept="image/*"
                className={s.fileInput}
                onChange={e => loadFile(e.target.files[0])}
              />
            </div>
            {preview && (
              <button type="button" className={s.removeImg} onClick={() => setPreview(null)}>이미지 제거</button>
            )}
            {errors.img && <p className={s.errMsg}>{errors.img}</p>}
          </section>

          {/* 작품 정보 */}
          <section className={s.card}>
            <h2 className={s.cardTitle}>작품 정보</h2>
            <div className={s.fieldGrid}>
              <FormField label="작품명 *" error={errors.name}>
                <input className={s.input} value={form.name} onChange={set('name')} placeholder="작품명을 입력해주세요" />
              </FormField>

              <FormField label="종류 *">
                <div className={s.typeRow}>
                  {ART_TYPES.map(t => (
                    <button
                      key={t} type="button"
                      className={`${s.typeBtn} ${form.artType === t ? s.typeBtnActive : ''}`}
                      onClick={() => setForm(f => ({ ...f, artType: t }))}
                    >
                      {t}
                    </button>
                  ))}
                </div>
              </FormField>

              <FormField label="재료">
                <input className={s.input} value={form.material} onChange={set('material')} placeholder="사용 재료 (예: 유채, 수채화, 디지털 드로잉)" />
              </FormField>

              <FormField label="테마 태그">
                <div className={s.tagGrid}>
                  {THEME_TAGS.map(tag => (
                    <button
                      key={tag} type="button"
                      className={`${s.tagBtn} ${form.tags.includes(tag) ? s.tagBtnActive : ''}`}
                      onClick={() => toggleTag(tag)}
                    >
                      #{tag}
                    </button>
                  ))}
                </div>
              </FormField>

              <FormField label="작품 설명 *" error={errors.descript}>
                <textarea
                  className={s.textarea}
                  value={form.descript}
                  onChange={set('descript')}
                  placeholder="작품에 대한 설명을 자세히 입력해주세요. (작품의 의미, 제작 배경 등)"
                  rows={5}
                />
              </FormField>
            </div>
          </section>

          {/* 경매 설정 */}
          <section className={s.card}>
            <h2 className={s.cardTitle}>경매 설정</h2>
            <div className={s.fieldGrid}>
              <FormField label="시작가 *" error={errors.price}>
                <div className={s.priceRow}>
                  <input
                    className={s.input}
                    type="number"
                    min={1000}
                    step={1000}
                    value={form.price}
                    onChange={set('price')}
                    placeholder="0"
                  />
                  <span className={s.priceUnit}>원</span>
                </div>
              </FormField>

              <div className={s.timeGrid}>
                <FormField label="경매 시작 시간 *" error={errors.startTime}>
                  <input
                    className={s.input}
                    type="datetime-local"
                    value={form.startTime}
                    onChange={set('startTime')}
                    min={new Date().toISOString().slice(0, 16)}
                  />
                </FormField>
                <FormField label="경매 종료 시간 *" error={errors.endTime}>
                  <input
                    className={s.input}
                    type="datetime-local"
                    value={form.endTime}
                    onChange={set('endTime')}
                    min={form.startTime || new Date().toISOString().slice(0, 16)}
                  />
                </FormField>
              </div>

              {form.startTime && form.endTime && !errors.endTime && (
                <div className={s.durationInfo}>
                  경매 기간: {calcDuration(form.startTime, form.endTime)}
                </div>
              )}
            </div>
          </section>

          {/* 유의사항 */}
          <div className={s.notice}>
            ※ 등록된 작품은 경매 시작 후 삭제가 불가능합니다. 낙찰 후에는 작가가 직접 배송을 진행해야 합니다.
            AI 생성 이미지 등록 시 계정이 정지될 수 있습니다.
          </div>

          <button type="submit" className={s.submitBtn} disabled={submitting}>
            {submitting ? '등록 중…' : '작품 등록하기'}
          </button>
        </form>
      </div>
    </PageWrap>
  )
}

function FormField({ label, error, children }) {
  return (
    <div className={s.field}>
      <p className={s.fieldLabel}>{label}</p>
      {children}
      {error && <p className={s.errMsg}>{error}</p>}
    </div>
  )
}

function calcDuration(start, end) {
  const ms   = new Date(end) - new Date(start)
  const days = Math.floor(ms / 86400000)
  const hrs  = Math.floor((ms % 86400000) / 3600000)
  return `${days}일 ${hrs}시간`
}