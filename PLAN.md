# DailyAtelier 개발 계획

> [!IMPORTANT]
> `PLAN.md`는 현재 승인된 작업 계획을 관리한다.
>
> 작업 완료 후 핵심 구현·검증 결과를 `PLAN_DONE.md`에 기록한 다음 해당 계획을 `PLAN.md`에서 제거한다.
>
> 현재 범위에 포함되지 않은 미완료·후속 작업은 `BACKLOG.md`에서 관리한다.
>
> 새 계획이나 범위 변경은 사용자 승인 후 반영한다.

## 찜 중복 방지와 MySQL 무결성 검증

### 목표와 유지 계약

- 사용자와 작품 한 쌍은 `likes`에 최대 한 행만 존재하도록 DB와 JPA 매핑에
  `(user_id, art_id)` unique 제약을 추가한다. 기존 V1 baseline은 수정하지 않는다.
- `POST /api/arts/{artId}/like`의 성공 응답(`200`, `LikeStatusDto`, `liked=true`)은
  최초 요청과 이미 찜된 요청 모두 유지한다. 동시 동일 찜 요청도 두 요청 모두
  성공으로 끝나고 최종 행 수는 한 건이어야 한다.
- 이번 범위는 찜 중복 방지로 한정한다. 찜 삭제와 삭제된 작품의 `art_id = NULL`
  tombstone 정책, Likes 오류 DTO 계약, 기존 데이터 정리 정책은 변경하지 않는다.

### 구현 계획

1. 신규 `V10__add_likes_user_art_unique_constraint.sql`을 추가해 `likes`에
   이름 있는 `uq_likes_user_art` unique 제약을 만든다. 엔티티 `Likes`에도 같은
   제약을 선언해 JPA 모델과 Flyway 스키마를 일치시킨다.
   - 로컬 개발 DB는 초기화 가능하므로 migration에는 임의 삭제·병합 DML을 넣지
     않는다. 비어 있지 않은 DB에 적용하기 전에는 중복 `(user_id, art_id)` 행을
     조회하고, 발견되면 보존 기준을 별도 승인받는다.
   - `art_id`가 NULL인 기존 삭제 작품 찜 이력은 MySQL unique의 NULL 의미에 따라
     계속 여러 행을 허용하므로 현행 tombstone 목록 계약을 유지한다.

2. `LikesService.addLike()`의 조회 순서와 존재 확인을 잠금 읽기로 명시한다.
   - 현재 구현은 `getUser()` 일반 조회 → `getArt()` 일반 조회 →
     `existsByUserAndArt()` 일반 조회 순서다. MySQL REPEATABLE READ에서는 첫
     일반 조회가 읽기 뷰를 만들 수 있으므로, 단순히 작품 행 잠금만 추가한 뒤
     기존 일반 `exists`를 호출하면 잠금 대기 전 스냅샷으로 찜이 없다고 판단할
     가능성이 있다.
   - 먼저 기존 `ArtRepository.findByIdForUpdate()`로 작품 행을 잠근 뒤 사용자를
     조회하고, `LikesRepository`에 `PESSIMISTIC_WRITE` 기반
     `findByUserAndArtForUpdate()`를 추가한다. 이 잠금 존재 조회의 현재 값으로
     이미 찜된 행이면 저장하지 않고, 없을 때만 저장한다. 일반 `exists`는
     `addLike()` 경로에서 사용하지 않는다.
   - 이 순서는 같은 작품의 동시 찜을 작품 행에서 직렬화하고, 뒤따르는 존재
     확인도 일관 읽기 뷰가 아닌 최신 커밋 상태로 수행한다. 따라서 DB unique
     충돌을 정상 멱등 응답으로 변환하려고 rollback-only 트랜잭션에서 예외를
     잡는 방식은 사용하지 않는다.
   - DB unique 제약은 서비스 밖의 쓰기와 미래 변경까지 막는 최종 무결성 장치로
     유지한다.
   - 동일 작품을 찜하는 서로 다른 사용자의 요청도 잠금으로 직렬화될 수 있으나,
     중복 행 방지와 현재 멱등 응답을 보장하는 최소 변경으로 둔다.
   - 입찰도 같은 `ArtRepository.findByIdForUpdate()`를 가장 먼저 획득한 뒤
     사용자·포인트 계정 잠금으로 진행한다. 찜은 그 뒤에 사용자·likes만 다루므로
     잠금 순서 역전은 만들지 않는다. 다만 입찰의 계정·원장 처리 동안 찜 요청이
     대기하고, 반대로 짧은 찜 트랜잭션 동안 입찰이 대기할 수 있다. 이번 범위의
     짧은 단일 행 쓰기에서는 허용하되, MySQL 동시성 테스트에 입찰과의 경합을
     추가하지 않고 별도 성능·잠금 진단 범위로 남긴다.

