# 157 Learning/Validation Blind Final Gap Review

Status: `NORMATIVE_REFINEMENT / DESIGN_ONLY / NON_FINAL`
Scope: FR-LEARN-078~095
Purpose: FR-LEARN-001~077을 보지 않은 공격자/독립 검증자 관점에서 ground truth, uncertainty, privacy attack, adaptive adversary, incident response, subgroup harm, evidence diversity의 잔여 실패모드를 닫는다.

## FR-LEARN-078 Ground-truth Authority Governance
정답 label/expected result는 source, author, authority, version, dispute/appeal 상태를 가져야 한다. disputed/unqualified truth를 qualification 정답으로 사용하지 않는다.

## FR-LEARN-079 Ground-truth Drift / Label Evolution
규정·정책·도메인 지식 변화로 과거 정답이 바뀌면 affected benchmark, fixture, oracle, qualification, decision을 재평가한다. 과거 label을 영구 진실로 고정하지 않는다.

## FR-LEARN-080 Uncertainty Decomposition
최종 uncertainty를 최소 epistemic, aleatoric, evidence insufficiency, oracle disagreement, OOD uncertainty로 분리한다. 서로 다른 불확실성을 하나의 confidence 값으로 숨기지 않는다.

## FR-LEARN-081 Selective Prediction / Risk-Coverage Governance
ABSTAIN/HOLD 비율과 coverage를 함께 관리한다. 어려운 사례를 모두 abstain하여 precision만 높이는 metric gaming을 금지하고 risk-coverage curve를 qualification에 사용한다.

## FR-LEARN-082 Subgroup Robustness / Worst-group Assurance
전체 평균 외에 language/framework/industry/tenant/risk class 등 subgroup별 worst-group 성능과 FN/FP를 측정한다. 평균 PASS가 critical subgroup 실패를 덮지 못한다.

## FR-LEARN-083 Adaptive Adversary Evaluation
공격자가 validator/rule의 이전 판정과 방어를 관찰해 전략을 바꾸는 adaptive attack을 검증한다. 고정 adversarial fixture만으로 공격 내성을 주장하지 않는다.

## FR-LEARN-084 Privacy Membership / Inference Attack Resistance
학습자산·모델·응답에서 특정 고객/레코드의 학습 포함 여부나 민감 속성을 추론할 수 있는 membership/inference risk를 평가한다. privacy budget만으로 안전을 가정하지 않는다.

## FR-LEARN-085 Model Extraction / Knowledge Exfiltration Guard
반복 질의로 Rule/Detector/Prompt/Oracle/tenant knowledge를 복원하거나 추출하는 위험을 rate, query pattern, response minimization, access policy로 통제한다.

## FR-LEARN-086 Prompt / Tool / Retrieval Contamination Boundary
외부 문서·RAG·tool output·prompt injection이 learning candidate 또는 oracle truth로 승격될 때 trust boundary와 sanitization/qualification을 요구한다. retrieved text를 자동 truth로 취급하지 않는다.

## FR-LEARN-087 Evidence Diversity / Effective Independence
Evidence 수가 많아도 동일 source/runtime/model/log에서 파생되면 독립 증거로 중복 계산하지 않는다. evidence dependency graph와 effective independent evidence count를 유지한다.

## FR-LEARN-088 Counterevidence Preservation
PASS를 지지하는 증거뿐 아니라 반대 evidence, failed run, minority oracle, rejected reviewer 의견도 보존한다. 최종 decision package에서 counterevidence를 숨기지 않는다.

## FR-LEARN-089 Learning Incident Response
학습/validator 사고를 DETECT→CONTAIN→QUARANTINE→IMPACT→REMEDIATE→REQUALIFY→CLOSE 상태로 관리하고 incident receipt와 affected assets/decisions를 결속한다.

## FR-LEARN-090 Near-miss / Weak-signal Registry
실패 임계치 직전, 불안정성 증가, 반복 HOLD 등 near-miss를 별도 등록해 실제 사고 전 trend를 감지한다. PASS/FAIL 이진 결과만 저장하지 않는다.

## FR-LEARN-091 Assurance SLO / Error-budget Governance
validator availability뿐 아니라 stale qualification, unresolved disagreement, revalidation debt, FP/FN incident에 대한 assurance SLO/error budget을 둔다. budget 소진 시 신규 activation/learning 속도를 제한할 수 있다.

## FR-LEARN-092 Reproducibility Entropy / Nondeterminism Budget
동일 snapshot에서도 비결정적 결과가 발생하는 구성요소를 식별하고 허용 nondeterminism budget을 관리한다. 재현성 편차가 임계치를 넘으면 qualification을 HOLD한다.

## FR-LEARN-093 External Evaluation / Red-team Independence
고위험 learning/validator release는 개발자·학습 Agent와 독립된 evaluator/red-team의 검증을 요구할 수 있다. 동일 Agent의 self-red-team을 독립검증으로 계산하지 않는다.

## FR-LEARN-094 Decision Explanation Fidelity
설명은 실제 사용된 evidence/rule/oracle/policy 경로에서 생성되어야 한다. 사후 생성된 그럴듯한 설명이 실제 decision lineage와 불일치하면 explanation PASS 금지.

## FR-LEARN-095 Unknown-unknown Escalation
기존 taxonomy에 맞지 않는 반복 anomaly, unsupported artifact, novel failure를 UNKNOWN_UNKNOWN candidate로 등록하고 강제 분류하지 않는다. 임계치를 넘으면 human/domain review와 taxonomy expansion 검토를 요구한다.

## P0 우선 Gate
P0: 078, 080, 082, 083, 084, 086, 087, 088, 089, 093, 094, 095.

## 핵심 불변식
1. unqualified/disputed ground truth로 final qualification 금지.
2. average metric으로 critical worst-group failure 은폐 금지.
3. correlated evidence를 independent evidence로 중복 산입 금지.
4. counterevidence 삭제/비공개로 PASS 강화 금지.
5. learning incident 영향반경 미해결 상태에서 affected qualification CURRENT 유지 금지.
6. explanation lineage와 actual decision lineage 불일치 시 explanation 신뢰 금지.
7. unknown-unknown을 임의 known class로 강제 변환하여 PASS 금지.

## Design Lock 영향
FR-LEARN-078~095를 Requirement/Applicability/Trace/Test expectation에 반영하기 전 최종 Learning/Validation design closure 선언을 금지한다.