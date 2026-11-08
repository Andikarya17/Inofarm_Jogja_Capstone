# Audit desain menu pengelola, 9 November 2026

Method: design review only (detector not applicable, native platform).

## Putusan

Identitas aplikasi sudah cocok: hijau forest, sage, krem, dan botani terasa berhubungan dengan pekerjaan greenhouse. UI belum konsisten matang. Beberapa halaman sudah punya hierarki, sedangkan pemilih umum, alat keuangan, dan rincian perhitungan masih terasa seperti kumpulan kontrol standar. Menambah ornamen ke seluruh layar tidak menyelesaikan masalah ini.

Keluhan popup terlalu polos terkonfirmasi pada source `Choice`: setiap opsi hanya `TextButton`, tanpa radio/centang, sorotan pilihan aktif, atau divider. Sebaliknya pemilih greenhouse Beranda sudah memiliki radio dan sorotan. Tidak adanya garis sendiri bukan cacat universal; pada daftar ini pemisah, selected state, dan anatomi baris perlu diperbaiki bersama.

Arahan pengguna: APK sudah versi terbaru, warna sekarang dipertahankan, ilustrasi daun boleh dikurangi agar isi lebih menonjol, semua menu pengelola dan login masuk cakupan. Deliverable saat ini adalah audit dan master prompt, bukan implementasi.

## Dasar dan keterbatasan

- Source ditinjau pada HEAD `9ac7b3f`, turunan implementasi Beranda `d6774fc`. `git diff d6774fc HEAD -- android` kosong.
- Impeccable context memuat PRODUCT.md dan DESIGN.md. Native guidance dan mode Operate digunakan; hasil autodeteksi platform null tidak mengubah fakta aplikasi Compose.
- Screenshot historis diperiksa langsung: `outputs/frontend-secondary-menus/before-after-menu.png`, `before-after-transaksi.png`, `before-after-privat.png`; `after/frontend-secondary-light-412/login-ready.png`, `secondary-activities-filter.png`, `season-report.png`; `outputs/manager-home/after/light-412-picker.png` dan `after/frontend-secondary-manager-home-real/real-overview.png`.
- Beranda menggunakan evidence 8 November. Menu sekunder menggunakan evidence 7 November dan source saat ini. Ada perubahan berikutnya pada wiring konteks dan Antrean; screenshot lama bukan bukti semua state terbaru.
- Screenshot laporan memakai harness dengan pembesaran font 200%; ukuran teks pada gambar itu tidak boleh dianggap ukuran normal semua perangkat. Nama dan angka sintetis di screenshot bukan data bisnis.
- Administrasi, akun, dan ekspor dinilai dari source dan konteks evidence, bukan walkthrough visual baru. Animasi dinilai dari source; gambar statis tidak membuktikan kualitas gerak.
- Tidak menjalankan APK, build, tes DB, TalkBack, benchmark, atau pengukuran kontras baru pada audit dokumentasi ini. Tidak mengklaim seluruh interaksi lolos. Detector HTML tidak berlaku untuk Compose.

## Penilaian heuristik

Skala 0 buruk sampai 4 sangat baik. Ini penilaian desain sementara dari bukti terbatas, bukan hasil UAT atau skor aksesibilitas terukur.

