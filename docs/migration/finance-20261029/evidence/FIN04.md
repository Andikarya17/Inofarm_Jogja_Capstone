# FIN04 backend/domain

29 Oktober 2026 sekitar 15:02 WIB, branch `codex/finance-schedules-targets`, dependency FIN03 `2b0ebb0`. Schema additive schedules/targets/immutable reference_versions hanya dijalankan guarded MySQL inofarm_test. Contract `contracts/v2/REFERENCES.md` + OpenAPI. Tidak ada dependency baru/production thresholds.

PASS backend **38 tests, 605 assertions** via scripts/test-backend.ps1 (guard awal4/12). Bukti U05: hari pertama1, sebelum pindah HSTnull, pergantian midnight WIB dari UTC, leap day, sebelum semai unavailable. U06: actual tidak diganti, missing/single/range/tolerance inclusive, decimal invalid/unit/pH invalid ditolak, overlap target ditolak, versi lama tetap900 setelah update1000, pemilihan berdasarkan waktu kegiatan, worker cross-group denied, jadwal tidak punya completion otomatis. Snapshot service diuji; snapshot activity persistent baru FIN05/06 sehingga belum mengklaim integrasi U06 lengkap.

PASS Android assembleDebug/testDebugUnitTest/lintDebug, 15 detik, 13 unit test tanpa failure/error, termasuk preview umur. Log ignored `tmp/finance-reference-android.txt`; backend `tmp/finance-reference-tests.txt`. Pint dan diff whitespace PASS. Fixture semuanya sintetis. Device/process-death/IME/performance belum diuji.

Test awal mendeteksi route wildcard yang tertimpa resource master; route reference diberi identitas placeholder terpisah dan suite diulang lulus. Review menemukan trim middleware dapat menyamakan isi retry berbeda; v2 mempertahankan whitespace/empty string dan test regression memastikan conflict. Source v1 dipertahankan dan suite lamanya lulus.

FIN04 fitur keseluruhan PARTIAL sampai UI/acuan activity integration dan revisi historis eksplisit tersedia. Next durable local DB/outbox, activity server receipt/pull, lalu Compose entry. Q04/Q05 tetap tidak otomatis disahkan.
