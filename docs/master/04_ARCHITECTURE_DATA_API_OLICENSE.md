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

- 격리 기술: 실제 `contracts/sandbox-boundary.v1.json`(README의 "Rootless Bubblewrap Sandbox"와 일치)은 Rootless Bubblewrap(`bwrap`)을 기본이자 유일한 허용 Backend로 고정한다(`remote_ci_backend: FORBIDDEN`) — Network/User Namespace 분리, Source는 Read-only 마운트, 쓰기는 `/tmp`로만 한정, 모든 Capability Drop, 실패 시 Fail-closed가 계약으로 강제된다. 이 설계서가 이전에 쓴 "MicroVM 또는 동급 격리 Container"는 부정확한 일반화이며 실제 백엔드는 Bubblewrap으로 고정이다. 실행 단위마다 신규 프로비저닝하며 이전 실행의 파일시스템·메모리·프로세스를 재사용하지 않는다. Tenant 격리는 organization_id/project_id/program_id/run_id를 모두 필수로 요구하고 Cross-tenant 읽기·쓰기를 기본 거부한다
- Syscall 필터링(DESIGN_ONLY, 신규 — NIST SP 800-190/일반 Linux 샌드박싱 관행 대조로 2026-08-09 발견): 현재 격리는 Namespace 분리 + Capability Drop까지만 적용되고 Seccomp-bpf 기반 Syscall Allowlist는 계약(`contracts/sandbox-boundary.v1.json`)에도 실제 `bwrap` 호출 스크립트에도 없다(`grep -rn seccomp` 결과 0건, 2026-08-09 확인). Capability Drop은 "무엇을 할 수 있는가"를 제한하지만 Seccomp은 "어떤 Syscall 자체를 호출할 수 있는가"를 제한하는 별개의 방어 계층이다 — 대상 코드의 실제 분석 툴체인(컴파일러·테스트러너 등)이 필요로 하는 Syscall 집합을 조사해 Allowlist를 만드는 별도 작업이 선행되어야 하므로 지금 코드에 반영하지 않고 설계서에만 기록한다
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
- TrainingRequest, TrainingPlan, TrainingRun, EvaluationReport (DESIGN_ONLY)
- ModelVersion, RAGIndexVersion, PromptVersion, AgentPolicyVersion (DESIGN_ONLY)
- DeploymentApproval, ProductionObservation, RelearnTrigger (DESIGN_ONLY)
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

Review/Verification 개별 Finding의 상세 판정은 `contracts/oreview-result.v1.schema.json`을 따른다: 영역(domain)마다 `PASS|FAIL|HOLD|NOT_RUN|NOT_APPLICABLE`을 매기고 최소 10개 영역을 요구하며, `quality_decision`(PASS/FAIL/HOLD)과 `merge_authorized`(계약상 항상 false, 별도 권한자가 Merge)를 분리한다. 이 설계서가 앞서 제안한 Finding 단위 장기 생애주기(OPEN→ACKNOWLEDGED→FIX_PLANNED→FIXED→RE_REVIEWED→CLOSED, ACCEPTED_RISK/FALSE_POSITIVE/DUPLICATE/WONT_FIX)는 계약에 없는 `DESIGN_ONLY` 확장이었다 — 실제로는 매 `validation_run`이 스냅샷 판정을 남긴다.

**결정(2026-08-09, G5)**: 새 Finding 생애주기 계약을 제정하지 않고 이 설계서를 스냅샷 모델에 맞춰 단순화한다. `security-findings.v1.schema.json`의 기존 3단계(OPEN/CLOSED/ACCEPTED_RISK)와 최신 `validation_run` 스냅샷을 조합하면 Case Dashboard·Organization Portfolio가 요구하는 "미해결 Critical/High Finding 수" 같은 실시간 집계를 충분히 구현할 수 있다. Finding을 가로지르는 별도 생애주기 엔티티는 이 값을 얻기 위한 필수 요건이 아니므로 도입하지 않는다(G9~G11의 "실제 계약에 맞춰 설계를 단순화" 선례를 따름).

