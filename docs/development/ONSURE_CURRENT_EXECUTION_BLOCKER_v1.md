# ONSURE 현재 실행 차단 사유 v1

## 현재 세션 환경

```text
저장소 접근          GitHub Connector
코드 수정            가능
PR·브랜치 관리       가능
로컬 셸 실행         불가
JDK 17 실행          NOT_RUN
Maven 실행           NOT_RUN
제품 종단간 시험     NOT_RUN
범용 하네스           NOT_RUN
개발 관문             HOLD
```

## 차단 사유

현재 채팅 세션은 GitHub 파일·브랜치·PR을 읽고 수정할 수 있지만, 사용자의 Codespace 또는 VS Code 터미널에서 직접 명령을 실행할 수 없다.

따라서 다음을 실제로 증명할 수 없다.

- Java 17 컴파일
- Maven 의존성 해석
- JUnit 전체 실행
- 실제 프로세스 하네스
- 두 독립 실행의 환경·결과 동일성
- 실행 증적 파일 생성
- 개발 관문 성공 표식

## 허용되는 작업

- 설계·코드·계약·시험 코드 검토
- GitHub 정적 비교
- 결함 수정과 테스트 코드 작성
- 실행 전 점검 스크립트 작성
- 통합 실행기 작성
- Draft PR 생성
- 실행 상태를 `NOT_RUN/HOLD`로 정확히 기록

## 허용되지 않는 주장

- 실제 컴파일 통과
- 실제 JUnit 통과
- 종단간 실행 통과
- 독립 실행 2회 통과
- 개발 완료
- 최종 후보
- 최종 잠금

## 실행 재개 명령

VS Code 또는 Codespace에서 다음을 실행한다.

```bash
git checkout main
git pull --ff-only origin main

bash scripts/preflight-local-assurance.sh
bash scripts/preflight-universal-harness.sh
mvn -B -ntp test
bash scripts/run-product-platform-e2e.sh
bash scripts/run-universal-harness-twice.sh \
  operator-independent-1 operator-independent-2 local-jdk17
bash scripts/run-onsure-development-gate.sh
```

## 차단 해제 조건

```text
JDK17 확인
Maven 확인
추적 파일 변경 없음
Maven/JUnit PASS
제품 종단간 시험 2회 PASS
범용 하네스 독립 2회 PASS
ONSURE 자체 보증 PASS
개발 관문 PASS
증적 SHA-256 재검증 PASS
```

실행 중 실패가 발생하면 완료로 처리하지 않고, 증적 보존 → 근본원인분석 → 수정 → 집중 재시험 → 전체 회귀검증 순서로 진행한다.
