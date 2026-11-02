# FIN05 server slice

29 Oktober 2026 sekitar 15:12 WIB, branch `codex/finance-sync-server`, dependency FIN04 `83c5a61`. Feature FIN05 masih PARTIAL sampai Android durable local/outbox/retry/process-death/isolation terbukti. Server activity create/update/cancel/history disiapkan lebih awal sebagai dependency vertical slice FIN06/07.

Migration additive000004 activity/material/snapshot, tidak mengubah legacy; guarded MySQL inofarm_test saja. API/contract `contracts/v2/ACTIVITY_SYNC.md` dan action-specific schema. Tidak ada migration database pengguna, token akun nyata, deploy atau clear app.

PASS `scripts/test-backend.ps1`: **44 tests,708 assertions** dengan guard awal4/12. Test baru: worker create+creator dari sesi, normal/sync replay receipt sama, same-ID/different-content409, forged creator422, cross-GH detail/mutation404, dua material, missing vs zero, nonfinite/negative/ph/float/type validation, skipped reason, own/other edit, manager cancel, stale version, closed queue, audit, snapshot tanggal29→preserve29→explicit refresh28 dengan versi lama tetap29. Pull upper sequence stable meski ada write baru, audience manager tidak di worker, cursor tamper/account mismatch/old epoch ditolak. Fault injection sesudah SQL receipt membuktikan rollback mutation/snapshot/audit/feed/receipt, bukan hanya exception sebelum write.

PASS `.tools/php/php.exe scripts/test-finance-concurrency.php`: **dua proses PHP+dua koneksi MySQL8.0.30 independen**, barrier melepaskan koreksi base_version1 bersamaan; satu accepted, satu version_conflict, versi akhir2, audit/feed/receipt masing-masing2 (create+accepted update). Database terverifikasi inofarm_test; fixture spesifik dibersihkan finally. Ini bukti race optimistic version U08 bagian domain, bukan oversell/alokasi uang atau seluruh HTTP/device race.

PASS JSON Schema/fixture/local OpenAPI refs, Pint scoped, git diff whitespace. Log ignored `tmp/finance-sync-server-tests.txt`, `tmp/finance-sync-concurrency.txt`. Percobaan awal script race gagal sebelum SQL karena PHP imports terletak setelah pemakaian (hasil formatter); imports dipindah ke awal dan rerun pass. Tidak ada klaim gagal tersebut sebagai pass.

U04/U06/U07/U08/U17/U18 backend sebagian terbukti. Retry diuji dengan membuang hasil lalu mengulang operasi pada API; belum fault jaringan device sungguhan. Process death, DB lokal migration, ACK lama dan account switching masih FIN05 lokal. Business gates dan KF07–14 finance/report/recovery masih belum tersedia.
