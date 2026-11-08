# Master prompt: upgrade Beranda pengelola Inofarm

Tanggal: 8 November 2026. Target pelaksana: GPT 6.1 Sol.
Status: spesifikasi implementasi, bukan bukti fitur sudah tersedia.
Prompt pemanggil: [RUN_MANAGER_HOME_UPGRADE.md](RUN_MANAGER_HOME_UPGRADE.md).

## Mandat

Implementasikan lima upgrade berikut sampai terintegrasi, diuji, dan terdokumentasi pada Android native Kotlin + Jetpack Compose, Laravel REST API, dan MySQL:

1. Pemilihan sehari-hari cukup greenhouse.
2. Beranda awal pengelola langsung menyajikan ringkasan seluruh greenhouse yang boleh diakses.
3. Informasi perlu perhatian mempunyai dasar data, waktu, dan tindakan yang jelas.
4. Setelah memilih greenhouse, Beranda tetap berupa ringkasan dengan akses ke detail.
5. Keuangan di Beranda sederhana dengan makna dan cakupan yang tepat.

Gunakan skill **Impeccable** dan **antislop mode DURING**. Pengguna telah memilih DURING dan meminta preferensi ini berlaku seterusnya; jangan menanyakan mode lagi. Gunakan skill UI, human/accessibility, layoutmobile, dan copywriting yang relevan. Ini aplikasi kerja, sehingga gunakan Impeccable Operate beserta panduan Android/native. Baca craft-floor sebelum mengedit UI. Jangan berhenti pada mockup, usulan, perubahan teks, atau UI berisi data contoh.

Pengguna juga meminta desain yang bagus, cocok dengan tampilan aplikasi sekarang, tidak polos, dan memiliki animasi. Pertahankan identitas botani yang sudah diimplementasikan. Ikuti spesifikasi visual dan motion di bawah; hasil akhir harus diperiksa melalui render aplikasi.

## Konteks bisnis dan batas keputusan

Menurut pengguna, satu lokasi Inofarm mempunyai empat greenhouse. Dalam satu greenhouse, penanaman berlangsung bersama dengan satu varietas melon dan fase yang sama. Pengelola memahami lokasi dan greenhouse; pengguna tidak perlu memahami hierarki database untuk membuka informasi.

- Empat adalah kondisi usaha yang dijelaskan pengguna, bukan batas/hardcode jumlah kartu, nama, ID, atau seed produksi.
- Satu greenhouse mempunyai banyak periode secara historis. Alur harian menggunakan satu periode budidaya berjalan dan satu kelompok tanam yang relevan. Varietas, tanggal semai/pindah, umur, dan fase adalah informasi.
- Identitas season/group tetap dipertahankan untuk referensi kegiatan, panen, alokasi, laporan, audit, dan draf. Menyederhanakan UI tidak mengizinkan menghapus atau menggabungkan riwayat.
- `status=open` pada musim lama tidak otomatis membuktikan bahwa tanaman masih aktif: musim bisa dibuka kembali untuk koreksi keuangan. Bedakan periode budidaya berjalan dari izin koreksi transaksi.
- Fase berasal dari catatan aktual yang sah, bukan estimasi otomatis berdasarkan umur. HSS/HST menggunakan kalender WIB, mulai hari 1; sebelum pindah tanam, HST belum berlaku.
- Cakupan utama adalah pengelola. Pertahankan fungsi pekerja dan pembatasan peran; uji regresi komponen bersama. Tidak merombak login, semua form, atau navigasi utama tanpa kebutuhan langsung.
- Arahan ini menggantikan larangan dalam prompt menu lanjutan lama untuk mengubah dashboard, hanya pada cakupan upgrade ini. Persyaratan keselamatan data dan fungsi lain tetap berlaku.

## Persiapan wajib

