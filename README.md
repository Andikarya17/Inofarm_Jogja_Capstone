# Inofarm: transisi operasional dan evaluasi keuangan

Target aktif29Oktober2026: Kotlin + Jetpack Compose, Laravel REST, MySQL; pekerja/pengelola, pencatatan manual tanpa IoT, operasional dan keuangan. **Build default masih legacy; slice operasional v2 tersedia dengan -PfinanceApp.** Operasional, panen, pengeluaran/alokasi, lampiran privat, dan penjualan lunas sudah memiliki implementasi dan bukti uji bertahap. FIN11 aset/penyusutan sampai FIN17 cutover serta sejumlah gap integrasi belum selesai.

Mulai dari [context pack](docs/context/README.md), [status implementasi nyata](docs/context/IMPLEMENTATION_STATUS.md), [master prompt](docs/migration/finance-20261029/MASTER_PROMPT.md). [Audit perbandingan](docs/migration/finance-20261029/AUDIT_COMPARISON.md) adalah baseline sebelum implementasi; subset kontrak executable di [contracts/v2](contracts/v2/README.md). Instruksi legacy di bawah bukan prosedur migrasi target.

**Serah terima30 Oktober 2026:** gunakan branch GitHub `codex/finance-fin10-handoff` yang memuat seluruh dependency checkpoint; jangan mengandalkan main. Ikuti [cara clone/pull dan setup](docs/migration/finance-20261029/HANDOFF_FIN11_FIN17.md) dan berikan [prompt FIN11–FIN17](docs/migration/finance-20261029/CONTINUE_FIN11_FIN17.md) kepada agent teman. Master prompt ikut disertakan dalam branch tersebut.

## Mode uji operasional v2

Native login dua peran, authorized GH/musim/kelompok, formulir kegiatan manual/multi-bahan, review, draf/outbox terenkripsi, receipt idempoten dan riwayat. UI menyatakan disimpan lokal dan diterima server secara berbeda. APK debug memakai package `com.inofarm.jogja.finance`, minSdk29; tidak menghapus data APK lama.

```powershell
./scripts/build-android.ps1 -JavaHome $env:JAVA_HOME -Tasks @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug', '-PfinanceApp')
```

Set JAVA_HOME/pass -JavaHome sesuai JDK yang terpasang. Runtime lokal yang diuji: Java25.0.4/Gradle9.3.1, PHP8.5.11, MySQL8.0.30. URL v2 dikonfigurasi lewat field login atau property `inofarmFinanceDebugApiUrl`; default emulator `/api/v2/` port8000. Tidak ada kredensial produksi/sample keuangan otomatis.

[Runner entry](scripts/test-finance-entry.ps1) menguji APK terpisah terhadap **inofarm_test saja**: online login→backend dihentikan→offline queue→force-stop test APK→reconnect/replay→manager history, lalu membersihkan hanya fixture UUID miliknya. `.env.testing`/MySQL test harus disiapkan lebih dulu; guard tidak boleh dinonaktifkan. Runner tidak migrate/reset inofarm_local atau clear APK pengguna. [Evidence FIN06](docs/migration/finance-20261029/evidence/FIN06.md) merinci hasil dan batas. UAT/pentest/cutover belum selesai. Publikasi kerja memakai branch GitHub sesuai instruksi terbaru pengguna.

---

# Legacy v1 — referensi instalasi dan histori

Bagian berikut mempertahankan prosedur owner/IoT lama selama coexistence. Cakupannya berbeda dari aplikasi target v2. README asli tersimpan byte-identik di [arsip](docs/archive/pre-finance-20261029/README.md).

Untuk clone dan menjalankan proyek di komputer lain, ikuti [Cara Instalasi](Cara%20Instalasi.md), termasuk setup Android Studio, backend/MySQL, akun lokal, dan koneksi emulator/USB.

