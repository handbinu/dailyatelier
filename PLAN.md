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

## 입찰 잠금 timeout 및 오류 계약 통일

### 배경과 목표

- 실제 MySQL 강제 잠금 실험에서 `ArtRepository.findByIdForUpdate`의 `jakarta.persistence.lock.timeout=3000` 힌트가 적용되지 않아 InnoDB 기본값에 가까운 약 50초 후 `409 BID_CONFLICT`가 반환되었다.
- `PointAccountRepository.findByUserIdForUpdate`는 같은 조건에서 약 50초 후 timeout 되었지만 예외가 입찰 충돌로 변환되지 않아 `500 INTERNAL_SERVER_ERROR`가 반환되었다.
- 입찰 트랜잭션이 획득하는 `art`와 `point_account` 행 잠금에 실제 MySQL에서 동작하는 동일한 요청 단위 timeout 정책을 적용하고, 잠금 timeout 응답을 공통 `ApiErrorResponseDto`의 `409 BID_CONFLICT`로 통일한다.
- timeout 시 입찰, 활성 예치, 계정 잔액과 포인트 원장이 모두 롤백되어 부분 데이터가 남지 않음을 실제 MySQL 통합 테스트로 보장한다.
- 비관적 락과 현재 잠금 획득 순서는 유지한다. 전역 `innodb_lock_wait_timeout`, Hikari 풀 크기, 입찰 외 도메인의 잠금 정책은 변경하지 않는다.

### timeout 구현 방식 비교와 결정 원칙

1. **JPA `jakarta.persistence.lock.timeout` 힌트 유지·보완**
   - 장점: 표준 API이고 Repository 선언만으로 의도가 드러난다.
   - 한계: 현재 MySQL/드라이버/Hibernate 조합의 실측에서 3초 값이 적용되지 않았다. 같은 힌트만 다른 위치에 반복하는 방식은 해결 근거가 없으므로 권장하지 않는다.
2. **트랜잭션 시작 후 MySQL 세션의 `innodb_lock_wait_timeout`을 요청 단위로 설정**
   - 장점: InnoDB 행 잠금 대기에 직접 적용되며 전역 DB 설정을 변경하지 않는다.
   - 한계: 초 단위이고 Hikari 커넥션 재사용 시 세션 값이 남을 수 있다. 같은 트랜잭션·물리 커넥션에서 잠금 전에 설정하고, 기존 세션 값을 보관한 뒤 성공·도메인 실패·timeout을 포함한 모든 종료 경로에서 같은 커넥션으로 원복해야 한다.
3. **JDBC statement/query timeout 또는 Spring 트랜잭션 timeout**
   - 장점: 애플리케이션 계층에서 시간 제한을 표현할 수 있다.
   - 한계: 행 잠금만을 대상으로 하지 않으며 드라이버의 취소 시점과 예외 유형이 달라질 수 있다. 요청 단위 세션 설정을 안전하게 적용할 수 없는 경우에만 대안으로 비교하며, 전체 입찰 처리 시간을 제한하는 별도 요구가 없는 현재 범위에서는 1차 선택으로 사용하지 않는다.

#### 권장 방식

- 요청/트랜잭션 단위 `SET SESSION innodb_lock_wait_timeout`을 우선 구현안으로 사용한다. `art`와 `point_account` 잠금 전에 같은 트랜잭션·물리 커넥션에서 동일한 timeout 값을 설정한다.
- 설정 전 세션 값을 보관하고 트랜잭션 종료 전 같은 커넥션에서 원복한다. 성공·도메인 실패·timeout 모든 종료 경로와 Hikari가 해당 커넥션을 후속 요청에 재사용하는 경우까지 값이 누출되지 않음을 검증한다.
- 세션 설정의 안전한 적용·원복을 보장할 수 없는 경우에만 JDBC statement/query timeout 또는 Spring 트랜잭션 timeout을 대안으로 비교한다. JPA 힌트의 추정 동작만으로 선택하지 않는다.

#### 목표 timeout 값 결정 지점

- 구현 시작 전에 API 응답 목표와 상위 HTTP 클라이언트·프록시 timeout보다 충분히 짧은 값을 사용자 승인으로 확정한다. 기존 선언값인 3초는 비교 기준일 뿐 자동 확정값으로 간주하지 않는다.
- 확정값은 입찰 잠금 전용 상수 또는 입찰 전용 설정 한 곳에서 관리하고 `art`와 `point_account`에 동일하게 적용한다. 기본 설정 파일에 임의의 값을 추가하거나 운영 전역 DB 값을 변경하지 않는다.
- 통합 테스트 허용 오차는 로컬 스케줄링 지연을 고려하되, InnoDB 기본 약 50초로 회귀하는 경우 반드시 실패하도록 상·하한을 둔다.

