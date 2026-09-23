# DailyAtelier 완료 기록

> [!IMPORTANT]
> `PLAN_DONE.md`는 완료된 작업의 결과를 빠르게 파악하기 위한 요약 문서다.
>
> 각 작업은 완료 후에도 참고할 핵심 결과와 계약만 간결하게 남긴다.
> 일반적인 테스트·빌드·QA 결과는 반복하지 않고, 이후 판단에 필요한 특수 검증·설계 결정·검증 한계만 추가 기록한다.
>
> 구현 과정, 조사 과정, QA 실행 절차, 테스트 케이스별 상세 내용,
> 커밋 해시·커밋 목록은 반복 기록하지 않는다.

## 작품 조회 및 공개 접근

- 진행 작품 목록, 상태와 무관한 상세, 작가 본인의 등록 작품 조회를 실제 API로 연결하고 URL 페이지네이션과 로딩·오류·빈 상태를 적용했다.
- 목록·상세는 공개하며 등록·내 작품·찜 변경은 인증이 필요하다. 만료 토큰의 401과 권한 오류 403을 구분한다.
## 작품 탐색·검색

- 공개 작품 검색과 세 경매 화면의 검색·필터·정렬·페이지네이션·URL 복원을 구현했다. 형태는 `DIGITAL`·`PHYSICAL`, 분류는 `ArtCategory`를 사용하고 `material`은 재료·매체·기법 설명으로 유지한다.
- `UPCOMING`은 `now < bidStartTime`, `ONGOING`은 `bidStartTime <= now < closingTime`, `ENDED`는 판매·유찰 작품과 시간상 종료한 활성 작품이며 취소 작품은 제외한다. 정렬 동률은 작품 ID 내림차순, API 페이지는 0부터 화면 URL은 1부터 시작한다.
- 빈 MySQL에서 Flyway V1→V2→V3와 Hibernate schema validation, 재실행 migration 0건을 확인했다.
## 작품 수정·삭제

- 소유 작가만 진행 중·마감 전 작품을 수정·삭제할 수 있다. 입찰 전에는 가격·기간·설명·이미지, 입찰 후에는 비가격 정보만 수정할 수 있다.
- 입찰 없는 작품은 물리 삭제하고 입찰 있는 작품은 이력을 보존한 `CANCELED`로 전환한다. 취소 작품은 공개·입찰·마감 대상에서 제외하되 작가·입찰자 이력에는 남긴다.
- 작품 행을 비관적 쓰기 잠금한 뒤 권한·상태·마감·입찰을 재검사하며, 리뷰가 있는 작품의 물리 삭제는 거절한다.
## 작품 수정·삭제 프론트 연동과 상태 표시

- 작가 작품 관리 화면에 수정·삭제를 연결하고 입찰 상태에 따른 편집 잠금, 이미지 유지·교체, 충돌 후 최신 상세 복구와 기존 목록 URL 복귀를 적용했다.
- 물리 삭제와 취소는 `DELETED`·`CANCELED` 및 기존 상태 필드로 구분한다. 작품명 수정, 취소 작품 공개 범위, tombstone 정리 API와 취소 시 찜 정책은 기존 계약을 확대하지 않았다.
- 빈 MySQL에서 Flyway V1~V7, Hibernate schema validation과 작품 목록 API 응답을 확인했다.
## 홈 신규·종료·Best Art 연동

- 홈 신규·종료·Best Art의 mock을 공개 검색 데이터로 교체하고 종료 탭, 낙찰가·최종가, 상세 이동을 연결했다.
- 종료 작품은 `SOLD`·`UNSOLD` 결과와 `RECENTLY_ENDED` 정렬을 제공하며 취소 작품은 제외한다.
## 판매자 최소 입찰 증분

