# Audit menyeluruh halaman pengelola (21 November 2026)

Pengujian manual di emulator `Inofarm_Botanical_API30` (APK debug dari branch `keuangan-biaya-standar`, `89ccc11`) terhadap Laravel nyata dan database aplikasi `inofarm_finance_local`, login `pengelola1@gmail.com`. Setiap simpan dicek langsung di MySQL. Bukan ponsel fisik, bukan TalkBack.

Backup sebelum pengujian: `D:/inofarm-backup/inofarm_finance_local-20261121-125544.sql` (SHA-256 `f1223c35…1525759`).

## Data uji yang sekarang ada di aplikasi

Atas izin pengguna ("tulis bebas"), data berikut dibuat dan dibiarkan agar bisa dilihat:

| Fitur | Data | Lokasi |
|---|---|---|
| Catat kegiatan | HSS 27, EC 2,1 / PPM 1050 (sebelum inject), AB Mix 4 mL, semprot Daconil 2 gram (pagi), stadia Vegetatif, catatan "UJI AUDIT 21 Nov" | GH2 |
| Catat panen | Layak 12,5 kg / 30 buah, Cacat 1,2 kg | GH2, 21 Nov |
| Penjualan lunas | 10 kg × Rp35.000 = Rp350.000, mitra baru "Toko Buah UJI AUDIT", grade A | GH2, musim "tes" |
| Biaya standar Musim 1 | FC: penyusutan 500 rb, sewa 1 jt, tenaga kerja 2 jt; VC: cocopeat 400 rb, AB Mix 250 rb, pestisida 150 rb, benih 200 rb (total Rp4,5 jt per GH) | seluruh kebun |
| Evaluasi musim | Laporan **Sementara** terbit + PDF diunduh | GH2, musim "tes" |
| Cek catatan | Pengukuran Pekerja 1 (18 Nov) diputuskan **Sesuai** | GH2 |
| Tambah greenhouse | "GH UJI AUDIT" + musim + semai Inthanon 200 tanaman, lalu tanam saat ini dikosongkan dan GH **dinonaktifkan** | - |

## Hasil fungsional per fitur

| Fitur | Hasil | Bukti |
|---|---|---|
| Login, logout, ganti akun | Jalan | - |
| Catat kegiatan (form gabungan) | Jalan: 4 baris satu `entry_batch_id`, `measurement_moment=before_injection` | SQL `activities` |
| Catat panen Layak/Cacat | Jalan, `fruit_count` null untuk Cacat | SQL `harvest_items` |
| Penjualan lunas + pembeli baru inline | Jalan, `customer_id` terisi, `buyer_grade=A` | SQL `sales`, `customers` |
| Biaya standar (lembar seluruh kebun) | Jalan, "tersimpan untuk 5 GH" | layar + kartu laba berubah |
| Laba musim berjalan, R/C, BEP | Benar (lihat bagian rumus) | hitung ulang manual |
| Evaluasi musim → pratinjau → terbit → PDF | Jalan, PDF 44 KB | berkas di Downloads emulator |
| Ringkasan investor | Jalan | layar |
| Cek catatan → Sesuai | Jalan | SQL `activity_reviews` |
| Wizard Tambah greenhouse | Jalan | layar + SQL |
| Kosongkan tanam saat ini | Jalan | SQL `running_group_id` null |
| Nonaktifkan greenhouse | Jalan **setelah** konflik versi (B2) | SQL `active=0` |
| Beranda kas bulan ini | Ikut berubah (Rp350.000 masuk, Rp10.000 keluar) | layar |
| Mode gelap | Terbaca, tidak ada yang rusak di Keuangan | screenshot |
| Back sistem | Jalan di laporan, keuangan, form | - |
| **Rekap panen (pintasan Beranda)** | **Tidak membuka apa-apa** (B1) | - |

Belum diuji di sesi ini: analisis investasi (NPV/IRR/PP/DPP), catat pengeluaran nyata, alokasi biaya, perolehan aset dan penyusutan, cadangan data, buat/reset akun, CRUD data master, impor pembeli .xlsx/.csv.

## Bug fungsional

**B1 (P1) Pintasan "Rekap panen" di Beranda mati.** `onHarvestProgress` hanya mengubah `harvestProgressOpen = true` (`android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt:990`), tetapi di blok `when` cabang `tab == 0 && manager ->` (`FinanceRoot.kt:964`) dievaluasi lebih dulu daripada `harvestProgressOpen && manager ->` (`FinanceRoot.kt:1075`), sehingga layar Rekap panen tidak pernah tampil. Diketuk dua kali, tidak ada respons.