### Improvement 실행 상세 — `contracts/patch-plan.v1.schema.json`
PatchPlan은 hunk 단위(`hunk_id`, `finding_id`, `preimage_sha256`, `approval_state`, `expected_effect`, `required_tests`)로 구성되며 `preapply_assessment`에 `risk_score`(0~100), `risk_level`(NONE~CRITICAL), `impact_scope`(changed_files, finding_ids)를 포함한다 — 이는 이 설계서가 제안한 Blast Radius 드라이런과 개념적으로 일치하지만, 실제 계약은 `PatchRun`이라는 별도 실행 엔티티나 `DRY_RUN`/`DRY_RUN_REVIEWED` 상태를 두지 않고 `preapply_assessment`를 PatchPlan 자체의 속성으로 포함한다. `patch-apply-receipt.v1.schema.json`, `patch-rollback-receipt.v1.schema.json`이 적용·Rollback 증거를 각각 담당한다. 이 설계서의 PatchRun 세부 상태(PENDING/DRY_RUN/RUNNING/REGRESSION_PENDING 등)와 BehaviorDiffReport는 계약에 없는 `DESIGN_ONLY` 확장이었다.

**결정(2026-08-09, G6)**: 별도 PatchRun 실행 엔티티나 DRY_RUN류 상태를 신설하지 않고, 이미 실재하는 `patch-plan.v1.schema.json`의 `preapply_assessment`(risk_score/risk_level/impact_scope)로 이 설계서를 단순화한다 — Blast Radius 드라이런이라는 의도는 `preapply_assessment`가 이미 PatchPlan 자체의 속성으로 충족한다. RollbackVerificationReceipt는 `patch-rollback-receipt.v1.schema.json`이 이미 담당하므로(§4-1 참조) 이 설계서에서 제거한다. BehaviorDiffReport(패치 전후 행동 변화 비교 리포트)만 대응 계약이 없는 채로 남는데, 이는 별도 신규 계약이 필요한지 여부를 향후 라운드에서 재검토한다.

### Knowledge/Memory — `contracts/failure-memory.v1.schema.json`, `improvement-memory.v1.schema.json`, `reusable-pattern-memory.v1.schema.json`
- FailureMemory: 필수 필드에 `first_failure_point`, `root_cause`, `confidence`(0~1)를 포함 — 이 설계서가 흡수한 RCA "최초 실패 지점·신뢰도" 프레이밍과 일치한다. `state`: CANDIDATE→VERIFIED→ACTIVE→QUARANTINED/STALE/ROLLED_BACK/HOLD
- ImprovementMemory: `decision`(IMPROVEMENT_PROVEN/NO_MEANINGFUL_IMPROVEMENT/REGRESSION_DETECTED/HOLD), `state`(CANDIDATE→VERIFIED→ACTIVE→STALE/ROLLED_BACK/HOLD)
- ReusablePatternMemory: `pattern_class`(AUTHORIZATION_POLICY_GAP/UNTRUSTED_INPUT_CONTROL_GAP/AVAILABILITY_BOUNDARY_FAILURE/REGRESSION_CONTROL_GAP/BEHAVIORAL_CONTRACT_DEVIATION 고정 5종), `independent_reproduction_count`(최소 2회), `deidentification`(raw_text_copied/project_identifiers_copied/evidence_identifiers_copied 모두 false 강제)

이 설계서의 KnowledgePattern(CANDIDATE→VALIDATED→TENANT_SCOPED/PROMOTED)과 `scope`(PROJECT_ONLY/REUSABLE_CANDIDATE는 계약에 이미 존재)는 위 세 계약으로 대체·정정한다. 재현 임계치는 이 설계서가 "3회 이상"으로 썼으나 계약은 최소 2회이므로 [08 체크리스트](08_REVIEW_CHECKLIST_OPEN_DECISIONS.md) C6을 계약값(2회) 기준으로 재검토해야 한다. `pattern_class` 5종 분류와 이 설계서의 AI/바이브 코딩 진단표([03 §4-1](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md))가 어떻게 매핑되는지는 아직 미정 — `DESIGN_ONLY`.

### MissedFinding / 재귀학습 승격 파이프라인
**2026-08-09 정정**: 아래 문단이 이전에 "MissedFinding은 실제 계약으로 확인됨"이라 쓴 것은 부정확했다. 실제로 확인된 것은 후보가 승격되는 **일반 파이프라인**(`contracts/learning-to-application-pipeline.v1.json`)뿐이며, 이 파이프라인은 `candidate_type`/`candidate_source_receipt_sha256`처럼 후보의 출처를 범용으로만 다룬다. **MissedFinding 고유의 필드(발견 경로, 원 실행 참조, 담당 Agent/모델 버전)는 이 계약 어디에도 없다** — MissedFinding은 이 일반 파이프라인에 후보를 "투입하는 입구" 역할을 하는 별도 엔티티로 설계되어야 하며, 아직 그 설계 자체가 없었다. 아래 두 절로 나눈다.

