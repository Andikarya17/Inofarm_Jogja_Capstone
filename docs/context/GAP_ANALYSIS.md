# Gap Analysis Proposal Source dan SOP

## Instalasi tim dan baseline GitHub — 24 Oktober 2026

Panduan komputer lain dan aturan ignore diperjelas; snapshot source diperiksa terhadap pola secret dan credential lokal. Backend suite/guard, Composer/Pint, Android build/unit/lint PASS lokal. Ini tidak membuktikan instalasi di komputer teman, pentest, hardware atau implementasi rancangan baru tanpa IoT. [Evidence](../implementation/GITHUB_PUBLICATION.md).

## Rancangan ulang tanpa IoT — 21 Oktober 2026

Target baru tanpa IoT telah disetujui pengguna; implementasi lama masih owner-only dengan perangkat, sehingga terdapat gap besar terhadap target pekerja/pengelola, multi-GH, input harian, sinkronisasi tulis dan keuangan. [paket diagram proposal](../design/mobile-greenhouse-20261021/README.md) memetakan KF-01–14 pada rancangan. Referensi Excel mengandung ketidaksesuaian tanggal spraying/teks pH; PDF makalah mengandung ketidakkonsistenan label B/C dan subtotal NPV. Model memakai definisi proposal baru, bukan menyalin kesalahan referensi. Aturan keuangan, agronomis dan kepatuhan target Android terhadap SOP multi-platform tetap terbuka. Tidak ada klaim pengujian aplikasi, UAT, pentest atau handover baru.

## Data demo lokal — 20 Oktober 2026

Ketiadaan hardware untuk eksplorasi struktur database ditangani fixture lokal eksplisit dengan akun demo terpisah. Ini bukan bukti ESP32/RTC/kalibrasi/greenhouse atau kelayakan handover. Full backend suite PASS20/404 dan HTTP akun demo PASS; UI APK khusus demo belum diuji. Owner asli utuh. Detail [DEMO_DATA](../implementation/DEMO_DATA.md).

## Publikasi Git per fitur — 15 Oktober 2026

Pengguna mengotorisasi commit per fitur dan push ke GitLab UMY `andika.arya.ft23/inofarm_jogja`, memakai identitas Andika Arya Putra dan email kampus yang diberikan. Riwayat remote `main` beserta konfigurasi SAST/Secret Detection dipertahankan; impor snapshot lokal dikerjakan pada branch `codex/project-import` dan disusun menjadi commit berdasarkan fitur. Ini pengelompokan source yang sudah ada, bukan rekonstruksi tanggal pengerjaan. Environment, credential, cache IDE, runtime, log dan artefak build tidak dipublikasikan. Verifikasi lokal dan prosedur ada di [GIT_WORKFLOW](../implementation/GIT_WORKFLOW.md); tidak mengubah requirement/kontrak/aturan agronomis dan tidak menutup gate hardware, pentest atau handover.

## Gap UI/navigasi yang dilaporkan pengguna — 15 Oktober 2026

Indikator filter tidak selaras dan pemilihan Dashboard memulihkan Pengaturan terbukti pada baseline OwnerApp; koreksi lokal terverifikasi dengan fixture terisolasi. Tinggi navigasi hardcoded tidak sesuai ukuran aktual, sehingga diganti pengukuran dan diberi tes batas viewport serta klik/scroll kontrol akhir pada OwnerApp, melengkapi tes lama yang memakai Scaffold tersendiri. Konten sama sekali tidak dapat diakses tidak terulang pada baseline AVD normal/font200/landscape; jangan menganggap gejala HP pengguna sudah direproduksi. Acceptance HP/OEM, hardware, full TalkBack/process death, pentest/reviewer/SOP multi-platform tetap terbuka. Bukti/hasil: VERIFICATION / outputs/ui-navigation-20261015/README.md.

## Gap tampilan run ulang — 15 Oktober 2026

APK berhasil diluncurkan tetapi embedded emulator tidak punya jendela tersendiri; panduan Running Devices dan launcher GUI Normal ditambahkan. Shared run config kehilangan module/build task dipulihkan; target Inofarm MainActivity explicit tersedia. CMD build/install/start PASS lokal, klik IDE/cold-start GUI belum diuji. Gate hardware/pentest/reviewer tidak ditutup.

## Gap login lokal — 14 Oktober 2026

Reverse emulator hilang membuat localhost menarget device sendiri; default10.0.2.2 dan izin debug Android17 menutup gap login emulator yang telah diperiksa langsung dari APK. Form API wajib pada setiap login diganti default configured dan disclosure; release server belum ditetapkan tidak disamarkan. Login/backend-read tanpa reverse, build/unit10/lint/UI1 PASS lokal. USB fisik/custom LAN, runtime denial/revocation/recreation prompt dan produksi belum terverifikasi; semua gate hardware/pentest/reviewer tetap terbuka. Detail VERIFICATION.

## Gap launcher — 14 Oktober 2026

Gap belum ada akun owner lokal ditutup melalui provisioning eksplisit pengguna; hash/role/tank/login/dashboard200 diperiksa. Credential tidak dijadikan akun default atau disimpan dalam source/docs; tidak menutup gate pentest/hardware. Detail VERIFICATION.

IDE awal membuka root sebagai modul umum dan tidak mengimpor Gradle android; launcher kini membuka android root dan menyediakan run config app. SDK37/JBR/AVD sudah tersedia; tidak diperlukan install ulang untuk build/launch yang PASS lokal. API lokal aktif dan halaman login emulator diperiksa. Sync selesai dan modul InofarmJogja.app terverifikasi; klik Run IDE serta branch cold-start/komponen hilang/komputer lain belum diuji langsung. Tidak menutup hardware/pentest/reviewer/SOP gates.

## Delta gap performa Android — 13 Oktober 2026

Pengukuran emulator baru selesai:5×startup/cache dan3×per scene tab/scroll. Kerja Main/cache/bitmap berkurang; warm startup dan scroll belum membaik konsisten. Laporan menyimpan median/rentang serta batas instrumentation; tidak ada klaim HP owner.

