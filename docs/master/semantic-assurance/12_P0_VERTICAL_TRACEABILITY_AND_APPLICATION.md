# ONSure P0 Finding 수직 Traceability 및 적용설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
본 문서는 `10_FINDING_LEDGER.md`의 P0 Finding을 단순 목록으로 보존하는 데서 끝내지 않고, 기존 ONSure Master 산출물의 책임구조인 **02 기능 → 03 Review → 04 Architecture/Data/API → 05 UI/UX → 06 Test/Operation → 07 AI/Agent → 08 Open Decision**으로 수직 전개한다.

Finding이 CLOSED 되려면 문서 한 곳에 설명이 추가되는 것만으로 부족하다. 최소 다음 계보를 충족해야 한다.

`Finding -> Requirement -> Review Rule -> Machine Contract -> Runtime Enforcement -> UX Projection -> Negative Fixture -> Execution Evidence -> Independent Verification -> Qualification`

## 2. 적용 원칙
1. P0 Finding은 반드시 하나 이상의 SA Capability와 하나 이상의 canonical gate에 연결한다.
2. 같은 root defect class라도 affected contract가 다르면 Finding은 별도 유지한다.
3. 02~08 중 해당 책임이 존재하지 않는 Finding은 `DESIGNED_CONTROL_OUTSIDE_CANONICAL_GATE_PATH`로 유지한다.
4. 문자열/boolean/count/label만으로 enforcement를 대체하지 않는다.
5. 원본 Contract → Derived Receipt → Envelope → Lineage → Final Claim에서 material semantic field는 보존·재검증·명시적 폐기 중 하나를 선택한다.
6. `PASS|VERIFIED|COMPLETE|LOCKED|INDEPENDENT` 같은 강한 상태는 prerequisite receipt가 없으면 발급하지 않는다.
7. v2 계약은 v1을 즉시 폐기하지 않는다. v1→v2 migration 및 fail-closed compatibility를 별도 관리한다.

## 3. P0 Root Defect Family와 02~08 적용 책임
| Root defect family | 02 Functional | 03 Review | 04 Architecture/API | 05 UI/UX | 06 Test/Operation | 07 AI/Agent | 08 Decision |
|---|---|---|---|---|---|---|---|
| DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF | 계산/재수행 기능 요구 | self-declared PASS Finding | proof/receipt 필수화 | declared vs proven 분리 | 선언만 바꾸는 mutation | AI self-attestation 금지 | 인정 가능한 proof 기준 |
| COUNT_OR_LABEL_AS_PROOF | denominator identity 요구 | count inflation/label overclaim | item identity+digest | count와 verified population 분리 | duplicate/missing case injection | benchmark denominator 고정 | 최소 표본/다양성 기준 |
| SEMANTIC_TYPE_ERASURE_ACROSS_HANDOFF | material field preservation | handoff semantic loss Finding | typed envelope/derived contract | 독립성/신선도 badge 보존 | field-drop mutation | independence profile 보존 | mandatory preservation set |
| CROSS_CONTRACT_SEMANTIC_CONFLICT | conflicting requirement reconciliation | enum/state/authority conflict Finding | cross-contract invariant engine | conflict 노출 | incompatible instance fixture | AI profile/sandbox conflict | canonical precedence |
| CANONICAL_GATE_BYPASS | hard gate prerequisite | bypass path Finding | gate composition contract | blocked reason/next gate | alternate path attack | AI/human bypass fixture | gate authority |
| DESIGNED_CONTROL_OUTSIDE_CANONICAL_GATE_PATH | operation/lineage 편입 요구 | orphan-control Finding | lineage/operation/acceptance binding | Not Integrated 표시 | no-op/skip fixture | SA/AI operation path | activation selector |
| STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE | status issuance prerequisite | overclaim Finding | status ontology v2 | historical/current/qualified 분리 | false-label fixture | human/AI confidence ceiling | label policy |
| AUTHORITY_AND_INDEPENDENCE_GAP | principal/SoD lifecycle | fake independence Finding | authority profile/key registry | role vs principal 표시 | same-principal multi-key attack | model/key!=independent | qualification/expiry |
| FRESHNESS_INVALIDATION_GAP | stale propagation 기능 | stale reuse Finding | invalidation graph | stale/current priority | source/policy/oracle change | model/prompt/tool drift | freshness SLA |
| EVIDENCE_IDENTITY_AND_CANONICALIZATION_GAP | canonical digest 요구 | opaque digest Finding | canonicalization profile | receipt identity 상세 | one-byte/canonicalization mutation | AI profile exact identity | crypto/version policy |

