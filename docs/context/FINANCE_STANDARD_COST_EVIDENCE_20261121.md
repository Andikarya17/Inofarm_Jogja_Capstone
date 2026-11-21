# Bukti: Keuangan biaya standar, grafik pemasukan dan pembeli (21 November 2026)

Branch `keuangan-biaya-standar` dari `github/main` `670903d`. Desain: [spec](../superpowers/specs/2026-11-21-keuangan-biaya-standar-pembeli-design.md). Rencana: [plan](../superpowers/plans/2026-11-21-keuangan-biaya-standar-pembeli.md). Mockup arah: kanvas Inofarm Keuangan (artifact privat milik pengguna).

Label bukti dipakai seperti dokumen sebelumnya: **tes otomatis** (PHPUnit/JUnit), **backend Laravel nyata** (artisan serve `--env=testing` + MySQL `inofarm_test`), **emulator** (`Inofarm_Botanical_API30`, mode terang). **Tidak ada uji ponsel fisik, TalkBack, atau `inofarm_finance_local`.**

## Keputusan pengguna yang dijalankan

- FC dan VC adalah angka standar yang sama untuk setiap GH, tanpa isian per GH: satu lembar per **Musim ke-n** untuk seluruh kebun. Baris wajib FC: Biaya penyusutan, Sewa tanah, Tenaga kerja; VC: Cocopeat/rockwool, Nutrisi AB Mix, Nutrisi tambahan dan pestisida, Benih. Baris tambahan bisa dibuat dan dihapus.
- Laba, R/C dan BEP musim memakai biaya standar. Biaya nyata (pembayaran, alokasi, aset, penyusutan) tetap bisa dicatat, disembunyikan di balik **ikon mata**, dan tidak dijumlahkan.
- Grafik batang bertumpuk Mitra/Perorangan yang bisa diketuk, per minggu, bulan, tahun. Tanpa kurva laba kumulatif.
- Pembeli perorangan: daftar yang bisa diimpor dari Excel/CSV, dipilih di form penjualan, atau diisi baru. Mitra: grade A/B/C per kg. Retail: "Belum tersedia".

## Yang berubah

| Bagian | Isi |
|---|---|
| Migration 23 | `seasons.sequence` (backfill urut `start_date`, `id`), `season_cost_sheets`/`season_cost_lines`, `customers`, `sales.customer_id`, `sale_items.buyer_grade`. Additive, `down()` menolak |
| Backend | `SeasonCostSheetService` (lembar dibuat saat pertama dibaca, menyalin lembar n−1), laporan musim berbasis lembar (formula `season-standard-cost-rational-half-up-v2`; alokasi jadi `reference_only` dan dihitung di `source_counts.allocations`), `reports/income`, `reports/profit-summary`, `customers` + `customers/import` (batas 128 KiB khusus route ini), pembeli di payload sale, `buyer_grade` hanya untuk mitra. `MutationLedger` melewati `change_feed` untuk catatan tanpa greenhouse |
| Kontrak | OpenAPI (route baru, `SeasonsPublic.sequence`, `SalePublic.customer_id`, `SaleItem.buyer_grade`), `season-cost-sheet-operation.schema.json` + fixture, [STANDARD_COSTS.md](../../contracts/v2/STANDARD_COSTS.md), [CUSTOMERS.md](../../contracts/v2/CUSTOMERS.md), SEASON_EVALUATION.md |
| Android | Tab Keuangan baru (`feature/financehome`), Biaya standar, Ringkasan investor, ikon mata (preferensi per akun, bawaan tersembunyi), daftar pembeli + impor .xlsx/.csv tanpa dependency (`feature/customers`), pemilih pembeli dan grade mitra di form penjualan. Android kini menerima formula laporan v2 dan tetap membuka laporan v1 yang sudah terbit |

## Hasil tes otomatis (21 November 2026, branch ini)

- Backend `./scripts/test-backend.ps1`: guard 4/4, lalu **215 tes / 5.268 assertion OK** (dari 183 sebelum branch). Baru: `FinanceStandardCostTest` (15), `FinanceIncomeReportTest` (7), `FinanceCustomerTest` (8), `FinanceBuyerGradeTest` (4).
- Android `./scripts/build-android.ps1`: assemble OK, **JVM 174 tes, 0 gagal**, lint **0 error**, 19 warning.
- `scripts/check-finance-contracts.py`: semua blok PASS termasuk lembar biaya. `scripts/check-finance-context.py`: 0 error.
- Instrumentasi `FinanceEntryUiTest` **tidak dijalankan** pada branch ini.

## Uji emulator terhadap backend nyata

Fixture `scripts/finance-device-fixture.php create manager-home` (2 GH sintetis, akun pengelola), lalu data lewat API: panen Layak per GH, 7 penjualan Okt–Nov (perorangan, mitra, satu tanpa tipe), lembar Musim 1 berisi angka Inofarm. APK debug dengan `-PinofarmFinanceDebugApiUrl=http://127.0.0.1:8001/api/v2/` dan `adb reverse tcp:8001`. Port 8000 milik proses lain tidak disentuh. MySQL proyek (port 3307) sempat mati dan dinyalakan ulang dengan argumen `scripts/local-database.ps1` (hanya `mysqld`, tanpa provisioning).

