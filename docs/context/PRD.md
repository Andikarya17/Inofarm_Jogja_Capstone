# PRD: pencatatan operasional dan evaluasi keuangan greenhouse

## Baseline kebutuhan aktif, 10 November 2026

KF01–14/KNF01–08 tetap berlaku. Keputusan terbaru menambah inbox verifikasi catatan pekerja per revisi, label belum diverifikasi pada pengukuran yang diterima server, pengecualian revisi invalid dari kartu pengelola, pengamatan opsional terstruktur, target panen terpisah dari populasi, kondisi layak/cacat per rincian, dan penyelesaian panen manual terpisah dari penutupan musim. Menu akun menyediakan Kelola greenhouse/Kelola akun. [Keputusan dan bukti PG0–PG7](MANAGER_ALIGNMENT_EVIDENCE_20261110.md).

Produk aktif Android native minSdk29, finance default tanpa flag; dua role worker/manager dengan assignment eksplisit. Pemilihan GH di Beranda pengelola tidak mengubah parent draf. [CLAUDE.md](../../CLAUDE.md) memuat matriks kewenangan/alur. [Audit10 November](SYSTEM_AUDIT_20261110.md) memisahkan kebutuhan, source dan database lokal yang masih tertinggal. Bagian FIN bertanggal di bawah adalah riwayat, bukan backlog terbaru atau penundaan push yang masih berlaku.

## Beranda pengelola — 8 November 2026

Login pengelola membuka semua greenhouse dalam penugasan. Satu ketukan pada greenhouse membuka ringkasan periode budidaya yang ditetapkan secara eksplisit, pasangan EC/PPM, panen, catatan diterima, dan kas bulanan WIB. Pembukaan musim untuk koreksi transaksi tetap terpisah. Temuan menunjukkan alasan dan waktu pemeriksaan. Draf tetap terikat parent asal saat cakupan browsing berubah. [Acceptance dan bukti](MANAGER_HOME_UPGRADE_EVIDENCE.md)

## Perubahan pengguna2 November2026

KF-04 diperluas dengan pengukuran EC mS/cm dan PPM ppm manual sebagai kegiatan tersendiri tanpa pemberian bahan; keduanya wajib saat submit, draf boleh parsial. pH manual dan histori tetap. Beranda memiliki tepat dua kartu EC/PPM dari satu catatan valid, kalender waktu ukur WIB hari ini→kemarin→sebelumnya, tanpa nol/rekaan atau campur scope. [Aturan lengkap dan state](../../contracts/v2/MEASUREMENTS.md). Tidak menghapus KF01–14, KNF01–08, U01–21. Instruksi ini mengatasi uraian legacy/coexistence di bawah.

## Lanjutan FIN16–17 — 2 November 2026

Administrasi pengelola kini mempunyai form native akun/master/assignment/jadwal/target dengan durable encrypted draft dan review/retry eksplisit. Job network-aware memakai outbox existing; profil terkini diperiksa sebelum send. [FIN16 acceptance](../migration/finance-20261029/FIN16_ACCEPTANCE.md) tetap PARTIAL untuk UAT/pentest/perangkat sasaran; [FIN17 install/run](../migration/finance-20261029/FIN17_INSTALL_RUN.md) dan cutover gates tersedia. Tidak ada perubahan KF/KNF atau keputusan produksi baru.

## FIN15 — 2 November 2026

FIN15 core PASS lokal: pengelola dengan kapabilitas operator sistem eksplisit dapat membuat cadangan manual privat, membaca status/riwayat dan memeriksa integritas paket. Riwayat drill restore terverifikasi tersedia di aplikasi. Setelah restore, operasi lama ditahan; receipt cocok diakui secara eksplisit tanpa replay, receipt hilang tetap memerlukan perbandingan manual. Isian maupun salinan lokal yang berbeda dari titik restore tetap disimpan. Q08 jadwal/retensi/RPO/RTO/operator produksi belum ditetapkan; tidak ada apply produksi melalui HTTP. [Kontrak](../../contracts/v2/RECOVERY.md), [evidence/batas](../migration/finance-20261029/evidence/FIN15.md). Commit lokal per fitur; push tetap ditunda pengguna.

