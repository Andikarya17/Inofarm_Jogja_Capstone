# Master prompt: penyelarasan fitur pengelola Inofarm

Status: rencana implementasi, 9 November 2026. Jalankan hanya ketika pengguna meminta pelaksanaan prompt ini. Dasar audit: [MANAGER_VISION_AUDIT_20261109.md](MANAGER_VISION_AUDIT_20261109.md). Baseline diperiksa pada `8d74cba`; baca HEAD aktual sebelum bekerja karena sebagian gap dapat sudah diselesaikan setelah audit.

## Tujuan dan ruang lingkup

Selesaikan alur pengelola Android sesuai visi pengguna: Beranda per/semua greenhouse, Antrean verifikasi input pekerja, Kegiatan yang juga dapat diisi pengelola, Panen dengan varietas dan target dibanding hasil, Keuangan lengkap dan dapat ditelusuri, serta pengelolaan greenhouse/akun yang mudah ditemukan.

Kerjakan Kotlin + Jetpack Compose, Laravel REST API dan MySQL. Gunakan fondasi existing. Ini perluasan fungsi bisnis terukur dan penyederhanaan UX, bukan sekadar kosmetik atau pembangunan ulang. Tidak ada IoT, sensor otomatis, ESP32, dosing, React sebagai produk, atau konversi data historis menjadi pengamatan manusia.

Pertahankan KF01-14, KNF01-08 dan U01-21. Ganti status acceptance hanya berdasarkan bukti baru. Pekerja bukan target redesign terpisah, tetapi pencatatan bersama, penugasan, sync dan umpan balik verifikasi wajib tetap berfungsi untuk kedua peran.

## Baca sebelum perubahan

1. AGENTS root/lokal, SOP yang relevan, `docs/context/README.md`, `IMPLEMENTATION_STATUS.md`, PRD, ARCHITECTURE, DATA_CONTRACTS, IMPLEMENTATION_PLAN, GAP_ANALYSIS; keputusan terbaru mengatasi checkpoint historis.
2. `docs/migration/finance-20261029/DECISIONS.md`, API_V2, TEST_PLAN dan AUDIT_COMPARISON dengan tanggal/batas historisnya. Kontrak executable `contracts/v2/` dan source/test menentukan fitur yang benar-benar sudah ada.
3. Audit pendamping dan `WORKER_RECORDING_RECOVERY_20261109.md`. Edit email sudah ada pada baseline, jangan membangun implementasi kedua dari audit lama.
4. Baca langsung kedua sumber berikut, bukan hanya ringkasan audit: `D:/Semester 7/Capstone 2/Proposal/Jadwal  MT 3 pemupukan dan stadia tumbuh Melon GH Krebet_093055.xlsx` (kedua sheet) dan `D:/Semester 7/Capstone 2/Proposal/Dwito Mufti Agung_574189_Makalah Analisis Usahatani Meloh Hidroponik.pdf` (halaman PDF 16-22,25). Baca bagian relevan proposal keuangan utama. Bila file hilang, gunakan ekstraksi dengan hash terverifikasi untuk pekerjaan independen dan laporkan keterbatasannya; jangan mengarang isi sumber.
5. `PRODUCT.md`, `DESIGN.md`, skill antislop beserta UI/human/layoutmobile/copywriting yang relevan, dan `.agents/skills/impeccable/SKILL.md`. Antislop **during** sudah dipilih untuk pekerjaan ini. Jalankan impeccable context sekali; gunakan Operate/native Android. Baca craft-floor sebelum edit UI, bukan untuk menganggap rencana sebagai UI selesai.

## Ketentuan kerja dan perlindungan data

