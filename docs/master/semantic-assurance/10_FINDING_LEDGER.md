# ONSure Semantic Assurance Finding Ledger

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent set: `docs/master/00_ONSURE_MASTER_DESIGN_SET.md`
Source review branch: `codex/meta-validation-doc-refresh`

## 1. 목적
이 문서는 ONSure 자체를 반복적으로 독립 검토하면서 실제 `contracts/*`, `docs/master/*`, 상태모델, 실행 경로, Final/Independent 경계를 교차 대조해 발견한 결함을 **Finding 단위로 보존**한다.

이 문서는 요약 보고서가 아니다. 서로 같은 주제를 다루더라도 실패 시나리오·영향·수정 계약이 다르면 별도 Finding으로 유지한다. 반대로 동일 결함을 다른 파일에서 반복 발견한 경우에는 canonical Finding 하나에 source를 추가한다.

현재 대화 기반 원시 검토에서는 **551개의 raw candidate observation**이 누적되었다. 이 숫자는 중복·세분화·동일 defect family의 반복을 포함한 raw count이며 Final defect count가 아니다. 본 Ledger는 실제 source에서 확인한 P0/P1 중심 Finding을 canonicalize하는 1차 권위 산출물이다. `raw candidate count != canonical defect count` 원칙을 유지한다.

## 2. Finding 분류
- `NEW_DEFECT_CLASS`: 기존 FR-META/XC/SA로 표현되지 않던 새로운 결함 유형
- `EXISTING_CONTROL_ENFORCEMENT_GAP`: 이미 설계된 통제가 실제 v1 Contract/State/Operation에 내려오지 않음
- `CROSS_CONTRACT_SEMANTIC_CONFLICT`: 두 개 이상의 정본 계약이 동시에 참일 수 없거나 서로 다른 의미를 강제
- `SEMANTIC_TYPE_ERASURE`: 원본 Contract의 material semantics가 Receipt/Envelope/Lineage로 넘어가며 사라짐
- `CANONICAL_GATE_BYPASS`: 좋은 통제가 존재하지만 canonical gate path가 해당 통제를 소비하지 않아 우회 가능
- `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`: boolean/string/count/label이 실제 재수행·증적을 대신함
- `COUNT_OR_LABEL_AS_PROOF`: 개수·상태명·권위명만으로 독립성·완전성·실행성을 주장
- `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`: VERIFIED/COMPLETE/TRUE_EXHAUSTION/LOCKED 등 강한 의미가 증적 수준을 초과

## 3. 공통 Finding 필드
향후 machine ledger는 최소 다음 필드를 가진다.

`finding_id, title, classification, severity, source_contracts, current_rule, concrete_defect, failure_scenario, impact, sa_mapping, xc_mapping, required_artifact_changes, contract_change_required, fixture_required, disposition, discovered_at`

`disposition`은 `OPEN | DESIGN_ACCEPTED | CONTRACTED | IMPLEMENTED | EXECUTED | EVIDENCE_BOUND | INDEPENDENTLY_VERIFIED | QUALIFIED | DUPLICATE | SUPERSEDED`를 사용한다. 현재 아래 Finding은 별도 명시가 없으면 `OPEN / DESIGN_ONLY`다.

## 4. P0 Canonical Finding Ledger

### FL-P0-001 CANONICAL_IDENTITY_AMBIGUITY
- 분류: `NEW_DEFECT_CLASS`
- Source: 일반 artifact identity, `evidence-receipt.v1.schema.json`, `receipt-envelope.v1.schema.json`
- 결함: JSON key order, Unicode NFC/NFD, `1`/`1.0`, null/missing, CRLF/LF, ZIP ordering/metadata 등 canonical bytes 규칙이 없다.
- 실패 시나리오: 의미가 같은 artifact가 서로 다른 hash가 되거나, 의미가 다른 artifact가 consumer별 canonicalization 차이로 동일 취급된다.
- 영향: Evidence binding, signature verification, historical reconstruction 불안정.
- 매핑: SA-01, SA-08, SA-12.
- 필수 변경: Canonical Artifact Identity Contract + canonical serialization profile + mutation fixture.

### FL-P0-002 ROOT_TRUST_RECOVERY_UNDEFINED
- 분류: `NEW_DEFECT_CLASS`
- Source: `oruda-authority-key-registry.v1.schema.json`, approval/final authority chain.
- 결함: root key 또는 authority registry 자체가 compromise됐을 때 re-seed/recovery authority가 정의되지 않는다.
- 실패 시나리오: 공격자가 root registry를 장악하면 revocation도 같은 trust root를 사용해 순환한다.
- 영향: 모든 approval/Final trust chain 무효화 가능.
- 매핑: SA-04, SA-08, SA-14.
- 필수 변경: Root Trust Recovery Protocol, emergency re-seed bundle, dual-control recovery receipt.

### FL-P0-003 VALIDATOR_BUILD_PROVENANCE_GAP
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: validator engine, implementation authority, approved dependencies.
- 결함: validator source→build script→compiler/JDK→dependency→environment→binary→signature→qualification 연결이 하나의 lineage로 고정되지 않는다.
- 영향: 검증기 binary가 바뀌어도 과거 qualification 재사용 가능.
- 매핑: SA-01, SA-14.

### FL-P0-004 TARGET_OWNED_TEST_TRUSTED_AS_ORACLE
- 분류: `NEW_DEFECT_CLASS`
- Source: target adapter/harness/test execution.
- 결함: target-owned hooks/test/harness를 ONSure oracle과 같은 trust class로 취급할 위험.
- 실패 시나리오: hostile target이 hidden corpus probing, output poisoning, fake PASS를 생성.
- 매핑: SA-07, SA-11, SA-14.

### FL-P0-005 TCB_RECURSION_UNBOUNDED
- 분류: `NEW_DEFECT_CLASS`
- 결함: verifier-of-verifier를 무한 재귀하지 않도록 하는 irreducible TCB 경계가 명시되지 않는다.
- 매핑: SA-14, XC-25.
- 필수 변경: TCB Manifest, irreducible trust assumptions, TCB mutation/recovery test.

### FL-P0-006 ASSURANCE_HISTORY_ROLLBACK
- 분류: `NEW_DEFECT_CLASS`
- Source: restart/restore, ledger, authority, final publication.
- 결함: DR restore가 revoked cert, consumed nonce, deleted authority, superseded qualification을 되살릴 수 있다.
- 매핑: SA-08, XC-15, XC-16.

### FL-P0-007 RUN_EPOCH_MIXED
- 분류: `NEW_DEFECT_CLASS`
- 결함: 장기 실행에서 credential/policy/model/flag/provider/time epoch가 섞일 수 있다.
- 매핑: SA-01, SA-04, SA-08.
- 필수 변경: RunEpochManifest + effect-time epoch check.

