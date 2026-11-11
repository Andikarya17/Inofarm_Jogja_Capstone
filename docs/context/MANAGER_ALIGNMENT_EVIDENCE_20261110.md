# Penyelarasan pengelola, 10 November 2026

## PG0 · Baseline dan keputusan

HEAD awal `52eafe3` adalah turunan `8d74cba`. Lima perubahan sesudah audit hanya menyentuh context. Setelah fetch, `github/main` pada `bbd712a` sudah mencakup runtime `8d74cba`. Perbandingan tree menunjukkan perbedaan hanya pada lima dokumen tersebut. Branch PG1 berangkat dari `52eafe3` agar kontrak pekerjaan dan baseline audit ikut tersedia.

Kedua sheet Excel dibaca langsung dari OOXML (SHA-256 `39091a131b63439f7223615e8872b34449c4c4f6ab8dfe411f78c8d7baf65aa9`). PDF makalah halaman 16–22 dan 25 dibaca dari sumber asli (SHA-256 `1302d2d23be371a43fb2b4ff7c91d5331537420f3671d361dde69242ccd65dcc`). Pembacaan ulang mengoreksi contoh audit di `Sheet1!H80`: teksnya **Panen Dalmatian**. `H90` berisi **Panen Yellow Hammi**. Di samping K34:K36, K38:K48 yang berisi jadwal semprot juga menampilkan tanggal April yang tidak selaras dengan rangkaian musim. Nilai sumber tetap utuh.

Keputusan pengguna sesi ini:

- Beranda boleh mengambil catatan pekerja yang belum diperiksa dan memberi label **Belum diverifikasi**. Catatan tidak valid tetap ada di riwayat.
- Pencatat memilih layak atau cacat pada setiap rincian panen. Grade yang sama boleh mendapat klasifikasi berbeda pada panen lain.
- Pengelola menandai panen selesai secara manual dengan alasan. Penutupan musim memakai tindakan terpisah.

Inbox mengikuti kebutuhan pemeriksaan catatan pekerja. Catatan pengelola sendiri tidak diberi keputusan otomatis. Catatan lama yang peran asalnya tidak diketahui tetap terbaca. Nilai agronomi aktif dan pemetaan periode investasi studi tetap memerlukan keputusan terpisah.

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

PG-AC06–07 diverifikasi di backend dan Android. MySQL guarded lulus 15 tes dan 364 assertion melalui `FinanceMeasurementTest|FinanceActivitySyncTest|ActivityReviewTest|FinanceReferenceTest`. Hasil mencakup EC target dalam/luar rentang, unit salah, versi target, material, skip dan pengamatan kosong/nol. JVM, build dan lint lulus, termasuk tes draf dengan koma desimal. Peninjauan editor target EC dan pengamatan opsional pada emulator memakai HTTP mock pada PG7.

## PG3 · Beranda pengelola

Branch `codex/manager-home-measurement-status` bergantung pada `bf396db`. Beranda mengambil pasangan EC/PPM dari satu revisi, memberi label pending, valid, pencatat pengelola atau histori lama, dan mengecualikan revisi tidak valid dari sumber kartu. Riwayat tetap memuat catatannya. Teks di dekat kartu membedakan hari kosong, hanya pengukuran tidak valid, dan data yang belum dapat diperiksa. Semua greenhouse menampilkan panen untuk kelompok yang sedang ditunjuk di tiap kartu. Kas bulan WIB memakai definisi sebelumnya.

PG-AC01 dan PG-AC08 sebagian diverifikasi. MySQL guarded lulus 10 tes dan 376 assertion lewat `ManagerHomeTest|ActivityReviewTest|FinanceMeasurementTest`, termasuk data kemarin, pending, invalid dengan fallback, koreksi valid, pergantian WIB, dan batas greenhouse. Android build/JVM/lint lulus. Bukti visual dan offline dengan perubahan PG3 menyusul pada PG7.

## PG4 · Panen bertahap

Branch `codex/harvest-target-progress` bergantung pada `02dc3fa`. Migrasi 19 menambahkan target dan penyelesaian kelompok serta kondisi pada rincian panen. Android meminta pilihan layak/cacat untuk panen baru, menampilkan varietas kelompok, dan menyediakan Rekap panen pada tab Panen. Catatan lama tanpa kondisi tetap terbaca dan menahan kesimpulan capaian bila angkanya belum terklasifikasi. Snapshot panen menyimpan versi varietas dan target saat dicatat. Keputusan selesai atau buka kembali memakai ledger dan outbox, serta alasan yang diaudit. Selesai menahan panen baru hingga dibuka kembali. Koreksi/batal tetap mengikuti musim dan saldo yang sudah terpakai.

