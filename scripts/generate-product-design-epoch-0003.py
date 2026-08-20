#!/usr/bin/env python3
from __future__ import annotations
import hashlib, json, re, subprocess, sys
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/".onsure/requirement-universe/epoch-0003-candidate"
ADMISSION=ROOT/"contracts/post-final-target-dd-001-040-authority-admission.candidate.v1.json"
RELATIONS=ROOT/"contracts/post-final-target-dd-to-fr-fin-relation.candidate.v1.json"
SOURCE_1=ROOT/"docs/master/semantic-assurance/162_FINAL_TARGET_DELTA_DESIGN_DISCOVERY_REOPENING.md"
SOURCE_2=ROOT/"docs/master/semantic-assurance/165_BLIND_DESIGN_DISCOVERY_WAVES_2_3.md"
HEADING=re.compile(r"^###\s+(DD-\d{3})(?:\s+[—-])?\s+(.+?)(?:\s+[—-]\s+(P[01]))?(?:\s*/.*)?$")
ELIGIBLE={"NORMATIVE_CURRENT","NORMATIVE_REFINEMENT"}

def sha256(b:bytes)->str:return hashlib.sha256(b).hexdigest()
def normalized(s:str)->str:return re.sub(r"\s+"," ",re.sub(r"[^0-9A-Za-z가-힣]+"," ",s)).strip().lower()

def run(cmd:list[str])->None:
    r=subprocess.run(cmd,cwd=ROOT)
    if r.returncode: raise RuntimeError(f"COMMAND_FAILED:{cmd}:{r.returncode}")

def parse_source(path:Path)->dict[str,dict]:
    lines=path.read_text(encoding="utf-8").splitlines(); out={}; rel=path.relative_to(ROOT).as_posix(); digest=sha256(path.read_bytes())
    for i,line in enumerate(lines):
        m=HEADING.match(line.strip())
        if not m: continue
        dd,title,priority=m.group(1),m.group(2).strip(),m.group(3)
        body=[]
        for nxt in lines[i+1:]:
            if nxt.startswith("### ") or nxt.startswith("## "): break
            if nxt.strip(): body.append(nxt.strip())
            if len(body)>=3: break
        text=f"{title}: {' '.join(body)}".strip()
        out[dd]={"title":title,"priority":priority or "P1","text":text,"authority_document":rel,"authority_document_sha256":digest,"line":i+1}
    return out