- 작품별 최소 입찰 증분을 생성·수정·상세·입찰에 연결했다. 기본값은 1,000원이며 100원~10,000,000원의 100원 배수만 허용하고 경매 시작부터 수정은 `409 MINIMUM_BID_INCREMENT_LOCKED`로 차단한다.
- 작품 잠금 뒤 최신 현재가와 증분으로 입찰을 다시 검증한다. Flyway V4는 기존 작품과 ID를 보존해 1,000원으로 백필한다.
- 21억 원 경계의 마지막 유효 입찰 이후 nullable 다음 최소가와 버튼 비활성 상태는 INTERNAL 충전 상한 때문에 직접 검증하지 않았다.
## 입찰 MVP

- 인증 사용자의 진행 중 경매 입찰과 사용자별 최고 입찰·최근 시각·최고가 여부·상태 집계를 구현했다. 판매자 본인 입찰은 금지하고 금액은 1원 이상 21억 원 이하 정수로 제한한다.
- 입찰 이력과 `Art.currentPrice`는 하나의 트랜잭션에서 변경한다. 작품 행을 `PESSIMISTIC_WRITE`로 잠가 같은 작품은 직렬화하고 서로 다른 작품은 독립 처리하며, 인식 가능한 락 실패는 `409 BID_CONFLICT`로 변환한다.

## 입찰 잠금 timeout 및 오류 계약 통일

- 실제 MySQL에서 입찰 트랜잭션의 `innodb_lock_wait_timeout`을 요청 단위 3초로 적용하고 종료 시 기존 session 값으로 원복한다.
- `art`와 `point_account` 잠금 timeout을 모두 `409 BID_CONFLICT`로 반환한다.
- timeout 시 입찰·작품 현재가·활성 예치·계정 잔액·포인트 원장의 전체 rollback과 Hikari 커넥션 재사용 시 session 값 비누출을 검증했다.

## 동시 입찰 부하 검증 및 Hikari pool 비교

- 실제 MySQL과 HTTP API에서 단일 작품·완전 분산·동일 계정 입찰 부하를 재현하고, 작품·포인트 계정 잠금과 Hikari 커넥션 대기를 구분해 검증했다.
- Hikari pool size 10·20·30을 비교했으나 처리량과 상위 백분위 응답 시간의 일관된 개선이 없어 production 기본값 10을 유지한다.
- 로컬 단일 장비 측정이므로 배포 환경의 반복 측정과 DB CPU 관찰은 별도 검증 대상으로 유지한다.

## 경매 마감·낙찰

- 입찰 0건은 유찰, 입찰이 있으면 금액·시각·ID 우선순위의 최고 입찰을 낙찰로 확정하고 실제 종료 시각을 저장한다. 스케줄러와 서버 시작 catch-up으로 누락 마감을 재처리한다.
- 작품별 트랜잭션과 비관적 락으로 중복 마감과 입찰·마감 경합을 제어한다. 현재가와 최고 입찰가가 다르면 확정을 중단하며 작가 응답에는 낙찰자 ID·닉네임을 노출하지 않는다.
- 실제 MySQL에서 자동 마감과 낙찰·패찰을 확인하고 장기 락의 `409 BID_CONFLICT` 반환을 검증했다.
## 주문 기능

- 작품당 주문 하나의 unique 제약과 구매자·판매자·작품·낙찰가·배송지 스냅샷을 도입했다. 낙찰과 `PAYMENT_PENDING` 주문 생성을 같은 트랜잭션에서 처리하고 재실행에는 기존 주문을 반환한다.
- 완전한 기본 배송지만 복사하며 `PAYMENT_PENDING` 동안 배송지를 확정·재확정할 수 있다. 정상 전이는 `PAYMENT_PENDING → PAID → PREPARING → SHIPPED → DELIVERED → CONFIRMED`이고 결제 기한은 생성 후 24시간이다.
- 결제·만료·포기 경합은 주문 행 잠금 후 최신 상태를 재검사한다. 주문 생성 실패 시 낙찰도 롤백하고 다음 마감 주기에서 재처리한다.
## 포인트 원장

