# Pengukuran EC/PPM — evidence lokal2 November2026

Branch `codex/manual-ec-ppm-dashboard`, dependency main `2269efa`. [AC/inventaris](../MANUAL_MEASUREMENTS_20261102.md), [kontrak](../../../../contracts/v2/MEASUREMENTS.md). Proposal Downloads tersedia60halaman; bagian pencatatan manual/draf/atomic local+server/dua aktor diperiksa. SOP GitLab dan pengumpulan dibaca, target publikasi GitHub mengikuti user. Tidak deploy/mengubah database/app pengguna.

Prasyarat awal belum tersedia: PHP8.2 tidak sesuai, vendor/SDK/jsonschema kosong. PHP8.5.12 portable, Composer2.11.1 dari lockfile, JDK25.0.2 existing, Gradle9.3.1 wrapper, SDK37.0/build-tools37 dan emulator API29 dipasang/dijalankan untuk uji. MySQL8.0.30 instance baru `.runtime/manual-measurement-test`, port3310, hanya `inofarm_test`, user khusus schema. Guard sebelum provider/SQL tetap aktif; tidak memakai instance Laragon pengguna.

| Perintah/check | Hasil aktual |
|---|---|
| `scripts/test-backend.ps1 -GuardOnly` |PASS4tes/12assertions |
| PHPUnit filter `FinanceMeasurementTest\|FinanceActivitySyncTest\|PlantAgeComparatorTest` |PASS12/258; awal1FAIL karena EC tak sengaja diwajibkan pada nutrisi lama, validasi diperbaiki lalu assertion asli lulus |
| PHPUnit `--filter '^(?!.*DemoHistoryTest)' --testdox` |PASS142/2305; pengecualian2tes demo historis sesuai runner baseline, sebelum source IoT dilepas |
| `build-android.ps1 -JavaHome C:/Users/VICTUS/.jdks/openjdk-25.0.2 -SdkPath .tools/android-sdk -Tasks @('-PfinanceApp',':app:assembleDebug',':app:assembleDebugAndroidTest',':app:testDebugUnitTest',':app:lintDebug')` |PASS final1m1s;80JVM, lint0errors/34warnings/5hints. Awal SDK belum ada, lalu syntax helper test dan indent lint diperbaiki, bukan assertion dihapus |
| API29 `FinanceMeasurementStoreTest,FinanceLocalStoreTest,FinanceSyncCoordinatorTest` |30tes PASS pada run gabungan31; UI tambahan awalFAIL karena tombol di luar item lazy yang dimuat |
| API29 `FinanceEntryUiTest#manualMeasurementAutosaveBackRecreationQueueAndDashboardKeepOnePair` |Rerun PASS1/13.275s setelah aksi dikelompokkan: EC/PPM koma, recreate/autosave/Back, raw draft, queued, kartu pasangan, reconnect accepted. HTTP dispatcher sintetis, bukan MySQL E2E |
| Python `check-finance-contracts.py` |PASS termasuk fixture/hash baru, missing/invalid EC dan PPM, fixture/hash lama utuh |
| Python `check-finance-context.py` |PASS31dokumen/281links/8archive hashes/KF14/KNF8/U21. Link artifact gambar historis tidak tersedia diganti keterangan historis jujur; docs/archive tidak diubah |
| `git diff --check` |PASS |

Log lokal di `tmp/measurement-*` dan Gradle reports tidak dipublikasikan sebagai artifact produk. DAO device membuktikan pencarian melewati1005catatan non-pengukuran, fallback/tie/delayed arrival/koreksi/batal, queued/reopen/hash, conflict/rejected/cancel queued, scope/akun/epoch. JVM membuktikan WIB midnight/precision/draft/legacy tanpa EC. Backend MySQL membuktikan replay/audit/actor/versi/scope/read terbaru. E2E Android→HTTP Laravel/MySQL khusus pasangan baru, process death dan default launch sesudah penghapusan IoT masih dilanjutkan pada branch pelepasan runtime; bukan PASS di checkpoint ini. UAT/pentest/perangkat fisik/produksi tetap belum disahkan.
