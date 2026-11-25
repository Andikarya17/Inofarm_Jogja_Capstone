# Keuangan per musim dan pencatatan penjualan: rencana implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tab Keuangan menampilkan FC, VC, TC dan penjualan per Musim ke-n, dengan unduhan Excel tanpa rasio. Penjualan dicatat per varietas + Layak/Cacat untuk Perorangan, Retail dan Mitra, lengkap dengan kg, jumlah buah opsional dan harga per kg, dan stok panen dikurangi FIFO oleh server.

**Architecture:**
- **Data:** migration 24 menambah tipe `retail`, tabel `sale_lines` (baris yang diketik pengelola), `sale_items.sale_line_id` (alokasi baris ke catatan panen), dan `consumer_prices`.
- **Server:** `SaleService` memakai kelas baru `SaleAllocator` untuk FIFO di dalam transaksi `MutationLedger` yang sama. Laporan baru `SeasonOverview` menjadi satu sumber angka untuk endpoint ringkasan dan untuk `SeasonWorkbook` (.xlsx).
- **Android:** membaca ringkasan lewat GET online. Penjualan tetap lewat antrean offline.

**Tech Stack:** PHP 8 / Laravel 13 / MySQL 8 / PHPUnit, ekstensi PHP `zip` + `xmlwriter` (sudah terpasang); Kotlin, Jetpack Compose Material3, OkHttp, `org.json`, JUnit. Tidak ada dependency baru.

**Spec:** `docs/superpowers/specs/2026-11-25-keuangan-per-musim-dan-penjualan-design.md`. Mockup: kanvas Inofarm Keuangan https://claude.ai/artifact/PskzgVNvPFBhH7zvRdwsQW, halaman "Per musim · 25 Nov" dan "Catat penjualan · 25 Nov".

## Global Constraints

- Baca `AGENTS.md` root dan `backend/AGENTS.md`. Remote `github`; fetch sebelum push; jangan force-push; jangan merge main otomatis.
- **Prasyarat branch:** pekerjaan Kelola di `perbaikan-bug-audit-pengelola` (belum di-commit) ikut mengubah `FinanceOverviewScreen.kt`, `InvestorSummaryScreen.kt`, `StandardCostScreen.kt`, `SaleDraft.kt`, `SaleEntryScreen.kt` dan `Kelola.kt`. Sebelum Task 1, pengguna harus menyetujui commit dan merge pekerjaan itu. Branch baru `keuangan-per-musim-penjualan` dibuat dari `github/main` sesudah merge. Kalau pengguna memilih lain, ikuti pilihannya, dan jangan pernah menimpa berkas yang belum di-commit.
- Migration additive, nomor 24, `down()` melempar `LogicException`. Tes hanya di MySQL `inofarm_test`: jalankan `./scripts/test-backend.ps1 -GuardOnly` lebih dulu. `inofarm_finance_local` hanya dimigrasi atas permintaan eksplisit pengguna, dengan backup.
- Kontrak v2: envelope top-level, UUID lowercase, desimal sebagai string, uang DECIMAL(20,2), kg/harga DECIMAL(20,6). Total HALF_UP sekali per transaksi (`SaleMath`). Kosong/null berbeda dari nol.
- Tipe pembeli wire: `consumer` (Perorangan), `retail` (Retail), `partner` (Mitra). Kualitas wire: `saleable` (Layak), `defective` (Cacat). Grade mitra: `A`, `B`, `C`.
- Semua endpoint baru hanya untuk pengelola (`finance-manager`). Pekerja mendapat 403.
- Tidak ada seed harga. Angka contoh mockup hanya boleh muncul di tes.
- Tidak boleh ada laba, R/C, B/C, BEP atau "Penerimaan (TR)" di layar Android mana pun sesudah rencana ini. Snapshot laporan terbit dan pencetak PDF/CSV-nya **tidak diubah** (keputusan bawaan untuk pertanyaan spec §10.2 selama pengguna belum menjawab lain).
- Form pengelola: Batal / Simpan → Ringkasan (`FarmFormFrame`, `FarmFormBar`). UI mengikuti DESIGN.md Canopy dan mockup. Untuk UI pakai skill `impeccable` + `antislop`, bukan `ponytail`. Teks UI tanpa em dash.
- Warna grafik: biaya hijau (FC `#2B503D`, VC `#9DB58A` pada kartu terang, `#D4E4B7`/`#7E9481` pada kartu gelap), penjualan jingga (Mitra `#B87424`, Retail `#D49A4E`, Perorangan `#E8C48A`). Tanpa tipe: `#C9D1C2`.
- OpenAPI `contracts/v2/openapi.json` dan schema JSON harus lulus `OpenApiParityTest` dan `scripts/check-finance-contracts.py`.
- Emulator hanya `Inofarm_Botanical_API30`, mode terang.

## Review Focus