- `PointAccount`, 불변 `PointTransaction`, `PointHold`, `PointCharge`와 콜백 inbox로 충전·입찰 예치·낙찰 결제·취소·환불을 연결했다. `PointAccount`가 잔액 기준이며 `User.reserve`는 읽기 전용 호환 필드다.
- 잔액과 원장은 같은 트랜잭션에서 변경하고 취소·환불은 반대 거래로 기록한다. 계정 행을 먼저 잠그며 주문 `PAID`, `COMMIT`, hold 확정은 하나의 트랜잭션으로 처리한다.
- 멱등성 키, PG 주문번호와 공급자 이벤트 ID unique 제약으로 중복 반영을 막고 콜백은 최대 5회 재처리한다. 외부 PG는 경계만 마련했으며 충전은 권한이 제한된 `INTERNAL` 데모 방식이다.
## 환불 상태 전이와 화면 연동

- 환불 승인 시 주문 `REFUNDED`와 요청 `REQUESTED → APPROVED`를 함께 전이한다. 동일 작업은 멱등 성공, 반대 작업은 충돌로 처리하고 포인트 환불 원장은 한 번만 반영한다.
- V2 migration은 기존 `REFUNDED + REQUESTED` 데이터만 `APPROVED`로 보정한다.
## 주문 UX 및 최신 응답 정책

- 구매자·판매자 주문 카드와 판매 상세 정보 위계를 모바일 우선으로 정리하고 배송지 변경 비교 dialog를 추가했다.
- 목록 조회는 이전 요청을 중단하고 늦은 성공·실패·`finally`와 unmount 이후 상태 변경을 폐기한다. 200% 확대는 자동화 환경에서 직접 재현하지 못했다.
## 로그인 후 원래 위치 복귀

- 내부 복귀 경로를 로그인 성공 후 한 번만 소비하고 보호 route, 작품 상세와 주문의 401 이동에 공통 적용했다.
- 절대 URL, protocol-relative·백슬래시 URL, 인증 화면 순환과 손상된 state는 홈으로 대체한다.
## 프로필 사진 변경

- Flyway V7로 `users.profile_image_url`을 추가하고 인증 principal 기준 JPEG/PNG 5MB 이하 Cloudinary 업로드와 프론트 미리보기·영속 반영을 구현했다.
- 클라이언트 사용자 ID·이미지 URL·public ID를 권한 근거로 사용하지 않고 `secure_url`만 저장한다. 파일 오류 400, 미인증 401, 사용자 없음 404, Cloudinary 실패 502를 반환한다.
## 우편번호 검색과 프로필 수정 동선

- Kakao 우편번호 `embed()`를 접근성 dialog에 연결하고 5자리 우편번호·기본주소·상세주소 입력과 전체 화면 프로필 수정 동선을 구현했다.
- 저장 성공 후 프로필을 재조회하며 실패 시 입력값을 보존한다.
## 프로필 기본정보 저장 피드백

- 저장 성공 안내, 조회 실패 재시도, 저장 작업 간 상호 차단과 이름 저장 계약을 보완했다.
## 작가 검색 및 상세 페이지

- 작가명 부분 일치·대소문자 무시 검색과 안정적인 정렬, 공개 작가 목록·상세·진행 작품 페이지를 구현했다.
- 검색어·페이지 URL을 보존하고 헤더의 작품·작가 검색 유형을 연결했다.
## 리뷰 기능 실제 연동

- Flyway V6으로 리뷰를 주문 기반 계약으로 변경하고 사용자·작품·주문 외래키와 주문당 리뷰 1개 unique 제약을 적용했다.
- 구매자의 `CONFIRMED` 주문 리뷰 작성·수정, 내 리뷰, 작가가 받은 리뷰와 판매 작품별 작성 현황을 실제 API와 화면에 연결했다.
## 1:1 문의 실제 연동

- 문의 API·Cloudinary 첨부·권한과 사용자 작성·목록, 관리자 필터·답변 화면, 실제 미답변 배지를 구현했다.
- 첨부 문의 작성, 관리자 답변, 중복 답변·타인 접근과 일반 사용자의 관리자 화면 차단은 과거 기록상 수동 검증 예정으로 남아 있었다.
## 헤더·인증 폼·포커스 접근성

