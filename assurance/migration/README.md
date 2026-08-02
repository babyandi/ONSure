# ONSure migration assurance candidates

이 디렉터리는 실제 이관 증적이 아니라 `products/onsure/` 이관 준비용 비최종 후보를 보관한다.

- `onsure-migration-manifest.v1.json`: 재현 가능한 파일 inventory 후보
- `onsure-open-pr-overlap.v1.json`: 열린 PR head별 충돌·재검증·병합 순서 HOLD
- 생성: `python3 scripts/onsure_monorepo_manifest.py`
- 검증: `python3 scripts/validate_monorepo_migration_readiness.py`

Manifest PASS는 라이선스, 고객 데이터 owner 확인, 실제 cutover 또는 Final PASS를 의미하지 않는다.
