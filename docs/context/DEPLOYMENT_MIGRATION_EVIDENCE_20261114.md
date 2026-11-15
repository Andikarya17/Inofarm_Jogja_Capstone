# Bukti fase D1: penjaga migration dan migration 20 pada database aplikasi

Tanggal 14 November 2026. Branch `codex/deploy-migration-guard`, bertumpuk di atas `codex/deploy-baseline`. Rujukan: [bukti D0](DEPLOYMENT_BASELINE_EVIDENCE_20261114.md), [audit deployment](DEPLOYMENT_READINESS_AUDIT_20261114.md) temuan P0-1 dan P2-1.

Gerbang G1 dijawab pemilik: pilot memakai `inofarm_finance_local` di laptop, dan migration 20 boleh dijalankan setelah backup.

## Penjaga migration

Tidak ada kode baru. Laravel sudah menyediakan `php artisan migrate:status --pending=1`, yang keluar dengan kode 1 selama masih ada migration pending dan kode 0 bila bersih. Perintah itu hanya membaca. Opsi `--pending` tanpa nilai tidak mengubah kode keluar, jadi nilainya wajib ditulis `=1`.

- **Diuji di `inofarm_test`:** `backend/tests/Feature/MigrationPreflightTest.php` memastikan kode 0 saat bersih dan kode 1 setelah satu baris `migrations` dihapus di dalam transaksi tes. Tes ini mengunci perilaku itu terhadap upgrade framework (fase D2).
- **Diuji pada database aplikasi, sebelum migrate:** kode 1, menampilkan migration 20 Pending.
- **Diuji pada database aplikasi, sesudah migrate:** kode 0, "No pending migrations".

Batasnya: penjaga ini hanya bekerja bila dijalankan. Ia masuk checklist rilis di runbook fase D5 (setelah `migrate --force`, sebelum server melayani). Server tidak menolak sendiri bila langkah itu dilewati.

## Migration 20 pada `inofarm_finance_local` (diuji)

| Langkah | Hasil |
|---|---|
| `migrate:status` | Migration 20 Pending, 1 sampai 19 Ran |
| Backup `mysqldump` (`--single-transaction --routines --triggers`) | `outputs/rollout-20261114/inofarm_finance_local-20261114-120516.sql`, 116.408 byte, 54 `CREATE TABLE`, penanda "Dump completed" ada |
| SHA-256 backup | `82b67c34642b7039fc6eded6d9040ce0ea6a5419e680c900980a6cf378fef59c` |
| `migrate --pretend` | Hanya tambahan: kolom `harvests.recorded_by_role` (nullable), indeks `harvest_review_inbox`, tabel `harvest_reviews` dengan 2 FK dan 1 unique |
| `migrate` | `2026_11_10_000020_create_harvest_reviews` DONE, 129,56 ms |
| Jumlah baris sebelum dan sesudah (`db:show --counts`) | 53 tabel lama identik; `migrations` 24 menjadi 25; tabel baru `harvest_reviews` 0 baris |
| Struktur sesudah | 55 tabel; `harvest_reviews` 8 kolom, FK ke `harvests` dan `users` (restrict), unique (`harvest_id`, `harvest_version`) |

Folder `outputs/` diabaikan Git; backup tidak di-stage. Kredensial database dibaca skrip di dalam proses PHP dan tidak dicetak.

## Sinkronisasi pekerja pada database aplikasi (diuji, dengan batas)

Permintaan dijalankan lewat HTTP kernel Laravel di dalam proses, memakai akun pekerja aktif yang sudah ada, di dalam satu transaksi yang di-rollback. Token sementara ikut ter-rollback; pemeriksaan sesudahnya menemukan 0 token tersisa.

| Permintaan sebagai pekerja | Hasil |
|---|---|
| `GET /api/v2/sync/changes?activity_reviews=1&measurement_review_status=1&harvest_reviews=1` | 200, 1 greenhouse di scope, 15 baris data |
| `GET /api/v2/me` | 200, 1 greenhouse |

Ini membuktikan middleware, controller dan SQL berjalan pada schema database aplikasi. **Belum diverifikasi:** permintaan yang sama dari aplikasi Android lewat jaringan ke `php artisan serve`. Itu masuk dry run fase Q2.

## Belum dilakukan atau belum diverifikasi

- **Restore backup belum diuji.** Hash dan kelengkapan dump sudah diperiksa, tetapi dump belum pernah dipulihkan ke database terpisah. Uji restore masuk fase D3.
- **Panen lama tidak punya `recorded_by_role`.** Migration tidak mengisi kolom itu untuk baris yang sudah ada (nilainya null). Dibaca dari migration; dampaknya ke inbox cek panen untuk panen lama belum diperiksa.
- **P2-1:** masih ada 1 penugasan aktif milik akun pengelola (dihitung read-only). Penonaktifannya menunggu keputusan pemilik; lihat laporan fase.
- Tidak ada database lain yang disentuh. `inofarm_test` hanya dipakai PHPUnit.
