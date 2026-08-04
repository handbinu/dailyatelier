# DailyAtelier 핵심 도메인 품질 감사

## 감사 기준

- 기준 브랜치: `dev`
- 기준 커밋: `9bca4370dda80193d44ee786caae61406b0e6578`
- 감사 방식: 구현, 호출 경로와 기존 테스트를 교차 확인하는 영역별 감사
- 제외 범위: 부하 테스트, SQL 실행 계획, 운영 환경 락 타임아웃 실측

## 전체 발견사항 요약

> `이번 수정 대상`은 다음 구현 단계의 권장 범위이며 아직 수정 승인을 의미하지 않는다. `BACKLOG`는 운영과 같은 실행 환경, 실제 PG 연동 또는 정책 확정이 선행되어야 하는 검증 항목이다. 잠재 위험도 안전한 정적 보완이 가능하면 이번 수정 대상으로 분류했다.

| QA 번호 | 심각도 | 판정 | 한 줄 요약 | 권장 우선순위 | 이번 수정 대상 / BACKLOG |
|---|---|---|---|---|---|
| QA-001 | 높음 | 확정 결함 | `SHIPPED → DELIVERED` 실제 호출 경로가 없어 구매 확정 흐름이 중단됨 | P0 | 이번 수정 대상 |
| QA-002 | 중간 | 확정 결함 | 구매자 요청·판매자 승인 환불 정책이 API·화면에 연결되지 않음 | P1 | 이번 수정 대상 |
| QA-003 | 높음 | 확정 결함 | 운영 데모 충전임을 명시하거나 발행량을 제한하는 장치가 없음 | P0 | 이번 수정 대상 |
| QA-004 | 높음 | 잠재 위험 | 콜백 처리기 예외 흡수로 부분 커밋 또는 rollback-only 충돌 가능성이 있음 | P0 | 해결 — 트랜잭션 분리 및 H2 자동 검증 완료 |
| QA-005 | 중간 | 확정 결함 | 정합성 검사가 계정 잔액과 원장 합계 외의 의미적 불일치를 놓침 | P1 | 이번 수정 대상 |
| QA-006 | 낮음 | 잠재 위험 | 동시 최초 콜백의 유니크 충돌을 멱등 성공으로 복구하지 않음 | P2 | BACKLOG — 실제 PG 콜백 진입점 도입 시 함께 검증 |
| QA-007 | 높음 | 확정 결함 | 현재 Flyway 이력만으로 빈 MySQL DB를 생성할 수 없음 | P0 | 해결 — 신규 V1 및 빈 MySQL 자동 검증 완료 |
| QA-008 | 높음 | 잠재 위험 | 기존 활성 경매·주문의 포인트 예치와 원장 관계를 이관하지 않음 | P0 | 적용 대상 없음 — 운영·공유 DB 부재 및 개발 DB 재생성 정책 |
| QA-009 | 중간 | 잠재 위험 | 기존 null `reserve`가 V1의 `NOT NULL` 변경을 중단시킬 수 있음 | P1 | 적용 대상 없음 — 신규 V1에서 null 불허 직접 보장 |
| QA-010 | 중간 | 확정 결함 | 충전 초기 조회 전·실패 후에도 0원 잔액과 충전 가능 상태를 표시함 | P1 | 이번 수정 대상 |
| QA-011 | 낮음 | 확정 결함 | 이전 중복확인 응답이 변경된 회원가입 입력을 검증 완료로 표시함 | P2 | 이번 수정 대상 |
| QA-012 | 낮음 | 잠재 위험 | 실패한 충전 후 금액을 바꿔도 이전 멱등성 키를 재사용함 | P2 | 이번 수정 대상 |
| QA-013 | 중간 | 확정 결함 | 도메인별 예외 처리 범위가 달라 오류 응답 계약이 일관되지 않음 | P1 | 해결 — 공통 예외 계약 및 회귀 검증 완료 |
| QA-014 | 낮음 | 잠재 위험 | 포인트 목록의 음수 페이지가 명시적인 400 API 오류로 변환되지 않음 | P2 | 이번 수정 대상 |

우선순위 기준:

- `P0`: 핵심 사용자 흐름 중단, 포인트·트랜잭션 안전성 또는 배포 자체에 직접 영향을 주므로 먼저 처리한다.
- `P1`: 데이터 정합성 탐지, 정책 연결, 주요 화면 상태와 공통 API 계약을 보완한다.
- `P2`: 경계 입력, 비동기 응답 경합과 재시도 품질을 보완한다.

## 권장 수정 묶음과 순서

아래 순서는 묶음 내부의 의존 관계를 기준으로 한다. 서로 다른 묶음의 작업은 별도 브랜치 또는 커밋으로 분리할 수 있지만, P0 검증이 끝나기 전에는 관련 기능을 완료로 판단하지 않는다.

### A. 사용자 흐름·권한·데이터 정합성

1. `QA-001`: 구매자가 `SHIPPED` 주문을 배송 완료 처리하도록 `DELIVERED` 전환 API·화면과 전체 흐름 테스트를 추가한다.
2. `QA-002`: 구매자 요청·판매자 승인 방식으로 기존 환불 서비스를 API·화면에 연결하고 `CONFIRMED` 이후 일반 환불을 차단한다.
3. `QA-003`: 데모 충전 표시, 고정 금액, 계정별 보유 한도, 선택적 재충전 기준, `DEMO_CHARGE` 원장 유형과 요청 제한을 함께 설계한다.
4. `QA-005`: 잔액 합계 검사에 활성 예치, 작품 참조, 주문 결제·환불, 충전 상태와 원장 참조 검사를 추가한다.

이 묶음에서는 주문·포인트 소유권 자체의 누락은 발견되지 않았다. 따라서 기존 인증 주체 기반 조회와 구매자·판매자 검증은 유지하고, 신규 배송·환불 진입점이 같은 검증 방식을 재사용하는지 회귀 테스트한다.

### B. 결제 콜백·원장

1. `QA-004`: 처리기 실패를 재현하는 트랜잭션 테스트로 실제 롤백 결과를 먼저 확정하고, 예외 기록을 별도 트랜잭션으로 분리할지 전체 실패로 전파할지 결정한다.
2. `QA-006`: 실제 PG 콜백 Controller 도입 시 동시 최초 수신의 유니크 충돌을 기존 이벤트 재조회와 payload 검증을 통한 멱등 결과로 변환한다.
3. `QA-005`: A 묶음의 의미적 정합성 검사를 콜백·충전 상태까지 확장해 콜백 처리 결과를 사후 탐지할 수 있게 한다.

`QA-004`와 `QA-006`은 모두 콜백 실패·재시도 문제지만 실패 지점이 다르다. 전자는 저장된 이벤트 처리의 트랜잭션 경계이고, 후자는 최초 이벤트 insert 경합이므로 하나의 결함으로 합치지 않고 같은 작업 묶음에서 순서대로 처리한다.

### C. Flyway·DB 마이그레이션

1. `QA-007`: 빈 DB 전체 기준 스키마와 기존 DB baseline 전략을 먼저 확정한다.
2. `QA-009`: 기존 `reserve` null을 사전 정규화하고 SQL 모드와 무관한 제약 강화 순서를 만든다.
3. `QA-008`: 기존 활성 경매·주문 데이터의 존재를 조사한 뒤 거래 동결·정리 또는 예치·원장 이관 중 하나를 선택한다.
4. 빈 DB와 대표 레거시 fixture에서 전체 migration, Hibernate `validate`, 재실행과 실패 복구를 순서대로 검증한다.

마이그레이션 SQL 수정 전에 운영 또는 배포 DB의 스키마 버전과 활성 거래 건수를 읽기 전용으로 확인해야 한다. 확인 없이 V1을 재작성하거나 기존 Flyway checksum을 변경하지 않는다.

### D. 프론트·오류 응답 일관성

1. `QA-013`: 백엔드 전역 오류 DTO와 안정적인 오류 코드 계약을 먼저 통일한다.
2. `QA-014`: 포인트 페이지 경계를 공통 계약의 400 응답으로 맞춘다.
3. `QA-010`: 충전 화면의 초기 로딩·조회 실패·재시도 상태와 제출 가능 조건을 분리한다.
4. `QA-011`: 회원가입 중복확인에 요청 값 스냅샷 또는 취소·최신 요청 식별을 적용한다.
5. `QA-012`: 충전 요청 내용과 멱등성 키를 함께 보관하고, 내용 변경 시 새 키를 발급한다.
6. 컴포넌트 테스트에서 401, 409, 응답 역전, 응답 유실과 빠른 중복 클릭을 검증한다.

오류 응답 계약을 먼저 고쳐야 프론트가 메시지 문자열이 아닌 오류 코드에 안정적으로 반응할 수 있다. 주문·입찰의 기존 중복 요청 가드와 409 재조회 로직은 유지한다.

### E. 운영 환경 검증 BACKLOG

다음 항목은 코드 수정 완료 여부와 별개로 운영과 같은 환경이 필요하므로 `BACKLOG.md` 범위로 유지한다.

1. 실제 MySQL의 경매 마감·입찰·결제·만료 경합, 잠금 순서, 락 타임아웃과 처리량을 측정한다.
2. 빈 DB 및 운영 데이터 복제본에서 Flyway 전체 적용, DDL 중간 실패, `repair`·재적용과 테이블 락 시간을 검증한다.
3. 기존 활성 입찰·주문·null `reserve`·외래키 불일치·중복 데이터의 사전 점검 SQL을 실행한다.
4. 실제 PG 연동 시 콜백 서명, 주문·금액·사용자 검증, 동시 최초 수신, 재전송 성공 응답과 장애 복구를 검증한다.
5. 브라우저 E2E에서 만료 토큰, 느린 네트워크, 탭 간 로그아웃, 요청 성공 후 응답 유실과 빠른 중복 클릭을 검증한다.
6. 계정·원장·예치·주문·충전의 의미적 정합성 운영 조회와 경보 주기, 보정 권한을 확정한다.
7. 실제 택배사 또는 통합 배송조회 API의 인증, 택배사 코드·상태 매핑, webhook·polling, 호출 제한과 장애 재시도를 검토한다.

## 상세 감사 결과 읽는 방법

- 아래 1~7단계 기록은 위 요약의 근거 원문이다. 파일 경로, 코드 위치, 발생 조건, 영향, 근거, 재현 방법과 권장 수정안은 삭제하지 않았다.
- 단계별 `무발견 근거`는 확인하지 않았다는 뜻이 아니라, 명시된 테스트와 구현 범위에서는 추가 결함을 확정하지 못했다는 뜻이다.
- 단계별 `추가 검증이 필요한 사항` 중 실행 환경 의존 항목은 위 운영 환경 검증 BACKLOG에 통합해 우선순위를 제시했으며, 세부 맥락은 각 단계에도 유지한다.

## 1단계 — 사용자 흐름 연결 감사

### 확인한 파일

백엔드 구현:

- `controller/UserController.java`
- `controller/ArtController.java`
- `controller/BidController.java`
- `controller/OrderController.java`
- `controller/SellerOrderController.java`
- `service/UserService.java`
- `service/PointAccountService.java`
- `service/ArtService.java`
- `service/BidService.java`
- `service/AuctionCloseService.java`
- `service/OrderService.java`
- `service/OrderStateService.java`
- `service/OrderPointLedgerService.java`
- `service/OrderExpirationService.java`
- `entity/Artist.java`
- `entity/Order.java`
- `entity/OrderStatus.java`

프론트 구현:

- `api/authApi.js`
- `api/artApi.js`
- `api/orderApi.js`
- `pages/auth/RegisterUser.jsx`
- `pages/auth/RegisterArtist.jsx`
- `pages/MyPage/UploadSell.jsx`
- `pages/Auction/ArtDetail.jsx`
- `pages/MyPage/OrderStatus.jsx`
- `pages/MyPage/SalesOrders.jsx`

### 확인한 테스트와 실행 결과

