# Canopy: baseline Fase 0 (12 November 2026)

Branch `codex/canopy-baseline`, dari HEAD `06aff0f`. Tidak ada kode produksi yang berubah. Rencana: [MASTER_PROMPT_FRONTEND_CANOPY.md](MASTER_PROMPT_FRONTEND_CANOPY.md). Temuan desain: [FRONTEND_CANOPY_AUDIT_20261111.md](FRONTEND_CANOPY_AUDIT_20261111.md).

## Bukti baru

- Screenshot "sebelum" diambil dari fixture MockWebServer di `FinanceEntryUiTest` (tes sementara, dibatalkan dengan `git checkout`, tidak di-commit). Emulator `Inofarm_Manager_Polish_API30`, 1080x2340, 420 dpi. Berkas: `outputs/frontend-canopy-20261112/before/` (diabaikan git): login, Beranda, detail greenhouse, sheet Catat, Riwayat, Keuangan, Kelola.
- Ini emulator dan fixture sintetis, bukan perangkat fisik dan bukan backend nyata. Tes dasar (JVM, lint, instrumentasi) belum dijalankan di Fase 0.
- Screenshot mengonfirmasi L-01, L-03, B-01 (judul dan tombol sama-sama "Semua greenhouse") dan N-01 (FAB Catat menutupi kartu ketiga).

## Perbedaan master prompt bagian 3 dengan HEAD

| Klaim | HEAD |
|---|---|
| `FinanceBackendDeviceTest.kt:619` dan `:687` satu-satunya rujukan `finance-tab-4` usang | `finance-tab-4` dirujuk 30 kali di 7 file tes. Sekitar 22 memakai `performClick()` (bukan hanya `assertDoesNotExist`). Bar pengelola hanya `finance-tab-0..3` (`FinanceNavigation.kt:26`); Keuangan kini `finance-tab-2` |
| Tes yang memakai `manager-home-picker` | 14 rujukan di 7 file; semua harus dialihkan ke `manager-greenhouse-{id}` di Fase 4 |
| `finance-tab-1` | 22 rujukan; sekarang Riwayat, perlu dicek per tes apakah dulunya Cek catatan |
| `finance-record-fab` | 5 rujukan (`FinanceEntryUiTest` 3, `FinanceNavigationActions` 2) |
| Nomor baris `FinanceRoot.kt` (631, 657-662, 679, 714) dan `FarmMotion`/`FarmShape`/`FontFamily.SansSerif` | Cocok |
| Font K6 | Berkas dan hash cocok; belum di-commit |

Belum diverifikasi ulang di HEAD: nomor baris `FinanceLogin.kt`, `ManagerHomeScreen.kt`, `HistoryScreen.kt`, `ActivityVerificationScreen.kt`, `FinancialEntryScreen.kt`. Ketersediaan `PullToRefreshBox` di material3 terpasang belum diperiksa (diputuskan di Fase 3).

## Matriks ringkas

| Requirement | Acceptance | File utama | Tes yang tersentuh |
|---|---|---|---|
| Token, komponen, ikon, font (5.1, 5.2, K6) | Build, JVM, lint lulus; tidak ada layar berubah | `core/design/InofarmTheme.kt`, `FarmChrome.kt`, `res/font` | JVM untuk fungsi murni baru |
| Login (5.3) | Instrumentasi login lulus; screenshot terang, gelap, 200%, landscape | `feature/auth/FinanceLogin.kt` | `finance-email`, `finance-password`, `finance-password-toggle`, `finance-login` |
| Shell dan dock pengelola (5.4, 5.12) | Catat di bar tengah membuka sheet; pekerja tidak berubah | `FinanceRoot.kt`, `FinanceNavigation.kt`, `ManagerSheets.kt` | `finance-record-fab`, `finance-tab-0..3`, `record-*` |
| Beranda dan detail (5.5, 5.6) | Tes Beranda lulus | `managerhome/ManagerHomeScreen.kt` | `manager-home-picker`, `manager-greenhouse-*`, `manager-add-greenhouse`, `manager-review-inbox`, `hub-section-*`, `finance-new`, `harvest-new`, `finance-measurement-new` |
| Riwayat dan Cek catatan (5.10, 5.11) | `choose()` tetap lulus | `history/HistoryScreen.kt`, `verification/ActivityVerificationScreen.kt` | `history-chip-*`, `finance-record-*`, `verification-*` |
| Keuangan dan Kelola (5.7, 5.8a) | Tes keuangan, aset, investasi, laporan, recovery, admin lulus | `expenses/FinancialEntryScreen.kt`, `ManagerSheets.kt` | `expense-gh`, `expense-history-kind`, `expense-new`, `finance-tools`, `*-new`, `manage-*` |
| Form dan Periksa (5.13, 5.14) | Tes entri dan device relevan lulus | `FinanceRoot.kt`, `harvest/`, `expenses/`, `sales/`, `assets/`, `admin/` | tag isian form, `*-save-draft`, `*-review`, `*-queue` |
| Polish dan dokumen (5.8, 5.9) | Semua butir bagian 9 | `DESIGN.md`, `CLAUDE.md`, evidence | tes navigasi pekerja |

## Keputusan terbuka untuk Fase 3

Tes yang memakai `finance-tab-4` dan `finance-tab-1` bukan akibat Canopy. Mengubahnya ke `finance-tab-2` memindahkan tag, bukan melemahkan assertion, jadi saya akan memperbaikinya dalam fase yang menyentuh navigasi (Fase 3) dan mencatatnya di sana.

