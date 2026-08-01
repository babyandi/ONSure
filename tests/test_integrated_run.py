from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class IntegratedRunTest(unittest.TestCase):
    def test_entrypoint_keeps_final_authorities_and_release_blocks(self) -> None:
        body = (ROOT / "scripts/onsure-integrated-run.py").read_text(encoding="utf-8")
        self.assertIn("validate-final-authority-consistency.py", body)
        self.assertIn('f"static-repeat-{iteration}"', body)
        self.assertIn("range(1, args.repeat + 1)", body)
        self.assertIn("onsure-final-stage.sh", body)
        self.assertIn('choices=("core", "oruda", "full")', body)
        self.assertIn('choices=("prepare", "codespace-final", "auto", "all")', body)
        self.assertIn('"--repeat"', body)
        self.assertIn('"--fail-closed"', body)
        self.assertIn("ONSURE_VALIDATION_PYTHON", body)
        self.assertIn("validation_environment()", body)
        self.assertIn("HOLD_INDEPENDENT_APPROVAL_REQUIRED", body)
        self.assertIn('"independent_otester_two_clean": "NOT_RUN"', body)
        self.assertIn('"independent_oaudit_two_clean": "NOT_RUN"', body)
        self.assertIn('"final_lock_allowed": False', body)


if __name__ == "__main__":
    unittest.main()
