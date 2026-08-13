# DailyAtelier 개발 계획

> [!IMPORTANT]
> 완료된 계획과 검증 결과는 `PLAN_DONE.md`에서 관리한다.
> 미완료·후속 작업은 `BACKLOG.md`에서 관리한다.
> 새 작업 계획은 사용자 요청 범위가 확정된 뒤 이 문서에 추가한다.

## 판매자 최소 입찰 증분 설정

### 목표와 완료 기준

판매자가 작품 등록 시 최소 입찰 증분을 설정하고, 서버가 작품의 최신 현재가와
증분을 기준으로 모든 입찰을 일관되게 검증한다. 사용자는 경매 용어를 미리 알지
않아도 등록 화면과 작품 상세에서 첫 입찰 가능 금액과 다음 입찰 가능 금액을 바로
이해할 수 있어야 한다.

다음 조건을 모두 만족하면 기능 구현을 완료한 것으로 본다.

- [x] 최소 입찰 증분의 저장·생성·수정·조회 API 계약이 구현되어 있다.
- [x] 기존 작품은 삭제나 DB 초기화 없이 1,000원으로 백필되어 있다.
- [x] 첫 입찰, 후속 입찰과 동일 사용자 재입찰에 같은 증분 규칙이 적용된다.
- [x] 동시 입찰의 후행 요청은 작품 잠금 후 최신 현재가로 다시 검증된다.
- [x] 최대 입찰가를 넘는 다음 입찰은 서버와 화면 모두에서 차단된다.
- [x] 판매 등록과 작품 상세에서 핵심 설명과 실제 금액을 상시 확인할 수 있다.
- [ ] 자동 테스트, MySQL 마이그레이션 검증과 대표 브라우저 QA가 완료되어 있다.
- [x] 기존 입찰·포인트 예치·경매 마감·낙찰 주문 흐름에 회귀가 없다.

### 확정 정책과 구현 계약

- [x] 기본 최소 입찰 증분은 1,000원으로 적용한다.
- [x] 최소 입찰 증분은 100원 이상 10,000,000원 이하만 허용한다.
- [x] 최소 입찰 증분 설정값은 100원 배수만 허용한다.
- [x] 100원 단위 제한은 증분 설정값에만 적용하고 시작가와 실제 입찰가는 기존
      1원 단위 정수 계약을 유지한다.
- [x] 첫 입찰부터 `현재가 + 최소 입찰 증분`을 최소 입찰 가능 금액으로 사용한다.
- [x] 경매 시작 시각과 정확히 같거나 지난 뒤에는 입찰 유무와 관계없이 증분
      수정을 허용하지 않는다.
- [x] 이번 작업에서는 기존 시작가·경매 시작 시각·마감 시각의 수정 정책을
      변경하지 않는다.
- [x] 시스템 최대 입찰가는 기존 2,100,000,000원 계약을 유지한다.
- [x] 일반적인 최소 금액 미달은 HTTP 409와 `BID_TOO_LOW`를 유지하고 실제 최소
      입찰 가능 금액을 오류 메시지에 포함한다.
- [x] 유효한 다음 입찰 금액을 만들 수 없는 경우 HTTP 409와
      `BID_LIMIT_REACHED` 오류로 구분한다.
- [x] 공통 오류 응답은 `timestamp`, `status`, `code`, `message`, `path` 필드만
      사용하고 증분 전용 필드를 추가하지 않는다.
- [x] 작품 상세 응답은 `minimumBidIncrement`와 nullable
      `nextMinimumBidPrice`를 제공한다.
- [x] 입찰 성공 응답도 갱신된 현재가와 다음 최소 입찰 가능 금액을 제공해 화면이
      추가 조회 없이 정상 상태를 갱신할 수 있게 한다.

### 현재 저장소 기준과 동반 정리 범위

- MySQL의 `start_price`, `current_price`, `bid_price`와 Java 가격 필드는 각각
  `INT`, `Integer`이다.
- 입찰 요청과 수정 요청의 최대값은 2,100,000,000원이지만 작품 생성 요청과
  판매 등록 화면에는 시작가 최대값 검증이 빠져 있다.
- `BidService`는 작품 행을 비관적 잠금으로 획득한 뒤 경매 상태와 가격을 검사하고,
  포인트 계정 잠금·입찰 저장·예치 변경·현재가 변경을 같은 트랜잭션에서 처리한다.
