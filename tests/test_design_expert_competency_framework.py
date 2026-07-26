import json
from pathlib import Path


FRAMEWORK = Path(__file__).resolve().parents[1] / "fixtures/learning/odesign-oui/expert-competency-framework.v1.json"


def load():
    return json.loads(FRAMEWORK.read_text(encoding="utf-8"))


def test_track_specific_fail_closed_promotion():
    value = load()
    policy = value["promotion_policy"]
    assert policy["unit_of_promotion"] == "TRACK"
    assert policy["no_global_expert_label"] is True
    assert policy["no_level_skipping"] is True
    assert policy["required_clean_rounds"] == 2
    assert policy["critical_or_high_findings_allowed"] == 0
    assert policy["pending_findings_allowed"] == 0
    assert policy["independent_reviewers"] == ["OTester", "OAudit"]


def test_four_priority_tracks_have_expert_task_minimums():
    value = load()
    tracks = value["tracks"]
    assert [item["id"] for item in tracks] == [
        "UI_UX", "DESIGN_SYSTEMS", "PRESENTATION_DOCUMENT", "AI_DESIGN"
    ]
    for item in tracks:
        assert item["targets"] == ["ODesign", "OUI"]
        assert item["task_bank"]["standard"] >= 30
        assert item["task_bank"]["adversarial"] >= 10
        assert item["task_bank"]["blind"] >= 10
        assert len(item["competencies"]) >= 6
        assert len(item["expert_oracles"]) >= 3


def test_runtime_lineage_and_effect_proof_are_mandatory():
    value = load()
    required = value["task_contract"]["required_chain"]
    assert required[0] == "source_and_user_request"
    assert "PageSpec_and_Croquis" in required
    assert "Canonical_Scene" in required
    assert "runtime_render_and_interaction" in required
    assert "effect_measurement" in required
    assert required[-1] == "independent_receipts"


def test_current_status_does_not_claim_expertise():
    value = load()["current_disposition"]
    assert value["official_level"] == "BEGINNER_NOT_YET_VERIFIED"
    assert value["expert_tracks"] == []
    assert value["decision"] == "HOLD"
    assert len(value["blocking_gates"]) >= 5