Android native Kotlin, Laravel REST API/MySQL, dan firmware ESP32 untuk pencatatan nutrisi manual melon NFT. React di `src/` dipertahankan sebagai referensi tampilan. Tidak ada automated dosing, sensor TDS, input PPM Android, atau login pekerja.

## Hasil

- APK debug: `output/inofarm-jogja-debug.apk`; source `android/` (Compose, ViewModel/StateFlow, repository HTTP, token Android Keystore, Beranda/Riwayat/Pengaturan dan pekerja/shift).
- Backend `backend/`: migrations MySQL, scoped auth, konfigurasi/fase/roster versioned, validasi B, ingestion idempotent, history/filter/CSV.
- Firmware `firmware/`: menu fase/PPM/review, adapter BOM, MicroSD queue/ACK, REST, konfigurasi cache dan pengingat. `output/firmware-headless.bin` **belum untuk pemasangan greenhouse**; pinout tetap kosong.
- [Kontrak API](docs/implementation/API.md), [keputusan teknik](docs/implementation/DECISIONS.md), [bukti pengujian](docs/implementation/VERIFICATION.md).

## Backend lokal pada komputer ini

### Jalankan Android sekali klik (14 Oktober 2026)

Jika aplikasi tidak terlihat setelah launcher selesai, buka **View > Tool Windows > Running Devices** di Android Studio dan pilih tab **Medium Phone API 37.0**. Emulator embedded bisa tetap hidup tanpa jendela tersendiri; Device Manager adalah daftar perangkat. Launcher kini membuka Studio/emulator baru dengan jendela normal dan menyimpan log run terakhir di `.runtime/android-launcher.log`. Jalankan CMD dari **Windows File Explorer**, karena link file di chat dapat membuka editor saja.

Untuk menjalankan MainActivity lewat IDE, buka proyek `android`, pilih konfigurasi **Inofarm MainActivity** dan emulator di toolbar lalu **Run** atau **Shift+F10**. Konfigurasi ini menarget `com.inofarm.jogja.MainActivity` pada modul `InofarmJogja.app`, dengan Gradle build sebelum run. File Activity tidak memerlukan `fun main()` tambahan. Bila membuat konfigurasi manual: Android App, module app, Launch **Specified Activity**, activity `com.inofarm.jogja.MainActivity`, Before launch **Gradle-aware Make**. Backend tetap harus aktif untuk login/data.

Klik dua kali `Run-Inofarm.cmd` di root workspace. Launcher membuka **folder android** sebagai proyek Gradle di Android Studio, memakai perangkat ADB aktif (atau menyalakan AVD `Medium_Phone_API_37.0`), menyiapkan MySQL/API lokal melalui script existing, build debug, ADB reverse port8000, install APK tanpa clear data, lalu membuka MainActivity. Akun owner tetap milik pengguna. Alamat server debug otomatis: emulator `http://10.0.2.2:8000/api/v1/`, perangkat USB `http://127.0.0.1:8000/api/v1/`. Login cukup email/password; alamat dapat diubah lewat **Pengaturan koneksi**. Android17 meminta izin jaringan lokal saat login memakai alamat emulator langsung; izinkan untuk backend komputer. Tidak membuat akun, menjalankan migration/test/fixture atau mengisi konfigurasi agronomis. Terminal launcher boleh ditutup setelah selesai; server lokal berjalan di background, log `.runtime/android-backend.*.log`.

Launcher memakai PowerShell7 dari PATH, instalasi standar, atau runtime Codex yang tersedia pada komputer ini. SDK37, Android Studio JBR dan AVD sudah tersedia saat verifikasi; tidak perlu instal ulang. Untuk komputer lain gunakan `scripts/run-android.ps1 -SdkPath ... -JavaHome ... -StudioPath ... -Avd ...`. Beberapa perangkat aktif memerlukan `-Serial emulator-5554` (sesuaikan `adb devices`). `-SkipBackend` menjalankan APK tanpa menyiapkan API/database; `-SkipStudio` melewati pembukaan IDE. Database/akun yang belum disiapkan mengikuti provisioning di bawah. Konfigurasi IDE `android/.run/app.run.xml` menyediakan target app setelah Gradle Sync berhasil; root repository sebelumnya adalah proyek IDE umum, bukan modul Android yang telah diimpor.

