# ONSure DesignTraceRegistry Machine 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `53_END_TO_END_DESIGN_TRACEABILITY_MATRIX.md`

## 1. 목적
Requirement→Review→Architecture→UX→Test→Contract→Operation→Evidence→Independent Verification 연결을 사람이 문서로만 추적하지 않고 machine-readable registry로 관리한다.

## 2. Entry
- trace_id
- capability_id
- requirement_refs[]
- review_rule_refs[]
- architecture_refs[]
- ux_refs[]
- test_refs[]
- governance_refs[]
- contract_refs[]
- operation_refs[]
- evidence_type_refs[]
- independent_verification_refs[]
- qualification_refs[]
- status_by_dimension
- owner
- last_updated_at

## 3. Status
각 dimension:
`MISSING | DESIGNED | CONTRACTED_CANDIDATE | IMPLEMENTATION_CANDIDATE | EXECUTED | EVIDENCE_BOUND | INDEPENDENTLY_VERIFIED | QUALIFIED`

상위 status가 하위 단계 존재를 자동 함의하지 않는다. 예: 코드가 있어도 Contract가 MISSING일 수 있으며 이는 gap이다.

## 4. Gap Rule
- Requirement 있으나 Test 없음 → T-GAP
- Contract 있으나 Operation 없음 → O-GAP
- Operation 있으나 Evidence 없음 → E-GAP
- Strong claim인데 Independent Verification 없음 → I-GAP
- P0 capability에 C/O/E/I gap 존재 → Final Gate blocker

## 5. Coverage 계산
퍼센트 하나만 저장하지 않고:
- exact capability population
- dimension별 complete count
- gap list
- excluded capability + rationale
- registry generation/digest
을 저장한다.

## 6. 변경 영향
Requirement/Contract/Operation이 변경되면 trace entry를 stale로 만들고 downstream test/evidence/qualification 재평가를 요구한다.

## 7. Machine Validation
- 모든 FR-META-001~060이 registry에 존재
- 29~64 P0 design capability가 capability population에 존재
- dangling ref 없음
- duplicate capability identity 없음
- `QUALIFIED`인데 C/O/E/I 중 MISSING인 entry 금지

## 8. Negative Test
- Requirement만 있고 모든 downstream ref 빈 상태를 COMPLETE로 표시
- unknown file/ref를 trace로 사용
- duplicate capability 두 entry
- Contract 변경 후 stale trace가 ACTIVE 유지
- exact population 없이 coverage_percent=100

## 9. 수용기준
설계/개발/검증 완성도는 DesignTraceRegistry의 exact population과 dimension 상태에서 재계산 가능해야 하며, 사람이 임의로 '완료' 표기를 올릴 수 없다.
