# Audit tampilan login dan pengelola, arah visual "Canopy"

Disusun 11 November 2026 di branch `codex/worker-flow-docs`, HEAD `06aff0f`. Dokumen ini tidak mengubah runtime. Rencana pengerjaan ada di [MASTER_PROMPT_FRONTEND_CANOPY.md](MASTER_PROMPT_FRONTEND_CANOPY.md).

Skill yang dipakai: `impeccable` (launcher context berhasil, mode **Operate**, platform Android native) dan `antislop-ui`. Kontras dicek dengan `.claude/skills/antislop-human/contrast-check.py`.

## Batas pemeriksaan

| Sumber | Yang dilihat | Batas |
|---|---|---|
| Kode HEAD | `FinanceLogin.kt`, `ManagerHomeScreen.kt`, `FinanceRoot.kt` (shell, toolbar, FAB), `FinanceNavigation.kt`, `FarmChrome.kt`, `FarmMenu.kt`, `ManagerSheets.kt` (Catat, Kelola), `FinancialEntryScreen.kt` (hub Keuangan), `MeasurementDashboard.kt`, `BotanicalBackdrop.kt`, `InofarmTheme.kt`, `DESIGN.md`, `PRODUCT.md` | Penilaian layar pengelola berasal dari kode, bukan render |
| Emulator `emulator-5554` (API 29) | Beranda pekerja build debug 11 Nov (sesi pekerja aktif, backend tidak tersambung) dan build `performance` 9 Nov (navigasi lama 5 tab, sesi berakhir) | Layar pengelola versi super admin dan layar login **tidak** dirender. Saya tidak logout dan tidak login, supaya sesi dan database pengguna tidak tersentuh |
| Mockup | [Artifact "Inofarm Canopy"](https://claude.ai/artifact/WqvsjyHQbgbQnJKuzd3Cb4): Login, Beranda pengelola, Detail greenhouse, sheet Catat (11 Nov); Riwayat, Cek catatan pekerja, Keuangan, sheet Alat keuangan, Kelola, serta form Kegiatan, Pengukuran, Panen, Periksa, Pengeluaran, Penjualan lunas, Alokasi biaya, Perolehan aset, Wizard greenhouse langkah 2, Buat akun (12 Nov) | Angka, nama greenhouse, varietas dan nama pekerja adalah **data contoh** untuk tata letak |
| Kode tambahan (12 Nov) | `HistoryScreen.kt`, `ActivityVerificationScreen.kt` (daftar), `FinancialEntryScreen.kt` (hub dan sheet), `ManagerSheets.kt` (Catat, Kelola), `AdminForm.kt` (label data master), tag di `androidTest` | Sama: dinilai dari kode |

## Pilihan referensi

| | Referensi 1 (hijau forest + krem) | Referensi 2 (lime neon + hitam hijau) |
|---|---|---|
| Kecocokan identitas | Palet hampir sama dengan `DESIGN.md` (forest `#132820`, brand `#2B503D`, sage `#D4E4B7`, krem `#F4F3E8`). Hasilnya evolusi, identitas tetap | Mengganti identitas. Lime neon tidak ada di sistem warna |
| Keterbacaan lapangan (mode Operate) | Kontras tenang, angka besar, kartu solid | Lime sebagai latar teks putih gagal kontras. Huruf kapital condensed sulit dipindai untuk label panjang bahasa Indonesia |
| Tanda "AI slop" (antislop-ui) | Donut chart tanpa pertanyaan, delta "+12,6% vs bulan lalu" tanpa data, ilustrasi dompet 3D | Judul kapital besar generik, koin 3D dekoratif, aksen dipakai di semua tempat |

**Keputusan: Referensi 1 sebagai dasar.** Dari Referensi 2 hanya satu pola yang diambil: **bar navigasi gelap dengan tombol aksi utama di tengah**. Tombol tengah memakai sage yang sudah ada, bukan lime. Dari Referensi 1 tidak diambil donut chart, delta persen, onboarding/splash dan ilustrasi 3D.

Nama arah: **Canopy**. Bidang forest menjadi "kanopi" di atas (hero login, hero Beranda, hero detail, bar navigasi), dan isi kerja berada di bidang krem/surface yang solid di bawahnya.

## Temuan

Prioritas: **P1** mengganggu tugas atau membingungkan, **P2** melemahkan hierarki atau identitas, **P3** poles.

### Login

| ID | P | Temuan | Bukti | Saran |
|---|---|---|---|---|
| L-01 | P2 | Latar ilustrasi cat air stok (daun, bercak) di-crop 228 dp dengan overlay 36%. Kesannya kartu ucapan, tidak terkait greenhouse. Di tema gelap overlay 86% membuatnya hampir hilang | `BotanicalBackdrop.kt:22-33`, `FinanceLogin.kt:89`, `assets/background login.jpg` | Ganti dengan bidang forest penuh di atas (±47% tinggi) dan motif garis daun vektor yang digambar Canvas, alpha 0,3. Tajam di semua densitas, ikut tema |
| L-02 | P2 | Identitas hanya teks "Inofarm" sans sistem, tanpa marka | `FinanceLogin.kt:108` | Marka sederhana (rumah kaca + daun, garis 1,8 dp) di tile forest, wordmark 40–44 sp ExtraBold |
| L-03 | P2 | Tiga "Masuk" bertumpuk: judul aplikasi, judul panel "Masuk", tombol "Masuk". Panel forest kecil mengambang di atas krem sehingga tidak jelas mana fokus | `FinanceLogin.kt:107-113`, `:177` | Sheet krem naik dari bawah dengan sudut 28 dp, menumpuk 36 dp di atas kanopi. Judul "Masuk" + satu kalimat "Pakai akun yang dibuat pengelola." |
| L-04 | P3 | Field tanpa ikon depan dan tanpa placeholder. Label melayang di dalam field gelap | `FinanceLogin.kt:123-154` | Label di atas field (pola yang sudah dipakai saat font >1,5), ikon surat/gembok di depan, placeholder `email@contoh.com` |
| L-05 | P2 | Tombol Masuk nonaktif selama isian kosong, tanpa alasan terlihat | `FinanceLogin.kt:72-73`, `:167-171` | Pertahankan perilaku (tes mungkin bergantung), tetapi tambahkan teks bantu di bawah tombol saat nonaktif hanya jika uji pengguna menunjukkan kebingungan. Bukan prioritas fase ini |
| L-06 | P2 | Tidak ada jawaban untuk "lupa password". Produk memang tanpa reset publik | `CLAUDE.md` (Peran dan kewenangan) | Teks non-interaktif: "Lupa password? Minta pengelola mengatur ulang." |
| L-07 | P3 | Gerak hanya fade panel 240 ms. Gagal masuk tidak punya umpan balik selain teks | `FinanceLogin.kt:112`, `FarmMotion.LoginEntrance` | Masuk bertahap (marka, judul, sheet) 0/60/120 ms; tekan tombol skala 0,97; gagal masuk geser horizontal sekali 6 dp; teks tombol berganti ke indikator progres di dalam tombol |

### Shell dan navigasi pengelola

| ID | P | Temuan | Bukti | Saran |
|---|---|---|---|---|
| N-01 | P1 | Tombol Catat melayang di atas isi. Daftar perlu ruang tambahan 80 dp agar baris akhir tidak tertutup, dan tombol hanya tampil di Beranda/Riwayat | `FinanceRoot.kt:621`, `:657-662`, `:693` | Pindahkan Catat ke tengah bar navigasi pengelola (5 slot: Beranda, Riwayat, **Catat**, Keuangan, Kelola). Selalu terlihat saat navigasi tampil dan tidak ada penulisan berjalan |
| N-02 | P2 | Navigasi punya dua rupa tergantung perangkat: kaca tembus pada API ≥31, font ≤1,4, tab Beranda; opak di kondisi lain | `FinanceRoot.kt:631-632`, `FarmChrome.kt:46-61` | Bar forest solid tunggal untuk pengelola. Hilangkan cabang kaca untuk pengelola |
| N-03 | P2 | Toolbar hanya "Inofarm". Tanggal, sapaan dan nama tersembunyi | `FinanceRoot.kt:903-904` | Beranda: sapaan WIB ("Selamat pagi/siang/sore/malam") + tanggal; avatar inisial membuka menu akun. Tab lain: judul tab |
| N-04 | P3 | Indikator tab hanya memudarkan warna, tidak berpindah | `FarmChrome.kt:179-182` | Pill indikator bergeser antar-tab dengan spring |
| N-05 | P1 | Pengelola tidak punya cara memperbarui data kecuali saat panel gagal muncul | `FinanceRoot.kt:909` (tombol Perbarui hanya pekerja), `ManagerHomeScreen.kt:124-128` | Tarik ke bawah untuk memperbarui di Beranda (PullToRefreshBox, jika ada di versi Material3 terpasang) |

### Beranda (semua greenhouse)

| ID | P | Temuan | Bukti | Saran |
|---|---|---|---|---|
| B-01 | P1 | Judul header dan tombol sama-sama "Semua greenhouse" | `ManagerHomeScreen.kt:91`, `:96` | Header tidak lagi menjadi pemilih. Pemilihan dilakukan dengan mengetuk kartu greenhouse |
| B-02 | P2 | Header forest hanya berisi tombol, sedangkan angka terpenting (kas bulan ini) ada di paling bawah | `ManagerHomeScreen.kt:90-123`, `:343` | Hero forest = kas bulan WIB: Penerimaan tercatat dan Pengeluaran tercatat, jumlah catatan, batang perbandingan dua warna, tautan "Lihat keuangan". **Tanpa laba, tanpa delta**, karena manager-home hanya memberi bulan berjalan |
| B-03 | P2 | Kartu "N catatan pekerja perlu dicek" berbobot sama dengan panel lain | `ManagerHomeScreen.kt:136-143` | Kartu sage satu ketukan penuh, ikon tile forest, rincian "N kegiatan · N panen" bila datanya ada |
| B-04 | P1 | Kartu greenhouse berisi 5–6 caption bertumpuk; HSS/HST ditulis sebagai kalimat panjang | `ManagerHomeScreen.kt:147-159`, `PeriodFacts :373-394` | Tile status kiri (sehat / perlu pilih tanam / nonaktif), nama + "varietas · kelompok", angka HST besar di kanan, baris chip: fase, panen kg, waktu catatan terakhir, peringatan PPM bila ada |
| B-05 | P2 | Tombol "Pilih greenhouse untuk mencatat" mengulang fungsi kartu dan Catat | `ManagerHomeScreen.kt:161-164` | Hapus |
| B-06 | P2 | Tombol tersebar: Tambah greenhouse (header), Cek catatan (panel), Rekap panen (di Riwayat), Evaluasi (di Alat keuangan) | `ManagerHomeScreen.kt:100-105`, `:133-143`, `FinanceRoot.kt:853-860`, `FinancialEntryScreen.kt:116` | Baris pintasan 4 ikon: Cek catatan (lencana jumlah), Tambah greenhouse, Rekap panen, Evaluasi musim. Semua tujuan sudah ada |
| B-07 | P2 | Catatan terbaru dibuat dari TextButton berisi tiga caption, tanpa pembeda jenis | `ManagerHomeScreen.kt:319-332` | Baris daftar: tile ikon per jenis (pengukuran/kegiatan/panen), judul, "greenhouse · pencatat", waktu di kanan |
| B-08 | P2 | Temuan "PPM di luar acuan" memakai panel netral, sehingga tidak terbaca sebagai peringatan | `ManagerHomeScreen.kt:279` | Kartu amber (`FarmColors.WarningSurface`/`Warning`) dengan ikon segitiga dan teks status |

### Detail satu greenhouse

| ID | P | Temuan | Bukti | Saran |
|---|---|---|---|---|
| D-01 | P2 | Lima chip bagian membungkus ke dua baris di layar sempit | `ManagerHomeScreen.kt:363-369` | Tab segmen satu baris yang bisa digeser horizontal, indikator geser |
| D-02 | P1 | Empat jalan mencatat di Ringkasan (Catat pengukuran, Catat kegiatan, Catat panen, Catat panen lagi di panel panen) ditambah tombol Catat | `ManagerHomeScreen.kt:197-209`, `:236-237` | Hapus tombol catat sebaris. Pertahankan "Catat hasil" pada baris jadwal |
| D-03 | P2 | Hero detail: "Aktif" sebagai caption polos, umur sebagai kalimat | `ManagerHomeScreen.kt:113-121` | Chip "Aktif", tiga tile angka: HSS, HST, fase aktual. Tombol bergaris "Ganti tanam saat ini" |
| D-04 | P3 | Judul "Pengukuran manual" berada dalam pill latar dan status hari ini muncul terpisah di bawah kartu | `MeasurementDashboard.kt:79-82`, `ManagerHomeScreen.kt:182` | Judul bagian "Pengukuran hari ini" + jam WIB di kanan, chip status cek di bawah kartu |

### Sheet Catat

| ID | P | Temuan | Bukti | Saran |
|---|---|---|---|---|
| S-01 | P2 | Lima baris berikon seragam, tanpa pemisah kegiatan lapangan dan uang, tanpa keterangan greenhouse yang akan dipakai | `ManagerSheets.kt:31-58` | Dua kelompok: "Di greenhouse" (Kegiatan, Pengukuran, Panen; kotak 3 kolom) dan "Uang" (Biaya, Penjualan; kotak 2 kolom). Baris konteks "Greenhouse · Kelompok" di bawah judul bila ada |
| S-02 | P3 | Ikon tidak cocok: Panen memakai ikon greenhouse, Penjualan memakai ikon alokasi, Pengukuran memakai ikon laporan | `ManagerSheets.kt:20-24` | Ikon baru: keranjang (panen), tetes (pengukuran), garis naik (penjualan) |

### Riwayat (tab Riwayat pengelola)

| ID | P | Temuan | Bukti | Saran |
|---|---|---|---|---|
| R-01 | P2 | Dua judul bertumpuk: header "Kegiatan" dari label jenis, lalu kartu filter berjudul "Riwayat" | `HistoryScreen.kt:66-74` | Satu judul halaman "Riwayat" + tombol Filter di kanan |
| R-02 | P2 | Chip penyaring (Semua kegiatan, Pengukuran, Panen) dan chip pindah ke Keuangan (Biaya, Penjualan, Aset) bercampur dalam `FlowRow` yang membungkus | `ManagerSheets.kt:79-91` | Satu baris chip yang bisa digeser; chip pindah dipisah garis tipis dan bergaris putus dengan chevron |
| R-03 | P2 | Tombol "Filter riwayat" tidak menunjukkan berapa filter aktif; cakupan ditulis sebagai kalimat panjang. Pengelola mengetik tanggal `YYYY-MM-DD`, sedangkan pekerja mendapat picker | `HistoryScreen.kt:75-80`, `:213-231`, `:264-270` | Tombol "Filter" berlencana jumlah filter aktif; cakupan satu baris; picker tanggal Android untuk pengelola juga |
| R-04 | P1 | Setiap catatan menjadi kartu terpisah 3–5 baris tanpa ikon jenis dan tanpa pengelompokan hari. Pengukuran tidak menampilkan EC/PPM di daftar | `HistoryScreen.kt:157-194` | Baris dalam satu kartu per hari WIB ("Hari ini", "Kemarin", tanggal) dengan judul hari yang menempel; tile ikon per jenis; EC/PPM di baris pengukuran bila ada di body; waktu dan chip status di kanan |
| R-05 | P3 | Kartu "Diperbarui …" memakan satu kartu sendiri | `HistoryScreen.kt:87-96` | Digabung ke baris cakupan di bawah judul |

### Cek catatan pekerja

| ID | P | Temuan | Bukti | Saran |
|---|---|---|---|---|
| C-01 | P2 | Jenis (Kegiatan/Panen) dan Status memakai dropdown `Choice`; ganti status butuh dua ketukan | `ActivityVerificationScreen.kt:148-154` | Segmen dua pilihan untuk jenis, chip status yang bisa digeser |
| C-02 | P2 | Tanggal filter diketik `YYYY-MM-DD` | `ActivityVerificationScreen.kt:170-179` | Picker tanggal Android, filter di sheet |
| C-03 | P2 | Kartu teks berbobot sama; EC/PPM ditulis sebagai teks biasa | `ActivityVerificationScreen.kt:200-212` | Tile ikon per jenis, chip status di kanan, kotak angka EC/PPM besar; kotak PPM amber bila data acuan menyebut di luar acuan |
| C-04 | P3 | Tombol "Belum terkirim" di header mengulang penanda toolbar | `ActivityVerificationScreen.kt:143-144` | Turun menjadi tautan di akhir daftar; tag `verification-outbox` tetap karena dipakai `FinanceBackendDeviceTest.kt:357-358` |

### Keuangan

| ID | P | Temuan | Bukti | Saran |
|---|---|---|---|---|
| F-01 | P2 | Header memuat deskripsi, panel "Catatan baru", pemilih greenhouse dan dua tombol sebelum riwayat terlihat | `FinancialEntryScreen.kt:64-78` | Hero forest: pemilih greenhouse berbentuk pill, kas bulan ini untuk greenhouse itu, tombol "Catat pengeluaran" (sage) dan "Alat keuangan" (bergaris) |
| F-02 | P2 | Jenis riwayat dipilih dari dropdown lima opsi | `FinancialEntryScreen.kt:61-63` | Chip jenis yang bisa digeser: Pembayaran, Penjualan lunas, Alokasi biaya, Aset, Penyusutan |
| F-03 | P2 | Nominal tanpa arah: pembayaran dan penjualan terlihat sama | `HistoryScreen.kt:159-169` | Pembayaran `−Rp…` warna teks biasa, penjualan lunas `+Rp…` hijau brand, rata kanan. Alokasi, aset, penyusutan tanpa tanda karena bukan arus kas |
| F-04 | P3 | Sheet Alat keuangan berupa daftar panjang dengan keterangan panjang | `FinancialEntryScreen.kt:82-131` | Kelompok "Catat dan alokasi" (daftar), "Hitung hasil" (Evaluasi musim dan Analisis investasi sebagai dua kartu forest), "Pemeliharaan". Semua tag lama tetap, termasuk `sale-new` |

### Kelola

| ID | P | Temuan | Bukti | Saran |
|---|---|---|---|---|
| K-01 | P3 | Ikon tidak punya tile sehingga ritmenya datar | `ManagerSheets.kt:102-133` | Tile tonal 44 dp; Tambah greenhouse memakai tile `Brand` sebagai aksi utama. Data master tetap tanpa ikon (keputusan `DESIGN.md`) |
| K-02 | P3 | Identitas akun baru terlihat di akhir daftar | `ManagerSheets.kt:134-141` | Kartu identitas forest di atas (inisial, nama, peran); tombol Keluar di paling bawah dengan keterangan draf tetap tersimpan |
| K-03 | P3 | Jadwal dan Target dua baris biasa | `ManagerSheets.kt:127-133` | Dua kotak berdampingan |

### Form input (semua fitur)

| ID | P | Temuan | Bukti | Saran |
|---|---|---|---|---|
| I-01 | P1 | Pengelola mengetik tanggal dan jam sebagai teks `YYYY-MM-DD` dan `HH:mm` di semua form; pekerja sudah mendapat picker | `FinanceRoot.kt:974-975`, `HarvestScreen.kt:55-56`, `FinancialEntryScreen.kt:158-159`, `SaleEntryScreen.kt:45-46`, `AssetEntryScreen.kt:49`, `:68-69`, `GreenhouseWizard.kt:263`, `:267-268` | Isian teks tetap (tag tes tetap) dengan tombol kalender/jam di ujung yang membuka picker Android dan mengisi nilai; tanggal terbaca ("Rabu, 11 Nov 2026") di bawahnya |
| I-02 | P2 | Pilihan pendek memakai dropdown: jenis kegiatan (5), kondisi buah (2), jenis pembayaran (2), kelas biaya (2), peran (2) | `FinanceRoot.kt:964`, `HarvestScreen.kt:68`, `FinancialEntryScreen.kt:157`, `:171`, `AdminForm.kt:54` | Chip atau segmen. Kondisi Cacat memakai amber agar terbaca beda tanpa hanya warna (teksnya tetap tertulis) |
| I-03 | P2 | Semua isian sama besar; EC, PPM, nominal dan bobot tidak menonjol | `FinanceRoot.kt:985-987`, `FinancialEntryScreen.kt:148`, `HarvestScreen.kt:76-77` | Isian angka utama besar (24–34 sp, tnum) dengan satuan di dalam isian |
| I-04 | P2 | Tombol Periksa dan Simpan draf ada di akhir gulir; pada form panjang (bahan, rincian panen, sumber penjualan) pengguna harus menggulir ke bawah | `FinanceRoot.kt:1051-1052`, `HarvestScreen.kt:95`, `SaleEntryScreen.kt:71-72`, `AssetEntryScreen.kt:89-90`, `FinancialEntryScreen.kt:180-181` | Bar bawah menempel: Simpan draf (bergaris) dan Periksa (utama), di atas keyboard |
| I-05 | P2 | Bagian form berupa garis pemisah + judul sehingga form panjang terasa satu dinding | `FarmChrome.kt:238-244` (`FarmSection`) | Bagian dalam kartu surface radius 20; rincian berulang (bahan, grade, sumber panen) dalam kartu sendiri dengan tombol hapus ikon di kanan |
| I-06 | P3 | Total panen dan total penjualan tidak terlihat saat mengisi | `HarvestScreen.kt:62-80`, `SaleEntryScreen.kt:53-58` | Kartu total: panen menjumlah buah dan kg dari isian; penjualan memakai `SaleDraft.preview()` (`SaleDraft.kt:19`) yang sudah ada. Disebut "Total isian", bukan nilai resmi |
| I-07 | P3 | Langkah Periksa berupa panel fakta polos | `FinanceRoot.kt:1057-1083`, `HarvestScreen.kt:103-124` | Kartu ringkasan dengan kepala forest (jenis, greenhouse, waktu), angka utama besar, pasangan label-nilai; tombol Ubah isian dan Simpan |
| I-08 | P3 | Wizard menulis "Langkah N dari 3" sebagai teks saja | `GreenhouseWizard.kt:249` | Indikator tiga segmen di atas + teks yang sama (live region tetap) |

### Catatan tes yang ditemukan saat audit

`FinanceBackendDeviceTest.kt:619` dan `:687` menekan `finance-tab-4`, padahal bar pengelola sekarang hanya punya `finance-tab-0..3` (`FinanceNavigation.kt:41-45`). Tes perangkat itu kemungkinan sudah tidak cocok sejak navigasi empat tab. Ini temuan dari membaca kode, belum dijalankan.

### Tipografi dan gerak

| ID | P | Temuan | Bukti | Saran |
|---|---|---|---|---|
| T-01 | P2 | Seluruh peran memakai `FontFamily.SansSerif`; angka hero maksimal 32 sp | `InofarmTheme.kt:84-116` | Plus Jakarta Sans (OFL, dirancang Tokotype di Indonesia: alasan merek untuk produk Jogja). **Disetujui pengguna 11 November 2026** bersama arah Canopy. Satu file variabel dari `google/fonts` (176.288 byte) + `OFL.txt` |
| M-01 | P3 | Gerak yang ada: fade masuk Beranda, warna indikator tab, review naik 12 dp. Tidak ada umpan balik tekan selain ripple | `ManagerHomeScreen.kt:77-78`, `FarmChrome.kt:291-308` | Lihat spesifikasi gerak di bawah |

## Spesifikasi gerak Canopy

Semua animasi berbasis waktu atau spring di Compose otomatis mengikuti skala animasi sistem (`MotionDurationScale`). Parallax yang mengikuti gulir bukan animasi waktu, jadi dimatikan manual saat `Settings.Global.ANIMATOR_DURATION_SCALE` = 0. Tidak ada animasi yang berulang terus.

| Momen | Gerak | Tujuan |
|---|---|---|
| Login muncul | Marka, judul, sheet: fade + naik 16 dp, 280 ms, jeda 0/60/120 ms, `FastOutSlowIn` | Mengarahkan mata ke form |
| Tekan tombol/kartu | Skala 0,97 (tombol) / 0,98 (kartu), `spring(dampingRatio = 0.7, stiffness = MediumLow)` | Umpan balik sentuh yang terasa fisik |
| Hover/stylus/mouse | Kartu naik 2 dp elevasi bayangan | Perangkat dengan pointer (ChromeOS, tablet + stylus) |
| Gagal masuk | Geser horizontal sekali, 6 dp, 3 ayunan, ±300 ms | Menandai kegagalan tanpa warna saja |
| Kartu greenhouse pertama kali tampil | Fade + naik 12 dp, jeda 40 ms per kartu, maksimal 6 kartu, hanya sekali per proses | Ritme masuk tanpa menunda daftar panjang |
| Tab bawah / tab segmen | Pill indikator bergeser (spring), ikon terpilih skala 1 → 1,08 → 1 | Menunjukkan arah perpindahan |
| Gulir Beranda | Hero menyusut ringan (alpha motif daun turun, padding mengecil); motif daun bergeser 0,5× kecepatan gulir | Kedalaman tanpa menutup isi |
| Perbarui | Tarik ke bawah, indikator Material3 | Memberi pengelola kontrol pembaruan |
| Angka | **Tetap langsung tampil**, tanpa count-up | Mengikuti `DESIGN.md` (nilai tersedia langsung tampil) dan menghindari kesan angka berubah |

## Warna dan kontras

Tidak ada warna baru. Pasangan yang dipakai mockup, hasil `contrast-check.py`:

| Teks | Latar | Rasio |
|---|---|---|
| `#C4CEC3` (teks sekunder di forest) | `#132820` / `#203B30` | 9,59 / 7,49 |
| `#D4E4B7` (sage di forest, Catat, tautan hero) | `#132820` | 11,52 |
| `#52665B` (muted) | `#F4F3E8` / `#FFFDF4` | 5,52 / 6,04 |
| `#805016` (amber) | `#FFF0D9` / `#FFFDF4` | 6,08 / 6,70 |
| `#2B503D` | `#D4E4B7` | 6,72 |
| `#365B66` (info) | `#E7F0F2` | 6,38 |
| `#FFFFFF` | `#2B503D` | 9,06 |
| `#687970` (garis field, placeholder) | `#FFFDF4` | 4,52 |
| `#9A3029` (chip "Perlu diperbaiki") | `#FCEBE7` | 6,43 |
| `#2B503D` (chip "Terkirim"/"Sesuai") | `#E6EBD9` | 7,44 |
| `#C4CEC3` (label tab tidak aktif di atas pill) | `#385442` | 5,15 |
| `#805016` | `#F4F3E8` | 6,12 |

Tema gelap belum dirancang di mockup. Aturannya ada di master prompt bagian 5.6.

## Yang tidak diambil dan alasannya

- Donut chart pengeluaran: pertanyaan yang dijawab Beranda adalah "kas bulan ini masuk vs keluar", cukup batang dua segmen.
- Delta persen terhadap bulan lalu: `manager-home` tidak memberi data bulan lalu. Menambahkannya berarti mengarang angka.
- Onboarding/splash "Get Started": aplikasi internal, pengguna langsung login.
- Glassmorphism kartu login di atas foto: foto stok rumah pohon tidak terkait produk, dan kaca di atas foto menurunkan kontras form.
- Tilt kartu mengikuti jari: gimmick, bentrok dengan gestur gulir.
