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

### Commerce and Case
상품 선택, Preflight, Quote, Order, Payment 상태, Service Case를 관리한다.

### Orchestration
Execution Plan을 DAG로 실행하며 Pause, Resume, Cancel, Retry, Compensation을 제공한다.

### Sandbox
고객별 격리 실행, CPU/Memory/Time/Network Policy, Secret Injection, Artifact Export를 관리한다.

### Evidence
Append-only Metadata와 Content-addressed Artifact를 관리한다.

## 4. 주요 데이터 엔터티
- Organization
- User, Role, Membership
- ProductCatalog, Plan, Feature
- Order, Payment, Refund
- License, Entitlement, Subscription
- CreditAccount, CreditReservation, UsageEvent
- System, Program, RepositoryBinding
- Baseline, ArtifactManifest
- ServiceCase, CaseScope, CaseRevision
- ProgramProfile, Component, Dependency, AIComponent
- Requirement, Policy, TraceLink
- ReviewRun, ReviewFinding, ReviewDecision
- VerificationRun, TestClaim, VerificationFinding
- ImprovementRequest, PatchPlan, PatchRun
- Evidence, Receipt, DeliveryPackage
- AuditEvent, DeletionJob, DeletionReceipt

## 5. 상태 모델
### ServiceCase
DRAFT → PREFLIGHT → QUOTED → PAYMENT_PENDING → LICENSE_PENDING → READY → LEARNING/VERIFYING → REVIEW_REQUIRED → IMPROVEMENT_OPTIONAL → DELIVERING → COMPLETED

예외: BLOCKED, SUSPENDED, CANCELLED, EXPIRED, REFUNDED

### ReviewFinding
OPEN → ACKNOWLEDGED → FIX_PLANNED → FIXED → RE_REVIEWED → CLOSED
예외: ACCEPTED_RISK, FALSE_POSITIVE, DUPLICATE, WONT_FIX

### CreditReservation
RESERVED → COMMITTED 또는 RELEASED; Timeout 시 자동 RELEASED

## 6. API 원칙
- REST와 Event를 병행한다.
- 모든 Write API는 Idempotency-Key를 지원한다.
- Organization과 License Context를 명시한다.
- 낙관적 잠금과 Version을 사용한다.
- 오류는 machine-readable code와 retryable 여부를 포함한다.
- PII와 Secret을 오류 본문에 넣지 않는다.

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

### Learning and Review
POST /v1/learning-runs
GET /v1/program-profiles/{id}
POST /v1/review-runs
GET /v1/review-runs/{id}/findings
POST /v1/findings/{id}/decisions

### Verification and Improvement
POST /v1/verification-runs
POST /v1/improvement-requests
POST /v1/patch-runs
POST /v1/patch-runs/{id}/approve
POST /v1/patch-runs/{id}/reverify

### License
POST /v1/license/validate
POST /v1/license/activate
POST /v1/license/deactivate
POST /v1/license/credit/reserve
POST /v1/license/credit/commit
POST /v1/license/credit/release
GET /v1/license/entitlements
GET /v1/license/jwks

## 8. Event 계약
- PaymentSucceeded, PaymentFailed, RefundCompleted
- LicenseIssued, LicenseSuspended, LicenseRevoked, EntitlementChanged
- CreditReserved, CreditCommitted, CreditReleased
- CaseReady, CaseStarted, CaseBlocked, CaseCompleted
- BaselineChanged
- ReviewCompleted, VerificationCompleted, PatchCompleted
- EvidenceSealed, DeliveryPublished, DeletionCompleted

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
- 결제 카드정보 비보관
- Tenant Key 또는 Enterprise 전용 Key 옵션

## 13. 보존과 삭제
계약별 Retention 종료 후 Source, Build Artifact, Log, Profile, Evidence를 유형별 정책으로 삭제한다. Legal Hold가 없으면 삭제 작업과 결과 Hash를 Deletion Receipt로 남긴다.