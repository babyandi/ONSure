# ONSure P0 Machine Contract Closure Blueprint

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
29~57에서 정의된 핵심 설계를 Claude가 임의 이름/필드로 구현하지 않도록 다음 P0 machine contract의 canonical naming과 책임을 고정한다.

## 2. 신규 Contract Set
1. `assurance-policy-profile.v2`
2. `assurance-subject-graph.v2`
3. `assurance-composition-snapshot.v2`
4. `evidence-graph-head.v2`
5. `assurance-currentness-generation.v2`
6. `assurance-certificate.v2`
7. `certificate-revocation-receipt.v2`
8. `authority-grant.v2`
9. `operation-intent.v2`
10. `operation-lifecycle-receipt.v2`
11. `distributed-work-unit.v2`
12. `distributed-aggregation-receipt.v2`
13. `ai-behavior-population.v2`
14. `onsure-release-qualification.v2`
15. `recovery-qualification-receipt.v2`
16. `design-trace-registry.v1`

## 3. 공통 필드
모든 authoritative object는 가능한 경우:
- contract/version
- object_id
- organization/tenant scope
- subject/context digest
- generation/epoch
- created_at
- producer principal
- authority/key reference
- canonicalization profile
- self/object digest
- signature 또는 signing metadata
를 가진다.

## 4. 공통 금지
- free-form PASS 문자열로 의미 확장
- caller-provided authority/self-attestation
- missing epoch/freshness를 default current로 처리
- ID만 있고 digest binding 없음
- count만 있고 exact population 없음
- 동일 Schema가 self/independent/final 의미를 discriminator 없이 혼합

## 5. Cross-Contract Invariant
- Certificate strength ≤ Composition/Final/Currentness/Qualification ceiling
- Composition subject population = SubjectGraph locked population
- Currentness source_final_lock_digest가 실제 FinalLock과 일치
- Revocation certificate_id + issuance generation 일치
- AuthorityGrant scope가 OperationIntent scope를 포함
- OperationLifecycle effect class = registered operation effect class
- Aggregation population = exact completed logical work population
- ONSureReleaseQualification build digest = 실제 validator build manifest

## 6. Fixture 최소기준
Contract당:
- valid 1
- type/schema invalid 1
- semantic invalid 2 이상
- cross-contract invalid 1 이상

P0 Contract는 단순 JSON Schema fixture뿐 아니라 CrossContractInvariantEngine fixture를 가져야 한다.

## 7. Activation
Contract 파일 생성 → Fixture → static validation → runtime consumer → shadow → migration/reconstruction → independent verification 순서다. Contract 존재만으로 active selector를 변경하지 않는다.

## 8. 완료기준
위 16개가 exact field/invariant/fixture/operation/evidence mapping을 가져야 P0 machine contract design closure 후보가 된다.
