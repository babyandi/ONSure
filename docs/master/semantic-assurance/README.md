# ONSure Semantic Assurance — Current Authority Index

Status: `CURRENT_NAVIGATION_INDEX / NON_NORMATIVE / NON_FINAL`

이 README는 navigation index이며 Requirement/Design authority 자체가 아니다. 권위 선택은 `docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md`와 `contracts/design-document-authority-registry.v1.json`을 따른다.

## 1. 식별 규칙
- 파일의 숫자 prefix는 정렬용이다.
- `21`, `126`, `127`, `138`, `151`, `160` 등 중복 prefix가 존재하므로 숫자만으로 문서를 참조하지 않는다.
- supersession/trace/authority는 immutable `document_id` 또는 full repository path를 사용한다.

## 2. 현재 최상위 Authority
- `ONSURE-DESIGN-AUTHORITY-0001` → `docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md`
- Final-target Product Authority → `docs/05`, `docs/41~44`
- `docs/40` → REFERENCE_ONLY
- Development/Product Design Realization → `docs/master/00~07`, `08A`
- `docs/master/08_REVIEW_CHECKLIST_OPEN_DECISIONS.md` → TRACKING_ONLY / NON_NORMATIVE
- Semantic-assurance requirement authority → Requirement Authority Manifest에서 `NORMATIVE_CURRENT|NORMATIVE_REFINEMENT`로 승인된 문서만 해당

## 3. Post-final-target Product Design 현재 계보
- `160_FINAL_TARGET_PRODUCT_AUTHORITY_RECONCILIATION.md` — final-target/master dual-current authority reconciliation
- `162_FINAL_TARGET_DELTA_DESIGN_DISCOVERY_REOPENING.md` — DD-001~024 discovery
- `163_FINAL_TARGET_DELTA_MISSING_DESIGN_CLOSURE.md` — DD-001~024 companion design
- `165_BLIND_DESIGN_DISCOVERY_WAVES_2_3.md` — DD-025~040 discovery
- `166_WAVES_2_3_MISSING_DESIGN_CLOSURE.md` — DD-025~040 companion design
- `167_POST_DELTA_AUTHORITY_EPOCH3_AND_CLOSURE_EXECUTION_PREPARATION.md` — execution/status boundary, non-requirement-originating
- `168_DESIGN_AUTHORITY_BLINDNESS_TRACE_POLICY_RECONCILIATION.md` — current reconciliation status when present

`126/128 ... PRODUCT_DESIGN_SCOPE_CLOSURE`는 pre-final-target historical evidence다. 현재 Product Design Scope는 `DISCOVERY_REOPENED`이며 `GLOBAL_DISCOVERY_EXHAUSTED=false`다.

## 4. Core Design Families
상세 설계는 기존 문서를 보존한다.
- Core/Review/Migration: 00~20
- Independent Assurance/Deployment/Currentness/Composition/Evidence/Certificate/Offline/Scale/AI: 21~41
- Policy/Persistence/API/Security/Privacy/Observability/Data/Threat/Compatibility/DR/External Trust: 42~56
- Machine Contract/Authority/Serialization/Recovery/Trace/Industry/Tier/Claim: 57~80
- Implementation handoff/Schema/Persistence/Policy/API/Inventory/Requirement Universe/Trace: 81~101
- Design QA/Lock/Global denominator execution history: 102~145
- Learning Validation/Meta-Assurance refinement: 146~161
- Post-final-target Design Discovery and reconciliation: 162+

문서번호 범위는 편의상 탐색용일 뿐 authority ordering을 의미하지 않는다.

## 5. Current Machine-readable Governance
- `contracts/design-document-authority-registry.v1.json`
- `contracts/requirement-authority-source-allowlist.v1.json`
- `contracts/requirement-authority-source-allowlist.delta-final-target.v1.json`
- `contracts/post-final-target-dd-001-040-authority-admission.candidate.v1.json`
- `contracts/post-final-target-dd-to-fr-fin-relation.candidate.v1.json`
- `contracts/dd-001-040-granular-vertical-trace.candidate.v1.json`
- `contracts/post-final-target-dd-policy-authority-floor.candidate.v1.json`
- `contracts/independent-design-discovery-wave-intake.candidate.v1.json`
- `contracts/product-design-requirement-epoch-0003.preseal.github.candidate.v1.json`

## 6. Blind Discovery Integrity
독립 saturation reviewer는 repository 전체를 탐색하지 않는다. `scripts/freeze-independent-design-discovery-baseline.py`가 exact Git tree SHA에서 생성한 sanitized bundle만 사용한다.

다음이 reviewer input에 누출되면 wave는 무효다.
- `DD-xxx` prior accepted conclusions
- `GLOBAL_DISCOVERY_EXHAUSTED` 결론
- 과거/현재 Product Design scope-closure 결론
- Wave A가 Wave B 결론을 보거나 반대의 경우

A/B는 동일 exact tree SHA와 authority population digest를 사용해야 하며 reviewer/process/model/common-control lineage를 기록한다.

## 7. DD-001~040 현재 상세도
- Parent FR-FIN relation: 40/40
- Companion design owner/object/state/authority/UI/evidence/fixture/oracle: 40/40
- Operation/API/Event materialization: OPEN
- Schema/Contract materialization: OPEN
- Executable Test materialization: OPEN
- Runtime Evidence: NOT_RUN

따라서 `40/40 mapped`를 Product Design coverage 100% 또는 Design Lock으로 해석하지 않는다.

## 8. Policy/Open Decision
안전 불변식과 configurable/human decision을 분리한다.
- safe-floor authority: `contracts/post-final-target-dd-policy-authority-floor.candidate.v1.json`
- human decision receipt schema: `contracts/human-design-authority-decision.candidate.v1.schema.json`
- 값이 미확정이면 코드가 약한 기본값을 만들지 않고 safe floor + `HOLD/UNKNOWN/REASSESSMENT_REQUIRED`를 사용한다.

## 9. Closure Execution
독립 saturation과 필요한 human decision/evidence가 준비된 뒤:

```bash
bash scripts/run-product-design-closure-post-delta.sh
```

runner는 authority registry → uncontaminated saturation → raw SHA authority → EPOCH 0003 deterministic A/B → forward/reverse trace → requirement/design coverage → granular DD trace → human authority completeness → Lock preflight → CLEAN x2 → fail-closed receipt 순으로 수행한다.

실행하지 않은 단계는 PASS가 아니다.

## 10. 현재 최고 표현
`DESIGN_AUTHORITY_RECONCILED_FOR_POST_FINAL_TARGET / DD_001_040_DESIGN_GRANULARIZED / BLIND_SATURATION_PROTOCOL_DECONTAMINATED / POLICY_SAFE_FLOORS_BOUND / MACHINE_LAYERS_AND_HUMAN_DECISIONS_OPEN / DESIGN_QA_HOLD / NON_FINAL`
