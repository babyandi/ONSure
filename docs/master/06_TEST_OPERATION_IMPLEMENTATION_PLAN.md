# ONSure 시험·운영·구현 계획서

## 1. 구현 원칙
- 문서 기준선 없이 코드부터 작성하지 않는다.
- 모든 Story는 Requirement, Acceptance, Test, Evidence를 가진다.
- 코드 작성 후 작성자가 아닌 독립 리뷰를 수행한다.
- 구현 완료와 제품 검증 완료를 분리한다.
- 완료된 시험은 Baseline이 바뀌지 않은 한 불필요하게 반복하지 않는다.
- BLOCKED는 격리하고 다른 Lane을 진행한다.

## 1-1. 실제 Workflow Operation Registry와의 관계
`contracts/workflow-operation-registry.v1.json`이 현재 등록된 45개 실행 가능 Operation의 단일 권위다(`project.*`, `program.learn`, `plan.*`, `validation.run`, `patch.*`, `improvement.prove`, `knowledge.separate`, `job.*`, `git.commit`/`git.draft-pr`, `license.*`, `case.*`). 모든 Operation은 CLI·Local Authenticated API·VS Code 3개 공통 표면(generic_surfaces)에 동일하게 도달해야 한다.

이 설계서(특히 [04_ARCHITECTURE_DATA_API_OLICENSE.md](04_ARCHITECTURE_DATA_API_OLICENSE.md) §7)가 제안한 API 중 Notification, Portfolio, PolicyPack, Acceptance Certificate, SBOM, Mutation Testing, Cross-Model Verification, Blast Radius, Coverage Report, RiskScore 관련 엔드포인트는 이 45개 목록에 없다 — 전부 `DESIGN_ONLY`이며, 구현 순서에 넣기 전에 `workflow-operation-registry.v1.json`에 Operation을 먼저 등록해야 한다(§6 변경 규칙: Requirement→Design→Contract/Schema→Code→Test→Evidence→Status 연결 필수).

## 2. 구현 Lane
### L0 Contract and Foundation
- ID 체계
- Product/Plan/Feature Catalog
- Core Schema
- Event Envelope
- Error Code
- Evidence Receipt
- Repository 구조와 개발환경

### L1 OLicense Integration
- ONSURE ProductCode
- Web Case License
- VS Code Subscription
- Entitlement Snapshot
- Seat/Device/System/Program Binding
- Credit Reserve/Commit/Release
- Revocation/Offline Grace

### L2 Commerce and Web Case
- Preflight
- Quote
- Order/Payment/Refund
- Case State Machine
- Upload/Git Binding
- Delivery Center

### L3 Engineering Core
- OLearning
- OPlanning
- OReview
- OVerification
- OImprovement
- OEvidence

### L4 VS Code and Git
- Extension Shell
- Chat Modes
- Profile/Review/Verification UI
- Local Runtime
- Worktree/Branch/Commit/Push/Draft PR
- CI Feedback

### L5 Security and Operations
- Tenant Isolation
- Sandbox
- Secret/Log Redaction
- Retention/Deletion
- Observability
- Runbook

## 3. Epic 구조
### EPIC-01 Learn
Repository 입력부터 Program Profile과 Learning Receipt까지.

### EPIC-02 Review
Diff 입력부터 독립 Review와 Merge Decision까지.

### EPIC-03 Verify
Requirement/Policy 기반 Scenario 실행과 Verification Receipt까지.

### EPIC-04 Improve
Finding 선택부터 Patch, Regression, Draft PR까지.

### EPIC-05 Web Commerce
Preflight부터 결제, License 발급, Case 완료까지.

### EPIC-06 VS Code Continuous
로그인부터 증분 학습, 리뷰, 검증, 개선, Git까지.

### EPIC-07 OLicense
발급, 활성화, 소비, 정지, 만료, 폐기, Offline까지.

### EPIC-08 OMemory
Fix/Failure Pattern 추출부터 Component Signature 매칭, MissedFinding 재귀학습 루프와 Golden Fixture 회귀 검증까지.

