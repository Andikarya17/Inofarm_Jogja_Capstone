---
name: Inofarm
description: Sistem visual Android untuk pencatatan operasional dan evaluasi keuangan greenhouse melon.
colors:
  brand: "#2B503D"
  ink: "#142B23"
  sage: "#D4E4B7"
  muted: "#52665B"
  soft: "#E6EBD9"
  info: "#365B66"
  info-surface: "#E7F0F2"
  background: "#F4F3E8"
  surface: "#FFFDF4"
  navigation: "#E9EEDF"
  outline: "#687970"
  divider: "#D3DBCB"
  warning: "#805016"
  warning-surface: "#FFF0D9"
  error: "#9A3029"
  error-surface: "#FCEBE7"
  forest-background: "#132820"
  forest-surface: "#203B30"
  forest-surface-variant: "#2E483C"
  forest-muted: "#C4CEC3"
  forest-selected: "#385442"
  forest-navigation: "#263F34"
  forest-outline: "#9BAC9C"
  forest-divider: "#4A6456"
  forest-warning: "#DCC28C"
  forest-warning-surface: "#493519"
  forest-error: "#FFB4A9"
  forest-error-surface: "#712820"
  forest-on-error-surface: "#FFDAD4"
  white: "#FFFFFF"
typography:
  display-small:
    fontFamily: sans-serif
    fontSize: "32px"
    fontWeight: 600
  headline-large:
    fontFamily: sans-serif
    fontSize: "34px"
    fontWeight: 700
  headline-small:
    fontFamily: sans-serif
    fontSize: "24px"
    fontWeight: 600
  title-medium:
    fontFamily: sans-serif
    fontSize: "16px"
    fontWeight: 600
  body-medium:
    fontFamily: sans-serif
    fontSize: "14px"
    fontWeight: 400
  label-large:
    fontFamily: sans-serif
    fontSize: "14px"
    fontWeight: 500
rounded:
  control: "12px"
  card: "20px"
  hero: "24px"
  navigation: "20px"
spacing:
  tiny: "4px"
  small: "8px"
  medium: "12px"
  large: "16px"
  page: "16px"
  section: "24px"
  touch: "48px"
components:
  button-primary:
    backgroundColor: "{colors.brand}"
    textColor: "{colors.white}"
    rounded: "{rounded.control}"
    typography: "{typography.label-large}"
  button-primary-forest:
    backgroundColor: "{colors.sage}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    typography: "{typography.label-large}"
  login-panel:
    backgroundColor: "{colors.forest-background}"
    textColor: "{colors.background}"
    rounded: "{rounded.hero}"
    padding: "20px"
  context-card:
    backgroundColor: "{colors.forest-background}"
    textColor: "{colors.background}"
    rounded: "{rounded.card}"
    padding: "16px 16px 8px"
  metric-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: "{spacing.large}"
---

# Sistem visual Android Inofarm

## Overview

Inofarm membantu pekerja dan pengelola mencatat pekerjaan greenhouse melon hidroponik dan meninjau hasilnya. Ilustrasi daun menjadi aksen di tepi atas login dan Beranda. Bidang forest menandai pintu masuk dan konteks kerja. Bidang solid memudahkan pengguna membaca angka, mengisi form, dan memeriksa transaksi. Material 3 memberi pola sentuh, fokus, Back, keyboard, dialog, dan sheet yang konsisten dengan Android. [Konteks produk](PRODUCT.md) · [Bukti pematangan pengelola](docs/context/MANAGER_UI_POLISH_EVIDENCE_20261109.md)

Fokus login ada pada Masuk. Beranda pengelola menampilkan cakupan penugasan, daftar greenhouse, dan akses ke ringkasan serta riwayat. Beranda pekerja membawa konteks pencatatan ke hasil pengukuran dan tindakan. Arah ENERGY / RHYTHM / MOTION adalah 2 / 2 / 2 untuk login dan menu, serta 1 / 2 / 2 untuk pemilih, form, detail, dan laporan.

## Colors

