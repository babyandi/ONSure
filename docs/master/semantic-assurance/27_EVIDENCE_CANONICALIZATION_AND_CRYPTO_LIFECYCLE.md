# ONSure Evidence Canonicalization & Crypto Lifecycle 설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
Evidence/Receipt/Selector/Approval의 hash와 signature가 consumer마다 다른 canonicalization을 사용하거나 key lifecycle과 분리되면 동일 bytes/semantic claim을 안정적으로 검증할 수 없다. 본 문서는 canonical representation, digest, signature, key lifecycle, algorithm agility를 통합 정의한다.

## 2. Canonicalization Profile
기본 프로파일 후보: `ONSURE_CANONICAL_JSON_V2`.

필수 규칙:
- UTF-8
- Unicode NFC
- object key lexicographic order
- number normalization
- explicit null vs missing 구분
- line ending normalization은 text artifact에만 별도 profile
- timestamp RFC3339 normalization
- path separator normalization은 path identity profile에서만 적용

무조건 모든 artifact를 JSON으로 변환하지 않는다.

## 3. Artifact Type별 Canonicalization
- JSON: canonical JSON
- text: byte-exact + optional normalized-text secondary digest
- binary: byte-exact SHA-256
- ZIP/JAR: container bytes + normalized content manifest를 분리
- directory: sorted relative path + file digest Merkle-style manifest
- database snapshot: engine-specific logical/physical identity 분리

## 4. Self-hash Circularity
Receipt self-hash는 `receipt_sha256`와 signature field를 canonical payload에서 제외하고 계산한다. 어떤 필드를 제외하는지 profile version에 명시한다.

## 5. Signature Payload
Signature 대상은:
- canonical payload bytes
- canonicalization profile id/version
- contract family/version
을 포함한다.

Consumer가 다른 profile로 재계산할 수 없도록 한다.

## 6. Key Lifecycle
Key 상태:
- PROVISIONED
- ACTIVE
- ROTATION_PENDING
- RETIRED
- REVOKED
- COMPROMISED

Receipt는 signer key id뿐 아니라 key registry epoch와 effect-time validity를 결속한다.

## 7. Revocation Semantics
과거 서명이 cryptographically valid해도 current trust가 revoked/compromised면 current assurance를 재평가한다.

구분:
- SIGNATURE_VALID_AT_CREATION
- SIGNER_CURRENTLY_TRUSTED
- RECEIPT_CURRENTLY_USABLE

## 8. Key Rotation
Rotation 시:
- old/new key overlap policy
- activation time
- retiring key final signing time
- registry epoch
- historical verification support
을 기록한다.

Key rotation 때문에 과거 receipt bytes를 재서명하지 않는다.

## 9. Algorithm Agility
algorithm field를 contract에 고정하되 migration path를 설계한다.
- current: Ed25519 candidate
- future algorithm 추가 시 profile/version 증가
- old consumer가 unknown algorithm이면 fail-closed

## 10. Trusted Time
signature validity/expiry는 trusted time source에 의존한다. local wall clock만으로 authority expiry를 최종 판정하지 않는다.

## 11. Nonce / Replay
Approval/selector/human acceptance 같은 single-use authority는 nonce + consumption ledger를 사용한다.

Nonce가 derived receipt에서 사라지지 않도록 preservation rule을 적용한다.

## 12. Merkle/Bundle
대규모 evidence set은 exact member manifest + population digest를 사용한다. count와 aggregate hash만으로 member identity를 숨기지 않는다.

## 13. Crypto TCB
TCB:
- hash implementation
- signature implementation
- canonicalization library
- key parser
- KMS/keystore
- trusted time

변경 시 qualification stale 여부를 평가한다.

## 14. Negative Fixture
1. JSON key order variation
2. NFC/NFD variation
3. 1 vs 1.0
4. null vs missing
5. CRLF/LF text
6. ZIP metadata/order variation
7. signature field included in self-hash
8. revoked signer key
9. stale registry epoch
10. nonce replay
11. unknown algorithm
12. canonicalization version mismatch

## 15. API/Library
공용 Crypto/Canonicalization module은:
- canonicalize(type, profile)
- digest(...)
- sign(...)
- verify(...)
- validateKeyAtEffect(...)
- consumeNonce(...)
을 제공한다.

각 서비스가 자체 임의 hashing 규칙을 구현하지 않는다.

## 16. Claude 개발 경계
구현 순서:
1. canonical profile library
2. digest fixtures
3. signed receipt payload rules
4. key registry/effect-time verifier
5. nonce consumption ledger
6. algorithm/version negotiation

## 17. 현재 상태
- 설계: PRESENT
- 일부 LocalReceiptCrypto 구현: 존재
- canonical profile 전체 표준화: 미완료
- qualification/execution: NOT_RUN