| Temuan | Kondisi setelah perubahan | Batas bukti |
|---|---|---|
| Startup cache/Keystore di UI | Restore async dengan state statis dan epoch; getter sesi memori | Source, StrictMode constructor dan debug regression; bukan semua CPU ringan |
| JSON/mapping pada dispatcher pemanggil | IO untuk blocking/setup/JSON cache bounded; Default untuk parsing API/mapping DTO | Source dan regression cancellation/expiry/401 |
| Dashboard menulis snapshot penuh | AtomicFile per-resource, total2MiB termasuk staging, schema2 | Regression budget/recovery/oversize/write failure; pengukuran terpisah |
| Bitmap density/decode UI | Nodpi+sampled IO decode, kepemilikan per composable | Emulator decoded dimensions/allocation, render lokal; HP owner belum diuji |
| History tumbuh/worker eager render/tren sort ulang | 4×25 live row+2 server cursor; lazy list; memoization | 24 halaman, previous/filter race,500 pekerja lazy,3 halaman API; eager roster tetap tradeoff |
| Release tanpa R8 | Minify/resource shrinking release/profile | Build PASS, profile correctness/smoke/rendering22 PASS; ukuran APK bukan RAM |
| Blur/fallback | Desain dipertahankan; fallback tanpa source/effect | Sampel emulator sepadan di laporan; bukan bukti penyebab utama jank atau HP |

Unit8 dan debug48 PASS. Gates fisik/API26/OEM/owner/pentest/reviewer/SOP tidak dinaikkan DONE. [ANDROID_PERFORMANCE](../implementation/ANDROID_PERFORMANCE.md), [VERIFICATION](../implementation/VERIFICATION.md).

## Delta gap Liquid Glass — 12 Oktober 2026

Kesenjangan sembilan konsep vs UI lama ditangani melalui hero forest, sensor mint, journal dengan semua reasons, hub/detail form, selector/footer/nav blur nyata. Unit8 dan UI/state27 on/off + final13 PASS pada emulatorAPI37; sembilan reference-versus-runtime dibandingkan tanpa phone-frame/status palsu. Logo existing, aset foto/vector/Roboto/system bars berbeda sedikit dari raster referensi; spacing/scroll mengikuti layar/font aktual. Fallback diperiksa melalui branch disabled pada API37; runtime API26–32 belum tersedia. Performance emulator dilaporkan terpisah, bukan klaim fluiditas HP greenhouse. Backend/firmware/React/kontrak serta produksi kosong tetap; gates fisik/pentest/reviewer/SOP/process-death acceptance tetap terbuka. [VERIFICATION](../implementation/VERIFICATION.md).

## Delta gap UI — 11 Oktober 2026

Metrik dashboard yang sebelumnya satu kolom kini adaptif 2×2/satu kolom; fase/koneksi/fetch dipusatkan dalam hero, capture/quality dekat bacaan. Simbol teks nav diganti ikon vektor; warna/font/shape/spacing/motion terpusat. Riwayat menjaga semua reasons sambil mengurangi dominasi metadata. Filter disclosable, form/shift berkelompok, helper/error/keyboard dan save-server vs applied-device lebih jelas.

Run gabungan UI/state20 dan unit8 PASS; run tambahan animation-off13 PASS. Screenshot nyata diperiksa; saat iterasi, word split nav, minimum-width harness, keyboard insets, selector landscape dan sinkronisasi input/recreation diperbaiki. Laporan [UI_REDESIGN](../implementation/UI_REDESIGN.md) memisahkan sumber, render, fixture, run gagal dan evidence akhir. Gate fisik/performance/ucapan TalkBack/SOP/pentest/reviewer tetap terbuka. Tidak ada perubahan backend/firmware/React/domain/data-layer atau values produksi.

## Penutupan gap audit T1–T7 dan batas terbaru

T1 guard efektif pre-mutation, T2 row validation/editor ACK, T3 parsed transport/fair queue/network worker, T4 resource/action/lifecycle/export, T5 trusted reading vs latest receipt/freshness, T6 lazy list/static loading/font/semantics, dan T7 encrypted scoped read cache telah diimplementasikan lokal dan diuji sesuai [VERIFICATION](../implementation/VERIFICATION.md). Tidak mengklaim template/review source sebagai runtime test.

Coverage terbaru: backend18/247 + isolated guard4/12, Android unit8, UI/state14 dan realHTTP1 pada run terpisah, targeted animation-off1, firmware native8 dan dua ESP32 compile. TalkBack service bound/tree smoke terbukti, full spoken walkthrough masih terbuka. List500 memeriksa lazy composition, bukan frame profiling. Least-privilege test account hanya fresh-provision code/parser verified; existing account tidak diubah. Hardware/SD power-loss/TLS greenhouse/process-death/soak/pentest/reviewer/SOP/Q-10 tetap OPEN atau NOT_VERIFIABLE sesuai gate. Git branch kerja ada; belum commit/push/deploy dan staging175 dipertahankan.

> **Delta implementasi 10 Oktober 2026:** gap audit “tidak ada native/backend/database/API/firmware” di bagian lama kini memiliki implementasi lokal nyata: `android/`, `backend/`, `firmware/`, migrations MySQL/API/test/build. React dipertahankan. Bukti [VERIFICATION](../implementation/VERIFICATION.md), kontrak [API](../implementation/API.md), progress [IMPLEMENTATION_PLAN](IMPLEMENTATION_PLAN.md). Audit historis/hash baseline tidak dihapus dan tidak menggambarkan source terbaru.

## Coverage dan gap terkini

- KF-06–09 data/history/validation/monitoring owner dan DER-FR-012 pekerja terjadwal mempunyai code Android/API/MySQL serta regression/integration evidence. Bukan lagi Kotlin template. Missing config/sensor/clock tetap incomplete; mismatch input terjaga dan semua reasons tampil.
- KF perangkat monitoring/input/pengingat memiliki adapter/menu/queue/ACK firmware compiled, tetapi physical accuracy, calibration, RTC/SD power-loss, LCD/buttons/LED/buzzer dan greenhouse network NOT_VERIFIABLE. Tidak menyamakan native unit test/HTTP fixture dengan pengujian hardware.
- KNF modularity/input/auth/access/pagination/env memiliki software checks. Pentest/long-run/reviewer/bukti GitLab/submission dan applicability SOP multi-platform tetap OPEN; native Android tidak membuktikan Windows/macOS/iOS compliance.
- Q-10 correction/full CRUD record historis tetap terbuka. Worker/shift disable/edit menjaga versi/snapshot; ini tidak dianggap keputusan untuk menghapus/mengoreksi riwayat nutrisi.
- Production configuration/roster/fase/thresholds kosong. Fixture testing terpisah; angka test tidak menjadi angka agronomis disahkan. Tidak ada dosing/TDS tambahan, PPM Android/worker login atau input harian greenhouse future scope.
- APK debug/local service/build firmware adalah deliverable lokal, bukan release/deployment/handover final. Tidak ada commit/push/deploy.

