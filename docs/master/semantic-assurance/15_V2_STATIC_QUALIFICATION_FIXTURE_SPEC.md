# ONSure Semantic Assurance v2 Static Qualification Fixture 명세

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
이 문서는 v2 Candidate Contract가 P0 Finding에서 요구한 invariant를 실제 JSON Schema 수준에서 표현하는지 확인하기 위한 **valid/invalid fixture set**을 정의한다.

Fixture 존재는 Runtime PASS를 의미하지 않는다. 본 단계의 목표는 `schema contract qualification`이며, 이후 실제 validator 실행 evidence가 별도로 필요하다.

## 2. 대상 Schema
- `contracts/assurance-status-vocabulary.candidate.v2.schema.json`
- `contracts/assurance-receipt-envelope.candidate.v2.schema.json`
- `contracts/authority-principal-profile.candidate.v2.schema.json`
- `contracts/semantic-assurance-gate-receipt.candidate.v2.schema.json`

## 3. 공통 Fixture 규칙
각 schema는 최소 1개 valid + 2개 invalid fixture를 가져야 한다.
Invalid fixture는 단순 type mismatch가 아니라 **P0 semantic invariant violation**을 재현해야 한다.

필수 metadata는 `contracts/semantic-assurance-v2-schema-instance-registry.candidate.v1.json`에서 관리한다.

## 4. Status v2 Fixture
### Valid
`assurance-status-v2.valid.json`
- decision PASS
- execution EXECUTED
- evidence EVIDENCE_BOUND
- freshness CURRENT
- publication SELF_VALIDATION_NONFINAL

### Invalid A
`assurance-status-v2.pass-not-run.invalid.json`
- PASS + execution NOT_RUN
- 기대: schema FAIL
- 대응 Finding: success label exceeds execution evidence

### Invalid B
`assurance-status-v2.final-stale.invalid.json`
- FINAL_LOCKED + freshness STALE
- 기대: schema FAIL
- 대응 Finding: finality without freshness/revocation edge

## 5. Receipt Envelope v2 Fixture
### Valid
`assurance-receipt-envelope-v2.valid.json`
- SELF_VALIDATION_NONFINAL
- independent=false
- qualification PARTIAL
- freshness CURRENT
- signed/canonicalized envelope

### Invalid A
`assurance-receipt-envelope-v2.independent-false.invalid.json`
- assurance_class=INDEPENDENT_AUDIT
- independent=false
- 기대: schema FAIL

### Invalid B
`assurance-receipt-envelope-v2.pass-stale.invalid.json`
- decision PASS
- freshness STALE
- 기대: schema FAIL

## 6. Authority Principal v2 Fixture
### Valid
- signer principal, scoped operation, signed registry epoch, non-revoked, qualification current

### Invalid A
`authority-principal-v2.revoked-without-time.invalid.json`
- revoked=true
- revoked_at/revocation_reason 미충족
- 기대: schema FAIL

### Invalid B
`authority-principal-v2.qualification-required-not-required.invalid.json`
- qualification.required=true
- state=NOT_REQUIRED
- 기대: schema FAIL

## 7. Semantic Gate v2 Fixture
### Valid
- decision PASS
- P0/P1 blocker 0
- semantic blocked/hold/not_run 0
- revocation CURRENT
- OTester/OAudit both INDEPENDENT+QUALIFIED
- final_lock_allowed=false

### Invalid A
`semantic-gate-v2.pass-with-open-p0.invalid.json`
- PASS + p0_count=1
- 기대: schema FAIL

### Invalid B
`semantic-gate-v2.pass-with-self-otester.invalid.json`
- PASS + OTester independence SELF_VALIDATION
- 기대: schema FAIL

## 8. 추가 Meta-Mutation
Schema validator 자체의 false-pass를 잡기 위해 다음 mutation을 별도 수행한다.
- Status PASS conditional 제거
- Final freshness conditional 제거
- Receipt independent=true conditional 제거
- Authority revoked conditional 제거
- Gate P0 zero conditional 제거
- Gate OTester/OAudit qualification conditional 제거

위 mutant 중 하나라도 invalid fixture를 통과시키면 Meta-Validator Qualification은 HOLD다.

## 9. Exit Criteria
Static qualification 완료 후보 조건:
1. 4 schema meta-validation PASS
2. 모든 valid fixture PASS
3. 모든 invalid fixture FAIL
4. seeded schema mutation을 invalid fixture가 탐지
5. schema/fixture digest 보존
6. validator implementation digest 보존
7. 결과는 `STATIC_CONTRACT_QUALIFIED_NONFINAL`까지만 허용

## 10. 비최종 경계
이 Fixture Pack은 Runtime behavior, independent OTester/OAudit, Final Candidate, FinalLock을 검증하지 않는다. 실제 Runtime enforcement가 없으면 P0 Finding은 CLOSED가 아니다.