Skema aplikasi mengikuti tema sistem. Tema terang memasangkan aksi hijau `brand` dengan teks putih serta memakai `ink` untuk teks utama di atas krem. Tema gelap memakai lapisan forest, teks krem, dan aksi sage dengan teks forest. Panel login dan kartu konteks memakai skema forest pada kedua tema. Label EC dan PPM memakai warna primer yang sama. Nama dan satuan membedakan keduanya.

Amber menandai perhatian seperti musim tertutup atau antrean yang perlu ditinjau. Merah menandai kesalahan atau status yang perlu ditangani di riwayat. Informasi sekunder memakai pasangan `onSurfaceVariant` dengan permukaannya. Tuliskan arti tiap status supaya pengguna dapat membacanya tanpa mengandalkan warna. Nilai pada YAML merangkum peran utama. Definisi skema dan peran Material 3 mengikuti [InofarmTheme.kt](android/app/src/main/java/com/inofarm/jogja/core/design/InofarmTheme.kt) dan [FarmChrome.kt](android/app/src/main/java/com/inofarm/jogja/core/design/FarmChrome.kt).

## Typography

Seluruh peran memakai `FontFamily.SansSerif` dengan ukuran dalam sp. Angka pengukuran dan nominal menggunakan fitur angka tabular `tnum`. `FarmAmount` memberi ruang pemenggalan pada pemisah ribuan dan menyimpan teks nominal utuh di semantik aksesibilitas.

| Peran Compose | Ukuran / tinggi baris | Bobot dan pemakaian |
| --- | --- | --- |
| `displaySmall` | 32 / 37 sp | Semibold untuk angka EC dan PPM |
| `headlineLarge` | 34 / 40 sp | Bold untuk nama Inofarm pada login |
| `headlineMedium`, `headlineSmall` | 28 / 34 dan 24 / 30 sp | Semibold untuk hierarki judul |
| `titleLarge`, `titleMedium`, `titleSmall` | 20 / 26, 16 / 22 dan 14 / 20 sp | Semibold, semibold dan medium untuk judul konteks, panel atau bagian |
| `bodyLarge`, `bodyMedium`, `bodySmall` | 16 / 24, 14 / 21 dan 12 / 18 sp | Normal untuk isi, keterangan, dan metadata |
| `labelLarge`, `labelMedium`, `labelSmall` | 14 / 20, 12 / 18 dan 11 / 16 sp | Medium untuk aksi, label metrik, dan tab |

## Layout

Gunakan 4 dp untuk hubungan label dengan nilai, 8 dan 12 dp dalam kelompok, 16 dp untuk tepi halaman dan isi panel kerja, serta 24 dp untuk pemisah bagian. Aksi utama dan tujuan navigasi punya tinggi minimum 48 dp. Login memiliki margin horizontal 24 dp dan batas lebar isi 420 dp. Shell layar kerja memusatkan isi dengan batas 900 dp dan mengikuti inset sistem serta keyboard. Ukuran `px` di YAML menyimpan nilai nominal untuk alat pembaca DESIGN.md. Aplikasi memakai dp untuk dimensi dan sp untuk teks.

Kartu EC dan PPM berjajar pada ruang cukup dan menumpuk ketika lebar konten kurang dari 268 dp atau skala font di atas 1,3. Pada skala yang sama, ringkasan sumber menempatkan tombol sumber pada baris sendiri. Login dan `FinanceField` memindahkan label ke atas isian ketika skala font di atas 1,5. Pertahankan ruang tumbuh untuk nama greenhouse, nominal, label, dan pesan. Gunakan area gulir untuk menjaga isian dan tindakan tetap dapat dijangkau saat tinggi berkurang atau keyboard tampil.

## Elevation & Depth

`BotanicalBackdrop` mengambil crop bagian atas `background_login.jpg` untuk login (228 dp) dan `background_dashboard.jpg` untuk Beranda (152 dp). Lapisan bergradasi menyatukan crop ke kanvas. Ilustrasi dekoratif tampil pada viewport potret di bawah 600 dp. Tema gelap memakai lapisan latar pada alpha 86% di tepi atas. Menu lainnya, form, detail, review, dialog, sheet, dan workspace laporan memakai bidang solid. [Implementasi latar](android/app/src/main/java/com/inofarm/jogja/core/design/BotanicalBackdrop.kt)

