# DailyAtelier 개발 계획

> [!IMPORTANT]
> `PLAN.md`는 현재 승인된 작업 계획을 관리한다.
>
> 작업 완료 후 핵심 구현·검증 결과를 `PLAN_DONE.md`에 기록한 다음 해당 계획을 `PLAN.md`에서 제거한다.
>
> 현재 범위에 포함되지 않은 미완료·후속 작업은 `BACKLOG.md`에서 관리한다.
>
> 새 계획이나 범위 변경은 사용자 승인 후 반영한다.

## Playwright 전체 UI 스크린샷 자동 수집

### 목적과 범위

- 기존 UI QA에서 검증하는 고유 화면 25개와 미방문 고유 화면 6개(일반 회원가입, 작가 회원가입, 1:1 문의 작성, 작품 등록, 작품 수정, 리뷰 작성)를 대상으로 한다. `/reliable-status`는 `/mypage/bid-status`와 같은 컴포넌트이므로 중복 촬영하지 않는다.
- 각 대상은 `desktop-chromium`(1440×900)과 `mobile-chromium`(390×844)에서 `fullPage: true`로 촬영한다. 등록·수정·작성 폼은 fixture 조회와 초기 렌더링까지만 수행하고 입력·제출·삭제·입찰·결제 등 데이터 변경 동작은 하지 않는다.
- 별도 상태 컷은 작품 원본 이미지 모달, 구매자/작가 리뷰 상세 모달, 주문 상세 펼침, 문의 상세 펼침을 포함한다. 상태 진입은 기존 fixture의 조회 또는 버튼 클릭만 사용한다.

### 구현 범위와 저장 구조

- `frontend/e2e/screenshots/`에 화면 목록·역할별 캡처 spec을 추가하고, 기존 `fixtures/auth.ts`의 buyer/artist/admin storage state와 `global.setup.ts`를 그대로 재사용한다. 공개 화면은 기본 fixture를 사용한다.
- `frontend/playwright.screenshots.config.ts`를 새로 두어 기존 `playwright.config.ts` 및 `npm run qa:ui`와 독립된 testDir, outputDir, reporter를 사용한다. 기본 config의 두 project/viewport는 동일하게 유지한다.
- 실행 산출물은 Git 제외 대상인 `frontend/ui-screenshots/<실행시각>/` 아래에 저장한다.
  - `desktop/`, `mobile/`: `<역할>__<화면키>__<상태>.png` (예: `buyer__order-status__detail.png`)
  - `manifest.json`: 파일 상대경로, 화면키, route/fixture 역할, viewport, UI 상태, 촬영 결과(`captured`/`skipped`/`failed`), 사유를 기록한다.
- 각 capture point는 화면 준비 후 `console.error`와 horizontal overflow를 검사하고 결과를 manifest에 남긴다. console error 또는 overflow가 있으면 해당 capture point는 `failed`로 판정하되, PNG는 가능한 경우 보존한다.
- desktop/mobile project가 같은 manifest를 동시에 갱신하지 않도록 project별 `manifest.<project>.json`을 먼저 기록하고, 전체 종료 시 단순 병합하여 `manifest.json`을 생성한다. 각 기록은 화면키·상태·project를 식별자로 갖고, 같은 식별자의 중복·누락은 병합 단계에서 실패로 기록한다.
- manifest의 상태는 `captured`(PNG와 모든 검사 통과), `skipped`(사전 정의된 촬영 불가 사유와 함께 의도적으로 제외), `failed`(준비·촬영·console error·overflow·병합 검증 실패)로 구분한다. 대상 31개 화면과 정의된 주요 상태가 모두 `captured` 또는 근거 있는 `skipped`인지 확인하며, manifest 누락은 완료로 간주하지 않고 `failed`로 처리한다. 개별 실패는 다른 capture point 수집을 계속하게 하되, 하나라도 `failed`이거나 누락이 있으면 최종 실행은 실패 상태로 종료한다.

### 실행과 검증

- local-demo backend와 Vite frontend를 실행하고, 기존 UI QA와 같은 `UI_QA_*` 계정 환경변수를 설정한다. 비밀번호는 `_local/test-accounts.md`에서만 읽고 산출물·manifest에 기록하지 않는다.
- 새 명령은 `npm run screenshots:ui`로 추가하고, 필요 시 `--project=desktop-chromium` 또는 `--project=mobile-chromium`을 전달할 수 있게 한다. 기존 `npm run qa:ui`는 변경하지 않는다.
- 검증은 양 project에서 31개 화면의 기본 상태와 위 주요 상태가 manifest에 남는지, PNG 파일 존재와 manifest 경로·역할·상태의 일치, 그리고 캡처 중 변경 API 요청이 발생하지 않는지를 확인한다. 기존 UI QA는 회귀 확인용으로 별도 실행한다.

### 조사 결과: 촬영 제약과 대응

- 작품 수정은 artist fixture의 진행 중 작품 카드에서 `수정·삭제` 링크를 따라가야 안정적인 `artId`를 얻는다. URL의 고정 ID는 사용하지 않는다.
- 리뷰 작성은 현재 구매자 fixture에서 이미 작성된 리뷰/주문일 수 있어, 주문 목록에서 `리뷰 작성`이 실제 노출되는 fixture가 없으면 초기 작성 폼 촬영을 `skipped`와 사유로 manifest에 남긴다. 대안은 local-demo에 구매 확정·미작성 리뷰 주문 fixture를 추가한 뒤 해당 UI만 촬영하는 것이다.
- 회원가입·문의 작성·작품 등록은 초기 빈 폼만 촬영하므로 데이터 변경 위험이 없다. 작품 수정은 조회된 진행 작품의 초기값만 촬영한다.
- 우편번호 모달은 외부 주소 검색 위젯 의존성이 있어, 로컬에서 로드되지 않으면 대상에서 제외하고 `skipped` 사유를 남긴다. 반면 작품 이미지·리뷰·주문·문의의 모달/펼침은 현재 local-demo fixture와 컴포넌트에 존재한다.
