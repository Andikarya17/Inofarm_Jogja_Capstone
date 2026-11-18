# Rombak pencatatan per greenhouse: bukti dan batas (17–18 November 2026)

Pekerjaan ini mengikuti `MASTER_PROMPT_RECORDING_REWORK_20261117.md` dan keputusan K1–K7 di `HANDOFF_RECORDING_REWORK_20261117.md`. Kedua dokumen itu milik pengguna dan **belum di-commit**, jadi tautannya baru berfungsi di laptop pengguna. Jawaban pengguna 17 November 2026 mengatasi K2/K4 handoff bila bertentangan. Jawaban itu dan rancangan migration 22 ada di rancangan backend `docs/context/RECORDING_REWORK_DESIGN_20261117.md`, yang ikut branch F1 dan baru ada di sini setelah F1 di-merge.

Semua fase di-merge ke main pada 18 November 2026 lewat PR #72 (F1) lalu #73–#78 (F0, F2–F6), atas permintaan pengguna. Fitur keuangan tidak disentuh (K7).

## Keputusan pengguna yang dipakai

| Pertanyaan | Jawaban (17 November 2026) |
|---|---|
| Satuan pencatatan | Pengukuran, nutrisi, stadia dan pekerjaan umum: **satu catatan per greenhouse**, karena satu GH punya satu tandon nutrisi. Hanya **penyemprotan** dibedakan per varietas |
| Bentuk form Catat kegiatan | Satu halaman memuat semua bagian |
| HSS/HST manual | Wajib |
| Letak pengaturan tema | Menu avatar di app bar, kedua peran |
| Tile Pengukuran di sheet Catat | Digabung ke Catat kegiatan |
| Cek nutrisi semua greenhouse | Memakai form gabungan per GH |
| Koreksi catatan dari form gabungan | Per bagian (per catatan) |
| Waktu migration 22 ke `inofarm_finance_local` | Di akhir, setelah F1 di-merge, dengan backup dan izin terpisah |

## Branch dan isi

Branch Android bertumpuk; F1 berdiri sendiri di atas main. PR #72–#78, sudah di-merge.

| Fase | Branch | Commit | Isi |
|---|---|---|---|
| F0 tema | `app-theme-preference` (dari main) | `01d04bf` | Tema terang/gelap di dalam aplikasi, terang sebagai bawaan, tidak lagi mengikuti sistem. Pilihan "Tampilan" ada di menu avatar kedua peran dan disimpan di `SharedPreferences` (`inofarm_display`). Status bar mengikuti tema |
| F1 backend | `backend-record-per-greenhouse` (dari main) | `6fb68a4` | Migration 22 (additive): `activities.season_id`, `planting_group_id` nullable, `entry_batch_id`, `age_basis`/`age_day`, `spray_date`/`spray_period`; dosis bahan opsional; `materials.purpose`. Bahan semprot dari nama (find-or-create di semua GH), daftar awal 10 bahan semprot, perintah `inofarm:spray-materials`. Pekerja mengakses semua GH aktif tanpa penugasan. Kontrak, OpenAPI dan fixture diperbarui |
| F2 wizard dan akun | `greenhouse-wizard-two-steps` (di atas F0) | `02806ec` | Wizard tambah GH dua langkah (Greenhouse, Mulai tanam) dengan satu atau dua varietas, satu kelompok per varietas bernama varietas. "Mulai tanam" juga dibuka dari GH yang belum ditanami. UI penugasan pekerja dihapus; akun dibuat tanpa memilih GH |
| F3 Catat memilih GH | `record-pick-greenhouse` (di atas F2) | `cc7782e` | Catat hanya menanyakan greenhouse, untuk kedua peran. Musim dan kelompok dipilih otomatis (`RecordingTarget`). GH yang belum ditanami atau belum disemai menampilkan alasan, dan pengelola mendapat tombol "Mulai tanam" |
| F4 form gabungan | `record-entry-form` (di atas F3) | `76381e4` | Satu form "Catat kegiatan" per GH: umur HSS/HST, pengukuran (EC, PPM, pH, suhu air, sebelum/sesudah inject), nutrisi, penyemprotan per varietas (tanggal, pagi/sore, bahan dengan dosis opsional, bahan baru dari nama), stadia, catatan. Satu isian menjadi satu catatan per bagian, semuanya dengan `entry_batch_id` yang sama, dan masuk antrean dalam satu transaksi lokal. SQLite schema 11. Cek catatan mengelompokkan per isian. Umur manual tampil di kartu GH |
| F5 panen dua varietas | `harvest-two-varieties` (di atas F4) | `e86a494` | GH dengan dua varietas mengisi panen berdampingan dalam satu form, menjadi satu panen per varietas dengan waktu yang sama. Jumlah buah opsional (`null`, bukan 0), dan semua pembaca jumlah buah aman terhadap kosong |
| F6 bukti dan context | `recording-rework-evidence` (di atas F5) | lihat PR | Dokumen ini, CLAUDE.md, GAP_ANALYSIS.md, DECISIONS.md, tangkapan layar |

## Pemeriksaan

