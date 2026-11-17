# Penyelarasan backend dengan jawaban lapangan Inofarm, 17 November 2026

Sumber kebutuhan: jawaban Inofarm atas kuesioner 15 November 2026 (`D:/Semester 7/Capstone 2/Simpan/Pertanyaan untuk Inofarm Answered.docx`). Lingkup: backend, kontrak dan tes. Android belum diubah.

## Perubahan

| No | Jawaban lapangan | Perubahan backend |
|---|---|---|
| P1-1 | Bahan, satuan, grade sama di semua GH (2.7, 5.1) | `POST greenhouses/{id}/copy-masters` menyalin varietas, satuan, bahan, grade dan kategori biaya aktif dari GH lain. Nama (dan dimensi) yang sudah ada dilewati sehingga aman diulang. Tiap salinan lewat ledger biasa (audit, feed, identitas). Master tetap per GH, tidak dijadikan global, karena Android menyimpan cache master per `greenhouse_id` |
| P1-2 | Listrik/pestisida dibagi rata antar GH, gaji bulanan (7.2, 7.3) | Alokasi biaya boleh ke musim di GH mana pun. `GET expenses/{id}/availability?split_season_ids[]=` mengembalikan `even_split` per sen, sen sisa ke musim pertama, jumlahnya selalu sama dengan sisa pembayaran. Laporan musim menghitung alokasi dari pembayaran GH lain |
| P1-3 | Biaya salah musim "dipindah" (7.7) | PATCH alokasi boleh mengganti `season_id`; musim lama dan baru harus terbuka. Riwayat tetap di audit. Sumber pembayaran tetap tidak bisa diganti |
| P1-4 | Panen hanya ditimbang (5.4) | `fruit_count` boleh `null` (key tetap wajib dikirim). Total buah menjadi `null` bila ada rincian tanpa jumlah; persentase capaian buah `null`. Target panen boleh kg saja; respons progres pengelola menambah `weight_achievement_percent` |
| P2-5 | Cash flow bulanan paling dicari, soft file (8.1, 8.5) | `GET reports/cash-flow?from=YYYY-MM&until=YYYY-MM[&greenhouse_id][&format=csv]`, bulan WIB, maksimal 24 bulan, pengelola saja. Uang keluar mencakup pembelian aset |
| P2-6 | Bandingkan antar GH/musim (8.3) | `GET season-reports/compare?season_ids[]=` (1–10 musim) memakai perhitungan pratinjau laporan musim, ditambah bobot panen dan sisa belum terjual |
| P2-7 | Total pemakaian bahan per musim (3.8) | `GET seasons/{id}/material-usage`: jumlah per bahan dan satuan dari kegiatan aktif yang selesai, tanpa konversi satuan |
| P2-8 | Penjualan dilacak lewat QRIS (6.1, 6.6) | Kolom opsional `payment_method` (cash/qris/transfer), `payment_reference`, `customer_type` (consumer/partner). Klien lama yang tidak mengirim key ini tidak menghapus nilai tersimpan |
| P3-9 | Musim selesai setelah buah habis terjual (2.5, 5.2) | Tutup musim ditolak `season_unsold_balance` (422) bila masih ada bobot layak/belum dinilai yang belum terjual dan catatan kosong. Dengan catatan (misalnya gagal jual), musim tertutup dan sisa itu terbaca sebagai gagal jual lewat `unsold_weight_kg` |
| P3-10 | Kegiatan dilewati tidak perlu alasan (3.7) | Alasan hanya wajib untuk nutrisi yang dilewati |
| P3-11 | Ukur sebelum dan sesudah inject (3.2) | `observations.measurement_moment`: `before_injection` atau `after_injection`, tanpa migration |
| P3-12 | Catatan disimpan selamanya, nota diarsipkan setelah satu siklus (7.8, 9.4) | Tidak ada perubahan kode: sistem memang tidak menghapus histori. Jadwal backup, RPO/RTO dan operator Q08 tetap belum dijawab, jadi `config/recovery.php` tidak diubah |

Migration baru: `2026_11_17_000021_align_field_practice` (additive: `harvest_items.fruit_count` nullable, tiga kolom `sales`). Rollback ditolak bila sudah ada rincian tanpa jumlah buah.

## Bukti

- `./scripts/test-backend.ps1` pada MySQL `inofarm_test`: guard 4 tes lulus; suite **167 tes, 4.249 assertion lulus** (162 sebelumnya + 5 di `FieldAlignmentTest`). Tiga tes lama disesuaikan dengan aturan baru: alokasi lintas GH, `fruit_count: null`, dilewati tanpa alasan.
- `scripts/check-finance-contracts.py` lulus; `scripts/check-finance-context.py` lulus. OpenAPI memuat empat path baru, parity route dua arah lulus.

## Batas

- **Android belum memakai fitur ini.** Sebelum klien mengirim `fruit_count: null` atau target kg saja, layar progres panen, Beranda dan target perlu menangani `null` (sekarang memakai `getLong`/`getInt`). Pemilih musim alokasi di Android masih terbatas pada GH pembayaran.
- Migration 21 belum diterapkan ke `inofarm_finance_local`; terapkan dengan cadangan seperti D1.
- Penjualan satu transaksi dari dua GH (jawaban 6.4) belum didukung; menunggu klarifikasi Inofarm.
- Target pengukuran belum membedakan sebelum/sesudah inject.
