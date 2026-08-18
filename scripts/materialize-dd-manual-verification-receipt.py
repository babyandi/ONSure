#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha256(path: Path) -> str | None:
    if not path.is_file():
        return None
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace") if path.is_file() else ""


def parse_surefire() -> dict:
    report_dir = ROOT / "target" / "surefire-reports"
    xmls = sorted(report_dir.glob("TEST-kr.co.oruda.onsure.platform.BuiltInDdSemanticEvaluatorsTest.xml"))
    if not xmls:
        return {"report_present": False, "tests": 0, "failures": 0, "errors": 0, "skipped": 0, "report_sha256": None}
    import xml.etree.ElementTree as ET
    root = ET.fromstring(xmls[-1].read_bytes())
    return {
        "report_present": True,
        "tests": int(root.attrib.get("tests", 0)),
        "failures": int(root.attrib.get("failures", 0)),
        "errors": int(root.attrib.get("errors", 0)),
        "skipped": int(root.attrib.get("skipped", 0)),
        "report_sha256": sha256(xmls[-1]),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-id", required=True)
    ap.add_argument("--source-tree-sha", required=True)
    ap.add_argument("--started-at", required=True)
    ap.add_argument("--static-rc", type=int, required=True)
    ap.add_argument("--qualification-status-rc", type=int, required=True)
    ap.add_argument("--maven-rc", type=int, required=True)
    ap.add_argument("--output", required=True)
    args = ap.parse_args()

    out = Path(args.output)
    prefix = out.parent / args.run_id
    surefire = parse_surefire()
    code_refs = [
        ROOT / "src/main/java/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluators.java",
        ROOT / "src/main/java/kr/co/oruda/onsure/platform/DdEvidenceResolver.java",
        ROOT / "src/main/java/kr/co/oruda/onsure/platform/DdSemanticEvaluator.java",
        ROOT / "src/main/java/kr/co/oruda/onsure/platform/DdSemanticEvaluatorRegistry.java",
        ROOT / "src/main/java/kr/co/oruda/onsure/platform/DdAssuranceOperationRuntime.java",
    ]
    all_ok = (
        args.static_rc == 0
        and args.qualification_status_rc == 0
        and args.maven_rc == 0
        and surefire["report_present"]
        and surefire["failures"] == 0
        and surefire["errors"] == 0
        and surefire["tests"] >= 6
    )
    receipt = {
        "contract": "ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V1",
        "run_id": args.run_id,
        "source_tree_sha": args.source_tree_sha,
        "started_at": args.started_at,
        "completed_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "execution_mode": "MANUAL_LOCAL_OR_APPROVED_EXECUTION_NODE_NO_GITHUB_ACTIONS",
        "steps": {
            "static_machine_definition": {"return_code": args.static_rc, "stdout_sha256": sha256(Path(str(prefix) + ".static.stdout")), "stderr_sha256": sha256(Path(str(prefix) + ".static.stderr"))},
            "qualification_status_validation": {"return_code": args.qualification_status_rc, "stdout_sha256": sha256(Path(str(prefix) + ".qualification_status.stdout")), "stderr_sha256": sha256(Path(str(prefix) + ".qualification_status.stderr"))},
            "maven_targeted_junit": {"return_code": args.maven_rc, "stdout_sha256": sha256(Path(str(prefix) + ".maven.stdout")), "stderr_sha256": sha256(Path(str(prefix) + ".maven.stderr")), "surefire": surefire},
        },
        "code_artifacts": [{"path": p.relative_to(ROOT).as_posix(), "sha256": sha256(p)} for p in code_refs],
        "claims": {
            "concrete_evaluator_code_materialized_count": 40,
            "compile_and_targeted_junit_established": all_ok,
            "fixture_mechanics_established": all_ok,
            "independent_evaluator_qualification_count": 0,
            "semantic_runtime_evidence_count": 0,
            "independent_clean": False,
            "design_lock": False,
        },
        "verdict": "PASS_NONFINAL_EXECUTION_MECHANICS_ONLY" if all_ok else "HOLD_NONFINAL",
        "github_actions_authority": False,
        "final_claim_allowed": False,
    }
    canonical = json.dumps(receipt, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    receipt["receipt_digest"] = hashlib.sha256(canonical).hexdigest()
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(receipt, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0 if all_ok else 2


if __name__ == "__main__":
    raise SystemExit(main())
