# ONSure Semantic Assurance 통합·권위 지도

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
이 문서는 OBuilder에서 검증 관점으로 재사용 가치가 확인된 설계 Gate를 ONSure에 그대로 복제하지 않고, 기존 `docs/master/02~08`의 책임구조에 맞춰 **검증 Capability로 재구성·흡수**하기 위한 통합 기준을 정의한다.

이 확장은 기존 ONSure 기준선을 대체하지 않는다. 기존 02~08 문서가 계속 기능·리뷰·아키텍처·UI·시험·AI 방법론·미확정 결정의 정본 역할을 유지하며, 본 디렉터리 문서는 PR #44에서 해당 문서에 병합할 내용을 책임별로 분리해 상세화한 companion design이다.

## 2. 통합 원칙
1. OBuilder의 설계 생성 방법론 자체는 ONSure로 가져오지 않는다.
2. ONSure가 다른 제품의 결함을 발견하는 데 직접 필요한 **closure rule, adversarial oracle, evidence rule, authority rule**만 가져온다.
3. 동일 의미의 Gate를 여러 개의 ONSure 서비스로 복제하지 않는다. 의미가 겹치면 하나의 통합 Capability로 합친다.
4. 기존 ONSure 기능과 중복되면 신규 서비스가 아니라 기존 기능의 검증 깊이를 확장한다.
5. `DESIGNED`, `IMPLEMENTED`, `EXECUTED`, `EVIDENCE_BOUND`, `INDEPENDENTLY_VERIFIED`, `QUALIFIED`를 서로 다른 상태로 관리한다.
6. 문서에 적힌 설계만으로 구현·실행·PASS를 주장하지 않는다.
7. 신규 Capability는 향후 최소 `Requirement -> Contract -> Runtime Enforcement -> Negative Fixture -> Execution Evidence -> Qualification` 계보를 가져야 한다.
8. ONSure 자체 독립검토에서 발견된 문제도 별도 Capability를 난립시키지 않고 기존 SA-01~SA-14에 흡수한다.
9. `Unknown=0`, `Excluded`, `Human approved`, `Different model`, `Hidden`, `Retry PASS`, `Certificate issued` 같은 선언은 단독으로 assurance truth가 아니다.
10. 검증기 자체, Ground Truth producer, independence verifier, contamination classifier도 필요 시 Qualification 대상이다.

## 3. 14개 통합 Capability
| ID | Capability | 목적 | 주 책임 문서 |
|---|---|---|---|
| SA-01 | Evidence Reperformance & Truth Binding | 선언된 PASS/count/digest를 재수행·read-back 없이 신뢰하지 않음 | 02,03,04,06 |
| SA-02 | Denominator & Coverage Discovery | 기존 요구·기능 개수 자체를 정답으로 고정하지 않고 역방향으로 누락 탐색 | 02,06 |
| SA-03 | Obligation Closure Engine | 하나의 의무를 Function 하나로 닫지 않고 Function/Invariant/Evidence/Test의 결합으로 검증 | 02,03,04,06 |
| SA-04 | Authority Lifecycle Validator | 권한 생성·위임·철회·종료·시점·effect-time 유효성 검증 | 02,03,04,06 |
| SA-05 | Canonical State Authority Validator | canonical state writer/command/effect/recovery authority 단일성 검증 | 03,04,06 |
| SA-06 | Rights & Remedy Executability | 선언된 권리가 실제 Function/API/UX/State/Test로 행사 가능한지 검증 | 02,04,05,06 |
| SA-07 | Distributed Effect Integrity | handoff, batch, retry, compensation, terminal effect의 실제 item/effect truth 검증 | 02,04,06 |
| SA-08 | Freshness & Invalidation Graph | source/contract/evidence/report/certificate의 stale 전파와 현재성 검증 | 02,04,05,06 |
| SA-09 | Principal / Policy / SoD Validator | identity/representation, policy precedence, principal uniqueness, 직무분리 검증 | 03,04,06 |
| SA-10 | Privacy Disclosure & Observer Validator | body 외 status/latency/header/channel까지 포함한 정보누출·추론 가능성 검증 | 03,05,06 |
| SA-11 | AI Lifecycle & Authority Closure | AI-UC별 applicability부터 TEVV/human/freshness까지 전체 계보 검증 | 02,03,06,07 |
| SA-12 | Cross-Model Semantic Trace Validator | Function/Requirement/Component/State/API/Test 간 responsibility·authority 보존 검증 | 03,04,06 |
| SA-13 | Business Semantic Integrity | 금액·단위·정밀도·반올림·정산·자격 등 업무 의미 불변식 검증 | 03,04,06 |
| SA-14 | Validator Requalification Engine | detector/oracle/rule/method 변경 시 blind/isolated 재자격 검증 | 06,07,08 |

