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


class Wave7PlatformMetaTest(unittest.TestCase):
    """Batch 1 Wave 7 (Platform/Meta, 77 SS5, 71 SS10/SS11). Last wave of Batch 1."""

    def test_all_four_schemas_are_valid_draft202012(self) -> None:
        for name in [
            "plugin-manifest.v1", "ai-population-receipt.v1",
            "onsure-release-qualification.v1", "contract-active-selector.candidate.v2",
        ]:
            jsonschema.Draft202012Validator.check_schema(load_schema(name))

    def test_revoked_publisher_plugin_cannot_be_qualified(self) -> None:
        schema = load_schema("plugin-manifest.v1")
        revoked = load_fixture("plugin-manifest.v1", "revoked-publisher")
        self.assertTrue(revoked["publisher_revoked"])
        self.assertEqual(revoked["qualification_state"], "REVOKED")
        bad = dict(revoked)
        bad["qualification_state"] = "QUALIFIED"
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_zero_observed_failures_is_sample_bounded_not_a_clean_bill(self) -> None:
        # "zero observed failure != zero failure probability": the receipt must carry
        # an actual statistical bound, not just a bare zero count.
        aipr = load_fixture("ai-population-receipt.v1", "valid")
        self.assertEqual(aipr["observed_failures"], 0)
        self.assertIsNotNone(aipr["interval_or_bound"]["upper"])
        self.assertGreater(aipr["interval_or_bound"]["upper"], 0)

    def test_critical_failures_without_observed_failures_is_rejected(self) -> None:
        schema = load_schema("ai-population-receipt.v1")
        bad = load_fixture("ai-population-receipt.v1", "critical-without-observed")
        self.assertGreater(bad["critical_failures"], 0)
        self.assertEqual(bad["observed_failures"], 0)
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_self_validation_alone_cannot_produce_qualified_release(self) -> None:
        schema = load_schema("onsure-release-qualification.v1")
        self_only = load_fixture("onsure-release-qualification.v1", "self-validation-only")
        self.assertEqual(self_only["independent_verifier_receipts"], [])
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(self_only)

    def test_release_qualification_is_scoped_per_archetype_not_global(self) -> None:
        orq = load_fixture("onsure-release-qualification.v1", "valid")
        states = {row["scope_state"] for row in orq["archetype_qualification_map"]}
        self.assertIn("QUALIFIED", states)
        self.assertIn("NOT_QUALIFIED", states, "must not claim every archetype is qualified uniformly")

    def test_active_contract_selector_does_not_arbitrarily_flip_the_active_version(self) -> None:
        # 137 SS5: "Active Selector 임의 변경 금지" -- this wave's fixture confirms v1
        # stays active (superseded_version=null), it does not promote v2 to active.
        selector = load_fixture("contract-active-selector.candidate.v2", "valid")
        self.assertEqual(selector["active_version"], "v1")
        self.assertIsNone(selector["superseded_version"])
        self.assertEqual(selector["authority"]["purpose"], "CONTRACT_ACTIVATION")

    def test_no_contract_claims_final(self) -> None:
        for name, suffix in [
            ("plugin-manifest.v1", "valid"), ("ai-population-receipt.v1", "valid"),
            ("onsure-release-qualification.v1", "valid"),
        ]:
            fixture = load_fixture(name, suffix)
            self.assertFalse(fixture["final_claim_allowed"], name)
            self.assertTrue(fixture["self_validation_nonfinal"], name)


if __name__ == "__main__":
    unittest.main()
