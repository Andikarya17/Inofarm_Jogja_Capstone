# Audit pencatatan pekerja dan administrasi — 9 November 2026

## Hasil dan batas pemeriksaan

Fitur kegiatan dan panen **masih ada** pada source `d359502`. Setelah fetch GitHub, `github/main` adalah `419a144` (merge PR #47); tree identik dengan `d359502`. Branch dokumen `codex/worker-recording-recovery-prompt` sudah fast-forward ke main tersebut. Tidak ada perubahan runtime dalam audit ini.

Emulator `emulator-5554`, aplikasi `com.inofarm.jogja.finance`, sudah berada dalam sesi dengan empat tab pekerja ketika diperiksa. Tidak dilakukan login baru dengan kredensial pengguna. Pembacaan hierarchy UI menunjukkan:

- Panen dan Kegiatan: `Lihat konteks di Beranda`, riwayat kosong, `Data server sudah diperiksa`, sinkron terakhir 09 Nov 2026 21:20 WIB.
- Beranda: `Pilih greenhouse` dan `Belum ada greenhouse dalam penugasan yang tersimpan. Sinkronkan atau hubungi pengelola.`

Ini membuktikan konteks greenhouse tidak tersedia pada tampilan yang diperiksa, **bukan** membuktikan penyebab definitif di database. Assignment kosong/kedaluwarsa, endpoint atau akun berbeda, scope refresh dan cache belum dibedakan lewat request autentikasi/API/DB. Tidak ada penugasan dibuat, password diubah, atau transaksi disimpan. Hierarchy dibaca; audit ini bukan verifikasi visual screenshot atau uji CRUD lengkap. Tidak menjalankan ulang build/backend/instrumentation.

## Source dan branch

| Temuan | Bukti |
|---|---|
| Form tambah kegiatan/panen masih tersedia | `FinanceRoot.kt`: aksi `finance-new`, `harvest-new`, `history-new`; `ActivityViewModel.new`, `HarvestViewModel.new`; `HarvestScreen.kt` |
| Aksi Beranda disembunyikan sampai konteks lengkap | `FinanceRoot.kt` memilih greenhouse aktif, musim terkait, kelompok aktif; tombol enabled saat musim `open` |
| Tab riwayat kembali ke Beranda jika konteks belum siap | `FinanceRoot.kt`, cabang `tab==2 || tab==3`, `canRecord` dan `recordLabel` |
| Scope server bergantung assignment | `backend/app/Domain/Identity/GreenhouseAccess.php`: user, active, effective_from/effective_until |
| Cache konteks dibaca per akun | `feature/context/ContextRepository.kt`: greenhouses/seasons/planting_groups dari LocalFinanceStore; investigasi sync masih diperlukan |
| Pekerja boleh membuat dan mengoreksi catatan sendiri | `ActivityService.php`, `HarvestService.php`; pembatalan accepted hanya pengelola, sesuai PRD |

`git rev-list --count HEAD..codex/finance-activity-entry` dan `HEAD..codex/finance-harvest` masing-masing menghasilkan **0**. Commit `ff5869f` (entry kegiatan), `89c5026` (history/conflict) dan `bb3d864` (panen) sudah menjadi ancestry source aktif. Merge ulang branch tersebut tidak menambah fitur. `codex/dummy-worker-history` memuat jalur legacy owner/device/ESP32, bukan modul operasional v2 yang dapat digabung penuh. Jangan mengembalikan IoT atau menimpa frontend baru untuk menangani masalah ini.

## Administrasi yang sudah ada dan gap nyata

- `feature/admin/AdminForm.kt` dan `AdminScreen.kt` sudah menyediakan resource Greenhouse, Akun, Penugasan, Musim dan Kelompok; akun memiliki role pekerja/pengelola dan password baru/reset.
- `MasterService.php` membuat assignment untuk pengelola pembuat greenhouse. Membuat greenhouse **tidak otomatis menugaskan seluruh pekerja**.
- `UserController.php` membuat akun sekaligus assignment greenhouse awal. Untuk akun existing, alur penugasan perlu ditelusuri dan diuji.
- **Edit email belum tersedia**: `AdminForm.fields` hanya menambahkan email pada create; update `UserController` tidak menerima atau mengubah email. Permintaan terbaru memerlukan perluasan frontend, validasi, ledger/audit dan backend; sekadar memunculkan field belum cukup.
- Update nama/role/aktif/password sudah mencabut token target; perilaku draf, retry password dan login ulang harus dipertahankan. Password bukan nilai yang boleh dibaca kembali.
- Delete master/account harus dinyatakan sebagai nonaktifkan dengan histori tetap, mengikuti aturan proyek. Pembatalan catatan accepted pekerja bukan hak existing; jangan mengklaim CRUD identik untuk semua peran.

## Empat sumber proposal

Keempat berkas di `D:/Semester 7/Capstone 2/Proposal` diekstrak secara read-only. Ekstraksi lokal berada di `outputs/worker-recording-audit/` (tidak dipublikasikan). Pembacaan relevan bukan proofreading visual seluruh halaman.

1. **Proposal Aplikasi Pencatatan Keuangan .pdf**, 60 halaman: PDF hal. 11–12 scope pekerja/pengelola; 16–18 KF/KNF; 20–21 aktor; 39–40 formulir nutrisi, semprot multi-bahan, stadia, panen grade/jumlah/kg, pekerjaan spontan, kegiatan dilewati dan pemisahan status. Ini baseline utama.
2. **Jadwal  MT 3 pemupukan dan stadia tumbuh Melon GH Krebet_093055.xlsx**, dua sheet:
   - `Sheet1!B5:H5`: tanggal, HSS/HST, PPM, pH, stadia, penambahan pupuk (L), spray pupuk tambahan opsional. `J4:M4`: HST, tanggal, bahan spraying, waktu. `L9` memuat dua bahan. `H80` dan `H90` menyebut panen varietas berbeda.
   - `Jadwal general!A2:F67`: pekerjaan dari benih, penyiraman, kebersihan, tali, pengendalian hama/penyakit, pinching, polinasi, pengamatan tanaman, fertigasi, seleksi buah, sampai panen dan pascapanen. `C25` menyebut EC/pH/DO/suhu air; `C31` tinggi/jumlah/lebar daun; `F36` grade/cacat/pohon tidak berbuah; `F37` panen.
   - Bukan semua nilai sheet adalah pengamatan aktual. pH berupa rentang, penomoran umur berganti, dan beberapa tanggal/HST tidak selaras (misalnya baris 20–21). `E90` berisi `5.8-6.12`; jangan memperbaiki atau menjadikannya aturan produksi secara diam-diam. Jadwal historis bukan dosis/rekomendasi otomatis.
3. **Proposal Monitoring dan Pengingat Nutrisi Melon NFT Terbaru - Copy (3).docx**: isi aktual juga membahas dua peran, pencatatan manual tanpa IoT, draf/sync, panen terpisah dari penjualan; nama berkas bukan alasan mengembalikan monitoring IoT.
4. **Dwito Mufti Agung_574189_Makalah Analisis Usahatani Meloh Hidroponik.pdf**, 27 halaman: acuan konteks budidaya, biaya dan penerimaan. Angka studi bukan default hasil aktual, harga, dosis atau parameter produksi aplikasi.

## Tindak lanjut

[Master prompt pemulihan dan administrasi](MASTER_PROMPT_WORKER_RECORDING_RECOVERY.md) memuat diagnosis, alur pengelola→penugasan→pekerja, mapping Excel, acceptance dan verifikasi. Dokumen ini tidak menyatakan masalah sudah diperbaiki. Penyebab kosongnya konteks akun runtime dan uji end-to-end pada akun berpenugasan sah tetap terbuka.
