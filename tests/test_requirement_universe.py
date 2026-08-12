from __future__ import annotations

import json
import pathlib
import subprocess
import sys
import unittest

import jsonschema

ROOT = pathlib.Path(__file__).resolve().parents[1]
UNIVERSE_DIR = ROOT / ".onsure" / "requirement-universe"


class RequirementUniverseBatch0Test(unittest.TestCase):
    """Batch 0 (137_CLAUDE_DEVELOPMENT_MASTER_HANDOFF.md) RU-01..RU-07 execution test.

    Actually runs the generator and scanner (not fixture-only), then checks the
    real output against structural invariants required by 88/90/91/92.
    """

    @classmethod
    def setUpClass(cls) -> None:
        gen = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "generate-requirement-universe.py")],
            cwd=ROOT, text=True, capture_output=True, check=False,
        )
        assert gen.returncode == 0, gen.stdout + gen.stderr
        scan = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "scan-global-trace-closure.py")],
            cwd=ROOT, text=True, capture_output=True, check=False,
        )
        assert scan.returncode == 0, scan.stdout + scan.stderr
        cls.snapshot = json.loads((UNIVERSE_DIR / "requirement-universe-snapshot.json").read_text(encoding="utf-8"))
        cls.records = json.loads((UNIVERSE_DIR / "requirement-records.json").read_text(encoding="utf-8"))
        cls.receipt = json.loads((UNIVERSE_DIR / "requirement-universe-generation-receipt.json").read_text(encoding="utf-8"))
        cls.trace_report = json.loads((UNIVERSE_DIR / "global-trace-scan-report.json").read_text(encoding="utf-8"))

    def test_snapshot_matches_schema(self) -> None:
        schema = json.loads((ROOT / "contracts" / "requirement-universe-snapshot.v2.schema.json").read_text(encoding="utf-8"))
        jsonschema.Draft202012Validator(schema).validate(self.snapshot)

    def test_record_sample_matches_schema(self) -> None:
        schema = json.loads((ROOT / "contracts" / "requirement-record.v2.schema.json").read_text(encoding="utf-8"))
        validator = jsonschema.Draft202012Validator(schema)
        for record in self.records:
            validator.validate(record)

    def test_trace_report_matches_schema(self) -> None:
        schema = json.loads((ROOT / "contracts" / "global-trace-scan-report.v1.schema.json").read_text(encoding="utf-8"))
        jsonschema.Draft202012Validator(schema).validate(self.trace_report)

    def test_requirement_ids_are_unique(self) -> None:
        ids = self.snapshot["requirement_ids"]
        self.assertEqual(len(ids), len(set(ids)))

    def test_authority_document_population_is_docs_master_scoped(self) -> None:
        for entry in self.snapshot["authority_document_population"]:
            self.assertTrue(entry["path"].startswith("docs/master/"), entry["path"])

    def test_universe_never_claims_final(self) -> None:
        self.assertFalse(self.snapshot["final_claim_allowed"])
        self.assertTrue(self.snapshot["self_validation_nonfinal"])
        self.assertFalse(self.receipt["final_claim_allowed"])

    def test_trace_scan_never_declares_lock_eligible_in_batch_0(self) -> None:
        # 91 SS5: exact denominator/applicability not yet confirmed, so Batch 0 must
        # never claim lock eligibility regardless of orphan counts.
        self.assertFalse(self.trace_report["lock_eligible"])

    def test_explicit_requirements_are_a_subset_of_known_prefixes(self) -> None:
        allowed_prefixes = ("FR-COM-", "FR-META-", "FR-FRESH-", "NFR-")
        for record in self.records:
            if record["source_class"] == "EXPLICIT_ID":
                self.assertTrue(
                    record["explicit_id"].startswith(allowed_prefixes),
                    record["explicit_id"],
                )

    def test_duplicate_candidate_ids_reference_real_requirements(self) -> None:
        # cross-contract check: snapshot.unresolved_duplicate_candidates[].requirement_ids
        # must all exist in requirement_ids (referential integrity across the same contract).
        known_ids = set(self.snapshot["requirement_ids"])
        for group in self.snapshot["unresolved_duplicate_candidates"]:
            self.assertGreaterEqual(len(set(group["requirement_ids"])), 2, group)
            for requirement_id in group["requirement_ids"]:
                self.assertIn(requirement_id, known_ids)

    def test_trace_rows_reference_real_requirements(self) -> None:
        # cross-contract check: trace-scan-report.rows must be exactly the snapshot population.
        snapshot_ids = set(self.snapshot["requirement_ids"])
        row_ids = {row["requirement_id"] for row in self.trace_report["rows"]}
        self.assertEqual(snapshot_ids, row_ids)

    def test_fr_com_008_is_flagged_orphan_without_test_or_evidence_path(self) -> None:
        # Known, disclosed real finding: FR-COM-008 (CRITICAL, POSITIVE_CLAIM_GATE, cites
        # contracts/main-branch-protection.v1.json) has zero test_refs/evidence_refs found
        # by the mechanical scanner. This must surface, not silently pass.
        row = next(r for r in self.trace_report["rows"] if r["requirement_id"] == "FR-COM-008")
        self.assertIn("REQUIREMENT_WITHOUT_TEST", row["orphan_dimensions"])
        self.assertIn("REQUIREMENT_WITHOUT_EVIDENCE_PATH", row["orphan_dimensions"])
        self.assertIn("FR-COM-008", self.trace_report["orphans"]["p0"])


if __name__ == "__main__":
    unittest.main()
