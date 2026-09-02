# DailyAtelier 개발 계획

> [!IMPORTANT]
> 완료된 계획과 검증 결과는 `PLAN_DONE.md`에서 관리한다.
> 작업 완료 시 계획과 검증 결과를 `PLAN_DONE.md`에 먼저 보존한 뒤 `PLAN.md`에서 해당 계획을 제거한다.
> 완료 문서 커밋 전에는 두 파일을 대조해 완료 기록 누락과 중복이 없는지 확인한다.
> 미완료·후속 작업은 `BACKLOG.md`에서 관리한다.
> 새 작업 계획은 사용자 요청 범위가 확정된 뒤 이 문서에 추가한다.

---

## 프로젝트 실행·테스트·데모 문서와 배포 환경 설정

### 범위 판단: GO

- README와 예제 환경변수, 프론트 API 주소 및 백엔드 CORS 설정으로 한정하면 현재
  포트폴리오 기능을 처음 보는 사람도 재현할 수 있는 적절한 크기의 작업이다.
- Docker, CI/CD, AWS·호스팅 서비스별 배포 절차, 실제 PG 연동, 운영 DB 마이그레이션,
  데모 데이터 seed 구현은 포함하지 않는다.
- 현재 로컬 전용 테스트 계정은 `_local/test-accounts.md`와 기존 DB 상태에 의존하므로
  공개 README에서 항상 사용 가능한 데모 계정으로 안내하지 않는다. 새 환경에서도
  재현 가능한 일반·작가 회원가입 기반 시나리오를 기본으로 문서화한다.
- 백엔드 설정의 재현성과 배포 origin 허용은 이번 목표에 직접 필요하므로 범위에
  포함하되, 도메인 코드와 API 계약은 변경하지 않는다.

### 조사 결과

#### 문서와 저장소 구조

- 루트 `README.md`는 없다. `frontend/README.md`는 React/Vite 기본 템플릿 안내만 있어
  DailyAtelier의 구조, 기능, 실행법 또는 테스트 방법을 설명하지 않는다.
- 프로젝트는 `backend`의 Spring Boot API와 `frontend`의 React/Vite SPA로 나뉜다.
- `backend/FLYWAY.md`에는 빈 MySQL 스키마와 조건부 MySQL 통합 테스트 절차가 있으나,
  프로젝트 전체를 시작하는 순서와 프론트 실행법은 없다.

#### 백엔드 실행과 환경설정

- Java toolchain은 17, Gradle Wrapper는 8.14.4, Spring Boot는 3.5.11이다. 기본 실행
  명령은 Windows `backend\gradlew.bat bootRun`, macOS/Linux `./backend/gradlew bootRun`이며
  별도 포트 설정이 없어 기본 `8080`을 사용한다.
- 실행에는 MySQL 스키마와 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`,
  `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`이 필요하다.
  Cloudinary는 작품 및 문의 첨부 이미지 업로드에 실제로 사용되는 외부 서비스다.
- `backend/src/main/resources/application.properties`는 위 환경변수를 참조하고
  `daliyatelier.env`를 선택적으로 import하지만, 두 파일 모두 `.gitignore` 대상이다.
  로컬에는 `backend/daliyatelier.env`가 있으나 비밀값을 포함한 로컬 전용 파일이고,
  환경변수 값 예제 파일은 없다. 추적 중인 `application.properties.example`은 있지만
  새 클론에서 Spring Boot가 자동으로 읽는 실제 공통 설정 파일은 없다.
- `daliyatelier.env`는 추측한 이름이 아니라 로컬 실제 파일, 메인·테스트 설정의 import와
  `.gitignore`에서 현재 사용 중인 철자임을 재확인했다. 이번 작업에서는 호환성을 깨는
  rename을 하지 않고 이 이름을 그대로 유지한다. `dailyatelier.env`로의 교정은 로컬 비밀
  파일과 모든 참조의 명시적 마이그레이션이 필요한 별도 작업이며 암묵적으로 수행하지 않는다.
- Flyway V1~V6가 빈 스키마를 구성한다. `backend/FLYWAY.md`와 추적 중인
  `application.properties.example`은 `spring.jpa.hibernate.ddl-auto=validate`를 기준으로
  하지만, 실제 로컬 실행 파일은 `update`여서 문서·예제와 실행 정책이 일치하지 않는다.
- CORS 허용 origin은 `SecurityConfig.java`에 `http://localhost:5173` 하나로 고정되어
  배포 프론트 주소를 환경변수로 바꿀 수 없다.

