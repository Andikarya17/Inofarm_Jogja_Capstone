# Frontend botani Android Inofarm

7 November 2026. Branch `codex/android-botanical-frontend` berbasis `b38b46b`, commit frontend operasional dari PR GitHub #42. Bukti sebelum perubahan direkam ulang dari build baseline itu. Semua nama, akun, catatan dan nominal pada tangkapan layar adalah data uji sintetis.

## Aset yang dipakai

| Asal lokal | Resource Android | Dimensi dan ukuran | SHA-256 |
|---|---|---|---|
| `assets/background login.jpg` | `drawable-nodpi/background_login.jpg` | 675 × 1200 px, 27.457 byte | `45fd236d1390d799d29b44e6b431b98775545e40fac747e083a120ff18f5c967` |
| `assets/background dashboard.jpg` | `drawable-nodpi/background_dashboard.jpg` | 736 × 1176 px, 31.206 byte | `53a8248de6dfc68d9553460ff1cfe43fcec4490ee9e697f29405039df1ed6f2a` |

Kedua ilustrasi disediakan pengguna. Resource adalah salinan byte-identik dengan penamaan Android. URL asal, kreator dan lisensi belum diketahui. Compose memakai crop proporsional pada viewport ponsel potret, overlay forest pada tema gelap dan permukaan solid pada jendela lebar atau lanskap. Gambar bersifat dekoratif dan tersedia dari paket aplikasi. Folder `UI inspo/` dipakai untuk menilai hierarki dan material panel.

## Implementasi dan penerimaan

| Area | Implementasi | Alur yang diuji |
|---|---|---|
| Masuk | Panel forest, aksi sage, label terpisah pada font besar, area gulir dengan inset IME | Toggle, Next, Done, tombol disabled, konfigurasi endpoint kosong, keluar dan pencabutan sesi yang belum dikonfirmasi |
| Beranda | Latar tetap, konteks solid, pasangan EC/PPM, sumber terlipat dan informasi sinkronisasi | Konteks berjenjang, dua kartu dari satu pengukuran, antrean lokal, penerimaan dan fallback hari WIB, nama greenhouse panjang |
| Navigasi | Tab satu baris atau sheet dengan pilihan sesuai peran, status sesi ringkas di toolbar | Empat tujuan pekerja, lima tujuan pengelola, Back, pilihan aktif dan akses saat sesi kedaluwarsa |
| Form dan review | Kelompok isian solid, label dan satuan, ringkasan nilai, aksi draf dan antrean terpisah | Raw input, rotasi, tinjau, Back, antrekan, pemulihan draf, nominal panjang dan keyboard lanskap |
| Antrean, riwayat dan panen | Baris padat, filter aktif, empty sesuai keadaan, konflik lokal/server, panen per grade | Filter/detail, histori kosong, queued, accepted, rejected, konflik, koreksi dan akses menurut peran |
| Keuangan dan layar sekunder | Satu daftar transaksi, alat dalam sheet, hasil dan basis laporan sebelum ekspor, komponen solid untuk aset, lampiran, administrasi dan pemulihan | Pembayaran dan alokasi, penjualan, aset/penyusutan, laporan dan investasi, lampiran privat, konfirmasi admin, ekspor dan tanda terima pemulihan melalui fixture |

Pemilihan aset, kontras forest dan aksen sage mengikuti brief yang disetujui. Dials login 2/2/1, beranda 2/2/2, layar kerja 1/2/1 untuk ENERGY/RHYTHM/MOTION. Kaca alpha dipakai selektif pada navigasi beranda dan dipisahkan dari panel data. Sistem visual aktual dijelaskan di [DESIGN.md](../../DESIGN.md).

## Bukti lokal

`outputs/frontend-botanical/` menyimpan tangkapan layar PNG Android, log dan hash APK. Baseline menggunakan emulator `Inofarm_Main_Review_20261103`, API 37, 1080 × 2400 px pada 420 dpi, sekitar 412 dp, tema terang dan skala teks 1,0. Build baseline dan empat tes pembanding lulus. SHA-256 APK baseline `b589861247b5527ac01bf6aca176eeb8b68d96b44fbc9b8076342aed2fdd55cc`.

