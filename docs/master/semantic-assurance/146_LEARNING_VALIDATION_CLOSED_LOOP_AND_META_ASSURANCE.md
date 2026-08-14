# 146 Learning & Validation Closed Loop / Meta-Assurance 설계 보강

Status: `NORMATIVE_REFINEMENT / DESIGN_ONLY / NON_FINAL`
Parent authorities: `02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md`, `07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md`, Semantic Assurance V2 design set
Purpose: ONSure가 사용하면서 검증 지식을 개선하되 자기확증·오염·권위혼합 없이 안전하게 학습하도록 Learning/Validation/Meta-Assurance 폐루프를 정의한다.

## 1. 설계 원칙
ONSure의 Learning은 하나가 아니다.

1. `TARGET_LEARNING`: 고객/대상 Program의 구조·행위·환경을 이해하고 ProgramProfile/AIProfile/DependencyInventory 등을 갱신한다.
2. `ASSURANCE_LEARNING`: Finding, Evidence, 실패, reviewer correction, false positive/negative에서 새로운 검증 지식 후보를 생성한다.
3. `VALIDATOR_LEARNING`: Rule, Detector, Oracle, Fixture, Prompt 등 ONSure 자신의 검증 자산을 개선한다.

세 종류는 동일 저장소를 사용할 수 있어도 authority와 승격 경로를 공유하지 않는다. 특히 `LearningCandidate`는 어떠한 경우에도 직접 PASS/FAIL/Final Decision authority가 될 수 없다.

### 절대 불변식
`Observation -> Candidate -> Independent Evaluation -> Qualification -> Approval -> Active Knowledge`

다음 경로는 금지한다.

`Observation -> Active`
`LearningCandidate -> PASS/FAIL`
`OLearning self-evaluation -> QUALIFIED`
`same model/same run self-approval -> APPROVED`

## 2. Learning Candidate Lifecycle
모든 학습자산은 다음 공통 상태기계를 따른다.

`OBSERVED -> CANDIDATE -> EVALUATED -> QUALIFIED -> APPROVED -> ACTIVE -> RETIRED`

보조상태: `REJECTED`, `QUARANTINED`, `STALE`, `REVOKED`, `SUPERSEDED`.

### 상태 의미
- `OBSERVED`: 단일 또는 복수 Observation에서 패턴이 관찰되었지만 학습자산으로 아직 식별되지 않음.
- `CANDIDATE`: identity/hash/provenance가 부여된 평가대상.
- `EVALUATED`: Golden/Challenge/negative/cross-target 평가가 실행되었으며 결과가 존재함. PASS를 의미하지 않음.
- `QUALIFIED`: 정의된 qualification threshold와 contamination/independence gate를 통과함.
- `APPROVED`: 권한 있는 Human/Policy Authority가 사용범위와 risk를 승인함.
- `ACTIVE`: 런타임 검증기가 사용할 수 있음.
- `RETIRED`: 신규 판정에는 사용하지 않되 provenance/replay를 위해 보존.

`STALE`, `REVOKED`, `QUARANTINED` 자산은 Active decision path에서 fail-closed 제외한다.

## 3. 학습 산출물 타입
공통 `LearningAsset` 아래 최소 다음 타입을 둔다.

- `PatternCandidate`: 반복 구조/오류/행위 패턴
- `RuleCandidate`: deterministic 또는 policy rule 후보
- `FixtureCandidate`: positive/negative/adversarial/reproduction fixture 후보
- `OracleCandidate`: expected-result/ground-truth 판정자 후보
- `DetectorCandidate`: 탐지기/분류기/heuristic/model 후보
- `PromptCandidate`: AI reviewer/verifier prompt 후보

추가 확장 가능 타입: `PolicyCandidate`, `MetamorphicRelationCandidate`, `EnvironmentRiskCandidate`, `FailureSignatureCandidate`.

모든 타입은 공통적으로 `asset_id`, `asset_type`, `version`, `state`, `scope`, `provenance`, `content_hash`, `created_by`, `independent_evaluator`, `qualification_receipt`, `approval_receipt`, `valid_from`, `fresh_until`, `supersedes`, `revocation_reason`을 가져야 한다.