## FIN14 — 2 November 2026

FIN14 core tersedia dan PASS lokal: pengelola dapat mengunduh PDF/CSV dari laporan musim atau investasi yang sudah diterima server. Isi memakai snapshot yang sama dengan tampilan, termasuk sumber/versi, input, status, alasan dan asumsi; koreksi sumber tidak menghitung ulang laporan lama. Nilai belum diketahui tetap belum diketahui. Pemilihan tujuan Android, pembatalan dan kegagalan unduhan tidak mengklaim sukses palsu. [Evidence/batas](../migration/finance-20261029/evidence/FIN14-backend.md), [kontrak](../../contracts/v2/REPORT_EXPORT.md). Commit lokal per fitur; push ditunda pengguna.

## Perbaikan histori FIN13 — 2 November 2026

Cancelled-only fresh-cache history kini dipertahankan dengan typed hydration/current scope; raw draft tidak ditimpa. APK/unit66 dan device17PASS (10 investasi +7 evaluasi musim). [Evidence](../migration/finance-20261029/evidence/20261102-CANCELLED_SCENARIO_HISTORY.md). Gap terkait ditutup lokal. Berikutnya FIN14; push ditunda pengguna, FIN15–17 di luar scope.


## Lanjutan FIN13 — 2 November 2026

Investasi terpisah dari laba musim: input net cash manual actual/projection dengan basis, I0 sekali, diskonto tanpa default, hasil independen dan laporan immutable. Backend dan native critical acceptance PASS lokal. [Evidence dan batas verifikasi](../migration/finance-20261029/evidence/FIN13-backend.md).


Native66 JVM/lint0,8 Android cases dan3 fase real HTTP/MySQL dengan dua OS reboot + SQL reconciliation PASS. [Evidence/batas native](../migration/finance-20261029/evidence/FIN13-native.md). FIN14 ekspor belum dimulai; checkpoint ini dipublikasikan atas instruksi terbaru pengguna.

## Lanjutan FIN12 — 2 November 2026

Native critical acceptance: JVM59/lint0,7 clean device cases and real API29 offline→two OS reboots→immutable reports/source correction/worker403+SQL reconciliation PASS. Broader cross-screen popup/touch regression remains open. [Native evidence](../migration/finance-20261029/evidence/FIN12-native.md).

FIN12 backend evaluasi musim tersedia dengan sebelas indikator independen, nol vs missing, coverage/pending eksplisit, final review dan sumber immutable. Native entry/acceptance masih diperiksa; investasi/ekspor/recovery dan gate produk belum selesai. [Evidence backend dan batas acceptance](../migration/finance-20261029/evidence/FIN12-backend.md). Regresi backend terbaru108 PASS/1755 assertions, dua race MySQL independen dan1024 oracle independen PASS; native acceptance masih berjalan.


## Lanjutan FIN11 — 30 Oktober 2026

FIN11 backend/schema11 dan native aset/penyusutan manual kini terverifikasi lokal: sumber/form/review, raw draft/autosave/Back/ON_STOP/logout, riwayat/audit/konflik/pembatalan, SQLite8 additive, dan tiga fase API nyata dengan dua reboot Android (boot3→4→5) serta SQL reconciliation PASS. Lihat [backend/math](../migration/finance-20261029/evidence/FIN11-backend.md), [native core](../migration/finance-20261029/evidence/FIN11-native-core.md), dan [native entry/acceptance](../migration/finance-20261029/evidence/FIN11-native-entry.md). Regresi69 mencatat66 PASS dan3 kegagalan yang ditindaklanjuti dengan fokus API36.1; tidak diklaim satu run69 bersih. JVM53/lint0errors; FIN12–17 dan gate produk tetap terbuka. Salinan proposal telah diterima dan hash/60 halaman cocok baseline; [verifikasi sumber](../migration/finance-20261029/evidence/20261030-PROPOSAL_RECEIVED.md). Nilai produksi tetap kosong; akses push ditunda pengguna.


