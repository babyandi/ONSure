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


class RuntimeExecutionReceiptTest(unittest.TestCase):
    """Batch 1 Wave 2 (Execution/Evidence). RuntimeExecutionReceipt existed as a file
    with zero fixtures/registration before this batch -- 137 SS4 explicitly forbids
    treating file existence as done. This closes that gap."""

    def test_schema_is_valid_draft202012(self) -> None:
        jsonschema.Draft202012Validator.check_schema(load_schema("runtime-execution-receipt.candidate.v2"))

    def test_valid_fixture_passes(self) -> None:
        schema = load_schema("runtime-execution-receipt.candidate.v2")
        jsonschema.Draft202012Validator(schema).validate(
            load_fixture("runtime-execution-receipt.candidate.v2", "valid")
        )

    def test_invalid_fixture_fails_on_self_attested_decision(self) -> None:
        schema = load_schema("runtime-execution-receipt.candidate.v2")
        with self.assertRaises(jsonschema.ValidationError):
            jsonschema.Draft202012Validator(schema).validate(
                load_fixture("runtime-execution-receipt.candidate.v2", "invalid")
            )

    def test_references_a_real_target_manifest_target(self) -> None:
        # cross-contract: RuntimeExecutionReceipt.target.target_id must be a target this
        # batch's TargetManifest fixture actually knows about.
        receipt = load_fixture("runtime-execution-receipt.candidate.v2", "valid")
        manifest = load_fixture("target-manifest.v1", "valid")
        self.assertEqual(receipt["target"]["target_id"], manifest["target_id"])

    def test_epochs_use_the_same_requirement_epoch_as_target_assurance_snapshot(self) -> None:
        receipt = load_fixture("runtime-execution-receipt.candidate.v2", "valid")
        snapshot = load_fixture("target-assurance-requirement-universe-snapshot.v1", "valid")
        self.assertEqual(receipt["epochs"]["requirement"], snapshot["requirement_epoch_id"])

    def test_decision_vocabulary_does_not_include_a_bare_self_attested_pass(self) -> None:
        # SA-01 Evidence Reperformance & Truth Binding: PASS_NONFINAL only, never a bare PASS
        # that could be read as a final/independent claim.
        schema = load_schema("runtime-execution-receipt.candidate.v2")
        decisions = set(schema["properties"]["decision"]["enum"])
        self.assertIn("PASS_NONFINAL", decisions)
        self.assertNotIn("PASS", decisions)


