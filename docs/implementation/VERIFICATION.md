## FIN09 native expense/allocation — 30 Oktober 2026 WIB

Branch `codex/finance-expense-entry`, base `ac55dd8`. Android35unit/48instrumentation/0lint errors PASS; five real guarded Laravel/MySQL phases prove payment/allocation offline/process restart/exact receipt/source balance/audit/worker denial, retaining worker/harvest regression. SQLite5 preserves raw drafts/outbox/hash/cursor/archives. [Evidence](../migration/finance-20261029/evidence/FIN09-android.md) includes test corrections, visual QA and limits. Private proof/full FIN09/UAT remain open.

## Canonical UTF-8 — 30 Oktober 2026 WIB

Branch `codex/finance-canonical-unicode`, base `3e2caf1`. Contract JVM6/native4/PHP5cases PASS; independent UTF-8/control/literal escape golden hashes and immutable mismatch queue hold verified. [Evidence](../migration/finance-20261029/evidence/20261030-CANONICAL_UNICODE.md) separates native-entry work in progress from this fix. No schema/dependency/server mutation changes.

## FIN09 backend payment/allocation — 30 Oktober 2026 WIB

Branch `codex/finance-expenses`, base `49efd66`. Backend67tests/1151assertions PASS, isolated guard4/12 PASS. Two independent PHP/MySQL processes competing for source budget across two seasons reconcile to one accepted allocation. [Evidence](../migration/finance-20261029/evidence/FIN09-backend.md) and [contract](../../contracts/v2/EXPENSES.md). Additive schema6; native entry/private proof and full FIN09 acceptance remain open.

## Consistent operational reads — 30 Oktober 2026 WIB

Branch `codex/finance-consistent-reads`, base `763cd7f`. Backend59tests/967assertions PASS after isolated guard4/12. Accepted revision projection and actor/token/scope boundary verified by controlled interleave and a second guarded MySQL connection. [Evidence](../migration/finance-20261029/evidence/20261030-CONSISTENT_READS.md) distinguishes these from two-process/throughput/device claims. No schema/Android/API v1 changes.

## Draft logout boundary — 30 Oktober 2026 WIB

Branch `codex/finance-draft-logout`, base `bb3d864`. Android27unit/39instrumentation/0lint errors dan real guarded offline/restart/replay/manager cancel/old ACK phases2 PASS. Latest editor flushed before credentials clear; failure retains session through recreation/Back; unchanged accepted editor not converted into correction. [Evidence](../migration/finance-20261029/evidence/20261030-DRAFT_LOGOUT.md) includes environment/fixture failures and limits. Backend/API/schema unchanged.

## FIN08 panen multi-grade — 29 Oktober 2026

GitHub branch `codex/finance-harvest`, base `89c5026`. Backend54tests/913assertions; Android27unit/33instrumentation/0lint errors; real worker activity+harvest offline/process restart/replay→manager cancel→old ACK no downgrade PASS. Independent PHP/MySQL two-connection harvest/activity base1 races one accepted/one conflict/final2 PASS. [Evidence FIN08](../migration/finance-20261029/evidence/FIN08.md) memuat source quantities/ledger reconciliation dan batas; bukan workflow penjualan/oversell/UAT. Follow-up editor logout/read consistency/identity di GAP_ANALYSIS tetap harus ditangani sebelum handover.

## FIN07 riwayat dan resolusi konflik — 29 Oktober 2026

Branch GitHub `codex/finance-history-conflicts`, dependency `e986eec`. Backend48tests/777assertions; Android21unit,0lint errors,26/26 focused instrumentation dalam satu run dan2 real guarded Laravel/MySQL device phases PASS. SQLite4 migration1/2/3 mempertahankan draf/outbox/cursor/arsip; local/server comparison membutuhkan reviewed version/revision. [Evidence FIN07](../migration/finance-20261029/evidence/FIN07.md) merinci koreksi race, fixture/test readiness, command dan batas; bukan acceptance produk. FIN08–17/UAT/pentest berikutnya.

## Pelaksanaan core FIN01–06 — 29 Oktober 2026

Source FIN06 `ff5869f`, GitHub branch codex/finance-activity-entry. Backend45tests/711assertions; Android19unit/12focused instrumentation; real guarded MySQL offline/restart/replay/manager drill passed. [Evidence FIN06](../migration/finance-20261029/evidence/FIN06.md) merinci commands, failures yang diperbaiki dan batas. Bukan acceptance seluruh produk; FIN07–17/UAT/pentest tetap terbuka. [Review provenance](../migration/finance-20261029/PROVENANCE_REVIEW.md) memperbaiki README arsip sambil mempertahankan indeks dan hash seluruh delapan file.

## Audit dan context pack proposal baru — 29 Oktober 2026

Branch `codex/finance-proposal-context`, baseline source HEAD `9da5b30bfe707f94b64028de98878c2e55adccea`. Scope: audit/perbandingan, context pack dan master prompt, bukan implementasi aplikasi target.

- Proposal 60 halaman diekstrak; diagram utama diperiksa visual, ERD PDF30–31 diperiksa halaman penuh. Source inventory344file tracked/untracked non-ignored; inspeksi rinci terfokus lintas modul, bukan klaim setiap baris direview.
- Delapan file pre-edit diarsipkan byte-identik, manifest SHA256 diverifikasi8/8; termasuk context modified sebelum task.
- Pemeriksaan14dokumen aktif: link relatif dan fenced blocks PASS; coverage KF14/14, KNF8/8, U21/21 PASS.
- Hash192file source pada android/backend/firmware/src/scripts (kecuali backend/AGENTS.md) identik dengan inventory awal; tidak mengubah runtime source/schema executable.
- Oracle decimal independen: NPV4132231.4049586777, IRR13.06623862918075%, PP1.6666666667, DPP1.9166666667 cocok fixture proposal; ini pemeriksaan angka rancangan, bukan unit test service yang belum dibuat.
- `git diff --check` PASS; Git memberi peringatan normalisasi CRLF→LF backend/AGENTS.md, bukan error whitespace.
- Tidak build/test app/backend/firmware, tidak SQL/migration/reset/deploy/commit/push. Data pengguna tidak disentuh. U01–21 target tetap NOT RUN. Hasil lama di bawah tidak menjadi hasil target baru.

