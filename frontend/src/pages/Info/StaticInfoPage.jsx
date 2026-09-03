import { Link } from 'react-router-dom'
import styles from './StaticInfoPage.module.css'

const LOGIN_TO_INQUIRY = {
  from: {
    pathname: '/mypage/inquiry/write',
    search: '',
    hash: '',
  },
}

const INFO_CONTENT = {
  qna: {
    eyebrow: 'CUSTOMER CENTER',
    title: '고객센터',
    intro: '문의가 필요하신 경우 1:1 문의를 이용해 주세요.',
    sections: [
      {
        title: '1:1 문의',
        body: '회원정보, 포인트, 작품, 배송, 경매 등 문의 유형을 선택해 내용을 작성할 수 있습니다.',
      },
      {
        title: '첨부 파일',
        body: 'JPG, PNG, PDF 파일을 최대 10MB까지 첨부할 수 있습니다.',
      },
      {
        title: '문의 확인',
        body: '등록한 문의와 답변은 마이페이지의 문의 현황에서 확인할 수 있습니다.',
      },
    ],
  },
  info: {
    eyebrow: 'AUCTION GUIDE',
    title: '경매 이용 안내',
    intro: 'Daily Atelier에서 작품을 탐색하고 경매에 참여하는 흐름을 안내합니다.',
    sections: [
      {
        title: '1. 작품 탐색',
        body: '전체 작품과 디지털·실물 작품 목록에서 관심 작품을 찾아 상세 정보를 확인합니다.',
        link: { to: '/auction/total', label: '전체 작품 보기' },
      },
      {
        title: '2. 로그인 후 입찰',
        body: '작품 상세 화면에서 로그인한 회원은 현재가와 최소 입찰 가능 금액을 확인하고 입찰할 수 있습니다.',
      },
      {
        title: '3. 낙찰 후 확인',
        body: '낙찰된 작품과 주문 상태는 마이페이지에서 확인할 수 있습니다.',
        link: { to: '/mypage/order-status', label: '주문 내역 보기' },
      },
    ],
  },
}

export default function StaticInfoPage({ type }) {
  const content = INFO_CONTENT[type]
  const isLoggedIn = Boolean(localStorage.getItem('token'))

  if (!content) return null

  return (
    <div className={styles.page}>
      <section className={styles.card} aria-labelledby="static-info-title">
        <p className={styles.eyebrow}>{content.eyebrow}</p>
        <h1 id="static-info-title">{content.title}</h1>
        <p className={styles.intro}>{content.intro}</p>

        <div className={styles.sections}>
          {content.sections.map((section) => (
            <section key={section.title} className={styles.section}>
              <h2>{section.title}</h2>
              <p>{section.body}</p>
              {section.link && <Link to={section.link.to} className={styles.textLink}>{section.link.label}</Link>}
            </section>
          ))}
        </div>

        {type === 'qna' && (
          <div className={styles.actions}>
            <Link
              to={isLoggedIn ? '/mypage/inquiry/write' : '/login'}
              state={isLoggedIn ? undefined : LOGIN_TO_INQUIRY}
              className={styles.primaryLink}
            >
              1:1 문의하기
            </Link>
            <Link to="/mypage/inquiry" className={styles.secondaryLink}>문의 내역</Link>
          </div>
        )}
      </section>
    </div>
  )
}