## Fase 0b: tes navigasi diselaraskan (12 November 2026)

Branch `codex/canopy-test-nav`, hanya `androidTest`. Pemetaan per baris menurut maksud, bukan ganti massal.

| Rujukan lama | Jalur sekarang | Jumlah |
|---|---|---|
| `finance-tab-4` / `selectFinanceTab(4)` untuk membuka Keuangan | `finance-tab-2` | 30 baris |
| `assertDoesNotExist` Keuangan pada pekerja atau sesi ditolak | `finance-tab-2` | termasuk di atas |
| `assertDoesNotExist` `finance-tab-4` pada pengelola (`FinanceEntryUiTest.kt:839`, "tidak ada tab kelima") | tetap `finance-tab-4` | 1 baris |
| `finance-admin` (menu akun) dan `finance-admin-accounts` | `finance-tab-3` lalu `manage-greenhouse` / `manage-users` | 5 baris, plus 2 assertion ada/tidak ada |
| `finance-sync` pada pengelola dengan catatan antre (`:1609`, `:1668`) | `openFinanceOutbox()` lalu `queue-send-now` | 2 baris |
| `finance-tab-1` pada tes pekerja dan baris Riwayat pengelola | tetap (Riwayat) | tidak diubah |

Belum dipetakan, maksud tidak jelas: `finance-sync` pada pengelola tanpa catatan antre (`FinanceEntryUiTest.kt:1176`, `:1197`, `:1207`). Tag itu hanya dibuat untuk pekerja (`FinanceRoot.kt:909`). Menunggu keputusan.

### Hasil per kelas (emulator API 30, fixture, sebelum perubahan Canopy)

| Kelas | Tes | Gagal | Dilewati | Catatan |
|---|---|---|---|---|
| `FinanceEntryUiTest` | 64 | 15 | 0 | Sebelum pemetaan: 18 gagal. Tiga hilang setelah pemetaan |
| `FinanceBackendDeviceTest` | 13 | 0 | 13 | Butuh backend dan argumen `devicePhase` (`assumeTrue`) |
| `FinanceAssetBackendDeviceTest` | 3 | 0 | 3 | Sama |
| `FinanceSeasonReportBackendDeviceTest` | 3 | 0 | 3 | Sama |
| `FinanceInvestmentBackendDeviceTest` | 3 | 0 | 3 | Sama |
| `FinanceRecoveryUiBackendDeviceTest` | 1 | 0 | 1 | Sama |
| `FinanceReportExportBackendDeviceTest` | 1 | 0 | 1 | Sama |

Kelas backend dilewati, bukan lulus: perubahan tag di dalamnya belum terbukti berjalan. Hanya pengecekan statis bahwa tidak ada `finance-tab-4` tersisa selain `FinanceEntryUiTest.kt:839`.

### 15 kegagalan `FinanceEntryUiTest` yang tersisa (baris sumber, kondisi)

Penyebab akar belum diselidiki; kolom ini hanya baris yang gagal. Semua ada sebelum Canopy.

| Tes | Baris | Kondisi yang gagal |
|---|---|---|
| `conflictKeepLocalRequiresReasonAndManagerCanAuditAndCancel`, `conflictUseServerPreservesLocalCopyAndWorkerHasNoCancel`, `queueReviewOpensConflictedRecordAndBackReturnsToSameQueue` | 1057 | Menunggu teks "Server · versi 2" |
| `assetConflictReviewPreservesRawLocalNameAndOldOperation` | 1870 | Menunggu teks "Server · versi 2" |
| `managerSeasonCloseAndReopenUseReasonedVersionedQueue` | 1126 | `history-action-confirm` seharusnya nonaktif |
| `twoFieldLoginHasAccessiblePasswordToggleAndHandlesMissingConfiguration` | 645 | Teks "Alamat server belum dikonfigurasi untuk aplikasi ini. Hubungi pengelola." |
| `longContextAndExpiredSessionKeepNavigationReachable` | 726 | `openFinanceOutbox()` menunggu `finance-unsent` |
| `secondaryMenusReflowAndKeepSheetActionsReachable` | 1268 | `openFinanceOutbox()` menunggu `finance-unsent` |
| `managerHomeOneTapFiltersRotationAndCachedSnapshot` | 1139 | Teks "Ringkasan kebun" tidak ada |
| `managerHomeFallsBackWhenSelectedGreenhouseIsRevoked` | 1197 | `finance-sync` pada pengelola (rujukan belum dipetakan) |
| `managerWalkthroughVisitsPickersScheduleToolsAndAdminWithoutSubmitting` | 1309 | Teks "Akhiri periode berjalan" tidak ditemukan |
| `managerRecordsObservationsAndEditsMeasurementTarget` | 1405 | Teks "Catat hasil" tidak ditemukan |
| `harvestOwnCorrectionConflictUsesNewVersionAndRetainsLocalArchive` | 1487 | `performScrollTo()` pada `history-compare` |
| `harvestBalanceRejectionCanBeCorrectedAfterExplicitReview` | 1499 | `performScrollTo()` pada `history-fix-rejected` |
| `attachmentReviewRemainsReachableAtLargeFontNarrowAndLandscape` | 1038 | `performScrollTo()` pada `attachment-review` |

Aturan setelah ini: kegagalan di daftar ini tidak dihitung sebagai akibat Canopy; kegagalan baru di luar daftar ini yang muncul setelah sebuah fase Canopy dihitung sebagai regresi fase itu.
