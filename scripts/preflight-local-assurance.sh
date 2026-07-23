#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { echo "PREFLIGHT_FAIL $1" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || fail "MISSING_COMMAND_$1"; }
require_file() { [[ -f "$1" ]] || fail "$2"; }

require java
require javac
require mvn
require git
require bash
require sha256sum
require cmp

JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] \
  || fail "JDK_17_REQUIRED_FOUND_java_${JAVA_MAJOR:-unknown}_javac_${JAVAC_MAJOR:-unknown}"

require_file pom.xml POM_MISSING
require_file .devcontainer/devcontainer.json DEVCONTAINER_CONFIGURATION_MISSING
require_file scripts/prepare-assurance-environment.sh ENVIRONMENT_PREPARATION_SCRIPT_MISSING
require_file scripts/execute-issue-4-final-gate.sh ISSUE4_FINAL_GATE_SCRIPT_MISSING
require_file scripts/run-product-platform-e2e.sh PRODUCT_E2E_RUNNER_MISSING
require_file scripts/run-onsure-development-gate.sh DEVELOPMENT_GATE_RUNNER_MISSING
require_file scripts/run-local-assurance.sh RUNNER_MISSING
require_file scripts/run-local-assurance-twice.sh TWICE_RUNNER_MISSING
require_file scripts/verify-local-assurance.sh REVERIFY_SCRIPT_MISSING
require_file scripts/summarize-local-assurance.sh SUMMARY_SCRIPT_MISSING

require_file src/main/java/io/onsure/platform/ValidationEngine.java PRODUCT_VALIDATION_ENGINE_MISSING
require_file src/main/java/io/onsure/platform/ProductPlatformE2EMain.java PRODUCT_E2E_MAIN_MISSING
require_file src/main/java/io/onsure/platform/TargetAdapter.java PRODUCT_TARGET_ADAPTER_MISSING
require_file src/main/java/io/onsure/platform/FixtureHarness.java PRODUCT_FIXTURE_HARNESS_MISSING
require_file src/main/java/io/onsure/platform/OrudaTargetAdapter.java ORUDA_TARGET_ADAPTER_MISSING
require_file src/main/java/io/onsure/platform/oruda/OrudaExecutionPackageCatalog.java ORUDA_EXECUTION_PACKAGE_CATALOG_LOADER_MISSING
require_file src/main/java/io/onsure/platform/oruda/OrudaDocumentMaterializer.java ORUDA_DOCUMENT_MATERIALIZER_MISSING
require_file src/main/java/io/onsure/platform/oruda/OrudaDocumentMaterializerMain.java ORUDA_DOCUMENT_MATERIALIZER_CLI_MISSING
require_file src/main/java/io/onsure/platform/oruda/OrudaPackageOutputReceiptVerifier.java ORUDA_PACKAGE_OUTPUT_RECEIPT_VERIFIER_MISSING
require_file src/main/java/io/onsure/platform/oruda/OrudaPackageExecutionRegistry.java ORUDA_PACKAGE_EXECUTION_REGISTRY_MISSING
require_file src/main/java/io/onsure/platform/oruda/ExecutionResultClassifier.java ORUDA_RESULT_CLASSIFIER_MISSING
require_file src/main/java/io/onsure/platform/oruda/OrudaEvidenceRegistry.java ORUDA_EVIDENCE_REGISTRY_MISSING
require_file src/main/java/io/onsure/platform/oruda/ReceiptLineageVerifier.java ORUDA_RECEIPT_LINEAGE_VERIFIER_MISSING
require_file src/main/java/io/onsure/platform/oruda/BlindReviewReceiptVerifier.java ORUDA_BLIND_REVIEW_VERIFIER_MISSING
require_file src/main/java/io/onsure/platform/oruda/IndependentRunReceiptVerifier.java ORUDA_INDEPENDENT_RUN_VERIFIER_MISSING
require_file src/main/java/io/onsure/platform/oruda/FinalCandidateGate.java ORUDA_FINAL_CANDIDATE_GATE_MISSING
require_file src/main/java/io/onsure/platform/oruda/OrudaFinalCandidateMain.java ORUDA_FINAL_CANDIDATE_CLI_MISSING
require_file src/main/java/io/onsure/platform/oruda/FinalApprovalReceiptVerifier.java ORUDA_FINAL_APPROVAL_VERIFIER_MISSING
require_file src/main/java/io/onsure/platform/oruda/OrudaFinalLockGate.java ORUDA_FINAL_LOCK_GATE_MISSING
require_file src/main/java/io/onsure/platform/oruda/OrudaFinalLockMain.java ORUDA_FINAL_LOCK_CLI_MISSING

