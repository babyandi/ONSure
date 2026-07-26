import json
from collections import Counter, defaultdict
from pathlib import Path

BANK = Path(__file__).resolve().parents[1] / "fixtures/learning/odesign-oui/expert-task-bank.v1.json"
REQUIRED = {
    "task_id", "track", "kind", "level", "unseen", "competency",
    "materialization_state", "requirements", "constraints", "conflicts",
    "required_states", "source_receipts", "expected_evidence", "oracle",
    "adversarial_mutations", "rollback_target", "promotion_effect",
}

def load():
    return json.loads(BANK.read_text(encoding="utf-8"))

def test_exact_200_track_specific_slots():
    value = load()
    tasks = value["tasks"]
    assert len(tasks) == 200
    assert len({task["task_id"] for task in tasks}) == 200
    per_track = Counter(task["track"] for task in tasks)
    assert per_track == {
        "UI_UX": 50, "DESIGN_SYSTEMS": 50,
        "PRESENTATION_DOCUMENT": 50, "AI_DESIGN": 50,
    }

def test_each_track_has_30_standard_10_adversarial_10_blind():
    grouped = defaultdict(Counter)
    for task in load()["tasks"]:
        grouped[task["track"]][task["kind"]] += 1
    for counts in grouped.values():
        assert counts == {"STANDARD": 30, "ADVERSARIAL": 10, "BLIND": 10}

def test_materialized_tasks_are_contract_complete_and_receipt_bound():
    tasks = [task for task in load()["tasks"] if task["kind"] != "BLIND"]
    assert len(tasks) == 160
    for task in tasks:
        assert REQUIRED <= set(task)
        assert task["materialization_state"] == "MATERIALIZED"
        assert task["requirements"] and task["constraints"] and task["oracle"]
        assert "runtime_effect_receipt" in task["expected_evidence"]
        assert task["promotion_effect"] == "NONE_CANDIDATE_ONLY"

def test_blind_prompts_are_not_leaked_into_learner_repository():
    tasks = [task for task in load()["tasks"] if task["kind"] == "BLIND"]
    assert len(tasks) == 40
    for task in tasks:
        assert task["unseen"] is True
        assert task["materialization_state"] == "SEALED_PENDING_INDEPENDENT_EVALUATOR"
        assert task["blind_commitment"]["learner_access"] == "FORBIDDEN"
        assert task["blind_commitment"]["custody"] == "OTester_and_OAudit"
        assert task["blind_commitment"]["prompt_sha256"] == "PENDING_INDEPENDENT_EVALUATOR_SEAL"

def test_bank_does_not_claim_expert_or_runtime_completion():
    value = load()
    assert value["disposition"] == "HOLD"
    assert value["summary"]["expert_verified"] == 0
    assert value["rules"]["runtime_application_forbidden_in_onsure"] is True
    assert all(task["promotion_effect"] == "NONE_CANDIDATE_ONLY"
               for task in value["tasks"])
