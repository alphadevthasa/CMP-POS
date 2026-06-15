"""Utilitas bersama untuk skrip harness (hanya stdlib, Python >= 3.11)."""
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
from pathlib import Path

try:
    import tomllib
except ModuleNotFoundError:  # Python < 3.11
    sys.exit(
        f"Harness ini butuh Python >= 3.11 (terdeteksi {sys.version.split()[0]}). "
        "Di macOS: `brew install python` lalu jalankan dengan `python3.13 ...`."
    )

ROOT = Path(os.environ.get("HARNESS_REPO", Path(__file__).resolve().parents[2])).resolve()
HARNESS_DIR = ROOT / ".harness"


def load_config(root: Path | None = None) -> dict:
    base = (root or ROOT) / ".harness" / "harness.toml"
    with open(base, "rb") as f:
        return tomllib.load(f)


def read_json(path: Path | str):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def write_json(path: Path | str, obj) -> None:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False, sort_keys=False)
        f.write("\n")


def sha1_bytes(data: bytes) -> str:
    return hashlib.sha1(data).hexdigest()


def glob_to_regex(pattern: str) -> re.Pattern:
    """Glob gaya gitignore: `**` lintas direktori, `*` dalam satu segmen."""
    pat = pattern.replace("\\", "/")
    out = []
    i = 0
    while i < len(pat):
        c = pat[i]
        if c == "*":
            if pat[i : i + 2] == "**":
                i += 2
                if pat[i : i + 1] == "/":
                    i += 1
                    out.append("(?:.*/)?")
                else:
                    out.append(".*")
                continue
            out.append("[^/]*")
        elif c == "?":
            out.append("[^/]")
        else:
            out.append(re.escape(c))
        i += 1
    return re.compile("^" + "".join(out) + "$")


def match_any(path: str, patterns: list[str]) -> bool:
    path = path.replace("\\", "/")
    return any(glob_to_regex(p).match(path) for p in patterns)


def iter_files(root: Path, patterns: list[str], exclude_dirs: list[str] | None = None):
    """Yield path relatif (posix) semua file di root yang cocok dengan salah satu glob."""
    exclude = set(exclude_dirs or [])
    regs = [glob_to_regex(p) for p in patterns]
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(d for d in dirnames if d not in exclude)
        for name in sorted(filenames):
            rel = (Path(dirpath) / name).relative_to(root).as_posix()
            if any(r.match(rel) for r in regs):
                yield rel


def fail(msg: str, code: int = 2):
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(code)


__all__ = [
    "ROOT", "HARNESS_DIR", "load_config", "read_json", "write_json", "sha1_bytes",
    "glob_to_regex", "match_any", "iter_files", "fail",
]
