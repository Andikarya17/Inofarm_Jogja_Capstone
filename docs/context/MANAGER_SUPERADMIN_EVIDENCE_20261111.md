# Bukti: pengelola sebagai super admin dan UI pengelola Versi B

Status 11 November 2026, **dalam pengerjaan**. Rencana: [master prompt](MASTER_PROMPT_MANAGER_SUPERADMIN.md). Baseline `abaed43`. Dokumen ini membedakan bukti baru dari bukti lama dan menyebut yang belum terverifikasi. Perubahan `CLAUDE.md`, `DECISIONS.md`, status dan gap menunggu Fase 6.

## Ringkasan per fase

| Fase | Branch | Status | Bukti |
|---|---|---|---|
| 0 Baseline dan pemetaan | `codex/worker-alignment-acceptance` | Selesai | Backend 146 tes/3.824 assertion, Android JVM 75 tes, lint 0 error/16 warning/1 hint |
| 1 Backend super admin | `codex/manager-superadmin-access` | Selesai dan commit | Backend 153 tes/3.941 assertion, guard 4/12, kontrak dan context exit 0 |
| 2 Teks dan dialog | `codex/manager-plain-copy` | **Sebagian** | Lihat bagian Fase 2 |
| 3–6 | belum dimulai | Belum | Navigasi 4 tab, detail greenhouse, wizard, polish dan dokumen |

## Perbedaan fakta master prompt dengan kode di HEAD

- `GreenhouseAccess` muncul **52 kali di 24 file** backend (grep), bukan sekitar 70.
- Kolom `reason` pada ledger audit **nullable** (`2026_10_29_000002`, baris 75), sehingga opsionalnya alasan tidak butuh migration.
- Greenhouse hanya punya field **`name`** (`MasterController.php:115`). Tidak ada lokasi. Langkah 1 wizard hanya memuat nama, kecuali kontrak diperluas.
- Outbox mengirim FIFO (`ORDER BY created_at, operation_id`, state `QUEUED`). Belum terbukti bahwa operasi anak ditahan bila induk ditolak. Keputusan wizard offline atau komposit dibuat di Fase 5.
- Aturan "pengelola tanpa penugasan harus menyiapkan greenhouse pertamanya sendiri" ada di `MasterService` dan dicabut di Fase 1.

## Fase 1: backend

Perilaku baru, dibuktikan oleh `ManagerSuperAdminAccessTest` (7 tes) dan 21 tes lama yang diperbarui:

- Manager tanpa penugasan melihat dan mengubah semua greenhouse, aktif atau nonaktif, dan mencatat kegiatan di dalamnya.
- Pekerja tetap hanya melihat penugasan efektif. Regresi: pekerja di greenhouse lain mendapat 404 pada kegiatan dan panen, serta 403 pada Beranda pengelola, penugasan, daftar akun dan pembuatan greenhouse.
- Replay operasi pekerja tetap memeriksa akses setelah penugasan dicabut.
- Scope feed `sync/changes` pengelola ikut greenhouse baru dari pengelola lain lewat scope key yang ada.
- `GET /users?all_accounts=1` mendaftar semua akun bagi pengelola. Edit akun tidak lagi terikat greenhouse tertentu.
- Pengelola tidak bisa menonaktifkan atau mengubah peran dirinya sendiri (422, pesan bahasa biasa).
- Alasan opsional untuk pengelola: master, acuan, akun, musim, selesai/buka panen, biaya, alokasi, penjualan, aset, skenario investasi, koreksi dan pembatalan kegiatan/panen. Pekerja memakai aturan lama. Alasan **tetap wajib** untuk keputusan "Perlu diperbaiki", recovery dan pembatalan lampiran.
- Payload kosong sah bila tidak ada kunci wajib, supaya pembatalan pengelola tanpa catatan bisa dikirim.

Keputusan penafsiran yang dicatat:

