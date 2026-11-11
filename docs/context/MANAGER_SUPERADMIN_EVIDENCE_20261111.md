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
| Backend PHPUnit | 146 tes/3.824 assertion | **156 tes/3.960 assertion, 0 gagal** |
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

Tidak terbukti oleh tes: penjaga "Harus ada minimal satu pengelola aktif" (tidak bisa dipicu lewat API).

## Fase 2: teks dan dialog (sebagian)

**Selesai dan terkompilasi:** layar Kelola (`AdminScreen`, `AdminForm`, ViewModel, repository): dialog "Ajukan perubahan?" dihapus; konfirmasi satu kalimat dengan tombol kata kerja hanya untuk tutup musim, nonaktifkan, ubah peran dan reset password; catatan opsional. Lembar tanam saat ini, tombol simpan bersama ("Simpan"), pesan sesudah simpan ("Tersimpan. Terkirim otomatis saat ada internet."), Cek catatan (Sesuai / Perlu diperbaiki, satu ketukan), Riwayat, Beranda, rekap panen, label status sinkron. Alasan koreksi di enam kelas Draft sadar peran (`CorrectionReasonRoleTest`).

**Belum selesai:** label pengiriman di kartu pengukuran Beranda ("Belum tersinkron", "Diterima server") masih memakai kata lama; label cek sudah "Belum dicek"/"Sudah dicek". Tes instrumentasi lama dengan teks lama: `FinanceAdminUiTest.kt:68`, `FinanceEntryUiTest.kt` (Ajukan perubahan?, Antrean). sekitar **295 string unik** di kode Android masih memuat server, antrean, sinkron, tinjau, pengajuan, epoch, snapshot, cakupan atau penugasan. Terbesar: `SeasonReport*`, `Investment*`, `Attachment*`, `Recovery*`, `MeasurementDashboard`, pesan repositori keuangan (`SaleRepository`, `AssetRepository`, `FinancialEntryRepository`, `ReportExportRepository`), `FinanceSyncCoordinator`, `HomeContextCard` (pekerja). Definisi selesai "tidak ada kata server dll. di layar pengelola" **belum terpenuhi**.

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
- **Instrumentasi Android.** Tes lama yang memakai tab Antrean atau teks lama pengelola belum diperbarui dan belum dijalankan; kemungkinan gagal.
- Alur wizard dari UI ke API nyata (hanya urutan permintaan dan aturan kolom yang diuji).
- Apakah `GET /users` dan daftar greenhouse menanggapi parameter filter seperti yang diasumsikan layar Kelola terfilter.

## Lingkungan

- `JAVA_HOME` pengguna menunjuk ke `java.exe`; nilainya tidak diubah. Build memakai nilai yang benar hanya di dalam proses perintah.
- Layar "sebelum" dan "sesudah" tidak diambil: instalasi APK ke emulator macet karena memori.
- Backend uji (`--env=testing`) berjalan di port 8001 dan hanya memakai `inofarm_test`. Port 8000 milik pengguna tidak disentuh.
