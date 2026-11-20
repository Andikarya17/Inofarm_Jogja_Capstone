# Masukan Inofarm: catat kegiatan dan panen, rencana pengerjaan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Form catat kegiatan dan panen mengikuti masukan lapangan Inofarm 20 November 2026: semai, nama musim otomatis, pop up mulai semai, PPM opsional, dua pengukuran, Nutrisi AB Mix, panen layak/cacat tanpa grade dan jam, dan grafik pola panen.

**Architecture:**
- **Server (Laravel):** menerima pengukuran hanya EC, nama nutrisi/satuan ketikan, dan rincian panen hanya dengan `quality`. Grade sistem "Layak"/"Cacat" dibuat otomatis, sehingga skema dan penjualan tidak berubah.
- **Android (Compose):** mengubah draf `EntryDraft`/`HarvestDraft` dan form-nya, lalu menggambar grafik dari cache panen di perangkat dengan `Canvas`.
- Tidak ada migration, dependency baru, atau perubahan modul penjualan/keuangan.

**Tech Stack:** Kotlin, Jetpack Compose Material3, JUnit; PHP Laravel 13, PHPUnit, MySQL 8 (`inofarm_test`).

**Spec:** `docs/superpowers/specs/2026-11-20-catat-kegiatan-panen-masukan-inofarm-design.md`

## Global Constraints

- Branch kerja `inofarm-field-feedback-recording`. Commit hanya file task, tanpa `.env`, dump, log, build atau `outputs/`.
- Penjualan, keuangan, laporan dan menu Grade di Kelola **tidak diubah**. `SaleService`, `SaleController`, `SaleEntryScreen` tidak disentuh.
- Tidak ada migration baru. Tidak ada dependency baru. Tidak ada library grafik.
- Tes backend hanya terhadap MySQL efektif `inofarm_test`, dengan guard dulu (`./scripts/test-backend.ps1 -GuardOnly`). Jangan menyentuh `inofarm_finance_local`.
- Emulator hanya `Inofarm_Botanical_API30`, mode terang.
- Teks yang tetap: "Tanggal semai", "Tanggal pindah tanam (opsional)", "Jumlah tanaman".
- Format nama musim: `<nama greenhouse> Musim <n>`.
- Satuan nutrisi tetap: `mL`, `L`, `gram` (tanpa mg).
- Nama grade sistem: `Layak` (quality `saleable`), `Cacat` (quality `defective`).
- Jam panen baru dikirim sebagai `12:00` WIB dan tidak tampil.
- Pop up: judul "Greenhouse belum dimulai semai", isi "Apakah mau mulai semai?", tombol "Tidak" dan "Lanjut".
- Desimal JSON tetap string. Jumlah buah integer atau null; kosong bukan nol.

## Review Focus

1. **Draf entri lama** (disimpan sebelum pembaruan, dengan kunci `ec`/`ppm`/`moment` tunggal) harus terbuka utuh sebagai satu blok pengukuran. Tes di Task 5.
2. **Koreksi panen lama** yang rinciannya memakai grade A/B tanpa `quality` harus tetap bisa disimpan dengan grade aslinya. Tidak boleh dipaksa layak/cacat atau ditolak. Tes di Task 9.
3. **Greenhouse yang sudah punya grade "layak"** (huruf kecil, aktif) harus memakai grade itu, bukan membuat duplikat. Tes di Task 8.
4. **Baris lama yang hanya berisi PPM** (nutrisi lama) tetap muncul di `measurements/latest` dan Beranda setelah deteksi pindah ke EC. Tes di Task 4.
5. **Membatalkan pilihan momen kedua** menghapus blok kedua saja. Nilai blok pertama tidak bergeser atau tertukar. Tes di Task 5.

Perintah yang dipakai berulang:
- Guard: `./scripts/test-backend.ps1 -GuardOnly`
- PHPUnit terfokus (dari `backend/`): `../.tools/php/php.exe vendor/phpunit/phpunit/phpunit --filter <Nama>`. Pakai `php` bila `.tools` tidak ada.
- JVM terfokus: `./scripts/build-android.ps1 -JavaHome "C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot" -Tasks ':app:testDebugUnitTest','--tests','com.inofarm.jogja.<Kelas>'`
- Penuh: `./scripts/test-backend.ps1`, `./scripts/build-android.ps1 -JavaHome <jdk>`, `./.tools/finance-contracts-venv/Scripts/python.exe scripts/check-finance-contracts.py`

---

### Task 1: Teks semai, Waktu, Satuan, Stadia/Fase, satuan EC

**Files:**
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/admin/GreenhouseWizard.kt` (baris 191, 209, 222-223, 229, 255, 264-265)
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/context/RecordingTarget.kt:49-56`
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/ManagerSheets.kt` (34, 52, 133-134)
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/context/HomeContextCard.kt` (117, 185)
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/activities/EntryForm.kt` (119, 132, 141, 155, 160, 239-241, 277, 283)
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/activities/domain/ActivityDraft.kt:15` (label `STAGE`)
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/activities/domain/EntryDraft.kt:126` (`scheduleSection`)
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/harvest/HarvestScreen.kt:83,191,200` ("Waktu panen" → "Waktu")
- Test: `android/app/src/test/java/com/inofarm/jogja/RecordingTargetTest.kt`

