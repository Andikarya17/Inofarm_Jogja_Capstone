# Instruksi agen Inofarm: baseline aktif29 Oktober 2026

## Prioritas dan scope terbaru

Pengguna meminta rombak ke **Aplikasi Mobile Pencatatan Operasional dan Evaluasi Keuangan Greenhouse Melon Hidroponik**, proposal `D:/Semester 7/Capstone 2/Proposal/Proposal Aplikasi Pencatatan Keuangan .pdf`. Stack tetap Android native **Kotlin + Jetpack Compose**, **Laravel REST API**, **MySQL**. Context aktif di `docs/context/`; instruksi sebelumnya di `docs/archive/pre-finance-20261029/AGENTS.md` bukan larangan yang mengatasi keputusan baru.

Dua peran pekerja/pengelola, input manual Android, multi-greenhouse/musim/kelompok, offline/sync, panen, keuangan/evaluasi/investasi. Tidak ada IoT/sensor otomatis/ESP32/dosing pada target. Source runnable masih legacy sampai target diuji. Rancangan bukan implementasi.

FIN00 pada29 Oktober menyusun proposal/audit/context/master prompt tanpa mengganti runtime. Pengguna kemudian meminta pelaksanaan MASTER_PROMPT.md; implementasi bertahap aktif, status aktual di docs/context/IMPLEMENTATION_STATUS.md dan kontrak executable di contracts/v2/. Jangan memakai status audit FIN00 sebagai klaim source terbaru belum berubah. Jangan meminta approval ulang untuk tindakan reversible dalam scope terotorisasi.

## Urutan konteks

1. AGENTS root dan lokal.
2. SOP GitLab/Pengumpulan di Aturan Main sesuai pekerjaan.
3. docs/context/README.md, PRD, ARCHITECTURE, DATA_CONTRACTS, IMPLEMENTATION_PLAN, GAP_ANALYSIS.
4. docs/migration/finance-20261029/DECISIONS.md, API_V2, TEST_PLAN, AUDIT_COMPARISON.
5. Proposal terbaru bagian relevan, source/test nyata. API v1 dan evidence lama docs/implementation tetap legacy.

Proposal menentukan kebutuhan; user terbaru mengatasi histori; SOP menentukan akademik; source menentukan implementasi. Proposed technical contracts bukan business approval. Baca bagian relevan, tidak harus seluruh dokumen berulang.

## Git dan perlindungan kerja

Branch deskriptif baru per fitur/bug/config/docs berbeda, prefix codex/. Satu branch per fitur, bukan seluruh rombak. Periksa status/diff/staging; pertahankan kerja orang lain; commit hanya task setelah checks. Otorisasi commit/push per fitur berlaku; instruksi pengguna terbaru29 Oktober memilih **GitHub**, remote `github`, `https://github.com/Andikarya17/Inofarm_Jogja_Capstone.git`, menggantikan tujuan publikasi GitLab sebelumnya. Bukan izin deploy/destructive data/history. Jangan force-push/ganti URL remote tanpa izin. Fetch/sync remote github sebelum push dan verifikasi; PR/CI sesuai workflow aktual. Main belum digabung otomatis.

Arsip pre29 Oktober menyimpan lima context modified sebelumnya. Provenance delapan file/hash ditinjau di docs/migration/finance-20261029/PROVENANCE_REVIEW.md; INDEX.md mempertahankan teks indeks dan README historis dipulihkan byte-identik. Atribut -text menjaga hash arsip lintas checkout. Folder desain26Okt tetap untracked dan dikecualikan dari publikasi karena provenance belum ditinjau. Secrets/env/log/build/runtime/dump tidak di-stage. Jangan reset/stash/delete kerja pengguna demi branch switch.

## Engineering

- Reuse native/backend yang cocok, pisahkan presentation/domain/data; React bukan target produk.
- Auth/role/assignment/parent diperiksa server termasuk file/export/sync; actor dari sesi.
- Durable draft/outbox per akun; local record+operation atomic; server mutation+audit+receipt atomic; version conflict, pull/tombstone dan restore epoch.
- Uang fixed decimal; lock saldo biaya/panen, bedakan payment/allocation/depreciation/harvest/sale/projection.
- HSS/HST kalender WIB mulai1; stage aktual tidak otomatis. Target/dosis/diskonto/harga produksi tidak dari fixture; missing bukan0/valid.
- UI Indonesia, unit/error/loading/empty/offline/last-known, local vs accepted, seluruh alasan; lifecycle/Back/IME/process death diuji.
- Migration additive; tidak auto-convert tank→GH atau event→human activity; legacy data dipertahankan.
- Dependency hanya perlu dan cek dokumentasi resmi/kompatibilitas. Room/WorkManager kandidat, bukan mandat PDF.
- Test backend hanya effective MySQL **inofarm_test**, guard sebelum SQL. Jangan reset/migrate:fresh/seed/restore inofarm_local/data pengguna untuk test.
- Tidak destructive app-data clear/drop/deploy tanpa otorisasi spesifik; production recovery memerlukan plan/confirmation/maintenance/audit/reconciliation.

## Verification dan selesai

Per task requirement→AC→source/test→evidence→context update. Mock bukan bukti runtime DB/device/restore; evidence historis bukan hasil baru. KF01–14, KNF01–08 dan U01–21 dipertahankan kecuali scope formal berubah. Pentest berizin, UAT, SOP Android-only dan historical CRUD adalah gate handover, bukan penghambat kerja independen. Laporkan outcome, file/alasan, tests, batas dan pertanyaan terbuka secara jujur.
