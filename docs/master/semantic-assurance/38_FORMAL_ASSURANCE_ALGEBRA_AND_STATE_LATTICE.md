# ONSure Formal Assurance Algebra·State Lattice 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `FR-META-009, 037, 048, 049, 060`, `30_DISTRIBUTED_ASSURANCE_COMPOSITION_AND_EVIDENCE_GRAPH.md`

## 1. 목적
PASS/FAIL, Assurance Level, Currentness, Unknown, Human Acceptance, Production 상태를 하나의 문자열이나 점수로 섞지 않고 **서로 다른 축을 가진 formal assurance tuple**로 정의한다. Product Composition Engine과 UI/API가 동일 의미를 사용하도록 canonical semantics를 제공한다.

## 2. Assurance Tuple
모든 material Claim/Subject result는 최소 다음 tuple을 가진다.

`A = (D, S, C, U, I, Q)`

- `D` Decision: 실제 판정 결과
- `S` Strength: 그 판정을 뒷받침하는 검증 강도
- `C` Currentness: 현재도 유효한가
- `U` Uncertainty: 미확인/관찰불가/충돌 상태
- `I` Independence: 독립성 수준/프로파일
- `Q` Qualification: validator/oracle/adapter 자격 상태

Human Acceptance, Deployment Authorization, Commercial Authorization은 Assurance tuple 밖의 별도 Governance axis다.

## 3. Decision Domain D
Canonical 후보:
- PASS
- FAIL
- HOLD
- BLOCKED
- NOT_RUN
- INCONCLUSIVE
- NON_FINAL
- NOT_APPLICABLE

`UNKNOWN`은 Decision이 아니라 U 축의 상태로 우선 표현한다. 기존 계약에서 UNKNOWN이 decision-like value로 존재하면 migration mapping을 명시해야 한다.

### 3.1 Decision 우선순위는 단일 total order가 아님
FAIL과 BLOCKED는 서로 다른 의미다. 따라서 `max()` 하나로 합성하지 않는다.

Composition rule은 다음 category를 먼저 분류한다.
- NEGATIVE: FAIL
- UNRESOLVED: HOLD|BLOCKED|NOT_RUN|INCONCLUSIVE|NON_FINAL
- POSITIVE: PASS
- EXCLUDED: NOT_APPLICABLE

Critical HARD child가 NEGATIVE이면 parent positive 금지.
Critical HARD child가 UNRESOLVED이면 parent PASS 금지.
EXCLUDED는 applicability proof가 있을 때만 denominator에서 제외.

## 4. Strength Domain S
최종 명칭은 OD-A01 확정 전까지 `S0~S5` logical level로 정의하고 UI label과 분리한다.
- S0 UNASSESSED
- S1 EXECUTED_OR_STATIC_OBSERVED
- S2 EVIDENCE_BOUND
- S3 INDEPENDENTLY_REPERFORMED
- S4 QUALIFIED_ASSURANCE
- S5 PRODUCTION_BOUND_HIGH_ASSURANCE

기존 L0~L5/AL0~AL5와의 display mapping은 별도 Profile에서 해결한다.

### 4.1 Strength Monotonicity
필수 proof가 추가될 때만 상향 가능하다.
- S1→S2: exact evidence/context binding
- S2→S3: valid independence profile + independent execution receipt
- S3→S4: current validator/oracle/adapter qualification
- S4→S5: required production deployment/runtime currentness closure

caller boolean, run count, model name 차이만으로 상향 금지.

## 5. Currentness Domain C
- CURRENT
- STALE
- REASSESSMENT_REQUIRED
- INVALIDATED
- REVOKED
- UNKNOWN

Currentness는 발급 당시 D/S를 삭제하지 않는다.
예: `PASS@S4 + C=STALE`은 historical qualified PASS이지만 현재 positive assurance로 사용 불가.

## 6. Uncertainty Domain U
Set-valued dimension:
- KNOWN_UNKNOWN
- DISCOVERED_UNKNOWN
- UNRESOLVED_CONFLICT
- UNCLASSIFIED_SURFACE
- UNOBSERVABLE
- MISSING_EVIDENCE
- MISSING_AUTHORITY
- MISSING_QUALIFICATION
- OFFLINE_REVOCATION_UNCERTAINTY

`U=∅`가 곧 완전성 증명은 아니다. Denominator/observability discovery proof가 별도로 필요하다.

## 7. Independence Domain I
독립성은 scalar 하나가 아니라 profile이다.
- execution
- principal
- credential_admin
- implementation
- oracle
- discovery
- knowledge

Product-level summary는 required axes의 minimum/closure로 계산하지만 원 profile을 보존한다.

## 8. Qualification Domain Q
- QUALIFIED
- PARTIAL
- NOT_PROVEN
- STALE
- REVOKED
- NOT_APPLICABLE

Target archetype/defect class별로 관리한다. 전역 QUALIFIED 금지.

## 9. Assurance Ceiling 함수
`ceiling(A, context)`는 다음을 적용한다.

### Rule C01 — Missing Evidence
D=PASS라도 material claim evidence binding 없음 → S≤S1, D positive publication HOLD.

### C02 — No Independence
independence required context에서 self-validation only → S≤S2.

