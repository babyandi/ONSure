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

core        ONSure core-oriented checks. Full Core isolation remains BLOCKED until modules are separated.
oruda       Core-oriented checks plus the optional ORUDA adapter fixture pack.
static-only Tracked JSON/JSONL, traceability, links and shell syntax checks only.
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

for command in git bash python3 sha256sum tee; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "ONSURE_ONE_SHOT_FAIL MISSING_COMMAND_$command" >&2
    exit 69
  }
done

STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
OUT="${ONSURE_ONE_SHOT_OUTPUT:-$ROOT/.onsure/one-shot/$STAMP}"
mkdir -p "$OUT/logs" "$OUT/receipts"
exec > >(tee "$OUT/logs/one-shot.stdout.log") 2> >(tee "$OUT/logs/one-shot.stderr.log" >&2)

HEAD_SHA="$(git rev-parse HEAD 2>/dev/null || true)"
ENVIRONMENT_DIGEST="NOT_CAPTURED"

fail() {
  local code="$1"
  python3 - "$OUT/result.json" "$PROFILE" "$code" "${HEAD_SHA:-UNKNOWN}" <<'PY'
import json, pathlib, sys
path, profile, failure, source = sys.argv[1:]
body = {
    "contract": "ONSURE_ONE_SHOT_RESULT_V2",
    "decision": "FAIL",
    "profile": profile,
    "failure": failure,
    "source_commit": source,
    "final_claim_allowed": False,
}
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
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
  python3 - "$OUT/receipts/$id.json" "$id" "$started" "$finished" "$exit_code" \
    "$OUT/logs/$id.stdout" "$OUT/logs/$id.stderr" "$HEAD_SHA" "$PROFILE" \
    "$ENVIRONMENT_DIGEST" "$@" <<'PY'
import hashlib, json, pathlib, sys
path, step, started, finished, code, stdout, stderr, source, profile, environment, *command = sys.argv[1:]
def digest(value):
    return hashlib.sha256(pathlib.Path(value).read_bytes()).hexdigest()
body = {
    "contract": "ONSURE_ONE_SHOT_STEP_RECEIPT_V2",
    "step": step,
    "started_at": started,
    "finished_at": finished,
    "exit_code": int(code),
    "source_commit": source,
    "profile": profile,
    "environment_digest": environment,
    "command": command,
    "stdout_sha256": digest(stdout),
    "stderr_sha256": digest(stderr),
}
body["receipt_sha256"] = hashlib.sha256(
    json.dumps(body, sort_keys=True, separators=(",", ":")).encode()
).hexdigest()
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  cat "$OUT/logs/$id.stdout"
  cat "$OUT/logs/$id.stderr" >&2
  [[ $exit_code -eq 0 ]] || fail "STEP_${id}_FAILED"
}

[[ "$HEAD_SHA" =~ ^[0-9a-f]{40,64}$ ]] || fail INVALID_GIT_HEAD
WORKTREE_STATUS="$(git status --porcelain)"
[[ -z "$WORKTREE_STATUS" ]] || fail WORKTREE_DIRTY_OR_UNTRACKED

printf '%s\n' "$HEAD_SHA" > "$OUT/source-commit.txt"
git ls-files -s | sha256sum | awk '{print $1}' > "$OUT/tracked-index.sha256"

python3 - "$OUT/environment.json" <<'PY'
import hashlib, json, os, pathlib, platform, subprocess, sys

def capture(command):
    try:
        result = subprocess.run(command, text=True, capture_output=True, check=False)
    except FileNotFoundError:
        return {"command": command, "exit_code": 127, "first_line": "NOT_INSTALLED"}
    text = (result.stdout or result.stderr).strip().splitlines()
    return {"command": command, "exit_code": result.returncode, "first_line": text[0] if text else ""}

