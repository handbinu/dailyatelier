# 기술 설계와 검증

[프로젝트 소개](../README.md) · [로컬 실행](../README.md#6-로컬-실행)

## 서비스 구조

```text
React Client
    │ REST API
    ▼
Spring Boot
├─ 인증·작품·입찰·주문·포인트
├─ 경매 마감 Scheduler
└─ Cloudinary Cleanup Worker
    │                       │
    ▼                       ▼
MySQL 8                  Cloudinary
거래·원장·outbox          이미지 저장소
```

입찰 금액은 즉시 소비하지 않고 예치합니다. 최고 입찰자가 바뀌면 이전 예치를 해제하고, 낙찰 주문 결제 시 확정합니다. 경매 취소, 주문 만료와 환불 시에는 포인트를 반환합니다.

## 동시 입찰과 자동 마감

같은 작품에 입찰이 동시에 도착하거나 입찰과 자동 마감이 겹치면 최고가, 포인트 예치와 낙찰 결과가 서로 달라질 수 있습니다.

- 작품 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 상태·마감 시각·현재가를 다시 검사합니다.
- 입찰 이력, 작품 현재가와 포인트 예치를 하나의 트랜잭션에서 변경합니다.
- 마감도 작품별 잠금을 사용하고, 낙찰 확정과 주문 생성을 같은 트랜잭션으로 묶습니다.
- 서버 시작 시 종료된 미처리 경매를 다시 조회해 누락된 마감을 처리합니다.
- MySQL `innodb_lock_wait_timeout`을 입찰 요청 단위로 3초 적용한 뒤 기존 세션 값으로 복원합니다.

관련 코드: [입찰 트랜잭션](../backend/src/main/java/com/dailyatelier/dailyatelier/service/BidService.java) · [마감·주문 생성](../backend/src/main/java/com/dailyatelier/dailyatelier/service/AuctionCloseService.java).

실제 MySQL에서 동시 입찰, 입찰·마감 경합, 자동 마감과 장기 락 timeout을 검증했습니다. timeout 시 입찰·현재가·예치·잔액·원장이 함께 rollback되고, 재사용된 Hikari 커넥션에 세션 설정이 남지 않는지 확인했습니다.

배포 환경과 동일한 MySQL 설정에서의 재검증과 21억 원 경계 입찰 이후 UI 검증은 남아 있습니다.

대표 증빙: [동시 입찰 테스트](../backend/src/test/java/com/dailyatelier/dailyatelier/service/BidServiceConcurrencyTest.java) · [주문 생성 실패 rollback 테스트](../backend/src/test/java/com/dailyatelier/dailyatelier/service/AuctionCloseOrderRollbackTest.java).

## 포인트 예치와 불변 원장

잔액만 갱신하면 입찰, 패찰, 결제와 환불의 변경 근거를 추적하기 어렵고 중복 요청이나 부분 실패에 취약합니다.

- `PointAccount`를 현재 잔액의 기준으로, `@Immutable`인 `PointTransaction`을 변경 이력으로 사용합니다.
- `PointHold`로 입찰 금액의 예치·확정·해제를 관리합니다.
- 잔액과 원장을 같은 트랜잭션에서 변경하고 환불은 결제 거래를 참조하는 반대 거래로 기록합니다.
- 계정 행 잠금과 멱등성 키·unique 제약으로 동시 변경과 중복 반영을 제어합니다.
- callback inbox는 `(provider, provider_event_id)` unique 제약으로 중복 이벤트 반영을 막고, 처리 실패를 별도 트랜잭션에 기록해 제한적으로 재처리합니다.

관련 코드: [정합성 검사](../backend/src/main/java/com/dailyatelier/dailyatelier/service/PointLedgerConsistencyService.java).

동시 충전·포인트 변경, 입찰 예치 교체, 낙찰 결제, 만료와 환불을 테스트했습니다. 계정과 원장 합계뿐 아니라 활성 예치, 주문과 충전 상태 사이의 의미적 불일치도 검사합니다.

대표 증빙: [의미적 불일치 테스트](../backend/src/test/java/com/dailyatelier/dailyatelier/service/PointLedgerConsistencyServiceTest.java) · [MySQL 계정 잠금·deadlock rollback 테스트](../backend/src/test/java/com/dailyatelier/dailyatelier/repository/PointLedgerMySqlConcurrencyTest.java).

현재 충전 provider는 권한이 제한된 `INTERNAL` 데모 구현입니다. 실제 PG 승인·취소·서명 검증, 운영 정산과 동시 최초 callback unique 충돌은 검증하지 않았습니다.

## Cloudinary cleanup

DB의 이미지 참조 변경과 Cloudinary 삭제는 하나의 ACID 트랜잭션으로 묶을 수 없습니다. DB commit 전에 삭제하면 rollback 시 정상 이미지를 잃고, 삭제하지 않으면 교체된 이미지가 외부 저장소에 남습니다.

- 작품·프로필에 URL과 case-sensitive `public_id`를 함께 저장합니다.
- 이미지 교체와 작품 물리 삭제 트랜잭션에서 cleanup outbox를 등록합니다.
- worker는 `FOR UPDATE SKIP LOCKED`로 작업을 claim하고 외부 삭제는 DB 트랜잭션 밖에서 실행합니다.
- lease와 claim token fencing으로 만료된 worker의 늦은 결과 반영을 차단합니다.
- 삭제 전 참조를 다시 확인하고, 일시적 오류는 지수 backoff로 제한 재시도합니다.

동시 claim, lease 만료 후 재선점, stale claim 무시, 참조 재검사와 최대 재시도 초과를 테스트했습니다. 실제 MySQL에서 `public_id`의 case-sensitive 비교·유일성 제약과 `SKIP LOCKED` 동작도 확인했습니다.

[cleanup 트랜잭션 코드](../backend/src/main/java/com/dailyatelier/dailyatelier/service/CloudinaryCleanupTransactionService.java) · [worker 테스트](../backend/src/test/java/com/dailyatelier/dailyatelier/service/CloudinaryCleanupWorkerTest.java)

브라우저 업로드 후 작품 생성·수정 API에 도달하지 못한 이미지와 URL만 저장된 기존 데이터는 현재 자동 cleanup 대상이 아닙니다.

## JWT 인증과 현재 권한

JWT는 [발급 시](../backend/src/main/java/com/dailyatelier/dailyatelier/jwt/JwtTokenProvider.java) 포함한 `authVersion`을 [인증 필터](../backend/src/main/java/com/dailyatelier/dailyatelier/jwt/JwtAuthenticationFilter.java)에서 `users.auth_version`과 비교해 비밀번호·권한 변경 전 토큰을 무효화합니다. 인가에는 매 요청 DB에서 읽은 현재 역할을 사용합니다. [사용자 서비스](../backend/src/main/java/com/dailyatelier/dailyatelier/service/UserService.java)는 비밀번호 변경과 버전 증가를 사용자 행 잠금이 있는 하나의 트랜잭션으로 처리합니다.

[JWT MySQL 통합 테스트](../backend/src/test/java/com/dailyatelier/dailyatelier/jwt/JwtInvalidationMySqlIntegrationTest.java)는 변경 성공·실패 rollback·동시 변경과 권한 변경 뒤의 인증·인가 계약을 검사합니다.

MySQL 전용 테스트의 실행 조건은 [공통 검증 환경](#검증-환경과-ci)을 따릅니다.

## Flyway와 MySQL

기존 migration만으로 빈 MySQL에 전체 스키마를 만들 수 없었고, H2로는 MySQL의 락·DDL 동작을 충분히 확인하기 어려웠습니다.

- 운영·공유 DB가 없는 개발 단계라는 전제에서 `V1__create_baseline_schema.sql`을 현재 기준 스키마로 재구성했습니다.
- 이후 변경은 V2 이상으로 누적하고 Hibernate는 `ddl-auto=validate`로 결과만 검사합니다.
- DBMS별 동작은 격리된 MySQL 스키마에서 별도 검증합니다.

빈 MySQL 8.0에서 Flyway V1~V9, Hibernate validation, 재실행 migration 0건, 주요 제약, 락 timeout과 `SKIP LOCKED`를 확인했습니다.

이후 JWT 작업에서 빈 DB V1→V11 적용·Hibernate validation과 기존 DB V10→V11 적용을 검증했습니다([완료 기록](../PLAN_DONE.md#jwt-무효화와-현재-권한-재검증)). 현재 빈 DB 검증 코드는 [FlywayEmptyDatabaseMySqlTest](../backend/src/test/java/com/dailyatelier/dailyatelier/repository/FlywayEmptyDatabaseMySqlTest.java)에서 확인할 수 있습니다. 앞선 락·제약 검증 이력과 migration 확장 검증은 구분합니다.

기존 DB에도 미적용 migration은 실행됩니다. [Flyway 안내](../backend/FLYWAY.md)의 기존 DB 처리 원칙을 따릅니다.

## k6 부하 관찰

DB 락과 커넥션 대기가 입찰 지연에 미치는 영향을 살펴보기 위해 k6로 세 가지 경합 상황을 재현했습니다.

- 단일 작품에 입찰 집중
- 여러 작품에 입찰 분산
- 동일 포인트 계정에 요청 집중

**측정 결과:** Hikari 커넥션 풀 크기 10·20·30을 비교했지만, 처리량과 상위 백분위 응답 시간이 일관되게 개선되지 않아 기본값 10을 유지했습니다.

측정은 로컬 단일 장비에서 진행했습니다. 배포 환경에서의 반복 측정과 실제 데이터 기반 실행 계획 검증은 수행하지 않았습니다.

[부하 테스트 도구](../backend/load-test)

## UI 요청 처리와 API 오류 계약

비동기 UI에서는 이전 요청을 취소하고, 늦게 도착한 성공·실패·`finally`가 최신 화면을 덮지 않도록 처리했습니다. 로그인·회원가입·충전·문의 답변·작품 등록에는 동기 제출 guard를 적용했습니다.

API 오류는 다음 형식으로 통일했습니다.

```json
{
  "timestamp": "2026-09-19T12:00:00",
  "status": 409,
  "code": "BID_CONFLICT",
  "message": "입찰 처리 중 충돌이 발생했습니다.",
  "path": "/api/arts/1/bids"
}
```

## 검증 환경과 CI

| 검증 환경 | 주요 대상 |
| --- | --- |
| 단위·서비스 테스트 | 입찰 규칙, 상태 전이, 포인트 원장과 환불 |
| API·동시성 테스트 | 인증·인가, 오류 계약, 공유 자원 경합 |
| H2 통합 테스트 | 일반 서비스와 repository 회귀 |
| MySQL 통합 테스트 | Flyway, 제약, 락 timeout과 `SKIP LOCKED` |
| 프론트 테스트 | 요청 순서 역전, 중복 제출과 부분 성공 |
| k6 | 작품·계정 락과 커넥션 대기 관찰 |

**GitHub Actions:** [UI QA workflow](../.github/workflows/ui-qa.yml)에서 일반 백엔드·프론트 테스트, lint·build, MySQL `local-demo` 기반 Playwright UI QA를 실행하도록 구성했습니다. 스크린샷은 캡처·파일 확인 후 artifact로 보관하며, 자동 시각 평가는 수행하지 않습니다.

**별도 MySQL 검증:** `DAILYATELIER_MYSQL_SCHEMA_TEST`, `DAILYATELIER_JWT_MYSQL_TEST` 조건의 테스트는 해당 CI에서 실행하지 않습니다. 테스트 목적에 맞는 환경변수와 격리 DB가 필요합니다. 빈 DB migration 검증은 빈 스키마에서 실행하며, 다른 테스트는 각각의 DB 준비 조건을 따릅니다. 스키마를 자동 삭제하지 않습니다.

## 테스트와 빌드 실행

저장소 루트에서 해당 디렉터리로 이동해 실행합니다.

백엔드:

Windows PowerShell:

```powershell
cd backend
.\gradlew.bat test
```

macOS/Linux:

```bash
cd backend
./gradlew test
```

프론트:

```bash
cd frontend
npm ci
npm test
npm run test:component
npm run lint
npm run build
```

## 후속 작업

미구현 기능은 [BACKLOG](../BACKLOG.md)에서 관리합니다. 실제 PG 연동과 배송사 위치 조회는 현재 제공하지 않습니다. [부하 도구](../backend/load-test)와 [완료 기록](../PLAN_DONE.md)에서 관련 근거를 확인할 수 있습니다.
