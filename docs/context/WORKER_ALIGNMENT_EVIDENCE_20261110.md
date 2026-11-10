# Penyelarasan pekerja, 10–11 November 2026

Baseline `51323f8`, emulator `emulator-5556` API 30 dan JDK 25. Berkas lokal pengujian baru ada di `outputs/worker-alignment-20261110/`. Screenshot sebelum perubahan ada di `outputs/worker-vision-audit-20261110/screenshots/` dengan fixture sintetis dari APK pada baseline tersebut.

## PK0 dan PK2

| Kebutuhan | Source dan bukti |
| --- | --- |
| Pemilihan greenhouse dan umur WIB | `FinanceRoot`, `HomeContextCard`, cache `greenhouses/seasons/planting_groups`, `PlantAge`. Cache master membawa `running_group_id`; pekerja tidak perlu mengakses `manager-home`. |
| EC/PPM per revisi | `LocalFinanceStore.latestMeasurement`, `ManualMeasurement`, `MeasurementDashboard`, `ChangeFeedController`. Reproduksi Android dengan SQLite fixture gagal pada baseline: keputusan invalid tidak mengganti pasangan terbaru. Sesudah perbaikan, tiga tes store lulus, termasuk fallback, semua invalid, koreksi, tengah malam WIB, restart, akun/cakupan dan epoch. |
| Aktivitas hari ini | `ActivityDraft`, `ContextViewModel`, riwayat dan antrean scoped. Jadwal boleh memiliki lebih dari satu realisasi. |
| Review dan progres panen | `HarvestService` dan `HarvestProgress`, dengan ledger dan snapshot versi. Kebijakan eligibility jual dan laporan mengikuti aturan penjualan existing. Asal historis yang tidak diketahui dipertahankan. |
| Regresi pengelola | Tes baseline gabungan kembali timeout mencari `Transaksi`. Source pengelola memakai judul `Alat keuangan` sejak `313ad5e`. Locator yang diperbarui memeriksa bagian Penjualan dan penerimaan serta kontrol penjualan yang tampil dan aktif. |

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

Suite backend guarded lulus 146 tes/3824 assertion, termasuk agregat multigrade, target kosong/terlampaui, sumber cursor, review, koreksi, pembatalan, musim tertutup dan pencabutan akses. OpenAPI dan checker kontrak lulus. Tes perangkat awal PK4 lulus alur review, tetapi rekonsiliasi SQL menemukan fixture memilih indeks grade yang berubah setelah server mengurutkan ID. Tes sekarang memilih grade berdasarkan ID. Run PK5 pertama berhenti pada login sebelum membuat panen. Run ulang setelah health check API dan pemasangan ulang reverse ADB lulus dalam 79,433 detik. Android membuka sumber revisi 2 dari progres. Rekonsiliasi mengonfirmasi 9,250000 kg layak dan 3,250000 kg cacat, keputusan invalid lalu valid, serta audit, receipt, feed dan bobot terjual yang sesuai.

## PK6

Operate Android native, botani forest–sage–krem. ENERGY/RHYTHM/MOTION 2/2/2 pada menu dan 1/2/2 pada form. Forest mengikat konteks kerja, crop botani menjaga kesinambungan dengan aset lama, dan bidang sage menunjukkan target yang sedang dibandingkan. Agenda memakai baris yang bisa ditelusuri ke catatan. Status pengiriman dan keputusan manusia tetap terpisah. EC/PPM pekerja tampil dengan koma desimal. Format wire tetap memakai titik.

| Interaksi | Implementasi | Bukti emulator |
| --- | --- | --- |
| Pilih greenhouse | Panel konteks baru masuk 230 ms | `worker-motion.mp4`, potret terang API 30 |
| Pindah tab | Indikator tonal dan isi baru 180 ms, saveable state per tab | `worker-motion.mp4` |
| Sheet dan picker | Transisi Material atau dialog waktu Android | `worker-motion.mp4`, screenshot sheet, tanggal dan jam |
| Buka pengamatan dan sumber | Tinggi konten beralih 180–200 ms | `worker-motion.mp4`, screenshot pengamatan |
| Status draf dan pengiriman | Warna dan live region mengikuti penyimpanan aktual | Tes draf, review dan pengiriman, termasuk offline/retry |
| Progres diperbarui | Batang beralih 240 ms, angka mengikuti snapshot | `worker-progress-motion.mp4`, sampel panjang batang 608, 607, 289, 255, 186 px |
| Animasi sistem nonaktif | Tiga skala Android 0 | `worker-motion-reduced.mp4` dan tes 320 dp/font 200% |