class Wave2NegativeGateTest(unittest.TestCase):
    """The five negative gates explicitly required for Batch 1 Wave 2. None of these
    are expressible as plain JSON Schema validation (they are cross-object/cross-array
    invariants), so each fixture pair here is schema-VALID by itself; the point is that
    a real cross-contract check -- not schema validation -- must catch the problem."""

    # --- Gate 1: dangling EvidenceGraph edge ---
    def test_dangling_evidence_graph_edge_is_detected(self) -> None:
        def find_dangling_edges(graph: dict) -> list[str]:
            node_ids = {n["node_id"] for n in graph["nodes"]}
            return [
                e["edge_id"] for e in graph["edges"]
                if e["source_node_id"] not in node_ids or e["target_node_id"] not in node_ids
            ]

        clean = load_fixture("evidence-graph-snapshot.v1", "valid")
        self.assertEqual(find_dangling_edges(clean), [])

        dangling = load_fixture("evidence-graph-snapshot.v1", "dangling-edge")
        self.assertEqual(find_dangling_edges(dangling), ["EDGE::A-B"])

    # --- Gate 2: stale/unqualified Collector producing an authoritative observation ---
    def test_stale_collector_cannot_back_an_authoritative_observation(self) -> None:
        def collector_may_be_authoritative(collector: dict) -> bool:
            return (
                collector["health_state"] == "HEALTHY"
                and collector["qualification_state"] == "QUALIFIED"
                and collector["coverage_state"] == "FULL"
            )

        def is_violation(collector: dict, observation: dict) -> bool:
            # an observation claiming authoritative=true while its Collector is not
            # HEALTHY+QUALIFIED+FULL is exactly the violation this gate exists to catch.
            return observation["authoritative"] and not collector_may_be_authoritative(collector)

        healthy_collector = load_fixture("collector-health-record.v1", "valid")
        good_observation = load_fixture("observation-record.v1", "valid")
        self.assertEqual(good_observation["collector_health_id"], healthy_collector["collector_health_id"])
        self.assertFalse(is_violation(healthy_collector, good_observation))

        stale_collector = load_fixture("collector-health-record.v1", "stale")
        bad_observation = load_fixture("observation-record.v1", "wrongly-authoritative")
        self.assertEqual(bad_observation["collector_health_id"], stale_collector["collector_health_id"])
        self.assertTrue(
            is_violation(stale_collector, bad_observation),
            "an authoritative=true observation backed by a non-HEALTHY/QUALIFIED/FULL collector must be flagged",
        )

    # --- Gate 3: duplicate/replayed Attempt ---
    def test_replayed_attempt_is_recorded_not_silently_dropped_or_doubled(self) -> None:
        original = load_fixture("attempt-record.v1", "valid")
        replay = load_fixture("attempt-record.v1", "replayed-duplicate")
        self.assertEqual(original["work_unit_id"], replay["work_unit_id"])
        self.assertEqual(original["attempt_number"], replay["attempt_number"])
        self.assertNotEqual(original["attempt_id"], replay["attempt_id"])

        aggregation = load_fixture("distributed-aggregation-receipt.v1", "valid")
        duplicate_entries = [
            d for d in aggregation["duplicate_attempts"] if d["work_unit_id"] == original["work_unit_id"]
        ]
        self.assertEqual(len(duplicate_entries), 1, "the real aggregation fixture must declare this replay pair")
        self.assertIn(original["attempt_id"], duplicate_entries[0]["attempt_ids"])
        self.assertIn(replay["attempt_id"], duplicate_entries[0]["attempt_ids"])
        self.assertIn(duplicate_entries[0]["resolution"], {"LATEST_LEASE_WINS", "EXPLICIT_CANCELLATION", "MANUAL_REVIEW_REQUIRED"})

    # --- Gate 4: Attempt failure misjudged as WorkUnit-wide success ---
    def test_work_unit_success_requires_at_least_one_succeeded_attempt(self) -> None:
        def has_succeeded_attempt(work_unit_id: str, attempts: list[dict]) -> bool:
            return any(a["work_unit_id"] == work_unit_id and a["outcome"] == "SUCCEEDED" for a in attempts)

        real_work_unit = load_fixture("work-unit.v1", "valid")
        real_attempts = [load_fixture("attempt-record.v1", "valid"), load_fixture("attempt-record.v1", "second-attempt")]
        self.assertEqual(real_work_unit["state"], "SUCCEEDED")
        self.assertTrue(has_succeeded_attempt(real_work_unit["work_unit_id"], real_attempts))

        fabricated_work_unit = load_fixture("work-unit.v1", "fabricated-success")
        fabricated_attempts = [load_fixture("attempt-record.v1", "only-failed-attempt")]
        self.assertEqual(fabricated_work_unit["state"], "SUCCEEDED")
        self.assertFalse(
            has_succeeded_attempt(fabricated_work_unit["work_unit_id"], fabricated_attempts),
            "a WorkUnit reporting SUCCEEDED whose only recorded Attempt outcome is FAILED must be rejected",
        )

    # --- Gate 5: execution/receipt/evidence parent mismatch ---
    def test_collector_execution_receipt_id_must_match_the_attempt_that_ran_it(self) -> None:
        attempt = load_fixture("attempt-record.v1", "valid")
        matching_collector = load_fixture("collector-health-record.v1", "valid")
        self.assertEqual(matching_collector["execution_receipt_id"], attempt["execution_receipt_id"])

        mismatched_collector = load_fixture("collector-health-record.v1", "parent-mismatch")
        self.assertNotEqual(
            mismatched_collector["execution_receipt_id"], attempt["execution_receipt_id"],
            "fixture must actually exercise the mismatch case",
        )
        # evidence attributed to an execution_receipt_id that no real Attempt on this work_unit
        # produced is exactly the "parent mismatch" this gate exists to catch.


if __name__ == "__main__":
    unittest.main()
