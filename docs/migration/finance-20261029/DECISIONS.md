# Decision register target baru

## Pengelola sebagai super admin, 11 November 2026

**Keputusan pengguna**, berdasarkan masukan dosen pembimbing (navigasi pengelola terlalu panjang, terlalu banyak kalimat, kata teknis, peran tidak jelas, alur tambah greenhouse tidak jelas, pengelola terkesan butuh validasi):

- Pengelola menjadi **super admin**: semua greenhouse (aktif dan nonaktif), semua akun, semua data master dan acuan, tanpa penugasan. Simpan langsung tanpa dialog "ajukan". Alasan perubahan opsional kecuali "Perlu diperbaiki", recovery dan pembatalan lampiran.
- UI pengelola mengikuti **Versi B** (berpusat pada greenhouse): empat tab Beranda, Riwayat, Keuangan, Kelola; tombol Catat; indikator belum terkirim; halaman detail greenhouse; wizard tiga langkah.
- Perilaku, izin dan UI pekerja **tidak berubah**, kecuali kosakata komponen bersama yang mengikuti glosarium.
- Aturan lama di `CLAUDE.md` ("Pengelola bukan superadmin global", "Jangan menambah hak global") **digantikan** oleh keputusan ini.

**Batas yang tetap (integritas data, bukan izin):** tidak ada hard delete histori, ledger, audit atau snapshot; laporan terbit tidak bisa diedit; panen yang terjual tidak bisa dikurangi di bawah jumlah terjual; alokasi biaya dan penyusutan tidak boleh melebihi saldo; musim tertutup harus dibuka dulu; pengelola tidak boleh menonaktifkan atau menurunkan peran dirinya sendiri dan harus ada minimal satu pengelola aktif; perubahan akun mencabut sesi akun itu; backup/restore tetap kapabilitas operator di konfigurasi server; konflik versi tetap ditangani.

**Keputusan teknis yang diambil, bukan diminta pengguna:**

- Penugasan baru hanya untuk akun pekerja. Penugasan pengelola yang sudah ada tetap sah sebagai data.
- Pembatalan lampiran tetap mewajibkan alasan (tidak termasuk daftar alasan opsional; memilih sisi yang menjaga data).
- Wizard memakai pengiriman berurutan yang butuh koneksi dengan progres per langkah dan operasi yang dipakai ulang saat coba lagi (opsi b di master prompt 5.3). Alasan: seluruh tulis administrasi sudah online-only lewat jurnal admin, bukan outbox offline, sehingga opsi ini tidak menurunkan kemampuan; endpoint komposit (opsi a) tidak dipilih karena akan mengubah kontrak tanpa kebutuhan offline. Greenhouse hanya punya field `name` di API, jadi langkah 1 hanya nama. Varietas milik satu greenhouse, sehingga greenhouse baru hanya menambah varietas baru.
- Tombol Catat untuk biaya dan penjualan tanpa greenhouse terpilih membuka pemilih greenhouse dulu.

**Dampak pada proposal:** PDF proposal tidak dibaca ulang pada pekerjaan ini dan tidak diubah. Butir KF/KNF/U yang menyebut pembatasan peran atau akses per greenhouse bagi pengelola kemungkinan terdampak dan **perlu pengecekan formal**; tidak ditemukan butir yang jelas bertentangan di dokumen konteks yang dibaca. Lihat [bukti](../../context/MANAGER_SUPERADMIN_EVIDENCE_20261111.md).

## Scope audit dan handoff, 10 November 2026

Pengguna meminta audit proposal/database/frontend/backend/API/context, mengizinkan inspeksi database lokal read-only, memilih **audit dan update context dulu**, lalu meminta `CLAUDE.md` berisi konteks, role/kewenangan, API, stack dan database. Hasil ada di [audit sistem](../../context/SYSTEM_AUDIT_20261110.md) dan [CLAUDE.md](../../../CLAUDE.md). Tidak ada izin baru untuk migrate database pengguna, perbaikan runtime atau deploy dalam task audit ini. Temuan schema lokal tertinggal dan OpenAPI belum lengkap dicatat sebagai pekerjaan lanjut.

## Keputusan penyelarasan pengelola, 10 November 2026

- Kartu EC/PPM boleh menampilkan catatan pekerja yang sudah diterima server dan belum diperiksa dengan label **Belum diverifikasi**. Catatan tidak valid tetap tersedia di riwayat.
- Pencatat memilih **layak** atau **cacat** per rincian panen. Grade yang sama dapat memiliki klasifikasi berbeda di panen lain.
- Pengelola menandai **Panen selesai** secara manual dengan alasan. Penutupan musim adalah tindakan terpisah.

