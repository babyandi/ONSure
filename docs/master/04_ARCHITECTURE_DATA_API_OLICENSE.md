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

이 절의 상태값은 실제 커밋된 `contracts/*.schema.json`과 `contracts/state-model-mapping.v1.json`을 1차 근거로 삼는다. 그 계약에 없는 이름은 이 설계서가 새로 제안하는 확장이며 [ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md §4](../architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md)의 `DESIGN_ONLY`로 표시한다. 필드 단위 상세는 이 문서가 아니라 해당 계약 파일을 원본으로 본다.

### ServiceCase (상거래 계층) — `contracts/service-case-state.v1.schema.json`
PREFLIGHT_REQUIRED → PREFLIGHT_BLOCKED → QUOTE_READY → QUOTED → QUOTE_EXPIRED → PAYMENT_PENDING → PAYMENT_RECEIPT_RECORDED → PAYMENT_CONFIRMED → PAYMENT_REJECTED → IN_PROGRESS → DELIVERED_AWAITING_ACCEPTANCE → DELIVERY_ACCEPTED

예외: REFUND_PENDING, REFUND_RECEIPT_RECORDED, REFUNDED, REFUND_REJECTED, CANCELLED, CLOSED

ServiceCase는 계약상 `final_claim_allowed: false`가 고정값이며, `legal_hold`/`legal_hold_reason`/`retention_state`(ACTIVE 또는 DELETED_SIGNED_EXTERNAL_VERIFICATION)를 필수 필드로 가진다. IN_PROGRESS는 커머스 계층에서 하나의 상태로만 존재하며, Learning/Review/Verification/Improvement의 세부 진행은 ServiceCase가 아니라 아래 실행 계층 상태기계가 `target_reference`로 연결되어 독립적으로 추적한다 — 즉 상거래 상태와 기술실행 상태는 의도적으로 분리되어 있다(단일 거대 상태기계가 아님).

QUOTED는 발급 후 14일간 유효하며(값은 [08_REVIEW_CHECKLIST_OPEN_DECISIONS.md](08_REVIEW_CHECKLIST_OPEN_DECISIONS.md) A3 확인 전까지 DRAFT), 만료 전 결제되지 않으면 QUOTE_EXPIRED로 전이한다. Preflight 이후 대상 Repository 규모가 초기 예상 대비 20%를 초과해 변하면 재견적을 요구한다(같은 이유로 DRAFT).

Learn/Verify/Learn&Verify/Improve&Reverify 4상품(01 §6)이 어떻게 IN_PROGRESS 하나의 상태 안에서 서로 다른 실행 계층 조합(아래 5개 상태기계 중 어느 것을 발동하는지)으로 구분되는지는 아직 계약으로 확정되지 않았다 — `DESIGN_ONLY`.

### 실행 계층 5개 상태기계 — `contracts/state-model-mapping.v1.json`
ServiceCase의 `target_reference`가 가리키는 대상에 대해 독립적으로 동작하며, Case 존재 여부와 무관하게(예: VS Code 구독의 지속적 실행) 재사용된다.

| 상태기계 | 상태 흐름 | 종료 성공 | 대응 Design 개념 |
|---|---|---|---|
| `program_profile` | UNREGISTERED→REGISTERED→INTAKE_READY→LEARNING→PROFILE_CANDIDATE→PROFILE_REVIEWED→PROFILE_ACTIVE (+STALE, HOLD) | PROFILE_ACTIVE | OLearning / ProgramProfile |
| `validation_run` | PLANNED→AWAITING_APPROVAL→READY→RUNNING→OBSERVED→DECIDED→EVIDENCE_LOCKED (+FAILED/RETRYABLE/CANCELLED/HOLD/NOT_RUN/INCONCLUSIVE) | EVIDENCE_LOCKED | OReview + OVerification 실행 |
| `improvement` | FINDING_CONFIRMED→IMPROVEMENT_PLANNED→AWAITING_PATCH_APPROVAL→PATCH_APPROVED→APPLYING→APPLIED_NON_FINAL→REGRESSION_RUNNING→(IMPROVEMENT_PROVEN\|NO_EFFECT\|REGRESSION_DETECTED)→DELIVERY_READY (+ROLLED_BACK, HOLD) | DELIVERY_READY | OImprovement / ImprovementRequest·PatchPlan·PatchRun |
| `git_delivery` | NO_CHANGE→WORKTREE_READY→CHANGES_APPLIED→LOCAL_VERIFIED→COMMITTED→PUSHED→DRAFT_PR_OPEN→REMOTE_CI_RUNNING→MERGE_READY_CANDIDATE→MERGED (+ROLLED_BACK, HOLD) | MERGED | OGit |
| `assurance_publication` | DESIGN_BASELINE→IMPLEMENTATION_CANDIDATE→SELF_VALIDATION_NONFINAL→INDEPENDENT_OTESTER_PASS→INDEPENDENT_OAUDIT_PASS→HUMAN_ACCEPTANCE_PASS→FINAL_CANDIDATE→FINAL_LOCKED→PRODUCTION_GO→COMMERCIAL_GO (+HOLD, BLOCKED, 순서 고정) | COMMERCIAL_GO | 00 §8 출시 Gate |

