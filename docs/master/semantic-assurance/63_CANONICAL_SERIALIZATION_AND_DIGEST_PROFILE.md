# ONSure Canonical Serialization·Digest Profile 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
동일 의미 object가 producer/언어/필드순서/숫자표현 차이로 다른 digest를 만들거나, 서로 다른 의미가 같은 축약 digest로 충돌하는 문제를 방지한다.

## 2. Canonicalization Profile
모든 authoritative digest object는 `canonicalization_profile_id/version`을 명시한다.

규칙 후보:
- UTF-8
- Unicode normalization profile 고정
- object key canonical sort
- array는 semantic order 여부를 schema별 명시
- null/absent 구분 정책
- integer/decimal representation 고정
- timestamp UTC canonical representation
- no insignificant whitespace
- unknown field handling 정책

## 3. Domain Separation
Digest preimage에 object type/version/profile을 포함해 서로 다른 object class 간 의미충돌을 막는다.
예: `ONSURE|FINAL_LOCK|v2|<canonical-bytes>`.

## 4. Self Digest
self_digest 필드는 digest 계산 preimage에서 제외하거나 명시적 placeholder rule을 사용한다. producer마다 다른 self-reference 처리 금지.

## 5. Population Digest
Set/Map 성격 population은 canonical key로 sort 후 item digest list를 결속한다. 순서가 의미인 list는 순서를 보존한다. count만 digest하지 않는다.

## 6. Merkle/Chunk
대형 population은 chunk/root를 허용하되:
- chunk membership
- chunk order/key range
- root construction profile
을 고정한다.

## 7. Signature Input
서명은 raw pretty JSON이 아니라 canonical bytes 또는 canonical object digest에 수행한다. signature metadata가 preimage에 포함되는 범위를 profile로 고정한다.

## 8. Migration
canonicalization profile 변경은 digest identity migration이다. old/new digest mapping receipt와 object semantic equality proof 없이 자동 치환하지 않는다.

## 9. Negative Test
- key 순서만 다른 동일 object digest 불일치
- array set order로 digest 변동
- null/absent가 서로 다른 의미인데 동일 digest
- self_digest 포함 재귀 계산
- pretty JSON bytes에 직접 signature
- old/new profile 혼재 cluster

## 10. 수용기준
동일 semantic object는 지원 언어/producer 간 동일 digest를 만들고, 모든 signed/linked authoritative object가 exact canonicalization profile을 공개한다.
