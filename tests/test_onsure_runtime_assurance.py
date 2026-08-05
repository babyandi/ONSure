from __future__ import annotations

import pathlib
import sys
import tarfile
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import onsure_runtime_assurance as runtime  # noqa: E402


class ONSureRuntimeAssuranceTest(unittest.TestCase):
    def test_benchmark_records_bounded_nonfinal_observations(self):
        result = runtime.benchmark([sys.executable, "-c", "print('ok')"], 2, 5)
        self.assertEqual("PASS_NONFINAL", result["decision"])
        self.assertEqual(2, result["metrics"]["successful_iterations"])
        self.assertFalse(result["performance_slo_asserted"])
        self.assertFalse(result["final_claim_allowed"])

    def test_fault_probes_contain_exit_and_timeout(self):
        for mode in ("nonzero_exit", "timeout", "synthetic_enospc"):
            result = runtime.fault_probe(mode, 0.1)
            self.assertTrue(result["fault_contained"])
            self.assertEqual("PASS_NONFINAL", result["decision"])

    def test_benchmark_baseline_and_bounded_soak(self):
        command = [sys.executable, "-c", "print('stable')"]
        baseline = runtime.benchmark(command, 1, 5)
        baseline["metrics"]["p95_ms"] = max(1000, baseline["metrics"]["p95_ms"])
        compared = runtime.benchmark_against_baseline(command, 2, 5, baseline, 20)
        self.assertTrue(compared["baseline_within_threshold"])
        soaked = runtime.soak(command, 0.05, 0, 5)
        self.assertGreater(soaked["iteration_count"], 0)
        self.assertEqual("PASS_NONFINAL", soaked["decision"])

    def test_backup_restore_verifies_deterministic_manifest_without_restoring_in_place(self):
        state = ROOT / ".onsure" / "runtime-assurance-test"
        state.mkdir(parents=True, exist_ok=True)
        (state / "checkpoint.json").write_text('{"state":"TEST"}\n', encoding="utf-8")
        try:
            with tempfile.TemporaryDirectory(dir=ROOT / ".onsure") as directory:
                archive = pathlib.Path(directory) / "backup.tar"
                created = runtime.backup(state, archive)
                verified = runtime.verify_restore(archive)
                self.assertEqual(1, created["file_count"])
                self.assertTrue(verified["restore_verified"])
                self.assertEqual("PASS_NONFINAL", verified["decision"])
                self.assertTrue((state / "checkpoint.json").is_file())
                dr_archive = pathlib.Path(directory) / "dr.tar"
                dr = runtime.dr_rehearsal(state, dr_archive)
                self.assertTrue(dr["isolated_restore_verified"])
                self.assertTrue(dr["corrupted_archive_rejected"])
                self.assertTrue(dr["path_traversal_archive_rejected"])
                self.assertTrue(dr["symlink_source_rejected"])
                self.assertFalse(dr["source_mutated"])
        finally:
            (state / "checkpoint.json").unlink(missing_ok=True)
            state.rmdir()

    def test_restore_rejects_path_traversal_archive_without_writing_outside(self):
        state_root = ROOT / ".onsure"
        state_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=state_root) as directory:
            archive = pathlib.Path(directory) / "traversal.tar"
            escaped = pathlib.Path(directory).parent / "escaped-by-archive.txt"
            with tarfile.open(archive, "w") as output:
                payload = b"escape"
                member = tarfile.TarInfo("../escaped-by-archive.txt")
                member.size = len(payload)
                output.addfile(member, __import__("io").BytesIO(payload))
            with self.assertRaisesRegex(ValueError, "RECOVERY_ARCHIVE_PATH_INVALID"):
                runtime.verify_restore(archive)
            self.assertFalse(escaped.exists())

    def test_backup_rejects_symlinked_state_content(self):
        state_root = ROOT / ".onsure"
        state_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=state_root) as directory:
            source = pathlib.Path(directory) / "state"
            source.mkdir()
            (source / "outside-link").symlink_to(ROOT / "README.md")
            with self.assertRaisesRegex(ValueError, "RECOVERY_SOURCE_SYMLINK_FORBIDDEN"):
                runtime.backup(source, pathlib.Path(directory) / "backup.tar")

    def test_health_is_local_and_does_not_probe_network_or_customer_data(self):
        result = runtime.health()
        self.assertEqual("NOT_RUN", result["network_probe"])
        self.assertEqual("NOT_RUN", result["customer_data_probe"])


if __name__ == "__main__":
    unittest.main()
