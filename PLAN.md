# DailyAtelier 개발 계획

> [!IMPORTANT]
> `PLAN.md`는 현재 승인된 작업 계획을 관리한다.
>
> 작업 완료 후 핵심 구현·검증 결과를 `PLAN_DONE.md`에 기록한 다음 해당 계획을 `PLAN.md`에서 제거한다.
>
> 현재 범위에 포함되지 않은 미완료·후속 작업은 `BACKLOG.md`에서 관리한다.
>
> 새 계획이나 범위 변경은 사용자 승인 후 반영한다.

## Playwright UI QA 커버리지 확대

### 목표

- 현재 6개 고유 route 수준의 Playwright UI QA를 local-demo fixture 기반의 읽기 전용 사용자 화면 전반으로 확장한다.
- desktop `1440×900`, mobile `390×844` 모두 실행하고, 모든 테스트에서 console error 및 horizontal overflow 검증을 유지한다.
- 단순 진입이 아니라 heading, 대표 fixture, 상태 badge, 핵심 읽기 전용 UI를 검증한다.
- 생성·수정·삭제·입찰·결제·환불·답변 제출 CTA는 클릭하지 않는다.
- visual regression, axe, CI 구성은 이번 범위에서 제외한다.

### local-demo fixture 보강

- 새 계정은 만들지 않고 기존 `demo-buyer-bid-qa`, `demo-artist-aria`와 작품·주문 fixture를 재사용한다.
- `demo-buyer-bid-qa`에 진행·종료/낙찰·취소/삭제 작품의 찜 fixture와 구매 확정 주문의 작성 리뷰 fixture를 추가한다.
- `demo-artist-aria` 판매 작품에 위 buyer 리뷰를 연결해 작가 리뷰 카드, 평균 별점, 판매/리뷰 집계를 검증할 수 있게 한다.
- fixture는 재기동해도 중복 생성되지 않는 idempotent seed로 구성하고, 기존 주문·입찰·충전·문의 상태의 의미는 변경하지 않는다.

### 공통 Playwright 구조

- 기존 `qaPage` fixture의 console error 수집과 `expectNoHorizontalOverflow(page)`를 유지한다.
- 작품명·작가명·상태 badge·주문 상태 row를 찾는 재사용 locator helper를 추가한다.
- ID를 고정하지 않고 목록에 표시된 link를 따라 작품·작가 상세에 진입한다.
- state-changing CTA는 존재 여부만 확인하고 클릭하지 않는다.
- 읽기 전용 상호작용은 **상태를 변경하지 않는** 필터·정렬·페이지네이션·상세 펼침·조회용 모달 열기/닫기로 한정한다.

### 기존 자동화 보강

| route | 보강 assertion |
|---|---|
| `/login` | 로그인 form label, 로그인 CTA, 성공 후 홈의 대표 fixture |
| `/` | `신규 작품`, `Best Art`, `QA 진행 중 입찰 작품` 등 대표 작품과 상세 link |
| `/auction/total` | heading, 진행 작품 fixture, 상태/가격/목록 CTA |
| `/mypage/inquiry` | 미답변 문의 제목, `답변 대기` 상태, 문의 상세 읽기 UI |
| `/mypage/manage-arts` | aria 작품 fixture, 진행/낙찰/취소 상태, 입찰 수·수정 link |
| `/admin/inquiries` | 미답변·답변 완료 fixture, 상태 badge, 답변 조회 UI. 답변 제출 제외 |

### 추가 public coverage

| route/시나리오 | 최소 assertion |
|---|---|
| `/register` | 회원 유형 선택 heading, 일반/작가 가입 CTA |
| `/auction/digital` | 디지털 작품 fixture, 목록 heading, 작품 상세 link |
| `/auction/analog` | 실물 작품 fixture, 목록 heading, 상태/가격 |
| `/search?q=QA` | 탐색 heading, QA 검색 결과, 검색/필터 UI |
| `/auction/:id` — 진행 작품 | 작품명, 작가명, 진행 상태, 현재가, 입찰 CTA 존재 |
| `/auction/:id` — 종료·낙찰 작품 | 작품명, 종료/낙찰 상태와 최종 가격 또는 결과 안내 |
| `/artists` | 작가 찾기 heading, `아리아 김` 등 fixture 작가 카드 |
| `/artists/:artistId` | 작가명, 소개, 공개 작품 fixture, 작품 상세 link |
| `/info` | 정적 안내 heading, 문의/관련 link |
| `/qna` | Q&A heading, 문의 작성/문의 내역 CTA의 로그인 상태별 노출 |

