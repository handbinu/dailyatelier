import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createArt } from '../../api/artApi'
import { PageBanner, PageWrap } from './components/atoms'
import s from './UploadSell.module.css'

const DEFAULT_FORM = {
  name: '',
  descript: '',
  wIntro: '',
  startPrice: '',
  bidStartTime: '',
  closingTime: '',
  imgPath: '',
  artStatus: 2,
}

const STATUS_OPTIONS = [
  { value: 2, label: '승인 대기' },
  { value: 0, label: '진행 중' },
]

export default function UploadSell() {
  const navigate = useNavigate()
  const [form, setForm] = useState(DEFAULT_FORM)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [createdArt, setCreatedArt] = useState(null)

  useEffect(() => {
    const token = localStorage.getItem('token')
    const userStatus = Number(localStorage.getItem('userStatus') ?? 0)

    if (!token) {
      alert('로그인이 필요합니다.')
      navigate('/login', { replace: true })
      return
    }
    if (userStatus !== 1) {
      alert('작가 회원만 접근할 수 있습니다.')
      navigate('/', { replace: true })
    }
  }, [navigate])

  const previewPath = useMemo(() => form.imgPath.trim(), [form.imgPath])

  const setValue = (key) => (e) => {
    const value = key === 'artStatus' ? Number(e.target.value) : e.target.value
    setForm((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: '' }))
  }

  const resetForm = () => {
    setForm(DEFAULT_FORM)
    setErrors({})
    setCreatedArt(null)
  }

  const validate = () => {
    const nextErrors = {}
    const startPrice = Number(form.startPrice)
    const bidStart = new Date(form.bidStartTime)
    const closing = new Date(form.closingTime)

    if (!form.name.trim()) nextErrors.name = '작품명을 입력해 주세요.'
    if (!Number.isFinite(startPrice) || startPrice < 1) {
      nextErrors.startPrice = '시작가는 1원 이상이어야 합니다.'
    }
    if (!form.bidStartTime) nextErrors.bidStartTime = '입찰 시작 시간을 선택해 주세요.'
    if (!form.closingTime) nextErrors.closingTime = '입찰 종료 시간을 선택해 주세요.'
    if (form.bidStartTime && form.closingTime && closing <= bidStart) {
      nextErrors.closingTime = '입찰 종료 시간은 시작 시간보다 이후여야 합니다.'
    }
    if (!form.imgPath.trim()) nextErrors.imgPath = '이미지 경로를 입력해 주세요.'

    setErrors(nextErrors)
    return Object.keys(nextErrors).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validate()) return

    setSubmitting(true)
    try {
      const payload = {
        ...form,
        name: form.name.trim(),
        descript: form.descript.trim(),
        wIntro: form.wIntro.trim(),
        imgPath: form.imgPath.trim(),
        startPrice: Number(form.startPrice),
      }
      const { data } = await createArt(payload)
      setCreatedArt(data)
    } catch (err) {
      const message = err.response?.data?.message || '작품 등록에 실패했습니다.'
      setErrors((prev) => ({ ...prev, submit: message }))
    } finally {
      setSubmitting(false)
    }
  }

  if (createdArt) {
    return (
      <PageWrap>
        <PageBanner title="작품 등록 완료" crumb="작품 등록" />
        <div className={s.doneWrap}>
          <h2 className={s.doneTitle}>{createdArt.name}</h2>
          <p className={s.doneSub}>작품이 등록되었습니다. 현재가는 시작가와 동일하게 설정됩니다.</p>
          <div className={s.doneMeta}>
            <span>작품 번호 {createdArt.artId}</span>
            <span>현재가 {Number(createdArt.currentPrice).toLocaleString()}원</span>
          </div>
          <div className={s.doneActions}>
            <button className={s.doneBtn} onClick={() => navigate(`/auction/${createdArt.artId}`)}>
              상세 보기
            </button>
            <button className={`${s.doneBtn} ${s.doneBtnOutline}`} onClick={resetForm}>
              추가 등록
            </button>
          </div>
        </div>
      </PageWrap>
    )
  }

  return (
    <PageWrap>
      <PageBanner title="작품 등록" crumb="작품 등록" />
      <div className={s.body}>
        <form onSubmit={handleSubmit} className={s.form}>
          <section className={s.card}>
            <h2 className={s.cardTitle}>작품 이미지</h2>
            <FormField label="이미지 경로 *" error={errors.imgPath}>
              <input
                className={s.input}
                value={form.imgPath}
                onChange={setValue('imgPath')}
                placeholder="/img/auction/new_1.jpg"
              />
            </FormField>
            {previewPath && (
              <div className={s.previewBox}>
                <img src={previewPath} alt="작품 미리보기" className={s.preview} />
              </div>
            )}
          </section>

          <section className={s.card}>
            <h2 className={s.cardTitle}>작품 정보</h2>
            <div className={s.fieldGrid}>
              <FormField label="작품명 *" error={errors.name}>
                <input
                  className={s.input}
                  value={form.name}
                  onChange={setValue('name')}
                  maxLength={30}
                  placeholder="작품명을 입력해 주세요"
                />
              </FormField>

              <FormField label="작품 설명">
                <textarea
                  className={s.textarea}
                  value={form.descript}
                  onChange={setValue('descript')}
                  maxLength={300}
                  rows={4}
                  placeholder="작품의 소재, 분위기, 특징을 입력해 주세요"
                />
              </FormField>

              <FormField label="작가 노트">
                <textarea
                  className={s.textarea}
                  value={form.wIntro}
                  onChange={setValue('wIntro')}
                  maxLength={500}
                  rows={5}
                  placeholder="작품 제작 배경이나 소개 문구를 입력해 주세요"
                />
              </FormField>
            </div>
          </section>

          <section className={s.card}>
            <h2 className={s.cardTitle}>경매 설정</h2>
            <div className={s.fieldGrid}>
              <FormField label="시작가 *" error={errors.startPrice}>
                <div className={s.priceRow}>
                  <input
                    className={s.input}
                    type="number"
                    min={1}
                    step={1000}
                    value={form.startPrice}
                    onChange={setValue('startPrice')}
                    placeholder="0"
                  />
                  <span className={s.priceUnit}>원</span>
                </div>
              </FormField>

              <div className={s.timeGrid}>
                <FormField label="입찰 시작 시간 *" error={errors.bidStartTime}>
                  <input
                    className={s.input}
                    type="datetime-local"
                    value={form.bidStartTime}
                    onChange={setValue('bidStartTime')}
                  />
                </FormField>
                <FormField label="입찰 종료 시간 *" error={errors.closingTime}>
                  <input
                    className={s.input}
                    type="datetime-local"
                    value={form.closingTime}
                    onChange={setValue('closingTime')}
                  />
                </FormField>
              </div>

              <FormField label="등록 상태">
                <select className={s.input} value={form.artStatus} onChange={setValue('artStatus')}>
                  {STATUS_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </FormField>
            </div>
          </section>

          <div className={s.notice}>
            이미지 파일 업로드는 아직 연결하지 않고, 현재 프로젝트의 정적 리소스 경로를 입력해 등록합니다.
          </div>

          {errors.submit && <p className={s.submitError}>{errors.submit}</p>}

          <button type="submit" className={s.submitBtn} disabled={submitting}>
            {submitting ? '등록 중' : '작품 등록하기'}
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
