# Accepted operational reads — 30 Oktober 2026 WIB

Branch `codex/finance-consistent-reads`, dependency `763cd7f`. Backend bug fix, no schema/dependency change and no migration of user data. Publication target GitHub; legacy API v1 unchanged.

Previously an activity header could be fetched before a concurrent correction and its material rows fetched afterwards, producing a version that never existed. Harvest used a read transaction but did not share an explicit boundary with permission changes. Activity/harvest list, detail and audit plus harvest availability now acquire the same sync metadata row lock used by mutations. They reload the actor, active role and live token before evaluating parent/assignment scope. Read transactions are short and serialize with writes; throughput under larger workloads has not been benchmarked.

Operational DTOs are projected from the immutable accepted `audit_logs.changes.after` matching the authorized header UUID/version. They do not rejoin mutable children or refresh historical reference/creator display names. Missing, ambiguous or identity-mismatched revisions fail instead of inventing a DTO. Availability remains a separate live balance projection under the same boundary; audit `actor_name` remains the current account name at read time. Snapshot lookup is internal and never replaces authorization of the parent.

## Verification

`scripts/test-backend.ps1`: isolated pre-SQL database guard **4 tests/12 assertions PASS**, then effective MySQL **inofarm_test 59 tests/967 assertions PASS**. Ignored log `tmp/finance-consistent-reads-tests.txt`. Five new tests cover:

- Old authorized activity/harvest headers project exactly their accepted version even after live child rows change; current list/detail return the new version.
- A controlled same-connection SQL listener injects a real service correction immediately after a header SELECT; the response stays entirely at version1 and the next read returns version2. This is a deliberate interleave, not a two-process concurrency claim.
- Missing revision returns `record_snapshot_missing`500 rather than mutable child data.
- Stale manager role, inactive account and revoked token are checked again at the read boundary.
- A second independent guarded MySQL connection (different CONNECTION_ID) times out with MySQL1205 trying to acquire the metadata lock held inside a scoped read. No ledger fixture had acquired that lock beforehand. This proves the lock boundary, not a throughput measurement.

The initial full run exposed strict PHP array-key-order assertions against MySQL JSON normalization; comparison now uses canonical hashes (preserving value types and array order) and exact HTTP JSON. Final full suite passed. Android source/protocol/schema unchanged, so prior Android evidence is not represented as a fresh device run here. API29/physical/UAT/pentest, cross-kind UUID identity, background sync and full finance/recovery remain separate work.

[Read contract](../../../../contracts/v2/HISTORY.md), [implementation status](../../../context/IMPLEMENTATION_STATUS.md).