### 추가 buyer/common coverage

| route | 계정 | 최소 assertion |
|---|---|---|
| `/mypage` | buyer, artist 각 1회 | 프로필, 보유/예치 포인트, 진행 입찰·낙찰 요약, 역할별 메뉴 |
| `/mypage/bid-status` | `demo-buyer-bid-qa` | 진행/임박/종료 요약, `QA 진행 중 입찰 작품`, 경쟁 중·낙찰·패찰·경매 취소 상태 |
| `/mypage/likes` | `demo-buyer-bid-qa` | 진행/낙찰/취소·삭제 작품 fixture, 상태 badge, 상세 link, 찜 해제 CTA 존재 |
| `/mypage/successful-bid` | `demo-buyer-bid-qa` | 낙찰 작품 fixture, 낙찰가, 주문 확인 link |
| `/mypage/my-review` | `demo-buyer-bid-qa` | 작성 리뷰 fixture, 별점, 작품명, 리뷰 상세 modal 열기/닫기 |
| `/mypage/profile-edit` | buyer | 조회된 닉네임·이메일 등 초기 form 값, 저장 CTA. 저장 제외 |
| `/mypage/order-status` | `demo-buyer-bid-qa` | 배송지 확정, 결제 완료, 배송 중, 배송 완료, 구매 확정, 취소, 환불 상태 fixture 및 상세 펼침 |
| `/charge` | `demo-buyer-bid-qa` | 사용 가능/예치 포인트, pending/paid/failed/canceled/refunded 충전 이력 및 상태 badge |

- `/reliable-status`는 `/mypage/bid-status`와 동일 화면의 별칭이므로 별도 desktop/mobile coverage 대상으로 추가하지 않는다.

### 추가 artist/admin coverage

| route | 계정 | 최소 assertion |
|---|---|---|
| `/mypage/artist-review` | `demo-artist-aria` | 판매 완료/리뷰 작성/미작성 집계, 작성 리뷰 fixture, 리뷰 상세 modal, 미작성 작품 보기 전환 |
| `/mypage/sales-orders` | `demo-artist-aria` | 배송지 미확정, 결제 완료, 배송 준비, 배송 중, 환불 요청/거절, 주문 상세 읽기 UI |
| `/admin/inquiries` | `demo-admin-inquiry-qa` | 기존 테스트 보강: 미답변/답변 완료 문의, 상태, 답변 본문 조회. 답변 등록 제외 |

### 제외 route

- `/register/user`, `/register/artist`, `/mypage/inquiry/write`, `/write-review/:orderId`, `/upload`, `/mypage/manage-arts/:artId/edit`
- 위 화면은 렌더링 smoke만으로 핵심 동작을 검증하기 어렵고, 기능 검증에는 DB 상태 변경이 필요하므로 별도 mutation E2E 범위에서 다룬다.

### 완료 기준

- local-demo로 안정적인 populated 상태를 만들 수 있는 public/buyer/artist/admin read-only 화면을 모두 desktop/mobile 대상으로 포함한다.
- 현재 자동화 route는 대표 fixture와 상태 assertion까지 강화한다.
- 새 fixture는 likes와 reviews의 populated read-only 화면만 지원한다.
- 테스트는 생성·수정·삭제·결제·입찰·환불·답변 제출을 수행하지 않는다.
- 전체 실행에서 console error 및 horizontal overflow 검증을 유지한다.
- fixture ID나 시간 변화에 취약한 하드코딩 대신 title, role, 상태, 화면 link 기반 locator를 사용한다.
