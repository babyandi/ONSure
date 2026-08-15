from __future__ import annotations

import json
import pathlib
import unittest

import jsonschema

ROOT = pathlib.Path(__file__).resolve().parents[1]

SCHEMAS = ["hazard.v1", "safety-case.v1", "appeal-case.v1"]


def load_schema(name: str) -> dict:
    return json.loads((ROOT / "contracts" / f"{name}.schema.json").read_text(encoding="utf-8"))


def load_fixture(name: str, suffix: str) -> dict:
    return json.loads((ROOT / "fixtures" / "contracts" / f"{name}.{suffix}.json").read_text(encoding="utf-8"))


class SafetyAppealContractsTest(unittest.TestCase):
    """Batch 6 (Safety/Appeal), grounded in
    127_SAFETY_HAZARD_AND_CONTESTABILITY_GOVERNANCE.md SS1/SS2/SS8."""

    def test_all_three_schemas_are_valid_draft202012(self) -> None:
        for name in SCHEMAS:
            jsonschema.Draft202012Validator.check_schema(load_schema(name))

    def test_every_valid_fixture_validates_and_every_invalid_fixture_is_rejected(self) -> None:
        for name in SCHEMAS:
            schema = load_schema(name)
            jsonschema.Draft202012Validator(schema).validate(load_fixture(name, "valid"))
            with self.assertRaises(jsonschema.ValidationError, msg=name):
                jsonschema.Draft202012Validator(schema).validate(load_fixture(name, "invalid"))

    def test_catastrophic_hazard_cannot_reach_validated_controlled_directly(self) -> None:
        # 127 SS8 negative case: "unvalidated safe state".
        schema = load_schema("hazard.v1")
        bad = load_fixture("hazard.v1", "invalid")
        self.assertEqual(bad["severity_class"], "CATASTROPHIC")
        self.assertEqual(bad["disposition"], "VALIDATED_CONTROLLED")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_safety_case_cannot_pass_from_security_finding_and_uptime_metrics_alone(self) -> None:
        # 127 SS1.6 negative case: "security finding 0을 safety proof로 사용",
        # "uptime/reliability metric만으로 safe behavior 주장".
        schema = load_schema("safety-case.v1")
        bad = load_fixture("safety-case.v1", "invalid")
        self.assertEqual(set(bad["evidence_classes"]), {"SECURITY_FINDING_COUNT", "UPTIME_METRIC"})
        self.assertEqual(bad["decision"], "PASS_NONFINAL")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_safety_case_never_generates_a_final_claim(self) -> None:
        valid = load_fixture("safety-case.v1", "valid")
        self.assertFalse(valid["final_claim_allowed"])
        self.assertTrue(valid["self_validation_nonfinal"])

    def test_appeal_case_cannot_be_decided_while_still_pending(self) -> None:
        schema = load_schema("appeal-case.v1")
        bad = load_fixture("appeal-case.v1", "invalid")
        self.assertEqual(bad["status"], "DECIDED")
        self.assertEqual(bad["decision"], "PENDING")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_appeal_case_valid_fixture_has_a_reviewer_distinct_from_the_challenged_principal(self) -> None:
        # The schema itself cannot enforce this (documented as a runtime-only check in the
        # description); this test documents the intent the fixture demonstrates, while
        # AppealLedgerTest.java is the real enforcement test.
        valid = load_fixture("appeal-case.v1", "valid")
        self.assertNotEqual(valid["reviewer_principal_id"], valid["challenged_decision_principal_id"])


if __name__ == "__main__":
    unittest.main()
