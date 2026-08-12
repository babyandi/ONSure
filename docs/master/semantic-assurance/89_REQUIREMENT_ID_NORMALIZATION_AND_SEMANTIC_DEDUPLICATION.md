# ONSure Requirement ID 정규화·Semantic Deduplication 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `88_GLOBAL_REQUIREMENT_UNIVERSE_AND_DENOMINATOR.md`

## 1. 목적
문서마다 같은 요구를 다른 이름으로 쓰거나, 하나의 요구가 여러 절에 반복되거나, 반대로 같은 용어가 서로 다른 의미로 쓰이는 문제를 Design Lock 전에 제거한다.

## 2. 정규화 단계
1. source text 보존
2. normative verb 식별(MUST/MUST_NOT/SHALL/REQUIRED에 해당하는 한국어 포함)
3. subject/action/object/condition/effect 구조 추출
4. semantic key 생성
5. 기존 canonical requirement와 비교
6. relation 후보 생성
7. P0/P1 conflict 분류

## 3. Canonical Semantic Tuple
`(subject, operation, object, condition, expected_effect, failure_ceiling, authority_scope)`

같은 tuple은 duplicate 후보, 일부 축이 더 좁거나 구체적이면 refine 후보로 본다.

## 4. 금지되는 자동 병합
다음은 문자열 유사도가 높아도 자동 병합하지 않는다.
- PASS vs CURRENT
- Approval vs Acceptance
- Deployment success vs Assurance success
- Independent execution vs Independent authority
- Retry success vs stable success
- N/A vs PASS
- Historical validity vs current usability

## 5. Naming Canonicalization
우선 canonical naming:
- FinalLock
- FinalApproval
- HumanAcceptance
- AssuranceCurrentness
- AssuranceCertificate
- RequirementUniverseSnapshot
- CompositionSnapshot
- AuthorityGrant
- RecoveryQualificationReceipt
- ONSureReleaseQualification

Alias는 유지하되 authority 문서와 machine contract에서는 canonical name을 사용한다.

## 6. Conflict Class
- NAME_ONLY
- ENUM_SEMANTIC
- AUTHORITY_SEMANTIC
- LIFECYCLE_SEMANTIC
- ASSURANCE_STRENGTH
- CURRENTNESS_SEMANTIC
- DENOMINATOR_SEMANTIC
- SECURITY_BOUNDARY

P0 class: AUTHORITY_SEMANTIC, ASSURANCE_STRENGTH, SECURITY_BOUNDARY, Final/Currentness 관련 lifecycle contradiction.

## 7. Duplicate Resolution Receipt 후보
- canonical_requirement_id
- duplicate_requirement_ids[]
- relation
- rationale
- source_digests[]
- decided_by
- reviewed_by
- decision_time

## 8. 수용기준
- canonical requirement ID 하나에 서로 모순되는 의미를 합치지 않는다.
- duplicate 제거로 mandatory requirement가 denominator에서 사라지지 않는다.
- alias가 machine contract 이름을 바꾸지 않는다.
- conflict 미해결 상태에서 baseline lock 금지.