백엔드에서 다음 10개 테스트 클래스를 실행했다.

- `PointAccountServiceTransactionTest`
- `ArtApiSecurityTest`
- `BidServiceTransactionTest`
- `AuctionCloseServiceTest`
- `AuctionCloseOrderRollbackTest`
- `OrderServiceTest`
- `OrderStateServiceTest`
- `OrderExpirationServiceTest`
- `OrderShippingAddressApiTest`
- `OrderManagementApiTest`

결과는 총 64건 통과, 실패 0건, 오류 0건, 제외 0건이다.

프론트는 `node --test src/utils/*.test.js`로 주문 상태·중복 요청·오류 변환 관련 테스트를 실행했고 총 9건이 통과했다. `npm test`는 프로젝트 테스트가 시작되기 전에 로컬 전역 npm 경로의 `npm-cli.js`를 찾지 못해 실패했으며, 같은 스크립트 본문을 직접 실행해 테스트 코드 자체의 통과 여부를 분리해 확인했다.

### 연결 구간별 확인 결과

#### 회원가입 → 작품 등록

- 일반·작가 회원가입 모두 회원 저장과 포인트 계정 초기화를 같은 회원가입 트랜잭션에서 호출한다.
- 작가 회원가입은 `Artist.user`의 `CascadeType.PERSIST`를 통해 신규 사용자를 함께 저장한다.
- 작품 등록은 인증 사용자 조회 후 작가 상태와 작가 프로필을 모두 확인한다.
- 이 구간에서 연결을 끊는 확정 결함은 발견하지 못했다.

근거:

- `UserService.registerUser`, `UserService.registerArtist`
- `Artist.user`
- `PointAccountService.initializeAccount`
- `ArtService.createArt`
- `PointAccountServiceTransactionTest`
- `ArtApiSecurityTest`

#### 작품 등록 → 입찰 → 낙찰 → 주문 생성

- 작품 생성 시 활성 상태와 시작가·현재가가 설정된다.
- 입찰은 작품 행 잠금 후 경매 시간·상태·판매자 본인 여부·현재가를 검증하고 입찰, 포인트 예치와 현재가를 같은 트랜잭션에서 변경한다.
- 경매 마감은 작품 행을 잠그고 최고 입찰과 활성 예치의 사용자·금액·입찰 식별자를 대조한 뒤 낙찰 상태와 주문을 생성한다.
- 주문 생성 실패 시 경매 마감 변경도 롤백되는 테스트가 존재한다.
- 이 구간에서 연결을 끊는 확정 결함은 발견하지 못했다.

근거:

- `ArtService.createArt`
- `BidService.createBid`
- `AuctionCloseService.closeAuction`
- `OrderService.createForSoldAuction`
- `BidServiceTransactionTest`
- `AuctionCloseServiceTest`
- `AuctionCloseOrderRollbackTest`

#### 주문 생성 → 배송지 확정 → 포인트 결제

- 낙찰 주문은 기본 배송지가 유효하면 생성 시 스냅샷으로 확정하고, 없으면 구매자가 결제 전 배송지를 확정할 수 있다.
- 배송지 변경과 결제는 모두 주문 행을 잠그며 구매자 소유권, `PAYMENT_PENDING`, 결제 기한과 배송지 확정을 확인한다.
- 결제 시 낙찰 예치를 `COMMIT` 원장 거래로 확정한 후 주문을 `PAID`로 변경한다.
- 결제 만료 또는 결제 전 구매자 포기 시 예치를 해제하고 주문을 `CANCELED`로 변경한다.
- 이 구간에서 연결을 끊는 확정 결함은 발견하지 못했다.

근거:

- `OrderService.createForSoldAuction`, `OrderService.confirmShippingAddress`
- `OrderStateService.markPaid`, `OrderStateService.cancelPending`
- `OrderPointLedgerService.commit`, `OrderPointLedgerService.release`
- `OrderExpirationService.expireOrder`
- `OrderServiceTest`
- `OrderStateServiceTest`
- `OrderExpirationServiceTest`
- `OrderShippingAddressApiTest`
- `OrderManagementApiTest`

#### 결제 → 배송 → 구매 확정

- 아래 `QA-001` 때문에 실제 API와 화면을 통한 전체 흐름이 `SHIPPED`에서 중단된다.

#### 취소·환불

- 결제 전 구매자 포기와 결제 만료는 API 또는 스케줄러 호출 경로가 있고 예치 해제까지 연결된다.
- 결제 후 환불은 서비스와 원장 반대 거래 구현 및 서비스 테스트는 있으나 실제 진입점이 확인되지 않았다. 아래 `QA-002`에 기록한다.

### 발견사항

#### QA-001 — 발송 완료 주문을 배송 완료로 전환할 호출 경로가 없음

