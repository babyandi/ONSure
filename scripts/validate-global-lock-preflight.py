#!/usr/bin/env python3
"""Mechanically checks the 18 Global Design/Implementation Lock gates
(docs/master/semantic-assurance/159_REVERSE_ALIGNMENT_AND_GLOBAL_LOCK_GATE_PREPARATION.md,
contracts/global-design-implementation-lock-matrix.candidate.v1.json) against real repo state.

Never fabricates SATISFIED: a gate this script cannot mechanically verify is reported
NEEDS_MANUAL_REVIEW with the reason, not silently passed. This script is itself
NON_FINAL evidence -- doc 159 SS11.18 requires an independent QA rerun after Claude
reports Batch 9 complete, which this script cannot substitute for (POST_BATCH9_INDEPENDENT_RERUN
is always reported EXTERNAL_DEPENDENCY, never SATISFIED, regardless of any other gate state).
"""
from __future__ import annotations

import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def load(path: str) -> dict:
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def run(cmd: list[str]) -> tuple[int, str]:
    result = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, check=False)
    return result.returncode, result.stdout + result.stderr


def check_p0_orphan_zero() -> dict:
    code, out = run([sys.executable, "scripts/scan-global-trace-closure.py"])
    if code != 0:
        return {"status": "NOT_SATISFIED", "note": f"scanner exited {code}: {out[:400]}"}
    report = load(".onsure/requirement-universe/global-trace-scan-report.json")
    p0 = report["orphans"]["p0"]
    status = "SATISFIED" if not p0 else "NOT_SATISFIED"
    return {
        "status": status,
        "note": f"mechanical scanner (explicit-ID FR-COM/FR-META/FR-FRESH/FR-LEARN population, "
                f"{len(report['rows'])} rows): p0_orphans={p0}. "
                "This scanner's denominator is the explicit-ID population only -- doc 140's "
                "independent QA snapshot reports a much larger 899-record population (explicit + "
                "generated non-ID source-anchored records) with its own orphan count, not "
                "reconciled with this scanner. Treat this SATISFIED as scoped to explicit-ID "
                "requirements, not the full QA population.",
    }


def check_p0_dcq_zero() -> dict:
    dcq = load("contracts/design-change-queue.v1.json")
    open_p0 = [i["change_id"] for i in dcq["items"] if i["severity"] == "P0" and i["status"] == "OPEN"]
    return {
        "status": "SATISFIED" if not open_p0 else "NOT_SATISFIED",
        "note": f"open P0 DCQ items: {open_p0}. "
                f"open P1 items (non-blocking): "
                f"{[i['change_id'] for i in dcq['items'] if i['status'] == 'OPEN']}",
    }


def check_contract_registry_integrity() -> dict:
    code, out = run([sys.executable, "scripts/validate-structured-contracts.py", "--require-full"])
    try:
        start = out.index("{")
        report, _ = json.JSONDecoder().raw_decode(out[start:])
    except (ValueError, json.JSONDecodeError):
        report = {}
    satisfied = code == 0 and report.get("decision") == "PASS"
    return {
        "status": "SATISFIED" if satisfied else "NOT_SATISFIED",
        "note": f"validate-structured-contracts.py --require-full exit={code} "
                f"decision={report.get('decision')} errors={report.get('errors')}",
    }


def check_authority_population_frozen() -> dict:
    manifest = load("contracts/requirement-authority-source-manifest.candidate.v1.json")
    state = manifest.get("current_state")
    return {
        "status": "SATISFIED" if state not in (None, "ROW_POPULATION_PENDING") else "NOT_SATISFIED",
        "note": f"requirement-authority-source-manifest.candidate.v1.json current_state={state!r}",
    }


def check_exact_ru_digest() -> dict:
    snapshot = load("contracts/global-requirement-universe-snapshot.execution.candidate.v3.json")
    digest = snapshot.get("global_population_digest")
    count = snapshot.get("global_requirement_count")
    return {
        "status": "SATISFIED" if digest and count is not None else "NOT_SATISFIED",
        "note": f"global_requirement_count={count} global_population_digest={digest} "
                f"note={snapshot.get('note')}",
    }


def check_applicability_critical_unknown_zero() -> dict:
    applicability = load("contracts/applicability-population.execution.candidate.v3.json")
    zero = applicability.get("critical_unknown_zero")
    return {
        "status": "SATISFIED" if zero else "NOT_SATISFIED",
        "note": f"critical_unknown_zero={zero} counts={applicability.get('counts')}",
    }


