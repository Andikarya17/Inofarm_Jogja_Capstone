# Master prompt: menyatukan menu lanjutan dengan dashboard

Arsip arahan implementasi dari baseline `dc97705`. [Audit menu lanjutan](SECONDARY_MENUS_UI_AUDIT.md) mencatat kondisi awal. [Matriks implementasi dan pengujian Android](ANDROID_SECONDARY_MENUS_20261107.md) mencatat hasilnya.

---

Implementasikan rombak frontend menu Antrean, Kegiatan, Panen dan Keuangan beserta seluruh alur turunannya pada Android Kotlin + Jetpack Compose. Gunakan Anti Slop mode DURING dan Impeccable mode Operate. Dashboard botani terbaru yang sudah diimplementasikan menjadi referensi utama. Jangan berhenti pada rencana, penggantian background atau satu layar contoh.

## Keputusan dan prioritas

- Pertahankan login, dashboard pekerja dan dashboard pengelola sebagai baseline visual. Jangan merombaknya lagi. Perubahan shared component hanya jika diperlukan untuk konsistensi dan tidak meregresikan ketiganya.
- Terapkan background botani dashboard pada halaman utama Antrean, Kegiatan, Panen dan Keuangan. Form, detail, review, dialog, sheet, laporan dan layar kerja turunan tetap mempunyai latar solid.
- Keputusan ini mengatasi batas lama dalam DESIGN.md/master prompt sebelumnya yang membatasi background pada login/beranda. Semua requirement bisnis dan otorisasi tetap berlaku.
- Gunakan resource `background_dashboard` dan `BotanicalBackdrop` existing. Jangan mencari gambar lain, menghasilkan aset AI atau mengubah file asli pengguna.
- Kedua peran mendapat Antrean/Kegiatan/Panen sesuai akses existing. Keuangan dan alur privatnya hanya pengelola. Jangan membuat akses baru demi menyamakan tampilan.

## Persiapan dan inventaris

1. Baca AGENTS root/lokal, SOP yang relevan, docs/context/README, PRD, ARCHITECTURE, DATA_CONTRACTS, IMPLEMENTATION_STATUS, kontrak v2, DESIGN.md, PRODUCT.md dan SECONDARY_MENUS_UI_AUDIT.md. Source dan render terbaru menentukan baseline; audit lama bukan bukti source belum berubah.
2. Periksa Git status/diff/staging. Pertahankan pekerjaan lokal dan semua dokumen/aset untracked. Buat branch `codex/` sesuai aturan repo; jangan reset/stash/delete pekerjaan pengguna.
3. Baca skill Anti Slop beserta UI/human/layoutmobile yang relevan dan Impeccable. Gunakan panduan android, operate, audit.native, adapt.native, layout/typeset/clarify/harden. Jalankan context launcher sekali per sesi; bila gagal, baca context langsung. Detector HTML tidak memvalidasi Compose. Baca craft-floor sebelum edit UI.
4. Inspeksi root/navigation, tema, `BotanicalBackdrop`, `FarmChrome`, konteks dan pengukuran dashboard. Buka screenshot dashboard terbaru. Source aplikasi berada di `android/app/src/main/java/com/inofarm/jogja/`.
5. Buat matriks route/screen → peran → entry point → state → komponen → acceptance → evidence. Petakan route nyata, jangan menebak fitur dari nama menu. Ambil screenshot baseline empat menu dan sampel turunannya dari build aktif. Existing screenshot boleh membantu, tetapi labeli historis dan periksa kesesuaiannya dengan commit aktif.

## Scope wajib

| Area | Layar/alur yang harus dicakup |
|---|---|
| Antrean | Ringkasan, kosong, menunggu, diterima bila tersedia, ditolak/konflik/auth/scope/epoch review, alasan, tinjau catatan dan tujuan detail yang dibukanya |
| Kegiatan | Riwayat, filter, detail, catat/edit, pengukuran manual, multi-bahan, kegiatan dilewati, review, audit, koreksi, pembatalan sesuai peran, konflik lokal/server |
| Panen | Riwayat/filter/detail, form multi-grade, tambah/hapus rincian, review, koreksi/batal sesuai peran, saldo/versi yang tersedia dan konflik |
| Keuangan | Hub/jenis riwayat/filter/detail, pengeluaran, alokasi biaya, penjualan, aset, penyusutan, alat keuangan, evaluasi musim, investasi, sumber/rumus, ekspor, lampiran dan recovery yang dicapai dari alur ini |

Scope adalah tampilan dan interaksi frontend existing. Administrasi global yang tidak dicapai dari empat menu tidak perlu diperluas tanpa alasan. Layar turunan yang sudah sesuai cukup dipertahankan dan diverifikasi; tidak wajib diubah hanya agar daftar file edit panjang.

## Sistem visual

Design Read: aplikasi kerja greenhouse dengan bingkai botani, konteks forest, surface krem solid, aksen sage dan interaksi native Android. Menu ENERGY 2 / RHYTHM 2 / MOTION 1; form/detail ENERGY 1 / RHYTHM 2 / MOTION 1.