Versi target 29 Oktober 2026. Sumber P/U/G/S didefinisikan di [SOURCES](../migration/finance-20261029/SOURCES.md). KF/KNF di bawah menggunakan nomor **proposal baru**, tidak setara dengan KF IoT lama. Implementasi bertahap tersedia sesuai [status/evidence](IMPLEMENTATION_STATUS.md); [audit](../migration/finance-20261029/AUDIT_COMPARISON.md) adalah baseline sebelum perubahan executable.

## 1. Tujuan dan cakupan

Membantu pekerja dan pengelola Inofarm mencatat budidaya melon NFT per greenhouse, musim, kelompok tanam; menelusuri hubungan kegiatan, bahan, pengeluaran, panen dan penjualan; mengevaluasi musim dan skenario investasi dengan input/rumus/asumsi yang transparan. Tidak menjamin peningkatan panen atau keuntungan.

Frontend Android Kotlin + Jetpack Compose; backend Laravel REST; database MySQL. Input PPM/pH manual melalui Android. Tidak ada ESP32, telemetry otomatis, sensor otomatis, automated dosing, atau login khusus perangkat pada produk target. Firmware lama tetap arsip source sementara.

Termasuk nutrisi, penyemprotan multi-bahan, stadia aktual, pekerjaan umum, kegiatan dilewati, panen per grade, draf/offline/sync, role, audit, biaya, penjualan lunas, aset/penyusutan, evaluasi, investasi, PDF/CSV, backup/restore. Tidak termasuk piutang, pinjaman, pajak, akuntansi lengkap, bank/payment gateway, e-commerce, maupun stok bahan otomatis. Pemakaian bahan bukan transaksi kas baru.

## 2. Aktor dan akses

| Kemampuan | Pekerja | Pengelola |
|---|---|---|
| Login, sesi, logout | Akun sendiri | Akun sendiri |
| Baca konteks, jadwal, target, riwayat operasional | Greenhouse penugasan | Cakupan usaha yang dikelola |
| Catat kegiatan/panen | Penugasan sah, musim terbuka | Ya, cakupan sah |
| Koreksi | Catatan sendiri, musim terbuka | Catatan dalam cakupan, alasan/audit |
| Pembatalan, buka kembali musim | Tidak | Ya, alasan/audit dan validasi referensi |
| Master/akun/penugasan/jadwal/target | Baca sesuai akses | Kelola |
| Keuangan, investasi, laporan keuangan, bukti transaksi | Tidak, termasuk API/file/sync | Ya |
| Backup/restore | Tidak | Terbatas; restore produksi melalui gate operasional |

Identitas pencatat berasal dari sesi server. `performed_by`/petugas bila berbeda dari pencatat harus mengikuti kewenangan, tidak boleh memalsukan creator. Master yang dipakai riwayat dinonaktifkan; riwayat tidak dihapus berantai. Pengelola menutup musim setelah review; data terlambat harus ditinjau dan musim dibuka kembali bila disetujui.

## 3. Requirement dan penerimaan

