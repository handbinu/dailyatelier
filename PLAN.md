# DailyAtelier 개발 계획

> [!IMPORTANT]
> `PLAN.md`는 현재 승인된 작업 계획을 관리한다.
>
> 작업 완료 후 핵심 구현·검증 결과를 `PLAN_DONE.md`에 기록한 다음 해당 계획을 `PLAN.md`에서 제거한다.
>
> 현재 범위에 포함되지 않은 미완료·후속 작업은 `BACKLOG.md`에서 관리한다.
>
> 새 계획이나 범위 변경은 사용자 승인 후 반영한다.

---

## UI 시각 정합화 구현 계획

### 목표

- `/upload`의 레이아웃을 다른 화면에 복제하지 않고, 공통 브랜드 언어를 화면 역할에 맞춰 색상·surface·타이포·CTA·form 상태에 정합화한다.
- 기존 라우트, API·도메인 계약, 접근성 동작, 최근 완료된 화면 구조를 보존한다.
- warm cream 전체 page canvas / white card·enabled form surface / charcoal 읽기 정보 / sage·deep sage 구조·보조 행동 / butter 최종 CTA / 의미 있는 light sage 보조 surface의 역할을 명확히 한다.

### 범위와 비목표

#### 구현 범위

1. 탐색·작품·작가 계열 정합화
2. 마이페이지·거래 화면 시각 정합화

#### 제외

- `/upload`와 `/mypage/manage-arts/:artId/edit`(EditArt)은 기준 화면으로 보존하며, 공통 변경에 따른 회귀 확인만 한다.
- `/mypage` 허브 sidebar·정보구조의 대규모 재설계와 `/admin/inquiries`의 모바일 drill-in 구조는 별도 UX/구조 작업 후보로만 기록한다.
- 백엔드 API, 인증·권한, 도메인 규칙, URL·요청/응답 계약은 변경하지 않는다.
- 화면 역할과 무관하게 `/upload`의 sticky UI와 progress panel을 전파하지 않는다. sage gradient·drawing-paper texture와 butter 최종 CTA는 공통 브랜드 언어로 재정의하되, 화면별 layout은 복제하지 않는다.
- 구현 중 범위 밖 구조 문제가 발견되면 임의로 포함하지 않고 문제·영향·권장 대응을 보고한 뒤 PLAN 수정 여부를 결정한다.

### 공통 원칙

- 전역 토큰을 기계적으로 교체하지 않는다. 토큰과 전용 CSS의 사용처를 먼저 조사하고, UI 가이드와 충돌하는 사용처만 최소 변경한다.
- 일반 page canvas는 warm cream, enabled input/select/textarea와 card/form/content surface는 white를 유지한다. focus는 white + sage/deep-sage border/ring으로 표현하고, disabled/readonly와 error는 별도 semantic 상태로 둔다.
- 최종 등록·가입·저장·제출·확정 CTA는 butter, secondary는 white + sage border/text, danger는 danger text/outline을 기본으로 한다. sage/deep sage는 선택·상태·진행·focus·보조 행동과 구조적 강조에 사용한다.
- 공통 notice는 light sage surface, pagination active와 FilterBar active는 deep sage를 사용한다. 이를 전역 단일 CSS로 강제하지 않고 공통 컴포넌트·variant와 실제 화면별 역할 CSS에 적용한다.
- gray/tinted surface는 계산·진행·중요 안내처럼 의미가 있는 경우만 남긴다. 메타와 문장을 box/chip으로 과도하게 분리하지 않는다.
- 핵심 금액·상태·제목·label은 charcoal 중심으로 읽히게 하며, 정책·제한·다음 행동을 바꾸는 helper는 작은 muted caption으로 숨기지 않는다.
- `PageBanner`, Header, 공통 action/filter/pagination은 실제 사용처와 영향 범위를 확인한 뒤 최소 변경한다. 공통 PageBanner는 sage gradient + drawing-paper texture를 기본으로 하며 `/upload`는 이를 더 강하게 활용하는 전용 사례로 유지한다.

### 3단계 — 탐색·작품·작가 계열의 메타·거래 위계 정합화

#### 대상

- `/`, `/search`
- `/auction/total`, `/auction/digital`, `/auction/analog`, `/auction/:id`
- `/artists`, `/artists/:artistId`

#### 구현 순서

1. 목록 카드의 제목, 작가, 가격, 마감, 상태, 다음 행동을 역할 순서대로 점검하고 의미 없는 gray section surface·status chip만 선별 축소한다.
2. 검색·초기화·재시도 등 일반 보조 행동은 `da-button-secondary`로, active filter와 current pagination 등 현재/선택 상태는 `da-button-state`로 정리한다. 작품 구매·입찰 확정처럼 최종 결정 CTA는 butter를 검토하며, 상태의 텍스트 단서는 유지한다.
3. 검색·경매 목록과 작가 hero는 화면 성격을 보존하되, 공통 brand role에 맞춰 탐색 영역이 불필요하게 dark/gray-heavy가 되지 않도록 surface와 설명 대비를 조정한다.
4. 작품 상세에서는 현재가·최소 입찰 정보·마감·입찰 가능 상태·입찰 행동을 하나의 거래 위계로 정리한다. 계산/진행 정보만 light sage를 사용하고 gray bid panel 중첩을 줄인다.
5. mobile에서 목록과 상세의 정보 순서를 가격·마감·상태·다음 행동 중심으로 확인한다. 별도 구조 작업이 필요한 경우 보고 후 결정한다.

