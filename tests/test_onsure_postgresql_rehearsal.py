from __future__ import annotations

import io
import pathlib
import shutil
import sys
import tarfile
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import rehearse_onsure_postgresql as rehearsal  # noqa: E402


class ONSurePostgresqlRehearsalTest(unittest.TestCase):
    def test_score_authority_migration_records_context_and_causality(self):
        self.assertEqual(["V1", "V2", "V3"], [path.name.split("__", 1)[0]
                                                for path in rehearsal.MIGRATIONS])
        migration = rehearsal.MIGRATIONS[-1].read_text(encoding="utf-8")
        for required in (
                "UNIQUE (project_id, target_id, receipt_sha256)",
                "CREATE TABLE validation_run_finding",
                "comparison_type",
                "improvement_baseline_run_id",
                "validation_run_score_improvement_baseline_scope_fk",
                "validation_run_comparison_baseline_scope_fk",
                "validation_run_comparison_current_scope_fk",
                "patch_apply_receipt_sha256",
                "improvement_proof_sha256"):
            self.assertIn(required, migration)

    def test_package_extraction_rejects_path_escape(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            archive = root / "escape.tar.gz"
            with tarfile.open(archive, "w:gz") as body:
                entry = tarfile.TarInfo("../../escape")
                content = b"escape"
                entry.size = len(content)
                body.addfile(entry, io.BytesIO(content))
            with self.assertRaisesRegex(ValueError, "PACKAGE_ARCHIVE_PATH_ESCAPE"):
                rehearsal.extract_package(archive, root / "output")

    def test_package_extraction_rejects_symlink(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            archive = root / "symlink.tar.gz"
            with tarfile.open(archive, "w:gz") as body:
                entry = tarfile.TarInfo("unsafe-link")
                entry.type = tarfile.SYMTYPE
                entry.linkname = "/etc/passwd"
                body.addfile(entry)
            with self.assertRaisesRegex(ValueError, "PACKAGE_ARCHIVE_NONREGULAR_ENTRY"):
                rehearsal.extract_package(archive, root / "output")

    def test_server_tool_discovery_is_explicit(self):
        installed = any(path.is_file() for path in pathlib.Path("/usr/lib/postgresql").glob("*/bin/postgres"))
        if not installed and shutil.which("postgres") is None:
            self.skipTest("PostgreSQL server binaries are optional for the unit suite")
        path = rehearsal.postgres_bin()
        self.assertTrue((path / "postgres").is_file())
        self.assertTrue((path / "initdb").is_file())

    def test_environment_facts_bind_every_executed_tool_without_secrets(self):
        installed = any(path.is_file() for path in pathlib.Path("/usr/lib/postgresql").glob("*/bin/postgres"))
        if not installed and shutil.which("postgres") is None:
            self.skipTest("PostgreSQL server binaries are optional for the unit suite")
        facts = rehearsal.environment_facts(rehearsal.postgres_bin())
        self.assertEqual(
            {"postgres", "initdb", "pg_ctl", "createdb", "psql", "pg_dump", "pg_restore", "java"},
            set(facts["tools"]),
        )
        for tool in facts["tools"].values():
            self.assertRegex(tool["sha256"], r"^[0-9a-f]{64}$")
        self.assertNotIn("password", str(facts).lower())

    def test_receipt_digest_excludes_only_its_own_digest(self):
        receipt = {"contract": "TEST", "decision": "PASS_NONFINAL", "value": 1}
        digest = rehearsal.receipt_digest(receipt)
        receipt["receipt_sha256"] = digest
        self.assertEqual(digest, rehearsal.receipt_digest(receipt))
        receipt["value"] = 2
        self.assertNotEqual(digest, rehearsal.receipt_digest(receipt))

    def test_source_commit_is_full_sha(self):
        if not (ROOT / ".git").exists():
            self.skipTest("source commit evidence requires Git metadata")
        self.assertRegex(rehearsal.source_commit(), r"^[0-9a-f]{40}$")

    def test_concurrent_migration_parser_uses_current_migration_count(self):
        source = (ROOT / "scripts/rehearse_onsure_postgresql.py").read_text(encoding="utf-8")
        self.assertIn("f\"executed={expected_migrations}\"", source)
        self.assertNotIn('if "executed=2" in output', source)


if __name__ == "__main__":
    unittest.main()
