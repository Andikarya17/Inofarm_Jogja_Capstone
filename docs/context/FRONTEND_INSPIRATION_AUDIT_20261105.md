# Audit frontend dan kecocokan UI inspo

5 November 2026. Method: design review only (detector not applicable, native platform). Anti Slop DURING dipilih pengguna untuk sesi ini; Impeccable dipakai dalam mode Operate. Deliverable berupa audit dan master prompt, bukan implementasi baru.

## Kesimpulan

Kedua inspirasi bisa diadaptasi ke Inofarm. Referensi plant shop menyumbang warna hijau dalam, fotografi tanaman, focal point, dan hubungan antara gambar dengan panel. Referensi login menyumbang foto lingkungan yang dominan dan panel masuk yang terpisah jelas dari latar. Struktur toko, keranjang, wishlist, promosi, social login dan registrasi tidak cocok untuk fungsi yang tersedia sekarang.

Pengguna memilih adaptasi selektif: kaca terbatas pada login/navigasi; form dan angka tetap jelas. Pengguna meminta foto melon hidroponik asli dari internet, bukan generasi AI. Pilihan ini menjadi arahan target dalam [master prompt baru](MASTER_PROMPT_FRONTEND_BOTANICAL.md). `DESIGN.md` tetap mendeskripsikan implementasi saat ini.

## Bukti dan batas penilaian

- Baseline source: `b38b46b`, `feat(android): redesign operational and finance workflows`. Tidak ada perubahan tracked saat audit dimulai. Branch dokumentasi: `codex/frontend-inspiration-audit`.
- Dua JPG di `UI inspo/` dibuka dan diperiksa visual, termasuk `login page.jpg`.
- Screenshot existing yang dibuka: `outputs/frontend-redesign/after-light-412/` untuk login, worker-home, manager-finance, worker-measurement-pair-accepted, worker-form-start, worker-history; `after-dark-360-font130/worker-home.png`; `after-dark-320-font200/manager-long-context.png`.
- Screenshot tersebut adalah bukti lokal pekerjaan sebelumnya pada 5 November, bukan capture baru sesi audit. Tanggal file login 5 November 2026 13:48 WIB. Struktur panel, warna, metrik, login, dan reflow navigasi cocok dengan source yang diperiksa. Kesamaan ini mendukung relevansi sampel, tetapi bukan pembuktian bahwa setiap pixel berasal dari commit yang sama.
- Screenshot memakai data sintetis instrumentation. Nilai dan nama di dalamnya bukan data usaha Inofarm.
- Source diperiksa pada tema, chrome, login, root/shell, konteks budidaya, pengukuran, riwayat, hub/form keuangan dan sampel laporan. Backend, rumus, keamanan server dan seluruh alur bisnis tidak diaudit.
- Tidak menjalankan build, emulator, click-through, TalkBack atau profiling baru. Tidak memberi skor aksesibilitas/performa menyeluruh dari gambar. Bukti uji sebelumnya tetap di [evidence frontend](ANDROID_OPERATIONAL_FRONTEND_20261105.md).
- Launcher `impeccable context` tidak selesai dan dihentikan; context dibaca langsung. Detector HTML, overlay browser dan subagent B tidak berlaku untuk Compose. Audit dipersistenkan di dokumen ini; tidak ada snapshot/trend dari engine Impeccable.

## Mengapa masih terasa polos

Rombak sebelumnya memperbaiki organisasi pekerjaan: login dua field, konteks dalam sheet, dua kartu pengukuran, review dan hierarki aksi. Namun dunia visualnya masih didominasi latar pucat/hijau gelap rata, panel solid seragam dan teks sans. Perbedaan dari referensi terutama ada pada fotografi, komposisi dan kedalaman material. Tambahan blur saja tidak menyelesaikannya.

Prompt sebelumnya menyebut warna kaya dan kaca selektif, tetapi belum menetapkan foto wajib, crop, susunan viewport login, porsi gambar beranda atau syarat kecocokan visual dengan referensi. Acceptance engineering jauh lebih konkret daripada acceptance identitas. Ini menjelaskan bagaimana hasil bisa lebih rapi dan teruji, tetapi tetap terasa jauh dari inspirasi.

## Temuan bernomor

Prioritas P1 berarti masalah penggunaan yang perlu ditangani sebelum hasil redesign diterima, P2 berarti perbaikan hierarki/komposisi, P3 berarti polish. Tingkat ini bukan tier pelanggaran otomatis Anti Slop. Aturan disebut sebagai kriteria evaluasi, bukan hasil detector.

### 1. P2: login belum memiliki identitas visual greenhouse

**Bukti:** `feature/auth/FinanceLogin.kt:89` dan screenshot login. Susunan vertikal terpusat berisi nama produk, panel putih dan dua field, dengan ruang kosong besar di atas/bawah. Tidak ada citra lingkungan kerja. Login masih bisa dipakai aplikasi lain setelah nama diganti.

**Dampak:** kesan pertama belum menyampaikan dunia melon hidroponik yang diinginkan. Ruang kosong tidak membawa informasi atau identitas.

