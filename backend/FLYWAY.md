# Flyway 적용 가이드

## 기준 스키마

- 현재 프로젝트에는 보존 대상 운영 DB와 공유 개발 DB가 없다.
- 완전히 빈 MySQL 스키마에는 Flyway가
  `V1__create_baseline_schema.sql`을 적용해 현재 최종 스키마를 생성한다.
- 신규 V1에는 레거시 데이터 이관 DML이나 기존 스키마 호환을 위한 조건부
  SQL을 포함하지 않는다.
- Hibernate 설정은 `spring.jpa.hibernate.ddl-auto=validate`로 유지한다.
- `baseline-on-migrate`와 `baseline-version`은 사용하지 않는다.

## 기존 개발 DB

- 기존 V0~V6 이력이 있는 개발 DB는 신규 V1의 업그레이드 대상이 아니라
  재생성 대상이다.
- 애플리케이션이나 테스트가 개발 DB를 자동으로 삭제하거나 초기화하지 않는다.
- 개발 DB 삭제와 빈 스키마 재생성은 대상과 백업 필요성을 확인한 뒤 별도
  승인을 받아 수동으로 수행한다.
- 신규 V1 적용 이후에는 적용된 migration을 수정하지 않고 모든 변경을 V2
  이상으로 순차 누적한다.

## 빈 MySQL 통합 테스트

`FlywayEmptyDatabaseMySqlTest`와 `PointLedgerMySqlSchemaTest`는 사용자가 준비한
격리된 빈 MySQL 스키마만 대상으로 실행한다. 테스트 실행 전에 `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`와 각 테스트의 활성화 환경변수를 설정한다.

- `DAILYATELIER_EMPTY_DB_TEST=true`: 신규 V1 적용, 최종 스키마 객체,
  Hibernate validate, 재실행 0건과 업무 데이터 0건을 검증한다.
- `DAILYATELIER_MYSQL_SCHEMA_TEST=true`: 포인트 스키마의 인덱스·외래키·CHECK와
  빈 원장 상태를 별도로 검증한다.

두 테스트는 Flyway `clean`, 스키마 삭제, 테이블 삭제 또는 `repair`를 실행하지
않는다. 지정한 DB가 비어 있지 않으면 테스트를 실패시키고 사용자가 승인한
수동 초기화 절차를 기다린다.