- Periksa status, staging, diff, branch dan ancestry. Buat branch `codex/` berbeda untuk tiap fitur, catat dependency commit; jangan memaksakan semua fase pada satu branch. Reuse checkout yang aman, jangan reset/stash/delete pekerjaan pengguna demi switch.
- Jangan stage `UI inspo/`, `assets/` untracked, desain 26 Oktober, caches, outputs pengujian lokal, env, secrets, log atau build tanpa alasan dan provenance yang sudah disetujui. Tidak menghapusnya.
- Commit hanya file task setelah checks. Otorisasi Git mengikuti AGENTS dan instruksi terbaru. Sebelum push fetch/sync `github`, cek divergensi tanpa force-push, verifikasi remote dan ikuti PR/CI aktual. Jangan auto-merge atau deploy.
- Migrasi additive. Jangan drop/truncate/reset/seed database pengguna atau clear app data. Test backend hanya setelah guard memastikan effective MySQL `inofarm_test`, sebelum SQL. Jangan menjalankan migrate:fresh terhadap konfigurasi yang belum dibuktikan aman.
- Actor selalu dari sesi, role/assignment/parent/versi diperiksa server juga pada list/detail/file/export/sync/replay. Offline bukan bypass akses.
- Local draft+outbox atomic per akun. Server mutation+audit+receipt/feed atomic. Gunakan UUID/version/hash/epoch, conflict review dan tombstone yang sudah ada. Jangan membuat queue kedua dengan aturan retry berbeda.
- Uang fixed decimal, kuantitas/satuan eksplisit. Unknown bukan nol. Data studi/fixture tidak menjadi default produksi.
- Persetujuan implementasi prompt mengizinkan pekerjaan reversible dalam scope, bukan perubahan kebijakan produksi yang belum diputuskan. Tanyakan hanya keputusan bisnis yang betul-betul menahan fase tertentu; teruskan pekerjaan independen.

## Kontrak makna yang harus dipertahankan

Pisahkan empat dimensi: `sync_state` (lokal/server), `record_state` (aktif/batal), hasil pembandingan acuan, dan keputusan verifikasi pengelola. Jangan mengganti arti ACCEPTED/REJECTED existing dengan valid/tidak valid bisnis.

Pengukuran di luar rentang dapat dicatat valid oleh pengelola. Hasil pembandingan otomatis bukan keputusan verifikasi. Ketiadaan target harus “Acuan belum tersedia”; jangan menganggap valid atau tidak valid secara otomatis.

Varietas, populasi, tanggal semai/pindah dan fase terkait periode/kelompok. Tanggal/fase baru tidak menulis ulang histori panen atau kegiatan. Satu GH cukup untuk konteks UX sehari-hari jika periode berjalan sudah ditetapkan; jangan menghapus struktur musim/kelompok.

Panen bukan penjualan. Penerimaan bukan laba. Pembayaran biaya bukan alokasi biaya. Pembelian aset bukan penyusutan. Investasi awal dan arus kas tidak boleh diduplikasi dari laba musim secara diam-diam.

## Fase PG0: kontrak dan baseline penerimaan

Hasil: matriks requirement -> source -> gap -> test -> evidence, kontrak additive dan rencana migrasi. Catat `belum diimplementasikan`, `implemented`, `verified` secara terpisah.

Tuliskan keputusan yang belum final tanpa menghalangi kerja independen:

- Verifikasi catatan pengelola sendiri: kebutuhan eksplisit adalah pemeriksaan input pekerja. Default rancangan adalah inbox pekerja; jangan auto-validasi catatan pengelola atau mengubah aturan existing tanpa keputusan. Simpan provenance peran pada waktu pencatatan agar demosi/promosi tidak mengubah histori inbox.
- Dampak pending/invalid pada ringkasan: usulan menampilkan data server dengan label “Belum diverifikasi”, menampilkan invalid di riwayat dan tidak sebagai angka utama terkini. Jangan mengubah ringkasan uang/panen karena review kegiatan saja. Bekukan cakupan keputusan ini sebelum menerbitkan filter produksi.
- Panen: bedakan populasi tanaman (tanaman), target buah (buah), hasil layak/cacat (buah/kg), dan tanaman tidak berbuah (tanaman). Klasifikasi grade layak serta finalisasi panen perlu basis eksplisit, bukan asumsi nama grade.
- Investasi tetap tahunan sesuai kontrak existing. Konversi dari urutan panen studi atau agregasi lintas GH memerlukan pemetaan periode dan cakupan yang disepakati. Jangan menahan perbaikan UI keuangan untuk menunggu otomasi baru ini.

Tidak membuat ambang EC, faktor konversi EC-PPM, dosis, tingkat diskonto, jumlah musim/tahun, persentase kegagalan atau deadline verifikasi produksi.

## Fase PG1: Antrean verifikasi pekerja

Bangun fitur server, data Android dan UI sampai end-to-end, bukan sekadar mengganti judul QueueScreen.

