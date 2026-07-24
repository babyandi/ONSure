# ONSURE 최종 실행 환경 v1

## 목적

Issue #4 종료와 개발 PR 준비 판단에 필요한 JDK 17·Maven 로컬 실행 환경과 최종 실행 명령을 고정한다.

## 필수 도구

```text
Git
Bash
JDK 17
javac 17
Maven
sha256sum
cmp
Python 3
```

실행 전 모든 도구의 경로와 버전을 기록한다.

## 작업공간 조건

- 승인된 ONSURE 저장소
- 실행 대상 커밋 SHA 고정
- 추적 파일 변경 없음
- 필수 계약·시험 데이터·오라클 존재
- 저장소 내부 임시 비밀정보 없음
- 이전 실행 산출물과 새 실행 산출물 분리

확인 명령:

```bash
git status --short
git rev-parse HEAD
java -version
javac -version
mvn -version
python3 --version
```

## 권장 실행 환경

### Codespace 또는 개발 컨테이너

```bash
bash scripts/prepare-assurance-environment.sh
```

### VS Code 로컬 환경

저장소 루트에서 JDK 17과 Maven이 같은 Java 실행 환경을 사용하도록 설정한다.

```bash
export JAVA_HOME=<JDK17_PATH>
export PATH="$JAVA_HOME/bin:$PATH"
```

Windows PowerShell에서는 대응하는 환경 변수 설정을 사용한다.

## 실행 전 점검

```bash
bash scripts/preflight-local-assurance.sh
bash scripts/preflight-universal-harness.sh
```

점검 항목:

- JDK·Maven·Git·Bash 사용 가능
- 추적 파일 변경 없음
- 제품 범위·대상 등록소·상태 모델 계약
- 시험 데이터·오라클·검증 축
- 영수증·소스 잠금·보안 발견사항 계약
- 실행 명령 허용 목록

## 정식 실행 순서

```bash
mvn -B -ntp test
bash scripts/run-product-platform-e2e.sh
bash scripts/run-universal-harness-twice.sh \
  operator-independent-1 operator-independent-2 local-jdk17
bash scripts/run-local-assurance-twice.sh
bash scripts/execute-issue-4-final-gate.sh
bash scripts/run-onsure-development-gate.sh
```

## 성공 표식

```text
ONSURE_PRODUCT_PLATFORM_E2E_PASS
ONSURE_UNIVERSAL_TWO_RUN_PASS
LOCAL_ASSURANCE_TWICE_PASS
ISSUE4_FINAL_GATE_EVIDENCE_READY
ONSURE_DEVELOPMENT_GATE_PASS
```

표식만으로 통과하지 않는다. 종료 코드, 영수증, 증적 SHA-256, 읽기 전용 재검증 결과가 필요하다.

## 실패 처리

```text
실패 명령·출력·종료 코드 보존
→ 근본원인분석
→ 수정
→ 집중 재시험
→ 전체 회귀검증 2회
→ 독립 검증·감사
→ 전체 관문 재실행
```

환경 누락은 결함 통과가 아니라 `BLOCKED` 또는 `NOT_RUN`이다.

## 결과 위치

실행별 증적은 고유 실행 디렉터리에 저장한다.

```text
receipts/local/<run-directory>/
```

보존 대상:

- 실행 문맥
- 소스 잠금
- 시험·회귀검증 출력
- 독립 검증·감사 영수증
- 증적 SHA-256 목록
- 최종 영수증
- 실패 근본원인분석

## 현재 상태

```text
실행 환경 문서     완료
JDK17 확인         NOT_RUN
Maven 확인         NOT_RUN
공식 실행          NOT_RUN
개발 관문          HOLD
```
