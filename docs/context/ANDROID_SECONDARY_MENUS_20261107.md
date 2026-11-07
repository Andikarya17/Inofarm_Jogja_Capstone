# Menu lanjutan Android

Baseline pembanding `dc97705`. Mode Operate, dengan ENERGY / RHYTHM / MOTION 2 / 2 / 1 pada menu dan 1 / 2 / 1 pada layar kerja. Header forest mengikuti kartu konteks beranda. Ilustrasi membingkai viewport, panel solid memisahkan isi operasional, dan sage menandai tindakan. Focal point setiap menu adalah pengajuan perlu ditinjau, catatan kegiatan, hasil panen per grade, dan transaksi keuangan.

## Matriks penerimaan

| Route / peran / pintu masuk | Penerimaan | Hasil dan bukti |
| --- | --- | --- |
| Antrean / kedua peran / tab 1 | Loading dan gagal baca terpisah dari kosong. Status lokal, server, sesi, penugasan dan epoch dapat dibedakan. ID serta alasan dapat dibaca. Back kembali ke Antrean | **Diubah.** `QueueScreen` dan rute detail di `FinanceRoot`. PNG antrean kosong, menunggu dan konflik. Tes offline/reconnect, ID lengkap, rute konflik dan Back. Scope/epoch divalidasi pada tes repository, tanpa capture seluruh state |
| Kegiatan / kedua peran / tab 2, Beranda, Antrean | Cakupan filter sesuai query. Draf, EC/PPM/pH, multi-bahan, alasan dilewati dan target kosong tetap dapat ditinjau. Koreksi, audit, konflik dan batal mengikuti peran | **Diubah** pada riwayat, filter, form, review dan status detail. Perbandingan lokal/server, audit dan konfirmasi batal **dipertahankan**. PNG daftar, filter, form, review, detail, konflik dan review kegiatan dilewati. Tes filter kosong, multi-bahan, koreksi dan pembatalan pengelola |
| Panen / kedua peran / tab 3, Beranda, Antrean | Buah dan kg per grade, tambah/hapus, saldo hanya untuk versi yang cocok, koreksi/batal mengikuti peran | **Diubah** pada riwayat, form, review, detail. Jalur audit, saldo dan referensi penjualan **dipertahankan**. PNG daftar offline dengan dua grade, form, review, detail dan IME. Tes rotasi, konflik, penolakan saldo, pembatalan dan sumber penjualan |
| Keuangan / pengelola / tab 4 | Scope catatan baru terpisah dari cakupan riwayat. Jenis, nominal, filter dan alat dapat dijangkau | **Diubah** pada `FinancialHub` dan riwayat bersama. PNG hub kosong dan berisi, sheet Alat keuangan benar-benar terbuka, serta matriks ukuran. Tes pekerja tanpa tab Keuangan dan pemilihan jenis riwayat |
| Pembayaran, alokasi, penjualan / pengelola / Keuangan dan Alat keuangan | Desimal, saldo sumber, lunas, dan dasar alokasi tetap sesuai kontrak | Form/review pembayaran dan alokasi **dipertahankan**. **Diubah** pada hierarki review/detail penjualan dan daftar transaksi. PNG tiap keluarga form/review/detail yang tersedia. Tes alokasi, saldo, penjualan lunas, total pembulatan sekali dan koreksi penolakan |
| Aset dan penyusutan / pengelola / Alat keuangan | Harga, nilai sisa, periode, sumber, nominal eksplisit dan versi terbaca | Form **dipertahankan**. Review dan detail **diubah** dengan nominal, sumber, periode, dan referensi sebagai pasangan label-nilai. PNG aset serta penyusutan. Tes tidak mengisi nominal otomatis, draf rekreasi, konflik dan penerimaan |
| Evaluasi, investasi, ekspor / pengelola / Alat keuangan atau Antrean | Basis, periode, status, aktual/proyeksi, alasan indikator dan snapshot yang diekspor tetap dapat ditelusuri | Panel hasil, rumus, sumber, form dan ekspor **dipertahankan**. **Diubah** pada tujuan Back dari Antrean. PNG rumus dan indikator dari harness Android dengan font 200%. Tes snapshot, pagination sumber dan ekspor melalui pemilih dokumen fixture |
| Lampiran / sesuai akses induk / detail Kegiatan, Panen, Pengeluaran | Status dan induk jelas. Pemeriksaan versi, izin, konfigurasi dan preview privat tetap berlaku | **Diubah** pada daftar, detail dan review dengan judul sesuai state dan panel induk. PNG detail dan review. Tes batas belum tersedia, berkas terenkripsi, pembukaan privat, logout dan akses pada 320 dp/lanskap |
| Pemulihan / cadangan khusus pengelola berkapabilitas, tanda terima sesuai scope / Alat keuangan dan Antrean | Tanda terima historis dibedakan dari penerimaan kini. Review memakai guard akun, epoch, scope dan hash | **Diubah** pada presentasi pemulihan di Antrean. Dialog cadangan, konfirmasi dan pemeriksaan tanda terima **dipertahankan**. PNG dialog cadangan dari harness bertema Inofarm. Tes receipt lama, epoch berubah, hash berbeda, pencabutan sesi dan konfirmasi backup fixture |
| Login dan Beranda / kedua peran | Login dan dua dashboard tetap mengikuti referensi. Pasangan EC/PPM, sumber dan waktu WIB dipertahankan | **Dipertahankan.** PNG before/after login, Beranda pekerja dan pengelola. Tes IME, pengukuran berpasangan, penerimaan server, fallback WIB dan menu per peran |

