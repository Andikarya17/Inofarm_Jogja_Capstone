# Audit kesesuaian visi pengelola, 9 November 2026

## Kesimpulan dan batas bukti

Aplikasi **belum sepenuhnya sesuai** dengan permintaan pengelola terbaru. Beranda, input kegiatan pengelola, panen multigrade, sebagian besar keuangan, serta administrasi greenhouse dan akun sudah memiliki implementasi Android dan backend. Kekurangan utama adalah verifikasi kegiatan pekerja oleh pengelola, target dibanding hasil panen, dan representasi terstruktur beberapa pengamatan dalam Excel. Perlu penyederhanaan akses administrasi dan keuangan, bukan membangun semuanya ulang.

Baseline source: `8d74cba1e03a87ec6dff509fbe398d1fdb9d0860`, berasal dari `codex/worker-recording-recovery`. Audit ini membaca source, kontrak dan test, mengekstrak ulang berkas asli secara read-only, menghitung ulang contoh aritmetika, serta melihat screenshot historis. **Tidak menjalankan APK, suite backend, JVM, atau instrumentasi ulang.** “Ada” berarti ada jalur implementasi yang ditelusuri, bukan seluruh acceptance runtime baru lulus. Data akun/database pengguna tidak diubah.

Screenshot lokal yang dilihat: `outputs/worker-recording-recovery/after-manager-records.png` dan `after-manager-harvest-detail.png`. Keduanya berasal dari pengujian sebelumnya yang dijelaskan dalam [bukti pemulihan](WORKER_RECORDING_RECOVERY_20261109.md); struktur, label dan tokennya sesuai source yang diperiksa. Ini bukan bukti semua layar, tema atau ukuran saat ini.

Implementasi berikutnya mengikuti [master prompt pengelola](MASTER_PROMPT_MANAGER_ALIGNMENT.md). [Prompt eksekusi](RUN_MANAGER_ALIGNMENT_SOL.md) dapat ditempel pada chat 6.1 Sol. Dokumen rencana ini tidak menyatakan perbaikan sudah dilaksanakan.

## Matriks kebutuhan terhadap source

