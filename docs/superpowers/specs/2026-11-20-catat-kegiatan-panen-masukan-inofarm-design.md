# Desain: masukan Inofarm untuk catat kegiatan dan panen

Tanggal: 20 November 2026. Status: **menunggu ACC pengguna**. Sumber: hasil uji langsung aplikasi oleh Inofarm, disampaikan pengguna pada 20 November 2026, dan diskusi lanjutannya di sesi yang sama.

## Maksud

Inofarm sudah mencoba aplikasi di lapangan. Masukan mereka menyelaraskan bahasa dan isian form dengan cara kerja nyata:

- Greenhouse selalu dimulai dari fase semai, bukan tanam.
- Nutrisi pokoknya AB Mix. Bahan lain hanya tambahan yang dicampur ke AB Mix.
- EC dan pH diukur sebelum dan sesudah inject pada hari yang sama.
- Inofarm tidak menentukan grade buah. Grade ditentukan mitra atau tengkulak saat membeli. Kalau Inofarm sendiri yang memberi grade, penilaiannya tidak adil, karena penjual cenderung menyebut semua buahnya grade A. Yang mereka tahu dan perlu catat hanya buah layak dan cacat (kg dan jumlah buah) per varietas.
- Catatan panen dipakai untuk melihat **pola panen**: kapan panen memuncak, dan apakah persentase cacat naik.

## Tujuan

1. Halaman mulai tanam menjadi **Mulai semai**, beserta semua teks "mulai tanam" yang tampil ke pengguna.
2. Nama musim baru dibuat otomatis dengan format `<nama greenhouse> Musim <n>`, misalnya `GH1 Musim 1`.
3. Pengelola yang mau mencatat di greenhouse yang belum dimulai semai mendapat pop up yang langsung mengarah ke halaman Mulai semai.
4. Form Catat kegiatan:
   - label "Waktu"
   - EC tanpa teks satuan di kotak isian
   - PPM opsional
   - pengukuran sebelum dan sesudah inject bisa diisi sekaligus
   - Nutrisi AB Mix dengan nutrisi tambahan yang diketik
   - "Satuan" untuk umur
   - "Stadia/Fase"
5. Form panen per varietas berisi **Layak** dan **Cacat** (kg, jumlah buah), tanpa grade dan tanpa jam.
6. Grafik pola panen: layak dan cacat per tanggal panen, per varietas.

## Bukan tujuan

- **Penjualan dan keuangan tidak diubah**, karena akan direvisi terpisah. Ini termasuk form penjualan, aturan satu sumber panen per baris, isian grade di penjualan, dan semua hitungan TR/BEP.
- Menu Grade di Kelola tidak diubah atau disembunyikan pada tahap ini.
- Musim yang sudah ada tidak diganti namanya.
- Satuan mg tidak ditambahkan. Gram dengan desimal sudah mencakupnya, dan Inofarm tidak yakin memakai mg.
- Tidak ada library grafik baru.

## Anti-tujuan (gagal meski "jalan")

- Pilihan grade muncul lagi di form panen dalam bentuk apa pun.
- Penjualan yang sudah ada, atau penjualan dari panen baru, gagal atau hitungannya berubah.
- Data panen lama (yang memakai grade) hilang, berubah, atau tidak bisa dibaca.
- Pengukuran yang hanya berisi EC tidak muncul di Beranda atau di "pengukuran terakhir".
- Pekerja bisa memulai semai.
- Teks "pindah tanam" atau "jumlah tanaman" ikut berubah menjadi "semai". Keduanya konsep lain: transplantasi (dasar HST) dan jumlah tumbuhan.

## Batasan dari kode sekarang

