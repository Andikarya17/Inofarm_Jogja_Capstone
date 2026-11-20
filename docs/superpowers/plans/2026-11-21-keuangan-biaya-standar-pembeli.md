# Keuangan biaya standar, grafik dan pembeli: rencana implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pengelola Inofarm mengisi satu lembar biaya standar per Musim ke-n untuk seluruh kebun, melihat laba musim dan grafik pemasukan bertumpuk per tipe pembeli, serta mencatat penjualan ke pembeli perorangan (dengan impor Excel) dan mitra per grade A/B/C.

**Architecture:** Backend Laravel mendapat migration 23 (`seasons.sequence`, `season_cost_sheets`, `season_cost_lines`, `customers`, `sales.customer_id`, `sale_items.buyer_grade`). Ada service baru di `app/Domain/Finance`, dan laporan musim mengganti sumber FC/VC menjadi lembar biaya standar. Lembar biaya dan pembeli berlaku untuk seluruh kebun, sehingga melewati `change_feed`; Android membacanya lewat GET online. Penjualan tetap lewat antrean offline. Android Compose mendapat layar Keuangan baru (grafik `Canvas`), Biaya standar, Ringkasan investor, dan layar pembeli.

**Tech Stack:** PHP 8 / Laravel 13 / MySQL 8 / PHPUnit; Kotlin, Jetpack Compose Material3, OkHttp, `org.json`, JUnit. Tidak ada dependency baru.

**Spec:** `docs/superpowers/specs/2026-11-21-keuangan-biaya-standar-pembeli-design.md`. Mockup: kanvas Inofarm Keuangan, https://claude.ai/artifact/PskzgVNvPFBhH7zvRdwsQW.

## Global Constraints

- Baca `AGENTS.md` root dan `backend/AGENTS.md`. Branch kebab-case tanpa prefix `codex/`, misalnya `keuangan-biaya-standar`. Remote `github`. Jangan merge main otomatis dan jangan force-push.
- Migration additive saja (nomor 23 dan seterusnya). `down()` melempar `LogicException` seperti migration finance lain.
- Tes hanya di MySQL `inofarm_test`. Jalankan `./scripts/test-backend.ps1 -GuardOnly` sebelum tes terfilter. Jangan menyentuh `inofarm_finance_local` tanpa izin pengguna dan backup.
- Kontrak v2: envelope top-level (`schema_version: "2"`, `sync_epoch`, `operation_id`, `entity_id`, `entity_type`, `action`, `base_version`, `payload`). UUID lowercase. Desimal dikirim sebagai string; uang DECIMAL(20,2), kg DECIMAL(20,6). Kosong/null berbeda dari nol. HALF_UP sekali setelah penjumlahan presisi penuh (pakai `Money`/`SaleMath` yang ada).
- Pekerja: semua endpoint baru menjawab 403 (`finance-manager` middleware atau `GreenhouseAccess::manager`).
- Nama baris wajib persis: FC `Biaya penyusutan`, `Sewa tanah`, `Tenaga kerja`; VC `Cocopeat/rockwool`, `Nutrisi AB Mix`, `Nutrisi tambahan dan pestisida`, `Benih`.
- Tidak ada seed angka biaya. Angka Inofarm hanya boleh muncul sebagai nilai tes.
- Form pengelola: Batal / Simpan, lalu Ringkasan (`FarmFormFrame`, `FarmFormBar`, `FarmReviewFrame summary = true`).
- UI mengikuti DESIGN.md (Canopy): tanpa warna baru, angka tabular, status ditulis dengan teks. Untuk UI pakai skill `impeccable` + `antislop`, **bukan** `ponytail`.
- Teks UI tanpa em dash.
- OpenAPI `contracts/v2/openapi.json` dan schema JSON diperbarui sampai `OpenApiParityTest` dan `scripts/check-finance-contracts.py` lulus.
- Emulator hanya `Inofarm_Botanical_API30`, mode terang.

## Review Focus

