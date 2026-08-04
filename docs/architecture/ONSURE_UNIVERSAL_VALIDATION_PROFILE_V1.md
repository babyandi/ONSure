# ONSURE 범용 Validation Profile 설계 v1

## 1. 목적

ONSURE은 외부 프로그램에 ONSURE 전용 파일이나 특정 제품 연결을 요구하지 않고
언어·빌드·테스트·API·DB·운영 특성을 탐지한다. 탐지 결과는 실행 가능한
`Validation Profile`과 실행하지 못한 검사의 `NOT_RUN` 사유로 변환한다.

외부 제품은 ONSURE의 필수 구성요소, 기준 검증 대상 또는 완료 조건이 아니다.

## 2. 네 단계 보증 수준

| 차수 | 식별자 | 필수 증거 |
|---|---|---|
| 1차 | `STRUCTURE_STATIC` | 소스 잠금, 언어·빌드·모듈·API·DB·배포 Inventory, 정적 검사 |
| 2차 | `COMPONENT_AND_NEGATIVE` | 실제 build·unit·component·negative 실행과 종료코드·출력 digest |
| 3차 | `END_TO_END_LINEAGE` | 실제 entrypoint, producer output과 consumer input의 schema·digest·artifact 연결 |
| 4차 | `OPERATIONAL_RESILIENCE` | 격리 Ubuntu 환경의 성능·동시성·장애·재시작·복구·migration·backup/restore |

하위 차수의 통과는 상위 차수의 통과를 의미하지 않는다. 적용 가능한 Step이 없거나
실행 증거가 없으면 해당 차수는 반드시 `NOT_RUN`이다.

## 3. 일곱 개 순차 검증군

모든 Validation Pack은 아래 순서를 유지한다. 특정 Framework에서 사용하는 Renderer,
Tester, Auditor 이름은 Pack의 역할 선언으로 받으며 ONSURE Core의 제품명이나 의존성으로
고정하지 않는다.

| 순서 | 식별자 | 검사 내용 |
|---|---|---|
| 1 | `ENVIRONMENT_DEPENDENCY` | Node module, renderer/runtime, font, malware scanner, signing fixture, 실행 도구·권한 |
| 2 | `STRUCTURE` | 단계·flag·gate·role·registry·traceability와 source inventory |
| 3 | `VALIDATOR_META` | 검증 Profile이 탐지된 필수 항목과 실패 경로를 실제 검사하는지 검사 |
| 4 | `STAGE_FUNCTIONAL` | 단계별 정상·실패·재시도·차단 경로의 build·unit·negative·contract 실행 |
| 5 | `CONNECTED_E2E` | 요청부터 최종 산출물 read-back·tester·audit·노출 판정까지 실제 연결 실행 |
| 6 | `EVIDENCE_DECISION` | 모든 PASS의 실제 log read-back SHA-256·environment hash·시작/종료 시각·reason 결속 확인 |
| 7 | `OPERATIONS_RECOVERY` | 의존성 누락·중단·재개·rollback·재실행·성능·backup/restore |

앞 검증군의 필수 Step이 통과하지 않으면 의존 검증군은 실행하지 않고 원인을 포함한
`NOT_RUN`으로 남긴다. 발견되지 않은 선택 도구는 적용 대상으로 선언하지 않으며,
대상이 요구한 도구가 누락되면 `BLOCKED`이다.

## 4. 판정

- `PASS_NONFINAL`: 계획된 Step 전부가 동일 source/environment 결속 아래 실행·통과
- `FAIL`: 제품 동작 또는 계약 위반이 실제 증거로 확인됨
- `BLOCKED`: 필수 도구·권한·격리환경·승인·서비스가 없어 실행할 수 없음
- `NOT_RUN`: 적용 여부를 판단하지 못했거나 실행하지 않음
- `INCONCLUSIVE`: 실행했지만 오라클 또는 증거가 충분하지 않음

`NOT_RUN`, `BLOCKED`, `INCONCLUSIVE`를 `PASS_NONFINAL`이나 `FAIL`로 축약하지 않는다.

## 5. Profile과 Pack SPI

Profile은 다음을 포함한다.

- source root와 immutable source reference
- 탐지된 언어·Framework·Build System
- 네 차수별 Step
- Step 종류, 의존 Step, 작업 디렉터리, 명령 인자, 제한시간
- 실행 전 승인·도구·서비스 요구사항
- 적용되지 않거나 발견하지 못한 차수의 `NOT_RUN` 사유

언어·Framework Pack은 탐지와 표준 Step 제공만 담당하며 ONSURE 최종 판정을 변경할
수 없다. Java/Maven, Java/Gradle, Python, Node/TypeScript, OpenAPI, PostgreSQL Pack은
서로 독립적으로 조합 가능해야 한다.