계약의 `mapping_rules`는 하위 상태기계의 성공이 상위를 자동 함의하지 않는다고 명시한다(`PROGRAM_PROFILE_ACTIVE_DOES_NOT_IMPLY_VALIDATION_PASS`, `VALIDATION_EVIDENCE_LOCKED_DOES_NOT_IMPLY_FINAL_PASS`, `IMPROVEMENT_PROVEN_DOES_NOT_IMPLY_MERGE_READY`, `MERGED_DOES_NOT_IMPLY_PRODUCTION_GO`, `SELF_VALIDATION_CANNOT_ISSUE_INDEPENDENT_PASS`) — 이는 [00 §6](00_ONSURE_MASTER_DESIGN_SET.md)의 "Review PASS가 Verification PASS를 의미하지 않는다" 원칙과 같은 근거를 공유한다.

Review/Verification 개별 Finding의 상세 판정은 `contracts/oreview-result.v1.schema.json`을 따른다: 영역(domain)마다 `PASS|FAIL|HOLD|NOT_RUN|NOT_APPLICABLE`을 매기고 최소 10개 영역을 요구하며, `quality_decision`(PASS/FAIL/HOLD)과 `merge_authorized`(계약상 항상 false, 별도 권한자가 Merge)를 분리한다. 이 설계서가 앞서 제안한 Finding 단위 장기 생애주기(OPEN→ACKNOWLEDGED→FIX_PLANNED→FIXED→RE_REVIEWED→CLOSED, ACCEPTED_RISK/FALSE_POSITIVE/DUPLICATE/WONT_FIX)는 계약에 없는 `DESIGN_ONLY` 확장이다 — 실제로는 매 `validation_run`이 스냅샷 판정을 남기고, Finding을 가로지르는 생애주기 추적 계약은 아직 없다.

### Improvement 실행 상세 — `contracts/patch-plan.v1.schema.json`
PatchPlan은 hunk 단위(`hunk_id`, `finding_id`, `preimage_sha256`, `approval_state`, `expected_effect`, `required_tests`)로 구성되며 `preapply_assessment`에 `risk_score`(0~100), `risk_level`(NONE~CRITICAL), `impact_scope`(changed_files, finding_ids)를 포함한다 — 이는 이 설계서가 제안한 Blast Radius 드라이런과 개념적으로 일치하지만, 실제 계약은 `PatchRun`이라는 별도 실행 엔티티나 `DRY_RUN`/`DRY_RUN_REVIEWED` 상태를 두지 않고 `preapply_assessment`를 PatchPlan 자체의 속성으로 포함한다. `patch-apply-receipt.v1.schema.json`, `patch-rollback-receipt.v1.schema.json`이 적용·Rollback 증거를 각각 담당한다. 이 설계서의 PatchRun 세부 상태(PENDING/DRY_RUN/RUNNING/REGRESSION_PENDING 등)와 BehaviorDiffReport/RollbackVerificationReceipt는 아직 계약에 없는 `DESIGN_ONLY` 확장이다.

### Knowledge/Memory — `contracts/failure-memory.v1.schema.json`, `improvement-memory.v1.schema.json`, `reusable-pattern-memory.v1.schema.json`
- FailureMemory: 필수 필드에 `first_failure_point`, `root_cause`, `confidence`(0~1)를 포함 — 이 설계서가 흡수한 RCA "최초 실패 지점·신뢰도" 프레이밍과 일치한다. `state`: CANDIDATE→VERIFIED→ACTIVE→QUARANTINED/STALE/ROLLED_BACK/HOLD
- ImprovementMemory: `decision`(IMPROVEMENT_PROVEN/NO_MEANINGFUL_IMPROVEMENT/REGRESSION_DETECTED/HOLD), `state`(CANDIDATE→VERIFIED→ACTIVE→STALE/ROLLED_BACK/HOLD)
- ReusablePatternMemory: `pattern_class`(AUTHORIZATION_POLICY_GAP/UNTRUSTED_INPUT_CONTROL_GAP/AVAILABILITY_BOUNDARY_FAILURE/REGRESSION_CONTROL_GAP/BEHAVIORAL_CONTRACT_DEVIATION 고정 5종), `independent_reproduction_count`(최소 2회), `deidentification`(raw_text_copied/project_identifiers_copied/evidence_identifiers_copied 모두 false 강제)

