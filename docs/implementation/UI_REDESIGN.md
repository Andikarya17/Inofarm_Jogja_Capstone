# Redesign UI Android

## Sembilan referensi disetujui — 12 Oktober 2026

Instruksi pengguna dan output/liquid-glass-concepts-20261012/PROMPT_IMPLEMENTASI_CODEX.md mengotorisasi implementasi sampai APK, navigasi, aset, animasi, dependency minimal dan verifikasi. Seluruh sembilan PNG dibuka dengan view_image sebelum edit. README/PENGATURAN/detail README, AGENTS, bagian SOP, lima context, API/DECISIONS/VERIFICATION/source serta tabel KF/KNF proposal dibaca. Branch kerja existing codex/android-ui-redesign dipertahankan. Baseline source/status/diff/hash ada di output/liquid-glass-implementation/baseline; index SHA256 tetap E341D18A7F377602FF6240623E1EDDF253CB7131A730AADD045C2AD94911D433. Tidak stage/reset/stash/commit/push/deploy.

Dashboard memakai hero forest dengan melon, empat metrik mint, nutrisi terakhir; tren, age, contact/capture/receive/fetch/clock dan seluruh alasan tetap tersedia. Riwayat memakai selector hari ini/7hari/filter, journal, seluruh checks termasuk warning/pass/incomplete, metadata penuh/ID, pagination dan ekspor filter. Pengaturan hub membuka pekerja/shift dan empat editor. Refresh/logout tersedia di menu akun dan hub; login/session/cache/private scope tetap existing.

| View target | Runtime |
|---|---|
| dashboard.png | DashboardScreen, grid adaptif; dialog kualitas sensor; detail record/provenance/tren |
| riwayat.png | HistoryScreen, RecordCard, metadata/filter/pagination/export |
| pengaturan.png | SettingsScreen hub, fase aktif langsung tersimpan terpisah |
| pekerja-shift.png | WorkersScreen segment Pekerja, create/edit/active/inactive dan list shift |
| jadwal-nutrisi.png | SettingsScreen schedule, hari/jam WIB/tambah/hapus/toleransi |
| rentang-ppm.png | SettingsScreen ppm, selector editor dua fase tanpa mengubah fase aktif |
| ambang-sensor.png | SettingsScreen sensors, delapan batas pada empat grup |
| usia-perangkat.png | SettingsScreen device, empat durasi dan dua versi terpisah |
| form-shift.png | WorkersScreen segment Shift, pekerja/hari/jam/date picker/optional end date dan footer |

Satu ConfigDraft saveable lintas halaman, baseVersion/pending ACK/checkpoint. Back menjaga draft; Batal memulihkan checkpoint sejak halaman dibuka, termasuk bagian lain yang sudah ada. Reload menerima snapshot/versi server secara explicit. Save mengirim seluruh konfigurasi, bukan PATCH; konflik tidak membuang teks. Worker/shift ID/version/key/mode/draft/ACK tetap. Back sistem dari Shift menuju Pekerja menjaga form; Batal Shift reset form. Header back pop route mengikuti pola existing. Tanggal tidak diisi sebelum owner memilih. Overlap/assignment tetap backend authoritative. Backend/firmware/React/API/ConfigDraft/OwnerViewModel/data layer tidak berubah.

Haze **1.6.10**, Apache-2.0, satu dependency glass; Kotlin2.2.10/Compose BOM2025.08.01/AGP9.1.1/minSdk26 tetap. API31+ non-low-RAM memakai blur18dp + HazeInputScale.Auto, tint/rim putih/mint dan depth. API26–30, low-RAM dan battery saver memakai opaque mint; effect-disabled branch diuji pada API37. Tidak mengklaim shader lens refraction atau semua API identik. Navigation/footer sample backdrop botanical asli sambil mereservasi viewport agar field tidak tertutup; segmented source lokal. Lisensi/notice dibundel di assets/third-party/haze. Keputusan compatibility dan tautan maintainer ada di DECISIONS.

