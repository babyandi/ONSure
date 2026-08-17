from __future__ import annotations

import json
import pathlib
import unittest

import jsonschema

ROOT = pathlib.Path(__file__).resolve().parents[1]

SCHEMAS = [
    "learning-candidate-asset.v1",
    "learning-promotion-receipt.v1",
    "corpus-integrity-report.v1",
    "learning-effectiveness-report.v1",
    "oracle-qualification.v1",
    "oracle-disagreement-case.v1",
    "validator-regression-qualification.v1",
    "derived-learning-lineage-disposition.v1",
    "learning-scope-promotion.v1",
    "validation-experiment.v1",
    "learning-stop-decision.v1",
]


def load_schema(name: str) -> dict:
    return json.loads((ROOT / "contracts" / f"{name}.schema.json").read_text(encoding="utf-8"))


def load_fixture(name: str, suffix: str) -> dict:
    return json.loads((ROOT / "fixtures" / "contracts" / f"{name}.{suffix}.json").read_text(encoding="utf-8"))


class LearningValidationContractsTest(unittest.TestCase):
    """Batch 5 (AI/Meta-Assurance) learning-candidate lifecycle contracts, grounded in
    148_LEARNING_VALIDATION_IMPLEMENTATION_CONTRACT_BLUEPRINT.md SS3 P0 invariants and
    149_LEARNING_VALIDATION_SCHEMA_FIXTURE_SPECIFICATION.md SS A-L positive/negative fixture cases."""

    def test_all_eleven_schemas_are_valid_draft202012(self) -> None:
        for name in SCHEMAS:
            jsonschema.Draft202012Validator.check_schema(load_schema(name))

    def test_every_valid_fixture_validates_and_every_invalid_fixture_is_rejected(self) -> None:
        for name in SCHEMAS:
            schema = load_schema(name)
            jsonschema.Draft202012Validator(schema).validate(load_fixture(name, "valid"))
            with self.assertRaises(jsonschema.ValidationError, msg=name):
                jsonschema.Draft202012Validator(schema).validate(load_fixture(name, "invalid"))

    def test_learning_candidate_asset_never_holds_final_decision_authority(self) -> None:
        # 148 P0 invariant 1: LearningCandidateAsset never issues a Final Decision itself.
        valid = load_fixture("learning-candidate-asset.v1", "valid")
        self.assertFalse(valid["final_decision_authority"])
        invalid = load_fixture("learning-candidate-asset.v1", "invalid")
        self.assertTrue(invalid["final_decision_authority"])  # the named negative case itself
        schema = load_schema("learning-candidate-asset.v1")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(invalid)

    def test_direct_candidate_to_active_promotion_is_rejected_at_schema_level(self) -> None:
        # 148 P0 invariant 2 / 149 P0 negative case 1: Candidate -> ACTIVE direct promotion.
        schema = load_schema("learning-promotion-receipt.v1")
        bad = load_fixture("learning-promotion-receipt.v1", "invalid")
        self.assertEqual(bad["from_state"], "CANDIDATE")
        self.assertEqual(bad["to_state"], "ACTIVE")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_confirmed_corpus_poisoning_forbids_a_clear_decision(self) -> None:
        # 148 P0 invariant 6 / 149 P0 negative case 6: confirmed poisoning/tenant-leak activation.
        schema = load_schema("corpus-integrity-report.v1")
        bad = load_fixture("corpus-integrity-report.v1", "invalid")
        self.assertEqual(bad["poisoning_state"], "CONFIRMED")
        self.assertEqual(bad["decision"], "CLEAR")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_unqualified_non_independent_oracle_cannot_report_qualified(self) -> None:
        # 148 P0 invariant 4 / 149 P0 negative case 4: stale/unqualified oracle used for final PASS.
        schema = load_schema("oracle-qualification.v1")
        bad = load_fixture("oracle-qualification.v1", "invalid")
        self.assertFalse(bad["independent"])
        self.assertEqual(bad["result"], "QUALIFIED")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_open_oracle_disagreement_cannot_carry_a_pass_related_decision(self) -> None:
        # 148 P0 invariant 5 / 149 P0 negative case 5: unresolved multi-oracle disagreement PASS.
        schema = load_schema("oracle-disagreement-case.v1")
        good = load_fixture("oracle-disagreement-case.v1", "valid")
        self.assertEqual(good["status"], "OPEN")
        self.assertIn(good["related_decision"], {"HOLD", "BLOCKED", "NOT_RUN"})
        bad = load_fixture("oracle-disagreement-case.v1", "invalid")
        self.assertEqual(bad["status"], "OPEN")
        self.assertEqual(bad["related_decision"], "PASS")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_validator_failing_golden_regression_cannot_stay_qualified(self) -> None:
        # 148 P0 invariant 9 / 149 P0 negative case 9: regression threshold exceeded, still QUALIFIED.
        schema = load_schema("validator-regression-qualification.v1")
        bad = load_fixture("validator-regression-qualification.v1", "invalid")
        self.assertEqual(bad["golden_result"], "FAIL")
        self.assertGreater(bad["false_positive_drift"], bad["drift_threshold"])
        self.assertEqual(bad["decision"], "QUALIFIED")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_consent_withdrawal_cannot_leave_a_derived_asset_with_no_action(self) -> None:
        # 148 P0 invariant 7 / 149 P0 negative case 7: unresolved lineage stays ACTIVE.
        schema = load_schema("derived-learning-lineage-disposition.v1")
        bad = load_fixture("derived-learning-lineage-disposition.v1", "invalid")
        self.assertEqual(bad["trigger"], "CONSENT_WITHDRAWAL")
        self.assertEqual(bad["disposition"], "NO_ACTION_WITH_PROOF")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_global_scope_promotion_cannot_be_approved_directly_from_tenant(self) -> None:
        # Guards against an automatic jump straight to GLOBAL scope without the INDUSTRY step.
        schema = load_schema("learning-scope-promotion.v1")
        bad = load_fixture("learning-scope-promotion.v1", "invalid")
        self.assertEqual(bad["from_scope"], "TENANT")
        self.assertEqual(bad["to_scope"], "GLOBAL")
        self.assertEqual(bad["decision"], "APPROVED")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_single_stochastic_run_cannot_claim_stability(self) -> None:
        # 148 P0 invariant 12 / 149 P0 negative case 12: single-run stability PASS.
        schema = load_schema("validation-experiment.v1")
        bad = load_fixture("validation-experiment.v1", "invalid")
        self.assertEqual(bad["mode"], "STOCHASTIC")
        self.assertEqual(bad["run_count"], 1)
        self.assertEqual(bad["result"], "STABLE")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_exceeded_budget_cannot_continue_without_stop_or_hold(self) -> None:
        schema = load_schema("learning-stop-decision.v1")
        bad = load_fixture("learning-stop-decision.v1", "invalid")
        self.assertEqual(bad["budget_state"], "EXCEEDED")
        self.assertEqual(bad["decision"], "CONTINUE")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)


if __name__ == "__main__":
    unittest.main()
