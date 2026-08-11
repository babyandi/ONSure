# ONSure Semantic Assurance Review 상세설계 확장

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent authority: `../03_OREVIEW_CODE_REVIEW_SPECIFICATION.md`

## 1. 목적
이 문서는 OReview의 기존 Requirement/Architecture/Policy/Code/AI/Security/Test Review를 대체하지 않는다. 기존 Review 영역에서 놓치기 쉬운 **의미적 권위·실행 진실·권리·분산 effect·observer leakage·업무 불변식**을 독립 Finding으로 검출하기 위한 Review 확장이다.

## 2. 신규 Review Domain
기존 Domain에 다음 semantic sub-domain을 추가한다. 구현 시 기존 `review_domain` enum/registry와의 충돌 여부를 먼저 확인하고, 계약이 없으면 `DESIGN_ONLY`로 유지한다.

- `EVIDENCE_TRUTH`
- `COVERAGE_DENOMINATOR`
- `OBLIGATION_CLOSURE`
- `AUTHORITY_LIFECYCLE`
- `STATE_AUTHORITY`
- `RIGHTS_REMEDY`
- `DISTRIBUTED_EFFECT`
- `PRINCIPAL_POLICY_SOD`
- `DISCLOSURE_OBSERVER`
- `CROSS_MODEL_TRACE`
- `BUSINESS_SEMANTICS`
- `AI_USE_CASE_ASSURANCE`
- `VALIDATOR_QUALIFICATION`

## 3. Evidence Truth Review
### 검토규칙
- upstream PASS/count/digest를 실제 source read-back 없이 소비하는지 확인
- expected digest 문자열끼리만 비교하고 source bytes/object를 읽지 않는지 확인
- test 실행 여부를 fixture/test-plan 존재로 대체하는지 확인
- report/dashboard narrative가 더 최신 하위 receipt와 불일치하는지 확인
- current target과 historical evidence revision이 다른지 확인

### Finding 유형
- `DECLARED_RESULT_ONLY`
- `DECLARED_DIGEST_ONLY`
- `SUBJECT_REVISION_NOT_READBACK`
- `REQUIRED_ORACLE_NOT_REPERFORMED`
- `EXECUTION_IDENTITY_GAP`
- `NARRATIVE_EVIDENCE_DRIFT`
- `EVIDENCE_CONTRADICTION`

### Severity 원칙
- Final/High-risk claim이 `DECLARED_RESULT_ONLY`에 의존: CRITICAL/HIGH 후보
- nonfinal 보조지표에만 사용: MEDIUM/LOW 후보

## 4. Denominator & Coverage Review
### 검토규칙
- 기존 FR/API/Table/Test count를 immutable authority처럼 취급하는지
- 새 actor/state/effect/failure/right가 발견됐는데 denominator를 유지하기 위해 기존 항목에 억지로 병합하는지
- happy-path 기준으로 negative-space를 N/A 처리하는지
- excluded component를 denominator에서 제거해 coverage를 부풀리는지
- duplicate semantic function/requirement가 count를 부풀리는지

### Finding 유형
- `DENOMINATOR_FALSE_AUTHORITY`
- `DENOMINATOR_CHANGE_SUPPRESSED`
- `NEGATIVE_SPACE_COVERAGE_GAP`
- `SILENT_SCOPE_SHRINK`
- `DUPLICATE_DENOMINATOR_INFLATION`
- `MATERIAL_ORPHAN_NOT_COUNTED`

## 5. Obligation Closure Review
### 검토규칙
- Function existence가 invariant/evidence/test 존재를 대신하는지
- mandatory `ALL_OF` member 중 일부가 GAP인데 전체 requirement PASS인지
- `IDENTIFIED`를 `SATISFIED`로 오인하는지
- downstream obligation이 owner/target stage 없이 “later”로만 남는지
- `ANY_OF`를 mandatory safety evidence 우회에 사용하는지

### Finding 유형
- `CONJUNCTIVE_OBLIGATION_MEMBER_GAP`
- `IDENTIFIED_AS_SATISFIED_FALSE_PROMOTION`
- `UNROUTED_OBLIGATION`
- `ANY_OF_SAFETY_BYPASS`
- `EXACTLY_ONE_OF_AUTHORITY_CONFLICT`

