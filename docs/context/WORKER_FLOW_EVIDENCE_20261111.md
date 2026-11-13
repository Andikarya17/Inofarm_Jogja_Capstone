# Bukti alur pekerja, 11 November 2026

Dasar: master prompt `MASTER_PROMPT_WORKER_FLOW.md` (berkas sudah tidak ada di repo), baseline `f7fe26f`. Lima cabang `codex/`, masing-masing dari ujung cabang sebelumnya, tanpa push. Tidak ada perubahan backend, API, kontrak atau migration. Perilaku dan izin pengelola tidak berubah, kecuali dua salinan teks yang dijelaskan di bagian "Perubahan yang menyentuh pengelola".

| Fase | Cabang | Commit | Isi |
|---|---|---|---|
| 1 | `codex/worker-nav` | `86aa754` | Dua tab, tombol Catat, indikator belum terkirim, sheet pekerja |
| 2 | `codex/worker-home` | `6ab0be3` | Kartu greenhouse, kartu perlu diperbaiki, pesan simpan, sheet konteks |
| 3 | `codex/worker-history` | `c17d234` | Chip Catatan saya, label Draf dan status cek, filter tanggal |
| 4 | `codex/worker-forms` | `20ab2f6` | Label tombol, jenis terkunci, acuan koreksi otomatis, istilah |
| 5 | `codex/worker-flow-docs` | ujung cabang | Penyempurnaan ukuran font, dokumen, bukti |

## Matriks temuan

| # | Temuan | Penerimaan | Berkas utama | Tes |
|---|---|---|---|---|
| 1 | Beranda belum merangkum greenhouse | Tiap greenhouse tugas punya kartu: jadwal hari ini dan pengukuran terakhir; kosong ditulis apa adanya | `feature/context/WorkerGreenhouseCards.kt` | `workerHomeShowsGreenhouseCardsAndCorrectionsCardWithoutOpeningAGreenhouse`, `workerHomeCardShowsLastMeasurementAndSaveMessageAfterRecording` |
| 2 | Tab tidak sesuai tugas | Beranda dan Riwayat; Catat di kedua tab | `feature/FinanceNavigation.kt`, `feature/FinanceRoot.kt`, `feature/ManagerSheets.kt` | `workerNavigationHasTwoTabsRecordButtonAndUnsentSheet`, `ManagerNavigationTest` |
| 3 | Perlu diperbaiki sulit ditemukan | Satu kartu di Beranda membuka daftar semua greenhouse; status cek di baris Riwayat | `WorkerGreenhouseCards.kt`, `WorkerDayAgenda.kt` (`workerCorrections`), `history/HistoryScreen.kt` | tes kartu Beranda, `workerHistoryShowsCheckStatusFiltersToOwnRecordsAndPicksDates` |
| 4 | Konfirmasi simpan tidak terlihat | Pesan muncul di layar tujuan dan hilang sendiri | snackbar di `FinanceRoot.kt`, `clearMessage` di `ActivityViewModel`/`HarvestViewModel` | `workerHomeCardShowsLastMeasurementAndSaveMessageAfterRecording` |
| 5 | Belum terkirim berisi yang terkirim | Pekerja melihat yang belum terkirim atau perlu dicek; tanpa ID dan percobaan; ada Kirim sekarang | `feature/QueueScreen.kt`, toolbar di `FinanceRoot.kt` | tes navigasi |
| 6 | Draf tidak terlihat sebagai draf | Baris draf berlabel "Draf" | `WorkerRecordStatus` di `HistoryScreen.kt` | `workerNativePickersAndImeKeepDraftOnNavigation` |
| 7 | Label tombol rancu | "Simpan sebagai draf" dan "Kirim" | `FinanceRoot.kt`, `harvest/HarvestScreen.kt` | `workerActivityFormNeverOffersMeasurementAndScheduleLocksTheType`, `workerCorrectionHasNoReferenceChoiceAndUsesPlainButtons` |
| 8 | Dua jalan ke form yang sama | Tombol Catat di Beranda dihapus; jenis terkunci dari jadwal, koreksi dan pengukuran; Kegiatan tidak menawarkan Pengukuran | `FinanceRoot.kt` | `workerScheduleEntryHasNoTypeChoice`, tes jenis kegiatan |
| 9 | Koreksi meminta keputusan teknis | Tanpa pilihan acuan; dinilai ulang hanya bila waktu berubah (kegiatan, panen, konflik) | `activities/domain/CorrectionReference.kt`, `FinanceRoot.kt`, `HarvestScreen.kt`, `HistoryScreen.kt` | `CorrectionReferenceTest` (6 tes JVM), tes koreksi |
| 10 | Istilah teknis | Target satu baris, tanpa versi, selisih atau "Kosong bukan nol" | `ReferenceCards` di `FinanceRoot.kt`, `WorkerEventTime.kt`, `ActivityDraft.kt`, `HarvestDraft.kt` | `CorrectionReferenceTest` (target), tes koreksi |
| 11 | Filter riwayat mengetik tanggal | Pemilih tanggal Android untuk pekerja; chip Catatan saya | `HistoryScreen.kt` (`DateFilterButton`), `HistoryRepository.kt`, `ManagerSheets.kt` | tes riwayat |
| 12 | Mode Menu bottom bar | Dihapus; dua tab selalu terlihat | `FinanceNavigation.kt` | `compactNavigationKeepsEveryTabReachableAtLargeFont` |
| 13 | Sheet konteks | Label sesuai urutan; musim tertutup dan kelompok belum ditanam tidak ditawarkan, dengan alasan | `context/HomeContextCard.kt` | `workerSelectsGreenhouseDirectlyAndOpensReadyRecordingContext` |
| 14 | Panel Pembaruan data dan loop 5 detik | Panel dihapus; tanggal mengikuti pergantian hari WIB; satu baris waktu pembaruan di atas kartu | `FinanceRoot.kt`, `WorkerGreenhouseCards.kt` | tes kartu Beranda |