Putaran pertama menemukan subjudul login bertemu area daun ketika IME menggeser form, pesan antrean lama yang dapat terbaca sebagai status terkini, dan pecahan rupiah yang terpisah di layar sempit. Pengantar login mendapat bidang solid pada layout pendek atau font besar, pesan antrean mencatat tindakan yang sudah terjadi, dan nilai rupiah memperoleh peluang pindah baris di pemisah ribuan. Review Impeccable juga menemukan pesan sesi kedaluwarsa tetap mengambil ruang dan berulang di Antrean. Penjelasan lengkap dipindahkan ke area gulir. Tangkapan IME yang tertutup onboarding Gboard diperlakukan sebagai percobaan pengambilan bukti.

Sampel kontras dari render dan token dihitung dengan luminansi sRGB. Tombol Masuk sage 11,13:1, teks field login 10,89:1, pengantar login pada crop terang 412 dp 14,14:1, metadata pada surface terang 6,04:1 dan gelap 7,49:1. Lihat `contrast.json` untuk pasangan tambahan dan batas komposit.

| Konfigurasi emulator | Bukti dan hasil |
|---|---|
| API 37 terang dan gelap, 412 dp, 1,0 | `after-light-412.txt` 4 tes lulus, `after-dark-412.txt` 3 tes lulus |
| API 37 terang dan gelap, 360 dp, 1,3 | 945 × 2100 px pada 420 dpi. `after-light-360-font130.txt` 2 tes dan `after-dark-360-font130.txt` 3 tes lulus |
| API 37 terang dan gelap, 320 dp, 2,0 | 840 × 1867 px pada 420 dpi. `after-light-320-font200.txt` 2 tes lulus. `acceptance-dark-320.txt` 6 tes lulus termasuk lanskap, sesi berakhir dan pemenggalan nominal. Versi sebelum penyesuaian matcher mencatat satu kegagalan karena dua pesan memuat awalan sesi yang sama |
| API 37 tablet potret terang, 686 dp, 1,0 | 1800 × 2400 px pada 420 dpi. `after-tablet-686.txt` 2 tes lulus. Latar solid dan tab horizontal dipakai pada kelas lebar medium |
| API 29 dan 30, potret terang | AVD Pixel 5, 1080 × 2340 px pada 440 dpi. `acceptance-api29.txt` dan `acceptance-api30.txt` masing-masing 2 tes lulus |
| API 30 gelap, 320 dp, 2,0 | 880 × 1956 px pada 440 dpi. `final-api30-dark-320.txt` 2 tes lulus pada APK final. `final-api30-dark-320/login-ime.png` memperlihatkan password fokus, tombol Masuk dan IME dalam satu tangkapan tanpa tip Gboard |

Build final menjalankan `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` dan `lintDebug`. Hasil 73 tes JVM lulus, 0 error lint, 16 peringatan dan satu hint. Hash APK final `9510ea79edfb6f30209edb1af92bbdc581fd63f90185508ee6337ba7f0393157` dicatat di `final-apk-hashes.txt`. Matriks sebelumnya memakai build bertahap. Perubahan terakhir hanya mempersingkat teks sinkronisasi ketika hasil tarik server berhasil tanpa catatan baru diterima, serta menyesuaikan matcher pengujian. Operasi tertahan tetap memiliki status dan alasannya sendiri.

Regresi gabungan API 37 mencatat 118 dari 120 tes lulus di `acceptance-regression.txt`. Satu kasus offline mengalami timeout Espresso pada pemilih konteks dan lulus saat diulang pada APK final. Kasus lain memakai fixture SQLite versi 6 yang masih membawa kolom dan indeks `scenario_id` dari versi 9. Rekonstruksi fixture disesuaikan dan delapan tes lampiran lulus pada APK tes yang dibangun ulang. Perbaikan fixture berlaku pada database sintetis milik tes. Log `final-targeted-api37.txt` juga mencatat hasil 12 dari 13 sebelum APK tes fixture diganti, dengan satu kegagalan fixture yang sama. Kedua fase penyimpanan draf dan hash outbox lintas proses lulus di `process-prepare.txt` dan `process-verify.txt`.

