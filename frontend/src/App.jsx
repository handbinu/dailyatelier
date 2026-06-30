import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Layout from './layouts/Layout'
import Login from './pages/auth/Login'
import RegisterSelect from './pages/auth/RegisterSelect'
import RegisterUser from './pages/auth/RegisterUser'
import RegisterArtist from './pages/auth/RegisterArtist'
import Home from './pages/Home/Home'

function PreparingPage({ title }) {
  return (
    <div style={{ maxWidth: '960px', margin: '6rem auto', padding: '0 1.5rem' }}>
      <h1 style={{ marginBottom: '0.75rem' }}>{title}</h1>
      <p style={{ color: '#666' }}>해당 페이지는 현재 정리 중입니다.</p>
    </div>
  )
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<RegisterSelect />} />
          <Route path="/register/user" element={<RegisterUser />} />
          <Route path="/register/artist" element={<RegisterArtist />} />

          <Route path="/" element={<Home />} />
          <Route path="/notice" element={<PreparingPage title="공지 사항" />} />
          <Route path="/event" element={<PreparingPage title="이벤트 안내" />} />
          <Route path="/auction/total" element={<PreparingPage title="전체 경매" />} />
          <Route path="/auction/digital" element={<PreparingPage title="디지털 경매" />} />
          <Route path="/auction/analog" element={<PreparingPage title="실물 경매" />} />
          <Route path="/auction/artist" element={<PreparingPage title="작가별 작품" />} />
          <Route path="/auction/:id" element={<PreparingPage title="작품 상세" />} />
          <Route path="/artist-introduce" element={<PreparingPage title="작가소개" />} />
          <Route path="/developer" element={<PreparingPage title="개발자 소개" />} />
          <Route path="/info" element={<PreparingPage title="경매 진행방법" />} />
          <Route path="/qna" element={<PreparingPage title="고객센터" />} />
          <Route path="/q-list" element={<PreparingPage title="Q&A" />} />
          <Route path="/search" element={<PreparingPage title="검색 결과" />} />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App