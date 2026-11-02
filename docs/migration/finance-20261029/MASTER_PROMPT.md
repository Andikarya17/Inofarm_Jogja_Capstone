# Master prompt implementasi rombak Inofarm

Salin instruksi setelah garis pembatas ke chat implementasi dalam workspace ini. Disusun29 Oktober 2026 berdasarkan audit statis; aplikasi target belum diimplementasikan. Baca file konteks terbaru saat eksekusi.

---

Bertindaklah sebagai senior Android/mobile developer sekaligus engineer backend dan integritas data. Implementasikan rombak Inofarm dari aplikasi owner/IoT menjadi **Aplikasi Mobile Pencatatan Operasional dan Evaluasi Keuangan Greenhouse Melon Hidroponik** sesuai proposal terbaru. Kerjakan frontend, backend, database, API, local persistence, pengujian, tooling dan dokumentasi secara nyata dan bertahap sampai acceptance terpenuhi. Jangan berhenti pada desain UI, rename atau rencana baru.

## 1. Sumber kebenaran dan langkah pertama

Workspace: `D:/Semester 7/Capstone 2/inofarm-jogja---hydroponic-iot-&-nutrition-manager`.
Proposal: `D:/Semester 7/Capstone 2/Proposal/Proposal Aplikasi Pencatatan Keuangan .pdf`,60 halaman; hash baseline `e4d7389e2fb1385d43ed507f33d211b7e66f92eae1bade4f15c64c6cf3bfc273`. Jika berubah, periksa delta kebutuhan.

Baca berurutan:
1. AGENTS.md root dan lokal terkait, termasuk backend/AGENTS.md.
2. docs/context/README.md dan PRD.md.
3. docs/context/ARCHITECTURE.md dan DATA_CONTRACTS.md.
4. docs/context/IMPLEMENTATION_PLAN.md dan GAP_ANALYSIS.md.
5. docs/migration/finance-20261029/SOURCES.md, AUDIT_COMPARISON.md, DECISIONS.md, API_V2.md, TEST_PLAN.md.
6. SOP GitLab/Pengumpulan bagian applicable, proposal bagian terkait, source dan test aktual.

Proposal menentukan kebutuhan, keputusan pengguna terbaru mengatasi histori, SOP menentukan akademik, source menentukan implementasi. API.md di docs/implementation adalah legacy v1. Arsip pre-finance dan diagram21/26 Oktober bukan requirement yang mengatasi PDF terbaru. Aturan lama owner-only, pekerja tidak login, PPM ESP32-only dan keuangan future-scope telah diganti target baru.

Periksa git status/branch/diff, instruksi, struktur source, toolchain dan test environment. Audit sebelumnya menemukan lima context modified dan folder desain26 Oktober untracked; jangan overwrite/reset/delete kerja pengguna. Sampaikan task pertama, perubahan yang dimaksud, AC dan verifikasi. Lanjutkan tindakan reversible yang jelas tanpa approval berulang; pertanyaan bisnis hanya menahan perilaku yang bergantung padanya.

## 2. Git dan keamanan data

Buat dan pindah ke branch codex/ deskriptif baru per fitur/bug/config/docs berbeda. Jangan satu branch untuk seluruh rombak. Jika dependency belum terintegrasi, catat base branch/commit dan gunakan branch baru dari baseline dependency yang valid. Commit hanya task terkait setelah status/diff/staging/validation/secret review. Commit/push mengikuti otorisasi Git yang berlaku; bukan izin deploy, force-push, ganti remote atau destructive history/data. Jangan reset/rebase riwayat bersama tanpa izin.

Backend test wajib effective MySQL **inofarm_test** dengan TestDatabaseGuard sebelum SQL. Jangan disable guard, menggunakan inofarm_local, migrate:fresh/truncate/drop/reset/seed database pengguna atau clear data APK pengguna untuk test. Synthetic fixture dan applicationIdSuffix test terpisah. Migration additive, legacy data tetap sampai ada keputusan import/retensi. Production restore/cutover/deploy memerlukan otorisasi spesifik.

## 3. Stack dan domain wajib

Frontend **Android native Kotlin + Jetpack Compose**, backend **Laravel REST API**, database pusat **MySQL** via backend. Jangan ganti Flutter/React Native/Firebase atau membangun React root sebagai produk. Pertahankan project/package existing bila tidak ada alasan migrasi eksplisit. Tambah dependency hanya untuk kebutuhan; cek dokumentasi resmi dan kompatibilitas sebelum pin versi, tidak upgrade semua karena tren.

