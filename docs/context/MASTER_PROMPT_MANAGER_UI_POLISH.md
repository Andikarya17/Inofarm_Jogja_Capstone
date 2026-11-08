# Master prompt frontend: pematangan UI pengelola Inofarm

Status: spesifikasi 9 November 2026, belum diimplementasikan. Baca bersama [audit dasar](MANAGER_UI_REVIEW_20261109.md). Instruksi pelaksanaan di bawah berlaku ketika prompt ini diminta untuk dijalankan.

## Peran dan tujuan

Bertindak sebagai senior mobile UX designer dan Android Compose engineer. Matangkan seluruh pengalaman pengelola Inofarm, termasuk login, lima menu utama, semua alur turunannya, popup pilihan, filter, review, detail dan administrasi. Hasil harus terasa dirancang sebagai alat kerja greenhouse melon: hangat, jelas, teliti, enak dipindai, dan konsisten.

Pengguna sudah menetapkan:

- Palet hijau forest, sage dan krem saat ini dipertahankan.
- Ilustrasi daun boleh dikurangi agar isi lebih menonjol.
- Semua menu pengelola termasuk Antrean dan Keuangan masuk scope, bukan hanya Beranda.
- Popup option perlu pembeda antaropsi yang jelas, termasuk garis pemisah yang halus.
- Login perlu motion yang berguna dan ringan.
- Gunakan Anti Slop mode `during` serta skill Impeccable project-local. Jangan bertanya ulang tentang mode, warna, atau cakupan yang sudah diputuskan.
- Tanyakan hanya keputusan yang benar-benar belum jelas dan mengubah kebutuhan produk. Kerjakan bagian independen selama menunggu. Jangan mulai aset baru tanpa arahan eksplisit untuk aset tersebut.

## Baseline, konteks, dan batas

1. Baca AGENTS root/lokal; `docs/context/README.md`, `IMPLEMENTATION_STATUS.md`, `GAP_ANALYSIS.md`, PRD/architecture/contracts yang relevan, `PRODUCT.md`, `DESIGN.md`, audit pendamping, dan source/test aktual.
2. Baseline saat prompt disusun adalah `9ac7b3f`, dengan source Android sama dengan `d6774fc`. Upgrade Beranda sudah ada. Verifikasi HEAD baru sebelum bekerja, jangan kembali ke main lama atau menjalankan ulang prompt historis dari nol.
3. Baca skill `antislop`, pendamping UI/human/layoutmobile/copywriting yang relevan, dan `.agents/skills/impeccable/SKILL.md`. Jalankan launcher context sekali, gunakan mode Operate serta native Android guidance. Baca craft-floor sebelum mengedit UI. Terapkan aturan web sebagai prinsip yang relevan, jangan menambahkan CSS/ARIA/GSAP ke Compose.
4. Pertahankan Kotlin + Jetpack Compose, Laravel REST API, MySQL dan seluruh kontrak v2. Ini refinement presentation, bukan migrasi framework, perubahan rumus, atau perluasan fitur bisnis.
5. Buat branch deskriptif `codex/` untuk fitur yang dikerjakan, periksa diff/staging, pertahankan perubahan pengguna dan file untracked. Ikuti otorisasi Git yang berlaku. Jangan merge/deploy otomatis.
6. Jangan mengganti `DESIGN.md` dengan dunia visual baru. Catat token/komponen yang benar-benar diimplementasikan, dan pisahkan target rancangan dari hasil yang sudah diuji.
7. Worker bukan target redesign baru, tetapi komponen bersama wajib diregresi pada peran pekerja. Jangan menampilkan fitur keuangan/administrasi kepada pekerja.

## Design Read

Mode: Operate. Watak: tenang, hangat, teliti. Aplikasi harus mudah dipakai saat pengguna berganti antara melihat kebun dan memeriksa catatan uang.

| Permukaan | ENERGY / RHYTHM / MOTION | Focal point |
| --- | --- | --- |
| Login | 2 / 2 / 2 | Form dan tombol Masuk |
| Beranda pengelola | 2 / 2 / 2 | Cakupan aktif, keadaan greenhouse dan tindakan relevan |
| Antrean, Kegiatan, Panen, Keuangan | 2 / 2 / 2 | Status/record penting dan satu tindakan utama |
| Pemilih, form, review, detail, administrasi | 1 / 2 / 2 | Pilihan atau keputusan yang sedang dibuat |
| Evaluasi dan investasi | 1 / 2 / 2 | Hasil, status kelengkapan, periode dan basis |

