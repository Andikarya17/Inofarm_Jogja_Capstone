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
    fontFamily: "Plus Jakarta Sans"
    fontSize: "32px"
    fontWeight: 600
  headline-large:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "34px"
    fontWeight: 700
  headline-small:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "24px"
    fontWeight: 600
  title-medium:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "16px"
    fontWeight: 600
  body-medium:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "14px"
    fontWeight: 400
  label-large:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "14px"
    fontWeight: 500
  hero-amount:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "22px"
    fontWeight: 700
  hero-number:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "26px"
    fontWeight: 800
  wordmark:
    fontFamily: "Plus Jakarta Sans"
    fontSize: "44px"
    fontWeight: 800
    letterSpacing: "-1.2px"
rounded:
  control: "12px"
  card: "20px"
  hero: "24px"
  navigation: "20px"
  sheet: "28px"
  tile: "14px"
  dock: "26px"
  chip: "10px"
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
  dock:
    backgroundColor: "{colors.forest-navigation}"
    textColor: "{colors.forest-muted}"
    rounded: "{rounded.dock}"
    height: "72px"
  dock-record-button:
    backgroundColor: "{colors.sage}"
    textColor: "{colors.ink}"
    size: "56px"
  form-section:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: "{spacing.large}"
  form-bar:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "16px"
    height: "52px"
  choice-chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    height: "{spacing.touch}"
  choice-chip-selected:
    backgroundColor: "{colors.brand}"
    textColor: "{colors.white}"
    rounded: "{rounded.control}"
  choice-chip-defective-selected:
    backgroundColor: "{colors.warning}"
    textColor: "{colors.white}"
    rounded: "{rounded.control}"
  detail-card:
    backgroundColor: "{colors.soft}"
    rounded: "{rounded.tile}"
    padding: "{spacing.medium}"
  info-card:
    backgroundColor: "{colors.info-surface}"
    textColor: "{colors.info}"
    rounded: "{rounded.tile}"
    padding: "{spacing.medium}"
---

# Sistem visual Android Inofarm

## Overview

Inofarm membantu pekerja dan pengelola mencatat pekerjaan greenhouse melon hidroponik dan meninjau hasilnya. Ilustrasi daun menjadi aksen di tepi atas login dan Beranda. Bidang forest menandai pintu masuk dan konteks kerja. Bidang solid memudahkan pengguna membaca angka, mengisi form, dan memeriksa transaksi. Material 3 memberi pola sentuh, fokus, Back, keyboard, dialog, dan sheet yang konsisten dengan Android. [Konteks produk](PRODUCT.md) · [Bukti pematangan pengelola](docs/context/MANAGER_UI_POLISH_EVIDENCE_20261109.md)

Fokus login ada pada Masuk. Beranda pengelola menampilkan cakupan penugasan, daftar greenhouse, dan akses ke ringkasan serta riwayat. Beranda pekerja membawa konteks pencatatan ke hasil pengukuran dan tindakan. Arah ENERGY / RHYTHM / MOTION adalah 2 / 2 / 2 untuk login dan menu, serta 1 / 2 / 2 untuk pemilih, form, detail, dan laporan.

## Colors

Skema aplikasi mengikuti tema sistem. Tema terang memasangkan aksi hijau `brand` dengan teks putih serta memakai `ink` untuk teks utama di atas krem. Tema gelap memakai lapisan forest, teks krem, dan aksi sage dengan teks forest. Panel login dan kartu konteks memakai skema forest pada kedua tema. Label EC dan PPM memakai warna primer yang sama. Nama dan satuan membedakan keduanya.

Amber menandai perhatian seperti musim tertutup atau antrean yang perlu ditinjau. Merah menandai kesalahan atau status yang perlu ditangani di riwayat. Informasi sekunder memakai pasangan `onSurfaceVariant` dengan permukaannya. Tuliskan arti tiap status supaya pengguna dapat membacanya tanpa mengandalkan warna. Nilai pada YAML merangkum peran utama. Penyempurnaan navigasi dan alur pengelola tidak menambah warna: tombol Catat, penanda belum terkirim, chip, daftar Kelola, dan wizard memakai peran Material 3 dan token di atas. Definisi skema dan peran Material 3 mengikuti [InofarmTheme.kt](android/app/src/main/java/com/inofarm/jogja/core/design/InofarmTheme.kt) dan [FarmChrome.kt](android/app/src/main/java/com/inofarm/jogja/core/design/FarmChrome.kt).

## Typography

