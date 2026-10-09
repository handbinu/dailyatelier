# DailyAtelier 공통 UI 가이드

이 가이드는 DailyAtelier의 화면을 새로 만들거나 기존 화면을 감사할 때 쓰는 시각 기준이다. 특정 화면의 레이아웃을 복제하는 문서가 아니라, 화면 역할에 맞는 정보 위계·surface·행동 우선순위를 정하는 기준이다.

## 1. 기준과 감사 관점

- 새 화면은 기존 화면과 비슷해 보이는지보다, 정보의 중요도와 행동의 우선순위가 명확한지를 먼저 확인한다.
- 기존 화면을 수정할 때는 gray-heavy UI, 낮은 타이포 대비, 구식 CTA 위계, tinted active form, 과도한 box/chip, desktop/mobile 시각 언어 불일치를 우선적으로 찾는다.
- `/upload`의 긴 form 흐름은 참고 사례일 뿐이다. card 수, 진행 패널, sticky UI를 다른 화면에 기계적으로 복제하지 않는다. 다만 sage gradient, drawing-paper texture, butter 최종 CTA는 공통 브랜드 언어로 사용한다.

## 2. 레이아웃과 여백

- 일반 목록·관리 페이지는 최대 `1280px` 컨테이너와 공통 외곽 기준선을 사용한다. 데스크톱 기본 gutter는 `24px`, 모바일은 `16px`이다.
- 같은 페이지의 주요 영역은 동일한 외곽 기준선을 우선한다. 좁은 읽기·입력 영역이 필요하면 섹션 전체가 아니라 내부 콘텐츠에만 `640~800px` 등의 최대 폭을 둔다.
- 전체 일반 page canvas는 warm cream으로 두고, 그 위의 white card 또는 white active form surface를 기본으로 둔다.
- card와 section은 충분한 내부 여백과 vertical rhythm을 갖는다. 작은 요소를 계속 box로 분리해 정보의 흐름을 끊지 않는다.
- 브랜딩 성격의 hero·소개 영역만 전체 폭을 사용할 수 있다. 일반 콘텐츠는 컨테이너 안에서 정렬한다.
- 데스크톱의 마이페이지는 sidebar와 본문을 분리할 수 있으나, 모바일에서는 하나의 읽기·행동 흐름으로 전환한다.

## 3. 색상 역할

- warm cream: 기본 page background와 넓은 콘텐츠 캔버스에 사용한다.
- white: card, enabled input/select/textarea, 기본 form surface에 사용한다.
- charcoal: 페이지·section 제목, field label, 본문, 핵심 값처럼 읽기 우선 정보에 사용한다.
- deep sage: 현재 위치, focus, 선택, 진행, 보조 행동, 구조적 강조에 사용한다.
- sage: 진행 상태, 완료 상태, 번호, secondary accent에 사용한다.
- light sage: 계산 결과, 중요한 안내, 진행 보조 정보처럼 의미가 있는 surface에 사용한다.
- butter: 등록, 가입, 저장, 제출, 확정처럼 사용자의 최종 핵심 결정을 실행하는 CTA에 사용한다. 한 화면·한 결정 맥락에서 butter CTA가 여러 개 경쟁하지 않게 한다.
- danger: 오류, 삭제, 되돌리기 어려운 행동에만 사용한다.
- gray 또는 muted neutral은 날짜·보조 메타·낮은 중요도의 맥락에만 사용한다. 읽어야 하는 규칙이나 helper를 회색 작은 글씨로 숨기지 않는다.
- 새 색상을 추가하기 전에 기존 역할 토큰으로 표현할 수 있는지 검토한다.

## 4. 타이포와 정보 위계

- 밝은 surface의 페이지 제목은 charcoal을 기본으로 한다. dark/brand banner에서는 충분한 대비의 light text를 사용할 수 있다.
- section title은 화면 구조를 빠르게 파악할 수 있을 정도로 분명하게 둔다. card·목록 제목은 그보다 한 단계 낮은 강도로 둔다.
- section description은 다음 입력 또는 다음 읽기 흐름을 설명한다. 일반 helper보다 충분한 대비와 line-height를 유지한다.
- field label은 charcoal과 굵기로 명확히 구분한다. placeholder는 입력 예시이므로 label을 대체하지 않는다.
- 일반 helper는 보조 정보로 두되, 금액·정책·제한·다음 행동을 바꾸는 rule/helper는 charcoal 중심의 읽기 쉬운 대비로 올린다.
- 제목, 핵심 값, 설명, 날짜, placeholder가 모두 같은 강도로 경쟁하지 않게 한다.

## 5. Surface와 card

- 일반 콘텐츠의 기본 조합은 cream page와 white card다.
- card는 실제로 관련 있는 정보 또는 행동을 묶을 때 사용한다. 모든 문장, 값, 보조 정보에 별도 gray box를 만들지 않는다.
- light sage surface는 계산 결과, 상태 요약, 중요한 안내처럼 surface 자체가 의미를 가질 때만 사용한다.
- radius와 shadow는 구조를 보조하는 수준으로 제한한다. card마다 강한 radius, shadow, 색면을 반복하지 않는다.
- 구분선은 긴 정보 흐름을 나눌 때 사용하고, 장식적인 box 중첩을 대신하는 수단으로 활용한다.

## 6. Form, helper, validation