**Interfaces:**
- Produces: tidak ada API baru. Kalimat yang harus persis sama:
  - `RecordingTarget.caption`: manager → `"Belum mulai semai. Mulai semai dulu untuk mencatat."`
  - `RecordingTarget.caption`: pekerja → `"Belum mulai semai. Minta pengelola memulai semai di greenhouse ini."`
  - `RecordingTarget.caption`: `NotSown` → `"Semai dimulai <d MMM yyyy>. Catatan bisa dibuat mulai tanggal itu."`
  - `ActivityType.STAGE.label` → `"Stadia/Fase"`
  - `scheduleSection("stage")` → `"Stadia/Fase"`, `scheduleSection("nutrition")` → `"Nutrisi AB Mix"`

- [ ] **Step 1: Ubah tes caption di `RecordingTargetTest`** supaya mengharapkan tiga kalimat di atas (cari assertion yang memuat "mulai tanam" / "Tanam dimulai").
- [ ] **Step 2: Jalankan** `--tests 'com.inofarm.jogja.RecordingTargetTest'`. Expected: FAIL pada kalimat lama.
- [ ] **Step 3: Ganti teks** sesuai tabel di spec bagian 1, plus:
  - `EntryForm`: "Waktu kejadian" → "Waktu", "Dasar umur" → "Satuan", hapus `suffix = "mS/cm"` dari field EC (Ringkasan tetap memakai satuan), "Nutrisi" → "Nutrisi AB Mix", "Stadia" → "Stadia/Fase", "Stadia saat ini (opsional)" → "Stadia/Fase saat ini (opsional)"
  - `ManagerSheets` detail panen "Hasil per grade" → "Layak dan cacat per varietas"
  - `FormCanopy.kt:252` dan `FinanceRoot.kt:1482` **tidak** diubah, karena dipakai form keuangan
- [ ] **Step 4: Jalankan** `--tests 'com.inofarm.jogja.RecordingTargetTest'`. Expected: PASS. Lalu `grep -rn "ulai tanam\|Waktu kejadian\|Dasar umur" android/app/src/main` hanya menyisakan nama variabel, tag atau form keuangan.
- [ ] **Step 5: Commit** `feat(android): Inofarm wording for sowing, time, age unit and stage`

### Task 2: Nama musim otomatis

**Files:**
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/admin/GreenhouseWizard.kt` (`WizardRules`, state `season` baris 116, field baris 260)
- Modify: pemanggil `GreenhouseWizardHost`, untuk meneruskan nama musim greenhouse dari `CultivationUiState.seasons`
- Test: `android/app/src/test/java/com/inofarm/jogja/GreenhouseWizardRulesTest.kt`

**Interfaces:**
- Produces: `WizardRules.seasonName(greenhouseName: String, existing: List<String>): String`

- [ ] **Step 1: Tes**
```kotlin
@Test fun seasonNameFollowsGreenhouseAndCountsUp() {
    assertEquals("GH1 Musim 1", WizardRules.seasonName(" GH1 ", emptyList()))
    assertEquals("GH1 Musim 3", WizardRules.seasonName("GH1", listOf("GH1 Musim 1", "Musim lama")))
    assertEquals("GH1 Musim 4", WizardRules.seasonName("GH1", listOf("gh1 musim 3", "x", "y")))
}
```
- [ ] **Step 2: Jalankan** `--tests 'com.inofarm.jogja.GreenhouseWizardRulesTest'`. Expected: FAIL (fungsi belum ada).
- [ ] **Step 3: Implementasi**
  - `seasonName`: `n` mulai dari `existing.size + 1`, naik selama `"<trim nama> Musim n"` sudah ada di `existing` (tanpa beda huruf besar/kecil).
  - Wizard: field "Nama musim" diganti `FarmFact("Nama musim", season)`. `season` dihitung ulang dari `seasonName(name, seasonNamesOfGh)` selama `!seasonDone`. Kalau `plantIn?.seasonName` tidak kosong (musim terbuka dipakai ulang), nama itu yang tampil.
  - Greenhouse baru memakai `existing = emptyList()`.
- [ ] **Step 4: Jalankan tes yang sama.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(android): season name follows "<GH> Musim <n>"`

### Task 3: Pop up greenhouse belum dimulai semai

