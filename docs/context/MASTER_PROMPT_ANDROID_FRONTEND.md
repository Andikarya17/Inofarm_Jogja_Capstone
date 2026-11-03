# Master prompt — frontend Android Inofarm

Salin bagian prompt di bawah ke chat coding pada project ini dengan model 6.1 Sol yang dipilih pengguna. Dokumen ini adalah arahan implementasi, bukan bukti UI sudah diperbaiki. Disusun 3 November 2026 dari skill Impeccable, PRD, tema dan struktur source aktual; belum ada penilaian screenshot aplikasi pada sesi penyusunan ini.

---

Gunakan $impeccable untuk mengimplementasikan peningkatan menyeluruh frontend aplikasi Android Inofarm Kotlin + Jetpack Compose. Kerjakan source sampai hasil dapat ditinjau di Android, bukan hanya memberi saran atau mockup. Fokus pada tampilan dan pengalaman penggunaan; backend Laravel/MySQL dan kontrak bisnis tetap menjadi acuan.

## Hasil yang saya inginkan

Aplikasi operasional greenhouse melon yang menarik, matang, berkarakter, dan nyaman dipakai setiap hari. Arah visual: terinspirasi Liquid Glass iOS, dengan warna yang tetap kaya dan jelas. Jangan menerjemahkannya menjadi semua permukaan transparan, kartu abu-abu pucat, atau gradient dekoratif di mana-mana. Gunakan bahasa material kaca secara selektif, dengan interaksi Android native. Arah ini sudah dipilih; lanjutkan dengan keputusan desain yang masuk akal tanpa meminta saya memilih ulang gaya.

## Baca dan pahami sebelum mengubah

1. AGENTS.md root/lokal, docs/context/README.md, PRD.md, ARCHITECTURE.md, DATA_CONTRACTS.md, IMPLEMENTATION_STATUS.md, serta contracts/v2 yang relevan. Periksa status/diff dan pertahankan perubahan pengguna. Buat branch codex/ deskriptif per fitur sesuai aturan repo.
2. Baca `.agents/skills/impeccable/SKILL.md`. Jalankan launcher Windows `& '.agents/skills/impeccable/scripts/impeccable.cmd' context` sekali. Jika gagal, laporkan dan gunakan context repository langsung; kegagalan engine tidak menghalangi pekerjaan Compose. Jangan mengklaim detector HTML memvalidasi native Android.
3. Gunakan mode Operate. Baca reference/android.md, operate.md, critique.md, audit.native.md, adapt.native.md, serta playbook yang relevan. Baca craft-floor.md sebelum edit UI. Terapkan prinsip skill dengan kebutuhan Android dan arahan pengguna sebagai prioritas.
4. Inspeksi entry point MainActivity.kt, feature/FinanceRoot.kt, core/design/InofarmTheme.kt, MeasurementDashboard.kt, seluruh screen aktif dan komponen bersama. Semua berada di android/app/src/main/java/com/inofarm/jogja/. Jangan mengasumsikan nama file sama dengan batas layar; navigasi dan lifecycle juga berada di FinanceRoot.
5. Bangun/jalankan baseline jika tooling tersedia dan ambil screenshot layar utama kedua peran sebelum edit. Catat 5–10 masalah konkret dengan file dan bukti visual. Bila tidak bisa menjalankan, nyatakan batasnya dan lakukan audit source; jangan mengarang hasil observasi.

## Identitas visual dan penggunaan kaca