Paket: [master prompt](../migration/finance-20261029/MASTER_PROMPT.md), [audit](../migration/finance-20261029/AUDIT_COMPARISON.md), [context](../context/README.md), [hasil checks dokumentasi](../migration/finance-20261029/DOCUMENT_CHECKS.json). Temp ekstraksi/render tidak dipublikasi.

> Catatan historis owner/IoT. Kebutuhan di [context pack](../context/README.md), subset executable dan evidence terbaru di [IMPLEMENTATION_STATUS](../context/IMPLEMENTATION_STATUS.md). Bukti lama di bawah tidak membuktikan target v2.

# Bukti implementasi lokal — 10 Oktober 2026

## Persiapan baseline GitHub — 24 Oktober 2026

Panduan instalasi clone baru dan penguatan .gitignore diverifikasi terhadap source/config. Backend guard PASS4/12, full suite hanya inofarm_test PASS20/404; Composer strict/platform/Pint PASS. Android assembleDebug/unit10/lint PASS (sebagian UP-TO-DATE, 0errors/28warnings/4hints) memakai JDK25 saat ini. MySQL awalnya belum hidup; setelah instance workspace dinyalakan suite lulus. Scan kandidat publikasi tidak menemukan secret nyata dari pola/komparasi lokal yang diperiksa; fixture publik dipertahankan. [GITHUB_PUBLICATION](GITHUB_PUBLICATION.md) mencatat prosedur, warning dan batas verifikasi. Keberhasilan push diperiksa terpisah melalui hash remote; tidak ada deployment atau verifikasi komputer teman/hardware baru.

## Data dummy dan akun demo — 20 Oktober 2026

Atas permintaan pengguna dibuat30 record+30 telemetry untuk10–19Oktober pada tandon DEMO ID2 di inofarm_local. Full suite hanya inofarm_test PASS20 tests/404assertions; isolated guard4/12; Pint PASS. HTTP akun demo login/history30/telemetry30/status filter3valid/17issues/10incomplete/date filter28/logout PASS. Dua jam invalid tidak masuk date filter. Fingerprint24 row existing identik sebelum/sesudah. Device simulator disabled/never_seen; bukan bukti hardware atau UI APK pada sesi ini. [DEMO_DATA](DEMO_DATA.md) berisi provenance/akses/SQL. Tanpa migration/reset owner/dependency/deploy.

## Validasi sebelum commit/push — 15 Oktober 2026

Otorisasi Git terbaru pengguna berlaku untuk commit per fitur dan push; pernyataan tanpa commit/push pada bagian lama adalah konteks sesi sebelumnya. Remote main awal `9ef1363cc1cfa170a384882ee4084995c01463f9` (tiga commit) dipertahankan, termasuk `.gitlab-ci.yml` SAST/Secret Detection.

- Backend: guard terisolasi PASS4/12; suite MySQL `inofarm_test` PASS18 tests/247 assertions; Composer validate strict dan Pint PASS. Percobaan awal mendapat4 connection errors saat MySQL mati; instance workspace existing dinyalakan dengan script existing lalu suite diulang penuh dan lulus. Tidak menjalankan test pada `inofarm_local`.
- Android: assembleDebug, testDebugUnitTest, lintDebug PASS; output Gradle menyatakan sebagian besar task UP-TO-DATE. Laporan unit10/0 failures; lint0 errors,28 warnings,4 hints. Tidak ada perangkat ADB terhubung, sehingga instrumentation tidak diulang; evidence historis tetap terpisah.
- Firmware: native8/8 PASS; esp32dev build dan esp32_compile_check PASS, tanpa flashing. Ada warning OneWire upstream; tidak membuktikan hardware/calibration/RTC/SD fisik.
- Kandidat publikasi diperiksa terhadap nilai secret environment lokal tanpa mencetak nilainya; tidak ada kecocokan. Environment aktual, output/outputs, runtime/tools/build/cache IDE dikecualikan; template kosong dan fixture TLS khusus androidTest tetap source pengujian.
- React tetap referensi tanpa perubahan source. Dependencies root belum terpasang, sehingga build/lint React tidak dijalankan pada sesi Git ini. Pentest, reviewer, acceptance HP/hardware, dan status pipeline server tetap gate terpisah.

Prosedur dan pembagian commit: [GIT_WORKFLOW](GIT_WORKFLOW.md).

## Perbaikan UI/navigasi — 15 Oktober 2026

Scope diotorisasi: presentation/navigation Android, regression tests dan docs. Branch `codex/ui-navigation-fixes` dibuat dari branch kerja existing beserta seluruh perubahan lokal. Tidak commit/push/deploy; index SHA256 tetap `E341D18A7F377602FF6240623E1EDDF253CB7131A730AADD045C2AD94911D433`. Source produksi hanya OwnerApp.kt dan HistoryScreen.kt; DashboardScreen.kt/SettingsScreen.kt sama dengan baseline. Backend/firmware/React/API/data produksi tidak diubah.

Reproduksi sebelum perbaikan pada MainActivity/OwnerApp nyata, package fixture terisolasi `.performance`: Hari ini → Filter tetap memilih Hari ini; Dashboard hero fase → Pengaturan → Dashboard tetap memilih/menampilkan Pengaturan. Baseline `instrumentation-repro.txt` gagal **2/3** tepat pada kedua assertion tersebut. Tes akses akhir normal lulus, begitu juga baseline font200 portrait dan landscape (masing-masing1). Gejala konten sama sekali tidak dapat dijangkau belum terulang pada baseline AVD; ukuran hardcoded memang berbeda dari navigasi aktual. Koreksi mengukur tinggi aktual, memakai safe drawing/IME dan memastikan viewport tidak overlap navigasi, bukan mengklaim gejala HP sudah direproduksi.

| Pemeriksaan | Hasil | Bukti di outputs/ui-navigation-20261015 |
|---|---|---|
| Build APK normal + unit + lint final | PASS; unit **10/10**, lint **0 errors / 28 warnings / 4 hints** | build-unit-lint-final.txt; lint-results.xml; normal/TEST-*.xml |
| Regresi UI final normal 1080×2400/font100 | PASS **21/21**: NavigationRegression6 + OwnerUi1 + Redesign6 + Editor8 | full-regression-final.txt |
| OwnerApp font200, termasuk portrait/landscape/filter/Back/recreation/IME | PASS **6/6** | font200-verified.txt |
| Layar pendek 840×1400/font200 dan font100, portrait/landscape/IME | PASS **3/3** per font | short-font200-final.txt; short-normal-final.txt |
| IME settled pada layar pendek/font200 | PASS **1/1**, field editable utuh di atas footer dan footer di atas IME | short-keyboard-settled.txt; short-font200/editor-keyboard.png |
| Screenshot dan batas layout | Diperiksa visual; viewport.bottom == navigation.top dalam matriks | normal/, font200/, short-font200/, layout-bounds.txt |

