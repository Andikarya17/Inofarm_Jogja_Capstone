# Bukti: Keuangan per musim dan pencatatan penjualan per baris (25 November 2026)

Branch `keuangan-per-musim-penjualan` (dasar `cf4bc57`, di atas commit Kelola `e778cec`). Desain: [spec](../superpowers/specs/2026-11-25-keuangan-per-musim-dan-penjualan-design.md). Rencana: [plan](../superpowers/plans/2026-11-25-keuangan-per-musim-dan-penjualan.md).

Label bukti: **tes otomatis** (PHPUnit/JUnit), **backend nyata** (`artisan serve --env=testing` port 8001 + MySQL `inofarm_test`), **emulator** (`Inofarm_Botanical_API30`, mode terang, `adb reverse tcp:8001`). **Tidak ada uji ponsel fisik, TalkBack, mode gelap, font 200%, atau `inofarm_finance_local`.**

## Keputusan pengguna yang dijalankan

- K1 FC/VC standar berlaku utuh untuk setiap GH (TC kebun = TC per GH x jumlah GH). K2 rasio (laba, R/C, B/C, BEP) dan "Penerimaan (TR)" dihapus dari layar; hanya FC, VC, TC dan penjualan. K3 tab Keuangan per Musim ke-n. K4 tiga tipe pembeli: Perorangan (harga tetap), Retail (harga ditawar per transaksi), Mitra (grade A/B/C). K5 satu penjualan berisi baris varietas + Layak/Cacat dengan kg, buah opsional, harga per kg.

## Yang dibangun

| Bagian | Isi |
|---|---|
| Migration 24 | `sale_lines`, `sale_items.sale_line_id` (backfill satu baris per item lama), `consumer_prices`, tipe pembeli `retail`. Additive, `down()` melempar |
| Server | Alokasi FIFO per baris (`SaleAllocator`, panen paling lama dulu, panen batal dikecualikan); payload `items` lama tetap diterima (409 untuk `harvest_exceeded` lama, 422 dengan alasan objek untuk `lines`); `consumer-prices`; `reports/season-overview` (menggantikan `profit-summary`); `reports/season-export` (.xlsx dari ekstensi `zip`/`xmlwriter`, tanpa dependency) |
| Android | Form penjualan per varietas + kualitas untuk Perorangan/Retail/Mitra dengan petunjuk harga, Ringkasan dan pratinjau stok FIFO; Retail di daftar pembeli; Harga perorangan di Kelola; tab Keuangan per musim tanpa laba/R/C/BEP; daftar penjualan musim; Unduh Excel |

## Tes otomatis (26 November 2026)

- Backend `./scripts/test-backend.ps1`: guard lulus, lalu **263 tes / 5.810 assertion OK** (220 / 5.333 sebelum branch ini).
- Android `./scripts/build-android.ps1`: assemble OK, **JVM 224 tes, 0 gagal**, lint **0 error**, 19 warning, 3 hint.
- `scripts/check-finance-contracts.py`: semua blok PASS (termasuk lembar biaya dan harga perorangan). `scripts/check-finance-context.py`: 0 error.
- Instrumentasi `FinanceEntryUiTest` penuh **tidak dijalankan** pada branch ini.

## Perbaikan review akhir (26 November 2026)