#### 프론트 실행과 환경설정

- `package-lock.json`이 있으며 설치는 `npm ci`, 개발 실행은 `npm run dev`이고 Vite 기본
  주소는 `http://localhost:5173`이다. Vite 8 기준 Node 요구사항은
  `^20.19.0 || >=22.12.0`이다.
- 공통 Axios 인스턴스인 `frontend/src/api/authApi.js`의 `baseURL`이
  `http://localhost:8080`으로 하드코딩되어 있고 나머지 API 모듈이 이 인스턴스를
  공유한다. `import.meta.env` 또는 `VITE_*` 환경변수는 사용하지 않는다.
- `frontend/.env`는 ignore되어 있으나 현재 파일은 없고, `.env.example`도 없다.

#### 현재 테스트 명령과 기준 결과

- 백엔드: `backend`에서 `./gradlew test` 또는 Windows의 `gradlew.bat test`.
  현재 319개가 통과하고 조건부 MySQL 통합 테스트 7개가 skip된다. 최초 실행 시 Gradle
  배포판과 의존성 다운로드를 위한 네트워크가 필요하다.
- 프론트 유틸리티: `frontend`에서 `npm test` — 현재 19개 통과.
- 프론트 컴포넌트: `npm run test:component` — 현재 129개 통과.
- 정적 검사와 번들: `npm run lint`, `npm run build` — 현재 모두 통과.
- 로컬 MySQL을 사용하는 실제 백엔드 시작과 프론트-백엔드 브라우저 연결은 이번 조사에서
  변경 없이 실행하지 않았으며, 구현 단계의 최종 검증 항목으로 남긴다.

#### 문서화 가능한 데모 범위와 오해 방지 사항

- 대표 happy path는 역할별로 독립된 흐름이 아니라 구매자·작가·시스템이 하나의 작품과
  주문 상태를 순서대로 넘기는 통합 흐름이다. 구매자는 회원가입·데모 충전·입찰·배송지
  확정·포인트 결제·수령 후 배송 완료 처리·구매확정·리뷰를, 작가는 회원가입·Cloudinary
  이미지 기반 작품/경매 등록·주문 준비·발송·받은 리뷰 확인을 수행한다.
- 경매는 종료 시각 이후 기본 10초 간격 스케줄러가 마감하며, 낙찰 시 주문을 만든다.
  전체 흐름은 작가와 구매자 두 세션, 짧은 종료 시각의 작품, 스케줄러 대기가 필요하다.
- 환불은 구매확정·리뷰와 양립하는 동일 선형 흐름이 아니다. 별도 주문에서 구매확정 전에
  구매자가 환불을 요청하고 작가가 승인 또는 거절하는 선택 데모로 분리해야 한다.
- 포인트 충전 provider는 `INTERNAL`이며 요청 즉시 내부 승인·원장 반영된다. 네이버페이,
  토스페이, 카드 등 외부 PG 승인·취소·웹훅은 연동되지 않았고 BACKLOG 범위다.
- 주문 결제도 외부 결제가 아니라 입찰 때 예치된 내부 데모 포인트를 확정하는 방식이다.
  택배사·송장번호는 작가가 직접 입력하며 실제 배송 추적 API는 없다.
- 로컬 테스트 계정에는 작가와 일반 회원이 있으나 Git에서 제외되고 현재 DB에 의존한다.
  관리자 계정은 일반 회원가입으로 만들 수 없으므로 대표 공개 데모 흐름에서는 제외하고,
  1:1 문의 관리자 답변은 별도 사전 준비가 필요한 기능으로 표시한다.
- 공지·이벤트·작가별 작품·작가소개·개발자 소개·경매 진행방법·고객센터·Q&A 일부
  라우트는 `PreparingPage`이므로 구현된 동적 기능처럼 소개하지 않는다.

### 구현 계획

#### 1단계. 재현 가능한 백엔드·프론트 환경설정

- [x] `.gitignore`에서 비밀값이 없는
  `backend/src/main/resources/application.properties`만 추적 가능하게 하고,
  `backend/.env`, `backend/daliyatelier.env`, `frontend/.env`는 계속 제외한다.
- [x] `backend/src/main/resources/application.properties`를 비밀값 없는 공통 설정으로
  추적한다. 기존 DB/JWT/Cloudinary 환경변수 계약을 유지하고, 로컬 환경 파일 import와
  배포 환경변수 주입이 모두 가능하게 한다.
