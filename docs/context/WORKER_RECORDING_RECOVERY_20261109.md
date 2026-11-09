# Pemulihan pencatatan pekerja, 9 November 2026

## Diagnosis

Fetch GitHub menghasilkan main `419a144`. Branch fitur berangkat dari `bc98146`, yang menambah master prompt dan audit di atas main tersebut. Kegiatan, panen, koreksi, dan administrasi sudah terintegrasi. Ancestry terhadap `ff5869f`, `89c5026`, `bb3d864` dan `codex/finance-admin-entry` menunjukkan nol commit tertinggal. Tidak diperlukan merge modul lama.

APK awal `com.inofarm.jogja.finance` 0.2.0 (2) menampilkan penugasan kosong setelah sinkron selesai. Pembacaan SQL dalam transaksi read-only pada database lokal yang ditunjuk pengguna menemukan dua akun aktif dengan role masing-masing dan **nol baris penugasan** sebelum uji UI. Ini menjelaskan kondisi awal akun tersebut. Perbaikan juga menutup kasus terpisah yang direproduksi dengan MySQL uji: pekerja sinkron sebelum mendapat penugasan, sehingga cursor berada di depan perubahan master yang semula di luar cakupan. Saat cakupan berubah, `ChangeFeedController` memulai ulang pull dalam cakupan baru. Android tetap menerapkan versi, scope, dan tombstone per akun.

Kegiatan dan Panen mempertahankan tujuan saat pemilih konteks dibuka. Pemilih memakai greenhouse aktif, musim terbuka, dan kelompok aktif. Pekerja tidak bergantung pada penetapan periode berjalan pengelola. Pesan membedakan pemeriksaan berlangsung, belum ditugaskan, greenhouse nonaktif, akses berubah, server gagal, dan penggunaan cache. Admin mencari greenhouse dari daftar yang dimuat dan memeriksa kesiapan pencatatan di detail. Penugasan memilih akun yang sudah dalam kewenangan pengelola atau mencari pekerja aktif tanpa penugasan dengan email lengkap. Email akun dapat diubah dengan UUID tetap, validasi unik, versi, audit, receipt, dan pencabutan token. Konfirmasi password hanya disimpan dalam memori form.

## Pemetaan sumber

Empat berkas asli dibaca secara lokal. Proposal keuangan halaman 11–12, 16–18, 20–21 dan 39–40 menetapkan alur pencatatan serta dua peran. DOCX proposal mendukung pemisahan draf, penerimaan, panen, dan penjualan. Makalah analisis usahatani memberi konteks budidaya dan perhitungan biaya serta penerimaan. Angkanya tidak dijadikan nilai awal form.

| Sumber workbook GH Krebet | Pemetaan |
|---|---|
| Sheet1 B5:H5 | Waktu aktual WIB, HSS/HST dari tanggal kelompok, PPM/pH, stadia aktual, bahan, jumlah dan satuan. |
| Sheet1 J4:M4 dan L9 | Penyemprotan menyimpan beberapa bahan pada satu kegiatan dengan jumlah dan satuan masing-masing. |
| Jadwal general C25, C31, C59 | EC mS/cm, PPM ppm dan pH manual. Keterangan pekerjaan menerima DO (mg/L), suhu air (°C), tinggi dan lebar daun (cm), jumlah daun (helai). Empat ukuran terakhir dan DO disimpan sebagai teks. |
| Jadwal general A2:F67 | Pekerjaan spontan atau realisasi jadwal, termasuk polinasi, seleksi buah, dan pascapanen. Kegiatan dilewati membutuhkan alasan. |
| Jadwal general F36–F41 dan Sheet1 H80/H90 | Observasi grade, cacat dan tanaman tidak berbuah dapat dicatat sebagai pengamatan. Panen aktual memakai grade, jumlah buah, kg dan keterangan. |

Perbedaan umur/tanggal pada baris 20–21 dan rentang pH di E90 tetap memerlukan peninjauan agronomi. Data jadwal 2025 tidak diimpor dan tidak dipakai sebagai hasil aktual.

## Pengujian

