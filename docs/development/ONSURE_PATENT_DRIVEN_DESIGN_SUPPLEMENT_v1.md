# ONSure 특허 검토 기반 제품 보완 설계 v1

## 1. 목적

이 문서는 ONSure 특허 검토에서 발견된 제품 설계 공백을 실제 개발 가능한 수준으로 구체화한다. 특허 명세서의 권리 문구를 구현 요구사항으로 그대로 사용하지 않고, 입력·처리·상태·출력·실패조건·수용시험으로 변환한다.

- 문서 상태: `DESIGN_CANDIDATE`
- 구현 상태: `NOT_IMPLEMENTED_OR_PARTIAL`
- 제품 승인: `NOT_GRANTED`
- 관련 특허: `ONS-02`, `ONS-04`, `ONS-05`, `F-01`, `F-02`

## 2. 공통 원칙

1. 학습·원인분석·개선 후보를 생성한 구성요소는 자기 결과의 최종 적합 여부를 결정하지 않는다.
2. 확정되지 않은 입력은 임의 기본값으로 채우지 않고 `UNKNOWN`과 필요한 추가 증거를 기록한다.
3. 상태전이 직전에 대상 기준선, 정책, 실행환경 및 승인 조건을 다시 확인한다.
4. 추가 설계는 현재 구현 완료를 의미하지 않으며 수용시험과 실행 증거가 있을 때만 구현 상태를 올린다.
5. IDE, Git, 특정 AI 모델과 같은 제품명·구현수단은 교체 가능하며, 공통 상태와 증거 계약은 유지한다.

## 3. ONS-02 개선 또는 재개발 경로 결정

### 3.1 목적

복수 결함을 각각 수정하는 데 그치지 않고 공통 원인, 영향범위, 자산 보존 가능성 및 회귀위험을 계산하여 다음 경로 중 하나를 결정한다.

- 국소 부분수정
- 복수 자산 수정
- 모듈 교체
- 구조 재구성
- 전면 재개발
- 추가 검증 또는 사람 판단 대기

### 3.2 입력 계약

`TargetBaseline`은 `target_id`, source 또는 package digest, version, build 및 dependency digest, runtime environment digest, policy digest, architecture asset와 external contract 식별자를 포함한다.

`Finding`은 `finding_id`, expected, actual, severity, affected assets, reproduction scenario, evidence receipts, occurrence count, prior improvements 및 fingerprint를 포함한다.

`CausalAssessment`는 원인 식별자, 최초 실패지점, 직접원인, 근본원인 후보, confidence, confirmation state, 지지·반증 증거 및 영향 자산을 포함한다.

`SoftwareAsset`은 파일·모듈·서비스·데이터 저장소·API·프롬프트·정책을 나타내며 dependency, interface/data contract, tests, replaceability, statefulness, criticality 및 ownership boundary를 포함한다.

`ChangeHistory`는 종전 변경 자산, 해결·재발·신규 결함, rollback 결과, 비용 및 before/after receipt를 포함한다. `ConstraintSet`은 보존 대상, 금지 변경, 허용 중단시간, 데이터 이관 제한, 규제·보안, 비용 및 기한을 포함한다.

### 3.3 인과영향 그래프

그래프는 결함 `F`, 원인 `C`, 자산 `A`, 시험·Oracle `T`, 외부 계약 `K`, 종전 변경 `H` 노드를 포함한다. 관계는 다음을 포함한다.

```text
CAUSED_BY(F,C)
LOCATED_IN(C,A)
DEPENDS_ON(A,A)
CONSUMES(A,K)
VERIFIED_BY(A,T)
CHANGED_BY(A,H)
RECURRED_AFTER(F,H)
CONTRADICTS(Evidence,C)
```

각 관계는 source digest, confidence, valid period 및 evidence receipt를 갖는다. 후보 변경 자산에서 정방향·역방향 의존 폐쇄를 계산하며 외부 계약 또는 미등록 경계에서 탐색이 중단되면 이를 완전한 영향범위로 취급하지 않고 `TRUNCATED_IMPACT_BOUNDARY`로 기록한다.

### 3.4 평가값 실시예

값은 0 이상 1 이하로 정규화하며 가중치와 임계값은 버전 관리되는 정책으로 둔다. 다음 식은 기본 실시예이고 청구범위나 구현을 특정 수치에 고정하지 않는다.