**B2 (P1) Konflik versi palsu saat menonaktifkan GH.** Urutan: Ubah GH → hapus centang Aktif → server menolak "Akhiri atau ganti periode berjalan terlebih dahulu" (tanpa tautan ke tindakan itu) → kosongkan tanam saat ini → Ubah lagi → Simpan → "Data telah berubah… Data ini baru diubah di perangkat lain". Perubahan itu dibuat di perangkat yang sama; form memakai `version` GH dari cache sebelum penetapan periode. Harus menekan "Pakai data terbaru" lalu mencentang ulang. Ringkasan juga menampilkan "Terbaru · Aktif: true" (boolean mentah).

**B3 (P1) Draf pengelola lama macet permanen.** Kegiatan pengelola 18 Nov (GH1) ditolak server `running_period_changed` karena versi GH1 berubah. Indikator "1 belum terkirim" menempel di semua tab. "Lanjutkan draf" membuka form **format lama** (pilih satu jenis kegiatan), dan tombol **Simpan** di Ringkasan tidak memberi respons apa pun. Tidak ada tombol "Buang draf" di sheet atau detail.

**B4 (P1) Dua sumber umur tanaman.** Form panen GH2 menampilkan **HSS 6** (dihitung `PlantAge` dari tanggal semai 16 Nov, `HarvestScreen.kt:48`), sedangkan kartu Beranda menampilkan **26/27 HSS** (umur yang diketik di form kegiatan). GH1 tertulis "HSS 1 · diisi 18 Nov" tiga hari kemudian. Keputusan 20 Nov menyebut umur "diisi sesuai fase dari Inofarm, bukan dihitung aplikasi", jadi strip form panen bertentangan.

**B5 (P2) Tombol tanpa umpan balik.** "Buat evaluasi musim" saat GH/musim belum dipilih tidak melakukan apa-apa dan tidak memberi pesan.

**B6 (P2) "Pengukuran hari ini" menampilkan data 18 Nov** di detail GH1, dengan catatan kecil "Kartu menampilkan pengukuran terakhir". Judul menyesatkan.

**B7 (P2, perlu dicek) Wizard menyimpan GH di langkah 1.** "Simpan dan lanjut" langsung membuat greenhouse; bila pengelola berhenti di langkah 2, GH tanpa musim tertinggal. GH5 ("Belum ada tanam saat ini") kemungkinan berasal dari pola ini.

### Status perbaikan (branch `perbaikan-bug-audit-pengelola`, 21 November 2026)

| Bug | Perbaikan | Bukti |
|---|---|---|
| B1 | Cabang Beranda pengelola di `FinanceRoot.kt` tidak lagi menutupi Rekap panen; tombol kembali berlabel "Kembali ke Beranda" | Emulator: Rekap panen terbuka, pilih GH2 tampil, back ke Beranda |
| B2 | `AdminScreen` selalu memuat ulang saat membuka satu GH dan membaca state VM terbaru; `AdminViewModel` membuka versi segar bila form lama belum diubah; pesan konflik tidak lagi menyebut "perangkat lain" dan boolean tampil Ya/Tidak; petunjuk "kosongkan tanam saat ini" muncul saat server menolak | Emulator: aktifkan → pasang tanam → tolak + petunjuk → kosongkan → Nonaktifkan, SQL `active=0`, versi 7 → 8 tanpa konflik |
| B3 | `LocalFinanceStore.discardRejected` + tombol "Buang isian yang ditolak" (dengan konfirmasi) di detail; galat Simpan tampil di Ringkasan kegiatan | Emulator: kegiatan offline, versi GH2 dinaikkan lewat API, ditolak `running_period_changed`, Simpan menampilkan pesan, Buang menghapus "1 belum terkirim". Instrumentasi `FinanceLocalStoreTest` 30/30 (2 tes baru) |
| B4 | Form panen memakai umur terakhir yang diketik (`lastTypedAge`), bukan dihitung dari tanggal semai | Emulator: strip "HSS 27", kartu "Umur tanaman terakhir dicatat: HSS 27 · diisi 21 Nov" |
| B5 | Teks "Pilih greenhouse laporan dulu" di bawah tombol nonaktif | Kode |
| B6 | Judul "Pengukuran terakhir" bila data bukan hari ini | Kode |

