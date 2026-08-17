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


class Wave6CompositionCertificateTest(unittest.TestCase):
    """Batch 1 Wave 6 (Composition/Certificate, 71 SS3/4/6 + 31 SS8)."""

    def test_all_five_schemas_are_valid_draft202012(self) -> None:
        for name in [
            "assurance-subject-graph.v1", "assurance-composition-snapshot.v1",
            "assurance-certificate.v1", "assurance-revocation-event.candidate.v2",
            "offline-trust-bundle.v1",
        ]:
            jsonschema.Draft202012Validator.check_schema(load_schema(name))

    def test_composition_references_a_real_subject_graph(self) -> None:
        graph = load_fixture("assurance-subject-graph.v1", "valid")
        composition = load_fixture("assurance-composition-snapshot.v1", "valid")
        self.assertEqual(composition["subject_population_digest"], graph["subject_population_digest"])
        self.assertEqual(composition["graph_head_digest"], graph["graph_head_digest"])

    def test_hard_edge_child_fail_with_parent_pass_is_rejected_at_schema_level(self) -> None:
        # 71 SS4 invariant: Critical HARD child FAIL/INVALIDATED/REVOKED forbids parent PASS.
        # This turned out to be expressible directly in the schema's own allOf/items
        # conditional, so the negative case is a genuine schema-validation failure,
        # not merely a cross-contract convention.
        schema = load_schema("assurance-composition-snapshot.v1")
        bad = load_fixture("assurance-composition-snapshot.v1", "hard-fail-parent-pass")
        self.assertEqual(bad["decision"], "PASS")
        self.assertEqual(bad["input_results"][0]["edge_propagation_class"], "HARD")
        self.assertEqual(bad["input_results"][0]["child_decision"], "FAIL")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad)

    def test_certificate_currentness_gates_decision(self) -> None:
        # signature valid != current assurance: decision must track currentness_state_at_issue.
        schema = load_schema("assurance-certificate.v1")
        cert = load_fixture("assurance-certificate.v1", "valid")
        self.assertEqual(cert["currentness_state_at_issue"], "CURRENT")
        self.assertIn(cert["decision"], {"PASS", "PASS_WITH_LIMITATIONS"})
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(load_fixture("assurance-certificate.v1", "invalid"))

    def test_certificate_references_the_final_lock_by_digest_only(self) -> None:
        lock = load_fixture("final-lock.candidate.v2", "valid")
        cert = load_fixture("assurance-certificate.v1", "valid")
        self.assertEqual(cert["final_lock_digest"], lock["lock_sha256"])

    def test_high_tier_certificate_requires_a_revalidation_deadline(self) -> None:
        cert = load_fixture("assurance-certificate.v1", "valid")
        self.assertEqual(cert["assurance_tier"], "TIER_3_HIGH")
        self.assertIsNotNone(cert["revalidation_due_at"])

    def test_revocation_event_targets_the_real_certificate(self) -> None:
        cert = load_fixture("assurance-certificate.v1", "valid")
        revocation = load_fixture("assurance-revocation-event.candidate.v2", "valid")
        self.assertEqual(revocation["subject"]["subject_type"], "CERTIFICATE")
        self.assertEqual(revocation["subject"]["subject_id"], cert["certificate_id"])

    def test_offline_trust_bundle_caps_trust_level_on_local_clock_only(self) -> None:
        schema = load_schema("offline-trust-bundle.v1")
        local_clock = load_fixture("offline-trust-bundle.v1", "local-clock-only")
        self.assertEqual(local_clock["trusted_time_evidence"]["source"], "LOCAL_OS_CLOCK_ONLY")
        self.assertIn(local_clock["trusted_time_evidence"]["trust_level"], {"LOW", "UNTRUSTED"})
        # confirm the schema itself enforces this, not just the fixture's honesty
        bad_local_clock = dict(local_clock)
        bad_local_clock["trusted_time_evidence"] = dict(local_clock["trusted_time_evidence"])
        bad_local_clock["trusted_time_evidence"]["trust_level"] = "HIGH"
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(bad_local_clock)

    def test_offline_uncertainty_is_never_hidden_status_degrades_explicitly(self) -> None:
        clean = load_fixture("offline-trust-bundle.v1", "valid")
        uncertain = load_fixture("offline-trust-bundle.v1", "local-clock-only")
        self.assertEqual(clean["offline_status"], "OFFLINE_CURRENT_WITHIN_GRACE")
        self.assertEqual(uncertain["offline_status"], "OFFLINE_STATUS_UNCERTAIN")
        self.assertNotEqual(clean["offline_status"], uncertain["offline_status"])

    def test_no_contract_claims_final(self) -> None:
        for name, suffix in [
            ("assurance-subject-graph.v1", "valid"), ("assurance-composition-snapshot.v1", "valid"),
            ("assurance-certificate.v1", "valid"), ("offline-trust-bundle.v1", "valid"),
        ]:
            fixture = load_fixture(name, suffix)
            self.assertFalse(fixture["final_claim_allowed"], name)
            self.assertTrue(fixture["self_validation_nonfinal"], name)


if __name__ == "__main__":
    unittest.main()
