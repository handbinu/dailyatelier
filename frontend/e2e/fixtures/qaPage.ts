import { expect, test as base, type Page } from '@playwright/test'

export const test = base.extend<{ consoleErrors: string[] }>({
  consoleErrors: [
    async ({ page }, use) => {
      const errors: string[] = []
      const collectError = (message: { type: () => string; text: () => string }) => {
        if (message.type() === 'error') errors.push(message.text())
      }

      page.on('console', collectError)
      await use(errors)
      page.off('console', collectError)
      expect(errors, '브라우저 console error가 없어야 합니다.').toEqual([])
    },
    { auto: true },
  ],
})

export { expect }

export async function expectNoHorizontalOverflow(page: Page) {
  await expect.poll(
    () => page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth),
    { message: '페이지에 horizontal overflow가 없어야 합니다.' },
  ).toBe(false)
}