### EPIC-09 OTraining (Target AI Auto-Learning)
Decide(Improve vs Train)부터 Training Plan, 데이터 품질검사, Training Run, 독립 재검증, Deployment 승인, Production Observation, Re-learn Trigger까지. 1단계 출시는 RAG 재인덱싱·Prompt 개선으로 한정하고 GPU Model Fine-tuning은 이후 단계([01_BUSINESS_PRODUCT_SERVICE_PLAN.md](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) §11-2).

## 4. Story 완료조건
- 요구사항 ID 연결
- 설계 또는 ADR 연결
- 정상·예외·부정 Test
- 코드리뷰 완료
- 보안 영향 검토
- Observability 추가
- Evidence 생성
- 문서 갱신
- Critical/High 0건

## 5. 시험 체계
### Unit
Parser, Rule, Meter, State Transition, Hash, Token Validation.

### Contract
OpenAPI, Event Schema, OLicense Token, Payment Webhook, Git Provider, SARIF/SBOM(CycloneDX·SPDX) 출력 스키마 유효성.

### Integration
DB, Queue, Storage, Sandbox, OLicense, Payment, Git, Model Provider.

### E2E Web
1. Learn 정상 Case
2. Verify 정상 Case
3. Learn & Verify 정상 Case
4. Improve & Re-verify
5. 결제 실패와 재시도
6. 중복 Webhook
7. 환불과 License 정지
8. Baseline 변경계약
9. Source 삭제와 Deletion Receipt

### E2E VS Code
1. 로그인·License 활성화
2. Repository 학습
3. Diff Continuous Review
4. Critical Finding 차단
5. Finding 기반 Patch
6. Worktree 격리
7. Regression
8. Commit·Push·Draft PR
9. CI 실패 회수
10. Offline Grace와 Reconnect

### OReview Fixture
- 실제 Bug
- False Positive 유도
- Architecture 위반
- Policy 우회
- Secret 노출
- Prompt Injection
- RAG Tenant 혼합
- 취약 Test
- 실패 Test 삭제 시도
- 요구사항 미연결 고아 코드(역방향 Traceability 탐지 검증)
- Docstring/주석과 실제 동작이 다른 코드(Doc-Code Consistency 검증)
- 커밋/PR 설명에서 거짓 또는 과장된 완료·테스트통과 주장(Self-Claim Verification 검증)
- Cross-Model 1차/2차 판정 고의 불일치 케이스(자동 승격 대신 Human 회부되는지 검증)
- Cross-Program Breaking Change: 한 Program의 Interface 변경이 다른 Program에 미치는 영향 탐지 검증
- Copyleft License 의존성이 상용 폐쇄소스 배포 정책과 충돌하는 케이스(PolicyPack 허용/차단 목록 검증)
- 동시에 열려있는 두 PR이 같은 Component를 상충되게 변경하는 Multi-PR Integration Risk 케이스
- 기존 테스트가 전무한 Repository에서 OPlanning이 최소 Smoke Test를 제안·실행하는지, PASS 표기가 구분되는지 검증
- Acceptance Certificate 위변조 시도(서명 불일치, 발급 후 Case 내용 변경)가 공개 검증 엔드포인트에서 거부되는지 확인

### Mutation Testing / Blast Radius Fixture
- 강한 Assertion과 약한 Assertion을 가진 동일 기능 Pair(Mutation Score 차이 검증)
- Mutation 주입 후에도 Test가 모두 통과하는 케이스(테스트 실효성 부재 탐지)
- 다중 Program에 영향을 주는 Patch의 Blast Radius 예측 정확도(DRY_RUN 예측 vs 실제 적용 결과 비교)
- 기능은 고쳤지만 성능이 저하되는 Patch(BehaviorDiffReport의 REGRESSION_FAILED 자동판정 검증)

