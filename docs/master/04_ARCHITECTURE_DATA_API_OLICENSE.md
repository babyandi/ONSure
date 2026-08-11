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
- `discovery_path`(enum, 02 §7-1 수용기준에 이미 있던 5가지를 그대로 채택): `INDEPENDENT_REVIEW_DISAGREEMENT`(Cross-Model Verification 불일치로 발견), `HUMAN_REVIEW_OVERRIDE`(사람이 자동판정을 뒤집음), `PRODUCTION_INCIDENT`(실제 운영 사고로 확인), `CUSTOMER_REPORT`(고객 신고), `DELAYED_REGRESSION`(뒤늦은 회귀)
- `original_run_reference`: 놓쳤어야 할 원 OReview/OVerification 실행의 run_id·리뷰 영역·시각 — "어떤 판정이 왜 틀렸는지" 역추적의 시작점
- `agent_context`: 원 실행을 수행한 Agent 역할, 모델 버전, Rule Pack Digest([07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md](07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md) §4 "MissedFinding의 RCA는 항상 어떤 Agent·모델버전·Rule Pack Digest에서 발생했는지 식별하는 것에서 시작한다"를 필드로 구체화한 것)
- `rca_reference`: `contracts/evidence-based-rca.v1.schema.json` 결과로의 링크(이미 실재하는 RCA 계약을 재사용, 신규 RCA 형식을 만들지 않음)
- `promoted_candidate_id`: RCA 완료 후 Rule/Pattern 개정안이 나오면, 그 시점부터는 위 일반 파이프라인의 `LEARNING_CANDIDATE`가 되며 이 필드가 `candidate_source_receipt_sha256`로 두 엔티티를 연결하는 다리 역할을 한다
- `status`: `OPEN`(등록됨) → `RCA_IN_PROGRESS` → `CANDIDATE_SUBMITTED`(이후 상태는 일반 파이프라인의 `states`를 그대로 참조, 별도 상태를 새로 정의하지 않는다)
- `registered_at`, `registered_by`

이 설계는 기존 5단계(`DETECTED→RCA_DONE→CAPABILITY_UPDATED→REGRESSION_VALIDATED→PROMOTED`)를 완전히 대체한다 — 승격 이후 단계는 이미 실재하는 일반 파이프라인의 더 세분화된 상태머신(SHADOW/CANARY 포함)을 그대로 쓰고, MissedFinding 고유 상태는 파이프라인 투입 이전 구간(`OPEN`/`RCA_IN_PROGRESS`/`CANDIDATE_SUBMITTED`)만 남긴다.

**`contracts/state-model-mapping.v1.json`과의 관계 (G4, 2026-08-11 정리)**: 실제 파일을 확인하면 `machines`에 등록된 것은 정확히 5개뿐이다 — `program_profile`, `validation_run`, `improvement`, `git_delivery`, `assurance_publication`. 이 다섯은 전부 ServiceCase의 `target_reference`가 가리키는 **고객 대상 실행 계층**(위 "실행 계층 5개 상태기계" 절, G28이 이미 "고객 대상 실행"으로 명시한 것과 동일 범위)이다. 이름이 비슷해 보이는 `improvement` 머신(`FINDING_CONFIRMED→IMPROVEMENT_PLANNED→...→DELIVERY_READY`)은 OImprovement/ImprovementRequest·PatchPlan·PatchRun, 즉 **고객 대상 프로그램의 코드를 고치는 것**이며, MissedFinding의 `status`(`OPEN→RCA_IN_PROGRESS→CANDIDATE_SUBMITTED`)나 그 뒤를 잇는 `learning-to-application-pipeline.v1.json`의 상태(`LEARNING_CANDIDATE→...→APPLIED_LOCKED`)와는 어휘·대상이 전혀 겹치지 않는 별개 개념이다 — 이름의 유사성으로 혼동하지 않는다.