def check_content_sha256_and_digests() -> dict:
    # Same underlying fact as EXACT_RU_DIGEST (both gated on the same null digest); kept as a
    # separate check because the lock matrix lists them as separate gates and a future
    # materialization could close one without the other (e.g. registry digests complete before
    # the RU population digest, or vice versa).
    snapshot = load("contracts/global-requirement-universe-snapshot.execution.candidate.v3.json")
    return {
        "status": "SATISFIED" if snapshot.get("global_population_digest") else "NOT_SATISFIED",
        "note": "global_population_digest is null pending full non-ID requirement materialization "
                "(unmaterialized_classes: " + str(snapshot.get("unmaterialized_classes")) + ")",
    }


def check_requirement_based_coverage_universe() -> dict:
    tcu = load("contracts/test-coverage-universe.candidate.v1.json")
    status = tcu.get("status")
    return {
        "status": "SATISFIED" if status == "MATERIALIZED" else "NOT_SATISFIED",
        "note": f"contracts/test-coverage-universe.candidate.v1.json self-reports status={status!r}, "
                f"known_priority_gaps={tcu.get('known_priority_gaps')} "
                "(FR-COM-008/NFR-CONFIG since closed by Batch 9 -- this candidate file's own status "
                "field predates that closure and has not been regenerated)",
    }


def check_no_nonpositive_to_positive_promotion() -> dict:
    code, out = run(["git", "grep", "-l", "-E", r'"final_claim_allowed"\s*:\s*true'])
    all_hits = [line for line in out.splitlines() if line.strip()]
    # *.invalid.json fixtures deliberately carry final_claim_allowed:true to prove the schema
    # validator REJECTS it -- those are intentional negative-test payloads, not real claims.
    violations = [hit for hit in all_hits if ".invalid.json" not in hit]
    return {
        "status": "SATISFIED" if not violations else "NOT_SATISFIED",
        "note": f"git grep for final_claim_allowed:true across the tracked tree, excluding "
                f"*.invalid.json negative fixtures (which intentionally carry it to prove "
                f"rejection): real violations={violations or 'none found'}. "
                f"excluded negative fixtures: {[h for h in all_hits if h not in violations]}",
    }


def check_forward_and_reverse_trace() -> dict:
    code, out = run([sys.executable, "scripts/scan-reverse-orphan.py"])
    if code != 0:
        return {"status": "NOT_SATISFIED", "note": f"scan-reverse-orphan.py exited {code}: {out[:400]}"}
    try:
        start = out.index("{")
        reverse_report, _ = json.JSONDecoder().raw_decode(out[start:])
    except (ValueError, json.JSONDecodeError):
        return {"status": "NOT_SATISFIED", "note": "scan-reverse-orphan.py output did not parse as JSON"}
    stale_total = sum(cat["cites_a_stale_nonexistent_id"] for cat in reverse_report["categories"].values())
    return {
        "status": "NOT_SATISFIED" if stale_total else "SELF_REPORTED_SEE_REGISTRY",
        "note": f"forward trace scanner exists and runs clean for the explicit-ID population "
                f"(scripts/scan-global-trace-closure.py, orphan_p0=0). Reverse-orphan scanner "
                f"exists (scripts/scan-reverse-orphan.py, disclosure-only, never gates a decision "
                f"on its own): {stale_total} file(s) cite a requirement id that does not exist in "
                f"the current Requirement Universe -- see status/reverse-orphan-scan-report.v1.json "
                f"for the real finding (FR-FIN-01~22, escalated to the user 2026-08-15, not yet "
                f"resolved). Not SATISFIED while a real dangling reference is open and unresolved.",
    }