Gunakan PowerShell 7 dari root workspace. PHP, Composer, Gradle dan PlatformIO sudah tersedia di `.tools/` (diabaikan Git). MySQL memakai executable Laragon, dengan data tersendiri di `.runtime/mysql/data`, port **3307**, dan schema `inofarm_local`/`inofarm_test`. Tidak memakai database Laragon utama.

```powershell
./scripts/local-database.ps1
Push-Location backend
& ../.tools/php/php.exe artisan migrate --force
& ../.tools/php/php.exe artisan inofarm:owner owner@example.com --name="Nama owner"
Pop-Location
./scripts/start-backend.ps1
```

Gunakan email Anda sendiri. Provisioning meminta password tersembunyi minimal 12 karakter; tidak ada akun/password produksi bawaan. API `http://127.0.0.1:8000/api/v1/`, health `http://127.0.0.1:8000/up`. Terminal server tetap berjalan. Konfigurasi, fase, jadwal dan pekerja awal kosong, diisi owner melalui APK.

Install APK: `adb install -r output/inofarm-jogja-debug.apk`. Untuk emulator/perangkat adb, di terminal lain:

```powershell
& 'C:/Users/ACER/AppData/Local/Android/Sdk/platform-tools/adb.exe' reverse tcp:8000 tcp:8000
```

APK debug dari Android Studio memakai `http://10.0.2.2:8000/api/v1/` otomatis, tanpa ketergantungan ADB reverse pada emulator. Pada Android17, izinkan jaringan lokal saat login; deklarasi permission hanya pada manifest debug. Launcher memilih default loopback untuk USB dan menyiapkan reverse. Untuk mengganti server, buka Pengaturan koneksi pada login. Build release/profile memakai property `-PinofarmApiUrl=https://alamat-server/api/v1/` atau kosong jika belum ditetapkan; tidak mengarang domain produksi. Debug dapat dioverride dengan `-PinofarmDebugApiUrl=...`. Release tetap mewajibkan HTTPS. Tidak ada deployment.

## Build dan test

```powershell
./scripts/test-backend.ps1
./scripts/build-android.ps1
./scripts/build-firmware.ps1
./scripts/build-firmware.ps1 -Test
./scripts/build-firmware.ps1 -HardwareCompileCheck
```

Android menggunakan SDK 37/JDK Android Studio yang tersedia; bila berbeda, beri `-JavaHome` dan `-SdkPath`. Output asli APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Gradle wrapper disertakan. Script firmware memakai SUBST sementara untuk mengatasi panjang command GCC Windows, lalu melepas mapping tanpa memindahkan file. Jangan menjalankan dua build firmware bersamaan. `HardwareCompileCheck` hanya memeriksa kompilasi adapter dengan pin tetap -1; **jangan flash binary check**.

Integrasi Android ↔ Laravel/MySQL memakai schema **testing terpisah**, bukan data owner. Jalankan regresi backend dahulu, lalu:

```powershell
& .tools/php/php.exe scripts/test-fixture.php
./scripts/start-test-backend.ps1
```

Di terminal lain dengan emulator aktif:

```powershell
& 'C:/Users/ACER/AppData/Local/Android/Sdk/platform-tools/adb.exe' reverse tcp:8001 tcp:8001
./scripts/build-android.ps1 -Tasks @(':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug',':app:connectedDebugAndroidTest','-Pandroid.testInstrumentationRunnerArguments.realBackend=true')
```

RealBackendTest menggunakan akun/device TEST ONLY di `inofarm_test`. Tanpa flag integrasi, test ini dilewati; test UI memakai MockWebServer. Jangan menjalankan PHPUnit selama integrasi HTTP: RefreshDatabase mereset schema testing. Untuk run baru, ulangi `test-backend.ps1` lalu `test-fixture.php`. Fixture bukan konfigurasi produksi/agronomis.

