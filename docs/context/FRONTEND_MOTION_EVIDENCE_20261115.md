# Bukti fase F3: transisi dan kontinuitas (15 November 2026)

Branch `codex/frontend-motion`, bertumpuk di atas `codex/frontend-performance` (F1) dan `codex/frontend-harden` (F2). Temuan dari [audit frontend](FRONTEND_MOTION_AUDIT_20261114.md): FE-P1-3, FE-P1-6, FE-P2-4, dan momen fokus login ke Beranda. Backend, API, kontrak dan schema SQLite tidak berubah.

Label bukti: **diuji** (ada output perintah), **dibaca dari kode**, **belum diverifikasi**. Bukti layar berasal dari emulator `Inofarm_Botanical_API30` (Android 11, 1080x2340, 440 dpi) dengan fixture, bukan perangkat fisik atau backend nyata.

## Motion thesis

- **Momen fokus:** kanopi forest login adalah bidang yang sama dengan bidang forest di atas Beranda. Setelah login diterima, bidang itu tetap di tempat lalu melipat ke tepi atas dalam 400 ms sementara Beranda muncul di bawahnya. Satu kali per login, bukan pada setiap start (sesi tersimpan langsung membuka Beranda).
- **Kontinuitas:** kedalaman tujuan menentukan arah. Masuk ke halaman, form, detail atau Periksa bergeser maju (shared axis X); kembali bergeser mundur; ketukan dock adalah fade-through; baris daftar memakai `animateItem`.
- **Umpan balik:** Back prediktif menunjukkan layar yang akan ditinggalkan mengecil ke tepi gesture sebelum dilepas. Tombol sibuk F2 tetap dipakai untuk simpan.
- **Anggaran:** hanya alpha, translasi dan skala pada `GraphicsLayer`, dibaca di fase gambar sehingga transisi tidak memicu recomposition per frame. Tanpa blur, shimmer, loop atau animasi angka. Semua durasi di `FarmMotion`; keluar selalu lebih cepat dari masuk.

## Perubahan per temuan

### FE-P1-3: transisi antarlayar

- Berkas baru `feature/DestinationMotion.kt`:
  - `FinanceDestination` (Tab, Page, Detail, Form, Review dengan kedalaman 0 sampai 3) dan `financeTransition(from, to, dockChanged)`.
  - `FarmDestinationMotion`: isi shell direkam ke `GraphicsLayer` setiap frame. Saat tujuan berubah, layer terakhir dipindah menjadi gambar keluar dan layer baru dibuat (lewat `SideEffect`, setelah komposisi dikomit dan sebelum frame berikutnya digambar). Layar yang keluar karenanya selalu frame terakhirnya sendiri, tidak pernah membaca state layar berikutnya, dan tidak punya node semantik. Layar yang masuk hidup sejak frame pertama.
  - Transisi yang terputus di tengah jalan memakai alpha, translasi dan skala layar setengah-masuk sebagai titik awal keluarnya. Layer yang sudah tidak digambar dilepas dua frame setelah transisi selesai, supaya render thread sudah selesai memakainya.
- `FinanceRoot.kt`: `destination` diturunkan dari state yang ada, dengan urutan sama seperti rantai layar. Pergantian cakupan greenhouse di Beranda pengelola tetap `Tab(0)` sehingga tidak dianimasikan (sesuai audit). Animasi masuk tab pekerja lama (alpha 0,74 dan naik 5 dp) dihapus dan digantikan fade-through. Ketukan dock (`tab` berubah) selalu fade-through, termasuk dari halaman greenhouse pekerja.
- `InofarmTheme.kt`, `FarmMotion`: `Depth` 300, `DepthExitShare` 250/300, `TabExit` 90, `TabEnterScale` 0,92, `BackPreviewScale` 0,92, `BackPreviewShift` 8 dp, `Focal` 400, `EmphasizedDecelerate` dan `EmphasizedAccelerate` (kurva Material 3).
- Kunci tujuan menutup bagian FE-P2-5 yang tertunda di F1.

### FE-P1-6: baris daftar