Target: akun pekerja dan pengelola; beberapa greenhouse; musim; kelompok tanam dengan varietas/tanggal sendiri; jadwal/target; nutrisi, PPM/pH manual, penyemprotan multi-bahan, stadia, pekerjaan umum, panen per grade; histori/koreksi/batal/audit; offline/sync; pengeluaran/alokasi; aset/penyusutan; penjualan lunas; evaluasi musim/investasi; PDF/CSV; backup/restore.

Tanpa IoT, ESP32, telemetry otomatis, TDS otomatis, sensor otomatis dan dosing. Firmware legacy tidak menjadi dependency critical path target. Tidak tambah piutang, pinjaman, pajak, akuntansi lengkap, bank sync/payment gateway, e-commerce atau stok bahan otomatis.

## 4. Reuse dengan pemisahan tanggung jawab

Reuse Compose navigation/design components, ViewModel/StateFlow, cancellable requests/session generation, endpoint configuration, secure session, OkHttp setelah review. Models NutrientRecord/Telemetry/PlantPhase, OwnerRepository dan satu OwnerViewModel tidak cocok menjadi seluruh domain baru; pecah fitur/presentation/domain/data. Cache baca terenkripsi existing bukan database draft/outbox. Logout/expire legacy menghapus cache; jangan pakai perilaku itu untuk menghapus pekerjaan offline pengguna.

Laravel existing menyediakan Sanctum/hash/rate-limit/test DB guard/versioning/idempotency device. Reuse fondasi, ubah owner/tank scope menjadi role+assignment GH. Workers roster bukan akun login; nama pekerja terjadwal bukan identitas creator. Event device tidak otomatis menjadi aktivitas manual.

Schema existing tanks/devices/events/config_versions/phase_versions/workers/shifts. Tambah migration baru; jangan edit migration yang telah dijalankan untuk mengubah makna histori. Namespace API v2 adalah rancangan transisi, bukan endpoint yang sudah tersedia. Pertahankan API v1 sementara untuk legacy/regression, rencanakan retirement setelah target terbukti.

## 5. Identity, role dan master

Pengelola: akun/assignment/master/GH/season/group, tanggal/jadwal/target, operasional, koreksi/batal/reopen dengan alasan, transaksi, evaluasi/investasi/report/admin. Pekerja: GH penugasan, baca jadwal/target/riwayat operasional, catat kegiatan/panen, koreksi milik sendiri selama musim terbuka. Tidak finance/admin/cancel berwenang pengelola.

Server memeriksa role, assignment, parent relation, season state dan version pada setiap endpoint/list/detail/file/export/sync/pull. UI hide bukan authorization. Actor/creator dari sesi; jangan percaya actor/role/total/saldo payload. Identitas pencatat dan petugas berbeda bila requirement disahkan. Login awal online; revocation/expiry menahan antrean, bukan mengirim sebagai akun lain.

GH→seasons→planting_groups; group mempunyai variety/population/sowing/transplant dates. Season ID stabil meski nama berubah. Master referenced dinonaktifkan, bukan hard-delete cascade. Close/reopen mempunyai reason/audit/version; late queue pada season closed ditinjau, bukan accepted diam-diam.

## 6. Operasional, tanggal, target dan status

HSS=selisih tanggal kegiatan dengan semai+1; HST=selisih tanggal kegiatan dengan pindah+1, kalender Asia/Jakarta. Sebelum pindah HSTnull/belum berlaku; pindah>=semai. Jangan pakai selisih milliseconds/24jam. Kebijakan sebelum semai belum jelas jangan ditebak. Stage aktual dipilih pengguna, tidak otomatis dari umur.

Activity: context, type, occurred_at, creator, petugas sesuai policy, PPM/pH jika relevan, materials quantity/unit, stage/notes, schedule optional, bukti. Spray multi-bahan. Skipped wajib alasan dan tidak dianggap selesai. Satu jadwal bisa banyak realisasi; completion rule tidak otomatis satu record. Actual values tidak diisi dari target.

Frontend/backend validate required per jenis, finite, PPM/jumlah>=0, pH0–14, unit cocok, relasi parent dan tanggal sah. Empty bukan zero. Incomplete bisa draft, tidak evaluation-ready. Agronomic threshold/tolerance berasal pengelola, tidak hardcode.