Reuse semantic tokens aktual dalam InofarmTheme, bukan skema warna paralel. Jadikan header menu forest yang ringkas, radius/border keluarga dashboard, aksi utama hijau/sage sesuai surface, heading/body/metadata dengan peran jelas. Tidak semua panel harus forest: daftar, angka dan isian tetap solid. Gunakan satu focal point per layar, spacing berjenjang dan ikon yang punya makna.

Implementasikan pola bersama untuk root menu, header, toolbar filter, baris/kartu catatan, status dan empty/error. Hindari empat versi hampir sama yang akan menyimpang. Pertahankan font sp, touch minimum 48 dp dan radius/token aktual. Kontras dihitung ulang untuk semua pasangan baru; jangan menganggap lulus karena warna pernah diuji pada dashboard.

Background ditempatkan sekali pada viewport halaman utama, bukan pada tiap item atau sepanjang tinggi LazyColumn. Pertahankan crop, dark overlay dan fallback landscape/tablet existing yang masuk akal. Semua judul, status, error, angka dan toolbar di atas ornamen harus punya bidang stabil. Jangan hanya menghapus guard `tab == 0`: bedakan root menu dari form/detail/review/alat aktif agar wallpaper tidak bocor ke layar kerja turunan. Gunakan state/destination nyata, bukan deteksi judul teks.

Navigasi mengikuti dashboard pada ukuran normal dan pola aksesibel existing pada font besar. Tidak wajib memperluas transparansi navbar; material opak yang konsisten lebih baik daripada teks tertutup. Akhir daftar harus dapat digulir bebas di atas navbar.

## Komposisi per menu

### Antrean

Header ringkas `Antrean`, penjelasan singkat lokal versus server dan ringkasan jumlah yang benar-benar berasal dari state. Jangan menampilkan nol sebagai tanda semua aman saat loading/gagal. Sesudahnya daftar operasi solid: jenis catatan, status yang mudah dipindai, konteks/waktu jika tersedia, alasan dan tindakan nyata. Prioritaskan item perlu tindakan secara visual tanpa mengubah urutan pengiriman engine.

ID operasi dan jumlah percobaan tetap dapat diakses sebagai metadata sekunder. Jangan menyembunyikan alasan penting dalam truncation tanpa cara membuka. Jangan menambah tombol retry/batal jika tidak ada handler yang sah. `Tinjau catatan` harus menuju entitas yang benar dan Back kembali ke konteks semula. Sesi kedaluwarsa, scope dicabut dan restore epoch memiliki tindakan/pesan berbeda. Tidak mengubah accepted menjadi klaim kesesuaian agronomi.

### Kegiatan

Header forest `Kegiatan` dengan konteks yang benar-benar berlaku. Jika riwayat lintas konteks, tampilkan cakupan filter sebenarnya; jangan mengambil GH dashboard seolah otomatis memfilter daftar. Toolbar ringkas menyatukan filter dan ringkasan filter aktif. Satu heading utama, tanpa judul berulang berukuran sama.

Daftar solid berbatas lembut mengikuti dashboard. Judul kegiatan, catatan singkat, waktu/scope dan status punya tingkat penekanan berbeda. Gunakan row dalam kelompok panel atau kartu per catatan sesuai kepadatan, bukan kartu besar untuk setiap label. Aksi pencatatan memakai handler existing jika tersedia; jangan menambah jalur yang melewati pemilihan scope atau validasi.

Form/detail tetap solid dan menggunakan FarmSection/FarmPanel yang sudah cocok. Susun waktu/konteks, bahan atau hasil ukur, catatan, alasan, lalu review. Pertahankan raw input, unit EC/PPM/pH, multi-bahan, kegiatan dilewati dan keadaan target belum tersedia. Review menegaskan apa yang akan disimpan lokal/diantrekan. Konflik memakai perbandingan lokal/server yang terbaca, bukan satu badge merah.

### Panen

Gunakan shell riwayat dan filter yang sama dengan Kegiatan. Identitas isi datang dari tanggal, kelompok, rincian grade, jumlah buah dan kg. Jangan menambah total yang mencampur scope/halaman pagination atau menampilkan perkiraan pendapatan sebagai hasil panen.

Form multi-grade memakai kelompok rincian yang jelas, label grade, jumlah dan bobot dengan unit; tombol tambah/hapus dapat dijangkau. Review menampilkan tiap grade dengan pasangan label/nilai yang mudah dibandingkan. Saldo dan versi mengikuti data existing; belum tersedia tidak menjadi nol. Koreksi, pembatalan, referensi penjualan dan seluruh alasan tetap utuh. Uji banyak grade, nama panjang, keyboard dan kembali ke draf.

### Keuangan pengelola

Header forest `Keuangan` dengan scope catatan baru yang jelas. Di bawahnya aksi Catat pengeluaran dan akses Alat keuangan yang nyata. Satukan pemilih jenis riwayat dan filter dalam toolbar ringkas, lalu daftar transaksi. Jangan biarkan beberapa judul besar/menu menghabiskan viewport sebelum catatan.