**일반 승격 파이프라인(실제 계약, `contracts/learning-to-application-pipeline.v1.json`/`contracts/learning-validation-engine.v1.json`)**: `LEARNING_CANDIDATE → VALIDATION_REQUESTED → VALIDATION_RUNNING → (VALIDATION_PASSED 또는 VALIDATION_FAILED) → PROMOTION_REVIEW → PROMOTION_APPROVED → SHADOW_APPLIED → CANARY_APPLIED → STABLE_APPLIED → APPLIED_LOCKED`(예외 ROLLED_BACK). 적용 등급은 3가지로 나뉜다: `VALIDATION_PACK_APPLY`(허용), `ONSURE_RUNTIME_CODE_APPLY`(제한적 허용, Human Review 필수), `TARGET_PRODUCT_APPLY`(현재 불허 — §7-2 OTraining 참조). `applied_count`는 Active Selector·Apply Commit·Post-apply Verification·Rollback Pointer가 모두 있는 STABLE_APPLIED/APPLIED_LOCKED만 집계한다.

**MissedFinding 엔티티(신규 기능정의, 2026-08-09 — 아직 `DESIGN_ONLY`, 계약 미제정)**: 자동 판정이 놓친 결함을 위 파이프라인에 투입하기 전 단계에서 포착·분류하는 엔티티. 필드:
- `missed_finding_id`
- `discovery_path`: INDEPENDENT_REVIEW_DISAGREEMENT | HUMAN_REVIEW_OVERRIDE | PRODUCTION_INCIDENT | CUSTOMER_REPORT | DELAYED_REGRESSION
- `original_run_reference`
- `agent_context`: Agent 역할, 모델 버전, Rule Pack Digest
- `rca_reference`: `contracts/evidence-based-rca.v1.schema.json` 결과 링크
- `promoted_candidate_id`
- `status`: OPEN → RCA_IN_PROGRESS → CANDIDATE_SUBMITTED
- `registered_at`, `registered_by`

이 설계는 기존 5단계를 대체한다. 승격 이후 단계는 일반 파이프라인을 그대로 사용한다.

### 아직 계약이 없는 이 설계서의 확장 (DESIGN_ONLY)
MissedFinding, RollbackVerificationReceipt, ConfidenceCalibrationReport, ReviewerAccuracyScore, AIConfigDriftReport, PeerBenchmark, AcceptanceCertificate/ExternalAcceptorGrant, CoverageReport, NotificationRule/NotificationEvent, PolicyPack/PolicyPackVersion, ReproducibilityAuditSample, SBOM, TrainingRequest/Plan/Run, ModelVersion/RAGIndexVersion/PromptVersion/AgentPolicyVersion, DeploymentApproval/ProductionObservation/RelearnTrigger는 계약 미제정 범위다.

### TrainingRequest / TrainingRun (DESIGN_ONLY)
TrainingRequest: DRAFT → SCOPED → APPROVED → TRAINING_IN_PROGRESS → EVALUATION_PENDING → (INDEPENDENTLY_VERIFIED → DEPLOYMENT_APPROVED → DEPLOYED) 또는 (EVALUATION_FAILED → SCOPED로 재진입)
예외: REJECTED, ABANDONED

TrainingRun: PENDING → DATA_QUALITY_CHECK → RUNNING → EVALUATION_RUNNING → (EVALUATION_PASSED 또는 EVALUATION_FAILED)
예외: FAILED, ABORTED

DEPLOYMENT_APPROVED는 Training을 수행한 모델·Provider와 다른 계열의 독립 재검증을 통과해야만 도달한다. DEPLOYED 이후 ProductionObservation에서 임계치를 넘는 성능저하가 확인되면 RelearnTrigger가 새 TrainingRequest 초안을 제안한다.

### CreditReservation — `contracts/license-state.v1.schema.json`의 `reservations` 필드
RESERVED → COMMITTED 또는 RELEASED 또는 EXPIRED.