- `Modifier.animateItem()` pada baris Riwayat (`HistoryScreen`), inbox dan riwayat keputusan Cek catatan (`ActivityVerificationScreen`), Belum terkirim (`QueueScreen`), daftar koreksi pekerja (`WorkerGreenhouseCards`) dan kartu greenhouse Beranda pengelola (`ManagerHomeScreen`).
- Judul bagian Belum terkirim ("Ditolak, perlu dicek", "Menunggu dikirim") kini item berkunci sendiri, sehingga mengirim catatan pertama sebuah kelompok tidak ikut memudarkan judulnya.
- **Belum:** jeda 150 ms yang menampilkan chip keputusan sebelum baris keluar dari inbox Cek catatan. Daftar kartu greenhouse pekerja di Beranda masih satu item (`forEach`), jadi belum memakai `animateItem`.

### FE-P2-4: Back prediktif

- Satu aksi Back (`handleBack`) dipakai bersama oleh `BackHandler` dan `PredictiveBackHandler`.
- Pratinjau aktif untuk halaman greenhouse pekerja dan detail catatan (Riwayat dan keuangan), hanya bila tidak sedang menyimpan, tidak ada perbandingan versi terbuka, keyboard tertutup, dan antrean tidak sedang dibuka. Layar mengecil 1 ke 0,92 dan bergeser 8 dp ke arah tepi gesture. Batal mengembalikannya dengan spring yang dihentikan bila gesture baru dimulai atau tujuan berubah. Saat dilepas, layar keluar mulai dari posisi pratinjau.
- Detail Cek catatan kini punya tujuan sendiri (`Detail("verification")`) untuk transisi, tetapi tanpa pratinjau karena layar itu memegang `BackHandler` sendiri.
- Form dengan draf tetap memakai `BackHandler` biasa.

### Momen fokus login ke Beranda

- `FinanceLogin.kt`, `LoginCanopyHandoff`: bidang forest setinggi kanopi login (rumus tinggi yang sama), digambar di atas shell, solid pada separuh pertama lalu memudar, melipat ke tepi atas dengan `FastOutSlowInEasing` dalam 400 ms waktu linear. Tanpa sentuhan dan disembunyikan dari TalkBack. Hanya muncul setelah login di proses yang sama.
- `SharedTransitionLayout` tidak dipakai untuk kartu greenhouse ke halaman greenhouse: di Compose BOM 2025.08.01 API itu masih `@ExperimentalSharedTransitionApi`. Kartu ke halaman memakai shared axis X.

## Tinjauan `impeccable-finish-reviewer`

Agen dijalankan pada versi pertama F3 dan mengembalikan delapan perbaikan material. Semuanya ditangani sebelum commit:

| # | Temuan | Penanganan |
|---|---|---|
| 1 | Pratinjau Back bisa macet saat keyboard terbuka atau antrean dibuka | Keduanya dikecualikan; pratinjau selalu kembali ke 0 dua frame setelah `handleBack` |
| 2 | Spring batal tidak dihentikan; layar keluar melompat dari offset pratinjau | `Job` disimpan dan dibatalkan; keluar mulai dari skala dan offset pratinjau |
| 3 | Transisi terputus membuat layar keluar meloncat; layer dibuat/dilepas saat komposisi | Titik awal keluar dari alpha/translasi/skala saat itu; tukar layer di `SideEffect`; lepas layer tertunda dua frame |
| 4 | Pratinjau macet bila Back prediktif dinonaktifkan di tengah gesture | `LaunchedEffect(destination, previewBack)` mengembalikan pratinjau ke 0 |
| 5 | Ketukan dock dari halaman greenhouse memakai shared axis | Perubahan `tab` selalu fade-through (`dockChanged`) |
| 6 | Detail Cek catatan tanpa transisi | Tujuan `Detail("verification")`; pratinjau tidak dipasang (lihat atas) |
| 7 | Judul bagian Belum terkirim ikut memudar | Judul menjadi item berkunci sendiri |
| 8 | Kanopi habis dalam sekitar 100 ms | Waktu linear, lipatan dengan `FastOutSlowInEasing`; bingkai seperempat kini masih solid |

## Pemeriksaan

