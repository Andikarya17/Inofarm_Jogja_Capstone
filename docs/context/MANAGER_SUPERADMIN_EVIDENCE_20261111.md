# Bukti: pengelola sebagai super admin dan UI pengelola Versi B

Status 11 November 2026. Rencana: [master prompt](MASTER_PROMPT_MANAGER_SUPERADMIN.md). Baseline `abaed43`. Dokumen ini membedakan bukti baru dari bukti lama dan menyebut yang **tidak** terverifikasi. Tidak ada migration; database `inofarm_finance_local` dan `inofarm_local` tidak disentuh.

## Ringkasan per fase

| Fase | Branch | Commit | Status |
|---|---|---|---|
| 0 Baseline | `codex/worker-alignment-acceptance` | `abaed43` | Selesai |
| 1 Backend super admin | `codex/manager-superadmin-access` | `4bf7caf` | Selesai |
| 2 Teks dan dialog | `codex/manager-plain-copy` | `5cbd11e`, `d41090d` | **Sebagian** (layar utama selesai, sapuan teks belum) |
| 3 Navigasi empat tab | `codex/manager-navigation-b` | `cb95f6b` | Diimplementasikan, tidak diverifikasi di perangkat |
| 4 Detail greenhouse | `codex/manager-greenhouse-hub` | `3c9fc79` | Diimplementasikan, tidak diverifikasi di perangkat |
| 5 Wizard | `codex/manager-greenhouse-wizard` | `d7dc1e1` | Diimplementasikan; urutan permintaan terbukti di backend |
| 6 Dokumen | `codex/manager-superadmin-docs` | `37582ec` | Dokumen diperbarui |

## Hasil pemeriksaan (output sesi ini)

| Pemeriksaan | Sebelum | Sesudah |
|---|---|---|
| Guard database uji | 4 tes/12 assertion | 4 tes/12 assertion |
| Backend PHPUnit | 146 tes/3.824 assertion | **157 tes/3.972 assertion, 0 gagal** |
| Contract checker | lulus | lulus (exit 0) |
| `check-finance-context.py` | lulus | lulus (`errors: []`) |
| Android JVM | 75 tes | **89 tes, 0 gagal** |
| Android lint | 0 error, 16 warning, 1 hint | 0 error, 16 warning, 1 hint |
| Debug APK | berhasil | berhasil |
| Instrumentasi Android | tidak dijalankan | **tidak dijalankan** |
| Screenshot emulator | tidak ada | **tidak ada** |

Tes backend hanya memakai `inofarm_test`.

## Perbedaan fakta master prompt dengan kode di HEAD

- `GreenhouseAccess` muncul 52 kali di 24 file backend, bukan sekitar 70.
- Kolom `reason` di ledger audit nullable, jadi alasan opsional tidak butuh migration.
- Greenhouse hanya punya `name` di API (tidak ada lokasi).
- **Seluruh tulis administrasi sudah online-only** (jurnal admin, bukan outbox offline). Ini menentukan keputusan wizard.
- Aturan "pengelola tanpa penugasan harus menyiapkan greenhouse pertamanya sendiri" ada di `MasterService` dan dicabut di Fase 1.

## Fase 1: backend (terbukti oleh tes)

`ManagerSuperAdminAccessTest` (7 tes) dan 21 tes lama yang aturannya diganti:

