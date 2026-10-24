# Diagram proposal aplikasi pencatatan greenhouse

Tanggal: 21 Oktober 2026. Status: **rancangan target disepakati untuk proposal; keuangan sementara**.

Paket memuat 17 diagram dalam tujuh jenis. [Buka PDF lengkap](Diagram-Proposal-Greenhouse.pdf). Gambar PNG ada di `png/`, gambar vektor di `svg/`, dan sumber diagram di `sumber/`.

## Keputusan yang berlaku

- Tidak ada IoT. Pekerja mengisi catatan harian melalui Android, termasuk hasil ukur PPM/pH manual.
- Kotlin + Laravel REST API + MySQL. Penyimpanan lokal dan antrean sinkronisasi merupakan rancangan target; bukan fitur yang sudah dibuktikan ada pada aplikasi lama.
- Banyak greenhouse. Pengelola membuat akun pekerja dan penugasan GH; pekerja tidak mengakses keuangan.
- Nama musim dapat diubah melalui kolom teks, sementara ID tetap. Setiap musim memiliki kelompok varietas dengan tanggal semai/pindah tanam. HSS/HST dimulai dari 1; HST belum berlaku sebelum pindah tanam.
- Pekerja dapat melihat jadwal, mengisi kegiatan dan panen, meninjau riwayat serta mengoreksi catatan sendiri pada musim terbuka. Pengelola membatalkan dan membuka kembali musim dengan alasan.
- Model sementara: penjualan lunas, bertahap, dapat menggabungkan beberapa panen dari musim yang sama. Panen belum terjual bukan penerimaan.
- Catatan draf, status kegiatan, status sinkronisasi dan pembatalan dipisahkan. Laporan hanya memakai catatan sah yang tersinkron.

## Daftar diagram

| No. | Jenis | Cakupan | Gambar |
|---|---|---|---|
| 01 | Use case | Operasional | [SVG](svg/01-use-case-operasional.svg) |
| 02 | Use case | Keuangan dan administrasi | [SVG](svg/02-use-case-keuangan.svg) |
| 03 | Activity | Input harian dan penyimpanan | [SVG](svg/03-activity-pencatatan.svg) |
| 04 | Activity | Transaksi dan evaluasi | [SVG](svg/04-activity-keuangan.svg) |
| 05 | Activity | Koreksi, konflik, pembatalan | [SVG](svg/05-activity-koreksi.svg) |
| 06 | Sequence | Login dan otorisasi | [SVG](svg/06-sequence-login.svg) |
| 07 | Sequence | Simpan lokal dan sinkronisasi | [SVG](svg/07-sequence-sinkronisasi.svg) |
| 08 | Sequence | Evaluasi dan ekspor | [SVG](svg/08-sequence-evaluasi.svg) |
| 09 | Class | Domain operasional | [SVG](svg/09-class-operasional.svg) |
| 10 | Class | Domain keuangan | [SVG](svg/10-class-keuangan.svg) |
| 11 | ERD | Operasional dan akun | [SVG](svg/11-erd-operasional.svg) |
| 12 | ERD | Keuangan dan investasi | [SVG](svg/12-erd-keuangan.svg) |
| 13 | ERD | Audit, sinkronisasi dan administrasi | [SVG](svg/13-erd-audit-sinkronisasi.svg) |
| 14 | User flow | Pekerja | [SVG](svg/14-user-flow-pekerja.svg) |
| 15 | User flow | Pengelola | [SVG](svg/15-user-flow-pengelola.svg) |
| 16 | Flowchart | HSS/HST dan validasi target | [SVG](svg/16-flowchart-umur-validasi.svg) |
| 17 | Flowchart | Evaluasi keuangan | [SVG](svg/17-flowchart-keuangan.svg) |

## Konsistensi model dan batasan