| ID | Permintaan | Status source saat audit | Bukti dan tindakan |
| --- | --- | --- | --- |
| PG-01 | Beranda memilih satu atau semua greenhouse | Ada | `feature/managerhome/ManagerHomeScreen.kt`, `ManagerHomeState.kt`; backend `Domain/Cultivation/ManagerHome.php`. Semua berarti seluruh greenhouse dalam kewenangan akun, bukan seluruh database. |
| PG-02 | Ringkasan kegiatan, panen dan keuangan jika ada input | Ada, dengan batas cakupan | Beranda punya Catatan terbaru, panen periode terpilih dan kas bulan WIB. Kas adalah penerimaan penjualan lunas dan pembayaran aktual, bukan laba. Mode semua greenhouse menampilkan catatan gabungan dan kas gabungan; jumlah panen khusus berada di detail greenhouse. Bila dibutuhkan total panen gabungan, tambahkan sebagai ringkasan terpisah dengan basis periode jelas. |
| PG-03 | Dua kartu EC dan PPM; bila hari ini kosong, beri tahu | Sebagian besar ada; penyajian perlu diperjelas | `MeasurementDashboard.kt: MeasurementCards/MeasurementSource`, `ManagerHome.php: snapshot`, `ManagerHomeScreen.kt: findings`. Ada pesan “Belum ada pengukuran tercatat hari ini” di bagian perhatian. Kartu tetap dapat menampilkan pengukuran sebelumnya; label waktu sumber terpisah. Tempatkan pesan hari kosong dekat kedua kartu, jangan membiarkan angka lama terbaca sebagai hari ini. Offline tidak membuktikan hari ini kosong. |
| PG-04 | Antrean berisi kegiatan pekerja lintas perangkat untuk valid/tidak valid | Belum ada alur bisnis ini | `feature/QueueScreen.kt: QueueScreen` menggunakan `activity.queue`, dengan judul penjelas “Pengajuan lokal dan tanggapan server untuk akun ini.” Ini outbox per akun, termasuk konflik/retry. `routes/api_v2.php` dan `ActivityService.php` tidak menyediakan keputusan verifikasi pengelola terhadap revisi kegiatan. |
| PG-05 | Ketuk antrean membuka detail aktual dan acuan Excel | Sebagian fondasi ada | Detail/riwayat aktivitas, `reference_snapshot`, bahan, satuan, HSS/HST dan hasil pembandingan sudah ada. Aksi Tinjau catatan pada antrean lokal bukan persetujuan manajerial. Tambahkan inbox server dan detail perbandingan beserta keputusan/reviewer/alasan/waktu. |
| PG-06 | Pengelola juga mencatat kegiatan harian | Ada | `FinanceRoot.kt: ActivityForm`, `ActivityViewModel`, `ActivityDraft`, `ActivityService`. Aksi Beranda memakai periode berjalan; tab Kegiatan punya aksi tambah. Saat parent belum siap pengelola diarahkan ke konteks Beranda. Perlu alur langsung yang lebih jelas dari tab. |
| PG-07 | Seluruh jenis pencatatan mengikuti Excel | Sebagian | Lima jenis tersedia: pengukuran, nutrisi, penyemprotan, pekerjaan umum, stadia; ada multibahan, alasan dilewati, jadwal opsional. DO, suhu air, tinggi/lebar/jumlah daun, taksasi, cacat dan tanaman tidak berbuah masih berupa keterangan bebas, bukan angka terstruktur. Jadwal asli tidak otomatis terpasang. |
| PG-08 | Acuan EC/PPM/pH sesuai jenis kegiatan dan waktu | Sebagian | `ReferenceService.snapshot` membaca EC/PPM/pH. Namun `ReferenceController` dan `AdminForm` editor acuan hanya menerima PPM/pH serta jenis nutrition/spray/general/stage. Target EC dan jenis measurement belum bisa dikonfigurasi melalui jalur tersebut. Tanpa target, hasil harus “acuan belum tersedia”, bukan tidak valid. |
| PG-09 | Panen pilih GH, varietas, jumlah buah dan berat | Ada sebagian alur | `HarvestScreen.kt`, `HarvestDraft.kt`, `HarvestService.php`: GH/kelompok, waktu, grade, jumlah buah dan kg tersedia. Varietas tersimpan pada kelompok, bukan pilihan bebas setiap panen. Detail panen belum menonjolkan varietas. Pertahankan relasi varietas agar tidak bertentangan dengan tanaman yang dipanen. |
| PG-10 | Ekspektasi 160 dibanding aktual layak 150 | Belum ada model/rekap khusus | `MasterService`/`AdminForm` punya populasi tanaman; payload panen punya jumlah buah per grade dan kg. Tidak ada target buah, klasifikasi layak/cacat terstruktur, atau rekap target-versus-aktual kumulatif. Populasi tanaman tidak otomatis sama dengan target buah. |
| PG-11 | Investasi awal dan penyusutan | Ada | `AssetEntryScreen`, `AssetDraft`, `AssetService`, `DepreciationMath`; pembayaran pembelian aset, nilai perolehan, nilai sisa, umur dan alokasi penyusutan terpisah. Basis alokasi manual sudah disetujui sebelumnya. Jangan mengganti dengan pembagian otomatis menurut angka makalah. |
| PG-12 | FC, VC, TC, penerimaan, pendapatan, R/C, B/C, BEP | Ada | `FinancialEntryScreen`, `SeasonReportScreen`, `SeasonReportSources`, `SeasonEvaluation`. FC mencakup alokasi biaya tetap dan penyusutan; VC dari alokasi variabel; penerimaan dari penjualan lunas. Perhitungan sumber yang belum lengkap diberi alasan. Perlu akses dan penjelasan lebih langsung dari Keuangan. |
| PG-13 | Analisis kelayakan investasi | Ada, basis berbeda dari tabel panen makalah | `InvestmentScreen`, `InvestmentScenarioDraft`, `InvestmentEvaluation` menghitung NPV/IRR/PP/DPP dengan arus kas tahunan relatif dan asumsi eksplisit. Makalah memakai urutan panen lintas greenhouse. Jangan menyamakan satu panen dengan satu tahun atau menyalin IRR 16,60%. |
| PG-14 | CRUD GH dengan nama, varietas, tanggal tanam, fase | Ada sebagai beberapa resource; UX belum satu alur | `AdminScreen`, `AdminForm`, `MasterService`, `RunningPeriodSheet`: GH, musim, kelompok, varietas/populasi/tanggal semai-pindah tersedia; fase berasal dari kegiatan stadia aktual. Tambahkan pintu Kelola greenhouse dan alur penyiapan periode, tanpa menduplikasi varietas/tanggal sebagai kolom GH yang menimpa histori. Delete memakai nonaktifkan. |
| PG-15 | CRUD akun, email, password, akun baru | Ada pada baseline terbaru | `AdminForm` selalu menampilkan email; `UserController` update menerima email, memeriksa keunikan dan mencabut token; `AdminScreen` punya password dan konfirmasi. Create, edit nama/role/status, reset password dan penugasan tersedia. Masuk melalui ikon akun > Administrasi. Jangan mengulang gap email pada audit sebelum commit `8d74cba`. |

