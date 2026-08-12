# ONSure End-to-End Assurance Sequence·Failure Paths 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
각 서비스·계약이 따로 맞아도 전체 순서가 틀리면 false assurance가 생길 수 있으므로 정상·실패·재검증 Sequence를 하나의 기준선으로 정의한다.

## 2. 정상 Sequence
1. Target 등록 및 server-side identity resolution
2. Requirement Universe discovery + applicability closure
3. Scope/Requirement/Target epoch lock
4. ExecutionPlan 승인
5. Validation work population 생성
6. Run/Attempt 실행
7. Required observation 수집
8. Evidence commit/seal
9. Decision/oracle 평가
10. OTester independent reperformance
11. OAudit independent assurance
12. Human Fact/Business Acceptance 필요 시 별도 승인
13. Final Candidate reconstruction
14. Freshness Barrier
15. Final Approval
16. Final Lock
17. Build/Release/Deployment identity 결속
18. Running population read-back
19. Currentness evaluation
20. Multi-target Product Composition
21. Certificate issuance
22. Continuous drift/revocation observation

어느 단계도 뒤 단계 성공으로 소급 대체되지 않는다.

## 3. Validation Failure Path
실패/관찰불가/예산소진은:
`RUNNING → FAILED|BLOCKED|INCONCLUSIVE|NOT_RUN → Evidence/Reason → Plan/Rework/Reperform`

FAIL을 risk accept해도 PASS로 바꾸지 않는다. Accepted Risk는 별도 business/governance 축이다.

## 4. Evidence Commit Failure
Evidence seal 이전 crash:
- decision은 positive Final input 금지
- recovery reconciler가 bytes/metadata/graph 상태 판정
- ambiguity는 `RECOVERY_REQUIRED`

## 5. Independence Failure
OTester/OAudit principal/admin/oracle/knowledge 독립성 미충족:
- self-validation 결과는 유지 가능
- independent assurance stage는 HOLD
- 다른 qualified verifier 재배정

## 6. Freshness Failure
Candidate 이후 target/policy/requirement/finding/authority 변경:
- Final Lock 거부
- 새 generation으로 필요한 범위 재검증
- 이전 run을 삭제하지 않음

## 7. Deployment Drift Path
FinalLock 후 deployed/running digest 불일치:
`Historical FinalLock preserved → Currentness INVALIDATED/REASSESSMENT_REQUIRED → Certificate current validity 하향 → notification → redeploy/revalidation`

## 8. New MissedFinding Path
새 MissedFinding:
1. RCA/affected defect class
2. validator/policy update 후보
3. historical impact scan
4. affected Final/Certificate graph traversal
5. SAFE 또는 REASSESSMENT/INVALIDATION
6. 필요한 재검증

새 finding을 현재 case 하나만 고치고 과거 certificate를 무시하지 않는다.

## 9. Authority/Key Revocation Path
- affected issuance/effect time window 계산
- approval/final/certificate/authority grants 영향평가
- historical signature fact와 current validity 분리
- reapproval/reissue/requalification

## 10. Rollback Path
운영 rollback은 과거 artifact로 되돌아가는 기술 operation이다.
- rollback artifact read-back
- 과거 assurance context currentness 평가
- policy/validator/authority 변화 확인
- 필요 시 reperformance
- CURRENT 자동복원 금지

## 11. DR Recovery Path
서비스 restore 후:
`DATA_RESTORED_UNQUALIFIED → integrity reconciliation → recovery qualification → limited service → strong issuance resume`

## 12. Selector Migration Path
v1→v2:
- dual read
- v1 active authority 유지
- reconstructed v2 shadow
- disagreement analysis
- blocker closure
- signed selector transition
- rollback pointer

Candidate 존재만으로 v2 active 금지.

## 13. Sequence Invariant
- Final보다 independent gate가 먼저
- Deployment는 Final evidence를 소급 생성하지 않음
- Currentness는 Historical Final을 수정하지 않음
- Certificate는 Final/Composition/Currentness보다 강해질 수 없음
- Recovery/Retry는 이전 실패 history를 삭제하지 않음

## 14. 수용기준
각 sequence stage는 input/output/authority/evidence와 fail path를 가진다. orchestration 구현은 이 순서를 생략하거나 서로 다른 generation의 좋은 결과를 조립하지 않는다.
