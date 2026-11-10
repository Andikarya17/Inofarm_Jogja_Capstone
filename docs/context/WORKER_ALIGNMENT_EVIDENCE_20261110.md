# Penyelarasan pekerja, 10 November 2026

Baseline `51323f8`, emulator `emulator-5556` API 30 dan JDK 25. Berkas lokal pengujian baru ada di `outputs/worker-alignment-20261110/`. Screenshot sebelum perubahan ada di `outputs/worker-vision-audit-20261110/screenshots/` dengan fixture sintetis dari APK pada baseline tersebut.

## PK0 dan PK2

| Kebutuhan | Source dan bukti |
| --- | --- |
| Pemilihan greenhouse dan umur WIB | `FinanceRoot`, `HomeContextCard`, cache `greenhouses/seasons/planting_groups`, `PlantAge`. Cache master membawa `running_group_id`; pekerja tidak perlu mengakses `manager-home`. |
| EC/PPM per revisi | `LocalFinanceStore.latestMeasurement`, `ManualMeasurement`, `MeasurementDashboard`, `ChangeFeedController`. Reproduksi Android dengan SQLite fixture gagal pada baseline: keputusan invalid tidak mengganti pasangan terbaru. Sesudah perbaikan, tiga tes store lulus, termasuk fallback, semua invalid, koreksi, tengah malam WIB, restart, akun/cakupan dan epoch. |
| Aktivitas hari ini | `ActivityDraft`, `ContextViewModel`, riwayat dan antrean scoped. Jadwal boleh memiliki lebih dari satu realisasi. |
| Review dan progres panen | `HarvestService` dan `HarvestProgress`, dengan ledger dan snapshot versi. Kebijakan eligibility jual dan laporan mengikuti aturan penjualan existing. Asal historis yang tidak diketahui dipertahankan. |
| Regresi pengelola | Tes baseline gabungan kembali timeout mencari `Transaksi`. Source pengelola memakai judul `Alat keuangan` sejak `313ad5e`. Locator yang diperbarui memeriksa judul dan kontrol penjualan yang tampil serta aktif. |

PK2 menambah opt-in proyeksi status review pengukuran pada pull. Pencatat menerima alasan sendiri. Akun pekerja lain dalam greenhouse yang sama hanya menerima identitas revisi dan keputusan. Cursor lama diulang secara scoped agar keputusan terdahulu bisa diterapkan. DB guard 4 tes/12 assertion dan subset backend MySQL `inofarm_test` 13 tes/1242 assertion lulus. Build APK, APK instrumentasi dan JVM lulus. Bukti SQLite/UI sintetis tidak digunakan sebagai bukti backend perangkat nyata.

## PK1

Beranda membaca pilihan greenhouse dari master scoped pada perangkat. Satu pilihan membuka periode yang ditetapkan atau satu kelompok aktif di musim terbuka. Musim dan kelompok tetap dapat dipilih ketika ambigu. Pergantian pilihan hanya mengubah konteks Beranda dan draf baru. Header memakai crop resource Android `background_dashboard.jpg` yang sama dengan aset pengguna tercatat, SHA-256 `53a8248de6dfc68d9553460ff1cfe43fcec4490ee9e697f29405039df1ed6f2a`. Pada lebar sempit dan skala font besar, bidang teks memakai lebar penuh.

Build debug dan instrumentasi lulus. Tes API 30 `workerSelectsGreenhouseDirectlyAndOpensReadyRecordingContext` lulus: satu pilihan GH membuka konteks dan form pengukuran. Run gabungan awal gagal pada ekspektasi pemilih bertingkat yang masih mengharapkan kelompok kosong setelah memilih GH tunggal. Assertion diganti dengan verifikasi nama musim dan kelompok terpilih. Hasil regresi pengelola dicatat terpisah.