- Pengelola tanpa penugasan melihat dan mengubah semua greenhouse (aktif atau tidak) dan mencatat kegiatan di dalamnya.
- Pekerja tetap hanya melihat penugasan efektif; pekerja di greenhouse lain mendapat 404 pada kegiatan dan panen, 403 pada Beranda pengelola, penugasan, daftar akun dan pembuatan greenhouse. Replay operasi pekerja tetap memeriksa akses setelah penugasan dicabut.
- Scope feed `sync/changes` pengelola ikut greenhouse baru dari pengelola lain.
- `GET /users?all_accounts=1` mendaftar semua akun; edit akun tidak terikat greenhouse tertentu.
- Pengelola tidak bisa menonaktifkan atau menurunkan peran dirinya sendiri.
- Alasan opsional untuk pengelola pada master, acuan, akun, musim, selesai/buka panen, biaya, alokasi, penjualan, aset, skenario, koreksi dan pembatalan. Alasan **tetap wajib** untuk "Perlu diperbaiki", recovery dan pembatalan lampiran; pekerja memakai aturan lama.
- Payload kosong sah bila tidak ada kunci wajib.
- `ManagerGreenhouseSetupSequenceTest` (3 tes): urutan permintaan wizard (greenhouse, musim, varietas, kelompok, tanam saat ini, penugasan, akun baru) diterima server untuk pengelola tanpa penugasan dan tanpa catatan; langkah yang diulang dengan operasi yang sama tidak membuat catatan kedua; pekerja ditolak.

Kontrak: 18 skema payload di `openapi.json` dan 6 skema keuangan tidak lagi mewajibkan `reason`; `HarvestCompletionPublic.reason` boleh null; parameter `all_accounts`. Skema `activity` dan `harvest` tetap mewajibkan `reason` karena dipakai bersama pekerja.

Penjaga "Harus ada minimal satu pengelola aktif": `test_the_last_active_manager_cannot_be_removed_through_the_api` membuktikan pengelola terakhir tidak bisa dinonaktifkan atau diturunkan lewat API (jumlah pengelola aktif tetap 1). Penjaga eksplisit di `UserController` praktis tidak terjangkau lewat sesi normal karena pelaku selalu pengelola aktif lain; yang menahan adalah larangan mengubah akun sendiri.

Pesan galat kolom: label bahasa Indonesia ditambahkan untuk sekitar 90 kunci payload di `lang/id/validation.php` (tes backend lulus; tidak ada tes khusus per label).

## Fase 2: teks dan dialog

**Selesai dan terkompilasi:** layar Kelola (`AdminScreen`, `AdminForm`, ViewModel, repository): dialog "Ajukan perubahan?" dihapus; konfirmasi satu kalimat dengan tombol kata kerja hanya untuk tutup musim, nonaktifkan, ubah peran dan reset password; catatan opsional. Lembar tanam saat ini, tombol simpan bersama ("Simpan"), pesan sesudah simpan ("Tersimpan. Terkirim otomatis saat ada internet."), Cek catatan (Sesuai / Perlu diperbaiki, satu ketukan), Riwayat, Beranda, rekap panen, label status sinkron. Alasan koreksi di enam kelas Draft sadar peran (`CorrectionReasonRoleTest`).

**Sapuan lanjutan (11 November):** string yang tampil ke pengguna di `SeasonReport*`, `Investment*`, `Attachment*`, `Recovery*`, repositori keuangan, `ReportExport*`, `FinanceSyncCoordinator`, `LocalFinanceStore` (pesan yang sampai ke layar), `HomeContextCard`, `WorkerDayAgenda`, kartu pengukuran Beranda dan `FinanceApi` diganti mengikuti glosarium; kode HTTP tidak lagi tampil. Sisa kecocokan kata lama (sekitar 110 literal) adalah kunci kawat, nama variabel atau pengecualian internal, ditambah tab pekerja "Antrean". Teks komponen bersama ikut berubah di layar pekerja (hanya teks). Teks di tes instrumentasi (`FinanceEntryUiTest`, `FinanceAdminUiTest`, `WorkerContextUiTest`, `FinanceRecoveryTest`, `FinanceRecoveryUiBackendDeviceTest`) diselaraskan dan dikompilasi (`compileDebugAndroidTestKotlin` lulus), **tidak dijalankan**; dua tes alur pengelola yang bergantung pada dialog lama dipangkas sampai editor.

