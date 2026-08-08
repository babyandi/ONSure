# ONSure 특허 기반 구현 할 일 대장 v1

## 상태

- 대장 상태: `ACTIVE_DESIGN_BACKLOG`
- 완료 주장: `PROHIBITED_WITHOUT_EVIDENCE`
- 기준 설계: `ONSURE_PATENT_DRIVEN_DESIGN_SUPPLEMENT_v1.md`

## 작업 원칙

각 작업은 계약, 구현, 단위시험, 실패주입, 전체 사용자 흐름 및 실행 Receipt가 연결되어야 완료된다. 클래스나 문서가 존재하는 것만으로 완료하지 않는다.

| ID | 관련 특허 | 구현 작업 | 우선순위 | 현재 상태 | 완료 증거 |
|---|---|---|---:|---|---|
| PAT-DES-001 | ONS-02 | TargetBaseline·Finding·CausalAssessment·SoftwareAsset·ChangeHistory·ConstraintSet 계약 정의 | P0 | `NOT_STARTED` | Schema validation과 invalid fixture |
| PAT-DES-002 | ONS-02 | 인과영향 그래프 생성과 잘린 외부 경계 표현 | P0 | `NOT_STARTED` | graph fixture 및 closure test |
| PAT-DES-003 | ONS-02 | 원인집중도·영향확산도·결합도·회귀위험·보존가능도 계산기 | P0 | `NOT_STARTED` | 계산 예제 golden test |
| PAT-DES-004 | ONS-02 | 부분수정·모듈교체·구조재구성·재개발 후보와 HOLD 결정기 | P0 | `NOT_STARTED` | decision table mutation test |
| PAT-DES-005 | ONS-02 | 경로별 실행명세와 수용기준 생성 | P0 | `NOT_STARTED` | payment/auth example artifacts |
| PAT-DES-006 | ONS-02 | Shadow 비교, 경로 수용검증 및 실패 후 재평가 | P1 | `NOT_STARTED` | PATH_ACCEPTED/REGRESSION/HOLD E2E |
| PAT-SUR-001 | ONS-04 | 공통 Session·Task Graph·Checkpoint 전환 계약 | P0 | `PARTIAL` | CLI/API/VS Code contract parity |
| PAT-SUR-002 | ONS-04 | 다른 인터페이스·실행노드 요청 시 문맥 재측정과 재개범위 결정 | P0 | `NOT_STARTED` | transition failure injection |
| PAT-SUR-003 | ONS-04 | VS Code→Web 승인 및 CLI→IDE 재개 흐름 | P1 | `NOT_STARTED` | two-surface E2E receipts |
| PAT-SUR-004 | ONS-04 | 로컬 divergence와 실행노드 권한 증가 차단 | P0 | `NOT_STARTED` | negative tests |
| PAT-LRN-001 | ONS-05 | Program Profile과 Behavior Profile의 동일 대상 기준선 결속 | P0 | `PARTIAL` | profile/receipt hash test |
| PAT-LRN-002 | ONS-05 | 구조 예상과 행동 관찰의 충돌·미관찰·불확실 판정 | P0 | `NOT_STARTED` | profile conflict fixtures |
| PAT-LRN-003 | ONS-05 | 관찰 신뢰등급과 간접관찰의 운영 동작 과장 차단 | P0 | `PARTIAL` | coverage downgrade tests |
| PAT-LRN-004 | ONS-05 | source·prompt·policy 변경 영향 폐쇄와 선택 재학습 | P1 | `NOT_STARTED` | stale/preserved/unknown mutation tests |
| PAT-LRN-005 | F-01 | 학습 후보 승격 등록소, Shadow·Canary·Stable·Locked 상태기계 | P1 | `NOT_STARTED` | promotion transition receipts |
| PAT-LRN-006 | F-01 | 적용 후 회귀 감시, 자동 강등 및 이전 버전 복구 | P1 | `NOT_STARTED` | rollback E2E |
| PAT-LRN-007 | F-02 | 미검증 영역 coverage·위험도와 독립 Oracle 시나리오 생성 | P2 | `NOT_STARTED` | hidden-oracle isolation test |
| PAT-CASE-001 | ONS-01~06 | AssuranceCase 최상위 상태기계와 모든 전이 계약 구현 | P0 | `NOT_STARTED` | 전체 상태 전이 및 금지전이 시험 |
| PAT-CASE-002 | ONS-01·05 | 소스 이해 Review Bundle, 사용자 정정 `USER_ASSERTED`, 증거 재확인 | P0 | `NOT_STARTED` | 정정·충돌·부분승인 E2E |
| PAT-CASE-003 | ONS-01·02 | Acceptance Baseline의 포함·제외 범위, Oracle, 위험, 미실행 승인 | P0 | `NOT_STARTED` | scope drift와 unknown 차단 시험 |
| PAT-CASE-004 | ONS-02 | 시간·비용·자원 추정과 상한 초과 중단·재승인 | P0 | `NOT_STARTED` | budget stop 및 resume receipt |
| PAT-CASE-005 | ONS-01 | Finding 이의제기·오탐·유예·위험수용·수정요구 처분과 불변 이력 | P0 | `NOT_STARTED` | challenge/revision audit E2E |
| PAT-CASE-006 | ONS-02 | MINIMAL·ROBUST·MODULE_REPLACE·REDESIGN·REDEVELOP·RISK_ACCEPT 선택지 | P0 | `NOT_STARTED` | 옵션별 비용·경계·rollback 비교 |
| PAT-CASE-007 | ONS-03 | 개선 생성자와 독립 재검증자의 권한·환경·증거 분리 | P0 | `NOT_STARTED` | same-actor denial 및 exception receipt |
| PAT-CASE-008 | ONS-03 | 검증 산출물과 실제 전달·배포 산출물 지문 동일성 강제 | P0 | `NOT_STARTED` | artifact substitution negative test |
| PAT-CASE-009 | ONS-01·03 | 배포 후 회귀 관찰, 수용기준 재확인과 Case Closure Receipt | P1 | `NOT_STARTED` | observe/rollback/close E2E |
| PAT-CASE-010 | ONS-03·06 | 제공자·검수자·승인자·실행자·검증자·배포자·자료관리자 역할 정책 | P0 | `NOT_STARTED` | role conflict 및 delegated approval test |
| PAT-CUS-001 | ONS-06 | 파일·다중파일·ZIP·빌드·패키지·이미지·서비스·API 취득 어댑터 | P0 | `NOT_STARTED` | 경로별 동일 증거계약 conformance |
| PAT-CUS-002 | ONS-06 | 격리 해제, path traversal·symlink·malware·secret·크기 제한 검사 | P0 | `NOT_STARTED` | hostile archive corpus test |
| PAT-CUS-003 | ONS-06 | 출처·무결성 매니페스트와 사용자·Tenant·프로젝트·목적·행위 결속 | P0 | `NOT_STARTED` | binding tamper 및 mixed-source test |
| PAT-CUS-004 | ONS-06 | Tenant별 저장·암호화 키·접근정책·실행환경 격리 | P0 | `NOT_STARTED` | cross-tenant access denial |
| PAT-CUS-005 | ONS-06 | OAuth/App/비밀저장소 기반 외부 저장소 권한 및 단기 최소권한 참조 | P0 | `NOT_STARTED` | token non-exposure와 scope test |
| PAT-CUS-006 | ONS-06 | 사용자·Tenant·프로젝트·저장소·ref·행위 결속과 매 행위 재검사 | P0 | `NOT_STARTED` | revoke/ref/protection mutation test |
| PAT-CUS-007 | ONS-06 | 완료·반환·반영·배포·만료 처분 결정과 법적보존 | P0 | `NOT_STARTED` | disposition decision table |
| PAT-CUS-008 | ONS-06 | 원본·clone·worktree·로그·캐시·색인·임베딩·백업 삭제 및 영수증 | P0 | `NOT_STARTED` | deletion coverage와 failure retry test |
| PAT-CUS-009 | ONS-05·06 | 프로젝트·Tenant·공통 학습 동의 분리와 비복원성·권리 검사 | P0 | `NOT_STARTED` | consent boundary와 reconstruction test |
| PAT-CUS-010 | ONS-06 | 취득 어댑터와 정책·최종판정·변경 권한 분리 | P0 | `NOT_STARTED` | adapter privilege escalation test |
| PAT-LRN-008 | ONS-05 | 프로젝트 경험 재사용 후보의 안전한 일반화와 범위 제한 | P1 | `NOT_STARTED` | rights/purpose/reidentification gate test |

