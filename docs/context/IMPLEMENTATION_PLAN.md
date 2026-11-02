# Implementation plan migrasi proposal terbaru

Baseline29 Oktober 2026. FIN00 dokumentasi adalah audit statis. Implementasi bertahap FIN01–06/core kini ada; [status/evidence terkini](IMPLEMENTATION_STATUS.md) menjelaskan PASS/PARTIAL/TODO. Tabel berikut tetap scope acceptance penuh, bukan klaim seluruh task selesai. [Master prompt](../migration/finance-20261029/MASTER_PROMPT.md) memandu eksekusi; [test plan](../migration/finance-20261029/TEST_PLAN.md) memuat U01–21.

## Aturan lintas task

Satu branch deskriptif `codex/finance-...` per fitur/perbaikan/config/docs berbeda. Pertahankan perubahan lokal; inspect status/diff sebelum switch/commit. Jangan menjadikan satu branch integrasi tempat semua fitur. Jika task berikut bergantung branch belum terintegrasi, gunakan branch baru dari commit dependency yang eksplisit dan catat base; integrasi tetap keputusan workflow, bukan force-reset. Commit/push hanya dalam otorisasi Git yang berlaku, tidak deploy.

Setiap task menghasilkan: source relevan, migration/DTO bila perlu, test yang bermakna, hasil command/evidence, update context dan decision register, serta daftar keterbatasan. DONE hanya ketika AC terpenuhi dan checks applicable lulus. Business gate hanya menahan dependent behavior; kerjakan bagian independen dengan label provisional yang jujur.

## Backlog terurut

| Task / branch suffix | Deliverable dan acceptance | Dependensi | Validasi utama |
|---|---|---|---|
| FIN00 finance-proposal-context | Audit, archive, 5 context aktif, prompt, sumber/decisions/test | Proposal/source | Hash arsip, links, KF/KNF/U coverage, diff |
| FIN01 finance-contracts | Freeze schema/DTO/error/status/precision/epoch, role matrix, OpenAPI, decision log | FIN00; Q10 untuk numeric contract | Schema lint, sample roundtrip, proposal traceability |
| FIN02 finance-auth-scope | Worker/manager auth, me, user activation/revocation, policies/list/file scope, session separation | FIN01 | U01/U02/U18, direct requests/cross-scope |
| FIN03 finance-cultivation-masters | Additive migrations GH/season/group/variety/unit/material/grade/category; master CRUD/deactivate, assignment, close/reopen | FIN01/02 | U03, FKs, parent consistency, fixture MySQL |
| FIN04 finance-schedules-targets | Schedule/target versions, HSS/HST, actual stage, comparator rules, snapshots | FIN03; Q04 dependent | U05/U06, WIB rollover, incomplete rules, reference changes |
| FIN05 finance-local-sync-core | Local DB/drafts/outbox/account isolation, transaction atomicity, server operation receipts/change feed/epoch, retry/pull | FIN01/02/03 | U16/U17/U18, process death, transaction crash, lost response |
| FIN06 finance-activity-entry | Compose role navigation/context/home/forms nutrition/spray/general/stage/skipped, multi-material, local save/queue | FIN04/05 | U04/U06/U16/U20, IME/recreation, no target autofill |
| FIN07 finance-history-conflicts | History/filter/detail/audit, own corrections, manager cancel, two-version conflict review, season close/reopen handling | FIN06 | U07/U08/U18, late queued operation, no silent overwrite |
| FIN08 finance-harvest | Panen multi-grade, local draft/sync, history/edit/cancel with future sold-balance constraint | FIN05/07 | U04/U07/U10, same group/season, cancelled rows |
| FIN09 finance-expenses | Expense/category/private proof upload + cost allocation + UI basis/balance | FIN03/05/07; Q02/Q07 dependent | U09/U21, concurrent allocation, upload authorization |
| FIN10 finance-sales | Paid sales from multiple harvest details one season, staged sales, server subtotal, correction/cancel locks | FIN08/09; Q01 dependent | U10/U17, oversell concurrent, harvest reduction rejection |
| FIN11 finance-assets | Assets/straight-line depreciation and allocation basis/cap, lifecycle UI/audit | FIN09; Q02 dependent | U11, duplicate usage/period, cumulative cap, double-count tests |
| FIN12 finance-season-evaluation | Per-indicator TC/TR/L/R-C/B-C/BEP; completeness/status/snapshot; UI source drill-down | FIN09/10/11 | U12/U15, table fixture, missing vs zero, cancelled exclusions |
| FIN13 finance-investment | Scenario/yearly cash flow/source/assumptions; NPV/IRR/PP/DPP services and UI | FIN01/03; Q03/Q10 dependent | U13/U14, independent precise oracle/solver failure |
| FIN14 finance-report-export | Immutable report snapshots, PDF/CSV downloads/authorization, formula/version/source display | FIN12/13 | U15/U21, source correction after export, CSV formula injection |
| FIN15 finance-backup-recovery | DB+files manifest/checksum, backup/admin UI, staged restore verification, epoch/reconcile | FIN05/09/14; Q08 dependent | U19, missing/corrupt file, restore isolated, old outbox held |
| FIN16 finance-integration-qa | End-to-end worker→manager→report, regression, KNF measurement, authorized pentest/remediation, UAT | FIN02–15 | U01–21, KNF01–08, two screens, Android 10+, no critical issues |
| FIN17 finance-cutover-docs | Install/run/API guide, dependency/seed/scripts cleanup, legacy archival plan, backup/rollback, academic submission | FIN16; Q09/Q12/Q13 | Reproducible fresh install in isolated env, restore drill, reviewer/UAT |