| Temuan | Perbaikan | Bukti |
|---|---|---|
| C1 penjualan sebelum migration 24 tidak bisa dikoreksi dari Android (409 `entity_exists`) | Server memetakan id item lama yang dikirim sebagai id baris ke baris hasil backfill (`SaleService::checkedLines`). Android memberi nama varietas pada penjualan lama dari cache panen (`SaleStock.withLines`) untuk daftar penjualan musim, petunjuk harga retail dan rincian | `test_backfilled_sale_corrected_with_old_item_id`, `test_backfilled_sale_with_two_items_same_grade_corrected_with_old_item_ids` (RED: 409 dan 422, GREEN sesudah perbaikan); JVM `backfilledSaleBodyWithItemsOnlyGetsVarietyNamesFromCache` |
| I1 Android menolak baris berulang yang sudah ada di server | Validasi melewati kunci berulang bila semua barisnya sudah ada di penjualan server (sama dengan aturan server) | JVM `repeatedKeyAllowedWhenEveryLineIsAlreadyOnTheServerSale` (RED lalu GREEN) |
| I2 restore terisolasi akan gagal (MySQL 3105) bila `consumer_prices` berisi | `RecoveryInventory` tidak memasukkan kolom `VIRTUAL/STORED GENERATED` ke paket (kolom `DEFAULT_GENERATED` tetap ikut); total desimal kini juga `sale_lines` dan `consumer_prices.price_per_kg`. Migration 24 tidak diubah | `FinanceRecoveryTest::test_backup_omits_generated_columns_and_restored_rows_insert` (RED: `active_key` ada di paket); drill restore terisolasi penuh tidak dijalankan |
| I3 "Musim belum dimuat" sesudah kembali dari rincian penjualan | Ringkasan musim terakhir disimpan di `FinanceHomeRepository` (memori, per akun dan sesi masuk); membuka rincian tidak lagi memindah halaman ke buku besar; halaman Penjualan musim menampilkan status memuat atau galat dengan "Coba lagi" | `FinanceOverviewUiTest.backFromSaleDetailKeepsSeasonSalesList` (RED di kode lama: daftar tidak kembali; GREEN), `seasonSalesWithoutSummaryShowsLoadingThenRetry`; emulator `Inofarm_Botanical_API30`, fixture |
| M1 `ensureFor` membaca ulang tanpa kunci | Pembacaan ulang sesudah unique violation memakai `lockForUpdate()` | backend penuh |
| Mitra ke tipe lain membuang baris B/C diam-diam | Dialog "Ganti ke Retail?" menyebut grade dan kg yang dihapus, pilihan "Ganti dan hapus" atau "Tetap Mitra" | JVM `leavingMitraNamesTheGradeLinesThatAreRemoved`, `FinanceEntryUiTest.saleLeavingPartnerConfirmsBeforeDroppingGrades` |

Hasil sesudah perbaikan: backend **266 tes / 5.856 assertion OK**; JVM **227 tes, 0 gagal**; lint 0 error, 19 warning, 3 hint; `assembleDebugAndroidTest` OK; emulator `FinanceOverviewUiTest` **7/7** dan 8 tes penjualan `FinanceEntryUiTest` **8/8** (fixture, bukan backend nyata); kontrak dan context check lulus.

## Uji emulator terhadap backend nyata

Fixture `scripts/finance-device-fixture.php create recording-rework` (dipakai karena GH A punya dua varietas; GH B satu varietas; GH C nonaktif; bukan `manager-home` yang hanya satu varietas per GH). Data lewat API: 9 panen (Layak dan Cacat, tiga tanggal per kelompok), lembar biaya Musim 1 (angka sintetis), dua pembeli (Retail, Mitra). APK debug dibangun dengan `-PinofarmFinanceDebugApiUrl=http://127.0.0.1:8001/api/v2/`. Port 8000 tidak disentuh. Seluruh penjualan, harga dan unduhan dilakukan lewat layar.