| Pemeriksaan | Hasil | Label |
|---|---|---|
| `scripts/build-android.ps1` `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebugAndroidTest` | BUILD SUCCESSFUL; JVM 116 tes, 0 gagal (tambah 4 di `FinanceTransitionTest`); lint 0 error, 18 warning (tidak naik) | Diuji |
| `DestinationMotionUiTest` (2 tes) dan `InteractionHardeningUiTest` (3 tes), terang dan gelap | OK (5 tests) di masing-masing mode | Diuji |
| `FinanceEntryUiTest#nutritionRunSavesOneMeasurementPerGreenhouseSkipsOneAndSendsSeparately`, `#workerHomeShowsGreenhouseCardsAndCorrectionsCardWithoutOpeningAGreenhouse` (versi pertama F3) | OK (2 tests) | Diuji |
| `FinanceEntryUiTest` penuh, emulator `-gpu swiftshader_indirect` | 65 tes, 16 gagal, tanpa crash | Diuji |

### `FinanceEntryUiTest` dibanding baseline

Gagal (16): `assetConflictReviewPreservesRawLocalNameAndOldOperation`, `attachmentReviewRemainsReachableAtLargeFontNarrowAndLandscape`, `conflictKeepLocalRequiresReasonAndManagerCanAuditAndCancel`, `conflictUseServerPreservesLocalCopyAndWorkerHasNoCancel`, `harvestBalanceRejectionCanBeCorrectedAfterExplicitReview`, `harvestOwnCorrectionConflictUsesNewVersionAndRetainsLocalArchive`, `longContextAndExpiredSessionKeepNavigationReachable`, `managerHomeFallsBackWhenSelectedGreenhouseIsRevoked`, `managerHomeOneTapFiltersRotationAndCachedSnapshot`, `managerRecordsObservationsAndEditsMeasurementTarget`, `managerSeasonCloseAndReopenUseReasonedVersionedQueue`, `managerWalkthroughVisitsPickersScheduleToolsAndAdminWithoutSubmitting`, `queueReviewOpensConflictedRecordAndBackReturnsToSameQueue`, `secondaryMenusReflowAndKeepSheetActionsReachable`, `twoFieldLoginHasAccessiblePasswordToggleAndHandlesMissingConfiguration`, `workerAgendaDistinguishesRealizationsAndSkippedRecords`.

Lima belas nama ada di baseline 17 nama. `workerAgendaDistinguishesRealizationsAndSkippedRecords` juga gagal 2 dari 2 pada `main` tanpa perubahan (kontrol F2) dan diduga bergantung jam; diserahkan ke Q1. Tidak ada regresi F3 yang konsisten.

### Ketidakstabilan GPU emulator

Dengan GPU perangkat keras (`-gpu` bawaan, NVIDIA RTX 3050 Laptop), dua run penuh F3 berhenti di tengah: satu karena ANR (`keyDispatchingTimedOut`) di tes ke-56, satu karena `Process crashed` di tes ke-30. Jejak crash: `Abort message: 'GL errors! .../SkiaOpenGLPipeline.cpp:127'` pada RenderThread aplikasi, dan dalam 1,5 detik yang sama juga pada launcher (`com.google.android.apps.nexuslauncher`) dan Google Search. **Kontrol:** build F1 (tanpa kode F3) di sesi emulator yang sama berhenti lebih cepat, setelah 19 tes, dengan ANR yang sama dan crash GL di Google Search. Karena itu crash ini disimpulkan berasal dari GPU emulator, bukan dari F3. Run penuh akhir memakai `-gpu swiftshader_indirect` (render perangkat lunak), dan run itu selesai tanpa crash. Di perangkat fisik, hal ini belum diverifikasi.

Bingkai bukti di `docs/context/evidence/f3-20261115/`, ditangkap `DestinationMotionUiTest` dengan jam terkendali:

| Berkas | Isi |
|---|---|
| `f3-forward-midway-light.png`, `-dark.png` | 150 ms setelah maju: layar lama tinggal gambar yang memudar dan bergeser ke kiri, layar baru hidup (satu-satunya node semantik) |
| `f3-forward-settled-light.png`, `-dark.png` | Setelah transisi selesai |
| `f3-canopy-handoff-quarter-light.png`, `-dark.png` | Kanopi login pada 100 ms: masih solid dan baru sedikit terlipat |

## Delivery Gate antislop (mode `during`)

Design Read: shell operasional Android untuk pekerja dan pengelola, arah Canopy, dial `DESIGN.md` 1 / 2 / 2 untuk layar kerja dan 2 / 2 / 2 untuk login. MOTION 2 berarti transisi navigasi dan satu momen fokus, tanpa koreografi gulir.