MOTION 2 berarti transisi state singkat, bukan choreography per halaman. Identitas berulang berasal dari konteks forest, pilihan sage, bidang krem, angka tabular, dan botani kecil pada pintu masuk. Kartu record seragam boleh dipakai untuk data sejenis. Variasi layout harus mengikuti isi.

## Sistem visual yang harus dijaga

Gunakan semantic roles yang sudah ada pada `core/design/InofarmTheme.kt` dan `FarmChrome.kt`. Nilai berikut adalah jangkar palet, bukan izin hard-code hex di setiap screen.

| Peran | Warna saat ini | Pemakaian |
| --- | --- | --- |
| Brand | `#2B503D` | Aksi utama pada tema terang |
| Ink | `#142B23` | Teks utama pada bidang terang |
| Sage | `#D4E4B7` | Pilihan/tindakan pada panel forest |
| Background / surface | `#F4F3E8` / `#FFFDF4` | Kanvas dan bidang kerja |
| Soft / divider | `#E6EBD9` / `#D3DBCB` | Pengelompokan dan pemisah ringan |
| Muted / outline | `#52665B` / `#687970` | Metadata dan batas kontrol |
| Warning / error | `#805016` / `#9A3029` | Perhatian dan kegagalan dengan label tertulis |
| Dark background / surface | `#132820` / `#203B30` | Lapisan tema gelap |
| Dark selected / divider | `#385442` / `#4A6456` | Pilihan dan pemisah tema gelap |

- Pertahankan tema sistem dan skema gelap yang tersedia. Jangan mengaktifkan dynamic color yang mengganti identitas hijau–krem. Gunakan pasangan on-color yang sesuai.
- Surface solid untuk isi, dialog, sheet, form dan laporan. Efek kaca yang sudah ada di navigasi tetap dibatasi dan memiliki fallback opak; jangan memperluas blur ke semua panel.
- Tipografi memakai SansSerif sistem: body 14–16 sp, label 12–14 sp, judul bagian 16–20 sp, judul halaman 24 sp, angka utama 24–32 sp sesuai ruang. Pertahankan skala yang sudah cocok; jangan mengurangi teks untuk memaksa muat.
- Angka uang memakai formatter fixed decimal/`FarmAmount` dan angka tabular. Jangan singkat menjadi Rp1,2jt pada keputusan transaksi. Satuan selalu berdekatan dengan nilai.
- Ritme: 4 dp label-nilai, 8–12 dp dalam kelompok, 16 dp tepi/isi, 24 dp antarkelompok. Radius kontrol 12 dp, kartu 20 dp, panel utama 24 dp; gunakan token yang ada.
- Hindari kapsul berwarna di semua judul. Judul bagian cukup tipografi dan jarak ketika latar sudah solid.
- Kurangi ilustrasi dashboard menjadi aksen header/tepi yang tidak melewati teks. Daftar panjang, filter dan form memakai kanvas tenang. Reuse drawable yang telah dilacak; pengaturan crop/tint/placement boleh, jangan membuat aset baru atau mempublikasikan folder untracked.

Alasan: warna mempertahankan identitas; surface solid menjaga ketelitian; tipografi sistem menjaga keterbacaan; spacing membedakan hubungan isi; divider membantu pemindaian; botani kecil memberi karakter tanpa mengalahkan data.

## Prioritas pertama: semua pemilih dan popup

Audit semua pemanggilan `Choice`, `AlertDialog`, `ModalBottomSheet`, `DropdownMenu`, menu akun, filter, pemilih greenhouse/periode, serta Alat keuangan. Buat komponen bersama di `core/design` hanya bila pola benar-benar berulang. Nama implementasi boleh menyesuaikan source.

### Anatomi baris pilihan

