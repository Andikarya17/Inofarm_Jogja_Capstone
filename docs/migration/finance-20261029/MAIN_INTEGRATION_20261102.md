# Integrasi GitHub main — 2 November 2026 WIB

Pengguna mengotorisasi PR untuk branch yang belum masuk main, pemeriksaan konflik, merge yang aman, lalu checkout lokal main dan pull. Remote publikasi tetap `github`; GitLab tidak diubah. Seluruh38 branch finance GitHub digabung melalui merge commit PR #1–38 dalam urutan ancestry. Semua PR diperiksa `mergeable=true`, `mergeable_state=clean`, SHA head/base sesuai, dan tree hasil merge sama dengan tree branch sumber. Tidak squash/force-push/delete branch atau deployment.

Main setelah38 PR: `a264751`; tree identik dengan checkpoint source `e910f6e`. Hasil uji berlaku untuk snapshot gabungan tersebut, bukan klaim setiap checkpoint antara diuji runtime terpisah. Catatan ini dan pembaruan context diterbitkan melalui PR dokumentasi tersendiri.

## Pemeriksaan aktual

- `git merge-tree --write-tree` untuk semua branch finance: tanpa konflik. Tree setiap hasil GitHub merge diverifikasi lewat GitHub API.
- Contracts/context termasuk delapan hash arsip: PASS. `git diff --check`: PASS.
- Guard database4 tests/12 assertions PASS; backend MySQL **inofarm_test**139 tests/2193 assertions PASS. Filter `^(?!.*DemoHistoryTest)` mengecualikan dua tes historis seperti runner existing. Tidak mengubah database pengguna.
- Finance assembleDebug, assembleDebugAndroidTest, testDebugUnitTest, lintDebug: PASS;77 JVM tests,0 failures/errors/skipped. Gradle84 tasks:3 executed/81 up-to-date,16detik; hasil cache dinyatakan, bukan seluruh test dieksekusi ulang.
- Composer audit locked: tidak ada advisory. GitHub tidak mempunyai workflow/check runs pada branch yang diperiksa; bukan klaim CI PASS.
- Run awal backend lulus tetapi Android berhenti sebelum build karena JAVA_HOME kosong. Run berikut memakai JDK terpasang `C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot`, SkipBackend untuk menghindari pengulangan, dan lulus build/audit.
- Evidence lokal ignored: `outputs/finance-readiness/20261102-212508-7353602c/` dan `outputs/finance-readiness/20261102-212553-6f3437d5/`. Tidak memublikasikan log/runtime/env.
- ADB tidak menampilkan perangkat; instrumentation/device tidak diulang. UAT/pentest/produksi dan gate FIN16/FIN17 tetap terbuka. Hasil ini tidak menjamin bebas seluruh bug runtime.

## Branch yang tidak memerlukan atau tidak aman untuk PR

Enam branch berikut menunjuk commit yang sudah ada pada baseline GitHub `9da5b30`, sehingga tidak memiliki perubahan baru untuk PR:

- `codex/finance-proposal-context`
- `codex/github-initial-publication`
- `codex/proposal-diagram-completeness`
- `codex/proposal-revision-mermaid`
- `codex/proposal-unified-flowchart`
- `codex/proposal-word-template-revision`

Lima branch GitLab berikut berada pada `e9b7c52` dengan riwayat tanpa common ancestor terhadap GitHub. Simulasi read-only `merge-tree --allow-unrelated-histories` menghasilkan25 add/add conflicts termasuk Android, backend, dependency dan context. Sesuai syarat pengguna hanya merge jika aman serta aturan root, branch ini tidak dipaksa digabung atau dipublikasikan ulang sebagai PR GitHub:

- `codex/docs-feature-branch-rule`
- `codex/dummy-worker-history`
- `codex/local-database-access`
- `codex/project-import`
- `codex/proposal-diagrams-mobile-greenhouse`

Main lokal GitLab lama disimpan utuh sebagai `codex/archive-gitlab-main-20261102` pada `e9b7c52` (upstream origin/main tetap). Main lokal baru mengikuti github/main dan pull fast-forward. Folder untracked desain26 Oktober dipertahankan;66 hash file dicatat sebelum switch untuk verifikasi akhir. Tidak reset/stash/delete pekerjaan lokal.

## Daftar PR finance

| PR | Branch | Merge commit |
|---|---|---|
| [#1](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/1) | `codex/finance-contracts` | `f7ef155` |
| [#2](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/2) | `codex/finance-auth-scope` | `f971187` |
| [#3](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/3) | `codex/finance-cultivation-masters` | `2c0ab16` |
| [#4](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/4) | `codex/finance-schedules-targets` | `c8d8590` |
| [#5](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/5) | `codex/finance-sync-server` | `081eb60` |
| [#6](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/6) | `codex/finance-local-outbox` | `8b1bf02` |
| [#7](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/7) | `codex/finance-activity-entry` | `59db524` |
| [#8](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/8) | `codex/finance-context-baseline` | `e5150fb` |
| [#9](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/9) | `codex/finance-history-conflicts` | `7b8086c` |
| [#10](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/10) | `codex/finance-harvest` | `86ba1c7` |
| [#11](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/11) | `codex/finance-draft-logout` | `ac62e73` |
| [#12](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/12) | `codex/finance-consistent-reads` | `fa46d0d` |
| [#13](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/13) | `codex/finance-expenses` | `86f1bc2` |
| [#14](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/14) | `codex/finance-canonical-unicode` | `2909c8e` |
| [#15](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/15) | `codex/finance-expense-entry` | `0ef6513` |
| [#16](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/16) | `codex/finance-reference-names` | `d03211f` |
| [#17](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/17) | `codex/finance-global-identities` | `5982e01` |
| [#18](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/18) | `codex/finance-private-attachments` | `90777b1` |
| [#19](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/19) | `codex/finance-attachment-outbox` | `51ae44c` |
| [#20](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/20) | `codex/finance-attachment-entry` | `8ae8a88` |
| [#21](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/21) | `codex/finance-paid-sales` | `49a95ab` |
| [#22](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/22) | `codex/finance-sales-entry` | `d73a4fa` |
| [#23](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/23) | `codex/finance-fin10-handoff` | `8abec25` |
| [#24](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/24) | `codex/finance-assets` | `81c2c4f` |
| [#25](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/25) | `codex/finance-assets-entry` | `3d19dcb` |
| [#26](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/26) | `codex/finance-public-timestamps` | `6ef8661` |
| [#27](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/27) | `codex/finance-season-evaluation` | `12fbc5f` |
| [#28](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/28) | `codex/finance-ui-popup-readiness` | `3cb8e2f` |
| [#29](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/29) | `codex/finance-investment` | `012c9fa` |
| [#30](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/30) | `codex/finance-cancelled-scenario-history` | `3640cee` |
| [#31](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/31) | `codex/finance-report-export` | `49acde1` |
| [#32](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/32) | `codex/finance-backup-recovery` | `fb3257c` |
| [#33](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/33) | `codex/finance-admin-entry` | `cc53432` |
| [#34](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/34) | `codex/finance-background-sync` | `ea2d3f9` |
| [#35](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/35) | `codex/finance-admin-validation` | `a808459` |
| [#36](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/36) | `codex/finance-mime-security` | `85ca830` |
| [#37](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/37) | `codex/finance-integration-qa` | `eebdce1` |
| [#38](https://github.com/Andikarya17/Inofarm_Jogja_Capstone/pull/38) | `codex/finance-cutover-docs` | `a264751` |
