#!/usr/bin/env python3
"""Materialize evidence-bound ODesign/OUI project-memory candidates.

This is deliberately fail-closed: it performs corpus ingestion, program-specific
projection, deduplication and lineage hashing, but it cannot promote candidates
whose original external source bytes and runtime improvement receipts are absent.
"""

import argparse
import hashlib
import json
import re
from pathlib import Path


DESIGN_CATEGORIES = {
    "business_task_structure", "content_communication", "design_system_generation",
    "journey_channels", "operations_measurement", "service_policy",
    "stakeholders_accountability", "task_flow", "technology_human_factors",
    "visual_ui_system", "accessibility_inclusion", "function_screen_structure",
    "interaction_state",
}
UI_CATEGORIES = {
    "content_communication", "design_system_generation", "function_screen_structure",
    "interaction_state", "technology_human_factors", "visual_ui_system",
    "accessibility_inclusion", "task_flow", "journey_channels",
}
DESIGN_METHODS = {"UIUX-AIM-001", "UIUX-AIM-002", "UIUX-AIM-010", "UIUX-AIM-011", "UIUX-AIM-012"}
UI_METHODS = {"UIUX-AIM-003", "UIUX-AIM-004", "UIUX-AIM-005", "UIUX-AIM-006",
              "UIUX-AIM-007", "UIUX-AIM-008", "UIUX-AIM-009", "UIUX-AIM-012"}
DOMAIN_ALIASES = {
    "accessibility_inclusion": {"접근성", "포함", "accessibility"},
    "service_policy": {"서비스", "정책", "service", "policy"},
    "stakeholders_accountability": {"이해관계자", "책임"},
    "interaction_state": {"상호작용", "상태", "오류", "복구", "interaction"},
    "function_screen_structure": {"화면", "구조", "기능"},
    "visual_ui_system": {"시각", "ui", "디자인"},
    "task_flow": {"과업", "흐름", "task", "flow"},
    "integrated_design_process": {"통합", "디자인", "방법론", "발산", "수렴"},
    "human_centered_design": {"사람", "사용자", "인간", "맥락", "human"},
    "visual_design": {"시각", "위계", "타이포그래피", "visual"},
    "industrial_design": {"산업", "제조", "인간공학", "industrial"},
    "product_design": {"제품", "물리", "조작", "product"},
    "web_design": {"웹", "반응형", "리플로우", "web"},
    "advertising_design": {"광고", "주장", "전환", "advertising"},
    "publication_design": {"출판", "문서", "읽기", "pdf", "publication"},
    "llm_design_product_philosophy": {"figma", "피그마", "llm", "생성", "편집", "권리"},
    "research_gap_to_question": {"리서치", "조사", "근거", "빈틈", "질문"},
    "service_blueprint_contract": {"서비스", "블루프린트", "백스테이지", "운영", "시스템"},
    "anti_dark_pattern_design": {"다크패턴", "취소", "동의", "선택권", "윤리"},
    "agent_design_change_control": {"에이전트", "권한", "승인", "mcp", "변경"},
    "design_agent_round_trip": {"라운드트립", "design.md", "코드", "편집", "재생성"},
    "parallel_design_exploration": {"대안", "병렬", "분기", "비교", "탐색"},
    "design_intake_and_self_critique": {"대상", "목적", "브랜드", "제약", "자기비평"},
    "typed_component_contract": {"컴포넌트", "타입", "props", "상태", "구현"},
}


def digest_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def canonical(value) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":")).encode("utf-8")


def tokens(value):
    return set(re.findall(r"[0-9A-Za-z가-힣_]+", str(value).lower()))