- 백엔드의 기존 최소가 가정은 `BidService.validateBidPrice`에 있고, 프런트의
  `currentPrice + 1`, `현재가보다 최소 1원` 가정은 `ArtDetail.jsx`에 집중되어 있다.
- 현재 작품 수정은 마감 전이고 입찰이 없으면 경매 시작 뒤에도 시작가와 기간을
  바꿀 수 있다. 이 동작은 이번 범위에서 유지하고 최소 입찰 증분만 시작 시각부터
  잠근다.
- 현재 프런트에는 작품 수정 화면과 `updateArt` API 함수가 없으므로 수정 기능은
  백엔드 PATCH 계약과 자동 테스트까지만 포함한다.
- 로컬 MySQL 8.0.45의 `dailyatelier`에는 성공한 Flyway V1~V3 이력, 작품 4건
  (활성 3건·유찰 1건), 입찰 0건이 있고 증분 컬럼은 없다. V4와 실제 백필이
  필요하며 기존 네 작품은 모두 기본 증분을 더해도 시스템 상한을 넘지 않는다.
- 로컬에서 Git 제외된 `application.properties`는 `ddl-auto=update`이므로 이
  설정에 의존하지 않고 격리 MySQL과 `ddl-auto=validate`로 V4 완전성을 검증한다.

## 1단계 — DB, 가격 정책과 작품 API 계약

### 예상 변경 파일

- `backend/src/main/resources/db/migration/V4__add_minimum_bid_increment.sql`
- `backend/src/main/java/com/dailyatelier/dailyatelier/entity/Art.java`
- 백엔드 공용 경매 가격 정책 클래스 신규 파일
- `backend/src/main/java/com/dailyatelier/dailyatelier/dto/ArtCreateRequestDto.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/dto/ArtUpdateRequestDto.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/dto/ArtResponseDto.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/dto/ArtDetailResponseDto.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/repository/ArtRepository.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/ArtService.java`
- 관련 DTO·서비스·API·마이그레이션 테스트 파일

### 구현 체크리스트

- [x] V4에서 `art.minimum_bid_increment`를 `INT NOT NULL DEFAULT 1000`으로
      추가해 기존 작품을 같은 실행에서 1,000원으로 백필한다.
- [x] V4에 100원 최소, 10,000,000원 최대와 100원 배수를 보장하는 MySQL
      CHECK 제약을 추가한다.
- [x] V4는 기존 행 삭제, 테이블 초기화, Flyway repair나 기존 migration 수정을
      사용하지 않는다.
- [x] 엔티티에 nullable이 아닌 최소 입찰 증분 필드를 추가한다.
- [x] 최대 입찰가, 증분 기본값·범위·단위와 overflow-safe 다음 최소가 계산을
      백엔드의 한 가격 정책 클래스로 중앙화한다.
- [x] 다음 최소가는 먼저 `long`으로 `(long) currentPrice + increment`를 계산한
      뒤 2,100,000,000원 이하일 때만 `Integer`로 변환한다.
- [x] 작품 생성 요청에서 증분 미입력은 1,000원을 적용하고 명시적 null, 범위
      이탈과 100원 미배수는 구조화된 400 응답으로 거절한다.
- [x] 작품 생성 시작가에 누락된 2,100,000,000원 상한을 추가한다.
- [x] 새 작품은 첫 입찰이 가능하도록
      `startPrice + minimumBidIncrement <= 2,100,000,000`을 검증한다.
- [x] 작품 수정 요청은 증분 필드의 미입력과 명시적 null을 구분하고 기존 PATCH의
      부분 수정 방식을 유지한다.
- [x] 현재 시각이 경매 시작 전일 때만 유효한 증분 수정을 허용하고, 시작 시각
      경계부터는 별도의 409 도메인 오류로 거절한다.
- [x] 기존 입찰이 있는 작품의 가격·기간 변경 제한 대상에 증분을 포함한다.
- [x] 증분 외 시작가·기간 수정 가능 시점과 기존 오류 의미는 변경하지 않는다.
- [x] 생성·수정 응답에 `minimumBidIncrement`를 포함한다.
- [x] 작품 상세 응답에 `minimumBidIncrement`와 nullable
      `nextMinimumBidPrice`를 포함한다.
