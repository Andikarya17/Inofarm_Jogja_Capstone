# Alur CRUD pengelola tanpa draf: bukti 17 November 2026

## Masalah dari pengujian pengguna

Pengguna bertanya kenapa menambah greenhouse "harus simpan draf" dan tidak bisa langsung CRUD. Audit kode Android menemukan:

1. Satu slot draf admin untuk kedua belas jenis data. Selama ada form yang belum selesai, "Tambah…" dan "Ubah" di semua daftar dinonaktifkan; yang tersisa hanya "Lanjutkan draf".
2. "Ubah" (termasuk "Ubah atau nonaktifkan" dari detail greenhouse) membuka draf lama apa pun, bukan data yang dipilih.
3. Jalan buntu: penolakan 409 selain `version_conflict` (misalnya `season_closed`, `entity_exists`) dan 403/404 meninggalkan form berstatus *pending* tanpa tombol hapus atau perbaiki, sehingga hanya "Kirim ulang" yang tersisa dan semua CRUD admin terkunci.
4. Kata "draf" menyesatkan: draf admin tidak masuk antrean "N belum terkirim" dan tidak pernah terkirim otomatis; tulis admin selalu butuh koneksi.
5. Dua tombol "Tambah greenhouse" dengan perilaku berbeda: wizard (Beranda, Kelola) dan form umum (daftar Greenhouse).
6. Menonaktifkan hanya bisa lewat Ubah → hapus centang Aktif → Simpan; daftar menampilkan "Versi N".

Backend tidak berubah: `POST`/`PATCH` master dan `POST greenhouses/{id}/running-period` sudah mendukung CRUD langsung. Hapus permanen tetap tidak ada sesuai aturan integritas; "hapus" berarti nonaktifkan.

## Perubahan

| Bagian | Sekarang |
|---|---|
| Form admin ([AdminScreen.kt](../../android/app/src/main/java/com/inofarm/jogja/feature/admin/AdminScreen.kt)) | Tombol "Batal" dan "Simpan …"; status draf di header disembunyikan. Batal langsung keluar bila belum ada perubahan, dan bertanya "Buang perubahan?" bila ada. Tombol "Hapus draf ini" dihapus. |
| Kunci satu slot ([AdminViewModel.kt](../../android/app/src/main/java/com/inofarm/jogja/feature/admin/AdminViewModel.kt)) | Tambah/Ubah tidak pernah dinonaktifkan karena form lain. Membuka data lain menampilkan "Buang isian sebelumnya?" dengan pilihan "Buang dan lanjut" atau "Buka isian sebelumnya". Hanya ubahan pada record yang sama yang dilanjutkan tanpa bertanya (`sameTarget`). |
| Penolakan server | `definitive()`: semua 4xx kecuali 401/408/429 dianggap final. Selain `version_conflict`, form langsung terbuka kembali untuk diperbaiki dan operasi lama dilupakan. `version_conflict` tetap menampilkan data terbaru dengan "Pakai data terbaru" atau "Tetap pakai isian saya". Galat jaringan/5xx tetap mempertahankan operasi untuk kirim ulang dengan UUID yang sama. |
| Pembatalan saat *pending* | Selalu bisa "Batalkan pengiriman"; pesan meminta memeriksa daftar karena perubahan mungkin sudah sampai ke server, lalu daftar dimuat ulang. |
| Tambah greenhouse | Tombol di daftar Greenhouse membuka wizard tiga langkah yang sama ([FinanceRoot.kt](../../android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt)). |
| Nonaktifkan / Aktifkan lagi | Tombol langsung di detail greenhouse/akun dan di baris data master (kecuali musim, yang memakai tutup/buka), dengan konfirmasi lalu satu kali kirim. |
| Daftar | Label "Versi N" diganti status: Aktif, Nonaktif, Musim berjalan, Musim ditutup. |

Catatan lokal terenkripsi tetap disimpan selama mengisi supaya isian tidak hilang bila aplikasi ditutup, dan supaya kirim ulang memakai `operation_id` yang sama. Password tetap tidak pernah ditulis ke catatan itu.

## Bukti

Dijalankan 17 November 2026 di laptop, JDK 25.0.4, pada branch `codex/manager-admin-crud` (berbasis `main`). Build, JVM dan lint diulang pada branch itu dengan hasil sama; tes emulator dijalankan sebelumnya pada working tree lama dengan kode admin yang identik.

| Pemeriksaan | Hasil |
|---|---|
| `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin` | lulus |
| `:app:testDebugUnitTest` | 121 tes, 0 gagal (dua tes baru di `AdminFormTest`: klasifikasi penolakan final dan aturan `sameTarget`) |
| `:app:lintDebug` | 0 error, 18 warning, 1 hint; tidak ada temuan di berkas admin |
| `FinanceAdminUiTest` (emulator `Inofarm_Botanical_API30`, fixture, bukan backend nyata) | 4/4 lulus: batal bertanya sebelum membuang (font 200%), form lama tidak memblokir Tambah, penolakan `season_closed` membuka form lalu simpan ulang berhasil, simpan langsung tanpa dialog |
| `FinanceAdminTest` (emulator yang sama) | 6/6 lulus |
| `FinanceEntryUiTest` dua tes walkthrough pengelola | gagal di Beranda sebelum mencapai admin ("Akhiri periode berjalan", "Catat hasil"), sama dengan baseline di [triase Q1](UI_TEST_TRIAGE_EVIDENCE_20261115.md); langkah admin di tes itu sudah diarahkan ke wizard tetapi belum tercapai |

## Batas

- Belum diuji terhadap backend Laravel/MySQL nyata, perangkat fisik, atau TalkBack.
- Penjaga server "pengelola tidak boleh menonaktifkan dirinya sendiri" tetap ditolak server (422) dan kini tampil sebagai galat yang bisa diperbaiki, tetapi belum diuji lewat API.
- Tes emulator belum diulang pada basis `main`.