## 4. 학습 Provenance / Lineage
학습자산은 어떤 근거에서 만들어졌는지 완전 역추적되어야 한다.

최소 lineage:
`Organization/Tenant -> Target -> Program -> Baseline -> Execution -> Finding/Observation -> Evidence -> Candidate -> Evaluation -> Qualification -> Approval -> Active Asset -> Validation Decision`

각 edge는 source/target identity, relationship type, timestamp, policy version, content hash를 가진다.

파생 학습자산은 원본이 삭제·철회·오염판정될 때 영향분석할 수 있도록 transitive lineage를 유지한다. Lineage가 끊어진 자산은 `PROVENANCE_INCOMPLETE`로 처리하고 Active 승격을 금지한다.

## 5. Negative Learning / Failure Registry
ONSure는 성공 사례만 학습하지 않는다. 다음은 모두 1급 학습자산 원천이다.

- False Positive
- False Negative
- 잘못 확정된 Finding
- Reviewer correction/appeal reversal
- rejected patch
- test escaped defect
- runtime incident
- stale rule miss
- validator crash/timeout
- unsupported target/environment
- evidence collection failure

`FailureRegistry`는 failure_id, target/version, symptom, root cause, escaped gate, missed detector/oracle, reproduction fixture, mitigation, recurrence count, first_seen/last_seen, linked candidate, resolution status를 유지한다.

같은 failure signature가 재발하면 기존 closed item을 새 항목으로 숨기지 않고 recurrence lineage를 연결한다.

## 6. Corpus Quality / Poisoning / Tenant Leakage
Corpus ingestion 전 다음 Gate를 거친다.

- duplicate/near-duplicate detection
- conflicting label detection
- corrupted/truncated evidence detection
- provenance completeness
- consent/scope authorization
- PII/secret/data-classification policy
- tenant leakage 검사
- poisoning/anomalous contribution 탐지
- source trust/age/freshness 평가
- class/language/framework distribution 검사

오염 의심 항목은 `QUARANTINED`로 이동하며 Golden/Challenge/Training 어디에도 자동 사용하지 않는다.

`FR-COM-009`의 Opt-in/Opt-out은 단순 원본 공유 여부가 아니라 파생 학습자산 lineage에도 적용한다. 조직의 contribution 철회가 들어오면 영향받는 derived assets를 찾아 `REVALIDATE`, `REVOKE`, `REBUILD`, `NO_ACTION_WITH_PROOF` 중 하나로 disposition한다.

## 7. Train/Test Leakage / Contamination Gate
Training/learning corpus와 검증용 corpus는 identity/hash lineage 수준에서 분리한다.

Corpus partition:
- `TRAINING_OR_LEARNING`
- `DEVELOPMENT`
- `GOLDEN_QUALIFICATION`
- `BLIND_REGRESSION`
- `PRIVATE_CHALLENGE`

동일 source 또는 semantic near-duplicate가 qualification/blind/challenge에 유입되면 contamination finding을 발생시키고 해당 qualification을 무효화한다.

Golden/Blind/Challenge set의 정답 및 identity는 검증기 개발 agent가 불필요하게 열람하지 못하도록 역할/권한을 분리한다.

## 8. 학습 효과 측정
Candidate/Active asset의 승격과 유지에는 baseline 대비 효과 측정이 필요하다.

최소 지표:
- Precision
- Recall
- False Positive Rate
- False Negative Rate
- Coverage
- Detection Latency
- Execution Cost
- Stability/Variance
- cross-language/framework/tenant slice performance

단일 aggregate score로 승격하지 않는다. Critical severity의 FN 증가, 특정 tenant/language slice의 급격한 품질하락 등 safety-critical regression은 평균 개선으로 상쇄할 수 없다.

모든 승격은 `before_version`, `candidate_version`, benchmark population digest, metric delta, threshold, regression exceptions를 기록한다.

## 9. Rollback / Learning Epoch
Active 학습자산 집합은 `LearningEpoch`로 versioning한다.

