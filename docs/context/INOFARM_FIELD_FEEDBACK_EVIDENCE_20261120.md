# Bukti: masukan lapangan Inofarm untuk catat kegiatan dan panen (20 November 2026)

Branch `inofarm-field-feedback-recording`, dibuat dari `github/main` `3b45c7a` (sudah berisi PR #86).

- Desain: [spec](../superpowers/specs/2026-11-20-catat-kegiatan-panen-masukan-inofarm-design.md).
- Rencana: [plan](../superpowers/plans/2026-11-20-catat-kegiatan-panen-masukan-inofarm.md).
- Penjualan, keuangan, laporan dan menu Grade di Kelola tidak diubah.
- Tidak ada migration baru. Database `inofarm_finance_local` tidak disentuh.

## Yang berubah

| # | Masukan Inofarm | Perubahan |
|---|---|---|
| 1 | Mulai tanam → mulai semai | Wizard dan semua teks "mulai tanam" menjadi "semai". "Tanggal pindah tanam", "Jumlah tanaman" dan istilah "tanam saat ini" tetap |
| 2 | Nama musim `GH1 Musim 1` | Wizard mengisi nama musim otomatis `<nama GH> Musim <n>`, tidak bisa diketik. Musim terbuka yang dipakai ulang tetap memakai namanya |
| 3 | Pop up greenhouse belum semai | Pengelola yang menekan Kegiatan/Panen pada GH tanpa tanam mendapat dialog "Greenhouse belum dimulai semai" / "Apakah mau mulai semai?" dengan tombol Tidak/Lanjut. Lanjut membuka Mulai semai |
| 4 | Waktu, EC, PPM opsional, sebelum + sesudah inject | Label "Waktu"; satuan mS/cm hilang dari kotak EC; EC wajib, PPM opsional (server dan Android); chip "Diukur" bisa dipilih dua dan masing-masing menjadi blok sendiri (sebelum di atas, sesudah di bawah). Keduanya jadi dua baris `measurement` dengan `entry_batch_id` yang sama. Bila jamnya sama, "terakhir" = sesudah inject (server dan perangkat) |
| 5 | Nutrisi AB Mix | Bagian "Nutrisi AB Mix": jumlah AB Mix dengan satuan mL/L/gram, ditambah nutrisi tambahan yang diketik namanya. Server membuat bahan bertujuan `nutrition` dan satuannya di greenhouse itu saja (`NutritionCatalog`) |
| 6 | Dasar umur → Satuan | Diganti |
| 7 | Stadia → Stadia/Fase | Form, Ringkasan, Riwayat, jadwal |
| 8 | Panen tanpa jam dan grade, layak/cacat | Per varietas ada baris tetap Layak dan Cacat (bobot, jumlah buah opsional); tidak ada pilihan grade maupun jam (dikirim 12.00 WIB). Server mengisi grade sistem "Layak"/"Cacat" (`QualityGradeCatalog`), jadi penjualan tetap jalan. Ada grafik pola panen per varietas di bagian Panen detail greenhouse pengelola |

## Hasil pemeriksaan (baru, 20 November 2026)

| Pemeriksaan | Hasil |
|---|---|
| PHPUnit penuh (`scripts/test-backend.ps1`, MySQL `inofarm_test`) | **181 tes / 4.862 assertion lulus** (sebelumnya 175) |
| Kontrak (`check-finance-contracts.py`) | PASS |
| Konteks (`check-finance-context.py`) | 0 error |
| JVM (`:app:testDebugUnitTest`) | Lulus, termasuk tes baru `ManualMeasurementTest`, `HarvestPatternTest` dan tambahan di `EntryDraftTest`, `HarvestDraftTest`, `GreenhouseWizardRulesTest`, `RecordingTargetTest` |
| Lint | 0 error, 18 warning |
| Instrumentasi terfokus, emulator `Inofarm_Botanical_API30` dengan fixture MockWebServer | 15 tes yang menyentuh layar yang diubah: **11 lulus**. Empat gagal karena tombol `finance-context-open` yang dihapus PR #86 (F8) dan sudah gagal di `main` (lihat batas) |
| Backend nyata (Laravel `artisan serve` port 8001 + MySQL `inofarm_test` + emulator API 30) | Fase `recording-rework`, `rework-correction`, `rework-manager-home` lulus. **Mode verify SQL 3/3 PASS** |

SQL `verify-recording-rework` membuktikan hal berikut:

- Satu isian menjadi enam baris dengan satu `entry_batch_id`:
  - dua pengukuran: sebelum EC 1.6 dengan PPM null, sesudah EC 1.8 dengan PPM 900
  - satu nutrisi: AB Mix 250 dan Kalsium 0.5, bertujuan `nutrition`, hanya di GH A, memakai satuan gram yang sudah ada
  - dua penyemprotan
  - satu stadia
- Dua panen per varietas dengan baris `defective Cacat 1.5 2`, `saleable Layak 12.5 null` dan `saleable Layak 7 null`, pukul 05:00 UTC (12.00 WIB).
- Satu grade Layak dan satu Cacat di GH A.
- Enam keputusan pengelola dan dua keputusan panen.

`verify-rework-correction` mengoreksi pengukuran sesudah inject, sementara empat bagian lain tetap di versi 1.

Tangkapan layar ada di [evidence/inofarm-field-feedback-20261120](evidence/inofarm-field-feedback-20261120/): dialog mulai semai, form kegiatan dan Periksa, form panen dua varietas, wizard langkah 2, dan inbox pengelola.

## Batas dan yang belum

- **Belum di ponsel fisik**, belum TalkBack nyata, belum diuji Inofarm.
- **Grafik pola panen belum dilihat di layar.** Logikanya diuji JVM (`HarvestPatternTest`); komponen Compose-nya terkompilasi dan lint bersih, tetapi tidak ada tangkapan layar atau tes UI.
- Fase backend nyata `rework-offline-queue` dan `rework-offline-replay` (mematikan backend) tidak dijalankan ulang; jalur antrean tidak diubah.
- `FinanceEntryUiTest` penuh belum selesai dalam satu putaran: proses crash pada `depreciationRequiresExplicitUsagePeriodBasisAndDoesNotInventAmount` (modul aset, tidak diubah).
- Sebelum crash, kegagalan di luar baseline 18 November disebabkan PR #86 F8, yang menghapus tombol "Ubah musim atau kelompok" (`finance-context-open`). Contohnya `attachmentUnknownPolicy…`, `narrowAndLandscape…`, `offlineQueueRecreation…`, `saleRejectedSource…`, `glassFrontend…` dan `workerNavigation…`. Tes-tes ini masih memanggil tombol itu, dan perlu diperbarui terpisah.
- `logoutFlushesLatest…` gagal sekali karena "multithreaded access to SnapshotStateObserver" (flake). `workerScheduleEntryHasNoTypeChoice` berubah oleh PR #86 F4.
- AB Mix dicatat sebagai satu angka. Perlu dikonfirmasi ke Inofarm apakah A dan B dicatat terpisah.
- Grade sistem "Layak"/"Cacat" tampil di menu Grade di Kelola sampai keuangan direvisi.
