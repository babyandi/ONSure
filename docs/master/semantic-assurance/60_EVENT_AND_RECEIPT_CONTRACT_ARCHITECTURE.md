# ONSure Event·Receipt Contract Architecture

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
State transition, external effect, evidence commit, authority change가 서비스 내부 로그에만 남지 않고 재구성 가능한 Event/Receipt 계약을 갖도록 한다.

## 2. Event Envelope
- event_id
- event_type/version
- occurred_at
- recorded_at
- producer
- organization/tenant
- subject_type/id/digest
- correlation_id
- causation_id
- operation_id
- policy/authority generation
- payload_digest
- schema_version

## 3. Receipt Envelope
- receipt_id/type/version
- operation_id
- subject/context digest
- input_digest
- output/effect_digest
- decision/lifecycle state
- evidence refs
- authority/principal/key refs
- issued_at
- nonce/idempotency key ref
- canonicalization profile
- self_digest/signature

## 4. Receipt Class
- EXECUTION
- EVIDENCE_COMMIT
- AUTHORIZATION
- INDEPENDENT_ASSURANCE
- HUMAN_ACCEPTANCE
- FINAL_APPROVAL
- FINAL_LOCK
- DEPLOYMENT_READBACK
- CURRENTNESS
- COMPOSITION
- CERTIFICATE
- REVOCATION
- POLICY_CHANGE
- RECOVERY_QUALIFICATION

서로 다른 assurance strength receipt를 type 하나로 평탄화하지 않는다.

## 5. Event vs Receipt
Event는 ‘무슨 일이 관측/기록되었는가’, Receipt는 ‘어떤 operation/effect/decision이 증거·권위와 함께 봉인되었는가’를 의미한다. Event 존재만으로 Receipt 의미를 대체하지 않는다.

## 6. Ordering
Global total order를 가정하지 않는다. subject/aggregate 내 version, causation chain, graph generation을 사용한다. clock timestamp만으로 supersession을 결정하지 않는다.

## 7. Replay
Event replay는 projection/state reconstruction에 사용하지만 external effect를 재실행하지 않는다. Receipt replay는 검증만 가능하며 소비성 approval/nonce는 single-consume ledger를 확인한다.

## 8. Negative Test
- event payload와 payload_digest 불일치
- same receipt_id different bytes
- approval receipt nonce 재소비
- independent receipt가 self-validation class
- recorded_at 순서만으로 revoked state 되돌림
- replay consumer가 payment/deploy effect 재실행

## 9. 수용기준
모든 강한 transition/effect는 대응 Event+필요시 Receipt를 갖고, 재시작/감사 시 causation과 evidence를 재구성할 수 있다.
