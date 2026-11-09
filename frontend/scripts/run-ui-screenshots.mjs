import { spawnSync } from 'node:child_process'

const runId = new Date().toISOString().replace(/[:.]/g, '-')
const result = spawnSync(
  'npx',
  ['playwright', 'test', '--config=playwright.screenshots.config.ts', ...process.argv.slice(2)],
  { stdio: 'inherit', shell: process.platform === 'win32', env: { ...process.env, UI_SCREENSHOT_RUN_ID: runId } },
)

process.exit(result.status ?? 1)