- `harvest_items.grade_id` wajib (FK ke `grades`), dan `sale_items.grade_id`/`grade_name` juga wajib. `SaleService` menyalin grade dari sumber panen ke baris penjualan. Karena itu grade di panen tidak boleh dibuat null tanpa menyentuh penjualan.
- `HarvestService` mewajibkan grade aktif dari greenhouse yang sama, grade unik per panen, dan grade tidak bisa diganti pada rincian lama.
- `ActivityService` menolak pengukuran tanpa EC **dan** PPM. Beranda (`ManagerHome.php:45`), `measurements/latest` (`ActivityController.php:29`) dan feed sinkronisasi (`ChangeFeedController.php:60`) mengenali pengukuran lewat `manual_ppm IS NOT NULL`.
- `resolveMaterials` hanya menerima nama bahan yang diketik untuk penyemprotan (`SprayMaterialCatalog`). Nutrisi masih wajib satuan berdimensi sama dengan bahannya.
- Satu isian kegiatan boleh menghasilkan beberapa baris per bagian dengan `entry_batch_id` yang sama, tanpa indeks unik per jenis. Dua baris pengukuran dalam satu isian sudah diterima server. Momen ukur disimpan di `observations.measurement_moment`.
- Database lokal `inofarm_finance_local` di migration 22. Tes hanya boleh memakai `inofarm_test`.

## Pendekatan untuk panen tanpa grade

**A. Grade sistem "Layak" dan "Cacat" (dipilih, keputusan builder, lihat catatan di bawah).** Android hanya mengirim `quality` per rincian, tanpa `grade_id`. Server mencari atau membuat grade bernama "Layak" dan "Cacat" di greenhouse itu, dengan pola yang sama seperti `SprayMaterialCatalog`, lalu mengisi `grade_id` dari situ.
- Untung: tanpa migration dan tanpa perubahan skema penjualan. Penjualan dari panen baru tetap jalan, dan sumbernya otomatis tampil sebagai "Layak · …" atau "Cacat · …".
- Rugi: dua baris "grade" di tabel `grades` sebenarnya mewakili kualitas, bukan grade. Keduanya juga terlihat di menu Grade di Kelola sampai keuangan direvisi.

**B. `grade_id` boleh null (migration 23).** Skemanya lebih bersih, tetapi `sale_items.grade_id`/`grade_name` dan `SaleService` harus ikut diubah, dan label "Grade belum tersedia" muncul di form penjualan. Ini bertentangan dengan keputusan "jangan sentuh penjualan".

**C. Android membuat grade lewat API master sebelum menyimpan panen.** Butuh koneksi untuk tulis admin, padahal panen harus bisa dicatat offline. Ditolak.

Pendekatan A dipilih karena satu-satunya yang memenuhi tujuan tanpa menyentuh penjualan. Kalau keuangan nanti direvisi, grade sistem ini bisa dimigrasikan ke kolom kualitas murni.

## Rancangan

### 1. Mulai semai (teks)

| Lokasi | Sekarang | Menjadi |
|---|---|---|
| `GreenhouseWizard.kt` judul, langkah, judul langkah 2 | Mulai tanam | Mulai semai |
| `GreenhouseWizard.kt` bagian | Tanam | Semai |
| `GreenhouseWizard.kt` keterangan | Semua varietas di greenhouse ini ditanam serentak. | Semua varietas di greenhouse ini disemai serentak. |
| `GreenhouseWizard.kt` progres | Menetapkan tanam saat ini… | Menetapkan semai saat ini… |
| `GreenhouseWizard.kt` layar selesai | Mulai tanam X dan Y… / Belum mulai tanam. Mulai tanam dari halaman greenhouse… | Mulai semai X dan Y… / Belum mulai semai. Mulai semai dari halaman greenhouse… |
| `RecordingTarget.kt` keterangan | Belum mulai tanam… / Minta pengelola memulai tanam… / Tanam dimulai … | Belum mulai semai… / Minta pengelola memulai semai… / Semai dimulai … |
| `ManagerSheets.kt` kartu | Mulai tanam · Greenhouse ini belum punya tanam saat ini | Mulai semai · Greenhouse ini belum dimulai semai |
| `HomeContextCard.kt` tombol dan teks cadangan | Mulai tanam / Belum mulai tanam. | Mulai semai / Belum mulai semai. |

