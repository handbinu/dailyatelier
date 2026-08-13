import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../../api/authApi'
import { createArt } from '../../api/artApi'
import {
  DEFAULT_MINIMUM_BID_INCREMENT,
  getMinimumBidIncrementError,
  getNextMinimumBidPrice,
  MAX_BID_PRICE,
  parseIntegerPrice,
} from '../../utils/bidPricePolicy'
import { PageBanner, PageWrap } from './components/atoms'
import s from './UploadSell.module.css'

const DEFAULT_FORM = {
  name: '',
  descript: '',
  material: '',
  format: '',
  category: '',
  wIntro: '',
  startPrice: '',
  minimumBidIncrement: String(DEFAULT_MINIMUM_BID_INCREMENT),
  bidStartTime: '',
  closingTime: '',
}

const CATEGORY_OPTIONS = [
  ['OIL_PAINTING', '유화'],
  ['WATERCOLOR', '수채화'],
  ['ACRYLIC_PAINTING', '아크릴화'],
  ['DRAWING', '드로잉'],
  ['DIGITAL_ART', '디지털 아트'],
  ['PRINTMAKING', '판화'],
  ['PHOTOGRAPHY', '사진'],
  ['SCULPTURE', '조각'],
  ['CRAFT', '공예'],
  ['MIXED_MEDIA', '혼합 매체'],
  ['OTHER', '기타'],
]

const isCategoryAllowed = (format, category) => (
  (format === 'DIGITAL' && category === 'DIGITAL_ART')
  || (format === 'PHYSICAL' && category !== 'DIGITAL_ART')
)

const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_IMAGE_SIZE = 5 * 1024 * 1024
const CLOUDINARY_FOLDER = 'arts'

