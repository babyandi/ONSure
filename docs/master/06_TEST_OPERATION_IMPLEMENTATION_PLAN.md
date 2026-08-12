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
- `registered_faults`, `executed_faults`, `valid_non_equivalent_faults`, `detected_faults`, `escaped_faults`, `invalid_or_equivalent_faults`
- `critical_escaped_fault_ids`, `fault_class_coverage`, `detector_pack_digest`, `oracle_pack_digest`, `benchmark_set_digest`, `execution_environment_digest`
Known Critical Seeded Defect Escape가 1건이라도 있으면 해당 Capability의 `QUALIFIED` 발급을 금지한다.

### 13.2 Benchmark Corpus 분리
1. `PUBLIC_REGRESSION`
2. `PRIVATE_QUALIFICATION`
3. `ROTATING_UNSEEN`
4. `NOVEL_COMPOSITION`
AI/학습 계열은 07 §8 Semantic Dataset Separation을 적용한다.

### 13.3 Fault Class Coverage
Functional/Boundary/State, Authentication/Authorization/Tenant Isolation, Data Integrity/Transaction/Consistency, Concurrency/Race/Deadlock/TOCTOU, Timeout/Retry/Resource Exhaustion, Recovery/Restart/Rollback, Security Injection/SSRF/XSS/Command/Path, Supply Chain/Dependency/Artifact Drift, Evidence Tampering/Replay/Substitution, Approval/Authority/Privilege Escalation, AI Prompt/RAG/Tool/Memory/Model Drift, Validation Bypass/Observer Failure/False Assurance를 독립 관리한다.

### 13.4 Validator Mutation Testing
Detector 제거, Severity 하향, Oracle 반전/Skip, NOT_RUN→PASS, Evidence binding 제거, stale receipt 허용, signature 검증 제거, Final eligibility 제거, REJECT approval 소비, same-principal independence, Collector health 제거, Scope Epoch 제거, retry failure history 삭제 mutant를 전용 Negative Fixture로 kill한다.

### 13.5 Cross-Contract Semantic Fixture
FinalApproval REJECT+FinalLock, Candidate eligible=false+PASS, candidate digest mismatch, target mismatch, Run1 PASS+Run2 FAIL, Scope Epoch 불일치, CANCELLED Evidence, purpose/type mismatch, expires<=approved, duplicate key/same-principal independence, RiskScore 저장값 불일치를 모두 BLOCK한다.

### 13.6 Atomic Validation Snapshot
서로 다른 Run의 좋은 결과만 조립하는 cross-run mixing을 금지하고 동일 target/scope/requirement/policy/config generation의 결과만 Final Snapshot으로 묶는다.

### 13.7~13.20 공통 Meta Gate
Flakiness/Retry history, Fixture precondition proof, Collector failure, crash consistency, Final TOCTOU, isolation contamination, operational equivalence, mock fidelity, anti-evasion, resource exhaustion, historical revalidation, 운영지표, P0 구현순서 및 qualification 수용기준은 기존 설계대로 유지한다.

## 14. Runtime·Composition·Certificate·Scale 시험 정본 흡수 (2026-08-12)
본 절은 `semantic-assurance/33_RUNTIME_COMPOSITION_CERTIFICATE_TEST_OPERATION_EXTENSION.md`의 시험 의미를 본 정본에 직접 흡수한다. 세부 fixture ID와 향후 machine schema는 companion을 참조하지만, 아래 시험군은 이제 본 문서의 필수 시험범위다.

### 14.1 Deployment / Currentness
- mutable tag 동일·digest 상이 배포
- rolling update old/new revision 혼재
- canary 일부 population PASS의 전체 승격 시도
- multi-region 중 일부 region STALE/UNKNOWN
- source 동일·config/feature flag 변경
- model/provider/prompt/RAG silent drift
- rollback 후 과거 FinalLock 자동복원 시도
- runtime observer 장애 중 CURRENT 발급 시도
- verified/deployed/running digest mismatch

