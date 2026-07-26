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

core        Standalone Core contracts, modules, tests, Generic/AI fixtures and internal evidence.
oruda       Core flow plus the optional ORUDA adapter module and fixtures.
static-only All tracked design, contract, schema, atomic traceability and shell checks; no Java runtime.
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
mkdir -p "$OUT/logs" "$OUT/receipts" "$OUT/atomic-traceability"
exec > >(tee "$OUT/logs/one-shot.stdout.log") \
  2> >(tee "$OUT/logs/one-shot.stderr.log" >&2)

HEAD_SHA="$(git rev-parse HEAD 2>/dev/null || true)"
START_TREE_SHA256="NOT_CAPTURED"
START_POLICY_SHA256="NOT_CAPTURED"
ENVIRONMENT_DIGEST="NOT_CAPTURED"
PYTHON_VALIDATOR="python3"

fail() {
  local code="$1"
  python3 - "$OUT/result.json" "$PROFILE" "$code" "${HEAD_SHA:-UNKNOWN}" <<'PY'
import json, pathlib, sys
path, profile, failure, source = sys.argv[1:]
body = {
    "contract": "ONSURE_ONE_SHOT_RESULT_V3",
    "decision": "FAIL",
    "profile": profile,
    "failure": failure,
    "source_commit": source,
    "assurance_class": "SELF_VALIDATION_NONFINAL",
    "independent_otester": "NOT_RUN",
    "independent_oaudit": "NOT_RUN",
    "final_lock_allowed": False,
    "production_go": False,
    "commercial_go": False,
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
    "$ENVIRONMENT_DIGEST" "$START_TREE_SHA256" "$START_POLICY_SHA256" "$@" <<'PY'
import hashlib, json, pathlib, sys
(path, step, started, finished, code, stdout, stderr, source, profile,
 environment, tree, policy, *command) = sys.argv[1:]
def digest(value):
    return hashlib.sha256(pathlib.Path(value).read_bytes()).hexdigest()
body = {
    "contract": "ONSURE_ONE_SHOT_STEP_RECEIPT_V3",
    "step": step,
    "started_at": started,
    "finished_at": finished,
    "exit_code": int(code),
    "source_commit": source,
    "source_tree_sha256": tree,
    "policy_sha256": policy,
    "profile": profile,
    "environment_digest": environment,
    "command": command,
    "stdout_sha256": digest(stdout),
    "stderr_sha256": digest(stderr),
    "assurance_class": "SELF_VALIDATION_NONFINAL",
    "independent_authority": False,
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

[[ "$HEAD_SHA" =~ ^[0-9a-f]{40}$|^[0-9a-f]{64}$ ]] || fail INVALID_GIT_HEAD
[[ -z "$(git status --porcelain --untracked-files=all)" ]] || fail WORKTREE_DIRTY_OR_UNTRACKED
printf '%s\n' "$HEAD_SHA" > "$OUT/source-commit.txt"
git ls-files -s | sha256sum | awk '{print $1}' > "$OUT/tracked-index.sha256"
START_TREE_SHA256="$(python3 - <<'PY'
import hashlib, pathlib, subprocess
root = pathlib.Path('.').resolve()
raw = subprocess.run(['git','ls-files','-z'], capture_output=True, check=True).stdout
h = hashlib.sha256()
for item in sorted(value for value in raw.split(b'\0') if value):
    path = root / item.decode()
    h.update(item); h.update(b'\0'); h.update(hashlib.sha256(path.read_bytes()).digest()); h.update(b'\0')
print(h.hexdigest())
PY
)"
START_POLICY_SHA256="$(python3 - <<'PY'
import hashlib, pathlib, subprocess
root = pathlib.Path('.').resolve()
prefixes = ('contracts/','fixtures/design/','docs/security/','findings/')
raw = subprocess.run(['git','ls-files','-z'], capture_output=True, check=True).stdout
h = hashlib.sha256()
for item in sorted(value for value in raw.split(b'\0') if value and value.decode().startswith(prefixes)):
    path = root / item.decode()
    h.update(item); h.update(b'\0'); h.update(hashlib.sha256(path.read_bytes()).digest()); h.update(b'\0')
print(h.hexdigest())
PY
)"
printf '%s\n' "$START_TREE_SHA256" > "$OUT/source-tree.sha256"
printf '%s\n' "$START_POLICY_SHA256" > "$OUT/policy-set.sha256"

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
    "contract": "ONSURE_ONE_SHOT_ENVIRONMENT_V2",
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

run_step repository-contracts python3 scripts/validate-repository-contracts.py \
  --output "$OUT/repository-contract-report.json"
run_step core-isolation-static python3 scripts/validate-core-isolation.py \
  --output "$OUT/core-isolation-report.json"
run_step shell-syntax bash scripts/check-shell-syntax.sh
run_step python-syntax python3 -m compileall -q scripts tests
run_step atomic-traceability python3 scripts/build-atomic-traceability.py \
  --output-dir "$OUT/atomic-traceability"

VENV="$ROOT/.onsure/validation-venv"
if [[ ! -x "$VENV/bin/python" ]]; then
  run_step validation-venv-create python3 -m venv "$VENV"
  run_step validation-dependencies "$VENV/bin/python" -m pip install \
    --disable-pip-version-check -r requirements-validation.txt
fi
PYTHON_VALIDATOR="$VENV/bin/python"
run_step structured-contracts "$PYTHON_VALIDATOR" scripts/validate-structured-contracts.py \
  --output "$OUT/structured-contract-report.json"

ATOMIC_DECISION="$(python3 - "$OUT/atomic-traceability/atomic-traceability-summary.v1.json" <<'PY'
import json, pathlib, sys
print(json.loads(pathlib.Path(sys.argv[1]).read_text())['decision'])
PY
)"
ATOMIC_UNMAPPED="$(python3 - "$OUT/atomic-traceability/atomic-traceability-summary.v1.json" <<'PY'
import json, pathlib, sys
print(json.loads(pathlib.Path(sys.argv[1]).read_text())['unmapped_or_partial_count'])
PY
)"

