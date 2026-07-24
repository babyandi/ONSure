# ONSure UI·UX 및 업무흐름 상세설계

## 1. UX 원칙
- 고객은 복잡한 내부 Meter보다 상품, 범위, 예상가격, 진행상태, 결과를 이해해야 한다.
- 위험한 작업은 실행 전 영향과 비용을 보여주고 명시적으로 승인받는다.
- PASS보다 미실행·차단·불확실성을 숨기지 않는다.
- Finding은 문제, 근거, 영향, 수정방법, 확인방법 순으로 표현한다.
- 개발자는 VS Code를 떠나지 않고 주요 흐름을 수행할 수 있어야 한다.

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
- 입력 방식: Git, Archive, Container Manifest
- 감지된 언어·Framework·Repository·Program
- 예상 Learning Unit과 신뢰구간
- 누락 자료 및 접근 실패
- 보안·보존 옵션
- 예상가격과 유효기간

### Case Dashboard
- Case 상태와 단계
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
- Requirement/Policy 연결
- 영향과 재현방법
- 개선 제안
- Accept Risk, False Positive, Improve 선택

### Delivery Center
- Executive Report
- Technical Report
- Program Profile
- Findings Export
- Evidence Pack
- Patch 또는 Draft PR
- Deletion Receipt

### License & Usage
- OLicense 상태
- Web Case와 VS Code 구독
- Seat, System, Program, Credit
- Reserve/Commit/Release 내역
- Hard Stop, Auto Top-up, Approval Required 설정

## 5. 관리자 화면
- Product Catalog와 Feature Entitlement 조회
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
- Evidence
- Git & PR
- License & Usage

### Chat
Ask, Plan, Act, Autopilot 모드를 제공한다. 현재 Workspace, Baseline, Entitlement, 예상 Credit을 항상 표시한다.

### Program Profile
Component Tree, Dependency Graph, AI Component, Unknown/Conflict, Profile Revision을 표시한다.

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
- Log와 Evidence
- 재실행 비용

### Improvement
- 승인 Finding
- Patch Plan
- Worktree와 Branch
- 변경파일·Diff
- Test 결과
- Accept, Edit, Abandon, Draft PR

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

## 7. 위험행위 Confirm
다음은 2단계 확인 또는 관리자 승인을 요구한다.
- 외부 Network 허용
- 고비용 검증
- Program/Scope 확대
- Patch 적용
- Push와 PR
- Risk Accept
- Evidence 삭제
- Offline Grace 연장

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