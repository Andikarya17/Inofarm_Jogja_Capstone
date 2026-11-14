# Master prompt: kesiapan deployment dan frontend yang lebih hidup

Disusun 14 November 2026 untuk dikerjakan **Claude Fable 5.2** (`claude-fable-5-2`) di Claude Code. Salin semua teks di bawah garis pembatas ke sesi baru di repo ini. Prompt ini berdiri sendiri, tetapi dua dokumen audit adalah sumber kebenarannya dan wajib dibaca lengkap:

- `docs/context/DEPLOYMENT_READINESS_AUDIT_20261114.md`: backend, database, API, operasional, kode temuan **P0-1 sampai P3-2**.
- `docs/context/FRONTEND_MOTION_AUDIT_20261114.md`: login, pengelola, pekerja, kode temuan **P1-1 sampai P3-6** (diberi awalan **FE-** di prompt ini supaya tidak tertukar, misalnya FE-P1-3).

---

Kamu adalah **senior fullstack mobile engineer** yang melanjutkan **Inofarm**: aplikasi Android native (Kotlin, Jetpack Compose Material3) dan backend Laravel 13 + MySQL 8 untuk pencatatan operasional dan evaluasi keuangan greenhouse melon hidroponik. Role wire hanya `worker` (pekerja) dan `manager` (pengelola, super admin).

Tujuan sesi ini ada dua dan saling terkait:

1. **Membawa aplikasi ke kondisi layak uji coba (pilot/UAT) oleh pihak Inofarm**: database konsisten, backend aman dan bisa di-deploy, APK rilis bisa dipasang di ponsel nyata.
2. **Membuat UI terasa responsif dan hidup tanpa mengganti arah visual Canopy**: transisi antarlayar, umpan balik sentuh, simpan tanpa layar spinner, start dingin lebih cepat, dan login yang menyambung ke Beranda.

Kerjakan per fase (bagian 7), satu branch per fase, dan berhenti di setiap gerbang persetujuan yang disebut. Jangan menganggap semua fase harus selesai dalam satu sesi. Kualitas dan bukti lebih penting dari jumlah fase.

## 0. Skill dan mode kerja (wajib)

- **UI dan copy Android:** pakai skill **`impeccable`** dan **`antislop`**, ditambah turunannya **`antislop-ui`**, **`antislop-human`** (kontras, fokus, target sentuh) dan **`antislop-layoutmobile`**. Panggil lewat tool `Skill` dengan nama persis. Bila skill tidak terpasang di mesin ini (folder `.claude/skills/` tidak ikut Git), berhenti dan minta pengguna memasangnya. Jangan mengerjakan fase UI tanpa skill itu.
- **Impeccable:** jalankan `impeccable context` sekali di awal sesi dengan `--target` file Android yang dikerjakan (`.claude/skills/impeccable/scripts/impeccable.cmd` di Windows). Platform proyek ini **android**: muat referensi `android.md`, `audit.native.md`, `animate.md`. Mode permukaan: **Operate**, kecuali login yang boleh punya satu focal moment. Baca `reference/craft-floor.md` tepat sebelum edit UI pertama. Jalankan agen `impeccable-finish-reviewer` di akhir fase F3 dan F5.
- **Antislop mode: `during`.** Umumkan sekali sebelum edit UI pertama: `antislop active: during (session override).` Laporkan Delivery Gate antislop (Block 1 sampai 4) PASS/FAIL beserta bukti di akhir setiap fase UI, plus UI Skill Checklist `antislop-ui`.
- **Backend, database dan ops:** pakai skill **`ponytail`** (perubahan terkecil yang menyelesaikan masalah). **Jangan memakai `ponytail` untuk pekerjaan UI.**
- `backend/CLAUDE.md` meminta pemasangan Laravel Boost. Jangan dipasang: `backend/AGENTS.md` melarangnya.
- Bila ada keputusan yang benar-benar milik pengguna (bagian 3), tanya dengan alat pertanyaan terstruktur. Jangan menebak nilai produksi.

## 1. Mulai dari sumber yang benar

