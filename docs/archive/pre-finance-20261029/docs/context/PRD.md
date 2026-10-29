# PRD Monitoring dan Pengingat Nutrisi Melon NFT

## Kelengkapan diagram proposal — 27 Oktober 2026

Tambahan: flowchart utama gabungan `17-flowchart-sistem-lengkap` telah dirender dan diperiksa visual; mencakup tiga flowchart terdahulu dan ringkasan sinkronisasi. Galeri menunjuk gambar gabungan sebagai pilihan utama; tidak ada perubahan kontrak atau implementasi.

[Peta kelengkapan](../design/proposal-revisi-20261026/KELENGKAPAN-DIAGRAM.md) mencatat Activity Diagram baru dan pemisahan user flow pekerja/pengelola mengikuti urutan proposal Monitoring. Tiga PNG/SVG tambahan berhasil dirender dan diperiksa visual; isi tetap rancangan revisi tanpa IoT. Tidak mengubah kebutuhan, kontrak API, implementasi, atau gate persetujuan mitra. DOCX dan arsip paket lama belum disusun ulang.

## Usulan revisi proposal V1 — 26 Oktober 2026

Pengguna meminta naskah revisi akademik dan gambar yang lebih ringkas. [Paket revisi](../design/proposal-revisi-20261026/README.md) memuat BAB I–III, peta penggantian, 13 diagram Mermaid dan penjelasan. Ini **usulan dokumen**, bukan pengesahan requirement baru atau implementasi. Kotlin/Laravel/MySQL dan target tanpa IoT dipertahankan; bukti mitra, kebijakan keuangan, parameter produksi dan keputusan SOP yang belum tersedia ditandai untuk dilengkapi. KF-01–14 dipetakan pada rancangan dan 21 skenario uji, tanpa klaim uji aplikasi telah lulus.

## Instalasi tim dan baseline GitHub — 24 Oktober 2026

Pengguna mengotorisasi first commit snapshot ke GitHub pribadi beserta panduan [Cara Instalasi](../../Cara%20Instalasi.md) dan proteksi file sensitif. Tidak mengubah kebutuhan produk; source runnable masih implementasi lama dan rancangan tanpa IoT belum diimplementasikan. Evidence persiapan: [GITHUB_PUBLICATION](../implementation/GITHUB_PUBLICATION.md).

## Rancangan ulang tanpa IoT — 21 Oktober 2026

Pengguna menyetujui target baru berupa aplikasi pencatatan harian Android tanpa IoT, tetap Kotlin + Laravel + MySQL, untuk beberapa greenhouse. Pekerja memiliki akun untuk jadwal/input kegiatan/panen/riwayat dan koreksi catatan sendiri saat musim terbuka; keuangan hanya pengelola. Pengelola membuat akun, mengatur penugasan GH dan membatalkan/membuka musim dengan alasan. Nama musim bebas diubah; identitas tetap, kelompok varietas memiliki tanggal semai/pindah tanam sendiri dan HSS/HST mulai 1. Penjualan lunas bertahap dari beberapa panen satu musim disetujui sementara; keuangan akan dikonfirmasi lagi. Acuan kebutuhan baru KF-01–14 berasal dari PDF proposal baru, bukan penomoran KF pada proposal IoT lama. Lihat [paket diagram proposal](../design/mobile-greenhouse-20261021/README.md). Bagian sebelumnya di bawah adalah riwayat scope/implementasi lama. Task ini membuat diagram, bukan merombak source aplikasi.

## Data demo lokal — 20 Oktober 2026

Pengguna mengotorisasi riwayat dummy tanpa IoT dan akun demo terpisah untuk inspeksi database/aplikasi. Data lokal 10–19 Oktober:30 record+30 telemetry pada tandon DEMO ID2; angka/jadwal fixture bukan requirement agronomis. KF/KNF dan produksi kosong owner asli tetap. Detail [DEMO_DATA](../implementation/DEMO_DATA.md).

## Publikasi Git per fitur — 15 Oktober 2026

Pengguna mengotorisasi commit per fitur dan push ke GitLab UMY `andika.arya.ft23/inofarm_jogja`, memakai identitas Andika Arya Putra dan email kampus yang diberikan. Riwayat remote `main` beserta konfigurasi SAST/Secret Detection dipertahankan; impor snapshot lokal dikerjakan pada branch `codex/project-import` dan disusun menjadi commit berdasarkan fitur. Ini pengelompokan source yang sudah ada, bukan rekonstruksi tanggal pengerjaan. Environment, credential, cache IDE, runtime, log dan artefak build tidak dipublikasikan. Verifikasi lokal dan prosedur ada di [GIT_WORKFLOW](../implementation/GIT_WORKFLOW.md); tidak mengubah requirement/kontrak/aturan agronomis dan tidak menutup gate hardware, pentest atau handover.

## Koreksi UI/navigasi — 15 Oktober 2026

Keluhan indikator Filter dan Dashboard kembali ke Pengaturan direproduksi pada OwnerApp nyata dengan fixture terisolasi, lalu diperbaiki dan diuji lokal. Pilihan Hari ini/7 hari/Filter dipisahkan dari draft/applied query; Filter selalu membuka panel, Terapkan/Reset tetap eksplisit, shortcut tanggal mempertahankan status yang sudah diterapkan. Ruang navigasi mengikuti tinggi aktual dan safe drawing/IME. Kontrol akhir Dashboard/Pengaturan dapat dijangkau pada matriks lokal; keluhan konten sama sekali tidak dapat diakses belum direproduksi pada baseline AVD. Tidak mengubah KF/KNF, kontrak atau rule bisnis. Bukti dan batas: VERIFICATION serta outputs/ui-navigation-20261015/README.md.

## Tooling membuka ulang — 15 Oktober 2026

Keluhan auto-run tidak terlihat ditangani lewat jendela launcher normal/petunjuk Running Devices/transcript dan konfigurasi IDE Inofarm MainActivity. Build/install/start ulang VERIFIED_LOCAL; tidak mengubah fitur/akun/data/requirement produk. Klik Run IDE/cold-start GUI masih terbatas bukti; VERIFICATION.

## Login/koneksi lokal — 14 Oktober 2026

Keluhan pengguna form API/koneksi emulator ditangani: alamat debug otomatis dan disclosure Pengaturan koneksi, login normal email/password, setup tetap terlihat bila alamat belum tersedia. Android17 meminta izin akses server komputer bila perlu. Login owner APK/backend asli tanpa reverse VERIFIED_LOCAL; konfigurasi/sensor awal tetap kosong. Tidak menambah requirement agronomis/KF/KNF atau domain produksi. Bukti dan batas di VERIFICATION.

## Progress launcher lokal — 14 Oktober 2026

Follow-up: owner lokal dibuat sesuai identitas/password yang diminta pengguna, role owner dan tandon server-side; login/dashboard API PASS. Tidak ada role admin baru, akun default dalam APK atau perubahan konfigurasi agronomis. Detail tanpa credential di VERIFICATION.

Permintaan pengguna auto-run Android dipenuhi dengan Run-Inofarm.cmd dan scripts/run-android.ps1: build/install/start debug lokal VERIFIED_LOCAL, halaman login emulator diperiksa. Launcher membuka Gradle root android dan mengaktifkan API lokal; owner tetap memasukkan akun dan URL existing. Tidak menambah requirement bisnis/KF/KNF, akun, data atau aturan produksi. Sync IDE selesai dan modul InofarmJogja.app dikenali; klik Run IDE dan cabang emulator cold-start belum diuji langsung; lihat VERIFICATION.

## Delta performa Android — 13 Oktober 2026

Otorisasi pengguna mencakup optimasi native, build/profiling lokal dan test; React, firmware dan perilaku backend tidak berubah. Owner melihat status pemulihan statis sebelum sesi/cache selesai diperiksa, sehingga tidak melihat login sesaat atau data akun lama. UI construction tidak membaca file/Keystore/JSON cache secara sinkron.

Riwayat online ditelusuri dalam jendela empat halaman (25 baris/halaman), dengan akses sebelumnya/berikutnya memakai cursor server. Cache offline tetap maksimal 500 catatan, bukan batas bisnis riwayat. Tren menyatakan hanya data dimuat; seluruh alasan, filter, urutan, clock/provenance, versi dan matching ACK tetap. Hero botanical dan liquid glass dipertahankan. Refresh resume/30 detik RESUMED tidak diubah; tidak ada TTL agronomis atau mutation queue baru.

Unit8, debug UI/state/correctness48 dan profile correctness/smoke/rendering22 PASS lokal. Sampel emulator menunjukkan pengurangan kerja Main/cache/bitmap; warm startup dan scroll belum terbukti membaik konsisten. Hasil pengukuran dan coverage profile pada [ANDROID_PERFORMANCE](../implementation/ANDROID_PERFORMANCE.md) dan [VERIFICATION](../implementation/VERIFICATION.md). KF/KNF/DER tidak diubah. Performa/kompatibilitas HP owner dan runtime API26 tetap NOT_VERIFIABLE.

## Implementasi visual disetujui — 12 Oktober 2026

Pengguna mengotorisasi PROMPT_IMPLEMENTASI_CODEX.md dan sembilan referensi Liquid Glass. Presentation Android kini mempunyai tiga tab Dashboard/Riwayat/Pengaturan, hub lima menu, empat editor konfigurasi, dan segment pekerja/shift. KF/KNF/DER bisnis tetap sama: owner-only, manual PPM ESP32, seluruh reasons, jadwal WIB dan backend-authoritative. Nilai contoh hanya instrumentation; tidak menjadi ambang/jadwal/roster produksi. Back menjaga draft; Batal memulihkan checkpoint halaman; save konfigurasi penuh/versioned dan fase aktif terpisah. Bukti build/runtime/comparison serta batas pada [UI_REDESIGN](../implementation/UI_REDESIGN.md), [VERIFICATION](../implementation/VERIFICATION.md). Hardware/pentest/reviewer/SOP dan field usability tetap terbuka.

