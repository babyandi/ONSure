# ONSure 기능 요구사항 및 프로그램 명세

## 1. Actor
실제 `contracts/tenant-context.v1.schema.json`은 5개 RBAC 역할(VIEWER, OPERATOR, APPROVER, AUDITOR, ADMIN)만 정의한다. 아래 업무 Actor는 그보다 세분화된 설계 개념이며, 계약의 5개 역할 중 하나로 매핑되어야 접근제어가 실제로 동작한다. 매핑은 아직 계약화되지 않은 `DESIGN_ONLY` 제안이다.

| 업무 Actor | 책임 | RBAC 매핑(제안) |
|---|---|---|
| Customer Owner | 계약, 결제, 범위 승인, 최종 인수 | ADMIN |
| Customer Admin | 조직, 사용자, 시스템, 정책 관리 | ADMIN |
| Developer | VS Code에서 학습·리뷰·검증·개선 수행 | OPERATOR |
| Reviewer | Finding과 Patch 승인 또는 반려 | APPROVER |
| Professional Reviewer | 유료 전문가 검토 | APPROVER |
| ONSure Operator | Case와 실행환경 운영 | OPERATOR |
| Security Auditor | Evidence와 감사로그 열람 | AUDITOR |
| External Acceptor | 고객 소프트웨어를 인수·검수하는 발주기관 등 제3자. Customer Owner가 초대한 범위에서 Delivery와 Acceptance Certificate만 읽기 전용으로 열람하며 ONSure 유료 계정이 없어도 됨 | VIEWER |
| Compliance Officer | 규제산업 Enterprise에서 정책·규제 프레임워크 버전 관리와 최종 승인을 담당(Reviewer와 겸직 불가) | APPROVER |
| OLicense | 라이선스·Entitlement·Credit 권위 | 시스템 주체(RBAC 대상 아님) |
| Payment Provider | 결제 승인·취소·환불 이벤트 제공 | 시스템 주체(RBAC 대상 아님) |

## 2. 공통 기능 요구사항
- FR-COM-001 모든 실행은 Organization, Product, Channel, License, System, Program, Baseline에 결속한다.
- FR-COM-002 고객 데이터는 Tenant별 논리·물리 격리 정책을 적용한다.
- FR-COM-003 실행 전 Entitlement, Credit, Feature, Validity를 확인한다.
- FR-COM-004 모든 결과는 정책 버전, 입력 Hash, 실행환경, 도구 버전, 결과 Hash를 기록한다.
- FR-COM-005 동일 입력·정책·도구 버전은 재현 가능한 판정 구조를 가져야 한다.
- FR-COM-006 ONSure 내부 오류에 의한 실패는 고객 사용량으로 확정하지 않는다.
- FR-COM-007 모든 자동 Patch는 별도 Worktree와 Branch에서 수행한다.
- FR-COM-008 고객 승인 전 Main Branch 직접 변경을 금지한다. 실제 `contracts/main-branch-protection.v1.json`이 이를 강제하는 구체 설정이다: PR 필수, 최소 승인 1명, 오래된 승인 자동 무효화, 대화 스레드 전부 해결 필수, 직접 Push·Force Push·Branch 삭제 차단, 관리자도 예외 없이 적용, Merge 전 독립 Status Check 필수.
- FR-COM-009 Organization은 자신의 Pattern/Fixture가 익명화된 공유 Corpus에 기여할지 여부를 명시적으로 선택(Opt-in/Opt-out)하며 기본값은 Opt-out이다. 규제산업 Enterprise Edition은 공유 Corpus 기여를 계약으로 원천 차단할 수 있다. 단, 공개 CVE/취약점 DB 등 이미 공개된 정보에서 유래한 Pattern은 고객 코드 관찰과 무관하므로 이 Opt-out 대상에서 제외하고 항상 최신으로 유지한다.
- FR-COM-010 Customer Admin은 Organization에 속한 모든 System/Program의 상태·위험·사용량을 통합한 Portfolio 조회 기능을 제공받는다.
- FR-COM-011 Case/Finding/License의 중요 상태 변화는 채널(Email, Webhook, VS Code, 관리자 알림함)로 능동 통지되어야 하며, 고객이 Dashboard를 확인하지 않아도 인지할 수 있어야 한다.
- FR-COM-012 Seat는 담당자 변경 시 Customer Admin이 즉시 회수·재배정할 수 있으며, 회수된 Seat의 이전 담당자 Access Token은 즉시 무효화한다.
- FR-COM-013 규제산업 Enterprise Edition은 직무분리(SoD)를 강제한다: 동일 사용자가 같은 ImprovementRequest의 개발(Patch 작성)과 검증(Re-verify 승인)과 최종 인수(Delivery 승인)를 모두 수행할 수 없다. 일반 Plan은 권고만 하고 강제하지 않는다.

