# Context pack aktif: operasional dan evaluasi keuangan

Rencana Beranda pengelola 8 November 2026: [master prompt lima upgrade](MASTER_PROMPT_MANAGER_HOME.md) dan [prompt pelaksanaan GPT 6.1 Sol](RUN_MANAGER_HOME_UPGRADE.md). Mencakup pilihan cukup greenhouse, ringkasan seluruh kebun, perhatian berbasis bukti, ringkasan satu greenhouse, serta keuangan dengan basis jelas. Wajib Impeccable dan antislop DURING, mempertahankan identitas botani dan menambahkan motion yang fungsional. Dokumen ini masih spesifikasi; bukan klaim perubahan runtime atau acceptance sudah lulus.

Menu lanjutan 7 November 2026. [Implementasi dan bukti Android](ANDROID_SECONDARY_MENUS_20261107.md) mencakup Antrean, Kegiatan, Panen, Keuangan, dan alur turunannya. Catatan audit berikut merekam status saat disusun.

Perancangan menu lanjutan dari baseline `dc97705` tercatat di [audit Antrean, Kegiatan, Panen, dan Keuangan](SECONDARY_MENUS_UI_AUDIT.md) serta [master prompt alur turunan](MASTER_PROMPT_SECONDARY_MENUS.md).

Frontend botani 7 November 2026. [Penilaian dua aset pengguna](FRONTEND_ASSET_REVIEW_20261107.md) dan [master prompt botani](MASTER_PROMPT_FRONTEND_BOTANICAL.md) merekam alasan pemilihan ilustrasi. [Hasil implementasi login dan Beranda](ANDROID_BOTANICAL_FRONTEND_20261107.md) mencatat aset Android dan hasil pengujiannya.

Riset visual 5 November 2026. [Audit frontend dan referensi UI](FRONTEND_INSPIRATION_AUDIT_20261105.md) serta [daftar kandidat foto](FRONTEND_PHOTO_SOURCES_20261105.md) merekam opsi yang dipertimbangkan sebelum ilustrasi dipilih.

Frontend 5 November 2026. [Rombak alur Android dan hasil verifikasi](ANDROID_OPERATIONAL_FRONTEND_20261105.md), termasuk login, konteks budidaya, riwayat, keuangan, motion dan material navigasi.

Frontend3 November2026: [perubahan visual Android, hasil uji dan batas acceptance](ANDROID_FRONTEND_EVIDENCE_20261103.md). Tema kaca tonal light/dark tersedia; bukan klaim seluruh gate frontend/UAT selesai.

Tooling desain3 November2026: [instalasi Impeccable project-local dan batas verifikasi](IMPECCABLE_SETUP.md).

Keputusan terbaru2 November2026: [pengukuran EC/PPM dan pelepasan IoT](../migration/finance-20261029/MANUAL_MEASUREMENTS_20261102.md), [kontrak pengukuran](../../contracts/v2/MEASUREMENTS.md). Finance kini default tanpa flag pada branch `codex/retire-iot-runtime`, bergantung `0d960db`; source produk v1/owner/firmware/React dilepas, data/migration/provenance tetap. Permintaan pengguna mengatasi coexistence/default legacy pada catatan historis di bawah. Verifikasi dicatat per branch; bukan cutover produksi.

## Lanjutan FIN16–17 — 2 November 2026

Panduan terbaru2 November2026: [instalasi/run FIN17](../migration/finance-20261029/FIN17_INSTALL_RUN.md), [matriks acceptance FIN16](../migration/finance-20261029/FIN16_ACCEPTANCE.md), dan [cutover/rollback/gate FIN17](../migration/finance-20261029/FIN17_CUTOVER.md). Seluruh 38 branch finance telah masuk GitHub `main` melalui PR #1–38 pada2 November2026. Isi hasil integrasi identik dengan checkpoint `e910f6e` yang diuji. [Hasil integrasi dan pengecualian branch legacy](../migration/finance-20261029/MAIN_INTEGRATION_20261102.md).

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