### FL-P0-008 STATUS_ONTOLOGY_DRIFT
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: `status-vocabulary.v1.json`, state model, Semantic Assurance rules.
- 결함: PASS/HOLD/NON_FINAL의 의미가 schema/version별로 변할 수 있으나 ontology version/digest/supersession mapping이 없다.
- 매핑: SA-08, SA-12.

### FL-P0-009 REGION_SPLIT_BRAIN_ASSURANCE
- 분류: `NEW_DEFECT_CLASS`
- 결함: region별 revocation/approval/nonce/authority epoch이 분리되면 A에서 revoked, B에서 valid가 가능하다.
- 필수 변경: AuthorityEpoch+RegionEpoch+watermark/quorum/leader semantics.
- 매핑: SA-05, SA-08.

### FL-P0-010 MIXED_VERSION_SEMANTIC_CONFLICT
- 분류: `NEW_DEFECT_CLASS`
- 결함: rolling upgrade 중 v1/v2 contract/status/evidence가 같은 run에 섞일 수 있다.
- 필수 변경: minimum reader/writer, dual-read/write, unknown fail-close.
- 매핑: SA-08, SA-12.

### FL-P0-011 CANARY_PASS_PROMOTED_GLOBALLY
- 분류: `NEW_DEFECT_CLASS`
- 결함: canary/feature flag population의 PASS가 전체 population PASS로 승격될 수 있다.
- 필수 변경: Variant/Experiment→Population→Artifact/Config→Evidence→Assurance binding.
- 매핑: SA-02, SA-08, SA-12.

### FL-P0-012 LIVE_STATE_SNAPSHOT_DRIFT
- 분류: `NEW_DEFECT_CLASS`
- 결함: repo source는 고정돼도 DB/queue/external service state가 run 도중 변한다.
- 필수 변경: runtime state epoch, DB snapshot, queue watermark, external observation point.
- 매핑: SA-01, SA-07, SA-08.

### FL-P0-013 CAPABILITY_FALSE_NOT_APPLICABLE
- 분류: `NEW_DEFECT_CLASS`
- 결함: applicability classifier가 잘못된 N/A를 내면 전체 validator class가 실행되지 않는다.
- 필수 변경: hidden archetype qualification, critical N/A independent challenge.
- 매핑: SA-02, SA-14, XC-07.

### FL-P0-014 VERIFICATION_CAUSED_UNAUTHORIZED_EFFECT
- 분류: `NEW_DEFECT_CLASS`
- 결함: 검증 자체가 payment/email/Git/webhook/DB mutation을 실제 발생시킬 수 있다.
- 필수 변경: `SIMULATED | SANDBOXED | SHADOW | REAL_EFFECT_AUTHORIZED`, cleanup/compensation evidence.
- 매핑: SA-04, SA-07.

### FL-P0-015 SHARED_CACHE_CONTAMINATION
- 분류: `NEW_DEFECT_CLASS`
- 결함: Maven/npm/pip/Gradle/compiler/Docker/model/RAG cache가 run/tenant 간 오염될 수 있다.
- 필수 변경: tenant+target+toolchain epoch namespace, content verification.
- 매핑: SA-01, SA-14.

### FL-P0-016 ASSURANCE_BACKUP_RESTORE_GAP
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: data RPO/RTO와 assurance RPO/RTO가 분리되지 않는다.
- 필수 변경: nonce/revocation/authority/stale/qualification continuity proof.
- 매핑: SA-08, XC-16.

### FL-P0-017 CORE_STATUS_ONTOLOGY_GAP
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `status-vocabulary.v1.json`.
- 결함: STALE/INVALIDATED/SUPERSEDED/REVOKED/DEGRADED_ASSURANCE/REASSESSMENT_REQUIRED/HISTORICAL_PASS_ONLY/INPUT_REQUIRED/QUALIFICATION 상태가 없다.
- 필수 변경: Assurance Status Ontology v2.
- 매핑: SA-08, SA-14.

### FL-P0-018 EVIDENCE_RECEIPT_META_BINDING_GAP
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `evidence-receipt.v1.schema.json`.
- 결함: target manifest, scope/requirement epoch, denominator, oracle/detector qualification, nonce, strength, freshness, credential/run epoch, observability, independence가 없다.
- 필수 변경: Evidence Receipt v2.
- 매핑: SA-01, SA-02, SA-04, SA-08, SA-14.

### FL-P0-019 RECEIPT_SIGNATURE_OPTIONAL
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `evidence-receipt.v1.schema.json`.
- 결함: signature가 nullable/optional이면 integrity와 authenticity가 분리되지 않는다.
- 필수 변경: trust class별 mandatory signature + key validity binding.

### FL-P0-020 RECEIPT_CANONICALIZATION_UNDEFINED
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: Evidence Receipt/ReceiptEnvelope.
- 결함: self-hash/signature 대상 canonical serialization이 정의되지 않는다.

### FL-P0-021 PUBLICATION_REVOCATION_STATE_GAP
- 분류: `CANONICAL_GATE_BYPASS`
- Source: state model/state machine/final contracts.
- 결함: Final 이후 stale/revoked/superseded/reassessment state가 없다.
- 매핑: SA-08, XC-12, XC-13.

### FL-P0-022 PUBLICATION_HISTORY_VALIDITY_CONFLATION
- 분류: `NEW_DEFECT_CLASS`
- 결함: historical Final event와 current assurance validity가 동일 state machine에 섞인다.
- 필수 변경: Publication History Machine와 Current Validity Machine 분리.

### FL-P0-023 SEMANTIC_OPERATION_AUTHORITY_CONTRACT_GAP
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `workflow-operation-registry.v1.json`.
- 결함: principal/tenant/purpose/authority/effect/qualification/SoD/surface authorization dimension이 없다.
- 매핑: SA-04, SA-07, SA-09.

### FL-P0-024 TARGET_CLASSIFICATION_PROOF_GAP
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: target adapter/target registry.
- 결함: target archetype, confidence, alternative classification, SA applicability proof가 없다.
- 매핑: SA-02, SA-14.

### FL-P0-025 ADAPTER_QUALIFICATION_GAP
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: target adapter 자체의 qualification record/version/capability가 없다.
- 매핑: SA-14.

### FL-P0-026 OBSERVATION_CHANNEL_SUFFICIENCY_GAP
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: collector/channel 존재와 claim observability sufficiency가 분리되지 않는다.
- 필수 변경: claim별 required collector, coverage, blind spot, sufficiency.
- 매핑: SA-01, SA-10.

