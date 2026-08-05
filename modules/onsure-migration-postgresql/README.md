# ONSure PostgreSQL migration candidate

This module owns the forward-only `onsure` schema migrations. Flyway uses its PostgreSQL
transactional advisory lock; migrations must not use non-transactional concurrent DDL.

The runner only accepts loopback PostgreSQL JDBC URLs. `preflight` validates configuration without
connecting. `validate`, `info` and `migrate` connect to PostgreSQL, and `migrate` additionally requires
`ONSURE_MIGRATION_AUTHORIZED=true`. No password is accepted on the command line.

Required environment:

- `ONSURE_DB_URL` (default `jdbc:postgresql://127.0.0.1:5432/onsure`)
- `ONSURE_DB_USER` (default `onsure`)
- `ONSURE_DB_PASSWORD` (required)
- `ONSURE_DB_SCHEMA` (default `onsure`)
- `ONSURE_SCORE_STORE=POSTGRESQL` enables authoritative Local API scorecard/history persistence after V3 is applied
- `ONSURE_MIGRATION_AUTHORIZED` (default `false`)

Production execution, backup/restore proof and signed migration receipt remain outside this module
and are not authorized by the repository.

V2 stores scorecard summaries, every domain/phase/group/area/step diagnosis and digest-only
provenance, plus consecutive-run comparisons. Prompt bodies, source files, command output and
customer data are not columns in these tables.

V3 scopes uniqueness and read-back by `project_id + target_id`, stores the complete score-node and
finding JSON alongside normalized query columns, and binds every run to source, profile,
environment, toolchain, input, output, report, receipt and evidence-manifest digests. Comparisons are
typed as `REPEATABILITY` for an identical source/context or `IMPROVEMENT` only when an approved
patch/proof lineage binds both runs and both source digests; all other source changes are retained as
`NOT_COMPARABLE` rather than presented as improvement.