Lokasi prefix Kotlin di tabel: `android/app/src/main/java/com/inofarm/jogja/`; prefix PHP: `backend/app/` kecuali routes. “CRUD akun” tidak berarti boleh membaca password lama atau menghapus audit. Akun lintas greenhouse di luar kewenangan tidak boleh dimutasi hanya karena salah satu penugasannya terlihat.

## Pembacaan Excel sebagai patokan kegiatan

Sumber asli: `D:/Semester 7/Capstone 2/Proposal/Jadwal  MT 3 pemupukan dan stadia tumbuh Melon GH Krebet_093055.xlsx`. Dibaca ulang dengan formula tetap terpelihara, kedua sheet diperiksa. SHA-256: `39091a131b63439f7223615e8872b34449c4c4f6ab8dfe411f78c8d7baf65aa9`.

| Sheet/rentang | Isi nyata | Pemetaan aplikasi dan kekurangan |
| --- | --- | --- |
| `Sheet1!B5:H5`, data `B6:H90` | Tanggal, HSS/HST, PPM, pH, stadia, penambahan pupuk L, spray tambahan opsional | Waktu aktual, umur turunan dari tanggal kelompok, hasil ukur, stadia, bahan+jumlah+satuan sudah tersedia. Nilai acuan dan hasil aktual harus tetap terpisah. |
| `Sheet1!J4:M36` | HST, tanggal, nama bahan spraying, pagi/sore; `L9` berisi Antracol dan Agrept | Multibahan didukung. Jadwal perlu mencatat versi sumber dan waktu rencana; jumlah/dosis aktual tidak boleh disalin dari nama bahan atau diisi otomatis. |
| `Jadwal general!C25` | Cek nutrisi harian: EC, pH, DO, suhu air | EC/PPM/pH terstruktur; DO dan suhu air masih teks. Usulan: field opsional bersatuan mg/L dan °C, bukan wajib untuk semua kegiatan. |
| `Jadwal general!C31,C42,C48,C59` | Pengamatan tinggi, jumlah dan lebar daun | Masih teks. Usulan form pengamatan terstruktur dengan cm, helai dan basis sampel. Jangan menganggap semua angka sebagai rerata seluruh greenhouse. |
| `Jadwal general!C4:C67,F3:F50` | Benih, penyiraman, kebersihan, tali, HPT, pinching, polinasi, fertigasi, seleksi, pengamatan dan pascapanen | Dapat ditulis pada pekerjaan umum, tetapi belum merupakan katalog/form harian dari worksheet. Perlu pemetaan jenis kegiatan dan jadwal yang bisa ditinjau, bukan puluhan field wajib dalam satu form. |
| `Jadwal general!F22,F33,F36:F42` | Taksasi/estimasi, grade, cacat, tanaman tidak berbuah, panen dan timbang | Belum ada rekap terstruktur yang membedakan estimasi, buah aktual, tanaman tidak berbuah, panen dan penjualan. |
| `Sheet1!H80,H90` | Panen Fujisawa dan Yellow Hammi | Varietas harus berasal dari master/kelompok. Ejaan di file berbeda dari contoh pengguna; jangan membuat duplikat master dari salah ketik. |

Anomali sumber yang harus dibawa ke tinjauan:

