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

## Cloudinary 고아 이미지 안전 정리

### 목표와 범위

- 작품 이미지 교체, 프로필 이미지 교체, 입찰·리뷰가 없는 작품의 물리 삭제로 더 이상 참조되지 않는 Cloudinary 원본을 정리한다.
- DB 변경과 Cloudinary 삭제를 하나의 분산 트랜잭션처럼 묶지 않는다. DB 트랜잭션에는 새 이미지 참조 반영과 이전 이미지 cleanup 작업 등록만 포함하고, 실제 Cloudinary 삭제는 commit 이후 재시도 가능한 작업자가 수행한다.
- 입찰 이력이 있어 `CANCELED` 상태로 보존되는 작품과 그 이미지는 삭제 대상에 넣지 않는다.
- 작품과 프로필에 필요한 필드·서비스만 확장한다. 공통 media 도메인, 범용 파일 관리 시스템, 메시지 브로커 도입은 이번 범위에 포함하지 않는다.

### 확정 설계

#### 이미지 식별자와 업로드 계약

- Cloudinary 삭제의 애플리케이션 식별자는 `public_id`로 통일한다. `secure_url`은 화면 표시용, `public_id`는 원본 삭제용으로 역할을 분리한다.
- `art`에는 nullable `cloudinary_public_id`, `users`에는 nullable `profile_image_public_id`를 추가한다. 기존 URL-only 행과 외부·fixture URL을 수용하기 위해 처음부터 `NOT NULL`로 강제하지 않는다.
- 작품 생성·수정 요청은 이미지가 새로 업로드된 경우 URL과 `publicId`를 함께 전달한다. 기존 이미지를 유지하는 수정은 저장된 URL과 식별자를 그대로 유지한다.
- Cloudinary 폴더 namespace는 작품 `arts/{userId}`, 프로필 `profiles/{userId}`로 분리한다. 작품 업로드 서명은 인증 사용자의 작품 폴더에만 발급하고, 프로필 서버 업로드도 인증 사용자의 프로필 폴더만 사용해 도메인 간 식별자 충돌과 오삭제를 막는다.
- 작품 API는 새 `publicId`가 현재 사용자의 작품 폴더에 속하는지뿐 아니라 `secureUrl`을 Cloudinary URL 규칙에 따라 정규화·파싱했을 때 cloud name, resource type, delivery type, 버전 이후 경로와 확장자를 제외한 public ID가 함께 전달된 `publicId`와 정확히 일치하는지 검증한다. URL 파싱이 모호하거나 변환 경로가 허용 계약을 벗어나면 저장을 거부하며, `cloudinary_public_id`에는 유니크 제약을 두어 같은 자산의 중복 연결을 막는다.
- 프로필 업로드 서비스의 반환형을 URL 문자열에서 `secureUrl + publicId` 결과 객체로 바꾼다. 프로필은 서버가 Cloudinary 응답을 직접 받으므로 두 값을 함께 검증하고 `profiles/{userId}` namespace를 확인한 뒤 저장한다.
- `asset_id`는 이번 삭제 계약에 필요하지 않으므로 저장하지 않는다. 추후 자산 대사 요구가 생길 때 별도 검토한다.

#### cleanup/outbox와 삭제 시점

- 작품·프로필 전용으로 사용할 최소 `cloudinary_cleanup` 테이블을 추가한다. 최소 필드는 작업 ID, `public_id`, resource type, 상태, 시도 횟수, 다음 시도 시각, 마지막 오류, 생성·완료 시각으로 한다.
- 이미지 교체 시 서비스는 기존 `public_id`를 읽고 새 URL·`public_id`를 저장하면서, 기존 값이 있고 새 값과 다를 때만 cleanup 행을 같은 DB 트랜잭션에 기록한다.
- 작품 물리 삭제 시 작품 행을 제거하기 전에 저장된 `public_id`의 cleanup 행을 같은 트랜잭션에 기록한다. 입찰 보유 작품의 취소 분기에는 cleanup 행을 만들지 않는다.
- Cloudinary 삭제 작업자는 미처리 또는 재시도 가능 작업만 조회해 DB commit 이후 `image/destroy`를 호출한다. 삭제 성공과 Cloudinary의 이미 삭제됨/not-found 응답은 모두 완료로 처리해 멱등성을 보장한다.
- 일시적 외부 오류는 제한된 지수형 backoff로 재시도하고, 최대 시도 횟수 초과 작업은 실패 상태와 마지막 오류를 보존한다. 요청 API의 성공 여부는 후속 Cloudinary 삭제 성공 여부에 의존하지 않는다.
- cleanup 상태 lifecycle은 `PENDING → PROCESSING → DONE`을 기본으로 하고, 재시도 가능한 오류는 `PENDING`으로 되돌리며 최대 시도 초과는 `FAILED`로 전환한다. 동시 작업자가 같은 행을 중복 처리하지 않도록 조건부 상태 전이 또는 잠금 조회로 선점한다.
- 동일 `public_id`에 대한 활성 작업은 `PENDING`/`PROCESSING` 동안 하나만 허용하되, `DONE`/`FAILED` 이력 때문에 영구적으로 재등록이 막히지 않게 한다. 단순 `UNIQUE(public_id)`는 사용하지 않고 활성 작업용 키 또는 별도 활성 플래그와 복합 유니크 제약, 상태를 확인하는 idempotent 등록 로직을 조합한다. 완료된 자산이 다시 참조되거나 cleanup 대상이 되는 비정상 경로는 참조 검증으로 삭제를 차단한다.