## Bukti Android

Tangkapan, log dan hash tersimpan lokal di `outputs/frontend-secondary-menus/`. Nama, nominal, catatan, backup dan sumber laporan pada bukti berasal dari fixture. Build baseline `dc97705` memakai APK SHA-256 `9510ea79edfb6f30209edb1af92bbdc581fd63f90185508ee6337ba7f0393157`. APK akhir memakai SHA-256 `aaf2522089ff18ff4a8f07f1a81e41eb672fe38689c82a256695cc13974e9fc1`.

| Konfigurasi | Hasil |
| --- | --- |
| API 37, 1080 × 2400 pada 420 dpi, sekitar 412 dp, terang, font 1,0 | 20 tes APK akhir lulus di `after/device-light-412-final.txt`. Baseline dan APK akhir menyediakan PNG per alur. Regresi luas sebelum penyesuaian review terakhir lulus 50 tes Android di `after/device-light-412.txt` |
| API 30, 320 dp, font 2,0, terang dan gelap | Tes empat menu, filter dan sheet lulus pada kedua tema. Lima kasus gelap lulus pada APK akhir, termasuk lanskap, form panen/pembayaran dengan IME, sesi berakhir dan review lampiran. Kegagalan awal terjadi karena helper tes mencari tab horizontal saat menu ringkas aktif. Helper sekarang memakai pemilih tab adaptif |
| API 30, 360 dp, font 1,3, terang dan gelap | Masing-masing satu tes empat menu, filter dan sheet lulus pada iterasi UI menu. PNG ada pada folder konfigurasi masing-masing |
| API 30, sekitar 412 dp gelap dan 686 dp terang, font 1,0 | Dua kasus pada tema gelap dan satu pada viewport lebar lulus. Latar solid terlihat pada viewport lebar dan lanskap |
| API 30, penyimpanan lintas proses | Kedua fase lulus. Force-stop di antara fase membuktikan ID, hash antrean, draf mentah, dan satu pasangan EC/PPM tetap dapat dibaca |

`assembleDebug`, `assembleDebugAndroidTest`, 73 tes JVM, dan `lintDebug` lulus. Lint mencatat 0 error, 16 peringatan, dan 1 hint. Perubahan runtime terpusat di Kotlin/Compose. Backend, kontrak v2, penyimpanan lokal dan formula memakai implementasi yang sama. Pengaturan ukuran, kepadatan, font, dan tema emulator API 30 dipulihkan sesuai `after/settings-restored-api30-final.txt`.

Perbandingan PNG tersedia dalam `before-after-menu.png`, `before-after-operasional.png`, `before-after-transaksi.png`, `before-after-privat.png` dan `before-after-baseline.png`. `render-adaptif.png` merangkum ukuran dan tema. Bukti konflik operasional diambil dari iterasi sebelumnya yang lulus regresi 50 kasus. Tes peluncuran native merekam screenshot dari jendela Android. Tes laporan membatasi lebar dan ukuran huruf lewat harness Compose. Pasangan token kontras tercatat di `contrast.json`. Keenam belas pasangan teks aktif berada pada 6,04:1 atau lebih tinggi.

## Delivery Gate Anti Slop dan verifikasi Impeccable

| Aturan | Hasil | Bukti |
| --- | --- | --- |
| R-02, R-15–18, R-36–38 | PASS | Copy baru menyebut tindakan dan state. Count berasal dari snapshot outbox. Arah Operate dan dials mengikuti brief. Fixture tetap di test |
| R-23, R-33 | PASS | Resource botani pengguna dipakai ulang. Perubahan UI ada di source Kotlin |
| R-01, R-04–14, R-19–22, R-29–31 | PASS pada layar yang ditinjau | Header forest mengikat menu ke kartu konteks. Sage membedakan aksi. Kartu, toolbar dan panel bertingkat mengikuti kepadatan isi. Motion yang ada mengikuti skala sistem. Form dan laporan solid |
| R-25, R-34 | PASS pada pasangan dan konfigurasi yang diuji | Luminansi sRGB atas token terang dan gelap. Status juga tertulis. Bukti screenshot terang/gelap pada ukuran kecil dan normal |
| R-24, R-26–27, R-35 | PARTIAL | Handler scope/route, alur draf, sync, konflik, recovery dan ekspor diuji. Injeksi gagal baca lokal dan klik setiap varian kontrol turunan belum direkam |
| R-03, R-32, R-39 | PARTIAL | Empat lebar, tiga skala font, IME dan lanskap teruji. Tab, Enter, Back dan sentuh membuka Antrean di API 30. Fokus terlihat dan ikon toolbar 48 dp. API 37 merekam fokus TalkBack pada judul Antrean di `after/accessibility/talkback-antrean.png` dan pemulihan pengaturan. Audit audio dan traversal seluruh layar masih terbuka |
| R-28, R-40 dan aturan web | N/A untuk FAQ, DOM, CSS, lazy loading hero web dan detector HTML | Aplikasi Compose memakai satu resource lokal per viewport menu. Angka performa baru tidak disimpulkan dari screenshot |

Validasi perangkat fisik, UAT, pentest berizin dan pengujian backend produksi berada di luar bukti perubahan frontend ini.