## 4. P0 적용 Batch A — Status / Finality / Publication
### 대표 Finding
- LOCAL_FINAL_RECEIPT_ALWAYS_PASS_BY_SCHEMA
- LOCAL_AGENT_RECEIPT_ALWAYS_PASS_BY_SCHEMA
- FINAL_LOCK_LOCKED_CONST_WITHOUT_FAILURE_REPRESENTATION
- OREVIEW_REVIEW_EXECUTION_CONST_PASS
- FAILURE_MEMORY_VERIFIED_STATE_WITHOUT_VERIFICATION_RECEIPT
- SECURITY_REVIEW_COMPLETE_WITH_ZERO_EVIDENCE
- STATE_MODEL_FINAL_LOCKED_HAS_NO_STALE_REVOKED_EXIT
- STATUS_VOCABULARY_NO_STALE_STATUS
- STATUS_VOCABULARY_NO_REVOKED_INVALIDATED_STATUS
- STATUS_VOCABULARY_MISSING_QUALIFICATION_DIMENSION

### 02 Functional 적용
- 상태는 `implementation`, `execution`, `evidence_binding`, `independence`, `qualification`, `freshness`, `publication` 차원으로 분리한다.
- PASS/VERIFIED/COMPLETE/LOCKED 발급은 상태별 prerequisite receipt set을 요구한다.
- Final 이후에도 STALE/REVOKED/INVALIDATED/REASSESSMENT_REQUIRED로 전환 가능해야 한다.

### 03 Review 적용
Finding family:
- `SUCCESS_ONLY_SCHEMA`
- `STRONG_LABEL_WITHOUT_PREREQUISITE`
- `FINALITY_WITHOUT_REVOCATION_EDGE`
- `QUALIFICATION_DIMENSION_COLLAPSED`

### 04 Architecture/API 적용
신규 후보: `assurance-status-vocabulary.v2.schema.json`.
상태 전이는 reason/evidence/authority/freshness epoch을 포함하며 terminal positive state도 revocation edge를 가진다.

### 05 UI/UX 적용
- Historical PASS와 Current Disposition을 동시에 표시한다.
- `SELF_VALIDATION_NONFINAL`, `INDEPENDENT`, `QUALIFIED`, `STALE`을 동일 PASS 배지로 축약하지 않는다.

### 06 Test 적용
- FAIL/HOLD/NOT_RUN 상태를 success-only schema에 넣었을 때 validator가 거부하는지 확인.
- FINAL_LOCKED 후 revocation event를 주입하고 current status가 stale/revoked로 내려가는지 확인.

### 08 Open Decision
- 공통 status ontology의 canonical owner와 migration version.
- 기존 v1 enum consumer의 fail-closed compatibility 정책.

## 5. P0 적용 Batch B — Receipt / Envelope / Semantic Preservation
### 대표 Finding
- EVIDENCE_RECEIPT_SIGNATURE_OPTIONAL
- RECEIPT_ENVELOPE_AUTHORITY_FREE_TEXT
- RECEIPT_ENVELOPE_STATE_FREE_TEXT
- RECEIPT_ENVELOPE_NO_TENANT_ID
- RECEIPT_ENVELOPE_NO_ENVIRONMENT_TOOLCHAIN_BINDING
- RECEIPT_ENVELOPE_SELF_HASH_CANONICALIZATION_UNDEFINED
- RECEIPT_ENVELOPE_ERASES_INDEPENDENCE_CLASS
- GIT_CHANGE_SET_APPROVAL_NONCE_LOST
- PATCH_APPLY_APPROVAL_EXPIRY_LOST
- PATCH_APPLY_APPROVED_ACTION_SCOPE_LOST
- DURABLE_JOB_APPROVAL_HISTORY_NO_NONCE_EXPIRY_AUTHORITY_EPOCH

### 02 Functional 적용
- Derived Contract Semantic Preservation을 공통 기능 요구로 둔다.
- material field set: tenant, subject, target, scope, policy/authority epoch, nonce/expiry, independence profile, qualification, source/environment/toolchain identity.

