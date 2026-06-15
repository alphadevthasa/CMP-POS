#!/usr/bin/env python3
"""Runner eval harness.

Mode:
  check     jalankan guardrail (invariants) pada repo apa adanya + eval retrieval RAG.
            Cepat, tanpa agen, aman untuk CI.
  validate  uji definisi task: tiap task dev/heldout HARUS gagal di repo yang belum diubah
            (kalau sudah lulus, task itu tidak mengukur apa-apa).
  agent     jalankan agen pada tiap task (di salinan repo), N kali, simpan trace, lalu
            nilai hasilnya dengan checks task + semua invariants (guard regresi).

Contoh:
  python3 scripts/harness/run_eval.py --mode check --save-baseline
  python3 scripts/harness/run_eval.py --mode agent --split dev --repeats 3
  python3 scripts/harness/run_eval.py --mode agent --repo ../cmp-pos-candidate
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import difflib
import hashlib
import json
import os
import re
import shlex
import shutil
import signal
import subprocess
import sys
import tempfile
import time
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rag as ragmod  # noqa: E402
from common import ROOT, iter_files, load_config, match_any, read_json, sha1_bytes, write_json  # noqa: E402

SKIP_NAMES = {".git", ".gradle", ".kotlin", ".idea", "build", "__pycache__", ".DS_Store"}
# `evals` TIDAK boleh ikut ke salinan kerja agen: di sana ada kriteria lulus dan tes tersembunyi.
SKIP_HARNESS_SUBDIRS = {"runs", "rag", "evals"}
DEFAULT_CATEGORY = {
    "file_exists": "missing_change",
    "file_contains": "missing_change",
    "file_not_contains": "forbidden_change",
    "cmd": "test_failure",
    "max_changed_files": "scope_creep",
    "changed_only_within": "scope_creep",
}
AGENT_SPLITS = ("dev", "heldout")


# ----------------------------------------------------------------------------
# Memuat task
# ----------------------------------------------------------------------------

def load_tasks(evals_dir: Path, splits: list[str]) -> list[dict]:
    tasks: list[dict] = []
    seen: set[str] = set()
    for split in splits:
        d = evals_dir / split
        if not d.is_dir():
            continue
        for p in sorted(d.glob("*.json")):
            t = read_json(p)
            t["split"] = split
            t["_file"] = p.name
            problems = validate_task(t, split)
            if problems:
                raise SystemExit(f"Task {p} tidak valid: " + "; ".join(problems))
            if t["id"] in seen:
                raise SystemExit(f"ID task ganda: {t['id']}")
            seen.add(t["id"])
            tasks.append(t)
    return tasks


def validate_task(t: dict, split: str) -> list[str]:
    errs = []
    if not t.get("id") or not re.fullmatch(r"[a-z0-9][a-z0-9-]*", t["id"]):
        errs.append("`id` wajib, huruf kecil/angka/strip")
    if not isinstance(t.get("checks"), list) or not t["checks"]:
        errs.append("`checks` wajib dan tidak boleh kosong")
    if split in AGENT_SPLITS and not t.get("prompt"):
        errs.append("`prompt` wajib untuk task dev/heldout")
    for c in t.get("checks", []):
        ty = c.get("type")
        if ty not in DEFAULT_CATEGORY:
            errs.append(f"tipe check tidak dikenal: {ty}")
            continue
        for key in ("pattern",):
            if key in c:
                try:
                    re.compile(c[key])
                except re.error as exc:
                    errs.append(f"regex salah di check {ty}: {exc}")
        if ty in ("file_contains", "file_not_contains") and not (c.get("glob") or c.get("path")):
            errs.append(f"{ty} butuh `glob` atau `path`")
        if ty == "cmd" and not c.get("cmd"):
            errs.append("cmd butuh `cmd`")
    return errs


# ----------------------------------------------------------------------------
# Checks
# ----------------------------------------------------------------------------

def _globs(c: dict) -> list[str]:
    g = c.get("glob") or c.get("path")
    return [g] if isinstance(g, str) else list(g)


def _flags(c: dict) -> int:
    f = re.M
    if c.get("dotall"):
        f |= re.S
    return f


def _read(path: Path) -> str | None:
    try:
        return path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return None


def run_check(c: dict, workdir: Path, ctx: dict, default_category: str | None = None) -> dict:
    ty = c["type"]
    target = c.get("glob") or c.get("path") or c.get("cmd") or c.get("globs") or c.get("n", "")
    name = c.get("name") or f"{ty}:{target if isinstance(target, (str, int)) else ','.join(target)}"
    res = {
        "name": name,
        "type": ty,
        "status": "pass",
        "detail": "",
        "category": c.get("category") or default_category or DEFAULT_CATEGORY[ty],
    }

    def done(status: str, detail: str = ""):
        res["status"], res["detail"] = status, detail
        return res

    if ty == "file_exists":
        ok = all((workdir / g).exists() for g in _globs(c))
        return done("pass" if ok else "fail", "" if ok else f"tidak ada: {', '.join(_globs(c))}")

    if ty in ("file_contains", "file_not_contains"):
        rx = re.compile(c["pattern"], _flags(c))
        files = list(iter_files(workdir, _globs(c), list(SKIP_NAMES)))
        if not files:
            return done("fail", f"tidak ada file cocok untuk {_globs(c)}")
        total = 0
        offenders: list[str] = []
        for rel in files:
            text = _read(workdir / rel)
            if text is None:
                continue
            for m in rx.finditer(text):
                total += 1
                if len(offenders) < 5:
                    line = text.count("\n", 0, m.start()) + 1
                    offenders.append(f"{rel}:{line}: {m.group(0).strip()[:80]}")
        if ty == "file_contains":
            need = c.get("min_count", 1)
            return done("pass" if total >= need else "fail", "" if total >= need else f"ditemukan {total}, butuh >= {need}")
        return done("pass" if total == 0 else "fail", "" if total == 0 else "; ".join(offenders))

    if ty == "cmd":
        missing_env = [e for e in c.get("requires_env", []) if not os.environ.get(e)]
        missing_tools = [t for t in c.get("requires_tools", []) if not shutil.which(t)]
        if missing_env or missing_tools:
            return done("skip", "butuh " + ", ".join(missing_env + missing_tools))
        cwd = workdir / c.get("cwd", ".")
        try:
            p = subprocess.run(
                c["cmd"], shell=True, cwd=cwd, capture_output=True, text=True,
                timeout=c.get("timeout", 600),
            )
        except subprocess.TimeoutExpired:
            return done("fail", f"timeout > {c.get('timeout', 600)}s")
        ok = p.returncode == c.get("expect_exit", 0)
        tail = (p.stdout + p.stderr).strip()[-400:]
        return done("pass" if ok else "fail", "" if ok else f"exit {p.returncode}: {tail}")

    if ty in ("max_changed_files", "changed_only_within"):
        changed = ctx.get("changed")
        if changed is None:
            return done("skip", "hanya bermakna di mode agent")
        if ty == "max_changed_files":
            ok = len(changed) <= c["n"]
            return done("pass" if ok else "fail", "" if ok else f"{len(changed)} file berubah (maks {c['n']})")
        outside = sorted(f for f in changed if not match_any(f, c["globs"]))
        return done("pass" if not outside else "fail", "" if not outside else "di luar lingkup: " + ", ".join(outside[:5]))

    return done("fail", f"tipe tidak dikenal {ty}")


def run_checks(task: dict, workdir: Path, ctx: dict, default_category: str | None = None) -> list[dict]:
    return [run_check(c, workdir, ctx, default_category) for c in task["checks"]]


def trial_status(checks: list[dict]) -> str:
    if any(c["status"] == "fail" for c in checks):
        return "fail"
    if any(c["status"] == "skip" for c in checks):
        return "pass_unverified"
    return "pass"


# ----------------------------------------------------------------------------
# Snapshot, diff, salinan repo
# ----------------------------------------------------------------------------

def _copy_ignore(repo: Path):
    def ignore(directory, names):
        rel = Path(directory).resolve().relative_to(repo.resolve()).as_posix()
        out = {n for n in names if n in SKIP_NAMES}
        if rel == ".harness":
            out |= {n for n in names if n in SKIP_HARNESS_SUBDIRS}
        return out
    return ignore


def snapshot(root: Path) -> dict[str, str]:
    snap = {}
    for dirpath, dirnames, filenames in os.walk(root):
        rel_dir = Path(dirpath).relative_to(root).as_posix()
        dirnames[:] = [d for d in dirnames if d not in SKIP_NAMES and not (rel_dir == ".harness" and d in SKIP_HARNESS_SUBDIRS)]
        for n in filenames:
            if n == ".DS_Store":
                continue
            p = Path(dirpath) / n
            snap[p.relative_to(root).as_posix()] = sha1_bytes(p.read_bytes())
    return snap


def make_diff(before_dir_files: dict[str, str], orig_root: Path, new_root: Path, changed: list[str], cap: int = 200_000) -> str:
    parts: list[str] = []
    for rel in changed:
        a = _read(orig_root / rel) if (orig_root / rel).exists() else ""
        b = _read(new_root / rel) if (new_root / rel).exists() else ""
        if a is None or b is None:
            parts.append(f"Binary files differ: {rel}\n")
            continue
        parts.extend(difflib.unified_diff(a.splitlines(True), b.splitlines(True), f"a/{rel}", f"b/{rel}"))
    out = "".join(parts)
    return out[:cap] + ("\n... (dipotong)\n" if len(out) > cap else "")


def git_rev(repo: Path) -> str:
    try:
        rev = subprocess.run(["git", "-C", str(repo), "rev-parse", "--short", "HEAD"], capture_output=True, text=True, timeout=10).stdout.strip()
        dirty = subprocess.run(["git", "-C", str(repo), "status", "--porcelain"], capture_output=True, text=True, timeout=10).stdout.strip()
        return (rev or "unknown") + ("+dirty" if dirty else "")
    except Exception:
        return "unknown"


# ----------------------------------------------------------------------------
# Metrik dari output agen
# ----------------------------------------------------------------------------

def _usage_tokens(usage: dict | None) -> int:
    if not isinstance(usage, dict):
        return 0
    keys = ("input_tokens", "output_tokens", "cache_creation_input_tokens", "cache_read_input_tokens")
    return sum(int(usage.get(k) or 0) for k in keys)


def extract_metrics(stdout: str, trace_path: Path | None = None) -> dict:
    """Ambil langkah/biaya/token dari output agen. Mendukung:
    - satu objek JSON `{"type":"result", num_turns, total_cost_usd, usage}` (claude --output-format json)
    - baris stream-json (event assistant berisi tool_use + event result akhir)
    - JSONL trace kustom dengan baris {"type":"tool_call","name":...,"args":...}
    Field yang tidak ada dibiarkan None (bukan ditebak)."""
    m = {"steps": None, "cost_usd": None, "tokens": None, "agent_is_error": None, "permission_denials": None, "tool_sequence": []}

    def apply_result(obj: dict):
        if "permission_denials" in obj:
            m["permission_denials"] = len(obj["permission_denials"] or [])
        if obj.get("num_turns") is not None:
            m["steps"] = obj["num_turns"]
        if obj.get("total_cost_usd") is not None:
            m["cost_usd"] = obj["total_cost_usd"]
        tk = _usage_tokens(obj.get("usage"))
        if tk:
            m["tokens"] = tk
        if "is_error" in obj:
            m["agent_is_error"] = bool(obj["is_error"])

    text = stdout.strip()
    try:
        obj = json.loads(text)
        if isinstance(obj, dict) and obj.get("type") == "result":
            apply_result(obj)
            return m
    except (json.JSONDecodeError, ValueError):
        pass

    sources = [text.splitlines()]
    if trace_path and trace_path.exists():
        sources.append(trace_path.read_text(encoding="utf-8", errors="replace").splitlines())
    for lines in sources:
        for line in lines:
            line = line.strip()
            if not line.startswith("{"):
                continue
            try:
                ev = json.loads(line)
            except json.JSONDecodeError:
                continue
            if ev.get("type") == "result":
                apply_result(ev)
            elif ev.get("type") == "assistant":
                for blk in (ev.get("message") or {}).get("content") or []:
                    if isinstance(blk, dict) and blk.get("type") == "tool_use":
                        h = hashlib.sha1(json.dumps(blk.get("input"), sort_keys=True).encode()).hexdigest()[:8]
                        m["tool_sequence"].append([blk.get("name"), h])
            elif ev.get("type") == "tool_call":
                h = hashlib.sha1(json.dumps(ev.get("args"), sort_keys=True).encode()).hexdigest()[:8]
                m["tool_sequence"].append([ev.get("name"), h])
    if m["steps"] is None and m["tool_sequence"]:
        m["steps"] = len(m["tool_sequence"])
    return m


# ----------------------------------------------------------------------------
# Satu percobaan agen
# ----------------------------------------------------------------------------

def run_agent_trial(task: dict, rep: int, repo: Path, evals_src: Path, run_dir: Path, cfg: dict, invariants: list[dict], tmp_root: Path) -> dict:
    tdir = run_dir / task["id"] / str(rep)
    tdir.mkdir(parents=True, exist_ok=True)
    workdir = tmp_root / f"{task['id']}-{rep}"
    shutil.copytree(repo, workdir, ignore=_copy_ignore(repo))
    before = snapshot(workdir)

    prompt_file = tdir / "prompt.txt"
    prompt_file.write_text(task["prompt"], encoding="utf-8")
    trace_file = tdir / "trace.jsonl"
    cmd = (
        cfg["agent"]["cmd"]
        .replace("{prompt_file}", shlex.quote(str(prompt_file)))
        .replace("{workdir}", shlex.quote(str(workdir)))
        .replace("{trace_file}", shlex.quote(str(trace_file)))
    )
    env = {**os.environ, "HARNESS_TASK_ID": task["id"], "HARNESS_PROMPT_FILE": str(prompt_file), "HARNESS_TRACE": str(trace_file), "HARNESS_WORKDIR": str(workdir)}

    t0 = time.time()
    timed_out = False
    proc = subprocess.Popen(cmd, shell=True, cwd=workdir, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, start_new_session=True)
    try:
        out, err = proc.communicate(timeout=cfg["agent"]["timeout_s"])
    except subprocess.TimeoutExpired:
        timed_out = True
        os.killpg(proc.pid, signal.SIGKILL)
        out, err = proc.communicate()
    duration = round(time.time() - t0, 2)
    (tdir / "agent_stdout.txt").write_text(out or "", encoding="utf-8")
    (tdir / "agent_stderr.txt").write_text(err or "", encoding="utf-8")

    after = snapshot(workdir)
    changed = sorted(f for f in set(before) | set(after) if before.get(f) != after.get(f))
    (tdir / "diff.patch").write_text(make_diff(before, repo, workdir, changed), encoding="utf-8")

    # File uji tersembunyi baru masuk SETELAH agen selesai (agen tidak melihatnya).
    for inj in task.get("inject_files", []):
        dst = workdir / inj["dst"]
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(evals_src / inj["src"], dst)

    ctx = {"changed": changed}
    checks = run_checks(task, workdir, ctx)
    inv_checks = []
    for inv in invariants:  # satu entri per invariant, bukan per check, supaya laporan tidak berulang
        results = run_checks(inv, workdir, ctx, default_category="rule_violation")
        bad = [r["detail"] for r in results if r["status"] == "fail"]
        inv_checks.append({
            "name": f"invariant:{inv['id']}",
            "type": "invariant",
            "status": "fail" if bad else ("skip" if any(r["status"] == "skip" for r in results) else "pass"),
            "detail": " | ".join(bad)[:300],
            "category": "rule_violation",
        })
    all_checks = checks + inv_checks

    metrics = extract_metrics(out or "", trace_file)
    status = trial_status(all_checks)
    result = {
        "task": task["id"],
        "split": task["split"],
        "rep": rep,
        "status": status,
        "passed": status in ("pass", "pass_unverified"),
        "exit_code": proc.returncode,
        "timed_out": timed_out,
        "duration_s": duration,
        "files_changed": changed,
        "checks": all_checks,
        **metrics,
    }
    write_json(tdir / "result.json", result)
    shutil.rmtree(workdir, ignore_errors=True)
    return result


# ----------------------------------------------------------------------------
# Ringkasan
# ----------------------------------------------------------------------------

def _mean(vals):
    vals = [v for v in vals if v is not None]
    return round(sum(vals) / len(vals), 4) if vals else None


def summarize_trials(trials: list[dict]) -> dict:
    by_split: dict[str, dict] = {}
    for sp in AGENT_SPLITS:
        st = [t for t in trials if t["split"] == sp]
        if not st:
            continue
        tasks: dict[str, dict] = {}
        for tid in sorted({t["task"] for t in st}):
            tt = [t for t in st if t["task"] == tid]
            tasks[tid] = {
                "trials": len(tt),
                "pass_rate": round(sum(t["passed"] for t in tt) / len(tt), 4),
                "statuses": Counter(t["status"] for t in tt),
            }
        by_split[sp] = {
            "n_tasks": len(tasks),
            "n_trials": len(st),
            "pass_rate": round(sum(t["passed"] for t in st) / len(st), 4),
            "unverified_rate": round(sum(t["status"] == "pass_unverified" for t in st) / len(st), 4),
            "tasks": tasks,
        }
    cost = {
        "mean_usd": _mean([t.get("cost_usd") for t in trials]),
        "mean_steps": _mean([t.get("steps") for t in trials]),
        "mean_duration_s": _mean([t.get("duration_s") for t in trials]),
        "total_tokens": sum(t.get("tokens") or 0 for t in trials) or None,
    }
    return {"splits": by_split, "cost": cost}


def summarize_invariants(invs: list[dict], workdir: Path) -> dict:
    checks: dict[str, str] = {}
    details: dict[str, str] = {}
    for inv in invs:
        results = run_checks(inv, workdir, {"changed": None}, default_category="rule_violation")
        st = trial_status(results)
        checks[inv["id"]] = "fail" if st == "fail" else ("skip" if st == "pass_unverified" else "pass")
        bad = [r["detail"] for r in results if r["status"] == "fail"]
        if bad:
            details[inv["id"]] = " | ".join(bad)[:300]
    return {
        "passed": sum(v == "pass" for v in checks.values()),
        "total": len(checks),
        "failed": sorted(k for k, v in checks.items() if v == "fail"),
        "checks": checks,
        "details": details,
    }


def env_fingerprint() -> dict:
    return {"android_sdk": bool(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))}


# ----------------------------------------------------------------------------
# CLI
# ----------------------------------------------------------------------------

def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mode", choices=["check", "validate", "agent"], default="check")
    ap.add_argument("--repo", default=str(ROOT), help="repo yang dievaluasi (mis. worktree kandidat)")
    ap.add_argument("--evals", default=None, help="folder evals (default: .harness/evals milik repo ini, BUKAN kandidat)")
    ap.add_argument("--split", choices=["dev", "heldout", "all"], default="dev")
    ap.add_argument("--task", action="append", help="batasi ke id task tertentu (boleh berulang)")
    ap.add_argument("--repeats", type=int, default=None)
    ap.add_argument("--jobs", type=int, default=1)
    ap.add_argument("--run-id", default=None)
    ap.add_argument("--no-rag", action="store_true", help="lewati eval retrieval RAG")
    ap.add_argument("--save-baseline", action="store_true", help="simpan summary sebagai baseline (check -> baseline.json, agent -> baseline-agent.json)")
    args = ap.parse_args(argv)

    repo = Path(args.repo).resolve()
    harness_root = ROOT  # evals, skrip, DAN konfigurasi diambil dari repo tepercaya ini, bukan dari kandidat
    cfg = load_config(harness_root)
    evals_dir = Path(args.evals).resolve() if args.evals else harness_root / ".harness" / "evals"
    run_id = args.run_id or time.strftime("%Y%m%d-%H%M%S") + f"-{args.mode}"
    run_dir = harness_root / ".harness" / "runs" / run_id
    run_dir.mkdir(parents=True, exist_ok=True)

    invariants = load_tasks(evals_dir, ["invariants"])
    agent_splits = ["dev", "heldout"] if args.split == "all" else [args.split]
    agent_tasks = load_tasks(evals_dir, agent_splits)
    if args.task:
        agent_tasks = [t for t in agent_tasks if t["id"] in args.task]
        if not agent_tasks:
            print("Tidak ada task cocok dengan --task", file=sys.stderr)
            return 2

    summary: dict = {
        "run_id": run_id,
        "mode": args.mode,
        "repo": repo.name,
        "git_rev": git_rev(repo),
        "created_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "env": env_fingerprint(),
        "agent_cmd_sha": sha1_bytes(cfg["agent"]["cmd"].encode())[:10],
    }

    exit_code = 0
    summary["invariants"] = summarize_invariants(invariants, repo)
    inv = summary["invariants"]
    print(f"Invariants: {inv['passed']}/{inv['total']} lulus" + (f"  GAGAL: {', '.join(inv['failed'])}" if inv["failed"] else ""))
    for k in inv["failed"]:
        print(f"  - {k}: {inv['details'].get(k, '')}")
    if inv["failed"]:
        exit_code = 1

    if not args.no_rag:
        index = ragmod.build_index(repo, verbose=False, cfg=cfg)
        qpath = evals_dir / "rag_queries.json"
        if qpath.exists():
            rg = cfg["rag"]
            res = ragmod.eval_queries(index, read_json(qpath), default_k=rg["top_k"], glossary=rg.get("glossary"))
            sp = {s: ragmod.eval_queries(index, read_json(qpath), default_k=rg["top_k"], split=s, glossary=rg.get("glossary")) for s in ("dev", "heldout")}
            summary["rag"] = {
                "n": res["n"], "hit_at_k": res["hit_at_k"], "mrr": res["mrr"],
                "dev": {"n": sp["dev"]["n"], "hit_at_k": sp["dev"]["hit_at_k"], "mrr": sp["dev"]["mrr"]},
                "heldout": {"n": sp["heldout"]["n"], "hit_at_k": sp["heldout"]["hit_at_k"], "mrr": sp["heldout"]["mrr"]},
                "misses": [r["q"] for r in res["results"] if not r["hit"]],
            }
            print(f"RAG: n={res['n']}  hit@k={res['hit_at_k']}  MRR={res['mrr']}  (dev {sp['dev']['hit_at_k']}, held-out {sp['heldout']['hit_at_k']})")

    if args.mode == "validate":
        bad = 0
        report = {}
        for t in agent_tasks:
            workdir = Path(tempfile.mkdtemp(prefix="validate-")) / "repo"
            shutil.copytree(repo, workdir, ignore=_copy_ignore(repo))
            for inj in t.get("inject_files", []):
                dst = workdir / inj["dst"]
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(evals_dir / inj["src"], dst)
            checks = run_checks(t, workdir, {"changed": None})
            failing = [c["name"] for c in checks if c["status"] == "fail"]
            ok = bool(failing)
            report[t["id"]] = {"fails_on_pristine_repo": ok, "failing_checks": failing}
            print(f"  {'OK  ' if ok else 'WARN'} {t['split']}/{t['id']}: " + (f"{len(failing)} check gagal di repo awal (benar)" if ok else "SUDAH LULUS di repo awal — task tidak mengukur apa-apa"))
            bad += 0 if ok else 1
            shutil.rmtree(workdir.parent, ignore_errors=True)
        summary["validate"] = report
        if bad:
            exit_code = 1

    if args.mode == "agent":
        if not cfg["agent"]["cmd"].strip():
            print("agent.cmd kosong di .harness/harness.toml — isi dulu untuk mode agent.", file=sys.stderr)
            return 2
        repeats = args.repeats or cfg["agent"]["repeats"]
        tmp_root = Path(tempfile.mkdtemp(prefix="harness-trials-"))
        jobs = [(t, r) for t in agent_tasks for r in range(1, repeats + 1)]
        print(f"Menjalankan {len(jobs)} percobaan ({len(agent_tasks)} task x {repeats}) …")
        trials: list[dict] = []
        with cf.ThreadPoolExecutor(max_workers=max(1, args.jobs)) as pool:
            futs = {pool.submit(run_agent_trial, t, r, repo, evals_dir, run_dir, cfg, invariants, tmp_root): (t, r) for t, r in jobs}
            for fut in cf.as_completed(futs):
                t, r = futs[fut]
                res = fut.result()
                trials.append(res)
                print(f"  {res['status']:<16} {t['split']}/{t['id']} #{r}  ({res['duration_s']}s, langkah={res.get('steps')}, biaya={res.get('cost_usd')})")
        shutil.rmtree(tmp_root, ignore_errors=True)
        summary.update(summarize_trials(trials))
        summary["trials"] = [{k: v for k, v in t.items() if k not in ("checks", "tool_sequence", "files_changed")} for t in trials]
        for sp, data in summary["splits"].items():
            print(f"{sp}: pass rate {data['pass_rate']}  ({data['n_tasks']} task, {data['n_trials']} percobaan, unverified {data['unverified_rate']})")

    # Counter tidak serializable apa adanya
    for data in summary.get("splits", {}).values():
        for tk in data["tasks"].values():
            tk["statuses"] = dict(tk["statuses"])
    write_json(run_dir / "summary.json", summary)
    (harness_root / ".harness" / "runs" / "LATEST").write_text(run_id + "\n", encoding="utf-8")
    print(f"Ringkasan: .harness/runs/{run_id}/summary.json")
    if args.save_baseline:
        if args.mode == "validate":
            print("--save-baseline tidak berlaku untuk mode validate", file=sys.stderr)
            return 2
        name = "baseline.json" if args.mode == "check" else "baseline-agent.json"
        write_json(harness_root / ".harness" / name, summary)
        print(f"Baseline disimpan: .harness/{name}")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
