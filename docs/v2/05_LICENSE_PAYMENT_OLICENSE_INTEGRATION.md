# ONSure 라이선스·결제·OLicense 연계

## 1. 권위

OLicense는 ONSure 상품·계약·라이선스·Entitlement·사용량의 단일 권위다. ONSure는 독립 제품이지만 OLicense가 발급한 권한을 소비한다.

## 2. 책임 분리

### Payment Provider
- 결제수단 인증
- 승인·취소·환불
- 정기결제
- 세금·영수증에 필요한 결제정보
- Webhook

### OLicense
- Product/Price Catalog
- Order·Contract
- License·Entitlement 발급
- Case·Subscription 상태
- Seat·System·Program·Credit 원장
- 사용량 예약·확정·해제
- 서명 Token·Offline Snapshot
- Revocation·Audit

### ONSure
- 상품 선택·견적 UX
- 대상·학습량·검증범위 산정
- Entitlement 검증
- 실제 실행과 사용량 보고
- 결과·Case 완료 상태 보고

## 3. Product Code

```text
ProductCode: ONSURE
Channel: WEB_CASE | VSCODE | API
ServiceType: LEARN | VERIFY | LEARN_VERIFY | IMPROVE_REVERIFY
Plan: DEVELOPER | TEAM | ENTERPRISE
Option: UNLIMITED_SYSTEMS_PROGRAMS | OFFLINE | PRIORITY_SUPPORT | EXPERT_REVIEW
```

## 4. Web Case License

필수 필드:

- LicenseId, CaseId, OrganizationId, CustomerId
- ProductCode, Channel, ServiceType
- SystemBinding, ProgramBinding
- BaselineType, BaselineDigest
- LearningUnitLimit
- VerificationPack
- ImprovementUnitLimit
- ReverificationLimit
- ValidFrom, ValidUntil
- ExpertReview, PriorityDelivery
- Status
- Signature, KeyId, RevocationVersion

Case는 완료·취소·만료 후 재사용할 수 없다.

## 5. VS Code Entitlement

필수 필드:

- SubscriptionId, OrganizationId
- Plan·Options
- Seat·Device
- ActiveSystemLimit
- ProgramLimit/Capacity
- MonthlyCredit·OveragePolicy
- ConcurrentRun·Storage·Retention
- FeatureMap
- SupportLevel
- BillingPeriod·Renewal
- Signature·Revocation

## 6. Credit Transaction

```text
RESERVE
→ COMMIT 또는 RELEASE
```

- Idempotency Key 필수
- 중복 Webhook·재전송 안전
- 부분 Commit·부분 Release 지원
- 만료된 Reservation 자동 해제
- 고객·Case·Run·Program·Meter별 추적
- 음수 잔액과 이중 소비 차단

## 7. 결제 흐름

### Web

```text
Preflight·Quote
→ Order 생성
→ 결제 승인
→ OLicense License 발급
→ ONSure 실행
→ 추가 비용 승인 또는 한도내 진행
→ 완료
→ 정산·영수증
```

### VS Code

```text
Plan 선택
→ 정기결제
→ Subscription Entitlement 발급
→ Seat·Device 활성화
→ 월간 Credit 갱신
→ 초과정책 집행
→ 갱신·변경·해지
```

## 8. 상태 동기화

- 결제 성공이나 실패만으로 기능을 허용하지 않고 OLicense ACTIVE 상태를 확인한다.
- Webhook 순서역전, 중복, 지연을 Event ID·Version으로 처리한다.
- Refund 시 사용량과 납품상태를 고려해 License를 취소·제한한다.
- Chargeback·Fraud 상태는 즉시 고위험 기능을 차단하고 관리자 검토로 전환한다.

## 9. 보안

- 비대칭 서명 Token 또는 동등 수준
- JWKS/Key Rotation
- 짧은 Online Token과 캐시된 Snapshot 분리
- Audience·Issuer·Nonce·Expiry 검증
- Replay·Clock rollback 방어
- License 발급·관리 권한과 ONSure 실행 권한 분리
- 모든 변경 Audit Event 기록

## 10. 실패 정책

- Online 검증 불가 시 Plan별 Grace 적용
- Web 신규 Case 시작과 결제 기능은 Fail-closed
- 이미 실행 중인 저위험 단계는 짧은 Grace 허용 가능
- Push·PR·Autopilot·고비용 Run은 재검증 실패 시 차단
- OLicense 복구 후 사용량 재조정·대조
