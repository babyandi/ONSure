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
- Dependency 취약점과 License — Copyleft(GPL 계열) License가 상용 폐쇄소스 배포 조건과 충돌하는지 조직 PolicyPack의 허용/차단 목록 기준으로 판정
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
`contracts/security-findings.v1.schema.json`은 `severity`(CRITICAL/HIGH/MEDIUM/LOW/INFO — 아래와 일치)와 `status`를 **OPEN, CLOSED, ACCEPTED_RISK 3개로만** 정의한다. 이 설계서가 이전에 제안한 6단계 생애주기(OPEN→ACKNOWLEDGED→FIX_PLANNED→FIXED→RE_REVIEWED→CLOSED)와 FALSE_POSITIVE/DUPLICATE/WONT_FIX 상태는 계약에 없는 `DESIGN_ONLY` 확장이다 — 세분화가 필요하면 이 3개 상태 위에 얹는 방식으로 계약을 먼저 확장해야 하며, 이 문서의 상세 상태를 이미 구현된 것처럼 다루면 안 된다.

- finding_id
- review_domain
- severity: CRITICAL/HIGH/MEDIUM/LOW/INFO
- confidence
- status (계약 기준 OPEN/CLOSED/ACCEPTED_RISK, 이 설계서의 세분화는 DESIGN_ONLY)
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

`contracts/oreview-result.v1.schema.json`이 실제 Decision 어휘다. 이 절은 원래 APPROVE/COMMENT/REQUEST_CHANGE/REJECT를 자체 어휘로 정의했으나, 계약은 [status-vocabulary.v1.json](../../contracts/status-vocabulary.v1.json)의 공통 `run_decisions`(PASS/FAIL/BLOCKED/HOLD/NOT_RUN/INCONCLUSIVE/NON_FINAL)를 그대로 재사용한다. 아래는 계약 기준으로 정정한 것이다.

### 영역별 Decision (도메인 최소 10개)
각 리뷰 영역(§4의 Requirement/Architecture/Policy/Code/AI/Security/Test 등)마다 `PASS`, `FAIL`, `HOLD`, `NOT_RUN`, `NOT_APPLICABLE` 중 하나와 `evidence_refs`, `observations`(최소 1개), `recommendation`을 기록한다.

- FAIL: 해당 영역에 Critical 또는 High 미해결 Finding, 정책 우회, 증거 위조
- HOLD: 입력·환경·정책 불충분, 또는 판정에 필요한 선행 조건 미충족(이 설계서가 이전에 쓴 INCONCLUSIVE에 해당)
- NOT_RUN: 해당 영역을 이번 범위에서 실행하지 않음(CoverageReport와 연동)
- PASS: Medium/Low만 존재하며 위험수용 가능하거나 결함 없음
- NOT_APPLICABLE: 대상 변경에 해당 영역이 적용되지 않음

### 종합 Decision
- `quality_decision`: PASS | FAIL | HOLD — 영역별 Decision을 종합한 최종 판정. 하나라도 FAIL이면 전체는 FAIL이다
- `independent_reviewer`: NOT_RUN | PASS | FAIL | HOLD — §3 파이프라인의 Independent Pass 결과를 별도로 기록하며 `quality_decision`을 덮어쓰지 않는다
- `merge_authorized`: 계약상 항상 `false`로 고정된다. 즉 OReview는 어떤 조합의 PASS로도 Merge를 승인하는 권한을 갖지 않는다 — "APPROVE가 Merge와 다르다"는 수준이 아니라 이 계약 버전에서는 Merge 승인 자체가 발급되지 않는다. Merge는 계약 밖의 별도 절차(Human 승인)로만 이뤄진다
- `final_claim_allowed`: 계약상 항상 `false` — OReview 결과 단독으로 Final 판정을 주장할 수 없다

## 7. Continuous Review
- 파일 저장 또는 Diff 변화 감지
- 저비용 Fast Review
- Commit 전 Full Review
- Push 전 Policy Gate
- PR 생성 후 Independent Review
- CI 결과 반영 후 Merge Review

동일 Finding은 안정적인 Fingerprint로 추적하며 코드 이동만으로 새 Finding으로 중복 생성하지 않는다.

PR 생성 후 Independent Review 단계에서는 동시에 열려있는 다른 Draft PR과의 Multi-PR Integration Risk Scan도 함께 수행한다([02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) OGit).

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