# Serah terima: desain ulang tab Kelola pengelola (21 November 2026)

Prompt untuk sesi baru (akun Claude lain, laptop yang sama). Salin bagian "Prompt" ke sesi baru.

## Prompt

Lanjutkan desain ulang tab **Kelola** pengelola di aplikasi Android Inofarm. Baca dulu `CLAUDE.md`, `AGENTS.md`, `DESIGN.md`, lalu file ini sampai habis. Pakai skill `impeccable` dan `antislop` (mode during) untuk UI; jangan pakai `ponytail` untuk UI. Jangan commit atau push tanpa izin pengguna. Database `inofarm_finance_local` milik pengguna: jangan reset/migrate:fresh/seed; PHPUnit hanya ke `inofarm_test`. Hanya emulator `Inofarm_Botanical_API30`, mode terang.

Mockup yang disetujui: kanvas https://claude.ai/artifact/SYtXYKV7S49zQtrmiAsyM5 (artboard `Main`, `Greenhouse`, `Bahan`, `UbahBahan`, `Orang`, `Akun`, `Nonaktifkan`; catatan di `project/canvas.json`). Kelola diatur per objek: Greenhouse, Orang, Akun saya. Gaya Canopy (forest #132820, krem #F4F3E8, surface #FFFDF4, brand #2B503D, sage #D4E4B7, Plus Jakarta Sans, dock #263F34).

**Keputusan pengguna (21 Nov 2026):** sheet ubah data master **tetap memakai langkah Ringkasan** (Batal / Simpan → Ringkasan dengan Ubah / Simpan). Jangan diubah menjadi langsung simpan.

### Posisi kode

Branch `perbaikan-bug-audit-pengelola`, **semua belum di-commit**. Branch ini juga memuat perbaikan bug B1–B6 dan UI U1/U3/U4/U5 dari audit (`docs/context/MANAGER_FULL_AUDIT_20261121.md`) yang dibuat sesi sebelumnya; jangan diulang atau dibuang. Periksa `git status` dulu.

File yang diubah/ditambah oleh pekerjaan Kelola:

| File | Isi |
|---|---|
| `android/app/src/main/java/com/inofarm/jogja/feature/admin/KelolaData.kt` (baru) | `KelolaOverview`/`KelolaGreenhouse`, `buildKelolaOverview`, `GreenhouseSettings`, label baris/daftar (`masterRowTitle`, `masterRowDetail`, `masterListTitle`, `masterAddLabel`, dll.) |
| `feature/admin/Kelola.kt` (baru) | `KelolaHome` (tab), `GreenhouseSettingsPage`, `MasterListPage`, `PeoplePage`, `AccountPage`, `DeactivatePage`, `AdminSheetEditor` (sheet dua langkah, sakelar "Tampil di form") |
| `feature/admin/AdminScreen.kt` (ditulis ulang) | `AdminHost` kini host layar penuh dengan tumpukan rute teks (`gh:<id>`, `list:<RESOURCE>:<id or ->`, `people`, `account:<id>`, `off:<id>`); `AdminEditor` layar penuh hanya untuk **akun baru**; `AdminFieldInput`, `AdminPasswordFields`, `AdminConflict`, `CopyMastersFlow` dipakai bersama. Pemilih "Pilih data → Jenis data" sudah dihapus. |
| `feature/admin/AdminViewModel.kt` | `openGreenhouse`, `settingsFor/settings` (dimuat ulang di `load()` setelah tiap simpan), `clearPeriod` (penetapan periode `planting_group_id: null` lalu muat ulang versi GH), `openChanged` (buka form dengan nilai sudah diubah, langsung ke Ringkasan). `detail/closeDetail/setup/returnToGreenhouse` dihapus. |
| `feature/admin/AdminRepository.kt` | `periods`, `overview`, `settings` (hanya API yang sudah ada: greenhouses, manager-home, assignments, targets, users, master per GH) |
| `feature/FinanceRoot.kt` | Tab 6 memakai `KelolaHome`; `AdminHost` dapat `onStartPlanting` dan `unsentFor`; pilihan Terang/Gelap di menu avatar kini **hanya untuk pekerja** (pengelola memilih di Kelola → Akun saya) |
| `feature/ManagerSheets.kt` | `ManageScreen`, `ManageRow`, `ManageBox`, `TextActionRow` dihapus |
| `feature/managerhome/ManagerHomeScreen.kt` | Bagian Pengaturan di detail GH: "Pengaturan greenhouse" membuka halaman pengaturan; "Musim dan kelompok tanam" membuka daftar musim |
| `android/app/src/test/java/com/inofarm/jogja/KelolaDataTest.kt` (baru) | 3 tes JVM |
| `android/app/src/androidTest/java/com/inofarm/jogja/FinanceAdminUiTest.kt` | 4 tes lama disesuaikan (`admin-new` ada di bar bawah → `scrollIfScrollable`), 4 tes baru dengan server fixture stateful `KelolaServer` |

