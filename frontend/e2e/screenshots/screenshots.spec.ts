import { test, expect, type Page } from '@playwright/test'
import path from 'node:path'
import { capture, expectCapture } from './capture'

type Role = 'public' | 'buyer' | 'artist' | 'admin'
type Entry = [string, string, string]

async function waitForDataReadiness(page: Page, role: Role, key: string) {
  const checks = {
    buyer: {
      mypage: [
        '프로필 정보를 불러오는 중입니다.',
        page.getByText('사용 가능 포인트').first(),
      ],
      'order-status': [
        '주문 내역을 불러오는 중입니다.',
        page.getByText('QA 주문 배송 중').first(),
      ],
    },
    admin: {
      'admin-inquiries': [
        '문의 목록을 불러오는 중입니다.',
        page.getByRole('button').filter({ hasText: 'QA 문의 목록 모바일 줄바꿈' }).first(),
      ],
    },
  } as const
  const check = checks[role as 'buyer' | 'admin']?.[key as never]
  if (!check) return
  try {
    await expect(page.getByText(check[0], { exact: true })).toBeHidden({ timeout: 10_000 })
    await expect(check[1]).toBeVisible({ timeout: 10_000 })
  } catch (error) {
    const message = error instanceof Error ? error.message.split('\n')[0] : String(error)
    throw new Error(`data readiness failed for ${role}/${key}: ${message}`)
  }
}

function defineRoute(testApi: typeof test, role: Role, [key, route, heading]: Entry) {
  testApi(`${role} ${key}`, { annotation: { type: 'ui-capture', description: `${key}:default` } }, async ({ page }, info) => {
    const result = await capture(page, info, {
      key, role, route,
      prepare: async () => {
        await page.goto(route, { waitUntil: 'domcontentloaded' })
        await expect(page.getByRole('heading', { name: heading }).first()).toBeVisible()
      },
      readiness: () => waitForDataReadiness(page, role, key),
    })
    await expectCapture(result)
  })
}

for (const entry of [
  ['login', '/login', 'Login'], ['register', '/register', '회원가입'], ['home', '/', '신규 작품'],
  ['auction-total', '/auction/total', '전체 경매'], ['auction-digital', '/auction/digital', '디지털 경매'],
  ['auction-analog', '/auction/analog', '실물 경매'], ['search', '/search?q=판매%20QA', '작품 탐색'],
  ['artist-list', '/artists', '작가 찾기'], ['info', '/info', '경매 이용 안내'], ['qna', '/qna', '고객센터'],
  ['register-user', '/register/user', '일반 회원가입'], ['register-artist', '/register/artist', '작가 회원가입'],
] as Entry[]) defineRoute(test, 'public', entry)

test('public artist-detail', { annotation: { type: 'ui-capture', description: 'artist-detail:default' } }, async ({ page }, info) => {
  const result = await capture(page, info, { key: 'artist-detail', role: 'public', route: '/artists', prepare: async () => {
    await page.goto('/artists', { waitUntil: 'domcontentloaded' })
    await page.getByRole('link', { name: /아리아 김/ }).first().click()
    await expect(page.getByRole('heading', { name: '아리아 김' })).toBeVisible()
  } })
  await expectCapture(result)
})

test('public art-detail', { annotation: { type: 'ui-capture', description: 'art-detail:default' } }, async ({ page }, info) => {
  const result = await capture(page, info, { key: 'art-detail', role: 'public', route: '/auction/analog', prepare: async () => {
    await page.goto('/auction/analog', { waitUntil: 'domcontentloaded' })
    await page.getByRole('link', { name: '오렌지 코발트 작품 상세 보기' }).click()
    await expect(page.getByRole('heading', { name: '오렌지 코발트' })).toBeVisible()
  } })
  await expectCapture(result)
})

