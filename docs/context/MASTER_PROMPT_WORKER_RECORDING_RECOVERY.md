# Master prompt — pulihkan pencatatan pekerja dan lengkapi administrasi

Gunakan seluruh dokumen ini sebagai instruksi pelaksanaan. Jangan berhenti pada rencana: diagnosis, implementasi, pengujian, evidence, context update, commit dan publikasi branch sesuai AGENTS. Jangan mengklaim selesai jika baru membuat tombol atau hanya lulus mock.

## Tujuan pengguna

Pekerja harus dapat memilih greenhouse penugasannya, mencatat kegiatan harian sesuai workbook GH Krebet, menambah panen, membaca riwayat dan mengoreksi catatan sendiri. Pengelola harus dapat mengelola greenhouse dan akun (nama, email, password, peran pekerja/pengelola), lalu menugaskan pekerja agar greenhouse tersebut benar-benar dapat dipakai mencatat. Prioritaskan alur utuh ini.

## Dasar yang wajib dibaca

1. AGENTS root/lokal, SOP yang relevan, `docs/context/README.md`, PRD, ARCHITECTURE, DATA_CONTRACTS, IMPLEMENTATION_PLAN dan GAP_ANALYSIS; keputusan terbaru mengatasi histori.
2. `WORKER_RECORDING_AUDIT_20261109.md` di folder ini. Baseline audit source `d359502`, main setelah fetch `419a144` (tree sama). Periksa ulang HEAD/main/status sebelum bekerja; jangan mengasumsikan baseline belum berubah.
3. Kontrak executable `contracts/v2/`, terutama kegiatan, pengukuran, HARVEST, MASTERS, auth, ledger/sync; migration finance DECISIONS, API_V2, TEST_PLAN dan AUDIT_COMPARISON pada bagian terkait.
4. Baca empat berkas asli dalam `D:/Semester 7/Capstone 2/Proposal` yang disebut audit. Proposal keuangan PDF adalah baseline utama; spreadsheet memberi rincian pekerjaan. Jangan mempublikasikan salinan/ekstraksi berkas pribadi.
5. Source/test nyata serta PRODUCT.md dan DESIGN.md. Jangan memakai evidence lama sebagai hasil pengujian task ini.

## Skill dan arah UI wajib

- **Antislop mode DURING** sudah dipilih pengguna untuk pekerjaan ini. Baca skill core, UI, human/accessibility, layoutmobile dan copywriting sesuai scope; terapkan selama pengerjaan. Tidak perlu bertanya ulang mode.
- **Impeccable** wajib: baca `.agents/skills/impeccable/SKILL.md`, jalankan context sekali, ikuti reference native Android serta Operate/harden yang relevan; baca craft-floor sebelum edit UI. Jangan memakai aturan web untuk mengklaim aksesibilitas Compose.
- Ini pemulihan fungsi dalam identitas botani forest/sage/krem existing. Pertahankan komponen native, typography, tema terang/gelap dan navigasi pengelola yang sudah matang. Tidak merombak aplikasi atau menambah ilustrasi tanpa kebutuhan.
- Aksi utama jelas: `Catat kegiatan`, `Catat panen`, `Tambah greenhouse`, `Tambah akun`, `Atur penugasan`. Jangan menyembunyikan tujuan utama hanya karena belum ada konteks; aksi dapat membuka pemilih/keterangan syarat yang sesuai.
- Tampilkan alasan spesifik dan langkah berikutnya untuk belum memilih greenhouse, belum ditugaskan, musim/kelompok belum ada, musim ditutup, master bahan/grade belum ada, akses dicabut, offline dan gagal memuat. Jangan melabeli kegagalan request sebagai daftar kosong yang sukses.
- Jaga target sentuh 48 dp, TalkBack, urutan fokus, IME/Back, font besar, lanskap, dark mode dan reduced motion. Gerak hanya membantu perpindahan/pesan simpan; tidak menghalangi input.

## Tahap 1 — diagnosis dan keputusan merge

