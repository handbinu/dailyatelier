# DailyAtelier 개발 계획

> [!IMPORTANT]
> `PLAN.md`는 현재 승인된 작업 계획을 관리한다.
>
> 작업 완료 후 핵심 구현·검증 결과를 `PLAN_DONE.md`에 기록한 다음 해당 계획을 `PLAN.md`에서 제거한다.
>
> 현재 범위에 포함되지 않은 미완료·후속 작업은 `BACKLOG.md`에서 관리한다.
>
> 새 계획이나 범위 변경은 사용자 승인 후 반영한다.

## 무료 포트폴리오 배포 검증

### 목표

- Cloudflare Worker Static Assets Free + Cloudtype Free + Aiven MySQL Free + 기존 Cloudinary로 공개 URL과 핵심 흐름을 검증한다.
- 상시 운영·유료 전환·PG 연동은 범위에서 제외한다.

### 전제와 완료 기준

- Cloudtype Free의 하루 1회 중지와 Aiven Free의 유휴 power-off 가능성을 수용한다.
- 개인 개발 DB와 분리된 Aiven 전용 demo 스키마만 사용한다.
- Worker Static Assets URL에서 작품 조회, 로그인, 데모 포인트 충전, 입찰, 기존 Cloudinary 이미지 표시를 확인한다.
- 실제 비밀값은 각 서비스의 환경변수/secret에만 넣고 저장소에는 추가하지 않는다.
- 관리자 기능은 유지하되, 관리자 계정은 개발자만 사용하는 비공개 시연 계정으로 둔다. 공개 방문자에게 관리자 권한이나 읽기 전용 관리자 체험 계정은 제공하지 않는다.

### 작업 순서

1. **Aiven demo DB 준비**
   - Aiven Free MySQL을 생성하고 전용 DB·사용자를 발급한다.
   - TLS JDBC 연결을 먼저 검증한 뒤, Cloudtype backend에 DB URL·계정·비밀번호를 secret으로 등록한다.
   - Flyway V1~V11 적용과 Hibernate validate 성공을 로그로 확인한다.

2. **Cloudtype backend 배포**
   - `backend` 하위 경로, Java 17/Spring Boot 설정, Gradle build와 실행 명령, 포트 `8080`을 지정한다.
   - DB/JWT/Cloudinary/CORS 및 `TZ=Asia/Seoul`을 환경변수로 설정한다.
   - 공개 demo DB의 최초 fixture 준비에만 `local-demo` profile과 enabled flag를 켜고, 완료 후에는 `DAILYATELIER_LOCAL_DEMO_ENABLED=false`로 유지한다. 재기동으로 fixture를 재생성하지 않는다.
   - API·Flyway·Cloudinary 설정 오류가 없는지 실행 로그를 확인한다.

3. **공개 데모 안전 경계 구현과 backend 검증**
   - **관리자·데모 계정**: 공개 소스에 있던 관리자 비밀번호를 Aiven 전용 demo DB에서만 랜덤 비밀번호 해시로 교체한다. 관리자 로그인 정보는 Git, README, 공개 프론트 환경변수, 화면 안내에 노출하지 않는다.
   - 관리자 비밀번호 교체는 기존 `authVersion`을 증가시키는 사용자 비밀번호 변경 경로와 동일한 원칙으로 수행한다. 교체 전 발급된 관리자 JWT가 이후 `401`이 되는 것을 확인한다. 작품·주문·포인트·문의 fixture는 변경하지 않는다.
   - 공개 구매자·작가 계정은 핵심 시연용으로 유지하되, 공개 demo 전용 비밀번호를 환경변수로 분리하고 관리자 계정과 공유하지 않는다. 로컬 fixture 설정은 Git 제외 `_local`에만 둔다.
   - **Cloudinary**: 공개 demo에서는 신규 작가 가입을 제한하고, 서명 발급은 환경변수에 지정한 demo 작가만 허용한다. 사용자별 서명 발급 횟수를 제한하되, 기존 `arts/<사용자>` 폴더 제한과 작품 등록 흐름은 유지한다.
   - 업로드 기능은 허용 demo 작가로 수동 확인하고, Cloudinary 사용량 알림과 전용 폴더를 설정한다. 콘텐츠 심사·분산 rate limit·사용자별 저장량 제한은 BACKLOG로 분리한다.
   - **공유 데이터**: 공개 계정의 입찰·주문·포인트 변경은 권한 우회가 아닌 공유 demo 데이터의 운영상 변경으로 분류한다. 자동 초기화·재시딩은 만들지 않고, 공유 상태 안내와 필요 시 승인된 수동 복구 절차만 둔다.
   - Cloudtype에는 `CORS_ALLOWED_ORIGINS`로 확정된 Cloudflare Worker origin과 로컬 origin만 명시하고, `SPRING_JPA_SHOW_SQL=false`를 적용한다. Aiven JDBC TLS `sslMode=REQUIRED`와 Hibernate validate는 유지한다.
   - 공개 프론트 배포 전에 backend 자동 테스트와 Cloudtype 환경변수 검토를 완료한다. 이미 공개된 Cloudtype API는 이 보안 조치와 환경변수 반영이 끝날 때까지 프론트에서 연결·안내하지 않는다.

4. **Cloudflare Worker Static Assets frontend 배포와 연결 확인**
   - 보안 조치가 적용된 backend 검증 이후에만 `frontend`를 Worker build root로 지정한다.
   - `frontend/wrangler.jsonc`의 `assets.directory=./dist`, `not_found_handling=single-page-application`을 사용하고 `npm run build`를 수행한다.
   - build 환경변수 `VITE_API_BASE_URL`에 Cloudtype HTTPS API URL을 넣고, backend CORS에는 확정된 Worker origin만 등록한다.
   - HTTPS Worker URL에서 API 호출이 CORS 오류 없이 동작하는지와 SPA 직접 경로 fallback을 확인한다.
   - 작품 탐색 → 공개 구매자 로그인 → 데모 충전 → 입찰 → 기존 이미지 표시와 비공개 관리자 로그인 → 관리자 기능 시연을 수동 확인한다. Cloudtype/Aiven free-tier 중단 뒤 재기동 절차도 확인한다.

### 변경 예상 범위와 승인 경계

- backend의 demo 계정 비밀번호 설정, 공개 demo 작가 가입·Cloudinary 서명 제한, 관련 단위·API 테스트와 배포 문서만 최소 변경한다. 기존 Flyway migration과 주요 거래·작품 등록 계약은 변경하지 않는다.
- Aiven 계정 비밀번호 해시 교체와 Cloudtype 환경변수 입력은 구현 검토·별도 승인 후에만 수행하며, 대상은 `dailyatelier_portfolio_demo`로 한정한다.
- 기존 로컬 개발·테스트 환경은 공개 demo feature property가 명시적으로 활성화된 Cloudtype 배포에서만 제한되도록 유지한다.
- Cloudtype이 `8080` 고정 포트를 허용하면 애플리케이션 코드는 변경하지 않는다. Aiven CA 검증 또는 SPA fallback이 현 설정만으로 불가능한 경우에만, 원인과 최소 변경안을 별도로 승인받는다.

### BACKLOG 후보

- 로그인·회원가입 API의 전역 rate limit과 계정 생성 spam 방지
- Cloudinary 콘텐츠 심사, 사용자별 저장량 제한, 분산 rate limit
- 공유 demo DB의 수동 스냅샷·복구 문서화와 방문자별 임시 계정
- Cloudtype 로그 보존·알림 정책 및 프론트 번들 분할 경고 개선