def check_learning_p0_contradiction_progress() -> dict:
    registry = load("contracts/claude-development-progress-registry.v1.json")
    section = registry.get("learning_p0_contradiction_runtime_evidence", {})
    closed_this_pass = section.get("classes_closed_this_pass", [])
    already_covered = section.get("classes_already_covered_by_prior_batch_5_work", [])
    still_open = section.get("classes_still_open", [])
    # A closed_this_pass entry counts as fully closed only if it carries no partial_disclosure
    # note -- checking the dict's own fields, not string-matching the free-text "class" label,
    # since a label can omit "(PARTIAL)" while the entry's partial_disclosure field still
    # discloses a real gap (caught this exact undercount/overcount risk while building this
    # check: classes 8 and 9's entries have partial_disclosure set but no "(PARTIAL)" in their
    # "class" string, which the earlier string-matching version would have missed).
    fully_closed = sum(1 for c in closed_this_pass if not c.get("partial_disclosure"))
    partial_entries = sum(1 for c in closed_this_pass if c.get("partial_disclosure"))
    fully_closed_total = fully_closed + len(already_covered)
    return {
        "status": "SELF_REPORTED_SEE_REGISTRY",
        "note": f"doc 158's 11 P0 Learning contradiction CLASSES have design-policy bindings "
                f"(precedence rules, not open contradictions); runtime enforcement progress read "
                f"live from contracts/claude-development-progress-registry.v1.json."
                f"learning_p0_contradiction_runtime_evidence: {fully_closed_total} fully closed "
                f"({len(already_covered)} via prior-batch coverage + {fully_closed} this pass), "
                f"{partial_entries} entries with a disclosed partial gap, {len(still_open)} "
                f"still-open items listed. Not SATISFIED until all 11 are fully closed with real "
                f"runtime tests/evidence (doc 159 SS9's runtime_tests_run/evidence_receipts "
                f"requirement). No other live P0 semantic contradiction is tracked in this repo's "
                f"registries.",
    }


def check_post_batch9_independent_rerun() -> dict:
    return {
        "status": "EXTERNAL_DEPENDENCY",
        "note": "doc 159 SS11.18 requires an independent QA rerun after Claude reports Batch 9 "
                "complete. This is structurally something Claude's own preflight script cannot "
                "satisfy on its own behalf -- reported here for completeness, never SATISFIED.",
    }


def registry_backed_gate(name: str, note: str) -> dict:
    # Gates whose truth depends on semantic judgment across many files (executable safety/appeal
    # chains, migration reconciliation semantics, validator/oracle qualification currentness,
    # reverse-trace completeness, reconstructability reruns, corpus/leakage runtime evidence) are
    # not independently re-derived here -- doing so honestly requires the same depth of audit that
    # produced contracts/claude-development-progress-registry.v1.json in the first place. Reporting
    # them SATISFIED from that registry is disclosed as self-reported, not independently
    # re-verified by this script, per the same non-fabrication discipline as every other check.
    return {"status": "SELF_REPORTED_SEE_REGISTRY", "note": note}


