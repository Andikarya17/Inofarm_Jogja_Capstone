> Catatan historis owner/IoT. Kebutuhan di [context pack](../context/README.md), subset executable dan evidence terbaru di [IMPLEMENTATION_STATUS](../context/IMPLEMENTATION_STATUS.md). Bukti lama di bawah tidak membuktikan target v2.

# Optimasi performa Android — 13 Oktober 2026

Scope: Android native/build/test lokal dan dokumentasi. Backend dibaca untuk kontrak pagination; backend, firmware, React dan data produksi tidak diubah. Branch kerja `codex/android-performance`, tanpa stage/commit/push/deploy. Staged index sebelum/sesudah identik (SHA256 `49A2A692BE142D74CF0ED5AE2CE9D861A022C6DB2B83D3027D21A453BBB6CAF2`). Baseline diambil dari source working tree pengguna, bukan index Git yang berisi perubahan lebih lama; snapshot/hash tersedia di `output/android-performance-20261013/source-before*`.

## Temuan dan perubahan yang terverifikasi dari source

| Audit | Tindakan dan dampak | Batas/tradeoff |
|---|---|---|
| A: startup sinkron | OwnerViewModel memulai restore asinkron; konstruksi SessionStore tidak membuka preferences/Keystore/file. State restoring statis mendahului login/dashboard; publish setelah epoch/session valid lalu refresh server. | Waktu total cache siap tetap mencakup dekripsi dan mapping; process death mendadak ketika purge belum selesai belum diuji. |
| B: dispatcher/sesi | Persistence, Keystore, HTTP setup dan cache berada di IO; parsing API dan mapping DTO di Default. Getter sesi memeriksa snapshot memori, expiry/clock/generation. Pasangan server/token dibekukan per request. | JSON cache yang dibatasi ukurannya tetap diparse di IO bersama dekripsi di bawah storage lock; tidak ada suspend dalam lock. |
| C: penulisan seluruh snapshot | Schema2 memakai enam AtomicFile AES-GCM per resource. Dashboard tidak lagi membaca/mengenkripsi history/config. Batas agregat2MiB termasuk old file selama staging, history500, scope server/account/tank, fetchedAt/filter terenkripsi. | Write tetap awaited di IO, tanpa antrean tambahan; update besar bisa ditolak agar cache baik sebelumnya utuh. Schema1 dibuang; sesi tetap, cache offline lama perlu refresh. |
| D: decode hero | Byte aset sama, pindah drawable-nodpi; decode lokal IO dengan sampling sesuai viewport, keyed produceState. Tidak ada image library/network/global bitmap cache baru. | Bitmap tampil dilepas melalui GC saat tak direferensikan; bukan klaim leak-free. |
| E: data/list/trend | Live history4×25 row, dua cursor server dan navigasi halaman sebelumnya; list pekerja/shift lazy dengan stable ID/key. Tren memoized berdasarkan input. | Offline cache hingga500; pekerja/shift tetap dimuat semua halaman100 untuk identitas/referensi editor. Dropdown pilihan pekerja masih eager; model roster tetap O(total roster). |
| F: build | Release/profile mengaktifkan R8 dan resource shrinking; debug tetap development. Profiling memakai signing debug lokal dan suffix `.performance` opsional. | Profile menyimpan ABI publik spesifik yang dipanggil APK instrumentation; release tidak menggunakan keep file profile. Profiling tidak identik byte-per-byte dengan release. |
| G: glass | Blur18dp/Auto dipertahankan. Root/editor tidak memasang Haze source/effect saat fallback policy existing aktif. | Tidak menebak ambang kemampuan GPU baru dari API level; pengukuran emulator tidak membuktikan kemampuan HP owner. |

File utama: `android/app/src/main/java/com/inofarm/jogja/data/{ApiClient,SessionStore,ReadCache,OwnerRepository,Models}.kt`, `ui/{OwnerViewModel,OwnerApp,LocalHeroImage,GlassComponents,EditorComponents,DashboardScreen,HistoryScreen,WorkersScreen}.kt`, dua hero `drawable-nodpi`, `android/app/build.gradle.kts`, rules profile/test (termasuk daftar ABI tepat dari pustaka instrumentation) dan pengujian `androidTest`. Tidak ada perubahan rules agronomis, PPM Android, device trust, interval polling, minSdk atau dependency.

## Lingkungan dan metode

Windows, Android Studio JBR25.0.3 (bytecode17), Gradle9.3.1/AGP9.1.1, Kotlin Compose plugin2.2.10, Compose BOM2025.08.01, Haze1.6.10. compile/target37, min26 tetap. Emulator `Medium_Phone_API_37.0`, Android17/API37 x86_64,1080×2400,density420,60Hz; MemTotal4007632KiB (~4GiB AVD). Emulator cold boot tanpa snapshot setelah SystemUI ANR pada run eksplorasi. Hanya system image API37 tersedia; tidak ada perangkat fisik owner.

