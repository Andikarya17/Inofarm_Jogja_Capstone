# Bukti fase F2: hardening interaksi (15 November 2026)

Branch `codex/frontend-harden`, dibuat dari `origin/main` `6eef4c7`. Fase ini menutup empat temuan dari [audit frontend](FRONTEND_MOTION_AUDIT_20261114.md): FE-P1-2, FE-P1-5, FE-P2-3 dan FE-P3-4 (keputusan pemilik G7). Backend, API, kontrak dan schema SQLite tidak berubah.

Label bukti: **diuji** (ada output perintah), **dibaca dari kode**, **belum diverifikasi**. Semua bukti layar berasal dari emulator `Inofarm_Botanical_API30` (Android 11, 1080x2340, 440 dpi) dengan komponen yang diisi data sintetis, bukan perangkat fisik atau backend nyata.

## Perubahan per temuan

### FE-P1-2: simpan tanpa layar spinner penuh

- `FinanceRoot.kt`: cabang `asset.entry.busy || sale.busy || activity.busy || harvest.busy || financial.busy` (layar "Menyimpan isian ke perangkat…") dan `runPhase == 4` (layar "Mengirim catatan…") dihapus. Form tetap tampil; pada alur "Cek nutrisi semua greenhouse" langkah Periksa tetap tampil selama `runPhase == 4`.
- Selama simpan: fokus dilepas (`clearFocus`), sentuhan pada isi ditahan oleh `Modifier.holdTouches`, dan Back ditahan pada `runPhase == 4`. Back pada form sudah ditahan sebelumnya lewat `writing` (`FinanceRoot.kt`, gabungan status `busy` semua form; dibaca dari kode).
- `FormCanopy.kt`, `FarmFormBar`: parameter baru `busy` dan `busyLabel`. Kedua tombol nonaktif segera saat `busy`. Bila proses melewati `FarmMotion.BusyReveal` (300 ms), tombol utama menampilkan `Menyimpan…` atau `Mengirim…` dengan indikator kecil, mempertahankan warna isi (dibaca sebagai sedang bekerja, bukan tidak tersedia), dan diumumkan lewat live region `Polite`. Pada skala font di atas 1,3 indikator disembunyikan supaya kata status muat. Kedua tombol disamakan tingginya (`IntrinsicSize.Min`) supaya label yang terlipat pada font besar tidak membuat tinggi tombol berbeda.
- `FarmReviewFrame` meneruskan `busy`; label `Mengirim…` dipakai bila tombol utama diawali "Kirim".
- Form yang meneruskan `busy`: kegiatan/pengukuran dan langkah nutrisi (`FinanceRoot.kt`), panen, biaya/alokasi, penjualan, aset/penyusutan, admin dan wizard greenhouse, serta `NutritionRunReview`.

### FE-P1-5: autofill login

- `FinanceLogin.kt`: isian Email membawa `contentType = ContentType.EmailAddress + ContentType.Username`, isian Password `ContentType.Password`. Saat Masuk ditekan, `LocalAutofillManager.current?.commit()` dipanggil supaya password manager bisa menawarkan simpan. Aplikasi tidak menyimpan password.

### FE-P3-4 (G7): email terakhir dan "Bukan saya"

- Berkas baru `feature/auth/LastLoginEmail.kt`: SharedPreferences privat `inofarm_login_hint`, satu kunci `email`, dinormalisasi `trim().lowercase()` seperti permintaan login.
- `FinanceRoot.kt` menyimpan email hanya setelah sesi tersedia (login diterima server). Email yang diketik lalu ditolak tidak disimpan.
- Login mengisi email yang diingat. Aksi `Bukan saya` (tag `finance-forget-email`, label aksesibilitas "Kosongkan email yang diingat perangkat ini") berada di baris label Email dan hanya tampil selama isian masih berisi email yang diingat. Menekannya menghapus nilai dari perangkat dan mengosongkan isian.

### FE-P2-3: placeholder Beranda pekerja

