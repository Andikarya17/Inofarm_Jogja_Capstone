# Master prompt: frontend Inofarm dari UI inspo

Arsip arahan yang disusun 5 November 2026 dan diperbarui 7 November 2026 setelah pengguna menyediakan dua ilustrasi. Arah visual berasal dari [audit source dan screenshot](FRONTEND_INSPIRATION_AUDIT_20261105.md) dan [penilaian aset](FRONTEND_ASSET_REVIEW_20261107.md). Lihat [hasil implementasi dan pengujian](ANDROID_BOTANICAL_FRONTEND_20261107.md) untuk status Android.

Salin bagian berikut ke chat implementasi pada repository Inofarm.

---

Gunakan skill Anti Slop dalam mode DURING dan Impeccable untuk merombak presentation frontend aplikasi Inofarm Kotlin + Jetpack Compose. Kerjakan implementasi native, bukan prototipe React/web. Gunakan background yang saya sediakan di `assets/` sebagai acuan visual utama login/dashboard. Referensi di `UI inspo/` tetap membantu hierarki, kedalaman panel dan kaca selektif, tetapi tidak mewajibkan foto atau suasana gelap penuh. Baca seluruh prompt sebelum mengubah source.

## 1. Keputusan desain yang sudah disetujui

- Ambil warna forest untuk kontras, sage/krem dari aset lokal, kedalaman lapisan dan kaca selektif dari referensi. Form, uang, hasil ukur dan keputusan penting harus tetap mempunyai permukaan stabil.
- Pakai `assets/background login.jpg` untuk login dan `assets/background dashboard.jpg` untuk beranda kedua peran. Keduanya ilustrasi botani dekoratif, bukan foto melon atau bukti kondisi greenhouse. Jangan mencari pengganti internet, menghasilkan aset AI, atau menambahkan foto hero tanpa permintaan baru.
- `docs/context/FRONTEND_PHOTO_SOURCES_20261105.md` adalah riset historis, bukan dependency implementasi ini. Pertahankan file asli pengguna; turunan Android dapat dibuat dengan nama resource yang sah. Catat asal sebagai aset disediakan pengguna; jangan mengarang fotografer, lisensi atau kepastian non-AI dari tampilannya saja.
- Login tetap Email, Password dan Masuk. Jangan menyalin Sign Up, Google, Apple, wishlist, keranjang atau forgot password yang tidak punya alur nyata.
- Pertahankan dua peran, tujuan navigasi, state dan makna bisnis existing. Jangan menambahkan statistik, chart, target panen, status tanaman sehat atau data simulasi ke runtime untuk mengisi layar.

## 2. Inspeksi dan batas pekerjaan

1. Baca AGENTS root/lokal, context README, PRD/ARCHITECTURE/DATA_CONTRACTS/IMPLEMENTATION_STATUS, keputusan migrasi dan kontrak v2 relevan. Periksa branch, status, diff dan staging. Buat branch deskriptif `codex/` sesuai SOP, berbasis checkout yang benar dan dengan dependency tercatat. Jangan mengikutkan folder desain historis atau foto inspirasi pengguna ke publikasi tanpa provenance.
2. Baca skill Anti Slop, UI/human/layoutmobile/copywriting yang relevan dan Impeccable. Mode Anti Slop DURING sudah eksplisit dalam prompt ini. Untuk Impeccable gunakan Operate serta panduan Android, audit.native, operate, layout, typeset, adapt.native dan playbook yang dibutuhkan. Jalankan context launcher sekali; jika gagal, gunakan context repo secara langsung dan nyatakan batasnya. Jangan memakai detector HTML sebagai bukti Compose.
3. Buka kedua JPG di `assets/` dan kedua JPG inspirasi secara visual, bukan hanya membaca namanya. Prioritaskan aset terbaru untuk background. Buka audit baru dan screenshot lokal existing, kemudian capture baseline dari build aktif ketika tooling tersedia. Catat commit, APK/perangkat, tema, font scale dan state. Bukti lama bukan hasil run baru.
4. Inspeksi `MainActivity.kt`, `feature/FinanceRoot.kt`, `core/design/InofarmTheme.kt`, `FarmChrome.kt`, `feature/auth/FinanceLogin.kt`, `feature/context/HomeContextCard.kt`, `feature/activities/MeasurementDashboard.kt`, `feature/history/HistoryScreen.kt`, `feature/expenses/FinancialEntryScreen.kt` serta screen aktif lain. Root berada di `android/app/src/main/java/com/inofarm/jogja/`.
5. Inventarisasi semua layar/state/tujuan sebelum refactor. Pisahkan komponen presentation bila perlu, tanpa mengganti ownership ViewModel, lifecycle collection, persistence, kontrak API atau rumus bisnis untuk mengejar visual.
6. Baca craft-floor Impeccable tepat sebelum edit UI. Catat brief target terpisah dari `DESIGN.md` existing; update DESIGN.md dari hasil yang benar-benar dibangun setelah review, bukan menulis rancangan seolah sudah tersedia.

