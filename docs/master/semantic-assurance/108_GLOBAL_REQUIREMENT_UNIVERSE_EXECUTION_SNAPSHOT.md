# 108 Global Requirement Universe Execution Snapshot

Status: `EXECUTED_PARTIAL / NON_FINAL`
Frozen input commit: `abe122ef9438f41e8b074f03b8017b6d30cbc389`

## 실제 materialization 결과
현재 branch 정본에서 명시 ID로 직접 확인 가능한 Requirement population을 먼저 고정했다.

- `FR-COM-001..013`: 13
- `FR-META-001..060`: 60
- explicit-ID subtotal: **73**

이 73은 전체 Requirement Universe가 아니다. `docs/master/02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md`와 기타 authority 문서에는 ID 없는 Program 기능, acceptance criterion, NFR, architecture invariant, policy/regulatory requirement가 존재하므로 global total은 아직 `NOT_YET_MATERIALIZED`다.

## Deterministic ID 규칙
ID 없는 항목은 source authority path + section anchor + normalized requirement text로 source_key를 만들고 `REQ-AUTO-<sha256-prefix>`를 후보 ID로 사용한다. 사람이 정한 의미 ID를 덮어쓰지 않는다.

## Anti-False-Completion
- explicit 73 != global denominator
- 문서 heading 수 != requirement 수
- duplicate 제거 전 count != authoritative population
- UNKNOWN extraction scope를 0으로 간주하지 않는다.

## 다음 입력
Global universe completion은 01~08/08A와 requirement authority로 지정된 companion/policy 문서 전체의 비ID requirement extraction이 끝나야 한다.
