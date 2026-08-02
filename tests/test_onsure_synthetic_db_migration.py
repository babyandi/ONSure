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
