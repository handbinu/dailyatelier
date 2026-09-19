# DailyAtelier

DailyAtelier는 신인 작가와 컬렉터를 연결하는 포트폴리오용 온라인 미술 경매 서비스입니다.

작품 탐색부터 입찰, 낙찰 주문, 배송, 구매확정과 리뷰까지 연결했으며, 특히 **동시 입찰과 포인트 거래의 정합성**을 백엔드 핵심 과제로 다뤘습니다.

- 비관적 락 기반 동시 입찰과 입찰·마감 경합 제어
- 예치·확정·해제·환불을 추적하는 불변 포인트 원장
- lease와 fencing을 적용한 Cloudinary cleanup
- 실제 MySQL 기반 Flyway·락 검증과 k6 부하 관찰

> 포인트 충전과 낙찰 결제는 실제 금융 거래가 아닌 내부 데모 포인트로 동작합니다. 외부 PG 승인·취소와 실제 배송사 위치 조회는 연동되어 있지 않습니다.

## 1. 서비스 흐름과 구현 범위

```text
작가 작품 등록
    ↓
구매자 입찰 · 포인트 예치
    ↓
자동 마감 · 낙찰 확정 · 주문 생성
    ↓
배송지 확정 · 예치 포인트 결제
    ↓
배송 · 구매확정 · 리뷰
```

입찰 금액은 즉시 소비하지 않고 예치합니다. 최고 입찰자가 바뀌면 이전 예치를 해제하고, 낙찰 주문 결제 시 확정합니다. 경매 취소, 주문 만료와 환불 시에는 포인트를 반환합니다.

주요 구현 범위:

- 회원·작가 가입, JWT 인증과 역할별 접근 제어
- 작품 등록·수정·삭제, 검색·필터·정렬·찜
- 입찰, 최소 입찰 증분, 자동 마감과 낙찰 결과
- 포인트 충전·예치·결제·해제·환불 원장
- 구매자·판매자 주문, 배송과 환불 상태 관리
- 주문 기반 리뷰, 1:1 문의, Cloudinary 이미지 업로드

## 2. 기술 스택과 구조

| 영역 | 기술 | 역할 |
| --- | --- | --- |
| Backend | Java 17, Spring Boot 3.5 | 거래 규칙, REST API, scheduler와 worker |
| Security | Spring Security, JWT | 인증과 역할별 권한 분리 |
| Persistence | Spring Data JPA, MySQL 8 | 거래 저장, 비관적 락과 DB 제약 |
| Migration | Flyway | 기준 스키마와 변경 이력 관리 |
| Frontend | React 19, Vite, React Router, Axios | 경매·주문 UI와 비동기 상태 관리 |
| External | Cloudinary | 작품·프로필·문의 이미지 저장 |
| Test | JUnit, Spring Test, Vitest, Testing Library, k6 | 계약·동시성·UI 상태·부하 검증 |

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

## 3. 핵심 문제 해결

### 3.1 동시 입찰과 자동 마감

**문제**

같은 작품에 입찰이 동시에 도착하거나 입찰과 자동 마감이 겹치면 최고가, 포인트 예치와 낙찰 결과가 서로 달라질 수 있습니다.

**선택**

- 작품 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 상태·마감 시각·현재가를 다시 검사합니다.
- 입찰 이력, 작품 현재가와 포인트 예치를 하나의 트랜잭션에서 변경합니다.
- 마감도 작품별 잠금을 사용하고, 낙찰 확정과 주문 생성을 같은 트랜잭션으로 묶습니다.
- 서버 시작 시 종료된 미처리 경매를 다시 조회해 누락된 마감을 처리합니다.
- MySQL `innodb_lock_wait_timeout`을 입찰 요청 단위로 3초 적용한 뒤 기존 세션 값으로 복원합니다.

**검증과 한계**

실제 MySQL에서 동시 입찰, 입찰·마감 경합, 자동 마감과 장기 락 timeout을 검증했습니다. timeout 시 입찰·현재가·예치·잔액·원장이 함께 rollback되고, 재사용된 Hikari 커넥션에 세션 설정이 남지 않는지 확인했습니다.

배포 환경과 동일한 MySQL 설정에서의 재검증과 21억 원 경계 입찰 이후 UI 검증은 남아 있습니다.

### 3.2 포인트 예치와 불변 원장

**문제**

잔액만 갱신하면 입찰, 패찰, 결제와 환불의 변경 근거를 추적하기 어렵고 중복 요청이나 부분 실패에 취약합니다.

**선택**