1. Inbox berasal dari kegiatan pekerja yang sudah tersimpan server pada GH dalam penugasan pengelola, termasuk kiriman dari perangkat/akun lain. Bounded pagination, filter GH/tanggal WIB/pekerja/jenis/status dan urutan deterministik. Default tampilkan yang menunggu pemeriksaan; riwayat keputusan tetap dapat dibuka.
2. Card memuat jenis kegiatan, GH/periode/varietas bila tersedia, pencatat, waktu kejadian, ringkasan input dan status verifikasi. Seluruh area card dapat membuka detail dengan semantics yang benar. Jangan menampilkan UUID operasi sebagai isi utama.
3. Detail menampilkan versi yang diperiksa, hasil ukur EC mS/cm, PPM ppm, pH, pengamatan opsional, bahan+jumlah+satuan, stadia, pekerjaan/dilewati+alasan, catatan/lampiran, waktu aktual, HSS/HST dan versi snapshot acuan. Aktual dan rencana/target ditempatkan berdekatan tanpa mengisi aktual dari target.
4. Aksi “Tandai valid” dan “Tandai tidak valid”. Tidak valid memerlukan alasan yang dibaca pekerja; valid boleh mempunyai catatan. Tampilkan ringkasan keputusan sebelum kirim. Hindari warna sebagai satu-satunya makna.
5. Model keputusan append-only terkait `activity_id` dan **activity_version** yang diperiksa, actor, waktu server, alasan, versi keputusan dan operation identity. Nama field/endpoint dibekukan dalam kontrak sebelum migrasi. Lindungi dua pengelola yang memutuskan bersamaan, keputusan terhadap versi basi, replay setelah response hilang, akun dicabut dan musim/parent berubah.
6. Koreksi pekerja/pengelola membuat revisi baru yang memerlukan pemeriksaan baru, tidak mewarisi valid dari versi lama. Keputusan lama tidak dihapus. Pembatalan record tetap lifecycle tersendiri. Histori existing jangan di-backfill sebagai valid; label “Belum pernah diverifikasi” dan sediakan filter histori tanpa menciptakan jutaan keputusan palsu.
7. Gunakan outbox existing untuk draf/pengajuan keputusan jika mendukung offline. Tanpa penerimaan server, tulis “Keputusan belum terkirim”; sumber cache hanya last-known. Saat sinkron, server wajib memeriksa versi yang ditinjau. Retry identik tidak membuat keputusan/audit kedua.
8. Pertahankan antrean teknis existing di akses “Pengiriman saya” atau “Sinkronisasi”, terpisah jelas dari “Verifikasi kegiatan”. Jangan menghilangkan konflik/auth/epoch/recovery. Peran pekerja tetap dapat mengelola kiriman lokalnya.
9. Pekerja melihat status/alasan keputusan pada versi catatannya dan dapat memperbaiki sesuai hak existing. Jangan membuka data keuangan atau kegiatan di luar penugasan melalui feedback ini.

## Fase PG2: Kegiatan dan acuan Excel

Reuse ActivityDraft/ActivityService/shared forms, schema/snapshot/pull/review/history/export yang relevan. Buat matriks semua baris kegiatan nonkosong dari kedua sheet: tipe, field, satuan, hari berbasis HSS/HST, opsional/wajib dan provenance. Judul bagian/baris kosong bukan tugas.