## 6. Authority Lifecycle Review
### 검토규칙
- first owner/admin authority의 bootstrap source가 존재하는지
- 자기 자신에게 무제한 권한을 부여하는 bootstrap escalation이 가능한지
- delegation이 grantor보다 넓은지, cycle/redelegation depth가 무제한인지
- last owner exit 시 zero-owner/orphan이 가능한지
- decision 후 effect 전 revoke/policy change에도 stale receipt로 실행되는지
- current role로 historical authorization을 재구성하는지

### Finding 유형
- `FIRST_AUTHORITY_MATERIALIZATION_UNDEFINED`
- `BOOTSTRAP_ESCALATION`
- `UNBOUNDED_REDELEGATION`
- `DELEGATION_CYCLE`
- `ZERO_OWNER_ORPHAN_STATE`
- `STALE_AUTHORIZATION_AT_EFFECT`
- `HISTORICAL_AUTHORITY_REWRITTEN_FROM_CURRENT_STATE`

## 7. Canonical State Authority Review
### 검토규칙
- 동일 canonical state를 둘 이상의 component/script/callback이 write하는지
- provider callback이 내부 command/validation 없이 final state를 쓰는지
- ops/debug/migration이 business invariant를 우회하는지
- cache/read-model이 authoritative state 대신 command eligibility를 결정하는지
- AI/tool이 proposal을 넘어 canonical effect를 직접 수행하는지

### Finding 유형
- `DUAL_CANONICAL_WRITER`
- `PROVIDER_CALLBACK_DIRECT_FINALITY`
- `OPS_AUTHORITY_BYPASS`
- `MIGRATION_INVARIANT_BYPASS`
- `PROJECTION_AS_AUTHORITY`
- `AI_DIRECT_EFFECT_AUTHORITY`

## 8. Rights / Remedy Review
### 검토규칙
- appeal/revoke/close/restore가 prose에만 있고 typed action path가 없는지
- UI/API action은 있으나 어떤 right/holder/authority가 근거인지 없는지
- operator remedy가 direct DB/manual mutation에 의존하는지
- right를 행사했더니 새 remedy가 생기는데 exercising path가 없는지
- restore 후 권리·resource binding이 silent regression 되는지

### Finding 유형
- `DECLARED_RIGHT_NOT_EXECUTABLE`
- `ACTION_WITHOUT_RIGHT_AUTHORITY`
- `HIDDEN_MANUAL_REMEDY`
- `TRANSITIVE_RIGHT_DEAD_END`
- `RIGHTS_RESTORE_REGRESSION`

## 9. Distributed Effect Review
### 검토규칙
- source dispatch receipt를 downstream success로 취급하는지
- batch summary가 item-level truth를 대체하는지
- partial success retry가 이미 성공한 item에 duplicate effect를 만드는지
- externally ambiguous outcome에서 read-back 없이 retry하는지
- irreversible/compensatable effect를 DB rollback으로 해결했다고 주장하는지
- terminal delete/close 시 dependency graph가 아닌 static blocker list만 사용하는지

### Finding 유형
- `HANDOFF_SUCCESS_FALSE_INFERENCE`
- `BATCH_SUMMARY_HIDES_ITEM_FAILURE`
- `DUPLICATE_EFFECT_ON_RETRY`
- `EXTERNAL_EFFECT_AMBIGUITY_UNRESOLVED`
- `COMPENSATION_COLLAPSED_TO_ROLLBACK`
- `TERMINAL_DEPENDENCY_ORPHAN`

## 10. Principal / Policy / SoD Review
### 검토규칙
- email/phone/account locator를 durable subject identity로 동일시하는지
- organization namespace 생성만으로 verified representation을 부여하는지
- alias/membership/delegation 변경을 policy evaluator cache가 반영하지 않는지
- allow/deny가 동시에 match될 때 precedence가 없는지
- quorum이 서로 다른 principal이 아니라 한 사람의 복수 role로 충족되는지
- emergency override가 expiry/evidence/post-review 없이 우회경로가 되는지

### Finding 유형
- `LOCATOR_EQUALS_SUBJECT_IDENTITY`
- `UNVERIFIED_REPRESENTATION_PROMOTED`
- `POLICY_PRECEDENCE_UNDEFINED`
- `STALE_PRINCIPAL_EXPANSION`
- `QUORUM_PRINCIPAL_COLLAPSE`
- `EMERGENCY_OVERRIDE_UNGOVERNED`