### OTraining Fixture
- 편향·중복·유출·오염(Poisoning)된 학습 데이터가 Training 시작 전에 차단되는지
- 평가 데이터셋을 학습 데이터셋으로 잘못 재사용하는 시도(결과 부풀리기) 탐지
- Training을 수행한 모델이 자기 결과를 스스로 배포 승인하려는 경로 차단(자기 참조 승인 금지)
- Before/After 성능 비교 없이 Deployment 승인을 요청하는 케이스 거부
- Production Observation에서 성능 저하를 감지해 RelearnTrigger가 발생하지만 자동 재학습·자동 배포로 이어지지 않는지
- 승인 없는 GPU Model Fine-tuning 요청이 1단계 출시 범위(RAG·Prompt만 허용) 밖임을 이유로 거부되는지

### OLicense Fixture
- 만료
- Revocation
- 잘못된 Audience
- 서명 Key 회전
- Clock Rollback
- Credit 이중 Commit
- Reserve Timeout
- Offline Usage 중복 동기화
- 실행 도중 Credit 소진 → Checkpoint까지 진행 후 BLOCKED 전이 → 추가 승인 후 Resume 또는 유예기간 초과 시 CANCELLED

### OMemory / 재귀학습 Fixture
- 동일 Component Signature에 대한 Pattern 매칭 정확도
- 3회 이상 False Positive Pattern의 자동 강등
- 고객 식별정보가 포함된 Pattern의 공유 Corpus 승격 차단(Anonymization 실패 케이스)
- MissedFinding 등록 → RCA → Rule 개정 → Golden Fixture 전체 회귀 → Promote 전 구간 통과
- Rule 개정 후 기존 Golden Fixture에서 신규 False Positive가 급증하는 회귀 실패 케이스
- 자기 참조 승인 시도(개정을 제안한 Agent가 스스로 회귀 통과를 승인하려는 경로) 차단

### AI/바이브 코딩 생성 코드 Fixture
[03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) §4-1의 점검표를 기준으로 다음 유형의 실제 및 합성 사례를 포함한다.
- Hallucinated Dependency
- 세션 간 구조 불일치(동일 기능의 반복 재구현)
- 과잉 생성과 요구사항 초과 구현
- 무의미한 예외 처리(Silent Error Swallowing)
- 테스트 없는 대량 커밋
- Commit/PR 설명과 실제 Diff 불일치
- 하드코딩된 System Prompt, 과도한 Tool 권한

## 6. 비기능 시험
- 대규모 Repository 학습 성능: [02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) NFR-PERF 목표치 기준 PASS/FAIL 판정
- Tenant별 Rate Limit과 동시 실행 상한 초과 시 Fail-closed 동작
- 동시 Case와 동시 VS Code 실행
- Worker Crash 복구
- Queue 중복전달
- DB Failover
- Object Storage 장애
- 모델 Provider Timeout과 비용 폭주 방어
- Tenant 침범 시도
- Sandbox 격리 우회·탈출 시도(Network Allowlist 우회, 잔존 Volume 접근)
- 데이터 삭제 완전성

## 7. 코드리뷰 절차
1. Self Review
2. Automated OReview
3. Independent OReview
4. Human Review
5. Review Finding 해결
6. Re-review
7. Merge Readiness

Critical 또는 High가 존재하면 Merge Ready가 될 수 없다. Accepted Risk는 권한자, 사유, 만료일, 보완통제를 기록한다.

## 8. 출시 검증
### Alpha
내부 Fixture와 제한 Repository. 데이터 보존과 외부 배포 금지.

### Beta
선정 고객 Web Case와 VS Code Pilot. 전문가 검토 필수.

### Release Candidate
전체 E2E 연속 2회, 성능·보안·복구, OLicense, 결제·환불, 삭제 시험 통과.

### General Availability
SLA, Support, Billing, Security 문서, Incident Runbook, Rollback 준비 완료.