PG-AC09 sebagian diverifikasi. `FinanceHarvestTest|FinanceSaleTest|FinanceMasterTest|ManagerHomeTest` lulus dengan 22 tes dan 527 assertion pada MySQL guarded. Kasus mencakup target 160/hasil layak 150, capaian 93,75%, koreksi di atas target, unknown, pembatalan, histori target lama, scope, replay dan saldo penjualan. Build/JVM/lint dan build APK instrumentasi lulus. Bukti klik Android direkap pada PG7.

## PG5 · Keuangan

Branch `codex/manager-finance-workflows` bergantung pada `b99cb9a`. Keuangan mengarahkan pengelola ke biaya dan alokasi, perolehan aset dan penyusutan, penjualan lunas, evaluasi musim, serta analisis investasi tahunan. Evaluasi menjelaskan basis B/C sederhana dan BEP di bagian perhitungan. Engine, versi rumus dan snapshot yang telah terbit tetap memakai kontrak existing.

Hasil oracle studi: FC Rp5.421.100, VC Rp3.430.000, TC Rp8.851.100, TR Rp24.435.000, laba Rp15.583.900, R/C 2,76067381, B/C sederhana 1,76067381, BEP 327,818519 kg dan Rp9.780,220994/kg. Teks makalah menyebut R/C 2,26 dan memberi judul B/C yang sama dengan R/C, padahal contoh B/C memakai laba. Jumlah PV tercetak Rp80.270.654,42 memberi NPV −Rp219.729.345,58 bila dikurangi I0 Rp300.000.000 sekali. Subtotal makalah menambahkan PV lagi. Investasi Rp310.161.000 berasal dari tabel aset dengan cakupan berbeda. Diskonto per musim/panen dan per tahun tidak konsisten, serta lampiran yang berlabel PP memakai PV. Tidak ada penyamaan panen dengan tahun, konversi basis diskonto, atau seed nilai studi.

PG-AC10–11: 25 tes MySQL guarded dan 498 assertion lulus untuk aset, evaluasi musim, investasi dan ekspor. Suite menjalankan transaksi sampai preview, publikasi snapshot dan export. Oracle independen memeriksa 1.025 kasus evaluasi musim, 259 investasi, dan 1.016 anggaran penyusutan tahunan, termasuk hash snapshot sumber dan pembulatan. Build/JVM/lint lulus. Klik alur laporan dari APK terhubung fixture diuji terpisah pada PG7.

## PG6 · Administrasi

Branch `codex/manager-administration-entry` bergantung pada `313ad5e`. Menu akun menyediakan Kelola greenhouse dan Kelola akun. Detail greenhouse menampilkan kesiapan, musim, varietas, populasi, tanggal semai/pindah serta stadia aktual bertanggal. Aksi penyiapan memakai editor yang sudah ada dan dapat kembali ke detail greenhouse. Penetapan periode membuka review Beranda pada greenhouse yang dipilih. Form dan pengajuan admin tetap tersimpan per akun melalui jurnal existing.

Build, JVM, lint dan build instrumentasi lulus. PG-AC12–13 memakai regresi backend dan uji jalur menu pada PG7. Backend akun, pencabutan sesi, validasi email dan perlindungan password memakai jalur yang sama dengan pemulihan sebelumnya.

## PG7 · Penerimaan terpadu

Branch `codex/manager-alignment-acceptance` bergantung pada `b9d2508`. Draf keputusan otomatis menyimpan pilihan/alasan per revisi ke store akun. Filter inbox tetap terpasang saat kembali dari detail. Kontrak publik mencatat status verifikasi, pengamatan, rekap panen dan rekonsiliasi operasi penyelesaian setelah pemulihan.