Spring .85/450 untuk pill, press120ms, fade220ms dan disclosure200ms; system duration scale termasuk0 dihormati. Tidak ada perhitungan nilai sensor palsu, shimmer/pulse atau animasi polling. Header/selector/detail/back/footer memakai Compose interaktif; phone frame dan status palsu dikeluarkan. System bars/insets existing nyata. Placeholder diperkuat untuk kontras dan input fokus memakai rim forest; font besar/narrow melipat layout serta scrolling.

Aset lokal **android/app/src/main/res/drawable/melon_hero.webp** dan **leaf_hero.webp** dibuat dengan built-in image_gen, dioptimasi WebP tanpa teks/UI. Prompt melon: “Wide botanical photograph, ripe netted cantaloupe at far right with melon vine leaves/dewdrops, empty left forest #194b36, no text/logo/phone/UI.” Prompt daun: “Wide dark forest #194b36, only modest real melon vine leaves/stem at far right, empty left75%, no melon fruit/text/logo/UI.” Original PNG generated tetap di C:/Users/ACER/.codex/generated_images/01a12eec-c228-79e2-93b9-55891212b955/; dua nama exec-c85fd2f9-77ba-4fcd-b822-0f60d6fff93b.png dan exec-aca633d3-2aed-495f-bd1d-16a8418e6bc2.png. Logo existing dipertahankan; seluruh glyph vektor terpisah dari raster.

File utama: DesignSystem.kt, GlassComponents.kt, EditorComponents.kt, OwnerApp.kt, DashboardScreen.kt, HistoryScreen.kt, SettingsScreen.kt, WorkersScreen.kt dan UiComponents.kt. build.gradle.kts menambah pinned Haze serta variant profile non-debug/signing lokal untuk diagnosis. Existing OwnerUiTest/EditorUiTest/RedesignUiTest diadaptasi pada route/tag/footer baru dengan assertion perilaku tetap; LiquidGlassUiTest/GlassRenderingTest/GlassPerformanceTest menambah evidence. Delapan docs implementation/context diperbarui. Tidak ada fixture dalam main APK. Error simpan konfigurasi/shift tampil di atas footer; tinggi footer diukur agar viewport field otomatis menghindarinya.

Unit 8, lint0 errors dan suite UI/state **27** lulus dengan animasi aktif; suite **27** animation-off juga lulus. Setelah spacing detail terakhir, targeted layout/runtime diperiksa kembali (lihat VERIFICATION untuk hasil final13). Date picker cancel, keyboard/login synchronization, system back, shared draft/cancel/conflict/baseVersion/ACK, edit inactive worker, create/overnight/edit shift, filter/pagination/export picker/recreation, list500, offline/clock/full reasons, session/logout/cache dan landscape tercakup. Source copies/hashes mempertahankan perubahan lokal pengguna; API/data/backend/firmware/React dan sembilan PNG asli cocok baseline.

Perbandingan asli-vs-runtime sembilan view ada di [contact sheet](../../output/liquid-glass-implementation/comparisons/all-nine.png); tiap view mempunyai pasangan sendiri. Crop hanya salinan evidence; width360 dan aspect ratio dipertahankan, crop mapping dicatat. Screenshot1080×2400 emulator API37/density420; angka980/25,6/6,2/29,1/72 serta12Oktober hanya fixture instrumentation. Video transisi non-debug di output/liquid-glass-implementation/animation-profile.mp4; metadata dan lima frame diperiksa. Tidak membuat skor kemiripan.

Sampel non-debug tanpa recording, populated fixture, 18 pergantian tab: full blur Auto537 frames, jank60/537 (11,17%), P50/P90/P95/P99=17/25/29/34ms. Opaque fallback540 frames, jank54/540 (10%),18/26/30/34ms. Sampel awal sambil merekam dan tanpa Auto menghasilkan48,25%; kedua kondisi berubah sehingga penurunan tidak diatribusikan hanya pada blur. Sampel stepped instrumentation/emulator bukan jaminan FPS perangkat atau penerimaan greenhouse. Jank tetap terukur; tidak mengklaim performance PASS tanpa batas.

