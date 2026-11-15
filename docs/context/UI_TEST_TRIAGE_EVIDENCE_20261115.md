# Bukti fase Q1: triase tes UI (15 November 2026)

Branch `codex/qa-ui-triage`, bertumpuk di atas `codex/frontend-polish` (F5). Hanya `FinanceEntryUiTest.kt` yang berubah; kode aplikasi, backend, API dan schema tidak berubah. Tidak ada asersi yang dihapus.

Label bukti: **diuji** (ada output perintah), **dibaca dari kode**, **belum diverifikasi**. Emulator `Inofarm_Botanical_API30` dengan `-gpu swiftshader_indirect`, MockWebServer.

## Perubahan

- Pencarian node tepat setelah navigasi (`finance-queue`, `attachment-queue`, `conflict-reason`, `harvest-weight-0`, `asset-new`) kini memakai `readyNode`, yang menunggu sampai node ada (paling lama 10 detik), bukan gagal pada frame pertama.
- `workerAgendaDistinguishesRealizationsAndSkippedRecords` menunggu kartu "Jadwal hari ini", yang baru muncul setelah snapshot perangkat terbaca. Dugaan ketergantungan jam diuji: memindah jadwal fixture ke 00.05 WIB tetap gagal, dan 08.00 WIB juga gagal sebelum penantian ditambahkan. Penyebabnya penantian, bukan jam. Jam fixture dikembalikan ke 08.00 WIB.
- Teks yang diganti dalam redesain Canopy dan dalam keputusan super admin (11 November): "Alamat aplikasi belum diatur. Hubungi pengelola.", "Akses akun belum dipastikan tercabut karena tidak ada koneksi.", panel konflik "Data terbaru · versi N" dan "Isian saya · versi N".

## Hasil

| Run | Hasil | Label |
|---|---|---|
| `FinanceEntryUiTest` penuh setelah perubahan (sebelum perbaikan agenda) | 65 tes, 16 gagal (F5: 17) | diuji |
| `workerAgendaDistinguishesRealizationsAndSkippedRecords` setelah penantian | 3 dari 3 lulus | diuji |
| Perkiraan suite penuh dengan perbaikan agenda | 15 gagal; belum dijalankan ulang penuh | belum diverifikasi |

Target "0 gagal konsisten" **tidak tercapai**. Sisa kegagalan bukan masalah waktu tunggu: node atau teks yang dicari memang sudah tidak ada karena layarnya didesain ulang, atau asersinya mengikuti aturan sebelum keputusan super admin. Memperbaikinya berarti menulis ulang alur tes terhadap layar Canopy, bukan menambah penantian.

## Status per nama

| Tes | Status akhir | Penyebab |
|---|---|---|
| `workerAgendaDistinguishesRealizationsAndSkippedRecords` | lulus 3/3 | penantian snapshot (diperbaiki) |
| `workerHistoryShowsCheckStatusFiltersToOwnRecordsAndPicksDates` | lulus di suite Q1 dan 2/2 sendiri | flake waktu sinkron; gagal sekali di F5 |
| `assetHubRemainsScrollableWithFont200Percent` | lulus di suite Q1 | `asset-new` kini ditunggu |
| `offlineQueueRecreation…`, `logoutFlushesIncomplete…`, `attachmentReason…` (2) | lulus di F5 dan Q1 | flake yang dicatat di F4; `logoutFlushes…` pernah gagal dengan `Detected multithreaded access to SnapshotStateObserver` |
| `twoFieldLoginHasAccessiblePasswordToggleAndHandlesMissingConfiguration` | gagal | dua teks diperbarui; masih menunggu pesan logout offline yang tidak muncul tepat satu kali; perlu diperiksa di layar |
| `conflictKeepLocalRequiresReason…`, `conflictUseServerPreservesLocalCopy…`, `queueReviewOpensConflictedRecord…` | gagal | panel konflik kini "Data terbaru · versi N"; teks diperbarui tetapi panel belum muncul dalam 10 detik; alur buka konflik dari Riwayat perlu ditulis ulang |
| `assetConflictReviewPreservesRawLocalNameAndOldOperation` | gagal | mengasersikan "Pakai isian saya" nonaktif tanpa alasan; sejak 11 November pengelola tidak wajib alasan. Asersi tidak diubah tanpa keputusan |
| `managerSeasonCloseAndReopenUseReasonedVersionedQueue` | gagal | sama: mengasersikan tombol konfirmasi nonaktif tanpa alasan, padahal alasan pengelola kini opsional |
| `managerWalkthroughVisitsPickersScheduleToolsAndAdminWithoutSubmitting`, `managerRecordsObservationsAndEditsMeasurementTarget` | gagal | mencari "Akhiri periode berjalan" dan "Catat hasil" dari halaman greenhouse lama; halaman detail Canopy memakai "Ganti tanam saat ini" dan jadwal berbeda |
| `managerHomeOneTapFiltersRotationAndCachedSnapshot`, `managerHomeFallsBackWhenSelectedGreenhouseIsRevoked` | gagal | mencari "Ringkasan kebun" dari Beranda pengelola lama |
| `longContextAndExpiredSessionKeepNavigationReachable`, `secondaryMenusReflowAndKeepSheetActionsReachable` | gagal | `openFinanceOutbox` menunggu tombol "N belum terkirim" yang tidak muncul pada fixture ini |
| `harvestBalanceRejectionCanBeCorrectedAfterExplicitReview`, `harvestOwnCorrectionConflictUsesNewVersionAndRetainsLocalArchive` | gagal | `history-fix-rejected` dan `conflict-reason` tidak muncul pada alur koreksi panen sekarang |
| `attachmentReviewRemainsReachableAtLargeFontNarrowAndLandscape` | gagal | `attachment-queue` tidak muncul setelah Periksa lampiran |

## Yang perlu keputusan

- Dua asersi mengikuti aturan lama "alasan wajib" untuk pengelola. Setelah keputusan super admin, tes perlu diganti menjadi "aktif tanpa alasan, alasan tersimpan bila diisi". Itu mengubah arti tes, jadi belum dilakukan.
- Empat tes walkthrough dan Beranda pengelola, dua tes antrean, tiga tes konflik, dua tes panen dan satu tes lampiran perlu ditulis ulang terhadap layar Canopy.
