#!/usr/bin/env python3
"""RU-01..RU-06: materialize the Global Requirement Universe from docs/master.

Implements 88/89/91/92 (Batch 0 of 137_CLAUDE_DEVELOPMENT_MASTER_HANDOFF.md), REVISED
per 145_REQUIREMENT_AUTHORITY_POPULATION_CLOSURE_CHECKPOINT.md and the 2026-08-14 Batch 0
authority-change requalification:
- RU-01 authority document inventory is now the Requirement Authority Source Manifest's
  ELIGIBLE rows (NORMATIVE_CURRENT / NORMATIVE_REFINEMENT), not "every tracked markdown
  file". Run scripts/materialize-requirement-authority-manifest.py first.
- RU-02 explicit ID extractor (FR-COM-*, FR-META-*, FR-FRESH-*, FR-LEARN-*, NFR-*)
- RU-03 structured non-ID requirement extractor (CANDIDATE_EXTRACTED, not auto-promoted),
  now scanning only eligible authority sources.
- RU-04 semantic normalizer (normalized text digest, exact-duplicate grouping only)
- RU-05 rule resolution: canonical text for an explicit ID with multiple eligible
  occurrences is chosen by AUTHORITY (NORMATIVE_REFINEMENT overrides NORMATIVE_CURRENT),
  never by "longest text wins" (removed -- prohibited by
  requirement-authority-classification-policy.candidate.v1.json). Any remaining ambiguity
  (multiple disagreeing occurrences at the same authority tier) is a disclosed, deterministic
  tie-break (lexicographically-first source path) AND is recorded as an unresolved relation
  candidate -- it is never silently merged.
- RU-06 universe snapshot sealing, now epoch-tagged (denominator-epoch.v1.schema.json,
  epoch_type=REQUIREMENT). The prior all-markdown-scanned population is preserved as epoch 1
  (superseded, LEGACY_UNFILTERED_ALL_TRACKED_MARKDOWN_RECORD_POPULATION_CANDIDATE per 145 SS1),
  never deleted.

Ambiguous ID taxonomies (e.g. SA-*/XC-*) are intentionally excluded from EXPLICIT_ID
extraction and remain DCQ-0001 (P1, still OPEN). See design-change-queue.v1.json.
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
ALGORITHM_VERSION = "RU-GEN-2.0.0"
REQUIREMENT_EPOCH_SEQUENCE = 2
REQUIREMENT_EPOCH_ID = "EPOCH::REQUIREMENT::0002"
SUPERSEDED_REQUIREMENT_EPOCH_ID = "EPOCH::REQUIREMENT::0001"

EXPLICIT_ID_RE = re.compile(
    r"\b(FR-COM-\d+|FR-META-\d+|FR-FRESH-\d+|FR-LEARN-\d+|NFR-[A-Z]+(?:-\d+)?)\b"
)
NORMATIVE_RE = re.compile(
    r"(?:해야 합니다|해야 한다|하여야|금지|필수|수용\s*기준|완료\s*조건|MUST|SHALL|REQUIRED|PROHIBITED|MUST NOT)",
    re.IGNORECASE,
)

SOURCE_CLASS_KEYWORDS = [
    ("ACCEPTANCE_CRITERION", ("수용기준", "수용 기준", "완료조건", "acceptance criterion", "acceptance criteria")),
    ("INVARIANT", ("불변식", "invariant", "must not", "금지", "prohibited")),
    ("REGULATORY_REQUIRED", ("regulatory", "규제", "compliance", "owasp", "nist", "iso", "gdpr")),
    ("POLICY_REQUIRED", ("policy", "정책", "governance", "거버넌스")),
    ("CONTRACT_REQUIRED", ("contract", "계약", "schema")),
]

AUTHORITY_RANK = {"NORMATIVE_REFINEMENT": 2, "NORMATIVE_CURRENT": 1}


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def load_eligible_authority_sources() -> dict[str, str]:
    """Returns {artifact_path: requirement_source_disposition} for ELIGIBLE rows only,
    per requirement-authority-source-manifest.candidate.v1.json's denominator_contribution_rules."""
    manifest_path = ROOT / ".onsure" / "requirement-universe" / "requirement-authority-source-manifest.json"
    if not manifest_path.exists():
        raise RuntimeError(
            "AUTHORITY_MANIFEST_MISSING: run scripts/materialize-requirement-authority-manifest.py first"
        )
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    eligible = {}
    for row in manifest["rows"]:
        if row["requirement_source_disposition"] in AUTHORITY_RANK:
            eligible[row["artifact_path"]] = row["requirement_source_disposition"]
    if not eligible:
        raise RuntimeError("NO_ELIGIBLE_AUTHORITY_SOURCES")
    return eligible


