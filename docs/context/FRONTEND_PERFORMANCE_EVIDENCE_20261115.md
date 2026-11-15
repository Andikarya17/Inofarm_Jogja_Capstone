# Bukti fase F1: responsivitas dan performa (15 November 2026)

Branch `codex/frontend-performance`, bertumpuk di atas `codex/frontend-harden` (F2). Temuan dari [audit frontend](FRONTEND_MOTION_AUDIT_20261114.md): FE-P1-1, FE-P2-5 (sebagian), FE-P2-6, FE-P2-7. Perilaku, teks dan tag tes tidak berubah. Backend, API, kontrak dan schema SQLite tidak berubah.

Label bukti: **diuji** (ada output perintah), **dibaca dari kode**, **belum diverifikasi**. Semua angka berasal dari emulator `Inofarm_Botanical_API30` (Android 11, x86_64, `sdk_gphone_x86_64`), bukan perangkat fisik.

## Perubahan per temuan

### FE-P1-1: Baseline Profile (G6)

- `android/app/build.gradle.kts`: dependency `androidx.profileinstaller:profileinstaller:1.4.1`, plugin `androidx.baselineprofile` 1.5.0, `baselineProfile { mergeIntoMain = true }` supaya satu profil berlaku untuk semua build type.
- Modul baru `android/baselineprofile` (`com.android.test`, `androidx.benchmark:benchmark-macro-junit4:1.5.0`, UiAutomator 2.3.0):
  - `LoginJourney.kt`: start dingin sampai form Masuk, ketuk dan isi Email, tampilkan password.
  - `BaselineProfileGenerator.kt`: `BaselineProfileRule`, dijalankan dengan `:app:generateBaselineProfile`.
  - `StartupBenchmark.kt`: start dingin 10 iterasi dengan `StartupTimingMetric` dan `FrameTimingMetric`, tanpa profil (`CompilationMode.None`) dan dengan profil (`CompilationMode.Partial(BaselineProfileMode.Require)`).
  - Argumen `androidx.benchmark.suppressErrors=EMULATOR` supaya modul bisa dijalankan di emulator proyek. Komentar di berkas menegaskan angka emulator hanya indikatif.
- Profil hasil rekaman: `android/app/src/main/generated/baselineProfiles/baseline-prof.txt` dan `startup-prof.txt` (masing-masing 1,9 MB, 18 ribu baris, 823 aturan untuk paket `com/inofarm/jogja`). Sisanya aturan Compose dan AndroidX.
- **Batas cakupan:** alur yang direkam hanya start dingin dan login. Beranda pekerja, Beranda pengelola, Riwayat dan form membutuhkan sesi; build rilis hanya menerima backend HTTPS dan tidak ada akun rilis yang boleh dipakai di sini. Profil untuk layar setelah login belum direkam.

### FE-P2-6: `stamp` di `rememberWorkerHome`

- `WorkerGreenhouseCards.kt`: `stamp` dibungkus `remember(data.greenhouses, data.seasons, data.groups, data.schedules, data.targets)`. Daftar ini dibangun sekali per daftar yang dimuat, bukan pada setiap recomposition Beranda.

### FE-P2-7: daftar Kelola

- `AdminScreen.kt`: isi daftar dipindah dari `Column` + `verticalScroll` + `forEach` ke `LazyColumn`. Pesan, status muat, detail, dan panel pilihan menjadi item tetap; baris data memakai `items` dengan kunci `id`. Bila satu halaman punya id kosong atau ganda, kunci jatuh ke posisi supaya tidak crash.
- Penyaringan pencarian dipindah ke `remember(state.rows, state.resource, search)`, jadi tidak dihitung ulang saat recomposition lain.

### FE-P2-5 (sebagian): root Compose

- `FinanceRoot.kt`: daftar `NutritionRunItem` untuk langkah Periksa "Cek nutrisi semua greenhouse" tidak lagi dibangun di dalam cabang `when`; kini `remember(runSaved, activity.records, context.greenhouses)`.
- **Belum dikerjakan:** kunci tujuan `FinanceDestination` dan pemindahan cabang besar ke composable sendiri. Kunci tujuan dibuat di fase F3 bersama `AnimatedContent`, karena di sana kunci itu langsung dipakai. Pemindahan cabang ke composable sendiri tidak dikerjakan pada fase ini: rantai `when` memegang sekitar 40 state dan baseline tes UI masih punya kegagalan, sehingga risiko regresinya tidak sebanding tanpa pengukuran recomposition yang menunjukkan masalah.

