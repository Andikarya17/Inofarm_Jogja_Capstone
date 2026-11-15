# Bukti fase F5: polish akhir (15 November 2026)

Branch `codex/frontend-polish`, bertumpuk di atas `codex/frontend-delight` (F4). Temuan dari [audit frontend](FRONTEND_MOTION_AUDIT_20261114.md): P1-4 (ripple), P2-2 (tarik untuk memperbarui), P2-8 (target 48 dp), P2-9 (jendela dan splash), P2-10 (ikon adaptif), P3-1, P3-2, P3-3, serta celah `DESIGN.md` "isian nominal belum menampilkan pemisah ribuan". Backend, API, kontrak dan schema SQLite tidak berubah.

Label bukti: **diuji** (ada output perintah atau gambar), **dibaca dari kode**, **belum diverifikasi**. Emulator `Inofarm_Botanical_API30` (Android 11, 1080×2340, 440 dpi), `-gpu swiftshader_indirect`, fixture sintetis.

## Perubahan

| Temuan | Perubahan | Berkas |
|---|---|---|
| P1-4 ripple | Pintasan Beranda (`FarmQuickAction`), kartu greenhouse pengelola, tombol Catat di dock dan baris sheet memakai `ripple()` Material 3 yang dipotong ke bentuknya (`FarmShape.Card`, lingkaran) | `CanopyKit.kt`, `ManagerHomeCanopy.kt`, `ManagerSheets.kt` |
| P2-8 | `FarmSegmentedTabs` setinggi `FarmSpace.Touch` (48 dp), sebelumnya 44 dp | `CanopyKit.kt` |
| P3-1 | Jeda stagger dikalikan `MotionDurationScale`, jadi "Hapus animasi" membuatnya nol | `CanopyKit.kt` |
| P3-2 | Warna mentah menjadi `FarmColors.ForestSurface`, `ForestContainer`, `ForestPill`, `WarningSurfaceDark` | `InofarmTheme.kt`, `CanopyKit.kt`, `FarmChrome.kt` |
| P2-9 | `windowBackground` forest `#132820` di semua mode, status bar terang dimatikan; atribut splash Android 12 di `values-v31` dan `values-night-v31` (latar forest, ikon adaptif) | `res/values*/styles.xml`, `res/values/colors.xml` |
| P2-10 | Adaptive icon: latar forest, daun sage dari ikon lama, lapisan monokrom untuk ikon bertema | `res/drawable/ic_launcher_*.xml`, `res/mipmap-anydpi-v26/`, `AndroidManifest.xml` |
| P3-3 | `background_login.jpg` dan `ic_inofarm.xml` yang tidak dipakai dihapus | `res/` |
| P2-2 | `FarmPullRefresh` (`PullToRefreshBox`) pada Beranda pekerja, Riwayat dan Keuangan; memakai `syncing` dan aksi sinkron yang sudah ada | `FinanceRoot.kt` |
| Pemisah ribuan | `IdrGrouping` (`VisualTransformation`): isian nominal menampilkan `1.234.567.890,50` sementara nilai yang divalidasi dan dikirim tetap angka yang diketik (titik yang diketik menjadi koma desimal, lihat tinjauan butir 1). Masukan yang bukan angka biasa tampil apa adanya, jadi galat validasi tidak tersembunyi | `IdrGrouping.kt`, pengeluaran, penjualan, aset, investasi |
| Form | Tanggal dan jam kegiatan pengelola berdampingan bila baris setidaknya 340 dp (`FarmFieldPair`, bertumpuk di atas skala font 1,3); pada font besar tombol "Simpan sebagai draf" mendapat tiga baris, padding sempit dan lebar setara | `FinanceRoot.kt`, `FormCanopy.kt` |

## Pemeriksaan

| Pemeriksaan | Hasil | Label |
|---|---|---|
| `testDebugUnitTest` | 119 tes, 0 gagal (termasuk `IdrGroupingTest`: pengelompokan, nol di depan, pemetaan kursor bolak-balik) | diuji |
| `lintDebug` | 0 error, 18 warning, 1 hint (sama dengan F4) | diuji |
| `AllSentMomentUiTest`, `DestinationMotionUiTest`, `InteractionHardeningUiTest` | 7 dari 7 lulus, sebelum dan sesudah perbaikan tinjauan | diuji |
| `glassFrontendCapturesWorkerManagerAndPreservesDraftNavigation` di terang, gelap, font 200% dan 360 dp (`wm density 480`) | 4 dari 4 lulus, sebelum dan sesudah perbaikan tinjauan; termasuk nominal `1.234.567.890,50` bertahan setelah recreate dan tampil sama di Periksa | diuji |
| `FinanceEntryUiTest` penuh (APK sebelum perbaikan tinjauan) | 65 tes, 17 gagal (F4: 19) | diuji |