| Blok | Butir | Hasil | Bukti |
|---|---|---|---|
| 1 | R-02 em dash | PASS | Tidak ada teks UI baru |
| 1 | R-03 mobile | PASS dengan batas | Transisi hanya menggeser isi 30 dp dan kembali; tata letak tidak berubah. 360 dp tidak dipotret |
| 1 | R-17, R-18, R-23, R-24, R-28, R-36, R-38 | PASS | Tidak ada angka, testimoni, aset, navigasi atau klaim baru |
| 1 | R-25 kontras | PASS | Tidak ada warna baru; bidang kanopi memakai `ForestScheme.background` yang sudah ada |
| 1 | R-26, R-35 kontrol dan verifikasi | PASS dengan batas | Navigasi diuji oleh `FinanceEntryUiTest` penuh dan `DestinationMotionUiTest`; pratinjau Back prediktif hanya dari kode |
| 1 | R-27 status | PASS | Tidak mengubah status kosong, memuat atau galat |
| 1 | R-32 keyboard | PASS dari kode | Back dengan keyboard terbuka tetap hanya menutup keyboard |
| 1 | R-33 patch skrip | PASS | Semua di source Kotlin |
| 1 | R-34 tema | PASS | Bingkai terang dan gelap |
| 1 | R-39 semantik dan gerak | PASS dengan batas | Layar keluar tanpa node semantik; kanopi `clearAndSetSemantics`; skala animasi sistem dihormati (dari kode). TalkBack nyata belum |
| 1 | R-40 performa | PASS | Tidak ada angka performa yang diklaim; transisi hanya di fase gambar |
| 2 | R-19 animasi | PASS | Shared axis: hubungan masuk/keluar kedalaman. Fade-through: tab sejajar tanpa arah. Fade: layar setara. `animateItem`: baris tidak meloncat. Pratinjau Back: menunjukkan tujuan sebelum dilepas. Kanopi: satu momen login. Tidak ada loop |
| 2 | R-01, R-04, R-06, R-07, R-08, R-09, R-10, R-12, R-13, R-14, R-22 | PASS | Tidak ada gradien, ikon, tipografi, latar, panah, badge, glass, bayangan, glow, kartu atau ilustrasi baru |
| 3 | Dial, fokus, ruang putih, aksen, motif, Design Read | PASS | MOTION 2 terpenuhi; satu momen fokus memakai bidang forest Canopy (motif identitas) |
| 4 | C-1 sampai C-5, R-05, R-11, R-15, R-16, R-20, R-21, R-29, R-30, R-31 | PASS | Setiap durasi dan easing punya alasan tertulis di thesis dan `FarmMotion` |

## UI Skill Checklist `antislop-ui`

| Butir | Hasil |
|---|---|
| Palet dari `DESIGN.md`, aksen di momen kunci, tanpa emoji | Ya |
| Gerak sesuai dial, bertujuan, tanpa loop | Ya |
| Glass/glow/bayangan/radius dalam batas | Ya, tidak ditambah |
| Kontrol punya perilaku nyata | Ya |
| Tahan di tema dan state | Terang dan gelap diuji; 200% dan 360 dp tidak dipotret pada fase ini |
| Metrik terukur | Tidak ada metrik yang diklaim |

## Batas dan yang belum diverifikasi

- **Pratinjau Back prediktif belum terlihat di layar.** Android 11 (API 30) tidak mengirim progres gesture ke `PredictiveBackHandler`; pratinjau baru muncul di Android 14 ke atas. Bukti hanya dari kode.
- Rekaman layar (`adb shell screenrecord`) diambil dari versi pertama F3, tetapi isinya tidak diperiksa karena mesin ini tidak punya pemutar atau pengurai video, jadi rekaman itu tidak dimasukkan sebagai bukti. Bukti gerak yang bisa dibaca hanyalah bingkai `DestinationMotionUiTest`, dan bingkai itu memakai fixture sederhana, bukan layar aplikasi.
- "Hapus animasi" hanya dibuktikan dari kode: `animate` dan `Animatable` Compose memakai skala durasi animator sistem, sehingga transisi selesai seketika.
- Jeda chip keputusan 150 ms dan `animateItem` untuk kartu greenhouse pekerja belum dikerjakan.
- Perangkat fisik, TalkBack nyata dan backend nyata belum diuji.