1. **Laporan musim yang sudah terbit** harus tetap identik setelah migration dan setelah lembar biaya diubah. Ditangani di tes Task 3 (`test_published_report_is_unchanged_by_cost_sheet_edit`).
2. **Musim lama tanpa nomor yang jelas**: dua musim di satu GH dengan `start_date` sama harus mendapat nomor berbeda yang stabil (urut `start_date`, lalu `id`). Ditangani di tes Task 1 (`test_sequence_backfill_breaks_ties_by_id`).
3. **Mengubah Musim n saat salah satu GH sudah menutup Musim n** harus ditolak dengan `season_closed`, bukan diam-diam mengubah biaya musim tertutup. Ditangani di Task 2 (`test_update_rejected_when_any_season_of_sequence_closed`).
4. **Penjualan lama tanpa `customer_type`** harus tetap muncul di grafik sebagai `unspecified`, tidak hilang dan tidak masuk Mitra. Ditangani di Task 4 (`test_income_counts_untyped_sales_as_unspecified`).
5. **Excel dengan nomor WA berbeda format** (`0812...`, `+62812...`, `62812...`, berspasi atau bertanda hubung) harus dianggap orang yang sama. Ditangani di Task 9 (`test_import_dedupes_whatsapp_formats`) dan di tes JVM normalisasi Task 10.

---

## Tahap 1 (wajib): biaya standar dan laporan musim

### Task 1: Migration 23 dan nomor musim

**Files:**
- Create: `backend/database/migrations/2026_11_21_000023_standard_costs_and_customers.php`
- Modify: `backend/app/Domain/Sync/MutationLedger.php`: lewati insert `change_feed` bila `$change['greenhouse_id'] === null`; audit dan receipt tetap ditulis.
- Modify: `backend/app/Domain/Cultivation/MasterService.php`: saat create `seasons`, isi `sequence = max(sequence di GH itu) + 1` dalam transaksi yang sama.
- Test: `backend/tests/Feature/FinanceStandardCostTest.php` (baru)

**Interfaces:**
- Produces: kolom `seasons.sequence` (unsigned int, nullable, index `(greenhouse_id, sequence)`).
- Produces: tabel `season_cost_sheets(id uuid pk, sequence uint unique, record_state varchar20 default 'active', version uint default 1, timestamps(6))`.
- Produces: tabel `season_cost_lines(id uuid pk, sheet_id fk, name varchar150, cost_class varchar2 FC|VC, amount decimal(20,2) null, is_required bool, sort_order uint, active bool default true)` dengan CHECK `amount IS NULL OR amount >= 0`.
- Produces: tabel `customers(id uuid pk, type varchar10 consumer|partner, name varchar200, whatsapp varchar20 null, whatsapp_display varchar30 null, source varchar10 manual|import, record_state, version, timestamps(6))`, unique `(type, whatsapp)`.
- Produces: `sales.customer_id` (uuid null, fk customers) dan `sale_items.buyer_grade` (char1 null, CHECK `IN ('A','B','C')`).
- Semua tabel ada di satu migration supaya hanya ada satu langkah rollout. Migration juga mem-backfill `sequence`.

- [ ] **Step 1: Write failing tests** di `FinanceStandardCostTest`:
  - `test_sequence_backfill_breaks_ties_by_id`: dua musim GH sama, `start_date` sama → `sequence` 1 dan 2 sesuai urutan `id`. Panggil langsung fungsi backfill statis `StandardCostMigration::backfillSequences()` yang dipakai migration.
  - `test_new_season_gets_next_sequence`: membuat musim lewat `POST /api/v2/seasons` (helper `createMaster`) di GH yang sudah punya musim 1 → respons `sequence` = 2.
  - `test_ledger_skips_feed_without_greenhouse`: jumlah `change_feed` tidak bertambah setelah mutasi entity tanpa greenhouse (pakai Task 2; tandai `markTestIncomplete` sampai Task 2 ada, lalu aktifkan).
- [ ] **Step 2:** `./scripts/test-backend.ps1 -GuardOnly`, lalu `cd backend; ../.tools/php/php.exe vendor/phpunit/phpunit/phpunit --filter FinanceStandardCostTest` → FAIL (kolom tidak ada).
- [ ] **Step 3:** Tulis migration + `App\Domain\Finance\StandardCostMigration::backfillSequences(): void` (`DB` update per GH, urut `start_date, id`), ubah `MasterService` dan `MutationLedger`. `MasterService::publicRow('seasons')` ikut mengirim `sequence`.
- [ ] **Step 4:** Tes terfilter → PASS. Jalankan `MigrationPreflightTest` dan `FinanceMasterTest` → PASS.
- [ ] **Step 5:** Commit `feat(backend): migration 23 for season sequence, cost sheets and customers`.

### Task 2: Lembar biaya standar (service, API)

**Files:**
- Create: `backend/app/Domain/Finance/StandardCostCatalog.php`, `backend/app/Domain/Finance/SeasonCostSheetService.php`, `backend/app/Http/Controllers/V2/SeasonCostSheetController.php`
- Modify: `backend/routes/api_v2.php`, `backend/app/Domain/Cultivation/MasterService.php` (panggil `ensureFor` saat musim dibuat)
- Test: `backend/tests/Feature/FinanceStandardCostTest.php`

