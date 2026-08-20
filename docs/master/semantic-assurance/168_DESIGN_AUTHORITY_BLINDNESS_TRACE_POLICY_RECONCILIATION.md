# 168 Design Authority, Blindness, Trace and Policy Reconciliation

Document-ID: `ONSURE-SA-RECONCILIATION-0168`
Status: `DESIGN_RECONCILIATION_EXTENDED / SELF_INVALIDATING_EVIDENCE_LOOPS_REMOVED / ACTIONS_NOT_AUTHORITY / EXECUTION_AND_INDEPENDENT_CLOSURE_HOLD / NON_FINAL`

## 1. 범위
설계서 재검토에서 확인된 post-final-target Product Design reconciliation을 현재 기준으로 유지한다.
1. Authority 정본 충돌 정정
2. immutable Document-ID / relation registry
3. Blind Discovery Saturation 오염 제거
4. DD-001~040 granular vertical trace
5. Open policy/authority safe-floor 분리
6. EPOCH 0003→Design QA→Pre-CLEAN→Independent CLEAN→main revalidation→Design Lock 재게이트
7. candidate Requirement Universe 직접 검증
8. DD machine contract / schema / fixture / evaluator qualification materialization
9. execution subject와 independent-qualified subject lineage 분리
10. evidence receipt를 git subject에 커밋해 스스로 subject를 변경하는 자기무효화 경로 제거

## 2. Authority 정정 — 설계계약 완료 / Final 아님
`docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md` 기준:
- `docs/05 + docs/41~44` = CURRENT FINAL-TARGET PRODUCT AUTHORITY
- `docs/40` = REFERENCE_ONLY
- `docs/master/01~07 + 08A` = current development/design realization authority
- `docs/master/08_REVIEW_CHECKLIST_OPEN_DECISIONS.md` = TRACKING_ONLY / NON_NORMATIVE
- `126/128 scope closure` = historical pre-final-target evidence
- current Product Design Scope = `DISCOVERY_REOPENED`

Requirement-authority population은 전체 미분류 파일 수와 denominator-eligible authority review 상태를 분리한다. `eligible_unreviewed_count` 또는 `eligible_disputed_count`만 denominator hard blocker이며, ineligible/unreviewed 문서는 disclosure로 남되 Requirement를 originate하지 않는다.

## 3. Document Identity — 완료
`contracts/design-document-authority-registry.v1.json`을 사용한다. 숫자 prefix는 authority identity가 아니며 immutable `document_id`와 full path가 authority/trace key다. Duplicate prefix는 허용하지만 short-number-only reference는 금지한다.

Validator: `scripts/validate-design-document-authority.py`.

## 4. Blind Saturation Decontamination — 프로토콜 완료 / 독립 실행 미완료
- exact commit/tree SHA freeze
- exact authority population digest
- sanitized bundle only
- prior DD/scope-closure conclusion leakage 발견 시 freeze 실패
- A/B 상호 결론 격리
- reviewer principal / process lineage 분리
- `same_authoring_context=false`
- `common_control_resolved=true`
- canonical wave receipt digest 검증
- 같은 authoring context의 자기검증은 independent wave로 인정하지 않음

Freeze generator: `scripts/freeze-independent-design-discovery-baseline.py`
Wave schema: `contracts/independent-design-discovery-wave-result.candidate.v1.schema.json`
Saturation validator: `scripts/validate-design-discovery-saturation.py`

독립 Wave A/B actual evidence가 없으므로 `GLOBAL_DISCOVERY_SATURATION_PROVEN=false`다.

## 5. DD-001~040 Granular Trace — design + machine/code materialized / execution OPEN
`contracts/dd-001-040-granular-vertical-trace.candidate.v1.json`에 40개를 전수 연결했다.

### 5.1 Design layer
40/40:
- FR-FIN parent
- canonical design object
- state/invalidation
- authority/SoD
- UI claim/disclosure
- Evidence/Receipt
- negative/recovery intent
- independent oracle intent

### 5.2 Machine definition layer
현재 tracked design/code artifact로 materialize된 denominator:
- DD operations: 40/40
- workflow operation registry inclusion
- generic request/result schema
- exact DD/schema binding
- negative/recovery fixture seed catalog
- semantic evaluator obligation catalog: 40/40
- qualification fixture denominator: `40 DD × 4 classes = 160 cases`
- static cross-check: `scripts/validate-dd-machine-definitions.py`

80개의 거의 동일한 schema 파일을 양산하지 않고 `generic schema + exact DD/operation binding`을 canonical model로 사용한다.

### 5.3 Concrete semantic evaluator code
`BuiltInDdSemanticEvaluators`는 DD-001~040 전체 rule definition을 materialize한다.