Seluruh peran memakai Plus Jakarta Sans (OFL, satu file variabel, `FarmFamily` di `InofarmTheme.kt`) dengan ukuran dalam sp; lisensi di `third_party/fonts/plus-jakarta-sans/OFL.txt`. Angka pengukuran dan nominal menggunakan fitur angka tabular `tnum`. `FarmAmount` memberi ruang pemenggalan pada pemisah ribuan dan menyimpan teks nominal utuh di semantik aksesibilitas.

| Peran Compose | Ukuran / tinggi baris | Bobot dan pemakaian |
| --- | --- | --- |
| `displaySmall` | 32 / 37 sp | Semibold untuk angka EC dan PPM |
| `headlineLarge` | 34 / 40 sp | Bold untuk nama Inofarm pada login |
| `headlineMedium`, `headlineSmall` | 28 / 34 dan 24 / 30 sp | Semibold untuk hierarki judul |
| `titleLarge`, `titleMedium`, `titleSmall` | 20 / 26, 16 / 22 dan 14 / 20 sp | Semibold, semibold dan medium untuk judul konteks, panel atau bagian |
| `bodyLarge`, `bodyMedium`, `bodySmall` | 16 / 24, 14 / 21 dan 12 / 18 sp | Normal untuk isi, keterangan, dan metadata |
| `labelLarge`, `labelMedium`, `labelSmall` | 14 / 20, 12 / 18 dan 11 / 16 sp | Medium untuk aksi, label metrik, dan tab |

## Layout

Gunakan 4 dp untuk hubungan label dengan nilai, 8 dan 12 dp dalam kelompok, 16 dp untuk tepi halaman dan isi panel kerja, serta 24 dp untuk pemisah bagian. Aksi utama dan tujuan navigasi punya tinggi minimum 48 dp. Login memiliki margin horizontal 24 dp dan batas lebar isi 420 dp. Shell layar kerja memusatkan isi dengan batas 720 dp dan mengikuti inset sistem serta keyboard. Ukuran `px` di YAML menyimpan nilai nominal untuk alat pembaca DESIGN.md. Aplikasi memakai dp untuk dimensi dan sp untuk teks.

Kartu EC dan PPM berjajar pada ruang cukup dan menumpuk ketika lebar konten kurang dari 268 dp atau skala font di atas 1,3. Label tab bawah berhenti membesar setelah skala font 1,3, sedangkan nama lengkap tetap ada di semantik. Daftar menu memberi ruang akhir tambahan 80 dp saat tombol Catat tampil (`LocalFarmListBottomInset`) agar baris terakhir tidak tertutup. Pada skala yang sama, ringkasan sumber menempatkan tombol sumber pada baris sendiri. Login dan `FinanceField` memindahkan label ke atas isian ketika skala font di atas 1,5. Pertahankan ruang tumbuh untuk nama greenhouse, nominal, label, dan pesan. Gunakan area gulir untuk menjaga isian dan tindakan tetap dapat dijangkau saat tinggi berkurang atau keyboard tampil.

## Elevation & Depth

`BotanicalBackdrop` mengambil crop bagian atas `background_login.jpg` untuk login (228 dp) dan `background_dashboard.jpg` untuk Beranda (152 dp). Lapisan bergradasi menyatukan crop ke kanvas. Ilustrasi dekoratif tampil pada viewport potret di bawah 600 dp. Tema gelap memakai lapisan latar pada alpha 86% di tepi atas. Menu lainnya, form, detail, review, dialog, sheet, dan workspace laporan memakai bidang solid. [Implementasi latar](android/app/src/main/java/com/inofarm/jogja/core/design/BotanicalBackdrop.kt)

Login, konteks, metrik, form, dan panel review memakai bidang solid. Lapisan kaca terbatas pada navigasi bawah beranda saat navigasi tampil, penyimpanan sedang tidak berlangsung, API minimal 31, window memakai akselerasi hardware, dan skala font maksimal 1,4. Komposit memakai tint dasar dengan alpha 94%, highlight tonal, garis tepi 1 dp, dan elevasi 2 dp. Ikon dan label tetap opak. Konten dapat bergulir di belakang tint dan mendapat ruang ekstra di akhir daftar. Pada kondisi lain, navigasi memakai bidang opak dengan ruang terpisah dari konten. [Material navigasi](android/app/src/main/java/com/inofarm/jogja/core/design/FarmChrome.kt) · [Shell aplikasi](android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt)

## Shapes