## 3-1. 독립검토에서 추가된 교차 통제의 배치
14개 Capability를 유지하되, 다음 항목을 필수 하위 통제로 흡수한다.

| 독립검토 항목 | 흡수 Capability | 핵심 요구 |
|---|---|---|
| Distributed Evidence Consistency | SA-01 | DB/Object/Queue/Git/Certificate partial commit을 CONSISTENT와 분리 |
| Result Selection / Retry Cherry-Picking | SA-01 | 모든 attempt 보존, 성공 retry만 선택 금지 |
| Trusted Time | SA-01, SA-08 | expiry/freshness/revocation의 시간 권위 검증 |
| Requirement Universe Authority | SA-02 | business/contract/code/runtime/rights/standard source class별 completeness |
| Authority Denominator Drift | SA-02 | legacy/docs/master/contract/runtime denominator 불일치 조정 |
| Unknown Discovery Coverage | SA-02 | unknown 0과 discovery completeness 분리 |
| Exclusion Abuse | SA-02 | Critical exclusion, 반복 제외, denominator 삭제 방지 |
| Assurance Level Ceiling | SA-03 | 평균이 아니라 critical assurance dimension의 최소수준으로 상한 결정 |
| Design-Omission Mutation | SA-03, SA-14 | Function/Right/Recovery/Observer/Denominator 제거 mutation |
| Independence Proof Recursion | SA-04, SA-09, SA-14 | principal/implementation/oracle/knowledge 독립성의 실제 provenance |
| Accepted Risk Accumulation | SA-09 | 반복 waiver, approver concentration, 누적위험 통제 |
| Offline Revocation | SA-08 | maximum offline freshness와 status unknown 처리 |
| Revocation Propagation | SA-08 | DB뿐 아니라 cache/CDN/downstream/offline bundle 전파 |
| Historical Revalidation Scale | SA-08 | reverse claim index와 scan completeness |
| Queue Replay / Authority Resurrection | SA-08 | authority/policy epoch, nonce, expiry 결속 |
| Assurance Recovery | SA-08 | 서비스 복구와 evidence/authority graph 복구 분리 |
| Assurance Communication Fidelity | SA-10 | NON_FINAL/PARTIAL을 PASS로 의미 상승 금지 |
| Assurance Surface Semantic Parity | SA-10 | Web/API/CLI/PDF/Certificate 동일 ontology |
| Human Misinterpretation Test | SA-10 | 색/배치/축약/접근성까지 실제 오인 검증 |
| Certificate Consumer Misuse | SA-10, SA-08 | scope/target/validity 무시 재사용 공격 |
| Ground Truth Qualification | SA-11, SA-14 | oracle/expert/real-world truth producer도 qualification |
| Memory-Blind Proof | SA-11, SA-14 | prior verdict/RAG/cache 접근의 기술적 차단 증거 |
| Human Reviewer Qualification | SA-11, SA-09 | domain/conflict/calibration/recency/blind capability |
| Reviewer Common-Mode Bias | SA-09, SA-11 | 동일 prior conclusion/shared draft를 독립으로 세지 않음 |
| Meta-Validator Qualification | SA-14 | Final Reconstructor/Invariant Engine/Independence Verifier 자체 검증 |
| Hidden/Golden Corpus Governance | SA-14 | owner/access log/rotation/leakage invalidation |
| Benchmark Precommitment | SA-14 | 결과 확인 전 benchmark denominator freeze |
| Semantic Contamination Classifier Qualification | SA-14 | classifier version/threshold/calibration/disagreement 관리 |
| Mutation Diversity | SA-14 | code/contract/authority/denominator/evidence/observer mutation 분리 |
| Validator Self-Improvement Governance | SA-14 | detector/rule/oracle 변경을 requalification event로 처리 |

세부 설계는 `09_INDEPENDENT_REVIEW_FINDINGS_INTEGRATION.md`를 따른다.

## 4. 기존 문서별 적용 책임
### 02 기능 요구사항 및 프로그램 명세
- 각 SA Capability의 사용자·시스템 기능, 입력, 산출물, 수용기준
- OPlanning/OReview/OVerification/OEvidence/OMemory와의 연결
- CoverageReport 앞단의 denominator discovery
- Rights/Authority/Distributed Effect/AI Use Case의 기능적 요구
- Requirement Universe, Unknown Discovery, Exclusion Abuse, Retry Selection, Trusted Time의 기능 요구

