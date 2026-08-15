from __future__ import annotations

import importlib.util
import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "scan_reverse_orphan",
    ROOT / "scripts" / "scan-reverse-orphan.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class ReverseOrphanScanTest(unittest.TestCase):
    """Autonomous Development Mode standing-policy execution queue item 7: reverse orphan
    scanning (Evidence -> Test -> Implementation -> Contract -> Design -> Requirement ->
    Authority). Exercises the classification MECHANISM via synthetic text rather than asserting
    on any specific file's live content -- a real finding this scanner surfaces today (e.g. the
    FR-FIN-* family) could legitimately get reconciled into the main Requirement Universe later,
    and a test hard-asserting on that specific finding would go stale the moment that happens,
    the same "worked example breaks when a real gap is closed" pattern already hit twice earlier
    this session (FR-COM-008 in test_requirement_universe.py, then again for the severity
    recalculation worklist)."""

    def test_a_fully_numbered_known_id_is_recognized_as_real(self) -> None:
        # known_requirement_ids() also contains non-ID generated records shaped like
        # "REQ::path::heading::hash" (source-anchored, not explicit-ID) -- filter to an
        # FR-/NFR-shaped explicit id before sampling, since those are what this scanner's regex
        # is meant to recognize.
        known = MODULE.known_requirement_ids()
        explicit_ids = {rid for rid in known if MODULE.NUMBERED_ID_PATTERN.fullmatch(rid)}
        self.assertTrue(explicit_ids, "expected at least one FR-*/NFR-* numbered id in the universe")
        sample_id = next(iter(explicit_ids))
        found = MODULE.cited_ids(f"this text references {sample_id} directly")
        self.assertIn(sample_id, found)
        self.assertTrue(found & known)

    def test_a_bare_category_prefix_is_not_mistaken_for_a_specific_stale_id(self) -> None:
        # The false positive this scanner's own first draft produced and then fixed: a bare
        # "FR-LEARN" mention (no numeric suffix) in prose is category shorthand, not a citation of
        # a specific nonexistent requirement -- every real FR-* id in the catalog has a numeric
        # suffix, so a bare FR-* token must never be extracted as if it were one.
        found = MODULE.cited_ids("FR-LEARN: one hash-chained entry in the ledger")
        self.assertNotIn("FR-LEARN", found)

    def test_a_bare_nfr_id_that_really_exists_is_recognized(self) -> None:
        known = MODULE.known_requirement_ids()
        bare_nfr_ids = {rid for rid in known if rid.startswith("NFR-") and "-" not in rid[4:]}
        if not bare_nfr_ids:
            self.skipTest("no bare (non-numbered) NFR ids in the current requirement universe")
        sample = next(iter(bare_nfr_ids))
        found = MODULE.cited_ids(f"see {sample} for the verification method")
        self.assertIn(sample, found)

    def test_a_numbered_id_not_in_the_current_universe_is_flagged_as_stale(self) -> None:
        # Synthetic case mirroring the real FR-FIN-* discovery: a well-formed, fully-numbered
        # requirement-shaped token that is NOT in the current Requirement Universe must be
        # extracted (so a real dangling-reference report can flag it), never silently dropped.
        known = MODULE.known_requirement_ids()
        synthetic_id = "FR-ZZZTEST-01"
        self.assertNotIn(synthetic_id, known)
        found = MODULE.cited_ids(f"this references {synthetic_id} which does not exist")
        self.assertIn(synthetic_id, found)

    def test_report_is_disclosure_only_and_never_claims_final_authority(self) -> None:
        import contextlib
        import io

        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            exit_code = MODULE.main()
        self.assertEqual(0, exit_code)
        report = json.loads(buffer.getvalue())
        self.assertFalse(report["final_claim_allowed"])
        self.assertIn("DISCLOSURE_ONLY", report["disposition"])
        for category in report["categories"].values():
            self.assertIn("cites_a_stale_nonexistent_id", category)


if __name__ == "__main__":
    unittest.main()
