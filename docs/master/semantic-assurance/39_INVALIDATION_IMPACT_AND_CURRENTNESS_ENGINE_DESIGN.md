# ONSure Invalidation Impact·Currentness Engine 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `29`, `30`, `38`

## 1. 목적
Source/Dependency/Config/Policy/Model/Authority/Validator/CVE/MissedFinding 등의 변화가 발생했을 때 어떤 Evidence, FinalLock, Deployment, Product Composition, Certificate를 STALE/REASSESSMENT_REQUIRED/INVALIDATED/REVOKED로 바꿔야 하는지 **결정론적으로 계산**한다.

## 2. 기본 원칙
- immutable historical record를 수정하지 않고 새 validity generation을 발행한다.
- event 이름만으로 전체 invalidate하지 않고 affected claim path를 계산한다.
- 반대로 영향 증명이 없다고 자동 SAFE로 두지 않는다. UNKNOWN impact는 REASSESSMENT_REQUIRED ceiling 후보다.
- REVOKED는 detector가 자동 발행하지 않는다. signed revocation authority가 필요하다.
- impact calculation 자체도 rule/profile version과 evidence를 가진다.

## 3. InvalidationEvent
필드 후보:
- event_id
- event_type
- observed_at
- effective_at nullable
- organization/tenant
- source_subject_ids[]
- changed_from_digest nullable
- changed_to_digest nullable
- triggering_evidence_digest
- severity/criticality
- authority_context nullable
- source_system
- confidence

Event Type:
SOURCE_CHANGED, BUILD_ARTIFACT_CHANGED, DEPENDENCY_CHANGED, CVE_DISCLOSED, CONFIG_CHANGED, FEATURE_FLAG_CHANGED, POLICY_CHANGED, REGULATION_CHANGED, ORACLE_CHANGED, VALIDATOR_CHANGED, VALIDATOR_DEFECT_DISCOVERED, QUALIFICATION_EXPIRED, MODEL_CHANGED, PROMPT_CHANGED, TOOL_CHANGED, RAG_CHANGED, EXTERNAL_CONTRACT_CHANGED, AUTHORITY_REVOKED, KEY_COMPROMISED, MISSED_FINDING_CONFIRMED, EVIDENCE_TAMPER_DETECTED, DEPLOYMENT_DRIFT, OBSERVER_FAILURE, CLOCK_TRUST_LOST.

## 4. Impact Rule
각 Rule:
- rule_id/version
- trigger_event_types[]
- source_node_types[]
- traversal_edge_types[]
- stop_conditions[]
- affected_claim_filter
- default_impact_class
- required_reassessment_operations[]
- policy_owner
- effective interval
- rule_digest

## 5. Impact Class
- NO_MATERIAL_IMPACT_PROVEN
- LIMITED_REPERFORMANCE_REQUIRED
- REASSESSMENT_REQUIRED
- INVALIDATING
- REVOCATION_REVIEW_REQUIRED
- UNKNOWN_IMPACT

`NO_MATERIAL_IMPACT_PROVEN`은 impact proof가 있을 때만 사용한다. 미탐지/미분류와 구분한다.

## 6. Graph Traversal Algorithm
1. event authenticity/tenant/context 검증
2. source nodes resolve
3. applicable impact rules freeze
4. traversable relation graph snapshot freeze
5. BFS/DFS deterministic traversal with canonical edge ordering
6. affected claims/receipts/finals/deployments/certificates 집합 생성
7. per-node impact class 계산
8. conflicts/unknowns 확인
9. new CurrentnessGeneration 작성
10. required operations queue 후보 생성
11. notification/audit receipt 생성

Graph mutation 도중 계산한 결과를 commit하지 않는다. frozen graph-head에 대해 계산하고 commit 전 graph head가 바뀌면 retry/reassessment한다.

## 7. Node별 처리
### EvidenceReceipt
input/policy/oracle/environment/validator identity가 event 영향경로에 있으면 STALE/INVALIDATED 후보.

### FinalLock
historical FinalLock bytes는 유지. material parent가 invalid/stale이면 Currentness를 하향.

### DeploymentRevision
artifact/config/dependency/runtime event에 직접 반응.

### CompositionSnapshot
필수 child currentness나 graph topology가 바뀌면 재계산 필요.

### Certificate
Certificate bytes 유지. verification API가 current validity generation을 결합해 반환.

## 8. CVE 처리
Dependency CVE Event에는 최소:
- package identity/version/artifact digest
- advisory id/version
- severity
- affected version range
- publication/updated timestamp
- exploitability/context

