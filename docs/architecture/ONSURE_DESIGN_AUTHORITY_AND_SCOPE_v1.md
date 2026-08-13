# ONSURE 설계 권위와 적용 범위 v1

파일명(`_v1`)은 자동화 스크립트와 기존 참조가 고정 경로로 사용하므로 유지한다. 내용 버전은 개정 이력으로 구분한다.

## 0. 개정 이력
- 2026-08-07: `docs/master`를 현행 제품 설계 정본으로 승격하고 과거 `docs/05`, `docs/07`, `docs/40~44`를 DEPRECATED 처리.
- 2026-08-13: Semantic Assurance 확장, Safety/Hazard, Contestability/Appeal, Design QA, Claude 개발 Handoff가 main에 병합된 이후 권위·supersession 체계를 재정의. Product Design Scope와 Design QA/Implementation 상태를 분리하고 machine semantic contract의 역할을 명시한다.

## 1. 목적
ONSURE 저장소의 다수 설계 문서·계약·상태파일·코드가 서로 다른 시점과 범위에서 작성될 때 잘못된 정본을 선택하는 문제를 방지한다. 이 문서는 설계 권위, 적용 범위, 충돌 처리, supersession, 구현 상태 표현을 고정한다.

## 2. 권위 계층
동일 의미영역에서 충돌이 발생할 때는 문서 종류가 아니라 **책임 레이어**를 먼저 판별한다.

### L0 — 제품 불변 원칙 / Trust Boundary
1. `README.md`의 독립 제품 원칙, fail-closed, Standalone-first, 금지된 Final/Production authority
2. 이 문서 `ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md`

L0는 상세 필드·상태값·operation count를 임의로 재정의하지 않는다.

### L1 — 제품·기능·아키텍처 정본
3. `docs/master/00_ONSURE_MASTER_DESIGN_SET.md`
4. `docs/master/01_BUSINESS_PRODUCT_SERVICE_PLAN.md`
5. `docs/master/02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md`
6. `docs/master/03_OREVIEW_CODE_REVIEW_SPECIFICATION.md`
7. `docs/master/04_ARCHITECTURE_DATA_API_OLICENSE.md`
8. `docs/master/05_UI_UX_WORKFLOW_SPECIFICATION.md`
9. `docs/master/06_TEST_OPERATION_IMPLEMENTATION_PLAN.md`
10. `docs/master/07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md`
11. `docs/master/08_REVIEW_CHECKLIST_OPEN_DECISIONS.md`
12. `docs/master/08A_ASSURANCE_POLICY_AND_OPEN_DECISION_INTEGRATION.md`

### L2 — Semantic Assurance / 확장 정본
13. `docs/master/semantic-assurance/*`

L2는 L1에 없는 상세 Assurance semantics를 정의한다. 동일 주제에서 L1과 L2가 충돌하면 다음 규칙을 적용한다.
- L1이 명시적으로 `DESIGN_ONLY` 또는 companion 위임을 선언한 경우 L2가 상세 권위다.
- L1의 명시적 고정 invariant를 L2가 약화할 수 없다.
- supersession 문서가 있으면 가장 최신 canonical supersession 판정을 따른다.
- Product Design Scope closure의 최신 판정은 `128_FINAL_FRESH_REVIEW_RERUN_AND_PRODUCT_DESIGN_SCOPE_CLOSURE.md`다.
- Phase/구현 시작 상태의 최신 판정은 `136_PRE_MERGE_STATUS_CORRECTION_AND_BASELINE_HANDOFF.md`다.
- Claude 개발의 최신 단일 진입점은 `137_CLAUDE_DEVELOPMENT_MASTER_HANDOFF.md`다.

### L3 — Machine Semantic Contract
14. `contracts/*.json`, `contracts/*.schema.json` 중 해당 의미영역의 canonical/active contract

Machine Contract는 문서 설명을 실행 가능한 필드·enum·불변식으로 materialize한다. 같은 의미영역에서 **active canonical contract와 문서의 예시/숫자가 충돌하면 contract의 필드·enum·operation population이 실행 권위**다. 단 Candidate/Design-only contract는 Active 의미를 자동 대체하지 않는다.

### L4 — 구현 / 테스트 / Evidence
15. 실행 코드
16. 자동 Test/Fixture
17. Evidence/Receipt/Status

