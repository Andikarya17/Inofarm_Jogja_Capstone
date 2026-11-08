# Pematangan UI pengelola, 9 November 2026

[Spesifikasi](MASTER_PROMPT_MANAGER_UI_POLISH.md) · [Audit referensi](MANAGER_UI_REVIEW_20261109.md)

## Baseline runtime sebelum perubahan UI

Build `:app:assembleDebug` dari `1e721f77bcb9a6bf594ada12dd1f6e5dc2991d94`. `git diff d6774fc HEAD -- android` kosong sebelum penambahan harness. APK debug standar `com.inofarm.jogja.finance`, versi 0.2.0, SHA-256 `b79131ab5b0a8768dacd56a18fb84b65d4a19768b822f080b7093933fe8289d2`. Instalasi dan peluncuran cold start berhasil pada AVD baru `Inofarm_Manager_Polish_API30`. API 30, 1080 × 2400 piksel, 420 dpi (~411 dp), font 1,0, tema terang, navigasi sistem tiga tombol. API 37 dicoba lebih dulu dan boot ADB pada AVD baru tidak siap. Data AVD sebelumnya dipertahankan.

Instrumentasi membuka `MainActivity`, masuk sebagai pengelola sintetis lewat HTTP `MockWebServer` lokal, dan membuka layar Compose. Skenario laporan, investasi, ekspor, dan beberapa keadaan pemulihan memakai harness terpisah yang memuat komponen produksi. Semua transaksi simulasi memakai fixture. Log, hash, PNG utuh, dan pasangan tangkapan berskala proporsional tersedia lokal di `outputs/manager-ui-polish/`.

| Referensi audit | Tangkapan baseline | Konfigurasi / perbandingan |
| --- | --- | --- |
| `frontend-secondary-light-412/login-ready.png` | `login-ready.png` | Panel, urutan field, pesan, dan daun cocok. API 30 menampilkan tiga tombol sistem, sedangkan referensi API 37 memakai gestur. Crop tepi sedikit berbeda. |
| `manager-home/after/light-412-picker.png` | `manager-walk-picker.png` | Radio dan sorotan tonal sama, tanpa garis antaropsi. Referensi memperlihatkan greenhouse aktif dan dua opsi fixture nyata pada font 130%. Baseline membuka semua greenhouse dengan lima opsi sintetis pada font 100%. |
| `frontend-secondary-manager-home-real/real-overview.png` | `manager-walk-overview.png` | Header forest dan kartu greenhouse sejenis. Referensi tema gelap dan dua greenhouse pada API 30 dengan fixture Laravel/MySQL. Baseline terang dan lima greenhouse melalui mock HTTP, termasuk periode aktif. |
| `before-after-menu.png`, baris sesudah | `secondary-queue-responsive.png`, `secondary-activities-responsive.png`, `secondary-harvest-responsive.png`, `secondary-finance-responsive.png` | Header forest, toolbar filter, ilustrasi viewport dan navigasi cocok. Isi daftar berbeda karena beberapa tangkapan awal menguji state kosong. Tangkapan lain mengisi catatan sintetis dan antrean offline. |
| `frontend-secondary-light-412/secondary-activities-filter.png` | `secondary-activities-filter.png`, `manager-walk-choice.png` | Sheet filter mempertahankan urutan isian. Dialog pilihan jenis masih berupa teks tanpa radio, sorotan aktif, atau garis. |
| `frontend-secondary-light-412/manager-finance-tools.png` | `manager-finance-tools.png`, `manager-walk-tools-no-scope.png` | Label dan kelompok lama cocok. Posisi atas sheet bergeser karena inset sistem dan tinggi konten. Versi tanpa cakupan menampilkan alasan transaksi dinonaktifkan. |
| `before-after-transaksi.png`, baris sesudah | `secondary-allocation-review.png`, `secondary-sale-review.png`, `secondary-asset-review.png` | Ringkasan alokasi sejenis. Baseline penjualan dan aset sudah menempatkan nominal serta fakta berlabel lebih tinggi daripada sebagian referensi 7 November. Gunakan hierarki yang sudah ada sebagai titik awal. |
| `before-after-privat.png` dan `season-report.png` | `secondary-attachment-detail.png`, `season-report.png`, `investment-report.png`, `secondary-recovery.png` | Deretan kartu indikator dan diagnostik mentah cocok. Laporan dan investasi sama-sama memakai harness font 200%, dengan posisi gulir dan inset berbeda. Lampiran baseline sempat menangkap state memuat. Cadangan memakai fixture, dan IME serta tema harness berbeda dari sebagian referensi. |
| Audit source akun dan administrasi | `manager-walk-account.png`, `manager-walk-admin*.png` | Screenshot runtime pertama untuk area ini. Identitas, tindakan dan Keluar menumpuk tanpa separator. Cakupan, editor, dan dialog konfirmasi tersedia. |

