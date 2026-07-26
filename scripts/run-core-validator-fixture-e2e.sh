#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { echo "ONSURE_CORE_FIXTURE_E2E_FAIL $1" >&2; exit 1; }
for command in java javac mvn git bash python3 sha256sum cmp; do
  command -v "$command" >/dev/null 2>&1 || fail "MISSING_COMMAND_$command"
done

JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || fail "JDK17_REQUIRED"
[[ -z "$(git status --porcelain --untracked-files=all)" ]] || fail "WORKTREE_DIRTY_OR_UNTRACKED"

python3 scripts/validate-core-isolation.py >/dev/null || fail "STATIC_CORE_ISOLATION"
bash scripts/preflight-local-assurance.sh --profile core >/dev/null

STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
OUT="${ONSURE_CORE_E2E_OUTPUT:-$ROOT/.onsure/core-fixture-e2e/$STAMP}"
mkdir -p "$OUT/test-1" "$OUT/test-2" "$OUT/execution-1" "$OUT/execution-2"

run_tests() {
  local output="$1"
  mvn -B -ntp -pl modules/onsure-core -am clean test | tee "$output/maven.log"
  grep -h '^Tests run:' modules/onsure-core/target/surefire-reports/*.txt \
    | python3 scripts/normalize-surefire-summary.py \
    | LC_ALL=C sort > "$output/test-summary.txt"
  (
    cd modules/onsure-core/target/classes
    find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum
  ) > "$output/classes.sha256"
  sha256sum "$output/test-summary.txt" "$output/classes.sha256" \
    > "$output/evidence.sha256"
}

run_tests "$OUT/test-1"
run_tests "$OUT/test-2"
cmp "$OUT/test-1/test-summary.txt" "$OUT/test-2/test-summary.txt"
cmp "$OUT/test-1/classes.sha256" "$OUT/test-2/classes.sha256"

mvn -B -ntp -pl modules/onsure-core -am -DskipTests package >/dev/null
mvn -B -ntp -pl modules/onsure-core dependency:build-classpath \
  -Dmdep.outputFile="$OUT/classpath.txt" >/dev/null
CP="$ROOT/modules/onsure-core/target/classes:$(cat "$OUT/classpath.txt")"

java -cp "$CP" io.onsure.platform.CoreValidatorFixtureE2EMain "$OUT/execution-1" \
  | tee "$OUT/execution-1/execution.log"
java -cp "$CP" io.onsure.platform.CoreValidatorFixtureE2EMain "$OUT/execution-2" \
  | tee "$OUT/execution-2/execution.log"

cmp "$OUT/execution-1/normalized-result.json" "$OUT/execution-2/normalized-result.json"
for run in execution-1 execution-2; do
  [[ -s "$OUT/$run/execution-inventory.json" ]] || fail "INVENTORY_MISSING_$run"
  [[ -s "$OUT/$run/general-program-revalidation-delta.json" ]] \
    || fail "REVALIDATION_DELTA_MISSING_$run"
  grep -q '"oruda_classes_required" : false' "$OUT/$run/execution-inventory.json" \
    || fail "ORUDA_CLASS_BOUNDARY_NOT_PROVEN_$run"
  grep -q '"oruda_fixtures_required" : false' "$OUT/$run/execution-inventory.json" \
    || fail "ORUDA_FIXTURE_BOUNDARY_NOT_PROVEN_$run"
done

sha256sum \
  "$OUT/test-1/test-summary.txt" "$OUT/test-1/classes.sha256" \
  "$OUT/test-2/test-summary.txt" "$OUT/test-2/classes.sha256" \
  "$OUT/execution-1/normalized-result.json" \
  "$OUT/execution-1/general-program-revalidation-delta.json" \
  "$OUT/execution-2/normalized-result.json" \
  "$OUT/execution-2/general-program-revalidation-delta.json" \
  > "$OUT/core-two-run-evidence.sha256"

cat > "$OUT/result.json" <<EOF
{
  "contract": "ONSURE_CORE_FIXTURE_TWO_RUN_RESULT_V1",
  "decision": "PASS_NONFINAL",
  "core_module": "modules/onsure-core",
  "oruda_module_required": false,
  "generic_ai_fixture_e2e_runs": 2,
  "maven_test_runs": 2,
  "independent_otester": "NOT_RUN",
  "independent_oaudit": "NOT_RUN",
  "final_claim_allowed": false
}
EOF
sha256sum "$OUT/result.json" >> "$OUT/core-two-run-evidence.sha256"
printf 'ONSURE_CORE_FIXTURE_TWO_RUN_PASS_NONFINAL %s\n' "$OUT"