- `Sheet1!J20:K21`: HST sama 25 tetapi tanggal berbeda. `K34` berisi 4 April 2024; `K35:K36` 8 April 2025, di tengah rangkaian Oktober-Desember 2025.
- `Sheet1!E88:E90` berisi `5.8-6.10`, `5.8-6.11`, `5.8-6.12`. Nilai ini tidak boleh “diperbaiki” atau diterbitkan otomatis menjadi target produksi.
- Kolom gabungan HSS/HST berganti penomoran pada `C16`; sheet general juga memisahkan tahap persemaian dan pemeliharaan. Basis umur harus eksplisit. Baris kosong/judul bagian bukan kegiatan yang otomatis perlu dicatat.
- Angka PPM dan rentang pH pada jadwal bukan bukti pengukuran aktual. Jadwal lama bukan resep agronomi yang telah disahkan untuk semua varietas/periode.

## Makalah keuangan dan koreksi logika

Sumber asli: `D:/Semester 7/Capstone 2/Proposal/Dwito Mufti Agung_574189_Makalah Analisis Usahatani Meloh Hidroponik.pdf`, 27 halaman. SHA-256: `1302d2d23be371a43fb2b4ff7c91d5331537420f3671d361dde69242ccd65dcc`. Bagian perhitungan dibaca pada halaman PDF 16-22 dan lampiran 25; halaman 16, 19, 20 dan 25 juga dilihat hasil render. Nomor halaman adalah urutan PDF, bukan nomor cetak.

| Konsep | Makalah | Kontrak/source saat ini | Keputusan untuk perbaikan |
| --- | --- | --- | --- |
| Investasi awal | Tabel 2 hal.16 total Rp310.161.000; Tabel 7 hal.20 I0 Rp300.000.000 | Pembelian aset dan I0 investasi eksplisit, tidak otomatis sama | Dua cakupan angka berbeda; tampilkan komponen/basis I0, jangan menetapkan salah satunya sebagai default. |
| Penyusutan | Nilai perolehan, sisa, umur; beban per GH/tahun/musim | Garis lurus `(A-S)/umur`, batas kumulatif dan alokasi manual | Pertahankan; tabel mencampur jumlah aset bersama dan basis per GH. Jangan membagi lagi atau membebankan semua ke setiap GH tanpa basis. |
| FC, VC, TC | Hal.17-18: 5.421.100; 3.430.000; 8.851.100 | `TC=FC+VC`; penyusutan hanya sekali dalam FC | Sesuai konsep. Pembayaran dan alokasi bukan dua biaya terpisah. Pembelian aset tidak sekaligus menjadi biaya operasi penuh dan penyusutan. |
| TR dan laba | Hal.19: 905 kg x 27.000 = 24.435.000; laba 15.583.900 | TR dari penjualan lunas, `L=TR-TC` | Bedakan kg panen, kg terjual, proyeksi dan penerimaan aktual. Panen sendiri tidak membuat transaksi uang masuk. |
| R/C | Rumus TR/TC, hasil 2,76; narasi menulis Rp2,26 | `TR/TC` | Gunakan formula, catat salah ketik narasi; bukan alasan mengubah source menjadi 2,26. |
| B/C | Judul tertulis Penerimaan/Biaya Total, tetapi contoh memakai 15.583.900/8.851.100=1,76 | `B/C sederhana=(TR-TC)/TC` | Source konsisten dengan contoh laba/biaya. Jelaskan “B/C sederhana”, bukan discounted B/C; tidak menggandakan R/C. |
| BEP | Kesimpulan hal.22: 327,8 kg dan sekitar Rp9.780/kg | `BEP_Q=TC/P`, `P=TR/Q terjual`; `BEP_PRICE=TC/Q terjual` | Sesuai angka studi dengan asumsi biaya tercatat tetap. Jangan mencampurnya dengan BEP margin kontribusi `FC/(P-VC/unit)`. |
| NPV | Tabel 7: jumlah PV 80.270.654,42; saldo terakhir -219.729.345,58; subtotal/narasi -139.458.691,16 | `-I0 + Σ CFt/(1+r)^t` | Dengan I0 300 juta dan jumlah PV tabel, hasil -219.729.345,58. Subtotal terlihat menambahkan PV dua kali. Jangan menjadikan subtotal salah sebagai oracle. |
| Diskonto/IRR | Hal.20 menyebut 0,7116% per musim dan 12% “dalam satu musim”; hal.21 menyebut 12% tahunan dan IRR16,60% | Basis tahunan eksplisit; solver IRR dengan batas arus kas | Basis waktu makalah tidak konsisten. Unit r dan t harus sama. Urutan panen 4 GH bukan kalender tahun. Jangan konversi tanpa jadwal/periode yang disepakati. |
| PP/DPP | Lampiran hal.25 menyebut PP ketika saldo kumulatif PV melewati nol pada panen27 | PP dari CF; DPP dari discounted CF, pilihan interpolasi/akhir tahun | Lampiran menggunakan pendekatan discounted payback. Label PP dan DPP harus dipisahkan; tabel potongan tersebut tidak cukup membuktikan ulang semua proyeksi/IRR. |