- `WorkerGreenhouseCards.kt`, `WorkerGreenhouseList`: bila cache perangkat belum menjawab dalam `FarmMotion.PlaceholderReveal` (150 ms), tampil teks `Memuat data perangkat…` (live region) dan 1 sampai 3 kartu placeholder diam berbentuk kartu greenhouse (tanpa shimmer, disembunyikan dari TalkBack). Kartu nyata menggantikannya dengan crossfade 200 ms (`FarmMotion.State`). Bila cache menjawab sebelum 150 ms, kartu langsung tampil tanpa transisi.
- Galat baca cache kini punya pesan sendiri: `Data perangkat belum bisa dibaca. Perbarui untuk mencoba lagi.`
- Beranda pengelola tidak diubah: datanya berasal dari `manager-home` dan sudah punya status muat sendiri.

### Dokumen

- `DESIGN.md`: bagian login (autofill, email terakhir) dan bagian gerak (simpan di tempat, placeholder).
- `FRONTEND_MOTION_AUDIT_20261114.md`: baris status pada FE-P1-2, FE-P1-5, FE-P2-3, FE-P3-4.

## Pemeriksaan

| Pemeriksaan | Hasil | Label |
|---|---|---|
| `scripts/build-android.ps1` dengan `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebugAndroidTest` (JDK 25.0.4) | BUILD SUCCESSFUL; JVM 112 tes, 0 gagal, 0 dilewati; lint 0 error, 18 warning (sama dengan baseline audit 14 November) | Diuji |
| `InteractionHardeningUiTest` (3 tes) mode terang, font 1,0 | OK (3 tests) | Diuji |
| `InteractionHardeningUiTest` mode gelap, font 1,0 | OK (3 tests) | Diuji |
| `InteractionHardeningUiTest` mode terang, font 2,0 | OK (3 tests) | Diuji |
| `FinanceEntryUiTest` penuh (65 tes) | 18 dari 65 gagal pada build akhir F2; tidak ada regresi F2 setelah kontrol (rincian di bawah) | Diuji |
| `python scripts/check-finance-context.py` | Lulus: 86 dokumen, 450 tautan, 8 hash arsip, 0 galat (pada working tree, termasuk dokumen pemilik yang belum di-commit) | Diuji |
| Kontras (`antislop-human/contrast-check.py`) | Semua pasangan lulus AA, terendah 5,52:1 (tabel di bawah) | Diuji |

Isi `InteractionHardeningUiTest`:

1. `formBarReportsSavingInPlaceOnlyAfterTheRevealDelay`: 200 ms setelah `busy`, teks `Menyimpan…` belum ada tetapi kedua tombol sudah nonaktif dan klik tidak memanggil aksi; setelah 300 ms tombol utama bertuliskan `Menyimpan…` dan tombol draf tetap terlihat; setelah selesai kembali ke `Periksa`.
2. `loginOffersTheRememberedEmailAndBukanSayaRemovesIt`: email tersimpan `  Pekerja@Example.Invalid ` tampil sebagai `pekerja@example.invalid`; kedua isian punya `ContentType` (email: kunci terdefinisi, karena `ContentType` gabungan tidak punya kesetaraan nilai; password: sama dengan `ContentType.Password`); `Bukan saya` menghapus nilai tersimpan dan isian; mengetik email lain tidak memunculkan aksi dan tidak menyimpan apa pun.
3. `workerHomeShowsPlaceholdersOnlyWhenTheCacheIsSlow`: sebelum 150 ms tidak ada placeholder; sesudahnya teks dan placeholder tampil; data tiba menggantikan placeholder; galat baca menampilkan pesan galat.

### `FinanceEntryUiTest` dibanding baseline

Dijalankan 15 November 2026 sekitar pukul 01.35 WIB lewat `adb shell am instrument -w -r -e class com.inofarm.jogja.FinanceEntryUiTest`, emulator `night yes`, font 1,0. Baseline pembanding: 17 nama di [bukti pekerja Canopy](WORKER_CANOPY_EVIDENCE_20261112.md) (16 kegagalan akhir ditambah `assetHubRemainsScrollableWithFont200Percent`).

