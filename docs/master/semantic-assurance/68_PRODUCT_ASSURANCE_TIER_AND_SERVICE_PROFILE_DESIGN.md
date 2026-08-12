# ONSure Product Assurance Tier·Service Profile 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
Developer/Team/Enterprise 같은 판매 Plan과 실제 Assurance 강도를 분리하고, 고객이 어떤 검증 수준을 구매/요청했는지 명확히 표현한다.

## 2. 두 축 분리
- Commercial Plan: 가격/사용량/지원/seat/기능 entitlement
- Assurance Tier: 검증 강도/독립성/배포결속/currentness/certificate 요구

Enterprise Plan이라고 자동으로 최고 Assurance가 되지 않으며 Developer Plan도 특정 Case에서 높은 Assurance Tier를 구매할 수 있다.

## 3. Tier 후보
### AT0 DISCOVERY_ONLY
Program understanding/scope discovery 중심. Final quality claim 없음.

### AT1 REVIEWED
정적/설계/정책 Review 중심. 실행 검증 부재를 명시.

### AT2 EXECUTION_VERIFIED
실제 build/test/scenario evidence를 포함. self-validation ceiling.

### AT3 INDEPENDENTLY_VERIFIED
독립 OTester 또는 정책이 요구하는 독립 검증 충족.

### AT4 AUDITED_HIGH_ASSURANCE
독립 OTester + OAudit + qualification/hidden/adversarial requirement 강화.

### AT5 PRODUCTION_BOUND_CURRENT
AT4 기반 + Verified-to-Deployed-to-Running + Currentness + Product Composition + Certificate.

## 4. Tier Manifest
- tier_id/version
- minimum assurance strength
- required validation lanes
- independent gate requirements
- qualification requirements
- adversarial/hidden benchmark requirements
- deployment/currentness binding
- product composition requirement
- certificate requirement
- allowed limitations
- required revalidation triggers

## 5. Claim Language
각 Tier별 고객 문구 템플릿을 제한한다.
- AT1: “정적·설계 검토 범위에서…”
- AT2: “지정된 실행 시나리오와 증거 범위에서…”
- AT3+: 독립 검증 주체/범위 표시
- AT5: 특정 deployment/runtime currentness 시점을 포함

절대적 “안전함/결함 없음” 표현은 Tier와 무관하게 금지한다.

## 6. Upgrade/Downgrade
Tier upgrade는 부족한 lane을 추가 수행해야 한다. 과거 AT2 PASS를 문서 라벨만 바꿔 AT4로 승격 금지.
Tier downgrade는 historical object를 수정하지 않고 새 delivery/certificate profile에서 낮은 claim scope로 표현한다.

## 7. Product Plan Mapping
Commercial Plan은 지원 가능한 Tier의 상한/옵션을 정의할 수 있으나 실제 발급 Tier는 해당 Case의 Evidence/Independent/Qualification 상태로 결정한다.

## 8. Negative Test
- Enterprise Plan을 이유로 AT5 자동 표시
- AT2 self-validation receipt를 AT3 independent로 재라벨
- 배포 결속 없이 AT5 certificate
- 동일 Case에서 UI는 AT4, certificate는 AT5

## 9. 수용기준
가격/상품 등급과 기술적 Assurance 강도가 machine/UI/Certificate에서 분리되고, 실제 발급 Tier는 현재 증거/독립성/qualification/currentness ceiling을 넘지 않는다.
