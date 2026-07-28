# ONSure 금융권 통제 추적·최종 수용 기준

## 1. 통제 레코드

모든 요구는 다음 필드를 가진다.

```text
Control ID, Source IDs, Jurisdiction, Version, Effective Date
Risk/Threat, Asset, Actor, Preconditions
Atomic Requirement, Design Component, Implementation Symbol/Config
Critical Callpath, Positive/Negative/Adversarial/Resilience Case IDs
Oracle, Evidence Schema, Retention, Independent Reviewer
Applicability/N-A rationale, Exception approval, Expiry
Status and exact Source/Model/Data/Prompt/Policy/Environment identity
```

## 2. P0 통제군

| ID | 통제 | 핵심 수용 기준 |
|---|---|---|
| FIN-GOV | 거버넌스·Materiality | 전 AI 자산 Inventory, Owner, 위험등급, Risk Appetite, 독립검증 주기 |
| FIN-IAM | IAM·MFA·SoD | AD/LDAP/OIDC, MFA, RBAC+ABAC, PAM/JIT, 자기승인 차단 |
| FIN-TEN | 격리 | Tenant/고객/프로젝트/환경/실행노드 간 교차 접근 공격 차단 |
| FIN-DAT | 데이터 보호 | 개인(신용)정보 분류·최소화·목적제한·마스킹·삭제·RAG 권한상속 |
| FIN-CRY | 암호·키 | TLS, 저장암호, KMS/HSM, 키 분리·회전·폐기, 서명검증 |
| FIN-NET | 망·외부연계 | Zero Trust, Default-deny Egress, 폐쇄망, 반출입 승인, SaaS/LLM Profile |
| FIN-AIS | AI 보안 | Injection, Exfiltration, Poisoning, Model theft, Unsafe output, Agent 권한상승 |
| FIN-MRM | 모델위험 | 개발·사용·검증 분리, 한계·가정·성능·Drift·변경·벤더 모델 검증 |
| FIN-SDL | Secure SDLC | Threat model, Review, SAST/DAST/SCA/IaC/Secret, 결함 SLA |
| FIN-SUP | 공급망 | SBOM, Provenance, 서명·재현 Build, 모델/데이터/VSIX/컨테이너 출처 |
| FIN-AUD | 감사·증적 | 행위자·시간·대상·전후 상태, WORM, Timestamp, Ledger Anchor, Legal Hold |
| FIN-RES | 복원력 | HA, RTO/RPO, Backup/Restore, Chaos, DR, Pause/Resume, 멱등성 |
| FIN-OPS | 관제·사고 | SIEM/SOC, 탐지·격리·Forensics·보고·재검증 Trigger |
| FIN-TPR | 제3자 | 계약, 데이터 처리, 하도급, 지역, 종료/회수, 독립시험, Exit Plan |
| FIN-CUS | 소비자보호 | 공정성, 설명, 이의제기, Human Override, 고위험 자동결정 제한 |
| FIN-DEV | AI 개발작업면 | VS Code Context/Tool/Git 최소권한, Prompt Injection·Secret 반출 차단 |
| FIN-EVD | 검사 제출 | 통제별 원문·설계·코드·시험·Receipt·승인 Pack과 누락 공개 |

## 3. 제품 유형별 최소 프로파일

### RAG/챗봇
문서 ACL 상속, Chunk/Embedding/Index 계보, Cross-user leakage, Prompt Injection, Citation faithfulness, 삭제 전파, 외부 LLM 반출을 시험한다.

### AI Agent
Tool Capability, 금액·건수·고객 범위, Transaction Signing, Human-in-the-loop, Replay/Idempotency, 메모리 오염, Multi-agent delegation을 시험한다.

### 신용·심사·평가 모델
대표성, 차별·편향, 설명, Override, Champion/Challenger, Drift, Backtesting, 독립 Validation, 소비자 이의제기를 시험한다.

### 코드 생성/개발 Agent
취약 코드, Secret, 라이선스, Dependency confusion, Prompt Injection, Workspace escape, 승인 없는 Git/배포를 시험한다.

### AI 보안제품
탐지 오탐·미탐, Fail-open, 자체검증, Policy bypass, Model evasion, 장애를 정상 차단으로 오판하는지를 시험한다.

## 4. Case Registry

각 Requirement는 최소 한 개 Positive와 한 개 Negative를 갖는다. 공격 가능한 통제는 Adversarial, 운영 중요 통제는 Resilience가 필수다.

- Positive: 허용 업무가 기대 결과·권한·Evidence로 성공
- Negative: 잘못된 입력·권한·상태가 정확한 사유로 차단
- Adversarial: 우회·변조·Replay·Injection에도 통제 유지
- Resilience: 장애·복구·부하·재시작 후 안전성과 일관성 유지
- Metamorphic: 의미 불변 변환에서 판정 일관성
- Differential: 독립 Oracle/Provider/버전 간 차이 분석

도구 오류·환경 미구성·0건 실행은 PASS가 아니다. Skip/Disabled는 승인된 N/A가 아니면 분모 미충족이다.

## 5. Evidence 계보

```text
Source Rule Hash
→ Atomic Requirement Hash
→ Design Hash
→ Implementation/Config/Model/Data Hash
→ Plan and Approval Hash
→ Execution Environment Hash
→ Input/Output/Oracle Hash
→ Result Receipt
→ Finding/RCA/Patch Hash
→ Regression Receipt
→ Independent Review Receipt
→ Release/Delivery Receipt
```

어느 부모 Hash든 1바이트 변경되면 하위 신뢰는 무효다.

## 6. 예외와 위험수용

Exception은 통제 ID, 위험, 영향, 보완통제, Owner, 승인자, 시작/만료, 재검토 조건을 가진다. AI가 위험을 수용할 수 없다. Critical 통제의 영구 예외를 허용하지 않는다. 만료된 예외는 자동 HOLD다.

## 7. 최종 Gate

다음을 모두 충족해야만 Final 후보가 된다.

1. 적용 통제 분모·요구·사례·실행 수 일치
2. Critical/High Finding 0
3. UNKNOWN/NOT_RUN/INCONCLUSIVE/PENDING 0
4. 승인되지 않은 Skip/Disabled/N-A 0
5. 현재 동일 HEAD·모델·데이터·Prompt·Policy·환경
6. Positive/Negative/Adversarial/Resilience 전부 실행
7. 실제 고객 또는 대표 금융 시나리오 3세트 이상
8. 변조·Replay·호출 우회 실패 주입
9. 복구·DR·Backup Restore 실제 시험
10. OTester와 OAudit가 서로 독립적으로 각 2회 CLEAN
11. 사람의 보안·준법·업무·Release 승인
12. Evidence Pack 내보내기와 독립 Read-back 검산

자체검증 결과는 `SELF_VALIDATION_NONFINAL` 상한이다.

## 8. 현재 설계와 구현의 관계

본 문서는 최종 목표를 고정한다. 기존 구현이 자동으로 충족된 것으로 간주되지 않는다. 각 통제는 `DESIGNED/IMPLEMENTED/CONNECTED/TESTED/EVIDENCED/INDEPENDENTLY_VERIFIED/OPERATING_EFFECTIVELY`로 별도 평가한다.

현재 전체 Java 17 Local Gate, 금융권 E2E, 독립 OTester/OAudit가 완료되지 않았으므로 상태는 계속 `NONFINAL/HOLD`다.
