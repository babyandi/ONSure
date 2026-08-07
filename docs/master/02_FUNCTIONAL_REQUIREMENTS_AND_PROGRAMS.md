# ONSure 기능 요구사항 및 프로그램 명세

## 1. Actor
- Customer Owner: 계약, 결제, 범위 승인, 최종 인수
- Customer Admin: 조직, 사용자, 시스템, 정책 관리
- Developer: VS Code에서 학습·리뷰·검증·개선 수행
- Reviewer: Finding과 Patch 승인 또는 반려
- Professional Reviewer: 유료 전문가 검토
- ONSure Operator: Case와 실행환경 운영
- Security Auditor: Evidence와 감사로그 열람
- OLicense: 라이선스·Entitlement·Credit 권위
- Payment Provider: 결제 승인·취소·환불 이벤트 제공

## 2. 공통 기능 요구사항
- FR-COM-001 모든 실행은 Organization, Product, Channel, License, System, Program, Baseline에 결속한다.
- FR-COM-002 고객 데이터는 Tenant별 논리·물리 격리 정책을 적용한다.
- FR-COM-003 실행 전 Entitlement, Credit, Feature, Validity를 확인한다.
- FR-COM-004 모든 결과는 정책 버전, 입력 Hash, 실행환경, 도구 버전, 결과 Hash를 기록한다.
- FR-COM-005 동일 입력·정책·도구 버전은 재현 가능한 판정 구조를 가져야 한다.
- FR-COM-006 ONSure 내부 오류에 의한 실패는 고객 사용량으로 확정하지 않는다.
- FR-COM-007 모든 자동 Patch는 별도 Worktree와 Branch에서 수행한다.
- FR-COM-008 고객 승인 전 Main Branch 직접 변경을 금지한다.
- FR-COM-009 Organization은 자신의 Pattern/Fixture가 익명화된 공유 Corpus에 기여할지 여부를 명시적으로 선택(Opt-in/Opt-out)하며 기본값은 Opt-out이다. 규제산업 Enterprise Edition은 공유 Corpus 기여를 계약으로 원천 차단할 수 있다.
- FR-COM-010 Customer Admin은 Organization에 속한 모든 System/Program의 상태·위험·사용량을 통합한 Portfolio 조회 기능을 제공받는다.
- FR-COM-011 Case/Finding/License의 중요 상태 변화는 채널(Email, Webhook, VS Code, 관리자 알림함)로 능동 통지되어야 하며, 고객이 Dashboard를 확인하지 않아도 인지할 수 있어야 한다.
- FR-COM-012 Seat는 담당자 변경 시 Customer Admin이 즉시 회수·재배정할 수 있으며, 회수된 Seat의 이전 담당자 Access Token은 즉시 무효화한다.

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
- 예상 Learning Unit, Credit, 시간 계산
- 실행 의존성 및 Stop Condition 정의
- 사용자 승인용 Plan Diff 제공

### 산출물
ExecutionPlan, ScopeManifest, ScenarioPlan, ResourceEstimate, ApprovalReceipt

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
APPROVE, COMMENT, REQUEST_CHANGE, REJECT, NOT_APPLICABLE, INCONCLUSIVE

### 수용기준
- Finding마다 파일·라인 또는 구성요소·근거·정책·영향·제안 포함
- 중복 Finding 통합
- 추측성 Finding은 Confidence와 확인방법 포함
- Critical/High Finding은 근거 없는 자동 승인 금지
- Critical Finding은 원 구현/1차 판정과 다른 모델 계열의 Cross-Model Verification을 거친 뒤에만 최종 확정한다([03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) §10-1)
- AI 생성 비중이 높은 Component에서 Critical/High 판정의 Confidence가 조직 임계치 미만이면 자동 승인·자동 반려 없이 Human 또는 Professional Reviewer에게 강제 회부한다(OVerification의 동급 판정에도 동일 원칙 적용)

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
PASS는 실행 증거 없이 생성할 수 없다. BLOCKED와 NOT_RUN은 FAIL과 분리한다.

