# ONSure Spring Boot Web Workbench Vertical Slice

- 상태: `IMPLEMENTED_UNVERIFIED_NONFINAL`
- 기준 Source: `main@e3e8add1e5917e127d6ea788147055f034231c70`
- 작업 Branch: `onsure/improve-web-springboot-workbench-20260828`
- 목적: ONSure의 Web Console을 Spring Boot 기반으로 시작하되, 기존 Assurance 권위와 frozen successor lineage를 침범하지 않는 첫 개발 Vertical Slice를 정의한다.

## 1. 적용한 OBuilder 설계 원칙

참조 정본:

- `babyandi/ORUDA/docs/architecture/OBUILDER_MASTER_DESIGN_BASELINE_v0.1.md`
- `babyandi/ORUDA/docs/architecture/OBUILDER_BOOTSTRAP_IMPLEMENTATION_BACKLOG_v0.1.md`
- `babyandi/ORUDA/docs/development/OBUILDER_INTEGRATED_E2E_STATUS_v1.md`

적용 원칙:

1. Typed Contract와 명시적 상태를 사용한다.
2. 구현 존재를 실행 PASS로 승격하지 않는다.
3. Capability가 연결되지 않았으면 임의 fallback을 만들지 않는다.
4. 모든 초기 결과는 Candidate/NON_FINAL이다.
5. Final/Release/Merge 권위를 Web surface가 소유하지 않는다.
6. Foundation Profile과 실행/검증 권위를 분리한다.
7. 실제 E2E 실행 증적 전에는 `NOT_RUN`을 `PASS`로 변경하지 않는다.
8. Web은 기존 Core read model을 사용하고 별도 Web-owned Assurance 상태를 만들지 않는다.

중요: ORUDA의 OBuilder 문서상 `SPRING_BOOT_JAVA17_MAVEN`은 설계되어 있으나 OBuilder Integrated E2E에서는 아직 `DESIGNED_NOT_EXECUTION_READY`다. 따라서 본 구현은 OBuilder의 구조 원칙을 참조하지만, OBuilder가 이 Spring Boot 결과를 자동 생성·검증했다는 주장을 하지 않는다.

## 2. 이번 Slice 범위

추가 모듈:

```text
modules/onsure-web-console
```

기술 기준:

- Java 17
- Spring Boot 3.4.13
- Maven
- embedded web server
- loopback default binding `127.0.0.1:47312`
- 외부 CDN/원격 프런트 의존성 없음
- authoritative workspace는 `ONSURE_WORKSPACE_ROOT`로만 명시한다

Spring Boot 3.4 계열은 Java 17을 지원하며, 현재 ONSure가 고정한 Jackson 2.18.x 계열과의 통합 위험을 낮추기 위해 3.4.13을 사용한다. 이 선택은 실행 검증 전 개발 기준이며 검증 결과에 따라 별도 Revision으로 조정할 수 있다.

제공 기능:

```text
GET /api/v1/workbench/status
GET /api/v1/workbench/catalog
GET /actuator/health
GET /
```

`/api/v1/workbench/catalog`는 기존 Core `ProductCatalog`를 통해 Workspace/Project/Target과 catalog revision을 읽는다. Web Controller가 catalog JSON 파일을 직접 파싱하지 않는다.

브라우저로 노출되는 Target projection은 로컬 파일 경로인 `sourceRoot`를 제외하고, Target identity와 immutable source reference, adapter/policy/execution profile만 제공한다.

`ONSURE_WORKSPACE_ROOT`가 비어 있거나 안전하게 읽을 수 없으면 임의 경로를 추측하지 않는다.

```text
NOT_CONFIGURED_NONFINAL
CORE_READ_MODEL_BLOCKED_NONFINAL
```

중 하나로 닫고 빈 catalog를 정상 연결 상태로 위장하지 않는다.

## 3. Core 변경 범위

기존 `ProductCatalog`에 다음 읽기 전용 snapshot API만 보강한다.

```text
workspaces()
projects()
```

등록/수정/삭제/검증 판정 의미는 변경하지 않는다. 기존 `targets(projectId)`, `revision()`과 조합해 Web read model을 구성한다.

