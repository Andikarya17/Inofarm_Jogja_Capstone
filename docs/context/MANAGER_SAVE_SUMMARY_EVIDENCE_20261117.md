# Input pengelola: Batal / Simpan, lalu Ringkasan (17 November 2026)

Keputusan pengguna 17 November 2026, setelah [audit alur input pengelola](MANAGER_INPUT_FLOW_AUDIT_20261117.md): untuk semua input pengelola, hapus "Simpan draf" dan langkah "Periksa" yang terpisah. Tombol form hanya **Batal** dan **Simpan**. Simpan membuka **Ringkasan** berbentuk langkah wizard (langkah 2 dari 2) berisi apa yang diisi, dan Simpan di ringkasan yang benar-benar menyimpan. Alur pekerja tidak berubah.

## Backend

Tidak berubah. Server tidak punya status draf: setiap `POST`/`PATCH` langsung commit. Ringkasan sepenuhnya di Android. Laporan musim dan laporan investasi tetap memakai pratinjau server (hash sumber wajib), yang kini menjadi langkah Ringkasan.

## Android

| Input pengelola | Sebelum | Sesudah |
|---|---|---|
| Kegiatan, pengukuran, panen | Simpan draf / Periksa → Periksa → Simpan | Batal / Simpan → Ringkasan (Ubah / Simpan) |
| Pengeluaran, alokasi biaya, penjualan, aset, penyusutan | Simpan draf / Periksa → Periksa → Simpan | Sama seperti di atas |
| Admin, master, akun, penugasan, jadwal, target | Batal / Simpan langsung, dialog konfirmasi hanya untuk tindakan berisiko | Batal / Simpan → Ringkasan; peringatan nonaktif, ubah peran, reset password dan tutup musim pindah ke ringkasan, label tombolnya mengikuti tindakan. Kirim ulang setelah koneksi putus tetap langsung |
| Keputusan cek catatan | Simpan keputusan + Simpan draf + autosave draf | Simpan keputusan → dialog "Ringkasan keputusan" (keputusan, revisi, alasan/catatan) → Simpan. Tidak ada draf |
| Skenario investasi | Simpan skenario / Simpan dan kembali | Simpan → Ringkasan skenario (per tahun) → Simpan; Batal |
| Evaluasi musim, laporan investasi | Lihat dulu → Simpan; Simpan dan kembali | Lihat ringkasan (dihitung server) → Simpan; Batal |
| Wizard tambah greenhouse, tanam saat ini, tutup/buka musim, selesai panen, pembatalan | sudah satu langkah dengan konfirmasi | tidak berubah |

**Batal, X dan Back** menutup langsung bila isian tidak berubah. Bila berubah, muncul "Buang isian ini?" (Buang / Lanjut mengisi).

**Draf tidak tertinggal.** Form tetap menyimpan otomatis selama terbuka (KNF-03: isian selamat bila aplikasi ditutup atau mati; logout juga menyimpan). Saat Batal, `LocalFinanceStore.restoreDraft` mengembalikan salinan perangkat ke keadaan saat form dibuka: catatan baru dihapus, koreksi atas catatan yang sudah diterima kembali ke salinan server (status diterima), dan apa pun yang sudah diantrekan sejak form dibuka tidak disentuh. `EntryOrigin` (di `feature/EntryOrigin.kt`) mencatat baris asal dan isian awal per form.

Berkas utama: `feature/FormCanopy.kt` (`FarmEntrySteps`, `rememberEntryCancel`, `FarmDiscardDialog`, `FarmReviewFrame(summary = true)`), `feature/EntryOrigin.kt`, `core/database/LocalFinanceStore.kt` (`restoreDraft`), tujuh repository (`discard`), view model kegiatan, panen, keuangan, penjualan, aset, evaluasi musim, investasi, `admin/AdminScreen.kt`, `verification/ActivityVerificationScreen.kt`.

Tag tes baru: `{form}-cancel` (Batal), `entry-discard-confirm` / `entry-discard-keep`, `admin-save` (Simpan di ringkasan admin), `admin-summary-back`, `verification-save`, `investment-scenario-review`, `investment-scenario-edit`, `investment-entry-cancel`, `report-cancel`. Tag lama yang hilang untuk pengelola: `expense-/sale-/asset-save-draft`, `investment-save-draft`, `report-save-draft`, `*-draft-status`. Tag pekerja tidak berubah.

## Bukti

Dijalankan 17 November 2026 di laptop, JDK 25.0.4.

| Pemeriksaan | Hasil |
|---|---|
| `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin` | lulus |
| `:app:testDebugUnitTest` | 121 tes, 0 gagal |
| `:app:lintDebug` | 0 error, 18 warning, 1 hint (sama dengan sebelumnya) |
| Instrumentasi terfokus, emulator `Inofarm_Botanical_API30` (Android 11), fixture | 62 tes: `FinanceLocalStoreTest` (termasuk tes baru `batalUndoesAutosaveWithoutTouchingQueuedOrAcceptedRecords`), `FinanceAdminUiTest`, `FinanceAdminTest`, `FinanceSeasonReportTest`, `FinanceInvestmentTest`, dan sembilan tes pengelola di `FinanceEntryUiTest` (draf keuangan/penjualan/aset dengan recreation, panen multigrade, logout, font 200% dengan keyboard dan landscape). 60 lulus pada putaran pertama. Gagal: `FinanceSeasonReportTest.revokedAssignment…` (tes memanggil `back()` yang kini bertanya dulu; disesuaikan, lalu `FinanceSeasonReportTest` 7/7 lulus) dan `managerRecordsObservationsAndEditsMeasurementTarget` (kegagalan baseline: mencari "Catat hasil" yang hilang sejak Canopy, tercatat di [triase Q1](UI_TEST_TRIAGE_EVIDENCE_20261115.md)). Putaran ulang `FinanceAdminUiTest` 4/4 dan tes font 200% pengeluaran lulus |

Tangkapan layar emulator (mode gelap, fixture): [form admin langkah 1](evidence/manager-summary-20261117/admin-form-step1.png), [ringkasan admin langkah 2](evidence/manager-summary-20261117/admin-summary-step2.png), [form pengeluaran font 200% lebar 320 dp dengan keyboard](evidence/manager-summary-20261117/expense-narrow-ime.png). Teks petunjuk ringkasan admin diganti sesudah tangkapan ini ("Simpan langsung mengirim perubahan ke server, jadi perlu koneksi internet."), karena tulis admin tidak lewat antrean.

## Batas

- Belum diuji terhadap backend Laravel/MySQL nyata, ponsel fisik atau TalkBack nyata.
- `FinanceEntryUiTest` penuh tidak dijalankan ulang; suite ini punya kegagalan baseline (sekitar 15–17 dari 65).
- `FinanceBackendDeviceTest` diselaraskan (klik `verification-save`, autosave alokasi menggantikan tombol draf) tetapi butuh backend uji, jadi tidak dijalankan.
- Catatan pengelola yang sudah menjadi draf dari versi sebelumnya tetap ada dan bisa dibuka dari Riwayat; membukanya lalu Batal tanpa perubahan tidak menghapusnya.
