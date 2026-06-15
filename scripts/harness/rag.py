#!/usr/bin/env python3
"""RAG ringan untuk repo: indeks BM25 atas dokumen + kode, tanpa dependensi eksternal.

Perintah:
  rag.py index            bangun / perbarui indeks (inkremental per file)
  rag.py query "teks"     cari potongan paling relevan (sitasi path:baris)
  rag.py eval             ukur kualitas retrieval (hit@k, MRR) dari rag_queries.json
  rag.py stats            ringkasan isi indeks

Dokumen yang ingin "dipelajari" cukup ditaruh di docs/knowledge/ lalu jalankan `index`.
PDF didukung bila paket opsional `pypdf` terpasang.
"""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
import time
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import ROOT, iter_files, load_config, match_any, read_json, sha1_bytes, write_json  # noqa: E402

INDEX_VERSION = 2
BM25_K1 = 1.5
BM25_B = 0.75

STOPWORDS = set(
    """
    a an the and or of to in on for with is are was be as at by it this that from
    yang dan di ke dari untuk dengan pada adalah ini itu atau juga akan dalam saat
    apa saja bagaimana cara kah lah nya
    """.split()
)

_WORD = re.compile(r"[A-Za-z0-9_]+")
_CAMEL = re.compile(r"[A-Z]+(?=[A-Z][a-z])|[A-Z]?[a-z]+|[A-Z]+|\d+")


def tokenize(text: str) -> list[str]:
    """Token kata + pecahan camelCase/snake_case, huruf kecil, tanpa stopword."""
    tokens: list[str] = []
    for word in _WORD.findall(text):
        parts = [p for chunk in word.split("_") for p in _CAMEL.findall(chunk)]
        low = word.lower()
        if len(parts) > 1:
            tokens.append(low)  # simpan juga bentuk utuh (mis. "computeTotals")
        tokens.extend(p.lower() for p in parts)
    return [t for t in tokens if len(t) >= 2 and t not in STOPWORDS]


# ----------------------------------------------------------------------------
# Membaca & memotong file
# ----------------------------------------------------------------------------

def read_text(path: Path) -> str | None:
    ext = path.suffix.lower()
    if ext == ".pdf":
        try:
            from pypdf import PdfReader  # type: ignore
        except ImportError:
            print(f"  ! lewati {path.name}: paket 'pypdf' belum terpasang", file=sys.stderr)
            return None
        try:
            reader = PdfReader(str(path))
            return "\n\n".join((page.extract_text() or "") for page in reader.pages)
        except Exception as exc:  # PDF rusak / terenkripsi
            print(f"  ! gagal membaca {path.name}: {exc}", file=sys.stderr)
            return None
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return None


_MD_HEAD = re.compile(r"^#{1,6}\s+(.*)")
_KT_DECL = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:(?:public|private|internal|data|sealed|enum|abstract|open|expect|actual|suspend)\s+)*"
    r"(?:class|object|interface|fun|val|typealias)\b"
)
_SQ_DECL = re.compile(r"^(?:CREATE\s+TABLE|[A-Za-z_][A-Za-z0-9_]*:)\s*", re.I)


def chunk_file(rel: str, text: str, max_chars: int, min_chars: int) -> list[dict]:
    """Potong teks jadi chunk dengan nomor baris. Batas alami: heading (md), deklarasi (kt/sq)."""
    ext = Path(rel).suffix.lower()
    lines = text.splitlines()
    chunks: list[dict] = []
    buf: list[str] = []
    start = 1
    section = ""
    size = 0

    def flush(end_line: int):
        nonlocal buf, start, size
        body = "\n".join(buf).strip("\n")
        if body.strip():
            chunks.append({"start": start, "end": end_line, "section": section, "text": body})
        buf = []
        size = 0

    for i, line in enumerate(lines, start=1):
        boundary = False
        if ext in (".md", ".rst", ".txt"):
            m = _MD_HEAD.match(line)
            if m:
                boundary = True
                new_section = m.group(1).strip()
            else:
                new_section = section
        elif ext in (".kt", ".kts"):
            boundary = bool(_KT_DECL.match(line))
            new_section = section
            if boundary:
                new_section = line.strip()[:80]
        elif ext == ".sq":
            boundary = bool(_SQ_DECL.match(line))
            new_section = line.strip()[:60] if boundary else section
        else:
            boundary = False
            new_section = section

        if buf and ((boundary and size >= min_chars) or size + len(line) + 1 > max_chars):
            flush(i - 1)
            start = i
        if boundary or not buf:
            section = new_section if boundary else section
        if not buf:
            start = i
        buf.append(line)
        size += len(line) + 1
    if buf:
        flush(len(lines))

    # Pecah chunk yang tetap terlalu besar (satu baris sangat panjang).
    final: list[dict] = []
    for c in chunks:
        if len(c["text"]) <= max_chars * 1.5:
            final.append(c)
            continue
        text_c = c["text"]
        for off in range(0, len(text_c), max_chars):
            final.append({**c, "text": text_c[off : off + max_chars]})
    return final