def main()->int:
    # Phase A: generate the established + final-target RU without allowing new DD prose to
    # participate in canonicalization of pre-existing explicit IDs such as FR-FRESH-*.
    run([sys.executable,"scripts/materialize-requirement-authority-manifest.py"])
    run([sys.executable,"scripts/generate-requirement-universe.py","--epoch-candidate"])
    # Phase B: now materialize the full post-delta authority population. DD source documents
    # contribute through the explicit admission registry below, not through prose auto-extraction.
    run([sys.executable,"scripts/materialize-product-design-authority.py"])
    full_manifest=json.loads((ROOT/".onsure/requirement-universe/requirement-authority-source-manifest.json").read_text(encoding="utf-8"))
    full_authority=[{"path":r["artifact_path"],"content_sha256":r["content_sha256"]} for r in full_manifest["rows"] if r["requirement_source_disposition"] in ELIGIBLE]
    full_authority.sort(key=lambda x:x["path"])
    authority_digest=sha256("\n".join(f"{x['path']}:{x['content_sha256']}" for x in full_authority).encode())

    admission=json.loads(ADMISSION.read_text(encoding="utf-8")); relation_raw=json.loads(RELATIONS.read_text(encoding="utf-8")); relations={r["dd"]:r["fr_fin"] for r in relation_raw["rows"]}
    parsed=parse_source(SOURCE_1)|parse_source(SOURCE_2); canonical=admission["canonical_ids"]
    missing=sorted(set(canonical)-set(parsed)); extra=sorted(set(parsed)-set(canonical))
    if missing or extra or len(canonical)!=40 or len(set(canonical))!=40: raise RuntimeError(f"DD_AUTHORITY_ADMISSION_MISMATCH missing={missing} extra={extra}")
    if set(relations)!=set(canonical): raise RuntimeError("DD_PARENT_RELATION_DENOMINATOR_MISMATCH")

    records_path=OUT/"requirement-records.json"; snapshot_path=OUT/"requirement-universe-snapshot.json"; receipt_path=OUT/"requirement-universe-generation-receipt.json"
    records=json.loads(records_path.read_text(encoding="utf-8"))
    # Base RU was generated before delta sources became eligible, so there are no prose-derived
    # DD duplicates and no delta-source override of existing explicit IDs.
    for dd in canonical:
        p=parsed[dd]; norm=normalized(p["text"]); priority=p["priority"]
        records.append({
            "requirement_id":dd,"explicit_id":dd,"source_class":"DISCOVERY_DELTA_AUTHORITY","extraction_method":"AUTHORITY_ADMISSION_REGISTRY",
            "authority_document":p["authority_document"],"source_anchor":{"heading":f"{dd} — {p['title']}","line":p["line"]},"authority_document_sha256":p["authority_document_sha256"],
            "exact_source_digest":sha256(p["text"].encode()),"normative_text":p["text"],"normative_text_digest":sha256(norm.encode()),
            "owner_domain":"post-final-target-design-discovery","taxonomy":"ASSURANCE","subject":"PRODUCT","criticality":"CRITICAL" if priority=="P0" else "HIGH",
            "claim_effect":"QUALIFICATION_GATE" if dd=="DD-040" else "POSITIVE_CLAIM_GATE","waivability":"NON_WAIVABLE" if priority=="P0" else "CONDITIONAL",
            "applicability_state":"UNKNOWN","applicability_rule_ref":None,"status":"ACTIVE","node_type":"CANONICAL_DISCOVERY_OBLIGATION","parent_fr_fin":relations[dd],"double_count_with_parent":False
        })
    records.sort(key=lambda r:r["requirement_id"]); ids=[r["requirement_id"] for r in records]
    if len(ids)!=len(set(ids)): raise RuntimeError("DUPLICATE_REQUIREMENT_IDS_AFTER_DD_ADMISSION")
    manifest_digest=sha256("\n".join(f"{r['requirement_id']}:{r['normative_text_digest']}" for r in records).encode())
    snapshot=json.loads(snapshot_path.read_text(encoding="utf-8")); snapshot.update({"requirement_ids":ids,"requirement_manifest_digest":manifest_digest,"requirement_epoch_id":"EPOCH::REQUIREMENT::0003::CANDIDATE","authority_document_population":full_authority,"authority_document_population_digest":authority_digest,"generated_at":datetime.now(timezone.utc).isoformat(),"dd_authority_admission":{"canonical_dd_count":40,"admission_contract":ADMISSION.relative_to(ROOT).as_posix(),"relation_registry":RELATIONS.relative_to(ROOT).as_posix(),"double_count_with_parent":False}})
    receipt=json.loads(receipt_path.read_text(encoding="utf-8")); receipt.update({"requirement_manifest_digest":manifest_digest,"authority_document_count":len(full_authority),"canonical_dd_requirement_count":40,"decision":"GENERATED_POST_DELTA_NONFINAL"})
    records_path.write_text(json.dumps(records,ensure_ascii=False,indent=2,sort_keys=True)+"\n",encoding="utf-8"); snapshot_path.write_text(json.dumps(snapshot,ensure_ascii=False,indent=2,sort_keys=True)+"\n",encoding="utf-8"); receipt_path.write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    ar={"contract":"ONSURE_DD_AUTHORITY_ADMISSION_RECEIPT_V1","dd_count":40,"missing_dd":[],"unmapped_dd":[],"requirement_count":len(ids),"requirement_manifest_digest":manifest_digest,"authority_population_digest":authority_digest,"decision":"ADMITTED_TO_EPOCH_0003_CANDIDATE_NONFINAL","final_claim_allowed":False}
    (OUT/"dd-authority-admission-receipt.json").write_text(json.dumps(ar,ensure_ascii=False,indent=2,sort_keys=True)+"\n",encoding="utf-8"); print(json.dumps(ar,ensure_ascii=False)); return 0

if __name__=="__main__":
    try: raise SystemExit(main())
    except (OSError,RuntimeError,ValueError,KeyError) as e: print(f"ONSURE_PRODUCT_DESIGN_EPOCH_0003_FAIL {e}",file=sys.stderr); raise SystemExit(1)