Radius 12 dp untuk kontrol, 20 dp untuk kartu dan bingkai navigasi, serta 24 dp untuk panel login dan hero. Canopy menambah 14 dp untuk ubin, isian form pengelola, kartu rincian dan kartu info (`FarmShape.Tile`), 10 dp untuk chip status, 26 dp untuk dock, dan 28 dp pada sudut atas sheet. Tombol bar form memakai 16 dp dan strip konteks 16 dp. Garis tepi tipis membatasi panel ringkasan dan metrik. Form pengelola memakai kartu bergaris tipis (`FarmFormSection`, radius 20 dp, garis `outlineVariant` 1 dp) sebagai pengganti judul dan pemisah. Form pekerja tetap memakai judul dan pemisah.

## Components

### Login dan shell

Login menempatkan nama Inofarm dan keterangan `Catatan operasional greenhouse melon` di atas panel Masuk. Pengantar mendapat latar solid ketika tinggi efektif di bawah 600 dp atau skala font di atas 1,3. Panel forest memuat Email, Password dengan toggle, pesan yang relevan, lalu tombol Masuk selebar panel. Keyboard berpindah dari Email ke Password dengan Next. Done mengikuti validasi yang sama dengan tombol Masuk. Pesan konfigurasi endpoint, izin jaringan, kesalahan, dan hasil keluar muncul di form. [FinanceLogin.kt](android/app/src/main/java/com/inofarm/jogja/feature/auth/FinanceLogin.kt)

Toolbar memuat Inofarm dan menu akun. Pekerja juga mendapat tombol ikon Perbarui. Penanda belum terkirim, atau teks `Memperbarui` saat sinkronisasi berjalan, mengisi tempat di antara judul dan ikon. Pada pekerja dengan skala font di atas 1,3, penanda pindah ke baris sendiri di bawah judul dan teks `Memperbarui` disembunyikan. Navigasi pekerja terdiri dari dua tab, Beranda dan Riwayat. Antrean, Kegiatan, Panen, dan menu `Menu · halaman` tidak ada lagi. Navigasi pengelola terdiri dari empat tab yang selalu terlihat: Beranda, Riwayat, Keuangan, dan Kelola. Tab aktif memakai indikator tonal, label lebih tebal, dan semantik pilihan. Menu akun memuat nama, peran, dan `Keluar`. Kelola greenhouse dan Kelola akun pindah ke tab Kelola. [FinanceNavigation.kt](android/app/src/main/java/com/inofarm/jogja/feature/FinanceNavigation.kt)

### Pemilih dan baris aksi

`FarmPicker` memakai dialog untuk daftar pendek, serta daftar gulir dalam sheet untuk daftar panjang yang dibuka dari layar utama atau form. Pemilih di dalam filter, konteks pekerja, dan administrasi memakai dialog native. Baris pilihan tumbuh dari 56 dp atau 72 dp saat ada keterangan. Setiap baris memiliki satu aksi sentuh, semantik radio, divider antaropsi, dan warna tonal untuk pilihan aktif. Baris aksi memakai ikon sesuai tugas, keterangan singkat, serta chevron untuk membuka tujuan. [FarmPicker.kt](android/app/src/main/java/com/inofarm/jogja/core/design/FarmPicker.kt)

### Konteks dan pengukuran

Pada Beranda pengelola, header forest merangkum cakupan dan waktu pemeriksaan. Pemilih greenhouse memakai satu sheet Material dengan pilihan aktif sage. Kartu greenhouse tersusun vertikal agar nama, fase dan umur panjang dapat membungkus. Ringkasan satu greenhouse menempatkan periode tanam dan pasangan pengukuran lebih dahulu. Baris catatan menampilkan jenis, greenhouse, pencatat dan waktu. Kas memakai angka tabular dengan periode WIB serta definisi penjualan lunas dan pembayaran aktual.

Beranda pekerja memakai mode Operate. Daftar penugasan memuat nama greenhouse, musim, kelompok dan umur yang diketahui. Header forest memakai crop ilustrasi botani bawaan di sudut luar area teks. Panel terpilih menampilkan tanggal WIB, konteks dan HSS/HST, dengan aksi lapangan di bawahnya. Periode berjalan yang sah atau satu kelompok yang memenuhi syarat membuka pencatatan langsung. Sheet tersedia untuk pilihan ambigu dan hanya menawarkan musim terbuka serta kelompok yang sudah ditanam (`startableOnly`). Tanpa greenhouse terpilih, Beranda memuat satu kartu per greenhouse dengan `Jadwal hari ini` dan `Pengukuran terakhir`, serta satu kartu `N catatan perlu diperbaiki` yang membuka daftar catatan beralasan pengelola. Panel `Pembaruan data` tidak ada. Pekerja dapat kembali ke daftar greenhouse melalui tombol atau Back. Pada layar sempit dan font besar, ilustrasi memberi ruang bagi teks dan tombol. [HomeContextCard.kt](android/app/src/main/java/com/inofarm/jogja/feature/context/HomeContextCard.kt)

