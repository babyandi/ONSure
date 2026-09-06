# 165 Blind Design Discovery Waves 2~3

Status: `DESIGN_DISCOVERY / NON_FINAL`

## 1. 목적
`162~164`의 final-target delta discovery 이후에도 설계 누락 가능성을 닫지 않고, 서로 다른 관점의 blind review를 추가 수행한다. 기존 설계 문서의 문구 반복은 새 요구사항으로 세지 않는다.

Wave 2 lenses:
- 금융회사 운영조직 / 감사 / 규제 대응
- 장기 운영 3~5년 / M&A / 조직 분할·합병
- 재해·리전 이동·데이터 주권
- 외부 AI SaaS·모델·Vendor EOL
- Black-box/제3자 시스템 검증 제약

Wave 3 lenses:
- 공격자/내부자/공모
- 장기 Evidence 검증가능성
- cryptographic lifetime
- external auditor/regulator evidence handoff
- discovery process 자체의 false-completeness

## 2. 중복 제거 원칙
기존 `46 Security/Privacy`, `51 DR/BCP`, `52 External Integration/Supply Chain`, `126 Fresh Review`, `162/163 Delta`에 이미 같은 machine invariant가 있으면 DUPLICATE/REFINEMENT로 처리하고 독립 subsystem을 새로 만들지 않는다.

## 3. Wave 2/3 신규·강화 obligation

### DD-025 Regulatory Effective-Date & Change Impact — P0
정책/규제 요구는 이름과 버전만이 아니라 jurisdiction, legal entity, business line, effective_from/to, transition/grace, supersession과 결속한다. 규제 변경 시 영향을 받는 Requirement/Policy/Certificate/Case를 역추적하고 기존 PASS를 자동 승계하지 않는다.

### DD-026 Applicability Context Change — P0
법인·업무·고객유형·데이터등급·materiality·deployment region 변경은 applicability denominator 변경 후보를 생성한다. 기존 Case/Certificate에 미치는 영향이 평가되기 전 CURRENT claim을 유지하지 않는다.

### DD-027 Financial Business-Date / Trading-Calendar Integrity — P1
업무일, 거래일, cutoff, timezone, holiday calendar, end-of-day/batch boundary가 검증 의미에 영향을 주는 경우 canonical machine time과 business calendar generation을 분리·결속한다. DST/locale 표시 차이로 동일 상태 의미가 바뀌지 않는다.

### DD-028 Black-box Access Constraint Claim Ceiling — P0
ToS, rate limit, no-admin/no-log/no-model-weight/no-dataset-access 등 Black-box 제약으로 필수 검증축을 수행하지 못하면 PARTIAL/NOT_PROVEN/HOLD로 claim ceiling을 낮춘다. 접근제약을 위험 없음으로 해석하지 않는다.

### DD-029 Third-Party Target Authorization Chain — P0
고객 소유가 아닌 API/서비스/모델/데이터에 active test를 수행하려면 owner/delegated authority, target scope, allowed effect, validity, revocation을 검증한다. ONSure 구매권한은 제3자 시스템 공격/부하시험 권한을 의미하지 않는다. `FR-FRESH-001`의 상세 refinement다.

### DD-030 Organization Restructuring & Ownership Transfer — P0
M&A, tenant split/merge, 사업부 이관, 법인 변경, ownership transfer 시 Requirement/Evidence/Certificate/Authority lineage를 재바인딩하며 SoD와 data access를 재검증한다. tenant identifier를 단순 rename하여 과거 권위를 승계하지 않는다.

### DD-031 Sovereign Residency Migration & DR Boundary — P0
리전 이동/DR/failover에서 data residency, encryption-key jurisdiction, subprocessor, evidence export restrictions를 재평가한다. 서비스 복구가 legal/residency compliance 복구를 의미하지 않는다.

### DD-032 Vendor/Model/Service EOL & Exit Requalification — P0
모델/Provider/API/Package/Plugin/External Service가 deprecate/EOL/contract termination되면 대체 경로의 semantic equivalence를 검증하고 영향 Case/Certificate를 재자격한다. alias-compatible만으로 동일 provider/model로 취급하지 않는다.

