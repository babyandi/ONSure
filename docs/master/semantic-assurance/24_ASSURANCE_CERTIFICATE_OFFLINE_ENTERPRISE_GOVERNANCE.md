# ONSure Assurance Certificate·Offline·Enterprise Governance 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `22_DEPLOYMENT_RUNTIME_CURRENTNESS_AND_REVOCATION_DESIGN.md`, `23_DISTRIBUTED_ASSURANCE_COMPOSITION_AND_EVIDENCE_GRAPH.md`

## 1. 목적
ONSure 내부 Receipt/FinalLock과 고객·감사자·발주기관이 소비하는 Assurance Certificate를 분리하고, 폐쇄망/Offline 및 Enterprise 권한위임·직무분리·긴급권한을 설계한다.

## 2. Certificate와 Internal Evidence 분리
Certificate는 내부 Evidence 전체 복사본이 아니다.

Certificate가 증명하는 것:
- 어떤 subject/product/version을 검증했는가
- 어떤 scope/requirement denominator인가
- 어떤 assurance level인가
- currentness는 무엇인가
- independent verification이 있었는가
- 중요한 limitation/exclusion은 무엇인가
- 언제까지/어떤 조건에서 유효한가
- revoked/stale 여부를 어떻게 확인하는가

Certificate가 기본 공개하지 않는 것:
- 고객 source
- secret
- hidden corpus
- exploit detail
- 내부 reviewer 개인정보
- raw prompt/민감 RAG corpus

## 3. AssuranceCertificate Entity
- certificate_id
- certificate_version
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
- issued_at
- not_before
- expires_at 또는 revalidation_due_at
- limitation_summary[]
- exclusion_summary[]
- verifier_public_identity_ref
- revocation_endpoint/ref
- offline_revocation_snapshot_digest nullable
- key_id/signature

## 4. Certificate 상태
- ISSUED_CURRENT
- ISSUED_STALE
- REASSESSMENT_REQUIRED
- INVALIDATED
- REVOKED
- EXPIRED
- SUPERSEDED
- OFFLINE_STATUS_UNCERTAIN

Certificate의 최초 ISSUED 상태와 현재 validity를 분리한다. 과거에 정상 발급되었다는 사실은 삭제하지 않는다.

## 5. Certificate 발급 Gate
다음 없이는 발급 금지:
- exact product composition snapshot
- FinalLock
- required independent OTester/OAudit
- Human Acceptance가 필요한 상품/계약이면 signed acceptance
- current validator qualification
- current authority/key state
- no unresolved P0 blocker in certificate scope
- current deployment/runtime binding이 필요한 Assurance Level이면 Verified-to-Deployed-to-Running chain

## 6. Public Verification
외부 검증자는 고객 내부 Evidence를 보지 않고 다음을 검증 가능해야 한다.
- certificate signature
- issuer/key chain
- subject digest
- issued/expiry
- revocation/currentness snapshot
- assurance level semantics/version
- limitation/exclusion

QR은 verification locator 또는 compact signed payload를 포함할 수 있으나 secret/token을 포함하지 않는다.

## 7. Revocation
Revocation은 별도 signed RevocationReceipt를 생성한다.
- certificate_id
- reason_code
- affected_claims
- effective_at
- authority principal/key
- replacement_certificate_id nullable
- signature

Revocation은 certificate bytes를 수정하지 않는다. verifier는 certificate + current revocation state를 결합해 판단한다.

## 8. Offline/Air-gapped Assurance
### 8.1 Offline Trust Bundle
- trusted root keys
- key registry snapshot
- policy snapshot
- validator qualification snapshot
- revocation snapshot
- trusted-time evidence
- bundle generation/expiry
- bundle digest/signature

### 8.2 Trusted Time
로컬 OS clock 하나만 신뢰하지 않는다. 가능한 환경에서는 TPM/secure clock/enterprise time authority를 사용한다. 신뢰 가능한 시간이 없으면 freshness ceiling을 낮춘다.

### 8.3 Offline Revocation Uncertainty
마지막 online revocation sync 이후 시간이 길어질수록 uncertainty가 증가한다. Offline에서 `CURRENT`를 영구 유지하지 않는다.

상태 예:
- OFFLINE_CURRENT_WITHIN_GRACE
- OFFLINE_REVALIDATION_DUE
- OFFLINE_STATUS_UNCERTAIN
- OFFLINE_BLOCKED

### 8.4 Reconnect Reconciliation
재접속 시:
1. remote authority epoch sync
2. revocation sync
3. usage/credit reconciliation
4. offline receipt replay detection
5. certificate/currentness re-evaluation
6. conflict가 있으면 HOLD