### 03 OReview 상세설계
- Capability별 Finding 유형
- Review rule, anti-pattern, 판정 근거
- 선언 결과와 실제 evidence 충돌 탐지
- 권한·상태·정책·privacy·business semantics의 의미 오류 탐지
- Independence self-attestation, Ground Truth unqualified, benchmark shopping, assurance surface drift Finding

### 04 아키텍처·데이터·API
- Service/Entity/State/API/Receipt/Graph의 논리 상세
- immutable identity, epoch, revision, authority owner
- cross-contract invariant와 read-back
- distributed effect와 stale propagation 구조
- EvidenceConsistencyTransaction, AttemptSelectionLedger, TrustedTimeEvidence, RevocationPropagationReceipt 등 cross-cutting records

### 05 UI·UX
- PASS보다 Evidence Strength, Scope/Unknown, Rights/Blocked Remedy, Freshness를 우선 표현
- privacy disclosure class와 cross-channel consistency
- AI human-review mode와 automation bias 경고
- offline/stale certificate, retry history, 누적 Accepted Risk, qualification limitation 노출
- 사용자 오인 가능성을 실제 Human Misinterpretation Test로 검증

### 06 시험·운영·구현
- Capability별 positive/negative/adversarial fixture
- kill/retry/race/stale/substitution/mutation 시험
- current revision evidence와 independent re-performance
- 운영 runbook 및 requalification trigger
- clock rollback, partial evidence commit, queue replay, benchmark shopping, hidden leakage, memory leakage, reviewer common-mode 시험

### 07 Component·AI Agent 방법론
- AI Use Case authority closure
- memory-blind / independent execution
- human judgment vs rubber-stamp 구분
- method/detector requalification
- Ground Truth producer와 Human Reviewer qualification

### 08 검토 체크리스트
- 계약/Runtime이 아직 없는 Capability를 DESIGN_ONLY/Open으로 추적
- 법무·사업·엔지니어링 결정이 필요한 임계치/정책 추적
- trusted time, offline freshness, benchmark governance, reviewer qualification, distributed evidence consistency 등의 미확정 정책 추적

## 5. 상태 모델
각 Capability는 아래 두 축을 분리한다.

### 설계/구현 성숙도
`DESIGN_ONLY -> CONTRACTED -> IMPLEMENTED -> EXECUTION_READY -> EXECUTED -> EVIDENCE_BOUND -> INDEPENDENTLY_VERIFIED -> QUALIFIED`

### 실행 판정
`PASS | FAIL | HOLD | BLOCKED | NOT_RUN | INCONCLUSIVE | NON_FINAL`

`IMPLEMENTED`는 `PASS`가 아니며 `EXECUTED`도 `EVIDENCE_BOUND`를 의미하지 않는다.

## 6. 공통 Machine Contract 원칙
모든 신규 Capability의 향후 machine artifact는 최소 다음을 가진다.
- `capability_id`
- `target_manifest_digest`
- `scope_or_denominator_epoch`
- `source_artifact_refs`
- `source_hashes`
- `validator_or_oracle_identity`
- `execution_state`
- `decision`
- `evidence_refs`
- `stale_triggers`
- `authority_boundary`

추가 cross-cutting 필드 후보:
- `attempt_set_digest`
- `time_authority_ref`
- `independence_attestation_ref`
- `requirement_universe_epoch`
- `unknown_discovery_coverage_ref`
- `exclusion_disposition_ref`
- `qualification_epoch`
- `current_revocation_epoch`

Schema와 Instance가 존재해도 Validator가 필수 필드를 실제 소비하지 않으면 enforcement로 보지 않는다.

## 7. 공통 Anti-False-Assurance 금지
- 실패 run을 삭제하고 성공 retry만 보고
- 결과를 본 뒤 benchmark/corpus 선택
- Hidden label만으로 contamination 없음 주장
- 다른 model/key만으로 independent 주장
- Human approval을 factual Ground Truth로 사용
- Unknown 0을 전체 completeness로 표현
- Critical 제외 후 denominator 축소
- stale/offline certificate를 현재 VALID처럼 표현
- 서비스 복구만으로 assurance graph 복구 주장
- 평균 Assurance score로 critical dimension 미달을 덮음

## 8. 비권위 경계
이 문서와 companion 문서의 추가는 구현 완료, Final PASS, Merge, Deployment, Production GO, Commercial GO, FinalLock을 승인하지 않는다. 기존 PR #44의 Draft/Non-final 경계를 유지한다.
