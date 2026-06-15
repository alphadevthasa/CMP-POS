#!/usr/bin/env python3
"""Gate keputusan: bandingkan run kandidat dengan baseline.

  python3 scripts/harness/gate.py --baseline .harness/baseline.json --candidate RUN_ID \
      [--base-ref main --head-ref harness/exp-001] [--final] [--json]

Keputusan:
  ACCEPT   tidak ada pelanggaran dan ada perbaikan nyata (di atas ambang noise)
  NEUTRAL  tidak ada pelanggaran, tetapi tidak ada perbaikan nyata (exit 0)
  REJECT   ada pelanggaran: regresi invariant, pass rate turun, biaya/langkah naik tanpa
           perbaikan, retrieval turun, atau diff menyentuh file di luar `surface` (exit 1)
Konfigurasi gate SELALU dibaca dari repo tepercaya (bukan dari kandidat).
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import HARNESS_DIR, ROOT, load_config, match_any, read_json  # noqa: E402


def load_summary(arg: str) -> dict:
    p = Path(arg)
    if p.is_dir():
        p = p / "summary.json"
    elif not p.exists():
        p = HARNESS_DIR / "runs" / arg / "summary.json"
    if not p.exists():
        raise SystemExit(f"summary tidak ditemukan: {arg}")
    return read_json(p)


def changed_files(base_ref: str, head_ref: str) -> list[str]:
    r = subprocess.run(["git", "-C", str(ROOT), "diff", "--name-only", f"{base_ref}...{head_ref}"], capture_output=True, text=True)
    if r.returncode != 0:
        raise SystemExit(f"git diff gagal: {r.stderr.strip()}")
    return [ln for ln in r.stdout.splitlines() if ln.strip()]


def pct_increase(old, new):
    if old in (None, 0) or new is None:
        return None
    return (new - old) / old * 100.0


def decide(base: dict, cand: dict, cfg_gate: dict, surface_globs: list[str], changed: list[str] | None, final: bool) -> dict:
    violations: list[str] = []
    warnings: list[str] = []
    gains: list[str] = []

    if base.get("mode") != cand.get("mode"):
        return {"decision": "ERROR", "violations": [f"mode berbeda: {base.get('mode')} vs {cand.get('mode')}"], "warnings": [], "gains": []}
    # Lingkungan hanya memengaruhi hasil mode agent (cmd Gradle dilewati tanpa Android SDK).
    # Mode check hanya memakai invariants + RAG, jadi tidak bergantung pada lingkungan.
    if base.get("mode") == "agent" and base.get("env") != cand.get("env"):
        return {"decision": "ERROR", "violations": [f"lingkungan berbeda: baseline {base.get('env')} vs kandidat {cand.get('env')} — hasil tidak bisa dibandingkan"], "warnings": [], "gains": []}
    if base.get("agent_cmd_sha") != cand.get("agent_cmd_sha"):
        warnings.append("agent.cmd berbeda antara baseline dan kandidat — perbandingan mungkin tidak adil")

    # 1. Diff hanya boleh menyentuh permukaan harness
    if changed is not None:
        outside = [f for f in changed if not match_any(f, surface_globs)]
        if outside:
            violations.append("diff menyentuh file di luar surface: " + ", ".join(outside[:8]))

    # 2. Invariants: tidak boleh ada regresi
    bi, ci = base["invariants"]["checks"], cand["invariants"]["checks"]
    for k, v in ci.items():
        if v == "fail" and bi.get(k) != "fail":
            violations.append(f"regresi invariant `{k}`: {cand['invariants']['details'].get(k, '')}"[:300])
    for k in cand["invariants"]["failed"]:
        if bi.get(k) == "fail":
            warnings.append(f"invariant `{k}` sudah gagal sejak baseline")
    for k in bi:
        if k not in ci:
            violations.append(f"invariant `{k}` hilang di kandidat")

    # 3. Pass rate (dev, dan held-out bila ada)
    min_imp = cfg_gate["min_improvement"]
    dev_delta = None
    for sp in ("dev", "heldout"):
        b = base.get("splits", {}).get(sp)
        c = cand.get("splits", {}).get(sp)
        if sp == "heldout" and final and cfg_gate.get("require_heldout") and not c:
            violations.append("held-out belum dijalankan (wajib untuk keputusan akhir: --final)")
            continue
        if not b or not c:
            if b and not c and sp == "dev":
                violations.append("kandidat tidak punya hasil dev")
            continue
        delta = round(c["pass_rate"] - b["pass_rate"], 4)
        if sp == "dev":
            dev_delta = delta
        if delta < -cfg_gate["max_pass_rate_drop"] - 1e-9:
            violations.append(f"pass rate {sp} turun {b['pass_rate']} -> {c['pass_rate']}")
        elif delta >= min_imp:
            gains.append(f"pass rate {sp} naik {b['pass_rate']} -> {c['pass_rate']}")
        if c["unverified_rate"] > b["unverified_rate"] + 1e-9:
            warnings.append(f"{sp}: porsi hasil tidak terverifikasi penuh naik ({b['unverified_rate']} -> {c['unverified_rate']})")

    # 4. Biaya & langkah
    improved_enough = bool(gains)
    for key, limit_key, label in (("mean_usd", "max_cost_increase_pct", "biaya"), ("mean_steps", "max_steps_increase_pct", "langkah")):
        inc = pct_increase((base.get("cost") or {}).get(key), (cand.get("cost") or {}).get(key))
        if inc is None:
            continue
        if inc > cfg_gate[limit_key]:
            msg = f"{label} rata-rata naik {inc:.0f}% (batas {cfg_gate[limit_key]}%)"
            (warnings if improved_enough else violations).append(msg + (" — ditoleransi karena ada perbaikan nyata" if improved_enough else ""))
        elif inc <= -10:
            gains.append(f"{label} rata-rata turun {abs(inc):.0f}%")

    # 5. Retrieval RAG
    br, cr = base.get("rag"), cand.get("rag")
    if br and cr:
        if cr["hit_at_k"] is not None and br["hit_at_k"] is not None:
            d = round(cr["hit_at_k"] - br["hit_at_k"], 4)
            if d < -cfg_gate["max_rag_hit_drop"] - 1e-9:
                violations.append(f"RAG hit@k turun {br['hit_at_k']} -> {cr['hit_at_k']}")
            elif d > 0:
                gains.append(f"RAG hit@k naik {br['hit_at_k']} -> {cr['hit_at_k']}")
        if cr["mrr"] is not None and br["mrr"] is not None:
            d = round(cr["mrr"] - br["mrr"], 4)
            if d < -cfg_gate["max_rag_mrr_drop"] - 1e-9:
                violations.append(f"RAG MRR turun {br['mrr']} -> {cr['mrr']}")
            elif d >= 0.05:
                gains.append(f"RAG MRR naik {br['mrr']} -> {cr['mrr']}")
    elif br and not cr:
        violations.append("kandidat tidak punya hasil eval RAG")

    if violations:
        decision = "REJECT"
    elif gains:
        decision = "ACCEPT"
    else:
        decision = "NEUTRAL"
    return {"decision": decision, "violations": violations, "warnings": warnings, "gains": gains, "dev_delta": dev_delta}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--baseline", required=True)
    ap.add_argument("--candidate", required=True)
    ap.add_argument("--base-ref")
    ap.add_argument("--head-ref")
    ap.add_argument("--final", action="store_true", help="keputusan akhir: wajib ada held-out")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args(argv)

    cfg = load_config(ROOT)
    base = load_summary(args.baseline)
    cand = load_summary(args.candidate)
    changed = None
    if args.base_ref or args.head_ref:
        if not (args.base_ref and args.head_ref):
            raise SystemExit("--base-ref dan --head-ref harus dipakai berpasangan")
        changed = changed_files(args.base_ref, args.head_ref)

    res = decide(base, cand, cfg["gate"], cfg["surface"]["globs"], changed, args.final)
    if args.json:
        print(json.dumps(res, indent=2, ensure_ascii=False))
    else:
        print(f"KEPUTUSAN: {res['decision']}")
        for title, key in (("Pelanggaran", "violations"), ("Perbaikan", "gains"), ("Peringatan", "warnings")):
            if res[key]:
                print(f"{title}:")
                for x in res[key]:
                    print(f"  - {x}")
        if res["decision"] == "NEUTRAL":
            print("Tidak ada perbaikan nyata di atas ambang noise. Jangan merge kecuali perubahan ini menyederhanakan harness.")
    return {"ACCEPT": 0, "NEUTRAL": 0, "REJECT": 1, "ERROR": 2}[res["decision"]]


if __name__ == "__main__":
    sys.exit(main())