Audit 10 Oktober 2026, cakupan workspace lokal. Fakta: React/TypeScript prototype, tujuh Kotlin/Gradle strings; tidak ada native app, backend/database/firmware atau metadata Git. Pengguna mengonfirmasi tidak ada proyek native lain. Bukti source menggunakan path/simbol/line pada snapshot audit; line dapat berubah pada pekerjaan berikutnya. Ketiadaan komponen di workspace tidak membuktikan ketiadaan pada semua repository tim.

Update keputusan pengguna 10 Oktober 2026: database **MySQL** dikonfirmasi. Ini menutup ketidakpastian pilihan engine saja (Q-11 sebagian); KF-08/F-004 dan kesiapan schema/API tetap MISSING pada workspace. Tidak ada koneksi, migration atau pengujian MySQL dilakukan pada pembaruan dokumentasi ini. Pernyataan enam Markdown baru dan hash baseline pada §8 merujuk audit awal, bukan perubahan dokumentasi lanjutan ini.

## 1. Pemeriksaan sumber dan batas verifikasi

Update scope pengguna 10 Oktober 2026: Android native Kotlin saja; React referensi tampilan. Scope direncanakan Android + backend MySQL + firmware ESP32 dan API/kontrak integrasi. Input PPM hanya ESP32 tahap sekarang; input harian greenhouse belum termasuk implementasi. F-018/Q-03 telah mendapat keputusan produk untuk media input, tetapi aturan angka/akses belum lengkap; F-019/Q-01 telah mendapat keputusan target Android-only, tetapi penerapan SOP multi-platform belum terbukti. Tidak ada status implementasi/compliance dinaikkan dari keputusan ini.

P/G/S berarti sumber lengkap pada [PRD §1](PRD.md). Proposal: teks OOXML seluruh 440 body blocks termasuk tabel/SDT, media 19, 17 gambar desain/timeline langsung diperiksa, header/footer/footnote/endnote. Lambang dua halaman awal bukan diagram requirement. Tidak ada materi lampiran substantif. Layout halaman DOCX tidak dirender karena bundled LibreOffice tidak tersedia; page-index dari daftar isi tidak dianggap verified. Seluruh 4 halaman G dan 3 halaman S dirender dan dilihat, termasuk activity diagrams.

Seluruh file `src/`, entry/config/manifest package, README, metadata, gitignore dan inventaris hidden diperiksa. Safe `git status`, `git log`, `git remote -v` semua gagal karena bukan Git repository. Tidak ada remote/branch/history/kontribusi yang dapat dinilai. Tidak ada Kotlin/Gradle/AndroidManifest, backend/SQL/ESP32/CI/test file ditemukan. Dependencies belum terpasang dan tidak ada lockfile; tidak dilakukan npm install, build aplikasi, Gradle test, runtime browser/emulator, pentest atau uji hardware. Audit static disertai probe fungsi validasi terisolasi; bukan klaim end-to-end.

## 2. Requirement traceability matrix

Priority: P0 blocker; P1 essential; P2 improvement penting; P3 optional. Status mengikuti PRD §4. Kolom Kotlin menyatakan **template/reference**, karena native file belum ada.

| ID | Requirement/source | Komponen penanggung jawab | Code/template evidence | Status | Gap dan aksi | Priority/task |
|---|---|---|---|---|---|---|
| KF-01 | Suhu air; P Tabel 2.3/§1.3 | ESP32 DS18B20 → LCD/API/owner | App.tsx:37–44,79–86; DashboardScreen.tsx; template Dashboard | MISSING | Display MOCKED; buat producer terkalibrasi, data aktual | P1 TASK-011/017 |
| KF-02 | Suhu/kelembapan ruangan; P Tabel 2.3 | ESP32 DHT22 | App.tsx fixture/random; template Dashboard | MISSING | Tidak ada sensor I/O; jangan menganggap dua kartu implementasi sensor | P1 TASK-011/017 |
| KF-03 | pH; P Tabel 2.3/§3.1 kalibrasi | ESP32 PH-4502C | App.tsx fixture/random; Dashboard | MISSING | Kalibrasi dan timestamp producer belum ada | P1 TASK-011/017 |
| KF-04 | Sensor di LCD; P Tabel 2.3 | Firmware/LCD20x4 | Tidak ada firmware; template hanya Compose | MISSING | UI web/native bukan LCD | P1 TASK-012 |
| KF-05 | Pengingat; P Tabel 2.3/Gambar2.6 | RTC/firmware/LCD/buzzer | NotificationsModal.tsx fixture; SettingsScreen | MISSING | Tidak ada scheduler/alarm hardware | P1 TASK-013/021 |
| KF-06 | Input fase; P Tabel 2.3/Gambar2.6 | Worker/device/backend | ManualEntryModal.tsx:23; SettingsScreen.tsx:18/36 | PARTIALLY_IMPLEMENTED | Form lokal ada; alat/backend MISSING; fase editor conflated | P1 TASK-014/006 |
| KF-07 | PPM manual; P Tabel 2.3/Gambar2.6 | Handheld/worker/device | ManualEntryModal.tsx:32–55; template validator | PARTIALLY_IMPLEMENTED | Input browser ada; konfirmasi device/provenance tidak ada | P1 TASK-014/015 |
| KF-08 | Database; P Tabel 2.3/ERD | Backend/DB | App.tsx:17–34,58–74 localStorage | MISSING | Browser JSON bukan terpusat; schema/API belum ada | P0 TASK-007/015/016 |
| KF-09 | Riwayat; P Tabel 2.3/Gambar2.11/§3.1 | Owner dashboard/backend | HistoryScreen.tsx list/filter; template MainActivity emptyList | PARTIALLY_IMPLEMENTED | Histori lokal/fixtures; date range/paging/remote/native hilang | P1 TASK-018/019 |
| KF-10 | Validasi jadwal/PPM; P Tabel 2.3/§1.3/Gambar2.8 | Backend validator | validation.ts:93–156; kotlinTemplates.ts:1281+ | INCORRECT | PPM precedes late; missing states/sensor decision; NaN valid; rules Q-02 | P0 TASK-003/009/020 |
| KNF-01 | Mudah digunakan pekerja; P Tabel2.4 | Hardware UX | Tidak ada LCD UX test | NOT_VERIFIABLE | SUS/field test belum tersedia | P1 TASK-023 |
| KNF-02 | Informasi dipahami; P Tabel2.4 | Owner/LCD UI | Labels/cards React | UI_ONLY | Static label ada; misleading empty valid; user evaluation belum | P1 TASK-017/023 |
| KNF-03 | WiFi; P Tabel2.4 | ESP32 network | Toggle status Dashboard:26–30 | NOT_VERIFIABLE | Toggle MOCKED; field WiFi test dibutuhkan | P1 TASK-015/022 |
| KNF-04 | Akurat RTC; P Tabel2.4 | RTC/time model | ManualEntryModal:35–44 Date.now/timeStr | MISSING | Tidak ada RTC; selected time beda epoch | P1 TASK-010/013 |
| KNF-05 | Box panel; P Tabel2.4/Tabel3.2 | Hardware | Proposal desain saja | NOT_VERIFIABLE | Inspeksi casing/ketahanan fisik | P1 TASK-011/023 |
| KNF-06 | Data monitoring tersimpan/diakses; P Tabel2.4 | Device/DB/dashboard | App logs/settings localStorage, sensors memory | PARTIALLY_IMPLEMENTED | Persist lokal terbatas; sensor history tak disimpan | P1 TASK-016/017/022 |

