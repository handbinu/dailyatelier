# DailyAtelier 개발 계획

> [!IMPORTANT]
> `PLAN.md`는 현재 승인된 작업 계획을 관리한다.
>
> 작업 완료 후 핵심 구현·검증 결과를 `PLAN_DONE.md`에 기록한 다음 해당 계획을 `PLAN.md`에서 제거한다.
>
> 현재 범위에 포함되지 않은 미완료·후속 작업은 `BACKLOG.md`에서 관리한다.
>
> 새 계획이나 범위 변경은 사용자 승인 후 반영한다.

## 통합 마이페이지 React 구현

> 상태: 구현 승인 대기. 확정 정적 시안의 정보 구조와 기존 기능을 결합하되, 정적 시안·기존 route·권한 계약은 변경하지 않는다.

### 목표와 범위

- 확정 정적 시안 `design-prototypes/mypage-unified-menu-refined/`의 구매·판매 통합 정보 구조와 반응형 레이아웃을 `/mypage`에 적용한다.
- 일반 구매자는 구매 활동만, 작가는 판매 관리 기본 진입과 구매 활동 전환을 제공한다.
- 기존 Header, `PrivateRoute`/`ArtistRoute`, 개별 기능 route 및 주문 상세 동작을 보존한다.
- React·프런트 테스트가 주 변경 후보이며, 판매 긴급 업무와 대표 입찰에 한해 선택적 API query를 최소 추가한다. DB migration은 포함하지 않는다.

### 보존할 기존 계약

| 정적 메뉴/CTA | 유지할 기존 경로 또는 동작 |
| --- | --- |
| 입찰 현황 / 다시 입찰 | `/mypage/bid-status`, `/auction/:artId` |
| 낙찰 작품 | `/mypage/successful-bid` |
| 구매 주문 / 결제 대기 | `/mypage/order-status`, 대상 주문은 `?orderId=:id` |
| 찜한 작품 / 작성한 리뷰 / 문의 / 프로필 | `/mypage/likes`, `/mypage/my-review`, `/mypage/inquiry`, `/mypage/profile-edit` |
| 포인트·입찰 예치 포인트 | `/charge` 및 기존 포인트 내역 진입 동선 |
| 작품 등록 / 관리 / 판매 주문 / 판매 작품 리뷰 | `/upload`, `/mypage/manage-arts`, `/mypage/sales-orders`, `/mypage/artist-review` |
| 배송 준비·발송·환불 판단 | 기존 `SalesOrders`의 상태 전이·상세 UI를 재사용 |

`사용 가능 포인트`와 `입찰 예치 포인트` 명칭은 기존 프로필의 `availablePoint`, `heldPoint`를 그대로 사용한다. `예치금`으로 통합하거나 금전 잔액처럼 표시하지 않는다.

### 구현 단계

1. **통합 셸과 권한별 탐색**
   - 현재 `/mypage`의 sidebar 기반 허브를 시안의 제목·자산 요약·탭·메뉴 구조로 교체한다.
   - 작가만 `판매 관리 / 구매 활동` 탭을 보며 기본값은 판매 관리다. 일반 구매자는 탭과 판매 메뉴·판매 데이터 요청을 렌더·마운트하지 않고 구매 활동만 표시한다.
   - 메뉴는 위 표의 기존 route를 `Link`로 연결한다. 모바일은 시안의 2열 구매 6개·판매 4개와 공통 메뉴 구분을 유지한다.

2. **구매 활동 요약과 결제 우선순위**
   - 프로필 1회 조회로 제목과 `availablePoint`/`heldPoint`를 표시한다. 통합 홈은 전체 페이지 순회 helper를 사용하지 않고 대표 항목만 작은 페이지 크기로 조회한다.
   - 대표 입찰은 새 선택 query `state=ACTIVE&page=0&size=1`로 조회한다. DB에서 진행 중 작품만 먼저 좁히고 기존 `max(bidTime) DESC, artId DESC` 순서를 유지한다. `totalElements === 0`일 때만 진행 중 입찰 0건으로 표시한다.
   - 대표 진행 작품은 기존 `getMyArts({ state: 'ACTIVE', page: 0, size: 2 })`를 사용한다. 현재 `artId DESC`는 가장 최근 등록된 진행 작품 선정 기준이며, `totalElements === 0`일 때만 0건이다.
   - 결제 대기는 `getBuyerOrders({ status: 'PAYMENT_PENDING', page: 0, size: 1 })`로 확인하고 없으면 안내를 렌더하지 않는다. `shippingAddressConfirmed`가 거짓이면 `배송지 입력하기`, 참이면 `결제하기`를 표시하고, 모두 `/mypage/order-status?orderId=:id`의 기존 상세 흐름으로 연결한다. 로딩·오류에는 추측 CTA를 표시하지 않는다.
   - 최근 활동은 통합 API 없이 입찰·주문·문의의 제한된 페이지 0 후보에서 실제 시각(`lastBidTime`, `createdAt`, `answeredAt ?? createdAt`)이 있는 항목만 합쳐 내림차순으로 정렬한 상위 2행이다. 전역 전체 시간순 feed로 표현하지 않으며, 출처와 기존 상세 목적지를 분명히 한다.