LearningEpoch는 active asset set digest, policy version, validator version, corpus snapshot/digest, qualification receipts를 포함한다.

새 epoch가 품질을 악화시키거나 incident를 만들면 이전 qualified epoch로 원자적 rollback 가능해야 한다.

Rollback 후 새 epoch가 만든 판정은 자동 PASS 상속하지 않고 영향받은 Decision/Evidence를 `REVALIDATION_REQUIRED`로 전환한다.

## 10. Forget / Deletion / Contribution Withdrawal
삭제·철회 처리 단위:
1. raw source
2. observation/evidence
3. derived candidate
4. active learning asset
5. model/detector artifact에 내재된 영향

단순 raw 삭제로 완료하지 않는다. lineage를 따라 파생영향을 계산하고 다음 disposition을 남긴다.

- `DELETED`
- `REVOKED`
- `RETRAIN_REQUIRED`
- `REQUALIFICATION_REQUIRED`
- `CRYPTOGRAPHICALLY_UNLINKABLE_WITH_PROOF`
- `LEGAL_RETENTION_HOLD`

Offboarding Closure Receipt에는 learning-derived impact disposition을 포함한다.

## 11. Oracle Qualification
Oracle은 Expected Result를 결정하는 별도 검증자산이다. Target만 검증하고 Oracle을 신뢰하는 구조를 금지한다.

Oracle 종류:
- deterministic/specification oracle
- reference implementation oracle
- runtime oracle
- statistical oracle
- human/professional oracle
- AI/model oracle
- composite/multi-oracle

Oracle lifecycle도 Learning Candidate Lifecycle과 동일한 qualification/approval을 적용한다.

Oracle qualification 최소조건:
- ground-truth provenance
- independent benchmark
- known error/boundary set
- false-positive/false-negative measurement
- environment/version scope
- freshness
- independent evaluator

Oracle이 unqualified/stale/revoked이면 해당 Oracle에 의존하는 final PASS를 금지한다.

## 12. Multi-Oracle Disagreement
둘 이상의 Oracle이 충돌하면 단순 majority vote로 PASS하지 않는다.

Decision state에 `ORACLE_DISAGREEMENT`를 둔다.

충돌 레코드에는 oracle identities/versions, outputs, confidence, evidence, environment, disagreement class를 기록한다.

Critical/High 또는 Final Decision 영향이 있으면 independent resolver 또는 Human/Professional Reviewer로 회부한다. 해결 전 overall decision은 PASS가 될 수 없다.

## 13. Stochastic Validation
비결정적 AI/Agent/LLM 동작은 1회 PASS로 확정하지 않는다.

동일 조건 반복 `N-run`을 지원하고 최소 다음을 기록한다.
- run count
- success/failure distribution
- output semantic variance
- latency/cost variance
- tool-call variance
- safety/policy violation frequency
- seed/provider/model/version/environment

N과 threshold는 risk tier/policy profile별로 다르게 설정한다. 반복 중 Critical failure가 1회라도 발생하면 평균 성공률로 상쇄하지 못하는 정책을 지원한다.

## 14. Metamorphic Validation
정답 Oracle을 직접 만들기 어려운 대상에는 Metamorphic Relation을 사용한다.

예:
- 입력 순서가 의미에 영향을 주지 않아야 하는 경우 permutation invariant
- 동일 의미의 format 변환 후 결과 semantic equivalence
- 권한 축소 후 capability 증가 금지
- irrelevant context 추가 후 critical decision 변화 금지

Metamorphic relation 자체도 `MetamorphicRelationCandidate -> QUALIFIED -> ACTIVE` lifecycle을 거친다.

## 15. Differential Validation
동일 Target을 다음과 비교한다.
- previous target version
- previous validator version
- alternate validator/model/provider
- reference implementation
- alternate environment

차이를 자동 분류한다: `EXPECTED_CHANGE`, `UNEXPLAINED_DRIFT`, `REGRESSION`, `IMPROVEMENT`, `INCONCLUSIVE`.

`UNEXPLAINED_DRIFT`가 Critical/High 영역에 있으면 PASS를 차단한다.