**Interfaces:**
- Produces: `StandardCostCatalog::REQUIRED`, berisi array berurutan `[['name'=>'Biaya penyusutan','cost_class'=>'FC'], ...7 baris]`.
- Produces: `SeasonCostSheetService::ensureFor(int $sequence): object`. Mengembalikan baris sheet, dan membuatnya bila belum ada: salin baris dan jumlah dari sheet `sequence-1`, bila tidak ada maka tujuh baris wajib dengan `amount` null. Tanpa ledger, karena dipanggil dalam transaksi pembuatan musim; tulis satu audit `season_cost_sheet` `create` dengan actor yang sama.
- Produces: `SeasonCostSheetService::publicSheet(object $sheet): array` dengan bentuk `{id, sequence, version, lines:[{id,name,cost_class,amount|null,is_required,sort_order}], totals:{FC:string|null, VC:string|null, TC:string|null, complete:bool}, greenhouse_count:int, open_greenhouse_names:[string]}`. Total `null` bila ada baris wajib kosong pada kelas itu.
- Produces: `SeasonCostSheetService::mutate(User $actor, array $op): array`. Hanya `update`; `entity_type: season_cost_sheet`. Ledger dengan `greenhouse_id => null`.
- Produces routes (semua `finance-manager`): `GET season-cost-sheets`, `GET season-cost-sheets/{id}`, `PATCH season-cost-sheets/{id}`, `GET season-cost-sheets/{id}/audit`. `GET season-cost-sheets?sequence=n` memanggil `ensureFor(n)` bila ada musim bernomor n.

- [ ] **Step 1: Failing tests:**
  - `test_sheet_totals_match_inofarm_table`: PATCH dengan FC 5806100.00/275000.00/1500000.00 dan VC 180000.00/1600000.00/350000.00/1300000.00 → `totals.FC = "7581100.00"`, `VC = "3430000.00"`, `TC = "11011100.00"`, `complete = true`.
  - `test_empty_required_amount_is_incomplete_not_zero`: satu baris wajib null → `totals.FC = null`, `complete = false`.
  - `test_required_line_cannot_be_renamed_or_removed`: rename → 422 dengan `field_errors` pada baris itu; hapus → 422.
  - `test_extra_line_can_be_added_and_removed`: tambah `Listrik pompa` VC 150000.00 → VC `3580000.00`; hapus → kembali `3430000.00`.
  - `test_duplicate_line_name_rejected_case_insensitive`: `sewa TANAH` sebagai baris tambahan → 422.
  - `test_update_rejected_when_any_season_of_sequence_closed`: GH1 Musim 1 ditutup → PATCH sheet 1 → 409/422 `season_closed`.
  - `test_new_sequence_copies_previous_sheet`: sheet 1 diisi, lalu GH1 membuat musim 2 → sheet 2 sudah ada dengan jumlah yang sama.
  - `test_worker_gets_403`: akun pekerja GET dan PATCH → 403.
  - `test_ledger_skips_feed_without_greenhouse` dari Task 1 diaktifkan.
- [ ] **Step 2:** Tes terfilter → FAIL.
- [ ] **Step 3:** Implementasi. Validasi payload di controller mengikuti `SaleController::perform` (`OperationInput::read`): `lines` array min:7 max:40; tiap baris `id` UUID, `name` string max:150 regex `\S`, `cost_class` in:FC,VC, `amount` nullable string desimal, `is_required` boolean. Penjumlahan memakai `bcadd` skala 2.
- [ ] **Step 4:** Tes terfilter → PASS.
- [ ] **Step 5:** Commit `feat(backend): season cost sheets shared by every greenhouse`.

### Task 3: Laporan musim berbasis biaya standar

**Files:**
- Modify: `backend/app/Domain/Finance/SeasonReportSources.php`, `backend/app/Domain/Finance/SeasonReportService.php` (coverage dan `cost_basis`)
- Modify tests: `backend/tests/Feature/FinanceSeasonReportTest.php`, `FinanceIntegrationFlowTest.php`, `FieldAlignmentTest.php`, dan setiap tes lain yang mengharapkan FC/VC dari alokasi (cari `'FC'` di `tests/Feature`).
- Test: `backend/tests/Feature/FinanceStandardCostTest.php`

