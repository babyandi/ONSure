#!/usr/bin/env python3
from __future__ import annotations
import contextlib,hashlib,importlib.util,io,json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE_SCRIPT=ROOT/'scripts/reconcile-design-discovery-waves-v3.py'
OUT=ROOT/'.onsure/design-discovery-v3/reconciliation-receipt-successor.json'

def load(p:Path):return json.loads(p.read_text(encoding='utf-8'))
def canon(d:dict)->str:
    x=dict(d);x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def sha(p:Path)->str:return hashlib.sha256(p.read_bytes()).hexdigest()

def main()->int:
    check=subprocess.run([sys.executable,'scripts/validate-dd040-nonblocking-p1-bound-decision.py'],cwd=ROOT,capture_output=True,text=True,check=False)
    if check.returncode:
        print(check.stdout.strip() or json.dumps({'contract':'ONSURE_DESIGN_DISCOVERY_SUCCESSOR_RECONCILIATION_V1','decision':'HOLD_NONFINAL','blocking_reasons':['DD040_BOUND_DECISION_NOT_PASS'],'final_claim_allowed':False}));return 31
    bound=json.loads(check.stdout.strip().splitlines()[-1]);ceiling=bound['nonblocking_p1_ceiling']
    spec=importlib.util.spec_from_file_location('onsure_reconciler_v3_base',BASE_SCRIPT)
    if spec is None or spec.loader is None:raise RuntimeError('BASE_RECONCILER_IMPORT_FAILED')
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
    original_load=mod.load
    def load_override(path):
        value=original_load(path)
        if Path(path).resolve()==Path(mod.POLICY_P1).resolve():
            value=dict(value);value['nonblocking_p1_ceiling']=ceiling;value['nonblocking_p1_ceiling_state']='EXTERNAL_HUMAN_AUTHORITY_RECEIPT'
        return value
    mod.load=load_override
    captured=io.StringIO()
    with contextlib.redirect_stdout(captured): rc=mod.main()
    base_receipt_path=ROOT/'.onsure/design-discovery-v3/reconciliation-receipt.json'
    if rc or not base_receipt_path.is_file():
        text=captured.getvalue().strip();print(text or json.dumps({'contract':'ONSURE_DESIGN_DISCOVERY_SUCCESSOR_RECONCILIATION_V1','decision':'HOLD_NONFINAL','blocking_reasons':['BASE_STRUCTURAL_RECONCILIATION_NOT_PASS'],'final_claim_allowed':False}));return 31
    base=load(base_receipt_path)
    if base.get('decision')!='SATURATION_CANDIDATE_NONFINAL' or base.get('blocking_reasons'):return 31
    decision_ref=Path(os.environ['ONSURE_DD040_BOUND_DECISION_RECEIPT']).expanduser();decision_ref=decision_ref if decision_ref.is_absolute() else ROOT/decision_ref; decision=load(decision_ref)
    receipt={
      'contract':'ONSURE_DESIGN_DISCOVERY_RECONCILIATION_RECEIPT_V3',
      'reconciler_principal':'ONSURE_DETERMINISTIC_DISCOVERY_RECONCILER_SUCCESSOR_V1',
      'reconciler_process_lineage':'sha256:'+sha(Path(__file__)),
      'base_structural_reconciliation_receipt_digest':base.get('receipt_digest'),
      'dd040_bound_decision_receipt_digest':decision.get('receipt_digest'),
      'dd040_bound_approver_principal':decision.get('approver_principal'),
      'nonblocking_p1_ceiling':ceiling,
      'pair_digest':base.get('pair_digest'),'wave_a_digest':base.get('wave_a_digest'),'wave_b_digest':base.get('wave_b_digest'),
      'wave_a_custody_digest':base.get('wave_a_custody_digest'),'wave_b_custody_digest':base.get('wave_b_custody_digest'),
      'frozen_tree_sha':base.get('frozen_tree_sha'),'frozen_authority_digest':base.get('frozen_authority_digest'),
      'blocking_reasons':[],'decision':'SATURATION_CANDIDATE_NONFINAL','github_actions_authority':False,'final_claim_allowed':False
    }
    receipt['receipt_digest']=canon(receipt);OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8');print(json.dumps(receipt,ensure_ascii=False,sort_keys=True));return 0
if __name__=='__main__':
    try:raise SystemExit(main())
    except Exception as e:
        print(json.dumps({'contract':'ONSURE_DESIGN_DISCOVERY_RECONCILIATION_RECEIPT_V3','decision':'HOLD_NONFINAL','blocking_reasons':[f'{type(e).__name__}:{e}'],'final_claim_allowed':False},ensure_ascii=False,sort_keys=True));raise SystemExit(31)