1. Baca AGENTS root/lokal dan SOP relevan. Periksa Git status/diff/staging; jangan menyentuh untracked milik pengguna, khususnya folder inspirasi/aset/desain historis. Branch fitur memakai `codex/` sesuai aturan repo, dengan dependency antarfitur tercatat. Jangan reset, stash, atau delete pekerjaan pengguna demi branch switch.
2. Baca bagian relevan `docs/context/README.md`, PRD, ARCHITECTURE, DATA_CONTRACTS, IMPLEMENTATION_PLAN, IMPLEMENTATION_STATUS, GAP_ANALYSIS; keputusan dan TEST_PLAN di `docs/migration/finance-20261029/`; PRODUCT.md dan DESIGN.md. Dokumen audit lama tidak menggantikan source terbaru.
3. Baca kontrak executable `contracts/v2/MASTERS.md`, REFERENCES, MEASUREMENTS, HISTORY, LOCAL_STORAGE, BACKGROUND_SYNC, EXPENSES, SALES, HARVEST, SEASON_EVALUATION dan openapi/schema terkait. Cocokkan source sebelum menentukan API tambahan.
4. Jalankan context launcher Impeccable sekali per sesi sesuai skill. Pertahankan sistem visual aktual; tidak perlu meminta ulang keputusan warna/mode yang sudah diberikan pengguna.
5. Inspeksi source dan test. Titik awal yang sudah ditemukan pada penyusunan prompt:
   - `android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt`: FinanceHome, state gh/season/group, rendering bersyarat, navigasi dan Back.
   - `feature/context/HomeContextCard.kt`: sheet tiga tingkat dan label konteks.
   - `feature/context/ContextRepository.kt`, `ContextViewModel.kt`: sumber master dan cache.
   - `feature/activities/MeasurementDashboard.kt`: pasangan pengukuran dan status.
   - `feature/history/`, `feature/expenses/`, `core/design/`: filter tujuan, keuangan, tema dan komponen bersama.
   - Backend `routes/api_v2.php`, layanan master, scoped reads, ledger, transaksi dan aggregation aktual.
6. Ambil baseline screenshot pengelola tanpa pilihan dan dengan konteks, serta pekerja. Catat commit/build/perangkat. Screenshot pengguna menjelaskan keluhan; fixture sintetis pada gambar bukan data usaha.
7. Susun matriks requirement → acceptance → source/test → evidence. Tentukan kontrak data dan resolver periode terlebih dahulu, kemudian implementasikan end-to-end dalam bagian yang bisa diverifikasi. Lanjutkan pekerjaan independen tanpa meminta approval berulang.

## 1. Pemilihan cukup greenhouse

### Alur dan bahasa

- Login pengelola baru masuk ke Beranda `Semua greenhouse`. Tidak membuka modal wajib.
- Header cakupan membuka sheet berjudul `Pilih greenhouse`, berisi `Semua greenhouse` dan greenhouse yang sah. Pilihan selesai dengan satu ketukan, sheet menutup, state terpilih terbaca.
- Menekan kartu greenhouse di ringkasan menjalankan transisi yang sama. Tidak ada langkah memilih musim/kelompok sesudahnya.
- Pakai `Ganti greenhouse`, `Periode tanam`, dan `Riwayat periode tanam` pada konteks pengguna. Jangan mengganti identifier wire atau histori hanya demi label.
- Dalam sesi yang sama, pulang dari detail/tab, rekreasi Activity, dan pemulihan proses mempertahankan pilihan serta posisi gulir bila masih sah. Login baru kembali ke semua greenhouse; jangan membawa pilihan akun lain.
- Cakupan browsing bukan parent draf. Mengganti greenhouse tidak boleh mengubah parent atau isi draf/outbox yang sudah dibuat. Saat membuat catatan, konteks tujuan ditampilkan jelas dan divalidasi kembali.

### Resolver periode yang aman

Tetapkan satu sumber kebenaran untuk konteks aktif, dipakai UI, cache, dan server. Periksa apakah model existing mampu membedakan budidaya berjalan dari musim yang sekadar terbuka untuk koreksi. Bila belum, buat kontrak additive penunjukan periode berjalan beserta kelompoknya dan alur pengelolaan yang minimal, tervalidasi, serta diaudit. Jangan menamai status/endpoint baru seolah sudah tersedia.

Resolver harus mempunyai state eksplisit: loading, ready, no-active-period, ambiguous/needs-review, unavailable/offline-cache, forbidden/inactive. Kelayakan mencakup relasi parent, scope, status greenhouse/group, dan penunjukan periode yang valid.