Java SPI는 `io.onsure.platform.ValidationPack`이다. 기본 구현은
`MavenValidationPack`, `GradleValidationPack`, `PythonValidationPack`,
`NodeValidationPack`, `OpenApiValidationPack`, `PostgresqlValidationPack`이며 Core가
항상 고정된 표준 Pack 집합으로 설치한다. Core에 명시적으로 설치한 신뢰된
Pack만 사용하며 대상 저장소가 임의 Java class나 command를 주입할 수 없다. Pack step
ID는 `<pack-id>.` prefix를 사용하고 원칙적으로 다음 검증군에만 기여할 수 있다.

- `STAGE_FUNCTIONAL`
- `CONNECTED_E2E`
- `OPERATIONS_RECOVERY`

환경·구조·검증기 메타검증·증적 판정 gate는 Core 전용이며 Pack이 교체할 수 없다. 유일한
예외는 Core에 고정 설치된 `NodeValidationPack`의 정확한 `node.dependencies` 명령
`npm --offline ci --ignore-scripts`다. 이 Step은 핵심 `environment.preflight` 뒤, 구조 inventory
앞에서 격리 snapshot에만 설치하며 외부 Pack이 같은 검증군이나 명령을 기여하면 거부한다.
메타검증은 일곱 검증군 전체와 실패·재시도·차단, 연결 E2E 여섯 facet,
그 여섯 facet의 실제 artifact 계보를 재계산하는 `WORKFLOW_LINEAGE`,
중단·재개·rollback·재실행 네 facet이 Profile에 존재하는지 다시 계산한다. 하나라도
누락되면 `VALIDATOR_PROFILE_COVERAGE_INVALID`로 실패한다.
Pack 명령도 no-network sandbox가 지원하는 offline Maven, Gradle, Python test, npm
명령으로 제한된다. Renderer, font, malware scanner, signer fixture처럼 제품별인 항목은
Pack이 typed `EnvironmentRequirement`로 탐지·선언하거나, 운영자가 대상 원본 밖의 엄격한
`ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1`으로 선언한다. 필수 항목이 누락되면 환경 단계에서
`BLOCKED`로 판정한다. 지원 종류는 executable, source file/directory, executable source
file, font family다. ClamAV는 `EXECUTABLE=clamscan`, 서명 Fixture는 `SOURCE_FILE`, launcher
권한은 `EXECUTABLE_SOURCE_FILE`, renderer asset은 `SOURCE_DIRECTORY`, font는 `FONT_FAMILY`로
선언한다. 외부 프로필의 의미 digest와 파일 digest를 영수증에 결속하고 실행 종료 전 파일
불변성을 다시 확인한다. Node module은 manifest/lock 집합 대조 후 위 고정 offline 설치로 확인한다.

CLI `universal`, 등록 Workflow `validation.run`, 관리 API `/v1/programs/validate`의
`UNIVERSAL` 프로필은 같은 `StandardValidationProfileDetector`와 `UniversalValidationRunner`를
사용한다. 관리 API는 universal receipt를 복제하지 않고 receipt 경로와 SHA-256, phase/group
outcome을 기존 관리 projection에 결속한다. 관리화면은 이 projection을 표시하며 `NOT_RUN`,
`BLOCKED`, `FAIL`을 PASS로 변환하지 않는다.

증적 판정은 PASS 필드의 존재만 검사하지 않는다. 신뢰된 실행 로그 경로 안의 실제 파일을
다시 읽어 `outputSha256`과 대조하고, 각 Step의 `environmentSha256`이 실행 영수증의
`environment_evidence.sha256`과 같은지 확인한다. 로그 누락·경로 이탈·symlink·변조,
환경 digest 불일치, 역전된 실행 시각은 `PASS_EVIDENCE_INTEGRITY_INVALID`로 실패한다.
7번 운영·복구까지 끝난 뒤 영수증 봉인 직전에 전체 PASS 로그를 다시 읽는
`ONSURE_PASS_EVIDENCE_FINALIZATION_V1` 검사를 수행한다. 운영·복구 로그를 포함한 최종 대조가
실패하면 기존 증적 Step이 통과했더라도 `EVIDENCE_DECISION`과 전체 판정을 `FAIL`로 내린다.

기본 Detector는 Java `ServiceLoader`에서 설치된 Pack을 ID 순으로 불러온다. 배포자가
검토한 별도 JAR의 `META-INF/services/io.onsure.platform.ValidationPack`만 로딩 대상이며,
검증 대상 source 안의 provider class나 descriptor는 classpath에 추가하지 않는다. 테스트와
embedding SDK는 `StandardValidationProfileDetector(List<ValidationPack>)`으로 Pack 집합을
명시적으로 고정할 수 있다.

## 6. 안전 실행