Target dipilih group/type/occurred_at/effective interval. Missing target/aturan→belum dapat dibandingkan; target angka tunggal tanpa tolerance→delta saja, bukan sesuai/tidak sesuai. Seluruh alasan tampil. Simpan snapshot acuan/tanggal/aturan immutable; perubahan tanggal/target dan re-evaluation historis eksplisit menghasilkan revisi audited.

Pisahkan state kegiatan, completeness, sync, cancellation, upload dan comparison. Draft/batal tidak diagregasi. Saved-local bukan accepted-server; accepted bukan agronomis sesuai atau semua transaksi lengkap.

## 7. Local persistence dan sinkronisasi

Bangun durable local DB untuk draft/cache/outbox terisolasi server+akun. Room/WorkManager kandidat engineering untuk transaksi/migration/retry, evaluasi dulu; framework DI bukan kewajiban. Simpan record+operation dalam transaksi lokal. Entity UUID tetap sepanjang hidup; operation UUID baru untuk create/update/cancel; retry tidak mengganti ID/payload.

Server mutation+audit+receipt+change-feed satu transaksi. Unique actor+operation dan canonical hash; same ID/same content mengembalikan receipt sebelumnya, same ID/different content409. Auth diperiksa pada replay, termasuk sesudah role dicabut. Commit berhasil respons hilang tidak menggandakan payment/sale/activity.

Base_version optimistic concurrency: dua update versi sama tidak last-write-wins. UI membandingkan lokal/server, resolusi mengirim operation baru berdasarkan versi terkini. ACK lama hanya mengubah operasi cocok, tidak menghapus edit baru. 401 reauth akun sama;403/closed-season/422/conflict ditahan untuk review;network/5xx/429 retry sesuai backoff. Queue bisa ditinjau.

Pull paginated stable cursor, version-aware upsert, tombstone/deactivation dan assignment updates. Delayed response akun lama tidak ditampilkan. Logout/offline expiry tidak diam-diam menghapus draft/outbox; isolation dan retention policy eksplisit. Test process kill/restart offline dan migration local DB.

Attachment retry/status terpisah, tidak membuat catatan ganda. Restore epoch berubah menahan antrean lama untuk reconciliation receipts/version; bukan auto-replay atau clear queue. Receipt retention harus mendukung durasi offline yang disepakati.

## 8. Database/transaction keuangan

Gunakan MySQL fixed DECIMAL, Kotlin BigDecimal dan JSON decimal strings. Bekukan currency/precision/scale/range/rounding, intermediate precision dan batas pembulatan line/aggregate. Jangan Float/Double untuk akumulasi uang atau silent overflow/truncate. UI lokal Rupiah dipisahkan dari wire tanpa pemisah ribuan. Total/saldo authoritative backend.

Expenses merekam pembayaran nyata; allocation mengaitkan sumber ke season dengan basis dan FC/VC. Total aktif<=source setelah lock dan validasi ulang. Pemakaian bahan bukan expense kedua. Pembelian aset tidak menjadi biaya operasi penuh sekaligus depresiasi.

Assets: cost A,residual S,life n,in-service date,usage/allocation basis dan link acquisition payment. D=(A−S)/n;n>0;0<=S<=A. Allocation sesuai waktu/proporsi disepakati; equal seasons hanya asumsi eksplisit, bukan default. Cumulative<=A−S; usage sama tidak dibebankan dua kali.

Harvest per grade/count/kg berbeda dari sale. Paid-only model awal; sale bertahap bisa beberapa harvest items satu season. Net active sold<=available harvest. Lock sumber dalam urutan deterministik, cek ulang saldo, atomic create/edit/cancel. Koreksi harvest di bawah sold ditolak sampai transaksi terkait diselesaikan pengelola. Jangan mengarang retur/kredit/kerusakan policy.

Test concurrent allocation/oversell/update/cancel memakai koneksi/proses MySQL independen; sequential/mock saja tidak membuktikan integritas.

## 9. Perhitungan musim

TC=FC+VC termasuk allocated depreciation sekali;TR=sum(q×p) dari paid active sales;L=TR−TC. R/C=TR/TC dan B/C sederhana=L/TC=R/C−1 jika TC>0. Q=kg terjual;P=TR/Q jika Q>0;BEPqty=TC/P jika P>0;BEPprice=TC/Q jika Q>0. Label BEP biaya tercatat dan asumsi total biaya tetap; jangan mengganti dengan FC/(P−variable/unit). Unsold harvest bukan penerimaan; B/C sederhana bukan discounted benefit-cost.

