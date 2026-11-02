# Cancelled scenario history — 2 November 2026

Branch `codex/finance-cancelled-scenario-history`, parent `ff2e4ba`. **PASS lokal**. Push ditunda atas instruksi terbaru pengguna; tidak ada percobaan publikasi pada task ini.

Fresh profile/older consumer bisa hanya mempunyai cached tombstone untuk skenario dibatalkan. Generic hydration sebelumnya menyaring semua tombstone, sehingga daftar lokal kehilangan histori itu walau backend/annual history dan snapshot laporan tetap utuh.

Hydration kini memasukkan tombstone hanya untuk investment_scenario, memvalidasi DTO bertipe dan status cancelled sebelum materialisasi. Current role/GH tetap diperiksa, join tidak menimpa local record/draft yang sudah ada, tidak mengaktifkan kembali skenario, tidak mengubah epoch/operation ID/hash dan tidak menghapus data. Tombstone kind lain tetap disaring seperti semula.

APK app/test assemble PASS (`tmp/fin13-cancelled-cache-build.txt`,5m19s), JVM66PASS. Android API29: **17PASS,149.17s** (`tmp/fin13-cancelled-cache-device.txt`):10 investment cases termasuk fresh cancelled-only cache/current role/revoked scope/idempotence dan raw draft retention, ditambah7 season-report regression. Tidak ada app data clear atau backend reset. Whitespace/staged secret/old contract compatibility checks dilakukan sebelum commit.

Gap cancelled-only history dari FIN13 ditutup oleh hasil ini. FIN14 PDF/CSV/private download berikutnya; legacy broad timing, physical/UAT/pentest/recovery/reviewer/cutover tetap terpisah. Nilai produksi tidak diisi dari fixture.
