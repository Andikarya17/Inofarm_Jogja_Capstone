# Audit kesiapan deployment: konsistensi backend, database, pengelola dan pekerja

Tanggal 14 November 2026. Basis kode: GitHub `main` `9da7308`, yaitu setelah PR #58 (halaman pekerja Canopy, Fase 0–7) di-merge. Dokumen ini membedakan hasil yang dijalankan pada sesi ini dari kesimpulan yang hanya berasal dari pembacaan kode.

## Kesimpulan

**Belum layak untuk deployment produksi. Layak untuk uji coba terbatas (pilot atau UAT) dengan pihak Inofarm setelah lima butir P0 di bawah selesai.** Logika domain, batas akses dan kontrak API sudah matang dan teruji. Sisa pekerjaannya ada di operasional: server yang bisa dijangkau ponsel, database yang tertinggal migration, APK rilis bertanda tangan, patch dependency dan cadangan data.

## Yang dijalankan pada sesi ini

| Pemeriksaan | Hasil |
|---|---|
| Merge `codex/worker-canopy-docs` ke `main` | Tanpa konflik (`git merge-tree`, GitHub "No conflicts with base branch"); PR #58 di-merge |
| Android `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` pada `233c61c` | BUILD SUCCESSFUL; JVM 112 tes, 0 gagal; lint 0 error, 18 warning, 1 hint |
| Backend PHPUnit (`scripts/test-backend.ps1`, hanya `inofarm_test`) | Guard 4 tes/12 assertion; suite 157 tes/3.972 assertion, 0 gagal |
| `check-finance-contracts.py` | Lulus (exit 0) |
| Probe batas akses pekerja (tes sementara, tidak di-commit) | 23 GET khusus pengelola: 22 ditolak 403; 1 (`harvest-progress/sources`) 200, sesuai desain dan tanpa data keuangan. 28 POST khusus pengelola: tidak ada yang berhasil (semua 422, lihat temuan P3-1) |
| Snapshot read-only `inofarm_finance_local` (transaksi `READ ONLY`, di-rollback) | 54 tabel, 75 FK; migration 20 **Pending**; data hanya 1 pengelola, 1 pekerja, 1 greenhouse |
| `composer audit --locked` | 7 advisory (1 low di `laravel/framework`, 6 tanpa skor di `symfony/http-foundation`, `http-kernel`, `routing`) |

Tidak dijalankan: instrumentasi Android, emulator, perangkat fisik, backend nyata dari UI, pentest.

## Temuan dan saran

### P0: harus selesai sebelum Inofarm mencoba aplikasi

**P0-1. Database aplikasi tertinggal migration 20 dan sinkronisasi pekerja akan gagal.**
`inofarm_finance_local` belum punya tabel `harvest_reviews` (`2026_11_10_000020_create_harvest_reviews` berstatus Pending). Android terbaru selalu mengirim `harvest_reviews=1` ke `GET sync/changes` (`FinanceSyncCoordinator.kt:115`). Untuk akun selain pengelola, `ChangeFeedController` lalu membuat subquery ke `harvest_reviews`. Akibatnya, menurut pembacaan kode, setiap tarikan data pekerja berakhir dengan galat SQL. Progres panen (`HarvestProgress`), verifikasi panen dan rekonsiliasi kiriman juga membaca tabel ini. Ini kesimpulan dari kode dan status migration; galatnya belum direproduksi lewat HTTP.
*Saran:* buat cadangan SQL, jalankan `php artisan migrate --pretend`, lalu `php artisan migrate` pada database target, dan verifikasi `sync/changes` sebagai pekerja. Tambahkan langkah "migrate:status harus bersih" pada checklist rilis. Pertimbangkan endpoint kesehatan yang menolak melayani bila ada migration pending.
*Status 14 November 2026:* selesai untuk database laptop. Migration 20 diterapkan pada `inofarm_finance_local` setelah backup, `sync/changes` sebagai pekerja menghasilkan 200 lewat HTTP kernel, dan `migrate:status --pending=1` menjadi penjaga yang diuji. Belum diuji dari aplikasi Android lewat jaringan; database server pilot belum ada. [Bukti D0](DEPLOYMENT_BASELINE_EVIDENCE_20261114.md), [bukti D1](DEPLOYMENT_MIGRATION_EVIDENCE_20261114.md).