Counts adalah eksekusi terpisah, bukan jumlah skenario unik yang dijumlahkan. Fixture mock HTTP hanya instrumentation; APK normal tidak memuat fixture server/data/login. Tidak memakai backend/MySQL atau melakukan mutation produksi; export SAF dibatalkan tanpa menulis file. Regresi memakai MainActivity/OwnerApp actual, query request/ekspor applied, selected semantics, shortcut3×, tab/reselection/system Back, draft+Activity recreation, invalid date/Reset, touch swipe sampai akhir dengan last value/control visible, batas viewport terhadap navigasi, klik Refresh dan empat editor lalu Batal, serta keyboard dengan insets aktual. Process death/OEM/hardware tidak dibuktikan oleh Activity recreation.

Kegagalan iterasi disimpan dan tidak dihitung PASS: DNS mock URL di thread UI (harness), guard route callback lama pada perbaikan pertama, lookup item ekspor lazy sebelum scroll pada font200, jumlah swipe tetap yang tidak cukup pada viewport pendek, dan snapshot IME sebelum settle. Harness terakhir mencari lazy item melalui list, swipe sampai scroll range akhir, serta menunggu IME sebelum verifikasi field/screenshot; assertion selected/query/geometry tetap dipertahankan. `README.md` output memuat mapping files, command dan batas. Ukuran emulator dipulihkan 1080×2400/density420, font1.0; animation scales tidak diubah.

APK normal `inofarm-ui-navigation-debug.apk`, SHA256 `CE8323BEA074AEA351F886F06F97D032A241ECFAA454A5CFC28A27A4F423AB2B`. Install-r package normal ke emulator PASS, am start MainActivity Status ok/COLD dan pidof mengonfirmasi proses hidup; sesi/data package normal tidak di-clear. Acceptance HP pengguna/OEM, seluruh API, hardware/calibration/RTC/SD, full TalkBack/process death, pentest/reviewer/SOP multi-platform dan handover tetap OPEN/NOT_VERIFIABLE.

## Membuka ulang dari launcher/IDE — 15 Oktober 2026

CMD juga PASS dengan PATH sementara hanya SystemRoot/System32/SystemRoot (simulasi tanpa pwsh Codex pada PATH): fallback PowerShell7 runtime existing berhasil, build1s/install/start. Tidak mengubah PATH sistem. Konfigurasi baru diparse sebagai XML dan module/Activity/build-task diperiksa; tombol IDE tetap belum diklik langsung.

CMD existing dan CMD sesudah perbaikan PASS: build UP-TO-DATE1s, install Success, MainActivity Status ok dan window focus. Proses qemu existing memiliki -qt-hide-window sehingga layar harus di Running Devices. Shared run config app tanpa module/method diperbaiki dan Inofarm MainActivity dibuat (specific_activity/com.inofarm.jogja.MainActivity/InofarmJogja.app/Android.Gradle.BeforeRunTask). Parser/whitespace diperiksa. Klik tombol Run IDE serta cold-start GUI normal belum diuji langsung; tidak menutup gate device/hardware/pentest. Source app/backend/firmware tidak diubah. Transcript .runtime/android-launcher.log memuat run terakhir tanpa credential.

## Perbaikan koneksi/login — 14 Oktober 2026

Launcher final run -SkipStudio -Serial emulator-5554 PASS: health existing, default emulator otomatis, build3s/install-r/start tanpa reset sesi; APK normal diperbarui di output/inofarm-jogja-debug.apk. Parser launcher dan diff whitespace PASS.

Diagnosis: MySQL3307/API8000 aktif, adb reverse --list kosong. Shell emulator ke10.0.2.2/up200 berhasil tetapi APK target37 timeout tanpa ACCESS_LOCAL_NETWORK; bukti shell bukan bukti akses app. Setelah permission debug dan runtime request, prompt Android muncul, permission granted=true, login owner nyata berhasil dengan base10.0.2.2 dan token tersimpan terenkripsi. adb reverse tetap kosong, log backend menunjukkan login/dashboard/config/history/telemetry/workers/shifts. Screenshot login ringkas `output/inofarm-login-simplified.png` dan owner live `output/inofarm-owner-live.png` diperiksa. Tidak ada fixture/migration atau mutation konfigurasi pada backend owner.

Build debug/unit10 (Contract8+ApiEndpoint2)/lint0errors27warnings4hints PASS; targeted OwnerUiTest1 dengan package .performance terpisah PASS setelah permission change, menguji alamat tersembunyi, toggle/edit/tutup lalu login, empty dashboard, riwayat, rotation dan editor existing lewat MockWebServer. Helper test existing menyesuaikan disclosure; bukan bukti semua suite diulang. Sesi/data app owner tidak dipakai fixture. Release/profile property/source policy diperiksa, build release belum diulang pada task ini. Runtime denial/revocation/recreation saat prompt, USB fisik/custom LAN/OEM/API26 belum terverifikasi. Gate hardware/pentest/reviewer tetap terbuka.

## Launcher Android — 14 Oktober 2026

Provisioning owner lokal atas permintaan eksplisit pengguna: akun baru di MySQL inofarm_local/local, role owner/name Inofarm/tank-linked/password hashed diperiksa. Pengguna mengulang pilihan password10 karakter setelah diberi tahu minimum12 CLI; pembuatan khusus lokal melalui model hashed tidak mengubah policy CLI. Login API dan GET dashboard200 PASS, token pemeriksaan dicabut lewat logout. Tidak ada reset, fixture, migration, threshold atau perubahan akun existing; script sementara di .runtime dihapus. Credential tidak dicatat dalam source/docs.