Hitung ulang independen menggunakan Python Decimal, **bukan eksekusi engine aplikasi**:

| Input contoh studi | Hasil hitung ulang |
| --- | --- |
| FC 5.421.100 + VC 3.430.000 | TC Rp8.851.100,00 |
| TR 24.435.000 - TC | Laba Rp15.583.900,00 |
| TR/TC; Laba/TC | 2,76067381; 1,76067381 |
| TC/27.000; TC/905 | 327,818519 kg; Rp9.780,220994/kg |
| -300.000.000 + 80.270.654,42 (jumlah PV tercetak) | -Rp219.729.345,58 |

Angka ini hanya fixture pembanding. Tidak mengimpor jumlah tanaman, harga, diskonto, biaya atau target studi menjadi data greenhouse pengguna. `SeasonEvaluation.php` dan `InvestmentEvaluation.php` sudah mengikuti rumus eksplisit; utamakan uji regresi dan kejelasan input/tampilan, bukan penggantian engine tanpa kegagalan terbukti.

## Logika bisnis yang perlu dibedakan

1. **Diterima server, sesuai acuan dan valid oleh pengelola adalah tiga hal berbeda.** EC di luar target bisa merupakan pengukuran yang benar. Pengelola dapat menilai catatannya valid sambil menandai kondisi budidaya perlu tindakan. Nilai di dalam target tidak membuktikan pengukuran dilakukan dengan benar.
2. **Populasi tanaman, target buah dan panen aktual memiliki satuan/basis berbeda.** Contoh 160 target buah dan 150 layak berarti capaian 93,75% dan selisih 10 buah. Selisih itu belum tentu gagal/cacat jika panen belum selesai. Jangan menyamakan 160 tanaman dengan 160 buah tanpa target eksplisit.
3. **Varietas/tanggal/fase milik periode tanam.** Greenhouse bisa berganti varietas pada musim berikutnya; histori sebelumnya harus tetap benar. Fase aktual disimpan bertanggal, bukan dipindahkan otomatis karena HST.
4. **Valid/tidak valid tidak menghapus hasil pengamatan.** Koreksi menghasilkan versi baru untuk diperiksa; keputusan versi lama tetap ada. Tidak valid bukan pembatalan panen/keuangan atau penolakan HTTP.
5. **“Semua greenhouse” tetap scoped.** Akun pengelola bukan izin membaca seluruh usaha, mengganti akun di luar kewenangan, atau mereset akun milik orang lain tanpa guard yang berlaku.

## Review frontend: Impeccable Operate dan antislop during

Metode: review source native dan dua screenshot historis; detector web tidak berlaku untuk Compose. Ini audit kesesuaian tugas, bukan skor aksesibilitas atau sertifikasi visual seluruh aplikasi.

Arah existing dipertahankan: forest/sage/krem, SansSerif, botani hanya pada login/Beranda, panel angka/form solid dan kaca selektif pada navigasi. Dials Beranda/menu 2/2/2, form/detail/laporan 1/2/2 mengikuti DESIGN.md. Fokus utama tiap layar adalah konteks greenhouse, catatan yang perlu keputusan, simpan kegiatan, hasil panen, atau hasil evaluasi. Identitas kebun hadir melalui konteks dan satuan yang nyata, bukan dekorasi tambahan.

