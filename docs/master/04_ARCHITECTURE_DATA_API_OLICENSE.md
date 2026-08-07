# ONSure 아키텍처·데이터·API·OLicense 상세설계

## 1. 논리 아키텍처

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

- 격리 기술: 실행 단위마다 커널 공유를 최소화하는 MicroVM 또는 동급 격리 Container를 신규 프로비저닝하며, 이전 실행의 파일시스템·메모리·프로세스를 재사용하지 않는다
- Lifecycle: Provision(Baseline과 정책 결속) → Execute → Artifact Export(Secret Scrub 후) → Destroy. Destroy는 실행 종료 후 수 분 내 완료하며 잔존 Volume은 자동 회수된다
- Network Policy: 기본 Egress Deny. Organization Policy와 Case Scope 승인이 있는 도메인만 Allowlist에 등록하며 DNS/HTTP(S) 단위로 필터링한다. 외부 Network 허용은 [05_UI_UX_WORKFLOW_SPECIFICATION.md](05_UI_UX_WORKFLOW_SPECIFICATION.md)의 2단계 확인 대상이다
- Resource Quota: Plan/Case별 CPU·Memory·Disk·실행시간 상한을 적용하며 초과 시 Graceful Timeout 후 Evidence에 Truncated로 표시하고 NOT_RUN 또는 BLOCKED로 판정한다
- Secret Injection: 실행 시작 시 단기 Credential을 메모리 내에만 주입하고 디스크에 영속화하지 않으며, Artifact Export 전 Secret Scanning으로 재확인한다
- Multi-tenancy: Tenant별 전용 Worker Pool 또는 물리적 분리(Enterprise 옵션)를 지원하며 cgroup 상한으로 Noisy Neighbor를 방지한다

### Evidence
Append-only Metadata와 Content-addressed Artifact를 관리한다.

### Notification
Case/Finding/License 상태 변화를 채널별 구독 설정에 따라 Email, Webhook, VS Code, 관리자 알림함으로 발송하고 발송 자체를 Evidence로 남긴다([02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) §10-1 ONotify).

### Risk Scoring
Program/Case 단위로 비전문가도 한눈에 이해할 수 있는 0~100 ProgramRiskScore와 A~E 등급을 산정해 Case Dashboard와 Organization Portfolio에 노출한다.

ProgramRiskScore = 100 − clamp(10·OpenCritical + 4·OpenHigh + 1·OpenMedium + 2·RecentMissedFinding + 1.5·AIGeneratedRatio점수 + 0.5·평균미해결일수, 0, 100)

- OpenCritical/High/Medium: 현재 미해결 ReviewFinding+VerificationFinding 수
- RecentMissedFinding: 최근 90일 내 확인된 MissedFinding 수([02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) §7-1)
- AIGeneratedRatio점수: AIProfile상 AI 생성 추정 비중을 0~10으로 정규화한 값
- 평균미해결일수: OPEN 상태로 머문 평균 일수를 0~10으로 정규화한 값
- 등급: 90≤A, 75≤B<90, 60≤C<75, 40≤D<60, E<40. Critical Finding이 1건이라도 있으면 등급은 C를 초과할 수 없다
- 가중치는 [01_BUSINESS_PRODUCT_SERVICE_PLAN.md](01_BUSINESS_PRODUCT_SERVICE_PLAN.md)의 Learning Unit 산정 공식과 동일한 원칙으로 분기별 재보정하며 재보정 이력은 Evidence로 남긴다

### Policy Management
고객이 자체 Coding/Architecture/보안 정책을 PolicyPack으로 업로드·버전관리할 수 있다([01_BUSINESS_PRODUCT_SERVICE_PLAN.md](01_BUSINESS_PRODUCT_SERVICE_PLAN.md)의 Enterprise 정책팩 판매와 연동).
- PolicyPack은 조직 표준 Rule Pack 위에 추가(Additive)되며 표준 정책의 Critical 규칙은 비활성화할 수 없다
- PolicyPackVersion마다 Digest를 고정해 [03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) §10 감사 Receipt에 결속한다
- 신규/개정 PolicyPack은 Golden Review Fixture 회귀를 통과해야 프로덕션 적용되며, 이는 OMemory 재귀학습([02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) §7-1)과 동일한 회귀 절차를 재사용한다

