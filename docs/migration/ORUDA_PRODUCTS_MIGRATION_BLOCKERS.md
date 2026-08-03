# ONSure 모노레포 이관 차단요인과 선행 작업

상태: `BLOCKED / PREPARATION_ONLY / NONFINAL`

## P0 — 실제 이관 전 필수

### 1. 기준 브랜치 수렴

- 조사 시 원격 `main` 외에 열린 Draft PR #27, #28과 원격에 없는 로컬 `main` 커밋이 존재했다.
- 실제 이관 Manifest는 어떤 변경선을 채택할지 결정된 하나의 immutable commit에서 다시 생성해야 한다.
- 선행 작업: PR 처리 방침, 포함/제외 commit, cutover SHA와 change freeze 승인.
- #27과 #28은 공통 merge base가 원격 main이고 어느 한쪽도 다른 쪽의 ancestor가 아닌 `DIVERGED` 상태였다. #27 기준 #28은 ahead 46/behind 265 commit이었다.
- 겹치는 86개 파일 중 63개는 tip 내용이 동일했다. 차이가 있는 23개는 #28의 후속 gate·authority·Java 수정이 더 최신이며, #27 전용 파일 3개는 호출되지 않는 구버전 검증기였다.
- 통합 순서는 #28을 권위 변경선으로 채택하고 #27을 #28에 의해 대체된 변경선으로 분류했다. #29 이관 준비 변경은 #28 위에서 충돌을 해소했다.
- #27, #28, #29는 통합 근거와 #30 링크를 남긴 뒤 병합 없이 닫았다. 각 head commit과 브랜치는 #30 history에 보존된다.
- 현재 overlap 판정은 `INTEGRATION_ORDER_RESOLVED`다. 이는 자동 main 병합 허용이나 #27/#28/#29의 Draft 상태 해제를 의미하지 않는다.

### 2. 공유 source-set 물리 분리

- `io.onsure.platform` split package는 제거되어 core가 단독 소유하고 CLI·Local API는 고유 package main entrypoint만 소유한다.
- `platform ↔ platform.oruda` cycle도 target-neutral evidence SPI로 제거됐으며 artifact/package cycle은 모두 0건이다.
- 다만 `onsure-core`와 `onsure-adapter-oruda`가 같은 `../../src/main/java`를 배타적 include/exclude로 읽는 공유 source module 2개는 남아 있다.
- 현재 Java 파일 경로 동결을 유지하므로 이를 adapter 전용 source directory로 실제 이동하지 않았다.
- 선행 작업: cutover 승인 후 두 artifact의 전용 source directory 이동, canonical/API/modular/ORUDA E2E와 Manifest digest 재검증, shared source module count 2→0.

### 3. 패키지 의존 순환 제거 — 완료된 준비 항목

- `FileValidationStore`의 RAG 직접 호출을 `io.onsure.common.RagCandidatePreparer`로 치환해 `platform → rag` import를 0건으로 유지한다.
- 기존 공개 API 호환을 위한 `rag → platform` 단방향 compile edge만 유지한다.
- ORUDA evidence persistence는 `TargetEvidenceContributor` SPI와 ServiceLoader provider로 역전했다.
- Maven artifact cycle, split package, `platform ↔ platform.oruda` mutual package cycle은 각각 0건이다.
- 이 완료는 shared source root 물리 이동이나 실제 모노레포 cutover 완료를 뜻하지 않는다.

### 4. 라이선스와 소유권 확정

- 루트 LICENSE/NOTICE/COPYING이 없고 VS Code package는 `UNLICENSED`다.
- GitHub repository owner나 filesystem owner는 저작권·재배포 권리의 증명이 아니다.
- 선행 작업: 코드·문서·fixture·third-party asset별 copyright owner, inbound license, outbound license, NOTICE 의무 승인.
- CycloneDX 1.6 SBOM 기준 runtime dependency 4개는 Apache-2.0을 선언하며 dependency 미선언 license는 0건이다. 이는 root source license 부재를 해소하지 않는다.

### 5. Manifest 데이터 분류 승인

- 자동 검사는 고신뢰 secret pattern과 경로 heuristic만 확인한다.
- findings, fixture, 학습 자료가 합성 데이터인지 고객/운영 데이터인지 자동으로 확정할 수 없다.
- 선행 작업: data owner가 고객 데이터 0건, 운영 credential 0건, 보존/삭제 요건을 서명 확인. 확정 후 Manifest의 sensitivity 상태를 봉인.

### 6. Namespace 호환성 계획

- 현재 `io.onsure`는 유지한다. 미래 후보는 `kr.co.oruda.products.onsure`다.
- package 변경은 binary/source compatibility와 receipt/contract의 class-name 참조를 깨뜨릴 수 있다.
- 선행 작업: deprecation/bridge 기간, Maven coordinate 변경, serialization/reflection 영향, SDK 소비자 migration과 rollback 계획.

## P1 — 모노레포 build 편입 전

### 7. 이중 build 권위 정리

