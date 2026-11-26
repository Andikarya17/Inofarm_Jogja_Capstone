# Inofarm — pencatatan operasional dan evaluasi keuangan

Produk aktif: aplikasi Android native Kotlin + Jetpack Compose, Laravel REST API v2 dan MySQL. Pekerja/pengelola memasukkan data manual per greenhouse, musim dan kelompok tanam. Tidak ada koneksi sensor, ESP32, kontrol perangkat atau dosing.

Fitur mencakup catat kegiatan per greenhouse (HSS/HST, pengukuran EC/PPM sebelum dan sesudah inject, nutrisi AB Mix, penyemprotan per varietas), panen per varietas dengan Layak/Cacat, cek catatan pekerja, keuangan per **Musim ke-n** (biaya standar FC/VC yang sama untuk setiap GH, TC, grafik penjualan, unduh Excel), penjualan per baris varietas dengan pembeli Perorangan/Retail/Mitra dan stok panen FIFO, aset/penyusutan, investasi, ekspor serta recovery terkontrol. Lihat [status implementasi](docs/context/IMPLEMENTATION_STATUS.md), [kontrak executable](contracts/v2/README.md) dan [CLAUDE.md](CLAUDE.md) untuk konteks lengkap.

## Setelah `git pull` dari main (untuk anggota tim)

Lakukan langkah ini setiap kali menarik main, supaya backend, database dan APK kamu sama dengan main. Jalankan dari folder root repo di PowerShell.

### 1. Tarik main

```powershell
git checkout main
git pull github main
```

Nama remote di laptop kamu mungkin `origin`; cek dengan `git remote -v`.

### 2. Dependency backend

```powershell
composer install --working-dir=backend
```

Wajib bila `backend/composer.lock` berubah. Aman dijalankan ulang.

### 3. Database: backup dulu, lalu migrate

Main sekarang sampai **migration finance 24** (29 file migration, 60 tabel). Migration 23 menambah biaya standar per musim dan daftar pembeli; migration 24 menambah baris penjualan (`sale_lines`), pembeli retail dan harga perorangan (`consumer_prices`). Data lama tetap ada, penjualan lama otomatis diberi baris.

```powershell
# Lihat migration yang belum diterapkan di database backend/.env kamu
php backend/artisan migrate:status --pending=1

# Backup dulu (simpan di luar repo)
./scripts/backup-mysql.ps1 -OutDir D:/inofarm-backup -EnvFile backend/.env

# Terapkan migration yang tertunda
php backend/artisan migrate
```

Jangan pakai `migrate:fresh`, `migrate:reset` atau `db:seed` pada database yang berisi data, karena semuanya menghapus data.

Bila belum punya database sama sekali (laptop baru), lihat [Setup pertama kali](#setup-pertama-kali) di bawah.

### 4. Build dan pasang APK

```powershell
./scripts/build-android.ps1 -JavaHome $env:JAVA_HOME -SdkPath "$env:LOCALAPPDATA/Android/Sdk"
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Pakai `adb install -r` (pasang ulang). Jangan uninstall atau Clear data, karena draf dan antrean yang belum terkirim tersimpan di HP. Bisa juga lewat Android Studio: buka folder `android/`, Sync Gradle, lalu Run.

### 5. Jalankan backend

```powershell
./scripts/start-backend.ps1
```

Server berjalan di `http://127.0.0.1:8000`. Emulator memakai `http://10.0.2.2:8000/api/v2/` secara bawaan. HP fisik di Wi-Fi yang sama butuh `./scripts/start-backend.ps1 -BindAddress 0.0.0.0` dan alamat IP laptop di layar login.

### 6. Cek cepat (opsional)

```powershell
./scripts/test-backend.ps1
```

Tes backend hanya berjalan pada database `inofarm_test` (lewat `backend/.env.testing`) dan menolak database lain.

### Ringkasan perubahan terbaru di main

| Bagian | Yang berubah |
|---|---|
| Keuangan | Tab Keuangan per Musim ke-n: FC, VC, TC per GH dan total kebun, grafik penjualan Mitra/Retail/Perorangan, daftar penjualan per musim dan unduh Excel. Laba, R/C, B/C, BEP dan TR tidak lagi tampil. |
| Penjualan | Satu penjualan bisa berisi beberapa varietas, masing-masing Layak/Cacat dengan kg, jumlah buah (opsional) dan harga per kg. Stok panen dikurangi server dari panen paling lama. |
| Pembeli | Tiga tipe: Perorangan (harga tetap, atur di Kelola → Harga perorangan), Retail (harga ditawar per transaksi) dan Mitra (grade A/B/C). |
| Kelola | Diatur per objek: Greenhouse, Orang, Akun saya. Mode Terang/Gelap pindah ke Kelola → Akun saya. |
| Database | Migration 23 dan 24 (lihat langkah 3). |

## Setup pertama kali

Prasyarat: PHP sesuai `backend/composer.lock` (PHP 8.5 diuji), Composer, MySQL 8, JDK 25 (atau JBR Android Studio), Android SDK 37 dan emulator/HP Android 10 (API 29) ke atas. Tidak perlu npm.

1. `composer install --working-dir=backend`
2. Database:
   - Cara cepat, bila memakai MySQL Laragon 8.0.30 dan belum ada `backend/.env`: `./scripts/local-database.ps1`. Skrip ini menyalakan MySQL di port 3307, membuat `inofarm_local` + `inofarm_test` beserta user acak, lalu menulis `backend/.env` dan `backend/.env.testing`.
   - Atau manual: salin `backend/.env.example` menjadi `backend/.env`, isi `DB_*`, lalu `php backend/artisan key:generate`.
3. `php backend/artisan migrate`
4. Buat akun pengelola: `php backend/artisan inofarm:finance-user email@contoh.com --name="Pengelola" --role=manager` (password diminta interaktif). Greenhouse, varietas dan akun pekerja lalu dibuat dari aplikasi pengelola.
5. Build/pasang APK dan jalankan backend seperti langkah 4 dan 5 di atas.

File `backend/.env`, `backend/.env.testing` dan `android/local.properties` tidak di-commit; masing-masing anggota membuat sendiri. Android Studio membuat `local.properties` (`sdk.dir=...`) saat pertama membuka folder `android/`.

## Verifikasi lengkap

```powershell
./scripts/test-backend.ps1
./scripts/build-android.ps1 -JavaHome $env:JAVA_HOME
python scripts/check-finance-context.py
```

Jangan menjalankan tes perangkat (HTTP fixture) dan PHPUnit bersamaan pada `inofarm_test`. Build debug bukan persetujuan UAT, pentest atau produksi. Release (`com.inofarm.jogja`) memerlukan signing dan URL HTTPS tersendiri; debug memakai `com.inofarm.jogja.finance`. [Panduan instalasi lengkap](docs/migration/finance-20261029/FIN17_INSTALL_RUN.md).

Source v1/owner/IoT, firmware dan prototipe React sudah dilepas dari produk aktif; tabel/migration historis dan [arsip](docs/archive/pre-finance-20261029/INDEX.md) dipertahankan.
