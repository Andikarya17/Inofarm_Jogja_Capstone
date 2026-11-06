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

Inofarm membantu pekerja dan pengelola mencatat pekerjaan greenhouse melon hidroponik dan meninjau hasilnya. Ilustrasi daun membingkai login dan beranda. Bidang forest menandai pintu masuk dan konteks budidaya, sementara bidang solid memudahkan pengguna membaca angka, mengisi form, dan memeriksa transaksi. Material 3 memberi pola sentuh, fokus, Back, keyboard, dialog, dan sheet yang konsisten dengan Android. [Konteks produk](PRODUCT.md) · [Brief botani](docs/context/MASTER_PROMPT_FRONTEND_BOTANICAL.md)

Fokus login ada pada Masuk. Beranda membawa pengguna dari greenhouse, musim, dan kelompok ke hasil pengukuran dan tindakan. Form, riwayat, dan laporan mengutamakan isi dan status dengan lebih sedikit ornamen. Arah ENERGY / RHYTHM / MOTION dari brief adalah 2 / 2 / 1 untuk login, 2 / 2 / 2 untuk beranda, dan 1 / 2 / 1 untuk layar kerja.

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

`BotanicalBackdrop` menempatkan `background_login.jpg` dan `background_dashboard.jpg` dari `drawable-nodpi` di belakang viewport login dan beranda. Gambar dekoratif tidak memiliki deskripsi aksesibilitas. Crop terpusat mempertahankan proporsi, sementara tinggi daftar dan form tumbuh sendiri. Ilustrasi muncul jika lebar viewport lebih kecil dari tingginya dan di bawah 600 dp. Pada kondisi lain, warna latar tema menjadi fallback solid. Tema gelap melapisi ilustrasi dengan warna latar tema pada alpha 86%. [Implementasi latar](android/app/src/main/java/com/inofarm/jogja/core/design/BotanicalBackdrop.kt) · [Asal aset pengguna](docs/context/FRONTEND_ASSET_REVIEW_20261107.md)

Login, konteks, metrik, form, dan panel review memakai bidang solid. Lapisan kaca terbatas pada navigasi bawah beranda saat navigasi tampil, penyimpanan sedang tidak berlangsung, API minimal 31, window memakai akselerasi hardware, dan skala font maksimal 1,4. Komposit memakai tint dasar dengan alpha 94%, highlight tonal, garis tepi 1 dp, dan elevasi 2 dp. Ikon dan label tetap opak. Konten dapat bergulir di belakang tint dan mendapat ruang ekstra di akhir daftar. Pada kondisi lain, navigasi memakai bidang opak dengan ruang terpisah dari konten. [Material navigasi](android/app/src/main/java/com/inofarm/jogja/core/design/FarmChrome.kt) · [Shell aplikasi](android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt)

## Shapes

Radius 12 dp untuk kontrol, 20 dp untuk kartu dan bingkai navigasi, serta 24 dp untuk panel login. Garis tepi tipis membatasi panel ringkasan dan metrik. Bagian form menggunakan judul dan pemisah, dengan panel untuk ringkasan, konteks, atau perbandingan versi.

## Components

### Login dan shell

Login menempatkan nama Inofarm dan keterangan `Catatan operasional greenhouse melon` di atas panel Masuk. Pengantar mendapat latar solid ketika tinggi efektif di bawah 600 dp atau skala font di atas 1,3. Panel forest memuat Email, Password dengan toggle, pesan yang relevan, lalu tombol Masuk selebar panel. Keyboard berpindah dari Email ke Password dengan Next. Done mengikuti validasi yang sama dengan tombol Masuk. Pesan konfigurasi endpoint, izin jaringan, kesalahan, dan hasil keluar muncul di form. [FinanceLogin.kt](android/app/src/main/java/com/inofarm/jogja/feature/auth/FinanceLogin.kt)

Toolbar memuat Inofarm, sinkronisasi, dan akun. Navigasi pekerja terdiri dari Beranda, Antrean, Kegiatan, dan Panen. Pengelola juga mendapat Keuangan. Tab aktif memakai indikator tonal, label lebih tebal, dan semantik pilihan. Saat lebar bagian dalam dibagi skala font kurang dari 54 dp per tujuan, bar berubah menjadi tombol `Menu · <halaman aktif>` yang membuka sheet `Pindah halaman` berisi seluruh tujuan peran. [FinanceNavigation.kt](android/app/src/main/java/com/inofarm/jogja/feature/FinanceNavigation.kt)

### Konteks dan pengukuran

