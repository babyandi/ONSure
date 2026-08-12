# ONSure Independent Assurance 실행 아키텍처

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
본 문서는 ONSure의 `Independent OTester`, `Independent OAudit`, `Human Acceptance`가 단순 역할명·boolean·다른 key/model/run으로 오인되지 않도록 **실행 주체, 권위, 격리, 입력, 산출물, 검증, 실패상태, 재검증**을 하나의 독립 Assurance 실행 아키텍처로 정의한다.

핵심 원칙은 다음과 같다.

`Independent = different execution + different principal + different credential administration + implementation independence + oracle independence + discovery independence + knowledge independence + current qualification`

위 축 중 하나라도 증명되지 않으면 `INDEPENDENT`를 발행하지 않는다.

## 2. 역할과 권위 분리
### 2.1 OTester
OTester는 대상 기능·요구사항·행동을 **실제 실행/재수행**하여 Claim의 사실성을 검증한다. OTester는 구현자/개선자와 동일 principal일 수 없으며, target-owned test 또는 target-generated oracle을 단독 truth authority로 사용할 수 없다.

### 2.2 OAudit
OAudit는 OTester 결과를 단순 재실행하는 역할이 아니라 다음을 별도로 검증한다.
- denominator/applicability completeness
- authority/SoD
- evidence provenance/canonicalization
- freshness/revocation
- Finding closure
- Final reconstruction
- OTester independence/qualification 자체

### 2.3 Human Acceptance
Human Acceptance는 사실 검증과 구분한다.
- FACT_VALIDATION
- RISK_ACCEPTANCE
- BUSINESS_ACCEPTANCE

`RISK_ACCEPTANCE|BUSINESS_ACCEPTANCE`는 사실 Ground Truth가 아니며 OTester/OAudit를 대체할 수 없다.

## 3. Principal Model
독립 실행 주체는 최소 다음 필드를 가진다.
- `principal_id`
- `principal_type`
- `organization_id`
- `credential_id`
- `credential_admin_owner_id`
- `authority_profile_sha256`
- `qualification_profile_sha256`
- `valid_from / valid_until / revoked_at`
- `tenant_scope / target_scope / purpose_scope`

동일 사람이 여러 key를 사용하거나 동일 KMS/admin owner가 여러 검증자 credential을 관리하면 principal independence가 충족되지 않는다.

## 4. Independence Profile 6축
| 축 | 질문 | 최소 증적 |
|---|---|---|
| Principal | 구현/승인/검증 주체가 다른가 | principal registry + SoD graph |
| Credential Admin | key 관리자가 다른가 | key registry/admin-owner binding |
| Implementation | 동일 코드/validator implementation을 공유하는가 | validator build/provenance digest |
| Oracle | 동일 oracle/expected-result 생성원을 공유하는가 | oracle profile/digest/producer |
| Discovery | 동일 requirement/denominator discovery 결과만 소비하는가 | independent discovery receipt |
| Knowledge | 이전 verdict/memory/RAG에 노출되었는가 | blind-context + denied-source-access evidence |

## 5. Independent Execution Plan
독립 실행은 ad-hoc 명령이 아니라 `IndependentAssuranceExecutionPlan`을 먼저 고정한다.

필수 내용:
- target/source/artifact identity
- requirement/scope/denominator/policy epoch
- selected assurance lane: OTESTER | OAUDIT | HUMAN_FACT_VALIDATION
- principal profile
- independence profile
- validator/oracle/fixture exact identity
- environment/toolchain identity
- blind-context requirement
- network/resource effect policy
- expected evidence set
- timeout/budget
- retry policy
- stale triggers
- completion/abort rule

계획 확정 후 fixture/oracle/validator를 바꿔 결과를 개선하는 것을 금지한다.

## 6. Execution Isolation
독립 실행 환경은 최소 다음을 분리한다.
- worktree/workspace
- cache namespace
- temp/scratch
- environment variables
- model/RAG memory
- result/receipt store
- credential material

고신뢰 lane은 동일 execution workspace를 재사용하지 않는다. `reset=true` 선언만으로 격리를 증명하지 않고 pre/post digest 또는 platform evidence를 요구한다.

## 7. Oracle/Fixture 독립성
OTester/OAudit는 다음 oracle class를 구분한다.
- SPEC
- EXECUTABLE
- METAMORPHIC
- DIFFERENTIAL
- EXPERT
- REAL_WORLD

