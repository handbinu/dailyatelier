// src/pages/MyPage/InquiryList.jsx  —  내 문의 목록 + 상세 (아코디언)
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageBanner, Badge, FilterBar, Empty, PageWrap } from './components/atoms'
import { getInquiryDetail, getMyInquiries } from '../../api/inquiryApi'
import s from './InquiryList.module.css'

const FILTERS  = ['전체', '대기 중', '답변 완료']
const STATUS_COLOR = { true: 'green', false: 'orange' }
const TYPE_LABELS = { MEMBER: '회원정보', POINT: '포인트', ART: '작품', DELIVERY: '배송', AUCTION: '경매', OTHER: '기타' }
const TYPE_COLORS  = { 배송: 'blue', 포인트: 'orange', 작품: 'green', 경매: 'blue', 회원정보: 'gray', 기타: 'gray' }

const formatDate = (value) => value ? new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date(value)) : ''

export default function InquiryList() {
  const [filter, setFilter] = useState('전체')
  const [open,   setOpen]   = useState(null)
  const [inquiries, setInquiries] = useState([])
  const [details, setDetails] = useState({})
  const [loading, setLoading] = useState(true)
  const [loadingDetailId, setLoadingDetailId] = useState(null)
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    const loadInquiries = async () => {
      setLoading(true)
      setError('')
      try {
        const { data } = await getMyInquiries({ size: 50, signal: controller.signal })
        setInquiries(data.content ?? [])
      } catch (requestError) {
        if (requestError.code !== 'ERR_CANCELED') {
          setError(requestError.response?.data?.message || '문의 내역을 불러오지 못했습니다.')
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }
    loadInquiries()
    return () => controller.abort()
  }, [])

  const items = filter === '전체'
    ? inquiries
    : filter === '대기 중'
      ? inquiries.filter(q => !q.answered)
      : inquiries.filter(q => q.answered)

  const answered = inquiries.filter(q => q.answered).length
  const unanswered = inquiries.filter(q => !q.answered).length

  const handleToggle = async (inquiryId) => {
    if (open === inquiryId) {
      setOpen(null)
      return
    }
    setOpen(inquiryId)
    setDetailError('')
    if (details[inquiryId]) return
    setLoadingDetailId(inquiryId)
    try {
      const { data } = await getInquiryDetail(inquiryId)
      setDetails(current => ({ ...current, [inquiryId]: data }))
    } catch (requestError) {
      setDetailError(requestError.response?.data?.message || '문의 상세 내용을 불러오지 못했습니다.')
    } finally {
      setLoadingDetailId(null)
    }
  }

  return (
    <PageWrap>
      <PageBanner title="문의 현황" crumb="문의 현황" />

      <div className={s.body}>
        <div className={s.topRow}>
          <div className={s.stats}>
            <div className={s.statChip} style={{ '--c': '#c0622a' }}>
              <span className={s.statNum}>{unanswered}</span>
              <span className={s.statLbl}>대기 중</span>
            </div>
            <div className={s.statChip} style={{ '--c': '#1e8c4f' }}>
              <span className={s.statNum}>{answered}</span>
              <span className={s.statLbl}>답변 완료</span>
            </div>
          </div>
          <Link to="/mypage/inquiry/write" className={s.writeBtn}>
            + 새 문의 작성
          </Link>
        </div>

        <FilterBar options={FILTERS} value={filter} onChange={setFilter} />

        {loading
          ? <div className={s.feedback} role="status">문의 내역을 불러오는 중입니다.</div>
          : error
            ? <div className={s.feedback} role="alert">{error}</div>
          : items.length === 0
          ? <Empty msg="문의 내역이 없습니다." />
          : <div className={s.list}>
              {items.map(q => (
                <InquiryItem
                  key={q.inquiryId}
                  inquiry={q}
                  detail={details[q.inquiryId]}
                  isOpen={open === q.inquiryId}
                  isLoading={loadingDetailId === q.inquiryId}
                  error={open === q.inquiryId ? detailError : ''}
                  onToggle={() => handleToggle(q.inquiryId)}
                />
              ))}
            </div>
        }

        <div className={s.notice}>
          <span className={s.noticeIcon}>ℹ️</span>
          영업일 기준 1~3일 내 답변 드립니다. 긴급한 문의는{' '}
          <a href="mailto:support@dailyatelier.com" className={s.noticeLink}>
            support@dailyatelier.com
          </a>
          으로 연락주세요.
        </div>

        <div className={s.backRow}>
          <Link to="/mypage" className={s.backLink}>← 마이페이지로 돌아가기</Link>
        </div>
      </div>
    </PageWrap>
  )
}