## Pemeriksaan

| Pemeriksaan | Hasil | Label |
|---|---|---|
| `scripts/build-android.ps1` `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebugAndroidTest` | BUILD SUCCESSFUL; JVM 112 tes, 0 gagal; lint 0 error, 18 warning (tidak naik; 5 `GradleDependency`, 5 `UseKtx`, 4 `NewerVersionAvailable`, 2 `ConfigurationScreenWidthHeight`, 1 `DataExtractionRules`, 1 `UnusedResources`) | Diuji |
| `:baselineprofile:assemble` | BUILD SUCCESSFUL | Diuji |
| `:app:generateBaselineProfile` (emulator dijalankan sebagai root lewat `adb root`, syarat `BaselineProfileRule` di bawah API 33) | BUILD SUCCESSFUL, profil tertulis ke `src/main/generated/baselineProfiles` | Diuji |
| `:baselineprofile:connectedBenchmarkReleaseAndroidTest` | 2 tes benchmark lulus (lihat angka di bawah) | Diuji |
| `FinanceAdminUiTest` | OK (2 tests) | Diuji |
| `FinanceAdminTest` | OK (6 tests) | Diuji |
| `WorkerContextUiTest` | OK (1 test) | Diuji |
| `InteractionHardeningUiTest` | OK (3 tests) | Diuji |
| `FinanceEntryUiTest#workerHomeCardShowsLastMeasurementAndSaveMessageAfterRecording`, `#nutritionRunSavesOneMeasurementPerGreenhouseSkipsOneAndSendsSeparately` | Lulus | Diuji |
| `FinanceEntryUiTest#workerHomeShowsGreenhouseCardsAndCorrectionsCardWithoutOpeningAGreenhouse` | Gagal pertama kali (`RootViewWithoutFocusException`) karena dialog sistem "Application Error: com.google.android.googlequicksearchbox" memegang fokus emulator. Setelah dialog ditutup: lulus 2 dari 2 | Diuji |

### Angka start dingin (indikatif, emulator)

Macrobenchmark 1.5.0, 10 iterasi start dingin per mode, alur `loginJourney`. Ringkasan disimpan di `docs/context/evidence/f1-20261115/startup-startupWithoutProfile.txt` dan `startup-startupWithProfile.txt`.

| Metrik | Tanpa profil | Dengan profil |
|---|---|---|
| `timeToInitialDisplayMs` median | 1.486,3 | 1.465,8 |
| `timeToInitialDisplayMs` min / max | 1.339,6 / 2.781,2 | 1.200,8 / 2.272,7 |
| `frameDurationCpuMs` P50 / P90 / P99 | 233,4 / 653,3 / 1.302,3 | 221,4 / 543,7 / 1.488,5 |

Selisih median 20,5 ms (1,4%) masih dalam sebaran antariterasi emulator (min sampai max lebih dari 1 detik). **Tidak ada klaim peningkatan.** Emulator x86 tidak mewakili ART di ponsel kelas menengah; pengukuran yang bermakna butuh satu perangkat fisik Android 10 ke atas dengan perintah yang sama.

## Catatan lingkungan

- Emulator dibuat root (`adb root`) untuk merekam profil, dan dikembalikan dengan `adb unroot` di akhir sesi.
- Dialog galat aplikasi Google Search muncul di emulator dan mengambil fokus jendela. Dialog ini menjelaskan setidaknya satu kegagalan Espresso pada sesi ini. Tes lampiran `attachmentReason…` tetap gagal sebagian walau dialog tidak ada (lulus 1 dari 3 masing-masing); diserahkan ke Q1.

## Batas dan yang belum diverifikasi

- Profil hanya mencakup start dan login; layar setelah login belum direkam.
- Tidak ada pengukuran di perangkat fisik.
- Jank gulir Beranda, Riwayat dan Kelola belum diukur.
- FE-P2-5 baru sebagian (lihat di atas).
