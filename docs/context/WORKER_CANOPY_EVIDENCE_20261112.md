# Halaman pekerja Canopy: bukti implementasi (12 sampai 13 November 2026)

Rencana: [MASTER_PROMPT_WORKER_CANOPY.md](MASTER_PROMPT_WORKER_CANOPY.md). Semua bukti layar dan tes UI di sini berasal dari emulator API 29 (AVD `Inofarm_Worker_API29`, 1080x2340) dengan fixture MockWebServer. Bukan perangkat fisik, bukan backend nyata, dan bukan TalkBack nyata. Backend, API, kontrak, migration dan schema SQLite lokal tidak disentuh, sehingga `test-backend.ps1` tidak dijalankan.

## Status per fase

Setiap fase dibuat dari ujung fase sebelumnya. Tidak ada yang di-push atau di-merge.

| Fase | Branch | Commit | Status |
|---|---|---|---|
| 0 Baseline | `codex/worker-canopy-baseline` | `dcd13a1` (hanya master prompt) | Selesai, angka tercatat di bawah |
| 1 Shell | `codex/worker-canopy-shell` | `5e6dbb8`, `28b4b3c` | Selesai |
| 2 Beranda | `codex/worker-canopy-home` | `e06453f` | Selesai |
| 3 Satu greenhouse, sheet Catat, sheet Belum terkirim | `codex/worker-canopy-greenhouse` | `c93b625` | Selesai, dengan dua bagian spesifikasi belum ada |
| 4 Form, Periksa, koreksi | `codex/worker-canopy-forms` | `cfd688b` | Selesai, dengan beberapa bagian spesifikasi belum ada |
| 5 Riwayat, detail, progres panen | `codex/worker-canopy-history` | `25604b7` | Selesai |
| 6 Cek nutrisi semua greenhouse | `codex/worker-canopy-nutrition-run` | `dda4b50` | Selesai |
| 7 Dokumen | `codex/worker-canopy-docs` | commit dokumen ini | Selesai |

## Matriks temuan

| # | Kebutuhan | Penerimaan | Berkas utama | Tes |
|---|---|---|---|---|
| W2 | Dock dua tab dan Catat di tengah | Dock pekerja memakai `FarmDockNavigation`; dock pengelola tidak berubah | `core/design/CanopyKit.kt`, `feature/FinanceRoot.kt` | `FarmDockSlotsTest`, `ManagerNavigationTest`, `workerNavigationHasTwoTabsRecordButtonAndUnsentSheet` |
| 6.1 | Header sapaan, menu akun | Sapaan sesuai jam WIB, avatar inisial, tombol belum terkirim di baris sendiri | `feature/FinanceRoot.kt` (`FinanceToolbar`) | `workerNavigationHasTwoTabsRecordButtonAndUnsentSheet` |
| 6.2, 6.5 | Beranda semua greenhouse, Perlu diperbaiki | Kartu ringkasan, kartu perbaikan, kartu greenhouse berurut, layar alasan pengelola | `feature/context/WorkerGreenhouseCards.kt`, `WorkerHomeLogic.kt` | `WorkerHomeLogicTest` (JVM), `workerHomeShowsGreenhouseCardsAndCorrectionsCardWithoutOpeningAGreenhouse`, `workerHomeCardShowsLastMeasurementAndSaveMessageAfterRecording` |
| 6.3 | Satu greenhouse | Bar, hero forest, jadwal, progres panen, catat di luar jadwal | `feature/context/WorkerGreenhousePage.kt`, `WorkerDayAgenda.kt` | `workerAgendaDistinguishesRealizationsAndSkippedRecords`, `workerScheduleEntryHasNoTypeChoice`, `workerSelectsGreenhouseDirectlyAndOpensReadyRecordingContext` |
| 6.4, 6.6 | Sheet Catat, sheet Belum terkirim | Chip greenhouse, kotak bercopy pekerja, kelompok antrean | `feature/ManagerSheets.kt`, `feature/QueueScreen.kt` | `workerNavigationHasTwoTabsRecordButtonAndUnsentSheet` |
| 6.7 sampai 6.9, 6.11, 6.13 | Form, Periksa, koreksi | `FarmFormFrame`, bar penuh lebar, alasan koreksi di atas, peringatan PPM bertulis | `feature/FinanceRoot.kt`, `feature/harvest/HarvestScreen.kt`, `feature/FormCanopy.kt` | `workerActivityFormNeverOffersMeasurementAndScheduleLocksTheType`, `workerCorrectionHasNoReferenceChoiceAndUsesPlainButtons`, `workerNativePickersAndImeKeepDraftOnNavigation`, `offlineMultigradeHarvestRecreationReconnectManagerAuditAndCancel` |
| 6.12, 6.14 | Riwayat, detail, progres panen | Kartu per hari, chip status, chip greenhouse, picker tanggal, hero sage | `feature/history/*`, `feature/harvest/WorkerHarvestProgress.kt`, `feature/FinanceRoot.kt` | `workerHistoryShowsCheckStatusFiltersToOwnRecordsAndPicksDates`, `historyFilterShowsEmptyThenRestoresMatchingRecord` |
| 6.10 | Cek nutrisi semua greenhouse | Satu draf pengukuran per greenhouse, lewati, periksa semua, kirim terpisah | `feature/context/NutritionRun.kt`, `feature/FinanceRoot.kt`, `feature/activities/ActivityViewModel.kt` | `nutritionRunSavesOneMeasurementPerGreenhouseSkipsOneAndSendsSeparately` |

