# ONSure 배포·DB Migration 설계 v1

상태: `DESIGN_ONLY / PREPARATION_ONLY / NONFINAL`

## 1. 현재 판정

- 실행 가능한 배포 정의: `NOT_IMPLEMENTED`
- 배포 권한: `false`
- DB migration component: `NOT_PRESENT`
- DB engine과 migration tool: `NOT_SELECTED`
- GitHub Actions: 사용하지 않음
- Production/Commercial GO와 Final PASS: `false`

이 문서는 Dockerfile, Helm chart나 SQL migration을 추가하라는 지시가 아니다. 운영 topology와 영속성 요구가 승인되기 전에 실행 파일을 만들면 존재하지 않는 component를 구현 완료로 오인하게 된다.

## 2. 배포 후보 경계

초기 후보는 하나의 ONSure product root에서 Local API, CLI와 VS Code client를 제공하는 구조다. 외부 공개 network service는 승인하지 않는다.

필수 속성:

- non-root runtime identity
- application artifact read-only
- Local API loopback bind 기본값
- secret은 repository가 아닌 외부 provider에서 주입
- evidence용 writable volume과 application artifact 분리
- health, readiness, audit 상태를 서로 구분
- image/package version과 immutable source digest 결속
- 서명된 deployment receipt와 이전 immutable artifact rollback

container image와 orchestrator는 `NOT_SELECTED`다. 지원 운영환경과 air-gap 요구, base image 정책, SBOM/서명 형식, volume/network/secret 계약이 결정된 뒤 별도 ADR로 선택한다.

## 3. DB 도입 결정점

현재 file/evidence store가 존재한다는 사실은 관계형 DB migration component가 있다는 의미가 아니다. DB가 필요해지는 capability와 consistency/tenant/retention 요구를 먼저 확정한다.

도입 전 필수 결정:

1. DB engine ADR와 schema owner
2. tenant isolation과 retention/deletion/legal-hold 모델
3. forward-only 또는 compensating migration 정책
4. migration lock과 concurrent deploy 처리
5. backup/restore 및 disaster-recovery proof
6. 이전 application version과 rollback compatibility window
7. destructive DDL 승인 절차
8. 서명된 migration receipt와 source/schema digest

DB를 사용하지 않기로 결정하는 경우에도 `NO_DATABASE` ADR을 남기고 file/evidence store의 durability, concurrency, backup과 upgrade 계약을 명시해야 한다.

## 4. 단계별 구현 조건

### Phase A — 현재

- design contract와 validator만 유지
- runtime deployment와 DB migration은 `NOT_RUN`
- repository secret/customer operational data 금지

### Phase B — 운영환경 승인 후

- 선택된 topology의 최소 runtime package 작성
- non-root, read-only, loopback/deny-by-default network 시험
- install/upgrade/rollback fixture와 receipt schema 추가

### Phase C — DB 채택 승인 후

- 전용 migration component와 단일 schema owner 지정
- 테스트 전용 합성 database에서 forward/rollback/restore 검증
- application compatibility와 concurrent migration failure injection 추가

### Phase D — 독립 검증

- 독립 ONTester/ONAudit
- security/compliance/data owner 승인
- Production/Commercial GO는 별도 사람 권위로만 결정

## 5. 기계 검증

권위 후보:

- `contracts/onsure-operational-boundary.v1.json`
- `product.yaml`
- `.obuilder/product-build.yaml`

검증:

```bash
python3 scripts/validate_onsure_operational_boundary.py
```

validator는 배포 권한, premature tool 선택, public network, secret commit, destructive DDL, rollback 누락과 GitHub Actions 사용을 fail-closed로 거부한다.
