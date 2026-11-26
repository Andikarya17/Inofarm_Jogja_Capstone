# FIN10 paid sales

Backend branch codex/finance-paid-sales, dependency0ff3f8f. Additive schema10 stores sales and immutable-identity sale_items, preserving legacy and harvest histories. Scope is a manager's assigned greenhouse and one open season. Each selected harvest item must belong to that season; multiple groups/grades are supported through separate sources. Source list relation is checked server-side. Actor comes from the session. Native form/history/selection/outbox integration is the next increment; only shared Kotlin math and contract fixtures are implemented here.

`GET/POST sales`, `GET/PATCH sales/{id}`, `POST sales/{id}/cancel`, `GET sales/{id}/audit` use scoped consistent reads, immutable accepted snapshots and the existing operation ledger. List filters greenhouse/season/state and inclusive WIB from/until, with bounded cursor pagination. Public UUIDs and versions follow core rules. Replay requires current authorization/epoch/hash and confirms the original receipt before closed/inactive write-state checks. Same operation with different content conflicts; cancellation/correction require a reason and current base_version.

The [operation schema](sale-operation.schema.json) requires season_id, paid_at (RFC3339 offset), payment_status=`paid`, customer_name (explicit empty string permitted), notes and1–30 line items. Each line has globally reserved id, harvest_item_id, strictly positive quantity_kg and nonnegative price_per_kg; decimal strings use20,6. Line and source IDs cannot repeat within a transaction. A line's source/parent remain fixed; removal retains historical inactive rows, and adding another line reserves a new ID. Season remains fixed for the sale. Payment status is explicit; this contract supplies no production price. Return/credit/refund behavior remains outside the initial paid-only scope in Q01. Cancellation voids a reasoned record and releases its accepted quantity; it preserves the last line details for history.

Server multiplies each quantity×price exactly at12 decimal places, sums full products, then rounds the transaction total once with HALF_UP to IDR20,2. Quantity total must fit20,6; money/quantity overflow is rejected before SQL. Exact line/transaction amounts use decimal40,12 storage for traceability. Zero price is explicit known zero; missing price is invalid. The synthetic [golden](fixtures/sale-operation.json) has two lines1kg×0.004: exact0.008, posted0.01. No per-line cent rounding. Seasonal revenue will sum posted active paid transaction totals; evaluation must retain this rounding convention rather than silently recompute another aggregate. Shared Kotlin SaleAmounts provides a labeled preview with BigDecimal; accepted server values remain authoritative.

Global sync metadata and deterministic source-item locks serialize mutations. Active sold quantity is the sum of active lines whose sale header is active. It must equal harvest_items.sold_weight_kg, and new net usage must stay within harvested weight. Counter mismatch returns500 harvest_balance_inconsistent and holds writes/availability for explicit reconciliation, without auto repair. Sale edit credits only its own old quantities before validating replacements; cancel releases its current quantities. Every changed counter increments separate harvest balance_version and publishes a scoped harvest_balance with no monetary fields. Physical harvest version stays independent. Harvest correction/cancel also checks the actual-sales reconciliation and refuses to undercut used weight. Cancelled sale headers retain current line details, but their quantity is excluded by the header-state join.

Root/child IDs, sale mutation, counter updates, audit/receipt and financial/balance feeds commit in one MySQL transaction. Snapshot line metadata includes source harvest/version and grade name/ID; source or master rename does not rewrite historical accepted receipts. Workers receive authorized operational balances, while sale price/customer/amount/audit/receipt/feed require a manager assignment. Invalid/cross-season sources, unknown totals/actor fields, duplicate IDs, stale version and closed season are rejected. [Backend evidence](../../docs/migration/finance-20261029/evidence/FIN10-backend.md).

## Baris penjualan dan FIFO (25 November 2026)

Payload `sales` kini boleh memakai `lines` (1–30 baris) sebagai ganti `items`. Keduanya tidak boleh dikirim bersama dan salah satunya wajib (422). Skema: [operation schema](sale-operation.schema.json), contoh: [fixture retail](fixtures/sale-lines-retail.json).

Satu baris `lines[]` berisi `id` (UUID dari klien, unik), `planting_group_id` (varietas/kelompok tanam), `grade_id`, `quantity_kg`, `price_per_kg`, serta opsional `fruit_count` (bilangan bulat minimal 1, atau null) dan `buyer_grade` (A/B/C, khusus mitra). Klien tidak memilih rincian panen. Server mengambil kilogram dari panen aktif **paling lama lebih dulu** (`occurred_at`, lalu id) untuk kelompok dan grade itu, menyebar ke beberapa rincian panen bila perlu, dan menyimpannya sebagai alokasi. Baris yang diterima ditampilkan di `SalePublic.lines[]`:

- `id`, `planting_group_id`, `grade_id`, `grade_name`, `variety_name`, `buyer_grade`, `quantity_kg`, `fruit_count`, `price_per_kg`, `exact_amount`.
- `quality`: `saleable` (Layak), `defective` (Cacat) atau null bila nama grade bukan grade sistem.
- `allocations[]`: `{harvest_item_id, harvest_id, quantity_kg}` per rincian panen yang terpakai, berurut dari panen terlama.

Total tetap dihitung seperti sebelumnya: kuantitas × harga pada 12 desimal, dijumlahkan, lalu dibulatkan HALF_UP sekali per transaksi.

### Bentuk lama tetap diterima

`items[]` (`id`, `harvest_item_id`, `quantity_kg`, `price_per_kg`, `buyer_grade` opsional) tetap berlaku agar antrean offline dari aplikasi lama bisa terkirim. Respons `SalePublic` selalu memuat `items[]` (kini dengan `sale_line_id`, null untuk data lama) dan `lines[]`. Koreksi penjualan lama dengan `lines` menyimpan alokasi sebelumnya sebagai kredit sebelum alokasi ulang.

### Tipe pembeli

`customer_type` dan `customer.type` menerima `consumer`, `retail` dan `partner` (atau null). Grade A/B/C hanya untuk `partner`. Daftar pembeli: [CUSTOMERS.md](CUSTOMERS.md).

### Bentuk galat saldo panen

`harvest_exceeded` berbeda menurut bentuk payload:

| Payload | Status | `reasons` |
|---|---|---|
| `lines` | 422 | objek `{line_id, available_kg}` per baris yang kekurangan |
| `items` (lama) | 409 | teks, seperti sebelumnya |

Skema `Error.reasons` di OpenAPI adalah gabungan teks atau objek tersebut. Klien membaca `line_id` untuk menandai baris yang bermasalah dan `available_kg` untuk menawarkan jumlah maksimal.