Pekerjaan hari ini memakai baris agenda ringkas. Jadwal kelompok, catatan akun sendiri, koreksi verifikasi, dan kiriman tertahan diberi judul terpisah. Realisasi dan catatan dilewati terlihat sebagai status tertulis. Kegiatan tanpa jadwal tetap dapat dicatat.

Beranda menampilkan dua kartu setara, EC dalam mS/cm dan PPM dalam ppm, dari satu catatan manual yang memenuhi syarat pada greenhouse dan kelompok terpilih. Saat catatan hari ini belum tersedia, tampilkan catatan terakhir yang memenuhi syarat beserta waktu WIB dan statusnya. Catatan historis berisi PPM saja dapat menampilkan `EC belum tersedia`. Tampilkan keadaan memuat atau kosong dengan teks. Ringkasan sumber membedakan data offline, belum tersinkron, dan diterima server. `Lihat sumber` membuka waktu, jenis input, dan pencatat bila tersedia. Pencatatan dibuka dari tombol Catat. Beranda tidak memiliki tombol Catat tersendiri. [MeasurementDashboard.kt](android/app/src/main/java/com/inofarm/jogja/feature/activities/MeasurementDashboard.kt) · [Kontrak pengukuran](contracts/v2/MEASUREMENTS.md)

### Form, review, dan riwayat

Verifikasi pengelola dibuka dari Beranda lewat `Cek catatan pekerja` atau kartu jumlah catatan yang perlu dicek, dengan kategori Kegiatan dan Panen. Kartu menempatkan jenis, greenhouse, pencatat, waktu dan status sebelum rincian teknis. Detail menempatkan catatan aktual dan acuan berversi berdekatan, lalu keputusan pengelola dan histori. Antrean perangkat pekerja dan pengelola dibuka dari penanda belum terkirim di toolbar. Detail pekerja menampilkan alasan keputusan pada revisi catatan sendiri. Form pekerja memakai picker tanggal dan jam Android, mengunci jenis kegiatan bila berasal dari jadwal, koreksi, atau pengukuran, dan tidak menampilkan pilihan acuan koreksi: acuan dipertahankan otomatis dan dinilai ulang hanya bila waktu kejadian berubah, dengan isian manual yang dapat dibuka untuk mempertahankan detik. HSS/HST dan acuan historis tampil dekat waktu kejadian.

`FarmSection` mengelompokkan isian berdasarkan pekerjaan, misalnya waktu, konteks, hasil, atau catatan. Pertahankan satuan, bantuan, jenis keyboard dan kesalahan di dekat isian. Review memakai pasangan label dan nilai dalam panel. Bedakan `Simpan draf dan kembali`, tindakan periksa, `Kembali ke isian`, serta `Simpan`, yang menyimpan di perangkat dan menaruh catatan di antrean kirim. Form pekerja memakai `Simpan sebagai draf` dan `Kirim`. Pesan hasil simpan pekerja tampil sebagai snackbar pada layar tujuan dan hilang sendiri. Status draf menyebut penyimpanan di perangkat. Antrean dan riwayat menyebut penerimaan server secara eksplisit.

Judul ringkas memberi orientasi di menu. Toolbar merangkum cakupan riwayat. Filter mengelompokkan cakupan, jenis dan status, serta periode WIB. Pekerja memilih `Dari` dan `Sampai` dengan picker tanggal Android; pengelola mengetik tanggal. Pekerja mendapat chip `Catatan saya`, aktif secara bawaan. Baris pekerja menulis `Draf · tersimpan di perangkat` atau `Terkirim`, disusul `Belum dicek`, `Sesuai`, atau `Perlu diperbaiki`. Baris solid mendahulukan jenis catatan, grade atau nominal, disusul konteks, waktu, dan status. Panen menampilkan cuplikan per grade di riwayat dan rincian lengkap di detail. Saat pengguna meninjau konflik, dua panel memisahkan versi lokal dan server beserta alasannya. Antrean mendahulukan pengajuan yang perlu ditinjau dan menyimpan ID serta percobaan di rincian operasi pengelola. Antrean pekerja hanya memuat catatan yang belum terkirim atau perlu dicek dan tidak menampilkan ID maupun percobaan. Back dari detail yang dibuka lewat antrean kembali ke antrean. [Komponen menu](android/app/src/main/java/com/inofarm/jogja/core/design/FarmMenu.kt) · [Riwayat](android/app/src/main/java/com/inofarm/jogja/feature/history/HistoryScreen.kt)

