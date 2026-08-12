# ONSure Target-bound Deployment & Release Identity 설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
본 문서는 검증한 artifact와 실제 설치·활성·롤백된 artifact의 동일성을 **Target 단위**로 고정한다. 기존 deployment root가 target에 직접 결속되지 않는 구조에서는 `deployment.verify-installed`를 실행하지 않고 BLOCKED하는 것이 정답이다.

목표 체인:

`Target -> Source -> Build -> Release Candidate -> Signed Package -> Deployment Installation -> Active Deployment -> Verified-to-Deployed Receipt -> Currentness`

## 2. 핵심 불변식
1. 모든 deployment는 정확히 하나의 `target_id`와 `target_manifest_digest`를 가진다.
2. 같은 package라도 다른 target에 설치되면 별도 deployment identity다.
3. 검증 artifact와 deployed artifact는 byte/content digest 또는 정의된 canonical deployment digest로 비교한다.
4. deployment rollback은 과거 assurance를 자동 복원하지 않는다.
5. active deployment가 바뀌면 이전 Final/Certificate currentness를 재평가한다.

## 3. TargetDeploymentIdentity
필수 필드:
- deployment_identity_id
- target_id
- project_id
- tenant_id
- target_manifest_sha256
- source_tree_sha256
- build_provenance_sha256
- release_manifest_sha256
- package_sha256
- deployment_environment_sha256
- installation_root_identity
- deployed_artifact_set_digest
- active_revision
- installed_at
- activated_at
- deployment_authority_profile_sha256
- deployment_receipt_sha256

경로나 버전 문자열만 identity로 사용하지 않는다.

## 4. Build/Release Provenance
Verified-to-Deployed를 성립시키려면 source→artifact 체인이 필요하다.

최소 lineage:
- source commit/tree digest
- build script digest
- build toolchain digest
- dependency/SBOM digest
- environment/container image digest
- output artifact digest
- signing key/profile
- release manifest digest

`source SHA 같음`만으로 build artifact 동일성을 주장하지 않는다.

## 5. Release Manifest
Release Manifest는 최소 다음을 포함한다.
- release_id/version
- target_id
- candidate artifact set
- artifact digest per item
- entrypoint/config digest
- SBOM/provenance refs
- required runtime/dependency versions
- migration scripts digest
- rollout policy
- rollback candidate
- signer

## 6. Installation Identity
Installation은 파일 복사 성공이 아니라 다음을 read-back해야 한다.
- actual installed file set
- actual bytes digest
- actual config/feature flags
- actual runtime environment
- actual executable entrypoint
- active symlink/selector/current pointer

manifest에 적힌 값이 아니라 설치 후 관측값을 receipt로 만든다.

## 7. Verified-to-Deployed 판정
판정:
- EXACT_MATCH
- NON_MATERIAL_VARIANCE
- MATERIAL_MISMATCH
- UNKNOWN

`NON_MATERIAL_VARIANCE`는 사전 정의된 canonicalization/variance policy와 authority receipt가 필요하다. 임의 설명문으로 mismatch를 허용하지 않는다.

`MATERIAL_MISMATCH|UNKNOWN`은 Production/Commercial positive claim을 차단한다.

## 8. Deployment Environment Binding
환경 identity는 최소:
- region/cluster/node pool
- OS/kernel/runtime/JVM
- container image digest
- DB/schema version
- config digest
- feature flag epoch
- secret/credential epoch는 secret value가 아니라 identity/epoch
- external endpoint/contract epoch

## 9. Activation vs Installation
`INSTALLED`와 `ACTIVE`를 분리한다.

상태:
- PACKAGE_VERIFIED
- INSTALLED_INACTIVE
- ACTIVATION_PENDING
- ACTIVE_CURRENT
- ACTIVE_STALE
- ROLLBACK_PENDING
- ROLLED_BACK
- REVOKED
- UNKNOWN

설치만 됐다고 Production currentness를 발급하지 않는다.

## 10. Rollback Assurance
Rollback 시 다음을 수행한다.
1. rollback target identity read-back
2. rollback package/provenance 재검증
3. actual active pointer 확인
4. post-rollback smoke/critical validation
5. current assurance reconstruction
6. downstream certificate stale/revocation 평가

과거에 PASS했던 version으로 돌아갔다는 이유만으로 과거 certificate를 그대로 current로 복구하지 않는다.

## 11. Blue/Green / Canary
Canary/Blue-Green은 population binding이 필요하다.
- variant_id
- traffic population/percentage
- selector/routing config digest
- active target artifact digest per variant
- observation window

Canary PASS를 global deployment PASS로 승격하지 않는다.

## 12. Multi-region / Split Brain
region마다 active deployment가 다를 수 있다. Global currentness는:
- region set denominator
- region deployment identity
- replication/rollout watermark
- stale region count
을 소비한다.

한 region만 최신이어도 전체 PASS로 표현하지 않는다.

## 13. Deployment Authority
설치/활성/롤백 권한은 분리할 수 있다.
- PACKAGE_APPROVER
- DEPLOY_EXECUTOR
- ACTIVATION_APPROVER
- ROLLBACK_AUTHORITY
- DEPLOYMENT_VERIFIER

동일 principal 조합은 SoD policy에 따라 제한한다.

## 14. API 후보
- `POST /deployments/packages/verify`
- `POST /deployments/install`
- `POST /deployments/{id}/activate`
- `POST /deployments/{id}/readback`
- `POST /deployments/{id}/verify-to-source`
- `POST /deployments/{id}/rollback`
- `GET /targets/{targetId}/active-deployments`

## 15. UI 요구
Target 화면에서:
- Verified artifact
- Installed artifact
- Active artifact
- match state
- environment state
- last currentness check
- rollback target
을 동시에 보여준다.

## 16. Negative Fixture
1. correct package / wrong target
2. same version string / different bytes
3. package manifest correct / installed file tampered
4. active pointer points old version
5. canary PASS promoted globally
6. rollback without post-validation
7. region split brain
8. config/feature flag changed after deployment
9. deployment verifier same principal as mutating executor where SoD required
10. stale deployment receipt replay

## 17. Final Gate 연동
Production/Commercial candidate는 반드시:
- semantic gate receipt
- final approval/lock
- TargetDeploymentIdentity
- Verified-to-Deployed receipt
- current deployment read-back
- current freshness
을 결속한다.

## 18. Claude 개발 경계
우선 구현 순서:
1. TargetDeploymentIdentity persistence
2. deployment manifest target binding
3. installed file-set readback
4. active pointer/current revision
5. verified-to-deployed comparator
6. rollback currentness invalidation
7. multi-region/variant 확장

Target binding이 없으면 기존처럼 `deployment.verify-installed=BLOCKED`를 유지한다.

## 19. 현재 상태
- 설계: PRESENT
- target-bound deployment identity runtime: NOT_IMPLEMENTED
- verified-to-deployed schema: candidate 존재
- actual execution: NOT_RUN
- Production authority: 없음