- [x] 같은 파일의 Hibernate 설정을 `update`에서 `validate`로 변경한다. 이는 표현이나
  문서만 맞추는 변경이 아니라, Hibernate의 실행 중 스키마 자동 변경을 중단하고 Flyway를
  유일한 스키마 변경 기준으로 확정하는 실행 정책 변경이다. 기존 개발 DB는 자동 수정·삭제하지
  않으며, 불일치 시 `FLYWAY.md`의 재생성 정책을 안내한다.
- [x] `backend/daliyatelier.env.example`을 추가해 필요한 키, 로컬 MySQL URL 예시,
  충분히 긴 JWT 예시값, Cloudinary placeholder, CORS origin 예시만 제공한다. 실제 키와
  로컬 계정 정보는 포함하지 않는다. 현재 실제 파일명 철자를 그대로 따르며
  `dailyatelier.env`로 rename하거나 이중 파일명을 추가하지 않는다.
- [x] `SecurityConfig.java`가 환경변수 기반 허용 origin 목록을 사용하게 하고 기본값은
  `http://localhost:5173`으로 유지한다. 콤마로 구분한 복수 origin을 허용하되 wildcard와
  credentials 조합은 허용하지 않는다.
- [x] `frontend/src/api/authApi.js`의 Axios `baseURL`을
  `VITE_API_BASE_URL`에서 읽고, 값이 없을 때만 기존 `http://localhost:8080`을 사용한다.
  모든 API 함수가 계속 하나의 공통 인스턴스를 사용하도록 유지한다.
- [x] `frontend/.env.example`에 `VITE_API_BASE_URL=http://localhost:8080`을 제공하고,
  Vite 환경변수는 build 시점 값이라는 점과 배포별 값 설정 방법을 README에 연결한다.

#### 2단계. 루트 실행·테스트·데모 문서 작성

- [x] 새 `README.md`에 프로젝트 목적, 포트폴리오 데모라는 성격, 백엔드·프론트 기술
  스택, 디렉터리 구조와 구현 기능/준비 중 기능을 구분해 작성한다.
- [x] 사전 요구사항(Java 17, MySQL, Node 버전, Cloudinary), 빈 DB 생성, 예제 env 복사와
  값 입력, 백엔드 실행 후 프론트 실행 순서를 Windows와 macOS/Linux 명령으로 제공한다.
- [x] 로컬 기본 주소와 배포 시 `VITE_API_BASE_URL`, `CORS_ALLOWED_ORIGINS`를 함께
  변경해야 한다는 체크리스트를 제공하고 비밀값을 커밋하지 않도록 경고한다.
- [x] 백엔드 전체 테스트와 조건부 MySQL 스키마 테스트, 프론트 유틸리티·컴포넌트
  테스트, lint, production build 명령을 목적과 작업 디렉터리까지 포함해 정리한다.
- [x] 대표 데모는 다음 하나의 통합 happy path로 작성하고 각 단계 앞에 수행 주체를 표시한다.
  구매자 회원가입·데모 충전 → 작가 회원가입·작품/경매 등록 → 구매자 작품 탐색·입찰 →
  시스템 경매 마감·낙찰 주문 생성 → 구매자 배송지 확정·포인트 결제 → 작가 주문 준비·발송 →
  구매자 실제 수령 후 배송 완료 처리·구매확정·리뷰 → 작가 받은 리뷰 확인.
- [x] 통합 happy path에는 구매자·작가의 서로 다른 브라우저 세션, 경매 종료 후 최대 약 10초,
  Cloudinary 연결과 충분한 구매자 데모 포인트가 필요함을 명시한다. 작가의 발송 전에는
  구매자가 배송 완료나 구매확정을 진행하지 않도록 상태 전이 순서를 분명히 한다.
- [x] 환불은 별도 "선택 데모"로 분리한다. 별도 주문을 결제한 뒤 구매확정 전에 구매자가
  환불 사유를 입력해 요청하고, 작가가 승인하면 내부 포인트 환불로 종료되며 거절하면 기존
  주문 상태로 계속 진행되는 분기를 설명한다. 이 주문은 happy path의 리뷰 단계와 섞지 않는다.
- [x] 별도 "데모 한계" 절에서 내부 포인트 충전·결제, 실제 PG 미연동, 배송 추적 미연동,
  로컬 DB 의존 계정, 준비 중 화면을 명시해 포트폴리오 기능을 과장하지 않는다.