3. **판매 관리 요약과 긴급 업무**
   - 작가 프로필과 `getMyArts` 첫 페이지로 소개·진행 작품을 표시하고, 판매 리뷰는 기존 `getArtistReviews` 첫 페이지 요약과 기존 전체 route를 사용한다.
   - 배송 준비는 기존 `status=PAID&priority=OLDEST_WAITING`으로 `paidAt ASC, orderId ASC` 정렬과 전체 건수를 표시한다.
   - 환불 요청은 선택적 `refundRequestStatus=REQUESTED&priority=OLDEST_WAITING`으로 `refundRequestedAt ASC, orderId ASC` 정렬과 전체 건수를 표시한다. `paidAt`, `refundRequestedAt`은 주문 상태 전이 시각으로 저장되어 별도 migration이 필요 없다.
   - 기본 판매 주문 목록의 `createdAt DESC`는 유지한다. 긴급 `priority`는 홈과 긴급 업무용 전체 보기에서만 사용한다.
   - 긴급 카드의 전체 보기는 각각 `/mypage/sales-orders?status=PAID&priority=OLDEST_WAITING`, `/mypage/sales-orders?refundRequestStatus=REQUESTED&priority=OLDEST_WAITING`로 연결한다. 카드 자체에는 `orderId=:id`를 더한다.
   - `SalesOrders`는 `?orderId=:id` 진입 시 현재 목록 페이지와 무관하게 `getSellerOrder(orderId)`를 직접 조회해 대상 상세를 자동으로 연다. 대상이 목록 첫 페이지에 없어도 주문 맥락·환불 사유·판단 버튼을 보장한다.

4. **반응형·상태 처리와 탭 복귀**
   - 1440px 가로 탐색, 390px 2열 메뉴와 결제 우선순위를 적용한다. 긴 작품명·보조 정보는 CTA와 같은 행을 강제하지 않는다.
   - 빈 데이터에는 예시 작품명·금액을 남기지 않고 역할별 빈 상태를 표시한다. 다수 데이터는 요약과 전체 보기로 기존 전용 화면에 위임한다.
   - 기존 `createLatestRequest`/`AbortSignal` 패턴으로 탭 전환·unmount 뒤 오래된 응답이 화면을 덮지 않게 한다. 전역 상태·새 캐시는 도입하지 않는다.
   - 작가 탭은 `/mypage?view=sell|buy`를 단일 상태 원천으로 사용한다. 기본값은 판매 관리이고, 탭 클릭은 browser history에 남겨 뒤로 가기·앞으로 가기에서 직전 탭으로 복귀한다. 일반 구매자는 query와 무관하게 구매 활동만 표시한다.

5. **변경 대상과 검증**
   - 변경 후보: `frontend/src/pages/MyPage/MyPage.jsx`, `MyPage.module.css`, 필요한 작은 표시 전용 컴포넌트, `SalesOrders.jsx`, `orderApi.js`, `userApi.js`, 판매 주문·입찰 controller/service/repository 및 API 계약 테스트, `MyPage.test.jsx`와 판매 주문 딥링크 회귀 테스트. `App.jsx`, Header, Flyway, 기존 route 이름은 바꾸지 않는다.
   - 컴포넌트 테스트: 권한별 UI·요청, 작가 기본 탭·history 복귀, 메뉴 목적지, 결제 대기·배송지 상태 CTA, 대표 항목 0건 기준, 최근 활동 2행, 로딩·오류·긴 제목을 확인한다.
   - 판매 주문 API·화면 테스트: 환불 필터, 긴급 정렬·페이지네이션, 기본 정렬 불변, 필터된 전체 목록, 목록 밖 `?orderId=` 상세를 확인한다. 입찰 API·화면 테스트: `state=ACTIVE`가 종료 작품을 제외하고 정확한 0건 판단에 쓰이는지 확인한다.
   - 자동 검증: `npm run test:component`, `npm run lint`, `npm run build` 및 기존 주문·입찰·리뷰·작품 관리·권한 route 회귀. 브라우저 QA는 구매자·작가의 1440px/390px, 결제 대기 유무, `PAYMENT_PENDING`·`PAID`·`PREPARING`·환불 요청, 빈·다수 데이터, 긴 제목과 메뉴 목적지를 읽기 중심으로 확인한다. 실제 상태 변경은 승인된 테스트 환경·방법에서만 수행한다.

### 완료 기준

- 일반 구매자에게 판매 탐색·요청이 없고, 작가에게 판매 기본 진입과 구매 전환이 있다.
- 기존 메뉴·CTA가 실제 route 또는 기존 상세 흐름으로 연결되며, 결제 대기 CTA의 문구와 다음 행동이 일치한다.
- 결제 대기 0건은 안내와 빈 여백 없이 사라지고, 상태별 CTA는 `availableActions` 및 배송지 확정 상태를 벗어나지 않는다.
- 1440px·390px에서 승인 시안의 우선순위·메뉴 구조를 유지하며, 기존 전용 기능 화면과 권한 검사가 회귀하지 않는다.