## 3. OLearning
### 책임
Repository와 관련 자료를 수집·정규화하고 Program Profile을 생성한다.

### 기능
- Repository, Archive, Container Manifest 입력
- 언어·Framework·Build System 탐지
- Module·Service·Deployment Unit 식별
- API·Event·DB·External Dependency 분석
- Prompt·Agent·Tool·RAG 구성 식별
- Test·Policy·Document 연결
- Dynamic Trace 선택 수집
- Unknown, Conflict, Missing Evidence 표시
- 증분 학습과 Profile Revision 관리
- AI 구성 Drift 탐지: 이전 Baseline의 AIProfile 대비 Prompt/Agent/Tool 권한·RAG 구성 변화를 비교하고, 특히 권한 확대·신규 외부 연동처럼 위험이 커지는 변화는 별도 표시

### 산출물
ProgramProfile, ComponentGraph, AIProfile, DependencyInventory, DataFlow, BaselineManifest, LearningReceipt

### 수용기준
- 모든 Profile 요소가 원본 위치로 역추적 가능
- 추론 정보와 확인 정보 구분
- 미확인 항목을 사실처럼 확정하지 않음
- 동일 Baseline 재학습 시 비결정적 차이를 설명 가능

## 4. OPlanning
### 책임
검토·검증·개선 계획을 위험과 계약 범위에 맞게 수립한다.

### 기능
- 계약 Scope와 Program Profile 결합
- 위험 기반 우선순위
- Review Pack과 Verification Pack 선택
- 시나리오·Fixture·환경 요구사항 생성
- Verification Scenario용 테스트 데이터는 프로덕션 데이터 직접 사용을 금지하고 Masking 또는 Synthetic 생성을 원칙으로 하며, 고객이 명시 승인한 범위에서만 마스킹된 샘플을 제한적으로 허용
- 예상 Learning Unit, Credit, 시간 계산
- 실행 의존성 및 Stop Condition 정의
- 사용자 승인용 Plan Diff 제공
- 위험 기반으로 범위를 좁힐 때 무엇을 이번 Review/Verification 대상에서 제외했는지와 제외 사유를 명시적으로 기록(Coverage Report)

### 산출물
ExecutionPlan, ScopeManifest, ScenarioPlan, ResourceEstimate, ApprovalReceipt, CoverageReport

### 수용기준
- CoverageReport 없이 "전체 검토 완료"로 표현하지 않는다. 제외된 Component가 있으면 Case Dashboard와 Delivery에 항상 노출한다

### CoverageReport 상세 기능정의(2026-08-09 — 계약은 아직 없음, `DESIGN_ONLY`)
CoverageReport는 무엇을 안/못 봤는지와 왜를 항상 같이 공개한다.

- `coverage_report_id`
- `run_reference`
- `scope_source`
- `included`: component_id, 적용 Review/Verification 영역, 결과
- `excluded`: component_id, exclusion_reason, excluded_by, excluded_at
- `domain_coverage`: 영역별 RUN/NOT_RUN/PARTIAL
- `coverage_percent`: 반드시 excluded 목록과 함께 표시
- `generated_at`

수용기준: Case Dashboard와 Delivery Report는 `coverage_percent`를 표시하는 화면마다 같은 화면 또는 한 클릭 이내 거리에 `excluded` 목록을 노출해야 한다.

## 5. OReview
### Review 영역
Requirement, Architecture, Design, Policy, Code, AI, Security, Performance, Test, Quality, Merge