Ringkasan sepuluh KF: 0 IMPLEMENTED; 3 PARTIALLY_IMPLEMENTED; 6 MISSING; 1 INCORRECT. “MISSING” pada KF sensor membahas pembacaan sistem, sedangkan representasi UI-nya MOCKED. Status KNF hardware NOT_VERIFIABLE tidak sama dengan NON_COMPLIANT akademik. Traceability semua DER/NFR/REC terdapat PRD §7 dan tasks di IMPLEMENTATION_PLAN; tidak ada requirement di luar matriks yang dianggap disahkan hanya karena UI ada.

## 3. Audit setiap layar dan komponen UI

| Layar/file | Purpose/role/entry dan actions | State/data/validation | Loading/error/empty | Kelengkapan dan requirement |
|---|---|---|---|---|
| DashboardScreen.tsx | Owner, tab beranda; refresh, toggle status, menu input/history | App sensors/settings.activePhase/logs[0]; local menu/iotStatus; four cards/last activity | Spinner refresh simulasi; tanpa telemetry error; null log fallback valid | MOCKED telemetry/device; local navigation functional secara source; KF-01–03/09/10, DER001/004; timestamp tidak tampil |
| SensorChart.tsx | Section dashboard; pilih suhu/pH/keduanya dan tooltip | Local metric/hover; 5 arrays/time labels dan SVG paths statis | Tidak ada remote loading/error/empty | MOCKED; DER003; tidak terhubung refresh, tidak grafik PPM/room/humidity |
| HistoryScreen.tsx | Owner tab riwayat; filter chips/status/search, reset, export, +Catat PPM | Props logs; memo local filter; counters seluruh logs; dateStr substring bukan date range | Empty filter ada; tidak ada fetch/loading/error/paging | PARTIALLY_IMPLEMENTED lokal; KF-09; export seluruh logs bukan filteredLogs |
| SettingsScreen.tsx | Owner tab pengaturan; jam/toleransi, fase editor/PPM, ambang suhu/pH, save | Initial props → local state; HTML number min/max, handleSave tidak cross-check PPM min/max atau finite; dekoratif range track/thumb | Toast timer tanpa storage/API ACK; tidak ada load/save error | PARTIALLY_IMPLEMENTED lokal; DER002; editor phase mengubah activePhase; ambang room/humidity ERD belum ada |
| ManualEntryModal.tsx | Modal dari Dashboard/History; actor browser operator bebas; input waktu/fase/PPM/nama/catatan, save/cancel | defaults waktu/PPM; initial activePhase; live TS validator; HTML required/range/step | Direct submit tanpa confirmation summary; tak ada API/loading/error | PARTIALLY_IMPLEMENTED browser; KF-06/07/10; input Android konflik wireframe vs device flow |
| NotificationsModal.tsx | Bell TopAppBar; mark all read/close | 3 hardcoded notifications; parent unread initially2 dan clear0; read state tidak persist | Tidak ada fetch/error/empty | MOCKED; DER010/KF-05 belum schedule engine |
| KotlinCodeViewer.tsx | Developer tooling dari header/banner/FAB; pilih template, copy/download | selected KOTLIN_CODE_FILES, local copied | writeText promise tidak awaited/catch; sukses clipboard prematur | Tool prototipe; tidak required owner screen; klaim “100% siap pakai” unsupported |
| TopAppBar.tsx | Global header; bell/code/frame toggle | callbacks/unread parent; icons/brand | Tidak ada data access | UI_ONLY wrapper; bell memakai fixture |
| BottomNavBar.tsx | Global tabs beranda/riwayat/pengaturan | currentTab/callback App; fixed viewport bottom | No platform back-stack | UI_ONLY navigation shell; viewport anchoring potensi mismatch frame, belum runtime verified |
| App.tsx | Root frame/banner/mobile toolbar, modal composition | useState/effect localStorage; random refresh; direct CSV | JSON parse catch fallback fixture; writes console.error | Sebagian interaction lokal ada; no session/API/repository; bukan native |
| Login | Owner auth P §3.1 dan SOP | Tidak ada | Tidak ada | MISSING; DER005 |
| Device LCD menus | Pekerja hardware | Tidak ada firmware/menu implementation | Tidak ada | MISSING; KF-04–07/DER007 |

Tidak ada layar produk yang dapat diberi label Fully functional end-to-end. Layar native semuanya MISSING sebagai physical modules; template UI_ONLY/MOCKED. Template dashboard: hardcoded seluruh parameter/fase/status, callback history tidak dipakai button; template history: list supplied empty, callbacks no-op, model missing; template settings: remember local, tombol save callback tanpa payload, phase toggle reset angka; MainActivity: bell no-op dan currentRoute terpisah back stack; Theme: color scheme statis, tidak ada data functionality. Native model/logic validator tidak terhubung screen/repository.

### Perbandingan wireframe dan flow

Gambar2.10–12 menyediakan tiga tab/empat kartu/latest manual record/history badges/settings. React mempertahankan struktur, unit, phase labels dan sebagian actions. Brand/gaya/chart tambahan tidak otomatis defect. Perbedaan fungsi yang penting: device status palsu; last-record empty valid; grafik statis; date-range filter belum; setting tidak mencapai perangkat; room/humidity threshold diagram belum; phase semantics berubah. Export filter scope belum specified. Wireframe “Tambah Nutrisi” mendukung presence tombol tetapi berbenturan alat-only worker flow, sehingga classify **conflict Q-03**, bukan unnecessary confirmed. Kotlin viewer/device-frame/Android statusbar simulasi merupakan tooling tambahan di luar owner use case; jangan port sebagai fitur produk tanpa alasan.

## 4. Register temuan teknis dan konflik