Tetap: "Tanggal semai", "Tanggal pindah tanam (opsional)", "Jumlah tanaman". Builder mencari sisa teks "mulai tanam" atau "memulai tanam" yang tampil ke pengguna di modul catat, wizard dan Beranda, lalu memperlakukannya sama. Nama variabel dan tag tes tidak wajib diganti.

### 2. Nama musim otomatis

- Di wizard (Tambah greenhouse langkah 2 dan Mulai semai), kotak "Nama musim" diganti teks baca saja `<nama greenhouse> Musim <n>`.
- `n` = jumlah musim greenhouse itu yang ada di perangkat + 1.
- Kalau nama yang sama sudah dipakai musim lain di greenhouse itu, `n` dinaikkan sampai unik.
- Server tidak berubah, karena nama tetap string biasa.

### 3. Pop up greenhouse belum dimulai semai

- Di sheet Catat, kalau **pengelola** menekan Kegiatan atau Panen pada greenhouse yang `RecordingTarget.NotPlanted`, form tidak dibuka. Yang muncul dialog:
  - Judul: "Greenhouse belum dimulai semai"
  - Isi: "Apakah mau mulai semai?"
  - Tombol: **Tidak** (tutup dialog, tetap di sheet) dan **Lanjut** (buka wizard Mulai semai untuk greenhouse itu, alur yang sama dengan kartu Mulai semai).
- Pekerja tidak mendapat dialog. Keterangan "Belum mulai semai. Minta pengelola memulai semai di greenhouse ini." tetap tampil, dan form tidak terbuka.
- `NotSown` (tanggal semai di masa depan) tidak berubah dan tidak memunculkan dialog.
- Kartu Mulai semai di sheet tetap ada.

### 4. Form Catat kegiatan

**Waktu.** Bagian "Waktu kejadian" menjadi "Waktu" di form kegiatan dan panen. Kalau labelnya berasal dari komponen bersama yang juga dipakai form keuangan (`FormCanopy.kt:252`, `FinanceRoot.kt:1482`), form keuangan tetap memakai label lama.

**Umur tanaman.** Chip "Dasar umur" menjadi "Satuan". Isinya tetap HSS/HST.

**Pengukuran.**
- Teks satuan `mS/cm` di kotak EC dihapus. Kotak PPM tetap memakai `ppm`. Ringkasan dan Riwayat tetap menulis satuan.
- PPM opsional: kalau bagian pengukuran diisi, EC wajib dan PPM, pH serta suhu air boleh kosong. Teks bantuan: "PPM, pH dan suhu air opsional. Kosongkan semua bila tidak mengukur."
- Chip "Diukur" bisa dipilih dua sekaligus:
  - tidak ada yang dipilih: satu blok pengukuran tanpa momen (perilaku lama)
  - satu dipilih: satu blok dengan momen itu
  - dua dipilih: dua blok bertumpuk, **Sebelum inject di atas** dan **Sesudah inject di bawah**, masing-masing dengan EC, PPM, pH dan suhu air
- Setiap blok yang terisi menjadi satu baris `measurement` dengan `entry_batch_id` yang sama dan `observations.measurement_moment` masing-masing.
- Membatalkan pilihan kedua menghapus blok itu beserta isinya. Kalau blok sudah berisi angka, minta konfirmasi dulu (pola konfirmasi yang sudah ada).

**Nutrisi AB Mix.** Bagian "Nutrisi" menjadi "Nutrisi AB Mix":

```
Nutrisi AB Mix
  Jumlah AB Mix   [        ]   Satuan [mL | L | gram]
Nutrisi tambahan (opsional)
  Nama bahan [ketik            ]
  Jumlah [      ]   Satuan [mL | L | gram]          [hapus]
  + Tambah nutrisi tambahan
```

