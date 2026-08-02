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
- VS Code Extension의 등록 Workspace·Project·Target identity 결속과 로컬 파일 URI 검증
- VS Code용 실행계획 생성·서명 승인 확인·승인 Bundle 기반 Validation 명령
- Node 내장 회귀 테스트와 Local API 등록·학습·Plan HTTP 통합 테스트
- ZIP 순서·timestamp·compression을 정규화하는 재현 가능 VSIX 패키징 도구
- 등록 identity에 결속된 Local Workspace Snapshot API와 14개 VS Code 전용 read view
- 재시작 후 Profile·Plan·Run·Patch/Proof/Git receipt 복구 및 승인형 Patch·Commit·Draft PR 명령
- source digest 검증 Hunk diff, 선택 Hunk 외부 서명 요청 계약과 승인 purpose 정합화
- subprocess group 기반 Autopilot pause/resume/cancel 및 CLI·Local API·VS Code control journal

### Changed

- 기존 hashing facade는 공개 API를 유지하면서 공통 SHA-256 primitive를 사용한다.
- `FileValidationStore`는 RAG package를 직접 호출하지 않고 공통 중립 candidate preparer를 사용한다.
- Manifest의 untracked 파일 mode는 Git index mode와 동일하게 정규화된다.
- 승인되지 않은 등록 Target Validation은 내부 실행 실패 대신
  `APPROVED_EXECUTION_PLAN_BUNDLE_REQUIRED`로 fail-closed 응답한다.
- VS Code Learn·Validation 요청은 금지된 source 및 product-state 경로 override를 더 이상 전송하지 않는다.
- VS Code 14개 View는 하나의 캐시된 snapshot을 공유하며 다른 View와 동일 상태 목록을 반복하지 않는다.
- Hunk 승인 purpose는 published receipt schema와 동일한 `PATCH_HUNK_APPROVAL`을 사용한다.

### Compatibility

- Java namespace와 Maven coordinate 변경 없음
- 기존 `RagPreparationService`의 `ValidationReport` overload 유지
- 실제 ORUDA-Products 이동, main 병합, 배포, Production GO, Final PASS 없음

### Known migration blockers

- 공유 Java source root 4개 모듈과 `io.onsure.platform` split package
- package 경로 동결로 남아 있는 `platform ↔ platform.oruda` package cycle
- 라이선스·소유권·고객 데이터에 대한 사람의 승인 미실행
- 배포 runtime 및 DB migration 구성요소 미구현(설계 계약만 존재)