## 16. Environment Matrix
검증결과는 environment-independent라고 가정하지 않는다.

Environment identity 최소축:
OS, CPU/architecture, runtime/JDK/.NET/Python, DB/version, browser, container/runtime, library/dependency set, model/provider/version, locale/timezone, network/security mode.

정책상 필요한 environment matrix가 미실행이면 해당 조합은 `NOT_RUN` 또는 `COVERAGE_GAP`이지 PASS가 아니다.

환경별 결과 divergence는 Differential Validation으로 연결한다.

## 17. Evidence Absence Semantics
다음 상태를 구분한다.
- `EVIDENCE_NOT_COLLECTED`: 수집 실행 자체 없음
- `EVIDENCE_COLLECTION_FAILED`: 실행했으나 수집 실패
- `OBSERVED_ABSENCE`: 검사가 정상 실행됐고 기대/탐지 대상이 관찰되지 않음
- `EVIDENCE_UNAVAILABLE`: 외부 제약으로 확보 불가
- `EVIDENCE_STALE`: 과거 증거만 존재
- `EVIDENCE_PRESENT`: 유효 증거 존재

`EVIDENCE_NOT_COLLECTED`, `FAILED`, `UNAVAILABLE`, `STALE`을 `OBSERVED_ABSENCE` 또는 PASS로 변환하지 않는다.

## 18. Validator Drift
각 validator/detector/oracle 버전은 고정된 Golden Set과 rolling Challenge Set에 대한 성능 추세를 가진다.

추적 지표: FP/FN, severity별 miss, coverage, latency, crash/timeout, instability, environment-specific regression.

Drift threshold 초과 시 상태를 `STALE` 또는 `REQUALIFICATION_REQUIRED`로 전환하고 Active 사용을 중단할 수 있어야 한다.

## 19. Private Challenge Set
운영에서 발견된 새로운 escaped defect/failure는 기존 training/golden set에 즉시 섞지 않는다.

먼저 비공개 `PRIVATE_CHALLENGE` 후보로 편입하고 identity/answer access를 제한한다. Validator 개선 후 이 set으로 unseen behavior를 평가한다.

Challenge 항목을 개발자가 이미 열람했으면 blind 자격을 잃고 다른 set으로 대체한다.

## 20. Blind Regression
Validator/Rule/Prompt/Oracle 변경 후 개발 주체가 정답을 모르는 independent blind rerun을 지원한다.

Blind evaluator는 원 implementation agent와 독립된 identity/role/model family를 사용할 수 있어야 하며, answer leakage 여부를 기록한다.

Blind 결과는 ordinary unit test PASS와 별도의 qualification evidence다.

## 21. Learning Stop Condition
더 많이 학습한다고 무조건 좋아진다고 가정하지 않는다.

다음 중 하나면 candidate/epoch promotion을 중단하거나 HOLD한다.
- marginal improvement below threshold
- FP/FN trade-off가 policy 한계 초과
- critical slice regression
- corpus diversity 부족
- contamination 의심
- instability/variance 증가
- cost/latency budget 초과
- independent evaluation 부족
- unresolved oracle disagreement
- unresolved P0/P1 safety finding

Stop condition은 실패가 아니라 `NO_PROMOTION`이라는 정상 판정일 수 있다.

## 22. Knowledge Freshness
모든 Active Pattern/Rule/Fixture/Oracle/Detector/Prompt는 freshness metadata를 갖는다.

- `valid_from`
- `fresh_until`
- `last_qualified_at`
- `revalidate_on`
- `superseded_by`
- `environment_scope`
- `target_scope`

`fresh_until` 경과 또는 dependency/provider/policy/environment material change 발생 시 자동 `REQUALIFICATION_REQUIRED`로 전환한다.

## 23. Tenant / Knowledge Scope Promotion
학습자산 scope:

`PRIVATE -> ORGANIZATION -> INDUSTRY -> GLOBAL`

상위 scope 승격은 자동 전파가 아니다. 각 단계마다 consent, anonymization/de-identification, provenance, leakage, diversity, qualification, policy approval을 새로 검사한다.