## 3. Design Read dan identitas

Design Read: aplikasi kerja Android untuk pekerja dan pengelola greenhouse melon, dengan bingkai ilustrasi botani sage/krem, kontras forest dan kaca terbatas. Login ENERGY 2 / RHYTHM 2 / MOTION 1; beranda ENERGY 2 / RHYTHM 2 / MOTION 2; form, riwayat dan laporan ENERGY 1 / RHYTHM 2 / MOTION 1. Identitas login berasal dari bingkai daun dan panel yang jelas, bukan animasi dekoratif.

Motif visual: daun di sudut dan bidang organik pada dua aset lokal membingkai area kerja yang tenang. Motif ini botani umum, tidak spesifik melon; identitas Inofarm harus diperkuat oleh nama produk, konteks greenhouse dan pasangan EC/PPM. Kedua aset berbeda gaya daun dan warna dasar, sehingga token panel, tombol dan tipografi harus menyatukannya. Jangan menambah blob, pola kontur atau daun baru pada icon/divider/kartu; ornamen sudah tersedia di background.

Palet awal, sebagai titik mulai yang harus diverifikasi pada hasil render:

| Peran | Nilai awal | Alasan |
|---|---|---|
| Forest utama | `#142B23` | Kedalaman login/hero dan teks penting pada surface terang |
| Forest surface gelap | `#203B30` | Memisahkan konten dark dari latar tanpa semua kartu menjadi kaca |
| Netral hangat | `#F4F3E8` | Bidang kerja terang dan teks utama di forest |
| Sage aksi pada forest | `#D4E4B7` | Tombol utama terlihat tanpa neon |
| Muted terang | `#52665B` | Metadata sekunder pada netral hangat |
| Muted gelap | `#C4CEC3` | Metadata pada forest surface |

Empat pasangan solid sudah dihitung dalam sesi penyusunan: netral/forest 13,46:1; forest/sage 11,13:1; muted terang/netral 5,52:1; muted gelap/forest surface 7,49:1. Itu hanya rasio sRGB pasangan tersebut, bukan sertifikasi kontras seluruh UI. Jangan memberi teks putih pada sage hanya karena tombol biasanya putih. Warna warning/error/info harus tetap semantik dan diuji terpisah.

Light theme tetap light: background ilustrasi lokal dengan area kerja netral hangat dan kontras forest yang kuat. Jangan meratakan seluruh tombol/panel ke pastel sehingga fokus hilang. Dark theme memakai forest bertingkat; background yang aslinya terang diberi overlay forest terukur atau memakai fallback solid jika hasilnya keruh. Jangan membalik warna JPG atau menganggap aset sudah punya versi dark. Tema mengikuti kebijakan aplikasi existing.

Tipografi: satu sans untuk field, label, angka, daftar dan tombol. Tuning role lebih penting daripada mengganti font. Brand boleh memakai satu aksen serif jika lisensi dan glyph sesuai, terbatas pada nama/judul login; tidak wajib menambah font. Angka metrik 30–34 sp, heading layar kerja sekitar 24–28 sp, isi 14–16 sp, metadata 12–14 sp sebagai titik awal. Semua mengikuti fontScale. Nominal panjang dan nama GH panjang harus reflow, bukan diperkecil sampai tidak terbaca.

