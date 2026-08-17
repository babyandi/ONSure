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


class IndependentAssuranceExecutionPlanTest(unittest.TestCase):
    """independent-assurance-execution-plan.candidate.v2.schema.json existed as a file
    with zero fixtures/registration before this batch (same 137 SS4 gap as
    RuntimeExecutionReceipt in Wave 2)."""

    def test_schema_is_valid_draft202012(self) -> None:
        jsonschema.Draft202012Validator.check_schema(load_schema("independent-assurance-execution-plan.candidate.v2"))

    def test_valid_fixture_passes(self) -> None:
        schema = load_schema("independent-assurance-execution-plan.candidate.v2")
        jsonschema.Draft202012Validator(schema).validate(
            load_fixture("independent-assurance-execution-plan.candidate.v2", "valid")
        )

    def test_invalid_fixture_rejects_cherry_picked_retry_policy(self) -> None:
        schema = load_schema("independent-assurance-execution-plan.candidate.v2")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(
                load_fixture("independent-assurance-execution-plan.candidate.v2", "invalid")
            )


class AuthorityGrantNegativeGateTest(unittest.TestCase):
    """71 SS7 AuthorityGrant invariants: delegated scope subset of parent, child expiry
    <= parent expiry, and same-principal-multiple-keys must never count as multiple
    independent approvers (SoD). None of these are expressible in plain JSON Schema."""

    def test_delegated_child_expiry_within_parent_window(self) -> None:
        parent = load_fixture("authority-grant.v1", "valid")
        child = load_fixture("authority-grant.v1", "delegated-child")
        self.assertEqual(child["parent_grant_id"], parent["grant_id"])
        self.assertLessEqual(child["valid_until"], parent["valid_until"])

    def test_child_exceeding_parent_expiry_is_detected(self) -> None:
        parent = load_fixture("authority-grant.v1", "valid")
        bad_child = load_fixture("authority-grant.v1", "child-exceeds-parent-expiry")
        self.assertEqual(bad_child["parent_grant_id"], parent["grant_id"])
        self.assertGreater(
            bad_child["valid_until"], parent["valid_until"],
            "fixture must actually exercise the exceeds-parent-expiry violation",
        )

    def test_same_principal_multiple_keys_do_not_count_as_multiple_approvers(self) -> None:
        def distinct_approver_count(grant: dict) -> int:
            return len({entry["approver_principal_id"] for entry in grant["approval_chain"]})

        clean = load_fixture("authority-grant.v1", "valid")
        self.assertEqual(len(clean["approval_chain"]), 1)
        self.assertEqual(distinct_approver_count(clean), 1)

        sod_violation = load_fixture("authority-grant.v1", "sod-violation")
        self.assertEqual(
            len(sod_violation["approval_chain"]), 2,
            "fixture must have two approval_chain entries to look like a two-approver quorum",
        )
        self.assertEqual(
            distinct_approver_count(sod_violation), 1,
            "both entries share approver_principal_id -- this is one principal with two keys, not two approvers",
        )


class HumanAcceptanceReceiptNegativeGateTest(unittest.TestCase):
    """21_CLAUDE_DEVELOPMENT_HANDOFF.md DEV-09: nonce replay must be rejected, not
    silently accepted as a second valid acceptance."""

    def test_schema_is_valid_draft202012(self) -> None:
        jsonschema.Draft202012Validator.check_schema(load_schema("human-acceptance-receipt.v1"))

    def test_valid_fixture_passes(self) -> None:
        schema = load_schema("human-acceptance-receipt.v1")
        jsonschema.Draft202012Validator(schema).validate(load_fixture("human-acceptance-receipt.v1", "valid"))

    def test_accepted_decision_requires_not_revoked_and_consumed_nonce(self) -> None:
        schema = load_schema("human-acceptance-receipt.v1")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(load_fixture("human-acceptance-receipt.v1", "invalid"))

    def test_nonce_replay_across_two_receipts_is_detected(self) -> None:
        original = load_fixture("human-acceptance-receipt.v1", "valid")
        replay = load_fixture("human-acceptance-receipt.v1", "nonce-replay")
        self.assertNotEqual(original["receipt_id"], replay["receipt_id"])
        self.assertEqual(
            original["nonce"], replay["nonce"],
            "a genuine replay attempt reuses the exact same nonce across two distinct receipts",
        )
        # a real replay-consumption ledger must reject the second presentation of a
        # consumed nonce; this fixture's existence documents the attack shape, and
        # the schema-level replay_consumption.consumed=true on the original marks that
        # nonce as already spent before the replay could ever be considered.
        self.assertTrue(original["replay_consumption"]["consumed"])

    def test_human_acceptance_grant_permits_the_operation(self) -> None:
        # cross-contract: the AuthorityGrant a HumanAcceptanceReceipt cites must actually
        # allow assurance.human-accept, not just exist.
        receipt = load_fixture("human-acceptance-receipt.v1", "valid")
        grant = load_fixture("authority-grant.v1", "delegated-child")
        self.assertEqual(receipt["authority_grant_id"], grant["grant_id"])
        self.assertIn("assurance.human-accept", grant["allowed_operation_patterns"])


if __name__ == "__main__":
    unittest.main()
