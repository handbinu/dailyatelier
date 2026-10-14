import { buyerTest as test } from '../fixtures/auth'
import { expectNoHorizontalOverflow, expect } from '../fixtures/qaPage'

test('구매자가 문의 현황 fixture를 조회한다', async ({ page }) => {
  await page.goto('/mypage/inquiry')
  await expect(page.getByRole('heading', { name: '문의 현황' })).toBeVisible()
  await expect(page.getByText(/QA 문의 목록 모바일 줄바꿈/)).toBeVisible()
  await expect(page.getByText('대기 중').first()).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