## 4. 권위 경계

현재 Web Console은 다음을 하지 않는다.

- 파일 수정
- Shell/Build 실행
- Verification PASS/FAIL 생성
- Evidence 생성 또는 독립검증 대체
- Git Branch/Commit/Push/PR 실행
- Merge
- Release
- FinalLock
- Production GO
- Commercial GO

API 계약은 다음 값을 강제한다.

```text
state = READ_ONLY_CANDIDATE_NONFINAL
independentVerificationComplete = false
finalClaimAllowed = false
productionGo = false
mutation = BLOCKED
merge = BLOCKED
release = BLOCKED
finalDecision = BLOCKED
```

Core catalog read model도 다음을 유지한다.

```text
independentVerificationComplete = false
finalClaimAllowed = false
productionGo = false
```

## 5. ONSure 목표 설계와의 연결

ONSure 정본의 Web 방향은 VS Code/Web/CLI가 동일 Core 상태 모델을 공유하는 것이다.

현재 물질화된 흐름:

```text
Spring Boot runtime
→ Web Workbench surface
→ typed read-only status contract
→ ONSure ProductCatalog read model
→ explicit authority boundary
```

다음 연결 순서:

```text
Session read model
→ Program Profile read model
→ Learning/Verification/Findings/Evidence read model
→ Approval Inbox
→ authenticated command boundary
→ controlled mutation intents
→ independent verification read-back
```

새 Web 전용 Assurance 엔진이나 별도 PASS 로직을 만들지 않는다.

## 6. 요구 검증 명령

아래는 실행 예정 명령이며 이 문서 작성 시점에는 실행 증적이 없다.

```bash
mvn -f pom-modular.xml -pl modules/onsure-web-console -am test
mvn -f pom-modular.xml -pl modules/onsure-web-console -am package
ONSURE_WORKSPACE_ROOT="$PWD" java -jar modules/onsure-web-console/target/onsure-web-console-0.1.0-SNAPSHOT.jar
curl -fsS http://127.0.0.1:47312/api/v1/workbench/status
curl -fsS http://127.0.0.1:47312/api/v1/workbench/catalog
curl -fsS http://127.0.0.1:47312/actuator/health
```

검증 시 최소 확인:

1. 미설정 workspace가 `NOT_CONFIGURED_NONFINAL`로 닫히는지
2. 설정 workspace가 기존 `ProductCatalog`를 읽는지
3. target `sourceRoot`가 browser response에 포함되지 않는지
4. catalog 오류가 PASS/available=true로 승격되지 않는지
5. status와 catalog 모두 final/production 권위를 false로 유지하는지

## 7. 현재 검증 상태

```text
SOURCE_MATERIALIZED             COMPLETE
SPRING_BOOT_MODULE              COMPLETE
READ_ONLY_STATUS_CONTRACT       COMPLETE
CORE_CATALOG_READ_MODEL         COMPLETE
CORE_CATALOG_READ_APIS          COMPLETE
LOCAL_PATH_REDACTION            MATERIALIZED_NOT_RUN
INITIAL_BROWSER_SURFACE         COMPLETE
FAIL_CLOSED_AUTHORITY_TEST      MATERIALIZED_NOT_RUN
CORE_CATALOG_READ_MODEL_TEST    MATERIALIZED_NOT_RUN
MAVEN_TEST                      NOT_RUN
MAVEN_PACKAGE                   NOT_RUN
RUNTIME_BOOT                    NOT_RUN
HTTP_STATUS_READBACK            NOT_RUN
HTTP_CATALOG_READBACK           NOT_RUN
BROWSER_READBACK                NOT_RUN
INDEPENDENT_VERIFICATION        NOT_RUN
PROMOTION                       BLOCKED
FINAL_LOCK                      NOT_ALLOWED
PRODUCTION_GO                   false
COMMERCIAL_GO                   false
```

실행 증적 없이 위 `NOT_RUN` 및 `MATERIALIZED_NOT_RUN` 항목을 PASS로 변경하지 않는다.
