#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$ROOT"
MODE="full"; PROFILE="core"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) MODE="${2:-}"; shift 2 ;;
    --profile) PROFILE="${2:-}"; shift 2 ;;
    *) echo "usage: bash scripts/onsure-local-gate.sh [--mode static|full] [--profile core|oruda]" >&2; exit 64 ;;
  esac
done
[[ "$MODE" == "static" || "$MODE" == "full" ]] || { echo "ONSURE_LOCAL_GATE_FAIL INVALID_MODE_$MODE" >&2; exit 64; }
[[ "$PROFILE" == "core" || "$PROFILE" == "oruda" ]] || { echo "ONSURE_LOCAL_GATE_FAIL INVALID_PROFILE_$PROFILE" >&2; exit 64; }
for command in git bash python3 sha256sum cmp; do command -v "$command" >/dev/null 2>&1 || { echo "ONSURE_LOCAL_GATE_FAIL MISSING_COMMAND_$command" >&2; exit 69; }; done
[[ -z "$(git status --porcelain)" ]] || { echo "ONSURE_LOCAL_GATE_FAIL WORKTREE_DIRTY_OR_UNTRACKED" >&2; exit 72; }
COUNT_AUTHORITY="contracts/omission-failure-injection-counts.v1.json"
[[ -f "$COUNT_AUTHORITY" ]] || { echo "ONSURE_LOCAL_GATE_FAIL FAILURE_COUNT_AUTHORITY_MISSING" >&2; exit 72; }
FAILURE_INJECTION_TOTAL="$(python3 - "$COUNT_AUTHORITY" <<'PY'
import json,sys
body=json.load(open(sys.argv[1],encoding='utf-8')); counts=body.get('counts',{}); total=body.get('total')
if body.get('contract')!='ONSURE_OMISSION_FAILURE_INJECTION_COUNTS_V1' or total!=sum(counts.values()): raise SystemExit(1)
print(total)
PY
)" || { echo "ONSURE_LOCAL_GATE_FAIL FAILURE_COUNT_AUTHORITY_INVALID" >&2; exit 72; }
STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"; OUT="${ONSURE_LOCAL_GATE_OUTPUT:-$ROOT/.onsure/local-gate/$STAMP}"
mkdir -p "$OUT/logs" "$OUT/artifacts"
python3 scripts/create-source-snapshot.py --output "$OUT/source-start.json"
VALIDATION_PYTHON="python3"
if ! python3 -c 'import jsonschema, yaml' >/dev/null 2>&1; then
  python3 -m venv "$OUT/validation-venv"
  "$OUT/validation-venv/bin/python" -m pip install --require-hashes --disable-pip-version-check --no-input -r requirements-validation.txt > "$OUT/logs/validation-dependency-install.log" 2>&1
  VALIDATION_PYTHON="$OUT/validation-venv/bin/python"