### FL-P0-027 ASSURANCE_PROPAGATION_ENGINE_UNQUALIFIED
- 분류: `NEW_DEFECT_CLASS`
- 결함: child HOLD→PASS, NOT_RUN→PASS, stale edge 무시, mandatory toggle 등의 mutation에 propagation engine 자체 qualification이 필요하다.
- 매핑: SA-14, XC-25.

### FL-P0-028 LEARNING_SHADOW_CANARY_BYPASS
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: learning validation/application pipeline.
- 결함: 상태는 SHADOW/CANARY를 정의하지만 direct PROMOTION_APPROVED→STABLE_APPLIED 경로가 존재한다.
- 매핑: SA-11.

### FL-P0-029 LEARNING_ROLLBACK_CLOSURE_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: rollback target identity, selector verification, post-rollback validation, downstream invalidation이 없다.
- 매핑: SA-08, SA-11.

### FL-P0-030 INDEPENDENT_VALIDATION_COUNT_NOT_PROVEN
- 분류: `COUNT_OR_LABEL_AS_PROOF`
- 결함: 두 번 검증했다는 boolean/count가 실제 두 execution/principal/evidence-origin independence를 증명하지 않는다.
- 매핑: SA-09, SA-14, XC-10.

### FL-P0-031 PROMOTION_EFFECT_TIME_AUTHORITY_NOT_REVALIDATED
- 분류: `SEMANTIC_TYPE_ERASURE`
- 결함: approval 시 authority는 검사하지만 실제 apply effect 시점의 authority/policy/revocation freshness가 없다.
- 매핑: SA-04.

### FL-P0-032 VALIDATED_CANDIDATE_APPLIED_CANDIDATE_IDENTITY_GAP
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: validation 이후 candidate가 바뀌어도 모든 transition이 동일 immutable candidate digest를 강제하지 않는다.
- 매핑: SA-01, SA-11.

### FL-P0-033 SANDBOX_DISK_INODE_QUOTA_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `sandbox-boundary.v1.json`.
- 결함: disk bytes/tmpfs/inode/file-count quota가 없다.
- 실패 시나리오: tiny-file 또는 disk-fill DoS.

### FL-P0-034 SANDBOX_HISTORICAL_CURRENT_VERIFICATION_CONFLATION
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- 결함: verified fixtures는 채워져 있으나 current runtime verification/independent verification은 NOT_RUN일 수 있다.
- 필수 변경: historical/current/independent verification set 분리.

### FL-P0-035 CRITICAL_RECALL_DENOMINATOR_UNBOUND
- 분류: `COUNT_OR_LABEL_AS_PROOF`
- 결함: false pass/fail 0을 주장해도 critical denominator identity/count가 없다.
- 매핑: SA-02, SA-14.

### FL-P0-036 EVIDENCE_ACTOR_ROLE_FREE_TEXT
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: actor.role 문자열만으로 `INDEPENDENT_AUDITOR`를 주장할 수 있다.
- 필수 변경: authoritative role registry + principal resolution.

### FL-P0-037 EVIDENCE_PARENT_TENANT_BINDING_NOT_ENFORCED
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: parent receipt IDs가 같은 tenant인지 구조적으로 증명하지 못한다.

### FL-P0-038 TENANT_CONTEXT_AUTHORITY_EPOCH_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `tenant-context.v1.schema.json`.
- 결함: role assignment source/validity/revocation/authority epoch이 없다.

### FL-P0-039 TENANT_CONTEXT_PURPOSE_RESOURCE_SCOPE_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: role은 있으나 resource+operation+purpose scope가 없다.

### FL-P0-040 PUBLIC_SDK_OPERATION_AUTHORIZATION_PROFILE_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `public-sdk-boundary.v1.json`.
- 결함: operation별 required role/purpose/resource/effect class가 없다.

### FL-P0-041 LEARNING_VALIDATOR_INDEPENDENCE_DECLARED_NOT_PROVEN
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- Source: `learning-validation-engine.v1.json`.
- 결함: `INDEPENDENT_DECISION` role 이름이 principal/implementation/oracle/knowledge 독립성 proof를 대신한다.

### FL-P0-042 LEARNING_DATASET_SEPARATION_BYTE_ONLY
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: byte hash separation은 semantic-family/near-duplicate/derived fixture contamination을 잡지 못한다.
- 매핑: SA-14, XC-26, XC-28.

### FL-P0-043 LEARNING_HIDDEN_ACCESS_RESTRICTED_UNDEFINED
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- 결함: hidden access가 RESTRICTED 문자열이나 authorized readers/access log/rotation/leak invalidation이 없다.

### FL-P0-044 ASSURANCE_LANE_REQUIREMENT_UNIVERSE_PRELOCKED
- 분류: `CANONICAL_GATE_BYPASS`
- Source: `assurance-lanes.v1.json`.
- 결함: requirement_model이 denominator discovery/challenge보다 먼저 정답처럼 소비될 수 있다.

### FL-P0-045 ASSURANCE_LANE_FULL_REGRESSION_TWICE_IDENTITY_GAP
- 분류: `COUNT_OR_LABEL_AS_PROOF`
- 결함: full regression twice가 distinct execution/evidence identity를 직접 결속하지 않는다.

### FL-P0-046 APPROVED_DEPENDENCY_ARTIFACT_HASH_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `approved-dependency-manifest.v1.json`.
- 결함: Maven coordinate만 있고 실제 artifact SHA/repository origin/signature가 없다.

### FL-P0-047 COMPONENT_SIGNATURE_OMITS_CONFIG_DEPENDENCY_AI_IDENTITY
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `component-contract.v1.schema.json`.
- 결함: code/interface hash만으로 config/dependency/schema/prompt/RAG/tool-policy 변경을 놓친다.

### FL-P0-048 BEHAVIOR_RECEIPT_ORACLE_IDENTITY_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `behavior-observation-receipt.v1.schema.json`.
- 결함: PASS/FAIL을 판정한 oracle version/digest가 없다.

### FL-P0-049 BEHAVIOR_RECEIPT_SIGNATURE_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: runtime behavioral evidence authenticity chain이 약하다.

### FL-P0-050 LICENSE_CREDIT_CONSERVATION_NOT_SCHEMA_ENFORCED
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: `license-state.v1.schema.json`.
- 결함: `total = available + reserved + committed` conservation이 강제되지 않는다.
- 매핑: SA-13.

### FL-P0-051 LICENSE_OFFLINE_GRACE_AUTHORITY_SOURCE_UNBOUND
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: offline grace/tolerance가 어떤 policy/edition authority에서 승인됐는지 없다.

