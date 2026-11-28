# QA menyeluruh halaman pengelola di database asli (28 November 2026)

## Cara pengujian

| Hal | Nilai |
|---|---|
| APK | debug dari `main` `301fae6` (sesudah PR #89/#90), dibangun ulang 28 Nov |
| Perangkat | emulator `Inofarm_Botanical_API30`, mode terang (mode gelap dicek sekilas) |
| Backend | Laravel lokal `127.0.0.1:8000` lewat `scripts/start-backend-for-studio.ps1` |
| Database | `inofarm_finance_local` (database aplikasi, bukan `inofarm_test`), setiap simpan dicek langsung dengan SQL |
| Akun | `pengelola1@gmail.com` (akun pengelola asli) |
| Backup sebelum uji | `D:/inofarm-backup/inofarm_finance_local-20261128-115924.sql`, SHA-256 `cd62c6557a02f711632dc710b7c9fdfcca9d83d9156158fb5dad61b3568068e2` |

Bukan ponsel fisik, bukan TalkBack. Halaman pekerja tidak diuji (sesuai permintaan).

## Ringkasan

Hitungan inti benar: stok FIFO penjualan, total penjualan tiga tipe pembeli, biaya standar per GH, penyusutan garis lurus, NPV/IRR/PP/DPP, dan penjaga server (alokasi melebihi pembayaran, batal pembayaran yang masih teralokasi, tutup musim dengan sisa buah, email ganda) semuanya bekerja. Masalah terbesar ada di tiga tempat:

1. **Empat bug yang membuat fitur tidak bisa dipakai di data asli**: daftar penjualan musim selalu kosong, unduh Excel gagal, jadwal lama tidak bisa diubah, dan antrean macet permanen setelah pembatalan ditolak.
2. **Validasi terlambat (pola sistemik)**: form dan Ringkasan meloloskan isian yang pasti ditolak server. Pengelola baru tahu setelah form tertutup (chip "N belum terkirim"), atau tombol Simpan diam tanpa pesan.
3. **Bahasa dan format teknis**: tanggal ISO, desimal API mentah (`15000.00`, `2.500000 kg`), potongan UUID, enum Inggris, pesan exception Java/Laravel, serta PDF/CSV laporan yang isinya dump teknis.

## Yang berjalan baik

| Fitur | Bukti |
|---|---|
| Login, password salah, keluar, login ulang | Pesan "Email atau password tidak sesuai." |
| Rekap panen: tandai selesai lalu buka kembali | Layar + `planting_groups.harvest_completion` |
| Catat kegiatan gabungan | 5 baris satu `entry_batch_id`, `measurement_moment` sebelum/sesudah inject |
| Catat panen Layak/Cacat | `harvest_items` 5,5 kg/12 buah dan 0,5 kg tanpa buah |
| Penjualan Perorangan, Retail (pembeli baru, QRIS), Mitra (grade A/B + Cacat) | `sale_lines` benar; total Rp47.500 = 30.000 + 12.500 + 5.000 |
| FIFO stok | 3 kg diambil 2,5 kg dari panen 21 Nov + 0,5 kg dari panen baru; kembali utuh setelah batal |
| Petunjuk harga retail terakhir | "Terakhir ke Toko QA 28 Nov: Rp38.000/kg untuk 1 kg, 28 Nov" |
| Pengeluaran, koreksi, alokasi, aset, penyusutan, pembatalan berurutan | SQL `record_state` dan versi naik |
| Penjaga server | Alokasi > sisa, batal pembayaran teralokasi, tutup musim dengan 2,5 kg sisa, email ganda, satuan target, min > maks, tanggal akhir < mulai: semua ditolak |
| Analisis investasi | I0 10 jt, CF 4 jt × 3 th, 10%: NPV −Rp52.592,04, IRR 9,70%, PP 2,5 th, DPP belum kembali (cocok hitung manual) |
| Penyusutan | (100.000 − 20.000) / 2 = Rp40.000/tahun |
| Mulai semai | Validasi pindah tanam sebelum semai, varietas dan jumlah wajib |
| Nonaktifkan greenhouse | Daftar periksa jelas, memblok selama masih ada tanam |
| Harga perorangan | Validasi inline "Harga harus lebih dari nol." |
| Mode gelap | Beranda dan Keuangan terbaca |

## Bug yang menghentikan fitur (perbaiki dulu)

**Q1. Daftar "Penjualan Musim 1" selalu kosong di data asli.** Keuangan → Lihat semua penjualan Musim 1 menulis "Belum ada penjualan lunas di Musim 1 yang tersimpan di perangkat", padahal ringkasan di atasnya Rp555.500 · 16 kg · 4 transaksi. Tarik untuk memperbarui tidak menolong. Akar: `FinanceRoot.kt:1117-1123` memilih musim dari cache dengan `optInt("sequence") == n`, tetapi body `change_feed` semua musim yang dibuat sebelum migration 23 (keenam musim di database ini, mis. feed seq 20 musim "tes") tidak memuat `sequence`. Uji 26 Nov lolos karena fixture `inofarm_test` membuat musim sesudah migration. Perbaikan: terbitkan ulang feed musim dengan `sequence` atau ambil daftar dari server, plus tes dengan musim lama.

**Q2. Unduh Excel keuangan gagal.** "Excel belum bisa diunduh. Server belum dapat memproses permintaan." Log Laravel: `ErrorException: tempnam(): file created in the system's temporary directory` di `backend/app/Domain/Finance/SeasonWorkbook.php:21`. `php artisan serve` hanya meneruskan variabel lingkungan tertentu (`ServeCommand::$passthroughVariables`, tanpa `TEMP`/`TMP`), sehingga folder sementara di Windows tidak bisa ditulis dan notice diubah Laravel menjadi galat 500. Ini cara backend dijalankan proyek (`start-backend-for-studio.ps1`). Perbaikan: tulis ke `storage_path('app/tmp')` atau buat zip di memori.

**Q3. Jadwal lama tidak bisa diubah atau dinonaktifkan.** Form Ubah jadwal mengisi "Waktu rencana" dengan format server `2026-11-29T00:00:00.000000Z`, lalu server menolak dengan pesan mentah `Format Waktu rencana harus Y-m-d\TH:i:sP.` Hanya bisa disimpan setelah waktu diketik ulang. Jam yang tampil juga UTC (00:00 untuk jadwal 07:00 WIB), jadi pengelola bisa "membetulkan" ke jam yang salah.

**Q4. Antrean macet permanen setelah pembatalan ditolak.** Membatalkan pembayaran yang masih teralokasi: form tahu "Teralokasi Rp15.000" tetapi tetap mengizinkan dan menulis "Tersimpan". Server menolak (benar). Setelah alokasinya dibatalkan, permintaan batal itu tetap macet: "Kirim sekarang" tidak mengirim ulang, tombol Batalkan hilang, dan "Buang isian yang ditolak" tidak muncul karena kode tolak dianggap bisa diperbaiki (`HistoryScreen.kt:384-410`). "Perbaiki catatan yang ditolak" membuka form koreksi yang tidak bisa membatalkan. Chip "1 belum terkirim" menempel sampai data aplikasi dihapus.

### Status perbaikan Q1–Q4 (branch `qa-pengelola-tahap-1-3`, 28 November 2026)

| Bug | Perbaikan | Bukti |
|---|---|---|
| Q1 | `SeasonSequence.seasonsFor`: memakai `sequence` bila ada, bila tidak menurunkannya seperti backfill server (per GH, urut tanggal mulai lalu id); dipakai di `FinanceRoot.kt` | Emulator + DB asli: daftar Penjualan Musim 1 menampilkan penjualan 21 Nov Rp350.000 dari cache lama tanpa hapus data. JVM `SeasonSequenceTest` 3 tes |
| Q2 | `SeasonWorkbook` menulis berkas sementara di `storage/app/tmp` (sudah di-ignore git), bukan `sys_get_temp_dir()` | Emulator + Laravel `artisan serve`: `inofarm-keuangan-musim-1-20261128.xlsx` tersimpan, 3 sheet, GH2 Rp350.000 / 10 kg sama dengan SQL; folder tmp kosong lagi |
| Q3 | `AdminForm.wireTimestamp`: waktu jadwal/target/penugasan ditampilkan dan dikirim dalam WIB dengan offset `+07:00`; format salah memberi pesan Indonesia, bukan exception Java | Emulator: Ubah jadwal menampilkan `2026-11-29T07:00:00+07:00`, aktif/nonaktif tersimpan tanpa mengetik ulang (SQL versi 3 → 4, jam tetap 07:00). JVM `AdminFormTest` (tes lama yang mengunci keluaran `…Z` diperbaiki) |
| Q4 | `ReviewableRejections.mayReplace(kind, code, action)`: penolakan atas pembatalan tidak lagi dianggap bisa diperbaiki lewat form koreksi, jadi tombol "Buang isian yang ditolak" muncul; dipakai di `HistoryScreen` dan `LocalFinanceStore` | Emulator: pengeluaran Rp15.000 + alokasi → batalkan pengeluaran ditolak → "Buang isian yang ditolak" → antrean bersih → alokasi lalu pengeluaran dibatalkan (SQL cancelled). JVM `OperationRouteTest` |

Pemeriksaan: backend PHPUnit 266 tes / 5.856 assertion, JVM 231 tes lulus, lint 0 error. Belum: ponsel fisik. Belum diubah: form masih mengizinkan menekan Batalkan pada pembayaran yang teralokasi (server tetap menolak; kini bisa dibuang).

## Bug lain (P1)

| Kode | Temuan |
|---|---|
| Q5 | Riwayat menandai pengukuran Pekerja 1 (18 Nov 18.03 dan 18.54) "Belum dicek", padahal `activity_reviews` = valid dan Cek catatan menulis "Sesuai". |
| Q6 | Pengeluaran bertanggal 31 Des 2026 diterima form dan server. Setelah Simpan tidak ada pesan, catatan "hilang" dari Kas November; risiko dicatat dua kali. |
| Q7 | pH 15 di Catat kegiatan: Simpan diam, tidak ada pesan di mana pun. Galat `spray_groups` juga hanya tampil bila varietas > 1 (`EntryForm.kt:264-273`). |
| Q8 | Catat panen kosong: Simpan diam tanpa pesan. Catat kegiatan kosong: pesan ada tetapi di bawah lipatan, form tidak menggulir ke galat. |
| Q9 | Penjualan 9 kg dari sisa 8 kg lolos ke Ringkasan ("8 kg → kurang"), lalu Simpan diam. Harga Rp43/kg (salah ketik) diterima tanpa peringatan. |
| Q10 | Koreksi kegiatan membuka form lama satu jenis; untuk Nutrisi tampil EC/PPM/pH kosong, "Satuan: Pilihan tersimpan belum tersedia… Minta pengelola menambahkannya", jumlah `4.000000`. Tetap tersimpan benar (versi 2, 5 mL). |
| Q11 | Detail panen menulis "HSS 6 · HST belum berlaku" (dihitung dari tanggal semai), bertentangan dengan HSS 27 di halaman GH. Sisa bug B4 21 Nov. |
| Q12 | Tambah jadwal: tanggal salah memunculkan exception Java "Text '29/11/2026 07:00' could not be parsed at index 0". |
| Q13 | Kelola menghitung pengelola sebagai pekerja ("GH1 · 1 pekerja" padahal penugasannya hanya Pengelola 1). Penugasan role manager lama masih aktif. |
| Q14 | Unduh CSV evaluasi musim berisi dump JSON-path (`field,type,value,json_value,text_escaped`, `/metrics/L/exact_fraction/numerator`). PDF 12 halaman untuk 1 GH berisi UUID, SHA256, blok JSON, pecahan pembulatan, `Rp-4.150.000,00`, `0,07777778 ratio`. Laba, R/C, B/C ada di berkas walau sudah dihapus dari layar. |
| Q15 | Detail skenario investasi: `10000000.00 IDR`, `interpolation`, baris kosong `\n\n`. Hasil: `Rp-52.592,04`, `9,70102574%`, `2,50000000 tahun`, tanpa kesimpulan sederhana. |
| Q16 | Lampiran: server "belum diaktifkan", tetapi "Pilih berkas" tetap aktif dan membuka pemilih berkas. |

## Pola sistemik: validasi server tidak ada di form

Semua contoh di bawah lolos langkah isi dan Ringkasan, lalu baru ditolak server. Ada tiga akibat berbeda: masuk antrean "Perlu diperbaiki" (pengelola mengira sudah tersimpan), kembali ke langkah 1 dengan pesan di ujung bawah form, atau Simpan diam.

| Form | Isian | Akibat |
|---|---|---|
| Alokasi biaya | Rp20.000 dari sisa Rp15.000 (angka sisa tertulis di form) | Antrean |
| Penjualan, pembeli baru | WhatsApp `abc12` | Antrean; saat diperbaiki kolom tidak bertanda |
| Pembatalan pembayaran | Masih teralokasi | Antrean macet (Q4) |
| Aset | Harga Rp120.000 > pembayaran Rp100.000 | Kembali ke langkah 1, pesan di bawah |
| Penyusutan | Rp50.000 > anggaran Rp40.000 | Kembali ke langkah 1 tanpa alasan yang terlihat |
| Target | EC dengan satuan ppm; min > maks | Ditolak satu per satu |
| Musim | Tanggal akhir sebelum mulai | Pesan gabungan generik |
| Akun | Email `bukan-email` | Ditolak server |
| Tutup musim | Sisa 2,5 kg tanpa catatan | Kolom tetap berlabel "(opsional)" |
| Biaya standar | Baris "wajib" dikosongkan | Boleh disimpan; TC 5 GH jadi "Belum lengkap" |

Saran: cerminkan aturan server di form (sisa alokasi, stok, anggaran, satuan dari parameter, rentang pH, format WA/email, tanggal tidak di masa depan), gulir ke galat pertama, dan jangan izinkan Simpan di Ringkasan bila ada galat yang sudah diketahui.

## Dari sisi pengelola sehari-hari

**Alur**
- Satu isian Catat kegiatan menjadi 4-5 baris di Beranda dan Riwayat. Dua baris "Pengukuran manual" identik (tidak menyebut sebelum/sesudah inject atau nilai EC). Membatalkan satu isian perlu 5 kali batal.
- Setelah Simpan, aplikasi tidak memberi pesan sukses dan sering berpindah ke tempat lain (penjualan berakhir di Riwayat › Kegiatan).
- Riwayat menyimpan filter per chip; chip Panen tersangkut filter "21 Nov saja" yang tidak dipasang pengelola, sehingga catatan hari ini tidak terlihat.
- "Lihat penjualan lunas" di halaman GH2 membuka Keuangan seluruh kebun, bukan penjualan GH2.
- Pilihan greenhouse di Biaya nyata hilang setiap pindah tab; semua item Alat keuangan jadi abu-abu.
- Buat evaluasi musim masih meminta pengelola mengetik "jumlah catatan belum terkirim" (aplikasi sendiri tahu) dan memilih ulang musim.
- Jadwal hanya satu waktu, tanpa ulangan harian; waktu dan target diketik dalam format `YYYY-MM-DDTHH:mm:ss+07:00`.
- Analisis investasi meminta kas neto per tahun diketik manual, tanpa bantuan dari penjualan/biaya yang sudah tercatat.
- Daftar pembeli tidak bisa menambah, mengubah atau menonaktifkan pembeli secara manual.
- Grade mitra berurutan tetap (A, lalu B, lalu C); tidak bisa langsung memilih B.
- QRIS/Transfer tanpa isian nomor referensi (kolom `payment_reference` sudah ada di backend).
- Kategori pengeluaran tidak bisa ditambah dari form, dan duplikat beda huruf besar diterima ("audit 18 nov" di samping "Audit 18 Nov").

**Kejelasan angka dan status**
- Dua arti "pengeluaran": Kas bulan ini = pembayaran aktual (Rp10.000), Keuangan musim = biaya standar (Rp4,5 jt/GH), tanpa penjelasan.
- Periode Musim 1 "1 Okt 2026 sampai sekarang" berasal dari GH uji "UJI Pemulihan WR 09 Nov", yang juga menambah TC kebun Rp4,5 jt. Data uji lama masih aktif (GH, varietas "www", "sh", "Varietas UJI WR", kategori "Audit 18 Nov").
- "Jadwal aktif" berbeda arti antara Kelola (1 aktif, sudah lewat) dan halaman GH ("Belum ada jadwal aktif").
- Grafik penjualan membulatkan Rp350.000 menjadi "0,4 jt"; grafik pola panen tanpa angka.
- "Terakhir: HSS …" di form kegiatan ikut berubah saat mengetik.
- Detail penjualan: label "Pembayaran" kosong (tanggal lunas dan cara bayar tidak tampil), plus 3 baris "tes1 · Layak" tanpa angka.

**Bahasa dan format** (contoh yang masih tampil)
Tanggal ISO di kolom, detail aset, jadwal, filter, ringkasan investasi; `15000.00` di form koreksi; `2.500000 kg tersedia · versi saldo 2`; `Rp100.000 · · ae867d4b`; `Referensi pemakaian: 32a05ccc`; UUID di sheet antrean; "Revisi ke-1 di server", "Versi kelompok 1 · pembanding 1", "Seluruh greenhouse", "akhir tidak termasuk", "pembulatan kumulatif", "horizon", "interval solver", "tahun0", "snapshot", "agregasi", "sesuai kontrak", "Pengelola Pengelola 1"; jam 12.15 dan 12:04 bercampur; "Isian saya sebelum diselesaikan 1"; pesan ganda "Catatan telah dibatalkan." + "Tersimpan. Terkirim otomatis saat ada internet."

**Tampilan**
Chip "Sebelum/Sesudah inject" terpilih hampir sama dengan tidak terpilih; tab detail GH terpotong ("Pengaturan" perlu digeser); chip Aset/Penyusutan di Riwayat keuangan tersembunyi di geser horizontal; form Evaluasi musim, Evaluasi investasi dan Mulai semai tidak memakai kerangka form yang sama (X Mulai semai menutup tanpa konfirmasi); blok perbandingan EC/pH/PPM "belum tersedia" (9 baris) tampil di detail Nutrisi dan Cek catatan; "Perlu perhatian" Beranda berisi 4 kartu "Belum ada pengukuran hari ini" yang sama; daftar bahan tidak urut abjad; pemilih GH tidak urut dan memuat GH nonaktif.

## Data uji di database asli dan pembersihannya

| Data | Status akhir |
|---|---|
| Catat kegiatan GH2 28 Nov (batch `80ece66d`, 5 baris) | Dibatalkan |
| Panen GH2 28 Nov `648452fa` | Dibatalkan |
| Penjualan perorangan `05253d38`, retail `6a110737`, mitra `f6056816` | Dibatalkan; stok kembali (`sold_weight_kg` 10 dan 0) |
| Pengeluaran Rp15.000 `d317f8b9`, alokasi `82ca8210` | Dibatalkan |
| Pembayaran aset `ae867d4b`, aset `3cac56eb`, penyusutan `74e89c39` | Dibatalkan |
| Skenario investasi `045f2499` | Dibatalkan |
| Jadwal GH2 29 Nov `479f4c4c` | Dinonaktifkan |
| Kategori "audit 18 nov" `4a4d57fe` | Dinonaktifkan |
| Koreksi AB Mix GH2 21 Nov (4 → 5 → 4 mL) | Nilai kembali 4 mL, versi 3 |
| Tandai selesai lalu buka kembali panen GH2 | Kembali "Panen bertahap" |
| Pembeli retail "Toko QA 28 Nov" `cac0070d` | **Masih aktif**: aplikasi tidak menyediakan cara menonaktifkan pembeli |

Catatan yang dibatalkan tetap ada di riwayat dan audit (sesuai aturan tanpa hard delete). Tidak dibuat: laporan evaluasi/investasi terbit (immutable), akun baru, target, harga perorangan, penutupan musim, perubahan biaya standar, reset password.

## Batas pengujian

- Impor pembeli (.xlsx/.csv) dan unggah lampiran tidak bisa diuji tuntas: pemilih berkas Android di emulator tidak merespons ketukan otomatis.
- Wizard Tambah greenhouse, salin master antar GH, reset password akun lain, dan alur Cek catatan untuk catatan pekerja baru tidak dijalankan (tidak ada catatan pekerja yang belum dicek, dan pengujian pekerja di luar cakupan).
- Emulator sempat mati di tengah sesi dan kembali ke snapshot lama; data aplikasi dihapus dan login ulang. Database tidak terdampak.
- Belum ponsel fisik, TalkBack, skala huruf besar.

## Urutan perbaikan yang disarankan

1. Q1 (daftar penjualan kosong) dan Q2 (Excel) karena keduanya fitur keuangan utama yang mati di data asli.
2. Q4 (antrean macet) dan Q3 (ubah jadwal), lalu Q5 (status cek berbeda).
3. Validasi di form untuk semua baris tabel pola sistemik, termasuk Q6–Q9.
4. PDF/CSV laporan versi pengelola (Q14) dan tampilan investasi (Q15).
5. Bahasa/format: satu formatter tanggal, uang, kg dan jam untuk semua layar; tanpa UUID dan enum.
6. Alur: kelompokkan isian per `entry_batch_id` (tampil dan batal), pesan sukses setelah Simpan, ingat pilihan GH, filter Riwayat tidak terbawa diam-diam.
7. Kebersihan data: putuskan nasib GH/varietas/kategori uji, dan penugasan role manager lama.

## Tahap 1–3 sesudah QA (branch `qa-pengelola-tahap-1-3`, 28 November 2026)

Keputusan pengguna 28 Nov: form pengelola wajib menunggu server saat Simpan (antrean offline tetap cadangan), tanggal ke belakang bebas tetapi tanggal sesudah hari ini (WIB) ditolak, dan satu isian tetap bisa dibatalkan per bagian ditambah "Batalkan seluruh isian".

| Tahap | Perubahan | Bukti |
|---|---|---|
| 1 | `ManagerDelivery`: kegiatan, isian gabungan, panen, penjualan, pengeluaran/alokasi, aset/penyusutan, pembatalan di Riwayat, skenario dan laporan menjalankan sinkronisasi lalu menunggu jawaban server. Diterima: form tertutup dan snackbar "Tersimpan di server."; ditolak dan bisa diperbaiki: form dibuka lagi dengan alasan; tanpa koneksi: antrean seperti dulu | Emulator + DB asli: simpan pengeluaran → snackbar "Tersimpan di server.", SQL `active`; pembatalan → "Catatan dibatalkan." |
| 1 | `FarmFormFrame(errors)`: bar "Belum bisa disimpan: …" di atas Batal/Simpan dan gulir ke kolom galat pertama, di semua form domain | Emulator: pH 15 → galat di kolom pH dan di bar |
| 1 | Aturan server di form: pemetaan galat bagian `EntryDraft` (ID bagian tetap), stok penjualan di langkah 1, format WhatsApp (sama dengan `WhatsappNumber` server), `FinanceLimits` (sisa alokasi, harga aset ≤ pembayaran, penyusutan ≤ anggaran), form admin menjalankan `payload()` sebelum Ringkasan, satuan target otomatis dari parameter, minimum ≤ maksimum, pH 0–14 | JVM `QaHardeningTest` |
| 1 | Tanggal sesudah hari ini ditolak: server `OperationInput::notFuture` (kegiatan, panen, pembayaran, lunas) dan form (`ManualTime.afterToday`) | PHPUnit (2 tes disesuaikan: tanggal masa depan kini 422) |
| 1 | Riwayat membaca keputusan cek untuk pengelola juga | Emulator: pengukuran Pekerja 1 tampil "Sesuai" |
| 2 | `FarmFormat` (tanggal, jam WIB, Rp dengan −, kg, angka isian) dipakai di Rekap panen, Kelola, aset, pengeluaran, panen, filter, investasi, penyusutan; isian koreksi tidak lagi `15000.00`/`4.000000`; UUID dan "Revisi ke-n di server" dihapus dari layar | JVM `QaHardeningTest`; emulator Kelola/Riwayat |
| 2 | Waktu jadwal/target/penugasan dipilih lewat kalender + jam (WIB) | Emulator: form Tambah jadwal |
| 2 | Investasi: Rp/persen/tahun dibulatkan untuk dibaca, cara hitung dalam Bahasa Indonesia, kalimat kesimpulan NPV, asumsi backend tanpa jargon | Kode, PHPUnit |
| 2 | PDF laporan: halaman 1 ringkasan (GH, musim/skenario, periode, status, FC/VC/TC/penjualan/kg atau NPV/IRR/PP/DPP + kesimpulan), seluruh data tersimpan di "Lampiran untuk pemeriksa". CSV tetap data audit lengkap (kontrak), tombolnya kini "Unduh data audit (CSV)" | PDF dirender dari laporan GH2 di DB asli (baca saja): halaman 1 bersih; `FinanceReportExportTest` lulus |
| 3 | Riwayat dan Beranda: bagian satu isian menjadi satu kartu "Isian kegiatan · N bagian" yang bisa dibuka; tiap bagian tetap bisa dibatalkan, plus "Batalkan seluruh isian". `manager-home.recent` mengirim `entry_batch_id` dan `sections` (OpenAPI ditambah) | Emulator + DB asli: isian 2 bagian dibatalkan sekaligus, SQL keduanya `cancelled` |
| 3 | Snackbar hasil simpan pengelola, tombol "Hapus filter, tampilkan semua" saat filter aktif, GH Biaya nyata diingat selama aplikasi berjalan, judul baris pengukuran sebelum/sesudah inject + nilai EC/PPM/pH | Emulator |
| Tambahan | Hitungan "N pekerja" di Kelola hanya akun pekerja; detail panen tidak lagi menampilkan umur hitungan (HSS 6); label catatan tutup musim; pesan sisa buah memakai koma | Emulator: GH1 0 pekerja, GH2 1 pekerja |

Pemeriksaan akhir: backend PHPUnit 266 tes / 5.858 assertion, JVM 238 tes, lint 0 error, kontrak lulus, context check lulus. Data QA tambahan (pengeluaran Rp1.000 ×3, isian kegiatan QA, jadwal) sudah dibatalkan/dinonaktifkan. Belum: ponsel fisik, TalkBack, instrumentasi `FinanceEntryUiTest` penuh (teks tombol CSV dan pesan simpan berubah), uji penolakan server yang lolos aturan form (jalur "form dibuka lagi" baru diuji lewat kode).