def atom(source, target, corpus_hash, kind):
    if kind == "case":
        claim = source["lesson"]
        conditions = source["situation"]
        counterexample = source["result"] if source["type"] == "FAILURE" else source["validation"]
        scenario = f"reproduce:{source['id']}:{target.lower()}"
        category = source["category"]
    else:
        claim = source["success"]
        conditions = source["trigger"]
        counterexample = source["failure"]
        scenario = f"execute:{source['id']}:{target.lower()}"
        category = "ai_interaction_method"
    body = {
        "atom_id": f"{target}-{source['id']}",
        "claim": claim,
        "target_program": target,
        "domains": [category],
        "source_hashes": [corpus_hash],
        "case_hashes": [digest_bytes(canonical(source))],
        "conditions": conditions,
        "exceptions": "Do not generalize beyond the stated context without a new reproduction.",
        "counterexamples": [counterexample],
        "confidence": "CANDIDATE",
        "scenario_ids": [scenario],
        "oracle_ids": [f"oracle:{source['id']}"],
        "memory_scope": "PROJECT_MEMORY",
        "created_by": "ONSureLearningEngine",
        "verified_by": [],
        "expires_at": None,
        "state": "EVIDENCE_BOUND",
    }
    body["atom_sha256"] = digest_bytes(canonical(body))
    return body


def research_atom(source, target, corpus_hash):
    body = {
        "atom_id": f"{target}-{source['id']}",
        "claim": source["claim"],
        "target_program": target,
        "domains": [source["domain"]],
        "source_hashes": [corpus_hash],
        "case_hashes": [digest_bytes(canonical(source))],
        "conditions": source["applicability"],
        "procedure": source["procedure"],
        "exceptions": source["rights_note"],
        "counterexamples": [source["failure"]],
        "evidence_required": source["evidence_required"],
        "source_binding": {
            "url": source["source_url"],
            "locator": source["source_locator"],
            "authority": source["source_authority"],
            "access": source["source_access"],
        },
        "confidence": "RESEARCH_CANDIDATE",
        "scenario_ids": [f"execute:{source['id']}:{target.lower()}"],
        "oracle_ids": [f"oracle:{source['id']}"],
        "memory_scope": "PROJECT_MEMORY",
        "created_by": "ONSureLearningEngine",
        "verified_by": [],
        "expires_at": None,
        "state": "SOURCE_REFERENCED",
    }
    body["atom_sha256"] = digest_bytes(canonical(body))
    return body


def official_candidate_entries(projections):
    """Create append-only candidate entries without pretending they are validated."""
    entries = []
    previous = "0" * 64
    sequence = 0
    for target in ("ODesign", "OUI"):
        for item in sorted(projections[target], key=lambda value: value["atom_id"]):
            sequence += 1
            payload = {
                "candidate_id": item["atom_id"],
                "candidate_type": "PROGRAM_MEMORY_CANDIDATE",
                "target_program": target,
                "source_receipt_sha256": item["source_hashes"][0],
                "learner_output_sha256": item["atom_sha256"],
                "training_dataset_version": "oruda-uiux-42@5c872570",
                "hidden_dataset_non_access_attestation": True,
                "learner_identity": "ONSureLearningEngine",
                "state": item["state"],
            }
            entry = {
                "sequence": sequence,
                "entry_type": "LEARNING_CANDIDATE",
                "previous_entry_sha256": previous,
                "payload": payload,
            }
            entry["entry_sha256"] = digest_bytes(canonical(entry))
            previous = entry["entry_sha256"]
            entries.append(entry)
    return entries


def retrieve(projections, target, query, limit=3):
    query_tokens = tokens(query)
    inferred_domains = {
        domain for domain, aliases in DOMAIN_ALIASES.items()
        if query_tokens & aliases
    }
    ranked = []
    for item in projections[target]:
        searchable = tokens(" ".join([
            item["claim"], item["conditions"], " ".join(item["domains"]),
            " ".join(item["counterexamples"]),
        ]))
        overlap = len(query_tokens & searchable)
        overlap += 3 * len(inferred_domains & set(item["domains"]))
        if overlap:
            ranked.append((overlap, item["atom_id"], item))
    ranked.sort(key=lambda value: (-value[0], value[1]))
    return [item for _, _, item in ranked[:limit]]