License 자체는 `contracts/license-state.v1.schema.json`에 이미 실재한다: `status`(ISSUED/ACTIVE/SUSPENDED/REVOKED/EXPIRED), `offline_grace_hours`, `clock_tolerance_seconds`, `credits{total,available,reserved,committed}`가 필드로 존재한다. 실행 도중 Credit이 소진되면 진행 중인 `validation_run`은 안전한 Checkpoint까지만 진행한 뒤 HOLD로 전이한다.

## 6. API 원칙
- REST와 Event를 병행한다.
- 모든 Write API는 Idempotency-Key를 지원한다.
- Organization과 License Context를 명시한다.
- 낙관적 잠금과 Version을 사용한다.
- 오류는 machine-readable code와 retryable 여부를 포함한다.
- PII와 Secret을 오류 본문에 넣지 않는다.
- 인증: Web/Admin Console은 Session Cookie + CSRF Token, VS Code Extension은 OAuth2 Authorization Code + PKCE로 발급한 단기 Access Token과 Refresh Token, 외부 시스템 M2M 연동은 OAuth2 Client Credentials를 기본으로 하며 Enterprise에 한해 IP Allowlist가 결속된 장기 API Key를 허용한다
- Access Token 수명은 1시간 이내로 하고 Refresh Token 회전(Rotation)을 적용한다
- 객체 수준 권한 검사: 객체 ID 기반 API는 매 요청마다 구체적 객체가 호출자의 Organization/Tenant Context에 실제로 속하는지 검사한다
- 민감 업무 흐름 보호: 금전·승인이 걸린 흐름은 일반 Rate Limit과 별개로 남용 탐지를 적용한다

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
GET /v1/certificates/{certId}/verify

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

### Training (OTraining, DESIGN_ONLY — workflow-operation-registry.v1.json 미등록)
POST /v1/training-requests
POST /v1/training-requests/{id}/plan
POST /v1/training-runs
GET /v1/training-runs/{id}/evaluation-report
POST /v1/training-runs/{id}/deployment-approval
GET /v1/model-versions/{id}
GET /v1/deployments/{id}/observation

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
- NotificationSent, NotificationFailed, NotificationSuppressed
- MutationTestCompleted, BlastRadiusComputed, BehaviorDiffCompleted, SBOMGenerated
- CrossModelVerificationRequested, CrossModelVerificationDisagreed, SelfClaimMismatchDetected
- ComponentContractBreakingChange, CrossProgramImpactDetected
- RollbackVerified, RollbackVerificationFailed
- ConfidenceCalibrationDrifted, ReviewerAccuracyBelowThreshold, AIConfigDriftDetected
- AcceptanceCertificateIssued, AcceptanceCertificateRevoked, ExternalAcceptorGranted
- TrainingRequested, TrainingRunCompleted, EvaluationFailed, DeploymentApproved, RelearnTriggered

이벤트는 event_id, occurred_at, producer, schema_version, organization_id, correlation_id, causation_id를 포함한다.

## 9. OLicense 책임과 경계
ORUDA/OLicense는 ProductCode ONSURE에 대해 Catalog, Plan, Edition, Feature, Web Case License/VS Code Subscription, Seat/Device/System/Program Capacity, Credit, Validity/Suspension/Revocation, Signed Entitlement Snapshot, Offline Grace, Usage/Audit 원장을 관리한다. ONSure는 Validate, Activate, Reserve, Commit, Release, Report를 수행하며 발급·가격·한도 변경 권한을 갖지 않는다.

## 10. License Token 필드
- issuer, audience, subject
- organization_id
- product_code=ONSURE
- channel=WEB_CASE|VSCODE|API
- service_type=LEARN|VERIFY|LEARN_VERIFY|IMPROVE_REVERIFY|TRAIN_REVERIFY
- plan=DEVELOPER|TEAM|ENTERPRISE
- feature_entitlements
- system_limit, program_limit, program_unit_limit
- learning_unit_limit, credit_balance 또는 credit_policy
- dataset_limit, training_run_limit, model_version_limit (OTraining DESIGN_ONLY)
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
- 신뢰 경계 원칙: 고객 Repository의 자체 결과를 최종 판정으로 신뢰하지 않는다. 개선을 생성한 Model과 최종 Reviewer/Oracle을 분리한다. 클라이언트의 Feature 표시를 권한 증거로 사용하지 않으며 실행마다 서버가 Entitlement를 재확인한다. 결제 성공 Event만으로 실행을 허용하지 않고 OLicense의 License ACTIVE 상태를 별도로 확인한다

