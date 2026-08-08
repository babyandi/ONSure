from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "normalize_surefire_summary",
    ROOT / "scripts" / "normalize-surefire-summary.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


class SurefireSummaryNormalizationTest(unittest.TestCase):
    def test_only_elapsed_time_is_normalized(self) -> None:
        first = (
            "Tests run: 42, Failures: 0, Errors: 0, Skipped: 0, "
            "Time elapsed: 8.991 s -- in kr.co.oruda.onsure.AllTests\n"
        )
        second = first.replace("8.991", "9.532")
        self.assertEqual(MODULE.normalize(first), MODULE.normalize(second))
        self.assertIn("Tests run: 42", MODULE.normalize(first))
        self.assertIn("Failures: 0", MODULE.normalize(first))
        self.assertIn("kr.co.oruda.onsure.AllTests", MODULE.normalize(first))

    def test_failure_count_is_not_normalized(self) -> None:
        passing = "Tests run: 42, Failures: 0, Time elapsed: 1.0 s\n"
        failing = "Tests run: 42, Failures: 1, Time elapsed: 1.0 s\n"
        self.assertNotEqual(MODULE.normalize(passing), MODULE.normalize(failing))

    def test_both_repeated_harnesses_use_the_normalizer(self) -> None:
        for relative_path in (
            "scripts/run-product-platform-e2e.sh",
            "scripts/run-local-assurance.sh",
        ):
            script = (ROOT / relative_path).read_text(encoding="utf-8")
            self.assertIn(
                'python "$ROOT/scripts/normalize-surefire-summary.py"',
                script,
                relative_path,
            )


if __name__ == "__main__":
    unittest.main()
