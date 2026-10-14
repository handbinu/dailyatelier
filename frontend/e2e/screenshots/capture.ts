import { expect, type Page, type TestInfo } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

export type CaptureState = 'captured' | 'skipped' | 'failed'

type CaptureOptions = {
  key: string
  role: 'public' | 'buyer' | 'artist' | 'admin'
  route: string
  state?: string
  captureMode?: 'full-page' | 'viewport'
  prepare: () => Promise<void>
  skip?: {
    kind: 'fixture-unavailable'
    reason: string
  }
}

export async function capture(page: Page, testInfo: TestInfo, options: CaptureOptions) {
  const consoleErrors: string[] = []
  const onConsole = (message: { type: () => string; text: () => string }) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  }
  page.on('console', onConsole)
  const state = options.state ?? 'default'
  const project = testInfo.project.name
  const viewport = page.viewportSize()
  const result = {
    key: options.key, role: options.role, route: options.route, state, project,
    viewport, status: 'captured' as CaptureState, reason: '', file: '',
    captureMode: options.captureMode ?? 'full-page', consoleErrors, horizontalOverflow: false,
  }

  try {
    if (options.skip) {
      result.status = 'skipped'
      result.reason = options.skip.reason
      return result
    }
    await options.prepare()
    result.horizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > window.innerWidth,
    )
    const runId = process.env.UI_SCREENSHOT_RUN_ID
    if (!runId) throw new Error('UI_SCREENSHOT_RUN_ID가 없습니다.')
    const directory = path.resolve('ui-screenshots', runId, project.startsWith('desktop') ? 'desktop' : 'mobile')
    await mkdir(directory, { recursive: true })
    const filename = `${options.role}__${options.key}__${state}.png`
    const target = path.join(directory, filename)
    await page.screenshot({
      path: target,
      fullPage: result.captureMode === 'full-page',
      animations: 'disabled',
    })
    result.file = path.relative(path.resolve('ui-screenshots', runId), target).replaceAll('\\', '/')
    if (consoleErrors.length || result.horizontalOverflow) {
      result.status = 'failed'
      result.reason = [
        consoleErrors.length ? `console error ${consoleErrors.length}건` : '',
        result.horizontalOverflow ? 'horizontal overflow 감지' : '',
      ].filter(Boolean).join(', ')
    }
  } catch (error) {
    result.status = 'failed'
    result.reason = error instanceof Error ? error.message : String(error)
  } finally {
    page.off('console', onConsole)
    await testInfo.attach('ui-screenshot-capture', {
      contentType: 'application/json', body: Buffer.from(JSON.stringify(result)),
    })
  }
  return result
}

export async function expectCapture(result: Awaited<ReturnType<typeof capture>>) {
  expect(result.status, result.reason).not.toBe('failed')
}
