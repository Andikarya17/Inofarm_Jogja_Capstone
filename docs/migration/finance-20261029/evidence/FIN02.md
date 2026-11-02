# FIN02 backend auth/scope

29 Oktober 2026 sekitar 14:46 WIB; branch `codex/finance-auth-scope`, dependency FIN01 `0f5582a`. Publikasi GitHub sesuai koreksi pengguna, tanpa mengganti remote. Feature FIN02 parsial: server foundation pass, Android session/login UI dan administrasi akun teraudit menyusul.

Migration additive `2026_10_29_000001`: public UUID nullable + active/version pada users; tabel greenhouses dan assignment dengan FK restrict. Tidak mengubah role/public_id akun legacy atau mengkonversi tanks/roster/events. Migration hanya dijalankan suite pada guarded `inofarm_test`; belum diaplikasikan ke database aplikasi pengguna.

API tersedia: POST auth/login, POST auth/logout, GET me, GET greenhouses, GET greenhouses/{id} di `/api/v2`. Sanctum ability `finance-v2`, expiry 24 jam configurable `FINANCE_SESSION_SECONDS`; login bounded email/password, per-email+IP limiter 5 percobaan/menit dan route throttle. Token/password/SQL tidak masuk respons error. CLI `inofarm:finance-user {email} --name=... --role=worker|manager` meminta password tersembunyi, tidak auto-assign GH atau memodifikasi legacy. CLI belum dijalankan untuk akun nyata.

PASS: dua role/two-GH, manager tanpa akses global, input scope palsu tidak mengubah scope, detail cross-GH 404, assignment nonaktif/future/expired tidak terbaca, akun nonaktif 403 pada token existing, expiry/logout 401, wrong ability/legacy ditolak, token v2 ditolak v1, pagination limit<=100, structured errors dan payload limit. Q creator tetap sesi; belum ada finance/file/export endpoint untuk diuji. Tidak mengklaim U02 seluruhnya selesai.

Focused suite dan legacy regression: `scripts/test-backend.ps1` PASS **27 tests, 476 assertions**, 0 failure; guard 4/12 sebelum suite; PHP 8.5.11/MySQL dedicated inofarm_test. Pint scoped + git diff --check PASS. Log ignored `tmp/finance-auth-tests.txt`. Test pertama menemukan limiter 429 tidak mengirim Retry-After; header diperbaiki dan suite diulang lulus. Tidak ada dependency runtime baru. U01/U02/U18 bagian server diuji, device account-isolation belum diverifikasi.

Review: source/route/bootstrap/migration/test dan status diperiksa; hanya file task + status/evidence baru di-stage. Dokumen FIN00 pengguna tetap lokal. Berikut FIN03 master + audit; FIN05 outbox/receipt; FIN06 Compose slice.