## Hasil pemeriksaan (dari output perintah sesi ini)

| Pemeriksaan | Hasil |
|---|---|
| `build-android.ps1` (assembleDebug, testDebugUnitTest, lintDebug), JDK `jdk-25.0.2` | Lulus pada tiap fase |
| Tes JVM | 104 pada baseline, 112 pada akhir, 0 gagal. Delapan tes baru: `FarmDockSlotsTest` (2), `WorkerHomeLogicTest` (6) |
| Lint | 0 error, 18 warning, 1 hint, sama dengan baseline |
| `python scripts/check-finance-context.py` | Gagal pada dua tautan rusak yang sudah ada di `origin/main`: `MASTER_PROMPT_MANAGER_SUPERADMIN.md` dan `MASTER_PROMPT_WORKER_FLOW.md`. Tidak ada tautan baru yang rusak. Keduanya belum diperbaiki karena menunggu keputusan pengguna |
| `FinanceAdminUiTest` | 2 dari 2 lulus |
| `FinanceEntryUiTest` penuh pada baseline | 20 dari 64 gagal (daftar di bawah) |
| `FinanceEntryUiTest` penuh pada akhir | 17 dari 65 gagal. Satu kegagalan baru dibanding baseline (`assetHubRemainsScrollableWithFont200Percent`) lulus 3 dari 3 saat dijalankan sendiri, jadi dicatat sebagai flake |

Catatan lingkungan: dokumen lama mencatat 15 kegagalan pada baseline. Di emulator ini baseline gagal 20 dari 64. Empat tes pekerja yang tidak ada di daftar 15 itu (`workerAgendaDistinguishesRealizationsAndSkippedRecords`, `workerHistoryShowsCheckStatusFiltersToOwnRecordsAndPicksDates`, `workerNativePickersAndImeKeepDraftOnNavigation`, `workerScheduleEntryHasNoTypeChoice`) gagal pada baseline karena tes menekan tombol Perbarui saat sinkronisasi awal masih berjalan. Penyebabnya belum dipastikan selain itu. Perbandingan dilakukan per nama terhadap baseline yang dijalankan di mesin yang sama.

Kegagalan baseline per nama (21 nama, dari `outputs/worker-canopy-20261112/baseline-failing-ui-tests.txt`; satu, `operatorBackupRequiresReason...`, berasal dari kelas `FinanceRecoveryTest`):

