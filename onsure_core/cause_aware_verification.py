"""Cause-aware verification and remediation memory for ONSure.

This module is intentionally product-neutral. A target program can be ORUDA,
another AI agent, a RAG service, or any LLM-based workflow as long as it
provides a profile with ordered stages, required fields, final gates, and
runtime routes.
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from typing import Any, Mapping


DECISION_ALLOW = "ALLOW"
DECISION_BLOCK = "BLOCK"


def canonical(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def digest(value: Any) -> str:
    return hashlib.sha256(canonical(value).encode("utf-8")).hexdigest()


def is_hash(value: Any) -> bool:
    return isinstance(value, str) and len(value) == 64 and all(c in "0123456789abcdef" for c in value)


@dataclass(frozen=True)
class CauseDefinition:
    code: str
    cause: str
    remediation: str
    memory_kind: str


CAUSES = {
    "PROGRAM_ROUTE_MISSING": CauseDefinition(
        code="PROGRAM_ROUTE_MISSING",
        cause="The program is registered, but its executable runtime, contract, or test route is missing or non-standard.",
        remediation="Add the missing route to the program profile and require it during intake before verification can pass.",
        memory_kind="program_learning",
    ),
    "STAGE_PARENT_HASH_MISSING": CauseDefinition(
        code="STAGE_PARENT_HASH_MISSING",
        cause="A workflow stage can consume regenerated or fabricated input because its parent hash is missing or stale.",
        remediation="Bind each stage input to the previous stage output hash and reject drift before execution.",
        memory_kind="behavior_learning",
    ),
    "STAGE_BODY_DRIFT": CauseDefinition(
        code="STAGE_BODY_DRIFT",
        cause="A stage body changed after its parent hash was recorded, allowing fixture replay or middle-output substitution.",
        remediation="Hash the exact canonical body consumed by the next stage and verify it at the boundary.",
        memory_kind="behavior_learning",
    ),
    "REQUIRED_OUTPUT_FIELD_MISSING": CauseDefinition(
        code="REQUIRED_OUTPUT_FIELD_MISSING",
        cause="The target program dropped fields that are required for downstream correctness or auditability.",
        remediation="Promote the missing field set into the program contract and bind it to downstream receipts.",
        memory_kind="failure_memory",
    ),
    "FORMAL_PROCEDURE_MISSING": CauseDefinition(
        code="FORMAL_PROCEDURE_MISSING",
        cause="The target program produced an output without the approved procedural receipts.",
        remediation="Require the configured procedure steps in order, with each step parent-bound to the previous receipt.",
        memory_kind="program_learning",
    ),
    "RENDER_OR_OUTPUT_BINDING_MISSING": CauseDefinition(
        code="RENDER_OR_OUTPUT_BINDING_MISSING",
        cause="The final output hash is not bound to the full canonical intermediate representation.",
        remediation="Hash the full canonical representation and required field manifest into the final output receipt.",
        memory_kind="failure_memory",
    ),
    "FINAL_GATE_NOT_PASS": CauseDefinition(
        code="FINAL_GATE_NOT_PASS",
        cause="The program can claim final completion while an independent verifier, audit gate, or required receipt is pending.",
        remediation="Block final completion unless every configured final gate is PASS and receipt-bound.",
        memory_kind="improvement_memory",
    ),
    "LOOP_RESULT_UNSTABLE": CauseDefinition(
        code="LOOP_RESULT_UNSTABLE",
        cause="Repeated verification loops produced different decisions, causes, remediation targets, or evidence hashes.",
        remediation="Stabilize ONSure verification projections before promoting the finding or changing a target program.",
        memory_kind="improvement_memory",
    ),
}


def _finding(code: str, program: str, details: str) -> dict[str, Any]:
    cause = CAUSES[code]
    return {
        "code": code,
        "program": program,
        "cause": cause.cause,
        "remediation": cause.remediation,
        "memory_kind": cause.memory_kind,
        "details": details,
    }


def verify_program_run(profile: Mapping[str, Any], run: Mapping[str, Any]) -> dict[str, Any]:
    """Verify one target-program run and return cause-aware findings."""
    findings: list[dict[str, Any]] = []
    program_id = str(profile.get("program_id", "UNKNOWN_PROGRAM"))

    routes = run.get("routes", {})
    for name, expected in profile.get("required_routes", {}).items():
        if routes.get(name) != expected:
            findings.append(_finding("PROGRAM_ROUTE_MISSING", program_id, f"{name}: expected {expected!r}, got {routes.get(name)!r}"))

    stages = profile.get("stages", [])
    outputs = run.get("stage_outputs", {})
    for index, stage in enumerate(stages):
        name = stage["name"]
        output = outputs.get(name)
        if not isinstance(output, Mapping):
            findings.append(_finding("STAGE_BODY_DRIFT", stage.get("program", program_id), f"{name}: stage output missing"))
            continue

        expected_parent = None
        if index > 0:
            previous_name = stages[index - 1]["name"]
            previous = outputs.get(previous_name, {})
            expected_parent = digest(previous)
        if expected_parent and output.get("parent_hash") != expected_parent:
            findings.append(_finding("STAGE_PARENT_HASH_MISSING", stage.get("program", program_id), f"{name}: parent hash mismatch"))

        body = output.get("body")
        if body is not None and output.get("body_hash") != digest(body):
            findings.append(_finding("STAGE_BODY_DRIFT", stage.get("program", program_id), f"{name}: body hash mismatch"))

        required_fields = profile.get("required_output_fields", {}).get(name, [])
        body_map = body if isinstance(body, Mapping) else output
        missing = [field for field in required_fields if field not in body_map]
        if missing:
            findings.append(_finding("REQUIRED_OUTPUT_FIELD_MISSING", stage.get("program", program_id), f"{name}: {','.join(missing)}"))

        expected_steps = profile.get("required_procedure_steps", {}).get(name)
        if expected_steps:
            actual_steps = [step.get("step") for step in output.get("procedure", {}).get("steps", [])]
            if actual_steps != list(expected_steps):
                findings.append(_finding("FORMAL_PROCEDURE_MISSING", stage.get("program", program_id), f"{name}: {actual_steps!r}"))

    final_output = run.get("final_output", {})
    binding = final_output.get("binding", {})
    if binding.get("canonical_run_hash") != digest(outputs):
        findings.append(_finding("RENDER_OR_OUTPUT_BINDING_MISSING", program_id, "final_output.binding.canonical_run_hash"))

    for gate in profile.get("final_gates", []):
        receipt = final_output.get("gates", {}).get(gate, {})
        if receipt.get("status") != "PASS" or not is_hash(receipt.get("receipt_hash")):
            findings.append(_finding("FINAL_GATE_NOT_PASS", program_id, gate))

    memory_candidates = [
        {
            "memory_kind": item["memory_kind"],
            "program": item["program"],
            "cause_code": item["code"],
            "cause": item["cause"],
            "remediation": item["remediation"],
            "evidence_hash": digest(item),
            "status": "CANDIDATE_NOT_PROMOTED",
        }
        for item in findings
    ]

    return {
        "program": "ONSure",
        "decision": DECISION_ALLOW if not findings else DECISION_BLOCK,
        "finding_count": len(findings),
        "findings": findings,
        "memory_candidates": memory_candidates,
        "remediation_targets": sorted({item["program"] for item in findings}),
    }


def result_projection(result: Mapping[str, Any]) -> dict[str, Any]:
    """Return the stable subset used to compare repeated verification loops."""
    return {
        "decision": result.get("decision"),
        "finding_codes": [item.get("code") for item in result.get("findings", [])],
        "finding_programs": [item.get("program") for item in result.get("findings", [])],
        "memory_kinds": [item.get("memory_kind") for item in result.get("memory_candidates", [])],
        "remediation_targets": result.get("remediation_targets", []),
    }


def verify_program_run_loop(profile: Mapping[str, Any], run: Mapping[str, Any], loops: int = 1) -> dict[str, Any]:
    """Run the same verification repeatedly and expose loop stability evidence."""
    if loops < 1:
        raise ValueError("loops must be >= 1")

    iterations = []
    projection_hashes = []
    for index in range(loops):
        result = verify_program_run(profile, run)
        projection = result_projection(result)
        projection_hash = digest(projection)
        projection_hashes.append(projection_hash)
        iterations.append(
            {
                "index": index + 1,
                "decision": result["decision"],
                "result_hash": digest(result),
                "projection_hash": projection_hash,
                "finding_codes": projection["finding_codes"],
                "remediation_targets": projection["remediation_targets"],
            }
        )

    stable = len(set(projection_hashes)) == 1
    final = verify_program_run(profile, run)
    final["loop"] = {
        "requested": loops,
        "stable": stable,
        "projection_hash": projection_hashes[0],
        "iterations": iterations,
    }

    if not stable:
        final["decision"] = DECISION_BLOCK
        final["findings"].append(_finding("LOOP_RESULT_UNSTABLE", "ONSure", f"projection hashes: {projection_hashes!r}"))
        final["finding_count"] = len(final["findings"])
        final["memory_candidates"].append(
            {
                "memory_kind": "improvement_memory",
                "program": "ONSure",
                "cause_code": "LOOP_RESULT_UNSTABLE",
                "cause": CAUSES["LOOP_RESULT_UNSTABLE"].cause,
                "remediation": CAUSES["LOOP_RESULT_UNSTABLE"].remediation,
                "evidence_hash": digest(final["loop"]),
                "status": "CANDIDATE_NOT_PROMOTED",
            }
        )
        final["remediation_targets"] = sorted(set(final["remediation_targets"]) | {"ONSure"})

    return final


def build_sample_oruda_report_profile() -> dict[str, Any]:
    """Example target profile for an ORUDA report-generation chain."""
    procedure_steps = [
        "page_intent",
        "information_priority",
        "concept_croquis",
        "layout_candidate_comparison",
        "detailed_croquis",
        "asset_selection",
        "geometry_contract",
        "visual_quality_gate",
    ]
    return {
        "program_id": "ORUDA_REPORT_CHAIN",
        "required_routes": {
            "oreport_runtime": "products/oreport/runtime",
            "oreport_contract": "products/oreport/contracts",
            "oreport_tests": "tests/oreport",
            "odesign_runtime": "products/odesign/runtime",
            "odesign_contract": "products/odesign/contracts",
            "odesign_tests": "tests/odesign",
            "oui_runtime": "products/oui/runtime",
            "oui_contract": "products/oui/contracts",
            "oui_tests": "tests/oui",
            "odocument_runtime": "products/odocument/runtime",
            "odocument_contract": "products/odocument/contracts",
            "odocument_tests": "tests/odocument",
        },
        "stages": [
            {"name": "raw", "program": "ODocument"},
            {"name": "claim", "program": "ODocument"},
            {"name": "page_spec", "program": "OReport"},
            {"name": "design", "program": "ODesign"},
            {"name": "scene", "program": "OUI"},
            {"name": "render", "program": "Canvas"},
        ],
        "required_output_fields": {
            "page_spec": ["intent", "information_priority", "geometry_contract"],
            "scene": ["objects", "field_manifest"],
            "render": ["render_hash", "binding"],
        },
        "required_procedure_steps": {"design": procedure_steps},
        "final_gates": ["otester", "oaudit"],
    }


def build_sample_run(*, omit_scene_manifest: bool = False, pending_gate: str | None = None) -> dict[str, Any]:
    routes = {
        "oreport_runtime": "products/oreport/runtime",
        "oreport_contract": "products/oreport/contracts",
        "oreport_tests": "tests/oreport",
        "odesign_runtime": "products/odesign/runtime",
        "odesign_contract": "products/odesign/contracts",
        "odesign_tests": "tests/odesign",
        "oui_runtime": "products/oui/runtime",
        "oui_contract": "products/oui/contracts",
        "oui_tests": "tests/oui",
        "odocument_runtime": "products/odocument/runtime",
        "odocument_contract": "products/odocument/contracts",
        "odocument_tests": "tests/odocument",
    }
    stages = {}
    previous = None
    bodies = {
        "raw": {"bytes_sha256": "1" * 64},
        "claim": {"text": "claim"},
        "page_spec": {"intent": "explain", "information_priority": ["a"], "geometry_contract": {"width": 1920, "height": 1080}},
        "design": {"visual_quality_gate": "PASS"},
        "scene": {"objects": [{"object_id": "title"}], "field_manifest": {"title": {"object_id": "title"}}},
        "render": {"render_hash": "2" * 64, "binding": {"field_manifest_hash": "3" * 64}},
    }
    if omit_scene_manifest:
        bodies["scene"].pop("field_manifest")
    procedure_steps = build_sample_oruda_report_profile()["required_procedure_steps"]["design"]
    for name, body in bodies.items():
        output = {"body": body, "body_hash": digest(body)}
        if previous is not None:
            output["parent_hash"] = digest(previous)
        if name == "design":
            output["procedure"] = {"steps": [{"step": step} for step in procedure_steps]}
        stages[name] = output
        previous = output
    final_gates = {
        "otester": {"status": "PASS", "receipt_hash": "4" * 64},
        "oaudit": {"status": "PASS", "receipt_hash": "5" * 64},
    }
    if pending_gate:
        final_gates[pending_gate]["status"] = "PENDING"
    return {
        "routes": routes,
        "stage_outputs": stages,
        "final_output": {
            "binding": {"canonical_run_hash": digest(stages)},
            "gates": final_gates,
        },
    }