- Backend lengkap melalui `scripts/test-backend.ps1` lulus. Guard efektif MySQL `inofarm_test` berjalan sebelum SQL dan lulus 4 tes/12 assertion. Suite lengkap lulus 141 tes/2.827 assertion, termasuk regenerasi sesi akun, saldo, audit, verifikasi, dan ekspor.
- Kotlin JVM 74 tes lulus. Build APK dan build instrumentasi lulus. Lint melaporkan 0 error, 16 warning dan 1 hint. Pemeriksa boundary lulus pada arsip tree Git yang mengabaikan cache lokal `firmware/.pio`. Kontrak dan context lulus.
- PG-AC01–05 dan PG-AC08. `pg7-real-review-home.txt` mencatat satu tes lulus pada APK uji API 30 dengan Laravel/MySQL. Pekerja mencatat pengukuran dari form Android. Pengelola membuka inbox server, menyatakan revisi pertama tidak valid, dan mengulang permintaan yang sama. Pekerja melihat alasan di Android. Koreksi dikirim dari harness APK ke API yang sama, lalu pengelola memvalidasi revisinya di Android. Beranda menampilkan EC 1,25 mS/cm dan PPM 630 dari revisi valid. Kedua akun diuji bergantian pada satu emulator.
- PG-AC06–07. `pg7-observations-target-final.txt` mencatat satu tes UI lulus dengan HTTP mock. Form kegiatan pengelola mengirim DO 6,5 mg/L dan jumlah daun nol, dengan tinggi tanaman kosong dikecualikan dari payload. Pengelola memilih Pengukuran manual, EC manual dan mS/cm pada editor Target dan membuka konfirmasi. Uji perubahan target pada MySQL berjalan terpisah di PG2.
- PG-AC09. `managerReadsPartialHarvestAndCompletesOnlyHarvest` lulus pada emulator API 30 dan MySQL uji. Dua panen ditulis dari form pekerja. Pengelola membuka rekap 150 buah layak dan 90,75 kg terhadap target 160. Android menampilkan 93,75%. Penyelesaian menyimpan alasan, dan pemeriksaan server menemukan musim masih terbuka. Bukti: `pg4-device.txt`, `pg4-harvest-progress.png`, `pg4-reconciliation.json`.
- PG-AC10, PG-AC11, PG-AC14. Tiga fase `FinanceSeasonReportBackendDeviceTest` lulus melalui APK uji terpisah dan MySQL guarded. Koneksi emulator ke API uji diputus dua kali dan proses APK dihentikan antar fase. Envelope/hash pengajuan tetap sama. Koreksi sumber menolak preview basi, lalu pengelola membuka Pengiriman saya, meninjau ulang dan menerbitkan laporan baru. SQL terpisah memverifikasi TC lama Rp113,33, TC baru Rp80,00, TR nol dan harga rata-rata null, enam sumber beku per laporan dan satu audit/receipt/feed per laporan.
- Pemeriksaan kontras enam pasangan token yang digunakan memberi 6,04:1 sampai 14,72:1. Panel data dan isian memakai permukaan solid, sementara tindakan berlabel dan komponen Material memakai target sentuh minimum 48 dp. Pemeriksaan ukuran dan interaksi dicatat terpisah dari kontras warna.
- PG-AC01, PG-AC05 dan PG-AC12–15. `pg7-native-regression.txt` mencatat 37 tes Android lulus pada API 30. Cakupan meliputi 29 tes penyimpanan/sinkronisasi, dua tes editor admin, dan enam tes alur UI dengan HTTP mock. Kasus mencakup pemilihan greenhouse dan respons lama, Beranda landscape, tampilan cache, akses menu administrasi, skip, rotasi draf panen, serta penolakan bisnis, konflik dan pemulihan epoch. Tes editor mencakup form gelap dengan font 200% dan membuka lagi draf sesudah Back.
- PG-AC15. Empat tes UI lulus pada lebar 320 dp, tema gelap dan font 160%, mencakup Beranda, akses admin, draf, serta konfirmasi (`pg7-narrow-dark.txt`). Pada ukuran tablet 700 dp, tema terang dan font 100%, walkthrough menu lulus. Tes landscape pada percobaan gabungan habis waktu menunggu rotasi. Pemeriksaan ulang dengan layar virtual yang sudah diatur ke 900 × 700 dp landscape lulus untuk kartu EC dan tautan keuangan (`pg7-tablet-landscape.txt`). Screenshot berawalan `pg7-320-` dan `pg7-tablet-` memakai data sintetis.
- Tinjauan visual memakai `outputs/worker-recording-recovery/after-manager-activity-detail.png` sebagai bukti sebelum perubahan. Detail itu menampilkan penerimaan dan acuan. Screenshot PG7 `pg1-manager-review.png`, `pg1-manager-decision.png`, `pg1-worker-feedback.png` dan `pg7-manager-home-reviewed.png` memperlihatkan tambahan status keputusan, alasan, dan pasangan pengukuran pada Beranda. Screenshot `pg4-harvest-progress.png` dari PG4 mendahului penyesuaian jarak rekap di PG7.

Log, rekonsiliasi fixture, dan screenshot PG1–PG7 disimpan lokal di `outputs/manager-alignment/`. MySQL menguji konflik keputusan secara berurutan. Verifikasi manual TalkBack, alur penyiapan admin lintas akun pada APK terhubung, dan dua pengelola yang mengirim secara paralel dari perangkat terpisah masih terbuka. Perangkat fisik, UAT, pentest, penerimaan akademik, dan produksi berada pada gate serah terima terpisah.
