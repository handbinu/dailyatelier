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

## Playwright 기반 UI 자동 QA MVP

### 목표

- local-demo fixture를 사용해 핵심 public/protected 화면의 실제 브라우저 회귀를 Chromium에서 확인한다.
- 1440px desktop과 390px mobile에서 horizontal overflow, console error, 실패 screenshot을 최소 자동 QA 기준으로 만든다.

### 구현 범위

1. frontend에 Playwright Chromium 설정과 `qa:ui` 실행 스크립트를 추가한다.
2. API 로그인 결과를 localStorage storageState로 저장해 구매자·작가·관리자 protected route를 역할별로 테스트한다. local-demo QA 계정 비밀번호와 인증 토큰은 추적되는 코드·문서에 하드코딩하지 않고, 환경변수 또는 Git에서 제외된 로컬 설정으로 주입한다.
3. `/login`, `/`, `/auction/total`, `/mypage/inquiry`, `/mypage/manage-arts`, `/admin/inquiries`의 읽기 전용 smoke 테스트를 추가한다.
4. 각 대상에서 console error와 horizontal overflow를 검증하고, 실패 시에만 screenshot과 trace를 남긴다. screenshot은 `only-on-failure`를 유지한다.
5. Playwright `storageState`, `test-results`, `playwright-report`를 Git 추적 대상에서 제외하고, local-demo 실행 방법·계정 주입 방법·산출물 위치를 QA 문서에 기록한다.

### 실행 환경 원칙

- Playwright는 frontend/backend 서버를 직접 시작하거나 종료하지 않는다.
- 사용자가 별도로 실행한 local-demo backend와 Vite frontend를 대상으로 QA만 수행한다.

### 제외 범위

- axe 접근성 검사
- 전체 화면 baseline 저장과 visual regression 비교는 2차 범위로 남긴다.
- AI 자동 분석
- CI/CD 연동
- 외부 유료 서비스
- 입찰·충전·주문·답변 등록처럼 fixture를 변경하는 E2E
- 테스트용 DB 초기화·삭제 자동화

### 완료 조건

- local-demo 실행 환경에서 Chromium desktop/mobile 프로젝트가 모두 실행된다.
- 최소 public/protected route가 역할별 storageState로 통과하며, 비밀번호와 인증 토큰이 추적 파일에 남지 않는다.
- `storageState`, `test-results`, `playwright-report`는 Git 추적 대상이 아니다.
- 테스트 실패 시에만 screenshot과 trace가 남고, console error 및 horizontal overflow가 테스트 실패로 보고된다.
- QA 실행은 사용자가 기동한 backend/frontend를 대상으로만 수행하며, 테스트가 두 서버의 생명주기를 변경하지 않는다.
