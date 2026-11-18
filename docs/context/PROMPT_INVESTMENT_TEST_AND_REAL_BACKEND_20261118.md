# Prompt sesi baru: tes investasi gagal dan uji backend nyata (18 November 2026)

Salin blok di bawah ke sesi Claude Code baru di folder repo ini.

```text
Baca CLAUDE.md, AGENTS.md, backend/AGENTS.md, lalu docs/context/RECORDING_REWORK_EVIDENCE_20261118.md dan docs/context/PILOT_READINESS_EVIDENCE_20261115.md. Posisi kode: github/main setelah PR #78 (rombak pencatatan per greenhouse sudah di-merge; migration 22 sudah di inofarm_finance_local). Fetch dulu dan bekerja di branch baru dari github/main, satu branch per bagian, jangan merge ke main tanpa izinku.

Aturan tetap:
- Emulator hanya Inofarm_Botanical_API30, mode terang (gelap lewat pengaturan di aplikasi). Emulator ini tiga kali mati atau ANR dengan error grafis glTexImage2D/glPixelStorei; coba jalankan dengan -gpu swiftshader_indirect seperti dry run Q2, dan catat flag yang dipakai.
- Tes backend dan fixture perangkat hanya di MySQL efektif inofarm_test dengan TestDatabaseGuard. Jangan menyentuh inofarm_finance_local atau inofarm_local.
- Jangan ubah fitur keuangan kecuali perbaikan bug yang terbukti, dan tanyakan dulu sebelum mengubah perilaku.
- Bedakan hasil fixture HTTP sintetis, backend Laravel nyata dan perangkat fisik di setiap laporan.

Bagian A: tes investasi.
FinanceEntryUiTest#investmentWorkspaceRecreationAndLogoutKeepLatestIncompleteRawInput gagal di emulator yang baru dinyalakan ulang: performTextReplacement pada tag investment-horizon tidak menemukan node di pohon semantik yang digabung ("the unmerged tree contains '1' node"). APK yang sama lulus di rangkaian penuh F5, dan source investasi tidak berubah dibanding sebelum rombak. Cari penyebabnya (urutan state/scroll, keyboard, merge semantik field, atau efek F0 tema / helper logoutFromAccount yang kini performScrollTo), jalankan tes itu sendiri minimal tiga kali sebelum dan sesudah perbaikan, lalu putuskan apakah yang diperbaiki tes atau aplikasi. Jelaskan buktinya.

Bagian B: uji backend nyata untuk rombak pencatatan.
Jalankan backend Laravel nyata (scripts/start-test-backend.ps1 -Port 8001, --env=testing, inofarm_test) dan emulator lewat adb reverse tcp:8001, dengan fixture scripts/finance-device-fixture.php. Perbarui FinanceBackendDeviceTest (atau tambah tes perangkat baru) untuk alur baru: Catat memilih greenhouse saja; form "Catat kegiatan" per GH dengan HSS/HST, pengukuran, nutrisi, penyemprotan dua varietas dengan satu bahan dari daftar dan satu bahan baru dari nama; panen dua varietas dengan jumlah buah kosong; Cek catatan per isian oleh pengelola; pekerja tanpa penugasan melihat semua GH aktif dan ditolak di keuangan. Periksa di database inofarm_test bahwa satu isian menjadi satu baris per bagian dengan entry_batch_id sama, planting_group_id null untuk catatan GH, bahan baru muncul di semua GH, dan fruit_count null. Fixture perlu bahan semprot (jalankan logika SprayMaterialCatalog lewat pembuatan GH atau perintah inofarm:spray-materials pada inofarm_test). Perbaiki juga cleanup fixture yang gagal karena activity_reviews (gap Q2) bila menghalangi.

Akhiri dengan dokumen bukti docs/context/ (apa yang diuji, di mana, hasil, batas), perbarui CLAUDE.md dan GAP_ANALYSIS.md singkat, jalankan scripts/check-finance-context.py, lalu PR untuk ditinjau.
```