- Container solid, judul yang menyebut objek pilihan, tombol tutup dengan nama aksesibel. Jelaskan konteks hanya bila judul belum cukup.
- Baris minimum 48 dp; target awal 56 dp satu baris dan 72 dp dengan keterangan, tetapi tinggi harus tumbuh mengikuti font/nama panjang.
- Label utama rata kiri, keterangan relevan di bawah jika tersedia. Jangan menambah deskripsi generik pada semua opsi.
- Single select mempunyai radio/centang dan sorotan tonal pada nilai aktif. Opsi lain tetap terbaca. State terpilih harus tersedia pada semantics, bukan warna saja.
- Gunakan `HorizontalDivider` 1 dp pada batas antaropsi untuk pemilih data dan daftar alat. Inset sejajar awal teks, tanpa divider sesudah opsi terakhir. Pisahkan kelompok dengan heading/jarak lebih besar. Jangan menjadikan divider satu-satunya petunjuk batas sentuh.
- Seluruh row melakukan satu tindakan. Hindari radio dan row menjadi dua target TalkBack atau dua callback terpisah. Ikon dekoratif tidak dibaca dua kali.
- Simpan posisi daftar/kueri yang diperlukan selama interaksi. Tampilkan nilai aktif ketika pemilih dibuka ulang. Nilai lama yang tidak tersedia tidak otomatis diganti opsi pertama.
- State wajib: tidak dipilih, terpilih, pressed/focus, disabled beralasan, memuat, kosong, gagal, dan hasil pencarian kosong bila pencarian disediakan.

### Container dan perilaku

- Menu akun singkat tetap dropdown berjangkar; pisahkan identitas, Administrasi, dan Keluar. Dialog konfirmasi tetap untuk keputusan berkonsekuensi.
- Daftar pilihan yang panjang memakai sheet dengan area gulir dan judul tetap. Daftar kecil boleh dialog pilihan native. Visual row dan selected state harus konsisten di kedua container.
- Jangan menumpuk dua modal bottom sheet. Saat filter/periode sudah dalam sheet, gunakan panel pemilih internal yang punya Kembali, atau dialog pilihan native yang fokusnya diuji. Back pertama menutup pemilih, bukan membuang form.
- Pilihan langsung memperbarui field lalu menutup pemilih untuk single select biasa. Penetapan periode, hapus/batal, dan perubahan akses tetap melalui review/konfirmasi asli.
- Jika daftar panjang perlu pencarian, cari hanya atas opsi scoped yang tersedia dan sebut keterbatasan cache. Jangan memberi kesan pencarian global server tanpa API yang mendukungnya.
- Jangan menambah ikon arbitrer pada setiap nama greenhouse/musim. Ikon bermakna lebih berguna pada tujuan aksi.

### Alat keuangan

Ganti baris `FinanceTool` yang hanya teks menjadi action row: ikon relevan, label, keterangan singkat bila membedakan fungsi, chevron untuk membuka workspace, divider dan feedback sentuh. Buat kelompok Transaksi (alokasi biaya, penjualan lunas), Aset (perolehan, penyusutan), Evaluasi (musim, investasi), serta Pemeliharaan (cadangan/pemulihan). Pertahankan Catat pengeluaran sebagai CTA utama hub.

Keterangan harus menjelaskan beda transaksi, bukan janji baru. Contoh: Alokasikan biaya → Pembagian pembayaran ke musim. Tombol disabled tetap menjelaskan kebutuhan greenhouse. Jangan gunakan radio untuk daftar aksi dan jangan menjalankan transaksi hanya karena row disentuh.

## Pekerjaan per layar

