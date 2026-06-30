// src/pages/MyPage/components/atoms.jsx
// ── 공유 원자 컴포넌트 ──────────────────────────────────────────
// 규칙:
//  - 이모지 사용 금지 (디자인 시스템 룰)
//  - PageWrap 은 Layout 헤더와 중복되지 않도록 배경 컨테이너만 담당
//  - Empty 의 아이콘은 SVG 인라인으로 처리
import { Link } from 'react-router-dom'
import s from './atoms.module.css'

// ── PageBanner ───────────────────────────────────────────────────
// 각 서브페이지 최상단 타이틀 배너 (다크 배경 + 브레드크럼)
export function PageBanner({ title, crumb }) {
  return (
    <div className={s.banner}>
      <div className={s.bannerInner}>
        <span className={s.breadcrumb}>홈 · 마이페이지 · {crumb}</span>
        <h1 className={s.title}>{title}</h1>
      </div>
    </div>
  )
}

// ── Badge ────────────────────────────────────────────────────────
// color: 'green' | 'orange' | 'blue' | 'gray' | 'red'
export function Badge({ label, color = 'gray' }) {
  return <span className={`${s.badge} ${s[`badge_${color}`]}`}>{label}</span>
}

// ── StarDisplay ──────────────────────────────────────────────────
// 0~10점 범위. 별 5개 기준으로 반올림 표시
export function StarDisplay({ star }) {
  const filled = Math.round(star / 2)
  return (
    <span className={s.star} title={`${star} / 10`} aria-label={`별점 ${star}점`}>
      {'★'.repeat(filled)}{'☆'.repeat(5 - filled)}
      <em className={s.starNum}>{star}</em>
    </span>
  )
}

// ── Empty ────────────────────────────────────────────────────────
// 빈 상태 플레이스홀더. 이모지 대신 SVG 아이콘 사용
export function Empty({ msg = '내역이 없습니다.' }) {
  return (
    <div className={s.empty}>
      <svg className={s.emptyIcon} viewBox="0 0 48 48" fill="none" aria-hidden="true">
        <rect x="8" y="14" width="32" height="26" rx="3" stroke="currentColor" strokeWidth="1.5"/>
        <path d="M16 14V10a8 8 0 0116 0v4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
        <circle cx="24" cy="27" r="3" stroke="currentColor" strokeWidth="1.5"/>
      </svg>
      <p className={s.emptyMsg}>{msg}</p>
    </div>
  )
}

// ── FilterBar ────────────────────────────────────────────────────
// 가로 필터 탭 버튼 행
export function FilterBar({ options, value, onChange }) {
  return (
    <div className={s.filterBar} role="group" aria-label="필터">
      {options.map((opt) => (
        <button
          key={opt}
          className={`${s.filterBtn} ${value === opt ? s.filterBtnActive : ''}`}
          onClick={() => onChange(opt)}
          aria-pressed={value === opt}
        >
          {opt}
        </button>
      ))}
    </div>
  )
}

// ── SortSelect ───────────────────────────────────────────────────
export function SortSelect({ options, value, onChange }) {
  return (
    <select
      className={s.sortSelect}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      aria-label="정렬 방식"
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  )
}

// ── ArtThumb ─────────────────────────────────────────────────────
// 비율 고정 이미지 래퍼
export function ArtThumb({ src, alt, ratio = '1/1' }) {
  return (
    <div className={s.thumb} style={{ aspectRatio: ratio }}>
      <img src={src} alt={alt} loading="lazy" />
    </div>
  )
}

// ── PageWrap ─────────────────────────────────────────────────────
// 각 서브페이지 루트 컨테이너. Layout 의 Header/Footer 와 중복되지 않음.
export function PageWrap({ children }) {
  return <div className={s.pageWrap}>{children}</div>
}

// ── CardGrid ─────────────────────────────────────────────────────
export function CardGrid({ children, cols = 3 }) {
  return (
    <div className={s.cardGrid} style={{ '--cols': cols }}>
      {children}
    </div>
  )
}

// ── ActionBtn ────────────────────────────────────────────────────
// variant: 'outline' | 'fill' | 'accent' | 'danger'
export function ActionBtn({ children, to, onClick, variant = 'outline', disabled }) {
  const cls = `${s.actionBtn} ${s[`actionBtn_${variant}`]}`
  if (to) return <Link to={to} className={cls}>{children}</Link>
  return (
    <button className={cls} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  )
}