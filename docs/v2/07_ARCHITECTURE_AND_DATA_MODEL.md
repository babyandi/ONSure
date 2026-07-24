# ONSure V2 아키텍처와 데이터 모델

## 1. 논리 아키텍처

```text
ONSure Web / VS Code / CLI
            ↓
      ONSure API Gateway
            ↓
Case·Subscription Orchestrator
            ↓
Learning | Verification | Improvement | Evidence | Git
            ↓
Sandbox·Model Provider·Repository·CI

OLicense
- Catalog·Order·Contract
- License·Entitlement
- Credit·Usage·Audit
```

## 2. 주요 컴포넌트

- Identity/Organization: 고객·조직·역할
- Intake: Upload·Repository·Runtime 접수
- Scope Estimator: System·Program·Learning Unit 산정
- Case Manager: 웹 Case 수명주기
- Subscription Manager: VS Code Binding·활성화
- Learning Engine: Program/Behavior Profile
- Verification Engine: Scenario·Run·Finding·RCA
- Improvement Engine: Plan·Patch·Rollback·Comparison
- Evidence Store: 변경 불가 증적·보고서
- Git Engine: Worktree·Branch·Commit·PR
- Billing Adapter: Payment 연계
- OLicense Adapter: Entitlement·Credit·Token
- Notification/Support: 상태·승인·지원

## 3. 핵심 엔터티

```text
Organization
├─ User / Seat / Device
├─ System
│  └─ Program
│     ├─ Repository
│     ├─ Runtime
│     └─ Baseline
├─ ServiceCase
├─ Subscription
├─ LicenseSnapshot
└─ UsageAccount

Program
├─ ProgramProfile
├─ BehaviorProfile
├─ VerificationPlan
├─ Run
├─ Finding
├─ Improvement
└─ EvidenceBundle
```

## 4. ServiceCase

필드:

- CaseId, ServiceType, Status
- OrganizationId, SystemId, ProgramIds
- BaselineDigest
- LearningUnitEstimated/Allowed/Used
- VerificationPacks
- ImprovementUnitAllowed/Used
- ReverificationAllowed/Used
- Quote·Order·LicenseId
- Dates·Pause·Completion
- Deliverables·Evidence Digest

## 5. Subscription

- SubscriptionId·Plan·Status
- Seat/Device Assignments
- Active System·Program Binding
- Credit Period·Balance·OveragePolicy
- Feature Entitlements
- Offline Snapshot
- Renewal·Cancellation

## 6. 실행 상태

```text
Run: QUEUED → AUTHORIZED → RESERVED → RUNNING → SUCCEEDED/FAILED/HOLD → SETTLED
Task: PLANNED → APPROVAL_REQUIRED → READY → RUNNING → DONE/RETRY/BLOCKED
Finding: CANDIDATE → REPRODUCED → CONFIRMED → ACCEPTED/RISK_ACCEPTED → FIXED → REGRESSION_LOCKED
Improvement: PROPOSED → APPROVED → APPLIED → VERIFIED → PROVEN/INEFFECTIVE/REGRESSED → ROLLED_BACK
```

## 7. OLicense 연계 API

- ResolveCatalog
- CreateOrderReference
- ValidateLicense
- GetEntitlements
- Activate/Deactivate Seat·Device·System·Program
- Reserve/Commit/Release Credit
- ReportUsage
- Complete/Cancel Case
- Refresh Online Token
- Verify Offline Snapshot

모든 변경 API는 Idempotency Key와 Version을 사용한다.

## 8. 데이터 격리

- Organization별 논리·물리 격리 선택
- 고객 Source와 Artifact 암호화
- Secret 별도 Vault
- Model Provider 전송정책 적용
- 프로젝트 Memory와 범용 익명 Pattern 분리
- Evidence 보존·삭제 정책
- 관리자 Cross-tenant 접근 Audit

## 9. 배포모델

- SaaS Shared Control Plane + Isolated Worker
- Enterprise Dedicated Tenant
- On-premise/Offline Runtime
- VS Code Local Runtime + Cloud Control Plane

OLicense 연결은 Online API 또는 서명된 Offline Snapshot으로 제공한다.

## 10. 신뢰 경계

- 고객 Repository의 결과를 최종 판정으로 신뢰하지 않는다.
- 개선을 생성한 Model과 최종 Reviewer/Oracle을 분리한다.
- ONSure Client의 Feature 표시를 권한 증거로 사용하지 않는다.
- 결제 성공 Event만으로 실행을 허용하지 않고 OLicense ACTIVE Entitlement를 확인한다.
- Evidence와 License·Usage를 Run/Commit SHA에 결속한다.
