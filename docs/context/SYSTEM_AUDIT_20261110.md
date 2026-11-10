# Audit database, aplikasi dan kontrak API, 10 November 2026

## Kesimpulan

Model domain source sudah mengikuti kebutuhan operasional/keuangan proposal dan perluasan pengelola terbaru. **Database aplikasi lokal belum mengikuti source**: masih migration 16, sementara runtime memerlukan 17–19. OpenAPI dapat diarahkan seluruhnya ke route nyata, tetapi belum mencatat seluruh API runtime. Context pack memiliki bukti terbaru, namun ringkasan dan sejumlah rancangan lama masih menyesatkan bila dibaca sebagai kondisi aktif.

Task ini memperbarui dokumentasi dan menambahkan [CLAUDE.md](../../CLAUDE.md). Tidak memperbaiki kode runtime, mengubah schema/isi database aplikasi, menerapkan migration, membersihkan app-data, atau deploy. Pengguna secara eksplisit memilih database lokal read-only dan audit/context terlebih dahulu.

## Baseline dan metode

- Source awal `9a1613e` pada `codex/manager-alignment-acceptance`; branch audit `codex/database-api-context-audit-20261110`. Tracked tree awal bersih. Folder pengguna `UI inspo/`, `assets/`, desain 26 Oktober dan cache Python tetap tidak disentuh.
- Proposal asli 60 halaman dibaca ulang; SHA-256 `e4d7389e2fb1385d43ed507f33d211b7e66f92eae1bade4f15c64c6cf3bfc273`. Teks kebutuhan KF/KNF, alur dan perhitungan dicocokkan; gambar ERD asli halaman PDF 30–31 juga diperiksa. Penomoran PDF berbeda dari nomor halaman tercetak.
- Root/backend AGENTS, SOP GitLab/Pengumpulan, enam dokumen context inti, keputusan/API/test plan dan evidence PG0–PG7 dibandingkan dengan source. SOP Android-only/CRUD historis/pentest tetap gate akademik, bukan alasan mengganti platform/data secara sepihak.
- Laravel configuration efektif dibaca melalui bootstrap tanpa mencetak credential. MySQL awalnya tidak berjalan; instance existing pada datadir `.runtime/mysql/data` dinyalakan tanpa initialize/provision/reset. Pembacaan aplikasi dilakukan dalam transaksi **READ ONLY**, diakhiri rollback. MySQL tetap berjalan setelah audit; tidak ada perubahan konfigurasi `.env`.
- `information_schema` menyediakan tabel, kolom/tipe/nullability/default/key dan FK. Jumlah baris diambil tanpa menyalin isi pribadi. Semua FK diperiksa orphan. Enam pemeriksaan agregat tambahan memeriksa parent scope, batas saldo panen dan keberadaan snapshot/receipt-audit.
- Route matching dijalankan tanpa dispatch controller: setiap method/path OpenAPI dicocokkan router Laravel memakai UUID sintetis. Constraint resource diekspansi untuk perbandingan dua arah. HEAD otomatis tidak dihitung. Controller hasil match ditinjau terhadap resource.
- Android ditelusuri dari MainActivity/FinanceRuntime/FinanceRoot ke repository, OperationRoute, HTTP transport, sync dan store. Review frontend ini berfokus pada alur/data/API dan build; bukan audit visual/TalkBack baru.
- Suite backend dijalankan hanya pada guarded MySQL `inofarm_test`; struktur database uji sesudah migration menjadi pembanding target. JVM/build/lint Android dan checker kontrak/context dijalankan ulang. Tidak ada device fixture yang berjalan bersamaan dengan PHPUnit.

Artefak kerja lokal di `outputs/database-api-audit-20261110/`: inspeksi read-only, metadata schema, pencocokan route, ekstraksi proposal/SOP dan log. Artefak ini ignored dan tidak dipublikasikan. Fakta yang diperlukan untuk handoff disalin tanpa credential/data pribadi ke [inventaris database](DATABASE_INVENTORY_20261110.md) dan [peta API](API_ROUTE_MAP_20261110.md).

