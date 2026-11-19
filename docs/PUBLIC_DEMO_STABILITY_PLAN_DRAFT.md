# 공개 데모 운영 안정화 PLAN 초안

> 상태: 구현·자동/HTTP·브라우저 QA 완료 (2026-11-19). 이 문서는 승인된 설계와 초기 조사 근거를 보존한다. 운영 Aiven DB 변경·배포·README 공개는 수행하지 않았으며, Aiven MySQL은 2026-12-10까지 유지하고 이전은 범위 밖이다.

> 아래의 "현재 구현과 확인된 공백"은 구현 전 조사 시점의 기록이다. 완료 결과와 배포 전 확인 경계는 다음과 같다.

## 완료 결과와 배포 전 확인

- V12은 기존 `NULL` 작품을 LEGACY로 보호하면서 `MANAGED`·`ARTIST_CREATED` 분류와 관리형 경매 생성 키 unique 제약을 추가했다. bootstrap 3건(24·48·72시간)과 일일 최대 1건 보충은 기존 거래 행을 변경하지 않는다.
- 두 공개 계정의 계정·콘텐츠·주문 mutation을 분리해 차단하고, 아리아의 신규 작품 관리·proxy 업로드 및 구매자의 체험 경매 입찰·MANAGED 결제 대기 취소만 허용한다. 일반 작가의 직접 업로드는 allowlist 설정으로 유지한다.
- 대상 backend 정책·보충 테스트, 빈 MySQL Flyway V1→V12 및 V11→V12 보존 검증, 실제 HTTP 정책·원장 검증과 사용자 브라우저 QA를 완료했다. 배포 전에는 공개 환경의 포인트 잔액/상한, allowlist·maintenance·CORS 환경변수와 실제 배포 health를 별도로 확인한다.

## 목표와 비목표

- 공개 구매자 `demo-buyer-bid-qa`가 관리형 경매에 입찰하고, 공개 작가 `demo-artist-aria`가 자신의 새 작품을 이미지와 함께 등록·수정·삭제하는 체험을 유지한다.
- 기존 전시 작품과 기존 입찰·예치·낙찰·주문·원장 행은 갱신·삭제·초기화하지 않는다. 일반 계정의 정상 기능도 바꾸지 않는다.
- DB 초기화, `local-demo` 재실행, Aiven 이전, 전역/분산 rate-limit 인프라와 외부 모니터링 서비스 도입은 이 작업에 포함하지 않는다.

## 현재 구현과 확인된 공백

| 영역 | 이미 구현됨 | 실제 공백 |
| --- | --- | --- |
| 경매 마감 | `AuctionCloseScheduler`가 기동 시 및 기본 10초마다 만료 `ACTIVE` 경매를 잠근 뒤 마감한다. 낙찰 시 주문도 같은 흐름에서 생성한다. | 진행 경매를 보충하는 production 기능·저수량 탐지가 없다. `LocalDemoDataSeeder`의 시간 갱신은 `local-demo` 프로필·명시 flag에만 있고, 무입찰·무주문 작품에만 안전하다. |
| 공개 계정 정책 | public-demo 활성 시 **새 작가 가입은 차단**되고, 지정 작가만 `arts/<userId>` 서명을 받는다. 서명은 기본 10분 5회 인메모리 제한이다. | 두 공개 계정의 비밀번호·이메일·전화·주소·프로필 이미지는 변경 가능하다. 전시 작품과 아리아가 새로 만든 작품을 구분하는 데이터가 없어 보호 범위를 안전하게 나눌 수 없다. |
| 실제 작품 업로드 | 업로드는 브라우저가 Cloudinary에 직접 전송한다. 화면은 JPG/PNG/WEBP·5MB 이하를 검사하고, 서버는 작가 권한·폴더 namespace·등록 시 URL/public ID 일치를 확인한다. | 서명 횟수 제한은 업로드 개수·파일 크기·저장 총량 제한이 아니다. Spring multipart 10MB와 프로필/문의의 서버 검사는 작품 직접 업로드에 적용되지 않는다. Cloudinary signed preset은 요청 파라미터가 우선하고 preset별 파일 크기 상한도 지원하지 않아, 이 경로를 유지한 채 서버 강제할 수 없다. |
| 공개 구매자 잔액 | `LocalDemoDataSeeder`는 local-demo에서만 최초 500만 P를 idempotent하게 적립한다. `DemoPointChargePolicy`는 INTERNAL 충전 뒤 사용 가능+예치 합계가 기본 100만 P 이하인지 검사한다. | 공개 운영 DB가 실제로 local-demo 500만 P로 초기화됐는지와 `maximum-balance` override 값은 저장소에서 확인할 수 없다. 500만 P 상태라면 기본 100만 P 정책은 추가 충전을 거절하므로, 현재 PLAN의 잔액 회복 보장이 성립하지 않는다. |
| 거래 정합성 | 입찰은 작품·계정 잠금, 예치·원장 처리, 자동 마감은 상태 전이와 주문 생성을 사용한다. 낙찰 뒤 결제 대기 주문은 기존 만료 scheduler가 예치를 해제한다. | 공개 구매자는 관리형 경매 외 기존 작품도 입찰할 수 있고, 낙찰 후 기존 주문의 배송·결제·환불 흐름을 바꿀 수 있다. 잔액이 소진·예치됐을 때 공개 체험 전용 회복 경계가 없다. |
| 배포/장애 | CORS는 명시 origin만 허용하고 `*`를 거부한다. CI는 `main` push에서 local-demo MySQL UI QA를 실행한다. | 저장소에 readiness/health 공개 endpoint, 외부 uptime 알림, 배포 후 실제 Worker→Cloudtype→Aiven QA, 진행 경매 부족 알림은 없다. Cloudtype free 중지와 실제 배포 환경변수/가동 상태는 코드만으로 확인할 수 없다. |