Spacing membentuk kelompok: 4/8 dp untuk hubungan label-nilai, 12/16 dp dalam kelompok, 24 dp antarbagian. Pertahankan touch target minimum 48 dp. Radius kontrol 12 dp, kartu kerja 16–20 dp, panel login 24–28 dp sebagai keluarga token; bukan semua komponen kapsul. Alasan setiap pengecualian dicatat.

## 4. Komposisi layar, bukan sekadar tema

### Login

- Gunakan `assets/background login.jpg` (675 × 1200 px, portrait) sebagai latar dekoratif. Daun kanan atas dan kiri bawah menjadi bingkai; area krem tengah menampung konten. Tidak ada kewajiban menampilkan buah/foto greenhouse pada layar ini.
- Nama Inofarm berada di area pengantar, dengan kalimat singkat faktual seperti `Catatan operasional greenhouse melon`. Tidak menambah klaim produktivitas atau keamanan.
- Panel form forest ditempatkan di area tengah kosong, sedikit ke bawah bila ruang memungkinkan, lebar layar dikurangi margin 20–24 dp, maksimum sekitar 420 dp. Sisakan sebagian bingkai daun di luar panel. Hindari ruang kosong berlebihan di atas form dan hindari panel menutupi seluruh ilustrasi.
- Urutan panel: judul Masuk, Email, Password dengan toggle, pesan state/error yang relevan, tombol Masuk full width. Field solid/nyaris opak dan label terlihat saat terisi. Warna tombol sage dengan teks forest ketika enabled; disabled terbedakan.
- Panel boleh mempunyai transmisi ringan, satu highlight tepi dan bayangan tipis; panel solid forest juga sah bila kaca tidak memberi manfaat pada bidang krem rata. Teks di luar panel memakai forest dengan kontras teruji terhadap background. Jangan menggelapkan seluruh background light sampai ilustrasi hilang atau memburamkan field/teks.
- Pada tinggi pendek, landscape, font besar atau IME terbuka: panel menjadi fokus layout yang bisa digulir; ornamen boleh terpotong sebelum keterbacaan dikurangi. Tombol dan field aktif tetap dapat dicapai. Jangan memakai tinggi panel tetap atau mengubah ukuran background mengikuti tinggi seluruh form yang bergulir.
- Pertahankan konfigurasi endpoint, izin jaringan, loading, error, logout yang belum terkonfirmasi, pencegahan submit berulang dan semantics/test tags existing. Jangan menyimpan password ke durable draft baru.

### Beranda pekerja dan pengelola

Komposisi target ketika scope terpilih pada compact/font normal:

1. Toolbar ringkas dengan Inofarm, sinkronisasi dan akun.
2. Blok konteks ringkas sekitar 144–180 dp sebagai sasaran awal, bukan tinggi wajib: area teks solid untuk GH, musim, kelompok dan umur; aksi ubah konteks. Tidak perlu foto di dalam kartu. `assets/background dashboard.jpg` (736 × 1176 px) menjadi latar viewport di belakang konten, bukan hero tambahan.
3. Tepat dua kartu EC mS/cm dan PPM ppm, setara secara struktur, label kecil, angka dominan, unit jelas. Tidak ada foto di belakang angka. Sumber tetap satu catatan valid scoped.
4. Ringkasan freshness dan sync yang ringkas; detail sumber bisa dibuka tanpa kehilangan alasan. Offline, queued atau konflik tetap mudah terlihat.
5. Aksi utama Catat pengukuran, lalu Catat kegiatan dan Catat panen sebagai sekunder, mengikuti alur existing.
6. Jadwal/kegiatan yang benar-benar tersedia, kemudian informasi lain yang sudah ada.

