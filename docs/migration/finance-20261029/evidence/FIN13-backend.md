# FIN13 investasi — verifikasi backend 2 November 2026

Branch `codex/finance-investment`, parent `3fa142e`. Backend/math dan native critical acceptance **PASS lokal**; lihat [native evidence](FIN13-native.md) untuk APK/unit/device/real API/two reboot reconciliation dan batas regresi.

- Schema13 additive: scenario root dan annual value objects append-only `(scenario_id,scenario_version,year)`; scenario_id FK pada report_snapshots. Tidak mengubah migrasi terbit1–10 atau frozen identity schema8. Runtime global identity mencakup dua kind baru.
- Manager/current GH authorization sebelum replay/read/sources. Scenario create/update/cancel versioned, numeric missing tetap null; alasan koreksi/pembatalan; audit/receipt/feed atomik. Annual rows versi lama tidak dihapus.
- `annual-relative-rational-bisection-v1`: I0 sekali tahun0; CF neto akhir tahun relatif tanggal dasar; actual/projection/basis dinyatakan manusia, bukan audit bukti. Horizon1–100 batas teknis, tanpa default produksi. Diskonto fraksi8 >−1; PP/DPP konvensi eksplisit interpolation/end_of_year.
- NPV/PP/DPP rasional eksak, pembulatan HALF_UP hanya keluaran2/8. IRR konvensional, Horner decimal80/bisection max256/tolerance fraction1e−14, metadata interval/iterasi/metode; tidak memalsukan pecahan eksak. Unknown/nonconventional/no recovery tidak disulap nol dan indikator lain independen.
- Preview/review terikat scenario version/source hash/formula/note. Immutable snapshot mencakup input, source provenance/relative periods, formula, assumptions/reasons/results. Source correction/cancellation tidak menghitung ulang laporan lama. Shared SavedReportStorage memeriksa full hash dan source hash sebelum pembacaan; FIN12 regression tetap lulus.
- DTO investasi baru terpisah dari season DTO dan generic anticipatory schema yang lebih sempit. Input metric berisi annual list; output decimal string max1024 mendukung horizon/rate ekstrem. Path/schema lama dipertahankan struktural. Executable request schema/golden dan actual synthetic Laravel scenario/preview/report/source fixture, kedua hash diverifikasi independen.

## Hasil

`artisan test --filter='^(?!.*DemoHistory).*'`: **119 PASS,1906 assertions,58.90s**, `tmp/fin13-backend-regression.txt`. Dua DemoHistory legacy yang bergantung port3307 tetap dikecualikan; tidak menyentuh database pengguna. Focus investasi terbaru **11 PASS,151 assertions** (7 feature/125 +4 unit/26), `tmp/fin13-backend-contract-ready.txt`. Semua memakai MySQL inofarm_test guarded sebelum SQL.

Independent Python Fraction/Decimal oracle **259 PASS**, `tmp/fin13-numeric-boundaries.txt`: formula proposal, exact fractions, annual100, r mendekati−1/ekstrem, negative/unknown CF, first recovery/no recovery, both conventions, IRR analytic/interval sign/tolerance. Tidak menyebut oracle sebagai bukti agronomi/usaha mitra.

Dua race proses PHP/MySQL independen **PASS**: duplicate PIDs29732/30448, connections405/404 → satu snapshot/receipt/audit/feed; concurrent source update PIDs10552/31048, connections410/409 → update2 diterima, old reviewed report `report_sources_changed`, nol snapshot. Logs `tmp/fin13-race-{duplicate,source}.txt`. Ini urutan teramati, bukan klaim semua schedule. Report-first dan immutable old report setelah update/cancel dibuktikan feature tests. Fixture UUID milik runner dibersihkan, tanpa reset database.

`scripts/check-finance-contracts.py` **PASS** termasuk timestamp formatter eksplisit, semua old schemas/goldens dan FIN13 actual DTO/full/source hashes. Pint/diff check PASS saat checkpoint. Source code baru belum di-commit sampai checkpoint fitur konkret siap.

## Batas dan scope terbaru

Native critical acceptance kini PASS:66 JVM/lint0,8 device cases dan3 fase real API/two OS reboot/SQL reconciliation. [Evidence native](FIN13-native.md). Root navigation focus1PASS/16.992s; native history follow-up untuk cancelled-only cache pada profile baru dicatat di evidence native. FIN14 PDF/CSV belum tersedia. Pengguna terbaru meminta langsung commit/push checkpoint terakhir lalu penjelasan langkah berikutnya; FIN14 menjadi task berikutnya. FIN15–17 di luar scope saat ini. UAT/pentest/fisik/reviewer dan recovery/cutover tetap terbuka. Nilai produksi kosong; tidak ada deploy/production restore/app-data clear.