## Redesign presentation Android — 11 Oktober 2026

Pengguna mengotorisasi redesign native dengan gpt-taste dan redesign-existing-projects. Hierarki beranda, grid sensor adaptif, PPM/alasan riwayat, navigasi berikon, form/feedback, serta token/motion Compose diterapkan. Fitur owner-only, PPM ESP32-only, konfigurasi awal kosong, backend-authoritative B dan seluruh reasons tetap sama. Perubahan visual mendukung KNF-02 dan flow KF-09/10; tidak menambah requirement agronomis, skor kesehatan atau fitur produksi.

Suite gabungan 20 UI/state dan unit8 lulus; tambahan animation-off13 lulus pada emulator API37. Font200%/280dp, keyboard, landscape, partial/offline/clock warning dan disclosure diperiksa. Bukti rinci [UI_REDESIGN](../implementation/UI_REDESIGN.md) dan [VERIFICATION](../implementation/VERIFICATION.md). Acceptance greenhouse, spoken TalkBack penuh, device performance, hardware/SOP/pentest/reviewer tetap terbuka.

## Addendum keputusan dan coverage T1–T7

Pengguna mengizinkan seluruh perbaikan audit dan menyetujui dashboard refresh saat resume/setiap 30 detik saat aktif, usia data tanpa TTL agronomis baru, serta cache baca privat terenkripsi Keystore schema 1 maksimum 500 riwayat per server/akun/tandon. Logout/401/known expiry membersihkan cache; pembukaan mencoba server dan data tersimpan dilabeli last-known. Batas file 2 MiB mempertahankan cache lama bila update terlalu besar. Ini keputusan teknis, tidak mengisi ambang/jadwal/roster produksi atau mengubah validasi B.

KF/KNF terkait kini memiliki regression editor, session/lifecycle, source-vs-receipt freshness, UI lazy/font besar, dan read-cache persistence. Assignment tetap pekerja terjadwal; reasons/snapshot immutable tetap tersedia. Bukti dan batas acceptance ada di [VERIFICATION](../implementation/VERIFICATION.md). Hardware/pentest/reviewer/SOP/Q-10 tetap terbuka; bagian audit awal di bawah bersifat historis.

> **Update implementasi 10 Oktober 2026:** pengguna telah mengizinkan coding Android/backend/firmware, testing dan debugging dalam scope. Catatan audit “belum ada izin/komponen” di bawah adalah baseline historis, bukan kondisi sekarang. Implementasi dan hasil lokal ada di [VERIFICATION](../implementation/VERIFICATION.md); kontrak aktual [API](../implementation/API.md), keputusan teknik [DECISIONS](../implementation/DECISIONS.md). Production configuration/roster/fase tetap kosong; fixture terpisah dari produksi. Validasi B menampilkan semua alasan, mismatch fase ikut status issues tanpa menolak/menimpa input. Hardware, agronomic operational values, Q-10 dan SOP/reviewer gates belum dinyatakan selesai.

Tanggal audit: 10 Oktober 2026, Asia/Jakarta. Dokumen spesifikasi dan analisis; bukan izin implementasi. Status berlaku hanya untuk workspace yang diperiksa. Pengguna mengonfirmasi belum ada proyek Android terpisah.

## 1. Sumber dan cara membaca bukti

| Kode | Sumber otoritatif | Lokator |
|---|---|---|
| P | `Aturan Main/Proposal Monitoring dan Pengingat Nutrisi Melon NFT Terbaru.docx` | Bab, subbab, tabel, dan nomor gambar |
| G | `Aturan Main/SOP GitLab UMY v2.pdf` | Halaman PDF 1–4, bagian 1–8, activity diagram h.4 |
| S | `Aturan Main/SOP Pengumpulan Capstone v2.pdf` | Halaman PDF 1–3, bagian 1–7, activity diagram h.3 |
| C | Source workspace, terutama `src/`, `package.json`, `vite.config.ts` | Path dan simbol; temuan terperinci di GAP_ANALYSIS |
| U | Instruksi audit dan konfirmasi pengguna | Arah Android native Kotlin; belum ada proyek native terpisah; database MySQL dikonfirmasi 10 Oktober 2026 |

Aturan akademik mengikuti G/S; kebutuhan produk mengikuti P; implementasi mengikuti C. Saran engineering diberi REC. Kebutuhan tambahan yang diturunkan dari narasi/diagram atau SOP diberi DER, tidak mengganti KF/KNF asli. Konflik dicatat pada Q, tidak diselesaikan diam-diam.

Seluruh teks tubuh, tabel, daftar istilah, daftar pustaka, dan bagian lampiran P diekstraksi dari OOXML; 19 media diekstraksi, termasuk 17 gambar desain/timeline yang diperiksa secara visual. Dua media awal adalah lambang UMY. Bagian lampiran setelah heading tidak berisi materi substantif. Header/footer dan footnote/endnote diperiksa secara struktural; tidak menambahkan persyaratan. Halaman pengesahan memiliki tanggal persetujuan kosong sehingga persetujuan formal tidak terverifikasi.

Nomor halaman P dalam daftar isi/daftar gambar hanya **indeks internal belum diverifikasi dengan render halaman**: Tabel 2.3 h.13; Tabel 2.4 h.14; Gambar 2.1–2.14 berindeks h.15,16,18,20,21,22,23,25,26,29,30,31,32,32. Daftar isi mengandung urutan halaman yang tidak konsisten (Bab III h.28 setelah wireframe h.29–32). Gunakan nomor bagian/tabel/gambar sebagai lokator utama. Bundled LibreOffice tidak tersedia pada runtime Windows sehingga layout/paginasi seluruh DOCX tidak diverifikasi; gambar desain tetap diperiksa langsung, bukan hanya caption. Semua tujuh halaman kedua SOP diekstraksi dan dirender lalu diperiksa, termasuk diagram yang tidak terbaca melalui ekstraksi teks.

## 2. Produk dan tujuan

Judul proposal: **Monitoring dan Pengingat Pemberian Nutrisi Kebun Melon Hidroponik Nutrient Film Technique Berbasis Internet of Things**. Mitra pengambilan data adalah Inofarm Yogyakarta (P §2.1.1, Tabel 2.1). Melon dalam greenhouse NFT bergantung pada kedisiplinan pekerja dalam pemberian nutrisi, pengukuran PPM, dan pencatatan. Pengelola kesulitan memastikan waktu, fase tanaman, hasil pengukuran, dan kondisi lingkungan dari catatan manual yang tidak terpusat (P §1.1–1.2).

Tujuan P §1.4: perangkat ESP32 memberi pengingat serta menerima fase/PPM di lapangan; dashboard terintegrasi memberi pengelola riwayat, hasil validasi, dan kondisi greenhouse. Hasil yang diharapkan adalah transparansi operasional dan evaluasi historis, bukan bukti otomatis bahwa tindakan fisik benar-benar dilakukan. Catatan mengandung klaim pekerja dan hasil ukur handheld, bukan pengukuran dosis otomatis.

| Stakeholder/aktor | Tanggung jawab dan media |
|---|---|
| Pekerja greenhouse | Memberi nutrisi fisik, menunggu tercampur, mengukur EC/PPM handheld, memilih fase/input PPM melalui LCD dan tombol perangkat |
| Owner/pengelola | Melihat dashboard, sensor, PPM manual, riwayat/status, mengevaluasi dan mengatur jadwal/parameter |
| Tim Capstone | Hardware, firmware, backend, dashboard, integrasi, pengujian, dokumentasi |
| Pembimbing/reviewer UMY | Klarifikasi rancangan/konflik, pemeriksaan SOP dan keluaran akademik |

P Gantt Capstone 2 membagi IoT kepada Syafito Denova; backend/data kepada Razzin Ayyuri; UI/web kepada Choirul Siregar; mobile kepada Choirul Siregar dan Andika Arya; pengujian/laporan kepada tim. Ini rencana penanggung jawab, bukan bukti kontribusi aktual.

## 3. Lingkup dan batas sistem

### Keputusan scope pengguna — 10 Oktober 2026

- Target akhir **Android native Kotlin saja**; prototipe React hanya referensi tampilan APK mobile, bukan dashboard web yang akan dikembangkan sebagai produk.
- Scope pengerjaan Codex yang direncanakan: Android, backend dengan **MySQL**, serta firmware ESP32 dan kontrak/API output ESP32 ke backend dan aplikasi. Belum ada implementasi baru yang diverifikasi atau coding yang dijalankan pada turn klarifikasi ini.
- Pencatatan PPM tahap sekarang **hanya lewat ESP32**. Tombol/form input PPM pada referensi React tidak otomatis diport ke Android.
- Subfitur input harian greenhouse adalah rencana berikutnya, belum termasuk implementasi sekarang. Field, aktor, waktu, media input dan hubungannya dengan catatan nutrisi belum ditentukan; jangan menyamakannya dengan input PPM Android.
- Kotlin dikonfirmasi untuk aplikasi Android; backend **Laravel/PHP + REST API** telah disetujui pengguna. Database **MySQL**; toolchain firmware dan versi/environment belum ditentukan.
- Keputusan target produk tidak menyatakan SOP multi-platform telah dipenuhi atau pengecualian pembimbing telah diberikan. Bukti penerapan akademik tetap terbuka di Q-01/Q-13.

