# DailyAtelier 6단계 포인트 원장 기능 개발 계획

> [!IMPORTANT]
> **현재 진행 단계:** 5단계 — API와 프론트 연동 구현 및 검증 완료, 6단계 시작 전
>
> **작업 범위:** 내부 포인트 계정·불변 원장, 포인트 충전 상태 모델, 입찰 예치·해제·확정,
> 낙찰 주문 포인트 결제, 취소·환불 반대 거래, 동시성·멱등성·실패 재처리와 관련 API·화면·테스트
>
> **이번 단계에서 하지 않는 내용:** 네이버페이·토스페이 실제 API 호출과 웹훅 연동,
> 카드·계좌이체, 복합 결제, 부분 결제·부분 환불, 차순위 낙찰자 승계와 낙찰 포기 제재
>
> **결제수단 정책:** 이번 구현에서 작품 주문의 결제수단은 `INTERNAL_POINT` 하나로 고정한다.
> 결제수단 분기는 최소 인터페이스만 두며 네이버페이·토스페이 구현체는 추가하지 않는다.
>
> **문서 관리 규칙:** 이 공지는 이후 단계에서도 `PLAN.md` 최상단에 유지하며 단계 진행 시
> 현재 단계·작업 범위·이번 단계에서 하지 않는 내용을 갱신한다. 전체 작업 완료 후 완료된 계획과
> 검증 결과를 `PLAN_DONE.md`로 옮기고, `PLAN.md`에서는 완료된 내용을 제거해 다음 작업을 위한
> 빈 계획 문서로 정리한다. 미완료·후속 작업은 삭제하지 않고 `BACKLOG.md`로 이동한다.

## 1. 선행 조사 결과와 확정 정책

### 현재 구현

- `User.reserve`는 `Integer` 단일 필드이며 회원가입 시 `0`으로 초기화된다.
- 프로필 API와 마이페이지가 `reserve`를 보유 포인트로 표시하지만 차감·예치·원장은 없다.
- `Charge.jsx`는 `MOCK_USER.reserve`와 지연 타이머를 사용하는 목업이며 실제 충전 API가 없다.
- 입찰은 `Art` 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 `Art.currentPrice` 변경과 `Bid` 저장을
  하나의 트랜잭션으로 처리한다.
- 동일 작품의 입찰·수정·취소·마감은 모두 작품 행 잠금으로 직렬화된다.
- 자동 마감은 최고 입찰과 현재가를 검증하고 낙찰 확정과 `PAYMENT_PENDING` 주문 생성을
  같은 작품 단위 트랜잭션으로 처리한다.
- 주문은 작품당 하나만 허용하며 `orders.art_id` 유니크 제약이 있다.
- 결제·포기·만료·환불은 주문 행 비관적 잠금 후 최신 상태를 재검사한다.
- `OrderStateService.markPaid()`는 주문 상태만 `PAID`로 바꾸며 실제 포인트 처리는 없다.
- 경매 취소는 입찰 이력을 보존하고 작품을 `CANCELED`로 바꾸지만 예치 해제 접점은 없다.
- 운영 스키마는 명시적 마이그레이션 없이 `hibernate.ddl-auto=update`에 의존한다.

### 낙찰과 포인트 차감 시점

기존 주문의 24시간 결제 대기 정책을 유지한다.

1. 경매 진행 중 최고 입찰 금액은 `HELD` 상태로 유지한다.
2. 낙찰 마감 시 주문을 `PAYMENT_PENDING`으로 생성하고 낙찰자의 예치를 유지한다.
3. 구매자가 포인트 결제를 확정할 때 예치를 `COMMITTED`로 바꾸고 주문을 `PAID`로 전환한다.
4. 예치 확정과 주문 `PAID` 전환은 반드시 같은 데이터베이스 트랜잭션에서 처리한다.
5. 결제 전 낙찰 포기 또는 결제 기한 만료 시 예치를 `RELEASED`로 바꾼다.

