# ONSure Disaster Recovery·Business Continuity Assurance 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
ONSure 장애·재해·리전 손실 후 서비스 복구와 Assurance 권위 복구를 분리한다. 시스템이 다시 응답한다고 과거 Final/Certificate 발급 자격이 자동 복원되지 않도록 한다.

## 2. Recovery 대상
- relational DB
- append-only ledger
- evidence object store
- evidence graph head/index
- key/authority registry
- active selector/policy profile
- validator build/qualification
- queue/work-unit state
- certificate/revocation state

## 3. RPO/RTO와 Assurance
사업 RPO/RTO는 서비스 복구 목표이며 Assurance integrity 기준과 별도다. 데이터가 RPO 범위에서 유실되어도 어떤 assurance generation이 영향받았는지 반드시 계산한다.

## 4. Recovery State
`DISASTER_DECLARED → CONTROL_PLANE_RECOVERING → DATA_RESTORED_UNQUALIFIED → INTEGRITY_RECONCILIATION → ASSURANCE_REQUALIFICATION → LIMITED_SERVICE → FULL_SERVICE`

예외:
`RECOVERY_HOLD | INTEGRITY_UNKNOWN | KEY_AUTHORITY_UNKNOWN`

## 5. Recovery Qualification
필수 검사:
- DB snapshot generation 확인
- ledger continuity/head
- object manifest completeness
- graph head continuity
- certificate/revocation generation
- key/authority epoch
- active selector/policy epoch
- validator qualification validity
- outbox/queue duplicate reconciliation

## 6. Service Mode
복구 단계별 허용 기능을 분리한다.
- read historical evidence: 조건부 허용
- new validation: qualification 범위에 따라 허용
- FinalLock/Certificate issuance: recovery qualification 전 금지
- revocation: key/authority currentness 확보 전 제한/이중승인 후보

## 7. Multi-region Failover
Failover region은 기존 primary와 동일 policy/selector/key/validator generation인지 확인한다. DR replica가 과거 generation이면 strong write를 금지한다.

## 8. Evidence Loss
Evidence bytes가 유실됐으나 metadata/receipt가 남은 경우 historical fact는 보존할 수 있지만 reperformance 가능성을 잃는다. 영향 Certificate에는 limitation/currentness 재평가를 수행한다.

## 9. Key Loss/Compromise
Signing key loss와 compromise를 분리한다.
- loss: 새 발급 중단/키회전, historical signature 검증 가능성 평가
- compromise: affected issuance window 계산, certificate/revocation/requalification 수행

## 10. Business Continuity
외부 provider/OLicense/Git/AI가 장기 장애일 때 기능별 degraded mode를 정의한다. 특정 provider 장애 때문에 검증 범위를 조용히 축소해 PASS하지 않는다.

## 11. DR Drill
정기 Drill은:
- restore from backup
- cross-region failover
- ledger/object divergence
- key unavailable
- queue replay
- revocation backlog
- active selector restore
를 포함한다.

Drill 성공은 서비스 기동이 아니라 Recovery Qualification 완료까지다.

## 12. Negative Test
- old replica가 active selector authority 획득
- revocation 최신본 없이 certificate CURRENT 반환
- graph head gap인데 Final issuance 재개
- backup restore 후 duplicate external effect
- compromised key window certificate 미재평가
- AI provider outage로 AI tests skip 후 전체 PASS

## 13. 수용기준
- 서비스 복구와 assurance authority 복구가 분리된다.
- Recovery Qualification 전 strong issuance가 금지된다.
- DR/failover generation 차이를 machine-readable HOLD로 표현한다.