Walkthrough baseline menjangkau lima tab, cakupan greenhouse dan periode, jadwal/realisasi, pemilih kegiatan, filter, seluruh tujuan alat, form dan review transaksi, konflik, lampiran, ekspor, pemulihan, serta administrasi. Dari 20 kasus pertama, 19 lulus. Kasus penyusutan berhenti saat Compose Test melaporkan `performMeasureAndLayout called during measure layout`. Penyusutan dan skenario login ditolak lalu berhasil lulus pada ulang uji baseline (`before/device-retry.log`). Rekaman baseline ada di `before/login.mp4` dengan skenario penyusutan lebih dulu dan login pengelola di bagian akhir.

## Arah kerja

Mode Operate. Dials 2 / 2 / 2 untuk login dan menu, 1 / 2 / 2 untuk pemilih, form, dan laporan. Bidang forest menjaga orientasi, sage menunjukkan pilihan aktif, dan bidang krem menjaga fokus pada isi. Dalam laporan, angka tabular dan pembagian indikator mengikuti pertanyaan pengelola. Botani yang sama menjadi aksen di login dan Beranda. Motion login memberi umpan balik pada request dan menandai saat sesi sah membuka shell, dengan satu fade per permukaan.

## Inventaris perubahan

| Area | Presentasi dan fungsi yang diperiksa | Bukti emulator |
| --- | --- | --- |
| Login, shell, akun | Crop botani, panel masuk, transisi label dan pesan, fade saat shell masuk. Akun memisahkan identitas, Administrasi, dan Keluar. Empat tujuan pekerja dan lima pengelola tetap tersedia. | `login-ready`, `login-ime`, `manager-walk-account`, video login, tes IME dan submit tunggal |
| Beranda, greenhouse, periode, jadwal | Header lebih pendek, judul bagian datar, chevron pada greenhouse, pemilih bersama. Periode aktif memakai bidang tonal dan penetapan memiliki tampilan tinjauan tersendiri. Jadwal mendahulukan waktu dan jenis. | `manager-walk-overview`, `manager-walk-picker`, `manager-walk-period`, `manager-walk-period-review`, `manager-walk-schedule`, `single-greenhouse`, `measurement-source`, `cash`, `offline` |
| Antrean dan konflik | Ringkasan angka yang berasal dari antrean, status mendahului jenis catatan, rincian operasi dapat dibuka. Perbandingan lokal dan server tetap memakai dua panel berlabel, dengan alasan dan keputusan tersendiri. | `secondary-queue-*`, `secondary-activity-conflict`, tes offline, konflik, audit dan Back |
| Kegiatan dan filter | Judul menu lebih ringan. Filter mengelompokkan cakupan, jenis dan status, serta periode WIB. Pemilih dalam filter memakai dialog native. Form kegiatan memakai baris pilihan bersama. | `secondary-activities*`, `secondary-filter-responsive`, `manager-walk-choice`, `secondary-activity-form`, `secondary-activity-review` |
| Panen | Satuan berada pada label, grade tetap terpisah, konteks tinjauan lebih ringkas. Saldo berbasis versi tetap muncul di detail. | `secondary-harvest*`, tes multigrade, IME dan audit |
| Hub keuangan dan alat | Catatan baru terpisah dari riwayat. Catat pengeluaran tetap aksi utama. Tujuh tujuan alat berada dalam empat kelompok dengan ikon, keterangan, divider dan alasan jika cakupan belum tersedia. | `manager-finance`, `manager-finance-tools`, `manager-walk-tools-no-scope` |
| Pembayaran, alokasi, penjualan | Nominal berada di awal pembayaran dan alokasi. Review memisahkan sumber, musim, basis dan saldo versi. Penjualan menempatkan total sebagai fokus dan memisahkan bobot dari harga per kg. | `manager-payment*`, `secondary-allocation-*`, `secondary-sale-*` |
| Aset, penyusutan | Identitas, pembayaran sumber, dan umur pakai terpisah. Nominal alokasi dan anggaran periode punya panel tersendiri. | `secondary-asset-*`, `secondary-depreciation-*` |
| Evaluasi, investasi | Hasil dikelompokkan tanpa satu kartu per indikator. Basis, periode, alasan ketidaklengkapan, sumber, dan diagnostik tetap tersedia. Kontrak investasi aktif memakai NPV, IRR, PP dan DPP. | `season-report`, `investment-report`, keduanya harness font 200%, tes semua indikator dan pagination sumber |
| Lampiran, ekspor, pemulihan | Nama, ukuran, jenis, status dan induk berkas dikelompokkan. Ekspor menunjukkan format dan nama berkas saat proses. Cadangan memisahkan dampak, alasan, status dan rincian paket. | `secondary-attachment-*`, pemilih dokumen fixture, `secondary-recovery`, `manager-walk-recovery` |
| Administrasi | Header tetap, cakupan dan daftar terpisah, editor serta review menampilkan isian berlabel. Review akses menampilkan dampak sebelum Ajukan. | `manager-walk-admin*`, tes semua pilihan resource dan draf 200% |