`learning-to-application-pipeline.v1.json` 자체도 `state-model-mapping.v1.json`의 5개 머신 중 어디에도 등록돼 있지 않다는 것을 실제 파일 대조로 확인했다. 이는 누락이 아니라 범위 밖이다: 이 파이프라인은 ONSure가 **자기 자신의** 탐지 역량(Rule/Pattern/RAG/Prompt/Model)을 승격하는 경로(`VALIDATION_PACK_APPLY`/`ONSURE_RUNTIME_CODE_APPLY`)이고, 대상 프로그램에 적용하는 `TARGET_PRODUCT_APPLY`는 ONSure 자신이 `APPLIED_LOCKED`를 최소 1건 증명하기 전까지 MVP에서 하드 게이트로 불허된다([00_ONSURE_MASTER_DESIGN_SET.md](00_ONSURE_MASTER_DESIGN_SET.md) §2-2, [02 §7-2 OTraining](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md)). 즉 이 파이프라인(그리고 그 앞단인 MissedFinding)은 ONSure 자신의 내부 역량개선 루프이지 ServiceCase 실행 상태가 아니다 — G28이 `contracts/state-machine.v1.json`(ONSure 자신의 내부 개발/퍼블리시 파이프라인, UNINITIALIZED→...→PUBLICATION_ELIGIBLE)에 대해 이미 정리한 것과 정확히 같은 범주 분리다.

**결론(G4 확정)**: MissedFinding의 `status`와 그 뒤를 잇는 `promoted_candidate_id`/`learning-to-application-pipeline`은 `state-model-mapping.v1.json`에 **등록 대상이 아니다** — 등록 범위(고객 대상 실행)를 벗어나기 때문이다. 향후 MissedFinding이 실제 계약(가칭 `missed-finding.v1.schema.json`, 아직 미제정)으로 제정되더라도 `state-model-mapping.v1.json`의 `machines`에 6번째 항목으로 추가하지 않는다. 대신:
1. MissedFinding 계약 자체가 (본 절처럼) `promoted_candidate_id`를 통해 `learning-to-application-pipeline.v1.json`의 `candidate_source_receipt_sha256`와 연결됨을 명시하는 것으로 다리는 이미 확보된다 — 별도 매핑 계약이 필요 없다.
2. `state-model-mapping.v1.json`의 `legacy_mapping` 절도 대상이 아니다 — 그 절은 구 모델(레거시 명명)을 신 레지스트리로 번역하는 용도이지, 범위 밖 하위시스템을 참조하는 용도가 아니다.
3. 만약 향후 "ONSure 자기학습/승격 계층"(MissedFinding·learning-to-application-pipeline·learning-validation-engine·failure/improvement/reusable-pattern-memory)에 대해서도 `state-model-mapping.v1.json`과 같은 역할의 교차정합성 레지스트리가 필요해지면(현재는 없음 — 이 계약들의 상태 어휘도 서로 아직 레지스트리화되어 있지 않다), 그것은 `state-model-mapping.v1.json` 자체를 확장하는 것이 아니라 별도 신규 계약(예: 가칭 `learning-model-mapping.v1.json`)으로 만들어야 한다 — 고객 대상 실행과 ONSure 내부 역량개선을 같은 레지스트리에 섞지 않는다는 원칙(G28)을 유지하기 위함이다.

### 아직 계약이 없는 이 설계서의 확장 (DESIGN_ONLY)
다음은 이번 세션에서 제안했으나 대응하는 `contracts/*.schema.json`을 찾지 못했다. 아이디어 자체를 폐기하라는 뜻이 아니라, 구현 전 계약부터 만들어야 한다는 뜻이다. (2026-08-09 갱신: CaseRevision·ComponentContract/Cross-Program Impact Scan·ProgramRiskScore는 G2/G3/G7 작업으로 실제 계약이 생겨 목록에서 제외했다. BlastRadiusReport는 PatchPlan.preapply_assessment로 부분 흡수된 것으로 이미 정리됨.)

