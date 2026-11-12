# Master prompt: perbaikan tampilan login dan pengelola, arah "Canopy"

Status: **rencana implementasi**, disusun 11 November 2026 dan diperluas 12 November 2026 (Riwayat, Cek catatan, Keuangan, Kelola, sheet Catat, semua form input) untuk dijalankan **Claude Sonnet 5.5** di Claude Code. Penyusunan dokumen ini tidak mengubah runtime. Baseline penyusunan: branch `codex/worker-flow-docs`, commit `06aff0f`. Baca HEAD aktual saat mulai.

Pekerjaan ini **hanya frontend Android** (Kotlin + Jetpack Compose Material3). Tidak ada perubahan backend, kontrak API, database, atau aturan bisnis.

---

## 0. Cara memulai

Prompt pembuka ada di [`RUN_FRONTEND_CANOPY.md`](RUN_FRONTEND_CANOPY.md). Prompt itu memilih **antislop mode DURING** sebagai session override.

---

## 1. Tugas

Kamu melanjutkan aplikasi Android **Inofarm**: pencatatan operasional dan evaluasi keuangan greenhouse melon hidroponik. Role wire `worker` (pekerja) dan `manager` (pengelola, super admin). Baca `CLAUDE.md`, `AGENTS.md`, `PRODUCT.md`, `DESIGN.md` dulu.