## 9. 운영 Runbook
- Case Blocked 처리
- License 발급 실패
- Payment/Refund 불일치
- Credit 분쟁
- Worker 장애
- 모델 Provider 장애
- Git 권한 만료
- Merge 이후 결함 발견(Post-merge Incident, Hotfix Worktree로 분리 처리)
- Source 유출 의심
- Tenant Isolation 사고
- Evidence 손상
- 삭제 실패
- 재귀학습 개정 회귀 실패(Rule/Pattern 개정이 False Positive를 급증시킨 경우 즉시 이전 Rule Pack Digest로 Rollback)
- AI Agent 이상행동(신규, NIST AI RMF MANAGE 기능 대조로 2026-08-09 발견): Agent가 [07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md](07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md) §3.1 권한표를 벗어난 Tool 호출을 시도하거나, §3.2 Plan-Act-Observe 루프의 반복·비용 상한(Max Turn/Token/Credit)에 비정상적으로 자주 도달하는 경우. 상한 도달 자체는 이미 정상 Stop Condition으로 처리되지만(강제종료 후 INCONCLUSIVE 표시), 짧은 기간 내 반복 발생하면 개별 실행 실패가 아니라 이 사고 유형으로 승격해 Containment(해당 Agent Credential 즉시 정지)와 RCA를 별도로 남긴다

사고는 Severity, Owner, Timeline, Customer Communication, Containment, RCA, Corrective Action, Regression Test를 남긴다.

Customer Communication은 공개 Status Page(구성요소별 가동 상태, 진행 중 사고 게시)와 영향받은 Organization 대상 개별 통지([02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) §10-1 ONotify)로 이원화한다. Critical 사고는 Status Page 게시와 개별 통지를 15분 이내 동시에 시작한다.

## 10. 모니터링
- Case 상태 체류시간
- Queue Lag
- Worker 성공률
- Model 비용과 Token
- Credit Reserve 잔류
- License Validate 실패
- Review Finding 추세
- Verification Flaky 비율
- Patch 회귀 실패율
- Evidence Seal 실패
- 삭제 SLA 초과
- MissedFinding 발생률과 Detection Recall 추세
- KnowledgePattern 강등률과 공유 Corpus 승격률
- Confidence Calibration 이탈도(구간별 실측 정확도와 표시 Confidence의 괴리)
- Reviewer(모델/Human/Professional)별 정확도와 기준 미달 배정 제한 발생 건수
- Rollback 검증 실패율
- AI 구성 Drift(권한 확대) 탐지 건수
- 정기 Evidence 재현성 감사 결과(재현 성공률, ReproducibilityAuditSample 기준)

## 11. 우선 구현 순서
1. Schema, Receipt, OLicense 계약
2. Local Runtime과 OLearning 최소기능
3. OReview 코드리뷰 최소기능
4. OVerification 실행 Harness
5. Web Learn & Verify Case
6. OImprovement Worktree Patch
7. OMemory 최소기능(Pattern 추출·매칭, MissedFinding 등록)
8. VS Code Developer
9. Payment/Refund와 운영화
10. Team/Enterprise 기능
11. OMemory 재귀학습 루프 자동화(RCA→Rule 개정→Golden Fixture 회귀→Promote), `contracts/learning-to-application-pipeline.v1.json`의 `VALIDATION_PACK_APPLY` 경로로 `APPLIED_LOCKED` 최소 1건 달성
12. OTraining(Target AI Auto-Learning) — 11번이 `APPLIED_LOCKED` 1건 이상을 실제로 달성하기 전까지 착수하지 않는다(`TARGET_PRODUCT_APPLY: mvp_allowed=false` 하드 게이트, [00_ONSURE_MASTER_DESIGN_SET.md §2-2](00_ONSURE_MASTER_DESIGN_SET.md))

## 12. 최종 수용기준
- 문서와 코드 Traceability 확보
- Web와 VS Code 실제 Full-Chain 각각 연속 2회 PASS
- OLicense 전 수명주기 PASS
- OReview 독립 검토 PASS
- Critical/High 연속 2회 0건
- Rollback과 Recovery PASS
- 고객 데이터 삭제 증명 PASS
- OMemory 재귀학습 루프가 의도적으로 주입한 MissedFinding 사례를 RCA→개정→회귀 통과까지 완결 처리
- Final Evidence Pack 봉인

