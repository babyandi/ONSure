#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PRECLEAN=ROOT/'.onsure/independent-clean/preclean-subject.json'; IDS=('INDEPENDENT-CLEAN-A','INDEPENDENT-CLEAN-B')

def digest_payload(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None); return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def digest_subject(d:dict)->str:
    x=dict(d); x.pop('subject_digest',None); return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--source-commit-sha',default=None); ap.add_argument('--source-tree-sha',default=None); ap.add_argument('--receipts-dir',default=os.environ.get('ONSURE_INDEPENDENT_CLEAN_RECEIPTS_DIR','evidence/independent-clean')); args=ap.parse_args()
    evidence=Path(args.receipts_dir); evidence=evidence if evidence.is_absolute() else ROOT/evidence
    expected_commit=args.source_commit_sha or git('rev-parse','HEAD'); expected_tree=args.source_tree_sha or git('rev-parse','HEAD^{tree}'); reasons=[]; waves=[]
    if not PRECLEAN.is_file(): reasons.append('PRECLEAN_SUBJECT_MISSING'); pre={}
    else:
        try: pre=json.loads(PRECLEAN.read_text(encoding='utf-8'))
        except Exception: pre={}; reasons.append('PRECLEAN_SUBJECT_UNREADABLE')
    if pre:
        if pre.get('contract')!='ONSURE_INDEPENDENT_CLEAN_PRECLEAN_SUBJECT_V2': reasons.append('PRECLEAN_SUBJECT_CONTRACT_MISMATCH')
        if pre.get('source_commit_sha')!=expected_commit: reasons.append('PRECLEAN_SUBJECT_COMMIT_MISMATCH')
        if pre.get('source_tree_sha')!=expected_tree: reasons.append('PRECLEAN_SUBJECT_TREE_MISMATCH')
        if pre.get('decision')!='READY_FOR_INDEPENDENT_CLEAN_NONFINAL': reasons.append('PRECLEAN_SUBJECT_NOT_READY')
        if pre.get('blocking_reasons'): reasons.append('PRECLEAN_SUBJECT_HAS_BLOCKERS')
        if pre.get('subject_digest')!=digest_subject(pre): reasons.append('PRECLEAN_SUBJECT_DIGEST_INVALID')
        for f in ('subject_digest','requirement_manifest_digest','authority_population_digest','coverage_digest'):
            v=str(pre.get(f,''));
            if len(v)!=64 or any(c not in '0123456789abcdef' for c in v): reasons.append(f'PRECLEAN_{f.upper()}_INVALID')
    for cid in IDS:
        p=evidence/f'{cid}.json'
        if not p.exists(): reasons.append(f'MISSING_RECEIPT:{cid}'); continue
        try:d=json.loads(p.read_text(encoding='utf-8'))
        except Exception: reasons.append(f'INVALID_JSON:{cid}'); continue
        waves.append(d)
        if d.get('contract')!='ONSURE_INDEPENDENT_CLEAN_RECEIPT_V2': reasons.append(f'CONTRACT_MISMATCH:{cid}')
        if d.get('clean_id')!=cid: reasons.append(f'CLEAN_ID_MISMATCH:{cid}')
        if d.get('receipt_digest')!=digest_payload(d): reasons.append(f'DIGEST_MISMATCH:{cid}')
        if d.get('decision')!='CLEAN': reasons.append(f'NOT_CLEAN:{cid}')
        if d.get('blocking_finding_count')!=0: reasons.append(f'BLOCKING_FINDINGS:{cid}')
        if d.get('final_claim_allowed') is not False: reasons.append(f'FINAL_CLAIM_NOT_FALSE:{cid}')
        if d.get('source_commit_sha')!=expected_commit: reasons.append(f'CURRENT_COMMIT_MISMATCH:{cid}')
        if d.get('source_tree_sha')!=expected_tree: reasons.append(f'CURRENT_TREE_MISMATCH:{cid}')
        if pre:
            if d.get('preclean_subject_digest')!=pre.get('subject_digest'): reasons.append(f'PRECLEAN_SUBJECT_DIGEST_MISMATCH:{cid}')
            if d.get('requirement_manifest_digest')!=pre.get('requirement_manifest_digest'): reasons.append(f'REQUIREMENT_DIGEST_NOT_PRECLEAN_SUBJECT:{cid}')
            if d.get('authority_population_digest')!=pre.get('authority_population_digest'): reasons.append(f'AUTHORITY_DIGEST_NOT_PRECLEAN_SUBJECT:{cid}')
            if d.get('coverage_digest')!=pre.get('coverage_digest'): reasons.append(f'COVERAGE_DIGEST_NOT_PRECLEAN_SUBJECT:{cid}')
        for f,n in (('source_commit_sha',40),('source_tree_sha',40),('preclean_subject_digest',64),('requirement_manifest_digest',64),('authority_population_digest',64),('coverage_digest',64)):
            v=str(d.get(f,''));
            if len(v)!=n or any(c not in '0123456789abcdef' for c in v): reasons.append(f'INVALID_{f.upper()}:{cid}')
        for f in ('verifier_principal','verifier_process_lineage','model_or_method_lineage','verified_at'):
            if not d.get(f): reasons.append(f'MISSING_{f.upper()}:{cid}')
        cca=d.get('common_control_attestation') or {}
        if not isinstance(cca,dict): reasons.append(f'COMMON_CONTROL_ATTESTATION_NOT_OBJECT:{cid}')
        else:
            if 'common_control_present' not in cca: reasons.append(f'COMMON_CONTROL_PRESENCE_MISSING:{cid}')
            if not isinstance(cca.get('details',[]),list): reasons.append(f'COMMON_CONTROL_DETAILS_INVALID:{cid}')
            if cca.get('independence_still_satisfied') is not True: reasons.append(f'COMMON_CONTROL_INDEPENDENCE_NOT_SATISFIED:{cid}')
    if len(waves)==2:
        for field in ('source_commit_sha','source_tree_sha','preclean_subject_digest','requirement_manifest_digest','authority_population_digest','coverage_digest'):
            if waves[0].get(field)!=waves[1].get(field): reasons.append(f'{field.upper()}_DIVERGENCE')
        if waves[0].get('verifier_principal')==waves[1].get('verifier_principal'): reasons.append('CLEAN_A_B_VERIFIER_PRINCIPAL_NOT_DISTINCT')
        if waves[0].get('verifier_process_lineage')==waves[1].get('verifier_process_lineage'): reasons.append('CLEAN_A_B_PROCESS_LINEAGE_NOT_DISTINCT')
        sig=lambda w:(w.get('verifier_principal'),w.get('verifier_process_lineage'),w.get('model_or_method_lineage'))
        if sig(waves[0])==sig(waves[1]): reasons.append('CLEAN_A_B_NOT_INDEPENDENT_LINEAGE')
        a=(waves[0].get('common_control_attestation') or {}).get('common_control_present'); b=(waves[1].get('common_control_attestation') or {}).get('common_control_present')
        if a is True and b is True:
            da=(waves[0].get('common_control_attestation') or {}).get('details',[]); db=(waves[1].get('common_control_attestation') or {}).get('details',[])
            if set(da)&set(db): reasons.append('CLEAN_A_B_SHARED_COMMON_CONTROL_UNRESOLVED')
    out={'contract':'ONSURE_INDEPENDENT_CLEAN_TWICE_VALIDATION_V6','expected_source_commit_sha':expected_commit,'expected_source_tree_sha':expected_tree,'receipts_dir':str(evidence),'preclean_subject_digest':pre.get('subject_digest') if pre else None,'receipt_count':len(waves),'blocking_reasons':sorted(set(reasons)),'decision':'CLEAN_TWICE_NONFINAL' if not reasons else 'HOLD_NONFINAL','design_lock':False,'github_actions_authority':False,'final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 61
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError,subprocess.CalledProcessError) as e: print(f'ONSURE_INDEPENDENT_CLEAN_FAIL {e}',file=sys.stderr); raise SystemExit(1)
