#!/usr/bin/env python3
from __future__ import annotations
import json,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
REG=ROOT/'contracts/design-document-authority-registry.v1.json'
def main()->int:
    d=json.loads(REG.read_text(encoding='utf-8'))
    rows=d['documents']; ids=[r['document_id'] for r in rows]; paths=[r['path'] for r in rows]
    errors=[]
    if len(ids)!=len(set(ids)): errors.append('DUPLICATE_DOCUMENT_ID')
    if len(paths)!=len(set(paths)): errors.append('DUPLICATE_DOCUMENT_PATH')
    known=set(ids)
    for r in rows:
        if not (ROOT/r['path']).exists(): errors.append(f"MISSING_PATH:{r['document_id']}:{r['path']}")
    for rel in d['relations']:
        if rel['from'] not in known or rel['to'] not in known: errors.append(f"UNKNOWN_RELATION_ENDPOINT:{rel}")
    # Ambiguous numeric prefixes are allowed but can never be identity.
    prefixes={}
    for r in rows:
        name=Path(r['path']).name; m=re.match(r'^(\d+)_',name)
        if m: prefixes.setdefault(m.group(1),[]).append(r['document_id'])
    ambiguous={k:v for k,v in prefixes.items() if len(v)>1}
    out={'contract':'ONSURE_DESIGN_DOCUMENT_AUTHORITY_VALIDATION_V1','document_count':len(rows),'relation_count':len(d['relations']),'ambiguous_numeric_prefixes':ambiguous,'errors':errors,'decision':'PASS_NONFINAL' if not errors else 'HOLD_NONFINAL','final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True))
    return 0 if not errors else 1
if __name__=='__main__': raise SystemExit(main())
