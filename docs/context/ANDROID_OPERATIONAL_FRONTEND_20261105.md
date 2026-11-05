# Rombak frontend Android Inofarm

5 November 2026, branch `codex/android-operational-frontend`, baseline `02592a0`. Pekerjaan mengikuti `MASTER_PROMPT_ROMBAK_FRONTEND_ANDROID.md`, Anti Slop During dan GPT Taste yang diadaptasi ke Compose. [Sistem visual](../../DESIGN.md) menyimpan komposisi, token dan alasan material.

## Cakupan dan penelusuran

| Kebutuhan | Penerimaan dan perubahan source | Uji dan bukti |
|---|---|---|
| KF-01, KNF akses dan UX | `FinanceLogin` memakai Email, Password dan Masuk. Endpoint berasal dari konfigurasi APK. Toggle password bernama, IME Next menuju password, Done mengirim jika siap. Menu akun memuat keluar dan administrasi sesuai peran. | `twoFieldLoginHasAccessiblePasswordToggleAndHandlesMissingConfiguration`, `glassFrontendCapturesWorkerManagerAndPreservesDraftNavigation`, PNG login dan beranda dua peran. |
| KF-02–06, EC/PPM | `HomeContextCard` memindahkan pemilih greenhouse, musim dan kelompok ke sheet. Pemilihan induk mengosongkan turunan. `MeasurementDashboard` menaruh dua metrik sebelum aksi, menumpuk kartu pada font besar, menjaga unit dan sumber pasangan terakhir. | `manualMeasurementAutosaveBackRecreationQueueAndDashboardKeepOnePair`, `twoManualCardsKeepLegacyEcUnavailableAtNarrowLargeFont`, PNG kartu kosong, pasangan antre dan pasangan diterima. |
| KF pencatatan/panen dan histori | `FinanceRoot` dan `HarvestScreen` membagi form serta review. Antrean menampilkan status dan alasan. `HistoryScreen` memakai kartu catatan, filter sheet dan panel perbandingan lokal/server. | Regresi draft, review, queue, sync, rekreasi, konflik, pembatalan, musim tertutup, filter, IME dan landscape pada `FinanceEntryUiTest`. |
| KF transaksi, aset, laporan dan investasi | `FinancialEntryScreen` menempatkan histori dalam satu daftar gulir. Panel alat mengelompokkan alokasi, penjualan, aset, penyusutan, evaluasi, investasi dan pemulihan. Form dan hasil memakai panel sesuai peran data. | `FinanceEntryUiTest`, `FinanceSeasonReportTest`, `FinanceInvestmentTest`, PNG ruang keuangan dan nominal besar. |
| KF lampiran, administrasi, ekspor dan pemulihan | Panel batas lampiran, kartu daftar, konteks admin dan ringkasan cadangan. Konfirmasi, pemilihan dokumen dan pembatasan existing tetap menjadi jalur tindakan. | `FinanceEntryUiTest`, `FinanceAdminTest`, `FinanceRecoveryTest`, `FinanceReportExportTest`. |
| KNF keterbacaan dan interaksi | `FarmChrome` memberi panel solid, indikator tab, fokus keyboard, material navigasi dan transisi review. `InofarmTheme` memberi peran light/dark dan skala. | Perbandingan PNG, kontras sRGB, ukuran emulator, rekaman dan frame timing. |

Server masih memakai email sebagai identitas. Username menunggu perubahan kontrak autentikasi. Paket release membutuhkan alamat HTTPS v2 dari konfigurasi build. [Cara menjalankan Android](../migration/finance-20261029/FIN17_INSTALL_RUN.md).

## Baseline dan hasil render

Baseline diambil ulang dari build `02592a0` pada `Inofarm_Main_Review_20261103`, `sdk_gphone64_x86_64`, Android API 37, 1080 × 2400 px pada 420 dpi (sekitar 412 dp), tema terang dan skala teks 1,0. Tes capture memakai akun sintetis dan HTTP MockWebServer. PNG login baseline menampilkan hasil keluar lokal saat server fixture sudah berhenti. PNG hasil menampilkan keluar yang dikonfirmasi fixture. Perbandingan login menilai susunan form.

