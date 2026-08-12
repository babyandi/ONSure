# ONSure Design Baseline Candidate Lock Preconditions

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
설계 작업 자체의 종료조건을 정의한다. 이 Lock은 제품 FinalLock이나 Production/Commercial 권위가 아니다. 오직 **설계 기준선 후보가 충분히 닫혔는가**를 판단한다.

## 2. Design Baseline Candidate Complete 조건
### A. Structure Closure
- 02~08 책임구조가 명확
- companion 00~80이 parent를 대체하지 않고 trace됨
- duplicate logical concepts가 canonical naming으로 정리됨
- P0 structural design gap 0

### B. Trace Closure
- FR-META-001~060 requirement orphan 0
- Contract candidate orphan 0
- Operation orphan 0
- Customer claim orphan 0
- each P0 design obligation has review/test/evidence path

### C. Semantic Closure
- state dimensions orthogonal
- PASS/Final/Current/Qualified/Independent 의미가 분리
- composition/invalidation/recovery/certificate semantics formalized
- cross-contract rule table 존재
- safe default for unknown/partial/stale/unverifiable 존재

### D. Authority Closure
- effect operation authority mapping 존재
- tenant/resource/purpose/effect-time binding 존재
- SoD/four-eyes/delegation/break-glass rule 존재
- self-attested authority/independence/qualification 금지

### E. Policy Closure
- P0 open decision은 fixed invariant 또는 configurable policy로 변환
- industry/product/org/case priority 명시
- safety floor 약화 금지
- Assurance Tier evidence criteria 명시

### F. Operational Closure
- persistence/recovery/DR semantics 존재
- event/receipt/idempotency/retry/cancel semantics 존재
- observability degraded mode가 assurance state에 연결
- external integration/plugin/AI/Meta-Assurance trust path 존재

## 3. Design Completion 재평가
00~35 시점 92~94%, 00~56 시점 95~97% 후보였다. 58~80에서 당시 남은 P0 구조·machine semantics·policy/authority/trace 영역을 추가 상세화했다.

따라서 **설계 문서 폐쇄성 자체는 `97~98% 후보`로 재평가할 수 있다.** 단 다음 2~3%는 구현결과가 있어야 발견 가능한 예외 semantics와 실제 산업/상품 초기값 보정이다. 이 숫자는 구현률/검증률이 아니다.

## 4. 아직 Design Baseline Lock을 선언하지 않는 이유
다음 검사가 실제 machine-readable inventory로 수행되지 않았다.
- FR-META-001~060 exact trace rows 생성 및 orphan 계산
- 모든 companion/document id inventory exact count
- candidate contract name collision 자동검사
- 02~08 final index cross-check 자동검사

따라서 현재 상태는 `DESIGN_BASELINE_CANDIDATE_READY_FOR_LOCK_CHECK`, 아직 `LOCKED`가 아니다.

## 5. Lock Check 산출물
실제 lock check는 최소 다음을 생성해야 한다.
- design_document_inventory.json
- design_trace_registry.candidate.json
- design_orphan_report.json
- design_conflict_report.json
- open_policy_report.json
- design_baseline_manifest.json
- design_baseline_receipt.json

## 6. Anti-False-Completion
다음은 Design Complete 증거가 아니다.
- 문서 수가 많음
- Finding 수가 많음
- Claude 구현이 진행 중임
- Candidate Schema가 존재함
- PR이 mergeable함

완료는 exact inventory + trace + contradiction/orphan 0 + unresolved P0 semantics 0으로 판단한다.

## 7. 현재 Design 상태
`DESIGN_BASELINE_CANDIDATE_READY_FOR_LOCK_CHECK / NON_FINAL`

구현/실행/독립검증 상태는 기존대로 별도 관리한다.
