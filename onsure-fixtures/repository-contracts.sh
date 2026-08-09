#!/bin/bash
set -e
python3 scripts/validate-repository-contracts.py --output /tmp/onsure-self-check-repository-contracts.json