- 심각도: 높음
- 관련 파일과 코드 위치:
  - `backend/src/main/java/com/dailyatelier/dailyatelier/entity/OrderStatus.java:28-29`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/OrderStateService.java:135-145`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/controller/SellerOrderController.java:56-74`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/controller/OrderController.java:77-83`
  - `frontend/src/pages/MyPage/SalesOrders.jsx`
  - `frontend/src/pages/MyPage/OrderStatus.jsx`
- 발생 조건:
  1. 낙찰 주문을 포인트로 결제한다.
  2. 판매자가 `PREPARING`을 거쳐 택배사와 송장번호를 입력하고 `SHIPPED`로 변경한다.
  3. 배송 완료 후 구매자가 구매 확정을 시도하려 한다.
- 영향:
  - 상태 정책상 구매 확정은 `DELIVERED → CONFIRMED`에서만 가능하지만 주문을 `SHIPPED → DELIVERED`로 바꾸는 API, 스케줄러 또는 프론트 동작이 없다.
  - 실제 사용자는 주문을 `CONFIRMED`까지 완료할 수 없고 주문이 `SHIPPED`에 머문다.
- 근거:
  - `OrderStatus.canTransitionTo`는 `SHIPPED`에서 `DELIVERED`만, `DELIVERED`에서 `CONFIRMED`만 허용한다.
  - `OrderStateService.markDelivered`는 존재하지만 전체 메인 소스에서 호출하는 코드가 없다.
  - 판매자 상태 API는 `PREPARING`과 `SHIPPED`만 허용한다.
  - 구매자 API의 구매 확정은 바로 `OrderStateService.confirm`을 호출하므로 `SHIPPED` 상태에서는 충돌 응답이 발생한다.
  - `OrderStateServiceTest`는 테스트 코드가 `markDelivered`를 직접 호출한 뒤 구매 확정을 수행하므로 실제 API 경로 부재를 검출하지 못한다.
- 재현 또는 검증 방법:
  - API 통합 테스트에서 결제, 판매자 준비, 발송 후 구매자 `/confirm`을 호출하면 `ORDER_STATUS_CONFLICT`가 반환되는지 확인한다.
  - 애플리케이션의 모든 컨트롤러·스케줄러 빈에서 `markDelivered` 호출자가 없음을 확인한다.
- 권장 수정안:
  - 배송 완료의 신뢰 주체와 정책을 먼저 정한 뒤 택배 조회 이벤트, 판매자 처리, 구매자 수령 확인 또는 관리자 처리 중 하나의 명시적 `DELIVERED` 전환 경로를 제공한다.
  - 선택한 실제 API 경로로 발송부터 구매 확정까지 수행하는 통합 테스트를 추가한다.
- 판정: 확정된 결함

#### QA-002 — 결제 후 환불 구현이 실제 API·화면 흐름에 연결되지 않음

- 심각도: 중간
- 관련 파일과 코드 위치:
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/OrderStateService.java:66-94`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/OrderPointLedgerService.java:63-85`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/controller/OrderController.java:68-93`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/controller/SellerOrderController.java:56-74`
  - `frontend/src/api/orderApi.js`
  - `frontend/src/pages/MyPage/OrderStatus.jsx:572,609-610`
  - `frontend/src/pages/MyPage/SalesOrders.jsx:353`
- 발생 조건:
  - 구매자가 구매 확정 전 주문의 환불을 요청하고 판매자가 승인하려 한다.
- 영향:
  - 서비스 계층에는 포인트 반대 거래와 `REFUNDED` 상태 전이가 있지만 이를 호출하는 컨트롤러, 스케줄러 또는 프론트 요청이 없어 애플리케이션 사용 흐름에서 실행할 수 없다.
  - 운영자가 별도 DB 조작을 시도하면 원장 불변식을 우회할 위험이 있다.
- 근거:
  - 메인 소스에서 `OrderStateService.refund`의 호출자는 확인되지 않았다.
  - 구매자 API는 결제 전 취소, 구매 확정과 결제만 제공하고 판매자 API는 준비·발송만 제공한다.
  - 프론트는 환불 결과를 표시하지만 환불을 요청하거나 승인하는 API 함수와 동작은 없다.
  - 서비스·동시성 테스트는 환불 메서드를 직접 호출하므로 실제 진입점 부재를 보장하지 않는다.
- 재현 또는 검증 방법:
  - 실행 중인 애플리케이션의 매핑 목록에서 주문 환불 엔드포인트가 없음을 확인한다.
  - `PAID` 또는 `PREPARING` 주문을 UI와 공개 API만 사용해 `REFUNDED`로 변경할 수 있는지 시나리오 테스트한다.
- 권장 수정안:
  - 구매자 환불 요청과 판매자 승인·거절을 별도 상태로 기록하고, 승인 시 기존 포인트 반대 거래와 `REFUNDED` 전이를 호출한다.
  - `CONFIRMED` 이후 일반 환불 요청은 차단하고 요청·승인 주체, 사유와 처리 결과를 감사할 수 있게 한다.
  - 인증된 환불 진입점과 포인트 반대 거래까지 포함하는 API 통합 테스트를 작성한다.
- 판정: 확정된 결함 — 구매자 요청·판매자 승인, 구매 확정 후 일반 환불 불가 정책 확정

### 무발견 근거

- `회원가입 → 작품 등록 → 입찰 → 낙찰 주문 생성 → 배송지 확정 → 포인트 결제` 구간은 구현 호출 경로가 연속적으로 존재하며, 관련 트랜잭션·서비스·API 테스트 64건이 모두 통과했다.
- 결제 전 취소와 결제 만료는 주문 상태 변경과 포인트 예치 해제가 같은 서비스 트랜잭션 안에서 실행되고 관련 테스트가 통과했다.
- 이 무발견 판정은 이번에 확인한 코드와 H2 기반 테스트 범위에 한정한다. 권한 세부 사항과 동시성 전체 판정은 이후 해당 감사 영역에서 별도로 확정한다.

### 추가 검증이 필요한 사항

- 작가 회원가입부터 포인트 계정 생성, 작품 등록까지를 실제 HTTP 요청으로 잇는 통합 테스트가 없어 이 연결은 정적 근거와 개별 테스트로만 확인했다.
- 회원가입부터 주문 완료까지 전 구간을 하나의 시나리오로 실행하는 종단 간 테스트가 없다.
- `QA-001`은 구매자가 배송 완료를 처리한 뒤 별도로 구매 확정하는 정책으로 결정했다. 실제 택배사 API 연동은 BACKLOG로 분리한다.
- `QA-002`는 구매자 요청·판매자 승인 방식이며 구매 확정 후 일반 환불은 허용하지 않는 것으로 결정했다.
- 프론트 화면 테스트는 주문 유틸리티 9건에 한정되어 실제 컴포넌트 렌더링과 전체 사용자 조작은 보장하지 않는다.
- 로컬 npm 설치 경로 문제로 `npm test` 명령 자체는 실행되지 않았으나 동일한 Node 테스트 명령은 통과했다. 개발 환경 재현성 관점의 판정은 별도 범위에서 다룬다.

## 2단계 — 소유권과 내부 승인 API 권한 감사

### 확인한 파일

인증·권한:

- `config/SecurityConfig.java`
- `jwt/JwtAuthenticationFilter.java`
- `jwt/JwtTokenProvider.java`

주문 API와 소유권:

- `controller/OrderController.java`
- `controller/SellerOrderController.java`
- `service/OrderQueryService.java`
- `service/OrderService.java`
- `service/OrderStateService.java`
- `repository/OrderRepository.java`

포인트 API와 소유권·승인:

- `controller/PointController.java`
- `service/PointQueryService.java`
- `service/PointChargeService.java`
- `payment/InternalPointPaymentProvider.java`
- `payment/PaymentApproval.java`
- `dto/PointChargeRequestDto.java`
- `repository/PointAccountRepository.java`
- `repository/PointTransactionRepository.java`
- `repository/PointChargeRepository.java`
- `main/resources/application.properties.example`

### 확인한 테스트와 실행 결과

다음 6개 백엔드 테스트 클래스를 실행했다.

- `PointApiTest`
- `OrderManagementApiTest`
- `OrderShippingAddressApiTest`
- `OrderQueryServiceTest`
- `OrderStateServiceTest`
- `PointChargeServiceTransactionTest`

결과는 총 34건 통과, 실패 0건, 오류 0건, 제외 0건이다.

테스트가 확인한 주요 항목:

- 비인증 사용자의 주문·포인트 API 접근 거부
- 일반 회원의 작가 주문 API 접근 거부
- 다른 구매자·판매자의 주문 상세 조회와 상태 변경 거부
- 결제와 배송지 변경에서 인증 사용자 ID 전달
- 포인트 충전 요청에서 인증 사용자 ID 사용
- 서비스에 직접 전달된 `trustedInternalRequest=false` 승인 거부

### 영역별 확인 결과

#### 구매자 주문 조회·변경

- 구매자 목록 쿼리는 클라이언트가 전달한 사용자 ID가 아니라 JWT에서 만든 `AuthenticationPrincipal`을 사용하고, 저장소 쿼리 자체가 `buyer.userId`로 제한된다.
- 구매자 상세 조회는 주문의 구매자 스냅샷과 인증 사용자 ID를 비교하고 불일치 시 `ORDER_ACCESS_DENIED`를 반환한다.
- 배송지 변경, 결제 전 취소, 구매 확정과 포인트 결제는 주문 행을 조회한 후 실제 주문 구매자와 인증 사용자 ID를 비교한다.
- 이 경로에서 타인 주문을 조회하거나 변경할 수 있는 확정 결함은 발견하지 못했다.

근거:

- `OrderController.java:33-93`
- `OrderQueryService.java:34-68`
- `OrderService.java:65-103`
- `OrderStateService.java:36-63, 147-181, 193-200`
- `OrderRepository.java:40-45, 54-61`
- `OrderManagementApiTest`
- `OrderShippingAddressApiTest`
- `OrderQueryServiceTest`
- `OrderStateServiceTest`

#### 판매자 주문 조회·변경

- `/api/artists/**`는 보안 설정에서 `ROLE_ARTIST`로 제한된다.
- 판매자 목록 쿼리는 인증 사용자 ID와 `seller.userId`를 결합해 조회한다.
- 판매자 상세 조회는 판매자 스냅샷을 인증 사용자와 비교한다.
- 준비·발송 상태 변경 서비스는 주문의 실제 판매자와 인증 사용자 ID를 다시 비교한다.
- 역할 제한과 주문별 소유권 검사가 함께 적용되므로, 이 경로에서 타인 판매 주문 접근 확정 결함은 발견하지 못했다.

근거:

- `SecurityConfig.java:75-84`
- `SellerOrderController.java:24-78`
- `OrderQueryService.java:70-104`
- `OrderStateService.java:96-132, 203-210`
- `OrderRepository.java:47-52, 63-70`
- `OrderManagementApiTest`
- `OrderQueryServiceTest`
- `OrderStateServiceTest`

#### 포인트 잔액·거래·충전 내역

- 포인트 API 경로는 사용자 식별자를 요청 파라미터나 본문으로 받지 않고 모두 인증 사용자 ID를 사용한다.
- 잔액은 인증 사용자 ID를 기본키로 조회하고, 거래·충전 목록 저장소 쿼리는 같은 ID를 조건으로 사용한다.
- 충전 생성도 인증 사용자 ID로 포인트 계정을 잠그고 사용자별 멱등 키를 조회한다.
- 정적 호출 경로상 다른 사용자의 포인트 내역을 지정해 조회하는 IDOR 경로는 발견하지 못했다.

근거:

- `PointController.java:26-53`
- `PointQueryService.java:25-46`
- `PointTransactionRepository.java:20-22`
- `PointChargeRepository.java:16-18, 25-27`
- `PointChargeService.java:39-59`
- `PointApiTest`

#### 데모 내부 충전의 표시와 발행 통제

- 실제 PG 연동 전 `INTERNAL` 결제 제공자만 활성화한다는 구현 범위는 문서에서 확인된다.
- 운영 포트폴리오에서 데모 충전을 제공하는 것은 의도된 동작이지만, 아래 `QA-003`과 같이 데모임을 알리고 발행량을 통제하는 장치는 구현되어 있지 않다.

### 발견사항

#### QA-003 — 데모 충전 표시와 발행량 통제 장치가 없음

- 심각도: 높음
- 관련 파일과 코드 위치:
  - `backend/src/main/java/com/dailyatelier/dailyatelier/config/SecurityConfig.java:75-84`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/controller/PointController.java:47-62`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/payment/PaymentApproval.java:5-10`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/payment/InternalPointPaymentProvider.java:13-24`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/PointChargeService.java:62-89`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/dto/PointChargeRequestDto.java:5`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/dto/PointChargeResponseDto.java:6-22`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/entity/PointTransactionType.java:3-12`
  - `backend/src/main/resources/application.properties.example:1-26`
  - `backend/src/test/resources/application.properties:1-11`
  - `frontend/src/pages/MyPage/Charge.jsx:14-17,83-111,181-226`
  - `PLAN_DONE.md:232-243,395-406,598-623,684-705`
  - `BACKLOG.md:35-47`
  - `backend/src/test/java/com/dailyatelier/dailyatelier/controller/PointApiTest.java:55-83`
- 발생 조건:
  1. 방문자가 배포된 포트폴리오에서 유효한 계정으로 인증한다.
  2. UI의 직접 입력 또는 API 요청으로 1,000 이상인 임의 금액과 새 `Idempotency-Key`를 전송한다.
  3. 보유 잔액이나 누적 충전량과 관계없이 데모 포인트 충전이 즉시 승인된다.
- 영향:
  - 배포된 포트폴리오에서 데모 충전 API가 활성화되는 것 자체는 방문자의 경매·주문·포인트 결제 체험을 위한 의도된 동작이다.
  - 그러나 API 응답과 화면이 실제 금전 결제가 없는 데모 포인트임을 명확히 표시하지 않아 실제 결제 기능으로 오인될 수 있다.
  - 서버가 고정 충전 금액, 계정별 최대 잔액 또는 재충전 기준을 강제하지 않아 새 멱등 키를 사용한 반복 호출로 과도한 포인트를 발행할 수 있다.
  - 데모 충전도 일반 `CHARGE` 유형으로 기록되어 이후 실제 PG 충전과 원장·운영 조회에서 구분되지 않는다.
- 근거:
  - 임시 기능 의도는 `PLAN_DONE.md`의 “실제 네이버페이·토스페이 API는 이번 단계에서 제외”, “이번 구현에서는 `INTERNAL` 구현체만 활성화”, “`Charge.jsx`를 내부 충전 흐름에 연결”이라는 범위에서 확인된다.
  - 실제 PG 연동은 `BACKLOG.md`의 네이버페이·토스페이 후속 항목으로 명시되어 있다.
  - 운영 포트폴리오에서 일반 인증 방문자가 데모 충전을 사용할 수 있는 것은 사용자 요구사항에 따른 의도된 공개 범위다.
  - 요청 DTO에는 최소 1,000 조건만 있고 서버가 허용하는 고정 선택지, 1회 상한, 계정별 최대 보유 포인트 또는 잔액 기준 재충전 조건이 없다.
  - 프론트에는 10,000~300,000의 선택지가 있지만 직접 입력도 허용하므로 클라이언트 선택지는 서버 통제 수단이 아니다.
  - 프론트 화면은 `내부 포인트 충전`이라고 표시할 뿐 실제 금전 결제가 없는 데모라는 안내가 없고, 실제 결제 금액·환불·미성년자 동의 문구와 함께 일반 결제 화면처럼 노출한다.
  - 충전 응답 DTO에는 데모 여부나 결제 성격을 나타내는 필드가 없다.
  - 원장 유형은 `CHARGE` 하나이며 `DEMO_CHARGE`처럼 실제 PG 충전과 구분되는 유형이 없다.
  - 동일 멱등 키의 재호출과 다른 요청 재사용 충돌은 이미 구현되어 있어 같은 요청의 네트워크 재시도로 인한 중복 적립은 방지한다. 다만 사용자가 새 키로 반복하는 별도 충전까지 제한하지는 않는다.
  - `PointPaymentProvider`와 `PaymentApproval` 경계는 존재하므로 향후 실제 PG 구현으로 교체할 구조적 기반은 유지되어 있다.
  - `PointApiTest`는 별도 프로필이나 권한 없이 일반 인증 사용자의 요청이 `200 OK`, `PAID`가 되는 것을 정상 동작으로 단언한다.
- 재현 또는 검증 방법:
  - 일반 회원으로 프론트 선택지에 없는 금액을 직접 API로 요청하고 승인되는지 확인한다.
  - 새 멱등 키로 충전을 반복해 계정 잔액이 제한 없이 증가하는지 확인한다.
  - 같은 멱등 키의 같은 요청은 한 번만 적립되고, 같은 키의 다른 금액은 충돌하는지 기존 테스트와 함께 확인한다.
  - 충전 응답과 원장 거래에서 데모 충전임을 식별할 수 있는 값이 없는지 확인한다.
- 권장 수정안:
  - API 응답에 데모 충전 여부와 실제 금전 결제가 없다는 식별 정보를 포함하고 화면의 결제수단·완료·유의사항에도 이를 명확히 표시한다.
  - 서버가 허용하는 1회 충전 금액을 고정된 선택지로 제한하고 클라이언트 직접 입력을 신뢰하지 않는다.
  - 계정별 최대 보유 포인트 한도를 원자적으로 검사하며, 필요하면 잔액이 정한 기준 이하일 때만 재충전을 허용한다.
  - 현재 멱등성 키 기반 중복 방지를 유지하고 고정 키 재사용·동시 요청 테스트를 보강한다.
  - 원장에 `DEMO_CHARGE` 등 명시적인 거래 유형과 설명을 기록해 향후 실제 PG `CHARGE`와 구분한다.
  - 현재 결제 승인 인터페이스를 유지해 실제 PG 연동 시 데모 구현을 교체할 수 있게 한다.
  - IP·계정별 과도한 반복 호출에 대한 요청 제한과 데모 데이터 운영 정책을 검토한다.
  - 고정 금액, 최대 잔액, 재충전 기준과 동시 요청 제한을 검증하는 API·서비스 테스트를 추가한다.
- 판정: 확정된 결함

### 무발견 근거

- 주문 목록은 구매자·판매자별 저장소 조건으로 필터링되고, 상세·변경은 서비스에서 인증 사용자와 주문 당사자를 다시 비교한다.
- 작가 주문 API는 역할 검사와 주문 판매자 검사를 모두 적용한다.
- 포인트 잔액·거래·충전 내역 API는 외부 사용자 식별자를 입력받지 않고 인증 사용자 ID만 저장소 조건으로 사용한다.
- 관련 API·서비스 테스트 34건이 모두 통과했으며 타인 주문 조회, 결제, 준비·발송 시도의 거부가 포함된다.
- 위 무발견 판정은 소유권 경로에 한정하며 `QA-003`의 데모 충전 표시·발행 통제 문제는 별도 확정 결함이다.

### 추가 검증이 필요한 사항

- `PointApiTest`는 잔액과 충전 생성만 직접 확인하고 거래 목록·충전 목록이 다른 사용자 데이터를 반환하지 않는 API 테스트는 없다. 현재 구현은 인증 사용자 ID로 필터링되지만, 회귀 방지를 위해 두 사용자의 데이터를 만든 저장소·API 통합 테스트가 필요하다.
- JWT 역할은 토큰에 들어 있는 `userStatus`로 결정되고 요청 시 DB의 현재 상태를 재조회하지 않는다. 계정 역할 변경·정지·탈퇴 후 기존 토큰을 즉시 무효화해야 하는 정책인지 확인한 뒤 토큰 폐기 또는 사용자 상태 재검증 필요성을 판단해야 한다.
- `OrderStateService.markPaid(orderId)`와 `refund(orderId, ...)`처럼 사용자 ID를 받지 않는 서비스 오버로드는 현재 공개 Controller에서 호출되지 않는다. 향후 새 진입점이 생길 때 반드시 내부 호출 권한 또는 당사자 검증을 강제해야 한다.
- 실제 네이버페이·토스페이 연동은 기존 `BACKLOG.md`의 주문·결제 후속 고도화 대상으로 유지한다.
- 데모 표시, 고정 충전 선택지, 계정별 최대 보유 한도, 선택적 재충전 기준, `DEMO_CHARGE` 원장 구분과 반복 호출 제한은 후속 개선 대상으로 분류한다. 실제 PG 연동 시에는 현재 결제 승인 인터페이스를 유지하면서 데모 승인 구현을 교체한다.

## 3단계 — 트랜잭션 경계와 동시성 감사

### 확인한 파일

경매·주문·포인트 구현:

- `service/BidService.java`
- `service/AuctionCloseService.java`
- `service/AuctionCloseScheduler.java`
- `service/OrderService.java`
- `service/OrderStateService.java`
- `service/OrderPointLedgerService.java`
- `service/OrderExpirationService.java`
- `service/OrderExpirationScheduler.java`
- `service/ArtService.java`
- `repository/ArtRepository.java`
- `repository/OrderRepository.java`
- `repository/PointAccountRepository.java`
- `repository/PointHoldRepository.java`
- `repository/PointTransactionRepository.java`
- `entity/Order.java`
- `entity/OrderStatus.java`
- `entity/PointAccount.java`
- `entity/PointHold.java`
- `entity/PointTransaction.java`

동시성·트랜잭션 테스트:

- `AuctionCloseServiceConcurrencyTest`
- `AuctionCloseOrderRollbackTest`
- `BidServiceConcurrencyTest`
- `BidServiceTransactionTest`
- `OrderStateConcurrencyTest`
- `OrderExpirationServiceTest`
- `ArtServiceMutationTransactionTest`
- `PointLedgerMySqlConcurrencyTest`
- `PointLedgerMySqlSchemaTest`
- `OrderMySqlSchemaTest`

### 실행한 테스트와 결과

H2 기반 서비스·트랜잭션·동시성 테스트 7개 클래스를 실행했다.

- `AuctionCloseServiceConcurrencyTest`
- `AuctionCloseOrderRollbackTest`
- `BidServiceConcurrencyTest`
- `BidServiceTransactionTest`
- `OrderStateConcurrencyTest`
- `OrderExpirationServiceTest`
- `ArtServiceMutationTransactionTest`

결과는 총 31건 통과, 실패 0건, 오류 0건, 제외 0건이다.

MySQL 전용 테스트는 `DAILYATELIER_MYSQL_SCHEMA_TEST=true`와 실제 MySQL 연결이 필요한 조건부 테스트다. 현재 감사 실행 환경에는 해당 환경 변수가 설정되지 않아 이번 단계에서 실행하지 않았다. `PLAN_DONE.md`에는 2026-07-31 기준 MySQL 8.0의 마이그레이션·제약·`REPEATABLE-READ`·계정 행 잠금 직렬화·교착 감지와 롤백을 확인한 이력이 있으나, 이번 감사의 직접 실행 결과와는 구분한다.

### 영역별 확인 결과

#### 입찰과 경매 마감

- 입찰은 `BidService.createBid`의 서비스 트랜잭션 안에서 작품 행을 비관적 쓰기 잠금으로 먼저 획득한다.
- 같은 작품의 입찰, 작품 수정·삭제와 경매 마감은 모두 같은 작품 행 잠금을 선행하므로 작품 단위로 직렬화된다.
- 여러 계정이 관련된 입찰은 사용자 ID를 정렬한 순서로 포인트 계정 행을 잠가 서로 반대 순서로 계정을 잡는 교착 가능성을 낮춘다.
- 입찰 저장, 포인트 예치·해제 원장과 작품 현재가 변경은 같은 트랜잭션 안에서 처리된다.
- 경매 마감은 작품 행을 잠근 뒤 최고 입찰·현재가·활성 예치 정합성을 확인하고 작품을 낙찰 상태로 바꾼 후 주문을 생성한다.
- `OrderService.createForSoldAuction`의 기본 트랜잭션 전파는 호출 중인 경매 마감 트랜잭션에 참여하므로 주문 생성 실패 시 작품의 낙찰 상태도 함께 롤백된다.
- 동시 마감은 작품 행 잠금으로 직렬화되고, 주문의 작품별 유니크 제약과 기존 주문 조회가 중복 주문을 추가로 방지한다.

근거:

- `BidService.java:58-104,107-123,215-234`
- `AuctionCloseService.java:29-106`
- `OrderService.java:34-63`
- `ArtRepository.java:26-34`
- `OrderRepository.java:18-26`
- `AuctionCloseServiceConcurrencyTest`
- `AuctionCloseOrderRollbackTest`
- `BidServiceConcurrencyTest`
- `BidServiceTransactionTest`
- `ArtServiceMutationTransactionTest`

이 구간에서 부분 커밋, 중복 주문 또는 마감·입찰 경합으로 상태가 역전되는 확정 결함은 발견하지 못했다.

#### 주문 결제와 포인트 차감

- 결제는 주문 행을 먼저 비관적 쓰기 잠금으로 조회하므로 같은 주문의 결제, 만료, 포기와 환불이 주문 상태 확인 전에 직렬화된다.
- 주문 상태·결제 기한·배송지를 확인한 뒤 포인트 계정과 낙찰 예치를 잠그고, 예치 금액 차감, `COMMIT` 원장 저장, 예치 확정과 주문 `PAID` 변경을 같은 서비스 트랜잭션에서 처리한다.
- 원장 저장에 `saveAndFlush`를 사용해 DB 제약 위반이 주문 상태 변경 전에 현재 트랜잭션으로 전파되며, 런타임 예외 시 계정·예치·주문 변경이 함께 롤백된다.
- 두 결제가 동시에 실행되면 첫 요청만 `COMMIT`을 만들고, 대기하던 요청은 잠금 획득 후 이미 `PAID`인 주문을 반환한다.

근거:

- `OrderStateService.java:25-64,184-190`
- `OrderPointLedgerService.java:22-38,87-108`
- `OrderRepository.java:23-26`
- `PointAccountRepository.java:14-16`
- `PointHoldRepository.java:19-21`
- `OrderStateConcurrencyTest`
- 기존 1단계에서 실행한 `OrderStateServiceTest`

이 구간에서 주문 `PAID`와 포인트 `COMMIT`이 분리되거나 같은 결제가 두 번 차감되는 확정 결함은 발견하지 못했다.

#### 결제 만료·포기·환불 경합

- 결제 만료와 구매자 포기는 결제와 같은 주문 행 잠금을 선행한다.
- 만료 또는 포기가 먼저 커밋되면 대기하던 결제는 `CANCELED` 상태를 다시 읽고 거부된다.
- 결제가 먼저 커밋되면 만료 처리는 `PAYMENT_PENDING`이 아니므로 멱등하게 건너뛴다.
- 예치 해제와 주문 `CANCELED` 전이는 같은 트랜잭션에서 처리된다.
- 환불도 주문 행 잠금 후 `COMMITTED` 예치와 원 결제 원장을 확인하고 반대 거래와 주문 `REFUNDED`를 같은 트랜잭션에서 처리하며, 동시 환불은 한 번만 잔액을 복원한다.

근거:

- `OrderExpirationService.java:21-48`
- `OrderStateService.java:66-94,160-181`
- `OrderPointLedgerService.java:40-85`
- `OrderStateConcurrencyTest`
- `OrderExpirationServiceTest`

이 구간에서 결제와 만료·포기가 모두 성공하거나 예치 해제·환불이 중복 적용되는 확정 결함은 발견하지 못했다.

#### 잠금 순서

- 주문 결제·만료·포기·환불은 `주문 → 포인트 계정 → 포인트 예치` 순서다.
- 입찰은 `작품 → 정렬된 포인트 계정` 순서로 잠그며 활성 예치 변경은 작품 잠금 아래에서 처리한다.
- 작품 취소는 `작품 → 포인트 예치 → 포인트 계정` 순서로 주문 계열과 계정·예치 순서가 반대지만, 현재 상태 정책상 작품 취소는 활성 경매에만 적용되고 주문 계열 처리는 낙찰된 작품에만 적용되므로 같은 작품·예치를 두 경로가 동시에 처리하는 호출 조건은 확인되지 않았다.
- 포인트 충전은 충전 또는 계정 행만 잠그고 작품·주문·예치 잠금을 추가로 기다리지 않아 현재 호출 그래프에서 순환 대기는 확인되지 않았다.

이 잠금 순서 대조에서 현재 재현 가능한 교착 경로는 발견하지 못했다. 다만 향후 작품 취소와 주문 처리 정책이 확장되면 계정·예치 잠금 순서를 하나로 통일해야 한다.

### 발견사항

- 확정된 결함 없음.
- 현재 구현에서 재현 가능한 잠재 동시성 위험 없음.

### 무발견 근거

- 경매·입찰 계열은 작품 행, 주문 상태 계열은 주문 행을 경합의 선행 잠금으로 사용한다.
- 포인트 계정과 예치 변경은 상위 트랜잭션에 참여하고 원장 `flush` 실패가 호출자까지 전파된다.
- 주문 생성 실패 롤백, 입찰 단계별 저장 실패 롤백, 동시 마감의 단일 주문, 같은 계정의 다중 작품 초과 예치 방지, 중복 결제·환불, 결제와 만료·포기 경합을 직접 검증하는 31건이 모두 통과했다.
- 테스트는 외부 테스트 트랜잭션을 끈 상태에서 별도 스레드와 실제 서비스 프록시를 사용하므로 단순 단위 테스트보다 실제 커밋 경계를 가깝게 검증한다.

### 추가 검증이 필요한 사항

- 이번 실행은 H2 기반 서비스 동시성 테스트다. 실제 MySQL에서 서비스 전체 호출의 잠금 대기·교착·롤백이 같은 결과를 내는지는 조건부 MySQL 테스트 환경에서 다시 확인해야 한다.
- `PointLedgerMySqlConcurrencyTest`는 MySQL 행 잠금과 교착 롤백을 JDBC 수준에서 검증하지만 경매·주문 서비스 전체를 MySQL에서 호출하지는 않는다.
- 실제 배포 환경의 비관적 락 타임아웃과 `409 BID_CONFLICT` 목표 대기시간 실측은 기존 `BACKLOG.md` 대상이며 이번 감사에서 실행하지 않았다.
- 동시 입찰 부하 테스트와 실행 계획 검증도 기존 `BACKLOG.md` 범위로 유지한다.
- 향후 차순위 낙찰 승계, 결제 후 작품 상태 변경 또는 관리자 보정 경로가 추가되면 현재 분리된 작품·주문 잠금 그래프와 계정·예치 잠금 순서를 다시 감사해야 한다.

## 4단계 — 결제 콜백 멱등성과 원장 불일치 감사

### 확인한 파일

콜백 inbox:

- `service/PaymentCallbackService.java`
- `entity/PaymentCallbackEvent.java`
- `entity/PaymentCallbackStatus.java`
- `repository/PaymentCallbackEventRepository.java`
- `payment/PaymentCallbackProcessor.java`
- `resources/db/migration/V3__create_payment_callback_inbox.sql`

충전과 원장:

- `service/PointChargeService.java`
- `service/PointLedgerConsistencyService.java`
- `payment/PointPaymentProvider.java`
- `payment/InternalPointPaymentProvider.java`
- `entity/PointAccount.java`
- `entity/PointTransaction.java`
- `entity/PointTransactionType.java`
- `entity/PointHold.java`
- `entity/PointCharge.java`
- `repository/PointAccountRepository.java`
- `repository/PointTransactionRepository.java`
- `repository/PointHoldRepository.java`
- `repository/PointChargeRepository.java`
- `resources/db/migration/V1__create_point_ledger.sql`

관련 테스트:

- `PaymentCallbackServiceTest`
- `PointLedgerConsistencyServiceTest`
- `PointChargeServiceTransactionTest`
- `PointChargeServiceConcurrencyTest`
- `PointLedgerConstraintTest`

### 실행한 테스트와 결과

위 5개 테스트 클래스를 실행했다. 결과는 총 13건 통과, 실패 0건, 오류 0건, 제외 0건이다.

확인된 보장 범위:

- 같은 콜백 이벤트 ID와 같은 payload hash 재수신 시 기존 이벤트 반환
- 같은 이벤트 ID의 다른 payload hash 거부
- 처리 완료 이벤트의 처리기 재실행 방지
- 실패 콜백의 최대 5회 재시도 제한
- 충전 승인·환불의 단일 원장 반영과 실패 롤백
- 같은 충전 동시 승인 시 잔액과 `CHARGE` 원장 한 번만 증가
- 계정 잔액과 원장 델타 합계 불일치 탐지
- 원장 멱등 키와 충전 업무 키의 DB 유니크 제약

### 영역별 확인 결과

#### 콜백 수신 멱등성

- `(provider, provider_event_id)` 유니크 제약이 있어 같은 공급자의 같은 이벤트가 두 행으로 저장되는 것은 DB에서 방지된다.
- 순차 재수신은 기존 행의 payload hash를 비교해 같은 원문이면 기존 이벤트를 반환하고 다른 원문이면 거부한다.
- 아래 `QA-006`처럼 최초 수신이 동시에 들어오는 check-then-insert 경합은 기존 테스트가 다루지 않으며 서비스가 유니크 충돌을 멱등 결과로 변환하지 않는다.

#### 콜백 처리 멱등성과 실패 경계

- 처리는 콜백 이벤트 행을 비관적 쓰기 잠금으로 읽으므로 같은 이벤트의 동시 처리기는 직렬화된다.
- 이미 `PROCESSED`인 이벤트는 처리기를 다시 호출하지 않고, 실패 횟수가 5회에 도달하면 재처리를 차단한다.
- 아래 `QA-004`처럼 처리기와 inbox 상태를 같은 트랜잭션에서 실행하면서 처리기 예외를 내부에서 잡는 구조는 실제 DB 변경 처리기가 연결될 때 실패 원자성을 보장하지 못할 수 있다.

#### 충전 승인과 원장 멱등성

- 충전 승인은 충전 행을 먼저 잠그고 `PENDING`에서만 계정 적립과 `CHARGE` 원장을 생성한다.
- 이미 `PAID`인 충전은 승인 정보가 같을 때 기존 결과를 반환한다.
- 계정 적립, 원장 저장과 충전 `PAID` 전이는 같은 트랜잭션이며 원장 `saveAndFlush` 실패 시 계정과 충전 상태가 함께 롤백된다.
- 충전 생성의 `(user_id, idempotency_key)`, 원장의 전역 `idempotency_key`, 외부 결제의 `(provider, pg_order_id)` 유니크 제약이 중복 반영을 방지한다.
- 이 구간에서 현재 내부 데모 충전의 같은 승인 또는 환불이 두 번 원장에 기록되는 결함은 발견하지 못했다.

#### 정합성 검사 범위

- 아래 `QA-005`와 같이 현재 정합성 검사는 포인트 계정의 현재 잔액과 원장 델타 합계만 비교한다.
- 활성 예치 합계, 주문 결제 상태와 `COMMIT`, 충전 상태와 `CHARGE`, 환불 원거래와 반대 거래 등의 의미적 연결은 검사하지 않는다.

### 발견사항

#### QA-004 — 콜백 처리기 예외를 같은 트랜잭션에서 흡수해 실패 원자성이 불명확함

- 심각도: 높음
- 관련 파일과 코드 위치:
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/PaymentCallbackService.java:39-55`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/payment/PaymentCallbackProcessor.java:5-8`
  - `backend/src/test/java/com/dailyatelier/dailyatelier/service/PaymentCallbackServiceTest.java:59-75`
- 발생 조건:
  - 향후 실제 PG 콜백 처리기가 충전·계정·원장 등 DB 상태를 변경한 뒤 런타임 예외를 던진다.
- 영향:
  - 처리기가 현재 콜백 트랜잭션 안에서 직접 DB를 변경한 뒤 예외를 던지면 `PaymentCallbackService`가 예외를 잡아 정상 반환하므로 처리기의 부분 변경과 콜백 `FAILED`가 함께 커밋될 수 있다.
  - 반대로 처리기가 별도 Spring 트랜잭션 프록시의 기본 `REQUIRED` 메서드를 호출해 예외가 트랜잭션 경계를 통과하면 공유 트랜잭션이 rollback-only가 될 수 있다. 이 경우 예외를 잡아 `FAILED`로 변경해도 최종 커밋에서 전체 롤백되어 실패 횟수와 오류가 남지 않을 수 있다.
  - 어느 경우든 외부 결제 처리 결과와 inbox 재시도 상태가 어긋날 가능성이 있다.
- 근거:
  - `process` 전체가 하나의 `@Transactional`이고 처리기 호출, 성공·실패 상태 기록이 같은 트랜잭션에 있다.
  - `RuntimeException`을 메서드 밖으로 다시 던지지 않고 내부에서 `event.failed`로 변환한다.
  - 처리기 인터페이스는 트랜잭션 전파나 실패 시 변경 금지 계약을 정의하지 않는다.
  - 현재 테스트 처리기는 DB를 변경하지 않고 즉시 예외만 던지므로 두 트랜잭션 시나리오를 검증하지 않는다.
  - 아직 실제 PG 콜백 Controller와 구체 처리기가 없어 실제 부분 커밋은 재현된 상태가 아니다.
- 재현 또는 검증 방법:
  - 테스트 처리기가 관리 엔티티 또는 별도 테이블을 변경한 후 직접 예외를 던지게 하고 변경이 커밋되는지 확인한다.
  - `@Transactional` 서비스 빈을 처리기에서 호출해 DB 변경 후 예외를 던지게 하고 `UnexpectedRollbackException`과 콜백 시도 횟수 보존 여부를 확인한다.
- 권장 수정안:
  - 업무 처리 트랜잭션과 실패 inbox 기록 트랜잭션의 경계를 명시적으로 설계한다.
  - 성공 시 업무 변경과 `PROCESSED`를 한 트랜잭션으로 원자 처리하고, 실패 시 업무 변경은 롤백한 뒤 `FAILED`·시도 횟수는 별도의 새 트랜잭션으로 기록한다.
  - 실제 충전 처리기를 연결한 통합 테스트로 성공, 중간 실패, rollback-only와 재시도를 검증한다.
- 판정: 잠재 위험

##### 후속 처리 결과

- 상태: 해결
- 구현 커밋: `b6a5399 fix(payment): 콜백 실패 트랜잭션 경계 명확화`
- 업무 처리 트랜잭션과 실패 상태 기록 트랜잭션을 분리해 처리기 실패 시 업무 변경은 롤백하고 `FAILED` 상태·시도 횟수·오류는 별도 트랜잭션으로 기록하도록 변경했다.
- H2 기반 `PaymentCallbackServiceTest`에서 처리기의 DB 변경 후 예외 시 변경 롤백, 실패 상태 커밋과 후속 재처리 성공을 검증했다.
- 실제 PG 콜백 진입점, MySQL 동시 최초 수신과 외부 응답 계약은 검증하지 않았으며 `QA-006`과 함께 BACKLOG로 유지한다.

#### QA-005 — 원장 정합성 검사가 계정 잔액 합계 외의 의미적 불일치를 탐지하지 못함

- 심각도: 중간
- 관련 파일과 코드 위치:
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/PointLedgerConsistencyService.java:16-23`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/repository/PointTransactionRepository.java:29-61`
  - `backend/src/test/java/com/dailyatelier/dailyatelier/service/PointLedgerConsistencyServiceTest.java:61-92`
  - `backend/src/main/resources/db/migration/V1__create_point_ledger.sql:20-124`
- 발생 조건:
  - 계정 잔액과 원장 델타 합계는 일치하지만 예치·주문·충전 상태 또는 원장 참조 관계가 잘못된 데이터가 존재한다.
- 영향:
  - `inspect().consistent()`가 참이어도 활성 예치 합계와 `held_balance`가 다르거나, `PAID` 주문에 `COMMIT`이 없거나, `PAID` 충전에 올바른 `CHARGE`가 없는 상태를 놓칠 수 있다.
  - 계정 행이 없고 사용자와 원장만 남은 경우에도 현재 쿼리는 `point_account`에서 시작하므로 해당 사용자를 보고하지 않는다.
  - 운영자가 잔액 합계 검사만으로 전체 포인트 원장 정합성이 보장된다고 오판할 수 있다.
- 근거:
  - `PointLedgerConsistencyService`는 `findLedgerMismatches()` 결과만 반환한다.
  - 해당 쿼리는 계정별 `available_balance`, `held_balance`와 원장의 `available_delta`, `held_delta` 합계만 비교한다.
  - `point_hold`, `point_charge`, `orders`, 거래 유형·참조·반대 거래 관계는 쿼리에 포함되지 않는다.
  - 테스트도 계정 잔액을 직접 변조해 합계 차이를 검출하는 경우만 확인한다.
- 재현 또는 검증 방법:
  - 계정의 `held_balance`와 원장 `held_delta` 합계는 유지한 채 활성 `point_hold` 상태나 금액만 불일치하게 만든 뒤 검사가 통과하는지 확인한다.
  - `PAID` 주문 또는 충전의 참조 원장을 의미적으로 맞지 않게 구성하고 검사 결과를 확인한다.
  - 포인트 계정 행을 제거할 수 있는 테스트 데이터에서 사용자·원장만 남긴 뒤 보고 여부를 확인한다.
- 권장 수정안:
  - 계정·원장 합계 검사를 유지하면서 활성 예치 합계, 예치와 작품의 활성 참조, 주문 상태와 `COMMIT`·환불, 충전 상태와 `CHARGE`·환불 관계를 별도 검사 항목으로 확장한다.
  - 보고서가 검사 종류별 불일치와 식별자를 제공하도록 하고 각 의미적 불일치를 만든 테스트를 추가한다.
  - 운영 조회 SQL도 같은 범위를 반영한다.
- 판정: 확정된 결함

#### QA-006 — 동시 최초 콜백 수신의 유니크 충돌을 멱등 결과로 복구하지 않음

- 심각도: 낮음
- 관련 파일과 코드 위치:
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/PaymentCallbackService.java:23-36`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/entity/PaymentCallbackEvent.java:11-14`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/repository/PaymentCallbackEventRepository.java:13-24`
  - `backend/src/main/resources/db/migration/V3__create_payment_callback_inbox.sql:1-18`
  - `backend/src/test/java/com/dailyatelier/dailyatelier/service/PaymentCallbackServiceTest.java:35-49`
- 발생 조건:
  - 같은 공급자·이벤트 ID의 최초 콜백 두 건이 기존 행 조회를 동시에 완료한 뒤 각각 insert를 시도한다.
- 영향:
  - 유니크 제약으로 중복 행은 방지되지만 한 요청은 데이터 무결성 예외로 실패할 수 있다.
  - 콜백 Controller가 이를 성공 응답으로 변환하지 않으면 PG가 이미 저장된 이벤트를 계속 재전송할 수 있다.
- 근거:
  - `receive`는 잠금 없는 조회 후 insert하는 check-then-act 구조다.
  - 유니크 충돌을 잡아 기존 행을 다시 조회하고 payload hash를 검증하는 복구 경로가 없다.
  - 기존 테스트는 두 수신을 순차 호출하며 동시 최초 수신을 검증하지 않는다.
  - 아직 실제 PG 콜백 Controller가 없어 외부 응답 동작은 확정되지 않았다.
- 재현 또는 검증 방법:
  - 두 스레드가 같은 이벤트를 동시에 `receive`하도록 시작 장벽을 두고 한 요청에서 유니크 예외가 발생하는지 확인한다.
  - 실제 MySQL에서 같은 경합과 Controller 응답 코드를 확인한다.
- 권장 수정안:
  - insert 유니크 충돌 시 기존 이벤트를 다시 조회하고 payload hash가 같으면 멱등 성공으로 반환한다.
  - payload가 다르면 기존 충돌 정책대로 거부하고 보안·운영 로그를 남긴다.
  - 동시 수신 통합 테스트와 PG 성공 응답 계약을 추가한다.
- 판정: 잠재 위험

### 무발견 근거

- 콜백 이벤트 처리에는 행 잠금과 완료 상태 단락이 있어 순차·동시 재처리에서 완료 처리기를 다시 실행하지 않는 기본 구조가 있다.
- 콜백 이벤트, 충전, PG 주문번호와 원장 멱등 키에는 DB 유니크 제약이 존재한다.
- 충전 승인·환불은 행 잠금과 트랜잭션으로 잔액·원장·충전 상태를 함께 변경하며 관련 테스트가 통과했다.
- 계정 잔액과 원장 델타 합계 불일치는 현재 정합성 검사로 탐지된다.
- 위 무발견 근거는 `QA-005`의 의미적 정합성 범위와 `QA-006`의 동시 최초 수신을 제외한 범위다. `QA-004`는 후속 구현과 H2 자동 검증 결과를 위에 별도로 기록했다.

### 추가 검증이 필요한 사항

- 현재 메인 소스에는 실제 PG 콜백 Controller와 `PaymentCallbackProcessor` 구현체가 없다. 따라서 콜백 서명, PG 주문·금액·사용자 검증과 실제 승인 연결은 아직 검증할 대상이 없으며 기존 네이버페이·토스페이 연동 `BACKLOG.md` 범위다.
- 실제 PG 도입 시 `QA-004`의 분리된 트랜잭션 경계를 실제 처리기로 회귀 검증하고, `QA-006`의 동시 최초 수신을 MySQL 통합 테스트로 확정해야 한다.
- `QA-005`의 의미적 정합성 검사는 운영 조회·경보 주기와 보정 권한 정책까지 함께 설계해야 한다.

## 5단계 — Flyway 신규 DB 및 기존 DB 적용 위험 감사

### 확인한 파일

- `backend/src/main/resources/application.properties.example`
- `backend/src/test/resources/application.properties`
- `backend/src/main/resources/db/migration/V1__create_point_ledger.sql`
- `backend/src/main/resources/db/migration/V2__normalize_point_ledger_foreign_keys.sql`
- `backend/src/main/resources/db/migration/V3__create_payment_callback_inbox.sql`
- `backend/src/main/resources/db/migration/V4__link_art_active_point_hold.sql`
- `backend/src/main/resources/db/migration/V5__add_order_point_payment_method.sql`
- `backend/src/main/java/com/dailyatelier/dailyatelier/entity/User.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/entity/Art.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/entity/Order.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/BidService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/AuctionCloseService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/OrderPointLedgerService.java`

### 확인한 테스트와 검증 범위

- `PointLedgerMySqlSchemaTest`는 환경 변수 `DAILYATELIER_MYSQL_SCHEMA_TEST=true`일 때만 실행되며, 연결된 기존 DB에 `repair()`와 `migrate()`를 수행한 뒤 포인트 테이블의 일부 제약·인덱스, 계정-원장 합계와 두 번째 `migrate()`의 실행 건수 0을 확인한다.
- `OrderMySqlSchemaTest`도 같은 환경 변수에 의존하며, 주문 인덱스와 주소 우편번호 타입만 확인한다.
- 기본 테스트 설정은 Flyway를 비활성화하고 Hibernate `ddl-auto=update`를 사용한다. 따라서 일반 H2 테스트는 MySQL 전용 마이그레이션 문법, 빈 DB 부트스트랩, 레거시 데이터 이관을 검증하지 않는다.
- 이번 환경에서는 전용 MySQL 스키마 테스트 실행 조건을 확인할 수 없어 실제 적용·락 시간·장애 복구 실험은 하지 않았다. 이는 `BACKLOG.md` 대상이다.

### 발견사항

#### QA-007 — Flyway 마이그레이션만으로 빈 DB를 생성할 수 없음

- 심각도: 높음
- 관련 파일과 코드 위치:
  - `backend/src/main/resources/application.properties.example:9,14-16`
  - `backend/src/main/resources/db/migration/V1__create_point_ledger.sql:1-2,12-13,72-79`
  - `backend/src/main/resources/db/migration/V2__normalize_point_ledger_foreign_keys.sql:35-50`
  - `backend/src/main/resources/db/migration/V4__link_art_active_point_hold.sql:1-31`
  - `backend/src/main/resources/db/migration/V5__add_order_point_payment_method.sql:1-14`
- 발생 조건:
  - 테이블이 전혀 없는 신규 MySQL DB에서 운영 예시 설정처럼 Hibernate를 `validate`, Flyway를 활성화하고 애플리케이션을 시작한다.
- 영향:
  - 첫 마이그레이션이 존재하지 않는 `users` 테이블을 `ALTER`하려다 실패하므로 신규 DB를 배포 가능한 스키마로 만들 수 없다.
  - `users`, `art`, `bid`, `orders` 등 레거시 핵심 테이블이 외부 절차로 먼저 생성되어야 하지만 그 절차와 기준 스키마가 Flyway 이력에 포함되어 있지 않다.
- 근거:
  - 저장소의 Flyway 이력은 V1부터 V5까지이며 V1 첫 문장이 `ALTER TABLE users`이다.
  - V1은 새 포인트 테이블의 외래키 대상으로 `users`, `art`, `bid`, `orders`가 이미 존재한다고 전제한다.
  - V4와 V5 역시 각각 기존 `art`, `orders`를 변경할 뿐 핵심 테이블을 생성하지 않는다.
  - `baseline-on-migrate=true`는 기존 비어 있지 않은 DB의 이력 기준점을 만들 뿐, 빈 DB에 누락된 핵심 테이블을 생성하지 않는다.
  - 일반 테스트는 Flyway를 끄고 Hibernate가 스키마를 갱신하므로 이 실패를 가린다.
- 재현 또는 검증 방법:
  - 격리된 빈 MySQL 스키마에서 현재 설정과 V1~V5만으로 `flyway migrate`를 실행해 V1의 `users` 변경에서 실패하는지 확인한다.
  - 성공 기준은 마이그레이션 완료 후 `ddl-auto=validate` 애플리케이션 컨텍스트가 별도 DDL 없이 기동되는 것이다.
- 권장 수정안:
  - 현재 엔티티 전체의 기준 스키마를 만드는 초기 마이그레이션을 제공하고, 기존 운영 DB에는 별도의 안전한 baseline 전략을 문서화한다.
  - 빈 DB 전용 Testcontainers 테스트에서 전체 마이그레이션과 Hibernate 검증을 자동화한다.
- 판정: 확정된 결함

##### 후속 처리 결과

- 상태: 해결
- 기존 V0~V6을 현재 최종 스키마를 직접 생성하는 신규 V1으로 재구성했다.
- 별도 빈 MySQL 스키마에서 신규 V1 단독 적용, 14개 테이블, 일반 인덱스 9개, 유니크 9개, 외래키 26개, CHECK 9개와 조건식을 검증했다.
- 같은 실행에서 Hibernate `ddl-auto=validate` 기동, 두 번째 migrate 0건, 14개 업무 테이블 데이터 0건을 확인했다.

#### QA-008 — 기존 활성 경매·주문의 포인트 예치 관계를 이관하지 않음

- 심각도: 높음
- 관련 파일과 코드 위치:
  - `backend/src/main/resources/db/migration/V1__create_point_ledger.sql:126-186`
  - `backend/src/main/resources/db/migration/V4__link_art_active_point_hold.sql:1-47`
  - `backend/src/main/resources/db/migration/V5__add_order_point_payment_method.sql:1-14`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/BidService.java:79-92,126-203`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/AuctionCloseService.java:82-99`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/OrderPointLedgerService.java:22-38,87-106`
- 발생 조건:
  - V1 적용 전에 최고 입찰이 존재하는 진행 중 경매, 또는 결제대기·기결제 주문이 기존 DB에 남아 있다.
- 영향:
  - 기존 최고 입찰 금액은 사용자 계정의 가용 포인트에서 예치로 이동되지 않아 배포 직후 추가 사용으로 과다 약정할 수 있다.
  - 기존 경매 작품의 `active_point_hold_id`는 null인 채여서 마감 시 예치 무결성 검사에 실패한다.
  - 기존 주문은 V5에서 모두 `INTERNAL_POINT`로 표시되지만 연결된 예치·`COMMIT` 원장이 없어 결제, 만료, 취소 또는 환불 경로가 무결성 오류로 막힐 수 있다.
- 근거:
  - V1의 데이터 이관은 `users.reserve`를 계정과 `OPENING_BALANCE` 거래로 복사하는 것뿐이며 기존 입찰·주문을 읽지 않는다.
  - V4는 nullable 참조 컬럼과 제약만 추가하고 활성 최고 입찰에 대한 `point_hold` 생성이나 계정 잔액 조정을 하지 않는다.
  - V5는 기존 주문에 `INTERNAL_POINT` 기본값을 일괄 부여하지만 대응 원장이나 예치 상태를 만들지 않는다.
  - 마감 서비스는 최고 입찰과 일치하는 `HELD` 예치가 없으면 예외를 던지고, 주문 포인트 서비스는 작품의 활성 예치를 필수로 요구한다.
  - MySQL 스키마 테스트는 기존 활성 경매·주문 fixture와 업무 동작을 검증하지 않는다.
- 재현 또는 검증 방법:
  - 레거시 스키마에 잔액이 있는 사용자, 진행 중 작품과 최고 입찰, 결제대기·결제완료 주문을 각각 만든 뒤 V1~V5를 적용한다.
  - 적용 후 계정의 가용·예치 합계, 작품 활성 예치, 경매 마감, 주문 결제·만료·환불을 실행해 정합성을 확인한다.
- 권장 수정안:
  - 배포 정책상 레거시 진행 거래를 종료할지 이관할지 먼저 결정한다.
  - 이관한다면 활성 최고 입찰별 예치·원장·계정 잔액과 기존 주문별 결제 의미를 명시적으로 변환하고 사전·사후 검증 SQL을 제공한다.
  - 이관하지 않는다면 배포 전 거래 동결·정리 조건과 실패 시 복구 절차를 문서화한다.
- 판정: 잠재 위험 — 해당 상태의 기존 데이터 존재 여부를 이번 환경에서 확인하지 않음

##### 후속 처리 결과

- 상태: 현재 프로젝트 범위에서 적용 대상 없음
- 실제 운영 DB와 보존해야 할 공유 개발 DB가 없고 기존 개발 DB는 승인 후 수동 재생성하는 정책으로 확정했다.
- 따라서 활성 경매·주문 데이터 이관은 구현하지 않았으며, 이를 데이터 이관 구현 완료로 판단하지 않는다.

#### QA-009 — 기존 null `reserve` 값이 V1 적용을 중단시킬 수 있음

- 심각도: 중간
- 관련 파일과 코드 위치:
  - `backend/src/main/resources/db/migration/V1__create_point_ledger.sql:1-2,134-146`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/entity/User.java:41-43`
  - 포인트 원장 도입 전 `User.reserve` 매핑: `@Column`으로 nullable 허용
- 발생 조건:
  - 기존 `users.reserve`에 null 값이 있고 MySQL SQL 모드가 null을 자동 보정하지 않고 `NOT NULL` 변경을 거부한다.
- 영향:
  - V1이 첫 DDL에서 중단되어 이후 포인트 테이블과 데이터 이관이 적용되지 않는다.
- 근거:
  - 원장 도입 전 엔티티는 `reserve`에 `nullable=false`를 선언하지 않았고 DB 기본값도 엔티티만으로 보장되지 않았다.
  - V1은 null 값을 먼저 0으로 정규화하지 않고 즉시 `INT NOT NULL DEFAULT 0`으로 변경한다.
  - 현재 MySQL 스키마 테스트에는 null `reserve` 레거시 fixture와 SQL 모드별 적용 검증이 없다.
- 재현 또는 검증 방법:
  - V1 이전 스키마의 사용자 한 명에 `reserve=null`을 저장하고 운영과 같은 MySQL 버전·SQL 모드에서 V1을 적용한다.
- 권장 수정안:
  - 제약 강화 전 `UPDATE users SET reserve=0 WHERE reserve IS NULL`을 명시하고, null 건수 사전 점검과 이관 후 검증을 추가한다.
- 판정: 잠재 위험 — 기존 null 데이터와 운영 SQL 모드를 확인하지 않음

##### 후속 처리 결과

- 상태: 현재 프로젝트 범위에서 적용 대상 없음
- 신규 V1이 `users.reserve`를 처음부터 `INT NOT NULL DEFAULT 0`으로 생성한다.
- 레거시 DB를 업그레이드하지 않으므로 기존 null `reserve`를 사전 정규화하는 요구는 적용되지 않는다.

### 무발견 근거

- V1의 계정·기초 잔액 insert는 `NOT EXISTS`와 고정 멱등 키를 사용하므로 해당 DML 구간이 재실행될 때 중복 계정·기초 거래를 만들지 않는 구조다.
- V2, V4, V5는 대상 외래키·컬럼·유니크 인덱스 존재 여부를 `information_schema`에서 확인해 이미 적용된 동일 변경을 다시 요청하지 않는다.
- 포인트·콜백 테이블의 기본 키, 주요 유니크 키, 외래키, 체크 제약은 엔티티가 요구하는 핵심 무결성과 대체로 일치하며 기존 MySQL 스키마 테스트가 일부를 확인한다.
- 다만 `CREATE TABLE IF NOT EXISTS`는 같은 이름의 불완전한 기존 테이블을 교정하지 않으며, 위 근거가 부분 DDL 실패 후의 자동 복구나 임의 레거시 스키마 호환성을 보장하지는 않는다.

### 추가 검증이 필요한 사항

- 빈 MySQL DB 전체 부트스트랩, 실제 레거시 데이터 fixture 이관, DDL 중간 실패 후 `repair`·재적용은 격리된 DB 실행 환경이 필요한 `BACKLOG.md` 대상이다.
- 운영과 같은 MySQL 버전·SQL 모드에서 `reserve=null`, 외래키 불일치, 중복 데이터, 기존 동명 포인트 테이블을 각각 포함한 사전 점검이 필요하다.
- 테이블 크기에 따른 `ALTER TABLE` 락 시간과 배포 중 서비스 영향 실측은 이번 감사 제외 범위이며 `BACKLOG.md` 대상으로 유지한다.

## 6단계 — 프론트 로딩·오류·중복 클릭·인증 만료 상태 감사

### 확인한 파일

- `frontend/src/api/authApi.js`
- `frontend/src/api/artApi.js`
- `frontend/src/api/orderApi.js`
- `frontend/src/api/pointApi.js`
- `frontend/src/utils/authStorage.js`
- `frontend/src/utils/orderRequestGuard.js`
- `frontend/src/utils/orderView.js`
- `frontend/src/utils/sellerOrderView.js`
- `frontend/src/pages/auth/PrivateRoute.jsx`
- `frontend/src/pages/auth/Login.jsx`
- `frontend/src/pages/auth/RegisterUser.jsx`
- `frontend/src/pages/auth/RegisterArtist.jsx`
- `frontend/src/pages/MyPage/UploadSell.jsx`
- `frontend/src/pages/Auction/ArtDetail.jsx`
- `frontend/src/pages/MyPage/OrderStatus.jsx`
- `frontend/src/pages/MyPage/SalesOrders.jsx`
- `frontend/src/pages/MyPage/Charge.jsx`
- `frontend/src/pages/MyPage/BidStatus.jsx`
- `frontend/src/pages/MyPage/SuccessfulBid.jsx`
- `frontend/src/pages/MyPage/ManageArts.jsx`

### 확인한 테스트와 실행 결과

- `orderRequestGuard.test.js`, `orderView.test.js`, `sellerOrderView.test.js`를 확인했다.
- `node --test src/utils/*.test.js` 직접 실행 결과 9건 통과, 실패 0건이다.
- `node node_modules/eslint/bin/eslint.js .` 실행 결과 오류 없이 통과했다.
- `npm test`는 테스트 시작 전 로컬 전역 npm 경로의 `npm-cli.js`를 찾지 못해 실패했다. 같은 테스트 스크립트를 Node로 직접 실행해 테스트 코드 결과를 분리 확인했다.
- 현재 테스트는 순수 유틸리티만 대상으로 하며 회원가입, 충전, 입찰, 주문 React 컴포넌트의 실제 비동기 상태 전이는 검증하지 않는다.

### 발견사항

#### QA-010 — 충전 화면이 초기 조회 전·실패 후에도 0원 잔액과 충전 가능 상태를 표시함

- 심각도: 중간
- 관련 파일과 코드 위치:
  - `frontend/src/pages/MyPage/Charge.jsx:32-38`
  - `frontend/src/pages/MyPage/Charge.jsx:42-67`
  - `frontend/src/pages/MyPage/Charge.jsx:130-179`
  - `frontend/src/pages/MyPage/Charge.jsx:230-256`
- 발생 조건:
  - 충전 화면에 처음 진입해 잔액·거래·충전 내역 조회가 진행 중이거나, 세 요청 중 하나가 실패한다.
- 영향:
  - 실제 잔액을 받기 전 기본값 0원이 현재 보유 포인트로 표시되고 예상 잔액도 이를 기준으로 계산된다.
  - 조회 실패 시 오류 문구와 함께 0원·빈 내역이 실제 데이터처럼 남으며 충전 버튼도 활성화된다.
  - 사용자는 조회 중인지 실제 잔액이 0원인지 구분하기 어렵고, 계정별 최대 보유 한도 등을 도입하면 오래된 잔액 기준으로 충전을 시도할 수 있다.
- 근거:
  - `balance`, `transactions`, `charges`는 각각 `0`, 빈 배열로 초기화되지만 초기 조회 전용 `loading` 상태가 없다.
  - `Promise.all` 실패 시 오류만 설정하며 미조회 상태를 별도로 유지하지 않는다.
  - 제출 버튼 비활성화 조건은 `charging || finalAmount < 1000`뿐이라 초기 조회 완료 여부와 무관하다.
  - 이 화면을 렌더링해 로딩·실패 상태를 검증하는 컴포넌트 테스트가 없다.
- 재현 또는 검증 방법:
  - 포인트 조회 응답을 지연시키거나 하나를 500으로 실패시킨 뒤 화면의 잔액, 빈 내역, 충전 버튼 상태를 확인한다.
- 권장 수정안:
  - 초기 조회의 `loading`, `loaded`, `error` 상태를 명시적으로 분리하고 조회 완료 전 잔액을 자리표시자로 표시한다.
  - 초기 조회 실패 시 데이터 영역과 충전 제출을 비활성화하고 재조회 동작을 제공한다.
- 판정: 확정된 결함

#### QA-011 — 회원가입 중복확인 응답이 변경된 입력값을 검증 완료로 표시할 수 있음

- 심각도: 낮음
- 관련 파일과 코드 위치:
  - `frontend/src/pages/auth/RegisterUser.jsx:20-47,60-75,90-97,139-146`
  - `frontend/src/pages/auth/RegisterArtist.jsx:20-47,60-75,89-97,128-139`
- 발생 조건:
  1. 아이디 또는 닉네임 A로 중복확인을 요청한다.
  2. 응답이 오기 전에 입력값을 B로 변경한다.
  3. A에 대한 사용 가능 응답이 나중에 도착한다.
- 영향:
  - 화면은 현재 입력값 B에 대해 중복확인이 끝난 것처럼 `checked=true`와 사용 가능 메시지를 표시한다.
  - 사용자는 B를 별도로 확인하지 않고 제출할 수 있다. 최종 중복 여부는 서버가 다시 검증하더라도 화면의 사전 검증 상태와 안내는 사실과 달라진다.
- 근거:
  - 입력 변경 시 `checked`를 false로 만들지만, 진행 중 요청을 취소하거나 요청 당시 값과 현재 값을 비교하지 않는다.
  - 이전 요청의 성공 콜백은 무조건 해당 필드의 `checked`를 true로 되돌린다.
  - 중복확인 버튼에는 요청 중 비활성화나 최신 요청 식별도 없다.
  - 두 회원가입 컴포넌트 모두 같은 구조이며 관련 비동기 테스트가 없다.
- 재현 또는 검증 방법:
  - A의 중복확인 응답을 지연시키고 B로 변경한 뒤 A의 사용 가능 응답을 반환해 B가 검증 완료로 표시되는지 확인한다.
- 권장 수정안:
  - 응답 적용 전에 요청 당시 값과 현재 값을 비교하고, 필드별 요청 ID 또는 `AbortController`로 이전 요청을 무효화한다.
  - 중복확인 진행 상태를 표시하고 동일 필드의 반복 요청을 제어하는 컴포넌트 테스트를 추가한다.
- 판정: 확정된 결함

#### QA-012 — 실패한 충전 뒤 금액을 바꿔도 이전 멱등성 키를 재사용함

- 심각도: 낮음
- 관련 파일과 코드 위치:
  - `frontend/src/pages/MyPage/Charge.jsx:38,71-111,146-167`
  - `frontend/src/api/pointApi.js:18-23`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/PointChargeService.java:39-55`
- 발생 조건:
  1. 충전 요청이 서버에는 접수됐지만 응답 유실 등으로 화면에서는 실패한다.
  2. 사용자가 충전 금액을 변경하고 다시 제출한다.
- 영향:
  - 새 금액 요청이 이전 요청과 같은 멱등성 키로 전송되어 서버의 `IDEMPOTENCY_KEY_REUSED` 충돌을 받는다.
  - 화면에는 일반 충전 실패 메시지만 표시되고 새 요청으로 진행하려면 페이지를 다시 열어야 할 수 있다.
- 근거:
  - `requestKey.current`는 최초 제출 시 한 번 생성되고 성공 후 “추가 충전”을 누를 때만 초기화된다.
  - 프리셋·직접 입력 변경 처리에서는 키를 초기화하지 않는다.
  - 서버는 같은 키의 기존 충전과 공급자·금액이 다르면 409를 반환한다.
  - 동일 금액의 네트워크 재시도에는 키 유지가 올바르므로, 문제는 실패 후 요청 내용을 변경한 경우로 한정된다.
- 재현 또는 검증 방법:
  - 첫 요청을 서버에서 생성한 뒤 클라이언트 응답만 실패시키고, 금액을 변경해 재제출하여 409와 화면 복구 동작을 확인한다.
- 권장 수정안:
  - 재시도할 요청의 금액·수단 스냅샷을 키와 함께 보관한다.
  - 요청 내용이 바뀌면 새 키를 발급하고, 같은 내용의 불확실한 재시도에만 기존 키를 유지한다.
  - `IDEMPOTENCY_KEY_REUSED`를 사용자가 이해할 수 있는 새 요청 안내로 변환한다.
- 판정: 잠재 위험 — 서버 접수 후 응답 유실 조건의 실제 브라우저 재현은 수행하지 않음

### 무발견 근거

- 로그인, 회원가입, 작품 등록과 입찰 제출은 처리 상태 동안 제출 버튼을 비활성화하고 `finally`에서 상태를 복구한다.
- 입찰은 클라이언트 유효성 검사, 처리 중 버튼 차단, 409 발생 후 작품 재조회와 오류 코드별 안내를 구현한다.
- 구매·판매 주문은 주문 ID별 동기식 요청 가드로 같은 주문의 중복 동작을 차단하며 관련 유틸리티 테스트가 통과했다.
- 주문의 409 응답은 상세와 목록을 함께 다시 조회하고, 포인트 충전 409는 최소한 잔액을 다시 조회한다.
- 공통 응답 인터셉터는 401에서 저장된 인증 정보를 제거하고 인증 변경 이벤트를 발행한다. 보호 화면은 `PrivateRoute` 구독을 통해 로그인 화면으로 이동한다.
- 공개 작품 조회가 만료 토큰 때문에 401을 받으면 인증 헤더를 제거해 한 번 재시도하므로 공개 상세 열람은 유지된다.
- 위 근거는 실제 DOM에서의 빠른 연속 입력·클릭과 네트워크 응답 순서를 검증하는 컴포넌트·E2E 테스트를 대체하지 않는다.

### 추가 검증이 필요한 사항

- React 컴포넌트 테스트로 회원가입의 응답 역전, 충전 초기 조회 실패, 주문·입찰의 빠른 중복 클릭과 401 전환을 검증할 필요가 있다.
- 브라우저 E2E에서 만료 토큰 상태로 공개 작품, 보호 화면, 진행 중 주문 작업에 각각 접근했을 때 인증 정리와 이동 상태를 확인해야 한다.
- 느린 네트워크, 요청 성공 후 응답 유실과 탭 간 인증 만료는 실행 환경이 필요한 `BACKLOG.md` 대상이다.

## 7단계 — 서비스 책임·중복 로직·예외 응답 일관성 감사

### 확인한 파일

- `backend/src/main/java/com/dailyatelier/dailyatelier/dto/ApiErrorResponseDto.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/config/SecurityConfig.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/exception/OrderApiExceptionHandler.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/exception/BidApiExceptionHandler.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/exception/PointApiExceptionHandler.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/controller/UserController.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/controller/ArtController.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/controller/BidController.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/controller/OrderController.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/controller/SellerOrderController.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/controller/PointController.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/UserService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/ArtService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/BidService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/OrderService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/OrderStateService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/OrderQueryService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/OrderPointLedgerService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/PointQueryService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/PointChargeService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/entity/Order.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/entity/OrderStatus.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/entity/Art.java`

### 확인한 테스트와 실행 결과

- `ArtApiSecurityTest`, `PointApiTest`, `OrderManagementApiTest`, `BidApiTest`, `OrderTest`, `OrderStatusTest`를 실행했다.
- 결과는 총 39건 통과, 실패 0건, 오류 0건, 제외 0건이다.
- 주문·입찰·포인트 테스트는 해당 컨트롤러에 한정된 예외 처리기의 응답 코드와 일부 오류 코드를 검증한다.
- 작품 API 테스트는 `ResponseStatusException`의 HTTP 상태만 검증하며 공통 오류 DTO의 필드와 오류 코드 일관성은 검증하지 않는다.

### 발견사항

#### QA-013 — 도메인별 예외 처리 범위가 달라 동일 계열 오류의 응답 계약이 일관되지 않음

- 심각도: 중간
- 관련 파일과 코드 위치:
  - `backend/src/main/java/com/dailyatelier/dailyatelier/dto/ApiErrorResponseDto.java:8-29`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/config/SecurityConfig.java:51-73`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/exception/OrderApiExceptionHandler.java:16-58`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/exception/BidApiExceptionHandler.java:13-43`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/exception/PointApiExceptionHandler.java:13-34`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/UserService.java:37-41,89-94,129-152`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/ArtService.java:163-167,200-204,256-301,312-323`
  - `backend/src/test/java/com/dailyatelier/dailyatelier/controller/ArtApiSecurityTest.java:141-156`
- 발생 조건:
  - 주문·입찰·포인트 또는 Spring Security 계층에서 인증·권한·검증·충돌 오류가 발생하는 경우와, 회원·작품 서비스의 유사 오류가 발생하는 경우를 비교한다.
- 영향:
  - 주문·입찰·포인트·보안 오류는 `timestamp`, `status`, `code`, `message`, `path`를 갖지만 회원·작품의 `ResponseStatusException`은 같은 애플리케이션 전용 `code` 계약을 보장하지 않는다.
  - 프론트가 오류 코드를 기준으로 재조회·필드 안내·인증 처리를 확장할 때 API별 별도 분기나 메시지 문자열 의존이 필요하다.
  - 작품 서비스에는 영문과 한글 사유가 혼재해 사용자 메시지 품질도 호출 경로에 따라 달라진다.
- 근거:
  - 세 예외 처리기는 `assignableTypes`로 특정 컨트롤러에만 적용된다.
  - 보안 설정은 명시적으로 `ApiErrorResponseDto`를 직렬화한다.
  - 회원·작품 서비스는 별도 도메인 예외나 전역 변환기 없이 `ResponseStatusException`을 직접 던진다.
  - 작품 테스트는 400·404 상태만 확인하고 `code`, `message`, `path`를 검증하지 않아 계약 차이를 발견하지 못한다.
- 재현 또는 검증 방법:
  - 존재하지 않는 작품 조회, 타인 작품 수정, 로그인 실패, 주문 조회 실패를 각각 호출해 JSON 필드와 오류 코드를 비교한다.
- 권장 수정안:
  - 애플리케이션 전역 오류 응답 계약과 안정적인 오류 코드 체계를 정의한다.
  - 도메인 예외는 상태·코드·메시지를 보유하게 하고 하나의 전역 처리기에서 공통 DTO로 변환하되, 도메인별 메시지 정책은 유지한다.
  - 각 컨트롤러 계약 테스트에 공통 필드와 대표 오류 코드를 추가한다.
- 판정: 확정된 결함

##### 후속 처리 결과

- 상태: 해결
- 구현 커밋: `33430ea refactor(backend): API 예외 응답 계약 통합`
- 회원·작품 서비스의 `ResponseStatusException`을 상태·코드·메시지를 갖는 공통 도메인 예외로 전환하고, 전역 처리기가 `ApiErrorResponseDto`로 변환하도록 구현했다.
- 주문·입찰·포인트 전용 처리기의 우선순위를 명시해 기존 HTTP 상태·오류 코드·사용자 메시지를 유지했으며, 잘못된 JSON과 DTO·요청 파라미터 오류를 구조화된 400 응답으로 처리했다.
- 회원·작품·주문·입찰·포인트·보안 API의 대표 400·401·403·404·409 계약 테스트를 포함한 백엔드 231건이 통과했고 실패·오류 0건, 제외 7건이었다.
- 프론트 유틸리티 테스트 11건과 컴포넌트 테스트 6건, ESLint와 production build가 통과했다.
- `QA-014`의 포인트 음수 페이지 검증과 오류 변환은 구현하지 않았으며 미완료 상태로 유지한다.

#### QA-014 — 포인트 내역의 음수 페이지가 명시적인 API 오류로 변환되지 않음

- 심각도: 낮음
- 관련 파일과 코드 위치:
  - `backend/src/main/java/com/dailyatelier/dailyatelier/controller/PointController.java:31-45`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/PointQueryService.java:33-50`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/exception/PointApiExceptionHandler.java:13-34`
  - `backend/src/main/java/com/dailyatelier/dailyatelier/service/OrderQueryService.java:106-114`
- 발생 조건:
  - `/api/users/me/points/transactions?page=-1` 또는 `/charges?page=-1`을 호출한다.
- 영향:
  - `PageRequest.of`의 `IllegalArgumentException`이 포인트 예외 처리기에 포함되지 않아 의도한 `INVALID_POINT_REQUEST` 400 응답 대신 공통 계약 밖의 서버 오류 응답이 발생할 수 있다.
- 근거:
  - 포인트 조회는 크기만 1~50으로 정규화하고 페이지 값을 그대로 `PageRequest.of`에 전달한다.
  - `PointApiExceptionHandler`는 `PointApiException`, 요청 본문 검증과 헤더 누락만 처리한다.
  - 같은 주문 조회는 페이지와 크기 범위를 먼저 검증해 `INVALID_PAGE_REQUEST` 400을 반환한다.
  - `PointApiTest`에는 음수 페이지 경계 테스트가 없다.
- 재현 또는 검증 방법:
  - 인증된 요청으로 두 포인트 목록 API에 `page=-1`을 전달하고 상태와 JSON 응답 필드를 확인한다.
- 권장 수정안:
  - 포인트 조회도 페이지·크기 정책을 명시적으로 검증하고 안정적인 400 오류 코드로 변환한다.
  - 여러 목록 API가 같은 페이지 정책을 사용한다면 공통 값 객체나 검증 유틸리티로 통합하고 경계 계약 테스트를 추가한다.
- 판정: 잠재 위험 — 실제 전체 애플리케이션 오류 디스패치 응답은 이번 단계에서 호출하지 않음

### 무발견 근거

- 주문 상태 전이는 `Order.transitionTo`와 `OrderStatus.canTransitionTo`에 집중되어 있고 서비스는 엔티티 예외를 `ORDER_STATUS_CONFLICT`로 변환한다. 직접 상태 setter가 없어 주문 불변식 우회 경로는 확인되지 않았다.
- 구매자·판매자 주문 변경의 소유권 검사는 `OrderStateService` 내부 메서드로 통일되어 있으며 조회 서비스도 같은 `ORDER_ACCESS_DENIED` 코드를 사용한다.
- 포인트 계정·예치·충전 엔티티는 상태 변경 메서드로 잔액과 상태 불변식을 보호하며 서비스가 직접 숫자 필드를 덮어쓰는 경로는 확인되지 않았다.
- 주문 조회와 상태 변경, 주문 원장 처리가 별도 서비스로 분리되어 트랜잭션 조정과 응답 조립이 한 클래스에 과도하게 집중된 상태는 아니다.
- `Art`는 공개 setter를 사용하고 작품 상태·낙찰 입찰·활성 예치·마감 시각을 여러 서비스가 조합한다. 현재 확인한 운영 경로에서는 서로 모순되는 변경을 확정하지 못했으므로 결함으로 기록하지 않았지만, 도메인 메서드로 전환할 구조 개선 후보이다.
- `ArtService`의 경매 취소 예치 해제와 `BidService`의 패찰 예치 해제에는 유사 원장 작성 코드가 있으나 사유·참조와 호출 조건이 달라 현재 동작 결함으로 단정하지 않았다.

### 추가 검증이 필요한 사항

- 전체 애플리케이션 통합 테스트로 회원·작품·포인트·주문·입찰의 대표 400·401·403·404·409·500 응답 스키마를 표 형태로 비교할 필요가 있다.
- 예상하지 못한 런타임 예외의 내부 메시지 노출 여부와 운영 로깅·추적 ID 정책은 실행 환경과 운영 정책이 필요한 `BACKLOG.md` 대상이다.
- `Art`의 상태 조합을 도메인 불변식으로 정의한 뒤 직접 setter 호출을 정적 검색 또는 아키텍처 테스트로 제한할지는 후속 구조 개선 범위에서 결정해야 한다.
