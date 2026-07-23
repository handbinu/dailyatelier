import { useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { AUTH_STATE_CHANGED_EVENT } from '../../utils/authStorage'

function PrivateRoute (){
    const [, setAuthVersion] = useState(0)
    const token = localStorage.getItem('token')
    const location = useLocation()

    useEffect(() => {
        const handleAuthChange = () => setAuthVersion((version) => version + 1)
        window.addEventListener(AUTH_STATE_CHANGED_EVENT, handleAuthChange)
        return () => window.removeEventListener(AUTH_STATE_CHANGED_EVENT, handleAuthChange)
    }, [])

    if(!token){
        return <Navigate to="/login" state={{from:location}} replace/>
    }

    return <Outlet/>
}

export default PrivateRoute
