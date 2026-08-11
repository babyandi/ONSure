# ONSure Semantic Assurance Contract Upgrade Blueprint

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Depends on: `10_FINDING_LEDGER.md`
Parent authority: `../04_ARCHITECTURE_DATA_API_OLICENSE.md`, `../06_TEST_OPERATION_IMPLEMENTATION_PLAN.md`

## 1. 목적
이 문서는 `10_FINDING_LEDGER.md`에서 source-confirmed된 P0/P1 Finding을 실제 ONSure Contract/State/API/Operation/Receipt 구조에 어떻게 반영할지 정의한다. 단순 필드 추가가 아니라 **정본 권위, 상태, 실행, 증적, 독립성, freshness, qualification을 하나의 수직 계보로 닫는 것**이 목적이다.

기존 v1 계약은 바로 삭제하지 않는다. v2 후보를 병행 설계하고 `reader/writer compatibility -> dual-read -> migration receipt -> current selector -> v1 retirement` 순서로 전환한다.

## 2. 공통 v2 Contract Header
모든 신규/승격 machine contract는 가능한 한 아래 공통 header를 갖는다.

```text
contract_id
contract_version
schema_digest
subject_type
subject_id
subject_digest
tenant_context_ref
target_manifest_digest
scope_epoch
requirement_universe_epoch
policy_epoch
run_epoch
created_at
valid_from
valid_until
current_disposition
parent_edges[]
producer_principal_ref
producer_key_ref
signature
canonicalization_profile
final_claim_allowed
```

`parent_edges`는 단순 ID 목록이 아니라 다음 relation을 typed edge로 표현한다.
`DERIVED_FROM | REPERFORMS | AGGREGATES | SUPERSEDES | CONTRADICTS | INVALIDATES | REVOKES | QUALIFIES | APPLIES_TO | EFFECT_OF`

모든 graph는 self-edge/cycle 검사를 통과해야 한다. 단, 역사적 supersession graph와 causal graph가 각각 다른 DAG를 가질 수 있으므로 graph class를 구분한다.

## 3. Bundle A — Assurance Status Ontology v2
### 3.1 상태 차원 분리
기존 implementation/verification 두 축에 다음을 추가한다.

- Implementation: `DESIGN_ONLY | CONTRACTED | IMPLEMENTED | EXECUTION_READY | DEPRECATED`
- Execution: `NOT_RUN | RUNNING | PASS | FAIL | HOLD | BLOCKED | INCONCLUSIVE`
- Evidence: `UNBOUND | PARTIALLY_BOUND | EVIDENCE_BOUND | REPERFORMED_BOUND`
- Independence: `NOT_ASSESSED | SELF_VALIDATION | INDEPENDENCE_PARTIAL | INDEPENDENT`
- Qualification: `NOT_QUALIFIED | QUALIFICATION_PENDING | QUALIFIED | QUALIFICATION_STALE | QUALIFICATION_REVOKED`
- Freshness: `CURRENT | STALE | INVALIDATED | REVOKED | SUPERSEDED | HISTORICAL_ONLY | STATUS_UNKNOWN`
- Assurance: `NON_FINAL | DEGRADED_ASSURANCE | REASSESSMENT_REQUIRED | FINAL_CANDIDATE | FINAL_LOCKED`

### 3.2 Publication 상태기계 분리
`PublicationHistory`는 과거 이벤트를 append-only로 보존한다.
`CurrentAssuranceValidity`는 현재 유효성을 별도로 계산한다.

FinalLock이 역사적으로 존재해도 `CurrentAssuranceValidity=REVOKED`일 수 있다. 이 두 값은 서로 덮어쓰지 않는다.

### 3.3 Hard invariant
- `STALE|REVOKED|INVALIDATED|STATUS_UNKNOWN`은 positive current Final claim 불가
- `SELF_VALIDATION`은 Independent PASS로 mapping 불가
- `QUALIFICATION_STALE` validator 결과는 critical Final 근거 불가
- `INPUT_REQUIRED`는 PASS로 승격 금지