Pada sekitar 412 dp/font 1,0, sasarkan konteks, dua metrik dan aksi utama terbaca tanpa hero besar mendominasi layar. Background dashboard tetap mengikuti viewport, tidak diulang dan tidak dipanjangkan mengikuti LazyColumn. Karena ornamen tepinya lebih ramai daripada login, semua angka, status, toolbar dan teks penting harus berada di area solid atau area tenang yang kontrasnya diuji. Jangan membuat semua kartu transparan agar wallpaper terlihat. Verifikasi dengan tinggi perangkat yang dicatat; jangan mengunci seluruh konten ke satu viewport atau menghilangkan informasi demi target ini. Pada font besar, susun ulang dan izinkan scroll.

Ketika scope belum lengkap: tampilkan langkah berikutnya dan aksi pilih konteks di atas background yang sama. Jangan menampilkan angka kosong sebagai nol atau menyediakan metrik scoped yang belum bisa diketahui. Pemilihan parent mengosongkan child sesuai perilaku existing. HSS/HST dan tanggal tetap kalender WIB.

### Shell navigasi

- Pertahankan tujuan pekerja Beranda, Antrean, Kegiatan, Panen; pengelola menambah Keuangan. Jangan mengubah role gating menjadi hanya menyembunyikan tombol.
- Navbar boleh memakai material kaca ringan, ikon opak, label terbaca dan selected state nyata. Gunakan pola/semantics Android; referensi iPhone bukan instruksi menyalin kontrol Cupertino.
- Pastikan konten akhir dapat digulir sampai bebas dari navbar. Jangan meninggalkan teks penting permanen di balik tint.
- Audit masalah dua baris pada 320 dp/font 200%. Pilih pola adaptif yang menjaga seluruh tujuan tetap terjangkau dan tidak menghabiskan bagian besar viewport. Bila perlu overflow/drawer, pastikan label dan posisi dapat dipahami, serta uji semua tujuan sebelum menetapkannya. Jangan mengatasi masalah dengan mematikan fontScale.

### Form dan review

- Background dekoratif dibatasi pada login/beranda. Form memakai permukaan solid, judul ringkas, konteks secukupnya dan kelompok isian sesuai pekerjaan.
- Jangan membungkus setiap label atau field dalam kartu baru. Gunakan section heading, jarak dan divider bila cukup. Card dipakai untuk kelompok yang benar-benar terpisah atau ringkasan review.
- Satuan, helper, error dan jenis keyboard dekat field. Raw input, ketelitian nominal, desimal dan detik yang diizinkan kontrak tetap dipertahankan.
- Bedakan Simpan draf, Tinjau dan Antrekan. State lokal tidak diberi label sudah diterima server. CTA tetap dapat dicapai ketika IME terbuka.
- Review memakai struktur label/nilai yang mudah diperiksa; seluruh alasan invalid dan sumber tetap ada. Tidak mengubah wizard/Back behavior tanpa regresi meaningful.

### Riwayat, antrean dan panen

- Utamakan judul kegiatan/jenis transaksi, nilai jika relevan, waktu dan konteks. Gunakan daftar berirama rapat dengan pemisah yang cukup; kartu besar hanya untuk penekanan yang diperlukan.
- Kelompokkan metadata; status normal tenang, tindakan yang diperlukan menonjol. Detail konflik membedakan lokal/server dan seluruh alasan secara eksplisit.
- Filter jelas dan state filter aktif terlihat. Empty karena filter berbeda dari belum punya data; beri tindakan yang benar-benar tersedia.
- Panen tetap per grade, penjualan tetap alur terpisah. Perubahan visual tidak mengubah hubungan panen, saldo dan penjualan.

### Keuangan, laporan dan layar sekunder

- Ruang keuangan mempunyai header/konteks ringkas, aksi Catat pengeluaran, akses alat dan daftar transaksi dalam satu alur gulir. Kurangi bobot menu berpanel sebelum data.
- Jangan membuat kartu omzet/laba atau chart baru tanpa sumber/periode/basis yang sah di kontrak existing. Visual menarik tidak memerlukan angka tambahan.
- Bedakan pembayaran, alokasi biaya, pembelian aset, penyusutan, panen, penjualan, evaluasi dan proyeksi. Label dan format rupiah harus membantu perbandingan tanpa mengubah fixed decimal.
- Laporan dan investasi mendahulukan hasil yang tersedia, status, periode dan basis; asumsi/rumus/sumber dapat diperluas. Semua alasan invalid/missing tetap dapat diakses; laporan lama tetap immutable.
- Terapkan sistem yang sama pada aset, lampiran, administrasi, ekspor, akun, recovery dan dialog, bukan hanya layar showcase. Permission, validasi dan konfirmasi existing dipertahankan.

