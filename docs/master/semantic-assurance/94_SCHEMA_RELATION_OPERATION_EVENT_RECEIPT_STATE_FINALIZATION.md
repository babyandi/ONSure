# ONSure Schema·Relation·Operation·Event·Receipt·State Ontology 최종 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Covers: Task 11~16

## 1. Schema Generation Profile
공통 JSON Schema 규칙:
- draft: 2020-12
- `$id`: `urn:onsure:<domain>:<name>:v<major>`
- `additionalProperties=false` 기본
- digest: lowercase `^[a-f0-9]{64}$`
- timestamp: RFC3339 UTC, effect-time 필드 별도
- identity: opaque stable ID + digest binding
- enum unknown 처리: positive gate에서 unknown enum fail-closed
- nullable은 의미적으로 `UNKNOWN` 허용 시에만 사용
- signature object는 algorithm/key_id/signed_digest/signed_at/signature_value를 분리

## 2. Cross-Contract Relation Validator
Rule ID 예:
- XR-FINAL-001 Approval=REJECT/HOLD → FinalLock 생성 금지
- XR-CERT-001 Certificate.final_lock_digest가 active final과 일치
- XR-DEPLOY-001 Deployment.expected_artifact_digest=verified artifact
- XR-RUNTIME-001 runtime population member가 deployment revision에 결속
- XR-COMP-001 composition children exact population 일치
- XR-AUTH-001 operation purpose/scope가 AuthorityGrant 범위 내
- XR-RECOVERY-001 recovered generation이 pre-failure generation을 임의 상속하지 않음

relation validator 실패는 schema-valid 여부와 무관하게 HOLD/REJECT.

## 3. Canonical Operation Registry
Namespace:
`learning.* planning.* review.* verification.* improvement.* training.* evidence.* memory.* git.* delivery.* license.* semantic.* assurance.* deployment.* currentness.* certificate.* authority.* recovery.* plugin.* ai.* meta.*`

각 operation:
- operation_name/version
- subject types
- required authority
- input/output contracts
- effect class READ|WRITE|EXTERNAL_EFFECT
- idempotency mode
- event/receipt type
- safe failure state

## 4. Canonical Event Registry
Event 공통 envelope:
`event_id,type,version,tenant_id,subject_id,operation_id,causation_id,correlation_id,occurred_at,effect_time,payload_digest,producer_principal,producer_key_id`.

모든 authoritative write/effect는 event 또는 equivalent durable ledger entry를 생성한다.

## 5. Receipt Taxonomy
- ExecutionReceipt
- EvidenceReceipt
- ApprovalReceipt
- AuthorityReceipt
- QualificationReceipt
- DeploymentReceipt
- CurrentnessReceipt
- CompositionReceipt
- CertificateReceipt
- RecoveryReceipt
- RevocationReceipt
- MigrationReceipt
- DesignLockReceipt

Receipt는 상태 문자열이 아니라 발생한 사실과 결속을 증명한다.

## 6. State Ontology 분리
서로 섞지 않는다.
- Decision: PASS|FAIL|HOLD|INCONCLUSIVE
- Lifecycle: DRAFT|ACTIVE|SUPERSEDED|RETIRED
- Currentness: CURRENT|STALE|REASSESSMENT_REQUIRED|INVALIDATED|REVOKED|UNKNOWN
- Qualification: QUALIFIED|PARTIAL|NOT_PROVEN|EXPIRED|REVOKED
- Authority: VALID|EXPIRED|REVOKED|NOT_YET_VALID|UNKNOWN
- Deployment: PLANNED|TRANSITIONING|DEPLOYED|PARTIAL_DRIFT|ROLLED_BACK|FAILED
- Certificate: ISSUED_CURRENT|ISSUED_STALE|INVALIDATED|REVOKED|EXPIRED|SUPERSEDED|OFFLINE_STATUS_UNCERTAIN

## 7. Acceptance
- 모든 authoritative contract에 schema profile 적용 가능
- 모든 effect operation에 event+receipt mapping 존재
- state axis 간 implicit conversion 0
- relation validator rule 없는 Final/Certificate authoritative edge 0
