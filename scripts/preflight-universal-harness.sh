#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
fail() { echo "ONSURE_UNIVERSAL_PREFLIGHT_BLOCKED $1" >&2; exit 78; }
require() { command -v "$1" >/dev/null 2>&1 || fail "MISSING_COMMAND_$1"; }
require_file() { [[ -f "$1" ]] || fail "$2"; }

for command in java javac mvn git bash sha256sum; do require "$command"; done
JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || fail "JDK_17_REQUIRED"
[[ -z "$(git status --porcelain --untracked-files=no)" ]] || fail "TRACKED_WORKTREE_DIRTY"

require_file docs/onsure/review/onsure_universal_harness_execution_transition.md PR6_EXECUTION_TRANSITION_MISSING
require_file docs/onsure/review/oruda_evidence_registry_schema.md PR6_EVIDENCE_SCHEMA_MISSING
require_file harness/universal-v1/axes/verification-axes.v1.json AXIS_DEFINITION_MISSING
require_file harness/universal-v1/schemas/fixture.v1.schema.json FIXTURE_SCHEMA_MISSING
require_file harness/universal-v1/schemas/oracle.v1.schema.json ORACLE_SCHEMA_MISSING
require_file harness/universal-v1/schemas/evidence.v1.schema.json EVIDENCE_SCHEMA_MISSING
require_file harness/universal-v1/schemas/receipt.v1.schema.json RECEIPT_SCHEMA_MISSING
require_file harness/universal-v1/oracles/default-oracles.v1.json ORACLE_SET_MISSING
require_file fixtures/universal-v1/sample-target/fixtures.v1.json SAMPLE_FIXTURE_SET_MISSING
require_file src/main/java/kr/co/oruda/onsure/harness/UniversalHarnessRunner.java UNIVERSAL_RUNNER_MISSING
require_file src/main/java/kr/co/oruda/onsure/harness/RunVerifier.java RUN_VERIFIER_MISSING
require_file src/main/java/kr/co/oruda/onsure/harness/FinalCandidateGate.java FINAL_CANDIDATE_GATE_MISSING
require_file src/main/java/kr/co/oruda/onsure/harness/RegressionGate.java REGRESSION_GATE_MISSING

if grep -R --line-number --fixed-strings 'ORUDA-Master-Queue' \
  src/main src/test harness/universal-v1 scripts/run-universal-harness.sh scripts/run-universal-harness-twice.sh \
  >/dev/null 2>&1; then
  fail "FORBIDDEN_ORUDA_MASTER_QUEUE_REFERENCE"
fi
mvn -B -ntp -DskipTests validate >/dev/null || fail "MAVEN_VALIDATE_FAILED"
printf 'ONSURE_UNIVERSAL_PREFLIGHT_PASS %s\n' "$(git rev-parse HEAD)"