| Heuristik | Skor /4 | Pertimbangan |
| --- | --- | --- |
| Status sistem terlihat | 3 | Draf, offline, antrean, dan penerimaan server sudah dibedakan; penekanan visual status masih lemah pada sebagian daftar |
| Sesuai bahasa pekerjaan | 3 | Greenhouse, musim, grade dan pembayaran jelas; rincian investasi masih memaparkan istilah mesin |
| Kendali pengguna | 3 | Back, simpan draf, tutup sheet dan koreksi tersedia di source; traversal baru belum diuji |
| Konsistensi | 2 | Pemilih greenhouse dan Choice umum memakai pola selected state berbeda |
| Pencegahan kesalahan | 3 | Review, konteks, saldo dan konfirmasi tersedia; pengaruh polish terhadapnya perlu regresi |
| Mengenali tanpa menghafal | 2 | Opsi aktif Choice tidak terlihat di daftar; pengguna harus mengingat nilai pada field sebelumnya |
| Efisiensi | 2 | Alat keuangan memerlukan pemindaian beberapa tautan setara; filter panjang |
| Estetika dan kesederhanaan | 2 | Warna koheren, tetapi header/kartu berulang dan ilustrasi lebar lebih kuat daripada sebagian isi |
| Pemulihan kesalahan | 3 | Alasan dan konflik tersedia; pemisahan penyebab, dampak dan tindakan dapat diperjelas |
| Bantuan kontekstual | 2 | Bantuan penting tersedia, tetapi beberapa penjelasan panjang dan teknis |
| Total sementara | **25/40** | Fondasi fungsional baik; pematangan visual dan konsistensi masih diperlukan |

## Inventaris dan rekomendasi

Path source di tabel relatif terhadap `android/app/src/main/java/com/inofarm/jogja/`.

| Area / source | Penilaian saat ini | Perbaikan yang diarahkan |
| --- | --- | --- |
| Login, `feature/auth/FinanceLogin.kt` | Panel forest memberi fokus yang baik; belum ada motion khusus untuk transisi form login | Kurangi dominasi bingkai daun, pertahankan panel; animasikan perubahan idle/loading/error dan transisi sukses singkat |
| Shell/navigasi/akun, `feature/FinanceRoot.kt`, `FinanceNavigation.kt` | Lima tujuan pengelola jelas; fallback sheet font besar sudah ada | Konsistenkan selected/focus, pisahkan identitas akun dari tindakan dan Keluar; jangan tambah tab keenam |
| Beranda, `feature/managerhome/ManagerHomeScreen.kt` | Identitas paling kuat; header dan beberapa judul berbidang gelap mengambil perhatian besar | Padatkan orientasi, satu fokus per cakupan, kurangi kapsul judul; pertahankan urutan berbasis kebutuhan pengelola |
| Greenhouse/periode, `feature/managerhome/RunningPeriodSheet.kt` | Pemilih greenhouse lebih matang daripada Choice; review periode membawa konsekuensi nyata | Samakan anatomi pilihan, divider halus; pisahkan pilih cakupan dari penetapan periode yang memerlukan tinjauan |
| Antrean, `feature/QueueScreen.kt` | Urutan perlu ditinjau dan jumlah sudah ada; kartu banyak bergantung pada teks | Status + masalah + tindakan menjadi hierarki utama; rincian ID/percobaan tetap dapat dibuka |
| Kegiatan/riwayat/filter, `feature/history/HistoryScreen.kt`, form di `FinanceRoot.kt` | Toolbar, daftar dan filter sudah terstruktur; banyak field pemilih tampak setara | Ringkas metadata, pertegas jenis kegiatan; kelompokkan filter konteks dan periode, tampilkan cakupan aktif |
| Jadwal/realisasi, `FinanceRoot.kt`, Beranda pengelola | Sudah terhubung dengan konteks dan pencatatan | Baris waktu, jenis, konteks, lalu Catat realisasi; tidak menciptakan status selesai/terlambat dari asumsi |
| Panen, `feature/harvest/HarvestScreen.kt` | Grade, buah dan kg tersedia; form/review masih panjang | Kelompok grade terbaca sebagai data, satuan dekat nilai; lebih jelas antara hasil panen dan saldo yang tersedia |
| Keuangan, `feature/expenses/FinancialEntryScreen.kt` | Header catatan baru berbeda dari riwayat, tetapi beberapa kontrol sama kuat | Pisahkan blok Catatan baru dan Riwayat; satu CTA utama; jangan menambah total kas campuran |
| Alat keuangan, `FinancialEntryScreen.kt` fungsi `FinanceTool` | Hanya tombol teks panjang, sedikit pembeda antar tujuan | Baris aksi berikon relevan, label, keterangan singkat, chevron navigasi, divider dan kelompok yang jelas |
| Pembayaran/alokasi, `FinancialEntryScreen.kt` | Nominal sudah menonjol; sumber/saldo dapat menjadi paragraf panjang | Nominal, sumber, tujuan, saldo dan alasan dalam kelompok label-nilai; pertahankan basis uang |
| Penjualan, `feature/sales/SaleEntryScreen.kt` | Review sudah memisahkan sumber panen | Rincian sumber per baris, bobot dan harga terpisah, total lunas sebagai fokus; tanpa animasi angka |
| Aset/penyusutan, `feature/assets/AssetEntryScreen.kt` | Review membaik, field dan referensi masih padat | Bedakan data aset, sumber pembayaran, periode dan alokasi; jangan menyamakan perolehan dengan biaya musim |
| Evaluasi musim, `feature/reports/SeasonReportScreen.kt` | Setiap indikator memakai panel dan tombol rumus serupa | Kelompokkan hasil menurut pertanyaan bisnis; prioritas hasil, status, basis; rumus/sumber terungkap bertahap |
| Investasi, `feature/investments/InvestmentScreen.kt` | Rincian expanded menampilkan input/diagnostik panjang | Pisahkan hasil, asumsi arus kas, sumber aktual/proyeksi dan rincian teknis; pertahankan semua alasan invalid |
| Lampiran, `feature/attachments/AttachmentScreen.kt` | Induk dan status sudah dipisahkan | Baris berkas dengan nama, jenis/ukuran, status, aksi nyata; preview hanya melalui akses privat yang ada |
| Ekspor, `feature/exports/ReportExportHost.kt` | Status proses dan hasil memakai dialog | Nama snapshot/jenis berkas jelas, feedback unduh dan batal; sukses hanya setelah operasi benar selesai |
| Cadangan/pemulihan, `feature/recovery/RecoveryHost.kt` | Banyak teks dalam dialog, aksi sensitif berdekatan dengan evaluasi | Kelompok terpisah Pemeliharaan; dampak/alasan/status utama jelas, detail teknis dapat dibuka |
| Administrasi, `feature/admin/AdminScreen.kt` | Fungsional, padat field dan tombol teks; belum diaudit visual baru | Kelompok cakupan, daftar data, editor, review; tindakan perubahan akses/penutupan musim terpisah |
| Konflik/audit/batal, `HistoryScreen.kt` | Versi lokal/server dan alasan tersedia | Dua bagian berlabel jelas, field berubah mudah dibandingkan; konfirmasi dan histori tetap utuh |