#### desktop / mobile 확인

- Desktop: card 중첩과 shadow/radius가 과하지 않은지, 가격·거래 상태가 날짜·보조 메타보다 먼저 읽히는지, 입찰 CTA가 명확한지 확인한다.
- Mobile: 320px에서도 card grid, filter, pagination이 overflow 없이 읽히는지와 상세의 거래 핵심 정보·입찰 CTA 우선순위를 확인한다.

#### 자동 테스트

- 검색 query/filter/sort/pagination, 작품·작가 목록 상태와 pagination 테스트를 유지한다.
- 작품 상세의 입찰 단위·로그인 복귀·찜·dialog·disabled state 계약과 상태 label/CTA accessible name을 검증한다.

### 4단계 — 마이페이지·거래 화면의 남은 시각 불일치 정합화

#### 대상

- `/mypage/bid-status`, `/mypage/likes`, `/mypage/successful-bid`
- `/mypage/order-status`, `/mypage/sales-orders`, `/mypage/manage-arts`
- `/mypage/my-review`, `/mypage/artist-review`, `/mypage/inquiry`, `/charge`

#### 구현 순서

1. 완료된 거래/목록 UX와 API 상태를 먼저 확인한다. 레이아웃 재작성 없이 nested gray/tinted panel, 중복 card, 의미 없는 chip만 최소한으로 줄인다.
2. 금액, 주문/입찰/판매 상태, 다음 행동, helper의 대비를 재정렬한다. 진행·계산·중요 안내일 때만 light sage surface를 쓴다.
3. 최종 등록·저장·확정 CTA는 butter로 맞추고, sage/deep sage 보조 행동과 취소·환불·삭제 danger outline/text 역할을 유지한다.
4. 리뷰·문의·충전의 작은 muted 정책/상태 문구를 읽을 수 있는 hierarchy로 올린다. 충전 계산 결과와 선택 상태는 의미 있는 sage surface로 보존한다.
5. mobile은 이미 개선된 card/list/detail 구조를 뜯지 않고, gray panel, CTA 순서, 작은 메타, overflow만 화면별로 수정한다.

#### desktop / mobile 확인

- Desktop: 주문/판매 row의 핵심 값·상태·다음 행동이 table header/detail panel보다 우선하는지, card별 primary가 하나로 읽히는지 확인한다.
- Mobile: 식별 정보·상태·핵심 값·다음 행동 순서, 보조 메타의 후순위화, 320px/390px에서 filter·badge·action row·긴 주문 번호 overflow를 확인한다.

#### 자동 테스트

- 입찰/낙찰/찜/주문/판매 주문/작품 관리의 API 요청, 상태별 CTA, latest request, 취소·환불·배송 행동 테스트를 유지한다.
- 리뷰·문의·충전의 loading/empty/error/success/retry, pagination/filter를 유지하고, DOM 변경 시 키보드 흐름과 CTA accessible name을 추가 검증한다.

### 구현 후 사용자 브라우저 QA 대상

- 공통: 1440px, 1024px, 390px, 320px, 200% 확대에서 gutter, overflow, focus-visible, CTA 대비
- Form: login/register/profile/inquiry/review의 enabled·focus·disabled·error·policy·submit 흐름
- 탐색/거래: filter/pagination, 작품 상세 입찰, 가격·마감·상태, 로그인 복귀
- 마이페이지: 주문·판매·입찰·관리·리뷰·문의·충전의 핵심 값/상태/다음 행동 및 mobile 순서
- 기준 화면: `/upload`, EditArt의 surface, CTA, focus 상태 회귀 여부

### 완료 조건

- 각 대상 화면이 `UI_GUIDE.md`의 색상 역할, form 상태, CTA 강도, 정보 대비 원칙을 충족한다.
- 기능·API·route·인증/권한·기존 접근성 계약과 상태별 사용자 행동이 유지된다.
- 공통 변경이 `/upload`와 EditArt를 회귀시키지 않는다.
- 변경 대상 자동 테스트와 사용자 브라우저 QA가 완료되며, 범위 밖 구조 문제는 별도 보고된다.

### 공통 검증 원칙

- 키보드만으로 주요 흐름을 완료할 수 있어야 하며, 기존 포커스 순서·focus-visible·AccessibleDialog의 focus trap/Escape/복귀를 유지한다.
- 버튼·링크의 의미 요소, form label·helper·error의 프로그램적 연결, 상태의 텍스트 단서를 유지한다. 오류 외 상태를 색상만으로 전달하지 않는다.
- `prefers-reduced-motion`을 존중하고, 200% 확대·320px 폭에서도 콘텐츠·CTA가 잘리거나 겹치지 않아야 한다.
- 각 단계는 관련 Vitest/Testing Library 테스트를 유지·갱신하며, 시각 클래스명보다 역할·이름·상태·행동을 검증한다.
- 사용자 브라우저 QA는 실제 로컬 데이터와 로그인 계정으로 수행하며, 기능 회귀와 시각 불일치 해소를 확인한 뒤 완료로 판단한다.
