# ONSure Deployment·Certificate·Authority·Policy·Industry·Tier 최종 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Covers: Task 22~27

## 1. Deployment Identity
Chain:
`BuildArtifactIdentity -> DeploymentRevision -> RuntimeInstancePopulation`.

필드:
- build artifact digest/provenance/SBOM
- environment/cluster/region/namespace
- desired vs observed artifact digest
- deployment strategy/generation
- runtime population digest
- traffic cohort digest
- config/dependency/model/prompt/RAG runtime digest

Rolling/Blue-Green/Canary는 별도 transition state와 cohort currentness를 가진다.

## 2. Certificate Protocol
Certificate machine object와 PDF/HTML/QR 표현을 분리한다.
Verification mode:
- ONLINE_CURRENT
- OFFLINE_BUNDLE_BOUND
- HISTORICAL_SIGNATURE_ONLY

검증은 signature만 보지 않고 subject binding, FinalLock, composition, currentness, qualification, revocation, limitation/exclusion을 함께 확인한다.

## 3. Authority Governance
AuthorityGrant:
- principal/admin-owner
- tenant/subject/operation/purpose scope
- valid window
- delegation depth/parent grant
- four-eyes requirement
- break-glass flag

위임은 원 grant보다 넓을 수 없다. Final Approval/Certificate revoke/Policy weakening은 policy에 따라 서로 다른 principal/admin-owner를 요구한다.

## 4. Policy Profile
Precedence:
`global safe baseline -> product profile -> industry profile -> customer contract override`.

Override는 상위 safe minimum보다 약화될 경우 `WEAKENING_CHANGE`로 별도 승인/impact scan을 요구한다.

수치형 값(TTL/sample/offline grace)은 versioned policy input이며 source code constant가 authority가 아니다.

## 5. Industry Profile
기본 archetype:
- GENERAL_ENTERPRISE
- FINANCIAL_REGULATED
- PUBLIC_SECTOR
- HEALTHCARE_SENSITIVE

각 Profile은 mandatory controls, retention, independent verification, offline restrictions, certificate disclosure minimum을 가진다.

## 6. Assurance Tier
Candidate:
- AT0 UNASSESSED
- AT1 EXECUTED
- AT2 EVIDENCE_BOUND
- AT3 INDEPENDENTLY_VERIFIED
- AT4 QUALIFIED
- AT5 PRODUCTION_BOUND_CURRENT

Plan/가격제와 Tier는 분리한다. Enterprise Plan 구매가 AT5를 보장하지 않는다.

Downgrade trigger:
- currentness stale
- qualification expired
- required independent receipt revoke
- deployment/runtime mismatch
- critical requirement added

## 7. Acceptance
- certificate가 현재 validity를 동적으로 검증
- authority weakening/break-glass가 strength를 올리지 않음
- industry/customer override provenance 존재
- AT5는 verified→deployed→running currentness 없이는 발급 불가
