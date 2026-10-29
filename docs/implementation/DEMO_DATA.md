> Catatan historis owner/IoT. Kebutuhan di [context pack](../context/README.md), subset executable dan evidence terbaru di [IMPLEMENTATION_STATUS](../context/IMPLEMENTATION_STATUS.md). Bukti lama di bawah tidak membuktikan target v2.

# Data demo lokal 10–19 Oktober 2026

Permintaan pengguna 20 Oktober 2026: riwayat dummy tanpa IoT, untuk melihat struktur
database dan aplikasi menggunakan akun demo terpisah. Dibuat 30 record (tiga per hari)
dan 30 telemetry dalam `inofarm_local` pada port 3307, tandon demo ID **2**.
Tandon/owner asli tidak diubah. Fingerprint semua 24 row aplikasi yang sudah ada
sebelum provisioning tetap sama sesudahnya.

## Melihat lewat aplikasi

1. Jalankan `Run-Inofarm.cmd` bila backend/aplikasi belum aktif.
2. Logout dari akun owner saat ini, lalu login dengan email `demo.inofarm@example.invalid`.
3. Password acak ada di file privat `.runtime/demo-history-credentials.json`.
   Itu password akun aplikasi demo, berbeda dari password MySQL/phpMyAdmin.
4. Buka Riwayat. Gunakan semua tanggal atau filter 10–19 Oktober 2026.

Tidak perlu rebuild APK. Dua catatan jam invalid tampil pada semua tanggal, tetapi
tidak masuk filter tanggal karena `source_at` null; waktu mentah sintetis tetap di body.
Perangkat demo nonaktif/tanpa last contact; status disconnected/never seen bukan error
pembuatan data dan tidak diubah menjadi seolah hardware online.

## Melihat lewat phpMyAdmin / Workbench

Buka <http://localhost/phpmyadmin/index.php?server=2>, login dengan akun MySQL seperti
[DATABASE_ACCESS](DATABASE_ACCESS.md), lalu pilih `inofarm_local`.
Akun aplikasi demo tidak dipakai untuk login phpMyAdmin.

| Tabel | Isi demo yang dapat ditelusuri |
| --- | --- |
| tanks | DEMO DUMMY - BUKAN DATA GREENHOUSE, ID 2 |
| users | Owner DEMO DUMMY; password hashed, relasi tank_id |
| devices | SIMULATOR DUMMY - TANPA ESP32 FISIK, enabled=false |
| workers / worker_versions | DEMO Budi dan DEMO Sari; nama/status versioned |
| shift_versions | Budi 06:00–14:00, Sari 14:00–22:00 WIB, khusus demo |
| config_versions / phase_versions | Konfigurasi angka/jadwal contoh dan fase historis demo |
| events | 30 kind=record dan 30 kind=telemetry, seluruhnya tank_id=2 |

Pada `events`, input ada di `body`, PPM di `manual_ppm`, status/reasons lengkap di
`validation_result`, dan pekerja terjadwal/snapshot shift di `assignment`.
Salin isi `scripts/inspect-demo-history.sql` ke tab SQL untuk tampilan kolom yang lebih
mudah dibaca. Query hanya membaca data dan dibatasi 100 row.

## Status dan provenance

| Status database | Jumlah record | Contoh |
| --- | --- | --- |
| valid | 3 | Tepat waktu, fase/PPM/sensor sesuai konfigurasi demo |
| issues | 17 | Terlambat, terlalu awal, PPM rendah/tinggi, sensor anomali, beda fase, gabungan masalah |
| incomplete | 10 | Sensor gagal/hilang/belum kalibrasi, snapshot usang, jam invalid |

Ada 14 skenario; tambahan tanpa shift memakai assignment `unscheduled`, dan jam invalid
memakai `time_unknown`. Terlambat/normal/anomali merupakan hasil pemeriksaan dalam
`validation_result.checks`, bukan enum baru pada kolom status. Semua alasan tetap disimpan.
Nama pekerja adalah assignment jadwal, bukan identitas penekan tombol yang terverifikasi.

Seluruh angka, jam dan timestamp sumber merupakan fixture sintetis, **bukan rekomendasi
agronomis atau bukti sensor/RTC fisik**. Waktu penerimaan/evaluasi adalah waktu nyata
pembuatan demo. Konfigurasi demo dibuat efektif sebelum tanggal dummy hanya pada tandon
baru agar resolver historis bekerja; konfigurasi owner asli tidak dibackdate.

## Generator dan evidence

Command eksplisit dari folder backend: `../.tools/php/php.exe artisan inofarm:demo-history`.
Default tanggal acuan adalah hari ini WIB, dengan 10 hari penuh sebelumnya.
`--as-of=2026-10-20` menghasilkan rentang di atas. Command menolak demo/file credential
existing dan tidak reset/menambah duplikat. Bukan seeder otomatis atau endpoint API.
Command hanya menerima environment local; builder juga menerima testing/inofarm_test
untuk regression tests. Driver/host/port/schema/override diperiksa sebelum mutation.

Generator memakai Input validator, IngestController, NutrientValidator B-1 dan ShiftResolver
existing dalam transaksi, tanpa menulis status palsu langsung. Device token acak tidak
disimpan/disampaikan dan perangkat demo disabled; internal provisioning bukan klaim
telah menerima HTTP dari ESP32.

Verifikasi 20 Oktober: full backend suite pada inofarm_test PASS20/404; isolated test guard
PASS4/12; Pint PASS. HTTP backend lokal: login demo, history30, telemetry30, filter
valid3/issues17/incomplete10, filter tanggal28 dan logout PASS. Sesi verifikasi dicabut.
UI APK belum diuji khusus pada sesi ini; API yang digunakan APK sudah diperiksa.
Tidak ada migration, reset owner, dependency tambahan, deployment atau pengujian fisik.