## Temuan dan tindakan berikutnya

| ID | Prioritas/status | Bukti dan dampak | Tindak lanjut |
|---|---|---|---|
| DB-01 | **P1, terbuka** | Konfigurasi efektif menuju `inofarm_finance_local` di 127.0.0.1:3307. 21 migration total berhenti finance 16; target 24 total sampai finance 19. Tabel `activity_reviews` dan lima kolom tambahan belum ada. Pembacaan langsung `ManagerHome::snapshot` di transaksi read-only gagal SQLSTATE 42S02/1146. | Rencana rollout additive 17–19 pada database yang benar, backup/preflight dan verifikasi hasil dalam task terpisah. Audit ini tidak melakukan migrate. |
| API-01 | **P2, terbuka** | OpenAPI 94 path/127 operasi; runtime 153 operasi setelah ekspansi, OpenAPI 128 setelah pdf/csv diekspansi. **25 operasi runtime tidak didokumentasikan**, termasuk GET/POST users, PATCH users/{id}, POST users/assignment-candidate, detail/audit master dan versions acuan. | Tambahkan operasi berdasarkan controller/validasi aktual; parity dua arah harus menangkap tambahan route. Android admin sudah memakai beberapa route ini. |
| API-02 | **P2, terbuka** | **34 operasi** memiliki setidaknya satu respons sukses tanpa content/schema (logout dapat memang kosong). Beberapa schema lain masih object generik. `check-finance-contracts.py` lulus karena fokus schema/fixture/local refs, bukan kelengkapan route atau DTO. | Lengkapi schema request/response/error per endpoint; jangan menganggap generator client sudah dapat mewakili seluruh backend. Daftar operasi ada di peta API. |
| API-03 | **P2, terbuka; ketidakkonsistenan semantik** | `ActivityController::latestMeasurement` memilih active/completed/PPM non-null dengan waktu WIB, tanpa mengecualikan review invalid. `ManagerHome::snapshot` sudah mengecualikan keputusan invalid pada revisi saat ini. Dengan record terbaru invalid dan record lama valid, kedua pembacaan bisa memilih record berbeda. | Tetapkan semantik endpoint latest mengikuti keputusan terbaru dan uji invalid/correction/fallback/scope. Android Beranda manager memakai manager-home, sehingga temuan ini bukan klaim kartu manager sekarang memilih invalid. Tidak ada caller `measurements/latest` yang ditemukan pada source Android utama. |
| CTX-01 | **P2, ditangani dokumentasi** | README context menyebut migration16, GAP menyebut pengamatan hanya teks, PRD bagian akhir menyebut minSdk26/flag/API29 belum diuji, backend AGENTS menyebut subset FIN awal. Bukti PG7 sebenarnya sudah ada. DATA_CONTRACTS mencampur nama tabel rancangan dan fisik. | Tambahkan baseline aktif, mapping fisik, role/flow/API dan warning histori; perbaiki teks aktif yang salah. Histori/evidence/arsip tidak ditulis ulang sebagai hasil baru. |
| CTX-02 | **P2, ditangani dokumentasi** | `docs/migration/finance-20261029/API_V2.md` masih berisi rancangan endpoint seperti assets/deactivate, PUT cash-flows, exports jobs dan POST restores yang bukan route aktif. | Tandai sebagai rancangan historis; arahkan ke route map/OpenAPI/controller. Tidak menambahkan endpoint fiktif agar sesuai rancangan lama. |
| FE-01 | **P2, terbuka; risiko existing** | `feature/context/ContextRepository.kt` mengambil maksimum 1.000 per jenis cache untuk selector operasional; list besar dapat tidak menawarkan data di luar batas. Admin listing memiliki pagination sendiri. | Skenario >1.000 untuk pemilih/browsing dan solusi pagination/search sesuai kebutuhan. Ini bukan bukti data lokal sekarang hilang: dataset lokal kecil. |
| QA-01 | **Gate terbuka** | Hasil baru hanya static/read-only MySQL, PHPUnit, JVM/build/lint. Tidak ada rerun perangkat/emulator/TalkBack/pentest/UAT pada audit ini. PG7 memiliki bukti device tersendiri beserta batas. | Teruskan gate perangkat fisik, Doze/background dispatch, latency, akses/demosi semua export, UAT/pentest dan keputusan SOP. |