Deviasi tersisa: Roboto/system bars/logo existing berbeda dari raster, foto daun/melon baru dan watermark vektor lebih sederhana, rim/refraction tidak identik hasil generator, placeholder lebih gelap untuk keterbacaan. Header produksi menghilangkan Pratinjau; label pH larutan lebih spesifik daripada pH air. Provenance/age/reload/full reasons menambah konten. Hari shift membungkus dua baris agar target48dp; notes/Atur shift pada viewport tertentu memerlukan scroll. PPM spacing diiterasi untuk mendekati komposisi acuan. Versi server3 pada screenshot fixture merupakan data test, bukan default produksi. Semua field/action dapat dijangkau, tidak memaksa poster muat satu viewport.

NOT_VERIFIABLE: runtime API26–32/OEM/HP greenhouse, actual low-RAM/battery-saver transition pada perangkat fisik, hardware/calibration/RTC/SD/LCD/buttons/LED/buzzer/network, full spoken TalkBack/process-death acceptance/soak, pentest/reviewer/SOP multi-platform dan handover. Gates tersebut tidak dinaikkan DONE. Laporan akhir dan checksum ada di output/liquid-glass-implementation/README.md dan VERIFICATION.

## Riwayat redesign — 11 Oktober 2026

Scope diotorisasi melalui permintaan pengguna yang memanggil gpt-taste dan redesign-existing-projects. Perubahan hanya presentation Android, window/IME setup, instrumentation terkait, dan dokumentasi. Branch kerja `codex/android-ui-redesign`; index staged sebelumnya tetap sama. Backend, firmware, data layer, OwnerViewModel, ConfigDraft, kontrak REST, dependency, root React, proposal dan SOP tidak diubah dalam pekerjaan ini. Tidak ada commit/push/deploy/flash.

## Audit dan sumber

Kedua SKILL.md dibaca langsung dari C:/Users/ACER/.agents/skills/. Prinsip yang dipakai: scan/diagnose/targeted fix, hierarki informasi, kontras, typography berjenjang, restraint pada kartu, motion yang menjelaskan state. Instruksi khusus pengguna mengatasi randomisasi/Python palsu, AIDA, React/Tailwind/GSAP, marketing, foto acak dan interaksi hover. Implementasi tetap Kotlin/Compose existing.

AGENTS.md, addendum lima docs/context, API/DECISIONS/VERIFICATION, SOP GitLab h.1–2, SOP Capstone h.1–2, serta tabel kebutuhan KF/KNF proposal dibaca. Baseline audit lama dibedakan dari modul native yang sudah ada. SOP multi-platform, pentest/reviewer dan acceptance hardware tetap terbuka.

Baseline runtime: OwnerUiTest sebelum perubahan lulus 1 test pada Medium_Phone_API_37.0. `output/redesign-before-dashboard.png` diperiksa: empat metrik tersusun satu kolom, metrik keempat jauh di bawah fold, rincian kesegaran tersebar, navigasi memakai simbol teks dan warna default. Audit source juga menemukan riwayat padat metadata, semua section berbobot serupa, dan typography/token yang belum terpusat.

## Keputusan dan manfaat untuk owner