1. **Antrean offline lama:** operasi sale dengan `items[].harvest_item_id`, dibuat sebelum pembaruan, harus tetap diterima server dan menghasilkan satu baris per item. Ditangani di Task 2 (`test_legacy_items_payload_still_accepted`).
2. **Draf lokal bentuk lama di HP** (`items` dengan `sourceId`) harus terbuka di form baru: sumbernya dipetakan ke kelompok + grade dari cache, dan kalau tidak ketemu, baris tetap ada dengan pesan "Pilih buah lagi". Draf tidak boleh crash atau hilang. Ditangani di Task 7 (`legacyDraftMapsSourceToGroupAndGrade`, `legacyDraftWithUnknownSourceKeepsLine`).
3. **Panen dibatalkan atau dikoreksi turun sesudah ada penjualan:** FIFO tidak boleh mengambil dari panen batal, dan koreksi penjualan lama tidak boleh membuat stok minus. Ditangani di Task 2 (`test_cancelled_harvest_excluded_from_fifo`, `test_correcting_backfilled_sale_keeps_total`).
4. **Lembar biaya musim yang barisnya belum lengkap:** ringkasan dan Excel menampilkan "Belum diisi" atau sel kosong, bukan Rp0, dan TC null. Ditangani di Task 5 (`test_overview_costs_null_when_line_blank`) dan Task 6 (`test_workbook_leaves_blank_cost_cells_empty`).
5. **Nama varietas beda huruf atau spasi** ("golden ", "GOLDEN") harus memakai harga perorangan yang sama. Ditangani di Task 4 (`test_variety_key_normalized`) dan Task 8 (`consumerPriceMatchesVarietyIgnoringCaseAndSpaces`).

---

## Tahap 1: server

### Task 1: Migration 24

**Files:**
- Create: `backend/database/migrations/2026_11_25_000024_sale_lines_retail_consumer_prices.php`
- Create: `backend/app/Domain/Finance/SaleLineMigration.php` (fungsi backfill statis, bisa dites)
- Test: `backend/tests/Feature/FinanceSaleLinesTest.php` (baru)

**Interfaces:**
- Produces: tabel `sale_lines(id uuid pk, sale_id fk sales, planting_group_id fk, grade_id fk grades, grade_name varchar150, variety_name varchar150, buyer_grade char1 null, quantity_kg decimal(20,6), fruit_count int unsigned null, price_per_kg decimal(20,6), exact_amount decimal(40,12), active bool default true, timestamps(6))`. CHECK `quantity_kg > 0 AND price_per_kg >= 0 AND exact_amount >= 0 AND (buyer_grade IS NULL OR buyer_grade IN ('A','B','C')) AND (fruit_count IS NULL OR fruit_count >= 1)`. Index `(sale_id, active)`.
- Produces: `sale_items.sale_line_id` uuid null, fk `sale_lines`, index.
- Produces: CHECK `customer_values` di `customers` dibuat ulang dengan `type IN ('consumer','retail','partner')` (bagian `source`/`record_state` disalin persis dari migration 23).
- Produces: tabel `consumer_prices(id uuid pk, variety_key varchar150, variety_name varchar150, quality varchar10 saleable|defective, price_per_kg decimal(20,6) >= 0, record_state varchar20 default 'active', version uint default 1, timestamps(6))`. Unik aktif `(variety_key, quality)` lewat kolom generated `active_key` (NULL bila tidak aktif) + unique index.
- Produces: `SaleLineMigration::backfill(): int`: untuk setiap `sale_items` tanpa `sale_line_id`, buat satu `sale_lines` dengan `id = SaleLineMigration::lineId($saleItemId)` (UUID v5, namespace tetap di konstanta kelas), lalu isi `sale_line_id`. `variety_name` diambil dari `harvests → planting_groups → varieties`, dan `active` disalin. Mengembalikan jumlah baris yang dibuat. Idempoten.

- [ ] **Step 1: Write failing tests** di `FinanceSaleLinesTest`:
  - `test_backfill_creates_one_line_per_item_and_keeps_totals`: buat penjualan lewat payload `items` lama, kosongkan `sale_line_id` dan hapus `sale_lines`, lalu panggil `backfill()`. Hasilnya `sale_lines` = jumlah item, dan total `exact_amount` per sale sama dengan `sales.exact_total`.
  - `test_backfill_is_idempotent`: panggilan kedua mengembalikan 0.
  - `test_customers_accept_retail`: `POST customers/import` dengan `type=retail` dan `dry_run=false` → 1 baris `retail` tersimpan.
- [ ] **Step 2:** `./scripts/test-backend.ps1 -GuardOnly`, lalu `cd backend; ../.tools/php/php.exe vendor/phpunit/phpunit/phpunit --filter FinanceSaleLinesTest` → FAIL.
- [ ] **Step 3:** Tulis migration (memanggil `SaleLineMigration::backfill()` di akhir `up()`) dan backfill. Ubah validasi `type` di `CustomerController` (index dan import) menjadi `in:consumer,retail,partner`.
- [ ] **Step 4:** Tes terfilter PASS, lalu `./scripts/test-backend.ps1` → seluruh tes PASS.
- [ ] **Step 5: Commit** `feat(backend): migration 24 for sale lines, retail buyers and consumer prices`.

