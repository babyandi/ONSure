# ONSure PostgreSQL migration boundary

상태: `POSTGRESQL_FLYWAY_CANDIDATE_IMPLEMENTED / EXECUTION_NOT_AUTHORIZED`

제품 migration 소유 모듈은 `modules/onsure-migration-postgresql`이고 schema owner는 `onsure`다.
V1은 고객 payload를 저장하지 않는 assurance event 식별자·digest·시각만 정의한다. Flyway의
PostgreSQL transactional lock을 사용하며 non-transactional concurrent DDL은 migration에서 금지한다.

runner의 `preflight`는 DB에 연결하지 않는다. `validate`와 `info`는 합성/승인된 PostgreSQL에
연결하며 `migrate`는 추가로 `ONSURE_MIGRATION_AUTHORIZED=true`가 필요하다. 비밀번호는 인자로
받지 않고 외부 환경파일로만 주입한다. 원격 JDBC host와 URL 내 credentials는 거부한다.

```bash
mvn -B -ntp -q -f pom-modular.xml -pl modules/onsure-migration-postgresql -am package
java -jar modules/onsure-migration-postgresql/target/onsure-migration-postgresql-0.1.0-SNAPSHOT.jar preflight
python3 scripts/onsure_synthetic_db_migration.py apply --database /synthetic/path.db --lock /synthetic/path.lock
```

Ubuntu Production PostgreSQL migrate, lock 경쟁, backup/restore, rollback compatibility와 서명 receipt는 `NOT_RUN`이다.
destructive DDL, 고객 데이터 fixture와 자동 rollback은 기본 거부한다.

Docker 없이 임시 loopback PostgreSQL server를 사용할 수 있는 개발 호스트에서는 다음 명령이 실제
Flyway apply/idempotency/validate와 `pg_dump`/`pg_restore`를 실행한다. system PostgreSQL service와
고객 데이터는 사용하지 않으며 RHEL 운영 증거로 승격하지 않는다.

```bash
python3 scripts/rehearse_onsure_postgresql.py
```