def kind_of(rel: str) -> str:
    return "code" if Path(rel).suffix.lower() in (".kt", ".kts", ".sq", ".py") else "docs"


def make_record(rel: str, c: dict) -> dict:
    # Path dan section ikut diindeks agar query nama file/kelas ikut cocok.
    indexed = f"{rel}\n{c['section']}\n{c['text']}"
    toks = tokenize(indexed)
    return {
        "path": rel,
        "kind": kind_of(rel),
        "start": c["start"],
        "end": c["end"],
        "section": c["section"],
        "text": c["text"],
        "tf": dict(Counter(toks)),
        "len": len(toks),
    }


# ----------------------------------------------------------------------------
# Indeks
# ----------------------------------------------------------------------------

def index_path(cfg: dict, root: Path) -> Path:
    return root / cfg["rag"]["index_path"]


def build_index(root: Path = ROOT, verbose: bool = True, cfg: dict | None = None) -> dict:
    """`cfg` opsional: pakai konfigurasi tepercaya milik pemanggil (mis. saat mengevaluasi repo kandidat)."""
    cfg = cfg or load_config(root)
    rcfg = cfg["rag"]
    exts = {e.lower() for e in rcfg["extensions"]}
    ipath = index_path(cfg, root)

    old_files: dict = {}
    old_chunks_by_file: dict[str, list[dict]] = {}
    params = {"max": rcfg["chunk_chars"], "min": rcfg["min_chunk_chars"], "v": INDEX_VERSION}
    if ipath.exists():
        try:
            old = read_json(ipath)
            if old.get("params") == params:
                old_files = old.get("files", {})
                for rec in old.get("chunks", []):
                    old_chunks_by_file.setdefault(rec["path"], []).append(rec)
        except (json.JSONDecodeError, KeyError):
            pass

    files: dict = {}
    chunks: list[dict] = []
    reused = rebuilt = skipped = 0
    skip_globs = rcfg.get("exclude_globs", [])
    for rel in iter_files(root, rcfg["include"], rcfg.get("exclude_dirs")):
        p = root / rel
        if p.suffix.lower() not in exts or match_any(rel, skip_globs):
            continue
        digest = sha1_bytes(p.read_bytes())
        if old_files.get(rel, {}).get("sha1") == digest and rel in old_chunks_by_file:
            file_chunks = old_chunks_by_file[rel]
            reused += 1
        else:
            text = read_text(p)
            if text is None:
                skipped += 1
                continue
            file_chunks = [
                make_record(rel, c)
                for c in chunk_file(rel, text, rcfg["chunk_chars"], rcfg["min_chunk_chars"])
            ]
            rebuilt += 1
        files[rel] = {"sha1": digest, "chunks": len(file_chunks)}
        chunks.extend(file_chunks)

    df: Counter = Counter()
    for rec in chunks:
        df.update(rec["tf"].keys())
    avgdl = (sum(r["len"] for r in chunks) / len(chunks)) if chunks else 0.0
    index = {
        "version": INDEX_VERSION,
        "params": params,
        "built_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "files": files,
        "n_chunks": len(chunks),
        "avgdl": avgdl,
        "df": dict(df),
        "chunks": chunks,
    }
    write_json(ipath, index)
    if verbose:
        print(
            f"Indeks: {len(files)} file, {len(chunks)} chunk "
            f"(baru/ubah: {rebuilt}, dipakai ulang: {reused}, dilewati: {skipped}) -> "
            f"{ipath.relative_to(root)}"
        )
    return index