- Penugasan baru hanya untuk akun **pekerja** (`MasterService`). Penugasan pengelola lama tetap sah sebagai data.
- Pembatalan lampiran tetap mewajibkan alasan. Daftar 4.1 poin 5 tidak menyebutnya, dan master prompt meminta memilih sisi yang menjaga data bila ragu.
- Pesan pemulihan data memakai "Data dipulihkan dari cadangan…" supaya kata server tidak tampil, sedikit berbeda dari kalimat contoh di bagian 5.5.

Belum dibuktikan oleh tes: penjaga "Harus ada minimal satu pengelola aktif" (`UserController`). Lewat API penjaga ini tidak bisa dipicu karena pelaku selalu pengelola aktif yang berbeda dari target. Penjaga ini berfungsi sebagai pengaman, bukan perilaku yang teruji.

Kontrak: 18 skema payload di `openapi.json` dan 6 skema keuangan tidak lagi mewajibkan `reason`; `HarvestCompletionPublic.reason` boleh null; parameter `all_accounts` ditambahkan. Skema `activity` dan `harvest` tetap mewajibkan `reason` karena dipakai bersama pekerja.

## Fase 2: teks dan dialog (sebagian)

Selesai dan terkompilasi:

- Layar Kelola (`AdminScreen`, `AdminForm`, `AdminViewModel`, `AdminRepository`): dialog "Ajukan perubahan?" dihapus untuk simpan biasa. Konfirmasi satu kalimat dengan tombol kata kerja hanya untuk tutup musim, nonaktifkan, ubah peran dan reset password. Catatan menjadi "Catatan (opsional)". Subjudul penjelas dihapus. "Penugasan" menjadi "Pekerja greenhouse".
- Lembar tanam saat ini (`RunningPeriodSheet`, `ManagerHomeRepository`): catatan opsional, istilah "Tanam saat ini", tombol "Simpan".
- Tombol simpan bersama (`FinanceRoot`, keuangan, penjualan, panen, aset) menjadi "Simpan". Pesan sesudah simpan menjadi "Tersimpan. Terkirim otomatis saat ada internet." di lima ViewModel.

**Belum selesai** (jangan dianggap tuntas): sekitar 445 string di 76 file masih memuat kata server, antrean, sinkron, tinjau, pengajuan, epoch, snapshot atau penugasan. Terbesar: `HistoryScreen`, `QueueScreen`, `ManagerHomeScreen`, `ActivityVerificationScreen`, `SeasonReportScreen`, `InvestmentScreen`, `AttachmentScreen`, `RecoveryHost`, repositori laporan/investasi/lampiran, pesan `FinanceSyncCoordinator`, `FinanceNavigation` ("Antrean", ditangani Fase 3). Koreksi dengan alasan wajib di `ActivityDraft`, `HarvestDraft`, `FinancialDraft`, `SaleDraft`, `AssetDraft` dan `InvestmentScenarioDraft` belum mengenal peran, jadi form Android pengelola **masih meminta alasan koreksi** untuk catatan keuangan dan operasional walau server sudah tidak mewajibkannya.

## Lingkungan dan batas pemeriksaan

- `JAVA_HOME` pengguna menunjuk ke `java.exe`, bukan folder JDK, sehingga Gradle menolaknya. Nilai pengguna tidak diubah. Build memakai `JAVA_HOME` hanya di dalam proses perintah.
- Layar "sebelum" **belum diambil**. Emulator `Inofarm_Manager_Polish_API30` dinyalakan, tetapi memori bebas mesin sekitar 450 MB dari 16 GB sehingga instalasi APK macet. Percobaan menghentikan proses emulator ditolak oleh pembatas izin dan tidak diulang. APK "sebelum" (HEAD, URL uji port 8001) ada di folder scratchpad sesi.
- Backend uji berjalan di port 8001 dengan `--env=testing` (hanya `inofarm_test`). Port 8000 milik backend lokal pengguna tidak disentuh.
- Manifest fixture lama `tmp/manager-home-device-fixture.json` diganti nama menjadi `...stale-20261111.json` (database uji sudah direset PHPUnit). Fixture baru dibuat di `inofarm_test`.
- Tidak ada migration. Database `inofarm_finance_local` dan `inofarm_local` tidak disentuh.