if [[ "$STATIC_ONLY" == true ]]; then
  python3 - "$OUT/result.json" "$PROFILE" "$HEAD_SHA" "$ENVIRONMENT_DIGEST" \
    "$START_TREE_SHA256" "$START_POLICY_SHA256" "$ATOMIC_DECISION" "$ATOMIC_UNMAPPED" <<'PY'
import json, pathlib, sys
(path, profile, head, environment, tree, policy, atomic_decision, unmapped) = sys.argv[1:]
body = {
    "contract": "ONSURE_ONE_SHOT_RESULT_V3",
    "decision": "BLOCKED_NONFINAL" if atomic_decision == "BLOCKED" else "NON_FINAL",
    "blocking_reasons": ["ATOMIC_REQUIREMENTS_UNMAPPED"] if int(unmapped) else [],
    "mode": "STATIC_ONLY",
    "profile": profile,
    "source_commit": head,
    "source_tree_sha256": tree,
    "policy_sha256": policy,
    "environment_digest": environment,
    "repository_contracts": "PASS",
    "core_isolation_static": "PASS",
    "shell_syntax": "PASS",
    "python_syntax": "PASS",
    "structured_contracts": "PASS_NONFINAL",
    "atomic_traceability": atomic_decision,
    "atomic_unmapped_or_partial_count": int(unmapped),
    "maven_junit": "NOT_RUN",
    "runtime_e2e": "NOT_RUN",
    "independent_otester": "NOT_RUN",
    "independent_oaudit": "NOT_RUN",
    "final_lock_allowed": False,
    "production_go": False,
    "commercial_go": False,
    "final_claim_allowed": False,
}
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
  (cd "$OUT" && find . -type f ! -name evidence.sha256 -print0 \
    | sort -z | xargs -0 sha256sum > evidence.sha256)
  echo "ONSURE_ONE_SHOT_STATIC_BLOCKED_NONFINAL $OUT atomic_unmapped=$ATOMIC_UNMAPPED"
  exit 0
fi

for command in java javac mvn cmp; do require "$command"; done
JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || fail JDK17_REQUIRED

