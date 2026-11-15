import { artistTest as test } from '../fixtures/auth'
import { expectNoHorizontalOverflow, expect } from '../fixtures/qaPage'

test('작가가 내 작품 관리 fixture를 조회한다', async ({ page }) => {
  await page.goto('/mypage/manage-arts')
  await expect(page.getByRole('heading', { name: '내 작품 관리' })).toBeVisible()
  await expect(page.getByText(/등록 작품/)).toBeVisible()
  await expect(page.getByText('판매 QA 주문 구매 확정')).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