def paragraph_after(lines: list[str], heading_line_number: int, heading: str, max_lines: int = 6) -> str:
    """Capture the body paragraph following a heading-line explicit-ID definition."""
    collected: list[str] = []
    for candidate in lines[heading_line_number: heading_line_number + max_lines]:
        candidate_stripped = candidate.strip()
        if candidate_stripped.startswith("#"):
            break
        if not candidate_stripped:
            if collected:
                break
            continue
        collected.append(candidate_stripped)
    body = " ".join(collected).strip()
    return f"{heading}: {body}" if body else heading


def normalized_text(text: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^0-9A-Za-z가-힣]+", " ", text)).strip().lower()


def deterministic_candidate_id(authority_document: str, heading: str, normalized: str) -> str:
    anchor = re.sub(r"[^0-9a-z]+", "-", heading.lower()).strip("-") or "root"
    key = sha256_bytes(normalized.encode())[:20]
    return f"REQ::{authority_document}::{anchor}::{key}"


def classify_source_class(path: str, text: str) -> str:
    value = (path + " " + text).lower()
    for source_class, keywords in SOURCE_CLASS_KEYWORDS:
        if any(keyword in value for keyword in keywords):
            return source_class
    return "PROGRAM_FUNCTION"


def taxonomy_for(path: str, text: str) -> str:
    value = (path + " " + text).lower()
    if any(k in value for k in ("security", "보안", "권한", "secret", "인증", "authz", "authn")):
        return "SECURITY"
    if any(k in value for k in ("privacy", "개인정보", "gdpr", "pii")):
        return "PRIVACY"
    if any(k in value for k in ("assurance", "independent", "qualification", "currentness", "evidence", "learning", "학습")):
        return "ASSURANCE"
    if any(k in value for k in ("deploy", "배포", "installation", "rollback")):
        return "DEPLOYMENT"
    if any(k in value for k in ("ai ", "llm", "model", "rag", "prompt", "agent")):
        return "AI_SPECIFIC"
    if any(k in value for k in ("regulatory", "규제", "compliance", "owasp", "nist", "iso")):
        return "REGULATORY"
    if any(k in value for k in ("license", "가격", "결제", "commercial", "상거래", "olicense")):
        return "COMMERCIAL_CONTRACTUAL"
    if any(k in value for k in ("운영", "가용성", "성능", "확장", "sla", "retention")):
        return "NON_FUNCTIONAL"
    return "FUNCTIONAL"


def criticality_for(text: str) -> str:
    if any(k in text for k in ("금지", "반드시", "필수", "MUST NOT", "MUST", "SHALL")):
        return "CRITICAL"
    if any(k in text for k in ("REQUIRED", "필요")):
        return "HIGH"
    return "MEDIUM"


def owner_domain_for(relative_path: str) -> str:
    parts = Path(relative_path).parts
    if len(parts) >= 2 and parts[1] == "semantic-assurance":
        return "semantic-assurance"
    return Path(relative_path).stem


