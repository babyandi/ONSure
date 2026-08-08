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
- FR-COM-009 Organization은 자신의 Pattern/Fixture가 익명화된 공유 Corpus에 기여할지 여부를 명시적으로 선택(Opt-in/Opt-out)하며 기본값은 Opt-out이다. 규제산업 Enterprise Edition은 공유 Corpus 기여를 계약으로 원천 차단할 수 있다. 단, 공개 CVE/취약점 DB 등 이미 공개된 정보에서 유래한 Pattern(고객 코드의 행위·구조를 관찰해 만든 Pattern이 아닌 것)은 고객 코드 관찰과 무관하므로 이 Opt-out 대상에서 제외하고 항상 최신으로 유지한다.
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

## 5. OReview
### Review 영역
Requirement, Architecture, Design, Policy, Code, AI, Security, Performance, Test, Quality, Merge

### 기능
- 요구사항과 변경파일 Traceability
- 역방향 Traceability: 어떤 요구사항에도 연결되지 않은 신규 코드(고아 코드·과잉 생성)를 별도 탐지
- 설계규칙·Dependency Boundary 검토
- 정책 위반 탐지
- 버그·동시성·예외·자원누수·복잡도 검토
- Prompt Injection, Tool 권한, RAG 출처·오염 검토
- Secret, 취약 Dependency, 인증·인가 검토
- 테스트 누락과 취약 Assertion 검토
- AI 자기주장 검증(Self-Claim Verification): Commit/PR/Chat 응답에서 AI가 스스로 밝힌 구현·수정·테스트통과 주장을 추출해 실제 Evidence와 대조하고 불일치 시 Finding 생성
- PR 단위 Inline Comment, Summary, Decision 생성
- 독립 Review Pass 지원(가능한 경우 원 구현에 사용된 모델과 다른 계열의 모델로 수행)

### Decision
영역별: PASS, FAIL, HOLD, NOT_RUN, NOT_APPLICABLE. 종합(quality_decision): PASS, FAIL, HOLD. `merge_authorized`는 계약상 항상 false다([03 §6](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) 참조, `contracts/oreview-result.v1.schema.json`).

### 수용기준
- Finding마다 파일·라인 또는 구성요소·근거·정책·영향·제안 포함
- 중복 Finding 통합
- 추측성 Finding은 Confidence와 확인방법 포함
- Critical/High Finding은 근거 없는 자동 승인 금지
- Critical Finding은 원 구현/1차 판정과 다른 모델 계열의 Cross-Model Verification을 거친 뒤에만 최종 확정한다([03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) §10-1)
- AI 생성 비중이 높은 Component에서 Critical/High 판정의 Confidence가 조직 임계치 미만이면 자동 승인·자동 반려 없이 Human 또는 Professional Reviewer에게 강제 회부한다(OVerification의 동급 판정에도 동일 원칙 적용)
- Human/Professional Reviewer의 판정도 자동판정과 동등하게 품질관리 대상이다. Golden Review Fixture에 대한 Reviewer 판정 정확도를 주기적으로 측정하고, 특정 Reviewer의 정확도가 지속적으로 기준 미달이면 배정을 제한한다

## 6. OVerification
### 기능
- Static, Build, Unit, Integration, Scenario, Adversarial, Performance, Recovery, License 검증
- ProgramProfile 또는 (Verify 단독 상품의 경우) 고객 제공 ScopeManifest를 구조 정보 입력으로 사용
- 요구사항별 Test Claim 생성
- 실제 실행결과와 Expected 결과 비교
- Mutation Testing: 대상 코드에 결함을 의도적으로 주입해 기존 Test Suite가 실제로 탐지하는지 측정(Mutation Score)하여 "테스트 존재"와 "테스트 실효성"을 구분
- Negative Test와 Fail-closed 확인
- Regression Set 구성
- 결과 재실행과 Flaky 분리
- 독립 OTester/OAudit 판정 지원

### Decision
PASS, FAIL, BLOCKED, NOT_RUN, INCONCLUSIVE, NON_FINAL