- Kandidat sah tunggal dapat diselesaikan otomatis bila aturan model membuktikan bahwa itu periode berjalan. Tidak memilih berdasarkan nama, posisi pertama, created_at terbaru, atau open semata.
- Tidak ada periode: tampilkan `Belum ada periode tanam aktif`, akses riwayat tetap tersedia; aksi menambah periode hanya melalui jalur administrasi yang sah.
- Banyak kandidat/data legacy ambigu: tampilkan `Periode tanam perlu ditinjau`, berikan akses pengelolaan; tahan pencatatan baru yang parent-nya belum jelas. Ringkasan lain yang aman tetap tampil.
- Pengelola dapat menetapkan/mengganti periode berjalan secara eksplisit melalui review yang menunjukkan greenhouse, varietas, tanggal, dan dampaknya. Tidak menutup musim keuangan lama otomatis.
- Terapkan aturan satu periode budidaya berjalan secara server-side dan atomic, termasuk create/update/reopen/penunjukan bersamaan sesuai jalur yang relevan. Jangan mengandalkan disable tombol Android.
- Data historis multi-group tetap dapat dibaca. Tidak melakukan backfill/merge/delete atau memilih satu kelompok secara diam-diam. Perubahan baru mengikuti praktik satu varietas dan tanggal bersama; konflik lama dilaporkan dan ditangani melalui tindakan beralasan.
- Revalidasi setelah pull, perubahan hak, penggantian periode, dan saat submit. Perubahan konteks tidak me-rebase draf lama ke periode baru.

## 2. Beranda awal adalah ringkasan seluruh Inofarm

`Semua greenhouse` berarti seluruh greenhouse dalam kewenangan akun, bukan seluruh database. Jangan menampilkan label seluruh Inofarm tanpa penjelasan bila akses akun hanya sebagian.

Komposisi satu daftar gulir:

1. Header forest ringkas: `Ringkasan kebun`, cakupan terpilih, tanggal WIB, dan status kesegaran data yang ringkas.
2. Daftar ringkasan greenhouse sebagai isi utama: nama aktual, varietas periode berjalan, umur dengan label terbaca, fase aktual, waktu catatan operasional terakhir. Bila data tidak tersedia, sebutkan yang belum tersedia.
3. `Perlu perhatian` hanya jika ada temuan berdasar data. Gangguan akses/sinkronisasi yang menghalangi pembacaan dapat tampil di atas daftar sebagai pemberitahuan ringkas.
4. `Catatan terbaru`: maksimum lima catatan diterima server dalam cakupan sah, jenis kegiatan/pengukuran/panen, greenhouse, nama pencatat, waktu kejadian. `Lihat semua` menuju riwayat sesuai jenis dan filter. Cantumkan waktu penerimaan bila berbeda dan membantu menjelaskan keterlambatan offline.
5. `Keuangan bulan ini`: ringkasan kas tercatat dan tautan Keuangan sesuai aturan bagian 5.

Untuk telepon, gunakan kartu greenhouse vertikal yang ringkas dan mudah dipindai. Jumlah mengikuti data. Jangan carousel yang menyembunyikan greenhouse atau grid sempit yang memotong nama/umur. Tablet dapat memakai susunan adaptif jika urutan baca tetap jelas.

Ringkasan terbaru harus mempunyai aturan deterministik, misalnya occurred_at DESC lalu ID stabil, memakai seluruh cakupan query, bukan satu halaman riwayat yang kebetulan dimuat. Nama pencatat dan petugas mengikuti arti field existing, bukan saling menggantikan.

Tidak perlu rata-rata EC/PPM lintas greenhouse, grafik dekoratif, leaderboard pekerja, atau KPI produktivitas rekaan. Tidak mengklaim semua pekerja sudah mengirim data. GH inaktif tidak menjadi sasaran pencatatan aktif, tetapi historinya tetap dapat dicapai dan transaksi historisnya tetap dihitung jika masuk cakupan/periode keuangan.

## 3. Perlu perhatian berdasarkan bukti

Setiap temuan memiliki jenis, scope, record/rule sumber, waktu pemeriksaan, alasan, dan tujuan tindakan. Deduplicate berdasarkan identitas stabil. Urutkan hambatan pencatatan/akses lebih dahulu, lalu jadwal atau kelengkapan. Warna selalu disertai teks.

