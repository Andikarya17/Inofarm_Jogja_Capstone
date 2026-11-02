> Catatan historis owner/IoT. Kebutuhan di [context pack](../context/README.md), subset executable dan evidence terbaru di [IMPLEMENTATION_STATUS](../context/IMPLEMENTATION_STATUS.md). Bukti lama di bawah tidak membuktikan target v2.

# Melihat database lokal Inofarm

Database aplikasi adalah `inofarm_local` pada MySQL `127.0.0.1:3307`.
Instance Laragon utama pada port `3306` terpisah. Data tidak perlu dipindah atau diimpor ulang.
`inofarm_test` khusus pengujian otomatis, bukan data aplikasi owner.

## Menyalakan database

Jalankan dari root proyek menggunakan PowerShell 7:

```powershell
./scripts/local-database.ps1
```

`Run-Inofarm.cmd` juga menyiapkan database ketika menjalankan aplikasi.
Jangan menjalankan migration, seeder atau test untuk sekadar melihat isi database.

## phpMyAdmin pada laptop ini

1. Buka <http://localhost/phpmyadmin/index.php?server=2>.
2. Pilih server **Inofarm - MySQL 3307** jika diminta.
3. Login dengan username `inofarm` dan password dari nilai `DB_PASSWORD` di `backend/.env`.
4. Buka `inofarm_local`. Klik tabel lalu **Browse/Jelajahi** untuk melihat baris.

Konfigurasi lokal berada di `C:/laragon/etc/apps/phpMyAdmin/config.inc.php`.
Koneksi pertama port 3306 tetap tersedia. Koneksi kedua memakai autentikasi cookie;
password tidak ditambahkan ke konfigurasi phpMyAdmin atau dokumentasi ini.
Cadangan sebelum perubahan berada di `.runtime/phpmyadmin-config-before-inofarm.php`.
Konfigurasi dan cadangan lokal tersebut tidak dibundel ke Git.

## MySQL Workbench

Buat koneksi lewat tombol **+** di sebelah **MySQL Connections**:

| Field | Nilai |
| --- | --- |
| Connection Name | Inofarm Lokal |
| Connection Method | Standard (TCP/IP) |
| Hostname | 127.0.0.1 |
| Port | 3307 |
| Username | inofarm |
| Password | Nilai DB_PASSWORD dari backend/.env |
| Default Schema | inofarm_local |

Pilih **Test Connection**, simpan, lalu buka koneksinya.
Di panel **Schemas**, buka `inofarm_local` > **Tables**.
Klik kanan tabel > **Select Rows - Limit 1000** untuk melihat data.
Jangan membagikan password atau isi `.env`.

Jika koneksi ditolak, pastikan script database sudah dijalankan. Port database adalah
3307; URL phpMyAdmin tetap port HTTP biasa, bukan `localhost:3307`.

## Verifikasi lokal 20 Oktober 2026

MySQL workspace dinyalakan melalui script existing. Koneksi SQL ke port 3307,
keberadaan database, dan daftar tabel diperiksa tanpa mengubah data.
Konfigurasi phpMyAdmin diperiksa menggunakan PHP syntax check.
Koneksi Workbench di atas merupakan panduan konfigurasi, bukan klaim telah diuji melalui UI.
