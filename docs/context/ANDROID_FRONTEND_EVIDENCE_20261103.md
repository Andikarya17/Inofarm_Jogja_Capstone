# Frontend Android — 3 November 2026

Branch `codex/android-liquid-glass`. Permintaan pengguna: menjalankan master prompt frontend dengan Impeccable, arah Liquid Glass berwarna.

## Perubahan source

- Tema light/dark dengan peran Material yang eksplisit, termasuk EC hijau dan PPM biru kehijauan; system bars mengikuti appearance.
- Header/navigation memakai material tonal kaca (highlight/tint/depth), ikon stroke konsisten dan label Android. Implementasi ini fallback solid, bukan backdrop blur atau refraction.
- Shared field memakai latar solid; selector memisahkan label dan nilai. Heading konsisten diterapkan ke layar entry, histori, aset, lampiran, administrasi dan investasi; laporan tetap mengikuti tema bersama.
- Beranda tidak lagi memiliki judul ganda. Aksi pencatatan dibedakan bobotnya; dua kartu EC/PPM mempertahankan missing/unit/source dan kontrak scope/WIB existing.
- Keuangan menonjolkan pencatatan pengeluaran dan mempunyai tombol melipat menu untuk fokus ke riwayat. Konflik/penolakan riwayat menggunakan penekanan error, tidak semua status berwarna hijau.
- PRODUCT.md/DESIGN.md merekam kebutuhan dan implementasi visual. Persistence, API, uang, auth/role dan lifecycle tidak diubah.

## Audit baseline dan hasil

Baseline dinilai dari source; emulator belum memiliki APK ketika capture awal dicoba, sehingga tidak ada screenshot before yang sah. Jangan memakai screenshot hasil sebagai baseline.

| Dimensi | Source baseline | Hasil yang ditinjau |
|---|---|---|
| Hierarki | Selector satu baris, banyak aksi setara | Label/nilai terpisah, pengeluaran primer, menu bisa dilipat |
| Keterbacaan | Tema hanya light; EC/PPM tidak menonjol | Dua scheme eksplisit, isi/form solid, dua kartu tonal |
| Konsistensi | Navigasi memakai karakter Unicode | Ikon stroke bersama dan heading/field bersama |
| Navigasi | Menu keuangan mengambil 65% tinggi | Tersedia fokus ke riwayat; dua wilayah scroll tetap ada saat menu dibuka |
| Feedback | Local/server jujur; semua sync label hijau | Semantik existing dipertahankan, konflik/ditolak diberi error |
| Identitas | Hijau/netral dasar | Chrome kaca tonal dan lapisan konten stabil |

## Verifikasi

Emulator `Inofarm_Main_Review_20261103`, Android 17/API37, bukan perangkat fisik. Fixture UI memakai MockWebServer dan akun sintetis; bukan bukti MySQL/runtime produksi.

- Assemble debug + test APK: PASS.
- JVM: 73 tests, 0 failures.
- Sepuluh pasangan warna utama body/muted/primary/EC/PPM light/dark dihitung dengan relative luminance sRGB: minimum 5,08:1, seluruhnya melewati 4,5:1. Output `outputs/frontend-glass/contrast.json`; belum mencakup setiap kombinasi komponen/state.
- Lint debug: PASS, tidak ada error; warning dependency/toolchain existing tidak diatasi dengan upgrade di task desain ini.
- Lima kasus terarah PASS: visual dua peran/draft uang panjang/recreation, offline→reconnect→manager history, form keuangan font200+IME+landscape, form kegiatan font200+IME+landscape, dua kartu legacy EC missing pada narrow large-font.
- Capture final dua peran: PASS light font1.0 dan dark font1.3, masing-masing satu run setelah perbaikan test. Screenshot mencakup home, measurement, form, history, finance dan payment.
- Regresi lengkap `FinanceEntryUiTest`: 40 kasus, 39 PASS dan 1 gagal pada Espresso `closeSoftKeyboard` timeout dalam multi-material/rotation; tidak diklaim satu run40 bersih. Kasus meliputi panen, penjualan, alokasi, aset, lampiran, konflik, logout/draft dan investasi.
- Rerun kasus timeout sebelum perbaikan kembali gagal di helper yang sama. Helper test kini memakai WindowInsetsController dan menunggu IME tidak visible, tanpa menelan exception. Empat kasus setelah perbaikan PASS (62,319 detik): multi-material/rotation, capture kedua peran, form kegiatan font200+landscape+IME, dan form keuangan font200+landscape+IME. Log `ime-helper-rerun.txt`. Tidak ada perubahan runtime untuk mengatasi kegagalan helper ini.
- Satu run dark screenshot sempat gagal karena test memilih ulang item LazyColumn yang sudah tidak dikomposisi setelah scroll; test diperbaiki dengan menuju tombol pencatatan langsung, lalu rerun PASS.
- Pengaturan emulator dikembalikan ke night=no dan font_scale=1.0 setelah capture.

Log dan PNG lokal di `outputs/frontend-glass/`: `verified-light/`, `verified-dark130/`, `final-ui-light.txt`, `final-ui-dark130-rerun.txt`, `final-ui-light-rerun.txt`, dan `full-ui-regression.txt`. Folder outputs tidak dipublikasikan Git. Test screenshot dapat diulang melalui `FinanceEntryUiTest#glassFrontendCapturesWorkerManagerAndPreservesDraftNavigation`; jalankan instrumentation manual agar file external tidak terhapus oleh cleanup Gradle.

APK debug review: `outputs/frontend-glass/Inofarm-glass-debug.apk`, SHA256 `CC23D5CF474E1BE489E0C1E93824C3C590745064EDBA1ADA939ED7FD2A5204E2`. Bukan APK release produksi.

## Batas acceptance

Belum ada baseline screenshot, pengujian tablet/foldable, TalkBack manual menyeluruh, pengukuran frame/performa hardware, UAT lapangan atau audit kontras numerik seluruh kombinasi. Seluruh layar memperoleh tema/komponen bersama, tetapi screenshot setiap state seluruh layar bukan hasil sesi ini. Blur/refraksi Liquid Glass belum diimplementasikan. Ini peningkatan frontend native yang teruji pada cakupan di atas; bukan klaim semua gate master prompt atau handover produk telah selesai.