### 수용기준
PASS는 실행 증거 없이 생성할 수 없다. BLOCKED와 NOT_RUN은 FAIL과 분리한다. 실행된 Test/Scenario가 0건이거나 전부 Skip되었거나 도구·환경 오류로 결과를 얻지 못한 경우는 PASS로 표기할 수 없으며 NOT_RUN 또는 BLOCKED로만 판정한다. 기존 테스트가 전무한 대상은 OPlanning이 최소 Smoke/Golden Path Test를 자동 제안해 실행하며, 이 경우 PASS는 "기존 테스트로 확인됨"과 "신규 최소 테스트로만 확인됨"을 구분해 표기한다(테스트가 없어서 통과한 것을 테스트가 충분해서 통과한 것처럼 보이지 않게 한다).

## 7. OImprovement
### 기능
- 승인된 Finding만 입력
- Root Cause 후보와 영향범위 생성: 재현 가능한 실패를 기준으로 최초 실패 지점(First Failure Point), 원인 후보, 신뢰도, 미확인 사항을 분리해 생성한다. 재현되지 않는 실패는 RCA_CANDIDATE 상태를 벗어나 확정 RCA로 표기하지 않는다
- Patch Plan과 예상 변경파일 제시
- Blast Radius 드라이런: Patch를 실제 적용하기 전 영향받는 파일·Component·의존 Program을 시뮬레이션으로 제시하고 사용자 승인 대상에 포함
- Worktree·Branch 생성
- 최소 변경 원칙 Patch
- 관련 Test 추가 또는 수정(수정된 결함마다 재발 방지용 회귀 Test 필수)
- 전체 회귀검증
- Before/After Evidence: 대상 결함의 해소 여부뿐 아니라 관련 없는 기능의 동작 Diff와 성능 지표(응답시간·자원사용) 변화를 함께 비교해 의도치 않은 부작용을 확인
- Rollback 또는 Abandon

### 금지
- 검증과 무관한 기능 추가
- 고객 승인 없는 대규모 Refactoring
- Main 직접 Commit
- 실패 Test 삭제로 PASS 조작
- 정책 Gate 우회

## 7-1. OMemory
### 책임
OReview, OVerification, OImprovement의 실행 결과와 Before/After Evidence에서 재사용 가능한 지식을 추출·검증하여 이후 Learning, Planning, Review, Verification, Improvement에 근거 있는 신호로 제공한다. 자동 판정이 놓친 결함을 재귀학습으로 흡수해 탐지 능력을 지속적으로 보강한다.

### 기능 — 지식 축적
- Fix Pattern 추출: 승인된 ImprovementRequest의 RCA와 Patch에서 재발 방지 가능한 패턴 후보 생성
- Failure Pattern 추출: FAIL로 종료된 Review와 Verification에서 실패 유형 추출
- AI/바이브 코딩 특유 패턴 별도 태깅: Hallucinated Dependency, Prompt Injection 방어 누락, 과잉 생성, Silent Error Swallowing, Test 없는 대량 커밋 등 ([03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md)의 AI/Vibe-coding 진단 절과 연동)
- Component Signature(코드 Hash + Interface Hash) 단위로 Pattern을 매칭해 신규 Case의 대상과 대조
- Pattern Confidence를 재현 횟수, 적용 성공률, False Positive 이력으로 산정
- 신규/기존 고객 소스에서 나온 Pattern은 기본적으로 Tenant 전용이며, 고객 식별 정보를 제거한 뒤에만 공유 Corpus로 승격 가능
- 재현 3회 이상 실패(False Positive)한 Pattern은 자동 강등

### 기능 — 재귀학습(Recursive Detection Learning)
실제 `contracts/learning-validation-engine.v1.json`과 `contracts/learning-to-application-pipeline.v1.json`이 이 루프의 권위다. 자동 Review/Verification이 놓친 결함이 Independent Review 불일치, Human Review Override, Production Incident, 고객 신고, 뒤늦은 Regression으로 확인되면 다음 파이프라인을 수행한다.

`LEARNING_CANDIDATE → VALIDATION_REQUESTED → VALIDATION_RUNNING → (VALIDATION_PASSED 또는 VALIDATION_FAILED) → PROMOTION_REVIEW → PROMOTION_APPROVED → SHADOW_APPLIED → CANARY_APPLIED → STABLE_APPLIED → APPLIED_LOCKED`(예외 ROLLED_BACK)

4개 엔진으로 역할을 분리하며 어느 엔진도 자기 자신을 검증·승인하지 않는다(계약의 `hard_invariants`).

