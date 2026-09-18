import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import api from '../../api/authApi'
import { deleteArt, getArt, updateArt } from '../../api/artApi'
import {
  getMinimumBidIncrementError,
  getNextMinimumBidPrice,
  MAX_BID_PRICE,
  parseIntegerPrice,
} from '../../utils/bidPricePolicy'
import { getArtImageSrc } from '../../utils/artImage'
import { PageBanner, PageWrap } from './components/atoms'
import formStyles from './UploadSell.module.css'
import styles from './EditArt.module.css'

const CATEGORY_OPTIONS = [
  ['OIL_PAINTING', '유화'], ['WATERCOLOR', '수채화'], ['ACRYLIC_PAINTING', '아크릴화'],
  ['DRAWING', '드로잉'], ['DIGITAL_ART', '디지털 아트'], ['PRINTMAKING', '판화'],
  ['PHOTOGRAPHY', '사진'], ['SCULPTURE', '조각'], ['CRAFT', '공예'],
  ['MIXED_MEDIA', '혼합 매체'], ['OTHER', '기타'],
]
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_IMAGE_SIZE = 5 * 1024 * 1024
const CLOUDINARY_FOLDER = 'arts'
const MANAGE_PATH_PATTERN = /^\/mypage\/manage-arts(?:\?.*)?$/

const toLocalDateTime = (value) => value ? String(value).slice(0, 16) : ''
const isCategoryAllowed = (format, category) => (
  (format === 'DIGITAL' && category === 'DIGITAL_ART')
  || (format === 'PHYSICAL' && category !== 'DIGITAL_ART')
)

const toForm = (art) => ({
  descript: art.descript ?? '', material: art.material ?? '', format: art.format ?? '',
  category: art.category ?? '', wIntro: art.wIntro ?? '', startPrice: String(art.startPrice ?? ''),
  minimumBidIncrement: String(art.minimumBidIncrement ?? ''),
  bidStartTime: toLocalDateTime(art.bidStartTime), closingTime: toLocalDateTime(art.closingTime),
  imgPath: art.imgPath ?? '',
})

const getRequestMessage = (error, fallback) => {
  if (error.response?.status === 401) return '로그인이 만료되었습니다. 다시 로그인해 주세요.'
  if (error.response?.status === 403) return '이 작품을 수정하거나 삭제할 권한이 없습니다.'
  if (error.response?.status === 404) return '작품을 찾을 수 없습니다.'
  return error.response?.data?.message || error.message || fallback
}