FIN05 berisi satu fondasi sinkronisasi lintas layer; pecah menjadi branch subfitur lokal/server/pull bila ukuran review memerlukan. Setiap split harus punya AC sendiri, bukan menandai semua selesai sekaligus. Pengeluaran/penjualan/aset/evaluasi bukan satu branch besar.

## Milestone dan gate

M0 (FIN00–01): kebutuhan dan kontrak terbaca, ambigu dicatat. M1 (FIN02–04): auth/scope + konteks + acuan. M2 (FIN05–08): satu jalur pekerja Android offline→backend→history/correction terbukti. M3 (FIN09–11): transaksi dengan invariant concurrency. M4 (FIN12–14): evaluasi dan ekspor dapat ditelusuri. M5 (FIN15–17): recovery, security, UAT, cutover.

Urutan proposal16 minggu: minggu1–2 kebutuhan,3–4 model/form,5–7 akun/master/operasional,5–9 sync,8–10 transaksi,10–11 evaluasi/ekspor,12 integrasi/recovery,13–14 QA,15 UAT,16 handover. Ini kerangka relatif dari PDF51–57; tanggal mulai/deadline/PIC belum diberikan. Jangan menyebut deadline kalender atau estimasi selesai yang belum disepakati.

## Vertical slice pertama setelah prompt dijalankan

Periksa instruksi/status/source; bekukan kontrak minimum auth+GH/season/group+activity+operation; buat akun sintetis dua peran dan dua GH pada inofarm_test; tampilkan konteks authorized; simpan aktivitas lokal+outbox; backend scoped accept/receipt; tampilkan accepted status; replay dan denial cross-GH diuji. Gate business lain tidak menghalangi slice ini, tetapi kolom belum disahkan disimpan draft atau ditandai belum dapat dibandingkan. Jangan memasukkan dummy ke production app.

## Strategi migration/release

Migration baru additive, dry-run dan forward migration pada test; rollback test hanya isolated. Tidak mengubah schema inofarm_local atau data owner melalui suite. Role akun lama/import legacy membutuhkan mapping; paket APK uji terpisah. Sebelum cutover: backup+restore proof, matrix compatibility app/API, freeze sementara penulisan, reconciliasi outbox, kesehatan service dan jalur rollback yang menjaga transaksi baru. Tidak menjalankan drop/truncate/reset/clear app data/deploy tanpa otorisasi spesifik.
