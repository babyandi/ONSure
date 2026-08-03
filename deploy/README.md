# ONSure deployment boundary

상태: `RHEL_SYSTEMD_CANDIDATE_NONFINAL`

운영 후보는 컨테이너가 아닌 RHEL 계열 단독 서버다. `deploy/rhel/`에 non-root systemd
서비스, 외부 환경파일, 전용 writable 경로와 PostgreSQL migration gate를 정의했다. Local API는
`127.0.0.1`에만 bind하며 공개 network listener는 승인하지 않는다.

기존 `Dockerfile.candidate`와 `compose.candidate.yaml`은 이전 합성 보안 시험 자료로만 보존한다.
선택된 배포 경로가 아니며 build/run gate도 아니다.

```bash
bash scripts/package_onsure_rhel.sh
python3 scripts/validate_onsure_operational_boundary.py
python3 scripts/onsure_deploy_migration_skeleton.py preflight
```

패키징은 로컬 artifact만 만든다. 서버 install, PostgreSQL 변경, service enable/start, OpenAI 실호출,
rollback, Production GO, Commercial GO와 Final PASS는 `NOT_RUN / NOT_AUTHORIZED`다.
