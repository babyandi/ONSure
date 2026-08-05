# ONSure 제품 요구사항 및 수용 기준 — 최종 목표 정본

- 적용 범위: MVP가 아닌 금융권 최종 제품
- 대상: ONSure 자체, ORUDA 제품군, 외부 회사·금융회사의 모든 AI 제품
- 상태: NORMATIVE TARGET / 구현 완료 주장 아님

## 1. 정본 문서 집합

이 문서는 다음 상세 설계와 함께 읽어야 한다.

1. `40_FINAL_PRODUCT_RESEARCH_AND_ROLE_MODELS.md` — 49개 외부 출처와 롤모델
2. `41_ONSURE_FINAL_TARGET_ARCHITECTURE.md` — 최종 제품·구성요소·역할·운영 아키텍처
3. `42_VSCODE_AGENT_AND_GIT_FULL_CHAIN_DESIGN.md` — Claude형 VS Code Agent 및 통제된 Git Full-Chain
4. `43_FINANCIAL_CONTROL_TRACE_AND_ACCEPTANCE.md` — 금융통제·시험·Evidence·Final Gate
5. `44_UNIFIED_AI_WORK_DEVELOPER_ASSURANCE_DESIGN.md` — Codex형 개발, Claude형 VS Code, ChatGPT Work형 범용업무의 단일 세션·권한·증적 통합


기존 MVP 범위는 최종 목표를 축소하거나 누락시키는 상위 기준이 아니다. 단계별 개발은 이 최종 정본의 Requirement ID를 유지한 채 구현 순서만 나눈다.

## 2. 제품 목표

ONSure는 금융회사가 자체 개발·구매·위탁·오픈소스로 도입한 AI 제품을 White/Gray/Black-box 방식으로 독립 검증하는 AI Assurance Platform이어야 한다.

검증 범위는 다음 전체다.

- 요구사항과 금융 규제·내부통제의 완전성
- 아키텍처·위협모델·데이터 흐름·Trust Boundary
- 코드·설정·IaC·의존성·SBOM·Build Provenance
- 모델·데이터·Prompt·RAG·Vector Index·Agent·Tool
- 실제 운영 Critical Callpath 연결
- 정상·실패·경계·공격·복원·성능 시험
- 품질·정확성·공정성·설명·재현성·Human Oversight
- IAM·망·암호·키·감사·격리·관제·사고·DR
- Finding·RCA·승인 개선·동일 조건 회귀
- 독립 검증과 금감원/감사 제출 Evidence Pack

## 3. 핵심 사용자와 역할

개발자, AI/Model Owner, Product Owner, Data Owner, Security, Privacy, Compliance/Legal, Independent Validator, Auditor, Release Manager, CISO/Risk Committee를 분리한다. 동일인이 개발·검증·최종 승인을 동시에 수행하지 못하도록 SoD를 강제한다.

## 4. 제품 표면

- VS Code Extension: Claude형 저장소 분석·계획·수정·시험·Git 작업
- Enterprise Web: 포트폴리오·통제·승인·Finding·감사·관제
- CLI: 로컬·폐쇄망 실행과 운영 자동화
- Public SDK/API: 외부 제품·하네스·검증 Adapter
- Execution Node: 데이터가 있는 망 내부 격리 실행
- Offline Bundle: 완전 폐쇄망 설치·업데이트·Evidence 반출입

모든 표면은 동일 Core API, Policy Gateway, 상태기계와 Evidence Ledger를 사용해야 한다.

## 5. 최종 기능 요구군

### FR-FIN-01 Intake·Inventory
조직, 관할, 업무, Materiality, 데이터 등급, Source/Build/Model/Data/Prompt/RAG/Tool/Policy/Infra/Vendor 자산을 등록하고 Owner와 계보를 관리한다.