Affected dependency가 실제 build/runtime population에 존재하는지 SBOM/provenance로 확인한다. 단순 package name matching으로 INVALIDATE하지 않으며, match uncertainty가 있으면 REASSESSMENT_REQUIRED.

## 9. MissedFinding Historical Impact
Confirmed MissedFinding은:
- original blind spot rule/detector/oracle
- affected target archetype
- defect class
- scope pattern
- historical run population
을 기준으로 과거 결과를 탐색한다.

과거 run이 해당 surface를 검증했다고 주장했으나 detector가 blind했다면 SAFE로 유지하지 않는다. qualification impact와 customer certificate impact를 모두 계산한다.

## 10. Validator Defect / Qualification Impact
Validator bug가 발견되면 validator build digest를 통해 해당 validator가 사용된 모든 Evidence/Final/Certificate를 추적한다. defect가 특정 defect class에만 영향을 주면 claim-level impact를 우선하고, 범위를 입증할 수 없으면 broader REASSESSMENT_REQUIRED.

## 11. Authority / Key Impact
Key revoked/compromised event는 signing effect time과 policy를 고려한다.
- key가 정상 유효했던 과거 signature가 revocation 후에도 historical authenticity를 유지할 수 있는지
- compromise effective time 이전/이후
- policy가 retroactive invalidation을 요구하는지
를 분리한다.

단순 `revoked=true`만으로 모든 과거 승인 의미를 동일 처리하지 않는다.

## 12. Runtime Drift Impact
RuntimeInstance population drift가 발견되면 deployment/product certificate까지 즉시 currentness를 재평가한다. mismatch population이 serving traffic에 포함되는지 traffic authority와 함께 본다.

## 13. Recovery
재검증이 완료되면 이전 validity generation을 덮어쓰지 않고 새 generation:
- restored_by_run_ids
- restored_evidence_digest
- restored_qualification_digest
- restored_at
- previous_generation_digest
을 발행한다.

CURRENT 복원은 원 trigger의 required revalidation operations가 모두 evidence-bound 완료돼야 한다.

## 14. Idempotency / Duplicate Event
동일 semantic event를 provider/event ID만 바꿔 중복 수신할 수 있다. canonical event fingerprint를 사용해 logical duplicate를 식별하되, update된 advisory처럼 실제 의미 변경은 새 generation으로 처리한다.

## 15. Concurrency
동시에 여러 invalidation event가 발생하면:
- event ordering을 wall-clock만으로 정하지 않음
- effective time + causal relation + graph generation 사용
- independent events는 set composition으로 계산
- 서로 모순되는 impact result는 stronger negative를 무조건 고르는 대신 conflict/HOLD와 rule reason을 보존

## 16. Currentness Evaluation API 후보
- `POST /v2/currentness/events`
- `POST /v2/currentness/evaluate`
- `GET /v2/currentness/subjects/{id}`
- `GET /v2/currentness/events/{id}/impact`
- `POST /v2/currentness/recovery-evaluations`
- `GET /v2/certificates/{id}/current-validity`

## 17. Event
- InvalidationImpactCalculated
- HistoricalAssuranceAffected
- ReperformanceRequired
- RequalificationRequired
- CurrentnessGenerationCreated
- CurrentnessRestored
- ImpactCalculationConflict

## 18. Negative Test
- graph head 변경 중 stale traversal commit
- CVE package-name false match
- MissedFinding affected historical runs 누락
- validator defect인데 old certificate current 유지
- revocation event duplicate로 multiple state transitions
- rollback 후 required operations 미완료인데 CURRENT
- UNKNOWN impact를 SAFE로 변환
- stale child인데 Composition snapshot 재사용
- authority compromise effective time 무시
- observer failure 동안 no-drift 증명

## 19. Performance
대규모 historical graph에서는 reverse index를 유지한다.
- digest→evidence
- validator build→runs/certificates
- dependency artifact→targets
- policy/oracle→claims
- target→deployments/runtime/certificates
- requirement→claims/evidence

Impact scan은 전체 graph full scan보다 reverse index + graph traversal을 기본으로 한다. Index 결과는 graph-head/version과 결속한다.

## 20. 수용기준
- 동일 event + graph head + rule profile이면 동일 impact digest
- affected material node 누락을 검출하는 qualification corpus 존재
- UNKNOWN impact가 positive currentness로 fail-open하지 않음
- recovery는 required revalidation evidence 없이 CURRENT를 복원하지 않음
- Certificate verify는 latest valid currentness generation을 반영
- rule change는 historical impact rule 결과의 재평가 trigger가 됨

## 21. 비최종 경계
이 설계는 currentness engine candidate이며 실제 Certificate/Final 상태를 변경할 권위가 없다. Contract/fixture/runtime/historical qualification 이후 activation한다.