## 12-1. 규제산업 컴플라이언스 설계
목표 고객에 금융·공공·의료 규제 산업이 포함되므로 다음을 Organization Plan 속성으로 관리한다.
- 데이터 거주지(Data Residency)
- 국내 개인정보보호법 대응
- 금융권 망분리/On-premises/Air-gapped
- 공공·의료 감사 로그 보존/제3자 접근이력
- 규제 프레임워크 버전관리(NIST, ISO, OWASP, MITRE, 금융권 MRM 등)
- Enterprise Edition Feature Gate

## 13. 보존과 삭제
계약별 Retention 종료 후 Source, Build Artifact, Log, Profile, Evidence를 유형별 정책으로 삭제한다. Legal Hold가 없으면 삭제 작업과 결과 Hash를 Deletion Receipt로 남긴다.

Legal Hold는 Security Auditor 또는 Customer Owner의 명시적 요청과 ONSure Operator의 승인으로만 설정·해제하며(단독 설정 불가), 대상 Case/Evidence ID, 사유, 요청자, 예상 해제일을 기록한다. Legal Hold가 설정된 대상은 삭제 SLA 계산에서 제외하되 해제 즉시 원래 Retention 정책을 재적용한다. 규제산업 계약의 장기 보존 요구가 일반 삭제 SLA와 충돌하는 경우 계약서의 보존기간을 우선한다.

## 14. Meta-Validation Architecture 및 Cross-Contract Final Assurance 상세설계 (신규, 2026-08-09)
이 절은 개별 Schema 유효성만으로 Final Claim을 만들지 않고 **Target·Scope·Evidence·Oracle·Authority·State의 전체 Contract Graph를 재계산**하기 위한 설계다. 기존 계약이 강제하는 `final_claim_allowed:false`는 유지하며, 아래 항목은 현 시점 `DESIGN_ONLY`인 신규 설계다.

### 14.1 신규 논리 컴포넌트
- **ValidationTargetManifestService**: 검증 대상의 전체 정체성을 생성·잠금
- **ScopeEpochService**: Scope/Requirement Universe의 버전·변경 추적
- **ValidatorCapabilityQualificationService**: Target 유형별 검증자격 계산
- **ObservabilityQualificationService**: 결함 유형별 필요한 관측채널 충족 여부 계산
- **CrossContractInvariantEngine**: Contract/Receipt 간 의미 불변식 재검산
- **AtomicSnapshotAssembler**: 동일 Context에서 동시에 성립한 결과만 Snapshot 구성
- **FinalClaimReconstructor**: 저장된 PASS/Score를 믿지 않고 Raw Evidence에서 Final Claim 재계산
- **FinalFreshnessBarrier**: Lock 직전 변경·신규 Finding·만료 여부 재검증
- **AssuranceValidityService**: VALID/STALE/INVALIDATED/SUPERSEDED 수명주기 관리
- **HistoricalImpactScanner**: 새 Rule/MissedFinding 발생 시 과거 인증 영향분석

### 14.2 ValidationTargetManifest(ProductLock)
SourceLock보다 넓은 제품 정체성을 정의한다. 필수 digest 후보:
- source/commit/tree
- build artifact
- dependency artifacts + provenance
- runtime configuration
- feature flags
- policy/rule pack
- model/provider/deployment
- system prompt/tool definitions
- RAG corpus/embedding/index
- external service contracts
- OS/runtime/DB/deployment environment

`target_manifest_digest`가 바뀌면 기존 Final Claim은 자동 STALE 대상이다.

### 14.3 Scope/Requirement Epoch
- `scope_epoch`, `scope_digest`
- `requirement_epoch`, `requirement_set_digest`
- `discovered_components`, `excluded_components`, `unknown_components`

새 Component/Requirement 발견 시 denominator를 다시 계산하고 이전 Coverage를 `STALE_COVERAGE`로 만든다. Scope 축소·확대는 기존 PASS를 유지한 채 조용히 변경할 수 없다.

