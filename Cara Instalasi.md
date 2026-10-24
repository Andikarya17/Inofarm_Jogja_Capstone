# Cara Instalasi Inofarm Jogja

Panduan menjalankan source di komputer sendiri melalui Android Studio.
Diperbarui 24 Oktober 2026. Contoh perintah utama memakai Windows PowerShell.

## 1. Kenali isi repository

| Folder | Kegunaan |
| --- | --- |
| `android/` | Proyek aplikasi Android native Kotlin/Jetpack Compose; buka folder ini di Android Studio. |
| `backend/` | Laravel REST API dan migration MySQL; wajib berjalan untuk login dan mengambil data. |
| `firmware/` | Firmware ESP32 implementasi lama; tidak wajib untuk membuka aplikasi atau memakai data demo. |
| `src/` | Prototipe React sebagai referensi UI, bukan proyek Android. |
| `docs/` | Kontrak API, keputusan, hasil pengujian, dan diagram proposal. |
| `scripts/` | Alat bantu build/test/run; sebagian memiliki asumsi khusus komputer pengembang. |

**Kondisi source:** implementasi yang dapat dijalankan masih aplikasi owner/leader
untuk sistem nutrisi dengan sumber data IoT. Rancangan baru aplikasi pencatatan
greenhouse tanpa IoT di `docs/design/mobile-greenhouse-20261021/` belum diterapkan
ke source aplikasi. Clone repository tidak otomatis mendapatkan fitur rancangan baru.

Database, password, akun lokal, APK, SDK, dan dependency terunduh tidak dibagikan.
Tidak ada akun/password login bawaan. Source dapat dibangun tanpa ESP32, tetapi
monitoring nyata membutuhkan backend, perangkat dan konfigurasi yang sesuai.

## 2. Siapkan perangkat lunak

| Komponen | Versi/kebutuhan proyek |
| --- | --- |
| Git | Untuk clone repository. |
| Android Studio | Versi yang mendukung Android Gradle Plugin **9.1.1** dan SDK **37.0**. |
| Android SDK | Platform **Android API 37.0**, Build-Tools **36.0.0**, Platform-Tools; Emulator + system image untuk emulator. |
| Java | **JDK 25** untuk mengikuti `android/gradle/gradle-daemon-jvm.properties`. Target kompilasi Java source tetap 17. |
| Gradle | Wrapper **9.3.1** sudah disertakan, tidak perlu memasang Gradle global. |
| PHP CLI | **8.4.1 minimum untuk dependency dalam `composer.lock` saat ini**; gunakan PHP 8.5 untuk mengikuti lingkungan yang sudah diuji. |
| Ekstensi PHP | `pdo_mysql`, `mbstring`, `openssl`, `curl`, `fileinfo`, `ctype`, `filter`, `hash`, `session`, `tokenizer`, `dom`, `xml`, `xmlwriter`; `zip` memudahkan Composer. |
| Composer | Composer 2 untuk memasang dependency sesuai lockfile. |
| MySQL | MySQL 8; lingkungan pengembang memakai 8.0.30. |
| PowerShell 7 | Opsional, untuk script `.ps1`; langkah manual di bawah tidak memerlukan launcher khusus. |

