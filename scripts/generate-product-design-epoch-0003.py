#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / ".onsure/requirement-universe/epoch-0003-candidate"
ADMISSION = ROOT / "contracts/post-final-target-dd-001-040-authority-admission.candidate.v1.json"
RELATIONS = ROOT / "contracts/post-final-target-dd-to-fr-fin-relation.candidate.v1.json"
SOURCE_1 = ROOT / "docs/master/semantic-assurance/162_FINAL_TARGET_DELTA_DESIGN_DISCOVERY_REOPENING.md"
SOURCE_2 = ROOT / "docs/master/semantic-assurance/165_BLIND_DESIGN_DISCOVERY_WAVES_2_3.md"
DELTA_DOCS = {
    "docs/master/semantic-assurance/162_FINAL_TARGET_DELTA_DESIGN_DISCOVERY_REOPENING.md",
    "docs/master/semantic-assurance/163_FINAL_TARGET_DELTA_MISSING_DESIGN_CLOSURE.md",
    "docs/master/semantic-assurance/165_BLIND_DESIGN_DISCOVERY_WAVES_2_3.md",
    "docs/master/semantic-assurance/166_WAVES_2_3_MISSING_DESIGN_CLOSURE.md",
}
HEADING = re.compile(r"^###\s+(DD-\d{3})\s+[—-]\s+(.+?)(?:\s+[—-]\s+(P[01]))?(?:\s*/.*)?$")


def sha256(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def normalized(s: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^0-9A-Za-z가-힣]+", " ", s)).strip().lower()


def parse_source(path: Path) -> dict[str, dict]:
    lines = path.read_text(encoding="utf-8").splitlines()
    out: dict[str, dict] = {}
    rel = path.relative_to(ROOT).as_posix()
    digest = sha256(path.read_bytes())
    for i, line in enumerate(lines):
        m = HEADING.match(line.strip())
        if not m:
            continue
        dd, title, priority = m.group(1), m.group(2).strip(), m.group(3)
        body = []
        for nxt in lines[i + 1:]:
            if nxt.startswith("### "):
                break
            if nxt.startswith("## "):
                break
            if nxt.strip():
                body.append(nxt.strip())
            if len(body) >= 3:
                break
        text = f"{title}: {' '.join(body)}".strip()
        out[dd] = {"title": title, "priority": priority or "P1", "text": text, "authority_document": rel, "authority_document_sha256": digest, "line": i + 1}
    return out


def main() -> int:
    for cmd in ([sys.executable, "scripts/materialize-product-design-authority.py"], [sys.executable, "scripts/generate-requirement-universe.py", "--epoch-candidate"]):
        rc = subprocess.run(cmd, cwd=ROOT)
        if rc.returncode:
            return rc.returncode
    admission = json.loads(ADMISSION.read_text(encoding="utf-8"))
    relations_raw = json.loads(RELATIONS.read_text(encoding="utf-8"))
    relations = {r["dd"]: r["fr_fin"] for r in relations_raw["rows"]}
    parsed = parse_source(SOURCE_1) | parse_source(SOURCE_2)
    canonical_ids = admission["canonical_ids"]
    missing = sorted(set(canonical_ids) - set(parsed))
    extra = sorted(set(parsed) - set(canonical_ids))
    if missing or extra or len(canonical_ids) != 40 or len(set(canonical_ids)) != 40:
        raise RuntimeError(f"DD_AUTHORITY_ADMISSION_MISMATCH missing={missing} extra={extra} count={len(canonical_ids)}")
    records_path = OUT / "requirement-records.json"
    snapshot_path = OUT / "requirement-universe-snapshot.json"
    receipt_path = OUT / "requirement-universe-generation-receipt.json"
    records = json.loads(records_path.read_text(encoding="utf-8"))
    # Delta design prose is authoritative source material, but DD admission owns its denominator nodes.
    # Remove auto-extracted non-ID prose from these documents to prevent semantic double counting.
    records = [r for r in records if not (r.get("authority_document") in DELTA_DOCS and r.get("extraction_method") == "CANDIDATE_EXTRACTED")]
    for dd in canonical_ids:
        p = parsed[dd]
        norm = normalized(p["text"])
        priority = p["priority"]
        records.append({
            "requirement_id": dd,
            "explicit_id": dd,
            "source_class": "DISCOVERY_DELTA_AUTHORITY",
            "extraction_method": "AUTHORITY_ADMISSION_REGISTRY",
            "authority_document": p["authority_document"],
            "source_anchor": {"heading": f"{dd} — {p['title']}", "line": p["line"]},
            "authority_document_sha256": p["authority_document_sha256"],
            "exact_source_digest": sha256(p["text"].encode()),
            "normative_text": p["text"],
            "normative_text_digest": sha256(norm.encode()),
            "owner_domain": "post-final-target-design-discovery",
            "taxonomy": "ASSURANCE",
            "subject": "PRODUCT",
            "criticality": "CRITICAL" if priority == "P0" else "HIGH",
            "claim_effect": "QUALIFICATION_GATE" if dd == "DD-040" else "POSITIVE_CLAIM_GATE",
            "waivability": "NON_WAIVABLE" if priority == "P0" else "CONDITIONAL",
            "applicability_state": "UNKNOWN",
            "applicability_rule_ref": None,
            "status": "ACTIVE",
            "node_type": "CANONICAL_DISCOVERY_OBLIGATION",
            "parent_fr_fin": relations.get(dd, []),
            "double_count_with_parent": False,
        })
    records.sort(key=lambda r: r["requirement_id"])
    ids = [r["requirement_id"] for r in records]
    if len(ids) != len(set(ids)):
        raise RuntimeError("DUPLICATE_REQUIREMENT_IDS_AFTER_DD_ADMISSION")
    manifest_digest = sha256("\n".join(f"{r['requirement_id']}:{r['normative_text_digest']}" for r in records).encode())
    snapshot = json.loads(snapshot_path.read_text(encoding="utf-8"))
    snapshot["requirement_ids"] = ids
    snapshot["requirement_manifest_digest"] = manifest_digest
    snapshot["requirement_epoch_id"] = "EPOCH::REQUIREMENT::0003::CANDIDATE"
    snapshot["dd_authority_admission"] = {"canonical_dd_count": 40, "admission_contract": ADMISSION.relative_to(ROOT).as_posix(), "relation_registry": RELATIONS.relative_to(ROOT).as_posix(), "double_count_with_parent": False}
    snapshot["generated_at"] = datetime.now(timezone.utc).isoformat()
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    receipt["requirement_manifest_digest"] = manifest_digest
    receipt["canonical_dd_requirement_count"] = 40
    receipt["decision"] = "GENERATED_POST_DELTA_NONFINAL"
    records_path.write_text(json.dumps(records, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    snapshot_path.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    admission_receipt = {
        "contract": "ONSURE_DD_AUTHORITY_ADMISSION_RECEIPT_V1",
        "dd_count": 40,
        "missing_dd": [],
        "unmapped_dd": sorted(set(canonical_ids) - set(relations)),
        "requirement_count": len(ids),
        "requirement_manifest_digest": manifest_digest,
        "authority_population_digest": snapshot["authority_document_population_digest"],
        "decision": "ADMITTED_TO_EPOCH_0003_CANDIDATE_NONFINAL",
        "final_claim_allowed": False,
    }
    (OUT / "dd-authority-admission-receipt.json").write_text(json.dumps(admission_receipt, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(admission_receipt, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, KeyError) as e:
        print(f"ONSURE_PRODUCT_DESIGN_EPOCH_0003_FAIL {e}", file=sys.stderr)
        raise SystemExit(1)