중요 원칙:
- caller가 전달한 precomputed `pass=true`를 oracle로 사용하지 않는다.
- digest-bound/current evidence에서 normalized facts를 resolve한다.
- DD별 predicate를 ONSure가 직접 계산한다.
- evidence missing/stale/integrity mismatch/conflict는 HOLD다.
- positive result ceiling은 `PASS_NONFINAL`이다.
- final claim 및 evaluator self-authorized external effect는 금지한다.

Runtime/qualification 구성요소:
- `DdAssuranceOperationRuntime`
- `DdAssuranceContractValidator`
- `DdSemanticEvaluator`
- `DdSemanticEvaluatorRegistry`
- `BuiltInDdSemanticEvaluators`
- `FileBackedDdEvidenceResolver`
- `DdQualifiedRuntimeFactory`
- `DdSemanticRuntimeEvidenceMain`
- `PostFinalTargetWorkflowDispatcher`
- `SemanticAssuranceV2DispatcherBridge`

Built-in evaluator code가 존재한다는 사실은 qualification을 의미하지 않는다. qualification receipt가 없으면 runtime은 `IMPLEMENTED_UNQUALIFIED / HOLD`다.

### 5.4 Qualification model — receipt-derived
Tracked `contracts/dd-semantic-evaluator-qualification-status.candidate.v1.json`은 code/materialization disclosure다. 실제 qualification authority가 아니다.

실제 qualification status는 다음에서 파생한다.
- frozen qualification bundle V2
- current compiled evaluator class SHA-256
- exact obligation registry SHA-256
- DD별 independent qualification receipt 40개
- positive/negative/recovery/adversarial 4종 planned fixture result
- reviewer/process lineage
- expiry/currentness

Validator는 `.onsure/dd-independent-qualification/validated-status.json`을 derived status로 materialize한다.

Qualification subject tree와 runtime execution tree는 서로 다른 lineage다. Qualification receipt를 저장하기 위해 git subject를 변경해서 qualification 자체를 무효화하는 방식은 금지한다.

### 5.5 Runtime evidence model
Target runtime evidence는 synthetic fixture와 분리한다.

필수:
- current execution commit/tree
- independently-qualified subject tree
- exact DD↔operation
- target identity
- execution principal/environment
- digest-bound current evidence
- qualification receipt digest
- evaluator identity/version
- canonical runtime receipt digest

40개 actual runtime receipt가 모두 `PASS_NONFINAL`이어야 target runtime gate를 닫을 수 있다.

### 5.6 현재 OPEN
현재 허용된 실행 방법으로 actual evidence가 없다.
- Java compile/JUnit current-subject execution
- route execution mechanics 40/40
- schema-validator execution mechanics 40/40
- qualification fixture mechanics 160/160
- independent evaluator qualification 40/40
- target semantic runtime evidence 40/40

따라서 code/materialization과 execution/qualification을 동일시하지 않는다.

## 6. Design Capability Coverage — denominator materialized / implementation NOT_RUN
`contracts/design-capability-coverage.candidate.v2.json`에 28개 mandatory Product Design capability를 전수 materialize했다.

모든 capability는 의도적으로 `DESIGN_ONLY / NOT_RUN`이다. structural validator 통과는 implementation/runtime PASS를 의미하지 않는다.

## 7. Candidate-native Requirement / Trace Architecture — 완료
현재:
- `scan-global-trace-closure.py --universe-dir <candidate>`
- `scan-reverse-orphan-product-design.py --universe-dir <candidate>`
- `validate-product-design-candidate-preflight.py`

를 사용한다.

Historical live EPOCH를 candidate 검증을 위해 교체하지 않는다.

## 8. Policy / Human Authority — safe floor 완료 / 18 decisions OPEN
Exact human decision denominator는 18개다.
- DD human-authority subjects 14개
- Learning Validation P1 contradiction policy 4개

추천 결정 packet은 준비돼 있으나 일반적인 `진행해/계속해`를 승인으로 간주하지 않는다.

HDA receipt는 git subject를 움직이지 않도록 기본적으로 외부 또는 `.onsure` evidence directory에 materialize한다.

Validator: `scripts/validate-human-design-authority-decisions.py`.

## 9. GitHub Actions 사용 금지 / 실행 권위
GitHub Actions는 ONSure의 execution, independent assurance, CLEAN, Design Lock 판정 권위로 사용하지 않는다.

현재 execution authority는 `LOCAL_OR_APPROVED_EXECUTION_NODE_NO_GITHUB_ACTIONS`다.