| Area | Hierarki/layout target | Perilaku yang wajib dipertahankan |
| --- | --- | --- |
| Login | Identitas ringkas, panel forest, field, feedback dekat form, CTA Masuk. Kurangi daun di ruang kerja; satu fokus visual | Email/Password, toggle, Next/Done, izin jaringan, error endpoint, logout status, password tidak dipersistenkan |
| Shell dan akun | Toolbar ringkas, navigasi aktif jelas, menu akun berkelompok | Lima tab pengelola, empat pekerja, fallback font besar, safe insets, Back |
| Beranda seluruh greenhouse | Header cakupan lebih pendek, daftar greenhouse, perhatian berbukti dan ringkasan yang tersedia | Scope assignment, freshness, sumber status, tanpa metrik/rekomendasi rekaan |
| Beranda satu greenhouse | Periode tanam, EC dan PPM, sumber/waktu, aksi pencatatan, kas dan catatan relevan | Satu catatan manual berpasangan, fallback WIB, missing bukan nol, scope baru segera mengikat isi |
| Periode tanam | Daftar kandidat terkelompok, periode aktif jelas, review sebagai langkah terpisah | Hak akses, alasan, status busy, idempotensi dan konflik versi |
| Antrean | Ringkasan status ringkas; record menonjolkan masalah dan aksi, detail teknis dapat diperluas | Urutan prioritas yang ada, draf vs accepted, retry, konflik, scope/epoch, Back ke Antrean |
| Kegiatan | Toolbar cakupan/filter, jenis dan ringkasan kegiatan lebih kuat daripada metadata | Form pengukuran/manual multi-bahan, alasan dilewati, target belum tersedia, histori/koreksi/batal |
| Jadwal | Waktu dan kegiatan, konteks/notes, Catat realisasi | Tidak otomatis menandai seluruh jadwal selesai; jangan menyimpulkan terlambat tanpa kontrak |
| Panen | Grade sebagai kelompok dengan buah/kg, ringkasan hasil dan status | Tambah/hapus grade, nilai nol sah vs kosong, saldo dan versi, koreksi/review |
| Keuangan | Blok Catatan baru berbeda dari Riwayat. Selector jenis, cakupan dan filter mudah dipindai | Greenhouse catatan baru terpisah dari filter histori; semua jenis riwayat tetap tersedia |
| Pembayaran/alokasi | Nominal dominan, lalu sumber, tujuan, kategori dan saldo; bantuan dekat field | Pembayaran tidak sama dengan biaya musim; sumber terkunci, saldo versi, dasar alokasi |
| Penjualan lunas | Sumber panen per kelompok, kg/harga, ringkasan total, review | Total dihitung menurut kontrak; tidak menambah piutang atau animasi penghitung uang |
| Aset/penyusutan | Identitas aset, perolehan, nilai sisa/umur, sumber pembayaran; periode dan nominal alokasi terpisah | Pembayaran, perolehan dan beban musim tidak digabung; tidak isi nominal otomatis |
| Evaluasi musim | Status dan basis/periode sebelum hasil; kelompok indikator relevan, rincian rumus/sumber dapat dibuka | Kelengkapan, semua alasan provisional/invalid, snapshot dan sumber tidak hilang |
| Investasi | Skenario, aktual/proyeksi, hasil, arus kas/asumsi, rincian teknis | NPV/IRR/PI/PP sesuai kontrak, diskonto eksplisit, hasil unavailable/invalid tidak dianggap berhasil |
| Lampiran | File row, nama/ukuran/jenis/status, konteks induk, aksi | Batas/izin, versi induk, enkripsi dan preview privat; tidak membuka URL publik baru |
| Ekspor | Snapshot/format, progress, hasil atau error dengan tindakan jelas | Ekspor snapshot yang dipilih, akses server, pemilih dokumen native, cancel |
| Cadangan/pemulihan | Workspace/dialog tenang dengan dampak, alasan, status dan rincian terpisah | Capability, konfirmasi, epoch, audit dan reconciliation; jangan melakukan backup nyata saat QA tanpa otorisasi |
| Administrasi | Cakupan, daftar data, editor berkelompok, review perubahan | Semua resource aktual, draf per akun, perubahan sesi/akses, password memory-only, konfirmasi |
| Konflik/audit/pembatalan | Label versi lokal dan server kuat, pasangan field dapat dibandingkan, alasan dan tindakan jelas | Tidak menghapus histori, tidak auto-resolve, tidak menyamarkan pembatalan sebagai edit |

Pada laporan dan investasi, hindari satu kartu besar identik untuk setiap indikator. Gunakan kelompok hasil dan baris pendukung, sambil menjaga setiap indikator mudah ditemukan. Jangan menambah chart jika sumber numeriknya belum tersedia atau jika grafik hanya mengulang angka. Pertahankan angka presisi/rumus di rincian; presentasi tidak boleh mengubah perhitungan.

Untuk form, satukan isian menurut pekerjaan: konteks, waktu, hasil/nominal, sumber dan keterangan. Label selalu terlihat, satuan dekat field, error dekat penyebab. Tindakan utama dan Simpan draf dan kembali harus tetap terjangkau saat IME tampil. Jangan menjadikan semua form wizard baru tanpa kebutuhan.

## Motion specification