| Perubahan | Kebutuhan yang dibantu |
|---|---|
| Hero berwarna botanical memuat konteks tandon, fase, koneksi dan waktu fetch | Owner segera mengetahui fase dan apakah klaim koneksi sudah diperiksa. Tidak menciptakan skor kesehatan atau nama/ID tandon yang tidak tersedia di DTO. |
| Empat metrik label → nilai/unit → kualitas | Perbandingan sensor lebih cepat. Grid 2×2 hanya ketika area konten ≥360 dp dan fontScale ≤1.3; selain itu satu kolom. Nilai null tetap kosong; quality diterjemahkan sebagai bacaan tersedia/tidak tersedia/gagal/belum dikalibrasi, tanpa klaim agronomis normal. |
| Waktu capture dan usia menurut server dekat metrik | Capture, receive, contact dan fetch tetap berbeda. Usia memakai serverTime existing, tidak menjadi TTL baru. Last-known/loading tidak mengklaim kontak baru. Clock kiriman terbaru yang tidak dipercaya langsung diberi pesan. |
| PPM manual, fase ESP32, waktu konfirmasi dan pekerja terjadwal mendahului metadata | Owner dapat memindai aktivitas nutrisi. Semua reasons tetap tampil, termasuk warning ketika badge utama incomplete. Waktu measurement/receive/evaluation, fase owner dan versi tersedia lewat disclosure. |
| Filter bisa dibuka; ringkasan filter diterapkan dan ekspor selalu jelas | Daftar lebih cepat dijangkau. Draft filter tetap terpisah dari appliedFilters; ekspor dan pagination memakai kontrak existing. Immutable keys/LazyColumn tetap dipakai. |
| Pengelolaan pekerja/shift ditempatkan di awal Pengaturan | Owner tidak perlu melewati semua field konfigurasi untuk mengelola roster. Tab utama tetap tiga; pekerja merupakan subhalaman dengan back. |
| Form berkelompok, helper opsional, error dekat field, keyboard sesuai tipe | Nilai produksi tetap kosong. Error memakai batas teknis existing; penyimpanan dan validasi backend tidak diganti. Versi tersimpan server dibedakan dari versi applied yang dilaporkan ESP32. |

## Sistem visual dan motion

`DesignSystem.kt` memusatkan palette, typography, spacing, shape, depth, motion dan InofarmTheme. Font Android sans-serif existing; tidak ada font/library baru. Angka sensor/PPM memakai `tnum`; tidak menganimasikan angka perantara. Background #F4F6F0, brand #245B43, ink #203C30, teks secondary #53675B; surface konten opaque. Amber/error/info tetap semantik, dengan label teks. Token kontras foreground aktif yang diuji berada pada ≥4.87:1; putih pada brand 7.92:1. Ini pemeriksaan matematis token dan review render, bukan audit aksesibilitas seluruh pixel/perangkat.

Radius kontrol 12 dp, grup konten 20 dp, hero 24 dp, navigasi 28 dp. Kartu digunakan untuk grup fungsional; metadata memakai whitespace/divider. Tiga ikon navigasi vektor lokal memakai stroke konsisten. Logo existing dipertahankan. Angka, grafik dan fixture tidak dimasukkan ke fallback produksi.

Glass selektif pada navigasi memakai **fallback opaque tinted solid** #F0F5EB, highlight putih 1 dp dan elevasi 3 dp. Tidak ada backdrop blur, refraksi, alpha yang membuat teks bergantung pada konten bergerak, maupun dependency/API efek baru. Pada semua API, fallback ini sengaja menjadi hasil final. Mayoritas surface tetap solid. Scaffold mereservasi area navigasi; konten terakhir dapat di-scroll ke atasnya.

PrimaryAction memakai press scale 0.985 selama 120 ms serta ripple/focus native. Seleksi tab/disclosure 200 ms; fade navigasi 220 ms dengan exit 120 ms. Animasi Compose memakai pengaturan durasi sistem. Tidak ada shimmer/pulse/entrance polling atau animasi nilai sensor. Navigasi dengan font besar menjadi dua baris tab utuh. enableEdgeToEdge + adjustResize serta inset consumption mengatasi status bar/keyboard, tanpa status bar iPhone.

