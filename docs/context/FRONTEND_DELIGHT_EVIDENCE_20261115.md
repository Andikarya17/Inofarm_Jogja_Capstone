# Bukti fase F4: umpan balik yang hidup (15 November 2026)

Branch `codex/frontend-delight`, bertumpuk di atas `codex/frontend-motion` (F3). Temuan dari [audit frontend](FRONTEND_MOTION_AUDIT_20261114.md): FE-P2-1 (haptik) dan FE-P2-11 (momen "terkirim"), serta haptik `Reject` di login. Backend, API, kontrak dan schema SQLite tidak berubah.

Label bukti: **diuji** (ada output perintah), **dibaca dari kode**, **belum diverifikasi**. Emulator `Inofarm_Botanical_API30` (Android 11), dijalankan dengan `-gpu swiftshader_indirect` karena GPU perangkat keras emulator membuat crash GL (lihat [bukti F3](FRONTEND_MOTION_EVIDENCE_20261115.md)).

## Perubahan

### FE-P2-1: haptik

- Berkas baru `core/design/FarmHaptics.kt`: `FarmHaptics` dengan tepat tiga aksi, `confirm()`, `reject()`, `segmentTick()`, lewat `LocalHapticFeedback` dan tipe Compose `HapticFeedbackType.Confirm`, `Reject`, `SegmentTick`. Satu parameter `enabled` mematikan semuanya. Setelan getar sistem tetap menentukan apakah getar terasa.
- Pemakaian, satu per momen bermakna:

| Momen | Haptik | Tempat |
|---|---|---|
| Login ditolak (pesan galat baru) | `Reject`, bersama getar visual ±6 dp yang sudah ada | `FinanceLogin.kt` |
| Form ditolak validasi (galat isian muncul saat Periksa) | `Reject` | `FinanceRoot.kt` (`formRejected`) |
| Tab dock berganti | `SegmentTick`, tidak pada komposisi pertama | `FinanceRoot.kt` |
| Antrean habis terkirim | `Confirm` | `rememberAllSent` |
| Keputusan "Sesuai" diterima server di Cek catatan | `Confirm`, hanya saat berubah menjadi diterima di depan pengelola | `ActivityVerificationScreen.kt` |

- Tidak ada haptik pada ketukan biasa, gulir, atau chip filter. Chip filter Riwayat tidak diberi `SegmentTick` supaya dua aksi berurutan (pilih chip lalu dock) tidak terasa ramai; dicatat sebagai pilihan sadar.

### FE-P2-11: momen "terkirim"

- Penanda `N belum terkirim` mengganti angka dengan crossfade 200 ms per nilai (`AnimatedContent`), tanpa hitungan berjalan.
- Ikon sinkron pada toolbar pekerja berputar (`farmSpinning`, satu putaran 1 detik) **hanya** selama `syncing` benar. Modifier dipasang dan dicabut bersama status sinkronisasi, jadi putarannya berhenti saat sinkronisasi selesai.
- `rememberAllSent(unsent, onAllSent)`: bila jumlah belum terkirim turun dari di atas nol ke nol di depan pengguna, chip sage bertanda centang "Semua terkirim" tampil 1,5 detik (`FarmMotion.AllSentHold`) lalu memudar 200 ms. Live region mengumumkan "Semua catatan terkirim". Jumlah yang sejak awal nol, atau hanya turun ke angka positif lain, tidak memunculkannya. Chip memakai tempat yang sama dengan tombol belum terkirim: satu baris pada pengelola dengan font normal, baris sendiri pada pekerja atau font di atas 1,3.

## Pemeriksaan

| Pemeriksaan | Hasil | Label |
|---|---|---|
| `testDebugUnitTest` | 116 tes, 0 gagal (termasuk `FinanceTransitionTest`) | diuji |
| `lintDebug` | 0 error, 18 warning (sama dengan F2/F3) | diuji |
| `AllSentMomentUiTest` (2), `DestinationMotionUiTest` (2), `InteractionHardeningUiTest` (3) | 7 dari 7 lulus | diuji |
| `FinanceEntryUiTest` penuh, emulator API 30 swiftshader | 65 tes, 19 gagal | diuji |

Dibanding run F2 (18 gagal): `attachmentReasonLogoutFlushes…` dan `workerCorrectionHasNoReferenceChoice…` kini lulus (keduanya flake yang sudah dikenal). Tiga yang baru gagal dibanding F2 juga gagal pada run F3 sebelum kode F4 ada, jadi bukan regresi F4:

- `assetHubRemainsScrollableWithFont200Percent`: termasuk baseline 17.
- `logoutFlushesIncompleteHarvestEditorSnapshotBeforeClearingCredentials`: `Detected multithreaded access to SnapshotStateObserver`, galat runtime Compose saat logout; gagal pada run F3b, F3c dan F3d.
- `offlineQueueRecreationReconnectAndManagerHistoryKeepHonestStatus`: tag `finance-queue` belum ada saat tes langsung mengklik setelah `finance-review`; gagal pada run F3b, F3c dan F3d. Kemungkinan terkait transisi tujuan F3; dibawa ke Q1.

## Delivery Gate antislop (mode `during`)

Block 2 (Purpose-Gate), alasan tertulis per animasi atau umpan balik:

| Unsur | Pemicu state | Tujuan |
|---|---|---|
| Crossfade angka belum terkirim | `unsent` berubah | Menunjukkan bahwa angka berubah tanpa animasi angka (K5) |
| Putaran ikon sinkron | `syncing == true` | Menunjukkan proses nyata sedang berjalan; berhenti bersama proses |
| Chip "Semua terkirim" 1,5 detik | `unsent` turun ke 0 | Momen kepercayaan aplikasi offline: catatan sudah di server |
| `Confirm` | diterima server | Menegaskan hasil yang tidak terlihat langsung |
| `Reject` | galat login atau validasi | Menyertai pesan galat yang tertulis |
| `SegmentTick` | tab dock berganti | Menandai perpindahan tab tanpa melihat layar |

| Blok | Hasil | Bukti |
|---|---|---|
| 1 Hard Gate | PASS | Teks baru "Semua terkirim" tanpa em dash; kontras Ink `#142B23` di atas Sage `#D4E4B7` 11,13:1; status tertulis, warna hanya penanda (K6); live region; tidak ada klaim atau angka baru |
| 2 Purpose-Gate | PASS | Tabel di atas; tidak ada animasi tanpa pemicu state |
| 3 Liveliness | PASS | MOTION 2; sage tetap aksen tunggal, kini juga di momen terkirim |
| 4 Craftsmanship | PASS | Label spesifik, palet dan bentuk dari `FarmShape.Control` dan token yang ada |

UI Skill Checklist `antislop-ui`: palet dari `DESIGN.md` (ya), aksen di momen kunci (ya), titik status menandai state nyata (ya, centang hanya saat antrean benar-benar habis), gerak tanpa loop di luar proses nyata (ya), metrik terukur (tidak ada yang diklaim).

## Batas dan yang belum diverifikasi

- **Getar tidak bisa dirasakan di emulator.** Haptik hanya dibuktikan dari kode; perangkat fisik diperlukan.
- Chip "Semua terkirim" pada aplikasi nyata belum dipotret karena alur kirim di emulator selesai dari fixture tanpa jeda yang bisa ditangkap; logikanya diuji oleh `AllSentMomentUiTest`.
- TalkBack nyata untuk pengumuman "Semua catatan terkirim" belum dijalankan.
