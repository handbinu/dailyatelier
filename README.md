# DailyAtelier

DailyAtelier는 신인 작가와 컬렉터를 연결하는 포트폴리오용 온라인 미술 경매
서비스입니다. 작가는 작품과 경매를 등록하고, 구매자는 내부 데모 포인트로 입찰한 뒤
낙찰 주문의 배송·구매확정·리뷰 흐름을 체험할 수 있습니다.

> 이 저장소는 포트폴리오 데모입니다. 포인트 충전과 주문 결제는 실제 금융 거래가
> 아니며, 외부 PG와 실제 배송 조회는 연동되어 있지 않습니다.

## 프로젝트 구성

| 경로 | 구성 | 기본 주소 |
| --- | --- | --- |
| `backend` | Java 17, Spring Boot 3.5, Spring Security, JPA, Flyway, MySQL | `http://localhost:8080` |
| `frontend` | React 19, Vite 8, React Router, Axios | `http://localhost:5173` |

주요 기능은 회원·작가 가입과 JWT 로그인, 작품 등록·검색·찜, 포인트 충전과 입찰,
경매 자동 마감, 낙찰 주문·배송·환불, 리뷰, 1:1 문의입니다. 공지·이벤트·작가별 작품·
작가소개·개발자 소개·경매 진행방법·고객센터·Q&A 일부 화면은 준비 중 안내만 제공합니다.

## 사전 준비

- JDK 17
- MySQL 8
- Node.js `^20.19.0 || >=22.12.0`
- npm
- 작품 및 문의 첨부 이미지 업로드용 Cloudinary 계정

Gradle과 npm 의존성을 처음 설치할 때는 인터넷 연결이 필요합니다.

## 로컬 환경설정

### 1. MySQL 스키마 준비

비어 있는 스키마를 만듭니다. 애플리케이션을 처음 실행하면 Flyway가 V1~V6 migration을
순서대로 적용하고, Hibernate는 `ddl-auto=validate`로 결과만 검증합니다.

```sql
CREATE DATABASE dailyatelier
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

기존 V0~V6 개발 DB를 자동 변환하거나 삭제하지 않습니다. 기존 DB 처리와 격리 MySQL
검증 원칙은 [backend/FLYWAY.md](backend/FLYWAY.md)를 참고하세요.

### 2. 백엔드 환경변수

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
| `CORS_ALLOWED_ORIGINS` | API 접근을 허용할 프론트 origin 목록 |

`daliyatelier.env`는 현재 프로젝트가 실제로 사용하는 파일명입니다. 철자를 바꾸지 마세요.
실제 파일과 비밀값은 Git에서 제외되며, 예제 값은 개발 또는 운영 비밀값으로 사용하면
안 됩니다.

여러 프론트 주소를 허용할 때는 공백 없이 쉼표로 구분합니다.

```properties
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://portfolio.example.com
```

credentials를 사용하는 CORS 설정이므로 `*`는 허용되지 않습니다.

### 3. 프론트 환경변수

환경파일 없이 실행하면 API 주소는 `http://localhost:8080`입니다. 다른 API를 사용할
때만 예제 파일을 복사해 값을 바꿉니다.

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

## 실행

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

로그에서 Flyway V1~V6 적용과 Hibernate 검증, `Tomcat started on port 8080`을 확인합니다.
기본 API 확인:

```bash
curl http://localhost:8080/api/arts
```

터미널 2 — 프론트:

```bash
cd frontend
npm ci
npm run dev
```

브라우저에서 `http://localhost:5173`에 접속합니다.

## 테스트와 빌드

### 백엔드

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

일반 전체 테스트는 H2를 사용합니다. 실제 MySQL 스키마 검증 테스트는 기본적으로 skip되며,
기존 데이터가 없는 격리 스키마와 환경변수를 준비한 뒤에만 실행합니다.