- AB Mix adalah satu bahan tetap bernama "AB Mix" per greenhouse, dicari atau dibuat server (pola `SprayMaterialCatalog`, tujuan nutrisi).
- Nutrisi tambahan diketik bebas, lalu menjadi bahan nutrisi greenhouse itu.
- Semua bahan masuk ke satu catatan `nutrition` di isian yang sama, sebagai baris `activity_materials` biasa. Tidak ada tabel baru.
- Satuan dibatasi tiga pilihan tetap: mL, L, gram. Server mencari atau membuat satuan bernama itu di greenhouse, sehingga Android tidak bergantung pada master satuan yang sudah ada. Aturan "satuan berdimensi sama dengan bahan" dilonggarkan untuk nutrisi, sama seperti penyemprotan.
- Bagian nutrisi dianggap terisi kalau jumlah AB Mix diisi atau ada sedikitnya satu nutrisi tambahan. Jumlah dan satuan saling wajib per baris. Nutrisi tambahan wajib punya nama.
- Bahan nutrisi lama yang dipilih dari master tetap terbaca di Riwayat dan koreksi.

**Stadia/Fase.** Judul bagian dan label tampilan "Stadia" menjadi "Stadia/Fase", termasuk label jenis di Riwayat dan Ringkasan. Kotaknya menjadi "Stadia/Fase saat ini (opsional)".

**Server (kegiatan).**
- Aturan pengukuran: EC wajib, PPM opsional.
- Beranda, `measurements/latest` dan feed sinkronisasi mengenali pengukuran lewat `manual_ec IS NOT NULL`, bukan `manual_ppm`.
- Kalau dua pengukuran punya `occurred_at` yang sama, "pengukuran terakhir" memilih yang sesudah inject.
- Kartu cek PPM terhadap target hanya menilai bila PPM ada.
- `resolveMaterials` menerima nama ketikan untuk nutrisi, dan nama satuan tetap (mL, L, gram). Kontrak OpenAPI dan schema JSON di `contracts/v2/` diperbarui.

### 5. Form panen

- Per varietas, dua baris tetap:
  - **Layak**: Bobot (kg), Jumlah buah (opsional)
  - **Cacat**: Bobot (kg), Jumlah buah (opsional)
- Pilihan grade dan tombol tambah rincian dihapus. Teks "Hasil per grade" menjadi "Layak dan cacat per varietas" (sheet Catat dan Ringkasan).
- Baris yang bobot dan jumlahnya kosong tidak dikirim. Baris yang punya jumlah buah wajib punya bobot. Bobot 0 boleh.
- Satu varietas boleh kosong, tetapi tidak kedua varietas sekaligus (aturan lama).
- Jam panen tidak ditampilkan. `occurred_at` dikirim sebagai tanggal pukul 12.00 WIB. Server masih membutuhkan waktu, dan umur tanaman dihitung per hari.
- Payload rincian: `id`, `quality` (`saleable`/`defective`, wajib), `weight_kg`, `fruit_count`. `grade_id` tidak dikirim lagi.
- Server: kalau `grade_id` tidak ada, `quality` wajib, dan server mengisi `grade_id`, `grade_name`, `grade_version` dari grade sistem "Layak"/"Cacat" greenhouse itu (pendekatan A). Paling banyak satu rincian per kualitas per panen, lewat aturan grade unik yang sudah ada. Payload lama dengan `grade_id` tetap diterima.
- Panen lama:
  - Riwayat tetap menampilkan nama grade-nya.
  - Koreksi panen lama mempertahankan rinciannya apa adanya. Bobot dan jumlah buah bisa diubah, grade tampil sebagai teks baca saja, dan tidak ada baris baru.
  - Koreksi panen baru memakai form Layak/Cacat.

### 6. Grafik pola panen