- [x] `ArtRepository`의 constructor projection과 응답 DTO 생성부를 새 계약에
      맞게 변경한다.

### 테스트 체크리스트

- [x] 생성 DTO의 기본값, 명시적 null, 100원 최소, 100원 배수와 1,000만원
      상한을 검증한다.
- [x] 시작가 21억원 상한과 시작가·증분 합계의 첫 입찰 가능 경계를 검증한다.
- [x] 수정 DTO가 필드 미입력과 null을 구분하고 증분 범위·단위를 검증하는지
      확인한다.
- [x] 경매 시작 직전 증분 수정은 성공하고 정확한 시작 시각부터 실패하는지
      고정 Clock으로 검증한다.
- [x] 입찰이 존재하는 작품의 증분 수정 제한과 기존 시작가·기간 정책 회귀를
      검증한다.
- [x] 생성·수정·상세 API 응답이 증분과 다음 최소가를 노출하는지 검증한다.
- [x] V4가 기존 작품을 보존하고 1,000원으로 백필하며 NOT NULL·DEFAULT·CHECK를
      생성하는지 검증한다.
- [x] 빈 MySQL의 V1→V4 적용, Hibernate validate와 두 번째 migrate 0건을
      검증한다.

## 2단계 — 입찰 규칙, 포인트 트랜잭션과 동시성

### 예상 변경 파일

- `backend/src/main/java/com/dailyatelier/dailyatelier/service/BidService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/dto/BidCreateResponseDto.java`
- 입찰 오류 처리 계약 관련 파일
- `backend/src/test/java/com/dailyatelier/dailyatelier/controller/BidApiTest.java`
- `backend/src/test/java/com/dailyatelier/dailyatelier/service/BidServiceTest.java`
- `backend/src/test/java/com/dailyatelier/dailyatelier/service/BidServiceTransactionTest.java`
- `backend/src/test/java/com/dailyatelier/dailyatelier/service/BidServiceConcurrencyTest.java`
- `backend/src/test/java/com/dailyatelier/dailyatelier/service/ArtMutationConcurrencyTest.java`
- 경매 마감·낙찰 회귀 테스트 파일

### 구현 체크리스트

- [x] 기존 `validateBidPrice`의 `현재가보다 큼` 검증을 서버 가격 정책의 실제
      다음 최소 입찰가 검증으로 교체한다.
- [x] 가격 검증은 작품 행 비관적 잠금과 경매 상태 검사 뒤, 포인트 계정 잠금과
      어떤 저장 작업보다 먼저 수행한다.
- [x] 다음 최소가가 시스템 상한 이하면 요청가가 그 금액 이상인지 검사하고 실제
      입찰가에는 100원 배수 제한을 적용하지 않는다.
- [x] 최소가보다 낮은 요청은 기존 `BID_TOO_LOW` 409를 반환하고 서버 최신
      최소가를 사용자 메시지에 포함한다.
- [x] 다음 최소가가 시스템 상한을 초과하면 저장과 포인트 변경 전에
      `BID_LIMIT_REACHED` 409를 반환한다.
- [x] 입찰 성공 응답에 `minimumBidIncrement`와 nullable
      `nextMinimumBidPrice`를 포함한다.
- [x] 기존 입찰 저장, 현재가 변경, 동일 사용자 차액 예치, 다른 사용자 예치
      해제와 원장 저장 순서를 변경하지 않는다.
- [x] 두 동시 입찰의 후행 트랜잭션이 선행 트랜잭션의 현재가를 본 뒤 최소가를
      다시 계산하도록 기존 작품 단위 직렬화를 유지한다.

### 테스트 체크리스트

- [x] 첫 입찰에서 정확히 `현재가 + 증분`인 금액은 성공한다.
- [x] 첫 입찰에서 최소 입찰 가능 금액보다 1원 부족하면 `BID_TOO_LOW`로
      실패한다.
- [x] 후속 입찰은 갱신된 현재가와 같은 증분으로 최소가를 다시 계산한다.
- [x] 실제 입찰가가 100원 배수가 아니어도 최소가 이상이면 성공한다.
- [x] 동일 사용자의 재입찰은 새 입찰가와 기존 활성 예치의 차액만 추가 예치한다.
- [x] 다른 사용자의 후속 입찰은 신규 전액 예치와 기존 최고 입찰자 예치 해제를
      원자적으로 처리한다.
