# Canonical UTF-8 across PHP/JVM/Android — 30 Oktober 2026 WIB

Branch `codex/finance-canonical-unicode`, dependency `3e2caf1`. Separate correction discovered during native finance entry development; unfinished editor changes are excluded from this commit. No schema/runtime dependency or server mutation changes.

The new expense golden fixture contained an em dash. JVM org.json.quote escaped Unicode differently from the required raw UTF-8 contract, producing a different SHA256 from Python/PHP. Global string replacements for U+2028/U+2029 could also alter a user's literal backslash-u text. Kotlin now emits JSON string escaping directly: quote/backslash and control characters escaped, valid Unicode/slash retained, paired UTF-16 surrogates accepted and incomplete pairs rejected. No platform quote dependency or Unicode normalization.

Existing immutable envelopes/hashes are retained. Coordinator detects a hash that cannot be reproduced under the contract and marks the operation REJECTED `canonical_hash_mismatch` without sending, rewriting or auto-replacing it. Reconciliation remains explicit follow-up work. Standard activity/harvest golden hashes remain unchanged. Test-only assets reference the shared synthetic contract fixtures, not app credentials/user data.

Verification:

- JVM contract classes CanonicalUnicodeTest2 + FinanceWireContractTest4 **6 PASS**. They ran within a35-test working tree that also contains five unfinished native-entry unit tests; no native-entry feature acceptance is claimed by this evidence. Earlier focused6 run and final full working-tree run logs ignored `tmp/finance-canonical-unicode-unit.txt`, `tmp/finance-canonical-unicode-final.txt`.
- Native API37 instrumentation **4/4 PASS**: CanonicalUnicodeDeviceTest1 reads the same five fixtures (Unicode/activity/harvest/expense/allocation); FinanceSyncCoordinatorTest3 verifies response-loss replay/account isolation and mismatch held across repeated sync with0 mutation calls and unchanged UUID/envelope/hash. Medium_Phone_API_37.0, isolated `.finance`, JDK25/Gradle9.3.1/AGP9.1.1. Lint0errors in the same working-tree run. No API29/physical/real-MySQL mutation claim here.
- Isolated TestDatabaseGuard4/12 PASS before PHP; FinanceWireContractTest **5 tests/23 assertions PASS**, no DB required. Python contract/schema/ref/golden checker PASS. Logs ignored `tmp/finance-canonical-guard.txt`, `tmp/finance-canonical-php.txt`.
- New independent Python fixture covers em dash/euro/accents/CJK/melon emoji/slash/U+2028/U+2029/C1/quotes/backslash/control characters and literal backslash-u strings. One early test expected IllegalArgumentException for malformed low surrogate but implementation threw IllegalStateException; exception type standardized and final tests passed.

Native expense/allocation editor remains in progress on its own branch; private upload, finance modules, reconciliation and handover gates remain incomplete. [Wire contract](../../../../contracts/v2/README.md), [status](../../../context/IMPLEMENTATION_STATUS.md).