Yang sudah mendukung kerja: pilihan greenhouse, identitas pencatat/waktu, label penerimaan server, pemisahan kas dan evaluasi, kontrol native, status kosong, serta detail multigrade. Screenshot panen menunjukkan informasi nyata tetapi masih padat dengan desimal enam digit dan versi saldo; varietas/target yang diminta belum ditonjolkan.

| Prioritas | Temuan | Prinsip | Perbaikan |
| --- | --- | --- | --- |
| P1 | Antrean bermakna teknis tetapi diharapkan sebagai verifikasi pekerja | C-2, C-3, R-24/R-26 | Antrean pengelola berisi inbox kegiatan server; pertahankan pengiriman lokal melalui tab sekunder/menu Sinkronisasi. |
| P1 | Belum ada target-versus-hasil panen | C-2, R-17/R-38 | Tampilkan target bersumber, jumlah/bobot aktual dan capaian kumulatif. Bila target kosong, jangan tampilkan 0%. |
| P1 | Acuan Excel belum seluruhnya bisa direpresentasikan atau dikonfigurasi | C-2/C-5, R-27 | Form sesuai pekerjaan, optional measurement, snapshot sumber; status acuan kosong/ambigu terlihat. |
| P2 | Status hari ini jauh dari kartu angka lama | C-4, R-17/R-27 | Dekatkan “Belum ada pencatatan hari ini”, beri label catatan terakhir dan waktu WIB. |
| P2 | Kelola greenhouse/akun dan analisis keuangan kurang langsung ditemukan | C-3, R-15/R-24 | Label tugas eksplisit, akses Kelola greenhouse/Kelola akun, dan kelompok biaya/penjualan/evaluasi. Jangan menambah tujuh tab bawah. |
| P2 | Detail panen lebih menonjolkan presisi storage/versi daripada identitas tanaman | C-1/C-3 | Tampilkan varietas, total buah/kg dan status; hilangkan nol pecahan tak perlu hanya pada format, pertahankan nilai presisi penuh dalam data dan detail audit. |

Delivery Gate untuk dokumen ini: sumber/nilai/temuan ditelusuri, existing design dibaca, tidak membuat aset atau statistik produksi, rencana dipisahkan dari hasil. Gate build/click-through/kontras/TalkBack/tema/process death **belum dijalankan ulang**, wajib pada implementasi. Tidak mengklaim frontend baru sudah lulus.

## Prioritas pelaksanaan

1. Bekukan kontrak verifikasi dan pemetaan Excel, lalu bangun inbox pengelola sampai penerimaan dua akun nyata pada lingkungan uji.
2. Lengkapi pengamatan, acuan dan status hari ini. Reuse input pekerja/pengelola yang sama agar detail antrean tidak kehilangan data.
3. Tambahkan target dan rekap panen dengan varietas/periode yang benar.
4. Sederhanakan penyiapan greenhouse dan akses akun; regresi email/reset yang sudah ada.
5. Perjelas alur keuangan, koreksi catatan sumber dan uji angka studi. Pertahankan fixed decimal, snapshot, saldo, dan basis tahunan investasi.

Keputusan sebelum perilaku bisnis dipakai produksi: siapa boleh memverifikasi catatan pengelola sendiri; apakah ringkasan utama menghitung catatan belum diverifikasi; definisi target buah/grade layak dan kapan panen dinyatakan selesai; nilai agronomi aktif; basis alokasi biaya bersama serta periode investasi. Usulan aman dan pekerjaan yang tidak bergantung pada keputusan ini dijelaskan di master prompt. Permintaan menyusun prompt bukan persetujuan diam-diam terhadap semua usulan tersebut.

## Verifikasi paket dokumentasi

`scripts/check-finance-context.py` lulus: 57 dokumen aktif, 364 tautan, delapan hash arsip, cakupan KF14/KNF8/U21 tetap utuh. Tiga dokumen baru diperiksa penutupan code fence dan format teksnya; `git diff --check` bersih. Perubahan hanya paket Markdown dan indeks/status context, bukan source runtime, kontrak executable atau workbook/PDF sumber. Pemeriksaan ini tidak menggantikan acceptance implementasi PG-AC01 sampai PG-AC15.
