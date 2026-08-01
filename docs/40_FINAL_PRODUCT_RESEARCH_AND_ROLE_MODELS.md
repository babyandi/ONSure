# ONSure 최종 제품 외부조사·롤모델 기준서

- 상태: FINAL-TARGET NORMATIVE INPUT / 구현 완료 주장 아님
- 적용 대상: ONSure 자체 및 금융회사가 개발·구매·위탁한 모든 AI 제품
- 조사 기준일: 2026-07-28
- 원칙: 출처의 존재를 곧 적합성으로 간주하지 않고, 원자 요구사항·시험·증적으로 변환한다.

## 1. 조사 목적과 방법

ONSure의 최종형을 MVP 확대판이 아니라 금융권 AI Assurance Platform으로 재정의하기 위해 규제·표준·위협·상용제품·개발자 도구를 교차 조사했다. 특정 제품을 복제하지 않고 각 계층의 장점을 조합한다.

출처는 49개 URL로 고정한다. 같은 기관이라도 서로 다른 통제 목적의 독립 문서는 별도 출처로 인정한다. 검색 요약만이 아니라 공식 원문 페이지를 우선한다. 변경될 수 있는 규정은 버전·시행일·수집시각·원문 Hash를 Evidence로 보존해야 한다.

## 2. 롤모델 결론

| 층 | 롤모델 | 채택할 점 | 그대로 채택하지 않을 점 |
|---|---|---|---|
| 금융 모델위험 | Fed/OCC MRM, PRA SS1/23 | Inventory, Materiality, 독립 검증, 지속 모니터링, 이사회 책임 | 전통 통계모델만의 검증 범위 |
| AI 거버넌스 | NIST AI RMF, ISO 42001, IBM watsonx.governance | 수명주기, 위험등급, 통제 매핑, 다중 공급자 | 문서 체크리스트만으로 PASS |
| AI 보안 | MITRE ATLAS, OWASP GenAI/Agentic, HiddenLayer, Lakera/F5 | 위협지식, Red Team, 모델·앱·Agent 공격 | 런타임 차단만으로 설계·코드 적합성 대체 |
| 앱·공급망 | NIST SSDF, OWASP ASVS, KISA 공급망 | Secure SDLC, SBOM, Provenance, 취약점 검증 | SAST/SCA 성공만으로 AI 안전성 인정 |
| 관측·평가 | Fiddler, Arize류, Azure/AWS/IBM | Drift, 품질, 설명, 운영 관측 | 벤더 자체 지표를 독립 증적으로 인정 |
| 개발자 작업면 | Claude Code, VS Code Copilot Agent, GitHub | 저장소 이해, 계획, 수정, 명령, 반복, Git 전달 | Agent의 무승인 Merge·배포·신뢰근 선택 |

ONSure의 고유 결합점은 다음이다.

> 외부 AI 제품의 요구사항·설계·구현·연결·시험·운영을 금융권 통제에 매핑하고, Positive/Negative/Adversarial/Resilience 시험과 개선·재검증을 동일 Source/Model/Data/Policy 계보에 결속하며, 독립 검증과 감사 Evidence Pack까지 제공한다.

## 3. 조사 출처 49개

### A. 국내 금융·법·보안

1. 금융위원회 금융분야 망분리 개선 로드맵 — https://www.fsc.go.kr/no010101/82885
2. 금융위원회 금융권 AI 활용 지원 — https://www.fsc.go.kr/no010101/83594
3. 금융위원회 금융권 생성형 AI 혁신서비스 — https://www.fsc.go.kr/po010101/83554
4. 금융위원회 내부업무망 SaaS 제도화 — https://www.fsc.go.kr/no010101/86745
5. 금융위원회 고성능 AI 금융권 보안위협 대응 — https://www.fsc.go.kr/no010101/86972
6. 금융위원회 프런티어 AI 보안위협 금융분야 대응 — https://www.fsc.go.kr/no010101/87240
7. 금융보안원 내부업무망 SaaS 보안 해설서 — https://www.fsec.or.kr/bbs/detail?bbsNo=11929&menuNo=222
8. 금융보안원 가이드 자료실 — https://www.fsec.or.kr/bbs/222
9. 국가법령정보센터 전자금융감독규정 — https://www.law.go.kr/LSW/admRulInfoP.do?admRulSeq=2000000084730
10. KISA SW 공급망 보안 가이드라인 1.0 — https://www.kisa.or.kr/2060204/form?page=1&postSeq=15
11. KISA 제로트러스트·공급망 가이드 목록 — https://www.kisa.or.kr/2060204?page=1
12. KISA 소프트웨어 개발 보안 가이드 — https://www.kisa.or.kr/2060204/form?lang_type=KO&page=1&postSeq=5
13. KISA 소프트웨어 보안약점 진단가이드 — https://www.kisa.or.kr/2060204/form?page=1&postSeq=9
14. KISA SW 공급망 보안 강화 로드맵 — https://www.kisa.or.kr/2060204/form?page=1&postSeq=24

### B. 국제 AI·보안·품질 표준

