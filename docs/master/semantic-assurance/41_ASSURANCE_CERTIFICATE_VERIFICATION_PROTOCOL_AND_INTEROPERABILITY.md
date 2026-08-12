# ONSure Assurance Certificate Verification Protocol·Interoperability 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `31`, `37`, `38`, `39`, `40`

## 1. 목적
ONSure Assurance Certificate를 단순 PDF/보고서가 아니라 고객·감사자·발주기관·자동화 시스템이 동일하게 검증할 수 있는 **서명된 검증 프로토콜**로 정의한다. 내부 Evidence 전체를 노출하지 않으면서 subject, scope, assurance strength, currentness, limitation, revocation을 검증 가능하게 한다.

## 2. Certificate Artifacts
한 Certificate 발급은 최소 세 표현을 가질 수 있다.
1. Canonical machine object(JSON/CBOR 후보)
2. Human-readable PDF/HTML projection
3. Compact verification reference(QR/URI/payload)

Authority는 canonical machine object다. PDF/HTML/QR은 canonical digest를 참조하는 projection이며 내용 불일치 시 machine object를 기준으로 FAIL/HOLD한다.

## 3. Canonical Certificate Profile
필수 semantic group:
- issuer identity
- subject identity
- evaluated product/version/target
- requirement/scope denominator
- CompositionSnapshot
- FinalLock
- Decision
- Assurance Strength
- Currentness at issue
- independent verification summary
- ONSure release qualification generation
- material limitations/exclusions
- issue/not-before/expiry/revalidation due
- revocation registry locator/profile
- signature/key chain

Certificate profile version을 명시하고 parser가 unknown critical field/profile을 무시하지 않도록 fail-close policy를 둔다.

## 4. Public vs Restricted Projection
### Public Minimum
- certificate_id/version
- issuer
- subject/product identifier 또는 privacy-safe digest
- decision/strength/currentness summary
- issued/expiry/revalidation due
- revocation state/verification status
- material limitation indicator
- verifier profile version

### Restricted Customer View
- detailed target/scope/requirement
- exclusion rationale
- independent verifier summary
- deployment/currentness detail
- affected finding category summary

### Internal
- raw evidence/finding/source/hidden benchmark/principal details

Projection level은 Certificate truth를 변경하지 않는다.

## 5. Verification Modes
### ONLINE_CURRENT
현재 issuer key/revocation/currentness/qualification을 online 조회한다. 가장 강한 current verification.

### OFFLINE_BUNDLE_BOUND
signed Offline Trust Bundle의 key/revocation/qualification/time snapshot 범위에서 검증한다. 결과에 `offline_snapshot_age`와 uncertainty를 포함한다.

### HISTORICAL_SIGNATURE_ONLY
발급 당시 signature/subject binding만 확인. 현재 유효성 보장은 아님. UI/API에서 CURRENT verification과 명확히 분리한다.

## 6. Verification Algorithm
1. parse profile/version
2. canonicalization profile resolve
3. certificate self/content digest verify
4. signature/key chain verify
5. issuer authority scope verify
6. subject binding verify
7. time/not-before/expiry verify
8. revocation state fetch/verify 또는 offline snapshot assess
9. referenced FinalLock/CompositionSnapshot binding verify
10. ONSure qualification generation validity assess
11. currentness generation assess
12. material limitation/exclusion integrity verify
13. verification result 생성

## 7. Verification Result
- verification_id
- certificate_id/digest
- mode
- signature_status
- issuer_authority_status
- subject_binding_status
- time_status
- revocation_status
- currentness_status
- qualification_status
- limitation_integrity_status
- overall_result: VALID_CURRENT|VALID_HISTORICAL_ONLY|VALID_OFFLINE_WITH_UNCERTAINTY|STALE|REASSESSMENT_REQUIRED|INVALID|REVOKED|EXPIRED|UNKNOWN
- reasons[]
- verified_at
- verifier_profile_digest

`signature_status=VALID`만으로 overall VALID_CURRENT 금지.

## 8. QR / Compact Reference
QR payload 후보:
- certificate id
- canonical digest
- issuer/verifier endpoint locator
- profile version
- optional compact signature

금지:
- access token
- secret
- raw evidence
- customer source path
- sensitive Finding detail

QR scanning만으로 권한이 필요한 내부 정보에 접근하게 하지 않는다.

