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

## 문제 묶음 2 — 선택·페이지 전환의 늦은 응답 반영 방지

### 확인 결과

- 작업 시작 전 `git status --short` 출력은 비어 있어 기존 미커밋 변경은 없다.
- 관리자 문의 상세는 `selectInquiry` 호출마다 별도 보호 없이 `getInquiryDetail`을 기다린다. 문의 A를 요청한 뒤 B를 선택했을 때 A가 늦게 완료되면 `selected`와 `answer`를 A 값으로 함께 되돌릴 수 있고, A의 `finally`가 B 조회 중 `detailLoading`도 먼저 해제할 수 있다. 상세 조회 실패 때도 이전 선택과 답변 값이 남을 수 있다.
- 내 문의 목록 자체는 마운트 단위 `AbortController`로 언마운트 후 반영을 막고 있다. 반면 상세 조회는 취소 신호를 전달하지 않는다. 상세 데이터는 문의 ID별 캐시에 저장되어 서로 덮지 않지만, 공유 상태인 `loadingDetailId`와 `detailError`는 먼저 시작한 요청의 늦은 성공·실패·`finally`가 현재 펼친 문의 B의 로딩 또는 오류 표시를 바꿀 수 있다. 문의를 접거나 이미 캐시된 다른 문의를 펼치는 경우에도 진행 중 요청을 명시적으로 무효화해야 한다.
- 낙찰 작품 목록의 `getMyWins` API 함수는 이미 `signal`을 받을 수 있지만 `SuccessfulBid`가 전달하지 않는다. 페이지를 연속 이동하거나 재시도하면 이전 페이지의 늦은 성공, 실패 또는 `finally`가 최신 페이지의 `result`, `error`, `loading`을 덮을 수 있고, 언마운트 시 활성 요청도 취소되지 않는다.
- 기존 `createLatestRequest`는 새 요청 시 이전 `AbortController`를 취소하고, 세대와 활성 요청을 함께 검사해 취소를 무시하는 서버·테스트 대역의 늦은 완료도 폐기하며, `dispose`로 언마운트 이후 콜백을 막는다. 주문 목록(`OrderStatus`, `SalesOrders`)에서 목록 페이지·필터 조회에 이미 사용 중이다.
- `getInquiryDetail`, `getAdminInquiries`, `getMyInquiries`, `getMyWins`는 모두 선택적 `signal` 계약을 이미 제공한다. 이번 범위에서 API 함수 시그니처나 백엔드 변경은 필요하지 않다.

### 작업 단위 판단

- 세 화면을 하나의 구현 커밋으로 묶지 않는다.
- 관리자 문의와 내 문의는 같은 상세 API와 동일한 컴포넌트 테스트 파일을 사용하고, 빠른 A→B 선택에서 상세·로딩·오류 상태의 일관성을 함께 검증하므로 하나의 문의 상세 단위로 묶는다.
- 낙찰 작품은 페이지 목록 요청만 다루며 문의 선택·답변 상태와 독립적이므로 별도 단위와 커밋으로 분리한다.
- 새 전역 요청 관리자나 공통 상태 프레임워크는 만들지 않는다. 목록형 연속 요청에는 기존 `createLatestRequest`를 재사용하고, 캐시 조회·접기처럼 네트워크 요청이 없는 동작에서도 즉시 취소가 필요한 내 문의 상세에는 화면 로컬 `AbortController`를 사용한다.

### 1단계 — 문의 상세 선택의 최신 응답만 반영

대상 파일:

- `frontend/src/pages/MyPage/AdminInquiry.jsx`
- `frontend/src/pages/MyPage/InquiryList.jsx`
- `frontend/src/test/Inquiry.test.jsx`

작업 내용:

