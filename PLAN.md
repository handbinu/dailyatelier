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

# 작품 수정 저장·삭제 CTA 배치 및 높이 보정 계획

## 범위와 원인

- 대상은 `frontend/src/pages/MyPage/EditArt.module.css`의 desktop 및 mobile(`max-width: 640px`) 저장·삭제 버튼 레이아웃이다.
- desktop의 `.actions > :first-child { flex: 0 1 280px; }`가 column 레이아웃에서도 첫 버튼의 main-axis(높이) basis로 남아 `수정 저장` 버튼을 과도하게 키운다.
- desktop의 `justify-content: space-between`은 삭제와 저장 버튼을 액션 바 양 끝으로 분리한다.
- `EditArt.jsx`는 저장 submit과 삭제 button을 독립적으로 렌더링할 뿐이므로 JSX·저장/삭제 API·CTA 색상·다른 화면 디자인은 변경하지 않는다.
- 검색 결과 이미지 공백은 수집기 신뢰성 개선의 별도 범위이며 이 수정에 포함하지 않는다.

## 최소 수정

1. desktop `.actions`에서 `space-between`을 제거하고 DOM 순서 그대로 `수정 저장` → `작품 삭제`를 우측에 묶는다.
   - CSS ordering과 JSX 순서 변경 없이, 시각 순서와 keyboard Tab 순서를 일치시킨다.
   - 두 버튼은 동일한 약 140~160px 너비와 12~16px 간격을 사용한다.
   - 삭제는 기존 danger outline, 저장은 기존 sage 채움 CTA를 유지한다.

2. mobile media query에서 column 레이아웃과 기존 `flex-basis: auto` 보정을 유지한다.
   - `수정 저장`은 full width와 최소 높이 52px을 유지한다.
   - `작품 삭제`는 저장 아래에 12~16px 간격으로 배치하고, 내용 너비의 compact outline으로 우측 정렬한다. full width는 적용하지 않는다.
   - 삭제 버튼의 최소 높이, outline·disabled 스타일은 기존 값을 유지한다.

3. 삭제 버튼의 outline·disabled 처리와 form submit/삭제 핸들러는 수정하지 않는다.
   - 저장 CTA의 sage 색상은 이번 결함의 원인이 아니므로 변경하지 않는다.

## 검증 계획

1. desktop 브라우저에서 `수정 저장` → `작품 삭제`가 우측에 나란히, 동일한 약 140~160px 너비와 12~16px 간격으로 표시되는지 확인한다. 액션 바 양 끝으로 분리되지 않아야 하며, Tab도 같은 순서로 이동해야 한다.
2. mobile 브라우저에서 `수정 저장`이 full width·최소 52px 높이이고, `작품 삭제`가 12~16px 아래 우측의 compact outline으로 표시되는지 확인한다. 저장 버튼의 280px 높이 재발이 없어야 한다.
3. 저장 성공·validation error·저장 중 disabled 및 삭제 확인/삭제 중 disabled 흐름을 기존 React 테스트와 수동 브라우저 QA로 회귀 확인한다.
4. 이 변경과 별개로 검색 카드 이미지 공백은 검증 대상에 포함하지 않으며, 해당 결과는 수집기 계획에서 재검증한다.

## 완료 기준

- mobile `수정 저장` 버튼에 desktop의 280px height basis가 적용되지 않는다.
- desktop에서는 두 버튼이 우측에 가까이 묶이고, mobile에서는 저장 full-width와 삭제 compact outline의 위계가 분명하다.
- 기존 색상, 두 버튼의 disabled·validation 처리와 저장·삭제 기능이 유지된다.