Tiap indikator value/unit/status/reasons/formula/inputs/assumptions sendiri; missing atau pembagi invalid bukan0/NaN/Infinity. Indikator lain tetap dihitung jika sah. Nol data diketahui berbeda dari transaksi yang belum dicatat. Running season provisional; final review biaya/alokasi/sale/pending diketahui tidak membuktikan semua offline devices lengkap. Tampilkan source drill-down dan version.

Fixture musim: FC5m,VC3m,TC8m,Q400kg,P30k,TR12m,L4m,R/C1,5,B/C0,5,BEPqty266,666...,BEPharga20k. Ini synthetic test, bukan production seed.

## 10. Investasi

Scenario terpisah dari evaluasi musim: I0 sekali year0, annual CF1..n, r,horizon,base_date,actual/projection source dan assumptions. NPV=−I0+sum(CFt/(1+r)^t). Horizon integer positif, year1..n tidak duplicate/missing, finite,1+r>0. Tahun interval dari tanggal acuan; depreciation bukan cash out, residual hanya bila assumed realization akhir horizon dan tidak double-count.

IRR model awal conventional flows: initial outflow, future CF>=0 dengan setidaknya satu positif. Solver failure/out-of-scope→unavailable+reason, bukan0%;NPV tetap tersedia jika valid. Algoritme/bracket/iteration/tolerance dibekukan dan diuji.

PP/DPP memerlukan I0>0 dan cumulative cash/discounted cash. Interpolasi=k+sisa investasi/cash recovery year;DPP memakai discounted values. Konvensi interpolasi vs akhir tahun harus eksplisit/disepakati. Tidak recover within horizon status tersendiri; negative flow setelah recovery warning. Rate/horizon produksi tidak dari contoh.

Fixture I0=100m,CF1=CF2=60m,r10%:NPV4.132.231,40495868;IRR13,0662386292%;PP1,66666667;DPP1,91666667(interpolasi). Nominal deviasi≤Rp1; usulan rasio0,0001,IRR0,0001poin persentase,PP/DPP0,0001tahun. Gunakan oracle independen, bukan fungsi sama sebagai expected.

## 11. Snapshot, export, attachment dan recovery

Immutable report snapshot memuat scope/filter/source versions/input/formula version/assumptions/result/completeness/time/actor. UI review/PDF/CSV memakai snapshot sama; source change membuat versi baru. Jangan live recalculation diam-diam saat download. CSV user text dilindungi formula injection. Scope report/file sama dengan source; private file access layer, bukan URL publik.

Upload validate extension/MIME/size/count/name/ownership kedua sisi, safe filenames, configurable documented limits. Status gagal upload berbeda dari record commit. Jangan expose private path/credentials. PDF diuji render/layout, bukan sekadar file ada.

Backup DB+files+manifest+checksums. Restore isolated membandingkan rows/relations/totals/files. Production restore memerlukan authorized plan/confirmation/write-maintenance/audit/verification dan sync epoch/reconcile. Backup dibuat bukan bukti restore. Jadwal/retention/RPO/RTO/operator tetap keputusan pengelola.

## 12. UX dan mutu mobile

Worker: Login→GH/season/group→home jadwal+queue→form kegiatan/panen→review→draft/queue→status→history/correction/conflict. Manager: operasional+master/assignments+expenses/allocation+sales+assets+season evaluation+investment+reports+admin.

Semua KF01–14 tetap scope; pecah navigasi/fitur sesuai kebutuhan. Bahasa Indonesia, field unit/error jelas, loading/empty/error/offline/last-updated. Tampilkan lokal vs server accepted, semua alasan, saldo/source laporan dapat ditelusuri. Tidak tampil fake realtime atau fixture money. Prioritaskan form diuji mitra sebelum polish/efek glass.

ViewModel per fitur, immutable state, repository/use-case, typed enums/domain. Compose tidak akses DB credential atau kalkulasi uang ad hoc. Back stack, draft recreation, process death, IME/insets, font besar/landscape diuji. Target minimum Android 10/API 29 sesuai proposal; code baseline minSdk 26, perubahan konfigurasi direncanakan dan diuji, bukan upgrade dependency massal. Minimal dua screen sizes,1.000synthetic records,≥30timings local save target≤2s; laporkan perangkat/range/failures, build sukses bukan performance pass.

