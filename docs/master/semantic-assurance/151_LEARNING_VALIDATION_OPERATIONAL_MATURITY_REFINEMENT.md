# 151 Learning & Validation Operational Maturity Refinement

Status: `NORMATIVE_REFINEMENT / DESIGN_ONLY / NON_FINAL`
Parents: `146_LEARNING_VALIDATION_CLOSED_LOOP_AND_META_ASSURANCE.md`, `147_LEARNING_VALIDATION_CLOSED_LOOP_QA_GATES.md`, `148_LEARNING_VALIDATION_IMPLEMENTATION_CONTRACT_BLUEPRINT.md`, `150_DESIGN_QA_LOCK_RERUN_AFTER_LEARNING_VALIDATION_REFINEMENT.md`
Purpose: 학습·검증 폐루프가 장기 운영 과정에서 자기오염·재검증 누락·영향전파 누락·비용 폭증으로 무너지지 않도록 3차 운영 성숙도 요구사항을 추가한다.

## FR-LEARN-041 Causal Attribution
학습자산 적용 후 품질 변화가 해당 변경 때문인지 검증해야 한다. 단순 before/after 상관관계만으로 효과를 확정하지 않으며, 가능한 경우 treatment/control, shadow comparator, counterfactual 또는 동등한 causal evidence를 사용한다.

## FR-LEARN-042 Learning Impact Radius
Rule/Oracle/Detector/Prompt/Corpus/Policy/Validator 변경마다 영향받는 Requirement, Decision, Evidence, Tenant, Target, Certificate, Learning Asset을 fan-out 분석한다. 영향반경 계산 없이 재검증 완료를 선언하지 않는다.

## FR-LEARN-043 Requalification Trigger Matrix
Model/provider/prompt/parser/runtime/policy/corpus/environment/tenant-scope/authority 변경 유형별 mandatory requalification set을 계약화한다. trigger가 발생하면 기존 qualification은 정책에 따라 STALE 또는 REQUALIFICATION_REQUIRED가 된다.

## FR-LEARN-044 Graceful Deprecation
Knowledge asset 폐기는 최소 `ACTIVE → DEPRECATED → GRACE → RETIRED` 또는 동등한 단계로 관리하며, 기존 판정 replay와 migration 기간을 보장한다. 즉시 RETIRED로 과거 판정 재현성을 깨뜨리지 않는다.

## FR-LEARN-045 Knowledge Compatibility
Knowledge/Rule/Oracle/Detector 버전 간 backward/forward semantic compatibility를 판정한다. 비호환 변경은 migration/requalification 없이 기존 decision authority를 상속하지 못한다.

## FR-LEARN-046 Cross-Tenant Transfer Risk
Organization/Industry/Global 학습자산은 source tenant와 별도 tenant holdout에서 transfer impact를 검증한다. 익명화만으로 cross-tenant 안전성을 가정하지 않으며 tenant-specific artifact의 자동 상위 scope 사용을 금지한다.

## FR-LEARN-047 Synthetic Data Collapse Guard
모든 learning asset은 `origin_type`과 `generation_depth`를 가진다. 최소 origin_type은 `REAL_OBSERVATION`, `HUMAN_AUTHORED`, `PUBLIC_REFERENCE`, `SYNTHETIC`, `DERIVED_SYNTHETIC`이다. synthetic-derived asset은 정책 임계 depth 이상에서 독립 real/reference evidence 없이 GLOBAL 승격할 수 없다.

## FR-LEARN-048 Feedback Loop Amplification Guard
Finding→Pattern→Rule→Finding처럼 자기 파생 계보가 다시 자기 근거로 사용되는 순환을 탐지한다. circular/self-derived evidence는 독립 evidence 수에 포함하지 않으며 unresolved circular support가 final PASS authority를 강화하지 못한다.

## FR-LEARN-049 Human Override Drift
Human/Professional Reviewer override를 taxonomy와 reason/evidence로 기록하고 반복 override pattern을 분석한다. 지속적 동일방향 override는 validator/reviewer/policy requalification trigger가 된다.

## FR-LEARN-050 Cost-Aware Assurance
학습·검증 개선은 accuracy만으로 승인하지 않는다. 최소 risk, FP/FN, coverage, runtime cost, latency, reviewer effort를 함께 평가하고 조직 정책의 Pareto/guardrail을 초과하는 개선은 HOLD 또는 별도 승인을 요구한다.

## FR-LEARN-051 Temporal Distribution Shift
시간대·계절·업무주기·시장/규제 이벤트에 따른 분포 변화를 별도 drift 축으로 관리한다. 시간창 benchmark와 historical window를 사용하고 특정 시점 데이터만으로 장기 안정성을 주장하지 않는다.

## FR-LEARN-052 Decision Debt / Revalidation Backlog
Knowledge/Policy/Oracle/Validator 변경으로 과거 decision이 재검증 대상이 되면 revalidation queue를 생성한다. severity, blast radius, customer exposure, age 기반 우선순위와 SLA를 갖고 stale decision debt를 가시화하며 backlog가 정책 임계치를 넘으면 신규 final assurance 범위를 제한할 수 있다.

## P0 우선 Gate
다음은 P0로 취급한다: FR-LEARN-042, 043, 046, 047, 048, 052.

## 추가 계약 경계
- LearningImpactAssessment
- RequalificationTriggerMatrix
- KnowledgeDeprecationPlan
- KnowledgeCompatibilityAssessment
- CrossTenantTransferAssessment
- LearningOriginLineage
- CircularSupportFinding
- HumanOverrideTrendReport
- AssuranceCostEffectivenessReport
- TemporalShiftAssessment
- RevalidationBacklog
- CausalEffectAssessment

## 핵심 불변식
1. impact radius가 unresolved이면 affected final decisions를 CURRENT로 유지하지 않는다.
2. requalification trigger 발생 후 stale qualification을 PASS authority로 사용하지 않는다.
3. cross-tenant transfer proof 없는 tenant-derived asset의 상위 scope 자동 사용 금지.
4. synthetic-derived ancestry가 정책 depth를 초과하면 independent anchor 없이 GLOBAL 승격 금지.
5. circular/self-derived evidence를 independent evidence로 중복 계산 금지.
6. revalidation backlog가 정책상 critical threshold를 넘으면 관련 assurance scope에 HOLD 또는 제한을 적용한다.
7. semantic-incompatible knowledge version 간 PASS/qualification 상속 금지.
8. causal attribution evidence가 불충분하면 `IMPROVEMENT_PROVEN` 대신 `CORRELATED_CHANGE_ONLY` 또는 HOLD를 사용한다.

## 구현 Batch 연결
- Batch 1: identity/state/compatibility/trigger contract
- Batch 3: impact radius, evidence ancestry, decision currentness
- Batch 4: cross-tenant transfer, privacy/scope
- Batch 5: causal attribution, synthetic lineage, override drift, temporal drift, cost-aware qualification
- Batch 7: deprecation/offboarding linkage
- Batch 8: compatibility migration/requalification/replay
- Batch 9: negative/adversarial/regression/transfer/circularity/backlog tests

## Design Lock 영향
FR-LEARN-041~052를 Requirement Authority Manifest와 Product Design RU에 반영하고 Applicability/Trace/Test expectation을 생성하기 전 `LEARNING_VALIDATION_DESIGN_CLOSED` 또는 `DESIGN_BASELINE_READY_FOR_LOCK`을 최종 선언하지 않는다.