`PRIVATE` 또는 Opt-out 자산은 global corpus로 직접 이동하지 못한다.

공개 CVE/표준 등 고객 데이터와 무관한 public-source knowledge는 별도 `PUBLIC_REFERENCE` provenance를 사용한다.

## 24. Bias / Coverage Balance
학습 corpus와 validator 성능을 전체 평균뿐 아니라 slice별로 평가한다.

권장 slice:
language, framework, architecture, DB, cloud/on-prem, OS, industry, organization size, AI/non-AI, model/provider, severity class, target age/version.

특정 slice의 데이터 부족은 `COVERAGE_IMBALANCE`로 기록하고, 해당 slice에 대한 과도한 confidence/coverage claim을 금지한다.

## 25. Closed-loop Operation
전체 폐루프:

`Target Learning -> Planning -> Review/Verification -> Evidence/Finding -> Feedback/Failure Registry -> LearningCandidate -> Independent Evaluation -> Golden/Challenge/Blind/Metamorphic/Differential Validation -> Qualification -> Approval -> LearningEpoch Activation -> Validator Runtime -> Drift/Incident Monitoring -> Requalification/Rollback/Retirement`

학습과 검증은 연결되지만 Final Authority는 분리한다.

## 26. 신규 기능 요구사항
- `FR-LEARN-001` ONSure는 Target/Assurance/Validator Learning을 별도 authority domain으로 구분해야 한다.
- `FR-LEARN-002` 모든 학습자산은 공통 Candidate Lifecycle과 immutable provenance를 가져야 한다.
- `FR-LEARN-003` LearningCandidate가 qualification/approval 없이 Active decision path에 들어가는 것을 fail-closed 차단해야 한다.
- `FR-LEARN-004` False Positive/False Negative/rejected finding/escaped defect를 Negative Learning 자산으로 보존해야 한다.
- `FR-LEARN-005` Failure Registry는 재현 fixture와 recurrence lineage를 유지해야 한다.
- `FR-LEARN-006` Corpus는 poisoning, duplication, label conflict, tenant leakage, consent 위반을 검증해야 한다.
- `FR-LEARN-007` Training/Golden/Blind/Private Challenge 간 contamination을 탐지하고 오염된 qualification을 무효화해야 한다.
- `FR-LEARN-008` Candidate/epoch promotion은 baseline 대비 precision/recall/FP/FN/coverage/latency/stability 변화를 기록해야 한다.
- `FR-LEARN-009` Active LearningEpoch는 이전 qualified epoch로 rollback 가능해야 하고 영향받는 판정을 재검증 대상으로 전환해야 한다.
- `FR-LEARN-010` 삭제/기여철회는 derived learning lineage까지 영향분석해야 한다.
- `FR-LEARN-011` Oracle 자체를 qualification 대상으로 취급해야 한다.
- `FR-LEARN-012` Multi-Oracle disagreement가 unresolved인 상태에서는 영향받는 Final PASS를 금지해야 한다.
- `FR-LEARN-013` 비결정적 AI/Agent 검증은 정책에 따른 N-run stochastic validation과 variance 측정을 지원해야 한다.
- `FR-LEARN-014` 정답 Oracle이 부족한 대상은 qualified Metamorphic Relation으로 검증할 수 있어야 한다.
- `FR-LEARN-015` target/validator/model/environment 버전 간 Differential Validation을 지원해야 한다.
- `FR-LEARN-016` 요구된 Environment Matrix에서 실행되지 않은 조합을 PASS로 표현해서는 안 된다.
- `FR-LEARN-017` Evidence Absence의 원인을 NOT_COLLECTED/FAILED/OBSERVED_ABSENCE/UNAVAILABLE/STALE/PRESENT로 구분해야 한다.
- `FR-LEARN-018` Validator/Oracle/Detector drift를 Golden/Challenge 기준으로 지속 측정하고 threshold 초과 시 requalification을 요구해야 한다.
- `FR-LEARN-019` 운영에서 발견된 신규 실패를 answer leakage가 통제된 Private Challenge Set으로 관리해야 한다.
- `FR-LEARN-020` validator 변경 후 독립 blind regression evidence를 생성할 수 있어야 한다.
- `FR-LEARN-021` Learning Stop Condition을 만족하지 못하면 더 많은 학습을 이유로 promotion을 강행해서는 안 된다.
- `FR-LEARN-022` Active knowledge는 freshness/supersession/revalidation 조건을 가져야 한다.
- `FR-LEARN-023` 학습자산의 PRIVATE/ORGANIZATION/INDUSTRY/GLOBAL scope 승격은 단계별 consent/qualification gate를 거쳐야 한다.
- `FR-LEARN-024` corpus와 validator 성능은 주요 language/framework/industry/environment slice별 coverage/bias를 측정해야 한다.
- `FR-LEARN-025` OLearning/학습 주체는 자신이 생성한 Candidate를 단독으로 QUALIFIED/APPROVED/ACTIVE로 승격할 수 없어야 한다.