### 기능
- 요구사항과 변경파일 Traceability
- 역방향 Traceability
- 설계규칙·Dependency Boundary 검토
- 정책 위반 탐지
- 버그·동시성·예외·자원누수·복잡도 검토
- Prompt Injection, Tool 권한, RAG 출처·오염 검토
- Secret, 취약 Dependency, 인증·인가 검토
- 테스트 누락과 취약 Assertion 검토
- AI 자기주장 검증(Self-Claim Verification)
- PR 단위 Inline Comment, Summary, Decision 생성
- 독립 Review Pass 지원

### Decision
영역별: PASS, FAIL, HOLD, NOT_RUN, NOT_APPLICABLE. 종합(quality_decision): PASS, FAIL, HOLD. `merge_authorized`는 계약상 항상 false다.

### 수용기준
- Finding마다 파일·라인 또는 구성요소·근거·정책·영향·제안 포함
- 중복 Finding 통합
- 추측성 Finding은 Confidence와 확인방법 포함
- Critical/High Finding은 근거 없는 자동 승인 금지
- Critical Finding은 Cross-Model Verification을 보조 corroboration으로 사용하되, Final Truth는 [03](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md)·[07](07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md)의 Ground Truth/독립성 기준을 따른다
- AI 생성 비중이 높은 Component에서 Critical/High Confidence가 임계치 미만이면 Human/Professional Reviewer에게 강제 회부
- Human/Professional Reviewer 판정도 품질관리 대상

## 6. OVerification
### 기능
- Static, Build, Unit, Integration, Scenario, Adversarial, Performance, Recovery, License 검증
- ProgramProfile 또는 ScopeManifest를 구조 정보 입력으로 사용
- 요구사항별 Test Claim 생성
- 실제 실행결과와 Expected 결과 비교
- Mutation Testing
- DAST/Fuzzing
- Negative Test와 Fail-closed 확인
- Regression Set 구성
- 결과 재실행과 Flaky 분리
- 독립 OTester/OAudit 판정 지원

### Decision
PASS, FAIL, BLOCKED, NOT_RUN, INCONCLUSIVE, NON_FINAL

### 수용기준
PASS는 실행 증거 없이 생성할 수 없다. BLOCKED와 NOT_RUN은 FAIL과 분리한다. 실행된 Test/Scenario가 0건이거나 전부 Skip되었거나 도구·환경 오류로 결과를 얻지 못한 경우는 PASS로 표기할 수 없다. 기존 테스트가 전무한 대상은 최소 Smoke/Golden Path Test를 제안·실행하며 PASS 표기를 구분한다.

## 7. OImprovement
### 기능
- 승인된 Finding만 입력
- Root Cause 후보와 영향범위 생성
- Patch Plan과 예상 변경파일 제시
- Blast Radius 드라이런
- Worktree·Branch 생성
- 최소 변경 원칙 Patch
- 관련 Test 추가 또는 수정
- 전체 회귀검증
- Before/After Evidence
- Rollback 또는 Abandon

### 금지
- 검증과 무관한 기능 추가
- 고객 승인 없는 대규모 Refactoring
- Main 직접 Commit
- 실패 Test 삭제로 PASS 조작
- 정책 Gate 우회

## 7-1. OMemory
### 책임
OReview, OVerification, OImprovement의 실행 결과와 Before/After Evidence에서 재사용 가능한 지식을 추출·검증하여 이후 Learning, Planning, Review, Verification, Improvement에 근거 있는 신호로 제공한다. 자동 판정이 놓친 결함을 재귀학습으로 흡수한다.

### 기능 — 지식 축적
- Fix Pattern / Failure Pattern 추출
- AI/바이브 코딩 특유 패턴 태깅
- Component Signature 단위 Pattern 매칭
- Pattern Confidence 산정
- Tenant 전용/공유 Corpus 분리
- Pattern 승격 최소 독립 재현 2회
- 재현 실패 누적 자동 강등 계약 적용

### 기능 — 재귀학습
`LEARNING_CANDIDATE → VALIDATION_REQUESTED → VALIDATION_RUNNING → VALIDATION_PASSED/FAILED → PROMOTION_REVIEW → PROMOTION_APPROVED → SHADOW_APPLIED → CANARY_APPLIED → STABLE_APPLIED → APPLIED_LOCKED`를 사용한다.

