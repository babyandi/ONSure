# 116 FR-COM Trace 및 Global Requirement Remediation

Status: `EXECUTED_PARTIAL / NON_FINAL`

## 1. 목적
115의 Design Lock HOLD 원인 중 `FR-COM-001..013 machine trace 미결속`, 비ID Requirement 미materialization, applicability context 미확정을 순서대로 해소한다.

## 2. FR-COM 13건 trace closure
FR-COM-001~013을 모두 Global Trace의 explicit requirement population에 편입한다. 각 행은 최소 Design, Contract/Registry owner, Operation/API owner, Event/Receipt, Test owner를 가진다. 실제 구현 Contract가 없는 경우 `DESIGN_CONTRACT_OWNER_PENDING`을 사용하며 orphan을 PASS로 가장하지 않는다.

| ID | 핵심 의미 | Design owner | Contract/Registry owner | Operation/API | Event/Receipt | Test owner |
|---|---|---|---|---|---|---|
| FR-COM-001 | 실행 Context 결속 | 02,04,53 | tenant-context / product-process-lineage | workflow dispatch | execution receipt | 06 |
| FR-COM-002 | Tenant 격리 | 04,46,49 | tenant-context / authority profile | tenant scoped operations | auth/audit receipt | 06 security |
| FR-COM-003 | Entitlement/Credit/Feature/Validity | 04 OLicense | entitlement/credit registry | license validate/consume | license receipt | 06 license |
| FR-COM-004 | policy/input/env/tool/result hash | 04,60,63 | receipt envelope | all effect ops | execution receipt | 06 evidence |
| FR-COM-005 | 동일 입력 재현성 | 43,63 | canonicalization profile | reperformance | reperformance receipt | 06 deterministic |
| FR-COM-006 | ONSure 내부오류 비용 미확정 | 43,47 | usage reservation/settlement owner | credit settle | usage receipt | 06 fault injection |
| FR-COM-007 | Patch 별도 worktree/branch | 02 OImprovement | git/change registry owner | improvement patch | git operation receipt | 06 git |
| FR-COM-008 | Main 직접변경 금지 | 03/52 | main-branch-protection.v1.json | git merge/push | git receipt | branch protection test |
| FR-COM-009 | Shared Corpus opt-in/out | 46,67 | privacy/policy profile | corpus contribute | consent/audit receipt | privacy test |
| FR-COM-010 | Portfolio 조회 | 05 | portfolio projection owner | portfolio read | read audit | UI/API test |
| FR-COM-011 | 중요상태 능동 통지 | 05,47 | notification rule/event | notification dispatch | delivery receipt | notification test |
| FR-COM-012 | Seat 회수·재배정/token 무효화 | 04,46 | seat/credential authority | seat revoke/reassign | authority receipt | RBAC/session test |
| FR-COM-013 | 규제산업 SoD 강제 | 55,62,67 | authority grant/policy profile | high-risk ops | authority/approval receipt | SoD negative test |

## 3. Explicit trace 결과
- 기존 FR-META trace: 60/60
- 신규 FR-COM trace: 13/13
- explicit requirement trace 후보: **73/73**

중요: `73/73`은 Global Requirement Universe 전체 closure가 아니다. 비ID Program 기능·수용기준·NFR·Invariant·Policy/Regulatory requirement denominator는 별도 materialization이 필요하다.

## 4. 비ID Requirement deterministic ID 규칙
권위문서의 heading path와 source line anchor를 이용해 다음 ID를 생성한다.

`REQ-{DOMAIN}-{SOURCE_DOC_ID}-{SECTION}-{ORDINAL}`

예:
- `REQ-OLEARN-02-03-F001`
- `REQ-OPLAN-02-04-A001`
- `REQ-NFR-02-11-N001`

ID 생성 입력은 `normalized source path + heading hierarchy + normalized requirement text + ordinal`이며 결과가 바뀌면 Universe epoch를 갱신한다.

## 5. Requirement extraction 대상
최소 다음을 denominator discovery source로 포함한다.
- docs/master/01~08A
- semantic-assurance design authority 문서
- explicit Contract invariant
- state transition invariant
- policy profile mandatory rule
- Industry profile mandatory control
- acceptance criteria
- NFR

단, 설명·예시·향후 후보를 자동 Requirement로 승격하지 않는다. `MUST/SHALL/금지/필수/수용기준/하드조건` 또는 명시된 requirement semantics만 extraction candidate다.

## 6. Semantic normalization
각 candidate는 `UNIQUE | DUPLICATE | REFINES | SUPERSEDES | CONFLICTS | CONDITIONAL_OVERRIDE`로 분류한다. 중복은 denominator에서 하나의 canonical requirement만 세고 source aliases를 보존한다. 상충은 숨기지 않고 conflict queue로 보낸다.

## 7. 현재 판정
- Task 1 FR-COM explicit gap: **CANDIDATE_CLOSED**
- Global non-ID materialization: **PARTIAL / NOT_COMPLETE**
- Global denominator: **NOT_YET_AUTHORITATIVE**