JVM 177 lulus, lint 0 error. Belum: ponsel fisik, `FinanceEntryUiTest` penuh. Catatan: instrumentasi menghapus data aplikasi di emulator, jadi draf macet 18 Nov yang asli ikut hilang; B3 diuji dengan kondisi yang dibuat ulang.

## Rumus keuangan (diverifikasi)

Kartu "Laba musim berjalan · 5 GH" (Keuangan) dan laporan evaluasi GH2 dihitung ulang manual:

| Besaran | Rumus | Hitung manual | Aplikasi |
|---|---|---|---|
| TC kebun | 5 GH × Rp4.500.000 | Rp22.500.000 | Rp22.500.000 |
| Laba kebun | TR − TC = 350.000 − 22.500.000 | −Rp22.150.000 | −Rp22.150.000 (Rugi) |
| R/C kebun | 350.000 / 22.500.000 = 0,01556 | 0,02 | 0,02 |
| BEP harga kebun | TC / kg terjual = 22.500.000 / 10 | Rp2.250.000/kg | Rp2.250.000 |
| BEP produksi kebun | TC / harga rata-rata = 22.500.000 / 35.000 | 642,86 kg | 643 kg |
| Laba GH2 | 350.000 − 4.500.000 | −Rp4.150.000 | −Rp4.150.000,00 |
| R/C GH2 | 350.000 / 4.500.000 | 0,0777778 | 0,07777778 |
| B/C GH2 | −4.150.000 / 4.500.000 | −0,9222222 | −0,92222222 |
| BEP kuantitas GH2 | 4.500.000 / 35.000 | 128,571 kg | 128,571429 kg |
| BEP harga GH2 | 4.500.000 / 10 | Rp450.000/kg | Rp450.000,00/kg |
| Harga rata-rata | 350.000 / 10 kg | Rp35.000/kg | Rp35.000/kg |

Semua angka benar. Masalahnya ada di penyajian:

- **K1** B/C tidak tampil di kartu laba Keuangan (hanya di laporan evaluasi).
- **K2** Presisi rasio berbeda: 0,02 di Keuangan vs 0,07777778 di laporan; satuan "rasio" ditulis di belakang angka.
- **K3** Nama metrik berbeda antar layar: "BEP produksi" vs "BEP kuantitas pada biaya tercatat"; laporan menulis "metode biaya tercatat" padahal memakai biaya standar.
- **K4** Sebelum lembar biaya standar diisi, kartu laba hanya menulis "Belum bisa dihitung" (Laba berwarna hijau) tanpa tombol "Isi biaya standar".
- **K5** Dua konsep "pengeluaran" tanpa penjelasan: Beranda kas = pembayaran aktual (Rp10.000), Keuangan = biaya standar (Rp22,5 jt).
- **K6** GH uji "UJI Pemulihan WR 09 Nov" punya musim terbuka, jadi ikut menambah TC Rp4,5 jt dan tampil di tabel investor. Ini kebersihan data, bukan bug rumus.
- **K7** Field `payment_method` (QRIS/tunai) dari backend 17 Nov belum ada di form penjualan; baris tersimpan `NULL`.

**Statistik di halaman awal Keuangan.** Sudah ada: grafik batang pemasukan bertumpuk Mitra/Perorangan (Minggu/Bulan/Tahun, bisa diketuk), rincian per tipe pembeli, kartu laba musim berjalan, serta grafik penerimaan vs biaya per musim di Ringkasan investor. Belum ada: biaya atau laba per bulan pada grafik utama (grafik hanya pemasukan), laba per GH di halaman awal (hanya di halaman investor), dan komposisi FC/VC. Bagian "Per tipe pembeli" mengulang angka yang sudah ada di bawah grafik.

## Alur yang bisa dipersingkat

