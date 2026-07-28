# DailyAtelier 작품 수정·삭제 구현 계획

## 목표와 범위

- 인증된 소유 작가만 자신의 작품을 수정하거나 삭제할 수 있게 한다.
- 입찰 이력, 작품 상태, 실제 마감 시각에 따라 허용 작업을 서버에서 강제한다.
- 입찰·마감·수정·삭제가 동시에 요청되어도 작품 행 잠금 뒤 최신 조건을 다시 검사한다.
- 이번 구현 범위는 백엔드 수정·삭제 API와 상태 일관성, 자동화 테스트까지로 제한한다.
- 프론트 수정 화면과 삭제 버튼 연동, Cloudinary 원본 이미지 정리는 이번 범위에서 제외한다.

## 확정 정책

### 공통 조건

- 수정·삭제는 `artStatus = ACTIVE`이고 서버 시각이 `closingTime`보다 이른 작품만 대상으로 한다.
- 스케줄러 반영 전이라도 `closingTime`이 지났으면 `AUCTION_CLOSED` 충돌로 거절한다.
- `UNSOLD`, `SOLD`, `CANCELED` 작품은 수정과 추가 삭제를 모두 금지한다.
- 인증 사용자가 작가인지와 별개로 작품의 `artist.user.userId`가 요청자와 일치해야 한다.
- 존재하지 않는 작품은 `404`, 타인 작품은 `403`, 상태·입찰 정책 위반은 `409`, 입력값 오류는 `400`으로 구분한다.

### 수정 허용 범위

- 입찰 이력이 없으면 다음 필드를 수정할 수 있다.
  - 가격: `startPrice`
  - 기간: `bidStartTime`, `closingTime`
  - 비가격 정보: `descript`, `material`, `wIntro`, `imgPath`
- 입찰 이력이 없을 때 `startPrice`를 바꾸면 `currentPrice`도 같은 값으로 동기화한다.
- 기간 변경 후에도 `bidStartTime < closingTime`이고 `closingTime`은 현재보다 미래여야 한다.
- 입찰 이력이 하나라도 있으면 `descript`, `material`, `wIntro`, `imgPath`만 수정할 수 있다.
- 작품명 `name`은 이번 요청의 허용 필드에 포함되지 않으므로 수정 대상에서 제외한다.
- 부분 수정 요청은 필드의 미전달과 명시적 초기화를 구분해, 설명성 필드는 빈 값으로 지울 수 있게 한다.

### 삭제 처리

- 입찰 이력이 없고 공통 조건을 만족하면 작품 행을 물리 삭제한다.
- 입찰 이력이 하나라도 있고 공통 조건을 만족하면 행과 입찰 이력을 보존하고 `artStatus = CANCELED`로 전환한다.
- 입찰 이력은 없지만 리뷰가 존재하면 데이터 무결성을 위해 물리 삭제를 거절한다.
- 물리 삭제 전 찜 행의 작품 참조만 `null`로 분리하고 찜 행 자체는 보존한다.
- 작품 참조가 분리된 찜 목록 응답은 `artDeleted = true`, `availabilityMessage = "없어진 작품입니다."`로 반환한다.
- `Art.STATUS_CANCELED = 3`을 추가한다.
- 취소 전환 시 실제 처리 시각을 `closedAt`에 기록한다.
- 취소 작품은 공개 진행 목록과 입찰 대상에서 제외하고, 작가의 전체·종료 작품 조회에는 `CANCELED` 결과로 포함한다.
- 취소 작품은 입찰자의 내 입찰 목록에 그대로 보존하고 `bidResult = CANCELED`, `bidResultMessage = "작가가 취소한 경매입니다."`로 반환한다.
- 삭제 API는 실제 처리 결과를 `DELETED` 또는 `CANCELED`로 반환해 호출자가 구분할 수 있게 한다.

### 추후 프론트 UX

- 작품 삭제 버튼을 누르면 즉시 API를 호출하지 않고 `"정말 삭제하시겠습니까?"` 확인창을 먼저 표시한다.
- 사용자가 취소하면 아무 요청도 보내지 않고, 확인한 경우에만 삭제 API를 호출한다.
- 삭제 API가 `DELETED`를 반환하면 목록에서 작품을 제거하고, `CANCELED`를 반환하면 취소 상태로 갱신한다.
- 입찰자의 내 입찰 목록에서 `bidResult = CANCELED`인 작품에는 `"작가가 취소한 경매입니다."` 안내 문구를 표시한다.
- 확인창과 취소 안내 UI의 실제 구현은 프론트 연동 단계에서 진행한다.

## API 계약

### 작품 부분 수정

- `PATCH /api/arts/{artId}`
- 인증 필수이며 요청자 ID는 토큰에서만 가져온다.
- 가격·기간·설명·이미지 관련 필드만 받는 별도 `ArtUpdateRequestDto`를 사용한다.
- 잠금 획득 후 소유권, 상태, 마감 시각, 입찰 존재 여부와 필드 허용 범위를 다시 검사한다.
- 성공 시 변경된 작품 응답과 `200 OK`를 반환한다.

### 작품 삭제 또는 취소

- `DELETE /api/arts/{artId}`
- 인증 필수이며 요청자 ID는 토큰에서만 가져온다.
- 잠금 획득 후 소유권, 상태, 마감 시각과 입찰 존재 여부를 다시 검사한다.
- 성공 시 작품 ID, `DELETED|CANCELED`, 최종 상태를 담은 응답과 `200 OK`를 반환한다.

## 1. 상태·DTO·조회 기준 정리

