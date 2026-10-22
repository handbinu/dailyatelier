import { expectNoHorizontalOverflow, expect, test } from '../fixtures/qaPage'

test('로그인 폼으로 구매자 계정을 인증한다', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('아이디').fill(process.env.UI_QA_BUYER_ID ?? '')
  await page.getByLabel('비밀번호').fill(process.env.UI_QA_BUYER_PASSWORD ?? '')
  await page.getByRole('button', { name: '로그인' }).click()
  await expect(page).toHaveURL(/\/$/)
  await expectNoHorizontalOverflow(page)
})

test('홈과 전체 경매 목록을 표시한다', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '신규 작품' })).toBeVisible()
  await expect(page.getByText('세이지 브레스')).toBeVisible()
  await expectNoHorizontalOverflow(page)

  await page.goto('/auction/total')
  await expect(page.getByRole('heading', { name: '전체 경매' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '진행 중인 전체 작품' })).toBeVisible()
  await expect(page.getByRole('link', { name: '오렌지 코발트 작품 상세 보기' })).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
