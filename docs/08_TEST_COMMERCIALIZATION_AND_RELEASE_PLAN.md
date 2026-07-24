# ONSURE 시험·상용화·출시 계획

## 1. 목표

ONSURE는 문서와 데모 화면이 아니라 실제 저장소에서 학습·검증·보완·Git 전달이 재현될 때 출시할 수 있습니다.

## 2. 시험 체계

### 2.1 학습 시험

- 저장소 인벤토리 정확성
- 목적·기능·모듈 관계 추출 정확성
- 프롬프트·RAG·Tool 구성 식별
- 불확실성과 충돌 표시
- Commit 변경 후 증분 학습
- 잘못된 학습 후보의 활성 기준선 유입 차단

### 2.2 Agent 시험

- Ask·Plan·Act·Autopilot 권한 분리
- 장기 작업 중단·재개
- 도구 실패 후 RCA·재계획
- 무한 반복과 비용 초과 차단
- 사용자 승인 범위 밖 변경 차단

### 2.3 검증 시험

- 정상·경계·실패·적대 시나리오 생성
- 코드·프롬프트·RAG·Tool·모델 회귀검증
- 동일 입력 반복 시 변동성 측정
- 실패 재현 명령과 최초 실패 지점 확인
- `PASS`, `FAIL`, `HOLD`, `NOT_RUN` 구분

### 2.4 보완 시험

- Finding과 연결된 Patch만 생성
- Hunk 단위 승인·거부
- 잘못된 Patch 원상복구
- 수정 전후 동일 Fixture 비교
- 개선 효과 없음과 회귀 탐지

### 2.5 Git 시험

- Dirty Workspace 보호
- 새 Worktree·Branch 생성
- Commit·Push·Draft PR
- 원격 CI 결과 회수
- Force Push·무단 Reset 차단
- Merge·Rollback 정책 적용

### 2.6 VS Code 시험

- Activity Bar와 모든 View 연결
- 대화에서 실제 실행까지 연결
- Diff·승인·Run 상태 표시
- Extension 재시작 후 상태 복구
- Local Runtime 장애 후 재연결

### 2.7 보안 시험

- Prompt Injection과 Tool 권한 우회
- Secret 읽기·출력·로그 노출
- 저장소 간 Memory 오염
- 악성 파일·의존성·스크립트 실행
- Provider 데이터 전송 정책 위반
- Evidence 위·변조와 Source SHA 불일치

## 3. 필수 E2E 시나리오

### E2E-01 최초 학습

새 저장소를 등록하고 Program Profile을 생성한 뒤 사용자 검토까지 완료합니다.

### E2E-02 결함 발견과 자동 보완

숨겨진 결함을 재현하고 RCA·Patch·회귀검증·Draft PR을 완결합니다.

### E2E-03 AI 행동 회귀

프롬프트 또는 RAG 변경 전후의 정확도·정책 준수·변동성을 비교합니다.

### E2E-04 Dirty Workspace

사용자 변경을 보존한 채 별도 Worktree에서 작업을 완료합니다.

### E2E-05 장기 작업 복구

검증 도중 VS Code와 Runtime을 재시작한 뒤 동일 Run을 복구합니다.

### E2E-06 고위험 변경 차단

권한·데이터 삭제·정책 변경을 자동 적용 또는 Merge하지 못하게 합니다.

### E2E-07 Provider 교체

서로 다른 LLM Provider에서도 동일 계약과 Evidence 구조를 유지합니다.

### E2E-08 오프라인 실행

Enterprise Offline Mode에서 학습·검증·보완·Git 로컬 전달을 수행합니다.

## 4. 출시 관문

### Developer Preview

- Local Runtime 기본 동작
- CLI 학습·검증
- VS Code Chat과 Program Profile
- 수동 Patch와 로컬 Git Branch

### Private Beta

- 자동 보완과 Before/After
- Commit·Push·Draft PR
- 중단·재개
- 실제 고객 저장소 3종

### Public Beta

- 설치 프로그램과 자동 업데이트
- GitHub Provider 안정화
- 라이선스·Telemetry 선택
- 10개 이상 저장소 Full-Chain
- 치명·중대 보안 결함 0건

### General Availability

- Developer·Team Edition
- 지원 정책과 SLA
- Offline/On-prem 설치 경로
- 업그레이드·호환성·Rollback
- Blind Review 및 독립 보안 검토

## 5. 상용 Edition

### Developer

- 단일 사용자
- 로컬 Runtime
- VS Code·CLI
- 제한된 프로젝트·실행량
- GitHub Draft PR

### Team

- 팀 프로젝트
- 중앙 정책·Evidence·Memory
- 협업 승인과 PR 관리
- GitHub·GitLab

### Enterprise

- 온프레미스·폐쇄망
- Private Model Gateway
- SSO·RBAC·감사
- 다중 조직과 데이터 격리
- 산업별 검증팩

### Assessment

- 기간형 진단·보완·재검증 서비스
- 고객에게 보고서와 Patch·Evidence 인계

### Embedded/OEM

- 공개 API와 제품 계약을 이용한 타 제품 내장
- 향후 ORUDA 포함은 이 판매 형태의 하나일 뿐입니다.

## 6. 가격 원칙

정확한 가격은 시장 검증 후 확정하되 다음 축으로 구성합니다.

- 사용자 수
- 등록 프로젝트 수
- 월 실행량·모델 사용량
- 중앙 서버·온프레미스 여부
- 산업별 검증팩
- 지원·SLA 수준
- Embedded 배포 수량

모델 API 비용은 라이선스에 포함하거나 고객 Provider를 사용하는 두 방식을 지원합니다.

## 7. PoC 기준

PoC는 단순 오류 목록이 아니라 고객 저장소에서 다음을 입증해야 합니다.

```text
학습 시간 단축
+ 기존 테스트가 놓친 결함 발견
+ 자동 보완 1건 이상
+ 수정 전후 개선 증명
+ 회귀 차단
+ Draft PR과 Evidence 인계
```

## 8. KPI

- 최초 Program Profile 생성 시간
- 유효 Finding 정밀도
- 재현 가능한 Finding 비율
- Patch 채택률
- 자동 보완 성공률
- 회귀 발생률
- 사람 검토 시간 절감
- 동일 실패 재탐지 시간 단축
- Full-Chain 완료율
- 월간 활성 프로젝트와 갱신율

## 9. 출시 금지 조건

- ORUDA 또는 특정 외부 제품이 없으면 핵심 기능이 실행되지 않음
- 학습과 최종 판정이 분리되지 않음
- Dirty Workspace를 손상시킬 가능성 존재
- Source SHA와 Evidence가 결속되지 않음
- 검증되지 않은 Patch가 최종 또는 Merge Ready로 표시됨
- 치명·중대 보안 결함 잔존
- 중단·복구와 Rollback 미검증

## 10. 최종 출시 판정

```text
제품 기준선 고정
→ MVP Full-Chain 2회
→ 실제 외부 저장소 10종
→ 보안·적대 시험
→ 장애·복구 시험
→ 사용자 승인시험
→ 독립 Blind Review
→ 문서·설치·지원 준비
→ GA 승인
```

하나라도 미실행이면 출시 상태는 `HOLD`입니다.