## Lima prioritas

1. **P1: pemilih tidak konsisten.** `FinanceRoot.kt:756` (`Choice`) belum memperlihatkan opsi terpilih. Satukan row, divider, radio/centang dan fokus. R-26/R-39, C-2/C-4. Penanda aktif merupakan kebutuhan interaksi, bukan ornamen.
2. **P1: Alat keuangan kurang mudah dipindai.** `FinancialEntryScreen.kt:107` (`FinanceTool`) hanya tombol teks. Gunakan tiga kelompok: Transaksi, Aset, Evaluasi; Pemeliharaan terpisah di bawah. R-14/R-31, C-3. Jangan mengubah tujuan atau izin.
3. **P2: fokus layar kalah oleh bingkai dan pengulangan kartu.** `FarmMenu.kt`, `BotanicalBackdrop.kt`, `ManagerHomeScreen.kt`. Kurangi ilustrasi di area kerja dan ukuran header; variasikan panel ringkasan, daftar, dan toolbar sesuai isinya. R-05/R-20/R-31. Kartu berulang tetap tepat untuk record sejenis.
4. **P2: rincian evaluasi/investasi terlalu menyerupai keluaran teknis.** `Indicator` pada laporan dan investasi. Susun hasil, alasan, input, rumus, kemudian diagnostik; jangan menyembunyikan alasan hasil invalid. R-06/R-15, C-1/C-3.
5. **P2: motion perlu diperluas secara selektif.** `FinanceLogin.kt` belum memiliki transisi khusus; komponen lain sudah punya sebagian motion. Tambahkan feedback login dan perubahan panel/filter, tanpa menganimasikan setiap kartu atau memutar angka uang. R-19/R-35/R-39.