- Pertahankan pengenalan Inofarm: hijau kebun yang tegas sebagai warna utama, netral hangat bersih, aksen melon/amber terbatas. Pilih nilai final lewat uji kontras. Warna status error, warning, sukses dan informasi memiliki makna konsisten; jangan mengandalkan warna saja.
- Pisahkan tiga lapisan: latar tenang, konten solid/tonal yang terbaca, dan chrome kaca di atasnya. Terapkan kaca pada navigation bar, toolbar atau pemilih konteks mengambang yang memang memperoleh manfaat dari pemisahan lapisan. Hindari kaca bertumpuk dan semua card menjadi glass.
- Kaca terasa melalui transmisi latar yang terkendali, tint yang mempertahankan warna, highlight tepi tipis, pemisahan kedalaman, dan feedback interaksi. Jangan mengganti ini hanya dengan alpha rendah atau shadow besar.
- Angka keuangan, nilai EC/PPM, teks panjang, field input, pesan error, dialog keputusan dan review transaksi harus mempunyai latar stabil. Kontras harus bertahan saat konten di belakang bergulir.
- Jangan mengklaim backdrop blur bila hanya memburamkan isi composable. Evaluasi kemampuan API/minSdk/perangkat nyata sebelum memilih implementasi. Gunakan fallback tonal solid yang tetap indah pada perangkat yang tidak mendukung atau saat efek mahal. Dependensi baru hanya bila benar-benar diperlukan dan setelah memeriksa dokumentasi resmi/kompatibilitas.
- Buat light/dark scheme yang disengaja; jangan sekadar membalik warna. Sediakan fallback tanpa transparansi/blur. Hindari shader atau refraction real-time mahal sebagai prasyarat UI.
- Material 3 menjadi dasar semantics dan interaksi: sistem Back, gesture, IME, focus, dialogs, sheets, touch target minimal 48 dp. Jangan menyalin kontrol Cupertino atau perilaku iOS.
- Tipografi sans yang konsisten, body nyaman sekitar 14–16 sp dengan hierarki peran Material. Angka penting menonjol; label dan satuan tetap terbaca. Font scale besar tidak boleh dipaksa mengecil.
- Gunakan spacing, radius, elevation, icon stroke dan motion tokens bersama. Animasi pendek berfungsi menjelaskan perubahan state; hormati pengaturan animasi sistem.

## Rancangan tiap area

Beranda: konteks greenhouse–musim–kelompok mudah dikenali dan diganti; satu aksi utama pencatatan jelas. Tepat dua kartu pengukuran EC mS/cm dan PPM ppm mengikuti kontrak, dari satu catatan valid dengan waktu ukur WIB hari ini→kemarin→sebelumnya. Pertahankan informasi freshness, offline dan last-known. Jangan membuat data dummy, status aman palsu, atau nilai nol untuk data kosong.

Navigasi: petakan seluruh destinasi yang ada sebelum merapikan. Destinasi utama compact dapat memakai bar 3–5 item sesuai peran; fitur sekunder tetap mudah dicapai. Jangan menghilangkan fitur demi layout. Pada lebar besar, adaptasikan struktur bila perangkat tersebut didukung.

Form operasional/panen/keuangan: kelompokkan berdasarkan langkah mental pengguna, beri label dan satuan jelas, keyboard sesuai tipe, helper/error dekat field, ringkasan review yang mudah dipindai, serta CTA yang tetap dapat dicapai saat keyboard terbuka. Simpan draf dan submit harus terlihat berbeda. Jangan memecah form menjadi wizard jika membahayakan state existing.

Riwayat: hierarki judul, waktu, greenhouse/scope, status dan nilai; filter jelas, detail/koreksi dapat ditemukan. Bedakan aktivitas, kelengkapan, sinkronisasi, pembatalan dan pembandingan; jangan merangkum semua menjadi satu badge sukses.

Keuangan/laporan/investasi: angka dengan format Indonesia, label/periode/basis jelas, status missing/invalid/pending dan seluruh alasan tetap tersedia. Chart hanya bila sumber data sah dan membantu keputusan; sertakan nilai/label alternatif. Pertahankan actual versus projection, pembayaran versus alokasi/penyusutan, serta snapshot laporan immutable.

Login, lampiran, penjualan, aset, administrasi dan recovery: terapkan sistem visual yang sama; tampilkan progres, empty, gagal, retry dan disabled yang masuk akal. Fitur keuangan hanya untuk pengelola sesuai otorisasi existing.