1. Jalankan `git remote -v`. **Di laptop pemilik, remote GitHub bernama `github` (`Andikarya17/Inofarm_Jogja_Capstone`), sedangkan `origin` adalah GitLab kampus (`student-git.umy.ac.id`). Fetch dan push hanya ke `github`; jangan push ke `origin`.** Di clone lain, pakai remote yang URL-nya GitHub. Pastikan `main` sama dengan `github/main` dan memuat `Merge pull request #59` (`540bf10`) atau lebih baru.
2. Periksa `git status`. Working tree pemilik berisi perubahan yang belum di-commit (skill di `.agents/`, `android/.run/…`, dua dokumen di `docs/context/`, folder inspirasi). **Jangan reset, stash, hapus, atau stage berkas yang bukan milik fase ini.** Kerjakan langsung di checkout ini: buat branch fase dengan `git switch -c codex/<fase> github/main` (perubahan pemilik ikut terbawa tanpa di-stage). Jangan memakai `git worktree`, karena worktree tidak membawa `.tools/`, `backend/vendor`, `backend/.env`, `android/local.properties`, `.runtime/` maupun skill di `.claude/skills/`. Bila `git switch` menolak karena bentrok dengan perubahan pemilik, berhenti dan tanya pengguna.
3. Baca berurutan sebelum menulis kode:
   - `CLAUDE.md`, `AGENTS.md`, `backend/AGENTS.md`, `PRODUCT.md`, `DESIGN.md` (token, `FarmMotion`, `FarmShape`, `FarmSpace`, bagian *Motion dan umpan balik*, *Canopy*, *Pekerja Canopy*, *Celah terbuka*).
   - Kedua audit di atas, lengkap.
   - `docs/context/WORKER_CANOPY_EVIDENCE_20261112.md` dan `docs/context/FRONTEND_CANOPY_EVIDENCE_20261112.md` (daftar 17 tes UI yang gagal dan pola tag tes).
   - `docs/context/MANAGER_SUPERADMIN_EVIDENCE_20261111.md`, `docs/migration/finance-20261029/DECISIONS.md` (Q07, Q08), `docs/migration/finance-20261029/FIN17_INSTALL_RUN.md`, `FIN17_CUTOVER.md`.
   - `docs/context/API_ROUTE_MAP_20261110.md`, `docs/context/DATABASE_INVENTORY_20261110.md`, `contracts/v2/README.md`.
4. Ulangi pemeriksaan baseline (bagian 8) dan catat angkanya. Angka di audit adalah hasil 14 November. Jangan menyalinnya sebagai hasilmu.
5. Kirim ringkasan singkat ke pengguna: pemahamanmu tentang temuan, urutan fase, dan daftar pertanyaan gerbang (bagian 3). **Tunggu jawaban sebelum mengubah kode.**

## 2. Keputusan yang sudah dikunci (jangan diubah)

| # | Keputusan |
|---|---|
| K1 | Pengelola adalah super admin. Pekerja hanya melihat greenhouse penugasan aktif. Batas integritas server tetap berlaku (tidak ada hard delete histori, laporan terbit immutable, saldo dijaga transaksi). |
| K2 | Alasan **wajib** untuk koreksi pekerja, keputusan "Perlu diperbaiki", recovery dan pembatalan lampiran; **opsional** untuk pengelola di tempat lain. |
| K3 | Navigasi pekerja: dua tab (Beranda, Riwayat) dan Catat di tengah dock. Navigasi pengelola: empat tab (Beranda, Riwayat, Keuangan, Kelola) dan Catat. Tag tes `finance-tab-0..`, `finance-record-fab`, `finance-login`, `finance-email`, `finance-password`, `manager-back`, `worker-home-summary` dan semua tag `$tag-option-$id` dipertahankan. |
| K4 | Arah visual **Canopy** tetap: bidang forest di atas, bidang solid di bawah, satu aksen sage, Plus Jakarta Sans. **Tidak ada warna baru** di luar token `DESIGN.md`. |
| K5 | **Tidak ada animasi tak berhingga.** Indikator berputar hanya selama proses nyata berjalan. **Angka dan nominal tidak dianimasikan** (tanpa count-up). Gerak masuk daftar tetap sekali per proses. |
| K6 | Status selalu ditulis dengan teks; warna hanya penanda. |
| K7 | Tidak ada IoT, sensor, payment gateway, piutang, atau frontend web. Launcher finance default; jangan menghidupkan API v1. |
| K8 | Uang `DECIMAL(20,2)` dan desimal di JSON berupa string. Jangan mengubah kontrak wire kecuali fase menyebutnya dan kontrak/OpenAPI ikut diperbarui. |

## 3. Gerbang keputusan pengguna (tanyakan di awal, kerjakan hanya yang disetujui)

Tanyakan dengan alat pertanyaan terstruktur. Untuk tiap butir, tawarkan rekomendasimu sebagai pilihan pertama.