### FL-P0-052 APPROVAL_NONCE_CONSUMPTION_NOT_IN_CONTRACT
- 분류: `SEMANTIC_TYPE_ERASURE`
- Source: plan/hunk/git approval.
- 결함: nonce는 있으나 consume/replay-ledger binding이 없다.

### FL-P0-053 APPROVAL_EFFECT_TIME_REVALIDATION_MISSING
- 분류: `SEMANTIC_TYPE_ERASURE`
- 결함: approved_at/expires_at은 있으나 실행 시 key/actor/policy/revocation freshness 재검증이 없다.

### FL-P0-054 APPROVAL_EVIDENCE_NONCE_EXPIRY_LOSS
- 분류: `SEMANTIC_TYPE_ERASURE`
- Source: execution approval→approval evidence.
- 결함: nonce/expiry가 파생 evidence에서 사라진다.

### FL-P0-055 LOCAL_FINAL_RECEIPT_ALWAYS_PASS_BY_SCHEMA
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- Source: `local-final-receipt.v1.schema.json`.
- 결함: decision이 `const PASS`; FAIL/HOLD/NOT_RUN receipt를 같은 contract로 표현할 수 없다.

### FL-P0-056 LOCAL_FINAL_RECEIPT_NO_SIGNATURE_OR_ACTOR
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: self-validation final receipt의 producer authenticity가 없다.

### FL-P0-057 VULNERABILITY_DENYLIST_EMPTY_CAN_LOOK_SAFE
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- Source: `dependency-vulnerability-denylist.v1.json`.
- 결함: empty denylist가 advisory coverage/currentness 없이 "취약점 없음"으로 소비될 위험.

### FL-P0-058 REUSE_LINK_PROVIDER_INTERFACE_REVISION_UNBOUND
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `reuse-link.v1.schema.json`.
- 결함: provider/consumer exact revision, interface version/schema hash가 없다.

### FL-P0-059 SOURCE_LOCK_TARGET_IDENTITY_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `source-lock.v1.schema.json`.
- 결함: target/tenant/program/environment/dependency set이 source lock에 없다.

### FL-P0-060 EXECUTION_PLAN_EXTERNAL_UNRESTRICTED_CONFLICTS_SANDBOX
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: execution plan vs sandbox boundary.
- 결함: plan은 EXTERNAL_UNRESTRICTED를 허용하고 sandbox는 deny/allowlist 원칙.

### FL-P0-061 EXECUTION_PLAN_PERMISSION_SELF_DECLARATION_GAP
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- 결함: modify/git/push/merge boolean이 request인지 granted authority인지 분리되지 않는다.

### FL-P0-062 PATCH_APPLY_APPROVAL_EXPIRY_SCOPE_LOST
- 분류: `SEMANTIC_TYPE_ERASURE`
- Source: approval→patch apply receipt.
- 결함: nonce/expiry/approved action/hunk authority가 downstream에서 손실된다.

### FL-P0-063 PATCH_APPLY_TEST_RESULT_WITHOUT_TEST_RECEIPTS
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- Source: `patch-apply-receipt.v1.schema.json`.
- 결함: focused/full regression PASS가 receipt IDs/oracle/denominator 없이 enum으로 기록된다.

### FL-P0-064 PATCH_ROLLBACK_VERIFICATION_EFFECT_CLOSURE_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `patch-rollback-receipt.v1.schema.json`.
- 결함: expected baseline, post-rollback build/test/runtime, external/irreversible effect compensation이 없다.

### FL-P0-065 SERVICE_VERIFICATION_PROVIDER_AUTHORITY_UNBOUND
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `service-verification-receipt.v1.schema.json`.
- 결함: provider가 해당 payment/refund/deletion case의 authoritative provider인지 증명하지 않는다.

### FL-P0-066 RECEIPT_ENVELOPE_AUTHORITY_STATE_CLAIMS_UNTYPED
- 분류: `SEMANTIC_TYPE_ERASURE`
- Source: `receipt-envelope.v1.schema.json`.
- 결함: authority/previousState/nextState free text, claims arbitrary object. typed semantics가 envelope에서 지워진다.

### FL-P0-067 FINAL_ACCEPTANCE_SOURCE_CONTENT_EPOCH_MISSING
- 분류: `CANONICAL_GATE_BYPASS`
- Source: final acceptance source registry.
- 결함: path/section/count만 있고 source content hash/requirement epoch이 없다.

### FL-P0-068 FINAL_APPROVAL_SIGNATURE_AUTHORITY_EXPIRY_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `oruda-final-approval-receipt.v1.schema.json`.
- 결함: HUMAN_FINAL_AUTHORITY const만 있고 signature/key/nonce/expiry/epoch proof가 없다.

### FL-P0-069 FINAL_LOCK_APPROVAL_DECISION_NOT_REQUIRED
- 분류: `CANONICAL_GATE_BYPASS`
- Source: `oruda-final-lock.v1.schema.json`.
- 결함: approval receipt hash는 요구하지만 그 receipt의 decision=APPROVE를 직접 요구하지 않는다.

### FL-P0-070 FINAL_LOCK_RUN_DISTINCTNESS_EVIDENCE_GAP
- 분류: `COUNT_OR_LABEL_AS_PROOF`
- 결함: run1/run2 job IDs만 있고 distinctness, run receipt digest, PASS/target binding이 없다.

### FL-P0-071 FINAL_LOCK_OTESTER_OAUDIT_FRESHNESS_GAP
- 분류: `CANONICAL_GATE_BYPASS`
- 결함: Final Lock에 OTester/OAudit/freshness barrier/scope-policy-oracle qualification receipt가 직접 없다.

### FL-P0-072 PROGRAM_RISK_SCORE_DRAFT_RULE_HARD_CODED
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: `program-risk-score.v1.schema.json`.
- 결함: 설명은 DRAFT business cutoff라면서 schema는 90/75/60/40을 enforce한다.

### FL-P0-073 PROGRAM_RISK_SCORE_FORMULA_NOT_RECALCULATED
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- 결함: raw counts와 score formula consistency를 schema가 재계산하지 않는다.

### FL-P0-074 VALIDATION_TARGET_REQUIRED_EVIDENCE_PRE_META_MODEL
- 분류: `CANONICAL_GATE_BYPASS`
- Source: target registry.
- 결함: denominator/applicability/reperformance/qualification/freshness evidence가 required set에 없다.

### FL-P0-075 VALIDATION_CASE_ORACLE_STRING_NOT_CONTRACT
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- Source: validation-case registry.
- 결함: oracle이 문자열이며 implementation/version/digest/qualification이 없다.