Confirmed = source/diagram/probe mendukung; risk = perlu runtime/additional evidence. Prioritas bukan kuantifikasi security exploit.

| ID | Bukti path/rujukan | Finding/implication | Severity | Aksi/requirement |
|---|---|---|---|---|
| F-001 | package.json/main.tsx; tidak ada app/Gradle/.kt | Workspace bukan native Android; blocker semua Android checks | P0 confirmed | Q-01 TASK-001/005; semua KF owner |
| F-002 | kotlinTemplates.ts:554/1287 model imports; BorderStroke usages tanpa import | Template missing model/catalog/imports; klaim ready-to-use tidak didukung | P0 confirmed incompleteness; compile belum dijalankan | TASK-005/006; jangan blind copy |
| F-003 | App.tsx:37–44,79–86; Dashboard:26–30; SensorChart:14–46 | Telemetry random, device status toggle, chart statis; tidak ada network calls | P0 confirmed integration gap | KF-01–03/08; DER003/004; TASK-015–019 |
| F-004 | seluruh workspace tidak ada backend/auth/API/SQL/firmware; dependencies saja | Tidak ada data terpusat/authority/access trust | P0 confirmed workspace gap | KF-08/DER005; TASK-007/008 |
| F-005 | validation.ts:117–149 vs P Gambar2.8 | Low/high sebelum late berbeda urutan flowchart; sensor omitted | P0 confirmed discrepancy; final semantics Q-02 | KF-10/DER009; TASK-003/009 |
| F-006 | P §1.3 vs Gambar2.2/2.4/2.5/2.8 | Batas jadwal/fase/PPM vs sensor validation/abnormal; belum terselesaikan | P0 unresolved source conflict | Q-02, TASK-003; jangan pilih diam-diam |
| F-007 | validation.ts validateNutrientInput; isolated probe NaN/time malformed | NaN PPM dapat status valid; empty time/invalid schedule jadi late bukan malformed; waktu awal luar toleransi berlabel late | P1 confirmed function behavior; UI exploit belum diverifikasi | DER-NFR001/KF-10 TASK-009 |
| F-008 | ManualEntryModal:35–44; App:43/80–86 | timeStr memakai input jam, epoch memakai saat submit; “WIB” tanpa timezone konversi; sensor refresh timestamp tetap | P1 confirmed | KNF-04/REC-004 TASK-010/017 |
| F-009 | SettingsScreen:18,36,149/160; ManualEntryModal:23 | Fase editor rentang menjadi fase aktif kebun; modal initial phase tidak sync prop; retained draft | P1 confirmed behavior, intended semantics Q-04 | KF-06/DER002 TASK-003/006/021 |
| F-010 | SettingsScreen handleSave + form PPM inputs | Min PPM boleh melebihi max; Number(empty)=0; tidak ada explicit finite/cross-field rule; jam settings tidak required | P1 confirmed omission; runtime constraints terbatas | DER-NFR001 TASK-021 |
| F-011 | App.tsx:17–34,58–74; Dashboard fields .toFixed; logs.filter History | JSON valid tapi shape salah diterima; []/null/object malformed bisa merusak rendering; storage failure hanya console | P1 confirmed unvalidated parsing; crash risk belum UI tested | KNF-06/REC-006 TASK-006/017 |
| F-012 | Dashboard:224/235/243/256–260 | Tanpa record menampilkan 850/08:05/Valid; badge green walau status abnormal | P1 confirmed | DER001/KNF-02 TASK-017/020 |
| F-013 | App:112; INITIAL_LOGS tanggal 11/12 Okt vs audit10 Okt | logs[0] dianggap latest tanpa sort; seed bertanggal masa depan digunakan sebagai default | P1 confirmed prototype artifacts | KF-09/DER001 TASK-017/018 |
| F-014 | App:95–109 CSV; History onExportCsv callback tanpa filters | CSV quotes/newlines tidak escaped; formula-leading user text diteruskan; export semua logs; Blob URL tidak revoked | P2 confirmed serializer omission; spreadsheet execution risk, belum export runtime tested | KF-09/Q-11 TASK-019 |
| F-015 | NotificationsModal notificationList; App unread2/clear0 | Notifikasi/read count fixture, tak sinkron logs/settings/status | P1 confirmed MOCKED | KF-05/DER010 TASK-021 |
| F-016 | kotlinTemplates MainActivity emptyList/no-op; Settings save/no payload | Template navigation UI tidak punya data persistence/handlers; model import belum | P1 confirmed | KF-09/DER002 TASK-005/017–021 |
| F-017 | index.html:21 entry `/src/main.tsx`; vite.config.ts tanpa base | Subdirectory readiness belum dibuktikan; root entry/current config risk untuk S §3 | P2 risk; bukan deployment defect confirmed | DER-NFR003 TASK-005/023 setelah Q-01 |
| F-018 | Gambar2.10 vs §1.3/Gambar2.6 | Android/dashboard add nutrient vs alat worker input | P0 unresolved scope conflict | Q-03 TASK-003 |
| F-019 | S h.1 §2 vs U Android dan P Gantt web/mobile | Platform minimum SOP belum terjawab oleh native-only | P0 unresolved applicability | Q-01 TASK-001 |
| F-020 | P Gambar2.9 buzzer GPIO33 vs §3.1 GPIO27; skematik GPIO label/bus duplikat; Gambar2.14 DHT22/SHT31 | Wiring/sensor model tidak konsisten; image bukan verified electrical design | P0 source discrepancy; listrik fisik belum diperiksa | Q-08/09 TASK-004/011 |
| F-021 | P §3.1 GitHub vs G GitLab UMY; Git read commands gagal | Repo authoritative source conflict; kontribusi/branch/remote tak dapat dinilai | P0 academic planning | Q-14 TASK-002 |
| F-022 | S §5 full CRUD vs P worker correction-before-confirm | Entitas/hak edit/delete historis belum dipilih; UI logs hanya create/read | P1 source gap, Q-10 | DER011 TASK-004/024 |
| F-023 | .gitignore env patterns; source inspected tanpa actual credential | Tidak ditemukan secret literal aktif; tidak ada Git history/deployment/auth untuk memastikan keamanan | P1 NOT_VERIFIABLE keseluruhan | DER-NFR001 TASK-008/025 |
| F-024 | KotlinCodeViewer:20–23 clipboard promise; App/Settings setTimeout | Copy success bisa sebelum promise selesai; timer cleanup absent; small resource leak Blob URL | P2 confirmed omission; failure runtime risk | Tooling, bukan KF mandatory; optional setelah core |

### Temuan dari semua gambar proposal

