# Penyelarasan pengelola, 10 November 2026

## PG0 · Baseline dan keputusan

HEAD awal `52eafe3` adalah turunan `8d74cba`. Lima perubahan sesudah audit hanya menyentuh context. Setelah fetch, `github/main` pada `bbd712a` sudah mencakup runtime `8d74cba`. Perbandingan tree menunjukkan perbedaan hanya pada lima dokumen tersebut. Branch PG1 berangkat dari `52eafe3` agar kontrak pekerjaan dan baseline audit ikut tersedia.

Kedua sheet Excel dibaca langsung dari OOXML (SHA-256 `39091a131b63439f7223615e8872b34449c4c4f6ab8dfe411f78c8d7baf65aa9`). PDF makalah halaman 16–22 dan 25 dibaca dari sumber asli (SHA-256 `1302d2d23be371a43fb2b4ff7c91d5331537420f3671d361dde69242ccd65dcc`). Pembacaan ulang mengoreksi contoh audit di `Sheet1!H80`: teksnya **Panen Dalmatian**. `H90` berisi **Panen Yellow Hammi**. Di samping K34:K36, K38:K48 yang berisi jadwal semprot juga menampilkan tanggal April yang tidak selaras dengan rangkaian musim. Nilai sumber tetap utuh.

Keputusan pengguna sesi ini:

- Beranda boleh mengambil catatan pekerja yang belum diperiksa dan memberi label **Belum diverifikasi**. Catatan tidak valid tetap ada di riwayat.
- Pencatat memilih layak atau cacat pada setiap rincian panen. Grade yang sama boleh mendapat klasifikasi berbeda pada panen lain.

Inbox mengikuti kebutuhan pemeriksaan catatan pekerja. Catatan pengelola sendiri tidak diberi keputusan otomatis. Catatan lama yang peran asalnya tidak diketahui tetap terbaca. Finalisasi panen, nilai agronomi aktif dan pemetaan periode investasi studi tetap memerlukan keputusan terpisah.

| AC | Source dan gap pada baseline | Bukti yang ditargetkan |
| --- | --- | --- |
| 01, 08 | `ManagerHome` dan `ManagerHomeScreen` sudah scoped. Pesan WIB jauh dari kartu dan keputusan review belum tersedia. | `ManagerHomeTest`, pergantian GH, data kemarin dan offline pada Android. |
| 02–05 | `QueueScreen` adalah outbox, `ActivityService` sudah versioned. Belum ada keputusan manusia. | `ActivityReviewTest`, kontrak v2, alur dua akun pada APK uji. |
| 06–07 | `ActivityDraft` lima jenis, `ReferenceService` snapshot. Belum ada pengamatan terstruktur dan jalur editor EC. | Pemetaan kedua sheet, uji payload, koreksi dan target melalui MySQL serta form. |
| 09 | `HarvestService` multigrade dan kunci saldo. Belum ada target, klasifikasi per rincian atau rekap capaian. | Tes panen bertahap, target terpisah, koreksi/batal dan saldo. |
| 10–11 | `SeasonEvaluation` dan `InvestmentEvaluation` sudah menghitung angka utama dengan sumber dan snapshot. | Oracle studi, tes transaksi sampai snapshot/export, cek navigasi keuangan. |
| 12–13 | `AdminHost` mencakup master dan akun. Kelola greenhouse dan Kelola akun perlu akses langsung. | Regresi administrasi di MySQL dan Android, cek penyiapan parent dan histori. |
| 14–15 | Store/outbox dan palet native tersedia. Belum ada pengujian proses dan visual untuk layar baru. | Instrumentasi, pengujian dua akun, screenshot dan batas tiap konfigurasi. |

Migrasi fitur memakai penambahan kolom/tabel. Source lama dan ledger tetap disimpan. Fixture hanya pada MySQL efektif `inofarm_test` dengan guard sebelum SQL. Baseline Android build/JVM/lint lulus. Suite backend awal mulai berjalan tetapi MySQL sempat berhenti, sehingga hasil itu tidak dipakai sebagai baseline lulus.

