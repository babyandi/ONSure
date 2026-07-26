from __future__ import annotations

import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class RepositoryContractsTest(unittest.TestCase):
    def test_repository_contracts_are_self_consistent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = pathlib.Path(directory) / "report.json"
            result = subprocess.run(
                [
                    sys.executable,
                    str(ROOT / "scripts" / "validate-repository-contracts.py"),
                    "--output",
                    str(report),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            body = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(body["decision"], "PASS")
            self.assertFalse(body["final_claim_allowed"])
            self.assertEqual(body["implementation_counts"]["PARTIAL"], 8)
            self.assertEqual(body["implementation_counts"]["STUB"], 5)
            self.assertEqual(body["implementation_counts"]["DESIGN_ONLY"], 7)
            self.assertEqual(body["verification_counts"]["NOT_RUN"], 20)
            self.assertEqual(body["limitations"]["runtime_execution"], "NOT_RUN")


if __name__ == "__main__":
    unittest.main()
