# Context pack aktif: operasional dan evaluasi keuangan

Baseline kebutuhan: 29 Oktober 2026. Stack tetap Android native Kotlin + Jetpack Compose, Laravel REST API, MySQL. Ini menggantikan context pack IoT. [Status implementasi nyata](IMPLEMENTATION_STATUS.md) membedakan kontrak target, subset executable dan hasil uji terkini.

Baca berurutan: [PRD](PRD.md), [Architecture](ARCHITECTURE.md), [Data contracts](DATA_CONTRACTS.md), [Implementation plan](IMPLEMENTATION_PLAN.md), [Gap analysis](GAP_ANALYSIS.md).

- [Master prompt pelaksanaan](../migration/finance-20261029/MASTER_PROMPT.md): salin untuk memulai implementasi bertahap.
- [Audit dan perbandingan](../migration/finance-20261029/AUDIT_COMPARISON.md).
- [Keputusan dan pertanyaan terbuka](../migration/finance-20261029/DECISIONS.md).
- [Rancangan API v2](../migration/finance-20261029/API_V2.md).
- [Matriks pengujian](../migration/finance-20261029/TEST_PLAN.md).
- [Register sumber](../migration/finance-20261029/SOURCES.md).
- [Arsip sebelum perubahan](../archive/pre-finance-20261029/docs/context/PRD.md): termasuk perubahan lokal yang belum di-commit, bukan sumber requirement aktif.
- [Provenance arsip/context](../migration/finance-20261029/PROVENANCE_REVIEW.md) dan [indeks arsip](../archive/pre-finance-20261029/INDEX.md).

Status checkpoint30 Oktober: schema/API core+master+acuan+activity/panen/pengeluaran/alokasi/lampiran privat/penjualan, draf/outbox dan layar entry/history v2 sudah diuji bertahap, termasuk device offline→Laravel/MySQL uji→manager history. Build default tetap legacy; target terpisah -PfinanceApp. [Kontrak executable](../../contracts/v2/README.md) lebih spesifik daripada rancangan seluruh domain. FIN11 aset/penyusutan, FIN12–17, UI administrasi/master/acuan dan integrasi menyeluruh belum lengkap; jangan mengklaim DONE produk. `docs/implementation/API.md` adalah API v1. Desain21/26 Oktober historis; PDF terbaru dan keputusan pengguna mengatasi konflik.

[Serah terima dan setup FIN11–FIN17](../migration/finance-20261029/HANDOFF_FIN11_FIN17.md) serta [prompt lanjutan](../migration/finance-20261029/CONTINUE_FIN11_FIN17.md) berlaku pada branch GitHub `codex/finance-fin10-handoff`. Pengguna meminta berhenti aman pada checkpoint FIN10; jangan mengklaim FIN11 sudah diimplementasikan.