## Keputusan terkini yang direkonsiliasi, 8 November 2026

Ringkasan keputusan berikut berasal dari chat proyek yang dibaca pada [audit serah terima](../../context/DOCUMENTATION_AUDIT_20261108.md), dibandingkan dengan source/kontrak. Bukan persetujuan baru terhadap seluruh Q terbuka.

- Pengguna melanjutkan FIN16–17 sesudah FIN11–15 teman, lalu meminta integrasi branch. PR1–46 terkait rangkaian finance/IoT/frontend/dokumentasi sudah masuk main sampai `0ea7f66`. Pembatasan lama berhenti FIN10/FIN15 dan push ditunda di bawah merupakan histori. Beranda `d6774fc` dipush tetapi belum masuk main saat fetch audit.
- 7 November: gunakan dua ilustrasi lokal `assets/background login.jpg` dan `assets/background dashboard.jpg`, dengan identitas forest/sage/krem, kaca selektif, panel solid dan adaptasi font/IME. Rencana foto internet sebelumnya digantikan keputusan aset ini.
- 8 November: pengguna menjelaskan empat greenhouse dan satu varietas/waktu tanam bersama per greenhouse pada praktik lapangan. Alur harian pengelola cukup memilih greenhouse; awalnya semua greenhouse dalam penugasan. Jumlah greenhouse tetap berbasis data, histori musim/kelompok dipertahankan. Periode berjalan ditetapkan eksplisit/diaudit, tidak disamakan dengan musim open. [Kontrak executable](../../../contracts/v2/MANAGER_HOME.md).
- Perhatian harus berdasar bukti dan waktu data. Kas bulanan adalah penerimaan penjualan lunas dan pembayaran aktual, bukan laba atau alokasi/penyusutan. Aturan tenggat/completion dan biaya bersama lintas greenhouse tidak otomatis disahkan.
- Impeccable dan antislop DURING diminta untuk UI, dengan motion singkat yang menghormati pengaturan Android. Preferensi DURING disimpan pada laptop; periksa skill pada sesi baru.
- Pengguna pernah meminta main diperlakukan sebagai jalur deploy/rilis, tetapi pekerjaan Beranda secara eksplisit tidak mengizinkan deploy atau merge otomatis. Penerimaan UAT/pentest/produksi tetap terpisah.

Untuk keadaan siap lanjut gunakan [HANDOFF 8 November](../../context/HANDOFF_20261108.md). Tabel D01–D10 di bawah merekam keputusan audit FIN00; D04 audit-only dan D05 mempertahankan source legacy sudah digantikan instruksi implementasi/penghapusan source berikutnya. Perlindungan data/histori tetap berlaku.

## Keputusan pengguna 2 November2026 — penghapusan IoT dan dashboard manual

Pengguna meminta pencatatan menjadi satu-satunya produk aktif, penghapusan source/runtime/tooling IoT dan prototipe monitoring setelah shared dependencies dipisahkan; tabel/migration/histori/provenance tetap. Finance menjadi default tanpa flag dengan identitas/penyimpanan finance dipertahankan. Ini otorisasi implementasi reversible, bukan deploy/drop/clear data/cutover.

Pengukuran adalah kegiatan tersendiri tanpa bahan, EC mS/cm + PPM ppm wajib lengkap untuk submit, draft boleh parsial, pH manual/histori tetap. Tidak konversi, backfill nol, ambang/faktor agronomi. Dashboard tepat dua kartu dari satu record valid per GH/musim/kelompok: waktu ukur WIB hari ini→kemarin→sebelumnya; urutan occurred_at lalu UUID descending. Legacy EC tidak tersedia eksplisit; empty bukan0. Queued lengkap berlabel belum tersinkron; draft/cancel/rejected/conflict/hold dikecualikan. [Kontrak](../../../contracts/v2/MEASUREMENTS.md), [inventaris/AC](MANUAL_MEASUREMENTS_20261102.md).

Status pelaksanaan: [IMPLEMENTATION_STATUS](../../context/IMPLEMENTATION_STATUS.md). Q10 angka disetujui pengguna saat pelaksanaan (IDR, nominal20,2, kuantitas/harga20,6, HALF_UP total); solver tetap belum disahkan. Pengguna juga mengoreksi publikasi ke remote GitHub `github`; otorisasi ini mengatasi tujuan GitLab operasional sebelumnya, bukan konfirmasi gate pengumpulan akademik. Baris audit FIN00 di bawah dipertahankan sebagai histori, bukan status executable terbaru.

