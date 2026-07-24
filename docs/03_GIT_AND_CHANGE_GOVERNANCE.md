# ONSURE Git 및 변경관리 기준

## 1. 목적

ONSURE은 대상 프로그램을 학습하고 검증하며 보완 개발한 결과를 실제 저장소 변경으로 안전하게 전달해야 합니다. Git 관리는 부가기능이 아니라 제품의 핵심 실행 경로입니다.

```text
학습 기준선
→ 작업 계획
→ 격리 Branch/Worktree
→ 수정
→ 테스트·재검증
→ 변경 검토
→ Commit
→ Push
→ Draft PR
→ 승인·Merge 또는 Rollback
```

## 2. 기본 원칙

- 기본 Branch에 직접 수정하지 않습니다.
- 모든 실행은 Source SHA와 Branch 기준선을 고정합니다.
- 학습 중인 기준선과 수정 중인 작업공간을 분리합니다.
- 사용자의 기존 미커밋 변경을 임의로 삭제·덮어쓰기·stash하지 않습니다.
- ONSURE이 생성한 변경과 기존 사용자 변경을 구분합니다.
- Commit과 PR은 검증 결과 및 Evidence를 연결합니다.
- Merge는 별도 권한이며 기본 자동 수행하지 않습니다.

## 3. 저장소 접수

접수 시 다음을 확인합니다.

- 원격 저장소 URL과 Provider
- 기본 Branch
- 현재 HEAD SHA
- 추적·미추적·Staged 변경
- Submodule 및 LFS
- Branch 보호 규칙
- CI 상태
- 사용 가능한 Build/Test 명령
- Secret 및 권한 범위

작업공간이 깨끗하지 않으면 다음 중 하나를 선택합니다.

```text
READ_ONLY 분석
새 Worktree 생성
사용자 변경을 포함한 명시적 기준선 생성
HOLD
```

ONSURE이 임의로 Reset 또는 Force Push하지 않습니다.

## 4. Branch 전략

권장 Branch 명명:

```text
onsure/learn-<project>-<date>
onsure/verify-<finding>-<date>
onsure/improve-<finding>-<date>
onsure/regression-<release>-<date>
```

하나의 Branch는 하나의 명확한 개선 목적을 가집니다. 서로 독립적인 결함은 가능한 한 분리합니다.

## 5. Commit 계약

Commit은 다음 정보를 포함합니다.

```text
유형: learn | verify | fix | improve | test | docs | policy
대상 Finding 또는 Improvement ID
변경 목적
검증 상태
Evidence 위치
```

예시:

```text
fix(agent): prevent unauthorized tool execution

Finding: F-SEC-014
Verification: PASS
Evidence: .onsure/evidence/run-20260724-014.json
```

검증되지 않은 변경은 `NON_FINAL` 상태로 표시합니다.

## 6. Diff 및 Patch 검토

ONSURE은 적용 전후 다음을 제공합니다.

- 변경 파일 목록
- Line Diff
- 구조적 변경 요약
- API·DB·Prompt·RAG 영향
- 삭제·이동·권한 변경
- 위험도
- 테스트 영향
- Rollback 방법

사용자는 전체 승인, 파일별 승인, Hunk별 승인, 거부를 선택할 수 있습니다.

## 7. PR 생성

Draft PR 본문에는 최소 다음이 포함됩니다.

```text
문제와 재현 조건
근본원인
변경 내용
변경하지 않은 범위
검증 명령과 결과
Before/After 비교
알려진 위험과 미검증 항목
Rollback 방법
Evidence 링크 또는 Hash
```

검증 미완료 상태에서는 Ready PR 또는 Merge Ready를 주장하지 않습니다.

## 8. CI 및 재검증

Push 후 로컬 결과만으로 최종 PASS를 유지하지 않습니다. 원격 CI 결과를 수집해 로컬 Evidence와 비교합니다.

```text
Local PASS
→ Push
→ Remote CI
→ 동일 Commit 확인
→ 결과 수집
→ 불일치 시 HOLD
→ 일치 시 MERGE_READY 후보
```

## 9. Merge 정책

Merge 모드:

- Manual: 사용자 또는 조직 담당자가 Merge
- Guarded: 필수 Gate 통과 후 사용자가 최종 승인
- Conditional Auto Merge: 저위험 변경과 사전 정책이 모두 충족된 경우

다음은 자동 Merge 금지 기본 항목입니다.

- 인증·권한·암호화
- 데이터 삭제·Migration
- 기준선·정책 변경
- 모델 또는 핵심 Provider 교체
- Secret·네트워크 권한 확대
- 고위험 공급망 변경
- 미해결 치명·중대 Finding 존재

## 10. Rollback

모든 적용 변경은 다음 중 하나 이상의 복구 포인터를 가져야 합니다.

- 이전 Commit SHA
- Revert Commit 계획
- 설정·Prompt·Dataset 이전 버전
- DB Migration Down 또는 복구 절차
- 배포 Artifact 이전 버전

Rollback 후에도 회귀검증을 다시 수행합니다.

## 11. Git Provider

초기 지원 우선순위:

1. GitHub
2. GitLab
3. Bitbucket
4. 사내 Git 서버

Core Git 기능은 Provider와 독립적으로 구현하고 PR·Review·CI 연동은 Adapter로 분리합니다.

## 12. 완료 기준

다음 Full-Chain이 실제 저장소에서 재현되어야 합니다.

```text
Dirty Workspace 보호
→ 전용 Worktree/Branch
→ Patch 적용
→ Test PASS
→ Commit
→ Push
→ Draft PR
→ CI 결과 회수
→ Evidence 결속
→ Merge 또는 Rollback
```