`scripts/run-android.ps1` default VERIFIED_LOCAL: SDK37/JBR25/AVD existing ditemukan, proyek android dibuka di Android Studio, MySQL3307 disiapkan oleh script existing, health API8000 HTTP200, assembleDebug BUILD SUCCESSFUL36s, install -r Success dan MainActivity Status ok. CMD wrapper juga dijalankan dengan -SkipStudio -SkipBackend: build ulang UP-TO-DATE2s/install/launch PASS. Emulator dibangunkan tanpa clear data; dumpsys window mengonfirmasi MainActivity fokus dan screenshot login `output/inofarm-launcher.png` diperiksa. Tidak ada fixture/migration/akun baru/database test. Android Studio import Gradle PASS: idea.log 23:39:38 mencatat `Gradle sync finished in 3 m 25 s 470 ms`, gradle.xml mencantumkan root/app, dan log modul `InofarmJogja.app` cocok run config. Klik tombol Run IDE belum diuji langsung. Branch cold-start AVD, beberapa serial, komponen hilang dan komputer lain belum diuji runtime. Parser PowerShell tanpa error dan git diff --check PASS. Perubahan hanya launcher/run config/ignore generated IDE/docs; gate hardware/pentest tetap terbuka.


## Optimasi Android — 13 Oktober 2026

Scope Android/build/test lokal dan dokumentasi; lihat [ANDROID_PERFORMANCE](ANDROID_PERFORMANCE.md) untuk metode, before/after, file, audit A–G dan keterbatasan. Branch `codex/android-performance`, index pengguna dipertahankan (SHA256 `49A2A692BE142D74CF0ED5AE2CE9D861A022C6DB2B83D3027D21A453BBB6CAF2`). Tidak stage/commit/push/deploy atau mutation data produksi. Backend hanya dibaca; firmware/React tidak disentuh.

| Check baru | Hasil dan evidence |
|---|---|
| Debug build/unit/lint | PASS; `output/android-performance-20261013/final-debug-build.txt`; unit8, lint0errors/27warnings/4hints |
| Debug instrumentation | PASS48; `final-debug-ui.txt`,215.317s; mencakup18 performance correctness dan2 smoke/lifecycle baru serta suite editor/UI/state |
| Release/profile build | PASS; `final-build-with-scroll.txt` dan `profile-http1-build.txt`; R8/resource shrink aktif. Profile memakai rules ABI khusus instrumentation, tidak diterapkan ke release |
| Profile instrumentation | PASS22; `final-profile-ui.txt`,31.64s;18 correctness,2 smoke/lifecycle,2 rendering |
| Metrik sebelum/sesudah | Masing-masing1 harness5×VM/cache/decode,3×tab blur,3×tab fallback,3×scroll PASS;5×cold/warm Activity. `measurements.json` dan laporan memuat median/rentang. Warm/scroll belum membaik konsisten |
| Visual | `after-dashboard.png`/`after-settings.png` diperiksa; dua hero dan liquid glass dipertahankan |
| API26/HP owner/hardware/soak | NOT_VERIFIABLE, tidak tersedia/tidak dijalankan; minSdk26 tetap |

Emulator API37 x86_64 1080×2400,density420,60Hz/~4GiB. Package test `.performance` terisolasi; mock TLS fixture tidak masuk main APK. StrictMode konstruksi, offline/cache500/near2MiB, schema/corrupt/budget/AtomicFile, expiry/clock/logout/401/filter/session races, pagination dan editor matching ACK diuji. Polling30s diuji dengan virtual clock untuk correctness. Build PASS tidak berarti performance PASS. Run awal yang gagal/terganggu dan runner R8 ABI failures disimpan sebagai diagnosis, bukan dilaporkan PASS; status runtime profile/pengukuran final ada di laporan tertaut.

Performa dan kompatibilitas pada HP owner: NOT_VERIFIABLE; perlu pengujian pada model perangkat aktual.

## Liquid Glass sembilan view — 12 Oktober 2026

Otorisasi: PROMPT_IMPLEMENTASI_CODEX.md dan persetujuan eksplisit pengguna atas sembilan PNG. APK terbaru: output/liquid-glass-implementation/inofarm-jogja-liquid-glass-debug.apk. Semua PNG asli dibuka sebelum edit; hanya area app direplikasi. Root React/backend/firmware/kontrak/data layer/OwnerViewModel/ConfigDraft tidak berubah dari baseline awal turn. Branch codex/android-ui-redesign; staged index tetap SHA256 E341D18A7F377602FF6240623E1EDDF253CB7131A730AADD045C2AD94911D433. Tidak commit/push/deploy/stage/reset/stash.

| Check | Hasil | Evidence |
|---|---|---|
| Baseline assemble/unit/lint | PASS | baseline/build.log; build existing sebelum edit |
| Baseline UI | PASS1 | baseline/ui.log dan dashboard.png |
| assembleDebug terakhir | PASS | final-feedback-checks.log/final-artifact-build.log; APK main disalin, install sukses dan MainActivity berjalan di emulator (PID23531), tanpa AndroidRuntime entry pada smoke ini |
| Unit | PASS8 | checks-feedback/unit/TEST-com.inofarm.jogja.ContractTest.xml |
| Lint terakhir | PASS0 errors,26 warnings,3 hints | checks-feedback/lint.xml; warning/hint tidak disembunyikan |
| Suite software/UI/state animation-on | PASS27 | checks-on-final.log; checks-final/connected XML |
| Suite animation-off | PASS27 | checks-off-final.log; checks-off/connected XML; tiga system animation scales0 |
| Setelah spacing header/PPM dan footer feedback terakhir | PASS13 | final-feedback-checks.log; checks-feedback/connected XML; gallery/draft/worker/shift/pagination/font/landscape/offline/reasons |
| Glass runtime/fallback | PASS2 termasuk font test | GlassRenderingTest; glass-supported/fallback PNG dan pixel difference; fallback dipaksa pada API37, bukan perangkatAPI26 |
| Profile non-debug | PASS1 per run | profile-optimized.log/profile-fallback.log/profile-video-final.log; minSdk26, signing lokal, isDebuggable=false |
| Sembilan screenshot + comparison | REVIEWED_LOCAL | screenshots/ dan comparisons/ tiap nama; all-nine.png; crop-mapping.json; originals tidak diedit |
| Motion video | CAPTURED/REVIEWED_FRAMES | animation-profile.mp4,16,889detik1080×2400; metadata/lima frame1/3/5/7/9s |
| Scope/index/reference preservation | PASS | baseline/hashes.json dan preservation.json; tanpa mutasi Git index/backend/production data |

