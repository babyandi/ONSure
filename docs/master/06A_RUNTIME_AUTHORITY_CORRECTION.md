# 06A Runtime Authority Correction

Status: `ACTIVE_CORRECTION_COMPANION / NON_FINAL`
Parent: `06_TEST_OPERATION_IMPLEMENTATION_PLAN.md`

이 문서는 06 본문의 과거 Operation count 표현과 현재 Active machine registry 사이의 숫자 drift를 제거하기 위한 정정 companion이다.

## 1. Workflow Operation Count
현재 Active 단일 권위는 `contracts/workflow-operation-registry.v1.json`이다.

- Active operation count: **49**
- `06_TEST_OPERATION_IMPLEMENTATION_PLAN.md`의 과거 **45개** 표현: `SUPERSEDED_DESCRIPTIVE_COUNT`
- Candidate extension: `contracts/workflow-operation-extension.candidate.v2.json`의 13개
- Candidate 13개는 아직 Active가 아니므로 Active count를 62로 변경하지 않는다.

문서·README·UI·테스트에서 Operation 수를 표시할 때 하드코딩하지 않고 Active registry의 `operation_count`를 읽는다.

## 2. Phase Status Correction
현재 Phase 상태는 `136_PRE_MERGE_STATUS_CORRECTION_AND_BASELINE_HANDOFF.md`와 `contracts/runtime-authority-reconciliation.v1.json`을 따른다.
- Product Design Scope: COMPLETE_CANDIDATE
- Design QA: IN_PROGRESS/HOLD
- Claude Implementation: NOT_STARTED at the pre-development authority point
- Test/Independent Assurance/Production: NOT_STARTED

## 3. Implementation Rule
새 Operation은 `Requirement → Design → Contract → Authority → Dispatcher → Surface → Test → Receipt/Evidence → Trace`가 연결되기 전에는 Active registry에 넣지 않는다.

## 4. Anti-drift
Operation count를 별도 상태문서에 손으로 복제하지 않는다. 숫자를 설명해야 하는 문서는 registry path와 generation을 함께 기록한다.
