# Backend Inofarm: transisi operasional dan keuangan

Baseline setelah remediasi10 November2026 mengikuti [bukti penyelesaian](../docs/context/AUDIT_REMEDIATION_20261110.md). Database aplikasi lokal sudah migration19, parity route OpenAPI lengkap, filter latest mengikuti review dan cache konteks membaca seluruh halaman. Status audit lama di bawah harus dibaca sebagai checkpoint sebelum perbaikan. QA/keputusan produksi tetap mengikuti gate yang tercatat.

Ikuti AGENTS root/context aktif29 Oktober2026 beserta keputusan pengguna2 November2026. Produk aktif hanya API v2 pekerja (scoped penugasan) dan pengelola (super admin, semua greenhouse), activities/pengukuran manual/harvest, expenses/allocation/assets/sales, evaluation/investment, sync/audit/report/recovery. Route/source owner/device v1 dilepas; tabel/migration historis tetap dipertahankan.

Baseline 10 November 2026 source `9a1613e` sudah mencakup core/master/reference/activity/review/harvest/expense/allocation/sale/asset/evaluation/investment/export/recovery dan native terkait; acceptance tidak semuanya selesai. Baca CLAUDE.md root, docs/context/SYSTEM_AUDIT_20261110.md, contracts/v2/ dan routes/api_v2.php. API_V2/DATA_CONTRACTS lama mencakup rancangan, bukan nama route/tabel fisik yang selalu tepat. OpenAPI belum memuat 25 operasi runtime.

Source sampai migration finance 19, tetapi audit read-only menemukan database aplikasi efektif inofarm_finance_local masih 16. Jangan menganggap kelulusan inofarm_test membuktikan schema aplikasi terbaru; jangan auto-migrate/reset data pengguna dalam task audit. Migration additive; jangan ubah makna histori atau auto-convert device events. Domain services/policy/validation; fixed decimal, transaction locks, idempotent operation/base_version, audit dan snapshots immutable. Actor dari sesi; file/sync/export scoped.

Test wajib effective MySQL inofarm_test; guard sebelum SQL/provider. Jangan RefreshDatabase/migrate:fresh/reset/seed pada inofarm_local/data pengguna; jangan disable guard. Concurrency dibuktikan MySQL, bukan mock saja.

Entity UUID v2 global: root create dan inline children/balance harus claim EntityIdentity dalam transaksi/lock ledger; tambahkan source kind saat memperluas modul. Jangan melepas reservation saat cancel/deactivate atau mengubah ID secara otomatis. Schema8 backfill memerlukan preflight/maintenance; CLI identity check read-only. Synthetic cleanup hanya UUID milik fixture yang telah diverifikasi.

Jangan install Laravel Boost/dependency hanya rekomendasi skeleton. Branch per fitur/Git authorization mengikuti root dan koreksi pengguna: remote github. Publikasi bukan deploy/destructive operation. FIN00 dokumentasi tetap historis; implementasi aktif mengikuti master prompt dan evidence baru.