Coverage runtime: login/empty/loading/error/last-known/invalid-clock receipt; empat sensor dan kualitas; tren/clock/contact/capture/receive/fetch; seluruh reasons dan metadata/ID; filter draft/applied, source-date shortcut/pagination/ekspor; SAF cancel/recreation; worker create/edit inactive/active action contract; shift create/overnight/edit/key/version/recreation/date-picker cancel; config full snapshot/cross-page/back/cancel/minmax/field errors/version conflict/matching ACK/recreation; session/logout/cache scoped/expiry/query cancellation. Font200% dan area Compose280dp diperiksa, landscape memakai orientasi Activity nyata, keyboard/safe insets/footer tidak menutup field. Semantics/48dp dan selected controls diperiksa; bukan spoken TalkBack acceptance lengkap.

Iterasi gagal disimpan, tidak dianggap PASS: footer test lama memakai performScrollTo pada footer fixed; duplicate lookup status; label nav200% overflow; login click beradu transisi IME pada animation0; dialog Batal ambigu; pagination menunggu lazy item di luar viewport; encoding terminal. Sumber/test diperbaiki dengan field tag, viewport reservasi, label layout, keyboard synchronization, dialog ancestor, wait atas state hasil query lalu scroll footer, dan UTF-8. Assertion ID/version/active/checks/cancel tetap, tidak dilemahkan. Server conflict juga harus assertIsDisplayed; feedback berada di footer dengan viewport sesuai tinggi terukur. Foto/glyph/forms adalah komponen/aset terpisah, bukan screenshot/hotspot.

Haze1.6.10 Apache-2.0 dipin dan compatibility build diperiksa dengan existing Kotlin2.2.10/BOM2025.08.01/AGP9.1.1. Blur18dp + automatic input scaling pada API31+ non-low-RAM/battery saver off; API26–30/low-RAM/battery saver/disabled memakai opaque mint. License/notice ada di APK assets/third-party/haze. Tidak mengklaim shader refraction Apple; rim/depth lokal bersifat presentasi. Android system bars asli dan minSdk26 tetap.

| Sampel gfxinfo non-debug,18 pergantian tab | Frames | Jank/deadline miss | P50/P90/P95/P99 |
|---|---:|---:|---|
| Auto blur, tanpa recording | 537 |60 (11,17%) |17/25/29/34ms |
| Opaque fallback, tanpa recording |540 |54 (10%) |18/26/30/34ms |
| Awal, recording dan tanpa Auto |543 |262 (48,25%) |31/48/57/81ms |

Raw log: frame-sample-profile-optimized.txt, frame-sample-profile-fallback.txt, frame-sample-profile-recorded-before.txt; recorded-final terpisah. Dua kondisi berubah dari sampel awal; tidak mengklaim pengurangan hanya akibat blur. Skenario populated fixture, emulatorAPI37x86_64/density420, instrumentation stepped frame clock. Jank tetap ada dan timing/GPU emulator/recording berbeda dari HP; bukan PASS performa greenhouse atau score kemiripan. Variant profile tidak diterbitkan sebagai rilis produksi.

Deviasi visual tersisa: Roboto/system bars dan logo existing; foto daun/melon baru/glyph lebih sederhana, rim tanpa lens shader; placeholder lebih kontras; label Pratinjau tidak masuk produksi; provenance/age/reload/pagination/reasons dapat menambah tinggi; tujuh hari shift wrap2baris dengan touch48dp dan notes/Atur shift bisa membutuhkan scroll. Version server3 adalah fixture. PPM/header diiterasi kembali sesudah perbandingan. UI menyesuaikan density/font/insets, tidak memaksa poster. Lihat UI_REDESIGN dan README output untuk mapping/perbedaan tiap view serta prompt/path dua aset.

NOT_VERIFIABLE/OPEN: runtime API26–32 dan OEM/low-RAM/battery saver/performa pada HP greenhouse; calibration/sensor/RTC/SD power loss/LCD/buttons/LED/buzzer/WiFi fisik; full spoken accessibility/process death/soak; pentest/reviewer/SOP multi-platform/Q-10/hand-over. Tidak ada flash/backend destructive operations atau produksi diisi fixture. System animation settings emulator dipulihkan ke null(default1)/1.0/1.0.

## Verifikasi redesign Android — 11 Oktober 2026

Permintaan redesign mengotorisasi perubahan presentation Kotlin/Compose existing. Hasil berikut adalah bukti terbaru Android pada branch `codex/android-ui-redesign`; hasil backend/firmware di bagian audit berikut tetap historis dan tidak dijalankan ulang untuk redesign. Data layer, OwnerViewModel, ConfigDraft, API.md dan dependency tidak berubah dibanding snapshot sebelum redesign. Tidak ada commit/push/deploy/flash.