test('public art-detail image modal', { annotation: { type: 'ui-capture', description: 'art-detail:image-modal' } }, async ({ page }, info) => {
  const result = await capture(page, info, { key: 'art-detail', role: 'public', route: '/auction/analog', state: 'image-modal', captureMode: 'viewport', prepare: async () => {
    await page.goto('/auction/analog', { waitUntil: 'domcontentloaded' })
    await page.getByRole('link', { name: '오렌지 코발트 작품 상세 보기' }).click()
    await page.getByRole('button', { name: '오렌지 코발트 원본 이미지 보기' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
  } })
  await expectCapture(result)
})

test.describe('buyer screenshots', () => {
  test.use({ storageState: path.resolve('.playwright', 'buyer.json') })
  for (const entry of [
    ['mypage', '/mypage', 'MY PAGE'], ['bid-status', '/mypage/bid-status', '입찰 현황'],
    ['successful-bid', '/mypage/successful-bid', '낙찰 작품'], ['order-status', '/mypage/order-status', '주문 조회'],
    ['charge', '/charge', '적립금 충전'], ['likes', '/mypage/likes', '찜한 작품'],
    ['my-review', '/mypage/my-review', '내가 쓴 리뷰'], ['profile-edit', '/mypage/profile-edit', '회원 정보 수정'],
    ['inquiry', '/mypage/inquiry', '문의 현황'], ['inquiry-write', '/mypage/inquiry/write', '1:1 문의하기'],
  ] as Entry[]) defineRoute(test, 'buyer', entry)

  test('buyer order detail', { annotation: { type: 'ui-capture', description: 'order-status:detail' } }, async ({ page }, info) => {
    const result = await capture(page, info, { key: 'order-status', role: 'buyer', route: '/mypage/order-status', state: 'detail', prepare: async () => {
      await page.goto('/mypage/order-status', { waitUntil: 'domcontentloaded' })
      await page.getByRole('button', { name: /QA 주문 배송 중 주문 상세 보기/ }).click()
      await expect(page.getByRole('heading', { name: '배송 정보' })).toBeVisible()
    } })
    await expectCapture(result)
  })
  test('buyer my-review modal', { annotation: { type: 'ui-capture', description: 'my-review:modal' } }, async ({ page }, info) => {
    const result = await capture(page, info, { key: 'my-review', role: 'buyer', route: '/mypage/my-review', state: 'modal', captureMode: 'viewport', prepare: async () => {
      await page.goto('/mypage/my-review', { waitUntil: 'domcontentloaded' })
      await page.getByRole('button', { name: /판매 QA 주문 구매 확정 리뷰 상세 보기/ }).click()
      await expect(page.getByRole('dialog')).toBeVisible()
    } })
    await expectCapture(result)
  })
  test('buyer inquiry detail', { annotation: { type: 'ui-capture', description: 'inquiry:detail' } }, async ({ page }, info) => {
    const result = await capture(page, info, { key: 'inquiry', role: 'buyer', route: '/mypage/inquiry', state: 'detail', prepare: async () => {
      await page.goto('/mypage/inquiry', { waitUntil: 'domcontentloaded' })
      await page.getByText(/QA 문의 목록 모바일 줄바꿈/).first().click()
      await expect(page.getByText('대기 중').first()).toBeVisible()
    } })
    await expectCapture(result)
  })
  test('buyer write-review edit initial', { annotation: { type: 'ui-capture', description: 'write-review:edit-initial' } }, async ({ page }, info) => {
    const result = await capture(page, info, {
      key: 'write-review', role: 'buyer', route: '/write-review/:orderId', state: 'edit-initial',
      prepare: async () => {
        await page.goto('/mypage/order-status', { waitUntil: 'domcontentloaded' })
        const link = page.getByRole('link', { name: '리뷰 쓰기·수정' }).first()
        await link.click()
        await expect(page.getByRole('heading', { name: '리뷰 수정하기' })).toBeVisible()
      },
    })
    await expectCapture(result)
  })
})

test.describe('artist screenshots', () => {
  test.use({ storageState: path.resolve('.playwright', 'artist.json') })
  for (const entry of [
    ['manage-arts', '/mypage/manage-arts', '내 작품 관리'], ['artist-review', '/mypage/artist-review', '내 작품 리뷰 보기'],
    ['sales-orders', '/mypage/sales-orders', '판매 주문 관리'], ['upload', '/upload', '작품 등록'],
  ] as Entry[]) defineRoute(test, 'artist', entry)
  test('artist edit-art', { annotation: { type: 'ui-capture', description: 'edit-art:default' } }, async ({ page }, info) => {
    const result = await capture(page, info, { key: 'edit-art', role: 'artist', route: '/mypage/manage-arts', prepare: async () => {
      await page.goto('/mypage/manage-arts', { waitUntil: 'domcontentloaded' })
      await page.getByRole('button', { name: '진행 중' }).click()
      await page.getByRole('link', { name: '수정·삭제' }).first().click()
      await expect(page.getByRole('heading', { name: /작품 수정/ })).toBeVisible()
    } })
    await expectCapture(result)
  })
  test('artist review modal', { annotation: { type: 'ui-capture', description: 'artist-review:modal' } }, async ({ page }, info) => {
    const result = await capture(page, info, { key: 'artist-review', role: 'artist', route: '/mypage/artist-review', state: 'modal', captureMode: 'viewport', prepare: async () => {
      await page.goto('/mypage/artist-review', { waitUntil: 'domcontentloaded' })
      await page.getByRole('button', { name: /판매 QA 주문 구매 확정 리뷰 상세 보기/ }).click()
      await expect(page.getByRole('dialog')).toBeVisible()
    } })
    await expectCapture(result)
  })
})

test.describe('admin screenshots', () => {
  test.use({ storageState: path.resolve('.playwright', 'admin.json') })
  defineRoute(test, 'admin', ['admin-inquiries', '/admin/inquiries', '문의 관리'])
})
