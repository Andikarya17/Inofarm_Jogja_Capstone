# Serah terima checkpoint FIN10 → FIN11–FIN17

Checkpoint30 Oktober 2026 WIB, atas instruksi terakhir pengguna untuk berhenti aman, commit/push GitHub dan menyerahkan lanjutan. **FIN11 belum diimplementasikan.** Sumber checkpoint FIN10: `c03df6d4633abf691e0e1975d233140ab7050216` (native entry/history) di atas `405f5091cc420ffeab9fcb7ae3ca158a14df2336` (backend/math), keduanya telah diverifikasi pada remote GitHub. Branch serah terima **`codex/finance-fin10-handoff`** mengandung seluruh dependency bertumpuk FIN01–FIN10 dan dokumen ini; teman cukup mengambil branch tersebut. Main belum digabung otomatis.

Repository: [Inofarm_Jogja_Capstone di GitHub](https://github.com/Andikarya17/Inofarm_Jogja_Capstone). [Master prompt](MASTER_PROMPT.md), [prompt yang diberikan kepada agent teman](CONTINUE_FIN11_FIN17.md), [status aktual](../../context/IMPLEMENTATION_STATUS.md), [keputusan pengguna](DECISIONS.md). Audit FIN00 dan README legacy adalah histori, bukan status implementasi terbaru.

## Mengambil source

Clone baru, jalankan dari folder induk yang dipilih teman (Git/izin akses GitHub diperlukan):

```powershell
git clone --branch codex/finance-fin10-handoff --single-branch https://github.com/Andikarya17/Inofarm_Jogja_Capstone.git Inofarm_Jogja_Capstone
Set-Location Inofarm_Jogja_Capstone
git status --short
git log -3 --oneline
```

Untuk checkout yang sudah ada, periksa perubahan lokal dahulu. Jika tidak kosong, simpan/commit kerja sendiri di branch yang tepat; jangan reset/stash/delete otomatis. Di clone baru `origin` adalah GitHub. Di checkout penulis `origin` masih GitLab sehingga perintah ini memakai remote `github` eksplisit:

```powershell
git status --short
git remote -v
# Hanya bila remote github belum ada:
git remote add github https://github.com/Andikarya17/Inofarm_Jogja_Capstone.git
git fetch github
# Bila branch lokal belum ada:
git switch --track -c codex/finance-fin10-handoff github/codex/finance-fin10-handoff
# Bila sudah ada: git switch codex/finance-fin10-handoff
git pull --ff-only github codex/finance-fin10-handoff
git log -3 --oneline
```

Jangan jalankan `remote add` atau `switch -c` lagi bila nama sudah ada. Jika fast-forward ditolak, periksa riwayat dan perubahan; jangan force/reset/rebase riwayat bersama. Akses private repository memerlukan undangan pemilik dan login GitHub masing-masing. Pull membawa source dan dokumen, **tidak membawa database, akun/password, file bukti privat, SDK, PHP, vendor, .tools atau .env**. Jangan memakai akun produksi untuk fixture.

Untuk mulai FIN11 setelah checkout bersih:

```powershell
git switch -c codex/finance-assets
```

Buat branch berikutnya per fitur dari dependency valid; catat base commit dan push ke remote yang URL-nya benar-benar GitHub. Tidak harus mengambil setiap branch historis satu per satu.

## Environment dan menjalankan v2

Baseline yang benar-benar dipakai saat verifikasi: Windows/PowerShell, PHP8.5.11 dengan BCMath/PDO MySQL/fileinfo, Composer+composer.lock, MySQL8.0.30, JDK25.0.4, Gradle9.3.1/AGP9.1.1 (wrapper ada), Android SDK37 dan emulator API37. Ini lingkungan yang diuji, bukan klaim semua versi lain kompatibel. Target v2 minSdk29, tetapi runtime API29/perangkat fisik belum diverifikasi. Jangan upgrade dependency massal. Buka folder `android` di Android Studio; root React/firmware bukan produk target.

1. Siapkan PHP/Composer di PATH, extension yang diperlukan lock, dan MySQL sendiri. Dari `backend`, jalankan `composer install` menggunakan lock, bukan composer update. Pastikan `composer check-platform-reqs` lulus. Folder vendor/.tools tidak ikut Git.
2. Salin `backend/.env.example` ke `.env` **hanya bila belum ada**, isi kredensial database development milik teman. Contoh port3307 berasal mesin penulis; sesuaikan port MySQL sendiri. Untuk database lokal baru yang memang disiapkan untuk proyek, jalankan `php artisan key:generate` dan `php artisan migrate`. Migration additive dapat menyentuh data lokal; jangan mengarahkannya ke DB pengguna/produksi yang belum disiapkan. Tidak ada migrasi/seed otomatis saat pull. Jangan memakai migrate:fresh/reset/seed untuk data development pengguna.
3. Buat akun v2 eksplisit melalui `php artisan inofarm:finance-user <email-sendiri> --name="Nama pengelola" --role=manager`; password diminta tersembunyi. Akun owner legacy bukan akun manager v2. Perintah ini tidak membuat greenhouse/assignment. Pada database development baru, buat GH dan master lewat API v2 sesuai [kontrak master](../../../contracts/v2/MASTERS.md) dan OpenAPI; pembuatan GH memberi assignment pencipta. Editor admin/master native masih gap, sehingga login baru bisa menampilkan konteks kosong secara sah. Jangan isi konfigurasi produksi dari fixture pengujian.
4. Dari `backend`, `php artisan serve --host=127.0.0.1 --port=8000`. URL login emulator `http://10.0.2.2:8000/api/v2/`. Untuk USB gunakan `adb reverse tcp:8000 tcp:8000` dan `http://127.0.0.1:8000/api/v2/`. Alamat API bisa diganti pada layar login. Pilih izin jaringan lokal bila Android memintanya. Release mewajibkan HTTPS; tidak ada deployment dalam handoff ini.

Build native dari root (isi path JDK/SDK yang benar di komputer teman):

```powershell
$env:JAVA_HOME='C:/lokasi/JDK'
./scripts/build-android.ps1 -JavaHome $env:JAVA_HOME -SdkPath "$env:LOCALAPPDATA/Android/Sdk" -Tasks @('-PfinanceApp',':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug')
# Emulator/perangkat aktif, tanpa menghapus app-data:
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r android/app/build/outputs/apk/debug/app-debug.apk
```

`-PfinanceApp` wajib untuk target v2; default build/launcher lama masih legacy. APK debug v2 `com.inofarm.jogja.finance` terpisah dari legacy. Token/draft/outbox terisolasi server+akun dan tetap tersimpan saat logout; masuk kembali akun yang sama untuk melanjutkan antrean. Jangan clear data agar “memperbaiki” sync.

## Pengujian aman di komputer teman

Siapkan schema **`inofarm_test`** terpisah dan akun MySQL test yang hanya mempunyai privilege schema itu. Salin `.env.testing.example` ke `.env.testing` bila belum ada, isi host/port/user/password/key sendiri, `DB_DATABASE=inofarm_test`, `DB_URL=` kosong. Generate key dengan `php artisan key:generate --env=testing`. Guard memeriksa konfigurasi efektif sebelum SQL; jangan mematikannya. PHPUnit memakai RefreshDatabase **hanya schema test**. Jangan menjalankannya saat fixture/server HTTP integrasi masih aktif.

Dari root:

```powershell
./scripts/test-backend.ps1 -GuardOnly
./scripts/test-backend.ps1
python -m venv .tools/finance-contracts-venv
& .tools/finance-contracts-venv/Scripts/python.exe -m pip install jsonschema
& .tools/finance-contracts-venv/Scripts/python.exe scripts/check-finance-contracts.py
python scripts/check-finance-context.py
```

`scripts/test-backend.ps1` memilih `.tools/php/php.exe` jika ada, selain itu PHP di PATH. Runner integrasi perangkat `scripts/test-finance-entry.ps1` saat checkpoint **memerlukan PHP pada `.tools/php/php.exe`**; sediakan instalasi lokal lengkap di lokasi itu atau perbaiki pemilihan PHP secara teruji dalam branch tooling terpisah. Jangan mengira satu exe tanpa extension/ini cukup. Runner juga mengasumsikan satu perangkat ADB aktif, port8001 kosong, folder `tmp` tersedia dan MySQL test sudah bermigrasi. Jalankan suite backend terlebih dahulu, baru runner; jangan keduanya bersamaan.

```powershell
New-Item -ItemType Directory -Force tmp | Out-Null
# Setelah backend suite selesai, emulator API37 aktif dan .tools/php siap:
./scripts/test-finance-entry.ps1 -JavaHome $env:JAVA_HOME -SkipChecks
```

Runner membangun/menginstal APK test suffix, membuat fixture dua role/GH di inofarm_test, menghentikan hanya API miliknya untuk barrier offline, force-stop APK uji, replay dan cleanup UUID fixture. Batas upload dalam runner hanya fixture. Jika runner gagal dan manifest fixture tersisa, periksa provenance/cleanup miliknya; jangan reset database atau menimpa manifest. Log/manifests/credentials sementara berada di ignored tmp/outputs dan tidak ikut Git. Hasil run mesin penulis adalah evidence historis; teman harus mencatat hasil run miliknya sendiri.

## Cakupan checkpoint dan sisa pekerjaan

- FIN01–FIN05: kontrak core/master/acuan, auth/role/GH server, global identity, ledger atomic, immutable snapshots, fixed decimal, local encrypted persistence/sync foreground dan guard epoch. Admin/master/acuan editor native, background scheduling/fresh profile/reconciliation masih gap.
- FIN06–FIN08: entry manual, review, history/audit/filters/conflict/cancel/close-reopen dan panen multi-grade, raw draft/logout/late ACK guards. UAT dan sebagian KNF belum terbukti.
- FIN09: pembayaran aktual vs alokasi operasi FC/VC eksplisit, cap source, file privat selectable/encrypted/outbox/retry/preview/audit. Q07 upload produksi belum dikonfigurasi, jadi tetap ditahan.
- FIN10: paid sale multi-harvest satu musim, exact aggregate HALF_UP, source lock/reconciled counter, koreksi/cancel/replay/audit, native draft/review/history/conflict/offline. Retur/kredit/piutang tidak ditambahkan.
- **FIN11–FIN17 belum selesai:** aset/penyusutan → evaluasi musim → investasi → snapshot/PDF/CSV → backup/restore/reconciliation → integrasi/KNF/UAT/pentest → cutover/dokumentasi. UI admin/master/acuan serta gap sebelumnya harus ditutup sebelum klaim produk selesai. Produksi tidak di-deploy dan main tidak digabung pada checkpoint.

Baca [evidence backend FIN10](evidence/FIN10-backend.md) dan [evidence Android FIN10](evidence/FIN10-android.md) untuk command/hasil/batas. Backend terakhir94tests/1604assertions + guard4/12,3 races MySQL independen; Android terakhir48unit dan8fase real DB/device. Regresi akhir56instrumentation PASS (29UI,23localstore,3sync,1SaleRepository raw draft failure), lint0errors/33warnings/5hints. Bukti tidak setara acceptance mitra/API29/pentest/restore. Pengujian lampiran/fitur sebelumnya tercatat pada status, bukan perlu diulang tanpa alasan.

## Memberikan pekerjaan ke agent teman

Buka repository di agent teman pada branch handoff atau branch FIN11 yang baru, lalu salin seluruh isi setelah garis pembatas dari [CONTINUE_FIN11_FIN17.md](CONTINUE_FIN11_FIN17.md). Pastikan agent bisa membaca [MASTER_PROMPT.md](MASTER_PROMPT.md) dan AGENTS.md dalam clone, bukan hanya tautan chat. Minta agent memeriksa source/status/testing nyata terlebih dahulu, melanjutkan FIN11 dan menutup gap sampai FIN17, mempertahankan keputusan Q02/Q07/Q10 serta pembatasan database.

Proposal PDF asli berada di luar repository penulis; bagikan lewat saluran yang sesuai secara terpisah bila teman memerlukan verifikasi akademik. Jangan menambahkan secrets atau data nyata ke Git untuk mempermudah setup. Folder desain26Oktober yang belum ditinjau tetap dikecualikan; seluruh file task yang dapat dipublikasikan berada pada branch handoff.