## 4. Bundle B — Evidence Receipt / ReceiptEnvelope v2
### 4.1 EvidenceReceiptV2 필수 결속
- target manifest/scope/requirement/policy/run epoch
- command/toolchain/environment digest
- fixture/oracle/detector/validator identity + qualification epoch
- actor/principal/credential/authority epoch
- evidence strength
- observability sufficiency
- freshness disposition
- all attempts set digest
- parent typed edges
- tenant binding
- signature

### 4.2 Canonicalization
`ONSURE_CANONICAL_JSON_V1`을 별도 계약으로 두고 다음을 고정한다.
- UTF-8
- Unicode NFC
- deterministic key order
- integer/decimal normalization
- null vs missing semantic rule
- RFC3339 UTC timestamp
- path separator/case policy
- ZIP/document set ordering
- self-hash field exclusion rule

### 4.3 ReceiptEnvelopeV2
Envelope는 원 receipt의 assurance semantics를 잃지 않는다.
필수 carry-forward:
- `assurance_class`
- `independence_class`
- `qualification_state`
- `freshness_state`
- `scope_epoch`
- `authority_epoch`
- `source_receipt_digest`

OTESTER/OAUDIT/PUBLICATION type은 generic free-text authority로 만들 수 없고 specialized profile을 참조한다.

## 5. Bundle C — Principal / Authority / Key Registry v2
### 5.1 AuthorityKeyRegistryV2
Registry 자체가 signed root artifact다.

필수:
- registry_epoch
- previous_registry_digest
- root_signers[]
- quorum_rule
- signature_set
- recovery_authority_ref
- emergency_reseed_policy_ref

Key entry 필수:
- key_id/public_key_sha256/algorithm
- principal_id
- tenant/resource/operation/purpose scope
- valid_from/until
- revoked/revoked_at/revocation_reason
- replaced_by exact key ref
- credential assurance level

### 5.2 SoD
동일 principal이 high-risk requester/approver/executor/verifier/final authority 역할을 동시에 수행하지 못하게 policy graph로 강제한다. `different key != different principal`을 machine rule로 고정한다.

### 5.3 Decision-to-Effect
모든 approval은 `nonce`, `consumed_at`, `effect_deadline`, `authority_epoch`, `policy_epoch`, `effect_target_digest`를 갖는다. 실제 effect 직전 재검증 결과를 `EffectAuthorityRevalidationReceipt`로 남긴다.

## 6. Bundle D — Target / Program / Requirement / Denominator v2
### 6.1 TargetManifestV2
- target type/archetype
- classifier version/qualification
- confidence
- alternative classifications
- applicable SA capability set
- N/A candidate + challenge result
- source/deployment/runtime surfaces

### 6.2 RequirementUniverseV2
단일 문서를 denominator authority로 사용하지 않는다.
Source class:
`BUSINESS | CONTRACT | POLICY | CODE | CONFIG | ARCHITECTURE | DATA | API | SECURITY | PRIVACY | OPERATIONS | RIGHTS | FAILURE_RECOVERY | RUNTIME_BEHAVIOR | EXTERNAL_STANDARD`

각 source class는 completeness state와 evidence를 가진다.

### 6.3 AtomicRequirementV2
PASS에는 acceptance criterion별 oracle digest, execution receipt, evidence refs가 필수다.
IMPLEMENTED에는 code/contract/data/API 중 실제 implementation trace가 최소 하나 필요하다.

### 6.4 DenominatorEpoch
모든 CoverageReport/FinalAcceptance/ValidationCaseRegistry는 동일 current denominator epoch을 참조한다. denominator 변경 시 downstream artifact를 stale 처리한다.

