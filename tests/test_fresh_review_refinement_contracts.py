from __future__ import annotations

import json
import pathlib
import unittest

import jsonschema

ROOT = pathlib.Path(__file__).resolve().parents[1]

SCHEMAS = ["offboarding-closure.v1", "engagement-authorization.v1", "accessible-claim-render.v1"]


def load_schema(name: str) -> dict:
    return json.loads((ROOT / "contracts" / f"{name}.schema.json").read_text(encoding="utf-8"))


def load_fixture(name: str, suffix: str) -> dict:
    return json.loads((ROOT / "fixtures" / "contracts" / f"{name}.{suffix}.json").read_text(encoding="utf-8"))


class FreshReviewRefinementContractsTest(unittest.TestCase):
    """Batch 7 (Fresh Review Refinements), grounded in
    126_FINAL_FRESH_PRODUCT_DESIGN_REVIEW_AND_SCOPE_CLOSURE.md FR-FRESH-001/002/003."""

    def test_all_three_schemas_are_valid_draft202012(self) -> None:
        for name in SCHEMAS:
            jsonschema.Draft202012Validator.check_schema(load_schema(name))

    def test_every_valid_fixture_validates_and_every_invalid_fixture_is_rejected(self) -> None:
        for name in SCHEMAS:
            schema = load_schema(name)
            jsonschema.Draft202012Validator(schema).validate(load_fixture(name, "valid"))
            with self.assertRaises(jsonschema.ValidationError, msg=name):
                jsonschema.Draft202012Validator(schema).validate(load_fixture(name, "invalid"))

    def test_legal_hold_forbids_deletion_in_offboarding(self) -> None:
        # FR-FRESH-003: "legal hold는 deletion보다 우선하지만 access authority를 자동 연장하지 않음".
        schema = load_schema("offboarding-closure.v1")
        bad = load_fixture("offboarding-closure.v1", "invalid")
        self.assertTrue(bad["legal_hold"])
        self.assertEqual(bad["deletion_state"], "DELETED")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_localization_fallback_cannot_drop_limitation_disclosure(self) -> None:
        # FR-FRESH-002: "localization fallback이 UNKNOWN/HOLD/limitation 문구를 누락하지 않음".
        schema = load_schema("accessible-claim-render.v1")
        bad = load_fixture("accessible-claim-render.v1", "invalid")
        self.assertTrue(bad["localization_fallback_used"])
        self.assertFalse(bad["limitation_disclosure_present"])
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_accessible_claim_render_never_permits_a_color_only_signal(self) -> None:
        valid = load_fixture("accessible-claim-render.v1", "valid")
        self.assertFalse(valid["color_only_signal"])


if __name__ == "__main__":
    unittest.main()
