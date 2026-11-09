export const AUTH_STATE_CHANGED_EVENT = 'dailyatelier:auth-state-changed'

const AUTH_STORAGE_KEYS = ['token', 'userId', 'nickname', 'userStatus']

export const getStoredToken = () => localStorage.getItem('token')

export const getStoredUserStatus = () => localStorage.getItem('userStatus')

export const subscribeToAuthChanges = (listener) => {
  window.addEventListener(AUTH_STATE_CHANGED_EVENT, listener)
  window.addEventListener('storage', listener)

  return () => {
    window.removeEventListener(AUTH_STATE_CHANGED_EVENT, listener)
    window.removeEventListener('storage', listener)
  }
}

export const clearStoredAuth = () => {
  AUTH_STORAGE_KEYS.forEach((key) => localStorage.removeItem(key))
  window.dispatchEvent(new Event(AUTH_STATE_CHANGED_EVENT))
}
