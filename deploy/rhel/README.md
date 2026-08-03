# RHEL-family standalone deployment candidate

Status: `CANDIDATE_IMPLEMENTED / INSTALL_NOT_RUN / PRODUCTION_NOT_AUTHORIZED`.

This is the selected non-container topology: one RHEL-family server, a system PostgreSQL service,
the ONSure loopback API and LLM Gateway under systemd and optional outbound HTTPS to the OpenAI Responses API.
No reverse proxy or public listener is selected. The Local API remains bound to `127.0.0.1`.

The candidate assumes Java 17, Maven 3.8+ for packaging, PostgreSQL supplied by a RHEL-supported
module stream, systemd, `systemd-sysusers` and `systemd-tmpfiles`. An operator must select a supported
PostgreSQL stream for the exact RHEL release, initialize it and constrain PostgreSQL to loopback.

Files map as follows:

- `/opt/onsure/{app,migration,lib}`: immutable packaged JARs, owned by root
- `/etc/onsure/onsure.env`: external secrets/config, `root:onsure`, mode `0640`
- `/var/lib/onsure`: writable application state, owned by `onsure`
- `/var/log/onsure`: optional writable service logs; journald remains preferred
- `/etc/systemd/system/{onsure,onsure-llm-gateway,onsure-migrate}.service`: supplied candidate units

The committed environment example intentionally omits all secret values. The migration service
fails closed until `ONSURE_MIGRATION_AUTHORIZED=true`; changing that flag and starting the units are
human operator actions after backup/restore and schema review. Rollback is application rollback to a
previous immutable package while retaining forward-compatible schema, or an approved database restore.
Flyway Community does not provide a repository-authorized automatic production rollback here.

Suggested review-only checks (they do not install anything):

```bash
systemd-analyze verify deploy/rhel/onsure.service deploy/rhel/onsure-llm-gateway.service deploy/rhel/onsure-migrate.service
python3 scripts/onsure_systemd_security.py
python3 scripts/validate_onsure_rhel_package.py
python3 scripts/validate_onsure_operational_boundary.py
python3 scripts/onsure_deploy_migration_skeleton.py preflight
```

Actual package copy, PostgreSQL initialization, firewall/SELinux changes, service enablement,
migration execution, rollback and Production GO are `NOT_RUN` and not authorized by this repository.

현재 비-RHEL 개발 호스트의 `systemd-analyze security --offline=yes` 결과는 API·Gateway unit 2.8,
migration unit 2.7(`OK`, 낮을수록 제한이 강함)이다. tar validator는 root ownership,
경로 탈출·symlink 부재, 내부 SHA-256 전수 일치, main class와 비밀값 미포함을 확인한다.
두 결과는 unit/source digest에 결속되지만 실제 RHEL enable/start와 SELinux 검증을 대신하지 않는다.
