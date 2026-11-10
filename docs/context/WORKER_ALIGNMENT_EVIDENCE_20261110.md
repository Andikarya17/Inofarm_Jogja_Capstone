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

Build debug dan instrumentasi lulus. Tes API 30 `workerSelectsGreenhouseDirectlyAndOpensReadyRecordingContext` lulus: satu pilihan GH membuka konteks dan form pengukuran. Run gabungan awal gagal pada ekspektasi pemilih bertingkat yang masih mengharapkan kelompok kosong setelah memilih GH tunggal. Assertion diganti dengan verifikasi nama musim dan kelompok terpilih. Run ulang gabungan lulus, termasuk kontrol penjualan aktif di lembar Alat keuangan dan simpan/pulihkan draf.

## PK3

`WorkerEventTime` memakai picker Android dengan isian manual yang dapat dibuka untuk detik. Preview HSS/HST dihitung dari tanggal kejadian WIB atau menampilkan acuan historis pada koreksi. `WorkerDayAgenda` mengambil aktivitas/panen dari cache scoped, memakai hari WIB, dan memisahkan realisasi, jadwal dilewati, catatan saya, keputusan perlu diperbaiki, dan kiriman tertahan. Detail pekerja tidak menampilkan saldo bobot penjualan.

Build debug, build instrumentasi dan JVM lulus. API 30 dengan MockWebServer lulus 3 tes meliputi pemilihan GH, draf pengukuran/Back/recreation/antrean, dan panen multigrade offline/recreation/reconnect/audit pengelola. Tangkapan baru `worker-alignment-picker.png` dan `worker-alignment-selected.png` berasal dari APK yang sama dan fixture sintetis. Interaksi picker waktu, IME dan agenda terisi masih memerlukan pengujian khusus.

## PK4

Migration finance 20 menambah provenance panen saat create dan satu review append-only per revisi. Inbox pengelola memisahkan kegiatan dan panen, termasuk filter catatan lama. Pekerja membaca keputusan untuk panen sendiri dan koreksi menghasilkan revisi pending. Review diikat ke versi sumber dan outbox keputusan. Pengelola dapat meninjau catatan aktif dalam cakupan meski musim sudah ditutup karena tindakan ini hanya menambah keputusan. Pencatat yang kemudian menjadi pengelola tetap memerlukan pengelola lain untuk meninjau panennya.

Guarded MySQL `inofarm_test` lulus 15 tes/1233 assertion untuk panen, verifikasi, penjualan, dan parity OpenAPI. Tes memeriksa penolakan role/scope, replay, review basi, pergantian revisi, audit/receipt/feed, dan kesetaraan saldo sebelum/sesudah. Build debug, instrumentasi dan JVM lulus. Tes Android dua akun terhadap HTTP Laravel/MySQL disimpan dalam `FinanceBackendDeviceTest.workerHarvestReviewRoundTripThroughGuardedBackend`. Bukti hasil perangkat dan rekonsiliasi akan dicatat pada checkpoint integrasi.

## PK5

Beranda dan Panen membuka progres pekerja yang terpisah dari kontrol finalisasi. API memberi target serta total menurut kualitas dan status review, tanpa detail transaksi. Hasil tercatat menghitung versi aktif sekali per catatan. Capaian menggunakan buah atau kg layak. Daftar sumber memuat ID dan versi aktif dengan filter status, dan detail Android memeriksa kecocokan versi sebelum dibuka. Cache progres terakhir memakai penyimpanan terenkripsi dan dibersihkan saat cakupan atau epoch berubah.

Suite backend guarded lulus 146 tes/3824 assertion, termasuk agregat multigrade, target kosong/terlampaui, sumber cursor, review, koreksi, pembatalan, musim tertutup dan pencabutan akses. OpenAPI dan checker kontrak lulus. Tes perangkat awal PK4 lulus alur review, tetapi rekonsiliasi SQL menemukan fixture memilih indeks grade yang berubah setelah server mengurutkan ID. Tes sekarang memilih grade berdasarkan ID. Uji ulang yang mencakup progres berjalan pada fixture baru.