- Pertahankan pengukuran EC+PPM tanpa bahan; nutrisi PPM/pH dan bahan aktual; spray multibahan; stadia aktual; pekerjaan umum/spontan; kegiatan dilewati beserta alasan. Kedua peran dapat mencatat sesuai scope dan musim.
- Sediakan pengamatan terstruktur opsional untuk DO, suhu air, tinggi tanaman, jumlah daun, lebar daun, beserta unit dan basis sampel/catatan. Jadikan field wajib hanya untuk subtype yang benar-benar memerlukannya dan sudah diputuskan. Jangan memasukkan nol pada catatan lama atau mengubah teks lama menjadi angka lewat tebakan.
- Kegiatan spesifik seperti benih, kebersihan, tali, polinasi, seleksi, fertigasi, deteksi HPT dan pascapanen dapat memakai katalog/jenis pekerjaan umum dengan input sesuai tugas. Jangan memaksakan satu giant form harian. Beberapa kegiatan dan pengukuran per hari harus tetap sah.
- Lengkapi jalur acuan agar measurement dan EC dapat direpresentasikan konsisten pada admin, API validation, unit, persistence, snapshot, comparator dan kontrak. Pengguna tetap mengisi target yang disahkan; Excel tidak menyediakan target EC yang boleh ditebak dari PPM.
- Jadwal menyediakan rencana dan tautan realisasi, tidak auto-complete karena tanggal terlewati. Tandai status belum dikerjakan/dilewati hanya dengan dasar yang ada; jangan menganggap tidak ada kiriman sebagai pekerja tidak bekerja saat perangkat belum sinkron.
- Anomali `K21,K34:K36,E88:E90` dan basis HSS/HST harus tercatat dalam preview sumber. Jangan import/publish ke production tanpa tinjauan nilai. Template atau import dry-run boleh disiapkan, tanpa mengubah workbook asli atau data pengguna.
- Aksi “Catat kegiatan” dari tab Kegiatan langsung menuju pemilih GH/periode bila belum siap lalu melanjutkan ke form; konteks yang belum tersedia memberi alasan dan akses penyiapan yang tepat.

## Fase PG3: Beranda sesuai status hari ini

- Login pengelola membuka semua GH yang sah. Pemilih satu GH mengikat seluruh konten ke GH dan periode berjalan yang benar. Pergantian cepat tidak boleh menampilkan respons GH lama pada pilihan baru.
- Semua GH: gambaran tiap GH dan catatan terbaru, panen/keuangan jika ada, basis periode terlihat. Satu GH: identitas varietas/fase/tanggal, hasil harian, panen dan keuangan relevan. Jangan menjumlahkan EC atau PPM antar-GH.
- Tepat dua kartu EC dan PPM. Keduanya berasal dari satu record yang memenuhi kebijakan sumber, bukan EC dari A dan PPM dari B. Input pengelola juga dapat ditampilkan dengan identitas pencatat, karena pengelola diizinkan mencatat; jangan diam-diam menampilkan hanya worker lalu mengabaikan input sah pengelola.
- Bila server telah diperiksa untuk hari kalender WIB dan belum ada pengukuran hari ini, tampilkan **“Belum ada pencatatan hari ini” dekat kedua kartu**. Riwayat terakhir boleh tetap ada sebagai informasi sekunder berlabel tanggal/waktu, mempertahankan fallback existing. Bila offline/gagal/cakupan partial, katakan “Belum dapat memeriksa pencatatan hari ini”, jangan mengklaim kosong atau memasukkan nol.
- Bedakan hari ini tanpa catatan, catatan belum diverifikasi, tidak valid, diterima server, queued lokal dan data lama. Terapkan kebijakan yang dibekukan PG0; seluruh alasan sumber tidak layak harus dapat ditelusuri. Legacy tanpa EC tetap “EC belum tersedia”.
- Pergantian WIB tengah malam/foreground, koreksi/batal/review, pencabutan scope dan perubahan periode memperbarui state tanpa kehilangan draf parent asal.
- Kas bulan ini = penerimaan lunas dan pembayaran aktual sesuai periode; label bukan laba. Bila ringkasan semua GH ditambah total panen, tampilkan periode dan hindari double-count antar-grade/record.

## Fase PG4: Panen, varietas, target dan hasil