## 7. OImprovement
### 기능
- 승인된 Finding만 입력
- Root Cause 후보와 영향범위 생성
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
- Failure Pattern 추출: REJECT/REQUEST_CHANGE로 종료된 Review와 FAIL로 종료된 Verification에서 실패 유형 추출
- AI/바이브 코딩 특유 패턴 별도 태깅: Hallucinated Dependency, Prompt Injection 방어 누락, 과잉 생성, Silent Error Swallowing, Test 없는 대량 커밋 등 ([03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md)의 AI/Vibe-coding 진단 절과 연동)
- Component Signature(코드 Hash + Interface Hash) 단위로 Pattern을 매칭해 신규 Case의 대상과 대조
- Pattern Confidence를 재현 횟수, 적용 성공률, False Positive 이력으로 산정
- 신규/기존 고객 소스에서 나온 Pattern은 기본적으로 Tenant 전용이며, 고객 식별 정보를 제거한 뒤에만 공유 Corpus로 승격 가능
- 재현 3회 이상 실패(False Positive)한 Pattern은 자동 강등

### 기능 — 재귀학습(Recursive Detection Learning)
자동 Review/Verification이 놓친 결함이 Independent Review 불일치, Human Review Override, Production Incident, 고객 신고, 뒤늦은 Regression으로 확인되면 다음 루프를 수행한다.

1. MissedFinding 등록: 놓친 결함을 원 Case, 원 Rule Pack/모델 버전과 함께 기록
2. RCA: Rule 미존재, Confidence Threshold 오류, 모델 한계, Fixture 미포함 중 원인 분류
3. 보강안 생성: 신규/개정 KnowledgePattern 또는 Rule Pack 개정안, 필요 시 Golden Review Fixture·OLicense Fixture에 해당 사례 추가
4. 회귀 검증: 개정안이 동일 MissedFinding을 재현 탐지하는지 확인하는 동시에, 기존 Golden Fixture 전체에 대해 False Positive가 급증하지 않는지 확인(모델/Rule 개정도 하나의 Patch로 취급해 회귀검증)
5. 승격: 회귀 검증을 통과한 개정안만 프로덕션 Rule Pack/Pattern Library에 반영하고 Rule Pack Digest를 갱신

이 루프는 사람이 승인한 개정만 프로덕션에 반영하며, 탐지 결과를 스스로 무비판 재학습해 자기 자신을 검증하지 않는다(자기 참조 승인 금지). Cycle마다 Recall(놓친 결함 비율 감소)과 False Positive율 변화를 함께 추적해 개선/퇴보를 판정한다.

### 산출물
KnowledgePattern, MissedFinding, PatternApplicationReceipt, PatternLibraryRevision, DetectionCapabilityChangeReport

### 수용기준
- 모든 Pattern은 최초 근거가 된 ReviewFinding, VerificationFinding 또는 ImprovementRequest로 역추적 가능
- Pattern 매치는 그 자체로 Critical/High 자동 확정 근거가 될 수 없으며 Confidence를 높이는 보조 신호로만 사용한다
- 공유 Corpus로 승격되는 Pattern과 Fixture는 고객 식별정보를 포함하지 않는다(Anonymization 필수)
- FR-COM-009의 Opt-out을 선택한 Organization의 데이터는 어떤 형태로도 공유 Corpus 후보 추출 대상에서 제외한다(Tenant 전용 Pattern 생성은 계속 가능)
- 재귀학습으로 인한 Rule/모델 개정은 반드시 전체 Golden Fixture Regression을 통과한 뒤에만 프로덕션에 반영한다
- MissedFinding은 발견 경로(Independent Review/Human Override/Incident/고객신고/지연 Regression)를 구분해 기록한다

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
- Merge 권고
- Rollback 정보 제공
- Post-merge Incident 대응: Merge 이후 발견된 결함은 Draft PR 흐름과 분리된 Hotfix Worktree로 처리하며, 원인이 된 Merge Commit과 새 MissedFinding/ImprovementRequest를 상호 링크한다

## 10. ODelivery
- Web Report
- Program Profile
- Findings CSV/JSON/SARIF(GitHub/GitLab Code Scanning 연동용 표준 포맷)
- SBOM(CycloneDX/SPDX 포맷, 의존성 공급망 투명성 증빙)
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