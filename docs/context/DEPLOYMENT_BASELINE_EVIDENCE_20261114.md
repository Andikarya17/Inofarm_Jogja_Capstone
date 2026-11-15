# Bukti fase D0: baseline dan reproduksi P0-1

Tanggal 14 November 2026. Basis kode `origin/main` `6eef4c7`, branch `codex/deploy-baseline`. Rujukan: [audit deployment](DEPLOYMENT_READINESS_AUDIT_20261114.md), [audit frontend](FRONTEND_MOTION_AUDIT_20261114.md), [master prompt](MASTER_PROMPT_FRONTEND_DEPLOYMENT_20261114.md).

Dokumen ini memisahkan tiga jenis pernyataan: **diuji** (ada output perintah pada sesi ini), **dibaca dari kode**, dan **belum diverifikasi**.

## Baseline (diuji pada sesi ini)

Lingkungan: Windows 11, PHP 8.5.11 (`.tools/php`), MySQL di `127.0.0.1:3307` yang sudah menyala sebelum sesi, JDK `jdk-25.0.4.101-hotspot`.

| Pemeriksaan | Perintah | Hasil |
|---|---|---|
| Guard database uji | `scripts/test-backend.ps1` | 4 tes, 12 assertion, lulus |
| Backend PHPUnit, hanya `inofarm_test` | `scripts/test-backend.ps1` | 157 tes, 3.972 assertion, 0 gagal |
| Android | `scripts/build-android.ps1` (`assembleDebug`, `testDebugUnitTest`, `lintDebug`) | BUILD SUCCESSFUL; JVM 112 tes, 0 gagal; lint 0 error, 18 warning, 1 hint |
| Kontrak | `scripts/check-finance-contracts.py` | Lulus (exit 0) |
| Context | `scripts/check-finance-context.py` | Lulus, 445 tautan, 0 error |
| Advisory dependency | `composer audit --locked` (`.tools/composer.phar`) | 7 advisory pada 4 paket (exit 1) |

Angka ini sama dengan audit 14 November. Setelah tes baru di bawah, suite backend menjadi 158 tes.

## P0-1: terbukti dari schema dan status migration

**Diuji, hanya membaca:**

- `php artisan db:show` pada konfigurasi efektif: koneksi `mysql`, database `inofarm_finance_local`, port 3307.
- `php artisan migrate:status` pada koneksi itu: `2026_11_10_000020_create_harvest_reviews` berstatus **Pending**; migration 19 berstatus Ran.
- Daftar tabel dari `db:show`: `inofarm_finance_local` punya `harvests`, `harvest_items`, `harvest_reference_snapshots`, dan **tidak punya** `harvest_reviews`. `inofarm_test` punya keempatnya.

**Diuji di `inofarm_test`:** tes baru `backend/tests/Feature/WorkerSyncClientFlagsTest.php` memanggil `GET /api/v2/sync/changes?activity_reviews=1&measurement_review_status=1&harvest_reviews=1` sebagai pekerja yang punya penugasan, lalu menarik halaman berikutnya dengan cursor. Hasil: 200 pada keduanya (1 tes, 9 assertion). Jadi dengan schema lengkap, kombinasi flag yang selalu dikirim Android berjalan.

**Dibaca dari kode:** `ChangeFeedController` membuat subquery ke `harvest_reviews` untuk akun selain pengelola bila `harvest_reviews=1` (baris 65 sampai 70), dan `FinanceSyncCoordinator.kt` selalu mengirim flag itu.

**Kesimpulan:** tabel yang dibaca query itu tidak ada di database aplikasi, sehingga tarikan data pekerja ke backend laptop akan gagal dengan galat SQL. Penyebabnya migration yang tertinggal, bukan kode.

**Belum diverifikasi:** galat HTTP itu sendiri tidak direproduksi terhadap `inofarm_finance_local`. Reproduksinya butuh login pekerja (menulis baris token) dan `sync/changes` mengunci baris `sync_state`, sedangkan fase ini tidak boleh mengubah database pengguna. Verifikasi "200 sebagai pekerja" pada database aplikasi dilakukan di fase D1 setelah migration.

## Catatan samping

- Akun MySQL yang dipakai aplikasi bisa melihat dua schema sekaligus (`inofarm_finance_local` dan `inofarm_test`, total 127 tabel di `db:show`). Karena itu `php artisan db:table harvest_reviews` tanpa nama schema menampilkan tabel milik `inofarm_test` dan bisa menyesatkan. Untuk server pilot, akun aplikasi sebaiknya hanya punya hak pada satu database (masuk runbook D5).
- `composer` tidak ada di PATH laptop ini; pakai `php .tools/composer.phar`.

## Tidak dijalankan pada fase ini

`assembleDebugAndroidTest`, instrumentasi Android, emulator, perangkat fisik, dan permintaan HTTP apa pun ke backend dengan database aplikasi.

## Keputusan gerbang pengguna (14 November 2026)

| Gerbang | Jawaban |
|---|---|
| G1 | Pilot memakai `inofarm_finance_local` di laptop; migration 20 boleh dijalankan setelah backup |
| G2 | Sesi ini hanya menyiapkan runbook, tidak men-deploy |
| G3 | Pemilik membuat dan menyimpan keystore rilis sendiri |
| G4 | Masa sesi 7 hari, lewat env contoh |
| G5 | Nilai pilot: lampiran JPG/PNG/PDF maksimal 5 MB dan 5 berkas; backup harian, retensi 14 hari, RPO 24 jam, RTO 1 hari kerja, operator pemilik. Bukan keputusan produksi |
| G6 | `androidx.profileinstaller` dan modul `:baselineprofile` boleh; `core-splashscreen` hanya bila atribut tema tidak cukup |
| G7 | Email terakhir boleh diingat, dengan aksi "Bukan saya" |