body = {
    "contract": "ONSURE_ONE_SHOT_ENVIRONMENT_V1",
    "platform": platform.platform(),
    "machine": platform.machine(),
    "python": sys.version.splitlines()[0],
    "path_sha256": hashlib.sha256(os.environ.get("PATH", "").encode()).hexdigest(),
    "tools": {
        "git": capture(["git", "--version"]),
        "bash": capture(["bash", "--version"]),
        "java": capture(["java", "-version"]),
        "javac": capture(["javac", "-version"]),
        "maven": capture(["mvn", "-version"]),
    },
}
pathlib.Path(sys.argv[1]).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
ENVIRONMENT_DIGEST="$(sha256sum "$OUT/environment.json" | awk '{print $1}')"

run_step repository-contracts python3 scripts/validate-repository-contracts.py --output "$OUT/repository-contract-report.json"
run_step shell-syntax bash scripts/check-shell-syntax.sh

if [[ "$STATIC_ONLY" == true ]]; then
  python3 - "$OUT/result.json" "$PROFILE" "$HEAD_SHA" "$ENVIRONMENT_DIGEST" <<'PY'
import json, pathlib, sys
path, profile, head, environment = sys.argv[1:]
body = {
    "contract": "ONSURE_ONE_SHOT_RESULT_V2",
    "decision": "NON_FINAL",
    "mode": "STATIC_ONLY",
    "profile": profile,
    "source_commit": head,
    "environment_digest": environment,
    "repository_contracts": "PASS",
    "shell_syntax": "PASS",
    "json_schema_instance_validation": "NOT_RUN",
    "yaml_validation": "NOT_RUN",
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

for command in java javac mvn cmp; do
  require "$command"
done
JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || fail JDK17_REQUIRED

run_step preflight bash scripts/preflight-local-assurance.sh --profile "$PROFILE"
run_step maven-tests mvn -B -ntp test
run_step python-tests python3 -m unittest discover -s tests -p 'test_*.py'
run_step universal-harness-twice bash scripts/run-universal-harness-twice.sh \
  one-shot-internal-1 one-shot-internal-2 local-jdk17

ORUDA_FIXTURES="NOT_RUN"
if [[ "$PROFILE" == "oruda" ]]; then
  run_step optional-oruda-validator-fixtures bash scripts/run-product-platform-e2e.sh
  ORUDA_FIXTURES="PASS"
fi

python3 - "$OUT/result.json" "$PROFILE" "$HEAD_SHA" "$ENVIRONMENT_DIGEST" "$ORUDA_FIXTURES" <<'PY'
import json, pathlib, sys
path, profile, head, environment, oruda = sys.argv[1:]
body = {
    "contract": "ONSURE_ONE_SHOT_RESULT_V2",
    "decision": "BLOCKED",
    "blocking_reason": "P0_CORE_COMPILE_ISOLATION_AND_ATOMIC_TRACEABILITY_OPEN",
    "mode": "FULL_AVAILABLE_AUTOMATION",
    "profile": profile,
    "source_commit": head,
    "environment_digest": environment,
    "repository_contracts": "PASS",
    "shell_syntax": "PASS",
    "maven_junit": "PASS",
    "python_regression": "PASS",
    "internal_universal_harness": "PASS_NONFINAL",
    "core_generic_ai_fixture_e2e": "NOT_SEPARATELY_PROVEN",
    "core_compile_without_oruda": "NOT_PROVEN_SINGLE_MODULE",
    "optional_oruda_fixtures": oruda,
    "vscode_product_full_chain": "NOT_RUN",
    "web_product_full_chain": "NOT_RUN",
    "independent_otester": "NOT_RUN",
    "independent_oaudit": "NOT_RUN",
    "final_lock_allowed": False,
    "final_claim_allowed": False,
}
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

(cd "$OUT" && find . -type f ! -name evidence.sha256 -print0 | sort -z | xargs -0 sha256sum > evidence.sha256)
echo "ONSURE_ONE_SHOT_BLOCKED_NONFINAL $OUT" >&2
exit 75
