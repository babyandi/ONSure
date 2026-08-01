# ONSure 통합 AI Work·Developer·Assurance 설계

- 문서 지위: 최종 제품 통합 작업면 정본
- 대상 기능군: Codex형 개발 Agent, Claude형 VS Code Agent, ChatGPT Work형 범용 업무 Agent
- 구현 원칙: 기능 요구의 클린룸 재설계이며 타사 코드·식별자·내부 구현 복제 금지
- 판정: NORMATIVE TARGET / 구현 완료 증거 아님

## 1. 통합 목표

ONSure는 세 종류의 기능을 별도 제품처럼 오가는 방식이 아니라 하나의 프로젝트, 하나의 대화, 하나의 계획, 하나의 권한, 하나의 실행 기록과 Evidence 계보에서 제공해야 한다.

사용자는 같은 채팅 세션에서 다음 흐름을 중단 없이 수행할 수 있어야 한다.

```text
외부 조사·자료 수집
→ 요구사항·설계서·보고서 작성
→ 저장소 분석·코드 수정
→ 의존성·제품 설치
→ 빌드·시험·보안검증
→ Git Branch·Commit·Push·Draft PR
→ DOCX·PPTX·XLSX·PDF 산출
→ 승인·독립검증·감사 Evidence Pack
```

## 2. 통합과 분리의 원칙

### 2.1 반드시 하나여야 하는 것

- Project/Workspace Identity
- Conversation/Task/Step 상태
- 계획과 사용자 지시의 우선순위
- 승인 Inbox와 Approval Bundle
- 프로젝트 Memory와 Context Manifest
- Tool/Connector/Model Registry
- 파일·Artifact·Git 변경 이력
- 비용·Token·시간·외부 전송 가시성
- Event Journal, Receipt, Evidence Ledger
- Pause/Resume/Checkpoint/Recovery

### 2.2 보안상 분리해야 하는 것

- 개발 실행과 독립 OTester/OAudit
- 문서 생성과 최종 사실·시각 품질 검수
- 인터넷/Connector 접근과 내부망 원문 처리
- 모델 추론과 실제 명령 실행
- Patch 생성과 Merge/Release 승인
- 설치 실행과 운영 배포 승인
- 프로젝트 Memory와 검증된 범용 지식
- 사용자 업무 세션과 감사자 읽기 전용 세션

통합은 권한 경계를 제거하는 것이 아니다. 사용자 경험과 상태 계보는 통합하되 실행 Trust Domain은 분리한다.

## 3. 기능 계층

| 계층 | ONSure 구성요소 | 통합 역할 |
|---|---|---|
| 작업면 | ONWorkbench | VS Code·Web·Desktop·CLI의 동일 채팅·계획·상태 |
| 개발 | ONDeveloper | 저장소 이해, Patch, Terminal, Test, Git |
| 범용업무 | ONWork | 조사, 분석, 문서·보고서·프레젠테이션·데이터 업무 |
| 조율 | ONOrchestrator | Intent 분해, Agent 선택, Task Graph, 장기작업 |
| 기억 | ONMemory | 프로젝트 상태, 결정, 제약, 출처, 만료와 승격 |
| 산출물 | ONArtifact | DOCX·PPTX·XLSX·PDF·이미지 생성과 Read-back QA |
| 조사 | ONResearch | 승인된 웹 조사, 출처·인용·수집시각·근거 추적 |
| 도구 | ONTool | Terminal, Browser, MCP, Skill, Plugin의 공통 Registry |
| 연결 | ONConnector | GitHub, Drive, Mail, Calendar, Slack 등 업무 시스템 |
| 자동화 | ONAutomation | 예약, 반복, 조건 감시, 재시도와 통지 |
| 실행 | ONExecution Node | 로컬·Codespaces·사내망·폐쇄망 실제 실행 |
| 설치 | ONPackage·ONDelivery | 패키지 검증, 설치, 설정, 기동, 롤백 |
| Git | ONGit | Worktree, Branch, Stage, Commit, Push, Draft PR |
| 통제 | ONPolicy | IAM, DLP, 승인, 명령·파일·네트워크 경계 |
| 증적 | ONEvidence | 전체 Task·Tool·Artifact·Git·승인의 Hash 계보 |
| 검증 | ONVerification | 기능·보안·품질·복원력 검증과 독립 검증 연결 |

