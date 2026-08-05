#!/usr/bin/env python3
"""Run a disposable, real PostgreSQL/Flyway migration and backup/restore rehearsal."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import pathlib
import platform
import shutil
import socket
import subprocess
import sys
import tarfile
import tempfile
from typing import Iterable

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
DEFAULT_PACKAGE = ROOT / "target/onsure-rhel-candidate.tar.gz"
DEFAULT_OUTPUT = ROOT / "assurance/runtime/onsure-postgresql-flyway-rehearsal.v1.json"
MIGRATIONS = tuple(sorted((ROOT /
    "modules/onsure-migration-postgresql/src/main/resources/db/migration/postgresql").glob("V*__*.sql")))
EVIDENCE_PREFIX = "assurance/runtime/"


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as body:
        for chunk in iter(lambda: body.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def source_commit() -> str:
    value = run("SOURCE_COMMIT", ["git", "rev-parse", "HEAD"])
    if len(value) != 40 or any(character not in "0123456789abcdef" for character in value):
        raise ValueError("SOURCE_COMMIT_INVALID")
    return value


def source_code_dirty_paths() -> list[str]:
    tracked = run("SOURCE_DIRTY_TRACKED", ["git", "diff", "--name-only", "HEAD", "--", "."])
    untracked = run("SOURCE_DIRTY_UNTRACKED", ["git", "ls-files", "--others", "--exclude-standard"])
    values = {line.strip() for line in (tracked + "\n" + untracked).splitlines() if line.strip()}
    return sorted(value for value in values if not value.startswith(EVIDENCE_PREFIX))


def environment_facts(binaries: pathlib.Path) -> dict[str, object]:
    tools: dict[str, object] = {}
    for name in ("postgres", "initdb", "pg_ctl", "createdb", "psql", "pg_dump", "pg_restore"):
        path = (binaries / name).resolve()
        tools[name] = {"path": str(path), "sha256": sha256_file(path)}
    java = shutil.which("java")
    if not java:
        raise ValueError("JAVA_EXECUTABLE_NOT_AVAILABLE")
    java_path = pathlib.Path(java).resolve()
    tools["java"] = {"path": str(java_path), "sha256": sha256_file(java_path)}
    return {
        "os": platform.system(),
        "os_release": platform.release(),
        "machine": platform.machine(),
        "python_version": platform.python_version(),
        "uid": os.getuid(),
        "gid": os.getgid(),
        "tools": tools,
    }


def receipt_digest(value: dict[str, object]) -> str:
    normalized = dict(value)
    normalized.pop("receipt_sha256", None)
    return hashlib.sha256(json.dumps(
        normalized, sort_keys=True, separators=(",", ":"), ensure_ascii=False,
    ).encode("utf-8")).hexdigest()


def postgres_bin() -> pathlib.Path:
    def version(path: pathlib.Path) -> tuple[int, ...]:
        try:
            return tuple(int(value) for value in path.parent.name.split("."))
        except ValueError:
            return (0,)

    candidates = sorted(
        (path for path in pathlib.Path("/usr/lib/postgresql").glob("*/bin")
         if (path / "postgres").is_file()),
        key=version,
        reverse=True,
    )
    if not candidates:
        value = shutil.which("postgres")
        if value:
            return pathlib.Path(value).parent
        raise ValueError("POSTGRESQL_SERVER_BINARY_NOT_AVAILABLE")
    return candidates[0]


def run(label: str, arguments: list[str], environment: dict[str, str] | None = None) -> str:
    result = subprocess.run(
        arguments,
        cwd=ROOT,
        env=environment,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode:
        detail = (result.stderr or result.stdout).strip().splitlines()
        suffix = detail[-1] if detail else "NO_OUTPUT"
        raise ValueError(f"{label}_FAILED:{suffix[:500]}")
    return result.stdout.strip()


def extract_package(package: pathlib.Path, destination: pathlib.Path) -> None:
    with tarfile.open(package, "r:gz") as archive:
        for member in archive.getmembers():
            relative = pathlib.PurePosixPath(member.name.removeprefix("./"))
            if relative.is_absolute() or ".." in relative.parts:
                raise ValueError("PACKAGE_ARCHIVE_PATH_ESCAPE")
            if not (member.isfile() or member.isdir()):
                raise ValueError("PACKAGE_ARCHIVE_NONREGULAR_ENTRY")
        archive.extractall(destination, filter="data")


def free_loopback_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind(("127.0.0.1", 0))
        return int(listener.getsockname()[1])


def java_command(runtime: pathlib.Path, action: str) -> list[str]:
    classpath = f"{runtime}/opt/onsure/migration/*:{runtime}/opt/onsure/lib/*"
    return [
        "java", "-cp", classpath,
        "io.onsure.migration.postgresql.PostgresqlMigrationMain", action,
    ]


def psql(
    binaries: pathlib.Path,
    socket_directory: pathlib.Path,
    port: int,
    database: str,
    sql: str,
    username: str = "postgres",
) -> str:
    return run("PSQL", [
        str(binaries / "psql"), "-X", "-v", "ON_ERROR_STOP=1", "-At",
        "-h", str(socket_directory), "-p", str(port), "-U", username,
        "-d", database, "-c", sql,
    ])


def concurrent_migrate(
    runtime: pathlib.Path, environment: dict[str, str], expected_migrations: int,
) -> list[int]:
    processes = [
        subprocess.Popen(
            java_command(runtime, "migrate"),
            cwd=ROOT,
            env=environment,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        for _ in range(2)
    ]
    try:
        executed: list[int] = []
        for process in processes:
            output, error = process.communicate(timeout=60)
            if process.returncode:
                detail = (error or output).strip().splitlines()
                raise ValueError("CONCURRENT_MIGRATE_FAILED:" + (detail[-1] if detail else "NO_OUTPUT"))
            if f"executed={expected_migrations}" in output:
                executed.append(expected_migrations)
            elif "executed=0" in output:
                executed.append(0)
            else:
                raise ValueError("CONCURRENT_MIGRATE_RESULT_INVALID")
        return sorted(executed)
    except subprocess.TimeoutExpired as timeout:
        raise ValueError("CONCURRENT_MIGRATE_TIMEOUT") from timeout
    finally:
        for process in processes:
            if process.poll() is None:
                process.kill()
                process.communicate()


def rehearse(package: pathlib.Path) -> dict[str, object]:
    started_at = dt.datetime.now(dt.timezone.utc)
    package = package.resolve()
    if not package.is_file() or ROOT not in package.parents:
        raise ValueError("RHEL_PACKAGE_REQUIRED_INSIDE_PRODUCT_ROOT")
    binaries = postgres_bin()
    required = ("initdb", "pg_ctl", "createdb", "psql", "pg_dump", "pg_restore")
    if any(not (binaries / name).is_file() for name in required):
        raise ValueError("POSTGRESQL_TOOLCHAIN_INCOMPLETE")
    dirty_paths = source_code_dirty_paths()
    if dirty_paths:
        raise ValueError("SOURCE_CODE_WORKTREE_DIRTY:" + ",".join(dirty_paths[:20]))
    commit = source_commit()
    evidence_environment = environment_facts(binaries)
    environment_sha256 = hashlib.sha256(json.dumps(
        evidence_environment, sort_keys=True, separators=(",", ":"), ensure_ascii=False,
    ).encode("utf-8")).hexdigest()

    with tempfile.TemporaryDirectory(prefix="onsure-postgresql-rehearsal-") as temporary:
        base = pathlib.Path(temporary)
        data = base / "data"
        sockets = base / "socket"
        runtime = base / "runtime"
        backup = base / "onsure.dump"
        sockets.mkdir(mode=0o700)
        runtime.mkdir(mode=0o700)
        extract_package(package, runtime)
        port = free_loopback_port()
        run("INITDB", [
            str(binaries / "initdb"), "-D", str(data), "--username=postgres",
            "--auth=trust", "--no-locale", "--encoding=UTF8",
        ])
        started = False
        try:
            run("POSTGRES_START", [
                str(binaries / "pg_ctl"), "-D", str(data), "-l", str(base / "postgres.log"),
                "-o", f"-F -p {port} -k {sockets} -c listen_addresses=127.0.0.1",
                "-w", "start",
            ])
            started = True
            psql(
                binaries, sockets, port, "postgres",
                "CREATE ROLE onsure LOGIN PASSWORD 'synthetic-rehearsal-password';",
            )
            run("CREATEDB", [
                str(binaries / "createdb"), "-h", str(sockets), "-p", str(port),
                "-U", "postgres", "-O", "onsure", "onsure",
            ])
            runtime_environment = dict(os.environ)
            runtime_environment.update({
                "ONSURE_DB_URL": f"jdbc:postgresql://127.0.0.1:{port}/onsure?sslmode=disable",
                "ONSURE_DB_USER": "onsure",
                "ONSURE_DB_PASSWORD": "synthetic-rehearsal-password",
                "ONSURE_DB_SCHEMA": "onsure",
                "ONSURE_MIGRATION_AUTHORIZED": "true",
            })
            first = run("MIGRATE_FIRST", java_command(runtime, "migrate"), runtime_environment)
            second = run("MIGRATE_SECOND", java_command(runtime, "migrate"), runtime_environment)
            validation = run("MIGRATE_VALIDATE", java_command(runtime, "validate"), runtime_environment)
            info = run("MIGRATE_INFO", java_command(runtime, "info"), runtime_environment)
            expected_migrations = len(MIGRATIONS)
            if f"executed={expected_migrations}" not in first or "executed=0" not in second \
                    or "PASS_NONFINAL" not in validation or "pending=0" not in info:
                raise ValueError("MIGRATION_RESULT_CONTRACT_INVALID")
            psql(
                binaries, sockets, port, "onsure",
                "INSERT INTO onsure.assurance_event "
                "(event_id,event_type,observed_at,evidence_sha256) VALUES "
                "('synthetic-1','REHEARSAL',CURRENT_TIMESTAMP,repeat('a',64));",
                username="onsure",
            )
            psql(
                binaries, sockets, port, "onsure",
                "INSERT INTO onsure.validation_run_score "
                "(run_id,project_id,target_id,source_sha256,receipt_sha256,validation_outcome,"
                "earned_points,max_points,scorecard_json,observed_at) VALUES "
                "('synthetic-run-1','synthetic-project','synthetic-target',repeat('b',64),"
                "repeat('c',64),'NOT_RUN',0,100,'{\"contract\":\"ONSURE_VALIDATION_SCORECARD_V1\"}'::jsonb,"
                "CURRENT_TIMESTAMP);"
                "INSERT INTO onsure.validation_score_node "
                "(run_id,node_type,node_id,outcome,possible_points,earned_points,diagnosis,improvement_guide) VALUES "
                "('synthetic-run-1','DOMAIN','DESIGN','NOT_RUN',100,0,'synthetic diagnosis','synthetic guide');",
                username="onsure",
            )
            psql(
                binaries, sockets, port, "onsure",
                "INSERT INTO onsure.validation_run_finding "
                "(run_id,finding_id,severity,status,diagnosis,improvement_guide,finding_json) VALUES "
                "('synthetic-run-1','synthetic-finding','MEDIUM','OPEN','synthetic diagnosis',"
                "'synthetic guide','{\"finding_id\":\"synthetic-finding\"}'::jsonb);",
                username="onsure",
            )
            psql(
                binaries, sockets, port, "onsure",
                "BEGIN;"
                "INSERT INTO onsure.validation_run_score "
                "(run_id,project_id,target_id,source_sha256,receipt_sha256,validation_outcome,"
                "earned_points,max_points,scorecard_json,observed_at) VALUES "
                "('rollback-only','synthetic-project','synthetic-target',repeat('d',64),"
                "repeat('e',64),'NOT_RUN',0,100,'{}'::jsonb,CURRENT_TIMESTAMP);"
                "ROLLBACK;",
                username="onsure",
            )
            rollback_count = psql(
                binaries, sockets, port, "onsure",
                "SELECT count(*) FROM onsure.validation_run_score WHERE run_id='rollback-only';",
                username="onsure",
            )
            if rollback_count != "0":
                raise ValueError("TRANSACTION_ROLLBACK_REHEARSAL_INVALID")
            run("PG_DUMP", [
                str(binaries / "pg_dump"), "-Fc", "-h", str(sockets), "-p", str(port),
                "-U", "postgres", "-d", "onsure", "-f", str(backup),
            ])
            backup_sha256 = sha256_file(backup)
            backup_size = backup.stat().st_size
            run("CREATE_RESTORE_DB", [
                str(binaries / "createdb"), "-h", str(sockets), "-p", str(port),
                "-U", "postgres", "-O", "onsure", "onsure_restore",
            ])
            run("PG_RESTORE", [
                str(binaries / "pg_restore"), "--exit-on-error", "--no-owner",
                "--role=onsure", "-h", str(sockets), "-p", str(port),
                "-U", "postgres", "-d", "onsure_restore", str(backup),
            ])
            restored_events = psql(
                binaries, sockets, port, "onsure_restore",
                "SELECT count(*) FROM onsure.assurance_event;",
            )
            restored_history = psql(
                binaries, sockets, port, "onsure_restore",
                "SELECT count(*) FROM onsure.flyway_schema_history WHERE success AND version IS NOT NULL;",
            )
            restored_scores = psql(
                binaries, sockets, port, "onsure_restore",
                "SELECT count(*) FROM onsure.validation_run_score;",
            )
            restored_nodes = psql(
                binaries, sockets, port, "onsure_restore",
                "SELECT count(*) FROM onsure.validation_score_node;",
            )
            restored_findings = psql(
                binaries, sockets, port, "onsure_restore",
                "SELECT count(*) FROM onsure.validation_run_finding;",
            )
            restored_environment = dict(runtime_environment)
            restored_environment["ONSURE_DB_URL"] = (
                f"jdbc:postgresql://127.0.0.1:{port}/onsure_restore?sslmode=disable"
            )
            restored_validation = run(
                "RESTORED_VALIDATE", java_command(runtime, "validate"), restored_environment
            )
            restored_migrate = run(
                "RESTORED_MIGRATE", java_command(runtime, "migrate"), restored_environment
            )
            if restored_events != "1" or restored_history != str(expected_migrations) \
                    or restored_scores != "1" or restored_nodes != "1" or restored_findings != "1" \
                    or "PASS_NONFINAL" not in restored_validation \
                    or "executed=0" not in restored_migrate:
                raise ValueError(
                    "BACKUP_RESTORE_RESULT_INVALID:"
                    f"events={restored_events}:history={restored_history}:"
                    f"scores={restored_scores}:nodes={restored_nodes}:findings={restored_findings}:"
                    f"validate={restored_validation}:migrate={restored_migrate}"
                )
            run("CREATE_CONCURRENT_DB", [
                str(binaries / "createdb"), "-h", str(sockets), "-p", str(port),
                "-U", "postgres", "-O", "onsure", "onsure_concurrent",
            ])
            concurrent_environment = dict(runtime_environment)
            concurrent_environment["ONSURE_DB_URL"] = (
                f"jdbc:postgresql://127.0.0.1:{port}/onsure_concurrent?sslmode=disable"
            )
            concurrent_executed = concurrent_migrate(
                runtime, concurrent_environment, expected_migrations,
            )
            concurrent_history = psql(
                binaries, sockets, port, "onsure_concurrent",
                "SELECT count(*) FROM onsure.flyway_schema_history WHERE success AND version IS NOT NULL;",
            )
            if sorted(concurrent_executed) != [0, expected_migrations] \
                    or concurrent_history != str(expected_migrations):
                raise ValueError("CONCURRENT_MIGRATION_IDEMPOTENCY_INVALID")
            version = psql(binaries, sockets, port, "postgres", "SHOW server_version;")
            completed_at = dt.datetime.now(dt.timezone.utc)
            result: dict[str, object] = {
                "contract": "ONSURE_POSTGRESQL_FLYWAY_REHEARSAL_V1",
                "decision": "PASS_NONFINAL",
                "source_commit": commit,
                "source_code_dirty_paths": [],
                "started_at": started_at.isoformat().replace("+00:00", "Z"),
                "completed_at": completed_at.isoformat().replace("+00:00", "Z"),
                "environment": evidence_environment,
                "environment_sha256": environment_sha256,
                "postgresql_version": version,
                "package_sha256": sha256_file(package),
                "package_size_bytes": package.stat().st_size,
                "migrations": [{"path": path.relative_to(ROOT).as_posix(), "sha256": sha256_file(path)}
                               for path in MIGRATIONS],
                "migration_count": len(MIGRATIONS),
                "network_binding": "127.0.0.1_EPHEMERAL",
                "migration_first_executed": expected_migrations,
                "migration_second_executed": 0,
                "pending_after_migration": 0,
                "restored_event_count": 1,
                "restored_history_count": expected_migrations,
                "restored_validation_score_count": 1,
                "restored_validation_score_node_count": 1,
                "restored_validation_finding_count": 1,
                "transaction_rollback_rehearsal": "PASS_NONFINAL",
                "restored_schema_validation": "PASS_NONFINAL",
                "backup_sha256": backup_sha256,
                "backup_size_bytes": backup_size,
                "concurrent_migration_executed_counts": sorted(concurrent_executed),
                "concurrent_migration_history_count": expected_migrations,
                "customer_data_used": False,
                "system_postgresql_service_modified": False,
                "production_migration": "NOT_RUN",
                "rhel_runtime": "NOT_RUN_HOST_IS_NOT_RHEL",
                "final_claim_allowed": False,
            }
            result["receipt_sha256"] = receipt_digest(result)
            return result
        finally:
            if started:
                stopped = subprocess.run(
                    [str(binaries / "pg_ctl"), "-D", str(data), "-m", "fast", "-w", "stop"],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    check=False,
                )
                if stopped.returncode:
                    raise RuntimeError("POSTGRESQL_EPHEMERAL_STOP_FAILED")


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--package", type=pathlib.Path, default=DEFAULT_PACKAGE)
    parser.add_argument("--output", type=pathlib.Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args(argv)
    result = rehearse(args.package)
    output = args.output if args.output.is_absolute() else ROOT / args.output
    output = output.resolve()
    if ROOT not in output.parents or output.is_symlink():
        raise ValueError("EVIDENCE_OUTPUT_MUST_BE_INSIDE_PRODUCT_ROOT")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, tarfile.TarError) as error:
        print("ONSURE_POSTGRESQL_REHEARSAL_FAIL " + str(error), file=sys.stderr)
        raise SystemExit(1)
