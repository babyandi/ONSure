# ONSure 아키텍처·데이터·API·OLicense 상세설계

Customer Web / VS Code Extension / Admin Console
→ API Gateway & Identity
→ Commerce & Case Service
→ OLicense Adapter
→ Orchestration Service
→ OLearning / OPlanning / OReview / OVerification / OImprovement
→ OEvidence / OGit / ODelivery
→ Queue / Sandbox / Object Storage / Relational DB / Observability

Local Runtime은 고객 Source를 로컬에서 처리할 수 있으며 SaaS Control Plane에는 최소 Metadata와 승인된 Evidence만 전송하는 모드를 지원한다.

## 2. 배포 모델
- SaaS: Control Plane과 격리 Worker
- Hybrid: SaaS Control Plane + Customer Local Runtime
- On-premises: 고객망 내부 전체 배포
- Air-gapped: Signed Offline License와 수동 Evidence Export

## 3. 핵심 서비스
### Identity and Organization
Organization, User, Role, SSO, MFA, Tenant Context를 관리한다.

- SSO: SAML 2.0과 OIDC를 지원한다
- Enterprise Provisioning: SCIM 기반 자동 사용자 생성·비활성화를 지원한다
- MFA는 Customer Owner/Admin과 위험행위 승인자에게 필수로 강제할 수 있다(Organization Policy)
- 비SSO(이메일/비밀번호) 계정: 최소 길이·복잡도 정책, 알려진 유출 비밀번호 대조, 로그인 실패 임계치 초과 시 잠금, 비밀번호 재설정은 이메일 인증 링크로만 허용
- Cross-Organization Access: SI·컨설팅·품질관리 회사처럼 여러 고객 Organization을 동시에 다루는 사용자를 위해 하나의 User Identity가 여러 Organization Membership을 가질 수 있으며, 화면 상단 Organization Switcher로 전환한다. Organization 간 데이터는 Switcher 전환 없이는 상호 조회 불가능하다(FR-COM-002 Tenant 격리 유지)

### Commerce and Case
상품 선택, Preflight, Quote, Order, Payment 상태, Service Case를 관리한다.

### Orchestration
Execution Plan을 DAG로 실행하며 Pause, Resume, Cancel, Retry, Compensation을 제공한다.

Queue 우선순위는 Plan 등급(Enterprise > Team > Developer > Web One-time)과 대기시간을 함께 고려한 가중 공정 큐잉(Weighted Fair Queuing)을 적용해, 낮은 등급이라도 대기시간이 길어지면 우선순위가 점진적으로 상승한다(Starvation 방지). 동일 Tenant 내 다중 요청은 NFR-AVAIL의 동시 실행 상한을 넘지 않는 범위에서 병렬 처리한다.

### Sandbox
고객별 격리 실행, CPU/Memory/Time/Network Policy, Secret Injection, Artifact Export를 관리한다.

- 격리 기술: 실제 `contracts/sandbox-boundary.v1.json`은 Rootless Bubblewrap(`bwrap`)을 기본이자 유일한 허용 Backend로 고정한다(`remote_ci_backend: FORBIDDEN`). Network/User Namespace 분리, Source Read-only, 쓰기 `/tmp` 한정, Capability Drop, 실패 시 Fail-closed를 적용한다.
- Syscall 필터링은 DESIGN_ONLY이며 Seccomp-bpf Allowlist는 별도 qualification 뒤 제정한다.
- Lifecycle: Provision → Execute → Artifact Export → Destroy.
- Network Policy: 기본 Egress Deny. 승인된 Allowlist만 허용한다.
- Resource Quota 초과는 Evidence에 Truncated로 표시하고 NOT_RUN 또는 BLOCKED로 판정한다.
- Secret Injection은 단기 Credential을 메모리에만 주입하고 Artifact Export 전 Secret Scanning을 재실행한다.

### Evidence
Append-only Metadata와 Content-addressed Artifact를 관리한다.

### Notification
Case/Finding/License 상태 변화를 채널별 구독 설정에 따라 Email, Webhook, VS Code, 관리자 알림함으로 발송하고 발송 자체를 Evidence로 남긴다.

### Risk Scoring
Program/Case 단위의 ProgramRiskScore를 산정하되 Final Hard Gate를 대체하지 않는다.

### Policy Management
고객 PolicyPack을 버전관리하고 Golden Review Fixture Regression을 거쳐 적용한다.

### Extension Distribution
VS Code Marketplace/Open VSX 및 Signed VSIX Offline 배포를 지원한다.

