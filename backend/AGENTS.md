# Backend Inofarm: transisi operasional dan keuangan

Ikuti AGENTS root/context aktif29 Oktober 2026 dan docs/migration/finance-20261029. Backend masih v1 owner/tank/device; target auth pekerja/pengelola scoped greenhouse, activities/harvest, expenses/allocation/assets/sales, evaluation/investment, sync/audit/report/recovery.

API_V2/DATA_CONTRACTS memuat rancangan lengkap; subset core/master/reference/activity sudah executable, lihat contracts/v2/, routes/api_v2.php dan docs/context/IMPLEMENTATION_STATUS.md. Migration additive; jangan ubah makna histori atau auto-convert device events. Domain services/policy/validation; fixed decimal, transaction locks, idempotent operation/base_version, audit dan snapshots immutable. Actor dari sesi; file/sync/export scoped.

Test wajib effective MySQL inofarm_test; guard sebelum SQL/provider. Jangan RefreshDatabase/migrate:fresh/reset/seed pada inofarm_local/data pengguna; jangan disable guard. Concurrency dibuktikan MySQL, bukan mock saja.

Jangan install Laravel Boost/dependency hanya rekomendasi skeleton. Branch per fitur/Git authorization mengikuti root dan koreksi pengguna: remote github. Publikasi bukan deploy/destructive operation. FIN00 dokumentasi tetap historis; implementasi aktif mengikuti master prompt dan evidence baru.
