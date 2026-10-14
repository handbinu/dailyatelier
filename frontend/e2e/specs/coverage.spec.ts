import { buyerTest, artistTest, adminTest } from '../fixtures/auth'
import { expectNoHorizontalOverflow, expect, test } from '../fixtures/qaPage'

async function expectFixtureArt(page: Parameters<typeof expectNoHorizontalOverflow>[0], name: string) {
  await expect(page.getByRole('link', { name: `${name} 작품 상세 보기` })).toBeVisible()
}

test('공개 경매, 탐색, 작가 및 안내 화면을 읽기 전용으로 조회한다', async ({ page }) => {
  await page.goto('/register')
  await expect(page.getByRole('heading', { name: '회원가입' })).toBeVisible()
  await expect(page.getByRole('button', { name: '일반 회원' })).toBeVisible()

  await page.goto('/auction/digital')
  await expect(page.getByRole('heading', { name: '디지털 경매' })).toBeVisible()
  await expectFixtureArt(page, 'QA 디지털 진행 작품')

  await page.goto('/auction/analog')
  await expect(page.getByRole('heading', { name: '실물 경매' })).toBeVisible()
  await expectFixtureArt(page, '오렌지 코발트')

  await page.goto('/search?q=판매%20QA')
  await expect(page.getByRole('heading', { name: '작품 탐색' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '탐색 결과' })).toBeVisible()
  await expect(page.getByText('판매 QA 주문 구매 확정')).toBeVisible()

  await page.goto('/artists')
  await expect(page.getByRole('heading', { name: '작가 찾기' })).toBeVisible()
  const artist = page.getByRole('link', { name: /아리아 김/ }).first()
  await expect(artist).toBeVisible()
  await artist.click()
  await expect(page.getByRole('heading', { name: '아리아 김' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '공개 작품' })).toBeVisible()

  await page.goto('/info')
  await expect(page.getByRole('heading', { name: '경매 이용 안내' })).toBeVisible()
  await page.goto('/qna')
  await expect(page.getByRole('heading', { name: '고객센터' })).toBeVisible()
  await expectNoHorizontalOverflow(page)
})

test('공개 작품 상세는 진행 및 종료 결과를 표시한다', async ({ page }) => {
  await page.goto('/auction/analog')
  const ongoing = page.getByRole('link', { name: '오렌지 코발트 작품 상세 보기' })
  await ongoing.click()
  await expect(page.getByRole('heading', { name: '오렌지 코발트' })).toBeVisible()
  await expect(page.getByText('입찰하기')).toBeVisible()

  await page.goto('/search?q=판매%20QA%20주문%20구매%20확정&status=ENDED')
  const ended = page.getByRole('link', { name: '판매 QA 주문 구매 확정 작품 상세 보기' })
  await ended.click()
  await expect(page.getByRole('heading', { name: '판매 QA 주문 구매 확정' })).toBeVisible()
  await expect(page.getByText('낙찰', { exact: true })).toBeVisible()
  await expect(page.getByText('종료된 경매에는 입찰할 수 없습니다.', { exact: true })).toBeVisible()
  await expectNoHorizontalOverflow(page)
})

buyerTest('구매자가 마이페이지와 입찰·낙찰 현황을 조회한다', async ({ page }) => {
  await page.goto('/mypage')
  await expect(page.getByRole('heading', { name: 'MY PAGE' })).toBeVisible()
  await expect(page.getByRole('complementary').getByText('입찰 QA')).toBeVisible()

  await page.goto('/mypage/bid-status')
  await expect(page.getByRole('heading', { name: '입찰 현황' })).toBeVisible()
  await expect(page.getByText('QA 진행 중 입찰 작품')).toBeVisible()
  await expect(page.getByText('경쟁 중')).toBeVisible()
  const wonBidCard = page.getByText('QA 낙찰 주문 작품', { exact: true }).locator('xpath=ancestor::div[3]')
  await expect(wonBidCard.getByText('낙찰', { exact: true })).toBeVisible()

  await page.goto('/mypage/successful-bid')
  await expect(page.getByRole('heading', { name: '낙찰 작품' })).toBeVisible()
  const successfulBid = page.getByRole('article').filter({ hasText: '판매 QA 주문 구매 확정' })
  await expect(successfulBid).toBeVisible()
  await expect(successfulBid.getByText('낙찰 확정', { exact: false })).toBeVisible()
  await expect(successfulBid.getByText('낙찰가 241,000원', { exact: true })).toBeVisible()
  await expect(successfulBid.getByRole('link', { name: '주문 확인' })).toBeVisible()
  await expectNoHorizontalOverflow(page)
})