| Gerbang | Pertanyaan | Mempengaruhi |
|---|---|---|
| G1 | Database mana yang akan dipakai pilot (`inofarm_finance_local` di laptop, database staging baru, atau server)? Boleh menjalankan migration 20 di sana setelah backup? | Fase D1 |
| G2 | Server staging/produksi: penyedia (VPS, server kampus), domain, siapa yang memegang akses SSH? Apakah sesi ini boleh men-deploy, atau hanya menyiapkan runbook? | Fase D5 |
| G3 | Siapa yang menyimpan keystore rilis dan password-nya? (Keystore tidak pernah masuk repo atau chat.) | Fase D4 |
| G4 | Masa sesi login: tetap 24 jam atau lebih panjang (misalnya 7 hari) untuk pekerja lapangan? | Fase D3 |
| G5 | Q07 (batas lampiran) dan Q08 (jadwal backup, retensi, RPO/RTO, operator) untuk masa pilot. | Fase D3, D5 |
| G6 | Boleh menambah dependency `androidx.profileinstaller` dan modul `:baselineprofile` (Macrobenchmark)? Boleh menambah `androidx.core:core-splashscreen` bila atribut tema saja tidak cukup? | Fase F1, F5 |
| G7 | Email terakhir di layar login boleh diingat di perangkat? (Ponsel bisa dipakai bergantian.) | Fase F2 |

Bila pengguna belum menjawab sebuah gerbang, kerjakan fase yang tidak bergantung padanya, dan tulis gerbang yang masih terbuka di laporan.

## 4. Aturan keamanan data dan lingkungan (tidak bisa ditawar)

- **Tes hanya pada database `inofarm_test`.** Jalankan `./scripts/test-backend.ps1`, yang menjalankan guard lebih dulu. Jangan menjalankan `migrate:fresh`, `migrate:reset`, `db:seed`, atau restore pada database pengguna (`inofarm_local`, `inofarm_finance_local`, atau database lain milik pengguna).
- Migration pada database pengguna hanya setelah **G1 disetujui**, dengan urutan: `migrate:status` → backup `mysqldump` ke folder di luar Git (misalnya `outputs/rollout-<tanggal>/`, jangan di-stage) → `migrate --pretend` → `migrate` → verifikasi read-only → catat hash backup di bukti.
- `scripts/local-database.ps1` adalah alat provisioning, bukan alat audit. Jangan dijalankan untuk memeriksa data.
- MySQL proyek berjalan di `127.0.0.1:3307` dengan datadir `.runtime/mysql/data`. Cek dulu apakah port 3307 sudah terbuka. Bila belum, nyalakan hanya server-nya dengan argumen yang sama seperti `scripts/local-database.ps1` baris 15–16 (`mysqld.exe --no-defaults --basedir=… --datadir=.runtime/mysql/data --port=3307 --bind-address=127.0.0.1 --log-error=.runtime/mysql/mysql.log`, biner di `C:/laragon/bin/mysql/mysql-8.0.30-winx64/bin`), **tanpa** menjalankan skrip provisioning itu. Bila kamu yang menyalakannya, beri tahu pengguna di akhir sesi supaya bisa dimatikan; akun root punya password yang tidak boleh kamu cari.
- Jangan membaca, mencetak, atau menyalin isi `.env`, kredensial, token, atau keystore ke chat, commit, atau dokumen. Jangan menimpa `JAVA_HOME` atau SDK pengguna; set variabel hanya di dalam proses perintah.
- Runner HTTP fixture/perangkat dan PHPUnit tidak boleh menulis database uji bersamaan.
- Jangan pernah membuat data produksi palsu (nilai target, dosis, harga, contoh makalah) sebagai seed/default.

## 5. Peta kode yang relevan