1. Periksa status/staging; pertahankan file pengguna. Fetch `github`. Buat branch fitur deskriptif `codex/` dari source terbaru yang memuat frontend pengelola; jangan reset/stash/delete kerja pengguna. Pisahkan fitur berbeda dalam branch/commit yang jelas.
2. Bandingkan branch kegiatan, panen, history, administrasi dan frontend. Audit menemukan `ff5869f`, `89c5026`, `bb3d864` sudah terintegrasi. Ulangi ancestry/diff; merge/cherry-pick hanya bila ada patch relevan yang belum masuk, dan uji kompatibilitas. Jangan merge jalur dummy/IoT/owner lama secara penuh.
3. Reproduksi login pekerja pada endpoint/build yang benar, dengan akun uji berizin atau sesi yang diberikan. Catat versi APK, role, endpoint tanpa secret, assignment efektif, greenhouse aktif, musim terbuka dan kelompok aktif. Jika kredensial belum tersedia, lakukan pengujian terisolasi dulu dan jelaskan batasnya; jangan menebak password atau mengubah akun pengguna.
4. Telusuri auth→scope API→full pull/tombstone→cache per akun→ContextViewModel→HomeContextCard→FinanceRoot. Snapshot audit menunjukkan greenhouse kosong, tetapi belum diketahui apakah server atau cache penyebabnya. Bandingkan respons autentikasi, assignment dan cache secara aman. Jangan bypass scope untuk membuat daftar terisi.
5. Buktikan kasus akun sah dengan penugasan dan kasus tanpa penugasan. Perbaiki akar masalah yang benar-benar ditemukan. Bila hanya konfigurasi data yang kosong, laporkan demikian; sediakan alur admin yang menyelesaikannya, bukan seed produksi diam-diam.

## Tahap 2 — pengelola: greenhouse, akun dan penugasan

Reuse `feature/admin/`, `MasterService`, `UserController`, ledger dan kontrak existing. Backend dan Android sudah memiliki fondasi administrasi; jangan membuat sistem paralel.

### Greenhouse

- Daftar, cari/pilih, lihat detail, tambah, ubah nama/status dan nonaktifkan/aktifkan kembali dalam cakupan pengelola. Detail menyajikan penugasan dan kesiapan pencatatan: musim terbuka, kelompok aktif, bahan/satuan, grade.
- Pembuatan greenhouse harus tetap memberi pengelola pembuat akses yang sah secara atomik (existing MasterService membuat assignment pembuat). Pastikan greenhouse baru muncul sesudah refresh dan login ulang.
- Setelah membuat greenhouse, berikan langkah menyiapkan musim/kelompok dan penugasan pekerja. Musim dan kelompok tetap parent wajib; jangan menebaknya dari nama greenhouse atau menghapus relasi agar form lebih singkat.
- Nonaktifkan adalah padanan hapus master yang sudah dirujuk. Dialog menyebut dampak pada input baru, penugasan, draf dan histori. Histori/audit/panen/keuangan tidak dihapus berantai. Jangan menawarkan hard delete database.

### Akun, email, password, peran

- Daftar/detail/tambah/ubah/nonaktifkan/aktifkan akun dalam kewenangan pengelola; nama, email unik, role `worker`/`manager`, status aktif dan assignment jelas.
- **Implementasikan edit email end-to-end**: pada baseline email hanya tersedia saat create di `AdminForm.fields` dan `UserController`. Tambahkan validasi format, normalisasi konsisten dengan auth existing, keunikan saat edit (kecualikan akun sendiri), konflik versi, ledger/replay dan audit aman; login berikutnya memakai email baru. Email lama tidak tetap menjadi alias tanpa kontrak eksplisit.
- Password baru wajib pada create; pada edit kosong berarti tidak mengubah password. Sediakan ubah/reset password, konfirmasi isian dan feedback. Tidak ada fitur membaca password lama. Pertahankan hashing backend, password hanya di memori UI dan retry aman; jangan simpan plaintext ke draf/outbox/log/audit/receipt/screenshot.
- Perubahan role/email/password/nonaktif harus menegakkan invalidasi sesi sesuai kontrak. Sesi lama tak boleh mempertahankan kapabilitas pengelola setelah downgrade. Draf akun tetap disimpan dan tidak dipindahkan berdasarkan email; account ID tetap stabil.
- Cegah pengelola menonaktifkan/demote dirinya secara tidak sengaja sebagaimana guard existing; uji bahwa pengelola terakhir yang diperlukan untuk administrasi tidak terkunci oleh alur baru. Jangan memperluas scope seluruh akun hanya demi memudahkan picker.
- Semua operasi ini ditolak untuk pekerja pada API langsung, bukan hanya disembunyikan dari menu.

### Penugasan dan alur lengkap

- Kelola pekerja↔greenhouse, status aktif dan masa berlaku; pekerja bisa mempunyai beberapa greenhouse. Create akun existing sudah membuat assignment awal; tampilkan hasilnya, hindari assignment duplikat pada retry.
- Untuk akun existing, alur penugasan harus dapat memilih akun yang memang boleh dikelola. Audit potensi kebuntuan picker yang hanya menampilkan akun greenhouse tujuan. Solusi tetap server-scoped; jangan membuka daftar pengguna global kepada pekerja/pengelola tanpa kewenangan.
- Setelah penugasan diterima, login/sync pekerja menampilkan greenhouse yang sah beserta musim/kelompok yang cocok. Assignment masa depan/kedaluwarsa/dicabut harus ditangani berbeda dari gagal jaringan.
- Pengelola menyediakan master bahan/satuan/grade lewat jalur existing agar pekerja tidak buntu pada form nutrisi atau panen.

