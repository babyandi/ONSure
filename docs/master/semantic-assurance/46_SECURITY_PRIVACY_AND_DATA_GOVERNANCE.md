# ONSure Security·Privacy·Data Governance 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
ONSure가 고객 Source, Evidence, Hidden Corpus, AI Prompt/RAG/Memory, Certificate, Audit를 동시에 다루는 과정에서 **검증 편의를 이유로 데이터 경계가 무너지지 않도록** 데이터 분류·접근·보존·반출·학습사용 규칙을 정의한다.

## 2. Data Class
- PUBLIC
- CUSTOMER_INTERNAL
- CONFIDENTIAL
- PERSONAL_DATA
- SECRET
- HIDDEN_QUALIFICATION
- CRYPTO_KEY_MATERIAL
- LEGAL_HOLD

복수 class 가능하며 가장 강한 restriction을 적용한다.

## 3. Data Subject/Ownership
모든 저장 object는 최소:
- organization_id/tenant_id
- project/target/case scope
- data_owner
- data_class[]
- purpose
- retention policy
- origin
- sharing eligibility
을 가진다.

## 4. Purpose Binding
Source/Evidence를 다음 목적 외에 자동 재사용 금지:
- PRODUCT_VALIDATION
- IMPROVEMENT
- CUSTOMER_SUPPORT
- QUALIFICATION
- SHARED_CORPUS_CONTRIBUTION
- LEGAL_AUDIT

원 목적보다 넓은 목적은 새 authority/consent가 필요하다.

## 5. Shared Corpus
기본 Opt-out. 공유 가능하려면:
- Organization opt-in
- raw source/evidence identifier 제거
- deidentification validation
- legal/license eligibility
- semantic leakage 검토
- promotion receipt

Hidden qualification corpus와 customer-derived shared corpus를 같은 pool로 취급하지 않는다.

## 6. Hidden Qualification Data
- learner/implementation agent 접근 금지
- 최소권한 verifier만 접근
- access receipt
- result feedback 제한
- rotation/leakage detection

Hidden 결과 반복 노출로 target/validator를 튜닝하면 corpus generation을 COMPROMISED로 처리한다.

## 7. Encryption/Key Boundary
- data-at-rest encryption
- tenant/customer-managed key option
- signing key와 data-encryption key 분리
- key rotation/revocation generation
- backup key lifecycle

키 접근권한과 assurance approval 권한을 동일 principal에 몰아주지 않는 정책을 지원한다.

## 8. Data Minimization
SaaS Control Plane은 Local/Hybrid mode에서 원 Source 대신 필요한 metadata/digest/evidence summary만 수신할 수 있어야 한다. Raw source 업로드가 필요 없는 operation은 source를 서버로 전송하지 않는다.

## 9. AI Provider Boundary
외부 AI provider 사용 시:
- provider/profile allowlist
- 전송 data class 제한
- training/retention policy 확인
- prompt/source redaction
- tenant policy
- provider request receipt
을 요구한다.

Secret/HIDDEN/금지 class는 policy가 명시적으로 허용하지 않으면 외부 provider로 전송 금지.

## 10. Retention/Deletion
Retention은 object type + data class + contract/legal basis로 결정한다.
삭제:
- logical deletion만으로 완료 주장 금지
- object replicas/index/cache/backups 처리정책 명시
- DeletionReceipt
- Legal Hold 우선

Cryptographic evidence 역사 보존과 개인정보 삭제권이 충돌할 경우 raw 개인식별 정보와 최소 무결성 metadata를 분리하는 설계를 사용한다.

## 11. Export/Download
Evidence Pack/Report/Certificate export는:
- export scope preview
- data class filter
- secret/PII scan
- recipient/purpose
- expiry/watermark 필요 시
- export receipt
을 가진다.

## 12. Cross-tenant Isolation
- object ownership server-side verification
- storage namespace isolation
- cache key tenant binding
- search/index tenant filter를 query parameter 하나에만 의존하지 않음
- background worker work-unit tenant binding
- signed public certificate namespace와 tenant-private evidence namespace 분리

## 13. Audit Privacy
Audit는 필요한 principal/action을 남기되 secret/raw source를 로그에 복제하지 않는다. 민감 필드는 digest/reference를 사용한다.

## 14. Negative Test
- Tenant A evidence ID로 Tenant B 조회
- cache key tenant omission
- hidden corpus를 learner가 조회
- Opt-out 고객 pattern이 shared corpus에 승격
- external AI provider에 secret prompt 전송
- deletion 후 search index에서 원문 조회
- export pack에 다른 project evidence 혼입
- audit log에 API token 기록

## 15. 수용기준
- 데이터 목적/소유/등급이 없는 object는 authoritative evidence가 될 수 없다.
- hidden corpus/customer data boundary가 machine-enforced된다.
- 삭제/반출/외부 provider 사용이 receipt로 감사 가능하다.
- tenant isolation은 UI filter가 아니라 service/storage/worker 전체 경계에서 유지된다.