Dibanding F4, `attachmentReasonFailedFlush…`, `logoutFlushesIncomplete…` dan `offlineQueueRecreation…` lulus. Satu yang baru gagal, `workerHistoryShowsCheckStatusFiltersToOwnRecordsAndPicksDates` (batas tunggu 15 detik), lulus 2 dari 2 saat dijalankan sendiri dengan APK akhir: flake, dibawa ke Q1. `twoFieldLoginHasAccessiblePasswordToggle…` gagal di semua konfigurasi karena menunggu teks lama "Alamat server belum dikonfigurasi…", sedangkan aplikasi kini menulis "Alamat aplikasi belum diatur. Hubungi pengelola."; diperbaiki di Q1.

Gambar: [evidence/f5-20261115](evidence/f5-20261115/) memuat login, Beranda kedua peran, halaman greenhouse, Riwayat, form kegiatan pekerja dan form pengeluaran di empat konfigurasi, serta jendela pembuka dan peluncur (API 30).

### Tinjauan `impeccable-finish-reviewer`

Tujuh perbaikan material, semuanya diterapkan pada commit `fix(polish): finish-review fixes…`:

1. Titik yang diketik pada isian nominal terbaca parser sebagai desimal, padahal layar menampilkan titik sebagai pemisah ribuan (contoh: `15.000` tersimpan 15). Kini titik yang diketik pada isian ber-`IdrGrouping` langsung menjadi koma, jadi layar selalu menampilkan arti yang disimpan (`15,000`). Angka berawalan nol tidak dikelompokkan. Pemetaan titik ke koma **dibaca dari kode**; belum ada tes UI khusus.
2. Pada font 200% awal nominal terpotong dan sufiks menempel: kini sufiks dilepas, ukuran turun ke `titleMedium`, dan teks bantu membacakan `Tertulis: 1.234.567.890,50` (terlihat di gambar).
3. Ripple krem tidak terlihat di atas sage (tombol Catat, baris nutrisi): kini berwarna ink.
4. Snackbar memakai ungu bawaan Material: kedua skema kini mengisi `inverseSurface`, `inverseOnSurface`, `inversePrimary` (terlihat di gambar Riwayat terang dan gelap).
5. Tanggal dan jam berdampingan kemungkinan terpotong pada 360 dp: kini hanya berdampingan bila baris setidaknya 340 dp. Form kegiatan pengelola pada 360 dp belum dipotret.
6. Indikator tarik bisa tertinggal aktif bila tarikan tidak memulai sinkron: kini dilepas setelah 300 ms.
7. Ikon bertema tidak benar-benar memotong batang dari daun: kini urat dihapus dan hanya batang di luar daun digambar.

## Kontras token baru (dihitung, WCAG)

| Pasangan | Rasio |
|---|---|
| Sage `#D4E4B7` di atas forest `#132820` (splash, ikon) | 11,52:1 |
| Sage di atas `ForestSurface` `#203B30` | 9,01:1 |
| Sage di atas `ForestContainer` `#263F34` | 8,45:1 |
| Sage di atas `ForestPill` `#385442` | 6,20:1 |
| Krem `#F4F3E8` di atas `ForestPill` | 7,49:1 |
| Krem di atas `WarningSurfaceDark` `#493519` | 10,42:1 |

## Batas dan yang belum diverifikasi

- Emulator Android 11 tidak menampilkan splash Android 12; atribut `values-v31` hanya **dibaca dari kode**. Pada Android 11 jendela pembuka berwarna forest (terlihat di gambar).
- Ikon bertema (monokrom) hanya berlaku Android 13 ke atas; belum dilihat.
- Pada font 200% nominal panjang `1.234.567.890,50` menggulir di dalam isian satu baris sehingga awalnya terpotong saat kursor di akhir. Ini perilaku isian satu baris biasa; nilainya utuh di langkah Periksa.
- Tombol "Tambah" bergaris putus-putus dari mockup belum dibuat.
- Getar ripple dan tarikan pada perangkat fisik, TalkBack nyata, dan layar di atas 600 dp belum diperiksa.
