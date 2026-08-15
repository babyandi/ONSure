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
        manifest_gen = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "materialize-requirement-authority-manifest.py")],
            cwd=ROOT, text=True, capture_output=True, check=False,
        )
        assert manifest_gen.returncode == 0, manifest_gen.stdout + manifest_gen.stderr
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
        cls.authority_manifest = json.loads((UNIVERSE_DIR / "requirement-authority-source-manifest.json").read_text(encoding="utf-8"))
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

    def test_deterministic_clean_rerun_produces_identical_digest(self) -> None:
        # 145 SS7 / this batch's requalification requirement: a CLEAN rerun must be
        # reproducible, not just internally consistent on a single run.
        rerun = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "generate-requirement-universe.py")],
            cwd=ROOT, text=True, capture_output=True, check=False,
        )
        assert rerun.returncode == 0, rerun.stdout + rerun.stderr
        rerun_digest = json.loads(rerun.stdout)["requirement_manifest_digest"]
        self.assertEqual(rerun_digest, self.receipt["requirement_manifest_digest"])

    def test_universe_never_claims_final(self) -> None:
        self.assertFalse(self.snapshot["final_claim_allowed"])
        self.assertTrue(self.snapshot["self_validation_nonfinal"])
        self.assertFalse(self.receipt["final_claim_allowed"])

    def test_trace_scan_never_declares_lock_eligible_in_batch_0(self) -> None:
        # 91 SS5: exact denominator/applicability not yet confirmed, so Batch 0 must
        # never claim lock eligibility regardless of orphan counts.
        self.assertFalse(self.trace_report["lock_eligible"])

    def test_explicit_requirements_are_a_subset_of_known_prefixes(self) -> None:
        allowed_prefixes = ("FR-COM-", "FR-META-", "FR-FRESH-", "FR-LEARN-", "NFR-")
        for record in self.records:
            if record["source_class"] == "EXPLICIT_ID":
                self.assertTrue(
                    record["explicit_id"].startswith(allowed_prefixes),
                    record["explicit_id"],
                )

    def test_authority_manifest_is_fail_closed(self) -> None:
        # 145: DesignArtifactInventory membership != requirement-origination authority.
        # Every scanned doc must have a row; UNREVIEWED rows must be excluded from the
        # eligible/scanned population (fail-closed), not silently promoted.
        eligible_dispositions = {"NORMATIVE_CURRENT", "NORMATIVE_REFINEMENT"}
        eligible_paths = {
            r["artifact_path"] for r in self.authority_manifest["rows"]
            if r["requirement_source_disposition"] in eligible_dispositions
        }
        scanned_paths = {a["path"] for a in self.snapshot["authority_document_population"]}
        self.assertEqual(eligible_paths, scanned_paths)
        self.assertGreater(self.authority_manifest["review_summary"]["unreviewed_count"], 0)
        unreviewed_paths = {
            r["artifact_path"] for r in self.authority_manifest["rows"] if r["review_state"] == "UNREVIEWED"
        }
        self.assertEqual(unreviewed_paths & scanned_paths, set())

    def test_no_longest_text_wins_every_explicit_record_has_disclosed_authority(self) -> None:
        # 145 SS1/SS4: LONGEST_TEXT_WINS is prohibited; every EXPLICIT_ID record must
        # disclose which authority tier its canonical text came from.
        for record in self.records:
            if record["source_class"] == "EXPLICIT_ID":
                self.assertIn(
                    record["canonicalization_authority"], ("NORMATIVE_CURRENT", "NORMATIVE_REFINEMENT"),
                    record["explicit_id"],
                )

    def test_fr_learn_population_matches_qa_cross_check_files(self) -> None:
        # cross-contract check against the independently-produced QA extension candidates.
        ext_001_025 = json.loads((ROOT / "contracts" / "product-design-requirement-universe.learning-extension.candidate.v1.json").read_text(encoding="utf-8"))
        ext_026_077 = json.loads((ROOT / "contracts" / "product-design-requirement-universe.learning-extension-026-077.candidate.v1.json").read_text(encoding="utf-8"))
        fr_learn_ids = {r["requirement_id"] for r in self.records if r["requirement_id"].startswith("FR-LEARN-")}
        self.assertTrue(set(ext_001_025["requirement_ids"]).issubset(fr_learn_ids))
        self.assertEqual(ext_026_077["requirement_count"], 52)
        numbers = sorted(int(x.rsplit("-", 1)[-1]) for x in fr_learn_ids)
        self.assertEqual(numbers, list(range(1, 96)), "FR-LEARN-001..095 must be complete with no gaps")

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

    def test_fr_com_008_trace_orphan_was_closed_by_real_observation_evidence(self) -> None:
        # Regression guard, not a worked example: FR-COM-008 (CRITICAL, POSITIVE_CLAIM_GATE,
        # cites contracts/main-branch-protection.v1.json) was previously a live P0 trace orphan
        # (zero test_refs/evidence_refs found by the mechanical scanner). Batch 9 closed it with
        # a real GITHUB_API observation (status/main-branch-protection-evidence.v1.json,
        # decision=FAIL -- main is genuinely unprotected, a disclosed finding, not fabricated
        # PASS) plus tests/test_main_branch_protection.py citing "FR-COM-008" directly. This test
        # now asserts the orphan STAYS closed; if it ever starts failing again, that means the
        # test/evidence refs were removed or the requirement lost its trace path, which is a real
        # regression to investigate -- not something to silently re-loosen back to the old
        # assertion. (This test file's own citation of "FR-COM-008" is excluded from the
        # scanner's test_refs via scan-global-trace-closure.py's META_TEST_FILES set.)
        row = next(r for r in self.trace_report["rows"] if r["requirement_id"] == "FR-COM-008")
        self.assertNotIn("REQUIREMENT_WITHOUT_TEST", row["orphan_dimensions"])
        self.assertNotIn("REQUIREMENT_WITHOUT_EVIDENCE_PATH", row["orphan_dimensions"])
        self.assertNotIn("FR-COM-008", self.trace_report["orphans"]["p0"])


if __name__ == "__main__":
    unittest.main()