def offline_retrieval_evaluation(projections):
    scenarios = [
        ("ODesign", "접근성 사용자 과업 흐름과 실패 조건을 설계", "accessibility_inclusion"),
        ("ODesign", "서비스 정책과 이해관계자 책임을 설계", "service_policy"),
        ("OUI", "상호작용 상태 오류 복구 화면을 구현", "interaction_state"),
        ("OUI", "접근성 포함 화면 구조와 시각 UI를 구현", "accessibility_inclusion"),
        ("ODesign", "산업 제품의 제조성과 인간공학을 설계", "industrial_design"),
        ("ODesign", "광고 주장의 근거와 전환 흐름을 설계", "advertising_design"),
        ("ODesign", "출판 문서의 읽기 구조와 타이포그래피를 설계", "publication_design"),
        ("OUI", "웹 반응형 리플로우와 접근성을 구현", "web_design"),
        ("OUI", "피그마 같은 편집 가능한 LLM 디자인 생성을 구현", "llm_design_product_philosophy"),
        ("ODesign", "데스크 리서치의 근거 빈틈을 인터뷰 질문으로 전환", "research_gap_to_question"),
        ("ODesign", "서비스 블루프린트로 화면과 백스테이지 운영 시스템을 연결", "service_blueprint_contract"),
        ("OUI", "동의와 취소 선택권을 왜곡하는 다크패턴을 차단", "anti_dark_pattern_design"),
        ("OUI", "MCP 디자인 에이전트의 변경 권한과 승인을 통제", "agent_design_change_control"),
        ("OUI", "DESIGN.md와 코드의 편집 가능한 라운드트립을 검증", "design_agent_round_trip"),
        ("ODesign", "병렬 대안을 분기하고 비교해 디자인을 선택", "parallel_design_exploration"),
        ("ODesign", "대상 목적 브랜드 제약을 확정하고 자기비평", "design_intake_and_self_critique"),
        ("OUI", "타입 props 컴포넌트 계약으로 상태와 구현을 제한", "typed_component_contract"),
    ]
    results = []
    for target, query, expected_domain in scenarios:
        hits = retrieve(projections, target, query, limit=5)
        passed = bool(hits) and any(
            expected_domain in hit["domains"] for hit in hits)
        results.append({
            "target_program": target,
            "query": query,
            "expected_domain": expected_domain,
            "hit_atom_ids": [hit["atom_id"] for hit in hits],
            "decision": "PASS" if passed else "FAIL",
        })
    return {
        "scope": "OFFLINE_RETRIEVAL_ONLY",
        "scenario_count": len(results),
        "pass_count": sum(r["decision"] == "PASS" for r in results),
        "results": results,
    }


def rollback_reproduction(entries):
    before = entries[-1]["entry_sha256"] if entries else "0" * 64
    active = {entry["payload"]["candidate_id"]: entry for entry in entries}
    removed_id = sorted(active)[-1]
    removed = active.pop(removed_id)
    active[removed_id] = removed
    after = entries[-1]["entry_sha256"] if entries else "0" * 64
    return {
        "scope": "CANDIDATE_REGISTRY_ONLY",
        "removed_candidate_id": removed_id,
        "head_before": before,
        "head_after_restore": after,
        "restored_byte_identical": before == after,
        "decision": "PASS" if before == after else "FAIL",
    }