Backend dijalankan dengan guard Laravel sebelum SQL pada effective MySQL `inofarm_test`. `WorkerRecordingRecoveryTest` memeriksa pertumbuhan dan pencabutan cakupan pada cursor yang sudah ada, kandidat penugasan, replay, penugasan masa depan, status greenhouse dan histori. Pengujian identitas mencakup email format salah/duplikat, email baru dan lama saat login, konflik versi, reset password, demosi, token lama, serta ketiadaan plaintext dalam receipt, audit dan feed. `FinanceIntegrationFlowTest`, `FinanceMasterTest`, `FinanceActivitySyncTest`, `FinanceMeasurementTest` dan `FinanceHarvestTest` menguji jalur operasional dan negatif dengan MySQL.

Suite backend lulus 137 tes dengan 2.653 assertion. Android lulus 73 tes JVM, build APK dan lint dengan 0 error. Emulator lulus 33 tes instrumentasi, terdiri dari 29 tes storage/sync, tiga tes pemilih dan admin, serta satu tes tab Kegiatan dengan HTTP sintetis. Pemeriksaan kontrak dan integritas arsip staged lulus.

APK uji `com.inofarm.jogja.finance.performance` 0.2.0 (2) dipasang terpisah pada `emulator-5554`. Uji UI akun existing diotorisasi pengguna dan mengakses Laravel lokal melalui `http://10.0.2.2:8000/api/v2/`. Pengguna mengisi password di emulator. Data dummy dimasukkan melalui form aplikasi dan diberi label UJI. Tidak ada reset database pengguna, seed tersembunyi, reset password akun existing, atau clear-data. Screenshot dan log lokal ada di `outputs/worker-recording-recovery/`.

Pengelola membuat dan membaca detail `UJI Pemulihan WR 09 Nov`, menonaktifkan, lalu mengaktifkan kembali. MySQL mencatat versi 3, tiga receipt dan tiga aksi audit. Pengelola membuat musim, varietas, kelompok, dua grade dan satu penugasan efektif bagi pekerja existing. Pekerja masuk, memilih konteks dari Beranda, mengisi pengamatan tanaman, dan melihat status Diterima server di Kegiatan. Setelah sesi dibuka kembali, tombol tambah di tab Panen membuka pemilih dan meneruskan ke formulir yang benar.

Draf panen dua grade muncul sebagai Disimpan lokal sebelum proses APK uji dihentikan. Pekerja membuka lagi draf dengan nilai utuh, meninjau, lalu mengirim dari viewport 320 dp dengan font 160%. Setelah panen diterima, pekerja mengoreksi bobot Grade B dari 2,75 kg menjadi 2,80 kg. Rekonsiliasi read-only MySQL menemukan satu kegiatan versi 1 dengan satu receipt dan satu audit, serta satu panen versi 2 dengan dua receipt dan dua audit. Grade A tercatat 4 buah dan 6,25 kg, Grade B 2 buah dan 2,80 kg. Fixture ini tetap tersedia di database lokal agar bisa dilihat ulang.

Setelah pengelola masuk kembali, Beranda menampilkan dua catatan atas nama pekerja yang ditugaskan. Detail kegiatan menampilkan catatan pengamatan versi 1 dan detail panen menampilkan bobot akhir serta versi 2. Keduanya berstatus Diterima server. Pekerja menjalani alur ini tanpa pengelola menunjuk periode berjalan pada Beranda.

## Matriks penerimaan

