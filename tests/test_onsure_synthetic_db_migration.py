from __future__ import annotations

import pathlib
import sqlite3
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import onsure_synthetic_db_migration as migration  # noqa: E402


class SyntheticMigrationTest(unittest.TestCase):
    def test_apply_is_idempotent_and_rollback_removes_synthetic_schema(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            original_root = migration.ROOT
            migration.ROOT = root
            try:
                database, lock = root / ".onsure/synthetic.db", root / ".onsure/migration.lock"
                first = migration.apply(database, lock)
                second = migration.apply(database, lock)
                self.assertEqual(["001_create_assurance_event"], first["changed_migration_ids"])
                self.assertEqual([], second["changed_migration_ids"])
                with sqlite3.connect(database) as connection:
                    connection.execute("INSERT INTO assurance_event VALUES (?, ?, ?)",
                                       ("event-1", "2026-08-02T00:00:00Z", "a" * 64))
                rolled_back = migration.rollback(database, lock)
                self.assertEqual(["001_create_assurance_event"], rolled_back["changed_migration_ids"])
                with sqlite3.connect(database) as connection:
                    table = connection.execute(
                        "SELECT 1 FROM sqlite_master WHERE type='table' AND name='assurance_event'").fetchone()
                self.assertIsNone(table)
            finally:
                migration.ROOT = original_root

    def test_failed_migration_rolls_back_partial_schema_releases_lock_and_can_resume(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            migration_root = root / "migrations"
            migration_root.mkdir()
            up = migration_root / "001_atomic.up.sql"
            down = migration_root / "001_atomic.down.sql"
            up.write_text("CREATE TABLE half_created(id TEXT);\nINVALID SQL;\n", encoding="utf-8")
            down.write_text("DROP TABLE IF EXISTS half_created;\n", encoding="utf-8")
            original_root, original_migrations = migration.ROOT, migration.MIGRATIONS
            migration.ROOT, migration.MIGRATIONS = root, migration_root
            try:
                database, lock = root / ".onsure/synthetic.db", root / ".onsure/migration.lock"
                with self.assertRaises(sqlite3.Error):
                    migration.apply(database, lock)
                self.assertFalse(lock.exists())
                with sqlite3.connect(database) as connection:
                    table = connection.execute(
                        "SELECT 1 FROM sqlite_master WHERE type='table' AND name='half_created'").fetchone()
                    history = connection.execute(
                        "SELECT 1 FROM onsure_schema_history WHERE migration_id='001_atomic'").fetchone()
                self.assertIsNone(table)
                self.assertIsNone(history)

                up.write_text("CREATE TABLE half_created(id TEXT);\n", encoding="utf-8")
                resumed = migration.apply(database, lock)
                self.assertEqual(["001_atomic"], resumed["changed_migration_ids"])
            finally:
                migration.ROOT, migration.MIGRATIONS = original_root, original_migrations

    def test_applied_migration_digest_drift_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            migration_root = root / "migrations"
            migration_root.mkdir()
            up = migration_root / "001_drift.up.sql"
            down = migration_root / "001_drift.down.sql"
            up.write_text("CREATE TABLE drift_guard(id TEXT);\n", encoding="utf-8")
            down.write_text("DROP TABLE IF EXISTS drift_guard;\n", encoding="utf-8")
            original_root, original_migrations = migration.ROOT, migration.MIGRATIONS
            migration.ROOT, migration.MIGRATIONS = root, migration_root
            try:
                database, lock = root / ".onsure/synthetic.db", root / ".onsure/migration.lock"
                migration.apply(database, lock)
                up.write_text("CREATE TABLE drift_guard(id TEXT, changed TEXT);\n", encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "DIGEST_DRIFT"):
                    migration.apply(database, lock)
                self.assertFalse(lock.exists())
            finally:
                migration.ROOT, migration.MIGRATIONS = original_root, original_migrations

    def test_exclusive_lock_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            original_root = migration.ROOT
            migration.ROOT = root
            try:
                lock = root / ".onsure/migration.lock"
                lock.parent.mkdir()
                with migration.MigrationLock(lock):
                    with self.assertRaisesRegex(ValueError, "LOCK_HELD"):
                        with migration.MigrationLock(lock):
                            pass
                with self.assertRaisesRegex(ValueError, "OUTSIDE_PRODUCT_STATE"):
                    migration.apply(root / "outside.db", root / "outside.lock")
            finally:
                migration.ROOT = original_root


if __name__ == "__main__":
    unittest.main()