require_file src/test/java/io/onsure/platform/ValidationPlatformE2ETest.java PRODUCT_E2E_TEST_MISSING
require_file src/test/java/io/onsure/platform/ProductExecutionBoundaryTest.java PRODUCT_BOUNDARY_TEST_MISSING
require_file src/test/java/io/onsure/platform/oruda/OrudaExecutionPackageCatalogTest.java ORUDA_EXECUTION_PACKAGE_CATALOG_TEST_MISSING
require_file src/test/java/io/onsure/platform/oruda/OrudaDocumentMaterializerTest.java ORUDA_DOCUMENT_MATERIALIZER_TEST_MISSING
require_file src/test/java/io/onsure/platform/oruda/OrudaPackageExecutionRegistryTest.java ORUDA_PACKAGE_EXECUTION_REGISTRY_TEST_MISSING
require_file src/test/java/io/onsure/platform/oruda/OrudaMvf001E2ETest.java ORUDA_MVF_E2E_TEST_MISSING
require_file src/test/java/io/onsure/platform/oruda/ExecutionResultClassifierTest.java ORUDA_RESULT_CLASSIFIER_TEST_MISSING
require_file src/test/java/io/onsure/platform/oruda/OrudaEvidenceLineageAndCandidateTest.java ORUDA_LINEAGE_CANDIDATE_TEST_MISSING
require_file src/test/java/io/onsure/platform/oruda/OrudaFinalLockGateTest.java ORUDA_FINAL_LOCK_TEST_MISSING

require_file fixtures/e2e/general-program/onsure-target.json GENERAL_TARGET_E2E_MISSING
require_file fixtures/e2e/general-program-fixed/onsure-target.json GENERAL_REMEDIATED_TARGET_E2E_MISSING
require_file fixtures/e2e/ai-program/onsure-target.json AI_TARGET_E2E_MISSING
require_file fixtures/e2e/oruda-target/oruda-target.json ORUDA_TARGET_E2E_MISSING
require_file fixtures/oruda/mvf-001/oruda-target.json ORUDA_MVF_TARGET_MISSING
require_file fixtures/oruda/mvf-001/mvf-runner.sh ORUDA_MVF_RUNNER_MISSING
require_file fixtures/design/adversarial-transition-fixtures.v1.json ADVERSARIAL_FIXTURE_MISSING
require_file findings/security-findings.v1.json SECURITY_FINDINGS_REGISTER_MISSING

require_file contracts/product-scope.v1.json PRODUCT_SCOPE_CONTRACT_MISSING
require_file contracts/validation-target-registry.v1.json VALIDATION_TARGET_REGISTRY_MISSING
require_file contracts/target-adapter.v1.json TARGET_ADAPTER_CONTRACT_MISSING
require_file contracts/oruda-execution-packages.v1.json ORUDA_EXECUTION_PACKAGE_MAP_MISSING
require_file contracts/oruda-document-materialization.v1.schema.json ORUDA_DOCUMENT_MATERIALIZATION_SCHEMA_MISSING
require_file contracts/oruda-package-output-receipt.v1.schema.json ORUDA_PACKAGE_OUTPUT_RECEIPT_SCHEMA_MISSING
require_file contracts/oruda-package-execution-registry.v1.schema.json ORUDA_PACKAGE_EXECUTION_REGISTRY_SCHEMA_MISSING
require_file contracts/oruda-evidence-registry.v1.schema.json ORUDA_EVIDENCE_REGISTRY_SCHEMA_MISSING
require_file contracts/oruda-harness-command-manifest.v1.schema.json ORUDA_HARNESS_COMMAND_SCHEMA_MISSING
require_file contracts/oruda-blind-review-receipt.v1.schema.json ORUDA_BLIND_REVIEW_SCHEMA_MISSING
require_file contracts/oruda-independent-run-receipt.v1.schema.json ORUDA_INDEPENDENT_RUN_SCHEMA_MISSING
require_file contracts/oruda-final-candidate-gate.v1.schema.json ORUDA_FINAL_CANDIDATE_SCHEMA_MISSING
require_file contracts/oruda-final-approval-receipt.v1.schema.json ORUDA_FINAL_APPROVAL_SCHEMA_MISSING
require_file contracts/oruda-final-lock.v1.schema.json ORUDA_FINAL_LOCK_SCHEMA_MISSING
require_file contracts/receipt-envelope.v1.schema.json RECEIPT_CONTRACT_MISSING
require_file contracts/local-agent-receipt.v1.schema.json LOCAL_AGENT_RECEIPT_CONTRACT_MISSING
require_file contracts/local-run-context.v1.schema.json RUN_CONTEXT_CONTRACT_MISSING
require_file contracts/source-lock.v1.schema.json SOURCE_LOCK_CONTRACT_MISSING
require_file contracts/local-final-receipt.v1.schema.json FINAL_RECEIPT_CONTRACT_MISSING
require_file contracts/security-findings.v1.schema.json SECURITY_FINDINGS_CONTRACT_MISSING
require_file contracts/state-machine.v1.json STATE_MACHINE_CONTRACT_MISSING
require_file contracts/assurance-lanes.v1.json ASSURANCE_LANES_CONTRACT_MISSING
require_file docs/architecture/ONSURE_GENERAL_VALIDATION_PLATFORM_v1.md GENERAL_PLATFORM_ARCHITECTURE_MISSING

GIT_DIR="$(git rev-parse --git-dir 2>/dev/null)" || fail "NOT_A_GIT_REPOSITORY"
[[ -n "$GIT_DIR" ]] || fail "NOT_A_GIT_REPOSITORY"
[[ -z "$(git status --porcelain --untracked-files=no)" ]] || fail "TRACKED_WORKTREE_DIRTY"

COMMIT="$(git rev-parse HEAD)"
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || fail "INVALID_COMMIT_SHA"

mvn -B -ntp -DskipTests validate >/dev/null || fail "MAVEN_VALIDATE_FAILED"

echo "LOCAL_ASSURANCE_PREFLIGHT_PASS $COMMIT"