## Batas implementasi

- Scope adalah presentation Compose, resources, tokens, UI state presentation dan tests relevan. Refactor komponen secara terukur. Jangan mengubah API/schema/rumus keuangan/auth/role/sync atau mengganti arsitektur persistence untuk mengejar desain.
- Pertahankan raw input, autosave, durable draft/outbox per akun, atomic enqueue, konflik, restore epoch, pilihan scope, audit dan alasan penolakan. Jangan mengganti lifecycle collection atau ownership ViewModel tanpa analisis dampak dan regresi.
- Pertahankan semantic/test tags atau migrasikan test bermakna bersamaan. Label tidak boleh menjanjikan tersimpan di server ketika baru lokal.
- Tidak ada IoT/sensor otomatis/dosing. Tanggal kalender WIB, HSS/HST, batas peran dan aturan EC/PPM mengikuti kontrak.
- Jangan menghapus data aplikasi, melakukan destructive reset, atau menggunakan database pengguna untuk testing. Fixture hanya untuk preview/test dan tidak mengalir ke runtime produksi.

## Urutan pelaksanaan

1. Audit source dan baseline visual; catat inventaris layar/state serta prioritas. Dokumentasikan product context dari kebutuhan nyata, dan design direction/tokens dengan membedakan existing versus target. Hindari menggandakan requirement yang bertentangan dengan docs/context.
2. Implementasikan fondasi tema/komponen bersama dan satu irisan utuh: shell navigasi, beranda, form pencatatan serta review. Verifikasi screenshot dan interaksi sebelum menyebarkan pola.
3. Terapkan pola ke riwayat/panen, lalu pengeluaran/penjualan/aset, laporan/investasi, dan layar sekunder. Teruskan seluruh scope; jangan berhenti setelah satu showcase screen.
4. Lakukan critique hasil render, harden state dan edge cases, kemudian polish akhir. Audit native mencakup accessibility, layout, lifecycle dan performa. Selesaikan masalah yang ditemukan; jangan hanya menuliskannya.

## Acceptance dan bukti

- Screenshot before/after dari emulator atau perangkat yang disebutkan, setidaknya beranda, form, riwayat dan keuangan, termasuk kedua peran. Screenshot browser/image-generation bukan bukti implementasi native.
- Uji light/dark, lebar compact, font scale 1.3 dan 2.0, teks panjang, jumlah uang besar, keyboard, scroll, Back dan inset sistem. Restore pengaturan perangkat setelah pengujian. Tambahkan tablet bila memang target.
- Verifikasi loading, empty, offline, last-known, error, retry, saving, queued, accepted, rejected/conflict dan disabled pada alur yang diubah. Jangan menghilangkan pesan penting untuk mempercantik screenshot.
- Periksa TalkBack/semantics, urutan fokus, target sentuh, contrast teks dan indikator penting. Ukur/inspeksi scroll dan animasi pada perangkat yang tersedia; jangan mengklaim performa dari screenshot.
- Jalankan build, lint dan test relevan menggunakan workflow repo aktual. Regresi UI untuk perubahan struktur/navigasi dan alur draft–review–queue wajib bermakna; tidak perlu test kosmetik yang hanya menyalin implementasi.
- Nilai baseline dan hasil dengan rubrik sama: hierarki, keterbacaan, konsistensi, navigasi, feedback state, identitas visual. Tunjukkan bukti perbaikan konkret; hindari skor sempurna tanpa alasan.
- Update context/evidence; berikan ringkasan file/alasan, hasil checks, screenshot, batas yang belum terverifikasi, dan commit sesuai aturan repo. Jangan mengklaim UAT atau kesiapan produksi dari polish frontend.

Mulai dengan inspeksi repository dan baseline, kemudian lanjutkan implementasi secara mandiri. Pertanyaan hanya untuk konflik kebutuhan yang benar-benar tidak dapat diselesaikan dari context, bukan untuk meminta izin ulang atas edit frontend reversible yang sudah saya minta.
