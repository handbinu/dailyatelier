# DailyAtelier 개발 계획

> [!IMPORTANT]
> 완료된 계획과 검증 결과는 `PLAN_DONE.md`에서 관리한다.
> 미완료·후속 작업은 `BACKLOG.md`에서 관리한다.
> 새 작업 계획은 사용자 요청 범위가 확정된 뒤 이 문서에 추가한다.

## 작품 탐색 기능

### 목표와 범위

- 작품명·작가명 부분 검색, 작품 형태·카테고리·경매 상태 필터를 제공한다.
- 마감 임박순, 최신 등록순, 가격 낮은순, 가격 높은순 정렬과 페이지네이션을 제공한다.
- 검색 조건을 URL과 동기화하고 전체 초기화를 지원한다.
- 검색 및 경매 목록은 비로그인 사용자에게 공개하고 데스크톱·모바일 화면을 모두 구현한다.
- 헤더의 기존 작품 검색 계약인 `/search?q=...`를 유지한다.
- `/search`는 예정·진행·종료 작품 탐색, `/auction/*`는 진행 중 경매 목록으로 역할을 분리한다.

### 확정 계약

#### 작품 분류

- 작품 형태 `ArtFormat`: `DIGITAL`, `PHYSICAL`
- 작품 카테고리 `ArtCategory`: `OIL_PAINTING`, `WATERCOLOR`, `ACRYLIC_PAINTING`, `DRAWING`, `DIGITAL_ART`, `PRINTMAKING`, `PHOTOGRAPHY`, `SCULPTURE`, `CRAFT`, `MIXED_MEDIA`, `OTHER`
- `material`은 검색 카테고리로 사용하지 않고 작가가 입력하는 재료·매체·기법 설명으로 유지한다.
- 형태와 카테고리는 문자열 enum으로 저장하며 허용 조합은 서비스 정책과 테스트로 관리한다.
- 정확한 최신 등록순을 위해 작품에 `createdAt`을 추가한다.

#### 기존 개발 데이터

- 현재 Flyway 마이그레이션에는 작품 초기 데이터가 없으므로 별도 백필 로직을 구현하지 않는다.
- 개발용 로컬 DB의 수동 등록 작품은 초기화하고 `format`, `category`, `created_at`을 필수 컬럼으로 추가한다.
- V3 적용 전 기존 작품 레코드가 있는 개발 DB는 수동으로 초기화하며, Flyway 마이그레이션은 기존 데이터를 삭제하지 않고 스키마 변경만 수행한다.
- 부정확한 기본 분류나 `bidStartTime` 기반 등록 시각 대체값은 사용하지 않는다.

#### 공개 범위와 시간 기준

- `UPCOMING`: 활성 작품이고 현재 시각이 입찰 시작 시각보다 이전인 작품
- `ONGOING`: 활성 작품이고 `bidStartTime <= now < closingTime`인 작품
- `ENDED`: 판매·유찰 처리된 작품과 스케줄러 처리 전이라도 `closingTime <= now`인 활성 작품
- 취소 작품은 공개 탐색 결과에서 제외한다.
- 모든 시간 판정은 서버에 주입된 `Clock`을 기준으로 한다.

#### API와 정렬

- 공개 API: `GET /api/arts/search`
- 파라미터: `q`, `artist`, `format`, `category`, `status`, `sort`, `page`, `size`
- 빈 검색어와 빈 필터는 제한 없는 전체 공개 작품 탐색으로 처리하고 공백 문자열은 서버에서 정리한다.
- `ENDING_SOON`: 미종료 작품 우선, 마감 시각 오름차순, 작품 ID 내림차순
- `NEWEST`: 등록 시각 내림차순, 작품 ID 내림차순
- `PRICE_ASC`: 현재가 오름차순, 작품 ID 내림차순
- `PRICE_DESC`: 현재가 내림차순, 작품 ID 내림차순
- API 페이지는 0부터, 화면 URL 페이지는 1부터 시작한다. 조건 변경 시 첫 페이지로 돌아간다.
- 잘못된 요청값은 공통 `ApiErrorResponseDto` 형식의 구조화된 400 응답으로 반환한다.

#### 화면별 역할

- `/search`: 예정·진행·종료를 포함한 전체 공개 작품 탐색
- `/auction/total`: 진행 중인 전체 경매
- `/auction/digital`: 진행 중인 디지털 작품 경매
- `/auction/analog`: 진행 중인 실물 작품 경매
- 기존 URL을 유지하고 리다이렉트하지 않는다.
- 검색·경매 화면은 공통 조회 API, 작품 카드, 필터, 결과 그리드, 페이지네이션과 상태 UI를 재사용한다.
- 경매 화면의 고정 상태·형태 조건은 URL 쿼리로 덮어쓸 수 없게 한다.

