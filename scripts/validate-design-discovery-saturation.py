#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INTAKE = ROOT / "contracts/independent-design-discovery-wave-intake.candidate.v1.json"
RESULT_DIR = ROOT / ".onsure/design-discovery"


def main() -> int:
    intake = json.loads(INTAKE.read_text(encoding="utf-8"))
    waves = []
    for wid in ("INDEPENDENT-SATURATION-A", "INDEPENDENT-SATURATION-B"):
        p = RESULT_DIR / f"{wid}.json"
        if not p.exists():
            print(json.dumps({"gate":"DISCOVERY_SATURATION","status":"HOLD","missing_result":str(p.relative_to(ROOT))}))
            return 30
        waves.append(json.loads(p.read_text(encoding="utf-8")))
    reasons = []
    if any(w.get("wave_id") != expected for w, expected in zip(waves, ("INDEPENDENT-SATURATION-A", "INDEPENDENT-SATURATION-B"))): reasons.append("WAVE_ID_MISMATCH")
    if len({w.get("frozen_scope_digest") for w in waves}) != 1: reasons.append("SCOPE_DIGEST_CHANGED")
    if len({w.get("frozen_authority_digest") for w in waves}) != 1: reasons.append("AUTHORITY_DIGEST_CHANGED")
    if any(w.get("mandatory_lens_coverage_percent") != 100 for w in waves): reasons.append("MANDATORY_LENS_COVERAGE_NOT_100")
    if any(w.get("untriaged_candidate_count") != 0 for w in waves): reasons.append("UNTRIAGED_CANDIDATES")
    if any(w.get("new_p0_count") != 0 for w in waves): reasons.append("NEW_P0_DISCOVERED")
    if any(not w.get("independence_attested", False) for w in waves): reasons.append("INDEPENDENCE_NOT_ATTESTED")
    if any(w.get("consumed_prior_candidate_conclusions", True) for w in waves): reasons.append("BLINDNESS_VIOLATION")
    if any(not w.get("p1_novelty_within_policy_ceiling", False) for w in waves): reasons.append("P1_NOVELTY_CEILING_NOT_MET")
    receipt = {
        "contract":"ONSURE_DESIGN_DISCOVERY_SATURATION_RECEIPT_V1",
        "wave_ids":[w["wave_id"] for w in waves],
        "scope_digest":waves[0].get("frozen_scope_digest"),
        "authority_digest":waves[0].get("frozen_authority_digest"),
        "blocking_reasons":reasons,
        "saturation_candidate":not reasons,
        "decision":"SATURATION_CANDIDATE_NONFINAL" if not reasons else "HOLD_NONFINAL",
        "final_claim_allowed":False
    }
    RESULT_DIR.mkdir(parents=True, exist_ok=True)
    (RESULT_DIR / "saturation-receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2, sort_keys=True)+"\n", encoding="utf-8")
    print(json.dumps(receipt, ensure_ascii=False, sort_keys=True))
    return 0 if not reasons else 31

if __name__ == "__main__":
    try: raise SystemExit(main())
    except (OSError, ValueError, KeyError) as e:
        print(f"ONSURE_DISCOVERY_SATURATION_FAIL {e}", file=sys.stderr); raise SystemExit(1)