#### 실패 경계

- DB rollback 시 새 참조와 cleanup 등록이 함께 rollback되므로 현재 참조 중인 기존 이미지는 삭제되지 않는다.
- DB commit 후 Cloudinary 삭제가 실패하면 새 DB 참조는 유지되고 cleanup 행만 재시도 대상으로 남는다.
- Cloudinary 삭제를 먼저 실행한 뒤 DB를 변경하는 흐름은 만들지 않는다.
- 새 업로드 성공 후 작품·프로필 DB 저장 자체가 실패한 경우의 새 파일은 이번 cleanup 행으로 포착할 수 없음을 명시한다. 프로필 서버 업로드에는 실패 시 즉시 보상 삭제를 시도하되, 그 보상까지 실패한 건의 영속 재시도는 pending 자산 관리 없이는 보장하지 않는다.

#### 기존 URL-only 데이터

- Flyway는 식별자 컬럼을 nullable로 추가하며 URL에서 `public_id`를 SQL로 추정해 채우지 않는다. 외부 URL, 버전·변환 URL, 확장자와 폴더 경계를 잘못 해석해 다른 자산을 삭제할 위험을 피한다.
- 배포 이후 새 업로드부터 URL과 `public_id`를 항상 함께 저장한다.
- 기존 행은 식별자가 null이면 자동 삭제 대상에 넣지 않는다. 기존 Cloudinary 자산을 정리해야 할 경우 Cloudinary Admin API/내보내기 결과와 DB URL을 대조해 검증된 값만 별도 운영 절차로 backfill한다. 이 일회성 대사 도구 구현은 이번 범위에 포함하지 않는다.

#### 이번 범위에서 제외할 고아 경로

- 브라우저 작품 업로드 성공 후 생성·수정 API까지 도달하지 못한 pending 이미지는 이번 작업에서 제외하고 별도 후속으로 둔다.
- 이 경로까지 안전하게 처리하려면 업로드 intent/pending 상태, 완료 확인 API, 만료 시각과 주기 정리 정책이 추가되어 현재 목표보다 계약과 운영 범위가 커진다.
- 이번 작업은 DB가 한 번 참조한 이미지의 교체·물리 삭제에서 생기는 확정적 고아 정리에 집중한다. 후속 작업에서는 사용자별 폴더의 미참조 자산을 일정 유예기간 후 정리하는 방식과 업로드 intent 도입을 비교한다.

### 구현 단계

1. **DB와 도메인 계약 추가**
   - Flyway로 작품·프로필 `public_id` 컬럼과 `cloudinary_cleanup` 테이블·필수 인덱스/제약을 추가한다.
   - `Art`, `User`, 작품 생성·수정 DTO와 Cloudinary 업로드 결과 타입을 URL+식별자 계약으로 확장한다.
   - 기존 URL-only 행은 null 식별자로 정상 조회·수정 가능하게 유지한다.

2. **업로드와 교체 계약 연결**
   - 작품 서명을 `arts/{userId}`, 프로필 업로드를 `profiles/{userId}` namespace로 제한하고 프론트가 작품 Cloudinary 응답의 `secure_url`, `public_id`를 작품 API에 함께 전달하게 한다.
   - 백엔드가 작품 URL과 `publicId`에서 추출한 cloud/resource/delivery/public ID가 동일 자산을 나타내는지, 사용자별 namespace와 중복 연결 조건을 함께 검증한다.
   - 프로필 업로드가 URL+`public_id`를 반환하고 namespace를 확인한 뒤 한 트랜잭션에서 사용자 참조와 이전 이미지 cleanup을 저장하게 한다.

3. **작품 교체·물리 삭제 cleanup 등록**
   - 작품 이미지가 실제 교체될 때만 이전 식별자의 cleanup을 등록한다.
   - 입찰·리뷰가 없는 작품의 물리 삭제에는 cleanup을 등록하고, 입찰 보유 작품의 취소에는 등록하지 않는다.
   - rollback 시 작품/프로필 변경과 cleanup 등록이 함께 취소되는지 검증한다.

4. **commit 이후 삭제 작업자와 재시도**
   - Cloudinary 서명 기반 destroy 호출, not-found 성공 처리, 실패 상태·backoff·최대 시도 횟수와 동시 선점을 구현한다.
   - 스케줄 실행 주기와 재시도 값은 설정으로 분리하되 운영 UI나 수동 재처리 API는 추가하지 않는다.