1. Satu kelompok tanam berada dalam satu musim dan memiliki satu varietas. Semua aktivitas, jadwal dan panen mempunyai kelompok yang jelas. Penjualan dan alokasi biaya mempunyai musim yang jelas. Backend harus memeriksa kecocokan kelompok, jadwal, panen, musim dan GH; FK tunggal tidak menggantikan validasi lintas hubungan ini.
2. Jadwal dan target merupakan referensi. Aktual tidak diisi otomatis dari target. Penanda selisih bukan bukti keberhasilan budidaya. Target yang belum diisi menghasilkan belum dapat dibandingkan; toleransi PPM dan waktu belum ditetapkan.
3. Status kegiatan: direncanakan, selesai, dilewati dengan alasan. Status draf terpisah. Status sinkronisasi: lokal, menunggu, tersinkron, konflik; penolakan server mempunyai pesan tersendiri. Pembatalan memiliki audit, bukan penghapusan fisik.
4. UUID objek tetap sepanjang revisinya. Setiap mutasi memiliki `operation_id` baru. Retry menggunakan ID/payload yang sama. `base_version` dipakai untuk mendeteksi konflik. Server menjalankan deduplikasi, pemeriksaan versi, perubahan domain dan audit dalam transaksi atomik. ID sama dengan payload berbeda ditolak.
5. Registry `objek_data` merupakan keputusan desain teknis untuk integritas audit/lampiran. UUID setiap objek domain yang diaudit juga menjadi FK satu-ke-satu ke registry. Diagram domain menyingkat label registry tersebut; audit, lampiran dan operasi merujuk registry dengan FK eksplisit. Pembuatan registry dan objek dilakukan atomik. Entitas registry bukan tabel pengganti semua data bisnis.
6. Entitas referensi di beberapa ERD adalah tabel yang sama. FK pembuat/pencatat/petugas mengacu ke `pengguna`; FK GH pada `skenario_greenhouse` mengacu ke `greenhouse`. Petugas yang dicatat tidak boleh dipalsukan oleh klien; default pencatat berasal dari sesi server, dan kewenangan pencatatan atas nama petugas lain harus diperiksa.
7. Kelompok/musim/master yang dipakai riwayat tidak dihapus fisik. Perubahan acuan tanggal/target memerlukan versi/audit dan konfirmasi; snapshot target pada catatan lama tetap dapat ditelusuri.
8. Biaya bahan, penggunaan bahan dan pembelian aset tidak dihitung ganda. Total alokasi pengeluaran tidak melebihi sumber. Alokasi penyusutan tidak melebihi nilai aset yang dapat disusutkan. Penjualan kumulatif tidak melebihi berat tersedia tiap detail panen; koreksi panen yang sudah terjual tidak boleh membuat saldo negatif tanpa penyelesaian terkait.
9. Nilai uang menggunakan desimal tetap. R/C = TR/TC; B/C sederhana = (TR-TC)/TC. BEP mengikuti definisi proposal baru dan asumsi biaya yang dijelaskan, bukan model margin kontribusi. NPV/IRR memakai periode tahunan. PP/DPP sementara memakai interpolasi dengan asumsi kas merata pada tahun pemulihan. Tidak ada kesimpulan numerik palsu untuk pembagi nol, data kurang, IRR tidak tunggal, atau modal belum kembali.
10. Laporan menyimpan cakupan, versi data, input/asumsi dan waktu pembentukan. Ekspor harus konsisten dengan snapshot yang dilihat. Perhitungan tidak membuat transaksi baru. Musim berjalan diberi label sementara.

## Sumber dan temuan referensi

- **P:** `D:\Semester 7\Capstone 2\Proposal_Capstone_Aplikasi_Mobile_Greenhouse-new-anget.pdf`, Bab II, KF-01–14 dan KNF-01–08. Pilihan Flutter/Express/PostgreSQL di proposal diganti sesuai keputusan pengguna menjadi Kotlin/Laravel/MySQL.
- **X:** `D:\Semester 7\Capstone 2\Jadwal  MT 3 pemupukan dan stadia tumbuh Melon GH Krebet_093055.xlsx`. Sheet1 B6:C15 memperlihatkan HSS 1–10; B16:C16 mengulang 1 pada 14 Oktober 2025, ditafsirkan sebagai awal HST dan disetujui pengguna. H80 dan H90 menyebut dua varietas. Beberapa tanggal spraying dan teks pH akhir tidak konsisten; tidak diimpor sebagai aturan produksi.
- **F:** `D:\Semester 7\Capstone 2\Dwito Mufti Agung_574189_Makalah Analisis Usahatani Meloh Hidroponik.pdf`, Tabel 2–8. Label B/C dan subtotal NPV mengandung ketidakkonsistenan. Dari angka Tabel 7, -300.000.000 + 80.270.654,42 = -219.729.345,58; subtotal -139.458.691,16 tidak dijadikan expected result. Periode panen dan diskonto perlu rekonsiliasi dengan periode tahunan proposal.
- **K1–K5:** keputusan dan persetujuan pengguna dalam percakapan tanggal 21 Oktober 2026.

## Hal yang belum final

Kategori dan dasar alokasi biaya, kondisi transaksi mitra, umur/nilai sisa aset, horizon/diskonto, kebijakan laporan akhir, parameter agronomis, serta jadwal produksi masih perlu konfirmasi. Dukungan platform di SOP akademik perlu diselaraskan dengan target Android. Paket tidak mengklaim implementasi, pentest, UAT atau handover selesai.

## Menggunakan dan mengedit

- Masukkan PNG ke Word untuk penggunaan praktis. Gunakan SVG untuk pembesaran tanpa pecah. Diagram kompleks lebih cocok pada halaman landscape atau lampiran, bukan diperkecil sampai label tidak terbaca.
- DOT dapat diedit dan dirender dengan Graphviz. JSON sequence memuat peserta dan pesan, sedangkan SVG sequence dapat diedit sebagai vektor.
- PDF terdiri dari tiga halaman penjelasan dan 17 halaman diagram. Ukuran halaman mengikuti kebutuhan keterbacaan. Nomor diagram adalah nomor paket, bukan penomoran final Bab II.
- Paket ZIP memuat PDF, PNG, SVG, sumber DOT/JSON dan README. File referensi pengguna tidak disalin ke dalam ZIP.

## Verifikasi

Semua 14 sumber DOT dirender, tiga sequence dibuat sebagai SVG, dan 20 halaman PDF dirender kembali untuk inspeksi visual. Diperiksa: tujuh kategori, hak akses, relasi GH–musim–kelompok, pemisahan panen/penjualan, jalur draf/offline/konflik, arah pesan sequence, serta label dan area halaman. Pemeriksaan ini adalah validasi dokumen/desain, bukan pengujian aplikasi atau pengesahan rumus oleh mitra.
