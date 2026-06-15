#!/usr/bin/env python3
"""Analisis hasil eval: klasifikasi kegagalan, tugas flaky, biaya, dan rekomendasi tuas.

  python3 scripts/harness/analyze.py [RUN_ID|PATH]     (default: run terakhir)
Menulis report.md dan failures.json di folder run.
"""
from __future__ import annotations

import argparse
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import HARNESS_DIR, read_json, write_json  # noqa: E402

LOOP_THRESHOLD = 4


def resolve_run(arg: str | None) -> Path:
    runs = HARNESS_DIR / "runs"
    if arg is None:
        latest = runs / "LATEST"
        if not latest.exists():
            raise SystemExit("Belum ada run. Jalankan scripts/harness/run_eval.py dulu.")
        arg = latest.read_text().strip()
    p = Path(arg)
    if not p.is_dir():
        p = runs / arg
    if not (p / "summary.json").exists():
        raise SystemExit(f"summary.json tidak ditemukan di {p}")
    return p


def detect_loop(seq: list) -> bool:
    run = 1
    for a, b in zip(seq, seq[1:]):
        run = run + 1 if a == b else 1
        if run >= LOOP_THRESHOLD:
            return True
    return False


def classify(trial: dict, taxonomy: dict) -> tuple[str | None, str]:
    """Kategori utama untuk percobaan GAGAL + alasan singkat."""
    if trial["status"] != "fail":
        return None, ""
    if trial.get("timed_out"):
        return "timeout_or_loop", "melewati batas waktu"
    if detect_loop(trial.get("tool_sequence") or []):
        return "timeout_or_loop", f"tool call identik >= {LOOP_THRESHOLD} kali berurutan"
    failing = [c for c in trial["checks"] if c["status"] == "fail"]
    if trial.get("agent_is_error") and not failing:
        return "agent_error", "agen melaporkan error"
    if not failing:
        return "agent_error", f"exit code {trial.get('exit_code')}"
    cats = [c["category"] for c in failing if c["category"] in taxonomy]
    if not cats:
        return "missing_change", failing[0]["name"]
    cats.sort(key=lambda c: taxonomy[c]["priority"])
    primary = cats[0]
    first = next(c for c in failing if c["category"] == primary)
    return primary, f"{first['name']}: {first['detail']}".strip(": ")[:160]


def load_trials(run: Path) -> list[dict]:
    out = []
    for p in sorted(run.glob("*/*/result.json")):
        out.append(read_json(p))
    return out