따라서 이번 계획에서 “낙찰 포인트 최종 차감”은 단순 마감 시점이 아니라
낙찰 주문의 포인트 결제 승인 시점을 의미한다.

### 원장 기본 원칙

- `PointTransaction`은 저장 후 수정·삭제하지 않는 불변 거래 원장으로 사용한다.
- 잔액 변경은 항상 원장 거래와 같은 트랜잭션에서 발생해야 한다.
- 취소·환불은 과거 거래를 수정하거나 삭제하지 않고 반대 거래를 추가한다.
- 모든 금액은 `Integer` 대신 `Long`을 사용하고 음수를 허용하지 않는다.
- 사용 가능 잔액과 입찰 예치 잔액을 구분한다.
- `PointAccount`는 빠른 검증·조회용 현재값이고 `PointTransaction`은 감사·복구의 근거다.
- 정합성 검사에서 계정 잔액과 원장 합계를 사용자별로 대조할 수 있어야 한다.

## 2. 엔티티·enum·상태 전이

### `PointAccount`

- 사용자 공유 기본키 `userId`
- 사용 가능 포인트 `availableBalance`
- 입찰 예치 포인트 `heldBalance`
- 동시 변경 추적용 `version`
- `createdAt`, `updatedAt`

불변식:

- `availableBalance >= 0`
- `heldBalance >= 0`
- 잔액은 원장 거래 없이 직접 변경하지 않는다.
- `availableBalance + heldBalance`는 아직 소비되지 않은 총 포인트다.

### `PointTransaction`

- `transactionId`, `userId`
- `type`, 양수 `amount`
- `availableDelta`, `heldDelta`
- `availableBalanceAfter`, `heldBalanceAfter`
- `referenceType`, `referenceId`
- `idempotencyKey`
- `reversalOfTransactionId`
- `reasonCode`, `description`, `createdAt`

`PointTransactionType`:

- `OPENING_BALANCE`: 기존 `User.reserve` 이전
- `CHARGE`: 충전 완료
- `HOLD`: 신규 최고 입찰 예치
- `HOLD_INCREASE`: 현재 최고 입찰자의 차액 추가 예치
- `RELEASE`: 최고가 상실·경매 취소·결제 전 주문 취소에 따른 예치 해제
- `COMMIT`: 낙찰 주문 결제에 따른 예치 최종 차감
- `REFUND`: 결제 취소에 따른 사용 가능 포인트 복원
- `ADJUSTMENT_CREDIT`, `ADJUSTMENT_DEBIT`: 내부 관리자 보정 전용

| 거래 | `availableDelta` | `heldDelta` |
|---|---:|---:|
| 10,000P 충전 | +10,000 | 0 |
| 7,000P 입찰 예치 | -7,000 | +7,000 |
| 2,000P 재입찰 차액 예치 | -2,000 | +2,000 |
| 9,000P 예치 해제 | +9,000 | -9,000 |
| 9,000P 낙찰 결제 확정 | 0 | -9,000 |
| 9,000P 주문 환불 | +9,000 | 0 |

### `PointHold`

- `holdId`, `artId`, `userId`
- 현재 예치가 연결된 `latestBidId`
- `amount`, `status`
- `createdAt`, `updatedAt`, `releasedAt`, `committedAt`
- `releaseReason`, `commitOrderId`

`PointHoldStatus`:

- `HELD`
- `RELEASED`
- `COMMITTED`

허용 전이:

- 신규 생성 → `HELD`
- `HELD → RELEASED`
- `HELD → COMMITTED`
- `RELEASED`, `COMMITTED`는 최종 상태다.
- 패찰 후 같은 사용자가 다시 최고 입찰자가 되면 새 `PointHold`를 생성한다.
- 현재 최고 입찰자가 자기 입찰가를 올리면 기존 `HELD`의 금액만 늘린다.

`Art`에는 현재 활성 예치를 가리키는 `activePointHold`를 추가한다.
작품 행 잠금과 이 관계를 이용해 같은 작품에 활성 예치가 둘 이상 생기지 않게 한다.

