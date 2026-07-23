# ONSURE Universal Verification Harness v1 Runbook

## 기준선

- 제품 Core 기준선: PR #2 `design/assurance-architecture-v1`
- 문서 기준선: PR #6 `docs/onsure/review/`
- 원 Harness 구현: PR #7
- 통합 브랜치: `integration/product-core-universal-harness-v1`
- 런타임: JDK 17, Maven
- 현재 상태: `INTEGRATED / NOT_RUN`
- Final Lock: 금지

## Codespace 후순위 원칙

현재 Codespace는 별도로 구성 중이며, ONSURE 구현 작업의 선행 조건으로 사용하지 않는다.

```text
CODESPACE_STATUS = CONFIGURING
CODESPACE_PRIORITY = DEFERRED_TO_FINAL_EXECUTION_STAGE
CURRENT_WORK_MODE = GITHUB_REPOSITORY_INTEGRATION_AND_STATIC_REVIEW
EXECUTION_STATUS = NOT_RUN
```

Codespace가 준비되기 전에는 다음 작업을 우선한다.

1. 계약·모델·스키마 명칭과 필드 일관성 검토
2. 30개 검증 축의 Fixture·Oracle·Evidence·Receipt 커버리지 보강
3. Harness known-good·known-bad·timeout·변조 Meta-Test 확대
4. `NOT_RUN/BLOCKED`의 PASS 승격 우회 경로 제거
5. RCA → 수정 → 독립 Regression 2회 Gate 보강
6. PR #2 제품 Core와 Universal Harness의 추적성 확인
7. 실행 명령과 결과 파일 경로의 정적 정합성 검토

Codespace가 준비되기 전에는 아래 상태를 변경하지 않는다.

```text
MAVEN_COMPILE = NOT_RUN
JUNIT = NOT_RUN
SINGLE_HARNESS_RUN = NOT_RUN
INDEPENDENT_RUN_1 = NOT_RUN
INDEPENDENT_RUN_2 = NOT_RUN
FINAL_CANDIDATE = BLOCKED
FINAL_LOCK_ALLOWED = false
```

## 구조

```text
harness/universal-v1/
├─ axes/verification-axes.v1.json
├─ schemas/
├─ oracles/default-oracles.v1.json
├─ rca/rca-template.v1.json
└─ status/current-status.v1.json
fixtures/universal-v1/sample-target/
src/main/java/io/onsure/harness/
src/test/java/io/onsure/harness/
scripts/
```

## 단일 실행

Codespace 또는 clean JDK 17 환경이 준비된 최종 실행 단계에서만 수행한다.

```bash
bash scripts/run-universal-harness.sh <operator-id> <environment-label>
```

생성물:

```text
receipts/universal-v1/runs/<run-id>/
├─ logs/
├─ evidence/
├─ receipts/
├─ rca/                       # 실제 FAIL이 있을 때
├─ run-summary.json
├─ evidence-manifest.sha256
└─ run-receipt.json
```

판정 우선순위:

```text
FAIL > BLOCKED > NOT_RUN > PASS
```

`NOT_RUN`, `BLOCKED`, Evidence 누락, Hash 불일치는 PASS로 승격할 수 없습니다.

## 두 번 독립 실행

```bash
bash scripts/run-universal-harness-twice.sh \
  operator-independent-1 operator-independent-2 local-jdk17
```

두 실행은 다음을 만족해야 합니다.

- Run ID와 운영자 ID가 서로 다름
- 환경 Digest 동일
- 30개 축 전부 PASS
- `NOT_RUN=0`, `BLOCKED=0`
- Critical·Major 결함 0
- 정상화 결과 Digest 동일
- 각 실행 Evidence Manifest와 Receipt 재검증 PASS

성공해도 결과는 `Final Candidate`일 뿐이며 `final_lock_allowed=false`입니다.

## 실패와 Regression

실패 Fixture는 자동으로 `rca/<fixture-id>.json`을 생성하며 초기 상태는 다음과 같습니다.

```text
root_cause=PENDING_ANALYSIS
fix_reference=NOT_SET
regression_run_1=NOT_RUN
regression_run_2=NOT_RUN
status=RCA_PENDING
```

수정 후:

```bash
java ... io.onsure.harness.HarnessCli regression \
  <baseline-run> <regression-run-1> <regression-run-2> <output-json>
```

두 회귀 실행이 독립적이고 모두 clean일 때만 Regression Receipt가 PASS입니다.

## 실행 전 검사

Codespace가 준비된 최종 실행 단계에서 수행한다.

```bash
bash scripts/preflight-universal-harness.sh
mvn -B -ntp test
```

검사 범위:

- 범용 축 30개 고정
- 7종 Fixture가 모든 축을 커버
- Oracle 미등록 차단
- 명령 실행 경계와 timeout
- Evidence/Receipt Hash 계보
- 증적 변조 차단
- 독립 실행 운영자 분리
- RCA 생성
- 두 번의 clean Regression
- 자동 Final Lock 금지

## 금지

- Codespace 준비를 기다리며 구현을 중단
- 실행하지 않은 결과를 PASS로 기록
- 일부 축을 생략하고 Final Candidate 선언
- 동일 운영자·동일 Run 재사용을 독립 실행으로 인정
- Evidence 파일과 Receipt를 함께 바꿔 우회
- RCA_PENDING 상태에서 종료
- 자동 Final Lock 생성
- ORUDA-Master-Queue 사용