## 비교와 단일 권장안

| 대안 | 장점 | 배제 사유 |
| --- | --- | --- |
| `local-demo` 시더를 주기 실행 | 기존 fixture를 재사용한다. | 기존 안전 갱신도 무입찰·무주문 조건을 전제로 하며, 운영 DB의 관계·원장을 건드릴 위험이 있어 금지한다. |
| 기존 종료/무입찰 작품의 마감일 연장 | 행 수가 늘지 않는다. | 현재 운영 데이터가 실제로 무입찰인지 보장할 수 없고, 재활성화는 공개 이력·작품 상태를 바꾼다. |
| **전용 새 데모 경매를 저수량일 때만 추가** | 과거 거래를 불변으로 두고 입찰 가능한 작품만 보충한다. | 권장한다. 명시적 opt-in과 소량 상한이 필요하다. |

### 가장 작은 안전한 권장안

`dailyatelier.public-demo.enabled=true`인 배포에서만 동작하는 전용 maintenance 컴포넌트를 추가한다. 관리형/작가 생성/기존 전시 작품을 DB에서 구분하고, 매일 한 번(기동 시에도 한 번) 다음을 수행한다.

1. 최초에는 현재 진행 중인 **관리형 공개 데모 경매 3건**을 안정 키(`public-demo:managed:bootstrap-1..3`)로 확보한다. 모두 즉시 시작하되 마감은 각각 생성 후 24·48·72시간으로 분산한다. 이후 진행 수가 3 미만이면 하루 최대 1건만 추가하고, 추가분은 생성 후 72시간에 마감한다. 따라서 정상 scheduler 실행에서는 하나가 마감돼도 최소 두 건이 남아 체험 공백이 없다.
2. 관리형 경매는 코드가 만든 정적 `frontend/public/img/demo-art/*` 경로만 사용하고 `cloudinary_public_id`는 비운다. 기존 행, bid, point hold, order, point transaction은 조회 외 변경하지 않는다.
3. `art`에 nullable한 내부 분류(`MANAGED`/`ARTIST_CREATED`)와 생성 키 unique 제약을 둔다. 기존 행의 `NULL`은 `LEGACY`로 간주해 보호하고, 스케줄러는 DB unique 제약으로 동시 실행·재기동의 중복 생성을 막는다. 제목 prefix만으로는 사용자 수정·동명이인·동시 실행을 견딜 수 없어 이 최소 migration이 필요하다.
4. 공개 작가가 새로 만든 작품만 `ARTIST_CREATED`로 서버가 표기한다. 이 작품은 기존 소유자·진행 상태·입찰 후 가격/기간 lock 규칙 안에서 수정·삭제를 허용하고, `LEGACY`와 `MANAGED`는 수정·삭제를 차단한다.

공개 계정은 정확한 두 ID만 별도 정책을 적용한다. 구매자는 `MANAGED`와 아리아의 `ARTIST_CREATED` 경매에만 입찰하고, 관리형 경매 낙찰 뒤에는 결제 대기에서 직접 취소하거나 기존 만료 scheduler가 예치를 해제하도록 허용한다. 결제·배송·환불·구매확정은 기존 주문 및 관리형 경매 모두 차단한다. 작가는 작품 등록·이미지 업로드 및 `ARTIST_CREATED` 작품 관리만 허용하고, 계정 정보 변경은 막는다. 일반 계정과 공개 읽기 계약은 유지한다.