`assetConflictReviewPreservesRawLocalNameAndOldOperation`, `attachmentReasonLogoutFlushesLatestRawTextWithoutChangingAcceptedMetadata`, `attachmentReviewRemainsReachableAtLargeFontNarrowAndLandscape`, `conflictKeepLocalRequiresReasonAndManagerCanAuditAndCancel`, `conflictUseServerPreservesLocalCopyAndWorkerHasNoCancel`, `harvestBalanceRejectionCanBeCorrectedAfterExplicitReview`, `harvestOwnCorrectionConflictUsesNewVersionAndRetainsLocalArchive`, `longContextAndExpiredSessionKeepNavigationReachable`, `managerHomeFallsBackWhenSelectedGreenhouseIsRevoked`, `managerHomeOneTapFiltersRotationAndCachedSnapshot`, `managerRecordsObservationsAndEditsMeasurementTarget`, `managerSeasonCloseAndReopenUseReasonedVersionedQueue`, `managerWalkthroughVisitsPickersScheduleToolsAndAdminWithoutSubmitting`, `operatorBackupRequiresReasonAndConfirmationAndShowsBackupNotRestoreSuccess`, `queueReviewOpensConflictedRecordAndBackReturnsToSameQueue`, `secondaryMenusReflowAndKeepSheetActionsReachable`, `twoFieldLoginHasAccessiblePasswordToggleAndHandlesMissingConfiguration`, `workerAgendaDistinguishesRealizationsAndSkippedRecords`, `workerHistoryShowsCheckStatusFiltersToOwnRecordsAndPicksDates`, `workerNativePickersAndImeKeepDraftOnNavigation`, `workerScheduleEntryHasNoTypeChoice`.

Kegagalan pada akhir (17 nama, `final-failing-ui-tests.txt`): semua nama di atas kecuali lima yang kini lulus (`attachmentReasonLogoutFlushesLatestRawTextWithoutChangingAcceptedMetadata`, `operatorBackupRequiresReasonAndConfirmationAndShowsBackupNotRestoreSuccess`, `workerAgendaDistinguishesRealizationsAndSkippedRecords`, `workerNativePickersAndImeKeepDraftOnNavigation`, `workerScheduleEntryHasNoTypeChoice`), ditambah `assetHubRemainsScrollableWithFont200Percent` (flake). Tidak ada kegagalan baru yang konsisten.

## Perubahan pada tes

Tidak ada assertion yang dihapus untuk meloloskan tes. Setiap perubahan setara dan dicatat:

- Landing pekerja kini `worker-home-summary` menggantikan `finance-context-open` (Beranda tidak lagi punya tombol "Pilih greenhouse"). Helper `openHomeContext()` membuka konteks lewat kartu greenhouse.
- Teks "1 dari 1 jadwal tercatat" menjadi "1 dari 1 tugas"; "Belum ada pengukuran" menjadi "Nutrisi belum dicek"; assertion nilai EC dan PPM di kartu Beranda diganti chip "Nutrisi dicek" (nilai ada di halaman greenhouse).
- Tombol kembali ke Beranda kini `manager-back` menggantikan `worker-greenhouse-overview`.
- Teks agenda "Pekerjaan hari ini" menjadi "Jadwal hari ini"; status jadwal diperiksa lewat "1 dilewati".
- Empat tombol bar bawah kini memakai `scrollIfScrollable()`.
- Tes jenis kegiatan memeriksa bahwa opsi chip Pengukuran tidak ada, menggantikan membuka dropdown lalu Back.
- Teks riwayat "Terkirim · Belum dicek" dan "Terkirim · Perlu diperbaiki" menjadi chip "Belum dicek" dan "Perlu diperbaiki"; "Draf · tersimpan di perangkat" menjadi "Draf".
- Tes konteks Beranda tidak lagi menganggap greenhouse belum terpilih.
- Beberapa tes menunggu kartu greenhouse muncul dan mengulang sinkronisasi sampai jadwal tiba.
- `invalidHarvestDraftKeepsSaveReachableAtNarrowAndLandscapeLargeFont` memakai root pertama bila ada dua jendela saat menangkap layar.
- Tes baru: `nutritionRunSavesOneMeasurementPerGreenhouseSkipsOneAndSendsSeparately`, dengan fixture dua greenhouse.

## Perubahan yang menyentuh pengelola

- `FarmDockNavigation` menerima 2 atau 4 item (sebelumnya tepat 4). Tata letak 4 item tidak berubah, dibuktikan `FarmDockSlotsTest`.
- `RecordSheet`, `QueueScreen`, `FarmReviewFrame` dan `HistoryScreen` diberi parameter berdefault atau cabang khusus pekerja; jalur pengelola memakai nilai bawaan.
- `HistoryScreen` memakai `canopy = true` untuk pekerja; untuk pengelola tidak berubah.
- Tidak ada perubahan backend, API, kontrak, migration, atau schema SQLite lokal.
- Screenshot pengelola sebelum dan sesudah untuk layar yang kodenya disentuh **belum diambil**. Bukti bahwa pengelola tidak berubah hanya dari tes (`ManagerNavigationTest`, `FinanceAdminUiTest`, dan `FinanceEntryUiTest` per nama) dan pembacaan kode.