- **Learning Engine**(후보 생성 전담): FAILURE_RECEIPT_ANALYSIS, RCA_CLUSTERING, FAILURE_MODE/FIXTURE/ORACLE/RUBRIC/REMEDIATION_PATTERN Candidate 생성만 수행. PASS_DECISION, PROMOTION_GATE_OPEN, SILENT_RUBRIC_CHANGE, **SELF_APPROVAL**은 금지(`LEARNING_ENGINE_CANNOT_PASS_VALIDATE_OR_PROMOTE`)
- **Validator Engine**(독립 판정 전담): FALSE_PASS, FALSE_FAIL, NONDETERMINISM, GOLDEN_REGRESSION, HIDDEN_TEST_RESULT를 측정하며 Learner 출력을 재계산 없이 신뢰하지 않는다(`VALIDATOR_MUST_RECALCULATE_CANDIDATE_FROM_SOURCE_EVIDENCE`). Hidden Dataset은 Learning Engine이 접근할 수 없다(`HIDDEN_DATASET_MUST_NOT_BE_USED_BY_LEARNING_ENGINE`)
- **Executor Engine**(Queue 소비·실행): READY→RUNNING→DONE/RETRY/HOLD/CANCELLED/EXPIRED→APPLY_PENDING→POST_APPLY_VERIFY→APPLIED_LOCKED. Queue Lease, Idempotency Key, 중복소비 차단, Checkpoint Resume을 강제
- **Governance Engine**(승격·적용 통제): Promotion Receipt, Reviewer/Approver 분리, Apply Commit 또는 안정 Registry Version, Post-apply Verification, Rollback Pointer, Applied Count 회계를 강제. Candidate를 Applied로 집계하거나 Rollback Pointer 없이 승격하는 것을 금지

승격은 SHADOW_APPLIED(제한 노출) → CANARY_APPLIED(부분 확대) → STABLE_APPLIED(전체 적용) → APPLIED_LOCKED(불변 Evidence로 고정) 순으로 점진적이다. `applied_count`는 STABLE_APPLIED 또는 APPLIED_LOCKED이면서 Active Selector·Apply Commit·Post-apply Verification Receipt·Rollback Pointer가 모두 있어야만 집계한다(`MISSING_APPLY_RECEIPT_IS_ZERO_APPLIED`) — Candidate가 Queue에 있거나 PASS Receipt만 있고 Promotion Approval이 없으면 0건으로 취급한다.

이 루프는 사람이 승인한 개정만 프로덕션에 반영하며, 탐지 결과를 스스로 무비판 재학습해 자기 자신을 검증하지 않는다(자기 참조 승인 금지). Cycle마다 Recall(놓친 결함 비율 감소)과 False Positive율 변화를 함께 추적해 개선/퇴보를 판정한다.

### 산출물
KnowledgePattern, MissedFinding, PatternApplicationReceipt, PatternLibraryRevision, DetectionCapabilityChangeReport, PromotionReceipt, ApplyReceipt, RollbackPointer

### 수용기준
- 모든 Pattern은 최초 근거가 된 ReviewFinding, VerificationFinding 또는 ImprovementRequest로 역추적 가능
- Pattern 매치는 그 자체로 Critical/High 자동 확정 근거가 될 수 없으며 Confidence를 높이는 보조 신호로만 사용한다
- 공유 Corpus로 승격되는 Pattern과 Fixture는 고객 식별정보를 포함하지 않는다(Anonymization 필수)
- FR-COM-009의 Opt-out을 선택한 Organization의 데이터는 어떤 형태로도 공유 Corpus 후보 추출 대상에서 제외한다(Tenant 전용 Pattern 생성은 계속 가능)
- 재귀학습으로 인한 Rule/모델 개정은 반드시 전체 Golden Fixture Regression을 통과한 뒤에만 프로덕션에 반영한다
- MissedFinding은 발견 경로(Independent Review/Human Override/Incident/고객신고/지연 Regression)를 구분해 기록한다
- Rollback Pointer 없는 승격, Active Selector 없는 Applied 집계는 금지한다(계약의 `hard_invariants` 그대로 적용)
- 이 루프가 최초로 APPLIED_LOCKED에 1건 이상 도달하기 전까지는 §7-2 OTraining의 TARGET_PRODUCT_APPLY(대상 프로그램에 학습결과 적용)를 MVP 범위에 포함하지 않는다(`contracts/learning-to-application-pipeline.v1.json`의 `TARGET_PRODUCT_APPLY: mvp_allowed=false`, 사유 "ONSure Core가 자신의 승격 경로를 먼저 증명해야 함")