Durasi berikut adalah titik awal desain, perlu dinilai di perangkat. Reuse `FarmMotion` dan animasi existing; hindari dua transisi pada node yang sama.

| Pemicu | Gerak target | Batas/perilaku |
| --- | --- | --- |
| Login pertama tampil | Satu fade-through panel maksimal 220–260 ms jika membantu orientasi | Form langsung dapat digunakan; tanpa stagger field, tanpa animasi daun berulang; tidak replay saat mengetik/IME/recompose |
| Masuk ditekan | Label/loading berubah dalam ruang tombol tetap, sekitar 150–180 ms | Submit tunggal, indikator mengikuti request, tanpa progress palsu atau delay buatan |
| Error login | Pesan muncul dan ruangnya berkembang sekitar 180–220 ms | Tidak shake; error terbaca/terumumkan, email tidak hilang |
| Auth sukses | Fade-through ke shell 180–220 ms | Hanya sesudah sesi sah; tidak menampilkan data akun sebelumnya |
| Pilihan aktif/tab | Perubahan tonal/indikator 150–200 ms | State langsung benar; hormati screen reader dan kurangi gerak sistem |
| Sheet/menu | Transisi native Material | Jangan override spring tanpa alasan; dismiss mengembalikan fokus ke pemicu |
| Buka rincian | Expand/collapse 180–220 ms | Fokus/scroll stabil, anak tersembunyi tidak tetap terbaca TalkBack |
| Filter/cakupan berubah | Feedback isi singkat dan state memuat yang tepat | Jangan crossfade data lama seolah milik scope baru; scroll dipertahankan hanya bila masih relevan |
| Draf/antrean berganti status | Label dan tonal berubah sekitar 150–200 ms | Hanya berdasarkan state asli, bukan waktu animasi |

Hormati Remove animations/skala durasi sistem. Saat nonessential motion dimatikan, gunakan perubahan langsung. Tidak ada bounce uang, count-up, shimmer tanpa akhir untuk data cached, parallax, konfeti login atau pulsa sinkronisasi dekoratif. Jangan menambah Lottie/dependency hanya untuk efek dasar yang tersedia di Compose.

## Integritas domain dan aksesibilitas

- Server tetap menentukan actor, scope, role, akses file/export/sync. UI tidak membuka akses baru.
- Durable draft/outbox per akun, atomic local record+operation, version conflict, receipt, tombstone dan restore epoch tidak berubah.
- Simpan lokal tidak sama dengan diterima server. Jangan memakai toast sukses ambigu atau centang hijau accepted sebelum receipt sah.
- Kalender WIB/HSS/HST, fase aktual, EC mS/cm dan PPM ppm tetap sesuai kontrak; tidak ada IoT/otomatisasi baru.
- Uang fixed decimal. Jangan menggabungkan pembayaran, alokasi, penyusutan, panen, penjualan dan proyeksi menjadi saldo rekaan.
- Kontras teks normal minimal 4,5:1, teks besar dan indikator kontrol relevan minimal 3:1. Ukur pasangan aktual pada kedua tema; jangan menganggap tint transparan pasti aman.
- Target interaksi minimal 48 × 48 dp, tidak tumpang tindih. Gunakan semantics heading/selected/stateDescription yang benar, urutan fokus logis, label ikon, focus visible, Tab/Enter/Back dan TalkBack.
- Uji font scale sampai 2,0, 320/360/412 dp, lanskap dan IME. Jangan paksa tinggi tetap pada nama, pesan, nominal atau option row.
- Jika tablet termasuk target yang didukung, verifikasi 600–840+ dp dan adaptasi navigasi/layout sesuai ukuran tanpa mengganti pola pengguna ponsel. Catat cakupan perangkat nyata.

## Urutan pelaksanaan

### Gate wajib sebelum edit UI: jalankan aplikasi dan cocokkan screenshot

Jangan mulai mengubah UI hanya berdasarkan source, audit ini, atau screenshot historis. **Build dan jalankan aplikasi Android dari checkout/HEAD yang akan diubah terlebih dahulu**, pada emulator atau perangkat uji yang tersedia. Jangan menganggap APK yang kebetulan terpasang berasal dari source terbaru.