**P0-2. Belum ada server yang bisa dijangkau ponsel Inofarm.**
Backend saat ini berjalan lewat `php artisan serve` di laptop (`APP_URL=http://127.0.0.1:8000`, MySQL di `127.0.0.1:3307`). APK rilis hanya menerima HTTPS (`ApiBaseUrl.kt:9`).
*Saran:* siapkan VPS atau server kampus dengan PHP sesuai `composer.lock` (≥8.4.1), MySQL 8, web server (Nginx atau Apache dengan PHP-FPM), domain dan sertifikat TLS (misalnya Let's Encrypt). Set `APP_ENV=production`, `APP_DEBUG=false`, `APP_KEY` baru dan kredensial database baru. Jangan menyalin `.env` laptop.

**P0-3. APK rilis belum bisa dibagikan.**
Build type `release` tidak punya `signingConfig`, dan `inofarmFinanceApiUrl` kosong secara bawaan. Varian `profile` memakai kunci debug dan ditandai "never distributed".
*Saran:* buat keystore rilis yang disimpan di luar repo, tambahkan `signingConfig` yang membaca properti lokal, isi `-PinofarmFinanceApiUrl=https://…/api/v2/`, naikkan `versionCode`/`versionName`, lalu uji APK rilis (minify aktif) di satu ponsel nyata sebelum dibagikan.

**P0-4. Dependency backend punya advisory keamanan.**
`symfony/http-foundation` (4 advisory, termasuk trusted proxy dan URL yang host-nya bisa dipilih klien), `symfony/http-kernel` (forwarded header), `symfony/routing` (bypass akses berbasis path), dan `laravel/framework` (low). Versi terpasang: Symfony 8.1.8/8.1.10.
*Saran:* `composer update "symfony/*" laravel/framework --with-dependencies` dalam batas constraint yang ada, jalankan ulang `test-backend.ps1`, lalu `composer audit` sampai bersih atau tiap sisa advisory dicatat beserta alasannya. Setel `TrustProxies` sesuai reverse proxy server.
*Status 14 November 2026:* advisory selesai. `composer audit --locked` menghasilkan 0 advisory setelah Symfony naik ke 8.1.11 dan Laravel ke 13.42.0; suite backend 159 tes lulus. `TrustProxies` belum disetel karena bergantung pada server pilot. [Bukti D2](DEPLOYMENT_DEPENDENCY_EVIDENCE_20261114.md).

**P0-5. Belum ada cadangan dan jadwal operasional.**
`routes/console.php` hanya berisi perintah contoh: tidak ada jadwal backup, tidak ada `sanctum:prune-expired` (sudah ada 3 token kedaluwarsa di database lokal), tidak ada cron `schedule:run`. Keputusan Q08 (jadwal, retensi, RPO/RTO, operator) dan Q07 (batas lampiran) masih terbuka.
*Saran:* untuk pilot, cukup `mysqldump` harian terenkripsi ke lokasi lain plus satu kali uji restore ke database terpisah. Jadwalkan `sanctum:prune-expired --hours=24`. Putuskan Q07 dan Q08 bersama pengelola Inofarm, minimal untuk masa pilot.

### P1: sebaiknya selesai sebelum atau di awal pilot

**P1-1. 17 tes UI instrumentasi masih gagal.** `FinanceEntryUiTest` penuh: 17 dari 65 gagal di emulator (baseline lama, bukan regresi PR #58). Beberapa menyangkut alur inti: konflik, koreksi panen, Beranda pengelola, tutup musim dan login. *Saran:* triase per nama: perbaiki tes yang usang setelah perombakan UI, dan perbaiki kode bila ternyata ada bug nyata. Targetkan suite hijau sebelum UAT supaya regresi berikutnya terlihat.

**P1-2. Belum ada uji di perangkat fisik dan backend nyata dari UI.** Semua bukti layar memakai emulator dan fixture MockWebServer. Belum diuji: TalkBack nyata, Doze dan pengiriman latar, sinyal lemah di greenhouse, font 200% dan lebar 360 dp di sebagian besar layar. *Saran:* sebelum pilot, lakukan satu hari "dry run" dengan dua ponsel Android 10+ (satu pekerja, satu pengelola) ke server staging. Lakukan juga skenario offline lalu online, catat-koreksi-cek, panen sampai penjualan, laporan musim dan ekspor PDF.

**P1-3. Data awal Inofarm belum ada.** Database lokal hanya berisi data sintetis (1 greenhouse, 1 pengelola, 1 pekerja), dan memang tidak ada seed produksi. *Saran:* siapkan checklist onboarding yang diisi pengelola lewat aplikasi: greenhouse, musim, varietas, kelompok tanam dan "tanam saat ini", satuan, bahan, grade, kategori biaya, jadwal/target, akun pekerja dan penugasan. Tanpa "tanam saat ini", alur "Cek nutrisi semua greenhouse" milik pekerja akan melewati greenhouse tersebut.

**P1-4. Dokumen instalasi sudah usang.** `FIN17_INSTALL_RUN.md` masih menyebut schema 15/SQLite 9 dan "server memberi assignment pencipta", padahal sekarang migration 20/SQLite 10 dan pengelola adalah super admin. *Saran:* perbarui panduan instalasi server dan rilis APK berdasarkan P0-2 sampai P0-5.

### P2: konsistensi data dan perilaku

**P2-1. Sisa penugasan untuk akun pengelola.** Ada 1 baris `greenhouse_assignments` aktif milik pengelola. Setelah keputusan super admin, penugasan hanya untuk pekerja. Baris ini tidak berbahaya karena pengelola melihat semua greenhouse lewat role, tetapi bisa membingungkan daftar "pekerja di greenhouse ini". *Saran:* nonaktifkan lewat API (bukan hard delete), atau pastikan layar penugasan menyaring role pekerja.

**P2-2. Masa sesi 24 jam.** `FINANCE_SESSION_SECONDS` bawaan 86.400. Pekerja lapangan akan diminta login ulang tiap hari. Outbox tetap tersimpan, jadi data tidak hilang. *Saran:* bicarakan dengan Inofarm: 24 jam lebih aman, tetapi 7 hari lebih nyaman. Nilainya bisa diubah lewat env tanpa ubah kode.

**P2-3. Bagian spesifikasi pekerja yang belum dikerjakan** (dari bukti PR #58): baris lipat "Belum dicatat dari 7 hari terakhir", chip greenhouse nonaktif di sheet Catat, angka utama dua kolom di langkah Periksa, chip "Diubah" pada koreksi, peringatan pH/EC di luar acuan, dan total panen bertitik desimal di langkah Periksa. Tidak ada yang memblokir pilot.

**P2-4. Pemisah ribuan pada nominal pengelola, mode gelap/200%/360 dp pada wizard dan form** masih tercatat belum selesai di CLAUDE.md.

### P3: kecil

**P3-1. Validasi berjalan sebelum otorisasi pada tulis khusus pengelola.** Pekerja yang memanggil `POST expenses`, `sales`, `users`, `season-reports/preview` dan sejenisnya dengan payload tidak lengkap menerima 422 beserta daftar field, bukan 403. Dengan payload valid, tes yang ada membuktikan hasilnya 403 (misalnya `POST greenhouses`). Tidak ada data yang bocor, tetapi bentuk payload jadi terbuka dan perilakunya tidak seragam dengan GET (403). *Saran:* panggil pengecekan role pengelola sebelum `OperationInput::read`/validasi pada controller khusus pengelola, lalu tambahkan tes "pekerja + payload kosong = 403".

**P3-2. Lint 18 warning** (naik dari 16 pada baseline lama). Tidak ada error.

## Yang sudah konsisten antara pengelola dan pekerja

- `GreenhouseAccess` menjadi satu sumber cakupan: pengelola melihat semua greenhouse; pekerja hanya melihat penugasan aktif yang efektif. `FinanceSession` menolak akun nonaktif, role lain dan token tanpa ability `finance-v2` di setiap request.
- Semua GET keuangan, akun, penugasan, laporan, investasi dan recovery menolak pekerja (403). Feed `sync/changes` pekerja tidak memuat entitas keuangan (`FinanceExpenseTest`).
- Aturan alasan sudah sesuai keputusan: wajib untuk koreksi pekerja dan "Perlu diperbaiki", opsional untuk pengelola. Replay tetap memeriksa akses setelah penugasan dicabut.
- Kontrak dua arah antara OpenAPI dan route lulus. Backend, kontrak dan migration tidak diubah oleh PR #58, sehingga perubahan pekerja murni di Android.
- Integritas: 75 FK di database lokal, tidak ada pengguna tanpa `public_id`, tidak ada pekerja aktif tanpa penugasan, dan tidak ada greenhouse aktif tanpa musim terbuka.

## Urutan kerja yang disarankan

1. P0-1 pada database yang akan dipakai: backup, migrate, lalu verifikasi sync pekerja.
2. P0-4 patch dependency, lalu jalankan ulang tes backend.
3. P0-2 server staging HTTPS, lalu P0-3 APK rilis bertanda tangan yang mengarah ke server itu.
4. P0-5 backup harian, uji restore, prune token.
5. P1-2 dry run dua ponsel, lalu P1-1 triase tes UI.
6. P1-3 onboarding data Inofarm bersama pengelola, lalu UAT dengan skenario tertulis dan tanda terima.