모든 내부 프로그램 명칭은 ON 계열을 사용한다. ORUDA 연계는 ONSure Validator와 Evidence Exchange Contract를 통해 수행하며 직접 런타임 결합으로 독립성을 훼손하지 않는다.

## 4. 단일 세션 상태 모델

`ONSession`은 다음 객체를 결속한다.

- Session ID, Project ID, Workspace/Repository ID
- User/Role/Device/Execution Node Identity
- Goal, Constraints, Definition of Done
- Task Graph와 Step별 상태
- 현재 HEAD, Dirty State, Artifact Version
- 사용 모델·Provider·Prompt/Response Hash
- 읽은 파일·URL·메일·업무자료의 Context Manifest
- Tool Call, Command, Network, File Change
- Approval Request/Decision/Expiry/Scope
- Finding, RCA, Patch, Test Case, Receipt
- 비용·Token·시간·저장량·외부 전송량
- Checkpoint와 Resume Preconditions

상태는 `QUEUED/RUNNING/WAITING_APPROVAL/PAUSED/RECOVERING/COMPLETED_NONFINAL/FAILED/BLOCKED/CANCELLED`을 사용한다. 화면이나 모델을 바꿔도 동일 Journal을 이어간다.

## 5. 통합 작업 모드

| 모드 | 주요 기능 | 쓰기 범위 |
|---|---|---|
| Ask | 코드·문서·자료 질의, 설명, 비교 | 읽기 전용 |
| Research | 웹·연결자료 조사, 출처표 작성 | 조사 노트·후보만 |
| Plan | 개발·문서·설치·검증 통합 계획 | 계획만 |
| Create | 문서·표·슬라이드·코드 후보 생성 | 승인된 Workspace |
| Act | 파일·명령·설정 변경 | 승인 Bundle 범위 |
| Verify | 시험·Read-back·보안검증 | 제품 변경 금지 |
| Improve | Finding 결속 수정과 회귀 | 격리 Worktree |
| Automate | 예약·반복·조건 감시 | 사전 정책 범위 |
| Audit | Evidence 독립 검산 | 읽기 전용 |
| Offline | 외부 모델·네트워크 금지 | 내부 Node만 |

사용자는 한 세션에서 모드를 전환할 수 있으나 고위험 권한은 모드 전환만으로 확대되지 않는다.

## 6. 개발 기능

- 저장소·Symbol·의존성·설계·Issue·PR 분석
- 선택 영역·열린 파일·Diff·진단 Context 사용
- Plan 기반 파일 생성·수정·이름 변경
- Hunk 단위 승인과 적용 직전 Hash 재검산
- Terminal, Build, Lint, Test, Harness 실행
- 실패 분석, 제한된 반복 수정, 동일 Fixture 회귀
- 프로그램·의존성 설치 계획과 격리 시험 설치
- Git status/diff/log/blame/branch/worktree/stage/commit/push
- Signed Commit, Draft PR, Finding·Receipt 연결
- 장기 작업, 병렬 Worktree, Checkpoint·복구
- Local LLM·사내 Gateway·허용 Cloud Model 선택

`main` 직접 수정, 무승인 Force Push, AI 단독 Approve/Merge/Release/GO/FinalLock은 금지한다.

## 7. 범용 업무 기능

- DOCX·PPTX·XLSX·PDF 생성·수정·변환
- 첨부파일·표·이미지·데이터 분석
- 보고서·사업계획서·제안서·감사자료 작성
- 기업 템플릿·브랜드·용어·작성규칙 적용
- 산출물 미리보기, 페이지/슬라이드 단위 수정
- Native Object·수식·차트·링크·접근성 보존
- 렌더·Read-back으로 잘림·겹침·폰트·내용 손실 검증
- 외부 조사와 URL·게시일·수집일·근거 문장 추적
- 연결 시스템 자료 검색·요약·초안 작성
- 예약 브리핑·조건 감시·반복 보고
- 승인된 Skill/Plugin의 저장·버전·재사용

외부 전송, 이메일 발송, 공유 권한 변경, 일정 생성, 공개 게시 같은 사람·외부 대상 행위는 대상과 내용을 재확인하고 명시 승인 후 실행한다.

## 8. Agent 조율

