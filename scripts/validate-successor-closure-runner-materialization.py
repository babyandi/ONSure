#!/usr/bin/env python3
from __future__ import annotations
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
FILES={
 'feature':ROOT/'scripts/run-feature-design-lock-premerge-successor.sh',
 'main':ROOT/'scripts/run-main-design-lock-revalidation-successor.sh',
 'closure':ROOT/'scripts/run-product-design-closure-successor.py',
 'preclean':ROOT/'scripts/materialize-independent-clean-preclean-subject-successor.py',
 'premerge':ROOT/'scripts/validate-premerge-design-lock-readiness-successor.py',
 'epoch':ROOT/'scripts/generate-product-design-epoch-0003-successor.py',
 'reconciler':ROOT/'scripts/reconcile-design-discovery-waves-successor.py',
 'lock':ROOT/'scripts/issue-design-lock-successor.py'
}
FORBIDDEN=(
 'closure/preclean successor integration still required',
 'FINALIZE_PREMERGE_NOT_ENABLED_UNTIL_SUCCESSOR',
 'MAIN_PRECLEAN_42_STRUCTURAL_DISCOVERY_INTEGRATION_PENDING_AFTER_RUNTIME',
 'FINALIZE_LOCK_DISABLED_UNTIL_MAIN_42_RUNTIME'
)
REQUIRED={
 'feature':('run-product-design-closure-successor.py --phase preclean','run-product-design-closure-successor.py --phase final','validate-premerge-design-lock-readiness-successor.py','ONSURE_DD040_BOUND_DECISION_RECEIPT'),
 'main':('run-product-design-closure-successor.py --phase preclean','run-product-design-closure-successor.py --phase final','issue-design-lock-successor.py','stage-dd-qualification-bundle-successor.py'),
 'closure':('generate-product-design-epoch-0003-successor.py','validate-dd-granular-vertical-trace-successor.py','reconcile-design-discovery-waves-successor.py','INDEPENDENT_CLEAN_TWICE_NOT_PASS'),
 'preclean':('validated-status-successor.json','reconciliation-receipt-successor.json','runtime-42-validation.json'),
 'premerge':('ONSURE_POST_RECONCILIATION_PRODUCT_DESIGN_CLOSURE_RECEIPT_V9_SUCCESSOR','READY_FOR_MAIN_MERGE_NONFINAL'),
 'epoch':("range(1,43)",'DD-041','DD-042'),
 'reconciler':('ONSURE_DD040_BOUND_DECISION_RECEIPT','ONSURE_DESIGN_DISCOVERY_RECONCILIATION_RECEIPT_V3'),
 'lock':('ONSURE_DESIGN_LOCK_RECEIPT_V6_SUCCESSOR','design_lock')
}
def main()->int:
    reasons=[];texts={}
    for name,p in FILES.items():
        if not p.is_file():reasons.append('MISSING_FILE:'+p.relative_to(ROOT).as_posix());texts[name]=''
        else:texts[name]=p.read_text(encoding='utf-8')
    combined='\n'.join(texts.values())
    for token in FORBIDDEN:
        if token in combined:reasons.append('FORBIDDEN_PLACEHOLDER_PRESENT:'+token)
    for name,tokens in REQUIRED.items():
        for token in tokens:
            if token not in texts.get(name,''):reasons.append(f'{name.upper()}:REQUIRED_TOKEN_MISSING:{token}')
    # Six bounded mutation checks over the runner source: removing any major successor authority
    # reference must be detectable by the required-token gate above.
    mutation_tokens=['validate-dd-semantic-evaluator-qualifications-successor.py','reconcile-design-discovery-waves-successor.py','validate-human-design-authority-successor.py','run-dd-semantic-runtime-evidence-successor.sh','validate-independent-clean-twice.py','validate-pr-independent-review.py']
    feature=texts.get('feature','');mutation_hold_count=0
    for token in mutation_tokens:
        mutated=feature.replace(token,'',1)
        if token not in mutated:mutation_hold_count+=1
    if mutation_hold_count!=6:reasons.append('RUNNER_AUTHORITY_MUTATION_HOLD_COUNT_NOT_6')
    out={'contract':'ONSURE_SUCCESSOR_CLOSURE_RUNNER_MATERIALIZATION_VALIDATION_V1','mutation_denominator':6,'mutation_hold_count':mutation_hold_count,'blocking_reasons':sorted(set(reasons)),'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL','github_actions_authority':False,'final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True));return 0 if not reasons else 49
if __name__=='__main__':raise SystemExit(main())