- Learning Engine: 후보 생성 전담, PASS/승격/자기승인 금지
- Validator Engine: FALSE_PASS/FALSE_FAIL/NONDETERMINISM/GOLDEN/HIDDEN 검증, Learner 결과 재계산
- Executor Engine: Queue Lease, Idempotency, Checkpoint Resume
- Governance Engine: Promotion, Apply, Post-apply Verification, Rollback 통제

### 산출물
KnowledgePattern, MissedFinding, PatternApplicationReceipt, PatternLibraryRevision, DetectionCapabilityChangeReport, PromotionReceipt, ApplyReceipt, RollbackPointer

### 수용기준
- 모든 Pattern은 최초 근거로 역추적 가능
- Pattern은 Critical/High 자동 확정의 유일근거가 될 수 없음
- 공유 Corpus 익명화
- Opt-out 데이터 공유 금지
- Rule/모델 개정은 Golden + Hidden/Qualification 회귀를 통과
- MissedFinding 발견경로 기록
- Rollback Pointer/Active Selector 없는 Applied 금지
- OMemory가 APPLIED_LOCKED 1건 전 OTraining TARGET_PRODUCT_APPLY 금지

## 7-2. OTraining (Target AI Auto-Learning)
### 출시 전제조건
OMemory 자기 재귀학습이 APPLIED_LOCKED 최소 1건에 도달하기 전 OTraining은 MVP·상용 출시 대상이 아니다.

### 책임
검증된 Finding 또는 승인된 개선 목표에 근거해 대상 프로그램의 AI 구성요소를 재학습하고 독립 재검증을 통과한 경우에만 배포를 승인한다.

### 기능
- Decide: Improve vs Train
- Training Plan
- RAG 학습자료 소유권 분리
- 학습 데이터 품질 검사
- Training Run
- Before/After 비교
- 독립 재검증
- Model/RAG/Prompt/Agent Version 승격
- Production Observation
- Re-learn Trigger

### 산출물
TrainingRequest, TrainingPlan, TrainingRun, ModelVersion/RAGIndexVersion/PromptVersion/AgentPolicyVersion, EvaluationReport, DeploymentApproval, ProductionObservation, RelearnTrigger

### 수용기준
- 검증된 Finding/승인 목표 없이 Training 시작 금지
- 평가 데이터셋과 학습 데이터셋 분리
- Before/After 없이 배포 승인 금지
- 자기참조 승인 금지
- Production Observation 전 개선 최종주장 금지
- Re-learn Trigger 자동실행 금지

### 금지
- 고객 동의·라이선스 없는 학습
- 평가 데이터셋 학습 재사용
- AI 변경으로 Patch 승인 우회
- Production Observation 생략

## 8. OEvidence
- Immutable Evidence Metadata
- Artifact Hash
- Policy Digest
- Environment Manifest
- Tool Version
- Input/Output Digest
- Finding/Decision Link
- Review/Verification/Improvement/Delivery/Retention Receipt
- Evidence Schema Version 결속
- 정기 Evidence 재현성 감사

## 9. OGit
- Workspace Cleanliness 검사
- Worktree 생성·폐기
- Branch Naming
- Diff Limit 및 Forbidden Path
- Commit Message 생성
- Push 승인
- Draft PR 생성
- Git/CI Provider 연동
- CI 상태/Review Comment 수집
- Merge 권고만 제공, Merge 실행 금지
- Rollback 정보/검증
- Post-merge Incident 대응
- Multi-PR Integration Risk Scan

## 10. ODelivery
- Web Report
- Program Profile
- Findings CSV/JSON/SARIF
- SBOM
- CoverageReport
- Acceptance Certificate
- Evidence Pack
- Patch/Diff
- Draft PR
- Executive Summary
- Technical Report
- Deletion Receipt

## 10-1. ONotify
### 책임
Case, Finding, License의 중요 상태 변화를 구독 채널로 능동 통지한다.

### 기능
- CaseBlocked, CriticalFindingOpened, VerificationFailed, LicenseExpiringSoon, LicenseSuspended, CreditLow, PatchRegressionFailed, DeletionCompleted
- Email/Webhook/VS Code/관리자 알림함
- Organization/User 구독 설정
- Webhook 재시도/서명/Dead Letter
- 발송 Evidence 기록