`ONOrchestrator`는 요청을 개발, 조사, 문서, 데이터, 설치, 검증 Task로 분해한다. 각 Task는 다음 계약을 갖는다.

- 입력과 기대 산출물
- 사용 가능 모델·Tool·Connector
- 파일·데이터·네트워크 범위
- 시간·비용·반복 상한
- 승인 지점과 중지 조건
- 검증 Oracle과 증적 요구
- 실패 시 Rollback/Recovery
- 다른 Task에 전달할 Artifact Hash

여러 Agent가 병렬 수행해도 동일 파일을 무승인 동시 수정하지 않는다. 충돌은 자동 덮어쓰지 않고 HOLD한다.

## 9. 모델 독립성

Codex형·Claude형·Work형은 모델명이 아니라 Capability Profile이다. ONSure는 모델을 교체할 수 있어야 하며 각 모델에 다음을 선언한다.

- 지원 Context·Tool Calling·멀티모달·코딩·문서 기능
- 배포 위치·Region·보존·학습 사용 여부
- 데이터 등급별 허용 여부
- 비용·성능·신뢰도·Fallback 정책
- 알려진 제한과 필요한 독립 검증

모델 교체 후 고위험 업무의 기존 PASS는 자동 승계하지 않는다. Provider 장애 시 더 약한 모델로 조용히 강등하지 않고 재계획 또는 HOLD한다.

## 10. Tool·Skill·Plugin·Connector

모든 확장 기능은 `ONTOOL-MANIFEST`를 가져야 한다.

- Publisher, Version, Signature, Hash, SBOM
- 요구 권한과 데이터 접근 범위
- Network Destination과 보존 정책
- 지원 명령과 부작용
- 승인 등급, 시간·비용 상한
- 설치·업데이트·삭제·Rollback
- Test Evidence와 취약점 상태

도구의 자연어 출력과 Repository 문서는 비신뢰 입력이다. System Policy나 Approval을 변경할 수 없다.

## 11. 설치·실행 통합

채팅 요청에서 설치까지 다음 계보를 강제한다.

```text
Package 식별
→ 서명·Hash·SBOM·악성코드·라이선스 검사
→ 호환성·권한·네트워크·Rollback 계획
→ 승인
→ 격리 환경 시험 설치
→ 승인된 Node 설치·설정·기동
→ 기능·보안·성능·복구 검증
→ 설치 Receipt
```

관리자 권한, 방화벽, 운영 DB, 인증서·키, 보안 Agent, 운영 배포는 강화 승인과 작업자·승인자 분리가 필요하다.

## 12. 공통 Evidence 계보

```text
User Request
→ Goal/Plan
→ Context Manifest
→ Model/Tool Calls
→ Source/Artifact Changes
→ Command/Install/Git Operations
→ Test and Read-back
→ Finding/RCA/Improvement
→ Independent OTester/OAudit
→ Human Decision
→ Delivery Receipt
```

각 단계는 Parent Hash, Identity, Timestamp, Policy Version, Environment, Source/Artifact Hash를 포함한다. 중간 객체가 1바이트라도 달라지면 후속 PASS와 승인을 재사용할 수 없다.

## 13. P0 통합 수용 기준

- VS Code와 Web에서 동일 Session·Task·Approval·Evidence 상태 확인
- 조사→문서→코드→설치→시험→Git→최종 산출물 E2E 3세트
- 화면·모델·Execution Node 전환 후 Checkpoint 복구
- 파일/Hunk/명령/외부행위별 권한 범위 강제
- 프로젝트 Memory와 범용 지식의 오염·승격 공격 차단
- 악성 Repository·문서·웹·Tool의 Prompt Injection 차단
- Secret·개인신용정보 무승인 외부 전송 0
- DOCX·PPTX·XLSX·PDF Native/Render/Read-back 검증
- Branch→Commit→Push→Draft PR 2회 반복
- 패키지 시험 설치·실패 Rollback·재검증
- Tool/Skill/Plugin/Connector 공급망 검증
- 모든 단계 Receipt 및 Parent Hash 변조 차단
- OTester·OAudit 각각 독립 2회 CLEAN
- Critical/High, UNKNOWN, NOT_RUN, PENDING 0

충족 전 `FinalLock=false, Production GO=false, Commercial GO=false`를 유지한다.