Windows PowerShell 예시:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/dailyatelier_empty_test"
$env:DB_USERNAME = "your-test-user"
$env:DB_PASSWORD = "your-test-password"
$env:DAILYATELIER_EMPTY_DB_TEST = "true"
.\gradlew.bat test --tests com.dailyatelier.dailyatelier.repository.FlywayEmptyDatabaseMySqlTest
```

포인트 테이블 제약조건 검증은 다른 빈 스키마를 준비하고
`DAILYATELIER_MYSQL_SCHEMA_TEST=true`로
`com.dailyatelier.dailyatelier.repository.PointLedgerMySqlSchemaTest`를 실행합니다. 두 테스트는
스키마나 테이블을 자동 삭제하지 않습니다.

### 프론트

```bash
cd frontend
npm ci
npm test
npm run test:component
npm run lint
npm run build
```

- `npm test`: 순수 JavaScript 유틸리티 테스트
- `npm run test:component`: Vitest 컴포넌트 테스트
- `npm run lint`: 전체 ESLint 검사
- `npm run build`: production 번들 생성

## 대표 데모 시나리오

공개 저장소에는 고정 데모 계정이나 seed 데이터가 없습니다. 빈 DB에서는 일반 회원과 작가
회원을 직접 가입해 서로 다른 브라우저 세션에서 진행하세요.

아래 내용은 현재 UI, API 상태 전이, 자동 테스트와 기존 기능별 QA 완료 기록을 기준으로
정리한 기능 가이드입니다. 이번 실행·문서 작업에서 경매부터 리뷰까지 전체 거래 흐름을
새로 회귀 검증했다는 의미는 아닙니다.

### 통합 happy path

1. **구매자** — 일반 회원으로 가입·로그인하고 마이페이지의 적립금 충전에서 금액을
   선택한 뒤 `{금액}P 데모 충전`을 누릅니다.
2. **작가** — 작가 회원으로 가입·로그인하고 `작품 등록`에서 이미지와 경매 정보를 입력한
   뒤 `작품 등록하기`를 누릅니다. 이미지는 Cloudinary에 실제 업로드됩니다.
3. **구매자** — 작품 상세의 `입찰하기`에서 다음 입찰 가능 금액 이상을 입력해 입찰합니다.
   입찰 금액만큼 내부 포인트가 예치됩니다.
4. **시스템** — 종료 시각이 지난 경매를 기본 10초 간격으로 마감합니다. 최고 입찰이 있으면
   낙찰 처리하고 결제 기한 24시간의 주문을 생성합니다.
5. **구매자** — `마이페이지 → 주문 조회`에서 `배송지 확정` 후 `포인트 결제`를 누릅니다.
   외부 결제가 아니라 입찰 때 예치된 내부 포인트가 확정됩니다.
6. **작가** — `마이페이지 → 판매 주문 관리`에서 `배송 준비`를 누른 뒤, 택배사와
   송장번호를 입력하고 `발송 처리`를 누릅니다.
7. **구매자** — 작품을 실제로 전달받았다고 가정한 뒤 주문 조회에서 `배송 완료`,
   이어서 `구매 확정`을 누릅니다. 작가의 발송 전에는 이 단계가 제공되지 않습니다.
8. **구매자** — 확정 주문의 `리뷰 쓰기·수정`에서 별점과 내용을 입력하고 `리뷰 등록`을
   누릅니다.
9. **작가** — `마이페이지 → 작품 리뷰`의 `내 작품 리뷰 보기`에서 받은 리뷰를 확인합니다.

### 선택 데모: 환불

환불은 위 happy path와 별도 주문으로 진행합니다. 결제 후 구매확정 전 상태에서 구매자가
`환불 요청`으로 사유를 입력하면, 작가는 판매 주문 관리에서 `환불 승인` 또는 `환불 거절`을
선택합니다. 승인 시 내부 포인트가 반환되고 주문은 환불 완료로 끝납니다. 거절 시 주문은
기존 상태로 계속 진행됩니다. 환불 주문은 구매확정·리뷰 시나리오와 섞지 않습니다.

## 데모 범위와 한계

- `데모 포인트` 충전 provider는 `INTERNAL`이며 요청 즉시 내부 승인·원장에 반영됩니다.
- 네이버페이, 토스페이, 카드 등 외부 PG 승인·취소·웹훅은 연동되어 있지 않습니다.
- 낙찰 주문의 `포인트 결제`는 실제 결제가 아니라 예치된 내부 포인트 확정입니다.
- 택배사와 송장번호는 작가가 직접 입력하며 실제 배송사 위치 조회 API는 없습니다.
- Cloudinary 작품·문의 이미지 업로드는 실제 외부 서비스 연동입니다.
- 관리자 1:1 문의 답변 기능은 관리자 계정을 별도로 준비해야 하며 일반 회원가입으로
  관리자 권한을 만들 수 없습니다.
- 로컬 전용 QA 계정 정보는 Git에 포함되지 않으며 README의 실행 전제도 아닙니다.

## 배포 환경 체크리스트

- 운영 MySQL과 Flyway migration 적용 권한을 준비합니다.
- `DB_*`, `JWT_SECRET`, `CLOUDINARY_*`를 배포 환경의 비밀 저장소에서 주입합니다.
- `CORS_ALLOWED_ORIGINS`에 실제 프론트 origin만 명시합니다. `*`는 사용하지 않습니다.
- `VITE_API_BASE_URL`을 실제 API 주소로 설정한 뒤 프론트를 다시 빌드합니다.
- 브라우저 preflight와 `GET /api/arts` 응답을 확인합니다.
- 실제 `.env`, `daliyatelier.env`, 비밀번호와 API key가 Git에 포함되지 않았는지 확인합니다.

Docker, CI/CD, AWS 등 특정 클라우드 배포 구성과 실제 PG 연동은 이 문서의 범위에
포함하지 않습니다.
