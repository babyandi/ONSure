# ONSure 모노레포 이관 차단요인과 선행 작업

상태: `BLOCKED / PREPARATION_ONLY / NONFINAL`

## P0 — 실제 이관 전 필수

### 1. 기준 브랜치 수렴

- 조사 시 원격 `main` 외에 열린 Draft PR #27, #28과 원격에 없는 로컬 `main` 커밋이 존재했다.
- 실제 이관 Manifest는 어떤 변경선을 채택할지 결정된 하나의 immutable commit에서 다시 생성해야 한다.
- 선행 작업: PR 처리 방침, 포함/제외 commit, cutover SHA와 change freeze 승인.

### 2. Split package와 공유 source-set 제거

- `io.onsure.platform` source가 `onsure-core`, `onsure-cli`, `onsure-local-api`, `onsure-adapter-oruda` artifact에 Maven include/exclude로 분산된다.
- 모듈 POM들이 `../../src/main/java`를 공통 source root로 직접 추가한다.
- 이 상태로 모노레포 module graph에 넣으면 package ownership, JPMS, incremental build와 dependency visibility가 불명확하다.
- 선행 작업: 각 artifact 전용 source directory, 명시적 public SPI/API, 한 package당 단일 owner.

### 3. 패키지 의존 순환 제거

- 정적 import graph에서 `io.onsure.platform ↔ io.onsure.rag` 순환이 확인됐다.
- ORUDA adapter 영역은 `platform ↔ platform.oruda` 양방향 결합을 가진다.
- 선행 작업: Core-owned port를 정의하고 RAG/adapter가 port를 구현하도록 의존 역전. Core에서 ORUDA class import 0건을 물리적으로 검증.

### 4. 라이선스와 소유권 확정

- 루트 LICENSE/NOTICE/COPYING이 없고 VS Code package는 `UNLICENSED`다.
- GitHub repository owner나 filesystem owner는 저작권·재배포 권리의 증명이 아니다.
- 선행 작업: 코드·문서·fixture·third-party asset별 copyright owner, inbound license, outbound license, NOTICE 의무 승인.

### 5. Manifest 데이터 분류 승인

- 자동 검사는 고신뢰 secret pattern과 경로 heuristic만 확인한다.
- findings, fixture, 학습 자료가 합성 데이터인지 고객/운영 데이터인지 자동으로 확정할 수 없다.
- 선행 작업: data owner가 고객 데이터 0건, 운영 credential 0건, 보존/삭제 요건을 서명 확인. 확정 후 Manifest의 sensitivity 상태를 봉인.

### 6. Namespace 호환성 계획

- 현재 `io.onsure`는 유지한다. 미래 후보는 `kr.co.oruda.products.onsure`다.
- package 변경은 binary/source compatibility와 receipt/contract의 class-name 참조를 깨뜨릴 수 있다.
- 선행 작업: deprecation/bridge 기간, Maven coordinate 변경, serialization/reflection 영향, SDK 소비자 migration과 rollback 계획.

### 7. 기준선 회귀 계약 정합성

- `tests/test_onsure_autopilot.py`는 merge authority가 비어 있으면 항상 거부될 것을 요구하지만 `scripts/onsure-autopilot.py`는 terminal state가 `MERGE_AUTHORIZED_READY`일 때만 이를 검사한다.
- `tests/test_repository_contracts.py`의 구현 상태 기대값(`PARTIAL=8`, `STUB=5`, `DESIGN_ONLY=7`, 총 20)은 현재 권위 matrix의 값(`PARTIAL=22`, `STUB=2`, `DESIGN_ONLY=4`, 총 28)과 일치하지 않는다.
- 선행 작업: 테스트 기대값만 기계적으로 갱신하지 말고 terminal-state별 merge authority 계약과 28개 Capability matrix의 권위를 먼저 확정한 뒤 기준선 회귀를 복구한다.

## P1 — 모노레포 build 편입 전

### 8. 이중 build 권위 정리

- 루트 monolith `pom.xml`과 `pom-modular.xml`이 동시에 존재한다.
- 선행 작업: release artifact 권위 하나를 지정하고 다른 build는 compatibility gate로 제한. dependency lock/SBOM 추가.

### 9. 실행 구성요소 계약 확정

- API와 CLI는 있으나 standalone worker, browser web, DB migration은 없다.
- 선행 작업: 없는 구성요소를 빈 디렉터리로 “구현” 처리하지 말고 `product.yaml`에서 `NOT_PRESENT`로 선언. worker/DB가 실제 도입될 때 별도 ADR과 migration ownership 추가.

### 10. 배포 정의 부재

- Dockerfile, Compose, Helm, Kubernetes, deploy 디렉터리가 없다.
- 선행 작업: 지원 배포 모드, base image, runtime user, volume/network/secrets, air-gap, upgrade/rollback 정책 승인 후 작성.

### 11. Repo-root 가정 제거

- 스크립트는 Git root와 루트 상대경로를 기준으로 계약·상태·fixture를 찾는다.
- 현재 외부 workspace 절대경로는 없지만 `products/onsure/`로 들어가면 상위 모노레포 root와 제품 root가 달라진다.
- 선행 작업: `ONSURE_PRODUCT_ROOT` 또는 실행기 계산값 하나를 권위로 정하고, 모든 출력이 제품 root 밖으로 나가지 않는지 시험.

### 12. ORUDA 통합 경계 재정의

- ORUDA 코드는 외부 workspace를 직접 참조하지 않지만 ONSure 저장소 안에 optional adapter와 ORUDA 전용 receipt/materialization 구현이 존재한다.
- 읽기 전용 비교에서 다른 제품 source와 exact-content 복사는 0건이었다. 다만 ORUDA에도 `onsure_core/cause_aware_verification.py`와 대응 test가 같은 상대경로로 존재하고 digest는 달라 divergent copy 가능성이 있다.
- 선행 작업: adapter owner, shared-library 여부, 버전 호환표, 중복 구현 중 authoritative source를 결정.

### 13. Future root metadata 부재

- `product.yaml`, `CHANGELOG.md`, 제품 전용 `AGENTS.md`, `.obuilder/`가 없다.
- 선행 작업: ORUDA-Products schema와 상위 AGENTS를 먼저 확정하고, current repository에 임의 포맷을 선행 도입하지 않음.

## P2 — 실제 cutover 및 사후 검증

- immutable source commit에서 최종 Manifest 재생성 및 서명
- `products/onsure/`로 이동한 tree의 before/after digest 대응 검증
- Clean build 2회, unit/integration, package, 독립 checkout 실행
- 외부 workspace 절대경로 0건, 다른 제품 source 직접 참조 0건
- 비밀값·고객 운영 데이터 0건에 대한 자동 검사와 owner 확인
- 공개 API/CLI/receipt/schema 호환성 시험
- 모노레포 전체 build graph와 다른 제품에 대한 역방향 영향 시험
- rollback rehearsal

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
