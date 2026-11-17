# Audit alur input pengelola: draf, Periksa dan simpan langsung (17 November 2026)

Pertanyaan pengguna: setelah form admin/master dibuat langsung simpan (PR #66), masih adakah input pengelola yang harus lewat draf atau langkah Periksa dan tidak bisa langsung disimpan? Kalau ada, apakah memang sebaiknya begitu?

Cakupan: source Android dan backend pada `github/main` `52c8ab9` (setelah PR #70). Metode: membaca source dan route. Tidak ada kode yang diubah, tidak ada tes atau emulator yang dijalankan untuk audit ini.

## Ringkasan

- **Server tidak punya status draf.** Semua `POST`/`PATCH` langsung commit dalam satu transaksi (mutasi + audit + receipt + feed). Draf hanya ada di Android (`SyncState.LOCAL` di SQLite terenkripsi). Satu-satunya langkah dua tahap di server adalah laporan musim dan laporan investasi: `POST season-reports` mewajibkan `expected_sources_hash` dan `expected_formula_version` dari pratinjau.
- **Masih ada delapan form pengelola yang wajib lewat Periksa:** kegiatan, pengukuran, panen, pengeluaran, alokasi biaya, penjualan, aset dan alokasi penyusutan. Polanya: form → `Simpan draf` | `Periksa` → layar Periksa → `Simpan` → antrean (outbox) → terkirim otomatis.
- Yang sudah langsung: admin/master/akun/penugasan/jadwal/target (tanpa draf sejak 17 November), wizard greenhouse, tanam saat ini, tutup/buka musim, tandai panen selesai/buka kembali, keputusan cek catatan, skenario investasi, pembatalan catatan.

## Matriks input pengelola

| Input | Layar / file | Alur di Android | Server | Penilaian |
|---|---|---|---|---|
| Greenhouse, musim, kelompok, varietas, bahan, satuan, grade, kategori biaya, penugasan, jadwal, target | `admin/AdminScreen.kt`, `AdminViewModel.kt` | Langsung `Simpan` (online). Catatan lokal terenkripsi hanya untuk kirim ulang dengan `operation_id` sama | `POST`/`PATCH {resource}` | Sudah benar |
| Akun (buat, ubah, reset password) | `AdminScreen.kt` | Langsung, online | `POST`/`PATCH users` | Sudah benar |
| Tambah greenhouse | `admin/GreenhouseWizard.kt` | Tiga langkah, tiap langkah `Simpan dan lanjut` | Beberapa `POST` berurutan | Sudah benar |
| Tanam saat ini | `managerhome/RunningPeriodSheet.kt` | Pilih → `Simpan`, online | `POST greenhouses/{id}/running-period` | Sudah benar |
| Tutup / buka musim | `SeasonActions` di `history/HistoryScreen.kt` | Dialog dengan catatan opsional | `POST seasons/{id}/close\|reopen` | Sudah benar |
| Tandai panen selesai / buka kembali | `harvest/HarvestProgressScreen.kt` | Dialog konfirmasi, masuk antrean | `POST planting-groups/{id}/harvest-completion` | Sudah benar |
| Keputusan cek catatan (Sesuai / Perlu diperbaiki) | `verification/ActivityVerificationScreen.kt` `DecisionEditor` | `Simpan keputusan` langsung ke antrean; ada juga tombol `Simpan draf` dan autosave 350 ms | `POST activity-reviews`, `harvest-reviews` | Benar; tombol `Simpan draf` tidak perlu (lihat R3) |
| Pembatalan catatan | `history/HistoryScreen.kt` | Dialog alasan/catatan → antrean | `POST {resource}/{id}/cancel` | Sudah benar |
| Skenario investasi | `investments/InvestmentScreen.kt` | `Simpan skenario` langsung ke antrean, `Simpan dan kembali` untuk draf | `POST`/`PATCH investment-scenarios` | Sudah benar |
| **Kegiatan dan pengukuran** | `FinanceRoot.kt` `ActivityForm` / `ActivityReview` | **Form → `Periksa` → `Simpan`** | `POST activities` (via `sync/operations`) | Periksa tidak perlu wajib (R1) |
| **Panen** | `harvest/HarvestScreen.kt` | **Form → `Periksa` → `Simpan`** | `POST harvests` | Periksa tidak perlu wajib (R1) |
| **Pengeluaran, alokasi biaya** | `expenses/FinancialEntryScreen.kt` | **Form → `Periksa` → `Simpan`** | `POST expenses`, `cost-allocations` | Periksa boleh tetap, tetapi sebaiknya ringkasan di form (R2) |
| **Penjualan lunas** | `sales/SaleEntryScreen.kt` | **Form → `Periksa` → `Simpan`** | `POST sales` | Sama dengan pengeluaran (R2) |
| **Aset, alokasi penyusutan** | `assets/AssetEntryScreen.kt` | **Form → `Periksa` → `Simpan`** | `POST assets`, `depreciation-allocations` | Sama dengan pengeluaran (R2) |
| Laporan musim | `reports/SeasonReportScreen.kt` | `Lihat dulu` (pratinjau server) → `Simpan` | `POST season-reports/preview` → `POST season-reports` dengan hash | **Harus tetap dua tahap** |
| Laporan investasi | `investments/InvestmentScreen.kt` | `Lihat dulu` → `Simpan` | `POST investment-reports/preview` → `POST investment-reports` | **Harus tetap dua tahap** |
| Lampiran | `attachments/AttachmentScreen.kt` | Pilih berkas → `Cek lampiran` → `Simpan lampiran` | `POST attachments` (multipart) | Wajar: pengguna perlu melihat berkas yang benar sebelum diunggah |

## Kenapa delapan form itu masih memakai draf

Draf di form domain **bukan** sisa yang lupa dibersihkan seperti draf admin. Itu bagian dari desain offline-first:

1. KNF-03 meminta draf dan antrean bertahan setelah aplikasi ditutup, dimatikan, atau dibuka offline. Pencatatan lapangan sering tanpa sinyal.
2. Rekaman lokal dan operasi outbox ditulis dalam satu transaksi SQLite. UUID dan hash envelope dipertahankan sehingga kirim ulang idempoten.
3. Form domain boleh disimpan parsial (misalnya pengukuran tanpa PPM) sebagai draf; validasi penuh hanya saat diantrekan.

Berbeda dengan admin: tulis admin selalu butuh koneksi dan tidak pernah masuk outbox, jadi kata "draf" di sana menyesatkan dan sudah dihapus. Di form domain, draf memang menyelamatkan isian.

Yang **tidak** diwajibkan oleh dokumen apa pun adalah layar Periksa terpisah untuk pengelola. PRD hanya menulis "formulir → review → simpan draf atau antrekan" pada alur pekerja ([PRD](PRD.md), bagian alur). Pengelola mengisi lebih banyak data per hari dan bisa mengoreksi atau membatalkan dengan audit, jadi langkah tambahan ini terutama menambah ketukan.

## Rekomendasi

Keputusan pengguna pada hari yang sama melampaui R1–R4: semua input pengelola memakai Batal / Simpan lalu Ringkasan, termasuk form keuangan dan admin. Hasilnya di [bukti alur Ringkasan](MANAGER_SAVE_SUMMARY_EVIDENCE_20261117.md). Tabel di bawah dipertahankan sebagai dasar pertimbangan.

| # | Prioritas | Usulan | Alasan |
|---|---|---|---|
| R1 | P1 | Kegiatan, pengukuran dan panen pengelola: tombol utama `Simpan` memvalidasi lalu langsung mengantrekan, tanpa layar Periksa. Galat tampil di isian. Setelah tersimpan, form tertutup dengan pesan "Tersimpan. Terkirim otomatis saat ada internet." | Data operasional, sering, mudah dikoreksi (revisi baru + audit). Langkah Periksa menggandakan ketukan tanpa mencegah kesalahan yang tidak bisa dicegah validasi |
| R2 | P2 | Pengeluaran, alokasi, penjualan, aset, penyusutan: ganti layar Periksa dengan **kartu ringkasan di bagian bawah form** (nominal, greenhouse/musim, sumber) dan satu tombol `Simpan`. Bila ingin tetap ada rem, cukup untuk nominal besar atau penjualan yang menghabiskan sisa panen | Nominal uang, saldo panen dan anggaran penyusutan dikunci server, jadi Periksa bukan penjaga integritas; ringkasan di form tetap memberi kesempatan membaca angka sekali |
| R3 | P3 | Hapus tombol `Simpan draf` pada keputusan cek catatan; autosave 350 ms sudah menjaga isian | Dua tombol simpan untuk satu keputusan membingungkan |
| R4 | P2 | Tombol kiri form domain pengelola: ganti `Simpan draf` menjadi `Batal` dengan pertanyaan "Simpan sebagai draf?" (Simpan draf / Buang) hanya bila ada perubahan. Autosave tetap berjalan | Menyamakan pola dengan form admin; draf tetap ada untuk offline |
| — | Tetap | Laporan musim dan investasi tetap `Lihat dulu` → `Simpan` | Laporan terbit immutable; server menolak simpan tanpa hash sumber dari pratinjau |
| — | Tetap | Alur pekerja tetap memakai `Simpan sebagai draf` dan `Periksa` → `Kirim` | Ada di PRD, pekerja lebih sering offline, dan hasil kirim pekerja perlu dicek pengelola |

Dampak teknis bila R1/R2 dikerjakan: hanya Android. ViewModel sudah punya `queue()` yang memvalidasi lalu mengantrekan (`ActivityViewModel.queue`, `AssetEntryViewModel.queue`, dan seterusnya), jadi tombol utama cukup memanggil `queue()` alih-alih `review()`. `FinanceRoot.kt` (urutan `FinanceDestination.Review`) dan composable `*Review` menjadi tidak dipakai untuk pengelola. Tag tes `*-review`, `*-queue`, `finance-review`, `finance-queue` dipakai `FinanceEntryUiTest` dan perlu disesuaikan; bandingkan per nama tes karena suite ini sudah punya kegagalan baseline.

## Temuan lain dari audit yang sama

| # | Prioritas | Temuan | Bukti |
|---|---|---|---|
| A1 | P1 (laten) | Backend sejak migration 21 mengizinkan `fruit_count: null` pada rincian panen dan target panen hanya kg. Android masih membaca dengan `getLong`/`getInt`, yang melempar `JSONException` saat komposisi bila nilainya `null`, sehingga layar crash. Saat ini belum terjadi karena Android sendiri selalu mengirim jumlah buah; risiko muncul bila data dibuat lewat API, fixture, atau klien lain | `harvest/HarvestProgressScreen.kt:138,150,154,159`, `harvest/HarvestScreen.kt:92,208`, `harvest/WorkerHarvestProgress.kt:194,235`, `managerhome/ManagerHomeScreen.kt:223` |
| A2 | P2 | Fitur backend 17 November belum dipakai Android: salin master antar GH (`copy-masters`), alokasi biaya lintas GH dan usulan bagi rata, pindah musim alokasi, cash flow bulanan + CSV, perbandingan musim, pemakaian bahan per musim, `payment_method`/`payment_reference`/`customer_type` penjualan, `measurement_moment`. Pemilih musim alokasi masih difilter ke GH pembayaran | `expenses/FinancialEntryScreen.kt:226`; tidak ada referensi rute baru di `android/app/src/main` |
| A3 | P3 | Tutup musim kini bisa ditolak `season_unsold_balance` (422) bila masih ada sisa panen belum terjual tanpa catatan. Android menampilkan pesan server, tetapi dialog tutup musim belum menjelaskan bahwa catatan wajib dalam kasus itu | `history/HistoryScreen.kt:462` `SeasonActions`; [bukti backend](FIELD_ALIGNMENT_BACKEND_EVIDENCE_20261117.md) |

## Batas

Audit membaca source, bukan menjalankan aplikasi. Klaim "crash" pada A1 berasal dari perilaku `org.json` (`getLong` pada `null` melempar), belum direproduksi di emulator. Rekomendasi R1–R4 adalah usulan UX dan belum menjadi keputusan pengguna.