## 7-2. OTraining (Target AI Auto-Learning)
`docs/v2/09_TARGET_AI_AUTO_LEARNING_BUSINESS_AND_DEVELOPMENT_STRATEGY.md`에서 흡수. OLearning(Program Understanding Learning)과 다른 축이다 — OLearning은 ONSure가 대상 프로그램을 이해하는 학습이고, OTraining은 대상 프로그램 **안의** RAG·Prompt·Agent·Model 자체를 실제 데이터로 재학습·개선하는 기능이다.

### 출시 전제조건(하드 게이트)
`contracts/learning-to-application-pipeline.v1.json`은 학습결과 적용을 3등급으로 나눈다: VALIDATION_PACK_APPLY(허용), ONSURE_RUNTIME_CODE_APPLY(제한적 허용, Human Review 필수), **TARGET_PRODUCT_APPLY(현재 불허 — "ONSure Core가 자신의 승격 경로를 먼저 증명해야 함")**. OTraining이 대상 프로그램에 학습결과를 적용하는 것은 정확히 TARGET_PRODUCT_APPLY에 해당한다. 따라서 **OMemory의 자기 재귀학습 루프(§7-1)가 APPLIED_LOCKED에 최소 1건 도달하기 전까지 OTraining은 MVP·상용 출시 대상이 아니다.** 이 순서를 임의로 앞당기지 않는다 — ONSure가 대상 프로그램을 재학습시키려면, 먼저 자기 자신의 학습 결과를 안전하게 승격시킬 수 있음을 증명해야 한다.

### 책임
검증된 Finding 또는 승인된 개선 목표(정확도·안정성·속도·비용)에 근거해 대상 프로그램의 AI 구성요소를 재학습하고, 독립 재검증을 통과한 경우에만 배포를 승인한다.

### 기능
- Decide: OImprovement와 공유하는 RCA(원인이 코드·정책·데이터·RAG·Prompt·Agent·Model 중 어디인지 판정, §7 OImprovement 참조) 결과를 바탕으로 코드 Patch(Improve)로 충분한지 AI 재학습(Train)이 필요한지 판정 근거를 기록한다. 판정은 사람 또는 승인된 규칙이 하며 ONSure가 임의로 결정하지 않는다
- Training Plan 생성: 대상 구성요소(RAG Index/Prompt/Agent 선택정책/Model), 학습 데이터 출처, 평가 데이터셋, 예상 비용·GPU·소요시간을 제시하고 승인받는다
- RAG 학습자료 소유권 분리(`docs/architecture/ONSURE_PROGRAM_RAG_ENVIRONMENT_v1.md`에서 흡수): ONSure 자신의 검증·학습에서 나온 RAG 후보는 ONSure 저장소에서 관리하고, 대상 프로그램의 실제 RAG 학습자료는 대상 프로그램의 `.onsure/rag-preparation/`에서 대상이 소유한다 — ONSure가 고객의 학습자료를 중앙으로 가져와 소유하지 않는다. 환경 생성(Bootstrap)은 검증과 분리된 명시적 승인 후에만 하며, Bootstrap 자체를 실제 적재·Index 생성·Embedding·파인튜닝·적용으로 표시하지 않는다
- 학습 데이터 품질 검사: 편향·중복·유출·오염(Poisoning) 여부 확인. 오염 검증 자료 자체도 COMPLETE/PARTIAL/MISSING/DUPLICATE/STALE로 준비상태를 판정하며 PARTIAL/MISSING/DUPLICATE/STALE는 투입 자격을 HOLD로 둔다(`docs/architecture/ONSURE_RAG_ADVERSARIAL_MATERIAL_PREPARATION_v1.md`)
- Training Run 실행: RAG 재인덱싱, Prompt 개정, Agent 정책 재학습, Model Fine-tuning 중 해당 유형만 수행
- Before/After 비교: 기존 버전과 신규 버전을 동일 평가 데이터셋·시나리오로 비교
- 독립 재검증: Training을 수행한 모델과 다른 모델 또는 규칙 기반으로 결과를 재확인한다([03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) §10-1 Cross-Model Verification과 동일 원칙 재사용)
- 승인된 경우에만 ModelVersion/RAGIndexVersion/PromptVersion/AgentPolicyVersion을 승격하고 Deployment Approval을 기록
- Production Observation: 배포 후 실 운영 데이터에서 성능·오류율을 관찰
- Re-learn Trigger: Observation에서 임계치를 넘는 성능저하나 신규 실패 패턴이 확인되면 새 TrainingRequest를 제안한다(자동 실행 아님) — 이 제안은 §7-1 OMemory의 MissedFinding 등록과 같은 성격이며, ONSure 자신의 탐지 능력을 보강하는 재귀학습 루프와 동일한 "검증된 근거 → 독립 재검증 → 승격" 구조를 대상 프로그램의 AI에도 적용한 것이다