### `PointCharge`

- `chargeId`, `userId`, `provider`
- 서버 생성 고유 주문번호 `merchantOrderId`
- PG 발급 주문번호 `pgOrderId`; 내부 방식에서는 nullable
- `requestedAmount`, `paidAmount`
- `status`, `idempotencyKey`
- `failureCode`, `failureMessage`
- `createdAt`, `paidAt`, `failedAt`, `canceledAt`, `refundedAt`
- 원 충전·환불 원장 거래 참조

`PointChargeStatus`:

- `PENDING`
- `PAID`
- `FAILED`
- `CANCELED`
- `REFUNDED`

허용 전이:

- `PENDING → PAID`
- `PENDING → FAILED`
- `PENDING → CANCELED`
- `PAID → REFUNDED`
- `FAILED`, `CANCELED`, `REFUNDED`는 최종 상태다.
- 결제 실패 후 재시도는 실패 건을 되살리지 않고 새 결제 시도와 PG 주문번호를 만든다.

### 결제수단 확장 지점

`OrderPaymentMethod`:

- `INTERNAL_POINT`만 이번 범위에서 사용

`PaymentProvider`:

- `INTERNAL`
- `NAVER_PAY`
- `TOSS_PAY`

이번 구현에서는 `INTERNAL` 구현체만 활성화한다.
`NAVER_PAY`, `TOSS_PAY`는 enum과 어댑터 인터페이스 이상의 구현을 하지 않는다.

### 데이터베이스 제약과 인덱스

- `point_account.user_id`: PK/FK
- `point_transaction.idempotency_key`: 유니크
- 원거래별 동일 유형 반대 거래 중복 방지 제약
- `point_transaction(user_id, created_at, transaction_id)`: 원장 조회
- `point_transaction(reference_type, reference_id, type)`: 업무 참조 조회
- `point_hold(art_id, created_at)`: 작품별 예치 이력
- `point_hold(user_id, status, created_at)`: 사용자 활성 예치 조회
- `point_charge.merchant_order_id`: 유니크
- `point_charge(provider, pg_order_id)`: nullable 유니크
- `point_charge(user_id, idempotency_key)`: 유니크
- 잔액과 금액의 `CHECK` 제약 적용 여부를 실제 MySQL 버전에서 검증한다.

## 3. 트랜잭션 경계와 동시성 제어

### 공통 잠금 원칙

입찰 관련 흐름은 다음 순서를 고정한다.

1. 대상 `Art` 행 비관적 쓰기 잠금
2. 현재 활성 hold와 이전 최고 입찰자 확인
3. 관련 `PointAccount`를 사용자 ID 오름차순으로 비관적 쓰기 잠금
4. 최신 상태·현재가·잔액 재검사
5. 업무 엔티티·계정 잔액·원장 거래 저장
6. 트랜잭션 커밋

주문 결제 흐름은 `Order → PointAccount → PointHold` 순서로 잠근다.
잠금 순서를 거꾸로 사용하는 서비스가 없도록 저장소 호출 규칙을 통합 테스트로 검증한다.

- 잠금 시간 초과와 교착은 무한 재시도하지 않는다.
- 교착은 서비스 경계에서 제한 횟수만 재시도한다.
- 재시도 후 실패하면 `409 POINT_CONFLICT`를 반환한다.
- 같은 멱등성 키의 재호출은 기존 성공 결과를 반환한다.
- 운영 로그에 주문·작품·사용자·멱등성 키를 남긴다.

### 입찰 트랜잭션

한 트랜잭션에서 다음을 모두 처리한다.