**Arahan:** foto greenhouse melon sebagai latar; subject tetap terlihat di area atas; panel hijau gelap di area tengah-bawah; Email, Password dan Masuk tetap inti. Saat IME/font besar, foto mengalah pada ruang form. Foto situs lain tidak boleh diberi keterangan seolah kebun Inofarm.

**Kriteria:** R-20, R-31, R-37; Impeccable shape/bolder. Ini kekurangan terhadap brief visual, bukan kegagalan autentikasi.

### 2. P2: kartu konteks mengambil terlalu banyak perhatian dibanding pekerjaan

**Bukti:** `feature/context/HomeContextCard.kt:47` dan screenshot pengukuran diterima. Panel putih besar berisi label konteks, nama GH, musim/kelompok, umur dan tombol outline. Pada 320 dp/font 200%, nama panjang memenuhi sebagian besar viewport.

**Dampak:** pekerja harus melewati konteks yang sudah diketahui untuk membaca pengukuran atau mencatat. Pada kondisi belum memilih GH, layar tampak kosong setelah satu panel besar.

**Arahan:** jadikan konteks blok ringkas dengan satu crop foto di area atas/samping, hierarki GH lalu musim/kelompok/umur; tombol ubah tetap jelas. Pada font besar pindahkan foto keluar dari alur, kecilkan peran heading dengan token yang tepat, bukan memaksa font mengecil. Nama lengkap tetap dapat dibaca melalui expand atau sheet yang tersedia.

**Kriteria:** C-3, R-03, R-14, R-31; Impeccable layout/adapt. Ruang kosong bukan alasan menambah metrik atau fitur palsu.

### 3. P2: panel bersama menghasilkan ritme yang terlalu seragam

**Bukti:** `core/design/FarmChrome.kt:123`, `InofarmTheme.kt`, screenshot form dan hub keuangan. `FarmPanel` memakai surface yang sama, radius 20 dp, padding 16 dp dan jarak 12 dp untuk bermacam isi.

**Dampak:** konteks, form dan menu alat memiliki bobot visual mirip. Pengguna melihat kumpulan kotak sebelum melihat struktur tugas.

**Arahan:** pertahankan komponen bersama tetapi bedakan peran: konteks bergambar, metrik berangka, section form solid, transaksi berupa baris, review sebagai ringkasan. Jangan mengganti semua panel menjadi kaca. Kesetaraan EC/PPM tetap disengaja dan tepat.

**Kriteria:** R-05, R-14, R-20, R-31; Impeccable layout/colorize.

### 4. P2: informasi status penting belum memiliki tingkat penekanan yang cukup berbeda

**Bukti:** `feature/activities/MeasurementDashboard.kt:91` dan `feature/history/HistoryScreen.kt:51`; screenshot pengukuran dan riwayat. Freshness, waktu, pencatat, penerimaan dan sinkron muncul sebagai beberapa blok teks. Pesan sync cukup menonjol sebelum daftar.

**Dampak:** metadata bersaing dengan hasil ukur atau catatan yang sedang dicari. Menghapus metadata akan merusak kejujuran state, jadi solusinya ialah hierarki.

**Arahan:** satu baris ringkasan freshness/status, detail sumber yang dapat dibuka, metadata dengan ukuran body-small yang tetap terbaca. Offline/konflik/penolakan tetap terlihat dan seluruh alasan dapat diakses. Jangan menyatukan accepted, lengkap dan sesuai-target sebagai satu status sukses.

**Kriteria:** R-27, R-38, C-3; Impeccable clarify/layout.

### 5. P1: navigasi dua baris pada font besar memakan ruang kerja

**Bukti:** `feature/FinanceRoot.kt:211` membagi destinasi menjadi dua baris ketika fontScale >1,4 dan lebar <480 dp. Screenshot 320 dp/font 200% menunjukkan bar bawah tinggi, bersamaan dengan kartu konteks bernama panjang.

**Dampak:** font tidak terpotong, tetapi ruang untuk isi menyusut tajam. Reflow yang lolos clipping belum otomatis efisien digunakan.

**Arahan:** evaluasi alternatif navigasi aksesibel pada ukuran ini, misalnya pola overflow/drawer untuk tujuan sekunder dengan label jelas. Pertahankan seluruh tujuan, role gating dan kemampuan kembali. Jangan mematikan font scaling atau menghilangkan label untuk mempertahankan tampilan mockup. Ini memerlukan uji interaksi sebelum memilih solusi final.

**Kriteria:** R-03, R-32, R-39, C-4; Impeccable adapt. Screenshot tidak membuktikan tujuan tidak bisa dicapai; temuan ini mengenai efisiensi ruang.

### 6. P3: material kaca belum membawa karakter referensi

**Bukti:** `core/design/FarmChrome.kt:40` memakai tint 94%, highlight gradient dan border pada navbar. Latar di belakang hampir seragam. Implementasi tidak memakai backdrop blur.

**Dampak:** hasil terbaca sebagai navigasi tonal, sedangkan referensi memiliki kedalaman dari foto, variasi terang-gelap dan lapisan. Ini kesenjangan ekspektasi visual, bukan bug hanya karena tidak blur.