- `pom.xml`을 독립 release 후보 검증 권위로, `pom-modular.xml`을 미래 분해 compatibility gate로 지정했다.
- `contracts/onsure-build-boundary.v1.json`, `product.yaml`, `.obuilder/product-build.yaml` 간 드리프트를 자동 검증한다.
- 별도 source를 가진 Provider SPI, local/mock/OpenAI 구현, PostgreSQL migration과 Public SDK 후보를 compatibility build에 추가했고 artifact graph cycle은 0건이다.
- 남은 선행 작업: SPI/SDK versioning, 실제 OpenAI request 검증과 모노레포 build owner 승인.

### 8. 실행 구성요소 계약 확정

- API와 CLI, PostgreSQL/Flyway migration은 있으나 standalone worker와 browser web은 없다.
- 선행 작업: worker/web은 `NOT_PRESENT`를 유지한다. 임시 PostgreSQL 16.14의 apply/idempotency/validate/dump/restore는 통과했지만 RHEL PostgreSQL lock 경쟁·운영 backup/restore·호환성 검증과 승인 전 비최종 후보로만 취급한다.

### 9. RHEL 운영환경 실행 검증 미완료

- RHEL 계열 단독 서버와 systemd, loopback PostgreSQL/Flyway, external secret, OpenAI HTTPS egress 후보를 구현했다. 이전 Docker/Compose는 선택되지 않은 합성 시험 자료다.
- validator는 immutable package, migration authorization, rollback 요구와 실제 배포 권한 거부를 유지한다.
- 선행 작업: 정확한 RHEL/PostgreSQL 지원 버전, SELinux/firewall, RHEL PostgreSQL lock·backup/restore, systemd start/stop, OpenAI 실호출과 upgrade/rollback 승인 시험.

### 10. Repo-root 가정 제거

- Manifest, migration readiness, build boundary, product metadata 스크립트는 `ONSURE_PRODUCT_ROOT` 또는 스크립트 위치 기준 제품 root를 사용한다.
- 절대경로 override, marker 확인, 제품 root 밖 path escape 거부를 테스트한다.
- 기존 실행·gate 스크립트 일부는 여전히 Git root와 루트 상대경로를 기준으로 계약·상태·fixture를 찾는다.
- 현재 외부 workspace 절대경로는 없지만 `products/onsure/`로 들어가면 상위 모노레포 root와 제품 root가 달라진다.
- 선행 작업: 남은 실행 스크립트를 같은 resolver 계약으로 단계적 전환하고 모든 출력을 시험.

### 11. ORUDA 통합 경계 재정의

- ORUDA 코드는 외부 workspace를 직접 참조하지 않지만 ONSure 저장소 안에 optional adapter와 ORUDA 전용 receipt/materialization 구현이 존재한다.
- 읽기 전용 비교에서 다른 제품 source와 exact-content 복사는 0건이었다. 다만 ORUDA에도 `onsure_core/cause_aware_verification.py`와 대응 test가 같은 상대경로로 존재하고 digest는 달라 divergent copy 가능성이 있다.
- 선행 작업: adapter owner, shared-library 여부, 버전 호환표, 중복 구현 중 authoritative source를 결정.

### 12. Future root metadata 부재

- `product.yaml`, `CHANGELOG.md`, 제품 전용 `AGENTS.md`, `.obuilder/product-build.yaml` 비최종 후보를 추가했다.
- 후보는 독립 build 권위, 실제 구성요소 상태와 금지된 release 권한을 fail-closed로 검증한다.
- 선행 작업: ORUDA-Products 최종 schema와 상위 AGENTS 확정 후 후보 변환·검증. 현재 후보를 최종 schema로 주장하지 않음.

## P2 — 실제 cutover 및 사후 검증

- immutable source commit에서 최종 Manifest 재생성 및 서명
- `products/onsure/`로 이동한 tree의 before/after digest 대응 검증
- Clean build 2회, unit/integration, package, 독립 checkout 실행
- 외부 workspace 절대경로 0건, 다른 제품 source 직접 참조 0건
- 비밀값·고객 운영 데이터 0건에 대한 자동 검사와 owner 확인
- 공개 API/CLI/receipt/schema 호환성 시험
- 모노레포 전체 build graph와 다른 제품에 대한 역방향 영향 시험
- rollback rehearsal

격리된 임시 Git root의 `products/onsure/`에 대한 digest cutover·rollback 및 full build 리허설은 준비 gate로 제공한다. 실제 ORUDA-Products 수정이나 cutover 승인을 의미하지 않는다.

## 이번 준비 작업에서 실행 금지/미실행

| 항목 | 상태 |
|---|---|
| ORUDA-Products 실제 파일 이동/병합 | `NOT_RUN` |
| Java package/namespace 변경 | `NOT_RUN` |
| Maven 공개 coordinate 변경 | `NOT_RUN` |
| 다른 제품 저장소 수정 | `NOT_RUN` |
| main 병합 | `NOT_RUN` |
| 배포 | `NOT_RUN` |
| Production GO / Final PASS | `NOT_RUN / PROHIBITED` |
