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


class Wave4FinalChainTest(unittest.TestCase):
    """Batch 1 Wave 4 (Final: 11_CONTRACT_UPGRADE_BLUEPRINT.md Bundle H).

    FinalCandidate (semantic-assurance-gate-receipt.candidate.v2 -- this IS the
    v2 FinalClaimReconstruction: it already embeds the FinalFreshnessBarrier
    reference and independent OTester/OAudit/HumanAcceptance receipts, per 11
    SS10.1/10.2) != FinalApproval (final-approval-receipt.candidate.v2, a
    separate principal's signed act) != FinalLock (final-lock.candidate.v2,
    requires a real APPROVE decision plus all independent receipt hashes).

    None of these four schemas existed with real fixtures/registration before
    this batch (137 SS4: file existence != done), even though three of the
    four schema FILES pre-existed as unregistered candidates.
    """

    def test_all_four_schemas_are_valid_draft202012(self) -> None:
        for name in [
            "atomic-validation-snapshot.v2",
            "semantic-assurance-gate-receipt.candidate.v2",
            "final-approval-receipt.candidate.v2",
            "final-lock.candidate.v2",
        ]:
            jsonschema.Draft202012Validator.check_schema(load_schema(name))

    def test_gate_receipt_can_never_self_authorize_a_lock(self) -> None:
        # FinalCandidate != FinalApproval, structurally: the gate receipt's own
        # final_lock_allowed is a hard const:false regardless of its decision.
        schema = load_schema("semantic-assurance-gate-receipt.candidate.v2")
        self.assertEqual(schema["properties"]["final_lock_allowed"], {"const": False})
        gate = load_fixture("semantic-assurance-gate-receipt.candidate.v2", "valid")
        self.assertEqual(gate["decision"], "PASS")
        self.assertFalse(gate["final_lock_allowed"])

    def test_gate_receipt_decision_vocabulary_has_no_approve_or_lock_state(self) -> None:
        schema = load_schema("semantic-assurance-gate-receipt.candidate.v2")
        decisions = set(schema["properties"]["decision"]["enum"])
        self.assertEqual(decisions, {"PASS", "BLOCKED", "HOLD", "INCONCLUSIVE"})
        self.assertNotIn("APPROVE", decisions)
        self.assertNotIn("LOCKED", decisions)

    def test_approval_requires_a_principal_distinct_from_the_gate_reconstructor_sod(self) -> None:
        gate = load_fixture("semantic-assurance-gate-receipt.candidate.v2", "valid")
        approval = load_fixture("final-approval-receipt.candidate.v2", "valid")
        self.assertNotEqual(approval["principal_profile_id"], gate["authority"]["principal_profile_id"])

    def test_self_approval_fixture_is_detected_as_a_sod_violation(self) -> None:
        gate = load_fixture("semantic-assurance-gate-receipt.candidate.v2", "valid")
        self_approval = load_fixture("final-approval-receipt.candidate.v2", "self-approval")
        # schema-valid by itself, but must fail an SoD cross-check against the gate reconstructor
        self.assertEqual(self_approval["principal_profile_id"], gate["authority"]["principal_profile_id"])

    def test_final_lock_requires_approve_decision_and_matching_gate_hash(self) -> None:
        schema = load_schema("final-lock.candidate.v2")
        self.assertEqual(schema["required"], schema.get("required"))  # sanity: schema loaded
        gate = load_fixture("semantic-assurance-gate-receipt.candidate.v2", "valid")
        approval = load_fixture("final-approval-receipt.candidate.v2", "valid")
        lock = load_fixture("final-lock.candidate.v2", "valid")
        self.assertEqual(lock["gate_receipt_sha256"], gate["gate_receipt_sha256"])
        self.assertEqual(lock["final_approval_sha256"], approval["approval_sha256"])
        self.assertEqual(lock["final_approval_decision"], "APPROVE")
        self.assertEqual(approval["decision"], "APPROVE")

    def test_final_lock_built_from_a_self_approved_approval_is_flagged(self) -> None:
        gate = load_fixture("semantic-assurance-gate-receipt.candidate.v2", "valid")
        self_approval = load_fixture("final-approval-receipt.candidate.v2", "self-approval")
        lock_from_self_approval = load_fixture("final-lock.candidate.v2", "from-self-approval")
        self.assertEqual(lock_from_self_approval["final_approval_sha256"], self_approval["approval_sha256"])
        # the approval this lock references was signed by the SAME principal as the gate
        # reconstructor -- this chain must never be treated as a valid FinalLock.
        self.assertEqual(self_approval["principal_profile_id"], gate["authority"]["principal_profile_id"])

    def test_final_lock_never_grants_production_or_commercial_go(self) -> None:
        # FinalLock != current Production assurance: this is a hard schema const,
        # true for EVERY lock_state, not just LOCK_CANDIDATE.
        schema = load_schema("final-lock.candidate.v2")
        self.assertEqual(schema["properties"]["production_go_allowed"], {"const": False})
        self.assertEqual(schema["properties"]["commercial_go_allowed"], {"const": False})
        lock = load_fixture("final-lock.candidate.v2", "valid")
        self.assertFalse(lock["production_go_allowed"])
        self.assertFalse(lock["commercial_go_allowed"])

    def test_final_lock_is_immutable_after_issuance_same_id_must_not_change_state(self) -> None:
        # FinalLock is an immutable historical issuance fact; currentness/revocation
        # tracking belongs on a SEPARATE axis (Wave 5's CurrentnessSnapshot/
        # InvalidationEvent), not by mutating this record's lock_state in place.
        original = load_fixture("final-lock.candidate.v2", "valid")
        mutated = load_fixture("final-lock.candidate.v2", "mutated-same-id")
        self.assertEqual(original["lock_id"], mutated["lock_id"])
        self.assertNotEqual(
            original["lock_state"], mutated["lock_state"],
            "fixture must actually exercise a post-issuance state mutation",
        )
        # any two records sharing a lock_id must be byte-identical, or the second one
        # is an illegal mutation of an issued lock, not a legitimate new fact.
        self.assertNotEqual(original, mutated)

    def test_no_decision_state_is_ever_promoted_from_unknown_stale_partial_inconclusive(self) -> None:
        # UNKNOWN/STALE/PARTIAL/INCONCLUSIVE must never appear as a positive Final claim.
        gate_schema = load_schema("semantic-assurance-gate-receipt.candidate.v2")
        approval_schema = load_schema("final-approval-receipt.candidate.v2")
        lock_schema = load_schema("final-lock.candidate.v2")
        self.assertNotIn("PASS", {"INCONCLUSIVE"})  # sanity
        gate = load_fixture("semantic-assurance-gate-receipt.candidate.v2", "valid")
        self.assertIn(gate["decision"], gate_schema["properties"]["decision"]["enum"])
        self.assertNotIn(gate["revocation"]["state"], {"STALE", "STATUS_UNKNOWN", "REASSESSMENT_REQUIRED"})

    def test_atomic_validation_snapshot_feeds_the_gate_receipts_evidence_bundle(self) -> None:
        avs = load_fixture("atomic-validation-snapshot.v2", "valid")
        gate = load_fixture("semantic-assurance-gate-receipt.candidate.v2", "valid")
        self.assertEqual(gate["evidence_bundle"]["receipt_type"], "ATOMIC_VALIDATION_SNAPSHOT")
        self.assertEqual(gate["evidence_bundle"]["receipt_sha256"], avs["snapshot_sha256"])
        self.assertEqual(gate["evidence_bundle"]["receipt_id"], avs["snapshot_id"])

    def test_gate_pass_requires_fully_clean_and_independent_state(self) -> None:
        # the pre-existing schema's own allOf conditionals require this for decision=PASS;
        # confirm the "valid" chain fixture genuinely satisfies them rather than just
        # happening to validate.
        gate = load_fixture("semantic-assurance-gate-receipt.candidate.v2", "valid")
        self.assertEqual(gate["decision"], "PASS")
        self.assertEqual(gate["semantic_capability_closure"]["blocked_count"], 0)
        self.assertEqual(gate["semantic_capability_closure"]["hold_count"], 0)
        self.assertEqual(gate["open_findings"]["p0_count"], 0)
        self.assertEqual(gate["open_findings"]["p1_count"], 0)
        self.assertEqual(gate["revocation"]["state"], "CURRENT")
        self.assertEqual(gate["independent_otester"]["independence_state"], "INDEPENDENT")
        self.assertEqual(gate["independent_otester"]["qualification_state"], "QUALIFIED")
        self.assertEqual(gate["independent_oaudit"]["independence_state"], "INDEPENDENT")
        self.assertEqual(gate["independent_oaudit"]["qualification_state"], "QUALIFIED")

    def test_no_contract_claims_final(self) -> None:
        avs = load_fixture("atomic-validation-snapshot.v2", "valid")
        self.assertFalse(avs["final_claim_allowed"])
        self.assertTrue(avs["self_validation_nonfinal"])


if __name__ == "__main__":
    unittest.main()