### Keuangan dan laporan

Rekap pengelola memakai pemilih greenhouse dan kelompok, lalu menunjukkan varietas, populasi tanaman, target buah, hasil layak dan cacat, serta capaian. Tindakan selesai ada sesudah rekap dan meminta alasan. Pekerja membuka Progres panen dari Beranda atau dari Riwayat panen. Bidang sage menonjolkan hasil layak dan target. Rekap di bawahnya menunjukkan hasil menurut kondisi dan keputusan, dengan sumber per revisi. Kelola greenhouse dan Kelola akun tersedia dari tab Kelola.

Keuangan memisahkan konteks dan tombol `Catat pengeluaran` pada blok Catatan baru dari toolbar Riwayat. `Alat keuangan` mengelompokkan biaya dan alokasi, investasi awal/aset dan penyusutan, penjualan dan penerimaan, evaluasi, serta pemeliharaan. Laporan dan investasi menempatkan periode dan basis di atas kelompok hasil, dengan alasan tetap terlihat saat hasil belum tersedia atau tidak valid. Rincian rumus, sumber, input, dan diagnostik dapat dibuka. [FinancialEntryScreen.kt](android/app/src/main/java/com/inofarm/jogja/feature/expenses/FinancialEntryScreen.kt) · [Kontrak operasional](contracts/v2/README.md)

### Catat, belum terkirim, dan Kelola (pengelola)

**Tombol Catat.** `ExtendedFloatingActionButton` berikon tambah dengan teks `Catat`, tampil hanya pada tab Beranda dan Riwayat ketika bar navigasi tampil dan tidak ada penulisan berjalan (tag `finance-record-fab`). Tombol membuka sheet `Catat` berisi baris aksi berikon dengan keterangan: Kegiatan, Pengukuran, Panen, Biaya, dan Penjualan. Pekerja hanya melihat Kegiatan, Pengukuran, dan Panen. Bila greenhouse terpilih belum punya tanam saat ini, baris `Mulai tanam` muncul paling atas. Sheet memakai bidang `surface` dan membuka penuh tanpa tahap setengah. Jika belum ada greenhouse terpilih untuk biaya atau penjualan, pemilih greenhouse muncul lebih dahulu. [ManagerSheets.kt](android/app/src/main/java/com/inofarm/jogja/feature/ManagerSheets.kt)

**Penanda belum terkirim.** Tombol teks di toolbar berikon sinkronisasi dan label `N belum terkirim`, hanya tampil bila ada operasi yang belum diterima atau digantikan. Perubahan jumlahnya diumumkan sebagai live region sopan. Sentuhan membuka sheet penuh (tinggi 92%) berjudul `Belum terkirim` yang memuat antrean perangkat dengan aksi kirim sekarang. Pekerja memakai penanda yang sama dengan ikon jadwal. Sheet pekerja hanya memuat catatan belum terkirim atau perlu dicek, dengan `Kirim sekarang`, tanpa ID pengiriman dan jumlah percobaan.

**Daftar Kelola.** Judul `Kelola`, lalu kartu bergaris tipis berisi `Tambah greenhouse`, `Greenhouse`, dan `Pekerja dan akun` sebagai baris aksi berikon. Bagian `Data master` (jenis melon, pupuk dan bahan lain, gram/liter/buah, kelas hasil panen, pengelompokan pengeluaran) memakai baris teks tanpa ikon dengan chevron karena ikonnya akan sama semua. Bagian `Jadwal dan target` memuat Jadwal dan Target. Kartu `Akun saya` menutup daftar dengan nama, peran, dan tombol `Keluar`.

**Chip Riwayat.** Chip filter (`Semua kegiatan`, `Pengukuran`, `Panen`) menyaring daftar Riwayat dan menandai pilihan aktif. Chip bantu (`Biaya`, `Penjualan`, `Aset`) berchevron dan tidak memiliki status terpilih. Sentuhannya membuka tab Keuangan, tempat catatan uang berada. Perbedaan bentuk ini membuat chip yang menyaring dan chip yang berpindah halaman dapat dibedakan.

