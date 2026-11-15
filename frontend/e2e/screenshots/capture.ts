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
  readiness?: () => Promise<void>
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
    await options.readiness?.()
    await waitForCaptureImages(page, result.captureMode)
    result.horizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > window.innerWidth,
    )
    const runId = process.env.UI_SCREENSHOT_RUN_ID
    if (!runId) throw new Error('UI_SCREENSHOT_RUN_ID가 없습니다.')
    const directory = path.resolve('ui-screenshots', runId, project.startsWith('desktop') ? 'desktop' : 'mobile')
    await mkdir(directory, { recursive: true })
    const filename = `${options.role}__${options.key}__${state}.png`
    const target = path.join(directory, filename)
    await withFullPageHeaderInFlow(page, result.captureMode, async () => {
      await page.screenshot({
        path: target,
        fullPage: result.captureMode === 'full-page',
        animations: 'disabled',
      })
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

async function waitForCaptureImages(page: Page, captureMode: 'full-page' | 'viewport') {
  const outcome = await page.evaluate(async ({ captureMode, timeoutMs }) => {
    const isVisibleCaptureImage = (image: HTMLImageElement) => {
      if (!image.getAttribute('src') || image.closest('[aria-hidden="true"]')) return false
      const style = window.getComputedStyle(image)
      const rect = image.getBoundingClientRect()
      return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0
    }
    const inViewport = (image: HTMLImageElement) => {
      const rect = image.getBoundingClientRect()
      return rect.bottom > 0 && rect.right > 0 && rect.top < window.innerHeight && rect.left < window.innerWidth
    }
    const scope = captureMode === 'viewport'
      ? document.querySelector('[role="dialog"]') ?? document
      : document.querySelector('#root') ?? document
    const images = Array.from(scope.querySelectorAll('img')).filter(isVisibleCaptureImage)
      .filter((image) => captureMode === 'full-page' || inViewport(image))
    const scroll = { x: window.scrollX, y: window.scrollY }

    try {
      if (captureMode === 'full-page') {
        for (const image of images) {
          image.scrollIntoView({ block: 'center', inline: 'nearest' })
          await new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
        }
      }

      const checkImage = async (image: HTMLImageElement) => {
        const deadline = Date.now() + timeoutMs
        let source = image.currentSrc || image.src
        while (Date.now() < deadline) {
          if (!image.complete) {
            await new Promise((resolve) => setTimeout(resolve, 50))
            continue
          }
          if (image.naturalWidth <= 0) {
            await new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
            const nextSource = image.currentSrc || image.src
            if (nextSource !== source) {
              source = nextSource
              continue
            }
            throw new Error(`${image.alt || source}: naturalWidth is 0`)
          }
          try {
            await image.decode()
          } catch {
            throw new Error(`${image.alt || source}: decode failed`)
          }
          const nextSource = image.currentSrc || image.src
          if (nextSource === source) return
          source = nextSource
        }
        throw new Error(`${image.alt || source}: image readiness timed out`)
      }
      await Promise.all(images.map(checkImage))
      return { count: images.length }
    } catch (error) {
      return { error: error instanceof Error ? error.message : String(error) }
    } finally {
      window.scrollTo(scroll.x, scroll.y)
    }
  }, { captureMode, timeoutMs: 12_000 })
  if ('error' in outcome) throw new Error(`image readiness failed: ${outcome.error}`)
}

async function withFullPageHeaderInFlow(
  page: Page,
  captureMode: 'full-page' | 'viewport',
  screenshot: () => Promise<void>,
) {
  if (captureMode !== 'full-page') return screenshot()
  const originalStyle = await page.evaluate(() => {
    const header = document.querySelector<HTMLElement>('#root > header, #root > div > header')
    if (!header || !['fixed', 'sticky'].includes(window.getComputedStyle(header).position)) return undefined
    const style = header.getAttribute('style')
    header.style.position = 'absolute'
    header.style.top = '0'
    header.style.left = '0'
    header.style.right = '0'
    return style
  })
  try {
    await screenshot()
  } finally {
    await page.evaluate((style) => {
      const header = document.querySelector<HTMLElement>('#root > header, #root > div > header')
      if (!header || style === undefined) return
      if (style === null) header.removeAttribute('style')
      else header.setAttribute('style', style)
    }, originalStyle)
  }
}

export async function expectCapture(result: Awaited<ReturnType<typeof capture>>) {
  expect(result.status, result.reason).not.toBe('failed')
}
