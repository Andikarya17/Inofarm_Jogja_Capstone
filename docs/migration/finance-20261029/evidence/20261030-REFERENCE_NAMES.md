# Preserve full reference names — 30 Oktober 2026 WIB

Branch GitHub `codex/finance-reference-names`, base `2f91180`. Static review found that master validation/schema permits150 characters while harvest grade/payment category/allocation season snapshots had VARCHAR120. Valid master names could therefore fail when used by downstream transactions.

Additive migration `2026_10_30_000007` widens only harvest_items.grade_name, expenses.category_name and cost_allocations.season_name to150. It does not rewrite names, amounts, IDs, versions, receipts or JSON/audit snapshots. Down intentionally refuses narrowing historical text; rollback/recovery requires a reviewed forward plan instead of truncation. No migration was run on user data, and no Android/API/dependency change was needed.

`scripts/test-backend.ps1`: isolated pre-SQL guard **4/12 PASS**, then effective MySQL **inofarm_test 69 tests/1187 assertions PASS**. Ignored log `tmp/finance-reference-names-final.txt`. The new case uses150 accented/CJK characters, creates harvest/payment/allocation with complete names, renames masters, corrects accepted transactions while preserving original name snapshots, reads detail/audit and rejects151-character master input. Existing operational/financial/read-concurrency regressions pass. Initial test accidentally included immutable greenhouse_id in a season PATCH; request was correctly rejected, test corrected, final suite passed.

Prior Android/device evidence remains separate; no new device run claimed for this schema-width correction. Registry across entity kinds, uploads, remaining finance modules and handover gates are still open. [Status](../../../context/IMPLEMENTATION_STATUS.md), [expense contract](../../../../contracts/v2/EXPENSES.md).
