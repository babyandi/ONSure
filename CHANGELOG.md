# 변경 이력

이 문서는 ONSure 독립 저장소의 변경 후보를 기록한다. 실제 release 또는 ORUDA-Products 이관 완료를 의미하지 않는다.

## Unreleased — 모노레포 이관 준비

### Added

- 현재 구조와 `products/onsure/` 미래 구조 대응표
- 전체 이관 대상 파일 기준 Manifest 후보와 비최종 readiness validator
- 공통 SHA-256 primitive 및 플랫폼 중립 RAG preparation request
- canonical/compatibility build 권위 계약과 모듈 경계 validator
- `ONSURE_PRODUCT_ROOT` 기반 제품 루트 해석
- `product.yaml`, 제품 전용 `AGENTS.md`, `.obuilder/` 준비 메타데이터
- 통합 기준 238개 public Java class의 JVM descriptor 호환성 baseline
- CycloneDX 1.6 SBOM과 dependency license 검토 inventory
- 격리된 `products/onsure/` cutover·rollback 리허설
- Draft PR #27/#28 head에 결속된 overlap·재검증 매트릭스
- rootless bubblewrap 실행환경 진단기와 host 구성 가이드
- fail-closed 배포·DB migration `DESIGN_ONLY_NONFINAL` 계약과 validator

### Changed

- 기존 hashing facade는 공개 API를 유지하면서 공통 SHA-256 primitive를 사용한다.
- `FileValidationStore`는 RAG package를 직접 호출하지 않고 공통 중립 candidate preparer를 사용한다.
- Manifest의 untracked 파일 mode는 Git index mode와 동일하게 정규화된다.

### Compatibility

- Java namespace와 Maven coordinate 변경 없음
- 기존 `RagPreparationService`의 `ValidationReport` overload 유지
- 실제 ORUDA-Products 이동, main 병합, 배포, Production GO, Final PASS 없음

### Known migration blockers

- 공유 Java source root 4개 모듈과 `io.onsure.platform` split package
- package 경로 동결로 남아 있는 `platform ↔ platform.oruda` package cycle
- 라이선스·소유권·고객 데이터에 대한 사람의 승인 미실행
- 배포 runtime 및 DB migration 구성요소 미구현(설계 계약만 존재)
