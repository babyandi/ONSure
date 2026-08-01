#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for command in java javac mvn git bash sha256sum cmp; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "VALIDATOR_FIXTURE_E2E_FAIL MISSING_COMMAND_$command" >&2
    exit 69
  }
done
JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || {
  echo "VALIDATOR_FIXTURE_E2E_FAIL JDK_17_REQUIRED_FOUND_java_${JAVA_MAJOR:-unknown}_javac_${JAVAC_MAJOR:-unknown}" >&2
  exit 70
}
[[ -z "$(git status --porcelain --untracked-files=no)" ]] || {
  echo "VALIDATOR_FIXTURE_E2E_FAIL TRACKED_WORKTREE_DIRTY" >&2
  exit 72
}

bash "$ROOT/scripts/preflight-local-assurance.sh" --profile oruda
bash "$ROOT/scripts/preflight-universal-harness.sh"
OUT="$ROOT/receipts/validator-fixture-e2e/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$OUT/test-1" "$OUT/test-2" "$OUT/execution-1" "$OUT/execution-2"

run_tests() {
  local output="$1"
  rm -rf target
  mvn -B -ntp \
    -Donsure.allowTrustedFixtureAutoApproval=true \
    -Dtest=ImplementationAuthorityContractTest,ValidationPlatformE2ETest,ProductExecutionBoundaryTest,OrudaExecutionPackageCatalogTest,OrudaDocumentMaterializerTest,OrudaPackageExecutionRegistryTest,OrudaMvf001E2ETest,ExecutionResultClassifierTest,OrudaEvidenceLineageAndCandidateTest,OrudaFinalLockGateTest,UniversalHarnessContractTest,OracleEngineTest,UniversalHarnessRunnerTest,FinalCandidateAndRegressionTest \
    test | tee "$output/maven.log"
  grep -h '^Tests run:' target/surefire-reports/*.txt \
    | python "$ROOT/scripts/normalize-surefire-summary.py" \
    | LC_ALL=C sort > "$output/test-summary.txt"
  (cd target/classes && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum) \
    > "$output/classes.sha256"
  (cd target/test-classes && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum) \
    > "$output/test-classes.sha256"
  sha256sum "$output/test-summary.txt" "$output/classes.sha256" "$output/test-classes.sha256" \
    > "$output/evidence.sha256"
}

run_tests "$OUT/test-1"
run_tests "$OUT/test-2"
cmp "$OUT/test-1/test-summary.txt" "$OUT/test-2/test-summary.txt"
cmp "$OUT/test-1/classes.sha256" "$OUT/test-2/classes.sha256"
cmp "$OUT/test-1/test-classes.sha256" "$OUT/test-2/test-classes.sha256"

mvn -B -ntp -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile="$OUT/classpath.txt" >/dev/null
CP="$ROOT/target/classes:$(cat "$OUT/classpath.txt")"
java -Donsure.allowTrustedFixtureAutoApproval=true -cp "$CP" \
  io.onsure.platform.ProductPlatformE2EMain "$OUT/execution-1" \
  | tee "$OUT/execution-1/execution.log"
java -Donsure.allowTrustedFixtureAutoApproval=true -cp "$CP" \
  io.onsure.platform.ProductPlatformE2EMain "$OUT/execution-2" \
  | tee "$OUT/execution-2/execution.log"

cmp "$OUT/execution-1/normalized-result.json" "$OUT/execution-2/normalized-result.json"
for run in execution-1 execution-2; do
  [[ -s "$OUT/$run/general-program-revalidation-delta.json" ]] || {
    echo "VALIDATOR_FIXTURE_E2E_FAIL REVALIDATION_DELTA_MISSING_$run" >&2
    exit 80
  }
  [[ -s "$OUT/$run/execution-inventory.json" ]] || {
    echo "VALIDATOR_FIXTURE_E2E_FAIL EXECUTION_INVENTORY_MISSING_$run" >&2
    exit 80
  }
done

sha256sum \
  "$OUT/test-1/test-summary.txt" "$OUT/test-1/classes.sha256" "$OUT/test-1/test-classes.sha256" \
  "$OUT/test-2/test-summary.txt" "$OUT/test-2/classes.sha256" "$OUT/test-2/test-classes.sha256" \
  "$OUT/execution-1/normalized-result.json" "$OUT/execution-1/general-program-revalidation-delta.json" \
  "$OUT/execution-2/normalized-result.json" "$OUT/execution-2/general-program-revalidation-delta.json" \
  > "$OUT/validator-fixture-e2e-lock.sha256"

printf 'ONSURE_VALIDATOR_FIXTURE_E2E_PASS %s\n' "$OUT"
