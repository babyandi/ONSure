#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROFILE="core"
STATIC_ONLY=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile)
      PROFILE="${2:-}"
      shift 2
      ;;
    --static-only)
      STATIC_ONLY=true
      shift
      ;;
    -h|--help)
      cat <<'EOF'
usage: bash scripts/onsure-one-shot.sh [--profile core|oruda] [--static-only]

core        ONSure standalone core checks; ORUDA is not required.
oruda       Core checks plus the optional ORUDA adapter and target pack.
static-only Contract, schema, traceability, link and shell syntax checks only.
EOF
      exit 0
      ;;
    *)
      echo "ONSURE_ONE_SHOT_FAIL UNKNOWN_ARGUMENT_$1" >&2
      exit 64
      ;;
  esac
done

[[ "$PROFILE" == "core" || "$PROFILE" == "oruda" ]] || {
  echo "ONSURE_ONE_SHOT_FAIL INVALID_PROFILE_$PROFILE" >&2
  exit 64
}

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="${ONSURE_ONE_SHOT_OUTPUT:-$ROOT/.onsure/one-shot/$STAMP}"
mkdir -p "$OUT/logs" "$OUT/receipts"

exec > >(tee "$OUT/logs/one-shot.stdout.log") 2> >(tee "$OUT/logs/one-shot.stderr.log" >&2)

fail() {
  local code="$1"
  printf '{"contract":"ONSURE_ONE_SHOT_RESULT_V1","decision":"FAIL","profile":"%s","failure":"%s","final_claim_allowed":false}\n' \
    "$PROFILE" "$code" > "$OUT/result.json"
  echo "ONSURE_ONE_SHOT_FAIL $code $OUT" >&2
  exit 1
}

require() { command -v "$1" >/dev/null 2>&1 || fail "MISSING_COMMAND_$1"; }
run_step() {
  local id="$1"
  shift
  echo "=== $id ==="
  local started finished exit_code
  started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  set +e
  "$@" >"$OUT/logs/$id.stdout" 2>"$OUT/logs/$id.stderr"
  exit_code=$?
  set -e
  finished="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  python3 - "$OUT/receipts/$id.json" "$id" "$started" "$finished" "$exit_code" "$OUT/logs/$id.stdout" "$OUT/logs/$id.stderr" <<'PY'
import hashlib, json, pathlib, sys
path, step, started, finished, code, stdout, stderr = sys.argv[1:]
def digest(p): return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()
body = {
    "contract": "ONSURE_ONE_SHOT_STEP_RECEIPT_V1",
    "step": step,
    "started_at": started,
    "finished_at": finished,
    "exit_code": int(code),
    "stdout_sha256": digest(stdout),
    "stderr_sha256": digest(stderr),
}
body["receipt_sha256"] = hashlib.sha256(json.dumps(body, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  cat "$OUT/logs/$id.stdout"
  cat "$OUT/logs/$id.stderr" >&2
  [[ $exit_code -eq 0 ]] || fail "STEP_${id}_FAILED"
}

require git
require bash
require python3
require sha256sum

HEAD_SHA="$(git rev-parse HEAD)"
[[ "$HEAD_SHA" =~ ^[0-9a-f]{40,64}$ ]] || fail INVALID_GIT_HEAD
TRACKED_STATUS="$(git status --porcelain --untracked-files=no)"
[[ -z "$TRACKED_STATUS" ]] || fail TRACKED_WORKTREE_DIRTY

printf '%s\n' "$HEAD_SHA" > "$OUT/source-commit.txt"
git ls-files -s | sha256sum | awk '{print $1}' > "$OUT/tracked-index.sha256"

run_step repository-contracts python3 scripts/validate-repository-contracts.py --output "$OUT/repository-contract-report.json"

while IFS= read -r script; do
  bash -n "$script" || fail "SHELL_SYNTAX_${script//\//_}"
done < <(find scripts -maxdepth 1 -type f -name '*.sh' -print | sort)

echo "SHELL_SYNTAX_PASS" > "$OUT/logs/shell-syntax.stdout"
: > "$OUT/logs/shell-syntax.stderr"

if [[ "$STATIC_ONLY" == true ]]; then
  python3 - "$OUT/result.json" "$PROFILE" "$HEAD_SHA" <<'PY'
import json, pathlib, sys
path, profile, head = sys.argv[1:]
body = {
    "contract": "ONSURE_ONE_SHOT_RESULT_V1",
    "decision": "NON_FINAL",
    "mode": "STATIC_ONLY",
    "profile": profile,
    "source_commit": head,
    "repository_contracts": "PASS",
    "shell_syntax": "PASS",
    "maven_junit": "NOT_RUN",
    "python_regression": "NOT_RUN",
    "runtime_e2e": "NOT_RUN",
    "independent_otester": "NOT_RUN",
    "independent_oaudit": "NOT_RUN",
    "final_claim_allowed": False,
}
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  (cd "$OUT" && find . -type f ! -name evidence.sha256 -print0 | sort -z | xargs -0 sha256sum > evidence.sha256)
  echo "ONSURE_ONE_SHOT_STATIC_NONFINAL $OUT"
  exit 0
fi

require java
require javac
require mvn
JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || fail JDK17_REQUIRED

run_step preflight bash scripts/preflight-local-assurance.sh --profile "$PROFILE"
run_step maven-tests mvn -B -ntp test
run_step python-tests python3 -m unittest discover -s tests -p 'test_*.py'

if [[ -x scripts/run-universal-harness-twice.sh || -f scripts/run-universal-harness-twice.sh ]]; then
  run_step universal-harness-twice bash scripts/run-universal-harness-twice.sh \
    one-shot-independent-1 one-shot-independent-2 local-jdk17
fi

if [[ "$PROFILE" == "oruda" ]]; then
  run_step optional-oruda-product-fixtures bash scripts/run-product-platform-e2e.sh
  run_step optional-onguard-fixed-target bash scripts/run-onguard-fixed-target-local.sh --core
fi

python3 - "$OUT/result.json" "$PROFILE" "$HEAD_SHA" <<'PY'
import json, pathlib, sys
path, profile, head = sys.argv[1:]
body = {
    "contract": "ONSURE_ONE_SHOT_RESULT_V1",
    "decision": "SELF_VALIDATION_NONFINAL",
    "mode": "FULL_AVAILABLE_AUTOMATION",
    "profile": profile,
    "source_commit": head,
    "repository_contracts": "PASS",
    "shell_syntax": "PASS",
    "maven_junit": "PASS",
    "python_regression": "PASS",
    "available_runtime_harness": "PASS",
    "vscode_product_full_chain": "NOT_RUN",
    "web_product_full_chain": "NOT_RUN",
    "independent_otester": "NOT_RUN",
    "independent_oaudit": "NOT_RUN",
    "final_claim_allowed": False,
}
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

(cd "$OUT" && find . -type f ! -name evidence.sha256 -print0 | sort -z | xargs -0 sha256sum > evidence.sha256)
echo "ONSURE_ONE_SHOT_SELF_VALIDATION_NONFINAL $OUT"