## 4. 주요 데이터 엔터티
- Organization, User, Role, Membership
- ProductCatalog, Plan, Feature
- Order, Payment, Refund
- License, Entitlement, Subscription
- CreditAccount, CreditReservation, UsageEvent
- System, Program, RepositoryBinding
- Baseline, ArtifactManifest
- NotificationRule, NotificationEvent, NotificationDeliveryReceipt
- PortfolioSnapshot, ProgramRiskScore
- PolicyPack, PolicyPackVersion
- MutationTestResult, BehaviorDiffReport, BlastRadiusReport, SBOM
- CrossModelVerificationReceipt, SelfClaim
- RollbackVerificationReceipt, ConfidenceCalibrationReport, ReviewerAccuracyScore, AIConfigDriftReport, PeerBenchmark
- CoverageReport
- AcceptanceCertificate, ExternalAcceptorGrant
- ReproducibilityAuditSample
- TrainingRequest, TrainingPlan, TrainingRun, EvaluationReport
- ModelVersion, RAGIndexVersion, PromptVersion, AgentPolicyVersion
- DeploymentApproval, ProductionObservation, RelearnTrigger
- ServiceCase, CaseScope, CaseRevision
- ProgramProfile, Component, Dependency, AIComponent
- Requirement, Policy, TraceLink
- ReviewRun, ReviewFinding, ReviewDecision
- VerificationRun, TestClaim, VerificationFinding
- ImprovementRequest, PatchPlan, PatchRun
- Evidence, Receipt, DeliveryPackage
- AuditEvent, DeletionJob, DeletionReceipt
- KnowledgePattern, ComponentSignature, PatternApplicationReceipt
- MissedFinding, DetectionCapabilityChangeReport
- ComponentContract, ComponentInterface, ComponentVersion, ReuseLink

## 5. 상태 모델
ServiceCase, program_profile, validation_run, improvement, git_delivery, assurance_publication은 실제 계약의 상태 어휘를 따른다. ServiceCase의 상거래 상태와 기술실행 상태는 분리한다. Strong state는 predecessor/event/receipt/authority/lineage를 재구성할 때만 유효하다.

## 6. API 원칙
- REST와 Event 병행
- 모든 Write API Idempotency-Key
- Organization/License Context 명시
- 낙관적 잠금/Version
- machine-readable error/retryable
- PII/Secret 비노출
- 객체 수준 tenant ownership 검증
- 금전·승인 workflow abuse detection

## 7. 주요 API
### Case / Learning / Review / Verification / Improvement / Training / Notification / Policy / Knowledge / License
기존 v1 API를 유지한다. 신규 Assurance v2 API는 §15에서 추가한다.

## 8. Event 계약
기존 Payment/License/Case/Review/Verification/Evidence/Training 이벤트에 더해 §15 신규 Assurance Event를 추가한다.

## 9. OLicense 책임과 경계
ORUDA/OLicense는 Catalog, Plan, Feature, Entitlement, Credit, Validity, Suspension, Revocation, Signed Entitlement Snapshot, Offline Grace, Usage/Audit를 관리한다. ONSure는 Validate, Activate, Reserve, Commit, Release, Report만 수행한다.

## 10. License Token 필드
issuer, audience, subject, organization_id, product_code=ONSURE, channel, service_type, plan, feature_entitlements, system/program limits, learning/credit limits, OTraining limits, case/subscription, baseline_binding, valid_from/until, offline_grace_until, revocation_version, key_id, signature.

## 11. Offline 정책
Signed Snapshot의 서명/Audience/시간/Revocation Version, Grace, Clock Rollback, Offline License File, Reconnect Usage 동기화를 검증한다.

## 12. 보안 설계
고객 Source 비공개, 암호화, 최소권한 Worker, Egress Allowlist, Secret Scanning, Artifact Content Addressing, Break-glass 감사, Tenant Key 옵션, 클라이언트 자기주장 불신을 기본 원칙으로 한다.

## 12-1. 규제산업 컴플라이언스 설계
Data Residency, 개인정보 마스킹, 망분리/폐쇄망, 감사 로그 보존, 규제 프레임워크 버전관리와 Compliance Officer 승인 정책을 Enterprise Feature Gate로 관리한다.

## 13. 보존과 삭제
Retention 종료 후 Source/Artifact/Log/Profile/Evidence를 정책에 따라 삭제하고 Deletion Receipt를 남긴다. Legal Hold는 별도 승인과 감사로 설정/해제한다.