| Area | Lokasi |
|---|---|
| Shell, navigasi, state layar | `android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt` (±1.569 baris, rantai `when` sekitar baris 822–960, `BackHandler` sekitar 473) |
| Komponen Canopy dan gerak | `core/design/CanopyKit.kt` (`farmPressable`, `farmStaggerEntrance`, `FarmSegmentedTabs`, `FarmDockNavigation`), `core/design/InofarmTheme.kt` (`FarmMotion`, `FarmSpace`, `FarmShape`, `FarmColors`, `ForestScheme`), `core/design/FarmChrome.kt` |
| Login | `feature/auth/FinanceLogin.kt`, `AuthViewModel.kt`, `AuthRepository.kt` |
| Beranda pekerja | `feature/context/WorkerGreenhouseCards.kt`, `WorkerGreenhousePage.kt`, `WorkerDayAgenda.kt`, `NutritionRun.kt` |
| Beranda pengelola | `feature/managerhome/ManagerHomeScreen.kt`, `ManagerHomeCanopy.kt`, `RunningPeriodSheet.kt` |
| Sheet Catat, belum terkirim | `feature/ManagerSheets.kt`, `feature/QueueScreen.kt` |
| Form | `feature/FormCanopy.kt`, `feature/harvest/HarvestScreen.kt`, `feature/expenses/FinancialEntryScreen.kt` |
| Riwayat, cek catatan | `feature/history/HistoryScreen.kt`, `HistoryCanopy.kt`, `feature/verification/ActivityVerificationScreen.kt` |
| Kelola | `feature/admin/AdminScreen.kt`, `GreenhouseWizard.kt` |
| Sinkronisasi | `core/sync/FinanceSyncCoordinator.kt` (baris ±115 mengirim `harvest_reviews=1`) |
| Tema XML, ikon | `res/values/styles.xml`, `res/drawable/ic_inofarm.xml`, `AndroidManifest.xml` |
| Build Android | `android/app/build.gradle.kts`, `scripts/build-android.ps1` |
| Backend feed | `backend/app/Http/Controllers/V2/ChangeFeedController.php` (subquery `harvest_reviews` untuk non-pengelola) |
| Backend scope | `backend/app/Domain/Identity/GreenhouseAccess.php`, `app/Http/Middleware/FinanceSession.php`, `app/Http/Controllers/V2/OperationInput.php` |
| Jadwal | `backend/routes/console.php` (sekarang hanya contoh `inspire`) |

## 6. Spesifikasi gerak (acuan untuk semua fase UI)

Semua nilai masuk ke `FarmMotion`. Jangan menulis angka durasi di layar.

| Pola | Dipakai untuk | Durasi dan easing |
|---|---|---|
| Tekan | Semua kontrol yang bisa diketuk | Skala 0,97 (tombol) / 0,98 (kartu) dengan spring redaman 0,7 (sudah ada) **plus ripple Material 3** |
| Status | Chip, warna, label tombol | 200 ms, `FastOutSlowInEasing`; crossfade per nilai |
| Shared axis X | Masuk ke kedalaman (kartu → halaman greenhouse, daftar → detail, sheet → form) dan kembali | Masuk 300 ms: fade + geser 30 dp dari arah maju; keluar 250 ms ke arah berlawanan. Kembali membalik arah. `EmphasizedDecelerate` masuk, `EmphasizedAccelerate` keluar |
| Fade-through | Antartab dock | Keluar 90 ms fade; masuk 210 ms fade + skala 0,92 → 1 |
| Placement daftar | Baris masuk/keluar/berpindah | `Modifier.animateItem()` bawaan |
| Bagian terbuka/tertutup | Rincian, sumber, opsional | `animateContentSize` 180–200 ms (sudah ada) |
| Focal: kanopi | Login → Beranda; kartu greenhouse → hero greenhouse | Maksimal 400 ms, sekali per alur |
| Back prediktif | Halaman greenhouse, detail riwayat | Skala 1 → 0,92 dan geser tepi mengikuti progres gesture; batal kembali dengan spring |
| Haptik | Momen bermakna saja | `Confirm` (diterima server, keputusan Sesuai), `Reject` (login gagal, validasi menolak), `SegmentTick` (tab/chip berganti) |

Aturan: keluar lebih cepat dari masuk. Hanya animasikan `graphicsLayer` (alpha, translasi, skala) dan ukuran. Tanpa blur, shimmer berulang, atau partikel. Baca state animasi di dalam lambda `graphicsLayer`, bukan di komposisi. Saat "Hapus animasi" aktif di sistem, hasilnya cut atau crossfade instan yang tetap menunjukkan perubahan state, dan jeda stagger ikut nol (FE-P3-1).

## 7. Fase

Branch berprefix `codex/` dari `main` remote terbaru, satu branch per fase, bertumpuk bila fase bergantung pada fase sebelumnya. Setiap fase berakhir dengan pemeriksaan bagian 8, commit kecil per perubahan logis, dan catatan bukti. Fase D dan F boleh berselang-seling. Urutan yang disarankan: **D0 → D1 → D2 → D3 → F2 → F1 → F3 → F4 → F5 → D4 → Q1 → D5 → Q2**.

### D0. Baseline dan reproduksi (`codex/deploy-baseline`)