Gagal pada build akhir (18 nama): `assetConflictReviewPreservesRawLocalNameAndOldOperation`, `attachmentReasonFailedFlushRetainsSessionAndTextThroughRecreation`, `attachmentReasonLogoutFlushesLatestRawTextWithoutChangingAcceptedMetadata`, `attachmentReviewRemainsReachableAtLargeFontNarrowAndLandscape`, `conflictKeepLocalRequiresReasonAndManagerCanAuditAndCancel`, `conflictUseServerPreservesLocalCopyAndWorkerHasNoCancel`, `harvestBalanceRejectionCanBeCorrectedAfterExplicitReview`, `harvestOwnCorrectionConflictUsesNewVersionAndRetainsLocalArchive`, `longContextAndExpiredSessionKeepNavigationReachable`, `managerHomeFallsBackWhenSelectedGreenhouseIsRevoked`, `managerHomeOneTapFiltersRotationAndCachedSnapshot`, `managerRecordsObservationsAndEditsMeasurementTarget`, `managerSeasonCloseAndReopenUseReasonedVersionedQueue`, `managerWalkthroughVisitsPickersScheduleToolsAndAdminWithoutSubmitting`, `queueReviewOpensConflictedRecordAndBackReturnsToSameQueue`, `secondaryMenusReflowAndKeepSheetActionsReachable`, `twoFieldLoginHasAccessiblePasswordToggleAndHandlesMissingConfiguration`, `workerCorrectionHasNoReferenceChoiceAndUsesPlainButtons`.

| Perbandingan | Nama | Pemeriksaan lanjut |
|---|---|---|
| Baseline, kini lulus | `assetHubRemainsScrollableWithFont200Percent`, `workerHistoryShowsCheckStatusFiltersToOwnRecordsAndPicksDates` | |
| Lulus pada run ini, gagal pada run F2 sebelumnya | `workerAgendaDistinguishesRealizationsAndSkippedRecords` | Mendukung dugaan ketergantungan jam (catatan untuk Q1) |
| Baru gagal | `workerCorrectionHasNoReferenceChoiceAndUsesPlainButtons` (`ComposeNotIdleException`) | Sendiri pada build F2: lulus 2 dari 2. Flake |
| Baru gagal | `attachmentReasonLogoutFlushesLatestRawTextWithoutChangingAcceptedMetadata` | Build F2: gagal 2 dari 2. **Kontrol `main` `6eef4c7`**: lulus 2 dari 6, gagal 4 dengan galat yang sama. Tidak stabil di emulator ini, bukan regresi F2. Nama ini ada di baseline awal 21 nama |
| Baru gagal | `attachmentReasonFailedFlushRetainsSessionAndTextThroughRecreation` | Build F2: gagal 2 dari 2. **Kontrol `main`**: lulus 1 dari 6, gagal 5 dengan galat yang sama. Bukan regresi F2 |

Kedua tes lampiran gagal di helper `logoutFromAccount` (`FinanceNavigationActions.kt:78`) dengan `AssertionError: Failed to inject touch input` saat mengetuk `finance-logout` di menu akun. Layar lampiran tidak memakai `FarmFormBar` dan status `busy` lampiran tidak termasuk penahan sentuhan F2. Diserahkan ke Q1.

### Kontras pasangan baru

| Teks | Latar | Rasio |
|---|---|---|
| `Menyimpan…` putih (terang) | Brand `#2B503D` | 9,06:1 |
| `Menyimpan…` Ink `#142B23` (gelap) | Sage `#D4E4B7` | 11,13:1 |
| `Memuat data perangkat…` Muted `#52665B` (terang) | Surface `#FFFDF4` | 6,04:1 |
| sama | Background `#F4F3E8` | 5,52:1 |
| `Memuat data perangkat…` `#C4CEC3` (gelap) | Surface `#203B30` | 7,49:1 |
| sama | Background `#132820` | 9,59:1 |
| `Bukan saya` Brand (terang) | Surface `#FFFDF4` | 8,89:1 |
| `Bukan saya` Sage (gelap) | Surface `#203B30` | 9,01:1 |

Tombol `Simpan draf` yang nonaktif memakai warna nonaktif Material 3; WCAG 1.4.3 mengecualikan kontrol nonaktif.

## Bukti visual

Folder `docs/context/evidence/f2-20261115/`, semua dari build akhir 15 November 2026, emulator `Inofarm_Botanical_API30`, ditangkap oleh `InteractionHardeningUiTest` (`captureToImage`, 1080x2063 tanpa bilah sistem):