### 14.4 Evidence Target Binding
현재 Evidence Receipt의 source/policy/environment/toolchain 결속에 더해 Final용 Evidence는 다음을 필수 결속한다.
- target_manifest_digest
- scope_epoch_digest
- requirement_set_digest
- oracle_set_digest
- detector_pack_digest
- validation_generation_id
- run_id/nonce

다른 Target/Run/Scope의 증거를 재사용하는 Evidence Substitution을 차단한다.

### 14.5 Atomic Validation Snapshot
Final은 여러 Run의 좋은 결과를 선택해 조립하지 않는다. Snapshot에 포함되는 모든 필수 Lane은 같은 target/scope/requirement/policy/config generation에 속해야 한다. 서로 다른 Epoch의 PASS fragment를 합친 결과는 Final 후보가 될 수 없다.

### 14.6 Cross-Contract Invariant Engine
최소 불변식:
- X001 FinalLock의 Approval decision은 `APPROVE`
- X002 FinalLock.candidate_digest = FinalCandidate.candidate_digest
- X003 FinalCandidate `eligible=true` ↔ `decision=PASS`
- X004 Run1/Run2 모두 PASS이며 required context가 동일
- X005 Run1/Run2는 정책이 요구하는 principal/execution 독립성 충족
- X006 CANCELLED/NOT_RUN/HOLD/BLOCKED/INCONCLUSIVE는 positive Final Evidence로 사용 금지
- X007 ServiceVerification approval_purpose ↔ verification_type 일치
- X008 `approved_at < expires_at`, 소비시각은 validity window 안
- X009 RiskScore = raw findings에서 독립 재계산한 값
- X010 Key Registry key_id/fingerprint uniqueness 및 authority cardinality
- X011 revoked/revoked_at/validity window 의미 일치
- X012 모든 Final material claim은 PRIMARY Evidence까지 역추적

각 invariant에는 최소 1개의 dedicated negative fixture를 등록한다.

### 14.7 상태 온톨로지 분리
다음은 서로 다른 차원이며 하나의 선형 상태로 혼합하지 않는다.
- Execution Lifecycle: RUNNING/CANCELLED/FAILED/EVIDENCE_LOCKED
- Verification Decision: PASS/FAIL/HOLD/NOT_RUN/INCONCLUSIVE/NON_FINAL
- Technical Assurance: SELF_VALIDATION_NONFINAL/INDEPENDENT_* / FINAL_LOCKED
- Human Acceptance
- Deployment Authorization(PRODUCTION_GO)
- Commercial Authorization(COMMERCIAL_GO)

`PUBLICATION_ELIGIBLE` 같은 내부 상태가 UI/API에서 `FINAL_LOCKED`로 의미 상승되지 않도록 명시적 mapping을 둔다.

### 14.8 State Authenticity / Reconstruction
DB의 `current_state` 문자열을 권위로 사용하지 않는다. Strong State는 predecessor, event, receipts, decisions, authority, lineage를 재구성할 때만 유효하다. 재시작 시 ledger/event chain으로 상태를 다시 계산할 수 있어야 한다.

### 14.9 Final Claim Reconstructor
Final report/score의 저장된 값을 읽어 승인하지 않는다. 다음 raw source에서 다시 계산한다.
1. Target Manifest
2. Scope/Requirement Epoch
3. Run receipts
4. Oracle results
5. Findings/closure/accepted risk
6. OTester/OAudit receipts
7. Human acceptance
8. Evidence graph
9. Freshness/Revocation events

재구성 결과와 저장된 report가 다르면 `FINAL_CLAIM_MISMATCH_HOLD`다.

### 14.10 ProgramRiskScore 재계산
`program-risk-score.v1.schema.json`은 필드와 grade band를 검증하지만 JSON Schema만으로 설명문상의 산술공식을 증명하지 못한다. 따라서 Score consumer/Final Claim은 raw Finding/MissedFinding에서 점수를 독립 재계산하고 저장된 `score`와 비교한다. Score는 보조지표이며 Hard Invariant를 대체하지 않는다.

### 14.11 Approval Context Binding
Approval은 candidate digest뿐 아니라 `target_manifest + scope + requirement + policy + assurance policy`의 canonical `approval_context_digest`에 결속한다. `nonce`, single-consume ledger, expires/revocation 검사를 강제한다. Action 승인에는 verb뿐 아니라 resource/scope/parameter/purpose를 canonical `action_intent_digest`로 결속한다.