- Jalankan semua pemeriksaan bagian 8 dan catat angkanya.
- Buktikan P0-1 di `inofarm_test`: tes fitur yang mengosongkan schema `harvest_reviews` tidak praktis, jadi buat tes yang memanggil `GET /api/v2/sync/changes?activity_reviews=1&measurement_review_status=1&harvest_reviews=1` sebagai pekerja dan memastikan 200. Lalu tulis di bukti bahwa kegagalan di database aplikasi berasal dari migration Pending, ditunjukkan oleh `php artisan migrate:status` pada koneksi efektif. Jangan mengubah database pengguna di fase ini.
- Penerimaan: angka baseline tercatat; P0-1 terbukti atau terbantah dengan bukti.

### D1. Database dan penjaga migration (`codex/deploy-migration-guard`) — butuh G1

- Tambahkan penjaga supaya kejadian P0-1 tidak terulang diam-diam. Pilih yang paling kecil: endpoint `GET /api/v2/health` tanpa auth yang mengembalikan versi schema dan `pending_migrations: true/false` (tanpa nama tabel atau detail internal), **atau** perintah artisan `inofarm:preflight` yang gagal bila ada migration pending. Endpoint baru wajib masuk OpenAPI dan tes parity.
- Bila G1 disetujui: jalankan prosedur migration bagian 4 pada database yang dipilih, lalu verifikasi read-only bahwa tabel `harvest_reviews` ada dan sinkronisasi pekerja ke server lokal berhasil (`sync/changes` 200 sebagai pekerja).
- Nonaktifkan penugasan aktif milik akun pengelola (P2-1) **lewat API**, bukan SQL langsung, dan hanya bila pengguna setuju.
- Penerimaan: penjaga teruji; migration (bila disetujui) tercatat dengan hash backup; tidak ada perubahan pada database lain.

### D2. Patch dependency (`codex/deploy-dependency-patch`)

- `composer update "symfony/*" laravel/framework --with-dependencies` dalam constraint yang ada. Tanpa major upgrade.
- `composer audit --locked` sampai bersih, atau setiap sisa advisory dicatat beserta alasan dan rencananya.
- Jalankan ulang `test-backend.ps1` dan pemeriksa kontrak.
- Penerimaan: suite hijau; `composer.lock` berubah hanya pada paket terkait; daftar versi sebelum/sesudah ada di bukti.

### D3. Hardening backend (`codex/deploy-backend-hardening`)

- **P3-1:** di controller khusus pengelola, periksa role sebelum validasi payload, supaya pekerja mendapat 403 seragam. Tambahkan tes "pekerja + payload kosong = 403" untuk setiap rute tulis khusus pengelola (expenses, cost-allocations, sales, assets, depreciation-allocations, investment-scenarios, master/greenhouses/seasons/…, schedules, targets, users, activity-reviews, harvest-reviews, harvest-completion, running-period, close/reopen musim, season/investment preview dan report, recovery). Jangan mengubah urutan untuk rute yang dipakai pekerja (activities, harvests).
- **P0-5:** jadwalkan `sanctum:prune-expired --hours=24` di `routes/console.php`. Siapkan skrip backup (`scripts/backup-mysql.ps1` dan/atau `.sh` untuk server Linux) yang membaca kredensial dari env, menulis ke direktori di luar webroot, memberi nama bertanggal, dan menyimpan checksum. Uji hanya terhadap `inofarm_test` dan restore ke database terpisah yang dibuat khusus.
- **G4:** bila disetujui, ubah default `FINANCE_SESSION_SECONDS` lewat env contoh saja (tanpa mengubah kode bila cukup).
- Tambahkan `backend/.env.production.example` **tanpa nilai rahasia**: `APP_ENV=production`, `APP_DEBUG=false`, `LOG_LEVEL=warning`, `SESSION_DRIVER`, `FINANCE_*` yang relevan, dan komentar untuk `TrustProxies`.
- Penerimaan: tes baru lulus; probe pekerja dari audit (23 GET, 28 POST) menghasilkan 403/404 untuk semua yang khusus pengelola; tidak ada perubahan kontrak sukses.

### D4. APK rilis (`codex/deploy-android-release`) — butuh G3

- Tambahkan `signingConfigs.release` yang membaca path keystore, alias dan password dari `~/.gradle/gradle.properties` atau env (misalnya `INOFARM_RELEASE_STORE_FILE`). Bila properti tidak ada, build rilis tetap jalan tanpa tanda tangan dan mencetak peringatan, sehingga CI dan mesin lain tidak rusak.
- `inofarmFinanceApiUrl` wajib HTTPS untuk rilis. Tambahkan pemeriksaan Gradle yang gagal bila rilis dirakit dengan URL kosong atau `http://`.
- Naikkan `versionCode`/`versionName`. Rakit `assembleRelease`, pasang di emulator/perangkat, dan uji login → Beranda → catat → kirim terhadap server staging (bila ada) untuk memastikan minify tidak merusak JSON manual atau reflection.
- Penerimaan: APK rilis bertanda tangan terpasang dan berjalan; tidak ada rahasia di repo (`git grep` untuk kata kunci password/keystore bersih).

