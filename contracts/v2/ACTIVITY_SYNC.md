# Aktivitas dan sync server — FIN05 server slice

Available: GET/POST activities, GET/PATCH activities/{id}, POST activities/{id}/cancel, GET activities/{id}/audit; POST sync/operations memakai service identik (saat ini entity_type activity saja); GET sync/changes; GET sync/status. Namespace `/api/v2`. Schema formal operasi diperbarui untuk create/update/cancel. Tidak ada import event legacy.

Payload create sesuai fixture; nutrition ready membutuhkan PPM/pH manual dan >=1 bahan; spray >=1 bahan; stage actual_stage; general notes nonblank. Skipped membutuhkan skipped_reason dan tidak berarti completed. Empty numeric null, bukan0; bila diisi PPM/quantity>=0, pH0–14, string decimal(20,6), finite, unit/material GH+dimension cocok. Maksimal30 material/request16KiB. Ini baseline struktural dari proposal, validasi agronomi/dosis/target/performer tetap konfigurasi/keputusan mitra. Tidak menerima performed_by/actor/role dari client. Draft incomplete tetap lokal, bukan accepted/evaluation-ready server.

Update payload aktivitas lengkap + reason + reference_policy preserve|refresh; parent group tidak boleh pindah. Pekerja hanya koreksi sendiri pada musim open; manager koreksi semua scoped, cancel hanya manager. Cancel payload hanya reason. Semua mutation base_version dan operation identity, closed season409, stale version409 dengan current_version/current_data authorized, revoked scope404. Tabel referensi aktivitas menyimpan immutable snapshot per versi; audit menyimpan before/after termasuk material set. Update notes/actual dengan preserve memakai tanggal/target beku sambil menghitung checks actual baru. Mengubah waktu/jenis memerlukan refresh eksplisit. Refresh mengambil tanggal/target terkini dengan alasan; snapshot lama tetap utuh.

Materi/satuan inaktif dapat tetap dipakai pada koreksi reference yang sudah ada; referensi baru harus aktif. Jadwal optional; harus sama group/type, tidak otomatis completed setelah satu aktivitas. Kegiatan sebelum semai ditahan422 dengan alasan sampai Q05 jelas. Actual stage tidak diturunkan umur. Belum ada attachment.

Receipt memeriksa authorization dan token di dalam lock metadata sebelum replay. Retry create lintas normal/sync route menghasilkan receipt awal (status201), tidak duplicate. Semua aksi satu transaction mutation+snapshot+audit+feed+receipt; fault setelah SQL receipt harus rollback semuanya. Global metadata lock juga memberi sequence commit-order, tidak hanya auto-increment order.

Pull memakai cursor bertanda tangan yang terikat actor dan epoch. Selama cakupan tetap, batas atas sequence tetap sepanjang rangkaian halaman. Perubahan setelah halaman pertama masuk siklus berikutnya. Cursor final digunakan untuk polling. Cursor yang mendahului perubahan role atau himpunan greenhouse efektif memulai pembacaan ulang dari awal dalam cakupan baru, termasuk saat assignment menjadi efektif tanpa mutasi baru. Cursor lama tanpa fingerprint cakupan juga memulai pembacaan ulang satu kali. Upsert berdasarkan versi mencegah duplikasi.

Scope terkini menyertakan greenhouse nonaktif untuk histori. Klien menyembunyikan cache greenhouse yang aksesnya dicabut dan mempertahankan draf. Pekerja tidak menerima audience pengelola. Tombstone mencatat nonaktif atau pembatalan. Epoch restore berbeda menahan antrean dengan 409. Batas halaman 50 secara default dan maksimal 100, dengan paling banyak 1000 greenhouse per cakupan.

History paginated order occurred_at/id descending dengan cursor Laravel; filters group/type/from/until kalender WIB; batal tetap dapat ditelusuri. Audit cursor integer default50/max100. Receipt/draft retention tidak dipurge otomatis. Status accepted menunjukkan penerimaan server, bukan kesesuaian agronomi atau kelengkapan transaksi.

Uji concurrency tambahan `php scripts/test-finance-concurrency.php` memakai dua proses/koneksi MySQL inofarm_test dan file barrier; membersihkan hanya fixture UUID/actor miliknya. Tidak membuka SQL sebelum TestDatabaseGuard. Tidak menjalankan script terhadap database pengguna.

## Pengamatan lapangan opsional

Payload aktivitas menerima `observations` dengan DO `dissolved_oxygen_mg_l`, suhu air `water_temperature_c`, tinggi `plant_height_cm`, lebar `leaf_width_cm` (desimal string), `leaf_count` (integer helai), dan `sample_basis` (teks maksimum 500 karakter). Kosong tidak menghasilkan nol. Pada koreksi, field observations yang tidak dikirim mempertahankan nilai lama. Kirim null untuk mengosongkan. Snapshot baru merekam versi jadwal yang ditautkan saat dipilih atau di-refresh secara eksplisit.