### 14.12 Final Freshness Barrier
Final Lock 직전에 다음을 원시 상태에서 재검산한다.
- target/config/dependency/model/prompt/RAG 변경 없음
- scope/requirement/policy/rule/oracle 변경 없음
- Candidate 이후 신규 Critical/High 없음
- Approval 유효·미폐기·미사용
- required observer/collector complete
- OTester/OAudit receipts current
- Certificate invalidation event 없음

하나라도 실패하면 Lock을 발행하지 않는다.

### 14.13 Evidence Transactionality
Evidence/Receipt/Ledger/ChainHead는 논리 transaction으로 기록한다. 상태는 `PREPARED→COMMITTED`를 사용하고 crash 중간 기록은 `ABORTED_UNTRUSTED`로 복구한다. 일부 파일 존재만으로 PASS를 재구성하지 않는다.

### 14.14 Final Lock 원자성·멱등성
- candidate_digest당 active Final Lock 최대 1개
- approval_id/nonce single-consume
- 동일 요청 retry는 동일 결과 반환
- candidate revalidation, approval consume, lock creation, audit append 중간 crash는 재시작 후 결정론적으로 복구

### 14.15 Assurance Revocation
Final Lock 기록은 삭제/변조하지 않지만 현재 유효성은 변경 가능하다.
- `VALID`
- `STALE`
- `INVALIDATED`
- `SUPERSEDED`

새 Critical, CVE, policy/regulation 변경, deployed artifact drift, MissedFinding 영향이 발생하면 certificate 상태를 갱신하고 영향을 받은 사용자에게 통지한다.

### 14.16 Verified-to-Deployed Identity
`validated_artifact_digest == deployed_artifact_digest`가 아니면 운영 제품에 대한 Final Claim을 유지하지 않는다. Validation Environment와 Production Environment 차이를 `MATCH|NON_MATERIAL|MATERIAL|UNKNOWN`으로 분류하며 MATERIAL/UNKNOWN은 Assurance Ceiling에 반영한다.

### 14.17 Observability Qualification
결함 종류마다 Required Observation Matrix를 정의한다. 예: Cross-tenant DB write 검증은 API 결과만으로 충분하지 않고 Authority Event/DB State 또는 동등한 독립 관측을 요구할 수 있다. Collector가 중간 종료되면 부재 주장(No leak 등)을 PROVEN으로 만들지 않는다.

### 14.18 Evidence Origin / Claim Graph
Evidence에는 origin과 변환계보를 부여한다.
- PRIMARY
- DERIVED
- AGGREGATED

같은 원천에서 생성된 여러 파일은 독립증거 하나로 센다. Claim graph는 자기 자신으로 순환해 증명할 수 없고 PRIMARY source까지 역추적 가능해야 한다.

### 14.19 Trust Registry Semantic Integrity
Key Registry는 단순 Schema-valid를 넘어 다음을 강제한다.
- unique key_id / public-key fingerprint
- independent role에 요구되는 principal 분리
- 각 필수 authority 최소 cardinality
- valid_from < valid_until
- revoked=true면 revoked_at 필수
- signing time이 key validity window 내
- revoked/replaced key의 소비정책 명시

`different key_id`만으로 독립 principal을 증명하지 않는다.

### 14.20 Validator TCB(Trusted Computing Base)
Final verifier가 암묵적으로 신뢰하는 OS/kernel/JVM/crypto library/filesystem/time source/key registry/sandbox runtime을 TCB Manifest로 공개한다. TCB를 최소화하고 Final Receipt Verifier는 가능한 한 ONSure business logic과 독립된 작은 reference implementation으로 유지한다.

### 14.21 API/운영 수용기준
- Final 관련 API는 저장된 PASS/LOCKED 상태만 읽어 성공을 반환하지 않는다.
- `REJECT` Approval, mismatched Target/Scope/Epoch, expired approval은 Final Lock에 절대 사용되지 않는다.
- 동일 Candidate의 이중 Lock과 동일 Approval 재사용을 차단한다.
- 신규 invalidation event가 있으면 Certificate verify API가 즉시 현재 상태를 반환한다.
- ProgramRiskScore A등급이 Final Hard Gate를 우회할 수 없다.
- Schema-valid하더라도 Cross-Contract Invariant가 실패하면 요청은 fail-closed한다.
