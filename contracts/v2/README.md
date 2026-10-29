# Kontrak core v2 — FIN01

Baseline source `9da5b30`; branch awal `codex/finance-contracts`. Proposal SHA256 telah diperiksa cocok pada 29 Oktober 2026. Kontrak executable berkembang dari auth/context/activity/sync hingga panen dan [pengeluaran/alokasi](EXPENSES.md). Upload, aset, penjualan, evaluasi dan recovery masih ditambahkan per modul; rancangan API_V2 bukan bukti route yang belum diimplementasikan. FIN01 keseluruhan tetap parsial.

## Wire dan angka

- Role wire `worker` (pekerja), `manager` (pengelola). Role legacy owner/leader tidak dikonversi otomatis. Setiap role v2, termasuk manager, memerlukan assignment greenhouse eksplisit. Tidak ada akses global implisit.
- UUID canonical huruf kecil; version positif 32-bit. Tanggal `YYYY-MM-DD`, kejadian RFC3339 dengan offset; penyimpanan UTC, kalender agronomis Asia/Jakarta.
- Entity UUID global lintas jenis v2, termasuk inline children/balances; collision409 generik, accepted replay tetap utuh. [Registry/backfill contract](IDENTITIES.md) membedakannya dari namespace operation/epoch dan legacy.
- Q10 disetujui pengguna pada chat pelaksanaan: IDR, nominal DECIMAL(20,2), kuantitas/harga DECIMAL(20,6), HALF_UP pada total transaksi. Input yang melebihi skala/range ditolak, tidak dibulatkan diam-diam. JSON angka desimal selalu string, Kotlin BigDecimal, PHP BCMath. Pembulatan line vs total: subtotal diperhitungkan presisi penuh, total transaksi dibulatkan sekali; detail formula modul akan dibekukan sebelum implementasinya. Tidak ada default nilai bisnis.
- Error `{code,message,field_errors,request_id,reasons,current_version?}`. 401 `authentication_required`; 403 `access_denied`; 404 `not_found`; 409 `version_conflict`, `operation_conflict`, `epoch_mismatch`, `season_closed`; 422 `validation_failed`; 429 `rate_limited`. Semua alasan dan field errors dipertahankan. Error internal tidak mengekspos SQL/trace.
- Request JSON max 16 KiB existing tetap berlaku. Satu operasi per request; pagination default 50, maximum 100. Upload terpisah pada FIN09/14, belum tersedia.

## State dan operasi

Aktivitas `completed|skipped`, completeness lokal `draft|ready`, pembatalan `active|cancelled`; sync lokal terpisah `LOCAL|QUEUED|ACCEPTED|CONFLICT|REJECTED|AUTH_REQUIRED|SCOPE_REVOKED|EPOCH_REVIEW`. Pembandingan dan upload berdiri sendiri. Accepted hanya berarti server menerima catatan.

Envelope pada `activity-operation.schema.json`. Object tetap UUID sepanjang hidup; operation UUID baru setiap create/update/cancel. Retry memakai payload persis sama, termasuk epoch. Actor selalu dari sesi, bukan payload. Action create base_version null; update/cancel versi positif. Cancel memakai payload hanya `{reason}` dan memerlukan pengelola. [FIN07 riwayat/resolusi](HISTORY.md) membekukan filter, audit, arsip lokal, dan penggantian operasi setelah tinjauan eksplisit.

Hash SHA256 atas canonical UTF-8: object keys ASCII diurutkan, array berurutan, tidak ada whitespace, string Unicode/slash tidak di-escape kecuali kontrol/quote/backslash, angka JSON hanya integer. Decimal string tidak dinormalisasi ("6.0" berbeda dari "6.00"); outbox menyimpan envelope immutable. Null berbeda dari absent/0. Fixture hash dibuat dengan Python stdlib independen, dibaca oleh PHP dan Kotlin. Payload tidak boleh mengandung key actor/role/totals atau field tidak dikenal.

Kotlin memakai JSON escaping canonical sendiri agar org.json JVM/Android tidak menghasilkan hash berbeda. Pasangan Unicode tidak lengkap ditolak; teks literal backslash-u dipertahankan. Fixture Unicode, kontrol, emoji dan fixture keuangan diperiksa Python/PHP/JVM/native API37. Hash antrean yang tidak cocok ditahan REJECTED `canonical_hash_mismatch`; envelope/UUID/hash tidak ditulis ulang atau diganti otomatis. [Evidence30 Oktober](../../docs/migration/finance-20261029/evidence/20261030-CANONICAL_UNICODE.md).

Commit server mutation+audit+receipt+change-feed satu transaksi. Unique actor+operation; replay memeriksa akses terkini sebelum receipt. Same ID/hash mengembalikan receipt awal; hash berbeda 409. Update menggunakan optimistic version, bukan last-write-wins. Restore mengganti epoch; antrean lama ditahan untuk rekonsiliasi. Receipt/draft tidak dipurge otomatis sampai Q06 disepakati.

## Hak akses dan batas keputusan

Pekerja membaca konteks/riwayat GH penugasan, mencatat dan mengoreksi milik sendiri saat musim terbuka. Pengelola dalam penugasan mengatur master, koreksi, cancel/reopen, finance dan laporan. File/export/pull wajib mengikuti scope objek. Legacy v1 dipertahankan; token v2 memiliki ability `finance-v2`, tidak memberi ability owner/device.

Q04/Q05 belum disahkan: tidak auto-complete jadwal, tidak menetapkan stadia dari umur, creator dari sesi, petugas lain belum bisa dipilih. Sebelum semai ditahan untuk review; tidak mengarang HSS. Missing target menghasilkan alasan belum dapat dibandingkan. Business gate lain tetap ada pada DECISIONS baseline dan tidak dianggap selesai oleh kontrak ini.

## Verifikasi

`scripts/check-finance-contracts.py` memvalidasi JSON Schema dan fixture activity/harvest/expense/allocation serta seluruh local refs OpenAPI. `FinanceWireContractTest` pada PHPUnit dan Android memeriksa hash lintas bahasa dan penolakan format angka; fixture keuangan/Unicode juga dibaca JVM dan native API37. [Panen FIN08](HARVEST.md) memakai integer JSON untuk jumlah buah dan decimal string untuk bobot kg. Schema mendeskripsikan struktur; invariants lintas parent/role/saldo tetap diuji di service+MySQL, bukan dibuktikan oleh schema saja.

Rujukan teknik diperiksa 29 Oktober 2026: [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html), [Laravel Sanctum 13](https://laravel.com/framework/docs/13.x/sanctum). Tidak menambah dependency runtime.