## PG1 · Antrean verifikasi

Branch `codex/manager-activity-verification` menambah [kontrak v2](../../contracts/v2/ACTIVITY_REVIEWS.md), migrasi 17, keputusan append-only dan provenance peran asal. Ledger yang sama menangani keputusan, receipt, audit dan feed. Server memeriksa penugasan lagi pada replay. Klien baru menyatakan dukungan jenis feed agar klien lama tetap dapat menarik perubahan lain. Compose memisahkan Antrean pengelola menjadi verifikasi server dan Pengiriman saya. Pekerja membaca alasan di detail kegiatan miliknya. Revisi baru menunggu keputusan baru.

- PG-AC02–05. PHPUnit terarah `ActivityReviewTest|FinanceActivitySyncTest|ManagerHomeTest` lulus pada MySQL uji dengan 12 tes dan 345 assertion. Cakupan meliputi dua pemeriksa untuk revisi yang sama, koreksi, versi basi, replay, perubahan epoch, akses dicabut dan isolasi feedback.
- Pemeriksa kontrak lulus, termasuk validasi alasan wajib, tipe versi, dan penolakan actor buatan klien. Android build, JVM dan lint lulus. Pemeriksa context lulus dengan cakupan KF14/KNF8/U21. Pemeriksa boundary pada working directory menemukan cache lokal `firmware/.pio`; Git tidak memiliki file terlacak dalam folder firmware.
- `FinanceBackendDeviceTest#managerVerifiesWorkerRevisionsThroughGuardedBackend` lulus pada emulator API 30 dengan dua akun sintetis dan APK uji terpisah. Pekerja mengisi form ukur dan mengirim melalui outbox. Pengelola menerima catatan pada inbox, mengirim keputusan tidak valid dengan alasan, mengulang envelope yang sama dan membuka Pengiriman saya. Pekerja membaca feedback di Android, mengoreksi melalui HTTP transport APK, dan pengelola memutuskan revisi kedua valid dari UI. Response final memuat dua keputusan dan alasan lama.
- Screenshot lokal dalam `outputs/manager-alignment/` menunjukkan loading detail, keputusan masih di antrean, dan alasan yang terlihat oleh pekerja. Arah Operate memakai panel solid dan kontrol Material yang sudah ada. Screenshot baseline sesi ini, TalkBack, tema dan ukuran lain, pengiriman keputusan saat proses mati, dan pembuktian dua pengelola paralel pada APK masih terbuka. Tes MySQL menguji konflik secara berurutan dan ledger menserialisasi transaksi.

## PG2 · Kegiatan dan acuan

Branch `codex/activity-observations-and-references` bergantung pada `8d96a31`. Migrasi 18 menambah pengamatan terstruktur opsional tanpa konversi teks lama. Form pekerja dan pengelola memakai penyimpanan draf yang sama. Tab Kegiatan pengelola membuka pemilih konteks langsung bila periode belum siap. Editor acuan menerima pengukuran dan EC mS/cm. Realisasi menyimpan versi jadwal, dan detail verifikasi memisahkan aktual dari acuan. [Preview semua baris kegiatan Excel dan anomali](MANAGER_EXCEL_MAPPING_20261110.md).

PG-AC06–07 diverifikasi sebagian. MySQL guarded lulus 15 tes dan 364 assertion melalui `FinanceMeasurementTest|FinanceActivitySyncTest|ActivityReviewTest|FinanceReferenceTest`. Hasil mencakup EC target dalam/luar rentang, unit salah, versi target, material, skip dan pengamatan kosong/nol. JVM, build dan lint lulus, termasuk tes draf dengan koma desimal. Peninjauan editor target EC dan pengamatan opsional pada layar emulator masih terbuka.