Dijalankan 17–18 November 2026 di laptop, JDK 25.0.4. Backend hanya memakai MySQL efektif `inofarm_test`. Database aplikasi `inofarm_finance_local` **tidak disentuh**.

| Pemeriksaan | Hasil |
|---|---|
| Backend `./scripts/test-backend.ps1`, branch F1 | 173 tes / 4.724 assertion lulus (167 lama, 6 baru di `RecordPerGreenhouseTest`) |
| Backend, branch Android F0–F5 (tanpa migration 22) | 167 tes / 4.249 assertion lulus pada setiap fase |
| Checker kontrak `check-finance-contracts.py` | lulus di F1 (termasuk fixture `activity-entry-operation.json` dan parity OpenAPI) dan di setiap fase Android |
| `:app:testDebugUnitTest` (JVM) | 137 tes, 0 gagal di F5. Tes baru: `ThemePreferenceTest`, `RecordingTargetTest`, `EntryDraftTest` (6), dua tes `FinanceLocalStoreTest` lokal, dua tes `HarvestDraftTest` |
| `:app:lintDebug` | 0 error, 18 warning, 1 hint |
| `check-finance-context.py` | lulus di F6 |

### Instrumentasi di emulator

Hanya `Inofarm_Botanical_API30` (Android 11), mode terang. Mode gelap dicek lewat pengaturan di dalam aplikasi. Semua tes memakai fixture HTTP sintetis, **bukan** backend Laravel nyata dan bukan ponsel fisik.

| Tes baru | Fase | Hasil |
|---|---|---|
| `themeMenuSwitchesLightAndDarkForBothRoles` | F0 | lulus |
| `greenhouseWizardSavesTwoVarietiesInTwoStepsWithoutAssignments` | F2 | lulus |
| `catatAsksOnlyForTheGreenhouseForBothRoles` | F3 | lulus |
| `entryFormSendsOneRecordPerSectionForBothRoles` | F4 | lulus, termasuk asersi bahwa satu isian menjadi satu catatan per bagian dengan `entry_batch_id` yang sama |
| `upgradeToElevenKeepsOldSingleTypeDraftsAndOutbox`, `queueEntryWritesAllSectionsAtomicallyAndKeepsTheirIds` (`FinanceLocalStoreTest`) | F4 | lulus |
| `harvestTwoVarietiesSideBySideSendsOneHarvestEach` | F5 | lulus bila dijalankan sendiri, terang dan gelap. Asersi: dua panen terkirim untuk dua varietas, waktu sama, `fruit_count` null |

Tiga tes lama di-`@Ignore` karena perilakunya dihapus oleh K4, dengan alasan tertulis di anotasi: `workerActivityFormNeverOffersMeasurementAndScheduleLocksTheType` (pilihan jenis kegiatan), `multiMaterialDraftRotationAndBackRetainRawFieldsBeforeReview` (nutrisi tunggal dengan PPM/pH wajib) dan `activityMenuEntryKeepsSkippedReasonAndReturnsToHistory` ("Kegiatan dilewati").

**`FinanceEntryUiTest` penuh** (70 tes, 3 diabaikan):

Dijalankan 18 November 2026 dengan APK F5 final; F6 tidak mengubah kode. Putaran pertama berhenti di tes ke-55 karena aplikasi tidak merespons di emulator (`keyDispatchingTimedOut`), lalu emulator macet total. Emulator dinyalakan ulang, dan 16 tes yang belum selesai dijalankan terpisah.

| Bagian | Dijalankan | Gagal |
|---|---|---|
| Putaran penuh sampai berhenti | 51 (+3 diabaikan) | 16 |
| Sisa setelah emulator dinyalakan ulang | 16 | 1 (`managerHomeFallsBackWhenSelectedGreenhouseIsRevoked`) |
| **Total** | **67** | **17** |

Enam belas dari 17 adalah nama baseline lama ([triase Q1](UI_TEST_TRIAGE_EVIDENCE_20261115.md)): `assetConflict…`, `attachmentReasonFailedFlush…`, `attachmentReview…`, `conflictKeepLocal…`, `conflictUseServer…`, `harvestBalance…`, `harvestOwnCorrection…`, `longContext…`, `managerHomeFallsBack…`, `managerHomeOneTap…`, `managerRecordsObservations…`, `managerSeasonClose…`, `managerWalkthrough…`, `queueReview…`, `secondaryMenus…`, `twoFieldLogin…`. Semua tes baru rombak lulus, termasuk `harvestTwoVarietiesSideBySideSendsOneHarvestEach`.

Satu kegagalan baru: `investmentWorkspaceRecreationAndLogoutKeepLatestIncompleteRawInput`. Tes ini gagal mengetik di `investment-horizon`, karena field itu hanya ada di pohon semantik yang belum digabung. Kegagalan ini berulang saat tes dijalankan sendiri di emulator yang baru dinyalakan ulang. APK yang sama lulus tes ini di rangkaian penuh F5, dan source investasi tidak berubah dibanding `github/main` (K7). Penyebabnya belum diselidiki.