15. NIST AI RMF — https://www.nist.gov/itl/ai-risk-management-framework
16. NIST GenAI Profile AI 600-1 — https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.600-1.pdf
17. NIST SSDF SP 800-218 — https://csrc.nist.gov/pubs/sp/800/218/final
18. NIST AI SSDF SP 800-218A — https://csrc.nist.gov/pubs/sp/800/218/a/final
19. NIST SP 800-53 — https://csrc.nist.gov/pubs/sp/800/53/r5/upd1/final
20. NIST Zero Trust SP 800-207 — https://csrc.nist.gov/pubs/sp/800/207/final
21. NIST Cyber AI Profile IR 8596 — https://nvlpubs.nist.gov/nistpubs/ir/2025/NIST.IR.8596.iprd.pdf
22. ISO/IEC 42001 — https://www.iso.org/standard/81230.html
23. ISO/IEC 23894 — https://www.iso.org/standard/77304.html
24. ISO/IEC 27001 — https://www.iso.org/standard/27001
25. ISO/IEC 27017 — https://www.iso.org/standard/43757.html
26. ISO/IEC 27018 — https://www.iso.org/standard/76559.html
27. ISO 22301 — https://www.iso.org/standard/75106.html
28. OWASP GenAI/LLM Top 10 — https://owasp.org/www-project-top-10-for-large-language-model-applications/
29. OWASP Agentic Applications Top 10 — https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/
30. OWASP AI Testing Guide — https://owasp.org/www-project-ai-testing-guide/
31. OWASP ASVS — https://owasp.org/www-project-application-security-verification-standard/
32. OWASP Agentic Skills Top 10 — https://owasp.org/www-project-agentic-skills-top-10/
33. MITRE ATLAS — https://atlas.mitre.org/
34. MITRE SAFE-AI — https://atlas.mitre.org/pdf-files/SAFEAI_Full_Report.pdf
35. MITRE AI Incident Sharing — https://ai-incidents.mitre.org/

### C. 금융감독·모델위험·복원력

36. Federal Reserve 2026 Revised MRM Guidance — https://www.federalreserve.gov/supervisionreg/srletters/SR2602.pdf
37. Federal Reserve SR 11-7 — https://www.federalreserve.gov/boarddocs/srletters/2011/sr1107.pdf
38. OCC Bulletin 2026-13 — https://www.occ.treas.gov/news-issuances/bulletins/2026/bulletin-2026-13.html
39. OCC Corporate and Risk Governance Handbook — https://www.occ.treas.gov/publications-and-resources/publications/comptrollers-handbook/files/corporate-risk-governance/pub-ch-corporate-risk.pdf
40. PRA SS1/23 Model Risk Management — https://www.bankofengland.co.uk/prudential-regulation/publication/2023/may/model-risk-management-principles-for-banks-ss
41. Bank of England AI/ML Feedback Statement — https://www.bankofengland.co.uk/prudential-regulation/publication/2023/october/artificial-intelligence-and-machine-learning
42. EBA ICT and Security Risk Management — https://www.eba.europa.eu/activities/single-rulebook/regulatory-activities/internal-governance/guidelines-ict-and-security-risk-management
43. EBA Operational Risks and Resilience/DORA — https://www.eba.europa.eu/publications-and-media/publications/operational-risks-and-resilience

### D. 제품·IDE 롤모델

44. IBM watsonx.governance — https://www.ibm.com/products/watsonx-governance/model-governance
45. HiddenLayer Total AI Security — https://www.hiddenlayer.com/
46. Lakera AI Security — https://www.lakera.ai/
47. Fiddler AI Control Plane — https://www.fiddler.ai/
48. F5 AI Guardrails/Red Team — https://www.f5.com/go/solution/f5-ai-security-with-guardrails
49. VS Code Copilot Agent Mode — https://code.visualstudio.com/blogs/2025/02/24/introducing-copilot-agent-mode

## 4. 출처를 요구사항으로 변환하는 규칙

각 출처 항목은 다음 레코드로 수집한다.

```text
Source ID / Publisher / Title / Version / Effective date / URL / Retrieval time
→ Normative or informative
→ Applicable product types and jurisdictions
→ Atomic control statements
→ Threats and failure modes
→ Positive/Negative/Adversarial/Resilience cases
→ Required evidence and retention
→ Conflicts/exceptions
→ Human legal interpretation required
```

외부 링크가 사라져도 검증기준이 바뀌지 않도록 원문 Hash와 라이선스상 허용되는 메타데이터를 Evidence Vault에 보존한다. 법적 해석은 ONSure가 확정하지 않고 준법·법무 승인 대상으로 표시한다.

## 5. 연구에서 도출된 P0 차별 요구

1. 타사 블랙박스·그레이박스·화이트박스 제품을 모두 검증한다.
2. 설계 문서 존재를 구현·연결·시험 완료로 오인하지 않는다.
3. AI 모델만이 아니라 데이터, RAG, Prompt, Agent, Tool, API, IaC, 배포, 운영자를 동일 계보에서 본다.
4. 금융 업무 중요도와 모델 Materiality에 따라 검증 깊이·독립성·재시험 주기를 달리한다.
5. 벤더가 제공한 시험 결과와 ONSure 독립 Oracle을 분리한다.
6. 검사 제출용 Control-to-Evidence Pack을 자동 생성하되 법적 적합 판정은 권한자 승인을 요구한다.
7. VS Code 자동개선은 Finding 범위·승인 범위·Worktree 안에서만 허용한다.
8. Merge, Production/Commercial GO, FinalLock은 AI 단독으로 실행하지 않는다.

## 6. 조사 완료 기준

49개 출처를 나열한 것만으로 조사가 끝나지 않는다. 각 통제는 `SOURCE→CONTROL→REQUIREMENT→DESIGN→IMPLEMENTATION→CALLPATH→CASE→RECEIPT→INDEPENDENT REVIEW` 추적률 100%가 되어야 한다. 미매핑·충돌·시행일 미확인은 `UNKNOWN/HOLD`다.