구현은 상위 의미를 변경할 권위가 없다. 코드가 다른 의미를 구현했다면 `IMPLEMENTATION_DRIFT` 또는 Design Change Queue 대상이다.

### L5 — 참고/과거 문서
18. `docs/09_PROGRAM_LEARNING_METHODOLOGY.md`
19. `docs/v2`, 개별 과거 Architecture/Runbook
20. DEPRECATED 문서군

## 3. Supersession 규칙
- 파일 번호가 크다는 이유만으로 자동 우선하지 않는다.
- `SUPERSEDED`, `SUPERSEDED_BY`, `canonical owner`를 명시한 최신 판정만 supersession authority를 가진다.
- 조기 Fresh Review 결론은 `128`의 supersession 표를 따른다.
- `130~135`에서 Claude 개발이 진행된 것처럼 보일 수 있는 표현은 `136`에 의해 상태 의미가 정정된다.
- Product Design Scope `COMPLETE_CANDIDATE`는 Design QA PASS, Design Lock, 구현 완료를 의미하지 않는다.

## 4. DEPRECATED 문서군
신규 설계 근거로 사용하지 않는다.
- `docs/05_PRODUCT_REQUIREMENTS_AND_ACCEPTANCE.md`
- `docs/07_CORE_ARCHITECTURE_AND_STATE_MODEL.md`
- `docs/40_FINAL_PRODUCT_RESEARCH_AND_ROLE_MODELS.md`
- `docs/41_ONSURE_FINAL_TARGET_ARCHITECTURE.md`
- `docs/42_VSCODE_AGENT_AND_GIT_FULL_CHAIN_DESIGN.md`
- `docs/43_FINANCIAL_CONTROL_TRACE_AND_ACCEPTANCE.md`
- `docs/44_UNIFIED_AI_WORK_DEVELOPER_ASSURANCE_DESIGN.md`

## 5. 제품 경계
ONSURE Core는 Workspace Intake, Program/Behavior/Improvement Learning, Planning, Review, Verification, Diagnosis, Improvement, Evidence, Memory, Git Full-Chain, CLI, Local Authenticated API, VS Code Extension을 독립 제공해야 한다. ORUDA 관련 코드는 선택형 Target Adapter다.

## 6. 구현 상태 용어
- `IMPLEMENTED`: 권위 요구사항에 맞는 실행 경로와 자동 시험이 존재
- `PARTIAL`: 일부만 구현
- `STUB`: 파일/클래스가 있으나 고정값·Marker·caller Boolean 등에 의존
- `DESIGN_ONLY`: 설계/후보 계약만 존재
- `NOT_RUN`: 실행 가능한 코드가 있으나 현재 기준선 증적 없음
- `BLOCKED`: 필수 입력·환경·독립 권한·선행 구현 없음
- `CONFLICT`: 권위 문서/계약/코드가 다른 의미 정의
- `DEPRECATED`: 현행 기준선에서 사용 금지

파일 존재, 컴파일 성공, 단위시험 존재만으로 `IMPLEMENTED`를 선언하지 않는다.

## 7. 판정 상한
Full-Chain 실행과 독립 검증 전까지 Final PASS, Final Audit PASS, FinalLock, Production GO, Commercial GO, Expert Verified를 금지한다.

## 8. 변경 규칙
모든 기능 변경은 다음 연결을 갱신한다.
`Requirement -> Design -> Contract/Schema -> Code -> Test -> Evidence -> Status`

설계 공백/충돌은 `contracts/design-change-queue.v1.json`을 사용한다. 구현 편의를 위해 상위 의미를 묵시적으로 바꾸지 않는다.

## 9. 현재 기준선 판정
- Product Design Scope: `COMPLETE_CANDIDATE`
- Design QA / Baseline Lock: `IN_PROGRESS / HOLD`
- Claude Implementation: `NOT_STARTED` (137 기준 개발 진입 준비 완료)
- Test / Independent Assurance / Production: `NOT_STARTED`
- Production/Commercial authority: 없음

이 상태는 구현 착수 가능성을 의미하지만 `IMPLEMENTATION_READY_DESIGN_BASELINE` 최종 승격은 Authority/정본/핵심 Contract/Open Decision binding의 개발 전 정합성 보강 완료 후 별도 판정한다.
