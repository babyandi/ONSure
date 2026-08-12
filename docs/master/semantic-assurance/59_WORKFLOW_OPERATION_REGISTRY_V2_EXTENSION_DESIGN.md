# ONSure Workflow Operation Registry v2 Extension 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
새 Assurance 기능이 문서/Service에만 존재하고 generic dispatcher 밖에서 우회 실행되지 않도록 operation name, effect class, authority class, receipt requirement를 정식 registry 설계로 고정한다.

## 2. Operation Entry 필드
- operation_name/version
- domain
- effect_class
- subject_type
- required_context_fields
- required_authority_profile
- required_qualification
- SoD policy ref
- idempotency_mode
- receipt_type
- async/sync
- reversible/compensatable
- activation version
- deprecated_at nullable

## 3. 신규 Operation 후보
### Currentness/Deployment
- `deployment.target.register`
- `deployment.revision.observe`
- `deployment.runtime.readback`
- `assurance.currentness.evaluate`
- `assurance.invalidation.register`
- `assurance.impact.evaluate`
- `assurance.revocation.issue`

### Composition/Graph
- `assurance.subject.register`
- `assurance.dependency.register`
- `assurance.compose`
- `evidence.graph.commit`
- `evidence.graph.impact`

### Certificate
- `certificate.issue`
- `certificate.verify`
- `certificate.revoke`
- `offline.trust-bundle.issue`
- `offline.reconcile`

### Governance
- `authority.grant.issue`
- `authority.grant.revoke`
- `break-glass.open`
- `break-glass.close`
- `policy.profile.activate`

### Scale/Qualification
- `work-unit.claim`
- `work-unit.complete`
- `work.aggregate`
- `plugin.qualify`
- `adapter.qualify`
- `onsure-release.qualify`

## 4. Authority
READ_ONLY operation도 object ownership을 검증한다. AUTHORITY/FINAL/CERTIFICATE/REVOCATION/POLICY effect는 RBAC role 외 별도 AuthorityGrant/Qualification/SoD를 요구한다.

## 5. Receipt
Mutating operation은 lifecycle receipt를 필수로 한다. Final effect는 signed authoritative receipt를 요구한다. read operation 중 public certificate verification은 별도 public-safe profile을 사용한다.

## 6. Internal Call 금지
Service method 직접 호출로 dispatcher/authority/evidence path를 우회하지 못하도록 authoritative mutation entrypoint는 registry/dispatcher를 통과한다.

## 7. Versioning
Unknown operation/version은 fail-close. deprecated operation은 new authoritative writes 금지, legacy read/reconstruction만 정책적으로 허용 가능.

## 8. Negative Test
- 등록되지 않은 semantic operation 직접 호출
- READ_ONLY로 위장한 mutation
- receipt 없는 completed mutation
- authority effect가 일반 APPROVER role만으로 실행
- deprecated v1 operation으로 v2 Final effect 생성

## 9. 수용기준
모든 29~58 신규 mutation capability가 operation registry에 정식 이름·effect·authority·receipt를 갖는다.