### 1단계 — MySQL 세션 timeout 방식 검증과 구현안 확정

#### 구현·조사

- 격리된 부하 테스트용 MySQL 스키마와 기존 강제 잠금 도구를 사용해 요청 단위 `SET SESSION innodb_lock_wait_timeout`의 실제 대기 시간, 발생 SQLSTATE/MySQL 오류 코드, Spring/JPA 변환 예외를 기록한다.
- 세션 설정이 `art`와 `point_account`의 `SELECT ... FOR UPDATE`에 동일하게 적용되는지 확인한다.
- 설정 전 세션 값을 보관하고 성공·도메인 실패·timeout 모든 종료 경로에서 같은 물리 커넥션으로 원복되는지 확인한다.
- timeout 전후와 Hikari가 같은 커넥션을 재사용한 후속 트랜잭션에서 `@@session.innodb_lock_wait_timeout`을 조회하여 세션 값이 누출되지 않음을 검증한다.
- 세션 방식의 안전한 적용·원복이 불가능한 경우에만 JDBC/query/transaction timeout의 동작과 예외 계약을 비교하고, production에는 검증된 한 방식만 적용한다.

#### 완료 기준

- 선택한 방식이 두 잠금 모두에서 확정된 목표 시간 범위 안에 timeout 된다.
- timeout 예외의 실제 타입과 원인 체인이 확인되어 필요한 예외 변환 범위를 정할 수 있다.
- 전역 `@@global.innodb_lock_wait_timeout` 값이 변경되지 않고, 성공·실패·timeout 이후 재사용된 Hikari 커넥션의 세션 값이 설정 전 값으로 원복된다.

#### 자동 검증·실측

- 별도 MySQL 세션으로 fixture 행을 잠근 뒤 HTTP 입찰 요청의 시작·종료 시각 측정
- timeout 설정·원복 전후와 Hikari 커넥션 재사용 후 global/session 변수 조회
- 애플리케이션 로그의 SQLSTATE/MySQL 오류 코드와 최종 HTTP 응답 대조

### 2단계 — 입찰 잠금 timeout과 오류 계약 통일

#### production 코드 변경 범위

- 입찰 처리에서 사용하는 `ArtRepository.findByIdForUpdate`와 `PointAccountRepository.findByUserIdForUpdate`의 잠금 조회에 1단계에서 확정한 동일 timeout 정책을 적용한다.
- `BidService`의 `art` 잠금에 한정된 예외 변환을 입찰 트랜잭션 내 두 잠금에 공통 적용한다. 확인된 Spring/JPA timeout·비관적 잠금 예외만 잡고, 무관한 데이터 접근 오류를 `BID_CONFLICT`로 숨기지 않는다.
- 잠금 timeout은 기존 공통 오류 DTO 형식으로 HTTP 409, 코드 `BID_CONFLICT`를 반환한다. 메시지와 path 등 기존 계약은 불필요하게 변경하지 않는다.
- timeout 예외가 트랜잭션 경계 밖에서 소비되어 커밋되지 않도록 rollback-only 동작을 보장한다. 필요하면 변환 위치 또는 예외 타입의 rollback 규칙만 최소 변경한다.
- 입찰 외 서비스가 공유 Repository 메서드를 사용하는지 확인하고, 영향이 있다면 입찰 전용 잠금 메서드를 분리한다. 주문·충전 등 다른 도메인의 timeout 정책 변경은 범위에서 제외한다.

#### 완료 기준

- `art`와 `point_account` 강제 잠금 모두 확정된 목표 시간 범위 내에 공통 `409 BID_CONFLICT`로 종료된다.
- 예상하지 못한 DB 오류는 기존 공통 500 계약을 유지하며 잠금 충돌로 오분류되지 않는다.
- production 변경은 timeout 적용과 입찰 예외 변환에 필요한 Repository/Service 및 관련 설정 범위로 제한된다.

#### 자동 검증

- Service 단위 테스트: 두 잠금 지점의 확인된 timeout 예외가 `BID_CONFLICT`로 변환되는지 검증
- 예외 구분 테스트: 일반 데이터 접근 예외가 `BID_CONFLICT`로 변환되지 않는지 검증
- API 테스트: 공통 `ApiErrorResponseDto`의 `status`, `code`, `message`, `path` 계약 검증