### FL-P0-076 FAILURE_MEMORY_VERIFIED_WITHOUT_VERIFICATION_RECEIPT
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- Source: `failure-memory.v1.schema.json`.
- 결함: `VERIFIED` 상태가 receipt/oracle 없이 가능하다.

### FL-P0-077 IMPROVEMENT_MEMORY_PROVEN_WITHOUT_PROOF_BINDING
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- Source: `improvement-memory.v1.schema.json`.
- 결함: IMPROVEMENT_PROVEN이 ImprovementProof digest 없이 가능하다.

### FL-P0-078 PROGRAM_PROFILE_SNAPSHOT_COMPLETE_WITHOUT_INVENTORY
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- Source: `program-profile.v1.schema.json`.
- 결함: snapshot_complete=true인데 source_inventory는 required가 아니다.

### FL-P0-079 PROGRAM_PROFILE_VERIFIED_BOOLEAN_ONLY
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- 결함: component/dependency/AI/dataflow item의 verified boolean이 evidence/oracle를 대신한다.

### FL-P0-080 SERVICE_CASE_SUBOBJECTS_UNSCHEMATIZED
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: `service-case-state.v1.schema.json`.
- 결함: preflight/quote/order/payment/delivery/refund/deletion이 arbitrary object.

### FL-P0-081 SERVICE_CASE_DELETION_RECEIPT_BINDING_WEAK
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- 결함: retention_state가 DELETED_SIGNED_EXTERNAL_VERIFICATION인데 deletion_receipt=null 가능.

### FL-P0-082 UNATTENDED_AUTOPILOT_RESTART_AUTHORITY_REVALIDATION_MISSING
- 분류: `CANONICAL_GATE_BYPASS`
- Source: `unattended-autopilot.v1.json`.
- 결함: continue_after_restart=true이나 source/policy/approval/credential freshness recheck가 없다.

### FL-P0-083 SCHEMA_INSTANCE_NEGATIVE_REQUIRED_NOT_PER_SCHEMA_ENFORCED
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: schema-instance registry.
- 결함: global negative required=true지만 많은 schema에 invalid fixture가 없다.

### FL-P0-084 IMPROVEMENT_PROOF_CROSS_FIELD_INCONSISTENCY
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: improvement-proof.
- 결함: decision=IMPROVEMENT_PROVEN + regression FAIL/context mismatch/new critical 등 모순을 막지 않는다.

### FL-P0-085 REUSABLE_PATTERN_PRIVACY_RIGHTS_REVIEW_CONST_PASS
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- Source: reusable-pattern-memory.
- 결함: privacy_review/rights_review가 const PASS이며 reviewer/evidence 없음.

### FL-P0-086 DURABLE_JOB_CHECKPOINT_EPOCH_GAP
- 분류: `SEMANTIC_TYPE_ERASURE`
- Source: durable-job-state.
- 결함: checkpoint에 source/policy/authority/run epoch과 approval nonce/expiry가 없다.

### FL-P0-087 IMPLEMENTATION_AUTHORITY_PATH_NOT_CONTENT_BOUND
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- Source: implementation-authority.
- 결함: authoritative class/script가 path/name만 있고 digest 없음.

### FL-P0-088 MAIN_BRANCH_PROTECTION_STATUS_CHECK_UNSPECIFIED
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- 결함: independent status checks required=true지만 check identity/authority/head SHA/freshness가 없다.

### FL-P0-089 PRODUCT_SCOPE_TARGET_SUPPORT_UNQUALIFIED
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- 결함: AI/web/API/desktop/mobile 등 지원대상은 넓으나 target-type qualification matrix가 없다.

### FL-P0-090 LOCAL_AGENT_RECEIPT_ALWAYS_PASS_BY_SCHEMA
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- Source: local-agent receipt.
- 결함: decision const PASS; FAIL/HOLD/NOT_RUN 표현 불가.

### FL-P0-091 OREVIEW_PASS_WITH_EMPTY_EVIDENCE
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: `oreview-result.v1.schema.json`.
- 결함: domain PASS인데 evidence_refs 빈 배열 가능.

### FL-P0-092 OREVIEW_NOT_APPLICABLE_WITHOUT_JUSTIFICATION
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: NOT_APPLICABLE justification/challenge가 없다.

### FL-P0-093 OREVIEW_QUALITY_PASS_WITH_FAILED_DOMAIN
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- 결함: domain FAIL/HOLD/NOT_RUN과 quality_decision PASS 조합을 막지 않는다.

### FL-P0-094 RCA_CONFIRMED_WITHOUT_CONFIRMATION_PROOF
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- Source: evidence-based RCA.
- 결함: CONFIRMED가 causal experiment receipt digest/oracle/result 없이 가능.

### FL-P0-095 SECURITY_REVIEW_COMPLETE_WITH_ZERO_EVIDENCE
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- Source: security-findings.
- 결함: COMPLETE에 source/target/scanner/reviewer/executed domain receipt가 없다.

### FL-P0-096 SECURITY_ACCEPTED_RISK_WITHOUT_AUTHORITY
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: ACCEPTED_RISK에 approver/expiry/compensating control/risk receipt가 없다.

### FL-P0-097 LOCAL_RUN_CONTEXT_UNDERSPECIFIED
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: local-run-context.
- 결함: run_id/start만 있고 target/source/tenant/policy/fixture/oracle/environment/toolchain/actor가 없다.

### FL-P0-098 PRODUCT_LINEAGE_EVIDENCE_BUNDLE_PARENT_SET_INCOMPLETE
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: product-process-lineage.
- 결함: EVIDENCE_LOCK stage consume set은 다수 artifact인데 EVIDENCE_BUNDLE parent_bindings는 세 개만 기록.

### FL-P0-099 PRODUCT_LINEAGE_NO_FINAL_RECONSTRUCTION_INVALIDATION
- 분류: `CANONICAL_GATE_BYPASS`
- 결함: lineage가 OAudit에서 끝나며 Freshness Barrier/Final Reconstruction/역방향 invalidation edge가 없다.

### FL-P0-100 ATOMIC_REQUIREMENT_PASS_WITHOUT_TEST_EVIDENCE
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: atomic-requirement.
- 결함: verification_status=PASS인데 test_methods/evidence_refs empty 가능.

### FL-P0-101 ATOMIC_REQUIREMENT_IMPLEMENTED_WITHOUT_IMPLEMENTATION_TRACE
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- 결함: IMPLEMENTED인데 code_symbols/contract_refs empty 가능.

### FL-P0-102 BEHAVIOR_PROFILE_SANDBOX_BACKEND_CONFLICT
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: behavior-profile vs sandbox-boundary.
- 결함: behavior profile은 CI_SUDO_UNSHARE_BWRAP를 허용하지만 sandbox contract는 ROOTLESS_BWRAP only.