| Alur | Sekarang | Usulan |
|---|---|---|
| Catat kegiatan | Catat → chip GH (tidak wajib) → kartu Kegiatan → sheet kedua "Greenhouse: Belum dipilih" → dialog daftar GH → "Lanjut ke formulir" | Pilih GH sekali di sheet pertama, langsung buka form; ingat GH terakhir |
| Penjualan | GH dipilih di sheet Catat, form tetap meminta "Musim penjualan: Belum dipilih" (hanya ada satu musim) | Turunkan musim dari GH; auto-pilih bila cuma satu |
| Evaluasi musim | GH dan musim dipilih di daftar, lalu diminta lagi di form; 3 dropdown pernyataan + **ketik jumlah catatan belum terkirim**; catatan pengelola wajib | Bawa pilihan dari daftar; isi jumlah antrean otomatis dari outbox; catatan opsional |
| Biaya nyata, aset, alokasi | Ikon mata → kartu "Biaya nyata" → "Buka pencatatan biaya nyata" → pilih GH → baru tombol aktif; di "Alat keuangan" semua item abu-abu sampai GH dipilih di layar sebelumnya | Ganti ikon mata dengan baris "Biaya nyata" biasa; pilih GH di dalam tiap form |
| Ubah/nonaktifkan GH dari detail GH | Kadang membuka konsol CRUD generik berisi semua GH ("Pilih data → Jenis data") | Selalu buka form GH yang sedang dilihat; tombol "Nonaktifkan" langsung, dengan tautan "Kosongkan tanam saat ini" bila ditolak |
| Cek catatan | Buka kartu → gulir → dropdown Keputusan → "Simpan keputusan" → dialog ringkasan → Simpan; tidak lanjut ke catatan berikutnya | Dua tombol besar Sesuai / Perlu diperbaiki; setelah simpan buka catatan berikutnya |
| Riwayat | Satu isian Catat kegiatan muncul sebagai 4 baris | Kelompokkan per `entry_batch_id` |

Duplikasi pintu masuk: "Tambah greenhouse" (Beranda, Kelola, daftar GH), "Catat penjualan" (Catat, Keuangan, Alat keuangan), "Evaluasi musim" (Beranda, Alat keuangan), "Cek catatan" (pintasan + banner di Beranda), tombol kembali ganda (panah + teks "Kembali ke daftar"). "Ringkasan investor" dan "Analisis investasi" bernama mirip tetapi isinya berbeda.

Pertanyaan untuk pengguna: menu Catat pengelola hanya berisi Kegiatan, Panen, Penjualan. Pengeluaran nyata tidak ada lagi di Catat. Apakah ini disengaja sejak biaya standar 21 Nov?

## UI (impeccable critique + antislop)

Nielsen, 0–4: status sistem 2, bahasa pengguna 2, kendali 3, konsistensi 2, cegah salah 3, kenali bukan ingat 2, efisiensi 2, estetika 3, pulih dari galat 2, bantuan 2. **Total 23/40 (Acceptable)**.

Yang sudah baik: palet Canopy (forest + krem) konsisten dan punya identitas; mode gelap terbaca; langkah Ringkasan sebelum simpan jelas; dialog "buang isian" melindungi data; validasi muncul di tempatnya; angka besar di kartu hero mudah dibaca.

Masalah prioritas:

1. **U1 (P1) Ruang form habis oleh bar bertumpuk.** Form di dalam tab (Biaya standar, Catat pengeluaran) menampilkan app bar + kartu hero + bar Batal/Simpan + dock. Saat keyboard terbuka hanya **satu isian** yang terlihat. Form Catat kegiatan/panen/penjualan juga tetap menampilkan app bar "Selamat siang · 1 belum terkirim" di atas header form.
2. **U2 (P1) Konsol CRUD generik terlalu polos.** Kelola → Greenhouse / Pekerja dan akun / data master memakai satu layar "Pilih data → Jenis data" (10 jenis), dengan kartu berisi nama + "Aktif" + dua tombol teks. Terasa panel admin template, bukan bagian Canopy. Form Ubah GH memakai checkbox "Aktif", dan pesan tolak berwarna abu netral.
3. **U3 (P1) Bahasa teknis di layar.** "(YYYY-MM-DD)", "(HH:mm)", "Detik opsional HH:mm:ss", "Lebih dari nol, maksimal6 desimal; tanpa pemisah ribuan" (spasi hilang), "Kosong bukan nol", "10.000000 kg", "Total isian, bukan nilai resmi", "Perkiraan total penjualan", "Revisi 1", "Versi: belum terkirim", "Aktif: true", asumsi laporan berisi "snapshot", "agregasi", "sesuai kontrak", "Musim ke-n", nama berkas PDF `inofarm-season_report-<uuid>.pdf`.
4. **U4 (P2) Format angka dan waktu tidak seragam.** Rp350.000 vs Rp350.000,00; jam 13.00 vs 13:05; desimal 1.5 dan 12.5 kg vs 2,1 mS/cm; tanggal 2026-11-21 vs 21 Nov 2026 (ringkasan semprot, detail kegiatan, periode musim).
5. **U5 (P2) Dekorasi daun menutupi data.** Garis daun melintasi grafik pemasukan dan grafik investor, dan muncul di setiap kartu gelap (Beranda, Keuangan, Biaya standar, detail GH, Kelola). Sebagai motif identitas bagus di satu tempat; di atas grafik menjadi gangguan.