Perbandingan `perbandingan.png`, rangkuman `uji-ime.png` dan hasil utama `hasil-utama.png` menyusun tangkapan layar emulator dengan skala proporsional. Gambar sumber dan PNG tangkapan tetap tersimpan terpisah. Render laporan dan investasi berasal dari harness Compose yang membatasi lebar dan skala teks. Inset shell dinilai terpisah pada render MainActivity. Tablet potret 686 dp menggunakan tab horizontal. Lebar expanded mulai 840 dp dan perangkat lipat berada di luar bukti run ini. [Kelas ukuran jendela](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes) · [Pola navigasi Android](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns)

Pada viewport debug API 37 yang dipendekkan ke 1080 × 1700 px, tes mengirim enam swipe pada baseline dan APK final dengan skala teks 1,0. Baseline merekam 28 frame, P50 150 ms, P95 300 ms dan 26 frame janky. APK final merekam 24 frame, P50 150 ms, P95 200 ms dan 23 frame janky. Sampel `perf-baseline-isolated/` dan `perf-final-isolated/` kecil dan memperlihatkan jank tinggi pada emulator. Pengukuran pertama pada tinggi 2400 px merekam nol frame untuk APK sesudah dan dikeluarkan dari perbandingan.

Satu cold launch APK debug final dengan sesi fixture tersimpan mencatat `TotalTime` 2914 ms dari `am start -W`. Sampel proses sesudah peluncuran mencatat PSS 86.977 KiB dan RSS 209.600 KiB dari `dumpsys meminfo`. Nilai tersebut menggambarkan satu keadaan emulator. Uji Laravel/MySQL, perangkat fisik, pentest berizin dan UAT mitra berada di luar bukti frontend ini.

Uji keyboard emulator memeriksa fokus terlihat dan urutan Tab dari Sinkronkan ke Menu akun, Beranda lalu Antrean. Enter membuka Antrean dan Back kembali ke Beranda. Ikon toolbar memiliki bounds semantik 126 × 126 px pada 420 dpi atau 48 × 48 dp. TalkBack diaktifkan sementara dan layanan touch exploration tercatat aktif. Audit pengumuman suara dan seluruh urutan fokus TalkBack belum mencakup semua layar. Pengaturan ukuran, kepadatan, font, tema, stylus dan layanan aksesibilitas diverifikasi kembali di `manual-accessibility/settings-restored.txt`.

## Anti Slop Delivery Gate

| Kriteria | Hasil | Bukti |
|---|---|---|
| Arah, keaslian materi dan klaim, R-17/18/23/36–38 | PASS | Brief disetujui, file APK cocok dengan dua JPG pengguna, fixture terpisah dari runtime, data kosong tidak diberi angka |
| Copy dan perubahan source, R-02/33 | PASS pada perubahan | Label memakai istilah operasional Indonesia dan perubahan ditulis langsung di source Compose |
| Hierarki, warna, tipografi, material dan motion, R-01/04–16/19–22/29–31 | PASS pada layar yang ditinjau | Focal point Masuk dan EC/PPM, sage untuk aksi dan pilihan, sans sistem mengikuti skala, pemisah untuk form dan riwayat, alpha terbatas pada navigasi beranda |
| Reflow, tema, kontras dan aksesibilitas, R-03/25/32/34/39 | PARTIAL | Matriks terang/gelap, font 1,0/1,3/2,0, IME, lanskap, kontras sampel dan semantik Compose. Audit TalkBack dan seluruh kombinasi perangkat masih terbuka |
| Interaksi dan keadaan runtime, R-24/26/27/35 | PARTIAL | Regresi berfixture pada alur autentikasi, scope, draf, peran, sinkronisasi, laporan dan layar sekunder. Click-through manual setiap kontrol dan UAT mitra belum menyeluruh |
| Media dan performa, R-40 | PARTIAL | Resource lokal dengan dimensi diketahui, crop/fallback tercatat. Sampel debug emulator tidak menjadi benchmark perangkat fisik |
| Aturan DOM, CSS dan detector HTML | N/A | Aplikasi Android native Kotlin dan Compose |