### 수용기준
- Critical Finding과 CaseBlocked 발생 후 5분 이내 발송 시도
- 반복 Webhook 실패 시 관리자 알림함 Fallback
- Opt-in 채널만 사용

## 11. 비기능 요구사항
- **NFR-SEC**: 저장·전송 암호화, Secret 비노출, 최소권한
- **NFR-REL**: 멱등성, 재시도, 중복 이벤트 방어
- **NFR-PERF**: 대규모 Repository 단계적 분석과 중단·재개
- **NFR-AVAIL**: SaaS 가용성, Rate Limit, Tenant 동시실행 상한
- **NFR-AUDIT**: 모든 권한·실행·변경 감사
- **NFR-PORT**: SaaS, Local, 폐쇄망 배포
- **NFR-PRIV**: 보존기간과 완전 삭제 증명
- **NFR-OBS**: Trace, Metric, Structured Log
- **NFR-ACCESS**: 역할 분리
- **NFR-SESSION**: 세션 타임아웃·동시세션·Fixation 방지
- **NFR-CONFIG**: 보안 헤더·CORS·설정 검증

## 12. 추적성
Requirement → Design Component → Code Module → Test Case → Evidence → Release를 단일 ID 체계로 연결한다.

## 13. Validation Completeness·Assurance 기능 요구사항 (신규, 2026-08-09)
이 절은 "ONSure가 대상의 결함 부재를 절대적으로 증명한다"는 기능이 아니라, **무엇을 검증했고 무엇을 검증하지 못했는지, 검증기가 그 대상을 검증할 자격이 있는지, Final Claim이 어느 근거에서 나왔는지를 기계적으로 증명**하는 기능을 정의한다. 아래 항목은 기능정의 기준이며 계약/코드 구현상태는 [08](08_REVIEW_CHECKLIST_OPEN_DECISIONS.md)에서 추적한다.

### FR-META-001 Validation Target Manifest
모든 Validation은 Source SHA만이 아니라 제품 전체 정체성을 하나의 `ValidationTargetManifest`로 고정해야 한다.

필수 포함:
- target_id/target_type
- source/tree/build artifact digest
- dependency artifact digest/provenance
- runtime config/feature flags
- policy/rule pack
- model/prompt/tool/RAG corpus/index
- external service contract
- OS/runtime/DB/deployment environment

**수용기준**: Manifest digest가 바뀌면 이전 Final/Certificate는 현재 대상에 그대로 적용되지 않으며 STALE 또는 REASSESSMENT_REQUIRED가 된다.

### FR-META-002 Scope/Requirement Epoch Lock
Scope와 Requirement Universe를 각각 epoch/digest로 관리한다. 신규 Component/Requirement 발견 또는 제외 변경 시 epoch를 증가시키고 Coverage를 다시 계산한다.

**수용기준**: 검증 중 Scope를 줄여 기존 PASS를 유지하거나, 새 Requirement 발견 후 이전 100% Coverage를 유지할 수 없다.

### FR-META-003 Validator Capability Qualification
Target 유형별로 ONSure가 실제 검증할 수 있는 Capability를 사전 Qualification한다.

필드 예:
- target_class
- language/framework/architecture/deployment class
- supported defect classes
- static/runtime/security/recovery/AI/platform capability
- qualification benchmark version
- recall/precision/critical escape rate
- qualification_status: QUALIFIED | PARTIAL | NOT_PROVEN

**수용기준**: NOT_PROVEN Capability가 필요한 Target에는 해당 영역 Full Assurance를 발급하지 않는다.

### FR-META-004 Observability Qualification
각 Fault/Claim별 Required Observation Matrix를 정의한다. `PROCESS_EXIT`, LOG, TRACE, NETWORK, DATABASE_STATE, AI_TOOL_CALL, AUTHORITY_EVENT 등 필요한 채널이 실제로 완전 수집됐는지 확인한다.

**수용기준**: 필요한 Collector가 실패/부분수집이면 해당 Claim의 absence를 PROVEN으로 만들 수 없다.

### FR-META-005 Oracle 유형과 독립성
Oracle은 다음 유형을 구분한다.
- SPEC_ORACLE
- EXECUTABLE_ORACLE
- METAMORPHIC_ORACLE
- DIFFERENTIAL_ORACLE
- EXPERT_ORACLE
- REAL_WORLD_ORACLE

