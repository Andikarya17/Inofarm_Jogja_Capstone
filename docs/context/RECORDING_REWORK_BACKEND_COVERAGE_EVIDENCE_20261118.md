# Rombak pencatatan terhadap backend nyata, lanjutan: bukti (18 November 2026)

Lanjutan dari [bukti uji backend nyata pertama](INVESTMENT_TEST_AND_REAL_BACKEND_EVIDENCE_20261118.md). Dikerjakan dari `github/main` `318b8e9` (PR #80–#82 sudah di-merge). Label bukti: **fixture HTTP sintetis** (MockWebServer di dalam tes), **backend Laravel nyata** (artisan serve + MySQL), **perangkat fisik**. Tidak ada uji di perangkat fisik dalam dokumen ini.

## Lingkungan

- Emulator hanya `Inofarm_Botanical_API30` (Android 11, 1080×2340), mode sistem terang (`cmd uimode night` = no). Flag: `-gpu swiftshader_indirect -no-snapshot-save -no-boot-anim`, satu sesi emulator untuk semua bagian. Emulator tidak crash dan tidak ANR selama sesi.
- MySQL proyek dinyalakan (hanya start, tanpa migrate/seed) di `127.0.0.1:3307`, dengan argumen yang sama seperti `scripts/start-backend-for-studio.ps1`.
- Backend nyata: `scripts/start-test-backend.ps1 -Port 8001` (`--env=testing`), database efektif **`inofarm_test`**, `adb reverse tcp:8001 tcp:8001`, base URL `http://127.0.0.1:8001/api/v2/`. Fixture dan pemeriksa SQL memakai `TestDatabaseGuard`. `inofarm_finance_local` dan `inofarm_local` tidak disentuh. PHPUnit tidak dijalankan di sesi ini.
- Sebelum dan sesudah tiap fixture, `inofarm_test` kosong: 0 baris `users`, `greenhouses`, `materials`, `activities`, `activity_reviews`, `harvests`, `harvest_items`, `harvest_reviews`, `entity_identities`, `audit_logs`, `change_feed`, `sync_operations`, `personal_access_tokens`.

## Branch

| Bagian | Branch | Dasar | Isi |
|---|---|---|---|
| A | `docs-migration-22-applied-merge-main` | `github/docs-migration-22-applied` (#79) | Merge `github/main` ke #79. Satu konflik di `CLAUDE.md`, diselesaikan dengan menyimpan kedua paragraf. Di-push atas persetujuan pengguna, di-merge lewat PR #79 |
| B | `recording-rework-backend-coverage` | `github/main` | Empat fase baru di `RecordingReworkBackendDeviceTest`, mode verify di fixture, dokumen ini |
| C | `device-test-entry-form` | `github/main` | `managerVerifiesWorkerRevisionsThroughGuardedBackend` pindah ke form gabungan |

## Bagian A: PR #79

`gh pr view 79`: `mergeable: CONFLICTING`. Merge percobaan `github/main` ke `docs-migration-22-applied`:

- `GAP_ANALYSIS.md` tergabung otomatis: bagian #82 ("uji backend nyata") di atas bagian #79 ("setelah rombak"), keduanya utuh.
- `CLAUDE.md` bentrok di paragraf pembuka: #82 menambah paragraf "Backend nyata …" di atas paragraf rombak, #79 mengubah paragraf rombak menjadi "di-merge … PR #72–#78" dan "migration 22 sudah diterapkan". Penyelesaian: paragraf #82 tetap di atas, paragraf rombak memakai versi #79. Tidak ada paragraf yang hilang. Selisih terhadap `github/main` hanya 6 baris yang memang diubah #79.
- `check-finance-context.py` di worktree bersih gagal karena `RECORDING_REWORK_DESIGN_20261117.md` menaut ke `HANDOFF_RECORDING_REWORK_20261117.md`, yang hanya ada untracked di laptop pengguna. Ini juga berlaku untuk `github/main`, bukan akibat merge. Di checkout pengguna (file ada) pemeriksa lulus.

Commit merge `62c67e4`, di-push ke `docs-migration-22-applied` atas persetujuan pengguna; PR #79 lalu di-merge ke main. PR #83 (B) dan #84 (C) di-merge sesudahnya, setelah ditinjau.

## Bagian B: alur yang belum tercakup, terhadap backend nyata

Jenis bukti: **backend Laravel nyata + MySQL `inofarm_test` + emulator**, fixture `finance-device-fixture.php create recording-rework` (GH A dua varietas, GH B aktif, GH C nonaktif, pekerja tanpa penugasan).

Tes dijalankan berurutan pada satu fixture. `pm clear` hanya sebelum fase pertama, karena fase berikutnya memakai antrean dan cache perangkat dari fase sebelumnya.

| Fase (`-e devicePhase`) | Metode | Yang dibuktikan | Mode verify |
|---|---|---|---|
| `recording-rework` | `workerRecordsOneEntryPerGreenhouseAndManagerChecksItAsOneEntry` (diperluas) | Seperti sebelumnya, ditambah **Cek catatan panen**: dua panen dari satu isian dua varietas muncul sebagai dua kartu; pengelola memberi "Sesuai" untuk satu dan "Perlu diperbaiki" dengan alasan untuk yang lain | `verify-recording-rework`: dua `harvest_reviews` (`invalid` dengan alasan, `valid`), versi panen 1, satu baris audit/receipt/feed per keputusan |
| `rework-correction` | `workerAndManagerCorrectOneSectionOfTheEntry` | Pekerja mengoreksi bagian pengukuran (PPM 900 → 950). Tanpa alasan, form menampilkan "Isi alasan koreksi." dan tidak membuka Periksa; API tanpa alasan dijawab **422**. Dengan alasan, versi 2 diterima dan status cek kembali "pending". Pengelola mengoreksi bagian stadia tanpa alasan (Simpan → Ringkasan → Simpan) | `verify-rework-correction`: pengukuran v2 PPM 950, `planting_group_id` null, audit `update` oleh pekerja dengan alasan; stadia v2 "Panen", audit oleh pengelola, alasan null; tiga bagian lain tetap v1 dengan `entry_batch_id` sama; dua baris audit/receipt/feed per bagian yang dikoreksi; belum ada keputusan untuk revisi 2 |
| `rework-manager-home` | `managerEntryAfterDesignatingTheRunningPeriodCarriesItsVersion` | Pengelola menetapkan periode berjalan GH A dari Beranda (detail GH → riwayat periode), lalu Catat → GH A → isian EC/PPM/stadia. Kedua operasi membawa `running_period_version` = versi GH setelah penetapan, `planting_group_id` null, dan diterima server (jalur yang diperbaiki #81) | `verify-rework-manager-home`: `running_group_id` = kelompok A, audit `designate_period`, dua baris tingkat GH `recorded_by_role` manager, satu baris ledger per entitas |
| `rework-offline-queue` | `workerQueuesAnEntryWhileTheBackendIsDown` | Pekerja login; host mematikan backend begitu `files/evidence/rework-offline-ready.txt` muncul; tes menunggu API benar-benar tak terjangkau (`IOException`), lalu menyimpan isian. Dua operasi tetap di antrean, tidak ada yang `ACCEPTED` | |
| `rework-offline-replay` | `queuedEntryReplaysOnceAfterTheBackendReturns` | Proses baru (PID berbeda) setelah backend dinyalakan lagi. Kedua operasi terkirim otomatis dengan `operation_id` dan hash yang sama. Kirim ulang envelope yang sama secara manual mengembalikan receipt yang sama (versi 1), audit tetap satu | `verify-rework-offline`: dua baris v1, masing-masing tepat **1** `audit_logs`, **1** `sync_operations`, **1** `change_feed` |

### Hasil

| Putaran | Hasil |
|---|---|
| Uji coba 1 | Fase 1 gagal sebelum perubahan tes: kartu inbox hilang di antara scroll dan klik karena daftar dimuat ulang setelah sinkron. Klik kini diulang sampai detail terbuka (`openFromList`). Asersi tidak diubah |
| Uji coba 2 | Fase 1–3 dan antrean offline lulus. Fase `rework-manager-home` sebelumnya gagal karena tes membaca `running_group_id` dari `GET greenhouses/{id}`, yang tidak memuat kolom itu; tes kini membaca `manager-home`. Backend tidak menyala lagi setelah dimatikan (path skrip host tidak dikutip); antrean bertahan dua kali percobaan kirim yang gagal, lalu terkirim sekali setelah backend menyala. `verify-rework-offline` PASS |
| **Putaran bersih** | **5/5 fase lulus, 4/4 mode verify PASS** (48 s, 23 s, 19 s, 26 s, 7 s). [Log SQL](evidence/recording-rework-backend-coverage-20261118/sql-verify.txt) |

Tangkapan layar putaran bersih (emulator, backend nyata, data sintetis), di [folder bukti](evidence/recording-rework-backend-coverage-20261118/):

- Cek catatan panen: [dua kartu panen](evidence/recording-rework-backend-coverage-20261118/rework-manager-harvest-inbox.png), [keputusan](evidence/recording-rework-backend-coverage-20261118/rework-manager-harvest-decision.png)
- Koreksi: [form pekerja saat alasan ditolak](evidence/recording-rework-backend-coverage-20261118/rework-correction-worker-reason-required.png), [terkirim](evidence/recording-rework-backend-coverage-20261118/rework-correction-worker-sent.png), [ringkasan pengelola](evidence/recording-rework-backend-coverage-20261118/rework-correction-manager-summary.png)
- Beranda: [penetapan periode](evidence/recording-rework-backend-coverage-20261118/rework-manager-period.png), [ringkasan isian pengelola](evidence/recording-rework-backend-coverage-20261118/rework-manager-entry-summary.png)
- Offline: [antrean saat backend mati](evidence/recording-rework-backend-coverage-20261118/rework-offline-queued.png), [setelah terkirim](evidence/recording-rework-backend-coverage-20261118/rework-offline-replayed.png)
- Bukti JSON dari perangkat: `rework-*-proof.json` di folder yang sama

## Bagian C: tes perangkat lama

`FinanceBackendDeviceTest#managerVerifiesWorkerRevisionsThroughGuardedBackend` **dipindahkan, tidak dipensiunkan**. Tes baru belum menutup cakupannya: satu kegiatan melewati "Perlu diperbaiki" → koreksi → "Sesuai" di dua revisi, pekerja membaca alasan pengelola di detail, kirim ulang keputusan mengembalikan receipt yang sama, dan PPM koreksi tampil di kartu Beranda.

Perubahan: pekerja mencatat lewat Catat (greenhouse saja) dan form gabungan (`entry-ec`, `entry-ppm`, HST wajib), sehingga pengukuran menjadi catatan tingkat GH. Koreksi lewat API kini membuang `running_period_version`, seperti yang dilakukan aplikasi (`ActivityDraft` hanya mengirimnya saat create); tanpa itu server menolak dengan "operation.payload harus berupa daftar/objek dengan kolom yang diizinkan" begitu GH sudah punya periode berjalan dari putaran sebelumnya.

Jenis bukti: backend Laravel nyata, fixture `create manager-home`.

| Putaran | Hasil |
|---|---|
| 1 | lulus (32 s) |
| 2–6, sebelum perbaikan payload | 2, 5 dan 6 gagal di koreksi API (penyebab di atas). 4 gagal karena langkah Periksa tidak terbuka dalam 20 detik; penyebabnya tidak diselidiki. Output putaran 3 tidak tersimpan |
| 7–9, sesudah perbaikan | 2 lulus, 1 gagal karena `Detected multithreaded access to SnapshotStateObserver` saat Compose menggambar; tidak ada frame dari kode tes atau aplikasi di stack. Pola yang sama tercatat di [triase Q1](UI_TEST_TRIAGE_EVIDENCE_20261115.md) |

SQL setelah putaran 1 dan 2 ([output](evidence/recording-rework-backend-coverage-20261118/activity-verification-sql.txt)): pengukuran `planting_group_id` null, HST 30; putaran 1 berakhir di v2 PPM 630 dengan keputusan `invalid v1`, `valid v2`, dua audit dan dua receipt. Fixture `manager-home` dibersihkan; `inofarm_test` kembali kosong.

Catatan: manifest `tmp/manager-home-device-fixture.json` dari sesi sebelumnya merujuk akun yang sudah tidak ada. File itu dipindah ke `tmp/manager-home-device-fixture.stale-20261118.json` (folder diabaikan Git), tidak dihapus.

## Bagian D: `FinanceEntryUiTest` penuh

Jenis bukti: **fixture HTTP sintetis** (MockWebServer), bukan backend nyata. APK dari `device-test-entry-form` (source aplikasi sama dengan `github/main` `318b8e9`), emulator swiftshader yang sama, satu kali `am instrument -e class com.inofarm.jogja.FinanceEntryUiTest`.

| Ukuran | Hasil |
|---|---|
| Durasi | 13,4 menit, satu putaran tanpa ANR atau emulator macet (F5: berhenti di tes ke-55) |
| Dijalankan / diabaikan | 67 / 3 (tiga `@Ignore` dari K4) |
| Gagal | **15**, semuanya dari 16 baseline lama |
| Gagal baru | **0** |
| `investmentWorkspaceRecreationAndLogoutKeepLatestIncompleteRawInput` | **lulus di dalam rangkaian** |

Lima belas yang gagal: `assetConflict…`, `attachmentReview…`, `conflictKeepLocal…`, `conflictUseServer…`, `harvestBalance…`, `harvestOwnCorrection…`, `longContext…`, `managerHomeFallsBack…`, `managerHomeOneTap…`, `managerRecordsObservations…`, `managerSeasonClose…`, `managerWalkthrough…`, `queueReview…`, `secondaryMenus…`, `twoFieldLogin…`. Satu baseline lulus kali ini: `attachmentReasonFailedFlush…`, yang di [triase Q1](UI_TEST_TRIAGE_EVIDENCE_20261115.md) memang dicatat sebagai flake. Penyebab tiap kegagalan ada di triase itu; tidak ada tes baseline yang diubah. Log lengkap tetap di `tmp/entry-ui-full-20261118.log` (diabaikan Git).

Tes investasi lulus di rangkaian penuh, tetapi kegagalan aslinya tetap belum pernah muncul lagi sejak tesnya diperkuat, jadi ini bukti tidak ada regresi, bukan bukti akar masalahnya hilang.

## Bagian E: temuan kecil (usulan, belum dikerjakan)

1. **"Memperbarui" menempel ke judul** di halaman bertingkat seperti Cek catatan pekerja (`FinanceRoot.kt:1215`). Usul: beri jarak awal `FarmSpace.Small` pada label, atau ganti label dengan ikon berputar seperti di toolbar pekerja. Android saja.
2. **Cek catatan panen belum dikelompokkan per isian.** Panen tidak punya `entry_batch_id`; dua panen dari satu isian dua varietas tampil sebagai dua kartu ([tangkapan](evidence/recording-rework-backend-coverage-20261118/rework-manager-harvest-inbox.png)). Usul A (Android saja): kelompokkan kartu panen per pencatat + greenhouse + `occurred_at` yang sama, dengan satu keputusan untuk keduanya. Usul B: kolom `harvests.entry_batch_id` (migration 23, backend dan kontrak). A cukup untuk kebutuhan sekarang.
3. **Permintaan tanpa token dan tanpa `Accept: application/json` menjawab 500** (`Route [login] not defined`). Middleware auth mencoba redirect ke route `login` sebelum handler JSON di `bootstrap/app.php` berjalan. Usul: `$middleware->redirectGuestsTo(fn () => null)` untuk `api/*`, plus tes PHPUnit yang memanggil `GET /api/v2/me` tanpa header dan mengharapkan 401. Backend saja.
4. **Baru: EC di form koreksi tampil mentah** sebagai `1.800000` (titik, enam desimal, terpotong di field), bukan `1,8` ([tangkapan](evidence/recording-rework-backend-coverage-20261118/rework-correction-worker-reason-required.png)). `ActivityDraft.from` memakai string server apa adanya. Usul: tampilkan dengan koma dan tanpa nol di belakang saat form koreksi dibuka. Android saja.

## Batas

1. Tidak ada uji di ponsel fisik, TalkBack, font 200% atau lebar 360 dp.
2. Pembacaan Beranda aplikasi terhadap `inofarm_finance_local` setelah #81 dan uji restore backup 18 November belum dilakukan (butuh izin terpisah).
3. Fase offline mematikan backend dari host dengan menghentikan proses `php artisan serve`; ini tidak meniru jaringan seluler yang putus-sambung.
4. Koreksi bagian semprot (per varietas) dan nutrisi tidak diuji; yang diuji pengukuran (pekerja) dan stadia (pengelola).
5. Koreksi panen dua varietas oleh pekerja setelah "Perlu diperbaiki" tidak diuji.
