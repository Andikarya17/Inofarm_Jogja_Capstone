# FIN16 — perbaikan dependency MIME

2 November 2026 WIB, branch `codex/finance-mime-security`, parent `1fb1b39`.

`composer audit --locked` mengonfirmasi CVE-2026-45070/PKSA-wtxr-p26d-nn42 pada symfony/mime8.1.7 existing. [Advisory resmi Symfony](https://symfony.com/cve-2026-45070) menyebut8.1.10 sebagai versi perbaikan; Laravel13.35 menerima `^7.4.0 || ^8.0.0`, PHP8.5.11 memenuhi requirement8.4.1. Update terarah `composer update symfony/mime:8.1.10 --minimal-changes --no-interaction --prefer-dist` mengubah **satu package saja** pada lockfile; manifest/framework/library Android tidak berubah.

- `MimeHeaderSafetyTest` menolak nama parameter CRLF/spasi/colon dan mempertahankan filename sah. Digabung regresi `FinanceAttachmentTest|FinanceReportExportTest|FinanceRecoveryTest`: **25 PASS/416 assertions**, `tmp/fin16-mime-checks.log`.
- **MySQL inofarm_test/schema-only test account**, tidak ada SQL ke database pengguna. Guard existing aktif sebelum SQL.
- `composer check-platform-reqs`: PASS. `composer audit --locked --format=json`: **0 advisory/0 abandoned**, `tmp/fin16-audit-final.json`. Hasil audit ini bertanggal, bukan jaminan semua kerentanan atau pentest berizin selesai.
- Tidak perlu mengulang Android karena tidak ada perubahan APK. Regresi backend134/2137 sebelum patch dan focused25/416 sesudah patch dicatat terpisah, bukan diklaim satu run138 bersih.
