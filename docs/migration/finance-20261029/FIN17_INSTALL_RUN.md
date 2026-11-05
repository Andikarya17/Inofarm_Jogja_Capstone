# FIN17 — instalasi dan penggunaan v2

Lanjutan2 November2026: branch `codex/retire-iot-runtime` bergantung pada `codex/manual-ec-ppm-dashboard` (`0d960db`). Finance satu-satunya produk aktif/default; tidak perlu flag. Riwayat integrasi sebelum penghapusan: Checkpoint tersebut kini telah masuk GitHub **`main`** melalui PR #1–38; [evidence integrasi](MAIN_INTEGRATION_20261102.md). [Hasil engineering/gate](FIN16_ACCEPTANCE.md), [cutover/rollback](FIN17_CUTOVER.md), [status implementasi](../../context/IMPLEMENTATION_STATUS.md), [kontrak executable](../../../contracts/v2/README.md). FIN17 dokumen/tooling tersedia; penerimaan dan cutover produksi belum disahkan.

## Ambil source dan dependency

```powershell
git clone --branch main https://github.com/Andikarya17/Inofarm_Jogja_Capstone.git Inofarm
Set-Location -LiteralPath Inofarm
git status --short
```

Checkout existing: periksa status/diff dahulu, fetch remote GitHub aktual, kemudian switch/pull fast-forward branch tersebut. Pilih branch fitur terbaru untuk perubahan yang belum di-merge; `main` memuat baseline integrasi. Jangan reset/stash/delete kerja sendiri. Periksa `git remote -v`; checkout ini memiliki alias `github` dan `origin` ke GitHub yang sama, tanpa mengganti URL origin. Clone baru umumnya memakai origin. Tidak perlu mengambil setiap branch dependency satu per satu.

Lingkungan run baru: Windows/PowerShell7, PHP8.5.12 dengan BCMath/PDO MySQL/fileinfo/mbstring/GD/DOM sesuai composer.lock, MySQL8.0.30, Java25.0.2, Gradle9.3.1/AGP9.1.1, SDK/build-tools37. Gunakan lockfile; `composer check-platform-reqs` menentukan extension wajib. Tidak perlu Node/React, PlatformIO, firmware, ESP32, sensor atau credential DB di Android. PHP minimal mengikuti dependency lock (Symfony MIME8.1.10 membutuhkan PHP≥8.4.1); jangan menyimpulkan PHP8.3 kompatibel dari framework saja.

```powershell
Set-Location backend
composer install --no-interaction --prefer-dist
composer check-platform-reqs
# Hanya bila .env belum ada:
if(-not(Test-Path -LiteralPath .env)) {Copy-Item -LiteralPath .env.example -Destination .env}
```

Isi `.env` dengan DB development milik Anda dan APP_KEY sendiri. Jangan menyalin `.env`/password mesin teman. Default contoh `inofarm_local` adalah nama historis, bukan izin menyentuh DB pengguna yang sudah berisi data. Untuk **database development baru/kosong yang Anda siapkan khusus**, lakukan `php artisan key:generate`, tinjau `php artisan migrate --pretend`, lalu `php artisan migrate`. Pada database existing, ikuti gate migration/cutover lebih dahulu. Tidak ada migrate:fresh/reset/seed otomatis, konversi tank→GH/event→aktivitas/roster→akun, atau import transaksi palsu.

Migration additive target sampai **schema15**, local SQLite **schema9**; tidak mengedit migration terdahulu. Registry identity schema8 memerlukan preflight pada data lama. `php artisan inofarm:finance-identities --json` membaca ringkasan collision/missing identity; bukan alat auto-repair. Production deployment belum diotorisasi.

## Akun, API dan perangkat

```powershell
php artisan inofarm:finance-user email-anda --name="Nama pengelola" --role=manager
php artisan serve --host=127.0.0.1 --port=8000
```