### 산출물
TrainingRequest, TrainingPlan, TrainingRun, ModelVersion/RAGIndexVersion/PromptVersion/AgentPolicyVersion, EvaluationReport, DeploymentApproval, ProductionObservation, RelearnTrigger

### 수용기준
- 검증된 Finding 또는 승인된 목표 없이 Training을 시작할 수 없다(OImprovement의 임의 요청 금지 원칙과 동일)
- 평가 데이터셋은 학습 데이터셋과 물리적으로 분리되며 학습에 재사용되지 않는다
- Before/After 비교 없이 배포 승인을 발급하지 않는다
- 독립 재검증을 통과하지 못한 Training 결과는 배포하지 않는다(자기 참조 승인 금지 — Training을 수행한 모델이 스스로 결과를 승인할 수 없음)
- Production Observation으로 실측 확인하기 전까지 "개선되었다"고 최종 주장하지 않는다(NON_FINAL)
- Re-learn Trigger는 제안일 뿐이며 자동 재학습·자동 배포로 이어지지 않는다

### 금지
- 학습 데이터에 대한 고객 동의·라이선스 확인 없는 학습
- 평가 데이터셋을 학습에 사용해 결과를 부풀리는 행위
- Model/RAG/Prompt/Agent 변경을 코드 Patch처럼 취급해 OImprovement의 최소 변경 원칙을 우회
- Production 배포 후 Observation 생략

## 8. OEvidence
- Immutable Evidence Metadata
- Artifact Hash
- Policy Digest
- Environment Manifest
- Tool Version
- Input/Output Digest
- Finding/Decision Link
- Review Receipt
- Verification Receipt
- Improvement Receipt
- Delivery Receipt
- Retention/Deletion Receipt
- Evidence Schema Version 결속: 모든 Evidence Metadata는 schema_version을 가지며, 계약된 보존기간 동안에는 과거 schema_version의 Evidence도 검증할 수 있는 도구를 유지한다(하위호환 검증도구 없는 Schema 폐기 금지)
- 정기 Evidence 재현성 감사: 표본 추출한 과거 Evidence의 입력·정책·도구 버전으로 재실행해 동일 판정이 재현되는지 주기적으로 확인하고, [01_BUSINESS_PRODUCT_SERVICE_PLAN.md](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) KPI "Evidence 재현 성공률"의 실측 근거로 삼는다(FR-COM-005가 실제로 지켜지고 있는지 스스로 검증)

## 9. OGit
- Workspace Cleanliness 검사
- Worktree 생성·폐기
- Branch Naming
- Diff Limit 및 Forbidden Path
- Commit Message 생성
- Push 승인
- Draft PR 생성
- 지원 Git Provider: GitHub, GitLab(SaaS/Self-managed), Bitbucket, 온프레미스 Git(SSH) — Provider별 OAuth App 또는 PAT 인증, Enterprise는 GitHub/GitLab App 설치형 인증 우선
- 지원 CI Provider: GitHub Actions, GitLab CI, Jenkins — Webhook 우선, 미지원 환경은 Polling으로 대체
- CI 상태 회수
- Review Comment 수집
- Merge 권고: `contracts/git-change-set.v1.schema.json`은 `merge_state`를 항상 `"PROHIBITED"`로 고정한다 — OGit은 권고만 하며 어떤 경로로도 Merge를 실행하지 않는다(계약 수준 강제, 정책 예외 없음)
- Rollback 정보 제공
- Rollback 검증: Rollback 실행 후 대상 Baseline이 실제로 직전 정상 상태(마지막 PASS Verification 시점)와 동일한지 자동 비교하고, 불일치 시 단순 실패가 아닌 Critical Incident로 승격
- Post-merge Incident 대응: Merge 이후 발견된 결함은 Draft PR 흐름과 분리된 Hotfix Worktree로 처리하며, 원인이 된 Merge Commit과 새 MissedFinding/ImprovementRequest를 상호 링크한다
- Multi-PR Integration Risk Scan: 동시에 열려있는 여러 Draft PR이 각각은 통과해도 함께 병합될 때 같은 Component를 상충되게 변경하는지 예측하고, 위험이 있으면 관련 PR 담당자에게 상호 링크된 경고를 표시