## 구현 단계와 변경 경계

1. **관리형 경매 보충과 식별**
   - `PublicDemoAuctionMaintenanceService`와 scheduler를 추가한다. repository에는 관리형 진행 수 조회와 생성 키 기반의 idempotent 생성만 추가한다.
   - production property는 기본 `false`, 작가 ID·목표 3건·72시간·일일 1건 상한을 명시한다. 기동 시/주기 실행은 flag가 켜진 public demo에서만 가능하다.
   - `AuctionCloseService`, 기존 마감/만료 scheduler, `LocalDemoDataSeeder`, 기존 art/bid/order/ledger 행은 바꾸지 않는다.

2. **작가 체험과 업로드의 실제 경계**
   - `PublicDemoAccessPolicy`와 `ArtService`에서 아리아가 `ARTIST_CREATED` 작품을 생성·수정·삭제하도록 허용하고, `LEGACY`/`MANAGED` 작품 변경은 `403`으로 막는다. `CloudinaryService`의 기존 작가·namespace·서명 횟수 제한은 재사용한다.
   - Cloudinary signed preset은 5MB 제한을 지원하지 않고 signed 요청에서는 request 값이 preset 값보다 우선하므로 사용하지 않는다. 공개 아리아의 기존 signature 발급을 막고, 새 인증 API가 `MultipartFile`을 받아 기존 5MB·JPG/PNG/WEBP 검사를 서버에서 수행한 뒤 `arts/demo-artist-aria`로 직접 업로드한다. 응답 URL/public ID는 기존 작품 등록 계약에 그대로 사용한다. 일반 작가의 기존 직접 signed-upload 흐름은 바꾸지 않는다.
   - 이는 이미 있는 Spring multipart·`CloudinaryService` 업로드 패턴을 작품에만 최소 재사용하는 변경이다. 기존 10분 5회 서명 제한의 같은 in-memory counter를 공개 아리아의 proxy 업로드 시도에 재사용하고, 총 업로드 수·저장 용량 quota와 별도 인프라는 추가하지 않는다. Cloudinary 콘솔 사용량 알림은 운영 확인 항목으로 둔다.

3. **구매자 체험과 계정 보호**
   - `PublicDemoAccessPolicy`에 public account와 art 분류를 받는 action별 공통 `403` 계약을 추가한다. 비밀번호, 이름, 닉네임, 이메일, 전화, 주소, 프로필 이미지는 두 공개 계정 모두 변경 차단한다. 기존 새 작가 가입 차단 정책은 유지한다.
   - `BidService`에서는 공개 구매자가 `MANAGED` 또는 아리아 소유 `ARTIST_CREATED`·진행 중 작품에만 입찰하도록 한다. 잔액 회복은 새 API 없이 기존 `PointChargeService`의 허용 금액을 재사용한다. 운영 공개 구매자의 실제 초기 잔액과 `dailyatelier.point.demo.maximum-balance` 환경값은 배포 전 읽기 전용으로 확인하며, local-demo 500만 P와 기본 상한 100만 P의 충돌 여부를 확인 항목으로 남긴다. 이 작업에서 운영 잔액·설정은 변경하지 않는다.
   - 낙찰 뒤에는 공개 구매자에게 결제·배송지·배송완료·환불·구매확정을 허용하지 않는다. 결제 대기 중인 관리형 주문은 구매자 취소 또는 기존 payment-expiration scheduler로 예치를 해제한다. 공개 작가의 관리형 주문 판매 처리도 차단한다.
   - 찜·문의·리뷰는 기존 전시/공유 데이터 훼손과 첨부 업로드를 피하기 위해 두 공개 계정에 한해 차단한다. 일반 계정은 기존 service 계약을 유지한다.

4. **운영 가시성 및 배포 확인**
   - actuator를 도입하지 않는 최소안으로 공개하지 않는 내부 health endpoint 또는 Cloudtype이 호출 가능한 health check를 추가하고, DB 연결 실패를 5xx로 드러낸다. 인증·민감 정보는 노출하지 않는다.
   - maintenance 로그에 `activeManagedCount`, `createdCount`, `skippedReason`, 실패를 남기고, 0건 지속/예외는 Cloudtype 로그 또는 선택한 외부 uptime 알림의 수동 점검 기준으로 문서화한다.
   - 배포 승인 뒤 Cloudtype 환경변수에서 explicit Worker origin, `DAILYATELIER_PUBLIC_DEMO_ENABLED`, protected IDs, 관리형 경매 flag를 확인한다. Aiven의 TLS 연결·잔여 기간(2026-12-10)과 free-tier 중지 후 recovery는 실제 콘솔에서 확인한다.

