# AGENTS.md — panduan untuk agen AI di repo CMP-POS

POS Multiplatform (Kotlin Multiplatform + Compose Multiplatform) untuk Android dan iPad.
Offline-first (SQLDelight/SQLite). Teks UI dan dokumen berbahasa Indonesia; identifier kode berbahasa Inggris.

## Peta kode (package `com.pos.app`, di `composeApp/src/`)

- `commonMain/.../domain/model`, `domain/repository`: Kotlin murni. Tanpa UI, DB, atau API platform.
- `commonMain/.../data`: implementasi repository (SQLDelight) dan `AppContainer` (wiring manual).
- `commonMain/.../ui`: Compose. Satu state-holder, `PosStore`.
- `commonMain/.../print`: `ReceiptFormatter` (murni) dan interface `ReceiptPrinter`; implementasinya di `androidMain` dan `iosMain`.
- `commonMain/.../util`: `Money` (Rupiah), `Time`, `Initials`.
- Skema DB: `commonMain/sqldelight/com/pos/app/db/Pos.sq`.
- Tes: `commonTest/` (`kotlin.test`).

## Aturan keras (dicek otomatis oleh `.harness/evals/invariants/`)

1. `domain/` tidak meng-import `ui`, `data`, `print`, SQLDelight, atau API platform.
2. `commonMain` tidak meng-import `android.*`, `platform.*`, `java.*`. (`androidx.compose.*` boleh.)
3. Tanpa framework DI (Koin/Hilt/Dagger). Wiring lewat `AppContainer`.
4. Uang adalah `Long` (rupiah). Jangan `Double`/`Float` untuk field uang. (`taxPercent` adalah persentase, bukan uang.)
5. Offline-first: jangan tambah pustaka jaringan.
6. Jangan menghapus atau melemahkan tes agar build hijau. Sekarang ada 13 `@Test`; jumlahnya tidak boleh turun.

## Cara bekerja

1. **Cari konteks dulu.** Sebelum mengubah kode atau dokumen, jalankan
   `python3 scripts/harness/rag.py query "<pertanyaan>"`. Query berbahasa Indonesia atau Inggris sama-sama bisa.
   Hasilnya hanya petunjuk: buka file aslinya dan baca sebelum mengedit.
2. **Perubahan sekecil mungkin.** Sentuh hanya file yang perlu. Jangan refactor yang tidak diminta.
3. **Kode adalah sumber kebenaran.** Jika dokumen berbeda dari kode, ubah dokumen, kecuali diminta lain.
4. **Verifikasi.** Jalankan `./gradlew :composeApp:testDebugUnitTest` (butuh JDK 17 dan Android SDK).
   Jika tidak bisa dijalankan, katakan terus terang. Jangan menyatakan tes lulus tanpa menjalankannya.
5. **Lapor singkat.** File yang berubah, apa yang diverifikasi, apa yang tidak.

## Harness dan eval

- Eval: `.harness/evals/`. Guardrail cepat: `python3 scripts/harness/run_eval.py --mode check`.
- Optimasi harness: perintah `/harness-optimize`. Agen hanya boleh mengubah file "surface" di
  `.harness/harness.toml` (AGENTS.md, CLAUDE.md, `docs/knowledge/**`, `.harness/experiments/*.md`).
  Jangan mengubah eval, skrip, atau konfigurasi gate.
- Dokumen baru untuk dipelajari: taruh di `docs/knowledge/`, lalu jalankan `/rag-learn`.
