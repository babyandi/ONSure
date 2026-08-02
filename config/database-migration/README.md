# ONSure database migration boundary

상태: `NOT_PRESENT / DESIGN_ONLY_NONFINAL`

ONSure에는 현재 영속 관계형 DB와 schema migration component가 없다. 이 디렉터리는 빈 migration을 구현으로 가장하지 않고 미래 결정 조건만 기록한다.

DB를 도입하려면 engine ADR, schema owner, tenant/retention 모델, migration lock, backup/restore proof, rollback compatibility window와 서명된 migration receipt가 먼저 승인되어야 한다. Flyway, Liquibase 또는 SQL 파일은 아직 선택하거나 추가하지 않는다.

`migration-plan.v1.json`은 engine/tool/schema를 선택하지 않은 preflight 골격이다. migration 목록은 비어 있고 apply 명령은 `NOT_AUTHORIZED`다. 현재 migration command는 `NOT_RUN_NOT_APPLICABLE`이며 destructive DDL과 고객 데이터 fixture는 기본 거부한다.

```bash
python3 scripts/onsure_deploy_migration_skeleton.py preflight
```
