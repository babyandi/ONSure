# ONSure Semantic Assurance v2 Runtime Wiring·Adapter 구현설계

Status: `IMPLEMENTATION_CANDIDATE / DRAFT / NON_FINAL`

## 1. 목적
본 문서는 P0 Finding을 문서·Schema 수준에서 끝내지 않고 실제 Runtime 경계로 내리는 구현 기준을 정의한다. 현재 branch에는 다음 구현 후보가 존재한다.

- `src/main/java/kr/co/oruda/onsure/platform/SemanticAssuranceV2Reconstructor.java`
- `src/main/java/kr/co/oruda/onsure/platform/SemanticAssuranceV2WorkflowService.java`

두 클래스는 기존 v1 authority를 대체하지 않으며 `SELF_VALIDATION_NONFINAL`만 발급한다.

## 2. v1→v2 Reconstructor 책임
`SemanticAssuranceV2Reconstructor`는 다음을 수행한다.

1. v1 Status/Receipt에서 직접 관찰 가능한 값만 보존한다.
2. 누락된 v2 의미를 다음 GapClass로 명시한다.
   - DIRECTLY_MAPPABLE
   - DERIVABLE_WITH_PROOF
   - REQUIRES_READBACK
   - REQUIRES_REPERFORMANCE
   - REQUIRES_HUMAN_OR_EXTERNAL_AUTHORITY
   - UNRECOVERABLE_FROM_V1
3. legacy `PASS`를 v2 `PASS`로 자동 승격하지 않는다.
4. Evidence/Independence/Qualification/Freshness를 추정하지 않는다.
5. Final Candidate reconstruction은 exact evidence, epoch, OTester/OAudit, Human Acceptance, open Finding 조건이 없으면 HOLD한다.
6. 모든 reconstruction 산출물은 `final_claim_allowed=false`를 유지한다.

## 3. Semantic Workflow Runtime Boundary
`SemanticAssuranceV2WorkflowService`는 다음 operation을 실제 Java method로 분리한다.

- `semantic.applicability.evaluate`
- `semantic.denominator.discover`
- `semantic.denominator.challenge`
- `semantic.denominator.lock`
- `semantic.reperformance.run`
- `semantic.authority.revalidate`
- `semantic.independence.assess`
- `semantic.freshness.invalidate`
- `semantic.freshness.reconstruct`
- `semantic.validator.requalify`
- `assurance.otester.accept`
- `assurance.oaudit.accept`
- `assurance.human-accept`
- `assurance.final-candidate.reconstruct`
- `git.push`
- `deployment.verify-installed`

`git.push`는 외부 effect이므로 현재 `BLOCKED / EXTERNAL_EFFECT_RUNTIME_NOT_WIRED`로 fail-closed한다. `deployment.verify-installed`는 verified/deployed bytes를 직접 read-back해 SHA-256 동일성을 비교한다.

## 4. 기존 Dispatcher 통합 원칙
기존 `LocalWorkflowDispatcher`는 49개 v1 operation의 현재 authority다. v2 operation을 바로 기존 switch에 추가하여 authority를 바꾸지 않는다.

통합 단계:
1. Semantic Workflow Service 단위 테스트
2. TenantRbacService 역할정책 확장
3. `workflow-operation-registry.candidate.v2.json`과 implementation method 1:1 비교
4. 별도 feature flag/selector에서 v2 dispatcher lane 활성화
5. 동일 request의 v1/v2 shadow result 비교
6. independent gate qualification 후에만 canonical dispatcher 후보로 승격

## 5. RBAC / SoD
Semantic v2 operation은 operation name만으로 권한을 부여하지 않는다.

최소 Role Matrix 후보:
- denominator/applicability/reperformance: OPERATOR 또는 AUDITOR, target/tenant scope 필수
- authority.revalidate / independence.assess: AUDITOR
- validator.requalify: AUDITOR + 별도 qualified principal
- otester.accept / oaudit.accept: local self-validation principal 금지
- human-accept: APPROVER 또는 ADMIN이 아니라 `HUMAN_FINAL_AUTHORITY` profile 필요
- final-candidate.reconstruct: AUDITOR 또는 reconstruction service, FinalLock authority 없음
- deployment.verify-installed: OPERATOR + deployment scope

## 6. Runtime Output 공통규칙
모든 v2 runtime output은 최소:
- authenticated tenant
- authenticated actor
- operation
- subject/target identity
- exact input digest where applicable
- output digest
- decision
- assurance_class
- independent_authority
- final_claim_allowed
을 가진다.

현재 구현 후보는 모두 `SELF_VALIDATION_NONFINAL`, `independent_authority=false`, `final_claim_allowed=false`로 고정한다.

## 7. Final Gate Reconstructor
Final Candidate는 단순 job ID 두 개나 aggregate count가 아니라 다음을 요구한다.

- target/source/artifact exact identity
- scope/requirement/denominator/policy/oracle/validator qualification/authority epoch
- non-empty evidence set
- distinct independently qualified OTester/OAudit receipt digest
- explicit Human Acceptance receipt
- open P0=0, open P1=0
- current freshness/revocation
- deployment scope가 있으면 Verified-to-Deployed equality

현재 구현은 이러한 입력 중 하나라도 빠지면 HOLD하고 `final_lock_allowed=false`를 유지한다.

## 8. Deployment Identity
`deployment.verify-installed`는 파일 path 문자열 비교가 아니라 실제 bytes를 SHA-256으로 재계산한다.

성공 조건:
`verified_artifact_sha256 == deployed_artifact_sha256`

불일치 시 FAIL이며 Production GO에 사용할 수 없다.

## 9. 구현 검증 Fixture
최소 Java test family:
- legacy PASS가 v2 PASS로 승격되지 않음
- v1 receipt에 tenant/authority/qualification이 없으면 HOLD
- same principal/multiple key independence false
- denominator duplicate ID HOLD
- applicability N/A rationale missing HOLD
- reperformance byte mismatch HOLD/FAIL
- Final Candidate OTester/OAudit/Human/epoch 누락 HOLD
- deployed byte mismatch FAIL
- `git.push` external effect runtime 미연결 BLOCKED

## 10. 현재 상태
- Reconstructor: implementation candidate created
- Semantic Workflow Service: implementation candidate created
- 기존 dispatcher wiring: NOT_WIRED
- compile/test execution: NOT_RUN in current assistant runtime
- independent OTester/OAudit execution: NOT_RUN
- Final authority: 없음

따라서 본 단계는 `IMPLEMENTATION_CANDIDATE_CREATED / RUNTIME_WIRING_PENDING / EXECUTION_NOT_RUN / NON_FINAL`이다.
