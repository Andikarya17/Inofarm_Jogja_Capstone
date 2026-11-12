# Canopy: bukti implementasi (12 November 2026)

Rencana: [MASTER_PROMPT_FRONTEND_CANOPY.md](MASTER_PROMPT_FRONTEND_CANOPY.md). Baseline sebelum Canopy: [FRONTEND_CANOPY_BASELINE_20261112.md](FRONTEND_CANOPY_BASELINE_20261112.md). Semua bukti di sini berasal dari emulator API 30 (1080x2340) dengan fixture MockWebServer. Bukan perangkat fisik, bukan backend nyata, dan bukan TalkBack nyata.

## Status per fase

| Fase | Branch | Status |
|---|---|---|
| 0, 0b | `codex/canopy-baseline`, `codex/canopy-test-nav` | Selesai |
| 1 Fondasi (token, font, ikon, komponen) | `codex/canopy-foundation` | Selesai |
| 2 Login | `codex/canopy-login` | Selesai, ditinjau reviewer |
| 3 Shell, dock, sheet Catat | `codex/canopy-shell` | Selesai |
| 4 Beranda semua greenhouse | `codex/canopy-home` | Selesai, ditinjau reviewer |
| 5 Detail greenhouse | `codex/canopy-detail` | Selesai, ditinjau reviewer |
| 6 Riwayat dan Cek catatan | `codex/canopy-history` | Selesai, ditinjau reviewer; baris pengelola dengan data terbukti di `mhist-manager-rows.png` (terang, gelap, 200%) |
| 7 Keuangan dan Kelola | `codex/canopy-finance-manage` | Selesai: hero kas per greenhouse, chip, sheet Alat keuangan bergrup, kartu identitas Kelola |
| 8 Form | `codex/canopy-forms` | Selesai di kode; bukti layar sebagian (lihat bawah). Ditinjau reviewer; perbaikan di `251a7d7` |
| 9 Polish | `codex/canopy-polish` | Sebagian: pemeriksaan kontras (semua pasangan baru lulus, 6,08 sampai 8,56:1), tanpa animasi berulang, `DESIGN.md` diperbarui dari source, uji penuh tanpa regresi. Audit native, TalkBack nyata dan 360 dp tidak dikerjakan |

## Form pengelola (Fase 8)

Kerangka `FarmFormFrame` (header X + status draf, strip konteks forest, kartu `FarmFormSection`, bar bawah penuh lebar dengan garis atas) di `feature/FormCanopy.kt`. Berlaku untuk: pengeluaran dan alokasi, penjualan, aset dan penyusutan, kegiatan dan pengukuran, panen, form admin, form akun, wizard Tambah greenhouse, dan semua langkah Periksa (`FarmReviewFrame`). Form pekerja tidak berubah (K8): cabang `worker` memakai tata letak lama dan tag yang sama.

Bukti layar (emulator, terang, 100%) di `outputs/frontend-canopy-20261112/after/`: `v2-measure-*`, `v2-harvest-review`, `forms-*`, `account-*`, `admin-wizard-1*`, `secondary-sale-*`, `secondary-allocation-*`, `secondary-asset-*`, `secondary-depreciation-*`. Pada 200%: `font200/forms-*` (pengukuran dan panen, termasuk keyboard terbuka).

Temuan yang diperbaiki saat pengujian: hero kas memotong nominal panjang pada 200% (kini membungkus di titik ribuan); bar IME lama tidak lagi tampil di atas form pengelola; "Total isian" panen memakai koma desimal.

## Belum dibuktikan atau belum dikerjakan