- [x] 포인트가 정확히 필요한 금액이면 성공하고 1포인트 부족하면 입찰·현재가·
      예치·원장에 부분 변경이 남지 않는다.
- [x] 동시 입찰의 후행 요청이 최신 현재가 기준 최소가보다 낮으면 실패하고 높은
      요청만 현재가를 변경한다.
- [x] 합계가 정확히 2,100,000,000원이 되는 입찰은 허용하고 성공 응답의 다음
      최소가는 null이다.
- [x] 합계가 2,100,000,000원을 초과하는 작품은 어떤 입찰도 저장하지 않고
      `BID_LIMIT_REACHED`를 반환한다.
- [x] `Integer.MAX_VALUE`에 가까운 비정상 fixture에서도 덧셈 overflow 없이
      상한 오류를 반환한다.
- [x] 입찰 저장 또는 예치·계정·원장·현재가 저장 실패 시 기존 전체 롤백 계약이
      유지된다.
- [x] 경매 마감이 최고 입찰, 현재가와 활성 예치의 사용자·금액·입찰 식별자를
      기존처럼 대조하고 낙찰 주문을 한 번만 생성한다.
- [x] 입찰·수정·삭제·마감 경합의 기존 잠금 순서와 회귀 테스트를 통과한다.

## 3단계 — 판매 등록과 작품 상세 UX

### 예상 변경 파일

- `frontend/src/pages/MyPage/UploadSell.jsx`
- `frontend/src/pages/MyPage/UploadSell.module.css`
- `frontend/src/pages/Auction/ArtDetail.jsx`
- `frontend/src/pages/Auction/ArtDetail.module.css`
- 필요 시 프런트 가격 정책 상수 파일
- 판매 등록 증분 컴포넌트 테스트 신규 파일
- 작품 상세 입찰 컴포넌트 테스트 신규 파일

### 구현 체크리스트

- [x] 판매 등록 폼 상태에 기본값 `1000`인 `minimumBidIncrement`를 추가한다.
- [x] 증분 입력에 `type="number"`, `min="100"`, `max="10000000"`,
      `step="100"`과 명확한 연결 label을 적용한다.
- [x] HTML 속성에만 의존하지 않고 JavaScript에서 정수·범위·100원 배수를 다시
      검증한다.
- [x] 등록 화면에서 시작가 2,100,000,000원 상한과 시작가·증분 합계 경계를
      서버와 동일하게 검증한다.
- [x] 증분 입력 아래에 “다음 입찰자는 현재가보다 최소 이 금액만큼 높게 입찰해야
      합니다.”와 “100원 단위 · 기본 1,000원” 도움말을 항상 표시한다.
- [x] 현재 시작가와 유효한 증분으로 `첫 입찰 가능 금액은 31,000원부터입니다.`
      형식의 실제 예시를 실시간 표시한다.
- [x] 도움말과 오류를 입력의 `aria-describedby`로 연결하고 tooltip이나 hover를
      사용하지 않아 키보드·모바일·스크린리더에서도 같은 정보를 제공한다.
- [x] 생성 payload에 숫자로 변환한 증분을 포함하고 등록 완료 화면에도 설정값을
      표시한다.
- [x] 작품 상세의 `currentPrice + 1`, “최소 1원”과 현재가만으로 판단하는 상한
      가정을 모두 제거한다.
- [x] 작품 상세에 현재가, 다음 입찰 가능 금액과 최소 입찰 증분을 각각 명확한
      label과 실제 금액으로 표시한다.
- [x] 입찰 입력의 프런트 최소 검증과 placeholder는 서버가 반환한
      `nextMinimumBidPrice`를 사용한다.
- [x] 동시 입찰 가능성을 안내하고 프런트 검증이 서버 최종 검증을 대체하지
      않도록 한다.
- [x] 입찰 성공 응답으로 현재가·증분·다음 최소가를 함께 갱신한다.
- [x] `BID_TOO_LOW` 409에서는 작품 상세를 재조회하고 반환된 최신 최소가를
      오류 안내에 직접 사용해 비동기 상태 갱신 지연에 의존하지 않는다.
- [x] `BID_LIMIT_REACHED` 또는 nullable 다음 최소가에서는 “최소 증분을 적용하면
      시스템 최대 입찰가를 초과합니다.”를 표시하고 입력과 버튼을 비활성화한다.