| Gambar/media | Hubungan/transisi yang dibaca langsung | Implikasi/konsistensi |
|---|---|---|
| 2.1 image3 | Worker input record includes phase, PPM, save; owner dashboard includes sensor/history/status serta setting jadwal/parameter | Memisahkan responsibility; tidak menyatakan Android input worker |
| 2.2 image4 | Sensor/LCD/DB, branch “PPM perlu nutrisi manual?”, worker memberi/mengukur/confirm, sistem memeriksa waktu/PPM/sensor, owner evaluasi | Branch berbasis PPM bertentangan schedule trigger flow; automatic PPM tidak tersedia. Q-02/05; jangan tambah sensor |
| 2.3 image5 | Sensor → ESP32 → DB; cek jadwal dari DB; confirm worker → DB ACK → validasi ambil parameter → DB → owner dashboard | Diagram pesan backend diperlukan, belum deployed; urutan send validator narasi vs arrow tidak menentukan transaction protocol |
| 2.4 image6 | Pengguna→pekerja/pengelola; schedule→record→validation; validation→parameter/sensor; device aggregates sensor; notification/recap | Enam enum status termasuk pending/sensor abnormal; class tidak sama dengan model TS |
| 2.5 image7 | 11 entity relasional; users schedule/parameter/record; device sensor/data/backup/recap; record references sensor/schedule/user; validation references record/parameter | Relasi/field digambar belum DDL; nullability/index/delete policy tidak ada; class one-to-many validation vs ERD perlu keputusan |
| 2.6 image8 | Jadwal alarm → nutrisi fisik → tunggu tercampur → handheld → pilih fase/input → review → correct/cancel/confirm → save/send | Correction sebelum commit jelas; save ACK/replay belum specified |
| 2.7 image9 | Device/latest sensor/jadwal → history/status/PPM → grafik sensor/PPM → evaluasi → ubah jadwal/PPM/sensor → save | Grafik PPM dan remote apply config belum di prototype |
| 2.8 image10 | Init WiFi+RTC fallback, sensor normal/abnormal, schedule alarm, input/pending, confirm, validity decision late→low→high→sensor | Prioritas code mismatch; sensor vs boundaries unresolved; boot fetch config sebelum offline branch perlu cache fallback |
| 2.9 image11 | 5V/filter caps, ESP32, DS18B20/DHT22 pull-up, pH analog34, I2C21/22, buttons, buzzer33, LEDs, MicroSD SPI | Label/simbol GPIO/I2C/SPI ambigu/duplikat, power connections tidak lengkap; jangan menghasilkan pin mapping final dari gambar |
| 2.10 image12 | Owner dashboard phase/status, 4 sensors, latest manual record, add action, bottom tabs | Add scope conflict; angka ilustrasi bukan sensor/threshold approved |
| 2.11 image13 | History filter/export/counts/date/manualPPM/phase/status | Filter date narasi, export detail belum; UI card variation tidak otomatis defect |
| 2.12 image14 | Pagi/sore/tolerance, phase PPM editor, suhu/pH bounds, save | Numeric examples hanya desain; threshold room/humidity Class/ERD belum wireframe |
| 2.13 image15 | Physical wiring LCD/RTC/sensors/buttons/buzzer | Button UP/DOWN berbeda LEFT/RIGHT; MicroSD/LED tidak terlihat; pin detail belum authoritative |
| 2.14 image16 | Tandon sensor → ESP32/LCD/WiFi/cloud/mobile; panel buttons/LED/buzzer | DHT22/SHT31 label bertentangan scope DHT22; diagram tidak menunjukkan TDS atau dosing |
| 3.1 image17 | Analisis → desain → hardware → integrasi → test → laporan | Tahap software dalam narasi tidak menjadi kotak tersendiri; rencana harus memasukkan software sebelum integrasi |
| 3.2 image18 | Capstone1 April–Juli2026, penanggung jawab, milestone4Juli/6–11Juli | Rencana proposal, bukan bukti completion/deadline resmi SOP |
| 3.3 image19 | Capstone2 Okt2026–Jan2027; web+mobile, backend/IoT, integration/test/report, sidang minggu4Jan | Mendukung Q-01 tentang kedua frontend; jadwal perlu konfirmasi aktual |

## 5. IoT integration readiness

Keputusan pengguna lanjutan 10 Oktober 2026 memperjelas consumer Android owner/leader-only dan producer input pekerja IoT-only, satu ESP32/satu tandon tahap awal. Empat parameter sensor dipertahankan; jumlah modul fisik/model belum diverifikasi. Assignment pekerja melalui jadwal hari/shift diinginkan, belum menjadi implementasi/verified author. Validasi B gabungan dipilih; detail rule masih Q-02. Laravel/PHP telah disetujui tetapi tidak menutup gap implementasi backend. Tidak ada status hardware/security/integration dinaikkan dari keputusan ini.

| Kemampuan | Required/proposed | Existing | Gap/action |
|---|---|---|---|
| ESP32 telemetry empat nilai | KF-01–03/08 | MOCKED display | Producer/API/schema/time/quality TASK-011/015/016 |
| Device status/timestamp/freshness | DER004; cutoff REC/TBD | Toggle/string tetap | Device ID/last contact, cutoff Q-11 TASK-016/017 |
| Nutrient record/manualPPM/phase | KF-06–09 | Browser form/local logs | Device confirmation/provenance/backend TASK-014/015 |
| Validation results | KF-10/DER009 | Client incomplete/conflicting | Rules signed; backend authority TASK-003/009/020 |
| Historical trends | DER003/KNF-06 | Static chart; logs browser | Actual telemetry and PPM history TASK-018/019 |
| Owner config/schedules | DER002/KF-05 | Local settings | Authorized persist/device apply TASK-021 |
| Device disconnected/MicroSD/RTC | DER006/KNF-04 | No device code | Local persistence/replay/time TASK-010/013/015/022 |
| Android cache/sync background | REC-001 | None | Optional; jangan jadikan mandatory offline writes |

## 6. SOP compliance matrix

COMPLIANT menilai bukti lokal hanya; PARTIALLY_COMPLIANT sebagian tindakan; NON_COMPLIANT hanya pelanggaran yang dapat didemonstrasikan dalam komponen; NOT_VERIFIABLE tidak cukup evidence remote/runtime/hardware; NOT_APPLICABLE bila fitur/lingkungan tidak ada dalam cakupan. Tidak ada quota contribution/format folder submission yang diciptakan.