```text
CauseConcentration = common-cause weighted findings / all weighted findings
ImpactSpread = impacted weighted assets / all registered weighted assets
Recurrence = min(1, equivalent recurrence count / saturation count)
RegressionRisk = 0.35*ImpactSpread + 0.25*Coupling
               + 0.20*StateDataImpact + 0.20*TestGap
EvidenceReliability = 0.30*Reproducibility + 0.25*CausalConfirmation
                    + 0.20*BaselineCompleteness + 0.15*ObservationDirectness
                    + 0.10*IndependentVerification
Preservability = verified unaffected asset weight / all asset weight
```

`Coupling`은 함께 변경해야 하는 계약 경계와 순환 의존 수를 정책 포화값으로 정규화한다. 필수 증거가 `NOT_RUN`이면 0점으로 조용히 대체하지 않고 `ER_INCOMPLETE`로 판정한다.

### 3.5 경로 결정 규칙 실시예

| 경로 | 예시 조건 |
|---|---|
| 부분수정 | 원인 집중도 `<0.45`, 영향 확산도 `<0.25`, 회귀위험 `<0.40`, 예상 해결도 `>=0.70` |
| 복수 자산 수정 | 영향 확산도 `<0.45`, 회귀위험 `<0.55`, 동일 계약 경계 안에서 해결 가능 |
| 모듈교체 | 원인 집중도 `>=0.60`, 원인이 교체 가능한 모듈에 집중, 보존 가능도 `>=0.60` |
| 구조 재구성 | 원인 집중도 `>=0.60`, 영향 확산도 또는 결합도 `>=0.60`, 외부 계약 보존 가능 |
| 전면 재개발 | 원인 집중도 `>=0.70`, 영향 확산도 `>=0.65`, 반복 실패도 `>=0.67`, 보존 가능도 `<0.35` |

후보별로 예상 해결도, 회귀위험, 보존도, 이관위험, 검증비용 및 증거 신뢰도 벡터를 생성한다. 금지조건을 먼저 적용하고, 남은 후보의 Pareto 우월관계 또는 정책 가중합을 비교한다. 상위 두 후보 차이가 임계값보다 작으면 자동 선택하지 않고 `SHADOW_COMPARE` 또는 `HUMAN_APPROVAL_REQUIRED`로 전이한다.

### 3.6 HOLD 조건

- 차단 또는 높은 결함의 원인이 후보 상태인 경우
- 증거 신뢰도가 정책 임계값 미만이거나 불완전한 경우
- 계산 후 source, policy, architecture 또는 dependency snapshot이 변경된 경우
- 영향 폐쇄가 미등록 외부 시스템에서 잘린 경우
- 상태 데이터의 migration 또는 rollback 가능성이 불명확한 경우
- 정상동작 Oracle 또는 필수 회귀시험이 없는 경우
- 보존 대상과 폐기 대상에 같은 자산이 포함된 경우
- 규제·권리·승인 제약을 충족하지 못한 경우

HOLD 출력은 `missing_evidence`, `required_experiment`, `affected_candidate_paths`, `resume_condition`을 포함한다.

### 3.7 수용검증

부분수정은 실제 변경집합이 허용 경계를 지켰는지 확인한다. 모듈교체는 외부 계약, 데이터 migration 및 rollback을 추가 확인한다. 재개발은 요구조건 coverage, 데이터 의미 보존 또는 의도적 변경, 병행운영 및 cutover를 확인한다.

결과 상태는 `PATH_ACCEPTED`, `PATH_INEFFECTIVE`, `PATH_REGRESSION`, `HOLD` 중 하나이다. 실패한 경로는 삭제하지 않고 반증 이력으로 저장해 평가값과 후보 순위를 다시 계산한다.

```text
EVIDENCE_COLLECTED
→ GRAPH_MATERIALIZED
→ METRICS_COMPUTED
→ PATH_CANDIDATES
→ PATH_SELECTED
→ APPROVAL_REQUIRED
→ SHADOW_EXECUTION
→ ACCEPTANCE_VALIDATION
→ PATH_ACCEPTED
```

### 3.8 수용 사례

