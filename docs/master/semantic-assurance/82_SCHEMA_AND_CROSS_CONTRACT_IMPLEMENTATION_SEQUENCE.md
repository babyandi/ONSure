# Schema·Cross-Contract 구현 순서 설계

Status: `DESIGN_ONLY / CANDIDATE / NON_FINAL`

## 1. 원칙
Schema를 무작정 병렬 제정하지 않는다. 하위 identity/epoch/authority가 먼저 존재해야 상위 Final/Certificate 의미가 안전하게 닫힌다.

## 2. 제정 순서
### Wave 1 — Identity Foundation
- TargetManifest
- RequirementUniverseSnapshot
- Scope/Requirement/Policy Epoch
- AuthorityPrincipalProfile
- ValidatorBuildManifest
- CanonicalizationProfile

### Wave 2 — Execution/Evidence
- RuntimeExecutionReceipt
- EvidenceReceiptEnvelope
- EvidenceGraphNode/Edge
- CollectorHealth/Observation qualification
- WorkUnit/Attempt

### Wave 3 — Qualification/Independence
- ValidatorQualification
- IndependentAssuranceExecutionPlan
- IndependentAssuranceReceipt
- HumanAcceptanceReceipt
- AuthorityGrant

### Wave 4 — Final
- AtomicValidationSnapshot
- SemanticAssuranceGateReceipt
- FinalCandidate
- FinalApproval
- FinalLock

### Wave 5 — Deployment/Currentness
- BuildArtifactIdentity
- DeploymentRevision
- RuntimePopulationSnapshot
- CurrentnessSnapshot
- InvalidationEvent
- RecoveryQualificationReceipt

### Wave 6 — Composition/Certificate
- AssuranceSubjectGraph
- CompositionSnapshot
- AssuranceCertificate
- RevocationReceipt
- OfflineTrustBundle

### Wave 7 — Platform/Meta
- PluginManifest/Qualification
- AIPopulationReceipt
- ONSureReleaseQualification
- ActiveContractSelector

## 3. Cross-Contract validator 선행 규칙
각 Wave는 개별 JSON Schema validation만으로 완료하지 않는다. 다음 relation validator를 동시에 구현한다.
- target digest equality
- epoch equality/currentness
- principal/key independence
- purpose/resource/parameter binding
- approval→candidate→lock lineage
- verified artifact→deployed→running equality
- child population→composition denominator completeness
- certificate→composition/final/currentness binding

## 4. Migration
v1 object를 v2로 변환할 때 새 필드를 추정하지 않는다.
- 직접 존재: DIRECT
- authoritative source read-back 필요: READBACK
- 실행 필요: REPERFORMANCE
- 외부 권위 필요: EXTERNAL_AUTHORITY
- 복원 불가: UNRECOVERABLE

`UNRECOVERABLE`은 HOLD이며 null을 그럴듯한 default로 채우지 않는다.

## 5. Fixture 규칙
각 Contract 최소:
- 1 valid
- 2 semantic-invalid
- 1 cross-contract-invalid if relation-bearing
- cryptographic contract는 tamper/replay/revoked-key fixture 추가

## 6. 완료 기준
Wave N 상위 Contract가 Wave N-1의 authority를 문자열/boolean으로 대체하지 않는지 검증한다.
