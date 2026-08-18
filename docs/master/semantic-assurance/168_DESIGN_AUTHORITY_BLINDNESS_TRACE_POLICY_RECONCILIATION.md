# 168 Design Authority, Blindness, Trace and Policy Reconciliation

Document-ID: `ONSURE-SA-RECONCILIATION-0168`
Status: `DESIGN_RECONCILIATION_EXTENDED / ACTIONS_NOT_AUTHORITY / RUNTIME_AND_INDEPENDENT_CLOSURE_HOLD / NON_FINAL`

## 1. 범위
설계서 재검토에서 확인된 핵심 reconciliation을 현재 post-final-target Product Design 기준으로 유지한다.
1. Authority 정본 충돌 정정
2. immutable Document-ID / relation registry
3. Blind Discovery Saturation 오염 제거
4. DD-001~040 granular vertical trace
5. Open policy/authority의 safe-floor 분리
6. EPOCH 0003→Design QA→Saturation→Lock→CLEAN 실행 체인 재게이트
7. candidate Requirement Universe 직접 검증
8. DD machine contract / schema / fixture / evaluator qualification materialization

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
- reviewer/process/model/common-control lineage 필수
- 같은 authoring context의 자기검증은 independent wave로 인정하지 않음

Freeze generator: `scripts/freeze-independent-design-discovery-baseline.py`
Saturation validator: `scripts/validate-design-discovery-saturation.py`

독립 Wave A/B actual evidence가 없으므로 `GLOBAL_DISCOVERY_SATURATION_PROVEN=false`다.

## 5. DD-001~040 Granular Trace — design + machine definition materialized / execution OPEN
`contracts/dd-001-040-granular-vertical-trace.candidate.v1.json`에 40개를 전수 연결했다.

### 5.1 Design layer
40/40:
- FR-FIN parent
- canonical design object
- state/invalidation
- authority/SoD
- UI claim/disclosure
- Evidence/Receipt
- negative/recovery fixture intent
- independent oracle intent

### 5.2 Machine definition layer
다음은 현재 tracked design/code artifact로 materialize됐다.
- 40 DD operations: `contracts/dd-machine-operation-schema-fixture-registry.candidate.v1.json`
- workflow operation registry inclusion: `contracts/workflow-operation-registry.v1.json`
- generic request schema: `contracts/dd-assurance-request.candidate.v1.schema.json`
- generic result schema: `contracts/dd-assurance-result.candidate.v1.schema.json`
- exact DD/schema binding: `contracts/dd-machine-schema-binding.candidate.v1.json`
- 40 negative/recovery fixture oracle catalog: `contracts/dd-machine-fixture-catalog.candidate.v1.json`
- 40 semantic evaluator obligations: `contracts/dd-semantic-evaluator-registry.candidate.v1.json`
- evaluator qualification receipt schema: `contracts/dd-semantic-evaluator-qualification.candidate.v1.schema.json`
- static cross-check: `scripts/validate-dd-machine-definitions.py`

80개의 거의 동일한 schema 파일을 양산하지 않고 `generic schema + exact DD/operation binding`을 canonical model로 사용한다. schema identity는 registry row에 1:1 유지된다.

### 5.3 Runtime framework code
- `DdAssuranceOperationRuntime`
- `DdAssuranceContractValidator`
- `DdSemanticEvaluator`
- `DdSemanticEvaluatorRegistry`
- `PostFinalTargetWorkflowDispatcher`
- `SemanticAssuranceV2DispatcherBridge`
- `PostFinalTargetDdWorkflowTest` test definition

Default evaluator registry는 empty다. evaluator가 등록되어도 `qualificationCurrent=true`와 `independentQualification=true`를 모두 충족하지 않으면 실행되지 않고 HOLD다. Qualified evaluator도 positive evidence receipt 없이 PASS_NONFINAL을 낼 수 없고 evaluator qualification만으로 external effect를 자기승인할 수 없다.

### 5.4 아직 OPEN
현재 허용된 실행 방법으로 다음 actual evidence가 없다.
- Java compile/JUnit current-head execution
- 40 route execution evidence
- schema-validator execution evidence
- concrete DD semantic evaluator qualification 40/40
- semantic fixture/oracle execution 40/40
- semantic runtime evidence 40/40

따라서 machine definition materialization과 runtime qualification을 동일시하지 않는다.

## 6. Design Capability Coverage — denominator materialized / implementation NOT_RUN
`contracts/design-capability-coverage.candidate.v2.json`에 28개 mandatory Product Design capability를 전수 materialize했다.

모든 capability는 의도적으로 `DESIGN_ONLY / NOT_RUN`이다. 이 matrix가 structural validator를 만족하더라도 implementation/runtime PASS를 의미하지 않는다.