## 27. P0 Gate
다음은 구현·릴리스 전 P0다.
1. Learning Candidate Promotion Gate
2. Oracle Qualification
3. False Positive/False Negative Feedback Loop
4. Corpus Contamination/Poisoning Gate
5. Tenant Consent/Deletion/Derived Lineage
6. Validator Regression/Rollback
7. Learning Authority와 Final Decision Authority 완전 분리

P0 중 하나라도 구현/검증/증거가 없으면 Learning 기능을 `SELF_IMPROVING_VALIDATOR` 또는 이에 준하는 표현으로 Release/Marketing claim 하는 것을 금지한다.

## 28. 후보 Contract / Program 경계
후속 개발에서 최소 다음 계약군을 후보화한다.
- LearningAsset / LearningCandidate
- LearningEpoch
- LearningProvenanceGraph
- FailureRegistryEntry
- CorpusPartition / CorpusQualityReport / ContaminationReport
- LearningEvaluationReport / LearningQualificationReceipt / LearningApprovalReceipt
- Oracle / OracleQualificationReceipt / OracleDisagreement
- StochasticValidationReport
- MetamorphicRelation / MetamorphicValidationReport
- DifferentialValidationReport
- EnvironmentMatrix / EnvironmentExecutionCoverage
- EvidenceAbsenceRecord
- ValidatorDriftReport
- ChallengeSet / BlindRegressionReceipt
- KnowledgeFreshnessRecord
- ScopePromotionReceipt
- LearningDeletionImpact / LearningRollbackReceipt

이 목록은 design identity다. schema file 존재만으로 구현 완료로 승격하지 않는다.

## 29. Done Gate
Learning/Validation Closed Loop의 설계 완료와 구현 완료는 분리한다.

구현 Done Gate 최소조건:
`Contract -> Runtime/Implementation -> Positive Fixture -> Negative Fixture -> Cross-contract invariant -> Compile -> Automated Test -> Golden/Challenge/Blind evidence -> Qualification/Approval evidence -> Trace update`

특히 다음 negative case는 필수다.
- Candidate가 approval 없이 ACTIVE 승격 시도
- poisoned/tenant-leaked corpus 사용
- training item의 blind set 유입
- stale/revoked Oracle로 PASS 시도
- unresolved Oracle disagreement 상태의 PASS
- single stochastic run만으로 Final PASS
- unexecuted environment를 PASS로 표시
- EVIDENCE_NOT_COLLECTED를 OBSERVED_ABSENCE로 위조
- deteriorated epoch의 rollback 실패
- deleted/withdrawn source의 derived asset 계속 ACTIVE

## 30. Design QA 영향
이 문서 추가로 Product Design Requirement Universe에는 `FR-LEARN-001~025`가 신규 normative requirement로 materialize되어야 한다.

Design QA에서 다음을 재계산한다.
- Requirement exact denominator
- Applicability
- Requirement -> Design/Contract/Test/Evidence trace
- orphan/reverse orphan
- authority manifest digest
- baseline manifest/reconstructability

이 문서 자체가 Product Design Requirement Authority에 포함되지 않은 상태에서 FR-LEARN-001~025를 denominator에 산입해서는 안 된다.