**MissedFinding**(위 절 참조 — 신규 기능정의는 있으나 계약은 아직 없음), RollbackVerificationReceipt, **ConfidenceCalibrationReport**(아래 §OReview 자체 품질 절에 신규 기능정의 추가, 계약은 아직 없음), ReviewerAccuracyScore, AIConfigDriftReport, PeerBenchmark, **AcceptanceCertificate/ExternalAcceptorGrant**(위 절 참조 — G7, 2026-08-11 신규 기능정의 추가, 계약은 아직 없음), **CoverageReport**([02 §4](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md)에 신규 기능정의 추가, 계약은 아직 없음), **NotificationRule/NotificationEvent/NotificationDeliveryReceipt**(위 절 참조 — G7, 2026-08-11 신규 기능정의 추가, 계약은 아직 없음), **PolicyPack/PolicyPackVersion**(위 절 참조 — G7, 2026-08-11 신규 기능정의 추가, 계약은 아직 없음. `dependency-license-policy.v1.json`이 ONSure 자체 빌드용으로 같은 모양의 라이선스 규칙 계약을 이미 갖고 있어 향후 계약 제정 시 선례로 재사용 가능), ReproducibilityAuditSample, **SBOM**(대상 Program용 — 위 절 참조. G7, 2026-08-11 신규 기능정의 추가, 계약은 아직 없음. ONSure 자체 내부 빌드 SBOM은 `SbomGenerator.java`로 이미 구현되어 있으나 범위가 다름), TrainingRequest/Plan/Run, ModelVersion/RAGIndexVersion/PromptVersion/AgentPolicyVersion, DeploymentApproval/ProductionObservation/RelearnTrigger

### TrainingRequest / TrainingRun (DESIGN_ONLY)
TrainingRequest: DRAFT → SCOPED → APPROVED → TRAINING_IN_PROGRESS → EVALUATION_PENDING → (INDEPENDENTLY_VERIFIED → DEPLOYMENT_APPROVED → DEPLOYED) 또는 (EVALUATION_FAILED → SCOPED로 재진입)
예외: REJECTED, ABANDONED

TrainingRun: PENDING → DATA_QUALITY_CHECK → RUNNING → EVALUATION_RUNNING → (EVALUATION_PASSED 또는 EVALUATION_FAILED)
예외: FAILED, ABORTED

DEPLOYMENT_APPROVED는 Training을 수행한 모델·Provider와 다른 계열의 독립 재검증([03 §10-1](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) Cross-Model Verification 재사용)을 통과해야만 도달한다(자기 참조 승인 금지). DEPLOYED 이후 ProductionObservation에서 임계치를 넘는 성능저하가 확인되면 RelearnTrigger가 새 TrainingRequest 초안을 제안하며, 이는 [OMemory](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) MissedFinding과 동일하게 "제안일 뿐 자동 실행 아님" 원칙을 따른다.

### CreditReservation — `contracts/license-state.v1.schema.json`의 `reservations` 필드
RESERVED → COMMITTED 또는 RELEASED 또는 EXPIRED(계약상 4개 상태이며, 이 설계서가 이전에 "Timeout 시 자동 RELEASED"라 쓴 것과 달리 EXPIRED는 RELEASED와 별개 상태로 구분된다 — 정정)

License 자체는 `contracts/license-state.v1.schema.json`에 이미 실재한다: `status`(ISSUED/ACTIVE/SUSPENDED/REVOKED/EXPIRED), `offline_grace_hours`, `clock_tolerance_seconds`, `credits{total,available,reserved,committed}`가 필드로 존재해 이 설계서의 Offline 정책(§11)·Credit 개념과 상당히 부합한다. 실행 도중 Credit이 소진되면 진행 중인 `validation_run`은 안전한 Checkpoint까지만 진행한 뒤 위 5개 실행 상태기계의 HOLD로 전이하는 것이 계약과 일치하며, ServiceCase 자체를 별도 대기 상태로 두는 것은 계약에 없다(이 설계서의 이전 서술을 정정).

