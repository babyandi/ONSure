# 147 Learning & Validation Closed Loop QA Gates

Status: `QA_GATE / NON_FINAL`
Parent: `146_LEARNING_VALIDATION_CLOSED_LOOP_AND_META_ASSURANCE.md`
Purpose: 146의 25개 보강축이 단순 설계 설명으로 끝나지 않고 구현·테스트·증거·승격 통제까지 닫히는지 검증한다.

## 1. 공통 Gate
각 capability는 최소 `Contract/Schema → Runtime/Implementation → Positive Test → Negative/Semantic-invalid Test → Cross-contract Invariant → Evidence/Receipt → Trace`를 갖춰야 한다. 파일 존재만으로 PASS 금지.

## 2. Learning Lifecycle Gate
- 상태: `OBSERVED → CANDIDATE → EVALUATED → QUALIFIED → APPROVED → ACTIVE → RETIRED`
- 역방향/점프 승격 금지
- `CANDIDATE → ACTIVE` 직접 승격 금지
- 각 전이는 actor/authority/policy/time/evidence를 기록
- RETIRED 지식은 신규 판정에 사용 금지

## 3. Candidate Type Gate
다음 객체를 별도 identity로 검증한다: PatternCandidate, RuleCandidate, FixtureCandidate, OracleCandidate, DetectorCandidate, PromptCandidate. 서로 lifecycle·authority·qualification이 다르면 하나의 generic blob으로 축약 금지.

## 4. Self-Approval Separation Gate
OLearning 또는 candidate generator가 자신이 생성한 지식을 직접 QUALIFIED/APPROVED/ACTIVE로 만들 수 없어야 한다. 독립 evaluator/approver와 SoD를 증명한다.

## 5. Provenance Gate
모든 learning asset은 source Finding/Evidence/Target/Baseline/Version/Observation과 lineage/hash로 결속되고 원본 변경/철회 시 impact를 계산할 수 있어야 한다.

## 6. Negative Learning Gate
False Positive, False Negative, rejected patch, overturned Finding, appeal reversal을 학습 자산으로 기록하고 positive-only corpus 편향을 금지한다.

## 7. Failure Registry Gate
실패 원인, 회피 패턴, 재발 여부, 재현 fixture, affected versions, closure evidence를 장기 추적한다. 동일 failure의 재발을 새 사건으로만 분리해 원인 계보를 잃지 않는다.

## 8. Corpus Quality Gate
중복, label 오류, corruption, poisoning, tenant leakage를 검사한다. contamination/tenant leak이 의심되면 corpus promotion은 HOLD.

## 9. Train/Test Leakage Gate
학습 corpus와 Golden/Blind/Challenge set 간 동일·파생·near-duplicate contamination을 검사한다. 누출이 발견되면 해당 benchmark 결과는 qualification 근거로 사용 금지.

## 10. Learning Effectiveness Gate
승격 전후 Recall, Precision, FN, FP, Coverage, Detection Latency를 동일 benchmark 기준으로 비교한다. 개선 주장에는 baseline과 confidence/variance가 필요하다.

## 11. Rollback Gate
Rule/Detector/Prompt/Oracle 승격 후 품질 악화·drift·incident가 발생하면 이전 learning epoch로 복귀 가능해야 하고 rollback receipt를 남긴다.

## 12. Forget/Deletion Gate
Customer deletion/consent withdrawal/offboarding 시 원본뿐 아니라 derived learning lineage를 따라 영향 자산을 `REVOKE/DELETE/REQUALIFY_REQUIRED`로 처리한다.

## 13. Oracle Qualification Gate
Oracle은 validator와 별도 qualification 대상이다. Oracle version/hash/source/expected-result authority/qualification evidence를 기록하며 unqualified oracle은 PASS authority로 사용 금지.

## 14. Multi-Oracle Disagreement Gate
static/runtime/human/model oracle 간 의미 충돌이 있으면 `DISAGREEMENT/HOLD`로 처리하며 단순 다수결로 PASS 금지. resolution authority와 근거 필요.