| Cek | Hasil layar | SQL / berkas | Bukti |
|---|---|---|---|
| Harga perorangan di Kelola | Alfa Layak Rp30.000, Beta Layak Rp28.000 tersimpan | `consumer_prices` 2 baris aktif, versi 1, harga sama | [01](evidence/keuangan-per-musim-20261125/01-kelola-harga-perorangan-menu.png), [02](evidence/keuangan-per-musim-20261125/02-harga-perorangan-daftar.png) |
| Penjualan Perorangan, harga terisi | Alfa 25 kg x 30.000 dan Beta 10 kg x 28.000 terisi dari Kelola ("Masih bisa diubah"), total Rp1.030.000 | `sales` consumer 1.030.000,00 / 35 kg; `sale_lines` 750.000 + 280.000 | [03](evidence/keuangan-per-musim-20261125/03-penjualan-perorangan-harga-terisi.png), [04](evidence/keuangan-per-musim-20261125/04-ringkasan-perorangan.png) |
| Retail dua varietas | Alfa 150 kg x 24.000 + Beta 60 kg x 22.000 = Rp4.920.000, pembeli terdaftar; petunjuk "Harga ditawar. Belum ada penjualan sebelumnya"; pada form berikutnya "Terakhir ke Toko ...: Rp24.000/kg" | `sales` retail 4.920.000,00 / 210 kg | [06](evidence/keuangan-per-musim-20261125/06-penjualan-retail-dua-varietas.png), [15](evidence/keuangan-per-musim-20261125/15-retail-petunjuk-harga-terakhir.png) |
| Mitra tiga grade | Gamma Layak: A 40 kg x 30.000, B 30 x 24.000, C 20 x 18.000 = Rp2.280.000, transfer | `sale_lines.buyer_grade` A/B/C; `sales` partner 2.280.000,00 / 90 kg | [07](evidence/keuangan-per-musim-20261125/07-penjualan-mitra-tiga-grade.png) |
| Stok FIFO | Ringkasan: "Stok panen sesudah disimpan 250 kg → 160 kg, diambil dari panen 4 Nov (90 kg)"; untuk retail Alfa "2 Nov (95 kg) dan 9 Nov (55 kg)" | `harvest_items.sold_weight_kg`: Alfa 2 Nov 120/120, Alfa 9 Nov 55/95, Beta 3 Nov 70/70 (perorangan 25 + retail 95 dari 2 Nov, retail 55 dari 9 Nov) | [05](evidence/keuangan-per-musim-20261125/05-ringkasan-stok-fifo.png) |
| Tab Keuangan per musim | Musim 1 berjalan, TC Rp22.022.200 (2 GH), FC 15.162.200, VC 6.860.000, per GH 11.011.100; tanpa laba, R/C, BEP | Lembar biaya sintetis: FC/GH 7.581.100 + VC/GH 3.430.000 = 11.011.100, x2 | [08](evidence/keuangan-per-musim-20261125/08-keuangan-per-musim.png) |
| Penjualan musim | Minggu 23 Nov Rp8.230.000: Mitra 2.280.000, Retail 4.920.000, Perorangan 1.030.000; GH A 5.950.000 (245 kg, 2 transaksi), GH B 2.280.000 (90 kg, 1 transaksi) | jumlah `sales.total_amount` 8.230.000, 335 kg, 3 transaksi | [09](evidence/keuangan-per-musim-20261125/09-keuangan-penjualan.png), [10](evidence/keuangan-per-musim-20261125/10-keuangan-penjualan-per-tipe.png) |
| Daftar penjualan, filter Retail | Semua: 3 transaksi, 335 kg, Rp8.230.000; Retail: 1 transaksi, 210 kg, Rp4.920.000 | cocok | [11](evidence/keuangan-per-musim-20261125/11-daftar-penjualan-musim.png), [12](evidence/keuangan-per-musim-20261125/12-daftar-penjualan-filter-retail.png) |
| Unduh Excel | Sheet pilihan, picker sistem dengan nama `inofarm-keuangan-musim-1-20261125.xlsx` (tombol SAVE bisa diketuk di emulator ini), pesan "Excel tersimpan" | File 4.209 byte ditarik dari perangkat dan dibuka (zip + XML): sheet "Ringkasan per GH" (FC 15.162.200, VC 6.860.000, TC 22.022.200, penjualan 8.230.000, 335 kg, 3 transaksi), "Rincian biaya" (7 baris, TC per GH 11.011.100, total kebun 22.022.200), "Daftar penjualan" (7 baris, total 8.230.000). Semua sama dengan layar dan SQL | [13](evidence/keuangan-per-musim-20261125/13-unduh-excel-sheet.png), [14](evidence/keuangan-per-musim-20261125/14-excel-tersimpan.png), [berkas](evidence/keuangan-per-musim-20261125/inofarm-keuangan-musim-1-20261125.xlsx) |