Bedakan scope catatan baru dan scope filter riwayat jika state keduanya independen. Jangan menjanjikan header memfilter data yang tidak difilternya. Nominal dan jenis transaksi dominan; waktu, sumber dan status sekunder. Bedakan pembayaran, alokasi, penjualan, aset dan penyusutan; tidak mengubah fixed decimal atau rumus.

Sheet alat mengelompokkan transaksi/aset serta evaluasi/pemulihan dengan label/penjelasan singkat bila perlu. Semua tindakan punya tujuan nyata. Uji sheet terbuka: nama screenshot yang mengandung tools bukan bukti isinya tampil.

Form/review pengeluaran yang sudah baik dipertahankan. Terapkan hierarki konsisten ke alokasi, penjualan, aset, penyusutan dan lampiran. Laporan/investasi: hasil, periode, basis, status, lalu sumber/rumus/alasan yang dapat dibuka. Tidak menambah grafik, KPI atau asumsi fiktif. Snapshot lama immutable, actual/projection jelas, ekspor mengikuti laporan yang dilihat. Recovery hanya memperbaiki presentation alur existing, tidak menambah eksekusi restore produksi.

## State, aksesibilitas dan perlindungan fungsi

Setiap data view mencakup loading, empty, filtered-empty, error/retry, offline/last-known dan state penerimaan yang relevan. Empty memberi langkah berikutnya yang sah; jangan mengisi layar dengan ilustrasi/angka palsu. Warning/error tetap terlihat, bukan hanya warna. Mengurangi kepadatan tidak boleh menghapus alasan, sumber atau unit.

Pertahankan durable draft per akun, atomic enqueue, ID operasi, version conflict, tombstone, restore epoch, session/role, Back/IME/process recreation dan ownership ViewModel. Tidak mengubah backend/API/schema/rumus untuk desain. Jangan clear app data, reset database pengguna atau melakukan deployment. Jika backend test diperlukan, guard effective MySQL inofarm_test berlaku.

Jaga semantics/test tags atau migrasikan tes bermakna. Font besar harus reflow, bukan dipaksa mengecil. Tombol dan field bisa dijangkau ketika IME terbuka; dialog/sheet bisa ditutup dengan Back; fokus kembali logis. Hormati animasi sistem dan hindari gerak dekoratif background.

## Urutan implementasi dan penerimaan

1. Inventaris dan capture baseline; catat apa yang sudah sesuai. Buat acceptance per layar sebelum edit.
2. Kerjakan shell menu bersama dan satu alur Kegiatan lengkap sampai form/review/detail. Review screenshot terhadap dashboard, lalu terapkan ke Antrean dan Panen.
3. Kerjakan Keuangan dan seluruh turunannya dalam matriks. Jangan menyatakan selesai hanya karena empat halaman utama sudah memakai wallpaper.
4. Build/lint/test sesuai workflow aktual; regresi meaningful navigasi, filter, draf-review-antre, konflik dan batas peran. Uji recovery/export/attachment hanya dalam lingkungan aman existing, tanpa destructive production action.
5. Capture before/after empat menu, setiap keluarga form/detail/review, filter dan tools sheet terbuka, konflik serta sampel laporan. Antrean harus punya sampel berisi dan perlu tindakan; Panen harus punya sampel multi-grade. Fixture hanya di test/preview, bukan produksi.
6. Uji light/dark, lebar 320/360/sekitar 412 dp, font scale 1/1,3/2, landscape/IME dan fallback lebar besar. Catat device/API/commit; pulihkan pengaturan. Uji TalkBack/fokus, sentuhan, kontras, scroll dan Back. Jangan mengklaim performa dari screenshot.
7. Capture ulang login dan dashboard kedua peran sebagai regresi shared components. Pastikan pasangan EC/PPM, freshness/WIB dan state tidak berubah.
8. Jalankan Anti Slop Delivery Gate dengan bukti PASS/PARTIAL/N/A. Update DESIGN.md agar batas background sesuai keputusan terbaru, context, matriks status dan evidence. Report file/alasan, hasil tes, screenshot, coverage yang belum diuji dan status Git. Ikuti workflow commit/push yang berlaku tanpa merge/deploy otomatis.

Acceptance visual: empat menu tampak sebagai satu keluarga dashboard melalui header forest, surface krem, radius/border, tipografi, toolbar dan navigasi; wallpaper bukan satu-satunya perubahan. Form/detail solid tetap punya identitas melalui token, heading dan CTA. Semua informasi bisnis tetap benar dan dapat diakses. Setiap item matriks harus ditandai diubah atau dipertahankan dengan bukti, bukan diam-diam dilewati.

Mulai dari inspeksi lalu implementasikan secara mandiri. Scope seluruh alur turunan dan background pada empat root menu sudah disetujui. Tanyakan hanya konflik yang tidak terjawab context; jangan meminta persetujuan ulang atas arah ini.