- 헤더 메뉴의 펼침 상태·Escape·포커스 복귀·배경 차단과 인증 폼의 label·오류 연결·오류 포커스를 적용했다.
- 전역 `focus-visible`, 필요한 범위의 reduced-motion과 모바일 홈 히어로 이미지 배치를 보완했다.
## 공통 dialog 접근성

- 작품 원본과 리뷰 dialog를 Portal 기반 공통 컴포넌트로 전환하고 최초 포커스, Tab 순환, Escape, opener 복귀와 배경 `inert`를 적용했다.
## 모바일 메뉴 viewport

- 900px 이하에서 메뉴 높이를 `calc(100dvh - var(--header-height))`로 설정하고 내부 스크롤을 유지했다.
## 작가 마이페이지 메뉴 구조

- 네 작가 전용 route를 `ArtistRoute`로 묶어 일반 회원의 화면·API 마운트를 차단했다.
- 일반·작가 navigation을 분리하고 `aria-current`와 작은 화면 줄바꿈을 적용했다.
## UI token 정리

- 정의되지 않은 `--space-7` 4곳을 기존 spacing token으로 교체하고 전역 token·컴포넌트 구조는 유지했다.
## 안내 콘텐츠와 미완성 route 정리

- `/qna`를 1:1 문의 안내, `/info`를 이용 안내로 정리하고 미완성 route를 제거했다. 푸터 안내와 로그인 후 문의 작성 복귀도 연결했다.
- 비로그인 `/qna → 로그인 → 문의 작성` 복귀는 구현했으나 브라우저에서 직접 확인하지 못했다.
## 실행·테스트·데모 문서와 배포 설정

- 비밀값 없는 공통 설정·예제 env, 실행·테스트·배포 체크리스트와 역할별 데모 흐름을 문서화하고 API·CORS 주소를 환경변수화했다.
- Hibernate `ddl-auto=validate`와 Flyway를 스키마 기준으로 확정했다. CORS 빈 목록·wildcard를 거부하고 기존 기본 주소와 `daliyatelier.env` 철자는 호환성을 위해 유지한다.
- 빈 MySQL에서 Flyway V1~V6와 Hibernate validate 기동을 확인했다. 당시 로컬 계정 문제로 회원가입·로그인 smoke test와 전체 거래 회귀는 실행하지 못했다.
## 핵심 도메인 품질 감사

- 사용자 흐름, 권한, 트랜잭션·동시성, 결제·원장, Flyway, 프론트 상태와 오류 계약을 감사해 `QA-001`~`QA-014`를 확정 결함·잠재 위험, P0~P2로 분류했다.
- 상세 근거는 `QUALITY_AUDIT.md`에 보존했다.

| ID | 심각도·판정 | 핵심 내용 | 결과 |
| --- | --- | --- | --- |
| QA-001 | 높음·확정 결함 | `SHIPPED → DELIVERED` 호출 경로 부재 | 해결 |
| QA-002 | 중간·확정 결함 | 구매자 요청·판매자 승인 환불 정책 미연결 | 해결 |
| QA-003 | 높음·확정 결함 | 운영 데모 충전 표시·발행 제한 부재 | 해결 |
| QA-004 | 높음·잠재 위험 | 콜백 예외의 부분 커밋·rollback-only 충돌 위험 | 해결 |
| QA-005 | 중간·확정 결함 | 계정·원장 합계 외 의미적 불일치 검사 부족 | 해결 |
| QA-006 | 낮음·잠재 위험 | 동시 최초 콜백 unique 충돌 복구 부재 | 실제 PG 도입 전 검증 필요 |
| QA-007 | 높음·확정 결함 | 기존 Flyway 이력으로 빈 MySQL 생성 불가 | 해결 |
| QA-008 | 높음·잠재 위험 | 기존 활성 거래의 예치·원장 이관 부재 | 운영·공유 DB가 없어 적용 대상 없음 |
| QA-009 | 중간·잠재 위험 | 기존 null `reserve`가 migration을 중단할 위험 | 신규 baseline 직접 생성으로 적용 대상 없음 |
| QA-010 | 중간·확정 결함 | 충전 조회 전·실패 후 0원과 충전 가능 상태 표시 | 해결 |
| QA-011 | 낮음·확정 결함 | 변경 전 회원가입 중복확인 응답 경합 | 해결 |
| QA-012 | 낮음·잠재 위험 | 충전 금액 변경 후 이전 멱등성 키 재사용 | 해결 |
| QA-013 | 중간·확정 결함 | 도메인별 오류 응답 계약 불일치 | 해결 |
| QA-014 | 낮음·잠재 위험 | 음수 페이지가 구조화된 400으로 변환되지 않음 | 해결 |