| Pemeriksaan redesign | Hasil | Bukti dan cakupan |
|---|---|---|
| Assemble debug, unit, lint | PASS; unit **8/8**; lint **0 errors, 17 warnings, 1 hint** | `output/redesign-window-final-checks.txt`, `output/TEST-redesign-unit.xml`, `output/redesign-lint.txt`. APK final `output/inofarm-jogja-redesign-debug.apk`, bukan release. Warning mencakup versi dependency, SharedPreferences, backup rules, dan primitive state existing; tidak disembunyikan atau diatasi dengan upgrade di luar scope. |
| Suite UI/state lengkap | PASS **20/20**, 0 failures/errors/skipped | `output/redesign-final-checks.txt`, `output/TEST-redesign-ui.xml`: EditorUiTest7 + OwnerStateTest6 + OwnerUiTest1 + RedesignUiTest6. Menguji draft/recreation/ACK/conflict, back/tab/filter, session/cache/concurrency existing, layout serta provenance. Sesudah penyesuaian terakhir icon system bar terang, assemble/unit/lint dan OwnerUiTest1 + RedesignUiTest6 diperiksa ulang **7/7** pada source final; `output/TEST-redesign-window.xml`. Counts ini run terpisah, bukan 27 skenario berbeda. |
| Animasi sistem dimatikan | PASS **13/13** | EditorUiTest7 + RedesignUiTest6 dengan animator/transition/window scales=0; `output/redesign-animation-off-tests.txt`, `output/TEST-redesign-animation-off.xml`. Ketiga setting dikembalikan. Run ini sebelum penyesuaian icon system bar terakhir; kode motion tidak berubah sesudahnya. Tidak membuktikan frame-time/performa perangkat target. |
| Review render emulator | PASS lokal | 14 screenshot flow/layout final ditinjau: login, dashboard, history/detail, settings, workers/shifts, offline, metrik/nav/editor font200%, keyboard, landscape. `output/redesign-*.png`. Font200% menggunakan LocalDensity dan area Compose 280 dp; landscape mengubah orientasi Activity nyata. Login settled saat system night mode juga ditinjau pada `output/redesign-system-dark-login.png`. |
| Kontras dan semantics | PASS pemeriksaan terbatas | Token foreground aktif diuji matematis ≥4.87:1; putih/brand 7.92:1, `output/redesign-contrast.txt`. Tes layout memeriksa label nav tanpa overflow, selected semantics dan target ≥48 dp. Status memiliki teks selain warna; warning/clock/partial/reasons tetap terlihat. Ini bukan audit aksesibilitas seluruh pixel/perangkat. |
| TalkBack | PASS smoke login terbatas | TalkBackService benar-benar bound, field Email/Password/Alamat API dan tombol Masuk ditemukan dalam tree; password node memiliki `password=true`. `output/redesign-talkback-service.txt`, `output/redesign-talkback.xml`. Layanan, accessibility_enabled dan night mode dipulihkan. Urutan node login diperiksa; full spoken/focus walkthrough seluruh layar **NOT_VERIFIABLE** pada pekerjaan ini. |
| Scope, index dan whitespace | PASS | Perbandingan snapshot `output/redesign-baseline` memastikan data/VM/draft/kontrak serta OwnerStateTest/OwnerUiTest tetap identik. 175 staged entries tidak berubah; `git diff --check` lulus. File implementasi/rationale di [UI_REDESIGN](UI_REDESIGN.md); checksums artefak di `output/SHA256-redesign.txt`. |

Semua data nonempty screenshot berasal fixture instrumentation, ditandai sebagai fixture pada flow utama dan tidak menjadi fallback APK. State kosong produksi tetap kosong. Regresi mencakup loading, partial/quality, invalid clock, incomplete + warning, error/offline/last-known, empty configuration, filter draft, editor recreation, tab/back dan export/picker existing. Filter/ekspor/validasi tetap memakai perilaku backend existing; tidak ada aturan agronomis, endpoint atau jadwal produksi baru.

Glass navigasi memakai **opaque tinted solid** dengan highlight tipis/elevasi, tanpa blur/refraksi maupun ketergantungan kontras pada konten terang/gelap yang bergerak. Konten angka/grafik/form/history solid. Layout/inset/IME telah diperbaiki dari temuan render; failures iterasi tidak dipakai sebagai evidence PASS.

**Acceptance tetap terbuka / NOT_VERIFIABLE:** hardware/sensor/calibration/RTC/SD/LCD/tombol/LED/buzzer, WiFi greenhouse, full spoken TalkBack, frame profiling/soak perangkat target, seluruh API/OEM/tablet dan usability greenhouse, process-death acceptance, pentest/reviewer/GitLab/SOP multi-platform, Q-10 correction/delete serta deployment/backup/retention. Keberhasilan lokal ini tidak menutup gate handover tersebut.

## Hasil terbaru perbaikan audit T1–T7

Bagian ini menggantikan counts dan keterbatasan software baseline di bawah. Seluruh T1–T7 diotorisasi pengguna; kebijakan 30 detik/cache disetujui eksplisit. Source di branch `codex/audit-fixes-t1-t7`; 175 staged file awal dipertahankan, perubahan baru tidak di-stage. Tidak ada commit/push/deploy/flash atau perubahan React.

| Pemeriksaan terbaru | Hasil | Bukti dan batas |
|---|---|---|
| Guard terisolasi | PASS 4 tests, 12 assertions | `TestDatabaseGuardTest`: effective URL path/query, read/write, requested testing vs cached environment; tidak membuka SQL/mutasi. |
| Backend MySQL | PASS 18 tests, 247 assertions | `output/backend-audit-tests.txt`; `composer test` menjalankan guard dahulu. Same-day jadwal lintas row/duplicate dalam row, trusted capture vs replay/invalid receipt, expiry/revocation, disabled device/limiter, isolasi tank, malformed/conflict/replay. |
| Android unit | PASS 8 tests | ContractTest: rentang teknis/draft serialization, envelope/freshness/quality/time/source-age, malformed/HTTP serta baseline contracts. |
| Android UI/state | PASS 14 tests dalam run UI/state; EditorUiTest terbaru 7/7; OwnerStateTest terakhir 6/6 | EditorUiTest 7 + OwnerStateTest 6 + OwnerUiTest 1: draft ID/version/active saat recreation; create ACK; conflict; document picker; partial resource/action failure; stale query/session/logout; cache encryption/scope/max500/expiry/recreated ViewModel; empty/back/rotation. Run state terakhir juga memastikan cancellation request lama tidak mematikan loading request pengganti; `output/android-audit-state-tests.txt`. |
| Android real backend | PASS 1 test, opt-in realBackend=true | RealBackendTest repository→Laravel HTTP→MySQL inofarm_test: login/config/phase/worker/shift/device event/replay/immutable rename/history/export/logout. Dijalankan terpisah sebelum run UI/state terbaru; total 15 skenario berbeda, bukan klaim satu run final 15. |
| Font/animation off | PASS 1 targeted instrumentation test | Font 200%, lebar 280 dp, field dapat diakses, grafik bersemantik dan loading statis, ketiga animation scales=0. Settings emulator dikembalikan. `output/android-animation-off-tests.txt`. |
| Android assemble/lint | PASS; lint 0 errors, 17 warnings, 1 hint | APK debug terbaru `output/inofarm-jogja-audit-debug.apk`; bukan APK release/production. Report lint audit dan SHA256 artefak terpisah dari baseline. |
| Firmware native | PASS 8 tests | Parsed transport/bench gate, fairness/backlog/conflict/timeout/failed ACK persistence, stable body/UUID, alarm deadline wrap, menu/week boundaries. Simulasi portable, bukan timing board. |
| Firmware ESP32 | PASS headless dan adapter compile-check | Worker jaringan/queue/alarm code compiled. Adapter RAM 22,884 B (7.0%), flash 398,341 B (30.4%). Pin/kalibrasi tetap kosong; tidak di-flash. |
| Accessibility | PASS smoke terbatas | TalkBackService benar-benar bound pada emulator; login accessibility tree memuat field/button dan password masked. `output/talkback-smoke.txt`, `output/ui-audit-talkback.xml`. Layanan dipulihkan ke awal. Belum walkthrough ucapan seluruh layar. |
| Manifest/style/working tree | PASS | Composer validate --strict; Pint pada file PHP berubah; PowerShell provisioning parser; git diff --check. Tidak ada staging baru/React diff. |