Login, konteks, metrik, form, dan panel review memakai bidang solid. Lapisan kaca terbatas pada navigasi bawah beranda saat navigasi tampil, penyimpanan sedang tidak berlangsung, API minimal 31, window memakai akselerasi hardware, dan skala font maksimal 1,4. Komposit memakai tint dasar dengan alpha 94%, highlight tonal, garis tepi 1 dp, dan elevasi 2 dp. Ikon dan label tetap opak. Konten dapat bergulir di belakang tint dan mendapat ruang ekstra di akhir daftar. Pada kondisi lain, navigasi memakai bidang opak dengan ruang terpisah dari konten. [Material navigasi](android/app/src/main/java/com/inofarm/jogja/core/design/FarmChrome.kt) · [Shell aplikasi](android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt)

## Shapes

Radius 12 dp untuk kontrol, 20 dp untuk kartu dan bingkai navigasi, serta 24 dp untuk panel login. Garis tepi tipis membatasi panel ringkasan dan metrik. Bagian form menggunakan judul dan pemisah, dengan panel untuk ringkasan, konteks, atau perbandingan versi.

## Components

### Login dan shell

Login menempatkan nama Inofarm dan keterangan `Catatan operasional greenhouse melon` di atas panel Masuk. Pengantar mendapat latar solid ketika tinggi efektif di bawah 600 dp atau skala font di atas 1,3. Panel forest memuat Email, Password dengan toggle, pesan yang relevan, lalu tombol Masuk selebar panel. Keyboard berpindah dari Email ke Password dengan Next. Done mengikuti validasi yang sama dengan tombol Masuk. Pesan konfigurasi endpoint, izin jaringan, kesalahan, dan hasil keluar muncul di form. [FinanceLogin.kt](android/app/src/main/java/com/inofarm/jogja/feature/auth/FinanceLogin.kt)

Toolbar memuat Inofarm, sinkronisasi, dan akun. Navigasi pekerja terdiri dari Beranda, Antrean, Kegiatan, dan Panen. Pengelola juga mendapat Keuangan. Tab aktif memakai indikator tonal, label lebih tebal, dan semantik pilihan. Saat lebar bagian dalam dibagi skala font kurang dari 54 dp per tujuan, bar berubah menjadi tombol `Menu · <halaman aktif>` yang membuka sheet `Pindah halaman` berisi seluruh tujuan peran. [FinanceNavigation.kt](android/app/src/main/java/com/inofarm/jogja/feature/FinanceNavigation.kt)

### Pemilih dan baris aksi

`FarmPicker` memakai dialog untuk daftar pendek, serta daftar gulir dalam sheet untuk daftar panjang yang dibuka dari layar utama atau form. Pemilih di dalam filter, konteks pekerja, dan administrasi memakai dialog native. Baris pilihan tumbuh dari 56 dp atau 72 dp saat ada keterangan. Setiap baris memiliki satu aksi sentuh, semantik radio, divider antaropsi, dan warna tonal untuk pilihan aktif. Baris aksi memakai ikon sesuai tugas, keterangan singkat, serta chevron untuk membuka tujuan. [FarmPicker.kt](android/app/src/main/java/com/inofarm/jogja/core/design/FarmPicker.kt)

### Konteks dan pengukuran

Pada Beranda pengelola, header forest merangkum cakupan dan waktu pemeriksaan. Pemilih greenhouse memakai satu sheet Material dengan pilihan aktif sage. Kartu greenhouse tersusun vertikal agar nama, fase dan umur panjang dapat membungkus. Ringkasan satu greenhouse menempatkan periode tanam dan pasangan pengukuran lebih dahulu. Baris catatan menampilkan jenis, greenhouse, pencatat dan waktu. Kas memakai angka tabular dengan periode WIB serta definisi penjualan lunas dan pembayaran aktual.