| Berkas | Isi |
|---|---|
| `f2-form-bar-saving-light.png`, `-dark.png`, `-light-font200.png` | Bar form saat simpan melewati 300 ms: tombol draf nonaktif, tombol utama `Menyimpan…` dengan indikator (indikator disembunyikan pada font 200%) |
| `f2-login-remembered-email-light.png`, `-dark.png`, `-light-font200.png` | Login dengan email yang diingat dan aksi `Bukan saya` di baris label |
| `f2-worker-home-placeholder-light.png`, `-dark.png`, `-light-font200.png` | Placeholder kartu greenhouse saat cache lambat |

Mode emulator semula `night yes`, `font_scale 1.0`; dibaca sebelum uji dan dikembalikan sesudahnya.

## Delivery Gate antislop (mode `during`)

Design Read: layar operasional Android untuk pekerja lapangan dan pengelola, arah Canopy dari `DESIGN.md`, dial form ENERGY 1 / RHYTHM 2 / MOTION 2 dan login 2 / 2 / 2 (dari `DESIGN.md`).

### Block 1: Hard Gate

| Butir | Hasil | Bukti |
|---|---|---|
| R-02 em dash | PASS | Teks UI baru: `Menyimpan…`, `Mengirim…`, `Bukan saya`, `Memuat data perangkat…`, pesan galat cache; tidak ada em dash |
| R-03 mobile | PASS | Screenshot font 200%: label tombol dan `Bukan saya` muat; indikator disembunyikan di atas 1,3. Lebar 360 dp belum dipotret (batas) |
| R-17, R-18, R-36, R-38 angka, testimoni, klaim, konten rekaan | PASS | Tidak ada angka atau klaim baru; email contoh di tes memakai domain `example.invalid` |
| R-23 aset | PASS | Tidak ada aset baru; placeholder berupa bentuk netral dari token |
| R-24 navigasi | PASS | Navigasi tidak berubah |
| R-25 kontras | PASS | Tabel kontras di atas, terendah 5,52:1 |
| R-26 kontrol hidup | PASS | `Bukan saya` menghapus email (tes 2); tombol bar nonaktif hanya selama `busy` dan kembali aktif (tes 1) |
| R-27 status | PASS | Beranda pekerja kini punya muat (placeholder), kosong (teks penugasan yang sudah ada) dan galat (pesan baru) |
| R-32 keyboard dan fokus | PASS dari kode | Kontrol Material 3 bawaan dengan fokus bawaan; fokus dilepas saat simpan. Navigasi keyboard fisik belum diuji |
| R-33 patch skrip | PASS | Semua perubahan ditulis langsung di source Kotlin |
| R-34 tema | PASS | Tiga tes lulus dan dipotret di terang dan gelap |
| R-35 dijalankan | PASS dengan batas | Build dan tes dijalankan; klik `Bukan saya`, klik tombol bar saat sibuk, dan pergantian placeholder dijalankan oleh tes. Status sibuk di aplikasi nyata tidak bisa dipotret karena simpan lokal selesai sebelum 300 ms |
| R-37 arah | PASS | `DESIGN.md` Canopy dipakai, tanpa warna baru |
| R-39 semantik | PASS dengan batas | `Bukan saya` punya label aksi; live region pada status sibuk dan teks memuat; placeholder `clearAndSetSemantics`. TalkBack nyata belum dijalankan |
| R-40 performa | PASS | Tidak ada angka performa yang diklaim; placeholder mencadangkan ruang supaya daftar tidak meloncat |
| R-28 FAQ | Tidak berlaku | |

### Block 2: Purpose-Gate

| Butir | Hasil | Alasan tertulis |
|---|---|---|
| R-19 animasi | PASS | Crossfade label tombol 160 ms: menunjukkan status berubah di tempat. Crossfade placeholder ke kartu 200 ms: menghindari kedip saat data tiba. Penundaan 300 ms dan 150 ms: simpan atau baca cepat tidak menampilkan status sama sekali. Tidak ada animasi tak berhingga selain indikator yang hanya ada selama proses berjalan (K5) |
| R-01, R-04, R-06, R-07, R-08, R-09, R-10, R-12, R-13, R-14, R-22 | PASS | Tidak ada gradien, ikon, tipografi, latar, panah, badge, glass, bayangan, glow, kartu fitur atau ilustrasi baru. Bayangan bar form sudah ada sebelumnya |

### Block 3: Liveliness