### 03 Review 적용
Finding family:
- `DERIVED_CONTRACT_SEMANTIC_LOSS`
- `INDEPENDENCE_TYPE_ERASURE`
- `AUTHORITY_FRESHNESS_FIELD_LOSS`
- `OPAQUE_SELF_HASH`

### 04 Architecture/API 적용
신규 후보: `assurance-receipt-envelope.v2.schema.json`.
`subject`, `context`, `authority`, `independence`, `qualification`, `freshness`, `canonicalization`, `signature`를 typed section으로 강제한다.

### 06 Test 적용
- v2 원본 receipt에서 nonce/expiry/independence를 하나씩 제거한 derived receipt mutation.
- 같은 receipt_id, 다른 bytes substitution.
- canonical field ordering/Unicode/path normalization mutation.

## 6. P0 적용 Batch C — Authority / Key / SoD / Independent Gate
### 대표 Finding
- AUTHORITY_KEY_REGISTRY_UNSIGNED
- AUTHORITY_KEY_PUBLIC_KEY_FILE_NOT_CONTENT_BOUND
- AUTHORITY_KEY_ROLE_COMBINATION_NOT_RESTRICTED
- AUTHORITY_KEY_SCOPE_TENANT_TARGET_PURPOSE_MISSING
- INDEPENDENT_RUN_OPERATOR_AUTHORITY_CONST_NOT_PROOF
- INDEPENDENT_RUN_NO_SIGNATURE
- BLIND_REVIEW_AUTHORITY_CONST_NOT_PROOF
- BLIND_REVIEW_NO_SIGNATURE
- INTERNAL_OTESTER_RECEIPT_CAN_MATCH_STATE_MACHINE_AUTHORITY_NAME
- INTERNAL_OAUDIT_RECEIPT_CAN_MATCH_STATE_MACHINE_AUTHORITY_NAME

### 02 Functional 적용
- authority role 문자열이 아니라 `principal + credential + scope + purpose + validity + registry epoch`을 권위 단위로 사용한다.
- 독립성은 principal/credential admin/implementation/oracle/discovery/knowledge 축을 별도로 평가한다.

### 03 Review 적용
Finding family:
- `AUTHORITY_ROLE_SELF_ATTESTED`
- `SAME_PRINCIPAL_MULTI_ROLE_SOD_COLLAPSE`
- `UNSIGNED_TRUST_REGISTRY`
- `INTERNAL_RECEIPT_AS_EXTERNAL_GATE`

### 04 Architecture/API 적용
신규 후보: `authority-principal-profile.v2.schema.json`.
key registry는 signed root/epoch를 가져야 하며 approval/independent receipt는 해당 epoch와 profile digest를 참조한다.

### 05 UI 적용
- `OTESTER`라는 role name보다 `Internal Self Validation / External Independent / Qualification status`를 우선 표시한다.

### 06 Test 적용
- 같은 principal의 key 3개로 reviewer/operator/final authority 충족 공격.
- Local OTester receipt를 external gate input으로 제출.
- revoked key + stale registry epoch 재사용.

### 07 AI/Agent 적용
- different model/run/key를 independence proof로 사용 금지.
- shared RAG/context/prior verdict가 있으면 knowledge independence를 낮춘다.

## 7. P0 적용 Batch D — Denominator / Requirement / Applicability
### 대표 Finding
- REQUIREMENTS_TRACEABILITY_ATOMIC_MAPPING_EXPLICITLY_PENDING
- PROGRAM_PROFILE_SOURCE_INVENTORY_OPTIONAL_DESPITE_SNAPSHOT_COMPLETE
- PROGRAM_PROFILE_UNKNOWN_ZERO_NO_DISCOVERY_PROOF
- VALIDATION_CASE_REGISTRY_MINIMUM_COUNT_FALSE_COVERAGE
- FINAL_ACCEPTANCE_SOURCE_REGISTRY_COUNT_AS_AUTHORITY
- DOCUMENT_MATERIALIZATION_HARD_CODED_COUNT_87
- OMISSION_INJECTION_COUNTS_WITHOUT_CASE_IDENTITIES
- PACKAGE_REGISTRY_COUNT_RANGE_AS_DENOMINATOR

