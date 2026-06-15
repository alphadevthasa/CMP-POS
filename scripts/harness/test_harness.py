"""Tes untuk harness itu sendiri (stdlib unittest).

  python3 -I -m unittest discover -s scripts/harness -v

Termasuk:
  - tes mutasi: menyuntikkan pelanggaran ke SALINAN kode asli untuk membuktikan bahwa tiap
    invariant benar-benar bisa gagal (bukan selalu hijau),
  - pipeline end-to-end dengan agen palsu (lulus, scope creep, pelanggaran aturan, timeout,
    file uji tersembunyi),
  - keputusan gate, parser metrik, dan RAG.
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import textwrap
import time
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
REAL_ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))

import analyze  # noqa: E402
import gate  # noqa: E402
import rag  # noqa: E402
import run_eval  # noqa: E402
from common import glob_to_regex, load_config, match_any  # noqa: E402

CFG_TEMPLATE = """
[project]
name = "t"
[surface]
globs = ["AGENTS.md", "docs/knowledge/**"]
[agent]
cmd = {cmd!r}
timeout_s = {timeout}
repeats = 2
[gate]
max_pass_rate_drop = 0.0
min_improvement = 0.10
max_cost_increase_pct = 20
max_steps_increase_pct = 25
require_heldout = true
max_rag_hit_drop = 0.0
max_rag_mrr_drop = 0.05
[rag]
include = ["README.md", "docs/**"]
exclude_dirs = [".harness", ".git"]
extensions = [".md", ".txt", ".kt"]
chunk_chars = 400
min_chunk_chars = 100
top_k = 5
index_path = ".harness/rag/index.json"
[rag.glossary]
pesanan = ["order"]
"""


def write(p: Path, text: str):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def write_json(p: Path, obj):
    write(p, json.dumps(obj, indent=2))


# ---------------------------------------------------------------------------
class TestCommon(unittest.TestCase):
    def test_glob(self):
        self.assertTrue(glob_to_regex("a/**/*.kt").match("a/b/c/X.kt"))
        self.assertTrue(glob_to_regex("a/**/*.kt").match("a/X.kt"))
        self.assertFalse(glob_to_regex("a/*.kt").match("a/b/X.kt"))
        self.assertTrue(glob_to_regex("**/*.kts").match("build.gradle.kts"))
        self.assertTrue(match_any("docs/knowledge/x/y.md", ["docs/knowledge/**"]))
        self.assertFalse(match_any("composeApp/src/A.kt", ["AGENTS.md", "docs/knowledge/**"]))

    def test_real_config_loads_and_surface_excludes_tooling(self):
        cfg = load_config(REAL_ROOT)
        globs = cfg["surface"]["globs"]
        for protected in (".harness/evals/dev/x.json", "scripts/harness/gate.py", ".harness/harness.toml", ".claude/commands/harness-optimize.md", "composeApp/src/commonMain/kotlin/A.kt"):
            self.assertFalse(match_any(protected, globs), protected)
        self.assertTrue(match_any("AGENTS.md", globs))


# ---------------------------------------------------------------------------
class TestRag(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        write(self.tmp / ".harness/harness.toml", CFG_TEMPLATE.format(cmd="", timeout=5))
        write(self.tmp / "README.md", "# Judul\n\nAplikasi kasir offline.\n\n## Pembayaran\n\nKembalian dihitung otomatis untuk tunai.\n")
        write(self.tmp / "docs/orders.md", "# Orders\n\nEvery order has status OPEN, SAVED, PAID or VOID.\n")
        write(self.tmp / "docs/printing.md", "# Printing\n\nThermal receipt via ESC/POS bytes.\n")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_tokenize_splits_identifiers(self):
        toks = rag.tokenize("computeTotals pos_order HTTPServer")
        for t in ("computetotals", "compute", "totals", "pos_order", "pos", "order", "http", "server"):
            self.assertIn(t, toks)

    def test_chunking_covers_file_and_splits_on_headings(self):
        text = "# A\n" + ("x " * 100 + "\n") * 3 + "# B\n" + ("y " * 100 + "\n") * 3
        chunks = rag.chunk_file("a.md", text, 400, 100)
        self.assertGreaterEqual(len(chunks), 2)
        self.assertEqual(chunks[0]["start"], 1)
        self.assertEqual(chunks[-1]["end"], len(text.splitlines()))
        self.assertTrue(any(c["section"] == "B" for c in chunks))

    def test_index_query_and_incremental_reuse(self):
        idx = rag.build_index(self.tmp, verbose=False)
        self.assertEqual(len(idx["files"]), 3)
        hits = rag.search(idx, "ESC/POS thermal receipt", k=3)
        self.assertEqual(hits[0]["path"], "docs/printing.md")
        # Tidak ada file berubah -> tidak ada yang dibaca ulang.
        reads: list[str] = []
        real_read = rag.read_text
        rag.read_text = lambda p: (reads.append(p.name), real_read(p))[1]
        try:
            rag.build_index(self.tmp, verbose=False)
            self.assertEqual(reads, [], "file tak berubah seharusnya dipakai ulang dari indeks")
            # Ubah satu file -> hanya file itu yang dibaca & dibangun ulang.
            (self.tmp / "docs/orders.md").write_text("# Orders\n\nNow also REFUNDED status.\n", encoding="utf-8")
            idx2 = rag.build_index(self.tmp, verbose=False)
            self.assertEqual(reads, ["orders.md"])
        finally:
            rag.read_text = real_read
        hits = rag.search(idx2, "refunded", k=1)
        self.assertEqual(hits[0]["path"], "docs/orders.md")
        # Konten lama dari file yang diubah tidak boleh tersisa di indeks.
        self.assertEqual(rag.search(idx2, "OPEN SAVED PAID VOID", k=1), [])

    def test_glossary_bridges_languages(self):
        idx = rag.build_index(self.tmp, verbose=False)
        self.assertEqual(rag.search(idx, "status pesanan", k=1)[0]["path"], "docs/orders.md")
        # tanpa glosarium, kata "pesanan" tidak punya padanan di dokumen Inggris
        no_gloss = rag.search(idx, "pesanan", k=1)
        self.assertEqual(no_gloss, [])
        with_gloss = rag.search(idx, "pesanan", k=1, glossary={"pesanan": ["order"]})
        self.assertEqual(with_gloss[0]["path"], "docs/orders.md")

    def test_indonesian_affix_variants(self):
        self.assertIn("hitung", rag._variants("menghitung"))
        self.assertIn("hitung", rag._variants("dihitung"))
        self.assertIn("bayar", rag._variants("pembayaran"))

    def test_eval_metrics(self):
        idx = rag.build_index(self.tmp, verbose=False)
        qs = [
            {"q": "thermal receipt", "expect": [{"path": "docs/printing.md"}]},
            {"q": "kembalian tunai", "expect": [{"path": "README.md", "contains": "Kembalian"}]},
            {"q": "pertanyaan tanpa jawaban zzz", "expect": [{"path": "docs/printing.md"}]},
        ]
        res = rag.eval_queries(idx, qs)
        self.assertEqual(res["n"], 3)
        self.assertAlmostEqual(res["hit_at_k"], 0.6667, places=3)

    def test_exclude_globs_keeps_meta_docs_out_of_index(self):
        write(self.tmp / "docs/meta.md", "# Meta\n\nPanduan harness tentang thermal receipt.\n")
        cfg = load_config(self.tmp)
        cfg["rag"]["exclude_globs"] = ["docs/meta.md"]
        idx = rag.build_index(self.tmp, verbose=False, cfg=cfg)
        self.assertNotIn("docs/meta.md", idx["files"])
        self.assertIn("docs/printing.md", idx["files"])

    def test_knowledge_folder_is_learned(self):
        write(self.tmp / "docs/knowledge/sharia.md", "# Akad Murabahah\n\nJual beli dengan margin keuntungan yang disepakati.\n")
        idx = rag.build_index(self.tmp, verbose=False)
        hits = rag.search(idx, "akad murabahah margin", k=1, prefix="docs/knowledge/")
        self.assertEqual(hits[0]["path"], "docs/knowledge/sharia.md")


# ---------------------------------------------------------------------------
class TestChecks(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        write(self.tmp / "a/X.kt", "import android.os.Build\nclass X\n")
        write(self.tmp / "a/Y.kt", "class Y\n")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def chk(self, c, changed=None):
        return run_eval.run_check(c, self.tmp, {"changed": changed})

    def test_file_exists(self):
        self.assertEqual(self.chk({"type": "file_exists", "path": "a/X.kt"})["status"], "pass")
        self.assertEqual(self.chk({"type": "file_exists", "path": "nope"})["status"], "fail")

    def test_contains_counts_across_files(self):
        self.assertEqual(self.chk({"type": "file_contains", "glob": "a/*.kt", "pattern": "^class", "min_count": 2})["status"], "pass")
        self.assertEqual(self.chk({"type": "file_contains", "glob": "a/*.kt", "pattern": "^class", "min_count": 3})["status"], "fail")

    def test_not_contains_reports_offender(self):
        r = self.chk({"type": "file_not_contains", "glob": "a/*.kt", "pattern": "^import android\\."})
        self.assertEqual(r["status"], "fail")
        self.assertIn("a/X.kt:1", r["detail"])

    def test_glob_without_match_fails_loudly(self):
        self.assertEqual(self.chk({"type": "file_not_contains", "glob": "zzz/*.kt", "pattern": "x"})["status"], "fail")

    def test_dotall(self):
        write(self.tmp / "b.kt", "foo(\n  a,\n  VOID\n)\n")
        self.assertEqual(self.chk({"type": "file_contains", "path": "b.kt", "pattern": "foo\\([^)]*VOID", "dotall": True})["status"], "pass")

    def test_cmd_pass_fail_skip(self):
        self.assertEqual(self.chk({"type": "cmd", "cmd": "true"})["status"], "pass")
        self.assertEqual(self.chk({"type": "cmd", "cmd": "exit 3"})["status"], "fail")
        self.assertEqual(self.chk({"type": "cmd", "cmd": "true", "requires_env": ["NO_SUCH_ENV_VAR_X"]})["status"], "skip")

    def test_scope_checks(self):
        self.assertEqual(self.chk({"type": "changed_only_within", "globs": ["a/**"]})["status"], "skip")
        self.assertEqual(self.chk({"type": "changed_only_within", "globs": ["a/**"]}, changed=["a/X.kt"])["status"], "pass")
        r = self.chk({"type": "changed_only_within", "globs": ["a/**"]}, changed=["a/X.kt", "README.md"])
        self.assertEqual(r["status"], "fail")
        self.assertEqual(r["category"], "scope_creep")
        self.assertEqual(self.chk({"type": "max_changed_files", "n": 1}, changed=["x", "y"])["status"], "fail")

    def test_trial_status(self):
        self.assertEqual(run_eval.trial_status([{"status": "pass"}]), "pass")
        self.assertEqual(run_eval.trial_status([{"status": "pass"}, {"status": "skip"}]), "pass_unverified")
        self.assertEqual(run_eval.trial_status([{"status": "skip"}, {"status": "fail"}]), "fail")

    def test_task_validation(self):
        self.assertTrue(run_eval.validate_task({"id": "Bad_ID", "checks": [{"type": "file_exists", "path": "x"}]}, "invariants"))
        self.assertTrue(run_eval.validate_task({"id": "ok", "checks": [{"type": "file_contains", "path": "x", "pattern": "("}]}, "invariants"))
        self.assertTrue(run_eval.validate_task({"id": "ok", "checks": [{"type": "file_exists", "path": "x"}]}, "dev"))  # prompt wajib
        self.assertFalse(run_eval.validate_task({"id": "ok", "prompt": "p", "checks": [{"type": "file_exists", "path": "x"}]}, "dev"))


# ---------------------------------------------------------------------------
class TestInvariantsOnRealRepo(unittest.TestCase):
    """Tes mutasi: invariant harus lulus di kode asli DAN gagal bila pelanggarannya disuntikkan."""

    @classmethod
    def setUpClass(cls):
        cls.base = Path(tempfile.mkdtemp())
        shutil.copytree(REAL_ROOT / "composeApp/src", cls.base / "composeApp/src", ignore=shutil.ignore_patterns("composeResources"))
        for f in ("build.gradle.kts", "settings.gradle.kts", "composeApp/build.gradle.kts", "gradle/libs.versions.toml"):
            (cls.base / f).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(REAL_ROOT / f, cls.base / f)
        cls.invs = {i["id"]: i for i in run_eval.load_tasks(REAL_ROOT / ".harness/evals", ["invariants"])}

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.base, ignore_errors=True)

    def status(self, inv_id: str, root: Path) -> str:
        res = run_eval.run_checks(self.invs[inv_id], root, {"changed": None})
        return run_eval.trial_status(res)

    def mutated(self):
        d = Path(tempfile.mkdtemp())
        shutil.copytree(self.base, d / "r")
        self.addCleanup(shutil.rmtree, d, True)
        return d / "r"

    def test_real_code_passes_architecture_invariants(self):
        for inv in ("domain-pure", "common-no-platform-imports", "no-di-framework", "money-is-long", "offline-first", "tests-not-removed"):
            self.assertEqual(self.status(inv, self.base), "pass", inv)

    def test_domain_pure_catches_ui_import(self):
        r = self.mutated()
        p = r / "composeApp/src/commonMain/kotlin/com/pos/app/domain/model/Models.kt"
        p.write_text(p.read_text().replace("package com.pos.app.domain.model\n", "package com.pos.app.domain.model\nimport androidx.compose.runtime.Composable\n", 1))
        self.assertEqual(self.status("domain-pure", r), "fail")

    def test_domain_pure_catches_data_layer_import(self):
        r = self.mutated()
        p = r / "composeApp/src/commonMain/kotlin/com/pos/app/domain/repository/Repositories.kt"
        p.write_text(p.read_text().replace("interface ProductRepository", "import com.pos.app.data.AppContainer\ninterface ProductRepository", 1))
        self.assertEqual(self.status("domain-pure", r), "fail")

    def test_common_no_platform_catches_android_import_but_allows_compose(self):
        r = self.mutated()
        p = r / "composeApp/src/commonMain/kotlin/com/pos/app/util/Time.kt"
        p.write_text("import android.os.Build\n" + p.read_text())
        self.assertEqual(self.status("common-no-platform-imports", r), "fail")
        # androidx.compose.* di commonMain adalah normal dan harus tetap lulus
        self.assertEqual(self.status("common-no-platform-imports", self.base), "pass")

    def test_no_di_catches_koin_in_gradle(self):
        r = self.mutated()
        p = r / "gradle/libs.versions.toml"
        p.write_text(p.read_text() + '\nkoin-core = { module = "io.insert-koin:koin-core", version = "4.0.0" }\n')
        self.assertEqual(self.status("no-di-framework", r), "fail")

    def test_money_is_long_catches_double_price(self):
        r = self.mutated()
        p = r / "composeApp/src/commonMain/kotlin/com/pos/app/domain/model/Models.kt"
        p.write_text(p.read_text().replace("val price: Long", "val price: Double", 1))
        self.assertEqual(self.status("money-is-long", r), "fail")
        # taxPercent adalah persentase, bukan uang -> tidak boleh memicu false positive
        self.assertEqual(self.status("money-is-long", self.base), "pass")

    def test_offline_first_catches_network_lib(self):
        r = self.mutated()
        p = r / "composeApp/src/commonMain/kotlin/com/pos/app/util/Time.kt"
        p.write_text("import io.ktor.client.HttpClient\n" + p.read_text())
        self.assertEqual(self.status("offline-first", r), "fail")

    def test_tests_not_removed_catches_deleted_test(self):
        r = self.mutated()
        (r / "composeApp/src/commonTest/kotlin/com/pos/app/CartTest.kt").unlink()
        self.assertEqual(self.status("tests-not-removed", r), "fail")


# ---------------------------------------------------------------------------
class TestMetrics(unittest.TestCase):
    def test_single_json_result(self):
        out = json.dumps({"type": "result", "subtype": "success", "is_error": False, "num_turns": 7, "total_cost_usd": 0.31, "usage": {"input_tokens": 100, "output_tokens": 40, "cache_read_input_tokens": 60}})
        m = run_eval.extract_metrics(out)
        self.assertEqual((m["steps"], m["cost_usd"], m["tokens"], m["agent_is_error"]), (7, 0.31, 200, False))

    def test_real_claude_output_shape(self):
        """Bentuk output `claude -p --output-format json` yang diamati langsung (v2.1.306)."""
        out = json.dumps({"type": "result", "subtype": "success", "is_error": False, "num_turns": 1, "total_cost_usd": 0.0492875, "result": "ok",
                          "permission_denials": [{"tool_name": "Bash"}, {"tool_name": "Bash"}],
                          "usage": {"input_tokens": 2, "cache_creation_input_tokens": 11541, "cache_read_input_tokens": 29527, "output_tokens": 4}})
        m = run_eval.extract_metrics(out)
        self.assertEqual((m["steps"], m["tokens"], m["permission_denials"]), (1, 41074, 2))

    def test_stream_json_counts_tool_calls_and_result(self):
        lines = [
            {"type": "assistant", "message": {"content": [{"type": "text", "text": "hi"}, {"type": "tool_use", "name": "Read", "input": {"p": 1}}]}},
            {"type": "assistant", "message": {"content": [{"type": "tool_use", "name": "Edit", "input": {"p": 2}}]}},
            {"type": "result", "num_turns": 2, "total_cost_usd": 0.1},
        ]
        m = run_eval.extract_metrics("\n".join(json.dumps(x) for x in lines))
        self.assertEqual(m["steps"], 2)
        self.assertEqual([t[0] for t in m["tool_sequence"]], ["Read", "Edit"])

    def test_custom_trace_file(self):
        with tempfile.TemporaryDirectory() as d:
            tp = Path(d) / "t.jsonl"
            tp.write_text("\n".join(json.dumps({"type": "tool_call", "name": "grep", "args": {"q": i}}) for i in range(3)))
            m = run_eval.extract_metrics("no json here", tp)
            self.assertEqual(m["steps"], 3)
            self.assertIsNone(m["cost_usd"])  # tidak ditebak

    def test_garbage_gives_nones(self):
        m = run_eval.extract_metrics("hello world")
        self.assertIsNone(m["steps"])
        self.assertIsNone(m["tokens"])

    def test_loop_detection(self):
        self.assertTrue(analyze.detect_loop([["a", "1"]] * 4))
        self.assertFalse(analyze.detect_loop([["a", "1"], ["a", "2"]] * 5))


# ---------------------------------------------------------------------------
def summary(**kw):
    s = {
        "run_id": "r", "mode": "agent", "env": {"android_sdk": False}, "agent_cmd_sha": "x",
        "invariants": {"passed": 2, "total": 2, "failed": [], "checks": {"i1": "pass", "i2": "pass"}, "details": {}},
        "splits": {"dev": {"n_tasks": 5, "n_trials": 15, "pass_rate": 0.5, "unverified_rate": 0.0, "tasks": {}}},
        "cost": {"mean_usd": 1.0, "mean_steps": 20.0, "mean_duration_s": 60.0, "total_tokens": 1000},
        "rag": {"n": 14, "hit_at_k": 0.9, "mrr": 0.75, "dev": {}, "heldout": {}, "misses": []},
    }
    for k, v in kw.items():
        s[k] = v
    return s


GATE_CFG = {"max_pass_rate_drop": 0.0, "min_improvement": 0.10, "max_cost_increase_pct": 20, "max_steps_increase_pct": 25, "require_heldout": True, "max_rag_hit_drop": 0.0, "max_rag_mrr_drop": 0.05}
SURFACE = ["AGENTS.md", "docs/knowledge/**"]


class TestGate(unittest.TestCase):
    def d(self, base, cand, changed=None, final=False):
        return gate.decide(base, cand, GATE_CFG, SURFACE, changed, final)

    def with_dev(self, rate, **kw):
        s = summary(**kw)
        s["splits"]["dev"]["pass_rate"] = rate
        return s

    def test_neutral_when_identical(self):
        self.assertEqual(self.d(summary(), summary())["decision"], "NEUTRAL")

    def test_accept_on_real_improvement(self):
        r = self.d(self.with_dev(0.5), self.with_dev(0.7))
        self.assertEqual(r["decision"], "ACCEPT")

    def test_small_gain_below_noise_is_neutral(self):
        self.assertEqual(self.d(self.with_dev(0.5), self.with_dev(0.55))["decision"], "NEUTRAL")

    def test_reject_pass_rate_drop(self):
        r = self.d(self.with_dev(0.5), self.with_dev(0.4))
        self.assertEqual(r["decision"], "REJECT")

    def test_reject_invariant_regression(self):
        cand = summary()
        cand["invariants"]["checks"]["i2"] = "fail"
        cand["invariants"]["failed"] = ["i2"]
        cand["invariants"]["details"] = {"i2": "boom"}
        self.assertEqual(self.d(summary(), cand)["decision"], "REJECT")

    def test_preexisting_invariant_failure_is_warning_not_regression(self):
        base = summary()
        base["invariants"]["checks"]["i2"] = "fail"
        base["invariants"]["failed"] = ["i2"]
        cand = summary()
        cand["invariants"] = json.loads(json.dumps(base["invariants"]))
        r = self.d(base, cand)
        self.assertEqual(r["decision"], "NEUTRAL")
        self.assertTrue(r["warnings"])

    def test_reject_missing_invariant(self):
        cand = summary()
        del cand["invariants"]["checks"]["i2"]
        self.assertEqual(self.d(summary(), cand)["decision"], "REJECT")

    def test_reject_surface_violation(self):
        r = self.d(summary(), summary(), changed=["AGENTS.md", "scripts/harness/gate.py"])
        self.assertEqual(r["decision"], "REJECT")
        self.assertIn("scripts/harness/gate.py", r["violations"][0])

    def test_surface_ok(self):
        self.assertEqual(self.d(summary(), summary(), changed=["AGENTS.md", "docs/knowledge/a.md"])["decision"], "NEUTRAL")

    def test_cost_increase_rejected_without_gain(self):
        cand = summary()
        cand["cost"]["mean_usd"] = 1.5
        self.assertEqual(self.d(summary(), cand)["decision"], "REJECT")

    def test_cost_increase_tolerated_with_gain(self):
        cand = self.with_dev(0.8)
        cand["cost"]["mean_usd"] = 1.5
        r = self.d(self.with_dev(0.5), cand)
        self.assertEqual(r["decision"], "ACCEPT")
        self.assertTrue(any("biaya" in w for w in r["warnings"]))

    def test_rag_drop_rejected(self):
        cand = summary()
        cand["rag"]["hit_at_k"] = 0.8
        self.assertEqual(self.d(summary(), cand)["decision"], "REJECT")

    def test_rag_gain_accepted(self):
        cand = summary()
        cand["rag"]["hit_at_k"] = 1.0
        self.assertEqual(self.d(summary(), cand)["decision"], "ACCEPT")

    def test_env_mismatch_is_error_in_agent_mode(self):
        cand = summary(env={"android_sdk": True})
        self.assertEqual(self.d(summary(), cand)["decision"], "ERROR")

    def test_env_mismatch_ignored_in_check_mode(self):
        # CI punya Android SDK, laptop tidak: mode check tidak memakainya, jadi tetap bisa dibandingkan.
        base, cand = summary(mode="check", splits={}), summary(mode="check", splits={}, env={"android_sdk": True})
        self.assertEqual(self.d(base, cand)["decision"], "NEUTRAL")

    def test_mode_mismatch_is_error(self):
        self.assertEqual(self.d(summary(mode="check"), summary(mode="agent"))["decision"], "ERROR")

    def test_final_requires_heldout(self):
        r = self.d(summary(), summary(), final=True)
        self.assertEqual(r["decision"], "REJECT")
        self.assertIn("held-out", r["violations"][0])

    def test_heldout_drop_rejected_in_final(self):
        ho = {"n_tasks": 2, "n_trials": 6, "pass_rate": 0.5, "unverified_rate": 0.0, "tasks": {}}
        base, cand = summary(), summary()
        base["splits"]["heldout"] = ho
        cand["splits"]["heldout"] = {**ho, "pass_rate": 0.33}
        self.assertEqual(self.d(base, cand, final=True)["decision"], "REJECT")


# ---------------------------------------------------------------------------
FAKE_AGENT = textwrap.dedent(
    """
    import json, os, time
    tid = os.environ["HARNESS_TASK_ID"]
    def w(p, s):
        os.makedirs(os.path.dirname(os.path.abspath(p)), exist_ok=True)
        open(p, "w").write(s)
    if os.path.exists(".harness/evals"): w("LEAKED_EVALS.flag", "agen bisa melihat evals")
    if tid == "make-hello": w("hello.txt", "hi\\n")
    elif tid == "scope-creep": w("hello.txt", "hi\\n"); w("other.txt", "x\\n")
    elif tid == "break-rule": w("hello.txt", "hi\\n"); w("src/bad.kt", "import android.os.Build\\n")
    elif tid == "slow": time.sleep(30)
    elif tid == "hidden-test": w("lib.py", "def add(a, b):\\n    return a + b\\n")
    print(json.dumps({"type": "result", "subtype": "success", "is_error": False, "num_turns": 3,
                      "total_cost_usd": 0.25, "usage": {"input_tokens": 100, "output_tokens": 50}}))
    """
)


class TestPipeline(unittest.TestCase):
    """End-to-end dengan agen palsu pada repo kecil."""

    @classmethod
    def setUpClass(cls):
        cls.repo = Path(tempfile.mkdtemp())
        fake = cls.repo / "fake_agent.py"
        fake.write_text(FAKE_AGENT)
        write(cls.repo / ".harness/harness.toml", CFG_TEMPLATE.format(cmd=f"{sys.executable} {fake}", timeout=3))
        write(cls.repo / "README.md", "# t\n")
        write(cls.repo / "src/ok.kt", "class Ok\n")
        write(cls.repo / ".harness/taxonomy.json", (REAL_ROOT / ".harness/taxonomy.json").read_text())
        ev = cls.repo / ".harness/evals"
        write_json(ev / "invariants/no-android.json", {"id": "no-android", "checks": [{"type": "file_not_contains", "glob": "src/**/*.kt", "pattern": "^import android\\."}]})
        ok_checks = [{"type": "file_contains", "path": "hello.txt", "pattern": "hi"}, {"type": "changed_only_within", "globs": ["hello.txt"]}]
        for tid in ("make-hello", "scope-creep", "break-rule"):
            write_json(ev / f"dev/{tid}.json", {"id": tid, "prompt": "p", "checks": ok_checks})
        write_json(ev / "dev/slow.json", {"id": "slow", "prompt": "p", "checks": [{"type": "file_exists", "path": "hello.txt"}]})
        write_json(ev / "dev/noop.json", {"id": "noop", "prompt": "p", "checks": [{"type": "file_exists", "path": "hello.txt"}]})
        write(ev / "hidden/check_add.py", "from lib import add\nassert add(1, 2) == 3\n")
        write_json(ev / "dev/hidden-test.json", {"id": "hidden-test", "prompt": "p", "inject_files": [{"src": "hidden/check_add.py", "dst": "check_add.py"}], "checks": [{"type": "cmd", "cmd": f"{sys.executable} check_add.py"}]})
        cls.env = {**os.environ, "HARNESS_REPO": str(cls.repo)}
        t0 = time.time()
        cls.proc = subprocess.run([sys.executable, "-I", str(HERE / "run_eval.py"), "--mode", "agent", "--split", "dev", "--no-rag", "--jobs", "3", "--run-id", "e2e"], env=cls.env, capture_output=True, text=True)
        cls.elapsed = time.time() - t0
        cls.run_dir = cls.repo / ".harness/runs/e2e"
        cls.summary = json.loads((cls.run_dir / "summary.json").read_text())

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.repo, ignore_errors=True)

    def trial(self, task, rep=1):
        return json.loads((self.run_dir / task / str(rep) / "result.json").read_text())

    def test_runner_finished_and_timeout_was_enforced(self):
        self.assertEqual(self.proc.returncode, 0, self.proc.stderr + self.proc.stdout)
        self.assertLess(self.elapsed, 25, "timeout 3s seharusnya memotong agen yang tidur 30s")

    def test_statuses(self):
        expect = {"make-hello": "pass", "hidden-test": "pass", "scope-creep": "fail", "break-rule": "fail", "slow": "fail", "noop": "fail"}
        for task, st in expect.items():
            for rep in (1, 2):
                self.assertEqual(self.trial(task, rep)["status"], st, f"{task}#{rep}")

    def test_pass_rate_and_metrics(self):
        dev = self.summary["splits"]["dev"]
        self.assertEqual(dev["n_trials"], 12)
        self.assertAlmostEqual(dev["pass_rate"], 4 / 12, places=3)
        self.assertEqual(self.trial("make-hello")["steps"], 3)
        self.assertEqual(self.trial("make-hello")["cost_usd"], 0.25)
        self.assertEqual(self.summary["cost"]["mean_steps"], 3.0)

    def test_hidden_test_only_runs_after_agent(self):
        r = self.trial("hidden-test")
        self.assertNotIn("check_add.py", r["files_changed"])  # file injeksi bukan perubahan agen
        self.assertEqual(r["files_changed"], ["lib.py"])

    def test_agent_cannot_see_evals(self):
        """Kriteria lulus & tes tersembunyi tidak boleh ada di salinan kerja agen."""
        for task in ("make-hello", "hidden-test", "noop"):
            for rep in (1, 2):
                self.assertNotIn("LEAKED_EVALS.flag", self.trial(task, rep)["files_changed"], f"{task}#{rep}")

    def test_diff_and_original_repo_untouched(self):
        patch = (self.run_dir / "make-hello/1/diff.patch").read_text()
        self.assertIn("hello.txt", patch)
        self.assertFalse((self.repo / "hello.txt").exists(), "agen harus bekerja di salinan, bukan repo asli")

    def test_invariant_guard_runs_after_agent(self):
        r = self.trial("break-rule")
        bad = [c for c in r["checks"] if c["status"] == "fail"]
        self.assertTrue(any(c["name"] == "invariant:no-android" and c["category"] == "rule_violation" for c in bad))
        names = [c["name"] for c in r["checks"]]
        self.assertEqual(len(names), len(set(names)), "satu entri per check/invariant, tanpa duplikat")

    def test_timeout_flagged(self):
        self.assertTrue(self.trial("slow")["timed_out"])

    def test_analyze_classifies_failures(self):
        proc = subprocess.run([sys.executable, "-I", str(HERE / "analyze.py"), "e2e"], env=self.env, capture_output=True, text=True)
        self.assertEqual(proc.returncode, 0, proc.stderr)
        failures = json.loads((self.run_dir / "failures.json").read_text())
        cats = {(f["task"]): f["category"] for f in failures["failures"]}
        self.assertEqual(cats["scope-creep"], "scope_creep")
        self.assertEqual(cats["break-rule"], "rule_violation")
        self.assertEqual(cats["slow"], "timeout_or_loop")
        self.assertEqual(cats["noop"], "missing_change")
        report = (self.run_dir / "report.md").read_text()
        self.assertIn("Taksonomi kegagalan", report)
        self.assertIn("Rekomendasi", report)

    def test_gate_end_to_end_same_run_is_neutral(self):
        proc = subprocess.run([sys.executable, "-I", str(HERE / "gate.py"), "--baseline", "e2e", "--candidate", "e2e"], env=self.env, capture_output=True, text=True)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("NEUTRAL", proc.stdout)

    def test_validate_mode_flags_already_satisfied_task(self):
        repo = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, repo, True)
        write(repo / ".harness/harness.toml", CFG_TEMPLATE.format(cmd="", timeout=3))
        write(repo / "README.md", "# t\n")
        write(repo / "already.txt", "done\n")
        write_json(repo / ".harness/evals/dev/already.json", {"id": "already", "prompt": "p", "checks": [{"type": "file_exists", "path": "already.txt"}]})
        write_json(repo / ".harness/evals/dev/todo.json", {"id": "todo", "prompt": "p", "checks": [{"type": "file_exists", "path": "missing.txt"}]})
        env = {**os.environ, "HARNESS_REPO": str(repo)}
        proc = subprocess.run([sys.executable, "-I", str(HERE / "run_eval.py"), "--mode", "validate", "--no-rag", "--run-id", "v"], env=env, capture_output=True, text=True)
        self.assertEqual(proc.returncode, 1)
        self.assertIn("SUDAH LULUS", proc.stdout)
        self.assertIn("OK   dev/todo", proc.stdout)


class TestGateWithGit(unittest.TestCase):
    """Cek surface lewat git diff sungguhan (--base-ref/--head-ref)."""

    def git(self, *a):
        return subprocess.run(["git", "-c", "user.email=t@t", "-c", "user.name=t", "-C", str(self.repo), *a], capture_output=True, text=True, check=True)

    def setUp(self):
        self.repo = Path(tempfile.mkdtemp())
        write(self.repo / ".harness/harness.toml", CFG_TEMPLATE.format(cmd="", timeout=3))
        write(self.repo / "AGENTS.md", "v1\n")
        write(self.repo / "scripts/x.py", "a\n")
        write_json(self.repo / "base.json", summary())
        self.git("init", "-q", "-b", "main")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "base")
        self.env = {**os.environ, "HARNESS_REPO": str(self.repo)}

    def tearDown(self):
        shutil.rmtree(self.repo, ignore_errors=True)

    def run_gate(self, head):
        return subprocess.run([sys.executable, "-I", str(HERE / "gate.py"), "--baseline", str(self.repo / "base.json"), "--candidate", str(self.repo / "base.json"), "--base-ref", "main", "--head-ref", head], env=self.env, capture_output=True, text=True)

    def test_only_surface_changed_is_ok(self):
        self.git("checkout", "-q", "-b", "good")
        write(self.repo / "AGENTS.md", "v2\n")
        self.git("commit", "-q", "-am", "tune")
        p = self.run_gate("good")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)

    def test_touching_scripts_is_rejected(self):
        self.git("checkout", "-q", "-b", "bad")
        write(self.repo / "AGENTS.md", "v2\n")
        write(self.repo / "scripts/x.py", "tampered\n")
        self.git("commit", "-q", "-am", "tamper")
        p = self.run_gate("bad")
        self.assertEqual(p.returncode, 1, p.stdout + p.stderr)
        self.assertIn("scripts/x.py", p.stdout)


if __name__ == "__main__":
    unittest.main()