기대결과: `CURRENT`는 verified→deployed→running chain과 current policy/qualification/authority가 모두 닫힐 때만 허용한다. 일부 혼재·미관측·stale 상태는 product CURRENT를 만들지 못한다.

### 14.2 Product Composition / Evidence Graph
- Critical HARD child HOLD/FAIL/UNKNOWN 숨김
- Critical child를 SOFT로 위장
- N/A proof 없는 denominator 제외
- conflicting PASS/FAIL 중 PASS만 선택
- supersession 없는 latest-wins
- retry PASS로 이전 critical failure 삭제
- cross-tenant graph edge
- DERIVED_FROM/SUPERSEDES cycle
- graph head 이후 child result 변경
- product population 일부 누락

기대결과: composition은 exact subject/edge population과 graph head에서 결정론적으로 재현되며 모든 ceiling reason을 graph path로 설명해야 한다.

### 14.3 Certificate / Offline
- expired/stale/revoked certificate를 CURRENT로 표시
- stale revocation snapshot으로 offline unlimited PASS
- revoked key certificate 발급/검증
- QR/공개 payload에 secret/raw evidence 노출
- exclusion/limitation 누락
- historical signature valid를 current assurance로 표현
- reconnect 후 remote revocation과 offline result 충돌

기대결과: 발급 당시 사실과 현재 validity를 분리하고 online/offline/historical verification mode를 명시한다.

### 14.4 Enterprise Authority
- delegation이 parent grant보다 넓음
- 같은 principal의 두 key를 four-eyes로 계산
- expired/revoked grant effect-time 사용
- break-glass로 Final PASS/Certificate strength 상승
- legal hold를 evidence freshness 연장 근거로 사용
- authority snapshot은 valid하나 effect 시점에 revoke된 경우

기대결과: operation authority는 effect-time principal/resource/purpose/tenant에 결속되고 emergency flow는 assurance strength를 올리지 않는다.

### 14.5 Scale / Distributed Work
- duplicate delivery double count
- stale lease worker late commit
- partition omission
- scheduling 순서에 따른 aggregate digest 변화
- cost/resource exhaustion으로 scope 축소 후 PASS
- cross-tenant WorkUnit 혼합
- coordinator restart 후 stale authority resurrection

기대결과: logical effect/receipt commitment는 idempotent하고 exact denominator 및 deterministic aggregation이 유지된다.

### 14.6 Plugin / Adapter Trust
- unsigned/revoked publisher plugin
- manifest보다 넓은 privilege 사용
- parser가 unsupported syntax를 조용히 drop
- plugin update 후 qualification 재사용
- plugin과 independent oracle가 같은 publisher/admin chain
- sandbox escape

### 14.7 AI Runtime Assurance
- provider alias의 silent model replacement
- dynamic system fragment가 prompt digest에서 빠짐
- RAG corpus/index/embedding 변경 미추적
- undeclared tool 사용 및 tool parameter 권한상승
- cross-tenant memory retrieval
- favorable seed/sample만 선택
- judge model과 target model 공통 blind spot
- multi-agent majority를 ground truth로 오인

### 14.8 ONSure Meta-Assurance
- validator/oracle/adapter 변경 후 과거 release qualification 재사용
- self-test만으로 ONSure release QUALIFIED
- target archetype 미검증인데 global QUALIFIED
- hidden benchmark contamination
- MissedFinding proving blind spot 발생 후 requalification 누락

## 15. 통합 완료조건
`FR-META-044~060` 및 29~69 설계에서 파생된 P0 test obligation은 본 문서 §14 또는 기존 §13에 최소 한 항목으로 연결되어야 한다. 세부 fixture가 아직 없는 항목은 `TEST_DESIGNED_NOT_EXECUTED`로 관리하며, 실제 실행 전 PASS/QUALIFIED를 주장하지 않는다.