## 14. Meta-Validation Architecture 및 Cross-Contract Final Assurance 상세설계
기존 설계의 ValidationTargetManifestService, ScopeEpochService, ValidatorCapabilityQualificationService, ObservabilityQualificationService, CrossContractInvariantEngine, AtomicSnapshotAssembler, FinalClaimReconstructor, FinalFreshnessBarrier, AssuranceValidityService, HistoricalImpactScanner를 유지한다.

Target Manifest에는 source/build/dependency/config/feature/policy/model/prompt/tool/RAG/external service/OS/runtime/DB/deployment environment digest를 포함한다. Scope/Requirement Epoch 변경은 Coverage 재계산을 유발한다. Final Evidence는 target/scope/requirement/oracle/detector/validation generation/run/nonce에 결속한다. Final은 동일 generation의 Atomic Snapshot에서만 구성하며 저장된 PASS/score/state를 Raw Evidence에서 재구성한다. Approval은 context/action intent/nonce/expiry/revocation에 결속하고 Final Freshness Barrier를 거친다. Evidence는 transactionally commit되고 FinalLock은 idempotent/single-consume이다. Current validity는 revocable하며 Verified-to-Deployed identity와 Observability Qualification을 별도 검증한다.

## 15. Deployment·Runtime Currentness·Composition·Certificate·Scale Architecture (신규)
이 절은 `02 FR-META-044~060`의 아키텍처 정본이다. 상세 원형은 `semantic-assurance/29~32`를 사용한다.

### 15.1 신규 논리 서비스
- **BuildArtifactIdentityService**: source/build/SBOM/provenance를 immutable artifact identity로 고정
- **DeploymentIdentityService**: tenant/target/environment/provider/region/cluster/account와 DeploymentRevision 결속
- **RuntimePopulationObserver**: 실제 active node/pod/process의 artifact/config/dependency/AI runtime identity read-back
- **AssuranceCurrentnessService**: FinalLock과 current deployment/runtime/policy/qualification/authority를 재평가
- **InvalidationGraphService**: drift/revocation/MissedFinding/CVE/authority 변경의 영향범위 graph traversal
- **AssuranceCompositionService**: exact subject/dependency population을 제품 수준 decision/assurance level/currentness로 합성
- **EvidenceGraphService**: DERIVED_FROM/CONTRADICTS/SUPERSEDES/INVALIDATES/REVOKES 등 material edge 관리
- **AssuranceCertificateService**: 내부 FinalLock과 분리된 signed customer-facing certificate 발급/검증/폐기
- **OfflineTrustBundleService**: 폐쇄망 key/policy/qualification/revocation/trusted-time snapshot 생성 및 reconciliation
- **AuthorityDelegationService**: AuthorityGrant/delegation/four-eyes/break-glass/legal-hold governance
- **WorkUnitCoordinator**: immutable distributed work partition/lease/retry/idempotent aggregation
- **PluginQualificationService**: Plugin/Adapter signature/privilege/compatibility/qualification 관리
- **AITargetRuntimeIdentityService**: Model/Prompt/Tool/Memory/RAG runtime identity/currentness 관리
- **ONSureReleaseQualificationService**: ONSure 자체 validator/oracle/adapter/benchmark release qualification

### 15.2 신규 Entity
**BuildArtifactIdentity**: artifact_id, sha256, type, build_receipt_digest, source_tree_digest, dependency_set_digest, sbom_digest, provenance_digest.

**DeploymentTarget**: organization/tenant/target/environment/provider/region/cluster/namespace/account와 target_binding_digest.

**DeploymentRevision**: expected/observed artifact, deployment manifest, config, secret reference set, runtime dependency, deployment strategy, rollout generation, state.

**RuntimeInstance**: deployment_revision_id, node/pod/process, boot_id, observed artifact/config/dependency, model/prompt/RAG/external contract digests, first/last seen, health.

**AssuranceCurrentnessSnapshot**: final_lock_digest, target_manifest, deployment revision, runtime population, policy/qualification/authority/observation epoch, CURRENT|STALE|REASSESSMENT_REQUIRED|INVALIDATED|REVOKED|UNKNOWN, reasons/actions.

**AssuranceSubject**: PRODUCT|SYSTEM|SERVICE|MODULE|DEPLOYMENT|RUNTIME_INSTANCE|AI_COMPONENT|EXTERNAL_DEPENDENCY identity.