### C03 — Qualification Not Proven
required Q=NOT_PROVEN/PARTIAL → S≤policy-defined ceiling, high-assurance certificate 금지.

### C04 — Currentness
C != CURRENT이면 S historical value는 보존하되 `current_positive_eligible=false`.

### C05 — Critical Unknown
Critical affected U non-empty → parent PASS/high assurance 금지.

### C06 — Accepted Risk
Accepted Risk는 D를 PASS로 변환하지 않는다. policy별 Certificate ceiling만 계산.

## 10. Dependency Composition Operator
Parent P와 children `A1...An`, dependency edge `E1...En`에 대해:

1. applicable population lock
2. HARD/CONDITIONAL/SOFT 분류 검증
3. child currentness/strength/decision normalization
4. negative propagation
5. unresolved propagation
6. strength minimum ceiling
7. currentness ceiling
8. uncertainty union + impact reduction proof
9. result tuple 생성

### 10.1 HARD
필수 child의 positive current assurance가 없으면 parent positive current assurance 금지.

### 10.2 CONDITIONAL
condition proof가 true인 execution/context에서 HARD처럼 적용. condition evaluation evidence가 없으면 unresolved.

### 10.3 SOFT
child 문제의 parent non-impact evidence가 있어야 parent decision을 유지할 수 있다. `SOFT` 라벨 자체는 proof가 아니다.

### 10.4 INFORMATIONAL
parent claim truth에 직접 영향하지 않지만 explanation/audit에는 보존.

## 11. Currentness Composition
Required child currentness set을 기준으로:
- any REVOKED affecting claim → parent cannot CURRENT
- any INVALIDATED affecting claim → parent cannot CURRENT
- any REASSESSMENT_REQUIRED affecting claim → parent ≤ REASSESSMENT_REQUIRED
- any STALE affecting claim → parent ≤ STALE 또는 policy에 따른 REASSESSMENT_REQUIRED
- any UNKNOWN required child → parent UNKNOWN/REASSESSMENT_REQUIRED ceiling

정확한 STALE vs REASSESSMENT precedence는 policy profile로 versioning한다.

## 12. Conflict Algebra
동일 canonical claim/context에 서로 다른 result가 존재할 때:
- explicit SUPERSEDES relation + valid supersession authority → superseding result
- mutually exclusive observation with later raw-state truth proof → truth-authoritative result
- otherwise `UNRESOLVED_CONFLICT` + HOLD

Timestamp latest-wins 금지.

## 13. Retry Algebra
Attempt는 result replacement가 아니라 history graph다.
- FAIL→PASS는 final attempt PASS일 수 있으나 flakiness/previous failure disposition이 남는다.
- Critical failure의 원인이 환경 오류였다는 proof 없이는 단순 retry PASS가 original failure를 무효화하지 않는다.
- aggregate metrics에는 attempt와 logical case를 구분한다.

## 14. N/A Algebra
NOT_APPLICABLE을 denominator에서 제외하려면:
- applicability rule id/version
- evaluated target/requirement digest
- rationale
- evidence
- evaluator authority
- challenge/review disposition

없으면 N/A가 아니라 unresolved exclusion이다.

## 15. Certificate Eligibility Predicate
`certificateEligible(A_product, context)`는 최소:
- D=PASS-compatible
- required U closure
- S ≥ product tier minimum
- Q sufficient
- required I sufficient
- C=CURRENT 또는 certificate type이 historical-only로 명시
- no unresolved material conflict
- exact composition snapshot
- authority/freshness valid

Historical Certificate는 current product guarantee와 다른 type/profile로 구분한다.

## 16. UI/API Serialization
API는 최소 다음을 별도 필드로 반환한다.
```text
decision
assurance_strength
currentness
uncertainty[]
independence_summary
qualification_summary
current_positive_eligible
ceiling_reasons[]
```
`status=PASS` 하나로 축약하는 endpoint를 금지한다.

## 17. Cross-contract Invariant
- PASS + S0 불가 또는 migration-only legacy state
- S3 이상 + required independence receipt 없음 불가
- S4 이상 + current qualification 없음 불가
- S5 + current production identity 없음 불가
- CURRENT + known material invalidation unresolved 불가
- REVOKED + positive-current eligible=true 불가
- N/A + applicability proof 없음 불가

## 18. Fixture
- PASS/S5 + runtime UNKNOWN
- PASS/S4 + Q=NOT_PROVEN
- PASS/S3 + self-validation-only
- parent PASS + critical HARD child HOLD
- parent CURRENT + required child STALE
- N/A without proof
- conflicting result latest-wins
- retry PASS erases critical failure

## 19. 완료조건
- Composition Engine, Certificate Service, UI/API가 같은 algebra profile digest 사용
- rule version이 Receipt/CompositionSnapshot/Certificate에 결속
- profile 변경 시 historical impact analysis
- 저장된 product score가 algebra 결과를 대체하지 않음

## 20. 비최종 경계
이 문서는 canonical semantics 후보이며 OD-A01 등 미확정 naming/policy를 임의 확정하지 않는다. Machine rule profile/fixture/runtime 제정 전까지 DESIGN_ONLY다.
