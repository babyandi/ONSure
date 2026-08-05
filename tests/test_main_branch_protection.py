from __future__ import annotations

import importlib.util
import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "validate_main_branch_protection",
    ROOT / "scripts" / "validate-main-branch-protection.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class MainBranchProtectionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = json.loads(
            (ROOT / "contracts/main-branch-protection.v1.json").read_text(encoding="utf-8")
        )
        self.evidence = json.loads(
            (ROOT / "status/main-branch-protection-evidence.v1.json").read_text(encoding="utf-8")
        )

    def test_canonical_not_run_state_is_valid_and_nonfinal(self) -> None:
        self.assertEqual([], MODULE.validate(self.policy, self.evidence))

    def test_failure_injections_are_all_detected(self) -> None:
        self.assertEqual([], MODULE.self_test(self.policy, self.evidence))

    def test_observed_drift_cannot_be_reported_as_pass(self) -> None:
        self.evidence.update(
            observation_state="OBSERVED",
            observed_at="2026-07-29T00:00:00Z",
            observed_source="GITHUB_API",
            observed_controls=dict(self.policy["required_controls"]),
            source_commit="a" * 64,
            evidence_sha256="b" * 64,
            decision="PASS_NONFINAL",
        )
        self.evidence["observed_controls"]["force_push_blocked"] = False
        errors = MODULE.validate(self.policy, self.evidence)
        self.assertIn("MAIN_PROTECTION_DECISION_MISMATCH:FAIL", errors)


if __name__ == "__main__":
    unittest.main()
