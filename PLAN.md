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

## 모바일 메뉴의 페이지 이동 후 닫힘 처리

### 조사 결과

- `Layout`이 일반 페이지 전환 동안 유지되고 그 안의 `Header`도 재사용되므로, 일반 route 사이를 이동해도 `mobileOpen` 상태는 자동으로 초기화되지 않는다. 로그인·회원가입처럼 `BARE_ROUTES`로 이동할 때만 Header가 unmount된다.
- 모바일 메뉴 내부 링크와 모바일 검색 제출은 각각 `setMobileOpen(false)`를 직접 호출하지만, 로고 링크에는 닫기 처리가 없다. 브라우저 뒤로가기·앞으로가기처럼 클릭 handler를 거치지 않는 route 변경에도 닫기 처리가 없다.
- 따라서 메뉴를 연 채 로고로 이동하거나 history를 이동하면 일반 페이지 사이에서는 메뉴가 열린 상태, `aria-expanded="true"`, body 스크롤 잠금과 `main`/`footer`의 `inert`가 유지된다. 모바일 내부 링크와 검색 제출에서는 메뉴가 닫히며 effect cleanup으로 스크롤 잠금과 `inert`가 해제된다.
- 메뉴를 열면 첫 번째 focus 가능 요소로 focus를 옮기고, Escape로 닫으면 햄버거 버튼으로 focus를 돌린다. 데스크톱 breakpoint 진입과 모바일 링크·검색을 통한 닫기에는 별도 focus 복귀가 없다. route 전환 후 페이지 본문 focus를 관리하는 공통 장치는 현재 없다.
- 기존 `Header.test.jsx`는 검색 5건과 데스크톱 드롭다운 Escape/focus 1건을 검증한다. 모바일 검색 후 닫힘은 포함하지만 로고, 모바일 내부 링크, 뒤로가기·앞으로가기와 route 변경 후 스크롤·`inert`·focus 정리는 검증하지 않는다.
- 조사 시작 시 `git status --short` 출력은 없었으며 기존 미커밋 변경은 없다. 기존 Header 테스트 6건은 모두 통과했다.

### 변경 원칙과 최소 범위

- route 위치 변경을 Header 내부의 단일 초기화 신호로 사용해 이동 수단과 관계없이 모바일 메뉴를 닫는다.
- 기존 Escape의 햄버거 focus 복귀는 유지한다. route 이동 시에는 이동 출처의 focus를 햄버거로 강제 이동하지 않고, 제거된 모바일 메뉴 내부 요소에 focus가 남지 않으며 body 스크롤 잠금과 배경 `inert`가 함께 정리되는지를 보장한다. 애플리케이션 전체의 route focus 정책은 이번 범위에 포함하지 않는다.
- 데스크톱 내비게이션, 검색 상태, 인증 표시, Header CSS와 Layout 구조는 변경하지 않는다. 필요한 파일은 `frontend/src/components/Header/Header.jsx`와 `frontend/src/test/Header.test.jsx`로 제한한다.

### 구현 단계

1. `Header.test.jsx`의 router harness를 route history 이동을 제어할 수 있게 최소 보강하고, 현재 누락된 route 변경 회귀 테스트를 먼저 추가한다.
2. `Header.jsx`에서 location 변경 시 열린 모바일 메뉴 상태를 초기화하도록 처리한다. 기존 개별 링크·검색 닫기와 충돌하거나 focus를 불필요하게 이동시키지 않도록 닫힘 책임을 정리한다.
3. 메뉴 종료 effect가 route 변경에도 body `overflow`와 `main`/`footer`의 `inert`를 복원하고, 기존 Escape focus 복귀 및 데스크톱 breakpoint 동작을 유지하는지 회귀 검증한다.

### 자동 테스트 범위

- 모바일 메뉴를 연 뒤 로고를 클릭하면 `/`로 이동하고 메뉴가 닫히며 햄버거의 `aria-expanded`가 `false`가 되는지 검증한다.
- 모바일 메뉴 내부의 대표 공개 링크와 인증 사용자 링크가 이동 후 메뉴를 닫는지 검증한다.
- history stack을 구성해 뒤로가기와 앞으로가기를 각각 실행했을 때 열린 메뉴가 닫히는지 검증한다.
- route 변경으로 닫힌 뒤 body 스크롤 잠금과 `main`/`footer`의 `inert`가 해제되고, 제거된 메뉴 요소에 focus가 남지 않는지 검증한다.
- Escape 닫기 시 햄버거 focus 복귀, 모바일 검색 제출 후 닫힘, 기존 Header 검색·데스크톱 드롭다운 테스트를 함께 실행한다.
- `npm run test:component -- --run src/test/Header.test.jsx`, 프론트 전체 component test, production build를 순서대로 통과시킨다.

### 사용자 브라우저 QA 시나리오

1. 모바일 viewport에서 마이페이지 등 일반 페이지의 메뉴를 열고 로고를 눌러 홈으로 이동한다. 메뉴가 닫히고 본문이 스크롤 가능하며 배경 조작이 가능한지 확인한다.
2. 메뉴를 다시 열어 경매·작가 링크와 로그인 상태의 입찰 현황·마이페이지·작품 등록 중 노출되는 링크를 각각 눌러 이동한다. 새 페이지에서 메뉴가 닫혀 있는지 확인한다.
3. 서로 다른 일반 페이지를 방문해 history를 만든 뒤 메뉴를 연 상태로 브라우저 뒤로가기와 앞으로가기를 실행한다. 매번 메뉴, 햄버거 상태, 스크롤 잠금이 초기화되는지 확인한다.
4. 키보드로 메뉴를 열어 내부 요소에 focus를 둔 뒤 route 이동을 실행한다. 사라진 메뉴에 focus가 갇히지 않는지 확인하고, 별도로 Escape로 닫을 때는 햄버거로 focus가 복귀하는지 확인한다.
5. 메뉴를 연 채 viewport를 데스크톱 너비로 변경했을 때 기존처럼 닫히고, 데스크톱 드롭다운과 검색이 정상 동작하는지 확인한다.

### 예상 커밋 경계

1. `chore: 모바일 메뉴 닫힘 처리 계획 추가` — 사용자 승인된 이 계획만 별도 커밋한다.
2. `fix(frontend): 페이지 이동 시 모바일 메뉴 닫기` — Header 최소 구현과 관련 자동 테스트를 함께 커밋한다.
3. `chore: 모바일 메뉴 닫힘 처리 완료 기록` — 검증 결과를 `PLAN_DONE.md`에 기록하고 `PLAN.md`의 완료 계획과 `BACKLOG.md`의 해당 항목을 정리한다.
