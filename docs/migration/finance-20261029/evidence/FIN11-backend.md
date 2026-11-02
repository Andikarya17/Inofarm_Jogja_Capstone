# FIN11 backend/math — 30 Oktober 2026 WIB

Branch `codex/finance-assets`, base handoff `04f1121` (includes FIN10 `c03df6d`). Nested clone `inofarm_Jogja_Capstone`, GitHub remote `origin`; parent working changes preserved. User deferred the proposal PDF; source verification against it remains open.

Additive schema11, assigned-manager/same-GH authorization, active acquisition-payment reservations, annual/cumulative caps, version/reason correction/cancel, UUID claims, immutable accepted reads and atomic mutation/audit/receipt/feed under sync-state lock. Published migrations1–10 and frozen schema8 map unchanged. [Contract](../../../../contracts/v2/ASSETS.md). New OpenAPI paths/public schemas leave older definitions structurally unchanged. Kotlin adds preview math only.

Verified locally:

- PHP8.5.12, MySQL8.0.30 clone instance3308; effective `inofarm_test`, test account privilege limited to that schema, guard before SQL. No HTTP fixture on this instance during suite. Original3307 services preserved. Empty development schema provisioned but no application migration/seed applied there.
- Guard4/12 PASS. PHPUnit `--filter '^(?!.*DemoHistory).*'`: **97tests/1522assertions PASS**, including5 FIN11 cases: caps/duplicates/authorization/version/cancel/replay/immutable reads/rounding and injected failure after receipt SQL rolling back allocation/identity/audit/feed/receipt. Only2 legacy demo tests excluded because their guard specifically requires3307. Not a full-suite PASS claim.
- **4 independent PHP/MySQL races PASS**, unique PIDs/CONNECTION_IDs and release barrier: payment two60-from100, allocation two20-from33.33, competing version1 corrections, and asset cancel versus allocation. Caps and ledger/audit/feed counts checked. Final cancel ordering: accepted cancellation and HTTP422 inactive asset, no allocation. Cleanup only actor/GH/UUID fixture data under global lock. Script: test-finance-asset-concurrency.php payment|allocation|update|cancel.
- Independent Fraction oracle **1016 PHP annual budgets PASS**, including leap-day,0.01/2years, zero/max base and exact telescoping. Two operation schemas/create-update-cancel and invalid numeric wire values PASS. Actual MySQL HTTP DTOs validate against typed OpenAPI public schemas; existing contract checker PASS.
- Android -PfinanceApp assembleDebug PASS; JVM50/0failures including2 new BigDecimal tests; lint **0errors/33warnings/5hints** after fixing ignored local SDK escaping and rerunning analysis. No runtime dependency added. Pint and Composer platform PASS.
- Optimized Composer autoload stalled; generated nonoptimized development autoload from a private temporary manifest with identical installed packages and explicit source paths. composer.json/lock unchanged. Interrupting Composer also stopped its owned MySQL child; restarted3308 before passing tests. Initial connection failures not counted as passes.
- Context checker: one existing missing ignored legacy screenshot `output/liquid-glass-implementation/comparisons/all-nine.png`. No fake evidence or historical-byte edits. SOP PDFs match previously read copies byte-for-byte.

Ignored evidence: tmp/fin11-focused.txt, tmp/fin11-regression-final.txt, tmp/fin11-public.json, Android XML/lint reports. No credential/build/runtime files for publication.

Remaining: FIN11 native form/source selection/raw draft/outbox/history/cancel/conflict/lifecycle and offline→restart→API/device proof; FIN12–17 and older administration/master/reference/background/fresh-profile/reconciliation gaps. No device acceptance or whole FIN11 DONE claim. PDF/API29/physical/UAT/pentest/production recovery/cutover/academic gates open; no deploy/user-data reset/legacy retirement.
