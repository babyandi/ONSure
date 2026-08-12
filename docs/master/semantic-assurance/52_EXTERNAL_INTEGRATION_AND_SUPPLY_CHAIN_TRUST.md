# ONSure External Integration·Supply Chain Trust 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
Git/CI/Payment/OLicense/AI Provider/Package Registry/Container Registry/Notification/External Acceptor 같은 외부 연동이 ONSure의 Final Assurance에 잘못된 권위를 제공하지 않도록 provenance·identity·freshness·reconciliation을 설계한다.

## 2. Integration Profile
각 외부 연동은:
- integration_id/type
- provider identity
- endpoint/region
- credential mode
- allowed operations
- data classes allowed
- trust basis
- signature/attestation capability
- freshness/replay policy
- failure/degraded policy
- qualification status
을 가진다.

## 3. Provider별 원칙
### Git/CI
commit/status 이름만 신뢰하지 않는다. repository binding, commit/tree digest, workflow identity, artifact provenance를 결속한다.

### Package/Container Registry
name:tag가 아니라 immutable artifact digest와 provenance/SBOM/signature를 우선한다.

### AI Provider
provider/model alias는 mutable identity다. model/deployment/profile/observed behavior generation과 정책을 추적한다.

### Payment/OLicense
결제 성공과 entitlement authority를 분리한다. Payment event만으로 검증/Final 권한을 열지 않는다.

### Notification/Webhook
전송 성공은 상대 시스템이 의미를 반영했다는 증명이 아니다. delivery receipt와 business acknowledgment가 필요하면 구분한다.

## 4. Supply Chain Provenance
ONSure 자체 build와 검증 대상 dependency에 대해 최소:
- source origin
- source/build digest
- builder identity
- build recipe
- dependency/SBOM
- signing/attestation
- timestamp
- revocation/advisory snapshot
을 추적한다.

## 5. External Attestation
외부 attestation은 claim scope와 issuer qualification을 확인한다. `signed=true`만으로 내용이 참이 되지 않는다.

## 6. Reconciliation
provider state와 ONSure local state가 다를 수 있다.
예:
- push receipt 있으나 remote commit 없음
- CI success webhook 있으나 해당 commit이 아님
- license ACTIVE cache이나 remote REVOKED
- container tag는 같지만 digest 변경

불일치는 `EXTERNAL_STATE_CONFLICT_HOLD`로 처리하고 자동 좋은 쪽 선택 금지.

## 7. Dependency Advisory Freshness
CVE/license/advisory 결과는:
- database/provider
- snapshot/generation
- checked_at
- artifact identity
에 결속한다. 조회 실패를 취약점 0으로 처리하지 않는다.

## 8. Credential Lifecycle
- short-lived credential 우선
- least scope
- rotation/revocation
- compromised credential impact window
- secrets not copied into evidence/log

## 9. Provider Qualification
Final에 material한 provider/adapter는 capability/availability/semantic fidelity qualification을 가질 수 있다. provider outage/degradation은 관련 claim ceiling에 반영한다.

## 10. Negative Test
- CI status가 다른 commit에서 온 것
- mutable container tag substitution
- revoked license의 stale local cache
- signed attestation issuer가 scope 권한 없음
- advisory lookup timeout을 0 vulnerability로 처리
- webhook replay
- package registry dependency digest drift
- AI model alias silent replacement

## 11. 수용기준
- 외부 연동 결과는 exact subject/context/freshness에 결속된다.
- 외부 시스템의 availability/claim을 ONSure assurance authority와 분리한다.
- mutable identifier만으로 artifact/model identity를 확정하지 않는다.
- reconciliation conflict는 fail-open하지 않는다.
