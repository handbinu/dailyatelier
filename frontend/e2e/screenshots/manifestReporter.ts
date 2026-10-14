import type { FullConfig, FullResult, Reporter, TestCase, TestResult } from '@playwright/test/reporter'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

type Record = { key: string; state: string; project: string; status: 'captured' | 'skipped' | 'failed'; [key: string]: unknown }

export default class ManifestReporter implements Reporter {
  private readonly runDir: string
  private readonly expected = new Map<string, { key: string; state: string; project: string }>()
  private readonly records = new Map<string, Record>()

  constructor(options: { runDir: string }) { this.runDir = options.runDir }

  onBegin(_config: FullConfig, suite: { allTests: () => TestCase[] }) {
    for (const test of suite.allTests()) {
      const capture = test.annotations.find((item) => item.type === 'ui-capture')?.description
      if (!capture) continue
      const [key, state] = capture.split(':')
      for (const project of test.parent.project()?.name ? [test.parent.project()!.name] : []) {
        this.expected.set(`${project}:${key}:${state}`, { project, key, state })
      }
    }
  }

  onTestEnd(test: TestCase, result: TestResult) {
    const attachment = result.attachments.find((item) => item.name === 'ui-screenshot-capture' && item.body)
    if (!attachment?.body) return
    const record = JSON.parse(attachment.body.toString()) as Record
    if (result.status !== 'passed' && record.status !== 'failed') {
      record.status = 'failed'
      record.reason = `Playwright ${result.status}`
    }
    this.records.set(`${record.project}:${record.key}:${record.state}`, record)
  }

  async onEnd(_result: FullResult) {
    await mkdir(this.runDir, { recursive: true })
    for (const record of this.records.values()) {
      const file = path.join(this.runDir, `manifest.${record.project}.json`)
      const current = await this.read(file)
      current.push(record)
      await writeFile(file, JSON.stringify(current, null, 2), 'utf8')
    }
    const records = [...this.records.values()]
    for (const [id, expected] of this.expected) {
      if (!this.records.has(id)) records.push({ ...expected, status: 'failed', reason: 'capture manifest 누락' })
    }
    const failed = records.some((record) => record.status === 'failed')
    await writeFile(path.join(this.runDir, 'manifest.json'), JSON.stringify({ records, failed }, null, 2), 'utf8')
    if (failed) process.exitCode = 1
  }

  private async read(file: string): Promise<Record[]> {
    try { return JSON.parse(await (await import('node:fs/promises')).readFile(file, 'utf8')) as Record[] } catch { return [] }
  }
}
