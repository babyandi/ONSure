# 127 Safety·Hazard Assurance & Contestability·Appeal Governance

Status: `DESIGN_ONLY / INTEGRATED_SCOPE_EXTENSION / NON_FINAL`
Parent review: `126_FINAL_FRESH_PRODUCT_DESIGN_REVIEW.md`

## 1. Safety / Hazard Assurance

### 1.1 목적
ONSure가 의료·자동화·Agentic System·외부 Tool Effect·운영제어처럼 실패가 사람·재산·운영에 위해를 줄 수 있는 Target을 검증할 때, Security/Quality/Reliability와 분리된 Safety Assurance를 제공한다.

### 1.2 핵심 Entity
#### Hazard
- hazard_id
- target_subject_id/digest
- description
- initiating_conditions[]
- hazardous_scenario_ids[]
- potential_harms[]
- severity_class
- exposure_class nullable
- controllability_class nullable
- industry_risk_model_ref
- source/provenance

#### SafetyRequirement
- safety_requirement_id
- hazard_ids[]
- safe_behavior
- safe_state_definition
- degraded_safe_state nullable
- detection/response_time requirement
- verification_method
- evidence_requirement
- criticality

#### SafetyControl
- control_id
- safety_requirement_ids[]
- control_type: PREVENT|DETECT|LIMIT|ISOLATE|FAIL_SAFE|RECOVER
- independence_class
- implementation_subject_id
- bypass/override authority

#### SafetyCase
- safety_case_id
- subject_digest
- scope/requirement/policy epoch
- hazard population digest
- safety requirement population digest
- safety control population digest
- residual risk population digest
- claim-argument-evidence graph digest
- currentness
- decision

### 1.3 상태
Hazard disposition:
- IDENTIFIED
- ANALYZED
- CONTROL_REQUIRED
- CONTROLLED_PENDING_VALIDATION
- VALIDATED_CONTROLLED
- RESIDUAL_RISK_ACCEPTANCE_REQUIRED
- UNACCEPTABLE
- STALE

Safety Case decision:
- PASS_NONFINAL
- FAIL
- HOLD
- INCONCLUSIVE
- REASSESSMENT_REQUIRED

`PASS_NONFINAL`은 Product Final/Certificate를 직접 생성하지 않는다.

### 1.4 Safety Gate
Safety-applicable Target의 Critical Hazard에 대해 다음이 닫히지 않으면 positive high-assurance claim을 금지한다.
1. exact Hazard population
2. Safety Requirement trace
3. Safety Control mapping
4. fault/abnormal scenario verification
5. safe/degraded-safe state evidence
6. residual safety risk authority
7. current deployment/runtime identity
8. currentness / drift impact

### 1.5 Safety Verification
필수 시험 후보:
- loss of dependency
- stale/wrong sensor or input
- timeout/delay
- partial output
- conflicting control command
- tool/external effect failure
- restart/recovery
- failover
- operator override
- AI hallucinated unsafe action
- unsafe fallback
- cascading failure

Safety test는 정상기능 PASS와 분리한다.

### 1.6 Residual Safety Risk
Residual Safety Risk acceptance는 일반 개발자/Reviewer가 수행하지 못한다. Policy/Industry Profile이 정의한 Safety Authority를 요구한다.

허용되지 않는 축소:
- "발생확률이 낮음"만으로 catastrophic harm 무시
- security finding 0을 safety proof로 사용
- uptime/reliability metric만으로 safe behavior 주장
- customer waiver로 unvalidated critical safety requirement를 PASS 처리

### 1.7 Safety Currentness
다음 변경은 safety case impact 분석을 발생시킨다.
- safety-critical code/config
- threshold/limit
- model/prompt/tool authority
- dependency/fallback
- operator procedure
- environment/deployment
- hazard/risk model
- industry rule

## 2. Contestability / Appeal / Dispute Governance

### 2.1 목적
ONSure의 판정이 고객·전문가·발주기관·감사자에게 영향을 주는 경우, 원 판정을 삭제·덮어쓰기하지 않고 formal challenge와 독립 재심을 지원한다.

### 2.2 Challengeable Object
- Finding / Finding disposition
- Applicability / N/A / exclusion
- Accepted Risk disposition
- Verification/Review decision
- Human Acceptance interpretation
- Validator/Release Qualification
- Product Composition result/ceiling
- Currentness/Invalidation/Revocation
- Assurance Certificate
- Customer-facing Claim

### 2.3 AppealCase
필수 필드:
- appeal_case_id
- appellant principal/organization/authority
- challenged_object_type/id/digest
- challenged_generation/decision
- filed_at
- reason_code
- argument
- submitted_evidence_refs[]
- original evidence population digest
- appeal reviewer assignment
- independence profile
- status
- deadline/SLA
- decision receipt digest