## 감사 발견사항 후속 수정

- QA-004는 업무 변경과 실패 inbox 기록의 트랜잭션을 분리해 실패 롤백과 재처리를 보장하고, QA-013은 오류 응답을 `timestamp`, `status`, `code`, `message`, `path`로 통일했다.
- QA-011은 최신 입력과 일치하는 중복확인 응답만 반영하고 미확인 제출을 차단했으며, QA-014는 잘못된 page·size를 `INVALID_POINT_REQUEST` 400으로 변환했다.
- 실제 PG 처리기와 MySQL의 동시 최초 콜백, 브라우저 장애 주입과 운영 MySQL·부하 환경은 검증하지 않았다.
## Flyway baseline 재구성

- 운영·공유 DB가 없는 개발 단계라는 전제에서 기존 V0~V6 대신 `V1__create_baseline_schema.sql`을 최종 기준으로 재구성하고 레거시 이관 DML·조건부 ALTER는 포함하지 않았다.
- 적용된 migration은 수정하지 않고 이후 변경은 V2 이상으로 누적한다. QA-008·009는 운영·공유 DB와 활성 거래가 없어 적용 대상 없음으로 판정했다.
- 빈 MySQL에서 V1 단독 적용, 제약·인덱스, Hibernate `ddl-auto=validate`와 재실행 migration 0건을 확인했다.
## 현재도 의미 있는 검증 한계

- 실제 PG 승인·취소·서명 검증, 운영 정산과 동시 최초 콜백 unique 충돌은 검증하지 않았다.
- 배포 환경과 동일한 MySQL의 락 timeout 재검증, 반복 부하 측정과 DB CPU 관찰, 실제 데이터 기준 실행 계획과 21억 원 경계의 마지막 유효 입찰 이후 UI 상태는 검증하지 않았다.
- 일부 브라우저 장애 주입 시나리오는 자동 테스트로 대체했다.
## 부록: 포인트 운영 조회 SQL

### 실패 또는 미처리 콜백

```sql
SELECT callback_event_id, provider, provider_event_id, pg_order_id,
       status, attempt_count, last_error, received_at, processed_at
FROM payment_callback_event
WHERE status IN ('RECEIVED', 'FAILED')
ORDER BY received_at;
```

### 최대 재시도 도달 콜백

```sql
SELECT callback_event_id, provider, provider_event_id, attempt_count,
       last_error, received_at
FROM payment_callback_event
WHERE status = 'FAILED' AND attempt_count >= 5
ORDER BY received_at;
```

### 계정과 원장 잔액 불일치

```sql
SELECT account.user_id,
       account.available_balance,
       account.held_balance,
       COALESCE(SUM(tx.available_delta), 0) AS ledger_available_balance,
       COALESCE(SUM(tx.held_delta), 0) AS ledger_held_balance
FROM point_account account
LEFT JOIN point_transaction tx ON tx.user_id = account.user_id
GROUP BY account.user_id, account.available_balance, account.held_balance
HAVING account.available_balance <> COALESCE(SUM(tx.available_delta), 0)
    OR account.held_balance <> COALESCE(SUM(tx.held_delta), 0)
ORDER BY account.user_id;
```

## 모바일 메뉴의 페이지 이동 후 닫힘 처리

