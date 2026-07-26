#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROFILE="core"
if [[ $# -gt 0 ]]; then
  [[ "${1:-}" == "--profile" && -n "${2:-}" && $# -eq 2 ]] || {
    echo "usage: bash scripts/preflight-local-assurance.sh [--profile core|oruda]" >&2
    exit 64
  }
  PROFILE="$2"
fi
[[ "$PROFILE" == "core" || "$PROFILE" == "oruda" ]] || {
  echo "PREFLIGHT_FAIL INVALID_PROFILE_$PROFILE" >&2
  exit 64
}

fail() { echo "PREFLIGHT_FAIL $1" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || fail "MISSING_COMMAND_$1"; }
require_file() { [[ -f "$1" ]] || fail "$2"; }

for command in java javac mvn git bash python3 sha256sum cmp; do require "$command"; done
JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] \
  || fail "JDK_17_REQUIRED_FOUND_java_${JAVA_MAJOR:-unknown}_javac_${JAVAC_MAJOR:-unknown}"

require_file pom.xml POM_MISSING
for module in onsure-core onsure-adapter-oruda onsure-cli onsure-test-fixtures; do
  require_file "modules/$module/pom.xml" "MODULE_POM_MISSING_${module//-/_}"
done
require_file .devcontainer/devcontainer.json DEVCONTAINER_CONFIGURATION_MISSING
require_file README.md README_MISSING
require_file docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md DESIGN_AUTHORITY_MISSING
require_file docs/verification/ONSURE_FULL_DESIGN_GAP_ASSESSMENT_v1.md DESIGN_GAP_ASSESSMENT_MISSING
require_file docs/verification/ONSURE_POST_MERGE_SELF_AUDIT_v1.md POST_MERGE_SELF_AUDIT_MISSING
require_file scripts/validate-repository-contracts.py REPOSITORY_CONTRACT_VALIDATOR_MISSING
require_file scripts/validate-core-isolation.py CORE_ISOLATION_VALIDATOR_MISSING
require_file scripts/check-shell-syntax.sh SHELL_SYNTAX_CHECKER_MISSING
require_file scripts/onsure-one-shot.sh ONE_SHOT_RUNNER_MISSING
require_file scripts/run-core-validator-fixture-e2e.sh CORE_FIXTURE_E2E_RUNNER_MISSING
require_file scripts/run-local-assurance.sh RUNNER_MISSING
require_file scripts/run-local-assurance-twice.sh TWICE_RUNNER_MISSING
require_file scripts/run-universal-harness-twice.sh UNIVERSAL_TWICE_RUNNER_MISSING
require_file scripts/verify-local-assurance.sh REVERIFY_SCRIPT_MISSING
require_file scripts/summarize-local-assurance.sh SUMMARY_SCRIPT_MISSING

require_file src/main/java/io/onsure/platform/ValidationEngine.java PRODUCT_VALIDATION_ENGINE_MISSING
require_file src/main/java/io/onsure/platform/CoreValidatorFixtureE2EMain.java CORE_FIXTURE_E2E_MAIN_MISSING
require_file src/main/java/io/onsure/platform/TargetAdapter.java PRODUCT_TARGET_ADAPTER_MISSING
require_file src/main/java/io/onsure/platform/GenericManifestTargetAdapter.java GENERIC_TARGET_ADAPTER_MISSING
require_file src/main/java/io/onsure/platform/FixtureHarness.java PRODUCT_FIXTURE_HARNESS_MISSING
require_file src/main/java/io/onsure/learning/OfficialLearningLedger.java LEARNING_LEDGER_MISSING
require_file src/main/java/io/onsure/rag/ProgramLearningOrchestrator.java LEARNING_ORCHESTRATOR_MISSING

require_file fixtures/e2e/general-program/onsure-target.json GENERAL_TARGET_E2E_MISSING
require_file fixtures/e2e/general-program-fixed/onsure-target.json GENERAL_REMEDIATED_TARGET_E2E_MISSING
require_file fixtures/e2e/ai-program/onsure-target.json AI_TARGET_E2E_MISSING
for contract in \
  product-scope.v1.json validation-target-registry.v1.json target-adapter.v1.json \
  core-extension-boundary.v1.json core-module-isolation.v1.json status-vocabulary.v1.json \
  requirements-traceability.v1.json program-profile.v1.schema.json \
  behavior-profile.v1.schema.json failure-memory.v1.schema.json \
  improvement-memory.v1.schema.json evidence-receipt.v1.schema.json \
  receipt-envelope.v1.schema.json local-agent-receipt.v1.schema.json \
  local-run-context.v1.schema.json source-lock.v1.schema.json \
  security-findings.v1.schema.json; do
  require_file "contracts/$contract" "CONTRACT_MISSING_${contract//[^A-Za-z0-9]/_}"
done

if [[ "$PROFILE" == "oruda" ]]; then
  require_file src/main/java/io/onsure/platform/ProductPlatformE2EMain.java OPTIONAL_VALIDATOR_FIXTURE_E2E_MAIN_MISSING
  require_file src/main/java/io/onsure/platform/OrudaTargetAdapter.java ORUDA_TARGET_ADAPTER_MISSING
  require_file src/main/java/io/onsure/platform/oruda/OrudaValidationEngineFactory.java ORUDA_ENGINE_FACTORY_MISSING
  require_file src/main/java/io/onsure/platform/oruda/OrudaExecutionPackageCatalog.java ORUDA_EXECUTION_PACKAGE_CATALOG_LOADER_MISSING
  require_file src/main/java/io/onsure/platform/oruda/OrudaDocumentMaterializer.java ORUDA_DOCUMENT_MATERIALIZER_MISSING
  require_file src/main/java/io/onsure/platform/oruda/OrudaPackageOutputReceiptVerifier.java ORUDA_PACKAGE_OUTPUT_RECEIPT_VERIFIER_MISSING
  require_file src/main/java/io/onsure/platform/oruda/OrudaPackageExecutionRegistry.java ORUDA_PACKAGE_EXECUTION_REGISTRY_MISSING
  require_file src/main/java/io/onsure/platform/oruda/OrudaEvidenceRegistry.java ORUDA_EVIDENCE_REGISTRY_MISSING
  require_file src/main/java/io/onsure/platform/oruda/ReceiptLineageVerifier.java ORUDA_RECEIPT_LINEAGE_VERIFIER_MISSING
  require_file fixtures/e2e/oruda-target/oruda-target.json ORUDA_TARGET_E2E_MISSING
  require_file fixtures/oruda/mvf-001/oruda-target.json ORUDA_MVF_TARGET_MISSING
fi

GIT_DIR="$(git rev-parse --git-dir 2>/dev/null)" || fail NOT_A_GIT_REPOSITORY
[[ -n "$GIT_DIR" ]] || fail NOT_A_GIT_REPOSITORY
[[ -z "$(git status --porcelain --untracked-files=all)" ]] || fail WORKTREE_DIRTY_OR_UNTRACKED
COMMIT="$(git rev-parse HEAD)"
[[ "$COMMIT" =~ ^[0-9a-f]{40}$|^[0-9a-f]{64}$ ]] || fail INVALID_COMMIT_SHA

python3 scripts/validate-repository-contracts.py >/dev/null \
  || fail REPOSITORY_CONTRACT_VALIDATION_FAILED
python3 scripts/validate-core-isolation.py >/dev/null \
  || fail CORE_ISOLATION_STATIC_VALIDATION_FAILED
bash scripts/check-shell-syntax.sh >/dev/null || fail SHELL_SYNTAX_VALIDATION_FAILED

if [[ "$PROFILE" == "core" ]]; then
  mvn -B -ntp -pl modules/onsure-core -am -DskipTests validate >/dev/null \
    || fail MAVEN_CORE_VALIDATE_FAILED
else
  mvn -B -ntp -pl modules/onsure-adapter-oruda -am -DskipTests validate >/dev/null \
    || fail MAVEN_ORUDA_ADAPTER_VALIDATE_FAILED
fi

echo "LOCAL_ASSURANCE_PREFLIGHT_PASS $PROFILE $COMMIT MODULE_ISOLATION_STATIC_PASS_RUNTIME_NOT_RUN"