## 예상 변경 파일

- `backend/src/main/java/.../service/PublicDemoAccessPolicy.java`
- `backend/src/main/java/.../service/PublicDemoAuctionMaintenanceService.java` 및 scheduler (신규)
- `backend/src/main/java/.../entity/Art.java`, `ArtRepository.java`, 관리형 보충 service/scheduler 및 **분류·생성 키용 Flyway migration**
- `UserService.java`, `ArtService.java`, `CloudinaryService.java`, `BidService.java`, `PointChargeService.java`, 주문·리뷰·문의·찜 mutation service
- 공개 아리아 전용 작품 image multipart API/controller와 `frontend/src/pages/MyPage/UploadSell.jsx`·수정 화면의 업로드 분기. 기존 5MB UI 검사는 UX로 유지한다.
- `backend/src/main/resources/application.properties`, `application.properties.example`, 배포 운영 문서/README
- 대응 service/API 테스트. 프런트 변경은 README에 계정 안내·제한을 추가할 때만 최소로 한다.

## 검증과 브라우저 QA

- 자동 테스트: 빈/기존 관리형 경매 count에서 최초 3건·하루 1건 상한·생성 키 멱등성, unique 충돌을 포함한 동시 scheduler·재기동, 기존 bid/order/hold/ledger 미변경, 마감 scheduler와의 경합을 검증한다.
- API 계약 테스트: public-demo off의 기존 동작, 두 공개 계정의 계정 변경 `403`, 아리아의 `ARTIST_CREATED` 작품 등록/수정/삭제 허용과 기존/관리형 작품 차단, 구매자의 관리형 경매 입찰·충전·주문 취소/만료 예치 해제 및 금지된 주문 후속 행동을 확인한다.
- 업로드 테스트: 공개 아리아의 multipart 5MB·JPG/PNG/WEBP·고정 namespace 검증과 Cloudinary 실패 rollback, 공개 아리아 signature 거절, 일반 작가의 기존 signature 흐름 불변, 등록 시 namespace/public ID 검증을 확인한다.
- 기존 backend·frontend CI와 함께 실행하되, 운영 Aiven DB나 `local-demo`를 대상으로 실행하지 않는다.
- 실제 배포 승인 후 브라우저 QA: Worker에서 공개 조회, 아리아의 5MB 이하 JPG/PNG/WEBP 등록과 자기 생성 작품 수정·삭제, 기존 전시 작품 수정 차단, buyer의 관리형 작품 입찰·데모 충전·결제 대기 취소, 계정 변경과 결제/배송/환불 차단, CORS preflight, Cloudtype cold start 뒤 health와 API 복구를 확인한다. 배포 전에는 공개 구매자의 실제 잔액과 충전 상한이 일치하는지도 읽기 전용으로 확인한다. 공유 운영 데이터의 주문·환불·삭제 동작은 실행하지 않는다.

## 우선순위와 승인 전 확인 사항

1. **P0 — 관리형 경매 보충, 데이터 분류, 계정 변경 차단:** 공개 README 계정을 열기 전 필요하다. 현재는 시간이 지나면 입찰 체험이 0건이고, 기존 전시와 작가 생성 작품을 구분할 근거가 없다.
2. **P1 — 배포 health/실제 QA와 데이터 고갈 경보:** free-tier 중지와 환경변수 누락은 코드 테스트로 발견할 수 없다. 사용자 노출 전에 한 번의 배포 검증과 이후 점검 기준이 필요하다.
3. **P2 — 분산 rate limit, 업로드 총량 quota, 별도 모니터링 서비스:** 유용하지만 최소 보호안 이후 별도 승인한다. 현재 인메모리 서명 제한은 인스턴스 재기동·다중 인스턴스에 공유되지 않는다.

DB migration은 관리형 경매의 중복 방지와 기존 전시/작가 생성 작품의 신뢰 가능한 분류에 필요하다. 기존 전역 새 작가 가입 차단은 유지한다. 이 PLAN 승인 전에는 운영 DB 수정이나 배포 작업을 시작하지 않는다.
