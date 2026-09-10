import { useCallback, useEffect, useRef, useState } from 'react'
import { PageBanner, Badge, Empty, FilterBar, PageWrap } from './components/atoms'
import { answerInquiry, getAdminInquiries, getInquiryDetail } from '../../api/inquiryApi'
import { createLatestRequest } from '../../utils/latestRequest'
import s from './InquiryList.module.css'

const FILTERS = [
  { label: '전체', value: 'ALL' },
  { label: '대기 중', value: 'PENDING' },
  { label: '답변 완료', value: 'ANSWERED' },
]
const TYPE_LABELS = { MEMBER: '회원정보', POINT: '포인트', ART: '작품', DELIVERY: '배송', AUCTION: '경매', OTHER: '기타' }
const TYPE_COLORS = { 배송: 'blue', 포인트: 'orange', 작품: 'green', 경매: 'blue', 회원정보: 'gray', 기타: 'gray' }

const formatDate = (value) => value ? new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
}).format(new Date(value)) : ''

export default function AdminInquiry() {
  const [filter, setFilter] = useState('ALL')
  const [inquiries, setInquiries] = useState([])
  const [selected, setSelected] = useState(null)
  const [answer, setAnswer] = useState('')
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [refreshError, setRefreshError] = useState('')
  const answerSubmitting = useRef(false)
  const detailRequest = useRef(null)

  const loadInquiries = useCallback(async (status = filter, { afterMutation = false } = {}) => {
    setLoading(true)
    if (afterMutation) setRefreshError('')
    else setError('')
    try {
      const { data } = await getAdminInquiries({ status, size: 50 })
      setInquiries(data.content ?? [])
      setRefreshError('')
      return true
    } catch (requestError) {
      const message = requestError.response?.data?.message || '문의 목록을 불러오지 못했습니다.'
      if (afterMutation) {
        setRefreshError(`답변은 등록됐지만 문의 목록을 갱신하지 못했습니다. ${message}`)
      } else {
        setError(message)
      }
      return false
    } finally {
      setLoading(false)
    }
  }, [filter])

  useEffect(() => {
    loadInquiries(filter)
  }, [filter, loadInquiries])

  useEffect(() => {
    const request = createLatestRequest()
    detailRequest.current = request
    return () => {
      request.dispose()
      if (detailRequest.current === request) detailRequest.current = null
    }
  }, [])

  const selectInquiry = (inquiryId) => {
    if (answerSubmitting.current) return
    return detailRequest.current?.run(
      async ({ signal }) => {
        const { data } = await getInquiryDetail(inquiryId, { signal })
        return data
      },
      {
        onStart: () => {
          setDetailLoading(true)
          setSelected(null)
          setAnswer('')
          setError('')
          setNotice('')
        },
        onSuccess: (data) => {
          setSelected(data)
          setAnswer(data.answer ?? '')
        },
        onError: (requestError) => {
          setError(requestError.response?.data?.message || '문의 상세 내용을 불러오지 못했습니다.')
        },
        onFinally: () => setDetailLoading(false),
      },
    )
  }

  const submitAnswer = async (event) => {
    event.preventDefault()
    if (answerSubmitting.current) return

    const request = {
      inquiryId: selected?.inquiryId,
      answer: answer.trim(),
      filter,
    }
    if (!request.inquiryId || !request.answer) {
      setError('답변 내용을 입력해 주세요.')
      return
    }
    answerSubmitting.current = true
    setSubmitting(true)
    setError('')
    setNotice('')
    setRefreshError('')
    try {
      const { data } = await answerInquiry(request.inquiryId, request.answer)
      setSelected(data)
      setAnswer(data.answer ?? '')
      setInquiries((current) => current.map((inquiry) => (
        inquiry.inquiryId === request.inquiryId
          ? { ...inquiry, answered: true, answeredAt: data.answeredAt }
          : inquiry
      )))
      setNotice('답변을 등록했습니다.')
    } catch (requestError) {
      setError(requestError.response?.data?.message || '답변 등록에 실패했습니다.')
      setSubmitting(false)
      answerSubmitting.current = false
      return
    }

    await loadInquiries(request.filter, { afterMutation: true })
    setSubmitting(false)
    answerSubmitting.current = false
  }

  return (
    <PageWrap>
      <PageBanner title="문의 관리" crumb="문의 관리" />
      <div className={`${s.body} ${s.adminBody}`}>
        <div aria-disabled={submitting} inert={submitting ? true : undefined}>
          <FilterBar options={FILTERS.map(option => option.label)} value={FILTERS.find(option => option.value === filter)?.label} onChange={(label) => {
            if (answerSubmitting.current) return
            setFilter(FILTERS.find(option => option.label === label).value)
          }} />
        </div>
        {notice && <div className={s.feedback} role="status">{notice}</div>}
        {refreshError && (
          <div className={s.feedback} role="alert">
            <p>{refreshError}</p>
            <button type="button" onClick={() => loadInquiries(filter, { afterMutation: true })} disabled={loading}>
              목록 다시 조회
            </button>
          </div>
        )}
        {error && <div className={s.feedback} role="alert">{error}</div>}
        {loading
          ? <div className={s.feedback} role="status">문의 목록을 불러오는 중입니다.</div>
          : inquiries.length === 0
            ? <Empty msg="조건에 맞는 문의가 없습니다." />
            : <div className={s.adminLayout}>
                <div className={s.list}>
                  {inquiries.map((inquiry) => {
                    const typeLabel = TYPE_LABELS[inquiry.inquiryType] ?? '기타'
                    return (
                      <button key={inquiry.inquiryId} className={`${s.adminItem} ${selected?.inquiryId === inquiry.inquiryId ? s.adminItemActive : ''}`} onClick={() => selectInquiry(inquiry.inquiryId)} disabled={submitting}>
                        <span className={s.adminItemMeta}><Badge label={typeLabel} color={TYPE_COLORS[typeLabel] ?? 'gray'} /> {inquiry.nickname} ({inquiry.userId})</span>
                        <strong>{inquiry.title}</strong>
                        <span>{inquiry.answered ? '답변 완료' : '대기 중'} · {formatDate(inquiry.createdAt)}</span>
                      </button>
                    )
                  })}
                </div>
                <section className={s.adminDetail} aria-live="polite">
                  {detailLoading && <p className={s.feedback}>문의 상세 내용을 불러오는 중입니다.</p>}
                  {!detailLoading && !selected && <p className={s.adminPlaceholder}>왼쪽 목록에서 문의를 선택하세요.</p>}
                  {!detailLoading && selected && <>
                    <h2>{selected.title}</h2>
                    <p className={s.adminMeta}>{selected.userId} · {formatDate(selected.createdAt)}</p>
                    <p className={s.adminContent}>{selected.content}</p>
                    {selected.attachmentUrl && (
                      <div style={{ marginTop: '16px', marginBottom: '16px' }}>
                        {selected.attachmentUrl.match(/\.(jpeg|jpg|gif|png)$/i) || selected.attachmentUrl.includes('image/upload') ? (
                          <img src={selected.attachmentUrl} alt="첨부 이미지" style={{ maxWidth: '100%', maxHeight: '400px', borderRadius: '8px', border: '1px solid var(--color-border)' }} />
                        ) : null}
                        <div style={{ marginTop: '8px' }}>
                          <a className={s.attachmentLink} href={selected.attachmentUrl} target="_blank" rel="noreferrer">
                            첨부 파일 다운로드: {selected.attachmentName || '파일 열기'}
                          </a>
                        </div>
                      </div>
                    )}
                    {selected.answered
                      ? <div className={s.aBlock}><div className={s.blockContent}><p className={s.blockText}>{selected.answer}</p><p className={s.blockMeta}>{formatDate(selected.answeredAt)} 답변 완료</p></div></div>
                      : <form className={s.answerForm} onSubmit={submitAnswer}>
                          <label htmlFor="admin-inquiry-answer">답변</label>
                          <textarea id="admin-inquiry-answer" value={answer} onChange={(event) => setAnswer(event.target.value)} maxLength={1000} rows={6} disabled={submitting} />
                          <button type="submit" disabled={submitting}>{submitting ? '등록 중…' : '답변 등록'}</button>
                        </form>}
                  </>}
                </section>
              </div>}
      </div>
    </PageWrap>
  )
}