Endpoint, kalkulasi, repositori bisnis, SQLite, kontrak, dan Laravel berada di luar diff. Uji emulator memakai fixture HTTP atau repository uji dan penyimpanan lokal sintetis. Tidak ada data pengguna yang dihapus atau transaksi bisnis yang dikirim.

## Build dan login pada APK akhir

APK debug SHA-256 `6fda6034d96e0bb6c83bc8eee435d19ecac74815c8e131e5ab2d5b7f2b78be13`. `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` (73 tes) dan `lintDebug` berhasil. Lint melaporkan 0 error, 16 peringatan dan 1 hint. Lihat `after/build-final.log` dan `after-final/identity.txt`.

Rekaman `motion-on/login.mp4` dan `motion-off/login.mp4` berasal dari `MainActivity` pada APK akhir, dengan server HTTP sintetis di loopback host. Keduanya merekam isian, label Memproses, pesan penolakan, percobaan ulang, dan Beranda. Server menerima dua request login per rekaman, dengan penolakan yang disengaja pada request pertama. Skala animator, window, dan transition masing-masing 1 atau 0 sesuai rekaman. `login-loading.png`, `login-error.png`, dan `login-success.png` tersedia pada kedua konfigurasi. Durasi transisi pada source adalah 240 ms untuk panel login, 160 ms untuk label tombol, 200 ms untuk ruang pesan, dan 220 ms untuk shell. Rekaman memverifikasi perubahan state secara visual, tanpa pengukuran performa frame perangkat fisik.

Pemeriksaan luminansi sRGB mencakup 19 pasangan peran aktual dari tema terang dan gelap. Rasio pasangan teks aktif yang diukur 6,04:1 sampai 14,72:1. Radio tidak aktif dan fokus diukur terpisah terhadap permukaan, minimal 4,52:1. `contrast.json` memuat warna foreground/background, ambang dan hasil per pasangan. Divider memperkuat pemindaian bersama posisi dan target sentuh.

### Regresi

- API 30, lebar sekitar 412 dp, terang, font 100%. Semua 20 skenario yang diulang dari baseline lulus pada APK akhir (`after-final/device.log`). Skenario administrasi melintasi 12 pilihan jenis data. Skenario periode memeriksa langkah review dengan konfirmasi nonaktif sebelum alasan diisi. Putaran antara baseline dan APK akhir sempat mencari label kelompok lama di satu assertion, lalu assertion memakai label `Transaksi`.
- Regresi tambahan API 30 lulus 35 tes (`extra/device.log`). Cakupan mencakup navigasi pekerja dan pengelola, penolakan login dan submit tunggal, pencabutan greenhouse, respons scope terlambat, lanskap, konflik, sync dan transaksi SQLite fixture.
- Tema gelap pada 320 dp dan font 200% lulus lima tes, termasuk picker aktif, Alat keuangan, administrasi, form dengan IME dan lanskap (`dark-320/device.log`). Tema terang pada 360 dp dan font 130% lulus satu tes reflow menu/filter/sheet (`light-360/device.log`). Animasi sistem disetel ke nol pada kedua rangkaian adaptif.
- Keyboard perangkat membuka tab Keuangan dan pemilih greenhouse dengan Tab/Enter. Back menutup dialog dan mengembalikan fokus ke `Greenhouse pencatatan`. `after-final/keyboard.log` dan `after-final/keyboard-*.png` mencatat sampelnya.
- Dua fase lintas proses lulus setelah `force-stop` pada APK emulator uji. Draf mentah, parent periode, ID dan hash antrean serta pasangan EC/PPM terbaca kembali (`after/process-prepare.log`, `after/process-verify.log`).
- `check-finance-context.py` memeriksa 348 tautan dan 8 hash arsip. `check-finance-contracts.py` memverifikasi fixture dan referensi kontrak v2. Oracle investasi lulus 259 kasus Fraction/Decimal. Pemeriksaan ini berjalan tanpa SQL.