**AssuranceDependencyEdge**: REQUIRES|CALLS|READS|WRITES|DEPLOYS_TO|AUTHORIZES|PROVIDES_GROUND_TRUTH|PROVIDES_ORACLE|PROVIDES_POLICY, propagation HARD|SOFT|CONDITIONAL|INFORMATIONAL.

**CompositionSnapshot**: exact subject/edge population digests, requirement/target/evidence epoch, composition rule version, decision, level, currentness, ceiling reasons.

**AssuranceCertificate**: subject/product/target/requirement/composition/final lock digest, decision, assurance level, currentness, independence summary, validity, limitation/exclusion, verifier public identity, revocation ref, signature.

**AuthorityGrant**: principal, tenant/subject/operation/purpose scope, validity, delegation depth, issuer/approval chain, revocation.

**WorkUnit**: parent run, target/scope/requirement epoch, input digest, operation, partition, attempt, lease, expected output contract.

**PluginManifest**: plugin/version/publisher/artifact signature, supported archetype, capabilities, privileges, I/O contracts, compatibility, qualification.

**ONSureReleaseQualification**: ONSure build, validator/oracle/adapter/fixture/benchmark/hidden corpus/environment/independent verifier/limitations/expiry/requalification trigger.

### 15.3 Verified→Deployed→Running Identity
필수 chain:
`SourceSnapshot → BuildReceipt → BuildArtifactIdentity → VerificationEvidence → FinalLock → DeploymentRevision → RuntimeInstancePopulation → CurrentnessSnapshot`.

Desired tag/name은 authority가 아니며 registry/runtime read-back digest를 사용한다. active population 중 일부가 다른 revision이면 전체 CURRENT를 발급하지 않는다. Rolling/Blue-Green/Canary/Multi-region은 cohort/traffic/region population을 분리한다.

### 15.4 Drift / Invalidation
Drift class: ARTIFACT, CONFIG, FEATURE_FLAG, DEPENDENCY, SECRET_REFERENCE, POLICY, MODEL, SYSTEM_PROMPT, TOOL_REGISTRY, RAG_CORPUS, EMBEDDING, EXTERNAL_CONTRACT, INFRASTRUCTURE, OBSERVER, VALIDATOR_QUALIFICATION, AUTHORITY.

즉시 INVALIDATED, REASSESSMENT_REQUIRED, STALE 후보를 정책으로 분리하고 REVOKED는 signed revocation authority가 있을 때만 사용한다. 저장된 FinalLock을 덮어쓰지 않고 새 validity/currentness generation을 발행한다.

### 15.5 Product Composition
Hard dependency의 FAIL/BLOCKED/INVALIDATED/REVOKED는 상위 PASS 금지. HOLD/UNKNOWN/NOT_RUN/INCONCLUSIVE는 상위 positive ceiling을 제한한다. STALE/REASSESSMENT_REQUIRED child가 있으면 Product CURRENT 불가. Soft dependency는 non-impact evidence가 있어야 downgrade 없이 유지한다. N/A는 applicability proof 필수다. Conflicting PASS/FAIL은 supersession proof 없으면 CONFLICT_HOLD다.

### 15.6 Evidence Graph
Node: Source, Requirement, Policy, Oracle, Fixture, Execution, Observation, Finding, RCA, Patch, Approval, Qualification, EvidenceReceipt, FinalLock, Deployment, RuntimeObservation, Certificate.

Edge: DERIVED_FROM, REPERFORMED_FROM, INDEPENDENTLY_CONFIRMS, CONTRADICTS, SUPERSEDES, INVALIDATES, REVOKES, SATISFIES, VIOLATES, DEPENDS_ON, DEPLOYMENT_OF, OBSERVATION_OF, QUALIFIES, APPROVES.

DERIVED_FROM/SUPERSEDES cycle, dangling edge, tenant-crossing material edge를 금지한다.

### 15.7 Certificate / Offline / Enterprise
Certificate는 FinalLock과 분리된 signed public artifact다. 발급 당시 result와 현재 validity를 분리하고 currentness/revocation/limitation/exclusion을 공개한다. Offline Trust Bundle은 trusted root/key registry/policy/qualification/revocation/trusted-time snapshot을 포함한다. reconnect 시 authority/revocation/usage/replay/currentness를 reconciliation한다.

Delegation은 parent grant보다 넓을 수 없다. Final Approval/Certificate revoke/Legal Hold/Policy relaxation/Hidden Corpus access 등은 policy에 따라 서로 다른 principal의 multiple approval을 요구한다. Break-glass는 operation access만 허용하며 Final PASS나 assurance level을 올리지 않는다.

