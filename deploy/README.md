# ONSure deployment boundary

상태: `RHEL_AND_UBUNTU_SYSTEMD_CANDIDATES_NONFINAL`

운영 후보는 컨테이너가 아닌 RHEL 계열 또는 Ubuntu 24.04 LTS 단독 서버다. 기존 경로 호환성을
위해 `deploy/rhel/`의 배포판 중립 systemd 서비스·환경파일·writable 경로·PostgreSQL migration
gate를 두 후보가 공유하고, `deploy/ubuntu/`에는 Ubuntu 전용 계획과 AppArmor/UFW 검토 경계를
정의했다. Local API와 PostgreSQL은 loopback에만 bind하며 공개 listener는 승인하지 않는다.

기존 `Dockerfile.candidate`와 `compose.candidate.yaml`은 이전 합성 보안 시험 자료로만 보존한다.
선택된 배포 경로가 아니며 build/run gate도 아니다.

```bash
bash scripts/package_onsure_rhel.sh
bash scripts/package_onsure_ubuntu.sh
python3 scripts/validate_onsure_rhel_package.py
python3 scripts/validate_onsure_ubuntu_package.py
python3 scripts/validate_onsure_operational_boundary.py
python3 scripts/onsure_deploy_migration_skeleton.py preflight
```

패키징은 로컬 artifact만 만든다. 서버 install, PostgreSQL 변경, SELinux/AppArmor·firewall 변경,
service enable/start, OpenAI 실호출, rollback, Production GO, Commercial GO와 Final PASS는
`NOT_RUN / NOT_AUTHORIZED`다.