**Files:**
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/ManagerSheets.kt` (`RecordSheet`, baris 62-105)
- Test: `android/app/src/androidTest/java/com/inofarm/jogja/FinanceEntryUiTest.kt` (tambah satu tes)

**Interfaces:**
- Consumes: `WorkerRecordTarget.notPlanted`, `WorkerRecordTarget.onStartPlanting` (sudah ada; `onStartPlanting` hanya non-null untuk pengelola).
- Produces: tag `start-sowing-dialog`, `start-sowing-no`, `start-sowing-continue`.

- [ ] **Step 1: Tes UI** `managerPickingActivityOnUnsownGreenhouseAsksToStartSowing`:
  - fixture greenhouse tanpa kelompok tanam
  - buka Catat, pilih greenhouse itu, tekan tile Kegiatan
  - assert `start-sowing-dialog` tampil dengan teks "Greenhouse belum dimulai semai" dan "Apakah mau mulai semai?"
  - tekan `start-sowing-no`: dialog hilang, sheet Catat masih ada
  - ulangi, tekan `start-sowing-continue`: wizard tampil dengan judul "Mulai semai"
- [ ] **Step 2: Jalankan** tes itu di `Inofarm_Botanical_API30`. Expected: FAIL (dialog tidak ada).
- [ ] **Step 3: Implementasi di `RecordSheet`.**
  - `pick` dicegat: kalau `worker?.onStartPlanting != null`, greenhouse terpilih `notPlanted`, dan pilihan ada di `FieldChoices`, set state `askStart = ghId` dan jangan buka form.
  - Tampilkan `AlertDialog` dengan teks di Global Constraints. "Lanjut" memanggil `onStartPlanting(ghId)`.
  - Pekerja tidak berubah.
- [ ] **Step 4: Jalankan tes yang sama.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(android): ask to start sowing before recording on an unsown greenhouse`

### Task 4: Server: EC wajib, PPM opsional, urutan sesudah inject

**Files:**
- Modify: `backend/app/Domain/Activities/ActivityService.php:178-183`
- Modify: `backend/app/Domain/Cultivation/ManagerHome.php:45,162`
- Modify: `backend/app/Http/Controllers/V2/ActivityController.php:29-31`
- Modify: `backend/app/Http/Controllers/V2/ChangeFeedController.php:60`
- Modify: `contracts/v2/MEASUREMENTS.md`, `contracts/v2/activity-operation.schema.json`, `contracts/v2/openapi.json` (deskripsi `manual_ppm` opsional untuk measurement)
- Test: `backend/tests/Feature/RecordPerGreenhouseTest.php`

**Interfaces:**
- Produces: "baris berisi bacaan" = `manual_ec IS NOT NULL OR manual_ppm IS NOT NULL`. Bila `occurred_at` sama, urutan terbaru: `after_injection` lebih dulu, lalu `id DESC`.

- [ ] **Step 1: Tes** `test_ec_only_measurement_is_accepted_and_after_injection_wins_the_latest`:
  - kirim measurement `manual_ppm => null`, `manual_ec => '1.6'` dengan `observations.measurement_moment => 'before_injection'`. Assert 201.
  - kirim measurement kedua dengan `entry_batch_id` dan `occurred_at` sama, `manual_ec => '2.1'`, `after_injection`. Assert 201.
  - `GET /api/v2/measurements/latest?season_id=…` → `data.manual_ec` = `'2.100000'` (ikuti format desimal respons yang ada)
  - `GET /api/v2/manager-home` memuat pengukuran greenhouse itu
  - kirim measurement tanpa `manual_ec` → 422 dengan `field_errors.manual_ec`
- [ ] **Step 2: Tes** `test_legacy_ppm_only_rows_still_count_as_readings`: nutrition tanpa `entry_batch_id` dengan `manual_ppm => '900'`, `manual_ph => '6'`, `manual_ec => null`, lalu `measurements/latest` mengembalikan baris itu.
- [ ] **Step 3: Jalankan** guard, lalu `--filter RecordPerGreenhouseTest`. Expected: tes baru FAIL.
- [ ] **Step 4: Implementasi.**
  - `ActivityService`: measurement hanya mewajibkan `manual_ec`, dengan pesan `'Isi EC; kosong bukan nol.'`.
  - Tiga query `whereNotNull('manual_ppm')` menjadi `where(fn ($q) => $q->whereNotNull('manual_ec')->orWhereNotNull('manual_ppm'))` (dengan prefix tabel sesuai query).
  - `latestPerSeason` dan `latest` menambah `ORDER BY (JSON_UNQUOTE(JSON_EXTRACT(observations, '$.measurement_moment')) = 'after_injection') DESC` setelah `occurred_at DESC`.
- [ ] **Step 5: Jalankan** `--filter RecordPerGreenhouseTest`, `--filter ManagerHomeTest`, `--filter FinanceMeasurementTest`, `--filter OpenApiParityTest`. Expected: PASS.
- [ ] **Step 6: Commit** `feat(backend): EC-only measurements and after-injection first on ties`

### Task 5: Android: PPM opsional dan dua blok pengukuran