## 13. Pelaksanaan bertahap dan testing

Ikuti FIN01–17: freeze contract→auth/scope→masters→schedules/targets→local/sync→activity→history/conflict→harvest→expenses→sales→assets→season evaluation→investment→export→recovery→integration QA→cutover/docs. FIN00 sudah paket audit; tidak mengulang semuanya tanpa perubahan source.

Vertical slice pertama: auth dua role+dua GH synthetic, authorized context, activity saved local+outbox, scoped server commit/receipt, accepted UI/history, replay/no-duplicate/cross-GH denial. Lanjut seluruh fitur, bukan berhenti setelah login/dashboard. Contract minimum mencakup schema/DTO/error/status/precision/version/epoch dan fixtures cross-layer. Nama endpoints di API_V2 masih usulan, bekukan sebelum implementasi lintas layer.

Per task: branch benar→AC/file scope→implement→focused tests→applicable checks→review diff/security→update context/contracts/verification. Backend unit/domain/feature/access/concurrency hanya inofarm_test. Android assembleDebug/unit/lint dan instrumentation role/form/offline/sync/isolation/lifecycle. Baca scripts build/test existing sebelum menjalankan, verifikasi environment runtime, jangan menebak path.

U01–21 pada TEST_PLAN wajib dipetakan; KNF01–08 ukur dengan evidence. E2E worker offline→sync→manager review→expenses/harvest/sale→evaluation→snapshot/export→staging restore. Old tests bukan evidence target baru. Record command,timestamp WIB,commit/environment,input,expected/actual,pass/fail/not-verifiable,artifact path dan limitations tanpa secrets.

## 14. Migration/cutover dan acceptance

Migration additive/dry-run test, tidak auto-map tank→GH atau event→human activities atau roster→user accounts. Import legacy jika diminta memerlukan mapping eksplisit/provenance/dry-run/reconciliation, tidak membuat musim/tanggal/petugas palsu. Demo history tidak menjadi transaksi aktual.

Sebelum cutover: backup+restore validated, account/storage/schema mapping approved, app/API compatibility matrix, write freeze/reconciliation dan rollback yang menjaga new transactions. Jangan drop legacy/clear user app/deploy tanpa izin terkait. Setelah parity/acceptance, keluarkan sensor polling/device route/firmware builds dari jalur produk dan update scripts/install/package/docs.

Done per fitur: AC terpenuhi, checks pass, scope/validation/audit/retry benar, UI states lengkap, docs dan evidence diperbarui, diff relevan. Done produk: seluruh KF, critical integrity/calculation/recovery/security, UAT mitra dan gate akademik. Usulan UAT≥90% tugas tanpa bantuan, semua critical calculations pass dan nol critical issue perlu konfirmasi; laporkan per role/task dan denominator.

Pentest berizin sebelum handover. Pengecualian Android-only terhadap SOP Windows/macOS/iOS dan cancellation historis sebagai CRUD dikonfirmasi pembimbing. Kotlin/build di desktop bukan bukti app lintas platform. Source PDF masih punya sisa teks IoT/nomor tabel salah; ikuti scope eksplisit tanpa IoT, catat editorial gaps, jangan menghidupkan sensor kembali.

## 15. Keputusan terbuka dan kontinuitas

Baca Q01–Q13: paid-only/return, cost/depreciation allocation, PP/DPP convention, required forms/unit/agronomy/schedule completion, performer vs creator, offline/logout/receipt retention, upload limits, RPO/RTO, legacy import, precision/rounding/solver, UAT tolerances, academic exceptions, dates/PIC. Tanya ketika diperlukan, kerjakan bagian independen. Technical reversible choice berbeda dari business rule; usulan tetap dilabeli usulan.

Setiap update akhir laporkan task/branch, outcome nyata, files/reason, API/schema delta, tests+evidence, AC status, limitation/decision dan next step. Jika context habis/terhenti, simpan progress agar dapat dilanjutkan tanpa klaim DONE palsu. Jangan mengurangi scope diam-diam untuk cepat selesai.

Mulai sekarang dari kondisi aktual dan FIN01, lanjutkan implementasi bertahap sesuai dependensi sampai scope yang dapat dikerjakan selesai, sambil menjaga data dan kerja pengguna.
