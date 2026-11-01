# FIN12 — native evaluation acceptance checkpoint

2 November 2026 WIB, branch codex/finance-season-evaluation, backend dependency81de79e. Local FIN12 critical acceptance PASS; broader cross-screen regression remains open, therefore not claiming complete product/FIN12 handover.

## Implemented

Dedicated manager season-evaluation workspace, explicit coverage/state/pending/notes, no business defaults; server preview bound to every reviewed input and expected source/formula hashes; raw draft/autosave/Back/ON_STOP/logout persistence; immutable create outbox; accepted/cached/queued distinction. Lists filter greenhouse/season with bounded pages. Every indicator exposes reasons, formulas, inputs, assumptions and exact fractions; source drill-down shows immutable versions, excluded/reference-only records, quantities/prices, asset life/date and depreciation budget. Indonesian output formatting preserves significant decimal places. Received reports materialize even when cached by an older consumer; role/scope revocation hides financial details while retaining raw drafts, server/account isolation and held operations. SQLite schema stays8; no new dependency or legacy React change.

## Verified artifact and environment

JDK25.0.2, finance debug APK com.inofarm.jogja.finance, SHA256 `96b933a9960d4b42a5f7fb792f5f5b3753b1725edbec27372619ad0091f9924f`. Assemble app/test + JVM59/0fail + lint0errors/33warnings PASS (`tmp/fin12-utf8-android-build.txt`). Tests use separate named temporary databases/preferences or separate finance application; no app data clear.

Seven new storage/transport/UI cases: clean **7 PASS** on owned API36.1 (`tmp/fin12-device-seven-rerun.txt`,88.277s). Covers raw unknown vs0, preview/queued identity across DB reopen, related pending records, current role/assignment, source paging/cache/server denial and all11 indicators at320dp/200% text. Prior run7 had6pass/1keyboard failure due System UI ANR; prior run6 had5pass/1same failure plus focused rerun1pass. These failures are retained and are not counted as clean runs. API36 emulator had OS dialogs after cold boot; recovery and720x1600/density280 display override were applied only to the owned test emulator.

## Real API/MySQL + actual OS reboot

`scripts/test-finance-season-reports.ps1 -JavaHome ... -PhpPath ... -GradleHome ... -Device emulator-5558 -RebootBetweenPhases -SkipBuild` PASS, reused the verified artifact. Fresh owned Finance_API29 AVD in project .runtime, official Android29 Google APIs x86_64 revision13 image,4cores/1536MB,720x1600/density280; no snapshot load/save and no user data clear. PHP8.5.12/MySQL8.0.30 effective inofarm_test:3308, guard before SQL. Owned PHP port8001/nonce/PID probes and fixture UUID account/GH checks; only owned fixture removed.

1. Native manager form/preview/source drill-down, stop the actual PHP server at a fixture barrier, then queue offline: PASS. Initial process PID4081/boot1, frozen request ID/hash in private proof.
2. Actual OS reboot; original request accepted and replayed twice without duplicate. Native new evaluation preview; cancel source depreciation via API, queue stale preview and receive report_sources_changed. Queue review button opens retained form and requires new preview. New TC80.00 preview, PHP stopped again, explicit reviewed replacement queued offline: PASS, PID2958/boot2. Old rejected operation/hash retained and superseded.
3. Actual second OS reboot, boot3; replacement accepted once. Prior report stays TC113.33 with included depreciation revision1; new report TC80.00 with excluded cancelled revision2; TR0.00 and P unavailable, six frozen sources each. Prior accepted receipt still replays after source change. Both native histories/source drill-down checked. Worker sees no finance tab and gets403 for report list/detail/sources: PASS.

MySQL independent final reconciliation: exactly two version1 snapshots with correct full canonical hashes; one audit/receipt/feed each, no duplicate replay; source cancellation revision2. Logs `tmp/fin12-real-api29-reboots.txt` and ignored outputs/finance-season-reports; private proof has boot1→2→3 and immutable hashes. Source payment80.00 and asset acquisition100.00 are not added twice alongside FC20/VC60/depreciation33.33.

First attempt on API36.1 passed phase1 but instrumentation stopped during phase2 after reboot. OS events showed Bluetooth crash, repeated phone startup ANRs and GMS font-provider startup timeout; no application Java crash stack was found. Exact cause remains unisolated and API36 full-reboot acceptance is not claimed. Failed logs archived in outputs/finance-season-reports-api36-failed-20261102. Same APK passed all three phases on API29.

## Open checks

Broader API29 regression of EntryUi/LocalStore/Sync/Reports/StorageTiming was interrupted after repeated legacy popup/touch lookup failures (`tmp/fin12-api29-regression.txt`); it is not a clean69 run. Investigate synchronization/window transitions and rerun relevant cases before closing FIN12 regression. Existing target-API runtime, whole-product API29/fresh profile/KNF, admin/master/reference UI, background/recovery/export/investment, physical device, mitra UAT/pentest/reviewer/cutover gates remain open. Push remains deferred; no deployment or production restore.
