# ONSure LLM Gateway와 관리화면 v1

상태: `IMPLEMENTED_LOCAL_CANDIDATE / SELF_VALIDATION_NONFINAL`

## 목적과 경계

ONSure의 모델 호출은 `onsure-provider-spi` 뒤의 `onsure-llm-gateway` 한 곳을 통과한다.
Gateway는 Provider 선택, network/customer-data/cost 승인, timeout, token·비용 집계와
content-free evidence receipt를 소유한다. 모델 응답의 업무 품질, 독립 시험, 감사, 배포 승인과
Production GO는 소유하지 않는다.

ORUDA OBuilder와 OLLMGateway 설계는 읽기 전용 설계 기준으로만 참조했다. 적용한 패턴은
`Request → Attempt → Usage → Receipt`, append-only digest chain, 원문 비저장, UI projection의
anti-false-pass 경계다. ORUDA/OBuilder 소스 복사, import, submodule, runtime/API/DB 의존 및 자동
동기화는 만들지 않았고 Source Intake도 수행하지 않았다. ONSure는 ORUDA 없이 독립 build·run한다.

현재 Java namespace `io.onsure`는 유지한다. 미래 후보 `kr.co.oruda.products.onsure`는 이관
문서에만 기록하며 이번 구현에서 package를 변경하지 않는다.

## 구성

| 구성요소 | 소유 경계 | 실행 |
|---|---|---|
| Local API | 인증, 관리 projection, 검증 workflow, 정적 관리화면 | `127.0.0.1:47311` |
| LLM Gateway | exact Provider 호출, 승인 정책, usage·receipt | `127.0.0.1:47312` |
| Provider SPI | transport-neutral request/response/health/error | Maven module |
| Local Mock Provider | network-free 결정론적 개발·회귀 시험 | Gateway 내부 선택 |
| OpenAI Provider | 명시적 network/customer/cost 승인 후 단일 호출 | Gateway 내부 선택 |
| Management UI | 실제 Local API projection만 표시 | `/admin` |

Gateway API 정본은 `contracts/openapi/onsure-llm-gateway.v1.json`, Local API 정본은
`contracts/openapi/onsure-local-api.v1.json`이다. 두 서버는 기동 시 구현 route와 OpenAPI path가
정확히 일치하지 않으면 실패한다.

Local API는 기존 ADMIN bearer token의 호환성을 유지하면서 선택적 `VIEWER`, `OPERATOR`,
`APPROVER` token을 분리한다. token digest만 session projection에 노출되며 원문 token은 audit,
receipt 또는 브라우저 저장소에 기록하지 않는다. `/v1/programs`는 외부 source를 읽기 전용으로
등록하고, `/v1/programs/validate`의 `MAVEN_STANDARD` profile은 원본이 아니라 bounded snapshot에서
고정 Maven/Python/API 명령을 실행한다. 실행 전후 원본 digest가 다르면 결과 생성을 거부한다.
`.git`, `.onsure`, build output, dependency cache, Python virtualenv는 source가 아닌 로컬 도구
상태로 분류해 digest와 snapshot에서 제외한다. 그 밖의 symlink는 외부 경로 탈출 위험 때문에 거부한다.

Gateway 변경은 `/v1/gateway-settings/requests`에서 secret-free 요청을 만들고, 요청자와 다른
`APPROVER` identity가 `/v1/gateway-settings/approvals`에서 결정한다. 승인 상태는
`APPROVED_PENDING_EXTERNAL_APPLY`이며 API가 환경파일을 쓰거나 서비스를 자동 재시작하지 않는다.
모든 성공한 상태 변경은 `/v1/audit-events`의 append-only digest chain에 기록한다.

## Evidence와 모니터링

Gateway는 매 호출마다 다음 metadata를 0600 JSONL ledger에 append하고 이전 entry digest에
결속한다.

- request/response SHA-256
- provider/model, success/failure, retryability
- input/output token, estimated/actual cost
- duration, provider request evidence, observed time
- retryable failure count, average duration, ledger byte/sequence
- fallback=false, retry count=0, final claim=false

Prompt·message·completion·API key·bearer token 원문은 ledger에 저장하지 않는다. 관리화면은
Gateway의 `/v1/health`와 `/v1/metrics`를 Local API 서버가 loopback으로 조회한 결과만 받으며
브라우저가 Provider나 Gateway를 직접 호출하지 않는다.

## 관리화면

`/admin`은 외부 asset이나 inline script/style 없이 Local API JAR에서 제공된다. 사용자가 입력한
Local API bearer token은 JavaScript closure 메모리에만 존재하고 localStorage/sessionStorage/cookie에
기록하지 않는다. 인증 API는 정확한 `http://127.0.0.1:<local-api-port>` same-origin만 허용한다.

표시 항목:

- Gateway Provider/model/binding/credential 구성 여부와 deny-by-default 정책
- 요청 성공·실패, input/output token, 실제 비용, 누적 지연, evidence chain 상태
- Product Catalog에 등록된 프로그램과 최근 validation decision/finding/evidence
- remediation plan 후보 수와 improvement proof 상태
- 독립 OTester/OAudit, Production GO의 실제 `NOT_RUN`/false 상태

화면은 설정값을 수정하거나 Provider를 호출하지 않는다. secret 값은 API response에 포함되지 않는다.

## 독립 실행

```bash
mvn -B -ntp -q -f pom-modular.xml clean package

export ONSURE_LLM_GATEWAY_TOKEN='<32+ character local secret>'
export ONSURE_LLM_EVIDENCE_ROOT="$PWD/.onsure/llm-evidence"
export ONSURE_LLM_PROVIDER=local-mock
java --add-modules jdk.httpserver \
  -cp 'modules/onsure-llm-gateway/target/*:modules/onsure-provider-spi/target/*:modules/onsure-provider-local-mock/target/*:modules/onsure-provider-openai/target/*' \
  io.onsure.gateway.llm.LlmGatewayMain
```

단독 서버 package는 `onsure.service`, `onsure-llm-gateway.service`, `onsure-migrate.service`를
포함한다. 실제 secret 주입, 서비스 enable/start, 외부 Provider 실호출과 Production GO는 별도
운영 권한이며 저장소 검증만으로 승인되지 않는다.
