# FIN07 — riwayat, koreksi, pembatalan dan konflik

Implementasi native memakai repository/ViewModel riwayat terpisah. Riwayat lokal memakai filter greenhouse, musim/kelompok, jenis, tanggal kalender WIB inklusif, dan status aktif/batal **sebelum** LIMIT/OFFSET. Urutan waktu kejadian terbaru, tie UUID; draf waktu tidak sah memakai waktu penyimpanan. Halaman awal50; muat berikutnya50. Draf dengan tanggal tidak sah tetap tersimpan, tetapi tidak dianggap cocok dengan filter tanggal sah. Backend `GET /activities` menyediakan filter yang sama, limit1–100 dan cursor; gunakan filter tetap saat melanjutkan cursor. Semua query tetap di dalam assignment server/akun.

`GET /activities/{id}/audit` memeriksa akses ke kegiatan dahulu. Halaman50 default, maksimum100, cursor ID audit terakhir. Actor UUID stabil dari sesi, `actor_name` adalah nama akun saat dibaca. Tiap revisi memuat action/version/reason/server_at dan before/after termasuk snapshot acuan. Native menampilkan perubahan, seluruh alasan acuan, dan cache audit terenkripsi dengan waktu pemeriksaan. Cache adalah last-known ketika jaringan gagal; bukan bukti pemeriksaan terbaru.

Pembatalan hanya pengelola, versi server positif, alasan1–1000 karakter, musim terbuka. Envelope `action=cancel`, payload **hanya `{reason}`**. Hasil server tetap menyimpan record, audit dan tombstone feed; local queued cancel belum berarti batal. Buka/tutup musim juga operasi versioned dengan alasan; dispatch ke `/seasons/{id}/close|reopen`, bukan endpoint activity. Koreksi pekerja tetap hanya catatan sendiri saat musim terbuka; backend memeriksa setiap replay.

## Resolusi eksplisit

- Konflik tidak digabung otomatis. UI membekukan pasangan lokal/server yang diperlihatkan. Pilihan harus cocok dengan **server version dan local revision** yang ditinjau; pull/edit lebih baru meminta tinjauan ulang.
- Gunakan server: simpan salinan draf lokal terenkripsi, tandai operasi konflik SUPERSEDED, gunakan isi server yang ditinjau sebagai last-known accepted. Tidak mengirim mutation baru.
- Pertahankan lokal: alasan dan pilihan preserve/refresh wajib; validasi isian ulang, arsipkan salinan lokal, SUPERSEDED operasi lama, buat **operation UUID baru** berbasis versi server yang ditinjau. ID entity tetap. Backend masih dapat menolak versi berubah lagi. Seluruh isi yang dipertahankan ditampilkan; tidak ada merge uang/field otomatis.
- Penolakan `validation_failed` dapat diperbaiki melalui alur bertanda khusus, review, lalu operasi baru. Envelope/hash operasi ditolak tetap utuh. `operation_conflict`/`entity_exists` memerlukan rekonsiliasi, bukan penggantian otomatis.
- `season_closed`: setelah pengelola membuka kembali dan cache musim diperiksa, pengguna dapat mencoba ulang **operasi asli** dengan UUID/hash/payload sama. Server memeriksa keadaan musim lagi untuk mutation baru. Jika operasi telah committed sebelum musim ditutup/GH dinonaktifkan, exact replay mengembalikan receipt asli setelah authorization/assignment/epoch/hash terkini diperiksa; state closed/inactive tidak menghapus penerimaan historis. Tidak auto-retry penutupan atau mengubah tanggal agar lolos.
- Scope/epoch berubah tetap ditahan. Resolusi epoch pemulihan belum tersedia; alur konflik biasa tidak melewati EPOCH_REVIEW.

## Penyimpanan dan konsistensi

SQLite schema3/4 menambahkan indeks metadata filter dan `local_record_revisions` terenkripsi. Migration1/2/3→4 additive; draft/outbox/cursor tidak dihapus. Detail+pending+arsip dibaca dalam satu transaksi agar tombol konflik dan versi yang ditinjau tidak berasal dari snapshot berbeda. Hasil async tidak dapat membuka kembali detail yang sudah ditutup/diganti. ACK/error terlambat tidak menurunkan versi atau mengaktifkan ulang operasi SUPERSEDED. Sync menarik perubahan lagi setelah batch diterima agar state musim/master mencerminkan hasil penerimaan.

Role guard lokal melindungi cache, record, dan outbox pengelola ketika akun masuk sebagai pekerja; operasi pengelola ditahan, bukan otomatis dikirim saat role dipulihkan. Pembatasan lokal melengkapi authorization backend. Salinan lokal tidak dipurge otomatis; kebijakan retensi Q06 masih terbuka.

FIN07 tidak mencakup attachment, rekonsiliasi restore epoch, editor admin/master lengkap, background scheduler, atau acceptance mitra. Pengujian dan batas bukti: [FIN07 evidence](../../docs/migration/finance-20261029/evidence/FIN07.md).