**Files:**
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/activities/domain/EntryDraft.kt`
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/activities/domain/ActivityDraft.kt:58-61`
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/activities/domain/ManualMeasurement.kt:22-41`, dan pemakai `ppm` (`WorkerGreenhouseCards.kt:85`, `MeasurementDashboard.kt`)
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/activities/EntryForm.kt` (bagian Pengukuran dan Ringkasan)
- Test: `android/app/src/test/java/com/inofarm/jogja/EntryDraftTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class MeasurementDraft(val moment: String = "", val ec: String = "", val ppm: String = "", val ph: String = "", val waterTemperature: String = "") {
    val filled: Boolean // any of ec, ppm, ph, waterTemperature not blank
}
// EntryDraft: field ec/ppm/ph/waterTemperature/moment diganti
val measurements: List<MeasurementDraft> = listOf(MeasurementDraft())
fun withMoments(selected: Set<String>): EntryDraft
```
- `withMoments(emptySet())` → satu blok `moment=""`. `setOf(x)` → satu blok `moment=x`. Dua momen → `[before_injection, after_injection]`.
- Nilai dipertahankan per momen. Blok yang tidak dipilih lagi dibuang. Saat beralih dari satu blok tanpa momen, nilainya pindah ke blok pertama yang dipilih.
- Kunci section: blok indeks 0 → `"measurement"`, blok indeks 1 → `"measurement:after_injection"`.
- Kunci error: `"measurements.$i.manual_ec"`, `"measurements.$i.manual_ph"`, dan seterusnya.
- `ManualMeasurement.ppm: String?`; ditambah `moment: String?`, diisi dari `observations.measurement_moment`.

- [ ] **Step 1: Tes** (ganti `ecAndPpmArePairedAndSprayNeedsVarietyTimeAndMaterial` bagian EC/PPM):
```kotlin
@Test fun ecIsRequiredButPpmIsOptional() {
    assertTrue("measurements.0.manual_ec" in base().copy(measurements = listOf(MeasurementDraft(ppm = "900"))).validation())
    assertTrue(base().copy(measurements = listOf(MeasurementDraft(ec = "1,8"))).validation().isEmpty())
}
@Test fun beforeAndAfterInjectionBecomeTwoMeasurementRecordsOfOneBatch() {
    val both = base().withMoments(setOf("before_injection", "after_injection")).let {
        it.copy(measurements = listOf(it.measurements[0].copy(ec = "1,6"), it.measurements[1].copy(ec = "2,1", ppm = "1000")))
    }
    val rows = both.sections().filter { it.type == ActivityType.MEASUREMENT }
    assertEquals(listOf("before_injection", "after_injection"), rows.map { it.measurementMoment })
    assertEquals(setOf(both.id), rows.map { it.entryBatchId }.toSet())
    assertEquals(2, rows.map { it.id }.toSet().size)
    val dropped = both.withMoments(setOf("before_injection"))
    assertEquals(listOf(MeasurementDraft("before_injection", ec = "1,6")), dropped.measurements)
}
@Test fun anEntryDraftSavedBeforeTwoMomentsReopensAsOneBlock() {
    val legacy = JSONObject().put("_local_form", JSONObject().put("entry", true).put("id", "88888888-8888-4888-8888-888888888888")
        .put("greenhouse_id", gh).put("season_id", season).put("date", "2026-11-17").put("time", "08:00")
        .put("ec", "1,8").put("ppm", "900").put("ph", "6").put("water_temperature", "").put("moment", "after_injection"))
    assertEquals(listOf(MeasurementDraft("after_injection", "1,8", "900", "6", "")), EntryDraft.from(legacy).measurements)
}
```
Tambahkan juga round-trip `EntryDraft.from(two.localJson()) == two` untuk dua blok.
- [ ] **Step 2: Jalankan** `--tests 'com.inofarm.jogja.EntryDraftTest'`. Expected: FAIL (tipe belum ada).
- [ ] **Step 3: Implementasi.**
  - `localJson` menulis `"measurements"` (array objek `moment/ec/ppm/ph/water_temperature`).
  - `from` membaca `"measurements"`, atau kunci tunggal lama bila array tidak ada.
  - `ActivityDraft.validation` untuk MEASUREMENT tidak lagi mewajibkan PPM.
  - `ManualMeasurement.parse` mensyaratkan EC untuk baris baru dan tetap membaca baris lama yang hanya berisi PPM. Urutan "terakhir" di perangkat sama dengan server (tie → `after_injection`).
  - UI: chip "Diukur" pilihan ganda memanggil `withMoments`. Tiap blok punya judul momennya bila ada dua.
  - Membatalkan momen yang bloknya berisi angka meminta konfirmasi dengan pola konfirmasi yang sudah ada.
  - Teks bantuan: "PPM, pH dan suhu air opsional. Kosongkan semua bila tidak mengukur."
- [ ] **Step 4: Jalankan** `--tests 'com.inofarm.jogja.EntryDraftTest'` lalu seluruh `:app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(android): optional PPM and before/after injection readings in one entry`

### Task 6: Server: nutrisi ketikan dan satuan tetap