| Jenis | Syarat | Bahasa/tindakan |
|---|---|---|
| Pengukuran hari ini belum tercatat | Cakupan periode diketahui dan pembacaan data mempunyai coverage yang jelas | `Belum ada pengukuran tercatat hari ini`; tampilkan waktu pemeriksaan dan buka pengukuran/riwayat |
| Data hanya cache/tidak lengkap | Offline, pull belum lengkap, atau fetch gagal | `Data terakhir tersimpan…` / `Belum dapat memeriksa catatan hari ini`; jangan menganggap nihil catatan |
| Jadwal perlu ditinjau | Jadwal eksplisit dan aturan realisasi/deadline telah terdefinisi | Tampilkan jadwal, waktunya dan bukti; jangan menyimpulkan terlambat jika completion policy belum tersedia |
| Nilai di luar acuan | Acuan sah/berversi dan toleransi/aturan perbandingan tersedia | Sebut nilai, satuan, waktu, dasar acuan; jika acuan tidak lengkap, `Belum dapat dibandingkan` |
| Antrean perlu tindakan | Operasi lokal akun/perangkat ini benar-benar konflik/ditolak/tertahan | Tautan ke detail Antrean dengan alasan; jangan menyebut total antrean semua pekerja |
| Periode ambigu | Resolver tidak dapat menentukan konteks sah | Tautan ke pengelolaan periode tanpa memilih otomatis |

Tidak ada label `Tanaman bermasalah`, `Pekerja belum bekerja`, `Semua aman`, atau skor kesehatan yang diturunkan hanya dari ketiadaan catatan. Pemberitahuan kelengkapan bukan kewajiban pengukuran harian yang baru. Bagian ini bukan fitur push notification baru.

## 4. Beranda satu greenhouse tetap ringkasan

- Header memuat nama greenhouse dan konteks periode berjalan; akses `Semua greenhouse` mudah ditemukan. Varietas, tanggal semai/pindah, umur, dan fase tampil sebagai informasi dengan pemenggalan yang baik.
- Tepat dua kartu utama pengukuran: EC mS/cm dan PPM ppm, berasal dari satu record sesuai `MEASUREMENTS.md`. Pertahankan fallback WIB hari ini → kemarin → sebelumnya, label waktu, legacy EC belum tersedia, dan pending lokal yang diizinkan kontrak. Jangan mencampur pasangan, menambah pH sebagai kartu ketiga, atau mengonversi EC↔PPM.
- Catatan lokal lengkap yang boleh tampil pada pengukuran tetap diberi label `Belum tersinkron`; jangan memasukkannya diam-diam ke agregat accepted.
- Tampilkan ringkasan kegiatan/jadwal yang tersedia, panen periode berjalan, serta keuangan dengan basis eksplisit. Panen adalah hasil panen, bukan penjualan atau estimasi uang.
- Gunakan `Lihat kegiatan`, `Lihat panen`, `Lihat keuangan`, dan `Riwayat periode tanam`. Tujuan membawa greenhouse, periode/tanggal dan jenis yang sesuai dengan ringkasan. Header tidak boleh mengaku terfilter bila query tujuan belum difilter.
- Back dari detail mengembalikan asal dan cakupan, tanpa menghilangkan draf. Riwayat periode lama merupakan penelusuran tersendiri dan tidak mengganti periode aktif untuk pencatatan.
- Aksi pencatatan existing tetap dapat dicapai saat konteks valid. Jika memilih semua greenhouse lalu membuat catatan, wajib tentukan satu greenhouse sah sebelum form, bukan memakai greenhouse pertama.
- Untuk query cepat berganti GH A → GH B, hasil A yang terlambat tidak boleh menimpa B. Keys mencakup akun, server, scope, epoch/periode dan parameter query relevan.

## 5. Keuangan sederhana dan tepat

### Ringkasan kas bulanan

Pada semua greenhouse maupun satu greenhouse, judul `Keuangan bulan ini` memiliki cakupan dan periode WIB eksplisit. Bulan menggunakan interval awal bulan inklusif sampai awal bulan berikutnya eksklusif berdasarkan paid_at. Penjualan dan pembayaran periode lama tetap masuk jika paid_at berada dalam bulan tersebut.

