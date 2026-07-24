# ONSure for VS Code 구독 정책

## 1. 목적

VS Code 서비스는 일회성 납품이 아니라 계속 변경되는 프로그램을 증분 학습·반복 검증·지속 개선하는 개발환경이다.

## 2. 사용자 흐름

```text
로그인
→ OLicense 구독 조회
→ Organization·System·Program 선택
→ Workspace Binding
→ 최초 또는 증분 학습
→ 검증·Finding·RCA
→ 개선계획·승인
→ Patch·회귀검증
→ Commit·Push·Draft PR
→ Memory·Evidence 갱신
```

## 3. Plan

### Developer

- 개인 또는 소규모 팀
- 제한된 Seat·Active System·Program Capacity
- Program Learning, Verification, Patch, Branch·Commit·Draft PR
- 제한된 Autopilot·월간 Credit

### Team

- 공유 Credit Pool
- 팀 승인·역할·정책
- CI/CD·PR Review 연계
- 다중 System·Program
- 공유 Program/Failure/Improvement Memory

### Enterprise

- 계약형 Seat·System·Program·Credit
- SSO·RBAC·감사·보존정책
- 온프레미스·폐쇄망·전용 Model Provider
- 정책팩·전용 Support·SLA

### Unlimited Systems & Programs

등록·활성화 가능한 System과 Program 수 제한을 제거하는 옵션이다. Seat, Credit, 동시 실행, Compute, Storage, 전문가 지원은 별도 계약한도를 적용한다.

## 4. Entitlement 항목

- SeatLimit
- DeviceLimit
- ActiveSystemLimit
- ProgramLimit 또는 ProgramCapacity
- MonthlyONSureCredit
- ConcurrentRunLimit
- StorageLimit
- RetentionDays
- Learning·Verify·Improve 권한
- ApplyPatch·Commit·Push·CreatePR 권한
- Autopilot Level
- CI/CD·CustomPolicy·Offline 권한
- SupportLevel

## 5. Workspace·System·Program Binding

- 사용자는 OLicense가 허용한 Organization 범위에서 System과 Program을 활성화한다.
- Workspace는 하나 이상의 Repository를 포함할 수 있다.
- Program은 하나 이상의 Repository·Runtime·Deployment를 참조할 수 있다.
- 동일 Program의 개발·시험·운영 환경은 Program 수를 늘리지 않지만 환경별 검증 사용량은 Credit으로 계산한다.
- 비활성 Program은 이력 열람만 허용하고 신규 실행을 차단할 수 있다.

## 6. Credit 정책

고객에게는 ONSure Credit 하나를 표시하고 내부적으로 Learning, Verification, AI Model, Sandbox, Improvement, Storage 원가를 측정한다.

초과정책:

- HARD_STOP
- AUTO_TOP_UP
- PAY_AS_YOU_GO
- ADMIN_APPROVAL_REQUIRED

기본값은 HARD_STOP이다.

ONSure 플랫폼 장애와 내부 Worker 오류로 인한 재실행은 차감하지 않는다. 고객 코드의 정상적인 빌드 실패 판정, 고객 환경 오류, 승인 후 수행한 분석은 계약정책에 따라 차감한다.

## 7. 기능 Gate

기능 숨김만으로 통제하지 않는다.

```text
VS Code UI
→ Local Runtime
→ ONSure Service
→ OLicense Entitlement
```

각 실행 시작과 고위험 단계 전에 Entitlement·잔여 Credit·System/Program Binding을 재검증한다.

## 8. Offline·Grace

- Enterprise 계약에 한해 Offline Snapshot을 지원할 수 있다.
- Snapshot에는 만료일, Feature, Limit, KeyId, Revocation Version을 서명한다.
- Clock rollback, Snapshot 복제, Device Binding 위조를 방어한다.
- Grace 기간에는 위험 기능과 고비용 실행을 제한할 수 있다.
- 만료 후 읽기·Evidence Export만 허용하고 수정·Push·PR은 차단하는 정책을 지원한다.

## 9. 웹 Case와 전환

- 웹 Learn/Learn & Verify 결과를 동일 고객의 VS Code 초기 Program Profile로 이전할 수 있다.
- 동일 기준선·유효기간 내 이전 시 전체 재학습을 강제하지 않고 증분 학습부터 Credit을 사용한다.
- VS Code의 Finding에 대해 전문가 최종 검증이 필요하면 별도 Web Professional Case로 전환한다.

## 10. 해지·다운그레이드

- 해지 시 계약 종료일까지 사용 가능
- 다운그레이드로 System·Program 한도를 초과하면 선택된 대상만 활성 유지
- Evidence·보고서 Export 기간 제공
- 보존기간 후 고객 정책에 따라 삭제
- 미사용 선불 Credit의 환불·소멸은 계약조건에 따른다.
