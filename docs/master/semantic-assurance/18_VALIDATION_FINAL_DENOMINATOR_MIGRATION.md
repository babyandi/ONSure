# ONSure Validation Case·Final Acceptance Denominator v2 Migration

Status: `DESIGN_ONLY / CONTRACT_CANDIDATE_CREATED / NON_FINAL`

## 1. 목적
현재 v1의 `minimum_registered`, `expected_count`, package/document count 같은 숫자 기반 완전성 주장을 제거하고, Validation/Final Acceptance를 **exact item identity + item digest + denominator epoch + population digest**로 전환한다.

대상 Candidate Contract:
- `contracts/validation-case-population.candidate.v2.schema.json`
- `contracts/final-acceptance-population.candidate.v2.schema.json`
- `contracts/semantic-denominator-epoch.candidate.v2.schema.json`
- `contracts/assurance-population-denominator.candidate.v2.schema.json`

## 2. v1 Validation Case 현황
현재 `validation-case-registry.v1.json`은 positive/negative/adversarial class마다 최소 등록 수와 case ID를 갖는다. 이 구조는 최소 개수를 지키는 데 유용하지만 다음을 증명하지 못한다.

- 현재 Requirement Universe 전체에서 필요한 Case가 모두 있는지
- 동일 semantic case가 중복 count 되었는지
- case ID의 실제 test code/fixture/oracle bytes가 무엇인지
- case가 current target/requirement/policy epoch에 적용되는지
- legacy case가 current denominator를 부풀리는지
- minimum count 충족이 risk-weighted completeness인지

따라서 v1 registry는 migration source이며 v2 denominator authority가 아니다.

## 3. v2 Validation Case Population
각 Case는 최소 다음을 가진다.
- `case_id`
- `case_sha256`
- `case_class`
- `requirement_ids[]`
- `applicability`
- `applicability_receipt_sha256`
- `oracle_id + oracle_sha256`
- `fixture_id + fixture_sha256`
- `test_class + test_method + test_code_sha256`
- `expected_disposition`

Population 자체는:
- `denominator_epoch`
- `source_registry_sha256`
- exact case set
- `population_digest`
을 가진다.

`minimum_registered`는 참고지표로만 남기고 completeness authority로 사용하지 않는다.

## 4. Case Migration Rule
각 v1 case는 다음 상태 중 하나로 분류한다.
- `MIGRATED_EXACT`: case/test/fixture/oracle exact identity를 모두 확보
- `READBACK_REQUIRED`: test code 또는 fixture/oracle digest 재조회 필요
- `REPERFORMANCE_REQUIRED`: expected result/Oracle 의미를 실제 실행으로 확인 필요
- `SUPERSEDED`: 새 requirement/fixture가 대체
- `LEGACY_REFERENCE_ONLY`: current denominator에는 미포함
- `HOLD`: source identity 또는 applicability를 복원할 수 없음

HOLD/READBACK_REQUIRED case를 포함해 population count를 채웠다고 PASS하지 않는다.

## 5. Final Acceptance Population
Final Acceptance Source는 문서 anchor/count가 아니라 다음 exact identity를 가진다.
- source_id
- source_type
- source_sha256
- authority_class
- source_authority_epoch
- current/superseded state
- acceptance obligation IDs

Final Acceptance Population은 current Requirement/Denominator epoch에 대해 authoritative source set을 고정한다.

## 6. Source Class 최소 후보
- MASTER_REQUIREMENT
- CONTRACT
- POLICY
- SECURITY_PRIVACY
- OPERATION
- RUNTIME_DISCOVERY
- RIGHTS_REMEDY
- EXTERNAL_NORMATIVE

Critical source class가 NOT_PROVEN이면 Final Acceptance Complete를 발급하지 않는다.

## 7. Exact Population Invariant
### Validation Case
- duplicate case_id = 0
- duplicate semantic case는 별도 equivalence review
- current required requirement orphan = 0
- applicable case without fixture/oracle/test digest = 0
- excluded/N/A case는 denominator에서 삭제하지 않고 disposition 유지

### Final Acceptance
- duplicate source_id = 0
- current source without digest = 0
- superseded source가 current authority로 count = 0
- acceptance obligation orphan = 0
- source class NOT_PROVEN이면 positive Final completeness 불가

## 8. Migration Failure Injection
- 같은 case ID를 두 번 넣어 count 충족
- 같은 test method를 여러 case ID로 복제
- current requirement 하나를 denominator에서 삭제
- legacy acceptance source를 current로 재라벨링
- N/A case를 population에서 완전히 제거
- source anchor text는 같지만 source bytes가 변경
- package/document count는 맞지만 item identity 일부 누락

모두 HOLD/FAIL이어야 한다.

## 9. Final Gate 연결
`semantic-assurance-gate-receipt.candidate.v2.schema.json`은 `denominator_epoch`을 소비하며, Final reconstruction은 Validation Case/Final Acceptance population digest가 current epoch에 대한 것인지 재검증해야 한다.

추가 Final reconstruction 입력 후보:
- `validation_case_population_sha256`
- `final_acceptance_population_sha256`

두 population 중 하나라도 stale/unknown이면 Final Candidate PASS 금지.

## 10. 현재 상태
- v2 population schema: CREATED
- v1 source inventory: IDENTIFIED
- exact per-case digest materialization: NOT_RUN
- final acceptance source digest materialization: NOT_RUN
- runtime migration: NOT_RUN
- Final authority: 없음

따라서 현재 상태는 `MIGRATION_DESIGNED / CONTRACT_CANDIDATE_CREATED / POPULATION_MATERIALIZATION_NOT_RUN / NON_FINAL`이다.