Beranda pekerja memakai mode Operate. Daftar penugasan memuat nama greenhouse, musim, kelompok dan umur yang diketahui. Header forest memakai crop ilustrasi botani bawaan di sudut luar area teks. Panel terpilih menampilkan tanggal WIB, konteks dan HSS/HST, dengan aksi lapangan di bawahnya. Periode berjalan yang sah atau satu kelompok yang memenuhi syarat membuka pencatatan langsung. Sheet tersedia untuk pilihan ambigu. Pekerja dapat kembali ke daftar greenhouse melalui tombol atau Back. Pada layar sempit dan font besar, ilustrasi memberi ruang bagi teks dan tombol. [HomeContextCard.kt](android/app/src/main/java/com/inofarm/jogja/feature/context/HomeContextCard.kt)

Pekerjaan hari ini memakai baris agenda ringkas. Jadwal kelompok, catatan akun sendiri, koreksi verifikasi, dan kiriman tertahan diberi judul terpisah. Realisasi dan catatan dilewati terlihat sebagai status tertulis. Kegiatan tanpa jadwal tetap dapat dicatat.

Beranda menampilkan dua kartu setara, EC dalam mS/cm dan PPM dalam ppm, dari satu catatan manual yang memenuhi syarat pada greenhouse dan kelompok terpilih. Saat catatan hari ini belum tersedia, tampilkan catatan terakhir yang memenuhi syarat beserta waktu WIB dan statusnya. Catatan historis berisi PPM saja dapat menampilkan `EC belum tersedia`. Tampilkan keadaan memuat atau kosong dengan teks. Ringkasan sumber membedakan data offline, belum tersinkron, dan diterima server. `Lihat sumber` membuka waktu, jenis input, dan pencatat bila tersedia. Aksi utama `Catat pengukuran` diikuti `Catat kegiatan`, `Catat panen`, dan jadwal untuk kelompok tersebut. [MeasurementDashboard.kt](android/app/src/main/java/com/inofarm/jogja/feature/activities/MeasurementDashboard.kt) · [Kontrak pengukuran](contracts/v2/MEASUREMENTS.md)

### Form, review, dan riwayat

Antrean pengelola membuka verifikasi dengan kategori Kegiatan dan Panen. Kartu menempatkan jenis, greenhouse, pencatat, waktu dan status sebelum rincian teknis. Detail menempatkan catatan aktual dan acuan berversi berdekatan, lalu keputusan pengelola dan histori. Pengiriman saya memberi jalur terpisah untuk antrean perangkat. Detail pekerja menampilkan alasan keputusan pada revisi catatan sendiri. Form pekerja memakai picker tanggal dan jam Android, dengan isian manual yang dapat dibuka untuk mempertahankan detik. HSS/HST dan acuan historis tampil dekat waktu kejadian.

`FarmSection` mengelompokkan isian berdasarkan pekerjaan, misalnya waktu, konteks, hasil, atau catatan. Pertahankan satuan, bantuan, jenis keyboard dan kesalahan di dekat isian. Review memakai pasangan label dan nilai dalam panel. Bedakan `Simpan draf dan kembali`, tindakan periksa, `Kembali ke isian`, serta `Simpan lokal & antrekan`. Status draf menyebut penyimpanan di perangkat. Antrean dan riwayat menyebut penerimaan server secara eksplisit.

Judul ringkas memberi orientasi di menu. Toolbar merangkum cakupan riwayat. Filter mengelompokkan cakupan, jenis dan status, serta periode WIB. Baris solid mendahulukan jenis catatan, grade atau nominal, disusul konteks, waktu, dan status. Panen menampilkan cuplikan per grade di riwayat dan rincian lengkap di detail. Saat pengguna meninjau konflik, dua panel memisahkan versi lokal dan server beserta alasannya. Antrean mendahulukan pengajuan yang perlu ditinjau dan menyimpan ID serta percobaan di rincian operasi. Back dari detail yang dibuka lewat Antrean kembali ke Antrean. [Komponen menu](android/app/src/main/java/com/inofarm/jogja/core/design/FarmMenu.kt) · [Riwayat](android/app/src/main/java/com/inofarm/jogja/feature/history/HistoryScreen.kt)

