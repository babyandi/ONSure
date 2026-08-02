import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import onsure_pr_overlap as overlap  # noqa: E402


class ONSurePrOverlapTest(unittest.TestCase):
    def test_exact_overlap_and_gate_revalidation_hold_merge_order(self):
        prs = [{
            "number": 27,
            "title": "fixture",
            "url": "https://example.invalid/27",
            "state": "OPEN",
            "isDraft": True,
            "baseRefName": "main",
            "headRefName": "feature",
            "headRefOid": "a" * 40,
            "files": [{"path": "tests/shared.py"}, {"path": "pom.xml"}],
        }]
        matrix = overlap.build_matrix({"tests/shared.py"}, prs)
        self.assertEqual("HOLD_MERGE_ORDER_REQUIRED", matrix["merge_decision"])
        self.assertEqual(
            ["tests/shared.py"], matrix["pull_requests"][0]["exact_path_overlap"]
        )
        self.assertEqual(
            ["pom.xml"],
            matrix["pull_requests"][0]["migration_gate_revalidation_paths"],
        )
        self.assertFalse(matrix["automatic_merge_allowed"])


if __name__ == "__main__":
    unittest.main()