- [x] `frontend/README.md`의 Vite 템플릿 문구는 제거하고 프론트 단독 실행·환경변수 요약과
  루트 README 링크만 남겨 서로 다른 실행 안내가 생기지 않게 한다.

#### 3단계. 문서 명령과 환경 전환 검증

- [x] 비밀값이 없는 새 클론과 같은 조건을 만들 수 있는 별도 임시 작업 디렉터리에서
  README 순서대로 예제 파일을 복사하고, 실제 로컬 비밀값은 Git 밖에서 주입한다.
- [x] 빈 MySQL 스키마에서 백엔드를 시작해 V1~V6 Flyway 적용, Hibernate validate,
  `GET /api/arts` 응답과 `http://localhost:5173` preflight 성공을 확인한다. 기존 DB를
  삭제하거나 재사용해 검증하지 않는다.
- [x] `npm ci`, `npm run dev`로 로컬 기본 API 연결을 확인하고, 다른
  `VITE_API_BASE_URL`로 `npm run build`한 산출물이 지정 주소를 사용하는지 확인한다.
- [x] 다른 `CORS_ALLOWED_ORIGINS` 값으로 백엔드를 시작해 해당 origin은 허용되고 임의
  origin은 거부되는지 확인한다.
- [x] 백엔드 `gradlew.bat test`/`./gradlew test`, 프론트 `npm test`,
  `npm run test:component`, `npm run lint`, `npm run build`를 README 그대로 실행한다.
  조건부 MySQL 테스트는 격리된 빈 스키마가 준비된 경우에만 `FLYWAY.md`의 두 플래그로
  각각 실행하고 일반 전체 테스트에서 skip되는 사실을 문서와 대조한다.
- [x] 프론트 개발 서버에서 공개 작품 API가 정상 연결되는지 확인하고, 현재 사용 가능한
  로컬 QA 계정이 있거나 신규 회원가입이 안전하게 가능한 경우에만 회원가입·로그인 정도의
  간단한 프론트-백엔드 smoke test를 수행한다. QA 계정·작품·주문 fixture를 별도로 만들지 않는다.
- [x] 경매 → 주문 → 배송 → 구매확정 → 리뷰 전체 흐름은 이번 문서 작업에서 다시 실행하지
  않는다. README의 버튼명과 상태 전이 주체는 현재 UI·API 코드, 자동 테스트와 기존 기능별
  QA 완료 기록을 대조해 작성하고, 이번 작업에서 전체 거래 흐름을 재검증했다고 기록하지 않는다.
- [x] `git diff --check`, `git status --short`, `git check-ignore`로 예제 파일과 공통 설정만
  추적되고 `.env`, `daliyatelier.env`, 빌드 산출물과 로컬 계정 문서는 제외되는지 확인한다.

### 변경 예정 파일과 이유

- `README.md` (신규): 프로젝트 구조, 준비·실행·테스트, 환경 전환, 역할별 데모와 한계의
  단일 진입점.
- `frontend/README.md`: 기본 Vite 문구를 프로젝트용 짧은 안내와 루트 문서 링크로 교체.
- `.gitignore`: 비밀값 없는 백엔드 공통 설정은 추적하고 실제 환경파일은 계속 보호.
- `backend/src/main/resources/application.properties`: 새 클론에서도 재현되는 공통 설정,
  Hibernate 자동 스키마 변경을 중단하고 Flyway를 변경 기준으로 확정하는 실행 정책과
  배포 CORS 환경변수 연결.
- `backend/daliyatelier.env.example` (신규): 백엔드 필수 환경변수 이름과 안전한 예시 제공.
- `backend/src/main/java/com/dailyatelier/dailyatelier/config/SecurityConfig.java`: 배포 프론트
  origin을 코드 수정 없이 설정.
- `frontend/.env.example` (신규): 프론트 API base URL 예시 제공.
- `frontend/src/api/authApi.js`: Axios base URL을 Vite 환경변수로 전환.
- `BACKLOG.md`: `npm ci`에서 확인된 프론트 의존성 취약점의 영향 범위와 안전한 업데이트를
  별도 작업에서 검토하도록 사용자 승인에 따라 후보를 추가.