## 5. Aset dan material

- Dua aset lokal adalah pilihan utama yang diberikan pengguna. Pertahankan file asli byte-identik. Salin/konversi non-generatif ke resource Android, misalnya `background_login` dan `background_dashboard`, jika diperlukan. Jangan memakai screenshot UI inspo, foto lain atau generative fill sebagai pengganti.
- Catat path, dimensi, hash dan transformasi turunan. Sumber URL/creator/lisensi belum diberikan; tulis belum diketahui, bukan asumsi aset milik Inofarm atau bebas lisensi. Tidak perlu mencari foto lagi untuk menjalankan layout yang sudah diminta.
- Pertahankan rasio aspek. Mulai dari crop berpusat pada portrait dan tinjau clipping sudut pada ukuran nyata. Jangan stretch/FillBounds atau tile. Jika crop menghilangkan terlalu banyak ornamen pada landscape/tablet, tampilkan aset proporsional pada bidang berwarna senada atau gunakan fallback solid; kontrol tetap prioritas.
- Resolusi 675 × 1200 dan 736 × 1176 px cukup sebagai bahan uji layout ponsel, tetapi belum membuktikan ketajaman pada layar densitas tinggi/tablet. Periksa pada ukuran tampil sebenarnya; jangan mengklaim HD atau memperbesar dengan AI. File JPEG kecil bukan bukti pemakaian RAM atau render sudah optimal.
- Background tersedia offline dan di-decode sesuai kebutuhan. Jadikan dekorasi tanpa pengumuman TalkBack. Ukur dampaknya pada render, jangan menambah blur real-time tanpa manfaat yang terlihat.
- Kaca login dan navbar tidak perlu ada pada viewport yang sama. Hindari kaca pada input, metrik, transaksi, dialog keputusan dan kartu error.
- Utamakan background lokal + bidang konten stabil + tint/highlight selektif. `Modifier.blur` memproses isi composable; jangan mengklaim backdrop blur dari blur pada container teks. API 29/30 harus punya fallback solid yang tetap berkarakter. Jangan memasukkan API dokumentasi terbaru yang tidak ada di dependency proyek.
- Tidak ada kewajiban GSAP, scroll pinning, parallax, AIDA, bento atau randomisasi layout. Ini aplikasi operasional Compose. Animasi 150–250 ms untuk state/transisi yang membantu, mengikuti pengaturan animasi sistem. Background tidak bergerak terus-menerus.

## 6. Batas data dan engineering

Scope perubahan: presentation Compose, resources, token, representasi UI state dan pengujian yang relevan. Tetap Kotlin/Compose, Laravel REST, MySQL. Tidak ada perubahan endpoint, schema, rumus, auth/role, sync/outbox atau data produksi untuk memenuhi visual.

Pertahankan durable draft per akun, atomic enqueue, raw input, restoration, logout, konflik, tombstone, restore epoch, attachment access dan ownership ViewModel. Jangan menambah fitur IoT/sensor/dosing. Jangan menjalankan clear app data, destructive reset, migrate:fresh atau test pada database pengguna. Jika perubahan backend benar-benar diperlukan, jelaskan dependency terpisah; jangan perluas scope diam-diam.

Pertahankan tag/semantics pengujian atau migrasikan test secara bermakna. Jangan menulis test kosmetik yang hanya memeriksa konstanta warna. Pertahankan KF01–14, KNF01–08 dan U01–21; handover/UAT/pentest tetap gate tersendiri.

## 7. Tahapan dan bukti penerimaan