### FR-FIN-02 Learning·Profile
정적 구조와 실제 실행행동을 함께 학습하고, 프로젝트 전용 기억과 검증된 익명 범용 패턴을 분리한다. 변경분 학습과 기준선 승격 승인을 지원한다.

### FR-FIN-03 Regulatory Control
국내 금융·개인정보·신용정보 통제와 NIST/ISO/OWASP/MITRE/금융 MRM 기준을 버전 관리하고 제품·업무별 적용성을 산정한다.

### FR-FIN-04 Threat·Risk Planning
위협, 위험, 기존 통제, Coverage Gap, Positive/Negative/Adversarial/Resilience 계획과 Oracle을 생성하며 전체·부분 승인을 지원한다.

### FR-FIN-05 Verification Fabric
설계, 코드, 앱, API, 모델, 데이터, RAG, Agent, 공급망, 성능, 장애, 복구, DR를 실제 실행한다. 0건·Skip·도구오류·환경오류를 PASS로 처리하지 않는다.

### FR-FIN-06 AI Security
Prompt Injection, Jailbreak, Exfiltration, Poisoning, Model theft, Unsafe Output, Tool 권한상승, Memory 오염, Multi-agent 위임, 거래·삭제·전송 오남용을 검증한다.

### FR-FIN-07 Financial Security
IAM/MFA/RBAC+ABAC/PAM/JIT/SoD, Tenant 격리, 개인정보 보호, KMS/HSM, 망분리·Zero Trust, WORM 감사, SOC/SIEM, Incident, Backup/DR를 검증한다.

### FR-FIN-08 Finding·RCA
재현 절차, 최초 실패점, 영향, 원인 후보, 신뢰도, 미확인 사항을 분리하고 모든 Finding을 Source와 Case Receipt에 결속한다.

### FR-FIN-09 Controlled Improvement
Finding에 결속된 최소 Patch만 격리 Worktree에서 수행하고 파일/Hunk 승인, Rollback, 동일 조건 회귀, Before/After 입증을 요구한다.

### FR-FIN-10 VS Code Agent
Ask/Plan/Act/Verify/Improve/Autopilot/Audit/Offline, 저장소·선택·Diff Context, Terminal/Test 반복, 세션 복구, 원격 실행, 비용·데이터 전송 가시화를 제공한다.

### FR-FIN-11 Git Full-Chain
status/diff/log/branch/worktree/stage/commit/push/Draft PR을 지원한다. 사용자 Dirty 변경 보존, 승인 Bundle 재검산, Signed Receipt를 강제한다. AI 단독 Merge·Release·Production/Commercial GO·FinalLock은 금지한다.

### FR-FIN-12 Evidence·Independent Validation
원문→요구→설계→구현→호출→시험→Finding→개선→재검증→전달 Hash 계보, 외부 Anchor, 장기보존·Legal Hold, Restricted/Shareable Pack을 제공한다. OTester와 OAudit은 별도 프로세스와 Oracle을 사용한다.

### FR-FIN-13 Operations
HA, Scale, Queue, Checkpoint, Pause/Resume, Idempotency, RTO/RPO, Backup/Restore, DR, 관제, Incident, 변경·패치·취약점 SLA를 제공한다.

### FR-FIN-14 Vendor·Supply Chain
외부 모델·AI SaaS·오픈소스·데이터·컨테이너·VSIX·패키지의 출처, 계약, 지역, 보존, 하도급, SBOM, 서명, Provenance, Exit Plan을 검증한다.



### FR-FIN-15 Unified Workbench
VS Code·Web·Desktop·CLI에서 동일 Project, Session, Task Graph, Approval, Memory, Checkpoint, Evidence를 사용한다. 화면·모델·Execution Node가 바뀌어도 조건 검산 후 작업을 재개한다.

### FR-FIN-16 Developer Agent
저장소 분석, Patch, Terminal, Test, Harness, 설치, Git 작업을 제공하되 Hunk·명령·네트워크·Git 단계별 승인과 Dirty 변경 보존을 강제한다.