Masukkan password lewat prompt tersembunyi, minimal12karakter. Provisioner tidak membuat assignment/greenhouse. Di Android, buka **Menu akun → Kelola akun, budidaya dan acuan** sebagai pengelola untuk menambah greenhouse. Server memberi assignment pencipta secara atomik. Lanjut varietas/musim/kelompok, satuan/bahan/grade/kategori, akun pekerja dan penugasan/jadwal/target sesuai data usaha. Akun owner legacy bukan manager v2. Data produksi/target/harga/diskonto tidak diisi dari fixture.

```powershell
Set-Location ..
$env:JAVA_HOME='C:/lokasi/JDK'
./scripts/build-android.ps1 -JavaHome $env:JAVA_HOME -SdkPath "$env:LOCALAPPDATA/Android/Sdk" -Tasks @(':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug')
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Build normal langsung finance tanpa flag. Paket review **`com.inofarm.jogja.finance`**, Android10/API29 minimum, target37. `install -r` mempertahankan data. Login menerima Email dan Password, dengan alamat API dari konfigurasi build. Default debug emulator adalah `http://10.0.2.2:8000/api/v2/`. Untuk perangkat USB, jalankan `adb reverse tcp:8000 tcp:8000` dan tambahkan `-PinofarmFinanceDebugApiUrl=http://127.0.0.1:8000/api/v2/` ke daftar argumen Gradle. Berikan izin jaringan lokal bila Android meminta.

Paket release mengambil endpoint HTTPS v2 dari `-PinofarmFinanceApiUrl=<alamat-api>` serta mengikuti rencana signing dan distribusi. Saat memasang APK pengganti, konfigurasi alamat server yang sama dengan sesi asal agar setelah masuk kembali akun menemukan draf dan antrean pada scope server yang sama. Jika alamat tidak valid atau kosong, layar Masuk menampilkan pesan konfigurasi.


## Upgrade pengukuran manual dan batas identitas

Versi0.2.0/code2 mempertahankan debug/profile `com.inofarm.jogja.finance`, release `com.inofarm.jogja`, provider `${applicationId}.finance-files`, JobScheduler1601, nama DB SQLite9 dan vault. Update `install -r` dengan signing yang sama; tidak uninstall/clear app data. Build flag lama tidak memilih runtime lain. Release masih memerlukan signing/distribusi/approval produksi; APK debug bukan release.

Schema15 additive menambah nullable `activities.manual_ec` dan index; EC historis tetap null/absent. Update server/schema dahulu sebelum submit jenis `measurement`; audit/receipt/hash antrean lama dipertahankan. Backup/restore menuntut schema target yang cocok; jangan memaksa paket schema14 masuk schema15 atau rollback drop kolom. Identitas legacy `com.inofarm.jogja` mungkin masih menyimpan data owner: tetap tersimpan, tanpa auto-import tank/event atau penghapusan; release upgrade perlu inventaris data dan signing review tersendiri.

Beranda→pilih GH/musim/kelompok→**Catat pengukuran**→EC mS/cm dan PPM ppm→review→antrekan. pH opsional; tidak perlu bahan. Kedua kartu dari satu record waktu ukur terakhir WIB, fallback kemarin/sebelumnya, queued jelas dan missing bukan0. [Kontrak](../../../contracts/v2/MEASUREMENTS.md). Sumber/tooling firmware/React/v1 tidak ada pada produk aktif; histori tabel/migration/archive tetap.

## Alur penggunaan dan pemulihan kesalahan