### FL-P0-103 BEHAVIOR_PROFILE_STABLE_WITH_UNSTABLE_SCENARIOS
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- 결함: unstable_scenarios가 있어도 stable=true 가능.

### FL-P0-104 RESPONSIBILITY_SEPARATION_RECEIPT_RULE_CONFLICT
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: internal-responsibility-separation vs evidence receipt.
- 결함: every receipt required fields가 실제 common receipt schema와 다르다.

### FL-P0-105 OMAKER_AUTO_ALLOWED_CONFLICTS_PATCH_APPROVAL
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: OMaker/OBuilder adapter vs patch-plan.
- 결함: provider contract는 AUTO_ALLOWED change class를 두지만 patch plan은 모든 hunk APPROVAL_REQUIRED.

### FL-P0-106 REQUIREMENTS_TRACEABILITY_ATOMIC_MAPPING_PENDING
- 분류: `CANONICAL_GATE_BYPASS`
- Source: requirements-traceability.
- 결함: capability-group only, atomic requirements pending 상태.

### FL-P0-107 STATE_MACHINE_PUBLICATION_TERMINAL_INVALIDATION_GAP
- 분류: `CANONICAL_GATE_BYPASS`
- Source: state-machine.
- 결함: PUBLICATION_ELIGIBLE terminal success 이후 stale/revoked/reassessment 경로 없음.

### FL-P0-108 INDEPENDENT_RUN_AUTHORITY_CONST_NO_SIGNATURE
- 분류: `COUNT_OR_LABEL_AS_PROOF`
- Source: ORUDA independent run receipt.
- 결함: INDEPENDENT_EXECUTION_OPERATOR const이지만 key/signature/oracle/qualification 없음.

### FL-P0-109 FINAL_CANDIDATE_RUN_DISTINCTNESS_AND_RECEIPT_GAP
- 분류: `CANONICAL_GATE_BYPASS`
- Source: final-candidate gate.
- 결함: run1/run2 동일 가능, actual run receipt digest/OTester/OAudit/freshness 없음.

### FL-P0-110 AUTHORITY_KEY_REGISTRY_UNSIGNED
- 분류: `NEW_DEFECT_CLASS`
- Source: authority key registry.
- 결함: key registry 자체 signer/signature/digest/epoch 없음.

### FL-P0-111 AUTHORITY_KEY_ROLE_SOD_UNENFORCED
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- 결함: 동일 principal이 reviewer/executor/final authority 역할을 모두 가질 수 있다.

### FL-P0-112 EVIDENCE_REGISTRY_MIXED_SOURCE_POLICY_ROWS
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: evidence registry.
- 결함: row source_hash/policy_digest가 registry-level source/policy와 달라도 차단되지 않는다.

### FL-P0-113 EVIDENCE_REGISTRY_REGRESSION_STATUS_NOT_RUN_IDENTITIES
- 분류: `COUNT_OR_LABEL_AS_PROOF`
- 결함: regression_run_1/2가 status만 저장하고 실제 run receipt가 없다.

### FL-P0-114 BLIND_REVIEW_BLINDNESS_NOT_PROVEN
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- Source: blind-review receipt.
- 결함: Blind Review라는 이름이나 denied context/access log/memory-blind proof가 없다.

### FL-P0-115 PACKAGE_PASS_WITH_EMPTY_OR_FAILED_OUTPUT
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: package execution registry.
- 결함: package PASS인데 output_receipts empty 또는 FAIL/BLOCKED output 가능.

### FL-P0-116 DOCUMENT_MATERIALIZATION_COUNT_87_NOT_DERIVED
- 분류: `COUNT_OR_LABEL_AS_PROOF`
- Source: document materialization.
- 결함: document_count const 87이 실제 nested documents total과 연결되지 않는다.

### FL-P0-117 DOCUMENT_MATERIALIZATION_NO_TARGET_SOURCE_BINDING
- 분류: `SEMANTIC_TYPE_ERASURE`
- 결함: materialization에 target/job/source digest/producer signature가 없다.

### FL-P0-118 HARNESS_RECEIPT_BINDING_CAN_BE_EMPTY
- 분류: `CANONICAL_GATE_BYPASS`
- Source: harness command manifest.
- 결함: receipt_binding이 empty 가능, COMMAND/ORACLE/EXPECTED/ACTUAL material field mandatory 아님.

### FL-P0-119 HARNESS_SCRIPT_FIXTURE_ORACLE_CONTENT_IDENTITY_MISSING
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- 결함: command script, fixture, oracle가 ID/path만 있고 content digest 없음.

### FL-P0-120 TRUE_EXHAUSTION_BEFORE_SOURCE_BYTES_IMPORTED
- 분류: `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`
- Source: execution package map.
- 결함: source bytes NOT_IMPORTED 상태에서 TRUE_EXHAUSTION_DECISION label 존재.

### FL-P0-121 OMISSION_INJECTION_COUNTS_WITHOUT_CASE_IDENTITIES
- 분류: `COUNT_OR_LABEL_AS_PROOF`
- Source: omission-failure-injection-counts.
- 결함: 118 count만 있고 case IDs/digests/expected oracle/mutation diversity가 없다.

### FL-P0-122 SEMANTIC_REGISTRY_OUTSIDE_CANONICAL_LINEAGE
- 분류: `CANONICAL_GATE_BYPASS`
- Source: SA Registry vs product-process-lineage.
- 결함: SA applicability/execution/result artifact 없이 기존 lineage가 끝까지 갈 수 있다.

### FL-P0-123 SEMANTIC_REGISTRY_OUTSIDE_FINAL_ACCEPTANCE_DENOMINATOR
- 분류: `CANONICAL_GATE_BYPASS`
- Source: SA/XC Registry vs final acceptance registry.
- 결함: SA-01~14/XC-01~30이 기존 Final Acceptance denominator에 없다.

### FL-P0-124 SEMANTIC_REGISTRY_OUTSIDE_VALIDATION_CASE_DENOMINATOR
- 분류: `CANONICAL_GATE_BYPASS`
- Source: SA/XC Registry vs validation-case registry.
- 결함: mandatory SA/XC cases가 current positive/negative/adversarial denominator에 없다.

### FL-P0-125 SEMANTIC_CONTROL_WITHOUT_EXECUTABLE_OPERATION_PATH
- 분류: `CANONICAL_GATE_BYPASS`
- Source: SA Registry vs workflow-operation registry.
- 결함: SA 14개 모두 required_execution=true지만 canonical operations가 없다.

