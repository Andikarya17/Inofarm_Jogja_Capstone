# FIN13 investasi native — 2 November 2026

Branch `codex/finance-investment`, parent `3fa142e`. Core backend, perhitungan, native form/store dan real API/two OS reboot **PASS lokal**. Ini checkpoint FIN13; FIN14 ekspor belum dimulai. Pengguna terbaru meminta langsung commit/push pekerjaan terakhir lalu penjelasan langkah berikutnya.

## Perubahan

Manager memiliki workspace skenario/arus kas dan laporan investasi. I0/CF/diskonto/konvensi tidak diberi nilai produksi awal. Raw input kosong, whitespace, koma belum lengkap dan tahun di luar horizon tetap tersimpan; payload hanya tahun1..horizon. Mengurangi horizon tidak menghapus raw tahun lain. Persen dikonversi ke fraksi8 tanpa pembulatan tersembunyi; negatif CF sah, I0 negatif ditolak. Pemilihan sumber aktual/proyeksi/belum diketahui dan basis tidak menjadi klaim audit usaha.

Repository/ViewModel memisahkan IO/data/presentation; generation/account/role/current GH dijaga. Autosave/Back/ON_STOP/logout mempertahankan isian; editor accepted tanpa perubahan tidak membuat draft palsu. Koreksi memakai alasan/base_version; pembatalan menyimpan alasan mentah, queue immutable, penutupan draf pembatalan mengarsipkan lokal dan mempertahankan server. Conflict resolution eksplisit; operasi penolakan yang diizinkan diganti dengan ID operasi baru sambil mempertahankan envelope/hash lama.

SQLite9 additive menambah scenario index dan scoped hydration atas accepted cache, bukan mengubah frozen identity migration backend8. Skenario/laporan lama yang diterima klien sebelum fitur ini tetap dapat dimaterialisasi; worker/revoked GH tidak mendapat akses cache keuangan. Audit dan frozen source pages disimpan terenkripsi. Semua empat indikator/reasons/input/asumsi tampil; IRR menampilkan interval/metode/iterasi, PP/DPP no recovery dibedakan dari missing. Decimal/metadata integer diperiksa ketat; exact fraction dan IRR percentage dibandingkan dengan output HALF_UP tanpa float. Laporan accepted read-only; evaluasi ulang membuat laporan baru.

## Verifikasi

- APK app/test assemble, JVM **66 PASS**, lint **0 errors/33 warnings/5 hints**, `tmp/fin13-android-fourth.txt` (7m19s). APK app SHA256 `51c7751a289b4f6dae1e1fbbbfad21d54f928a57cd1db9037f6553d48b075f8d`.
- Android API29 Finance_API29/emulator-5558: **8 PASS,61.824s**, `tmp/fin13-api29-device-first.txt`. Tampilan320dp/font200%, raw/invalid horizon retention, typed metrics/solver/source pagination, preview failure and stale-session write prevention, scope/role isolation, durable operation/database reopen, cancellation/no-op/discard and actual schema8→9 migration preserve raw/hash/cursor.
- Real Laravel/MySQL inofarm_test: **3 phases PASS with two actual OS reboots**, `scripts/test-finance-investment.ps1 -RebootBetweenPhases -SkipBuild`, `tmp/fin13-real-api29-reboots.txt`; phase scenario27.667s, report46.001s, verify18.58s. Offline prerequisite is an owned PHP server actually stopped, not a mock flag. Boot count increases asserted between phases; no app data clear.
- Phase1 scenario queued offline. Phase2 accepted/replayed same operation/hash, report1 NPV4132231.40; server correction I0150m/version2 caused reviewed old source409. Explicitly re-previewed/replaced operation queued offline. Phase3 replacement NPV−45867768.60/PP not recovered; old report hash/results/source version1 unchanged, new sources version2; scenario cancellation3 did not invalidate old receipt replay. Worker UI/API403 checked.
- SQL independently reconciled exactly3 scenario versions,6 annual version rows,2 report snapshots/version1,3 sources each, canonical full+source hashes, one receipt/audit/feed per accepted report. Only runner-owned fixture UUIDs removed. No PHPUnit/RefreshDatabase while live HTTP fixture existed.
- Backend **119 PASS/1906 assertions**, independent oracle259, two independent process races and typed actual DTO/hash contracts are detailed in [backend evidence](FIN13-backend.md).

Final root-navigation/recreation/logout focus **1 PASS,16.992s**, `tmp/fin13-root-api29-focus.txt`; test APK build PASS41s, `tmp/fin13-root-test-build.txt`. Main app APK unchanged. Together with8-case run,9 unique new native cases pass across two runs, not a claimed single9 run. Earlier broad legacy69 run had68PASS/1timeout and unchanged focused case1PASS; this is69 unique passes across runs, not a single clean69. [Timing evidence](20261102-UI_TEST_READINESS.md). Physical device, UAT/pentest/reviewer, background sync/master UI/recovery/cutover remain separate gates. No deploy/production restore/legacy retirement is authorized by this publication.


Follow-up visible from final source review: generic hydration currently filters cached tombstones; a cancelled investment scenario received only into cache on a fresh profile can be omitted from the local scenario list. Backend annual/audit history and immutable reports remain intact. Add typed cancelled-scenario cache hydration and a fresh-profile fixture before treating all history acceptance complete. This commit is a tested core checkpoint with this explicit follow-up. Context checker also retains one pre-existing missing image link in docs/implementation/UI_REDESIGN.md (`output/liquid-glass-implementation/comparisons/all-nine.png`); contract, staged secret/compatibility and whitespace checks pass.


Resolved follow-up: cancelled-only fresh-cache history repaired and17 device cases PASS on separate bug branch. [Evidence](20261102-CANCELLED_SCENARIO_HISTORY.md). Earlier finding above is retained as checkpoint history. Push deferred by user.
