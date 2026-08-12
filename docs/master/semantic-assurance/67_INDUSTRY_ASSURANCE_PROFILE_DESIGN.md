# ONSure Industry Assurance Profile 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
금융·공공·의료 등 규제산업에서 공통 ONSure Assurance Semantics는 유지하되, 더 강한 Authority/Retention/Offline/Currentness/Certificate 요구를 profile로 적용한다.

## 2. 공통 원칙
Industry Profile은 Semantic Invariant를 약화하지 않는다. 산업별 차이는 stronger requirement, retention, approval, deployment/currentness, reporting에 한정한다.

## 3. Profile 후보
- GENERAL
- FINANCIAL_REGULATED
- PUBLIC_SECTOR
- HEALTHCARE_REGULATED
- HIGH_ASSURANCE_CUSTOM

## 4. Financial 후보 강화
- four-eyes 확대
- deployment/runtime currentness 필수
- audit/authority retention 강화
- external AI/provider policy stricter
- network/data residency 제한
- certificate/revocation SLA 강화
- model/RAG/provider drift revalidation stricter

## 5. Public Sector 후보 강화
- air-gapped/offline trust 지원
- signed offline bundle
- local evidence/source processing
- export/반출 통제
- long retention/official audit
- supplier/plugin provenance 강화

## 6. Healthcare 후보 강화
- personal/sensitive data class 강화
- purpose binding/least disclosure
- external provider 제한
- access audit/retention
- model/AI decision limitation disclosure

## 7. High Assurance Custom
고객 계약으로 더 강한:
- independent OTester/OAudit
- hidden benchmark
- multi-region currentness
- mandatory production binding
- external certificate verification
을 조합한다.

## 8. Profile Manifest
- industry_profile_id/version
- base policy profile
- mandatory rules
- non-overridable rules
- retention/data residency rules
- offline rules
- authority/SoD rules
- currentness/revalidation rules
- certificate profile
- supported assurance tiers

## 9. Conflict
Organization policy가 industry mandatory rule을 약화하면 activation HOLD. 더 강한 override만 허용한다.

## 10. Negative Test
- 금융 profile에서 single-person final approval
- 공공 air-gap profile인데 external AI call
- 의료 profile에서 source/raw PII가 public certificate에 포함
- tenant override로 mandatory retention/currentness 약화

## 11. 수용기준
산업별 차이는 versioned profile로 설명 가능하고 동일 ONSure 상태 온톨로지를 유지한다. 산업명만 붙이고 실제 control이 강화되지 않는 profile은 허용하지 않는다.
