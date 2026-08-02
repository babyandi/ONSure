# ONSure database migration boundary

상태: `NOT_PRESENT / DESIGN_ONLY_NONFINAL`

ONSure에는 현재 영속 관계형 DB와 schema migration component가 없다. 이 디렉터리는 빈 migration을 구현으로 가장하지 않고 미래 결정 조건만 기록한다.

DB를 도입하려면 engine ADR, schema owner, tenant/retention 모델, migration lock, backup/restore proof, rollback compatibility window와 서명된 migration receipt가 먼저 승인되어야 한다. Flyway, Liquibase 또는 SQL 파일은 아직 선택하거나 추가하지 않는다.

현재 migration command는 `NOT_RUN_NOT_APPLICABLE`이며 destructive DDL과 고객 데이터 fixture는 기본 거부한다.
