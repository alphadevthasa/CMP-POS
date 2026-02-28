# POS Multiplatform

Aplikasi **Point of Sale (Kasir)** lintas platform — **Android & iPad** — dibangun dengan
**Kotlin Multiplatform + Compose Multiplatform**. Offline-first (SQLite lokal), lengkap dari
pemilihan produk, keranjang, penyimpanan pesanan, cetak bill, pembayaran (Tunai/Kartu/QRIS),
sampai cetak struk & riwayat transaksi.

https://github.com/user-attachments/assets/ea683689-4e95-47d8-aaa8-049d1937a6a1

> _Demo: pilih 2 produk → keranjang terisi → **Bayar sekarang** → popup pembayaran **Tunai** →
> masukkan uang diterima → kembalian dihitung otomatis._

---

## Daftar Isi

- [Tentang](#tentang)
- [Fitur](#fitur)
- [Tech Stack](#tech-stack)
- [Arsitektur & Struktur Proyek](#arsitektur--struktur-proyek)
- [Prasyarat](#prasyarat)
- [Menjalankan](#menjalankan)
- [Alur Penggunaan](#alur-penggunaan)
- [Printing (Struk & Bill)](#printing-struk--bill)
- [Format Mata Uang](#format-mata-uang)
- [Pengaturan](#pengaturan)
- [Testing](#testing)
- [Kredit Gambar Produk](#kredit-gambar-produk)
- [License](#license)

---

## Tentang

POS Multiplatform adalah aplikasi kasir untuk tablet/HP yang dirancang untuk:

- **Cepat & sentuh-dulu** — grid produk besar, tombol `+` untuk menambah ke keranjang.
- **Offline** — semua data (produk, pesanan, transaksi, pengaturan) disimpan lokal di SQLite.
  Tidak butuh server/internet untuk operasional.
- **Satu codebase** — UI & logika di-share untuk Android dan iOS (iPad), jadi tampilan dan
  perilaku konsisten.

Cocok untuk warung, kafe, restoran kecil, atau kasir pop-up yang butuh aplikasi ringan di tablet.

---

## Fitur

### Katalog Produk
- Grid produk dengan **foto**, **nama**, **harga**, dan tombol **+** untuk tambah ke keranjang.
- Filter berdasarkan **kategori** (Semua, Kopi, Makanan, Minuman, Snack).
- **Pencarian** produk secara langsung (ketik nama di "Cari menu...").
- Jumlah produk tersedia ditampilkan otomatis.

### Keranjang & Order
- Tambah / kurang jumlah (`−` `+`) / hapus item.
- Kolom **Diskon** (dengan format Rupiah) yang mengurangi total.
- Ringkasan **Subtotal, Diskon, Pajak, Total bayar**.
- **Simpan** — menyimpan pesanan (status *SAVED*) lalu mengosongkan keranjang. Pesanan pindah ke
  **Riwayat** dan siap dilanjutkan/dicetak/dibayar kapan saja.
- **Lihat bill** — pratinjau *bill* (tagihan sebelum bayar), siap dicetak.
- **Bayar sekarang** — membuka popup pembayaran.

### Pembayaran
Popup pembayaran dengan 3 metode (tab):

| Metode | Alur |
|--------|------|
| **Tunai** | Input *Uang diterima* (format Rupiah), tombol nominal cepat (Uang pas / 20rb / 50rb / 100rb), **kembalian** dihitung otomatis → *Terima pembayaran*. |
| **Kartu** | Panduan proses di mesin EDC (langkah 1 & 2), status *Menunggu konfirmasi dari mesin EDC* → *Konfirmasi pembayaran*. |
| **QRIS** | Ilustrasi kode QR, nominal & status, *Menunggu pembayaran* → *Cek pembayaran*. |

Setelah pembayaran berhasil → pesanan berstatus **PAID** dan **struk** (receipt) otomatis
dibuat untuk dicetak.

### Riwayat Transaksi
- Daftar semua pesanan (nomor order, waktu, status, total).
- Detail pesanan: item, total, info pembayaran (metode, diterima, kembali).
- **Cetak ulang**: bill (untuk pesanan belum lunas) atau struk (untuk pesanan lunas).

### Cetak (Printing)
- **Bluetooth ESC/POS** — untuk printer thermal struk (Android).
- **PDF / share sheet / AirPrint** — fallback lintas platform (iOS/iPad & Android).
- Bill (sebelum bayar) dan struk (setelah bayar) keduanya bisa dicetak.

### Pengaturan
- Nama toko, alamat, telepon (tercetak di header struk).
- Pajak (%), footer struk.
- Alamat/MAC printer Bluetooth dan lebar kertas (32 = 58mm / 48 = 80mm).

### Lintas Platform
- **Tablet & iPad** — layout **2-pane** (produk kiri, keranjang/total kanan) pada lebar ≥ 700dp;
  layout tunggal (bertumpuk) di layar sempit.
- System bar hitam, konten aman dari notch/status bar.

---

## Tech Stack

| Lapisan | Teknologi |
|---------|-----------|
| Bahasa | **Kotlin** (Multiplatform) |
| UI | **Compose Multiplatform** (Material 3) |
| Database | **SQLDelight** (SQLite, offline-first) |
| State | `MutableStateFlow` (satu state-holder `PosStore`) |
| Font | **DM Sans** (teks umum) + **Plus Jakarta Sans** (judul/harga/total) |
| Concurrency | **kotlinx-coroutines** |
| Waktu | **kotlinx-datetime** |
| Build | Gradle (wrapper) |

Target: **Android** (minSdk 24, compileSdk 35) dan **iOS** (iPad/iPhone).

---

## Arsitektur & Struktur Proyek

```
POSMultiplatform/
├── composeApp/                     # modul utama KMP (shared + per platform)
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/pos/app/
│       │   │   ├── domain/model/       # Product, Order, CartItem, Payment, computeTotals
│       │   │   ├── domain/repository/  # interface Product/Order/SettingsRepository
│       │   │   ├── data/
│       │   │   │   ├── db/             # DatabaseDriverFactory (expect)
│       │   │   │   ├── repo/           # implementasi repository (SQLDelight)
│       │   │   │   └── AppContainer.kt # wiring manual (tanpa DI framework)
│       │   │   ├── ui/                 # App, RegisterScreen, PaymentDialog, ReceiptDialog,
│       │   │   │                       # HistoryScreen, SettingsScreen, PosStore, Theme
│       │   │   ├── print/              # ReceiptFormatter (ESC/POS + teks), Printer (interface)
│       │   │   └── util/               # Money (Rupiah), Time, Initials
│       │   ├── sqldelight/.../Pos.sq   # skema & query SQLDelight
│       │   └── composeResources/       # font + gambar produk
│       ├── androidMain/              # MainActivity, Printer Android (Bluetooth SPP), driver
│       ├── iosMain/                  # MainViewController, Printer iOS (AirPrint/share)
│       └── commonTest/               # unit test
├── iosApp/                          # host Xcode untuk iOS/iPad
└── docs/                            # spec & kredit gambar
```

**Prinsip desain**
- **Domain** murni (model + aturan hitung) — tanpa dependensi UI/DB, mudah diuji.
- **Data** via repository (SQLDelight) — implementasi tersembunyi di balik interface.
- **UI** Compose — satu `PosStore` sebagai state-holder, `AppContainer` wiring manual (tanpa
  Koin/Hilt, biar sederhana).
- **Print** dipisah: `ReceiptFormatter` (murni Kotlin, render ke ESC/POS bytes & teks) +
  `Printer` (per platform: Bluetooth vs AirPrint/PDF).

---

## Prasyarat

- **JDK 17**
- **Android Studio** (atau Android SDK, compileSdk 35) — untuk build & jalankan Android
- **Xcode 15+** — untuk build & jalankan iOS/iPad
- **Gradle** — via wrapper (`./gradlew`), tidak perlu install manual
- (Opsional) **ffmpeg** — hanya untuk membuat ulang GIF demo

---

## Menjalankan

### 1. Clone

```bash
git clone https://github.com/alphadevthasa/KMP-POS.git
cd KMP-POS
```

### 2. Android (HP / tablet Android)

```bash
# via Gradle (device/emulator sudah tersambung)
./gradlew :composeApp:installDebug

# atau buka di Android Studio lalu Run ▶
```

APK debug juga dihasilkan di
`composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

### 3. iOS / iPad

```bash
open iosApp/iosApp.xcodeproj
```

Lalu di Xcode:
1. Set **Team** (Signing) — atau isi `TEAM_ID` di `iosApp/Configuration/Config.xcconfig`.
2. Pilih **iPad simulator** atau **perangkat iPad** sebagai destination.
3. **Run ▶**.

Build framework Kotlin akan otomatis lewat build-phase `Compile Kotlin Framework`
(`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`).

### 4. Smoke test

1. Jalankan app → tampil **Pilih menu favorit** + grid produk (dengan foto).
2. Ketuk 2–3 produk → muncul di keranjang kanan (badge item bertambah).
3. Tekan **Bayar sekarang** → pilih **Tunai**, masukkan *Uang diterima* → kembalian muncul.
4. **Terima pembayaran** → struk muncul (bisa dicetak/dibagikan).
5. Buka tab **Riwayat** → pesanan tersimpan, bisa dicetak ulang.

---

## Alur Penggunaan

```
   ┌─────────────┐   ┌──────────────┐   ┌──────────────────┐
   │ Pilih Produk│ → │   Keranjang  │ → │  Simpan / Bayar  │
   └─────────────┘   └──────────────┘   └──────────────────┘
                                                   │
                        ┌──────────────────────────┼───────────────────┐
                        ▼                          ▼                   ▼
                  ┌──────────┐            ┌────────────────┐   ┌──────────────┐
                  │Lihat Bill│            │  Pembayaran    │   │ Simpan order │
                  │ (cetak)  │            │ Tunai/Kartu/   │   │ → Riwayat    │
                  └──────────┘            │ QRIS           │   └──────────────┘
                                          └────────────────┘
                                                   │ (lunas)
                                                   ▼
                                          ┌────────────────┐
                                          │ Cetak Struk    │
                                          │ + masuk Riwayat│
                                          └────────────────┘
```

1. **Pilih produk** — ketuk kartu produk (atau tombol `+`) untuk menambah ke keranjang.
2. **Atur keranjang** — ubah jumlah, hapus item, isi **Diskon** bila perlu.
3. **Simpan** (opsional) — simpan pesanan untuk di-*hold*; keranjang kosong, pesanan masuk
   **Riwayat** (status *Disimpan*).
4. **Lihat bill** (opsional) — pratinjau & cetak *bill* tagihan sebelum bayar.
5. **Bayar sekarang** — pilih metode (**Tunai/Kartu/QRIS**), selesaikan pembayaran.
6. **Cetak struk** — setelah lunas, struk (receipt) bisa dicetak/dibagikan.
7. **Riwayat** — lihat semua transaksi, cetak ulang bill/struk kapan saja.

---

## Printing (Struk & Bill)

`ReceiptFormatter` (common, murni Kotlin) me-render dokumen ke:
- **ESC/POS bytes** → untuk printer thermal Bluetooth.
- **Teks** → untuk pratinjau layar & fallback PDF.

| Platform | Cetak langsung | Fallback |
|----------|----------------|----------|
| **Android** | Bluetooth **SPP/RFCOMM** (ESC/POS) | PDF (Android share/print) |
| **iOS/iPad** | **AirPrint** | Share sheet (PDF/teks) |

> **Catatan**: iOS memakai AirPrint/share (bukan raw ESC/POS BLE). Printer thermal Bluetooth
> murah umumnya didukung penuh di Android. Alamat printer & lebar kertas diatur di
> **Pengaturan**.

**Jenis dokumen**
- **Bill** — dibuat *sebelum* bayar (berisi item + total, ditandai **BELUM DIBAYAR**).
- **Struk (receipt)** — dibuat *setelah* bayar (berisi item + total + info pembayaran,
  ditandai **LUNAS**), juga bisa dicetak ulang dari **Riwayat**.

---

## Format Mata Uang

- Nilai disimpan sebagai **integer Rupiah** (tanpa desimal).
- Tampilan & input memakai format Indonesia: **`Rp 12.000,00`** (titik = ribuan, koma = desimal).
- Kolom nominal (Diskon, Uang diterima) memakai **keyboard angka** dan auto-format saat diketik
  (`.000` grouping + suffix `,00`).

---

## Pengaturan

Buka tab **Pengaturan**:

| Field | Keterangan |
|-------|------------|
| Nama toko | Tercetak di header struk/bill |
| Alamat / Telepon | Info toko di struk |
| Pajak (%) | Persentase pajak yang dihitung ke total |
| Footer struk | Teks penutup di struk |
| Alamat printer Bluetooth | MAC address printer thermal |
| Lebar kertas | 32 (58mm) atau 48 (80mm) karakter |

---

## Testing

Unit test untuk logika inti (hitung total keranjang, format & parse Rupiah, formatter struk,
initials produk):

```bash
./gradlew :composeApp:testDebugUnitTest
```

---

## Kredit Gambar Produk

Foto produk pada katalog berasal dari **Wikimedia Commons** dengan lisensi bebas
(CC0 / Public domain / CC-BY). Daftar lengkap (file, judul, lisensi, sumber) ada di
[`docs/product-image-credits.txt`](docs/product-image-credits.txt).

Font:
- **DM Sans** & **Plus Jakarta Sans** — [Google Fonts](https://fonts.google.com/) (SIL Open Font License).

---

## License

Silakan tentukan / tambahkan lisensi untuk repo ini (mis. MIT) sesuai kebutuhan.