각 Oracle은 `oracle_digest`, `target_code_dependency`, `ground_truth_grade`, `independence_class`를 기록한다. Cross-Model Agreement는 Ground Truth가 아니라 corroboration이다.

### FR-META-006 Evidence Target Binding
Final에 사용되는 Evidence는 최소 `target_manifest_digest`, `scope_epoch_digest`, `requirement_set_digest`, `policy_digest`, `oracle_set_digest`, `detector_pack_digest`, `environment_digest`, `run_id`, `nonce`에 결속한다.

**수용기준**: 다른 Target/Scope/Run의 정상 Evidence를 현재 Claim에 대입하면 fail-closed한다.

### FR-META-007 Evidence Freshness / Anti-Reuse
Evidence마다 생성시각, run, nonce, validity context를 확인하고 stale/replayed evidence를 Final에서 제외한다. Approval/Receipt의 single-consume를 지원한다.

### FR-META-008 Independence Profile
독립성을 Execution, Principal, Implementation, Oracle, Discovery, Knowledge 6축으로 기록한다. 서로 다른 run_id나 key_id만으로 완전 독립을 주장하지 않는다.

### FR-META-009 Decision Propagation
의존 하위 결과의 FAIL/HOLD/BLOCKED/NOT_RUN/INCONCLUSIVE/UNKNOWN은 상위 Claim에 전파한다. Critical dependency가 미검증이면 전체 PASS로 평균화하지 않는다.

### FR-META-010 Atomic Validation Snapshot
Final은 하나의 동일 Target/Scope/Requirement/Policy generation에서 필수 Lane이 동시에 성립한 Snapshot이어야 한다. 서로 다른 Run의 좋은 결과를 선택적으로 조립하지 않는다.

### FR-META-011 Validation Staleness / Invalidation
다음 변경은 기존 Validation의 유효성을 재평가한다.
- source/artifact/dependency
- config/feature flags
- policy/rule/oracle
- model/prompt/RAG
- external interface
- 중요 규제/표준
- 새 Critical CVE/MissedFinding

상태: VALID | STALE | INVALIDATED | SUPERSEDED.

### FR-META-012 Final Freshness Barrier
Final Lock 직전에 Target/Scope/Policy/Findings/Approval/Observer/Independent Receipt를 다시 읽어 현재도 조건이 성립하는지 재검증한다.

### FR-META-013 Final Claim Reconstruction
저장된 report/score/current_state를 신뢰하지 않고 Raw Evidence와 Contract Graph에서 Final Claim을 다시 계산한다. 재계산 결과가 저장값과 다르면 HOLD한다.

### FR-META-014 ProgramRiskScore Independent Recalculation
ProgramRiskScore는 raw Finding/MissedFinding에서 독립 재계산한다. 저장된 score/grade의 Schema-valid 여부만으로 의사결정하지 않는다. RiskScore는 Hard Gate를 대체하지 못한다.

### FR-META-015 Accepted Risk / Waiver Integrity
Waiver나 Risk Accept는 FAIL/NOT_RUN을 PASS로 바꾸지 않는다. 별도 `ACCEPTED_WITH_EXCEPTION` 성격으로 표시하고 affected requirement/claim, reason, approver, scope, risk, expiry, compensating control, revalidation rule을 기록한다.

Non-waivable 후보: Evidence integrity, Final decision integrity, target identity, tenant isolation, validator independence.

### FR-META-016 Finding Closure Semantics
CLOSED는 FIXED/FALSE_POSITIVE/DUPLICATE/NOT_APPLICABLE/MITIGATED/SUPERSEDED를 구분한다. ACCEPTED_RISK는 해결상태와 분리한다.

### FR-META-017 Composite / Interaction Risk
단일 Finding 외에 Attack/Failure/Privilege/Data Leakage Chain을 생성하고 Component×Component, Permission×State, Failure×Recovery, AI×Tool×RAG 상호작용을 위험기반으로 탐색한다.