1. 작품 잠금 및 경매 상태·현재가 재검사
2. 활성 hold와 관련 포인트 계정 잠금
3. 신규 입찰 금액과 추가 예치액 계산
4. 사용 가능 잔액 검증
5. `Bid` 저장
6. 신규 hold 생성 또는 현재 최고 입찰자의 hold 증액
7. 새 입찰자의 `HOLD` 또는 `HOLD_INCREASE` 원장 기록
8. 이전 최고 입찰자가 다르면 기존 hold를 `RELEASED`로 변경
9. 이전 최고 입찰자의 `RELEASE` 원장 기록과 잔액 복원
10. `Art.currentPrice`, `activePointHold` 갱신

규칙:

- 현재 최고 입찰자와 새 입찰자가 같으면 `새 입찰가 - 기존 hold 금액`만 추가 예치한다.
- 이전에 패찰하여 hold가 해제된 사용자가 다시 입찰하면 전액을 새로 예치한다.
- 잔액이 부족하면 전체 트랜잭션을 롤백하고 `INSUFFICIENT_POINTS`를 반환한다.
- 입찰·원장·계정·hold 중 하나라도 저장 실패하면 현재가와 모든 변경을 롤백한다.
- 같은 작품의 입찰은 작품 행 잠금, 다른 작품에서 같은 사용자 잔액을 쓰는 요청은
  계정 행 잠금으로 직렬화한다.

### 경매 취소 트랜잭션

`ArtService.deleteArt()`의 입찰 있는 작품 취소 흐름에서:

1. 작품과 활성 hold·계정을 잠근다.
2. 작품을 `CANCELED`로 전환한다.
3. 활성 hold를 `RELEASED`로 전환한다.
4. `RELEASE` 반대 거래를 추가하고 사용 가능 잔액을 복원한다.
5. 기존 입찰과 원장 이력은 삭제하지 않는다.

### 자동 마감 트랜잭션

- 유찰 작품에는 활성 hold가 없어야 한다.
- 낙찰 시 최고 입찰과 활성 `HELD`의 사용자·금액이 일치하는지 검증한다.
- 불일치하면 정합성 오류를 기록하고 마감·주문 생성을 모두 롤백한다.
- 작품을 `SOLD`로 전환하고 기존처럼 주문을 멱등 생성한다.
- hold는 결제 전까지 `HELD`로 유지한다.
- 마감 재실행은 주문과 hold 상태를 중복 변경하지 않는다.
- 자동 마감 실패는 기존 스케줄러 방식대로 다음 주기에 재처리한다.

### 낙찰 주문 포인트 결제 트랜잭션

1. 주문 행 비관적 쓰기 잠금
2. 본인 주문, `PAYMENT_PENDING`, 결제 기한과 배송지 확정 여부 재검사
3. 결제수단 `INTERNAL_POINT` 검증
4. `Order.winningPrice`와 `PointHold.amount` 재검증
5. hold가 `HELD`인지 확인
6. 계정의 `heldBalance` 재검증
7. `COMMIT` 원장 거래 추가
8. hold를 `COMMITTED`로 변경
9. `heldBalance`에서 낙찰가 차감
10. 주문을 `PAID`로 전환

일부 성공은 허용하지 않으며 하나라도 실패하면 원장·hold·잔액·주문 상태를 모두 롤백한다.

### 주문 포기·기한 만료

`PAYMENT_PENDING → CANCELED`와 같은 트랜잭션에서:

- `HELD → RELEASED`
- `RELEASE` 원장 추가
- `heldBalance`를 `availableBalance`로 복원

결제와 만료·포기가 경합하면 주문 행 잠금 후 먼저 커밋된 상태만 유효하다.
후행 작업은 최신 상태를 보고 멱등 종료하거나 `409`를 반환한다.

### 환불

- 기존 `COMMIT` 또는 `CHARGE` 거래를 삭제하거나 수정하지 않는다.
- 원거래를 참조하는 `REFUND` 반대 거래를 추가한다.
- 환불액을 사용 가능 잔액에 복원한다.
- 주문 행 잠금과 데이터베이스 유니크 제약으로 동일 주문의 중복 환불을 막는다.
- 주문 `REFUNDED`, 반대 원장과 잔액 복원은 같은 트랜잭션에서 처리한다.