**Interfaces:**
- Consumes: `SeasonCostSheetService::ensureFor`, `publicSheet` (Task 2).
- Produces: sumber jenis `season_cost_sheet` dengan `role: season_cost_basis`, `included: true`, `contribution: {FC: string, VC: string}`. `cost_allocation` dan `depreciation_allocation` menjadi `included: false`, `role: reference_only`, `reason: 'Biaya nyata; laporan memakai biaya standar.'`.
- Produces: preview dan snapshot memuat `cost_basis: "standard"` dan `cost_sheet: {id, sequence, version}`. Bila sheet tidak lengkap, FC/VC null dan alasan menyebut nama baris wajib yang kosong.

- [ ] **Step 1: Failing tests:**
  - `test_report_uses_standard_costs_not_allocations`: musim dengan alokasi FC 4999990.00 dan lembar lengkap (angka Task 2), plus penjualan lunas 15450000.00 / 500 kg → preview FC `7581100.00`, VC `3430000.00`, TC `11011100.00`, laba `4438900.00`.
  - `test_allocations_listed_as_reference_only`: sumber `cost_allocation` ada dengan `included: false`.
  - `test_incomplete_sheet_keeps_report_provisional`: Benih null → FC/VC/TC null, alasan memuat `Benih`.
  - `test_published_report_is_unchanged_by_cost_sheet_edit`: terbitkan laporan, PATCH sheet, GET `reports/{id}` → body dan hash identik dengan sebelum PATCH.
- [ ] **Step 2:** Tes terfilter → FAIL.
- [ ] **Step 3:** Ubah `collect()`: ambil `seasons.sequence`, panggil `ensureFor`, tambahkan sumber sheet, dan hentikan penjumlahan alokasi. Musim tanpa sequence → FC/VC null dengan alasan. Perbarui tes lama ke basis baru: ganti persiapan alokasi dengan PATCH sheet, lalu sesuaikan angka yang diharapkan. Jangan hapus tes yang memeriksa integritas sumber.
- [ ] **Step 4:** Seluruh backend: `./scripts/test-backend.ps1` → semua PASS.
- [ ] **Step 5:** Commit `feat(backend): season reports use standard cost sheets`.

### Task 4: Endpoint pemasukan dan ringkasan laba

**Files:**
- Modify: `backend/app/Domain/Finance/FieldReports.php`, `backend/app/Http/Controllers/V2/FieldReportController.php`, `backend/routes/api_v2.php`
- Test: `backend/tests/Feature/FinanceIncomeReportTest.php` (baru)

**Interfaces:**
- Consumes: `SeasonEvaluation` / `SeasonReportService` (fungsi hitung yang sama dengan preview, tanpa snapshot), `SeasonCostSheetService::publicSheet`.
- Produces: `GET reports/income?granularity=week|month|year&from=YYYY-MM-DD&until=YYYY-MM-DD[&greenhouse_id=]`. Bentuknya `{granularity, buckets:[{key, label, start, receipts, weight_kg, by_type:{consumer:{receipts,weight_kg}, partner:{...}, unspecified:{...}}}], totals:{...}}`. Bucket WIB; minggu mulai Senin, `key` = tanggal Senin. Maksimal 104 bucket (lebih → 422).
- Produces: `GET reports/profit-summary[?greenhouse_id=]`. Bentuknya `{open:{greenhouse_count, TR, TC, profit, rc, bep_price, bep_kg, sold_kg, complete}, by_sequence:[{sequence, greenhouse_count, TR, TC, profit, rc, complete}], by_greenhouse:[{greenhouse_id, name, sequence, TR, TC, profit, rc}]}`. Desimal sebagai string; nilai yang tidak bisa dihitung null.

- [ ] **Step 1: Failing tests:**
  - `test_income_month_buckets_split_by_type`: penjualan consumer 6300000.00 dan partner 18200000.00 di November 2026 → bucket `2026-11` receipts `24500000.00`, by_type sesuai.
  - `test_income_counts_untyped_sales_as_unspecified`: penjualan tanpa `customer_type` → `unspecified`.
  - `test_income_excludes_cancelled_sales`.
  - `test_week_bucket_starts_monday_wib`: penjualan `2026-11-23T00:30:00+07:00` (Senin) masuk bucket `2026-11-23`; `2026-11-22T23:59:00+07:00` masuk `2026-11-16`.
  - `test_profit_summary_open_seasons`: 2 GH Musim 1 terbuka, sheet TC 11011100.00, TR total 30000000.00 → `open.TC = "22022200.00"`, `profit = "7977800.00"`, `greenhouse_count = 2`.
  - `test_profit_summary_worker_403`.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implementasi. Pengelompokan dengan `DATE_ADD(paid_at, INTERVAL 7 HOUR)` seperti `cashFlow`. Minggu: `YEARWEEK(..., 3)` atau hitung tanggal Senin.
