# Tes investasi dan uji backend nyata rombak pencatatan: bukti (18 November 2026)

Dua bagian dari permintaan pengguna 18 November 2026, dikerjakan dari `github/main` `3b80002` (setelah PR #78). Label: **fixture HTTP sintetis** (MockWebServer di dalam tes), **backend Laravel nyata** (artisan serve + MySQL), **perangkat fisik**. Tidak ada uji di perangkat fisik dalam dokumen ini.

## Lingkungan

- Emulator hanya `Inofarm_Botanical_API30` (Android 11, 1080×2340, 440 dpi), mode sistem terang (`cmd uimode night` = no).
- Flag emulator per tahap:
  - Bagian A, sebelum perbaikan: `-gpu swiftshader_indirect -no-snapshot-save` (warm boot), lalu `-gpu swiftshader_indirect -no-snapshot-load -no-snapshot-save` (cold boot), lalu tanpa `-gpu` (GPU perangkat keras NVIDIA RTX 3050, cold boot).
  - Bagian A, sesudah perbaikan: GPU perangkat keras, sesi emulator yang sama.
  - Bagian B: `-gpu swiftshader_indirect -no-snapshot-save -no-boot-anim`.
- Selama sesi ini emulator tidak crash dan tidak ANR, juga dengan GPU perangkat keras.
- Backend nyata: `scripts/start-test-backend.ps1 -Port 8001` (`--env=testing`). Database efektif **`inofarm_test`** (MySQL 8, port 3307, migration 22, 27 file migration), lewat `adb reverse tcp:8001 tcp:8001` dan base URL `http://127.0.0.1:8001/api/v2/`. Fixture dan pemeriksa memakai `TestDatabaseGuard`. `inofarm_finance_local` dan `inofarm_local` tidak disentuh.
- PHPUnit dijalankan setelah fixture dibersihkan dan backend perangkat tidak sedang dipakai, jadi tidak ada dua penulis di `inofarm_test` bersamaan.

## Bagian A: `investmentWorkspaceRecreationAndLogoutKeepLatestIncompleteRawInput`

Branch `investment-test-horizon-fix`, commit `037fd6b`. Hanya tes yang berubah; source aplikasi tidak berubah. Jenis bukti: fixture HTTP sintetis.

### Hasil

| Kondisi | Sebelum perbaikan | Sesudah perbaikan |
|---|---|---|
| swiftshader, warm boot | 3/3 lulus | |
| swiftshader, cold boot | 3/3 lulus | |
| swiftshader, cold boot, preferensi tema `dark` tersimpan | 1/1 lulus | |
| GPU perangkat keras, cold boot | 3/3 lulus | 3/3 lulus (16,9–17,2 detik) |
| GPU perangkat keras, 6 proses `yes` membebani CPU emulator | 3/3 lulus | |

Kegagalan dari laporan F5/F6 **tidak bisa direproduksi** dalam 12 percobaan sebelum perbaikan.

### Penyebab: kesimpulan dari kode, bukan dari kegagalan yang teramati

- Pesan *"the unmerged tree contains '1' node"* tidak berarti field-nya tergabung ke induk. Di compose-ui-test 1.9.0 (`SemanticsNodeInteraction.fetchOneOrThrow`), pencarian pertama di pohon gabungan gagal, lalu pesan itu membuat pencarian **kedua** di pohon tak tergabung, sesaat kemudian. Kedua pencarian melewati node nonaktif (`skipDeactivatedNodes = true`). Jadi `investment-horizon` sempat tidak ada di pohon, lalu muncul lagi. Ini race, bukan masalah merge semantik.
- `investment-name` memakai `FinanceField` yang sama dan selalu ditemukan. Tidak ada `mergeDescendants` di atas field ini.
- Hal di luar idling Compose yang bisa menata ulang `LazyColumn` setelah scroll: perubahan inset IME (header ringkas "Investasi" menggantikan toolbar saat keyboard tampil, `FinanceRoot.kt`), dan autosave `delay(350)`.
- Dugaan F0 tema dan `logoutFromAccount` (`performScrollTo`) tidak didukung bukti. Tes lulus dengan preferensi tema `dark` dan `light`, dan logout berada di luar langkah yang gagal.

### Keputusan: yang diperbaiki tes, bukan aplikasi

Pengguna tidak terdampak: field tetap ada dan data tetap tersimpan. Tes kini memakai `fill(tag, value)`, yang mengulang scroll dan `performTextReplacement` sampai berhasil (maksimal 10 detik), untuk `investment-name`, `investment-horizon` dan `investment-initial`. Mengetik ulang aman karena teksnya diganti, bukan ditambah. Asersi data tidak diubah.

**Batas:** perbaikan ini belum dibuktikan menghapus kegagalan aslinya, karena kegagalan itu tidak muncul lagi. `FinanceEntryUiTest` penuh tidak dijalankan ulang di sesi ini.

## Bagian B: rombak pencatatan terhadap backend Laravel nyata

Jenis bukti: **backend Laravel nyata + MySQL `inofarm_test` + emulator**. Bukan ponsel fisik, bukan server pilot.

Branch:

| Branch | Dasar | Isi |
|---|---|---|
| `review-greenhouse-level-rows` | `github/main` | Perbaikan backend (`ba216df`) |
| `recording-rework-real-backend` | `review-greenhouse-level-rows` | Fixture, tes perangkat, dokumen ini |

### Temuan: Cek catatan menjawab 500 untuk catatan tingkat greenhouse

Putaran pertama di perangkat: pekerja berhasil mengirim, tetapi inbox pengelola kosong. `GET activity-reviews?status=pending` menjawab 500. Log Laravel: `Attempt to read property "name" on null`, di `ActivityReviewService.php:154`. Sejak migration 22, pengukuran, nutrisi, stadia dan pekerjaan umum disimpan dengan `planting_group_id` null, tetapi `entry()` masih membaca `$ctx['group']->name`. Akibatnya, **satu saja catatan pekerja tingkat GH yang belum dicek membuat seluruh inbox Cek catatan pengelola gagal dimuat**, begitu pula `activities/{id}/verification`. Fixture HTTP sintetis F4 tidak menjalankan kode server ini, jadi bug ini lolos.

Pola yang sama ada di `RunningPeriod::assertHomeParent` (`$context['group']->id`), yang dipanggil untuk catatan berisi `running_period_version`.

Perbaikan (bukan fitur keuangan):

- `context.group` dan `context.variety` bernilai null untuk catatan tingkat GH. OpenAPI `ActivityReviewEntry` dan `ActivityVerificationDetail` diperbarui (`group` boleh null). Android sudah membaca field ini dengan aman (`text("group")`).
- `assertHomeParent` hanya membandingkan versi greenhouse bila catatan tidak punya kelompok.
- Tes baru `test_manager_reviews_greenhouse_level_rows_and_running_period_drafts_accept_them` **gagal sebelum perbaikan** (500, pesan yang sama) dan lulus sesudahnya. Tes itu juga membuktikan bahwa versi periode yang usang tetap ditolak dengan `running_period_changed`.

### Fixture `recording-rework`

`scripts/finance-device-fixture.php create recording-rework` membuat data berikut, semuanya lewat `MasterService`:

- pengelola, dan pekerja **tanpa penugasan**;
- GH A dengan dua varietas dan dua kelompok yang disemai 1 Oktober 2026 dalam satu musim terbuka;
- GH B aktif;
- GH C yang lalu dinonaktifkan lewat update ledger;
- grade, satuan gram, bahan nutrisi dan kategori biaya.

Karena GH dibuat lewat `MasterService`, `SprayMaterialCatalog::ensure` menambah 10 bahan semprot awal per GH, sama seperti GH yang dibuat dari aplikasi.

Mode baru `verify-recording-rework` memeriksa SQL. Cleanup fixture kini juga menghapus:

- `activity_reviews` (gap Q2 yang membuat cleanup gagal);
- kegiatan tingkat GH, dicari lewat `season_id`;
- bahan dan satuan GH fixture;
- bahan semprot ketikan di GH mana pun. Namanya memuat 8 karakter awal UUID fixture, jadi baris lain tidak tersentuh.

Cleanup sempat gagal karena varietas kedua. Transaksi di-rollback utuh, lalu cleanup diperbaiki.

### Tes perangkat `RecordingReworkBackendDeviceTest`

Kelas baru, dijalankan dengan `-e devicePhase recording-rework -e recordingFixture <manifest base64>`, setelah `pm clear com.inofarm.jogja.finance`. Isi tes:

1. Pekerja login. API `greenhouses` memuat GH A dan GH B, tidak memuat GH C. Sheet Catat hanya menampilkan pilihan greenhouse (tidak ada pilihan musim atau kelompok) dan tidak menampilkan GH C.
2. Form "Catat kegiatan" GH A berisi:
   - HST 30;
   - EC 1,8 dan PPM 900, sesudah inject;
   - nutrisi AB mix 250 gram;
   - penyemprotan pagi untuk dua varietas, dengan Antracol dari daftar dan satu bahan baru diketik;
   - stadia dan catatan.

   Setelah Periksa lalu Kirim, lima operasi `ACCEPTED` dengan satu `entry_batch_id`.
3. Panen dua varietas berdampingan, 12,5 kg dan 7 kg, jumlah buah dikosongkan. Dua operasi `ACCEPTED` dengan `fruit_count` null.
4. Pekerja tidak punya tab Keuangan, dan `GET expenses` dijawab **403**.
5. Pengelola membuka Cek catatan dan melihat **satu kartu** untuk isian itu ("Satu isian: nutrisi, penyemprotan, pengukuran manual, penyemprotan, stadia"). Satu keputusan "Sesuai" untuk "seluruh isian (5 bagian)" menghasilkan lima `activity_review` `ACCEPTED`.

| Putaran | Perangkat | `verify-recording-rework` |
|---|---|---|
| 1 (sebelum perbaikan backend) | gagal di langkah 5 (inbox 500) | tidak dijalankan |
| 2 | lulus, 40,0 detik | PASS |
| 3 | lulus, 40,0 detik | PASS |
| 4 | lulus, 40,0 detik | PASS |

Putaran 2–4 berjalan sesudah perbaikan backend, masing-masing dengan fixture baru dan cleanup sesudahnya. Di antara putaran 2 dan 3, satu hal di tes diubah: tempat menyimpan tangkapan layar, dari folder eksternal (ditolak `Permission denied` untuk shell di API 30) ke `filesDir/evidence`. Asersinya tetap sama.

### Yang diperiksa di `inofarm_test` (putaran 4, [output lengkap](evidence/recording-rework-real-backend-20261118/sql-verify.txt))

- Satu isian menjadi **lima baris**: `measurement`, `nutrition`, `spray`, `spray` dan `stage`. Kelimanya memakai **satu `entry_batch_id`**, `age_basis` hst, `age_day` 30 dan `recorded_by_role` worker.
- `planting_group_id` **null** untuk pengukuran, nutrisi dan stadia. Dua baris semprot masing-masing menunjuk satu kelompok varietas.
- EC `1.800000`, PPM `900.000000`. Nutrisi: satu bahan, `250.000000`.
- Tiap baris semprot punya dua bahan: Antracol dari daftar, dan bahan ketikan di GH A, `spray_period` morning.
- Bahan ketikan ada di **semua GH aktif** (2 dari 2) dan **tidak ada di GH C** yang nonaktif.
- Dua panen, satu per kelompok, dengan waktu sama. Kedua rincian `fruit_count` **NULL**.
- Lima `activity_reviews` `valid` oleh pengelola. Pekerja tanpa baris `greenhouse_assignments`.

Setelah cleanup, `inofarm_test` kembali kosong: 0 baris `users`, `greenhouses`, `materials`, `activities`, `activity_reviews`, `harvests`, `harvest_items`, `entity_identities`, `audit_logs`, `change_feed`, `sync_operations`, `personal_access_tokens`.

Tangkapan layar putaran 4 (emulator, backend nyata, data sintetis):

- [Catat pekerja](evidence/recording-rework-real-backend-20261118/rework-worker-catat.png)
- [Form terisi](evidence/recording-rework-real-backend-20261118/rework-worker-entry-filled.png)
- [Periksa](evidence/recording-rework-real-backend-20261118/rework-worker-entry-review.png)
- [Panen dua varietas](evidence/recording-rework-real-backend-20261118/rework-worker-harvest-two.png)
- [Inbox pengelola satu kartu](evidence/recording-rework-real-backend-20261118/rework-manager-inbox.png)
- [Keputusan seluruh isian](evidence/recording-rework-real-backend-20261118/rework-manager-decision.png)
- [Bukti JSON dari perangkat](evidence/recording-rework-real-backend-20261118/device-proof.json)

## Pemeriksaan

| Pemeriksaan | Branch | Hasil |
|---|---|---|
| `./scripts/test-backend.ps1` | `review-greenhouse-level-rows` | guard 4/12, backend **174 tes / 4.751 assertion** lulus (173 + 1 baru) |
| `check-finance-contracts.py` | `review-greenhouse-level-rows` | lulus |
| `:app:assembleDebug`, `:app:assembleDebugAndroidTest` | keduanya | lulus |
| `check-finance-context.py` | `recording-rework-real-backend` | lihat PR |

JVM dan lint tidak dijalankan ulang: source aplikasi Android tidak berubah, hanya `androidTest`.

## Batas dan temuan terbuka

1. Panen dua varietas dicek per catatan panen. Tes ini tidak menjalankan Cek catatan panen.
2. Koreksi per bagian dari form gabungan, alur offline/antrean, dan form pengelola terhadap backend nyata belum diuji.
3. Tes perangkat lama `FinanceBackendDeviceTest#managerVerifiesWorkerRevisionsThroughGuardedBackend` masih memakai form pengukuran sebelum rombak (`finance-ec`) dan tidak dijalankan. Tes baru menggantikan cakupannya untuk alur rombak.
4. UI: di judul inbox "Cek catatan pekerja", label "Memperbarui" menempel ke judul ([tangkapan](evidence/recording-rework-real-backend-20261118/rework-manager-inbox.png)). Dicatat, tidak diubah.
5. Ponsel fisik, TalkBack, font 200% dan 360 dp belum dicek.
6. Bug inbox 500 juga berlaku untuk `inofarm_finance_local` sampai perbaikan backend di-merge, begitu ada catatan pekerja tingkat GH yang belum dicek.
7. `backend/CLAUDE.md` (bawaan skeleton) meminta memasang Laravel Boost; `backend/AGENTS.md` melarangnya, jadi Boost tidak dipasang.
