# Rancangan: Keuangan per musim dan pencatatan penjualan (25 November 2026)

Status: **menunggu persetujuan pengguna**. Mockup yang sudah disetujui ada di kanvas privat milik pengguna "Inofarm Keuangan", di halaman **Per musim · 25 Nov** (4 layar) dan **Catat penjualan · 25 Nov** (3 layar). Halaman "Versi 21 Nov" adalah histori.

## 1. Maksud

Inofarm ingin aplikasi mencatat angka keuangan dengan jelas, tetapi tidak menghitung rasio untuk mereka. Mereka menghitung R/C, BEP dan B/C sendiri, jadi aplikasi cukup menyajikan FC, VC, TC dan penjualan apa adanya, per musim, dan bisa diunduh ke Excel.

Penjualan dicatat seperti cara Inofarm menjual: ke siapa (Perorangan, Retail, Mitra), buah apa (varietas, Layak atau Cacat), berapa kg, berapa buah, berapa harga per kg. Stok panen harus berkurang dengan benar tanpa pengelola memilih catatan panen satu per satu.

## 2. Keputusan pengguna (25 November 2026)

| # | Keputusan |
|---|---|
| K1 | FC/VC standar per Musim ke-n berlaku **utuh untuk setiap GH** (VC 5 juta, 4 GH: tiap GH 5 juta). TC kebun = TC per GH × jumlah GH yang punya musim itu. Ini sudah perilaku kode sekarang dan tidak berubah. |
| K2 | Keuangan hanya **FC, VC, TC**. Laba, R/C, B/C, BEP harga, BEP produksi dan label "Penerimaan (TR)" **dihapus** dari aplikasi, atas permintaan Inofarm, karena mereka menghitung sendiri. |
| K3 | Tab Keuangan disusun **per Musim ke-n** (pendekatan A, mockup halaman Per musim). |
| K4 | Tiga tipe pembeli: **Perorangan** (pembeli akhir, harga per kg tetap berapa pun beratnya), **Retail** (toko/pedagang, makin banyak makin murah, **harga ditawar per transaksi**), **Mitra** (tengkulak, menilai grade A/B/C, harga ditawar). |
| K5 | Satu penjualan bisa berisi 2 buah atau lebih (varietas + Layak/Cacat), masing-masing dengan bobot kg, jumlah buah opsional dan harga per kg (mockup halaman Catat penjualan). |

## 3. Tujuan, bukan tujuan, anti-tujuan

**Tujuan**
- Pengelola melihat FC, VC, TC per GH dan total kebun untuk satu musim, penjualan musim itu (per minggu, per GH, per tipe pembeli), dan perbandingan antar musim.
- Pengelola mengunduh Excel berisi angka yang sama persis dengan layar.
- Pengelola mencatat penjualan per varietas + kualitas, dan server mengurangi stok dari panen paling lama lebih dulu.
- Retail menjadi tipe pembeli penuh: di form, daftar pembeli, impor, grafik dan Excel.