Dasar API resmi diperiksa 11 Oktober 2026: [accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults), [Compose motion](https://developer.android.com/develop/ui/compose/animation/quick-guide), [edge-to-edge](https://developer.android.com/develop/ui/compose/system/setup-e2e), dan [inset consumption](https://developer.android.com/develop/ui/compose/system/insets-ui). Build memakai dependency yang sudah dipin, bukan upgrade versi.

## File implementasi

- android/app/src/main/java/com/inofarm/jogja/ui/DesignSystem.kt — token, tema, tombol utama.
- android/app/src/main/java/com/inofarm/jogja/ui/OwnerApp.kt — toolbar, navigasi adaptif, login, fade; lifecycle/session/nav actions dipertahankan.
- android/app/src/main/java/com/inofarm/jogja/ui/DashboardScreen.kt — hero, grid adaptif, quality/provenance; pengolahan tren existing dipertahankan.
- android/app/src/main/java/com/inofarm/jogja/ui/HistoryScreen.kt — grouping/disclosure filter, applied summary, ekspor dan list existing.
- android/app/src/main/java/com/inofarm/jogja/ui/UiComponents.kt — section/banner, full reasons, metadata disclosure, field/helper/error dan loading statis.
- android/app/src/main/java/com/inofarm/jogja/ui/SettingsScreen.kt dan WorkersScreen.kt — grouping, radius, helper/error/keyboard dan aksi existing. Saveable draft/mode/ID/baseVersion serta matching ACK dipertahankan.
- android/app/src/main/java/com/inofarm/jogja/MainActivity.kt dan android/app/src/main/AndroidManifest.xml — tema serta edge-to-edge/adjustResize; auth/export/lifecycle code tetap.
- android/app/src/main/res/drawable/ic_nav_dashboard.xml, ic_nav_history.xml, ic_nav_settings.xml — ikon lokal.
- android/app/src/androidTest/java/com/inofarm/jogja/RedesignUiTest.kt — enam regresi layout/provenance/flow dengan fixture test-only; EditorUiTest.kt — sinkronisasi input sebelum recreation, tanpa melemahkan assertion pascarecreation/ACK.

## Verifikasi dan batas

Laporan final beserta counts ada di [VERIFICATION](VERIFICATION.md): unit 8, suite UI/state20, run terpisah animation-off13, serta final window/layout smoke7 sesudah penyesuaian terakhir icon system bar. Assemble/lint lulus; lint 0 errors, 17 warnings, 1 hint. Screenshot `output/redesign-*.png` merupakan emulator; screenshot data nonempty ditandai fixture, bukan greenhouse. Screenshot narrow/font200 memakai area Compose 280 dp dan Density fontScale=2; landscape melakukan orientasi Activity nyata. Baseline sumber/index disimpan di output/redesign-baseline untuk perbandingan lokal, tanpa mengganti staging pengguna.

Tema selalu terang; SystemBarStyle.light eksplisit menjaga icon system bar gelap ketika sistem memakai night mode. Render login settled saat night mode ditinjau. TalkBackService bound dan accessibility tree login memuat label field/aksi serta password protected; setting emulator dipulihkan. Pemeriksaan tree/semantics ini tidak membuktikan walkthrough ucapan/fokus seluruh flow.

Masalah yang ditemukan selama iterasi: test recreation dipanggil sebelum sinkronisasi penerimaan input; tes kini memeriksa teks sebelum/ sesudah recreation. Label navigasi terbelah di tengah kata, kemudian layout dua baris dan lebar teks diperbaiki. Theme root yang memaksakan minimum lebar pada harness narrow diperbaiki agar ukuran 280 dp benar-benar terpakai. Render keyboard menunjukkan ruang inset berlebih, kemudian edge-to-edge/adjustResize diperbaiki. Selector landscape dibedakan dari chip tren melalui test tag metrik. Log run gagal dipertahankan; tidak dipakai sebagai bukti PASS.

NOT_VERIFIABLE / acceptance terbuka: sensor/RTC/SD/LCD/tombol/LED/buzzer fisik, hardware calibration, WiFi greenhouse, performa/frame-time/soak pada perangkat target, semua kombinasi API/OEM/tablet, full spoken TalkBack walkthrough, process-death acceptance, pentest/reviewer/SOP multi-platform, Q-10 correction/delete dan deployment. Tidak ada klaim fluiditas Apple identik atau handover final.


## Delta performa — 13 Oktober 2026

Byte dua hero botanical dipertahankan, dipindah ke drawable-nodpi dan didecode IO sesuai viewport. State restoring statis, history window dan list pekerja lazy mengikuti delta performa. Liquid glass18dp/Auto tetap; root/editor melepas source/effect pada fallback. Screenshot after-dashboard.png/after-settings.png diperiksa dan profile rendering2 PASS (termasuk narrow/font200%). Performa tidak disimpulkan dari screenshot; lihat [ANDROID_PERFORMANCE](ANDROID_PERFORMANCE.md).