run_step preflight bash scripts/preflight-local-assurance.sh --profile "$PROFILE"
run_step maven-reactor-tests mvn -B -ntp test
run_step python-tests "$PYTHON_VALIDATOR" -m unittest discover -s tests -p 'test_*.py'
run_step core-fixture-two-run bash scripts/run-core-validator-fixture-e2e.sh
run_step development-gate bash scripts/run-onsure-development-gate.sh

ORUDA_FIXTURES="NOT_RUN"
if [[ "$PROFILE" == "oruda" ]]; then
  run_step optional-oruda-fixture-two-run bash scripts/run-product-platform-e2e.sh
  ORUDA_FIXTURES="PASS_NONFINAL"
fi

END_HEAD="$(git rev-parse HEAD)"
END_STATUS="$(git status --porcelain --untracked-files=all)"
[[ "$END_HEAD" == "$HEAD_SHA" ]] || fail SOURCE_COMMIT_CHANGED_DURING_RUN
[[ -z "$END_STATUS" ]] || fail SOURCE_CHANGED_OR_UNTRACKED_DURING_RUN
END_TREE_SHA256="$(python3 - <<'PY'
import hashlib, pathlib, subprocess
root = pathlib.Path('.').resolve()
raw = subprocess.run(['git','ls-files','-z'], capture_output=True, check=True).stdout
h = hashlib.sha256()
for item in sorted(value for value in raw.split(b'\0') if value):
    path = root / item.decode()
    h.update(item); h.update(b'\0'); h.update(hashlib.sha256(path.read_bytes()).digest()); h.update(b'\0')
print(h.hexdigest())
PY
)"
[[ "$END_TREE_SHA256" == "$START_TREE_SHA256" ]] || fail SOURCE_TREE_CHANGED_DURING_RUN

python3 - "$OUT/result.json" "$PROFILE" "$HEAD_SHA" "$ENVIRONMENT_DIGEST" \
  "$START_TREE_SHA256" "$START_POLICY_SHA256" "$ORUDA_FIXTURES" \
  "$ATOMIC_DECISION" "$ATOMIC_UNMAPPED" <<'PY'
import json, pathlib, sys
(path, profile, head, environment, tree, policy, oruda, atomic_decision, unmapped) = sys.argv[1:]
blocking = [
    "PRODUCT_PROGRAM_BEHAVIOR_PLANNING_REVIEW_IMPROVEMENT_GIT_VSCODE_FULL_CHAIN_OPEN",
    "INDEPENDENT_OTESTER_OAUDIT_NOT_RUN",
]
if int(unmapped):
    blocking.append("ATOMIC_REQUIREMENTS_UNMAPPED")
body = {
    "contract": "ONSURE_ONE_SHOT_RESULT_V3",
    "decision": "BLOCKED_NONFINAL",
    "blocking_reasons": blocking,
    "mode": "FULL_AVAILABLE_AUTOMATION",
    "profile": profile,
    "source_commit": head,
    "source_tree_sha256": tree,
    "policy_sha256": policy,
    "environment_digest": environment,
    "repository_contracts": "PASS",
    "structured_contracts": "PASS_NONFINAL",
    "core_isolation_static": "PASS",
    "maven_reactor_tests": "PASS",
    "python_regression": "PASS",
    "core_generic_ai_fixture_e2e_two_run": "PASS_NONFINAL",
    "internal_development_gate": "SELF_VALIDATION_NONFINAL",
    "optional_oruda_fixtures": oruda,
    "atomic_traceability": atomic_decision,
    "atomic_unmapped_or_partial_count": int(unmapped),
    "vscode_product_full_chain": "NOT_RUN",
    "web_product_full_chain": "NOT_RUN",
    "independent_otester": "NOT_RUN",
    "independent_oaudit": "NOT_RUN",
    "final_lock_allowed": False,
    "production_go": False,
    "commercial_go": False,
    "final_claim_allowed": False,
}
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

(cd "$OUT" && find . -type f ! -name evidence.sha256 -print0 \
  | sort -z | xargs -0 sha256sum > evidence.sha256)
echo "ONSURE_ONE_SHOT_BLOCKED_NONFINAL $OUT" >&2
exit 75
