# OReview 코드리뷰 상세설계서

## 1. 목적
OReview는 단순한 문법·버그 탐지기가 아니다. 요구사항, 설계, 정책, 코드, AI 구성, 보안, 성능, 테스트, 품질, Merge 적정성을 하나의 변경 단위에서 연결해 검토한다.

## 2. 리뷰 입력
- 고정 Baseline과 Target Revision
- PR Diff 또는 Workspace Diff
- Program Profile
- 요구사항·정책·설계 문서
- Coding/Architecture Rule Pack
- Test 결과와 Coverage
- Build·Dependency·Security 결과
- Prompt·Agent·Tool·RAG 구성
- OLicense Entitlement와 허용 기능

## 3. 리뷰 파이프라인
1. Scope Lock: 대상 Revision, 파일, 정책 버전 고정
2. Change Classification: 기능, 버그, 정책, 설정, AI, Test, Infra 분류
3. Traceability Review: 요구사항과 변경 연결
4. Architecture Review: Layer·Dependency·Boundary 검토
5. Policy Review: 조직·제품·규제 정책 적용
6. Code Review: 정확성·예외·동시성·성능·유지보수
7. AI Review: Prompt, RAG, Agent, Tool, Memory
8. Security Review: 인증·인가·입력·Secret·Dependency
9. Test Review: Coverage가 아닌 행위·경계·부정 시나리오 검토
10. Independent Pass: 다른 모델 또는 규칙 기반 독립 검토
11. Reconciliation: 중복·충돌·불확실성 정리
12. Merge Decision: 증거와 미해결 Finding 기반 판정

## 4. 리뷰 영역별 규칙
### Requirement Review
- 요구사항 누락, 잘못된 해석, 과잉 구현
- Acceptance Criteria 미충족
- 변경된 동작에 대한 문서·테스트 미갱신
- 비기능 요구사항 영향 누락

### Architecture Review
- 금지 Dependency
- Domain과 Infrastructure 결합
- 순환참조
- 상태·트랜잭션 경계 오류
- 공유 DB 또는 Event 계약 무단 변경
- Tenant·License Boundary 위반

### Policy Review
- 인증·권한·감사·보존·삭제 정책
- Fail-open 구현
- 정책 버전 미결속
- 예외 승인 없는 우회
- OLicense Feature Gate 누락

### Code Review
- Null/Optional 처리
- Error swallowing
- Race condition, deadlock, shared mutable state
- Resource leak, timeout, retry storm
- Injection, unsafe parsing, path traversal
- 잘못된 캐시·트랜잭션·일관성
- 과도한 복잡도와 중복
- Log에 Secret·개인정보 노출

### AI Review
- Prompt Injection 방어
- System/User/Tool instruction 경계
- Tool 최소권한과 파라미터 검증
- RAG 출처, 최신성, 오염, Tenant 혼합
- Hallucination을 사실로 확정하는 흐름
- Memory 저장·삭제·범위
- 모델 변경 시 결과 Drift
- 비용·Token·Context 폭주

### Security Review
- OWASP 계열 취약 패턴
- Dependency 취약점과 License
- Secret, Key, Token 관리
- JWT 검증, Audience, Issuer, Expiry
- SSRF, XSS, CSRF, SQL/Command Injection
- 암호화와 키 회전
- 관리자 권한과 감사로그

### Test Review
- 정상경로만 존재하는지
- Negative, Boundary, Error, Recovery 누락
- Mock이 실제 결함을 가리는지
- Assertion이 약하거나 결과를 검증하지 않는지
- Flaky·비결정적 Test
- 실패 Test 삭제 또는 Skip 남용
- License·결제·Webhook 멱등성 시험 누락

## 5. Finding Schema
- finding_id
- review_domain
- severity: CRITICAL/HIGH/MEDIUM/LOW/INFO
- confidence
- status
- title
- description
- evidence_refs
- source_location
- requirement_refs
- policy_refs
- impact
- exploitability 또는 failure_condition
- recommendation
- verification_method
- duplicate_group
- reviewer_identity/model/tool_version

## 6. Decision 규칙
- REJECT: Critical 미해결, 정책 우회, 증거 위조, 위험한 Main 직접변경
- REQUEST_CHANGE: High 미해결 또는 수용기준 미충족
- COMMENT: Medium/Low만 존재하며 위험수용 가능
- APPROVE: 필수 Gate 통과, Critical/High 0건, 필수 Test PASS
- INCONCLUSIVE: 입력·환경·정책 불충분

APPROVE는 Merge 실행과 동일하지 않다. 실제 Merge 권한은 별도 역할과 정책이 가진다.

## 7. Continuous Review
- 파일 저장 또는 Diff 변화 감지
- 저비용 Fast Review
- Commit 전 Full Review
- Push 전 Policy Gate
- PR 생성 후 Independent Review
- CI 결과 반영 후 Merge Review

동일 Finding은 안정적인 Fingerprint로 추적하며 코드 이동만으로 새 Finding으로 중복 생성하지 않는다.

## 8. 자동 수정 연계
OReview는 직접 Main을 수정하지 않는다.

Finding → 사용자 선택 → OImprovement Patch Plan → Worktree → Patch → Test → Re-review → Re-verify → Draft PR

## 9. 코드리뷰 자체 품질검증
- Golden Review Fixture
- False Positive/False Negative Set
- 언어별 Bug Corpus
- 정책 위반 Corpus
- AI Prompt/RAG/Tool 적대 Fixture
- Reviewer 간 일치도
- Finding 재현율
- Inline 위치 정확도
- 독립 Blind Review

## 10. 감사와 증거
각 Review 실행은 Baseline SHA, Target SHA, Diff Hash, Rule Pack Digest, Reviewer Version, Input Digest, Output Digest, Decision을 Receipt에 결속한다.

## 11. 금지사항
- 실행하지 않은 Test를 PASS로 표기
- Evidence 없는 Critical 판정
- 코드 스타일 차이를 보안 결함으로 과장
- 고객 정책보다 모델의 일반 선호를 우선
- 기존 Finding을 설명 없이 삭제
- Review 결과를 이용해 라이선스 한도를 우회