1. Periksa instruksi run proyek, konfigurasi debug dan ketersediaan emulator/perangkat. Catat commit, varian build, hash APK, API Android, ukuran viewport, density, font scale dan tema. Gunakan environment/akun uji terotorisasi; jangan mencetak kredensial atau mengganti data aplikasi pengguna.
2. Buka login dan masuk sebagai **pengelola**. Kunjungi Beranda (seluruh dan satu greenhouse), Antrean, Kegiatan, Panen, Keuangan, serta semua tujuan turunannya dalam inventaris. Buka popup option secara nyata: Choice umum, pemilih greenhouse/periode, filter, Alat keuangan dan menu akun. Jangan menyimpulkan popup baik hanya karena halaman induknya terlihat baik.
3. Ambil screenshot runtime baru pada kondisi yang sebanding dengan referensi: route, peran, scope, isi/state, posisi scroll, tema, viewport dan font scale. Gunakan referensi lokal yang disebut dalam audit pendamping, serta screenshot tambahan dari pengguna bila tersedia. Gambar laporan dari harness font 200% harus dibedakan dari screenshot layar aplikasi normal.
4. Susun tabel **referensi → screenshot runtime → konfigurasi → cocok/berbeda → penjelasan**. Bandingkan struktur, hierarki, warna, spacing, pilihan aktif, divider, navigasi, clipping dan overlay. Perbedaan jam atau isi fixture dicatat sebagai beda data, bukan otomatis regresi visual. Simpan perbandingan bersebelahan dengan skala proporsional.
5. Jika runtime berbeda dari screenshot referensi, telusuri versi APK/branch, konfigurasi, scope dan state terlebih dahulu. Setelah itu, gunakan runtime yang terbukti berasal dari HEAD sebagai baseline implementasi; screenshot lama tetap menjadi bukti historis. Jangan memaksa runtime kembali ke desain lama hanya agar gambar sama.
6. Jalankan interaksi dasar sebelum edit: buka/pilih/tutup opsi, Back, perpindahan tab, fokus form dan IME. Rekam login dan transisi yang relevan karena screenshot statis tidak bisa membuktikan animasi. Jangan mengirim transaksi, perubahan akses atau cadangan nyata demi walkthrough; gunakan fixture/harness terisolasi yang jelas labelnya bila diperlukan.
7. Simpan hasil awal dalam evidence baru dan laporkan perbedaan material sebelum implementasi. Jika build, launch, akun pengelola atau backend uji belum tersedia, selesaikan setup aman yang terotorisasi. Jika tetap terblokir, laporkan penyebab serta bukti yang belum tersedia dan minta hanya informasi yang diperlukan. **Jangan melewati gate ini dengan klaim source inspection setara menjalankan aplikasi; tunda edit visual yang baseline-nya belum dapat diperiksa.** Dokumentasi dan diagnosis independen boleh dilanjutkan.

### Sesudah baseline runtime terverifikasi

1. Lengkapi inventaris route/state berdasarkan walkthrough di atas. Pisahkan screenshot fixture, real API, dan bukti historis. Tandai surface yang belum dapat diakses; jangan menganggapnya sudah diverifikasi.
2. Perbaiki shared picker/action row/section/status/typography terlebih dahulu. Uji pada Choice umum, pemilih greenhouse, Alat keuangan, dan menu akun.
3. Terapkan pada Antrean, Kegiatan, Panen, Keuangan beserta form/review/detail/filter. Pertahankan tujuan, callback, test tags dan semantik yang valid.
4. Matangkan laporan, investasi, lampiran, ekspor, cadangan/pemulihan dan administrasi. Dokumentasikan alasan jika suatu surface hanya perlu konsistensi komponen.
5. Rapikan Beranda dan login; terapkan motion yang ditentukan. Jangan menghapus upgrade Beranda sebelumnya.
6. Jalankan verifikasi, perbaiki temuan, simpan before/after pada konfigurasi yang sama, baru perbarui evidence, DESIGN.md dan context dengan hasil nyata.

Jangan berhenti setelah beberapa layar terlihat cantik. Inventory harus menandai setiap area sebagai diperbaiki, dipertahankan dengan alasan dan bukti, atau belum terverifikasi dengan blocker konkret.

## Acceptance dan evidence