### FR-FIN-17 Work·Artifact Agent
조사·분석·DOCX·PPTX·XLSX·PDF 생성·수정, 템플릿 적용, Native Object 보존, Render·Read-back QA와 출처 추적을 제공한다.

### FR-FIN-18 Orchestration·Memory
개발·조사·문서·설치·검증 Task를 조율하고 프로젝트 Memory, 결정, 제약, 출처, 만료를 관리한다. 검증되지 않은 대화·웹·Repository 정보는 범용 기준으로 자동 승격하지 않는다.

### FR-FIN-19 Tool·Skill·Plugin·Connector
Terminal, Browser, MCP, Skill, Plugin, GitHub, Drive, Mail, Calendar 등 확장 기능을 서명 Manifest, 최소권한, 네트워크·데이터 경계, 공급망 검증과 승인 정책 아래 제공한다.

### FR-FIN-20 Automation
예약·반복·조건 감시를 지원하며 실행 상한, 멱등성, 재시도, 중지조건, 승인 만료, 결과 통지를 Evidence에 결속한다.

### FR-FIN-21 Package·Delivery
설치 파일의 서명·Hash·SBOM·악성코드·라이선스·호환성을 검증하고, 격리 시험 설치, 승인된 설치·설정·기동, 기능·보안·복구 검증과 Rollback Receipt를 제공한다.

### FR-FIN-22 Unified Evidence
사용자 요청→계획→Context→모델·도구→파일·Artifact→명령·설치·Git→시험→개선→독립검증→사람결정의 단일 Parent Hash 계보를 강제한다.

## 6. 수용 원칙

상태는 `DECLARED→DESIGNED→IMPLEMENTED→CONNECTED→TESTED→EVIDENCED→INDEPENDENTLY_VERIFIED→OPERATING_EFFECTIVELY`로 진행한다. 낮은 상태를 높은 상태로 추정하지 않는다.

Final 후보 최소조건:

- 적용 통제·요구·Case·실행 분모 100% 일치
- Positive/Negative/Adversarial/Resilience 모두 실행
- Critical/High 0
- UNKNOWN/NOT_RUN/INCONCLUSIVE/PENDING 0
- 동일 Source/Model/Data/Prompt/Policy/Environment 2회 반복
- 실제 금융 시나리오 3세트 이상
- 외부 AI 제품 유형 5종 이상과 White/Gray/Black-box 검증
- OTester·OAudit 각각 독립 2회 CLEAN
- 보안·준법·업무·Release 사람 승인
- Evidence Pack 독립 Read-back 검산

## 7. 금지된 완료 주장

설계만 존재, 코드만 존재, 테스트 파일만 존재, 명령 종료코드 0, 도구 장애로 미실행, 벤더 자체 보고서, 과거 다른 Commit의 PASS는 Final 증거가 아니다.

현재 전체 금융권 E2E와 독립 검증이 완료되지 않았으므로 `SELF_VALIDATION_NONFINAL / HOLD`, `FinalLock=false`, `Production GO=false`, `Commercial GO=false`를 유지한다.

## 8. 범용 검증 현재 권위

범용 검증의 세부 완료 기준은
`docs/verification/ONSURE_UNIVERSAL_AUTHORITY_AND_COMPLETION_v1.md`를 따른다. 네 단계 Runner,
자동 업무 의미 후보 생성, target provenance binding 코드와 단위 테스트가 존재하더라도 실제
실행 receipt를 대신하지 않는다. 현재 HEAD에 결속된 ONSure 자체, 독립 Python, 독립 Node의
`REAL_REPOSITORY` 네 단계 receipt가 없으므로 세 대상의 검증 상태는 모두 `NOT_RUN`이고 범용성
판정은 `HOLD`다. 저장소 내부 fixture와 과거 provenance 없는 universal evidence set은 이 세
대상의 실행 증거로 집계하지 않는다.
