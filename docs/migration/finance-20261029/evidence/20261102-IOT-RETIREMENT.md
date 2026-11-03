# Pelepasan IoT dan finance default — 2–3 November2026

Branch `codex/retire-iot-runtime`, dependency pengukuran `0d960db` pada `codex/manual-ec-ppm-dashboard`, baseline main `2269efa`. [Inventaris/AC dan alasan pemisahan](../MANUAL_MEASUREMENTS_20261102.md), [kontrak EC/PPM](../../../../contracts/v2/MEASUREMENTS.md), [build/upgrade](../FIN17_INSTALL_RUN.md). Tidak deploy, clear data, uninstall, drop tabel, mengedit migration lama atau mengonversi event/tank.

## Source dan upgrade

107file khusus legacy dilepas:103file pada inventaris awal, fixture TLS publik/README owner, dan2asset atribusi Haze setelah library tidak lagi dibundel. Android owner data/UI/config/telemetry dan entry selection, backend v1/controller/device+owner middleware/provisioning/ingest services/model khusus v1, firmware/PlatformIO dan React/Vite/npm/config prototipe tidak lagi aktif. `ApiBaseUrl` dan tema shared dipisahkan ke core; Haze/navigation owner dilepas. Proguard profile roots owner/Haze dibersihkan; roots library bersama tetap.

Semua migration1–14, map identity8, legacy table/data, User/Sanctum/tank_id, audit/receipt/feed/recovery inventory serta8hash arsip tetap. Schema15 nullable EC additive; SQLite9/vault/provider/job1601 tetap. Build default debug/profile `.finance`, release `com.inofarm.jogja`, code2/min29. `install -r` pada emulator uji mempertahankan byte main DB+WAL+SHM+preferensi vault sebelum launch; bukan bukti migrasi akun owner/produksi.

Dashboard tepat dua kartu pada konteks dipilih. HSS/HST dan jadwal tetap; acuan berada di formulir. EC/PPM satu record, occurred_at WIB/UUID desc, empty/missing/fallback eksplisit, queued/rejected/conflict/cancel/epoch/scope dipisahkan. Koreksi queued mempertahankan nama pencatat awal dari server. Loop cache lifecycle-aware membaca waktu sync terbaru termasuk job background, membatalkan read saat STOP, dan tidak menyamakan LAN tanpa internet dengan tidak adanya jaringan.

## Perintah dan hasil aktual

| Check | Hasil |
|---|---|
| `scripts/test-backend.ps1 -GuardOnly` / PHPUnit guard |PASS4/12; effective MySQL8.0.30 `inofarm_test`, instance baru port3310 sebelum SQL |
| `php vendor/phpunit/phpunit/phpunit --testdox` di backend |PASS130tes/2369assertions, tanpa filter. Baseline pre-retirement142 termasuk13tes v1;13 khusus v1 dilepas,1tes penolakan route/command ditambah; semua finance tests dipertahankan |
| `build-android.ps1 -JavaHome C:/Users/VICTUS/.jdks/openjdk-25.0.2 -SdkPath .tools/android-sdk -Tasks @(':app:assembleDebug',':app:assembleDebugAndroidTest',':app:testDebugUnitTest',':app:lintDebug',':app:assembleProfile',':app:assembleRelease')` |PASS2m58s;73JVM, lint0errors/16warnings/1hint; profile/release R8 PASS. Release tidak di-install/distribusikan |
| `check-finance-contracts.py`, `check-finance-context.py --staged`, `check-product-boundary.py` |PASS;31dokumen/277tautan/8hash arsip staged valid, KF14/KNF8/U21 tetap |
| API29 `FinanceMeasurementStoreTest,FinanceLocalStoreTest,FinanceSyncCoordinatorTest,FinanceSyncScheduleTest` |PASS31/19.26s |
| API29 `FinanceEntryUiTest` |PASS39tes/388.309s pada source final |
| `test-finance-entry.ps1 -JavaHome ... -SdkPath ... -SkipChecks -ManualMeasurements` |PASS8fase real Laravel/MySQL melalui ADB reverse; EC1.250000/PPM625.500000, actor sesi, hash/replay, force-stop/restart, manager history, harvest cancel/old ACK, payment100.01/allocation60.01/remaining40.00, PNG/PDF2 hash/receipt/audit/feed, sale0.01/cancel/restored counters; SQL reconciliation PASS |
| API29 `FinanceProcessSurvivalTest#prepare`→force-stop→`#verify` dengan survivalPhase |PASS2fase/proses berbeda; catatan general lama, draf EC mentah `1,`/PPM kosong, queued EC1.25/PPM625.5 dan UUID/hash asli tetap; hanya DB fixture khusus dihapus setelah verify |
| Regresi keuangan/admin native melalui readiness Device |PASS72tes/78.65s; readiness contracts/context/boundary/device PASS. 30save pada1.000rows maksimum24.6711ms. Backend/build di-skip eksplisit pada run ini karena sudah lulus terpisah |
| `package-finance.ps1` dari tracked checkout bersih |PASS dari commit bersih225f705fbba61844e2bb0a974fa574d092227779, build2m14s; identity/minSdk/debug/hash diverifikasi tanpa firmware/npm |
| Composer `check-platform-reqs`, PHP lint, PowerShell parser, `git diff --check` |PASS |

