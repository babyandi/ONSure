#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT_DIR="${ONSURE_DD_VERIFY_OUT:-.onsure/manual-dd-verification-successor}"
mkdir -p "$OUT_DIR"
RUN_ID="dd-manual-successor-$(date -u +%Y%m%dT%H%M%SZ)"
COMMIT_SHA="$(git rev-parse HEAD)"; TREE_SHA="$(git rev-parse 'HEAD^{tree}')"
set +e
python3 scripts/validate-dd-denominator-42.py >"$OUT_DIR/$RUN_ID.denominator.stdout" 2>"$OUT_DIR/$RUN_ID.denominator.stderr"; DEN_RC=$?
mvn -B -Dtest=BuiltInDdSemanticEvaluatorsTest,DdSemanticEvaluatorQualificationFixtureTest,DdQualifiedRuntimeFactoryTest,DesignGapDdSemanticEvaluatorsTest,DesignGapDdQualificationFixtureTest test >"$OUT_DIR/$RUN_ID.maven.stdout" 2>"$OUT_DIR/$RUN_ID.maven.stderr"; MAVEN_RC=$?
set -e
python3 - "$OUT_DIR/$RUN_ID.json" "$RUN_ID" "$COMMIT_SHA" "$TREE_SHA" "$DEN_RC" "$MAVEN_RC" <<'PY'
import hashlib,json,pathlib,sys,xml.etree.ElementTree as ET
out=pathlib.Path(sys.argv[1]); run_id=sys.argv[2]; commit=sys.argv[3]; tree=sys.argv[4]; den_rc=int(sys.argv[5]); maven_rc=int(sys.argv[6])
root=pathlib.Path('.')
classes=['BuiltInDdSemanticEvaluatorsTest','DdSemanticEvaluatorQualificationFixtureTest','DdQualifiedRuntimeFactoryTest','DesignGapDdSemanticEvaluatorsTest','DesignGapDdQualificationFixtureTest']
reports=[]; total=fail=err=skip=0
for c in classes:
 p=root/'target/surefire-reports'/f'TEST-kr.co.oruda.onsure.platform.{c}.xml'
 if not p.is_file(): reports.append({'test_class':c,'present':False}); continue
 x=ET.fromstring(p.read_bytes()); item={'test_class':c,'present':True,'tests':int(x.attrib.get('tests',0)),'failures':int(x.attrib.get('failures',0)),'errors':int(x.attrib.get('errors',0)),'skipped':int(x.attrib.get('skipped',0))}; reports.append(item); total+=item['tests']; fail+=item['failures']; err+=item['errors']; skip+=item['skipped']
base=next((r for r in reports if r['test_class']=='DdSemanticEvaluatorQualificationFixtureTest'),{})
ext=next((r for r in reports if r['test_class']=='DesignGapDdQualificationFixtureTest'),{})
routes=next((r for r in reports if r['test_class']=='DesignGapDdSemanticEvaluatorsTest'),{})
base160=base.get('tests')==160 and base.get('failures')==0 and base.get('errors')==0
ext13=ext.get('tests')==13 and ext.get('failures')==0 and ext.get('errors')==0
route42=routes.get('tests',0)>=2 and routes.get('failures')==0 and routes.get('errors')==0
all_ok=maven_rc==0 and base160 and ext13 and route42 and fail==0 and err==0
rec={'contract':'ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V7','run_id':run_id,'source_commit_sha':commit,'source_tree_sha':tree,'execution_mode':'MANUAL_LOCAL_OR_APPROVED_EXECUTION_NODE_NO_GITHUB_ACTIONS','steps':{'denominator_guard':{'return_code':den_rc},'maven_targeted_junit':{'return_code':maven_rc,'reports':reports,'tests':total,'failures':fail,'errors':err,'skipped':skip}},'claims':{'concrete_evaluator_code_materialized_count':42,'compile_and_targeted_junit_established':all_ok,'dd_authorized_route_execution_mechanics_count':42 if route42 else 0,'dd_schema_validator_execution_mechanics_count':42 if route42 else 0,'qualification_fixture_denominator':173,'qualification_fixture_mechanics_executed_count':173 if base160 and ext13 else 0,'qualification_fixture_mechanics_established':base160 and ext13,'dd042_minimum_adversarial_fixture_mechanics_count':6 if ext13 else 0,'independent_evaluator_qualification_count':0,'semantic_runtime_evidence_count':0,'design_lock':False},'limitations':['Denominator guard may remain HOLD solely because independent qualification is intentionally external.','Synthetic fixture mechanics are not independent qualification.','Route mechanics are not target runtime evidence.'],'verdict':'PASS_NONFINAL_EXECUTION_MECHANICS_ONLY' if all_ok else 'HOLD_NONFINAL','github_actions_authority':False,'final_claim_allowed':False}
x=dict(rec); rec['receipt_digest']=hashlib.sha256(json.dumps(x,sort_keys=True,separators=(',',':')).encode()).hexdigest(); out.write_text(json.dumps(rec,indent=2,sort_keys=True)+'\n')
print(json.dumps(rec,sort_keys=True))
raise SystemExit(0 if all_ok else 2)
PY
