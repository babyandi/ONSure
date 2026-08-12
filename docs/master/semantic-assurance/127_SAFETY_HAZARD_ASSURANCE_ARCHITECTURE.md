# 127 Safety / Hazard Assurance Architecture

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `126_FINAL_FRESH_PRODUCT_DESIGN_REVIEW.md`

## 1. 목적
ONSure가 Security/Quality/Reliability와 별도로 **위해(Harm) 중심 Safety Assurance**를 수행할 수 있도록 한다. 공격자가 없어도 발생 가능한 hazardous behavior, fail-safe/degraded-safe, residual safety risk, safety acceptance를 독립 lifecycle로 관리한다.

## 2. 핵심 원칙
- `SECURE != SAFE`, `RELIABLE != SAFE`, `FUNCTIONALLY_CORRECT != SAFE`.
- Safety Claim은 단순 defect count가 아니라 Hazard→Scenario→Control→Evidence→Residual Risk chain으로 증명한다.
- safety-critical requirement가 NOT_RUN/UNKNOWN이면 상위 Safety PASS 금지.
- 일반 Risk Accept가 Safety Residual Risk를 자동 승인하지 않는다.
- safe state 진입 실패는 별도 Critical finding class다.
- safety mitigation이 다른 critical function을 파괴하면 PASS 금지.

## 3. 주요 Entity
### Hazard
- hazard_id
- subject_id
- hazard_type
- initiating_condition
- hazardous_state
- potential_harm[]
- affected_population/assets
- source/provenance
- status

### HazardousScenario
- scenario_id
- hazard_id
- preconditions
- trigger
- system_state
- environment_state
- human/operator interaction
- expected_safe_behavior
- observed_behavior

### SafetyRequirement
- safety_requirement_id
- hazard_ids[]
- safe_state
- degraded_safe_state nullable
- response_time/deadline nullable
- mandatory_control_ids[]
- verification_method
- criticality

### SafetyControl
- control_id
- prevention/detection/containment/recovery class
- independence class
- target component
- failure mode
- monitoring requirement

### SafetyCase
- safety_case_id
- subject/target digest
- requirement/hazard population digest
- claim_argument_graph_digest
- evidence_set_digest
- residual_risk_snapshot_digest
- decision/currentness

### ResidualSafetyRisk
- risk_id
- hazard/scenario binding
- initial risk
- mitigations
- residual risk
- uncertainty
- acceptance authority
- expiry/review trigger

## 4. Hazard Discovery
최소 discovery lane:
- requirements-derived hazards
- architecture/control-flow hazards
- FMEA/Fault Tree/What-if 계열
- human/operator misuse and foreseeable misuse
- external effect/tool hazards
- AI nondeterministic hazardous outcomes
- recovery/rollback hazards
- environment/dependency hazards

동일 분석기법 반복으로 신규 hazard 0건인 것을 saturation proof로 사용하지 않는다.

## 5. Risk Model
기본 engine은 산업별 profile을 허용한다. 후보 공통축:
- Severity
- Exposure/Likelihood
- Controllability/Detectability
- Uncertainty

산업별 규칙이 요구되면 profile-specific formula를 사용한다. 공통 숫자 하나를 모든 산업에 강제하지 않는다.

## 6. Safe State / Degraded Safe State
각 safety-critical scenario는 가능한 경우 다음을 선언한다.
- normal safe state
- degraded safe state
- transition trigger
- transition deadline
- operator notification
- recovery prerequisites

Fail-safe가 불가능한 경우 그 사실과 compensating control을 명시한다.

## 7. Verification
필수 검증 후보:
- safety requirement trace
- injected fault/hazard scenario
- control failure injection
- fail-safe transition
- timeout/deadline violation
- sensor/input corruption
- partial dependency failure
- operator error/foreseeable misuse
- AI stochastic hazardous outcome population
- recovery from safe/degraded state

## 8. Safety Decision
Decision 축:
- NOT_ASSESSED
- PASS_NONFINAL
- FAIL
- HOLD
- INCONCLUSIVE
- RESIDUAL_RISK_ACCEPTANCE_REQUIRED

Safety Assurance Strength는 기존 Assurance Tier와 별도로 claim scope를 표시하고, 제품 상위 Certificate에는 Safety scope/limitation을 명시한다.

## 9. Safety Acceptance Authority
Safety residual risk acceptance는 다음을 요구한다.
- explicit safety purpose
- authorized principal
- exact hazard/scenario/risk binding
- residual risk and uncertainty
- compensating controls
- expiry/review trigger
- signed receipt

개발자 또는 동일 검증자가 단독 승인하는 것을 금지할 수 있도록 Industry Policy에서 SoD를 강제한다.

## 10. Currentness / Invalidation
다음 변경은 SafetyCase 재평가 trigger다.
- safety requirement 변경
- architecture/control 변경
- model/prompt/tool 변경
- deployment environment 변경
- new incident/near miss
- new hazard discovery
- control qualification expiry
- safety-relevant dependency update

## 11. Incident / Near Miss Feedback
실제 incident와 near miss는 새로운 evidence이자 historical impact trigger다.
- incident_id
- affected subject/version
- hazard mapping
- previously-known vs newly-discovered
- certificate impact
- required revalidation

Near miss를 단순 운영로그로 묻지 않는다.

## 12. AI/Agent Safety
AI/Agent target은 추가로:
- hazardous tool action
- unsafe delegation/escalation
- unsafe autonomy boundary crossing
- delayed/omitted human handoff
- stochastic hazardous output rate
- unsafe memory/RAG influence
- model/provider safety drift
를 별도 claim으로 검증한다.

## 13. API/Contract 후보
- `HazardRegister`
- `SafetyRequirementSet`
- `SafetyControlProfile`
- `SafetyCaseSnapshot`
- `ResidualSafetyRiskReceipt`
- `SafetyIncidentImpactReceipt`

API 후보:
- `POST /v2/safety/hazards`
- `POST /v2/safety/cases`
- `POST /v2/safety/cases/{id}/evaluate`
- `POST /v2/safety/residual-risk/accept`
- `POST /v2/safety/incidents`
- `GET /v2/safety/impact/{incidentId}`

## 14. UI
- Hazard Register
- Hazard→Requirement→Control→Evidence trace
- Safe/Degraded-safe state
- unresolved safety unknowns
- residual risk acceptance
- incident/near-miss impact
- Safety Certificate scope/limitation

## 15. Negative/Adversarial Test
- functional PASS인데 fail-safe 미동작
- security PASS인데 hazardous autonomous action 발생
- 일반 Risk Accept로 safety residual risk 우회
- critical hazard를 N/A로 자기선언
- degraded mode가 실제로 더 위험함
- retry 후 hazardous attempt 삭제
- incident 발생 후 historical SafetyCase CURRENT 유지
- model alias 변경 후 safety evidence 재사용

## 16. 기존 설계 연결
- Requirement Universe: Safety requirement/hazard population 포함
- OReview: Safety/Hazard domain 추가
- OVerification: fault/hazard scenario lane 추가
- OImprovement: safety mitigation + regression
- Evidence Graph: Hazard/SafetyCase/ResidualRisk edge 추가
- Currentness: safety incident/new hazard invalidation
- Authority: residual safety risk acceptance SoD
- Certificate: Safety scope/limitation/currentness 공개
- Industry Profile: 의료/산업자동화 등 safety-specific rules

## 17. 현재 상태
`SAFETY_HAZARD_AXIS_DESIGNED / CONTRACT_RUNTIME_NOT_IMPLEMENTED / NON_FINAL`
