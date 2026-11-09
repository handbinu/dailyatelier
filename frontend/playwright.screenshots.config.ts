import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'

const runId = process.env.UI_SCREENSHOT_RUN_ID
if (!runId) throw new Error('UI_SCREENSHOT_RUN_ID가 필요합니다. npm run screenshots:ui로 실행하세요.')
const runDir = path.resolve('ui-screenshots', runId)

export default defineConfig({
  testDir: './e2e/screenshots',
  globalSetup: './e2e/global.setup.ts',
  outputDir: path.join(runDir, 'test-results'),
  workers: 1,
  reporter: [['list'], ['./e2e/screenshots/manifestReporter.ts', { runDir }]],
  use: { baseURL: process.env.UI_QA_BASE_URL || 'http://localhost:5173' },
  projects: [
    { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    { name: 'mobile-chromium', use: { ...devices['iPhone 13'], browserName: 'chromium', viewport: { width: 390, height: 844 } } },
  ],
})
