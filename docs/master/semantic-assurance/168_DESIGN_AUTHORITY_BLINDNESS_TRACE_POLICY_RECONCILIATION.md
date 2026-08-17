# 168 Design Authority, Blindness, Trace and Policy Reconciliation

Document-ID: `ONSURE-SA-RECONCILIATION-0168`
Status: `DESIGN_RECONCILIATION_EXECUTED / RUNTIME_CLOSURE_HOLD / NON_FINAL`

## 1. 범위
설계서 재검토에서 확인된 6개 작업을 하나의 reconciliation으로 수행한다.
1. Authority 정본 충돌 정정
2. immutable Document-ID / relation registry
3. Blind Discovery Saturation 오염 제거
4. DD-001~040 granular vertical trace
5. Open policy/authority의 safe-floor 분리
6. EPOCH 0003→Design QA→Saturation→Lock→CLEAN 실행 체인 재게이트

## 2. Authority 정정 — 완료
`docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md`를 현재 권위로 정정했다.
- `docs/05 + docs/41~44` = CURRENT FINAL-TARGET PRODUCT AUTHORITY
- `docs/40` = REFERENCE_ONLY
- `docs/master/01~07 + 08A` = current development/design realization authority
- `docs/master/08_REVIEW_CHECKLIST_OPEN_DECISIONS.md` = TRACKING_ONLY / NON_NORMATIVE
- `126/128 scope closure` = historical pre-final-target evidence
- current Product Design Scope = `DISCOVERY_REOPENED`

## 3. Document Identity — 완료
`contracts/design-document-authority-registry.v1.json`을 제정했다. 숫자 prefix는 authority identity가 아니며 immutable `document_id`와 full path가 authority/trace key다. Duplicate prefix는 허용하지만 short-number-only reference는 금지한다. Validator는 `scripts/validate-design-document-authority.py`다.

## 4. Blind Saturation Decontamination — 프로토콜 완료 / 독립 실행 미완료
기존 intake의 `minimum_head` 및 Master conclusion leakage를 제거했다.
- exact commit/tree SHA freeze
- exact authority population digest
- sanitized bundle only
- repository browsing during blind wave 금지
- prior DD/scope-closure conclusion leakage pattern 발견 시 freeze 실패
- A/B 상호 결론 격리
- reviewer/process/model/common-control lineage 필수

Freeze generator: `scripts/freeze-independent-design-discovery-baseline.py`
Saturation validator: `scripts/validate-design-discovery-saturation.py`

독립 Wave A/B 실제 결과는 아직 없으므로 `GLOBAL_DISCOVERY_SATURATION_PROVEN=false`다.

## 5. DD-001~040 Granular Trace — design layer 완료 / machine layer OPEN
`contracts/dd-001-040-granular-vertical-trace.candidate.v1.json`에 40개를 전수 materialize했다.

40/40 연결: FR-FIN parent, canonical design object, state/invalidation, authority/SoD, UI claim/disclosure, Evidence/Receipt, negative/recovery fixture, independent oracle.

명시적 OPEN: Operation/API/Event 40, Schema/Contract 40, Executable Test 40, Runtime Evidence 40 NOT_RUN. Machine-layer gap instance는 현재 120이며 `40/40 mapped`를 coverage complete로 승격하지 않는다. Strict validator는 `scripts/validate-dd-granular-vertical-trace.py`다.

## 6. Policy / Authority — safe floor 완료 / human decision OPEN
`contracts/post-final-target-dd-policy-authority-floor.candidate.v1.json`에서 DD-001~040을 FIXED_INVARIANT / TENANT_CONFIGURABLE_WITH_FLOOR / INDUSTRY_PROFILE_WITH_FLOOR / HUMAN_AUTHORITY_REQUIRED로 분리했다. 사람이 결정해야 하는 값이 없어도 안전 floor는 즉시 적용되며 코드가 임의의 약한 기본값을 만들 수 없다.

Human decision receipt schema: `contracts/human-design-authority-decision.candidate.v1.schema.json`.
Completeness gate: `scripts/validate-human-design-authority-decisions.py`.

기존 delta source review seed의 `HUMAN_REVIEWED` 오해도 제거했다. `REVIEWED`는 source-role classification 검토이며 human design-authority approval은 별도 provenance/receipt로만 인정한다.

## 7. EPOCH 0003 / Design QA / Lock / CLEAN — 실행 경로 재게이트 완료
`contracts/product-design-requirement-epoch-0003.preseal.github.candidate.v1.json`을 V2 경계로 갱신했다.

`bash scripts/run-product-design-closure-post-delta.sh`는 document authority → uncontaminated saturation → raw SHA authority → deterministic EPOCH 0003 A/B → forward/reverse trace → requirement/design coverage → strict DD granular trace → human authority completeness → Global Lock preflight → historical live epoch restore → CLEAN x2 → blocker-aware receipt 순으로 수행한다.

CLEAN이 우연히 통과해도 granular trace/human authority/lock blocker를 override하지 못한다.

## 8. 실제 미실행 경계
현재 GitHub-direct 환경에서는 checkout execution node / PR-triggered Actions run이 없으므로 frozen baseline actual run, independent saturation A/B, raw SHA materialization, EPOCH 0003 A/B, applicability/trace/orphan rerun, DD machine operation/schema/test materialization, required human decisions, Design Lock preflight, CLEAN x2는 실제 PASS 증거가 없다.

## 9. 현재 판정
완료: `AUTHORITY_RECONCILIATION / DOCUMENT_ID_GOVERNANCE / BLIND_PROTOCOL_DECONTAMINATION / DD_DESIGN_GRANULARIZATION / POLICY_SAFE_FLOOR_BINDING / CLOSURE_CHAIN_REGATING`

미완료: `MACHINE_LAYER_MATERIALIZATION / HUMAN_AUTHORITY_DECISIONS / INDEPENDENT_SATURATION_EVIDENCE / RUNTIME_QA_LOCK_CLEAN`

Highest claim:
`DESIGN_AUTHORITY_RECONCILED / DD_001_040_DESIGN_TRACE_GRANULARIZED / BLIND_PROTOCOL_DECONTAMINATED / EPOCH_0003_CLOSURE_CHAIN_READY_BUT_BLOCKED / DESIGN_QA_HOLD / NON_FINAL`