1. Rekam baseline dan inventaris. Gunakan temuan audit sebagai daftar masalah awal, bukan daftar bug yang sudah direproduksi seluruhnya.
2. Buat komposisi login dan beranda utuh menggunakan dua background lokal, tema dan fallback. Review render terhadap aset terbaru dan hierarki referensi UI inspo. Koreksi komposisi sebelum menerapkan ke semua layar.
3. Sebarkan bahasa visual ke form/review, riwayat/panen, keuangan, laporan/investasi dan layar sekunder. Lanjutkan seluruh scope yang dapat dikerjakan; jangan berhenti setelah login saja.
4. Jalankan build/lint/test sesuai workflow repo, lalu regresi alur yang struktur/navigasinya berubah. Gunakan emulator/perangkat Android untuk evidence, bukan screenshot browser atau gambar generatif.
5. Tinjau light/dark pada lebar 320, 360, sekitar 412 dp; font scale 1,0/1,3/2,0; portrait/landscape; IME; teks GH panjang; nominal panjang. Tambahkan tablet jika target didukung. Pulihkan setting perangkat sesudah tes.
6. Uji loading, empty, error/retry, offline, last-known, saving, queued, accepted, rejected, conflict, expired session dan disabled. Uji login toggle/Next/Done, scope sheet/parent-child, navigasi semua tujuan, draf→review→kembali→antre, filter→detail serta akses alat keuangan sesuai peran. Catat tiap aksi dan hasil nyata; bedakan test otomatis dari click-through manual.
7. Periksa TalkBack, fokus, minimum touch target, Back dan insets. Ukur kontras teks terhadap bidang krem, sage, ornamen gelap dan overlay pada tiap state/theme; rasio pasangan solid saja belum cukup. Uji motion off dan fallback material di API 29/30.
8. Ukur scroll/startup/memori pada konfigurasi yang dicatat bila tooling memungkinkan. Jank debug emulator tidak boleh disebut performa perangkat fisik. Optimalkan gambar/material bila ada regresi terukur.

### Acceptance visual yang wajib terlihat pada screenshot

| Layar | Hasil yang harus tampak | Hasil yang belum memenuhi brief |
|---|---|---|
| Login | Background login lokal terlihat proporsional; panel forest; field jelas; tombol sage | Ilustrasi diregangkan, hilang di balik overlay gelap, atau diganti foto tanpa permintaan |
| Beranda | Background dashboard lokal membingkai konten; konteks ringkas; angka EC/PPM dan aksi dominan | Wallpaper bertile/diregangkan mengikuti daftar atau ornamen terlihat melalui angka |
| Form | Section terbaca, field stabil, kesalahan dekat isian, CTA dapat dijangkau | Foto/kaca di belakang input atau semua hal dibungkus kartu |
| Riwayat | Jenis, nilai/waktu dan status mudah dipindai | Pesan metadata mendominasi setiap baris |
| Keuangan | Hierarki transaksi/menu jelas, uang dan basis terbaca | Dashboard metrik rekaan untuk menyerupai aplikasi lain |
| Font besar | Konten dan tujuan tetap terbaca/terjangkau; dekorasi mengalah | Label dikecilkan paksa, terpotong atau navigasi mendominasi viewport |

Definition of done: ada perubahan komposisi dan identitas yang terlihat, kedua background lokal dipakai sesuai perannya, semua fungsi existing tetap dapat digunakan pada cakupan tes, provenance aset dicatat tanpa mengarang bagian yang belum diketahui, dan evidence before/after berasal dari Android. Jalankan Anti Slop Delivery Gate dengan PASS/PARTIAL/N/A dan bukti per kriteria; jangan mengklaim semua PASS jika belum diperiksa. Tidak ada klaim UAT/siap produksi hanya dari polish UI.

Update context/evidence dan DESIGN.md sesuai hasil aktual. Laporkan file/alasan, build/test, screenshot, sumber aset, keterbatasan yang tersisa dan status Git. Ikuti otorisasi commit/push repository; jangan deploy atau merge main otomatis.

Mulai dari inspeksi dan baseline, lalu kerjakan implementasi. Kaca selektif dan dua background lokal sudah dipilih; permintaan terbaru mengatasi rencana pencarian foto sebelumnya. Jangan menghasilkan aset AI. Tanyakan hanya konflik kebutuhan yang tidak bisa diselesaikan dari context atau keputusan yang benar-benar belum terotorisasi.