1. Dari Panen, pilih GH dahulu jika belum ada, lalu periode/kelompok yang cocok. Tampilkan varietas master (contoh pengguna Yellow Hami, Sweet Hami, Dalmantian, Fujisawa), jangan hardcode sebagai satu-satunya pilihan atau menciptakan master produksi. Jika beberapa kelompok aktif, minta memilih tanaman yang benar. Jika satu, konfirmasi konteks tanpa pilihan berulang.
2. Pertahankan input waktu, grade, jumlah buah integer dan kg decimal; hitung total dari rincian. Varietas bukan pilihan bebas yang dapat bertentangan dengan group. Simpan sumber/versi nama varietas dan basis target agar histori tetap benar saat master diperbarui.
3. Tambahkan target panen eksplisit per periode/kelompok: target buah dan bila diperlukan target kg, basis/penetap/tanggal/versi. Populasi tanaman existing ditampilkan terpisah. Target opsional pada histori, kosong bukan nol, tidak dipaksakan sama dengan populasi.
4. Model hasil layak dan cacat harus eksplisit dan tidak menghitung buah yang sama dua kali. Tanaman tidak berbuah bukan grade buah. Pertahankan kompatibilitas multigrade dan saldo penjualan; klasifikasi lama yang belum diketahui tidak otomatis layak semua.
5. Rekap kumulatif seluruh panen aktif dalam periode dan detail tiap kejadian: target, aktual layak buah/kg, cacat bila diketahui, selisih target dan capaian. Untuk fixture target160 dan layak150, capaian93,75%, selisih10; belum boleh berlabel gagal10 jika panen bertahap belum selesai. Aktual boleh melebihi target, jangan clamp100% atau tolak otomatis.
6. Hindari input “jumlah total” dan “aktual” yang menduplikasi jumlah rincian tanpa rekonsiliasi. Jika target diubah, keputusan audited dan snapshot laporan lama tidak berubah. Penyelesaian panen memerlukan tindakan eksplisit/basis yang disepakati, tidak otomatis dari HST.
7. Koreksi/batal memperbarui rekap secara konsisten. Bobot yang sudah dijual tetap dilindungi; jangan memotong saldo, menghapus grade yang dipakai, atau menciptakan penjualan dari panen.

## Fase PG5: Keuangan yang sesuai makalah dan mudah dipakai

Pertahankan engine yang benar. Inventaris ulang seluruh jalur sebelum menulis fitur baru: pembayaran operasi/aset, kategori, FC/VC, alokasi, aset, penyusutan, penjualan lunas, evaluasi musim, investasi, ekspor dan riwayat.

Tampilkan jalur kerja dengan label Indonesia: Biaya dan alokasi, Investasi awal/aset dan penyusutan, Penjualan/penerimaan, Evaluasi musim, Analisis investasi. Pengguna mengisi sumber transaksi/asumsi; TC, laba dan rasio dihitung, bukan disimpan sebagai input angka kedua. Letakkan pilihan GH/musim/basis di atas hasil dan tampilkan sumber yang belum lengkap serta tindakan melengkapinya.

Rumus yang dipertahankan dan diuji:

| Indikator | Kontrak |
| --- | --- |
| Penyusutan | `(nilai perolehan - nilai sisa)/umur`; alokasi manual eksplisit dibatasi anggaran tahunan/kumulatif existing. |
| FC, VC, TC | FC = alokasi tetap aktif + alokasi penyusutan aktif; VC = alokasi variabel aktif; TC = FC+VC. |
| TR dan laba | TR = total penjualan lunas aktif dengan rounding per transaksi; L=TR-TC. |
| R/C dan B/C | R/C=TR/TC; B/C sederhana=L/TC; TC harus >0. Label B/C bukan discounted B/C. |
| BEP | P=TR/Q terjual; BEP kg=TC/P; BEP harga=TC/Q. Syarat denominator dan asumsi biaya tercatat tetap terlihat. |
| NPV/IRR | NPV=-I0+Σ CFt/(1+r)^t, periode tahunan existing; IRR hanya pada kondisi solver yang didukung, dengan alasan bila tidak tersedia. |
| PP/DPP | PP kumulatif CF; DPP kumulatif discounted CF. Konvensi interpolasi/akhir tahun dipilih eksplisit; belum kembali modal bukan nol tahun. |

Catat rekonsiliasi makalah dalam bantuan atau dokumentasi formula: B/C salah label; narasi R/C2,26 tidak sesuai perhitungan2,76; I0 310.161.000 berbeda cakupan dari300.000.000; subtotal NPV tidak sesuai saldo akhir; PP lampiran memakai PV; basis diskonto/panen/tahun tidak konsisten. Jangan menampilkan semua editorial akademik pada form harian; cukup formula, periode, sumber dan asumsi yang membantu keputusan.

Uji fixture studi dengan hasil pada audit pendamping. Jangan mengganti rumus agar cocok dengan subtotal NPV yang salah. Jangan mengubah seluruh arus kas menjadi laba musim: biaya penyusutan nonkas harus direkonsiliasi secara eksplisit. Tidak otomatis membagi aset/biaya bersama ke semua GH; source allocation scope existing dipertahankan hingga kebijakan lintas GH disahkan.

