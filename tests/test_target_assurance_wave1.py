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


class TargetAssuranceWave1Test(unittest.TestCase):
    """Batch 1 Wave 1 (Identity Foundation) contracts, built after DCQ-0002's
    resolution (141_REQUIREMENT_UNIVERSE_AUTHORITY_DECISION.md): TargetManifest,
    TargetAssuranceRequirementUniverseSnapshot, its migration mapping from the
    pre-existing candidate schema, and the shared DenominatorEpoch contract."""

    def test_all_four_schemas_are_valid_draft202012(self) -> None:
        for name in [
            "target-manifest.v1",
            "target-assurance-requirement-universe-snapshot.v1",
            "target-assurance-requirement-universe-migration-mapping.v1",
            "denominator-epoch.v1",
        ]:
            jsonschema.Draft202012Validator.check_schema(load_schema(name))

    def test_snapshot_fixture_references_a_real_target_manifest(self) -> None:
        manifest = load_fixture("target-manifest.v1", "valid")
        snapshot = load_fixture("target-assurance-requirement-universe-snapshot.v1", "valid")
        self.assertEqual(snapshot["target_manifest_id"], manifest["manifest_id"])
        self.assertEqual(snapshot["target_id"], manifest["target_id"])

    def test_snapshot_fixture_references_a_real_epoch(self) -> None:
        epoch = load_fixture("denominator-epoch.v1", "valid")
        snapshot = load_fixture("target-assurance-requirement-universe-snapshot.v1", "valid")
        self.assertEqual(epoch["epoch_type"], "REQUIREMENT")
        self.assertEqual(snapshot["requirement_epoch_id"], epoch["epoch_id"])

    def test_migration_mapping_covers_exactly_the_real_old_vocabulary(self) -> None:
        # cross-contract check against the REAL pre-existing schema, not an assumption
        old_schema = json.loads(
            (ROOT / "contracts" / "requirement-universe-snapshot.candidate.v2.schema.json").read_text(encoding="utf-8")
        )
        old_values = set(old_schema["properties"]["source_classes"]["items"]["enum"])
        mapping = load_fixture("target-assurance-requirement-universe-migration-mapping.v1", "valid")
        mapped_values = {entry["old_value"] for entry in mapping["mappings"]}
        self.assertEqual(old_values, mapped_values, "migration mapping must cover every old_value, no more no less")

    def test_migration_candidate_values_are_members_of_the_new_vocabulary(self) -> None:
        new_schema = load_schema("target-assurance-requirement-universe-snapshot.v1")
        new_values = set(new_schema["properties"]["source_classes"]["items"]["enum"])
        mapping = load_fixture("target-assurance-requirement-universe-migration-mapping.v1", "valid")
        for entry in mapping["mappings"]:
            for candidate in entry["candidate_new_values"]:
                self.assertIn(candidate, new_values, entry)

    def test_migration_disposition_arity_is_honest(self) -> None:
        mapping = load_fixture("target-assurance-requirement-universe-migration-mapping.v1", "valid")
        for entry in mapping["mappings"]:
            if entry["disposition"] == "DIRECT":
                self.assertEqual(len(entry["candidate_new_values"]), 1, entry)
            if entry["disposition"] == "UNRECOVERABLE":
                self.assertEqual(len(entry["candidate_new_values"]), 0, entry)

    def test_no_contract_claims_final(self) -> None:
        for name, suffix in [
            ("target-manifest.v1", "valid"),
            ("target-assurance-requirement-universe-snapshot.v1", "valid"),
            ("target-assurance-requirement-universe-migration-mapping.v1", "valid"),
            ("denominator-epoch.v1", "valid"),
        ]:
            fixture = load_fixture(name, suffix)
            self.assertFalse(fixture["final_claim_allowed"], name)
            self.assertTrue(fixture["self_validation_nonfinal"], name)

    def test_target_manifest_type_reuses_product_scope_vocabulary(self) -> None:
        # target_type must not invent a competing vocabulary from product-scope.v1.json
        product_scope = json.loads((ROOT / "contracts" / "product-scope.v1.json").read_text(encoding="utf-8"))
        allowed = set(product_scope["commercial_positioning"]["supported_target_types"])
        schema_values = set(load_schema("target-manifest.v1")["properties"]["target_type"]["enum"])
        self.assertEqual(allowed, schema_values)


if __name__ == "__main__":
    unittest.main()