- `Penerimaan penjualan`: jumlah posted total penjualan aktif, lunas, diterima server; gunakan nilai transaksi authoritative sesuai pembulatan existing SALES, bukan menghitung ulang dengan cara lain.
- `Pengeluaran tercatat`: jumlah pembayaran aktif diterima server pada paid_at, termasuk operating dan asset_acquisition sesuai arti uang keluar existing. Satu pembayaran dihitung sekali.
- Alokasi biaya dan penyusutan tidak ditambahkan lagi ke total uang keluar. Tidak memakai seluruh nominal sumber expense yang terpilih lewat filter musim seolah semuanya biaya musim tersebut.
- Tidak menampilkan `Laba`, ROI, NPV, saldo rekening atau kas tersedia dari dua angka ini. Bila selisih dibutuhkan kemudian, labelnya selisih penerimaan/pengeluaran tercatat, bukan laba; tidak perlu menambahnya dalam upgrade awal.
- Draf, queued, konflik, rejected dan transaksi batal tidak masuk total accepted. Koreksi accepted/tombstone mengubah ringkasan sesuai versi sah tanpa double-count.

### Periode tanam dan biaya bersama

Ringkasan biaya periode, jika ditampilkan, menjadi bagian terpisah dengan label periode dan basis: alokasi biaya aktif serta penyusutan sesuai kontrak evaluasi. Gunakan hasil evaluasi authoritative bila memang tersedia, dengan status sementara/kelengkapan dan akses laporan. Jangan menciptakan perhitungan laba kedua di dashboard.

Saat ini expense mempunyai greenhouse_id dan alokasi ke musim greenhouse yang sama. Jangan mengarang kategori kas tingkat lokasi atau membagi biaya lintas greenhouse otomatis. Pembayaran yang belum teralokasi tetap dihitung dalam kas bulanan greenhouse pemiliknya; akses detail harus membantu memahami status alokasi. Kebutuhan biaya bersama lintas greenhouse dicatat sebagai gap bila model belum mendukung, tanpa mengubah model akuntansi di luar scope.

### Kualitas angka

Uang memakai fixed decimal/BigDecimal, tidak Double/Float. Nol hanya bermakna total catatan nol dalam query lengkap, bukan bukti nihil kegiatan usaha. Bila query lengkap tanpa transaksi, gunakan `Belum ada transaksi tercatat pada periode ini`; bila belum dimuat/gagal/coverage tidak lengkap, tampilkan belum tersedia atau cache berwaktu. Jangan menjumlahkan satu halaman list untuk mengklaim total.

Seluruh endpoint/read cache/tautan keuangan hanya untuk pengelola dengan scope sah. Worker denial diuji termasuk endpoint agregat, detail, cache, file dan navigasi setelah demotion sesuai jalur yang berubah.

## Kontrak data, backend, dan offline

Reuse repository dan domain service yang benar. Tambahkan read model/agregasi backend bila dibutuhkan untuk ringkasan lintas greenhouse yang lengkap dan konsisten; jangan mengerjakan N+1 request per kartu. Pisahkan presentation, domain resolver/summary, dan data; jangan menambah seluruh logika ke FinanceRoot.

Dokumentasikan kontrak baru sebelum menghubungkannya ke UI. Kontrak mencakup role/scope, filter, kalender, interval, decimal string, status coverage, as_of, periode aktif, sumber/rule temuan, dan error state. As_of bukan janji semua perangkat pekerja telah tersinkron. Semua bagian satu snapshot harus mempunyai basis konsisten; bila waktu berbeda, labeli per bagian.

Agregasi memakai seluruh dataset scoped, menghitung setiap identitas sekali, mengecualikan cancelled, dan menjaga konsistensi dengan koreksi concurrent. Endpoint detail/aggregate tidak boleh memperluas scope melalui parameter GH/season/group milik akun lain. Reuse transaction/read consistency dan authorization boundary existing.

Cache ringkasan per server+akun+scope+periode+epoch. Saat offline, gunakan snapshot terakhir lengkap dengan waktu; jangan menampilkan sebagian halaman sebagai total final. Loading/error satu bagian tidak mengosongkan bagian valid lain. Refresh saat kembali STARTED, perubahan sumber/pull diterima, pergantian hari/bulan WIB, atau perubahan hak/periode. Hindari polling berat baru dan reset scroll akibat refresh.