| ID | Hasil dan bukti |
|---|---|
| WR-01 | UI nyata membuat greenhouse, musim, varietas, kelompok dan dua grade. `FinanceActivitySyncTest` dan `FinanceMasterTest` melengkapi satuan dan bahan pada MySQL uji. |
| WR-02 | `FinanceIntegrationFlowTest` membuat akun melalui API, login sebagai pekerja dan mencatat dalam cakupan awal. Uji UI nyata memakai akun existing sesuai permintaan pengguna. |
| WR-03 | Lookup pekerja existing tanpa penugasan dan penugasan pertama lulus di UI nyata. `WorkerRecordingRecoveryTest` membuktikan picker kandidat GH tambahan, duplikasi, akses silang dan assignment masa depan. |
| WR-04 | Aksi dari Beranda dan tab Panen lulus di UI nyata dengan konteks awal kosong. `FinanceEntryUiTest.activityMenuEntryKeepsSkippedReasonAndReturnsToHistory` lulus dari tab Kegiatan hingga riwayat dengan HTTP sintetis. |
| WR-05 | Pengamatan umum dengan ukuran/helai masuk MySQL lewat perangkat. Nutrisi, multibahan, stadia, EC+PPM dan dilewati dicakup test aktivitas, referensi dan pengukuran. DO, suhu air, dan ukuran tanaman menggunakan teks bersatuan. |
| WR-06 | Panen multigrade, review, draf lokal, proses ulang, accepted, riwayat, dan koreksi sendiri lulus di UI nyata. `FinanceHarvestTest` membuktikan batas saldo terjual dan perlindungan histori. |
| WR-07 | APK pekerja menampilkan empat tab tanpa Keuangan atau Administrasi. Penolakan akses finance, greenhouse asing, catatan orang lain, dan cancel worker diverifikasi di MySQL uji. |
| WR-08 | Edit email diuji di MySQL dan JVM. UUID tetap, email lama ditolak, duplikat dan format salah ditolak. Kunci draf tetap berdasarkan server dan UUID akun. |
| WR-09 | MySQL menguji reset password, demosi, invalidasi token, guard akun sendiri, dan eksklusi plaintext. Konfirmasi password dan opsi reset tersedia di form. |
| WR-10 | Aktivasi ulang greenhouse lulus di UI nyata. MySQL menguji mutasi versi, parent nonaktif, musim ditutup, pencabutan akses, dan retensi histori. |
| WR-11 | Proses APK nyata dihentikan dan draf panen bisa dibuka tanpa duplikasi. Back dari IME, perubahan viewport, dan simpan dari ukuran kecil diuji. Respons hilang, retry identik, konflik dan batas antar-akun lulus di instrumentasi storage/sync. Matriks rotasi dan seluruh form masih parsial. |
| WR-12 | Kondisi kosong terverifikasi dari SQL dan baseline UI. Pull sesudah perubahan cakupan diuji di MySQL. `WorkerContextUiTest` membedakan loading, kosong sah, offline dan API gagal dengan state sintetis. |
| WR-13 | Ada screenshot pekerja sebelum/sesudah, admin sesudah, viewport 393 dp dan 320 dp dengan font 160%. Karena mode sistem emulator API 29 terkunci, palet gelap diuji dengan tema Compose pada fixture pemilih dan editor admin di viewport 320 dp. Pembacaan TalkBack menyeluruh, screenshot admin sebelum perubahan dalam sesi yang sama, perangkat fisik, dan perbandingan semua varian tetap terbuka. |

## Pemeriksaan desain dan aksesibilitas

Impeccable Operate memakai pola pemilih dan dialog Material yang sudah ada. Dials form 1/2/2 mengikuti DESIGN.md. Aksen sage menandai tindakan utama, panel forest menandai konteks pekerja, dan ruang antarbagian memisahkan parent, isian dan tindakan. UI memakai label serta kondisi tekstual. Tidak ada aset baru atau motion tambahan.

- **R-03, R-34, C-4.** Pada viewport yang diperiksa, nama greenhouse dan pesan dapat membungkus. Tombol simpan panen dapat dijangkau dan ditekan pada 320 dp dengan font 160%. Tema forest tampil pada pemilih dan editor admin sintetis.
- **R-25.** Lima pasangan token teks dan permukaan yang diperiksa memiliki kontras 6,04:1 sampai 13,46:1.
- **R-24, R-26, R-27, R-35, C-2, C-5.** Jalur tambah, pemilih, review, antrekan, detail dan koreksi diklik. Compose menguji empty, loading, offline dan error. Status penerimaan catatan UJI berasal dari riwayat dan rekonsiliasi MySQL.
- **R-32, R-39.** Kontrol memakai komponen Material dengan label, fokus dan target sentuh 48 dp pada aksi baru. Pemeriksaan TalkBack menyeluruh dan lintasan keyboard eksternal belum dilakukan.
