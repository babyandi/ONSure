# ONSURE 설계 권위와 적용 범위 v1

파일명(`_v1`)은 자동화 스크립트(`scripts/preflight-local-assurance.sh`, `scripts/validate_repository_contracts_v2.py`)와 `status/design-conflict-register.v1.json`이 고정 경로로 참조하므로 유지한다. 내용 버전은 아래 개정 이력으로 구분한다.

## 0. 2026-08-07 개정 이력

최초 작성(2026-07-26)은 `docs/05`, `docs/07`을 `docs/master` 상세설계보다 상위 권위로 두었다. 이후 실제 구현(README "현재 구현된 주요 경계"·"명시적으로 미완료인 주요 기능", `kr.co.oruda.onsure.platform` 모듈, Service Case/OLicense 상태 코어)과 2026-08-07 이후의 `docs/master` 개정(00~07)이 서로의 용어를 그대로 공유하며 함께 진행된 반면, `docs/05`·`docs/07`·`docs/41` 등은 2026-07-29 이후 갱신되지 않았다. 이번 개정은 이 실태를 반영해 `docs/master` 전체를 상위 권위로 올리고, 더 이상 갱신되지 않는 구버전 문서군을 `DEPRECATED`로 표시한다. `docs/05`·`docs/07`이 가진 `docs/master`에 없던 개념(Diagnosis 단계의 재현성·최초 실패 지점 프레이밍, 3단계 승인경계, 금융 규제 프레임워크 버전관리, SoD)은 폐기 전에 `docs/master`로 흡수했다(같은 날짜 `docs/master` 커밋 참조).

## 1. 목적

ONSURE 저장소의 다수 설계 문서가 서로 다른 시점과 범위에서 작성되어 개발자와 자동화 도구가 잘못된 문서를 권위로 선택하는 문제를 방지한다.

이 문서는 설계 문서의 우선순위, 적용 범위, 충돌 처리와 구현 상태 표현을 고정한다.

## 2. 권위 우선순위

동일 주제에서 내용이 충돌할 경우 다음 순서로 적용한다.

1. `README.md`의 독립 제품 원칙과 제품 경계
2. `docs/master/00_ONSURE_MASTER_DESIGN_SET.md`
3. `docs/master/01_BUSINESS_PRODUCT_SERVICE_PLAN.md`
4. `docs/master/02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md`
5. `docs/master/03_OREVIEW_CODE_REVIEW_SPECIFICATION.md`
6. `docs/master/04_ARCHITECTURE_DATA_API_OLICENSE.md`
7. `docs/master/05_UI_UX_WORKFLOW_SPECIFICATION.md`
8. `docs/master/06_TEST_OPERATION_IMPLEMENTATION_PLAN.md`
9. `docs/master/07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md`
10. `docs/09_PROGRAM_LEARNING_METHODOLOGY.md`
11. 기계 판정 계약과 JSON Schema
12. `docs/v2`, 개별 Architecture, 과거 Runbook과 참고 자료
13. DEPRECATED 문서군(§3-1) — 신규 설계 근거로 인용 금지, 과거 맥락 참고 전용

상위 권위 문서를 하위 문서가 묵시적으로 변경할 수 없다. 변경하려면 상위 문서와 추적성 대장을 같은 변경에서 갱신해야 한다.

## 3-1. DEPRECATED 문서군

다음은 v2 기준 설계 근거로 사용하지 않는다. 실제 구현·README와의 용어 정합성이 끊어졌으며 후속 갱신이 중단되었다. 각 문서에서 유효했던 개념은 위 §0에 명시된 대로 `docs/master`로 흡수 완료했다.

- `docs/05_PRODUCT_REQUIREMENTS_AND_ACCEPTANCE.md`
- `docs/07_CORE_ARCHITECTURE_AND_STATE_MODEL.md`
- `docs/40_FINAL_PRODUCT_RESEARCH_AND_ROLE_MODELS.md`
- `docs/41_ONSURE_FINAL_TARGET_ARCHITECTURE.md`
- `docs/42_VSCODE_AGENT_AND_GIT_FULL_CHAIN_DESIGN.md`
- `docs/43_FINANCIAL_CONTROL_TRACE_AND_ACCEPTANCE.md`
- `docs/44_UNIFIED_AI_WORK_DEVELOPER_ASSURANCE_DESIGN.md`

이 문서들을 참조하는 자동화 스크립트나 계약이 있다면, `docs/master`의 대응 개념으로 재결속하는 별도 변경이 필요하다(§6 변경 규칙).

## 3. 제품 경계

ONSURE Core는 다음을 독립적으로 제공해야 한다.

- Workspace Intake
- Program, Behavior, Improvement Learning
- Planning, Review, Verification, Diagnosis, Improvement
- Evidence, Memory, Git Full-Chain
- CLI와 Local Authenticated API
- VS Code Extension

ORUDA 관련 코드는 선택형 Target Adapter다. ORUDA 파일, Fixture, 정책, 실행기 또는 저장소가 없다는 이유로 ONSURE Core Preflight가 실패해서는 안 된다.

## 4. 구현 상태 용어

- `IMPLEMENTED`: 권위 요구사항에 맞는 실행 경로와 자동 시험이 존재한다.
- `PARTIAL`: 요구사항 일부만 실제 구현됐다.
- `STUB`: 클래스·명령·파일은 존재하지만 고정값, Marker 검색, 호출자 Boolean 등에 의존한다.
- `DESIGN_ONLY`: 설계 또는 계약만 존재한다.
- `NOT_RUN`: 실행 가능한 코드가 있으나 현재 기준선 증적이 없다.
- `BLOCKED`: 필수 입력·환경·독립 권한 또는 선행 구현이 없어 진행할 수 없다.
- `CONFLICT`: 권위 문서·계약·코드가 서로 다른 의미를 정의한다.
- `DEPRECATED`: 현행 기준선에서 사용하면 안 되는 과거 경로다.

파일 존재, 컴파일 성공, 단위시험 존재만으로 제품 기능을 `IMPLEMENTED`로 판정하지 않는다.

## 5. 판정 상한

Codespace 또는 동등한 격리 실행환경에서 현재 `main`을 대상으로 Full-Chain을 실행하고 독립 검증을 완료하기 전까지 다음을 금지한다.

- Final PASS
- Final Audit PASS
- FinalLock
- Production GO
- Commercial GO
- Expert Verified

## 6. 변경 규칙

모든 기능 변경은 다음 연결을 갱신해야 한다.

`Requirement -> Design -> Contract/Schema -> Code -> Test -> Evidence -> Status`

연결이 끊기면 `IMPLEMENTED`가 아니라 `PARTIAL`, `STUB`, `NOT_RUN` 또는 `BLOCKED`로 판정한다.

## 7. 현재 기준선 판정

현재 구현은 검증 하네스, 파일 기반 증적, 일부 학습 원장과 자동 실행 보조 기능을 포함한다. Standalone 제품 전체 구현, 실제 VS Code Extension, OReview, 실제 Program/Behavior Learning, Patch/Git Full-Chain, Web/Commerce/OLicense는 완료되지 않았다.