- [x] `Art`에 `STATUS_CANCELED` 상수를 추가한다.
- [x] 전달 여부를 식별할 수 있는 부분 수정 DTO와 삭제 결과 DTO를 추가한다.
- [x] 수정 DTO에 가격 범위, 문자열 길이, 이미지 경로와 기간 입력 검증을 적용한다.
- [x] `MyArtState.ALL`, `MyArtState.ENDED`에 취소 상태를 포함한다.
- [x] 작가 작품 응답이 취소 작품을 `CANCELED`로 반환하도록 상태 매핑을 확장한다.
- [x] 입찰자 작품 응답이 취소 작품을 `CANCELED`와 안내 문구로 반환하도록 매핑을 확장한다.
- [x] 공개 목록, 상세 조회, 입찰, 마감 대상 조회에서 취소 상태의 동작이 기존 상태와 충돌하지 않는지 확인한다.

### 1단계 검증

- [x] 수정 요청의 유효성 오류와 미전달·초기화 필드 구분을 테스트한다.
- [x] 취소 작품이 공개 진행 목록과 입찰·마감 대상에서는 제외되고 작가 조회에는 포함되는지 테스트한다.
- [x] 입찰자의 취소 작품이 내 입찰 목록에 남고 취소 결과와 안내 문구를 제공하는지 테스트한다.
- [x] 기존 `ACTIVE`, `UNSOLD`, `SOLD` 응답과 조회 필터가 유지되는지 회귀 테스트한다.

## 2. 수정·삭제 트랜잭션 구현

- [x] `ArtController`에 `PATCH`, `DELETE` 엔드포인트를 추가한다.
- [x] 기존 입찰·마감과 동일한 작품 행 비관적 쓰기 잠금을 수정·삭제에도 사용한다.
- [x] `BidRepository`에 잠금 이후 입찰 이력 존재 여부를 확인하는 쿼리를 추가한다.
- [x] 물리 삭제 전 리뷰 존재 여부를 확인하고, 찜 행은 작품 참조만 분리해 보존한다.
- [x] 삭제 작품의 찜 목록 응답에 삭제 여부와 안내 문구를 제공한다.
- [x] `ArtService`에 소유권, 상태, 마감 시각, 입찰 유무를 한 트랜잭션 안에서 검사하는 공통 정책 로직을 둔다.
- [x] 입찰 전에는 가격·기간·비가격 정보를 수정하고 시작가 변경 시 현재가를 함께 동기화한다.
- [x] 입찰 후에는 허용된 비가격 필드만 반영하고 가격·기간 변경 시 전체 요청을 거절한다.
- [x] 입찰이 없는 작품은 물리 삭제하고, 입찰이 있는 작품은 `CANCELED` 상태로 전환한다.
- [x] 시간 경계 검증을 재현할 수 있도록 서버 시각 의존성을 `Clock`으로 주입한다.

### 2단계 검증

- [x] 비인증 요청, 일반 회원, 타인 작가, 소유 작가를 구분해 권한을 테스트한다.
- [x] 입찰 전 전체 허용 필드 수정과 시작가·현재가 동기화를 테스트한다.
- [x] 입찰 후 비가격 정보 수정 성공과 가격·기간 수정 거절을 테스트한다.
- [x] 입찰 없는 작품의 물리 삭제와 입찰 있는 작품의 취소 전환·이력 보존을 테스트한다.
- [x] 찜 행 보존·삭제 작품 안내와 리뷰 존재 시 물리 삭제 거절을 테스트한다.
- [x] 유찰·낙찰·취소 작품과 마감 시각이 지난 진행 작품의 수정·삭제 거절을 테스트한다.
- [x] 존재하지 않는 작품과 잘못된 기간·가격 요청의 HTTP 상태를 테스트한다.

## 3. 경합·회귀 통합 검증

- [x] 수정과 입찰이 경합하면 먼저 잠금을 얻은 작업 이후 최신 입찰 유무와 가격을 기준으로 두 번째 작업이 판단하는지 검증한다.
- [x] 삭제와 입찰이 경합하면 입찰 선행 시 취소 전환, 삭제 선행 시 후속 입찰 실패로 일관되는지 검증한다.
- [x] 수정·삭제와 마감이 경합하면 마감 선행 시 변경이 거절되고, 변경 선행 시 마감이 최신 데이터로 처리되는지 검증한다.
- [x] 같은 작품의 수정과 삭제가 경합할 때 후행 작업이 잠금 뒤 최신 상태 또는 존재 여부를 재검사하는지 검증한다.
- [x] 서로 다른 작품의 수정·삭제는 불필요하게 서로를 차단하지 않는지 검증한다.
- [x] 기존 입찰 동시성, 자동 마감, 작품 조회·등록 테스트를 포함한 백엔드 전체 테스트를 실행한다.
- [x] 테스트 통과와 변경 범위를 확인한 뒤 사용자 승인 전에는 커밋하거나 push하지 않는다.

## 구현 예정 파일

- `backend/src/main/java/com/dailyatelier/dailyatelier/controller/ArtController.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/service/ArtService.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/entity/Art.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/repository/ArtRepository.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/repository/BidRepository.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/repository/LikesRepository.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/repository/ReviewRepository.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/dto/LikeItemDto.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/dto/ArtUpdateRequestDto.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/dto/ArtDeleteResponseDto.java`
- `backend/src/main/java/com/dailyatelier/dailyatelier/dto/MyArtState.java`
- 관련 서비스·컨트롤러·트랜잭션·동시성 테스트 파일

## 기존 미완료 항목 보존

- [ ] `bid(art_id, bid_price, bid_time, bid_id)`: 최고 입찰 조회 권장 인덱스
- 실제 MySQL 실행 계획을 확인한 뒤 중복 인덱스가 있으면 추가하지 않는다.
