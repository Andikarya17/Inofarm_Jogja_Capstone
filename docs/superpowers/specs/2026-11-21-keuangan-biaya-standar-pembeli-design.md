# Desain: Keuangan pengelola dengan biaya standar, grafik pemasukan dan pembeli

Tanggal: 21 November 2026. Status: **menunggu ACC pengguna**. Sumber: masukan Inofarm yang disampaikan pengguna pada 20 November 2026 (tabel Fix Cost, Variabel Cost, Total Cost; tiga tipe pembeli), referensi UI Finora, dan diskusi di sesi yang sama. Mockup yang disetujui sebagai arah: kanvas **Inofarm Keuangan** (https://claude.ai/artifact/PskzgVNvPFBhH7zvRdwsQW, 10 layar).

## Maksud

Inofarm menghitung biaya dengan **angka standar per greenhouse per musim**, sama untuk semua GH, seperti di Excel mereka. Aplikasi sekarang menghitung biaya dari pembayaran nyata yang dialokasikan ke musim, sehingga laba di aplikasi tidak pernah sama dengan cara Inofarm berpikir. Selain itu mereka ingin:

- melihat pemasukan dalam grafik per minggu, bulan dan tahun;
- menunjukkan ringkasan laba ke calon investor;
- mencatat siapa pembelinya: perorangan (dengan daftar pembeli yang bisa diimpor dari Excel) dan mitra/tengkulak yang menilai grade A, B, C sendiri.

## Tujuan

1. **Biaya standar per musim, sama untuk setiap GH.** Tidak ada isian biaya per GH (penegasan pengguna 21 November 2026): satu angka per baris berlaku untuk tiap GH yang sedang menjalani musim itu. Biaya tetap (FC) dengan tiga baris wajib: Biaya penyusutan, Sewa tanah, Tenaga kerja. Biaya variabel (VC) dengan empat baris wajib: Cocopeat/rockwool, Nutrisi AB Mix, Nutrisi tambahan dan pestisida, Benih. Nama baris wajib terkunci, jumlahnya bisa diubah. Pengelola boleh menambah, mengubah dan menghapus baris tambahan di FC dan VC. TC = jumlah FC + jumlah VC, dihitung, tidak diketik.
2. **Laba musim memakai biaya standar.** Laba = TR − TC, R/C, BEP harga dan BEP produksi dihitung dari biaya standar musim itu dan penjualan lunas musim itu.
3. **Layar Keuangan baru** (tab Keuangan pengelola):
   - grafik batang pemasukan per minggu, bulan atau tahun, bertumpuk Mitra dan Perorangan;
   - batang bisa diketuk untuk melihat rinciannya;
   - kartu per tipe pembeli, kartu laba musim berjalan, dan pintasan ke Catat penjualan, Biaya standar dan Ringkasan investor.
4. **Ikon mata** untuk menyembunyikan atau menampilkan pencatatan biaya nyata (Catat pengeluaran, Alokasi, Aset, Penyusutan). Pilihan disimpan di perangkat. Data lama tidak dihapus.
5. **Ringkasan investor**: laba tercatat lintas musim, penerimaan dan biaya per musim, tabel per GH, dan bagian kelayakan investasi yang memakai skenario investasi yang sudah ada.
6. **Pembeli perorangan**: daftar pembeli (nama, nomor WhatsApp opsional), impor dari file Excel (.xlsx) atau .csv, serta form penjualan yang mencari dan memilih pembeli terdaftar atau mengisi pembeli baru.
7. **Penjualan mitra** per varietas dengan baris grade A, B, C, masing-masing berisi **kg dan harga per kg** (keputusan pengguna 21 November 2026).

## Bukan tujuan

- Retail. Hanya tampil sebagai "Belum tersedia" sampai aturan harga bertingkat dipastikan.
- Kurva laba kumulatif di ringkasan investor (dibatalkan pengguna 21 November 2026).
- Proyeksi otomatis atau angka investasi yang tidak berasal dari skenario investasi pengelola.
- Mengubah atau menghapus modul biaya nyata, aset, penyusutan, dan data lamanya.
- Riwayat beli lama dari Excel. Yang diimpor hanya identitas pembeli.
- Panen tetap Layak/Cacat. Grade A/B/C hanya ada di penjualan mitra.
- Library grafik atau pembaca Excel baru. Tidak ada dependency baru.

## Anti-tujuan (gagal meski "jalan")

- Biaya terhitung dua kali: biaya standar **dan** alokasi atau penyusutan masuk ke TC laporan musim yang sama.
- Laporan musim yang sudah terbit ikut berubah karena biaya standar diisi atau diubah.
- Grafik pemasukan memasukkan penjualan yang dibatalkan atau belum lunas.
- Biaya standar muncul sebagai uang keluar di kas Beranda, padahal tidak pernah dibayar.
- Nomor WhatsApp pembeli terbaca oleh akun pekerja, lewat API, sync, maupun berkas.
- Impor Excel membuat pembeli ganda untuk nomor WhatsApp yang sama.
- Angka contoh dari mockup masuk ke seed atau default produksi. Angka Inofarm (FC Rp7.581.100, VC Rp3.430.000) juga **bukan** seed: pengelola yang mengisinya.

## Batasan

- Migration additive saja (migration 23 dan seterusnya). Tes hanya di `inofarm_test`. Database `inofarm_finance_local` hanya dimigrasi dengan backup dan izin pengguna.
- Kontrak v2 tetap berlaku: envelope mutasi, UUID lowercase, desimal sebagai string, `base_version`, retry idempoten, dan audit+receipt+feed dalam satu transaksi. OpenAPI dan JSON schema diperbarui sampai parity lulus.
- Pekerja tidak punya akses keuangan atau data pembeli (403).
- Form pengelola memakai pola 17 November: Batal / Simpan, lalu Ringkasan.
- Tampilan mengikuti Canopy (DESIGN.md): tanpa warna baru, angka tabular, dan status ditulis dengan teks, tidak hanya warna.
- **Tenggat kurang dari 2 minggu** (jawaban pengguna 21 November 2026), sehingga ada garis potong (lihat Tahapan).

## Pendekatan biaya standar

Di aplikasi, musim tercatat per greenhouse (`GH1 Musim 2`, `GH2 Musim 2`, ...). Ada empat cara menyimpan "satu angka untuk semua GH":

| Pendekatan | Ringkasan | Kelebihan | Kekurangan |
|---|---|---|---|
| **D. Satu lembar per musim ke-n (dipilih)** | Satu lembar biaya untuk "Musim n" seluruh kebun. Setiap musim GH dengan nomor n memakai lembar itu. Lembar Musim n+1 menyalin Musim n. | Tidak ada isian per GH sama sekali, jadi angka antar-GH tidak mungkin berbeda; satu simpan = satu operasi; benar walau musim antar-GH tidak serentak | Butuh nomor musim (`seasons.sequence`); laporan musim membaca lembar lewat nomor itu |
| A. Disalin ke tiap musim GH | Lembar per musim GH, perubahan diterapkan ke semua GH | Laporan membaca lembar milik musim sendiri | Masih ada tempat menyimpan angka per GH, sehingga bisa berbeda; perlu N operasi per simpan |
| B. Satu template berversi tanggal | Satu daftar dengan tanggal berlaku | Satu sumber data | Mengubah angka untuk musim berjalan membingungkan; laporan harus mengunci versi |
| C. Pembayaran dan alokasi otomatis | Template membuat expense dan cost_allocation palsu | Memakai ulang alur laporan | Biaya standar terbaca sebagai uang keluar di kas Beranda |

A sempat dipilih, lalu diganti **D** setelah pengguna menegaskan bahwa FC (dan VC) tidak pernah diinput per GH.

## Desain

### 1. Data (migration 23)

- **`seasons.sequence`** (unsigned int, nullable lalu diisi): nomor musim ke-n per greenhouse. Backfill berdasarkan urutan `start_date`, lalu `id`, per GH. Musim baru mendapat nomor terbesar + 1 di server; nama `<GH> Musim <n>` tetap dibuat seperti sekarang. Nomor ini dipakai untuk mengelompokkan "Musim 2" lintas GH di ringkasan investor.
- **`season_cost_sheets`**: satu baris per nomor musim untuk seluruh kebun, tanpa `greenhouse_id`. Kolomnya `id`, `sequence` (unik), `version`, `record_state` dan timestamps. Entity ledger: `season_cost_sheet`.
- **`season_cost_lines`**: baris lembar. Kolomnya:
  - `id`, `sheet_id`
  - `name` (150)
  - `cost_class` FC/VC
  - `amount` DECIMAL(20,2), **nullable**: kosong berarti belum diisi, berbeda dari nol
  - `is_required`, `sort_order`, `active`
  - Tidak boleh ada dua baris aktif dengan nama yang sama dalam satu lembar (dibandingkan tanpa beda huruf besar-kecil).
- Daftar baris wajib adalah konstanta di server (`StandardCostCatalog`), bukan seed angka. Lembar baru selalu berisi tujuh baris wajib. Baris dan jumlahnya disalin dari lembar nomor sebelumnya (n−1); bila belum ada lembar sama sekali, jumlah dibiarkan kosong.
- Lembar Musim n dibuat oleh server saat musim GH pertama dengan nomor n dibuat (wizard dan Mulai semai), dalam transaksi yang sama. Untuk nomor musim yang sudah ada sebelum migration, lembarnya dibuat lewat perintah artisan yang dijalankan bersama migration, atau saat pertama dibaca pengelola. Pilihan antara keduanya diserahkan ke pembangun.
- Biaya per GH = jumlah baris lembar. Biaya kebun untuk Musim n = biaya per GH × banyaknya musim GH bernomor n.

### 2. API biaya standar

- `GET season-cost-sheets` (semua nomor, terbaru dulu), `GET season-cost-sheets/{id}` dan audit.
- `PUT season-cost-sheets/{id}` lewat envelope (`action: update`, `base_version`): satu operasi untuk seluruh kebun. Payload berisi daftar baris lengkap (`id`, `name`, `cost_class`, `amount`, `is_required`). Aturan yang dijaga server:
  - baris wajib tidak boleh hilang dan namanya tidak boleh berubah;
  - nama baris tambahan wajib diisi dan unik dalam lembar;
  - `amount` ≥ 0 atau null;
  - ditolak (`season_closed`) bila ada musim GH bernomor n yang sudah ditutup, karena sama dengan aturan musim tertutup yang sudah ada: buka dulu, baru ubah.
- Layar Biaya standar hanya punya pemilih **Musim n** dan keterangan "berlaku untuk N GH". Tidak ada pemilih GH.

### 3. Laporan musim

- `SeasonReportSources` memakai **biaya standar** sebagai satu-satunya sumber FC dan VC: lembar dengan `sequence` yang sama dengan musim GH yang dilaporkan. Sumbernya punya jenis baru `season_cost_sheet` dengan kontribusi per baris. Musim tanpa nomor atau tanpa lembar menghasilkan biaya belum lengkap, bukan nol.
- `cost_allocation` dan `depreciation_allocation` musim itu tetap dicantumkan sebagai `reference_only` dengan alasan "Biaya nyata; laporan memakai biaya standar". Keduanya tidak dijumlahkan.
- Biaya dianggap lengkap hanya bila semua baris wajib punya jumlah. Bila ada yang kosong, laporan tetap sementara dan alasannya menyebut baris yang kosong.
- Snapshot laporan baru memuat `cost_basis: "standard"` dan versi lembar. Laporan yang sudah terbit tidak dihitung ulang, karena sifat immutable-nya sudah dijaga.
- Rumus tidak berubah: TC = FC + VC, laba = TR − TC, R/C = TR/TC, B/C = laba/TC, BEP harga = TC/kg terjual, BEP produksi = TC/harga rata-rata. Penyebut nol menghasilkan "belum bisa dihitung".

### 4. Grafik pemasukan dan ringkasan

- **`GET reports/income`** (perluasan `FieldReports`, khusus pengelola). Parameternya `granularity=week|month|year`, `from`, `until` dan `greenhouse_id` opsional.
  - Setiap bucket WIB berisi `receipts`, `weight_kg`, dan rincian per `customer_type`: `consumer`, `partner` dan `unspecified` untuk penjualan lama yang belum bertipe.
  - Minggu dimulai hari Senin WIB. Hanya penjualan aktif dan lunas yang dihitung.
  - Endpoint `reports/cash-flow` yang lama tetap ada.
- **`GET reports/profit-summary`** (khusus pengelola) mengembalikan:
  - per GH dan per musim: TR, kg, TC standar, laba, R/C, BEP, serta status lengkap atau sementara;
  - agregat per `sequence` lintas GH;
  - agregat untuk musim yang sedang terbuka.
  - Hitungannya memakai fungsi evaluasi musim yang sama dengan laporan musim, tanpa membuat snapshot.
- Kartu "Laba musim berjalan" di layar Keuangan memakai agregat musim terbuka.
- Ringkasan investor memakai agregat per `sequence` dan tabel per GH. Bagian kelayakan investasi membaca skenario investasi yang sudah ada; bila belum ada skenario, bagian itu kosong dan menampilkan tombol untuk mengisi skenario.

### 5. Pembeli

- **`customers`** dengan kolom:
  - `id`, `type` (`consumer` atau `partner`), `name` (200)
  - `whatsapp` (nullable, disimpan dalam bentuk digit ternormalisasi `62…`), `whatsapp_display`
  - `source` (`manual` atau `import`), `record_state`, `version`, timestamps
  - Unik untuk `whatsapp` aktif per `type` (bila tidak null).
  - Entity ledger: `customer`, hanya untuk pengelola, tanpa baris feed.
- **`sales.customer_id`** nullable dengan FK. `customer_type` tetap dipakai dan diisi dari tipe pembeli. `customer_name` tetap diisi sebagai salinan nama saat penjualan dibuat, supaya riwayat tidak berubah bila nama pembeli diedit.
- **Endpoint**:
  - `customers`: list dengan `type`, `q` dan cursor; detail; create/update/cancel lewat envelope.
  - `POST customers/import` dengan `dry_run=1` untuk pratinjau dan `dry_run=0` untuk menyimpan.
  - Impor menerima maksimal 1.000 baris per permintaan (`[{row, name, whatsapp}]`). Hasilnya per baris: `created`, `existing` (nomor sudah ada) atau `invalid` beserta alasannya.
  - Batas request JSON 16 KiB mengharuskan Android mengirim impor dalam potongan. Pembangun memilih ukuran potongan, atau menaikkan batas khusus endpoint ini dengan alasan tertulis.
  - Impor yang disimpan menjadi satu entri audit per pembeli baru.
- **Pembeli baru dari form penjualan** (diperbarui saat perencanaan): payload sale boleh membawa objek `customer` (`id`, `type`, `name`, `whatsapp`). Bila `id` belum ada, server membuat pembeli itu dalam transaksi sale yang sama. Bila sudah ada, server hanya merujuknya. Dengan begitu tidak ada dua operasi yang harus berurutan di antrean offline.
- **Lembar biaya dan pembeli tidak lewat `sync/changes`**: baris `change_feed` wajib terikat ke satu greenhouse, sedangkan keduanya berlaku untuk seluruh kebun. `MutationLedger` tetap menulis audit dan receipt, tetapi melewati baris feed bila `greenhouse_id` null. Android membaca keduanya lewat GET, dan perubahannya butuh koneksi, sama seperti form admin.
- **Excel di Android**: `.xlsx` dibaca dengan `ZipInputStream` dan `XmlPullParser` bawaan Android, dari lembar pertama dan `sharedStrings`; `.csv` dipisah koma atau titik koma. Batasnya 2 MB dan 1.000 baris. Kolom ditebak dari judul (nama/pembeli; hp/wa/whatsapp/telepon) dan bisa diganti pengguna. Baris dengan nama kosong dilewati. Nomor yang tidak valid bisa diimpor tanpa nomor.

### 6. Penjualan mitra (per kg)

- `sale_items.buyer_grade` nullable (`A`, `B`, `C`), hanya boleh diisi bila pembelinya bertipe `partner`.
- Validasi `distinct` pada `harvest_item_id` diganti dengan pasangan unik (`harvest_item_id`, `buyer_grade`), supaya satu rincian panen Layak per varietas bisa dijual dalam tiga baris grade.
- Saldo kg panen tetap dijaga transaksi, dengan menjumlahkan semua baris grade. Harga per kg per grade memakai kolom yang sudah ada. Tidak ada jumlah buah.
- Form mitra per varietas berisi tiga baris grade (kg, harga per kg). Baris grade yang kosong tidak dikirim.

### 7. Android

- **Tab Keuangan** diganti layar baru sesuai mockup:
  - segmen periode;
  - grafik batang bertumpuk Compose `Canvas` dengan semantik per batang (`Role.Button`, label berisi tanggal dan nominal);
  - panel rincian, kartu per tipe pembeli, kartu laba musim berjalan, dan tiga pintasan.
- **Ikon mata** di app bar Keuangan (`Tampilkan biaya nyata` / `Sembunyikan biaya nyata`) membuka atau menutup bagian berisi isi layar `FinancialEntryScreen` yang sekarang. Pilihan disimpan per akun di penyimpanan lokal, defaultnya tersembunyi. Catat pengeluaran di sheet Catat ikut mengikuti pilihan ini.
- **Layar baru**: Biaya standar (pemilih Musim n, tanpa pemilih GH), sheet Tambah biaya, Ringkasan investor, Penjualan per tipe pembeli, sheet Siapa pembelinya, form perorangan dengan cari/pilih/baru, Daftar pembeli, Impor dari Excel, dan form mitra.
- Lembar biaya, pembeli, grafik dan ringkasan dibaca langsung dari endpoint, dengan keadaan memuat, kosong, gagal dan "butuh koneksi" yang ditulis jelas. Penjualan tetap lewat antrean offline seperti sekarang.

## Tahapan dan garis potong

| Tahap | Isi | Status untuk demo |
|---|---|---|
| 1 | Migration 23 (sequence, lembar biaya), API lembar, laporan musim berbasis biaya standar, layar Biaya standar dan Tambah biaya | **Wajib** |
| 2 | `reports/income` dan `reports/profit-summary`, layar Keuangan baru dengan grafik dan ikon mata, Ringkasan investor tanpa unduh PDF | **Wajib** |
| 3 | `customers` dan impor, form perorangan cari/pilih/baru, Daftar pembeli, Impor Excel, layar Penjualan per tipe | Wajib bila tahap 1–2 selesai di minggu pertama |
| 4 | `buyer_grade` dan form mitra per kg | Bila sempat |
| 5 | Unduh PDF ringkasan investor, retail | Sesudah tenggat |

## Diputuskan pembangun

- Cara membuat lembar untuk musim lama: saat dibaca, atau lewat perintah artisan.
- Ukuran potongan impor, atau batas request khusus untuk endpoint impor.
- Bentuk normalisasi nomor WhatsApp (08…, +62…, 62…).
- Nama tag tes Compose, dengan mengikuti pola `{form}-cancel` / `{form}-review`.
- Pemecahan file Kotlin dan PHP, mengikuti pola modul yang sudah ada.

## Masih ditanyakan ke Inofarm (tidak memblokir tahap 1–2)

- Kolom apa saja yang ada di Excel pembeli mereka.
- Aturan harga bertingkat retail.