def build_report(run: Path) -> tuple[str, dict]:
    summary = read_json(run / "summary.json")
    taxonomy = read_json(HARNESS_DIR / "taxonomy.json")
    trials = load_trials(run)

    failures = []
    by_cat: dict[str, list[dict]] = defaultdict(list)
    for t in trials:
        cat, why = classify(t, taxonomy)
        if cat:
            rec = {"task": t["task"], "split": t["split"], "rep": t["rep"], "category": cat, "why": why}
            failures.append(rec)
            by_cat[cat].append(rec)

    L: list[str] = []
    L.append(f"# Laporan eval `{summary['run_id']}`")
    L.append("")
    L.append(f"- Mode: **{summary['mode']}**, revisi repo: `{summary.get('git_rev')}`, dibuat {summary['created_at']}")
    L.append(f"- Lingkungan: {summary['env']}")
    inv = summary["invariants"]
    L.append(f"- Invariants: **{inv['passed']}/{inv['total']}** lulus" + (f" — gagal: {', '.join(inv['failed'])}" if inv["failed"] else ""))
    if "rag" in summary:
        r = summary["rag"]
        L.append(f"- RAG: hit@k **{r['hit_at_k']}**, MRR **{r['mrr']}** (n={r['n']}; dev {r['dev']['hit_at_k']}, held-out {r['heldout']['hit_at_k']})")
    for sp, data in summary.get("splits", {}).items():
        L.append(f"- `{sp}`: pass rate **{data['pass_rate']}** ({data['n_tasks']} task, {data['n_trials']} percobaan; tidak terverifikasi penuh: {data['unverified_rate']})")
    c = summary.get("cost")
    if c and any(v is not None for v in c.values()):
        L.append(f"- Rata-rata per percobaan: biaya ${c['mean_usd']}, langkah {c['mean_steps']}, durasi {c['mean_duration_s']}s; total token {c['total_tokens']}")
    L.append("")

    if by_cat:
        L.append("## Taksonomi kegagalan")
        L.append("")
        L.append("| Kategori | Percobaan | Task | Tuas yang disarankan |")
        L.append("|---|---:|---|---|")
        for cat, recs in sorted(by_cat.items(), key=lambda kv: -len(kv[1])):
            tasks = ", ".join(sorted({r["task"] for r in recs}))
            L.append(f"| `{cat}` | {len(recs)} | {tasks} | {taxonomy[cat]['levers'][0]} |")
        L.append("")
        top = sorted(by_cat.items(), key=lambda kv: -len(kv[1]))[:2]
        L.append("## Rekomendasi siklus berikutnya")
        L.append("")
        L.append("Pilih **satu** tuas (satu perubahan per eksperimen), mulai dari kategori terbesar:")
        L.append("")
        for cat, recs in top:
            L.append(f"- `{cat}` ({len(recs)} percobaan) — {taxonomy[cat]['description']}")
            for lv in taxonomy[cat]["levers"]:
                L.append(f"  - {lv}")
        L.append("")
        L.append("## Contoh kegagalan")
        L.append("")
        for cat, recs in sorted(by_cat.items(), key=lambda kv: -len(kv[1])):
            for r in recs[:3]:
                L.append(f"- `{cat}` · {r['split']}/{r['task']} #{r['rep']}: {r['why']}")
        L.append("")

    # Friksi izin tool: agen mencoba tool yang tidak diizinkan (bukan kegagalan, tetapi memboroskan langkah)
    denied = [t for t in trials if (t.get("permission_denials") or 0) > 0]
    if denied:
        L.append("## Penolakan izin tool")
        L.append("")
        L.append(f"{len(denied)} dari {len(trials)} percobaan sempat ditolak izin tool (agen lalu mencari jalan lain). "
                 "Jika berulang untuk tool yang memang wajar dipakai, longgarkan `--allowedTools` di `agent.cmd`; "
                 "jika agen mencoba tool yang seharusnya dihindari, perjelas di AGENTS.md.")
        L.append("")
        for t in denied[:10]:
            L.append(f"- {t['split']}/{t['task']} #{t['rep']}: {t['permission_denials']} penolakan")
        L.append("")

    # Tugas flaky: lulus sebagian
    flaky = []
    for sp, data in summary.get("splits", {}).items():
        for tid, tk in data["tasks"].items():
            if 0 < tk["pass_rate"] < 1:
                flaky.append(f"{sp}/{tid} ({tk['pass_rate']})")
    if flaky:
        L.append("## Task flaky (hasil tidak stabil antar ulangan)")
        L.append("")
        L.extend(f"- {f}" for f in flaky)
        L.append("")
        L.append("Perbaikan di bawah rentang noise ini tidak boleh dianggap nyata; naikkan `repeats`.")
        L.append("")

    if trials:
        by_task: dict[str, list[dict]] = defaultdict(list)
        for t in trials:
            by_task[t["task"]].append(t)
        L.append("## Per task")
        L.append("")
        L.append("| Task | Split | Lulus | Langkah (rata-rata) | Biaya USD (rata-rata) | Durasi s |")
        L.append("|---|---|---:|---:|---:|---:|")
        for tid, tt in sorted(by_task.items()):
            def mean(k):
                v = [x[k] for x in tt if x.get(k) is not None]
                return round(sum(v) / len(v), 2) if v else "-"
            L.append(f"| {tid} | {tt[0]['split']} | {sum(x['passed'] for x in tt)}/{len(tt)} | {mean('steps')} | {mean('cost_usd')} | {mean('duration_s')} |")
        L.append("")

    if inv["failed"]:
        L.append("## Invariant gagal")
        L.append("")
        for k in inv["failed"]:
            L.append(f"- `{k}`: {inv['details'].get(k, '')}")
        L.append("")

    misses = summary.get("rag", {}).get("misses") or []
    if misses:
        L.append("## Query RAG yang meleset")
        L.append("")
        L.extend(f"- {m}" for m in misses)
        L.append("")
        L.append(f"Tuas: {'; '.join(taxonomy['retrieval_miss']['levers'])}")
        L.append("")

    if not trials and not by_cat and not inv["failed"] and not misses:
        L.append("Tidak ada kegagalan terdeteksi di run ini.")
        L.append("")

    return "\n".join(L), {"failures": failures, "by_category": {k: len(v) for k, v in by_cat.items()}}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("run", nargs="?", default=None)
    ap.add_argument("--print", action="store_true", help="cetak laporan ke stdout")
    args = ap.parse_args(argv)
    run = resolve_run(args.run)
    md, data = build_report(run)
    (run / "report.md").write_text(md + "\n", encoding="utf-8")
    write_json(run / "failures.json", data)
    if args.print:
        print(md)
    else:
        print(f"Laporan: {run / 'report.md'}")
        if data["by_category"]:
            print("Kegagalan per kategori:", dict(Counter(data["by_category"])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
