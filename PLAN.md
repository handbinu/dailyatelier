# DailyAtelier 개발 계획

> [!IMPORTANT]
> `PLAN.md`는 현재 승인된 작업 계획을 관리한다.
>
> 작업 완료 후 핵심 구현·검증 결과를 `PLAN_DONE.md`에 기록한 다음 해당 계획을 `PLAN.md`에서 제거한다.
>
> 현재 범위에 포함되지 않은 미완료·후속 작업은 `BACKLOG.md`에서 관리한다.
>
> 새 계획이나 범위 변경은 사용자 승인 후 반영한다.

---

# UI 스크린샷 수집 신뢰성 개선 계획

## 목표와 범위

- 기존 72개 촬영 지점, manifest 형식, 모달의 `viewport` 캡처 정책은 유지한다.
- 고정 시간 대기 없이 데이터 목록과 이미지가 실제로 준비된 경우에만 `captured`로 기록한다.
- 준비 확인 실패·timeout·이미지 decode 실패는 PNG를 정상 `captured`로 남기지 않고 기존 `failed` 상태와 `reason`에 기록한다.
- 공통 QA/화면 동작은 바꾸지 않는다. 수집 전용 Playwright 코드와 필요한 최소 selector만 다룬다.

## 원인 구분

| 증상 | 확인한 원인 | 처리 방침 |
| --- | --- | --- |
| Buyer 마이페이지·주문 조회, Admin 문의 관리가 loading 상태로 촬영됨 | `capture.ts`는 `prepare()` 뒤 즉시 screenshot을 찍고, 공통 route 준비는 `domcontentloaded`와 heading 표시만 확인한다. 각 React 화면은 별도 API 완료 뒤 loading 문구를 제거한다. | 해당 촬영 지점에서 loading 문구 소멸과 fixture의 실제 목록/요약 요소 표시를 기다린다. |
| 홈·찜·작품 관리·리뷰 카드의 이미지 공백, 회원유형 아이콘 일시 누락 | 카드 이미지가 `loading="lazy"`이고, 현재 캡처는 화면 밖 lazy 이미지를 활성화하거나 `img.complete`/`naturalWidth`를 확인하지 않는다. 아이콘도 DOM 표시 직후 캡처될 수 있다. | 실제 캡처에 보이는 렌더링 이미지로 범위를 한정해 스크롤 활성화와 decode 완료를 확인한다. 기존 `onError` fallback이 적용된 이미지는 fallback의 정상 decode를 허용한다. |
| 긴 full-page PNG의 Header 중복 | 글로벌 Header는 `position: fixed`이고, `Layout`의 `main`은 이미 Header 높이만큼 `margin-top`을 갖는다. fixed를 static으로 바꾸면 Header가 flow에 편입되어 본문이 추가로 밀릴 수 있다. | full-page 캡처 순간에만 Header를 flow 밖의 `absolute` 위치로 바꿔 문서 최상단에 한 번만 그린다. 기존 `main` 여백은 유지하고, 촬영 직후 원래 style을 복원한다. viewport 모달 캡처에는 적용하지 않는다. |

## 최소 수정 계획

1. `frontend/e2e/screenshots/capture.ts`
   - `CaptureOptions`에 선택적 readiness 훅을 추가하고, `prepare()` 다음·screenshot 이전에 실행한다.
   - 수집 전용 이미지 준비 helper를 둔다. 대상은 `src`가 있고, 렌더링 크기가 0이 아니며 `display:none`·`visibility:hidden`·`aria-hidden`이 아닌 실제 캡처 영역의 `img`로 한정한다. full-page는 Header와 본문 이미지를 대상으로 각 요소를 viewport로 이동해 lazy load를 활성화한 뒤 원래 scroll 위치로 복원하고, viewport 모달은 dialog 안과 현재 viewport에 보이는 이미지만 검사한다.
   - 이미지 검사는 후보를 모두 활성화한 뒤 병렬로 `complete`, `naturalWidth > 0`, `decode()` 성공을 확인한다. 전체 이미지 준비 예산은 capture당 12초로 제한하며, 오류 fallback으로 `src`가 교체된 경우에는 교체 후 이미지를 다시 확인한다. 후보가 없으면 성공으로 처리한다. 숨겨진 반응형 이미지, 0 크기 장식 이미지, 화면 밖의 비캡처 modal/메뉴 리소스는 검사·실패 판정 대상에서 제외한다.
   - full-page일 때만 `#root > header`의 computed position이 `fixed`/`sticky`인지 확인한다. 해당 Header에만 캡처 직전 inline `position:absolute; top:0; left:0; right:0`를 임시 적용한다. `Layout.module.css`의 `main { margin-top: var(--header-height) }`는 건드리지 않아 현재 Header 공간과 본문 시작 위치를 유지하며, screenshot 성공·실패와 관계없이 원래 inline style을 복원한다. 모달 `viewport`은 현행 그대로 둔다.
   - readiness/image/header 처리 중 timeout 또는 오류가 나면 screenshot을 실행하지 않고 `status: failed`, 구체적 `reason`을 manifest attachment에 남긴다. 기존 console error·horizontal overflow 실패 판정도 유지한다.