**Bukan tujuan**
- Tabel tingkat harga retail otomatis (harga ditawar, K4).
- Piutang, bayar belakangan, titip jual (tetap di luar produk).
- Satu transaksi dari beberapa GH (tetap satu transaksi = satu GH).
- Mengubah laporan musim atau investasi yang sudah terbit (snapshot immutable).
- Mengubah cara biaya standar diisi (sudah benar sejak PR #88).
- Mengubah modul investasi (skenario, NPV/IRR/PP/DPP) dan pencatatan biaya nyata di balik ikon mata.

**Anti-tujuan** (gagal walau "jalan")
- Angka di Excel berbeda dari angka di layar.
- Stok panen bisa minus, atau satu kg terjual dua kali saat dua pengelola menyimpan bersamaan.
- Antrean offline berisi penjualan bentuk lama gagal terkirim sesudah pembaruan.
- Harga contoh ikut menjadi data bawaan produksi.
- Rasio yang dihapus muncul lagi di layar lain.

## 4. Batasan

- Aturan proyek di `CLAUDE.md` dan `AGENTS.md`: migration additive, tidak ada hard delete histori, desimal sebagai string di JSON, tes hanya di MySQL `inofarm_test`, `inofarm_finance_local` hanya dimigrasi atas permintaan eksplisit dan dengan backup.
- Tanpa dependency baru. Server membuat .xlsx dengan ekstensi PHP `zip` dan `xmlwriter` (keduanya terpasang di `.tools/php`).
- Pengelola: form hanya Batal / Simpan → Ringkasan (keputusan 17 November).
- Emulator yang dipakai hanya `Inofarm_Botanical_API30`, mode terang.

## 5. Pendekatan yang dipertimbangkan

**Susunan Keuangan** (dipilih pengguna: A)
- A. Per musim: biaya standar memang per musim dan penjualan sudah terikat ke musim lewat panennya. **Dipilih.**
- B. Per bulan ala buku kas: biaya standar tidak bertanggal, jadi pengeluaran bulanan hanya bisa dari biaya nyata, padahal biaya nyata tidak dijumlahkan. Ditolak.
- C. Per GH: biaya semua GH identik, jadi halamannya nyaris sama. Dijadikan rincian di dalam A.

**Mengurangi stok panen** (keputusan saya)
- 1. Server membagi setiap baris penjualan ke catatan panen secara FIFO, dengan tabel baris penjualan baru. Stok dijaga di satu transaksi yang mengunci baris panen. **Dipilih.**
- 2. Android memecah baris ke catatan panen memakai saldo di cache. Ditolak: saldo cache bisa usang, dan jumlah buah tidak bisa dipecah per catatan panen.
- 3. Tetap memilih catatan panen seperti sekarang. Ditolak: tidak sesuai cara Inofarm berpikir, dan nama varietas tidak terlihat.

**Membuat Excel** (keputusan saya)
- Server membuat .xlsx. Angkanya satu sumber dengan layar, dan Android cukup mengunduh lewat alur unduh laporan yang sudah ada. **Dipilih.**
- Android menulis .xlsx dari cache. Ditolak: risiko angka berbeda dari server.

## 6. Rancangan

### 6.1 Data: migration 24 (additive)

| Perubahan | Isi |
|---|---|
| `customers.type` | CHECK `customer_values` diganti agar menerima `retail` di samping `consumer` dan `partner`. Unik `(type, whatsapp)` tetap. |
| `sales.customer_type` | Kolom tanpa CHECK; validasi server menerima `retail`. |
| Tabel baru `sale_lines` | `id` UUID dari klien, `sale_id` FK, `planting_group_id` FK, `grade_id` FK, `grade_name` (snapshot), `variety_name` (snapshot), `buyer_grade` CHAR(1) null (A/B/C), `quantity_kg` DECIMAL(20,6) > 0, `fruit_count` INT UNSIGNED null, `price_per_kg` DECIMAL(20,6) ≥ 0, `exact_amount` DECIMAL(40,12) ≥ 0, `active`, timestamps. |
| `sale_items.sale_line_id` | FK nullable ke `sale_lines`. `sale_items` kini menjadi **alokasi** baris ke satu catatan panen. Saldo `harvest_items.sold_weight_kg` tetap dihitung dari alokasi ini. |
| Backfill | Setiap `sale_items` yang ada mendapat satu `sale_lines` dengan id turunan deterministik (UUID v5 dari id sale_item), `fruit_count` null, dan nilai yang sama. Total penjualan lama tidak berubah. |
| Tabel baru `consumer_prices` | Harga perorangan untuk seluruh kebun: `id`, `variety_key` (nama varietas, huruf kecil dan dirapikan), `variety_name`, `quality` (`saleable`/`defective`), `price_per_kg`, `version`, `record_state`, timestamps, unik `(variety_key, quality)` untuk yang aktif. Tidak punya `greenhouse_id`, jadi seperti lembar biaya: tidak masuk `change_feed` dan dibaca lewat GET. |

`down()` menolak, seperti migration 23.

### 6.2 Server

**Penjualan** (`SaleController`, `SaleService`, `SaleMath`)
- Payload baru `lines[]`: `id`, `planting_group_id`, `grade_id`, `quantity_kg`, `fruit_count` (opsional, bulat ≥ 1), `price_per_kg`, `buyer_grade` (opsional). Maksimal 30 baris.
- Kunci unik per transaksi: `(planting_group_id, grade_id, buyer_grade)`. Dua baris Golden Layak hanya boleh pada mitra dengan grade berbeda.
- Semua kelompok tanam harus berada di musim penjualan. Musim harus terbuka (aturan lama tetap berlaku).
- **FIFO:** untuk setiap baris, server mengunci `harvest_items` aktif di kelompok itu dengan `grade_id` sama, dari panen aktif, urut `harvests.occurred_at` lalu `id`, dan mengisi alokasi sampai bobot baris terpenuhi. Kalau kurang, respons 422 `harvest_exceeded` dengan sisa yang tersedia per baris. Id alokasi = UUID v5 dari `(line_id, harvest_item_id)` dan diklaim di `EntityIdentity`.
- **Koreksi:** semua alokasi lama transaksi itu dilepas, lalu baris dialokasikan ulang dalam transaksi yang sama. Alasan tetap mengikuti aturan koreksi yang sekarang.
- **Bentuk lama:** `items[]` dengan `harvest_item_id` tetap diterima, supaya antrean offline yang dibuat sebelum pembaruan tetap terkirim. Setiap item menjadi satu baris dengan alokasi tunggal ke catatan panen itu.
- `customer_type` dan `customer.type` menerima `retail`. `buyer_grade` tetap hanya untuk `partner`.
- Body penjualan (respons, audit, feed) memuat `lines[]` beserta `variety_name`, `grade_name`, `fruit_count` dan `allocations[]` (harvest_item_id, harvest_id, kg). `items[]` tetap dikirim untuk klien lama.

**Ringkasan musim** `GET reports/season-overview?sequence=n` (pengelola saja). Menggantikan `reports/profit-summary`, yang dihapus beserta tesnya.
- `sequences[]`: setiap Musim ke-n yang ada, dengan `greenhouse_count`, `fc`, `vc`, `tc` (kebun), `sales_amount`, `sales_kg`, `status` (berjalan bila ada musim n yang terbuka).
- Untuk n yang diminta:
  - `date_range` (mulai paling awal sampai selesai paling akhir, atau hari ini bila masih berjalan).
  - `cost_sheet` per GH (baris dan total) dan total kebun. FC/VC null bila ada baris kosong.
  - `sales` total, kg, jumlah transaksi, `by_type` (consumer, retail, partner, unspecified).
  - `by_greenhouse[]` (Rp, kg, transaksi).
  - `weekly[]` (minggu WIB mulai Senin, seperti `bucketStart` yang ada, per tipe pembeli).
- Penjualan dihitung dari `sales` aktif yang `season_id`-nya musim dengan sequence n di GH aktif, bukan dari rentang tanggal.

**Excel** `GET reports/season-export?sequence=n|all&parts=summary,costs,sales` → `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- Sheet sesuai mockup: **Ringkasan per GH** (Musim, GH, FC, VC, TC, Penjualan, Terjual kg, Transaksi, baris Total), **Rincian biaya** (Musim, Kelas, Baris, Wajib, Per GH, Jumlah GH, Total kebun), **Daftar penjualan** (Tanggal, GH, Pembeli, Tipe, Varietas, Kualitas, Grade, kg, buah, Harga/kg, Total).
- Angka ditulis sebagai sel angka, bukan teks. Tidak ada rumus.
- Nama file `inofarm-keuangan-musim-<n|semua>-<tanggal>.xlsx`. Angkanya langsung dari data saat itu, bukan snapshot, dan baris judul menyebut waktu unduh.
- Dibuat dari data yang sama dengan `season-overview` (satu kelas perhitungan).

**Harga perorangan** `consumer-prices`: GET daftar, POST buat, PATCH ubah atau nonaktifkan, dengan envelope operasi seperti master lain. Hanya pengelola; pekerja 403.

**Grafik pemasukan lama** `reports/income`: `by_type` mendapat `retail`.

### 6.3 Android

**Tab Keuangan** (`feature/financehome`, mockup "Keuangan per musim")
- Chip musim dari `sequences`, dengan musim berjalan sebagai bawaan.
- Kartu TC kebun beserta FC/VC dan angka per GH.
- Rincian biaya per GH, dengan Ubah ke Biaya standar.
- Penjualan musim: grafik per minggu bertumpuk Mitra/Retail/Perorangan dalam tiga tingkat jingga, ketuk batang untuk rincian, lalu per GH dan tombol "Lihat semua penjualan".
- Grafik antar musim: FC+VC bertumpuk berdampingan dengan penjualan.
- Ikon mata untuk biaya nyata tetap ada. Tombol Excel membuka sheet unduh.
- **Dihapus:** `ProfitCard`, tile dan layar **Ringkasan investor** (isinya per GH per musim pindah ke layar ini dan ke Excel), serta pemilih Minggu/Bulan/Tahun.

**Daftar penjualan satu musim**: layar baru berisi cari, filter tipe (Semua/Perorangan/Retail/Mitra) dan GH, dikelompokkan per tanggal. Datanya dari cache sales hasil sync. Ringkasannya dari `season-overview`.

**Unduh Excel**: sheet pilih musim dan bagian, lalu file disimpan lewat pemilih file sistem (`CreateDocument`) dan diunduh seperti ekspor laporan yang ada.

**Form Catat penjualan** (`feature/sales`, mockup "Catat penjualan")
- "Ke siapa?" berisi Perorangan / Retail / Mitra dan **wajib** dipilih. Penjualan lama tanpa tipe tampil sebagai "Tanpa tipe".
- Pembeli: dipilih dari daftar sesuai tipe, atau diisi baru. **Wajib untuk Retail dan Mitra, opsional untuk Perorangan.**
- Konteks GH dan musim: musim terbuka GH itu dipilih otomatis, dan GH bisa diganti.
- Kartu buah per varietas + Layak/Cacat. Sisanya dihitung dari saldo `harvest_items` di cache, dijumlah per kelompok dan grade. Isiannya bobot, jumlah buah opsional dan harga per kg. Ada "Sesudah dijual sisa …" dan "Tambah buah lain". Mitra memecah per grade.
- Harga:
  - Perorangan: diisi dari `consumer_prices` (cocok berdasarkan nama varietas + kualitas) dan masih bisa diubah.
  - Retail: kosong, dengan petunjuk harga terakhir ke pembeli itu untuk varietas yang sama (dari cache sales).
  - Mitra: kosong.
- Pembayaran (tanggal, jam WIB, Tunai/Transfer/QRIS) dan centang lunas. Total di bawah. Ringkasan langkah 2 dari 2 menampilkan stok sesudah disimpan beserta pratinjau FIFO dari cache (server yang menentukan).

**Pembeli**: daftar, impor dan pemilih mendukung tipe Retail.

**Kelola → Data master → Harga perorangan**: layar daftar dan ubah, mengikuti mockup.

**Laporan musim yang sudah ada** (`feature/reports/SeasonReportScreen`): layar hanya menampilkan FC, VC, TC, penjualan dan kg. Nilai rasio di snapshot lama tetap tersimpan, tetapi tidak ditampilkan.

### 6.4 Kontrak dan dokumen

OpenAPI (route baru, `retail`, `lines`, route `profit-summary` dihapus), schema JSON operasi sale (`lines` dan legacy `items`) dan operasi harga perorangan beserta fixture, `contracts/v2/CUSTOMERS.md`, catatan penjualan dan ringkasan musim, `check-finance-contracts.py`. Sesudah selesai: dokumen bukti `docs/context/` dan paragraf di `CLAUDE.md`.

## 7. Pengujian dan bukti yang diharapkan

- **PHPUnit (`inofarm_test`):**
  - FIFO lintas tiga panen, `harvest_exceeded` dengan sisa, dan koreksi yang mengalokasikan ulang.
  - Dua pengelola menjual stok yang sama bersamaan (skrip konkurensi seperti `test-finance-concurrency.php`).
  - Payload `items` lama tetap diterima.
  - Retail di sale dan customers, `buyer_grade` hanya mitra, `fruit_count` opsional, baris ganda ditolak.
  - Backfill migration menjaga total.
  - `season-overview` (angka contoh mockup sebagai oracle), Excel yang bisa dibuka (zip dan XML dibaca balik di tes, sel angka bertipe angka).
  - CRUD `consumer_prices` dan 403 untuk pekerja, `profit-summary` sudah tidak ada.
- **JVM:** model grafik tiga tipe, agregasi stok per varietas + kualitas, pratinjau FIFO, petunjuk harga retail, isian harga perorangan, parsing body sale dengan `lines` dan tanpa `lines`.
- **Emulator** `Inofarm_Botanical_API30` terhadap Laravel nyata + `inofarm_test`: tiga tipe penjualan, Ringkasan, stok berkurang, Keuangan per musim, unduh Excel lalu dibuka.
- **`inofarm_finance_local`:** migration 24 hanya bila pengguna meminta, dengan backup dan bukti sebelum/sesudah seperti 21 November.

## 8. Diserahkan ke pembangun

Nama kelas dan berkas, susunan composable, bentuk persis JSON di luar field yang disebut, cara menulis XML xlsx, urutan fase, dan test tag. Pembangun memutuskan sendiri selama perilaku dan aturan di atas terpenuhi.

## 9. Keputusan yang saya ambil (tolong dicek)

1. Tabel baris penjualan + alokasi FIFO di server, dengan bentuk `items` lama tetap diterima.
2. Harga perorangan berlaku untuk seluruh kebun per nama varietas + kualitas, bukan per GH.
3. Pembeli wajib untuk Retail dan Mitra, opsional untuk Perorangan. Tipe pembeli wajib untuk penjualan baru.
4. Ringkasan investor dihapus karena isinya laba dan R/C. Angka per GH per musim ada di Keuangan dan Excel.
5. `reports/profit-summary` dihapus dan diganti `reports/season-overview`.
6. Excel dibuat server dari data saat itu, bukan snapshot laporan.

## 10. Pertanyaan untuk pengguna

1. **Branch.** Pekerjaan Kelola di `perbaikan-bug-audit-pengelola` belum di-commit (57 berkas) dan ikut mengubah `FinanceOverviewScreen.kt`, `InvestorSummaryScreen.kt`, `StandardCostScreen.kt`, `SaleDraft.kt` dan `SaleEntryScreen.kt`. Usulan: commit dan merge pekerjaan Kelola dulu, lalu branch baru `keuangan-per-musim-penjualan` dibuat dari main yang baru.
2. **PDF/CSV laporan musim.** Unduhan laporan musim yang sudah terbit masih mencetak laba, R/C dan BEP dari snapshot. Pilihannya: dibiarkan (snapshot tidak diubah), atau pencetak baru tidak menampilkan baris itu untuk unduhan berikutnya.
