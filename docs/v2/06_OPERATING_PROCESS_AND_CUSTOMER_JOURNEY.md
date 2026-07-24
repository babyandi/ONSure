# ONSure 운영 프로세스와 고객 여정

## 1. 웹 고객 여정

```text
회원가입·조직생성
→ 서비스 선택
→ 대상 연결/업로드
→ Preflight
→ 범위·견적·납기 확인
→ 결제
→ License 발급
→ Intake·Baseline Lock
→ 학습/검증
→ 중간확인
→ 결과 납품
→ 개선 선택
→ 개선·재검증
→ Case 종료
```

## 2. Learn 운영

- 자료 접수와 권한 검증
- System·Program 경계 확인
- Learning Unit 산정과 한도 확인
- Program Profile 초안
- 충돌·불확실성 표시
- 내부 품질검토
- 최종 Profile·구성도·권장 검증범위 납품

## 3. Verify 운영

- 검증기준 충분성 판정
- 실행환경·Fixture·Credential 준비
- 검증계획과 제외범위 승인
- 정상·경계·실패·적대 시험
- Finding 재현·중복제거·Severity 판정
- RCA·잔여위험·최종보고서

## 4. Learn & Verify 운영

학습 완료 후 고객 확인 Gate를 둔다. 고객이 Program Profile과 대상 경계를 확인한 뒤 검증기준을 확정한다. 큰 범위 변경은 변경견적 또는 신규 Case다.

## 5. Improve & Re-verify 운영

- 유효 Finding 선택
- 개선 난이도·영향·위험 산정
- 견적·결제·Entitlement 갱신
- 전용 Branch/Worktree
- Patch·승인
- 단위·영향·전체 회귀검증
- Before/After 비교
- Draft PR 또는 Patch Bundle 납품

## 6. VS Code 고객 여정

```text
구독·로그인
→ Seat 활성화
→ Workspace 연결
→ System·Program Binding
→ 최초 학습
→ 변경별 증분학습
→ 검증·개선·Git
→ Usage·Credit 확인
→ 팀 승인·CI
→ 갱신·확장
```

## 7. 운영 역할

- Customer Owner: 계약·범위·결제 승인
- Customer Developer: 자료·환경·변경 확인
- ONSure Automation: 학습·검증·개선·Evidence
- ONSure Reviewer: 고위험 Finding·Patch 품질검토
- Support/Operations: Case·장애·납기 관리
- OLicense Admin: Catalog·Contract·License·Refund

## 8. SLA·우선순위

자동서비스와 전문가 지원을 분리한다.

- Platform SLA: 접수·실행·결과 접근 가용성
- Case Delivery: 합의한 영업일 기준 납기
- Support Response: Support Level별 최초 응답시간
- Expert Review: 구매한 전문가 시간·건수 기준

## 9. 고객 대기와 일시정지

자료·권한·승인 대기는 Case Timer를 일시정지할 수 있다. ONSure 내부 장애나 작업대기는 납기정지 사유가 아니다. 모든 정지·재개는 타임스탬프와 사유를 기록한다.

## 10. 품질 Gate

- 대상·권한 확인
- 기준선 Lock
- 검증계획 승인
- Evidence 완전성
- Finding 재현성
- 수정 영향·Rollback
- 재검증과 잔여위험
- License·Usage 정합성
- 최종 산출물 검수

## 11. 분쟁 방지

견적과 계약화면에서 System, Program, 포함자료, Baseline, Learning Unit, Verification Pack, Improvement Unit, 재검증, 기간, 제외범위, 고객 의무, 데이터 삭제일을 명시한다.