Kartu forest menonjolkan nama greenhouse, diikuti musim, kelompok, serta HSS dan HST menurut kalender WIB. Sheet konteks menampilkan pilihan greenhouse, musim, dan kelompok secara berurutan. Mengganti greenhouse mengosongkan musim dan kelompok. Mengganti musim mengosongkan kelompok. Selama konteks belum lengkap, kartu memberi langkah berikutnya dan beranda menahan metrik serta tindakan pencatatan. [HomeContextCard.kt](android/app/src/main/java/com/inofarm/jogja/feature/context/HomeContextCard.kt)

Beranda menampilkan dua kartu setara, EC dalam mS/cm dan PPM dalam ppm, dari satu catatan manual yang memenuhi syarat pada greenhouse dan kelompok terpilih. Saat catatan hari ini belum tersedia, tampilkan catatan terakhir yang memenuhi syarat beserta waktu WIB dan statusnya. Catatan historis berisi PPM saja dapat menampilkan `EC belum tersedia`. Tampilkan keadaan memuat atau kosong dengan teks. Ringkasan sumber membedakan data offline, belum tersinkron, dan diterima server. `Lihat sumber` membuka waktu, jenis input, dan pencatat bila tersedia. Aksi utama `Catat pengukuran` diikuti `Catat kegiatan`, `Catat panen`, dan jadwal untuk kelompok tersebut. [MeasurementDashboard.kt](android/app/src/main/java/com/inofarm/jogja/feature/activities/MeasurementDashboard.kt) · [Kontrak pengukuran](contracts/v2/MEASUREMENTS.md)

### Form, review, dan riwayat

`FarmSection` mengelompokkan isian berdasarkan pekerjaan, misalnya waktu, konteks, hasil, atau catatan. Pertahankan satuan, bantuan, jenis keyboard dan kesalahan di dekat isian. Review memakai pasangan label dan nilai dalam panel. Bedakan `Simpan draf dan kembali`, tindakan periksa, `Kembali ke isian`, serta `Simpan lokal & antrekan`. Status draf menyebut penyimpanan di perangkat. Antrean dan riwayat menyebut penerimaan server secara eksplisit.

Baris riwayat mendahulukan jenis catatan dan nominal jika tersedia, disusul konteks, waktu, serta status. Filter aktif terlihat dan detail tetap dapat dibuka. Saat pengguna meninjau konflik, gunakan dua panel untuk versi lokal dan server serta tampilkan alasan yang tersedia. Antrean membedakan pengajuan yang menunggu pengiriman dari yang perlu ditinjau. Panen menampilkan rincian per grade dan penjualan memiliki alur sendiri. [HistoryScreen.kt](android/app/src/main/java/com/inofarm/jogja/feature/history/HistoryScreen.kt) · [HarvestScreen.kt](android/app/src/main/java/com/inofarm/jogja/feature/harvest/HarvestScreen.kt)

### Keuangan dan laporan

Keuangan memuat konteks untuk catatan baru, `Catat pengeluaran`, pilihan jenis riwayat dan daftar transaksi dalam satu area gulir. `Alat keuangan` membuka sheet untuk transaksi, aset, evaluasi, investasi, dan pemulihan. Tampilkan pembayaran, alokasi biaya, aset, penyusutan, dan penjualan dengan labelnya masing-masing. Laporan dan investasi mendahulukan hasil yang tersedia beserta status, periode, dan basis. Rincian rumus dan sumber dapat dibuka, termasuk alasan saat hasil belum tersedia atau tidak valid. [FinancialEntryScreen.kt](android/app/src/main/java/com/inofarm/jogja/feature/expenses/FinancialEntryScreen.kt) · [Kontrak operasional](contracts/v2/README.md)

### Motion dan umpan balik

Indikator tab dan warna status draf bertransisi selama 200 ms. Review masuk dengan fade dan pergeseran 12 dp selama 220 ms melalui satu lapisan visual. Kartu audit dan panel rumus berubah tinggi saat pengguna membuka rincian. Sheet mengikuti transisi Material. Compose memakai skala animasi sistem. Angka pengukuran dan nominal langsung menampilkan nilai tersedia, dan ilustrasi tetap diam.

## Do's and Don'ts

- Gunakan ilustrasi sebagai bingkai viewport. Batasi aset dekoratif pada login dan beranda.
- Pertahankan teks, angka, dan pesan di atas bidang yang stabil. Simpan status dalam teks, dengan warna sebagai penanda tambahan.
- Tampilkan sumber, waktu WIB, dan alasan yang relevan. Gunakan keterangan belum tersedia untuk data kosong dan label penerimaan server untuk operasi yang sudah diterima.
- Pertahankan skala font pengguna, urutan fokus, inset sistem, dan ruang gulir. Pakai sheet ketika seluruh tujuan navigasi perlu lebih banyak ruang.
- Gunakan status dan hasil dari kontrak operasional. Catat hasil render dan verifikasi per perangkat serta konfigurasi di [context frontend](docs/context/README.md).