Offline 결과가 online authority보다 자동 우선하지 않는다.

## 9. Enterprise Governance
### Principal 범주
- Customer Owner
- Customer Admin
- Developer
- Reviewer
- Compliance Officer
- Security Auditor
- ONSure Operator
- External Acceptor
- Independent OTester
- Independent OAudit
- Emergency Authority

### AuthorityGrant
- grant_id
- principal_id
- organization/tenant scope
- subject/project scope
- operation scope
- purpose
- valid_from/until
- delegation_depth
- issued_by
- approval chain
- revocation state

## 10. Delegation
- 권한 위임은 원 권한보다 넓을 수 없다.
- delegation depth 제한.
- final authority delegation은 별도 정책으로 제한.
- 위임 만료 시 파생 grant도 자동 재평가.
- delegation chain 전체가 receipt에 보존되어야 한다.

## 11. Four-eyes / SoD
고위험 operation:
- Final Approval
- Certificate issuance/revocation
- Legal Hold set/release
- Emergency override
- policy relaxation
- hidden corpus access
- accepted critical risk

정책에 따라 2인 이상 서로 다른 principal/admin-owner approval을 요구한다. 같은 사람이 여러 계정/키로 승인하는 것은 2인으로 세지 않는다.

## 12. Emergency / Break-glass
Break-glass는 권한 우회가 아니라 별도 고위험 workflow다.
필수:
- explicit reason
- incident/ticket
- short TTL
- narrow operation/subject scope
- second-person approval 또는 사후 mandatory review 정책
- enhanced audit
- automatic expiry
- 사용 후 affected assurance re-evaluation

Emergency override로 Final PASS를 생성할 수 없다. 필요한 경우 operation을 허용할 뿐 Assurance ceiling은 낮아진다.

## 13. Legal Hold
- set/release authority 분리
- 대상 evidence/case 범위 exact binding
- reason/legal basis
- expiry/review date
- immutable audit
- retention policy와 conflict resolution

Legal Hold가 evidence deletion을 막아도 Certificate currentness를 자동 연장하지 않는다.

## 14. Enterprise Audit Investigation
감사자는 다음을 재구성할 수 있어야 한다.
- 누가 어떤 권한으로 operation 수행
- 당시 authority epoch
- 어떤 evidence/target에 영향
- 어떤 certificate/final state가 생성/변경
- delegation/break-glass 사용 여부
- later revocation/invalidation 여부

## 15. API 후보
- `POST /v2/assurance-certificates`
- `GET /v2/assurance-certificates/{id}`
- `GET /v2/assurance-certificates/{id}/verify`
- `POST /v2/assurance-certificates/{id}/revoke`
- `GET /v2/revocation-snapshots/current`
- `POST /v2/offline-trust-bundles`
- `POST /v2/offline-reconciliation`
- `POST /v2/authority-grants`
- `POST /v2/authority-grants/{id}/revoke`
- `POST /v2/break-glass-sessions`
- `POST /v2/legal-holds`

## 16. Negative Test
- expired certificate를 CURRENT로 표시
- revoked certificate offline 재사용
- stale revocation snapshot으로 unlimited offline PASS
- delegated authority가 원 grant보다 넓음
- 동일 principal의 두 key를 four-eyes로 계산
- break-glass로 Final PASS 생성
- legal hold를 freshness 연장 근거로 사용
- QR에 secret 포함
- certificate에서 exclusion 누락
- revoked key로 certificate 발급
- external acceptor가 internal evidence raw access

## 17. 수용기준
- Certificate는 내부 FinalLock과 분리된 signed public artifact다.
- 현재 validity는 certificate 발급 당시 decision과 별도 계산된다.
- Offline uncertainty를 숨기지 않는다.
- delegation/four-eyes/break-glass가 principal identity 수준에서 검증된다.
- Emergency workflow는 assurance strength를 올리지 않는다.
- revocation/currentness 조회가 불가능하면 verifier는 fail-open하지 않는다.

## 18. 기존 산출물 적용 위치
- `01`: Certificate/Enterprise 상품가치·유료 기능
- `02`: Certificate/Offline/Governance 기능요구
- `03`: authority/delegation/certificate review
- `04`: Entity/API/Event
- `05`: 고객 Certificate/QR/Offline uncertainty UI
- `06`: revocation/offline/delegation/break-glass adversarial tests
- `07`: AI reviewer/GT/hidden corpus authority governance
- `08`: Certificate Level/TTL/Offline grace/2인 승인 정책 결정

## 19. 비최종 경계
현재 Certificate를 실제 발급하거나 외부에 ONSure 인증을 주장하지 않는다. Contract/Runtime/independent qualification 이후 별도 activation한다.
