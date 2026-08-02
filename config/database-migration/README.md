# ONSure database migration boundary

상태: `NOT_PRESENT / DESIGN_ONLY_NONFINAL`

ONSure에는 현재 영속 관계형 DB와 production schema migration component가 없다. `synthetic/`의 SQLite SQL은 runner의 순서·digest drift·idempotency·exclusive lock·rollback만 시험하며 제품 schema나 engine 선택이 아니다.

DB를 도입하려면 engine ADR, schema owner, tenant/retention 모델, migration lock, backup/restore proof, rollback compatibility window와 서명된 migration receipt가 먼저 승인되어야 한다. Flyway, Liquibase 또는 SQL 파일은 아직 선택하거나 추가하지 않는다.

`migration-plan.v1.json`은 engine/tool/schema를 선택하지 않은 preflight 골격이다. migration 목록은 비어 있고 apply 명령은 `NOT_AUTHORIZED`다. 현재 migration command는 `NOT_RUN_NOT_APPLICABLE`이며 destructive DDL과 고객 데이터 fixture는 기본 거부한다.

```bash
python3 scripts/onsure_deploy_migration_skeleton.py preflight
python3 scripts/onsure_synthetic_db_migration.py apply --database /synthetic/path.db --lock /synthetic/path.lock
```
