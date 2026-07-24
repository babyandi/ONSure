# ONSure 시험·운영·구현 계획서

## 1. 구현 원칙
- 문서 기준선 없이 코드부터 작성하지 않는다.
- 모든 Story는 Requirement, Acceptance, Test, Evidence를 가진다.
- 코드 작성 후 작성자가 아닌 독립 리뷰를 수행한다.
- 구현 완료와 제품 검증 완료를 분리한다.
- 완료된 시험은 Baseline이 바뀌지 않은 한 불필요하게 반복하지 않는다.
- BLOCKED는 격리하고 다른 Lane을 진행한다.

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
OpenAPI, Event Schema, OLicense Token, Payment Webhook, Git Provider.

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

### OLicense Fixture
- 만료
- Revocation
- 잘못된 Audience
- 서명 Key 회전
- Clock Rollback
- Credit 이중 Commit
- Reserve Timeout
- Offline Usage 중복 동기화

## 6. 비기능 시험
- 대규모 Repository 학습 성능
- 동시 Case와 동시 VS Code 실행
- Worker Crash 복구
- Queue 중복전달
- DB Failover
- Object Storage 장애
- 모델 Provider Timeout과 비용 폭주 방어
- Tenant 침범 시도
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
- Source 유출 의심
- Tenant Isolation 사고
- Evidence 손상
- 삭제 실패

사고는 Severity, Owner, Timeline, Customer Communication, Containment, RCA, Corrective Action, Regression Test를 남긴다.

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

## 11. 우선 구현 순서
1. Schema, Receipt, OLicense 계약
2. Local Runtime과 OLearning 최소기능
3. OReview 코드리뷰 최소기능
4. OVerification 실행 Harness
5. Web Learn & Verify Case
6. OImprovement Worktree Patch
7. VS Code Developer
8. Payment/Refund와 운영화
9. Team/Enterprise 기능

## 12. 최종 수용기준
- 문서와 코드 Traceability 확보
- Web와 VS Code 실제 Full-Chain 각각 연속 2회 PASS
- OLicense 전 수명주기 PASS
- OReview 독립 검토 PASS
- Critical/High 연속 2회 0건
- Rollback과 Recovery PASS
- 고객 데이터 삭제 증명 PASS
- Final Evidence Pack 봉인