## 13. Meta-Validation Qualification·시험·운영 계획 (신규, 2026-08-09)
이 절은 대상 제품 시험과 별개로 **ONSure 자체가 결함을 실제로 잡을 능력이 있는지, 잘못된 PASS를 낼 수 없는지**를 검증한다. 현재 `contracts/omission-failure-injection-counts.v1.json`의 118건은 등록된 주입 케이스 수이며, 실행·탐지·escape가 입증된 수가 아니다. 따라서 개수 집계에서 실제 탐지자격 증명으로 전환한다.

### 13.1 Detector Qualification Report
모든 seeded fault corpus 실행마다 다음을 기록한다.

- `registered_faults`
- `executed_faults`
- `valid_non_equivalent_faults`
- `detected_faults`
- `escaped_faults`
- `invalid_or_equivalent_faults`
- `critical_escaped_fault_ids`
- `fault_class_coverage`
- `detector_pack_digest`
- `oracle_pack_digest`
- `benchmark_set_digest`
- `execution_environment_digest`

Known Critical Seeded Defect Escape가 1건이라도 있으면 해당 Capability의 `QUALIFIED` 발급을 금지한다.

### 13.2 Benchmark Corpus 분리
Corpus는 최소 다음 네 세트로 분리한다.

1. `PUBLIC_REGRESSION`: 개발자가 볼 수 있는 회귀세트
2. `PRIVATE_QUALIFICATION`: 독립 검증조직만 정답 접근
3. `ROTATING_UNSEEN`: 주기적으로 교체하는 미공개 변형
4. `NOVEL_COMPOSITION`: 기존 단일 결함을 새로운 조합으로 합성한 케이스

AI/학습 계열은 [07 §8](07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md)의 Semantic Dataset Separation을 적용한다. Hidden 결과를 반복적으로 조회해 Rule을 튜닝하는 행위를 금지한다.

### 13.3 Fault Class Coverage
최소 다음 defect class를 독립적으로 관리한다.
- Functional/Boundary/State
- Authentication/Authorization/Tenant Isolation
- Data Integrity/Transaction/Consistency
- Concurrency/Race/Deadlock/TOCTOU
- Timeout/Retry/Resource Exhaustion
- Recovery/Restart/Rollback
- Security Injection/SSRF/XSS/Command/Path
- Supply Chain/Dependency/Artifact Drift
- Evidence Tampering/Replay/Substitution
- Approval/Authority/Privilege Escalation
- AI Prompt/RAG/Tool/Memory/Model Drift
- Validation Bypass/Observer Failure/False Assurance

전체 평균 Recall이 높더라도 Critical class의 Recall 저하는 허용하지 않는다.

### 13.4 Validator Mutation Testing
대상 제품이 아니라 ONSure 자신의 검증기 코드를 고의로 훼손하여 Meta-Suite가 이를 탐지하는지 확인한다. 최소 Mutant:

- Detector 하나 제거
- Severity CRITICAL→MEDIUM 강제 하향
- Oracle 결과 반전/Skip
- NOT_RUN을 PASS로 변환
- Evidence target binding 제거
- stale receipt 허용
- receipt hash/signature verification 비활성화
- Final Candidate eligibility 검사 제거
- REJECT approval을 APPROVE처럼 소비
- 동일 principal의 두 key를 독립 reviewer로 간주
- Collector health 확인 제거
- Scope Epoch 비교 제거
- retry 실패 이력 삭제

각 Mutant는 하나 이상의 전용 Negative Fixture에 의해 반드시 kill되어야 한다.

### 13.5 Cross-Contract Semantic Fixture
개별 JSON Schema가 valid여도 조합이 잘못된 사례를 고정 Fixture로 등록한다.