### FR-META-018 Negative Assurance
`count(CRITICAL)=0`과 `CRITICAL_ABSENCE_PROVEN`을 분리한다. 부재 주장은 detector 실행, scope/observability 충분성, unsupported critical surface 0, tool health, Evidence binding을 모두 요구한다.

### FR-META-019 Flakiness / Statistical Assurance
Retry 전후 결과를 모두 보존하고 Flaky PASS를 안정 PASS와 분리한다. 비결정 시스템은 위험 기반 sample size, failure rate, confidence bound, seed/generator version을 기록한다.

### FR-META-020 Fixture Precondition Proof
Negative/Adversarial Fixture는 공격 전 실제 비권한/다른 Tenant/만료 Token/승인부재 등 선행조건을 증명한다. Positive counterpart도 함께 실행하여 보안 강화가 정상 권리를 파괴하지 않았는지 검증한다.

### FR-META-021 Rights / Capability Preservation
Remediation 후 원 결함 해소뿐 아니라 정당한 사용자·운영자 권리의 `Declared→Authorized→Reachable→Invocable→Effective→Recoverable` 체인을 검증한다. 보안 Fix로 operator recovery가 사라지면 Rights Regression으로 HOLD한다.

### FR-META-022 Evidence Transactionality / Survivability
검증기 crash·timeout·disk full에도 incomplete execution이 PASS로 복구되지 않아야 한다. Evidence는 PREPARED→COMMITTED 또는 동등한 durable transaction을 사용하고 미완료는 ABORTED_UNTRUSTED/INCONCLUSIVE로 처리한다.

### FR-META-023 Validation Isolation
동시 Run이 workspace/DB/cache/queue/state를 서로 오염시키지 않도록 immutable workspace 또는 lease를 사용한다. pre/post state digest와 cleanup 결과를 기록한다.

### FR-META-024 Verified-to-Deployed Identity
검증 Artifact와 실제 배포 Artifact digest가 동일한지 확인한다. Environment 차이는 MATCH/NON_MATERIAL/MATERIAL/UNKNOWN으로 분류하고 MATERIAL/UNKNOWN은 Assurance Ceiling에 반영한다.

### FR-META-025 Dependency / External Freshness
Dependency approval은 version/license뿐 아니라 artifact hash, origin, provenance를 포함한다. CVE/advisory 확인은 검사시점과 advisory snapshot을 기록한다. "denylist 0건"을 "취약점 없음"으로 표현하지 않는다.

### FR-META-026 Evidence Origin Independence
Evidence 파일 수가 아니라 독립 origin 수를 계산한다. PRIMARY/DERIVED/AGGREGATED를 구분하고 Material Claim은 PRIMARY Evidence까지 역추적 가능해야 한다.

### FR-META-027 Trust Registry Semantic Integrity
Key registry에서 unique key/fingerprint, principal separation, authority cardinality, validity window, revocation semantics를 검증한다. 다른 key가 같은 principal이면 완전 독립자로 세지 않는다.

### FR-META-028 Trusted Computing Base Manifest
ONSure가 암묵적으로 신뢰하는 OS/kernel/JVM/crypto/filesystem/time/key registry/sandbox 등을 TCB Manifest로 공개하고 Final Verifier의 TCB를 최소화한다.

### FR-META-029 Detector Qualification
Seeded fault를 `registered→executed→detected/escaped`로 추적한다. Critical seeded defect escape가 있으면 해당 Capability Qualification을 발급하지 않는다.

### FR-META-030 Validation Saturation Proof
동일 방법을 반복해 신규 Finding이 0건인 것만으로 saturation을 선언하지 않는다. independent discovery method, ontology diversity, hidden fault sensitivity, observability 충분성을 함께 요구한다.

### FR-META-031 Semantic Dataset Separation
Training/Validation/Hidden/Golden은 byte hash뿐 아니라 semantic family 기준으로 오염을 관리한다. Hidden 결과의 반복 피드백으로 Rule을 최적화하지 않는다.

### FR-META-032 Memory-Blind Independent Review
고신뢰 Lane 일부는 이전 score/verdict/KnowledgePattern을 보지 않고 수행한다. Memory-aware와 blind 결과 불일치는 자동 다수결이 아니라 HOLD/제3 검증으로 처리한다.