### 적용
02는 denominator discovery/epoch/applicability를 필수 기능으로, 03은 count-as-proof/false-N/A를 Finding으로, 04는 exact item identity와 source class를 가진 denominator snapshot으로, 05는 included/excluded/unknown/unobservable을 함께 표시하고, 06은 duplicate/missing/legacy inflation mutation을 실행한다.

Final Acceptance와 Validation Case는 고정 count가 아니라 `current denominator epoch + exact case/source IDs + digest`를 소비해야 한다.

## 8. P0 적용 Batch E — Harness / Oracle / Execution Identity
### 대표 Finding
- HARNESS_COMMAND_SCRIPT_CONTENT_DIGEST_MISSING
- HARNESS_FIXTURE_ID_NOT_CONTENT_BOUND
- HARNESS_ORACLE_ID_NOT_CONTENT_BOUND
- HARNESS_RECEIPT_BINDING_CAN_BE_EMPTY
- BEHAVIOR_PROFILE_OBSERVATION_DECISION_WITHOUT_ORACLE_BINDING
- BEHAVIOR_RECEIPT_ORACLE_IDENTITY_MISSING
- INDEPENDENT_RUN_NO_ORACLE_FIXTURE_VALIDATOR_BINDING

### 적용
04는 `ExecutionIdentity = target + fixture + command/script + oracle + validator + environment + policy + authority`로 정의한다. 06은 각 축을 한 바이트/한 revision씩 바꾸는 identity mutation을 필수화한다. SA-01 Reperformance와 SA-14 Qualification이 같은 identity contract를 공유한다.

## 9. P0 적용 Batch F — Learning / Memory / Hidden / Benchmark
### 대표 Finding
- LEARNING_VALIDATOR_INDEPENDENCE_DECLARED_NOT_PROVEN
- LEARNING_DATASET_SEPARATION_BYTE_ONLY
- LEARNING_HIDDEN_ACCESS_RESTRICTED_UNDEFINED
- FAILURE_MEMORY_VERIFIED_STATE_WITHOUT_VERIFICATION_RECEIPT
- IMPROVEMENT_MEMORY_IMPROVEMENT_PROVEN_WITHOUT_PROOF_BINDING
- REUSABLE_PATTERN_PRIVACY_RIGHTS_REVIEW_CONST_PASS
- BLIND_REVIEW_BLINDNESS_NOT_PROVEN

### 적용
07은 memory-blind technical proof, hidden corpus access ledger, benchmark precommitment, ground-truth producer/reviewer qualification을 canonical method로 사용한다. 06은 leakage/benchmark shopping/common-mode reviewer mutation을 수행하며, 04는 corpus/benchmark/reviewer qualification epoch을 receipt에 포함한다.

## 10. P0 적용 Batch G — Patch / Git / Deployment
### 대표 Finding
- PATCH_APPLY_HUNK_APPROVAL_BINDING_MISSING
- PATCH_APPLY_TEST_RESULT_WITHOUT_TEST_RECEIPTS
- PATCH_ROLLBACK_EXPECTED_BASELINE_UNBOUND
- GIT_DELIVERY_APPROVAL_BASE_BRANCH_HEAD_UNBOUND
- GIT_CHANGE_SET_PUSH_PASS_DECLARED_WITHOUT_REMOTE_RECEIPT
- DRAFT_PR_RECEIPT_BASE_HEAD_SHA_UNBOUND
- DEPLOYMENT_OPERATIONS_OUTSIDE_CANONICAL_PRODUCT_LINEAGE
- PRODUCTION_GO_NOT_BOUND_TO_DEPLOYMENT_IDENTITY

### 적용
모든 mutating operation은 `approved intent digest -> exact preimage -> actual effect -> provider/read-back -> postimage`를 연결한다. Deployment는 Product Lineage의 정식 stage가 되어야 하며 `Verified Artifact Digest == Deployed Artifact Digest`를 Final/Production 전제조건으로 둔다.

