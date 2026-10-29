# Gap analysis baseline 29 Oktober 2026

Requirement aktif berasal proposal keuangan. Tabel berikut adalah gap pada FIN00 sebelum implementasi; [status/evidence terkini](IMPLEMENTATION_STATUS.md) mencatat subset FIN01–06 yang sudah executable. Build default masih legacy, target operasional v2 memakai -PfinanceApp. [Audit rinci](../migration/finance-20261029/AUDIT_COMPARISON.md), [sumber](../migration/finance-20261029/SOURCES.md), [keputusan terbuka](../migration/finance-20261029/DECISIONS.md).

## Coverage target

| Requirement | Existing yang dapat dipakai | Gap utama | Status target |
|---|---|---|---|
| KF01 | Sanctum, hash/password, rate limit, secure session | Worker auth, assignments, finance scope | PARTIAL FOUNDATION |
| KF02 | Worker roster/shift versioning | Akun, GH, season, group, masters | TODO |
| KF03 | Config/phase snapshots IoT | HSS/HST, group target/schedule/stage | TODO |
| KF04–05 | Device event record | Android manual form, multi-material/spray | TODO |
| KF06 | Event history/filter, versioned worker/config | Activity correction/cancel/audit/conflict | PARTIAL FOUNDATION |
| KF07 | Tidak ada domain expense | Payments/allocation/evidence | TODO |
| KF08 | Tidak ada harvest/sale | Grade harvest, paid sales/saldo | TODO |
| KF09 | Tidak ada asset ledger | Depreciation/cap/allocation | TODO |
| KF10–11 | Validator nutrisi bukan evaluator uang | Seasonal formulas + investment | TODO |
| KF12 | CSV history legacy | Financial report snapshot/PDF/CSV | PARTIAL FOUNDATION |
| KF13 | Encrypted read cache; device replay | Account durable outbox/pull/tombstones/conflicts | PARTIAL FOUNDATION |
| KF14 | Tooling DB lokal bukan recovery workflow | Backup+file manifest, restore verify, epoch | TODO |

Tidak ada KF baru dinyatakan complete hanya karena konsep mirip telah ada. KNF01–08 target harus diuji ulang; evidence aplikasi lama bukan evidence performa, uang, recovery atau isolation domain baru.

## Gap prioritas dan tindakan

P0: identity/scope, model multi-GH/musim/kelompok, durable local write, idempotency/versioning, money decimal, locking saldo, destructive migration avoidance. Tutup melalui FIN01–11 sebelum klaim data integrity.
P1: operational form validation, lifecycle, report snapshot/export, private attachment, recovery, per-indicator reason, audit. FIN04–15.
P2: presentation polish dan optimasi setelah form/flow acceptance; jangan menghabiskan scope pada efek visual sebelum data benar.

## SOP dan gate handover

| Sumber | Aturan | Kondisi / tindakan |
|---|---|---|
| G §3/4.2 | Branch per fitur | Branch dokumentasi baru dibuat; implementasi nanti branch masing-masing |
| G §4.3–4.8 | Validate/diff/secrets/sync/push verify | Audit ini belum publikasi; validasi task dokumentasi, jangan mengeklaim remote |
| G §4.9/8 | MR sesuai workflow, CI bila dipakai | Periksa repository/remote saat publikasi; jangan mengarang reviewer/CI sukses |
| S §1/6 | Auth, frontend/backend validation, file limit/private, anti-injection | Fondasi auth legacy ada; semua endpoint target perlu test; pentest sebelum handover |
| S §2 | Responsive, feedback, lintas platform | Target Android 10+ dua layar; pengecualian Windows/macOS/iOS perlu keputusan resmi, Kotlin bukan bukti lintas platform |
| S §3/4 | Layers, API configurable, payload bounded | Struktur target dirancang, implementasi dan limit final belum ada;10KB rekomendasi bukan angka sakral |
| S §5 | Relasi, pagination, CRUD efisien | Master deactivate, transaksi cancellation/audit; konfirmasi CRUD historis dengan pembimbing |
| S §7 | Login/create/read/update/delete/access tests | U01–21 dan regression suite target belum dieksekusi |
| S diagram penyerahan | Dokumen, submit, review/revisi | PIC/deadline/platform/reviewer/UAT terbuka; bukan gate coding independen |

## Ketidakpastian proposal

Penjualan lunas, retur/rusak, dasar alokasi penyusutan, PP/DPP convention, toleransi rasio/IRR/UAT, detail form/completion, upload/retensi/offline/RPO/RTO harus dicatat sebagai pertanyaan atau usulan. Tidak mengisi nilai production dari contoh. Sisa judul IoT dan referensi tabel salah ada di DECISIONS; sumber PDF tidak diedit dalam task ini.

## Verifikasi audit

Dilakukan: ekstraksi60 halaman, review narasi/tabel dan diagram utama, inventaris344 file non-ignored, inspeksi source lintas modul, archive8 file pre-edit dengan hash, penyusunan context/traceability. Pemeriksaan dokumen akhir dicatat di `docs/implementation/VERIFICATION.md`. Tidak menjalankan build/app/SQL/functional tests. Source/migration executable tetap lama, data pengguna tetap tidak disentuh.

## Review implementasi sesudah FIN07/selama FIN08

Status di atas tetap histori audit FIN00; hasil executable ada di IMPLEMENTATION_STATUS dan evidence per fitur. Flush edit terakhir sebelum logout telah diperbaiki/diverifikasi30 Oktober di evidence/20261030-DRAFT_LOGOUT.md. Read konsisten header+child/version serta scope boundary telah diperbaiki/diverifikasi30 Oktober di evidence/20261030-CONSISTENT_READS.md. Registry UUID lintas jenis dan preflight/backfill metadata telah diimplementasikan/diverifikasi pada schema8; lihat evidence/20261030-GLOBAL_IDENTITIES.md. Import/rekonsiliasi data nyata tetap memerlukan keputusan terpisah. Scope restore/epoch reconciliation belum otomatis. Catatan ini bukan izin purge draft atau auto-map legacy. Background scheduling, API29/perangkat fisik, UI end-to-end timings, UAT dan pentest tetap gate terpisah.

## FIN09 backend/native — 30 Oktober 2026

Pembayaran/alokasi eksplisit dan plafon source sudah diuji backend dua proses MySQL, native35unit/48instrumentation serta5 fase nyata offline/process restart/replay. Lihat [status dan evidence](IMPLEMENTATION_STATUS.md). Private proof/file authorization dan limit Q07 masih berikutnya; seluruh FIN09/KF07/U09/U21 belum dinyatakan complete. Selector acuan/source maksimum1000 cache, pagination selector jarak jauh dan timing dataset tetap follow-up. Registry lintas jenis telah ditangani schema8; scope restore/epoch/scheduler/admin/finance selanjutnya/UAT/pentest tetap terbuka.