| ID | Sumber/page/section | Aturan dan applicability | Status | Evidence | Missing action | Priority |
|---|---|---|---|---|---|---|
| SOP-G-01 | G h.1 §1–2 | GitLab UMY remote, akun kampus/access, SSH/token dan jaringan kampus | NOT_VERIFIABLE | Tidak ada .git/remote/account/network evidence | Konfirmasi Q-14 dan akses; TASK-002 | P0 |
| SOP-G-02 | G h.1 §3/4.2, h.3 §6 | Branch kerja setiap fitur/bug/config; main/master stable | NOT_VERIFIABLE | Git commands not repository | Repo/branch workflow TASK-002; jangan init saat audit | P0 |
| SOP-G-03 | G h.1 §3; h.2 §4.3; h.3 §6 | Uji/build/run lokal sebelum commit/push | NOT_VERIFIABLE | Tidak ada history/hasil build; probe bukan aplikasi | Build/test dan evidence setelah setup TASK-005/023 | P1 |
| SOP-G-04 | G h.2 §4.4–4.5 | Status/diff/stage hanya relevant; exclude env/credential/log/artifacts | PARTIALLY_COMPLIANT | .gitignore mengecualikan env/build/log; tracked history tidak ada | Periksa tracked staged changes di repo aktual TASK-002 | P1 |
| SOP-G-05 | G h.2 §4.6; h.3 §6 | Commit ringkas/deskriptif/konsisten; conventional format optional | NOT_VERIFIABLE | Tidak ada log | Tentukan pesan untuk task approved; TASK-002 | P2 |
| SOP-G-06 | G h.1 §3; h.2 §4.7; h.3 §8 | Sinkronisasi remote, resolve branch kerja, ulang validasi konflik | NOT_VERIFIABLE | Tidak ada remote/history | Bukti sync/validation dalam workflow TASK-002 | P1 |
| SOP-G-07 | G h.2 §4.8 | Push setelah commit/tests dan verifikasi branch/commit remote | NOT_VERIFIABLE | Tidak ada remote atau push authorization | Verifikasi setelah izin commit/push; TASK-002/026 | P1 |
| SOP-G-08 | G h.2 §4.9; h.4 diagram | MR jika workflow mewajibkan; ringkasan/alasan/dampak | NOT_VERIFIABLE | Kebijakan MR belum diberikan | Q-14 dan MR evidence bila applicable; TASK-002 | P2 |
| SOP-G-09 | G h.3 §8 | Jika menggunakan CI/CD, pipeline setelah push | NOT_APPLICABLE | Tidak ditemukan CI lokal; remote belum diketahui | Reassess bila CI tim digunakan; TASK-002/026 | P2 |
| SOP-G-10 | G h.3 §6–7 | Secrets tidak hardcoded/file sensitive tidak dipublish; traceable history | NOT_VERIFIABLE | No active literal credential ditemukan; Git publication/history tak ada | Secret/history review tanpa menyalin value; TASK-025/026 | P1 |
| SOP-G-11 | G h.2–3 §5 | Line ending/permission sesuai platform; aturan WSL jika digunakan | NOT_VERIFIABLE | Windows PowerShell; config Git/WSL tidak ada | Review repo line-ending policy; WSL guidance hanya jika applicable TASK-002 | P2 |
| SOP-S-01 | S h.1 §1 | Pentest wajib sebelum layak/handover | NOT_VERIFIABLE | Tidak ada laporan pentest | Scope izin, lakukan remediation/retest TASK-025 | P1 |
| SOP-S-02 | S h.1 §1 | Auth mitigasi brute force/injection/serangan relevan | NOT_VERIFIABLE | Auth/backend tidak ada di workspace | Implement auth controls, uji akses TASK-008/025 | P0 |
| SOP-S-03 | S h.1 §1; h.2 §6 | Form frontend+backend validation termasuk masking/anti-injection | PARTIALLY_COMPLIANT | HTML constraints ada; cross-field dan backend tidak tersedia | Validate typed input server; parameterized queries; masking bukan pengganti validation TASK-009/021/025 | P1 |
| SOP-S-04 | S h.1 §1 | Upload extension eksplisit kedua sisi; max ukuran dan jumlah | NOT_APPLICABLE | Tidak ada fitur upload | Reassess jika upload diperlukan; jangan tambah upload hanya demi SOP | P2 |
| SOP-S-05 | S h.1 §1; h.2 §6 | Sensitive files tidak direct URL; validated access layer | NOT_VERIFIABLE | Tidak ada file service/deployment | Review deployment/access control TASK-025 | P1 |
| SOP-S-06 | S h.1 §2 | Responsive desktop/laptop, smartphone/tablet; Windows/macOS/Android/iOS | NOT_VERIFIABLE | CSS responsive static; native belum; tidak runtime platform test | Q-01 lalu matrix target test TASK-001/023 | P0 |
| SOP-S-07 | S h.1 §2 | Tidak overflow/cutoff/unreadable pada target | NOT_VERIFIABLE | Fixed viewport nav/mobile frame risk; belum runtime | Screenshot/test matrix setelah runtime tersedia TASK-023 | P1 |
| SOP-S-08 | S h.1 §2 | Loading/error/feedback jelas | PARTIALLY_COMPLIANT | Refresh spinner/toast/empty history ada; storage failure console | Honest save/error/empty/stale TASK-017/021/022 | P1 |
| SOP-S-09 | S h.1 §3 | Clean code/modular/DRY dan architecture clear/layers separated | PARTIALLY_COMPLIANT | Components/utils/types; App menggabungkan data access/commands/UI | Boundary native/repository dan review TASK-005/006 | P1 |
| SOP-S-10 | S h.1 §3 | No hardcoded URL/secret/credential/role/data sensitif | NOT_VERIFIABLE | Demo labels/operator names hardcoded; bukan established credential. No actual secrets ditemukan | Konfigurasi actual API/auth; jangan role trust dari client TASK-008/016/025 | P1 |
| SOP-S-11 | S h.1 §3 | Relative/configurable routes; web works subdirectory | NOT_VERIFIABLE | index.html root source path; Vite no base; deployment belum | Jika web retained, test build with approved base TASK-005/023 | P2 |
| SOP-S-12 | S h.2 §4 | API config terpusat, credential/sensitive parameter .env | NOT_VERIFIABLE | Tidak ada API call/service; env example bukan live service | Backend env, public base config; no secret in APK TASK-008/016 | P1 |
| SOP-S-13 | S h.2 §4 | Request/payload limit wajib; ~10KB recommended, besar mis10MB prohibited | NOT_VERIFIABLE | No request handling | Set limit berdasar approved contract dan test rejection TASK-007/016 | P1 |
| SOP-S-14 | S h.2 §5 | Full CRUD sistem | PARTIALLY_COMPLIANT | Create/read logs, update settings; edit/delete logs tidak ada; backend belum | Q-10 pilih entitas/rights/audit; TASK-004/024 | P1 |
| SOP-S-15 | S h.2 §5 | Schema structured valid relations business aligned | NOT_VERIFIABLE | ERD rancangan; tidak ada DDL/database | Q-11 implement reviewed schema TASK-007 | P1 |
| SOP-S-16 | S h.2 §5 | Efficient queries; no SELECT* tanpa pagination; bounded large reads; insert/update/delete perf | NOT_VERIFIABLE | Tidak ada query engine | Pagination/index/query review actual DB TASK-007/016/024 | P1 |
| SOP-S-17 | S h.2 §6 | No end-user secret exposure; password bukan plaintext; safe sensitive processing | NOT_VERIFIABLE | Class/ERD password string/VARCHAR bukan bukti plaintext implementation; auth belum | Server hash/token/access and deployment review TASK-008/025 | P1 |
| SOP-S-18 | S h.2 §7 | Minimal tests login/add/edit/delete/access | NOT_VERIFIABLE | Tidak ada test suite/report | SOP scenarios after approved CRUD/auth TASK-008/024/025 | P1 |
| SOP-S-19 | S h.2 §7 | Repeatable tests recommended; scripts/collection/seeder optional support | NOT_VERIFIABLE | lint typecheck only; isolated probe saat audit | REC-007 test harness/report TASK-023/026 | P2 |
| SOP-S-20 | S h.3 diagram | Siapkan project sesuai SOP → dokumentasi/berkas → submit platform ditentukan | NOT_VERIFIABLE | No submission receipt/package; docs konteks baru bukan full handover | Q-13 platform/format/date; TASK-026 | P1 |
| SOP-S-21 | S h.3 diagram | Reviewer periksa, revisi jika gagal, pernyataan lolos | NOT_VERIFIABLE | No reviewer result | Record review/remediation/resubmission TASK-026 | P1 |