## 11. P0 적용 Batch H — Final Reconstruction / Gate Composition
### 대표 Finding
- FINAL_APPROVAL_NO_NONCE_OR_EXPIRY
- FINAL_APPROVAL_SIGNATURE_MISSING
- FINAL_LOCK_DOES_NOT_REQUIRE_APPROVAL_DECISION_APPROVE
- FINAL_LOCK_RUN_DISTINCTNESS_NOT_ENFORCED
- FINAL_LOCK_NO_OTESTER_OAUDIT_RECEIPT_BINDING
- FINAL_LOCK_NO_SCOPE_REQUIREMENT_POLICY_ORACLE_BINDING
- FINAL_LOCK_NO_FRESHNESS_BARRIER_RECEIPT
- FINAL_CANDIDATE_NO_RUN_RECEIPT_DIGESTS
- FINAL_CANDIDATE_NO_OTESTER_OAUDIT_BINDING
- LEGACY_PUBLICATION_TRANSITION_BYPASSES_HUMAN_ACCEPTANCE

### 적용
신규 후보 `semantic-assurance-gate-receipt.v2.schema.json`은 Final Candidate 이전에 다음을 결속한다.
- current target/source identity
- requirement/scope/denominator epoch
- policy/oracle/validator qualification epoch
- evidence bundle digest
- freshness barrier receipt
- distinct independent OTester/OAudit receipts
- human acceptance receipt
- open P0/P1 blocker count and exact set digest
- current revocation disposition

FinalLock는 단독 boolean/const가 아니라 위 gate receipt를 소비하는 별도 승인/봉인 단계로 남긴다.

## 12. P0 적용 Batch I — Workflow Operation / Reachability
### 대표 Finding
- WORKFLOW_REGISTRY_MISSING_SEMANTIC_ASSURANCE_OPERATIONS
- WORKFLOW_REGISTRY_MISSING_FINAL_ASSURANCE_OPERATIONS
- WORKFLOW_REGISTRY_MISSING_INVALIDATION_OPERATION
- WORKFLOW_REGISTRY_MISSING_REQUALIFICATION_OPERATION
- HUMAN_ACCEPTANCE_HAS_STATE_BUT_NO_CANONICAL_OPERATION_PATH
- GIT_PUSH_STAGE_HAS_NO_CANONICAL_WORKFLOW_OPERATION

### 필수 operation family
- `semantic.applicability.evaluate`
- `semantic.denominator.discover|challenge|lock`
- `semantic.reperformance.run`
- `semantic.authority.revalidate`
- `semantic.independence.assess`
- `semantic.freshness.invalidate|reconstruct`
- `semantic.validator.requalify`
- `assurance.otester.accept`
- `assurance.oaudit.accept`
- `assurance.human-accept`
- `assurance.final-candidate.reconstruct`
- `git.push`
- `deployment.verify-installed`

각 operation은 role/permit/effect class/input/output receipt/timeout/idempotency/version을 가진다.

## 13. 02~08 완료 판정
문서별 완료는 다음을 모두 만족해야 한다.
- 02: 모든 P0에 기능 요구와 acceptance가 존재
- 03: 모든 P0에 machine-detectable Finding family가 존재
- 04: 모든 P0에 entity/invariant/API/contract owner가 존재
- 05: 사용자가 assurance limitation을 오해하지 않는 projection rule 존재
- 06: 모든 P0 root class에 negative/adversarial fixture와 expected disposition 존재
- 07: AI/learning/reviewer/oracle 관련 P0에 qualification/isolation method 존재
- 08: 미확정 threshold/authority/migration decision이 explicit OPEN으로 남음

위 조건은 설계 completeness 기준일 뿐 Runtime PASS가 아니다.

## 14. 구현 우선순위
1. Status/Receipt/Authority/Gate v2 core contracts
2. Workflow Operation v2 + Product Lineage v2
3. Denominator/Applicability + Validation Case/Final Acceptance denominator migration
4. Harness/Oracle/Validator identity and qualification
5. Learning/Memory/Blind/Benchmark governance
6. Patch/Git/Deployment verified-to-deployed binding
7. UI semantic parity and human misinterpretation tests
8. full failure-injection execution + independent qualification

## 15. 현재 경계
본 문서는 P0 Finding을 기존 산출물 책임으로 **적용 설계**한 것이다. 아래는 아직 주장하지 않는다.
- v2 Runtime 구현 완료
- 기존 v1 migration 완료
- P0 Finding CLOSED
- independent OTester/OAudit PASS
- Final Candidate/Final Lock/Production/Commercial GO