## Hasil pemeriksaan

Semua dari output perintah pada sesi ini. Backend dan kontrak tidak dijalankan karena tidak ada berkas backend atau kontrak yang berubah.

| Pemeriksaan | Hasil |
|---|---|
| `build-android.ps1` (assembleDebug, testDebugUnitTest, lintDebug) | Lulus pada tiap fase |
| Tes JVM | 90 tes pada fase 1–3, 96 pada fase 4 (enam tes `CorrectionReferenceTest` baru), 0 gagal, 0 error |
| Lint | 0 error, 16 warning, 1 hint, sama dengan baseline |
| `python scripts/check-finance-context.py` | `errors: []` |
| Emulator API 30 (`Inofarm_Botanical_API30`), MockWebServer, putaran akhir satu tes per panggilan tanpa pekerjaan lain berjalan | 19 tes alur pekerja: 7 baru (`workerNavigationHasTwoTabsRecordButtonAndUnsentSheet`, `workerHomeShowsGreenhouseCardsAndCorrectionsCardWithoutOpeningAGreenhouse`, `workerHomeCardShowsLastMeasurementAndSaveMessageAfterRecording`, `workerHistoryShowsCheckStatusFiltersToOwnRecordsAndPicksDates`, `workerActivityFormNeverOffersMeasurementAndScheduleLocksTheType`, `workerScheduleEntryHasNoTypeChoice`, `workerCorrectionHasNoReferenceChoiceAndUsesPlainButtons`) dan 12 lama yang dipakai ulang. 17 lulus pada percobaan pertama. `compactNavigationKeepsEveryTabReachableAtLargeFont` dan `logoutFlushesLatestActivityEditorSnapshotBeforeClearingCredentials` gagal sekali, lalu lulus pada ulangan tunggal. Satu putaran sebelumnya, yang berjalan bersamaan dengan build Gradle, gagal pada tiga tes pertama dan dibuang; emulator juga beberapa kali menampilkan dialog ANR "Pixel Launcher isn't responding" dan sekali perlu di-reboot (data aplikasi tetap) |
| Font 200% dan lebar 360 dp | Tujuh tes baru dijalankan pada 200% dan 360 dp. Awalnya dua gagal karena tes menekan tombol yang tertutup tombol Catat atau pesan simpan; sesudah tes diperbaiki semuanya lulus. Review akhir menemukan judul "Inofarm" terpecah per huruf saat indikator belum terkirim tampil; sekarang indikator pindah ke baris sendiri pada font di atas 1,3 untuk pekerja |

