# Acceptance dan verification plan

Saat FIN00 semua skenario **NOT RUN**; hasil pelaksanaan baru sekarang tercatat di [status/evidence](../../context/IMPLEMENTATION_STATUS.md), bukan diubah menjadi PASS seluruh U01–21. Sumber P §3.4 PDF55–57. Backend hanya `inofarm_test`; fixture sintetis, APK instrumentation terpisah. Test guard sebelum SQL. Mock bukan bukti transaksi MySQL concurrency, process death nyata, restore atau device usability.

| ID | Skenario dan assertion | Layer / task |
|---|---|---|
| U-01 | Login valid/invalid, logout, expiry; pesan aman; antrean tidak pindah pengguna | API + Compose + store /02,05 |
| U-02 | Worker direct finance/GH lain/detail/file/export/sync denied | Policy/API /02,09,14 |
| U-03 | CRUD/deactivate master referenced; FK/history utuh; parent salah ditolak | MySQL/API/UI /03 |
| U-04 | Empty vs zero, negative/nonfinite/overflow, wrong units, conditional required; draft tidak masuk laporan | Domain/API/form /06,08 |
| U-05 | Hari semai/pindah=1; sebelum pindah HSTnull; beda kelompok; WIB midnight; pindah<semai ditolak | Domain fixtures /04 |
| U-06 | Target absent/changed/single/no comparator; actual unchanged; snapshot preserved | Domain/API/UI /04,06 |
| U-07 | Own/other correction, cancel reason, closed season, late queue; reopen audited | API/UI /07 |
| U-08 | Dua update base_version sama; satu accepted satu conflict; local draft utuh | Parallel DB/API+UI /07 |
| U-09 | Expense split musim, concurrent over-allocation; asset payment tidak dihitung ulang biaya operasi | MySQL /09,11 |
| U-10 | Partial/multi-harvest sale satu musim; oversell concurrent; koreksi harvest below sold rejected; cancellation saldo benar | MySQL/API /08,10 |
| U-11 | Residual>cost/life0/negative invalid; cumulative cap; usage tidak double-charge | Domain+MySQL /11 |
| U-12 | Fixture musim Tabel2.8, TC0/Q0/P0/missing per indikator; reasons benar; indikator lain tersedia | Domain+API /12 |
| U-13 | Fixture investasi Tabel2.9; NPV/IRR/PP/DPP sesuai tolerance, oracle independen | Domain /13 |
| U-14 | Duplicate/missing years, r<=-1, invalid horizon/nonfinite, nonconventional CF, no payback, solver fail; no fake0 | Domain/API/UI /13 |
| U-15 | Review snapshot→edit source→export snapshot lama; PDF/CSV source/version/filter/results tetap sama | Integration+render /14 |
| U-16 | Offline create/edit, force process stop, restart offline, reconnect; record/outbox survive transactionally | Device+API /05,06 |
| U-17 | Commit lalu response dropped; retry tidak duplicate; ID sama isi beda conflict | Fault injection+DB /05,10 |
| U-18 | Revoke sesi/assignment, update masters, dua akun, delayed old response; scope/cache/queue isolated | API+device /02,05,07 |
| U-19 | Restore DB+files isolated; checksum/relations/counts cocok; old epoch queue held/reconciled | Recovery drill /15 |
| U-20 | Dua layar, font/IME/landscape, loading/empty/error/offline;1.000records dan≥30timings local save≤2s | Device/performance /06,16 |
| U-21 | Unsafe extension/MIME/size/count/name, unauthorized file/export, brute force, injection | Security/pentest berizin /09,14,16 |

Tambahan: crash antara local row/outbox; crash mutation/audit/receipt; pull pagination sambil update/tombstone; revoked assignment; upload retry tanpa duplicate; rounding multiline sale; restore missing file/checksum mismatch; upgrade storage mempertahankan draft. Race tests memakai koneksi/proses independen dan barrier, bukan request sequential.

## Oracle numerik

Musim: FC5m+VC3m=TC8m;400kg×30k=TR12m; L4m; R/C1,5; B/C0,5; BEPqty266,666...; BEPharga20k. Investasi: I0=100m, CF1=CF2=60m, r10%; NPV4.132.231,40495868; IRR13,0662386292%; PP1,66666667; DPP1,91666667(interpolasi). Sintetis. Nominal tolerance Rp1; usulan rasio0,0001, IRR0,0001 poin persentase, PP/DPP0,0001tahun. Jangan membandingkan output fungsi dengan fungsi itu sendiri.

## Eksekusi/evidence

Baca efek scripts existing sebelum menjalankan `scripts/test-backend.ps1`, `scripts/build-android.ps1`; periksa effective DB guard. Android assembleDebug/testDebugUnitTest/lintDebug sesuai perubahan; instrumentation role/form/process death/sync/isolation; backend unit/domain/feature/concurrency MySQL. Runtime paths diverifikasi, bukan ditebak. Dependency dicek manifest/lock dan dokumentasi resmi saat implementasi.

Evidence: task/requirement, commit/branch, command, timestamp WIB, environment, fixture, expected/actual, pass/fail/not-verifiable, log path dan keterbatasan. Jangan simpan credential/data pribadi/dump ke repo. Output generated mengikuti .gitignore; ringkasan terkurasi masuk docs.

UAT: tugas per peran, peserta/perangkat/durasi aktual, definisi bantuan/denominator sebelum uji. Usulan≥90% tanpa bantuan, seluruh perhitungan kritis lulus, nol critical issue; laporkan per peran/tugas. Penerimaan mitra/pembimbing tidak digantikan screenshot agen.
