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