DB-01 berdampak lebih luas dari inbox: `EntityIdentity::SOURCES` sekarang menyertakan `activity_reviews`, dan pemeriksaan identitas saat create membaca union sumber. Create v2 yang memakai registry dapat gagal pada schema16 walau resource yang dibuat bukan review. Ini kesimpulan source; audit tidak mengirim mutasi percobaan ke database pengguna. `ChangeFeedController` worker dengan `activity_reviews=1` juga merujuk tabel yang belum ada. Jangan mengatasi ini dengan menghapus scope/ledger/fitur verifikasi.

## Kesesuaian database terhadap proposal

Relasi inti di ERD proposal cocok dengan migration: GH → musim → kelompok; kelompok → kegiatan/panen; kegiatan → banyak bahan; panen → banyak rincian; biaya → alokasi → musim; aset → penyusutan → musim; penjualan → rincian → rincian panen. Jadwal bersifat opsional pada kegiatan. Tambahan ledger, identity registry, snapshot dan versi mendukung offline/audit/keuangan, bukan penyimpangan scope.

| Kebutuhan | Source/tabel dan frontend | Bukti pemeriksaan / batas |
|---|---|---|
| KF-01 auth/role/scope | users, greenhouse_assignments, Sanctum; GreenhouseAccess/ScopedRead; AuthRepository | Tes auth/master/revoke dan suite backend lulus; manager tidak memiliki akses GH global |
| KF-02 master/penugasan | greenhouses, seasons, planting_groups, varieties; MasterService/UserController/AdminHost | Route admin tersedia; OpenAPI belum lengkap API-01 |
| KF-03 acuan/umur/stadia | schedules, targets, cultivation_reference_versions; GroupContext/ReferenceService | Referensi snapshot/versioned; umur WIB; stage aktual, bukan dari umur otomatis |
| KF-04 nutrisi/pengukuran | activities/activity_materials/activity_reference_snapshots; ActivityDraft/ActivityService | EC/PPM manual dan pengamatan opsional sudah di source; lokal tertinggal DB-01 |
| KF-05 multi-bahan | activity_materials; formulir kegiatan dan validasi satuan/material | Suite memeriksa bahan/satuan dan field invalid; tidak ada stok otomatis |
| KF-06 koreksi/batal/audit | version/record_state, audit_logs, history; MutationLedger | Alasan/base_version/scope diuji; keputusan manusia tambahan memakai activity_reviews |
| KF-07 biaya/bukti/alokasi | expenses, cost_allocations, record_attachments; FinancialEntry/Attachment | Ledger/saldo/scope/file diuji; konfigurasi produksi upload tetap terbuka |
| KF-08 panen/penjualan | harvests/harvest_items/sales/sale_items; Harvest/Sale | Saldo active sold weight, multigrade dan musim; target/quality/completion perlu migration19 di lokal |
| KF-09 aset/penyusutan | assets/depreciation_allocations; AssetWorkspace | Pembelian vs beban terpisah, plafon kumulatif/tahunan; alokasi manual eksplisit |
| KF-10 evaluasi musim | report_snapshots; SeasonEvaluation/SeasonReportRepository | FC/VC/TC/TR/laba/rasio/BEP, kelengkapan dan denominator terpisah; snapshot immutable |
| KF-11 investasi | investment_scenarios/investment_cash_flow_versions/report_snapshots | Tahunan, actual/projection, NPV/IRR/PP/DPP; tidak menganggap panen=tahun |
| KF-12 ringkasan/ekspor | ManagerHome/ReportExportService; export host | Route reports/{id} memang laporan musim; investment-reports/{id} terpisah, bukan salah navigasi |
| KF-13 offline/sync | sync_state/sync_operations/change_feed, SQLite10/outbox/journal | Epoch, replay/scope, konflik dan account isolation; device evidence PG7 tidak diulang |
| KF-14 backup/restore | backup_runs/restore_runs/RecoveryService, RecoveryHost | Akses operator sistem terpisah; tidak ada HTTP restore apply produksi; Q08 tetap terbuka |