## Firmware

Provision satu perangkat setelah owner siap:

```powershell
Push-Location backend
& ../.tools/php/php.exe artisan inofarm:device --output=../.runtime/device-private.json
Pop-Location
```

Credential ditulis ke file private yang diabaikan Git, tidak dicetak. Isi `firmware/include/secrets.h` dari contoh dengan WiFi, URL API berakhiran /api/v1/, deviceId, token dan CA TLS Anda. Isi `board_config.h` dari contoh **setelah wiring/power/BOM dikonfirmasi**, lalu aktifkan INOFARM_WIRING_ENABLED=1. Pin -1 tidak diganti tebakan. PH4502C memerlukan pengamanan tegangan ADC dan kalibrasi terukur; awalnya quality uncalibrated. DS3231 harus diatur/ditinjau UTC secara fisik; firmware tidak memakai waktu compile/receive sebagai pengganti clock.

Menu OK → fase → enam digit PPM handheld dengan LEFT/RIGHT dan OK → review → OK simpan; BACK membatalkan/mengubah. Enam digit adalah kapasitas editor teknis, bukan rentang agronomis. Tanpa SD, tidak diklaim tersimpan. Record lokal pending sampai ACK server cocok dan durable. Replay mempertahankan waktu sumber. Interval telemetry dan durasi buzzer kosong sampai owner mengisi. Driver membaca minimal setiap dua detik untuk kompatibilitas DHT22; cadence upload tetap milik owner.

## Lingkungan lain dan batas

Install PHP >=8.4.1 (PHP 8.5 sudah diuji; dependency terkunci membutuhkan lebih tinggi dari constraint dasar ^8.3) dengan pdo_mysql/mbstring/openssl/curl/fileinfo, Composer, MySQL 8, Android Studio/SDK dan PlatformIO 6.1.18. Composer install memakai `backend/composer.lock`, Gradle wrapper, dan PlatformIO memakai `platformio.ini`. Siapkan .env/.env.testing MySQL sendiri, schema testing khusus, artisan key:generate lalu migrate. Jangan menyalin credential komputer ini. Script database menolak port yang bukan instance miliknya dan overwrite environment yang sudah ada.

Belum diverifikasi: wiring/kalibrasi/RTC/SD power-loss/LCD/LED/buzzer fisik, jaringan greenhouse, soak test, pentest/reviewer handover, dan penerapan SOP multi-platform. Koreksi/hapus riwayat immutable menunggu Q-10. Tidak ada commit/push/deploy.

## Perbaikan audit T1–T7

APK debug terbaru: `output/inofarm-jogja-audit-debug.apk`; evidence terbaru [VERIFICATION](docs/implementation/VERIFICATION.md), kebijakan [DECISIONS](docs/implementation/DECISIONS.md). Semua staged file awal dipertahankan dan belum ada commit/push/deploy.

`./scripts/test-backend.ps1 -GuardOnly` menguji guard tanpa SQL. Full suite/fixture mewajibkan konfigurasi efektif MySQL `inofarm_test`, termasuk DB_URL/cached/read-write overrides. Gunakan `backend/.env.testing.example` dengan credential milik environment testing; fresh provisioning menyediakan test-only account. Instance lama tidak dimigrasikan otomatis. Jangan clear cached config global demi melewati penolakan guard; betulkan konfigurasi testing secara eksplisit.

Android read cache terenkripsi schema1/server-account-tank/max500/max2MiB, last-known dan refresh saat resume/30 detik selama dashboard aktif; logout/401/known expiry menghapus cache. Update oversize/write gagal mempertahankan snapshot lama. Tidak ada offline writes. Riwayat/filter/export mempertahankan snapshot backend. Firmware HTTP bench memerlukan `INOFARM_ALLOW_HTTP_BENCH=1` dan parsed IPv4 privat; lihat [firmware README](firmware/README.md) untuk queue blocked/ACK dan batas hardware.
