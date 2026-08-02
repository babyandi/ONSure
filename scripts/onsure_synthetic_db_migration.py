#!/usr/bin/env python3
"""SQLite-only synthetic migration/rollback/lock rehearsal; never a production DB runner."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import sqlite3
import tempfile
from typing import Any

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
MIGRATIONS = ROOT / "config/database-migration/synthetic"


class MigrationLock:
    def __init__(self, path: pathlib.Path):
        self.path = path.resolve()
        self.descriptor: int | None = None

    def __enter__(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        try:
            self.descriptor = os.open(self.path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        except FileExistsError as error:
            raise ValueError("SYNTHETIC_MIGRATION_LOCK_HELD") from error
        os.write(self.descriptor, str(os.getpid()).encode())
        os.fsync(self.descriptor)
        return self

    def __exit__(self, *_):
        if self.descriptor is not None:
            os.close(self.descriptor)
        self.path.unlink(missing_ok=True)


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def synthetic_path(value: pathlib.Path, suffix: str) -> pathlib.Path:
    original = value.absolute()
    resolved = original.resolve()
    sandbox = (ROOT / ".onsure").resolve()
    if sandbox not in resolved.parents or resolved.suffix != suffix or original.is_symlink():
        raise ValueError("SYNTHETIC_MIGRATION_PATH_OUTSIDE_PRODUCT_STATE")
    return resolved


def migrations() -> list[tuple[str, pathlib.Path, pathlib.Path]]:
    result = []
    for up in sorted(MIGRATIONS.glob("*.up.sql")):
        migration_id = up.name.removesuffix(".up.sql")
        down = up.with_name(migration_id + ".down.sql")
        if not down.is_file():
            raise ValueError("SYNTHETIC_MIGRATION_DOWN_MISSING:" + migration_id)
        result.append((migration_id, up, down))
    if not result:
        raise ValueError("SYNTHETIC_MIGRATIONS_MISSING")
    return result


def apply(database: pathlib.Path, lock: pathlib.Path) -> dict[str, Any]:
    database = synthetic_path(database, ".db")
    lock = synthetic_path(lock, ".lock")
    database.parent.mkdir(parents=True, exist_ok=True)
    applied: list[str] = []
    with MigrationLock(lock), sqlite3.connect(database) as connection:
        connection.execute("CREATE TABLE IF NOT EXISTS onsure_schema_history (migration_id TEXT PRIMARY KEY, up_sha256 TEXT NOT NULL, applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)")
        for migration_id, up, _ in migrations():
            existing = connection.execute(
                "SELECT up_sha256 FROM onsure_schema_history WHERE migration_id = ?", (migration_id,)).fetchone()
            digest = sha256(up)
            if existing:
                if existing[0] != digest:
                    raise ValueError("SYNTHETIC_MIGRATION_DIGEST_DRIFT:" + migration_id)
                continue
            connection.executescript(up.read_text(encoding="utf-8"))
            connection.execute("INSERT INTO onsure_schema_history(migration_id, up_sha256) VALUES (?, ?)",
                               (migration_id, digest))
            applied.append(migration_id)
        connection.commit()
    return evidence("APPLY", database, applied)


def rollback(database: pathlib.Path, lock: pathlib.Path) -> dict[str, Any]:
    database = synthetic_path(database, ".db")
    lock = synthetic_path(lock, ".lock")
    rolled_back: list[str] = []
    with MigrationLock(lock), sqlite3.connect(database) as connection:
        for migration_id, _, down in reversed(migrations()):
            existing = connection.execute(
                "SELECT 1 FROM onsure_schema_history WHERE migration_id = ?", (migration_id,)).fetchone()
            if not existing:
                continue
            connection.executescript(down.read_text(encoding="utf-8"))
            connection.execute("DELETE FROM onsure_schema_history WHERE migration_id = ?", (migration_id,))
            rolled_back.append(migration_id)
        connection.commit()
    return evidence("ROLLBACK", database, rolled_back)


def evidence(action: str, database: pathlib.Path, changed: list[str]) -> dict[str, Any]:
    return {
        "contract": "ONSURE_SYNTHETIC_DB_MIGRATION_REHEARSAL_V1",
        "action": action,
        "database_engine": "SQLITE_SYNTHETIC_ONLY",
        "database_sha256": sha256(database),
        "changed_migration_ids": changed,
        "lock_enforced": True,
        "customer_data_used": False,
        "production_migration_authorized": False,
        "decision": "PASS_NONFINAL",
        "final_claim_allowed": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("apply", "rollback"))
    parser.add_argument("--database", type=pathlib.Path, required=True)
    parser.add_argument("--lock", type=pathlib.Path, required=True)
    args = parser.parse_args()
    result = apply(args.database, args.lock) if args.action == "apply" else rollback(args.database, args.lock)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, sqlite3.Error) as error:
        print("ONSURE_SYNTHETIC_DB_MIGRATION_FAIL " + str(error), file=__import__("sys").stderr)
        raise SystemExit(1)