## 7. Bundle E — Harness / Behavior / Oracle / Validator Qualification
### 7.1 HarnessCommandManifestV2
각 entry는:
- fixture digest/version/provenance
- command executable digest
- script digest
- working source tree digest
- environment key value commitment
- oracle digest/version
- timeout/resource profile
- mandatory receipt bindings
을 가진다.

`receipt_binding`은 선택형이 아니라 material minimum set을 강제한다.

### 7.2 BehaviorProfileV2
AI/non-AI conditional schema를 분리한다.
`stable=true`는 unstable scenario 0 + repeated run threshold + statistical rule 충족 시에만 가능하다.

### 7.3 OracleQualification
Oracle도 qualification subject다.
- implementation digest
- ground-truth producer
- calibration corpus
- known failure modes
- validity scope/epoch

### 7.4 ValidatorQualificationRun
public regression, hidden corpus, OOD, design-omission mutation, contract mutation, authority mutation을 분리한다.

## 8. Bundle F — Learning / Memory / Benchmark / Hidden Corpus
### 8.1 Promotion pipeline
강제 순서:
`CANDIDATE -> VALIDATION_PENDING -> VALIDATION_PASSED -> PROMOTION_APPROVED -> SHADOW_APPLIED -> CANARY_APPLIED -> STABLE_APPLIED -> APPLIED_LOCKED`

Critical path에서 stage skip을 schema/state machine 양쪽에서 차단한다.

### 8.2 Candidate identity
모든 transition은 동일 candidate digest를 carry한다.

### 8.3 Hidden/Golden governance
- corpus owner
- access ACL
- denied-source audit
- first disclosure
- semantic family
- rotation/retirement
- leakage incident
- qualification invalidation scope

### 8.4 Benchmark precommit
benchmark set digest와 selection policy를 결과 visibility 이전 trusted timestamp에 고정한다.

### 8.5 Memory
Failure/Improvement/Reusable Pattern의 VERIFIED/ACTIVE 상태는 proof receipt를 필수로 참조한다. privacy/rights review는 const PASS가 아니라 signed decision receipt다.

## 9. Bundle G — Patch / Git / Deployment / Verified-to-Deployed
### 9.1 Patch
PatchPlan hunk는 exact preimage/replacement/postimage digest와 approval scope를 가진다.
PatchApply는 nonce/expiry/authority epoch를 carry하고 focused/full regression receipt를 직접 참조한다.

### 9.2 Rollback
Rollback은 source tree만이 아니라 DB migration/config/external effect를 disposition한다.
`ROLLED_BACK` 후 post-rollback verification이 없으면 assurance는 `REASSESSMENT_REQUIRED`다.

### 9.3 Git
Approval은 branch name뿐 아니라 base/head commit을 고정한다.
Push/Draft PR receipt는 provider read-back으로 exact remote ref를 확인한다.

### 9.4 Deployment
Canonical lineage에 `DEPLOYMENT_PLAN -> DEPLOYMENT_APPLY -> DEPLOYMENT_READBACK -> VERIFIED_TO_DEPLOYED_BINDING`을 추가한다.
Production GO는 verified artifact digest와 deployed artifact digest가 동일하거나 승인된 transformation proof가 있을 때만 가능하다.

## 10. Bundle H — Final Reconstruction / Approval / Lock v2
### 10.1 FinalFreshnessBarrier
Final 후보 직전 다음 current state를 다시 읽는다.
- target/source/deployment digest
- requirement/denominator epoch
- policy/authority/revocation epoch
- validator/oracle qualification
- OTester/OAudit qualification/freshness
- open findings/accepted risk/unknown/exclusions
- SA/XC closure

### 10.2 FinalClaimReconstruction
기존 summary를 신뢰하지 않고 parent receipt graph에서 Final claim을 재구성한다.

### 10.3 FinalApprovalV2
signed, nonce-bound, expiring, target/candidate digest-bound, authority epoch-bound.