`output/ui-audit-large-font.png` dan `output/ui-audit-dashboard.png` adalah screenshot emulator, bukan angka produksi. Daftar fixture 500 record memverifikasi <20 heading record terkomposisi serta alasan tetap ada; belum pengukuran frame-time/soak pada perangkat target.

Guard sebelum bootstrap terbukti melalui isolated regression dan seluruh backend tests pada inofarm_test. Fresh provisioning ditambah akun test khusus yang hanya punya grant test; provisioning akun tersebut belum dieksekusi pada instance yang sudah ada. Instance pengujian tetap memakai akun legacy dengan grant lama; tidak mengklaim least-privilege terverifikasi.

Cache adalah satu snapshot encrypted schema 1/server-account-tank, max500 history dan max2 MiB. Oversize/write failure mempertahankan cache lama, cursor cache null, known expiry wajib. Restart/ViewModel restoration, expiry/scope/encryption diuji; tidak mengklaim seluruh process-death, revocation offline atau storage fault pada perangkat fisik sudah diuji. Save acknowledgement dan status refresh terpisah; UI tidak mengklaim operasi gagal jika backend sudah mengakui sukses.

**Gate tetap terbuka:** hardware/calibration/RTC/SD power-loss/LCD/tombol/LED/buzzer/network greenhouse, firmware TLS/latency fisik, TalkBack full spoken walkthrough, device frame profiling/soak, process-death acceptance, pentest berizin, reviewer/GitLab/SOP multi-platform, deployment/backup/retention dan Q-10 correction/delete. Tidak ada agronomic threshold/jadwal/roster produksi baru.

## Source yang berubah dan alasan

| Kelompok file | Perubahan |
|---|---|
| backend/app/Support/TestDatabaseGuard.php, bootstrap/app.php, tests/TestCase.php, phpunit.xml, tests/Unit/TestDatabaseGuardTest.php; scripts/test-backend.ps1, test-fixture.php; backend/composer.json/.env.testing.example/.gitignore; scripts/local-database.ps1 | Guard sebelum DB mutation, isolated safety regression, force testing environment dan fresh-only test account. Existing credentials tidak diubah. |
| backend/app/Services/Input.php, Http/Controllers/AuthController.php/ReadController.php; tests/Feature/InofarmTest.php/SecurityTest.php | Days per row, stable identity/expiry, trusted reading vs latest receipt dan perluasan security/endpoint regressions. |
| android data/ApiClient.kt, OwnerRepository.kt, Models.kt, SessionStore.kt, ReadCache.kt | Cancellation, authoritative envelopes, freshness provenance, encrypted scoped session/read cache. |
| android ui/OwnerViewModel.kt, OwnerApp.kt, SettingsScreen.kt, WorkersScreen.kt, ConfigDraft.kt; MainActivity.kt | Resource/action/request-version/session/query/lifecycle, saveable editor/draft/ACK/baseVersion, export recreation. |
| android ui/DashboardScreen.kt, HistoryScreen.kt, UiComponents.kt; test/ContractTest.kt dan androidTest/OwnerUiTest.kt/OwnerStateTest.kt/EditorUiTest.kt | Honest freshness/loading/last-known, lazy keys/pagination/reasons, static loading/font/semantics dan regression evidence. |
| firmware/include/transport.h, secrets.example.h, src/main.cpp, test/test_core/test_main.cpp | Explicit bench gate/host parse, independent fair queue/network worker, retained blocked/ACK/stable event, alarm deadline tests. |
| .gitignore, README.md, firmware/README.md; API/DECISIONS/VERIFICATION dan lima docs/context | Ignore Kotlin generated cache; repeatable commands, keputusan/kontrak/progress/evidence terbaru dengan batas yang tetap terbuka. |

Paths Android di tabel relatif terhadap `android/app/src/main/java/com/inofarm/jogja/` kecuali test di source set terkait. File baru tetap untracked untuk review; 175 staged awal tidak di-stage ulang. Tidak ada dependency baru atau perubahan source React.

## Evidence baseline sebelum perbaikan audit

Counts dan pernyataan belum ada cache/UI nonempty berikut adalah snapshot implementasi awal; kondisi software terbaru di tabel atas.

Pengguna mengizinkan coding/testing/debugging Android, Laravel/MySQL dan ESP32 pada chat ini. Tidak ada commit/push/deploy atau flashing. React/reference/proposal/SOP tidak diubah. Status IMPLEMENTED_LOCAL tidak berarti seluruh gate greenhouse/akademik selesai.

## Lingkungan dan versi resolved

| Komponen | Versi / bukti |
|---|---|
| Backend | PHP portable 8.5.11, Laravel 13.35.0, Sanctum 4.3.3, PHPUnit 12.5.38; composer.lock dan Composer validate --strict lulus. |
| MySQL | 8.0.30 executable Laragon; instance/data khusus workspace di port 3307, schemas inofarm_local dan inofarm_test. Laravel session time_zone +00:00 diuji. |
| Android | AGP 9.1.1, Gradle 9.3.1, built-in Kotlin/Compose compiler 2.2.10, JDK Android Studio 25.0.3, compile/target SDK 37, min SDK 26. Compose BOM 2025.08.01, OkHttp 4.12.0. |
| Emulator | Medium_Phone_API_37.0, Android 17; actual connected instrumentation, bukan preview Compose. API HTTP nyata melalui adb reverse 8001. |
| Firmware | PlatformIO 6.1.18, espressif32 6.12.0, Arduino framework 2.0.17, GCC Xtensa 8.4.0 patch5, native GCC MinGW 5.1.0. Lib BOM versions platformio.ini. |

