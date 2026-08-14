from __future__ import annotations

import json
import pathlib
import unittest

import jsonschema

ROOT = pathlib.Path(__file__).resolve().parents[1]


def load_schema(name: str) -> dict:
    return json.loads((ROOT / "contracts" / f"{name}.schema.json").read_text(encoding="utf-8"))


def load_fixture(name: str, suffix: str) -> dict:
    return json.loads((ROOT / "fixtures" / "contracts" / f"{name}.{suffix}.json").read_text(encoding="utf-8"))


class Wave5DeploymentCurrentnessTest(unittest.TestCase):
    """Batch 1 Wave 5 (Deployment/Currentness, 29_DEPLOYMENT_RUNTIME_CURRENTNESS_AND_
    REVOCATION_DESIGN.md + 71 SS2/SS12 + 39 SS3). VERIFIED != DEPLOYED != RUNNING !=
    CURRENT: each of these is tracked by a separate contract, not folded into one."""

    def test_all_eight_schemas_are_valid_draft202012(self) -> None:
        for name in [
            "build-artifact-identity.v1", "deployment-target.v1", "deployment-revision.v1",
            "runtime-instance.v1", "runtime-population-snapshot.v1", "currentness-snapshot.v1",
            "invalidation-event.v1", "recovery-qualification-receipt.v1",
        ]:
            jsonschema.Draft202012Validator.check_schema(load_schema(name))

    def test_verified_to_deployed_chain_digests_match(self) -> None:
        bai = load_fixture("build-artifact-identity.v1", "valid")
        dr = load_fixture("deployment-revision.v1", "valid")
        self.assertEqual(dr["expected_artifact_digest"], bai["artifact_sha256"])
        self.assertEqual(dr["observed_artifact_digest"], bai["artifact_sha256"])

    def test_drifted_deployment_revision_is_detected(self) -> None:
        bai = load_fixture("build-artifact-identity.v1", "valid")
        drift = load_fixture("deployment-revision.v1", "drifted")
        self.assertEqual(drift["expected_artifact_digest"], bai["artifact_sha256"])
        self.assertNotEqual(
            drift["observed_artifact_digest"], bai["artifact_sha256"],
            "fixture must actually exercise expected != observed drift",
        )

    def test_runtime_population_snapshot_references_real_instances(self) -> None:
        rps = load_fixture("runtime-population-snapshot.v1", "valid")
        ri1 = load_fixture("runtime-instance.v1", "valid")
        ri2 = load_fixture("runtime-instance.v1", "second-instance")
        self.assertEqual(set(rps["exact_runtime_instance_ids"]), {ri1["runtime_instance_id"], ri2["runtime_instance_id"]})
        self.assertTrue(rps["runtime_population_complete"])

    def test_currentness_current_requires_matching_digests_and_complete_population(self) -> None:
        # doc71 SS2 conditional: CURRENT implies expected==observed AND population complete.
        dr = load_fixture("deployment-revision.v1", "valid")
        rps = load_fixture("runtime-population-snapshot.v1", "valid")
        cs = load_fixture("currentness-snapshot.v1", "valid")
        self.assertEqual(cs["state"], "CURRENT")
        self.assertEqual(cs["expected_artifact_digest"], dr["expected_artifact_digest"])
        self.assertEqual(cs["observed_artifact_digest"], dr["observed_artifact_digest"])
        self.assertEqual(cs["expected_artifact_digest"], cs["observed_artifact_digest"])
        self.assertTrue(cs["runtime_population_complete"])
        self.assertTrue(cs["positive_final_eligible"])

    def test_stale_currentness_from_drift_is_not_positive_final_eligible(self) -> None:
        stale = load_fixture("currentness-snapshot.v1", "stale-drift")
        self.assertEqual(stale["state"], "STALE")
        self.assertNotEqual(stale["expected_artifact_digest"], stale["observed_artifact_digest"])
        self.assertFalse(stale["positive_final_eligible"])

    def test_currentness_is_the_separate_mutable_axis_final_lock_is_not(self) -> None:
        # FinalLock (Wave 4) is immutable after issuance; CurrentnessSnapshot is the
        # separate, repeatedly-re-evaluated axis that references it by digest only.
        lock = load_fixture("final-lock.candidate.v2", "valid")
        cs_current = load_fixture("currentness-snapshot.v1", "valid")
        cs_stale = load_fixture("currentness-snapshot.v1", "stale-drift")
        self.assertEqual(cs_current["final_lock_digest"], lock["lock_sha256"])
        self.assertEqual(cs_stale["final_lock_digest"], lock["lock_sha256"])
        # same referenced lock, two DIFFERENT currentness_snapshot_id at two different
        # states -- the lock itself was never rewritten to produce this.
        self.assertNotEqual(cs_current["currentness_snapshot_id"], cs_stale["currentness_snapshot_id"])
        self.assertNotEqual(cs_current["state"], cs_stale["state"])

    def test_invalidation_event_references_the_currentness_snapshot_it_would_invalidate(self) -> None:
        cs = load_fixture("currentness-snapshot.v1", "valid")
        event = load_fixture("invalidation-event.v1", "valid")
        self.assertIn(cs["currentness_snapshot_id"], event["source_subject_ids"])

    def test_recovery_with_missing_evidence_forbids_prior_final_carryover(self) -> None:
        # 98: "복구 후 자동 Current 금지" -- missing/unverifiable objects must force
        # NO_PRIOR_ASSURANCE_CARRYOVER, never a silent PASS-with-full-carryover.
        clean = load_fixture("recovery-qualification-receipt.v1", "valid")
        self.assertEqual(clean["missing_or_unverifiable_objects"], [])
        self.assertEqual(clean["qualification_decision"], "PASS")
        self.assertIn(clean["assurance_ceiling"], {"FULL_PRIOR_ASSURANCE_ELIGIBLE", "REASSESSMENT_REQUIRED"})

        missing = load_fixture("recovery-qualification-receipt.v1", "missing-evidence")
        self.assertGreater(len(missing["missing_or_unverifiable_objects"]), 0)
        self.assertIn(missing["qualification_decision"], {"FAIL", "PARTIAL_HOLD"})
        self.assertEqual(missing["assurance_ceiling"], "NO_PRIOR_ASSURANCE_CARRYOVER")

    def test_no_contract_claims_final(self) -> None:
        for name, suffix in [
            ("build-artifact-identity.v1", "valid"), ("deployment-target.v1", "valid"),
            ("deployment-revision.v1", "valid"), ("runtime-instance.v1", "valid"),
            ("runtime-population-snapshot.v1", "valid"), ("currentness-snapshot.v1", "valid"),
            ("invalidation-event.v1", "valid"), ("recovery-qualification-receipt.v1", "valid"),
        ]:
            fixture = load_fixture(name, suffix)
            self.assertFalse(fixture["final_claim_allowed"], name)
            self.assertTrue(fixture["self_validation_nonfinal"], name)


if __name__ == "__main__":
    unittest.main()
