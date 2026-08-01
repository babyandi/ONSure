# ONSure VS Code Agent·Git Full-Chain 상세 설계

## 1. 목표

사용자는 VS Code에서 Claude Code 계열 도구처럼 자연어로 저장소를 이해시키고 계획·수정·시험·반복·Git 전달을 수행할 수 있어야 한다. ONSure의 차이는 금융권 통제, 독립 검증, Evidence 계보, 최소권한을 실행 경로에서 강제하는 것이다.

## 2. 제품 화면

Activity Bar에 다음 View를 제공한다.

- Chat/Session
- Project & Program Profile
- Asset/Data/AI Inventory
- Requirements & Trace
- Threat Model & Financial Controls
- Verification Plan
- Runs & Live Terminal
- Findings & RCA
- Improvement/Diff/Hunk Approval
- Evidence & Receipts
- Git/Branch/Commit/PR
- Approvals & Exceptions
- Runtime/Cost/Data Egress
- Admin/Policy/Identity

## 3. 작업 모드

| 모드 | 동작 | 승인 |
|---|---|---|
| Ask | 읽기·설명·검색·분석 | 외부/민감 데이터 접근 시 정책 |
| Plan | 변경·시험 계획과 영향 제시 | 실행 전 승인 |
| Act | 승인된 파일·명령·범위 실행 | 파일/Hunk/명령 승인 |
| Verify | 승인된 하네스 실행, 수정 없음 | 파괴적·외부연계 별도 승인 |
| Improve | Finding 결속 Patch·회귀 | Patch 범위 승인 |
| Autopilot | 정해진 정책 안 반복 | Merge/GO/Final 제외 |
| Audit | 읽기 전용 독립 검증 | 증적 접근 권한 |
| Offline | 외부 네트워크·Provider 금지 | 반출입 이중 승인 |

## 4. Claude형 핵심 기능

1. 저장소 전체와 현재 선택·열린 파일·Diff·진단을 Context로 사용
2. AGENTS/정책/프로젝트 규칙의 계층 적용과 충돌 표시
3. 작업을 작은 Step으로 계획하고 진행 상태 복구
4. 파일 Create/Read/Edit/Rename과 Symbol-aware Patch
5. IDE Diagnostic, Test Explorer, Terminal 결과 관찰
6. 컴파일·Lint·시험 실패 시 제한된 반복 수정
7. 장기 작업 Checkpoint/Pause/Resume/Cancel
8. 여러 세션·작업 큐·백그라운드 실행
9. Remote Execution Node와 Heartbeat/Lease/Reconnect
10. 승인 요청 Inbox와 모바일/웹 원격 승인
11. 여러 모델/Provider 선택과 Local LLM 지원
12. Tool/MCP/Extension 권한 Registry
13. 비용·Token·전송 데이터·실행시간 사전/실시간 표시
14. 결과 요약, 변경 근거, 미검증·불확실성 명시

## 5. Context 보안

- Workspace Trust가 없으면 실행·Git write 금지
- 파일 경로 Allow/Deny, Secret Zone, 데이터 등급 적용
- README·Issue·코드·도구 출력의 Prompt Injection을 비신뢰 입력으로 표시
- System/Policy/Approval 메시지와 Repository Content를 분리
- 숨김 파일·상위 경로·Symlink·Submodule 경계 검증
- 선택 영역을 외부 모델에 보내기 전 DLP/개인신용정보 검사
- Context Manifest에 파일 Hash·범위·제외 사유 기록
- 모델 응답은 명령이 아니라 Proposal로 취급

## 6. 명령 실행 보안

Command Plan에는 executable, args, cwd, env key 목록, timeout, network, filesystem scope, expected output, rollback을 포함한다. Shell 문자열 재평가를 피하고 구조화 실행을 우선한다.

- Rootless Sandbox/Container
- Process tree 전체 timeout/kill
- stdout/stderr 동시 drain과 크기 제한
- Network default deny
- Secret broker의 단기 토큰
- 위험 명령 분류
- 삭제·권한·패키지 설치·DB 변경·외부 전송은 강화 승인
- 명령 실패를 테스트 성공/보안 차단으로 오판 금지

## 7. 파일 수정과 승인

Diff는 전체/파일/Hunk/Line 단위로 승인한다. 승인 Bundle은 원본 HEAD, 원본 Blob Hash, Patch Hash, 허용 파일, Finding ID, 만료, 승인자, 서명을 포함한다. 적용 직전 다시 검증한다. 사용자 Dirty 변경은 삭제·덮어쓰기·자동 Stash하지 않는다. 충돌 시 HOLD한다.

