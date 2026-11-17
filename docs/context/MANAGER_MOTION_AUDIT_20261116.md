# Audit motion halaman pengelola, 16 November 2026

Audit baca kode atas semua layar pengelola untuk mencari tempat yang layak diberi animasi. Pada hari yang sama P1 sampai P3 diimplementasikan atas permintaan pengguna; lihat [status implementasi](#status-implementasi-16-november-2026) di akhir dokumen.

Skill yang dipakai: `impeccable` (mode Operate, referensi `animate.md` dan `android.md`) dan `motion` (aturan universal: animasi hanya untuk perubahan state, hubungan antarelemen atau umpan balik; 150 sampai 300 ms untuk elemen kecil, sampai 500 ms untuk permukaan besar; hormati reduced motion). Library Motion dan audit MotionScore khusus web, jadi tidak berlaku untuk Compose. Aturan motion di `DESIGN.md` §Motion dan konstanta `FarmMotion` (`core/design/InofarmTheme.kt:65`) menjadi acuan.

## Yang sudah ada dan tidak perlu disentuh

| Bagian | Implementasi |
|---|---|
| Perpindahan layar shell | `FarmDestinationMotion`: shared axis X 300 ms, fade-through dock, fade 200 ms, pratinjau Back prediktif |
| Dock dan tab segmen | Pil bergeser dengan spring redaman 0,9; warna 200 ms; ikon dock membesar 1,08 |
| Tekan kartu dan tombol | `farmPressable`, spring redaman 0,7, skala 0,97/0,98 |
| Masuk pertama | `farmStaggerEntrance` sekali per proses pada kartu greenhouse dan tile sheet Catat |
| Daftar | `animateItem` pada kartu greenhouse Beranda, baris Riwayat, Cek catatan, Belum terkirim |
| Simpan dan sinkron | `FarmFormBar` dengan `BusyReveal` 300 ms; "Semua terkirim" dan ikon sinkron berputar; haptik confirm/reject/segmentTick |
| Sheet tanam saat ini | `AnimatedVisibility` fade pada mode ganti |

Sistemnya sudah matang. Temuan di bawah adalah celah yang membuat layar **meloncat** atau **berganti tanpa penjelasan**, bukan tempat untuk menambah dekorasi.

## P1: perubahan state yang sekarang meloncat

### 1. Beranda: kartu yang datang belakangan mendorong daftar greenhouse

`feature/managerhome/ManagerHomeScreen.kt:103`, `:121`, `:237`–`:276`

Kartu "N catatan pekerja perlu dicek", panel data tersimpan, judul "Perlu perhatian", kartu "Belum terkirim" dan temuan muncul setelah data datang. Hanya kartu greenhouse yang memakai `animateItem`, jadi kartu greenhouse ikut bergeser, tetapi item yang baru datang muncul mendadak dan judul bagian tidak ikut bergeser.

**Usul:** tambahkan `Modifier.animateItem()` pada item `coverage`, `review-inbox`, `attention`, `local-queue` dan setiap temuan. Pakai spesifikasi bawaan atau `fadeInSpec = tween(FarmMotion.State)`. Biaya kecil, risiko rendah.

### 2. Detail greenhouse: isi bagian berganti mendadak saat pil bergeser

`feature/managerhome/ManagerHomeScreen.kt:140`–`:299`

`FarmSegmentedTabs` (Ringkasan, Kegiatan, Panen, Keuangan, Pengaturan) menggeser pil dengan spring, tetapi item di bawahnya (`measurement`, `work`, `harvest`, `settings`, `recent`, `cash`) hilang dan muncul dalam satu frame. Pil yang halus di atas isi yang terpotong terasa tidak selaras.

**Usul:** `animateItem()` pada item per bagian supaya yang keluar memudar dan yang tinggal bergeser ke tempatnya. Jangan pakai geser horizontal: bagian ini adalah filter isi satu halaman, bukan halaman baru.

### 3. Wizard Tambah greenhouse: langkah berganti tanpa arah

`feature/admin/GreenhouseWizard.kt:244`–`:312`, `feature/FormCanopy.kt:257`

`when (step)` menukar isi langkah 1, 2, 3 seketika, dan batang `FarmStepIndicator` berubah warna tanpa transisi. Pengguna tidak melihat bahwa ia maju atau mundur.

**Usul:**
- Bungkus isi langkah dengan `AnimatedContent(step)`, shared axis X yang sama dengan shell: masuk 30 dp dengan `FarmMotion.EmphasizedDecelerate`, keluar lebih cepat dengan `EmphasizedAccelerate`, arah mengikuti naik atau turunnya `step`. Indikator, judul sheet dan `FarmFormBar` tetap diam.
- Batang indikator: `animateColorAsState(tween(FarmMotion.State))`, supaya batang yang terisi menjelaskan kemajuan.
- Layar "Greenhouse siap dicatat": fade biasa, tanpa perayaan.

### 4. Riwayat dan Keuangan: ganti chip membangun ulang seluruh layar

`feature/FinanceRoot.kt:1058` (`SaveableStateProvider("history-$historyChip")`), `feature/expenses/FinancialEntryScreen.kt` (`key(kind) { HistoryScreen(...) }`)

Kegiatan ke Panen di Riwayat, dan Pembayaran ke Penjualan ke Aset di Keuangan, mengganti seluruh `HistoryScreen` dalam satu frame. Di Keuangan, kartu hero dan baris chip ikut dibangun ulang karena keduanya ada di dalam blok `key(kind)`.

**Usul:**
- Crossfade 200 ms (`FarmMotion.State`) hanya pada isi daftar. Chip dan hero harus tetap diam. Di Keuangan, hero dan chip perlu dipindah keluar dari `key(kind)`. Perubahan struktur ini paling besar di P1, jadi jalankan `FinanceEntryUiTest` sesudahnya.
- Haptik `segmentTick()` saat chip berubah. Dokumentasi `FarmHaptics` sudah menjanjikan "filter chip", tetapi kode baru memanggilnya pada dock.

### 5. Cek catatan: isian alasan muncul mendadak

`feature/verification/ActivityVerificationScreen.kt:463`

Memilih Sesuai atau Perlu diperbaiki memunculkan isian dalam satu frame, dan labelnya berubah antara "Alasan (wajib)" dan "Catatan (opsional)". Ini titik keputusan terpenting bagi pengelola.

**Usul:** `AnimatedVisibility(decision != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut())` dengan `FarmMotion.State`. Label boleh berganti tanpa animasi. Fokus atau gulir ke isian saat "Perlu diperbaiki" dipilih membantu karena alasan wajib.

## P2: konsistensi dan rasa cepat

### 6. Rincian yang dibuka di Evaluasi musim dan Investasi

`feature/reports/SeasonReportScreen.kt:145`–`:183`, `feature/investments/InvestmentScreen.kt:196`–`:239`, `feature/history/HistoryScreen.kt:382`

"Rumus dan asumsi", "Lihat rincian" pada kartu sumber, "Lihat perubahan" pada kartu audit dan "Lihat isi yang belum terkirim" membuka konten tanpa transisi. **`DESIGN.md:276` menyatakan kartu audit dan baris rumus berubah tinggi saat rincian dibuka, tetapi kodenya belum melakukannya.** Ada ketidaksesuaian antara dokumen dan kode.

**Usul:** `Modifier.animateContentSize(tween(FarmMotion.State))` pada kartu pembungkus, seperti di `QueueScreen.kt:189`.

### 7. Indikator memuat yang berkedip

`SeasonReportScreen.kt:48`, `InvestmentScreen.kt:53`, `harvest/HarvestProgressScreen.kt:116`, `admin/AdminScreen.kt:106`

Indikator memuat muncul seketika, juga untuk respons cepat. Akibatnya indikator berkedip sesaat lalu hilang. Form sudah menyelesaikan masalah ini dengan `FarmMotion.BusyReveal`.

**Usul:** pakai pola yang sama: tampilkan indikator hanya bila proses lewat 300 ms, lalu fade 160 ms. Satu helper kecil `rememberBusyReveal(busy)` bisa dipakai bersama `FarmFormBar`.

### 8. Hero Keuangan: kolom kas muncul saat greenhouse dipilih

`FinancialEntryScreen.kt`, item `finance-workspace`

Memilih greenhouse pencatatan menambah label bulan dan `CashColumns`, sehingga tinggi hero berubah dalam satu frame.

**Usul:** `animateContentSize(tween(FarmMotion.State))` pada isi hero.

### 9. Warna chip berganti tanpa transisi, padahal tab sudah halus

`feature/history/HistoryCanopy.kt:55`, `core/design/CanopyKit.kt:263`

`CanopyTypeChips` dan `FarmChoiceChips` mengganti warna wadah dan teks dalam satu frame, sedangkan `FarmSegmentedTabs` sudah memakai `animateColorAsState` 200 ms.

**Usul:** `animateColorAsState(tween(FarmMotion.State))` untuk warna wadah dan teks, supaya pilihan chip terasa sama dengan tab.

## P3: butuh keputusan pemilik dulu

### 10. Container transform dari kartu greenhouse ke halaman detail

`ManagerHomeCanopy.kt:151` (`GreenhouseCard`) ke `ManagerDetailCanopy.kt:28` (`GreenhouseHero`)

Ini satu-satunya tempat di area pengelola yang pantas menjadi momen motion yang dirancang khusus: kartu adalah asal ruang halaman detail, dan Material menyebut pola ini container transform. Sekarang hanya ada fade alpha 0,78 ke 1 selama 200 ms (`ManagerHomeScreen.kt:80`).

**Hambatan:** `DESIGN.md:280` sengaja memutuskan bahwa pergantian cakupan greenhouse di Beranda pengelola tetap tanpa animasi, dan `FinanceDestination` memetakan daftar maupun detail ke `Tab(0)`. `SharedTransitionLayout` juga harus hidup berdampingan dengan lapisan beku `FarmDestinationMotion`. **Jangan dikerjakan tanpa keputusan eksplisit untuk mengubah aturan itu.** Kalau disetujui, kerjakan sebagai `sharedBounds` antara permukaan kartu dan hero forest, 300 sampai 400 ms, dengan potongan langsung saat "Hapus animasi" aktif.

## Jangan ditambah

- **Angka yang menghitung naik** pada kas, kg, HSS/HST atau PPM. `DESIGN.md` melarangnya, dan pada data keuangan angka yang sedang bergerak bisa terbaca salah.
- **Batang proporsi kas yang tumbuh.** Animasi ini menyiratkan perubahan yang sebenarnya tidak terjadi.
- **Stagger di Kelola, Keuangan atau daftar Riwayat.** Kelola adalah menu statis yang sering dibuka; animasi masuk berulang hanya memperlambat.
- **Shimmer, loop tak berhingga, bounce pada Catat, atau parallax motif daun saat gulir.**
- **Mengubah dock, transisi shell atau Back prediktif.** Bagian ini sudah selesai dan konsisten.

## Aturan pelaksanaan

- Semua durasi diambil dari `FarmMotion`, tanpa angka baru di layar.
- API Compose (`animateItem`, `AnimatedContent`, `AnimatedVisibility`, `animateContentSize`, `animate*AsState`) sudah mengikuti skala animasi sistem. Dengan "Hapus animasi" aktif, hasilnya potongan langsung, sama seperti aturan shell.
- Animasi yang keluar dibuat lebih cepat daripada yang masuk.
- Bukti: rekam layar emulator sebelum dan sesudah, sekali dengan animasi normal dan sekali dengan "Hapus animasi", pada 360 dp dan skala font 200%. Bukti mock atau fixture dicatat sebagai bukti mock atau fixture.

## Status implementasi 16 November 2026

Semua butir P1, P2 dan P3 dikerjakan. Pada P1.4 strukturnya berbeda dari usulan awal: hero dan chip Keuangan **tidak** dipindah keluar dari `key(kind)`. Sebagai gantinya, `HistoryScreen` mendapat `contentFade` sehingga hanya daftar di bawah chip yang memudar. Hero dan chip memang dibangun ulang, tetapi tampil di tempat yang sama tanpa animasi, jadi terlihat diam. Pilihan ini menghindari perubahan struktur besar.

| Butir | Lokasi utama |
|---|---|
| P1.1, P1.2 | `ManagerHomeScreen.kt` (`HomeItem`), `CanopyKit.kt` (`farmListItem`) |
| P1.3 | `GreenhouseWizard.kt` (`AnimatedContent` langkah dan layar selesai), `FormCanopy.kt` (`FarmStepIndicator`) |
| P1.4 | `HistoryScreen.kt` (`contentFade`), `FinanceRoot.kt` (chip Riwayat dan haptik), `FinancialEntryScreen.kt` (chip Keuangan dan haptik) |
| P1.5 | `ActivityVerificationScreen.kt` (`AnimatedVisibility` dan `BringIntoViewRequester`) |
| P2.6 | `SeasonReportScreen.kt`, `InvestmentScreen.kt`, `HistoryScreen.kt` |
| P2.7 | `CanopyKit.kt` (`rememberBusyReveal`, `FarmBusyReveal`), dipakai di Evaluasi musim, Investasi, Rekap panen dan Kelola |
| P2.8 | `FinancialEntryScreen.kt` (hero `animateContentSize`) |
| P2.9 | `CanopyKit.kt` (`FarmChoiceChips`), `HistoryCanopy.kt` (`CanopyTypeChips`) |
| P3 | `managerhome/GreenhouseOpening.kt`, `ManagerHomeCanopy.kt` (kartu mencatat posisinya), `FinanceRoot.kt` (`rememberGreenhouseOpening` di atas `SaveableStateProvider`) |

**Keputusan P3.** `SharedTransitionLayout` tidak dipakai. Beranda dibangun ulang untuk setiap cakupan greenhouse (`SaveableStateProvider("manager-home-$gh")`), sehingga kartu dan hero tidak pernah hidup bersamaan dalam satu komposisi, dan isi lama tidak boleh tampil dengan data cakupan baru. Yang dipakai adalah container transform manual: posisi kartu dicatat saat diketuk, lalu hero di halaman baru digambar dari posisi dan ukuran kartu menuju tempatnya. Arah kembali (hero mengecil ke kartu) **tidak** dibuat. Back tetap berupa potongan, seperti sebelumnya.

**Bukti:**

| Pemeriksaan | Hasil | Label |
|---|---|---|
| `assembleDebug`, `compileDebugAndroidTestKotlin` | lulus | diuji |
| `testDebugUnitTest` | 119 tes, 0 gagal | diuji |
| `lintDebug` | 0 error, 18 warning; satu warning baru dari kode ini sudah diperbaiki | diuji |
| `FinanceEntryUiTest` penuh di emulator API 30 | 65 tes, 16 gagal: 15 sama persis dengan daftar baseline di [triase Q1](UI_TEST_TRIAGE_EVIDENCE_20261115.md), ditambah `narrowAndLandscapeAtTwoHundredPercent…` yang gagal karena `Detected multithreaded access to SnapshotStateObserver` (flake yang sudah tercatat) dan lulus 2/2 saat diulang sendiri. Tidak ada tes baseline yang berubah dari lulus menjadi gagal secara konsisten | fixture, bukan backend nyata |
| Tampilan transform, wizard dan fade dilihat di layar | belum. Tes Beranda pengelola yang membuka greenhouse termasuk 15 tes yang gagal di baseline, jadi jalur ketuk kartu belum dijalankan oleh tes mana pun | belum diverifikasi |
| "Hapus animasi", 360 dp, font 200 %, TalkBack, perangkat fisik | belum | belum diverifikasi |