Klarifikasi lanjutan pengguna pada tanggal yang sama:

- Android hanya digunakan **owner/leader greenhouse**; pekerja memberi nutrisi dan input melalui perangkat IoT, tidak memiliki login Android pada tahap sekarang. Hak rinci owner/leader dan provisioning akun/perangkat belum ditentukan.
- Tahap awal **satu ESP32 dan satu tandon/kolam nutrisi**. Pengguna menyebut empat sensor; kontrak monitoring tetap empat parameter (suhu air, pH, suhu ruangan, kelembapan). Jangan menyimpulkan empat modul fisik atau menambah sensor: proposal DS18B20 + pH + DHT22 menghasilkan empat nilai; BOM/model fisik tetap Q-09.
- Pengguna meminta **pengelolaan jadwal pekerja berdasarkan hari dan shift pada aplikasi**, untuk mengaitkan waktu input/button perangkat dengan pekerja yang dijadwalkan, dan menyerahkan rancangan yang sesuai kepada agen. Masuk rencana implementasi TASK-027, terpisah dari future input harian greenhouse. Rancangan derivasi dijelaskan §14; jam/hari/nama pekerja operasional tetap diisi owner, bukan default buatan. Nama assignment tidak membuktikan siapa penekan tombol dan tidak boleh diklaim authenticated author.
- Q-02: pengguna memilih **B — validasi gabungan** untuk tahap awal. Catatan hanya dinyatakan valid bila pemeriksaan jadwal/fase/PPM dan sensor terkait memenuhi aturan yang disahkan. Sensor abnormal menghalangi hasil valid; hasil setiap pemeriksaan tetap ditampilkan terpisah. Pengguna mengizinkan evaluasi perubahan setelah testing, bukan perubahan rule otomatis. Ambang, snapshot/usia bacaan, missing sensor, pending dan prioritas status masih membutuhkan keputusan. Frasa “seumumnya hidroponik NFT” bukan formula/ambang yang disahkan.
- Backend **Laravel (PHP), REST API, MySQL** telah disetujui pengguna; versi/toolchain/hosting diperiksa sebelum implementasi. Laravel menyediakan MySQL, migrations, validation dan mobile API authentication: [database](https://laravel.com/docs/13.x/database), [migrations](https://laravel.com/docs/13.x/migrations), [validation](https://laravel.com/docs/13.x/validation), [Sanctum](https://laravel.com/docs/13.x/sanctum), diperiksa 10 Oktober 2026. Persetujuan stack tidak membuktikan service telah dibuat.

**In scope:** empat nilai sensor (suhu air, pH, suhu ruangan, kelembapan), tampilan LCD, jadwal berbasis RTC, alarm buzzer, fase vegetatif/generatif, input PPM manual, konfirmasi input, riwayat terpusat, validasi waktu/fase/PPM, dashboard owner, parameter/jadwal owner, MicroSD fallback, dan pengujian integrasi. Login disebut eksplisit pada P §3.1 pengembangan software meskipun tidak masuk Tabel 2.3.

**Out of scope:** automated nutrient dosing, pompa peristaltik, sensor TDS/PPM otomatis, sensor tambahan, metode hidroponik selain NFT, lahan terbuka, prediksi Machine Learning. ML muncul sebagai saran masa depan, bukan deliverable sekarang. Diagram tandon tidak mengotorisasi aktuator tambahan.

Hardware P §1.3/Tabel 3.1–3.2: ESP32 DevKit V1, DS18B20 waterproof, DHT22, PH-4502C, RTC DS3231, LCD 20x4 I2C, empat push button, buzzer aktif 5V, LED merah/hijau, adaptor 5V 2A, MicroSD module/card, resistor/pull-up, perfboard, box ABS waterproof, cable gland, buffer pH dan aquades. Desain Gambar 2.14 mencantumkan DHT22/SHT31, sedangkan batasan hanya DHT22: Q-09. Skematik bukan pinout siap diproduksi: Q-08.

Dependencies: perangkat fisik/kalibrasi, jaringan WiFi greenhouse, backend/database, kontrak data dan otorisasi, parameter owner yang disahkan, akses GitLab kampus. MySQL/Firebase dan Laravel/Node.js/Firebase adalah contoh proposal. Pengguna menetapkan **MySQL** pada 10 Oktober 2026; Laravel/PHP disetujui; versi/environment MySQL, schema dan deployment belum diputuskan. REST API direncanakan P §3.1; endpoint aktual belum ada. Arah Kotlin berasal dari U; proposal menyebut web/mobile dan Gantt memuat keduanya. Keputusan platform mengikuti Q-01.

## 4. Kondisi awal dan definisi status

Tidak ada `app/`, file Kotlin fisik, AndroidManifest, Gradle wrapper/build, version catalog, ViewModel, Room, API client, firmware, database/schema, server backend, maupun test suite pada workspace. Ada React 19/Vite/Tailwind dan tujuh template (enam `.kt`, satu Gradle) dalam `src/utils/kotlinTemplates.ts`. Nilai SDK 35/26/35 hanyalah teks template, bukan konfigurasi proyek aktif. Lihat ARCHITECTURE dan GAP_ANALYSIS.

Status: IMPLEMENTED = requirement terpenuhi dan diverifikasi; PARTIALLY_IMPLEMENTED = sebagian perilaku ada, integrasi/lingkup belum; UI_ONLY = hanya tampilan; MOCKED = berasal dari fixture/simulasi; MISSING = komponen tidak ditemukan dalam workspace; INCORRECT = perilaku terbukti bertentangan/keliru; NOT_VERIFIABLE = bukti diperlukan di luar cakupan; NOT_APPLICABLE = bukan tanggung jawab komponen. Tidak ada KF sistem yang terverifikasi IMPLEMENTED. Kehilangan modul di workspace tidak membuktikan seluruh tim belum mengerjakannya di lokasi lain.

## 5. Kebutuhan fungsional asli

Kalimat kebutuhan berikut mengikuti P §2.1.3 Tabel 2.3 (indeks internal h.13). AC adalah operasionalisasi pengujian dari kebutuhan, bukan target akurasi/latensi baru. Perlakuan kegagalan yang tidak ditentukan proposal ditandai rekomendasi atau TBD.

### KF-01 Membaca suhu air

**Kebutuhan asli:** Sistem dapat membaca suhu air. **Sumber:** P §2.1.3 Tabel 2.3; elaborasi P §2.2 dan §3.1. **Tujuan:** membaca suhu air untuk transparansi operasional sesuai peran.

**Aktor/komponen:** ESP32/DS18B20; pekerja melihat LCD dan owner melihat dashboard. **Preconditions:** Sensor terpasang/terkalibrasi, perangkat menyala. **Trigger:** Siklus pembacaan sensor.

**Main workflow:** Baca DS18B20 → asosiasikan waktu baca → tampilkan pada LCD → kirim/simpan untuk dashboard.

**Input:** Pembacaan DS18B20; waktu RTC. **Output:** Nilai suhu air dalam °C; histori bacaan.

**Processing/validation:** Kesalahan sensor tidak boleh diganti angka normal tanpa penanda (REC-006); ambang akurasi/cadence TBD.

**Alternative/error:** input dibatalkan/koreksi sebelum konfirmasi mengikuti Gambar 2.6 bila relevan. Saat sensor/jaringan/penyimpanan gagal, jangan menampilkan sukses tanpa bukti; mekanisme retry/quality/ACK merupakan REC-003/REC-006 yang perlu disepakati. Pada fungsi hardware, error harus diuji di alat; mock UI tidak membuktikan pemulihan. Perilaku rinci yang tidak dijelaskan P adalah TBD — REQUIRES HUMAN CONFIRMATION.

**AC-KF-01:** Given sensor dan termometer referensi tersedia, When pembacaan dilakukan, Then nilai/waktu teridentifikasi pada LCD dan dashboard, serta perbandingan kalibrasi dicatat tanpa mengklaim target error yang belum disahkan.

**Status saat audit:** MISSING; display MOCKED di App dan template DashboardScreen. **Rencana:** TASK-011, TASK-017.

### KF-02 Membaca suhu dan kelembapan ruangan

**Kebutuhan asli:** Sistem dapat membaca suhu dan kelembapan ruangan. **Sumber:** P §2.1.3 Tabel 2.3; elaborasi P §2.2 dan §3.1. **Tujuan:** membaca suhu dan kelembapan ruangan untuk transparansi operasional sesuai peran.

**Aktor/komponen:** ESP32/DHT22; pekerja/owner. **Preconditions:** DHT22 terpasang sesuai batasan. **Trigger:** Siklus pembacaan sensor.

**Main workflow:** Baca dua parameter DHT22 → timestamp → LCD → database/dashboard.

**Input:** Suhu dan kelembapan DHT22; waktu. **Output:** Suhu °C dan kelembapan %.

**Processing/validation:** Nilai numerik/quality diperiksa; kegagalan parsial per nilai sebaiknya dipertahankan (REC-006); target akurasi TBD.

**Alternative/error:** input dibatalkan/koreksi sebelum konfirmasi mengikuti Gambar 2.6 bila relevan. Saat sensor/jaringan/penyimpanan gagal, jangan menampilkan sukses tanpa bukti; mekanisme retry/quality/ACK merupakan REC-003/REC-006 yang perlu disepakati. Pada fungsi hardware, error harus diuji di alat; mock UI tidak membuktikan pemulihan. Perilaku rinci yang tidak dijelaskan P adalah TBD — REQUIRES HUMAN CONFIRMATION.

**AC-KF-02:** Given DHT22 menghasilkan dua bacaan, When data tersimpan, Then suhu dan kelembapan tidak tertukar, memiliki unit/waktu, dan dapat ditemukan kembali.

**Status saat audit:** MISSING; dua kartu React menerima fixture/random. **Rencana:** TASK-011, TASK-017.

### KF-03 Membaca pH larutan

**Kebutuhan asli:** Sistem dapat membaca nilai pH larutan nutrisi. **Sumber:** P §2.1.3 Tabel 2.3; elaborasi P §2.2 dan §3.1. **Tujuan:** membaca ph larutan untuk transparansi operasional sesuai peran.

**Aktor/komponen:** ESP32/PH-4502C; pekerja/owner. **Preconditions:** Probe/konversi/kalibrasi pH tersedia. **Trigger:** Siklus baca pH.

**Main workflow:** Ambil signal probe → konversi yang terkalibrasi → LCD → database/dashboard.

**Input:** Signal analog/hasil kalibrasi; waktu. **Output:** pH tanpa unit fisik; histori.

**Processing/validation:** P §3.1 menetapkan kalibrasi buffer pH 4/7/10; rumus konversi/akurasi tidak ditentukan. Kegagalan konversi bukan pH normal (REC-006).

**Alternative/error:** input dibatalkan/koreksi sebelum konfirmasi mengikuti Gambar 2.6 bila relevan. Saat sensor/jaringan/penyimpanan gagal, jangan menampilkan sukses tanpa bukti; mekanisme retry/quality/ACK merupakan REC-003/REC-006 yang perlu disepakati. Pada fungsi hardware, error harus diuji di alat; mock UI tidak membuktikan pemulihan. Perilaku rinci yang tidak dijelaskan P adalah TBD — REQUIRES HUMAN CONFIRMATION.

**AC-KF-03:** Given kalibrasi terhadap buffer dicatat, When larutan dibaca, Then hasil pH dan waktu diteruskan ke LCD/dashboard dan dapat ditelusuri ke data yang sama.

**Status saat audit:** MISSING; nilai pH fixture/random. **Rencana:** TASK-011, TASK-017.

### KF-04 Menampilkan data sensor pada LCD

**Kebutuhan asli:** Sistem dapat menampilkan data sensor pada LCD. **Sumber:** P §2.1.3 Tabel 2.3; elaborasi P §2.2 dan §3.1. **Tujuan:** menampilkan data sensor pada lcd untuk transparansi operasional sesuai peran.

**Aktor/komponen:** Firmware/LCD 20x4; pekerja. **Preconditions:** LCD I2C terhubung; hasil baca tersedia. **Trigger:** Data baru atau membuka monitoring.

**Main workflow:** Render empat parameter yang relevan dalam menu LCD 20x4; kembali dari input ke monitoring.

**Input:** Hasil baca KF-01–03. **Output:** Informasi lokal pekerja.

**Processing/validation:** Layout detail/menu paging tidak disediakan lengkap. Quality/error feedback direkomendasikan tanpa menambah sensor.

**Alternative/error:** input dibatalkan/koreksi sebelum konfirmasi mengikuti Gambar 2.6 bila relevan. Saat sensor/jaringan/penyimpanan gagal, jangan menampilkan sukses tanpa bukti; mekanisme retry/quality/ACK merupakan REC-003/REC-006 yang perlu disepakati. Pada fungsi hardware, error harus diuji di alat; mock UI tidak membuktikan pemulihan. Perilaku rinci yang tidak dijelaskan P adalah TBD — REQUIRES HUMAN CONFIRMATION.

**AC-KF-04:** Given empat parameter diterima, When pekerja membuka monitoring lokal, Then nilai dan identitas parameter terbaca pada LCD tanpa membuka Android.

**Status saat audit:** MISSING; UI Android/web bukan LCD. **Rencana:** TASK-012.

### KF-05 Pengingat jadwal nutrisi

**Kebutuhan asli:** Sistem dapat memberikan pengingat jadwal pemberian nutrisi. **Sumber:** P §2.1.3 Tabel 2.3; elaborasi P §2.2 dan §3.1. **Tujuan:** pengingat jadwal nutrisi untuk transparansi operasional sesuai peran.

**Aktor/komponen:** RTC/firmware/LCD/buzzer; owner mengatur, pekerja merespons. **Preconditions:** Jadwal owner tersimpan dan jam perangkat tersedia. **Trigger:** Waktu RTC mencapai jadwal aktif.

**Main workflow:** Cek jadwal → LCD mengingatkan → buzzer/notifikasi lokal → pekerja memberi nutrisi manual.

**Input:** Waktu RTC; jadwal/fase/hari aktif/toleransi yang disahkan. **Output:** Alarm lokal dan informasi status jadwal.

**Processing/validation:** Durasi/repetisi/snooze dan penanda tidak direspons TBD Q-05; offline RTC dibahas DER-FR-006. Tidak mengganti alarm hardware dengan push Android.

**Alternative/error:** input dibatalkan/koreksi sebelum konfirmasi mengikuti Gambar 2.6 bila relevan. Saat sensor/jaringan/penyimpanan gagal, jangan menampilkan sukses tanpa bukti; mekanisme retry/quality/ACK merupakan REC-003/REC-006 yang perlu disepakati. Pada fungsi hardware, error harus diuji di alat; mock UI tidak membuktikan pemulihan. Perilaku rinci yang tidak dijelaskan P adalah TBD — REQUIRES HUMAN CONFIRMATION.

**AC-KF-05:** Given jadwal aktif dan RTC disetel untuk uji, When jadwal tiba, Then LCD/buzzer memberi pengingat, sementara nutrisi tetap ditambahkan pekerja secara fisik.

**Status saat audit:** MISSING; popup notifikasi fixture bukan scheduler. **Rencana:** TASK-013, TASK-021.

### KF-06 Input fase tanaman

**Kebutuhan asli:** Sistem dapat menerima input fase tanaman. **Sumber:** P §2.1.3 Tabel 2.3; elaborasi P §2.2 dan §3.1. **Tujuan:** input fase tanaman untuk transparansi operasional sesuai peran.

**Aktor/komponen:** Pekerja melalui tombol/LCD; backend menyimpan; owner melihat. **Preconditions:** Pekerja membuka menu pencatatan. **Trigger:** Pemilihan fase.

**Main workflow:** Pilih vegetatif atau generatif → lanjut PPM → ringkasan → konfirmasi/correct/cancel → simpan fase pada record.

**Input:** Pilihan fase pekerja. **Output:** Fase pada tiap pencatatan.

**Processing/validation:** Tidak ada umur/hari otomatis untuk perpindahan fase. Fase record, pilihan editor parameter, dan fase aktif dashboard harus dibedakan (Q-04).

**Alternative/error:** input dibatalkan/koreksi sebelum konfirmasi mengikuti Gambar 2.6 bila relevan. Saat sensor/jaringan/penyimpanan gagal, jangan menampilkan sukses tanpa bukti; mekanisme retry/quality/ACK merupakan REC-003/REC-006 yang perlu disepakati. Pada fungsi hardware, error harus diuji di alat; mock UI tidak membuktikan pemulihan. Perilaku rinci yang tidak dijelaskan P adalah TBD — REQUIRES HUMAN CONFIRMATION.

**AC-KF-06:** Given pekerja memilih generatif, When ringkasan dikonfirmasi, Then record menyimpan generatif, dan edit parameter vegetatif tidak mengubah fase record itu.

**Status saat audit:** PARTIALLY_IMPLEMENTED pada form React; jalur perangkat MISSING. **Rencana:** TASK-014, TASK-006.

### KF-07 Input PPM hasil ukur manual

**Kebutuhan asli:** Sistem dapat menerima input nilai PPM hasil pengukuran manual. **Sumber:** P §2.1.3 Tabel 2.3; elaborasi P §2.2 dan §3.1. **Tujuan:** input ppm hasil ukur manual untuk transparansi operasional sesuai peran.

**Aktor/komponen:** Pekerja melalui handheld lalu tombol/LCD. **Preconditions:** Nutrisi sudah dicampur dan diukur dengan handheld. **Trigger:** Pekerja memasukkan hasil pengukuran.

**Main workflow:** Input angka PPM → tampilkan fase/PPM/waktu/sensor terakhir → pekerja koreksi/batalkan atau konfirmasi → kirim data.

**Input:** Nilai ukur manual; fase; waktu input. **Output:** Pencatatan PPM manual, bukan sensor telemetry.

**Processing/validation:** Batas nilai dan increment digit TBD Q-03. Abnormal agronomis berbeda dari payload malformed; tidak diam-diam membuang pengukuran abnormal. Konfirmasi sebelum penyimpanan P Gambar 2.6.

**Alternative/error:** input dibatalkan/koreksi sebelum konfirmasi mengikuti Gambar 2.6 bila relevan. Saat sensor/jaringan/penyimpanan gagal, jangan menampilkan sukses tanpa bukti; mekanisme retry/quality/ACK merupakan REC-003/REC-006 yang perlu disepakati. Pada fungsi hardware, error harus diuji di alat; mock UI tidak membuktikan pemulihan. Perilaku rinci yang tidak dijelaskan P adalah TBD — REQUIRES HUMAN CONFIRMATION.

**AC-KF-07:** Given handheld menghasilkan angka dan pekerja mengonfirmasinya, When record dikirim, Then nilai manual dan fase tersimpan persis; cancel tidak membuat record.

**Status saat audit:** PARTIALLY_IMPLEMENTED pada form/localStorage React; jalur IoT MISSING. **Rencana:** TASK-014, TASK-015.

### KF-08 Penyimpanan database

**Kebutuhan asli:** Sistem dapat menyimpan data ke database. **Sumber:** P §2.1.3 Tabel 2.3; elaborasi P §2.2 dan §3.1. **Tujuan:** penyimpanan database untuk transparansi operasional sesuai peran.

**Aktor/komponen:** Backend/database; ESP32 producer; owner consumer. **Preconditions:** Kontrak/penyimpanan remote siap. **Trigger:** Telemetry baru atau pencatatan terkonfirmasi.

**Main workflow:** Terima data → periksa struktur/akses → simpan hubungan record/sensor/waktu → validasi → dashboard membaca kembali.

**Input:** Sensor dan pencatatan fase/PPM/waktu; parameter. **Output:** Data operasional terpusat/historis.

**Processing/validation:** Database engine MySQL dikonfirmasi pengguna; versi/environment, relasi opsional, transaksi dan retention TBD; MicroSD fallback P §3.1. ACK dan idempotency REC-003; localStorage bukan database integrasi.

**Alternative/error:** input dibatalkan/koreksi sebelum konfirmasi mengikuti Gambar 2.6 bila relevan. Saat sensor/jaringan/penyimpanan gagal, jangan menampilkan sukses tanpa bukti; mekanisme retry/quality/ACK merupakan REC-003/REC-006 yang perlu disepakati. Pada fungsi hardware, error harus diuji di alat; mock UI tidak membuktikan pemulihan. Perilaku rinci yang tidak dijelaskan P adalah TBD — REQUIRES HUMAN CONFIRMATION.

**AC-KF-08:** Given perangkat mengirim satu pencatatan terkonfirmasi, When layanan menerima dan menyimpan, Then owner pada sesi/perangkat lain dapat membaca data yang sama dan waktu sumber tetap tersedia.

**Status saat audit:** MISSING; hanya settings/logs browser localStorage. **Rencana:** TASK-007, TASK-015, TASK-016.

### KF-09 Riwayat pemberian nutrisi

**Kebutuhan asli:** Sistem dapat menampilkan riwayat pemberian nutrisi. **Sumber:** P §2.1.3 Tabel 2.3; elaborasi P §2.2 dan §3.1. **Tujuan:** riwayat pemberian nutrisi untuk transparansi operasional sesuai peran.

**Aktor/komponen:** Dashboard owner/backend. **Preconditions:** Pencatatan sudah tersimpan; owner berhak baca. **Trigger:** Owner membuka Riwayat.

**Main workflow:** Muat record → tanggal/waktu/PPM/fase/status → filter tanggal → evaluasi riwayat.

**Input:** Record manual/validasi; filter. **Output:** Daftar historis dan ringkasan yang sesuai dataset.

**Processing/validation:** Filter tanggal disebut P §3.1; ekspor ada pada wireframe. Pagination S §5. Penghitungan scope/filter/ekspor perlu Q-11.

**Alternative/error:** input dibatalkan/koreksi sebelum konfirmasi mengikuti Gambar 2.6 bila relevan. Saat sensor/jaringan/penyimpanan gagal, jangan menampilkan sukses tanpa bukti; mekanisme retry/quality/ACK merupakan REC-003/REC-006 yang perlu disepakati. Pada fungsi hardware, error harus diuji di alat; mock UI tidak membuktikan pemulihan. Perilaku rinci yang tidak dijelaskan P adalah TBD — REQUIRES HUMAN CONFIRMATION.

**AC-KF-09:** Given record tersimpan pada dua tanggal, When owner memilih rentang tanggal, Then hanya record dalam rentang tampil dengan nilai/fase/waktu/status dari sumber, dan hasil kosong bukan fixture.

**Status saat audit:** PARTIALLY_IMPLEMENTED; daftar/filter status dan search lokal ada, date range/backend/native tidak ada. **Rencana:** TASK-018, TASK-019.

### KF-10 Validasi jadwal dan PPM

**Kebutuhan asli:** Sistem dapat melakukan validasi berdasarkan jadwal dan nilai PPM. **Sumber:** P §2.1.3 Tabel 2.3; elaborasi P §2.2 dan §3.1. **Tujuan:** validasi jadwal dan ppm untuk transparansi operasional sesuai peran.

**Aktor/komponen:** Backend sistem validasi; owner menetapkan; dashboard/LCD menerima hasil. **Preconditions:** Record lengkap, fase/jadwal/parameter yang disahkan tersedia. **Trigger:** Pencatatan diterima.

**Main workflow:** Periksa lengkap → waktu terhadap jadwal/toleransi → PPM terhadap fase → sensor menurut konflik Q-02 → tetapkan dan simpan status → tampilkan alasan.

**Input:** Waktu input, fase, PPM manual, parameter/jadwal; sensor terkait jika disahkan. **Output:** Hasil validasi dan keterangan/waktu.

**Processing/validation:** Flowchart mengutamakan terlambat sebelum PPM, sedangkan kode mengutamakan PPM. Peran sensor melampaui batasan §1.3: Q-02. Jangan menetapkan formula final hingga keputusan tercatat.

**Alternative/error:** input dibatalkan/koreksi sebelum konfirmasi mengikuti Gambar 2.6 bila relevan. Saat sensor/jaringan/penyimpanan gagal, jangan menampilkan sukses tanpa bukti; mekanisme retry/quality/ACK merupakan REC-003/REC-006 yang perlu disepakati. Pada fungsi hardware, error harus diuji di alat; mock UI tidak membuktikan pemulihan. Perilaku rinci yang tidak dijelaskan P adalah TBD — REQUIRES HUMAN CONFIRMATION.

**AC-KF-10:** Given kasus uji disepakati owner/pembimbing untuk tepat waktu, di luar toleransi, PPM rendah/tinggi dan gabungan pelanggaran, When backend memvalidasi, Then semua hasil sama dengan decision table yang disahkan dan alasan bisa ditelusuri.

**Status saat audit:** INCORRECT sebagai kandidat spesifikasi: urutan berbeda flowchart, belum status lengkap, NaN dapat valid; backend MISSING. **Rencana:** TASK-003, TASK-009, TASK-020.

## 6. Kebutuhan non-fungsional asli

Sumber semua KNF: P §2.1.3 Tabel 2.4 (indeks internal h.14).

| ID | Kebutuhan asli | Operasionalisasi/acceptance | Status dan evidence | Task |
|---|---|---|---|---|
| KNF-01 | Sistem mudah digunakan oleh pekerja Greenhouse | Pekerja menyelesaikan menu LCD, input fase/PPM, konfirmasi dan cancel dalam usability test; P §3.1 menyebut SUS tanpa skor wajib | NOT_VERIFIABLE; tidak ada alat/usability report | TASK-012,014,023 |
| KNF-02 | Tampilan informasi mudah dipahami | Owner/pekerja dapat membedakan parameter, unit, PPM manual, waktu dan status; tidak mengandalkan warna saja (REC) | UI_ONLY; label/unit ada, usability belum diuji | TASK-017,018,023 |
| KNF-03 | Sistem dapat beroperasi menggunakan jaringan WiFi | Uji ESP32 pada WiFi greenhouse mengirim data ke backend; browser online bukan bukti koneksi perangkat | NOT_VERIFIABLE; firmware/perangkat tidak tersedia | TASK-015,022 |
| KNF-04 | Data waktu tercatat secara akurat menggunakan RTC | Record memakai waktu RTC dan uji NTP/fallback tercatat; drift maksimum tidak diberikan | MISSING pada source; Date.now bukan RTC | TASK-010,013,022 |
| KNF-05 | Perangkat terlindungi menggunakan box panel | Pemeriksaan fisik box ABS/cable gland sesuai P Tabel 3.2; tidak mengklaim rating IP baru | NOT_VERIFIABLE; hanya gambar rancangan | TASK-011,023 |
| KNF-06 | Data monitoring dapat disimpan dan diakses kembali | Bacaan lingkungan dan pencatatan bisa dimuat setelah restart/sesi lain; retention TBD | PARTIALLY_IMPLEMENTED untuk logs/settings browser; sensor tidak disimpan | TASK-007,016,017,022 |

Tidak ada SLA, cadence telemetry, batas error sensor, kapasitas user, atau skor SUS yang ditetapkan pada tabel KNF. P §3.1 menyebut long-run 24–48 jam sebagai **durasi rencana uji**, bukan jaminan uptime. Security, maintainability, compatibility dan feedback minimum berasal dari SOP/DER berikut, bukan KNF yang diganti namanya. Offline perangkat ditegaskan dalam narasi/flowchart; offline Android cache/write belum dinyatakan.

## 7. Kebutuhan turunan dan rekomendasi

DER mempertahankan kewajiban yang tampak di narasi/diagram/SOP. Konflik tidak menjadi implementasi siap jalan.

| ID | Spesifikasi turunan dan sumber | AC yang dapat diperiksa | Status audit | Task |
|---|---|---|---|---|
| DER-FR-001 | Dashboard owner menampilkan empat sensor, fase, record terakhir dan hasil validasi; P §1.4, Gambar 2.7/2.10 | Data kartu identik dengan backend; tanpa record menampilkan kosong | MOCKED; fallback palsu | TASK-017,020 |
| DER-FR-002 | Owner mengatur jadwal/toleransi, rentang PPM per fase dan ambang sensor; Gambar 2.1/2.7/2.12, ERD | Konfigurasi tersimpan dan terbaca perangkat/backend; pilihan editor tidak mengubah fase kebun | PARTIALLY_IMPLEMENTED lokal; Q-02/04/05 | TASK-003,021 |
| DER-FR-003 | Grafik historis sensor dan PPM manual; P Gambar 2.7, §3.1 monitoring | Grafik merepresentasikan record bertimestamp; manual PPM diberi label terpisah | MOCKED sensor; grafik PPM MISSING | TASK-019 |
| DER-FR-004 | Status perangkat dan data sensor terbaru; Gambar 2.7/2.10, ERD `last_online` | Status berasal bukti perangkat, bukan toggle owner; waktu terakhir terbaca | MOCKED; tidak ada device ID/heartbeat | TASK-016,017 |
| DER-FR-005 | Login/manajemen pengguna; P §3.1 software, Class/ERD, S §1/7 | Login/logout dan pembatasan hak owner/producer diuji di server; akses tanpa izin ditolak | MISSING; cara identifikasi pekerja pada alat Q-06 | TASK-008 |
| DER-FR-006 | RTC/offline sementara dan datalogger MicroSD; P Gambar 2.8, §3.1 firmware/integrasi, Tabel 3.1 | Putus WiFi: record tersimpan lokal dan alarm memakai RTC; reconnect/replay diuji setelah kontrak disahkan | MISSING pada workspace, fisik NOT_VERIFIABLE | TASK-010,013,015,022 |
| DER-FR-007 | Konfirmasi, koreksi sebelum simpan, cancel; P Gambar 2.6 | Pekerja melihat ringkasan fase/PPM/waktu/sensor, cancel tidak menghasilkan record | MISSING jalur hardware; form langsung submit | TASK-014 |
| DER-FR-008 | LED feedback PPM sesuai/tidak ideal; P §1.5, Tabel 3.1 | Uji input terhadap parameter menyalakan indikator sesuai aturan yang disahkan | MISSING; lokasi validasi offline Q-07 | TASK-009,014 |
| DER-FR-009 | Belum tervalidasi dan data sensor abnormal muncul Class/ERD/flowchart | Status pending tak disamakan valid; sensor abnormal mengikuti keputusan Q-02/05 | MISSING; definisi deadline pending belum jelas | TASK-003,020 |
| DER-FR-010 | Notifikasi jadwal/validasi/sensor dan tanda baca pada Class/ERD; ikon bell wireframe | Event berasal sistem dan status baca terkait identitas; channel push Android TBD | MOCKED popup; Q-12 | TASK-003,021 |
| DER-FR-011 | Full CRUD sistem; S §5/7 | Entitas/hak/retention diputuskan, create/read/update/delete dan akses diuji; tidak menghapus audit log diam-diam | PARTIALLY_IMPLEMENTED create/read logs, update settings; Q-10 | TASK-004,024 |
| DER-NFR-001 | Pentest, mitigasi auth/injection, validasi frontend/backend, perlindungan secret; S §1/4/6 | Laporan pentest berizin dan remediation; akses/input invalid ditolak server | PARTIALLY_IMPLEMENTED hanya HTML constraints/.gitignore; keamanan menyeluruh NOT_VERIFIABLE | TASK-008,025 |
| DER-NFR-002 | Responsif desktop/laptop, smartphone/tablet; Windows/macOS/Android/iOS; feedback/loading/error; S §2 | Matriks platform yang disahkan diuji terhadap overflow, input dan kegagalan | NOT_VERIFIABLE runtime; Android-only Q-01 | TASK-001,023 |
| DER-NFR-003 | Clean code, modular/DRY, presentation/business/data terpisah; configurable API/routes; S §3/4 | Review dependency arah layer dan subdirectory untuk web bila dipertahankan | PARTIALLY_IMPLEMENTED komponen/util, App menggabungkan persistence/events | TASK-005,006 |
| DER-NFR-004 | Payload dibatasi, pagination/result limit, query efisien; S §4/5 | Request/result besar dibatasi dan uji pagination stabil; sekitar 10 KB rekomendasi SOP, bukan ceiling kontrak final | MISSING data access; limit final TBD | TASK-007,016,024 |
| DER-NFR-005 | Validasi/review berkas dan revisi sebelum lolos pengumpulan; S diagram h.3, G §3–8 | Tim menyiapkan bukti; reviewer memeriksa, memberi revisi atau menyatakan lolos | NOT_VERIFIABLE eksternal | TASK-002,026 |

| REC ID | Rekomendasi, bukan requirement yang disahkan |
|---|---|
| REC-001 | Cache Android baca melalui Room bila kebutuhan offline/volume membenarkan; WorkManager hanya untuk pekerjaan tertunda yang disetujui |
| REC-002 | Kotlin UI + ViewModel/StateFlow + repository; domain use case/DI framework hanya bila berguna; satu app module cukup awal |
| REC-003 | Stable event ID/idempotency, ACK server dan replay MicroSD tanpa duplikat |
| REC-004 | Timestamp kanonik UTC/offset, waktu capture/receive terpisah, presentasi Asia/Jakarta |
| REC-005 | Versi/snapshot parameter dan provenance hasil validasi untuk menjaga evaluasi historis |
| REC-006 | Quality/error/stale eksplisit; jangan buat bacaan atau validasi sukses saat belum ada data |
| REC-007 | Pengujian dapat diulang sesuai rekomendasi S §7; contract/unit/UI tests sesuai risiko |

## 8. Spesifikasi fitur dan layar owner

Tujuan layar native belum diimplementasikan. Path React berikut adalah referensi desain/perilaku, bukan modul Kotlin. Lihat screen inventory lengkap GAP_ANALYSIS untuk modal/tools prototipe.

### SCR-01 Login dan sesi

Required by DER-FR-005, P §3.1, S §1/7; MISSING. Data: identitas, role/permission dari backend, sesi. Aksi: login/logout; navigasi setelah login ke Dashboard dan session-expired kembali login. Loading saat autentikasi; error terbedakan tanpa membocorkan credential; empty form memakai hint, bukan akun default. Validasi frontend dan server; brute-force mitigation pada server. Acceptance: credential salah tidak memberi sesi, owner berhasil memperoleh hak, pekerja/tanpa sesi tidak dapat mengubah parameter. Detail worker login di alat/dashboard Q-06. Tidak mewajibkan self-registration/profile screens yang tidak disebut P.

### SCR-02 Dashboard monitoring

Required KF-01–03/09/10, DER-FR-001/003/004; existing `src/components/DashboardScreen.tsx`, `SensorChart.tsx`, template DashboardScreen; MOCKED. UI: fase, status perangkat, empat kartu dengan unit, waktu baca, aktivitas manual terakhir dan status validasi, akses riwayat/pengaturan, grafik. Data: latest telemetry, device last contact, record/validation terakhir dan fase yang sumbernya disahkan. Aksi refresh harus mengambil data sumber. Navigasi bottom tabs dan riwayat lengkap. Tombol create PPM Android di luar scope tahap sekarang sesuai keputusan Q-03.

State: loading request, partial sensor failure, empty/no telemetry, empty/no manual record, stale last known, disconnected, request error dengan retry; jangan menampilkan 850 PPM atau Valid sebagai fallback kosong. Tidak ada cutoff stale dalam P: Q-11. Acceptance: refresh tidak membuat random value; latest record dipilih menurut waktu/data sumber; offline data diberi label; unit tidak tertukar. Tampilan grafik mendukung data aktual, bukan path dekoratif. Grafik PPM manual tidak diberi label telemetry otomatis.

### SCR-03 Riwayat nutrisi

Required KF-08/09/10, KNF-06; `src/components/HistoryScreen.tsx`; PARTIALLY_IMPLEMENTED lokal. UI: tanggal/waktu, PPM manual, fase, status/keterangan; filter tanggal P §3.1, status dan ringkasan wireframe; ekspor pada Gambar 2.11. Data: record/validation dari backend yang berhak diakses, paging dan filter. Aksi filter/reset/load more, ekspor bila scope Q-11 disahkan. Bottom navigation. Empty dataset/filter eksplisit; initial/loading next page/error/retry; draft/pending ditampilkan berbeda dari valid. Status gabungan mengikuti decision table Q-02. Acceptance: date range benar berdasarkan timestamp kanonik, pagination tidak mengulang record, export sesuai pilihan yang dijelaskan. File export perlu escaping/safety (GAP F-014). Koreksi/hapus historis hanya setelah Q-10.

### SCR-04 Pengaturan jadwal dan parameter

Required DER-FR-002/KF-05/10; `src/components/SettingsScreen.tsx`; PARTIALLY_IMPLEMENTED lokal. UI: jadwal, toleransi, pilihan fase **editor rentang**, min/max PPM, ambang sensor sesuai keputusan Q-02, Save. Data: konfigurasi backend/versi dan keadaan penerapan perangkat. Aksi load/edit/save/cancel; perubahan fase kebun berbeda dari pilihan editor (Q-04). Navigation tabs. Missing configuration memerlukan owner mengisi/disahkan; angka wireframe tidak menjadi default produksi. Loading mengambil/simpan; error mempertahankan draft; sukses baru setelah ACK penyimpanan, feedback penerapan ke alat terpisah. Validasi finite, jam parseable, min/max berurutan; batas numerik final Q-03/05. Acceptance: perubahan disimpan server, dapat dimuat kembali/sesi lain, perangkat mendapat jadwal yang benar; save gagal tidak memberi toast sukses. Tombol slider React hanya dekoratif; native interaction diuji, bukan ditiru sebagai gambar.

### SCR-05 Grafik dan notifikasi

Grafik dapat menjadi section dashboard/history, tidak perlu screen tambahan. DER-FR-003 mengharuskan tren sensor/PPM manual berdasarkan user flow. Pilihan interval/metric mengubah dataset yang terlihat; empty/error/partial series harus eksplisit. Unit berbeda tidak memakai satu skala ambigu (REC). Notifikasi DER-FR-010 berasal Class/ERD, popup prototipe MOCKED. Inbox/modal vs push/background TBD Q-12; tidak mewajibkan izin push atau WorkManager sebelum channel disepakati. Read state harus tersimpan sesuai identitas jika fitur ini disahkan.

Kotlin code viewer dan frame-device toggle adalah tooling prototipe, tidak ada use case owner pada P. Jangan memasukkannya ke produk native tanpa alasan yang disetujui. Brand MelonNutri vs Inofarm Jogja dan kartu/grid/grafik tambahan bukan otomatis defect; ukur dampak pada requirement/flow, bukan kesamaan pixel semata.

## 9. User flows dan batas komponen

**Pekerja (P Gambar 2.6):** firmware baca sensor/LCD dan cek RTC → alarm saat jadwal → pekerja memberi nutrisi fisik dan menunggu tercampur → mengukur handheld → menu perangkat memilih fase → input PPM lewat tombol → ringkasan fase/PPM/waktu/sensor → confirm menyimpan/kirim; jika tidak benar, ubah sebelum confirm atau cancel kembali monitoring. Backend menyimpan dan memvalidasi. Android bukan perangkat input utama dalam flow ini. Identitas pekerja, koreksi setelah simpan dan offline feedback belum jelas.

**Pengelola (P Gambar 2.7):** login disebut §3.1 → Android/dashboard memuat ringkasan/status device/latest sensor/jadwal → lihat riwayat, PPM manual, fase, status dan grafik → evaluasi terlambat/abnormal → jika perlu ubah jadwal/rentang/ambang → backend menyimpan → firmware menerima dan memakai konfigurasi → dashboard memberi feedback. Tidak ada perintah menambah nutrisi secara otomatis. Kata “jadwal penyiraman” pada narasi tidak mengesahkan water pump.

**Monitoring:** sensor → ESP32 timestamp → LCD dan API → database → owner. **Recording:** handheld + pekerja → perangkat confirm → event/database → validasi → database → dashboard/LCD feedback. **Recovery:** MicroSD/RTC saat WiFi gagal adalah rancangan; replay protocol dan ACK belum dijelaskan, lihat DATA_CONTRACTS.

## 10. Business rules dan decision table yang belum final

1. Nutrisi dan PPM manual; hanya empat nilai sensor lingkungan. PPM adalah hasil handheld, tidak berasal sensor TDS.
2. Fase per record adalah vegetatif/generatif. Tidak ada rumus umur/hari tanaman. Identitas kebun/tandon/batch dan hubungan fase aktif perlu Q-04/11.
3. Owner menetapkan jadwal/toleransi/rentang; Gambar 2.12 menampilkan 08:00/16:00, 15 menit, 800–1000 PPM vegetatif, suhu 20–28 °C, pH 5.5–6.5. Ini **contoh wireframe**, bukan parameter produksi yang disahkan. Rentang 1100–1400 generatif dan batas input 100–3000/step 10 muncul di code, tidak ditetapkan narasi sebagai requirement.
4. KF-10/§1.3 membatasi jadwal/fase/PPM; Activity/Class/ERD/flowchart menambah sensor abnormal. Catat Q-02; jangan menghapus maupun menyetujui sensor validation secara sepihak.
5. Gambar 2.8: kelengkapan → waktu → PPM → sensor; jika semua sesuai valid; jika tidak, urutan terlambat, rendah, tinggi, sensor abnormal. TS/template mengutamakan low/high, baru late. Urutan proposal adalah evidence rancangan, final sign-off tetap diperlukan karena batasan dan diagram bertentangan.
6. Flowchart menyebut belum tervalidasi bila pekerja tidak mulai input; kapan status itu dibuat, keterkaitan jadwal/record dan grace period belum diberikan. Jangan membuat PPM=0 placeholder.
7. Waktu input tidak membuktikan waktu pencampuran nutrisi. Waktu ukur/input/terima harus tidak dicampur tanpa kesepakatan. RTC sumber perangkat; UTC/Asia/Jakarta disarankan REC-004. Toleransi awal/terlambat, lintas tengah malam, hari aktif dan timezone perlu Q-05.
8. Koreksi/cancel **sebelum** konfirmasi dinyatakan Gambar 2.6. Edit/delete record setelah simpan berasal kewajiban CRUD SOP; hak dan audit trail TBD Q-10. Revalidation setelah parameter berubah TBD Q-02/10, REC-005 menjaga provenance.
9. Data malformed berbeda dari pengukuran agronomis abnormal. Min/max/finite adalah pemeriksaan struktur rekomendasi/DER-NFR-001; batas handheld tidak boleh diciptakan. Duplicate prevention, retention dan recovery detail merupakan draft REC.

| Kasus | Bukti proposal | Keputusan yang diperlukan |
|---|---|---|
| Lengkap, waktu/PPM sesuai | “Sudah Tervalidasi” pada flowchart | Inklusivitas batas dan parameter aktif |
| Waktu di luar toleransi, PPM sesuai | “Terlambat Dicatat” | Early vs late, tanggal dan schedule occurrence |
| PPM rendah atau tinggi | Status rendah/tinggi | Rentang tiap fase disahkan owner |
| Terlambat sekaligus PPM abnormal | Flowchart late dahulu; code PPM dahulu | Primary status atau multi-reason Q-02 |
| Sensor abnormal dengan PPM/waktu sesuai | Diagram memasukkan abnormal; batasan tidak | Apakah memengaruhi validasi atau alert terpisah Q-02 |
| Tidak ada input | Belum tervalidasi pada flowchart | Deadline dan model event Q-05 |
| Payload incomplete/invalid | Activity memeriksa kelengkapan | Tolak struktur atau simpan pending; jangan valid |

## 11. Traceability dan pengujian

Matriks canonical requirement → source/component/code/status/issue/action/priority ada pada GAP_ANALYSIS §2. Ringkasan task/AC pada setiap KF di atas. Setiap KNF/DER memiliki task dan acceptance. Tidak ada mapped Kotlin file fisik: gunakan template path sebagai referensi audit dan **expected files proposed** pada IMPLEMENTATION_PLAN.

Baseline acceptance scenarios: AC-KF-01–10 di §5, KNF di §6, DER di §7. Tambahkan kasus tepat boundary sesuai keputusan, multi-failure, config unavailable, date rollover, duplicate replay, ACK hilang, sensor error, offline restart, unauthorized config CRUD, empty history dan storage error. P §3.1 juga memerlukan black-box per fitur, kalibrasi pH 4/7/10, suhu dibanding termometer, long-run 24–48 jam, SUS/tes pekerja-LCD dan owner-dashboard, ketahanan greenhouse dan jangkauan WiFi. S §7 minimal login/tambah/edit/hapus/akses; pentest S §1 sebelum penyerahan. Jangan menyatakan pengujian perangkat telah lulus dari test mock.

## 12. Akademik dan keluaran

G mengharuskan branch kerja, validasi lokal, pemeriksaan status/diff/file sensitif, commit deskriptif, sinkronisasi sebelum push, verifikasi remote. `type(scope): message` adalah pilihan, MR conditional jika workflow mewajibkan, CI conditional jika digunakan. Tidak ada wajib GitFlow, kuota commit per anggota atau folder Android tertentu dalam G.

S mengatur keamanan, UI, clean code/layers, configurable route/API, limit payload, full CRUD, query/pagination, credential protection, test minimal serta alur submit/review/revisi. S tidak menyebut tenggat numerik, URL platform pengumpulan, format nama file/ZIP, template laporan, ukuran video, kuota kontribusi atau ketentuan demo rinci. Diagram h.3 hanya menyatakan dokumentasi/berkas pendukung dan platform yang ditentukan: Q-13.

Deliverable P §3.1: firmware `.bin`, dashboard siap deploy, API terdokumentasi, hardware, laporan BAB I–V, manual pekerja, panduan admin owner, skematik/wiring/source, video demo greenhouse. Proposal menyebut GitHub; akademik G mengharuskan GitLab UMY, konflik Q-14 dicatat. Gantt Capstone 1 April–Juli 2026 memuat rencana pendaftaran 4 Juli dan ujian 6–11 Juli; Capstone 2 Oktober 2026–Januari 2027 merencanakan sidang akhir Januari minggu ke-4. Semua adalah rencana proposal, **bukan deadline resmi SOP atau bukti pelaksanaan**.

## 13. Open questions dan approval gates

Pertanyaan berikut masih **TBD — REQUIRES HUMAN CONFIRMATION**, kecuali bagian yang dicatat telah terjawab. Konfirmasi pengguna 10 Oktober 2026 menetapkan tidak ada proyek Android terpisah dan database menggunakan **MySQL**. Q-11 terjawab sebagian hanya untuk pilihan engine; tidak ada schema/API/database aktif yang diverifikasi dan tidak ada izin implementasi dari keputusan engine ini.

| ID | Keputusan dan pihak yang perlu menyetujui | Task yang tertahan |
|---|---|---|
| Q-01 | PARTIALLY RESOLVED: pengguna memilih Android native Kotlin saja; React referensi tampilan APK (10 Oktober 2026). Masih terbuka: penerapan SOP Windows/macOS/Android/iOS oleh pembimbing/tim, lokasi proyek dan toolchain | TASK-001,005,023; keputusan produk tidak sama dengan bukti compliance akademik |
| Q-02 | PARTIALLY RESOLVED: pengguna memilih **B — gabungan jadwal/fase/PPM dan sensor** untuk tahap awal (10 Oktober 2026); evaluasi perubahan setelah testing. Masih terbuka: threshold produksi, prioritas multi-failure, sensor snapshot/usia/missing, pending, boundary/versi/revalidation; owner+pembimbing | TASK-003,009,020 |
| Q-03 | PARTIALLY RESOLVED: PPM hanya lewat ESP32 untuk tahap sekarang (pengguna, 10 Oktober 2026); tidak ada create PPM Android. Input harian greenhouse adalah future scope terpisah, belum didefinisikan. Masih terbuka: rentang/increment PPM produksi dan otorisasi operator; owner+pembimbing | TASK-003,014; create PPM Android di luar scope saat ini |
| Q-04 | PARTIALLY RESOLVED: owner menetapkan fase aktif tandon di Android; pekerja memilih fase record di ESP32; mismatch memberi peringatan (pengguna, 10 Oktober 2026). Keduanya dipertahankan; editor range tidak mengganti fase aktif. Effective time/version/history mengikuti kontrak terdokumentasi | TASK-003,006,021 |
| Q-05 | Jadwal/hari/fase/occurrence, toleransi early/late/tengah malam, durasi alarm/ack dan timeout pending | TASK-003,010,013,021 |
| Q-06 | PARTIALLY RESOLVED: Android owner/leader saja; pekerja input melalui IoT. Jadwal pekerja hari/shift dirancang agen sesuai permintaan pengguna, assignment berdasarkan waktu konfirmasi catatan di perangkat; §14/TASK-027. Masih terbuka: roster/jam shift operasional, delegasi/hak owner-leader, device trust dan correction policy; assignment bukan verified author | TASK-004,008,014,027 |
| Q-07 | Backend otoritatif vs validasi LED/LCD offline, cache parameter/version/ACK; tim backend+IoT | TASK-004,009,015 |
| Q-08 | Pinout/catu daya yang benar: buzzer GPIO33 vs §3.1 GPIO27; skematik bus/sensor tidak konsisten; tim IoT/pembimbing | TASK-004,011 |
| Q-09 | DHT22-only §1.3 vs DHT22/SHT31 label Gambar 2.14; modul MicroSD/LED tidak lengkap pada Gambar 2.13 | TASK-004,011 |
| Q-10 | Entitas full CRUD/hak edit/delete, retention, soft-delete/audit trail dan revalidation; owner+pembimbing | TASK-004,024 |
| Q-11 | PARTIALLY RESOLVED: **Laravel/PHP REST API + MySQL**, satu ESP32/satu tandon tahap awal (pengguna, 10 Oktober 2026). Masih terbuka: versi/environment/toolchain, API/schema/deployment, batch bila diperlukan, cadence/stale cutoff, filter/chart/export scope | TASK-004,007,016,019 |
| Q-12 | Notifikasi lokal LCD/buzzer vs inbox owner vs push Android; event/read lifecycle dan permission | TASK-003,021 |
| Q-13 | Platform/format/tenggat resmi pengumpulan, bukti pentest/review yang diterima, status pengesahan proposal | TASK-002,025,026 |
| Q-14 | GitLab UMY authoritative sesuai SOP vs GitHub proposal; lokasi remote/tim/access kampus dan kebijakan MR | TASK-002 |

Pertanyaan tidak mengotorisasi perubahan. Jalankan task klarifikasi terlebih dahulu; setelah persetujuan implementasi baru pilih task siap dengan dependencies terpenuhi.

## 14. Jadwal pekerja dan referensi UI — keputusan lanjutan

**Keputusan final tahap awal dan izin pengerjaan (U, 10 Oktober 2026):** owner menetapkan fase aktif tandon dari Android, pekerja tetap memilih fase saat mencatat di ESP32, ketidakselarasan memberi peringatan; input tidak ditolak atau diganti otomatis. Semua jadwal/rentang/toleransi/ambang produksi mulai **kosong**, diisi owner melalui konfigurasi tervalidasi; contoh screenshot tidak menjadi default. Owner phase context dan worker submitted phase dicatat terpisah beserta waktu/version; mismatch tidak otomatis invalid/reject hanya karena berbeda. Status validasi gabungan B tetap mencantumkan **seluruh alasan**, bukan hanya satu badge. Konfigurasi belum lengkap tidak menghasilkan valid; pemilihan badge utama tidak menyembunyikan reasons. Perubahan aturan setelah testing harus terdokumentasi dan menjaga provenance hasil lama. Pengguna mengizinkan implementasi scope Android + Laravel/MySQL + firmware, testing/debugging iteratif; tidak perlu meminta ulang approval task yang masih dalam scope. Scope tambahan, business rule tak terjawab, Git publication/deployment atau operasi destruktif tetap terpisah. Turn ini menyusun prompt dan dokumentasi, bukan membangun aplikasi.

**DER-FR-012 (sumber U, 10 Oktober 2026):** Owner/leader mengelola daftar pekerja dan jadwal hari/shift di Android; backend mengaitkan catatan nutrisi ESP32 dengan pekerja yang dijadwalkan pada waktu input. Agen diminta merancang mekanisme terbaik; aturan berikut derivasi engineering dari permintaan itu, bukan kutipan proposal atau bukti siapa melakukan tindakan fisik.

- Worker adalah daftar personel, bukan akun login Android. Jadwal pekerja terpisah dari jadwal pemberian nutrisi. Owner mengisi nama, hari aktif, jam mulai/selesai, tanggal berlaku dan penugasan; tidak ada roster/jam contoh sebagai default produksi.
- Sumber pencocokan adalah waktu **konfirmasi/simpan pencatatan nutrisi pada ESP32**, bukan sembarang tombol navigasi dan bukan waktu data tiba di server. Timestamp tersebut juga menjelaskan event yang diasosiasikan; jika pengguna menginginkan tombol aksi lain harus dicatat berbeda, bukan mengganti makna waktu diam-diam.
- Waktu tampilan/jadwal **Asia/Jakarta**; transport waktu ber-offset/UTC. Backend mencocokkan jadwal, bukan mempercayai workerId dari client/device. Endpoint/base/format event rinci masih TASK-004.
- Interval shift **mulai inklusif, selesai eksklusif**: tepat pergantian shift termasuk shift berikutnya. Shift lintas tengah malam berakhir pada hari berikutnya; hari jadwal mengacu hari mulai shift. Jam mulai sama dengan selesai ditolak sebagai ambigu, bukan otomatis 24 jam.
- Satu penanggung jawab per interval untuk tandon awal. Backend menolak jadwal aktif tumpang tindih, termasuk overlap lintas hari. Jika data bentrok sudah ada, jangan pilih nama acak: assignment ditandai konflik. Tanpa jadwal cocok, catatan tetap disimpan dengan assignment belum terjadwal. Waktu sumber tidak dapat dipercaya/missing ditandai tidak dapat ditentukan, bukan diganti receive time.
- Replay offline memakai waktu event asli. Jadwal efektif dipertahankan sebagai versi/occurrence historis; perubahan owner berlaku ke depan dan tidak menghapus versi yang dibutuhkan event terlambat. Simpan referensi assignment + snapshot nama/shift/versi saat pencocokan. Tidak mengubah catatan lama otomatis setelah edit jadwal atau rename pekerja.
- Nama ditampilkan sebagai **“Pekerja terjadwal”**, bukan “Dicatat oleh” terverifikasi. Correction manual, pergantian mendadak/absen dan hak edit/delete historis belum disahkan Q-10; tidak menebak pekerja pengganti. Masalah assignment tidak otomatis menjadi kegagalan validasi nutrisi B tanpa keputusan tambahan.
- AC: tepat batas shift, lintas hari/minggu, overlap, tanpa jadwal, offline replay dan edit/rename setelah pencatatan menghasilkan assignment deterministik dengan provenance; pengguna tanpa hak tidak bisa mengubah roster/jadwal. Task terkait TASK-027; fitur masih MISSING.

**Referensi UI pengguna:** empat screenshot Stitch AI dilampirkan 10 Oktober 2026 (dua beranda, riwayat, pengaturan). Gunakan brand Inofarm Jogja, aksen hijau, permukaan terang, empat kartu sensor dan navigasi Beranda/Riwayat/Pengaturan sebagai acuan desain; variasi brand MelonNutri pada salah satu gambar bukan nama final baru. Beranda mendukung fase/status perangkat, tren sensor dan input PPM terakhir; riwayat mendukung filter/ekspor dan hasil validasi; pengaturan mendukung jadwal nutrisi, parameter per fase dan ambang sensor. Daftar pekerja/jadwal shift ditambahkan dari Pengaturan, bukan form input PPM Android. Desain harus scroll, tanpa kartu terpotong oleh bottom navigation seperti referensi gambar. Grafik suhu dan pH memerlukan skala/unit terpisah atau pemilihan metrik; jangan menyamakan nilai pada satu sumbu. Semua angka, kurva, tanggal/count, jam 08:00/16:00, toleransi 15 menit dan rentang pada gambar **contoh tampilan**, bukan parameter/data produksi disahkan. Screenshot bukan bukti hardware/backend terintegrasi.