Setelah uji, fixture dibersihkan dan `inofarm_test` kosong lagi (users, greenhouses, sales, sale_lines, consumer_prices, customers, lembar biaya, entity_identities, audit_logs = 0; 60 tabel, 29 migration).

## Tes perangkat opt-in `FinanceBackendDeviceTest` (fase penjualan)

Fase lama belum pernah dijalankan sejak tag form berubah. Hasil terhadap backend nyata (fixture bawaan, offline barrier, restart proses):

| Fase | Hasil |
|---|---|
| `sale-source-queue` (pekerja mencatat panen Layak/Cacat offline) | **lulus** setelah harness diperbarui |
| `sale-queue` (pengelola membuat penjualan Perorangan dengan form baru, baris per varietas + kualitas, offline, lalu diputar ulang ke server) | **lulus** |
| `sale-verify` (kirim ulang, receipt `0.01`, lalu batalkan lewat riwayat) | **gagal di langkah navigasi**: tes masih membuka tab riwayat lama (`finance-history-list`) yang sudah tidak ada sesudah tab Keuangan dirombak. Penerimaan penjualan sebelum langkah itu lulus: SQL `sales` 1 baris aktif, versi 1, consumer, lunas, 0,01, 2,000000 kg |
| `verify` fixture (`scripts/finance-device-fixture.php verify`) | tidak dijalankan: ia mensyaratkan fase kegiatan awal (aktivitas, pembayaran, alokasi) yang tidak ikut dijalankan |

Perubahan harness (bukan kode produk): `FinanceBackendDeviceTest` fase `sale-source` memakai `openRecordIn("harvest", greenhouse)` dan form panen Layak/Cacat tanpa tombol tambah, grade, atau jam; fase `sale` membaca grade sistem dari `GET harvests/{id}` dengan token pengelola; `scripts/finance-device-fixture.php` cleanup kini juga menghapus `consumer_prices` milik fixture. Fase `sale-verify` (pembatalan lewat riwayat) **belum diperbarui** dan tetap celah terbuka. `scripts/test-finance-entry.ps1` tidak dapat dijalankan utuh di Windows PowerShell 5.1 (stderr `adb` dianggap error) dan butuh port 8001 bebas; fase dijalankan lewat skrip sementara. Catatan lingkungan: pada emulator API 30 ini shell `adb` tidak bisa menulis ke `/sdcard/Android/data/<paket>`; manifest fixture didorong sebagai root lalu `chown`/`chcon` ke pemilik aplikasi.

## Batas dan celah terbuka

