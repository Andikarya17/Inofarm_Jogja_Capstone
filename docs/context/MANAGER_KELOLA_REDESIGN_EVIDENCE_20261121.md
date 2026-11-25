# Bukti: desain ulang tab Kelola pengelola (21–22 November 2026)

Branch `perbaikan-bug-audit-pengelola`, **belum di-commit**. Lanjutan dari [serah terima Kelola](HANDOFF_KELOLA_REDESIGN_20261121.md). Branch yang sama memuat perbaikan bug B1–B6 dan UI U1/U3/U4/U5 dari [audit pengelola](MANAGER_FULL_AUDIT_20261121.md); dokumen ini hanya mencatat pekerjaan Kelola.

Label bukti: **tes otomatis** (JUnit JVM), **emulator + fixture** (instrumentasi dengan server HTTP palsu), **emulator + backend nyata** (`Inofarm_Botanical_API30`, API 30, mode terang, terhadap Laravel lokal `127.0.0.1:8000` dan MySQL `inofarm_finance_local`, akun `pengelola1@gmail.com`). **Tidak ada uji ponsel fisik, TalkBack, mode gelap, atau skala font besar.** Backend tidak diubah dan PHPUnit tidak dijalankan untuk pekerjaan ini.

## Arah dan keputusan

- Mockup yang disetujui: kanvas Kelola (artifact privat pengguna, artboard `Main`, `Greenhouse`, `Bahan`, `UbahBahan`, `Orang`, `Akun`, `Nonaktifkan`). Kelola diatur per objek: Greenhouse, Orang, Akun saya. Gaya Canopy tanpa warna baru.
- Keputusan pengguna 21 November 2026: sheet ubah data master **tetap** memakai langkah Ringkasan (Batal / Simpan → Ringkasan dengan Ubah / Simpan).

## Yang berubah pada sesi 22 November

| Temuan | Perubahan | File |
|---|---|---|
| Halaman pengaturan GH lambat (>7 detik "Memuat pengaturan…" di backend nyata) | `AdminRepository.settings` membaca sepuluh daftar dan periode manager-home **bersamaan** (`coroutineScope` + `async`); `overview` (Kelola home) juga membaca penugasan, target dan periode bersamaan | `feature/admin/AdminRepository.kt` |
| Terang/Gelap dan filter Semua/Pekerja/Pengelola rata kiri | `FarmSegmentedTabs(equalWidth = true)`: kolom sama lebar tanpa gulir, label boleh dua baris, pil mengikuti tinggi tab. Dipakai juga untuk Aktif/Nonaktif dan Musim/Kelompok tanam agar semua segmen di Kelola seragam. Tag tetap (`theme-*`, `people-filter-*`, `admin-filter-*`, `admin-list-*`), peran Tab | `core/design/CanopyKit.kt`, `feature/admin/Kelola.kt` |
| Status bar dan nav bar abu-abu/hitam di dialog Kelola | `FarmFullScreenDialog`: `decorFitsSystemWindows = false`, bar transparan, ikon bar mengikuti tema aplikasi, tanpa dim. Dipakai `AdminHost` dan wizard Tambah greenhouse. Strip status bar diwarnai `surface` supaya menyatu dengan top bar halaman Kelola | `core/design/CanopyKit.kt`, `feature/admin/AdminScreen.kt`, `feature/admin/GreenhouseWizard.kt` |
| **Bug (backend nyata):** Nonaktifkan GH1 menulis "Tidak ada tanam saat ini" dan tombol aktif, padahal GH1 punya tanam saat ini | `GET greenhouses` hanya mengirim `id, name, active, version`, jadi `running_group_id` selalu kosong di daftar. Halaman kini memakai periode manager-home: `ready` dan `needs_review` menahan nonaktifkan (server menolak 422 selama ada kelompok yang ditetapkan; `needs_review` bisa menyimpan penetapan lama). Halaman menampilkan memuat sampai periode terbaca. Fixture `KelolaServer` diperbaiki agar baris GH sama dengan API nyata | `feature/admin/Kelola.kt`, `androidTest/.../FinanceAdminUiTest.kt` |
| Chip "Aktif" di hero akun hampir tak terbaca (hijau gelap di atas forest) | Chip memakai `FarmTone.Forest` (tile + sage) | `feature/admin/Kelola.kt` |
| Keterangan "Keluar dari semua pera…" terpotong di samping label dua baris | `KelolaRow(hint = …)`: keterangan akibat tindakan tampil di bawah label (Jadikan pengelola/pekerja, Atur ulang password, Aktifkan lagi) | `feature/admin/Kelola.kt` |
| Ruang kosong tinggi di atas pegangan sheet Ubah bahan | Sheet hanya memakai inset bawah dan samping (`safeDrawing.only(Bottom + Horizontal)`) | `feature/admin/Kelola.kt` |
| Lint `ComposableNaming` | `activeChip` → `ActiveChip` | `feature/admin/Kelola.kt` |

