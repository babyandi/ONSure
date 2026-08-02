# ONSure deployment boundary

상태: `DESIGN_ONLY_NONFINAL`

이 디렉터리는 실행 가능한 배포 정의가 아니다. Dockerfile, Compose, Helm, Kubernetes manifest와 배포 credential을 포함하지 않는다. `deployment-plan.v1.json`은 non-root/read-only/loopback/외부 secret 조건을 기계 검증하는 preflight 골격이며 install·rollback 명령은 `NOT_AUTHORIZED`로 고정한다.

배포 topology, container image, orchestrator, runtime identity와 secret provider가 승인되기 전에는 실제 배포 파일을 추가하지 않는다. 현재 권위 계약은 `contracts/onsure-operational-boundary.v1.json`이며 검증 명령은 다음과 같다.

```bash
python3 scripts/validate_onsure_operational_boundary.py
python3 scripts/onsure_deploy_migration_skeleton.py preflight
```

배포, Production GO, Commercial GO와 Final PASS는 허용되지 않는다.
