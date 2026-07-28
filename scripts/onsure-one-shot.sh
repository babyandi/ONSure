#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$ROOT"
PROFILE="core"; MODE="full"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile) PROFILE="${2:-}"; shift 2 ;;
    --static-only) MODE="static"; shift ;;
    -h|--help) echo "usage: bash scripts/onsure-one-shot.sh [--profile core|oruda] [--static-only]"; exit 0 ;;
    *) echo "ONSURE_ONE_SHOT_FAIL UNKNOWN_ARGUMENT_$1" >&2; exit 64 ;;
  esac
done
[[ "$PROFILE" == "core" || "$PROFILE" == "oruda" ]] || { echo "ONSURE_ONE_SHOT_FAIL INVALID_PROFILE_$PROFILE" >&2; exit 64; }
for command in git bash python3 sha256sum cmp tee; do command -v "$command" >/dev/null 2>&1 || { echo "ONSURE_ONE_SHOT_FAIL MISSING_COMMAND_$command" >&2; exit 69; }; done
[[ -z "$(git status --porcelain)" ]] || { echo "ONSURE_ONE_SHOT_FAIL WORKTREE_DIRTY_OR_UNTRACKED" >&2; exit 72; }
HEAD_SHA="$(git rev-parse HEAD)"; [[ "$HEAD_SHA" =~ ^[0-9a-f]{40,64}$ ]] || { echo "ONSURE_ONE_SHOT_FAIL INVALID_GIT_HEAD" >&2; exit 72; }
COUNT_AUTHORITY="contracts/omission-failure-injection-counts.v1.json"
FAILURE_INJECTION_TOTAL="$(python3 - "$COUNT_AUTHORITY" <<'PY'
import json,sys
body=json.load(open(sys.argv[1],encoding='utf-8')); counts=body.get('counts',{}); total=body.get('total')
if body.get('contract')!='ONSURE_OMISSION_FAILURE_INJECTION_COUNTS_V1' or total!=sum(counts.values()): raise SystemExit(1)
print(total)
PY
)" || { echo "ONSURE_ONE_SHOT_FAIL FAILURE_COUNT_AUTHORITY_INVALID" >&2; exit 72; }
STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"; OUT="${ONSURE_ONE_SHOT_OUTPUT:-$ROOT/.onsure/one-shot/$STAMP}"
mkdir -p "$OUT/logs" "$OUT/receipts"
python3 scripts/create-source-snapshot.py --output "$OUT/source-start.json"
python3 - "$OUT/environment.json" <<'PY'
import hashlib,json,os,pathlib,platform,subprocess,sys
def capture(command):
    try: result=subprocess.run(command,text=True,capture_output=True,check=False,timeout=15)
    except FileNotFoundError: return {"command":command,"exit_code":127,"first_line":"NOT_INSTALLED"}
    except subprocess.TimeoutExpired: return {"command":command,"exit_code":124,"first_line":"TIMEOUT"}
    lines=(result.stdout or result.stderr).strip().splitlines()
    return {"command":command,"exit_code":result.returncode,"first_line":lines[0] if lines else ""}
