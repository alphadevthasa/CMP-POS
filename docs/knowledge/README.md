# Pengetahuan tambahan untuk RAG

Taruh dokumen yang ingin "dipelajari" agen di folder ini: catatan domain, aturan bisnis,
spesifikasi integrasi, hasil riset, dan sebagainya.

- Format yang dibaca: `.md`, `.txt`, `.rst`, `.pdf` (PDF butuh `pip install pypdf`).
  Format lain (mis. `.docx`) konversi dulu ke Markdown.
- Satu topik per file, judul bagian (`#`, `##`) yang jelas. Potongan dokumen dibuat per bagian,
  jadi judul yang baik membuat hasil pencarian lebih tepat.
- Setelah menambah atau mengubah dokumen: `python3 scripts/harness/rag.py index`
  (hanya file yang berubah yang diproses ulang), atau jalankan `/rag-learn` di Claude Code.
- Mencari: `python3 scripts/harness/rag.py query "pertanyaan Anda" --path-prefix docs/knowledge/`
- Jangan taruh data rahasia (kredensial, data pelanggan, dokumen yang tidak boleh masuk repo).
  Folder ini ikut ter-commit.