Masalah lain: angka BEP "Rp2.250.00 / 0" patah ke baris baru; "Laba: Belum bisa dihitung" berwarna hijau; tombol "Masuk" di login tertutup lipatan saat banner muncul; judul "Cek catatan …" terpotong chip "1 belum terkirim"; GH nonaktif tampil paling atas di Beranda; "Retail · Belum tersedia" tampil sebagai fitur mati; chip terpilih punya dua gaya (gelap di HSS, krem pucat di "Sebelum inject"); ikon jam yang sama untuk semprot, stadia dan nutrisi di Riwayat; badge "Terkirim" di setiap baris; "Data sudah diperbarui." dan "Diperbarui 13.22" tampil berdua; form Evaluasi musim tidak memakai `FarmFormFrame` (Batal berupa teks di bawah judul, tanpa header X dan bar bawah); sheet Catat punya ruang kosong besar di atas judul; satuan semprot hanya "gram"; satuan AB Mix "mL / L / gram" ambigu antara volume total dan konsentrasi.

antislop: tidak ada em dash, statistik palsu atau testimoni di UI. Pelanggaran yang relevan: R-26 (tombol tanpa perilaku: Rekap panen, Buat evaluasi musim tanpa pilihan, Simpan draf macet), R-27 (tidak ada keadaan galat untuk draf macet), R-05/R-20 (konsol CRUD template).

### Status perbaikan UI (branch yang sama, 21 November 2026)

| Temuan | Perbaikan | Bukti |
|---|---|---|
| U1 | Bar atas disembunyikan saat form pengelola terbuka (kegiatan, panen, penjualan, pengeluaran, aset, evaluasi, investasi); Biaya standar, Ringkasan investor dan Daftar pembeli tampil penuh tanpa bar dan dock | Emulator: form penjualan tanpa sapaan; Biaya standar dengan keyboard memperlihatkan dua isian |
| U3 | Label tanggal/jam tanpa "(YYYY-MM-DD)"/"(HH:mm)", petunjuk angka diganti contoh ("Contoh 12,5"), pesan galat tanggal mengarah ke kalender, "maksimal6 desimal", "Kosong bukan nol", "bukan nilai resmi", "Versi: …", "Aktif: true" diganti; nama PDF `Inofarm-evaluasi-musim-xxxxxxxx.pdf` | Emulator (form penjualan), kode |
| U4 | `moneyLabel` tanpa ",00" bila tidak ada sen; kg dan EC/PPM memakai koma; tanggal ringkasan dan detail memakai format terbaca; rasio laporan 2 desimal di layar (PDF/CSV tetap presisi server); nama metrik laporan = Keuangan (BEP produksi, BEP harga/kg) | Emulator (Keuangan), kode |
| U5 | `FarmHeroCard(motif = false)` untuk grafik pemasukan, grafik investor dan ringkasan biaya standar | Emulator: grafik tanpa daun |
| Kecil | Kartu laba 2×2 dengan B/C (laba/TC) dan tombol "Isi biaya standar" bila biaya kosong; "Belum bisa dihitung" tidak hijau; kartu "Per tipe pembeli" dan "Retail · Belum tersedia" dihapus (persentase dan "Daftar pembeli" pindah ke panel grafik); banner duplikat Cek catatan dihapus; GH nonaktif di urutan akhir; pesan logout di bawah tombol Masuk; judul "Cek catatan" | Emulator: B/C −0,98, BEP tidak patah |

Belum: U2 (desain ulang konsol Kelola), pengelompokan Riwayat per isian, isi otomatis jumlah antrean di Evaluasi musim, ponsel fisik.

## Urutan perbaikan yang disarankan

1. B1, B3, B2 (fungsi mati dan buntu) beserta tes.
2. B4 dan B6 (keputusan satu sumber umur tanaman dan judul pengukuran).
3. Alur: Catat satu kali pilih GH, Evaluasi musim isi otomatis, Cek catatan dua tombol, Riwayat dikelompokkan.
4. U1 dan U3/U4 (ruang form, bahasa, format) dengan `impeccable clarify`, `layout`, lalu `polish`.
5. U2 (desain ulang Kelola) dan K1–K5 (penyajian keuangan, grafik biaya/laba).
