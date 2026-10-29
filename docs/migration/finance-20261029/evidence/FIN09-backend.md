# FIN09 backend payment/allocation slice — 30 Oktober 2026 WIB

Branch GitHub `codex/finance-expenses`, dependency `49efd66`. Implements payments, explicit allocation, balance locking and scoped history/audit. **FIN09 overall PARTIAL**: native expense/allocation editor and private evidence upload are follow-up features. No asset/depreciation/sale/evaluation/recovery claim.

## Requirements and behavior

KF07/U09: one actual operating payment can fund explicit FC/VC allocations to multiple seasons in the same GH. DECIMAL(20,2) IDR strings preserve exact cents; blank/null/float/exponent/overflow/excess scale are rejected and explicit zero stays known. Allocation basis is required/nonblank; no business percentage/duration or automatic classification. Asset-acquisition payments cannot be charged in full as operating allocations. Same category keeps its historical name/version snapshot.

Allocation mutation locks source budget inside the global commit-order ledger; active detail SUM reconciles with its server-only counter before changing or presenting balances. Over-allocation409, reducing a payment below usage409, source cancellation with active references409, wrong parent/scope404, worker403. Closed seasons hold changes including changes to their active payment sources; reasoned reopen is required. Accepted receipt replay still works after closed state while enforcing current scope/role/epoch/hash. Mutation/detail/counter/balance feed/audit/receipt commit atomically; fault injection proves rollback.

Migration `2026_10_30_000006_create_expenses_and_cost_allocations.php` is additive, restricted historical FKs and amount/balance CHECKs. Physical expense versions are independent from live allocation balance versions; balance UUID differs from payment UUID. All financial feed rows have manager audience. List/detail/audit use the accepted revision and scoped read boundary. No runtime dependency added or user database/app data reset.

## Verification

- `scripts/test-backend.ps1`: isolated pre-SQL guard **4 tests/12 assertions PASS** then **67 tests/1151 assertions PASS** on effective MySQL `inofarm_test`. Ignored log `tmp/finance-expenses-tests-final.txt`. Seven new feature tests and one shared-fixture unit test cover exact split/replay/corrections/cancellation, worker/foreign/revoked scope and pull exclusion, numeric/basis/asset/zero rules, closed/reopen receipt behavior, post-receipt rollback, corrupt counter hold, foreign category/season, WIB/pagination/audit.
- `.tools/php/php.exe scripts/test-finance-allocation-concurrency.php`: **PASS**, two independent PIDs and MySQL CONNECTION_IDs reach a barrier then each requests60.01 from100.00 in different seasons. One accepted, one allocation_exceeds_source; active detail sum=counter60.01, balance version2, one allocation receipt/audit/feed and two source balance feeds (initial+accepted allocation). MySQL8.0.30/inofarm_test. Ignored log `tmp/finance-expenses-race-final.txt`; only its synthetic actor/GH/UUID rows and individual scratch files cleaned. No database reset.
- `.tools/finance-contracts-venv/Scripts/python.exe scripts/check-finance-contracts.py`: **PASS** activity/harvest/expense/allocation JSON Schemas, Python SHA256 golden fixtures, invalid decimal/integer cases and local OpenAPI refs. PHP reads the new finance hashes/scale. New Kotlin fixture coverage follows the native editor; no new Android run claimed here.
- Existing OpenAPI paths and schemas were compared structurally against parent Git JSON and are unchanged; finance additions only. Schema/ref checker does not claim full OpenAPI semantic validation or runtime authorization proof.

## Limits and next work

U09 source-budget concurrency is proven for this backend slice; full U09/U21/KF07 acceptance still needs native offline entry, file authorization/limits, device integration and UAT. Q02 production allocation policy remains open; explicit basis/nominal is supported. Q07 upload configuration remains open. Asset acquisition linkage protection and depreciation are FIN11. Cross-kind UUID registry, restore/scope reconciliation, background scheduling, API29/physical, UAT/pentest and handover gates remain open.

[Contract](../../../../contracts/v2/EXPENSES.md), [current status](../../../context/IMPLEMENTATION_STATUS.md).