- Tempat: halaman detail greenhouse pengelola, bagian Panen, untuk musim terbuka. Pekerja tidak mendapat grafik pada tahap ini.
- Isi per varietas:
  - Ringkasan: "Total 320 kg · cacat 12%"
  - Grafik batang bertumpuk per tanggal panen: kg layak (hijau) dan kg cacat (merah bata)
  - Sumbu tanggal pendek ("18 Nov"), bisa digulir horizontal kalau tanggalnya banyak
- Hanya panen aktif. Revisi terbaru, tanpa yang dibatalkan.
- Rincian panen lama yang `quality`-nya kosong tidak dihitung, dan ada catatan kecil "N kg panen lama tanpa layak/cacat tidak dihitung".
- Digambar dengan Compose `Canvas` dari data panen yang sudah tersimpan di perangkat (cache sinkronisasi). Tanpa endpoint baru dan tanpa library baru.
- Aksesibilitas: grafik punya deskripsi untuk TalkBack ("18 Nov: layak 40 kg, cacat 5 kg; …"). Warna tidak menjadi satu-satunya pembeda: ada legenda berteks, dan angka muncul saat batang disentuh.
- Kosong: "Belum ada panen di musim ini."

## Pemeriksaan dan bukti

- **Backend:** tes PHPUnit untuk
  - pengukuran hanya EC (diterima, muncul di latest dan Beranda)
  - dua pengukuran satu isian
  - nutrisi ketikan dan satuan tetap
  - panen hanya `quality` (grade sistem dibuat sekali per greenhouse dan dipakai ulang; dua kualitas dalam satu panen; payload `grade_id` lama tetap jalan)
  - **penjualan dari panen baru tetap lulus tanpa perubahan kode penjualan**
- **Kontrak:** `scripts/check-finance-contracts.py` lulus setelah OpenAPI/schema/fixture diperbarui.
- **Android:**
  - tes JVM untuk validasi `EntryDraft` (EC wajib, PPM opsional, dua blok), `HarvestDraft` (layak/cacat, tanpa grade, jam tetap), nama musim otomatis, dan agregasi data grafik
  - lint 0 error
  - instrumentasi terfokus di emulator `Inofarm_Botanical_API30` (mode terang) dengan fixture; tag tes lama yang berubah diperbarui
- **Backend nyata:** satu putaran `RecordingReworkBackendDeviceTest` (atau tes baru sejenis) terhadap `inofarm_test` untuk dua pengukuran, nutrisi ketikan, dan panen layak/cacat.
- **Database lokal:** tidak ada migration. `inofarm_finance_local` tidak disentuh, kecuali grade sistem dan satuan yang dibuat server saat pertama dipakai.
- **Konteks:** dokumen bukti di `docs/context/` dan paragraf ringkas di `CLAUDE.md`.

## Diserahkan ke builder

- Bentuk visual dialog, chip pilihan ganda dan grafik, mengikuti komponen Canopy yang ada (`FarmFormFrame`, `FarmChoiceChips`, token warna).
- Cara menyimpan dua blok pengukuran di `EntryDraft` dan draf lokal, dengan syarat draf lama tetap terbuka.
- Nama tag tes baru.
- Urutan commit per bagian, di branch baru tanpa prefix `codex/`.

## Dipastikan ke Inofarm (tidak menghambat)

- AB Mix dicatat sebagai **satu angka** (A dan B dianggap sama banyak). Kalau Inofarm mencatat A dan B terpisah, bagian ini menjadi dua kotak; perubahannya kecil.

## Urutan pengerjaan

1. Teks: mulai semai, Waktu, mS/cm, Satuan, Stadia/Fase.
2. Pop up mulai semai dan nama musim otomatis.
3. Pengukuran: PPM opsional (server dan Android) dan sebelum/sesudah inject.
4. Nutrisi AB Mix dan nutrisi tambahan (server dan Android).
5. Panen layak/cacat tanpa grade dan jam (server dan Android).
6. Grafik pola panen.
7. Uji backend nyata, bukti dan konteks.