## Tahap 3 — pencatatan harian dan panen pekerja

### Konteks dan navigasi

- Greenhouse mudah dipilih dari Beranda dan alur tambah pada tab Kegiatan/Panen. Bila belum lengkap, lanjutkan pemilihan musim/kelompok, lalu kembali ke tujuan tambah semula; jangan membuat pengguna berputar tanpa petunjuk.
- Konteks terakhir boleh dipulihkan per account ID jika masih sah; jangan bocorkan pilihan/cache antar-akun. Pergantian greenhouse membatalkan pilihan parent turunan yang tidak cocok, tetapi tidak memindahkan draf existing ke parent baru.
- Pekerja tidak harus mengatur `running_period_version` milik alur pengelola agar bisa mencatat dengan parent sah. Ikuti kontrak existing; jangan menyamakan penetapan periode berjalan dengan membuka musim.

### Mapping workbook ke input aktual

| Sumber | Input dan perilaku |
|---|---|
| Sheet1 B5:H5 | Tanggal/waktu WIB, HSS/HST derived dari tanggal semai/pindah, PPM, pH, stadia aktual, bahan/jumlah/satuan termasuk L, keterangan |
| Sheet1 J4:M4 dan contoh L9 | Penyemprotan multi-bahan, jumlah/dosis dan satuan per bahan, waktu pelaksanaan; jangan memecah satu kegiatan menjadi transaksi kas |
| Jadwal general C25 | Pengukuran EC mS/cm dan PPM ppm sesuai keputusan terbaru, pH; DO/suhu air ada dalam sumber, belum merupakan field terstruktur existing |
| Jadwal general C31, C59 | Pengamatan tinggi/jumlah/lebar daun; minimal dapat dicatat dengan label/satuan eksplisit dalam pekerjaan umum tanpa membuang informasinya |
| Jadwal general A2:F67 | Semai, penyiraman, kebersihan, tali, pinching, polinasi, seleksi buah, hama/penyakit, fertigasi, pengamatan, panen/pascapanen sebagai jadwal atau pekerjaan spontan |
| Jadwal general F36–F41; Sheet1 H80/H90 | Pengamatan grade/cacat/tidak berbuah terpisah dari hasil panen aktual; panen tanggal/kelompok/grade/jumlah buah/kg/keterangan, lalu pekerjaan pascapanen |

- Tipe existing: measurement, nutrition, spray, stage, general. Gunakan label dan bantuan yang memudahkan pekerja menemukan pekerjaan workbook. Jadwal/reference berasal dari master yang disiapkan pengelola; jangan mengimpor tanggal 2025 sebagai pekerjaan selesai atau menanam semua jadwal historis sebagai data produksi.
- Untuk DO, suhu air, serta ukuran tanaman yang belum terstruktur, jelaskan mapping dan batasnya. Sediakan catatan aktual dengan label dan satuan, atau perluas kontrak secara additive jika memang dibutuhkan; jangan mengklaim field terstruktur sudah ada dan jangan membangun IoT.
- Target/rentang rekomendasi bukan hasil aktual. Jangan isi PPM/pH/dosis/grade/jumlah/bobot dari fixture atau jadwal. Catatan kosong bukan nol, HST sebelum pindah bukan nol, stadia bukan hasil otomatis dari umur. Perbedaan tanggal/HST dan nilai sumber yang janggal tetap ditandai untuk review.
- Nutrisi menyimpan bahan/jumlah/satuan dan pengukuran sesuai validasi; pengukuran EC+PPM tersendiri tidak membutuhkan bahan. Pekerjaan umum tidak memaksa pengukuran yang tidak relevan. Kegiatan dilewati perlu alasan; satu realisasi tidak otomatis menyelesaikan seluruh jadwal.
- Panen memungkinkan beberapa grade, jumlah buah dan kg valid; grade berasal dari konteks yang sah. Panen bukan penjualan. Koreksi tidak boleh mengurangi saldo di bawah bobot yang sudah dipakai penjualan.

### CRUD, draf, audit dan offline

