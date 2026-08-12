# ONSure Naming·Conflict·Trace·Master Index Closure

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
설계가 00~78까지 확장되면서 발생할 수 있는 중복 이름·의미 충돌·고아 Requirement/Contract·인덱스 drift를 제거한다. 이 문서는 설계 명칭의 canonicalization 기준이다.

## 2. Canonical Naming
### 상태
- Verification Decision: PASS|FAIL|HOLD|BLOCKED|NOT_RUN|INCONCLUSIVE|NON_FINAL
- Currentness: CURRENT|STALE|REASSESSMENT_REQUIRED|INVALIDATED|REVOKED|UNKNOWN
- Qualification: QUALIFIED|PARTIAL|NOT_PROVEN|STALE|REQUALIFICATION_REQUIRED|REVOKED
- Independence: SELF_ONLY|PARTIAL|INDEPENDENT_PROVEN|NOT_PROVEN
- Human Acceptance: NOT_REQUIRED|PENDING|ACCEPTED|REJECTED|EXPIRED|REVOKED

다른 문서의 유사 표현은 위 canonical vocabulary로 mapping한다. historical v1 enum은 삭제하지 않고 compatibility mapping을 둔다.

## 3. Canonical Entity Names
- `ValidationTargetManifest` — ProductLock/TargetLock 등 유사명 대체
- `RequirementUniverseSnapshot` — requirement set/denominator authority의 상위 객체
- `AssuranceCurrentnessSnapshot`
- `AssuranceCompositionSnapshot`
- `EvidenceGraphSnapshot`
- `IndependentAssuranceReceipt`
- `FinalCandidate`, `FinalApprovalReceipt`, `FinalLock`
- `DeploymentRevision`, `RuntimeInstance`
- `AssuranceCertificate`, `RevocationReceipt`
- `AuthorityGrant`
- `RecoveryQualificationReceipt`
- `ONSureReleaseQualification`

## 4. 중복 문서 번호
`21_CLAUDE_DEVELOPMENT_HANDOFF.md`와 `21_INDEPENDENT_ASSURANCE_EXECUTION_ARCHITECTURE.md`가 번호 21을 공유한다. 파일명 자체는 현재 브랜치 호환성을 위해 유지하되, logical document id는:
- DEV-HANDOFF-01
- SA-DESIGN-21
로 분리한다. 후속 물리 rename은 Claude 작업과 충돌 가능성이 있으므로 별도 maintenance commit에서 수행한다.

## 5. Acceptance Certificate vs Assurance Certificate
기존 AcceptanceCertificate는 고객 인수/발급 사실 중심 legacy concept, 신규 AssuranceCertificate는 machine assurance/currentness/revocation/tier object다.
- Human/customer acceptance는 `HumanAcceptanceReceipt`
- public assurance proof는 `AssuranceCertificate`
- legacy AcceptanceCertificate는 신규 개발에서 사용 금지하고 migration mapping을 제공한다.

## 6. PASS 표현 정리
- self-validation PASS → `PASS / SELF_ONLY / NON_FINAL`
- independent PASS → `PASS / INDEPENDENT_PROVEN`
- historical final issuance → FinalLock historical fact
- current production assurance → separate Currentness=CURRENT + Tier AT5 조건

`FINAL_PASS`라는 단일 문자열로 위 의미를 합치지 않는다.

## 7. Trace Closure Rule
모든 `FR-META-001~060`은 최소 다음 필드를 가진 trace row로 관리한다.
- requirement_id
- parent/master section
- review_rule_ids[]
- architecture_entity_or_service[]
- operation_ids[]
- contract_ids[]
- test_obligation_ids[]
- evidence/receipt types[]
- ui/claim exposure[]
- open_policy_refs[]

### orphan definitions
- Requirement without review/contract/test path = REQUIREMENT_ORPHAN
- Contract without requirement/design owner = CONTRACT_ORPHAN
- Operation without contract/authority/receipt/test = OPERATION_ORPHAN
- Customer claim without machine evidence/tier source = CLAIM_ORPHAN

orphan이 하나라도 있으면 Design Baseline Complete 금지.

## 8. 02~08 Responsibility Boundary
- 02: 무엇을 해야 하는가 / 기능·NFR·수용기준
- 03: 무엇을 검토하고 어떤 Finding/Decision을 만드는가
- 04: 어떤 Service/Entity/API/Event/Invariant로 구현하는가
- 05: 사용자에게 상태·권위·제한을 어떻게 표현하는가
- 06: 어떻게 깨뜨리고 실행증거를 얻는가
- 07: AI/Agent/Truth/Meta-Assurance 방법론
- 08/08A: 사업·법무·정책·임계치 결정 추적
- semantic-assurance companion: cross-cutting formal design/machine contract blueprint

## 9. Contract Naming Rule
`<domain>-<object>.candidate.v2.schema.json` 또는 registry는 `<domain>-<registry>.candidate.v2.json`을 사용한다. `final`, `independent`, `qualified`, `current` 같은 강한 단어는 의미상 필수 field/invariant가 없는 object 이름에 사용하지 않는다.

## 10. Master Index 동기화 규칙
새 companion이 추가되면 같은 변경에서:
1. semantic README
2. `00_ONSURE_MASTER_DESIGN_SET.md` 주요 산출물/현재 기준선
3. DesignTraceRegistry blueprint
을 갱신한다.

## 11. Conflict Resolution
- Contract와 문서 충돌: Active Contract > Candidate Contract > Master Design > Companion > legacy/reference 순
- 둘 다 Candidate면 newer라는 이유만으로 선택하지 않고 explicit supersession record 필요
- 정책 충돌: hard invariant > industry floor > product profile > org policy > case stricter override
- 상태 의미 충돌: canonical orthogonal vocabulary로 변환 후 비교

## 12. 완료조건
- 이름 하나가 두 개의 서로 다른 강도 의미를 갖지 않음
- 21 번호 충돌은 logical id로 분리
- Acceptance vs Assurance Certificate 의미 분리
- FR-META trace orphan 0 후보
- Contract/Operation/Claim orphan 0 후보
- README/Master/Trace registry가 동일 기준선 번호를 가리킴