def collect(eligible_sources: dict[str, str]) -> dict[str, Any]:
    authority_population: list[dict[str, str]] = []
    explicit_occurrences: dict[str, list[dict[str, Any]]] = defaultdict(list)
    candidate_records: list[dict[str, Any]] = []
    raw_evidence: list[dict[str, Any]] = []

    for relative in sorted(eligible_sources):
        doc = ROOT / relative
        disposition = eligible_sources[relative]
        content_sha = sha256_file(doc)
        authority_population.append({"path": relative, "content_sha256": content_sha})
        heading = "ROOT"
        lines = doc.read_text(encoding="utf-8", errors="replace").splitlines()
        for line_number, raw in enumerate(lines, 1):
            stripped = raw.strip()
            if stripped.startswith("#"):
                heading = stripped.lstrip("#").strip() or heading
                for match in EXPLICIT_ID_RE.finditer(stripped):
                    explicit_id = match.group(1)
                    text = paragraph_after(lines, line_number, heading)
                    explicit_occurrences[explicit_id].append({
                        "explicit_id": explicit_id,
                        "authority_document": relative,
                        "authority_document_sha256": content_sha,
                        "disposition": disposition,
                        "heading": heading,
                        "line": line_number,
                        "raw": raw,
                        "text": text,
                    })
                    raw_evidence.append({
                        "kind": "EXPLICIT_ID",
                        "explicit_id": explicit_id,
                        "authority_document": relative,
                        "line": line_number,
                    })
                continue
            if not stripped:
                continue

            for match in EXPLICIT_ID_RE.finditer(stripped):
                explicit_id = match.group(1)
                text = re.sub(r"^[-*+|\d.)\s]+", "", stripped).strip().strip("|").strip()
                if len(text) < 4:
                    continue
                explicit_occurrences[explicit_id].append({
                    "explicit_id": explicit_id,
                    "authority_document": relative,
                    "authority_document_sha256": content_sha,
                    "disposition": disposition,
                    "heading": heading,
                    "line": line_number,
                    "raw": raw,
                    "text": text,
                })
                raw_evidence.append({
                    "kind": "EXPLICIT_ID",
                    "explicit_id": explicit_id,
                    "authority_document": relative,
                    "line": line_number,
                })

            if NORMATIVE_RE.search(stripped) and not EXPLICIT_ID_RE.search(stripped):
                text = re.sub(r"^[-*+|\d.)\s]+", "", stripped).strip().strip("|").strip()
                if len(text) < 8:
                    continue
                norm = normalized_text(text)
                req_id = deterministic_candidate_id(relative, heading, norm)
                candidate_records.append({
                    "requirement_id": req_id,
                    "explicit_id": None,
                    "source_class": classify_source_class(relative, text),
                    "extraction_method": "CANDIDATE_EXTRACTED",
                    "authority_document": relative,
                    "source_anchor": {"heading": heading, "line": line_number},
                    "authority_document_sha256": content_sha,
                    "exact_source_digest": sha256_bytes(raw.encode()),
                    "normative_text": text,
                    "normative_text_digest": sha256_bytes(norm.encode()),
                    "owner_domain": owner_domain_for(relative),
                    "taxonomy": taxonomy_for(relative, text),
                    "subject": "PROGRAM",
                    "criticality": criticality_for(text),
                    "claim_effect": "INFORMATIONAL",
                    "waivability": "CONDITIONAL",
                    "applicability_state": "UNKNOWN",
                    "applicability_rule_ref": None,
                    "status": "ACTIVE",
                    "_normalized_text": norm,
                })
                raw_evidence.append({
                    "kind": "CANDIDATE_EXTRACTED",
                    "requirement_id": req_id,
                    "authority_document": relative,
                    "line": line_number,
                })

    return {
        "authority_population": authority_population,
        "explicit_occurrences": explicit_occurrences,
        "candidate_records": candidate_records,
        "raw_evidence": raw_evidence,
    }