| ID | Syarat lulus | Bukti yang diminta |
| --- | --- | --- |
| UI-00 | Aplikasi dari HEAD dijalankan sebelum edit UI dan dibandingkan dengan screenshot referensi | Identitas APK/perangkat, walkthrough pengelola, screenshot baru, tabel cocok/berbeda dan diagnosis selisih |
| UI-01 | Seluruh area inventaris tercakup, termasuk modal/overlay | Matriks route → source → keadaan → screenshot/test |
| UI-02 | Semua pemilih memiliki selected state, pemisah sesuai aturan dan fokus benar | Screenshot awal/aktif/kosong/nama panjang serta uji pilih, buka ulang, Back |
| UI-03 | Alat keuangan dapat dipindai per kelompok dan tujuan/izin benar | Klik setiap action row, uji disabled sebelum konteks tersedia |
| UI-04 | Hierarki setiap menu jelas tanpa memperbesar semua kartu | Before/after ukuran sama, focal point dan alasan keputusan per layar |
| UI-05 | Login motion tersedia, tidak replay karena IME/recompose dan tidak menunda auth | Rekaman idle/loading/error/success, motion on/off, submit ganda |
| UI-06 | Terang/gelap, teks besar, nama/nominal panjang tetap terbaca | Screenshot matriks ukuran/font/tema, hasil ukur kontras aktual |
| UI-07 | Interaksi native dan aksesibilitas bekerja | Catatan tap/Tab/Enter/Back/IME/TalkBack per flow, fokus kembali sesudah overlay |
| UI-08 | Draf, state dan antrean tidak berubah akibat polish | Regresi lintas rotasi, proses, akun, offline/reconnect, konflik dan accepted |
| UI-09 | Domain keuangan dan hak akses tidak berubah | Tes existing terkait format, scope, receipt, saldo, peran pekerja/pengelola |
| UI-10 | Motion ringan dan sistem Remove animations dihormati | Rekaman/observasi perangkat; ukur frame bila menyatakan performa, jangan mengarang angka |
| UI-11 | Error/loading/empty/offline/last-known jujur | Evidence tiap pola bersama dan state spesifik tiap area |
| UI-12 | Tidak ada action dekoratif atau tujuan yang rusak | Log click-through setiap tombol, pemilih, menu, detail dan dialog |

Gunakan `assembleDebug`, `lintDebug`, unit/regresi yang relevan dan instrumentasi Compose sesuai tooling proyek. Build lulus saja tidak membuktikan kualitas visual. Tidak perlu membuat tes yang hanya mengulang konstanta warna/radius; prioritaskan selected state, dismiss/focus, single-submit dan preservasi draf. Jalankan DB test hanya bila diperlukan dan hanya setelah guard effective MySQL `inofarm_test` lulus. Jangan reset/migrate:fresh/seed `inofarm_local`, clear app-data, atau memicu tindakan sensitif nyata demi screenshot.

## Delivery Gate dan serah terima

Jalankan empat blok Anti Slop: Hard Gate, Purpose, Liveliness, Craftsmanship. Periksa semua kontrol, semua tema dan keadaan penting; tandai aturan khusus web N/A beserta alasannya. Impeccable HTML detector tidak berlaku untuk native Compose. Jangan melaporkan PASS jika hanya membaca source atau memakai screenshot lama.

Serahkan perubahan yang nyata, alasan utama, file terkait, screenshot sebelum/sesudah, rekaman login/motion, hasil build/tests, click-through, daftar batas dan item belum lulus. Pisahkan usulan desain, implementasi source, verifikasi emulator dan penerimaan perangkat fisik/UAT. Update `docs/context/README.md` dan evidence baru tanpa menghapus histori.

## Referensi yang dipakai sebagai prinsip

- [Divider Compose](https://developer.android.com/develop/ui/compose/components/divider): pemisahan baris dan kelompok secara native.
- [Bottom sheets Compose](https://developer.android.com/develop/ui/compose/components/bottom-sheets): container dan lifecycle overlay.
- [Accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults): target sentuh dan komponen selection.
- [Animation quick guide](https://developer.android.com/develop/ui/compose/animation/quick-guide): perubahan state/ukuran/isi. Cocokkan API dengan dependency proyek sebelum memakai contoh terbaru.

Ambil prinsip interaksi dan hierarki dari referensi, bukan menyalin identitas aplikasi lain. Beri Inofarm karakter melalui warna yang sudah dimiliki, komposisi sesuai pekerjaan, dan detail interaksi yang selesai.
