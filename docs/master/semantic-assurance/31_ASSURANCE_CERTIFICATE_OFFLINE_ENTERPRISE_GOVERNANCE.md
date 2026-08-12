# ONSure Assurance Certificate·Offline·Enterprise Governance 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `29_DEPLOYMENT_RUNTIME_CURRENTNESS_AND_REVOCATION_DESIGN.md`, `30_DISTRIBUTED_ASSURANCE_COMPOSITION_AND_EVIDENCE_GRAPH.md`

## 1. 목적
ONSure 내부 Receipt/FinalLock과 고객·감사자·발주기관이 소비하는 Assurance Certificate를 분리하고, 폐쇄망/Offline 및 Enterprise 권한위임·직무분리·긴급권한을 설계한다.

## 2. Certificate와 Internal Evidence 분리
Certificate는 내부 Evidence 전체 복사본이 아니다. Certificate는 subject/product/version, scope/requirement denominator, assurance level, currentness, independent verification, limitation/exclusion, validity 조건, revocation 확인 방법을 증명한다. 고객 source, secret, hidden corpus, exploit detail, raw prompt/민감 RAG corpus는 기본 공개하지 않는다.

## 3. AssuranceCertificate Entity
- certificate_id/version
- organization_id
- subject_id/digest
- product_version
- target_manifest_digest
- requirement_epoch/digest
- composition_snapshot_digest
- final_lock_digest
- assurance_level
- decision
- currentness_state
- independent_verification_summary
- issued_at/not_before/expires_at 또는 revalidation_due_at
- limitation_summary[]/exclusion_summary[]
- verifier_public_identity_ref
- revocation_endpoint/ref
- offline_revocation_snapshot_digest nullable
- key_id/signature

## 4. 상태
ISSUED_CURRENT|ISSUED_STALE|REASSESSMENT_REQUIRED|INVALIDATED|REVOKED|EXPIRED|SUPERSEDED|OFFLINE_STATUS_UNCERTAIN.
발급 당시 유효성과 현재 validity를 분리한다.

## 5. 발급 Gate
- exact product composition snapshot
- FinalLock
- required independent OTester/OAudit
- 필요한 경우 signed Human Acceptance
- current validator qualification
- current authority/key state
- certificate scope 내 unresolved P0 blocker 없음
- AL5 등 production-bound level은 Verified-to-Deployed-to-Running chain

없으면 Certificate 발급 금지.

## 6. Public Verification
외부 검증자는 내부 Evidence를 보지 않고 certificate signature, issuer/key chain, subject digest, issued/expiry, revocation/currentness snapshot, assurance level semantics/version, limitation/exclusion을 검증 가능해야 한다. QR에는 secret/token을 넣지 않는다.

## 7. Revocation
별도 signed RevocationReceipt를 생성한다.
- certificate_id
- reason_code
- affected_claims
- effective_at
- authority principal/key
- replacement_certificate_id nullable
- signature

Certificate bytes는 수정하지 않고 현재 revocation state와 결합해 검증한다.

## 8. Offline/Air-gapped Assurance
### Offline Trust Bundle
trusted root keys, key registry snapshot, policy snapshot, validator qualification snapshot, revocation snapshot, trusted-time evidence, bundle generation/expiry, bundle digest/signature를 포함한다.

### Trusted Time
로컬 OS clock 하나만 신뢰하지 않는다. TPM/secure clock/enterprise time authority를 우선하고 신뢰 가능한 시간이 없으면 freshness ceiling을 낮춘다.

### Offline Revocation Uncertainty
마지막 online sync 이후 시간이 길어질수록 uncertainty가 증가한다. 상태는 OFFLINE_CURRENT_WITHIN_GRACE|OFFLINE_REVALIDATION_DUE|OFFLINE_STATUS_UNCERTAIN|OFFLINE_BLOCKED를 사용한다.

### Reconnect Reconciliation
remote authority epoch sync → revocation sync → usage/credit reconciliation → offline receipt replay detection → certificate/currentness re-evaluation → conflict HOLD.

## 9. Enterprise Governance
Principal 범주: Customer Owner/Admin, Developer, Reviewer, Compliance Officer, Security Auditor, ONSure Operator, External Acceptor, Independent OTester/OAudit, Emergency Authority.

AuthorityGrant:
- grant_id/principal_id
- organization/tenant scope
- subject/project scope
- operation scope/purpose
- valid_from/until
- delegation_depth
- issued_by/approval chain
- revocation state

## 10. Delegation
- 원 권한보다 넓은 위임 금지
- delegation depth 제한
- Final authority delegation 별도 정책
- parent grant 만료/revoke 시 파생 grant 재평가
- 전체 delegation chain Receipt 보존

## 11. Four-eyes / SoD
Final Approval, Certificate issuance/revocation, Legal Hold, Emergency override, policy relaxation, hidden corpus access, accepted critical risk는 정책에 따라 서로 다른 principal/admin-owner의 2인 이상 승인을 요구한다. 같은 사람의 여러 계정/키는 2인으로 세지 않는다.

## 12. Break-glass
explicit reason, incident/ticket, short TTL, narrow scope, second-person approval 또는 mandatory post-review, enhanced audit, automatic expiry, affected assurance re-evaluation을 요구한다. Break-glass는 Final PASS를 생성하지 않는다.

## 13. Legal Hold
set/release authority 분리, exact case/evidence binding, legal basis, review/expiry date, immutable audit, retention conflict resolution을 요구한다. Legal Hold는 Certificate currentness를 연장하지 않는다.

## 14. Enterprise Audit Investigation
누가 어떤 권한으로 operation을 수행했는지, 당시 authority epoch, affected evidence/target/certificate, delegation/break-glass, later revocation/invalidation까지 재구성 가능해야 한다.

## 15. API 후보
- `/v2/assurance-certificates/*`
- `/v2/revocation-snapshots/current`
- `/v2/offline-trust-bundles`
- `/v2/offline-reconciliation`
- `/v2/authority-grants/*`
- `/v2/break-glass-sessions`
- `/v2/legal-holds`

## 16. Negative Test
expired/current 혼동, revoked offline reuse, stale revocation snapshot unlimited reuse, over-delegation, same principal multi-key four-eyes, break-glass Final PASS, Legal Hold freshness 연장, QR secret leakage, exclusion omission, revoked key certificate, External Acceptor raw evidence access.

## 17. 수용기준
- Certificate는 FinalLock과 분리된 signed public artifact다.
- 현재 validity는 발급 당시 decision과 별도 계산한다.
- Offline uncertainty를 숨기지 않는다.
- delegation/four-eyes/break-glass를 principal identity 수준에서 검증한다.
- Emergency workflow는 assurance strength를 올리지 않는다.
- revocation/currentness 조회 불가 시 fail-open하지 않는다.

## 18. 기존 산출물 적용 위치
- `01`: Certificate/Enterprise 상품가치
- `02`: Certificate/Offline/Governance 요구사항
- `03`: authority/delegation/certificate review
- `04`: Entity/API/Event
- `05`: Certificate/QR/Offline uncertainty UI
- `06`: revocation/offline/delegation adversarial tests
- `07`: AI reviewer/GT/hidden corpus authority governance
- `08`: Level/TTL/Offline grace/2인 승인 정책

## 19. 비최종 경계
현재 Certificate를 실제 발급하거나 외부에 ONSure 인증을 주장하지 않는다.