### FL-P0-126 STATUS_VOCABULARY_NO_QUALIFICATION_FRESHNESS_DIMENSION
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: status-vocabulary vs SA Registry.
- 결함: SA는 independent≠qualified, executed≠evidence-bound를 요구하지만 공통 ontology가 qualification/freshness/evidence-bound dimension을 표현하지 않는다.

### FL-P0-127 LEGACY_PUBLICATION_BYPASSES_HUMAN_ACCEPTANCE
- 분류: `CANONICAL_GATE_BYPASS`
- Source: state-machine vs state-model-mapping.
- 결함: legacy OAudit→PUBLICATION_ELIGIBLE transition은 Human Acceptance를 직접 요구하지 않는다.

### FL-P0-128 INTERNAL_OTESTER_OAUDIT_CAN_MATCH_EXTERNAL_GATE_NAME
- 분류: `SEMANTIC_TYPE_ERASURE`
- Source: local-agent receipt + state-machine.
- 결함: local self-validation receipt도 authority=OTESTER/OAUDIT를 쓰고 state-machine은 required_authority 이름만 요구한다.

### FL-P0-129 RECEIPT_ENVELOPE_ERASES_INDEPENDENCE_CLASS
- 분류: `SEMANTIC_TYPE_ERASURE`
- Source: local-agent receipt→ReceiptEnvelope.
- 결함: `independent_authority=false`가 Envelope에서 사라지고 receiptType OTESTER/OAUDIT만 남을 수 있다.

### FL-P0-130 PUBLICATION_RECEIPT_SEMANTICS_TOO_GENERIC
- 분류: `CANONICAL_GATE_BYPASS`
- Source: ReceiptEnvelope + state-machine.
- 결함: generic PUBLICATION receipt에는 Human/OTester/OAudit/freshness/qualification binding이 없지만 legacy publication gate가 이를 소비할 수 있다.

### FL-P0-131 DEPLOYMENT_OUTSIDE_CANONICAL_PRODUCT_LINEAGE
- 분류: `CANONICAL_GATE_BYPASS`
- Source: workflow operation registry vs product lineage.
- 결함: deployment.install/rollback operation은 있으나 lineage stage/receipt가 없다.

### FL-P0-132 PRODUCTION_GO_NOT_BOUND_TO_DEPLOYED_ARTIFACT_IDENTITY
- 분류: `CANONICAL_GATE_BYPASS`
- 결함: FinalLock→Production GO 사이 Verified-to-Deployed exact identity receipt가 canonical publication path에 없다.

## 5. P1/구조 보강 Finding Ledger
아래 항목도 source에서 확인되었으며 P0 설계와 함께 Contract v2에 포함한다.

| ID | Finding | Source | 요구 변경 |
|---|---|---|---|
| FL-P1-001 | DATA_REGION_FREE_TEXT_AUTHORITY_GAP | tenant-context | canonical region/jurisdiction registry |
| FL-P1-002 | PUBLIC_SDK_VERSION_COMPATIBILITY_GAP | public-sdk | min client/runtime, introduced/deprecated lifecycle |
| FL-P1-003 | DEPENDENCY_REVIEW_EXPIRY_MISSING | approved dependency | advisory snapshot/max age/recheck trigger |
| FL-P1-004 | COMPONENT_SUPERSESSION_REVISION_UNBOUND | component contract | successor exact contract digest |
| FL-P1-005 | RECEIPT_PARENT_EDGE_SEMANTICS_MISSING | evidence receipt | DERIVED_FROM/SUPERSEDES/CONTRADICTS/... typed edge |
| FL-P1-006 | RECEIPT_GRAPH_CYCLE_NOT_BLOCKED | evidence graph | DAG/self-parent/cycle validator |
| FL-P1-007 | FAIL_CLOSED_SCOPE_UNDEFINED | cross-cutting | claim/capability/run/cert propagation boundary |
| FL-P1-008 | SANDBOX_HOST_PROFILE_REQUALIFICATION | sandbox | backend+kernel+distro+cgroup qualification unit |
| FL-P1-009 | DATASET_VERSION_NOT_CONTENT_IDENTITY | learning | dataset digest/family/separation epoch |
| FL-P1-010 | EVIDENCE_KEY_ID_OPTIONAL | receipt | key mandatory by trust class |
| FL-P1-011 | PUBLIC_SDK_ASSURANCE_CLASS_STATIC | SDK | capability class vs current execution disposition 분리 |
| FL-P1-012 | COMPONENT_QUALITY_COVERAGE_PERCENT_FALSE_AUTHORITY | component | mutation/behavior/negative coverage 분리 |
| FL-P1-013 | BEHAVIOR_RECEIPT_EXIT_TIMEOUT_DECISION_INCONSISTENCY | behavior receipt | expected oracle cross-field rules |
| FL-P1-014 | LICENSE_RESERVATION_TIMESTAMP_STATE_INCONSISTENCY | license | state-dependent timestamps |
| FL-P1-015 | REUSE_LINK_DISCOVERY_CONFIDENCE_SOURCE_MISSING | reuse | discovery evidence/confidence |
| FL-P1-016 | EXECUTION_PLAN_REVIEW_PACK_FREE_TEXT | plan | registry+revision binding |
| FL-P1-017 | EXECUTION_PLAN_FIXTURE_COUNT_WITHOUT_IDENTITY | plan | fixture set digest |
| FL-P1-018 | RESTART_RESTORE_RECOVERED_COUNT_MISMATCH | restore | count=array length invariant |
| FL-P1-019 | RECEIPT_ENVELOPE_PERMIT_ID_WITHOUT_DIGEST | envelope | permit content/version hash |
| FL-P1-020 | FINAL_ACCEPTANCE_SECTION_ANCHOR_MUTABLE | final acceptance | heading/content digest |
| FL-P1-021 | VALIDATION_CASE_MINIMUM_COUNT_FALSE_COVERAGE | case registry | defect-class/risk weighted denominator |
| FL-P1-022 | GIT_DELIVERY_REMOTE_IDENTITY_WEAK | git approval | repo id/server identity/base head SHA |
| FL-P1-023 | FAILURE_MEMORY_CONFIDENCE_SELF_DECLARED | failure memory | calibrated confidence producer |
| FL-P1-024 | SERVICE_CASE_REVISION_SEQUENCE_UNENFORCED | service case | monotonic unique revision rule |
| FL-P1-025 | SCHEMA_INSTANCE_SCHEMA_FIXTURE_DIGEST_MISSING | schema registry | schema/fixture content identity |
| FL-P1-026 | REUSABLE_PATTERN_FIXED_THRESHOLD_UNQUALIFIED | memory | risk/domain-qualified threshold |
| FL-P1-027 | MAIN_BRANCH_MIN_ONE_APPROVAL_WEAK_FOR_SOD | branch protection | risk-class approval policy |
| FL-P1-028 | MODULE_BOUNDARY_PREFIX_BLACKLIST_BYPASS | module boundary | dependency graph/reflection/resource tests |
| FL-P1-029 | PRODUCT_SCOPE_CAPABILITY_MATURITY_MISSING | product scope | per-capability maturity+verification |
| FL-P1-030 | PATCH_PLAN_IMPACT_COUNT_NOT_DERIVED | patch plan | changed files/hunk count exact set equality |
| FL-P1-031 | RCA_UNKNOWN_ITEMS_WITH_CONFIRMED | RCA | confirmed closure requires unknown disposition |
| FL-P1-032 | DRAFT_PR_PR_NUMBER_PROVIDER_ID_MISSING | draft PR | provider/repo/pr/base-head readback |
| FL-P1-033 | CORE_ARCHITECTURE_MVP_LABEL_MATURITY_AMBIGUOUS | core architecture | canonical maturity ontology |
| FL-P1-034 | DEPENDENCY_LICENSE_POLICY_NO_EFFECTIVE_PERIOD | license policy | effective period/legal interpretation revision |
| FL-P1-035 | ATOMIC_REQUIREMENT_SOURCE_LINE_HASH_TOO_NARROW | atomic req | multi-line/table/context commitment |
| FL-P1-036 | BEHAVIOR_PROFILE_NON_AI_MODEL_FIELD_APPLICABILITY | behavior profile | AI/non-AI conditional schema |
| FL-P1-037 | CORE_EXTENSION_PREFLIGHT_PROFILE_UNBOUND | core extension | profile manifest/version/digest |
| FL-P1-038 | PROGRAM_LEARNING_MAX_LOOP_ARBITRARY | methodology | risk-weighted convergence stop |
| FL-P1-039 | STATE_MACHINE_ANY_GUARD_FAILURE_COLLAPSED | state machine | HOLD/BLOCKED/INCONCLUSIVE cause-preserving disposition |
| FL-P1-040 | BLIND_REVIEW_PASS_FAIL_ONLY | blind review | HOLD/BLOCKED/INCONCLUSIVE + reason/evidence |
| FL-P1-041 | DOCUMENT_SET_DIGEST_CANONICALIZATION | document materialization | deterministic set digest profile |
| FL-P1-042 | HARNESS_TIMEOUT_FIXED_WITHOUT_RISK_PROFILE | harness | fixture/risk-specific budget policy |
| FL-P1-043 | LEGACY_TRACE_CONTRIBUTION_PROVENANCE_GAP | legacy authority | migration/mapping receipt |
| FL-P1-044 | REMEDIATION_PLAN_STATE_OUTSIDE_CORE_ONTOLOGY | remediation plan | canonical status mapping |
| FL-P1-045 | SEMANTIC_CAPABILITY_PRIORITY_AGGREGATION_GAP | SA/XC registry | child P0 raises parent execution priority |
| FL-P1-046 | SEMANTIC_REQUIRED_CONTRACT_NOT_RESOLVABLE | SA registry | contract ID/path/version/digest |
| FL-P1-047 | XC_CONTROL_NO_OWNER_MATURITY_EVIDENCE | XC registry | owner/state/required evidence/acceptance |
| FL-P1-048 | NON_FINAL_ONTOLOGY_MAPPING_MISSING | status/state | NON_FINAL↔SELF_VALIDATION_NONFINAL mapping |

