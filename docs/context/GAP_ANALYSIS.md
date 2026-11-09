# Gap analysis baseline 29 Oktober 2026

## Gap pencatatan pekerja dan administrasi — 9 November 2026

Konfigurasi awal dua akun lokal tidak memiliki penugasan. Perbaikan branch menangani onboarding pekerja tanpa penugasan, kandidat dalam cakupan, replay cakupan untuk cursor, navigasi form pekerja, dan edit email akun. DO, suhu air, tinggi tanaman, jumlah dan lebar daun dicatat dalam keterangan pekerjaan dengan satuan. Bukti per acceptance dan kombinasi perangkat yang masih terbuka tercatat dalam [matriks WR](WORKER_RECORDING_RECOVERY_20261109.md).

## Gap aktif setelah upgrade Beranda, 8 November 2026

Frontend operasional, botani, menu sekunder dan perbaikan Run Android Studio sudah diintegrasikan sampai main `0ea7f66`. Beranda pengelola tersedia di `d6774fc`, belum masuk main pada fetch audit. Backend migration16 dan SQLite10 tersedia di branch tersebut. Bukan backlog implementasi dari nol. [Handoff](HANDOFF_20261108.md), [evidence Beranda](MANAGER_HOME_UPGRADE_EVIDENCE.md).

Prioritas bukti yang belum lengkap:

1. MH-10: ulang kombinasi demosi peran dengan seluruh jalur unduh/ekspor end-to-end; tes scope/revoke yang ada belum menutup kombinasi itu.
2. MH-12: urutan fokus dan pengumuman TalkBack seluruh kontrol, serta pengukuran performa pada perangkat sasaran. Sampel debug TalkBack 26/29 frame janky bukan hasil benchmark fisik.
3. UAT mitra, pentest berizin, perangkat fisik, OS background dispatch/Doze/reboot, end-to-end UI latency dan risiko selector operasional cache1000 masih perlu bukti sesuai scope masing-masing.
4. Q07/Q08 konfigurasi produksi, asumsi bisnis terbuka, SOP Android-only dan CRUD historis tetap menunggu keputusan yang sesuai. Biaya bersama lintas greenhouse serta aturan completion/tenggat jadwal belum dimodelkan sebagai fitur baru.
5. Integrasi Beranda ke main memerlukan review tersendiri; tidak ada merge/deploy pada audit ini.

Temuan lingkungan: `check-product-boundary.py` gagal pada `firmware/.pio/` ignored, sisa build lama yang tidak dilacak Git. Jangan menyimpulkan source IoT aktif kembali atau menghapus artefak pengguna tanpa kebutuhan. Akun demo/server terakhir di chat belum diverifikasi ulang. Bagian bertanggal lebih lama di bawah adalah riwayat perkembangan gap, bukan semua masih terbuka.

Gap terbaru3 November2026 telah ditutup secara engineering lokal untuk EC/PPM manual, fallback dashboard WIB, finance default dan pelepasan source/runtime/tooling IoT. Tabel/histori tetap dipertahankan. Integrasi API29→Laravel/MySQL8fase, UI39 dan readiness device72 PASS. UAT/pentest, perangkat fisik, dispatch OS background dan produksi tetap terbuka. [Evidence hasil dan batas](../migration/finance-20261029/evidence/20261102-IOT-RETIREMENT.md). Bagian sebelumnya di bawah adalah histori. Risiko selector operasional cache1000 tetap terpisah; pemilihan pengukuran terbaru membaca seluruh cache scoped.

## Lanjutan FIN16–17 — 2 November 2026

Gap editor native akun/master/assignment/acuan, background registration, current profile dan advisory MIME telah ditangani; [hasil FIN16](../migration/finance-20261029/FIN16_ACCEPTANCE.md). Masih terbuka: actual background dispatch/Doze/reboot, full real-device E2E terbaru/API29/fisik, UAT/pentest berizin, end-to-end UI latency, selector operasional cache1000, Q07/Q08 dan SOP akademik. FIN17 install/package/cutover/rollback tersedia, bukan pelaksanaan produksi. Tabel FIN00 di bawah tetap audit historis.

## FIN15 — 2 November 2026

Gap FIN15 backup+private files/signed manifest, staged restore verification, epoch hold/receipt review dan admin feedback ditutup lokal dengan132 backend/2127 assertions,33 device serta actual separate MySQL restore/reboot/root UI PASS. Copy lokal yang berbeda tetap ditahan atau menjadi LOCAL setelah explicit historical acknowledgement, tidak dianggap recovered/auto-replayed. Q08 jadwal/retensi/RPO/RTO/operator/key escrow dan produksi, physical-device/network/UAT/pentest/reviewer tetap terbuka; dataset kecil bukan production RTO/throughput. Satu tautan screenshot legacy hilang dan advisori symfony/mime lama tetap tercatat. [Kontrak](../../contracts/v2/RECOVERY.md), [evidence/batas](../migration/finance-20261029/evidence/FIN15.md). Commit lokal per fitur; push tetap ditunda pengguna.

## FIN14 — 2 November 2026