### 2.4 Appeal Lifecycle
`FILED → ADMISSIBILITY_REVIEW → EVIDENCE_LOCKED → INDEPENDENT_REVIEW → DECIDED → IMPACT_APPLIED → CLOSED`

예외:
- REJECTED_OUT_OF_SCOPE
- WITHDRAWN
- HOLD_INPUT_REQUIRED
- INCONCLUSIVE

### 2.5 Appeal Decision
- UPHOLD
- REVERSE
- MODIFY
- REASSESSMENT_REQUIRED
- INCONCLUSIVE

원 Decision은 immutable historical record로 유지한다. Appeal 결과는 superseding generation을 만든다.

### 2.6 Independence / SoD
원 판정의 principal 또는 동일 credential-admin/implementation/oracle lineage가 appeal final reviewer가 될 수 없다.

고위험 대상:
- Critical Finding reversal
- Critical N/A 승인
- Safety residual risk
- Certificate revocation reversal
- Validator Qualification reversal
은 강화된 독립성과 multi-party authority를 요구한다.

### 2.7 New Evidence
Appeal 중 새 Evidence가 들어오면:
- 원 Evidence를 교체하지 않음
- new evidence origin/freshness/target binding 검증
- requirement/scope/policy epoch 불일치 확인
- material evidence이면 기존 Final/Certificate까지 impact propagation

### 2.8 Customer/UI Workflow
Finding/Certificate/Currentness 화면에는 권한 있는 사용자에게:
- Challenge decision
- challenge reason
- supporting evidence upload/ref
- status/SLA
- independent reviewer
- original vs appeal decision
- downstream impact
를 표시한다.

Support ticket과 AppealCase는 분리한다. Support는 문의이고 Appeal은 assurance authority에 영향을 줄 수 있는 formal workflow다.

### 2.9 Abuse / Safety Guard
- 반복 악의적 appeal rate limit은 가능하나 critical evidence 제출을 조용히 무시하지 않음
- appeal pending을 자동 PASS로 처리하지 않음
- appeal filing만으로 revocation을 자동 중단하지 않음
- emergency safety/security revoke는 appeal보다 우선할 수 있으나 사후 독립 review를 요구

## 3. Assurance Composition 영향
Safety-applicable subject에서 unresolved Critical Hazard가 있으면 Product Assurance는 high positive ceiling을 넘지 못한다.

Material Appeal이 OPEN이고 challenged object가 Final/Certificate의 critical parent이면 currentness는 최소 `REASSESSMENT_REQUIRED` 또는 정책상 HOLD ceiling을 적용한다.

## 4. Requirement 확장
신규 canonical requirement candidate:
- `FR-META-061 Safety/Hazard Assurance`
- `FR-META-062 Contestability/Appeal Governance`

이 ID는 다음 Requirement Universe materialization 때 exact global population에 포함한다.

## 5. Review Domain 확장
OReview/OVerification domain에 다음을 추가한다.
- Safety/Hazard Review
- Safety Control Verification
- Contestability/Appeal Integrity Review

## 6. Architecture/API 후보
- `POST /v2/safety/hazards`
- `POST /v2/safety/cases`
- `POST /v2/safety/cases/{id}/evaluate`
- `POST /v2/appeals`
- `POST /v2/appeals/{id}/evidence`
- `POST /v2/appeals/{id}/decide`
- `GET /v2/appeals/{id}/impact`

## 7. Event/Receipt 후보
- HazardIdentified
- SafetyRequirementCreated
- SafetyControlValidated
- ResidualSafetyRiskAcceptanceRequired
- SafetyCaseBecameStale
- AppealFiled
- AppealEvidenceLocked
- AppealDecisionIssued
- ChallengedAssuranceReassessmentRequired

Receipts:
- SafetyCaseReceipt
- ResidualSafetyRiskAcceptanceReceipt
- AppealDecisionReceipt
- AppealImpactReceipt

## 8. Negative Fixture
Safety:
- safety-critical failure treated as normal functional FAIL only
- unsafe fallback accepted because availability improved
- unvalidated safe state
- waived critical safety requirement
- stale safety case after threshold/config change

Appeal:
- original reviewer decides appeal
- original decision deleted after reversal
- new evidence replaces old bytes
- appeal pending treated as PASS
- certificate revocation silently cancelled on filing
- cross-tenant appellant
- unqualified expert overturns Critical Finding

## 9. 구현 경계
현재는 설계만 반영한다. Claude 기존 DEV-01~13 및 Batch F~K를 자동 변경하지 않는다. 후속 Contract/Runtime Batch에서 materialize한다.
