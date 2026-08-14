# 151 Learning & Validation Operational Second-order Risks

Status: `NORMATIVE_REFINEMENT / DESIGN_ONLY / NON_FINAL`
Parents: `146_LEARNING_VALIDATION_CLOSED_LOOP_AND_META_ASSURANCE.md`, `147_LEARNING_VALIDATION_CLOSED_LOOP_QA_GATES.md`, `148_LEARNING_VALIDATION_IMPLEMENTATION_CONTRACT_BLUEPRINT.md`, `150_DESIGN_QA_LOCK_RERUN_AFTER_LEARNING_VALIDATION_REFINEMENT.md`
Purpose: FR-LEARN-001~025가 닫은 기본 폐루프 위에서, qualification 이후 실제 운영 투입·장기 자기개선에서 발생하는 2차 실패를 방지한다.

## 1. FR-LEARN-026 Confidence Calibration / Abstention
Validator/model의 confidence는 실제 정확도와 calibration되어야 한다. calibration error, reliability curve, Brier/ECE 또는 동등 지표를 기록하고 임계치 미만·OOD·증거부족에서는 `ABSTAIN/HOLD`를 허용한다. raw model confidence만으로 PASS 금지.

## 2. FR-LEARN-027 Shadow / Canary Activation
학습자산의 운영 승격은 최소 `APPROVED -> SHADOW -> CANARY -> ACTIVE`를 지원한다. SHADOW는 최종 판정에 영향 없이 비교만 수행하고, CANARY는 제한된 tenant/scope/traffic에서만 영향 가능하다. offline qualification만으로 즉시 ACTIVE 금지.

## 3. FR-LEARN-028 Knowledge Conflict / Precedence
서로 다른 Active Rule/Detector/Oracle/Policy가 충돌할 수 있으므로 precedence/conflict graph와 resolution authority를 둔다. unresolved P0 conflict가 있는 knowledge set은 final PASS authority를 가질 수 없다.

## 4. FR-LEARN-029 Catastrophic Forgetting / Interference
새 learning epoch가 과거 capability를 잃게 만들 수 있으므로 이전 Golden/Blind/Challenge capability 집합에 대한 regression을 수행하고 forgotten-capability count와 severity를 기록한다. 신규 metric 개선만으로 승격 금지.

## 5. FR-LEARN-030 OOD / Novelty Detection
입력을 `KNOWN / NOVEL / OOD / UNSUPPORTED` 등으로 분류하고 OOD/UNSUPPORTED 입력에 일반화된 확신을 부여하지 않는다. OOD는 정책에 따라 HOLD/ABSTAIN/전문가 회부한다.

## 6. FR-LEARN-031 Active-learning Sampling Bias
학습 대상으로 어떤 사례를 선택했는지 selection policy, probability/score, strata, excluded population을 기록한다. uncertainty sampling 등 편향된 표본으로 얻은 효과를 전체 population 성능으로 일반화 금지.

## 7. FR-LEARN-032 Benchmark Overfitting Guard
Golden/Blind/Challenge benchmark의 노출 횟수, tuning exposure, near-duplicate relation을 기록한다. 반복 노출된 benchmark는 qualification 가치가 감소하며 unseen holdout 또는 교체 set으로 재검증한다.

## 8. FR-LEARN-033 Challenge Set Secrecy
비공개 Challenge Set의 정답/fixture 핵심은 evaluator-only scope로 분리하고 개발 Agent/학습 파이프라인에 노출 금지한다. 노출·유출되면 해당 set의 blind qualification authority를 즉시 폐기한다.

## 9. FR-LEARN-034 Statistical Qualification
precision/recall/FN/FP 개선 주장은 최소 표본수, confidence interval, variance, statistical power와 필요 시 multiple-comparison correction을 포함한다. 작은 표본의 point estimate만으로 qualification 금지.

## 10. FR-LEARN-035 Correlated Oracle Failure
여러 Oracle이 있어도 동일 model/provider/prompt/corpus/parser/runtime/human source에 의존하면 독립성이 아니다. Oracle Independence Graph를 유지하고 common-cause dependency가 임계치를 넘으면 독립 검증 개수로 산입하지 않는다.

## 11. FR-LEARN-036 Decision-time Knowledge Snapshot
모든 final decision은 당시의 KnowledgeEpoch, Active Rule/Detector/Oracle/Prompt/Policy/Corpus snapshot identity와 hash에 결속한다. 이후 knowledge 변경 후에도 과거 판정을 time-travel 재현할 수 있어야 한다.

## 12. FR-LEARN-037 Emergency Quarantine / Kill Switch
Active knowledge/validator에서 대량 오판·poisoning·security incident가 의심되면 rollback 완료 전이라도 즉시 `QUARANTINED`로 전환하고 신규 판정 영향력을 차단한다. affected decision/tenant/version blast radius를 계산하고 quarantine receipt를 남긴다.

## 13. FR-LEARN-038 Feedback-channel Poisoning Resistance
사용자 FP/FN 신고, appeal, thumbs-up/down 등 feedback을 곧바로 학습 truth로 사용하지 않는다. identity/trust/anti-sybil, corroborating evidence, independent confirmation을 거쳐 Candidate로만 승격한다.

## 14. FR-LEARN-039 IP / License Provenance
공개·외부·고객 유래 Pattern/Rule/Fixture/Corpus에도 license, usage right, redistribution/training permission, jurisdiction/contract restriction을 lineage로 결속한다. 권리 불명확 자산은 상위 scope/global promotion 금지.

## 15. FR-LEARN-040 Metric Gaming / Goodhart Guard
단일 metric 최적화를 성공으로 보지 않는다. precision, recall, FN, FP, coverage, latency, cost, reviewer burden, user harm/operational risk를 multi-objective로 평가하고 어느 하나의 심각한 regression을 평균값으로 은폐하지 않는다.

## 16. P0 Second-order Gates
P0: 026 Calibration/Abstention, 027 Shadow/Canary, 028 Conflict/Precedence, 029 Forgetting, 030 OOD, 034 Statistical Qualification, 035 Oracle Independence, 036 Decision-time Snapshot.

## 17. 직접 금지 경로
- RAW_CONFIDENCE -> FINAL_PASS
- APPROVED -> ACTIVE (shadow/canary policy 적용 대상에서 직접 승격)
- OOD -> PASS without explicit policy authority
- CORRELATED_ORACLES -> INDEPENDENT_MULTI_ORACLE_CLAIM
- EXPOSED_CHALLENGE_SET -> BLIND_QUALIFICATION
- SMALL_SAMPLE_POINT_ESTIMATE -> QUALIFIED
- FEEDBACK -> ACTIVE_KNOWLEDGE
- UNKNOWN_LICENSE_ASSET -> GLOBAL_SCOPE

## 18. Design Lock 영향
FR-LEARN-026~040은 Product Design Requirement Universe의 NORMATIVE_REFINEMENT로 materialize되어야 한다. Requirement/Applicability/Design Trace가 없는 상태에서 Learning/Validation Design Closed를 최종 선언하지 않는다. Runtime/validator evidence는 Claude 구현 단계에서 별도 필요하다.
