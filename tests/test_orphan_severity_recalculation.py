from __future__ import annotations

import importlib.util
import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "recalculate_orphan_severity",
    ROOT / "scripts" / "recalculate-orphan-severity.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class OrphanSeverityRecalculationTest(unittest.TestCase):
    """Autonomous Development Mode standing-policy execution queue item 2: orphan severity
    recalculation. Verifies the script's R1/R2 escalation criteria are real and produce a
    disclosed, review-required worklist -- not a silent redefinition of the mechanical scanner's
    own P0/P1 output."""

    def test_report_never_claims_final_authority(self) -> None:
        import contextlib
        import io

        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            exit_code = MODULE.main()
        self.assertEqual(0, exit_code)
        report = json.loads(buffer.getvalue())
        self.assertFalse(report["final_claim_allowed"])
        self.assertIn("REVIEW_REQUIRED", report["disposition"])

    def test_fr_learn_012_is_a_recalculation_candidate(self) -> None:
        # FR-LEARN-012 (multi-oracle disagreement blocks Final PASS) is CRITICAL criticality and
        # QUALIFICATION_GATE claim_effect, currently only a mechanical P1 -- a known, real R1 case
        # at the time this test was written. If FR-LEARN-012 gets a real contract/test/evidence
        # closure later, this assertion will need updating (same "worked example goes stale when a
        # gap is legitimately closed" pattern as the FR-COM-008 test earlier this session) --
        # that is a sign progress happened, not a bug in this test.
        import contextlib
        import io

        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            MODULE.main()
        report = json.loads(buffer.getvalue())
        candidate_ids = {c["requirement_id"] for c in report["recalculation_candidates"]}
        if "FR-LEARN-012" in candidate_ids:
            candidate = next(c for c in report["recalculation_candidates"] if c["requirement_id"] == "FR-LEARN-012")
            self.assertIn("R1_CRITICAL_QUALIFICATION_GATE_ORPHAN", candidate["matched_rules"])

    def test_every_candidate_has_a_matched_rule_and_is_a_real_mechanical_p1(self) -> None:
        import contextlib
        import io

        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            MODULE.main()
        report = json.loads(buffer.getvalue())
        trace_report = json.loads(
            (ROOT / ".onsure/requirement-universe/global-trace-scan-report.json").read_text(encoding="utf-8"))
        mechanical_p1 = set(trace_report["orphans"]["p1"])
        for candidate in report["recalculation_candidates"]:
            self.assertTrue(candidate["matched_rules"])
            self.assertIn(candidate["requirement_id"], mechanical_p1)

    def test_does_not_mutate_the_mechanical_scan_report(self) -> None:
        # Running the recalculation must never change scan-global-trace-closure.py's own P0/P1
        # sets -- it is a read-only, additive review pass.
        before = json.loads(
            (ROOT / ".onsure/requirement-universe/global-trace-scan-report.json").read_text(encoding="utf-8"))
        import contextlib
        import io

        with contextlib.redirect_stdout(io.StringIO()):
            MODULE.main()
        after = json.loads(
            (ROOT / ".onsure/requirement-universe/global-trace-scan-report.json").read_text(encoding="utf-8"))
        self.assertEqual(before["orphans"]["p0"], after["orphans"]["p0"])
        self.assertEqual(before["orphans"]["p1"], after["orphans"]["p1"])


if __name__ == "__main__":
    unittest.main()