Tidak ditemukan dasar untuk menyatakan bug kritis P0 dari audit ini. Skor dan prioritas tidak membuktikan tidak adanya bug.

## Beban kognitif dan pengalaman

Choice aktif yang tidak tampak membebani ingatan. Alat keuangan membawa lebih dari empat tujuan dalam satu sheet; lima tujuan navigasi utama sendiri masih sesuai pola Android, bukan alasan otomatis menghapus menu. Filter memiliki lima pemilih dan dua tanggal: kelompokkan sesuai tugas agar pengguna memahami kaitannya. Rincian algoritme di investasi meningkatkan beban membaca walau datanya sah.

Perjalanan login memberi kesan hangat, tetapi pemilih generik memutus konsistensi itu. Saat review uang dan konflik, pengguna perlu kepastian tentang konteks dan status server, bukan animasi perayaan. Pengelola yang sering mencatat perlu pemindaian cepat; pengguna satu tangan memerlukan baris tap luas; pengguna TalkBack memerlukan selected state dan fokus yang benar. Audit ini belum menguji ketiganya secara langsung.

Kekuatan yang dipertahankan: identitas warna, pemisahan data lokal/server, dan perhatian terhadap konteks serta nilai yang belum tersedia. Catatan kecil: seragamkan inset separator, kurangi metadata berulang, dan jangan memakai ikon rumah sebagai satu-satunya petunjuk untuk membuka greenhouse.

## Referensi resmi dan penerapan

- [Compose divider](https://developer.android.com/develop/ui/compose/components/divider): garis pemisah list tersedia sebagai komponen native; terapkan pada opsi dan kelompok, bukan bingkai semua elemen.
- [Compose bottom sheets](https://developer.android.com/develop/ui/compose/components/bottom-sheets): gunakan lifecycle sheet native; pilih container sesuai panjang daftar dan hubungan dengan field.
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults): target sentuh minimal 48 dp dan perilaku selection perlu dijaga.
- [Compose animation](https://developer.android.com/develop/ui/compose/animation/quick-guide): perubahan ukuran/isi dapat dianimasikan; angka durasi di master prompt adalah usulan desain Inofarm, bukan kutipan standar.

## Delivery Gate dokumen

- Direction: berasal dari palet/komponen saat ini dan jawaban pengguna; mode Operate, bukan landing page.
- Purpose: setiap rekomendasi mengikat bentuk pada pemindaian, orientasi, tindakan atau status.
- Liveliness: fokus per layar, aksen sage terarah, ritme panel/daftar, motif botani dibatasi.
- Honesty: skor sementara, screenshot historis dan penilaian source dibedakan dari verifikasi runtime.
- Assets: tidak membuat atau mengunduh aset baru. Tidak menilai folder untracked sebagai aset publikasi.
- Runtime gate R-35: belum dijalankan; menjadi acceptance implementasi berikutnya, bukan PASS audit ini.

Questions skipped: pengguna sudah menetapkan seluruh cakupan, palet, izin mengurangi ilustrasi, versi APK dan meminta master prompt; tidak ada keputusan tambahan yang menghalangi penulisan prompt. Ini mengikuti instruksi pengguna terbaru, bukan penutupan gate implementasi.

Lanjutkan dengan [master prompt pematangan UI pengelola](MASTER_PROMPT_MANAGER_UI_POLISH.md). Dokumen ini tidak menggantikan status acceptance implementasi sebelumnya.
