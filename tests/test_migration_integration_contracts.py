from __future__ import annotations

import json
import pathlib
import unittest

import jsonschema

ROOT = pathlib.Path(__file__).resolve().parents[1]

SCHEMAS = ["migration-reconciliation-report.v1"]


def load_schema(name: str) -> dict:
    return json.loads((ROOT / "contracts" / f"{name}.schema.json").read_text(encoding="utf-8"))


def load_fixture(name: str, suffix: str) -> dict:
    return json.loads((ROOT / "fixtures" / "contracts" / f"{name}.{suffix}.json").read_text(encoding="utf-8"))


class MigrationIntegrationContractsTest(unittest.TestCase):
    """Batch 8 (Migration/Integration), 137 SS27: v1->v2 dual-read/divergence/cutover/rollback."""

    def test_schema_is_valid_draft202012(self) -> None:
        for name in SCHEMAS:
            jsonschema.Draft202012Validator.check_schema(load_schema(name))

    def test_every_valid_fixture_validates_and_every_invalid_fixture_is_rejected(self) -> None:
        for name in SCHEMAS:
            schema = load_schema(name)
            jsonschema.Draft202012Validator(schema).validate(load_fixture(name, "valid"))
            with self.assertRaises(jsonschema.ValidationError, msg=name):
                jsonschema.Draft202012Validator(schema).validate(load_fixture(name, "invalid"))

    def test_unrecoverable_loss_forbids_cutover_eligibility(self) -> None:
        schema = load_schema("migration-reconciliation-report.v1")
        bad = load_fixture("migration-reconciliation-report.v1", "invalid")
        self.assertEqual(bad["loss_classification"], "UNRECOVERABLE")
        self.assertTrue(bad["cutover_eligible"])
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_a_divergence_can_never_be_classified_none(self) -> None:
        # "문서상 mapping만으로 PASS하지 말고" -- diverged=true + loss_classification=NONE must fail.
        schema = load_schema("migration-reconciliation-report.v1")
        bad = {
            "contract": "ONSURE_MIGRATION_RECONCILIATION_REPORT_V1",
            "reconciliation_id": "r1", "subject_id": "s1",
            "old_representation_digest": "0" * 64, "new_representation_digest": "1" * 64,
            "diverged": True, "diverged_fields": ["field_a"],
            "loss_classification": "NONE", "reconstruction_attempted": False,
            "cutover_eligible": True, "evaluated_at": "2026-08-15T00:00:00Z",
        }
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)


if __name__ == "__main__":
    unittest.main()