### FR-META-033 Ground Truth Provenance / Epoch
Ground Truth는 `source, requirement_digest, oracle_digest, created/reviewed_by, evidence, validity_scope, epoch`을 가진 버전 객체다. Requirement/Policy/Oracle 변경 시 stale 처리한다.

### FR-META-034 Learning Regression Guard
Rule Pack 변경 후 전체 평균이 좋아져도 Critical Recall 또는 기존 Critical seeded defect 탐지력이 감소하면 승격하지 않는다.

### FR-META-035 Rule Weakening High-Risk Gate
Detector weakening/removal, Oracle change, Severity policy change, Coverage policy change는 신규 detector 추가보다 강한 승인·Hidden Benchmark·Critical Recall 회귀를 요구한다.

### FR-META-036 Historical Revalidation
새 MissedFinding/Detector/Rule이 과거 Validation에 영향을 줄 수 있으면 영향 대상 Certificate를 찾아 SAFE/REASSESSMENT_REQUIRED/STALE로 분류한다.

### FR-META-037 Assurance Level / Claim Semantics
단일 PASS와 Assurance Strength를 분리한다. 기본 레벨:
- L0 UNASSESSED
- L1 STATIC_REVIEWED
- L2 EXECUTION_VERIFIED
- L3 INDEPENDENTLY_VERIFIED
- L4 ADVERSARIALLY_VERIFIED
- L5 QUALIFIED_HIGH_ASSURANCE

Final Report는 Scope, Capability, Observability, Oracle, Evidence, Independence, Temporal assurance를 함께 공개한다.

### FR-META-038 Unknown / Uncertainty Budget
KNOWN_UNKNOWN, DISCOVERED_UNKNOWN, UNRESOLVED, UNCLASSIFIED, UNOBSERVABLE를 분리하고 dependency graph로 uncertainty를 상위 Claim에 전파한다. L5의 Critical Unknown 허용치는 0을 원칙으로 한다.

### FR-META-039 Anti-Evasion Validation
대상이 ONSure/Sandbox/안전시험을 감지해 다르게 행동하는 경우를 탐지하기 위해 instrumented/uninstrumented, validator marker 변화, naturalistic/blind scenario를 비교한다.

### FR-META-040 Validation Resource Exhaustion Integrity
검증 예산 소진 때문에 Scope가 조용히 축소되어 PASS하지 못하게 한다. Budget Exhausted는 CoverageReport와 Decision에 반영되어 HOLD/PARTIAL/INCONCLUSIVE로 처리한다.

### FR-META-041 Cross-Contract Semantic Validation
개별 Schema valid를 넘어 Contract 간 관계를 검증한다. 예: REJECT Approval은 Final Lock 불가, purpose/type 일치, target/candidate digest 일치, run context 일치, cancelled evidence 사용 금지.

### FR-META-042 Final Lock / Certificate Revocation
Final Lock 기록은 immutable하게 보존하되 현재 유효성은 revocable해야 한다. 새 Critical/CVE/Drift/Policy change 발생 시 Certificate 상태와 영향범위를 갱신하고 사용자에게 통지한다.

### FR-META-043 AI·개발자·운영자 표시 일관성
API, Web, VS Code, Report는 동일 상태 온톨로지와 Assurance Ceiling을 사용한다. `SELF_VALIDATION_NONFINAL PASS`를 단순 Final PASS로 축약하는 UI/문서 변환을 금지한다.

### 13.1 Meta-Validation 종합 수용기준
- 검증 대상의 Target Manifest와 Scope/Requirement Epoch가 없으면 Final Claim 불가
- Critical Capability가 NOT_PROVEN이면 L5 불가
- Critical Observation Gap/Unknown이 있으면 "전체 검증 완료" 표현 금지
- Cross-Run Evidence Mixing 금지
- Waiver/Accepted Risk가 FAIL/NOT_RUN을 PASS로 변경하지 않음
- Final Claim은 Raw Evidence에서 재구성 가능
- Final Lock 직전 Freshness Barrier 통과 필수
- 새 MissedFinding은 과거 인증 영향분석을 발생시킴
- Critical seeded defect escape 0이 검증기 Qualification의 하드 조건
- 검증기 자신의 Rule/Oracle 완화는 Critical Recall 회귀 0을 증명해야 함
