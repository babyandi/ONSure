from __future__ import annotations

import pathlib
import sys
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
        for mode in ("nonzero_exit", "timeout"):
            result = runtime.fault_probe(mode, 0.1)
            self.assertTrue(result["fault_contained"])
            self.assertEqual("PASS_NONFINAL", result["decision"])

    def test_backup_restore_verifies_deterministic_manifest_without_restoring_in_place(self):
        state = ROOT / ".onsure" / "runtime-assurance-test"
        state.mkdir(parents=True, exist_ok=True)
        (state / "checkpoint.json").write_text('{"state":"TEST"}\n', encoding="utf-8")
        try:
            with tempfile.TemporaryDirectory() as directory:
                archive = pathlib.Path(directory) / "backup.tar"
                created = runtime.backup(state, archive)
                verified = runtime.verify_restore(archive)
                self.assertEqual(1, created["file_count"])
                self.assertTrue(verified["restore_verified"])
                self.assertEqual("PASS_NONFINAL", verified["decision"])
                self.assertTrue((state / "checkpoint.json").is_file())
        finally:
            (state / "checkpoint.json").unlink(missing_ok=True)
            state.rmdir()

    def test_health_is_local_and_does_not_probe_network_or_customer_data(self):
        result = runtime.health()
        self.assertEqual("NOT_RUN", result["network_probe"])
        self.assertEqual("NOT_RUN", result["customer_data_probe"])


if __name__ == "__main__":
    unittest.main()
