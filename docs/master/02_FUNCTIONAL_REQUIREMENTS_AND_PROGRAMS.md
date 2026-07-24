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
- 설계규칙·Dependency Boundary 검토
- 정책 위반 탐지
- 버그·동시성·예외·자원누수·복잡도 검토
- Prompt Injection, Tool 권한, RAG 출처·오염 검토
- Secret, 취약 Dependency, 인증·인가 검토
- 테스트 누락과 취약 Assertion 검토
- PR 단위 Inline Comment, Summary, Decision 생성
- 독립 Review Pass 지원

### Decision
APPROVE, COMMENT, REQUEST_CHANGE, REJECT, NOT_APPLICABLE, INCONCLUSIVE

### 수용기준
- Finding마다 파일·라인 또는 구성요소·근거·정책·영향·제안 포함
- 중복 Finding 통합
- 추측성 Finding은 Confidence와 확인방법 포함
- Critical/High Finding은 근거 없는 자동 승인 금지

## 6. OVerification
### 기능
- Static, Build, Unit, Integration, Scenario, Adversarial, Performance, Recovery, License 검증
- 요구사항별 Test Claim 생성
- 실제 실행결과와 Expected 결과 비교
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
- CI 상태 회수
- Review Comment 수집
- Merge 권고
- Rollback 정보 제공

## 10. ODelivery
- Web Report
- Program Profile
- Findings CSV/JSON
- Evidence Pack
- Patch/Diff
- Draft PR
- Executive Summary
- Technical Report
- Deletion Receipt

## 11. 비기능 요구사항
- NFR-SEC: 저장·전송 암호화, Secret 비노출, 최소권한
- NFR-REL: 멱등성, 재시도, 중복 이벤트 방어
- NFR-PERF: 대규모 Repository의 단계적 분석과 중단·재개
- NFR-AUDIT: 모든 권한·실행·변경 감사
- NFR-PORT: SaaS, Local Runtime, 폐쇄망 배포 가능
- NFR-PRIV: 고객별 보존기간과 완전 삭제 증명
- NFR-OBS: Trace, Metric, Structured Log
- NFR-ACCESS: 관리자·개발자·감사자 역할 분리

## 12. 추적성
Requirement → Design Component → Code Module → Test Case → Evidence → Release를 단일 ID 체계로 연결한다.