## Keputusan dan checkpoint 30 Oktober–2 November (historis)

Pengguna memperluas batas pengerjaan sampai FIN15 pada2 November; push tetap ditunda. FIN15 mengimplementasikan backup manual dan drill restore hanya pada MySQL testing terisolasi, tidak memberi nilai/jadwal/retensi/RPO/RTO/operator produksi Q08 atau izin production apply. Kewenangan backup seluruh sistem adalah kapabilitas server terpisah dari peran/assignment greenhouse, default kosong; app tidak menerima dump/credential.


FIN11 technical freeze30 Oktober: `anniversary-cumulative-half-up-v1`, tahun pemakaian berdasarkan ulang tahun tanggal mulai pakai (leap day diklem dari anchor asli), periode [start,end), anggaran sen tahunan selisih kumulatif HALF_UP sehingga total tepat A−S. Ini konvensi teknik bagi alokasi manual Q02, bukan nominal/jadwal produksi. Pengguna menunda PDF proposal asli dan meminta melanjutkan dari checkpoint; verifikasi sumber PDF tetap terbuka. [Kontrak](../../../contracts/v2/ASSETS.md), [evidence backend/math](evidence/FIN11-backend.md). Native FIN11 dan FIN12–17 belum lengkap.

Checkpoint30 Oktober 2026: pengguna meminta berhenti aman setelah perubahan FIN10 diverifikasi, commit/push GitHub dan handoff **FIN11–FIN17**. Permintaan ini menggantikan instruksi sebelumnya untuk menyelesaikan FIN11 sebelum handoff. FIN11 belum diimplementasikan.

Q02 disetujui pengguna30 Oktober 2026: **alokasi penyusutan manual eksplisit**. Pengelola wajib mengisi nominal, periode pemakaian, referensi pemakaian unik, dan alasan/basis pembagian. Server membatasi anggaran tahunan dan kumulatif A−S, tanpa pembagian rata otomatis. Ini persetujuan pendekatan FIN11, belum bukti kode/test penyusutan. Detail kontrak tahun/precision/rounding tahunan harus dibekukan saat FIN11 dan dilabeli; jangan menganggap semua kebijakan Q02 lainnya otomatis disahkan.

Q07 diperjelas pengguna pada30 Oktober 2026: **"Konfigurasi produksi belum ditetapkan"**. Usulan JPG/PNG/PDF5MB dan5 berkas belum disahkan sebagai nilai produksi. Implementasi akses privat/configuration dan uji dengan batas fixture tetap dapat dilanjutkan; jangan mengaktifkan upload produksi memakai angka usulan tersebut secara otomatis.

| ID | Keputusan | Dasar/status |
|---|---|---|
| D01 | Proposal keuangan 60 halaman menjadi baseline aktif | Instruksi pengguna29 Oktober 2026 |
| D02 | Kotlin + Jetpack Compose, Laravel REST, MySQL | Pengguna eksplisit; tidak ganti stack |
| D03 | Operasional dan keuangan tanpa IoT, dua peran | P §1.3, KF01–14; mengatasi scope owner-only/ESP32 lama |
| D04 | Task saat ini audit, context pack dan master prompt | Deliverable turn ini; implementasi executable disusun untuk dimulai lewat prompt |
| D05 | Pertahankan source/data legacy; tidak auto-convert event | Keputusan engineering untuk menjaga provenance dan data pengguna |
| D06 | Namespace v2, schema additive, transaksi+audit+receipt | Rancangan teknik untuk dibekukan FIN01, bukan kutipan endpoint proposal |
| D07 | Test backend hanya MySQL inofarm_test | Instruksi proyek dan P §3.4; guard tidak dinonaktifkan |
| D08 | Setiap fitur/bug/config/doc task branch codex/ terpisah | Preferensi pengguna; branch task sekarang codex/finance-proposal-context |
| D09 | Penjualan lunas sebagai model awal; proyeksi terpisah aktual | Proposal; kebutuhan konfirmasi mitra tidak dihapus |
| D10 | Production defaults business kosong, fixtures terpisah | Proposal: target, dosis, diskonto, harga dan data contoh bukan nilai produksi |