- 로고·내부 링크·브라우저 history를 포함한 route 변경 시 모바일 메뉴를 닫고 body 스크롤 잠금과 배경 `inert`를 복원하도록 했다.
- Escape의 햄버거 focus 복귀와 데스크톱 breakpoint 동작을 유지했다.
## 낙찰 후 주문·배송지·결제 연결 개선

- 낙찰 작품·입찰 현황·상세에 구매자 본인의 nullable `orderId`를 제공해 정확한 주문을 직접 조회하고, 미연결 데이터에는 대체 안내를 표시한다.
- 배송지 확정 여부에 맞는 입력·결제 흐름과 사용 가능·예치 포인트 안내를 제공한다. 낙찰 예치가 결제를 담보하므로 사용 가능 포인트만으로 부족 상태를 판정하지 않는다.
- 결제 충돌·예치 무결성 오류 후 주문과 포인트의 최신 상태를 다시 조회한다.
## 화면 전환 및 조회 로딩 UX 1차 개선

- 충전 요청의 금액·결제수단·멱등성 키를 제출 시점에 고정하고 중복 제출과 처리 중 편집을 차단한다. 충전 성공은 후속 잔액·거래·내역 조회 실패와 분리한다.
- 관리자 문의 답변도 문의·답변·필터를 제출 시점에 고정하고, 답변 성공과 후속 목록 갱신 실패를 분리한다.
- 중복 제출과 후속 조회 실패 경계는 브라우저에서 직접 재현하지 않고 컴포넌트 테스트로 검증했다.
## 선택·페이지 전환의 늦은 응답 반영 방지

- 관리자·사용자 문의 상세와 낙찰 작품 목록에서 이전 요청을 취소하고 늦은 성공·실패·`finally`가 최신 선택·페이지 상태를 덮지 않도록 했다.
- 취소 요청은 사용자 오류로 표시하지 않고 unmount 시 폐기하며, 문의별 상세 캐시와 기존 답변·주문 연결 동작은 유지한다.
## 인증 폼의 중복 제출 방지

- 로그인과 두 회원가입 form에 동기 제출 guard를 적용하고 처리 중 입력·중복확인·제출을 잠가 같은 렌더의 연속 요청을 한 번만 처리한다.
- 실패 시 입력과 action을 복구하며 기존 중복확인·최신 응답 판별·성공 이동·로그인 후 원래 위치 복귀를 유지한다.
## 생성 요청 중 편집·이탈과 중복 제출 차단

- 문의 작성은 처리 중 폼 문맥과 취소를 잠그고, 작품 등록은 Cloudinary 서명·업로드·생성을 하나의 guard 구간으로 묶어 중복 생성과 편집을 차단한다.
- 어느 단계에서 실패해도 입력·첨부·이미지 파일을 보존해 재시도할 수 있으며 기존 API·Cloudinary 계약은 유지한다.
- 연속 submit과 Cloudinary 단계별 실패는 브라우저에서 직접 재현하지 않고 컴포넌트 테스트로 검증했다.
## 마이페이지 조회 상태와 재시도 UX 개선

- 문의·입찰 통계에서 조회 중·실패를 정상 `0`건과 구분해 `-`로 표시하고, 찜 추가 조회 실패 시 기존 목록을 유지한 채 실패한 페이지를 재시도하도록 했다.
- 입찰·찜 목록은 새 조회와 화면 이탈 시 이전 요청을 취소하고, 늦은 이전 응답이 최신 목록 상태를 덮지 않도록 한다.
- loading 전환 순간은 브라우저에서 명확히 확인하지 못해 자동 테스트로 검증했다.

## Cloudinary 고아 이미지 안전 정리

