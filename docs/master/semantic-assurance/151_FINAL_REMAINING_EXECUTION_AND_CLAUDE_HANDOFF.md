# 151 Final Remaining Execution and Claude Handoff

Status: `QA_EXECUTION_COMPLETE_AS_FAR_AS_CONNECTOR_ALLOWS / RUNTIME_EXECUTION_PENDING / NON_FINAL`

## 1. 이번 QA에서 실제 완료한 항목
- FR-LEARN-001~025 설계/QA Gate/contract blueprint 작성
- FR-LEARN 25개 Product Design RU extension materialization
- FR-LEARN applicability: APPLICABLE 21 / CONDITIONAL 4 / UNKNOWN 0
- FR-LEARN design trace: 25/25 mapped
- 14개 learning/validation contract family의 schema/fixture specification 작성
- explicit Product Design Requirement snapshot 갱신: 78 → 103
- explicit trace snapshot 갱신: 98/103 traced candidate
- learning fixture plan 14/14 작성

## 2. 현재 authoritative explicit population
- FR-COM: 13
- FR-META: 62
- FR-FRESH: 3
- FR-LEARN: 25
- explicit candidate total: 103

103은 exact active denominator가 아니다. 다음 non-ID source class가 아직 authority-filtered runtime materialization 되지 않았다.
- NON_ID_PROGRAM_FUNCTION
- ACCEPTANCE_CRITERION
- NFR
- ARCHITECTURE_INVARIANT
- POLICY_REQUIREMENT
- REGULATORY_REQUIREMENT

## 3. 남은 QA/runtime 작업 — Claude 실행 순서
### A. Requirement Authority generator 교체
1. `docs/master` 전체 markdown 자동 스캔 금지.
2. `contracts/requirement-authority-source-manifest.candidate.v1.json` 및 reviewed source rows를 입력으로 사용.
3. requirement-eligible source만 스캔.
4. manifest row 없는 source는 fail-closed.
5. `longest-text-wins` canonicalization 제거.
6. explicit authority/supersession/refine relation으로 canonical text 결정.

### B. Full Product Design RU 재생성
- explicit 103을 포함한다.
- 6개 non-ID class materialize.
- duplicate occurrence는 보존하되 denominator 중복 산입 금지.
- semantic variants는 authority/refine/supersede로 disposition.
- 결과에 exact population count + SHA-256 digest + generation receipt 기록.

### C. Applicability 전수 재생성
- exact RU와 1:1 cardinality.
- APPLICABLE / NOT_APPLICABLE / CONDITIONAL / UNKNOWN.
- N/A/Conditional은 proof/context 필요.
- Critical UNKNOWN=0이 아니면 Lock HOLD.

### D. Global Trace/Orphan/Contradiction 전수 재생성
Requirement → Design → Contract → Operation → API → Event → Receipt → Test → Evidence.
Reverse orphan도 검사.
- requirement 없는 design/code/test/evidence
- dangling contract refs
- state/authority/tier/policy/v1-v2 contradiction

### E. FR-COM-008 외부 control evidence
GitHub main branch protection을 실제 control-plane에서 읽고 `contracts/main-branch-protection.v1.json`과 대조.
현재 ChatGPT GitHub connector는 해당 API에 403이므로 Claude 실행환경 또는 적절한 GitHub 권한으로 수행.

### F. Artifact SHA/digest
- authoritative design population exact freeze
- 각 파일 content SHA-256
- Git blob/tree identity 병행
- Requirement/Appplicability/Trace/Contract/Operation/Event/Receipt/Policy registry digest
- Baseline Manifest에 모두 결속

### G. Reconstructability
CLEAN checkout 2회에서 동일 authority manifest로 재생성.
비결정적 필드는 canonical exclusion 정책으로 분리하고 semantic population/digest 동일성을 증명.

### H. Learning/Validation runtime materialization
149의 14개 contract family를 실제 JSON Schema로 생성하고 structured-contract registry에 등록.
각 contract는 positive fixture + schema-invalid + semantic-invalid fixture를 갖춘다.
12개 P0 cross-contract invariant를 validator test로 실행.
FR-LEARN-001~025 implementation trace와 runtime evidence를 25/25 채운다.

## 4. Learning/Validation 14 contract family
LearningCandidateAsset, LearningPromotionReceipt, CorpusIntegrityReport, LearningEffectivenessReport, OracleQualification, OracleDisagreementCase, ValidatorRegressionQualification, DerivedLearningLineageDisposition, LearningScopePromotion, ValidationExperiment, EvidenceObservation, LearningStopDecision, FailureRegistryEntry, CoverageBalanceReport.

## 5. 최종 Lock Gate
다음 모두 충족할 때만 `DESIGN_BASELINE_READY_FOR_LOCK=true` 후보가 된다.
- authority-filtered exact RU population/digest 존재
- Applicability exact 1:1, Critical UNKNOWN=0
- forbidden P0 orphan=0
- unresolved P0 contradiction=0
- unresolved P0 DCQ=0
- FR-COM-008 control evidence verified 또는 명시적 external blocker
- content SHA-256 manifest complete
- registry digest set complete
- reconstructable=true with CLEAN rerun evidence
- learning/validation FR-LEARN 25/25 implementation trace + runtime test evidence

## 6. 현재 판정
`LEARNING_VALIDATION_DESIGN_CLOSED_CANDIDATE / EXPLICIT_RU_103_CANDIDATE / EXPLICIT_TRACE_98_OF_103 / GLOBAL_DENOMINATOR_NOT_EXACT / GLOBAL_DESIGN_LOCK_HOLD / NON_FINAL`