## 6. Canonical Gate Hard Block
다음 Finding이 OPEN이면 Semantic Assurance를 Final Gate hard condition으로 승격할 수 없다.

1. FL-P0-017~023: 공통 Status/Receipt/Operation 기반
2. FL-P0-069~071: Final Approval/Final Lock
3. FL-P0-098~099: Product Lineage
4. FL-P0-106~109: Requirement/State/Independent Final candidate
5. FL-P0-110~114: Root Authority/Independent Blind Review
6. FL-P0-122~132: Semantic Assurance canonical gate 편입/우회

이 목록이 OPEN인 상태에서 `FULL_CHAIN_PASS`, `FINAL_CANDIDATE`, `FINAL_LOCKED`, `PRODUCTION_GO`, `COMMERCIAL_GO`를 새 Semantic Assurance 설계에 근거해 주장하지 않는다.

## 7. Contract Upgrade 대상군
세부 스키마·상태·API 설계는 `11_CONTRACT_UPGRADE_BLUEPRINT.md`에서 다룬다.

- Bundle A: Status Ontology v2 + Publication History/Current Validity 분리
- Bundle B: Evidence Receipt/ReceiptEnvelope v2 + canonicalization + typed edges
- Bundle C: Principal/Authority/Key Registry v2 + nonce/effect-time/recovery
- Bundle D: Target/Program/Requirement/Denominator/Appplicability v2
- Bundle E: Harness/Behavior/Oracle/Validator Qualification v2
- Bundle F: Learning/Memory/Benchmark/Hidden Corpus v2
- Bundle G: Patch/Git/Deployment/Verified-to-Deployed v2
- Bundle H: Final Candidate/Final Approval/Final Lock/Freshness Reconstruction v2
- Bundle I: Workflow Operation Registry v2 + Semantic Assurance operation reachability
- Bundle J: Schema Instance/Mutation/Meta-validator qualification

## 8. 검증 Fixture 원칙
모든 P0 Finding은 최소 하나의 reproducible negative fixture를 가진다. 단순 schema-invalid fixture에 그치지 않고 가능하면 실제 runtime/read-back failure를 포함한다.

필수 family:
- semantic type erasure
- stale/epoch replay
- nonce replay
- same principal different key
- fake independent OTester/OAudit
- mixed source/policy evidence
- PASS with missing evidence
- PASS with failed child
- Final approval REJECT reused as lock input
- run1=run2
- source changed after approval
- base branch moved after approval
- provider effect mismatch
- receipt graph cycle
- hidden corpus leakage
- benchmark after-result selection
- restore old authority
- deployment artifact mismatch
- stale certificate consumer misuse
- semantic capability omitted from canonical gate

## 9. 상태 및 비권위 경계
이 Ledger는 **Finding을 설계 산출물에 공식 반영한 것**이지 수정 완료를 의미하지 않는다. 각 Finding은 Contract/Runtime/Fixture/Execution/Independent Verification/Qualification이 닫힐 때만 해소된다.

PR #44의 Draft/Non-final 경계를 유지하며 Merge, FinalLock, Production GO, Commercial GO를 승인하지 않는다.
