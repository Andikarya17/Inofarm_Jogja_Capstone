# FIN16 — administrasi native, 2 November 2026 WIB

Dependency GitHub Cruelasss `e0dc9ac` (`codex/finance-backup-recovery`), mencakup FIN11–15. Branch `codex/finance-admin-entry`. Ini menutup gap editor native KF02/KF03/U03, bukan penerimaan seluruh FIN16/17.

## Perubahan dan acceptance

- `feature/admin/`: formulir terstruktur Indonesia untuk greenhouse, musim/kelompok, varietas/satuan/bahan/grade/kategori, akun/penugasan, jadwal/target; tambah/ubah/nonaktif serta tutup/buka musim memakai kontrak API v2 existing. Parent dipilih dari daftar server berhalaman, tidak diisi UUID manual. Daftar utama mempunyai tombol halaman berikutnya. Tidak mengubah migration, kalkulasi, receipt atau runtime legacy.
- Tombol **Kelola akun, budidaya dan acuan** di beranda pengelola, termasuk akun baru tanpa greenhouse. Worker tidak mempunyai entry atau akses repository. Server tetap memeriksa current role/assignment/parent/version.
- Satu workspace administrasi terenkripsi per server+akun; raw draft/autosave dan flush sebelum Back/ON_STOP. Pengajuan memerlukan review eksplisit. Journal pending ditulis sebelum HTTP dan menyimpan envelope/ID/hash yang sama untuk retry sesudah respons hilang atau reopen. Pengajuan pending tidak ditimpa edit. Rejection validasi/konflik dapat ditinjau eksplisit; epoch/scope/hasil tidak pasti tidak dibuang otomatis. Respons sesi lama ditolak.
- Password akun hanya di memori; journal tidak menyimpan plaintext maupun ciphertext password. Jika pengajuan akun memerlukan retry, password awal harus dimasukkan lagi dan hash envelope lengkap harus cocok sebelum HTTP. Non-secret draft tetap durable. Pengajuan admin bersifat online/explicit retry, terpisah dari antrean operasional. Satu draf yang belum selesai tidak ditimpa saat memilih data lain.
- Nilai kosong/0 berbeda, integer/tanggal/waktu/enum divalidasi, waktu API fractional UTC dinormalisasi ke format request detik+offset. Server melakukan validasi relasi, range, overlap dan aturan bisnis akhir; alasan server ditampilkan.

## Verifikasi aktual

Windows, PHP8.5.11, MySQL8.0.30/3307 **inofarm_test**, Java25.0.4, emulator API37 `emulator-5554`, suffix `.finance`. Akun test baru diberi hak hanya `inofarm_test`; database pengguna tidak dimigrasi/reset. Log privat di ignored `tmp/`.

- `build-android.ps1 -Tasks @('-PfinanceApp',':app:assembleDebug',':app:assembleDebugAndroidTest',':app:testDebugUnitTest',':app:lintDebug')`: PASS, **76 JVM, 0 lint errors/34 warnings/5 hints**, 1m40s, `fin16-admin-build-final.log`. Tambahan warning adalah saran KTX SharedPreferences; commit sinkron sengaja diperiksa keberhasilannya.
- `FinanceAdminTest,FinanceLocalStoreTest,FinanceSyncCoordinatorTest`: **31 PASS/3.901s**, `fin16-admin-device.log`. Lima kasus admin mencakup retry identical setelah reopen, secret exclusion/hash, stale autosave, current role/session isolation dan epoch mismatch.
- `FinanceAdminUiTest`: **2 PASS/6.554s**, `fin16-admin-ui-awake.log`: font200%/scroll/Back/resume, konfirmasi sebelum mutation. Dua percobaan awal gagal karena emulator asleep (tidak ada hierarchy Compose); setelah wake/unlock, suite sama lulus. Bukan bukti perangkat fisik atau UAT.
- Backend regression **132 tests, 2088 assertions, 1 failure** awal: Dompdf belum terpasang pada checkout FIN10 lokal. `composer install` dari lockfile FIN14 berhasil tanpa perubahan dependency. Rerun terarah `FinanceReportExportTest`: **5 PASS/102 assertions/9.717s**. Full regression final dicatat pada FIN16 integrasi; dua DemoHistory legacy tetap dikecualikan secara eksplisit, tidak dihapus.
- Contract checker PASS. Context checker PASS termasuk hash arsip dan KF14/KNF8/U21. Satu kompilasi test awal gagal karena API `keySet` tidak ada pada Android JSONObject; diganti iterator `keys`, build/unit final lulus.

## Batas

Device admin memakai transport sintetis; authorization/relasi/audit actual MySQL berasal regression existing, bukan klaim alur admin real-device lengkap. Admin belum diuji pada API29/perangkat fisik. Pemeriksaan relasi/dimensi/overlap authoritative di backend; UAT dapat menentukan penyempurnaan field error/selector. Satu workspace per akun, bukan multi-tab editor. Tidak ada deploy/cutover/pentest atau persetujuan akademik. Background scheduling/fresh profile serta regresi/KNF ditangani pada lanjutan integrasi.