Antrean lokal adalah milik perangkat/akun ini. Dashboard pengelola tidak mempunyai akses ajaib ke draf/outbox perangkat pekerja. Logout, revoke, demotion dan restore epoch tidak boleh membocorkan cache akun/peran lama atau menghapus draf untuk menyelesaikan masalah tampilan.

## Desain dan animasi wajib

Gunakan Impeccable untuk membentuk hierarki, kepadatan, states, typography dan motion, bukan hanya mencantumkan nama skill di laporan.

- Pertahankan BotanicalBackdrop/background_dashboard existing sebagai bingkai viewport, forest untuk orientasi/cakupan, sage sebagai aksen pilihan, krem solid untuk informasi. Reuse token InofarmTheme/FarmChrome/FarmShape/FarmSpace; jangan membuat tema paralel atau menimpa sistem desain seluruh aplikasi.
- Buat Beranda terasa berisi melalui data yang tertata: header ringkas, kartu greenhouse yang mudah dibandingkan, baris aktivitas dengan waktu, serta angka keuangan yang jelas. Hindari semua informasi menjadi kartu besar identik. Ruang kosong tetap disengaja dan tidak diisi ilustrasi/grafik rekaan.
- Kartu GH punya nama dominan, varietas/umur/fase sebagai informasi sekunder, dan affordance membuka ringkasan. Status menggunakan teks, bukan warna saja. Data panjang tidak terpotong tanpa akses lengkap.
- Ornamen daun tidak mengganggu teks. Form/detail/review tetap solid. Tidak membuat aset baru, mengambil gambar internet, atau mengubah file asli pengguna untuk tugas ini.
- Sheet memakai perilaku Material native, pilihan aktif jelas, sentuhan minimal 48 dp, heading/semantics/TalkBack benar. Nama tombol menjelaskan tujuan.
- Animasi pilihan greenhouse: transisi warna pilihan dan pergantian isi singkat sekitar 150–250 ms, menggunakan state/keys yang benar. Bersihkan data scope lama atau beri loading terlebih dahulu; animasi tidak boleh membuat metrik A tampak sebagai B.
- Expand/collapse rincian memakai perubahan ukuran/fade yang halus. Detail memakai transisi navigasi yang konsisten dengan aplikasi; Back dapat diprediksi. Refresh mempertahankan isi valid dan memakai indikator ringan.
- Hindari angka uang/EC/PPM yang menghitung naik, shimmer tanpa henti, parallax daun, bouncing, dan stagger panjang pada setiap reload. Motion menjelaskan perubahan, bukan menunda pekerjaan.
- Hormati animator duration scale/reduced motion Android; pada animasi nonaktif, seluruh fungsi/state tetap langsung benar. Gunakan API Compose sesuai versi proyek, tanpa dependency animasi baru kecuali benar-benar diperlukan dan diverifikasi.
- Verifikasi terang/gelap, layar 320–412 dp, font 1.0/1.3/2.0, landscape, nama panjang, nominal besar, inset navbar dan keyboard. Daftar terakhir harus dapat digulir di atas navigasi.
- Ambil screenshot dan rekaman transisi dari build nyata. Periksa jank yang terlihat dan ukur sampel bila tersedia; jangan mengklaim performa produksi berdasarkan emulator debug.

## Acceptance dan pengujian

