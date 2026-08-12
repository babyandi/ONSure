# ONSure Requirement Universe Materialization Handoff

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `88`, `89`, `90`

## 1. 목적
Claude가 전체 Requirement Universe를 실제 machine-readable registry로 materialize할 때 임의 해석하지 않도록 개발 단위를 고정한다.

## 2. 구현 순서
### RU-01 Authority Document Inventory
- docs/master authority population exact lock
- path, git_blob_sha, content_sha256

### RU-02 Explicit ID Extractor
- FR-COM-*
- FR-META-*
- NFR-*
- 기타 명시 식별자

### RU-03 Structured Requirement Extractor
ID 없는 Program 기능/수용기준/불변식을 source anchor와 함께 추출한다.
자동 추출 결과는 `CANDIDATE_EXTRACTED`로 두고 canonical authority로 자동 승격하지 않는다.

### RU-04 Semantic Normalizer
89번 tuple을 생성하고 duplicate/refine/conflict 후보를 만든다.

### RU-05 Human/Rule Resolution
명백한 structural duplicate는 rule로 해결할 수 있으나 P0 semantic conflict는 승인 없이 자동 병합 금지.

### RU-06 Universe Snapshot
exact requirement population과 digest 생성.

### RU-07 Global Trace Scanner
90번 closure scanner 실행.

## 3. 최소 Contract 후보
- `requirement-record.v2.schema.json`
- `requirement-universe-snapshot.v2.schema.json`
- `requirement-semantic-relation.v1.schema.json`
- `requirement-universe-generation-receipt.v1.schema.json`
- `global-trace-scan-report.v1.schema.json`

## 4. Negative Fixture
- 동일 문자열이지만 PASS/CURRENT 의미가 다른 요구를 duplicate로 병합
- ID 없는 Critical invariant를 누락
- retired requirement를 active denominator에 포함
- active requirement를 documentation-only라고 제외
- duplicate relation으로 마지막 mandatory requirement 제거
- source document digest가 바뀌었는데 epoch 유지
- FR-META만 universe로 사용하고 global=true 주장

## 5. 완료조건
- exact authority document population
- exact requirement population
- unresolved duplicate/conflict 명시
- global trace scan
- P0 orphan list
- raw extraction evidence

이 작업은 Design Lock을 자동 승인하지 않는다. 결과는 LockCheck의 입력이다.