**Chip bagian greenhouse.** Setelah memilih satu greenhouse, Beranda menampilkan baris chip filter `Ringkasan`, `Kegiatan`, `Panen`, `Keuangan`, dan `Pengaturan`, yang membungkus ke baris berikut bila sempit. Bagian aktif menentukan panel di bawahnya. Pengaturan memuat Musim dan kelompok tanam, Pekerja di greenhouse ini, serta Ubah atau nonaktifkan. Header forest memuat nama greenhouse, tanggal WIB, waktu pembaruan, status Aktif, fakta tanam saat ini (varietas, kelompok, HSS/HST, fase), dan tombol `Ganti tanam saat ini` atau `Pilih tanam saat ini`. Pada tampilan semua greenhouse, header menawarkan `Tambah greenhouse` sebagai tombol bergaris, dan kartu `N catatan pekerja perlu dicek` memuat tombol `Buka daftar`.

**Wizard Tambah greenhouse.** Dialog layar penuh berlatar `background` dengan judul sheet dan tombol tutup. Tiga langkah: Greenhouse, Mulai tanam (opsional, dapat `Lewati`), dan Pekerja (opsional, dapat `Lewati`). Teks `Langkah N dari 3` berada di atas judul langkah. Selama menyimpan, teks progres per tahap (misalnya `Menyimpan musim…`) diumumkan sebagai live region disertai indikator linear. Kesalahan isian tampil sebagai pesan pendukung di bawah isian terkait, dan pesan kegagalan umum tampil sebagai pemberitahuan di atas langkah. Setiap langkah tersimpan saat pengguna melanjutkan. Isian yang sudah tersimpan dikunci, dan `Kembali` hanya mundur satu langkah. Layar akhir berjudul `Greenhouse siap dicatat` memuat nama, ringkasan tanam dan pekerja, tombol `Catat kegiatan` bila tanam sudah ditetapkan, dan tombol bergaris `Ke greenhouse`. [GreenhouseWizard.kt](android/app/src/main/java/com/inofarm/jogja/feature/admin/GreenhouseWizard.kt)

### Form pengelola Canopy (Fase 8)

Berlaku untuk pengeluaran, alokasi, penjualan, aset, penyusutan, kegiatan, pengukuran, panen, form admin, form akun, wizard Tambah greenhouse, dan semua langkah Periksa. Kode ada di [FormCanopy.kt](android/app/src/main/java/com/inofarm/jogja/feature/FormCanopy.kt). Form **pekerja tidak berubah** (K8): cabang `worker` memakai tata letak lama dan tag yang sama; yang berubah bagi pekerja hanya font, gaya sheet Catat, dan login.

- **`FarmFormFrame`** menyediakan `LocalCanopyForm = true`, area gulir (jarak vertikal 12 dp, antarblok 12 dp) di atas, dan bar yang tetap di atas keyboard. Di dalam bingkai, `FinanceField` menaruh label di atas isian (semibold) dan memakai radius 14 dp.
- **`FarmFormHeader`**: tombol X (menyimpan draf lalu kembali, deskripsi `Simpan draf dan kembali`), judul `titleLarge` bold, dan status draf.
- **`FarmContextStrip`**: strip forest radius 16 dp berisi ubin ikon 40 dp, greenhouse (`titleSmall` bold), tanam, dan umur di kanan bila ada.
- **`FarmFormSection`**: kartu `surface` dengan garis 1 dp, padding 16 dp, judul `titleSmall` bold sebagai heading.
- **`FarmFormBar`**: bar `surface` penuh lebar (menetralkan padding halaman 16 dp lewat `fullBleed`), garis atas `HorizontalDivider` `outlineVariant`, bayangan 4 dp, tombol radius 16 dp tinggi minimum 52 dp. Kiri `Simpan draf` (bergaris, bobot 1), kanan aksi utama (bobot 1,4). Tag mengikuti form, misalnya `expense-save-draft`. Bar berada di luar area gulir, sehingga tes memakai `scrollIfScrollable()`.
- **`FarmReviewFrame`** (langkah Periksa): judul `Periksa {jenis}`, strip konteks, kartu rincian, dan bar `Ubah isian` / `Simpan` berikon centang.
- **`FarmWhenCard`**: waktu kejadian sebagai satu baris terbaca lewat `whenLabel` (`Kamis, 12 Nov 2026 · 21:13 WIB`; nilai ketikan dipertahankan bila tanggal tidak valid), tombol `Ubah` membuka isian tanggal dan jam, tinggi berubah 190 ms. Dipakai form pengukuran dan panen.
- **`FarmFieldPair`**: dua isian angka berdampingan, bertumpuk bila skala font di atas 1,3.
- **`FarmDetailCard`**: satu rincian berulang (bahan, grade) sebagai kartu radius 14 dp, ikon hapus merah di kanan atas.
- **`FarmInfoCard`**: catatan acuan dan umur di akhir form, nada info tanpa garis.
- **`FarmStepIndicator`**: tiga segmen setebal 6 dp untuk wizard; langkah selesai bercentang. Disembunyikan dari semantik karena teks `Langkah N dari 3` sudah dibaca.
- **`FarmRoleCards`**: peran akun sebagai dua kartu (radius 14 dp, terpilih berisi `primaryContainer` dengan garis 2 dp `primary`), bertumpuk bila skala font di atas 1,3.