### 3단계 — 실제 MySQL 강제 잠금·롤백 통합 검증

#### 통합 테스트

- 격리된 MySQL 스키마에 Flyway migration과 결정적 fixture를 적용하고, 별도 JDBC 세션으로 대상 행을 `SELECT ... FOR UPDATE`하여 잠금을 유지한다.
- **`art` 잠금 timeout:** HTTP 입찰 요청이 목표 시간 범위 내 `409 BID_CONFLICT`로 종료되는지 확인한다.
- **`point_account` 잠금 timeout:** `art` 잠금과 가격 검증을 통과한 요청이 계정 잠금에서 목표 시간 범위 내 동일한 409 계약으로 종료되는지 확인한다.
- 각 실패 전후에 입찰 수, `art.current_price`, `active_point_hold_id`, HELD 예치, 계정 available/held 잔액, 포인트 원장 건수·합계를 snapshot으로 비교한다.
- 잠금 세션 해제 후 같은 사용자와 작품으로 정상 입찰을 수행하여 커넥션·트랜잭션 상태가 오염되지 않았고 후속 처리가 가능한지 확인한다.

#### 완료 기준

- 두 강제 잠금 테스트가 HTTP 상태·도메인 코드·실제 대기 시간 상한을 모두 검증한다.
- timeout 요청으로 생성되거나 변경된 입찰·예치·계정 잔액·원장·작품 현재가가 없으며 모든 검증 SQL이 통과한다.
- 잠금 해제 후 정상 입찰이 성공하고 정합성 검증도 통과한다.
- 테스트는 허용된 전용 스키마 표식이 없으면 실행을 중단하며 다른 DB 데이터를 변경하지 않는다.

### 4단계 — 기존 입찰 회귀 검증과 완료 정리

#### 회귀 범위

- 기존 입찰 Service/API 테스트로 정상 201, 낮은 입찰가, 자기 작품 입찰, 잔액 부족, 작품 상태·기간 검증과 기존 `BID_CONFLICT` 계약을 확인한다.
- 기존 동시성 테스트로 동일 작품·동일 계정 경합에서 가격, 활성 예치, 잔액과 원장 정합성이 유지되는지 확인한다.
- 부하 smoke를 실행하여 정상 경쟁의 `BID_TOO_LOW`가 실패로 오분류되지 않고 예상하지 못한 500·client timeout이 없는지 확인한다. 성능 튜닝이나 Hikari 풀 비교 실험은 수행하지 않는다.
- `git diff --check`와 변경 파일 검토로 production 범위, 테스트, 계획 완료 문서 외 변경 및 `BACKLOG.md` 비변경을 확인한다.

#### 완료 기준

- 관련 단위·API·동시성·실제 MySQL 강제 잠금 테스트가 모두 통과한다.
- 기존 정상 입찰과 도메인 오류 계약에 회귀가 없고, 두 잠금 timeout만 목표 시간과 409 계약으로 통일된다.
- 결과에 목표 timeout 값, 실제 측정 범위, 선택한 구현 방식, 롤백 검증과 환경 한계를 기록한다.
- 완료 시 본 계획을 `PLAN.md`에서 제거하고 `PLAN_DONE.md`에는 이후에도 필요한 timeout 계약·검증 결과만 1~3개 bullet로 남긴다. `BACKLOG.md`는 수정하지 않는다.

### 커밋 경계

사용자 승인 전에는 아래 커밋을 만들지 않는다. 승인 후에도 각 단계 검증이 통과한 경우에만 분리한다.

1. **계획 문서 커밋** — `PLAN.md`만 포함한다.
   - 예정 메시지: `chore: 입찰 잠금 timeout 개선 계획 추가`
2. **production 수정 커밋** — 입찰용 MySQL timeout 적용, 두 잠금의 예외 계약 통일, 단위·API 테스트만 포함한다. 부하 도구와 문서 변경은 섞지 않는다.
   - 예정 메시지: `fix(backend): 입찰 잠금 timeout 계약 통일`
3. **MySQL 통합 검증 커밋** — 강제 잠금, rollback·후속 정상 요청 검증에 필요한 통합 테스트 및 테스트 전용 지원 코드만 포함한다.
   - 예정 메시지: `test(backend): 입찰 잠금 timeout 통합 검증 추가`
4. **완료 문서 커밋** — 구현·검증 완료 후 `PLAN.md`의 본 계획 제거와 `PLAN_DONE.md` 핵심 결과만 포함한다.
   - 예정 메시지: `chore: 입찰 잠금 timeout 개선 결과 정리`