def load_index(root: Path = ROOT) -> dict:
    cfg = load_config(root)
    ipath = index_path(cfg, root)
    if not ipath.exists():
        return build_index(root, verbose=False)
    return read_json(ipath)


# ----------------------------------------------------------------------------
# Pencarian
# ----------------------------------------------------------------------------

_PREFIXES = ("meng", "meny", "men", "mem", "me", "peng", "peny", "pen", "pem", "pe", "ber", "ter", "di", "ke", "se")
_SUFFIXES = ("kan", "nya", "lah", "kah", "an", "i")
EXPANSION_WEIGHT = 0.7


def _variants(tok: str) -> list[str]:
    """Bentuk dasar kasar kata Indonesia (hanya untuk lookup glosarium, bukan untuk indeks)."""
    out = [tok]
    for pre in _PREFIXES:
        if tok.startswith(pre) and len(tok) - len(pre) >= 3:
            stem = tok[len(pre):]
            out.append(stem)
            for suf in _SUFFIXES:
                if stem.endswith(suf) and len(stem) - len(suf) >= 3:
                    out.append(stem[: -len(suf)])
    for suf in _SUFFIXES:
        if tok.endswith(suf) and len(tok) - len(suf) >= 3:
            out.append(tok[: -len(suf)])
    return list(dict.fromkeys(out))


def query_weights(query: str, glossary: dict | None = None) -> dict[str, float]:
    """Token query berbobot. Istilah Inggris dari glosarium ikut dicari dengan bobot lebih kecil."""
    weights: dict[str, float] = {t: 1.0 for t in tokenize(query)}
    if glossary:
        for tok in list(weights):
            for var in _variants(tok):
                for term in glossary.get(var, []):
                    for t in tokenize(term):
                        weights[t] = max(weights.get(t, 0.0), EXPANSION_WEIGHT)
    return weights


def search(
    index: dict,
    query: str,
    k: int = 5,
    kind: str | None = None,
    prefix: str | None = None,
    glossary: dict | None = None,
) -> list[dict]:
    q_weights = query_weights(query, glossary)
    if not q_weights or not index["chunks"]:
        return []
    n = index["n_chunks"]
    df = index["df"]
    avgdl = index["avgdl"] or 1.0
    idf = {t: math.log(1 + (n - df.get(t, 0) + 0.5) / (df.get(t, 0) + 0.5)) for t in q_weights}
    scored = []
    for rec in index["chunks"]:
        if kind and rec["kind"] != kind:
            continue
        if prefix and not rec["path"].startswith(prefix):
            continue
        tf = rec["tf"]
        dl = rec["len"] or 1
        score = 0.0
        for t, w in q_weights.items():
            f = tf.get(t)
            if not f:
                continue
            score += w * idf[t] * (f * (BM25_K1 + 1)) / (f + BM25_K1 * (1 - BM25_B + BM25_B * dl / avgdl))
        if score > 0:
            scored.append((score, rec))
    scored.sort(key=lambda x: -x[0])
    return [
        {
            "score": round(s, 4),
            "path": r["path"],
            "start": r["start"],
            "end": r["end"],
            "section": r["section"],
            "kind": r["kind"],
            "text": r["text"],
        }
        for s, r in scored[:k]
    ]


def format_hits(hits: list[dict], max_chars: int = 600) -> str:
    if not hits:
        return "(tidak ada hasil)"
    out = []
    for i, h in enumerate(hits, 1):
        snippet = h["text"].strip()
        if len(snippet) > max_chars:
            snippet = snippet[:max_chars].rstrip() + " …"
        head = f"[{i}] {h['path']}:{h['start']}-{h['end']}  (skor {h['score']})"
        if h["section"]:
            head += f"  — {h['section']}"
        out.append(head + "\n" + "\n".join("    " + ln for ln in snippet.splitlines()))
    return "\n\n".join(out)


# ----------------------------------------------------------------------------
# Evaluasi retrieval
# ----------------------------------------------------------------------------