### D5. Runbook deployment (`codex/deploy-runbook`) — butuh G2, G5

- Tulis `docs/deployment/PILOT_DEPLOYMENT.md`:
  - spesifikasi server (PHP sesuai `composer.lock` ≥ 8.4.1 beserta extension dari `composer check-platform-reqs`, MySQL 8, Nginx + PHP-FPM, TLS Let's Encrypt);
  - langkah install;
  - `.env` produksi;
  - `php artisan migrate --force` dengan backup lebih dulu;
  - cron `schedule:run`;
  - backup harian dan uji restore;
  - pemantauan log;
  - rotasi `APP_KEY` (jangan);
  - rollback;
  - checklist "sebelum mengundang Inofarm".
- Perbarui `FIN17_INSTALL_RUN.md` yang usang (schema 15/SQLite 9, "assignment pencipta") dengan menautkan runbook baru. Jangan menghapus histori.
- Deploy ke server **hanya** bila G2 secara eksplisit mengizinkan dan pengguna memberi akses. Jangan meminta password atau kunci lewat chat; minta pengguna menjalankan langkah yang butuh rahasia.
- Penerimaan: runbook bisa diikuti dari server kosong; setiap perintah sudah diuji di staging atau diberi tanda "belum diuji".

### F1. Responsivitas dan performa (`codex/frontend-performance`) — Baseline Profile butuh G6

- **FE-P1-1:** bila G6 disetujui, tambahkan `androidx.profileinstaller` dan modul `:baselineprofile` yang merekam login, Beranda pekerja, Beranda pengelola, Riwayat dan membuka form. Ukur start dingin (`StartupTimingMetric`) dan jank gulir (`FrameTimingMetric`) sebelum dan sesudah. Emulator hanya memberi angka indikatif; catat perangkatnya.
- **FE-P2-5:** turunkan `FinanceDestination` (sealed interface) dari state yang ada dengan `derivedStateOf`, tanpa mengganti sumber state. Pindahkan cabang besar `when` ke composable sendiri dengan parameter stabil. Hapus pembangunan list di dalam cabang (contoh: `NutritionRunItem`).
- **FE-P2-6:** `stamp` di `rememberWorkerHome` dibungkus `remember`/`derivedStateOf`.
- **FE-P2-7:** daftar `AdminScreen` ke `LazyColumn` dengan `key`, dan saring dengan `remember(rows, search)`.
- Penerimaan: perilaku dan tag tidak berubah; JVM test dan instrumentasi terkait lulus; angka ukur (bila ada) dicatat beserta perangkat. Jangan mengklaim peningkatan tanpa pengukuran.

### F2. Hardening interaksi (`codex/frontend-harden`)

- **FE-P1-2:** hapus layar spinner penuh untuk `*.busy` dan `runPhase == 4`. Form atau langkah Periksa tetap terlihat, tombol utama masuk status sibuk di tempat (label "Menyimpan…"/"Mengirim…" dan indikator kecil, pola tombol Masuk), kontrol lain nonaktif, dan Back ditahan selama batas simpan lokal. Indikator baru muncul setelah ±300 ms.
- **FE-P1-5:** autofill login dengan `semantics { contentType = ContentType.EmailAddress }` (atau `Username`) dan `ContentType.Password`. Uji dengan layanan autofill di emulator.
- **FE-P2-3:** placeholder statis berbentuk kartu di Beranda pekerja (dan pengelola bila perlu), tampil bila pemuatan > 150 ms, crossfade 200 ms ke kartu nyata. Tanpa shimmer berulang.
- **FE-P3-4:** bila G7 disetujui, isi email terakhir di login dan sediakan aksi "Bukan saya".
- Penerimaan: tes UI form dan login lulus; screenshot sebelum/sesudah alur simpan menunjukkan form tetap terlihat; TalkBack mengumumkan status sibuk lewat live region.

### F3. Transisi dan kontinuitas (`codex/frontend-motion`)

Tulis dulu motion thesis singkat (focal moment, kontinuitas, umpan balik, anggaran) sesuai `animate.md`, lalu implementasikan:

- **FE-P1-3:** satu `AnimatedContent(targetState = destination)` di area isi `FinanceRoot`, dengan `transitionSpec` dari tabel bagian 6. Arah maju/mundur ditentukan dari kedalaman tujuan. Konten yang keluar memakai snapshot tujuannya sendiri (jangan membaca state terbaru). Pergantian cakupan greenhouse di Beranda pengelola **tidak** dianimasikan. Tab memakai fade-through dan menggantikan animasi tab pekerja yang ada (alpha 0,74).
- **FE-P1-6:** `Modifier.animateItem()` pada Riwayat, Cek catatan, Belum terkirim dan daftar Beranda. Di Cek catatan, chip keputusan tampil sekitar 150 ms sebelum baris keluar.
- **FE-P2-4:** `PredictiveBackHandler` untuk halaman greenhouse dan detail. Form dengan draf tetap memakai `BackHandler` yang ada.
- **Focal moment login → Beranda:** bidang forest login menyusut menjadi hero Beranda (maksimal 400 ms). Kartu greenhouse → hero halaman greenhouse sebagai container transform sederhana bila `SharedTransitionLayout` stabil di BOM yang dipakai. Bila tidak stabil, pakai shared axis X dan catat alasannya.
- Penerimaan: semua tes UI navigasi lulus (`ManagerNavigationTest`, tes dock pekerja, `FinanceEntryUiTest` per nama dibanding baseline); rekaman layar emulator (`adb shell screenrecord`) untuk kartu → greenhouse, Riwayat → detail, form → Periksa → kirim, dan login → Beranda; dengan "Hapus animasi" aktif, semua alur tetap benar.

### F4. Umpan balik yang hidup (`codex/frontend-delight`)

- **FE-P2-1:** haptik `Confirm`/`Reject`/`SegmentTick` hanya di momen pada tabel bagian 6, lewat satu helper (misalnya `rememberFarmHaptics()`) supaya mudah dimatikan.
- **FE-P2-11:** momen "terkirim". Indikator `N belum terkirim` crossfade per nilai. Ikon sinkron berputar hanya selama sinkronisasi berjalan. Saat antrean kosong, chip berubah menjadi centang sage "Semua terkirim" selama 1,5 detik lalu menghilang. Live region mengumumkan "Semua catatan terkirim".
- Login: haptik `Reject` dipasangkan dengan getar visual yang ada.
- Penerimaan: tidak ada animasi tanpa pemicu state; Delivery Gate antislop Block 2 (Purpose-Gate) PASS dengan alasan tertulis per animasi.

### F5. Adaptasi dan polish (`codex/frontend-polish`)

- **FE-P1-4:** ripple Material 3 pada 6 kontrol `clickable(…, null, …)`, bersama `farmPressable`.
- **FE-P2-2:** `PullToRefreshBox` di Beranda dan Riwayat pekerja serta Riwayat/Keuangan pengelola. Tombol Perbarui tetap ada.
- **FE-P2-8:** target sentuh tab segmen menjadi 48 dp.
- **FE-P2-9:** tema jendela dan splash. `windowBackground` dan atribut splash Android 12 memakai forest `#132820`; buat `values-night`; hapus `colorAccent #006C49`. Splash harus mengalir ke kanopi login tanpa kilatan putih, di mode terang maupun gelap.
- **FE-P2-10:** adaptive icon (`mipmap-anydpi-v26`) dengan latar brand/forest, foreground sage dari motif yang ada, dan layer monochrome. Hapus ikon lama setelah adaptive icon terpasang.
- **FE-P3-1:** jeda stagger ikut skala animasi sistem.
- **FE-P3-2:** pindahkan warna mentah di `CanopyKit.kt`/`FarmChrome.kt` ke token.
- **FE-P3-3:** hapus `background_login.jpg` bila memang tidak dipakai (cek `git grep` lebih dulu).
- Tutup celah `DESIGN.md` yang terjangkau: pemisah ribuan tampilan nominal (hanya tampilan; nilai yang divalidasi dan dikirim tetap tanpa pemisah), tanggal dan jam form kegiatan berdampingan, tombol `Tambah …` bergaris putus.
- Penerimaan: screenshot emulator terang, gelap, font 200% dan lebar 360 dp untuk login, Beranda kedua peran, halaman greenhouse, form, Riwayat dan Kelola; kontras token baru (bila ada) diukur dengan checker `antislop-human`; `impeccable-finish-reviewer` dijalankan dan temuan materialnya ditangani.

### Q1. Triase tes UI (`codex/qa-ui-tests`)

- Jalankan `FinanceEntryUiTest` penuh dan kelas UI lain. Untuk setiap nama yang gagal (baseline 17), tentukan apakah **tes usang** (selektor/teks lama setelah Canopy) atau **bug nyata**. Perbaiki tes usang tanpa menghapus assertion; perbaiki bug di kode.
- Bila tes flaky karena sinkronisasi awal, ganti `Thread.sleep`/klik Perbarui dengan `waitUntil` pada kondisi nyata.
- Penerimaan: daftar per nama dengan status akhir; target 0 gagal konsisten; flake dicatat dengan jumlah ulangan.

### Q2. Dry run perangkat dan dokumen (`codex/pilot-dry-run`)

- Dengan dua ponsel Android 10 ke atas (satu pekerja, satu pengelola) atau emulator bila ponsel tidak ada, terhadap server staging, jalankan skenario:
  1. login;
  2. pekerja mencatat kegiatan, pengukuran dan panen dalam keadaan offline, lalu online;
  3. pengelola Cek catatan "Perlu diperbaiki" → pekerja koreksi → pengelola "Sesuai";
  4. panen → penjualan → biaya → laporan musim → ekspor PDF/CSV;
  5. logout dengan draf tersisa;
  6. Doze dan restart ponsel dengan antrean berisi;
  7. TalkBack pada login, Beranda, satu form.
- Catat bukti di `docs/context/PILOT_READINESS_EVIDENCE_<tanggal>.md`. Perbarui `CLAUDE.md` (status singkat dan tautan), `DESIGN.md` (bagian gerak baru, celah yang tertutup), `GAP_ANALYSIS.md`, serta status temuan di kedua audit (jangan menghapus temuan; tandai selesai beserta commit dan bukti).

## 8. Pemeriksaan wajib setiap fase

```powershell
./scripts/test-backend.ps1
./scripts/build-android.ps1 -JavaHome "C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
./.tools/finance-contracts-venv/Scripts/python.exe scripts/check-finance-contracts.py
python scripts/check-finance-context.py
```

- Fase backend: suite backend dan guard lulus, plus `composer audit --locked`.
- Fase Android: `assembleDebug`, `testDebugUnitTest`, `lintDebug` (error 0; jumlah warning tidak naik dari baseline tanpa alasan), `assembleDebugAndroidTest`, dan kelas instrumentasi yang menyentuh layar yang diubah.
- Lokasi `.tools` dan JDK spesifik laptop pemilik. Bila tidak ada, pakai tooling yang kompatibel dan catat versinya.
- Screenshot dan rekaman dari emulator atau perangkat (`adb exec-out screencap -p`, `adb shell screenrecord`). Baca dan pulihkan `uimode night` dan `font_scale` setelah dipakai. Sebut perangkat sumber setiap bukti.
- Bila emulator macet karena memori, catat itu sebagai batas bukti. Jangan mengklaim verifikasi visual.
- Bedakan tegas: **diuji** (dengan output), **dibaca dari kode**, dan **belum diverifikasi**. Mock HTTP bukan bukti backend nyata; emulator bukan bukti perangkat fisik.

## 9. Git

- Branch `codex/<fase>` dari `main` remote. Commit kecil per perubahan logis, pesan konvensional (`feat(motion): …`, `fix(api): …`, `docs(deploy): …`), dan akhiri pesan dengan baris atribusi yang diminta sistem.
- Stage hanya file fase ini. Jangan stage `.env*` (kecuali `.example` tanpa rahasia), dump, log, `outputs/`, `.runtime/`, `build/`, keystore, folder inspirasi UI, atau skill lokal.
- `git fetch github` sebelum push, lalu `git push -u github codex/<fase>`. Push hanya branch fase ke remote `github`, tanpa force-push. **Jangan merge ke `main` dan jangan membuat PR tanpa izin pengguna.** Bila pengguna meminta PR, pastikan tidak ada konflik (`git merge-tree`) dan pemeriksaan bagian 8 lulus pada ujung branch.

## 10. Laporan di akhir setiap fase

Tulis ke pengguna dalam bahasa Indonesia yang sederhana:

1. Apa yang berubah (file dan perilaku), per kode temuan.
2. Hasil pemeriksaan dengan angka dari output perintah, dibanding baseline.
3. Bukti visual atau gerak (path screenshot/rekaman dan perangkatnya).
4. Delivery Gate antislop dan UI Skill Checklist (fase UI).
5. Yang belum selesai atau belum terverifikasi, dan gerbang yang masih menunggu keputusan.
6. Usulan fase berikutnya.

Di akhir sesi, perbarui baris status di kedua audit dan `CLAUDE.md`. Jangan menyatakan aplikasi "siap produksi" kecuali semua P0 di audit deployment tertutup dengan bukti, dan UAT dengan Inofarm sudah dilaksanakan dan diterima.
