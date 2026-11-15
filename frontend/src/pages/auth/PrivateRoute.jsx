import { useSyncExternalStore } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { getStoredToken, subscribeToAuthChanges } from '../../utils/authStorage'
import { createLoginState } from '../../utils/loginReturn'

function PrivateRoute (){
    const token = useSyncExternalStore(subscribeToAuthChanges, getStoredToken)
    const location = useLocation()

    if(!token){
        return <Navigate to="/login" state={createLoginState(location)} replace/>
    }

    return <Outlet/>
}

export default PrivateRoute
