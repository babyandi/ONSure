# ONSure End-to-End Design Traceability Matrix

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Scope: FR-META-001~060 + semantic-assurance 00~52

## 1. 목적
각 설계축이 Requirement만 있고 Architecture/Test가 없거나, Contract만 있고 UX/Operation이 없는 고립상태가 되지 않도록 수직 Trace를 고정한다.

## 2. Trace Column
모든 Material Design Capability는 최소 다음 열을 가진다.
`Requirement → Review → Architecture/Data/API → UX → Test/Operation → AI/Method(if applicable) → Governance/Open Decision → Contract → Runtime Operation → Evidence → Independent Verification`

## 3. 주요 Capability Trace
| Capability | Requirement | Review | Architecture | UX | Test | Deep Design | Contract 상태 |
|---|---|---|---|---|---|---|---|
| Target/Scope/Requirement Identity | FR-META-001~002 | 03 Truth/Scope | 04 §14 | 05 Assurance Card | 06 §13 | 26 | candidate 일부 |
| Evidence Binding/Freshness | 006~007 | Evidence sufficiency | 04 §14 | stale/unbound UX | meta fixture | 27,40,43 | candidate 일부 |
| Independence/Authority | 008,027 | 03 Human/Independence | 04 Trust registry | 05 authority display | adversarial | 21,24,31 | candidate 일부 |
| Final Reconstruction | 010~013 | Final claim review | 04 Final Reconstructor | 05 Final Snapshot | 06 cross-run/freshness | 17~19 | candidate 일부 |
| Deployment/Runtime Currentness | 024,044~047 | Runtime Review | 04 §15 | 05 §11 | 33 | 22,28,29,39 | next batch |
| Product Composition | 009,048~050 | Composition Review | 04 §15 | composition card | 33 | 30,38 | next batch |
| Evidence Graph | 026,051 | Evidence graph review | 04 §15 | explanation graph | 33 | 30,40,43 | next batch |
| Certificate/Public Verification | 042,052~053 | Certificate review | 04 §15 | Certificate Center | 33 | 31,41 | next batch |
| Offline/Enterprise Authority | 054~055 | Governance review | 04 §15 | offline/authority UX | 33 | 31,42,46 | next batch |
| Distributed Work/Scale | 040,056 | operation integrity | 04 §15 | operational status | 33 | 32,43,44,48 | next batch |
| Plugin/Adapter Trust | 057 | supply-chain review | 04 §15 | qualification state | 33 | 32,49,52 | next batch |
| AI Runtime Assurance | 019,031~035,058~059 | AI/runtime review | 04/07 | AI currentness | 33/34 | 32,34,39,46 | next batch |
| ONSure Meta-Assurance | 029~030,060 | Meta review | Qualification services | release qualification UX | validator mutation | 25,32,34,47 | candidate/next |
| Persistence/Recovery | 022 | evidence transaction review | 43,48 | recovery limitation | crash/DR | 43,48,51 | next batch |
| API/Operation Semantics | 041 | contract review | 44,45 | async/error state | retry/idempotency | 44,45 | next batch |
| Security/Data Governance | 015,027,038 | Security Review | 46,49 | privacy/export UX | tenant/hidden tests | 46,49 | next batch |
| Version/External Trust | 025,036 | supply-chain/semantic review | 50,52 | compatibility warning | mixed-version/provider | 50,52 | next batch |

## 4. Trace Gap 정의
- R-GAP: Requirement 없음
- V-GAP: Review rule 없음
- A-GAP: Architecture/Entity/API 없음
- U-GAP: UX 의미표현 없음
- T-GAP: Negative/Failure test 없음
- G-GAP: Governance/Open Decision 없음
- C-GAP: Machine Contract 없음
- O-GAP: Runtime Operation 없음
- E-GAP: Evidence/Receipt 없음
- I-GAP: Independent verification 없음

현재 29~52의 주된 gap은 C/O/E/I이며, 큰 R/V/A/T 구조공백은 대부분 설계되어 있다.

## 5. 완료 규칙
Capability를 `DESIGN_CLOSED_CANDIDATE`로 부르려면 R/V/A/U/T/G가 모두 존재하고 미확정 정책은 configurable policy 또는 Open Decision으로 명시되어야 한다.
`IMPLEMENTATION_READY`는 추가로 C/O/E가 필요하다.
`ASSURANCE_READY`는 I와 Qualification까지 요구한다.

## 6. 금지
- 문서가 있다는 이유로 Contract/Runtime 존재 주장
- 테스트 계획이 있다는 이유로 실행 PASS 주장
- 같은 파일 내 언급만으로 Trace closure 처리
- P0 C/O/E/I gap이 있는 Capability를 Final Gate 권위로 사용