Validator는 반드시 명시 matrix로 실행한다.

```bash
python3 scripts/validate-design-coverage.py \
  --matrix contracts/design-capability-coverage.candidate.v2.json \
  --root . --self-test
```

## 7. Candidate-native Requirement / Trace Architecture — 완료
과거 scanner는 live `.onsure/requirement-universe`에 하드코딩되어 candidate를 검증하려면 EPOCH swap이 필요했다. 이 경로를 제거했다.

현재:
- `scan-global-trace-closure.py --universe-dir <candidate>`
- `scan-reverse-orphan-product-design.py --universe-dir <candidate>`
- `validate-product-design-candidate-preflight.py`

를 사용한다.

`run-product-design-closure-post-delta.sh`는 historical live EPOCH를 교체하지 않고 `epoch-0003-candidate`를 직접 검사한다. 기존 live-universe 중심 `validate-global-lock-preflight.py`는 candidate Lock authority로 사용하지 않는다.

## 8. Policy / Human Authority — safe floor 완료 / 18 decisions OPEN
`contracts/post-final-target-dd-policy-authority-floor.candidate.v1.json`에서 DD-001~040을:
- FIXED_INVARIANT
- TENANT_CONFIGURABLE_WITH_FLOOR
- INDUSTRY_PROFILE_WITH_FLOOR
- HUMAN_AUTHORITY_REQUIRED

로 분리했다.

Human decision receipt schema: `contracts/human-design-authority-decision.candidate.v1.schema.json`.
Completeness gate: `scripts/validate-human-design-authority-decisions.py`.

현재 exact human decision denominator는 18개다.
- DD human-authority subjects 14개
- Learning Validation P1 contradiction policy 4개

승인 receipt가 없으므로 모두 OPEN이다. 안전 floor는 승인 전에도 적용되며 임의의 약한 default로 PASS시키지 않는다.

## 9. GitHub Actions 사용 금지 / 실행 권위
GitHub Actions는 ONSure의 검증·승격·Release/Lock 판정 권위로 사용하지 않는다.

현재 branch에 추가됐던 ONSure Actions workflow는 제거했다. 과거 Actions 실행 기록은 historical/reference-only이며 다음 어느 것도 충족하지 않는다.
- DD runtime execution evidence
- independent saturation
- independent CLEAN
- Design Lock
- Final / Production GO

현재 execution authority는 `LOCAL_OR_AUTOPILOT_EXPLICIT_RUN_ONLY`다. 실제 checkout에서 명시적으로 실행해 생성한 receipt만 runtime/self-validation evidence 후보가 된다.

수동 실행 원칙은 `docs/assurance/manual-verification-without-actions.md`를 따른다.

## 10. Closure chain — candidate-native / NON_FINAL
`bash scripts/run-product-design-closure-post-delta.sh`는 다음 순서를 강제한다.

1. document authority
2. independent Discovery saturation evidence
3. exact design inventory / reconstructability
4. eligible Requirement authority raw SHA
5. deterministic EPOCH 0003 A/B
6. candidate-native forward/reverse trace
7. final-product/design/DD/human validators
8. local assurance twice — reproducibility only
9. independent CLEAN A/B — 별도 evidence
10. candidate-native preflight
11. blocker-aware receipt

Local assurance twice는 independent CLEAN으로 계산하지 않는다. CLEAN A/B는 별도 독립 주체/lineage receipt가 필요하다.

## 11. 현재 판정
### Materialized
- Authority reconciliation
- immutable Document-ID governance
- Blind protocol decontamination
- DD-001~040 design trace
- DD 40 operation denominator
- generic request/result contract
- exact DD/schema binding
- DD fixture oracle catalog 40
- DD semantic evaluator obligation catalog 40
- qualification-aware evaluator framework
- 28 capability design denominator
- candidate-native forward/reverse scanners
- candidate-native preflight
- Actions-free closure runner

### Not established
- current-head compile/JUnit execution by authorized method
- DD semantic evaluator qualification 40/40
- DD fixture/oracle execution 40/40
- DD runtime evidence 40/40
- Independent Discovery Saturation A/B
- Human Design Authority 18 decisions
- Independent CLEAN A/B
- independent PR review
- main merge and main-SHA revalidation
- Design Lock

Highest allowed claim:
`DESIGN_AUTHORITY_RECONCILED / DD_MACHINE_AND_EVALUATOR_FRAMEWORK_MATERIALIZED / CANDIDATE_NATIVE_CLOSURE_PATH_READY / EXECUTION_AND_INDEPENDENT_ASSURANCE_OPEN / DESIGN_QA_HOLD / NON_FINAL`
