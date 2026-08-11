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

## 4. 기존 문서별 적용 책임
### 02 기능 요구사항 및 프로그램 명세
- 각 SA Capability의 사용자·시스템 기능, 입력, 산출물, 수용기준
- OPlanning/OReview/OVerification/OEvidence/OMemory와의 연결
- CoverageReport 앞단의 denominator discovery
- Rights/Authority/Distributed Effect/AI Use Case의 기능적 요구

### 03 OReview 상세설계
- Capability별 Finding 유형
- Review rule, anti-pattern, 판정 근거
- 선언 결과와 실제 evidence 충돌 탐지
- 권한·상태·정책·privacy·business semantics의 의미 오류 탐지

### 04 아키텍처·데이터·API
- Service/Entity/State/API/Receipt/Graph의 논리 상세
- immutable identity, epoch, revision, authority owner
- cross-contract invariant와 read-back
- distributed effect와 stale propagation 구조

### 05 UI·UX
- PASS보다 Evidence Strength, Scope/Unknown, Rights/Blocked Remedy, Freshness를 우선 표현
- privacy disclosure class와 cross-channel consistency
- AI human-review mode와 automation bias 경고

### 06 시험·운영·구현
- Capability별 positive/negative/adversarial fixture
- kill/retry/race/stale/substitution/mutation 시험
- current revision evidence와 independent re-performance
- 운영 runbook 및 requalification trigger

### 07 Component·AI Agent 방법론
- AI Use Case authority closure
- memory-blind / independent execution
- human judgment vs rubber-stamp 구분
- method/detector requalification

### 08 검토 체크리스트
- 계약/Runtime이 아직 없는 Capability를 DESIGN_ONLY/Open으로 추적
- 법무·사업·엔지니어링 결정이 필요한 임계치/정책 추적

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

Schema와 Instance가 존재해도 Validator가 필수 필드를 실제 소비하지 않으면 enforcement로 보지 않는다.

## 7. 비권위 경계
이 문서와 companion 문서의 추가는 구현 완료, Final PASS, Merge, Deployment, Production GO, Commercial GO, FinalLock을 승인하지 않는다. 기존 PR #44의 Draft/Non-final 경계를 유지한다.
