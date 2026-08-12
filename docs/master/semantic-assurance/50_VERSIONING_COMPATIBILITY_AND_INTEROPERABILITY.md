# ONSure Versioning·Compatibility·Interoperability 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
Schema/Policy/Plugin/Adapter/API/Certificate/Validator 버전이 공존할 때 consumer마다 다른 의미를 해석하거나 구버전 object를 신버전 PASS로 자동 승격하는 것을 방지한다.

## 2. Version 축
- API version
- schema version
- semantic contract version
- policy profile version
- operation version
- plugin/adapter version
- validator build/version
- certificate profile version
- evidence canonicalization profile

파일 이름의 v2와 semantic authority activation은 별개다.

## 3. Compatibility Class
- EXACT
- BACKWARD_READ_COMPATIBLE
- FORWARD_TOLERANT_NONAUTHORITATIVE
- MIGRATION_REQUIRED
- REPERFORMANCE_REQUIRED
- INCOMPATIBLE
- UNKNOWN_HOLD

## 4. Semantic Version Rule
필드 추가가 backward-compatible여도 assurance 의미가 강화되면 semantic major change다. 예: independence/freshness/authority 필드 추가는 단순 optional field upgrade로 취급하지 않는다.

## 5. Reader/Writer 정책
- writer는 active selector가 지정한 canonical version만 authoritative object로 생성
- reader는 legacy를 읽을 수 있으나 missing strong semantics를 추정하지 않음
- unknown enum/status는 safe default PASS가 아니라 UNKNOWN/HOLD

## 6. Migration Receipt
각 migration은:
- source contract/version
- destination contract/version
- source object digest
- transformed digest
- mapping class per critical field
- lost semantics[]
- readback/reperformance requirements
- decision ceiling
을 기록한다.

## 7. API Deprecation
API 제거 전:
- usage inventory
- dependent client/plugin list
- replacement path
- dual-read/dual-write 필요 여부
- cutover selector
- rollback window
을 명시한다.

## 8. Certificate Interoperability
Public Certificate는 profile/version을 명시한다. verifier가 모르는 profile이면 signature만 확인하고 Assurance CURRENT를 주장하지 않는다.

## 9. Plugin Compatibility
plugin manifest의 supported core/API/target versions와 실제 runtime을 대조한다. range match만으로 semantic compatibility를 확정하지 않고 qualification generation을 확인한다.

## 10. Mixed-version Cluster
동일 authoritative operation을 처리하는 cluster는 active semantic contract/policy/canonicalization generation이 일치해야 한다. 다르면 strong write를 중단하거나 routing isolation한다.

## 11. Negative Test
- v1 PASS receipt를 v2 PASS로 자동 upgrade
- unknown status를 PASS로 deserialize
- old node가 new canonicalization과 다른 digest 생성
- certificate verifier가 unknown profile을 CURRENT로 표시
- plugin range는 맞지만 qualification이 old core generation
- rolling deploy 중 old/new node가 서로 다른 Final 판단

## 12. 수용기준
- 모든 authoritative artifact가 semantic version/profile을 명시한다.
- 의미손실 migration은 ceiling을 낮춘다.
- mixed-version 상태가 Final/Certificate split-brain을 만들지 않는다.
