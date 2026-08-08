# ONSURE Approval Authority 물리적 분리 Gap v1

## 판정

```text
P0 / HOLD
```

## 확인된 문제

현재 제품 Workflow는 요청자가 Trusted Key Registry와 Replay Ledger 경로를 지정하지 못하도록 차단하고 `.onsure/approval-authority/`의 고정 경로를 사용한다.

그러나 이 경로는 검증 대상 Workspace 내부에 있다. 따라서 다음 권한이 물리적으로 분리되지 않는다.

```text
대상 Source 변경 권한
Approval Trusted Key 변경 권한
Approval Replay Ledger 변경 권한
```

고정된 경로는 Caller-selected path 공격을 차단하지만, 대상 Workspace 자체를 변경할 수 있는 공격자·도구·사용자로부터 Trust Root를 분리하지는 못한다.

## 이전 검증이 놓친 이유

- Path Override 차단 여부만 검사했다.
- Authority 파일의 물리적 소유권과 Source Root 외부 저장 여부를 검사하지 않았다.
- Replay Ledger Hash Chain을 Trust Anchor로 오해했다.
- 외부 Anchor·OS 권한·별도 저장소가 없는 상태를 명시적 HOLD로 만들지 않았다.

## 필요한 수정

- Approval Authority Home을 대상 Workspace 밖의 ONSure 전용 사용자/운영자 저장소로 이동
- Workspace Identity별 Authority Namespace
- OS 소유권과 권한 검증
- Key Registry 및 Replay Ledger의 Symlink·Hardlink·Replacement 차단
- Replay Ledger Head의 외부 Anchor
- Workspace 전체를 교체해도 Authority가 유지되는 복구시험
- Cross-workspace Authority substitution 공격시험

## 현재 안전 상한

이 Gap이 닫히기 전까지 다음을 금지한다.

```text
Public SDK approval path
Production approval authority
Commercial approval authority
FinalLock
```

현재 Local Workflow의 고정 경로 구현은 `SELF_VALIDATION_NONFINAL`에서만 사용한다.