- **Pekerja:** pilih GH/musim/kelompok→kegiatan manual/panen→review→simpan draf/antrekan. Lokal, antre, accepted, conflict, rejected, scope revoked dan epoch review ditampilkan terpisah. Riwayat/audit/koreksi mengikuti kewenangan server.
- **Pengelola:** buka **Keuangan** untuk mencatat pengeluaran dan melihat riwayat. **Alat keuangan** berisi alokasi biaya, penjualan lunas, aset, penyusutan, evaluasi musim, investasi dan cadangan. Ekspor PDF/CSV tersedia dari detail laporan yang sudah diterima. Alasan, sumber dan versi tetap dapat ditinjau.
- **Admin:** satu draf/journal per server+akun, autosave dan Back/ON_STOP flush. Pengajuan perlu review online. Jika respons terputus, buka lagi pengajuan tertunda dan retry operasi yang sama; tidak membuat ID baru. Password akun tidak disimpan; retry account mutation memerlukan password awal yang sama. Konflik tampil lokal/server dan memerlukan pilihan eksplisit. Jangan menghapus pending yang belum diketahui hasil server.
- **Sync:** tombol Sinkronkan tersedia; job persistent network-aware minimal15menit didaftarkan saat login/restore. OS/Doze/izin/force-stop dapat menunda; waktu itu bukan SLA. Expired/revoked memerlukan login akun yang sama; draf/outbox tetap disimpan. Job tidak mengirim admin journal atau melepaskan epoch hold.
- **Restore epoch:** masuk ulang, baca server baru, tinjau receipt historis. Receipt cocok dapat diakui eksplisit; missing receipt bukan bukti operasi belum pernah accepted. Salinan lokal berbeda tetap disimpan. Jangan re-ID/replay/rebase/purge otomatis. [Kontrak recovery](../../../contracts/v2/RECOVERY.md).
- **Bukti/backup:** upload produksi tetap tertahan sampai Q07 max bytes/count/MIME disepakati. Backup hanya manager yang public UUID-nya di allowlist `FINANCE_RECOVERY_OPERATORS` server. Allowlist kosong berarti nonaktif. Tidak ada unduhan dump/public path di app. Jadwal/retensi/RPO/RTO/key escrow masih Q08; CLI restore-isolated hanya fresh owned MySQL `inofarm_test` berbeda instance, tidak menerima produksi.

## Verifikasi efisien dan paket handover

Siapkan `.env.testing` dari `.env.testing.example` hanya bila belum ada; schema harus **inofarm_test**, DB_URL kosong, akun SQL khusus yang hanya memiliki privilege schema itu. Jangan memakai kredensial development/produksi. Guard dijalankan sebelum SQL. Tidak menjalankan PHPUnit/RefreshDatabase saat fixture HTTP/device/recovery aktif. Runner menolak port8001 atau manifest fixture existing dan tidak membersihkan milik proses lain.

```powershell
python -m venv .tools/finance-contracts-venv
& .tools/finance-contracts-venv/Scripts/python.exe -m pip install jsonschema
./scripts/test-finance-readiness.ps1 -JavaHome $env:JAVA_HOME
# Optional device regression; pilih ID dari adb devices:
./scripts/test-finance-readiness.ps1 -JavaHome $env:JAVA_HOME -SkipBuild -SkipBackend -Device emulator-5554
# Dari tracked source yang sudah committed:
./scripts/package-finance.ps1 -JavaHome $env:JAVA_HOME
```

Runner menyimpan hasil per langkah PASS/FAIL/SKIPPED dan commit di `outputs/finance-readiness/`; pilihan skip tidak dianggap lulus. Default backend menjalankan semua tes aktif (`.*`), termasuk penolakan route IoT; tes khusus implementation v1 yang telah dilepas tidak lagi dijalankan. Guard testDB tetap wajib. **Jangan menjalankan kedua runner bersamaan.** Oracle/concurrency/actual restore/reboot lengkap mempunyai runner masing-masing; pilih sesuai source yang berubah, bukan mengulang semua untuk perubahan dokumentasi.

Packager membangun finance debug dari tracked checkout bersih, memverifikasi identitas APK+minSdk29 dengan aapt2, lalu menyalin APK/manifest/INSTALL/SHA256SUMS dan ZIP ke `output/finance/`. Tidak memerlukan firmware, tidak memasukkan .env/log/dump/private proof, tidak deploy atau mengubah APK pengguna. Data historis/desain26Oktober yang belum ditinjau tetap tidak dipublikasikan. Server/database/signing keys tidak disertakan; pemilik menyiapkan environment sendiri.

Jika PDF500, jalankan composer install dari lockfile dan platform checks sebelum mengubah rumus/server. Jika login gagal, periksa URL/API v2/izin/token serta role assignment, bukan menghapus database/app-data. Daftar kosong pada akun baru sah hingga master dibuat. Semua batas/gate tersisa dicatat pada [FIN16](FIN16_ACCEPTANCE.md) dan [FIN17](FIN17_CUTOVER.md).
