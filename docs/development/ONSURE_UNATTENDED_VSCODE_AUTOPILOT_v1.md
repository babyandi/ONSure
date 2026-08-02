# ONSure 무인 VS Code Autopilot

## 목표

VS Code에서 한 번 시작한 뒤 ONSure가 체크포인트를 기준으로 안전하게 재개하고,
검증 가능한 다음 작업을 계속 수행한다. 완료 표식만 믿지 않고 Source SHA, 명령,
종료 코드, 출력 Digest와 필수 PASS 표식을 Receipt에 결속한다.

## 실행

VS Code에서 `Tasks: Run Task`를 열고 다음 작업을 실행한다.

- `ONSure: Autopilot Start or Resume`
- `ONSure: Autopilot Status`
- `ONSure: Resume After RCA`

터미널에서는 다음 명령과 같다.

```bash
python3 scripts/onsure-autopilot.py resume
python3 scripts/onsure-autopilot.py pause
python3 scripts/onsure-autopilot.py cancel
python3 scripts/onsure-autopilot.py status
```

VS Code나 PC가 중단되어도 같은 명령이 마지막 PASS 다음 단계부터 재개한다.
동시에 두 실행이 시작되면 파일 잠금으로 두 번째 실행을 차단한다.

## 계속 진행 범위

1. 로컬 환경 사전검사
2. 범용 하네스 사전검사
3. 전체 로컬 검증 1회
4. 서로 독립된 전체 로컬 검증 2회
5. 개발 Gate
6. 실행 Receipt와 체크포인트 봉인
7. 독립검증 조건 충족 시 `MERGE_AUTHORIZED_READY` 판정

실패한 단계는 최대 3회 실행하고, 실패 증적을 보존한 뒤
`BLOCKED_RCA_REQUIRED`로 표시한다. RCA로 코드나 환경이 바뀌면 이전 Source
체크포인트를 재사용하지 않으며 새 기준선에서 처음부터 검증해야 한다.

## 자동으로 하지 않는 작업

- Final PASS, Final Audit PASS, FinalLock
- Production GO 또는 Commercial GO
- 실제 고객 데이터 학습
- 비용 발생 외부 서비스
- 비밀정보 반출
- force push, hard reset, 임의 stash

이 항목은 실행 설정에 추가해도 명령 문자열 검사와 Human Gate에 의해 차단된다.

PR 승인·병합은 2026-07-25 사용자의 명시적 지시에 따라 허용된다. 단, 자기
검토를 독립 승인으로 표시하지 않으며 OTester·OAudit 별도 프로세스, 2회 회귀,
최신 PR HEAD 바이트 결속, 최신 main 수렴, 미해결 리뷰 0건과 P0/P1 0건이
모두 증명된 경우에만 실행한다.

## 상태 원장

- `status/implementation-status.v1.json`
- `status/verification-status.v1.json`
- `status/remaining-work-register.v1.json`

실행 중 가변 상태와 Receipt는 `.onsure/autopilot/`에 저장한다. 이 디렉터리는
로컬 실행 증적이며 Git에 커밋하지 않는다.
