# ONSure V2 구현 로드맵과 수용 기준

## 1. 구현 원칙

문서·UI만 완료로 보지 않는다. 웹 1회 Case, VS Code 지속형 Full-Chain, 결제·OLicense 라이선스가 실제 E2E로 연결되어야 한다.

## 2. Lane

### L0 계약 기준선

- Product/Service/Catalog 계약
- System·Program 판정 규칙
- Learning Unit·Credit Meter
- Case·Subscription·Run 상태
- License·Entitlement Schema
- Data Retention·Security 정책

### L1 OLicense

- ONSure Catalog·Price·Option
- Web Case License
- VS Code Subscription
- Signed Token·Offline Snapshot
- Seat·System·Program Activation
- Credit Reserve/Commit/Release
- Payment Webhook·Refund·Revocation

### L2 Web Commerce

- 회원·조직
- 서비스 선택
- Intake·Preflight·자동견적
- Checkout·Order·Invoice
- Case Dashboard
- 승인·중간결제·납품·종료

### L3 ONSure Core

- Learning Engine
- Verification Engine
- Finding·RCA
- Improvement·Reverification
- Evidence·Report
- Git·PR

### L4 VS Code

- Login·Entitlement
- Workspace/System/Program Binding
- Chat·Profile·Finding·Improvement
- Diff·Approval·Git·PR
- Credit·Plan·Upgrade 화면
- 중단·재접속 복구

### L5 Operations

- Admin Case Console
- License·Billing Support
- Expert Review Workflow
- SLA·Notification
- Fraud·Chargeback·Dispute
- Data Export·Deletion

### L6 Security·Quality

- Tenant Isolation
- Secret·Malware
- Prompt/Tool 공격
- Sandbox 탈출
- License 위조·Replay·Clock rollback
- Payment Webhook 중복·순서역전
- Supply Chain·SBOM

## 3. 단계별 Release

### Release A: Web Learn

1 System, 제한 Program, 자동 학습량 산정, 결제·License 발급, Program Profile 납품.

### Release B: Web Verify / Learn & Verify

검증기준 판정, Scenario, Finding·RCA, Evidence, 통합보고서.

### Release C: Improve & Re-verify

Finding 선택, 추가 견적·결제, Patch, 회귀검증, Before/After, PR.

### Release D: VS Code Developer

최초·증분학습, 검증, 개선, Git·PR, Credit·Entitlement.

### Release E: Team·Enterprise

공유정책·승인·CI/CD·SSO·RBAC·감사·Offline.

## 4. Web E2E 수용 시나리오

- Learn: 견적→결제→License→학습→납품→완료
- Verify: 적합성판정→검증→Finding→보고서
- Learn & Verify: 학습→고객확인→검증→납품
- Improve: 추가결제→Patch→재검증→PR
- 한도초과: 추가승인 없이는 실행 차단
- 기준선변경: 변경계약 또는 신규 Case
- 환불·취소·만료·License 취소

각 시나리오는 연속 2회 동일 정책으로 통과한다.

## 5. VS Code E2E 수용 시나리오

```text
구독
→ 로그인·Seat
→ Workspace Binding
→ 최초학습
→ Finding
→ Patch 승인
→ Test·Before/After
→ Commit·Push·Draft PR
→ 재시작 복구
→ Usage 정산
```

Developer, Team, Enterprise Entitlement 차단/허용을 각각 검증한다.

## 6. OLicense 수용 기준

- 결제 성공과 License ACTIVE 일치
- 중복 Webhook에도 License 1개
- Credit 이중차감 0건
- Token 위조·만료·Revocation 차단
- Offline Grace·Clock rollback 방어
- Unlimited가 Resource Unlimited로 오해되지 않도록 별도 Limit 집행
- ONSure가 임의로 Feature·Limit 확대 불가

## 7. 성능·운영 기준

- 견적·Entitlement 조회 응답 목표
- 장기 Run Queue·취소·재개
- 고객별 동시 실행 제한
- Worker 장애 후 Checkpoint 복구
- 결제·사용량 대사
- Evidence와 보고서 무결성
- 데이터 삭제 검증

구체 수치는 실제 인프라·원가 Benchmark 후 확정한다.

## 8. 출시 Gate

```text
Critical/High 보안결함 0
License·Billing E2E 2회 PASS
Web 주요 Case E2E 2회 PASS
VS Code Full-Chain 2회 PASS
Dirty Workspace 보존
Rollback·Refund·Revocation 검증
Tenant Isolation 검증
독립 기술검토·Blind Review
운영 Runbook·Support 준비
```

하나라도 미달이면 Production/Commercial GO를 주장하지 않는다.

## 9. VS Code 작업 시작점

- ONSure Issue #2: Core·VS Code·Git 구현
- ORUDA Issue #792: OLicense ONSure 확장
- 이 문서 세트를 양쪽 저장소의 공통 사업·계약 기준으로 사용
- 구현 PR은 Lane별로 분리하고 중복 권위 파일을 만들지 않는다.
