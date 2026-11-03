# FIN17 — cutover, rollback dan gate handover

2 November2026. **Dokumen/prosedur/tooling siap ditinjau; produksi belum boleh dinyatakan cutover/accepted.** Instruksi user mengotorisasi implementasi/commit/push, bukan deploy, production restore, drop data/tabel legacy atau clear app-data. Baseline FIN11–15 dan hasil engineering FIN16 pada [matriks acceptance](FIN16_ACCEPTANCE.md); [instalasi/run](FIN17_INSTALL_RUN.md).

## Gate sebelum eksekusi

| Gate | Keadaan sekarang | Bukti/keputusan yang masih diperlukan |
|---|---|---|
| Kode/kontrak |FIN01–15 core +FIN16 perbaikan tersedia di branch bertumpuk; schema15/SQLite9 |Review/merge terkoordinasi, release candidate hash dan kompatibilitas |
| U01–21/KNF01–08 |Engineering lokal,134backend+focused security/integration,77 JVM,66device+8follow-up,storage30/1000 |Full target-device E2E, job dispatch/Doze/reboot, waktu interaksi UI |
| UAT mitra |Belum dilakukan/disahkan pada lanjutan ini |Peserta dua peran, perangkat, task/denominator/bantuan/durasi, hasil dan tanda penerimaan;≥90% tanpa bantuan masih usulan |
| Pentest |Security regression dan dependency audit0; bukan pentest |Scope/host tertulis, izin, PIC/jendela, findings/remediation/retest |
| Upload Q07 |Default konfigurasi produksi kosong |Batas bytes/count/type dan persetujuan operator; bukan fixture5MB/5file |
| Recovery Q08 |Actual isolated restore FIN15 historis; produksi belum dikonfigurasi |Operator/allowlist,storage protection/key escrow,retensi/jadwal,RPO/RTO kapasitas nyata |
| Bisnis/Q01–13 |Q02 manual depreciation dan Q10 decimal disepakati; detail lain di DECISIONS |Asumsi actual/projection/PP-DPP/toleransi/form/petugas/offline/legacy sesuai register |
| SOP akademik |SOP Pengumpulan v2 dibaca; target tetap Android-only sesuai user |Pembimbing mengonfirmasi pengecualian Windows/macOS/iOS dan cancel/deactivate sebagai historical CRUD; lampiran evidence |
| Akun/data/migrasi |Tidak auto-convert legacy |Mapping/account/GH/scope/retensi yang ditandatangani, dry-run/reconciliation |
| Rilis/operasi |Finance default debug/profile `.finance`;source v1/IoT dilepas, data historis tetap |PIC/tanggal/persetujuan deploy,HTTPS/signing/storage/monitoring/rollback rehearsed |

Tidak ada gate yang dianggap PASS karena tombol backup bekerja atau build sukses. Isi PIC, tanggal dan dokumen persetujuan hanya setelah tersedia; tidak ada approval fiktif. Audit0 adalah hasil dependency bertanggal, bukan klaim bebas semua kerentanan.

## Compatibility dan data

| Komponen | Mode review sekarang | Cutover |
|---|---|---|
| Android target |Default tanpa flag,debug com.inofarm.jogja.finance,min29,target37;SQLite9 |Signing/application identity/distribution/version strategy disetujui sebelum release |
| Android legacy |Source dilepas; data package lama dipertahankan |Tidak auto-import/clear; release identity/signing/data review sebelum distribusi |
| Backend v2 |Laravel13.35/PHP8.5.11 yang diuji,MySQL8.0.30,schema15 |Deploy additive dengan backup yang dapat dipulihkan dan matrix app/API reviewed |
| Backend v1/firmware |Source/route/tooling dilepas atas instruksi pengguna2 November2026 |Deploy masih gate terpisah; tabel/migration/histori tidak dihapus |
| Sync |Schema wire2,UUID global,immutable op hash,epoch |Restore menahan antrean lama; tidak rebase/replay otomatis |

Tank bukan greenhouse, event perangkat bukan aktivitas manusia, roster bukan akun, dan demo bukan transaksi nyata. Import opsional harus mempunyai mapping/provenance/baris gagal/dry-run serta reconciliation totals; nilai/tanggal/petugas tidak ditebak. DDL1–14 beku;schema15 additive dan map schema8 yang sudah dipublikasikan tidak diubah. SQLite upgrade additive mempertahankan drafts/operations/archives. Backup packages tidak menyertakan APP_KEY; akses kunci dan storage merupakan rencana operator terpisah.

