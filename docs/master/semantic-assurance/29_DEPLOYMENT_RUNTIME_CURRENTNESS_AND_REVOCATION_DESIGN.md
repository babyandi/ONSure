# ONSure Deployment·Runtime Currentness·Revocation 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `00_ONSURE_MASTER_DESIGN_SET.md`, `02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md`, `04_ARCHITECTURE_DATA_API_OLICENSE.md`

## 1. 목적
기존 ONSure의 검증 결과가 `검증 시점의 artifact`에만 결속되고 실제 배포·실행 중 상태와 분리되는 문제를 해소한다. 이 설계는 다음 질문을 기계적으로 답할 수 있어야 한다.

1. 검증한 정확한 artifact가 어디에 배포되었는가?
2. 실제 실행 중인 instance가 그 artifact에서 유래했는가?
3. 검증 후 config/dependency/model/prompt/RAG/external contract가 변했는가?
4. 변경이 기존 Assurance를 STALE/INVALIDATED/REVOKED로 만들어야 하는가?
5. rollback/redeploy/recovery 후 어떤 검증을 다시 해야 하는가?

## 2. 핵심 원칙
- `VERIFIED != DEPLOYED != RUNNING != CURRENT`.
- 배포 성공은 Assurance 성공이 아니다.
- runtime health success는 semantic assurance success가 아니다.
- source commit 동일성만으로 deployed identity를 증명하지 않는다.
- immutable build artifact digest와 deployed artifact digest를 직접 비교한다.
- running instance는 deployment record와 별도 identity를 가진다.
- runtime/config/model/RAG drift는 변경 종류별 invalidation rule을 적용한다.
- rollback은 이전 Assurance를 자동 복원하지 않는다. rollback 대상 artifact의 당시 Assurance가 현재 policy/validator/authority 기준에서도 유효한지 재평가한다.
- FinalLock은 영구 상태가 아니라 특정 generation/context에 대한 historical fact다.

## 3. Entity
### 3.1 BuildArtifactIdentity
- artifact_id
- artifact_sha256
- artifact_type
- build_receipt_digest
- source_tree_digest
- dependency_set_digest
- sbom_digest
- provenance_digest
- created_at

### 3.2 DeploymentTarget
- deployment_target_id
- organization_id
- tenant_id
- target_id
- environment_class: DEV|TEST|STAGE|PROD|DR
- provider_type
- region/zone
- cluster_id nullable
- namespace/account/project
- target_binding_digest

### 3.3 DeploymentRevision
- deployment_revision_id
- deployment_target_id
- expected_artifact_digest
- observed_artifact_digest
- deployment_manifest_digest
- config_digest
- secret_reference_set_digest
- dependency_runtime_digest
- deployment_strategy: RECREATE|ROLLING|BLUE_GREEN|CANARY
- rollout_generation
- started_at/completed_at
- state

### 3.4 RuntimeInstance
- runtime_instance_id
- deployment_revision_id
- node/pod/process identity
- boot_id
- observed_artifact_digest
- observed_config_digest
- observed_dependency_digest
- model_runtime_digest nullable
- prompt_bundle_digest nullable
- rag_runtime_digest nullable
- external_contract_set_digest
- first_seen_at/last_seen_at
- health_state

### 3.5 AssuranceCurrentnessSnapshot
- currentness_snapshot_id
- final_lock_digest
- target_manifest_digest
- deployment_revision_id
- runtime_population_digest
- policy_epoch
- validator_qualification_epoch
- authority_epoch
- observation_epoch
- evaluated_at
- state: CURRENT|STALE|REASSESSMENT_REQUIRED|INVALIDATED|REVOKED|UNKNOWN
- reasons[]
- next_required_actions[]

## 4. Verified-to-Deployed-to-Running 계보
필수 chain:
`SourceSnapshot -> BuildReceipt -> BuildArtifactIdentity -> VerificationEvidence -> FinalLock -> DeploymentRevision -> RuntimeInstancePopulation -> CurrentnessSnapshot`

모든 edge는 ID가 아니라 digest로도 결속한다.

