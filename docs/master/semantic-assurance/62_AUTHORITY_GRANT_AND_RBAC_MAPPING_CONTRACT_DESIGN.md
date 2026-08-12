# ONSure AuthorityGrant·RBAC Mapping Contract 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
기존 5개 RBAC 역할과 세분화된 업무 Actor/Decision Authority 사이의 간극을 machine contract로 닫는다.

## 2. 분리 원칙
- RBAC: 일반 operation access의 coarse permission
- AuthorityGrant: 특정 subject/purpose/operation에 대한 fine-grained decision authority
- Qualification: 해당 판단을 할 능력
- Independence: 해당 판단을 독립적으로 할 자격

네 축 모두 필요한 operation에서 하나라도 없으면 strong decision 금지.

## 3. AuthorityGrant 필드
- grant_id
- principal_id
- organization/tenant
- authority_class
- operation_set
- subject_scope
- purpose_set
- valid_from/until
- delegation_depth_remaining
- issuer_grant_ref
- SoD_constraints[]
- qualification_requirements[]
- revocation_state
- authority_epoch
- signature

## 4. Authority Class
- REVIEW_DECISION
- INDEPENDENT_OTESTER
- INDEPENDENT_OAUDIT
- FACT_VALIDATOR
- RISK_ACCEPTOR
- BUSINESS_ACCEPTOR
- FINAL_APPROVER
- DEPLOYMENT_APPROVER
- CERTIFICATE_ISSUER
- REVOCATION_AUTHORITY
- POLICY_APPROVER
- BREAK_GLASS_AUTHORITY

## 5. RBAC Mapping
기본 후보:
- VIEWER: read only, public/private scope에 따른 조회
- OPERATOR: 실행/운영 operation, strong decision 없음
- APPROVER: strong decision 후보이나 AuthorityGrant 없이는 Final/Independent 권위 없음
- AUDITOR: audit/read/independent audit 후보, 별도 qualification/grant 필요
- ADMIN: 조직관리 권한, technical assurance authority 자동 부여 금지

ADMIN이라는 이유로 OTester/OAudit/Final 권위를 자동 부여하지 않는다.

## 6. Delegation Subset
파생 grant의 operation/subject/purpose/time/depth는 부모보다 넓을 수 없다. non-delegable authority flag를 지원한다.

## 7. Participation History
SoD 검증 시 현재 role만 보지 않고 같은 subject/change에서 implementation/oracle/review/approval participation history를 조회한다.

## 8. Negative Test
- ADMIN이 AuthorityGrant 없이 Final approve
- APPROVER가 independent OAudit 수행
- delegated child scope가 parent보다 넓음
- revoked grant cache로 effect commit
- same principal이 alias accounts로 four-eyes

## 9. 수용기준
업무 Actor와 RBAC가 1:1 고정되지 않으며, strong decision은 RBAC+Grant+Qualification+Independence+SoD의 결합으로만 생성된다.