3. 실제 MySQL 회귀 테스트를 기존 환경변수 기반 구조에 맞춰 추가한다.
   - `FlywayEmptyDatabaseMySqlTest`의 최신 migration 기대 버전을 V10까지,
     unique 제약 이름·컬럼 기대값을 `uq_likes_user_art`까지 확장해 빈 MySQL에서
     V1→V10 적용, Hibernate `validate`, 재실행 0건을 검증한다.
   - MySQL schema/constraint 테스트에서 서로 다른 user·art fixture를 만든 뒤
     같은 `(user_id, art_id)` 직접 삽입이 `DataAccessException`으로 거절되고,
     `art_id = NULL` 이력은 유지되는지를 검증한다.
   - MySQL 서비스 동시성 테스트에서 두 스레드가 같은 사용자·작품에
     `addLike()`를 동시에 호출하도록 고정하고, 두 결과가 `liked=true`, DB 행 수가
     1, 최종 상태가 liked임을 검증한다. 테스트는 기존 MySQL 테스트와 같이
     `DAILYATELIER_MYSQL_SCHEMA_TEST=true`일 때만 실제 MySQL을 사용하고 fixture
     정리 범위를 해당 테스트 사용자·작품으로 제한한다.
   - 동시성 테스트는 한 요청이 작품 잠금을 보유한 동안 다른 요청을 시작해
     대기시키고, 첫 요청을 커밋한 뒤 두 번째 요청이 최신 existing like를 읽어
     성공하는 순서를 관찰한다. 단순 최종 행 수뿐 아니라 스냅샷 재사용으로 인한
     unique 충돌이 없는지 검증한다.

### 브라우저 QA와 커밋 경계

- 프론트 코드와 HTTP 성공 응답 계약을 바꾸지 않는 백엔드 무결성 보완이므로,
  이번 완료 기준에 수동 사용자 브라우저 QA는 포함하지 않는다. 기존 Playwright
  UI QA는 변경된 화면의 검증 수단이 아니며, 실행 여부는 전체 회귀/CI 범위에서
  별도로 결정한다. MySQL 스키마·동시성 테스트가 이 변경의 필수 검증이다.
- 현재 계획 작성 단계에서는 커밋하지 않는다. 승인 후에도 계획 문서 커밋,
  기능 변경(migration·엔티티·repository·service·테스트) 커밋, 완료 시
  `PLAN.md` 정리와 `PLAN_DONE.md` 기록 커밋을 서로 분리한다. 기능 커밋에는
  계획·완료 문서를 섞지 않는다.

### 완료 기준과 검증 순서

1. 새 migration이 비어 있는 MySQL에서 V1부터 순서대로 적용되고, Hibernate
   schema validation과 Flyway 재실행 0건이 확인된다.
2. MySQL이 unique 제약을 실제로 강제하며, 삭제 작품 찜 이력의 NULL 정책은
   변하지 않는다.
3. 동시 동일 찜 요청에서, 잠금 대기 후에도 최신 existing like를 읽어 예외·중복
   행 없이 기존 성공 응답 계약을 유지한다.
4. 변경 완료 후 이 계획은 `PLAN.md`에서 제거하고, 유지할 제약·동시성 계약과
   MySQL 검증 조건만 `PLAN_DONE.md`에 간략히 기록한다. 기능 변경 커밋과 이
   완료 문서 커밋은 분리한다.