## Urutan cutover setelah semua gate terpenuhi

1. Tetapkan release commit/APK hash/server artifact, PIC/jendela, scope instance/DB/file roots dan cara menghubungi operator. Verifikasi target sesungguhnya dan hak akses, jangan memakai credential fixture. Tinjau HTTPS, debug=false,private storage,allowlist/upload limits/secret/signing melalui kanal privat.
2. Beri tahu pengguna dan tentukan batas penulisan untuk **semua writer**, termasuk instance v1 lama yang belum di-upgrade, worker/scheduler. Ambil inventaris device/draft/outbox diketahui; perangkat offline yang belum mengonfirmasi tetap unresolved, bukan dianggap kosong.
3. Freeze writes sesuai plan. Ambil DB+file backup/manifest/checksum, identitas epoch/source/schema dan key escrow yang disetujui. Uji restore terpisah terhadap titik itu: rows/FK/identities/decimal totals/snapshots/files/next IDs. Kegagalan mengakhiri cutover sebelum perubahan produksi.
4. Preflight identity registry/parent/scope/DDL dengan dry-run pada clone staging. Jalankan hanya migration additive yang telah ditinjau pada target yang diotorisasi. Rekonsiliasi lagi; tidak memakai migrate:fresh/seed atau auto-conversion legacy.
5. Deploy versi kompatibel dan smoke-test dua akun berizin melalui HTTPS:manual→harvest→payment/allocation→sale→evaluation→frozen export;worker cross-scope/file denial;logout/relogin/current profile;hold/reconcile epoch. Jangan memasukkan transaksi sintetis ke pembukuan usaha; gunakan prosedur staging atau data uji yang ditandai/dibatalkan diaudit sesuai plan.
6. Tinjau semua hold/conflict/late operations, bytes/checksum private files dan sumber laporan. Buka writes setelah pemeriksaan ditandatangani; catat epoch/versi/waktu/hash dan monitor auth/queue/error/source balance. Hentikan kembali jika critical integrity mismatch.
7. Handover mencakup install/API/contracts,hasil UAT/pentest/restore,daftar keputusan terbuka/known limits,PIC backup/monitoring dan prosedur recovery. Publikasi GitHub sesuai instruksi user berbeda dari pengesahan platform pengumpulan kampus; konfirmasi pembimbing bila kampus tetap mensyaratkan GitLab UMY.

## Rollback yang mempertahankan transaksi baru

Pemicu: incompatible API/app,saldo/row/hash mismatch,private-file failure,akses lintas scope,critical security issue atau migration gagal. Freeze **semua writes** lagi dan simpan audit/DB+files+outbox/receipt ledger dari keadaan sesudah cutover sebelum memilih rollback. Jangan menghapus data baru atau menjalankan migration down/drop/clear APK untuk kembali ke versi lama.

- Jika code rollback kompatibel dengan schema additive dan record baru, gunakan artifact kode sebelumnya yang ditinjau; pertahankan schema/rows/files/identities/receipts dan epoch yang masih valid. Uji read/write dengan versi lama di staging dahulu. Versi lama yang belum mengenal entity baru tidak boleh menghapusnya.
- Jika diperlukan data restore, terapkan rencana **production recovery terotorisasi tersendiri** dengan jendela,operator,key/storage,maintenance dan audit. CLI `inofarm:restore-isolated` sengaja tidak melayani target produksi. Pertahankan salinan keadaan sesudahcutover, petakan transaksi sesudah titikbackup, pulihkan ke target terpisah, dan rekonsiliasi sebelum promotion. Jangan menimpa database berjalan.
- Setelah restore yang sah: revoke sesi sesuai plan,putar epoch,login akun sama,hold antrean,cocokkan receipt/hash/versi/sumber. Missing receipt memerlukan review manual; tidak re-ID/replay/rebase otomatis. Pastikan raw edits/salinan berbeda tetap ada. Buka writes hanya setelah discrepancy selesai atau diterima eksplisit dan terdokumentasi.

## Form pencatatan penerimaan

Catat release hash/branch,instance terotorisasi,operator/PIC,reviewer/pembimbing,tanggal WIB,perangkat/peran,task berhasil/total/bantuan,durasi,critical issues dan evidence privat yang aman. Isi keputusan **diterima/ditolak/ditunda** dengan alasan dan tindak lanjut. Semua kolom itu belum diisi oleh agen karena mitra/PIC belum memberi hasil; tidak menghalangi implementasi reversible yang telah dipublikasikan.
