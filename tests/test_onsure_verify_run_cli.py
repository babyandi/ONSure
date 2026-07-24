import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from onsure_core.cause_aware_verification import (
    build_sample_oruda_report_profile,
    build_sample_run,
)


ROOT = Path(__file__).resolve().parents[1]
CLI = ROOT / "scripts" / "onsure_verify_run.py"


class ONSureVerifyRunCliTest(unittest.TestCase):
    def test_sample_oruda_cli_allows(self):
        completed = subprocess.run(
            [sys.executable, str(CLI), "--sample-oruda"],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        )

        result = json.loads(completed.stdout)
        self.assertEqual("ALLOW", result["decision"])

    def test_json_cli_blocks_and_returns_remediation_target(self):
        profile = build_sample_oruda_report_profile()
        run = build_sample_run(omit_scene_manifest=True)
        with tempfile.TemporaryDirectory() as tmp:
            profile_path = Path(tmp) / "profile.json"
            run_path = Path(tmp) / "run.json"
            profile_path.write_text(json.dumps(profile), encoding="utf-8")
            run_path.write_text(json.dumps(run), encoding="utf-8")

            completed = subprocess.run(
                [
                    sys.executable,
                    str(CLI),
                    "--profile",
                    str(profile_path),
                    "--run",
                    str(run_path),
                ],
                cwd=ROOT,
                check=False,
                text=True,
                capture_output=True,
            )

        result = json.loads(completed.stdout)
        self.assertEqual(1, completed.returncode)
        self.assertEqual("BLOCK", result["decision"])
        self.assertIn("OUI", result["remediation_targets"])


if __name__ == "__main__":
    unittest.main()
