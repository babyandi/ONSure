#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROFILE="core"
if [[ $# -gt 0 ]]; then
  [[ "${1:-}" == "--profile" && -n "${2:-}" && $# -eq 2 ]] || {
    echo "usage: bash scripts/onsure-final-stage.sh [--profile core|oruda]" >&2
    exit 64
  }
  PROFILE="$2"
fi
[[ "$PROFILE" == "core" || "$PROFILE" == "oruda" ]] || {
  echo "ONSURE_FINAL_STAGE_FAIL INVALID_PROFILE_$PROFILE" >&2
  exit 64
}

for command in git bash python3 java javac mvn sha256sum cmp bwrap prlimit timeout node npm; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "ONSURE_FINAL_STAGE_FAIL MISSING_COMMAND_$command" >&2
    exit 69
  }
done

JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || {
  echo "ONSURE_FINAL_STAGE_FAIL JDK17_REQUIRED" >&2
  exit 70
}
[[ -z "$(git status --porcelain)" ]] || {
  echo "ONSURE_FINAL_STAGE_FAIL WORKTREE_DIRTY_OR_UNTRACKED" >&2
  exit 72
}

STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
OUT="${ONSURE_FINAL_STAGE_OUTPUT:-$ROOT/.onsure/final-stage/$STAMP}"
mkdir -p "$OUT/logs" "$OUT/artifacts"

python3 scripts/create-source-snapshot.py --output "$OUT/source-start.json"

VALIDATION_PYTHON="python3"
if ! python3 -c 'import jsonschema, yaml' >/dev/null 2>&1; then
  python3 -m venv "$OUT/validation-venv"
  "$OUT/validation-venv/bin/python" -m pip install --require-hashes \
    --disable-pip-version-check --no-input -r requirements-validation.txt \
    | tee "$OUT/logs/validation-dependency-install.log"
  VALIDATION_PYTHON="$OUT/validation-venv/bin/python"
fi
VALIDATION_BIN="$(cd "$(dirname "$VALIDATION_PYTHON")" && pwd)"
export PATH="$VALIDATION_BIN:$PATH"

"$VALIDATION_PYTHON" scripts/validate-structured-contracts.py --require-full \
  | tee "$OUT/logs/structured-contracts.log"
python3 scripts/validate-final-product-requirements.py --self-test \
  | tee "$OUT/logs/final-product-requirements.log"
python3 scripts/validate-final-acceptance-coverage.py --self-test \
  | tee "$OUT/logs/final-acceptance-coverage.log"
python3 -m unittest discover -s tests -p 'test_*.py' -v \
  2>&1 | tee "$OUT/logs/full-python-regression.log"
python3 scripts/validate-codespace-free-remediation.py \
  | tee "$OUT/logs/codespace-free-static-gate.log"
python3 scripts/check-module-boundaries.py \
  | tee "$OUT/logs/module-boundary.log"
python3 scripts/validate-vscode-extension.py --require-node \
  | tee "$OUT/logs/vscode-static.log"
python3 scripts/extract-atomic-requirements.py \
  --output "$OUT/atomic-requirement-candidates.json"

# Final execution must use the Linux isolation profile. Missing or unusable sandbox tooling fails closed.
export ONSURE_FIXTURE_SANDBOX_MODE=REQUIRED

bash scripts/run-core-modular-twice.sh \
  | tee "$OUT/logs/core-modular-two-run.log"

mvn -B -ntp -f pom-modular.xml \
  -pl modules/onsure-cli,modules/onsure-local-api -am test \
  | tee "$OUT/logs/cli-local-api-modules.log"

ORUDA_MODULE="NOT_RUN"
if [[ "$PROFILE" == "oruda" ]]; then
  mvn -B -ntp -f pom-modular.xml -pl modules/onsure-adapter-oruda -am test \
    | tee "$OUT/logs/oruda-module.log"
  ORUDA_MODULE="PASS_NONFINAL"
fi

# Package the extension in an isolated copy so node_modules never influences source identity.
EXT_BUILD="$OUT/vscode-extension-build"
cp -R "$ROOT/vscode-extension" "$EXT_BUILD"
mkdir -p "$OUT/scripts"
cp "$ROOT/scripts/package_onsure_vsix.py" "$OUT/scripts/package_onsure_vsix.py"
(
  cd "$EXT_BUILD"
  npm ci --ignore-scripts --engine-strict --no-audit --no-fund \
    | tee "$OUT/logs/vscode-npm-install.log"
  npm run check | tee "$OUT/logs/vscode-node-check.log"
  npm run package -- --out "$OUT/artifacts/onsure.vsix" \
    | tee "$OUT/logs/vscode-package.log"
)
[[ -s "$OUT/artifacts/onsure.vsix" ]] || {
  echo "ONSURE_FINAL_STAGE_FAIL VSIX_MISSING" >&2
  exit 74
}
sha256sum "$OUT/artifacts/onsure.vsix" > "$OUT/artifacts/onsure.vsix.sha256"

set +e
bash scripts/onsure-one-shot.sh --profile "$PROFILE" \
  | tee "$OUT/logs/one-shot.log"
ONE_SHOT_EXIT=${PIPESTATUS[0]}
set -e

python3 scripts/create-source-snapshot.py --output "$OUT/source-end.json"
cmp "$OUT/source-start.json" "$OUT/source-end.json" >/dev/null || {
  echo "ONSURE_FINAL_STAGE_FAIL SOURCE_DRIFT" >&2
  exit 73
}

python3 - "$OUT/final-stage-result.json" "$PROFILE" "$ORUDA_MODULE" "$ONE_SHOT_EXIT" <<'PY'
import json, pathlib, sys
path, profile, oruda, one_shot = sys.argv[1:]
exit_code = int(one_shot)
body = {
    "contract": "ONSURE_FINAL_STAGE_RESULT_V3",
    "profile": profile,
    "structured_contracts": "PASS",
    "codespace_free_static_gate": "PASS",
    "module_boundary": "PASS",
    "fixture_sandbox": "BWRAP_PRLIMIT_NETWORK_UNSHARED",
    "core_modular_two_run": "PASS_NONFINAL",
    "cli_module": "PASS_NONFINAL",
    "local_api_module": "PASS_NONFINAL",
    "local_api_authentication_smoke": "PASS_NONFINAL",
    "vscode_static": "PASS",
    "vsix_package": "PASS_NONFINAL",
    "oruda_module": oruda,
    "one_shot_exit": exit_code,
    "decision": "BLOCKED" if exit_code == 75 else ("NON_FINAL" if exit_code == 0 else "FAIL"),
    "independent_otester": "NOT_RUN",
    "independent_oaudit": "NOT_RUN",
    "vscode_extension_host_full_chain": "NOT_RUN",
    "web_payment_provider_full_chain": "NOT_RUN",
    "final_lock_allowed": False,
    "production_go": False,
    "commercial_go": False,
}
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

find "$OUT" -type f ! -name evidence.sha256 -print0 \
  | sort -z | xargs -0 sha256sum > "$OUT/evidence.sha256"

if [[ $ONE_SHOT_EXIT -eq 0 ]]; then
  echo "ONSURE_FINAL_STAGE_SELF_VALIDATION_NONFINAL $OUT"
  exit 0
fi
if [[ $ONE_SHOT_EXIT -eq 75 ]]; then
  echo "ONSURE_FINAL_STAGE_BLOCKED_NONFINAL $OUT" >&2
  exit 75
fi

echo "ONSURE_FINAL_STAGE_FAIL ONE_SHOT_EXIT_$ONE_SHOT_EXIT $OUT" >&2
exit "$ONE_SHOT_EXIT"