### CaseRevision (DESIGN_ONLY)
Improve & Re-verify를 DELIVERY_ACCEPTED 이후의 새 CaseRevision으로 처리한다는 설계는 유지하되, `service-case-state.v1.schema.json`에 CaseRevision을 위한 필드나 상태가 없으므로 계약 확장이 선행되어야 한다.

### ComponentContract (DESIGN_ONLY)
DRAFT → ACTIVE → SUPERSEDED, 예외 BREAKING_CHANGE_FLAGGED. Cross-Program Impact Scan 아이디어(Provided Interface 변경 시 다른 Program에 Finding 전파)는 유효하나 이를 뒷받침할 `component-contract.v1.schema.json` 계약이 아직 없다.

### AcceptanceCertificate / ExternalAcceptorGrant (신규 기능정의, 2026-08-11 — `DESIGN_ONLY`, G7)
[02 §10](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md)와 [08 체크리스트 B2](08_REVIEW_CHECKLIST_OPEN_DECISIONS.md)가 이미 목적("소스나 Finding 상세 없이도 서명 검증만으로 확인 가능한 요약 증명서")을 정의했고 §7 API 목록에도 `POST /v1/cases/{caseId}/acceptance-certificates`, `POST /v1/cases/{caseId}/external-acceptors`, `GET /v1/certificates/{certId}/verify`가 이미 있다 — 이 절은 그 API가 실제로 다루는 엔티티의 필드 수준 설계다.

**중요한 제약**: `service-case-state.v1.schema.json`의 `final_claim_allowed`는 예외 없는 `const: false`이고(70행), G30에서 확인한 `TARGET_PRODUCT_APPLY: mvp_allowed=false` 하드 게이트와 같은 근거를 공유한다 — 즉 AcceptanceCertificate는 "결함이 없다"를 증명하는 문서가 될 수 없고, "이 시점에 이 정책·범위로 이런 결과를 받았다"만 증명할 수 있다. [08 §C 완전성 보장 메커니즘 절](08_REVIEW_CHECKLIST_OPEN_DECISIONS.md)이 실제로 강제되는 유일한 장치로 지목한 `final_claim_allowed: false`를 이 인증서가 우회하지 않도록, 아래처럼 완전성 부인 문구를 선택이 아닌 필수 필드로 둔다.

**AcceptanceCertificate 필드**:
- `acceptance_certificate_id`
- `case_id`, `case_revision_number`(nullable — `service-case-state.v1.schema.json`의 `case_revisions` 배열 중 `status: ACCEPTED`인 revision을 가리킴, G2 참조. CaseRevision이 없는 최초 Delivery는 top-level `status: DELIVERY_ACCEPTED`를 직접 참조)
- `baseline_reference`: 인증 대상 Baseline digest와 RepositoryBinding 참조
- `policy_binding`: `rule_pack_digest`, `policy_pack_version_id`(nullable — 조직이 표준 Rule Pack만 쓰면 없음, 아래 PolicyPack 참조), 판정 시점에 고정(03 §10 감사 Receipt 결속과 같은 패턴)
- `decision_summary`: `quality_decision`(PASS/FAIL/HOLD — `oreview-result.v1.schema.json` 어휘 재사용, G9), `open_finding_counts_by_severity`(Critical/High/Medium/Low, 발급 시점 `security-findings.v1.schema.json` OPEN 상태 스냅샷), `coverage_percent`(nullable — [02 §4 CoverageReport](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) 계약 제정 전까지 채울 수 없음), `program_risk_score`/`program_risk_grade`(발급 시점 `contracts/program-risk-score.v1.schema.json` 스냅샷 — G32로 실제 계약이 생겼으므로 이 필드는 채울 수 있음)
- `completeness_disclaimer`: 고정 상수 텍스트/코드("이 인증서는 명시된 정책·범위 내 판정이며 결함 부재를 증명하지 않는다. `final_claim_allowed`는 항상 false다") — 생략 불가 필수 필드, `final_claim_allowed: const false`와 같은 방식으로 계약화되어야 함
- `issued_at`, `issued_by`(시스템 발급, Evidence로 봉인)
- `external_acceptor_grants`: 아래 ExternalAcceptorGrant 배열(nullable/빈 배열 허용)
- `signature`: `key_id`, `signature`, `signed_at`(§10 License Token의 `key_id`/`signature` 패턴 재사용)
- `status`: `ISSUED` → `REVOKED`(예외 없음). Revoke는 사후 Case 내용 변조 발견 또는 Legal Hold 충돌 시에만 — `AcceptanceCertificateRevoked`(§8 Event, 이미 목록에 있음)와 결속
- `revoked_at`, `revoked_reason`(nullable, `REVOKED`가 아니면 null)