### Task 2: Baris penjualan dan alokasi FIFO

**Files:**
- Create: `backend/app/Domain/Finance/SaleAllocator.php`
- Modify: `backend/app/Domain/Finance/SaleService.php`, `backend/app/Domain/Finance/SaleMath.php`, `backend/app/Http/Controllers/V2/SaleController.php`
- Modify: `scripts/test-finance-concurrency.php` (kasus FIFO baru)
- Test: `backend/tests/Feature/FinanceSaleLinesTest.php`, `backend/tests/Feature/FinanceBuyerGradeTest.php` (tetap lulus)

**Interfaces:**
- Consumes: Task 1 tabel dan `SaleLineMigration::lineId()`.
- Produces: payload sale baru `lines[]` = `{id, planting_group_id, grade_id, quantity_kg, fruit_count|null, price_per_kg, buyer_grade|null}`. Payload hanya boleh berisi `lines` **atau** `items`, tidak keduanya, dan maksimal 30 baris.
- Produces: `SaleAllocator::allocate(string $seasonId, array $lines, Collection $oldAllocations): array`. Mengembalikan daftar alokasi `{id, sale_line_id, harvest_item_id, harvest_id, harvest_version, grade_id, grade_name, quantity_kg}`. Id alokasi = `SaleAllocator::allocationId($lineId, $harvestItemId)` (UUID v5).
- Produces: body sale (`publicRow`) tetap memuat `items` dan kini juga `lines[]` = `{id, planting_group_id, grade_id, grade_name, variety_name, quality, buyer_grade, quantity_kg, fruit_count, price_per_kg, exact_amount, allocations:[{harvest_item_id, harvest_id, quantity_kg}]}`. `quality` = `saleable` bila `grade_name` sama dengan "Layak" (tanpa beda huruf), `defective` bila sama dengan "Cacat", selain itu null.
- Produces: kegagalan `harvest_exceeded` (422) memuat `reasons[]` = `{line_id, available_kg}`.

Aturan FIFO, dikerjakan di dalam closure kedua `MutationLedger::run`:
```
untuk setiap baris (urut id baris):
  kandidat = harvest_items aktif, harvest aktif, harvests.planting_group_id = baris.planting_group_id,
             grade_id = baris.grade_id, urut harvests.occurred_at ASC, harvests.id, harvest_items.id
             (lockForUpdate)
  saldo kandidat = weight_kg - sold_weight_kg + (kg alokasi lama transaksi ini di item itu)
  ambil dari kandidat berurutan sampai quantity_kg terpenuhi; sisa > 0 → harvest_exceeded
```
Alokasi lama transaksi dinonaktifkan (`active=false`), lalu yang baru di-upsert per id. `sold_weight_kg` setiap item yang tersentuh dihitung ulang, dan `HarvestBalance::publish` dipanggil seperti sekarang.

- [ ] **Step 1: Write failing tests** di `FinanceSaleLinesTest`:
  - `test_line_allocates_fifo_across_three_harvests`: panen Layak 10, 20, 30 kg (17, 18, 19 Nov), lalu jual 25 kg → alokasi 10 kg dan 15 kg pada dua panen pertama, sisa 0/5/30, dan body `lines[0].allocations` berisi 2 entri.
  - `test_line_exceeding_stock_rejected_with_available`: stok total 60 kg, jual 61 → 422 `harvest_exceeded` dengan `reasons[0].available_kg` = `"60.000000"`.
  - `test_update_reallocates_and_releases`: jual 25 lalu koreksi jadi 5 → sisa panen pertama 5, panen kedua 20.
  - `test_cancelled_harvest_excluded_from_fifo`: panen 17 Nov dibatalkan → jual 15 kg diambil dari panen 18 Nov.
  - `test_correcting_backfilled_sale_keeps_total`: sale lama hasil backfill, ubah hanya `price_per_kg` → `total_weight_kg` sama dan tidak ada stok minus.
  - `test_legacy_items_payload_still_accepted`: payload `items` lama → 201, dan `lines` berjumlah sama dengan `items`.
  - `test_retail_sale_with_fruit_count`: `customer.type=retail`, dua baris (Golden Layak 50 kg 38 buah Rp28000, Inthanon Layak 20 kg 16 buah Rp26000) → `total_amount` `"1920000.00"`, `customer_type` `retail`, `fruit_count` 38 dan 16.
  - `test_duplicate_line_key_rejected`: dua baris dengan grup + grade sama tanpa buyer_grade → 422.
  - `test_buyer_grade_rejected_for_retail`: retail + `buyer_grade A` → 422.
  - `test_fruit_count_zero_rejected`: `fruit_count: 0` → 422 di `lines.0.fruit_count`.
  - `test_line_group_outside_sale_season_rejected` → 422.