이 설계서의 KnowledgePattern(CANDIDATE→VALIDATED→TENANT_SCOPED/PROMOTED)과 `scope`(PROJECT_ONLY/REUSABLE_CANDIDATE는 계약에 이미 존재)는 위 세 계약으로 대체·정정한다. 재현 임계치는 이 설계서가 "3회 이상"으로 썼으나 계약은 최소 2회이므로 [08 체크리스트](08_REVIEW_CHECKLIST_OPEN_DECISIONS.md) C6을 계약값(2회) 기준으로 재검토해야 한다. `pattern_class` 5종 분류와 이 설계서의 AI/바이브 코딩 진단표([03 §4-1](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md))가 어떻게 매핑되는지는 아직 미정 — `DESIGN_ONLY`.

### 아직 계약이 없는 이 설계서의 확장 (DESIGN_ONLY)
다음은 이번 세션에서 제안했으나 대응하는 `contracts/*.schema.json`을 찾지 못했다. 아이디어 자체를 폐기하라는 뜻이 아니라, 구현 전 계약부터 만들어야 한다는 뜻이다.

CreditReservation, CaseRevision, Baseline 동시성 다중 Branch 처리, MissedFinding(재귀학습 루프), ComponentContract/Cross-Program Impact Scan, BlastRadiusReport(PatchPlan.preapply_assessment로 부분 흡수됨), RollbackVerificationReceipt, ConfidenceCalibrationReport, ReviewerAccuracyScore, AIConfigDriftReport, PeerBenchmark, AcceptanceCertificate/ExternalAcceptorGrant, CoverageReport, NotificationRule/NotificationEvent, PolicyPack/PolicyPackVersion, ProgramRiskScore, ReproducibilityAuditSample, SBOM

### CreditReservation (DESIGN_ONLY)
RESERVED → COMMITTED 또는 RELEASED; Timeout 시 자동 RELEASED

실행 도중 CreditReservation이 소진되면 진행 중인 `validation_run`은 안전한 Checkpoint까지만 진행한 뒤 ServiceCase를 REFUND_PENDING이 아닌 별도 대기 상태로 전이해야 하나, 계약에 이런 대기 상태가 없어 실제로는 위 5개 상태기계의 HOLD를 재사용하는 방안을 검토해야 한다(설계 미확정).

### CaseRevision (DESIGN_ONLY)
Improve & Re-verify를 DELIVERY_ACCEPTED 이후의 새 CaseRevision으로 처리한다는 설계는 유지하되, `service-case-state.v1.schema.json`에 CaseRevision을 위한 필드나 상태가 없으므로 계약 확장이 선행되어야 한다.

### ComponentContract (DESIGN_ONLY)
DRAFT → ACTIVE → SUPERSEDED, 예외 BREAKING_CHANGE_FLAGGED. Cross-Program Impact Scan 아이디어(Provided Interface 변경 시 다른 Program에 Finding 전파)는 유효하나 이를 뒷받침할 `component-contract.v1.schema.json` 계약이 아직 없다.

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
- 규제 프레임워크 버전관리: NIST, ISO, OWASP, MITRE, 금융권 MRM(Model Risk Management) 등 외부 규제·표준 프레임워크를 버전 단위로 등록하고 PolicyPack에 매핑하며, 프레임워크 개정 시 영향받는 정책과 재검증 대상을 추적한다(Compliance Officer 승인 필요)
- 이 절의 요건은 Enterprise Edition Feature Gate로 관리하며 일반 Plan에는 기본 보안 설계(위 12절)만 적용한다

## 13. 보존과 삭제
계약별 Retention 종료 후 Source, Build Artifact, Log, Profile, Evidence를 유형별 정책으로 삭제한다. Legal Hold가 없으면 삭제 작업과 결과 Hash를 Deletion Receipt로 남긴다.

Legal Hold는 Security Auditor 또는 Customer Owner의 명시적 요청과 ONSure Operator의 승인으로만 설정·해제하며(단독 설정 불가), 대상 Case/Evidence ID, 사유, 요청자, 예상 해제일을 기록한다. Legal Hold가 설정된 대상은 삭제 SLA 계산에서 제외하되 해제 즉시 원래 Retention 정책을 재적용한다. 규제산업 계약의 장기 보존 요구가 일반 삭제 SLA와 충돌하는 경우 계약서의 보존기간을 우선한다.