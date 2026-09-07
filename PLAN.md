# DailyAtelier 개발 계획

> [!IMPORTANT]
> 완료된 계획과 검증 결과는 `PLAN_DONE.md`에서 관리한다.
>
> 아직 구현하지 않은 후속 작업은 `BACKLOG.md`에서 관리한다.
>
> 새 작업 계획은 사용자 요청 범위가 확정된 뒤 이 문서에 추가한다.

---

# 미정의 `--space-7` 사용처 정리

## 목표와 범위

- 전역 spacing scale은 변경하지 않고, 정의되지 않은 `--space-7` 4개 사용처만 기존 토큰으로 교체한다.
- 대상 파일은 다음으로 한정한다.
  - `frontend/src/pages/MyPage/UploadSell.module.css`
  - `frontend/src/pages/MyPage/MyReview.module.css`
  - `frontend/src/pages/MyPage/ArtistReview.module.css`
  - `frontend/src/pages/MyPage/InquiryWrite.module.css`
- 컴포넌트 구조, 공통 스타일 추출, 디자인 토큰 체계 재정비, 다른 spacing 사용처 변경은 범위에서 제외한다.

## 사전 판단

- `--space-7`은 `Global.css`에 정의되어 있지 않아, fallback 없는 해당 선언 전체가 브라우저에서 무효가 된다.
- `InquiryWrite`의 취소 버튼은 동일한 버튼 패턴인 `WriteReview` 및 `ProfileEdit`에 맞춰 `--space-6`으로 교체한다.
- `UploadSell`의 폼 카드 간격은 유사 등록 화면인 `WriteReview`의 섹션 간격에 맞춰 `--space-8`으로 교체한다.
- `MyReview`와 `ArtistReview`의 상세 모달은 기존 페이지 공통 content inset(세로 `--space-8`, 가로 `--space-6`) 및 모달 내부 위계를 기준으로 `--space-6`을 사용한다. 기존 의도값으로 보이는 28px을 새 토큰으로 복원하지 않으며, 모달은 브라우저 QA에서 여백 균형을 확인한다.

## 실행 단계

1. `UploadSell.module.css`와 `InquiryWrite.module.css`의 선언을 교체한다.
   - `.form`의 `gap: var(--space-7)`을 `gap: var(--space-8)`으로 변경한다.
   - `.cancelBtn`의 가로 padding `var(--space-7)`을 `var(--space-6)`으로 변경한다.

2. 리뷰 상세 모달의 padding을 교체한다.
   - `MyReview.module.css`의 `.modalDetail` 가로 padding을 `--space-6`으로 변경해 `padding: var(--space-8) var(--space-6)`으로 맞춘다.
   - `ArtistReview.module.css`의 `.modalDetail` 상·우·하 padding을 `--space-6`으로 변경해 `padding: var(--space-6) var(--space-6) var(--space-6) var(--space-4)`로 맞춘다.
   - 이미지 열과 상세 열의 분할 구조, 스크롤 영역, 모바일 media query는 변경하지 않는다.

3. 자동 검증과 브라우저 QA를 수행한다.
   - 전체 코드에서 `var(--space-7` 사용처가 0건인지 재검색한다. 문서의 과거 기록과 Backlog 문구는 수정·검사 대상에서 제외한다.
   - 프런트엔드 lint 및 관련 컴포넌트 테스트를 실행한다.
   - 아래 브라우저 QA 시나리오를 데스크톱과 모바일 폭에서 확인한다.

## 자동 검증

- `frontend` 디렉터리에서 `npm run lint`를 실행한다.
- 리뷰 모달 회귀 확인: `npm run test:component -- src/test/ReviewDialog.test.jsx src/test/ReviewList.test.jsx`.
- 문의 화면 회귀 확인: `npm run test:component -- src/test/Inquiry.test.jsx`.
- 작품 등록 화면 회귀 확인: `npm run test:component -- src/test/UploadSellClassification.test.jsx src/test/UploadSellBidIncrement.test.jsx`.
- `rg -n -F -- "var(--space-7" frontend/src` 결과가 없음을 확인한다.

## 브라우저 QA (사용자 확인)

1. 작가 계정으로 `/upload`에 진입해 작품 이미지·기본 정보·경매 정보 카드 사이가 충분히 분리되는지 확인한다. 카드 사이 간격은 32px이어야 한다.
2. `/mypage/inquiry/write`에서 '문의 등록'과 '취소' 버튼의 높이와 가로 여백이 균형적인지 확인한다. 취소 버튼은 유사 화면과 같은 24px 가로 padding이어야 한다.
3. `/mypage/my-review`에서 리뷰 상세를 열어 이미지 열과 정보 열 사이의 여백, 제목·본문·동작 버튼의 좌우 여백, 스크롤 시 내용이 가장자리에 붙지 않는지를 확인한다.
4. 작가 계정으로 `/mypage/artist-review`에서 리뷰 상세를 열어 이미지 열, 구매자 정보, 별점, 본문, 메타 정보의 여백 위계가 자연스러운지 확인한다. 특히 좌측 16px과 나머지 24px inset의 비대칭이 의도대로 보이는지 확인한다.
5. 두 리뷰 모달을 680px 이하 및 400px 이하 폭에서도 열어, 모달이 단일 열로 전환된 뒤 내용이 잘리거나 불필요하게 답답해지지 않는지 확인한다.

## 완료 기준

- 대상 4개 CSS 모듈에서만 `--space-7` 사용이 제거된다.
- 전역 `Global.css`와 다른 디자인 토큰은 변경하지 않는다.
- 자동 검증이 통과하고, 리뷰 모달의 여백에 대한 브라우저 QA 결과가 기록된다.
- QA에서 두 리뷰 모달의 `--space-6` 선택이 부자연스럽다고 판단되면, 해당 모달에 한해 `--space-8` 대안을 비교한 뒤 사용자 승인을 받아 후속 조정한다.