## 4. 멱등성·PG 메타데이터·실패 재처리

### 멱등성 키

다음 쓰기 작업은 `Idempotency-Key`를 필수로 받는다.

- 포인트 충전 생성·승인
- 낙찰 주문 포인트 결제
- 결제 취소·환불
- 외부 결제 콜백 처리용 내부 명령

처리 규칙:

- 같은 사용자·작업·키와 같은 요청 값이면 기존 결과를 반환한다.
- 같은 키에 금액·주문·작품이 다르면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.
- 같은 키가 처리 중이면 완료 결과를 재조회하거나 `409 PROCESSING`을 반환한다.
- 성공 후 응답 전 연결이 끊겨도 재호출로 중복 거래를 만들지 않는다.

### PG 주문번호와 금액 재검증

- `merchantOrderId`는 서버가 생성하고 변경하지 않는다.
- `pgOrderId`는 `(provider, pgOrderId)` 유니크로 저장한다.
- 클라이언트가 전달한 금액만 신뢰하지 않는다.
- 서버 요청 금액, PG 승인 조회 금액, 콜백 금액과 발행 포인트를 모두 비교한다.
- 하나라도 다르면 `PAID`와 `CHARGE`를 생성하지 않고 정합성 오류를 기록한다.
- 주문 결제는 `Order.winningPrice`, `PointHold.amount`, `PointTransaction.amount`가 같아야 한다.

### 중복 콜백 방지

향후 PG 콜백을 위한 `PaymentCallbackEvent` inbox를 둔다.

- `provider`, `providerEventId`, `pgOrderId`
- 원문 해시
- 처리 상태 `RECEIVED`, `PROCESSED`, `FAILED`
- 시도 횟수, 마지막 오류, 수신·처리 시각
- `(provider, providerEventId)` 유니크

실제 네이버페이·토스페이 웹훅 컨트롤러와 서명 검증은 이번 범위에서 제외한다.
내부 어댑터 테스트로 같은 이벤트가 반복 전달되어도 중복 충전되지 않는 계약을 검증한다.

### 실패 재처리

- 내부 오류는 트랜잭션을 롤백해 원장·잔액·상태를 모두 원복한다.
- 콜백 처리 실패는 inbox에 오류와 시도 횟수를 기록한다.
- 지수 백오프와 최대 재시도 횟수를 적용한다.
- 최대 횟수 초과 건은 운영 재처리 대상으로 분리한다.
- 재처리는 같은 멱등성 키와 provider event ID를 사용한다.
- `FAILED` 결제를 임의로 `PAID`로 되살리지 않는다.
- 외부 승인 성공 여부가 불명확한 timeout은 PG 주문 조회 후 확정하는 어댑터 계약을 둔다.

## 5. 6단계 구현 순서

### 1단계 — 스키마와 불변 원장 기반

- [x] 명시적 DB 마이그레이션 방식을 도입하고 운영 DDL 자동 변경을 제한한다.
- [x] `PointAccount`, `PointTransaction`, `PointHold`, `PointCharge`와 관련 enum을 추가한다.
- [x] 저장소, 유니크 제약과 조회 인덱스를 추가한다.
- [x] 기존 `User.reserve`를 `PointAccount.availableBalance`로 이관한다.
- [x] 기존 잔액마다 `OPENING_BALANCE`를 한 번만 생성한다.
- [x] 프로필 조회를 `PointAccount` 기준으로 변경하고 `User.reserve` 직접 쓰기를 금지한다.

검증:

- [x] 신규 사용자 포인트 계정 초기화
- [x] 기존 잔액 이관과 마이그레이션 재실행 멱등성
- [x] 음수 잔액·금액 차단과 유니크 제약
- [x] 실제 MySQL 스키마와 계정·원장 합계 대조

검증 결과 (2026-07-31):