Tidak ada status NON_COMPLIANT akademik yang dipaksakan dari hilangnya remote/hardware/laporan. Temuan perilaku source tetap confirmed dalam register F; compliance global memerlukan bukti deployment/test/reviewer. Upload tidak otomatis wajib ditambah. Git contribution evidence dibangun lewat history yang traceable; tidak ada persyaratan kuantitatif per anggota pada G.

## 7. Tindakan akademik dan keamanan

1. Konfirmasi scope multi-platform dan repo authoritative dengan pembimbing/tim (Q-01/14). G GitLab UMY govern academic use; catat proposal GitHub sebagai konflik, bukan izin mengabaikan SOP.
2. Pilih entitas/hak CRUD menjaga record integrity (Q-10), model identity perangkat/pekerja (Q-06), dan validation rule sign-off (Q-02).
3. Siapkan evidence test minimal dan pentest berizin sebelum handover. Jangan menguji layanan kampus atau publik tanpa scope/authorization.
4. Konfirmasi submission platform/format/tanggal resmi (Q-13). SOP tidak memberi detail deadline; Gantt proposal bukan pengumuman resmi.
5. Proposal keluaran laporan BAB I–V, manuals pekerja/owner, firmware/bin/API docs, skematik/wiring/source dan video greenhouse harus dibuktikan pada final delivery. Context pack ini tidak menggantikannya.

Security static: tidak ditemukan literal credential aktif/API key hardcoded dari file source yang dibaca. `.env.example` ditinjau dengan klasifikasi tanpa menyalin nilai credential. Tidak ada authenticated API atau stored password yang dapat diuji; insecure transport/deployed ACL/secret publication tidak dapat disimpulkan. LocalStorage dapat dimodifikasi client; status/record bukan audit evidence authoritative. CSV injection/escaping dan malformed local JSON adalah risiko data integrity yang didukung source, bukan hasil pentest.

## 8. Verification summary dan konsistensi

Update U 10 Oktober 2026: implementasi scope Android/backend/firmware + testing/debugging diizinkan untuk pengerjaan berikutnya. Q-04 memilih owner active phase + worker record phase dengan mismatch warning; config produksi kosong, owner mengisi, all validation reasons. Tidak ada implementasi/test baru pada turn penyusunan prompt, status gap belum dinaikkan. Catatan audit-only/approval setiap task terdahulu tidak menghalangi scope implementation yang kini diotorisasi. Masalah hardware/akademik yang belum ada bukti tetap NOT_VERIFIABLE pada gate terkait.

**Update keputusan lanjutan 10 Oktober 2026:** Laravel/PHP REST API + MySQL disetujui, validasi B gabungan dipilih tahap awal; Q-02 tetap terbuka untuk rules/threshold/snapshot. Permintaan jadwal pekerja dirancang PRD §14/DER-FR-012/TASK-027, status MISSING/TODO, bukan verified actor. F-006 kini mendapat pilihan produk B tetapi perbedaan sumber tetap dicatat dan implementasi belum ada. Empat screenshot Stitch ditinjau sebagai acuan UI; angka/kurva/count/jam/rentang contoh, bukan requirement produksi. Tidak ada build/API/DB/firmware/hardware test dilakukan pada update dokumentasi ini; tidak ada compliance/status implementasi dinaikkan. Catatan ketidakpastian stack/shift/bentuk validasi dalam baseline terdahulu dibaca bersama keputusan terbaru ini.

Probe terisolasi memakai bundled Node v24.19.0 dan TypeScript type stripping in-memory, membaca utility source tanpa mengubah aplikasi. Hasil: NaN PPM dengan jam jadwal → valid; jam kosong → late; jam awal di luar toleransi → late; late+low → low; jadwal kosong → late. Tidak membuktikan semua input tersebut dapat dikirim melalui browser HTML form. Import/model dan template inventory juga diperiksa static. Perbandingan SHA-256 sebelum/sesudah penulisan pack lulus: seluruh 26 file asli tidak berubah; hanya enam Markdown yang diminta ditambahkan. Tidak ada dependency installation, application build, emulator/hardware run, Git mutation, commit/push/deployment.

Cross-document checks: KF-01–10 dan KNF-01–06 lengkap pada PRD/matriks; DER/REC terpisah; task requirement IDs valid; files current vs proposed dibedakan; 11 ERD entities dipetakan DATA_CONTRACTS; Q unresolved tetap terbuka; contoh angka tidak jadi requirement produksi; Git optional MR/commit format tidak jadi mandatory. Status audit tidak boleh dinaikkan hanya karena dokumen selesai.