PHP/Gradle/large firmware archives diverifikasi checksum upstream sebelum dipakai. Keputusan/tool docs resmi di DECISIONS.md. Dependency lebih baru tersedia bukan alasan upgrade otomatis; versi kompatibel dipin dan build/test telah dijalankan.

## Hasil pengujian

| Pemeriksaan | Hasil | Cakupan / bukti source |
|---|---|---|
| PHPUnit MySQL | PASS **10 tests, 86 assertions** | InofarmTest, SecurityTest, NutrientValidatorTest, health test. Empty configuration/auth/clock; multi-reason; bounds; mismatch; asymmetric schedule windows; replay/conflict/as-of; shift midnight/overlap/unscheduled/rename/disable; cursor/date/export; role/tank isolation/rate-limit/payload; UTC DB session. |
| Pint seluruh backend | PASS | `php backend/vendor/bin/pint backend --test` setelah formatting; bukan hanya sebagian file. |
| Composer manifest/lock | PASS | `php .tools/composer.phar validate --working-dir=backend --strict`. |
| Android assembleDebug | PASS | APK actual `android/app/build/outputs/apk/debug/app-debug.apk`; salinan output/inofarm-jogja-debug.apk. |
| Android unit | PASS **6 tests** | ContractTest: production empty, phase editor isolation, malformed/finite/bounds/time/enum, full reasons+time mapping, quality mismatch, real HTTP access/base-url errors melalui MockWebServer. |
| Android connected | PASS **2 tests, 0 skipped/failures** | OwnerUiTest empty/navigation/back/rotation; RealBackendTest repository → real Laravel HTTP → MySQL. Login/config kosong round-trip/fase, pekerja/shift, persisted device record, same ACK replay, immutable name after rename, history/dashboard/CSV/logout. Fixture hanya inofarm_test. |
| Android lint | PASS **0 errors, 14 warnings, 1 hint** | `output/android-lint.txt`. Warnings newer pinned dependencies/KTX/state recommendations; tidak disembunyikan sebagai nol warning. |
| ESP32 headless | PASS build/link/bin | `esp32dev`; tanpa wiring/secrets produksi. RAM 21,736 B (6.6%), flash 309,857 B (23.6%). |
| ESP32 adapters | PASS compile-check | `esp32_compile_check` mencakup driver BOM, LCD/button/RTC/SD/LED/buzzer code; pin masih -1, **binary tidak di-flash/didistribusikan untuk operasi**. |
| Firmware native | PASS **4 tests** | Review/durable-save gate, cancel, ACK persisted+same ID, [start,end) dan Sunday rollover. Bukan hardware/SD durability test. |
| HTTP local | PASS health 200 / unauthorized dashboard 401 | Backend normal localhost:8000; testing localhost:8001. Kedua menggunakan MySQL, bukan SQLite/mock DB. |

Perintah lengkap ada di README. Report Android XML dan lint disalin ke output/ dengan APK/firmware; SHA256SUMS.txt memverifikasi artefak yang dibagikan. Source tests tetap dapat dijalankan ulang. Screenshot `output/ui-dashboard.png` menunjukkan UI emulator berdata kosong/loading, bukan contoh angka produksi.

## Debugging dan review yang telah dilakukan

- Android SDK local.properties colon escaping diperbaiki untuk lint; AndroidX test/espresso 3.7 dipakai karena InputManager lama tidak kompatibel Android 17. UI assertion menunggu history loaded, navigation/rotation diuji.
- Tidak mengandalkan koneksi emulator 10.0.2.2 yang timeout; adb reverse menyediakan API localhost tanpa perubahan firewall.
- Runtime source split presentation/data, Android token AES-GCM/Keystore, API base configurable, production HTTP ditolak, React source dipertahankan.
- Precision UTC/version time mempertahankan microseconds; invalid/future device clock raw tersimpan tetapi source index null dan tidak dipakai assignment. MySQL connection timezone eksplisit UTC.
- Warning mismatch fase ikut status issues sesuai B tanpa menolak input. Asymmetric tolerances mengecek semua window yang mungkin cocok, bukan hanya nearest yang dapat keliru.
- JSON empty maps config/snapshot tetap object {}, diuji round-trip Android/PHP/MySQL. Initial values tidak diisi fixture ke produksi. CSV membedakan clock/measurement/confirmation/receive/evaluation dan seluruh alasan/version.
- PlatformIO package-hook quoting untuk path spasi/& diperbaiki pada local ignored virtualenv; script mendokumentasikan surgical compatibility patch. GCC command mencapai >37k karakter; Windows SUBST sementara memperpendek path tanpa memindahkan source. Esptool Python dependencies dipasang pada virtualenv, bukan Python global.
- Firmware adapter compile terpisah mencegah klaim headless build sebagai bukti driver fisik. TLS system clock diambil dari RTC UTC yang sudah diatur dan tidak lostPower; LCD monitoring menampilkan WIB.

## Batas dan gate yang tetap terbuka

**NOT_VERIFIABLE fisik:** pinout/power/ADC PH4502C/calibration, sensor accuracy/disconnect, RTC commissioning/drift, LCD/tombol/debounce, LED/buzzer/reminder timing, MicroSD power-loss/restart/replay durability, WiFi/TLS greenhouse, handheld workflow dengan pekerja. Tidak ada alat terhubung/flash dilakukan. Clock trusted server hanya kebijakan deklarasi/struktur/nonfuture, bukan verifikasi RTC akurat.

**Belum acceptance:** soal SOP multi-platform Android-only, GitLab/network kampus, schema retention/backup/deployment, operational device token rotation, field usability/performance/soak, pentest berizin dan reviewer/handover. History correction/delete Q-10 menunggu keputusan; versi pekerja/shift bukan izin overwrite record nutrisi.

UI umum/data-empty dan repository nonempty diuji; belum menyatakan seluruh form/nonempty visual/accessibility atau process-death/offline Android sudah diuji. Belum ada local persistent offline cache Android; kegagalan setelah data dimuat diberi error/last-known di memory, restart memerlukan API. Sampling teknis dua detik/poll retry tiga puluh detik bukan cadence/threshold produksi owner. Semua nilai produksi/roster awal kosong dan memerlukan konfigurasi owner.