**ExternalAcceptorGrant 필드**: `grant_id`, `case_id`, `acceptor_identity`(이메일, ONSure 유료 계정 불필요 — [02 Actor 표](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) External Acceptor 정의와 일치), `scope`(`VIEW_CERTIFICATE_VERIFICATION_ONLY` — 공개 엔드포인트라 이 Grant 자체가 불필요 / `VIEW_DELIVERY_READONLY` — Delivery 읽기 전용 열람, 이 Grant 필수), `granted_by`(Customer Owner만 가능 — 02 Actor 표), `granted_at`, `expires_at`(nullable), `revoked_at`. 상태: `ACTIVE` → `EXPIRED` 또는 `REVOKED`.

**선행조건**: ServiceCase가 `DELIVERY_ACCEPTED`(또는 CaseRevision `ACCEPTED`)에 도달해야 발급 가능. `coverage_percent`는 CoverageReport 계약 제정 전까지 비워둘 수 있으므로 AcceptanceCertificate MVP는 이 필드 없이도 발급 가능하게 설계했다(하드 의존 아님). B2(법적 효력 문구)는 이 필드 설계로 해결되지 않으며 여전히 별도 법무 확인이 필요하다.

### PolicyPack / PolicyPackVersion (신규 기능정의, 2026-08-11 — `DESIGN_ONLY`, G7)
[Policy Management 절](#3-핵심-서비스)이 이미 원칙(Additive-only, Critical 규칙 비활성화 불가, Digest 고정, Golden Fixture 회귀 필수)을 정의했다 — 이 절은 필드 수준 설계를 추가한다.

**PolicyPack(부모) 필드**: `policy_pack_id`, `organization_id`, `name`, `description`, `created_by`, `created_at`, `status`(`DRAFT` → `ACTIVE` → `DEPRECATED` — 최소 하나의 ACTIVE 버전이 있는지 여부를 반영)

**PolicyPackVersion(자식, 버전관리 단위) 필드**: `policy_pack_version_id`, `policy_pack_id`, `version_number`(단조증가), `digest`(sha256 — 03 §10 감사 Receipt 결속에 이미 쓰는 값), `rules`(배열: `rule_id`, `category`[CODING/ARCHITECTURE/SECURITY/LICENSE], `severity`, `condition_reference`, `additive_only: true` — 표준 Rule Pack의 Critical 규칙 비활성화가 불가능함을 각 rule 레코드 수준에서도 강제), `regulatory_framework_mappings`(배열: `framework_name`[NIST/ISO/OWASP/MITRE/금융MRM], `framework_version`, `mapped_rule_ids` — [08 체크리스트 E1](08_REVIEW_CHECKLIST_OPEN_DECISIONS.md)이 원칙만 기술했던 부분의 필드화, nullable), `golden_fixture_regression_receipt_reference`(OMemory의 기존 Golden Review Fixture 회귀 절차 결과 링크, 신규 회귀 메커니즘을 만들지 않고 재사용), `status`(`DRAFT` → `PENDING_REGRESSION` → `REGRESSION_PASSED` → `ACTIVE` → `SUPERSEDED`, 예외 `REGRESSION_FAILED`→`DRAFT` 재진입 — G3의 ComponentContract가 이미 쓰는 DRAFT→ACTIVE→SUPERSEDED 어휘를 그대로 재사용해 새 상태 어휘를 만들지 않았다), `activated_at`, `superseded_by_version_id`(nullable)

**기존 실재 계약과의 관계(정직한 확인)**: `contracts/dependency-license-policy.v1.json`이 이미 ONSure **자신의** 빌드 의존성에 대해 정확히 같은 모양의 개념(SPDX id → `ALLOWED`/`FORBIDDEN` 결정 + 사유, `default_decision_for_unlisted_license: FORBIDDEN`)을 실제 계약으로 갖고 있다 — 이는 고객이 자체 Organization에 업로드하는 PolicyPack이 아니라 ONSure 내부 빌드용이라 별개 엔티티이지만, B5(Copyleft 차단 정책)가 요구하는 License 허용/차단 목록의 실제 스키마 모양은 이미 이 계약에 검증된 형태로 존재한다 — 향후 `policy-pack.v1.schema.json`을 제정할 때 라이선스 규칙 부분은 이 계약을 참고 선례로 재사용해야 하며 새로 발명할 필요가 없다.

### NotificationRule / NotificationEvent / NotificationDeliveryReceipt (신규 기능정의, 2026-08-11 — `DESIGN_ONLY`, G7)
[02 §10-1 ONotify](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md)가 이미 책임·기능·수용기준(Critical 5분 이내, Opt-in 채널만, Webhook 서명·재시도·Dead Letter, 발송 자체도 Evidence화)을 정의했으나 "산출물: NotificationRule, NotificationEvent, NotificationDeliveryReceipt" 세 이름만 나열했을 뿐 필드가 없었다 — 이 절이 그 필드를 채운다.

**NotificationRule 필드**: `notification_rule_id`, `organization_id`, `user_id`(nullable — 조직 기본값 vs 사용자별 override), `subscribed_event_types`(배열, 02 §10-1이 이미 정의한 8종 고정: `CaseBlocked`/`CriticalFindingOpened`/`VerificationFailed`/`LicenseExpiringSoon`/`LicenseSuspended`/`CreditLow`/`PatchRegressionFailed`/`DeletionCompleted`), `channel`(`EMAIL`/`WEBHOOK`/`VSCODE`/`ADMIN_INBOX`, Opt-in만 — 수용기준 그대로), `batching`(`IMMEDIATE`[Critical] 또는 `DAILY_DIGEST`[Medium 이하] — 02 §10-1 그대로), `webhook_endpoint_url`, `webhook_secret_reference`(nullable, WEBHOOK 채널일 때만), `created_by`, `created_at`, `updated_at`, `status`(`ACTIVE`/`DISABLED`)

**NotificationEvent 필드**(발생 사실의 append-only 기록): `notification_event_id`, `event_type`(같은 8종 enum), `source_reference`(case_id/finding_id/license_id 등 트리거 원본), `occurred_at`, `severity`

**NotificationDeliveryReceipt 필드**(발송 자체의 Evidence — 02 §10-1 "발송 자체도 Evidence로 기록"의 필드화): `delivery_receipt_id`, `notification_event_id`, `notification_rule_id`, `channel`, `attempted_at`, `delivered_at`(nullable), `delivery_status`(`PENDING` → `DELIVERED` 또는 `FAILED` → `RETRYING` → `DELIVERED` 또는 `DEAD_LETTERED` — 02 §10-1의 "실패 시 Dead Letter로 격리"를 상태기계로 구체화), `retry_count`, `fallback_triggered`(boolean — Webhook 반복 실패로 관리자 알림함 Fallback 발동 여부, 수용기준 그대로), `evidence_reference`

**Lifecycle 요약**: NotificationEvent는 Evidence와 동일하게 불변 append-only이며 별도 상태전이가 없다. 실제 상태기계는 NotificationDeliveryReceipt에만 있다(발송 채널 하나당 하나).

### SBOM — 대상 Program용 (신규 기능정의, 2026-08-11 — `DESIGN_ONLY`, G7, ONSure 자체 내부 SBOM과는 범위가 다름)
§7 API 목록에 이미 `GET /v1/program-profiles/{id}/sbom`이 있고 [02 §10](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) ODelivery 산출물에도 "SBOM(CycloneDX/SPDX 포맷)"이 있다 — 이 절은 그 엔티티의 필드 설계다.

**정직한 확인(코드까지 실제로 읽음)**: ONSure는 이미 실제로 동작하는 SBOM 생성 코드를 갖고 있다 — `src/main/java/kr/co/oruda/onsure/platform/SbomGenerator.java`(+ `SbomGeneratorTest.java`, 4개 테스트 모두 통과)가 `pom.xml`의 직접 의존성을 `contracts/approved-dependency-manifest.v1.json`의 License로 보강해 CycloneDX 1.5 JSON을 생성하고, 이 출력이 `contracts/assurance-lanes.v1.json`의 `ORUDA_BUILD` Lane `required_outputs`(125행, `"sbom"` 포함)를 충족한다. **하지만 이것은 ONSure 자신의 Maven 빌드 공급망 자기증명이지, 고객의 임의 대상 Program(npm/pip/cargo/go/gradle 등 다양한 생태계 가능)을 분석하는 이 기능과는 범위가 다르다** — 같은 이름(SBOM)의 서로 다른 두 엔티티를 혼동하면 안 된다. 재사용 가능한 것은 출력 포맷(CycloneDX 1.5)과 "Manifest에서 License를 조회해 보강" 패턴뿐이며, 임의 생태계의 의존성 트리를 추출하는 파서 계층은 아직 없다. 코드 자체의 주석도 "직접 의존성만 다루고 전이 의존성은 다루지 않는다"고 명시한다 — 이 한계를 고객向 설계에도 정직하게 반영한다.

**필드**:
- `sbom_id`
- `program_profile_id`(§5 `program_profile` 상태기계 참조, 최소 `PROFILE_ACTIVE` 도달 후 생성 가능)
- `baseline_reference`: 생성 시점 분석 대상 Source digest
- `format`: `CYCLONEDX_1_5`(ONSure 자체 생성기가 이미 검증한 포맷 재사용 — SPDX는 02가 대안으로 언급했으나 주 포맷 결정은 아직 안 됨, `DRAFT`)
- `ecosystems`: 감지된 패키지 매니저 배열(`MAVEN`/`NPM`/`PIP`/`CARGO`/`GO_MODULES`/`GRADLE`/`OTHER` — 하나의 Program이 모노레포로 여러 개일 수 있음)
- `components`: 배열, 각 항목 `type`, `group`, `name`, `version`, `scope`(required/optional), `purl`, `licenses`(배열), `transitive`(boolean)
- `transitive_resolution_status`: `DIRECT_ONLY` 또는 `FULL_TREE` — ONSure 자체 생성기가 현재 `DIRECT_ONLY`이므로, 고객向 MVP도 같은 제약으로 출발할지 이번 라운드에서 결정하지 않고 필드만 남긴다(`DRAFT`)
- `generated_at`
- `license_source`: License 보강에 쓰인 근거(고객 조직의 PolicyPack License 허용/차단 목록 — 위 PolicyPack 절 참조. 이 필드는 PolicyPack이 아직 계약이 없으므로 함께 `DESIGN_ONLY`)

**Lifecycle**: 별도 상태전이 없음 — CoverageReport와 마찬가지로 특정 실행 시점의 불변 스냅샷 산출물이다.

## 6. API 원칙
- REST와 Event를 병행한다.
- 모든 Write API는 Idempotency-Key를 지원한다.
- Organization과 License Context를 명시한다.
- 낙관적 잠금과 Version을 사용한다.
- 오류는 machine-readable code와 retryable 여부를 포함한다.
- PII와 Secret을 오류 본문에 넣지 않는다.
- 인증: Web/Admin Console은 Session Cookie + CSRF Token, VS Code Extension은 OAuth2 Authorization Code + PKCE로 발급한 단기 Access Token과 Refresh Token, 외부 시스템 M2M 연동은 OAuth2 Client Credentials를 기본으로 하며 Enterprise에 한해 IP Allowlist가 결속된 장기 API Key를 허용한다
- Access Token 수명은 1시간 이내로 하고 Refresh Token 회전(Rotation)을 적용한다
- 객체 수준 권한 검사(신규, OWASP API Security Top 10 API1 대조로 2026-08-09 발견): `GET /v1/cases/{caseId}` 같은 객체 ID 기반 API는 호출자가 인증됐다는 것만으로 부족하며, 매 요청마다 해당 caseId/programId 등 구체적 객체가 호출자의 Organization/Tenant Context에 실제로 속하는지 검사해야 한다 — 인증(누구인지)과 객체 수준 인가(이 특정 객체에 접근 가능한지)를 분리한다
- 민감 업무 흐름 보호(신규, API6 대조로 2026-08-09 발견): `POST /v1/orders`, `POST /v1/cases/{caseId}/approve-scope` 등 금전·승인이 걸린 흐름은 일반 Rate Limit(NFR-AVAIL)과 별개로 남용 탐지(동일 Actor의 비정상적으로 빈번한 반복 호출)를 적용한다

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
- NotificationSent, NotificationFailed, NotificationSuppressed(Opt-in 없음)
- MutationTestCompleted, BlastRadiusComputed, BehaviorDiffCompleted, SBOMGenerated
- CrossModelVerificationRequested, CrossModelVerificationDisagreed, SelfClaimMismatchDetected
- ComponentContractBreakingChange, CrossProgramImpactDetected
- RollbackVerified, RollbackVerificationFailed
- ConfidenceCalibrationDrifted, ReviewerAccuracyBelowThreshold, AIConfigDriftDetected
- AcceptanceCertificateIssued, AcceptanceCertificateRevoked, ExternalAcceptorGranted
- TrainingRequested, TrainingRunCompleted, EvaluationFailed, DeploymentApproved, RelearnTriggered

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
- channel=WEB_CASE|VSCODE|API (`docs/v2/05` §3과 일치)
- service_type=LEARN|VERIFY|LEARN_VERIFY|IMPROVE_REVERIFY|TRAIN_REVERIFY (`docs/v2/05` §3 값에 TRAIN_REVERIFY 추가)
- plan=DEVELOPER|TEAM|ENTERPRISE (`docs/v2/05` §3과 일치)
- feature_entitlements
- system_limit, program_limit, program_unit_limit
- learning_unit_limit, credit_balance 또는 credit_policy
- dataset_limit, training_run_limit, model_version_limit (`docs/v2/00` §3 "Dataset·Training·Model Version 한도"에서 흡수, OTraining 전용, DESIGN_ONLY)
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
- 신뢰 경계 원칙(`docs/v2/07` §10에서 흡수): 고객 Repository의 자체 결과를 최종 판정으로 신뢰하지 않는다. 개선을 생성한 Model과 최종 Reviewer/Oracle을 분리한다(§10-1 Cross-Model Verification과 동일 근거). 클라이언트(VS Code/Web)의 Feature 표시를 권한 증거로 사용하지 않으며 실행마다 서버가 Entitlement를 재확인한다. 결제 성공 Event만으로 실행을 허용하지 않고 OLicense의 License ACTIVE 상태를 별도로 확인한다

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