Pengguna meminta tampilan **login** dan **semua halaman pengelola** dibuat lebih menarik dan lebih enak dipakai: kartu yang bergerak tipis, umpan balik tekan/hover, animasi yang punya tujuan. Audit lengkap, bukti `path:baris`, dan alasan pemilihan referensi ada di [`FRONTEND_CANOPY_AUDIT_20261111.md`](FRONTEND_CANOPY_AUDIT_20261111.md). Mockup acuan: [Artifact "Inofarm Canopy"](https://claude.ai/artifact/WqvsjyHQbgbQnJKuzd3Cb4), 19 artboard dalam tiga baris: (1) Login, Beranda, Detail greenhouse, sheet Catat; (2) Riwayat, Cek catatan pekerja, Keuangan, sheet Alat keuangan, Kelola; (3) form Kegiatan, Pengukuran, Panen, langkah Periksa, Pengeluaran, Penjualan lunas, Alokasi biaya, Perolehan aset, Wizard greenhouse, Buat akun. Angka, nama greenhouse dan nama pekerja di mockup adalah data contoh. Jika link tidak bisa dibuka dari sesimu, bagian 5 dokumen ini sudah lengkap sebagai spesifikasi.

### 1.1 Keputusan yang sudah diambil (jangan ditanyakan ulang)

| ID | Keputusan |
|---|---|
| K1 | Arah visual **Canopy**: dasar Referensi 1 (forest + krem). Dari Referensi 2 hanya diambil bar navigasi gelap dengan tombol aksi di tengah. Tidak ada lime/neon, huruf kapital condensed, donut chart, delta persen, onboarding, atau ilustrasi 3D |
| K2 | **Tidak ada warna baru.** Semua warna dari `FarmColors`/`ForestScheme`. Tombol Catat memakai sage `#D4E4B7` di bar forest, sesuai aturan `DESIGN.md` "jangan menambah warna baru untuk aksi Catat" |
| K3 | Tombol **Catat** pengelola pindah dari FAB melayang ke **tengah bar navigasi** (5 slot: Beranda, Riwayat, Catat, Keuangan, Kelola), dengan label teks "Catat" di bawah lingkaran. Pekerja **tetap** memakai FAB dan bar dua tab yang sekarang |
| K4 | Navigasi pengelola menjadi **bar forest solid tunggal**. Cabang kaca (`translucentNavigation`) tidak dipakai lagi untuk pengelola. Untuk pekerja tetap seperti sekarang |
| K5 | Latar login dan Beranda pengelola: crop ilustrasi cat air diganti **motif garis daun vektor** yang digambar `Canvas`. File drawable **tidak dihapus** (`background_dashboard` masih dipakai `HomeContextCard.kt:102` milik pekerja) |
| K6 | Font: **Plus Jakarta Sans** (OFL), **disetujui pengguna 11 November 2026**. Satu file variabel `android/app/src/main/res/font/plus_jakarta_sans_variable.ttf` (sumber resmi `google/fonts`, `ofl/plusjakartasans/PlusJakartaSans[wght].ttf`, 176.288 byte) dan lisensinya di `third_party/fonts/plus-jakarta-sans/OFL.txt` (root repo, 4.402 byte). **Kedua file sudah diunduh 11 November 2026** (belum di-commit). SHA-256 TTF `89b3fb38aa0d275d7a731d0d817a4f1622b316b4d7fbdedcf02ee9099ff68bc8`, OFL `995c7199cab65954f545996326755daee7b63cc6b42b06c13da1f9502ab08a99`; cek ulang dan commit keduanya di Fase 1. Bobot 400/500/600/700/800 lewat `Font(R.font.plus_jakarta_sans_variable, weight, variationSettings = FontVariation.Settings(FontVariation.weight(n)))`; minSdk 29 mendukung font variabel. Semua peran tipografi pindah ke keluarga ini, fallback sistem otomatis. Pekerja ikut berganti font (efek bersama K8) |
| K7 | Angka (kas, EC, PPM, kg) **tetap langsung tampil**, tanpa animasi count-up |
| K8 | Pekerja tidak berubah kecuali efek bersama yang tidak terhindarkan: layar login (dipakai dua role) dan font bila K6 aktif. Catat dampak itu di evidence dengan screenshot pekerja |

---

## 2. Skill wajib

Semua skill di bawah sudah terpasang di proyek. Panggil dengan tool `Skill` memakai nama persis. Di setiap laporan fase, sebutkan skill yang dimuat dan aturan yang diterapkan. **Jangan memakai skill `ponytail` dan turunannya** untuk pekerjaan ini (permintaan pengguna).

| Skill | Kapan |
|---|---|
| `antislop` (inti) | Awal sesi, sebelum kode apa pun |
| `antislop-ui` | Setiap edit layar: warna, komponen, gerak, dosis bayangan/radius |
| `antislop-layoutmobile` | Bar navigasi 5 slot, tap target, font besar, layar 360 dp |
| `antislop-human` | Kontras, fokus, TalkBack, state. Jalankan `python .claude/skills/antislop-human/contrast-check.py <hex> <hex>` untuk setiap pasangan warna yang belum ada di tabel audit |
| `antislop-copywriting` | Setiap teks baru (sapaan, label pintasan, pesan kosong) |
| `antislop-code` | Sebelum setiap commit, bersihkan komentar generik tanpa mengubah kode |
| `impeccable` | Semua desain. Mode **Operate**, platform Android native |

Subagent (tool `Agent`, `subagent_type`):

| Subagent | Pakai |
|---|---|
| `impeccable-finish-reviewer` | Akhir Fase 2, 4, 5, 6, 7, 8 dan 9: review hasil terhadap bagian 5 dan `DESIGN.md`. Perbaiki temuan material sebelum commit fase |
| `impeccable-documenter` | Fase 9: perbarui `DESIGN.md` dari komponen yang benar-benar dirilis, bukan dari rencana |
| `impeccable-asset-producer`, `impeccable-manual-edit-applier` | Tidak dipakai |

### 2.1 Aturan antislop

- Mode **DURING** sebagai session override. Umumkan sekali sebelum edit pertama: `antislop active: during (session override).` Jangan membuat atau mengubah file settings antislop.
- Aturan berbasis web diterjemahkan ke Compose: CSS hover → `Modifier.hoverable` + `collectIsHoveredAsState`; `:active` → `collectIsPressedAsState`; `prefers-reduced-motion` → skala animasi sistem.
- Delivery Gate antislop di akhir setiap fase UI, ringkasannya masuk evidence. Checklist `antislop-ui` wajib "ya" semua: tanpa loop gerak terus-menerus, aksen hanya di momen kunci, tanpa angka karangan.

### 2.2 Aturan impeccable

- Launcher context sekali per sesi dari root repo: `.claude/skills/impeccable/scripts/impeccable.cmd context --target android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt`. Ikuti direktifnya, jangan jalankan ulang. Jika gagal, tulis "Context loading did not run; I'll read the existing project context directly." lalu baca `PRODUCT.md` dan `DESIGN.md`.
- Baca `reference/android.md` dan `reference/mode-operate.md`. Command per fase: Fase 1 `layout`; Fase 2 `animate`, `typeset`; Fase 3 `layout`, `adapt.native`; Fase 4 `layout`, `animate`, `delight` (hanya bagian yang sesuai mode Operate); Fase 5 `layout`, `distill`; Fase 6 `layout`, `clarify`; Fase 7 `layout`, `distill`; Fase 8 `layout`, `harden` (error isian, keyboard, font besar), `clarify`; Fase 9 `audit.native`, `polish`, `harden`.
- Baca `reference/craft-floor.md` **tepat sebelum setiap edit UI**.
- Ini **redesign terarah dari identitas yang sama**: palet, ikon garis `FarmIcon`, istilah dan alur tetap; bentuk layar, hierarki dan gerak berubah. Mockup dan bagian 5 adalah arah yang sudah disetujui, bukan saran bebas.
- Hook impeccable berorientasi web. Temuan yang tidak berlaku untuk Compose dicatat "tidak berlaku" dengan alasan.
- Verifikasi dengan melihat hasil render di emulator. Kumpulkan beberapa perbaikan sebelum screenshot ulang.

---

## 3. Fakta kode saat ini (verifikasi ulang di HEAD sebelum mengubah)

Jika berbeda di HEAD, ikuti kode dan catat perbedaannya.

**Token dan komponen bersama** (`android/app/src/main/java/com/inofarm/jogja/core/design/`)
- `InofarmTheme.kt`: `FarmColors`, `FarmSpace`, `FarmShape` (Control 12, Card 20, Hero 24, Navigation 20 dp), `FarmMotion` (Press 120, State 200, Navigation 220, LoginEntrance 240, LoginFeedback 160), `ForestScheme`, tipografi `FontFamily.SansSerif` (baris 84-116).
- `FarmChrome.kt`: `FarmGlass` (46-61), `FarmIcon` + `FarmControlIcon` digambar `Canvas` (63-171), `FarmTab` dengan indikator warna yang memudar (173-222), `FarmPanel`, `FarmFact`, `FarmAmount` (tnum), `FarmReviewEntrance`, `farmFadeEntrance`, `FarmAttention` (amber), `FarmNavigationIcon` (336-351).
- `FarmMenu.kt`: `FarmMenuList`, `FarmMenuHeader` (forest bila `contextual`), `FarmRecordCard`, `LocalFarmListBottomInset`.
- `BotanicalBackdrop.kt`: crop drawable + gradasi, hanya potret < 600 dp.

**Login**: `feature/auth/FinanceLogin.kt` (204 baris). Panel forest `LoginPanel` (187-199) di atas krem, `BotanicalBackdrop(R.drawable.background_login, 228.dp)`, label pindah ke atas saat font > 1,5, tombol nonaktif saat isian kosong, `Crossfade` "Memproses…". Tag: `finance-email`, `finance-password`, `finance-password-toggle`, `finance-login`.

**Shell**: `feature/FinanceRoot.kt`. `Scaffold` (643), toolbar `FinanceToolbar` (874-940), FAB Catat (657-662, tag `finance-record-fab`, hanya tab 0/5), bar `FinanceNavigation` (663-676), `translucentNavigation` (631-632), `BotanicalBackdrop` Beranda pengelola (679), inset FAB 80 dp (693), `ManagerHomeScreen` dipanggil 713-739, `ManageScreen` 820-822, `FinancialHub` 817-819.
- `feature/FinanceNavigation.kt`: `ManagerTab` (HOME, HISTORY, FINANCE, MANAGE), `ManagerScreenIds = [0,5,4,6]`, `navIndex()`, tag `finance-tab-$index`.
- `feature/ManagerSheets.kt`: `RecordChoice`, `RecordSheet`, `RecordTypeChips`, `ManageScreen`.

**Beranda pengelola**: `feature/managerhome/ManagerHomeScreen.kt` (442 baris). Header forest + tombol (90-123), panel cek catatan (133-143), kartu greenhouse (146-160), tombol "Pilih greenhouse untuk mencatat" (161-164), chip bagian `HubSections` (363-370), ringkasan pengukuran (167-195), tombol catat sebaris (197-209, 236-237), Perlu perhatian (259-303), Catatan terbaru (305-342), `CashSummary` (397-420), `PeriodFacts` (373-394). Data dari `HomeSnapshot` (`ManagerHomeState.kt`): `cards`, `choices`, `selected`, `period`, `detail`, `cash` (`receipts`/`payments` berisi `amount` dan `records`, `from`, `until`), `json.rows("attention")`, `json.rows("recent")`.

**Keuangan**: `feature/expenses/FinancialEntryScreen.kt` `FinancialHub` (46-…), header `FarmMenuHeader("Keuangan", …)` + panel "Catatan baru" (64-78), sheet "Alat keuangan" (82-…). `reports.vm.show(gh)` menerima string kosong (`SeasonReportViewModel.kt:25`).

**Riwayat, Cek catatan, form** (ditambahkan 12 November)
- Tab Riwayat pengelola = `HistoryScreen` (`feature/history/HistoryScreen.kt`) dengan `RecordTypeChips` (`ManagerSheets.kt:79-91`) dan tombol "Rekap panen" (`FinanceRoot.kt:853-860`). Baris `HistoryRecordRow` (131-195), filter sheet `HistoryFilters` (233-296; pengelola mengetik tanggal di 268-269), kartu "Diperbarui" (87-96). `HistoryScreen` juga dipakai pekerja dan hub Keuangan.
- Cek catatan pekerja = `ActivityVerificationScreen` (`feature/verification/ActivityVerificationScreen.kt`), dibuka lewat `tab = 1` dari Beranda. Jenis dan status memakai `Choice` (148-154), tanggal diketik (170-179), kartu 200-212, tombol "Belum terkirim" 143-144.
- Form: kegiatan/pengukuran `ActivityForm` dan `ActivityReview` (`FinanceRoot.kt:947-1083`), panen `HarvestForm`/`HarvestReview` (`harvest/HarvestScreen.kt`), pengeluaran dan alokasi `FinancialForm` (`expenses/FinancialEntryScreen.kt:143-…`), penjualan `SaleForm` (`sales/SaleEntryScreen.kt`, total pratinjau `SaleDraft.preview()` di `SaleDraft.kt:19`), aset dan penyusutan `AssetForm` (`assets/AssetEntryScreen.kt`), wizard (`admin/GreenhouseWizard.kt`), akun dan master (`admin/AdminScreen.kt`, field di `admin/AdminForm.kt:19-63`; password akun baru minimal 12 karakter, `AdminForm.kt:109`). Semua isian memakai `FinanceField` dan `Choice` (`FinanceRoot.kt:1184-…`).
- Label resource admin: Varietas, Bahan, Satuan, Grade panen, Kategori biaya (`AdminForm.kt:12-17`).

**Tes yang memakai tag yang akan disentuh** (`android/app/src/androidTest/...`): `FinanceEntryUiTest.kt` (10 rujukan), `FinanceNavigationActions.kt` (2), dan masing-masing 1 di `FinanceBackendDeviceTest`, `FinanceAssetBackendDeviceTest`, `FinanceInvestmentBackendDeviceTest`, `FinanceRecoveryUiBackendDeviceTest`, `FinanceReportExportBackendDeviceTest`, `FinanceSeasonReportBackendDeviceTest`. Grep ulang: `finance-record-fab|finance-tab-|manager-home-picker|manager-add-greenhouse|manager-review-inbox|hub-section-|finance-measurement-new|"finance-new"|"harvest-new"`. Tag tambahan yang dipakai tes untuk layar baris 2 dan 3: `expense-history-kind` (13), `expense-gh` (17), `expense-new`, `finance-tools`, `finance-tools-close`, `finance-history-filters`, `history-chip-*`, `record-*`, `verification-kind`, `verification-outbox`, `verification-review|decision|reason|submit`, `allocation-new`, `asset-new`, `depreciation-new`, `sale-new`, `report-open`, `investment-open`, `recovery-open`, serta semua tag isian form (`finance-date`, `finance-time`, `finance-ec`, `finance-ppm`, `harvest-count-N`, `expense-date`, dst.). Banyak tes memilih opsi lewat helper `choose(tag, id)` yang mengetuk `tag` lalu `"$tag-option-$id"` (`FinanceAssetBackendDeviceTest.kt:41`, `FinanceBackendDeviceTest.kt:489`, `FinanceEntryUiTest.kt:577`).

**Tes navigasi yang sudah usang sebelum Canopy** (dikoreksi 12 November setelah Fase 0): `finance-tab-4` muncul 30 kali di 7 file (`FinanceEntryUiTest` 16, `FinanceBackendDeviceTest` 5, `FinanceAssetBackendDeviceTest` 4, `FinanceSeasonReportBackendDeviceTest` 2, `FinanceInvestmentBackendDeviceTest`, `FinanceRecoveryUiBackendDeviceTest`, `FinanceReportExportBackendDeviceTest` masing-masing 1). `finance-tab-1` muncul 23 kali di 2 file, `manager-home-picker` 12 kali di 7 file, `finance-record-fab` 6 kali di 2 file. Tes ini ditulis untuk bar lama lima tab pengelola (0 Beranda, 1 Antrean, 2 Kegiatan, 3 Panen, 4 Keuangan; terlihat di build `performance` 9 November) dan belum diselaraskan dengan bar empat tab (0 Beranda, 1 Riwayat, 2 Keuangan, 3 Kelola). Tangani di **Fase 0b** (bagian 6), bukan di tengah fase Canopy.

**Pemetaan maksud, bukan indeks:**

| Maksud tes lama | Jalur sekarang |
|---|---|
| `finance-tab-4` (Keuangan) | `finance-tab-2` |
| `finance-tab-1` (Antrean) untuk melihat antrean/kiriman | penanda `finance-unsent` di toolbar → sheet Belum terkirim |
| `finance-tab-1` untuk cek catatan pekerja | Beranda → `manager-review-inbox` (screen 1) |
| `finance-tab-1` yang memang ingin Riwayat | tetap `finance-tab-1` |
| `finance-tab-2`/`-3` lama (Kegiatan/Panen) | `finance-tab-1` + `history-chip-activity`/`history-chip-harvest` |
| `manager-home-picker` untuk memilih greenhouse | ketuk kartu `manager-greenhouse-{id}` (setelah Fase 4); sampai Fase 4, biarkan |
| `finance-record-fab` | tetap; tag pindah ke tombol Catat di bar pada Fase 3 |

Periksa setiap rujukan satu per satu (konteks baris sebelumnya dan assertion sesudahnya). Jangan mengganti massal dengan sed. Tes yang maksudnya tidak bisa dipetakan dengan yakin: catat di evidence dan tanyakan.

**Status verifikasi yang diwarisi**: UI pengelola super admin (11 Nov) baru terkompilasi dan lulus tes JVM, **belum pernah dirender di emulator**. Karena itu Fase 0 wajib mengambil screenshot "sebelum".

---

## 4. Batas yang tidak boleh dilanggar

- Tidak mengubah backend, kontrak, schema JSON, OpenAPI, migration, database, rumus keuangan, desimal string, HALF_UP, UUID, envelope, idempotensi, atau perilaku sinkronisasi.
- Tidak mengubah izin, data, dan alur pekerja (K3, K4, K8).
- Tidak menambah dependency Gradle atau meng-upgrade versi. `PullToRefreshBox` hanya dipakai bila sudah ada di material3 yang terpasang lewat BOM `2025.08.01`. Periksa dengan `./gradlew :app:dependencies` atau sumber library; jika tidak ada, lewati dan catat.
- Tidak mengarang data: hero kas tanpa laba dan tanpa delta; rincian "N kegiatan · N panen" hanya bila datanya ada di state.
- Tidak menghapus tag tes. Tag yang elemennya pindah ikut pindah ke elemen pengganti (contoh: `finance-record-fab` ke tombol Catat di bar). Tes yang memakai elemen yang dihapus diarahkan ke jalur baru (lewat sheet Catat), bukan dibuang.
- **Aturan kompatibilitas chip/segmen.** Saat `Choice` (dropdown) diganti chip atau segmen, beri wadah chip tag lama (misalnya `expense-history-kind`) dan setiap chip tag `"$tag-option-$id"`. Dengan begitu helper `choose(tag, id)` tetap bekerja tanpa mengubah tes: ketukan pada wadah tidak berbuat apa-apa, ketukan pada opsi memilih. Buat satu komponen `FarmChoiceChips(label, selected, options, tag, onSelect)` dengan signature yang meniru `Choice` agar penggantian satu baris. Opsi yang lebih dari 6 atau datang dari master (kategori, bahan, grade, musim, sumber pembayaran) tetap memakai `Choice`/picker, boleh dengan 4 chip "terakhir dipakai" di depannya hanya bila data itu sudah ada; jangan menambah penyimpanan baru untuk itu.
- **Tanggal dan jam.** Isian teks dan tag tetap (tes mengetik nilai). Tambahkan tombol ikon di ujung isian yang membuka `DatePickerDialog`/`TimePickerDialog` Android dan menulis nilai berformat sama (`YYYY-MM-DD`, `HH:mm`; detik yang sudah ada dipertahankan). Tanggal terbaca berbahasa Indonesia ditampilkan sebagai teks bantu. Untuk filter riwayat dan cek catatan pengelola, pakai pola yang sama (bukan `DateFilterButton` pekerja) agar tag `history-from`/`history-until`/`verification-from`/`verification-until` tetap berupa isian.
- Semua teks baru bahasa sehari-hari sesuai glosarium `MASTER_PROMPT_MANAGER_SUPERADMIN.md` bagian 6 (tanpa server, antrean, sinkron, tinjau, epoch, snapshot).

---

## 5. Spesifikasi desain Canopy

### 5.1 Token baru (tambahkan ke `InofarmTheme.kt`, jangan ubah nilai lama)

| Token | Nilai | Pakai |
|---|---|---|
| `FarmShape.Sheet` | `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)` | Sheet login |
| `FarmShape.Tile` | `RoundedCornerShape(14.dp)` | Tile ikon 40–44 dp |
| `FarmShape.Dock` | `RoundedCornerShape(26.dp)` | Bar navigasi pengelola |
| `FarmShape.Chip` | `RoundedCornerShape(10.dp)` | Chip fakta/status |
| `FarmMotion.Entrance` | 280 | Masuk login dan hero |
| `FarmMotion.Stagger` | 40 (kartu), 60 (login) | Jeda masuk bertahap |
| `FarmMotion.PressScaleButton` / `PressScaleCard` | 0,97 / 0,98 | Umpan balik tekan |
| `FarmMotion.pressSpring` | `spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)` | Tekan dan indikator |

Tipografi tambahan (bisa sebagai `TextStyle` di `FarmType`, bukan mengganti peran M3): `heroAmount` 22 sp Bold tnum, `heroNumber` 26 sp ExtraBold tnum (HST/HSS), `wordmark` 44 sp ExtraBold tracking -1,2.

### 5.2 Komponen baru (`core/design/`)

| Komponen | Isi |
|---|---|
| `Modifier.farmPressable(interaction, scale)` | Skala saat ditekan dengan `pressSpring`; elevasi +2 dp saat hover. Dipakai kartu, pintasan, tombol utama |
| `Modifier.farmStaggerEntrance(index)` | Fade + naik 12 dp, jeda `index * Stagger`, hanya pertama kali per proses (`rememberSaveable`), index > 5 langsung tampil |
| `FarmLeafMotif` | `Canvas` garis daun (tangkai + 3 helai) sage alpha 0,22–0,32, `contentDescription = null`. Parameter offset parallax |
| `FarmIconTile` | Kotak `FarmShape.Tile` 40/44 dp berisi `FarmControlIcon`; varian warna: netral (`Soft`/`Brand`), info, amber, forest |
| `FarmStatusChip` | Teks 12 sp SemiBold di `FarmShape.Chip`; varian netral, info, amber, forest. Status tetap tertulis, warna hanya penanda |
| `FarmHeroCard` | Surface forest `FarmShape.Hero`, padding 20 dp, `FarmLeafMotif` di pojok, konten bebas |
| `FarmQuickAction` | Tile 56 dp + label 12 sp dua baris maks, lencana angka opsional (`semantics` menyebut jumlah) |
| `FarmSegmentedTabs` | Baris tab dalam wadah `Soft` radius 18, bisa digeser horizontal, pill terpilih `Brand` teks putih bergeser dengan spring, `Role.Tab`, tag `hub-section-$id` dipertahankan |
| `FarmDockNavigation` | Bar forest `FarmShape.Dock` tinggi min 72 dp, 4 tab + slot tengah Catat (lingkaran sage 56 dp naik 26 dp di atas bar dengan cincin forest 5 dp, label "Catat"), pill indikator `#385442` bergeser, label dibatasi skala font 1,3 seperti `FarmTab`. Tag tab `finance-tab-0..3` tetap pada urutan Beranda, Riwayat, Keuangan, Kelola; tombol tengah tag `finance-record-fab`, `Role.Button`, bukan bagian `selectableGroup` |
| Ikon tambahan `FarmIcon` | Mail, Lock, AlertTriangle, Droplet (pengukuran), Basket (panen), BarChart (rekap), CheckList (cek catatan), GreenhouseAdd. Gaya garis 1,8 dp sama dengan ikon lama |

### 5.3 Login (`FinanceLogin.kt`)

1. Latar krem penuh. Bagian atas: bidang forest tinggi ±47% layar (min 300 dp, maks 420 dp; di landscape/tinggi < 600 dp turun ke 200 dp dan marka disembunyikan), `FarmLeafMotif` di kanan atas.
2. Di bidang forest: tile marka 52 dp (`FarmShape.Tile` besar, `#203B30`, ikon rumah kaca + daun sage), wordmark "Inofarm", tagline "Catatan operasional greenhouse melon" warna `#C4CEC3`.
3. Sheet `FarmShape.Sheet` warna `surface` menumpuk 36 dp ke bidang forest. Isi: judul "Masuk" (headlineSmall), kalimat "Pakai akun yang dibuat pengelola.", label Email di atas field + ikon Mail + placeholder `email@contoh.com`, label Password + ikon Lock + toggle mata (tag lama), pesan (logika lama, termasuk logout dan izin jaringan), tombol Masuk 56 dp `Brand` radius 16, teks "Lupa password? Minta pengelola mengatur ulang." (non-interaktif).
4. Semua logika, validasi, `imePadding`, urutan fokus Next/Done, izin jaringan lokal, live region dan tag **tidak berubah**. Label sudah selalu di atas, jadi cabang `labelAbove` hanya menentukan ukuran, tidak lagi memindahkan label.
5. Gerak: masuk bertahap marka/judul/sheet 0/60/120 ms; tombol `farmPressable`; saat `state.error` berganti ke nilai baru, sheet bergeser horizontal sekali (6 dp, tiga ayunan, ±300 ms); selama memproses, isi tombol berganti `CircularProgressIndicator` 20 dp + "Memproses…" lewat `AnimatedContent`.
6. Tema gelap: sheet `ForestScheme.surface`, bidang atas `ForestScheme.background`, garis pemisah 1 dp `outlineVariant` di tepi sheet.

### 5.4 Shell dan navigasi pengelola (`FinanceRoot.kt`, `FinanceNavigation.kt`)

1. Pengelola memakai `FarmDockNavigation`; pekerja tetap `FinanceNavigation` + FAB. `translucentNavigation` hanya berlaku untuk pekerja.
2. Catat di bar tampil ketika `showNavigation` dan nonaktif (bukan hilang) saat `writing`. Ketuk = `recordSheet = true`, sheet dan pilihan tidak berubah. Di tab Keuangan/Kelola juga aktif.
3. Hapus FAB pengelola dan sesuaikan `recordInset` pengelola menjadi tinggi bar + 16 dp (cek baris terakhir tidak tertutup di 360 dp dan font 200%).
4. Toolbar pengelola di Beranda semua greenhouse: sapaan WIB (05.00–10.59 pagi, 11.00–14.59 siang, 15.00–18.29 sore, sisanya malam) di atas tanggal "Rabu, 11 Nov 2026"; kanan: penanda "N belum terkirim" (amber teks, garis tipis) dan avatar inisial 44 dp sage yang membuka menu akun lama (tag `finance-account`). Di detail greenhouse: tombol kembali + nama greenhouse. Di tab lain: judul tab. Penanda dan live region tidak berubah.
5. Tarik ke bawah untuk memperbarui di Beranda pengelola (memanggil jalur `managerRefresh++` + `onSync()` yang sudah ada), bila `PullToRefreshBox` tersedia.
6. `BotanicalBackdrop` di Beranda pengelola diganti `FarmLeafMotif` di dalam hero. Jangan menghapus fungsi `BotanicalBackdrop` (dipakai pekerja).

### 5.5 Beranda semua greenhouse (`ManagerHomeScreen.kt`, `selection == null`)

Urutan dari atas:

1. **Hero kas** (`FarmHeroCard`): "Kas {bulan} · WIB" + "Semua greenhouse"; dua kolom Penerimaan dan Pengeluaran (`FarmAmount` gaya `heroAmount`), di bawah tiap angka "Penjualan lunas · N" / "Pembayaran aktual · N"; batang dua segmen sage (`#D4E4B7`) dan amber muda (`ForestScheme.secondary` `#DCC28C`) proporsional ke nilai BigDecimal, disembunyikan bila keduanya nol (ganti teks "Belum ada transaksi tercatat bulan ini."); tautan "Lihat keuangan" (tag `manager-home-finance`). Batang `clearAndSetSemantics` karena angkanya sudah tertulis. Data dari `snapshot.cash`, tidak ada hitungan baru selain proporsi tampilan.
2. **Pintasan 4 ikon** (`FarmQuickAction`, grid 4 kolom, membungkus 2×2 bila lebar < 340 dp atau font > 1,3): Cek catatan (lencana `reviewCount`, "50+" bila ≥ 50, tag `manager-review-inbox`), Tambah greenhouse (tag `manager-add-greenhouse`), Rekap panen (buka `HarvestProgressScreen` lewat jalur yang sama dengan tombol "Rekap panen" di Riwayat), Evaluasi musim (`reports.vm.show("")`). Tambahkan callback baru ke `ManagerHomeScreen` seperlunya.
3. **Kartu cek catatan** sage (hanya bila `reviewCount > 0`): tile forest, "N catatan pekerja perlu dicek", rincian per jenis hanya jika datanya tersedia, chevron, seluruh kartu satu ketukan. Bila `reviewCount == null`, tidak ada kartu (pintasan sudah ada).
4. **Greenhouse** (judul + "N greenhouse"): kartu `farmPressable` + `farmStaggerEntrance`. Isi: `FarmIconTile` status (ready → netral ikon greenhouse; needs_review/none → amber ikon peringatan dengan teks "Tanam saat ini belum dipilih"/"Belum ada tanam saat ini"; inactive → netral redup, teks "Greenhouse tidak aktif"), nama (17 sp Bold), "varietas · Kelompok X", di kanan angka HST `heroNumber` + label "HST" (fallback HSS bila HST kosong; tidak ada angka bila keduanya kosong). Baris chip: "Fase {nilai}", "Panen {kg} kg" bila ada, "Catatan {waktu}" (teks biasa), dan chip amber "PPM di luar acuan" bila ada finding `measurement_outside_range` untuk greenhouse itu (filter `validToday && !cached` yang sama). `contentDescription` kartu merangkum nama, tanam, umur dan status. Tag `manager-greenhouse-{id}` tetap.
5. **Perlu perhatian**: kartu amber (`FarmAttention` gaya baru dengan tile ikon) untuk antrean bermasalah dan temuan, aksi lama tetap.
6. **Catatan terbaru**: daftar dalam satu `FarmRecordCard`, baris 48 dp+ dengan `FarmIconTile` per jenis (pengukuran info, kegiatan netral, panen netral basket), judul, "greenhouse · pencatat", waktu kanan; divider berindentasi. Tautan "Lihat semua kegiatan/panen" tetap.
7. Hapus tombol "Pilih greenhouse untuk mencatat" (B-05) dan header lama "Semua greenhouse" + tombol pemilih (B-01). Pemilih greenhouse `FarmPicker` tetap bisa dibuka dari detail (tombol kembali → semua) dan dari sheet Catat seperti sekarang. Jika tes memakai `manager-home-picker`, alihkan ke ketuk kartu `manager-greenhouse-{id}`.

### 5.6 Detail satu greenhouse (`selection != null`)

1. Hero forest: chip "Aktif" (atau "Nonaktif"), "Diperbarui HH.mm WIB"/"Data tersimpan · …", varietas (22 sp), "Kelompok X · {musim}", tiga tile `#203B30`: HSS, HST, fase aktual ("belum tercatat" bila kosong), tombol bergaris "Ganti tanam saat ini"/"Pilih tanam saat ini" (tag `manager-period-history`).
2. `FarmSegmentedTabs` menggantikan `HubSections` (Ringkasan, Kegiatan, Panen, Keuangan, Pengaturan).
3. Ringkasan: judul "Pengukuran hari ini" + jam WIB kanan, `MeasurementCards` tanpa pill judul lama (ubah hanya untuk pengelola atau lewat parameter, pekerja tidak berubah), chip status cek + pencatat + "Lihat sumber". Kalimat status hari ini yang lama tetap ada sebagai teks (testTag `manager-measurement-day-status`).
4. Hapus tombol catat sebaris (D-02): `finance-measurement-new`, `finance-new`, `harvest-new` di Beranda. Baris jadwal tetap punya "Catat hasil". Tes yang menekan tag itu dialihkan ke Catat → pilihan sheet.
5. Panen tanam saat ini: kartu baris dengan tile basket, "{kg} kg · {buah} buah" atau "Belum ada panen", chevron ke riwayat panen.
6. Kas: kartu dua baris Penerimaan/Pengeluaran dengan angka rata kanan, keterangan periode WIB, tautan lama.
7. Pengaturan: `FarmActionRow` dengan `FarmIconTile`.

### 5.7 Keuangan (`FinancialHub`) dan sheet Alat keuangan

1. Judul halaman "Keuangan".
2. `FarmHeroCard`: pill pemilih greenhouse pencatatan (tetap `Choice` bertag `expense-gh`, tampil sebagai pill forest dengan chevron); "Kas {bulan} · WIB" dengan Penerimaan dan Pengeluaran untuk greenhouse itu; dua tombol: "Catat pengeluaran" (sage di forest, tag `expense-new`, nonaktif bila belum ada greenhouse) dan "Alat keuangan" (bergaris terang, tag `finance-tools`). Angka kas diambil lewat `ManagerHomeRepository` yang sudah ada dengan greenhouse terpilih (endpoint `manager-home` yang sama, tanpa endpoint baru). Bila belum dimuat, gagal, atau tidak ada greenhouse terpilih, sembunyikan baris angka dan tulis "Kas bulan ini ada di Beranda".
3. Jenis riwayat menjadi `FarmChoiceChips` bertag `expense-history-kind` (aturan kompatibilitas bagian 4): Pembayaran, Penjualan lunas, Alokasi biaya, Aset, Penyusutan. Baris digeser horizontal.
4. Baris cakupan + tautan "Filter" (tag `finance-history-filters` tetap), lalu daftar per hari (pola Riwayat 5.10). Baris keuangan: tile ikon per jenis (pembayaran amber, penjualan sage, alokasi/aset/penyusutan netral), judul kategori atau nama aset, "greenhouse · jam · status", nominal rata kanan. Pembayaran ditulis `−Rp…` warna teks utama, penjualan lunas `+Rp…` warna `Brand`; alokasi, aset dan penyusutan tanpa tanda. Tanda hanya tampilan; `contentDescription` menyebut "Pembayaran Rp…"/"Penerimaan Rp…".
5. Sheet Alat keuangan: judul + "Untuk {greenhouse}"; kelompok "Catat dan alokasi" (Alokasikan biaya ke musim, Catat perolehan aset, Alokasi penyusutan, Catat penjualan lunas) sebagai daftar bertile; kelompok "Hitung hasil" (Evaluasi musim, Analisis investasi) sebagai dua kartu forest berdampingan; kelompok "Pemeliharaan" (`RecoveryBackupButton`, tetap hanya muncul bila `LocalRecoveryActions` ada). Semua tag lama tetap (`allocation-new`, `asset-new`, `depreciation-new`, `sale-new`, `report-open`, `investment-open`, `recovery-open`, `finance-tools-close`). Bila belum ada greenhouse, baris yang butuh greenhouse nonaktif dengan `stateDescription` lama.

### 5.8a Kelola (`ManageScreen`)

1. Kartu identitas forest di atas: avatar inisial sage 56 dp, nama, "Pengelola · semua greenhouse dan akun".
2. "Greenhouse dan orang": Tambah greenhouse (tile `Brand`, aksi utama), Greenhouse (jumlah greenhouse di kanan bila tersedia di state), Pekerja dan akun. Tile 44 dp.
3. "Jadwal dan target": dua kotak berdampingan (Jadwal, Target), menumpuk saat font > 1,3.
4. "Data master": baris teks tanpa ikon dengan label `AdminResource.label` dan keterangan lama.
5. Tombol "Keluar" bergaris dengan ikon di paling bawah (tag `manage-logout`) + "Draf yang belum terkirim tetap tersimpan di perangkat."
Tag `manage-*` tetap.

### 5.8 Tema gelap, font besar, lebar

- Gelap: latar `ForestScheme.background`, kartu `surfaceContainerHigh` + garis `outlineVariant`, hero `#203B30` dengan garis 1 dp `#4A6456` agar terpisah dari latar, bar `#263F34`, Catat tetap sage. Cek kontras setiap pasangan baru.
- Font 200% di 360 dp: pintasan 2×2, angka HST turun di bawah nama bila tidak muat, chip membungkus, label bar dibatasi 1,3, tidak ada teks terpotong tanpa semantik lengkap.
- Lebar > 600 dp: isi maksimal 900 dp (shell lama), bar maksimal 560 dp di tengah, hero login berdampingan bila landscape tablet tidak wajib (cukup tidak rusak).

### 5.9 Gerak

Ikuti tabel "Spesifikasi gerak Canopy" di dokumen audit. Tambahan wajib:
- Semua animasi berbasis waktu/spring otomatis mengikuti skala animasi sistem. Parallax motif daun dimatikan bila `Settings.Global.ANIMATOR_DURATION_SCALE` = 0.
- Tidak ada `rememberInfiniteTransition` di layar pengelola dan login.
- Animasi masuk hanya sekali per proses, tidak diulang saat kembali dari detail atau saat data diperbarui.
- `LazyColumn` tetap memakai `key` yang ada; tambahkan `Modifier.animateItem()` hanya untuk perubahan urutan kartu greenhouse jika tersedia di versi Compose terpasang.

### 5.10 Riwayat (tab Riwayat pengelola)

Perubahan dibatasi untuk pengelola dan hub Keuangan lewat parameter `HistoryScreen` (misalnya `style = HistoryStyle.Canopy`); tampilan pekerja tidak berubah (K8).
1. Satu judul "Riwayat" + tombol "Filter" bergaris di kanan dengan lencana jumlah filter aktif (tag `finance-history-filters` pada tombol). Header lama `kind.label` dan judul "Riwayat" di kartu filter dihapus untuk pengelola.
2. Baris cakupan satu baris: cakupan ringkas + "Diperbarui HH.mm WIB" (menggantikan kartu "Diperbarui").
3. Chip `RecordTypeChips` jadi satu baris geser: Kegiatan, Pengukuran, Panen (pill `Brand` saat aktif), garis pemisah tipis, lalu Biaya, Penjualan, Aset sebagai chip bergaris putus dengan chevron (tetap pindah ke Keuangan). Tag `history-chip-*` tetap.
4. Daftar dikelompokkan per hari WIB dari `occurred_at`/`paid_at`/`created_at`: judul "Hari ini · Rabu, 11 Nov", "Kemarin · …", lalu tanggal. Pakai `stickyHeader` bila tersedia. Kelompokkan hanya jika urutan list memang menurun per waktu (periksa `HistoryRepository`); jika tidak, tampilkan tanpa judul hari dan catat.
5. Baris dalam satu kartu per hari, divider berindentasi. Isi: tile ikon per jenis (pengukuran info/tetes, kegiatan netral/jam, panen netral/keranjang, draf bergaris putus), judul jenis, satu baris isi (EC dan PPM untuk pengukuran bila ada di body; cuplikan grade untuk panen; catatan 1 baris untuk kegiatan), "greenhouse · kelompok · pencatat", jam di kanan, chip status di bawah jam. Chip status: keputusan cek bila tersedia (`state.checks`, label `verificationLabel`), selain itu label `SyncState` lama. Warna chip: Belum dicek info, Sesuai/Terkirim sage, Perlu diperbaiki error, Belum terkirim/draf amber. Tag `finance-record-{id}` tetap pada baris.
6. "Muat 50 berikutnya" tombol bergaris lebar penuh (tag lama). Kosong/gagal/memuat memakai kartu yang ada dengan gaya Canopy.
7. Riwayat panen pengelola: tautan "Rekap panen" menjadi kartu baris bertile di atas daftar (tag `harvest-progress-open`).

### 5.11 Cek catatan pekerja (`ActivityVerificationScreen`, daftar)

1. Bar atas: kembali ke Beranda + judul "Cek catatan pekerja".
2. Segmen dua pilihan Kegiatan | Panen = `FarmChoiceChips` bertag `verification-kind`. Tampilkan jumlah per jenis hanya bila data jumlahnya ada.
3. Chip status geser bertag `verification-status`: Belum dicek, Sudah dicek, Perlu diperbaiki, Sesuai, Catatan lama.
4. Baris cakupan + tautan "Filter" yang membuka sheet berisi greenhouse, pencatat, jenis kegiatan, dan tanggal (pola tanggal bagian 4). Tag isian lama tetap.
5. Kartu: tile ikon, jenis, "pencatat · waktu", chip status kanan; untuk pengukuran dua kotak angka EC/PPM besar; untuk kegiatan lain catatan 2 baris; konteks "greenhouse · musim · kelompok" di bawah. Kotak PPM amber + "Di luar acuan" hanya bila baris inbox membawa hasil cek acuan; jika tidak, kotak netral. Tag `verification-{wire}-{id}` tetap.
6. Tombol "Belum terkirim" turun menjadi tautan "Catatan saya yang belum terkirim" di akhir daftar dengan tag `verification-outbox` (dipakai `FinanceBackendDeviceTest.kt:357-358`).
7. Detail verifikasi (`VerificationDetail`) hanya diberi gaya Canopy (kartu, chip, bar bawah keputusan Sesuai / Perlu diperbaiki); logika dan tag `verification-review|decision|reason|submit` tetap.

### 5.12 Sheet Catat (`RecordSheet`)

1. Judul "Catat" + baris konteks "Greenhouse · Kelompok" bila Beranda sedang membuka satu greenhouse dengan tanam saat ini.
2. Kartu "Mulai tanam" (tag `record-start-planting`) tetap di atas bila kondisinya terpenuhi.
3. Kelompok "Di greenhouse": Kegiatan, Pengukuran, Panen sebagai kotak 3 kolom (tile ikon, label, keterangan pendek). Kelompok "Uang": Biaya, Penjualan sebagai kotak 2 kolom. Pekerja tetap hanya melihat kelompok pertama (sheet pekerja boleh ikut bergaya kotak karena komponennya sama; catat di evidence sebagai efek bersama, atau batasi lewat parameter bila tidak diinginkan).
4. Ikon `RecordChoice` diganti: Kegiatan jam/daun, Pengukuran tetes, Panen keranjang, Biaya dompet, Penjualan garis naik. Tag `record-*` tetap.
5. Teks bawah: "Greenhouse lain? Pilih dulu di Beranda, atau biaya dan penjualan akan menanyakan greenhouse-nya."
6. Kotak masuk bertahap 30 ms setelah sheet naik; tekan = skala 0,96.

### 5.13 Pola form Canopy (semua form pengelola)

Berlaku untuk `ActivityForm` (kegiatan dan pengukuran), `HarvestForm`, `FinancialForm` (pengeluaran dan alokasi), `SaleForm`, `AssetForm` (perolehan dan penyusutan), form admin, dan wizard. Form pekerja memakai komponen yang sama hanya bila tidak mengubah perilaku pekerja; jika ragu, aktifkan lewat parameter untuk pengelola saja dan catat.

1. **Bar atas form**: tombol X (aksi `saveAndClose` yang sama dengan "Simpan draf dan kembali"), judul, dan status draf (`FarmDraftStatus`) di bawah judul. Bar IME lama (`FinanceRoot.kt:650-651`) diganti bar ini.
2. **Strip konteks forest** (radius 16): ikon greenhouse, "Greenhouse · Kelompok", umur HST di kanan (`ManagerDraftParents` sebagai sumber data).
3. **Bagian**: `FarmFormSection(title)` = kartu surface radius 20, padding 16, judul 15 sp Bold. Menggantikan `FarmSection` hanya di form pengelola.
4. **Isian**: label di atas (13 sp SemiBold), tinggi 52 dp, radius 14, garis `outline`, fokus 2 dp `Brand`; satuan sebagai suffix di dalam isian; bantuan dan error di bawah isian. Komponen `FinanceField` diberi parameter `suffix`, `prominent` (angka besar 24–34 sp tnum), dan `trailingAction` untuk tombol picker.
5. **Angka utama**: EC/PPM (dua kotak 72 dp berdampingan), jumlah/bobot panen (60 dp), nominal pengeluaran (di kartu forest, 34 sp, garis bawah sage), nominal alokasi (60 dp). Pemisah ribuan hanya tampilan (`VisualTransformation`); nilai tersimpan tetap tanpa pemisah, sesuai validasi lama.
6. **Pilihan pendek → chip/segmen** (aturan kompatibilitas bagian 4): jenis kegiatan (`finance-type`), kondisi buah (`harvest-quality-N`; Cacat aktif memakai amber `#805016` teks putih), jenis pembayaran (`expense-payment-kind`), kelas biaya (`allocation-class`, label "Biaya tetap / FC" dan "Biaya variabel / VC"), peran akun (kartu pilihan Pekerja/Pengelola dengan keterangan).
7. **Rincian berulang** (bahan, grade panen, sumber penjualan): kartu per rincian; pemilih di atas dengan tombol hapus ikon merah 48 dp di kanan (`contentDescription` "Hapus rincian N", tag lama), isian angka berdampingan. Tombol tambah bergaris putus lebar penuh (tag lama).
8. **Waktu**: form kegiatan, pengeluaran, penjualan, aset memakai dua isian Tanggal | Jam dengan tombol picker (bagian 4) dan tanggal terbaca di bawahnya. Form pengukuran dan panen menampilkan ringkasan "Rabu, 11 Nov 2026 · 07:30 WIB" + "Ubah" yang membuka dua isian yang sama (tag `finance-time-edit`/`harvest-time-edit` pada tombol Ubah). Tes yang mengetik tanggal di dua form ini diarahkan mengetuk Ubah lebih dulu.
9. **Bagian opsional**: "Pengamatan (opsional)" dan "Keterangan" dapat dibuka-tutup dengan `animateContentSize` (perilaku `observationsOpen` lama).
10. **Kartu total** (hanya pratinjau): panen = jumlah buah dan kg dari isian ("Total isian"); penjualan = `SaleDraft.preview()` di kartu forest "Total penerimaan", subtotal per rincian hanya bila `SaleAmounts.quote` menyediakannya; alokasi = batang "Sudah dialokasikan Rp… dari Rp…" dari `ExpenseBalanceCard` dan "Maksimal sisa" = terbayar − teralokasi dari saldo tersimpan, dengan kalimat "Saldo terakhir tersimpan. Bisa berubah sebelum catatan terkirim." Tidak ada rumus keuangan baru.
11. **Kartu acuan** (`ReferenceCards`) bergaya info (`#E7F0F2`/`#365B66`) di akhir form.
12. **Bar bawah menempel** di atas keyboard (`imePadding`): "Simpan draf" bergaris (tag `*-save-draft`) dan tombol utama "Periksa …" (tag `*-review`). Teks tombol mengikuti teks lama ("Periksa catatan", "Periksa panen", "Periksa penjualan", "Periksa catatan keuangan" boleh dipendekkan menjadi "Periksa" hanya bila tes tidak mencari teksnya). Pesan error tetap dekat isian dan ringkasannya di atas bar bawah.
13. Sumber penjualan menampilkan "Sisa N kg" di pemilih hanya bila opsi membawa data ketersediaan.

### 5.14 Periksa, wizard, dan form akun

1. **Periksa** (`ActivityReview`, `HarvestReview`, `FinancialReview`, `SaleReview`, `AssetReview`): kalimat "Pastikan isinya benar. Setelah disimpan, catatan dikirim saat ada koneksi."; kartu ringkasan dengan kepala forest (tile ikon jenis, jenis, "greenhouse · tanggal · jam WIB"), baris angka utama besar (dua kolom), lalu daftar label-nilai (`dl`) dengan pemisah tipis; rincian Cacat ditulis amber. Bar bawah: "Ubah isian" bergaris dan "Simpan" (`Brand`, ikon centang; tag `*-queue` tetap). Masuk memakai `FarmReviewEntrance` yang ada.
2. **Wizard Tambah greenhouse**: bar atas X + judul; indikator 3 segmen (Greenhouse, Mulai tanam, Pekerja) dengan centang pada langkah selesai dan segmen yang bergeser; teks "Langkah N dari 3 · bisa dilewati" (tag `wizard-step`, live region tetap); judul langkah; kartu terkunci "Greenhouse C sudah tersimpan" untuk isian yang tersimpan; bagian Musim dan Kelompok tanam memakai pola 5.13; bar bawah "Kembali" | "Simpan dan lanjut" dan tautan "Lewati langkah ini" (tag `wizard-skip`). Progres simpan per tahap tetap.
3. **Form akun** (`AdminScreen` untuk `AdminResource.USER`): peran sebagai dua kartu pilihan, Data akun (Nama, Email dengan ikon, Password awal dengan ikon dan tombol tampilkan, bantuan "minimal 12 karakter"), Greenhouse tempat bekerja sebagai chip pilihan tunggal (field `greenhouse_id` satu nilai), bar bawah "Buat akun". Form admin lain (greenhouse, musim, kelompok, master, jadwal, target, penugasan) mengikuti pola 5.13 tanpa mockup tersendiri.

---

## 6. Fase kerja

Satu branch `codex/` per fase, dibuat dari ujung fase sebelumnya. Commit hanya file task setelah pemeriksaan bagian 7 lulus.

| Fase | Branch | Isi | Selesai bila |
|---|---|---|---|
| 0 | `codex/canopy-baseline` | Baca dokumen, launcher, petakan requirement → acceptance → file → tes. Grep tag tes (bagian 3). Ambil screenshot **sebelum** (login, Beranda pengelola, detail, Keuangan, Kelola, sheet Catat) lewat fixture MockWebServer seperti `WORKER_FLOW_EVIDENCE_20261111.md` (cari helper di `BotanicalEvidence.kt`/tes UI). Commit dokumen audit, master prompt dan RUN bila belum ter-commit | Matriks dan screenshot sebelum ada di evidence; tanpa perubahan kode |
| 0b | `codex/canopy-test-nav` | Selaraskan tes instrumentasi dengan bar empat tab memakai tabel pemetaan di bagian 3. Hanya file `androidTest`, tanpa perubahan `main`. Jalankan kelas yang disentuh di emulator dan catat mana yang lulus, gagal karena alasan lain (tulis alasannya), atau butuh backend | Hasil per kelas tercatat di evidence sebagai **baseline sebelum Canopy**. Kegagalan yang tersisa di sini tidak boleh nanti diklaim sebagai akibat Canopy, dan sebaliknya |
| 1 | `codex/canopy-foundation` | Token 5.1, komponen 5.2, ikon baru, font bila K6 aktif. Tes JVM untuk fungsi murni baru (sapaan WIB, proporsi batang, pilihan angka HST/HSS) | Build, JVM, lint lulus; belum ada layar yang berubah |
| 2 | `codex/canopy-login` | 5.3 | Instrumentasi login lulus; screenshot login terang/gelap/200%/landscape; review finish-reviewer |
| 3 | `codex/canopy-shell` | 5.4, 5.12 | Tes navigasi pengelola dan pekerja lulus; Catat di bar membuka sheet baru; pekerja tidak berubah (screenshot) |
| 4 | `codex/canopy-home` | 5.5 | Tes Beranda pengelola lulus; screenshot; review finish-reviewer |
| 5 | `codex/canopy-detail` | 5.6 | Tes detail greenhouse lulus; screenshot; review finish-reviewer |
| 6 | `codex/canopy-history` | 5.10, 5.11, `FarmChoiceChips` | Tes Riwayat, verifikasi, dan helper `choose()` lulus tanpa mengubah tes; Riwayat pekerja tidak berubah (screenshot); review finish-reviewer |
| 7 | `codex/canopy-finance-manage` | 5.7, 5.8a | Tes Keuangan, aset, investasi, laporan, recovery dan admin lulus; screenshot; review finish-reviewer |
| 8 | `codex/canopy-forms` | 5.13, 5.14 (form kegiatan, pengukuran, panen, pengeluaran, alokasi, penjualan, aset, penyusutan, periksa, wizard, akun, form admin lain) | Tes entri dan backend device yang relevan lulus; screenshot setiap form 100%, 200% dan dengan keyboard terbuka; review finish-reviewer |
| 9 | `codex/canopy-polish` | 5.8, 5.9, `audit.native`, `polish`, `harden` (kosong, gagal, offline, data tersimpan, sesi berakhir), TalkBack, `impeccable-documenter` → `DESIGN.md`, evidence `FRONTEND_CANOPY_EVIDENCE_<tanggal>.md`, update `CLAUDE.md` (paragraf UI pengelola dan stack bila font berubah), `IMPLEMENTATION_STATUS.md`, `GAP_ANALYSIS.md`, `docs/context/README.md` | Semua butir bagian 9 |

---

## 7. Pemeriksaan wajib per fase

```powershell
./scripts/build-android.ps1 -JavaHome "C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
python scripts/check-finance-context.py
```

- Build mencakup debug/test APK, tes JVM dan lint. Laporkan jumlah tes, gagal, dan warning lint (baseline terakhir: 0 error, 16 warning, 1 hint). Tidak boleh ada error baru; warning baru dijelaskan.
- Instrumentasi di emulator untuk alur yang diubah, satu kelas per panggilan, tanpa Gradle lain berjalan bersamaan. Tes backend tidak perlu dijalankan karena backend tidak berubah; jika kamu tetap menyentuh file backend, berhenti (bagian 10).
- Screenshot di `outputs/frontend-canopy-<tanggal>/before|after|dark|font200|landscape/` (diabaikan git), lebar 360 dp dan 411 dp.
- Jangan menimpa `JAVA_HOME`, SDK, atau env pengguna. Jangan clear app data pada aplikasi yang dipakai pengguna. Jangan login ke backend lokal pengguna untuk screenshot; pakai fixture.
- Klaim "lulus" hanya dengan output perintah dari sesi ini.

---

## 8. Aturan anti-halu dan git

- Jangan menyebut file, fungsi, tag, atau tes yang belum kamu buka di sesi ini. Kutip `path:baris` untuk klaim penting.
- Bedakan bukti baru dari bukti lama, dan fixture dari backend/perangkat nyata. Emulator bukan perangkat fisik.
- Branch `codex/`, pertahankan perubahan dan file untracked pengguna (saat ini: `.agents/skills/...`, `android/.run/...`, `docs/context/README.md` termodifikasi; `UI inspo/`, `assets/`, `.claude/`, `.codex/`, `docs/design/proposal-revisi-20261026/`, `scripts/__pycache__/`, `scripts/start-backend-for-studio.ps1`, `skills-lock.json`, beberapa `MASTER_PROMPT_*`/`RUN_*`). Jangan reset/stash/delete untuk pindah branch. `docs/context/README.md` sudah termodifikasi pengguna: sebelum mengubahnya di Fase 9, lihat diff-nya dan pertahankan isinya.
- Jangan stage secrets, `.env`, dump, log, build, `outputs/`, `UI inspo/`, `assets/`.
- Remote `github`. Push hanya jika pengguna meminta; `git fetch` dulu, tanpa force-push dan tanpa merge ke `main`.
- Akhiri pesan commit dengan baris atribusi yang diminta sistem.

---

## 9. Definisi selesai

- Login dan semua halaman pengelola mengikuti bagian 5, dibuktikan screenshot sebelum/sesudah (terang, gelap, font 200% di 360 dp): Beranda, detail greenhouse, sheet Catat, Riwayat, Cek catatan, Keuangan, sheet Alat keuangan, Kelola, setiap form pada 5.13, langkah Periksa, wizard, dan form akun.
- Pengelola bisa mengisi tanggal dan jam lewat picker di semua form dan filter, dan tetap bisa mengetik nilai yang sama.
- Tidak ada dropdown untuk pilihan dua sampai lima opsi tetap; helper `choose()` di tes tetap lulus tanpa diubah.
- Catat ada di tengah bar pengelola dengan label, selalu terjangkau satu ketukan dari empat tab; tidak ada baris terakhir yang tertutup.
- Tidak ada warna baru, tidak ada angka karangan, tidak ada loop gerak; semua pasangan warna baru lulus `contrast-check.py`.
- Gerak bagian 5.9 berjalan dan mati bersih saat skala animasi 0 (screenshot/rekaman atau tes).
- TalkBack: tab, Catat, pintasan berlencana, kartu greenhouse dan hero kas terbaca dengan urutan dan isi yang benar.
- Pekerja tidak berubah kecuali login (dan font bila K6), dibuktikan screenshot dan tes navigasi pekerja.
- Build, JVM, lint dan instrumentasi yang relevan lulus dengan output tercatat; `DESIGN.md`, evidence dan context diperbarui.
- Laporan akhir: perubahan per fase, skill dan aturan penting, hasil tes, yang belum diverifikasi (perangkat fisik, Doze tidak relevan, TalkBack nyata bila hanya diuji semantik), branch per fase.

---

## 10. Kapan berhenti dan tanya pengguna

Lanjutkan pekerjaan reversible dalam scope tanpa izin per langkah. Berhenti dan tanya bila:

1. Perubahan menyentuh backend, kontrak, database, atau perilaku pekerja di luar K8.
2. K6: file font atau `OFL.txt` ternyata tidak ada di lokasi K6, atau hash-nya berbeda, saat Fase 1 (keduanya sudah diunduh 11 November). Tanya sekali apakah kamu boleh mengunduhnya dari `https://raw.githubusercontent.com/google/fonts/main/ofl/plusjakartasans/` (sebutkan nama dan ukuran file). Jangan mengunduh tanpa izin itu. Setelah ada, cek ukuran file sama dengan K6, commit font + lisensi di Fase 1, dan catat sumbernya di evidence. Jika belum dijawab, kerjakan Fase 1 dengan sans sistem lalu ganti keluarga font begitu file tersedia.
3. Butuh dependency baru atau upgrade (misalnya `PullToRefreshBox` tidak tersedia).
4. Tes lama hanya bisa dipertahankan dengan mengubah makna assertion-nya.
5. Push ke remote.

Format pertanyaan: satu kalimat konteks, pilihan konkret, rekomendasimu.