- [ ] **Step 2:** Tes terfilter → FAIL.
- [ ] **Step 3:**
  - Validasi `lines.*` di `SaleController`. `customer_type` dan `customer.type` menjadi `in:consumer,retail,partner`.
  - `SaleMath::quote` menerima baris dengan kunci yang sama (`quantity_kg`, `price_per_kg`), dan pesannya menyebut "baris".
  - `SaleService` mengubah `items` lama menjadi `lines`, masing-masing dengan alokasi tetap ke `harvest_item_id`-nya. Lalu `SaleAllocator`, simpan `sale_lines` (snapshot `variety_name`, `grade_name`), dan `publicRow` dengan `lines`.
  - `CustomerService::forSale` menerima `retail`.
- [ ] **Step 4:** Tambah kasus di `scripts/test-finance-concurrency.php`: dua klien bersamaan menjual 40 kg dari stok 60 kg → tepat satu 201 dan satu 422 `harvest_exceeded`, dengan `sold_weight_kg` total 40. Jalankan sesuai petunjuk di kepala skrip → PASS.
- [ ] **Step 5:** `./scripts/test-backend.ps1` → PASS (termasuk `FinanceBuyerGradeTest`, `FinanceSaleTest`).
- [ ] **Step 6: Commit** `feat(backend): sale lines with FIFO harvest allocation and retail buyers`.

### Task 3: Kontrak penjualan

**Files:**
- Modify: `contracts/v2/openapi.json` (schema `SaleOperationPayload` dengan `lines` dan `items` lama, `SalePublic.lines`, enum tipe pembeli), `contracts/v2/` schema operasi sale + fixture baru `sale-lines-retail.json`, `contracts/v2/CUSTOMERS.md`
- Create: `contracts/v2/SALES.md` (baris, FIFO, bentuk lama, kualitas, tipe pembeli)
- Test: `backend/tests/Feature/OpenApiParityTest.php` (sudah ada), `scripts/check-finance-contracts.py`

- [ ] **Step 1:** Perbarui kontrak sesuai Interfaces Task 2.
- [ ] **Step 2:** `cd backend; ../.tools/php/php.exe vendor/phpunit/phpunit/phpunit --filter OpenApiParityTest` dan `./.tools/finance-contracts-venv/Scripts/python.exe scripts/check-finance-contracts.py` → PASS.
- [ ] **Step 3: Commit** `docs(contracts): sale lines, retail buyers`.

### Task 4: Harga perorangan

**Files:**
- Create: `backend/app/Domain/Finance/ConsumerPriceService.php`, `backend/app/Http/Controllers/V2/ConsumerPriceController.php`
- Modify: `backend/routes/api_v2.php`, `contracts/v2/openapi.json`, schema + fixture `consumer-price-operation.schema.json`
- Test: `backend/tests/Feature/FinanceConsumerPriceTest.php` (baru)

**Interfaces:**
- Produces: `GET consumer-prices` → `{data:[{id, variety_name, quality, price_per_kg, version, record_state, updated_at}]}` (aktif saja; `?all=1` termasuk nonaktif).
- Produces: `POST consumer-prices` (envelope, `entity_type: "consumer_price"`, `action: create`, payload `{variety_name, quality, price_per_kg}`) dan `PATCH consumer-prices/{id}` (`action: update` payload yang sama dengan opsi `record_state: inactive`). Tidak ada DELETE. Audit ditulis, `change_feed` dilewati (tanpa greenhouse).
- Produces: `ConsumerPriceService::key(string $name): string` = huruf kecil, spasi di ujung dibuang, spasi ganda dijadikan satu.

- [ ] **Step 1: Write failing tests:**
  - `test_manager_creates_and_lists_price`
  - `test_variety_key_normalized`: "Golden" lalu " GOLDEN " dengan kualitas sama → 409 `duplicate`.
  - `test_update_bumps_version_and_audits`
  - `test_deactivate_then_create_same_key_allowed`
  - `test_worker_forbidden` (GET dan POST → 403)
  - `test_negative_price_rejected` (422).
- [ ] **Step 2:** FAIL → implementasi → PASS (filter `FinanceConsumerPriceTest`, lalu parity).
- [ ] **Step 3: Commit** `feat(backend): consumer price list`.

### Task 5: Ringkasan musim

**Files:**
- Create: `backend/app/Domain/Finance/SeasonOverview.php`
- Modify: `backend/app/Http/Controllers/V2/FieldReportController.php` (aksi `seasonOverview`, hapus `profitSummary`), `backend/app/Domain/Finance/FieldReports.php` (hapus `profitSummary`/`figures`; `income` mendapat `retail` di `by_type`), `backend/routes/api_v2.php` (route `reports/season-overview`, hapus `reports/profit-summary`), `contracts/v2/openapi.json`
- Test: `backend/tests/Feature/FinanceSeasonOverviewTest.php` (baru), `backend/tests/Feature/FinanceIncomeReportTest.php` (hapus tes profit-summary, tambah retail)

