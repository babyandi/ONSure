from __future__ import annotations

import os
import pathlib
import subprocess
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "deploy/ubuntu/onsure-postgresql-backup"


class ONSureUbuntuBackupTest(unittest.TestCase):
    def run_script(self, **values: str) -> subprocess.CompletedProcess[str]:
        environment = {
            "PATH": os.environ.get("PATH", ""),
            "ONSURE_DB_PASSWORD": "synthetic-not-a-real-password",
            **values,
        }
        return subprocess.run(
            [str(SCRIPT)], text=True, capture_output=True, check=False, env=environment
        )

    def test_non_loopback_database_is_rejected_before_execution(self):
        result = self.run_script(ONSURE_DB_HOST="db.example.invalid")
        self.assertEqual(77, result.returncode)
        self.assertIn("NON_LOOPBACK_DATABASE_DENIED", result.stderr)
        self.assertNotIn("synthetic-not-a-real-password", result.stdout + result.stderr)

    def test_unapproved_backup_root_is_rejected_before_execution(self):
        result = self.run_script(ONSURE_BACKUP_ROOT="/tmp/onsure-backups")
        self.assertEqual(77, result.returncode)
        self.assertIn("BACKUP_ROOT_DENIED", result.stderr)

    def test_unbounded_retention_is_rejected_before_execution(self):
        result = self.run_script(ONSURE_BACKUP_RETENTION_DAYS="365")
        self.assertEqual(64, result.returncode)
        self.assertIn("BACKUP_RETENTION_INVALID", result.stderr)


if __name__ == "__main__":
    unittest.main()
