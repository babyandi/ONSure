# ONSure Operation·Event·Receipt·Authority Matrix

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
모든 effect operation이 `누가/무엇을/왜/어떤 권한으로 실행했고 어떤 event/receipt/evidence를 남겼는지` 닫히도록 한다. Operation name이 곧 authority가 아니며, Event 존재가 Receipt 정당성을 대신하지 않는다.

## 2. 공통 Operation 필드
- operation_name/version
- effect_class: READ_ONLY|LOCAL_MUTATION|EXTERNAL_REVERSIBLE|EXTERNAL_IRREVERSIBLE|ASSURANCE_AUTHORITY
- tenant/resource scope
- required purpose
- required authority policy
- idempotency class/key
- request_context_digest
- required input contracts
- result contract
- event type
- receipt type
- retry policy
- currentness/invalidation effect

## 3. 주요 Matrix
| Operation | Authority | Event | Receipt | Positive effect 조건 |
|---|---|---|---|---|
| semantic.reperformance.run | VERIFIER | ReperformanceExecuted | RuntimeExecutionReceipt | target-bound server root + complete attempt |
| semantic.applicability.evaluate | VERIFIER/REVIEWER policy | ApplicabilityEvaluated | ApplicabilityReceipt | exact subject/requirement epoch |
| semantic.freshness.invalidate | ASSURANCE_OPERATOR | InvalidationRaised | InvalidationReceipt | trigger evidence + affected graph |
| semantic.validator.requalify | INDEPENDENT_QUALIFIER | ValidatorQualificationCompleted | ValidatorQualificationReceipt | independent benchmark/evidence |
| assurance.otester.accept | INDEPENDENT_OTESTER | OTesterDecisionRecorded | IndependentAssuranceReceipt | qualified principal/implementation/oracle |
| assurance.oaudit.accept | INDEPENDENT_OAUDIT | OAuditDecisionRecorded | IndependentAssuranceReceipt | OTester input + independent audit |
| assurance.human-accept | AUTHORIZED_ACCEPTOR | HumanAcceptanceRecorded | HumanAcceptanceReceipt | explicit subject/scope/tier/purpose |
| assurance.final-candidate.reconstruct | FINAL_RECONSTRUCTOR | FinalCandidateReconstructed | FinalCandidateReceipt | raw evidence recomputation |
| assurance.final-lock | FINAL_AUTHORITY | FinalLockIssued | FinalLockReceipt | approval+freshness+independent gates |
| deployment.readback | DEPLOYMENT_VERIFIER | DeploymentObserved | DeploymentReadbackReceipt | target-bound runtime read-back |
| assurance.currentness.evaluate | CURRENTNESS_ENGINE | CurrentnessEvaluated | CurrentnessReceipt | current graph/policy/observer data |
| assurance.compose | COMPOSITION_ENGINE | ProductAssuranceComposed | CompositionReceipt | exact population/edges/rules |
| certificate.issue | CERTIFICATE_ISSUER | CertificateIssued | CertificateIssuanceReceipt | tier/claim ceiling satisfied |
| certificate.revoke | REVOCATION_AUTHORITY | CertificateRevoked | RevocationReceipt | signed reason/effective_at |
| authority.grant | AUTHORITY_ADMIN+SoD | AuthorityGranted | AuthorityGrantReceipt | subset/approval/validity checks |
| authority.break-glass | EMERGENCY_AUTHORITY | BreakGlassActivated | BreakGlassReceipt | narrow scope/TTL/review obligation |
| workunit.commit | WORK_COORDINATOR | WorkUnitCommitted | WorkUnitCommitReceipt | valid lease/idempotency/population |
| plugin.qualify | PLUGIN_QUALIFIER | PluginQualificationCompleted | PluginQualificationReceipt | signed artifact + tests + scope |
| onsure.release.qualify | EXTERNAL/INDEPENDENT_QUALIFIER | ONSureReleaseQualified | ReleaseQualificationReceipt | archetype scope + hidden benchmark |
| recovery.qualify | RECOVERY_QUALIFIER | RecoveryQualificationCompleted | RecoveryQualificationReceipt | ledger/evidence/key reconciliation |

## 4. Effect-time Authority
모든 mutation/effect operation은 authorization check와 effect 사이에 authority epoch가 바뀌는 TOCTOU를 처리한다.
- strong effect는 `authorized_at`만 저장하지 않고 `effect_at`, `authority_epoch_at_effect`를 기록한다.
- revocation이 effect 전에 발생하면 거부한다.
- 장시간 job은 lease/authority recheck checkpoint를 사용한다.

## 5. Idempotency
- 동일 idempotency key + 동일 request_context_digest → 동일 logical result/receipt reference
- 동일 key + 다른 context → `IDEMPOTENCY_CONTEXT_CONFLICT`
- external irreversible effect는 provider idempotency + ONSure ledger를 모두 사용한다.

## 6. Retry
retry는 새 attempt를 생성하고 이전 결과를 삭제하지 않는다. final decision은 attempt history를 참조한다. retryable transport failure와 semantic failure를 구분한다.

## 7. Cancellation
Cancellation은 operation마다 cancel boundary를 정의한다. effect 이후 cancel request가 들어오면 compensation/rollback 여부를 별도 receipt로 기록하며 성공한 effect를 없던 것으로 만들지 않는다.

## 8. Operation Surface
CLI / LOCAL_AUTHENTICATED_API / VSCODE / Web-client(LOCAL API 소비)는 동일 canonical operation을 호출한다. surface별 별도 권위 규칙을 만들지 않는다.

## 9. Completeness Gate
Operation Registry v2에 등록된 effect operation은 다음 중 하나라도 누락되면 ACTIVE 불가다.
- authority mapping
- event mapping
- receipt mapping
- idempotency/retry semantics
- negative fixture
- lineage/evidence edge
