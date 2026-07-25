# GitHub Actions 없는 수동 검증 절차

## 운영 결정

GitHub Actions는 ONGuard·ONSure의 검증, 승격, 릴리스 판정에 사용하지 않는다.
검증은 검토자가 고정 커밋과 봉인 아카이브를 로컬 또는 별도 오프라인
검증 환경에서 직접 실행하고, 생성된 Receipt 디렉터리를 보관하는 방식으로
수행한다.

기존 Actions 실행 기록은 과거의 내부 자체검증 참고자료일 뿐이며 현재
게이트의 선행 조건이나 독립 검증 증거가 아니다.

## ONSure와 ONGuard 고정 대상 실행

필수 도구는 JDK 17, Maven, Python 3.12이다.

```bash
bash scripts/run-manual-verification.sh --core
```

이 명령은 ONSure 단위·계약시험, 두 번의 Development Gate, ONGuard 고정
대상의 코어·규칙 하네스·내부 역할분리 E2E를 실행한다. PostgreSQL과
격리망 검증은 실행하지 않으므로 결과는 명시적으로
`CORE_PASS_INFRA_NOT_RUN_HOLD`이며 전체 완료로 계산하지 않는다.

PostgreSQL Append-only 공격시험과 Docker 격리망 외부 Egress 차단까지
실행하려면 검증용 PostgreSQL 접속정보를 환경변수로 제공한 뒤 다음을
실행한다.

```bash
export ONGUARD_PG_HOST=127.0.0.1
export ONGUARD_PG_PORT=5432
export ONGUARD_PG_DB=onguard
export ONGUARD_PG_USER=onguard
export ONGUARD_PG_PASSWORD='검증 전용 암호'
bash scripts/run-manual-verification.sh --full
```

`--full`은 Docker, `pg_isready`, PostgreSQL 접속정보 또는 시험 의존성이
없으면 즉시 실패한다. 누락 항목을 `SKIPPED`나 `PASS`로 바꾸지 않는다.

## 증적과 판정 상한

실행 결과는 `receipts/` 아래에 저장하며 각 실행 디렉터리는
`evidence.sha256`으로 결속한다. 고정 ONGuard 대상은 manifest의 저장소,
커밋, archive SHA-256과 일치해야 한다.

- 내부 수동검증 상한: `SELF_VALIDATION_NONFINAL`
- 독립 OTester: `NOT_RUN`
- 독립 OAudit: `NOT_RUN`
- 도메인 규칙 검출기: `0/295 IMPLEMENTED`, `295/295 PENDING`
- FinalLock·Production GO·Commercial GO: 금지

외부 독립 실행은 이 저장소와 이해관계가 분리된 실행 주체가 같은 봉인
대상을 두 번 실행하고, Critical·Major 결함 0건과
`fixture → harness → oracle → result → evidence` 계보를 제출할 때까지
완료로 인정하지 않는다.