- [x] 작은 화면에서도 세 가격 정보와 상시 도움말이 잘리지 않고 입력·버튼의
      읽기 순서와 focus 표시가 유지되도록 반응형 스타일을 확인한다.

### 테스트 체크리스트

- [x] 판매 등록 화면에 1,000원 기본값과 상시 도움말이 표시된다.
- [x] 시작가 또는 증분 변경 시 첫 입찰 가능 금액 예시가 즉시 갱신된다.
- [x] 증분 100원과 10,000,000원은 허용하고 99원, 150원과 10,000,100원은
      클라이언트에서 거절한다.
- [x] 시작가 21억원 초과와 시작가·증분 합계 초과를 등록 전에 거절한다.
- [x] 유효한 생성 요청에 숫자형 `minimumBidIncrement`가 포함된다.
- [x] 작품 상세가 현재가, 다음 입찰 가능 금액과 최소 증분을 모두 표시한다.
- [x] 정확한 최소가 제출은 API를 호출하고 1원 부족은 호출 전에 안내한다.
- [x] 100원 배수가 아닌 실제 입찰가도 최소가 이상이면 API를 호출한다.
- [x] 입찰 성공 후 응답 기준으로 현재가와 다음 최소가를 갱신한다.
- [x] `BID_TOO_LOW` 발생 후 재조회한 최신 최소가를 표시한다.
- [x] `BID_LIMIT_REACHED`와 다음 최소가 null 상태에서 입력과 버튼을 비활성화한다.
- [x] 도움말·오류 연결, label, disabled 상태와 키보드 접근성을 검증한다.

## 4단계 — 통합 검증과 사용자 브라우저 QA

### 자동 검증 체크리스트

- [x] 백엔드 관련 DTO·서비스·API·트랜잭션·동시성 테스트를 먼저 실행한다.
- [x] 백엔드 전체 테스트를 실행해 입찰·포인트·경매 마감·낙찰 주문 회귀를
      확인한다.
- [x] 프런트 증분 등록·작품 상세 컴포넌트 테스트를 실행한다.
- [x] 프런트 전체 테스트, ESLint와 production build를 실행한다.
- [x] 격리된 빈 MySQL에서 V1→V4, Hibernate `ddl-auto=validate`, 재실행 0건과
      최종 컬럼·기본값·CHECK를 확인한다.
- [x] 기존 V1→V3 데이터 fixture에 V4를 적용해 행 수와 식별자가 유지되고 모든
      작품이 1,000원으로 백필되는지 확인한다.

### 브라우저 수동 QA 체크리스트

- [x] 로컬 테스트 계정은 Git에서 제외된 `_local/test-accounts.md`를 확인해
      판매자와 서로 다른 구매자 계정을 사용한다.
- [x] 판매자가 기본 증분, 최소 100원과 최대 10,000,000원 작품을 등록한다.
- [x] 판매 등록의 도움말, 실시간 첫 입찰가 예시와 validation 오류가 데스크톱과
      모바일 viewport에서 별도 hover 없이 이해되는지 확인한다.
- [x] API 수준에서 경매 시작 전 증분 PATCH 성공과 시작 경계 이후 409 거절을
      확인한다.
- [x] 작품 상세에서 현재가, 다음 입찰 가능 금액과 최소 증분이 구분되어 보이는지
      확인한다.
- [x] 구매자가 정확한 최소가로 첫 입찰하고 다음 최소가가 즉시 갱신되는지
      확인한다.
- [x] 최소가보다 1원 낮은 금액과 100원 배수가 아닌 유효한 실제 입찰가를 각각
      확인한다.
- [x] 같은 구매자가 재입찰한 뒤 마이페이지 포인트 가용·예치 잔액과 입찰 현황을
      확인한다.
- [x] 두 구매자의 오래된 화면을 이용해 선행 입찰 뒤 후행 요청이 최신 최소가
      안내를 받는 동시 입찰 대표 시나리오를 확인한다.
- [ ] 최대 입찰가 경계 fixture에서 마지막 유효 입찰과 이후 추가 입찰 불가 안내,
      disabled 상태를 확인한다.
- [x] 경매 마감 후 낙찰 결과와 주문 생성까지 기존 사용자 흐름을 확인한다.

