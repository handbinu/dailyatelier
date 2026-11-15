import { adminTest as test } from '../fixtures/auth'
import { expectNoHorizontalOverflow, expect } from '../fixtures/qaPage'

test('관리자가 문의 관리 fixture를 조회한다', async ({ page }) => {
  await page.goto('/admin/inquiries')
  await expect(page.getByRole('heading', { name: '문의 관리' })).toBeVisible()
  const pendingInquiry = page.getByRole('button')
    .filter({ hasText: 'QA 문의 목록 모바일 줄바꿈과 상태·날짜 우선순위 확인을 위한 긴 제목입니다' })
    .filter({ hasText: '입찰 QA (demo-buyer-bid-qa)' })
  await expect(pendingInquiry).toBeVisible()
  await expect(pendingInquiry.getByText('대기 중', { exact: true })).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