GATES = {
    "AUTHORITY_POPULATION_FROZEN": check_authority_population_frozen,
    "EXACT_RU_DIGEST": check_exact_ru_digest,
    "APPLICABILITY_ONE_TO_ONE_CRITICAL_UNKNOWN_ZERO": check_applicability_critical_unknown_zero,
    "FORWARD_TRACE_AND_REVERSE_ORPHAN": check_forward_and_reverse_trace,
    "P0_ORPHAN_ZERO_OR_AUTH_EXTERNAL_BLOCKER": check_p0_orphan_zero,
    "P0_CONTRADICTION_ZERO": check_learning_p0_contradiction_progress,
    "P0_DCQ_ZERO": check_p0_dcq_zero,
    "CONTRACT_REGISTRY_REFERENTIAL_INTEGRITY": check_contract_registry_integrity,
    "REQUIREMENT_BASED_TEST_COVERAGE_UNIVERSE": check_requirement_based_coverage_universe,
    "REQUIRED_NEGATIVE_ADVERSARIAL_RECOVERY_EVIDENCE": lambda: registry_backed_gate(
        "REQUIRED_NEGATIVE_ADVERSARIAL_RECOVERY_EVIDENCE",
        "116 *.invalid.json vs 119 *.valid.json fixtures repository-wide (every schema added since "
        "Batch 5 has a paired negative fixture). Adversarial/recovery-specific classification "
        "against contracts/test-coverage-universe.candidate.v1.json's test_classes taxonomy has "
        "not been separately materialized.",
    ),
    "CONTENT_SHA256_AND_REGISTRY_DIGESTS": check_content_sha256_and_digests,
    "CLEAN_RECONSTRUCTABILITY": lambda: registry_backed_gate(
        "CLEAN_RECONSTRUCTABILITY",
        "Full mvn test + python unittest discover reruns clean on every batch this session "
        "(most recently: 626 Java tests / 186 Python tests, 0 failures). OfficialLearningLedger "
        "and ContractSelectorLedger have real hash-chain re-verification tests. 'minimum two "
        "independent clean reruns' as a formally recorded artifact has not been produced.",
    ),
    "VALIDATOR_ORACLE_COLLECTOR_QUALIFICATION_CURRENTNESS": lambda: registry_backed_gate(
        "VALIDATOR_ORACLE_COLLECTOR_QUALIFICATION_CURRENTNESS",
        "OracleQualification/validator-regression-qualification implemented with real staleness "
        "and disagreement checks (Batch 5 Slice 3), self-attestation structurally blocked "
        "(self-approval prevented by actor-identity checks throughout). Global "
        "'currentness proven' claim across every validator/oracle instance is not separately "
        "audited here.",
    ),
    "CORPUS_CONTAMINATION_AND_TRAIN_TEST_LEAKAGE": lambda: registry_backed_gate(
        "CORPUS_CONTAMINATION_AND_TRAIN_TEST_LEAKAGE",
        "corpusIntegrityCheck implemented and tested (Batch 5 Slice 3): contamination/poisoning/"
        "tenant-leakage/train-test-leakage all have real Java checks + positive/negative fixtures. "
        "Counted toward Batch 5's PARTIALLY_EVIDENCE_READY state, not independently re-verified here.",
    ),
    "SAFETY_APPEAL_EXECUTABLE_EVIDENCE": lambda: registry_backed_gate(
        "SAFETY_APPEAL_EXECUTABLE_EVIDENCE",
        "Batch 6 EVIDENCE_READY: HazardLedger/AppealLedger real FSM + actor-identity SoD + negative "
        "cases + tests (HazardLedgerTest 6, AppealLedgerTest 8, "
        "tests/test_safety_appeal_contracts.py 7).",
    ),
    "MIGRATION_INTEGRATION_RECONCILIATION": lambda: registry_backed_gate(
        "MIGRATION_INTEGRATION_RECONCILIATION",
        "Batch 8 EVIDENCE_READY: real reconciliation/cutover/rollback with fail-closed negative "
        "tests. Disclosed gap: live v1/v2 traffic shadowing and dual-read are not wired -- "
        "reconciliation operates on caller-supplied representations only.",
    ),
    "FINAL_IDENTITY_CURRENTNESS_SEPARATION": lambda: registry_backed_gate(
        "FINAL_IDENTITY_CURRENTNESS_SEPARATION",
        "FinalCandidate != FinalApproval != FinalLock != Current Production Assurance maintained "
        "throughout every batch this session; no batch built or promoted any Final/Production-GO/"
        "Commercial-GO object.",
    ),
    "NO_NONPOSITIVE_TO_POSITIVE_PROMOTION": check_no_nonpositive_to_positive_promotion,
    "POST_BATCH9_INDEPENDENT_RERUN": check_post_batch9_independent_rerun,
}


def main() -> int:
    results = {name: check() for name, check in GATES.items()}
    satisfied = sum(1 for r in results.values() if r["status"] == "SATISFIED")
    not_satisfied = sum(1 for r in results.values() if r["status"] == "NOT_SATISFIED")
    self_reported = sum(1 for r in results.values() if r["status"] == "SELF_REPORTED_SEE_REGISTRY")
    external = sum(1 for r in results.values() if r["status"] == "EXTERNAL_DEPENDENCY")
    report = {
        "contract": "ONSURE_GLOBAL_LOCK_PREFLIGHT_REPORT_V1",
        "authority_ref": "docs/master/semantic-assurance/159_REVERSE_ALIGNMENT_AND_GLOBAL_LOCK_GATE_PREPARATION.md",
        "lock_matrix_ref": "contracts/global-design-implementation-lock-matrix.candidate.v1.json",
        "gate_count": len(GATES),
        "mechanically_satisfied": satisfied,
        "mechanically_not_satisfied": not_satisfied,
        "self_reported_from_registry": self_reported,
        "external_dependency": external,
        "gates": results,
        "overall": "HOLD_NONFINAL",
        "final_claim_allowed": False,
        "note": "This is a preflight aid, not the Global Design/Implementation Lock itself. "
                "GLOBAL_DESIGN_IMPLEMENTATION_LOCK=HOLD/NON_FINAL until every gate is genuinely "
                "SATISFIED AND an independent QA rerun (POST_BATCH9_INDEPENDENT_RERUN) has occurred.",
    }
    print(json.dumps(report, indent=2, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