결제 프로그램의 세 결함이 일부 입력·계산 함수에만 연결되고 외부 결제 계약이 변경되지 않으면 부분수정을 선택한다. 인증 프로그램의 복수 보안결함이 하나의 교체 가능한 모듈에 집중되고 외부 토큰·사용자 데이터 계약을 보존할 수 있으면 모듈교체를 우선한다. 외부 계약과 데이터 구조 자체가 결함 원인이고 영향 확산도와 반복 실패도가 높으며 보존 가능도가 낮으면 재개발 명세를 생성한다.

## 4. ONS-04 작업환경·실행노드 전환

### 4.1 전환 판정

종전 실행 주체와 다른 IDE, Web, CLI, SDK 또는 실행 노드가 체크포인트 이후 작업을 요청하면 화면 상태를 복사하는 대신 Project, Session, source baseline, 정책, Finding, 계획, 역할, 환경 및 체크포인트 지문을 다시 측정한다.

```text
ACTIVE → TRANSFER_VALIDATED → RESUMABLE
ACTIVE → TRANSFER_BLOCKED
TRANSFER_VALIDATED → REVALIDATION_REQUIRED
VERIFICATION_COMPLETE → LOCAL_DIVERGENCE
EXECUTION_READY → NODE_TRANSFER_VALIDATED
EXECUTION_READY → NODE_TRANSFER_BLOCKED
```

### 4.2 실시예

VS Code에서 생성한 Finding과 개선계획을 Web에서 승인할 때 Commit, blob, Finding, 계획, 정책 및 승인 역할이 모두 일치해야 승인 화면을 연다. 사이에 Commit이 변경되면 `REVALIDATION_REQUIRED`로 전이한다.

CLI 검증 결과를 IDE에서 이어갈 때 Workspace가 기준선과 같으면 Task Graph를 복원한다. 영향 파일에 로컬 변경이 있으면 `LOCAL_DIVERGENCE`로 전이하고 새 기준선 등록 또는 격리 작업영역을 요구한다. IntelliJ와 Eclipse는 현재 구현 주장이 아니라 대체 실시형태이다.

동일 Web 화면에서도 실행 노드가 바뀌면 노드 신뢰등급, 지역, 이미지, 도구, 네트워크 및 비밀정보 권한을 비교한다. 권한이 확대되거나 데이터 지역·환경이 다르면 종전 승인을 사용하지 않는다.

## 5. ONS-05 구조·행동 프로필 학습

### 5.1 충돌 판정

구조 프로필의 예상 책임·호출 그래프와 동일 대상 기준선에 결속된 행동 프로필의 실제 관찰을 비교한다. 불일치는 즉시 결함으로 확정하지 않고 실행조건, Feature Flag, Mock, 외부 장애 및 관찰 신뢰등급을 확인하는 추가 시나리오를 생성한다.

```text
PROFILE_ACTIVE → PROFILE_CONFLICT_PENDING
PROFILE_CONFLICT_PENDING → CONFIRMED_BEHAVIOR_GAP
PROFILE_CONFLICT_PENDING → OBSERVATION_INVALIDATED
PROFILE_ACTIVE → UNDECLARED_BEHAVIOR
PROFILE_ACTIVE → PROGRAM_PROFILE_STALE
```

구조에는 외부 전송 권한이 없으나 행동 추적에서 미등록 전송이 관찰되고 Tool Contract와 승인에도 없으면 `UNDECLARED_BEHAVIOR`로 판정한다. 최신 계약에는 허용되어 있지만 구조 프로필만 오래된 경우 `PROGRAM_PROFILE_STALE`로 판정한다.

### 5.2 선택 재학습

source, prompt, RAG 자료, 도구계약 또는 정책이 변경되면 파일 diff뿐 아니라 호출·데이터·프롬프트·Fixture·Oracle 관계의 영향 폐쇄를 계산한다. 영향 학습자산은 `STALE`, 의존경로가 없고 byte 지문이 같으며 unknown이 없는 자산은 `UNAFFECTED_PRESERVED`, 동적 의존성을 결정할 수 없으면 `IMPACT_UNKNOWN`으로 전이한다.

```text
LEARNING_ASSET_ACTIVE → STALE → ACTIVE_NEW_BASELINE
LEARNING_ASSET_ACTIVE → UNAFFECTED_PRESERVED
LEARNING_ASSET_ACTIVE → IMPACT_UNKNOWN
STALE → REJECTED_OR_HOLD
```

정책만 바뀐 경우에도 정책과 연결된 Constraint, Behavior, Oracle 및 시나리오를 `STALE_BY_POLICY`로 전이하고 새 정책의 실제 원문 지문과 적용범위를 결속하여 재학습한다.