- 작품·프로필은 URL과 case-sensitive `public_id`를 함께 저장하고 `arts/{userId}`·`profiles/{userId}` namespace 및 URL 동일 자산을 검증한다. 기존 URL-only 데이터와 `CANCELED` 작품 이미지는 자동 삭제하지 않는다.
- 이미지 교체·작품 물리 삭제는 같은 DB 트랜잭션에 cleanup을 등록하고, commit 이후 작업자가 lease·claim fencing·참조 재검증을 거쳐 삭제한다. 성공/not-found는 완료, 네트워크·408·429·5xx는 제한 재시도, 비재시도 오류와 최대 시도 초과는 `FAILED`로 보존한다.
- 실제 빈 MySQL 8.0에서 Flyway V1~V9, Hibernate validate, 재실행 0건, `SKIP LOCKED`, lease 재선점과 stale claim 차단을 검증했다. 브라우저 업로드 후 API 미도달 자산 정리는 별도 backlog로 유지한다.

## React Hooks lint 개선

- 작품 수정 화면은 경매 시작·종료 시각까지 다음 갱신을 예약해 재진입이나 입력 없이 잠금 상태를 최신화하며, `Date.now()`를 렌더에서 제거했다.
- 판매 주문 목록과 중복 확인은 렌더 중 ref를 갱신하지 않고 최신 값·요청 판별 계약을 유지했다. `react-hooks/purity`, `react-hooks/refs`를 error로 재활성화했다.

## 홈 1단계 사용성 개선

- 기존 홈 섹션 순서·링크·데이터 조회 계약을 유지하면서 저개수 카드 밀도, 종료 작품 배치·간격, Header 탐색 영역을 조정했다.
- 홈 작품 카드는 4:3 프레임의 `cover` 표시로 그리드 높이를 고정하고, 상세에서는 원본 전체를 확인하는 역할 분리를 유지한다.
- 모바일 히어로는 16:9 `cover`로 중앙 콘텐츠를 보존하고, 슬라이드 버튼은 이미지 영역 기준 중앙에 배치하며 SVG chevron과 기존 버튼 접근성 계약을 유지한다.
- Header dropdown은 문서 흐름에 높이를 추가하지 않는 overlay이며, Header 높이와 hover·focus·Escape·키보드 동작을 유지한다.

## 로컬 demo 작품 seed

- `local-demo` 프로필과 명시적 opt-in이 함께 있을 때만 자연키 기반으로 demo 계정·작가·20개 작품을 생성하고 로컬 정적 이미지를 연결한다. Flyway, 운영 API·Cloudinary 검증, 기존 비-demo 데이터는 변경하지 않는다.
- 작품 spec은 `ONGOING`·`UPCOMING`·`SOLD`·`UNSOLD` 역할을 가지며, `SOLD` fixture만 최소 bid·hold·winning bid·order 관계를 구성한다.
- 재실행은 거래가 없는 원래 `ONGOING`·`UPCOMING` 작품만 상대 시간 상태로 복구한다. 기존 `SOLD`·`UNSOLD`, 실제 QA 거래 작품, 비-demo 데이터는 보존한다.
- 가변 `Clock`의 `+6일` 재실행으로 진행·예정 구성 복구, demo 수량 불변, SOLD·주문·hold 관계 보존을 검증했다.

## 마이페이지 홈 사용성 개선

- 데스크톱 sidebar의 독립 스크롤을 제거하고 sticky를 유지했으며, 중복 작가 `작품 등록` QuickAction을 제거했다.
- 구매·판매와 리뷰 역할이 드러나도록 메뉴 명칭과 대비·세이지 accent를 정리하고, 작가 모바일 통계를 2×2로 배치하며 `24시간 내 마감` 명칭을 적용했다.
- 관련 MyPage 테스트 16개를 통과했다.

## 입찰 현황 사용성 개선

- 모바일·데스크톱 입찰 카드의 정보 위계와 배치를 개선했다.
- 상태 배지·가격·주요 행동의 가독성과 세이지 accent를 보정했다.
- 관련 Vitest 19개와 lint를 통과했다.

## 낙찰 작품 사용성 개선

- 낙찰 작품 카드의 모바일 이미지 비중과 보조 텍스트 가독성을 개선했다.
- `주문 확인`을 주요 세이지 CTA로 정리하고 `상세 보기`와 행동 위계를 명확히 했다.
- 관련 테스트 9개와 lint를 통과했다.
