# FIN16 — integrasi profil dan job sinkronisasi

2 November 2026 WIB; branch `codex/finance-background-sync`, parent `8f959f8`. [Kontrak/perilaku/rujukan Android](../../../../contracts/v2/BACKGROUND_SYNC.md). Tidak ada dependency atau migration baru.

`SyncStatusController` memakai consistency boundary ScopedRead dan menyertakan identitas publik terkini. Coordinator memverifikasi akun/peran sebelum initialize/pull/send; vault StateFlow memperbarui batas akses Compose. JobScheduler persistent/network-any memakai coordinator/outbox existing, tanpa credential/payload di job. Logout/expiry tidak mengirim antrean; epoch/scope hold tidak diubah. Registrasi job tidak menggagalkan login jika OS menolak. Admin journal tetap review/retry eksplisit.

## Checks aktual

- Windows/PHP8.5.11/MySQL8.0.30/3307, **inofarm_test dengan akun schema-only**; Java25.0.4/Gradle9.3.1/SDK37, owned emulator API37/5554. Tidak reset/migrasi database pengguna atau clear APK.
- Backend final `phpunit --filter '^(?!.*DemoHistoryTest)' --testdox`: **134 PASS/2137 assertions**, `tmp/fin16-backend-final.log`. Dua test DemoHistory legacy dikecualikan seperti evidence FIN11–15, bukan dihapus. Tambahan current identity/disabled actor/revoked token; full finance/master/security/export/recovery regression lulus setelah lockfile dependencies terpasang. Guard4/12 lulus sebelumnya.
- Finance assemble app+test/JVM/lint: **PASS/76 JVM/0 errors,34 warnings,5 hints**, **1m17s**, `tmp/fin16-sync-build.log`.
- Focused cross-feature device regression **66 PASS/84.892s**, `tmp/fin16-device-regression.log`: admin7, localstore23, coordinator5, scheduler1, recovery7, investment10, season7, export5, storage timing1. Demotion sebelum mutation menahan queue dan profil asing tidak mengubah peran. Job registration persistent/network/idempotent/empty extras lulus. Pengujian tidak mengklaim OS benar-benar dispatch sesudah15menit/Doze/reboot.
- Storage KNF02: AES-GCM SQLite,1.000 synthetic records, **30 atomic record+outbox saves,11.8686–22.7395ms,mean17.28978ms,0 di atas2detik**; CSV `tmp/fin16-storage-timings.csv`. Ini DAO/storage, bukan waktu lengkap interaksi UI maupun ukuran database produksi.
- Contract checker termasuk SyncStatusPublic/role/version/credential exclusion PASS; tidak ada penghapusan field/path lama. `git diff --check` PASS.

## Batas acceptance

Tidak mengulang oracle, concurrency races, real reboot/isolated recovery FIN11–15 karena source matematika/ledger/recovery tidak berubah; bukti tersebut tetap historis dengan dependency commit eksplisit. Android API29/physical baru, battery/Doze/reboot job dispatch, E2E real-device semua modul, UAT mitra dan pentest berizin belum dibuktikan di run ini. Ini PASS engineering lokal, bukan FIN16/product DONE atau izin cutover produksi. Q07/Q08/Q11–13 dan SOP Android-only/historical CRUD tetap gate eksternal.
