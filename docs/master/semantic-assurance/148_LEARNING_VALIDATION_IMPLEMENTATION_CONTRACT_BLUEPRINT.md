# 148 Learning & Validation Implementation Contract Blueprint

Status: `NORMATIVE_REFINEMENT / DESIGN_ONLY / NON_FINAL`
Parents: `146_LEARNING_VALIDATION_CLOSED_LOOP_AND_META_ASSURANCE.md`, `147_LEARNING_VALIDATION_CLOSED_LOOP_QA_GATES.md`

## 1. 목적
146의 25개 capability를 구현 가능한 계약 경계로 분해한다. 계약 수를 줄이기 위해 의미가 다른 identity/lifecycle/authority를 합치는 것을 금지한다.

## 2. 핵심 계약군
### A. LearningCandidateAsset
Pattern/Rule/Fixture/Oracle/Detector/Prompt 등 학습 후보의 identity, type, state, source lineage, scope, freshness를 가진다. Candidate 자체에는 최종 PASS/FAIL authority가 없다.

### B. LearningPromotionReceipt
상태 전이마다 from/to state, actor, authority, policy, evidence refs, evaluation/qualification refs, timestamp를 기록한다. `CANDIDATE→ACTIVE` 직접 전이는 금지한다. generator/evaluator/approver의 SoD는 cross-contract invariant로 검증한다.

### C. CorpusIntegrityReport
중복, corruption, label error, poisoning, tenant leakage, benchmark contamination 결과를 기록한다. P0 contamination이 있으면 candidate/epoch 승격은 HOLD다.

### D. LearningEffectivenessReport
동일 benchmark와 동일 비교조건에서 before/after precision, recall, FP, FN, coverage, detection latency와 variance/confidence를 기록한다. 효과가 불명확하거나 regression이 있으면 승격 근거가 될 수 없다.

### E. OracleQualification
Oracle identity/version/hash/source/expected-result authority, benchmark, independence, qualification result, freshness를 기록한다. UNQUALIFIED/STALE oracle은 final PASS authority에 사용할 수 없다.

### F. OracleDisagreementCase
static/runtime/human/model oracle 사이 disagreement를 별도 사건으로 관리한다. 단순 다수결로 해소하지 않고 resolution authority/evidence를 요구한다.

### G. ValidatorRegressionQualification
validator version별 Golden/Blind/Challenge 결과, FP/FN drift, threshold, rollback/requalification 결정을 기록한다.

### H. DerivedLearningLineageDisposition
원본 data/consent/tenant/source가 삭제·철회·offboarding될 때 파생 learning asset을 REVOKE/DELETE/REQUALIFY_REQUIRED/NO_ACTION_WITH_PROOF 중 하나로 disposition한다.

### I. LearningScopePromotion
`PRIVATE→ORGANIZATION→INDUSTRY→GLOBAL` 승격을 별도 승인 사건으로 관리한다. consent/privacy/anonymization/corpus-quality/policy proof 없이는 상향 승격 금지.

### J. ValidationExperiment
Stochastic/Metamorphic/Differential/Environment Matrix 실행의 experiment identity를 공통으로 관리하되 mode-specific contract를 보존한다. run count, seeds, model/provider/runtime/environment, transformation/relation/baseline comparator를 기록한다.

### K. EvidenceObservation
`EVIDENCE_NOT_COLLECTED`, `EVIDENCE_COLLECTION_FAILED`, `OBSERVED_ABSENCE`, `EVIDENCE_UNAVAILABLE`, `EVIDENCE_STALE`, `EVIDENCE_PRESENT`를 canonical state로 관리한다. 미수집과 관측 부재를 절대 합치지 않는다.

### L. LearningStopDecision
marginal gain, regression risk, false-positive cost, saturation, budget 기준으로 CONTINUE/STOP/HOLD를 기록한다.

### M. FailureRegistryEntry
실패 signature, root cause, recurrence, workaround, reproduction fixture, affected versions, closure evidence를 보존한다.

### N. CoverageBalanceReport
언어/framework/industry/tenant/risk-class별 학습 corpus와 benchmark 분포, 과소대표 영역, UNKNOWN/COVERAGE_GAP을 기록한다.

## 3. Cross-contract P0 Invariants
1. LearningCandidateAsset은 직접 Final Decision을 발행하지 못한다.
2. candidate creator와 final approver가 동일 actor이면 Enterprise/SoD policy에서 승격 실패.
3. qualification evidence 없는 ACTIVE 전이 금지.
4. unqualified/stale Oracle을 final PASS에 사용 금지.
5. unresolved OracleDisagreementCase가 있으면 관련 판정은 HOLD.
6. P0 corpus contamination/tenant leakage가 있으면 scope promotion/activation 금지.
7. consent withdrawal 대상 lineage가 unresolved이면 파생 asset ACTIVE 유지 금지.
8. blind/golden contamination이 있으면 해당 benchmark를 qualification evidence로 사용 금지.
9. validator regression threshold 초과 시 ACTIVE validator qualification 유지 금지.
10. EVIDENCE_NOT_COLLECTED를 OBSERVED_ABSENCE로 변환 금지.
11. stale knowledge asset을 final decision authority에서 제외.
12. SINGLE stochastic run으로 stability PASS 금지.

## 4. 구현 Batch 연결
- Batch 1: identity/state/authority/inter-contract invariant foundation
- Batch 3: EvidenceObservation, provenance/currentness, revocation propagation
- Batch 4: tenant/consent/privacy/scope promotion
- Batch 5: candidate lifecycle, Oracle, corpus integrity, effectiveness, drift/meta-assurance
- Batch 7: offboarding/deletion disposition
- Batch 8: learning epoch rollback/migration compatibility
- Batch 9: positive/negative/adversarial/blind/stochastic/metamorphic/differential/environment tests

## 5. Done Gate
각 계약은 Schema → valid fixture → semantic-invalid fixture → cross-contract validator → runtime consumer/producer → test execution → evidence/receipt → trace가 없으면 `IMPLEMENTED/TESTED/EVIDENCE_READY`로 승격하지 않는다.
