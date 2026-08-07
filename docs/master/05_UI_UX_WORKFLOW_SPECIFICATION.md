# ONSure UI·UX 및 업무흐름 상세설계

## 1. UX 원칙
실제 `contracts/workflow-operation-registry.v1.json`의 `generic_surfaces`는 CLI, LOCAL_AUTHENTICATED_API, VSCODE 3개뿐이며 "WEB"을 별도 Operation Dispatch 표면으로 등록하지 않는다. 이 설계서의 Web은 그 자체로 새로운 Operation 표면이 아니라 LOCAL_AUTHENTICATED_API를 호출하는 하나의 클라이언트로 구현되어야 한다는 뜻이다(다른 클라이언트와 동일한 Operation·권한 경계를 공유).

- 고객은 복잡한 내부 Meter보다 상품, 범위, 예상가격, 진행상태, 결과를 이해해야 한다.
- 위험한 작업은 실행 전 영향과 비용을 보여주고 명시적으로 승인받는다.
- PASS보다 미실행·차단·불확실성을 숨기지 않는다.
- Finding은 문제, 근거, 영향, 수정방법, 확인방법 순으로 표현한다.
- 개발자는 VS Code를 떠나지 않고 주요 흐름을 수행할 수 있어야 한다.

## 1-1. Organization Switcher
여러 Organization Membership을 가진 사용자(SI·컨설팅·품질관리 회사 등)를 위해 모든 화면 상단에 Organization Switcher를 고정 노출한다. 전환 시 이전 Organization의 데이터·세션 컨텍스트는 즉시 폐기하고 새 Organization Context로 재조회한다.

## 2. Web 정보구조
- Home
- Products
- Preflight & Quote
- Orders & Payments
- Systems & Programs
- Service Cases
- Learning Results
- Review & Verification
- Improvement
- Deliveries
- Licenses & Usage
- Organization & Users
- Security & Retention
- Support

## 3. Web 고객 흐름
### 신규 Case
회원가입/SSO → 조직 생성 → 상품 선택 → Source 연결 또는 Upload → Preflight → 예상 학습량·검증범위·가격 확인 → Scope 승인 → 결제 → OLicense 발급 → Case Ready → 실행 → Review → 산출물 인수

### Improve & Re-verify
Finding 목록 → 대상 선택 → 영향범위와 견적 → 승인·추가결제 → Patch Plan → Diff 승인 → Regression → Before/After → 납품

## 4. 주요 Web 화면
### Preflight
- 입력 방식: Git, Archive, Container Manifest — 실제 `contracts/target-adapter.v1.json`의 `supported_source_kinds`는 GIT_REPOSITORY, SOURCE_ARCHIVE, PACKAGE, BINARY, CONTAINER_IMAGE, DEPLOYED_SERVICE, DOCUMENT_AND_POLICY_SET까지 포함하므로 Package·Binary·배포된 서비스·문서/정책 세트 입력도 화면에 반영해야 한다
- 감지된 언어·Framework·Repository·Program
- 예상 Learning Unit과 신뢰구간
- 누락 자료 및 접근 실패
- 보안·보존 옵션
- 예상가격과 유효기간

### Case Dashboard
- Case 상태와 단계
- ProgramRiskScore와 등급(A~E), 등급 산정 근거 요약
- 검토·검증 범위에서 제외된 Component 목록과 제외 사유(CoverageReport) — "전체 검토 완료"로 오인되지 않도록 항상 노출
- 고정 Baseline
- 포함 System/Program
- 사용량과 잔여량
- Blocker와 고객 요청사항
- 예상 완료일
- 산출물 상태

### Finding Explorer
- Severity, Domain, Program, Status 필터
- 문제 요약
- Source 위치와 Evidence
- Requirement/Policy 연결(고아 코드 Finding은 연결된 요구사항이 없음을 명시)
- 영향과 재현방법
- Critical Finding의 Cross-Model Verification 결과(1차/2차 모델 일치 여부)
- AI 자기주장과 실제 Evidence 불일치 시 별도 배지 표시
- 개선 제안
- Accept Risk, False Positive, Improve 선택
- 대량 Finding 발생 시 일괄 처리: 필터로 좁힌 결과에 대해 일괄 Accept Risk/False Positive 지정, 저장된 필터 뷰, CSV 내보내기 후 일괄 재반영. 단 Critical/High의 일괄 Accept Risk는 개별 확인 없이 처리하지 않으며 §7 위험행위 확인을 거친다