Perhitungan dengan sumber parsial memiliki status sementara; kosong/denominator nol punya alasan. Preview, immutable snapshot, export PDF/CSV dan versi rumus harus konsisten. Mengubah transaksi tidak menghitung ulang laporan yang sudah diterbitkan secara diam-diam. NPV positif bukan klaim usaha pasti layak.

## Fase PG6: Kelola greenhouse dan akun

- Sediakan akses bernama **Kelola greenhouse** dan **Kelola akun** yang mudah ditemukan, misalnya pada menu akun/Administrasi. Pertahankan lima tab bawah; akses tambahan bukan alasan memenuhi layar dengan tujuh tujuan.
- Kelola greenhouse: daftar/cari/detail/buat/ubah/nonaktifkan/aktifkan; detail merangkum nama, periode, varietas, semai/pindah, populasi, fase aktual, kesiapan input dan penugasan. Alur penyiapan menghubungkan GH -> musim -> kelompok/varietas -> tanggal -> penetapan periode -> penugasan. Resume bila terhenti; jangan membuat parent duplikat saat retry.
- Fase seperti vegetatif/generatif tetap perubahan stadia aktual bertanggal dan audited, bukan overwrite tanpa histori atau auto-advance. Jangan mengubah tanggal tanam menjadi tanggal mulai musim tanpa membedakan semai/pindah.
- Kelola akun reuse create/edit email/nama/role/status/reset password dan assignment existing. Validasi email unik lintas case sesuai kontrak; password baru/konfirmasi tidak muncul dalam receipt, audit, feed, log atau draft plaintext. Password lama tidak dapat dilihat.
- Email/password/role/status yang berubah mencabut sesi sesuai perilaku existing; UUID akun tetap dan draf akun tidak hilang. Pertahankan guard akun sendiri, target lintas scope dan assignment efektif.
- Nonaktifkan sebagai penghapusan operasional yang menjaga histori. Jelaskan dampaknya pada akses, data tersimpan dan pengajuan tertunda. Jangan hard-delete user/GH atau cascade histori.

## Fase PG7: frontend dan penerimaan terpadu

Design Read: Operate; tenang, hangat, teliti. Pertahankan DESIGN.md: forest/sage/krem, SansSerif native dan angka tabular, botanical tepi login/Beranda, form/data solid, kaca selektif. ENERGY/RHYTHM/MOTION 2/2/2 untuk Beranda/menu dan1/2/2 untuk form/detail/laporan. Tidak membuat aset baru. Satu fokus per layar: pilih GH, periksa catatan, simpan kegiatan, rekap panen, atau evaluasi biaya.

Gunakan komponen Farm yang ada; label aktual/acuan/status tidak mengandalkan warna. Field bersatuan, error dekat field, pesan Indonesia yang dapat ditindaklanjuti. Progressive disclosure untuk rincian teknis, tetap tampilkan seluruh alasan bisnis penting. Format UI boleh membuang nol pecahan tidak perlu tanpa mengubah presisi wire/storage. Status penerimaan lokal/server/verifikasi selalu berbeda.

Touch target minimal48dp, semantics TalkBack, urutan fokus, contrast teks4,5:1 dan teks besar3:1. Uji tema terang/gelap, 320dp/normal/tablet, landscape, font100/160/200%, IME, Back, rotasi dan process death. Transisi singkat yang menghormati skala animasi Android; jangan menaruh data di atas blur/gambar yang mengurangi keterbacaan. Adaptasi native, bukan memasang aturan CSS/ARIA/GSAP pada Compose.

## Matriks acceptance wajib