- Wizard langkah 2 dan 3 belum terlihat di layar (fixture tidak punya route simpan greenhouse); hanya langkah 1.
- Form pada mode gelap dan 200% hanya sebagian: pengukuran dan panen 200% ada; akun, penjualan, aset, pengeluaran dan semua Periksa pada 200% dan gelap belum. Chip Cacat amber pada permukaan gelap belum dilihat.
- Lebar 360 dp belum diperiksa (emulator 411 dp).
- Pemisah ribuan tampilan pada isian nominal (5.13.5) belum ada: menyentuh tampilan nilai yang divalidasi, butuh keputusan. Kartu forest 34 sp untuk nominal pengeluaran belum ada.
- Tombol "Tambah ..." masih garis penuh, bukan garis putus. Baris rincian penjualan masih memakai tombol teks "Hapus rincian N", bukan `FarmDetailCard`. Tanggal dan jam di form kegiatan bertumpuk, bukan berdampingan. Langkah Periksa belum punya baris angka utama dua kolom.
- Dialog (wizard, admin) memakai bilah sistem gelap pada mode terang; sudah demikian sebelum Canopy.
- Picker greenhouse yang dibuka dari hero Keuangan berupa dialog hijau gelap bahkan pada mode terang (terbaca, tetapi bocoran tema hero).
- Fase 9: `audit.native`, `harden`, TalkBack, `impeccable-documenter`, skala animasi nol untuk parallax, `IMPLEMENTATION_STATUS.md`, `GAP_ANALYSIS.md`, `docs/context/README.md` (perlu diff ditunjukkan ke pengguna lebih dulu).

## Hasil pemeriksaan (sesi ini)

- Build debug dan test APK, 104 tes JVM, lint: 0 error, 18 warning, 1 hint (baseline 16 warning; sebagian berasal dari peringatan versi dependensi). `python scripts/check-finance-context.py`: tanpa error.
- `FinanceEntryUiTest` penuh pada pohon final (setelah `701ee5b`): 64 tes, **15 gagal**, persis 15 nama yang sama dengan baseline Fase 0b, sebab yang sama; tidak ada kegagalan baru. Sebelumnya, pada `6019e72`, ada 17 gagal; dua tambahan (`workerScheduleEntryHasNoTypeChoice`: guliran sebelum panel hari ini dimuat, kini menunggu barisnya; `glassFrontend…`: judul "Penjualan dan penerimaan" yang hilang saat sheet dibagi grup, kini "Catat dan alokasi") diperbaiki di `251a7d7`.
- `FinanceAdminUiTest` (2 tes) lulus. Kelas backend device dilewati (butuh backend dan `devicePhase`).

## Perubahan pada tes (setara, tidak ada assertion dihapus)

- Tag dialihkan: `manager-home-picker` ke `manager-add-greenhouse`, `manager-home-all` ke `manager-back`.
- `choose()` menerima opsi terpilih sebagai pengganti "opsi hilang" (dropdown menjadi chip).
- `scrollIfScrollable()` menggantikan `performScrollTo()` pada tombol di bar bawah (bar berada di luar area gulir), termasuk tombol admin.
- Helper `readyListNode`, `sendQueuedNow` toleran antrean yang menguras diri, `openFinanceOutbox` menunggu idle.
- `offlineQueueRecreation…`: menunggu `posts==1` sebelum teks "Terkirim" (snackbar simpan memuat kata itu), assertion `posts==1` dipertahankan.
- Judul sheet Alat keuangan: "Penjualan dan penerimaan" menjadi "Catat dan alokasi".
- Satu assertion picker dialihkan karena picker tanggal kini dialog Android.

## Dampak pada pekerja (K8)

Pekerja tetap memakai FAB, dua tab dan toolbar "Inofarm". Yang berubah: font Plus Jakarta Sans, gaya kotak sheet Catat, dan login. Screenshot: `outputs/frontend-canopy-20261112/after/shell-worker-*.png`.

## Bahan screenshot

`outputs/frontend-canopy-20261112/` (diabaikan git): `before/`, `after/`, `dark/`, `font200/`, `landscape/`. Tes tangkap-layar sementara (`canopyShot*`) tidak di-commit.