## 9. Revocation Registry Protocol
Revocation lookup은 certificate_id/digest를 받아:
- status
- effective_at
- reason class(public-safe)
- replacement certificate ref nullable
- registry generation
- response signature
을 반환한다.

Revocation endpoint가 unavailable일 때 ONLINE_CURRENT를 fail-open하지 않는다. policy에 따라 UNKNOWN/STATUS_UNCERTAIN.

## 10. Currentness Registry
Certificate validity는 revocation 외에도 STALE/REASSESSMENT_REQUIRED가 존재한다. 별도 currentness registry 또는 verification endpoint가:
- latest currentness generation
- assessed_at
- trigger summary
- required action
- signature
를 반환한다.

## 11. Privacy-preserving Subject Identity
공개 Certificate에 내부 repo/path/customer name을 노출하지 않기 위해:
- public subject identifier
- customer-verifiable private mapping
- canonical subject digest
를 분리할 수 있다.

공개 digest가 dictionary attack에 취약한 low-entropy identifier라면 salted commitment 또는 opaque public id를 고려한다. 정확한 방식은 privacy/security review 후 확정.

## 12. Interoperability Profile
ONSure 외부 시스템이 소비할 수 있도록 최소 machine fields와 semantic vocabulary를 versioned profile로 공개한다. 특정 외부 표준 포맷을 지금 확정하지 않으며, 필요 시 mapping adapter를 제공한다.

Mapping 시 금지:
- HOLD→PASS
- STALE→VALID
- strength/currentness 축 삭제로 단순 PASS 생성
- limitation/exclusion 누락

Lossy mapping은 `SEMANTIC_LOSS_WARNING`과 원 canonical certificate reference를 포함한다.

## 13. Certificate Supersession
새 Certificate 발급이 이전 것을 자동 revoke하지는 않는다.
관계:
- SUPERSEDES
- REPLACES_AFTER_REVALIDATION
- REISSUED_FORMAT_ONLY
- SCOPE_CHANGED

semantic scope가 달라지면 단순 version increment가 아니라 새 subject/scope binding을 가진다.

## 14. Key Rotation
issuer key rotation 시:
- old Certificate historical signature verification 지원
- new issue는 current key 사용
- key compromise와 normal rotation 분리
- verifier는 certificate signing time과 key validity/effect policy를 함께 평가

## 15. Certificate Transparency 후보
Enterprise/high assurance에서 발급/폐기 이벤트를 append-only transparency ledger에 commitment하는 옵션을 검토한다.
목적:
- silent issuance 방지
- later deletion/rewriting 탐지
- external audit

고객 confidential metadata는 public ledger에 직접 넣지 않고 certificate digest/opaque id만 commitment할 수 있다.

## 16. API 후보
- `GET /v2/certificates/{id}`
- `POST /v2/certificates/{id}/verify`
- `GET /v2/certificates/{id}/public`
- `GET /v2/certificates/{id}/currentness`
- `GET /v2/certificates/{id}/revocation`
- `GET /v2/certificate-profiles/{version}`
- `GET /v2/issuer-keys/current`

Public endpoints는 rate limiting/abuse protection을 적용하지만 인증이 없다는 이유로 sensitive data를 반환하지 않는다.

## 17. Negative Test
- PDF PASS but canonical object STALE
- valid signature + wrong subject
- valid signature + revoked certificate
- expired certificate + CURRENT API bug
- unsupported profile critical field ignored
- QR token leakage
- stale offline bundle presented as online current
- limitation removed in human projection
- key rotation breaks historical verification
- compromised key effective time ignored
- lossy external mapping converts HOLD to PASS

## 18. 수용기준
- machine/human/QR projection이 동일 canonical digest에 결속.
- current verification은 signature-only verification과 분리.
- revocation/currentness endpoint unavailable을 positive CURRENT로 처리하지 않음.
- limitation/exclusion integrity를 검증.
- public verifier가 source/raw evidence를 노출하지 않음.
- profile version unknown/unsupported 시 의미손실 없이 fail-close 또는 explicit unsupported.

## 19. 비최종 경계
이 문서는 ONSure 인증제도의 법적/규제적 효력을 주장하지 않는다. 실제 Certificate 발급은 Contract/crypto/public verification/independent qualification 이후 별도 activation한다.