| Butir | Hasil | Bukti |
|---|---|---|
| Dial dinyatakan | PASS | Design Read di atas |
| Konsisten dengan dial | PASS | MOTION 2: hanya transisi status singkat, tanpa koreografi |
| Satu fokus per layar | PASS | Tombol utama tetap fokus bar; placeholder tidak bersaing dengan judul |
| Ruang putih struktural | PASS | Placeholder memakai jarak `FarmSpace` yang sama dengan kartu nyata |
| Satu aksen | PASS | Sage tetap pada tombol utama mode gelap; tidak ada aksen baru |
| Motif identitas | PASS | Bentuk kartu `FarmShape.Card` dan isian `FarmShape.Tile` Canopy dipertahankan |
| Design Read sebelum kerja | PASS | Dial `DESIGN.md` dipakai sejak awal fase |

### Block 4: Craftsmanship dan Quality Locks

| Butir | Hasil | Bukti |
|---|---|---|
| C-1 niat | PASS | Setiap durasi dan status punya alasan di komentar kode dan di atas |
| C-2 fungsi | PASS | Lihat R-26 |
| C-3 komposisi dari isi | PASS | Tidak ada bagian baru selain status muat dan galat |
| C-4 ketahanan | PASS dengan batas | Terang, gelap, font 200% diuji; 360 dp, TalkBack dan keyboard fisik belum |
| C-5 bukti | PASS | Tidak ada klaim |
| R-05, R-11, R-15, R-16, R-20, R-21, R-29, R-30, R-31 | PASS | Tata letak, radius, label tombol spesifik (`Menyimpan…`, `Bukan saya`), palet dan tema tidak berubah dari Canopy |

## UI Skill Checklist `antislop-ui`

| Butir | Hasil |
|---|---|
| Palet dari `DESIGN.md` | Ya, hanya token yang ada |
| Aksen di momen kunci | Ya |
| Tanpa emoji dekoratif | Ya |
| Ritme sesuai dial | Ya, tidak berubah |
| Bebas bentuk AI default | Ya |
| Tanpa badge di atas judul | Ya |
| Kontrol punya perilaku nyata | Ya, `Bukan saya` dan tombol bar diuji |
| Gerak bertujuan, tanpa loop | Ya, indikator hanya selama proses |
| Glass/glow/bayangan/radius dalam batas | Ya |
| Titik status menandai state nyata | Tidak ada titik baru |
| Layar dibangun dari keputusan pengguna | Ya, form tetap terlihat saat simpan |
| Angka nyata | Ya, tidak ada angka baru |
| Isian kosong jujur | Ya, placeholder email `email@contoh.com` tetap |
| Status kosong/memuat/galat menyebut sebab dan aksi | Ya, galat menyebut "Perbarui untuk mencoba lagi" |
| Tahan di ukuran, tema, state | Sebagian: terang, gelap, 200% diuji; 360 dp belum |
| Metrik terukur | Ya, tidak ada metrik yang diklaim |

## Batas dan yang belum diverifikasi

- **Status sibuk di aplikasi nyata tidak bisa dipotret**: simpan lokal di emulator selesai sebelum 300 ms, jadi status hanya terlihat lewat tes komponen `FarmFormBar` dengan jam terkendali.
- **TalkBack nyata belum dijalankan**. Pengumuman live region hanya terbukti dari kode dan semantik.
- **Penahan sentuhan hanya menahan sentuhan**. Pengguna TalkBack atau keyboard masih bisa mengaktifkan isian lewat aksi aksesibilitas selama simpan; tombol bar sendiri nonaktif.
- **Autofill belum diuji dengan password manager berisi akun**. Emulator memakai layanan `com.google.android.gms/.autofill.service.AutofillService` tetapi tanpa akun Google (`dumpsys account`: 0 akun), sehingga isian nyata dan dialog simpan tidak bisa dibuktikan. Yang diuji hanya keberadaan petunjuk `ContentType` pada semantik.
- Email disimpan di SharedPreferences biasa (bukan vault terenkripsi) karena hanya petunjuk login; password tidak pernah disimpan.
- Lebar 360 dp, keyboard fisik, perangkat fisik dan backend nyata belum diuji.
- Beranda pengelola belum memakai placeholder.
- Transisi setelah simpan menunggu FE-P1-3 (fase F3).
