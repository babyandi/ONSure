# ONSure Semantic Assurance v2 Contract 이행·검증 계획

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
`11_CONTRACT_UPGRADE_BLUEPRINT.md`와 `12_P0_VERTICAL_TRACEABILITY_AND_APPLICATION.md`에서 정의한 P0 개선사항을 실제 v2 Contract로 이행할 때 필요한 **호환성, migration, validator, negative fixture, runtime adoption, evidence, rollback** 순서를 고정한다.

현재 생성된 v2 candidate:
- `contracts/assurance-status-vocabulary.candidate.v2.schema.json`
- `contracts/assurance-receipt-envelope.candidate.v2.schema.json`
- `contracts/authority-principal-profile.candidate.v2.schema.json`
- `contracts/semantic-assurance-gate-receipt.candidate.v2.schema.json`

이 파일들은 Candidate Schema이며 현재 v1 authority를 대체하지 않는다.

## 2. Migration 공통 원칙
1. **Dual Read, Single Authority**: transition 동안 v1/v2 read는 가능하되 동일 logical state에 두 writer가 존재하지 않는다.
2. **No Silent Upgrade**: v1 PASS를 v2 PASS로 자동 변환하지 않는다. 부족한 field는 `INPUT_REQUIRED|HOLD|NON_FINAL`로 남긴다.
3. **Semantic Preservation**: v1→v2 adapter가 nonce, expiry, authority, scope, source, independence, qualification, freshness를 추정으로 채우지 않는다.
4. **Fail Closed on Unknown**: v1에서 알 수 없는 field를 default 안전값인 것처럼 생성하지 않는다.
5. **Historical Integrity**: 과거 v1 receipt bytes는 수정하지 않고 v2 reconstruction/adaptation receipt가 별도 parent로 참조한다.
6. **Rollbackable Selector**: active contract version selector를 되돌릴 수 있어야 하며 rollback 자체도 receipt를 남긴다.
7. **No Final Gate Activation Before Qualification**: v2 schema 존재만으로 Final Gate에 편입하지 않는다.

## 3. Phase 0 — Contract Static Qualification
### 목표
Schema 자체가 의도한 P0 invariant를 표현하는지 검증한다.

### 필수 검사
- JSON Schema 2020-12 meta validation
- `$id` uniqueness
- required field completeness
- `additionalProperties:false` boundary
- enum/status ontology conflict
- cross-field conditional correctness
- recursive/self-reference 여부
- canonical hash field circularity 검토

### Negative fixture family
#### Status v2
- PASS + execution NOT_RUN
- PASS + evidence NOT_BOUND
- FINAL_LOCKED + SELF_VALIDATION
- FINAL_LOCKED + qualification STALE
- FINAL_LOCKED + freshness REVOKED

#### Receipt Envelope v2
- independent audit + independent=false
- PASS + freshness=STALE
- authority profile digest 누락
- tenant/scope/policy epoch 누락
- unsigned receipt
- same receipt_id with mutated payload

#### Authority Profile v2
- revoked=true + revoked_at null
- qualification required + NOT_REQUIRED
- same principal in mutually exclusive roles
- public key path only, digest 없음
- unsigned registry epoch

#### Semantic Gate Receipt v2
- PASS + P0 count > 0
- PASS + semantic NOT_RUN > 0
- PASS + revocation STALE
- PASS + OTester self-validation
- PASS + OAudit qualification STALE
- target artifact != deployment artifact where deployment required

### Exit Criteria
- 모든 valid fixture schema PASS
- 모든 invalid fixture schema FAIL
- meta-validator seeded omission이 invalid fixture를 놓치지 않음
- 아직 Runtime/Final authority는 없음

## 4. Phase 1 — v1→v2 Semantic Gap Inventory
각 v1 contract에서 v2 필드가 source-observable인지 분류한다.

