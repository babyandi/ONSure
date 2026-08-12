# ONSure Validator Build Provenance & TCB 설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
검증기의 source가 맞다는 사실만으로 실제 실행 binary가 같은 검증기라고 주장할 수 없다. 본 문서는 Validator의 `source -> build -> dependency -> environment -> binary -> signature -> qualification` 계보와 irreducible TCB를 정의한다.

## 2. Build Provenance Chain
필수 lineage:
`Validator Source -> Build Script -> Build Toolchain -> Dependency/SBOM -> Build Environment -> Binary/Package -> Signature -> Qualification`

모든 edge는 digest로 결속한다.

## 3. ValidatorBuildManifest
필수 필드:
- validator_id/version
- source_tree_sha256
- build_script_sha256
- build_config_sha256
- compiler/runtime version + digest
- dependency_manifest_sha256
- sbom_sha256
- container/environment sha256
- build actor/principal
- output artifact set digest
- signing key/profile
- built_at

## 4. Reproducible Build
가능한 validator는 reproducible build를 목표로 한다. 재현 불가 요소는 명시한다.
- timestamp
- random seed
- generated metadata
- platform-specific bytes

재현불가 요소를 제거하지 못하면 canonical artifact identity policy로 분리한다.

## 5. Dependency/SBOM
Validator 자체 dependency는 검증 대상 dependency와 별개로 관리한다.
- exact artifact hash
- origin/repository
- license
- signature/provenance
- vulnerability snapshot
- transitive dependency

`version string`만으로 dependency identity를 고정하지 않는다.

## 6. TCB Manifest
Irreducible trust boundary:
- OS/kernel
- filesystem
- JVM/runtime
- crypto provider
- trusted time source
- key registry/KMS
- sandbox/container runtime
- process isolation
- canonicalization library
- signature verification library

TCB의 가정과 검증 가능 항목을 분리한다.

## 7. TCB Change
TCB component 변경 시 qualification을 stale 처리한다. 단순 patch/minor version이라도 자동 non-material로 간주하지 않는다.

## 8. Validator Signature
실행 binary/package는 signed manifest를 가진다. 실행 시:
1. package bytes hash
2. manifest hash
3. signer authority
4. signer validity/revocation
을 확인한다.

## 9. Qualification Binding
Qualification receipt는 `validator artifact digest + TCB manifest digest + benchmark epoch`에 결속한다. source version만 같고 binary가 다르면 qualification 재사용 금지.

## 10. Self-update / Self-improvement
Validator가 자기 Rule/Model/Code를 개선하는 경우:
- old validator가 new validator를 단독 승인 금지
- hidden benchmark
- independent verifier
- regression guard
- rollback artifact
를 요구한다.

## 11. Supply-chain Attack Fixture
1. source same / binary mutated
2. build script changed
3. dependency artifact substitution
4. SBOM missing transitive dependency
5. signer revoked after build
6. stale qualification with new binary
7. compiler/JVM changed
8. compromised cache artifact
9. build provenance missing
10. TCB component changed without stale propagation

## 12. Runtime
실행 전 preflight:
- active validator artifact identity
- signature validity
- current qualification
- TCB compatibility
- environment compatibility

하나라도 unknown이면 HIGH assurance lane은 HOLD.

## 13. 운영/UI
Validator detail 화면에:
- source version
- binary digest
- build provenance
- SBOM
- signer
- qualification epoch
- TCB digest
- current/stale
를 표시한다.

## 14. Claude 개발 경계
구현 순서:
1. ValidatorBuildManifest
2. artifact signer/verifier
3. dependency/SBOM collector
4. TCB manifest
5. qualification binding
6. runtime preflight
7. stale propagation

## 15. 현재 상태
- 설계: PRESENT
- build provenance runtime: NOT_IMPLEMENTED
- qualification binding: candidate 일부
- execution: NOT_RUN