| ID | Kebutuhan proposal | Acceptance minimum | Task |
|---|---|---|---|
| KF-01 | Auth, sesi, role dan scope server | Pekerja ditolak mengakses keuangan/GH lain lewat request langsung; sesi dicabut tidak bisa sync | FIN-02 |
| KF-02 | User, penugasan, GH, varietas, musim, kelompok | Relasi GH–musim–kelompok konsisten; deactivate tidak merusak histori | FIN-03 |
| KF-03 | Tanggal, stadia, jadwal/target/version | HSS/HST hari pertama 1; target historis snapshot; perubahan audited | FIN-04 |
| KF-04 | Nutrisi, pengukuran manual, bahan, waktu/petugas | Aktual tidak diisi dari target; nilai/satuan sah; creator dari sesi | FIN-06 |
| KF-05 | Penyemprotan multi-bahan | Detail bahan >1 dapat disimpan, satuan valid, tiap field error jelas | FIN-06 |
| KF-06 | Filter, koreksi, batal, audit | Base version conflict tidak menimpa; alasan/pelaku/waktu/versi tersimpan | FIN-07 |
| KF-07 | Pengeluaran, bukti, kategori, alokasi | Alokasi gabungan tidak melebihi sumber termasuk request bersamaan | FIN-09 |
| KF-08 | Panen grade dan penjualan lunas | Penjualan bertahap, sumber satu musim, saldo tidak negatif | FIN-08, FIN-10 |
| KF-09 | Aset dan penyusutan teralokasi | Umur >0, 0≤nilai sisa≤harga, tidak double-count pembelian/penyusutan | FIN-11 |
| KF-10 | Evaluasi musim | TC/TR/L/rasio/BEP sesuai fixture; indikator invalid punya alasan | FIN-12 |
| KF-11 | Investasi tahunan | NPV/IRR/PP/DPP sesuai input, asumsi dan syarat per indikator | FIN-13 |
| KF-12 | Ringkasan dan PDF/CSV | Export menggunakan snapshot yang dilihat, bukan data terbaru diam-diam | FIN-14 |
| KF-13 | Local durable, idempotensi, pull, konflik | Process death tidak hilang; commit+response lost tidak duplicate; akun terisolasi | FIN-05, FIN-07 |
| KF-14 | Backup dan restore audit | DB+file+manifest+checksum; restore uji cocok; sync lama direkonsiliasi | FIN-15 |

## 4. Aturan operasional

- GH memiliki banyak musim; musim memiliki beberapa kelompok; kelompok mempunyai varietas, populasi, tanggal semai/pindah. Label musim boleh berubah tanpa mengubah identitas.
- HSS = selisih tanggal kegiatan dan semai +1; HST = selisih tanggal kegiatan dan pindah +1; tanggal kalender Asia/Jakarta. Sebelum pindah, HST `null`/belum berlaku. Pindah tidak boleh sebelum semai. Kebijakan kegiatan sebelum semai dikonfirmasi, tidak membuat umur positif palsu.
- Stadia dipilih dari kondisi aktual, tidak ditetapkan otomatis oleh umur. Jadwal opsional pada aktivitas spontan. Satu jadwal dapat memiliki beberapa realisasi; aturan completion belum diputuskan.
- PPM/jumlah bahan tidak negatif; pH 0–14; numerik berhingga; kosong berbeda dari nol; kolom wajib mengikuti jenis kegiatan. Rentang agronomis tidak di-hardcode.
- Target dipilih menurut kelompok, jenis, waktu kejadian, masa berlaku. Simpan nilai acuan, versi aturan, seluruh hasil/alasan. Angka tunggal tanpa toleransi hanya menghasilkan selisih, bukan label sesuai/tidak sesuai. Target/aturan kosong menghasilkan belum dapat dibandingkan.
- Koreksi tanggal/target: konfirmasi dan audit. Re-evaluasi historis eksplisit membuat revisi, tidak mengubah snapshot lama diam-diam.
- Empat dimensi terpisah: kegiatan (direncanakan/selesai/dilewati + alasan), kelengkapan (draf/siap diajukan), sinkronisasi (lokal/menunggu/tersinkron/konflik/ditolak), pembatalan (aktif/dibatalkan). Upload dan pembandingan memiliki state sendiri. Draf/batal tidak masuk evaluasi; `tersinkron` bukan sinonim agronomis benar atau semua transaksi lengkap.

## 5. Aturan keuangan