Target-owned test는 `TARGET_OWNED_CORROBORATION`으로 별도 분류한다. 독립 PASS는 최소 하나의 qualified oracle path 또는 독립 Ground Truth producer를 요구한다.

## 8. 실행 결과 상태
독립 실행은 success-only receipt를 금지한다.

허용 상태:
- NOT_RUN
- RUNNING
- PASS_NONFINAL
- FAIL
- BLOCKED
- HOLD
- INCONCLUSIVE
- INPUT_REQUIRED
- STALE
- REVOKED

`PASS_NONFINAL`은 Final authority가 아니며 Final reconstruction이 별도로 소비한다.

## 9. Retry / Attempt Integrity
모든 attempt를 보존한다.
- attempt_id
- attempt_number
- started_at/completed_at
- execution identity digest
- raw result digest
- reason

실패 후 성공 attempt 하나만 선택해 PASS로 만드는 것을 금지한다. Retry policy는 결과를 보기 전에 고정한다.

## 10. Evidence Contract
Independent receipt는 최소 다음을 보존한다.
- exact plan digest
- exact principal/credential profile digest
- independence profile digest
- qualification epoch
- target/source/artifact digest
- requirement/scope/denominator/policy epoch
- fixture/oracle/validator/environment/toolchain digest
- attempt history digest
- raw evidence digest set
- decision/reason codes
- freshness state
- signature

## 11. OTester→OAudit 조합 규칙
OAudit는 OTester와 다음을 공유할 수 없다.
- same principal
- same credential admin owner
- exact same independent lane implementation when alternative verifier is required
- OTester final verdict as oracle

OAudit는 OTester receipt를 **입력 evidence**로 소비할 수 있지만 그 verdict를 truth로 가정하지 않는다.

## 12. Human Acceptance 경계
Human Acceptance는 다음이 없으면 Final input으로 사용하지 않는다.
- authenticated principal
- authority purpose
- target/gate digest
- explicit acceptance class
- nonce
- expires_at
- signed receipt
- effect-time authority revalidation

Human approval 화면 클릭 또는 request boolean은 acceptance receipt가 아니다.

## 13. 재검증/Qualification
독립 실행 프로그램 자체도 qualification 대상이다.
- seeded defect recall
- false-positive rate
- blind-context leakage test
- oracle substitution test
- same-principal/key-admin attack
- retry cherry-picking attack
- evidence mutation/replay

Validator/oracle/plan engine 변경 시 qualification을 stale 처리한다.

## 14. API 후보
- `POST /semantic-assurance/independent-plans`
- `POST /semantic-assurance/independent-plans/{id}/execute`
- `POST /semantic-assurance/independent-runs/{id}/reperform`
- `POST /semantic-assurance/independence/assess`
- `POST /semantic-assurance/human-acceptance`
- `GET /semantic-assurance/independent-runs/{id}`

API 존재는 active authority를 의미하지 않는다.

## 15. UI/UX 요구
화면은 role label보다 다음을 우선 표시한다.
- principal identity
- independence 6축
- qualification current/stale
- blind-context state
- evidence origin
- historical vs current decision

`OTESTER PASS` 단일 badge는 금지한다.

## 16. Negative Fixture
필수 fixture:
1. same principal / different key
2. different principal / shared KMS admin
3. different run / same implementation+oracle
4. blind-context declaration only, no technical proof
5. OTester verdict copied into OAudit oracle
6. failed attempt hidden after retry PASS
7. stale qualification receipt reuse
8. unsigned independent receipt
9. Human Acceptance request boolean without signed receipt
10. revoked authority at effect time

## 17. Final Gate 연동
Final reconstruction은 최소 다음을 소비한다.
- current Independent OTester receipt
- current Independent OAudit receipt
- Human Acceptance receipt
- independence profiles
- qualification records
- attempt histories

하나라도 self-validation, stale, unsigned, unqualified이면 `HOLD`다.

## 18. Claude 개발 경계
Claude 구현은 본 설계를 active authority로 간주하면 안 된다. 구현 시 우선 순위는:
1. plan persistence
2. principal/credential/profile binding
3. attempt history
4. cryptographic receipt verification
5. blind context enforcement
6. OTester/OAudit execution
7. Final reconstruction integration

실제 독립 주체/환경이 없으면 `NOT_RUN|HOLD`를 유지한다.

## 19. 현재 상태
- 설계: PRESENT
- machine contract: 별도 candidate 작성 대상
- runtime: 미구현 또는 candidate 일부
- independent execution: NOT_RUN
- qualification: NOT_RUN
- Final authority: 없음
