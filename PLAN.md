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

# 모바일 작품 수정 저장 CTA 높이 보정 계획

## 범위와 원인

- 대상은 `frontend/src/pages/MyPage/EditArt.module.css`의 mobile(`max-width: 640px`) 버튼 레이아웃뿐이다.
- desktop의 `.actions > :first-child { flex: 0 1 280px; }`가 column 레이아웃에서도 첫 버튼의 main-axis(높이) basis로 남아 `수정 저장` 버튼을 과도하게 키운다.
- `EditArt.jsx`는 저장 submit과 삭제 button을 독립적으로 렌더링할 뿐이므로 JSX·저장/삭제 API·CTA 색상·다른 화면 디자인은 변경하지 않는다.
- 검색 결과 이미지 공백은 수집기 신뢰성 개선의 별도 범위이며 이 수정에 포함하지 않는다.

## 최소 수정

1. mobile media query의 `.actions > :first-child`에 `flex-basis: auto`(또는 동등한 flex reset)만 추가한다.
   - 기존 `width: 100%`를 유지해 저장 버튼은 mobile에서 full width를 유지한다.
   - 기존 `.saveButton, .deleteButton { min-height: 52px; }`를 유지해 두 버튼의 최소 터치 높이를 보존한다.
   - desktop media query 밖의 280px flex 규칙은 변경하지 않는다.

2. 삭제 버튼의 outline·disabled 처리와 form submit/삭제 핸들러는 수정하지 않는다.
   - 저장 CTA의 sage 색상은 이번 결함의 원인이 아니므로 변경하지 않는다.

## 검증 계획

1. mobile 브라우저에서 작품 수정 화면을 열어 `수정 저장`이 full width이되 52px 최소 높이 수준으로 렌더링되고, `작품 삭제`가 별도 행의 기존 outline·높이를 유지하는지 확인한다.
2. desktop 브라우저에서 저장 버튼이 기존의 가로 flex 배치와 280px 기준 폭으로 남는지 확인한다.
3. 저장 성공·validation error·저장 중 disabled 및 삭제 확인/삭제 중 disabled 흐름을 기존 React 테스트와 수동 브라우저 QA로 회귀 확인한다.
4. 이 변경과 별개로 검색 카드 이미지 공백은 검증 대상에 포함하지 않으며, 해당 결과는 수집기 계획에서 재검증한다.

## 완료 기준

- mobile `수정 저장` 버튼에 desktop의 280px height basis가 적용되지 않는다.
- desktop 레이아웃, 두 버튼의 기존 최소 높이와 저장·삭제 기능이 유지된다.