Gap FIN14 unduhan privat/immutable PDF-CSV, CSV formula injection, destination/cancel/error feedback ditutup lokal dengan backend7/119,72 JVM/lint0,5 device dan real API29+SQL reconciliation. Jalur NAT emulator10.0.2.2 intermittently memutus byte PDF; guard menolak berkas tidak lengkap. Acceptance nyata memakai sambungan ADB reverse; tidak diklaim bahwa NAT sudah pulih. Pre-existing symfony/mime CVE-2026-45070, satu tautan gambar dokumentasi legacy yang hilang, UAT/pentest/reviewer/perangkat fisik tetap terbuka. [Evidence/batas](../migration/finance-20261029/evidence/FIN14-backend.md), [kontrak](../../contracts/v2/REPORT_EXPORT.md). Commit lokal per fitur; push ditunda pengguna.

## Perbaikan histori FIN13 — 2 November 2026

Cancelled-only fresh-cache history kini dipertahankan dengan typed hydration/current scope; raw draft tidak ditimpa. APK/unit66 dan device17PASS (10 investasi +7 evaluasi musim). [Evidence](../migration/finance-20261029/evidence/20261102-CANCELLED_SCENARIO_HISTORY.md). Gap terkait ditutup lokal. Berikutnya FIN14; push ditunda pengguna, FIN15–17 di luar scope.


## Lanjutan FIN13 — 2 November 2026

Gap backend investasi ditutup lokal dengan119/1906 regression,259 independent numeric oracle, typed contracts dan dua real process races. Native/build/lifecycle/reboot masih terbuka. Gate UAT/pentest/fisik/recovery/reviewer tidak diklaim selesai. [Evidence dan batas verifikasi](../migration/finance-20261029/evidence/FIN13-backend.md).


Native66 JVM/lint0,8 Android cases dan3 fase real HTTP/MySQL dengan dua OS reboot + SQL reconciliation PASS. [Evidence/batas native](../migration/finance-20261029/evidence/FIN13-native.md). FIN14 ekspor belum dimulai; checkpoint ini dipublikasikan atas instruksi terbaru pengguna.

Follow-up FIN13: `hydrateReportCache` masih menyaring tombstone cache; scenario cancelled-only pada profile baru dapat tidak muncul di daftar lokal. Backend history/report utuh; tambahkan typed hydration dan fixture fresh profile. Root navigation/recreation/logout focus1PASS.

## Lanjutan FIN12 — 2 November 2026

Native critical acceptance: JVM59/lint0,7 clean device cases and real API29 offline→two OS reboots→immutable reports/source correction/worker403+SQL reconciliation PASS. Broader cross-screen popup/touch regression remains open. [Native evidence](../migration/finance-20261029/evidence/FIN12-native.md).

Gap backend evaluasi musim ditutup oleh schema12, source reconciliation, exact math/oracle, role/scope/replay/atomicity/identity/cap tests. Native acceptance FIN12 masih terbuka, termasuk verifikasi ulang scope quarantine dan real API/reboot; gate global admin/reference/background/KNF/API29/fisik/UAT/pentest/reviewer tetap belum selesai. [Evidence backend dan batas acceptance](../migration/finance-20261029/evidence/FIN12-backend.md). Regresi backend terbaru108 PASS/1755 assertions, dua race MySQL independen dan1024 oracle independen PASS; native acceptance masih berjalan.


## Lanjutan FIN11 — 30 Oktober 2026

FIN11 backend/schema11 dan native aset/penyusutan manual kini terverifikasi lokal: sumber/form/review, raw draft/autosave/Back/ON_STOP/logout, riwayat/audit/konflik/pembatalan, SQLite8 additive, dan tiga fase API nyata dengan dua reboot Android (boot3→4→5) serta SQL reconciliation PASS. Lihat [backend/math](../migration/finance-20261029/evidence/FIN11-backend.md), [native core](../migration/finance-20261029/evidence/FIN11-native-core.md), dan [native entry/acceptance](../migration/finance-20261029/evidence/FIN11-native-entry.md). Regresi69 mencatat66 PASS dan3 kegagalan yang ditindaklanjuti dengan fokus API36.1; tidak diklaim satu run69 bersih. JVM53/lint0errors; FIN12–17 dan gate produk tetap terbuka. Salinan proposal telah diterima dan hash/60 halaman cocok baseline; [verifikasi sumber](../migration/finance-20261029/evidence/20261030-PROPOSAL_RECEIVED.md). Nilai produksi tetap kosong; akses push ditunda pengguna.


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

Pembayaran/alokasi eksplisit dan plafon source sudah diuji backend dua proses MySQL, native35unit/48instrumentation serta5 fase nyata offline/process restart/replay. Backend lampiran privat menambah schema9, scoped upload/download, parent-version review dan cap concurrent; native core SQLite6 menyediakan encrypted copy/draft, bound multipart outbox dan download. File UI SQLite7 telah diuji termasuk raw alasan, private preview dan offline review/queue; system picker/physical/UAT dan fresh profile integration masih gate, Q07 konfigurasi produksi belum ditetapkan. Lihat [status dan evidence](IMPLEMENTATION_STATUS.md); seluruh FIN09/KF07/U09/U21 belum dinyatakan complete. Selector acuan/source maksimum1000 cache, pagination selector jarak jauh dan timing dataset tetap follow-up. Registry lintas jenis telah ditangani schema8; scope restore/epoch/scheduler/admin/finance selanjutnya/UAT/pentest tetap terbuka.