과거 Actions 기록은 historical/reference-only이며 다음 어느 것도 충족하지 않는다.
- current DD Java/JUnit
- evaluator independent qualification
- target runtime evidence
- Discovery Saturation
- Independent CLEAN
- Design Lock

## 10. Evidence custody / self-invalidation rule
Independent evidence를 git branch에 커밋해 reviewed/qualified/CLEAN subject SHA를 변경하는 순환을 금지한다.

External/local immutable evidence가 허용되는 범주:
- evaluator qualification receipts
- HDA decision receipts
- Independent CLEAN A/B receipts
- PR independent review receipt
- target evidence source package

Tracked code/contracts는 증거를 검증한다. 증거 자체를 tracked subject에 넣어 subject identity를 바꾸는 것을 요구하지 않는다.

## 11. Pre-CLEAN / Independent CLEAN
Independent CLEAN은 local reproducibility와 별도다.

순서:
1. 모든 non-CLEAN gate 실행
2. `ONSURE_INDEPENDENT_CLEAN_PRECLEAN_SUBJECT_V2` 봉인
3. exact requirement/authority/coverage/subject digest 고정
4. Independent CLEAN A
5. Independent CLEAN B
6. A/B independent lineage 및 exact subject digest 검증
7. full candidate preflight/closure 재실행

CLEAN A/B를 만들기 전에 CLEAN이 필요하다는 순환 dependency는 금지한다.

## 12. Feature pre-merge chain
`scripts/run-feature-design-lock-premerge.sh`를 사용한다.

- `PRE_CLEAN`: execution/qualification/runtime/Saturation/HDA/non-CLEAN QA 후 Pre-CLEAN subject까지
- 외부 Independent CLEAN A/B + PR independent review
- `FINALIZE_PREMERGE`: CLEAN/review 검증 후 `READY_FOR_MAIN_MERGE_NONFINAL` 요구

main merge는 이 readiness 이전에 금지한다.

## 13. Main revalidation / Design Lock
`scripts/run-main-design-lock-revalidation.sh`는 두 단계다.

### PRE_CLEAN
- main Java/JUnit V5 receipt 재생성
- feature에서 independently-qualified frozen bundle을 재생성하지 않고 staging
- 40 independent qualification receipts staging/revalidation
- current compiled evaluator artifact가 qualified artifact와 동일한지 검증
- immutable target evidence staging
- DD runtime 40개 main에서 재실행
- full non-CLEAN QA
- main Pre-CLEAN Subject 봉인
- STOP_FOR_INDEPENDENT_CLEAN_A_B

### FINALIZE_LOCK
- main subject용 새 Independent CLEAN A/B 검증
- full closure 재실행
- external immutable PR review receipt 및 reviewed feature-head ancestry 검증
- main-only `issue-design-lock.py`
- actual `ONSURE_DESIGN_LOCK_RECEIPT_V4`에서 `design_lock=true` 요구

Design Lock은 Final Lock, Production GO, Commercial GO와 동일하지 않다.

## 14. 현재 판정
### Materialized
- Authority reconciliation
- immutable Document-ID governance
- Blind protocol decontamination
- DD-001~040 design trace
- DD operation denominator 40/40
- request/result/schema binding
- concrete DD evaluator rules 40/40
- qualification fixture denominator 160/160
- qualification subject/runtime execution lineage separation
- receipt-derived qualification status model
- digest-bound target evidence model
- target runtime 40-DD execution entry/receipt materializer
- candidate-native forward/reverse scanners
- candidate-native preflight
- Pre-CLEAN Subject V2
- external/local evidence custody model
- two-phase feature pre-merge runner
- two-phase main Design Lock runner
- Design Lock issuer V4
- Actions-free closure chain

### Not established
- current-subject Java compile/JUnit execution
- qualification fixture actual execution 160/160
- DD independent evaluator qualification 40/40
- DD target runtime evidence 40/40
- Independent Discovery Saturation A/B
- Human Design Authority 18 decisions
- feature Pre-CLEAN actual execution
- feature Independent CLEAN A/B
- PR #54 independent review
- pre-merge readiness
- main merge
- main-SHA execution/revalidation
- main Independent CLEAN A/B
- Design Lock

Highest allowed claim:
`DESIGN_AUTHORITY_RECONCILED / DD_CONCRETE_RULE_EVALUATORS_40_OF_40_MATERIALIZED_UNVERIFIED / QUALIFICATION_AND_RUNTIME_LINEAGE_MATERIALIZED / SELF_INVALIDATING_EVIDENCE_LOOPS_REMOVED / EXECUTION_AND_INDEPENDENT_ASSURANCE_OPEN / DESIGN_QA_HOLD / NON_FINAL`