- [ ] **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat(backend): income buckets by buyer type and profit summary`.

### Task 5: Kontrak tahap 1

**Files:**
- Modify: `contracts/v2/openapi.json`, `contracts/v2/SEASON_EVALUATION.md`
- Create: `contracts/v2/STANDARD_COSTS.md`, `contracts/v2/season-cost-sheet-operation.schema.json`, fixture di `contracts/v2/fixtures/`

- [ ] **Step 1:** Tambah path dan schema untuk route Task 2–4. Dokumentasikan `cost_basis` dan alasan referensi.
- [ ] **Step 2:** `cd backend; ../.tools/php/php.exe vendor/phpunit/phpunit/phpunit --filter OpenApiParityTest` dan `./.tools/finance-contracts-venv/Scripts/python.exe scripts/check-finance-contracts.py` → PASS.
- [ ] **Step 3:** Commit `docs(contracts): standard cost sheets, income and profit summary`.

### Task 6: Android Biaya standar

**Files:**
- Create: `android/app/src/main/java/com/inofarm/jogja/feature/standardcost/StandardCostRepository.kt`, `StandardCostModel.kt`, `StandardCostViewModel.kt`, `StandardCostScreen.kt`
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt` (tujuan baru dari tab Keuangan), `FinanceRuntime.kt` (wiring)
- Test: `android/app/src/test/java/com/inofarm/jogja/feature/standardcost/StandardCostModelTest.kt`

**Interfaces:**
- Consumes: `FinanceApi.request(base, token, path, method, body, query)`; envelope dibangun seperti `AdminRepository`.
- Produces: `data class CostLine(val id:String, val name:String, val costClass:String, val amount:BigDecimal?, val required:Boolean, val sortOrder:Int)`.
- Produces: `data class CostSheet(val id:String, val sequence:Int, val version:Int, val lines:List<CostLine>, val greenhouseCount:Int)` dengan `fun total(costClass:String):BigDecimal?` (null bila ada baris wajib kelas itu yang kosong) dan `fun tc():BigDecimal?`.
- Produces: `fun CostSheet.withLine(line:CostLine):CostSheet`, `withoutLine(id)`, dan `withAmount(id, BigDecimal?)`. `withoutLine` pada baris wajib melempar `IllegalArgumentException`.
- Produces: `StandardCostRepository.load(sequence:Int?):CostSheet` dan `save(sheet:CostSheet):CostSheet` (PATCH dengan `base_version`; 409 dipetakan ke pesan konflik yang sudah ada).

- [ ] **Step 1: Failing JVM tests:**
  - `totals_match_inofarm_table`: FC `7581100.00`, VC `3430000.00`, TC `11011100.00`.
  - `blank_required_amount_gives_null_total`.
  - `required_line_cannot_be_removed`.
- [ ] **Step 2:** `./scripts/build-android.ps1 -JavaHome "C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot" -Tasks ':app:testDebugUnitTest'` → FAIL.
- [ ] **Step 3:** Model dan repository. Layar sesuai artboard `BiayaStandar` dan `TambahBiaya`:
  - hero forest TC per GH dengan bar FC/VC;
  - pemilih Musim n dan teks `Berlaku sama untuk N GH aktif`;
  - kartu FC dan VC dengan baris wajib bergembok (`Wajib`);
  - tombol garis putus `Tambah biaya tetap` / `Tambah biaya variabel`;
  - sheet tambah (jenis, nama, jumlah per GH per musim);
  - ketuk baris → ubah jumlah;
  - Simpan → Ringkasan → Simpan.
  - Keadaan memuat, gagal dan "Butuh koneksi untuk mengubah biaya standar" ditulis jelas.
- [ ] **Step 4:** JVM tests PASS; `./scripts/build-android.ps1 -JavaHome ...` (assemble, test, lint) PASS dengan lint 0 error.
- [ ] **Step 5:** Commit `feat(android): standard cost sheet screen`.

## Tahap 2 (wajib): layar Keuangan, ikon mata, ringkasan investor

### Task 7: Layar Keuangan dan grafik

