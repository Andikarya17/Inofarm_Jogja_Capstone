# Bukti fase Q2: dry run kesiapan pilot (15 November 2026)

Branch `codex/pilot-dry-run`, bertumpuk di atas `codex/qa-ui-triage` (Q1). Kode aplikasi, backend, API dan schema tidak berubah; yang berubah hanya `FinanceBackendDeviceTest.kt` dan `FinanceNavigationActions.kt` (tes perangkat).

Label bukti: **diuji** (ada output perintah), **dibaca dari kode**, **belum diverifikasi**.

## Lingkungan

- **Bukan server staging dan bukan ponsel fisik.** Server staging belum ada (G2: runbook saja) dan ponsel tidak tersedia di sesi ini. Dry run memakai emulator `Inofarm_Botanical_API30` (Android 11, `-gpu swiftshader_indirect`) terhadap backend Laravel nyata `scripts/start-test-backend.ps1 -Port 8001` (`--env=testing`) dan MySQL 8 lokal port 3307, database **`inofarm_test`** saja, lewat `adb reverse tcp:8001`.
- MySQL sudah berjalan sebelum sesi ini; sesi ini tidak menyalakan atau menghentikannya. Backend uji dinyalakan dan dihentikan oleh sesi ini.
- Data: fixture sintetis `scripts/finance-device-fixture.php create worker-alignment` (dua peran, dua greenhouse), dengan `TestDatabaseGuard` sebelum SQL. Setelah run, ulasan sintetis dan fixture dibersihkan (`cleanup worker-alignment`); manifest di `tmp/` terhapus. Database pengguna (`inofarm_finance_local`, `inofarm_local`) tidak disentuh.

## Hasil per skenario master prompt

| Skenario | Hasil | Label |
|---|---|---|
| 1. Login | Pekerja dan pengelola login ke backend nyata berkali-kali dalam dua tes di bawah | diuji |
| 2. Pekerja mencatat lalu kirim | Panen dua grade dan pengukuran EC/PPM dikirim dan `ACCEPTED` oleh server; mode offline lalu online **tidak** dijalankan terhadap backend nyata (hanya di tes MockWebServer `offlineQueueRecreation…`) | diuji sebagian |
| 3. "Perlu diperbaiki" → koreksi → "Sesuai" | Panen: `workerHarvestReviewRoundTripThroughGuardedBackend` lulus; pekerja tidak bisa membaca `harvest-reviews` (403), pengelola memutuskan "Perlu diperbaiki" dengan alasan, pekerja mengoreksi, pengelola "Sesuai", replay operasi mengembalikan `operation_id` yang sama, progres panen pekerja benar. `verify-worker-harvest`: *PASS: guarded MySQL reconciled two grades, two revisions, two decisions, audit, receipts, feed and sold weight.* Kegiatan: `managerVerifiesWorkerRevisionsThroughGuardedBackend` lulus, termasuk kartu pengukuran Beranda pengelola yang menampilkan PPM 630 berstatus Sesuai | diuji |
| 4. Panen → penjualan → biaya → laporan musim → ekspor PDF/CSV | Tidak dijalankan pada dry run ini. Alur ini punya bukti FIN14 lama (API29 + rekonsiliasi SQL) sebelum redesain Canopy | belum diverifikasi |
| 5. Logout dengan draf tersisa | Tidak dijalankan terhadap backend nyata; tes MockWebServer `logoutFlushesIncomplete…` lulus di F5 dan Q1 | belum diverifikasi di backend nyata |
| 6. Doze dan restart dengan antrean berisi | Tidak dijalankan | belum diverifikasi |
| 7. TalkBack | Tidak dijalankan | belum diverifikasi |

## Perubahan tes perangkat

Dua tes perangkat masih mengikuti navigasi pengelola sebelum keputusan super admin 11 November, sehingga gagal sebelum menyentuh logika:

- Inbox Cek catatan kini dibuka dari pintasan Beranda `manager-review-inbox` (helper baru `openReviewInbox`), bukan tab 1 (kini Riwayat).
- "Simpan keputusan" langsung menyimpan; langkah konfirmasi `verification-submit` sudah tidak ada.
- Label "Diterima server · revisi N" menjadi "Revisi N", dan "Valid" menjadi "Sesuai".

Tidak ada asersi tentang data server yang dihapus.

## Temuan

1. **Permintaan tanpa token dan tanpa `Accept: application/json` menghasilkan 500.** `curl http://127.0.0.1:8001/api/v2/me` menjawab 500 dengan log `Route [login] not defined`; dengan header JSON jawabannya 401 `authentication_required`. Aplikasi Android selalu mengirim header JSON, jadi tidak terdampak, tetapi pemantauan dan pemindai akan melihat 500. Runbook (`codex/deploy-runbook`) sudah mencatatnya; perbaikan backend (render JSON untuk `api/*` atau tanpa redirect tamu) belum dibuat.
2. **`finance-device-fixture.php cleanup worker-alignment` gagal** bila tes verifikasi kegiatan sudah membuat `activity_reviews` (FK `activity_reviews_activity_id_foreign` RESTRICT). Di sesi ini baris ulasan sintetis dihapus lewat skrip sementara di folder scratch dengan guard yang sama, lalu cleanup berhasil. Skrip fixture belum diperbaiki.
3. Sesi aplikasi lama untuk akun fixture yang sudah dihapus membuat login berikutnya macet; dry run memakai `pm clear` pada paket debug emulator sebelum tiap run.

## Yang masih dibutuhkan sebelum mengundang Inofarm

- Server pilot HTTPS (P0-2) dan APK rilis bertanda tangan keystore pemilik (D4) di dua ponsel fisik.
- Skenario 2 offline, 4 sampai 7 di ponsel terhadap server pilot.
- Keputusan pemilik yang masih terbuka: P2-1 (penugasan aktif akun pengelola), uji restore ke database terpisah, PR dan merge.
