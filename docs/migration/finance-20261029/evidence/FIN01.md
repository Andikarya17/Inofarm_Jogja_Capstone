# FIN01 core: kontrak executable lintas layer

29 Oktober 2026 sekitar 14:41 WIB. Base `9da5b30`, branch `codex/finance-contracts`. Scope: core auth/context/activity/sync; bukan seluruh kontrak finance. Source proposal hash cocok baseline. Perubahan lokal FIN00/archive/desain tidak di-stage.

## Acceptance dan hasil

- PASS schema core/OpenAPI local refs, fixture sintetis dan oracle hash SHA256 independen Python; `scripts/check-finance-contracts.py` memakai jsonschema pada venv `.tools/finance-contracts-venv` (bukan dependency APK/backend).
- PASS PHP/Kotlin decimal null/zero/locale/nonfinite/exponent/scale/overflow dan canonical object ordering. Fixture sama dibaca dua platform.
- PASS backend guard sebelum SQL: 4 test, 12 assertion. Full suite MySQL `inofarm_test`: 22 test, 419 assertion, termasuk 2 test kontrak baru (15 assertion). Runtime PHP 8.5.11, PHPUnit 12.5.38; hanya instance lokal existing dijalankan, tidak migrasi/test `inofarm_local`.
- PASS Android `assembleDebug testDebugUnitTest lintDebug`: BUILD SUCCESSFUL, 50 detik; 12 unit tests, 0 failure/error. Java 25.0.4, Gradle existing 9.3.1, dependency tidak di-upgrade. Bukan bukti device/performance.
- PASS Pint pada file PHP baru dan diff whitespace scope task.

Commands dari root:

```powershell
./scripts/test-backend.ps1 -GuardOnly
.tools/finance-contracts-venv/Scripts/python.exe scripts/check-finance-contracts.py
./scripts/test-backend.ps1
./scripts/build-android.ps1 -JavaHome 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
.tools/php/php.exe backend/vendor/laravel/pint/builds/pint --test backend/app/Domain/Contracts backend/tests/Unit/FinanceWireContractTest.php
```

Log lokal (ignored): `tmp/finance-baseline-backend-running.txt`, `tmp/finance-contracts-android.txt`; JUnit XML `android/app/build/test-results/testDebugUnitTest`. Percobaan awal baseline dihentikan karena MySQL belum berjalan; build awal gagal karena JAVA_HOME kosong; schema awal gagal karena jsonschema belum terpasang. Seluruhnya diperbaiki dan perintah di atas kemudian lulus, bukan disembunyikan sebagai pass awal.

API/schema delta: belum ada route/migration target, hanya kontrak executable dan domain wire utilities. OpenAPI core adalah kontrak target, bukan klaim route sudah aktif. U01–21 runtime belum dipenuhi; KF13/KNF06 mendapat fondasi kontrak saja. Numerik transaksi rinci, IRR solver dan seluruh business gates tetap dilacak. Langkah berikut FIN02 auth dan assignment scope server.