**Interfaces:**
- Consumes: `SeasonCostSheetService::publicSheet(ensureFor(n))`, `sales`/`sale_lines`.
- Produces: `SeasonOverview::build(User $actor, int $sequence): array` dan `SeasonOverview::sequences(User $actor): array`.
- Produces: `GET reports/season-overview?sequence=n` →
```
{ data: {
  sequences: [{sequence, status: "running"|"closed", greenhouse_count, fc, vc, tc, sales_amount, sales_kg}],
  selected: {
    sequence, date_range: {from, until, running},
    cost_sheet: {id, version, lines:[{name, cost_class, amount, is_required}], per_greenhouse: {fc, vc, tc}, farm: {fc, vc, tc}},
    sales: {amount, kg, count, by_type: {consumer|retail|partner|unspecified: {amount, kg, count}}},
    by_greenhouse: [{greenhouse_id, name, amount, kg, count}],
    weekly: [{key, label, start, amount, kg, by_type:{...}}] } } }
```
  - `greenhouse_count` = jumlah GH aktif yang punya musim dengan sequence n. `farm` = `per_greenhouse` × count. Bila ada baris kosong, `fc`/`vc`/`tc` null.
  - Penjualan = `sales` aktif yang `season_id`-nya musim sequence n di GH aktif.
  - `weekly` memakai `FieldReports::bucketStart` (Senin, WIB) dari minggu penjualan pertama sampai minggu `until`.
  - Tanpa `sequence`, `selected` memakai sequence berjalan terbesar, atau sequence terbesar kalau tidak ada yang berjalan. Sequence yang tidak ada → 404.

- [ ] **Step 1: Write failing tests** (data contoh mockup Musim 3, 2 GH di fixture tes):
  - `test_overview_farm_cost_is_sheet_times_greenhouses`: lembar FC 7581100, VC 3430000, 2 GH → `farm.tc` `"22022200.00"`, `per_greenhouse.tc` `"11011100.00"`.
  - `test_overview_costs_null_when_line_blank`
  - `test_overview_sales_by_type_includes_retail`: satu penjualan per tipe → keempat kunci `by_type` ada, nilai retail benar, `count` 1.
  - `test_overview_counts_only_sequence_sales`: penjualan Musim 1 tidak ikut di Musim 2.
  - `test_overview_weekly_buckets_monday_wib`: penjualan Minggu 23:30 WIB masuk minggu sebelumnya.
  - `test_sequences_list_has_status_and_totals`
  - `test_profit_summary_route_removed` → 404.
  - `test_income_by_type_has_retail` (di `FinanceIncomeReportTest`).
  - `test_worker_forbidden_overview` → 403.
- [ ] **Step 2:** FAIL → implementasi → PASS, lalu `./scripts/test-backend.ps1` PASS.
- [ ] **Step 3: Commit** `feat(backend): season overview replaces profit summary`.

### Task 6: Excel per musim

**Files:**
- Create: `backend/app/Domain/Finance/SeasonWorkbook.php` (penulis .xlsx minimal: `[Content_Types].xml`, `_rels/.rels`, `xl/workbook.xml`, `xl/_rels/workbook.xml.rels`, `xl/styles.xml`, `xl/worksheets/sheetN.xml`, inline string, angka sebagai `<c t="n">`)
- Modify: `FieldReportController` (aksi `seasonExport`), `routes/api_v2.php` (`GET reports/season-export`), `contracts/v2/openapi.json`
- Test: `backend/tests/Feature/FinanceSeasonExportTest.php` (baru)

**Interfaces:**
- Consumes: `SeasonOverview` (Task 5) dan `sale_lines`.
- Produces: `SeasonWorkbook::render(array $sheets): string`, dengan `$sheets` = `[['name' => string, 'rows' => list<list<string|int|float|null>>]]`. String yang berupa desimal ditulis sebagai angka.
- Produces: `GET reports/season-export?sequence=<n|all>&parts=summary,costs,sales` → 200 dengan `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment; filename="inofarm-keuangan-musim-<n|semua>-<YYYYMMDD>.xlsx"` dan `X-Content-SHA256`. `parts` kosong atau berisi nilai tidak dikenal → 422.
- Kolom sheet persis spec §6.2 (Ringkasan per GH 8 kolom + baris Total, Rincian biaya 7 kolom + baris Total, Daftar penjualan 11 kolom). Baris pertama setiap sheet berisi judul "Musim n · diunduh <tanggal jam> WIB", dan baris kedua header.

- [ ] **Step 1: Write failing tests:**
  - `test_workbook_is_valid_zip_with_three_sheets`: buka hasil dengan `ZipArchive`, ada `xl/worksheets/sheet1..3.xml`, dan `workbook.xml` memuat nama sheet "Ringkasan per GH", "Rincian biaya", "Daftar penjualan".
  - `test_summary_numbers_are_numeric_cells`: sel TC GH A `<c ... t="n"><v>11011100</v>`.
  - `test_workbook_leaves_blank_cost_cells_empty`
  - `test_sales_sheet_lists_lines_with_variety_quality_and_fruit`: baris Golden, Layak, 50, 38, 28000, 1400000.
  - `test_parts_filter_and_validation`: `parts=summary` → 1 sheet; `parts=foo` → 422.
  - `test_export_all_sequences`: `sequence=all` → ringkasan berisi semua musim.
  - `test_worker_forbidden_export` → 403.