**Koreksi angka F4.** Laporan F4 menyebut "2 gagal" untuk rangkaian penuh. Angka itu meleset. `manualMeasurementAutosaveBackRecreationQueueAndDashboardKeepOnePair` dan `workerNativePickersAndImeKeepDraftOnNavigation` sudah rusak sejak F4 karena masih memakai alur pengukuran lama, tetapi tidak terhitung di putaran itu. Rangkaian penuh F5 pertama (sebelum perbaikan) gagal 20 dari 67. Kedua tes diperbaiki di F5 dan lulus saat dijalankan sendiri. Emulator juga sempat mati setelah rangkaian F4 (layanan `uimode` hilang) dan dinyalakan ulang.

### Tangkapan layar

Emulator `Inofarm_Botanical_API30`, fixture sintetis.

- F0: [menu avatar pekerja dengan pilihan Tampilan](evidence/recording-rework-20261117/theme-worker-menu.png), [Beranda pengelola mode gelap](evidence/recording-rework-20261117/theme-manager-dark.png)
- F2: [wizard langkah 2, dua varietas](evidence/recording-rework-20261117/wizard-step2-light.png)
- F3: [Catat pekerja: pilih greenhouse](evidence/recording-rework-20261117/catat-worker-ready.png), [Catat pengelola: GH belum ditanami dengan Mulai tanam](evidence/recording-rework-20261117/catat-manager-unplanted-light.png)
- F4: [form gabungan terisi, terang](evidence/recording-rework-20261117/entry-worker-filled-light.png), [gelap](evidence/recording-rework-20261117/entry-worker-filled-dark.png), [ringkasan pengelola](evidence/recording-rework-20261117/entry-manager-summary-light.png)
- F5: [panen dua varietas](evidence/recording-rework-20261117/harvest-two-light.png), [review panen dua varietas](evidence/recording-rework-20261117/harvest-two-review.png)

## Batas

1. ~~Android F3–F5 membutuhkan backend F1~~: F1 sudah di main bersama Android.
2. **Integrasi Android dengan backend Laravel nyata belum diuji.** Semua tes UI memakai fixture. `FinanceBackendDeviceTest` belum dijalankan terhadap F1.
3. ~~Migration 22 belum dijalankan~~: diterapkan 18 November 2026 (bagian di bawah).
4. **Ponsel fisik, TalkBack, font 200% dan lebar 360 dp** belum dicek untuk form gabungan dan panen dua varietas.
5. **Panen dua varietas:** koreksi tetap per catatan panen, dan Cek catatan panen belum dikelompokkan per isian seperti kegiatan.
6. **Akses pekerja:** pekerja kini membaca pemakaian bahan per musim (`seasons/{id}/material-usage`) di semua GH aktif. Itu perilaku lama yang dulu dibatasi penugasan. GH nonaktif keluar dari scope pekerja, sehingga replay pekerja ke GH yang dinonaktifkan sebelum antrean terkirim mendapat 404.
7. **Beranda pengelola** tetap butuh satu kelompok periode berjalan per GH; catatan tingkat GH dibaca dari musimnya.
8. Tabel dan endpoint `assignments` tetap ada untuk kompatibilitas, tetapi tidak lagi menentukan akses atau tampil di UI.

## Penerapan ke database aplikasi, 18 November 2026

Atas permintaan pengguna, setelah semua PR di-merge dan main lulus pemeriksaan (backend 173 tes / 4.724 assertion, JVM 137, lint 0 error, checker kontrak dan context).

| Langkah | Hasil |
|---|---|
| Koneksi efektif | MySQL `127.0.0.1:3307`, database `inofarm_finance_local`; hanya migration 22 Pending |
| Backup `scripts/backup-mysql.ps1` | `outputs/rollout-20261118/inofarm_finance_local-20261118-144430.sql`, 163.957 byte, 55 `CREATE TABLE`, penanda "Dump completed" ada |
| SHA-256 backup | `5d470ea6cf37c7f073616bad8254e427795726d3f4def78803ee741d7c036f74` |
| `migrate --pretend` | Hanya kolom baru, backfill `activities.season_id` dari `planting_groups`, FK dan index baru, serta kolom yang menjadi nullable |
| `migrate` | `2026_11_17_000022_record_per_greenhouse` DONE, 462,63 ms; `migrate:status --pending=1` keluar 0 |
| Sebelum/sesudah | 55 tabel; jumlah baris `users` 2, `greenhouses` 5, `seasons` 4, `planting_groups` 4, `activities` 2, `harvests` 1, `harvest_items` 2, `sales` 0, `expenses` 0, `audit_logs` 32, `change_feed` 36 sama. Kedua kegiatan lama terisi `season_id` |
| `php artisan inofarm:spray-materials <pengelola>` | Satu-satunya akun pengelola aktif sebagai aktor. 5 GH, 50 bahan semprot dibuat (10 per GH), masing-masing dengan audit dan change feed (`audit_logs` 82, `change_feed` 86). Dijalankan ulang: 0 dibuat |

Folder `outputs/` diabaikan Git. Restore backup ini belum diuji. Pembacaan Beranda dari aplikasi terhadap database ini belum dicoba.
