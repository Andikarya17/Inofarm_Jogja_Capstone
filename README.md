# Inofarm — pencatatan operasional dan evaluasi keuangan

Produk aktif: aplikasi Android native Kotlin + Jetpack Compose, Laravel REST API v2 dan MySQL. Pekerja/pengelola memasukkan data manual per greenhouse, musim dan kelompok tanam. Tidak ada koneksi sensor, ESP32, kontrol perangkat atau dosing.

Pengukuran EC (mS/cm) dan PPM (ppm) dicatat bersamaan tanpa harus memberikan bahan nutrisi. Dashboard memiliki dua kartu dari satu catatan menurut waktu ukur WIB: hari ini, kemarin, kemudian histori sebelumnya. Data kosong dan EC historis tidak direka. Beranda pengelola mengecualikan revisi yang dinyatakan tidak valid; catatan pekerja yang diterima server tetapi belum diperiksa diberi label belum diverifikasi. Status itu terpisah dari antrean lokal yang belum tersinkron. pH manual tetap tersimpan pada kegiatan/riwayat.

Fitur mencakup HSS/HST, kegiatan dan bahan, panen, draf/outbox akun, audit/koreksi/pembatalan, biaya/alokasi, penjualan, aset/penyusutan, evaluasi musim, investasi, ekspor serta recovery terkontrol. Lihat [status implementasi dan batas acceptance](docs/context/IMPLEMENTATION_STATUS.md), [kontrak executable](contracts/v2/README.md), [perubahan EC/PPM](contracts/v2/MEASUREMENTS.md).

Melanjutkan dari sesi baru/Claude Code: [CLAUDE.md](CLAUDE.md), [audit sistem10 November](docs/context/SYSTEM_AUDIT_20261110.md), [struktur database](docs/context/DATABASE_INVENTORY_20261110.md), dan [peta API](docs/context/API_ROUTE_MAP_20261110.md). Source `9a1613e` sudah mencakup penyelarasan pengelola PG0–PG7 dan migration19. Database aplikasi lokal `inofarm_finance_local` masih migration16; audit tidak menerapkan migration. Handoff8 November adalah histori, bukan titik lanjut terbaru.

## Build dan run

Prasyarat: PHP sesuai composer.lock (PHP8.5.12 diuji), Composer, MySQL8, JDK25, SDK37.0/build-tools37, Android10/API29+. Tidak perlu npm atau firmware.

```powershell
composer install --working-dir=backend
# Konfigurasi backend/.env sendiri; tinjau backup/preflight sebelum migrate pada data yang ada.
# Provision akun finance secara interaktif, lalu buat master/assignment melalui aplikasi pengelola.
php backend/artisan inofarm:finance-user email@example.com --name="Pengelola" --role=manager
./scripts/build-android.ps1 -JavaHome $env:JAVA_HOME -SdkPath "$env:LOCALAPPDATA/Android/Sdk"
./scripts/run-android.ps1 -JavaHome $env:JAVA_HOME -SkipStudio
```

Build normal langsung membuka finance, tanpa flag. Debug/profile tetap `com.inofarm.jogja.finance`; release tetap `com.inofarm.jogja` dan perlu signing/distribusi tersendiri. Versi0.2.0/code2, minSdk29. Pakai `adb install -r`, tidak uninstall/clear data. SQLite10 pada branch Beranda menambah cache/jurnal secara additive; vault, draf/outbox, nama DB, provider authority dan JobScheduler ID tetap. Data legacy pada identitas lama tidak diubah menjadi catatan manusia. [Panduan lengkap instalasi](docs/migration/finance-20261029/FIN17_INSTALL_RUN.md), [upgrade dan cutover](docs/migration/finance-20261029/FIN17_CUTOVER.md).

## Verifikasi

```powershell
./scripts/test-finance-readiness.ps1 -JavaHome $env:JAVA_HOME
# Device dipilih eksplisit; tidak jalankan HTTP fixtures dan PHPUnit bersamaan.
./scripts/test-finance-readiness.ps1 -JavaHome $env:JAVA_HOME -SkipBuild -SkipBackend -Device emulator-5554
./scripts/package-finance.ps1 -JavaHome $env:JAVA_HOME
```

Backend tests hanya effective MySQL `inofarm_test` dengan guard sebelum SQL. Jangan reset/migrate:fresh/seed database pengguna. Runner melaporkan skip/failure secara terpisah; build/debug bukan persetujuan UAT/pentest/produksi. Packager hanya APK finance dan manifest/hash, tanpa server/env/data/kunci.

Source v1/owner/IoT, firmware/PlatformIO dan prototipe React sudah dilepas dari produk aktif setelah dependensi tema/URL dipisahkan. Tabel/migration historis dan [arsip/provenance](docs/archive/pre-finance-20261029/INDEX.md) dipertahankan; istilah IoT pada arsip/migration/tes penolakan tetap sah. [Inventaris dan keputusan](docs/migration/finance-20261029/MANUAL_MEASUREMENTS_20261102.md). Publikasi GitHub mengikuti instruksi pengguna; SOP akademik tetap gate tersendiri.
