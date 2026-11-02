# FIN17 — instalasi, packaging dan kesiapan handover

2 November2026 WIB. Branch `codex/finance-cutover-docs`, dependency `ab156d8`. FIN11–15 Cruelasss `e0dc9ac` tetap ancestor; FIN16 perubahan/pengujian dirinci dalam [acceptance](../FIN16_ACCEPTANCE.md). Main/remote GitLab tidak diubah.

## Deliverable

- [Install/run](../FIN17_INSTALL_RUN.md): clone branch yang benar, locked Composer/platform/MySQL/Android setup, bootstrap manager tanpa seed palsu, editor admin, alur kedua peran, draf/pending/sync/reconcile, private upload/backup dan troubleshooting. README/context/contracts diarahkan ke checkpoint terbaru; catatan FIN00/FIN10/push-ditunda tetap dilabeli historis.
- [Cutover/rollback](../FIN17_CUTOVER.md): compatibility/schema/wire/SQLite, freeze semua writer, backup/isolated reconciliation, dry-run additive, smoke/monitor/latequeue, rollback yang mempertahankan transaksi baru, legacy retention/mapping dan gate UAT/pentest/bisnis/SOP. Tidak mengarang PIC/tanggal/sign-off atau mengizinkan deploy/production restore.
- `scripts/package-finance.ps1`: membangun finance debug dari tracked source bersih; identitas `.finance`/minSdk29/debug diverifikasi dari APK dengan SDK aapt2. ZIP hanya APK,manifest,INSTALL,SHA256SUMS; tidak memerlukan firmware/React atau menyalin secrets/log/dump/data. Paket untuk review internal, bukan release production. Packager legacy tidak diubah.

## Verifikasi

PowerShell AST parse PASS. Changed Markdown local links **198 diperiksa PASS**, context checker **269links/8archive hashes/KF14/KNF8/U21 PASS** sebelum penambahan evidence ini. `git diff --check` PASS. Runbook dicocokkan dengan source command signatures,config,Android metadata,schema dan dua SOP PDF serta proposal49–50/55–57; tidak mengklaim fresh OS install. Composer install/platform check dan backend guarded fresh schema lewat PHPUnit sudah diuji pada FIN16; actual restore FIN15 adalah evidence historis, tidak dijalankan ulang untuk dokumentasi.

Runtime packaging dilakukan sesudah source committed agar manifest mempunyai provenance clean commit; hasil terukur ditambahkan setelah command selesai. Tidak mengulang backend/device suites untuk perubahan dokumentasi dan packager.

## Gate yang belum terpenuhi

UAT mitra/pentest berizin/perangkat sasaran/job dispatch nyata,production upload/recovery policies,academic Android-only/historical CRUD,review/merge dan operator release tetap belum disahkan. FIN16/17 tidak dilabeli produk DONE atau production cutover. Tidak migrate/reset/drop database pengguna,restore produksi,clear APK atau menghapus desain untracked26Oktober. Semua feature branches dipublikasikan ke GitHub sesuai otorisasi terbaru user.