- H2 단위·저장소 제약·트랜잭션·동시성 테스트 통과
- 신규 가입 계정 생성, 기존 잔액 이관 1회 보장, 원장 저장 실패 롤백 확인
- 동일 사용자 동시 초기화에서 계정과 `OPENING_BALANCE`가 각각 하나만 생성됨을 확인
- MySQL 8.0에서 Flyway V1·V2 적용 및 재실행 0건 확인
- MySQL 인덱스·유니크·CHECK·외래키와 사용자별 계정·원장 합계 일치 확인

### 2단계 — 충전 상태와 내부 결제 어댑터

- [x] 충전 생성, 내부 승인, 실패, 취소, 환불 상태 전이를 구현한다.
- [x] `merchantOrderId`, `pgOrderId`, 멱등성 키와 결제 금액 검증을 구현한다.
- [x] `PaymentProvider` 인터페이스와 권한이 제한된 `INTERNAL` 구현체만 추가한다.
- [x] 충전 완료와 `CHARGE` 원장·잔액 증가를 같은 트랜잭션으로 처리한다.
- [x] 충전 환불을 `REFUND` 반대 거래로 처리한다.
- [x] 콜백 inbox와 중복 이벤트 처리 계약을 구현한다.

검증:

- [x] 모든 허용·금지 상태 전이
- [x] 중복 승인·취소·환불
- [x] 같은 멱등성 키의 동일 요청과 다른 요청
- [x] 금액 불일치와 PG 주문번호 중복
- [x] 원장 저장 실패 시 충전 상태·잔액 롤백
- [x] 실패 이벤트 재처리와 최대 재시도

검증 결과 (2026-07-31):

- H2 충전 상태 단위·트랜잭션·동시성 테스트 통과
- 동일 요청 멱등 재호출과 다른 요청 충돌, 금액·권한 검증 확인
- 승인·환불 원장과 잔액 원자 처리 및 원장 실패 전체 롤백 확인
- 동일 충전 동시 승인에서 잔액과 `CHARGE` 원장이 한 번만 증가함을 확인
- 콜백 중복 처리 방지, 실패 재처리와 최대 5회 제한 확인
- 기존 경매·주문·포인트 원장 테스트를 포함한 백엔드 전체 테스트 통과

### 3단계 — 입찰 예치와 차액 재입찰

- [x] 기존 입찰 트랜잭션에 포인트 계정과 hold를 연결한다.
- [x] 신규 최고 입찰 금액 전액을 예치한다.
- [x] 같은 최고 입찰자의 재입찰은 차액만 추가 예치한다.
- [x] 새 사용자가 최고가가 되면 이전 사용자의 예치를 해제한다.
- [x] 패찰 후 재입찰은 새 hold로 전액 예치한다.
- [x] 잔액 부족 오류를 추가한다.
- [x] 입찰·예치·이전 예치 해제·현재가 변경을 한 트랜잭션으로 처리한다.
- [x] 작품 취소 시 활성 예치를 같은 트랜잭션에서 해제한다.

검증:

- [x] 신규 입찰, 동일 사용자 증액과 패찰 후 재입찰
- [x] 잔액 전액 입찰과 1포인트 부족
- [x] 입찰·원장·계정 저장 실패별 전체 롤백
- [x] 취소와 입찰 경합
- [x] 동시 입찰의 잔액 초과·이중 예치·이중 해제 방지
- [x] 서로 다른 작품에서 같은 사용자 잔액을 동시에 사용하는 경우

검증 결과 (2026-07-31):

- H2 입찰 예치 단위·트랜잭션·동시성 테스트 통과
- 신규 전액 예치, 동일 사용자 차액 증액, 패찰 해제 후 새 hold 재예치 확인
- 잔액 전액 입찰과 1포인트 부족 거절, 입찰·계정·hold·원장 실패 전체 롤백 확인
- 작품 취소와 입찰 경합 시 활성 예치가 정확히 한 번 해제됨을 확인
- 동일 작품 동시 입찰과 서로 다른 작품의 동일 계정 동시 사용에서 초과 예치 방지 확인
- 기존 충전·경매·주문·포인트 원장 테스트를 포함한 백엔드 전체 테스트 통과