- [ ] **Step 2:** FAIL → implementasi → PASS, lalu parity dan checker kontrak PASS.
- [ ] **Step 3: Commit** `feat(backend): season Excel export`.

## Tahap 2: Android

### Task 7: Model penjualan baru (logika, tanpa UI)

**Files:**
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/sales/SaleDraft.kt`
- Create: `android/app/src/main/java/com/inofarm/jogja/feature/sales/SaleStock.kt`
- Test: `android/app/src/test/java/com/inofarm/jogja/SaleDraftTest.kt`, `SaleStockTest.kt` (baru)

**Interfaces:**
- Produces: `data class SaleLine(val id: String, val groupId: String, val gradeId: String, val buyerGrade: String = "", val quantity: String = "", val fruitCount: String = "", val price: String = "")`.
- Produces: `SaleDraft.buyerType` bernilai `"consumer" | "retail" | "partner"`, dan wajib untuk draf baru. `SaleDraft.payload()` mengirim `lines` (tidak lagi `items`). `fruit_count` dikirim null bila kosong. `customer` wajib untuk retail/partner dan opsional untuk consumer.
- Produces: `SaleDraft.fromLocal(json, stock: SaleStock)`: membaca draf bentuk lama (`items[].sourceId`) dan memetakan `sourceId` lewat `stock.sourceOf(harvestItemId)` ke `groupId`+`gradeId`. Bila tidak ketemu, baris tetap ada dengan `groupId=""` dan validasi "Pilih buah lagi".
- Produces: `class SaleStock(harvests: List<JSONObject>, balances: List<JSONObject>, groups: List<JSONObject>, varieties: List<JSONObject>, grades: List<JSONObject>)`, dengan:
  - `fun options(seasonId: String): List<StockOption>`, di mana `StockOption(groupId, gradeId, varietyName, quality: String?, availableKg: BigDecimal)`, dijumlah per grup + grade dari panen aktif.
  - `fun fifoPreview(seasonId: String, lines: List<SaleLine>): List<StockAfter>` (urut panen sama dengan server).
  - `fun sourceOf(harvestItemId: String): Pair<String, String>?`.
- Produces: `object SalePricing` dengan `fun consumerPrice(prices: List<JSONObject>, varietyName: String, quality: String?): String?` (kunci dinormalisasi seperti `ConsumerPriceService::key`) dan `fun lastRetailPrice(sales: List<JSONObject>, customerId: String, varietyName: String): RetailHint?` (`RetailHint(pricePerKg, kg, paidAt)`).

- [ ] **Step 1: Write failing JVM tests:**
  - `payloadSendsLinesWithFruitCountNullWhenBlank`
  - `retailRequiresBuyer`, `consumerBuyerOptional`, `buyerTypeRequired`
  - `buyerGradeOnlyForPartner`, `duplicateLineKeyRejectedUnlessPartnerGradesDiffer`
  - `legacyDraftMapsSourceToGroupAndGrade`, `legacyDraftWithUnknownSourceKeepsLine`
  - `stockSumsHarvestsPerGroupAndGrade` (10+20+30 kg = 60)
  - `fifoPreviewTakesOldestFirst` (jual 25 → 10 dari panen 17 Nov, 15 dari 18 Nov)
  - `consumerPriceMatchesVarietyIgnoringCaseAndSpaces`
  - `lastRetailPriceUsesSameCustomerAndVariety`
  - `saleBodyWithoutLinesStillParses` (body lama hanya `items`)
- [ ] **Step 2:** `./scripts/build-android.ps1 -JavaHome "C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot" -Tasks ':app:testDebugUnitTest'` → FAIL.
- [ ] **Step 3:** Implementasi. `SaleAmounts.quote` tetap dipakai untuk pratinjau total.
- [ ] **Step 4:** JVM PASS.
- [ ] **Step 5: Commit** `feat(android): sale lines model, stock and pricing hints`.

### Task 8: Retail di pembeli dan Harga perorangan di Kelola

**Files:**
- Modify: `feature/customers/CustomerScreens.kt`, `CustomerRepository.kt` (tipe `retail` di daftar, filter dan impor)
- Create: `feature/admin/ConsumerPriceRepository.kt`, `feature/admin/ConsumerPriceScreen.kt`
- Modify: `feature/admin/Kelola.kt`: baris `KelolaRow("Harga perorangan", "<n> harga", Modifier.testTag("kelola-consumer-prices"))` di halaman utama Kelola, bagian kebun (dekat "Pekerja dan akun")
- Test: JVM `ConsumerPriceFormTest.kt` (baru), androidTest `FinanceAdminUiTest.kt` (`consumerPriceListAndEdit`)

**Interfaces:**
- Consumes: Task 4 (`consumer-prices`).
- Produces: `ConsumerPriceRepository.list(session, ticket): List<ConsumerPrice>`, `save(session, ticket, draft: ConsumerPriceDraft): ConsumerPrice`, `ConsumerPrice(id, varietyName, quality, pricePerKg, version)`.
- Layar mengikuti artboard `HargaPerorangan`: bagian Layak dan Cacat, satu baris per varietas aktif dari semua GH (nama unik setelah dinormalisasi), "Belum diatur, diketik saat menjual" bila kosong, dan sheet ubah Batal / Simpan → Ringkasan. Catatan bawah persis teks artboard.

- [ ] **Step 1:** Tes gagal: `priceDraftRejectsNegative`, `varietyNamesDedupedAcrossGreenhouses`, lalu tes UI `consumerPriceListAndEdit` dan `customerListFiltersRetail`.
- [ ] **Step 2:** Implementasi → tes PASS, lint 0 error.
- [ ] **Step 3: Commit** `feat(android): retail buyers and consumer price list`.

### Task 9: Form Catat penjualan dan Ringkasan

**Files:**
- Modify: `feature/sales/SaleEntryScreen.kt`, `SaleEntryViewModel.kt`, `SaleRepository.kt`, `feature/customers/BuyerSection.kt`, `feature/FinanceRoot.kt` (menyalurkan `SaleStock`, harga perorangan dan cache sales ke form)
- Test: `android/app/src/androidTest/java/com/inofarm/jogja/FinanceEntryUiTest.kt` (tes sale yang ada disesuaikan ke tag baru)

**Interfaces:**
- Consumes: Task 7 (`SaleStock`, `SalePricing`, `SaleLine`), Task 8 (`ConsumerPriceRepository.list()`, pembeli retail).
- Produces test tag: `sale-type-consumer|retail|partner`, `sale-gh-change`, `sale-stock-<groupId>-<gradeId>` (kartu), `sale-add-<groupId>-<gradeId>` (chip tambah), `sale-kg-<lineId>`, `sale-fruit-<lineId>`, `sale-price-<lineId>`, `sale-price-hint-<lineId>`, `sale-add-grade-<groupId>`, `sale-total`, `sale-review`, `sale-summary-stock-<groupId>-<gradeId>`.

Tata letak dan teks persis mengikuti artboard `CatatPenjualan` dan `RingkasanPenjualan`:
- Label tipe: "Perorangan" dengan deskripsi "Pembeli akhir, harga tetap"; "Retail" dengan deskripsi "Toko atau pedagang, harga ditawar"; "Mitra" dengan deskripsi "Tengkulak, grade A/B/C".
- Petunjuk harga:
  - Perorangan: "Harga perorangan <Varietas> <Layak|Cacat>, diisi dari Kelola. Masih bisa diubah."
  - Retail: "Harga ditawar. Terakhir ke <pembeli>: Rp<harga>/kg untuk <kg> kg, <tgl>." Bila belum ada riwayat: "Harga ditawar. Belum ada penjualan sebelumnya ke pembeli ini."
  - Mitra: "Grade dinilai mitra, harga ditawar."
- Teks stok: "Sesudah dijual sisa <kg> kg". Teks ringkasan FIFO: "Diambil dari panen <tgl> (<kg> kg)…".

- [ ] **Step 1:** Sesuaikan atau tambah tes UI fixture: `saleRetailTwoVarietiesShowsTotalAndStock` (total Rp1.920.000, Golden sisa 290 kg), `salePartnerSplitsGrades`, `saleConsumerPrefillsPrice`. Jalankan di emulator → FAIL.
- [ ] **Step 2:** Implementasi UI. Validasi server 422 dipetakan per baris lewat `lines.<i>.<field>` ke id baris.
- [ ] **Step 3:** Tes UI terfokus PASS, JVM PASS, lint 0 error.
- [ ] **Step 4: Commit** `feat(android): sale form by variety and quality for three buyer types`.

### Task 10: Tab Keuangan per musim dan daftar penjualan musim

**Files:**
- Create: `feature/financehome/SeasonOverviewModel.kt`, `feature/financehome/SeasonSalesScreen.kt`
- Modify: `feature/financehome/FinanceHomeRepository.kt` (hapus `profitSummary`, tambah `seasonOverview(session, ticket, sequence: Int?): SeasonOverview`), `FinanceOverviewScreen.kt` (ditulis ulang per artboard `KeuanganMusim`), `IncomeChartModel.kt` (tipe retail, atau hapus bila tidak dipakai lagi)
- Delete: `feature/financehome/InvestorSummaryScreen.kt` beserta `FinancePage.INVESTOR`
- Modify: `feature/reports/SeasonReportScreen.kt`: hanya menampilkan FC, VC, TC, penjualan, kg
- Test: JVM `SeasonOverviewModelTest.kt` (baru), androidTest `FinanceUiTest` atau tes finance home yang ada

**Interfaces:**
- Consumes: respons Task 5.
- Produces: `data class SeasonOverview(val sequences: List<SeasonTotals>, val selected: SeasonDetail)` beserta fungsi tampilan `weeklyBars(): List<WeekBar>` (`WeekBar(label, partner, retail, consumer, unspecified)`), `compareBars(): List<CompareBar>` (`CompareBar(name, fc, vc, sales, selected)`), dan `costShare(line): Float` (bagian dari TC per GH).
- Test tag: `finance-season-<n>`, `finance-tc`, `finance-cost-line-<i>`, `finance-week-<i>`, `finance-gh-<id>`, `finance-season-sales`, `finance-compare`, `finance-excel`, `finance-eye`; layar daftar: `season-sales-type-<type>`, `season-sales-gh`, `season-sales-search`.

- [ ] **Step 1:** Tes JVM gagal:
  - `parsesOverviewWithNullCosts` (tampil "Belum diisi")
  - `weeklyBarsStackFourTypes`
  - `compareBarsScaleToLargestValue`
  - `costShareUsesPerGreenhouseTc`
  - `noRatioFieldsInModel` (refleksi: model tidak punya properti `rc`, `bep`, `profit`)
- [ ] **Step 2:** Implementasi.
  - Daftar penjualan musim membaca sales dari `HistoryRepository` (kind `sale`) dan memfilter berdasarkan `season_id` dari musim dengan sequence itu. Ringkasan di kepala layar memakai `selected.sales`.
  - Cari, filter tipe dan GH bekerja di perangkat.
  - Ikon mata dan "Biaya nyata" tetap memakai `FinanceVisibilityPreference`.
- [ ] **Step 3:** `rg -n "R/C|BEP|B/C|Laba|Penerimaan \(TR\)" android/app/src/main` → tidak ada hasil di UI (komentar boleh hanya bila menjelaskan penghapusan).
- [ ] **Step 4:** JVM PASS, lint 0 error, lalu tes UI finance home disesuaikan PASS.
- [ ] **Step 5: Commit** `feat(android): finance tab per season without ratios`.

### Task 11: Unduh Excel

**Files:**
- Create: `core/network/SeasonExportTransport.kt` (pola `ReportExportTransport`: tanpa redirect, cek `Content-Type`, `X-Content-SHA256`, batas ukuran 8 MiB), `feature/financehome/SeasonExportSheet.kt`
- Modify: `FinanceRuntime.kt` (wiring), `FinanceOverviewScreen.kt` (tombol Excel), `FinanceRoot.kt` (launcher `ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")`)
- Test: JVM `SeasonExportTransportTest.kt` dengan MockWebServer