**Aturan chip dan segmen.** Pilihan pendek memakai `FarmChoiceChips` (menggantikan dropdown `Choice`, tanda tangan sama). Wadah diberi tag pada node inert 1 dp, sehingga tes yang menyentuh wadah lebih dulu tidak berbuat apa-apa, dan setiap chip bertag `$tag-option-$id`. `FarmRoleCards` mengikuti aturan yang sama. Aturan ini dipertahankan agar tes lama kompatibel. Chip: tinggi minimum 48 dp, radius 12 dp, terpilih `primary` dengan teks `onPrimary`, tak terpilih `surface` dengan garis `outline` 1 dp, peran radio, `FlowRow` atau gulir horizontal bila `scroll = true`. Pilihan panjang tetap memakai `FarmPicker`. `FarmSegmentedTabs` dipakai untuk bagian greenhouse dengan tag `hub-section-$id`.

**Chip Cacat amber.** Pada pilihan kondisi panen, chip `Cacat` terpilih memakai `warning` (#805016) dengan teks putih (kontras 6,83:1) lewat `warningId = HarvestQuality.DEFECTIVE.wire` di `HarvestScreen.kt`. Pasangan ini khusus chip terpilih; status teks tetap tertulis.


### Motion dan umpan balik

Panel login masuk selama 240 ms. Label tombol merespons status request selama 160 ms, dan ruang pesan dapat berubah tinggi selama 200 ms. Shell baru muncul dengan fade 220 ms setelah sesi tersedia. Indikator tab dan warna status draf bertransisi selama 200 ms. Review memakai fade dan pergeseran 12 dp selama 220 ms. Kartu audit dan baris rumus berubah tinggi saat rincian dibuka. Sheet mengikuti transisi Material. Compose memakai skala animasi sistem. Angka pengukuran dan nominal langsung menampilkan nilai tersedia, dan ilustrasi tetap diam.

Beranda pengelola memakai transisi singkat pada pilihan greenhouse, pembukaan sumber pengukuran, dan review periode. Pergantian cakupan segera mengikat isi ke pilihan baru. Pemutakhiran data pada cakupan yang sama mempertahankan daftar yang masih sah.

Pada pekerja, panel greenhouse baru muncul dalam 230 ms. Perpindahan dua tab memakai perubahan indikator tonal dan masuk singkat 180 ms. Posisi gulir tab disimpan. Bagian opsional dan sumber berubah tinggi dalam 180–200 ms. Nilai buah dan kg tampil langsung. Batang capaian beralih antar-snapshot dalam 240 ms. Umpan balik draf dan antrean mengikuti status penyimpanan yang sudah terjadi dan dapat diumumkan sebagai live region. [Bukti dan matriks gerak pekerja](docs/context/WORKER_ALIGNMENT_EVIDENCE_20261110.md)

### Canopy (12 November 2026)

Token tambahan Canopy di `InofarmTheme.kt`: `FarmShape` (Tile 14, Dock 26, Chip 10, Sheet 28 dp), `FarmType.HeroAmount` (22/28 bold), `HeroNumber` (26/30 extrabold) dan `Wordmark` (44/48 extrabold, jarak huruf -1,2 sp); dua yang pertama memakai `tnum`. Nada status (`FarmTone`) memetakan ke peran yang ada: Neutral, Good, Info, Amber (`warning` pada `warning-surface`, gelap `#DCC28C` pada `#493519`), Error, Forest, Brand.

Arah Canopy memakai bidang forest sebagai kanopi di atas (hero login, hero Beranda, hero detail, bar navigasi pengelola) dan bidang solid di bawahnya. Tidak ada warna baru; semua warna berasal dari token di atas. Komponen bersama ada di `core/design/CanopyKit.kt`: `farmPressable` (skala tekan 0,97 atau 0,98 dengan spring, angkat 2 dp saat hover), `farmStaggerEntrance` (sekali per proses), `FarmLeafMotif` (garis daun sage), `FarmIconTile`, `FarmStatusChip` (teks tetap tertulis, warna hanya penanda), `FarmHeroCard`, `FarmQuickAction`, `FarmSegmentedTabs`, `FarmChoiceChips` (menggantikan dropdown untuk pilihan pendek; tag wadah ditaruh pada node 1 dp yang inert dan tiap chip bertag `$tag-option-$id`), dan `FarmDockNavigation`. Pengelola memakai dock lima slot: Beranda, Riwayat, Catat (lingkaran sage terangkat, tag `finance-record-fab`), Keuangan, Kelola. Dock berbentuk kapsul 26 dp (`#263F34`, bayangan 2 dp, tinggi minimum 72 dp) dengan pil terpilih `#385442`; tombol Catat 56 dp di dalam cincin 66 dp, naik 26 dp dari bar, dan label dock berhenti membesar setelah skala font 1,3. Pekerja tetap dua tab dan FAB. Angka tidak dianimasikan dan tidak ada animasi tak berhingga. Gerak masuk (`farmStaggerEntrance`: fade dan naik 12 dp, 280 ms, jeda 40 ms per item, item ke-7 dan seterusnya langsung tampil) hanya berjalan sekali per proses per kunci. Tekan memakai spring (redaman 0,7) dengan skala 0,97 untuk tombol dan 0,98 untuk kartu, angkat 2 dp saat hover. Pil tab dan dock bergeser dengan spring lebih tenang (redaman 0,9) dan tidak beranimasi pada tata letak pertama. Konstanta ada di `FarmMotion` (tekan 120 ms, status 200 ms, navigasi 220 ms). Isian tanggal dan jam pengelola memakai `FinanceDateField` dan `FinanceTimeField`: teks tetap bisa diketik, ikon membuka picker Android.

## Do's and Don'ts

- Batasi ilustrasi ke tepi atas login dan Beranda agar data, form, dan menu tetap mudah dipindai.
- Pertahankan teks, angka, dan pesan di atas bidang yang stabil. Simpan status dalam teks, dengan warna sebagai penanda tambahan.
- Tampilkan sumber, waktu WIB, dan alasan yang relevan. Gunakan keterangan belum tersedia untuk data kosong dan label penerimaan server untuk operasi yang sudah diterima.
- Pertahankan skala font pengguna, urutan fokus, inset sistem, dan ruang gulir. Jaga navigasi pekerja tetap dua tab. Jaga navigasi pengelola tetap empat tab dan jangan menambah warna baru untuk aksi Catat atau penanda belum terkirim.
- Gunakan status dan hasil dari kontrak operasional. Catat hasil render dan verifikasi per perangkat serta konfigurasi di [context frontend](docs/context/README.md).
- Pertahankan form Canopy pengelola pada `FarmFormFrame`, `FarmFormSection` dan `FarmFormBar` penuh lebar dengan garis atas. Jangan memberi pekerja bingkai itu tanpa keputusan baru (K8).
- Ganti dropdown pendek dengan `FarmChoiceChips` dan pertahankan tag wadah serta `$tag-option-$id`.
- Jangan menambah animasi tak berhingga. Gerak masuk berjalan sekali per proses.

## Celah terbuka

Bukti berasal dari emulator API 30 (411 dp) dengan fixture, bukan perangkat fisik ([bukti](docs/context/FRONTEND_CANOPY_EVIDENCE_20261112.md)). Belum ada atau belum terbukti:

- Isian nominal belum menampilkan pemisah ribuan (butuh keputusan karena menyentuh tampilan nilai yang divalidasi); kartu forest 34 sp untuk nominal pengeluaran belum ada.
- Wizard langkah 2 dan 3 belum terlihat di layar; hanya langkah 1.
- Form pada mode gelap dan 200% hanya sebagian (pengukuran dan panen 200% ada). Chip Cacat amber pada permukaan gelap belum dilihat. Lebar 360 dp belum diperiksa.
- Dialog (wizard, admin) memakai bilah sistem gelap pada mode terang (sudah demikian sebelum Canopy). Picker greenhouse dari hero Keuangan berupa dialog forest bahkan pada mode terang.
- Tombol `Tambah ...` masih garis penuh, bukan garis putus. Baris rincian penjualan masih memakai tombol teks `Hapus rincian N`, bukan `FarmDetailCard`. Tanggal dan jam form kegiatan bertumpuk. Langkah Periksa belum punya baris angka utama dua kolom.