## Delivery Gate antislop (mode during)

Dijalankan sebagai pemeriksaan kode dan screenshot emulator. Klik menyeluruh pada setiap kontrol tidak dilakukan.

| Blok | Hasil | Bukti dan batas |
|---|---|---|
| 1 Hard Gate | PASS dengan batas | Tidak ada em dash di teks UI baru. Tidak ada angka karangan di UI produksi; angka contoh hanya di fixture tes. Keadaan kosong, memuat, dan galat ada di Beranda, Riwayat, dan alur nutrisi. Tombol dan kartu punya perilaku nyata (tes UI). Kontras dihitung dari token yang sudah diperiksa pada Canopy; kontras baru (chip amber pada gelap, teks gelap pada sage) terbaca di screenshot tetapi tidak diukur. Lebar 360 dp dan font 200% hanya diperiksa pada Beranda. R-35: tes UI menjalankan alur utama; klik setiap kontrol satu per satu tidak dilakukan |
| 2 Purpose-Gate | PASS | Tidak ada gradien, glow, kaca, atau badge dekoratif baru. Ikon relevan dengan isi (centang, peringatan, tetes, keranjang). Gerak memakai `farmStaggerEntrance` sekali per proses dan `farmPressable`; tidak ada animasi tak berhingga |
| 3 Liveliness | PASS | Dial 2/2/2 untuk Beranda dan 1/2/2 untuk form dan riwayat. Satu aksen sage. Motif daun di kartu forest Beranda. Satu fokus per layar |
| 4 Craftsmanship | PASS dengan batas | Keadaan kosong, memuat, dan galat ada. Mode gelap terlihat pada form, Riwayat, dan alur nutrisi; mode terang pada Beranda dan halaman greenhouse. Mode gelap Beranda dan halaman greenhouse tidak difoto |

## Belum ada atau belum terverifikasi

- Perangkat fisik, TalkBack nyata, performa, Doze, dan backend nyata.
- Font 200% dan lebar 360 dp selain Beranda; mode gelap pada Beranda dan halaman greenhouse. Mode malam emulator terkunci pada sesi awal, jadi sebagian screenshot gelap hanya tersedia setelah emulator dinyalakan ulang.
- Bagian spesifikasi yang belum dikerjakan: baris lipat "Belum dicatat dari 7 hari terakhir" (6.3); chip greenhouse nonaktif di sheet Catat untuk greenhouse tanpa tanam (6.4); baris angka utama dua kolom di langkah Periksa (6.11); chip "Diubah" dan teks "Sebelumnya N kg" pada koreksi (6.13); peringatan di luar acuan untuk pH dan EC (6.8); bagian "Jenis kegiatan" dan "Waktu kegiatan" yang terpisah (6.7); judul "Pengukuran terakhir" (komponen dipakai bersama pengelola); total panen di langkah Periksa masih bertitik desimal (komponen dipakai bersama pengelola).
- Chip greenhouse di Riwayat belum punya tes UI khusus karena fixture lama hanya punya satu greenhouse; fixture dua greenhouse kini ada dan bisa dipakai.
- Alur cek nutrisi diuji sampai activity dibuat ulang, bukan matinya proses. Draf tersimpan di perangkat per langkah, jadi data tidak hilang, tetapi layar Periksa semua tidak dipulihkan setelah proses mati (draf tampil sebagai Draf di Riwayat).
- Screenshot pengelola sebelum dan sesudah (lihat di atas).
- Dua tautan lama yang rusak pada `check-finance-context.py` belum diperbaiki; menunggu keputusan apakah diarahkan ke dokumen yang ada.

## Pertanyaan terbuka

- Apakah dua tautan lama yang rusak (`MASTER_PROMPT_MANAGER_SUPERADMIN.md`, `MASTER_PROMPT_WORKER_FLOW.md`) diarahkan ke dokumen yang ada atau dibiarkan?
- Apakah bagian spesifikasi yang belum dikerjakan perlu fase lanjutan, terutama baris lipat 7 hari dan bentuk Periksa dua kolom?
- Perlu keputusan soal total panen bertitik desimal di langkah Periksa: mengubahnya menyentuh tampilan pengelola.