- FinalApproval `REJECT` + FinalLock 요청 → BLOCK
- FinalCandidate `eligible=false` + `decision=PASS` → BLOCK
- Candidate digest mismatch → BLOCK
- FinalLock target ≠ Approval target → BLOCK
- Run1 PASS + Run2 FAIL → BLOCK
- Run1/Run2 Scope Epoch 불일치 → BLOCK
- CANCELLED run Evidence 사용 → BLOCK
- ServiceVerification `PAYMENT_VERIFICATION` + `verification_type=DELETION` → BLOCK
- `expires_at <= approved_at` → BLOCK
- duplicate key_id / same principal independent roles → BLOCK
- RiskScore 저장값 ≠ raw finding 독립 재계산값 → BLOCK

### 13.6 Atomic Validation Snapshot 시험
다음 Cross-Run Mixing 공격을 반드시 시험한다.

- Run1: Security PASS / Performance FAIL
- Run2: Security FAIL / Performance PASS
- Aggregator가 Run1 Security + Run2 Performance를 조립해 전체 PASS하려는 시도

동일 `target_manifest_digest`, `scope_epoch`, `requirement_epoch`, `policy_digest`, `validation_generation`에 속한 결과만 하나의 Final Snapshot으로 묶을 수 있어야 한다.

### 13.7 Flakiness·Retry·통계 시험
- 최초 Attempt와 Retry 결과를 모두 보존한다.
- `FAIL→PASS`는 안정 PASS가 아니라 Flaky 신호로 집계한다.
- 비결정 시스템은 고정 2회가 아니라 위험 기반 sample size를 사용한다.
- 반복 결과에는 `sample_size`, `observed_failures`, `failure_rate`, `confidence_bound`, `random_seed/generator_version`을 기록한다.
- Fuzz/Property-based test는 Replay Seed Set과 Fresh Exploration Seed Set을 분리한다.

### 13.8 Fixture Precondition Proof
Negative Fixture는 공격 전 선행조건이 실제로 성립함을 증명한다. 예:
- actor가 권한 P를 보유하지 않음
- 대상 resource가 다른 tenant 소유
- approval이 없음/만료됨
- token이 실제 expired 상태

Positive counterpart도 함께 실행해 과도한 fail-close로 정상권리가 사라지지 않았는지 확인한다.

### 13.9 Observability / Collector Failure 시험
- Log/Trace/Network/DB/Authority Collector를 실행 중 강제 종료한다.
- Collector가 10%만 동작한 상태에서 "문제 없음" 결론이 생성되지 않아야 한다.
- `collector_started`, `collector_healthy`, `collector_complete`, `collector_digest`가 없으면 해당 관측에 의존하는 부재 주장(No leak, No unauthorized access 등)을 PROVEN으로 만들지 않는다.

### 13.10 Crash Consistency / Evidence Transactionality
검증 프로세스를 Evidence 기록 각 단계에서 강제 종료한다.

- result 생성 직후
- receipt 생성 직후
- ledger append 전/후
- chain head 갱신 전/후
- approval consume 직후
- Final Lock 기록 직전/직후

미완료 기록은 `ABORTED_UNTRUSTED` 또는 INCONCLUSIVE로 복구되어야 하며 파일 일부 존재만으로 PASS를 복원하면 실패다. Final Lock은 재시도해도 동일 candidate/approval에 하나의 active lock만 생성해야 한다.

### 13.11 Final Freshness / TOCTOU 시험
다음 이벤트를 Candidate와 Lock 사이에 주입한다.
- 새 Critical Finding
- Policy/Rule Pack 변경
- Scope/Requirement Epoch 변경
- Dependency/CVE 정보 변경
- Target Artifact 변경
- Approval 만료/Revocation

Final Freshness Barrier가 하나라도 탐지하면 Lock을 거부해야 한다.

### 13.12 Validation Isolation / Contamination
- 같은 Target의 Run A/B를 동시에 실행하고 한쪽 상태 변경이 다른쪽에 관찰되지 않는지 확인한다.
- DB/cache/queue/workspace의 `pre_state_digest`, `post_state_digest`, `cleanup_result`를 검증한다.
- Test 순서를 무작위화하여 순서 의존성을 측정한다.
- Instrumented/Uninstrumented 실행을 비교해 Observer Effect를 확인한다.