### 10.4 FinalLockV2
필수 parent:
- FinalFreshnessBarrierReceipt
- FinalClaimReconstructionReceipt
- HumanAcceptanceReceipt
- IndependentOTesterReceipt
- IndependentOAuditReceipt
- SAClosureReceipt
- exact two independent full-chain run receipts

`APPROVAL decision != APPROVE`이면 schema validator와 runtime invariant 둘 다 차단한다.

## 11. Bundle I — Workflow Operation Registry v2
operation entry를 단순 문자열에서 typed object로 바꾼다.

필수 필드:
- operation_id/version/lifecycle
- required roles/principal policy
- tenant/resource/purpose scope
- effect class
- reversibility class
- required input/output receipt contracts
- SoD rule
- idempotency/replay rule
- minimum client/executor version
- fail-closed disposition

신규 operation family:
- `assurance.applicability-evaluate`
- `assurance.reperform`
- `assurance.denominator-challenge`
- `assurance.obligation-close`
- `assurance.authority-revalidate`
- `assurance.invalidate`
- `assurance.current-disposition`
- `assurance.sod-assess`
- `assurance.validator-requalify`
- `assurance.final-freshness-barrier`
- `assurance.final-reconstruct`
- `assurance.human-acceptance`
- `assurance.final-candidate`
- `assurance.final-lock`
- `git.push`
- `deployment.verify-readback`

모든 operation은 CLI/API/VSCode surface parity를 검증한다. Final authority를 가진 operation은 일반 SDK에서 자동 노출하지 않는다.

## 12. Bundle J — Schema / Mutation / Meta-validator Qualification
### 12.1 SchemaInstanceRegistryV2
모든 schema entry는 schema digest, valid fixture digest, invalid fixture digest를 가진다. P0 schema는 최소 1개 이상의 semantic-invalid fixture가 필요하다.

### 12.2 Cross-contract invariant suite
필수 mutation:
- child FAIL while parent PASS
- run1==run2
- approval REJECT reused
- authority epoch stale
- same principal different keys
- ReceiptEnvelope independence erased
- denominator item removed
- oracle digest changed under same ID
- source/policy mixed rows
- semantic capability removed from Final denominator
- status unknown treated PASS

### 12.3 Meta-validator
CrossContractInvariantEngine, FinalClaimReconstructor, IndependenceVerifier, ApplicabilityClassifier, ContaminationClassifier를 qualification 대상에 포함한다.

## 13. Migration 순서
1. v2 schema/design 승인
2. current selector 정의
3. dual-read / v1 write 금지 시점 정의
4. historical v1 artifact classification
5. migration/reseal receipt 생성
6. v2 negative/adversarial fixture 실행
7. current-source full chain 2회
8. independent OTester/OAudit 재검증
9. Final gate 편입 여부 별도 승인

v1 artifact는 자동으로 v2 PASS로 승격하지 않는다. migration 후에도 `HISTORICAL_ONLY`일 수 있다.

## 14. 기존 산출물 반영 책임
- 02 Functional: 각 Bundle의 user/system function, input/output, acceptance
- 03 Review: Finding family 및 cross-contract semantic conflict rule
- 04 Architecture: v2 entities/state/API/typed graph/selector/migration
- 05 UI: stale/revoked/nonfinal/qualification/degraded 표현
- 06 Test: 모든 P0 negative fixture와 fail-injection
- 07 AI/Agent: hidden/benchmark/memory/requalification
- 08 Open Decisions: threshold/legal/retention/clock/offline/compatibility 결정만 유지
- 09 Independent Review: defect family와 SA/XC mapping
- 10 Finding Ledger: canonical defect status authority

## 15. 비권위 경계
본 Blueprint는 구현·마이그레이션 완료가 아니다. v2 Contract가 실제 생성되고 Runtime validator가 소비하며 negative fixture와 independent qualification을 통과하기 전까지 `DESIGN_ONLY / NON_FINAL`이다. Merge, FinalLock, Production GO, Commercial GO를 승인하지 않는다.