### DD-033 Cryptographic Agility & Historical Verification — P0
서명/해시/암호 알고리즘의 deprecation, key-size floor 변화, CA/provider 폐기 이후에도 historical evidence를 검증 가능한 방식으로 migration/renewal/anchor한다. 알고리즘 약화가 historical fact를 자동 삭제하지도, 계속 strong-current로 유지하지도 않는다.

### DD-034 Regulator/Auditor Selective Disclosure & Chain of Custody — P1
외부 감사·감독기관 제출 pack은 최소 필요 disclosure, redaction provenance, export recipient/purpose, custody transfer, read-back verification을 가진다. redaction 후에도 claim scope/limitation/hash linkage가 검증 가능해야 한다.

### DD-035 Privileged Support/Admin Intervention Contamination — P0
지원/운영/DBA/SRE의 수동 개입이 Case, Evidence, validator, policy, queue, certificate state에 미친 영향을 별도 privileged-effect receipt로 기록한다. 운영자 수동 복구 후 기존 CLEAN을 자동 승계하지 않는다.

### DD-036 Collusion / Quorum Common-Ownership Independence — P0
서로 다른 계정·키·모델이어도 같은 조직/관리권한/결론 초안/공통 hidden leakage에 종속되면 independent/four-eyes로 보지 않는다. quorum은 principal uniqueness뿐 아니라 common-control/common-knowledge 위험을 평가한다.

### DD-037 Long-Horizon Evidence Readability & Schema Migration — P1
장기보존은 bytes 존재만이 아니라 schema/profile/parser/verification method를 미래에도 재현할 수 있어야 한다. schema migration은 source evidence를 덮어쓰지 않고 old→new transformation receipt와 dual read-back을 남긴다.

### DD-038 Financial Transaction / Market-Effect Safe Simulation — P0
거래·주문·결제·삭제·전송처럼 실제 금융/외부 effect를 일으킬 수 있는 검증은 simulation/sandbox/non-production identity를 우선하고, production effect 필요 시 별도 authority, ceiling, abort, reconciliation을 강제한다.

### DD-039 Monitoring Blind-Spot / Telemetry Loss Claim Ceiling — P1
운영효과 검증에서 telemetry gap, sampling loss, collector outage, clock skew, log truncation이 존재하면 `OPERATING_EFFECTIVELY` claim을 제한한다. "관측된 오류 0"과 "충분히 관측함"을 구분한다.

### DD-040 Discovery Exhaustion Protocol — P0 / NEW CROSS-CUTTING
설계 완전성은 한 번의 fresh review 또는 문서 개수로 선언하지 않는다. 독립 lens set, negative-space challenge set, external-standard delta, customer-lifecycle delta, adversarial delta, prior-unknown replay를 반복하고 신규 P0/P1 발견률이 수렴했음을 증거화한다. authority/target scope가 바뀌면 saturation은 invalidated된다.

## 4. Triage 결과
- VALID_REQUIREMENT: 16/16
- NEW CROSS-CUTTING: DD-030, DD-037, DD-040
- EXISTING-OWNER REFINEMENT: 13
- DUPLICATE rejected from candidate set: DR service-vs-assurance recovery, provider mutable identity, tenant isolation, generic appeal/waiver, generic offboarding, generic supply-chain provenance

## 5. Discovery Saturation 판정
Wave 2/3에서도 P0/P1이 16건 발견됐으므로 `GLOBAL_DISCOVERY_EXHAUSTED=false`다.
다만 이번 결과 이후에는 무한 반복이 아니라 `DD-040`의 saturation protocol로 종료조건을 machine-readable하게 관리해야 한다.

## 6. 다음 상태
`DESIGN_DISCOVERY_WAVES_2_3_COMPLETE / 16_VALID_DELTA / GLOBAL_DISCOVERY_NOT_YET_EXHAUSTED / NON_FINAL`