## 6. 미래 학습 기능

학습 후보는 장기적으로 `CANDIDATE → SHADOW → CANARY → STABLE → LOCKED`의 승격 수명주기를 사용한다. 학습부와 독립된 검증부, 비공개 시험 격리, 적용 후 오탐·회귀 감시, 자동 강등 및 이전 안정 버전 복구를 구현한다.

학습된 구조·행동 프로필에서 미검증 고위험 영역을 산출해 변형·경계·적대 시나리오를 생성하되, 생성부와 독립된 Oracle이 결과를 판정하도록 한다.

## 7. 사용자 검수 중심 보증 케이스

ONSure의 최상위 업무 단위는 개별 분석 실행이 아니라 `AssuranceCase`이다. 사용자는 시스템이 소스를 받았다는 사실만이 아니라 무엇을 이해했고, 무엇을 검증하며, 발견사항을 어떻게 처리하고, 어떤 결과를 배포하는지 단계별로 확인·정정·승인할 수 있어야 한다.

```text
CASE_DRAFT → INTAKE_RECEIVED → SOURCE_QUARANTINED → SOURCE_BASELINE_LOCKED
→ UNDERSTANDING_CANDIDATE → AWAITING_UNDERSTANDING_REVIEW
→ UNDERSTANDING_ACCEPTED | UNDERSTANDING_PARTIAL | HOLD
→ ACCEPTANCE_BASELINE_DRAFT → AWAITING_SCOPE_ACCEPTANCE → SCOPE_ACCEPTED
→ PLAN_ESTIMATED → AWAITING_PLAN_APPROVAL → READY → VALIDATING
→ FINDINGS_READY → FINDING_REVIEW_OPEN → FINDINGS_DISPOSED
→ OPTIONS_READY → AWAITING_OPTION_APPROVAL → IMPROVING
→ INDEPENDENT_REVERIFYING → DELIVERY_CANDIDATE
→ AWAITING_DELIVERY_APPROVAL → DELIVERED → POST_DEPLOY_OBSERVING → CLOSED
```

필수 계약은 `assurance-case`, `intake-declaration`, `understanding-review-bundle`, `acceptance-baseline`, `plan-estimate`, `finding-disposition`, `improvement-option-set`, `independent-reverification-mandate`, `deployment-attestation`, `post-deployment-observation`, `case-closure-receipt`이다. 사용자 정정은 즉시 사실로 덮어쓰지 않고 `USER_ASSERTED`로 기록한 뒤 소스·실행·문서 증거로 재확인한다.

검증 범위 승인에는 대상 기준선, 포함·제외 자산, 환경, 위험등급, 판정기준, Oracle, 허용된 미실행 항목, 예상 시간·비용·자원 상한과 중단조건을 포함한다. 상한을 넘으면 자동 계속하지 않고 재승인을 요구한다. 결함은 `ACCEPTED`, `CHALLENGED`, `FALSE_POSITIVE`, `DEFERRED`, `RISK_ACCEPTED`, `FIX_REQUIRED`로 처분하되 원본 증거와 개정 이력을 삭제하지 않는다.

개선 선택지는 `MINIMAL`, `ROBUST`, `MODULE_REPLACE`, `REDESIGN`, `REDEVELOP`, `RISK_ACCEPT`를 지원하며, 각 선택지에 해결 결함, 변경경계, 보존항목, 회귀위험, 예상 비용, rollback 및 수용기준을 제공한다. 개선 생성자와 독립 재검증자는 동일한 최종 판정 권한을 공유하지 않는다. 검증된 산출물 지문과 실제 전달·배포 산출물 지문이 다르면 전달을 차단하고, 배포 후 관찰에서도 승인된 수용기준과 회귀 신호를 확인한 뒤에만 케이스를 종료한다.

역할은 최소한 소스 제공자, 검수자, 승인자, 개선 실행자, 독립 검증자, 배포 승인자 및 자료관리자로 분리한다. 소규모 조직에서 한 사람이 복수 역할을 맡더라도 동일 작업의 생성·최종판정과 변경·배포 승인은 정책상 분리하거나 명시적 예외 영수증을 요구한다.

## 8. 소스 취득·수탁·외부 저장소·사후처리

### 8.1 취득 경로와 격리

