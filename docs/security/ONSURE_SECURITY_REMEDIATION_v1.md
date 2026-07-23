# ONSURE Security Remediation Contract v1

## 1. 목적

보안 검토는 코드 리뷰의 부속 항목이 아니다. ONSURE은 보안 위반을 탐지하고, 공격 경로와 영향도를 분석하고, 안전한 수정안을 선택하고, 허용 범위에서는 직접 수정하며, 동일 공격과 변형 공격을 재시험해야 한다.

## 2. 보호 자산

- 소스코드와 빌드 산출물
- GitHub token, API key, signing key, credential
- 고객·사용자·업무 데이터와 개인정보
- Receipt, Lock, Permit, Oracle, Policy
- CI/CD, container, cloud, runtime configuration
- OTester/OAudit의 독립 판정 권한
- AI Agent의 prompt, context, tool permission

## 3. 신뢰 경계

1. GitHub -> Source Intake
2. Source Intake -> ONSURE Runtime
3. Runtime -> external scanner/tool
4. Runtime -> patch workspace
5. Runtime -> Builder/Agent
6. Runtime -> OTester
7. OTester -> OAudit
8. OAudit -> Publication/Merge Gate

각 경계는 입력 hash, 호출 주체, 권한, 시간, 정책 digest를 기록한다.

## 4. 필수 위협 범주

### Application
- injection, XSS, CSRF, SSRF
- path traversal, unsafe file upload
- unsafe deserialization, template injection
- authentication bypass, privilege escalation
- insecure session/token lifecycle
- race, replay, duplicate execution, TOCTOU

### Data and Privacy
- secret in source/log/receipt
- personal data overcollection or unmasked output
- insecure storage or transit
- retention/deletion policy mismatch

### Supply Chain
- vulnerable or malicious dependency
- lockfile bypass
- unsigned/untrusted artifact
- compromised workflow/action
- dependency confusion and typosquatting
- incomplete SBOM or provenance

### Infrastructure
- excessive IAM
- root container, writable filesystem, dangerous capability
- open network/service, weak TLS
- insecure CI variable and artifact handling

### Assurance System
- forged or reused Receipt
- source/policy/runner/oracle mismatch
- Permit replay
- test suppression or skipped security lane
- Runtime writing OTester/OAudit decision
- stale evidence accepted as current

### AI/Agent
- prompt injection from repository content
- tool invocation beyond declared scope
- secret exfiltration through generated output
- untrusted code execution
- cross-tenant context leakage
- Agent self-approval

## 5. Finding schema

각 Finding은 다음을 포함한다.

- finding_id
- category and weakness identifier
- affected asset
- file/function/line or configuration path
- attack precondition
- exploit path
- impact and blast radius
- severity and confidence
- violated requirement/policy
- reproduction evidence
- recommended minimal fix
- recommended durable fix
- required regression/adversarial tests
- residual risk

## 6. 심각도와 Gate

- CRITICAL: 즉시 실행·병합·배포 차단
- HIGH: 병합·배포 차단
- MEDIUM: 명시적 승인과 기한 없는 배포 금지
- LOW: 계획된 보완 가능
- INFO: 근거 보존, 자동 PASS 영향 없음

CRITICAL/HIGH가 1건이라도 열려 있으면 Final PASS는 불가능하다.

## 7. 개선 절차

```text
Finding
-> exploit reproduction
-> root cause analysis
-> remediation options
-> approval classification
-> patch
-> focused security test
-> bypass/variant test
-> full regression
-> OTester reproduction
-> OAudit evidence check
-> close or residual-risk approval
```

## 8. 자동 수정 경계

자동 적용 가능:
- parameterized query
- allowlist/input validation
- output encoding
- secure cookie/header defaults
- secret removal and reference substitution
- least-privilege configuration where compatibility is preserved
- safe dependency patch/minor upgrade with lock update
- test and security fixture addition
- fail-open to fail-closed where documented behavior is unchanged

승인 필수:
- auth provider or identity model change
- permission/RBAC redesign
- cryptographic migration
- data retention/deletion changes
- network/API compatibility breaking change
- major dependency/framework migration
- business workflow or regulatory interpretation change

## 9. Patch Receipt

- finding_id
- source_commit_sha
- pre_patch_tree_sha
- patch_sha256
- changed_paths
- post_patch_tree_sha
- policy_digest
- actor and approval
- focused_test_receipts
- adversarial_test_receipts
- full_regression_receipt
- residual_risk

## 10. 완료 기준

보안 수정 완료는 다음을 모두 충족해야 한다.

1. 원 공격이 차단됨
2. 우회·변형 공격이 차단됨
3. 정상 기능이 유지됨
4. 새로운 CRITICAL/HIGH가 없음
5. 전체 회귀가 연속 2회 동일 결과
6. OTester가 독립 재현
7. OAudit이 증거 계보 검증

스캐너가 0건을 보고했다는 사실만으로 PASS할 수 없다.