/* ── 개별 문의 아코디언 아이템 ─────────────────────────── */
function InquiryItem({ inquiry: q, detail, isOpen, isLoading, error, onToggle }) {
  const typeLabel = TYPE_LABELS[q.inquiryType] ?? '기타'
  return (
    <div className={`${s.item} ${isOpen ? s.itemOpen : ''}`}>
      {/* 헤더 버튼 */}
      <button
        className={s.trigger}
        onClick={onToggle}
        aria-expanded={isOpen}
      >
        <div className={s.triggerLeft}>
          <Badge
            label={typeLabel}
            color={TYPE_COLORS[typeLabel] ?? 'gray'}
          />
          <span className={s.triggerTitle}>{q.title}</span>
        </div>
        <div className={s.triggerRight}>
          <Badge
            label={q.answered ? '답변 완료' : '대기 중'}
            color={STATUS_COLOR[q.answered]}
          />
          <span className={s.triggerDate}>{formatDate(q.createdAt)}</span>
          <span className={`${s.chevron} ${isOpen ? s.chevronOpen : ''}`}>›</span>
        </div>
      </button>

      {/* 펼쳐진 내용 */}
      {isOpen && (
        <div className={s.body_}>
          {isLoading && <div className={s.feedback} role="status">문의 상세 내용을 불러오는 중입니다.</div>}
          {error && <div className={s.feedback} role="alert">{error}</div>}
          {detail && <>
          <div className={s.qBlock}>
            <div className={s.blockLabel}>
              <span className={s.qMark}>Q</span>
              <span>질문</span>
            </div>
            <div className={s.blockContent}>
              <p className={s.blockTitle}>{detail.title}</p>
              <p className={s.blockText}>{detail.content}</p>
              {detail.attachmentUrl && (
                <div style={{ marginTop: '12px', marginBottom: '12px' }}>
                  {detail.attachmentUrl.match(/\.(jpeg|jpg|gif|png)$/i) || detail.attachmentUrl.includes('image/upload') ? (
                    <img src={detail.attachmentUrl} alt="첨부 이미지" style={{ maxWidth: '100%', maxHeight: '400px', borderRadius: '8px', border: '1px solid var(--color-border)' }} />
                  ) : null}
                  <div style={{ marginTop: '8px' }}>
                    <a className={s.attachmentLink} href={detail.attachmentUrl} target="_blank" rel="noreferrer">
                      첨부 파일 다운로드: {detail.attachmentName || '파일 열기'}
                    </a>
                  </div>
                </div>
              )}
              <p className={s.blockMeta}>{formatDate(detail.createdAt)} 작성</p>
            </div>
          </div>

          {detail.answered
            ? <div className={s.aBlock}>
                <div className={s.blockLabel}>
                  <span className={s.aMark}>A</span>
                  <span>답변</span>
                </div>
                <div className={s.blockContent}>
                  <p className={s.blockText}>{detail.answer}</p>
                  <p className={s.blockMeta}>데일리 아틀리에 고객센터 · {formatDate(detail.answeredAt)} 답변 완료</p>
                </div>
              </div>
            : <div className={s.pendingBlock}>
                <span className={s.pendingIcon}>⏳</span>
                <p className={s.pendingText}>
                  아직 답변이 등록되지 않았습니다.
                  <br />
                  <span className={s.pendingNote}>영업일 기준 1~3일 내로 답변 드리겠습니다.</span>
                </p>
              </div>
          }
          </>}
        </div>
      )}
    </div>
  )
}