## 11. Disclosure / Observer Review
### 검토규칙
- 민감 field가 body에 없어도 status/length/header/retry/latency 차이로 private state를 추론할 수 있는지
- API에서는 숨겼지만 email/push/webhook/support/localization/accessibility에서 새는지
- block/report/legal-hold/security decision reason이 공격 oracle이 되는지
- appeal에 필요한 설명과 과도한 disclosure 사이 경계가 없는지

### Finding 유형
- `PRIVATE_STATE_EXISTENCE_ORACLE`
- `CROSS_CHANNEL_DISCLOSURE_DRIFT`
- `ANTI_RETALIATION_DISCLOSURE_FAILURE`
- `APPEAL_UNDER_DISCLOSURE`
- `APPEAL_OVER_DISCLOSURE`

## 12. Cross-Model Semantic Trace Review
### 검토규칙
- count가 같다는 이유로 Function↔Component 등 1:1 mapping을 추정하는지
- n:1 merge에서 책임이 사라지는지
- 1:n split에서 canonical authority가 복제되는지
- package/deployment 구조를 business component 구조와 동일시하는지
- schema에 mandatory field가 있으나 validator가 읽지 않는지

### Finding 유형
- `PHANTOM_EQUIVALENCE`
- `RESPONSIBILITY_LOSS_ON_MAPPING`
- `AUTHORITY_DUPLICATION_ON_SPLIT`
- `MODEL_IDENTITY_CONFLATION`
- `UNENFORCED_CONTRACT_FIELD`
- `SCHEMA_VALIDATOR_DRIFT`

## 13. Business Semantic Review
### 검토규칙
- currency/unit/precision/rounding이 불명확한지
- gross/net/fee/tax 관계가 시스템별로 다른지
- FX source/time/revision이 없는지
- adjustment/refund가 original을 overwrite하는지
- component sum과 authoritative total이 reconcile되지 않는지
- quota/entitlement/score/SLA의 zero/negative/overflow가 정의되지 않는지

### Finding 유형
- `UNIT_OR_CURRENCY_AMBIGUITY`
- `ROUNDING_SEMANTICS_DRIFT`
- `FX_PROVENANCE_GAP`
- `HISTORICAL_VALUE_OVERWRITE`
- `CONSERVATION_INVARIANT_FAILURE`

## 14. AI Use-Case Assurance Review
### 검토규칙
- AI applicability와 실제 AI-UC catalog가 일치하는지
- AI-UC에 non-AI baseline/fallback/effect ceiling이 없는지
- AI-UC별 TEVV 없이 공통 family 몇 개로 전체를 PASS하는지
- Tool availability 증가로 effect class가 커졌는데 old TEVV를 재사용하는지
- human review가 source evidence 접근 없이 one-click approve인지

### Finding 유형
- `AI_UC_AUTHORITY_CLOSURE_GAP`
- `AI_NO_BASELINE_OR_FALLBACK`
- `AI_EFFECT_CEILING_UNDEFINED`
- `AI_UC_TEVV_COVERAGE_GAP`
- `AUTOMATION_BIAS_RUBBER_STAMP`

## 15. Validator Qualification Review
### 검토규칙
- detector/oracle/rule 변경 후 old qualification을 자동 상속하는지
- hidden/private set을 developer/learner가 볼 수 있는지
- shadow/nonblind run을 qualification substitute로 쓰는지
- critical miss가 있는데 평균 score로 승격하는지
- method transport가 manual summary로 변형됐는지

### Finding 유형
- `QUALIFICATION_STALE_AFTER_METHOD_CHANGE`
- `HIDDEN_SET_CONTAMINATION`
- `SHADOW_AS_QUALIFICATION_SUBSTITUTE`
- `CRITICAL_MISS_AVERAGED_AWAY`
- `METHOD_TRANSPORT_FIDELITY_GAP`

## 16. Decision 규칙
- Critical authority/evidence/state violation이 하나라도 unresolved면 해당 domain PASS 금지
- Required semantic domain이 NOT_RUN이면 overall Full Assurance 금지
- Finding 수가 0이어도 denominator/observability/fixture execution이 불충분하면 PASS 금지
- Accepted Risk는 Finding 해결이 아니며 semantic hard gate를 우회하지 못함
- Review 결과는 자체 선언이므로 OVerification/OTester/OAudit의 independent execution evidence를 대체하지 않음