5. **회귀 검증과 문서 정리**
   - 작품·프로필 API 계약, 삭제 분기, Flyway와 cleanup 재시도 테스트를 실행한다.
   - 구현 완료 후 `PLAN.md`에서 본 계획을 제거하고, 장기 참고가 필요한 계약과 검증 한계만 `PLAN_DONE.md`에 1~3개 bullet로 남긴다.
   - pending 업로드 정리가 필요하면 사용자 승인 후 `BACKLOG.md`에 별도 항목으로 추가한다.

### 완료 기준

- 새 작품·프로필 업로드는 URL과 `public_id`가 함께 저장된다.
- 작품·프로필 이미지 교체가 commit되면 이전 이미지 cleanup이 정확히 한 번 등록되고, rollback되면 등록되지 않는다.
- 입찰·리뷰가 없는 작품의 물리 삭제는 이미지 cleanup을 등록하지만, 입찰 보유 작품의 `CANCELED` 전환은 이미지 참조와 원본을 유지한다.
- Cloudinary 삭제 성공 또는 이미 삭제된 응답은 cleanup 완료로 기록된다.
- 일시적 삭제 실패는 API 트랜잭션을 되돌리지 않고 재시도되며, 최대 실패 후에도 대상과 오류가 DB에 남는다.
- 식별자가 없는 기존 URL-only 행은 오삭제 없이 기존 동작을 유지하며 자동 cleanup 대상에서 제외된다.
- 기존 API 오류는 공통 `ApiErrorResponseDto` 형식을 유지하고, 기존 작품 삭제의 `DELETED`/`CANCELED` 응답 의미를 변경하지 않는다.
- 범용 media 시스템이나 pending 업로드 관리 기능이 추가되지 않는다.

### 테스트 범위

- Flyway: 빈 MySQL 기준 신규 컬럼·nullable/unique 조건, cleanup 테이블·인덱스와 전체 migration 검증
- Cloudinary 서비스: 업로드 응답의 URL+`public_id` 파싱, 누락 응답 실패, destroy 서명/요청, 성공·not-found·일시 오류 분류
- 작품 서비스: 생성·교체 저장, 동일 이미지 유지, 물리 삭제 cleanup 등록, 취소 시 미등록, URL-only legacy 행 처리, 트랜잭션 rollback
- 프로필 서비스: 새 식별자 저장, 기존 이미지 cleanup 등록, 업로드 실패 시 기존 참조 유지, DB 실패 시 보상 삭제 시도
- cleanup 작업자: `PENDING/PROCESSING/DONE/FAILED` 상태 전이, 활성 작업 중복 등록 차단, 완료·실패 이력 이후의 안전한 재등록, 성공, 중복 실행 멱등성, 재시도 증가와 다음 시각, 최대 시도 실패 보존, 동시 선점
- API/프론트: 작품 생성·수정 payload에 `publicId` 포함, URL/publicId 동일 자산 검증의 성공·불일치 거부, 기존 이미지 유지 payload, 작품·프로필 namespace 분리, 사용자별 작품 폴더 서명, 기존 화면 성공·오류 동작 회귀
- 작품 삭제 통합 테스트: `DELETED`와 `CANCELED` 분기 및 찜 tombstone·입찰/예치 보존 계약 회귀

### 예상 수정 범위

- `backend/src/main/resources/db/migration/` 신규 Flyway와 migration 테스트
- `Art`, `User`, 신규 `CloudinaryCleanup` 엔티티와 repository
- `ArtCreateRequestDto`, `ArtUpdateRequestDto`, Cloudinary 업로드 결과/서명 DTO
- `CloudinaryService`, `ArtService`, `UserService`, cleanup worker/configuration
- `UploadSell.jsx`, `EditArt.jsx` 및 관련 프론트 테스트
- `CloudinaryServiceTest`, `UserServiceTest`, `ArtServiceMutationTest`, `ArtServiceMutationTransactionTest`와 cleanup 전용 테스트

### 커밋 경계

1. `chore: Cloudinary 이미지 정리 계획 추가`
   - 승인된 본 계획만 반영한다.
2. `feat(backend): Cloudinary 이미지 식별자 저장 구조 추가`
   - Flyway, 엔티티, DTO, 업로드 결과 계약과 해당 테스트를 포함한다.
3. `feat: 이미지 교체 및 삭제 cleanup 연동`
   - 작품·프로필 서비스, 작품 프론트 payload, 물리 삭제/취소 분기 테스트를 포함한다.
4. `feat(backend): Cloudinary 삭제 재시도 작업 추가`
   - destroy 호출, cleanup 작업자, 멱등성·재시도 테스트를 포함한다.
5. `chore: Cloudinary 이미지 정리 완료 기록`
   - 구현·검증 완료 후 계획 제거와 최소 `PLAN_DONE.md` 기록만 포함한다.