## 10. ODelivery
- Web Report
- Program Profile
- Findings CSV/JSON/SARIF(GitHub/GitLab Code Scanning 연동용 표준 포맷)
- SBOM(CycloneDX/SPDX 포맷, 의존성 공급망 투명성 증빙)
- CoverageReport(검토·검증 범위 포함/제외 내역)
- Acceptance Certificate: 발주기관 등 제3자(External Acceptor)가 소스나 Finding 상세 없이도 서명 검증만으로 "이 Baseline이 이 시점에 이런 정책·기준으로 이런 결과를 받았다"를 확인할 수 있는 요약 증명서. 공개 검증 엔드포인트로 인증 없이 서명 유효성만 확인 가능하며, Evidence Pack 전체 열람은 권한자만 가능
- Evidence Pack
- Patch/Diff
- Draft PR
- Executive Summary
- Technical Report
- Deletion Receipt

## 10-1. ONotify
### 책임
Case, Finding, License의 중요 상태 변화를 구독 채널로 능동 통지한다(FR-COM-011).

### 기능
- 통지 대상 이벤트: CaseBlocked, CriticalFindingOpened, VerificationFailed, LicenseExpiringSoon, LicenseSuspended, CreditLow, PatchRegressionFailed, DeletionCompleted
- 채널: Email, Webhook(고객 시스템 연동), VS Code Notification, 관리자 알림함
- Organization/User 단위 채널·심각도 구독 설정(Critical만 즉시, Medium 이하는 일간 요약 등)
- Webhook은 재시도와 서명 검증을 지원하며 실패 시 Dead Letter로 격리
- Notification 발송 자체도 Evidence로 기록해 "통지했다는 사실"을 감사 가능하게 함

### 산출물
NotificationRule, NotificationEvent, NotificationDeliveryReceipt

### 수용기준
- Critical Finding과 CaseBlocked는 발생 후 5분 이내 발송 시도
- Webhook 미수신이 반복되면 관리자 알림함으로 Fallback
- 고객이 구독하지 않은 채널로는 발송하지 않는다(Opt-in 채널만 사용)

## 11. 비기능 요구사항
- NFR-SEC: 저장·전송 암호화, Secret 비노출, 최소권한
- NFR-REL: 멱등성, 재시도, 중복 이벤트 방어
- NFR-PERF: 대규모 Repository의 단계적 분석과 중단·재개. 목표치는 다음을 기본값으로 하며 고객 SLA로 협의 변경 가능하다.
  - Learning: 100만 LOC 기준 8시간 이내 최초 완료, 이후 증분 학습은 변경분 10만 LOC 기준 30분 이내
  - Continuous Review(VS Code Fast Review): Diff 저장 후 5초 이내 1차 결과
  - Verification Scenario 실행: Verification Pack 1개당 평균 15분 이내, 병렬 실행 시 Case당 동시 Scenario 20개 이상 지원
  - Preflight 예상량 응답: 대상 Repository 접근 후 10분 이내
- NFR-AVAIL: SaaS Control Plane 월간 가용성 99.9%, API 요청 기준 Rate Limit과 Tenant별 동시 실행 상한을 적용해 특정 고객의 폭주가 다른 Tenant에 영향을 주지 않는다
- NFR-AUDIT: 모든 권한·실행·변경 감사
- NFR-PORT: SaaS, Local Runtime, 폐쇄망 배포 가능
- NFR-PRIV: 고객별 보존기간과 완전 삭제 증명
- NFR-OBS: Trace, Metric, Structured Log
- NFR-ACCESS: 관리자·개발자·감사자 역할 분리

## 12. 추적성
Requirement → Design Component → Code Module → Test Case → Evidence → Release를 단일 ID 체계로 연결한다.