| Cek | Hasil | Bukti |
|---|---|---|
| Grafik per bulan | Okt Rp9.110.000, Nov Rp6.696.000, "turun Rp2.414.000 dari Okt"; rincian Mitra/Perorangan/Tanpa tipe dan rata-rata Rp/kg | [01](evidence/keuangan-biaya-standar-20261121/01-grafik-bulan-nov.png), [02](evidence/keuangan-biaya-standar-20261121/02-batang-okt-diketuk.png) |
| Mengetuk batang | Nominal, perbandingan, rincian dan kartu per tipe pembeli berganti ke Okt (Mitra 72%) | 02 |
| Biaya standar | Diisi lewat layar; Ringkasan TC Rp11.011.100, ×2 GH Rp22.022.200. SQL: versi 2, jumlah baris aktif 11.011.100, **0 baris `change_feed`** | [03](evidence/keuangan-biaya-standar-20261121/03-biaya-standar-ringkasan.png) |
| Laba musim berjalan | TR Rp15.806.000, TC Rp22.022.200, **Rugi −Rp6.216.200** (merah), R/C 0,72, BEP Rp37.440/kg, 820 kg | [04](evidence/keuangan-biaya-standar-20261121/04-laba-musim-rugi.png) |
| Ringkasan investor | Musim 1: GH A Rp7,1 jt (−3,9 jt, R/C 0,65), GH B Rp8,7 jt (−2,3 jt, R/C 0,79) | [05](evidence/keuangan-biaya-standar-20261121/05-ringkasan-investor.png), [06](evidence/keuangan-biaya-standar-20261121/06-investor-per-gh.png) |
| Ikon mata | Kartu "Biaya nyata" muncul; tombolnya membuka layar pencatatan lama dengan "Kembali ke ringkasan keuangan" | [07](evidence/keuangan-biaya-standar-20261121/07-ikon-mata-biaya-nyata.png) |
| Daftar pembeli | Per pembeli: total kg dan jumlah beli | [08](evidence/keuangan-biaya-standar-20261121/08-daftar-pembeli.png) |
| Impor CSV | 4 baris dibaca, kolom ditebak; periksa: 2 baru, 1 sudah ada (`6281233334444` = `0812-3333-4444`), 1 nama kosong. SQL: 2 pembeli `import` dengan `62…` dan tampilan `0812-…` | [09](evidence/keuangan-biaya-standar-20261121/09-impor-csv-periksa.png) |
| Penjualan perorangan | Cari "sari" → pilih pembeli hasil impor; ringkasan menyebut pembeli dan tipe. SQL: Rp160.000, `consumer`, WA tersimpan. Grafik Nov naik ke Rp6.856.000 | [10](evidence/keuangan-biaya-standar-20261121/10-penjualan-perorangan-ringkasan.png) |
| Penjualan mitra | Pilih mitra terdaftar, dua baris dari satu sumber Layak: A 10 kg × 30.000, B 8 kg × 24.000 = Rp492.000. SQL: `partner`, `buyer_grade` A dan B | [11](evidence/keuangan-biaya-standar-20261121/11-penjualan-mitra-grade.png) |

Ditemukan dan diperbaiki selama uji emulator: judul ganda dan padding ganda di layar Keuangan, teks legenda terpotong, nominal rincian terpotong, rugi tampil "Rp-…" berwarna hijau (kini "Rugi −Rp…" merah, juga di ringkasan investor), grade mitra belum terlihat di Ringkasan penjualan, dan pembersih fixture yang tidak mengenal tabel baru (kini menghapus pembeli dan lembar milik fixture). Setelah uji, `inofarm_test` kosong lagi (users, greenhouses, sales, customers, lembar biaya, entity_identities = 0).

## Batas dan celah terbuka

- Belum diuji di ponsel fisik, TalkBack, mode gelap, font 200% atau layar 360 dp.
- Migration 23 **belum** diterapkan ke `inofarm_finance_local`. Perlu izin pengguna dan backup dulu.
- Form Biaya standar dan Impor tampil di dalam tab dengan dock tetap terlihat; Batal di Biaya standar langsung menutup tanpa dialog konfirmasi.
- Pemilih file sistem tidak bereaksi pada `adb input tap` di emulator ini; dipilih lewat D-pad/Enter. Ketukan jari biasa belum dibuktikan.
- Belum ada layar tabel transaksi per tipe pembeli; akumulasi ada di Daftar pembeli dan kartu "Per tipe pembeli".
- Unduh PDF ringkasan investor dan retail belum dikerjakan (di luar tenggat, sesuai rencana).
- Masih ditanyakan ke Inofarm: kolom Excel pembeli mereka dan aturan harga retail.