body={"contract":"ONSURE_ONE_SHOT_ENVIRONMENT_V8","platform":platform.platform(),"machine":platform.machine(),"python":sys.version.splitlines()[0],"path_sha256":hashlib.sha256(os.environ.get("PATH","").encode()).hexdigest(),"tools":{name:capture(command) for name,command in {"git":["git","--version"],"bash":["bash","--version"],"java":["java","-version"],"javac":["javac","-version"],"maven":["mvn","-version"],"bwrap":["bwrap","--version"],"prlimit":["prlimit","--version"],"node":["node","--version"],"npm":["npm","--version"]}.items()}}
pathlib.Path(sys.argv[1]).write_text(json.dumps(body,indent=2,sort_keys=True)+"\n",encoding="utf-8")
PY
ENVIRONMENT_DIGEST="$(sha256sum "$OUT/environment.json" | awk '{print $1}')"
set +e
ONSURE_LOCAL_GATE_OUTPUT="$OUT/local-gate" bash scripts/onsure-local-gate.sh --mode "$MODE" --profile "$PROFILE" > >(tee "$OUT/logs/local-gate.stdout.log") 2> >(tee "$OUT/logs/local-gate.stderr.log" >&2)
GATE_EXIT=$?
set -e
python3 scripts/create-source-snapshot.py --output "$OUT/source-end.json"
cmp "$OUT/source-start.json" "$OUT/source-end.json" >/dev/null || { echo "ONSURE_ONE_SHOT_FAIL SOURCE_DRIFT" >&2; exit 73; }
python3 - "$OUT/receipts/local-gate-step.json" "$HEAD_SHA" "$PROFILE" "$MODE" "$ENVIRONMENT_DIGEST" "$GATE_EXIT" "$OUT/logs/local-gate.stdout.log" "$OUT/logs/local-gate.stderr.log" <<'PY'
import hashlib,json,pathlib,sys
path,source,profile,mode,environment,exit_code,stdout,stderr=sys.argv[1:]
def digest(value): return hashlib.sha256(pathlib.Path(value).read_bytes()).hexdigest()
body={"contract":"ONSURE_ONE_SHOT_STEP_RECEIPT_V10","step":"LOCAL_GATE_AUTHORITY","source_commit":source,"profile":profile,"mode":mode,"environment_digest":environment,"exit_code":int(exit_code),"stdout_sha256":digest(stdout),"stderr_sha256":digest(stderr),"command":["bash","scripts/onsure-local-gate.sh","--mode",mode,"--profile",profile],"authority_class":"INTERNAL_SELF_VALIDATION"}
body["receipt_sha256"]=hashlib.sha256(json.dumps(body,sort_keys=True,separators=(",",":")).encode()).hexdigest()
pathlib.Path(path).write_text(json.dumps(body,indent=2,sort_keys=True)+"\n",encoding="utf-8")
PY
python3 - "$OUT/result.json" "$HEAD_SHA" "$PROFILE" "$MODE" "$ENVIRONMENT_DIGEST" "$GATE_EXIT" "$COUNT_AUTHORITY" "$FAILURE_INJECTION_TOTAL" <<'PY'
import json,pathlib,sys
path,source,profile,mode,environment,exit_code,authority,total=sys.argv[1:];code=int(exit_code)
body={"contract":"ONSURE_ONE_SHOT_RESULT_V13","decision":"NON_FINAL" if code==0 else "FAIL","source_commit":source,"profile":profile,"mode":mode.upper(),"environment_digest":environment,"local_gate_exit":code,"local_gate_authority":True,"product_subrequirements":"CONTRACT_PASS_WITH_40_KNOWN_INCOMPLETE" if code==0 else "FAIL","mvp_acceptance_contract":"PASS_WITH_ALL_11_ITEMS_NOT_RUN" if code==0 else "FAIL","mvp_full_chain":"NOT_RUN","two_consecutive_real_repository_runs":"NOT_RUN","workflow_surface_parity":"PASS" if code==0 else "FAIL","critical_callpaths":"PASS" if code==0 else "FAIL","failure_injection_authority":authority,"registered_failure_injections":int(total),"release_gate":"HOLD","independent_otester":"NOT_RUN","independent_oaudit":"NOT_RUN","final_lock_allowed":False,"production_go":False,"commercial_go":False}
pathlib.Path(path).write_text(json.dumps(body,indent=2,sort_keys=True)+"\n",encoding="utf-8")
PY
find "$OUT" -type f ! -name evidence.sha256 -print0 | sort -z | xargs -0 sha256sum > "$OUT/evidence.sha256"
if [[ $GATE_EXIT -ne 0 ]]; then echo "ONSURE_ONE_SHOT_FAIL LOCAL_GATE_EXIT_$GATE_EXIT $OUT" >&2; exit "$GATE_EXIT"; fi
echo "ONSURE_ONE_SHOT_SELF_VALIDATION_NONFINAL $OUT"
exit 0