## 공통 수용조건

1. 모든 상태전이는 source·policy·environment 식별값을 기록한다.
2. `UNKNOWN`, `NOT_RUN`, `HOLD`, `INCONCLUSIVE`를 PASS로 변환하지 않는다.
3. 생성자와 독립 검증자의 권한 및 실행 증거를 구분한다.
4. 현재 구현 경로뿐 아니라 실패·변조·만료·기준선 변경 Fixture를 포함한다.
5. README와 요구사항 추적표의 구현 상태를 실행 결과와 함께 갱신한다.
6. 표의 모든 항목은 적용 대상이며 우선순위는 제외 근거가 아니다.
7. 항목 제외는 제품 책임자와 보안·법무 승인 및 범위변경 Receipt 없이는 허용하지 않는다.
8. 구현·계약·권한·실패시험·사용자 흐름·운영 관찰 중 하나라도 없으면 `DONE`으로 표시하지 않는다.

## 특허 문서 환류

구현 과정에서 입력 계약, 상태전이 또는 기술적 효과가 변경되면 `/workspace/Patent/03_ONSURE`의 해당 명세서와 청구항 지원표에 변경 후보를 기록한다. 제품 구현이 특허 문구에 맞춰 왜곡되지 않도록 하며, 발명의 핵심 관계가 달라지면 변리사에게 신규사항·분할출원 영향을 검토 요청한다.