KNF01–08/U01–21 tidak diganti atau dianggap seluruhnya lulus oleh matriks ini. Riwayat pengujian dan acceptance berada di TEST_PLAN/FIN16/PG7.

Perbedaan konsep yang perlu dipahami agent berikutnya:

- `users.id` adalah bigint internal, `public_id` UUID untuk wire; pekerja legacy `workers` bukan akun login v2 otomatis.
- Struktur fisik `record_attachments` dan `investment_cash_flow_versions` berbeda dari nama konseptual `attachments` dan `cash_flows` dalam rancangan awal.
- `activities.observations`, target/completion panen dan snapshot menggunakan JSON karena kumpulan nilai/metadata terversi; bukan kekurangan tabel yang harus langsung dinormalisasi ulang. Audit tidak mengukur performa produksi JSON tersebut.
- Polimorfisme entity ledger/file dan snapshot tidak selalu memakai FK langsung. Source/service bertanggung jawab memeriksa owner/kind/parent. Jangan menganggap tidak ada FK polimorfik sebagai izin melewati validasi service.
- Delapan tabel legacy tetap ada; route/source v1 tidak aktif. Ini sesuai keputusan preservasi histori. Infrastruktur Laravel juga sah meski tidak digambar di ERD proposal.
- Nilai dan relasi lokal yang diperiksa tidak membuktikan kelengkapan keuangan usaha: tabel expenses/sales/assets lokal kosong saat audit. Pengujian keuangan menggunakan fixture sintetis di database uji terpisah.

### Hasil pembacaan database aplikasi

MySQL 8.0.30, `inofarm_finance_local`: **53 tabel, 73 FK, 21 migration total**. Database target uji: **54 tabel, 75 FK, 24 migration total**. Tidak ada perubahan tipe/nullability/default/key pada kolom yang sama; selisih tepat berupa tabel review dan lima kolom migration17–19. Audit belum membandingkan seluruh definisi index/CHECK SQL byte per byte.

73 pemeriksaan orphan FK lokal menghasilkan nol. Enam hitungan pelanggaran berikut juga nol: kelompok-varietas lintas GH, grade panen lintas GH, batas saldo panen, snapshot audit revisi kegiatan, snapshot audit revisi panen, dan receipt tanpa audit pasangan actor/operation/entity. Ini pemeriksaan dataset saat itu, bukan pembuktian semua invariant di semua transaksi. Identitas lengkap via union terbaru tidak dapat diperiksa pada lokal karena tabel review belum ada.

## Frontend → API yang diperiksa