### 1단계: 백엔드 분류 모델과 조회 기반

- `Art`에 형태, 카테고리, 등록 시각을 추가하고 신규 Flyway 마이그레이션을 작성한다.
- 작품 생성·수정 DTO와 서비스에 필수 분류값 저장 및 조합 검증을 추가한다.
- 검색 상태·정렬 enum, 검색 전용 응답 DTO와 동적 조회 구조를 구현한다.
- 작품명·작가명 부분 검색, 형태·카테고리·시간 상태 필터와 네 가지 정렬을 구현한다.
- repository/service/DTO 및 마이그레이션 테스트로 시간 경계, 취소 제외, 동률 정렬과 페이지 크기 제한을 검증한다.

주요 변경 대상:

- `backend/src/main/java/com/dailyatelier/dailyatelier/entity/Art.java`
- 작품 형태·카테고리·검색 상태·정렬 enum과 검색 DTO
- `ArtCreateRequestDto.java`, `ArtUpdateRequestDto.java`
- `ArtRepository.java`, 검색 전용 repository 구현, `ArtSearchService.java`
- 신규 Flyway migration과 관련 백엔드 테스트

커밋: `feat(backend): 작품 분류와 탐색 조회 기반 추가`

### 2단계: 공개 작품 탐색 API

- `GET /api/arts/search` 컨트롤러 계약과 파라미터 검증을 구현한다.
- 익명 요청과 만료 토큰 요청을 허용하고 기존 쓰기 API 권한은 유지한다.
- 기본값, 모든 필터·정렬 조합, 페이지 응답과 공통 오류 응답을 API 테스트로 검증한다.

주요 변경 대상:

- `ArtController.java`, 필요 시 검색 요청 DTO
- `SecurityConfig.java`
- 신규 `ArtSearchApiTest.java`, 필요 시 `ArtApiSecurityTest.java`

커밋: `feat(backend): 공개 작품 탐색 API 추가`

### 3단계: 검색 화면과 작품 등록 UX

- `/search` 준비 화면을 실제 작품 탐색 화면으로 교체한다.
- 작품명·작가명·형태·카테고리·상태·정렬 UI와 조건 초기화를 구현한다.
- URL을 단일 상태 원천으로 사용하고 새로고침, 직접 접근, 뒤로 가기와 페이지 이동을 지원한다.
- 작품 카드, 로딩·오류·빈 상태와 페이지네이션을 공용 컴포넌트로 분리한다.
- 작품 등록 화면에 형태와 카테고리 필수 선택을 추가하고 `material` 라벨을 재료·기법으로 명확히 한다.
- 데스크톱·모바일 반응형 화면, 접근성, 요청 취소와 오래된 응답 방지를 프론트 테스트로 검증한다.

주요 변경 대상:

- `frontend/src/App.jsx`, `frontend/src/api/artApi.js`
- 신규 검색 페이지와 CSS, 공용 작품 목록 컴포넌트
- `UploadSell.jsx`, `UploadSell.module.css`
- 신규 검색·등록 화면 테스트와 기존 `Header.test.jsx`

커밋: `feat(frontend): 작품 탐색과 분류 입력 화면 구현`

### 4단계: 경매 목록 통합과 종합 검증

- `/auction/total`, `/auction/digital`, `/auction/analog`를 각각 확정된 진행 중 경매 preset으로 구현한다.
- 검색 화면과 조회 함수·카드·결과 상태·페이지네이션을 공유하고 기존 전체 경매의 시간 조건 불일치를 해소한다.
- 상세 화면 이동 시 원래 검색·경매 URL을 보존한다.
- 백엔드 전체 테스트, 프론트 테스트, ESLint, 프로덕션 빌드와 데스크톱·모바일 화면을 종합 검증한다.
- 완료 결과는 `PLAN_DONE.md`에 옮기고 후속 범위는 `BACKLOG.md`에서 관리한다.

주요 변경 대상:

- `AuctionTotal.jsx`와 신규 경매 목록 wrapper 또는 공용 컴포넌트
- `App.jsx`, 관련 공용 CSS와 경매 목록 테스트
- `PLAN.md`, `PLAN_DONE.md`, `BACKLOG.md`

커밋: `refactor: 검색과 경매 작품 목록 구조 통합`
