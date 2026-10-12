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

## UI 시각 정합화 5단계 구현 계획

### 목표

- 최근 완료된 탐색·작품·작가·거래 화면의 visual role을 기준으로 정적 안내, 문의, 충전, 마이페이지 허브의 남은 불일치를 최소 수정한다.
- 기존 라우트, API·도메인 계약, 접근성 동작, 상태 전이와 큰 레이아웃을 보존한다.
- warm cream 전체 page canvas / white card·enabled form surface / charcoal 읽기 정보 / sage·deep sage 구조·보조 행동 / butter 최종 CTA / 의미 있는 light sage 보조 surface의 역할을 명확히 한다.

### 범위와 비목표

#### 구현 범위

1. `/info`, `/qna`의 gray-heavy canvas와 중첩 section card를 cream canvas·white 읽기 흐름으로 정리하고, 정의되지 않은 secondary border를 공통 secondary 역할로 교체한다.
2. `/mypage/inquiry`, `/admin/inquiries`의 notice·error·Q/A surface와 모바일 제목·상태·날짜 우선순위를 정리한다. butter final은 실제 문의 등록·답변 등록·제출에만 적용하며, 작성 화면 진입·조회·목록 이동에는 적용하지 않는다.
3. `/charge`의 유의사항·오류·계산 결과 surface를 semantic role로 분리한다. 충전 완료 화면에서는 `추가 충전`을 sage primary로, 단순 이동인 `마이페이지로`를 secondary로 구분한다.
4. `/mypage` 허브는 sidebar·정보구조를 바꾸지 않고 PageBanner, 요약 surface, 보조 CTA만 공통 visual role에 맞춘다.
5. local-demo 문의 QA fixture를 보강한다. `LocalDemoDataSeeder`에 `demo-buyer-one` 소유의 미답변 문의 1건을 idempotent하게 추가하고, 모바일 줄바꿈을 확인할 수 있는 긴 제목·첨부 파일 없음·비어 있는 답변 및 답변 완료 시각을 유지한다. `/mypage/inquiry`의 populated 목록·상세와 `/admin/inquiries`의 목록·상세·답변 등록 CTA를 실제 브라우저에서 검증하는 용도로만 사용한다.

#### 제외

- `/upload`, `/mypage/manage-arts/:artId/edit`(EditArt), 탐색·작품·작가, 인증·프로필·리뷰, 입찰·낙찰·주문·판매 화면은 기준 화면으로 보존하며 회귀 확인만 한다.
- `/mypage` 허브 sidebar·정보구조의 대규모 재설계와 `/admin/inquiries`의 모바일 drill-in 구조는 별도 UX/구조 작업 후보로만 기록한다.
- 백엔드 API, 인증·권한, 도메인 규칙, URL·요청/응답 계약은 변경하지 않는다.
- 화면 역할과 무관하게 `/upload`의 sticky UI와 progress panel을 전파하지 않는다. sage gradient·drawing-paper texture와 butter 최종 CTA는 공통 브랜드 언어로 재정의하되, 화면별 layout은 복제하지 않는다.
- 충전 완료 화면을 위한 seed 데이터 또는 QA 전용 route는 추가하지 않는다. 충전 완료는 성공 제출 직후의 프론트 일시 상태이므로 기존 `Charge.test.jsx`의 성공 상태 검증을 사용하고, 브라우저 미검증 사유를 QA 결과에 기록한다.
- 구현 중 범위 밖 구조 문제가 발견되면 임의로 포함하지 않고 문제·영향·권장 대응을 보고한 뒤 PLAN 수정 여부를 결정한다.

### 공통 원칙

- 전역 토큰을 기계적으로 교체하지 않는다. 토큰과 전용 CSS의 사용처를 먼저 조사하고, UI 가이드와 충돌하는 사용처만 최소 변경한다.
- 일반 page canvas는 warm cream, enabled input/select/textarea와 card/form/content surface는 white를 유지한다. focus는 white + sage/deep-sage border/ring으로 표현하고, disabled/readonly와 error는 별도 semantic 상태로 둔다.
- 최종 등록·가입·저장·제출·확정 CTA는 butter, secondary는 white + sage border/text, danger는 danger text/outline을 기본으로 한다. sage/deep sage는 선택·상태·진행·focus·보조 행동과 구조적 강조에 사용한다.
- 공통 notice는 light sage surface, pagination active와 FilterBar active는 deep sage를 사용한다. `FilterBar`는 정상 사용처를 일괄 변경하지 않고, 실제 불일치가 확인된 문의 계열 사용처만 대상으로 한다.
- gray/tinted surface는 계산·진행·중요 안내처럼 의미가 있는 경우만 남긴다. 메타와 문장을 box/chip으로 과도하게 분리하지 않는다.
- 핵심 금액·상태·제목·label은 charcoal 중심으로 읽히게 하며, 정책·제한·다음 행동을 바꾸는 helper는 작은 muted caption으로 숨기지 않는다.
- `PageBanner`, Header, 공통 action/filter/pagination은 실제 사용처와 영향 범위를 확인한 뒤 최소 변경한다. 공통 PageBanner는 sage gradient + drawing-paper texture를 기본으로 하며 `/upload`는 이를 더 강하게 활용하는 전용 사례로 유지한다.

### 구현 후 사용자 브라우저 QA 대상

- 공통: 1440px, 1024px, 390px, 320px, 200% 확대에서 gutter, overflow, focus-visible, CTA 대비
- 대상 화면: 정적 안내, 문의 목록·관리·작성, 충전, 마이페이지 허브의 notice/error/final·state·secondary 역할과 모바일 정보 순서
- 문의 화면은 추가 fixture로 populated 목록·상세와 관리자 답변 등록 CTA까지 브라우저에서 확인한다.
- 충전 완료 화면은 `Charge.test.jsx`의 성공 상태 테스트로 검증하며, 브라우저 미검증 사유를 함께 남긴다.
- 기준 화면: `/upload`, EditArt, 탐색·작품·작가, 거래 화면의 surface, CTA, focus 상태 회귀 여부

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