Log lokal berada di `tmp/retirement-*`, `outputs/finance-entry/` dan Gradle reports, diabaikan Git; bukan evidence historis yang diklaim sebagai run baru. Uji hanya emulator baru milik task `Inofarm_Manual_Verify_API29`, tidak perangkat/data pengguna.

## Kegagalan dan tindak lanjut

- Satu assertion v2 lama berharap403 pada route v1. Diperbarui menjadi404 karena route dilepas; token owner tetap ditolak v2. Rerun auth7/358 dan seluruh backend130/2369 PASS.
- Satu run PHPUnit tidak sengaja bertabrakan dengan pembuatan fixture HTTP; fixture gagal sebelum manifest terbentuk karena schema test sedang diinisialisasi. Run dibatalkan, guard tidak dimatikan, tidak mengenai DB pengguna. Seluruh backend diulang secara serial sampai PASS. Runner entry kini menolak proses PHPUnit aktif sebelum build dan sebelum fixture.
- Beberapa UI test awal mengakses popup/detail sebelum tersedia atau menunggu item lazy yang belum di-scroll. Wait dan scroll aktual ditambahkan; assertion record/audit/angka tetap. Run gabungan UI awal dihentikan untuk memperbaiki navigasi tes, bukan dinyatakan PASS atau crash produk. Run39 berikutnya35PASS/4FAIL;2aset/investasi lulus rerun setelah popup/scroll ditunggu. Dua kasus lampiran menyingkap satu item beranda baru terlalu tinggi pada peran pengelola: controls dan pengukuran dipisah menjadi item lebih kecil, dan scroll ke tombol/divalidasi form. Dua kasus lampiran lalu PASS29.957s. Regresi39 akhir pada source layout final seluruhnya PASS388.309s.
- Satu unduhan PDF real mendapat `ProtocolException: unexpected end of stream` melalui NAT emulator. Transport fixture diganti ke localhost ADB reverse;8fase final beserta unduhan/hash/cap/replay PASS tanpa mengurangi ukuran/isi/asersi file.
- Penurunan80→73JVM bukan pelemahan finance:10kasus pada2file tes owner/config/URL v1 dilepas,2tes URL finance+1tes pencatat koreksi/version hold ditambah. Pending envelope/hash lama tidak ditulis ulang.

## Batas penerimaan

Tidak melakukan UAT mitra, pentest berizin, OS dispatch/Doze/reboot job sungguhan, upgrade/signing produksi atau restore aktual schema15 lintas instance. Backend recovery/immutability/scoping ikut regresi MySQL, tetapi itu tidak setara restore drill baru. APK release build bukan izin distribusi. Draf/antrean lama tetap; tidak ada auto-import akun/data owner. Rollout server/schema dan client baru memerlukan rencana produksi pada FIN17. Q07/Q08 dan gate akademik/bisnis tetap seperti DECISIONS; tidak dibuat persetujuan fiktif.

## Paket dan publikasi

Paket lokal `output/finance/finance-review-225f705f-20261103-093540.zip` berisi APK debug review, manifest commit, INSTALL dan SHA256SUMS. APK SHA256 `bd13cf46467606b5b9b4a3caf0336ca23835e0aa6cf1688467134038751ee89d`. Source paket adalah commit225f705; commit dokumentasi berikutnya hanya mencatat hasil paket ini. APK dan runtime/credential tidak dimasukkan Git.

Branch pengukuran0d960db dipublikasikan pada [draft PR40](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/40), branch retirement225f705 pada [draft PR41](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/41) dengan base branch pengukuran. Remote main tidak berubah saat fetch; tidak merge/deploy. GitHub pada225f705 melaporkan0check runs/0statuses (combined pending), sehingga CI remote tidak diklaim PASS. Seluruh hasil PASS di atas berasal dari eksekusi lokal yang disebutkan.