- 위 파일 외 도메인, API, 화면, 테스트 데이터, `PLAN_DONE.md`는 수정하지
  않는다. 설정 검증에 자동 테스트 보강이 꼭 필요하다고 구현 중 확인되면 파일을 먼저
  추가하지 않고 변경 이유와 대상 테스트 파일을 보고해 승인을 받는다.

### 완료 조건

- [x] 저장소를 처음 받은 사람이 루트 README만으로 요구 버전, 필요한 외부 서비스와
  환경변수, DB 준비, 백엔드·프론트 실행 순서와 접속 주소를 이해할 수 있다.
- [x] 소스 수정 없이 프론트 API base URL과 백엔드 허용 origin을 로컬·배포 값으로
  각각 바꿀 수 있고, 미설정 시 현재 로컬 주소가 유지된다.
- [x] 예제 환경파일에는 모든 필수 키가 있으나 실제 DB 비밀번호, JWT secret,
  Cloudinary key, 데모 계정 비밀번호는 없다.
- [x] 새 빈 MySQL에서 V1~V6가 순서대로 적용되고 `ddl-auto=validate` 상태로 실제 백엔드가
  기동되어 API에 응답한다. 이 검증 없이는 실행 정책 변경을 완료로 판단하지 않는다.
- [x] 프론트가 로컬 기본값과 별도 API 주소 양쪽에서 빌드·연결된다.
- [x] README에 적힌 전체 자동 검증 명령이 통과하며, MySQL 조건부 테스트의 준비 조건과
  기본 skip 여부가 정확히 설명된다.
- [x] 구매자·작가·시스템의 수행 주체가 표시된 통합 happy path와 별도 환불 선택 시나리오가
  현재 기능 계약에 맞게 안내되며, 전체 거래 흐름을 이번 작업에서 재검증했다는 표현이 없다.
- [x] 브라우저 smoke test는 공개 API 연결과 가능한 범위의 회원가입·로그인까지만 확인하고,
  로컬 QA 데이터 부족으로 생략한 항목이 있으면 검증 결과에 그 사유를 명시한다.
- [x] 내부 데모 포인트, 실제 PG·배송 추적 미연동, Cloudinary 실제 연동, 준비 중 화면이
  명확히 구분된다.
- [x] 계획에 없는 기능 구현·리팩토링, 인프라 도입, 비밀값 또는 로컬 전용 계정 정보의
  Git 추적이 없다.

### 검증 결과

- 별도 빈 MySQL 스키마에서 Flyway V1~V6 적용과 Hibernate `ddl-auto=validate` 상태의
  실제 백엔드 기동을 확인했고, `GET /api/arts`가 200으로 응답했다. 조건부 MySQL 스키마
  테스트 2종도 각각 격리 스키마에서 통과했으며 검증 스키마는 확인 후 삭제했다.
- 기본 `http://localhost:5173`과 별도 허용 origin의 CORS preflight는 성공했고, 허용하지
  않은 origin은 403으로 거부됐다.
- `VITE_API_BASE_URL` 미설정 기본값과 별도 주소를 사용한 production build 산출물을 각각
  확인했다. `npm ci` 후 개발 서버의 공개 작품 화면에서도 로컬 API 데이터가 렌더링됐다.
- 백엔드 전체 테스트 319개가 통과하고 조건부 7개가 예정대로 skip됐다. 프론트 유틸리티
  19개, 컴포넌트 129개, lint와 production build가 모두 통과했다.
- 로컬 QA 문서의 기존 계정은 현재 DB에서 사용할 수 없어 회원가입·로그인 smoke test는
  생략했다. 새 계정이나 작품 fixture는 만들지 않았고 전체 거래 흐름도 다시 실행하지 않았다.
- 추적 파일로 만든 별도 임시 checkout에서 공통 설정과 두 예제 env가 존재하고 실제 env와
  로컬 계정 문서가 포함되지 않으며, 예제 파일 복사 절차가 성공하는 것을 확인했다.
- `npm ci`가 보고한 취약점 13건은 이번 완료 조건과 분리했고 수정하지 않았다. production/dev
  영향과 안전한 업데이트 가능성은 `BACKLOG.md`의 별도 후보로 남겼다.

### 예상 커밋 경계

1. `chore: 실행 환경변수와 배포 주소 설정 정리`
   - 공통 백엔드 설정 추적, 예제 env, CORS와 프론트 API base URL 환경변수화.
2. `chore: 프로젝트 실행과 역할별 데모 문서 작성`
   - 루트·프론트 README 작성과 검증 결과 반영.
