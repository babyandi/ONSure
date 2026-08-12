# ONSure Requirement Universe & Discovery Authority 설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
검증 완료율과 Coverage는 denominator를 누가 어떻게 만들었는지에 따라 달라진다. 본 문서는 Requirement Universe와 Discovery Authority를 별도 권위 객체로 정의해 scope 축소, false N/A, count inflation을 막는다.

## 2. Requirement Universe
Requirement Universe는 단순 목록이 아니라 다음 source class를 포함한다.
- explicit business requirement
- functional requirement
- non-functional requirement
- security/privacy/legal obligation
- architecture/data invariant
- runtime/deployment obligation
- AI/agent/model obligation
- inferred requirement
- external standard/regulation
- operational/recovery obligation

## 3. Requirement Identity
각 requirement는:
- requirement_id
- source_id/source_digest
- source_location
- semantic statement digest
- parent/child relation
- criticality
- applicability conditions
- authority class
- discovered_at
- requirement_epoch
을 가진다.

## 4. Discovery Authority
Discovery 결과는 어떤 방법으로 발견했는지 기록한다.
- MANUAL_DECLARED
- STATIC_DISCOVERY
- RUNTIME_DISCOVERY
- ARCHITECTURE_INFERENCE
- EXTERNAL_STANDARD_IMPORT
- ADVERSARIAL_DISCOVERY
- INDEPENDENT_DISCOVERY

한 방법만으로 universe completeness를 주장하지 않는다.

## 5. Denominator Authority
Coverage denominator는 exact requirement IDs+digests의 population이다. count는 derived metric일 뿐 authority가 아니다.

## 6. Applicability
각 requirement disposition:
- APPLICABLE
- NOT_APPLICABLE_JUSTIFIED
- EXCLUDED_WITH_AUTHORITY
- UNKNOWN
- UNOBSERVABLE
- SUPERSEDED

N/A는 evidence와 authority receipt를 요구한다.

## 7. Unknown Discovery
`unknown=0`은 completeness proof가 아니다. unknown discovery method가 실제 실행됐는지 증명해야 한다.

필수 evidence:
- discovery method set
- search space
- blind spots
- unsupported source classes
- observed unknowns
- unobservable regions

## 8. Epoch
다음 변경은 requirement epoch 증가:
- source requirement 변경
- code/architecture로 신규 obligation 발견
- external standard/regulation 변경
- N/A/exclusion 변경
- criticality 변경
- discovered hidden requirement 추가

과거 Coverage는 stale 처리한다.

## 9. Conflict
두 source가 충돌하면 임의 우선순위를 적용하지 않고 RequirementConflict를 생성한다.

필드:
- conflicting requirement IDs
- source authority
- impact
- proposed resolution
- decision authority
- expiry/review date

## 10. Traceability
`Requirement -> Validation Case -> Fixture/Oracle -> Execution -> Evidence -> Claim`

어느 edge든 없으면 requirement는 covered가 아니다.

## 11. Coverage Metric
최소:
- applicable total
- executed
- passed
- failed
- blocked
- hold
- not run
- unknown
- unobservable
- excluded

Coverage 100%는 applicable exact population의 execution closure를 의미한다.

## 12. Independent Challenge
Critical N/A, exclusion, unknown=0, completeness claim은 독립 challenge lane 대상이다.

## 13. Negative Fixture
1. duplicate requirement count inflation
2. critical requirement 삭제
3. hidden requirement 발견 후 old epoch PASS reuse
4. N/A without evidence
5. exclusion without authority
6. count same / item set changed
7. unsupported source class omitted
8. inferred requirement discarded
9. conflict silently resolved
10. legacy requirement mixed into current denominator

## 14. UI
Coverage 화면은 count보다 exact population 상태와 source class별 blind spot을 보여준다.

## 15. Claude 개발 경계
구현 순서:
1. RequirementUniverseSnapshot
2. DiscoveryReceipt
3. ApplicabilityDispositionReceipt
4. denominator population builder
5. epoch/invalidation
6. independent challenge
7. traceability closure

## 16. 현재 상태
- 설계: PRESENT
- denominator schema candidate: 존재
- full universe runtime: NOT_IMPLEMENTED
- execution: NOT_RUN
