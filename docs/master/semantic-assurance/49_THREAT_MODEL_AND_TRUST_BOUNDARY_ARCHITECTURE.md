# ONSure Threat Model·Trust Boundary 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
ONSure가 검증 대상·개발자·관리자·Plugin·AI Provider·OLicense·Git/CI·외부 발주기관과 연결될 때 어떤 입력과 주체를 신뢰하지 않는지 명시한다.

## 2. 주요 Trust Boundary
- Customer Client ↔ Control Plane
- Control Plane ↔ Local Runtime/Sandbox
- Sandbox ↔ Customer Source
- Core ↔ Plugin/Adapter
- ONSure ↔ External AI Provider
- ONSure ↔ Git/CI/Payment/OLicense
- ONSure ↔ Independent OTester/OAudit
- Private Evidence ↔ Public Certificate Verifier
- Online Authority ↔ Air-gapped Runtime

## 3. 공격자 모델
- malicious tenant user
- compromised developer credential
- compromised admin/operator
- malicious target code
- malicious plugin/adapter
- compromised external provider
- insider with partial authority
- stale/offline node
- supply-chain attacker
- target attempting validator evasion

## 4. 고가치 자산
- customer source/evidence
- authority/key registry
- Final approval/lock
- active selector
- hidden corpus/benchmark
- validator build/qualification
- revocation/currentness state
- OLicense entitlement/credit
- audit/ledger/graph head

## 5. STRIDE+Assurance 위협분류
기존 Spoofing/Tampering/Repudiation/Information Disclosure/DoS/Elevation 외에:
- False Assurance
- Evidence Substitution
- Authority Semantic Erasure
- Denominator Manipulation
- Stale Result Reuse
- Fake Independence
- Qualification Forgery
- Cross-generation Mixing
- Validation Evasion
을 1급 threat class로 둔다.

## 6. Trust 원칙
- 대상 프로그램의 self-report는 corroboration일 뿐 authority가 아니다.
- UI/client가 주장하는 tenant/role/target root를 신뢰하지 않는다.
- Plugin result는 plugin qualification ceiling을 넘지 못한다.
- external provider agreement는 Ground Truth가 아니다.
- signed object도 key/authority/currentness 검증 전에는 current authority가 아니다.

## 7. Threat Scenario 예
### T-01 Target Escape
대상 코드가 sandbox/network/filesystem boundary를 탈출하여 validator/evidence를 변경.
대응: isolation, read-only source, capability drop, target-bound root, evidence outside target control.

### T-02 Validator Evasion
대상이 ONSure marker를 감지해 안전하게 행동.
대응: blind/naturalistic scenario, marker variation, instrumented/uninstrumented comparison.

### T-03 Evidence Substitution
과거 정상 run receipt를 현재 target에 사용.
대응: target/scope/requirement/policy/run/nonce binding.

### T-04 Fake Independent Pass
동일 principal/admin/implementation이 다른 key/run으로 독립성을 위장.
대응: multi-axis independence profile.

### T-05 Revocation Suppression
stale cache/offline node가 revoked certificate를 CURRENT로 표시.
대응: generation-bound revocation, offline uncertainty ceiling, cache non-authority.

## 8. Threat-to-Control Trace
모든 threat는 최소:
- preventive control
- detective control
- negative fixture
- evidence source
- residual risk
- affected assurance claim
을 가진다.

## 9. Abuse Case
- repeatedly request hidden qualification feedback to infer answers
- use free/retry paths to exhaust validation budget
- intentionally produce massive dependency graph to force partial coverage
- create many identities to fake four-eyes
- upload crafted archive/path to escape target root
- use signed old plugin version after revocation

## 10. 수용기준
- 모든 외부/tenant/target/plugin/provider 입력은 explicit trust boundary를 가진다.
- false-assurance threat class가 일반 security threat와 동일하게 test/evidence trace를 가진다.
- residual risk가 있으면 Assurance Level/Currentness/Certificate limitation에 반영된다.