| AC | Bukti minimum |
| --- | --- |
| PG-AC01 | Pengelola melihat satu/semua GH sah; API langsung ke GH asing ditolak; respons pilihan lama tidak bocor ke pilihan baru. |
| PG-AC02 | Worker akun/perangkat A mengirim kegiatan; pengelola B melihatnya pada inbox server, bukan mengandalkan outbox akun B. |
| PG-AC03 | Detail menampilkan seluruh nilai, satuan, bahan, catatan, umur dan snapshot acuan dari versi yang diperiksa; sumber tanpa target tidak diberi keputusan palsu. |
| PG-AC04 | Valid/tidak valid, alasan wajib, reviewer/waktu, feedback worker, dua manager bersaing, koreksi sesudah review, keputusan versi basi, scope dicabut dan replay diuji. Tidak ada audit/decision duplikat. |
| PG-AC05 | Inbox bisnis dan pengiriman lokal tetap dapat diakses; offline, auth expired, conflict dan epoch recovery tidak hilang. |
| PG-AC06 | Pekerja dan pengelola mencatat jenis kegiatan yang dipetakan dari kedua sheet; multibahan, skip, pengamatan opsional, input salah, koma desimal dan catatan lama tanpa field baru diuji. |
| PG-AC07 | Editor acuan measurement/EC dan unit yang benar bekerja melalui UI/API; target missing/ambigu/berubah tidak menulis ulang snapshot lama. |
| PG-AC08 | Kartu EC/PPM satu record; hari ini kosong dengan data kemarin, seluruh data kosong, legacy EC kosong, offline/error, pergantian WIB, review invalid dan perubahan GH diperiksa. Pesan hari ini dekat kartu. |
| PG-AC09 | GH/varietas panen benar; target160 aktual layak150 menghasilkan93,75%; target kosong/0, hasil>target, panen bertahap, dua panen, koreksi/batal, grade tidak diketahui dan saldo terjual diuji. |
| PG-AC10 | Angka studi FC/VC/TC/TR/L/R/C/B/C/BEP cocok hitung independen; partial/missing/0, pembulatan lintas transaksi dan double-count aset/alokasi dicegah. |
| PG-AC11 | NPV/IRR/PP/DPP memakai unit periode yang sama, I0 sekali, actual/projection eksplisit, CF negatif/unknown/tidak pulih ditangani. Laporan/export cocok snapshot; jangan mengejar angka salah makalah. |
| PG-AC12 | Dari Kelola greenhouse, pengelola menyiapkan periode dan assignment sehingga kedua peran dapat mencatat; perubahan fase/tanggal/varietas tidak merusak histori. |
| PG-AC13 | Create akun, edit email, reset password, nonaktif/aktif, duplicate email, cross-scope, token revoked, self-guard dan draf per UUID diuji tanpa membocorkan password. |
| PG-AC14 | Back/IME/rotasi/process death/offline/relogin/response lost menjaga draft dan outbox; penolakan bisnis tidak disamakan dengan gagal sinkron. |
| PG-AC15 | Screenshot sebelum/sesudah target aktual, tema/viewport/font, click-through tiap aksi baru, empty/loading/error/offline, TalkBack/fokus dan kontrak angka ditulis sebagai hasil nyata. |

Jalankan pemeriksa kontrak proyek, unit PHP/Kotlin, backend MySQL guarded dan instrumentasi yang relevan. Reuse `FinanceActivitySyncTest`, `FinanceMeasurementTest`, `ManagerHomeTest`, `FinanceHarvestTest`, `FinanceSeasonReportTest`, `FinanceInvestmentTest`, `WorkerRecordingRecoveryTest`, test matematika/oracle dan test native existing. Tambahkan tests pada perilaku baru dan risiko regresi, bukan tests yang sekadar menyalin implementasi.

Minimal satu skenario dua akun dengan Laravel/MySQL uji nyata melalui APK: worker kirim -> pengelola review -> worker koreksi -> review ulang -> Beranda terbaru. Minimal satu skenario panen bertahap dan satu keuangan dari transaksi sampai laporan. Mock HTTP tidak membuktikan DB nyata. Jangan memakai akun/data pengguna untuk membuat fixture tanpa otorisasi khusus lingkungan tersebut.

## Kriteria selesai dan serah terima

Setiap fase mencatat requirement/AC, kontrak/migration/source, test baru, perintah/hasil sebenarnya, screenshot bila UI berubah, batas dan keputusan terbuka. Update context/DECISIONS hanya untuk keputusan yang benar-benar diambil; update DESIGN.md hanya untuk perubahan visual nyata. Audit awal dipertahankan sebagai baseline bertanggal.

Final laporkan apa yang berubah, cara pengelola mengaksesnya, checks yang benar-benar dijalankan dan gap yang tersisa. Jangan berhenti setelah menyusun rencana ulang bila diminta mengeksekusi. Jangan menyatakan seluruh produk selesai hanya karena build atau mocks lulus. UAT mitra, pentest berizin, perangkat sasaran, pengecualian SOP Android-only, penerimaan akademik dan produksi tetap gate terpisah.
