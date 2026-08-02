# ONSure deployment boundary

상태: `DESIGN_ONLY_NONFINAL`

`Dockerfile.candidate`와 `compose.candidate.yaml`은 배포 후보의 보안 조건을 실행 검증하기 위한 비최종 정의다. image build, UID/GID 65532, read-only root, no capabilities, no-new-privileges, network none, loopback ready message를 합성 환경에서 확인했지만 install·rollback은 계속 `NOT_AUTHORIZED`다.

배포 topology, registry, orchestrator와 secret provider가 승인되기 전에는 이 후보를 실제 배포 정의로 승격하지 않는다. 현재 권위 계약은 `contracts/onsure-operational-boundary.v1.json`이며 검증 명령은 다음과 같다.

```bash
python3 scripts/validate_onsure_operational_boundary.py
python3 scripts/onsure_deploy_migration_skeleton.py preflight
python3 scripts/validate_onsure_container_candidate.py
```

배포, Production GO, Commercial GO와 Final PASS는 허용되지 않는다.