buyerTest('구매자가 주문 조회와 배송 상세를 읽기 전용으로 확인한다', async ({ page }) => {
  await page.goto('/mypage/order-status')
  await expect(page.getByRole('heading', { name: '주문 조회' })).toBeVisible()
  await expect(page.getByText('QA 주문 배송 중')).toBeVisible()
  await page.getByRole('button', { name: /QA 주문 배송 중 주문 상세 보기/ }).click()
  await expect(page.getByRole('heading', { name: '배송 정보' })).toBeVisible()
  await expectNoHorizontalOverflow(page)
})

buyerTest('구매자가 충전 이력 상태를 읽기 전용으로 확인한다', async ({ page }) => {
  await page.goto('/charge')
  await expect(page.getByRole('heading', { name: '적립금 충전' })).toBeVisible()
  await expect(page.getByText('충전 완료')).toBeVisible()
  await expect(page.getByText('충전 환불')).toBeVisible()
  await expectNoHorizontalOverflow(page)
})

buyerTest('구매자가 fixture 찜과 리뷰를 읽기 전용으로 조회한다', async ({ page }) => {
  await page.goto('/mypage/likes')
  await expect(page.getByRole('heading', { name: '찜한 작품' })).toBeVisible()
  await expect(page.getByText('QA 진행 중 입찰 작품')).toBeVisible()
  await expect(page.getByText('QA 낙찰 주문 작품')).toBeVisible()
  await expect(page.getByText('QA 경매 취소 작품')).toBeVisible()
  await expect(page.getByRole('button', { name: '찜 해제' }).first()).toBeVisible()

  await page.goto('/mypage/my-review')
  await expect(page.getByRole('heading', { name: '내가 쓴 리뷰' })).toBeVisible()
  const review = page.getByRole('button', { name: /판매 QA 주문 구매 확정 리뷰 상세 보기/ })
  await expect(review).toBeVisible()
  await review.click()
  const dialog = page.getByRole('dialog', { name: '판매 QA 주문 구매 확정' })
  await expect(dialog.getByRole('heading', { name: '판매 QA 주문 구매 확정' })).toBeVisible()
  await dialog.getByLabel('닫기').click()
  await expectNoHorizontalOverflow(page)
})

buyerTest('구매자가 프로필 편집 화면의 초기 값을 조회한다', async ({ page }) => {
  await page.goto('/mypage/profile-edit')
  await expect(page.getByRole('heading', { name: '회원 정보 수정' })).toBeVisible()
  await expect(page.getByPlaceholder('닉네임 입력')).toHaveValue('입찰 QA')
  await expect(page.getByRole('button', { name: '수정 완료' })).toBeVisible()
  await expectNoHorizontalOverflow(page)
})

artistTest('작가가 판매 주문과 판매 작품 리뷰를 조회한다', async ({ page }) => {
  await page.goto('/mypage/sales-orders')
  await expect(page.getByRole('heading', { name: '판매 주문 관리' })).toBeVisible()
  await expect(page.getByText('판매 QA 주문 배송 중')).toBeVisible()
  await page.getByRole('button', { name: /판매 QA 주문 배송 중 주문 상세 보기/ }).click()
  await expect(page.getByRole('heading', { name: '주문 정보' })).toBeVisible()

  await page.goto('/mypage/artist-review')
  await expect(page.getByRole('heading', { name: '내 작품 리뷰 보기' })).toBeVisible()
  await expect(page.getByText('판매 작품 리뷰 현황')).toBeVisible()
  const review = page.getByRole('button', { name: /판매 QA 주문 구매 확정 리뷰 상세 보기/ })
  await expect(review).toBeVisible()
  await review.click()
  const dialog = page.getByRole('dialog', { name: '판매 QA 주문 구매 확정 리뷰 상세' })
  await expect(dialog.getByText('로컬 demo 읽기 전용 QA에서 리뷰 카드와 상세 내용을 확인하기 위한 후기입니다.')).toBeVisible()
  await expectNoHorizontalOverflow(page)
})

adminTest('관리자가 답변 완료 문의를 조회한다', async ({ page }) => {
  await page.goto('/admin/inquiries')
  await expect(page.getByRole('heading', { name: '문의 관리' })).toBeVisible()
  const answered = page.getByRole('button', { name: /QA 답변 완료 문의 상태와 답변 메타 확인용 문의입니다/ })
  await expect(answered).toBeVisible()
  await answered.click()
  await expect(page.getByText('답변 완료 문의의 본문과 완료 시각을 확인할 수 있도록 등록한 QA 답변입니다.')).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