Berkas tangkapan sebelum ada di `outputs/worker-vision-audit-20261110/screenshots/worker-home.png`. Berkas sesudah ada di `outputs/worker-alignment-20261110/`: `worker-picker-after.png`, `worker-home-after.png`, `worker-form-after.png`, `worker-progress-after.png`, dan `worker-progress-offline.png`. Data menggunakan fixture sintetis. Klip memakai screenrecord emulator 720 × 1560, dengan frame rate bervariasi. Tidak ada klaim benchmark performa.

## PK7

- Source checkpoint: PK2 `fddba98`, PK1 `cea64b2`, PK3 `baf4da1`, PK4 `ce70fbd`, PK5 `ece0243`, PK6 `d6245ac`. PK7 bertumpuk di `codex/worker-alignment-acceptance`.
- Guard MySQL `inofarm_test` lulus 4 tes/12 assertion sebelum suite. Uji backend 146/3824 dan parity 156 operasi OpenAPI terhadap 157 kombinasi runtime lulus. Uji dua proses PHP dengan koneksi MySQL berbeda menerima satu keputusan dan menolak satu sebagai basi. Penjualan sintetis tetap dapat dicatat saat review pending dan sesudah invalid. Audit, receipt, feed, dan nilai transaksi awal direkonsiliasi.
- Android 75 tes JVM, build debug dan instrumentasi, serta lint 0 error/16 warning/1 hint. Lint awal mengalami crash analyzer pada `WorkerDayAgenda.kt`. Pemisahan pembacaan cache dari Composable membuat analisis ulang berhasil tanpa mematikan aturan lint.
- Run akhir API 30 lulus 38 tes instrumentasi SQLite, koordinator sinkronisasi dan Compose dengan HTTP sintetis. Agenda menunjukkan dua realisasi dan satu catatan dilewati, lalu membuka form realisasi. Regresi gabungan pekerja/pengelola, EC/PPM offline/reconnect, dan panen multigrade lulus. Uji persiapan dan verifikasi draf lintas proses masing-masing lulus setelah force-stop. `pk7-android-final.txt`, `pk7-process-prepare.txt`, dan `pk7-process-verify.txt`.
- API 30 terang pada font 100%, gelap pada 130%, dan gelap pada 320 dp/font 200% dengan animasi nonaktif lulus cakupan picker, IME, dan navigasi yang diuji. Run gelap pertama mengalami exception `SnapshotStateObserver` di harness Compose. Run ulang `pk7-dark130.txt` lulus. Ukuran, tema, skala font dan animasi emulator dipulihkan.
- Android API 30 terhadap Laravel/MySQL uji lulus alur dua akun serta sumber progres. Setelah force-stop paket uji dan melepas reverse koneksi, progres cache terbuka dengan label data tersimpan. Tes SQLite terpisah memeriksa cache setelah reopen, pencabutan penugasan dan pergantian epoch. Fixture backend dibersihkan berdasarkan UUID setelah rekonsiliasi.
- API 37 dalam lanskap lulus tes picker, IME, dan pemulihan draf. TalkBack nyata dan touch exploration aktif di API 37. Fokus judul terlihat. Pengaturan layanan dan izin notifikasi dipulihkan. Cakupan TalkBack adalah sampel fokus, dengan traversal menyeluruh dan audio masih terbuka.
- Gate antislop dan Impeccable native memakai render emulator, alur klik, dan semantik Compose. Pasangan warna teks utama, sekunder, perhatian dan error yang diukur lolos 4,5:1 dengan rentang 6,04–14,72:1. Aset tetap resource existing. Bukti tidak mencakup semua kombinasi perangkat atau performa fisik.

Untuk mengulang alur HTTP, buat fixture `scripts/finance-device-fixture.php create worker-alignment` dengan base URL loopback uji, jalankan API port 8001, pasang reverse ADB dan panggil `FinanceBackendDeviceTest#workerHarvestReviewRoundTripThroughGuardedBackend` dengan `devicePhase=worker-harvest-review`, `checkWorkerProgress=1`, dan manifest Base64 pada `managerHomeFixture`. Jalankan mode `verify-worker-harvest`, `race-worker-review`, dan `cleanup` dengan nama fixture `worker-alignment`. Pisahkan sesi HTTP dari suite yang mereset DB uji.

Rollout migration 20 ke database aplikasi, perangkat fisik, UAT, pentest dan penerimaan produksi tetap di luar hasil engineering ini. Kebijakan review panen tidak mengubah eligibility penjualan atau definisi laporan.