| Alur Android | Pemanggilan/penyimpanan | Hasil |
|---|---|---|
| Login/logout | auth/login → sync/status; auth/logout | Base relatif v2, token/scope; tidak ditemukan route salah |
| Pemilih/riwayat | ContextRepository/HistoryRepository dari cache, sync/changes dan audit endpoint | Perlu membedakan cache lama dengan route salah; batas selector FE-01 |
| Pengiriman kegiatan | OperationRoute → POST sync/operations | Handler activity sesuai entity/action; bukan generic batch dispatcher |
| Pengiriman panen/keuangan | resource create/PATCH/cancel masing-masing | Sesuai route/controller; worker dibatasi server |
| Antrean pengelola | ActivityVerificationRepository → activity-reviews, recorders, verification | Berbeda dari outbox; DB-01 mencegah pembacaan lokal terbaru |
| Beranda/periode | ManagerHomeRepository → manager-home, running-period | Route benar; kegagalan SQL lokal terbukti, bukan navigasi yang salah |
| Rekap/selesai panen | HarvestRepository/OperationRoute → harvest-progress/harvest-completion | Action complete_harvest/reopen_harvest; penutupan musim terpisah |
| Akun/admin | AdminRepository → users, candidate, master/reference | Route tersedia; dokumentasi OpenAPI hilang untuk akun |
| Evaluasi | preview, season-reports, reports/{id}/sources | Path asimetris sesuai kontrak/source; jangan mengganti menjadi season-reports/{id} sembarang |
| Investasi | investment-scenarios, investment-reports/preview/detail/sources | Jalur terpisah dari laporan musim dan cash-flow direct endpoint rancangan lama |
| Lampiran/ekspor | attachments/{id}/content; report-exports dengan expected_snapshot_hash | Akses privat/ukuran/hash; data laporan tetap snapshot |
| Recovery | recovery/* dan sync/reconcile | Metadata/review saja; tidak ada restore apply produksi melalui UI |

Tidak ditemukan salah method/path pada pemanggilan yang ditelusuri. Kesimpulan ini bukan bukti seluruh tombol berhasil diklik di APK baru: pemeriksaan runtime UI terbaru tetap merujuk bukti PG7 dan belum diulang di audit.

## Verifikasi baru

| Pemeriksaan | Hasil |
|---|---|
| Guard database, `scripts/test-backend.ps1` | 4 tes/12 assertion lulus sebelum suite database |
| Suite backend MySQL inofarm_test | **141 tes/2.827 assertion lulus** |
| Android assembleDebug/testDebugUnitTest/lintDebug | **BUILD SUCCESSFUL**, 74 JVM tes, 0 failed/error/skipped |
| Android lint | 0 error, 16 warning, 1 hint; warning bukan ditutup dengan upgrade dependency otomatis |
| Checker kontrak | Lulus schema/fixtures/hash/local refs, termasuk review/finance/recovery |
| Router match | 127/127 operasi OpenAPI match; ekspansi menunjukkan 25 route belum dipublikasikan |
| Context checker sebelum edit | 59 dokumen aktif, 368 link, delapan hash arsip, KF14/KNF8/U21, nol error |
| Context checker setelah edit | 62 dokumen aktif, 402 link, delapan hash arsip tetap cocok;14 tautan CLAUDE diperiksa terpisah, semuanya tersedia |
| Read-only lokal | FK/agregat nol pelanggaran yang diperiksa; **Beranda gagal schema missing** |

Runner Android pertama berhenti karena JAVA_HOME/fallback JDK tidak ditemukan, sebelum build. Pengulangan memakai path JDK25 yang ditemukan melalui `Get-Command java` berhasil. Ekstraksi PDF memakai pypdf bundled setelah Python default tidak memiliki modul; gambar ERD diambil langsung dari embedded image karena renderer fitz tidak tersedia. Tidak ada dependency produk ditambah.

`check-product-boundary.py` diketahui membatasi folder legacy pada filesystem, sehingga cache ignored `firmware/.pio` dapat menimbulkan false positive lingkungan. Keberadaan route/source v1 diperiksa melalui Git/source dan tes `RetiredIotRoutesTest`; tidak menghapus cache pengguna untuk membuat checker hijau. Tidak ada klaim benchmark, pentest, UAT atau restore baru dari task ini.

## Handoff

Mulai agent baru dari [CLAUDE.md](../../CLAUDE.md). Prioritas terdekat adalah DB-01, kemudian kontrak API-01/API-02 dan semantik API-03. Pekerjaan implementasi perlu task tersendiri sesuai permintaan audit-only sekarang. Konfigurasi agronomi, Q07/Q08, biaya bersama lintas GH, completion jadwal, UAT/pentest/perangkat fisik dan pengecualian SOP tetap belum disahkan.

Remote GitHub belum dapat dinyatakan terbaru: dua percobaan fetch, termasuk HTTP/1.1, gagal karena koneksi direset. Ref cache `github/main=bbd712a` bukan verifikasi remote baru. Tidak menyamakan source audit `9a1613e` dengan main/deploy. Dokumentasi disiapkan untuk commit lokal; push ditahan karena fetch/sinkronisasi remote belum berhasil, bukan karena membutuhkan persetujuan ulang.