1. 원본은 읽기 전용으로 digest를 계산한다.
2. 제외 규칙과 크기 상한을 적용해 격리 snapshot을 만든다.
3. 실행 명령은 Profile이 생성한 argv 배열로만 전달하며 inline shell을 금지한다.
4. snapshot만 쓰기 가능하며 원본·상위 workspace·다른 제품 경로는 쓰기 금지한다.
5. 네트워크는 기본 차단하고 승인된 dependency cache 또는 loopback 서비스만 사용한다.
6. 시간·CPU·메모리·process·file·output 상한을 강제한다.
7. 실행 전후 원본 digest가 다르면 모든 결과를 무효화하고 `FAIL` 처리한다.

## 7. 초기 표준 탐지

- `pom.xml`: Maven offline `clean verify`
- `gradlew` + Gradle build file: Gradle offline `clean test`
- `pyproject.toml`, `pytest.ini`, `requirements.txt`, `tests/`: pytest 또는 unittest
- `package.json`: 선언된 build/test/integration script와 built-in `NodeScriptValidationPack`
- `openapi.yaml|yml|json`, `contracts/openapi/`의 모든 제품 계약: 계약마다 독립 Step과
  로그를 만들고 중복 key, 3.0/3.1, info, path/method, operationId 유일성, responses,
  local `$ref` AST 검증. 상위 제품을 검증할 때 fixture/test/generated subtree 계약은 제외
- `db/migration`, `migrations`: migration 정적 검증; 승인된 합성 DB 없으면 4차 `NOT_RUN`

PostgreSQL Pack은 최대 탐색 깊이·파일 수와 생성물 제외 규칙을 적용해 중첩 Maven 모듈의
`src/main/resources/db/migration`도 읽기 전용으로 찾는다. Flyway/PostgreSQL 표식과 SQL을
정적 inventory하되 승인된 합성 DB 접속이 없는 경우 apply·lock·rollback·restore를 통과로
간주하지 않고 운영복구 단계를 `NOT_RUN`으로 유지한다.

탐지는 대상 저장소에 `onsure-target.json`을 생성하거나 요구하지 않는다.

구조 단계와 Program Profile은 `ONSURE_STATIC_WORKFLOW_INVENTORY_V1`을 공통으로 사용한다.
이 inventory는 source digest와 개별 evidence file digest에 결속된 Node script, Maven project/module,
Java/Python main, OpenAPI operation, DB migration, executable shell 및 배포 정의 후보를 만든다.
후보 이름에서 test, render/produce, read-back, audit, gate/permit, exposure/release, recovery,
data-lifecycle 역할을 보수적으로 분류한다. 원래 command 본문은 저장하지 않으며 모든 후보는
`DISCOVERY_ONLY_REVIEW_REQUIRED`, `runtime_verified=false`, `auto_execute=false`다. 발견은 실행 권한이나
PASS가 아니다. 검증기 메타 단계는 발견 역할과 실제 executable StepKind를 다시 비교하고 매핑되지
않은 역할을 `REVIEW_REQUIRED_NOT_EXECUTABLE`로 기록한다. 해당 facet의 실행 Pack이 없으면 이후
placeholder는 계속 `NOT_RUN`이다.

`NodeScriptValidationPack`은 `test:negative`, `test:retry`, `test:blocking`,
`test:e2e-request`, `render`, `test:readback`, `test:tester`, `test:audit`,
`test:exposure`, `test:lineage`, `test:interruption`, `test:resume`, `test:rollback`, `test:rerun`을
각 검증 facet에 대응시킨다. 선언되지 않은 facet은 일반 integration test 통과로 대체하지
않으며 Core placeholder가 `NOT_RUN`으로 남긴다.

`test:lineage`는 snapshot의 `.onsure/workflow-lineage.v1.json`에
`ONSURE_PORTABLE_WORKFLOW_LINEAGE_V1` 영수증을 생성한다. Core는 스크립트 종료 코드만 신뢰하지
않고 request·artifact·schema 파일을 다시 읽어 SHA-256을 계산한다. 각 handoff의 producer output,
consumer input, artifact, producer/consumer schema digest가 실제 파일과 같아야 하며 permit subject,
read-back, tester, audit, expected/actual exposure도 같은 run ID·request digest·artifact와 permit ID에
결속돼야 한다. 다른 run이나 request에 발급된 permit 재사용은 실패한다.
지원하지 않는 JSON Schema keyword는 통과시키지 않고 `INCONCLUSIVE`, 누락·변조·경로 이탈·symlink,
handoff 재사용·permit 시간창 오류는 `FAIL`이다. 이는 self-validation 영수증이므로 독립 OTester,
독립 OAudit 또는 외부 signer를 대체하지 않는다.

## 8. 범용성 수용기준

최소 다음 독립 대상에서 같은 Profile 계약과 Runner를 사용해야 한다.

1. ONSURE 자체 Java/Maven
2. 중립 Python 프로그램
3. 중립 Node/TypeScript 프로그램

각 대상은 1~4차 결과를 모두 보고하며 미실행 차수를 숨기지 않는다. Fixture 전용
프로그램이나 특정 제품 Adapter 통과만으로 범용성을 주장하지 않는다.
