# Public payment timestamp contract correction —30 Oktober2026

Branch `codex/finance-public-timestamps`, base `b67e504`. FIN12 actual HTTP source validation exposed an existing mismatch: ExpensePublic.paid_at carried a strict request regex, but the expense service already returns normalized UTC timestamps with fractional seconds and Z. SalePublic already uses date-time correctly and is checked as a reference.

Only the ExpensePublic response paid_at pattern is corrected to accept UTC/fractional/offset output; type string and date-time format remain. Operation input schemas/Laravel request formatting, source values, routes, money, hashes and service behavior are unchanged. Every other OpenAPI path/schema field is compared structurally unchanged.

A shared stdlib-backed timestamp format check makes validation explicit even when optional jsonschema date-time packages are absent. Validation: contract checker accepts UTC/fractional and offset response timestamps and rejects absent timezone, invalid calendar dates, text and numbers for both expense/sale public formats. Three actual synthetic expense/sale snapshots from the guarded FIN12 HTTP test are additionally validated against the full public schemas. No production values, credentials, seed or deployment. This is a contract fix, not FIN12 completion.