| ID | Hasil yang wajib dibuktikan |
|---|---|
| MH-01 | Login pengelola menuju semua greenhouse tanpa sheet wajib; jumlah/nama mengikuti data dan scope, termasuk 0, 1, 4 dan lebih dari 4 fixture |
| MH-02 | Satu ketukan memilih GH, resolver mengisi parent benar; no-active dan ambiguous mempunyai penjelasan/tindakan tanpa salah parent |
| MH-03 | Periode lama yang reopen untuk koreksi tidak otomatis menggantikan budidaya berjalan; concurrent activation tidak menghasilkan dua periode berjalan |
| MH-04 | Semua ringkasan bersumber data, bukan fixture runtime atau penjumlahan satu halaman; uji dataset melewati batas pagination existing |
| MH-05 | Temuan mempunyai bukti; offline/coverage parsial tidak menghasilkan tuduhan belum bekerja, jadwal palsu, atau alarm tanpa acuan |
| MH-06 | Satu GH memakai satu pasangan EC/PPM; uji hari ini/kemarin/sebelumnya, legacy EC, queued, cancelled dan pergantian hari WIB |
| MH-07 | Catatan terbaru menunjukkan pencatat/jenis/GH/waktu yang benar; detail dan Back mempertahankan cakupan/asal |
| MH-08 | Kas bulanan tepat di batas WIB; pembayaran, aset, alokasi dan penyusutan tidak double-count; panen tidak dianggap penjualan |
| MH-09 | Nol/empty/missing/error/last-known dibedakan; pending lokal tidak masuk accepted total; koreksi/cancel memperbarui agregat |
| MH-10 | Scope lintas akun/GH, pekerja, demotion, revoke, logout, respons terlambat dan epoch tidak membocorkan data atau menampilkan hasil lama |
| MH-11 | Draf/outbox tetap utuh setelah ganti GH/periode, Back/IME, rotasi, background dan process death; tidak ada reparent otomatis |
| MH-12 | Terang/gelap, font besar, TalkBack/fokus, target sentuh, nama panjang/angka besar dan scroll lolos tinjauan visual; motion on/off tetap benar |
| MH-13 | Tujuan Keuangan/Kegiatan/Panen membawa filter sesuai ringkasan; kembali mempertahankan pilihan; login baru kembali semua GH |
| MH-14 | Pekerja tetap dapat melakukan alur existing yang sah; login, pengukuran, panen, keuangan dan sync terkait tidak mengalami regresi |

Jalankan unit test domain/resolver/aggregation, build dan lint Android, Compose instrumentation pada alur berubah, serta backend tests relevan bila kontrak/server berubah. Uji concurrency nyata bila menambahkan invariant periode. Test backend hanya effective MySQL `inofarm_test`, dengan guard sebelum SQL; jangan migrate:fresh/reset/seed database pengguna atau clear app-data pengguna.

Sediakan fixture sintetis terisolasi yang realistis, tanpa menjadikannya data produksi. Mock membuktikan UI/kontrak yang diuji, bukan runtime server. Jika mengklaim integrasi end-to-end, sertakan bukti HTTP Android → Laravel → MySQL uji. Catat uji yang tidak dapat dijalankan dan alasannya; jangan mengganti kegagalan dengan label PASS.

## Tahap kerja dan hasil akhir

1. Audit source/baseline dan dokumentasikan keputusan resolver serta definisi agregat. Bekukan kontrak yang perlu berubah.
2. Implementasikan resolver/aturan periode dan read model scoped, berikut test yang bermakna.
3. Implementasikan Beranda semua GH dan satu GH, navigasi, cache, serta filter tujuan.
4. Selesaikan visual/motion/aksesibilitas dengan Impeccable dan antislop DURING; render, tinjau, perbaiki temuan.
5. Jalankan acceptance, regresi relevan dan integrasi sesuai perubahan. Pertahankan bukti per commit/build.
6. Perbarui PRD/arsitektur/kontrak/IMPLEMENTATION_STATUS dan DESIGN/PRODUCT hanya bagian yang memang berubah. Tambahkan `docs/context/MANAGER_HOME_UPGRADE_EVIDENCE.md` berisi matriks MH-01–14, perintah/hasil, baseline/after, sumber angka dan batas validasi. Link dari README context.
7. Ikuti otorisasi Git repo: commit hanya perubahan task setelah checks, branch per fitur dengan dependency tercatat, fetch/sync sebelum push remote github tanpa force. Publikasi bukan deploy atau merge main otomatis.

Selesai berarti kelima upgrade bekerja bersama dengan data nyata melalui jalur produk yang sah, bukan hanya mockup. Jangan mengklaim UAT mitra, pentest, perangkat fisik, deployment atau acceptance produksi selesai jika belum ada bukti. Laporkan hasil, file utama/alasan, tests, dan sisa batas secara singkat dalam bahasa Indonesia. Bila ada blocker, selesaikan pekerjaan independen dan laporkan blocker spesifik tanpa menutupi status parsial.
