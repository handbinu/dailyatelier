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

## React Hooks lint 개선

### 범위

- `EditArt.jsx`에서 렌더 중 `Date.now()`를 제거하고, 경매 시작·종료 시점이 경과하면 잠금 UI가 갱신되도록 보완한다.
- `SalesOrders.jsx`의 렌더 중 `listParams.current` 갱신을 제거한다.
- `useDuplicateCheck.js`의 렌더 중 `valuesRef.current` 갱신을 제거한다.
- 수정 후 `react-hooks/purity`, `react-hooks/refs`를 재활성화해 해당 규칙의 lint 통과 여부를 검증한다.

제외: `set-state-in-effect` 전면 정리, `preserve-manual-memoization` 수정, React Compiler 도입, 관련 없는 구조 개선.

### 구현 단계

1. `EditArt.jsx`의 시간 기반 잠금 상태를 렌더 순수성을 지키는 상태·effect 흐름으로 옮기고, 시작·종료 시점에 UI가 다시 계산되도록 한다.
2. `SalesOrders.jsx`와 `useDuplicateCheck.js`에서 최신 값 전달 방식을 렌더 중 ref 대입 없이 동작하도록 최소 변경한다.
3. 두 lint 규칙을 활성화하고, 영향 파일의 동작과 전체 프론트 lint를 검증한다.

### 테스트 범위·완료 기준

- 작품 수정 화면에서 경매 시작·종료 시점 전후의 가격·기간·증분 입력 잠금 상태를 확인한다.
- 화면 재진입이나 별도 사용자 입력 없이 경매 시작·종료 시각이 경과하면 잠금 상태가 갱신되는지 확인한다.
- 판매 주문의 필터·페이지 변경 요청과 회원가입 중복 검사에서 최신 요청·입력값 처리 회귀가 없는지 관련 컴포넌트 테스트로 확인한다.
- `react-hooks/purity`, `react-hooks/refs` 활성화 상태에서 production 소스에 해당 lint 오류가 없어야 한다.
- 기존 프론트 lint와 영향 범위의 테스트를 통과해야 한다.

### 커밋 경계

1. 계획 문서: `chore: React Hooks lint 개선 계획 추가`
2. 구현 및 검증: `fix(frontend): React Hooks lint 위반 개선`
