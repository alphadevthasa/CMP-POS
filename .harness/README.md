# AI harness untuk CMP-POS

Siklus tertutup untuk mengoptimasi "harness" di sekeliling model (instruksi `AGENTS.md`, konteks/RAG,
batasan, verifikasi): **ukur → diagnosis → ubah satu tuas → uji → gate → ulangi**.

```
Eval (invariants + task + RAG) → Baseline → Trace → Analisis kegagalan
        ↑                                                   ↓
   Monitoring ← Merge (manusia) ← Gate ← Kandidat ← Hipotesis (satu tuas)
```

## Isi

| Lokasi | Fungsi |
|---|---|
| `AGENTS.md`, `CLAUDE.md` | Harness yang dioptimasi: instruksi untuk agen |
| `.harness/harness.toml` | Konfigurasi: surface, agen, gate, RAG (+ glosarium Indonesia→Inggris) |
| `.harness/evals/invariants/` | Guardrail arsitektur (cepat, tanpa agen). Juga dijalankan setelah tiap percobaan agen sebagai guard regresi |
| `.harness/evals/dev/` | Task untuk iterasi (prompt + checks) |
| `.harness/evals/heldout/` | Task untuk keputusan akhir. Jangan disetel ke set ini |
| `.harness/evals/hidden/` | Tes tersembunyi, disalin ke repo SETELAH agen selesai |
| `.harness/evals/rag_queries.json` | Eval retrieval (hit@k, MRR) |
| `.harness/taxonomy.json` | Kategori kegagalan → tuas yang disarankan |
| `.harness/baseline.json` | Baseline mode `check` (dipakai CI) |
| `.harness/experiments/` | Catatan eksperimen (satu file per eksperimen) |
| `scripts/harness/` | `rag.py`, `run_eval.py`, `analyze.py`, `gate.py`, dan tesnya |
| `.claude/commands/` | `/harness-optimize` (satu siklus), `/rag-learn` (indeks dokumen baru) |

Yang boleh diubah agen-proposer ("surface"): `AGENTS.md`, `CLAUDE.md`, `docs/knowledge/**`,
`.harness/experiments/*.md`. Eval, skrip, konfigurasi, dan prosedur optimasi sengaja di luar surface
supaya kandidat tidak bisa mengubah alat ukurnya sendiri; `gate.py` menolak diff yang menyentuhnya.

## Prasyarat

Python >= 3.11 (diuji di 3.13; hanya stdlib, tanpa `pip install`; PDF opsional lewat `pypdf`) dan git.
Mode agent memakai CLI Claude Code (`claude`). Task bertanda `cmd: ./gradlew ...` butuh JDK 17 + Android SDK.

## Perintah sehari-hari

```bash
# Tes harness itu sendiri
python3 -I -m unittest discover -s scripts/harness

# Guardrail + RAG (cepat, tanpa agen) — sama dengan CI
python3 -I scripts/harness/run_eval.py --mode check

# Pastikan tiap task benar-benar gagal di repo awal (tidak "sudah lulus")
python3 -I scripts/harness/run_eval.py --mode validate --split all --no-rag

# RAG
python3 scripts/harness/rag.py index
python3 scripts/harness/rag.py query "cara hitung kembalian tunai"
python3 scripts/harness/rag.py eval -v
```

## CI

`.github/workflows/harness.yml` menjalankan: tes harness, validasi task, guardrail + eval RAG, dan gate terhadap
`.harness/baseline.json`. Tes unit Kotlin/Gradle tidak dijalankan di CI ini.

Branch yang namanya diawali `harness/` dianggap eksperimen optimasi: CI menolaknya bila diff menyentuh file di
luar surface. Untuk mengubah alat ukurnya sendiri (eval, skrip, konfigurasi) pakai nama branch lain, mis. `chore/...`.
Setelah perubahan sah pada korpus RAG (mis. README diubah) atau eval, perbarui baseline:
`python3 scripts/harness/run_eval.py --mode check --save-baseline` lalu commit `.harness/baseline.json`.

## Mode agent (butuh agen + biaya)

1. Isi `agent.cmd` di `harness.toml`. Contoh Claude Code:
   `claude -p "$(cat {prompt_file})" --output-format json --permission-mode acceptEdits --max-budget-usd 2`
2. Baseline: `python3 scripts/harness/run_eval.py --mode agent --split dev --repeats 3 --save-baseline`
3. Di Claude Code, jalankan `/harness-optimize` untuk satu siklus (hipotesis → kandidat di git worktree → gate).
4. Keputusan akhir: held-out sekali, lalu `gate.py ... --final`.

Tiap percobaan agen berjalan di salinan repo (repo asli tidak disentuh) dan menyimpan `prompt.txt`,
`agent_stdout.txt`, `diff.patch`, dan `result.json` di `.harness/runs/<run-id>/<task>/<ulangan>/`.
Task dengan `cmd: ./gradlew ...` butuh `ANDROID_HOME`; tanpa itu hasilnya `pass_unverified`, bukan `pass`.
Gate menolak membandingkan run agent dari lingkungan berbeda.

## Metrik dari output agen

`run_eval.py` membaca langkah/biaya/token dari output JSON agen (`type: result` dengan `num_turns`,
`total_cost_usd`, `usage`) atau dari baris stream-json. Field yang tidak ada dibiarkan kosong, tidak ditebak.
Agen lain: tulis JSONL ke `$HARNESS_TRACE` dengan baris `{"type":"tool_call","name":...,"args":...}`.

## Batasan yang perlu diketahui

- RAG memakai BM25 leksikal + glosarium istilah, bukan embedding. Cukup untuk repo sekecil ini, tetapi
  pertanyaan yang kosakatanya sangat berbeda dari dokumen bisa meleset (lihat `rag.py eval`). Tambahkan istilah
  ke `[rag.glossary]` atau tulis dokumen di `docs/knowledge/`.
- Set eval awal kecil (5 dev, 2 held-out, 14 query RAG) dan sebagian check bersifat struktural (`"loose": true`).
  Anggap sebagai benih: tambahkan task dari kegagalan nyata, dan periksa sampel hasil secara manual.
- Perbaikan di bawah `min_improvement` (default 10 poin) dianggap noise. Naikkan `repeats` untuk task yang flaky.