### Penerimaan

| ID | Hasil berdasarkan bukti |
| --- | --- |
| UI-00 | APK sumber baseline dipasang dan dijalankan sebelum perubahan UI. Selisih struktur, konfigurasi, data dan versi dijelaskan pada tabel pembanding. |
| UI-01, UI-04 | Inventaris di atas menghubungkan area dengan PNG dan tes. PNG sebelum/sesudah dengan konfigurasi nominal sama ada pada `before-after-*.png`. Posisi gulir dan data fixture dapat berbeda. |
| UI-02, UI-03 | Opsi aktif dibaca melalui semantik radio, dipilih ulang, dan dibatalkan dengan Back. Daftar panjang dan semua resource admin dapat digulir. Empat tujuan transaksi/aset dinonaktifkan sebelum greenhouse dipilih. Tujuan alat dibuka melalui pengujian form dan workspace. |
| UI-05, UI-10 | Rekaman motion 1 dan 0. Uji submit ganda memeriksa satu request saat tombol sibuk. Waktu frame perangkat fisik belum diukur. |
| UI-06 | PNG dan kasus reflow pada sekitar 412/360/320 dp, font 100/130/200%, terang/gelap, lanskap dan IME. Kontras diukur pada pasangan aktual yang terdaftar. |
| UI-07 | Sentuh, Back dan IME diuji. Sampel Tab/Enter/Back mengonfirmasi fokus kembali pada pemicu Choice. Radio, nama kontrol, dan status pilihan diperiksa melalui semantik. Audit audio dan traversal TalkBack menyeluruh masih terbuka. |
| UI-08, UI-09 | Regresi draf, proses, offline/reconnect, konflik, scope, role, antrean dan nilai eksak lulus pada fixture. Kontrak dan kalkulator tidak berubah. |
| UI-11, UI-12 | Walkthrough mencakup state memuat, kosong, gagal, offline, last-known, dan tindakan utama pada route inventaris. Gabungan semua kondisi API, kegagalan penyimpanan, dan kontrol turunan belum diuji secara exhaustif. |

## Delivery Gate Anti Slop

| Blok | Hasil |
| --- | --- |
| Hard Gate | Build dan click-through Android tersedia. Foto, angka, dan status bisnis memakai data sebenarnya saat aplikasi dipakai. Angka demonstrasi berasal dari fixture. Uji pemilih, target sentuh, reflow, kontras, loading/error/empty, dan motion sistem mencakup skenario yang disebut di atas. R-32/R-35/R-39 masih parsial untuk traversal menyeluruh, TalkBack audio dan setiap varian kontrol. |
| Purpose | Aksen botani memakai gambar yang sudah ada. Gradasi meredakan tepi crop. Warna tonal menunjukkan pilihan atau konteks, ikon menunjukkan tujuan, dan chevron muncul pada aksi yang membuka tujuan. Divider membantu memindai daftar. Angka memakai formatter yang sudah ada. |
| Liveliness | Design Read ditetapkan sebelum perubahan UI. Forest untuk orientasi, sage untuk state terpilih, krem untuk isi. Fokus layar ada pada pilihan, status, atau data keputusan. Ritme berubah mengikuti hasil, daftar, dan form. |
| Craftsmanship | Skenario fixture melintasi lima menu, alur turunan dan dua peran. Status lokal dan diterima server tetap terbaca terpisah. C-4 tetap terbuka untuk verifikasi menyeluruh pada perangkat fisik dan aksesibilitas. |

FAQ, DOM, CSS, ARIA, pointer web, dan detector HTML Impeccable berstatus N/A untuk Compose. Bukti ini mencakup perangkat emulator. UAT mitra, perangkat fisik, dan penerimaan produksi tetap terbuka.

## Berkas serah terima

`outputs/manager-ui-polish/evidence.zip` berisi PNG asli baseline/akhir dan konfigurasi adaptif, perbandingan visual, rekaman login, log uji, serta dokumen ini. Perbandingan utama adalah `before-after-login-and-pickers.png`, `before-after-menus.png`, `before-after-transactions.png`, dan `before-after-private.png`. `render-adaptif.png` merangkum 320, 360, dan sekitar 412 dp. AVD uji dikembalikan ke lebar baseline, font 100%, tema terang dan pengaturan animasi semula.
