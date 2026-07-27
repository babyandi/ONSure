#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROFILE="core"
STATIC_ONLY=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile) PROFILE="${2:-}"; shift 2 ;;
    --static-only) STATIC_ONLY=true; shift ;;
    -h|--help)
      cat <<'EOF'
usage: bash scripts/onsure-one-shot.sh [--profile core|oruda] [--static-only]

core        Core-oriented validation with Generic/AI scope.
oruda       Core-oriented validation plus the optional ORUDA adapter fixture pack.
static-only Tracked source, structured contracts, granular requirements, surface parity, links and shell syntax.
EOF
      exit 0
      ;;
    *) echo "ONSURE_ONE_SHOT_FAIL UNKNOWN_ARGUMENT_$1" >&2; exit 64 ;;
  esac
done
[[ "$PROFILE" == "core" || "$PROFILE" == "oruda" ]] || {
  echo "ONSURE_ONE_SHOT_FAIL INVALID_PROFILE_$PROFILE" >&2
  exit 64
}

for command in git bash python3 sha256sum tee cmp; do
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
    "contract": "ONSURE_ONE_SHOT_RESULT_V6",
    "decision": "FAIL",
    "profile": profile,
    "failure": failure,
    "source_commit": source,
    "independent_otester": "NOT_RUN",
    "independent_oaudit": "NOT_RUN",
    "final_lock_allowed": False,
    "production_go": False,
    "commercial_go": False,
}
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  echo "ONSURE_ONE_SHOT_FAIL $code $OUT" >&2
  exit 1
}
require() { command -v "$1" >/dev/null 2>&1 || fail "MISSING_COMMAND_$1"; }