## 8. Git Full-Chain

```text
Repository Inspect
→ Dirty/Submodule/LFS/Remote 확인
→ 격리 Worktree 또는 새 Branch
→ 승인 Patch
→ Local Verification
→ Diff Review
→ Commit Approval
→ Signed Commit/Receipt
→ Push 직전 승인 재검산
→ Push
→ Draft PR
→ 독립 OTester/OAudit
→ Human Ready/Merge Decision
```

지원 기능:

- status/diff/log/blame/branch/worktree
- fetch/pull의 영향 사전 표시
- 새 Branch·Worktree 생성
- 선택 Stage/Unstage
- Commit message 제안과 서명
- Remote 설정 검산
- Push 및 Draft PR 생성
- PR 본문에 Finding·시험·Receipt 링크
- Review Comment를 Finding으로 수집
- Rebase/Cherry-pick은 별도 위험 승인
- Tag/Release는 Final Gate 후 사람 승인

금지:

- main 직접 수정
- 사용자 변경 자동 폐기
- 무승인 force push
- AI 단독 PR Approve/Merge
- AI 단독 Production/Commercial GO
- Evidence 불일치 상태의 Ready 주장
- GitHub Actions를 현재 ONSure의 권위 검증기로 사용

## 9. 세션·작업 상태

Session, Task, Step, Tool Call, Approval, Artifact, Receipt를 Event Journal에 기록한다. VS Code 종료·Extension Crash·네트워크 단절 뒤 동일 HEAD와 정책이면 재개한다. 변경되었으면 Rebase가 아니라 Plan Revalidation을 수행한다.

상태: QUEUED/RUNNING/WAITING_APPROVAL/PAUSED/RECOVERING/COMPLETED_NONFINAL/FAILED/BLOCKED/CANCELLED.

## 10. Remote Agent

금융망 내부 Execution Node가 실제 코드·데이터를 보유하고 Control Plane에는 최소 메타데이터만 전송한다. Node는 mTLS, Device Identity, Attestation, Lease, 단기 Credential을 사용한다. 연결이 끊기면 새 명령 수신을 멈추고 안전 Checkpoint 후 Fail-Closed한다.

## 11. Provider/Model 추상화

Provider Adapter는 Local LLM, 사내 Gateway, 허용 Cloud Model을 지원한다. 각 호출에 Model ID/Version, Region, Retention Policy, Training Opt-out, Input Classification, Token/Cost, Prompt/Response Hash를 기록한다. 고위험 기능은 모델 교체 후 기존 PASS를 승계하지 않는다.

## 12. API 경계

VS Code는 Core를 직접 우회하지 않는다.

```text
VS Code Webview/Commands
→ Loopback API Client
→ Identity/Policy/Approval Gateway
→ Core Orchestrator
→ Sandbox/Verification/Git Adapter
→ Evidence Ledger
```

CLI·Web·SDK도 같은 Gateway와 상태기계를 사용한다. UI에서만 가능한 우회 기능을 만들지 않는다.

## 13. 위협과 필수 공격시험

- Repository Prompt Injection이 Secret Exfiltration 유도
- 악성 MCP/Tool이 권한 확대
- Symlink로 Workspace 밖 수정
- Terminal Escape/Command Injection
- Approval Bundle Replay/TOCTOU
- Trusted Key Registry 대체
- Git remote 변경 후 Push
- Draft PR 대신 Ready/Merge 호출
- 숨은 Dirty 변경 덮어쓰기
- Session Resume 시 다른 HEAD 적용
- Remote Node 위장·Lease 경쟁
- 로그에 Secret/개인정보 기록
- 비용 폭주·무한 루프·Process orphan
- Extension Update/VSIX 공급망 변조

각 항목은 Positive 대조군, 실패 사례, 공격 사례, 실행 Receipt를 갖는다.

## 14. 수용 기준

- 13개 View와 Ask/Plan/Act/Verify/Improve/Autopilot/Audit/Offline 구현
- Extension 재시작·Node 단절·VS Code Crash 복구
- 전체/파일/Hunk 승인과 적용 직전 재검증
- 저장소 분석→수정→로컬 시험→Commit→Push→Draft PR 2회 반복
- main write/force push/merge/GO 공격 차단
- Secret·개인신용정보 외부 전송 0
- 모든 Tool Call과 Git 상태전이 Receipt 결속
- OTester/OAudit 독립 CLEAN 전 Final 불가
