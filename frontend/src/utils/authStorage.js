export const AUTH_STATE_CHANGED_EVENT = 'dailyatelier:auth-state-changed'

const AUTH_STORAGE_KEYS = ['token', 'userId', 'nickname', 'userStatus']

export const clearStoredAuth = () => {
  AUTH_STORAGE_KEYS.forEach((key) => localStorage.removeItem(key))
  window.dispatchEvent(new Event(AUTH_STATE_CHANGED_EVENT))
}