- `PointAccount`를 현재 잔액의 기준으로, `@Immutable`인 `PointTransaction`을 변경 이력으로 사용합니다.
- `PointHold`로 입찰 금액의 예치·확정·해제를 관리합니다.
- 잔액과 원장을 같은 트랜잭션에서 변경하고 환불은 결제 거래를 참조하는 반대 거래로 기록합니다.
- 계정 행 잠금과 멱등성 키·unique 제약으로 동시 변경과 중복 반영을 제어합니다.
- callback inbox는 `(provider, provider_event_id)` unique 제약으로 중복 이벤트 반영을 막고, 처리 실패를 별도 트랜잭션에 기록해 제한적으로 재처리합니다.

**검증과 한계**

동시 충전·포인트 변경, 입찰 예치 교체, 낙찰 결제, 만료와 환불을 테스트했습니다. 계정과 원장 합계뿐 아니라 활성 예치, 주문과 충전 상태 사이의 의미적 불일치도 검사합니다.

현재 충전 provider는 권한이 제한된 `INTERNAL` 데모 구현입니다. 실제 PG 승인·취소·서명 검증, 운영 정산과 동시 최초 callback unique 충돌은 검증하지 않았습니다.

### 3.3 Cloudinary cleanup

**문제**

DB의 이미지 참조 변경과 Cloudinary 삭제는 하나의 ACID 트랜잭션으로 묶을 수 없습니다. DB commit 전에 삭제하면 rollback 시 정상 이미지를 잃고, 삭제하지 않으면 교체된 이미지가 외부 저장소에 남습니다.

**선택**

- 작품·프로필에 URL과 case-sensitive `public_id`를 함께 저장합니다.
- 이미지 교체와 작품 물리 삭제 트랜잭션에서 cleanup outbox를 등록합니다.
- worker는 `FOR UPDATE SKIP LOCKED`로 작업을 claim하고 외부 삭제는 DB 트랜잭션 밖에서 실행합니다.
- lease와 claim token fencing으로 만료된 worker의 늦은 결과 반영을 차단합니다.
- 삭제 전 참조를 다시 확인하고, 일시적 오류는 지수 backoff로 제한 재시도합니다.

**검증과 한계**

동시 claim, lease 만료 후 재선점, stale claim 무시, 참조 재검사와 최대 재시도 초과를 테스트했습니다. 실제 MySQL에서 `public_id`의 case-sensitive 비교·유일성 제약과 `SKIP LOCKED` 동작도 확인했습니다.

브라우저 업로드 후 작품 생성·수정 API에 도달하지 못한 이미지와 URL만 저장된 기존 데이터는 현재 자동 cleanup 대상이 아닙니다.

### 3.4 MySQL/Flyway와 부하 검증

**문제**

기존 migration만으로 빈 MySQL에 전체 스키마를 만들 수 없었고, H2로는 MySQL의 락·DDL 동작을 충분히 확인하기 어려웠습니다. 입찰 지연도 DB 락과 커넥션 대기로 나눠 관찰할 필요가 있었습니다.

**선택**

- 운영·공유 DB가 없는 개발 단계라는 전제에서 `V1__create_baseline_schema.sql`을 현재 기준 스키마로 재구성했습니다.
- 이후 변경은 V2 이상으로 누적하고 Hibernate는 `ddl-auto=validate`로 결과만 검사합니다.
- DBMS별 동작은 격리된 MySQL 스키마에서 별도 검증합니다.
- k6로 단일 작품 집중, 작품 분산, 동일 포인트 계정 경합을 나눠 재현했습니다.

**검증과 한계**

빈 MySQL 8.0에서 Flyway V1~V9, Hibernate validation, 재실행 migration 0건, 주요 제약, 락 timeout과 `SKIP LOCKED`를 확인했습니다.

Hikari pool size 10·20·30 비교에서 처리량과 상위 백분위 응답 시간이 일관되게 개선되지 않아 기본값 10을 유지했습니다. 부하 결과는 로컬 단일 장비 측정이며, 배포 환경의 반복 측정과 실제 데이터 기반 실행 계획 검증은 남아 있습니다.

## 4. 품질 개선과 검증 전략

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

| 검증 환경 | 주요 대상 |
| --- | --- |
| 단위·서비스 테스트 | 입찰 규칙, 상태 전이, 포인트 원장과 환불 |
| API·동시성 테스트 | 인증·인가, 오류 계약, 공유 자원 경합 |
| H2 통합 테스트 | 일반 서비스와 repository 회귀 |
| MySQL 통합 테스트 | Flyway, 제약, 락 timeout과 `SKIP LOCKED` |
| 프론트 테스트 | 요청 순서 역전, 중복 제출과 부분 성공 |
| k6 | 작품·계정 락과 커넥션 대기 관찰 |