- Belum diuji di ponsel fisik, TalkBack, mode gelap, font 200% atau layar 360 dp.
- `inofarm_finance_local` sudah dimigrasi ke 24 pada 26 November 2026 (lihat bagian di bawah). Aplikasi belum dibuka terhadap database ini sesudah migration, dan restore backup belum diuji.
- Snapshot laporan musim yang sudah terbit dan PDF/CSV-nya tidak diubah, jadi unduhan laporan lama masih memuat laba/R/C/BEP (pertanyaan spec 10.2 belum dijawab).
- `FinanceEntryUiTest` penuh tidak dijalankan; delapan tes penjualannya lulus di emulator sesudah perbaikan review akhir, tes lain belum.
- "Musim belum dimuat" sesudah kembali dari rincian penjualan yang dibuka dari daftar penjualan musim: **diperbaiki** pada perbaikan review akhir, bukti `FinanceOverviewUiTest.backFromSaleDetailKeepsSeasonSalesList` (fixture, emulator). Belum diulang terhadap backend nyata.
- Total musim dan daftar penjualan musim hanya menghitung greenhouse yang aktif sekarang: penjualan GH yang kemudian dinonaktifkan tidak ikut (disengaja, agar daftar sama dengan ringkasan server).
- Fase `sale-verify` `FinanceBackendDeviceTest` gagal di navigasi riwayat lama dan `verify` fixture tidak dijalankan.
- Penjualan lama (sebelum migration 24) yang dikoreksi dari Android: jalur server teruji PHPUnit, belum diuji di emulator terhadap backend nyata. Restore terisolasi penuh dengan `consumer_prices` berisi belum dijalankan (hanya paket dan insert ulang di tes).
- Minor dari review yang ditunda:
  - Hitungan penghitung pasca-alokasi pada jalur `lines` melempar 409 dengan alasan teks (SaleService); `fruit_count` menerima string numerik "38"; grade dari GH lain atau tak cocok kelompok memberi `harvest_exceeded 0` alih-alih error validasi; id baris lama nonaktif bisa dipakai ulang melewati cek tabrakan.
  - SALES.md baris 409 lama menyebut alasan teks padahal runtime tanpa `reasons`.
  - PATCH `consumer-prices` tanpa `record_state` mengaktifkan ulang harga nonaktif (Android selalu mengirimnya); tanpa tes replay, tabrakan aktivasi ulang dan PATCH oleh pekerja.
  - `selected` null bila belum ada nomor musim; `date_range.until` musim tertutup bisa lebih awal dari penjualan akhir; konsumen harus memakai satu sumber jumlah GH.
  - Excel: U+FFFE/FFFF, `tempnam` bisa gagal, harga ditampilkan `#,##0.00` menyembunyikan 6 desimal; belum dibuka di Excel sungguhan (hanya zip + XML dibaca).
  - Form: "Harga perorangan" berada di bagian Data master sendiri, bukan dekat Pekerja dan akun; pengisian ulang harga yang sengaja dikosongkan, Ganti GH pada draf yang dibuka ulang meninggalkan draf lama, kredit ditambahkan pada baris saldo basi; tidak ada tes untuk nama pembeli bebas pada penjualan lama tanpa tipe.
  - Tab Keuangan: penjualan lokal belum terkirim tidak masuk daftar penjualan musim (pilihan produk perlu dikonfirmasi); teks rumus/asumsi laporan dari server belum diperiksa penuh untuk kata rasio; nama parameter `stackHeights`; motif daun melewati angka per GH.
  - Unduh Excel: pratinjau nama berkas memakai tanggal perangkat; sheet muncul lagi di belakang picker setelah rotasi; recreate activity saat picker terbuka membuang berkas.
  - Sumber androidTest tidak dikompilasi oleh skrip build biasa (`assembleDebugAndroidTest` dijalankan manual).

## Penerapan ke database aplikasi, 26 November 2026

Atas permintaan pengguna, dengan backup lebih dulu. Koneksi efektif `backend/.env`: MySQL `127.0.0.1:3307`, database `inofarm_finance_local`.

| Langkah | Hasil |
|---|---|
| Sebelum | `migrate:status --pending=1`: hanya `2026_11_25_000024_sale_lines_retail_consumer_prices` tertunda; 58 tabel, 28 migration; `sales` 1, `sale_items` 1, `customers` 1, `harvest_items` 4 baris |
| Backup `scripts/backup-mysql.ps1` | `outputs/rollout-20261126/inofarm_finance_local-20261126-083908.sql`, 472.225 byte, 58 `CREATE TABLE`, penanda "Dump completed" ada |
| SHA-256 backup | `ce3c96ad21c6d9834e91ac1a845224a8c11e90de895f42ef35434825e9245ac0` |
| `php artisan migrate --force` | migration 24 DONE (285 ms); `migrate:status --pending=1` sesudahnya: tidak ada yang tertunda |
| Sesudah | 60 tabel, 29 migration; `sales`/`sale_items`/`customers`/`harvest_items` jumlah baris tidak berubah; `sale_lines` 1, `consumer_prices` 0 |
| Backfill | `sale_items` tanpa `sale_line_id`: 0; item yang menunjuk baris tak ada: 0; id baris = `SaleLineMigration::lineId(sale_item.id)`; kg 10, harga 35.000/kg, `exact_amount` 350.000 sama dengan item dan `sales.total_amount` |
| CHECK `customer_values` | tipe `consumer`, `retail`, `partner` |

Belum: membuka aplikasi terhadap database ini sesudah migration, mengisi harga perorangan, dan uji restore backup 26 November.