Layar masuk dan pesan galat bersama (`AuthViewModel.userMessage`, `FinanceLogin`) sudah diganti ke bahasa sehari-hari; pesan kolom galat tidak lagi memuat nama kunci wire. Pesan ini juga dipakai pekerja (hanya teks, bukan perilaku).

Deviasi dari glosarium: pesan pemulihan data memakai "Data dipulihkan dari cadangan…" (tanpa kata server) di layar pengelola, tetapi sheet belum terkirim masih memakai kalimat contoh "Data di server dipulihkan…".

## Fase 3 sampai 5: UI (terkompilasi, tidak dilihat)

- **Navigasi** (`FinanceNavigation.kt`, `ManagerTab`): Beranda, Riwayat, Keuangan, Kelola; label selalu tampil; label tab berhenti membesar setelah 1,3x. Tab Antrean pengelola dihapus. Navigasi pekerja tidak berubah (diuji `ManagerNavigationTest`).
- **Catat** (`ManagerSheets.kt`): kegiatan, pengukuran, panen, biaya, penjualan; biaya/penjualan tanpa greenhouse terpilih membuka pemilih greenhouse; pengukuran membawa jenisnya.
- **Indikator belum terkirim:** tombol "N belum terkirim" di top bar hanya saat ada; membuka sheet "Belum terkirim" dengan "Kirim sekarang". Judul layar yang sama bagi pekerja tetap "Pengiriman saya".
- **Cek catatan:** kartu di Beranda ("N catatan pekerja perlu dicek", "50+" bila penuh) membuka daftar; keputusan satu ketukan.
- **Detail greenhouse:** header (status, tanam saat ini, tombol Ganti/Pilih tanam saat ini), bagian Ringkasan, Kegiatan, Panen, Keuangan, Pengaturan. Pengaturan membuka layar Kelola yang sudah terfilter ke greenhouse itu.
- **Wizard** (`GreenhouseWizard.kt`): tiga langkah, "Langkah N dari 3", simpan per langkah, progres, kesalahan per kolom, operasi dipakai ulang saat coba lagi. Keputusan: pengiriman berurutan yang butuh koneksi (opsi b); alasan di [DECISIONS](../migration/finance-20261029/DECISIONS.md).
- Tidak ada warna baru; semua memakai peran warna yang ada, jadi `contrast-check.py` tidak dijalankan.

**Review `impeccable-finish-reviewer`** dijalankan setelah Fase 3, 4 dan 5 terhadap kode (tanpa render). Temuan material Fase 3 dan 4 diperbaiki (ruang FAB, label tab pada font besar, kontrol tanpa tujuan, hitungan kartu cek, tujuan Pengaturan, "Catat panen", filter admin yang bocor). Hal yang hanya bisa dinilai dari render tetap terbuka.

## Yang tidak terverifikasi

- **Tampilan di emulator atau perangkat**, termasuk screenshot sebelum/sesudah, font 200%, mode gelap, TalkBack, jarak FAB ke baris terakhir, apakah lima chip muat di 360 dp.
- **Instrumentasi Android.** Teks sudah diselaraskan dan dikompilasi, tetapi tidak dijalankan (butuh emulator); tes yang memakai tab Antrean pengelola masih bisa gagal.
- Alur wizard dari UI ke API nyata (hanya urutan permintaan dan aturan kolom yang diuji).
- Apakah `GET /users` dan daftar greenhouse menanggapi parameter filter seperti yang diasumsikan layar Kelola terfilter.

## Lingkungan

- `JAVA_HOME` pengguna menunjuk ke `java.exe`; nilainya tidak diubah. Build memakai nilai yang benar hanya di dalam proses perintah.
- Layar "sebelum" dan "sesudah" tidak diambil: instalasi APK ke emulator macet karena memori.
- Backend uji (`--env=testing`) berjalan di port 8001 dan hanya memakai `inofarm_test`. Port 8000 milik pengguna tidak disentuh.