대상은 저장소뿐 아니라 개별 소스파일, 복수 파일, ZIP 또는 기타 압축객체, 빌드 산출물, 패키지, 실행 이미지, 실행 서비스 및 API로 취득할 수 있다. 취득 직후 실행하지 않고 격리영역에서 경로이탈, 심볼릭 링크 이탈, 악성객체, 실행가능 객체, 비밀정보 및 비정상 크기를 검사한다. 매니페스트는 출처, 파일경로, 크기, 유형, 다이제스트, 서명, 불변 저장소 참조, 취득시각 및 검사결과를 기록한다.

대상과 매니페스트는 사용자·Tenant·프로젝트·처리목적·허용행위·보존정책에 결속한다. Tenant별 저장경로, 암호화 키, 접근정책과 실행영역을 분리하며 업로드 대상과 외부 저장소 대상을 함께 검증하더라도 각각의 출처와 무결성 계보를 유지한다.

### 8.2 외부 저장소 사용자 정보

외부 저장소는 OAuth 위임 또는 설치형 App을 기본으로 하고 장기 개인 토큰은 예외로 다룬다. 자격정보 원문은 비밀정보 저장부에 두며 실행부에는 단기·최소권한 참조만 제공한다. 권한은 사용자·Tenant·프로젝트·Provider·저장소·불변 ref·허용행위·만료시각에 결속하고 읽기, Commit, Push, 변경요청, 병합 및 배포 권한을 구분한다.

각 외부 행위 직전에 토큰 유효성뿐 아니라 현재 사용자 권한, ref 상태, 보호정책, 설치상태와 승인범위를 재검사한다. 철회·만료·프로젝트 종료 시 토큰 참조, webhook, clone, worktree 및 임시 자격정보를 정리하고 영수증을 생성한다.

### 8.3 완료·반환·배포 이후 처리

작업완료, 결과반환, 저장소반영, 배포, 계약종료 또는 보존기간 만료를 처분 트리거로 관리한다. 자료별로 고객 반환, 기간보관, 검증 증거만 보존, 법적보존, 삭제 중 하나를 결정한다. 삭제 범위는 원본, 복제본, worktree, 임시영역, 로그 원문, 캐시, 색인, 임베딩, 파생자료 및 백업 참조를 포함하며 물리·논리·암호학적 삭제 또는 접근불능화 결과를 구분해 기록한다.

법적보존은 일반 삭제보다 우선하지만 목적·권한·기간이 명시된 별도 승인 객체가 있어야 한다. 삭제 뒤에도 고객 소스를 복원할 수 없는 최소 영수증은 대상 지문, 범위, 정책, 처리시각, 처리방식, 성공·실패 항목과 검증자를 포함한다.

### 8.4 학습 목적 제한과 어댑터 권한 분리

고객 소스와 파생표현은 기본적으로 해당 프로젝트 범위이며 자동 공통학습에 사용하지 않는다. 프로젝트 내부 적응, Tenant 내부 재사용, Tenant 간 재사용을 별도 동의로 구분한다. 범위 확대 전 권리, 처리목적, 비밀정보, 재식별·재구성 위험과 독립 성능을 검사하며, 허용되더라도 원본 재구성이 어려운 표현만 지정 범위에서 사용한다.

파일·ZIP·저장소·런타임·API 어댑터는 표준 증거와 무결성 정보를 만들 수 있으나 정책 변경, 최종 적합 판정 또는 승인 없는 대상 변경은 수행하지 못한다. 독립 검증부가 증거의 사용 가능성과 처리범위를 결정한다.

## 9. 전 기능 적용 원칙

본 문서와 구현 대장에 열거된 기능은 선택적 아이디어 목록이 아니라 ONSure 제품 보완 범위 전체이다. 우선순위는 구현 순서와 의존성만 나타내며 제외 권한을 뜻하지 않는다. 각 항목은 구현, 계약, 권한검사, 정상·실패 시험, 사용자 흐름, 실행 Receipt 및 운영 관찰까지 완료되어야 `DONE`으로 전이한다. 제외가 필요한 경우 제품 책임자와 보안·법무 검토가 결속된 범위변경 영수증을 남긴다.

## 10. 추적

세부 구현 작업, 시험 및 완료 증거는 `ONSURE_PATENT_DRIVEN_IMPLEMENTATION_BACKLOG_v1.md`에서 관리한다.