Izin Git 15 Oktober untuk publikasi yang disebutkan tetap historis berlaku sesuai scope; jangan menganggapnya izin deploy, force push, mengganti remote, atau menggabungkan fitur berbeda. Task ini tidak commit/push karena perubahan context sebelumnya belum dipisahkan menjadi commit dan pengguna meminta paket perencanaan, bukan publikasi.

## Keputusan terbuka dan pekerjaan independen

| ID | Pertanyaan / pemilik | Menahan | Yang tetap boleh dikerjakan |
|---|---|---|---|
| Q01 | Mitra: penjualan selalu lunas? retur/kerusakan/pembatalan bagaimana? | Perilaku retur/kredit dan acceptance penjualan | Model awal paid-only, saldo, test fixture; jangan tambah piutang |
| Q02 | Mitra: dasar alokasi biaya dan penyusutan per waktu/proporsi/musim? | Otomasi alokasi produksi | Alokasi eksplisit + basis, plafon concurrency, penyusutan dasar |
| Q03 | Mitra: PP/DPP interpolasi atau akhir tahun? actual/projection sourcing? | Default laporan investasi resmi | Kedua strategi berlabel/configured; fixture interpolasi proposal |
| Q04 | Mitra: kolom wajib per aktivitas, satuan, target/toleransi, jadwal selesai bertahap? | Formula agronomis/completion produksi | Schema/form dinamis, draft, comparator null/single/range, tidak auto-complete |
| Q05 | Pengelola: petugas berbeda pencatat; sebelum semai; edit/batal panen terjual? | Perilaku kasus belum ditentukan | Creator dari sesi, koreksi sendiri saat open, saldo negatif ditolak |
| Q06 | Pengelola: logout/offline expiry, lama offline, retensi draft/receipt? | Purge atau batas offline resmi | Isolasi akun, hold queue, tidak hapus draft otomatis; retry idempoten |
| Q07 | Pengelola: batas/jenis/jumlah lampiran; kebijakan file wajib? | Konfigurasi produksi upload | Access layer privat + field config + rejection test dengan nilai fixture |
| Q08 | Pengelola: jadwal/retensi backup, RPO/RTO, operator restore? | Operasional produksi | Backup/restore staging dan manifest/checksum; maintenance/epoch design |
| Q09 | Tim: import data IoT/demo, mapping akun/greenhouse, retensi legacy? | Import/drop/cutover data nyata | Schema additive, importer dry-run bila diminta, data legacy tetap |
| Q10 | Tim: kebijakan scale/range/rounding/currency dan solver IRR? | Freeze kontrak numerik | Eksplorasi fixture dan test oracle; tidak silently round |
| Q11 | Mitra: toleransi non-nominal dan UAT90% disahkan? | Pernyataan acceptance resmi | Uji dan lapor hasil terhadap usulan secara eksplisit |
| Q12 | Pembimbing: pengecualian Android-only; CRUD via pembatalan historis? | Klaim SOP/handover | Implementasi Android dan audit trail sesuai scope |
| Q13 | Tim/mitra: tanggal mulai, deadline, PIC, perangkat/peserta UAT, bukti lapangan? | Timeline kalender dan laporan akademik final | Rencana dependensi, environment check, synthetic tests |

## Konflik/editorial proposal

- PDF2 halaman pengesahan masih berjudul IoT lama; PDF18 paragraf sesudah KNF masih menyebut monitoring realtime. §1.3 dan arsitektur baru secara eksplisit tanpa IoT; baseline produk mengikuti bagian itu, tandai sisa teks untuk revisi akademik.
- PDF54 pembuka alat/bahan masih menyebut monitoring/kalibrasi; tabel alat sudah Android/backend. Tidak menjadi perintah membangun sensor.
- PDF31 Gambar2.8 diberi judul ERD Operasional padahal gambar keuangan; rujuk isi/halaman, jangan mengganti konsep menjadi operasional.
- PDF56 U12/U13 merujuk Tabel2.12/2.13, sedangkan fixture aktual Tabel2.8/2.9 PDF44/46–47. Gunakan fixture aktual.
- Penomoran cetak/daftar isi tidak monoton (PDF39 cetak34, PDF41 cetak29). Semua rujukan paket ini memakai urutan PDF.
- Investasi disebut dalam narasi ERD tetapi diagram keuangan yang tersedia belum menggambarkan seluruh investasi/sync/audit. Model teknis harus melengkapinya sebagai derivasi, bukan mengklaim diagram lengkap.

Tidak mengedit PDF sumber pada task ini. Konflik bukan alasan menghentikan pekerjaan yang tidak bergantung pada keputusan tersebut.
