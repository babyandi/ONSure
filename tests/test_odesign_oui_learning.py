import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "learning", ROOT / "scripts/run_odesign_oui_learning.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def run():
    base = ROOT / "fixtures/learning/odesign-oui"
    return MODULE.build(
        base / "actual-cases.json",
        base / "ai-methods.json",
        base / "design-methods-and-ai-products.json")


def test_real_corpora_are_projected_to_both_programs_deterministically():
    first = run()
    second = run()
    assert first["run_sha256"] == second["run_sha256"]
    assert first["counts"]["ODesign"] > 0
    assert first["counts"]["OUI"] > 0
    assert len({a["atom_id"] for p in first["programs"].values() for a in p}) \
        == first["counts"]["total"]


def test_learning_is_real_candidate_materialization_but_active_promotion_is_blocked():
    result = run()
    assert result["promotion"]["project_memory_candidates_materialized"] is True
    assert result["promotion"]["active_learning_performed"] is False
    assert result["promotion"]["decision"] == "HOLD"
    assert "ORIGINAL_EXTERNAL_SOURCE_BYTES_NOT_MATERIALIZED" in \
        result["promotion"]["missing_gates"]
    assert "ODESIGN_TO_OUI_RUNTIME_SCENARIO_NOT_RUN" in \
        result["promotion"]["missing_gates"]


def test_atoms_preserve_target_boundaries_and_lineage():
    result = run()
    for target, atoms in result["programs"].items():
        assert atoms
        for atom in atoms:
            assert atom["target_program"] == target
            assert atom["memory_scope"] == "PROJECT_MEMORY"
            assert atom["state"] in {"EVIDENCE_BOUND", "SOURCE_REFERENCED"}
            assert atom["source_hashes"]
            assert atom["case_hashes"]
            assert atom["counterexamples"]


def test_candidates_enter_hash_chained_official_ledger_without_false_completion():
    result = run()
    ledger = result["official_candidate_ledger"]
    assert len(ledger["entries"]) == result["counts"]["total"]
    assert ledger["recognized_completion_state"] == "HOLD_VALIDATION_NOT_REQUESTED"
    previous = "0" * 64
    for sequence, entry in enumerate(ledger["entries"], start=1):
        assert entry["sequence"] == sequence
        assert entry["previous_entry_sha256"] == previous
        assert entry["payload"]["state"] in {
            "EVIDENCE_BOUND", "SOURCE_REFERENCED"}
        previous = entry["entry_sha256"]
    assert ledger["head_sha256"] == previous


def test_offline_retrieval_routes_program_specific_knowledge():
    result = run()
    evaluation = result["retrieval_evaluation"]
    assert evaluation["scope"] == "OFFLINE_RETRIEVAL_ONLY"
    assert evaluation["pass_count"] == evaluation["scenario_count"]
    for scenario in evaluation["results"]:
        assert scenario["decision"] == "PASS"
        assert scenario["hit_atom_ids"]
        assert all(atom_id.startswith(scenario["target_program"] + "-")
                   for atom_id in scenario["hit_atom_ids"])


def test_candidate_registry_rollback_is_byte_identical():
    result = run()
    rollback = result["rollback_reproduction"]
    assert rollback["scope"] == "CANDIDATE_REGISTRY_ONLY"
    assert rollback["decision"] == "PASS"
    assert rollback["restored_byte_identical"] is True
    assert "ROLLBACK_REPRODUCTION_NOT_RUN" not in result["promotion"]["missing_gates"]


def test_integrated_design_domains_and_product_philosophies_are_routed():
    result = run()
    domains = {
        target: {domain for atom in atoms for domain in atom["domains"]}
        for target, atoms in result["programs"].items()
    }
    assert "industrial_design" in domains["ODesign"]
    assert "advertising_design" in domains["ODesign"]
    assert "publication_design" in domains["ODesign"]
    assert "web_design" in domains["OUI"]
    assert "llm_design_product_philosophy" in domains["OUI"]
    research_atoms = [
        atom for atoms in result["programs"].values() for atom in atoms
        if atom["atom_id"].split("-", 1)[1].startswith("IDM-")
    ]
    assert research_atoms
    assert all(atom["state"] == "SOURCE_REFERENCED" for atom in research_atoms)
    assert all(atom["source_binding"]["url"].startswith("https://")
               for atom in research_atoms)
    assert all(atom["verified_by"] == [] for atom in research_atoms)
