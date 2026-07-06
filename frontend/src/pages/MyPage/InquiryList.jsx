// src/pages/MyPage/InquiryList.jsx  —  내 문의 목록 + 상세 (아코디언)
import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { PageBanner, Badge, FilterBar, Empty, PageWrap } from './components/atoms'
import { MOCK_INQUIRIES } from './mockData'
import s from './InquiryList.module.css'

const FILTERS  = ['전체', '대기 중', '답변 완료']
const STATUS_COLOR = { true: 'green', false: 'orange' }
const TYPE_COLORS  = { '배송': 'blue', '포인트': 'orange', '작품': 'green', '경매': 'blue', '회원정보': 'gray', '기타': 'gray' }

export default function InquiryList() {
  const navigate = useNavigate()
  if (!localStorage.getItem('token')) {
    alert('로그인이 필요합니다.')
    navigate('/login', { replace: true })
    return null
  }

  const [filter, setFilter] = useState('전체')
  const [open,   setOpen]   = useState(null)

  const items = filter === '전체'
    ? MOCK_INQUIRIES
    : filter === '대기 중'
      ? MOCK_INQUIRIES.filter(q => !q.answered)
      : MOCK_INQUIRIES.filter(q => q.answered)

  const answered   = MOCK_INQUIRIES.filter(q => q.answered).length
  const unanswered = MOCK_INQUIRIES.filter(q => !q.answered).length

  return (
    <PageWrap>
      <PageBanner title="문의 현황" crumb="문의 현황" />

      <div className={s.body}>
        <div style={{
          backgroundColor: '#fff9db',
          border: '1px solid #ffe066',
          borderRadius: '8px',
          padding: '12px 16px',
          fontSize: '14px',
          color: '#f08c00',
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          marginBottom: '20px',
          lineHeight: '1.5'
        }}>
          ⚠️ 해당 기능은 현재 준비 중이며, 화면의 데이터는 임시 목업 데이터입니다.
        </div>
        {/* 상단 통계 + 작성 버튼 */}
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

        {items.length === 0
          ? <Empty msg="문의 내역이 없습니다." />
          : <div className={s.list}>
              {items.map(q => (
                <InquiryItem
                  key={q.id}
                  inquiry={q}
                  isOpen={open === q.id}
                  onToggle={() => setOpen(prev => prev === q.id ? null : q.id)}
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
function InquiryItem({ inquiry: q, isOpen, onToggle }) {
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
            label={q.type}
            color={TYPE_COLORS[q.type] ?? 'gray'}
          />
          <span className={s.triggerTitle}>{q.title}</span>
        </div>
        <div className={s.triggerRight}>
          <Badge
            label={q.answered ? '답변 완료' : '대기 중'}
            color={STATUS_COLOR[q.answered]}
          />
          <span className={s.triggerDate}>{q.createdAt}</span>
          <span className={`${s.chevron} ${isOpen ? s.chevronOpen : ''}`}>›</span>
        </div>
      </button>

      {/* 펼쳐진 내용 */}
      {isOpen && (
        <div className={s.body_}>
          {/* 질문 블록 */}
          <div className={s.qBlock}>
            <div className={s.blockLabel}>
              <span className={s.qMark}>Q</span>
              <span>질문</span>
            </div>
            <div className={s.blockContent}>
              <p className={s.blockTitle}>{q.title}</p>
              <p className={s.blockText}>
                {/* 실제 연동 시 q.content 사용 */}
                {q.title}에 대한 자세한 문의 내용이 여기에 표시됩니다.
              </p>
              <p className={s.blockMeta}>{q.createdAt} 작성</p>
            </div>
          </div>

          {/* 답변 블록 */}
          {q.answered
            ? <div className={s.aBlock}>
                <div className={s.blockLabel}>
                  <span className={s.aMark}>A</span>
                  <span>답변</span>
                </div>
                <div className={s.blockContent}>
                  <p className={s.blockText}>{q.answer}</p>
                  <p className={s.blockMeta}>데일리 아틀리에 고객센터 · 답변 완료</p>
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
        </div>
      )}
    </div>
  )
}