### 4단계 — 자동 마감과 낙찰 주문 결제

- [x] 마감 시 최고 입찰과 활성 hold 정합성을 검증한다.
- [x] 낙찰 주문에 결제수단 `INTERNAL_POINT`를 저장한다.
- [x] `COMMIT`과 주문 `PAID`를 원자 처리한다.
- [x] 결제 전 포기와 기한 만료 시 예치를 해제한다.
- [x] 결제 후 환불 시 반대 거래로 포인트를 복원한다.
- [x] 마감·결제·만료·환불 재실행을 멱등 처리한다.
- [x] 기존 `OrderPaymentService` 경계를 포인트 결제 유스케이스에 맞게 조정한다.

검증:

- [x] 마감과 입찰 경합 및 마감 재실행
- [x] 결제와 만료·포기 경합
- [x] 결제 동시 호출의 중복 확정 방지
- [x] 환불 동시 호출의 중복 복원 방지
- [x] 주문 상태 변경 실패 시 포인트 변경 롤백
- [x] 포인트 처리 실패 시 주문 `PAYMENT_PENDING` 유지

검증 결과 (2026-07-31):

- H2 자동 마감·주문 결제 단위·트랜잭션·동시성 테스트 통과
- 최고 입찰·현재가·활성 예치의 사용자·금액·입찰 정합성 검증 확인
- 주문 `PAID`·`COMMIT`·예치 확정과 포기·만료 예치 해제의 원자 처리 확인
- 결제·만료·포기 경합과 마감 재실행에서 중복 상태·원장 변경 방지 확인
- 환불 반대 거래와 사용 가능 잔액 복원, 재실행 멱등성 확인
- 주문 생성·상태 또는 포인트 원장 처리 실패 시 전체 롤백 확인
- 기존 포인트·입찰·경매·주문 테스트를 포함한 백엔드 전체 196건 통과

### 5단계 — API와 프론트 연동

- [x] 프로필·마이페이지에 사용 가능 포인트와 예치 포인트를 구분해 제공한다.
- [x] 포인트 거래 내역과 충전 내역·상태 조회 API를 추가한다.
- [x] 입찰 API에 잔액 부족과 예치 결과를 필요한 범위만 제공한다.
- [x] 주문 결제 API에 `Idempotency-Key`를 적용한다.
- [x] `Charge.jsx` 목업을 제거하고 내부 충전 흐름에 연결한다.
- [x] 주문 화면 결제 버튼을 실제 포인트 결제 API에 연결한다.
- [x] 중복 클릭을 방지하고 `409` 발생 시 최신 잔액·주문을 다시 조회한다.
- [x] 네이버페이·토스페이는 실제 동작 가능한 결제수단처럼 노출하지 않는다.

검증:

- [x] 잔액·예치액·원장 목록 표시
- [x] 충전과 주문 결제 성공·실패 화면
- [x] 중복 클릭과 네트워크 재시도
- [x] 인증·소유권·내부 승인 권한
- [x] 프론트 단위 테스트, ESLint와 프로덕션 빌드

검증 결과 (2026-07-31):

- 포인트 조회·충전, 입찰 잔액 응답, 주문 결제 멱등 헤더의 API 테스트 통과
- 주문 결제 구매자 소유권 검증과 포인트 충전 멱등 재호출·충돌 검증 확인
- 기존 H2 단위·트랜잭션·동시성 테스트를 포함한 백엔드 전체 201건 중 198건 통과, 3건 제외
- 프론트 단위 테스트 9건, ESLint와 Vite 프로덕션 빌드 통과

### 6단계 — 전체 동시성·MySQL 검증과 문서 완료 처리