| Dimensi | Baseline | Hasil |
|---|---|---|
| Hierarki | Login memberi ruang besar pada pengantar dan URL. Tombol beranda mendahului pengukuran. | Panel login berisi dua field dan satu aksi. Kartu konteks dan pengukuran mendahului tombol pencatatan. |
| Keterbacaan | Pilihan konteks selalu terbuka. Metrik memakai layout mendatar tetap. | Nama greenhouse menjadi fokus kartu. Label, nilai dan satuan metrik punya bobot berbeda dengan reflow pada ruang sempit. |
| Konsistensi | Radius kapsul sering dipakai pada aksi, field dan status. | Radius 12/20/26 dp membedakan kontrol, kartu dan navigasi. Panel review mengikuti struktur yang sama pada beberapa jenis catatan. |
| Navigasi | Keluar dan sinkronisasi menempati dua baris toolbar. Area menu keuangan dan histori menggulir terpisah. | Toolbar satu baris, menu akun, sheet konteks/filter/alat. Histori keuangan dapat digulir dalam daftar yang sama. |
| Feedback | Status pada kartu histori berupa badge besar. | Sync, pembatalan dan alasan tampil terpisah. Antrean membedakan operasi yang menunggu dari yang perlu ditinjau. |
| Identitas | Chrome tonal pada toolbar dan navbar. | Panel putih atau hijau gelap untuk pekerjaan, tint di satu permukaan navigasi, amber untuk kondisi yang perlu perhatian. |

## Lokasi bukti lokal

Folder `outputs/frontend-redesign/` berisi `before/`, `after-light-412/`, `after-dark-360-font130/`, `after-dark-320-font200/`, komparasi `before-after.png`, video `motion-glass.mp4`, `contrast.json` dan log pengujian. Bukti sintetis dan APK debug disimpan lokal.

## Verifikasi engineering

- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` dan `lintDebug` lulus. JVM 73 tes, nol kegagalan. Lint tidak melaporkan error. Log `delivery-build-checks.txt`.
- Putaran emulator awal mencatat 72 dari 76 tes lulus. Tiga kegagalan melibatkan helper penutup IME yang menargetkan Activity saat dialog memiliki fokus. Satu kegagalan melibatkan pengisian nama skenario pada daftar lazy. Helper kini memakai token jendela yang fokus. Tes investasi memastikan field sudah terlihat dan nilai raw masuk sebelum rekreasi.
- Enam tes terarah lulus sesudah perbaikan. Putaran berikutnya lulus 76 dari 76. Tinjauan visual 320 dp kemudian menemukan label tab terpecah dan label field panjang berimpit dengan outline. Implementasi memakai navigasi dua baris dan label di atas field untuk kondisi itu. Putaran dengan perbaikan reflow lulus 77 dari 77 tes dalam 353,727 detik (`regression-final.txt`).
- APK review diuji ulang setelah fallback solid ditambahkan pada skala teks besar. Lima tes pada 412 dp terang lulus, termasuk form pada skala 2,0 dengan IME dan landscape. Empat tes pada 360 dp gelap dan skala 1,3 lulus, termasuk login, pengukuran dan sampel frame. Empat tes pada 320 dp gelap dan skala 2,0 lulus, termasuk nama greenhouse panjang dan sesi kedaluwarsa. Setup percobaan pertama pada 320 dp gagal menemukan pesan sinkronisasi di bawah viewport. Helper kini menggulir ke pesan itu sebelum melanjutkan. Log `delivery-instrumentation.txt`, `delivery-login.txt` dan `delivery-rerun.txt` disimpan dalam folder ukuran masing-masing.
- Tes dua fase `FinanceProcessSurvivalTest` lulus setelah force-stop APK sintetis. Uji membaca kembali nilai raw draf, payload pengukuran, ID dan hash operasi pada proses yang berbeda. Log `process-prepare.txt` dan `process-verify.txt`.
- Pemeriksaan manual emulator menunjukkan fokus keyboard pada tab Panen dan Enter membuka Riwayat panen. TalkBack aktif dengan touch exploration dan memindahkan fokus ke heading Riwayat panen. Bukti `accessibility-review.png`. Pemeriksaan ini mencakup jalur tersebut.
- 52 pemeriksaan kontras pasangan warna utama lulus, termasuk simulasi tint navigasi di atas hitam/putih. Rasio terendah 4,922:1 untuk teks yang diukur dan 3,897:1 untuk batas nonteks yang diukur. Perhitungan menilai warna sRGB dan komposit lapisan.
- APK review memakai fallback solid pada skala teks di atas 1,4 sehingga daftar berakhir di atas navigasi. Spike beranda memakai tint 94% dan highlight tepi pada skala yang lebih kecil. API window blur Android bekerja antar-window. Untuk navigasi yang berada dalam window yang sama, implementasi memakai komposit alpha dan fallback solid tanpa jalur blur. [Android Window](https://developer.android.com/reference/android/view/Window#setBackgroundBlurRadius(int)).
- Sampel scroll beranda pada 360 dp, skala teks 1,3, tema gelap dan skala animasi 1 memakai enam swipe Android dengan durasi 420 ms. `gfxinfo` merekam 115 frame, 52 janky (45,22%), p50 19 ms dan p95 34 ms. Ini sampel debug dengan GPU emulasi. Hasil menunjukkan jank pada konfigurasi tersebut dan belum mengisolasi kontribusi tint terhadap frame timing. File `after-dark-360-font130/navigation-scroll-frames.txt`.
- Indikator tab, panel yang terbuka dan perpindahan review memakai animasi Compose. [Dokumentasi animasi Compose](https://developer.android.com/develop/ui/compose/animation/composables-modifiers).

APK debug `outputs/frontend-redesign/Inofarm-frontend-debug.apk`, SHA-256 `B589861247B5527AC01BF6ACA176EEB8B68D96B44FBC9B8076342AED2FDD55CC`.

## Anti Slop Delivery Gate

Status berlaku pada cakupan bukti. PARSIAL berarti pengujian masih terbuka.

### Hard Gate

| Aturan | Status dan bukti |
|---|---|
| R-02 | PASS. Pencarian sumber `feature/` dan `core/design/` tidak menemukan em dash pada copy UI. |
| R-03 | PASS pada ukuran yang diperiksa. PNG compact dan tes landscape/IME menunjukkan reflow field dan navigasi. |
| R-17 | PASS. EC/PPM berasal dari pasangan scoped. Jumlah antrean dihitung dari operasi lokal. |
| R-18 | PASS. Tidak ada testimonial di alur produk. |
| R-23 | PASS. Ikon Compose digunakan untuk fungsi yang diminta. Tidak ada logo, profil atau foto baru. |
| R-24 | PASS untuk destinasi yang diuji. Tab tetap membuka beranda, antrean, kegiatan, panen dan keuangan sesuai peran. |
| R-25 | PARSIAL. 52 pasangan yang diukur melewati ambang. Kombinasi state dan komponen yang lain belum dihitung satu per satu. |
| R-26 | PASS untuk kontrol yang diuji. Sheet, tab, akun, form, filter, antrean dan alat keuangan menjalankan callback nyata. |
| R-27 | PASS untuk state yang diuji. Konfigurasi kosong, riwayat kosong, data terakhir, gagal, draft, antre, penolakan dan konflik memiliki teks. |
| R-28 | N/A. Tidak ada FAQ. |
| R-32 | PARSIAL. Semantics, IME Next dan Back diuji. Pemeriksaan manual menelusuri Tab, Enter dan fokus TalkBack pada jalur Panen. Audit seluruh kontrol tetap terbuka. |
| R-33 | PASS. Kotlin Compose diedit di source. Skrip lokal digunakan untuk build, pengujian dan bukti. |
| R-34 | PASS untuk sampel. Tema mengikuti sistem. Ada render terang dan gelap pada ukuran yang dicatat. |
| R-35 | PARSIAL. Build, emulator dan click-through instrumented tersedia. Inventaris interaksi manual seluruh fitur belum lengkap. |
| R-36 | PASS. Angka performa berasal dari log emulator. Bukti release, UAT dan produksi tidak diklaim. |
| R-37 | PASS. Design Read dan rencana native ditetapkan sebelum edit. |
| R-38 | PASS. Fixture sintetis berada di instrumentation. Production path mengambil data scoped dari penyimpanan atau server. |
| R-39 | PARSIAL. Role tab, selected, label field, nama ikon dan focus ring Compose diperiksa pada alur inti. Audit seluruh kontrol tetap terbuka. Ketentuan HTML/ARIA web N/A. |
| R-40 | PASS pada cakupan. Tidak ada media tambahan di runtime. Frame timing berasal dari `gfxinfo`. Aturan hero image dan script HTML N/A. |

### Purpose Gate

| Aturan | Status dan bukti |
|---|---|
| R-01 | PASS. Highlight vertikal terbatas pada tepi chrome untuk membedakan lapisan. |
| R-04 | PASS. Ikon menandai akun, sync, visibilitas password dan tujuan tab. |
| R-06 | PASS. Sans sistem mendukung skala Android dan pembacaan nilai. |
| R-07 | PASS. Latar polos menjaga pemisahan kartu. |
| R-08 | PASS. Aksi memakai label langsung. |
| R-09 | PASS. Status memberi informasi nyata dan terbaca sebagai teks. |
| R-10 | PASS. Komposit kaca dibatasi pada satu permukaan navigasi. |
| R-12 | PASS. Elevasi tipis membantu menunjukkan bahwa navigasi berada di atas daftar. |
| R-13 | PASS. Tidak ada glow dekoratif. |
| R-14 | PASS. Kartu konteks, pengukuran, catatan dan review memiliki anatomi berbeda. EC/PPM setara. |
| R-19 | PASS. Motion digunakan untuk status, tab, transisi review, sheet dan buka detail. |
| R-22 | PASS. Identitas berasal dari konteks greenhouse dan data pengukuran. |

### Liveliness

| Pemeriksaan | Status dan bukti |
|---|---|
| Dials dan Design Read | PASS. ENERGY 2 / RHYTHM 2 / MOTION 2 dinyatakan sebelum implementasi. |
| Konsistensi dials | PASS pada render yang ditinjau. Struktur bervariasi antara ringkasan, panel, form dan daftar. |
| Fokus per layar | PASS pada render inti. Masuk berfokus pada kredensial, beranda pada budidaya, histori pada catatan, keuangan pada pembayaran dan riwayat. |
| Ruang sebagai struktur | PASS. Panel memisahkan konteks, nilai, dan tindakan. |
| Aksen | PASS. Amber menandai kondisi yang memerlukan perhatian. |
| Motif | PASS. Greenhouse, musim, kelompok dan pasangan EC/PPM memberi identitas operasional. |

### Craftsmanship dan Quality Locks

| Aturan | Status dan bukti |
|---|---|
| C-1, C-3 | PASS. Peran panel, radius, warna, tipografi dan motion tercatat di DESIGN.md. |
| C-2 | PASS untuk alur inti. Kontrol menggunakan aksi existing dan verifikasi instrumentation. |
| C-4 | PARSIAL. Matriks emulator dan state yang diuji lulus. API 29/30 dan perangkat fisik masih perlu run. |
| C-5 | PASS. Metrik dan status memakai data yang ada dengan sumber atau konteksnya. |
| R-05 | PASS. Susunan mengikuti pekerjaan layar tanpa template landing page. |
| R-11 | PASS. Radius 12/20/26 dp membedakan kontrol, panel dan navigasi. |
| R-15 | PASS. CTA menyebut tindakan seperti Masuk dan Catat pengukuran. |
| R-16 | PASS. Copy berisi tindakan, state dan petunjuk operasional. |
| R-20 | PASS pada tinjauan visual. Konteks greenhouse dan pasangan ukur tampil sebagai informasi utama. |
| R-21 | PASS. Light/dark mengikuti pengaturan sistem. |
| R-29 | PASS. Hijau, netral, amber dan warna status memiliki peran tertulis. |
| R-30 | PASS. Pola interaksi Material 3 dan motif data greenhouse mendefinisikan layout. |
| R-31 | PASS. Alasan keputusan visual dicatat di DESIGN.md. |

Gate keseluruhan masih parsial. Audit menyeluruh kontras dan aksesibilitas, fallback pada perangkat API 29/30, profiling pada perangkat fisik dan UAT berada di luar hasil run ini. Fixture emulator menguji jalur Compose dan HTTP sintetis. Bukti MySQL dan pemulihan server sebelumnya tetap dicatat terpisah.