export default function EditArt() {
  const { artId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const returnPath = MANAGE_PATH_PATTERN.test(location.state?.from || '')
    ? location.state.from : '/mypage/manage-arts?state=ALL&page=1'
  const [art, setArt] = useState(null)
  const [form, setForm] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [errors, setErrors] = useState({})
  const [selectedFile, setSelectedFile] = useState(null)
  const [previewUrl, setPreviewUrl] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [deleting, setDeleting] = useState(false)

  const loadArt = useCallback(async ({ conflict = false } = {}) => {
    setLoading(!conflict)
    if (!conflict) setError('')
    try {
      const { data } = await getArt(artId)
      setArt(data)
      setForm(toForm(data))
      setSelectedFile(null)
      setPreviewUrl('')
      if (conflict) setErrors({ submit: '작품 상태가 변경되어 최신 정보를 다시 불러왔습니다.' })
    } catch (requestError) {
      setError(getRequestMessage(requestError, '작품 정보를 불러오지 못했습니다.'))
    } finally {
      setLoading(false)
    }
  }, [artId])

  useEffect(() => { loadArt() }, [loadArt])
  useEffect(() => () => { if (previewUrl) URL.revokeObjectURL(previewUrl) }, [previewUrl])

  const hasBid = Number(art?.currentPrice) > Number(art?.startPrice)
  const auctionStarted = art ? Date.now() >= new Date(art.bidStartTime).getTime() : false
  const mutationUnavailable = art && (!art.isOwner || art.artStatus !== 0 || Date.now() >= new Date(art.closingTime).getTime())
  const priceAndPeriodLocked = hasBid || mutationUnavailable
  const incrementLocked = auctionStarted || hasBid || mutationUnavailable
  const categoryOptions = useMemo(
    () => CATEGORY_OPTIONS.filter(([category]) => isCategoryAllowed(form?.format, category)),
    [form?.format],
  )

  const setValue = (key) => (event) => {
    setForm((current) => ({ ...current, [key]: event.target.value }))
    setErrors((current) => ({ ...current, [key]: '', submit: '' }))
  }

  const handleFormatChange = (event) => {
    const format = event.target.value
    setForm((current) => ({
      ...current, format,
      category: isCategoryAllowed(format, current.category) ? current.category : '',
    }))
  }

  const handleImageChange = (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    if (!ALLOWED_IMAGE_TYPES.includes(file.type) || file.size > MAX_IMAGE_SIZE) {
      setErrors((current) => ({ ...current, imgFile: 'jpg, jpeg, png, webp 형식의 5MB 이하 파일을 선택해 주세요.' }))
      event.target.value = ''
      return
    }
    setSelectedFile(file)
    setErrors((current) => ({ ...current, imgFile: '', submit: '' }))
    setPreviewUrl((current) => {
      if (current) URL.revokeObjectURL(current)
      return URL.createObjectURL(file)
    })
  }

  const validate = () => {
    const next = {}
    const startPrice = parseIntegerPrice(form.startPrice)
    const increment = parseIntegerPrice(form.minimumBidIncrement)
    if (!form.material.trim()) next.material = '재료·기법을 입력해 주세요.'
    if (!form.format) next.format = '작품 형태를 선택해 주세요.'
    if (!form.category || !isCategoryAllowed(form.format, form.category)) next.category = '작품 형태에 맞는 카테고리를 선택해 주세요.'
    if (!priceAndPeriodLocked && (startPrice === null || startPrice < 1 || startPrice > MAX_BID_PRICE)) next.startPrice = '시작가격은 1원 이상 21억 원 이하의 정수로 입력해 주세요.'
    if (!incrementLocked) {
      const incrementError = getMinimumBidIncrementError(form.minimumBidIncrement)
      if (incrementError) next.minimumBidIncrement = incrementError
      if (!next.startPrice && !incrementError && getNextMinimumBidPrice(startPrice, increment) === null) next.startPrice = '시작가와 최소 입찰 증분의 합은 21억 원 이하여야 합니다.'
    }
    if (!priceAndPeriodLocked && (!form.bidStartTime || !form.closingTime || new Date(form.closingTime) <= new Date(form.bidStartTime))) next.closingTime = '입찰 종료 시간은 시작 시간보다 이후여야 합니다.'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const uploadImage = async () => {
    if (!selectedFile) return null
    const { data: signature } = await api.post('/api/uploads/cloudinary/signature', { folder: CLOUDINARY_FOLDER })
    const body = new FormData()
    body.append('file', selectedFile)
    body.append('api_key', signature.apiKey)
    body.append('timestamp', String(signature.timestamp))
    body.append('signature', signature.signature)
    body.append('folder', signature.folder)
    const response = await fetch(signature.uploadUrl, { method: 'POST', mode: 'cors', body })
    const result = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(result?.error?.message || '이미지 업로드에 실패했습니다.')
    return { imgPath: result.secure_url, publicId: result.public_id }
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (submitting || deleting || mutationUnavailable || !validate()) return
    setSubmitting(true)
    setErrors({})
    try {
      const uploadedImage = await uploadImage()
      const payload = {
        descript: form.descript.trim(), material: form.material.trim(), format: form.format,
        category: form.category, wIntro: form.wIntro.trim(),
      }
      if (uploadedImage) Object.assign(payload, uploadedImage)
      if (!priceAndPeriodLocked) Object.assign(payload, {
        startPrice: Number(form.startPrice), bidStartTime: form.bidStartTime, closingTime: form.closingTime,
      })
      if (!incrementLocked) payload.minimumBidIncrement = Number(form.minimumBidIncrement)
      await updateArt(artId, payload)
      navigate(returnPath, { replace: true, state: { feedback: '작품 정보를 수정했습니다.' } })
    } catch (requestError) {
      if (requestError.response?.status === 409) await loadArt({ conflict: true })
      else setErrors({ submit: getRequestMessage(requestError, '작품 수정에 실패했습니다.') })
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async () => {
    if (submitting || deleting || mutationUnavailable) return
    const message = hasBid
      ? '입찰 이력이 있어 작품은 삭제되지 않고 경매 취소 상태로 보존됩니다. 계속할까요?'
      : '이 작품을 삭제할까요? 삭제 후에는 복구할 수 없습니다.'
    if (!window.confirm(message)) return
    setDeleting(true)
    setErrors({})
    try {
      const { data } = await deleteArt(artId)
      const feedback = data.action === 'CANCELED' ? '입찰 이력이 있어 경매를 취소했습니다.' : '작품을 삭제했습니다.'
      navigate(returnPath, { replace: true, state: { feedback } })
    } catch (requestError) {
      if (requestError.response?.status === 409) await loadArt({ conflict: true })
      else setErrors({ submit: getRequestMessage(requestError, '작품 삭제에 실패했습니다.') })
    } finally {
      setDeleting(false)
    }
  }

  if (loading) return <State message="작품 정보를 불러오는 중입니다." />
  if (error || !form) return <State message={error} action={<button type="button" onClick={() => loadArt()}>다시 시도</button>} />

  return (
    <PageWrap>
      <PageBanner title="작품 수정·삭제" crumb="내 작품 관리" />
      <div className={formStyles.body}>
        <Link to={returnPath} className={styles.backLink}>← 내 작품 관리로</Link>
        <form className={formStyles.form} onSubmit={handleSubmit}>
          <section className={formStyles.card}>
            <h2 className={formStyles.cardTitle}>작품 정보</h2>
            <Field label="작품명"><input className={formStyles.input} aria-label="작품명" value={art.name} readOnly /><span className={styles.help}>작품명은 등록 후 수정할 수 없습니다.</span></Field>
            <Field label="작품 설명"><textarea className={formStyles.textarea} value={form.descript} onChange={setValue('descript')} maxLength={300} rows={4} /></Field>
            <div className={formStyles.classificationGrid}>
              <Field label="작품 형태 *" error={errors.format}><select className={formStyles.input} value={form.format} onChange={handleFormatChange} disabled={mutationUnavailable}><option value="">형태 선택</option><option value="DIGITAL">디지털</option><option value="PHYSICAL">실물</option></select></Field>
              <Field label="카테고리 *" error={errors.category}><select className={formStyles.input} value={form.category} onChange={setValue('category')} disabled={mutationUnavailable || !form.format}><option value="">카테고리 선택</option>{categoryOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
            </div>
            <Field label="재료·기법 *" error={errors.material}><input className={formStyles.input} value={form.material} onChange={setValue('material')} maxLength={120} disabled={mutationUnavailable} /></Field>
            <Field label="작가 소개"><textarea className={formStyles.textarea} value={form.wIntro} onChange={setValue('wIntro')} maxLength={500} rows={4} disabled={mutationUnavailable} /></Field>
          </section>

          <section className={formStyles.card}>
            <h2 className={formStyles.cardTitle}>작품 이미지</h2>
            <img className={formStyles.preview} src={previewUrl || getArtImageSrc(form.imgPath)} alt={`${art.name} 미리보기`} />
            <Field label="새 이미지 선택" error={errors.imgFile}><input className={formStyles.fileInput} type="file" accept="image/jpeg,image/png,image/webp" onChange={handleImageChange} disabled={mutationUnavailable} /><span className={styles.help}>선택하지 않으면 기존 이미지를 유지합니다.</span></Field>
          </section>

          <section className={formStyles.card}>
            <h2 className={formStyles.cardTitle}>경매 설정</h2>
            {hasBid && <p className={styles.lockNotice}>입찰 후에는 가격과 경매 기간을 수정할 수 없습니다.</p>}
            {!hasBid && auctionStarted && <p className={styles.lockNotice}>경매 시작 후에는 최소 입찰 증분을 수정할 수 없습니다.</p>}
            <Field label="시작가 *" error={errors.startPrice}><input className={formStyles.input} type="number" value={form.startPrice} onChange={setValue('startPrice')} disabled={priceAndPeriodLocked} /></Field>
            <Field label="최소 입찰 증분 *" error={errors.minimumBidIncrement}><input className={formStyles.input} type="number" step="100" value={form.minimumBidIncrement} onChange={setValue('minimumBidIncrement')} disabled={incrementLocked} /></Field>
            <div className={formStyles.timeGrid}>
              <Field label="입찰 시작 시간 *"><input className={formStyles.input} type="datetime-local" value={form.bidStartTime} onChange={setValue('bidStartTime')} disabled={priceAndPeriodLocked} /></Field>
              <Field label="입찰 종료 시간 *" error={errors.closingTime}><input className={formStyles.input} type="datetime-local" value={form.closingTime} onChange={setValue('closingTime')} disabled={priceAndPeriodLocked} /></Field>
            </div>
          </section>

          {mutationUnavailable && <p className={styles.unavailable} role="alert">종료되거나 취소된 작품은 수정하거나 삭제할 수 없습니다.</p>}
          {errors.submit && <p className={formStyles.submitError} role="alert">{errors.submit}</p>}
          <div className={styles.actions}>
            <button className={formStyles.submitBtn} type="submit" disabled={submitting || deleting || mutationUnavailable}>{submitting ? '저장 중...' : '수정 저장'}</button>
            <button className={styles.deleteButton} type="button" onClick={handleDelete} disabled={submitting || deleting || mutationUnavailable}>{deleting ? '삭제 중...' : '작품 삭제'}</button>
          </div>
        </form>
      </div>
    </PageWrap>
  )
}

function Field({ label, error, children }) {
  return <label className={formStyles.field}><span className={formStyles.fieldLabel}>{label}</span>{children}{error && <span className={formStyles.errMsg}>{error}</span>}</label>
}

function State({ message, action }) {
  return <main className={styles.state} aria-live="polite"><p>{message}</p>{action}</main>
}