MySQL 전용 테스트는 명시적인 환경변수와 빈 격리 스키마가 준비된 경우에만 실행되며 스키마를 자동 삭제하지 않습니다.

## 5. 범위와 한계

현재 범위에 포함되지 않는 항목:

- 실제 PG 승인·취소, 웹훅 서명 검증과 운영 정산
- 실제 배송사 위치 추적
- 마감 임박 자동 연장과 입찰 경쟁 알림
- 차순위 입찰자 낙찰 승계
- 전문 검색 엔진과 검색 추천
- 배포 환경 기준의 반복 부하 및 실행 계획 검증

상세한 후속 작업은 [BACKLOG.md](BACKLOG.md)에서 관리합니다.

## 6. 로컬 실행

### 사전 준비

- JDK 17
- MySQL 8
- Node.js `^20.19.0 || >=22.12.0`
- npm
- 작품 및 문의 첨부 이미지 업로드용 Cloudinary 계정

### MySQL 준비

비어 있는 스키마를 만듭니다. 애플리케이션을 처음 실행하면 Flyway가 V1~V9 migration을
순서대로 적용하고, Hibernate는 `ddl-auto=validate`로 결과만 검증합니다.

```sql
CREATE DATABASE dailyatelier
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

기존 개발 DB를 자동 변환하거나 삭제하지 않습니다. 기존 DB 처리와 격리 MySQL
검증 원칙은 [backend/FLYWAY.md](backend/FLYWAY.md)를 참고하세요.

### 환경변수

예제 파일을 복사합니다.

Windows PowerShell:

```powershell
Copy-Item backend/daliyatelier.env.example backend/daliyatelier.env
```

macOS/Linux:

```bash
cp backend/daliyatelier.env.example backend/daliyatelier.env
```

`backend/daliyatelier.env`의 값을 로컬 환경에 맞게 바꿉니다.

| 변수 | 용도 |
| --- | --- |
| `DB_URL` | MySQL JDBC URL |
| `DB_USERNAME` | MySQL 사용자 |
| `DB_PASSWORD` | MySQL 비밀번호 |
| `JWT_SECRET` | JWT 서명 키. 임의의 충분히 긴 비밀값 사용 |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | Cloudinary API secret |
| `CORS_ALLOWED_ORIGINS` | 허용할 프론트 origin |

`daliyatelier.env`는 현재 프로젝트가 실제로 사용하는 파일명입니다. 철자를 바꾸지 마세요.
실제 파일과 비밀값은 Git에서 제외되며, 예제 값은 개발 또는 운영 비밀값으로 사용하면
안 됩니다.

여러 프론트 주소를 허용할 때는 공백 없이 쉼표로 구분합니다.

```properties
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://portfolio.example.com
```

credentials를 사용하는 CORS 설정이므로 `*`는 허용되지 않습니다.

프론트는 기본적으로 `http://localhost:8080`을 API 주소로 사용합니다. 다른 주소가 필요하면
예제 파일을 복사해 값을 바꿉니다.

Windows PowerShell:

```powershell
Copy-Item frontend/.env.example frontend/.env
```

macOS/Linux:

```bash
cp frontend/.env.example frontend/.env
```

```properties
VITE_API_BASE_URL=http://localhost:8080
```

`VITE_API_BASE_URL`은 Vite build 시점에 번들에 포함됩니다. 배포 주소를 바꾸면 프론트를
다시 빌드해야 하며, 백엔드의 `CORS_ALLOWED_ORIGINS`에도 프론트 origin을 함께 추가해야
합니다.

### 실행

두 터미널에서 백엔드와 프론트를 각각 실행합니다. 백엔드는 상대 경로 환경파일을 읽으므로
반드시 `backend` 디렉터리에서 시작합니다.

터미널 1 — 백엔드:

```powershell
cd backend
.\gradlew.bat bootRun
```

macOS/Linux에서는 다음 명령을 사용합니다.

```bash
cd backend
./gradlew bootRun
```

터미널 2 — 프론트:

```bash
cd frontend
npm ci
npm run dev
```

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- 기본 API: `GET http://localhost:8080/api/arts`

### 테스트와 빌드

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

## 7. 추가 문서

- [PLAN_DONE.md](PLAN_DONE.md): 완료 작업의 핵심 계약과 검증 한계
- [BACKLOG.md](BACKLOG.md): 미구현 기능과 후속 검증
- [backend/FLYWAY.md](backend/FLYWAY.md): 기준 스키마와 MySQL 검증 원칙
- [backend/load-test](backend/load-test): 입찰 부하 및 잠금 진단 도구