def eval_queries(
    index: dict,
    queries: list[dict],
    default_k: int = 5,
    split: str | None = None,
    glossary: dict | None = None,
) -> dict:
    """Satu query dihitung hit bila ada hasil top-k yang cocok dengan SALAH SATU `expect`."""
    results = []
    for q in queries:
        if split and q.get("split", "dev") != split:
            continue
        k = q.get("k", default_k)
        hits = search(index, q["q"], k=k, glossary=glossary)
        rank = None
        for i, h in enumerate(hits, 1):
            for exp in q["expect"]:
                if h["path"] != exp["path"] and not h["path"].endswith(exp["path"]):
                    continue
                needle = exp.get("contains")
                if needle and needle.lower() not in h["text"].lower():
                    continue
                rank = i
                break
            if rank:
                break
        results.append(
            {
                "q": q["q"],
                "split": q.get("split", "dev"),
                "hit": rank is not None,
                "rank": rank,
                "top": [f"{h['path']}:{h['start']}" for h in hits[:3]],
            }
        )
    n = len(results)
    hit = sum(1 for r in results if r["hit"])
    mrr = sum((1 / r["rank"]) for r in results if r["rank"]) / n if n else 0.0
    return {
        "n": n,
        "hit_at_k": round(hit / n, 4) if n else None,
        "mrr": round(mrr, 4) if n else None,
        "results": results,
    }


# ----------------------------------------------------------------------------
# CLI
# ----------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", default=str(ROOT), help="root repo (default: repo ini)")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("index", help="bangun / perbarui indeks")

    q = sub.add_parser("query", help="cari potongan relevan")
    q.add_argument("text")
    q.add_argument("-k", type=int, default=None)
    q.add_argument("--kind", choices=["docs", "code"])
    q.add_argument("--path-prefix", default=None, help="mis. docs/knowledge/")
    q.add_argument("--json", action="store_true")
    q.add_argument("--max-chars", type=int, default=600)

    e = sub.add_parser("eval", help="ukur hit@k dan MRR")
    e.add_argument("--queries", default=".harness/evals/rag_queries.json")
    e.add_argument("--split", choices=["dev", "heldout"], default=None)
    e.add_argument("--min-hit", type=float, default=None, help="exit 1 bila hit@k di bawah nilai ini")
    e.add_argument("--out", default=None, help="tulis hasil JSON ke file")
    e.add_argument("--verbose", "-v", action="store_true")

    sub.add_parser("stats", help="ringkasan indeks")

    args = ap.parse_args(argv)
    root = Path(args.repo).resolve()
    cfg = load_config(root)

    if args.cmd == "index":
        build_index(root)
        return 0

    index = load_index(root)
    if args.cmd == "query":
        k = args.k or cfg["rag"]["top_k"]
        hits = search(
            index, args.text, k=k, kind=args.kind, prefix=args.path_prefix,
            glossary=cfg["rag"].get("glossary"),
        )
        if args.json:
            print(json.dumps(hits, ensure_ascii=False, indent=2))
        else:
            print(format_hits(hits, args.max_chars))
        return 0

    if args.cmd == "stats":
        by_kind = Counter(r["kind"] for r in index["chunks"])
        print(f"file: {len(index['files'])}  chunk: {index['n_chunks']}  dibangun: {index['built_at']}")
        for kd, c in sorted(by_kind.items()):
            print(f"  {kd}: {c} chunk")
        top = sorted(index["files"].items(), key=lambda kv: -kv[1]["chunks"])[:5]
        for rel, meta in top:
            print(f"  {meta['chunks']:>4}  {rel}")
        return 0

    if args.cmd == "eval":
        qpath = root / args.queries
        if not qpath.exists():
            print(f"File query tidak ada: {qpath}", file=sys.stderr)
            return 2
        res = eval_queries(
            index, read_json(qpath), default_k=cfg["rag"]["top_k"], split=args.split,
            glossary=cfg["rag"].get("glossary"),
        )
        print(f"RAG eval: n={res['n']}  hit@k={res['hit_at_k']}  MRR={res['mrr']}")
        if args.verbose or any(not r["hit"] for r in res["results"]):
            for r in res["results"]:
                mark = "OK  " if r["hit"] else "MISS"
                print(f"  {mark} rank={r['rank']}  [{r['split']}] {r['q']}")
                if not r["hit"]:
                    print(f"       top: {', '.join(r['top'])}")
        if args.out:
            write_json(root / args.out, res)
        if args.min_hit is not None and (res["hit_at_k"] or 0) < args.min_hit:
            return 1
        return 0
    return 2


if __name__ == "__main__":
    sys.exit(main())
