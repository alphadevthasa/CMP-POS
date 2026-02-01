# POS Multiplatform — Design Spec

Date: 2026-10-17
Status: approved

## Goal

A point-of-sale (cashier) app for tablets (Android) and iPad built on Kotlin
Multiplatform + Compose Multiplatform. Fully offline, local SQLite. Covers the
full flow: pick products → cart → save order → print bill (pre-payment) → pay →
print receipt → browse history and reprint.

## Non-goals (YAGNI)

Multi-user/auth, backend sync, advanced refunds/voids, promotions engine,
reporting/analytics, multi-branch. Add later if needed.

## Stack

- Kotlin Multiplatform + Compose Multiplatform (shared UI for Android + iOS).
- SQLDelight 2.x over SQLite (local, offline-first).
- No DI framework — a single manual `AppContainer` + one `PosStore` state holder.
- Money as `Long` in minor units (IDR has no decimals → plain rupiah).

## Module layout

```
POSMultiplatform/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, wrapper
  composeApp/
    commonMain/kotlin/com/pos/app/
      domain/model        Product, Category, Order, OrderLine, Payment,
                          OrderStatus, CartItem, Money helpers
      domain/repository   ProductRepository, OrderRepository, SettingsRepository
      data/db             DatabaseDriverFactory (expect), Pos.sq (SQLDelight)
      data/repo           *RepositoryImpl
      data/seed           SeedData (sample products/categories)
      ui                  ProductGridScreen, CartPane, PaymentDialog,
                          OrdersHistoryScreen, OrderDetailScreen,
                          ReceiptPreviewScreen, SettingsScreen, PosTheme,
                          App (root, responsive layout)
      ui                  PosStore (MutableStateFlow state holder)
      print               ReceiptFormatter (ESC/POS bytes + PDF doc model, common)
      print               Printer (expect class: connect/print/disconnect)
      util                Money formatting, OrderNumber generator
    androidMain/kotlin/com/pos/app/
      MainActivity, DatabaseDriverFactory.android, Printer.android
      (Bluetooth SPP ESC/POS), PdfPrinter.android (Android print framework)
    iosMain/kotlin/com/pos/app/
      MainViewController, DatabaseDriverFactory.ios, Printer.ios
      (CoreBluetooth BLE write; PDF/share fallback via UIActivityViewController)
  iosApp/                  Xcode project hosting the Compose UIViewController
```

Package: `com.pos.app`. Android minSdk 24, compileSdk 35, targetSdk 35.

## Data model (SQLDelight)

- `category(id, name)`
- `product(id, name, sku, price, category_id, active)`
- `order(id, order_no, status, note, subtotal, tax, discount, total,
  paid_total, change_due, created_at, updated_at)`
- `order_line(id, order_id, product_id, name_snapshot, price_snapshot, qty, line_total)`
- `payment(id, order_id, method, amount, tendered, change, created_at)`
- `setting(key, value)` — store name, address, phone, tax %, printer address.

`status` ∈ `OPEN`, `SAVED`, `PAID`, `VOID`.

Line items snapshot name+price so history stays correct if a product changes.

## Flows

1. **New order**: pick products into cart → edit qty → **Save order** (persist,
   status `SAVED`).
2. **Print bill** (pre-payment): ESC/POS bill without payment info.
3. **Pay**: pick method (Cash / Card / QRIS) → enter tendered (cash) → compute
   change → status `PAID` → record `payment` → **Print receipt**.
4. **History**: list orders (filter by status/date) → open detail → reprint
   bill (if `OPEN`/`SAVED`) or receipt (if `PAID`).

## Printing

`ReceiptFormatter` is pure Kotlin (common) producing:
- ESC/POS `ByteArray` for thermal Bluetooth printers, and
- a simple `PdfDoc` model (lines + alignment) rendered per platform.

`expect class Printer` with `suspend fun connect()`, `suspend fun write(bytes)`,
`suspend fun disconnect()`.
- **Android**: classic Bluetooth RFCOMM socket to configured printer address;
  fallback to Android print framework (share/PDF).
- **iOS**: CoreBluetooth BLE write to the configured peripheral characteristic;
  fallback to share-sheet PDF (`UIActivityViewController`).

Printer address/MAC and store info live in `setting` → live device tuning knob
(real hardware differs).

## UI / responsive

- Width ≥ 900dp: two-pane master–detail (product grid left, cart right).
- Narrower: single column, cart in a bottom sheet/dialog.
- iPad landscape uses the two-pane layout natively.
- Touch targets sized for tablet; product cards in a flexible grid.

## Testing

- `CartTest`: line totals, subtotal, tax, discount, total, change.
- `ReceiptFormatterTest`: byte output contains expected lines/alignments.
- SQLDelight round-trip smoke test (in-memory driver on JVM/Android).

## Build phases

1. Scaffold Gradle + modules + wrapper.
2. DB schema + seed + repositories.
3. Product grid + cart.
4. Save order + payment + totals.
5. Printing (formatter + platform printers + PDF fallback).
6. History + detail + reprint + settings.

## Open tuning knobs

- Printer MAC/name (per device), tax %, store identity, currency formatting.