**Arahan:** dahulukan foto dan tonal depth. Kaca hanya satu panel login atau navbar; isi teks opak. Jika memakai blur, evaluasi versi Compose dan fallback perangkat terlebih dahulu. `Modifier.blur` memburamkan isi composable, bukan otomatis mengambil backdrop; jangan pasang pada parent form. [Android Developers](https://developer.android.com/develop/ui/compose/graphics/blur/overview).

**Kriteria:** R-10, R-12, R-31, R-40; Impeccable polish. Jank pada bukti lama bukan bukti kaca penyebabnya.

## Yang layak dipertahankan

- Dua kartu EC/PPM punya angka, label dan unit yang mudah dibedakan, serta sumber pasangan dan waktu yang jujur.
- Login sederhana dengan toggle password bernama dan IME; kontrol tidak perlu ditambah hanya untuk menyerupai inspirasi.
- Tema terang/gelap dan fondasi token sudah tersedia. Perubahan berikutnya bisa fokus pada komposisi dan aset.
- State lokal/server, offline dan konflik sudah menjadi bagian bahasa produk. Visual baru harus memperjelasnya.

## Pemetaan inspirasi

| Elemen referensi | Kecocokan | Adaptasi Inofarm |
|---|---|---|
| Hijau gelap dan sage | Kuat | Login/hero forest, surface kerja terang atau gelap mengikuti tema |
| Tanaman besar sebagai focal point | Kuat dengan penggantian subjek | Melon menggantung/greenhouse nyata, bukan tanaman hias dalam pot |
| Serif editorial pada brand | Terbatas | Boleh satu aksen nama produk; angka, label dan form tetap sans |
| Foto latar penuh pada login | Kuat | Crop dengan area aman untuk panel, form dapat scroll saat IME |
| Panel kaca dan edge highlight | Selektif, disetujui pengguna | Login atau navigasi, tidak semua kartu sekaligus |
| Hero promosi/carousel toko | Tidak relevan | Ganti blok konteks budidaya yang punya fungsi; tanpa slide promosi |
| Search produk, wishlist, keranjang | Tidak relevan | Destinasi existing sesuai peran; jangan membuat e-commerce |
| Sign Up, Google, Apple, forgot password | Tidak sesuai kontrak existing | Jangan ditampilkan tanpa alur autentikasi yang nyata |
| Foto rumah di hutan | Suasana cocok, subjek tidak | Ganti foto melon hidroponik asli dari internet |
| Tipografi kecil dalam mockup | Tidak disalin | Teks sp, touch 48 dp, skala font dan keadaan lapangan |

## Kelayakan dan foto

Komposisi foto, scrim, panel tonal, radius dan hierarki dapat dikerjakan di Compose existing. `Image` mendukung crop; tidak perlu mengganti native Android dengan web. [Dokumentasi Image](https://developer.android.com/develop/ui/compose/graphics/images/customize).

`minSdk=29` dan BOM Compose `2025.08.01` pada source adalah batas nyata. Dokumentasi terbaru tidak menjamin seluruh API baru tersedia pada BOM itu. Uniform blur Android memerlukan API 31+; gunakan fallback solid pada perangkat yang tidak mendukung. Tidak ada alasan dari audit ini untuk memaksa upgrade atau dependency kaca.

[Daftar kandidat foto](FRONTEND_PHOTO_SOURCES_20261105.md) mencatat sumber, asal fotografi, status pemakaian dan kebutuhan crop. Belum ada foto yang dibeli, diunduh ke resources atau diintegrasikan. Pencarian internet dilakukan, tanpa image generation.

## Delivery Gate audit dan prompt

| Kelompok | Hasil sesi ini |
|---|---|
| Direction / R-37 | Selesai: adaptasi selektif dan foto internet non-AI dikonfirmasi pengguna |
| Honesty / R-17,18,36,38 | Observasi, rekomendasi dan bukti historis dibedakan; tidak ada data usaha rekaan |
| Asset / R-23 | Kebutuhan dan sumber dicatat; izin gambar belum dianggap selesai |
| Purpose / R-01,06,10,14,31 | Alasan warna, foto, material, tipografi dan komposisi tertulis dalam prompt |
| Liveliness | Dials target dan focal point tiap layar ditentukan; kesesuaiannya masih harus diuji pada implementasi |
| Runtime / R-03,25,26,27,32,34,35,39,40 | Belum diuji ulang; dokumen tidak mengklaim UI baru siap rilis |
| Dokumentasi | Audit, kandidat foto dan prompt terhubung; source runtime tidak diubah |

Tidak ada skor teknis total karena sesi ini tidak menguji seluruh aksesibilitas, performa dan interaksi. Prioritas pelaksanaan: komposisi login/beranda, hierarki panel/status, adaptasi font besar, lalu polish. Pertanyaan arah desain dan sumber aset telah dijawab. Pemilihan file foto berlisensi final tetap menjadi tahap pengadaan aset saat implementasi.