### Keuangan dan laporan

Rekap pengelola memakai pemilih greenhouse dan kelompok, lalu menunjukkan varietas, populasi tanaman, target buah, hasil layak dan cacat, serta capaian. Tindakan selesai ada sesudah rekap dan meminta alasan. Pekerja membuka Progres panen dari Beranda atau Panen. Bidang sage menonjolkan hasil layak dan target. Rekap di bawahnya menunjukkan hasil menurut kondisi dan keputusan, dengan sumber per revisi. Kelola greenhouse dan Kelola akun tersedia langsung dari menu akun pengelola.

Keuangan memisahkan konteks dan tombol `Catat pengeluaran` pada blok Catatan baru dari toolbar Riwayat. `Alat keuangan` mengelompokkan biaya dan alokasi, investasi awal/aset dan penyusutan, penjualan dan penerimaan, evaluasi, serta pemeliharaan. Laporan dan investasi menempatkan periode dan basis di atas kelompok hasil, dengan alasan tetap terlihat saat hasil belum tersedia atau tidak valid. Rincian rumus, sumber, input, dan diagnostik dapat dibuka. [FinancialEntryScreen.kt](android/app/src/main/java/com/inofarm/jogja/feature/expenses/FinancialEntryScreen.kt) · [Kontrak operasional](contracts/v2/README.md)

### Motion dan umpan balik

Panel login masuk selama 240 ms. Label tombol merespons status request selama 160 ms, dan ruang pesan dapat berubah tinggi selama 200 ms. Shell baru muncul dengan fade 220 ms setelah sesi tersedia. Indikator tab dan warna status draf bertransisi selama 200 ms. Review memakai fade dan pergeseran 12 dp selama 220 ms. Kartu audit dan baris rumus berubah tinggi saat rincian dibuka. Sheet mengikuti transisi Material. Compose memakai skala animasi sistem. Angka pengukuran dan nominal langsung menampilkan nilai tersedia, dan ilustrasi tetap diam.

Beranda pengelola memakai transisi singkat pada pilihan greenhouse, pembukaan sumber pengukuran, dan review periode. Pergantian cakupan segera mengikat isi ke pilihan baru. Pemutakhiran data pada cakupan yang sama mempertahankan daftar yang masih sah.

Pada pekerja, panel greenhouse baru muncul dalam 230 ms. Perpindahan empat tab memakai perubahan indikator tonal dan masuk singkat 180 ms. Posisi gulir tab disimpan. Bagian opsional dan sumber berubah tinggi dalam 180–200 ms. Nilai buah dan kg tampil langsung. Batang capaian beralih antar-snapshot dalam 240 ms. Umpan balik draf dan antrean mengikuti status penyimpanan yang sudah terjadi dan dapat diumumkan sebagai live region. [Bukti dan matriks gerak pekerja](docs/context/WORKER_ALIGNMENT_EVIDENCE_20261110.md)

## Do's and Don'ts

- Batasi ilustrasi ke tepi atas login dan Beranda agar data, form, dan menu tetap mudah dipindai.
- Pertahankan teks, angka, dan pesan di atas bidang yang stabil. Simpan status dalam teks, dengan warna sebagai penanda tambahan.
- Tampilkan sumber, waktu WIB, dan alasan yang relevan. Gunakan keterangan belum tersedia untuk data kosong dan label penerimaan server untuk operasi yang sudah diterima.
- Pertahankan skala font pengguna, urutan fokus, inset sistem, dan ruang gulir. Pakai sheet ketika seluruh tujuan navigasi perlu lebih banyak ruang.
- Gunakan status dan hasil dari kontrak operasional. Catat hasil render dan verifikasi per perangkat serta konfigurasi di [context frontend](docs/context/README.md).