## 15. Stochastic Validation Gate
AI/Agent 대상은 동일 조건 N-run 반복, seed/config/model/provider version 기록, 결과 분포·분산·불안정성을 측정한다. 단일 run PASS만으로 안정성 PASS 금지.

## 16. Metamorphic Validation Gate
정답을 직접 알기 어려운 경우 transformation과 invariant relation을 계약화하고 relation 위반을 FAIL/HOLD 근거로 사용한다.

## 17. Differential Validation Gate
이전 version, 다른 validator/model/provider/runtime 간 결과 차이를 자동 탐지하고 허용된 drift와 unexplained divergence를 구분한다.

## 18. Environment Matrix Gate
OS/JDK/DB/browser/runtime/model/provider/region 등 적용 가능한 환경축을 matrix로 선언하고 NOT_RUN/PARTIAL을 PASS로 승격하지 않는다.

## 19. Evidence Absence Gate
Canonical vocabulary는 `EVIDENCE_NOT_COLLECTED`, `EVIDENCE_COLLECTION_FAILED`, `OBSERVED_ABSENCE`, `EVIDENCE_UNAVAILABLE`, `EVIDENCE_STALE`, `EVIDENCE_PRESENT`로 고정한다. `EVIDENCE_NOT_COLLECTED`를 `OBSERVED_ABSENCE`로 해석하거나 자동 변환하는 것을 금지한다.

## 20. Validator Drift Gate
동일 Golden Set에 대해 validator version별 FP/FN/precision/recall trend를 기록하고 임계치 초과 drift 시 qualification을 재검토한다.

## 21. Challenge Set Gate
운영 신규 실패는 Golden set과 분리된 비공개 challenge set 후보로 관리하고, 학습 corpus와 분리해 future-generalization을 검증한다.

## 22. Blind Regression Gate
validator/learning asset 변경 후 과거 expected answer를 실행 주체가 보지 못하는 독립 blind rerun을 수행한다. 결과를 알고 튜닝한 동일 set 재실행만으로 regression PASS 금지.

## 23. Learning Stop Condition Gate
추가 학습의 marginal gain, regression risk, false-positive cost, coverage saturation, budget를 기준으로 CONTINUE/STOP/HOLD를 판정한다. 무한 반복 학습을 성공으로 간주하지 않는다.

## 24. Knowledge Freshness Gate
모든 active learning asset에 `valid_from`, `fresh_until` 또는 revalidation trigger, supersedes/superseded_by를 둔다. stale knowledge는 신규 final decision authority에서 제외한다.

## 25. Scope Promotion Gate
학습자산 scope는 `PRIVATE → ORGANIZATION → INDUSTRY → GLOBAL`로 분리한다. 상위 scope 승격에는 consent, anonymization/privacy proof, corpus-quality qualification, policy approval이 필요하다. 자동 상향 승격 금지.

## 26. Bias / Coverage Balance Gate
언어·framework·industry·tenant·risk class별 corpus 및 benchmark 분포를 측정하고 과소대표 영역을 UNKNOWN/COVERAGE_GAP으로 노출한다. 전체 평균 하나로 편향을 숨기지 않는다.

## 27. Final Closed-loop Gate
다음 직접 경로는 구조적으로 금지한다.
`LearningCandidate → PASS/FAIL`
허용 경로는 최소 다음이다.
`Observation → Candidate → Evaluation → Qualification → Approval → ActiveKnowledge → Validator Execution → Evidence → Independent/Policy Decision`

## 28. Design Lock 영향
146/147이 Requirement Authority Manifest에 NORMATIVE_REFINEMENT로 포함된 뒤에는 이 Gate들의 Requirement/Trace/Applicability/Contract/Test population이 생성되기 전 `DESIGN_BASELINE_READY_FOR_LOCK` 선언을 금지한다.
