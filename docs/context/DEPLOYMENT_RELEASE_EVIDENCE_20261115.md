# Bukti fase D4: APK rilis (15 November 2026)

Branch `codex/deploy-android-release`, dari tip D3 (`ed5c928`). Temuan [P0-3](DEPLOYMENT_READINESS_AUDIT_20261114.md): build `release` tanpa `signingConfig` dan URL API kosong. Keputusan pemilik G3: pemilik yang membuat dan menyimpan keystore; repo hanya membaca nilainya dari luar.

Label bukti: **diuji** (ada output perintah), **dibaca dari kode**, **belum diverifikasi**.

## Perubahan (`android/app/build.gradle.kts`)

- `signingConfigs.release` dibuat hanya bila `INOFARM_RELEASE_STORE_FILE` ada, dari Gradle property (`%USERPROFILE%\.gradle\gradle.properties`) atau variabel lingkungan, bersama `INOFARM_RELEASE_STORE_PASSWORD`, `INOFARM_RELEASE_KEY_ALIAS`, `INOFARM_RELEASE_KEY_PASSWORD`. Tidak ada nilai rahasia di repo; `.gitignore` sudah mengecualikan `*.jks`, `*.keystore` dan `keystore.properties`.
- Task `checkReleaseConfig` dijalankan sebelum `preReleaseBuild`: rilis ditolak bila `-PinofarmFinanceApiUrl` bukan `https://…/api/v2/`, dan memperingatkan bila keystore belum diisi (APK keluar tanpa tanda tangan). Build debug dan profile tidak terpengaruh.
- `versionCode` 2 → 3, `versionName` 0.2.0 → 0.3.0.

## Pemeriksaan

| Perintah | Hasil | Label |
|---|---|---|
| `:app:assembleDebug` tanpa properti | BUILD SUCCESSFUL | diuji |
| `:app:assembleRelease` tanpa URL | gagal dalam 1 detik: `Release needs -PinofarmFinanceApiUrl=https://<server>/api/v2/ (got '')` | diuji |
| `:app:assembleRelease -PinofarmFinanceApiUrl=http://…/api/v2/` | gagal dengan pesan yang sama | diuji |
| `:app:assembleRelease -PinofarmFinanceApiUrl=https://pilot.example.invalid/api/v2/` tanpa keystore | BUILD SUCCESSFUL, peringatan `Release APK is unsigned…`, keluaran `app-release-unsigned.apk` | diuji |
| Sama, dengan keystore sekali pakai di folder sementara (dihapus setelahnya) | BUILD SUCCESSFUL, `app-release.apk`; `apksigner verify` lulus dengan skema v2; `aapt2 dump badging`: `com.inofarm.jogja`, versionCode 3, versionName 0.3.0 | diuji |
| `git grep` pola password/kunci privat di `android`, `scripts`, `docs` | tidak ada temuan | diuji |

## Batas dan yang belum diverifikasi

- Keystore asli belum dibuat; itu tugas pemilik (G3). Kehilangan keystore berarti pembaruan tidak bisa dipasang di atas versi lama.
- APK rilis (minify dan shrink aktif) belum dipasang dan dijalankan di ponsel fisik terhadap server HTTPS nyata; server pilot belum ada (P0-2).
- Branch ini dibuat dari D3, sedangkan branch frontend F1 juga mengubah `build.gradle.kts` (baseline profile). Penggabungan keduanya perlu menyatukan dua bagian itu secara manual.
