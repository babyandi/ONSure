# ONSure Policy·Industry·Assurance Tier Baseline

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
정책값이 코드 상수로 흩어지거나 판매 상품등급이 기술적 Assurance 강도로 오인되는 것을 막는다. 모든 값은 AssurancePolicyProfile에 versioned policy로 들어가며 아래는 초기 안전 baseline 후보다.

## 2. 안전 불변식 — 절대 configurable 아님
- NOT_RUN/HOLD/BLOCKED/INCONCLUSIVE/UNKNOWN → PASS 승격 금지
- revoked/invalid signature/key → current authority 인정 금지
- Critical HARD dependency negative/unresolved → parent full PASS 금지
- self-validation → independent 승격 금지
- exact denominator/population commitment 없이 Final/Product Certificate 발급 금지
- break-glass/emergency operation으로 assurance strength 상승 금지
- offline uncertainty를 online currentness와 동일 표현 금지

## 3. 초기 configurable policy 후보
정확한 수치는 운영/산업 검증 후 조정하되 default는 fail-safe로 둔다.
- evidence freshness: artifact/config/policy 변경 이벤트 발생 시 TTL과 무관하게 즉시 stale 후보
- validator qualification: validator/oracle/adapter 변경 시 즉시 requalification required
- certificate: issue-time currentness snapshot 필수, revalidation_due_at 필수
- offline: revocation snapshot max-age 초과 시 `OFFLINE_STATUS_UNCERTAIN`
- four-eyes: Final Approval, Certificate issue/revoke, policy weakening, accepted Critical risk, break-glass, hidden corpus admin operation에 적용
- delegation depth: 최소 원칙 1단계 후보; 산업 profile에서 더 엄격하게 가능
- retry: semantic FAIL 이후 retry PASS는 `FLAKY/REASSESSED` history 유지
- AI statistical claim: confidence method/version, sample population, critical failure bound 없이는 strong claim 금지

## 4. Industry Profiles
### GENERAL_ENTERPRISE
- tenant isolation, evidence integrity, independent final gate, currentness, standard retention
- public certificate는 선택

### FINANCIAL_REGULATED
- stronger SoD/four-eyes
- on-prem/air-gapped 지원
- strict provider/model allowlist
- longer audit/evidence retention policy
- stronger currentness/revocation sync
- independent OTester/OAudit mandatory for high tier
- break-glass enhanced review mandatory

### PUBLIC_SECTOR
- data residency/on-prem/offline
- supply-chain provenance/SBOM/signature 강화
- named authority/delegation records
- audit export/legal hold
- public verification profile 분리

### HEALTHCARE_SENSITIVE
- sensitive data minimization/redaction
- training/RAG consent/provenance
- human acceptance/clinical-business authority separation
- stronger purpose limitation
- provider data-reuse prohibition enforcement

## 5. Assurance Tier
### AT0 UNASSESSED
실행/증거 없음. 고객 표시: 검증되지 않음.

### AT1 EXECUTED
정의된 검증이 실제 실행됐으나 evidence/independence/currentness strength 제한. 단일 self execution 가능.

### AT2 EVIDENCE_BOUND
Target/Scope/Requirement/Policy/Run에 결속된 Evidence와 Receipt가 존재하고 재구성 가능.

### AT3 INDEPENDENT
필수 Claim에 독립 OTester 등 요구 independent profile 충족.

### AT4 QUALIFIED
Validator/Oracle/Adapter/ONSure release qualification이 해당 target archetype/claim scope에서 current.

### AT5 PRODUCTION_BOUND_CURRENT
AT4 + verified artifact == deployed artifact + current runtime population binding + currentness=CURRENT + product composition closure.

## 6. Tier Ceiling
- unknown critical scope → <= AT1/AT2 policy ceiling
- evidence unbound → <= AT1
- independent missing → <= AT2
- qualification stale/not-proven → <= AT3 이하
- deployment/runtime binding 없음 → <= AT4
- currentness stale/reassessment required → historical tier는 유지 가능하나 current product claim에 AT5 표시 금지

## 7. 상품 Plan과의 관계
Web/Developer/Team/Enterprise는 판매·기능·용량 Plan이다. Assurance Tier는 실행된 증거로 계산한다. Enterprise 구매만으로 AT4/AT5를 부여하지 않는다. 낮은 Plan도 증거 조건을 만족하는 범위에서 기술 tier를 받을 수 있으나 제공 가능한 operation/independent service 범위는 entitlement에 의해 제한될 수 있다.

## 8. Profile 합성 우선순위
Global hard invariant > regulatory/industry floor > product/service profile > organization policy > case-specific stricter override.

하위 계층은 상위 safety floor를 약화시킬 수 없다. 충돌 시 stricter rule 또는 explicit HOLD를 사용한다.

## 9. 고객 Claim Language
- AT1: `실행된 검증 결과`
- AT2: `대상·범위에 결속된 증거 기반 결과`
- AT3: `독립 검증 포함`
- AT4: `자격이 확인된 검증 체계에서 평가`
- AT5: `검증된 대상과 현재 운영 배포가 결속된 상태에서 CURRENT`

절대 표현(완전히 안전/결함 없음/영구 보증)은 어떤 Tier에서도 금지한다.
