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
    """FR-COM-008 (02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md SS2): direct changes to main require
    customer approval; contracts/main-branch-protection.v1.json is the concrete control set and
    status/main-branch-protection-evidence.v1.json the real GITHUB_API observation against it."""

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

    def test_real_observed_evidence_honestly_reports_the_unprotected_branch_as_fail(self) -> None:
        # FR-COM-008 real closure: `gh api repos/babyandi/ONSure/branches/main/protection` returned
        # 404 "Branch not protected" on 2026-08-15 -- the repository's main branch currently has no
        # protection configured at all. The evidence file records that observation honestly (every
        # control false/zero, decision=FAIL) rather than leaving it NOT_RUN indefinitely or, worse,
        # fabricating a PASS. This is a real, actionable gap for the repository owner to close by
        # actually configuring branch protection -- not something this validator can or should fix
        # by itself.
        self.assertEqual("OBSERVED", self.evidence["observation_state"])
        self.assertEqual("FAIL", self.evidence["decision"])
        self.assertEqual([], MODULE.validate(self.policy, self.evidence))
        for control, required in self.policy["required_controls"].items():
            observed = self.evidence["observed_controls"][control]
            if control == "minimum_approvals":
                self.assertLess(observed, required)
            else:
                self.assertFalse(observed)

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
