# Audit frontend: responsivitas, gerak, dan rasa hidup UI (login, pengelola, pekerja)

Tanggal 14 November 2026. Basis kode: GitHub `main` `9da7308` (setelah PR #58). Audit memakai skill `impeccable` (referensi `audit.native.md`, `android.md` dan `animate.md` di folder skill lokal) dan `antislop-ui` (UI Skill Checklist).

**Batas bukti:** semua temuan di bawah berasal dari pembacaan source dan hitungan pola kode di `android/app/src/main`. Tidak ada emulator, perangkat fisik, profil frame, atau TalkBack pada audit ini. Angka durasi diambil dari kode dan `DESIGN.md`, bukan dari pengukuran. Temuan yang butuh layar untuk dipastikan ditandai *perlu dilihat*.

## Ringkasan

| # | Dimensi | Skor | Temuan kunci |
|---|---|---|---|
| 1 | Aksesibilitas | 3 | Semantik dan live region kuat; tab segmen 44 dp (di bawah 48 dp); TalkBack nyata belum pernah dijalankan |
| 2 | Performa | 2 | Tanpa Baseline Profile; layar penuh diganti spinner setiap simpan; root 1.569 baris dengan ~40 state dalam satu `when` |
| 3 | Tampilan dan tema | 3 | Token Canopy rapi; tema jendela, ikon peluncur dan warna aksen XML masih memakai hijau lama `#006C49`; tidak ada `values-night` |
| 4 | Kesesuaian platform | 3 | Material 3, inset, IME, Back dan snackbar benar; perpindahan layar tanpa transisi; 6 kontrol tanpa ripple; Back prediktif tanpa pratinjau dalam aplikasi |
| 5 | Adaptivitas | 3 | IME, landscape dan font besar ditangani; lebar dibatasi 900 dp; tanpa navigation rail untuk tablet (target memang ponsel) |
| **Total** | | **14/20** | **Good**: fondasinya benar, dan rasa "kaku" datang dari celah transisi dan umpan balik, bukan dari desain dasar |

**Verdict kesesuaian platform: PASS.** Aplikasi terasa native Android: Material 3, edge-to-edge dengan inset, `imePadding`, `BackHandler`, sheet dan dialog Material, snackbar, serta skala font yang dihormati. Rasa kaku muncul dari empat hal yang bisa diperbaiki tanpa mengganti arah visual Canopy:

1. Perpindahan antarlayar dipotong tanpa transisi (hard cut).
2. Setiap simpan mengganti seluruh layar dengan spinner.
3. Daftar tidak menganimasikan baris yang masuk atau keluar.
4. Tidak ada haptik dan tidak ada ripple pada beberapa kontrol utama.

Jumlah temuan: P0 0, P1 6, P2 11, P3 6.

## Hitungan pola kode (bukti)

| Pola | Jumlah | Catatan |
|---|---|---|
| `LazyColumn` / `items(key=…)` | 8 / 14 dari 19 | Daftar utama tervirtualisasi dan berkunci |
| `verticalScroll` | 15 | Admin, wizard, form; daftar baris admin di dalam `verticalScroll` |
| `Modifier.animateItem` | **0** | Baris tidak beranimasi saat masuk, keluar, atau berpindah |
| `AnimatedContent` / `AnimatedVisibility` / `Crossfade` | 2 / 2 / 0 | Hanya di login dan sheet periode |
| `animateContentSize` | 18 | Banyak dipakai untuk bagian yang terbuka/tertutup (baik) |
| `rememberInfiniteTransition` | 0 | Sesuai aturan "tanpa animasi tak berhingga" |
| `HapticFeedback` | **0** | Tidak ada umpan balik sentuh |
| `PullToRefresh` | 1 layar | Hanya Beranda pengelola |
| `derivedStateOf` | 0 | Beberapa turunan state dihitung ulang tiap recomposition |
| `collectAsStateWithLifecycle` | 20 (`collectAsState` 0) | Benar |
| `Dispatchers.IO` | 102 | Baca cache dan DB di luar thread utama (baik) |
| `semantics` / `liveRegion` | 66 / 15 | Fondasi TalkBack kuat |
| `clickable(…, null, …)` (tanpa ripple) | 6 | Dock, Catat, kartu greenhouse pengelola, kotak sheet Catat |
| Baseline Profile / `profileinstaller` | **tidak ada** | Lihat P1-1 |

## Temuan P1

**[P1-1] Tanpa Baseline Profile: start dingin dan gulir pertama lebih berat di ponsel kelas menengah.**
Lokasi: `android/app/build.gradle.kts` (tidak ada `androidx.profileinstaller` atau modul baseline profile). Kategori: Performa.
Dampak: Compose berjalan dalam mode interpretasi/JIT sampai ART selesai mengompilasi. Ponsel pekerja (Android 10 ke atas, sering kelas menengah) akan terasa lambat saat membuka aplikasi dan saat menggulir pertama kali. Ini sumber "tidak responsif" terbesar yang tidak terlihat di emulator laptop.
Saran: tambahkan `androidx.profileinstaller` dan modul `:baselineprofile` (Macrobenchmark) yang merekam alur login, Beranda pekerja, Beranda pengelola, Riwayat dan buka form. Ukur start dingin dan frame jank sebelum dan sesudah di satu perangkat fisik. Ini dependency baru, jadi perlu persetujuan pengguna.
Perintah: `/impeccable optimize`.
Status 15 November 2026: `profileinstaller`, modul `:baselineprofile` dan profil start/login terpasang; angka emulator tidak menunjukkan beda yang melampaui derau; layar setelah login dan perangkat fisik belum. [Bukti F1](FRONTEND_PERFORMANCE_EVIDENCE_20261115.md).

**[P1-2] Setiap simpan mengganti seluruh layar dengan spinner.**
Lokasi: `feature/FinanceRoot.kt` cabang `asset.entry.busy || sale.busy || activity.busy || harvest.busy || financial.busy -> Column { CircularProgressIndicator(); Text("Menyimpan isian ke perangkat…") }`, serta `runPhase == 4` ("Mengirim catatan…"). Kategori: Performa, Kesesuaian platform.
Dampak: simpan ke penyimpanan lokal biasanya cepat, jadi form hilang, spinner berkedip, lalu layar berikutnya muncul. Pengguna kehilangan konteks dan merasa aplikasi "loncat". Ini kebalikan dari aplikasi offline-first yang terasa instan.
Saran: biarkan form atau langkah Periksa tetap terlihat. Tombol utama masuk ke status sibuk (label "Menyimpan…" dengan indikator kecil, seperti tombol Masuk), dan kontrol lain dinonaktifkan. Tampilkan indikator baru setelah sekitar 300 ms supaya simpan cepat tidak berkedip. Setelah selesai, pindah layar dengan transisi (P1-3) dan snackbar yang sudah ada.
Perintah: `/impeccable harden`, `/impeccable animate`.
Status 15 November 2026: selesai untuk bagian tanpa layar spinner dan tombol sibuk di tempat; transisi setelah simpan menunggu P1-3. [Bukti F2](FRONTEND_HARDEN_EVIDENCE_20261115.md).

**[P1-3] Perpindahan layar dipotong tanpa transisi.**
Lokasi: `FinanceRoot.kt` (rantai `when` sekitar baris 822–960 memilih layar dari ~40 flag `rememberSaveable`). Hanya tab pekerja (alpha 0,74→1 dan naik 5 dp, 180 ms) dan langkah Periksa (`FarmReviewEntrance`) yang punya gerak. Kategori: Kesesuaian platform.
Dampak: membuka greenhouse dari kartu, membuka form dari sheet Catat, membuka detail dari Riwayat, dan kembali, semuanya hard cut. Pengguna tidak melihat hubungan ruang "masuk ke dalam" dan "kembali keluar". Ini penyebab utama rasa kaku.
Saran: turunkan satu kunci tujuan dari state yang ada, misalnya `sealed interface FinanceDestination` (Home, Greenhouse(id), Form(kind), Review(kind), Detail(kind,id), History, Finance, Admin…), tanpa merombak state. Bungkus area isi dengan satu `AnimatedContent(targetState = destination)`. Pola Material:
- **shared axis X** (geser 30 dp dan fade, 300 ms masuk, 250 ms keluar) untuk masuk ke kedalaman dan kembali;
- **fade-through** (keluar 90 ms, masuk 210 ms dengan skala 0,92→1) untuk antartab;
- **fade** 150 ms untuk sheet ke form.
Konten yang keluar harus memakai snapshot tujuan lamanya, jangan membaca state terbaru, supaya tidak berkedip ke isi yang salah saat keluar. Jangan menganimasikan pergantian cakupan greenhouse di Beranda pengelola karena isinya harus langsung terikat.
Perintah: `/impeccable animate`.
Status 15 November 2026: selesai; shared axis X, fade-through dock, gambar beku untuk layar keluar, cakupan Beranda pengelola tidak dianimasikan. [Bukti F3](FRONTEND_MOTION_EVIDENCE_20261115.md).

**[P1-4] Kontrol utama tanpa ripple.**
Lokasi: `CanopyKit.kt:178` (dock), `:338` (Catat), `ManagerHomeCanopy.kt:179` (kartu greenhouse), `ManagerSheets.kt:105,121,159` (kotak sheet Catat dan pilihan greenhouse). Semuanya `clickable(interaction, null, …)`. Kategori: Kesesuaian platform.
Dampak: umpan balik tekan hanya skala 0,97–0,98, yang sulit terlihat di bawah jari di layar terang luar ruang. Material 3 mengharapkan ripple sebagai tanda sentuhan diterima.
Saran: pakai `ripple()` Material 3 (terbatas pada bentuk kartu, warna dari peran tema) bersama skala yang sudah ada. Pertahankan `farmPressable`.
Perintah: `/impeccable polish`.

**[P1-5] Login tanpa dukungan autofill.**
Lokasi: `feature/auth/FinanceLogin.kt:174–202`. Kategori: Aksesibilitas, Kesesuaian platform.
Dampak: pengelola program dan password manager tidak bisa mengisi email dan password. Sesi berlaku 24 jam, jadi pekerja akan sering mengetik password 12 karakter di lapangan.
Saran: tambahkan `semantics { contentType = ContentType.EmailAddress }` (atau `Username`) dan `ContentType.Password` (Compose UI 1.8 ke atas, BOM 2025.08.01 sudah mencukupi). Jangan menyimpan password sendiri.
Perintah: `/impeccable harden`.
Status 15 November 2026: petunjuk autofill terpasang dan diuji lewat semantik; belum diuji dengan password manager yang berisi akun. [Bukti F2](FRONTEND_HARDEN_EVIDENCE_20261115.md).

**[P1-6] Daftar tidak menganimasikan baris yang berubah.**
Lokasi: `HistoryScreen.kt:162`, `ActivityVerificationScreen.kt:187,332`, `QueueScreen.kt:146`, daftar Beranda. `animateItem` = 0. Kategori: Kesesuaian platform.
Dampak: saat pengelola menekan "Sesuai", baris hilang seketika dan baris di bawahnya meloncat ke atas. Hal yang sama terjadi saat catatan terkirim di sheet Belum terkirim. Pengguna sulit memastikan aksinya berhasil.
Saran: tambahkan `Modifier.animateItem()` (fade dan placement bawaan) pada baris yang sudah berkunci. Untuk inbox Cek catatan, beri jeda 150 ms dengan chip keputusan tampil sebelum baris keluar, supaya hasil keputusan terlihat dulu.
Perintah: `/impeccable animate`.
Status 15 November 2026: sebagian; `animateItem` terpasang pada Riwayat, Cek catatan, Belum terkirim, koreksi dan kartu pengelola; jeda chip 150 ms dan kartu greenhouse pekerja belum. [Bukti F3](FRONTEND_MOTION_EVIDENCE_20261115.md).

## Temuan P2

**[P2-1] Tidak ada haptik.** Tambahkan `LocalHapticFeedback` dengan tipe Compose (`Confirm` saat catatan diterima server atau keputusan "Sesuai", `Reject` saat login gagal atau validasi menolak, `SegmentTick` saat tab atau chip berganti, `LongPress` bila ada). Hanya pada momen bermakna, tidak pada setiap ketukan. Hormati setelan getar sistem (API bawaan sudah begitu). `/impeccable delight`.

**[P2-2] Tarik untuk memperbarui hanya ada di Beranda pengelola.** Beranda dan Riwayat pekerja, serta Riwayat dan Keuangan pengelola, memakai tombol "Perbarui". Tambahkan `PullToRefreshBox` yang memicu sinkronisasi yang sama dan mempertahankan tombol untuk aksesibilitas. `/impeccable adapt`.

**[P2-3] Status memuat Beranda tanpa ruang cadangan.** `WorkerGreenhouseCards.kt:190` menampilkan spinner dan "Memuat data perangkat…", lalu kartu muncul dan mendorong isi ke bawah. Pakai placeholder statis berbentuk kartu (tanpa shimmer berulang) setinggi kartu nyata, lalu crossfade 200 ms ke kartu. Cache lokal cepat, jadi cukup tampilkan placeholder bila pemuatan lebih dari 150 ms. `/impeccable harden`. Status 15 November 2026: selesai untuk Beranda pekerja; Beranda pengelola tidak diubah. [Bukti F2](FRONTEND_HARDEN_EVIDENCE_20261115.md).

**[P2-4] Back prediktif tanpa pratinjau dalam aplikasi.** `targetSdk` 37 sudah mengaktifkan Back prediktif, tetapi layar dalam (halaman greenhouse, detail, form) memakai `BackHandler` biasa sehingga tidak ada pratinjau saat gesture ditahan. Untuk halaman greenhouse dan detail, pakai `PredictiveBackHandler` yang menggerakkan skala 1→0,92 dan geser tepi sesuai progres gesture. Batal mengembalikan keadaan dengan spring. Jangan pasang pada form yang punya draf belum tersimpan sebelum perilaku simpan otomatisnya jelas. `/impeccable animate`. Status 15 November 2026: terpasang untuk halaman greenhouse pekerja dan detail catatan; pratinjau belum terlihat karena emulator Android 11 tidak mengirim progres gesture. [Bukti F3](FRONTEND_MOTION_EVIDENCE_20261115.md).

**[P2-5] Root Compose monolitik.** `FinanceRoot.kt` 1.569 baris dengan ~40 flag state dan baris kode panjang. Setiap perubahan state membaca ulang seluruh `when`. Turunkan kunci tujuan (P1-3) dengan `derivedStateOf`, pindahkan cabang besar ke composable sendiri dengan parameter stabil, dan hindari membuat lambda baru dan `mapNotNull` di dalam cabang (contoh: `NutritionRunItem` dibangun di dalam `when`). Ini juga mempermudah transisi. `/impeccable optimize`. Status 15 November 2026: sebagian; daftar nutrisi diingat (F1), kunci tujuan `FinanceDestination` dipakai F3, pemecahan cabang belum. [Bukti F1](FRONTEND_PERFORMANCE_EVIDENCE_20261115.md).

**[P2-6] Turunan dihitung ulang setiap recomposition.** `rememberWorkerHome` membangun `stamp` dari semua greenhouse, musim, kelompok, jadwal dan target pada setiap recomposition (`WorkerGreenhouseCards.kt:120`). Bungkus dengan `remember(data)` atau `derivedStateOf`. `/impeccable optimize`. Status 15 November 2026: selesai. [Bukti F1](FRONTEND_PERFORMANCE_EVIDENCE_20261115.md).

**[P2-7] Daftar admin tidak tervirtualisasi dan disaring di komposisi.** `AdminScreen.kt:96,182`: baris dirender lewat `forEach` di dalam `verticalScroll`, dan penyaringan pencarian berjalan setiap ketukan. Untuk akun dan master yang bisa mencapai ratusan baris, pindah ke `LazyColumn` dengan `key`, dan saring dengan `remember(rows, search)`. `/impeccable optimize`. Status 15 November 2026: selesai. [Bukti F1](FRONTEND_PERFORMANCE_EVIDENCE_20261115.md).

**[P2-8] Tab segmen 44 dp.** `CanopyKit.kt` `FarmSegmentedTabs`: `heightIn(min = FarmSpace.Touch - 4.dp)`. Naikkan ke 48 dp atau perluas area sentuh dengan `minimumInteractiveComponentSize()`. `/impeccable adapt`.

**[P2-9] Tema jendela dan splash tidak menyambung ke Canopy.** `res/values/styles.xml` memakai `Theme.Material.Light` dengan `colorAccent #006C49` (hijau lama), tanpa `windowBackground`, tanpa `values-night`, dan tanpa atribut splash Android 12. Saat start dingin muncul jendela terang polos, lalu login dengan kanopi forest. Pada mode gelap ada kilatan putih. Set `android:windowBackground` dan atribut splash (`windowSplashScreenBackground` forest `#132820`, ikon peluncur) supaya splash mengalir langsung ke kanopi login. Ini momen pembuka yang paling murah untuk dibuat hidup. `/impeccable polish`.

**[P2-10] Ikon peluncur di luar palet dan bukan adaptive icon.** `res/drawable/ic_inofarm.xml` memakai `#006C49` dan `#BDF0CD`, tidak ada `mipmap-anydpi-v26`. Buat adaptive icon (latar `brand #2B503D` atau forest, foreground daun/greenhouse sage) beserta monochrome untuk ikon bertema Android 13. `/impeccable polish`.

**[P2-11] Momen fokus "terkirim" belum dirancang.** Inti kepercayaan aplikasi offline ini adalah saat catatan pindah dari "Belum terkirim" ke "Terkirim". Sekarang hanya ada teks dan snackbar. Usulan satu momen fokus: indikator `N belum terkirim` di top bar menyusut dengan hitungan turun (tanpa animasi angka berjalan; cukup crossfade per nilai), ikon sinkron berputar **hanya selama sinkronisasi benar-benar berjalan** lalu berhenti, dan saat antrean kosong chip berubah menjadi centang sage selama 1,5 detik lalu menghilang dengan fade. Ini terikat ke state nyata, jadi tidak melanggar aturan tanpa animasi tak berhingga. `/impeccable delight`.

## Temuan P3

- **[P3-1]** `farmStaggerEntrance` memakai `delay(index * step)` yang tidak ikut skala animasi sistem. Saat "Hapus animasi" aktif, item tetap menunggu hingga sekitar 200 ms lalu muncul seketika. Kalikan jeda dengan skala durasi animator, atau lewati jeda bila skalanya 0.
- **[P3-2]** Warna mentah yang menduplikasi token: `Color(0xFF203B30)`, `0xFF263F34`, `0xFF385442`, `0xFF493519` di `CanopyKit.kt` dan `FarmChrome.kt:399`. Pindahkan ke `FarmColors`/`ForestScheme`.
- **[P3-3]** `background_login.jpg` (27 KB) tidak dipakai lagi. `shrinkResources` membuangnya di rilis, tetapi sebaiknya dihapus dari source.
- **[P3-4]** Login: email terakhir tidak diingat antarsesi. Pertimbangkan mengisi email terakhir (bukan password), dengan pilihan "Bukan saya" untuk ponsel bersama. Status 15 November 2026: selesai sesuai keputusan G7. [Bukti F2](FRONTEND_HARDEN_EVIDENCE_20261115.md).
- **[P3-5]** Login: kanopi menyusut saat keyboard terbuka lewat `animateDpAsState` yang dibaca di komposisi, sehingga layout dihitung ulang tiap frame selama 220 ms. Masih wajar; bisa dipindah ke `Modifier.layout` atau `graphicsLayer` bila profil menunjukkan jank.
- **[P3-6]** Tanpa navigation rail untuk layar lebar. Target saat ini ponsel, jadi cukup dicatat.

## Halaman login: penilaian khusus

**Yang sudah baik:**
- Kanopi forest dengan motif daun dan wordmark masuk bertahap (60 ms per elemen) sekali per proses.
- Kanopi menyusut saat keyboard terbuka.
- Label tombol berganti ke "Memproses…" dengan crossfade 160 ms.
- Getar visual ±6 dp saat galat.
- Live region pada status, toggle password berlabel, `imePadding`, dan `ImeAction` Next/Done.
- Email di-*trim* dan di-*lowercase* sebelum dikirim.
- Status bar dipaksa ikon terang di atas forest, lalu dikembalikan saat keluar.

**Yang perlu diperbaiki, berurutan:**
1. Autofill (P1-5).
2. Splash dan jendela yang menyambung ke kanopi (P2-9).
3. Haptik `Reject` saat galat, dipasangkan dengan getar visual yang sudah ada (P2-1).
4. Transisi login ke shell: sekarang fade 220 ms. Pertimbangkan kanopi login yang "mengecil" menjadi hero Beranda (bidang forest yang sama, tinggi 47% layar menjadi tinggi hero) dengan `animateDpAsState` pada tinggi bidang forest selama 400 ms. Ini focal moment yang lahir dari arah Canopy (kanopi di atas, bidang solid di bawah), bukan efek umum.
5. Email terakhir (P3-4).

## Arah gerak (motion thesis) untuk Canopy

- **Focal moment:** kanopi forest sebagai satu bidang yang berpindah. Login menyusut ke hero Beranda, kartu greenhouse membuka halaman greenhouse dengan hero forest tumbuh dari kartu (container transform sederhana: bentuk dan posisi kartu ke hero, 350 ms, `FastOutSlowIn`). Satu momen per alur, bukan di setiap layar.
- **Kontinuitas:** shared axis X untuk masuk/keluar kedalaman, fade-through untuk tab, `animateItem` untuk baris, `animateContentSize` yang sudah ada untuk bagian yang terbuka/tertutup.
- **Umpan balik:** ripple dan skala tekan, haptik pada momen bermakna, tombol sibuk di tempat (bukan layar spinner), dan chip status yang crossfade saat berubah.
- **Anggaran:** hanya `graphicsLayer` (alpha, translasi, skala) dan ukuran kartu. Tanpa blur, tanpa shimmer berulang, tanpa animasi angka. Durasi mengikuti `FarmMotion`: tekan 120 ms, status 200 ms, navigasi 220–300 ms, focal moment maksimal 400 ms. Keluar lebih cepat dari masuk.
- **Aturan yang tetap:** tidak ada animasi tak berhingga; ikon sinkron hanya berputar saat sinkronisasi berjalan; angka dan nominal tidak dianimasikan; gerak masuk sekali per proses; "Hapus animasi" di sistem menghasilkan cut atau crossfade yang tetap menunjukkan perubahan state.

## UI Skill Checklist antislop-ui

| Butir | Hasil | Bukti |
|---|---|---|
| Palet dari DESIGN.md | PASS dengan catatan | Token Canopy dipakai; ikon peluncur dan tema XML masih hijau lama (P2-9, P2-10) |
| Aksen hanya di momen kunci | PASS | Sage untuk Catat, progres dan pil terpilih |
| Tanpa emoji dekoratif | PASS | Tidak ditemukan di teks UI |
| Ritme bervariasi | PASS | Hero forest, kartu, chip dan daftar berbeda per peran layar |
| Bebas bentuk AI default | PASS | Tanpa bento, terminal palsu, atau garis kiri dekoratif; garis amber pada jadwal dilewati menandai state nyata |
| Setiap kontrol punya tujuan | PASS dari kode | Tes UI menjalankan alur utama; belum ada klik menyeluruh di perangkat |
| Gerak sesuai dial dan bertujuan, tanpa loop | PASS | `rememberInfiniteTransition` = 0 |
| Glass/glow/shadow/radius dalam batas | PASS | Bayangan hanya dock dan angkat hover 2 dp; radius dari `FarmShape` |
| Titik status menandai state nyata | PASS | Chip status selalu bertulisan |
| Layar app dibangun dari keputusan pengguna | PASS | Beranda pekerja diurutkan dari greenhouse yang belum selesai; pengelola mulai dari kas dan cek catatan |
| Angka nyata, bukan karangan | PASS | Data dari cache dan server; contoh hanya di fixture tes |
| Status kosong/memuat/galat menyebut sebab dan aksi | PASS dengan catatan | Ada, tetapi memuat Beranda tanpa ruang cadangan (P2-3) |
| Tahan di semua ukuran, tema dan state | Perlu dilihat | 360 dp, 200% dan gelap belum lengkap (lihat `DESIGN.md` Celah terbuka) |
| Media berukuran, metrik terukur | PASS | Tidak ada metrik performa yang diklaim; audit ini tidak mengukur |

## Hal yang sudah bekerja baik (pertahankan)

- `collectAsStateWithLifecycle` di semua tempat; baca cache dan DB di `Dispatchers.IO`.
- Daftar utama memakai `LazyColumn` dengan `key`.
- `farmPressable` dan `farmStaggerEntrance` membaca state animasi di dalam lambda `graphicsLayer` (fase gambar), sehingga tidak memicu recomposition per frame.
- Gerak masuk sekali per proses dengan batas enam item; pil dock dan tab tidak beranimasi pada tata letak pertama.
- Status ditulis dengan teks, warna hanya penanda; live region untuk status simpan dan antrean.
- Skala label dock dibatasi 1,3 supaya dock tidak pecah pada font besar.

## Urutan perintah yang disarankan

1. **[P1] `/impeccable optimize`**: Baseline Profile dan pengukuran start dingin (P1-1), lalu root dan turunan state (P2-5, P2-6, P2-7).
2. **[P1] `/impeccable harden`**: simpan tanpa layar spinner (P1-2), autofill login (P1-5), placeholder Beranda (P2-3).
3. **[P1] `/impeccable animate`**: tujuan dan transisi shared axis/fade-through (P1-3), `animateItem` (P1-6), Back prediktif (P2-4).
4. **[P2] `/impeccable delight`**: haptik (P2-1) dan momen "terkirim" (P2-11).
5. **[P2] `/impeccable adapt`**: tarik untuk memperbarui (P2-2) dan target sentuh 48 dp (P2-8).
6. **[P2] `/impeccable polish`**: ripple (P1-4), splash, tema jendela dan adaptive icon (P2-9, P2-10), token warna (P3-2), lalu pass akhir.

Setelah perbaikan, jalankan ulang `/impeccable audit` dengan bukti emulator (terang, gelap, font 200%, 360 dp) dan satu perangkat fisik.