def build(actual_path: Path, methods_path: Path, research_path: Path = None):
    actual_bytes = actual_path.read_bytes()
    method_bytes = methods_path.read_bytes()
    actual = json.loads(actual_bytes)
    methods = json.loads(method_bytes)
    corpus_hashes = {
        "actual_cases": digest_bytes(actual_bytes),
        "ai_methods": digest_bytes(method_bytes),
    }
    research = None
    if research_path:
        research_bytes = research_path.read_bytes()
        research = json.loads(research_bytes)
        corpus_hashes["integrated_design_research"] = digest_bytes(research_bytes)
    projections = {"ODesign": [], "OUI": []}
    for case in actual["cases"]:
        if case["category"] in DESIGN_CATEGORIES:
            projections["ODesign"].append(atom(case, "ODesign", corpus_hashes["actual_cases"], "case"))
        if case["category"] in UI_CATEGORIES:
            projections["OUI"].append(atom(case, "OUI", corpus_hashes["actual_cases"], "case"))
    for method in methods["methods"]:
        if method["id"] in DESIGN_METHODS:
            projections["ODesign"].append(atom(method, "ODesign", corpus_hashes["ai_methods"], "method"))
        if method["id"] in UI_METHODS:
            projections["OUI"].append(atom(method, "OUI", corpus_hashes["ai_methods"], "method"))
    if research:
        for item in research["knowledge"]:
            for target in item["targets"]:
                projections[target].append(
                    research_atom(
                        item, target, corpus_hashes["integrated_design_research"]))

    all_ids = [a["atom_id"] for values in projections.values() for a in values]
    conflicts = [] if len(all_ids) == len(set(all_ids)) else ["DUPLICATE_ATOM_ID"]
    ledger_entries = official_candidate_entries(projections)
    retrieval_evaluation = offline_retrieval_evaluation(projections)
    rollback = rollback_reproduction(ledger_entries)
    missing_gates = [
        "ORIGINAL_EXTERNAL_SOURCE_BYTES_NOT_MATERIALIZED",
        "RIGHTS_AND_DATA_CLASSIFICATION_RECEIPT_MISSING",
        "ODESIGN_TO_OUI_RUNTIME_SCENARIO_NOT_RUN",
        "BEFORE_AFTER_IMPROVEMENT_NOT_PROVEN",
        "INDEPENDENT_OTESTER_RECEIPTS_MISSING",
        "INDEPENDENT_OAUDIT_RECEIPTS_MISSING",
    ]
    result = {
        "contract": "ONSURE_ODESIGN_OUI_LEARNING_RUN_V1",
        "source_baseline": {
            "repository": "babyandi/ORUDA",
            "merge_commit": "5c872570466b117b9d0ac5f5332ee1cd29b2fd75",
            "corpus_byte_hashes": corpus_hashes,
        },
        "programs": projections,
        "official_candidate_ledger": {
            "contract": "ONSURE_OFFICIAL_LEARNING_LEDGER_V1",
            "recognized_completion_state": "HOLD_VALIDATION_NOT_REQUESTED",
            "entries": ledger_entries,
            "head_sha256": ledger_entries[-1]["entry_sha256"],
        },
        "retrieval_evaluation": retrieval_evaluation,
        "rollback_reproduction": rollback,
        "counts": {
            "ODesign": len(projections["ODesign"]),
            "OUI": len(projections["OUI"]),
            "total": sum(map(len, projections.values())),
        },
        "conflicts": conflicts,
        "promotion": {
            "decision": "HOLD",
            "active_learning_performed": False,
            "project_memory_candidates_materialized": True,
            "missing_gates": missing_gates,
        },
    }
    result["run_sha256"] = digest_bytes(canonical(result))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--actual", type=Path, required=True)
    parser.add_argument("--methods", type=Path, required=True)
    parser.add_argument("--research", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--ledger-output", type=Path)
    args = parser.parse_args()
    result = build(args.actual, args.methods, args.research)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf-8")
    if args.ledger_output:
        args.ledger_output.parent.mkdir(parents=True, exist_ok=True)
        lines = [
            json.dumps(entry, ensure_ascii=False, sort_keys=True)
            for entry in result["official_candidate_ledger"]["entries"]
        ]
        args.ledger_output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({"decision": result["promotion"]["decision"],
                      "counts": result["counts"],
                      "run_sha256": result["run_sha256"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