**Files:**
- Create: `backend/app/Domain/Activities/NutritionCatalog.php`
- Modify: `backend/app/Domain/Activities/ActivityService.php:150-166,239-249`
- Modify: `backend/app/Http/Controllers/V2/ActivityController.php:121-125`
- Modify: `contracts/v2/activity-operation.schema.json`, `contracts/v2/openapi.json`, `contracts/v2/ACTIVITY_SYNC.md`
- Test: `backend/tests/Feature/RecordPerGreenhouseTest.php`

**Interfaces:**
- Produces:
```php
final class NutritionCatalog {
    public const AB_MIX = 'AB Mix';
    public const UNITS = ['mL' => 'volume', 'L' => 'volume', 'gram' => 'mass'];
    public function material(User $actor, string $greenhouseId, string $name): string; // purpose 'nutrition', per greenhouse only
    public function unit(User $actor, string $greenhouseId, string $name): string;     // name in UNITS; reuses an active unit with the same name (case-insensitive)
}
```
- Wire: `materials.*` menerima `unit_name` (`nullable|in:mL,L,gram`). `material_name` kini sah untuk `nutrition` maupun `spray`.

- [ ] **Step 1: Tes** `test_nutrition_names_and_fixed_units_are_typed_and_reused`:
  - nutrition dalam batch dengan `materials => [['material_name' => 'AB Mix', 'unit_name' => 'mL', 'quantity' => '500'], ['material_name' => 'Kalsium', 'unit_name' => 'gram', 'quantity' => '0.5']]` → 201
  - `materials` greenhouse itu punya tepat satu `AB Mix` dan satu `Kalsium` dengan `purpose = 'nutrition'`
  - kirim ulang isian baru dengan `'ab mix'` dan `'ML'` → tidak ada baris material/unit baru
  - greenhouse lain tidak mendapat `Kalsium`
  - `unit_name => 'mg'` → 422
- [ ] **Step 2: Jalankan** `--filter RecordPerGreenhouseTest`. Expected: FAIL.
- [ ] **Step 3: Implementasi.**
  - `NutritionCatalog`: pola `SprayMaterialCatalog::create`, yaitu identity claim, audit dan `change_feed` per greenhouse. `dimension` material = `'nutrition'`; `dimension` unit dari `UNITS`.
  - `resolveMaterials`: untuk nutrition, `material_name` diselesaikan lewat `material()` dan `unit_name` lewat `unit()`; `unit_name` tanpa `quantity` ditolak.
  - Pengecekan dimensi dilewati untuk nutrition, seperti spray. Pesan `'Pilih bahan dari daftar.'` hanya untuk jenis selain nutrition/spray.
- [ ] **Step 4: Jalankan** `--filter RecordPerGreenhouseTest`, `--filter FinanceActivitySyncTest`, `--filter OpenApiParityTest`, lalu `check-finance-contracts.py`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(backend): typed nutrition materials and mL/L/gram units`

### Task 7: Android: Nutrisi AB Mix dan nutrisi tambahan

**Files:**
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/activities/domain/ActivityDraft.kt:25-26,68-80,100-104,138,155`
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/activities/domain/EntryDraft.kt`
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/activities/EntryForm.kt:158-182,257-261,277`
- Test: `android/app/src/test/java/com/inofarm/jogja/EntryDraftTest.kt`

**Interfaces:**
- Consumes: wire Task 6 (`material_name`, `unit_name`).
- Produces:
  - `MaterialDraft(..., val unitName: String = "")`
  - `EntryDraft.abMixQuantity: String = ""`, `EntryDraft.abMixUnit: String = ""`; `EntryDraft.nutrition` kini hanya nutrisi tambahan
  - `const val AB_MIX = "AB Mix"` dan `val NutritionUnits = listOf("mL", "L", "gram")` di `EntryDraft.Companion`
  - Section nutrition = baris AB Mix (bila jumlah diisi) diikuti nutrisi tambahan

- [ ] **Step 1: Tes**
```kotlin
@Test fun abMixAndTypedExtrasBecomeOneNutritionRecord() {
    val entry = base().copy(abMixQuantity = "500", abMixUnit = "mL", nutrition = listOf(MaterialDraft(name = "Kalsium", quantity = "0,5", unitName = "gram")))
    assertTrue(entry.validation().isEmpty())
    val materials = entry.sections().single { it.type == ActivityType.NUTRITION }.payload().getJSONArray("materials")
    assertEquals("AB Mix", materials.getJSONObject(0).getString("material_name"))
    assertEquals("mL", materials.getJSONObject(0).getString("unit_name"))
    assertEquals("Kalsium", materials.getJSONObject(1).getString("material_name"))
    assertFalse(materials.getJSONObject(1).has("unit_id"))
}
@Test fun nutritionRowsNeedNameAmountAndUnitTogether() {
    assertTrue(base().copy(abMixQuantity = "500").validation().keys.any { it.startsWith("nutrition") })
    assertTrue(base().copy(nutrition = listOf(MaterialDraft(quantity = "1", unitName = "gram"))).validation().keys.any { it.startsWith("nutrition") })
}
```
Tambahkan round-trip `localJson`/`from` dengan `abMixQuantity`, `abMixUnit` dan `unitName`. Baris lama ber-`materialId` tetap dikirim dengan `material_id`/`unit_id`.
- [ ] **Step 2: Jalankan** `--tests 'com.inofarm.jogja.EntryDraftTest'`. Expected: FAIL.
- [ ] **Step 3: Implementasi.**
  - `ActivityDraft.validation`: nama ketikan sah untuk NUTRITION; satuan sah bila `unitId` **atau** `unitName` ada. Payload menulis `unit_name` bila `unitId` kosong.
  - `EntryDraft.validation`: bagian nutrisi kosong bila `abMixQuantity` kosong dan `nutrition` kosong; AB Mix dengan jumlah wajib punya satuan.
  - UI sesuai sketsa spec bagian 4:
    - "Jumlah AB Mix" + `FarmChoiceChips` satuan
    - "Nutrisi tambahan (opsional)" dengan field nama, jumlah, chip satuan, dan tombol "Tambah nutrisi tambahan"
    - baris lama ber-`materialId` menampilkan nama bahan sebagai teks dan memakai `Choice` satuan lama
  - Ringkasan menulis "AB Mix 500 mL" lalu nutrisi tambahan.