### Extension Distribution
- 공개 배포: VS Code Marketplace와 Open VSX에 게시하며 Semantic Versioning을 따르고 자동 업데이트를 기본으로 한다
- 폐쇄망/Air-gapped: 서명된 VSIX 오프라인 설치 패키지를 제공하며 자동 업데이트 대신 관리자 승인 후 수동 배포 절차를 따른다
- Extension은 신뢰되지 않은 Workspace에서 자동 실행을 금지하며 최소 권한 Workspace Trust를 요구한다
- Telemetry는 사용량 Meter(과금용)와 분리하며 기본 최소수집, Organization Policy로 완전 비활성화 가능

## 4. 주요 데이터 엔터티
- Organization(SharedCorpusOptIn 속성 포함, 기본값 false)
- User, Role, Membership
- ProductCatalog, Plan, Feature
- Order, Payment, Refund
- License, Entitlement, Subscription
- CreditAccount, CreditReservation, UsageEvent
- System, Program, RepositoryBinding(GitProvider, CIProvider, 인증방식 포함)
- Baseline, ArtifactManifest
- NotificationRule, NotificationEvent, NotificationDeliveryReceipt
- PortfolioSnapshot, ProgramRiskScore
- PolicyPack, PolicyPackVersion
- MutationTestResult, BehaviorDiffReport, BlastRadiusReport, SBOM
- CrossModelVerificationReceipt, SelfClaim
- RollbackVerificationReceipt, ConfidenceCalibrationReport, ReviewerAccuracyScore, AIConfigDriftReport, PeerBenchmark
- CoverageReport(schema_version 결속)
- AcceptanceCertificate, ExternalAcceptorGrant
- ReproducibilityAuditSample
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
### ServiceCase
DRAFT → PREFLIGHT → QUOTED → PAYMENT_PENDING → LICENSE_PENDING → READY → [LEARNING] → [REVIEW_REQUIRED] → [VERIFYING] → IMPROVEMENT_OPTIONAL → DELIVERING → COMPLETED

예외: BLOCKED, SUSPENDED, CANCELLED, EXPIRED, REFUNDED

BLOCKED은 사유(License 미해결, Credit 소진, Legal Hold, Operator 개입 필요 등)를 blocked_reason으로 구분해 기록하며, 사유가 해소되면 BLOCKED 진입 직전 단계로 복귀한다. SUSPENDED는 License가 LicenseSuspended(결제 분쟁·정책 위반 등)로 전이될 때만 진입하며, License가 재활성화(LicenseIssued 재발급 또는 분쟁 해소)되면 SUSPENDED 진입 직전 단계로 복귀한다. CANCELLED, EXPIRED, REFUNDED는 종결 상태이며 재진입 없이 CaseRevision으로만 후속 조치한다.

LEARNING, REVIEW_REQUIRED, VERIFYING는 각각 독립 상태이며 순서는 [00_ONSURE_MASTER_DESIGN_SET.md](00_ONSURE_MASTER_DESIGN_SET.md)의 Understand → Plan → Review → Verify 순서를 따른다. CaseScope에 포함된 상품에 따라 다음과 같이 조건부로 진입한다.

- Learn: READY → LEARNING → DELIVERING (REVIEW_REQUIRED, VERIFYING 생략)
- Verify: READY → VERIFYING → DELIVERING (LEARNING 생략, CaseScope에 결속된 기존 Baseline과 고객 제공 ScopeManifest를 구조 정보로 사용)
- Learn & Verify: READY → LEARNING → REVIEW_REQUIRED → VERIFYING → IMPROVEMENT_OPTIONAL → DELIVERING
- Improve & Re-verify: 아래 CaseRevision 참조

REVIEW_REQUIRED는 Learn 산출물(Program Profile) 또는 고객 제공 ScopeManifest 중 하나가 있어야 진입할 수 있다. Review Decision과 Verification Decision은 [00:57](00_ONSURE_MASTER_DESIGN_SET.md)에 따라 서로 독립적으로 저장되며, VERIFYING 단계의 FAIL이 REVIEW_REQUIRED 단계의 APPROVE를 무효화하지 않는다.

