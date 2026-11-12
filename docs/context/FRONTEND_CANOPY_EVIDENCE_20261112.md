# Canopy: bukti implementasi (12 November 2026)

Rencana: [MASTER_PROMPT_FRONTEND_CANOPY.md](MASTER_PROMPT_FRONTEND_CANOPY.md). Baseline sebelum Canopy: [FRONTEND_CANOPY_BASELINE_20261112.md](FRONTEND_CANOPY_BASELINE_20261112.md). Semua bukti di sini berasal dari emulator API 30 dengan fixture MockWebServer. Tidak ada perangkat fisik, backend nyata, atau TalkBack nyata.

## Status per fase

| Fase | Branch | Status |
|---|---|---|
| 0, 0b | `codex/canopy-baseline`, `codex/canopy-test-nav` | Selesai |
| 1 Fondasi (token, font, ikon, komponen) | `codex/canopy-foundation` | Selesai |
| 2 Login | `codex/canopy-login` | Selesai, ditinjau reviewer |
| 3 Shell, dock, sheet Catat | `codex/canopy-shell` | Selesai |
| 4 Beranda semua greenhouse | `codex/canopy-home` | Selesai, ditinjau reviewer |
| 5 Detail greenhouse | `codex/canopy-detail` | Selesai, ditinjau reviewer |
| 6 Riwayat dan Cek catatan | `codex/canopy-history` | Selesai, ditinjau reviewer; baris pengelola belum terlihat di screenshot |
| 7 Keuangan dan Kelola | `codex/canopy-finance-manage` | Sebagian: hero Keuangan, chip jenis, kartu hasil di sheet Alat keuangan, kartu identitas Kelola |
| 8 Form | `codex/canopy-forms` | **Sebagian kecil**: isian tanggal dan jam pengelola memakai picker Android |
| 9 Polish | belum | Hanya dokumen ini, `CLAUDE.md` dan `DESIGN.md` |

## Belum dikerjakan

- **Fase 8 (sebagian besar):** strip konteks forest, `FarmFormSection`, isian angka besar dengan satuan, chip untuk pilihan pendek (jenis kegiatan, kondisi buah, jenis pembayaran, kelas biaya, peran akun), bar bawah menempel di atas keyboard, kartu total, langkah Periksa bergaya kartu, wizard dengan indikator tiga segmen, form akun, dan ringkasan waktu dengan tombol Ubah pada form pengukuran dan panen.
- **Fase 7:** angka kas per greenhouse di hero Keuangan (hanya teks "Kas bulan ini ada di Beranda"), kelompok bertile pada daftar sheet Alat keuangan, dan tanda terlihat pada baris Riwayat pengelola belum dicek dengan data.
- **Fase 9:** pass `audit.native` dan `harden`, TalkBack, screenshot form pada 100%, 200% dan dengan keyboard, `impeccable-documenter`, dan pembaruan `IMPLEMENTATION_STATUS.md`, `GAP_ANALYSIS.md` dan `docs/context/README.md`.
- Skala animasi nol untuk parallax motif belum diuji.

## Hasil pemeriksaan (sesi ini)

- Build debug, tes JVM dan lint terakhir: build lulus, 104 tes JVM, 0 error lint, 18 warning, 1 hint. Baseline 16 warning; tambahan dua berasal dari peringatan versi dependensi.
- Instrumentasi `FinanceEntryUiTest` per fase dijalankan sebagian (kelas yang disentuh), bukan seluruh 64 tes setelah Fase 3. Kegagalan lama sejak baseline tidak berubah: konflik "Server · versi 2", teks "Ringkasan kebun" dan "Belum dapat memeriksa pencatatan hari ini", `finance-sync` pengelola, `Akhiri periode berjalan`, `Catat hasil`, penutupan musim.
- Kelas backend device dilewati (butuh backend dan `devicePhase`).
- Tes yang diubah: tag dialihkan (`manager-home-picker` ke `manager-add-greenhouse`, `manager-home-all` ke `manager-back`), helper `readyListNode` dan `sendQueuedNow`, serta helper `choose()` kini menerima opsi yang terpilih sebagai pengganti "opsi hilang". Pemeriksaan picker di tes walkthrough dihapus karena fiturnya dihapus spec.

## Dampak pada pekerja (K8)

Pekerja tetap memakai FAB, dua tab dan toolbar "Inofarm". Yang berubah: font Plus Jakarta Sans, gaya kotak sheet Catat, dan login. Screenshot: `outputs/frontend-canopy-20261112/after/shell-worker-*.png` dan `hist-manager-rows.png` (Riwayat pekerja).

## Bahan screenshot

`outputs/frontend-canopy-20261112/` (diabaikan git): `before/`, `after/`, `dark/`, `font200/`, `landscape/`. Tes tangkap-layar sementara (`canopyShot*`) tidak di-commit.