- [ ] **Step 4: Jalankan** `--tests 'com.inofarm.jogja.EntryDraftTest'` dan `:app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(android): AB Mix amount and typed extra nutrients`

### Task 8: Server: panen dengan quality saja (grade sistem)

**Files:**
- Create: `backend/app/Domain/Harvest/QualityGradeCatalog.php`
- Modify: `backend/app/Domain/Harvest/HarvestService.php:98-123`
- Modify: `backend/app/Http/Controllers/V2/HarvestController.php:128-131`
- Modify: `contracts/v2/harvest-operation.schema.json`, `contracts/v2/openapi.json`, `contracts/v2/HARVEST.md`
- Test: `backend/tests/Feature/FinanceHarvestTest.php`

**Interfaces:**
- Produces:
```php
final class QualityGradeCatalog {
    public const NAMES = ['saleable' => 'Layak', 'defective' => 'Cacat'];
    public function resolve(User $actor, string $greenhouseId, string $quality): string; // active grade with that name (case-insensitive) or a new one, this greenhouse only
}
```
- Wire: `items.*.grade_id` menjadi `sometimes|nullable|uuid|distinct`. Bila `grade_id` null, `quality` wajib (422 `items.N.quality`). Grade hasil resolve ikut aturan "grade unik per panen".

- [ ] **Step 1: Tes** `test_quality_only_lines_get_system_grades_and_can_be_sold`:
  - panen dengan `items => [['id'=>…, 'quality'=>'saleable', 'fruit_count'=>null, 'weight_kg'=>'40'], ['id'=>…, 'quality'=>'defective', 'fruit_count'=>3, 'weight_kg'=>'5']]` tanpa `grade_id` → 201; `data.items.*.grade_name` = `Layak`, `Cacat`
  - panen kedua di greenhouse sama → `grades` greenhouse itu tetap punya tepat satu `Layak` dan satu `Cacat`
  - greenhouse yang sudah punya grade aktif `'layak'` → panen baru memakai id itu
  - baris tanpa `grade_id` dan tanpa `quality` → 422 `items.0.quality`
  - **penjualan** dengan payload `FinanceSaleTest::sale` yang sama dari sumber `Layak` → 201, `total_amount` sesuai
  - koreksi panen itu tanpa `grade_id` → 200, grade tetap
- [ ] **Step 2: Jalankan** `--filter FinanceHarvestTest`. Expected: FAIL.
- [ ] **Step 3: Implementasi.**
  - Validator mengizinkan `grade_id` kosong. `HarvestService` mengisi `$line['grade_id'] = $grades->resolve(...)` sebelum baris 101, sehingga perbandingan grade lama dan pengecekan grade aktif tetap berlaku.
  - `QualityGradeCatalog` memakai pola audit/`change_feed` `SprayMaterialCatalog::create`.
- [ ] **Step 4: Jalankan** `--filter FinanceHarvestTest`, `--filter FinanceSaleTest`, `--filter FieldAlignmentTest`, `--filter OpenApiParityTest`, lalu `check-finance-contracts.py`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(backend): harvest lines by quality with system Layak/Cacat grades`

### Task 9: Android: form panen layak/cacat tanpa grade dan jam

**Files:**
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/harvest/domain/HarvestDraft.kt`
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/harvest/HarvestViewModel.kt:42` (draf baru)
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/harvest/HarvestScreen.kt:75-172,208-234`
- Test: `android/app/src/test/java/com/inofarm/jogja/HarvestDraftTest.kt`

**Interfaces:**
- Consumes: wire Task 8 (rincian tanpa `grade_id`).
- Produces:
  - `HarvestDraft.qualityLines(): HarvestDraft` memastikan tepat satu `HarvestLine` per `HarvestQuality` (Layak dulu) bila draf tidak punya baris ber-grade; id baris yang sudah ada dipertahankan.
  - `HarvestLine.legacy: Boolean get() = gradeId.isNotBlank()`.
  - `HarvestDraft.NOON = "12:00"`.
  - Baris non-legacy yang bobot dan jumlahnya kosong tidak dikirim dan tidak divalidasi.