QUOTED 상태의 Quote는 발급 후 14일간 유효하며, 만료 전 결제되지 않으면 자동으로 EXPIRED로 전이하고 재견적이 필요하다. Preflight 이후 대상 Repository 규모가 초기 예상 대비 20%를 초과해 변하면 Quote를 무효화하고 재계산을 요구한다.

### ReviewFinding
OPEN → ACKNOWLEDGED → FIX_PLANNED → FIXED → RE_REVIEWED → CLOSED
예외: ACCEPTED_RISK, FALSE_POSITIVE, DUPLICATE, WONT_FIX

### VerificationFinding
OPEN → CONFIRMED → RCA_CANDIDATE_LINKED → IMPROVEMENT_REQUESTED → RE_VERIFIED → CLOSED
예외: ACCEPTED_RISK, FALSE_POSITIVE, DUPLICATE, WONT_FIX, FLAKY_ISOLATED

CONFIRMED는 [02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md:102](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md#L102)의 수용기준("PASS는 실행 증거 없이 생성할 수 없다")과 동일하게 실행 Evidence 없이는 도달할 수 없다. ReviewFinding과 VerificationFinding은 finding_id 체계를 공유하지 않으며 별도 ID Prefix(RVW-, VFY-)로 구분한다.

### ImprovementRequest
DRAFT → SCOPED → APPROVED → PATCH_IN_PROGRESS → PATCH_SUBMITTED → REGRESSION_RUNNING → REGRESSION_PASSED → DELIVERED
예외: REJECTED, ABANDONED, ROLLED_BACK

REGRESSION_RUNNING에서 실패하면 PATCH_IN_PROGRESS로 되돌아가며 재시도 횟수는 ExecutionPlan의 Stop Condition을 따른다.

### PatchPlan
PROPOSED → APPROVED → SUPERSEDED
예외: REJECTED

### PatchRun
PENDING → DRY_RUN → DRY_RUN_REVIEWED → RUNNING → SUCCEEDED → REGRESSION_PENDING → REGRESSION_PASSED 또는 REGRESSION_FAILED
예외: FAILED, ABORTED, ROLLED_BACK

PatchRun은 ImprovementRequest 1건과 PatchPlan 1건에 결속되며 [02:110](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md#L110)의 Worktree/Branch 원칙에 따라 Main과 분리된 상태로만 존재한다. REGRESSION_FAILED는 기존 PatchRun을 재사용해 재시도하지 않고 새 PatchRun 인스턴스를 생성해 ImprovementRequest의 PATCH_IN_PROGRESS로 되돌아가며, 실패한 PatchRun 자체는 이력으로 보존한다(재시도 상한은 ExecutionPlan의 Stop Condition). DRY_RUN 단계는 실제 코드 변경 없이 영향받는 파일·Component·의존 Program을 BlastRadiusReport로 산출하며, 사용자가 DRY_RUN_REVIEWED로 승인해야 RUNNING으로 진행한다. SUCCEEDED 이후 REGRESSION_PENDING에서는 기능 회귀뿐 아니라 BehaviorDiffReport(무관 기능 동작 Diff, 성능 지표 변화)를 함께 산출하며, 이 리포트에 임계치를 초과하는 변화가 있으면 자동으로 REGRESSION_FAILED로 판정한다. ROLLED_BACK으로 전이할 때는 RollbackVerificationReceipt로 대상 Baseline이 직전 정상 상태와 실제로 동일한지 확인하며, 불일치 시 ROLLED_BACK을 확정하지 않고 Critical Incident로 승격한다.

### CreditReservation
RESERVED → COMMITTED 또는 RELEASED; Timeout 시 자동 RELEASED

실행 도중 CreditReservation이 소진되면 진행 중인 Review/Verification은 안전한 Checkpoint(현재 Scenario/Finding 처리 완료 시점)까지만 진행한 뒤 ServiceCase를 BLOCKED로 전이한다. 이미 산출된 부분 결과는 NON_FINAL Evidence로 보존하며 폐기하지 않는다. 고객이 추가 Credit을 승인하면 Checkpoint부터 Resume하고, 미승인 상태가 계약된 유예기간을 넘기면 CANCELLED로 전이한다.

### CaseRevision
Improve & Re-verify는 원칙적으로 COMPLETED된 ServiceCase에 새 CaseRevision을 추가하는 방식으로 처리하며 신규 ServiceCase를 생성하지 않는다. CaseRevision은 참조하는 BaselineManifest, 소비하는 Credit/Learning Unit, Evidence Pack을 원본 Case와 분리 기록하되 같은 System/Program Binding과 OLicense Case 계약을 상속한다. CaseRevision 생성 시 ServiceCase는 COMPLETED에서 IMPROVEMENT_OPTIONAL로 재진입한다.

### Baseline 동시성
System은 Branch별로 복수의 활성 Baseline을 동시에 가질 수 있다(예: main과 여러 Feature Branch를 동시에 작업하는 VS Code Team 사용). BaselineManifest는 Branch Ref와 결속되며 Finding, KnowledgePattern, VerificationRun은 모두 Baseline ID로 구분해 추적한다. Branch Merge 시 두 Baseline은 BaselineChanged 이벤트와 Lineage 정보로 연결되며, 병합 대상 Baseline에서 이미 CLOSED된 Finding은 재복사하지 않고 참조만 연결한다. 동일 Component에 대해 서로 다른 Baseline에서 동시에 진행 중인 Continuous Review는 Worker Pool에서 독립 격리 실행하며 서로의 Sandbox를 공유하지 않는다.

### KnowledgePattern
CANDIDATE → VALIDATED → TENANT_SCOPED 또는 PROMOTED(공유 Corpus)
예외: DEPRECATED(3회 이상 False Positive), RETRACTED(권한자 판단으로 회수)

CANDIDATE는 OMemory가 자동 추출한 상태이며, 사람 또는 Reconciliation 절차가 근거를 확인해야 VALIDATED로 전이한다. PROMOTED 전이는 Anonymization 검증을 통과해야 한다.

### MissedFinding
DETECTED → RCA_DONE → CAPABILITY_UPDATED → REGRESSION_VALIDATED → PROMOTED
예외: REJECTED(재현 불가), INSUFFICIENT_EVIDENCE

PROMOTED는 [02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) OMemory 절의 재귀학습 루프에서 전체 Golden Fixture Regression을 통과한 뒤에만 도달한다. CAPABILITY_UPDATED에서 REGRESSION_VALIDATED로 가지 못하고 반복 실패하면 INSUFFICIENT_EVIDENCE로 격리하고 Human Review로 넘긴다. Human Review가 새 RCA 근거나 대안 Rule 개정안을 제시하면 RCA_DONE으로 재진입하고, 근본적으로 탐지 불가능하다고 판단되면 REJECTED로 종결한다.

### ComponentContract
DRAFT → ACTIVE → SUPERSEDED
예외: BREAKING_CHANGE_FLAGGED

BREAKING_CHANGE_FLAGGED는 Architecture Review에서 최상위 우선순위 Finding으로 승격된다. Finding이 CLOSED되면(변경 철회 또는 영향받는 모든 Program의 대응 완료) ACTIVE로 복귀하고, 변경이 의도된 것으로 승인되면 새 ComponentVersion과 함께 ACTIVE로 확정되며 이전 버전은 SUPERSEDED로 전이한다. Provided Interface가 변경되면 같은 System 내 이 Interface를 Required Interface로 선언한 모든 다른 Program을 ReuseLink로 역조회해 Cross-Program Impact Scan을 수행하고, 영향받는 각 Program에 별도 Finding을 생성한다(원 변경 Program의 Review 통과와 무관하게 독립 판정). Program 간 저장소가 분리되어 있어 개별 CI에서는 이 영향을 알 수 없다는 점이 이 스캔의 존재 이유다.

## 6. API 원칙
- REST와 Event를 병행한다.
- 모든 Write API는 Idempotency-Key를 지원한다.
- Organization과 License Context를 명시한다.
- 낙관적 잠금과 Version을 사용한다.
- 오류는 machine-readable code와 retryable 여부를 포함한다.
- PII와 Secret을 오류 본문에 넣지 않는다.
- 인증: Web/Admin Console은 Session Cookie + CSRF Token, VS Code Extension은 OAuth2 Authorization Code + PKCE로 발급한 단기 Access Token과 Refresh Token, 외부 시스템 M2M 연동은 OAuth2 Client Credentials를 기본으로 하며 Enterprise에 한해 IP Allowlist가 결속된 장기 API Key를 허용한다
- Access Token 수명은 1시간 이내로 하고 Refresh Token 회전(Rotation)을 적용한다

## 7. 주요 API
### Case
POST /v1/preflights
POST /v1/quotes
POST /v1/orders
POST /v1/cases
GET /v1/cases/{caseId}
POST /v1/cases/{caseId}/baselines
POST /v1/cases/{caseId}/approve-scope
POST /v1/cases/{caseId}/execute
POST /v1/cases/{caseId}/cancel
GET /v1/cases/{caseId}/deliveries
GET /v1/cases/{caseId}/coverage-report
POST /v1/cases/{caseId}/acceptance-certificates
POST /v1/cases/{caseId}/external-acceptors
GET /v1/certificates/{certId}/verify(인증 불필요 — §6 API 인증 원칙의 유일한 예외. 서명 유효성과 발급 사실만 반환하며 Finding 상세·소스는 노출하지 않는다)

### Learning and Review
POST /v1/learning-runs
GET /v1/program-profiles/{id}
GET /v1/program-profiles/{id}/sbom
POST /v1/review-runs
GET /v1/review-runs/{id}/findings
GET /v1/review-runs/{id}/findings.sarif
POST /v1/findings/{id}/decisions
POST /v1/findings/{id}/cross-model-verify

### Verification and Improvement
POST /v1/verification-runs
GET /v1/verification-runs/{id}/mutation-score
POST /v1/improvement-requests
POST /v1/patch-runs
GET /v1/patch-runs/{id}/blast-radius
POST /v1/patch-runs/{id}/approve
POST /v1/patch-runs/{id}/reverify
GET /v1/patch-runs/{id}/behavior-diff

### Notification and Portfolio
POST /v1/organizations/{orgId}/notification-rules
GET /v1/organizations/{orgId}/notification-rules
GET /v1/organizations/{orgId}/portfolio
GET /v1/programs/{programId}/risk-score
GET /v1/programs/{programId}/risk-score/trend
GET /v1/programs/{programId}/benchmark

### Quality Assurance
POST /v1/patch-runs/{id}/rollback-verify
GET /v1/review-runs/confidence-calibration
GET /v1/reviewers/{reviewerId}/accuracy
GET /v1/program-profiles/{id}/ai-config-drift

### Policy Pack
POST /v1/organizations/{orgId}/policy-packs
POST /v1/policy-packs/{id}/versions
GET /v1/policy-packs/{id}/versions/{versionId}

### Knowledge and Recursive Learning
POST /v1/patterns/search
GET /v1/patterns/{id}
POST /v1/patterns/{id}/feedback
POST /v1/missed-findings
GET /v1/missed-findings/{id}
POST /v1/missed-findings/{id}/promote

### License
POST /v1/license/validate
POST /v1/license/activate
POST /v1/license/deactivate
POST /v1/license/credit/reserve
POST /v1/license/credit/commit
POST /v1/license/credit/release
POST /v1/license/seats/{seatId}/reassign
GET /v1/license/entitlements
GET /v1/license/jwks

## 8. Event 계약
- PaymentSucceeded, PaymentFailed, RefundCompleted
- LicenseIssued, LicenseSuspended, LicenseRevoked, EntitlementChanged
- CreditReserved, CreditCommitted, CreditReleased, CreditExhaustedMidRun
- CaseReady, CaseStarted, CaseBlocked, CaseCompleted
- BaselineChanged
- ReviewCompleted, VerificationCompleted, PatchCompleted
- EvidenceSealed, DeliveryPublished, DeletionCompleted
- PatternLearned, PatternPromoted, PatternDeprecated
- MissDetected, DetectionCapabilityUpdated, DetectionRegressionValidated
- NotificationSent, NotificationFailed, NotificationSuppressed(Opt-in 없음)
- MutationTestCompleted, BlastRadiusComputed, BehaviorDiffCompleted, SBOMGenerated
- CrossModelVerificationRequested, CrossModelVerificationDisagreed, SelfClaimMismatchDetected
- ComponentContractBreakingChange, CrossProgramImpactDetected
- RollbackVerified, RollbackVerificationFailed
- ConfidenceCalibrationDrifted, ReviewerAccuracyBelowThreshold, AIConfigDriftDetected
- AcceptanceCertificateIssued, AcceptanceCertificateRevoked, ExternalAcceptorGranted

이벤트는 event_id, occurred_at, producer, schema_version, organization_id, correlation_id, causation_id를 포함한다.

## 9. OLicense 책임과 경계
ORUDA/OLicense는 ProductCode ONSURE에 대해 다음을 관리한다.
- Catalog, Plan, Edition, Feature
- Web Case License와 VS Code Subscription
- Seat, Device, Active System, Program Capacity
- Learning Unit, ONSure Credit, Storage, Concurrency
- Validity, Suspension, Revocation
- Signed Entitlement Snapshot
- Offline Grace와 Clock Rollback 방어
- Usage와 Audit 원장

ONSure는 Validate, Activate, Reserve, Commit, Release, Report를 수행하며 발급·가격·한도 변경 권한을 갖지 않는다.

## 10. License Token 필드
- issuer, audience, subject
- organization_id
- product_code=ONSURE
- channel=WEB_CASE|VSCODE|API
- service_type
- plan
- feature_entitlements
- system_limit, program_limit, program_unit_limit
- learning_unit_limit, credit_balance 또는 credit_policy
- case_id 또는 subscription_id
- baseline_binding
- valid_from, valid_until
- offline_grace_until
- revocation_version
- key_id, signature

## 11. Offline 정책
- Signed Snapshot의 서명·Audience·시간·Revocation Version 검증
- Grace 기간 중 고위험 기능 제한 가능
- Clock Rollback 탐지 시 Fail-closed
- 장기 Offline은 별도 License File과 활성화 Receipt 필요
- Reconnect 시 Usage 동기화와 중복 방지

## 12. 보안 설계
- 고객 Source는 기본 비공개이며 전송·저장 암호화
- Worker당 임시 Credential과 최소권한
- Network Egress Allowlist
- Secret Scanning과 Log Redaction
- Artifact Content Addressing
- 관리자 Break-glass 승인과 감사
- 결제 카드정보 비보관, PCI-DSS 범위는 Payment Provider Tokenization으로 최소화
- Tenant Key 또는 Enterprise 전용 Key 옵션

## 12-1. 규제산업 컴플라이언스 설계
목표 고객에 금융·공공·의료 규제 산업이 포함되므로([01_BUSINESS_PRODUCT_SERVICE_PLAN.md:17](01_BUSINESS_PRODUCT_SERVICE_PLAN.md)) 다음을 Organization Plan 속성으로 관리한다.

- 데이터 거주지(Data Residency): Organization 단위로 처리·저장 Region을 고정하고 Cross-region 이동을 기본 차단
- 국내 개인정보보호법 대응: 개인정보 포함 가능 로그·소스에 대한 자동 탐지·마스킹과 처리방침 고지
- 금융권: 망분리 환경 대응은 On-premises/Air-gapped 배포 모델([04:19-21](04_ARCHITECTURE_DATA_API_OLICENSE.md))로 충족하며 SaaS Control Plane에는 원본 Source가 전송되지 않는 모드를 필수 옵션으로 제공
- 공공·의료: 고객별 별도 감사 로그 보존기간과 제3자 접근이력 조회 기능
- 이 절의 요건은 Enterprise Edition Feature Gate로 관리하며 일반 Plan에는 기본 보안 설계(위 12절)만 적용한다

## 13. 보존과 삭제
계약별 Retention 종료 후 Source, Build Artifact, Log, Profile, Evidence를 유형별 정책으로 삭제한다. Legal Hold가 없으면 삭제 작업과 결과 Hash를 Deletion Receipt로 남긴다.

Legal Hold는 Security Auditor 또는 Customer Owner의 명시적 요청과 ONSure Operator의 승인으로만 설정·해제하며(단독 설정 불가), 대상 Case/Evidence ID, 사유, 요청자, 예상 해제일을 기록한다. Legal Hold가 설정된 대상은 삭제 SLA 계산에서 제외하되 해제 즉시 원래 Retention 정책을 재적용한다. 규제산업 계약의 장기 보존 요구가 일반 삭제 SLA와 충돌하는 경우 계약서의 보존기간을 우선한다.