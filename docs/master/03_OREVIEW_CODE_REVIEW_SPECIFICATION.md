# OReview 전영역 리뷰 상세설계서

## 1. 목적
OReview는 단순한 문법·버그 탐지기가 아니다. 요구사항, 설계, 정책, 코드, AI 구성, 보안, 성능, 테스트, 품질, Merge 적정성을 하나의 변경 단위에서 연결해 검토한다. 문서명의 "코드리뷰"는 편의상 축약이며 실제 범위는 이 절에 나열한 전 영역을 포함한다.

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
10. Independent Pass: 원 구현과 다른 모델 계열 또는 규칙 기반 독립 검토(Critical 후보는 §10-1 Cross-Model Verification 필수)
11. Reconciliation: 중복·충돌·불확실성 정리
12. Merge Decision: 증거와 미해결 Finding 기반 판정

## 4. 리뷰 영역별 규칙
### Requirement Review
- 요구사항 누락, 잘못된 해석, 과잉 구현
- 어떤 요구사항에도 연결되지 않은 신규 코드(고아 코드) — 역방향 Traceability로 탐지
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
- AI 자기주장과 실제 Evidence 불일치(Self-Claim Verification) — "구현 완료", "테스트 통과" 등 AI가 스스로 밝힌 주장을 별도 추출해 대조
- 이전 Baseline 대비 AI 구성 Drift(Tool 권한 확대, 신규 외부 연동 등)가 검토 없이 반영되었는지 확인([02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) OLearning AI 구성 Drift 탐지와 연동)

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

## 4-1. AI/바이브 코딩 생성 코드 특화 진단
Claude 등 AI Agent와 대화형 코딩("바이브 코딩")으로 생성된 코드는 일반 코드리뷰 규칙만으로는 잡히지 않는 특유의 결함 유형을 반복한다. 다음을 별도 점검 항목으로 둔다.

| 점검 항목 | 탐지 신호 | 근거 |
|---|---|---|
| 구조 일관성 붕괴 | 동일 기능이 세션/커밋마다 다른 패턴으로 재구현됨 | Component Signature 중복(Duplicate Capability Fingerprint) |
| Hallucinated Dependency | 존재하지 않는 패키지·API·메서드 호출 | Import Resolution 실패, 정적 타입/링크 오류 |
| 과잉 생성 | 요청 범위를 넘는 파일·기능·추상화 생성 | Diff 범위와 Requirement Traceability 불일치 |
| 무의미한 예외 처리 | try/except pass류로 실패를 감춤 | Empty Catch, 로그 없는 예외 흡수 패턴 |
| 테스트 없는 대량 변경 | LOC 변화 대비 Test 변화 비율 급락 | Test/LOC 비율 임계치 미만 |
| 설명과 실제 변경 불일치 | Commit/PR 설명과 Diff 의미가 다름 | 자연어 요약과 AST 변경 대조 |
| 문서/주석과 실제 동작 불일치 | Docstring·주석이 설명하는 동작과 실제 구현이 다름 | Doc-Code Consistency Check(주석 파싱 결과와 실행/정적분석 결과 대조) |
| AI 구성 자체의 취약점 | 시스템 프롬프트 하드코딩, 과도한 Tool 권한 부여, RAG 출처 미검증 | 03의 AI Review 규칙과 동일 기준 적용 |

이 표의 항목은 [02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md)의 OMemory와 연동되어, 동일 항목이 반복 발견되면 KnowledgePattern으로 승격되고 이후 Case에서는 Preflight 단계부터 우선순위가 상향된다. 자동 판정이 이 항목을 놓쳤다가 나중에 확인된 경우 OMemory의 재귀학습 루프(MissedFinding)로 흡수한다.

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
- Reviewer 간 일치도(모델 Reviewer와 Human/Professional Reviewer 모두 포함)
- Finding 재현율
- Inline 위치 정확도
- 독립 Blind Review
- Confidence Calibration: Confidence 90%로 표시된 Finding 집합이 실제로 약 90% 비율로 맞는지 등 신뢰도 구간별 실측 정확도를 주기적으로 측정(Calibration Curve). 특정 구간이 체계적으로 과신/과소평가되면 Confidence 산정 로직을 재보정 대상으로 지정한다

## 9-1. OReview 자체 판정 재현성
[02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md)의 FR-COM-005("동일 입력·정책·도구 버전은 재현 가능한 판정 구조를 가져야 한다")는 고객 코드뿐 아니라 OReview·OVerification 자신의 AI 기반 판정에도 동일하게 적용된다.

- Reviewer Version에는 모델 명, 모델 버전, Decoding 설정(Temperature 등)을 포함하여 Receipt에 고정한다
- 프로덕션 판정에는 고정된 모델 버전만 사용하며 임의 시점 자동 업그레이드를 금지한다
- 모델 버전 교체는 별도 Rule Pack Digest 변경으로 취급하고 교체 전후 Golden Review Fixture 결과를 비교해 Drift를 확인한다
- 동일 Diff에 대한 반복 실행 결과가 Severity/Decision 수준에서 달라지면 Flaky Reviewer로 분리하고 해당 판정은 INCONCLUSIVE로 표시한다

## 10. 감사와 증거
각 Review 실행은 Baseline SHA, Target SHA, Diff Hash, Rule Pack Digest, Reviewer Version, Input Digest, Output Digest, Decision을 Receipt에 결속한다.

## 10-1. Cross-Model Verification
동일 모델(계열)이 코드를 생성하고 그 코드를 리뷰까지 하면, 그 모델이 원래 놓치는 유형의 결함을 리뷰에서도 동일하게 놓치는 상관된 blind spot 위험이 있다. 이를 줄이기 위해 다음을 규칙화한다.

- Critical로 잠정 판정된 Finding은 원 구현에 사용된 모델·Provider와 다른 계열의 모델로 2차 확인을 거친 뒤에만 최종 확정한다(예: 원 구현이 Claude 계열이면 2차 확인은 다른 Provider/다른 세대 모델)
- 두 모델의 판정이 불일치하면 자동 승격하지 않고 Human 또는 Professional Reviewer에게 회부한다(INCONCLUSIVE로 표시)
- 2차 확인에 사용된 모델·버전은 §9-1(OReview 자체 판정 재현성) 원칙에 따라 Receipt에 함께 고정한다
- Cross-Model 불일치 이력은 [02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) OMemory의 MissedFinding/KnowledgePattern 후보로 연결해 반복되는 blind spot 유형을 축적한다
- 비용 관리를 위해 Cross-Model 2차 확인은 Critical 후보에 한정하며 Medium/Low까지 전수 적용하지 않는다

## 11. 금지사항
- 실행하지 않은 Test를 PASS로 표기
- Evidence 없는 Critical 판정
- 코드 스타일 차이를 보안 결함으로 과장
- 고객 정책보다 모델의 일반 선호를 우선
- 기존 Finding을 설명 없이 삭제
- Review 결과를 이용해 라이선스 한도를 우회