> 2026-08-13 브라우저 QA에서 작가 1명·일반 회원 2명의 로컬 전용 계정을
> 회원가입 API로 생성하고 실제 로그인·역할을 검증했다. QA 작품은 ID 5~9로
> 보존했다. 기존 INTERNAL 데모 충전 API로 두 구매자에게 각각 1,000,000P를
> 충전한 뒤 성공 입찰·재입찰·stale-response·예치 이전과 마감 주문을 확인했다.
> 최대가 화면은 다음 입찰 가능 금액 2,100,000,000원까지 확인했지만, 데모 포인트
> 보유 상한이 계정당 1,000,000P라 성공 입찰 이후 nullable 다음 최소가와 disabled
> 상태는 직접 DB fixture 없이 브라우저에서 확인할 수 없어 미완료로 유지한다.

### 실제 검증 결과

- 구현 커밋은 `620e0f4`(백엔드)와 `5194f5e`(프런트엔드)로 분리했다.
- 백엔드 관련 테스트와 전체 테스트, 프런트 전용·전체 테스트, ESLint와 production
  build가 통과했다.
- 격리 MySQL의 V1→V4 적용, Hibernate validate, Flyway 재실행 0건과 기존 4개
  작품의 ID·행 수 보존 및 1,000원 백필을 확인했다.
- 로컬 `dailyatelier` DB도 V1~V4 성공, 기존 작품 4건 보존, 증분·DEFAULT 1,000,
  CHECK 존재, Hibernate validate와 Flyway 재실행 0건 상태를 확인했다.
- 브라우저에서는 등록 validation, 상세 금액, 첫 입찰·재입찰, 두 구매자
  stale-response, 포인트 예치 이전, 마감·낙찰·주문까지 통과했다.
- 2,100,000,000원 성공 입찰 후 nullable 다음 최소가와 disabled 표시는 정상 충전
  경로의 계정당 1,000,000P 상한 때문에 미수행했으며 `BACKLOG.md`에서 후속 관리한다.

## 커밋 경계

기능 코드와 계획·완료 문서를 같은 커밋에 포함하지 않는다. 각 기능 커밋에는 해당
코드와 그 코드를 검증하는 자동 테스트를 함께 포함한다.

1. 계획 문서 커밋
   - 메시지: `chore: 최소 입찰 증분 구현 계획 추가`
   - 범위: 승인된 `PLAN.md`만 포함한다.
2. 백엔드 기능 커밋
   - 메시지: `feat(backend): 최소 입찰 증분 정책 추가`
   - 범위: V4, 엔티티, 공용 가격 정책, 작품·입찰 API와 백엔드 테스트를 포함하고
     문서는 포함하지 않는다.
3. 프런트엔드 기능 커밋
   - 메시지: `feat(frontend): 입찰 증분 설정과 다음 입찰가 안내`
   - 범위: 판매 등록·작품 상세 UI와 프런트 테스트를 포함하고 문서는 포함하지
     않는다.
4. 완료 문서 커밋
   - 메시지: `chore: 최소 입찰 증분 검증 결과 정리`
   - 범위: 자동 테스트·MySQL·브라우저 QA 결과를 반영한 `PLAN.md`,
     `PLAN_DONE.md`, `BACKLOG.md`의 사용자 승인 범위만 포함한다.

승인된 단계, 순서, 완료 기준, 네 개의 커밋 경계와 메시지는 구현 중 임의로 합치거나
나누지 않는다. 범위 변경이 필요하면 저장소 상태를 바꾸기 전에 발견한 문제와 대안을
먼저 보고하고 승인을 받는다.

---

## 프런트 접근성 P1 개선

### 목표와 범위

헤더 메뉴의 키보드 조작, 인증 폼의 레이블·오류 전달, 주요 상호작용 요소의
포커스 표시와 동작 감소 설정을 보완한다. 공통 dialog 접근성은 이번 범위에서
제외하고 `BACKLOG.md`의 별도 후속 작업으로 유지한다.

### 1단계 — 헤더 메뉴 키보드 접근성

- [x] 데스크톱 드롭다운 trigger와 제어 대상을 연결하고 닫힌 메뉴의 링크가
      키보드 포커스를 받지 않게 한다.
- [x] Escape와 Header 밖 포커스 이동 시 메뉴를 닫고 Escape 종료 시 trigger로
      포커스를 복귀시킨다.
- [x] 모바일 메뉴 toggle에 펼침 상태와 제어 대상을 제공하고 열릴 때 첫 조작
      요소로 포커스를 이동한다.
