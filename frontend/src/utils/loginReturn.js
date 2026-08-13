const AUTH_PATHS = new Set([
  '/login',
  '/register',
  '/register/user',
  '/register/artist',
])

const isSafePathname = (pathname) => (
  typeof pathname === 'string'
  && pathname.startsWith('/')
  && !pathname.startsWith('//')
  && !pathname.includes('\\')
  && !pathname.includes('?')
  && !pathname.includes('#')
  && !AUTH_PATHS.has(pathname.toLowerCase())
)

export const toSafeReturnPath = (from) => {
  if (typeof from === 'string') {
    if (!from.startsWith('/') || from.startsWith('//') || from.includes('\\')) return '/'

    try {
      const url = new URL(from, 'https://dailyatelier.local')
      if (url.origin !== 'https://dailyatelier.local' || !isSafePathname(url.pathname)) return '/'
      return `${url.pathname}${url.search}${url.hash}`
    } catch {
      return '/'
    }
  }

  if (!from || typeof from !== 'object' || !isSafePathname(from.pathname)) return '/'

  const search = typeof from.search === 'string'
    && (from.search === '' || (from.search.startsWith('?') && !from.search.includes('#')))
    ? from.search
    : ''
  const hash = typeof from.hash === 'string'
    && (from.hash === '' || from.hash.startsWith('#'))
    ? from.hash
    : ''

  return `${from.pathname}${search}${hash}`
}

export const createLoginState = (location) => ({
  from: {
    pathname: location.pathname,
    search: location.search,
    hash: location.hash,
  },
})

export const getLoginReturnPath = (state) => toSafeReturnPath(state?.from)