- 관리자 문의 상세 조회에 컴포넌트 로컬 `createLatestRequest` 인스턴스를 두고, 문의 선택마다 이전 상세 요청을 취소한 뒤 `signal`을 `getInquiryDetail`에 전달한다.
- 최신 상세 요청만 `selected`, `answer`, 상세 오류와 `detailLoading`을 변경하게 한다. 새 문의 조회 시작 시 이전 선택·답변을 비워 실패 후 이전 문의나 입력값이 현재 선택처럼 남지 않게 하고, 언마운트 시 요청을 폐기한다.
- 답변 등록 중 선택 차단, 제출 대상·답변 스냅샷, 등록 성공 후 목록 갱신과 오류 분리 동작은 그대로 유지한다. 관리자 문의 목록 필터 조회의 별도 경합 개선은 이번 상세 선택 범위에 포함하지 않는다.
- 내 문의 상세에는 화면 로컬 활성 `AbortController` 참조를 두고 모든 토글 시작 시 직전 상세 요청을 취소한다. 새 상세 요청에는 `signal`을 전달하고, 현재 컨트롤러와 문의 ID가 일치할 때만 캐시·오류·로딩 상태를 반영한다.
- 문의 접기와 캐시된 문의 열기에서도 진행 중인 다른 문의 요청을 먼저 무효화하며, 언마운트 시 취소한다. 기존 문의 ID별 상세 캐시는 유지한다.

완료 기준:

- 관리자 문의 A→B 요청을 역순으로 완료해도 B의 상세와 답변 입력값만 남고, A의 오류나 `finally`가 B 상태를 바꾸지 않는다.
- 내 문의 A→B를 빠르게 펼치거나 A 요청 중 접기·캐시된 B 열기를 수행해도 현재 펼친 항목의 로딩·오류만 표시되고 늦은 A 응답은 화면 상태에 반영되지 않는다.
- 취소된 요청은 사용자 오류로 표시하지 않으며 언마운트 이후 상태 갱신이 없다.
- 기존 답변 중 중복 제출·선택 차단 및 답변 성공 후 목록 재조회 실패/재시도 테스트가 계속 통과한다.

예정 커밋:

- `fix(frontend): 문의 상세의 늦은 응답 반영 방지`

### 2단계 — 낙찰 작품 페이지의 최신 목록 응답만 반영

대상 파일:

- `frontend/src/pages/MyPage/SuccessfulBid.jsx`
- `frontend/src/test/OrderPostAuctionFlow.test.jsx`

작업 내용:

- 낙찰 작품 조회에 컴포넌트 로컬 `createLatestRequest`를 적용하고, 현재 페이지 값을 요청 시작 시 캡처해 `getMyWins`에 `signal`과 함께 전달한다.
- 페이지 이동이나 다시 시도로 새 조회가 시작되면 이전 요청을 취소하고, 최신 요청만 `result`, 인증/일반 오류와 `loading`을 변경하게 한다.
- 언마운트 시 활성 요청을 폐기한다. 기존 카드, 주문 연결, 페이지네이션 UI와 API 계약은 유지한다.

완료 기준:

- 연속 페이지 이동 요청을 역순으로 완료해도 최신 페이지 데이터와 페이지 표시만 유지된다.
- 이전 페이지의 늦은 실패 및 `finally`가 최신 성공 결과를 지우거나 로딩을 조기에 종료하지 않고, 취소 오류도 표시하지 않는다.
- 기존 낙찰 작품의 `orderId` 주문 링크와 주문 미연결 안내 테스트가 계속 통과한다.

예정 커밋:

- `fix(frontend): 낙찰 목록의 늦은 응답 반영 방지`

### 검증

1. `npm run test -- --test-name-pattern="최신 요청"`로 기존 `createLatestRequest`의 늦은 성공·실패·취소·dispose 계약을 확인한다.
2. `npm run test:component -- src/test/Inquiry.test.jsx src/test/OrderPostAuctionFlow.test.jsx`로 문의 선택 경합, 관리자 답변 회귀, 낙찰 페이지 이동 경합과 주문 링크 회귀를 검증한다.
3. `npm run lint`와 `npm run build`로 전체 프론트 정적 검사와 프로덕션 빌드를 확인한다.

### 범위 제외

- 관리자 문의 목록 필터/답변 후 목록 재조회 사이의 최신 요청 보호
- 문의 목록 또는 낙찰 목록의 skeleton·기존 콘텐츠 유지 등 시각적 로딩 UX 변경
- URL 페이지 상태 보존, 재시도 정책 확대, 새 공통 상태·요청 프레임워크
- 백엔드 API, DTO, 오류 응답 계약 변경
