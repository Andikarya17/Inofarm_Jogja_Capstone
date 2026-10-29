# API v2: rancangan kontrak untuk implementasi

**RANCANGAN FIN00; tidak seluruhnya aktif.** V1 tetap di `docs/implementation/API.md`/routes/api.php. Subset v2 sudah berjalan di routes/api_v2.php dan [contracts/v2](../../../contracts/v2/README.md); [status/evidence](../../context/IMPLEMENTATION_STATUS.md) menunjukkan bagian yang diuji. Nama endpoint peta lengkap di bawah adalah rancangan engineering, bukan endpoint literal proposal. Bekukan tiap modul sebelum implementasi.

## Konvensi

Base URL environment configurable, kandidat `/api/v2/`; HTTPS produksi. Auth bearer Sanctum dapat dipertahankan dengan policy baru. JSON decimal berupa string; UUID canonical; dates YYYY-MM-DD, timestamps ISO8601 offset. List cursor/limit bounded dengan stable order dan scope; batas dipilih/didokumentasikan, rekomendasi10KB SOP bukan otomatis seluruh kontrak upload. Pisahkan upload binary dan JSON. Semua mutation menyertakan operation_id/base_version bila applicable; jalur normal dan sync harus memakai domain service/idempotensi yang sama.

Subset lampiran executable schema9 memakai `GET attachments/policy`, `POST attachments` multipart operation+file, list/detail/audit, `GET attachments/{id}/content` dan reasoned cancel. Tabel di bawah tetap rancangan awal; nama download aktual adalah content. [Kontrak lampiran](../../../contracts/v2/ATTACHMENTS.md) membekukan scope, MIME/extension/hash, cap concurrent, replay dan private storage. Q07 konfigurasi produksi belum ditetapkan; native file queue/UI masih berikutnya.

Response error: `code`, `message`, `field_errors`, `request_id`, opsional `current_version` dan `reasons`. 401 sesi, 403 kewenangan, 404 absent/tak boleh diungkap sesuai policy, 409 version/operation conflict, 422 validation/domain, 413 ukuran, 429 rate limit. Jangan kirim stacktrace/SQL/token. API tidak mempercayai actor, role, total, sold_balance atau scope payload.

## Resource/action map

| Area | Endpoint kandidat | Kewenangan / validasi |
|---|---|---|
| Auth | POST auth/login; POST auth/logout; GET me | Login online; me berisi role+assignment+expiry, bukan credential |
| Master | GET/POST greenhouses, varieties, materials, units, grades, expense-categories; PATCH {resource}/{id} | Read scoped; write pengelola; deactivate versioned |
| Identity | GET/POST users; PATCH users/{id}; GET/POST assignments; PATCH assignments/{id} | Pengelola; no self elevation; revoke invalidates access |
| Context | GET/POST seasons; PATCH seasons/{id}; POST seasons/{id}/close dan /reopen; GET/POST planting-groups; PATCH planting-groups/{id} | Parent consistency; open/reopen reason/audit |
| Acuan | GET/POST schedules, targets; PATCH schedules/{id}, targets/{id} | Versi/effective time; referenced version immutable |
| Operasional | GET/POST activities; GET/PATCH activities/{id}; POST activities/{id}/cancel | Worker assigned/own correction/open; manager cancellation |
| Panen | GET/POST harvests; GET/PATCH harvests/{id}; POST harvests/{id}/cancel | Relasi/grade/count/weight, sold balance guarded |
| Biaya | GET/POST expenses; GET/PATCH expenses/{id}; POST expenses/{id}/cancel; GET/POST cost-allocations; PATCH cost-allocations/{id}; POST cost-allocations/{id}/cancel | Pengelola; lock source budget; reason/audit |
| Aset | GET/POST assets; GET/PATCH assets/{id}; POST assets/{id}/deactivate; GET/POST depreciation-allocations; PATCH depreciation-allocations/{id}; POST depreciation-allocations/{id}/cancel | Pengelola; cap dan periode overlap |
| Penjualan | GET/POST sales; GET/PATCH sales/{id}; POST sales/{id}/cancel | Pengelola; paid-only; server total; transaction lock harvest balances |
| Investasi | GET/POST investment-scenarios; GET/PATCH investment-scenarios/{id}; POST investment-scenarios/{id}/deactivate; PUT investment-scenarios/{id}/cash-flows | Pengelola; versioned entire yearly set, no duplicate year |
| Evaluasi | POST season-reports; POST investment-scenarios/{id}/reports; GET reports/{id} | Pengelola, create immutable snapshot from authorized inputs |
| Ekspor | POST reports/{id}/exports; GET exports/{id}; GET exports/{id}/download | Snapshot exact; scoped download; pending/error/ready state |
| Lampiran | POST attachments; GET attachments/{id}/download; POST attachments/{id}/cancel | Validasi file+owning object, private storage; explicit upload status |
| Sync | POST sync/operations; GET sync/changes?cursor=...; GET sync/status | Actor scoped, receipt/hash/base version/epoch, paginated pull |
| Audit | GET {resource}/{id}/audit | Sama atau lebih ketat dari object permissions |
| Recovery | POST backups; GET backups/{id}; POST restores/validate; POST restores | Pengelola terbatas; staged plan/confirmation/maintenance/audit, bukan restore tanpa gate |

Implementasi boleh mulai subset per task, jangan mengklaim endpoint tabel tersedia sebelum route/controller/test ada. Jangan menambah self registration, payment atau notification provider tanpa kebutuhan.

## Contoh operasi sintetis

```json
{
  "schema_version": "2",
  "sync_epoch": "fixture-epoch",
  "operation_id": "11111111-1111-4111-8111-111111111111",
  "entity_type": "activity",
  "entity_id": "22222222-2222-4222-8222-222222222222",
  "action": "create",
  "base_version": null,
  "payload": {
    "planting_group_id": "33333333-3333-4333-8333-333333333333",
    "schedule_id": null,
    "activity_type": "nutrition",
    "occurred_at": "2026-10-29T08:00:00+07:00",
    "manual_ppm": "900",
    "manual_ph": "6.0",
    "work_state": "completed",
    "materials": [],
    "notes": "Fixture uji; bukan rekomendasi budidaya"
  }
}
```

Contoh menunjukkan wire shape, bukan payload lengkap yang pasti diterima: kebutuhan material per jenis ditentukan contract final. Tidak ada actor_id/role/valid=true dari client. Result accepted harus mengembalikan ID cocok dan version authoritative. Bila respons hilang setelah commit, retry operation sama mengembalikan receipt sebelumnya tanpa menjalankan transaksi dua kali. ID sama isi berbeda menghasilkan conflict, meskipun record version sudah berubah. Retry authorization tetap diperiksa.

## Contract-test checklist

DTO roundtrip Kotlin/PHP, decimal null/0/overflow/locale, malformed UUID/time/enum, missing fields, foreign parent, assignment revoked, account switched, replay same/different content, base_version stale, closed season, race update/cancel, pages stable/tombstone, no finance in worker pull, unauthorized files/export, request/file limit, restore epoch mismatch. Contract fixture disimpan di repo tanpa data/credential nyata.