run_step() {
  local id="$1"; shift
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
def digest(path): return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()
body = {
    "contract": "ONSURE_ONE_SHOT_STEP_RECEIPT_V6",
    "step": step,
    "started_at": started,
    "finished_at": finished,
    "exit_code": int(code),
    "source_commit": source,
    "profile": profile,
    "environment_digest": environment,
    "command": command,
    "command_sha256": hashlib.sha256(json.dumps(command, separators=(",", ":")).encode()).hexdigest(),
    "stdout_sha256": digest(stdout),
    "stderr_sha256": digest(stderr),
    "authority_class": "INTERNAL_SELF_VALIDATION",
}
body["receipt_sha256"] = hashlib.sha256(json.dumps(body, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  cat "$OUT/logs/$id.stdout"
  cat "$OUT/logs/$id.stderr" >&2
  [[ $exit_code -eq 0 ]] || fail "STEP_${id}_FAILED"
}

[[ "$HEAD_SHA" =~ ^[0-9a-f]{40,64}$ ]] || fail INVALID_GIT_HEAD
[[ -z "$(git status --porcelain)" ]] || fail WORKTREE_DIRTY_OR_UNTRACKED

python3 scripts/create-source-snapshot.py --output "$OUT/source-start.json"

python3 - "$OUT/environment.json" <<'PY'
import hashlib, json, os, pathlib, platform, subprocess, sys
def capture(command):
    try:
        result = subprocess.run(command, text=True, capture_output=True, check=False)
    except FileNotFoundError:
        return {"command": command, "exit_code": 127, "first_line": "NOT_INSTALLED"}
    lines = (result.stdout or result.stderr).strip().splitlines()
    return {"command": command, "exit_code": result.returncode, "first_line": lines[0] if lines else ""}
body = {
    "contract": "ONSURE_ONE_SHOT_ENVIRONMENT_V5",
    "platform": platform.platform(),
    "machine": platform.machine(),
    "python": sys.version.splitlines()[0],
    "path_sha256": hashlib.sha256(os.environ.get("PATH", "").encode()).hexdigest(),
    "tools": {name: capture(command) for name, command in {
        "git": ["git", "--version"], "bash": ["bash", "--version"],
        "java": ["java", "-version"], "javac": ["javac", "-version"], "maven": ["mvn", "-version"],
        "bwrap": ["bwrap", "--version"], "prlimit": ["prlimit", "--version"],
        "node": ["node", "--version"], "npm": ["npm", "--version"]
    }.items()},
}
pathlib.Path(sys.argv[1]).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
ENVIRONMENT_DIGEST="$(sha256sum "$OUT/environment.json" | awk '{print $1}')"

run_step repository-contracts python3 scripts/validate-repository-contracts.py --output "$OUT/repository-contract-report.json"
run_step codespace-free-static python3 scripts/validate-codespace-free-remediation.py
run_step structured-contracts python3 scripts/validate-structured-contracts.py
run_step module-boundaries python3 scripts/check-module-boundaries.py
run_step product-subrequirements python3 scripts/validate-product-subrequirements.py --self-test
run_step workflow-surface-parity python3 scripts/validate-workflow-surface-parity.py --self-test
run_step vscode-static python3 scripts/validate-vscode-extension.py
run_step atomic-requirements python3 scripts/extract-atomic-requirements.py --output "$OUT/atomic-requirement-candidates.json"
run_step shell-syntax bash scripts/check-shell-syntax.sh

if [[ "$STATIC_ONLY" == true ]]; then
  python3 scripts/create-source-snapshot.py --output "$OUT/source-end.json"
  cmp "$OUT/source-start.json" "$OUT/source-end.json" >/dev/null || fail SOURCE_DRIFT
  python3 - "$OUT/result.json" "$PROFILE" "$HEAD_SHA" "$ENVIRONMENT_DIGEST" <<'PY'
import json, pathlib, sys
path, profile, head, environment = sys.argv[1:]
body = {
    "contract": "ONSURE_ONE_SHOT_RESULT_V6",
    "decision": "NON_FINAL",
    "mode": "STATIC_ONLY",
    "profile": profile,
    "source_commit": head,
    "environment_digest": environment,
    "repository_contracts": "PASS",
    "codespace_free_static_gate": "PASS",
    "structured_contracts": "PASS_OR_LIMITED_BY_OPTIONAL_PACKAGES",
    "module_boundary_static": "PASS",
    "product_subrequirements": "PASS_WITH_KNOWN_GAPS",
    "workflow_surface_parity": "PASS",
    "vscode_static": "PASS_OR_NODE_NOT_RUN",
    "atomic_requirement_candidates": "GENERATED_NONAUTHORITATIVE",
    "shell_syntax": "PASS",
    "maven_junit": "NOT_RUN",
    "runtime_e2e": "NOT_RUN",
    "release_gate": "HOLD",
    "independent_otester": "NOT_RUN",
    "independent_oaudit": "NOT_RUN",
    "final_lock_allowed": False,
    "production_go": False,
    "commercial_go": False,
}
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  (cd "$OUT" && find . -type f ! -name evidence.sha256 -print0 | sort -z | xargs -0 sha256sum > evidence.sha256)
  echo "ONSURE_ONE_SHOT_STATIC_NONFINAL $OUT"
  exit 0
fi

for command in java javac mvn bwrap prlimit timeout; do require "$command"; done
JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || fail JDK17_REQUIRED
export ONSURE_FIXTURE_SANDBOX_MODE=REQUIRED

run_step preflight bash scripts/preflight-local-assurance.sh --profile "$PROFILE"
run_step root-maven-tests mvn -B -ntp test
run_step modular-core-cli-api mvn -B -ntp -f pom-modular.xml \
  -pl modules/onsure-core,modules/onsure-cli,modules/onsure-local-api -am test
run_step python-tests python3 -m unittest discover -s tests -p 'test_*.py'
run_step universal-harness-twice bash scripts/run-universal-harness-twice.sh \
  one-shot-internal-1 one-shot-internal-2 local-jdk17

ORUDA_FIXTURES="NOT_RUN"
if [[ "$PROFILE" == "oruda" ]]; then
  run_step optional-oruda-validator-fixtures bash scripts/run-product-platform-e2e.sh
  ORUDA_FIXTURES="PASS_NONFINAL"
fi

python3 scripts/create-source-snapshot.py --output "$OUT/source-end.json"
cmp "$OUT/source-start.json" "$OUT/source-end.json" >/dev/null || fail SOURCE_DRIFT

python3 - "$OUT/result.json" "$PROFILE" "$HEAD_SHA" "$ENVIRONMENT_DIGEST" "$ORUDA_FIXTURES" <<'PY'
import json, pathlib, sys
path, profile, head, environment, oruda = sys.argv[1:]
body = {
    "contract": "ONSURE_ONE_SHOT_RESULT_V6",
    "decision": "NON_FINAL",
    "release_gate": "HOLD_PRODUCT_FULL_CHAIN_AND_INDEPENDENT_ASSURANCE_NOT_RUN",
    "mode": "FULL_INTERNAL_AUTOMATION",
    "profile": profile,
    "source_commit": head,
    "environment_digest": environment,
    "repository_contracts": "PASS",
    "codespace_free_static_gate": "PASS",
    "structured_contracts": "PASS",
    "product_subrequirements": "PASS_WITH_KNOWN_GAPS",
    "workflow_surface_parity": "PASS",
    "root_maven_junit": "PASS",
    "modular_core_cli_api": "PASS_NONFINAL",
    "python_regression": "PASS",
    "internal_universal_harness": "PASS_NONFINAL",
    "optional_oruda_fixtures": oruda,
    "vscode_extension_host_full_chain": "NOT_RUN",
    "web_payment_provider_full_chain": "NOT_RUN",
    "independent_otester": "NOT_RUN",
    "independent_oaudit": "NOT_RUN",
    "final_lock_allowed": False,
    "production_go": False,
    "commercial_go": False,
}
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
(cd "$OUT" && find . -type f ! -name evidence.sha256 -print0 | sort -z | xargs -0 sha256sum > evidence.sha256)
echo "ONSURE_ONE_SHOT_SELF_VALIDATION_NONFINAL $OUT"
exit 0