export default function UploadSell() {
  const navigate = useNavigate()
  const [form, setForm] = useState(DEFAULT_FORM)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [createdArt, setCreatedArt] = useState(null)
  const [selectedFile, setSelectedFile] = useState(null)
  const [previewUrl, setPreviewUrl] = useState('')

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl)
    }
  }, [previewUrl])

  const setValue = (key) => (e) => {
    setForm((prev) => ({ ...prev, [key]: e.target.value }))
    setErrors((prev) => ({ ...prev, [key]: '' }))
  }

  const handleFormatChange = (e) => {
    const format = e.target.value
    setForm((prev) => ({
      ...prev,
      format,
      category: isCategoryAllowed(format, prev.category) ? prev.category : '',
    }))
    setErrors((prev) => ({ ...prev, format: '', category: '' }))
  }

  const clearImage = () => {
    setSelectedFile(null)
    setErrors((prev) => ({ ...prev, imgFile: '' }))
    setPreviewUrl((prev) => {
      if (prev) URL.revokeObjectURL(prev)
      return ''
    })
  }

  const handleImageChange = (e) => {
    const file = e.target.files?.[0]
    if (!file) {
      clearImage()
      return
    }

    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      setErrors((prev) => ({ ...prev, imgFile: 'jpg, jpeg, png, webp 파일만 업로드할 수 있습니다.' }))
      e.target.value = ''
      return
    }

    if (file.size > MAX_IMAGE_SIZE) {
      setErrors((prev) => ({ ...prev, imgFile: '이미지 파일은 5MB 이하만 업로드할 수 있습니다.' }))
      e.target.value = ''
      return
    }

    setSelectedFile(file)
    setErrors((prev) => ({ ...prev, imgFile: '' }))
    setPreviewUrl((prev) => {
      if (prev) URL.revokeObjectURL(prev)
      return URL.createObjectURL(file)
    })
  }

  const resetForm = () => {
    setForm(DEFAULT_FORM)
    setErrors({})
    setCreatedArt(null)
    clearImage()
  }

  const validate = () => {
    const nextErrors = {}
    const startPrice = parseIntegerPrice(form.startPrice)
    const minimumBidIncrement = parseIntegerPrice(form.minimumBidIncrement)
    const bidStart = new Date(form.bidStartTime)
    const closing = new Date(form.closingTime)

    if (!form.name.trim()) nextErrors.name = '작품명을 입력해 주세요.'
    if (!form.format) nextErrors.format = '작품 형태를 선택해 주세요.'
    if (!form.category) nextErrors.category = '작품 카테고리를 선택해 주세요.'
    if (form.format === 'DIGITAL' && form.category && form.category !== 'DIGITAL_ART') {
      nextErrors.category = '디지털 작품은 디지털 아트 카테고리만 선택할 수 있습니다.'
    }
    if (form.format === 'PHYSICAL' && form.category === 'DIGITAL_ART') {
      nextErrors.category = '실물 작품은 디지털 아트 외 카테고리를 선택해 주세요.'
    }
    if (!form.material.trim()) nextErrors.material = '재료·기법을 입력해 주세요.'
    if (startPrice === null || startPrice < 1 || startPrice > MAX_BID_PRICE) {
      nextErrors.startPrice = '시작가격은 1원 이상 21억 원 이하의 정수로 입력해 주세요.'
    }
    const incrementError = getMinimumBidIncrementError(form.minimumBidIncrement)
    if (incrementError) nextErrors.minimumBidIncrement = incrementError
    if (!nextErrors.startPrice && !incrementError
      && getNextMinimumBidPrice(startPrice, minimumBidIncrement) === null) {
      nextErrors.startPrice = '시작가와 최소 입찰 증분의 합은 21억 원 이하여야 합니다.'
    }
    if (!form.bidStartTime) nextErrors.bidStartTime = '입찰 시작 시간을 선택해 주세요.'
    if (!form.closingTime) nextErrors.closingTime = '입찰 종료 시간을 선택해 주세요.'
    if (form.bidStartTime && form.closingTime && closing <= bidStart) {
      nextErrors.closingTime = '입찰 종료 시간은 시작 시간보다 이후여야 합니다.'
    }
    if (!selectedFile) nextErrors.imgFile = '이미지 파일을 선택해 주세요.'

    setErrors(nextErrors)
    return Object.keys(nextErrors).length === 0
  }

  const fetchCloudinarySignature = async () => {
    const { data } = await api.post('/api/uploads/cloudinary/signature', { folder: CLOUDINARY_FOLDER })
    return data
  }

  const uploadToCloudinary = async (file) => {
    const signatureData = await fetchCloudinarySignature()
    const formData = new FormData()
    formData.append('file', file)
    formData.append('api_key', signatureData.apiKey)
    formData.append('timestamp', String(signatureData.timestamp))
    formData.append('signature', signatureData.signature)
    formData.append('folder', signatureData.folder)

    const response = await fetch(signatureData.uploadUrl, {
      method: 'POST',
      mode: 'cors',
      body: formData,
    })

    const result = await response.json().catch(() => ({}))

    if (!response.ok) {
      const reason = result?.error?.message || `Cloudinary 업로드에 실패했습니다. (HTTP ${response.status})`
      throw new Error(reason)
    }

    return result
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validate()) return

    setSubmitting(true)
    try {
      const uploaded = await uploadToCloudinary(selectedFile)
      const payload = {
        ...form,
        name: form.name.trim(),
        descript: form.descript.trim(),
        material: form.material.trim(),
        wIntro: form.wIntro.trim(),
        imgPath: uploaded.secure_url,
        startPrice: Number(form.startPrice),
        minimumBidIncrement: Number(form.minimumBidIncrement),
      }
      const { data } = await createArt(payload)
      setCreatedArt(data)
    } catch (err) {
      const message = err.response?.data?.message || err.message || '작품 등록에 실패했습니다.'
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
            <span>최소 입찰 증분 {Number(createdArt.minimumBidIncrement).toLocaleString()}원</span>
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

  const categoryOptions = CATEGORY_OPTIONS.filter(([category]) => isCategoryAllowed(form.format, category))
  const startPrice = parseIntegerPrice(form.startPrice)
  const increment = parseIntegerPrice(form.minimumBidIncrement)
  const incrementError = getMinimumBidIncrementError(form.minimumBidIncrement)
  const firstBidPrice = startPrice !== null && startPrice >= 1 && startPrice <= MAX_BID_PRICE
    && !incrementError ? getNextMinimumBidPrice(startPrice, increment) : null

  return (
    <PageWrap>
      <PageBanner title="작품 등록" crumb="작품 등록" />
      <div className={s.body}>
        <form onSubmit={handleSubmit} className={s.form}>
          <section className={s.card}>
            <h2 className={s.cardTitle}>작품 이미지</h2>
            <FormField label="이미지 파일 *" error={errors.imgFile}>
              <input
                className={s.fileInput}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={handleImageChange}
              />
              <p className={s.fileHint}>jpg, jpeg, png, webp / 5MB 이하</p>
            </FormField>
            {previewUrl && (
              <div className={s.previewBox}>
                <img src={previewUrl} alt="작품 미리보기" className={s.preview} />
              </div>
            )}
          </section>

          <section className={s.card}>
            <h2 className={s.cardTitle}>작품 정보</h2>
            <p className={s.cardLead}>작품 설명은 분위기와 특징 위주로, 분류와 재료·기법은 구분해서 적어주세요.</p>
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
                  placeholder="예: 차분한 색감, 따뜻한 빛, 잔잔한 분위기"
                />
              </FormField>

              <div className={s.classificationGrid}>
                <FormField label="작품 형태 *" error={errors.format}>
                  <select className={s.input} value={form.format} onChange={handleFormatChange} required>
                    <option value="">형태 선택</option>
                    <option value="DIGITAL">디지털</option>
                    <option value="PHYSICAL">실물</option>
                  </select>
                </FormField>
                <FormField label="카테고리 *" error={errors.category}>
                  <select
                    className={s.input}
                    value={form.category}
                    onChange={setValue('category')}
                    disabled={!form.format}
                    required
                  >
                    <option value="">카테고리 선택</option>
                    {categoryOptions.map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </FormField>
              </div>

              <FormField label="재료·기법 *" error={errors.material}>
                <input
                  className={s.input}
                  value={form.material}
                  onChange={setValue('material')}
                  maxLength={60}
                  placeholder="예: 캔버스에 유채, 종이에 수채"
                />
              </FormField>

              <FormField label="작가 소개">
                <textarea
                  className={s.textarea}
                  value={form.wIntro}
                  onChange={setValue('wIntro')}
                  maxLength={500}
                  rows={4}
                  placeholder="작품을 만든 계기나 짧은 소개를 적어주세요"
                />
              </FormField>
            </div>
          </section>

          <section className={s.card}>
            <h2 className={s.cardTitle}>경매 설정</h2>
            <p className={s.cardLead}>시작가와 기간을 입력하면 등록 즉시 진행 중 상태로 저장됩니다.</p>
            <div className={s.fieldGrid}>
              <FormField label="시작가 *" error={errors.startPrice}>
                <div className={s.priceRow}>
                  <input
                    className={s.input}
                    type="number"
                    min={1}
                    max={MAX_BID_PRICE}
                    step={1}
                    value={form.startPrice}
                    onChange={setValue('startPrice')}
                    placeholder="0"
                  />
                  <span className={s.priceUnit}>원</span>
                </div>
              </FormField>

              <div className={s.field}>
                <label className={s.fieldLabel} htmlFor="minimum-bid-increment">최소 입찰 증분 *</label>
                <div className={s.priceRow}>
                  <input
                    id="minimum-bid-increment"
                    className={s.input}
                    type="number"
                    min="100"
                    max="10000000"
                    step="100"
                    value={form.minimumBidIncrement}
                    onChange={setValue('minimumBidIncrement')}
                    aria-describedby={`minimum-bid-increment-help minimum-bid-increment-example${errors.minimumBidIncrement ? ' minimum-bid-increment-error' : ''}`}
                    aria-invalid={Boolean(errors.minimumBidIncrement)}
                  />
                  <span className={s.priceUnit}>원</span>
                </div>
                <div id="minimum-bid-increment-help" className={s.fieldHelp}>
                  <p>다음 입찰자는 현재가보다 최소 이 금액만큼 높게 입찰해야 합니다.</p>
                  <p>100원 단위 · 기본 1,000원</p>
                </div>
                <p id="minimum-bid-increment-example" className={s.priceExample} aria-live="polite">
                  {firstBidPrice !== null
                    ? `첫 입찰 가능 금액은 ${firstBidPrice.toLocaleString()}원부터입니다.`
                    : '유효한 시작가와 최소 입찰 증분을 입력하면 첫 입찰 가능 금액을 확인할 수 있습니다.'}
                </p>
                {errors.minimumBidIncrement && (
                  <p id="minimum-bid-increment-error" className={s.errMsg}>{errors.minimumBidIncrement}</p>
                )}
              </div>

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
            </div>
          </section>

          <div className={s.notice}>
            이미지는 Cloudinary에 직접 업로드되고, 등록되면 작품 상태는 바로 진행 중으로 저장됩니다.
          </div>

          {errors.submit && <p className={s.submitError}>{errors.submit}</p>}

          <button type="submit" className={s.submitBtn} disabled={submitting}>
            {submitting ? '등록 중...' : '작품 등록하기'}
          </button>
        </form>
      </div>
    </PageWrap>
  )
}

function FormField({ label, error, children }) {
  return (
    <label className={s.field}>
      <span className={s.fieldLabel}>{label}</span>
      {children}
      {error && <p className={s.errMsg}>{error}</p>}
    </label>
  )
}
