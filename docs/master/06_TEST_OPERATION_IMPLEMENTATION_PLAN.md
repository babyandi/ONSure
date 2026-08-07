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
11. OMemory 재귀학습 루프 자동화(RCA→Rule 개정→Golden Fixture 회귀→Promote)

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