- [ ] H2 단위·트랜잭션·동시성 테스트 전체를 실행한다.
- [ ] 실제 MySQL에서 제약·잠금·교착·격리 수준을 검증한다.
- [ ] 다수 작품·사용자의 동시 입찰 부하 시나리오를 검증한다.
- [ ] 계정 잔액과 원장 합계 정합성 검사를 구현한다.
- [ ] 실패 콜백·미처리 이벤트·잔액 불일치 운영 조회 방법을 문서화한다.
- [ ] 기존 입찰·마감·주문·프론트 회귀 테스트를 실행한다.
- [ ] `User.reserve` 잔존 참조를 확인하고 호환 필드 제거 여부를 결정한다.
- [ ] 완료 계획과 테스트 결과를 `PLAN_DONE.md`로 옮긴다.
- [ ] `PLAN.md`에서 완료 내용을 제거하고 최상단 문서 관리 공지만 남긴다.

완료 기준:

- [ ] 잔액 초과 사용이 발생하지 않는다.
- [ ] 같은 금액이 둘 이상의 경매에 중복 예치되지 않는다.
- [ ] 패찰·취소 시 예치가 정확히 한 번 해제된다.
- [ ] 낙찰 결제가 정확히 한 번 확정된다.
- [ ] 주문 `PAID`와 `COMMIT`이 항상 함께 존재한다.
- [ ] 환불은 원거래를 보존하고 정확히 하나의 반대 거래를 가진다.
- [ ] 멱등 재호출과 중복 콜백이 잔액을 추가 변경하지 않는다.
- [ ] 계정 잔액과 원장 합계가 일치한다.

## 6. 변경 예정 파일

### 문서·설정

- `PLAN.md`
- `PLAN_DONE.md`
- `BACKLOG.md`
- `backend/build.gradle`
- `backend/src/main/resources/application.properties.example`
- 신규 DB 마이그레이션 파일

### 기존 백엔드

- `entity/User.java`
- `entity/Art.java`
- `entity/Order.java`
- `service/UserService.java`
- `service/BidService.java`
- `service/ArtService.java`
- `service/AuctionCloseService.java`
- `service/OrderService.java`
- `service/OrderPaymentService.java`
- `service/OrderStateService.java`
- `service/OrderExpirationService.java`
- 관련 repository, controller, DTO, 예외 처리와 보안 설정

### 신규 백엔드

- `PointAccount`, `PointTransaction`, `PointHold`, `PointCharge`
- 포인트 거래·예치·충전·결제·콜백 관련 enum
- 포인트 account·transaction·hold·charge repository
- 포인트 원장·예치·충전·주문 결제 service
- 결제 provider 인터페이스와 내부 구현
- 포인트 조회·충전·결제 controller와 DTO
- 콜백 inbox 엔티티·저장소·재처리 서비스

### 프론트엔드

- `frontend/src/api/userApi.js` 또는 신규 `pointApi.js`
- `frontend/src/api/orderApi.js`
- `frontend/src/pages/MyPage/Charge.jsx`
- `frontend/src/pages/MyPage/MyPage.jsx`
- `frontend/src/pages/MyPage/OrderStatus.jsx`
- 입찰 폼, 관련 상태·오류 변환 파일, CSS와 테스트

### 테스트

- 기존 입찰 서비스·트랜잭션·동시성 테스트 확장
- 기존 마감 서비스·동시성·주문 롤백 테스트 확장
- 기존 주문 상태·동시성·만료 테스트 확장
- 기존 작품 취소 트랜잭션·동시성 테스트 확장
- 신규 포인트 엔티티·저장소·서비스·멱등성·동시성 테스트
- 신규 MySQL 스키마·원장 정합성 테스트
- 신규 프론트 충전·잔액·주문 결제 테스트

## 7. `BACKLOG.md` 후속 범위

- 네이버페이 실제 결제 준비·승인·취소와 웹훅 서명 검증
- 토스페이 실제 결제 요청·승인·취소와 웹훅 서명 검증
- 외부 PG 실패 inbox 운영 재처리와 PG 정산·포인트 원장 대사
- 내부 포인트와 외부 결제를 조합하는 복합 결제
- 카드·계좌이체와 결제수단별 부분 취소·부분 환불
