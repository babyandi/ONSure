# ONSURE Post-Merge Self-Audit Changeset v1

## 목적

PR #19 병합 후 자기검증에서 발견한 과장·누락·경계 오류를 정정한다. 이 변경은 제품 구현 완료나 Runtime PASS가 아니라 P0를 명확히 드러내고 거짓 승격을 차단하는 비최종 교정이다.

## 교정한 내용

- Core와 ORUDA의 Runtime 선택 분리와 Compile-time Module 분리를 구분
- 구현 상태와 검증 상태를 별도 차원으로 분리
- 20개 기능군 Traceability가 원자 요구사항 전수 추적이 아님을 명시
- 현재 Main Runtime Evidence를 `NOT_RUN`으로 복구
- One-Shot Static은 `NON_FINAL`, Runtime은 P0가 열려 있으면 `BLOCKED_NONFINAL`
- Dirty 판정에 Untracked 파일 포함
- 단계 Receipt에 Command, Source Commit, Environment Digest 결속
- Git 추적 JSON·JSONL·Markdown·Shell 중심의 결정적 정적 검사
- Local OTester/OAudit를 `INTERNAL_SELF_VALIDATION`으로 고정
- Local Final Receipt에 `SELF_VALIDATION_NONFINAL`, 독립 Gate `NOT_RUN`, Final/Production/Commercial 금지 강제
- 직접 Main Commit 위반 기록 및 이후 Branch·PR 절차 복원

## 열려 있는 P0

- Core/ORUDA Maven Module과 Artifact 물리 분리
- ORUDA Module 삭제 상태의 Core Clean Build·Test·Generic/AI E2E 2회
- 원자 Requirement·Acceptance·Code Symbol·Test Method·Evidence 100% 추적
- Program/Behavior Learning 실제 구현
- OPlanning·OReview·RCA 확정
- Patch·Worktree·Git Full-Chain
- VS Code Extension·Local Authenticated API

## 실행 상태

- GitHub Clone in current temporary reviewer environment: BLOCKED_DNS
- Available Java: 21, required Java: 17
- Maven: NOT_INSTALLED
- Current branch Maven/JUnit: NOT_RUN
- Current branch Python/Shell static run: NOT_RUN because repository archive could not be obtained in the temporary environment
- Independent OTester/OAudit: NOT_RUN

## 판정

`SELF_VALIDATION_NONFINAL / BLOCKED`

이 Changeset은 P0를 닫지 않는다. 상태와 Gate를 사실대로 교정하고 후속 구현의 기준선을 제공한다.