Pengeluaran adalah pembayaran aktual; alokasi memiliki dasar dan tujuan musim. Pembelian aset tidak masuk biaya operasi penuh sekaligus penyusutan. FC mencakup biaya tetap teralokasi dan penyusutan, VC biaya variabel teralokasi. Semua total server-authoritative dan memakai desimal tetap.

Panen bukan penjualan. Detail penjualan merujuk detail panen satu musim; hanya penjualan lunas dalam model awal. Transaksi bersamaan dan koreksi harus menjaga saldo panen serta plafon alokasi. Retur/kerusakan dan kebijakan penjualan lunas tetap keputusan mitra terbuka.

Rumus, tipe dan syarat di [DATA_CONTRACTS](DATA_CONTRACTS.md). Musim berjalan dilabeli sementara. Finalisasi perlu checklist kelengkapan biaya/alokasi/penjualan/antrean diketahui; sistem tidak mengklaim mendeteksi semua perangkat offline. Investasi adalah skenario tersendiri, input aktual/proyeksi ditandai; penyusutan bukan cash outflow. Nilai produksi diskonto, umur aset, target, jadwal dan harga tidak otomatis diisi dari contoh.

## 6. UX dan layar

Pekerja: Login → pilih GH/musim/kelompok → beranda jadwal + antrean → formulir jenis kegiatan/panen → review → simpan draf atau antrekan → detail status → riwayat/koreksi/konflik.
Pengelola: kemampuan operasional + master/penugasan + pengeluaran/alokasi + aset + penjualan + evaluasi musim + investasi + ekspor + administrasi/backup.

Layar wajib: login, beranda, konteks, jadwal/target, form kegiatan, panen grade, riwayat/detail/audit, konflik versi, antrean, pengeluaran/alokasi/bukti, penjualan/saldo, aset/penyusutan, evaluasi, investasi, master/admin. Layout boleh bertahap, semua KF tetap target.

Bahasa Indonesia; unit dekat field; keyboard numerik sesuai format lokal; nilai wire tidak memakai pemisah ribuan. Loading/kosong/error/offline/last-updated jelas. Simpan lokal tidak disebut diterima server. Error per field dan ringkasan; tombol dapat dijangkau saat keyboard muncul, font besar, landscape dan dua ukuran layar. Back/recreation tidak menghilangkan draf. Data keuangan tidak muncul di menu/cache pekerja. Prioritaskan formulir yang diuji mitra sebelum efek visual akhir.

## 7. Non-fungsional

| ID | Target |
|---|---|
| KNF-01 | Bahasa Indonesia, satuan jelas, diuji tugas kedua peran |
| KNF-02 | Simpan lokal ≤2 detik; ≥30 pengulangan, 1.000 catatan sintetis, semua kegagalan dilaporkan |
| KNF-03 | Draf/antrean tetap ada setelah tutup/kill dan buka offline |
| KNF-04 | HTTPS, hash password, scope server, isolasi akun, file privat |
| KNF-05 | Retry operasi identik tidak duplicate termasuk respons hilang setelah commit |
| KNF-06 | Uang fixed decimal, deviasi nominal ≤Rp1; usulan rasio 0,0001, IRR 0,0001 poin persentase, PP/DPP 0,0001 tahun |
| KNF-07 | Restore diuji untuk data/relasi/file, tidak hanya backup berhasil |
| KNF-08 | Target minimum Android 10, ≥2 ukuran layar; tidak terpotong |

Build aktif minSdk29 tanpa flag finance; debug/profile memakai suffix `.finance`. Evidence historis mencakup API29 dan PG7 API30, bukan bukti perangkat fisik/UAT. Toleransi non-nominal dan UAT ≥90% tanpa bantuan berstatus usulan proposal; konfirmasi sebelum acceptance resmi. Pentest, UAT, keputusan SOP lintas platform/CRUD historis dan bukti mitra adalah gate handover. Daftar pertanyaan di [DECISIONS](../migration/finance-20261029/DECISIONS.md).