2. `frontend/e2e/screenshots/screenshots.spec.ts`
   - 공통 `defineRoute`는 현재 heading 확인을 유지하되, 필요한 지점만 선택적 readiness를 넘길 수 있게 한다. 72개 route 정의나 capture mode는 바꾸지 않는다.
   - Buyer 마이페이지는 `프로필 정보를 불러오는 중입니다.` 소멸과 프로필/요약의 실제 데이터 표시를, 주문 조회·상세 진입은 `주문 내역을 불러오는 중입니다.` 소멸과 주문 행 표시를 기다린다. 각 데이터 readiness locator의 timeout은 10초로 제한한다.
   - Admin 문의 관리는 `문의 목록을 불러오는 중입니다.` 소멸과 local-demo 문의 목록 버튼 표시를 10초 안에 기다린다. 이 조건이 실패하면 PC 결과를 정상 수집으로 기록하지 않는다.
   - 홈·찜·작품 관리·작가/구매자 리뷰 및 회원유형 선택은 기존 화면 진입 조건 후 이미지 helper를 사용한다. 상세·모달은 현재 dialog 표시 조건을 먼저 만족시킨 뒤, dialog 내부 이미지까지 준비 확인한다.

3. React 화면 코드
   - 이번 최소 수정에서는 `MyPage.jsx`, `OrderStatus.jsx`, `AdminInquiry.jsx`, `Home.jsx`, `ManageArts.jsx`, `ArtistReview.jsx`, `ArtResults.jsx`, `RegisterSelect.jsx`의 로딩 상태나 lazy 속성을 바꾸지 않는다.
   - 위 화면들은 이미 loading 문구, 목록/button 역할, 이미지 `alt`를 제공하므로 screenshot spec의 readiness locator로 충분하다. 구현 중 안정적으로 식별할 selector가 부족한 특정 화면만 `data-*` 속성을 추가하는 것으로 한정하고, 상태 관리·UI 동작 리팩터링은 하지 않는다.

## 검증 계획

1. 변경 전과 동일한 screenshot spec을 desktop/mobile 모두 실행해 72개 manifest record 수, route/key/state, capture mode(모달 6건 viewport)를 비교한다.
2. Buyer 마이페이지·주문 조회와 Admin 문의 관리에서 loading 문자열이 남은 PNG는 `captured`가 아니어야 하며, 성공 PNG에는 fixture 데이터 행이 보여야 한다.
3. 홈·찜·작품 관리·리뷰·회원유형 선택의 PNG에서 필수 이미지가 decode된 상태인지 확인한다. 의도된 fallback은 이미지 오류가 아니라 fallback 렌더링으로 판정한다.
4. 긴 mobile full-page의 Header가 문서 중간에 반복되지 않는지, viewport 모달의 overlay·고정 Header 동작이 변하지 않는지 원본 PNG로 비교한다.
5. 기존 화면 단위 React 테스트와 screenshot spec의 실패/manifest reason을 확인한다. 수집 안정화가 일반 QA의 API·UI 동작을 변경하지 않았는지 확인한다.

## 완료 기준

- 준비되지 않은 데이터·이미지가 `captured`로 기록되지 않는다.
- 실제 준비된 72개 촬영 결과의 manifest 계약과 modal viewport 정책이 유지된다.
- fixed Header 중복 없이 full-page 결과를 얻거나, 방지할 수 없는 경우 명시적 `failed` reason으로 재검증 대상이 남는다.
