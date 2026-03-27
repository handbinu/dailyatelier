import {BrowserRouter, Routes, Route, Navigate} from 'react-router-dom'
import Login from './pages/auth/Login'
import RegisterUser from './pages/auth/RegisterUser'
import RegisterArtist from './pages/auth/RegisterArtist'
import RegisterSelect from './pages/auth/RegisterSelect'
import PrivateRoute from './pages/auth/PrivateRoute'

const Home = () => <div>여기는 로그인해야만 보이는 홈 화면입니다! 🏠</div>;

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 기본 경로 -> 로그인으로 */}
        <Route path="/login" element={<Login />}/>
        <Route path="/register" element={<RegisterSelect/>}/>
        <Route path="/register/user" element={<RegisterUser/>}/>
        <Route path="/register/artist" element={<RegisterArtist/>}/>

        {/* 보호된 라우트 (토큰 없으면 /login으로)  */}
        <Route element={<PrivateRoute/>}>
          <Route path="/"        element={<Home />} />
        </Route>
        
        <Route path="*" element={<Navigate to="/" replace />}/>
      </Routes>
    </BrowserRouter>
  )
}

export default App