- [x] 모바일 메뉴가 열린 동안 본문·footer 상호작용과 배경 스크롤을 차단하고
      Escape, 링크 이동, 검색 제출과 breakpoint 변경 시 안전하게 복구한다.
- [x] 기존 마우스 hover, 로그인 상태별 메뉴와 반응형 동작을 유지한다.

### 2단계 — 인증 폼 레이블과 오류 접근성

- [x] 로그인과 일반·작가 회원가입의 모든 입력에 지속적으로 보이는 label과
      고유 id를 제공하고 placeholder는 예시·형식 안내로만 사용한다.
- [x] 필수 여부, 입력 도움말, 중복 검사와 비밀번호 확인 결과를 입력의
      `aria-describedby`와 `aria-invalid`에 연결한다.
- [x] 제출 오류와 비동기 검사 결과를 적절한 live region으로 전달한다.
- [x] 중복확인 미완료와 비밀번호 불일치의 blocking alert를 인라인 오류로
      바꾸고 관련 입력 또는 버튼으로 포커스를 이동한다.
- [x] 기존 API 계약, 검증 규칙과 모바일 폼 배치를 변경하지 않는다.

### 3단계 — 포커스 표시와 동작 감소 설정

- [x] 주요 링크·버튼·키보드 조작 요소에 식별 가능한 전역 `:focus-visible`을
      제공하고 기존의 더 구체적인 포커스 스타일을 보존한다.
- [x] 동작 감소 설정에서는 전역 smooth scroll을 끄고 홈 자동 슬라이드를
      시작하거나 재시작하지 않는다.
- [x] 홈 슬라이드 전환, 확인된 skeleton과 장식성 hover motion 등 현재 코드에서
      확인한 비필수 motion만 선택적으로 줄인다.
- [x] 전역 animation·transition을 무차별적으로 제거하지 않고 로딩·상태 전달과
      수동 슬라이드 조작을 유지한다.

### 4단계 — 검증과 완료 문서

- [x] 프런트 lint, 단위 테스트, 가능한 컴포넌트 테스트, production build와
      `git diff --check`를 실행한다.
- [ ] 1440px, 1024px, 768px, 390px과 인증 화면 200% 확대에서 Tab, Shift+Tab,
      Enter, Space와 Escape 동작을 확인한다.
- [ ] 기본 motion과 reduced-motion에서 홈 자동 전환, 수동 전환, smooth scroll,
      포커스 표시와 브라우저 오류를 확인한다.
- [x] 구현과 지원되는 검증이 통과한 뒤 관련 BACKLOG 세 항목만 완료 처리하고 공통 dialog
      항목은 미완료로 유지한다.

### 검증 결과

- ESLint, 단위 테스트 15개, 컴포넌트 테스트 61개와 production build가
  통과했으며 `git diff --check` 오류가 없다.
- Header와 인증 폼 테스트는 변경 원인에 맞춰 각각 첫 번째와 두 번째 기능
  커밋에 포함했다.
- 브라우저에서 390px 모바일 메뉴의 최초 포커스, 배경 스크롤 차단, Escape 종료와
  포커스 복귀, 가로 overflow 없음 및 데스크톱 trigger의 포커스 복귀를 확인했다.
- 작가 회원가입 입력 10개의 label 연결과 `aria-describedby` 대상 존재를 확인했다.
- 브라우저 환경의 motion 설정은 기본값만 사용할 수 있어 reduced-motion 강제
  에뮬레이션과 200% 확대 전체 회귀는 수행하지 못했다. 구현은 전역 일괄 제거 없이
  smooth scroll, 홈 자동 재생과 확인된 비필수 transition으로 제한했다.

### 커밋 경계

1. `503d78a` `fix(frontend): 헤더 메뉴 키보드 접근성 개선`
2. `db095f4` `fix(frontend): 인증 폼 오류 접근성 보완`
3. `6b4fb26` `fix(frontend): 포커스 표시와 동작 감소 설정 보완`
4. `chore: 프론트 접근성 백로그 완료 처리`

기능 커밋에는 각 기능 코드와 직접 관련된 테스트만 포함한다. `PLAN.md`의 계획·검증
상태와 `BACKLOG.md` 완료 표시는 전체 검증 후 네 번째 문서 커밋에서만 처리한다.
