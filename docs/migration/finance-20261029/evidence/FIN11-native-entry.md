# FIN11 native asset entry — 30 Oktober2026 WIB

Branch `codex/finance-assets-entry`; dependency `d545649` (native data core), `98a6fa8` (backend/math). Requirement: proposal PDF42–47, KF keuangan/riwayat/sinkronisasi, Q02 manual allocation dan Q10 precision. [Salinan proposal diterima](20261030-PROPOSAL_RECEIVED.md) dengan hash baseline yang sama.

## Implementasi

- Form terpisah aset dan alokasi penyusutan: sumber pembayaran pembelian aset aktif pada GH yang sama; aset/musim, tahun/periode penggunaan, nominal dan dasar eksplisit. Tidak mengisi nominal dari anggaran atau membagi musim otomatis. Review menampilkan nilai yang akan dikirim dan status lokal yang belum diterima server.
- ViewModel autosave, Back, ON_STOP, recreation dan flush terbaru sebelum logout; isian mentah yang tidak lengkap tetap disimpan tanpa membuat operasi. Kegagalan pemeriksaan sumber ketika antrekan dibuat mengembalikan form dengan alasan dan isian tetap tersedia.
- Riwayat aset/penyusutan, filter GH/musim/status/tanggal, detail, audit, koreksi, pembatalan beralasan dan perbandingan konflik. Financial conflict memakai alasan eksplisit tanpa kebijakan acuan agronomis; operasi lama/hash tetap immutable dan salinan lokal diarsipkan.
- SQLite8 additive menambah indeks asset/season untuk riwayat. Migration MySQL1–10 dan frozen global identity map schema8 tidak diubah. Fixture migrasi SQLite6 mempertahankan ciphertext/hash/blob dan tidak memakai DROP COLUMN yang tidak tersedia pada Android lama.
- Area tindakan keuangan dapat digulir dengan tinggi terbatas agar riwayat tetap mendapat tempat pada font200%/lebar320dp. Role pengelola dan generation/account scope tetap dipakai pada repository, context dan history.
- Runner `scripts/test-finance-assets.ps1` memerlukan PowerShell7, Java/SDK/PHP yang tersedia dan APK suffix `.finance`. Test API bind127.0.0.1:8001; router sementara privat memverifikasi nonce+PID proses PHP yang dibuat runner. Pemeriksaan HTTP langsung tanpa proxy. Tidak memakai listener yang tidak dikenali atau mematikan service lain. Fixture helper memasang TestDatabaseGuard sebelum SQL; hanya `inofarm_test`, akun/GH/parent UUID milik fixture. Instrumen aktif dihentikan sebelum cleanup saat runner gagal.

## Verifikasi

Environment: PHP8.5.12/BCMath/PDO MySQL3308 `inofarm_test`; Java25.0.2/Gradle9.3.1; finance APK `.finance`; API37 Android17 serta AVD baru API36.1 Android16 di `.runtime/avd-home`. Tidak menghapus data APK pengguna atau menjalankan migrasi pada `inofarm_local`.

| Pemeriksaan | Hasil dan batas |
|---|---|
| assembleDebug, testDebugUnitTest, lintDebug dengan `-PfinanceApp` | PASS:53 JVM tests,0 failure;0 lint errors,33warnings/5hints. |
| Tiga UI aset awal | PASS: form/review/recreation/accepted history; nominal alokasi tetap kosong sampai diisi; logout menyimpan raw tidak lengkap tanpa operasi. |
| Regresi69 perangkat API37 |66 PASS; dua lifecycle gagal karena jendela dialog ANR System UI mengambil fokus, satu fixture migrasi gagal karena android_metadata otomatis sudah ada. Ini bukan satu run69 PASS. |
| Fokus API36.1 |5/6 PASS termasuk dua lifecycle, migrasi yang diperbaiki, font200% dan sumber pembayaran berubah. Satu tes konflik awal salah memilih riwayat setelah kembali dari form; penantian/pemilihan diperbaiki dan rerun konflik1/1 PASS. Total71 kasus unik tercakup lewat regresi dan fokus, tanpa menyamakan rerun dengan satu suite bersih. |
| API nyata dengan penghentian proses |3/3 PASS pada API37: aset offline; restart/replay aset dan antrekan penyusutan offline; restart/replay penyusutan, audit, batal alokasi lalu aset dan worker403. MySQL memeriksa tepat2 audit/receipt/feed per entitas sesudah dua versi, bukan duplikasi replay. |
| API nyata dengan dua reboot OS |PASS3/3 dan SQL reconciliation pada API36.1: boot3 saat aset diantrekan, boot4 saat penyusutan diantrekan, boot5 saat verifikasi akhir. SQLite/enkripsi/session/outbox/hash bertahan; replay, audit/batal dan worker403 lulus. Tepat2 audit/receipt/feed per entitas; fixture dibersihkan. Tahap instrumen50.1/74.1/63.0detik mencakup UI/network/fixture, bukan pengukuran latency simpan KNF. |
| PHP helper/style, PowerShell parser, diff |PASS syntax/style dan parser; diff whitespace diperiksa. |
| Kontrak/context links |JSON schemas/goldens/OpenAPI refs PASS. Context checker tetap gagal pada tautan lama `docs/implementation/UI_REDESIGN.md` menuju ignored `../../output/liquid-glass-implementation/comparisons/all-nine.png`; tidak dibuat gambar palsu atau diubah riwayat tak terkait untuk meluluskan checker. |

Perintah inti: Gradle wrapper `-p android -PfinanceApp :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest` dengan class filter pada log; `scripts/test-finance-assets.ps1 -JavaHome ... -PhpPath ... -GradleHome ... -Device emulator-5556 -RebootBetweenPhases`. Runner mendukung proses restart default dan reboot opt-in. Angka100.00/0.00/3 tahun serta alokasi33.33 adalah sintetis.

Artefak lokal diabaikan Git: `tmp/fin11-native-regression.txt`, `tmp/fin11-api37-regression.xml`, `tmp/fin11-api36-focused.txt`, `tmp/fin11-asset-conflict.txt`, `tmp/fin11-real-final.txt`, `tmp/fin11-real-os-final-proof.txt`, `outputs/finance-assets/`, unit/lint/device XML. Manifest fixture memuat credential uji dan tidak dipublikasikan.

APK finance debug SHA256 pada acceptance akhir: `91e1945546f6de6b7e2dddcc79ca5409e8cda7027aff59854b8df398a582402e`. Selesai sekitar18:36 WIB. FIN11 implementation/automated acceptance lokal terverifikasi; tidak menutup gate handover keseluruhan.

## Batas

FIN12–17, gap administrasi/master/acuan, background sync/fresh-profile integrasi seluruh fitur, runtime API29/perangkat fisik, UAT/pentest dan gate akademik belum selesai. Run SDK37 yang mengalami System UI ANR tidak dianggap membuktikan stabilitas OS. Tidak ada produksi/deployment/restore/cutover/drop/clear data. Push GitHub403 tetap ditunda sesuai pengguna; hanya commit lokal diotorisasi.
