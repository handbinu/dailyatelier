import { BrowserRouter, Routes, Route, Navigate, Link } from 'react-router-dom'
import Layout      from './layouts/Layout'
import PrivateRoute from './pages/auth/PrivateRoute'
import ArtistRoute from './pages/auth/ArtistRoute'
import styles from './App.module.css'

// ── 인증
import Login          from './pages/auth/Login'
import RegisterSelect from './pages/auth/RegisterSelect'
import RegisterUser   from './pages/auth/RegisterUser'
import RegisterArtist from './pages/auth/RegisterArtist'

// ── 메인
import Home from './pages/Home/Home'

// ── 마이페이지 허브
import MyPage from './pages/MyPage/MyPage'

// ── 마이페이지 세부 — 공통 (일반·작가 모두)
import BidStatus      from './pages/MyPage/BidStatus'
import Likes          from './pages/MyPage/Likes'
import SuccessfulBid  from './pages/MyPage/SuccessfulBid'
import MyReview       from './pages/MyPage/MyReview'
import WriteReview    from './pages/MyPage/WriteReview'
import ProfileEdit    from './pages/MyPage/ProfileEdit'
import Charge         from './pages/MyPage/Charge'
import OrderStatus    from './pages/MyPage/OrderStatus'
import InquiryList    from './pages/MyPage/InquiryList'
import InquiryWrite   from './pages/MyPage/InquiryWrite'

// ── 마이페이지 세부 — 작가 전용
import UploadSell   from './pages/MyPage/UploadSell'
import ManageArts   from './pages/MyPage/ManageArts'
import ArtistReview from './pages/MyPage/ArtistReview.jsx'
import SalesOrders  from './pages/MyPage/SalesOrders'
import ArtDetail    from './pages/Auction/ArtDetail'
import AuctionTotal from './pages/Auction/AuctionTotal'
import ArtistList   from './pages/Artist/ArtistList'
import ArtistDetail from './pages/Artist/ArtistDetail'
import ArtSearch from './pages/Search/ArtSearch'

function PreparingPage({ title }) {
  return (
    <main className={styles.preparingPage}>
      <div className={styles.preparingCard}>
        <p className={styles.preparingEyebrow}>COMING SOON</p>
        <h1>{title}</h1>
        <p className={styles.preparingMessage}>
          더 나은 서비스로 준비하고 있습니다.<br />
          현재 이용 가능한 작품과 작가를 먼저 둘러보세요.
        </p>
        <div className={styles.preparingActions}>
          <Link to="/auction/total" className={styles.primaryLink}>전체 작품 보기</Link>
          <Link to="/" className={styles.secondaryLink}>홈으로 돌아가기</Link>
        </div>
      </div>
    </main>
  )
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/*
         * Layout이 모든 라우트를 감쌉니다.
         * ─ BARE_ROUTES(로그인·회원가입) : 헤더·푸터 없음
         * ─ 그 외 모든 페이지             : Header + Footer 항상 표시
         */}
        <Route element={<Layout />}>

          {/* ── 공개 인증 페이지 ── */}
          <Route path="/login"           element={<Login />} />
          <Route path="/register"        element={<RegisterSelect />} />
          <Route path="/register/user"   element={<RegisterUser />} />
          <Route path="/register/artist" element={<RegisterArtist />} />

          {/* ── 공개 페이지 ── */}
          <Route path="/"                element={<Home />} />
          <Route path="/notice"          element={<PreparingPage title="공지 사항" />} />
          <Route path="/event"           element={<PreparingPage title="이벤트 안내" />} />
          <Route path="/auction/total"   element={<AuctionTotal />} />
          <Route path="/auction/digital" element={<AuctionTotal type="digital" />} />
          <Route path="/auction/analog"  element={<AuctionTotal type="analog" />} />
          <Route path="/auction/artist"  element={<PreparingPage title="작가별 작품" />} />
          <Route path="/auction/:id"     element={<ArtDetail />} />
          <Route path="/artists"         element={<ArtistList />} />
          <Route path="/artists/:artistId" element={<ArtistDetail />} />
          <Route path="/artist-introduce" element={<PreparingPage title="작가소개" />} />
          <Route path="/developer"       element={<PreparingPage title="개발자 소개" />} />
          <Route path="/info"            element={<PreparingPage title="경매 진행방법" />} />
          <Route path="/qna"             element={<PreparingPage title="고객센터" />} />
          <Route path="/q-list"          element={<PreparingPage title="Q&A" />} />
          <Route path="/search"          element={<ArtSearch />} />

          {/* ── 보호된 라우트 (로그인 필수) ── */}
          <Route element={<PrivateRoute />}>

            {/* ── 마이페이지 ── */}
            <Route path="/mypage"                element={<MyPage />} />
            <Route path="/reliable-status"       element={<BidStatus />} />

            {/* 공통 세부 페이지 */}
            <Route path="/mypage/bid-status"     element={<BidStatus />} />
            <Route path="/mypage/likes"          element={<Likes />} />
            <Route path="/mypage/successful-bid" element={<SuccessfulBid />} />
            <Route path="/mypage/my-review"      element={<MyReview />} />
            <Route path="/mypage/profile-edit"   element={<ProfileEdit />} />
            <Route path="/mypage/order-status"   element={<OrderStatus />} />
            <Route path="/mypage/inquiry"        element={<InquiryList />} />
            <Route path="/mypage/inquiry/write"  element={<InquiryWrite />} />
            <Route path="/charge"                element={<Charge />} />

            {/* 리뷰 작성/수정 — artId 파라미터 */}
            <Route path="/write-review/:artId"   element={<WriteReview />} />

            {/* 작가 전용 */}
            <Route element={<ArtistRoute />}>
              <Route path="/upload"                element={<UploadSell />} />
              <Route path="/mypage/manage-arts"    element={<ManageArts />} />
              <Route path="/mypage/artist-review"  element={<ArtistReview />} />
              <Route path="/mypage/sales-orders"   element={<SalesOrders />} />
            </Route>

          </Route>

          {/* fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />

        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