상태:
- `DIRECTLY_MAPPABLE`
- `DERIVABLE_WITH_PROOF`
- `REQUIRES_READBACK`
- `REQUIRES_REPERFORMANCE`
- `REQUIRES_HUMAN_OR_EXTERNAL_AUTHORITY`
- `UNRECOVERABLE_FROM_V1`

`UNRECOVERABLE_FROM_V1`은 default PASS 값으로 채우지 않는다.

### 필수 대상
- `status-vocabulary.v1.json`
- `evidence-receipt.v1.schema.json`
- `receipt-envelope.v1.schema.json`
- `local-agent-receipt.v1.schema.json`
- `oruda-independent-run-receipt.v1.schema.json`
- `oruda-blind-review-receipt.v1.schema.json`
- `oruda-authority-key-registry.v1.schema.json`
- `oruda-final-candidate-gate.v1.schema.json`
- `oruda-final-approval-receipt.v1.schema.json`
- `oruda-final-lock.v1.schema.json`

### 산출물
`V1V2SemanticGapMatrix`

## 5. Phase 2 — Adapter / Reconstructor
### Status Adapter
v1 verification state를 v2 multi-dimensional state로 변환하되 알려지지 않은 차원을 명시한다.
예:
- v1 PASS + no evidence proof → v2 `execution=EXECUTED?`를 추정하지 않고 `evidence=UNKNOWN`, publication NON_FINAL
- v1 NOT_RUN → execution NOT_RUN, decision NOT_RUN

### Receipt Reconstructor
v1 receipt를 직접 수정하지 않는다.
`V1 Receipt -> Readback/Reperformance -> V2 Envelope Reconstruction Receipt`

### Authority Reconstructor
role/key 이름만으로 독립성이나 권한을 복원하지 않는다.
- principal ownership proof
- key admin ownership
- registry epoch/signature
- target/tenant/purpose scope
가 없으면 `NOT_PROVEN`.

### Gate Reconstructor
Final Candidate를 v1 job IDs/count로 계산하지 않고 exact receipt digest set과 current epochs에서 재구성한다.

## 6. Phase 3 — Workflow Operation v2 편입
다음 operation을 canonical registry에 추가한 뒤 dispatcher coverage를 검증한다.
- `semantic.applicability.evaluate`
- `semantic.denominator.discover`
- `semantic.denominator.challenge`
- `semantic.denominator.lock`
- `semantic.reperformance.run`
- `semantic.authority.revalidate`
- `semantic.independence.assess`
- `semantic.freshness.invalidate`
- `semantic.freshness.reconstruct`
- `semantic.validator.requalify`
- `assurance.otester.accept`
- `assurance.oaudit.accept`
- `assurance.human-accept`
- `assurance.final-candidate.reconstruct`
- `git.push`
- `deployment.verify-installed`

Operation entry는 이름만 등록하지 않고 최소:
- version
- required principal/role
- permit/purpose
- effect class
- input contracts
- output contracts
- idempotency
- retry policy
- timeout
- stale/revocation behavior
를 가진다.

## 7. Phase 4 — Canonical Product Lineage v2
필수 추가 artifact/stage:
- `SEMANTIC_APPLICABILITY_SET`
- `DENOMINATOR_EPOCH`
- `SEMANTIC_ASSURANCE_EXECUTION_SET`
- `SEMANTIC_ASSURANCE_CLOSURE_RECEIPT`
- `FRESHNESS_BARRIER_RECEIPT`
- `INDEPENDENCE_PROFILE`
- `VALIDATOR_QUALIFICATION_SET`
- `HUMAN_ACCEPTANCE_RECEIPT`
- `FINAL_RECONSTRUCTION_RECEIPT`
- `DEPLOYMENT_RECEIPT`
- `VERIFIED_TO_DEPLOYED_RECEIPT`

모든 artifact는 exact parent digest를 갖는다. Stage consumes 집합과 artifact parent_bindings 집합의 semantic closure를 CrossContractInvariantEngine이 검사한다.