**Files:**
- Create: `android/app/src/main/java/com/inofarm/jogja/feature/financehome/FinanceHomeRepository.kt`, `IncomeChartModel.kt`, `FinanceHomeViewModel.kt`, `FinanceHomeScreen.kt`, `IncomeBarChart.kt`, `FinanceVisibilityPreference.kt`
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt` (tab 4 menampilkan `FinanceHomeScreen`; isi `FinancialEntryScreen` lama ditampilkan di dalam bagian biaya nyata yang bisa disembunyikan), `feature/ManagerSheets.kt` (baris Biaya di sheet Catat mengikuti preferensi)
- Test: `android/app/src/test/java/com/inofarm/jogja/feature/financehome/IncomeChartModelTest.kt`, `FinanceVisibilityPreferenceTest.kt`

**Interfaces:**
- Consumes: `GET reports/income`, `GET reports/profit-summary` (Task 4).
- Produces: `data class IncomeBar(val key:String, val label:String, val consumer:BigDecimal, val partner:BigDecimal, val unspecified:BigDecimal, val consumerKg:BigDecimal, val partnerKg:BigDecimal)`. Fungsi bantu: `total()`, `share(type)`, dan `fun compareTo(previous:IncomeBar?):String`. Hasilnya `"naik RpX dari Okt"`, `"turun RpX dari Okt"`, atau `"<periode> pertama di grafik"`.
- Produces: `fun barHeights(bars:List<IncomeBar>, maxPx:Float):List<Triple<Float,Float,Float>>`, yaitu tinggi bagian partner, consumer dan unspecified. Minimal 3 px bila nilainya > 0.
- Produces: `class FinanceVisibilityPreference(storage)` dengan `showRealCosts(scopeKey):Boolean` (default **false**) dan `setShowRealCosts(scopeKey, Boolean)`. Penyimpanan `SharedPreferences("inofarm_display")` seperti `ThemePreference`.

- [ ] **Step 1: Failing JVM tests:**
  - `month_total_and_split`: Nov consumer 6300000, partner 18200000 → total 24500000, partner share 74.
  - `compare_text_rises_from_previous`: Okt 15800000 → `"naik Rp8.700.000 dari Okt"`.
  - `heights_scale_to_tallest_bar`.
  - `real_costs_hidden_by_default` dan `preference_is_per_account_scope`.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Layar sesuai artboard `Main`:
  - segmen Minggu/Bulan/Tahun;
  - hero forest dengan nominal periode terpilih dan teks perbandingan;
  - legenda Mitra (`#D4E4B7`) dan Perorangan (`#7E9481`, sudah lolos kontras ≥3:1 di atas `#132820`);
  - `IncomeBarChart` dengan Compose `Canvas` dan target sentuh per batang ≥48 dp. Semantik per batang: `Role.Button`, `selected`, dan label `"Nov 2026: total 24,5 jt, mitra 18,2 jt, perorangan 6,3 jt"`;
  - panel rincian (Mitra, Perorangan, total bobot, rata-rata Rp/kg);
  - kartu "Per tipe pembeli";
  - kartu "Laba musim berjalan" dari `profit-summary.open`, dengan chip `Sementara` bila `complete = false`;
  - tiga pintasan;
  - ikon mata di app bar (`aria`: `Tampilkan biaya nyata` / `Sembunyikan biaya nyata`).
  - Unspecified digambar sebagai segmen ketiga `#4A6456` hanya bila > 0, dengan legenda `Tanpa tipe`.
  - Tidak ada animasi berulang. Perubahan tinggi batang 240 ms dan mengikuti skala animasi sistem.
- [ ] **Step 4:** JVM tests dan build lengkap PASS.
- [ ] **Step 5:** Commit `feat(android): finance home with stacked income chart and real-cost toggle`.

### Task 8: Ringkasan investor (Android)

**Files:**
- Create: `android/app/src/main/java/com/inofarm/jogja/feature/financehome/InvestorSummaryScreen.kt`
- Test: tambah ke `IncomeChartModelTest.kt`: `investor_totals_sum_sequences`

**Interfaces:**
- Consumes: `profit-summary.by_sequence`, `by_greenhouse`; skenario investasi lewat repository investasi yang sudah ada (`feature/investments`).

- [ ] **Step 1:** Failing test `investor_totals_sum_sequences`: dua musim TR 50400000/61800000, TC 44044400/44044400 → laba total `24111200`, R/C `1,27`.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Layar sesuai artboard `Investor`, **tanpa kurva laba kumulatif** dan **tanpa tombol unduh**:
  - hero laba tercatat;
  - batang penerimaan vs biaya per musim;
  - tabel per GH dengan baris total;
  - bagian Kelayakan investasi. Bila ada skenario, tampilkan NPV/IRR/PP dari skenario yang sudah ada. Bila tidak ada, tampilkan teks kosong dan tombol `Isi skenario investasi` ke layar investasi yang sudah ada.
- [ ] **Step 4:** PASS + build.
- [ ] **Step 5:** Commit `feat(android): investor summary`.

