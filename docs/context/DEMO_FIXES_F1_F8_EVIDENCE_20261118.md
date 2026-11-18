# Perbaikan F1–F8 untuk demo, 18 November 2026

Branch `demo-fixes-f1-f8` dari `main` `e892561`. Menindaklanjuti temuan F1–F8 di [audit full-stack](FULLSTACK_AUDIT_20261118.md). Keputusan pengguna 18 November: F1 berupa arahan ke Kelola ditambah salin master, F6 mengikuti penetapan pengelola, dan F4 lewat perubahan kecil di server. Diverifikasi di emulator `Inofarm_Botanical_API30` terhadap Laravel lokal dan database aplikasi `inofarm_finance_local`, bukan fixture.

## Yang berubah

| # | Perubahan | Berkas utama |
|---|---|---|
| F1 | Picker master yang kosong (bahan, satuan, grade, kategori biaya) kini menyebut apa yang kurang. Untuk pengelola ada tombol "Tambah …" yang membuka Kelola pada greenhouse itu dengan form tambah langsung terbuka; isian form asal tetap utuh. Pekerja diminta menghubungi pengelola. Kelola → data master punya "Salin dari greenhouse lain" (API `copy-masters` yang sudah ada), dengan dialog konfirmasi dan hasil "Tersalin: …". | `FarmPicker.kt`, `FinanceRoot.kt` (`Choice`, `MasterGap`, `LocalAddMaster`), `AdminScreen.kt`, `AdminViewModel.kt`, `AdminRepository.kt`, `EntryForm.kt`, `HarvestScreen.kt`, `FinancialEntryScreen.kt` |
| F2 | Kartu atas halaman GH pekerja menampilkan umur yang terakhir diketik ("HSS 26 · umur diisi 18 Nov"), bukan hari sejak semai yang dihitung. Stadia juga dibaca dari catatan per greenhouse. | `WorkerGreenhousePage.kt` |
| F3 | Angka pengukuran tampil "2,2 mS/cm" dan "1100 ppm" di inbox dan detail Cek catatan, acuan/toleransi/selisih, ringkasan form dan detail catatan. | `IdrGrouping.kt` (`measureText`), `ActivityVerificationScreen.kt`, `EntryForm.kt`, `FinanceRoot.kt` |
| F4 | Jadwal di Beranda pengelola dan agenda pekerja membuka form kegiatan per greenhouse, bukan form lama. Bagian yang sejenis dengan jadwal membawa `schedule_id`. Server kini menerima jadwal pada catatan per greenhouse bila jadwal itu milik kelompok di musim yang sama dan jenisnya cocok. Agenda menghitung catatan per greenhouse sebagai realisasi. | `EntryDraft.kt`, `ActivityViewModel.kt`, `FinanceRoot.kt`, `WorkerDayAgenda.kt`, `backend/app/Domain/Activities/ActivityService.php`, tes baru di `RecordPerGreenhouseTest.php` |
| F5 | Greenhouse yang dipilih di sheet Catat dipakai juga untuk Biaya dan Penjualan. | `ManagerSheets.kt`, `FinanceRoot.kt` |
| F6 | Pekerja melihat "Tanam saat ini belum dipilih" pada GH yang belum ditetapkan pengelola, di kartu Beranda dan halaman GH. Pencatatan per greenhouse tidak diblokir. | `WorkerGreenhouseCards.kt`, `WorkerGreenhousePage.kt` |
| F7 | Error form biaya/alokasi muncul di bawah isiannya dengan gaya yang sama seperti isian teks (bingkai merah pada picker); Simpan yang ditolak menggulir ke atas. | `FinancialEntryScreen.kt`, `FormCanopy.kt`, `FinanceRoot.kt` (`FieldError`) |
| F8 | Tombol "Ubah musim atau kelompok" di halaman GH pekerja dihapus. Riwayat pekerja tidak lagi menampilkan teks "Semua greenhouse" di samping Filter, dan sheet Filter tidak lagi menanyakan greenhouse (sudah ada chip). | `FinanceRoot.kt`, `HistoryScreen.kt` |

## Bukti

| Pemeriksaan | Hasil |
|---|---|
| `scripts/test-backend.ps1` | OK, 176 tes, 4.784 assertion (175 + 1 tes jadwal baru) |
| Android `testDebugUnitTest` + `lintDebug` | 137 tes lulus; lint 0 error |
| `check-finance-contracts.py`, `check-finance-context.py` | PASS; 0 error |
| F1 emulator | Picker satuan kosong → "Tambah satuan" → form Satuan untuk GH tes → "gram" tersimpan → tutup Kelola → form kegiatan utuh. Sama untuk bahan nutrisi "AB mix". Kegiatan nutrisi 250 gram HSS 26 tersimpan (`activities.activity_type=nutrition`, `planting_group_id` null, `activity_materials.quantity=250`). Salin dari tes ke yyy: "Tersalin: 1 varietas, 1 bahan, 1 satuan, 1 kategori biaya." |
| F2 emulator | Halaman GH tes pekerja: "HSS 26 · umur diisi 18 Nov", "stadia belum dicatat". |
| F3 emulator | Inbox dan detail Cek catatan: "2,2 mS/cm", "1100 ppm". |
| F4 emulator + DB | Jadwal "Ukur EC dan PPM" (tes1, pengukuran) dibuat lewat Kelola; pekerja menekan Catat pada jadwal → form baru "Dari jadwal: Pengukuran manual" → kirim. Baris server: `schedule_id` terisi, `planting_group_id` null. Agenda: "1 dari 1 selesai", Beranda "1 dari 1 tugas selesai". |
| F5 emulator | Catat → ttt → Biaya langsung membuka form untuk ttt. |
| F6 emulator | Kartu pekerja "UJI Pemulihan WR 09 Nov": "Tanam saat ini belum dipilih". |
| F7 emulator | Simpan biaya kosong → gulir ke atas, error nominal dan kategori di bawah isiannya. |
| F8 emulator | Halaman GH tanpa "Ubah musim atau kelompok"; Riwayat pekerja hanya chip greenhouse; sheet Filter tanpa pilihan greenhouse. |

## Data yang ditulis ke `inofarm_finance_local` saat verifikasi

Tidak ada yang dihapus. GH tes: satuan "gram", bahan "AB mix", 1 kegiatan nutrisi (pengelola), 1 jadwal "Ukur EC dan PPM" 18 Nov 20.00 WIB, 1 pengukuran pekerja dari jadwal (EC 2,3, PPM 1150, HSS 26). GH yyy: hasil salin master dari tes (1 varietas, 1 bahan, 1 satuan, 1 kategori "Audit 18 Nov").

## Batas

- Belum dicek di mode gelap, skala font 200% dan ponsel fisik; komponen baru memakai token tema Material yang sama.
- Tes instrumentasi Android tidak dijalankan. Helper `openHomeContext` di `androidTest/FinanceNavigationActions.kt` masih mencari tombol `finance-context-open` di halaman GH pekerja yang sudah dihapus (F8); tes yang memilih musim/kelompok lewat tombol itu perlu disesuaikan.
- Jadwal berjenis "Pekerjaan umum" hanya bisa tertaut bila form hanya berisi catatan (bagian umum hanya dibuat saat tidak ada bagian lain); form menolak dengan pesan bila bagian jadwal kosong.
- Jadwal ke catatan per greenhouse hanya untuk musim yang sama; jadwal milik GH/musim lain atau jenis lain ditolak 422 (tes PHPUnit).