## 8. Phase 5 — Validation Case / Denominator Migration
고정 숫자를 canonical authority로 사용하지 않는다.

기존 count 기반 항목:
- Final Acceptance source count
- Validation Case minimum count
- Package count
- Document count
- Omission injection count
- Independent run count

v2에서는 `exact IDs + item digest + denominator epoch + population digest`로 전환한다.

### Negative fixture
- duplicate item으로 count 충족
- current denominator에서 critical item 삭제
- legacy item으로 current coverage 부풀림
- N/A를 denominator에서 제거
- package/document count와 실제 배열 불일치

## 9. Phase 6 — Independent Gate Qualification
OTester/OAudit는 role name이 아니라 다음 profile을 만족해야 한다.
- distinct principal
- credential/KMS admin separation
- implementation independence
- oracle independence
- discovery independence
- knowledge independence
- current qualification
- signed receipt
- current source/policy/oracle/validator epochs

Local Agent Receipt의 `OTESTER|OAUDIT`는 명시적으로 SELF_VALIDATION_NONFINAL lane에만 남긴다.

## 10. Phase 7 — Final Gate Shadow
v2 Gate를 즉시 authority로 전환하지 않는다.

### Shadow 비교
동일 target/run에 대해:
- legacy candidate decision
- v2 reconstruction decision
- disagreement reason
- missing v2 evidence
- stale/qualification differences
를 비교한다.

Legacy PASS / v2 HOLD가 발생하면 v2를 legacy PASS로 낮추지 않는다. 원인 Finding을 열고 해결한다.

### Exit Criteria
- disagreement root cause 모두 설명
- seeded bypass fixture 모두 v2에서 차단
- independent OTester/OAudit가 v2 reconstruction을 독립 재수행

## 11. Phase 8 — Active Selector 전환
활성화를 위해 별도 signed selector contract가 필요하다.

Selector 최소 필드:
- contract family
- active version
- effective_at
- superseded version
- migration receipt
- rollback pointer
- signer/authority
- selector digest/signature

Candidate 파일명을 검색해 자동 사용하지 않는다.

## 12. Phase 9 — Post-Activation Invalidation
활성화 후 다음 event는 qualification/current PASS를 stale 처리한다.
- schema mandatory field 변경
- status ontology change
- authority/key registry epoch change
- validator/oracle change
- denominator change
- workflow operation semantics change
- lineage parent set change
- Final reconstruction logic change

## 13. Finding Closure 규칙
P0 Finding은 다음을 모두 만족할 때만 `VERIFIED_CLOSED` 후보가 된다.
1. Requirement 반영
2. Review rule 반영
3. v2 Contract 또는 명시적 non-contract enforcement 반영
4. Runtime enforcement 구현
5. Negative fixture 존재
6. 실제 fixture execution
7. expected fail/hold 확인
8. regression clean
9. independent reperformance
10. qualification current

문서 반영만으로 CLOSED하지 않는다.

## 14. 실행 증적 요구
각 migration/qualification run은 최소:
- source commit/tree digest
- schema/validator digest
- fixture IDs+digests
- command/script digest
- environment/toolchain digest
- executor principal
- authority/qualification profile
- raw result digest
- attempt history
- decision
을 보존한다.

## 15. 현재 상태
현재 완료된 것은:
- P0 Finding 수직 책임 매핑 설계
- Status v2 candidate schema
- Receipt Envelope v2 candidate schema
- Authority Principal Profile v2 candidate schema
- Semantic Assurance Gate Receipt v2 candidate schema

아직 완료되지 않은 것은:
- schema fixture 실제 파일 생성/실행
- v1→v2 adapter 구현
- operation registry v2
- product lineage v2
- Final Acceptance/Validation Case denominator migration
- independent qualification
- active selector 전환

따라서 현재 상태는 `DESIGN_ONLY / CONTRACT_CANDIDATE_CREATED / EXECUTION_NOT_RUN / NON_FINAL`이다.