**Interfaces:**
- Produces: `SeasonExportTransport.fetch(base, token, sequence: String, parts: Set<String>): ExportFile(bytes, fileName)`. `sequence` adalah angka atau `"all"`, dan `parts` ⊂ `{summary, costs, sales}` serta tidak kosong.
- Sheet mengikuti artboard `EksporExcel`: pilihan musim (berjalan, selesai, Semua musim), tiga centang, nama file, Batal / Unduh .xlsx.

- [ ] **Step 1:** Tes gagal: `rejectsWrongContentType`, `rejectsHashMismatch`, `buildsQueryWithParts`, `rejectsEmptyParts`, `usesServerFileName`.
- [ ] **Step 2:** Implementasi → PASS, lint 0 error.
- [ ] **Step 3: Commit** `feat(android): download season Excel`.

## Tahap 3: bukti dan konteks

### Task 12: Uji backend nyata, bukti, konteks

**Files:**
- Modify: `scripts/finance-device-fixture.php` (cleanup juga menghapus `sale_lines` dan `consumer_prices` milik fixture)
- Create: `docs/context/FINANCE_SEASON_SALES_EVIDENCE_20261125.md`, folder `docs/context/evidence/keuangan-per-musim-20261125/`
- Modify: `CLAUDE.md` (paragraf baru di atas, ringkasan + tautan bukti), `docs/context/IMPLEMENTATION_STATUS.md`, `docs/context/GAP_ANALYSIS.md`

