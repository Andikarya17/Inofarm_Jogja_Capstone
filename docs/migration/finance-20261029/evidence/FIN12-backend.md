# FIN12 — evaluasi musim backend dan oracle

Tanggal evidence: 1–2 November 2026, Asia/Jakarta. Branch `codex/finance-season-evaluation`, dependency `928ac4d` (koreksi kontrak timestamp publik terpisah). FIN12 keseluruhan masih IN_PROGRESS sampai acceptance Android/API nyata selesai; FIN13–17 belum diimplementasikan pada checkpoint ini.

## Kebutuhan dan implementasi

Proposal asli60 halaman telah cocok hash baseline, terutama tabel evaluasi musim dan rumus biaya/penerimaan. Kontrak executable [SEASON_EVALUATION](../../../../contracts/v2/SEASON_EVALUATION.md), schema operasi dan OpenAPI menetapkan `season-recorded-cash-rational-half-up-v1`. FC mencakup alokasi FC operasi dan penyusutan sekali; VC hanya alokasi VC; harga aset/pembayaran menjadi referensi. TR memakai total transaksi lunas aktif yang sebelumnya dibulatkan HALF_UP per transaksi. Q hanya berat yang terjual, bukan seluruh panen.

Sebelas indikator mempunyai nilai string, unit, status, alasan, rumus, input, asumsi dan pecahan eksak sendiri. Nilai nominal dua desimal, kg/harga per kg enam, rasio delapan; pembulatan hanya presentasi. Missing berbeda dari nol yang dinyatakan setelah peninjauan. Pembagi tidak sah hanya menonaktifkan indikator terkait. Owner harus memilih coverage, state, pending diketahui atau unknown, dan catatan secara eksplisit; final memerlukan musim closed, biaya/penjualan complete dan pending0. Pernyataan tersebut bukan bukti semua perangkat offline diketahui.

Migration additive12 menyimpan laporan lengkap immutable, sources_hash dan snapshot_hash. Migration1–10 dan map schema8 tidak diubah. Preview, create, replay, daftar, detail dan halaman sumber memeriksa manager/assignment saat ini. Pembuatan memakai global sync-state lock dan transaksi snapshot/identity/audit/receipt/feed; perubahan sumber atau versi rumus menuntut review baru. Receipt lama direplay sebagai snapshot lama setelah otorisasi, tanpa menghitung ulang. AcceptedSnapshot membaca audit sumber dalam batch200; sumber total maksimum10000 dengan error eksplisit, tanpa pemotongan diam-diam.

## Hasil terverifikasi

- PHP8.5.12/BCMath/PDO, MySQL8.0.30 `inofarm_test` terpisah pada port3308, TestDatabaseGuard sebelum SQL. Data pengguna `inofarm_local` tidak diuji/reset.
- `artisan test --filter='^(?!.*DemoHistory).*'`: **106 PASS,1715 assertions**,1 November. Dua DemoHistory legacy bergantung port3307 dikecualikan sebagaimana evidence sebelumnya. Log ignored `tmp/fin12-backend-regression.txt`.
- Setelah dua case baru ditambahkan: `artisan test --filter=FinanceSeasonReportTest`: **8 PASS,196 assertions**. Log `tmp/fin12-backend-extended.txt`. Bersama unit/regresi sebelumnya terdapat108 case unik yang lulus lintas run; ini bukan klaim satu run108 bersih.
- Case API: fixture proposal FC5m/VC3m/TC8m/TR12m/Q400/L4m/R-C1.5/B-C0.5/BEPqty266.666667;200kg belum terjual tidak dihitung; pembelian aset dan sumber pembayaran tidak ditambahkan dua kali. Dua transaksi setengah sen menghasilkan TR0.02 sesuai pembulatan per transaksi.
- Replay/pagination/source correction/immutable historical snapshot; final closure/completeness/known-zero/strict JSON pending types; worker403/foreign404/current assignment revocation sebelum replay; immutable route405; checksum tamper500; fault injection setelah insert receipt membatalkan snapshot/identity/audit/feed/receipt; global master/financial UUID reuse ditolak;10001 sumber SQL sintetis menghasilkan report_source_limit dan tidak menghasilkan laporan sebagian.
- `check-season-evaluation-oracle.py --php ../.tools/php/php.exe`: **1024 PASS** dengan Python Fraction independen, exact fractions, status/null/rounding, operation golden; receipt/sources yang dihasilkan API divalidasi schema dan direkonsiliasi ulang totals/hash dengan Python. Log/perantara `tmp/fin12-public.json` ignored dan sintetis. Tidak membandingkan hasil fungsi dengan fungsi itu sendiri.
- `check-finance-contracts.py`: PASS, termasuk fixture public snapshot sintetis dan formatter timestamp eksplisit yang tidak bergantung extras opsional jsonschema. Seluruh path/schema lama OpenAPI dipertahankan secara struktural; hanya kontrak FIN12 ditambahkan.

## Verifikasi lanjutan — 2 November 2026

Regresi lengkap setelah penambahan case dan worker route: **108 PASS,1755 assertions**,134.47s (`tmp/fin12-backend-108.txt`), tetap mengecualikan dua DemoHistory legacy. Dua race PHP/MySQL independen PASS: duplicate memakai PIDs5128/25528, connections108/109, satu snapshot/ledger; source correction bersamaan memakai PIDs12248/26632, connections113/114, laporan terbit konsisten dengan revisi sumber1 sebelum cancellation2. Kedua skenario membersihkan hanya fixture UUID miliknya. Case source-first/409 juga dibuktikan feature test; hasil race yang teramati tidak diklaim mencakup semua urutan.

Android terbaru: **JVM59 PASS, lint0 errors/33 warnings**, assemble app/test PASS (`tmp/fin12-utf8-android-build.txt`,6m30s). Teks UTF-8 diperbaiki dan draf awal memakai job autosave yang dapat dibatalkan agar tidak menimpa edit baru. Ketujuh case perangkat dan tiga fase real API/reboot masih menunggu hasil saat penambahan catatan ini.

## Acceptance berikutnya

Native form/history/source drill-down/raw persistence/outbox telah ditulis. JVM59 dan build/lint awal lulus; perubahan scope-quarantine terakhir masih menunggu pemeriksaan ulang. Enam kasus perangkat unik pada revisi awal lulus lintas run: run6 berisi5 PASS dan1 terhalang dialog System UI ANR; kasus keyboard yang sama kemudian PASS tersendiri. Screenshot/dump ignored membuktikan dialog sistem menutupi aplikasi, bukan dianggap aplikasi lulus saat tertutup dialog. Ketujuh case dengan scope-quarantine tambahan dan tiga fase API nyata/reboot belum dinyatakan PASS pada checkpoint backend ini.

FIN14 ekspor, FIN13 investasi, recovery, admin/master/reference UI, background sync, regresi akhir/KNF/API29/perangkat fisik, UAT/pentest/reviewer/cutover tetap gate terpisah. Tidak ada push, deploy, production restore atau penghapusan data aplikasi pengguna.
