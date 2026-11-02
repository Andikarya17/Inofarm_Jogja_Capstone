# PRD: pencatatan operasional dan evaluasi keuangan greenhouse

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

Build legacy minSdk26; mode target -PfinanceApp memakai minSdk29 dan suffix test terpisah. Runtime Android10/API29 belum diuji, evidence device saat ini API37. Toleransi non-nominal dan UAT ≥90% tanpa bantuan berstatus usulan proposal; konfirmasi sebelum acceptance resmi. Pentest, UAT, keputusan SOP lintas platform/CRUD historis dan bukti mitra adalah gate handover. Daftar pertanyaan di [DECISIONS](../migration/finance-20261029/DECISIONS.md).