## Hasil tes (22 November 2026, sesudah semua perubahan di atas)

- `:app:testDebugUnitTest`: **180 tes, 0 gagal** (sama dengan 21 November: 177 lama + 3 `KelolaDataTest`).
- Emulator + fixture, `FinanceAdminUiTest` + `FinanceAdminTest`: **14/14 lulus** (dijalankan dua kali; kedua kalinya setelah fixture tidak lagi mengirim `running_group_id`, sehingga tes nonaktifkan kini bergantung pada periode manager-home seperti API nyata).
- `:app:lintDebug`: **0 error**, 19 warning, 4 hint (21 November: 20 warning; warning `ComposableNaming` dari Kelola hilang).
- `compileDebugAndroidTestKotlin` lulus.
- `FinanceEntryUiTest` penuh **tidak dijalankan** pada sesi ini.

## Emulator + backend nyata (22 November 2026)

Screenshot `adb exec-out screencap -p` (1080×2340) di `outputs/kelola-redesign-20261121/` (folder ignored, tidak di-commit). Tidak ada data yang disimpan ke `inofarm_finance_local`: sheet dan nonaktifkan dibuka lalu dibatalkan.

| Layar | Screenshot | Hasil |
|---|---|---|
| Kelola home | `02-kelola-home.png`, `03-kelola-home-akun.png` | 6 aktif · 1 nonaktif, chip Target belum ada, GH5 "Mulai semai", UJI "Tanam saat ini perlu ditetapkan", Orang 1 pengelola · 1 pekerja, Akun saya dengan Terang/Gelap sama lebar dan Keluar |
| Pekerja dan akun | `04-people.png`, `05-people-filter-pekerja.png` | Filter tiga kolom sama lebar; Pekerja menyaring ke 1 akun; status bar edge-to-edge, ikon gelap, strip menyatu dengan top bar |
| Detail akun pekerja | `06-account-worker.png` | Chip Aktif terbaca, keterangan di bawah label, Nonaktifkan akun merah |
| Akun sendiri | `07-account-self.png` | Tanpa Jadikan pekerja dan Nonaktifkan, dengan penjelasan |
| Pengaturan GH1 | `08a-gh-settings-1s.png`, `08b-gh-settings-2-5s.png`, `08-gh-settings-top.png`, `09-gh-settings-bottom.png` | 1 detik setelah ketuk masih memuat, 2,5 detik sudah tampil penuh (sebelumnya >7 detik). Hero tanam saat ini, Budidaya, Bahan/panen/biaya, Salin daftar, Orang dan status |
| Bahan | `10-materials.png`, `11-materials-search.png`, `12-materials-nonaktif.png` | 11 aktif; cari "Bi" → Bion M; filter Nonaktif → "Tidak ada bahan yang nonaktif." |
| Sheet Ubah bahan | `13-sheet-edit-material.png`, `14-sheet-ringkasan.png`, `15-discard-confirm.png` | Langkah 1 dengan sakelar Tampil di form; mematikan sakelar → Ringkasan "Tampil di form: Tidak" dengan tombol Nonaktifkan; Ubah → Batal → "Buang perubahan?" → Buang. Tidak disimpan |
| Musim dan kelompok tanam | `17-seasons.png`, `18-groups.png` | Musim/Kelompok tanam dan Berjalan/Ditutup sama lebar; 1 musim, 2 kelompok |
| Nonaktifkan GH1 | `19-deactivate-gh1.png` | Sesudah perbaikan: "Masih ada tanam saat ini · yellow hami · gh2 m3", tombol Kosongkan, Nonaktifkan nonaktif. Dibatalkan tanpa mengosongkan |

Waktu muat diukur dari screenshot berjeda, bukan profiler; angka pastinya **tidak diukur**.

## Batas dan sisa

- Belum ponsel fisik, TalkBack, mode gelap (`FarmFullScreenDialog` mengatur ikon bar untuk mode gelap, belum dilihat), skala font 200% atau lebar 360 dp. Label segmen sama lebar boleh dua baris, belum dilihat pada font besar.
- Alur kosongkan tanam saat ini lalu nonaktifkan hanya terbukti dengan fixture; di backend nyata tidak dijalankan agar GH pengguna tidak berubah. Wizard untuk membuat GH uji juga tidak dijalankan.
- Strip nav bar memakai warna `background`, sedangkan bar bawah halaman (Tambah …) memakai `surface`; ada sambungan warna tipis di bawah.
- Dialog konfirmasi ("Buang perubahan?") memakai jendela AlertDialog sendiri dengan scrim; ikon status bar di atas scrim terang, sesuai bawaan Android.
- Laravel Boost yang diminta `backend/CLAUDE.md` tidak dipasang (backend tidak berubah).