### 4.1 Verified-to-Deployed
- FinalLock이 참조하는 verified artifact digest와 DeploymentRevision.observed_artifact_digest가 같아야 한다.
- 배포 시스템이 보고한 desired image tag만 신뢰하지 않고 registry digest/runtime read-back을 사용한다.
- mutable tag(`latest`, branch tag)는 identity authority가 될 수 없다.

### 4.2 Deployed-to-Running
- DeploymentRevision의 artifact와 각 RuntimeInstance의 실제 observed artifact가 같아야 한다.
- cluster의 일부 instance가 다른 digest이면 `PARTIAL_DRIFT`이며 product-level CURRENT를 허용하지 않는다.
- autoscaling으로 instance population이 바뀌어도 deployment revision identity와 runtime population snapshot을 재계산한다.

## 5. Deployment Strategy별 규칙
### Rolling
모든 active instance가 새 revision으로 수렴하기 전까지 `TRANSITIONING`. 구 revision/new revision 혼재 중에는 production-wide CURRENT를 발급하지 않는다. 단, 명시적 staged assurance는 별도 scope로 표현 가능하다.

### Blue/Green
Blue와 Green을 별도 DeploymentRevision으로 유지한다. traffic authority가 어느 revision을 실제 serving하는지 traffic routing digest를 currentness에 포함한다.

### Canary
Canary population과 stable population을 분리한다. canary PASS를 전체 production PASS로 승격하지 않는다. rollout percentage, traffic cohort, observation window를 evidence로 고정한다.

### Multi-region
region별 deployment/currentness를 먼저 계산한 후 product composition rule로 합성한다. 한 region UNKNOWN/HOLD를 전체 PASS로 평균화하지 않는다.

## 6. Runtime Drift 분류
- ARTIFACT_DRIFT
- CONFIG_DRIFT
- FEATURE_FLAG_DRIFT
- DEPENDENCY_DRIFT
- SECRET_REFERENCE_DRIFT
- POLICY_DRIFT
- MODEL_DRIFT
- SYSTEM_PROMPT_DRIFT
- TOOL_REGISTRY_DRIFT
- RAG_CORPUS_DRIFT
- EMBEDDING_MODEL_DRIFT
- EXTERNAL_CONTRACT_DRIFT
- INFRASTRUCTURE_DRIFT
- OBSERVER_DRIFT
- VALIDATOR_QUALIFICATION_DRIFT
- AUTHORITY_DRIFT

각 drift는 severity와 affected_claims를 가진다. 모든 drift가 동일하게 Full Revalidation을 요구하지는 않으며 Impact Rule이 재검증 범위를 결정한다.

## 7. Invalidation Graph
Node:
- RequirementEpoch
- ScopeEpoch
- TargetManifest
- EvidenceReceipt
- OracleSet
- ValidatorQualification
- AuthorityProfile
- FinalCandidate
- FinalApproval
- FinalLock
- DeploymentRevision
- RuntimeInstancePopulation
- AssuranceCertificate

Edge:
- DEPENDS_ON
- DERIVED_FROM
- DEPLOYMENT_OF
- RUNNING_INSTANCE_OF
- SUPERSEDES
- INVALIDATES
- REVOKES
- REQUIRES_REPERFORMANCE
- REQUIRES_REQUALIFICATION

변경 이벤트 발생 시 graph traversal로 영향을 받는 node를 계산한다. 저장된 `PASS` 문자열을 직접 변경하는 것이 아니라 새로운 validity/currentness generation을 발행한다.

## 8. Invalidation Trigger
### 즉시 INVALIDATED 후보
- deployed artifact digest 불일치
- evidence tamper/signature invalid
- final approval signature/key invalid at effect time
- critical target identity substitution
- known validator defect가 해당 Claim 판정을 무효화

### REASSESSMENT_REQUIRED 후보
- dependency version/CVE 변경
- runtime config/feature flag 변경
- policy/regulation 변경
- model/provider/prompt/RAG 변경
- external API contract 변경
- important MissedFinding 발견

### STALE 후보
- evidence TTL 초과
- qualification expiry
- observation window expiry
- offline revocation uncertainty 증가

### REVOKED
권한 있는 revocation decision과 signed revocation receipt가 존재할 때만 사용한다. 자동 drift detector는 REVOKED를 직접 발행하지 않고 INVALIDATED/REASSESSMENT_REQUIRED를 제안한다.

