# Flyway 적용 가이드

## 신규 빈 DB

- 완전히 빈 MySQL 스키마에는 Flyway가 `V0`부터 최신 버전까지 순서대로
  적용된다.
- Hibernate 설정은 `spring.jpa.hibernate.ddl-auto=validate`로 유지한다.
- `V0`는 기존 V1이 전제하는 레거시 핵심 테이블만 생성한다. 적용된
  migration은 수정하지 않고 새 버전을 추가해 checksum을 보존한다.

## 기존 DB

- Flyway 이력이 없고 레거시 테이블이 있는 DB에는
  `spring.flyway.baseline-on-migrate=true`와
  `spring.flyway.baseline-version=0`을 사용한다. Flyway가 버전 0을 baseline으로
  기록해 V0는 건너뛰고 V1부터 적용한다.
- 첫 실행 전에 대상이 의도한 레거시 스키마인지 확인한다. 임의 스키마나
  부분 생성된 스키마를 수용하는 용도로 baseline을 사용하지 않는다.
- 이미 Flyway 이력이 있는 DB에는 낮은 버전인 V0가 out-of-order로 적용되지
  않는다. 기존 V1~V6 파일은 수정하지 않아 적용 이력의 checksum을 유지한다.
- 운영 DB 적용과 복제 환경 리허설은 이 단계에서 수행하지 않는다. 기존 데이터
  조사와 안전 이관은 다음 단계의 별도 migration 및 검증 범위다.

## 빈 DB 통합 테스트

`FlywayEmptyDatabaseMySqlTest`는 새로 만든 일회용 빈 스키마만 대상으로 실행한다.
테스트 실행 전에 빈 스키마를 준비하고 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`DAILYATELIER_EMPTY_DB_TEST=true`를 설정한다. 테스트는 전체 migration,
Hibernate validate 기동, 재실행 0건과 핵심 제약을 확인한다.
