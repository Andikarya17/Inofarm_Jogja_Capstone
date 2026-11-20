# Pembeli dan impor Excel

Keputusan pengguna 20–21 November 2026. Daftar pembeli berlaku untuk seluruh kebun dan hanya untuk pengelola (pekerja 403). Pembeli tidak terikat greenhouse, sehingga tidak masuk `sync/changes`; setiap pembeli baru tetap menulis audit `customer` `create`.

- `type`: `consumer` (perorangan) atau `partner` (mitra/tengkulak). `retail` belum ada.
- Nomor WhatsApp disimpan sebagai digit `62…` (`0812-3456 7890`, `+62 812 3456 7890` dan `6281234567890` sama). Nomor tidak valid (huruf, kurang dari 10 atau lebih dari 15 digit) ditolak; kosong boleh. Satu nomor hanya sekali per tipe.

| Endpoint | Keterangan |
|---|---|
| `GET customers?type=&q=&limit=&cursor=` | Cari nama, atau digit WhatsApp bila `q` berisi minimal 3 digit. Setiap baris membawa `purchase_count`, `total_kg`, `last_purchase_at` dari penjualan aktif |
| `GET customers/{id}` | Detail |
| `POST customers/import` | `{dry_run, type, rows:[{row, name, whatsapp}]}`, maksimal 1.000 baris dan 128 KiB (pengecualian batas 16 KiB). Hasil per baris `created`, `existing` (nomor sudah ada, dilewati) atau `invalid` dengan alasan. `dry_run: true` tidak menulis apa pun |

## Pembeli di penjualan

Payload `sales` boleh membawa `customer: {id, type, name, whatsapp}`:

- `id` sudah ada dan aktif dengan tipe yang sama: penjualan merujuknya; `customer_name` diisi nama tersimpan.
- `id` baru: server membuat pembeli dalam transaksi penjualan yang sama (sumber `manual`). Bila nomornya sudah dimiliki pembeli lain bertipe sama, pembeli itu yang dipakai.
- `sales.customer_id`, `customer_type` dan `customer_name` (salinan saat penjualan) terisi. Penjualan tanpa `customer` berjalan seperti sebelumnya.