fi
run_step(){
  local name="$1"; shift
  local log="$OUT/logs/$name.log"
  "$@" > "$log" 2>&1 || { echo "ONSURE_LOCAL_GATE_FAIL $name" >&2; tail -n 240 "$log" >&2 || true; exit 1; }
  python3 - "$OUT/artifacts/step-$name.json" "$name" "$log" "$(git rev-parse HEAD)" <<'PY'
import hashlib,json,pathlib,sys
path,name,log,source=sys.argv[1:]
log_path=pathlib.Path(log)
body={"contract":"ONSURE_LOCAL_GATE_STEP_RECEIPT_V1","step":name,"source_commit":source,
      "exit_code":0,"log_sha256":hashlib.sha256(log_path.read_bytes()).hexdigest(),"decision":"PASS"}
body["receipt_sha256"]=hashlib.sha256(json.dumps(body,sort_keys=True,separators=(",",":")).encode()).hexdigest()
pathlib.Path(path).write_text(json.dumps(body,indent=2,sort_keys=True)+"\n",encoding="utf-8")
PY
}
run_step structured-contracts "$VALIDATION_PYTHON" scripts/validate-structured-contracts.py --require-full
run_step design-coverage python3 scripts/validate-design-coverage.py --matrix status/design-capability-coverage.v2.json --root . --self-test
run_step product-subrequirements python3 scripts/validate-product-subrequirements.py --self-test
run_step final-product-requirements python3 scripts/validate-final-product-requirements.py --self-test
run_step final-acceptance-coverage python3 scripts/validate-final-acceptance-coverage.py --self-test
run_step mvp-acceptance python3 scripts/validate-mvp-acceptance-coverage.py --self-test
run_step mvp-status-consistency python3 scripts/validate-mvp-status-consistency.py
run_step workflow-surface-parity python3 scripts/validate-workflow-surface-parity.py --self-test
run_step critical-callpaths python3 scripts/validate-critical-callpaths.py --self-test
run_step validation-case-registry-static python3 scripts/validate-validation-case-registry.py --static-only
run_step status-consistency python3 scripts/validate-status-consistency.py
run_step automation-boundary python3 scripts/validate-ci-boundary.py
run_step verification-claims python3 scripts/validate-verification-claims.py
run_step module-boundary python3 scripts/check-module-boundaries.py
run_step repository-contracts python3 scripts/validate-repository-contracts.py
run_step codespace-free-remediation "$VALIDATION_PYTHON" scripts/validate-codespace-free-remediation.py
run_step omission-tests python3 -m unittest tests.test_omission_detection_gates -v
run_step automation-tests python3 -m unittest tests.test_ci_boundary -v
run_step verification-claim-tests python3 -m unittest tests.test_verification_claims -v
run_step full-python-regression python3 -m unittest discover -s tests -p 'test_*.py' -v
run_step shell-syntax bash scripts/check-shell-syntax.sh
if [[ "$MODE" == "full" ]]; then
  for command in java javac mvn bwrap prlimit timeout node npm; do command -v "$command" >/dev/null 2>&1 || { echo "ONSURE_LOCAL_GATE_FAIL MISSING_COMMAND_$command" >&2; exit 69; }; done
  JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"; JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
  [[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || { echo "ONSURE_LOCAL_GATE_FAIL JDK17_REQUIRED" >&2; exit 70; }
  export ONSURE_FIXTURE_SANDBOX_MODE=REQUIRED; unset ONSURE_FIXTURE_SANDBOX_BACKEND || true
  run_step java-root mvn -B -ntp -q test
  run_step java-modular mvn -B -ntp -q -f pom-modular.xml test
  run_step core-without-oruda mvn -B -ntp -q -f pom-modular.xml -pl modules/onsure-core -am test
  run_step sandbox-boundary bash scripts/test-fixture-sandbox-boundary.sh
  run_step generic-ai-e2e mvn -B -ntp -q -Dtest=ValidationPlatformE2ETest test
  run_step validation-case-registry-runtime python3 scripts/validate-validation-case-registry.py \
    --surefire-dir "$ROOT/target/surefire-reports" \
    --surefire-dir "$ROOT/modules/onsure-core/target/surefire-reports" \
    --surefire-dir "$ROOT/modules/onsure-adapter-oruda/target/surefire-reports" \
    --receipt "$OUT/artifacts/validation-case-execution-receipt.json"
  if [[ "$PROFILE" == "oruda" ]]; then
    run_step oruda-module mvn -B -ntp -q -f pom-modular.xml -pl modules/onsure-adapter-oruda -am test
    run_step oruda-e2e mvn -B -ntp -q -Dtest=io.onsure.platform.oruda.OrudaMvf001E2ETest test
  fi
  EXT_BUILD="$OUT/vscode-extension-build"; cp -R "$ROOT/vscode-extension" "$EXT_BUILD"
  (cd "$EXT_BUILD"; npm ci --ignore-scripts --no-audit --no-fund > "$OUT/logs/vscode-npm-install.log" 2>&1; npm run check > "$OUT/logs/vscode-node-check.log" 2>&1; npm run package -- --out "$OUT/artifacts/onsure.vsix" > "$OUT/logs/vscode-package.log" 2>&1)
  [[ -s "$OUT/artifacts/onsure.vsix" ]] || { echo "ONSURE_LOCAL_GATE_FAIL VSIX_MISSING" >&2; exit 74; }
  sha256sum "$OUT/artifacts/onsure.vsix" > "$OUT/artifacts/onsure.vsix.sha256"
fi
python3 scripts/create-source-snapshot.py --output "$OUT/source-end.json"
cmp "$OUT/source-start.json" "$OUT/source-end.json" >/dev/null || { echo "ONSURE_LOCAL_GATE_FAIL SOURCE_DRIFT" >&2; exit 73; }
python3 - "$OUT/local-gate-result.json" "$MODE" "$PROFILE" "$COUNT_AUTHORITY" "$FAILURE_INJECTION_TOTAL" "$OUT/artifacts" "$OUT/logs" "$OUT/source-start.json" "$OUT/source-end.json" <<'PY'
import hashlib,json,pathlib,sys
path,mode,profile,authority,total,artifact_dir,log_dir,start_path,end_path=sys.argv[1:]
static_steps={
    "structured-contracts","design-coverage","product-subrequirements",
    "final-product-requirements","final-acceptance-coverage","mvp-acceptance",
    "mvp-status-consistency","workflow-surface-parity","critical-callpaths",
    "validation-case-registry-static","status-consistency","automation-boundary",
    "verification-claims","module-boundary","repository-contracts",
    "codespace-free-remediation","omission-tests","automation-tests",
    "verification-claim-tests","full-python-regression","shell-syntax",
}
full_steps={
    "java-root","java-modular","core-without-oruda","sandbox-boundary",
    "generic-ai-e2e","validation-case-registry-runtime",
}
if profile=="oruda":
    full_steps |= {"oruda-module","oruda-e2e"}
expected=static_steps | (full_steps if mode=="full" else set())
source_start=json.loads(pathlib.Path(start_path).read_text(encoding="utf-8"))
source_end=json.loads(pathlib.Path(end_path).read_text(encoding="utf-8"))
if source_start != source_end:
    raise SystemExit("SOURCE_SNAPSHOT_MISMATCH")
source_commit=source_start.get("commit_sha")
receipts=[]
seen=set()
for receipt_path in sorted(pathlib.Path(artifact_dir).glob("step-*.json")):
    receipt=json.loads(receipt_path.read_text(encoding="utf-8"))
    claimed=receipt.pop("receipt_sha256")
    actual=hashlib.sha256(json.dumps(receipt,sort_keys=True,separators=(",",":")).encode()).hexdigest()
    if claimed!=actual or receipt.get("decision")!="PASS" or receipt.get("exit_code")!=0:
        raise SystemExit("INVALID_STEP_RECEIPT:"+receipt_path.name)
    step=receipt.get("step")
    if step in seen:
        raise SystemExit("DUPLICATE_STEP_RECEIPT:"+str(step))
    seen.add(step)
    log_path=pathlib.Path(log_dir)/(str(step)+".log")
    if not log_path.is_file():
        raise SystemExit("STEP_LOG_MISSING:"+str(step))
    log_sha=hashlib.sha256(log_path.read_bytes()).hexdigest()
    if receipt.get("log_sha256") != log_sha:
        raise SystemExit("STEP_LOG_HASH_MISMATCH:"+str(step))
    if receipt.get("source_commit") != source_commit:
        raise SystemExit("STEP_SOURCE_COMMIT_MISMATCH:"+str(step))
    receipts.append({"path":"artifacts/"+receipt_path.name,"sha256":claimed,"step":receipt["step"]})
if seen != expected:
    raise SystemExit("STEP_SET_MISMATCH:missing="+",".join(sorted(expected-seen))+":unexpected="+",".join(sorted(seen-expected)))
body={"contract":"ONSURE_LOCAL_GATE_RESULT_V9","mode":mode,"profile":profile,"decision":"PASS_NONFINAL","authority_class":"LOCAL_SELF_VALIDATION",
      "legacy_product_decomposition":{"contract_validation":"PASS","runtime_verification":"NOT_RUN"},
      "legacy_mvp_acceptance":{"contract_validation":"PASS","runtime_verification":"NOT_RUN"},
      "final_product_requirement_coverage":{"registered":22,"runtime_verification":"NOT_RUN"},
      "final_acceptance_coverage":{"registered":62,"runtime_verification":"NOT_RUN"},
      "source_snapshot_sha256":hashlib.sha256(pathlib.Path(start_path).read_bytes()).hexdigest(),
      "step_receipts":receipts,"step_receipt_count":len(receipts),
      "two_consecutive_real_repository_runs":"NOT_RUN","validation_case_execution_receipt":"artifacts/validation-case-execution-receipt.json" if mode=="full" else "NOT_RUN_STATIC_MODE","failure_injection_authority":authority,"registered_failure_injections":int(total),"github_actions":"DISABLED","independent_otester":"NOT_RUN","independent_oaudit":"NOT_RUN","final_eligibility":"BLOCKED","final_lock_allowed":False,"production_go":False,"commercial_go":False}
pathlib.Path(path).write_text(json.dumps(body,indent=2,sort_keys=True)+"\n",encoding="utf-8")
PY
find "$OUT" -type f ! -name evidence.sha256 -print0 | sort -z | xargs -0 sha256sum > "$OUT/evidence.sha256"
echo "ONSURE_LOCAL_GATE_PASS_NONFINAL $OUT"