- [ ] **Step 1: Tes**
```kotlin
@Test fun newHarvestSendsQualityLinesWithoutGradeAndAtNoon() {
    val d = HarvestDraft(greenhouseId = gh, groupId = group, date = "2026-11-20", time = HarvestDraft.NOON).qualityLines()
    val filled = d.copy(items = listOf(d.items[0].copy(weightKg = "40"), d.items[1]))
    assertTrue(filled.validation().isEmpty())
    val items = filled.payload().getJSONArray("items")
    assertEquals(1, items.length()); assertFalse(items.getJSONObject(0).has("grade_id"))
    assertEquals("saleable", items.getJSONObject(0).getString("quality"))
    assertEquals("2026-11-20T12:00:00+07:00", filled.payload().getString("occurred_at"))
}
@Test fun aCountWithoutWeightIsRejectedAndAllEmptyIsRejected() {
    val d = HarvestDraft(greenhouseId = gh, groupId = group).qualityLines()
    assertTrue(d.validation().containsKey("items"))
    assertTrue(d.copy(items = listOf(d.items[0], d.items[1].copy(fruitCount = "3"))).validation().containsKey("items.1.weight"))
}
@Test fun anOldGradedHarvestCorrectsWithItsGradesKept() {
    val old = draft().payload()
    val restored = HarvestDraft.from(UUID.randomUUID().toString(), gh, old, 2).copy(reason = "Koreksi")
    assertEquals(restored, restored.qualityLines())
    assertEquals(grade, restored.payload().getJSONArray("items").getJSONObject(0).getString("grade_id"))
}
```
Tes lama yang mengandalkan grade wajib disesuaikan: hapus asumsi "grade wajib" hanya untuk baris non-legacy.
- [ ] **Step 2: Jalankan** `--tests 'com.inofarm.jogja.HarvestDraftTest'`. Expected: FAIL.
- [ ] **Step 3: Implementasi.**
  - Validasi grade hanya untuk baris legacy. Baris non-legacy wajib `quality` dan bobot bila jumlah diisi. Sedikitnya satu baris terkirim per bagian (aturan dua varietas tetap). Payload melewatkan `grade_id` kosong.
  - `HarvestViewModel.new` membuat draf dengan `time = NOON` lalu `qualityLines()`, termasuk `companion`.
  - UI per varietas berisi dua kartu tetap "Layak" dan "Cacat", masing-masing `FarmFieldPair` Bobot (kg) dan Jumlah buah (opsional). Tanpa chip kondisi, tanpa tombol tambah/hapus. Notice "Grade aktif belum tersedia" dihapus.
  - Bagian waktu hanya tanggal (`FinanceDateField` / `WorkerEventTime` tanpa jam). Ringkasan menulis tanggal saja.
  - Draf legacy (ada baris ber-grade) memakai tampilan baris lama dengan grade sebagai teks baca saja dan tanpa tombol tambah.
- [ ] **Step 4: Jalankan** `--tests 'com.inofarm.jogja.HarvestDraftTest'` dan `:app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(android): harvest form records layak and cacat per variety`

### Task 10: Grafik pola panen

**Files:**
- Create: `android/app/src/main/java/com/inofarm/jogja/feature/harvest/domain/HarvestPattern.kt`
- Create: `android/app/src/main/java/com/inofarm/jogja/feature/harvest/HarvestPatternChart.kt`
- Modify: `android/app/src/main/java/com/inofarm/jogja/feature/managerhome/ManagerHomeScreen.kt` (bagian `"harvest"`, sekitar baris 216)
- Test: `android/app/src/test/java/com/inofarm/jogja/HarvestPatternTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class HarvestDay(val date: LocalDate, val saleableKg: BigDecimal, val defectiveKg: BigDecimal)
data class VarietyPattern(val groupId: String, val days: List<HarvestDay>, val totalKg: BigDecimal,
                          val defectivePercent: Int?, val unclassifiedKg: BigDecimal)
object HarvestPattern {
    /** [harvests]: server bodies; only active, non-draft harvests of [groupIds]; days ascending, WIB dates. */
    fun build(harvests: List<JSONObject>, groupIds: List<String>): List<VarietyPattern>
}
@Composable fun HarvestPatternChart(pattern: VarietyPattern, varietyName: String, modifier: Modifier = Modifier)
```
- `defectivePercent` = cacat / (layak + cacat) × 100, dibulatkan HALF_UP ke integer; `null` bila penyebut 0. `totalKg` = layak + cacat (tanpa unclassified).