- [ ] **Step 1:** `./scripts/test-backend.ps1`, `./scripts/build-android.ps1 -JavaHome ...`, checker kontrak, `python scripts/check-finance-context.py` → semua PASS. Catat jumlah tes.
- [ ] **Step 2:** Emulator `Inofarm_Botanical_API30` (mode terang) terhadap Laravel `--env=testing` + `inofarm_test` (port 8001, `adb reverse`, seperti bukti 21 November). Fixture 2 GH, panen Layak dan Cacat tiga tanggal, lembar biaya Musim 1. Lakukan dan screenshot:
  - Penjualan Perorangan dengan harga terisi.
  - Penjualan Retail dua varietas dengan petunjuk harga.
  - Penjualan Mitra dengan tiga grade.
  - Ringkasan stok FIFO.
  - Tab Keuangan per musim.
  - Daftar penjualan musim dengan filter Retail.
  - Unduh Excel, lalu file dibuka (unzip dan baca `sheet1.xml`).
  - Harga perorangan di Kelola.

  Cocokkan angka layar dengan SQL.
- [ ] **Step 3:** Tulis dokumen bukti (label: tes otomatis, backend nyata, emulator; batas: belum ponsel fisik, belum TalkBack, `inofarm_finance_local` belum dimigrasi kecuali pengguna meminta).
- [ ] **Step 4: Commit** `docs(context): evidence for season finance and sale lines`, lalu tanya pengguna sebelum push, PR, atau migration 24 ke `inofarm_finance_local`.
