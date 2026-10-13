import { adminTest as test } from '../fixtures/auth'
import { expectNoHorizontalOverflow, expect } from '../fixtures/qaPage'

test('관리자가 문의 관리 fixture를 조회한다', async ({ page }) => {
  await page.goto('/admin/inquiries')
  await expect(page.getByRole('heading', { name: '문의 관리' })).toBeVisible()
  await expect(page.getByText(/QA 문의 목록 모바일 줄바꿈/)).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