- [ ] **Step 1: Tes**
```kotlin
@Test fun daysSumPerQualityAndSkipCancelledAndUnclassified() {
    val p = HarvestPattern.build(listOf(
        harvest(groupA, "2026-11-18T12:00:00+07:00", "saleable" to "40", "defective" to "5"),
        harvest(groupA, "2026-11-18T15:00:00+07:00", "saleable" to "10"),
        harvest(groupA, "2026-11-19T12:00:00+07:00", "defective" to "5").put("record_state", "cancelled"),
        harvest(groupA, "2026-11-20T12:00:00+07:00", null to "7")), listOf(groupA)).single()
    assertEquals(listOf(LocalDate.parse("2026-11-18")), p.days.map { it.date })
    assertEquals(BigDecimal("50"), p.days.single().saleableKg.stripTrailingZeros())
    assertEquals(BigDecimal("55"), p.totalKg.stripTrailingZeros())
    assertEquals(9, p.defectivePercent)
    assertEquals(BigDecimal("7"), p.unclassifiedKg.stripTrailingZeros())
}
@Test fun noHarvestGivesNoPercent() { assertNull(HarvestPattern.build(emptyList(), listOf(groupA)).single().defectivePercent) }
```
`harvest(...)` adalah helper tes yang membangun body `{planting_group_id, occurred_at, record_state:"active", items:[{quality, weight_kg}]}`; `null` berarti tanpa `quality`.
- [ ] **Step 2: Jalankan** `--tests 'com.inofarm.jogja.HarvestPatternTest'`. Expected: FAIL.
- [ ] **Step 3: Implementasi.**
  - `build` mengelompokkan per `planting_group_id` dan tanggal WIB.
  - Chart: `Canvas` batang bertumpuk (layak hijau token Canopy, cacat merah bata token Canopy), label tanggal "d MMM" `id-ID`, digulir horizontal bila tidak muat, ringkasan "Total X kg · cacat Y%", dan legenda berteks.
  - `semantics { contentDescription = "18 Nov: layak 50 kg, cacat 5 kg; …" }`.
  - Ketuk batang menampilkan angkanya.
  - Kosong: "Belum ada panen di musim ini."
  - Catatan "N kg panen lama tanpa layak/cacat tidak dihitung" bila `unclassifiedKg > 0`.
  - Pasang di bagian Panen detail greenhouse pengelola, satu chart per varietas musim terbuka. Sumber body = catatan panen lokal yang sudah dipakai layar itu (`LocalRecord` kind `harvest`, `serverBody ?: body`).
- [ ] **Step 4: Jalankan** `--tests 'com.inofarm.jogja.HarvestPatternTest'` dan `:app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(android): harvest pattern chart per variety`

### Task 11: Uji perangkat, backend nyata, bukti dan konteks

**Files:**
- Modify: `android/app/src/androidTest/java/com/inofarm/jogja/RecordingReworkBackendDeviceTest.kt`
- Modify: tes instrumentasi yang memakai tag/teks yang berubah (`FinanceEntryUiTest.kt`, `WorkerContextUiTest.kt`), hanya yang gagal karena perubahan ini
- Create: `docs/context/INOFARM_FIELD_FEEDBACK_EVIDENCE_20261120.md`
- Modify: `CLAUDE.md` (satu paragraf di atas), `docs/context/README.md` hanya baris yang relevan (file ini punya perubahan pengguna yang belum di-commit; stage per hunk)

- [ ] **Step 1: Tambah fase** di `RecordingReworkBackendDeviceTest`:
  - satu isian dengan dua pengukuran (sebelum EC saja, sesudah EC+PPM)
  - AB Mix 500 mL + Kalsium 0,5 gram
  - panen dua varietas layak/cacat tanpa grade

  Verifikasi SQL di `inofarm_test`:
  - 2 baris `measurement` satu `entry_batch_id`
  - `materials.purpose='nutrition'` untuk AB Mix/Kalsium
  - `harvest_items.grade_name` ∈ {Layak, Cacat}
  - jam panen `05:00:00` UTC
- [ ] **Step 2: Jalankan** di `Inofarm_Botanical_API30` (`-gpu swiftshader_indirect`) terhadap Laravel lokal dan `inofarm_test`, dengan runner yang sama seperti 18 November. Expected: semua fase lulus.
- [ ] **Step 3: Jalankan penuh** `./scripts/test-backend.ps1`, `./scripts/build-android.ps1 -JavaHome <jdk>`, `check-finance-contracts.py`, `python scripts/check-finance-context.py`, dan `FinanceEntryUiTest` penuh. Expected: backend dan JVM lulus, lint 0 error, kontrak lulus. `FinanceEntryUiTest` tidak menambah kegagalan di luar baseline 15; catat daftar kegagalannya.
- [ ] **Step 4: Tulis bukti** berisi angka tes, perintah, perangkat, fixture vs backend nyata, dan batas (belum ponsel fisik, belum TalkBack nyata, AB Mix satu angka belum dikonfirmasi Inofarm). Tambah paragraf `CLAUDE.md` dengan tautan bukti.
- [ ] **Step 5: Commit** `docs(context): evidence for Inofarm field feedback on recording and harvest`