### 13.13 Operational Equivalence 시험
검증된 Artifact와 실제 배포 Artifact의 digest가 동일한지 확인한다. 검증환경과 운영환경의 차이를 `MATCH|MATERIAL_DIFFERENCE|UNKNOWN`으로 분류하고 MATERIAL/UNKNOWN 차이가 있는 경우 운영환경 전체 VALIDATED를 금지한다.

검증 항목:
- OS/CPU/runtime/DB version
- dependency artifact digest와 provenance
- runtime config/feature flags
- model/prompt/RAG corpus
- network topology/external service contract
- deployed artifact digest

### 13.14 Mock Fidelity / Real Trace
Mock 기반 검증에는 API schema뿐 아니라 error/timing/retry/order/side-effect fidelity를 측정한다. 고위험 외부연계는 가능한 경우 Recorded Real Trace 또는 Production Shadow로 보완한다.

### 13.15 Anti-Evasion 시험
대상이 검증환경을 인식해 다르게 행동하는 경우를 시험한다.
- validator marker 변화
- instrumented vs minimally-instrumented
- Sandbox/realistic simulation 차이
- AI에 "safety test"임을 알리는/알리지 않는 자연스러운 시나리오 비교

### 13.16 Validation Resource Exhaustion 시험
대규모 생성파일·의존그래프·무한상태공간으로 검증 예산을 고갈시킨다. 예산 소진 때문에 Scope가 자동 축소되어 PASS하면 실패다. 반드시 `VALIDATION_BUDGET_EXHAUSTED`/HOLD/PARTIAL로 승격하고 CoverageReport에 영향을 반영한다.

### 13.17 Historical Revalidation 시험
새 MissedFinding 또는 새 Detector가 승격되면 과거 Validation을 영향분석해 `SAFE|REASSESSMENT_REQUIRED|STALE`로 분류하는지 시험한다. 과거 PASS를 변경 없이 유지하는 것은 실패다.

### 13.18 운영 모니터링 추가지표
- Critical Seeded Defect Escape Rate
- Capability별 Recall/Precision/Specificity/False Alarm Rate
- Cross-contract invariant violation count
- Collector incomplete rate
- Final Freshness Barrier block count
- Stale/Revoked Certificate count
- Cross-run evidence mixing rejection count
- Validator Mutation kill rate
- Hidden/OOD benchmark 성능
- Critical Recall regression count

### 13.19 구현 우선순위(P0)
다른 Meta 기능보다 먼저 다음을 구현한다.
1. ValidationTargetManifest + Scope/Requirement Epoch
2. Evidence Target Binding
3. Cross-Contract Invariant Engine
4. Final Claim/Lineage Reconstructor
5. Validator Capability + Observability Qualification
6. Atomic Validation Snapshot
7. Final Freshness Barrier + Assurance Revocation
8. Detector Qualification Report + Hidden Benchmark
9. Validator Mutation Suite
10. Historical Revalidation

### 13.20 최종 수용기준 보강
기존 §12에 더해 다음을 모두 만족해야 ONSure 자체가 고신뢰 검증기로 Qualification된다.
- 등록된 fault 수가 아니라 실제 `executed/detected/escaped` 결과가 존재
- Critical seeded defect escape 0
- Cross-contract semantic negative fixture 100% 차단
- Final Snapshot cross-run mixing 차단
- Validator mutation critical mutant kill 100%
- Collector failure가 PASS로 세탁되지 않음
- Crash 중간 Evidence가 Final로 승격되지 않음
- Rule/Oracle 변경 후 Critical Recall 회귀 0
- Hidden/OOD benchmark 결과 공개 Golden과 별도 유지
- 검증 Artifact와 배포 Artifact 동일성 검증
- Final Freshness Barrier PASS 후에만 Final Lock 가능