def resolve_explicit(explicit_occurrences: dict[str, list[dict[str, Any]]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """RU-04/05 for EXPLICIT_ID: canonical text is chosen by AUTHORITY tier
    (NORMATIVE_REFINEMENT > NORMATIVE_CURRENT), never by text length. Occurrences that
    disagree within the SAME authority tier are NOT silently merged -- they are reported
    as an unresolved relation candidate and broken only by a disclosed, deterministic
    tie-break (lexicographically-first source document) so reruns stay reproducible."""
    records: list[dict[str, Any]] = []
    relation_candidates: list[dict[str, Any]] = []
    for explicit_id, occurrences in sorted(explicit_occurrences.items()):
        by_norm: dict[str, list[dict[str, Any]]] = defaultdict(list)
        for occ in occurrences:
            by_norm[normalized_text(occ["text"])].append(occ)
        if len(by_norm) > 1:
            relation_candidates.append({
                "explicit_id": explicit_id,
                "distinct_normalized_variants": len(by_norm),
                "occurrences": [
                    {"authority_document": o["authority_document"], "disposition": o["disposition"], "line": o["line"], "text": o["text"]}
                    for group in by_norm.values() for o in group
                ],
            })

        highest_rank = max(AUTHORITY_RANK[o["disposition"]] for o in occurrences)
        top_tier = [o for o in occurrences if AUTHORITY_RANK[o["disposition"]] == highest_rank]
        canonical = min(top_tier, key=lambda o: (o["authority_document"], o["line"]))

        norm = normalized_text(canonical["text"])
        records.append({
            "requirement_id": explicit_id,
            "explicit_id": explicit_id,
            "source_class": "EXPLICIT_ID",
            "extraction_method": "EXPLICIT_ID_PARSED",
            "authority_document": canonical["authority_document"],
            "source_anchor": {"heading": canonical["heading"], "line": canonical["line"]},
            "authority_document_sha256": canonical["authority_document_sha256"],
            "exact_source_digest": sha256_bytes(canonical["raw"].encode()),
            "normative_text": canonical["text"],
            "normative_text_digest": sha256_bytes(norm.encode()),
            "owner_domain": owner_domain_for(canonical["authority_document"]),
            "taxonomy": taxonomy_for(canonical["authority_document"], canonical["text"]),
            "subject": "PRODUCT" if explicit_id.startswith("FR-COM") else "ONSURE_META",
            "criticality": criticality_for(canonical["text"]),
            "claim_effect": "POSITIVE_CLAIM_GATE" if explicit_id.startswith(("FR-COM", "NFR")) else "QUALIFICATION_GATE",
            "waivability": "NON_WAIVABLE" if criticality_for(canonical["text"]) == "CRITICAL" else "CONDITIONAL",
            "applicability_state": "UNKNOWN",
            "applicability_rule_ref": None,
            "status": "ACTIVE",
            "canonicalization_authority": canonical["disposition"],
            "mention_count": len(occurrences),
            "mention_documents": sorted({o["authority_document"] for o in occurrences}),
        })
    return records, relation_candidates


def dedupe_candidates(candidate_records: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """RU-04/05 for CANDIDATE_EXTRACTED: relate exact-normalized-text duplicates without
    deleting any source-anchored record from the population (88 SS6: "record the relation,
    don't delete"). Every raw occurrence keeps its own requirement_id in requirement_ids;
    the duplicate relation is a separate overlay for later canonical-selection review."""
    by_norm: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for record in candidate_records:
        by_norm[record["_normalized_text"]].append(record)
    unresolved: list[dict[str, Any]] = []
    kept: list[dict[str, Any]] = []
    for norm, group in by_norm.items():
        unique_ids = sorted({r["requirement_id"] for r in group})
        if len(unique_ids) > 1:
            unresolved.append({
                "normalized_text_digest": sha256_bytes(norm.encode()),
                "requirement_ids": unique_ids,
            })
        seen_ids: set[str] = set()
        for record in group:
            record.pop("_normalized_text", None)
            if record["requirement_id"] in seen_ids:
                continue
            seen_ids.add(record["requirement_id"])
            kept.append(record)
    return kept, unresolved


def main() -> int:
    started_at = datetime.now(timezone.utc).isoformat()
    eligible_sources = load_eligible_authority_sources()

    collected = collect(eligible_sources)
    explicit_records, explicit_relation_candidates = resolve_explicit(collected["explicit_occurrences"])
    candidate_records, candidate_duplicate_groups = dedupe_candidates(collected["candidate_records"])

    all_records = explicit_records + candidate_records
    all_records.sort(key=lambda r: r["requirement_id"])

    requirement_ids = [r["requirement_id"] for r in all_records]
    manifest_source = "\n".join(
        f"{r['requirement_id']}:{r['normative_text_digest']}" for r in all_records
    ).encode()
    requirement_manifest_digest = sha256_bytes(manifest_source)

    authority_population_sorted = sorted(collected["authority_population"], key=lambda x: x["path"])
    authority_population_source = "\n".join(
        f"{a['path']}:{a['content_sha256']}" for a in authority_population_sorted
    ).encode()
    authority_document_population_digest = sha256_bytes(authority_population_source)

    unresolved_duplicate_candidates = candidate_duplicate_groups

    snapshot = {
        "contract": "ONSURE_REQUIREMENT_UNIVERSE_SNAPSHOT_V2",
        "generation_algorithm_version": ALGORITHM_VERSION,
        "requirement_epoch_id": REQUIREMENT_EPOCH_ID,
        "superseded_requirement_epoch_id": SUPERSEDED_REQUIREMENT_EPOCH_ID,
        "requirement_ids": requirement_ids,
        "requirement_manifest_digest": requirement_manifest_digest,
        "authority_document_population": authority_population_sorted,
        "authority_document_population_digest": authority_document_population_digest,
        "superseded_requirement_ids": [],
        "unresolved_duplicate_candidates": unresolved_duplicate_candidates,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "self_validation_nonfinal": True,
        "final_claim_allowed": False,
    }

    completed_at = datetime.now(timezone.utc).isoformat()
    receipt = {
        "contract": "ONSURE_REQUIREMENT_UNIVERSE_GENERATION_RECEIPT_V1",
        "generation_algorithm_version": ALGORITHM_VERSION,
        "authority_document_count": len(authority_population_sorted),
        "explicit_id_requirement_count": len(explicit_records),
        "candidate_extracted_requirement_count": len(candidate_records),
        "duplicate_candidate_group_count": len(unresolved_duplicate_candidates),
        "requirement_manifest_digest": requirement_manifest_digest,
        "started_at": started_at,
        "completed_at": completed_at,
        "decision": "GENERATED_NONFINAL",
        "self_validation_nonfinal": True,
        "final_claim_allowed": False,
    }

    out_dir = ROOT / ".onsure" / "requirement-universe"
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "requirement-records.json").write_text(
        json.dumps(all_records, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (out_dir / "requirement-universe-snapshot.json").write_text(
        json.dumps(snapshot, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (out_dir / "requirement-universe-generation-receipt.json").write_text(
        json.dumps(receipt, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (out_dir / "explicit-id-cross-document-variants.json").write_text(
        json.dumps(explicit_relation_candidates, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (out_dir / "raw-extraction-evidence.json").write_text(
        json.dumps(collected["raw_evidence"], ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    print(json.dumps({
        "authority_documents": len(authority_population_sorted),
        "explicit_id_requirements": len(explicit_records),
        "candidate_extracted_requirements": len(candidate_records),
        "duplicate_candidate_groups": len(unresolved_duplicate_candidates),
        "explicit_id_cross_document_variants": len(explicit_relation_candidates),
        "requirement_manifest_digest": requirement_manifest_digest,
    }))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError) as error:
        print(f"ONSURE_REQUIREMENT_UNIVERSE_GENERATION_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
