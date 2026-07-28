from __future__ import annotations

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class IntegratedRunTest(unittest.TestCase):
    def test_entrypoint_keeps_final_authorities_and_release_blocks(self) -> None:
        body = (ROOT / "scripts/onsure-integrated-run.py").read_text(encoding="utf-8")
        self.assertIn("validate-final-authority-consistency.py", body)
        self.assertIn("static-repeat-1", body)
        self.assertIn("static-repeat-2", body)
        self.assertIn("onsure-final-stage.sh", body)
        self.assertIn('"independent_otester_two_clean": "NOT_RUN"', body)
        self.assertIn('"independent_oaudit_two_clean": "NOT_RUN"', body)
        self.assertIn('"final_lock_allowed": False', body)


if __name__ == "__main__":
    unittest.main()