Screenshot disimpan lokal (diabaikan git) di `outputs/worker-flow-20261111/`: `before/`, `after/` (100%), `font200/` (200% pada 360 dp). Fixture sintetis dari MockWebServer, bukan backend nyata. Pada `font200/secondary-worker-home-after-record.png` dialog ANR emulator menutupi sebagian layar; tata letak toolbar tetap terbaca. Layar Antrean lama dan form koreksi "sebelum" tidak punya screenshot karena tidak ada tes yang menjangkaunya.

## Temuan review akhir yang diperbaiki

Review `impeccable-finish-reviewer` fase 2 dan fase 5 (laporan model, dicek terhadap kode): kartu greenhouse membedakan "pilih kelompok" dari "tidak ada pengukuran" dan menampilkan status belum terkirim atau belum dicek; daftar perlu diperbaiki hanya memuat greenhouse yang masih terlihat; pencarian cache dikunci pada id dan versi; sheet konteks menjelaskan musim ditutup dan kelompok belum ditanam serta tidak menghapus pilihan sebelum cache selesai dimuat; `workerReady` menunggu tanggal semai; detail yang dibuka dari sheet belum terkirim kembali ke sheet itu; id tab lama dipulihkan ke Beranda; cabang tab 2 dan 3 yang mati dihapus; ruang bawah daftar mengikuti skala font di belakang tombol Catat; `FinanceBackendDeviceTest` memakai tombol Catat. Tidak diperbaiki: label baca-layar khusus untuk kartu (kartu berisi sudah digabung oleh Surface yang dapat diklik) dan penggabungan debounce pembacaan cache.

## Perubahan yang menyentuh pengelola

- Bentuk `QueueScreen`, `RecordSheet`, `RecordTypeChips`, `HistoryScreen` dan `FinanceToolbar` dipakai bersama dan diberi parameter peran; jalur pengelola tidak berubah.
- Tag tombol Catat berganti dari `manager-record-fab` ke `finance-record-fab`.
- Pesan wajib isi EC, PPM, pH dan jumlah buah memakai bahasa biasa untuk semua peran (arti sama).
- Tes Android lama yang memakai `finance-tab-4` dan sejenisnya sudah usang sejak perombakan pengelola dan tidak diperbaiki di sini.

## Belum diverifikasi

- Perangkat fisik, TalkBack nyata, Doze dan latensi. Semantik diperiksa dari kode dan tes Compose saja.
- `FinanceBackendDeviceTest` (Laravel dan MySQL `inofarm_test`): selektornya diperbarui untuk dua tab dan tombol Catat, tetapi **tidak dijalankan**.
- Sisa `FinanceEntryUiTest`: pada HEAD awal semua tes gagal saat login karena salinan teks berubah. Sesudah satu baris diperbaiki, lari parsial seluruh kelas (14 dari 60 tes sebelum dihentikan) menghasilkan 5 lulus dan 9 gagal, semuanya tes pengelola atau keuangan yang usang atau tes login lama. Tidak ada klaim bahwa kelas penuh lulus.
- Pesan simpan selalu "Tersimpan. Terkirim otomatis saat ada internet."; varian "Terkirim" saat online belum ada karena hasil kirim tidak diketahui saat form menutup.
- Filter "Catatan saya" memfilter setelah kueri karena pencatat ada di badan terenkripsi; tidak ada kolom indeks baru (akan memerlukan migration SQLite lokal).
- Tab pekerja lama yang tersimpan di state (nomor 1–3) tidak diproses khusus; state tidak bertahan melintasi pembaruan aplikasi.
