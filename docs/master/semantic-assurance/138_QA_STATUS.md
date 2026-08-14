# 138 Design QA Requirement Universe Status

Status: GLOBAL_REQUIREMENT_UNIVERSE_PARTIAL / NON_FINAL

이 문서는 새 제품 설계를 추가하지 않고 main 기준선의 요구사항 분모를 검증한다.

기준 commit: 43d560b5964d4942eeb1728c0c62565764ac348b

현재 안정 ID가 확인된 최소 population은 다음과 같다.
- FR-COM-001~013: 13
- FR-META-001~062: 62
- FR-FRESH-001~003: 3
- NFR: SEC, REL, PERF, AVAIL, AUDIT, PORT, PRIV, OBS, ACCESS, SESSION, CONFIG: 11

따라서 현재 explicit floor는 89건이다.

89건은 Global total이 아니다. Program 기능, 산출물 의무, 수용기준, Architecture invariant, Policy/Regulatory 요구사항 가운데 독립 ID가 없는 normative statement를 더 materialize해야 한다.

Global denominator source class:
1. Explicit FR-COM
2. Explicit FR-META
3. Explicit FR-FRESH
4. Explicit NFR
5. Program function
6. Program output obligation
7. Program acceptance criterion
8. Architecture invariant
9. Policy requirement
10. Regulatory/industry requirement
11. Safety/Hazard requirement
12. Contestability/Appeal requirement
13. Cross-cutting fail-closed requirement

ID가 없는 requirement는 REQ-{OWNER}-{CLASS}-{NNN} 규칙으로 persistent ID를 부여한다. 기존 ID는 변경하지 않는다.

중복·관계 disposition은 UNIQUE, DUPLICATE_OF, REFINES, SUPERSEDES, CONFLICTS_WITH, EXAMPLE_ONLY, NON_NORMATIVE_CONTEXT로 구분한다. 중복·정제·대체 관계는 global denominator를 이중 계산하지 않는다.

Global Requirement Universe completion gate:
- 모든 source class scan 완료
- normative candidate disposition 완료
- stable requirement ID 100% 부여
- duplicate/refine/supersede 관계 정리
- unresolved P0 semantic conflict 0
- exact count 산출
- canonical population digest 산출
- source baseline commit 결속

현재 판정: EXPLICIT_FLOOR_89 / NON_ID_POPULATION_NOT_EXACT / GLOBAL_DENOMINATOR_NOT_LOCKED.