## 9. Recovery / Rollback
### Rollback
- rollback target artifact digest를 read-back한다.
- 과거 FinalLock 존재만으로 CURRENT 복원 금지.
- 현재 policy/authority/validator qualification과 rollback artifact의 historical evidence compatibility를 검사한다.
- incompatibility가 있으면 revalidation.

### Service Recovery
- service recovery success와 assurance recovery를 분리한다.
- restored database/ledger/evidence store의 integrity를 검증한다.
- recovery 이후 `ASSURANCE_REQUALIFICATION_REQUIRED`를 기본 ceiling으로 한다.

### Disaster Recovery
DR 환경은 production과 별도 DeploymentTarget이다. DR 전환 시 environment digest와 external dependencies가 달라지므로 기존 production Currentness를 그대로 상속하지 않는다.

## 10. API 후보
- `POST /v2/deployment-targets`
- `POST /v2/deployment-revisions`
- `POST /v2/deployment-revisions/{id}/readback`
- `GET /v2/deployment-revisions/{id}/runtime-instances`
- `POST /v2/assurance/currentness/evaluate`
- `GET /v2/assurance/currentness/{targetId}`
- `POST /v2/assurance/invalidation-events`
- `GET /v2/assurance/impact/{eventId}`
- `POST /v2/assurance/revocations`
- `POST /v2/deployments/{id}/rollback-requalification`

Write API는 tenant/target authority, idempotency, nonce, signed receipt를 요구한다.

## 11. Event 후보
- DeploymentObserved
- DeploymentArtifactMismatchDetected
- RuntimePopulationChanged
- RuntimeDriftDetected
- AssuranceBecameStale
- AssuranceReassessmentRequired
- AssuranceInvalidated
- AssuranceRevoked
- AssuranceCurrentnessRestored
- RollbackRequalificationRequired
- ValidatorQualificationExpired
- AuthorityEpochChanged

## 12. UI/UX
Case/Portfolio에서 `PASS`만 표시하지 않는다.
필수 표시:
- Validation result
- Assurance level
- Currentness state
- validated artifact
- deployed artifact
- running artifact
- last currentness evaluation
- stale/revocation reason
- affected environments/regions
- required action

`Historical PASS / Currently STALE`을 명확히 분리한다.

## 13. Negative/Adversarial Test
- mutable tag가 같은데 digest가 변경된 배포
- rolling update 중 old/new artifact 혼재
- canary 5% PASS를 전체 PASS로 승격 시도
- rollback 후 expired validator qualification 재사용
- config만 변경하고 source SHA 동일
- model/provider hot swap
- RAG corpus silent reindex
- revoked key로 과거 FinalLock 재사용
- evidence store restore 후 ledger head rollback
- multi-region 중 한 region stale
- runtime instance가 unregistered artifact 실행
- observer 장애 중 `CURRENT` 발급 시도

## 14. 수용기준
- production CURRENT는 verified→deployed→running identity chain이 닫혀야 한다.
- active runtime population 100%가 scope 내 expected revision에 결속되어야 한다. 예외 population은 명시적 excluded cohort와 approval이 없으면 허용하지 않는다.
- Currentness 계산은 저장된 Final status가 아니라 current raw state를 읽어 재계산한다.
- invalidation trigger는 affected Final/Certificate를 graph로 추적 가능해야 한다.
- rollback/recovery는 과거 PASS 자동복원 금지.
- currentness UNKNOWN/HOLD를 PASS로 변환하지 않는다.

## 15. 기존 산출물 적용 위치
- `02`: Deployment/Currentness/Revocation 기능 요구사항 추가
- `03`: deployment identity/currentness/revocation review domain 추가
- `04`: 본 문서 Entity/API/Event/Invariant 흡수
- `05`: historical validation과 current assurance 분리 UI
- `06`: rolling/canary/rollback/drift/failure injection
- `07`: model/prompt/RAG runtime drift 및 AI requalification
- `08`: TTL, revocation authority, partial rollout composition threshold를 Open Decision으로 관리

## 16. 개발 경계
이 설계는 Claude 현재 DEV-01~13 구현을 중단시키지 않는다. 후속 개발 Batch로 전달한다. 현재 v2 Active Selector, FinalLock, Production/Commercial authority를 변경하지 않는다.