### 15.8 Scale / WorkUnit
Distributed execution은 at-least-once를 허용하되 logical effect/receipt commitment/nonce consume는 idempotent하게 만든다. duplicate execution, stale lease, retry history, poison unit, tenant fairness/backpressure를 관리한다. aggregation은 canonical ordering으로 deterministic digest를 생성한다. Resource exhaustion은 denominator 축소 없이 BLOCKED/RESOURCE_LIMIT으로 노출한다.

### 15.9 Plugin / Adapter Trust
unsigned/unqualified/revoked plugin은 authoritative output을 만들지 못한다. PluginManifest privilege와 실제 filesystem/network/effect scope를 대조하고 update 시 requalification한다. Adapter는 discovery completeness, semantic mapping, version compatibility, negative fixture, unsupported feature disclosure, parser fidelity, runtime observability를 qualification한다.

### 15.10 AI Runtime Identity
AI Target은 provider/model/deployment, prompt hierarchy/template/dynamic assembly, Tool Registry, Agent Memory, RAG corpus/index/embedding/chunking/retrieval policy를 digest/epoch로 관리한다. Provider alias 동일성은 identity가 아니다. 비결정 AI는 repeated population, sampling config, outcome distribution, sample size/confidence method를 기록한다. Multi-agent는 delegation/message/shared-memory/common-mode failure를 별도 검증한다.

### 15.11 ONSure Meta-Assurance
ONSure self-test는 release qualification 입력일 뿐 최종 authority가 아니다. Release Qualification은 target archetype별 QUALIFIED|PARTIAL|NOT_PROVEN으로 관리하고 Core validator/oracle/adapter/critical fixture/security boundary/dependency/trust-root 변경 또는 MissedFinding blind spot 발견 시 requalification을 요구한다.

### 15.12 신규 API 후보
- `POST /v2/deployment-targets`
- `POST /v2/deployment-revisions`
- `POST /v2/deployment-revisions/{id}/readback`
- `GET /v2/deployment-revisions/{id}/runtime-instances`
- `POST /v2/assurance/currentness/evaluate`
- `GET /v2/assurance/currentness/{targetId}`
- `POST /v2/assurance/invalidation-events`
- `GET /v2/assurance/impact/{eventId}`
- `POST /v2/assurance/revocations`
- `POST /v2/assurance-subjects`
- `POST /v2/assurance-dependencies`
- `POST /v2/assurance/compositions`
- `GET /v2/assurance/compositions/{id}/explanation`
- `POST /v2/evidence-graph/edges`
- `POST /v2/assurance-certificates`
- `GET /v2/assurance-certificates/{id}/verify`
- `POST /v2/assurance-certificates/{id}/revoke`
- `POST /v2/offline-trust-bundles`
- `POST /v2/offline-reconciliation`
- `POST /v2/authority-grants`
- `POST /v2/break-glass-sessions`
- `POST /v2/work-units`
- `POST /v2/plugins/{id}/qualification`
- `POST /v2/adapters/{id}/qualification`
- `POST /v2/onsure-releases/{id}/qualification`

### 15.13 신규 Event 후보
DeploymentObserved, DeploymentArtifactMismatchDetected, RuntimePopulationChanged, RuntimeDriftDetected, AssuranceBecameStale, AssuranceReassessmentRequired, AssuranceInvalidated, AssuranceRevoked, AssuranceCurrentnessRestored, RollbackRequalificationRequired, ValidatorQualificationExpired, AuthorityEpochChanged, CompositionRecalculated, CertificateIssued, CertificateRevoked, OfflineReconciliationConflict, DelegationRevoked, BreakGlassUsed, PluginQualificationChanged, ONSureReleaseQualificationChanged.

### 15.14 Architectural Invariant
- CURRENT는 verified→deployed→running population closure 없이는 불가.
- Product PASS는 exact subject/dependency population과 composition snapshot 없이는 불가.
- Certificate는 FinalLock과 현재 currentness/revocation을 결합해 검증한다.
- Offline uncertainty를 CURRENT로 숨기지 않는다.
- Duplicate/retry execution이 denominator를 부풀리지 않는다.
- Plugin/Adapter/ONSure 자체 qualification을 자기선언으로 승격하지 않는다.
- Runtime health PASS가 Semantic Assurance PASS를 의미하지 않는다.