- enabled input, select, textarea의 표면은 선택 여부와 무관하게 white를 유지한다.
- focus는 white surface를 유지한 채 sage 또는 deep sage border/ring으로 명확히 표시한다.
- disabled와 readonly에만 warm neutral 또는 light tint를 사용한다. 사용 가능한 form control을 상태 표현을 위해 tinted surface로 만들지 않는다.
- native control은 기능과 접근성을 유지하되, border, surface, typography, focus 상태가 전체 UI와 크게 어긋나지 않게 정돈한다.
- placeholder는 예시를 제공하고, label은 field의 의미를 설명한다.
- error는 danger 색과 field 가까운 메시지로 전달한다. 여러 오류가 있는 긴 form에서는 상단 summary를 함께 제공할 수 있다.
- helper, 계산 결과, error는 각각 다른 semantic role이다. 같은 스타일의 작은 회색 문구로 합치지 않는다.

## 7. CTA와 행동 위계

- 최종 primary action은 butter fill과 charcoal text를 기본으로 한다. 등록, 가입, 저장, 제출, 확정처럼 완료를 만드는 행동에만 사용한다.
- deep sage/sage는 선택, 진행, 상태, 구조적 강조와 보조 행동에 사용한다.
- 같은 결정 맥락 안에서 여러 primary action이 같은 강도로 경쟁하지 않게 한다. 목록·관리 화면처럼 card마다 독립적인 주요 행동이 필요한 경우에는 각 card 안에서 우선순위를 분명히 한다.
- secondary action은 white 또는 transparent surface와 border를 기본으로 한다.
- danger action은 danger text 또는 outline 중심으로 두고, 일반 primary와 같은 강한 fill 경쟁을 피한다.
- butter CTA는 해당 흐름의 최종 결정을 하나만 명확히 표시한다. 보조 행동, 필터, 중복 확인에는 사용하지 않는다.
- charcoal CTA는 전역 이동 또는 구조적으로 강한 중립 행동처럼 예외적인 경우에만 사용한다.
- 기본 클릭·입력 컨트롤은 44px 이상을 우선한다. 조밀한 card 안의 보조 행동만 36~40px까지 줄일 수 있다.

## 8. 상태, 진행, 보조 UI

- 진행 단계는 색상만으로 상태를 전달하지 않고 텍스트·아이콘·형태 등의 비색상 단서를 함께 제공한다.
- desktop auxiliary UI는 정보량이 많고 긴 흐름의 위치 인지가 실제로 필요할 때만 사용한다.
- 모바일에서는 desktop sidebar를 그대로 세로 적층하지 않는다. 핵심 단계·현재 위치·남은 흐름만 남긴 compact UI로 바꿀 수 있다.
- sticky UI는 긴 흐름에서 현재 위치 인지가 필요할 때만 사용한다. 짧은 페이지의 장식적 sticky 사용은 피한다.
- chip과 pill은 상태 필터, 짧은 상태값, 제한된 선택 집합에 사용한다. 모든 메타 정보와 행동을 chip으로 만들지 않는다.

## 9. 모바일과 반응형 우선순위

- mobile은 desktop의 단순 세로 적층이 아니라, 화면 목적에 맞는 별도 정보 우선순위다.
- 표와 다열 layout은 식별 정보, 현재 상태, 핵심 값, 가능한 다음 행동 순으로 재구성한다.
- 보조 분류와 상세 메타는 숨기기보다 접기 영역 또는 하단 맥락으로 이동하는 방식을 먼저 검토한다.
- 긴 form에서는 충분한 gutter와 section 간 vertical rhythm을 유지한다.
- 버튼은 필요한 경우 full width 또는 명확한 줄바꿈으로 터치하기 쉽게 만들되, danger action이 primary보다 먼저 눈에 띄지 않게 한다.

## 10. Motion과 접근성

- motion은 선택 완료, 진행 상태 변경, 이미지 미리보기처럼 상태 변화를 확인시키는 짧은 micro-interaction에만 사용한다.
- page transition, 과도한 scroll animation, 반복적인 주의 끌기 animation은 사용하지 않는다.
- `prefers-reduced-motion: reduce`에서 transition과 animation을 제거하거나 정적인 상태로 대체한다.
- focus-visible 상태는 keyboard 사용자에게 충분히 명확해야 한다.

## 11. 브랜드 surface와 장식

- 일반 UI는 border, 명도 차이, 제한된 surface 색으로 정보 위계를 만든다.
- gradient, texture, shadow는 의미가 있는 경우에만 사용한다.
- 큰 색면이 필요한 화면은 평평한 SaaS block처럼 보이지 않도록 해당 화면 성격에 맞는 texture, gradient, 이미지, 명도 변화를 선택할 수 있다.
- 공통 PageBanner는 sage gradient와 drawing-paper texture 계열을 기본으로 사용한다. `/upload`는 이 공통 브랜드 언어를 더 강하게 활용한 사례이며, 화면별 차이는 제목·높이·내용에서만 둔다.

## 12. 화면 감사 체크리스트

- page background와 card/form surface의 역할이 cream/white로 분명한가?
- 제목, label, 핵심 값이 charcoal 중심으로 충분히 읽히는가?
- 중요한 helper와 정책 설명이 muted caption처럼 묻히지 않는가?
- enabled form control이 white이고, focus·disabled·error 상태가 semantic하게 분리되는가?
- 최종 CTA가 butter로 하나만 명확히 읽히고, sage/deep sage 보조 행동과 경쟁하지 않는가?
- PageBanner가 sage gradient와 drawing-paper texture의 공통 브랜드 언어를 따르는가?
- light sage surface가 실제 안내·계산·상태 정보를 담는가?
- 불필요한 gray box, chip, shadow, radius가 정보 밀도를 높이고 있지는 않은가?
- desktop auxiliary UI가 mobile에서 compact하고 목적에 맞는 형태로 전환되는가?
- motion이 짧고 상태 중심이며 reduced-motion 대응을 갖추는가?