### Hasil yang sudah terbukti (21 Nov 2026)

- `compileDebugKotlin` dan `compileDebugAndroidTestKotlin` lulus.
- `testDebugUnitTest`: **180 tes, 0 gagal** (177 lama + 3 baru).
- Emulator API 30 dengan fixture: `FinanceAdminUiTest` + `FinanceAdminTest` **14/14 lulus** (termasuk sheet bahan → Ringkasan → PATCH `active=false`; nonaktifkan GH ditahan sampai tanam saat ini dikosongkan, lalu PATCH memakai versi baru 4; akun sendiri tanpa Ganti peran/Nonaktifkan; Kelola home).
- Laravel lokal nyata (`inofarm_finance_local`, sudah jalan di 127.0.0.1:8000, login `pengelola1@gmail.com` / `jayainofarm`): Kelola home tampil benar (6 aktif · 1 nonaktif, chip Target belum ada, GH5 "Mulai semai", UJI "Tanam saat ini perlu ditetapkan", Orang 1 pengelola · 1 pekerja, Akun saya dengan Terang/Gelap dan Keluar). Screenshot di scratchpad sesi lama (hilang); ambil ulang.
- **Belum**: lint, halaman pengaturan GH/daftar/sheet/orang/akun/nonaktifkan di backend nyata, bukti, dokumen.

### Temuan yang harus diperbaiki dulu

1. **Halaman pengaturan GH lambat di backend nyata** (>7 detik "Memuat pengaturan…"): `AdminRepository.settings` membaca ±11 daftar berurutan. Jalankan paralel (`coroutineScope { async {…} }`) dan pertimbangkan menampilkan baris yang sudah ada sambil memuat.
2. Pilihan Terang/Gelap (dan filter Semua/Pekerja/Pengelola) memakai `FarmSegmentedTabs` yang rata kiri; mockup memakai dua/tiga kolom sama lebar. Buat segmen sama lebar di `Kelola.kt` (selectable, role Tab/RadioButton, tag `theme-light`/`theme-dark`, `people-filter-*` tetap).
3. Dialog host memberi status bar/nav bar abu-abu (perilaku lama `AdminHost`); periksa apakah bisa edge-to-edge tanpa merusak inset.

### Sisa langkah sampai selesai

1. Perbaiki temuan 1–3, jalankan ulang tes JVM dan admin instrumentasi.
2. `./gradlew :app:lintDebug` harus 0 error.
3. Di emulator terhadap Laravel lokal: buka tiap layar (Kelola home, pengaturan GH, daftar bahan + cari + filter, sheet Ubah bahan langkah 1 dan Ringkasan, musim/kelompok tanam, pekerja dan akun, detail akun, nonaktifkan GH berpemeriksa) dan ambil screenshot (`adb exec-out screencap -p`). Data uji boleh ditambah; jangan menonaktifkan GH milik pengguna yang dipakai (buat GH uji lewat wizard untuk uji nonaktifkan). Navigasi Kelola ada di dock kanan (≈ x 936, y 2094 pada 1080×2340).
4. Tulis bukti di `docs/context/MANAGER_KELOLA_REDESIGN_EVIDENCE_20261121.md` (hasil tes baru vs lama, fixture vs backend nyata, screenshot, batas: belum ponsel fisik/TalkBack/mode gelap), lalu ringkasan satu paragraf di `CLAUDE.md` dan perbarui bagian "Daftar Kelola" di `DESIGN.md`.
5. Laporkan ke pengguna; tanyakan sebelum commit.

### Catatan lain

- `backend/CLAUDE.md` meminta memasang Laravel Boost; **tidak** dilakukan (backend tidak berubah, dan aturan proyek melarang menambah dependency tanpa alasan). Sebutkan ke pengguna bila relevan.
- Nama tag tes penting: `manage-gh-<id>`, `manage-start-<id>`, `manage-users`, `manage-logout`, `gh-settings`, `gh-materials`, `admin-toggle-active`, `deactivate-clear-period`, `admin-toggle-confirm`, `admin-row-<id>`, `admin-filter-on/off`, `admin-active`, `admin-submit`, `admin-save`, `person-<id>`, `account-role`, `kelola-back`.