### Finding Explorer 내 전문가 검토 요청
Finding 목록 또는 Case Dashboard에서 "전문가 검토 요청"을 선택하면 대상 Finding/범위, 예상 소요시간, 추가 비용을 표시하고 승인 시 결제 후 관리자 화면의 "전문가 리뷰 배정" 큐로 전달한다. 배정된 전문가의 소견은 별도 Decision(EXPERT_CONCUR, EXPERT_OVERRIDE, EXPERT_ESCALATE)으로 Finding에 결합되며 자동 Decision을 덮어쓰지 않고 병기한다.

### Organization Portfolio
- 조직에 속한 모든 System/Program을 한 화면에서 통합 조회(PortfolioSnapshot 기반)
- Program별 최근 Case 상태, 미해결 Critical/High Finding 수, License/Credit 잔량을 한 눈에 비교
- 위험도 상위 Program 정렬과 Drill-down으로 개별 Case Dashboard 이동
- ProgramRiskScore 추세 스파크라인(개선/악화 방향 표시)
- 익명화된 동종 규모·언어 Program 대비 상대적 위치(예: "유사 규모 Program 상위 30%") — 공유 Corpus Opt-in Organization의 데이터만 비교 모수로 사용
- 알림 채널 구독 설정(Email/Webhook/VS Code/관리자 알림함, 심각도별 즉시/일간요약)
- 공유 Pattern Corpus 기여 Opt-in/Opt-out 설정(FR-COM-009, 기본값 Opt-out)이며 Enterprise 규제산업 계약은 이 설정을 비활성화(강제 Opt-out) 상태로 고정할 수 있다

### Support Center
- 등급별 SLA 표시: Web 고객(영업일 기준 1차 응답), VS Code Developer(2 영업일), Team(1 영업일), Enterprise(계약 SLA, 예: 4시간)
- 티켓 생성 시 관련 Case/Finding/License를 자동 첨부
- 상태: OPEN → IN_PROGRESS → WAITING_ON_CUSTOMER → RESOLVED → CLOSED
- Professional Reviewer 요청("Finding Explorer 내 전문가 검토 요청" 화면 참조)과 일반 기술지원 티켓을 구분해 큐잉

### Delivery Center
- Executive Report
- Technical Report
- Program Profile
- Findings Export
- Evidence Pack
- Patch 또는 Draft PR
- Acceptance Certificate 발급과 External Acceptor(발주기관) 초대 — 초대받은 제3자는 별도 계정 없이 서명 검증 링크로 발급 사실만 확인하거나, 초대 범위 내에서 Delivery를 읽기 전용으로 열람
- Deletion Receipt

### License & Usage
- OLicense 상태
- Web Case와 VS Code 구독
- Seat, System, Program, Credit
- Reserve/Commit/Release 내역
- Hard Stop, Auto Top-up, Approval Required 설정

## 5. 관리자 화면
- Product Catalog와 Feature Entitlement 조회
- Organization PolicyPack 업로드·버전관리·회귀결과 조회(고객 Admin도 자체 Organization 범위에서 동일 화면 접근 가능)
- Case 운영 큐
- 결제·환불·정산 상태
- License 발급 실패 및 재처리
- Worker와 Sandbox 상태
- 고객 승인 대기
- 전문가 리뷰 배정
- 보존·삭제 작업
- 감사 이벤트 검색

운영자는 OLicense 원장을 직접 임의 수정하지 않고 승인된 관리 API만 사용한다.

## 6. VS Code 화면
### Activity Bar
ONSure 아이콘 아래 다음 View를 제공한다.
- Chat
- Program Profile
- Plan
- Review
- Verification
- Findings
- Improvement
- Knowledge
- Evidence
- Git & PR
- License & Usage

### Chat
Ask, Plan, Act, Autopilot 모드를 제공한다. 현재 Workspace, Baseline, Entitlement, 예상 Credit을 항상 표시한다.

- Ask: 읽기 전용 질의응답. Program Profile/Finding/Evidence를 근거로 답하며 코드·설정을 변경하지 않는다
- Plan: ExecutionPlan 또는 PatchPlan 초안만 생성하고 실행하지 않는다. 사용자 승인 전까지 Act로 자동 전이하지 않는다
- Act: 승인된 Plan을 단계별로 실행하며, §7 위험행위 목록에 해당하는 단계마다 확인을 요구한다
- Autopilot: Stop Condition에 도달하거나 위험행위 목록에 해당하는 단계 전까지 연속 실행한다. Autopilot 상태에서도 Main 직접 변경과 Push는 FR-COM-008과 §7에 따라 사용자 승인 없이 수행할 수 없다. 실제 `contracts/unattended-autopilot.v1.json`은 Autopilot을 더 엄격하게 제한한다 — `forbidden_actions`로 FINAL_PASS, FINAL_AUDIT_PASS, FINAL_LOCK, PRODUCTION_GO, COMMERCIAL_GO, REAL_DATA_LEARNING, PAID_EXTERNAL_SERVICE, SECRET_EXPORT, FORCE_PUSH, HARD_RESET, IMPLICIT_STASH를 명시적으로 금지하고, `merge_authorization.authorized`는 계약상 항상 `false`이며, 단계별 최대 재시도(`maximum_stage_attempts`)는 1회로 제한한다(재시도 루프 자체를 허용하지 않음). 이 설계서의 Autopilot 정의는 이 계약의 상위 요약이며, 실제 구현은 계약의 금지 목록을 그대로 따라야 한다
- 모드는 실행 중 언제든 전환 가능하며, 전환 시점의 Plan/Diff 상태를 그대로 유지한다

