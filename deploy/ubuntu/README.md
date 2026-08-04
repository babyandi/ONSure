# Ubuntu 24.04 LTS standalone deployment candidate

Status: `CANDIDATE_IMPLEMENTED / INSTALL_NOT_RUN / PRODUCTION_NOT_AUTHORIZED`.

This non-container candidate runs ONSure and PostgreSQL on one Ubuntu 24.04 LTS server. ONSure is
managed by systemd, the Local API binds only to `127.0.0.1:47311`, the LLM Gateway binds only to
`127.0.0.1:47312`, and PostgreSQL must listen only
on loopback. Optional OpenAI Responses provider traffic is outbound HTTPS; no inbound public port is
required or authorized.

The package layout is `/opt/onsure/{app,migration,lib}` for root-owned immutable JARs,
`/etc/onsure/onsure.env` for `root:onsure` mode `0640` external configuration, and
`/var/lib/onsure` plus `/var/log/onsure` for the service account's writable data. The shared,
distribution-neutral systemd definitions remain under `deploy/rhel/` to preserve existing repository
paths; the Ubuntu package installs them under `/usr/lib/systemd/system`, `sysusers.d` and `tmpfiles.d`.

Candidate prerequisites are `openjdk-17-jre-headless`, PostgreSQL 16, systemd, `systemd-sysusers`
and `systemd-tmpfiles`. Maven 3.8+ is needed only when building the package. The installed Ubuntu
24.04.4 development host, Java 17, Maven 3.8.7 and PostgreSQL 16.14 are development observations,
not a production compatibility certification.

Ubuntu operator review must cover:

- AppArmor policy/denials for Java and the selected PostgreSQL package; no profile is loaded here.
- UFW or equivalent host policy with no inbound ONSure/PostgreSQL exposure.
- PostgreSQL cluster initialization, loopback binding, authentication and backup/restore ownership.
- External injection of API, database and Local API secrets; examples contain no active secret slot.
- Immutable package digest approval, migration authorization and rollback rehearsal.

The candidate includes a fail-closed daily PostgreSQL backup timer. It writes custom-format,
mode-0600 backups under `/var/lib/onsure/backups`, validates every new archive with
`pg_restore --list`, records a SHA-256 sidecar, serializes execution with `flock`, permits only a
loopback database host and applies bounded retention. The timer is packaged but is not enabled by
the repository.

Review-only commands (no installation):

```bash
bash scripts/package_onsure_ubuntu.sh
python3 scripts/validate_onsure_ubuntu_package.py
systemd-analyze verify deploy/rhel/onsure.service deploy/rhel/onsure-llm-gateway.service deploy/rhel/onsure-migrate.service
python3 scripts/onsure_ubuntu_systemd_security.py
python3 scripts/validate_onsure_operational_boundary.py
python3 scripts/onsure_ubuntu_lifecycle.py rehearse
```

The lifecycle rehearsal verifies archive paths and internal checksums, performs immutable install,
idempotent reinstall, upgrade and rollback inside `.onsure/`, and confirms that no host path was
modified.

Package installation, `apt` changes, AppArmor/UFW changes, PostgreSQL service modification,
systemd enable/start, migration, rollback, deployment and Production GO are `NOT_RUN` and not
authorized by this repository.