- Create→review→simpan lokal/antrekan→accepted server→riwayat/detail→koreksi catatan sendiri dengan alasan harus berfungsi untuk kegiatan dan panen.
- Nyatakan makna Delete: catatan accepted dibatalkan dengan alasan/audit oleh pengelola menurut PRD existing; pekerja tidak mendapat hard delete atau cancel accepted otomatis. Jika pengguna menghendaki pekerja membatalkan accepted sendiri, konfirmasi perubahan kebijakan itu secara spesifik sambil melanjutkan pekerjaan independen.
- Pembuangan draf lokal bila tersedia harus aman terhadap pending/in-flight operation. Jangan menghapus outbox yang mungkin sudah diterima server.
- Pertahankan durable draft/outbox per akun, local record+operation atomik, receipt/audit+mutation atomik, UUID stabil, retry idempotent, base_version/conflict, tombstone dan restore epoch. Logout, Back, IME, rotasi dan process death tidak menghilangkan input.

## Acceptance dan bukti wajib

| ID | Skenario dan hasil yang harus dibuktikan |
|---|---|
| WR-01 | Pengelola membuat greenhouse, musim, kelompok dan master yang diperlukan; semuanya dapat dipilih sesuai scope |
| WR-02 | Pengelola membuat akun pekerja dengan email/password/role, menugaskan GH; login pekerja menampilkan GH itu |
| WR-03 | Akun existing ditugaskan ke GH tambahan; tidak ada picker buntu/duplikasi; pekerja hanya melihat penugasan efektif |
| WR-04 | Tambah kegiatan dari Beranda dan tab Kegiatan, termasuk konteks awal kosong; selesai memilih kembali ke form yang dimaksud |
| WR-05 | Nutrisi, spray multi-bahan, stadia, pekerjaan umum workbook, pengukuran EC+PPM dan dilewati tervalidasi dan tampil kembali |
| WR-06 | Panen multigrade→review→lokal→server→riwayat→koreksi sendiri; saldo panen terjual tetap dilindungi |
| WR-07 | Worker tidak dapat ubah catatan orang lain/cancel accepted/kelola admin/akses keuangan/GH lain melalui UI maupun API |
| WR-08 | Edit email: email baru login berhasil, lama tidak; duplikat/format salah ditolak, ID akun dan draf tetap sama |
| WR-09 | Reset password dan perubahan role/aktif efektif di server; credential lama/sesi dicabut tidak bisa mutasi; tidak ada plaintext persisten |
| WR-10 | Ubah/nonaktifkan/aktifkan GH/akun/assignment menjaga histori; parent tidak aktif, musim tertutup dan assignment dicabut memberi alasan yang tepat |
| WR-11 | Offline, retry identik, respons hilang, konflik versi, login ulang, Back/IME/rotasi/process death tidak hilangkan/duplikasi catatan |
| WR-12 | Loading, empty sah, error API, cache stale dan tanpa assignment dibedakan; tidak ada klaim sukses saat gagal |
| WR-13 | Screenshot sebelum/sesudah untuk pekerja dan admin pada layar kecil/besar, font besar, tema gelap; tombol simpan tetap terjangkau dan TalkBack jelas |

Gunakan unit/JVM untuk validasi dan state yang relevan, instrumentasi Android untuk navigasi/durabilitas, serta API/backend MySQL untuk otorisasi/ledger/transaksi. Sebelum SQL test, guard effective database **inofarm_test**. Jangan migrate:fresh/reset/seed/clear-data pada inofarm_local atau data pengguna. Gunakan fixture sintetis yang terisolasi dan labeli bukti mock terpisah dari E2E MySQL. Baca konfigurasi runner sebelum menjalankannya pada emulator yang sedang dipakai pengguna.

Uji E2E minimum: login pengelola→buat GH dan parent→buat/atur pekerja→login pekerja→pilih GH→kegiatan dan panen diterima→koreksi→pengelola melihat hasil. Tambahkan jalur negatif role/scope dan edit email/password. Catat test yang belum dapat dijalankan beserta penghalangnya; jangan menandai PASS karena kode terlihat benar.

## Penyelesaian

Perbarui requirement→AC→source/test→evidence, IMPLEMENTATION_STATUS, GAP_ANALYSIS dan README terkait. Dokumentasikan apakah masalah awal adalah penugasan, sync/cache, navigasi, build atau kombinasi; tulis bukti yang mendukung. Commit hanya file task setelah checks, fetch/sync dan push remote `github` sesuai otorisasi repo; PR sesuai workflow tanpa auto-merge/deploy. Laporkan file utama, hasil checks, branch/commit/PR dan batas verifikasi. Jangan menutup gap dengan klaim UAT/pentest/perangkat fisik yang belum dilakukan.
