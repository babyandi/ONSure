# ONSure Public SDK v1

## 지원 경계

외부 Java 소비자는 `io.onsure:onsure-sdk`의
`io.onsure.sdk.v1.ONSureSdkV1`만 지원 API로 사용한다. `io.onsure.platform`은
제품 내부 구현이며 호환성 또는 외부 사용을 보장하지 않는다.

SDK v1은 다음 강타입 요청을 제공한다.

- Workspace, Project, Target 등록
- 등록 Target의 Program Learning
- 등록 Target과 Program Profile에 결속된 Plan 생성
- 서명된 Plan 승인
- 승인 Bundle 선택 소비를 포함한 Validation

모든 호출은 CLI, Loopback API, VS Code와 같은 `LocalWorkflowDispatcher`를
통과한다. SDK에는 임의 Operation/JSON Dispatch가 없다.

## 권위와 경로

호출자는 다음 경로를 선택할 수 없다.

- Trusted Key Registry
- Approval Replay Ledger와 외부 Anchor
- Approval Authority Root
- Program Profile, Plan, Validation Store 등 제품 상태 출력 위치

승인 API는 원본 Plan과 서명 Receipt만 받는다. 신뢰키와 Replay Ledger는
Workspace에 대응하는 제품 권위에서 내부적으로 결정한다. Validation의 승인
Bundle도 승인 Plan, 원본 Plan, 서명 Receipt만 받으며 권위 파일을 받지 않는다.

## 비최종 보증

모든 `Response`는 `SELF_VALIDATION_NONFINAL`, 독립 OTester/OAudit 상태와
`finalClaimAllowed=false`를 포함한다. SDK는 Merge, FinalLock, Production GO,
Commercial GO 권한을 제공하지 않는다.

```java
ONSureSdkV1 sdk = new ONSureSdkV1(workspaceRoot);
sdk.registerWorkspace(new ONSureSdkV1.WorkspaceRegistration("ws-1", "Workspace"));
sdk.registerProject(new ONSureSdkV1.ProjectRegistration("ws-1", "project-1", "Project"));
```

현재 실제 외부 Registry 배포와 독립 Consumer 환경 시험은 수행하지 않았으며,
그 전까지 SDK 배포 준비 상태는 비최종이다.