Semua tes sesi/cache memakai package `com.inofarm.jogja.performance`; tidak membersihkan package owner `com.inofarm.jogja`. MockWebServer dan fixture TLS terisolasi; sertifikat test-only hanya di APK instrumentation. Default client produksi diuji menolak sertifikat itu dan cleartext HTTP. Tidak menjalankan backend tests atau database mutation.

Baseline pristine dibangun di `.tools/perf-baseline` dari snapshot source sebelum perubahan, dengan package test terisolasi dan harness pengukuran yang sama. Tidak ada source produksi optimasi disalin ke baseline. Run eksplorasi `before-*` awal terjadi ketika sebagian build berjalan bersamaan dan tidak dipakai untuk klaim final. Tidak mencampur data screenrecord dengan run tanpa recording.

## Automated correctness dan build

Debug: `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `lintDebug` dengan `-PperformanceTest` PASS. Unit8 PASS. Instrumentation final48 PASS (`final-debug-ui.txt`,215.317s): Editor8, GlassRendering2, LiquidGlass5, OptimizedOwnerSmoke2, OwnerState6, OwnerUi1, PerformanceCorrectness18, Redesign6. Lint0errors/27warnings/4hints; tidak menyembunyikan warning. Build release/profile/profileAndroidTest juga PASS (`final-build-with-scroll.txt`, `profile-http1-build.txt`). Profile correctness22 PASS (`final-profile-ui.txt`):18 correctness,2 owner smoke/lifecycle dan2 rendering. Tiap build juga menjalankan1 metrics test,3×tab blur,3×tab fallback dan3×scroll, semua PASS. Build PASS bukan performance PASS.

Pengujian meliputi cold restore kosong/near-limit500 history/offline/corrupt/schema1, StrictMode disk saat konstruksi UI, expired/clock rollback, logout ketika restore tertahan, respons selesai setelah expiry, 401, relogin ticket lama, filter race saat pagination, 24 halaman navigasi maju/mundur dengan live bound100, cache oversize/disk failure, AtomicFile recovery dan budget antar-instance, write dashboard tidak mengubah bytes history. Semua alasan/provenance/filter cache diperiksa. Editor mempertahankan draft/ID/version/conflict/matching ACK, font besar/orientasi/keyboard/back/recreation sesuai suite. Test lifecycle memakai virtual Compose clock31s untuk kebenaran polling30s dan resume; bukan benchmark real-time polling.

Screenshots `after-dashboard.png` dan `after-settings.png` diperiksa: botanical hero dan liquid-glass presentation tetap tersedia; angka fixture bukan produksi. OptimizedOwnerSmoke export memeriksa endpoint/payload CSV, bukan menyimpan dokumen lewat system picker.

## Pengukuran dan keterbatasan

Semua angka berikut diukur baru pada13Oktober2026. Nilai adalah median [min–max]; sampel VM/cache/startup masing-masing5, frame/PSS per scene masing-masing3. Input synthetic500 history + padding config menghasilkan cache terenkripsi1.819.817byte sebelum dan1.820.079byte sesudah (budget2MiB); bukan payload produksi. Sumber lengkap: `output/android-performance-20261013/measurements.json`, raw `baseline-*`/`after-*`, `summarize.py`, `run-samples.ps1` dan `run-startup.ps1`.

| Metrik | Sebelum | Sesudah |
|---|---|---|
| Main saat pembuatan ViewModel, cache500 | 192.24 [182.83–219.07] ms | 0.27 [0.14–4.53] ms |
| Cache500 lengkap teramati | 192.45 [183.14–219.38] ms | 138.60 [118.12–205.59] ms |
| Write cache dashboard | 232.19 [204.75–264.72] ms | 10.14 [8.40–11.84] ms |
| Activity cold, first-frame TotalTime | 829.00 [750.00–854.00] ms | 540.00 [410.00–621.00] ms |
| Activity warm, first-frame TotalTime | 169.00 [160.00–231.00] ms | 180.00 [115.00–348.00] ms |
| Ciphertext berubah per dashboard write,5× | 1.819.817byte | 293byte |
| Default decode melon pada density420,1× | 2520×1680;16.934.400byte | 960×640;2.457.600byte |
| Default decode leaf pada density420,1× | 2520×1079;10.876.320byte | 960×411;1.578.240byte |
| APK profile lokal | 8,161,694byte | 2,180,884byte |

Default decode memakai BitmapFactory.decodeResource pada kedua build; ini alokasi bitmap aktual pada test tersebut, bukan pengukuran seluruh RAM layar/painter. Test decode viewport sesudah juga mencatat melon240×160=153.600byte dan leaf240×103=98.880byte untuk viewport kecil (`hero-allocation.txt`); pada viewport besar tetap sumber960px. Byte WebP asli tidak berubah. APK release unsigned sesudah1.826.767byte; APK debug11.698.887byte. Perbandingan ukuran utama memakai variant profile yang sama; ukuran APK bukan RAM.

Cold Activity memakai force-stop lalu `am start -W`; warm memakai Back (Activity selesai) lalu start lagi dalam PID yang sama. Semua5 run mencatat LaunchState COLD/WARM dan pasangan PID warm yang sama, tersimpan di `*-startup-*-pids.txt`. Sesi/cache kosong dan tanpa login/backend produksi. TotalTime mengukur first frame yang dilaporkan ActivityManager: build baru boleh menampilkan restoring pada frame pertama, sehingga angka itu **bukan** waktu dashboard/login siap digunakan. Cold-object ViewModel dalam process instrumentation juga bukan cold-process TTID; waktu seluruh cache teramati dipisahkan pada tabel.

| Scene frame,3× | Sebelum: jank%;P95 | Sesudah: jank%;P95 |
|---|---|---|
| 18 transisi tab, blur | 43.34 [40.27–43.64] %; 69.00 [69.00–69.00] ms | 38.89 [37.52–39.02] %; 65.00 [65.00–69.00] ms |
| 18 transisi tab, fallback override | 37.77 [37.44–39.84] %; 65.00 [65.00–65.00] ms | 36.90 [36.18–36.98] %; 65.00 [65.00–69.00] ms |
| 100 row,36 swipe (18 naik/18 turun) | 71.90 [61.16–83.47] %; 85.00 [69.00–113.00] ms | 74.59 [53.28–78.51] %; 85.00 [57.00–93.00] ms |

Tab memakai stepped Compose clock (24frame/transisi,16ms sleep/frame), tanpa recording. Scroll memakai gesture injection300ms dan waitForIdle, bukan input tangan pengguna; ~121–122 frame tercatat/run. Tab mencatat563–586/601–635frame sebelum (blur/fallback),551–558/586–603sesudah. Frame sample tetap memiliki overhead instrumentation/emulator. P95 scroll tidak membaik (85ms), median jank scroll sedikit naik dengan rentang sangat tumpang tindih; warm startup juga tidak terbukti membaik. Tidak menyimpulkan regresi/peningkatan stabil dari3run bervariasi atau menyalahkan blur. GPU histogram emulator memuat bucket4950ms yang tidak digunakan sebagai ukuran GPU nyata. Tidak mengubah policy/blur radius berdasarkan sampel ini.

| PSS process,MiB | Sebelum | Sesudah |
|---|---|---|
| Setelah cold first frame,5× | 36.39 [36.33–36.44] MiB | 22.56 [21.57–22.68] MiB |
| Setelah warm first frame,5× | 36.86 [36.79–36.94] MiB | 23.30 [23.30–23.50] MiB |
| Setelah transisi blur,3× | 52.76 [49.98–56.65] MiB | 46.08 [39.30–49.56] MiB |
| Setelah transisi fallback,3× | 55.20 [50.54–55.66] MiB | 41.55 [39.04–41.63] MiB |
| Scene100 row sebelum scroll,3× | 41.73 [41.39–44.14] MiB | 28.31 [28.25–28.33] MiB |
| Scene100 row sesudah36swipe,3× | 46.94 [44.53–59.38] MiB | 31.98 [31.41–32.68] MiB |

Scene100 row setara jumlah model empat halaman live, diberikan langsung sebagai fixture; bukan pengukuran network paging100 request. PSS scene mengandung instrumentation dan dipengaruhi code shrinking/test APK/GC; tidak membuktikan peak/leak-free/thermal stability atau RAM aman pada owner. Startup PSS berasal dari Activity standalone sesudah `am start`, bukan process instrumentation. Tidak ada build atau screen recording berjalan bersama sampel final. Urutan blok sebelum lalu sesudah belum diacak/interleave; host load/thermal tetap sumber noise.

Logical changed encrypted bytes bukan physical flash writes. StrictMode memeriksa disk access jalur konstruksi, bukan seluruh biaya CPU. Root harness tab mempertahankan Haze source untuk kedua mode sehingga tidak mengisolasi penghematan pelepasan source root pada fallback aplikasi aktual.

## Status kompatibilitas dan pekerjaan tersisa

API37 emulator merupakan bukti software lokal. API26–30, low-RAM fisik, GPU/thermal/battery/soak, process-death purge, cold/warm startup end-to-end owner dengan sesi/data nyata dan jaringan greenhouse belum terverifikasi. Override fallback emulator tidak ekuivalen perangkat low-RAM. Roster sangat besar masih memerlukan evaluasi kontrak/editor sebelum mengubah fetch menjadi lazy server paging. Pertahankan owner-managed konfigurasi dan backend-authoritative validation; pentest/reviewer/hardware tetap gate terbuka.

Performa dan kompatibilitas pada HP owner: NOT_VERIFIABLE; perlu pengujian pada model perangkat aktual.
