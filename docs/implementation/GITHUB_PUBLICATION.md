# Snapshot awal GitHub dan instalasi tim

Tanggal: 24 Oktober 2026, Asia/Jakarta.

Pengguna meminta satu first commit berisi seluruh source/folder proyek yang layak
dipublikasikan, panduan instalasi Android Studio untuk teman, serta pengecualian
file sensitif. Target: https://github.com/Andikarya17/Inofarm_Jogja_Capstone.git.

## Struktur Git

- `origin` tetap GitLab UMY; remote tambahan `github` memakai alamat di atas.
- Branch kerja publikasi `codex/github-initial-publication` dibuat sebagai orphan
  untuk first commit snapshot sesuai permintaan terbaru. Branch/riwayat GitLab
  existing dipertahankan; tidak ada reset, force push, atau penghapusan branch.
- Snapshot mencakup source existing, perubahan lokal demo dan paket diagram yang
  sudah ada saat task dimulai, serta panduan dan aturan ignore baru. Ini publikasi
  baseline pertama yang diminta pengguna, bukan perubahan fitur aplikasi baru.
- Push baseline hanya ke `github/main`. Riwayat awal GitHub terpisah dari GitLab;
  jangan push branch orphan ini ke `origin/main` atau memakai `--all`/`--mirror`.
  Pengembangan berikutnya tetap branch per fitur. Penyatuan riwayat dua remote
  membutuhkan pekerjaan integrasi tersendiri jika kelak diperlukan.

## Isi dan keamanan

[Cara Instalasi](../../Cara%20Instalasi.md) menjelaskan clone, versi sesuai source,
PHP/Composer/MySQL, environment baru, provisioning owner, Gradle Sync dari folder
`android`, emulator/ADB reverse, demo opsional dan test database terpisah.
Source runnable masih implementasi IoT lama; rancangan tanpa IoT belum dimigrasikan.

`.gitignore` ditambah untuk credential JSON, private/signing key, database lokal,
editor/cache/temp. Environment nyata, dependency, runtime, log, output build dan
konfigurasi firmware privat tetap dikecualikan. Template dan wrapper dipertahankan.
Sertifikat TLS localhost dalam `android/app/src/androidTest/assets/mock-server.p12`
adalah fixture publik, seperti dijelaskan README assets; tidak ada pada APK aplikasi produksi.

Kandidat snapshot diperiksa untuk file privat/hasil build, pola token/private key,
credential dalam URL dan kecocokan nilai secret environment lokal yang tersedia.
Temuan URL `user:password@example.invalid` pada ContractTest merupakan uji penolakan
credential dalam URL. Literal password lainnya yang terdeteksi adalah template,
validation rule, atau fixture test; bukan credential owner/database/perangkat nyata.
Pemeriksaan ini bukan pentest atau jaminan absolut ketiadaan secret.

## Verifikasi lokal sebelum publikasi

- Backend: database guard PASS 4 tests / 12 assertions; full suite PASS 20 tests /
  404 assertions, hanya MySQL `inofarm_test` pada 3307. Percobaan awal gagal koneksi
  karena MySQL belum hidup; dihentikan, instance workspace dinyalakan lewat script
  existing dengan path MySQL yang benar, lalu suite diulang dan lulus.
- Composer validate strict, check-platform-reqs dan Pint `--test` PASS.
- Constraint PHP dependency Symfony pada lockfile diperiksa: minimum efektif
  8.4.1 walaupun composer.json mengizinkan ^8.3; panduan/README dikoreksi sesuai lockfile.
- Android: assembleDebug, 10 unit tests dan lintDebug PASS (0 errors, 28 warnings,
  4 hints); sebagian task UP-TO-DATE. JDK 25 dari instalasi saat ini diberikan
  eksplisit setelah helper tidak menemukan path JBR lama. Peringatan SDK XML dan
  JAVA_HOME yang menunjuk executable dicatat; konfigurasi Java global tidak diubah.
- Demo/full suite memerlukan port MySQL 3307 sesuai guard; panduan membedakannya
  dari setup API normal yang juga mendukung 3306.
- Template `.env.example`/`.env.testing.example`, Gradle wrapper dan fixture TLS
  dapat di-track; contoh `.env`, credential/runtime, signing key, local.properties
  serta firmware secrets/config ditolak oleh aturan ignore.

Keberhasilan push harus diperiksa dengan kesamaan hash lokal dan remote sesudah
push. Tidak ada deployment, flashing, reset data owner atau perubahan source
produk. Instalasi pada komputer teman, instrumentation baru, React/firmware build
ulang dan pengujian fisik tidak dilakukan pada task dokumentasi/publikasi ini.
