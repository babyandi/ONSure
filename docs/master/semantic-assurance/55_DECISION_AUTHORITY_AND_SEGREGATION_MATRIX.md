# ONSure Decision Authority·Segregation of Duties Matrix

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
누가 무엇을 볼 수 있는가(RBAC)와 누가 어떤 강한 결정을 만들 수 있는가(Decision Authority)를 분리한다. role string 하나로 Final/Independent/Certificate 권위를 발급하지 않는다.

## 2. Decision Class
- TECHNICAL_OBSERVATION
- TECHNICAL_DECISION
- INDEPENDENT_TESTER_ASSURANCE
- INDEPENDENT_AUDIT_ASSURANCE
- FACT_VALIDATION
- RISK_ACCEPTANCE
- BUSINESS_ACCEPTANCE
- FINAL_APPROVAL
- DEPLOYMENT_AUTHORIZATION
- CERTIFICATE_ISSUANCE
- REVOCATION
- POLICY_CHANGE
- AUTHORITY_GRANT
- BREAK_GLASS

## 3. Principal Attributes
권위 판정은 최소:
- principal identity
- organization/tenant relationship
- role
- purpose
- qualification
- independence profile
- admin/key ownership
- validity window
- delegation chain
- prior conflicting participation
을 사용한다.

## 4. SoD 기본 Matrix
- 구현자 ≠ 독립 OTester
- 구현자/1차 Reviewer ≠ 독립 OAudit
- OTester ≠ OAudit (고신뢰 profile 기본)
- Risk Acceptance ≠ Fact Validation
- Certificate issuer ≠ 단독 revocation authority 정책 가능
- policy weakening proposer ≠ sole approver
- hidden corpus maintainer ≠ target/validator learner
- key administrator ≠ 단독 Final approver 권장

## 5. Four-eyes 계산
2개의 account/key가 아니라 2개의 독립 principal을 센다.
동일 admin owner/employee/service identity의 alias는 1인으로 취급한다.

## 6. Delegation
Delegated grant는:
- 원 grant scope의 subset
- 유효기간 이하
- delegation depth 이하
- purpose constraint 보존
- non-delegable authority 금지

Final/Revocation/Policy Weakening은 profile에 따라 delegation 자체를 금지할 수 있다.

## 7. Conflict of Interest
같은 subject/change에서:
- code implementation
- oracle creation
- hidden ground truth creation
- independent verification
- final approval
관계를 기록해 common-mode bias를 계산한다.

## 8. Emergency Authority
Break-glass는 operation permission을 일시 확대할 수 있으나 technical assurance strength를 올리지 않는다. 사용 후 affected assurance re-evaluation을 강제한다.

## 9. Authority Receipt
강한 decision에는:
- principal/grant
- qualification/independence refs
- purpose
- subject/context digest
- authority epoch
- signed_at/effective_at
- nonce
- signature
가 필요하다.

## 10. Negative Test
- 동일 principal의 두 key로 OTester/OAudit
- expired delegated grant로 Final approval
- fact validator가 자기 oracle 생성
- risk acceptor approval을 Ground Truth로 사용
- break-glass로 Certificate issuance strength 상승
- policy proposer가 단독 weakening 승인

## 11. 수용기준
- strong decision은 RBAC role만으로 생성되지 않는다.
- SoD/four-eyes는 principal/ownership/participation history까지 검증한다.
- Business/Risk Acceptance와 사실 검증을 분리한다.