### Program Profile
Component Tree, Dependency Graph, AI Component, Unknown/Conflict, Profile Revision을 표시한다.

### Plan
- ExecutionPlan을 DAG로 시각화(OLearning→OReview→OVerification→OImprovement 단계별 노드와 의존관계)
- ScopeManifest와 계약 CaseScope 비교, 범위 초과 항목 강조
- ResourceEstimate(예상 Learning Unit/Credit/소요시간)와 신뢰구간
- Stop Condition과 사용자 승인이 필요한 지점 표시
- 이전 실행 대비 Plan Diff 확인 후 승인(Approval Receipt 생성)

### Review
- Current Diff와 Review Domain
- Inline Finding
- Severity/Confidence
- Quick Fix가 아닌 Improvement Request 생성
- Re-review 상태

### Verification
- Verification Pack
- Scenario와 Test 상태
- PASS/FAIL/BLOCKED/NOT_RUN
- Mutation Score(Test Suite 실효성 지표)와 취약 구간 표시
- Log와 Evidence
- 재실행 비용

### Improvement
- 승인 Finding
- Patch Plan
- Blast Radius 미리보기(영향받는 파일·Component·타 Program 목록) — DRY_RUN 결과를 실제 Patch 적용 전 승인 단계에 노출
- Worktree와 Branch
- 변경파일·Diff
- Test 결과와 Before/After 동작·성능 Diff 요약
- Accept, Edit, Abandon, Draft PR

### Knowledge
- 현재 Diff/Component와 매칭된 KnowledgePattern 목록과 Confidence
- Pattern의 원 근거(원 Finding, 원 Case) 링크
- Tenant 전용 Pattern과 공유(Promoted) Pattern 구분 표시
- Accept/Reject Feedback 버튼 — 선택 결과는 OMemory 재귀학습 루프에 반영
- 자동 판정이 놓쳤다가 나중에 확인된 MissedFinding 이력과 이에 따른 탐지 능력 개선 이력(Detection Capability Change) 조회

### Git & PR
- Dirty Workspace 상태
- Worktree
- Branch
- Commit
- Push
- Draft PR
- CI
- Review Comment
- Merge Readiness
- Rollback 실행 시 검증 결과(대상 Baseline이 직전 정상 상태와 일치하는지) 표시, 불일치 시 Critical Incident 배지

## 7. 실행 권한 3단계

### 자동 허용
승인 없이 즉시 실행한다.
- 읽기 전용 분석
- Program Profile 후보 생성
- 검증 계획(ExecutionPlan) 생성
- 저비용 안전 테스트 실행
- Patch Dry-run 미리보기(Blast Radius)

### 사용자 또는 정책 승인 필요 (2단계 확인)
- 외부 Network 허용
- 고비용 검증
- Program/Scope 확대
- Patch 적용
- Push와 Draft PR
- Risk Accept
- Offline Grace 연장

### 고위험 별도 승인 (관리자 또는 Compliance Officer)
- Merge
- Baseline·정책 변경
- 인증·권한·암호화 변경
- Evidence 삭제, 데이터 삭제·Migration
- Secret 접근
- 외부 배포와 운영환경 변경

동일 위험행위라도 상위 등급 승인이 하위 등급 확인을 대체하지 않는다 — 예를 들어 Merge 승인이 그 전 단계의 Push 승인을 소급 생략시키지 않는다.

## 8. 접근성·국제화
- 키보드 탐색
- 색상 외 Severity 표시
- Screen Reader Label
- 시간대·통화·언어 분리
- 긴 Log의 Progressive Loading

## 9. 화면 수용기준
- 고객은 3분 안에 Case 상태와 다음 행동을 파악할 수 있어야 한다.
- Finding은 10초 내 문제와 영향, 30초 내 근거와 조치를 이해할 수 있어야 한다.
- 비용 차감 전 예상량을 확인할 수 있어야 한다.
- BLOCKED와 NOT_RUN을 PASS처럼 보이게 표현하지 않는다.
- VS Code에서 Baseline과 License 상태를 항상 확인할 수 있어야 한다.