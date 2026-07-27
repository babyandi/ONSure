from __future__ import annotations

import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class OmissionDetectionGateTest(unittest.TestCase):
    def run_command(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, *arguments],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_current_design_and_process_models_pass_all_failure_injection_self_tests(self) -> None:
        result = self.run_command(
            "scripts/validate-design-coverage.py",
            "--matrix", "status/design-capability-coverage.v2.json",
            "--root", ".",
            "--self-test",
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        report = json.loads(result.stdout)
        self.assertEqual("PASS", report["decision"])
        self.assertEqual(28, report["failure_injection_count"])
        self.assertEqual([], report["coverage_errors"])
        self.assertEqual([], report["self_test_errors"])
        self.assertEqual([], report["process_lineage_errors"])

    def test_missing_capability_is_detected(self) -> None:
        matrix = json.loads((ROOT / "status/design-capability-coverage.v2.json").read_text(encoding="utf-8"))
        matrix["capabilities"] = [
            item for item in matrix["capabilities"] if item["capability_id"] != "PROGRAM-LEARNING"
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "matrix.json"
            path.write_text(json.dumps(matrix), encoding="utf-8")
            result = self.run_command(
                "scripts/validate-design-coverage.py", "--matrix", str(path),
                "--skip-path-checks",
            )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("REQUIRED_CAPABILITY_MISSING:PROGRAM-LEARNING", result.stdout)

    def test_missing_process_stage_is_detected(self) -> None:
        model = json.loads((ROOT / "contracts/product-process-lineage.v1.json").read_text(encoding="utf-8"))
        model["stages"] = [stage for stage in model["stages"] if stage["stage_id"] != "IMPROVEMENT_PROOF"]
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "lineage.json"
            path.write_text(json.dumps(model), encoding="utf-8")
            result = self.run_command(
                "scripts/validate-product-process-lineage.py", "--model", str(path)
            )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("REQUIRED_STAGE_MISSING:IMPROVEMENT_PROOF", result.stdout)

    def test_missing_data_parent_binding_is_detected(self) -> None:
        model = json.loads((ROOT / "contracts/product-process-lineage.v1.json").read_text(encoding="utf-8"))
        artifact = next(item for item in model["artifacts"] if item["artifact_id"] == "PATCH_APPLY_RECEIPT")
        artifact["parent_bindings"] = []
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "lineage.json"
            path.write_text(json.dumps(model), encoding="utf-8")
            result = self.run_command(
                "scripts/validate-product-process-lineage.py", "--model", str(path)
            )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("ARTIFACT_PARENT_BINDING_MISSING:PATCH_APPLY_RECEIPT", result.stdout)

    def test_pass_without_evidence_is_detected(self) -> None:
        matrix = json.loads((ROOT / "status/design-capability-coverage.v2.json").read_text(encoding="utf-8"))
        matrix["capabilities"][0]["verification_state"] = "PASS"
        matrix["capabilities"][0]["evidence_refs"] = []
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "matrix.json"
            path.write_text(json.dumps(matrix), encoding="utf-8")
            result = self.run_command(
                "scripts/validate-design-coverage.py", "--matrix", str(path),
                "--skip-path-checks",
            )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("PASS_WITHOUT_EVIDENCE:CORE-ISOLATION", result.stdout)


if __name__ == "__main__":
    unittest.main()
