#!/usr/bin/env python3
"""Autonomous Development Mode standing-policy execution queue item 2: "orphan severity
recalculation -- promote any security/safety/privacy/authority/evidence-integrity issue to P0
when its actual claim impact requires it."

scan-global-trace-closure.py's own docstring is explicit that its mechanical P0 heuristic
(claim_effect=POSITIVE_CLAIM_GATE + criticality=CRITICAL + a specific orphan dimension) "MUST be
treated as a starting point for human/Design-Change-Queue review, not as a final severity
determination" (90_GLOBAL_TRACE_CLOSURE_SCANNER_DESIGN.md SS6). This script performs that review
mechanically wherever the review criteria themselves are mechanically checkable, and reports the
result as a SEPARATE, clearly-labeled recalculation -- it does not rewrite scan-global-trace-
closure.py's own P0/P1 output, which many other gates (Global Lock preflight's
P0_ORPHAN_ZERO_OR_AUTH_EXTERNAL_BLOCKER, tests/test_requirement_universe.py) already depend on
under its existing, narrower definition. Silently redefining that shared meaning would be a bigger,
riskier change than this recalculation pass is meant to be.

Recalculation criteria (additive to the existing mechanical P0 set, documented so a human/DCQ
reviewer can accept, reject, or refine each one -- this script does not claim final authority):
  R1: criticality=CRITICAL and claim_effect=QUALIFICATION_GATE (not just POSITIVE_CLAIM_GATE) and
      the requirement is missing a contract, test, or evidence path. A QUALIFICATION_GATE
      requirement gates whether something may be treated as qualified at all -- structurally at
      least as consequential as a POSITIVE_CLAIM_GATE requirement, so excluding it from escalation
      was never a considered design decision, just narrower wording in the original heuristic.
  R2: taxonomy in (SECURITY, PRIVACY, REGULATORY) and criticality in (CRITICAL, HIGH) and the
      requirement has zero test_refs AND zero evidence_refs (not merely one or the other) --
      a security/privacy/regulatory requirement with NO real verification path at all.
"""
from __future__ import annotations

import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def load(path: str) -> dict:
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def main() -> int:
    subprocess.run([sys.executable, str(ROOT / "scripts" / "scan-global-trace-closure.py")],
                    cwd=ROOT, check=True, capture_output=True)
    trace_report = load(".onsure/requirement-universe/global-trace-scan-report.json")
    records = load(".onsure/requirement-universe/requirement-records.json")
    records_by_id = {r["requirement_id"]: r for r in records}
    rows_by_id = {row["requirement_id"]: row for row in trace_report["rows"]}

    mechanical_p0 = set(trace_report["orphans"]["p0"])
    mechanical_p1 = set(trace_report["orphans"]["p1"])

    candidates: list[dict] = []
    for rid in sorted(mechanical_p1):
        record = records_by_id.get(rid)
        row = rows_by_id.get(rid)
        if record is None or row is None or not row["orphan_dimensions"]:
            continue
        criticality = record.get("criticality")
        claim_effect = record.get("claim_effect")
        taxonomy = record.get("taxonomy")
        dims = set(row["orphan_dimensions"])

        matched_rules = []
        if criticality == "CRITICAL" and claim_effect == "QUALIFICATION_GATE" and dims:
            matched_rules.append("R1_CRITICAL_QUALIFICATION_GATE_ORPHAN")
        if (taxonomy in ("SECURITY", "PRIVACY", "REGULATORY") and criticality in ("CRITICAL", "HIGH")
                and not row["test_refs"] and not row["evidence_refs"]):
            matched_rules.append("R2_SECURITY_PRIVACY_REGULATORY_NO_VERIFICATION_PATH_AT_ALL")

        if matched_rules:
            candidates.append({
                "requirement_id": rid,
                "criticality": criticality,
                "claim_effect": claim_effect,
                "taxonomy": taxonomy,
                "orphan_dimensions": sorted(dims),
                "matched_rules": matched_rules,
                "normative_text_excerpt": (record.get("normative_text") or "")[:200],
            })

    report = {
        "contract": "ONSURE_ORPHAN_SEVERITY_RECALCULATION_REPORT_V1",
        "authority_ref": "90_GLOBAL_TRACE_CLOSURE_SCANNER_DESIGN.md SS6 (explicitly requires human/DCQ review of the mechanical heuristic); Autonomous Development Mode standing policy execution queue item 2",
        "mechanical_p0_count": len(mechanical_p0),
        "mechanical_p1_count": len(mechanical_p1),
        "recalculation_candidate_count": len(candidates),
        "recalculation_candidates": candidates,
        "disposition": (
            "REVIEW_REQUIRED -- this report identifies candidates for P0 escalation under "
            "documented, disclosed criteria (R1, R2). It does NOT itself change any "
            "requirement's severity in scan-global-trace-closure.py's own output, "
            "contracts/claude-development-progress-registry.v1.json, or any other gate. "
            "Treat recalculation_candidates as a prioritized worklist for closure, not as a "
            "new authoritative P0 set."
        ),
        "final_claim_allowed": False,
    }
    print(json.dumps(report, indent=2, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