Gunakan installer resmi [Android Studio](https://developer.android.com/studio),
[PHP](https://www.php.net/downloads), [Composer](https://getcomposer.org/download/),
dan [MySQL](https://dev.mysql.com/downloads/). Laragon juga boleh dipakai untuk
menyediakan PHP/MySQL asalkan versinya sesuai; periksa **PHP CLI**, bukan hanya PHP web server.

Rujukan kompatibilitas: [AGP 9.1.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes).
Versi di source/lockfile menjadi acuan; jangan menerima upgrade otomatis saat setup awal.
Gradle daemon proyek meminta JDK 25, sehingga hanya memasang JDK minimum AGP belum cukup.
Walaupun `composer.json` mengizinkan PHP ^8.3, dependency Symfony yang terkunci
memerlukan PHP >=8.4.1; PHP 8.3 tidak cukup untuk memasang lockfile ini.

Periksa terminal:

```powershell
git --version
php --version
php --ini
php -m
composer --version
java -version
```

Jika perintah tidak dikenali, tambahkan direktori executable ke PATH lalu buka ulang
terminal. Android Studio dapat memakai Java berbeda dari terminal; periksa keduanya.
`JAVA_HOME` harus menunjuk **folder JDK**, bukan file `bin/java.exe`.

## 3. Clone repository

Pilih direktori pendek untuk mengurangi masalah panjang path di Windows:

```powershell
git clone https://github.com/Andikarya17/Inofarm_Jogja_Capstone.git C:\Projects\Inofarm_Jogja_Capstone
cd C:\Projects\Inofarm_Jogja_Capstone
```

Jika repo private, login memakai akun GitHub yang sudah mendapat akses. Jangan
menuliskan token di URL, source, screenshot, atau file yang di-commit.
Untuk macOS/Linux, clone ke direktori kerja sendiri dan sesuaikan pemisah path.

## 4. Siapkan MySQL lokal

Jalankan MySQL dari instalasi sendiri. Contoh ini memakai **port 3306**, sedangkan
template proyek memakai **3307**; sesuaikan `.env` dengan port MySQL yang benar.
Tidak perlu menyalin folder data MySQL milik pengembang.

Melalui MySQL Workbench, phpMyAdmin, atau MySQL CLI sebagai administrator, buat
database dan akun **baru khusus proyek**. Ganti dua placeholder password berikut
dengan password kuat yang berbeda sebelum menjalankan SQL secara lokal:

```sql
CREATE DATABASE inofarm_local CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'inofarm'@'127.0.0.1' IDENTIFIED BY 'GANTI_PASSWORD_DB_LOKAL';
GRANT ALL PRIVILEGES ON inofarm_local.* TO 'inofarm'@'127.0.0.1';

-- Opsional: khusus pengujian otomatis, terpisah dari data aplikasi.
CREATE DATABASE inofarm_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'inofarm_test_runner'@'127.0.0.1' IDENTIFIED BY 'GANTI_PASSWORD_DB_TEST';
GRANT ALL PRIVILEGES ON inofarm_test.* TO 'inofarm_test_runner'@'127.0.0.1';
```

Jika nama database/user sudah ada, periksa isinya dan gunakan konfigurasi existing
yang sesuai; jangan menghapus atau mereset database untuk mengulang panduan.
Akun aplikasi hanya mendapat akses `inofarm_local`, akun testing hanya `inofarm_test`.

## 5. Pasang dan jalankan backend

Dari root repository:

```powershell
cd backend
Copy-Item .env.example .env
composer install
composer check-platform-reqs
```

`Copy-Item` dilakukan pada clone baru saat `.env` belum ada. Jangan menimpa `.env`
yang sudah dipakai. Gunakan `composer install`, agar versi mengikuti `composer.lock`.
Jangan memakai `--ignore-platform-reqs`; perbaiki versi PHP/ekstensi jika gagal.

Edit **`backend/.env` secara lokal**:

```dotenv
APP_NAME="Inofarm Jogja"
APP_ENV=local
APP_KEY=
APP_DEBUG=false
APP_URL=http://127.0.0.1:8000
DB_CONNECTION=mysql
DB_HOST=127.0.0.1
DB_PORT=3306
DB_DATABASE=inofarm_local
DB_USERNAME=inofarm
DB_PASSWORD="GANTI_PASSWORD_DB_LOKAL"
CACHE_STORE=file
SESSION_DRIVER=file
QUEUE_CONNECTION=sync
LOG_CHANNEL=single
```

Isi password sesuai akun MySQL yang baru dibuat. Jika MySQL memakai 3307, ubah
`DB_PORT=3307`. File ini diabaikan Git dan tidak boleh dibagikan.

Masih dari folder `backend`, inisialisasi aplikasi baru:

```powershell
php artisan key:generate
php artisan migrate
php artisan inofarm:owner teman@example.com --name="Nama Teman"
php artisan serve --host=127.0.0.1 --port=8000
```

Ganti email dan nama dengan milik sendiri. Perintah owner meminta password tersembunyi
minimal **12 karakter**. Password aplikasi berbeda dari password MySQL.
`key:generate` cukup saat setup pertama; jangan mengganti key aplikasi existing sembarangan.
Migration menambah struktur ke database yang telah dipilih; jangan menjalankan
`migrate:fresh`, reset, atau test terhadap database owner.

Biarkan terminal server tetap hidup. Buka
[health backend lokal](http://127.0.0.1:8000/up): seharusnya HTTP 200.
Base URL API adalah `http://127.0.0.1:8000/api/v1/`.
Health 200 belum membuktikan login/koneksi MySQL; verifikasi login pada langkah 7.
Backend REST ini tidak memerlukan `npm install` atau build React untuk dipakai Android.

## 6. Buka proyek di Android Studio

1. Pilih **Open**, lalu pilih **folder `android` di dalam hasil clone**.
2. Di **SDK Manager**, pasang platform API 37.0, Build-Tools 36.0.0,
   Android SDK Platform-Tools dan Android Emulator jika diperlukan.
3. Di **Settings > Build, Execution, Deployment > Build Tools > Gradle**, gunakan
   Gradle wrapper proyek dan Java yang memenuhi daemon **JDK 25**. Jika Studio
   menawarkan pengunduhan JDK 25, selesaikan; pastikan koneksi unduh tersedia.
4. Tunggu **Gradle Sync** selesai. Jangan menjalankan file `.kt` sebagai program Java/Kotlin biasa.
5. Di **Device Manager**, buat emulator API 37.0. Aplikasi memiliki minimum API 26;
   emulator API 37.0 merupakan baseline yang dipakai proyek.
6. Pilih build variant **debug**, konfigurasi **app** atau **Inofarm MainActivity**,
   pilih emulator, lalu klik **Run** / **Shift+F10**.

Android Studio biasanya membuat `android/local.properties` otomatis. Jika SDK tidak
ditemukan, buat file lokal tersebut dengan lokasi SDK sendiri, misalnya:

```properties
sdk.dir=C:/Users/NAMA_USER/AppData/Local/Android/Sdk
```

File ini diabaikan Git. Jika emulator hidup tetapi tidak tampak, buka
**View > Tool Windows > Running Devices**. Jika modul app tidak ada, pastikan
yang dibuka adalah folder `android`, lalu lakukan Gradle Sync.

## 7. Hubungkan Android ke backend dan login

Di halaman login buka **Pengaturan koneksi**, jika perlu, lalu gunakan:

| Perangkat | Base URL API |
| --- | --- |
| Android Emulator standar di komputer yang menjalankan backend | `http://10.0.2.2:8000/api/v1/` (default debug) |
| HP Android melalui USB dengan ADB reverse | `http://127.0.0.1:8000/api/v1/` |

Untuk HP fisik, aktifkan Developer options/USB debugging dan setujui akses USB.
Jalankan dari terminal dengan `adb` di PATH (atau path lengkap ke SDK Platform-Tools):

```powershell
adb devices
adb reverse tcp:8000 tcp:8000
```

Jika beberapa perangkat terhubung, gunakan `adb -s SERIAL reverse tcp:8000 tcp:8000`.
Reverse perlu diulang jika koneksi ADB terputus. `localhost` pada emulator menunjuk
emulator sendiri, sehingga gunakan `10.0.2.2` untuk mengakses komputer host.
Pada Android 17, izinkan permintaan jaringan lokal untuk koneksi backend debug.

Login menggunakan akun yang dibuat dengan `inofarm:owner`. Setelah login pertama,
konfigurasi/fase/pekerja/jadwal dan riwayat dapat kosong; itu kondisi yang benar.
Isi konfigurasi hanya dengan nilai yang disetujui pemilik greenhouse. Tidak ada
threshold agronomis otomatis. Tanpa perangkat, monitoring dapat berstatus belum
ada data/disconnected; jangan menganggapnya sebagai pembacaan sensor nyata.

HTTP hanya untuk development debug lokal. Release memerlukan backend HTTPS sendiri
dan property `-PinofarmApiUrl=https://SERVER_SENDIRI/api/v1/`; belum ada server produksi
bawaan atau deployment yang disertakan.

## 8. Opsional: tampilkan riwayat demo tanpa ESP32

Generator membuat tandon dan akun demo **terpisah**, bukan mengisi akun owner baru.
Guard generator saat ini hanya menerima MySQL lokal **port 3307**. Jika setup
langkah 4 memakai 3306, lewati demo atau siapkan instance MySQL lokal terpisah pada
3307 beserta database/user proyek, lalu sesuaikan `.env`. Mengubah `.env` saja tidak
mengubah port server MySQL. Jangan mengubah instance yang dipakai proyek lain.
Pada root repo buat folder `.runtime`, lalu jalankan command lokal:

```powershell
New-Item -ItemType Directory -Force .runtime
cd backend
php artisan inofarm:demo-history
```

Command menghasilkan 30 record dan 30 telemetry sintetis untuk 10 hari penuh
sebelum hari ini WIB. Untuk contoh tanggal tetap gunakan
`php artisan inofarm:demo-history --as-of=2026-10-20`.
Login memakai email/password dalam **file privat lokal**
`.runtime/demo-history-credentials.json`, lalu buka Riwayat dan pilih semua tanggal.
File tidak di-commit. Jangan menyalin password ke dokumentasi.

Command menolak demo/file credential existing agar tidak membuat duplikat;
jangan menghapus data owner untuk mengulangnya. Angka, jadwal, dan sensor demo
adalah fixture, bukan rekomendasi agronomis atau bukti hardware.
Detail: [DEMO_DATA](docs/implementation/DEMO_DATA.md).

## 9. Opsional: verifikasi build dan test

Build/unit/lint Android dari terminal PowerShell, mulai root repository:

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Pada macOS/Linux gunakan `bash ./gradlew` dengan argumen task yang sama.
APK dihasilkan di `android/app/build/outputs/apk/debug/app-debug.apk`.
Tes UI `:app:connectedDebugAndroidTest` membutuhkan emulator/perangkat aktif.
Tes integrasi backend nyata memiliki setup terpisah; lihat [README](README.md).

Untuk backend, mulai dari folder `backend`, salin `.env.testing.example` menjadi
`.env.testing` hanya jika belum ada. Isi DB host/port yang benar,
`DB_DATABASE=inofarm_test`, user `inofarm_test_runner` dan password testing berbeda.
Full suite mencakup generator demo yang mewajibkan MySQL lokal **port 3307**;
siapkan instance testing pada port tersebut dan isi `DB_PORT=3307` di `.env.testing`.
Database aplikasi normal boleh tetap pada 3306 sesuai langkah 4.
Biarkan `DB_URL=` kosong dan jangan memakai cache konfigurasi environment lain.

```powershell
php artisan key:generate --env=testing
composer test
```

Guard menolak database selain MySQL `inofarm_test`. Suite boleh membangun ulang
schema testing; data testing tidak untuk data pribadi/owner. Jangan menjalankan
suite bersamaan dengan integrasi HTTP yang memakai schema testing yang sama.

## 10. Pemecahan masalah

| Masalah | Pemeriksaan |
| --- | --- |
| Gradle Sync gagal / SDK tidak ditemukan | Folder yang dibuka harus `android`; pasang API 37.0 dan periksa `local.properties`. |
| Java/toolchain tidak cocok | Daemon criteria meminta JDK 25; periksa Gradle JDK Studio dan hasil `java -version`. |
| Dependency gagal diunduh | Periksa internet/proxy dan nonaktifkan mode Gradle Offline saat setup pertama. |
| `could not find driver` | Aktifkan `pdo_mysql` pada PHP CLI sesuai `php --ini`, lalu buka ulang server. |
| `SQLSTATE` connection refused / access denied | Jalankan MySQL; cocokkan host, port, nama database, user dan password `.env`. |
| Login connection refused | Backend harus hidup; emulator memakai `10.0.2.2`, USB memakai ADB reverse. |
| Login gagal meski backend hidup | Gunakan akun hasil `inofarm:owner`, bukan akun database; lihat `backend/storage/logs/laravel.log` secara privat. |
| Port 8000 dipakai | Pilih port lain pada `artisan serve` dan ubah URL Android serta ADB reverse sesuai port itu. |
| Riwayat kosong | Akun owner baru memang kosong; untuk demo login memakai akun demo yang terpisah. |
| Launcher sekali klik gagal pada komputer lain | Pakai langkah manual panduan ini. Launcher mengasumsikan Laragon/path/AVD dan `.tools/php` komputer pengembang; folder itu tidak dibagikan. |

`Run-Inofarm.cmd` bukan installer dependency, pembuat akun, atau pengganti migration.
Jika backend sendiri sudah disiapkan, script launcher dapat memakai `-SkipBackend`
serta `-SdkPath`, `-JavaHome`, `-StudioPath`, `-Avd` sesuai komputer sendiri; gunakan
PowerShell 7. Untuk instalasi pertama, ikuti langkah manual di atas.

## 11. File privat dan batas verifikasi

`.gitignore` mengecualikan `.env` nyata, `.runtime`, `.tools`, `local.properties`,
firmware `secrets.h`/`board_config.h`, credential JSON, signing key privat,
database lokal, dependency, cache/log dan hasil build. Template `.env.example`,
`.env.testing.example`, `secrets.example.h`, `board_config.example.h`, lockfile,
wrapper dan source tetap disertakan. Sertifikat `mock-server.p12` di **androidTest**
adalah fixture TLS localhost yang sengaja publik; jangan dipakai untuk produksi.
Git tidak menyimpan direktori kosong; direktori runtime dibuat saat diperlukan.

Firmware tidak wajib untuk setup Android. Jika akan dipasang ke hardware, ikuti
[firmware README](firmware/README.md); jangan menebak pin, wiring, atau kalibrasi.
Koneksi hardware greenhouse, instalasi di komputer teman, pentest dan handover
belum dibuktikan hanya dengan build lokal. Hasil pengujian yang tersedia tercatat
di [VERIFICATION](docs/implementation/VERIFICATION.md).
