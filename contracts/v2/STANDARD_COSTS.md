# Biaya standar, pemasukan per tipe pembeli dan ringkasan laba

Keputusan pengguna 20–21 November 2026: biaya tetap (FC) dan biaya variabel (VC) adalah angka standar yang **sama untuk setiap greenhouse**, tanpa isian per greenhouse. Desain: `docs/superpowers/specs/2026-11-21-keuangan-biaya-standar-pembeli-design.md`.

## Lembar biaya standar

- Satu lembar per **nomor musim** (`seasons.sequence`, Musim ke-n per greenhouse) untuk seluruh kebun. Setiap musim greenhouse bernomor n memakai lembar n.
- Lembar n dibuat server saat pertama kali dibaca (daftar lembar atau laporan musim). Isinya menyalin baris dan jumlah lembar sebelumnya; bila belum ada lembar sama sekali, tujuh baris wajib dengan jumlah kosong.
- Baris wajib: FC `Biaya penyusutan`, `Sewa tanah`, `Tenaga kerja`; VC `Cocopeat/rockwool`, `Nutrisi AB Mix`, `Nutrisi tambahan dan pestisida`, `Benih`. Nama dan kelasnya tidak bisa diubah; baris wajib tidak bisa dihapus. Baris tambahan boleh ditambah, diganti nama dan dihapus. Nama unik dalam lembar tanpa membedakan huruf besar-kecil.
- `amount` adalah string desimal IDR 2 angka atau `null`. `null` berarti belum diisi dan **tidak** dianggap nol: total kelasnya `null`, TC `null`, `complete: false`.
- `totals` = FC dan VC per greenhouse; TC = FC + VC. Biaya kebun untuk Musim n = TC × `greenhouse_count`.
- Server tidak pernah mengisi angka. Angka Inofarm hanya muncul sebagai contoh di fixture.

| Endpoint | Keterangan |
|---|---|
| `GET season-cost-sheets[?sequence=n]` | Semua lembar, terbaru dulu; membuat lembar untuk setiap nomor musim yang sudah ada |
| `GET season-cost-sheets/{id}` | Detail |
| `PATCH season-cost-sheets/{id}` | Envelope `entity_type: season_cost_sheet`, `action: update`, `base_version`; payload `lines` lengkap ([schema](season-cost-sheet-operation.schema.json), [fixture](fixtures/season-cost-sheet-operation.json)). 409 `season_closed` bila ada greenhouse **aktif** yang sudah menutup musim bernomor n (greenhouse nonaktif tidak mengunci). Respons lembar memuat `locked` dan `closed_greenhouse_names` agar Android memberi tahu sebelum mengedit. Musim lama yang sudah ditutup sebelum migration 23 diisi dengan membuka kembali musimnya, mengisi lembar, lalu menutupnya lagi; 409 `version_conflict`; 422 untuk baris wajib yang dihapus/diganti dan nama ganda |
| `GET season-cost-sheets/{id}/audit` | Audit per versi |

Semua route khusus pengelola. Lembar tidak terikat ke greenhouse, sehingga tidak masuk `sync/changes`; audit dan receipt tetap ditulis. Android membacanya online.

## Laporan musim

Sejak formula `season-standard-cost-rational-half-up-v2`, FC dan VC laporan musim diambil dari lembar biaya standar musim itu (sumber `season_cost_sheet`, `role: season_cost_basis`). Alokasi biaya dan alokasi penyusutan tetap dicantumkan sebagai `reference_only` dan dihitung di `source_counts.allocations`, tetapi tidak dijumlahkan. Preview dan snapshot memuat `cost_basis: "standard"`, `cost_sheet` (id, sequence, version) dan `cost_gaps` (nama baris yang masih kosong). Laporan terbit sebelumnya tidak berubah. Sumber `season_cost_sheet` hanya memuat id, sequence, version, lines dan totals, sehingga perubahan greenhouse lain tidak mengubah `sources_hash`. `reviewed_final` ditolak (`report_review_incomplete`) selama `cost_gaps` belum kosong.

## Pemasukan per tipe pembeli

`GET reports/income?granularity=week|month|year&from=YYYY-MM-DD&until=YYYY-MM-DD[&greenhouse_id=]`

- Hanya penjualan aktif dan lunas. Bucket WIB: minggu mulai Senin (`key` = tanggal Senin), bulan (`YYYY-MM`), tahun (`YYYY`). Maksimal 104 bucket (lebih: 422).
- Setiap bucket: `receipts`, `weight_kg`, dan `by_type.consumer|partner|unspecified`. Penjualan lama tanpa `customer_type` masuk `unspecified`.

## Ringkasan laba

`GET reports/profit-summary[?greenhouse_id=]` menghitung langsung (bukan laporan terbit) dengan rumus laporan musim:

- `open`: semua musim yang sedang terbuka di greenhouse aktif: TR, TC, laba, R/C, BEP harga, BEP kg, kg terjual, `complete`.
- `by_sequence`: per Musim ke-n lintas greenhouse.
- `by_greenhouse`: per musim greenhouse.

Penjualan yang belum ada dihitung nol; biaya yang belum lengkap membuat TC, laba dan rasio `null` dengan `complete: false`.