**Garis potong:** tahap 1 dan 2 harus selesai, terbukti di emulator terhadap Laravel nyata dan `inofarm_test`, sebelum tahap 3 dimulai.

## Tahap 3: pembeli perorangan dan impor Excel

### Task 9: Backend pembeli dan impor

**Files:**
- Create: `backend/app/Domain/Finance/CustomerService.php`, `backend/app/Domain/Finance/WhatsappNumber.php`, `backend/app/Http/Controllers/V2/CustomerController.php`
- Modify: `backend/app/Domain/Finance/SaleService.php`, `backend/app/Http/Controllers/V2/SaleController.php`, `backend/routes/api_v2.php`, `contracts/v2/openapi.json`, `contracts/v2/SALES.md`
- Create: `contracts/v2/CUSTOMERS.md`, `contracts/v2/customer-operation.schema.json`
- Test: `backend/tests/Feature/FinanceCustomerTest.php`

**Interfaces:**
- Produces: `WhatsappNumber::normalize(?string $raw): ?string`. Contoh `"0812-3456 7890"`, `"+62 812 3456 7890"` dan `"6281234567890"` semuanya menjadi `"6281234567890"`. Hasil dengan panjang di luar 10–15 digit atau berisi huruf → null.
- Produces routes (`finance-manager`):
  - `GET customers?type=&q=&cursor=&limit=`: cari `name` LIKE atau `whatsapp` LIKE digit `q`; setiap baris membawa `purchase_count`, `total_kg`, `last_purchase_at`;
  - `GET customers/{id}`, `POST customers`, `PATCH customers/{id}`, `POST customers/{id}/cancel`, `GET customers/{id}/audit`;
  - `POST customers/import` dengan body `{dry_run:bool, type:"consumer", rows:[{row:int, name:string, whatsapp:string|null}]}` (maks. 1.000 baris; batas request 64 KiB khusus route ini, alasannya ditulis di kode). Hasilnya `{summary:{created, existing, invalid}, rows:[{row, status:"created"|"existing"|"invalid", customer_id|null, reason|null}]}`.
- Produces di sale: payload menerima `customer:{id, type:"consumer"|"partner", name, whatsapp|null}` (opsional). Server membuat pembeli bila `id` baru, lalu mengisi `sales.customer_id`, `customer_type` dan `customer_name` (salinan).

- [ ] **Step 1: Failing tests:**
  - `test_import_dedupes_whatsapp_formats`: tiga baris dengan format WA di atas → 1 created, 2 existing.
  - `test_import_dry_run_writes_nothing`.
  - `test_import_blank_name_invalid` dan `test_import_bad_number_imports_without_number_when_requested`. Baris dengan `whatsapp` null dan nama terisi → created.
  - `test_sale_creates_new_customer_inline` dan `test_sale_reuses_existing_customer`.
  - `test_customer_search_by_name_and_digits`.
  - `test_worker_cannot_read_customers` → 403 di semua route.
- [ ] **Step 2–4:** FAIL, implementasi, PASS + `OpenApiParityTest` + checker kontrak.
- [ ] **Step 5:** Commit `feat(backend): customers with excel import and inline sale buyer`.

### Task 10: Android pembeli, impor dan form perorangan

**Files:**
- Create: `android/app/src/main/java/com/inofarm/jogja/feature/customers/SpreadsheetReader.kt`, `CustomerRepository.kt`, `CustomerListScreen.kt`, `CustomerImportScreen.kt`, `CustomerPicker.kt`, `SalesByTypeScreen.kt`
- Modify: `feature/sales/SaleEntryScreen.kt`, `SaleDraft.kt`, `SaleEntryViewModel.kt` (bagian Pembeli: cari/pilih/baru; `customer` masuk payload), `feature/ManagerSheets.kt` (sheet Siapa pembelinya)
- Test: `android/app/src/test/java/com/inofarm/jogja/feature/customers/SpreadsheetReaderTest.kt`, `WhatsappDisplayTest.kt`

**Interfaces:**
- Produces: `object SpreadsheetReader { fun read(name:String, input:InputStream, maxBytes:Long = 2_000_000, maxRows:Int = 1000): Sheet }` dengan `data class Sheet(val headers:List<String>, val rows:List<List<String>>)`. `.xlsx` dibaca lewat `ZipInputStream` + `XmlPullParser` (`xl/sharedStrings.xml`, worksheet pertama dari `xl/workbook.xml`); `.csv` dipisah `,` atau `;` (deteksi dari baris pertama), mendukung tanda kutip.
- Produces: `fun guessColumns(headers:List<String>): Pair<Int?,Int?>`, yaitu indeks kolom nama dan WA. Nama: `nama`, `pembeli`, `customer`. WA: `wa`, `whatsapp`, `hp`, `telepon`, `telp`, `no`.
- Produces: `CustomerRepository.search(q, type)`, `importRows(rows, dryRun)` (dikirim dalam potongan 200 baris), dan `list(type, cursor)`.

