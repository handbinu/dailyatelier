import { chromium, request } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const storageDirectory = path.resolve('.playwright')
const baseURL = process.env.UI_QA_BASE_URL || 'http://localhost:5173'
const apiBaseURL = process.env.UI_QA_API_BASE_URL || 'http://localhost:8080'

const roles = {
  buyer: ['UI_QA_BUYER_ID', 'UI_QA_BUYER_PASSWORD'],
  artist: ['UI_QA_ARTIST_ID', 'UI_QA_ARTIST_PASSWORD'],
  admin: ['UI_QA_ADMIN_ID', 'UI_QA_ADMIN_PASSWORD'],
} as const

function credential(role: keyof typeof roles) {
  const [idKey, passwordKey] = roles[role]
  const userId = process.env[idKey]
  const password = process.env[passwordKey]

  if (!userId || !password) {
    throw new Error(`${idKey}와 ${passwordKey} 환경변수가 필요합니다.`)
  }

  return { userId, password }
}

export default async function globalSetup() {
  await mkdir(storageDirectory, { recursive: true })
  const api = await request.newContext({ baseURL: apiBaseURL })
  const browser = await chromium.launch()

  try {
    for (const role of Object.keys(roles) as Array<keyof typeof roles>) {
      const response = await api.post('/api/auth/login', { data: credential(role) })
      if (!response.ok()) {
        throw new Error(`${role} QA 계정 로그인에 실패했습니다: ${response.status()}`)
      }

      const auth = await response.json()
      const context = await browser.newContext()
      const page = await context.newPage()
      await page.goto(baseURL, { waitUntil: 'domcontentloaded' })
      await page.evaluate((value) => {
        localStorage.setItem('token', value.token)
        localStorage.setItem('userId', value.userId)
        localStorage.setItem('nickname', value.nickname)
        localStorage.setItem('userStatus', String(value.userStatus))
      }, auth)
      await context.storageState({ path: path.join(storageDirectory, `${role}.json`) })
      await context.close()
    }
  } finally {
    await api.dispose()
    await browser.close()
  }
}