- [ ] **Step 1: Failing JVM tests:**
  - `reads_xlsx_shared_strings_first_sheet`: fixture xlsx kecil di `src/test/resources/customers.xlsx` dibuat di tes dengan `ZipOutputStream`;
  - `reads_csv_semicolon_and_quotes`;
  - `guesses_name_and_phone_columns` (`"Nama Pembeli"`, `"No HP"` → 0, 1);
  - `rejects_file_over_limit`.
- [ ] **Step 2–4:** FAIL, implementasi sesuai artboard `FormPerorangan` (mode cari/terpilih/baru), `DaftarPembeli`, `ImporExcel` (pilih file lewat `ActivityResultContracts.OpenDocument` dengan mime xlsx dan csv, pencocokan kolom, pratinjau dry-run, daftar "Perlu dicek", `Impor N pembeli`), `Penjualan` (segmen Perorangan/Mitra/Retail, Retail = `Belum tersedia`) dan `PilihPembeli`. PASS + build.
- [ ] **Step 5:** Commit `feat(android): buyer list, excel import and buyer picker in sale form`.

## Tahap 4 (bila sempat): penjualan mitra per kg

### Task 11: Grade mitra

**Files:**
- Modify: `backend/app/Http/Controllers/V2/SaleController.php` (aturan `items.*` menerima `buyer_grade` nullable in:A,B,C; `distinct` per `harvest_item_id` diganti dengan pemeriksaan pasangan unik di service), `backend/app/Domain/Finance/SaleService.php`, `contracts/v2/openapi.json`, `contracts/v2/SALES.md`
- Modify: `android/.../feature/sales/*` (form mitra: per varietas tiga baris grade kg + harga per kg)
- Test: `backend/tests/Feature/FinanceSaleTest.php`, `android/.../feature/sales/SaleDraftTest.kt`

- [ ] **Step 1: Failing tests:**
  - `test_partner_sale_three_grades_same_harvest_item`: A 60 kg × 30000, B 50 kg × 24000, C 25 kg × 18000 dari satu rincian Layak → total `3450000.00`, saldo berkurang 135 kg.
  - `test_buyer_grade_rejected_for_consumer` → 422.
  - `test_duplicate_grade_for_same_item_rejected` → 422.
  - `test_grade_lines_cannot_exceed_balance` → 409/422 seperti aturan saldo yang ada.
  - JVM: `partner_total_sums_grade_lines`.
- [ ] **Step 2–4:** FAIL, implementasi sesuai artboard `FormMitra`, tetapi dengan isian **kg dan harga per kg** (bukan per buah) dan tanpa jumlah buah. PASS + build + kontrak.
- [ ] **Step 5:** Commit `feat: partner sales by buyer grade per kg`.

## Penutup

### Task 12: Bukti dan konteks

**Files:**
- Create: `docs/context/FINANCE_STANDARD_COST_EVIDENCE_<tanggal>.md`
- Modify: `CLAUDE.md` (paragraf status baru di atas), `docs/context/README.md`, `DESIGN.md` (bagian Keuangan dan grafik), `docs/context/GAP_ANALYSIS.md`

- [ ] **Step 1:** `./scripts/test-backend.ps1`, `./scripts/build-android.ps1 -JavaHome ...`, `python scripts/check-finance-context.py`, dan checker kontrak. Semua PASS; catat jumlah tes.
- [ ] **Step 2:** Di emulator `Inofarm_Botanical_API30` terhadap Laravel nyata + `inofarm_test`:
  - isi biaya standar dengan angka Inofarm;
  - catat penjualan perorangan dan mitra;
  - ketuk batang grafik;
  - buka ringkasan investor;
  - toggle ikon mata;
  - impor satu file .xlsx.
  - Ambil screenshot dan cek SQL bahwa TC laporan = 11011100.00 per GH dan alokasi tidak terjumlah.
- [ ] **Step 3:** Tulis bukti dengan batas yang jujur: emulator, bukan perangkat fisik; migration 23 belum diterapkan ke `inofarm_finance_local` kecuali pengguna mengizinkan dengan backup.
- [ ] **Step 4:** Commit `docs(context): evidence for standard costs and buyer sales`, push branch ke `github`, buka PR. Jangan merge.
